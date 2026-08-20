package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.sin

/**
 * High-performance, zero-latency synthesizer for GSM UnlockTool audio feedback.
 * Generates custom synthesized chimes for USB plug/unplug, operation start, stop, and done.
 */
object ToolSoundManager {
    private val scope = CoroutineScope(Dispatchers.Default)
    private const val SAMPLE_RATE = 44100

    /**
     * USB Port Connected (Device plugged in): Rising two-tone chime (C5 -> G5)
     */
    fun playUsbConnected() {
        scope.launch {
            playSequence(listOf(523.25 to 75, 783.99 to 130))
        }
    }

    /**
     * USB Port Disconnected (Device unplugged): Falling two-tone chime (G5 -> C5)
     */
    fun playUsbDisconnected() {
        scope.launch {
            playSequence(listOf(783.99 to 75, 523.25 to 130))
        }
    }

    /**
     * Operation Start (Flash/Backup/Service starting): High crisp activation tone (A5 -> D6)
     */
    fun playOperationStart() {
        scope.launch {
            playSequence(listOf(880.0 to 60, 1174.66 to 110))
        }
    }

    /**
     * Operation Stop (Operation cancelled/halted/error): Double low alert tone (440Hz -> 330Hz)
     */
    fun playOperationStop() {
        scope.launch {
            playSequence(listOf(440.0 to 90, 329.63 to 150))
        }
    }

    /**
     * Operation Done / Success (Operation completed 100%): 4-note victory chord (C5 -> E5 -> G5 -> C6)
     */
    fun playOperationDone() {
        scope.launch {
            playSequence(listOf(523.25 to 70, 659.25 to 70, 783.99 to 70, 1046.50 to 180))
        }
    }

    private fun playSequence(notes: List<Pair<Double, Int>>) {
        try {
            var totalSamples = 0
            for ((_, durationMs) in notes) {
                totalSamples += (SAMPLE_RATE * durationMs) / 1000
            }
            val buffer = ShortArray(totalSamples)
            var offset = 0

            for ((freq, durationMs) in notes) {
                val samples = (SAMPLE_RATE * durationMs) / 1000
                val fadeCount = minOf(120, samples / 4)
                for (i in 0 until samples) {
                    val angle = 2.0 * PI * i * freq / SAMPLE_RATE
                    // Apply smooth Hann-like linear envelope to eliminate audio clicks
                    val envelope = when {
                        i < fadeCount -> i.toDouble() / fadeCount
                        i > samples - fadeCount -> (samples - i).toDouble() / fadeCount
                        else -> 1.0
                    }
                    val sampleValue = (sin(angle) * Short.MAX_VALUE * 0.72 * envelope).toInt()
                    buffer[offset + i] = sampleValue.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
                }
                offset += samples
            }

            val audioTrack = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(buffer.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()

            audioTrack.write(buffer, 0, buffer.size)
            audioTrack.play()

            // Sleep coroutine just enough to complete playback before releasing
            val sleepDuration = (totalSamples * 1000L / SAMPLE_RATE) + 60
            Thread.sleep(sleepDuration)
            audioTrack.stop()
            audioTrack.release()
        } catch (_: Throwable) {
            // Gracefully ignore if audio device is unavailable
        }
    }
}
