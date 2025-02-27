@file:Suppress("MemberVisibilityCanBePrivate")

package com.haishinkit.compose

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.haishinkit.graphics.VideoGravity
import com.haishinkit.media.Stream
import com.haishinkit.view.HkSurfaceView
import com.haishinkit.view.HkTextureView

private const val TAG = "HaishinKitView"

/**
 * The main view renders a [Stream] object.
 */
@Composable
fun HaishinKitView(
    stream: Stream,
    modifier: Modifier = Modifier,
    videoGravity: VideoGravity = VideoGravity.RESIZE_ASPECT,
    viewType: HaishinKitViewType = HaishinKitViewType.SurfaceView,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var isViewReady by remember { mutableStateOf(false) }
    
    // For debugging
    val viewTypeString = if (viewType == HaishinKitViewType.SurfaceView) "SurfaceView" else "TextureView"
    Log.d(TAG, "Initializing HaishinKitView with $viewTypeString")

    val videoView =
        remember(context, viewType) {
            when (viewType) {
                HaishinKitViewType.SurfaceView -> HkSurfaceView(context)
                HaishinKitViewType.TextureView -> HkTextureView(context)
            }.apply {
                this.videoGravity = videoGravity
            }
        }

    // Observe the lifecycle
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    Log.d(TAG, "Lifecycle: ON_RESUME - Reattaching stream")
                    videoView.attachStream(stream)
                    isViewReady = true
                }
                Lifecycle.Event.ON_PAUSE -> {
                    Log.d(TAG, "Lifecycle: ON_PAUSE")
                    // Don't detach the stream when paused, this can cause a black screen
                }
                Lifecycle.Event.ON_DESTROY -> {
                    Log.d(TAG, "Lifecycle: ON_DESTROY - Detaching stream")
                    videoView.attachStream(null)
                    isViewReady = false
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            videoView.attachStream(null)
        }
    }

    Box(modifier = modifier.background(Color.Black)) {
        AndroidView(
            factory = {
                Log.d(TAG, "Creating AndroidView for HaishinKitView")
                videoView.apply {
                    // Try calling this later in LaunchedEffect
                }
            },
            modifier = modifier,
            update = { view ->
                // Update if needed
                view.videoGravity = videoGravity
            }
        )
        
        // Start the effect to attach the stream after the View is created
        LaunchedEffect(videoView, stream) {
            Log.d(TAG, "LaunchedEffect: Attaching stream to view")
            videoView.attachStream(stream)
            isViewReady = true
        }
    }
}
