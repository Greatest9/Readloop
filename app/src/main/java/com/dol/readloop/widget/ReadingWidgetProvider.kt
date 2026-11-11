package com.dol.readloop.widget

import android.app.NotificationManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.widget.RemoteViews
import com.dol.readloop.Alarms
import com.dol.readloop.Days
import com.dol.readloop.DoneReceiver
import com.dol.readloop.MainActivity
import com.dol.readloop.R
import com.dol.readloop.Slots
import com.dol.readloop.Store

class ReadingWidgetProvider : AppWidgetProvider() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == DoneReceiver.ACTION) {
            val hour = intent.getIntExtra(DoneReceiver.EXTRA_HOUR, -1)
            if (hour in Slots.HOURS) {
                Store.markDone(context, hour)
                context.getSystemService(NotificationManager::class.java)?.cancel(hour)
            }
            refreshAll(context)
            return
        }
        super.onReceive(context, intent)
    }

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        for (id in ids) mgr.updateAppWidget(id, build(context))
    }

    companion object {

        fun refreshAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context) ?: return
            val ids = mgr.getAppWidgetIds(
                ComponentName(context, ReadingWidgetProvider::class.java)
            )
            if (ids.isEmpty()) return
            val views = build(context)
            for (id in ids) mgr.updateAppWidget(id, views)
        }

        private fun build(context: Context): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_reading)

            val now = System.currentTimeMillis()
            val day = Days.today(now)
            val cur = Slots.currentSlot(now)
            val nxt = Slots.nextSlot(now)
            val block = Store.block(context, day, cur)

            val total = Slots.HOURS.size
            val done = Store.doneCount(context).coerceAtMost(total)

            views.setTextViewText(
                R.id.w_label,
                Days.SHORT[day] + " \u00b7 NOW " + Slots.label(cur)
            )
            views.setTextViewText(R.id.w_streak, "\uD83D\uDD25 " + Store.streak(context))
            views.setTextViewText(R.id.w_subject, block.subject)
            views.setTextViewText(R.id.w_detail, block.detail)
            views.setTextViewText(R.id.w_time, "until " + Slots.label((cur + 3) % 24))
            views.setTextViewText(R.id.w_ring_text, "$done/$total")
            views.setProgressBar(R.id.w_progress, total, done, false)

            val d = context.resources.displayMetrics.density
            views.setImageViewBitmap(
                R.id.w_ring,
                ring(
                    (62 * d).toInt(),
                    done.toFloat() / total.toFloat(),
                    0xFFE2E8F0.toInt(),
                    0xFF16A34A.toInt(),
                    6.5f * d
                )
            )

            val mins = ((Alarms.nextOccurrence(nxt, now) - now) / 60000L).coerceAtLeast(0)
            views.setTextViewText(
                R.id.w_next,
                "next " + Slots.label(nxt) + " \u00b7 " + Slots.hhmm(mins)
            )

            val open = PendingIntent.getActivity(
                context, 900,
                Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, open)

            val donePi = PendingIntent.getBroadcast(
                context, 901,
                Intent(context, ReadingWidgetProvider::class.java).apply {
                    action = DoneReceiver.ACTION
                    putExtra(DoneReceiver.EXTRA_HOUR, cur)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.w_done, donePi)

            return views
        }

        private fun ring(
            sizePx: Int,
            fraction: Float,
            trackColor: Int,
            accentColor: Int,
            strokePx: Float
        ): Bitmap {
            val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bmp)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = strokePx
            paint.strokeCap = Paint.Cap.ROUND

            val inset = strokePx / 2f + 1f
            val rect = RectF(inset, inset, sizePx - inset, sizePx - inset)

            paint.color = trackColor
            canvas.drawArc(rect, 0f, 360f, false, paint)

            val f = fraction.coerceIn(0f, 1f)
            if (f > 0.001f) {
                paint.color = accentColor
                canvas.drawArc(rect, -90f, 360f * f, false, paint)
            }
            return bmp
        }
    }
}
