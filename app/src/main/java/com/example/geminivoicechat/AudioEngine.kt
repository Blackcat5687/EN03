package com.example.geminivoicechat

import android.annotation.SuppressLint
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.util.Base64
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive

/**
 * Handles microphone capture and speaker playback using the raw PCM formats
 * that the Gemini Live API expects:
 *   - Input to the model:  16-bit PCM, 16 kHz, mono
 *   - Output from the model: 16-bit PCM, 24 kHz, mono
 *
 * The engine does not talk to the network itself — it just captures mic audio
 * as base64 chunks (via onAudioChunk) and exposes playChunk() to play back
 * whatever audio the model sends.
 */
class AudioEngine(
    private val onAudioChunk: (String) -> Unit
) {
    private val inputSampleRate = 16000
    private val outputSampleRate = 24000

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null
    private var recordJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    private var isRecording = false

    @SuppressLint("MissingPermission") // caller is required to have checked RECORD_AUDIO first
    fun startRecording() {
        if (isRecording) return

        val minBufSize = AudioRecord.getMinBufferSize(
            inputSampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = maxOf(minBufSize, 4096)

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            inputSampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        audioRecord?.startRecording()
        isRecording = true

        recordJob = scope.launch {
            val buffer = ByteArray(bufferSize)
            while (isActive && isRecording) {
                val read = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                if (read > 0) {
                    val chunk = if (read == buffer.size) buffer else buffer.copyOf(read)
                    val base64Chunk = Base64.encodeToString(chunk, Base64.NO_WRAP)
                    onAudioChunk(base64Chunk)
                }
            }
        }
    }

    fun stopRecording() {
        isRecording = false
        recordJob?.cancel()
        recordJob = null
        audioRecord?.let {
            try {
                it.stop()
            } catch (_: Exception) {
                // Recorder may already be stopped; safe to ignore.
            }
            it.release()
        }
        audioRecord = null
    }

    /** Prepares the AudioTrack used for playing back model responses. Call once before playChunk(). */
    fun preparePlayback() {
        if (audioTrack != null) return

        val minBufSize = AudioTrack.getMinBufferSize(
            outputSampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = maxOf(minBufSize, 4096)

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION_SIGNALLING.let {
                        AudioAttributes.USAGE_MEDIA
                    })
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(outputSampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(bufferSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        audioTrack?.play()
    }

    /** Feeds one base64-encoded PCM chunk (as sent by the model) to the speaker. */
    fun playChunk(base64Pcm: String) {
        val bytes = Base64.decode(base64Pcm, Base64.NO_WRAP)
        audioTrack?.write(bytes, 0, bytes.size)
    }

    /** Stops and clears any audio queued for playback — used when the user barges in. */
    fun clearPlaybackQueue() {
        audioTrack?.pause()
        audioTrack?.flush()
        audioTrack?.play()
    }

    fun stopPlayback() {
        audioTrack?.let {
            try {
                it.stop()
            } catch (_: Exception) {
            }
            it.release()
        }
        audioTrack = null
    }

    fun release() {
        stopRecording()
        stopPlayback()
    }
}
