package com.dol.readloop

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class EditorActivity : Activity() {

    private lateinit var slotsContainer: LinearLayout
    private lateinit var dayRow: LinearLayout

    private var currentDay = Days.today()

    private val draftSubject = HashMap<String, String>()
    private val draftDetail = HashMap<String, String>()

    private class Field(val hour: Int, val subject: EditText, val detail: EditText)
    private val fields = ArrayList<Field>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scroll = ScrollView(this)
        scroll.setBackgroundColor(0xFFF6F8FA.toInt())
        val col = LinearLayout(this)
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(dp(20), dp(28), dp(20), dp(40))

        col.addView(TextView(this).apply {
            text = "Schedule"
            textSize = 26f
            setTextColor(0xFF0F172A.toInt())
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
        })

        col.addView(TextView(this).apply {
            text = "Reminders fire at these 8 times, every day. Editing is the only control you get."
            textSize = 12.5f
            setTextColor(0xFF64748B.toInt())
            setPadding(0, dp(4), 0, dp(16))
        })

        dayRow = LinearLayout(this)
        dayRow.orientation = LinearLayout.HORIZONTAL
        dayRow.gravity = Gravity.CENTER_VERTICAL
        val dayLp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        dayLp.bottomMargin = dp(16)
        dayRow.layoutParams = dayLp
        col.addView(dayRow)

        slotsContainer = LinearLayout(this)
        slotsContainer.orientation = LinearLayout.VERTICAL
        col.addView(slotsContainer)

        val save = TextView(this).apply {
            text = "Save schedule"
            textSize = 15f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(0, dp(15), 0, dp(15))
            background = resources.getDrawable(R.drawable.bg_btn, theme)
        }
        save.setOnClickListener { saveSchedule() }
        col.addView(save)

        val pin = TextView(this).apply {
            text = "Change PIN"
            textSize = 13f
            gravity = Gravity.CENTER
            setTextColor(0xFF475569.toInt())
            setPadding(0, dp(20), 0, 0)
        }
        pin.setOnClickListener { changePin() }
        col.addView(pin)

        scroll.addView(col)
        setContentView(scroll)

        renderDayRow()
        renderSlots()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun key(day: Int, hour: Int, detail: Boolean) =
        (if (detail) "d" else "s") + "_" + day + "_" + hour

    /** Pull whatever is currently on screen into the draft map. */
    private fun capture() {
        for (f in fields) {
            draftSubject[key(currentDay, f.hour, false)] = f.subject.text.toString()
            draftDetail[key(currentDay, f.hour, true)] = f.detail.text.toString()
        }
    }

    private fun renderDayRow() {
        dayRow.removeAllViews()
        for (d in 0..6) {
            val selected = d == currentDay
            val tv = TextView(this).apply {
                text = Days.SHORT[d]
                textSize = 10f
                gravity = Gravity.CENTER
                letterSpacing = 0.06f
                setTextColor(if (selected) Color.WHITE else 0xFF475569.toInt())
                typeface = Typeface.create(
                    "sans-serif-medium",
                    if (selected) Typeface.BOLD else Typeface.NORMAL
                )
                setPadding(dp(6), dp(8), dp(6), dp(8))
                background = resources.getDrawable(
                    if (selected) R.drawable.bg_chip_selected else R.drawable.bg_chip,
                    theme
                )
            }
            tv.setOnClickListener {
                if (d == currentDay) return@setOnClickListener
                capture()
                currentDay = d
                renderDayRow()
                renderSlots()
            }
            val lp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            lp.rightMargin = if (d < 6) dp(4) else 0
            dayRow.addView(tv, lp)
        }
    }

    private fun renderSlots() {
        slotsContainer.removeAllViews()
        fields.clear()

        slotsContainer.addView(TextView(this).apply {
            text = Days.name(currentDay).uppercase()
            textSize = 10.5f
            letterSpacing = 0.12f
            setTextColor(0xFF94A3B8.toInt())
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(0, 0, 0, dp(10))
        })

        for (h in Slots.HOURS) {
            val fallback = Store.block(this, currentDay, h)

            val box = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(12), dp(14), dp(12))
                background = resources.getDrawable(R.drawable.bg_card, theme)
            }
            val lp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            lp.bottomMargin = dp(10)
            box.layoutParams = lp

            box.addView(TextView(this).apply {
                text = Slots.label(h) + "  \u2013  " + Slots.label((h + 3) % 24)
                textSize = 11f
                setTextColor(0xFF16A34A.toInt())
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            })

            val s = EditText(this).apply {
                setText(draftSubject[key(currentDay, h, false)] ?: fallback.subject)
                hint = "Subject"
                textSize = 15f
                setSingleLine(true)
            }
            box.addView(s)

            val d = EditText(this).apply {
                setText(draftDetail[key(currentDay, h, true)] ?: fallback.detail)
                hint = "What to read"
                textSize = 13.5f
                setSingleLine(true)
            }
            box.addView(d)

            fields.add(Field(h, s, d))
            slotsContainer.addView(box)
        }
    }

    private fun saveSchedule() {
        capture()
        for (day in 0..6) {
            for (h in Slots.HOURS) {
                val fallback = Store.block(this, day, h)
                val s = draftSubject[key(day, h, false)] ?: fallback.subject
                val d = draftDetail[key(day, h, true)] ?: fallback.detail
                Store.saveBlock(
                    this, day, h,
                    s.trim().ifBlank { Defaults.subject(day, h) },
                    d.trim().ifBlank { Defaults.detail(day, h) }
                )
            }
        }
        Alarms.scheduleAll(this)
        com.dol.readloop.widget.ReadingWidgetProvider.refreshAll(this)
        Toast.makeText(this, "Saved. Reminders rescheduled.", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun changePin() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            hint = "New PIN (4+ digits)"
        }
        AlertDialog.Builder(this)
            .setTitle("Change PIN")
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save") { _, _ ->
                val pin = input.text.toString().trim()
                if (pin.length < 4) {
                    Toast.makeText(this, "PIN must be at least 4 digits", Toast.LENGTH_SHORT).show()
                } else {
                    Store.setPin(this, pin)
                    Toast.makeText(this, "PIN updated", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }
}
