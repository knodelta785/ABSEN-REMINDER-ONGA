package com.example.absenreminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

private fun typeOf(i: Intent): Type? =
    i.getStringExtra("type")?.let { runCatching { Type.valueOf(it) }.getOrNull() }

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val t = typeOf(i) ?: return Reminder.refresh(c)
        Reminder.onFire(c, t)
    }
}

class ConfirmReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        typeOf(i)?.let { Reminder.confirm(c, it) }
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        Reminder.refresh(c)
    }
}
