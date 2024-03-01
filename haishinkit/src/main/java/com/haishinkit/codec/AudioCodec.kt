package com.haishinkit.codec

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.util.Log
import java.nio.ByteBuffer
import kotlin.properties.Delegates

/**
 * The AudioCodec translate audio data to another format.
 */
class AudioCodec : Codec(MIME) {
    @Suppress("unused")
    data class Setting(private var codec: AudioCodec? = null) : Codec.Setting(codec) {
        /**
         * The channel of audio output.
         */
        var channelCount: Int by Delegates.observable(DEFAULT_CHANNEL_COUNT) { _, oldValue, newValue ->
            if (oldValue != newValue) {
                codec?.channelCount = newValue
            }
        }

        /**
         * The bitRate of audio output.
         */
        var bitRate: Int by Delegates.observable(DEFAULT_BIT_RATE) { _, oldValue, newValue ->
            if (oldValue != newValue) {
                codec?.bitRate = newValue
            }
        }

        /**
         * The sampleRate of audio output.
         */
        var sampleRate: Int by Delegates.observable(DEFAULT_SAMPLE_RATE) { _, oldValue, newValue ->
            if (oldValue != newValue) {
                codec?.sampleRate = newValue
            }
        }
    }

    var sampleRate = DEFAULT_SAMPLE_RATE
        set(value) {
            field = value
            buffer.sampleRate = value
        }
    var channelCount = DEFAULT_CHANNEL_COUNT
    var bitRate = DEFAULT_BIT_RATE
    var aacProfile = DEFAULT_AAC_PROFILE
    private var buffer = AudioCodecBuffer()

    fun append(byteBuffer: ByteBuffer) {
        if (!isRunning.get()) return
        buffer.append(byteBuffer)
    }

    override fun onInputBufferAvailable(
        codec: MediaCodec,
        index: Int,
    ) {
        if (mode == Mode.ENCODE) {
            try {
                val inputBuffer = codec.getInputBuffer(index) ?: return
                val result = buffer.render(inputBuffer)
                codec.queueInputBuffer(
                    index,
                    0,
                    result,
                    buffer.presentationTimestamp,
                    0,
                )
            } catch (e: IllegalStateException) {
                Log.w(TAG, e)
            }
        } else {
            super.onInputBufferAvailable(codec, index)
        }
    }

    override fun createOutputFormat(): MediaFormat {
        return MediaFormat.createAudioFormat(MIME, sampleRate, channelCount).apply {
            if (mode == Mode.ENCODE) {
                setInteger(MediaFormat.KEY_AAC_PROFILE, aacProfile)
                setInteger(MediaFormat.KEY_BIT_RATE, bitRate)
            } else {
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, DEFAULT_KEY_MAX_INPUT_SIZE)
            }
        }
    }

    override fun dispose() {
        buffer.clear()
        super.dispose()
    }

    companion object {
        const val MIME = MIME_AUDIO_MP4A

        const val DEFAULT_SAMPLE_RATE: Int = 44100
        const val DEFAULT_CHANNEL_COUNT: Int = 1
        const val DEFAULT_BIT_RATE: Int = 64000
        const val DEFAULT_AAC_PROFILE = MediaCodecInfo.CodecProfileLevel.AACObjectLC
        const val DEFAULT_KEY_MAX_INPUT_SIZE = 1024 * 2

        private val TAG = AudioCodec::class.java.simpleName
    }
}
