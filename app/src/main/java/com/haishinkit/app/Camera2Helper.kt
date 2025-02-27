package com.haishinkit.app

import android.content.Context
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import com.haishinkit.media.Camera2Source
import com.haishinkit.media.MultiCamera2Source
import com.haishinkit.rtmp.RtmpStream
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

/**
 * Helper class for safe camera operations with Camera2 API
 */
class Camera2Helper(private val context: Context) {
    companion object {
        private const val TAG = "Camera2Helper"
        private const val CAMERA_CLOSE_TIMEOUT = 2500L // ms
    }

    private val cameraOpenCloseLock = Semaphore(1)
    private var backgroundThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null
    
    /**
     * Safely applies or switches camera source for the stream
     */
    fun safelyAttachCameraSource(stream: RtmpStream, useMultiCamera: Boolean) {
        try {
            // First safely detach current camera if it exists
            safelyDetachCurrentCamera(stream)
            
            // Then attach the new camera
            if (useMultiCamera) {
                attachMultiCamera(stream)
            } else {
                attachSingleCamera(stream)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error attaching camera source: ${e.message}", e)
            // Ensure any locks are released
            if (cameraOpenCloseLock.availablePermits() == 0) {
                cameraOpenCloseLock.release()
            }
        }
    }
    
    /**
     * Safely detaches the current camera source
     */
    fun safelyDetachCurrentCamera(stream: RtmpStream) {
        try {
            // Get the current video source
            val currentSource = stream.videoSource
            
            // Block access to the camera during the operation
            if (!cameraOpenCloseLock.tryAcquire(CAMERA_CLOSE_TIMEOUT, TimeUnit.MILLISECONDS)) {
                Log.e(TAG, "Timeout waiting to lock camera for closing")
                return
            }
            
            try {
                // Detach the current source with handling all possible exceptions
                if (currentSource is Camera2Source) {
                    try {
                        currentSource.close()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error closing Camera2Source: ${e.message}", e)
                    }
                } else if (currentSource is MultiCamera2Source) {
                    try {
                        currentSource.close()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error closing MultiCamera2Source: ${e.message}", e)
                    }
                }
                
                // Detach the source from the stream
                try {
                    stream.attachVideo(null)
                } catch (e: Exception) {
                    Log.e(TAG, "Error detaching video source: ${e.message}", e)
                }
                
                // Small delay before attaching a new camera
                Thread.sleep(100)
            } finally {
                cameraOpenCloseLock.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in safelyDetachCurrentCamera: ${e.message}", e)
        }
    }
    
    private fun attachSingleCamera(stream: RtmpStream) {
        try {
            if (!cameraOpenCloseLock.tryAcquire(CAMERA_CLOSE_TIMEOUT, TimeUnit.MILLISECONDS)) {
                Log.e(TAG, "Timeout waiting to lock camera for opening")
                return
            }
            
            try {
                val cameraSource = Camera2Source(context)
                
                // Apply the source to the stream before opening the camera
                stream.attachVideo(cameraSource)
                
                // Open the camera with exception handling
                try {
                    cameraSource.open(android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK)
                } catch (e: Exception) {
                    Log.e(TAG, "Error opening back camera: ${e.message}", e)
                    
                    // Try to open the front camera if the main one is unavailable
                    try {
                        cameraSource.open(android.hardware.camera2.CameraCharacteristics.LENS_FACING_FRONT)
                    } catch (e2: Exception) {
                        Log.e(TAG, "Error opening front camera: ${e2.message}", e2)
                    }
                }
            } finally {
                cameraOpenCloseLock.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in attachSingleCamera: ${e.message}", e)
            if (cameraOpenCloseLock.availablePermits() == 0) {
                cameraOpenCloseLock.release()
            }
        }
    }
    
    private fun attachMultiCamera(stream: RtmpStream) {
        try {
            if (!cameraOpenCloseLock.tryAcquire(CAMERA_CLOSE_TIMEOUT, TimeUnit.MILLISECONDS)) {
                Log.e(TAG, "Timeout waiting to lock camera for opening multi-camera")
                return
            }
            
            try {
                val multiCameraSource = MultiCamera2Source(context)
                
                // Apply the source to the stream before opening the cameras
                stream.attachVideo(multiCameraSource)
                
                // Open the cameras with exception handling
                try {
                    multiCameraSource.open(0, android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK)
                    
                    try {
                        multiCameraSource.open(1, android.hardware.camera2.CameraCharacteristics.LENS_FACING_FRONT)
                        multiCameraSource.getVideoByChannel(1)?.apply {
                            frame = android.graphics.Rect(20, 20, 90 + 20, 160 + 20)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error opening front camera for multi-camera: ${e.message}", e)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error opening back camera for multi-camera: ${e.message}", e)
                    
                    // If multi-camera setup fails, revert to single camera
                    safelyDetachCurrentCamera(stream)
                    attachSingleCamera(stream)
                }
            } finally {
                cameraOpenCloseLock.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in attachMultiCamera: ${e.message}", e)
            if (cameraOpenCloseLock.availablePermits() == 0) {
                cameraOpenCloseLock.release()
            }
        }
    }
    
    /**
     * Starts the background thread for camera operations
     */
    fun startBackgroundThread() {
        try {
            backgroundThread = HandlerThread("CameraBackground").also { it.start() }
            backgroundHandler = backgroundThread?.looper?.let { Handler(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting background thread: ${e.message}", e)
        }
    }
    
    /**
     * Stops the background thread
     */
    fun stopBackgroundThread() {
        try {
            backgroundThread?.quitSafely()
            try {
                backgroundThread?.join()
                backgroundThread = null
                backgroundHandler = null
            } catch (e: InterruptedException) {
                Log.e(TAG, "Error stopping background thread: ${e.message}", e)
            }
        } catch (e: Exception) {
            Log.e(TAG, "General error in stopBackgroundThread: ${e.message}", e)
        }
    }
    
    /**
     * Checks if a camera is available by ID
     */
    fun isCameraAvailable(cameraId: String): Boolean {
        try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId)
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Camera $cameraId is not available: ${e.message}", e)
            return false
        }
    }
} 