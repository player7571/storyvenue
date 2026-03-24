package com.storyvenue.app.voice

import android.media.AudioAttributes
import android.media.MediaPlayer

interface AudioReplyPlayer {
    fun play(
        url: String,
        onStarted: () -> Unit,
        onCompleted: () -> Unit,
        onError: (String) -> Unit,
    )

    fun stop()

    fun release()
}

class MediaPlayerAudioReplyPlayer : AudioReplyPlayer {
    private var mediaPlayer: MediaPlayer? = null

    override fun play(
        url: String,
        onStarted: () -> Unit,
        onCompleted: () -> Unit,
        onError: (String) -> Unit,
    ) {
        stop()

        val player = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .build(),
            )
        }

        mediaPlayer = player

        player.setOnPreparedListener {
            onStarted()
            it.start()
        }
        player.setOnCompletionListener {
            stop()
            onCompleted()
        }
        player.setOnErrorListener { _, _, _ ->
            stop()
            onError("assistant 오디오를 재생하지 못했습니다. 서버 주소와 네트워크를 확인해 주세요.")
            true
        }

        runCatching {
            player.setDataSource(url)
            player.prepareAsync()
        }.onFailure {
            stop()
            onError("assistant 오디오를 준비하지 못했습니다. 서버 주소를 확인해 주세요.")
        }
    }

    override fun stop() {
        mediaPlayer?.let { player ->
            runCatching { player.stop() }
            runCatching { player.reset() }
            runCatching { player.release() }
        }
        mediaPlayer = null
    }

    override fun release() {
        stop()
    }
}
