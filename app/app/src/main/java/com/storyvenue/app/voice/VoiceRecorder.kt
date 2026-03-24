package com.storyvenue.app.voice

import android.media.MediaRecorder
import java.io.File

interface VoiceRecorder {
    fun start(outputFile: File): Result<Unit>
    fun stop(): Result<File>
    fun discard()
}

@Suppress("DEPRECATION")
class MediaRecorderVoiceRecorder : VoiceRecorder {
    private var mediaRecorder: MediaRecorder? = null
    private var outputFile: File? = null

    override fun start(outputFile: File): Result<Unit> {
        discard()

        return runCatching {
            // TODO: Verify MediaRecorder behavior and audio quality on a real device.
            val recorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }

            this.outputFile = outputFile
            mediaRecorder = recorder
        }.onFailure {
            outputFile.delete()
            discard()
        }
    }

    override fun stop(): Result<File> {
        val recorder = mediaRecorder
            ?: return Result.failure(IllegalStateException("녹음이 시작되지 않았습니다."))
        val file = outputFile
            ?: return Result.failure(IllegalStateException("임시 녹음 파일 경로가 없습니다."))

        return runCatching {
            recorder.stop()
            recorder.release()
            file
        }.onFailure {
            file.delete()
            runCatching { recorder.release() }
        }.also {
            mediaRecorder = null
            outputFile = null
        }
    }

    override fun discard() {
        mediaRecorder?.let { recorder ->
            runCatching { recorder.reset() }
            runCatching { recorder.release() }
        }
        mediaRecorder = null

        outputFile?.let { file ->
            if (file.exists()) {
                file.delete()
            }
        }
        outputFile = null
    }
}
