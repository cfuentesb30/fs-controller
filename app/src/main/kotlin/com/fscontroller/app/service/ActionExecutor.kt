package com.fscontroller.app.service

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import com.fscontroller.app.BuildConfig

/** Launches the user's chosen app on top of whatever the remote button just opened. */
class ActionExecutor(private val context: Context) {

    /** Returns true if a launch was started. Main thread. */
    fun redirect(detection: ButtonDetector.Detection, targetPackage: String): Boolean {
        if (targetPackage == ButtonDetector.STOCK_PACKAGES[detection.button] ||
            targetPackage == detection.launchedPackage
        ) {
            debug("${detection.button}: target $targetPackage is already what the system opens")
            return false
        }
        val started = SystemClock.uptimeMillis()
        val pm = context.packageManager
        val intent = pm.getLeanbackLaunchIntentForPackage(targetPackage)
            ?: pm.getLaunchIntentForPackage(targetPackage)
        if (intent == null) {
            debug("${detection.button}: $targetPackage not installed or not launchable")
            return false
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return try {
            context.startActivity(intent)
            debug("${detection.button}: launched $targetPackage (+${SystemClock.uptimeMillis() - started}ms)")
            true
        } catch (e: ActivityNotFoundException) {
            debug("${detection.button}: launch of $targetPackage failed: ${e.message}")
            false
        } catch (e: SecurityException) {
            debug("${detection.button}: launch of $targetPackage refused: ${e.message}")
            false
        }
    }

    private fun debug(msg: String) {
        if (BuildConfig.DEBUG) Log.d(TAG, msg)
    }

    private companion object {
        const val TAG = "FsExecutor"
    }
}
