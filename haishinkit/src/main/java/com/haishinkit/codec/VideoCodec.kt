package com.haishinkit.codec

import android.content.Context
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.os.Build
import android.os.Bundle
import android.util.Size
import com.haishinkit.graphics.PixelTransform
import com.haishinkit.graphics.VideoGravity
import com.haishinkit.util.FeatureUtil
import kotlin.properties.Delegates

/**
 * The VideoCodec class provides methods for encode or decode for video.
 */
class VideoCodec(applicationContext: Context) : Codec() {
    @Suppress("UNUSED")
    data class Setting(private val codec: VideoCodec? = null) : Codec.Setting(codec) {
        /**
         * Specifies the video codec profile.
         */
        var profileLevel: VideoCodecProfileLevel by Delegates.observable(DEFAULT_PROFILE_LEVEL) { _, oldValue, newValue ->
            if (oldValue == newValue) return@observable
            if (!CodecCapabilities.isCodecSupportedByType(MODE_ENCODE, newValue.mime)) {
                throw IllegalArgumentException("Unsupported mime type for ${newValue.mime}.")
            }
            codec?.outputMimeType = newValue.mime
            codec?.profile = newValue.profile
            codec?.level = newValue.level
        }

        /**
         * Specifies the width resolution for a video output.
         */
        var width: Int by Delegates.observable(DEFAULT_WIDTH) { _, oldValue, newValue ->
            if (oldValue != newValue) {
                codec?.width = if (newValue % 2 == 0) newValue else newValue - 1
            }
        }

        /**
         * Specifies the height resolution for a video output.
         */
        var height: Int by Delegates.observable(DEFAULT_HEIGHT) { _, oldValue, newValue ->
            if (oldValue != newValue) {
                codec?.height = if (newValue % 2 == 0) newValue else newValue - 1
            }
        }

        /**
         * Specifies the bitrate for a video output.
         */
        var bitRate: Int by Delegates.observable(DEFAULT_BIT_RATE) { _, oldValue, newValue ->
            if (oldValue != newValue) {
                codec?.bitRate = newValue
            }
        }

        /**
         * Specifies the IFrameInterval for a video output.
         */
        var IFrameInterval: Int by Delegates.observable(DEFAULT_I_FRAME_INTERVAL) { _, oldValue, newValue ->
            if (oldValue != newValue) {
                codec?.IFrameInterval = newValue
            }
        }

        /**
         * Specifies the frameRate of a video format in frames/sec.
         */
        var frameRate: Int by Delegates.observable(DEFAULT_FRAME_RATE) { _, oldValue, newValue ->
            if (oldValue != newValue) {
                codec?.frameRate = newValue
            }
        }

        /**
         * Specifies the scaling mode for a video output.
         */
        var videoGravity: VideoGravity by Delegates.observable(DEFAULT_VIDEO_GRAVITY) { _, oldValue, newValue ->
            if (oldValue != newValue) {
                codec?.pixelTransform?.videoGravity = newValue
            }
        }
    }

    /**
     * Specifies the bitrate for a video output.
     */
    var bitRate = DEFAULT_BIT_RATE
        set(value) {
            field = value
            if (FeatureUtil.isEnabled(FeatureUtil.FEATURE_BITRATE_CHANGE) && isRunning.get()) {
                val bundle =
                    Bundle().apply {
                        putInt(MediaCodec.PARAMETER_KEY_VIDEO_BITRATE, value)
                    }
                codec?.setParameters(bundle)
            }
        }

    /**
     * Specifies the frame rate of a video format in frames/sec.
     */
    var frameRate = DEFAULT_FRAME_RATE
        set(value) {
            field = value
            pixelTransform.frameRate = value
        }

    /**
     * Specifies the IFrameInterval for a video output.
     */
    var IFrameInterval = DEFAULT_I_FRAME_INTERVAL

    /**
     * Specifies the width resolution for a video output.
     */
    var width = DEFAULT_WIDTH

    /**
     * Specifies the height resolution for a video output.
     */
    var height = DEFAULT_HEIGHT

    /**
     * Specifies the profile for a video output.
     */
    var profile = DEFAULT_PROFILE_LEVEL.profile

    /**
     * Specifies the profile-level for a video outout.
     */
    var level = DEFAULT_PROFILE_LEVEL.level

    /**
     * The pixel transform instance.
     */
    val pixelTransform: PixelTransform by lazy {
        PixelTransform.create(applicationContext).apply {
            videoGravity = DEFAULT_VIDEO_GRAVITY
            frameRate = DEFAULT_FRAME_RATE
        }
    }

    override var inputMimeType = MediaFormat.MIMETYPE_VIDEO_RAW
    override var outputMimeType = DEFAULT_PROFILE_LEVEL.mime

    override var codec: MediaCodec?
        get() = super.codec
        set(value) {
            if (value == null) {
                pixelTransform.surface = null
            }
            super.codec = value
        }

    /**
     * Specifies the color format for a surface.
     */
    private var colorFormat = DEFAULT_COLOR_FORMAT

    override fun createMediaFormat(mime: String): MediaFormat {
        return MediaFormat.createVideoFormat(mime, width, height).apply {
            if (mode == MODE_ENCODE) {
                setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
                setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
                setInteger(MediaFormat.KEY_CAPTURE_RATE, frameRate)
                setInteger(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 1000000 / frameRate)
                setInteger(MediaFormat.KEY_CHANNEL_COUNT, 1)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFrameInterval)
                setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat)
            } else {
                if (Build.VERSION_CODES.R <= Build.VERSION.SDK_INT) {
                    setInteger(MediaFormat.KEY_LOW_LATENCY, 1)
                }
            }
            setInteger(MediaFormat.KEY_PROFILE, profile)
            if (Build.VERSION_CODES.M <= Build.VERSION.SDK_INT) {
                setInteger(MediaFormat.KEY_LEVEL, level)
            } else {
                setInteger("level", level)
            }
        }
    }

    override fun configure(codec: MediaCodec) {
        super.configure(codec)
        if (mode == MODE_ENCODE) {
            pixelTransform.imageExtent = Size(width, height)
            pixelTransform.surface = codec.createInputSurface()
        }
    }

    companion object {
        val DEFAULT_PROFILE_LEVEL = VideoCodecProfileLevel.H264_BASELINE_3_2
        const val DEFAULT_BIT_RATE = 640 * 1000
        const val DEFAULT_FRAME_RATE = 30
        const val DEFAULT_I_FRAME_INTERVAL = 2
        const val DEFAULT_WIDTH = 854
        const val DEFAULT_HEIGHT = 480
        const val DEFAULT_COLOR_FORMAT = MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
        val DEFAULT_VIDEO_GRAVITY = VideoGravity.RESIZE_ASPECT

        private val TAG = VideoCodec::class.java.simpleName
    }
}
