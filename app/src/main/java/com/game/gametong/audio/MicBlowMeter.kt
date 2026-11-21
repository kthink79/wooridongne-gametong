package com.game.gametong.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 마이크 입력의 세기를 0~1로 재는 도구. 권한이 없거나 기기가 막으면 [start] 가 false 를 돌려주고,
 * 화면은 스와이프·탭 대체 조작으로 그대로 플레이할 수 있다.
 */
class MicBlowMeter {
    private var record: AudioRecord? = null
    private var thread: Thread? = null

    @Volatile
    var level: Float = 0f
        private set

    @Volatile
    var peak: Float = 0f
        private set

    @Volatile
    var running: Boolean = false
        private set

    fun hasPermission(context: android.content.Context): Boolean =
        context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (running) return true
        return try {
            val sampleRate = 16000
            val minBuf = AudioRecord.getMinBufferSize(
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
            )
            val bufferSize = maxOf(minBuf, sampleRate)
            val rec = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize,
            )
            if (rec.state != AudioRecord.STATE_INITIALIZED) {
                rec.release()
                return false
            }
            rec.startRecording()
            record = rec
            running = true
            thread = Thread {
                val buffer = ShortArray(sampleRate / 20)
                while (running) {
                    val read = try {
                        rec.read(buffer, 0, buffer.size)
                    } catch (t: Throwable) {
                        -1
                    }
                    if (read > 0) {
                        var sum = 0.0
                        var maxAbs = 0
                        for (i in 0 until read) {
                            val v = abs(buffer[i].toInt())
                            if (v > maxAbs) maxAbs = v
                            sum += v.toDouble() * v.toDouble()
                        }
                        val rms = sqrt(sum / read) / 32768.0
                        val amplitude = (rms * 3.2).toFloat().coerceIn(0f, 1f)
                        level = level * 0.55f + amplitude * 0.45f
                        if (level > peak) peak = level
                    } else if (read < 0) {
                        break
                    }
                }
            }.also { it.start() }
            true
        } catch (t: Throwable) {
            stop()
            false
        }
    }

    fun resetPeak() {
        peak = 0f
    }

    fun stop() {
        running = false
        thread?.interrupt()
        thread = null
        try {
            record?.stop()
        } catch (_: Throwable) {
        }
        try {
            record?.release()
        } catch (_: Throwable) {
        }
        record = null
        level = 0f
    }
}
