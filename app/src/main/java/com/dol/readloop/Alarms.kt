package com.dol.readloop

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

object Alarms {
    const val ACTION = "com.dol.readloop.SLOT"

    fun nextOccurrence(hour: Int, from: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = from
        cal.set(Calendar.HOUR_OF_DAY, hour)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        if (cal.timeInMillis <= from + 2000L) cal.add(Calendar.DAY_OF_YEAR, 1)
        return cal.timeInMillis
    }

    private fun pi(c: Context, hour: Int): PendingIntent =
        PendingIntent.getBroadcast(
            c,
            100 + hour,
            Intent(c, SlotReceiver::class.java).apply {
                action = ACTION
                putExtra("hour", hour)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    /** Schedules the next exact fire time for every slot. Idempotent — safe to call often. */
    fun scheduleAll(c: Context) {
        val am = c.getSystemService(AlarmManager::class.java) ?: return
        for (h in Slots.HOURS) {
            val p = pi(c, h)
            am.cancel(p)
            val at = nextOccurrence(h)
            try {
                if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p)
                } else {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p)
                }
            } catch (e: SecurityException) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, p)
            }
        }
    }
}
