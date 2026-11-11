package com.dol.readloop

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.AlertDialog
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {

    private lateinit var content: LinearLayout
    private var taps = 0
    private var firstTap = 0L
    private var askedNotif = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Notifier.ensureChannel(this)
        Store.prune(this)
        Alarms.scheduleAll(this)

        val scroll = ScrollView(this)
        scroll.setBackgroundColor(0xFFF6F8FA.toInt())
        content = LinearLayout(this)
        content.orientation = LinearLayout.VERTICAL
        content.setPadding(dp(20), dp(28), dp(20), dp(36))
        scroll.addView(content)
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        Alarms.scheduleAll(this)
        IconTiers.apply(this)
        render()
        askNotifOnce()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    // ------------------------------------------------------------------ render

    private fun render() {
        content.removeAllViews()

        val day = Days.today()

        val title = TextView(this).apply {
            text = "ReadLoop"
            textSize = 30f
            setTextColor(0xFF0F172A.toInt())
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
        }
        title.setOnClickListener { onSecretTap() }
        content.addView(title)

        content.addView(TextView(this).apply {
            text = Days.name(day) + "  \u00b7  your 3-hour reading cycle"
            textSize = 13f
            setTextColor(0xFF64748B.toInt())
            setPadding(0, dp(2), 0, dp(14))
        })

        content.addView(dayStrip(day))

        val chips = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        chips.addView(chip("\uD83D\uDD25 " + Store.streak(this) + " day streak"))
        chips.addView(chip(Store.doneCount(this).toString() + "/" + Slots.HOURS.size + " today"))
        content.addView(chips)

        if (Build.VERSION.SDK_INT >= 31) {
            val am = getSystemService(AlarmManager::class.java)
            if (am != null && !am.canScheduleExactAlarms()) content.addView(exactAlarmBanner())
        }

        val now = System.currentTimeMillis()
        val cur = Slots.currentSlot(now)
        content.addView(nowCard(day, cur))

        content.addView(sectionLabel("TODAY'S BLOCKS"))
        for (h in Slots.HOURS) content.addView(blockRow(day, h, cur))

        val nxt = Slots.nextSlot(now)
        val mins = ((Alarms.nextOccurrence(nxt, now) - now) / 60000L).coerceAtLeast(0)
        content.addView(sectionLabel("NEXT REMINDER"))
        content.addView(TextView(this).apply {
            text = Slots.label(nxt) + "   \u00b7   in " + Slots.hhmm(mins)
            textSize = 15f
            setTextColor(0xFF0F172A.toInt())
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(0, dp(2), 0, dp(10))
        })
        content.addView(TextView(this).apply {
            text = "Reminders repeat every 3 hours, around the clock. There is no off switch in this app."
            textSize = 11.5f
            setTextColor(0xFF94A3B8.toInt())
            setLineSpacing(dp(3).toFloat(), 1f)
        })
    }

    private fun dayStrip(today: Int): View {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        for (d in 0..6) {
            val isToday = d == today
            val tv = TextView(this).apply {
                text = Days.SHORT[d]
                textSize = 9.5f
                gravity = Gravity.CENTER
                letterSpacing = 0.06f
                setTextColor(if (isToday) Color.WHITE else 0xFF94A3B8.toInt())
                typeface = Typeface.create(
                    "sans-serif-medium",
                    if (isToday) Typeface.BOLD else Typeface.NORMAL
                )
                setPadding(dp(6), dp(5), dp(6), dp(5))
                background = resources.getDrawable(
                    if (isToday) R.drawable.bg_chip_selected else R.drawable.bg_chip,
                    theme
                )
            }
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            lp.rightMargin = if (d < 6) dp(4) else 0
            row.addView(tv, lp)
        }
        val rowLp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        rowLp.bottomMargin = dp(14)
        row.layoutParams = rowLp
        return row
    }

    private fun nowCard(day: Int, cur: Int): View {
        val b = Store.block(this, day, cur)
        val isDone = Store.isDone(this, cur)

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(16), dp(18), dp(16))
            background = resources.getDrawable(R.drawable.bg_card_now, theme)
        }
        val lp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        lp.topMargin = dp(14)
        card.layoutParams = lp

        card.addView(smallLabel("NOW READING  \u00b7  " + Slots.label(cur)))

        card.addView(TextView(this).apply {
            text = b.subject
            textSize = 21f
            setTextColor(0xFF0F172A.toInt())
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setPadding(0, dp(4), 0, 0)
        })

        card.addView(TextView(this).apply {
            text = b.detail
            textSize = 14f
            setTextColor(0xFF475569.toInt())
            setPadding(0, dp(4), 0, dp(12))
        })

        card.addView(primaryButton(
            if (isDone) "Completed \u2713   (tap to undo)" else "Mark as read"
        ) {
            if (Store.isDone(this, cur)) Store.unmarkDone(this, cur) else Store.markDone(this, cur)
            getSystemService(NotificationManager::class.java)?.cancel(cur)
            com.dol.readloop.widget.ReadingWidgetProvider.refreshAll(this)
            render()
        })

        return card
    }

    private fun blockRow(day: Int, hour: Int, currentHour: Int): View {
        val b = Store.block(this, day, hour)
        val isDone = Store.isDone(this, hour)
        val isCurrent = hour == currentHour

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(11), dp(12), dp(11))
            if (isCurrent) background = resources.getDrawable(R.drawable.bg_row_current, theme)
        }
        val lp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        lp.bottomMargin = dp(6)
        row.layoutParams = lp

        row.addView(TextView(this).apply {
            text = Slots.label(hour)
            textSize = 12f
            setTextColor(if (isCurrent) 0xFF16A34A.toInt() else 0xFF94A3B8.toInt())
            typeface = Typeface.create("monospace", Typeface.BOLD)
            width = dp(46)
        })

        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.addView(TextView(this).apply {
            text = b.subject
            textSize = 14.5f
            setTextColor(if (isDone) 0xFF94A3B8.toInt() else 0xFF0F172A.toInt())
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            if (isDone) paintFlags = paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        })
        col.addView(TextView(this).apply {
            text = b.detail
            textSize = 11.5f
            setTextColor(0xFF94A3B8.toInt())
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
        })
        row.addView(col, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        row.addView(TextView(this).apply {
            text = if (isDone) "\u2713" else "\u25CB"
            textSize = 16f
            setTextColor(if (isDone) 0xFF16A34A.toInt() else 0xFFCBD5E1.toInt())
            setPadding(dp(8), 0, 0, 0)
        })

        row.setOnClickListener {
            if (Store.isDone(this, hour)) Store.unmarkDone(this, hour) else Store.markDone(this, hour)
            getSystemService(NotificationManager::class.java)?.cancel(hour)
            com.dol.readloop.widget.ReadingWidgetProvider.refreshAll(this)
            render()
        }
        return row
    }

    // ------------------------------------------------------------------ small views

    private fun smallLabel(label: String) = TextView(this).apply {
        text = label
        textSize = 10f
        letterSpacing = 0.12f
        setTextColor(0xFF16A34A.toInt())
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    private fun sectionLabel(label: String) = TextView(this).apply {
        text = label
        textSize = 10.5f
        letterSpacing = 0.12f
        setTextColor(0xFF94A3B8.toInt())
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setPadding(0, dp(22), 0, dp(10))
    }

    private fun chip(label: String): View {
        val tv = TextView(this).apply {
            text = label
            textSize = 12f
            setTextColor(0xFF334155.toInt())
            setPadding(dp(11), dp(6), dp(11), dp(6))
            background = resources.getDrawable(R.drawable.bg_chip, theme)
        }
        val lp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        lp.rightMargin = dp(8)
        tv.layoutParams = lp
        return tv
    }

    private fun primaryButton(label: String, onClick: () -> Unit): View {
        val tv = TextView(this).apply {
            text = label
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(0, dp(13), 0, dp(13))
            background = resources.getDrawable(R.drawable.bg_btn, theme)
        }
        tv.setOnClickListener { onClick() }
        return tv
    }

    private fun exactAlarmBanner(): View {
        val tv = TextView(this).apply {
            text = "Exact alarms are off, so reminders may drift.\nTap to allow them \u2192"
            textSize = 12.5f
            setTextColor(0xFF7C2D12.toInt())
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = resources.getDrawable(R.drawable.bg_banner, theme)
        }
        val lp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        lp.topMargin = dp(14)
        tv.layoutParams = lp
        tv.setOnClickListener {
            if (Build.VERSION.SDK_INT >= 31) {
                try {
                    startActivity(
                        Intent(
                            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                            android.net.Uri.parse("package:$packageName")
                        )
                    )
                } catch (e: Exception) {
                    startActivity(Intent(Settings.ACTION_SETTINGS))
                }
            }
        }
        return tv
    }

    // ------------------------------------------------------------------ hidden editor

    private fun onSecretTap() {
        val now = System.currentTimeMillis()
        if (now - firstTap > 3500) {
            firstTap = now
            taps = 0
        }
        taps++
        if (taps < 7) return
        taps = 0
        if (Store.pinHash(this) == null) createPin() else askPin()
    }

    private fun createPin() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "Choose a PIN"
        }
        AlertDialog.Builder(this)
            .setTitle("Lock the schedule")
            .setMessage("Pick a PIN. It is the only way to edit your reading blocks.")
            .setView(input)
            .setCancelable(false)
            .setPositiveButton("Save") { _, _ ->
                val pin = input.text.toString().trim()
                if (pin.length < 4) {
                    toast("PIN must be at least 4 digits")
                } else {
                    Store.setPin(this, pin)
                    toast("PIN set. Tap the title 7 times again to open the schedule.")
                }
            }
            .show()
    }

    private fun askPin() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "PIN"
        }
        val dlg = AlertDialog.Builder(this)
            .setTitle("Unlock schedule")
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Unlock", null)
            .create()
        dlg.setOnShowListener {
            dlg.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                if (Store.checkPin(this, input.text.toString().trim())) {
                    dlg.dismiss()
                    startActivity(Intent(this, EditorActivity::class.java))
                } else {
                    input.error = "Wrong PIN"
                }
            }
        }
        dlg.show()
    }

    // ------------------------------------------------------------------ misc

    private fun askNotifOnce() {
        if (Build.VERSION.SDK_INT < 33 || askedNotif) return
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) return
        askedNotif = true
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 7)
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }
}
