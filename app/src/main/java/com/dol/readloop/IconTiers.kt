package com.dol.readloop

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/**
 * Android cannot redraw a launcher icon at runtime, so the manifest declares five
 * <activity-alias> entries, one per streak tier, and exactly one is enabled at a
 * time. The ring fills in quarter steps as the streak grows, mirroring the ring
 * on the home-screen widget.
 *
 *   0        no streak        empty grey ring
 *   1-2      starting         25%
 *   3-6      warming up       50%
 *   7-29     committed        75%
 *   30+      unstoppable      100%
 */
object IconTiers {

    private const val PREF = "icon_tier"

    private val ALIASES = arrayOf(
        "IconTier0",
        "IconTier1",
        "IconTier2",
        "IconTier3",
        "IconTier4"
    )

    fun tierFor(streak: Int): Int = when {
        streak >= 30 -> 4
        streak >= 7 -> 3
        streak >= 3 -> 2
        streak >= 1 -> 1
        else -> 0
    }

    /**
     * Idempotent and cheap: it returns immediately when the tier has not changed,
     * so it is safe to call from every receiver, the widget refresh and onResume.
     */
    fun apply(context: Context) {
        val wanted = tierFor(Store.streak(context))
        val prefs = context.getSharedPreferences("readloop", Context.MODE_PRIVATE)
        if (prefs.getInt(PREF, -1) == wanted) return

        val pkg = context.packageName
        val pm = context.packageManager
        try {
            // Enable the new alias *before* disabling the old ones so the launcher
            // never sees a moment with no enabled launcher activity.
            pm.setComponentEnabledSetting(
                ComponentName(pkg, pkg + "." + ALIASES[wanted]),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
            for (i in ALIASES.indices) {
                if (i == wanted) continue
                pm.setComponentEnabledSetting(
                    ComponentName(pkg, pkg + "." + ALIASES[i]),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
            prefs.edit().putInt(PREF, wanted).apply()
        } catch (e: Exception) {
            // Some OEM launchers refuse component toggles. Never crash the app.
        }
    }
}
