package com.dol.readloop

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class SlotReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val hour = intent.getIntExtra("hour", -1)
        if (hour in Slots.HOURS) {
            Notifier.notifySlot(context, hour)
        }
        // Self-healing: re-arm everything after each fire.
        Alarms.scheduleAll(context)
        IconTiers.apply(context)
        com.dol.readloop.widget.ReadingWidgetProvider.refreshAll(context)
    }
}
