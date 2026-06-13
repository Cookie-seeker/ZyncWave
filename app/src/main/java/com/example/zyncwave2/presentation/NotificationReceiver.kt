package com.example.zyncwave2.presentation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.zyncwave2.data.PlayerState

class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val player = PlayerState.exoPlayer ?: return
        when (intent.action) {
            ACTION_PLAY_PAUSE -> if (player.isPlaying) player.pause() else player.play()
            ACTION_NEXT -> {
                val list = PlayerState.songsList.value
                if (list.isEmpty()) return
                val next = (PlayerState.currentIndex.value + 1) % list.size
                val song = list.getOrNull(next) ?: return
                PlayerState.currentIndex.value = next
                PlayerState.currentSong.value = song
                player.seekTo(next, 0L)
                player.play()
            }
            ACTION_PREV -> {
                val list = PlayerState.songsList.value
                if (list.isEmpty()) return
                val curr = PlayerState.currentIndex.value
                val prev = if (curr - 1 < 0) list.size - 1 else curr - 1
                val song = list.getOrNull(prev) ?: return
                PlayerState.currentIndex.value = prev
                PlayerState.currentSong.value = song
                player.seekTo(prev, 0L)
                player.play()
            }
            ACTION_FAVORITE -> {
                val song = PlayerState.currentSong.value ?: return
                com.example.zyncwave2.data.FavoritesManager.toggleFavorite(context, song.id)
                // Forzar actualización de notificación
                context.sendBroadcast(Intent(ACTION_UPDATE_NOTIF).setPackage(context.packageName))
            }
            ACTION_CLOSE -> {
                player.pause()
                context.startService(
                    Intent(context, MusicService::class.java).apply {
                        action = "com.example.zyncwave2.CLOSE"
                    }
                )
            }
        }
    }

    companion object {
        const val ACTION_PLAY_PAUSE  = "com.example.zyncwave2.PLAY_PAUSE"
        const val ACTION_NEXT        = "com.example.zyncwave2.NEXT"
        const val ACTION_PREV        = "com.example.zyncwave2.PREV"
        const val ACTION_FAVORITE    = "com.example.zyncwave2.TOGGLE_FAVORITE"
        const val ACTION_CLOSE       = "com.example.zyncwave2.CLOSE"
        const val ACTION_UPDATE_NOTIF = "com.example.zyncwave2.UPDATE_NOTIF"
    }
}