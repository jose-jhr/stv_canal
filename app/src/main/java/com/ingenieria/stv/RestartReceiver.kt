package com.ingenieria.stv

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent


class RestartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val restartIntent = Intent(context, MainActivity::class.java)
        restartIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(restartIntent)
    }
}