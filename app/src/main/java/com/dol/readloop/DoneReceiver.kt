package com.dol.readloop

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class DoneReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val hour = intent.getIntExtra(EXTRA_HOUR, -1)
        if (hour in Slots.HOURS) {
            Store.markDone(context, hour)
            context.getSystemService(NotificationManager::class.java)?.cancel(hour)
            IconTiers.apply(context)
            com.dol.readloop.widget.ReadingWidgetProvider.refreshAll(context)
        }
    }

    companion object {
        const val ACTION = "com.dol.readloop.DONE"
        const val EXTRA_HOUR = "hour"
    }
}
