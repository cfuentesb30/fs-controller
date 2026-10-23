package com.fscontroller.app.data

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import com.fscontroller.app.model.SetupStatus
import com.fscontroller.app.service.RemapService
import java.io.File
import java.io.IOException

class SystemSetupStatusSource(private val context: Context) : SetupStatusSource {

    private val ownService = ComponentName(context, RemapService::class.java)

    /**
     * READ_LOGS maps to the `log` group (gid 1007), which is applied only when a process starts.
     * A grant made while we're running doesn't reach this process, so the group is the real
     * test. It can't change during the process lifetime, so it's read once.
     */
    private val processHasLogGroup: Boolean by lazy {
        try {
            File("/proc/self/status").useLines { lines ->
                lines.firstOrNull { it.startsWith("Groups:") }
                    ?.substringAfter(':')
                    ?.trim()
                    ?.split(Regex("\\s+"))
                    ?.contains(LOG_GID)
                    ?: false
            }
        } catch (e: IOException) {
            false
        }
    }

    override fun current(): SetupStatus = SetupStatus(
        serviceEnabled = serviceEnabled(),
        canReadLogs = context.checkSelfPermission(Manifest.permission.READ_LOGS) ==
            PackageManager.PERMISSION_GRANTED && processHasLogGroup,
    )

    private fun serviceEnabled(): Boolean {
        val resolver = context.contentResolver
        if (Settings.Secure.getInt(resolver, Settings.Secure.ACCESSIBILITY_ENABLED, 0) != 1) return false
        val enabled = Settings.Secure.getString(resolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        // unflattenFromString expands the short "pkg/.service.RemapService" form.
        return enabled.split(':').any { ComponentName.unflattenFromString(it.trim()) == ownService }
    }

    private companion object {
        const val LOG_GID = "1007"
    }
}
