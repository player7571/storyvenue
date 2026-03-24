package com.storyvenue.app.voice

import android.content.Context
import java.io.File

class VoiceRecordingFileStore {
    fun createTempFile(context: Context): File {
        val directory = File(context.cacheDir, "voice-recordings").apply {
            mkdirs()
        }

        return File(directory, "voice_turn_${System.currentTimeMillis()}.m4a")
    }
}
