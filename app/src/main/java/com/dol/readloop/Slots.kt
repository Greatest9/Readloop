package com.dol.readloop

import android.content.Context
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class Block(val hour: Int, val subject: String, val detail: String)

object Slots {
    /** The 3-hour reading cycle: 12:00, 15:00, 18:00, 21:00, 00:00, 03:00, 06:00, 09:00 */
    val HOURS = intArrayOf(0, 3, 6, 9, 12, 15, 18, 21)

    /** Blocks that must be completed for a day to keep the streak. */
    const val REQUIRED_FOR_STREAK = 6

    fun label(hour: Int): String = String.format(Locale.US, "%02d:00", hour)

    fun indexOf(hour: Int): Int = HOURS.indexOf(hour).coerceAtLeast(0)

    fun hhmm(mins: Long): String {
        val m = mins.coerceAtLeast(0)
        return if (m >= 60) "${m / 60}h ${m % 60}m" else "${m}m"
    }

    /** Most recent slot that has already started. */
    fun currentSlot(now: Long): Int {
        val cal = Calendar.getInstance()
        cal.timeInMillis = now
        val h = cal.get(Calendar.HOUR_OF_DAY)
        for (i in HOURS.indices.reversed()) if (HOURS[i] <= h) return HOURS[i]
        return 21
    }

    /** Next slot that has not started yet. */
    fun nextSlot(now: Long): Int {
        val cal = Calendar.getInstance()
        cal.timeInMillis = now
        val h = cal.get(Calendar.HOUR_OF_DAY)
        for (x in HOURS) if (x > h) return x
        return 0
    }
}

object Days {
    /** 0 = Monday ... 6 = Sunday */
    val NAMES = arrayOf(
        "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"
    )
    val SHORT = arrayOf("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN")

    fun today(now: Long = System.currentTimeMillis()): Int {
        val cal = Calendar.getInstance()
        cal.timeInMillis = now
        return (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7
    }

    fun name(day: Int): String = NAMES[day.coerceIn(0, 6)]
}

object Defaults {
    /** Your weekly timetable, one entry per day. */
    private val DAY_SUBJECTS = arrayOf(
        arrayOf("Fluid Mechanics", "Leadership"),                              // Monday
        arrayOf("Applied Mechanics"),                                          // Tuesday
        arrayOf("Computer Programming", "MATLAB"),                             // Wednesday
        arrayOf("Applied Electricity"),                                        // Thursday
        arrayOf("Engineering Mathematics"),                                    // Friday
        arrayOf("Engineering Drawing"),                                        // Saturday
        arrayOf("Entrepreneurship", "General Engineering Laboratory")          // Sunday
    )

    private val FOCUS = arrayOf(
        "Read the next section and take notes",
        "Work through practice problems / past questions",
        "Recap and summarise this block",
        "Active recall - test yourself without notes"
    )

    private fun subjects(day: Int) = DAY_SUBJECTS[day.coerceIn(0, 6)]

    fun subject(day: Int, hour: Int): String {
        val subs = subjects(day)
        return subs[Slots.indexOf(hour) % subs.size]
    }

    fun detail(day: Int, hour: Int): String {
        val subs = subjects(day)
        val idx = Slots.indexOf(hour)
        return FOCUS[(idx / subs.size) % FOCUS.size]
    }
}

object Store {
    private const val PREFS = "readloop"

    private fun p(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun dayKey(d: Date = Date()): String =
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(d)

    fun block(c: Context, day: Int, hour: Int): Block {
        val prefs = p(c)
        return Block(
            hour,
            prefs.getString("subj_${day}_$hour", null) ?: Defaults.subject(day, hour),
            prefs.getString("det_${day}_$hour", null) ?: Defaults.detail(day, hour)
        )
    }

    fun saveBlock(c: Context, day: Int, hour: Int, subject: String, detail: String) {
        p(c).edit()
            .putString("subj_${day}_$hour", subject.trim())
            .putString("det_${day}_$hour", detail.trim())
            .apply()
    }

    fun done(c: Context, key: String = dayKey()): MutableSet<Int> {
        val raw = p(c).getString("done_$key", "") ?: ""
        return raw.split(',')
            .mapNotNull { it.trim().toIntOrNull() }
            .filter { it in Slots.HOURS }
            .toMutableSet()
    }

    fun doneCount(c: Context, key: String = dayKey()): Int = done(c, key).size

    fun isDone(c: Context, hour: Int): Boolean = done(c).contains(hour)

    fun markDone(c: Context, hour: Int) {
        val k = dayKey()
        val s = done(c, k)
        s.add(hour)
        p(c).edit().putString("done_$k", s.joinToString(",")).apply()
    }

    fun unmarkDone(c: Context, hour: Int) {
        val k = dayKey()
        val s = done(c, k)
        s.remove(hour)
        p(c).edit().putString("done_$k", s.joinToString(",")).apply()
    }

    /** Consecutive days that met the streak threshold. Today only counts once it's met. */
    fun streak(c: Context): Int {
        var n = 0
        val cal = Calendar.getInstance()
        if (doneCount(c, dayKey(cal.time)) >= Slots.REQUIRED_FOR_STREAK) n++
        repeat(400) {
            cal.add(Calendar.DAY_OF_YEAR, -1)
            if (doneCount(c, dayKey(cal.time)) >= Slots.REQUIRED_FOR_STREAK) n++ else return n
        }
        return n
    }

    fun prune(c: Context) {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -45)
        val cutoff = cal.time
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val e = p(c).edit()
        for (k in p(c).all.keys) {
            if (!k.startsWith("done_")) continue
            val d = try {
                fmt.parse(k.removePrefix("done_"))
            } catch (ex: Exception) {
                null
            }
            if (d != null && d.before(cutoff)) e.remove(k)
        }
        e.apply()
    }

    // ---- hidden editor PIN ----

    fun pinHash(c: Context): String? = p(c).getString("pin_hash", null)

    fun setPin(c: Context, pin: String) {
        p(c).edit().putString("pin_hash", sha256(pin + "|readloop")).apply()
    }

    fun checkPin(c: Context, pin: String): Boolean = pinHash(c) == sha256(pin + "|readloop")

    private fun sha256(s: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(s.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }
}
