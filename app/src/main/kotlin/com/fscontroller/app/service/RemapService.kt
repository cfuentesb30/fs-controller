package com.fscontroller.app.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.view.accessibility.AccessibilityEvent
import com.fscontroller.app.AppGraph
import com.fscontroller.app.model.AppButton
import com.fscontroller.app.model.RemapConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Hosts [LaunchWatcher]. Being a bound accessibility service is what lets us start activities from
 * the background. No key filtering and no window events: detection comes from logcat.
 *
 * The watcher (and with it the logcat process) only runs while at least one remap is active.
 */
class RemapService : AccessibilityService() {

    private var scope: CoroutineScope? = null
    private var watcher: LaunchWatcher? = null
    private lateinit var executor: ActionExecutor

    /** In-memory copy of the settings, updated from the repository flow. Main thread only. */
    private var snapshot = RemapConfig(masterEnabled = false)

    override fun onServiceConnected() {
        shutdown()
        executor = ActionExecutor(this)
        val w = LaunchWatcher(::onDetection).also { watcher = it }
        val s = MainScope().also { scope = it }
        s.launch {
            AppGraph.repository.config.collect { config ->
                snapshot = config
                if (AppButton.entries.any { config.activeTargetFor(it) != null }) w.start() else w.stop()
            }
        }
    }

    private fun onDetection(detection: ButtonDetector.Detection) {
        val target = snapshot.activeTargetFor(detection.button) ?: return
        executor.redirect(detection, target)
    }

    private fun shutdown() {
        scope?.cancel()
        scope = null
        watcher?.stop()
        watcher = null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        shutdown()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        shutdown()
        super.onDestroy()
    }
}
