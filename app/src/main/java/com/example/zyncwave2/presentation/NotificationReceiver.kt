package com.example.zyncwave2.presentation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.zyncwave2.data.PlayerState

class NotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {

            ACTION_PLAY_PAUSE ->
                PlayerState.pendingCommand.value = PlayerState.PlayerCommand.PlayPause
            ACTION_NEXT ->
                PlayerState.pendingCommand.value = PlayerState.PlayerCommand.Next
            ACTION_PREV ->
                PlayerState.pendingCommand.value = PlayerState.PlayerCommand.Prev
            ACTION_FAVORITE ->
                PlayerState.pendingCommand.value = PlayerState.PlayerCommand.ToggleFavorite


            ACTION_CLOSE -> {
                PlayerState.exoPlayer?.pause()
                context.startService(
                    Intent(context, MusicService::class.java).apply {
                        action = "com.example.zyncwave2.CLOSE"
                    }
                )
            }
        }
    }

    companion object {
        const val ACTION_PLAY_PAUSE   = "com.example.zyncwave2.PLAY_PAUSE"
        const val ACTION_NEXT         = "com.example.zyncwave2.NEXT"
        const val ACTION_PREV         = "com.example.zyncwave2.PREV"
        const val ACTION_FAVORITE     = "com.example.zyncwave2.TOGGLE_FAVORITE"
        const val ACTION_CLOSE        = "com.example.zyncwave2.CLOSE"
        const val ACTION_UPDATE_NOTIF = "com.example.zyncwave2.UPDATE_NOTIF"
    }
}