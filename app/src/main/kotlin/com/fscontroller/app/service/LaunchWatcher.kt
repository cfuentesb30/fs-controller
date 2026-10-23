package com.fscontroller.app.service

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.fscontroller.app.BuildConfig
import java.io.IOException

/**
 * Tails `logcat` for ActivityTaskManager launches on a background thread and reports app-button
 * presses to [onDetection] on the main thread. Needs READ_LOGS (granted over ADB); without it
 * logcat only shows our own lines and nothing is ever detected.
 *
 * [start] and [stop] must be called on the main thread and are idempotent.
 */
class LaunchWatcher(
    private val onDetection: (ButtonDetector.Detection) -> Unit,
    private val detector: ButtonDetector = ButtonDetector(),
) {
    private val main = Handler(Looper.getMainLooper())
    private val timeoutToken = Any()
    private var session: Session? = null

    fun start() {
        if (session != null) return
        session = Session().also { it.thread.start() }
        debug("watcher started")
    }

    fun stop() {
        val s = session ?: return
        session = null
        s.close()
        main.removeCallbacksAndMessages(timeoutToken)
        detector.reset()
        debug("watcher stopped")
    }

    /** Main thread. */
    private fun onStartLine(line: String) {
        detector.onLine(line, SystemClock.uptimeMillis())?.let(::emit)
        main.removeCallbacksAndMessages(timeoutToken)
        val deadline = detector.deadlineMs ?: return
        main.postAtTime({
            detector.onTimeout(SystemClock.uptimeMillis())?.let(::emit)
        }, timeoutToken, deadline)
    }

    private fun emit(d: ButtonDetector.Detection) {
        debug("detected ${d.button} (system launching ${d.launchedPackage})")
        onDetection(d)
    }

    private inner class Session {
        @Volatile private var closed = false
        @Volatile private var process: Process? = null
        val thread = Thread(::run, "fs-logcat").apply { isDaemon = true }

        fun close() {
            closed = true
            process?.destroy()
            thread.interrupt()
        }

        private fun run() {
            var backoffMs = MIN_BACKOFF_MS
            while (!closed) {
                val startedAt = SystemClock.elapsedRealtime()
                try {
                    val p = ProcessBuilder(LOGCAT_CMD).redirectErrorStream(true).start()
                    process = p
                    if (closed) break
                    p.inputStream.bufferedReader().use { reader ->
                        while (true) {
                            val line = reader.readLine() ?: break
                            if (!line.contains("START u0")) continue
                            main.post { if (session === this@Session) onStartLine(line) }
                        }
                    }
                } catch (e: IOException) {
                    if (!closed) debug("logcat failed: ${e.message}")
                } finally {
                    process?.destroy()
                    process = null
                }
                if (closed) break

                // logcat died: restart with exponential backoff, reset after a healthy run.
                if (SystemClock.elapsedRealtime() - startedAt > HEALTHY_RUN_MS) backoffMs = MIN_BACKOFF_MS
                debug("logcat ended, restarting in ${backoffMs}ms")
                try {
                    Thread.sleep(backoffMs)
                } catch (e: InterruptedException) {
                    break
                }
                backoffMs = (backoffMs * 2).coerceAtMost(MAX_BACKOFF_MS)
            }
        }
    }

    private companion object {
        const val TAG = "FsWatcher"
        // `-v uid` makes logd stamp the writer's uid, which ButtonDetector requires to be 1000.
        val LOGCAT_CMD = listOf("/system/bin/logcat", "-T", "1", "-v", "brief", "-v", "uid", "ActivityTaskManager:I", "*:S")
        const val MIN_BACKOFF_MS = 1_000L
        const val MAX_BACKOFF_MS = 60_000L
        const val HEALTHY_RUN_MS = 30_000L

        fun debug(msg: String) {
            if (BuildConfig.DEBUG) Log.d(TAG, msg)
        }
    }
}
