package com.fscontroller.app.service

import com.fscontroller.app.model.AppButton

/**
 * Turns `ActivityTaskManager` log lines into remote app-button presses. Pure Kotlin, no Android
 * types; not thread-safe (LaunchWatcher calls it from the main thread only).
 *
 * Rules (verified on a Fire TV Stick 4K Max, Fire OS 8.1.8.5):
 * - Lines must come from `logcat -v brief -v uid` and be **written by uid 1000** (system_server).
 *   The "from uid 1000" text inside the message is forgeable by any app; the writer uid in the
 *   header is stamped by logd and is not. Lines from any other writer are ignored entirely.
 * - Only a `START u0 {…} from uid 1000` line can start a detection.
 * - Netflix is direct: the system starts `com.netflix.ninja` with category LAUNCHER. Redirect now.
 * - Prime Video, Disney+ and Peacock go through an Appstore trampoline. Redirect on the NEXT
 *   `START u0` line of any uid (the real app or its store page), or after [trampolineTimeoutMs].
 * - After a detection, new presses are ignored for [cooldownMs] so nothing can loop redirects.
 *
 * Timestamps are any monotonic millisecond clock, as long as the caller uses the same one for
 * [onLine], [deadlineMs] and [onTimeout].
 */
class ButtonDetector(
    private val trampolineTimeoutMs: Long = TRAMPOLINE_TIMEOUT_MS,
    private val cooldownMs: Long = COOLDOWN_MS,
) {

    /**
     * A button press to act on now.
     * [launchedPackage] is the package the system is starting on the line that resolved the press
     * (null after a timeout). If the user's target equals it, there is nothing to redirect.
     */
    data class Detection(val button: AppButton, val launchedPackage: String?)

    private var pending: AppButton? = null
    private var pendingDeadline = 0L
    private var lastDetectionMs: Long? = null

    /** When [onTimeout] should be called next, or null if no trampoline is pending. */
    val deadlineMs: Long? get() = if (pending != null) pendingDeadline else null

    fun onLine(line: String, nowMs: Long): Detection? {
        val start = parseStart(line) ?: return null

        pending?.let { button ->
            // First launch after the trampoline: the real app or the store page. Override it now.
            pending = null
            lastDetectionMs = nowMs
            return Detection(button, start.cmpPackage)
        }

        if (start.uid != SYSTEM_UID) return null
        val button = classify(start.intent) ?: return null
        lastDetectionMs?.let { if (nowMs - it < cooldownMs) return null }
        if (button in TRAMPOLINE_BUTTONS) {
            pending = button
            pendingDeadline = nowMs + trampolineTimeoutMs
            return null
        }
        lastDetectionMs = nowMs
        return Detection(button, start.cmpPackage)
    }

    /** Fires the pending trampoline press if its deadline has passed. */
    fun onTimeout(nowMs: Long): Detection? {
        val button = pending ?: return null
        if (nowMs < pendingDeadline) return null
        pending = null
        lastDetectionMs = nowMs
        return Detection(button, null)
    }

    fun reset() {
        pending = null
        lastDetectionMs = null
    }

    private class Start(val intent: String, val uid: Int, val cmpPackage: String?)

    companion object {
        const val TRAMPOLINE_TIMEOUT_MS = 1500L
        const val COOLDOWN_MS = 1000L
        private const val SYSTEM_UID = 1000

        private val TRAMPOLINE_BUTTONS = setOf(AppButton.PRIME_VIDEO, AppButton.DISNEY_PLUS, AppButton.PEACOCK)

        /**
         * Package each button opens on stock Fire OS (verified 2026-10-22), used to skip pointless
         * relaunches. Peacock isn't installed on the device, so its package is unknown; the
         * [Detection.launchedPackage] check covers it once it is.
         */
        val STOCK_PACKAGES: Map<AppButton, String> = mapOf(
            AppButton.NETFLIX to "com.netflix.ninja",
            AppButton.PRIME_VIDEO to "com.amazon.firebat",
            AppButton.DISNEY_PLUS to "com.disney.disneyplus",
        )

        // `logcat -v brief -v uid` header: real system_server lines read "I/ActivityTaskManager( 1000:  683): ".
        // A forged line shows the writer's own uid or name (e.g. "(shell: 5107)") and never matches.
        private val START_LINE = Regex(
            """^I/ActivityTaskManager\(\s*(?:1000|system):\s*\d+\):\s*START u0 \{(.*)\} from uid (\d+)\s*$"""
        )
        private val CMP = Regex("""\bcmp=([\w.]+)/""")

        private fun parseStart(line: String): Start? {
            val m = START_LINE.find(line) ?: return null
            val uid = m.groupValues[2].toIntOrNull() ?: return null
            val intent = m.groupValues[1]
            return Start(intent, uid, CMP.find(intent)?.groupValues?.get(1))
        }

        private fun classify(intent: String): AppButton? = when {
            "clickStreamReftag=r_btn_app_77" in intent -> AppButton.PEACOCK
            "clickStreamReftag=r_btn_app_12" in intent -> AppButton.DISNEY_PLUS
            "libraryItemId=deeplink_prime_video" in intent -> AppButton.PRIME_VIDEO
            // A home-screen Netflix tile ends with category LEANBACK_LAUNCHER, so it doesn't match.
            "cmp=com.netflix.ninja/" in intent &&
                "cat=[android.intent.category.LAUNCHER]" in intent -> AppButton.NETFLIX
            else -> null
        }
    }
}
