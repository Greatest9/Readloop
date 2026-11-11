package com.dol.readloop

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Notifier.ensureChannel(context)
        Alarms.scheduleAll(context)
        com.dol.readloop.widget.ReadingWidgetProvider.refreshAll(context)
    }
}
