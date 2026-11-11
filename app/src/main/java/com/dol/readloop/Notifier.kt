package com.dol.readloop

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon

object Notifier {
    const val CHANNEL = "reading_cycle"

    fun ensureChannel(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL) != null) return
        val ch = NotificationChannel(CHANNEL, "Reading reminders", NotificationManager.IMPORTANCE_HIGH)
        ch.description = "Your 3-hour reading cycle"
        ch.enableVibration(true)
        ch.vibrationPattern = longArrayOf(0, 250, 150, 250)
        ch.setShowBadge(true)
        nm.createNotificationChannel(ch)
    }

    fun notifySlot(c: Context, hour: Int) {
        ensureChannel(c)
        val nm = c.getSystemService(NotificationManager::class.java) ?: return

        val day = Days.today()
        val b = Store.block(c, day, hour)

        val open = PendingIntent.getActivity(
            c, 500 + hour,
            Intent(c, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val done = PendingIntent.getBroadcast(
            c, 600 + hour,
            Intent(c, DoneReceiver::class.java).apply {
                action = DoneReceiver.ACTION
                putExtra(DoneReceiver.EXTRA_HOUR, hour)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val window = Slots.label(hour) + " \u2013 " + Slots.label((hour + 3) % 24)

        val n = Notification.Builder(c, CHANNEL)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(b.subject)
            .setContentText(b.detail + "  \u00b7  " + Slots.label(hour))
            .setStyle(
                Notification.BigTextStyle().bigText(
                    b.detail + "\n\n" + Days.name(day) + " \u00b7 block " + window
                )
            )
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setPriority(Notification.PRIORITY_HIGH)
            .addAction(
                Notification.Action.Builder(
                    Icon.createWithResource(c, R.drawable.ic_check_white),
                    "Mark done",
                    done
                ).build()
            )
            .build()

        nm.notify(hour, n)
    }
}
