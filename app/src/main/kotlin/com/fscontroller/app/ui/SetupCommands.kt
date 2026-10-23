package com.fscontroller.app.ui

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import com.fscontroller.app.model.SetupStatus

/** One ADB command to show on Setup help. [id] is the step number (1-4). */
data class SetupStep(val id: Int, val command: String)

/**
 * Builds the Setup help commands. UI-only helper: reads the current
 * ENABLED_ACCESSIBILITY_SERVICES so command 2 can be pasted as shown. Never suggests am force-stop.
 */
object SetupCommands {
    private const val SERVICE_CLASS = "com.fscontroller.app.service.RemapService"

    const val KILL_SWITCH =
        "adb shell settings put secure enabled_accessibility_services '\"\"' " +
            "&& adb shell settings put secure accessibility_enabled 0"

    /** Steps for [status], for the current setup status. Call off the main thread (reads Settings). */
    fun stepsFor(context: Context, status: SetupStatus): List<SetupStep> {
        val ids = when {
            !status.serviceEnabled && !status.canReadLogs -> listOf(1, 2, 3, 4)
            !status.serviceEnabled -> listOf(2, 3)
            !status.canReadLogs -> listOf(1, 4)
            else -> emptyList()
        }
        if (ids.isEmpty()) return emptyList()
        val pkg = context.packageName
        return ids.map { id ->
            val cmd = when (id) {
                1 -> "adb shell pm grant $pkg android.permission.READ_LOGS"
                2 -> enableServiceCommand(enabledServicesWithOurs(context))
                3 -> "adb shell settings put secure accessibility_enabled 1"
                else -> "adb reboot"
            }
            SetupStep(id, cmd)
        }
    }

    /** Current list plus this service, appended once. Duplicates of ours (any spelling) are dropped. */
    fun enabledServicesWithOurs(context: Context): String {
        val ours = ComponentName(context, SERVICE_CLASS)
        val current = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ).orEmpty()
        return mergeServiceList(current, ours.flattenToString()) { ComponentName.unflattenFromString(it) == ours }
    }

    /** Only well-formed component names survive; anything else could not be a real service anyway. */
    private val COMPONENT = Regex("""^[A-Za-z0-9_.$]+/[A-Za-z0-9_.$]+$""")

    /** Pure merge: keep others in order, drop blanks/dupes/malformed entries, append [ours]. */
    internal fun mergeServiceList(current: String, ours: String, isOurs: (String) -> Boolean): String {
        val others = current.split(':')
            .map { it.trim() }
            .filter { COMPONENT.matches(it) && !isOurs(it) }
            .distinct()
        return (others + ours).joinToString(":")
    }

    /**
     * `adb shell` re-parses its arguments in the device's sh, so quoting for the PC shell alone is not
     * enough. Plain names go through as-is. Inner-class names (`$`) are single-quoted for the PC and
     * double-quoted with `\$` for the device, so both shells pass them through literally.
     */
    internal fun enableServiceCommand(list: String): String {
        val base = "settings put secure enabled_accessibility_services"
        return if ('$' !in list) "adb shell $base $list"
        else "adb shell '$base \"${list.replace("$", "\\$")}\"'"
    }
}
