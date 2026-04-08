package com.jarvis.app.speech

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import kotlinx.coroutines.*

/**
 * Records raw PCM audio using Android's AudioRecord API.
 * Records for as long as the user holds the bubble — no auto-stop,
 * no silence detection. Returns WAV bytes on stop.
 */
class AudioRecorder(private val context: Context) {

    companion object {
        private const val TAG = "JarvisAudioRecorder"
        private const val SAMPLE_RATE = 16000      // 16kHz — optimal for Whisper
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }

    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var pcmOutputStream: ByteArrayOutputStream? = null

    var isRecording: Boolean = false
        private set

    fun start(): Boolean {
        if (isRecording) return false

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "No RECORD_AUDIO permission")
            return false
        }

        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
            Log.e(TAG, "Invalid buffer size: $bufferSize")
            return false
        }

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize * 2
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord failed to initialize")
                audioRecord?.release()
                audioRecord = null
                return false
            }

            pcmOutputStream = ByteArrayOutputStream()
            isRecording = true
            audioRecord?.startRecording()

            recordingJob = scope.launch {
                val buffer = ByteArray(bufferSize)
                Log.d(TAG, "Recording started (${SAMPLE_RATE}Hz, mono, 16-bit)")

                while (isRecording && isActive) {
                    val bytesRead = audioRecord?.read(buffer, 0, buffer.size) ?: -1
                    if (bytesRead > 0) {
                        pcmOutputStream?.write(buffer, 0, bytesRead)
                    }
                }

                Log.d(TAG, "Recording loop ended")
            }

            return true
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception starting recording", e)
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Error starting recording", e)
            return false
        }
    }

    fun stop(): ByteArray? {
        if (!isRecording) return null
        isRecording = false

        try {
            audioRecord?.stop()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping AudioRecord", e)
        }

        runBlocking { recordingJob?.cancelAndJoin() }

        audioRecord?.release()
        audioRecord = null

        val pcmData = pcmOutputStream?.toByteArray()
        pcmOutputStream = null

        if (pcmData == null || pcmData.isEmpty()) {
            Log.w(TAG, "No audio data recorded")
            return null
        }

        Log.d(TAG, "Recorded ${pcmData.size} bytes of PCM (${pcmData.size / (SAMPLE_RATE * 2.0)}s)")

        return createWav(pcmData)
    }

    fun destroy() {
        isRecording = false
        recordingJob?.cancel()
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error in destroy", e)
        }
        audioRecord = null
        pcmOutputStream = null
        scope.cancel()
    }

    private fun createWav(pcmData: ByteArray): ByteArray {
        val totalDataLen = pcmData.size + 36
        val byteRate = SAMPLE_RATE * 1 * 16 / 8
        val blockAlign = 1 * 16 / 8

        val out = ByteArrayOutputStream()
        val dos = DataOutputStream(out)

        dos.writeBytes("RIFF")
        dos.writeIntLE(totalDataLen)
        dos.writeBytes("WAVE")

        dos.writeBytes("fmt ")
        dos.writeIntLE(16)
        dos.writeShortLE(1)
        dos.writeShortLE(1)
        dos.writeIntLE(SAMPLE_RATE)
        dos.writeIntLE(byteRate)
        dos.writeShortLE(blockAlign)
        dos.writeShortLE(16)

        dos.writeBytes("data")
        dos.writeIntLE(pcmData.size)
        dos.write(pcmData)

        dos.flush()
        return out.toByteArray()
    }

    private fun DataOutputStream.writeIntLE(value: Int) {
        write(value and 0xFF)
        write((value shr 8) and 0xFF)
        write((value shr 16) and 0xFF)
        write((value shr 24) and 0xFF)
    }

    private fun DataOutputStream.writeShortLE(value: Int) {
        write(value and 0xFF)
        write((value shr 8) and 0xFF)
    }
}
