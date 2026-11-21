package org.jellyfin.mobile.nflix.perf

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.view.Choreographer
import androidx.core.content.edit
import timber.log.Timber
import java.io.File

/**
 * Lightweight production perf probes: cold-start time (process start → first frame) and a
 * main-thread watchdog that records the stack of any freeze longer than [ANR_MS].
 */
object PerfWatch {
    const val ANR_MS = 5_000L
    private const val PREFS = "nflix_perf"
    private const val KEEP = 10

    @Volatile private var started = false

    /** Median of the last cold starts in ms, or null before the first measure. */
    fun medianColdStart(context: Context): Long? = median(history(context))

    fun lastColdStart(context: Context): Long? = history(context).lastOrNull()

    private fun history(context: Context): List<Long> =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("cold", null).orEmpty().let(::parse)

    internal fun parse(raw: String): List<Long> = raw.split(',').mapNotNull { it.trim().toLongOrNull() }.filter { it in 1..120_000 }

    internal fun append(raw: String, value: Long): String = (parse(raw) + value).takeLast(KEEP).joinToString(",")

    internal fun median(values: List<Long>): Long? {
        if (values.isEmpty()) return null
        val s = values.sorted()
        return if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
    }

    /** Call from the launcher activity's first onCreate (savedInstanceState == null). */
    fun measureColdStart(context: Context) {
        if (android.os.Build.VERSION.SDK_INT < 24) return
        val app = context.applicationContext
        Choreographer.getInstance().postFrameCallback {
            // The frame after the first layout is the first one the user sees.
            Choreographer.getInstance().postFrameCallback {
                val ms = SystemClock.uptimeMillis() - Process.getStartUptimeMillis()
                Timber.i("Nflix cold start: %d ms", ms)
                val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                prefs.edit { putString("cold", append(prefs.getString("cold", null).orEmpty(), ms)) }
            }
        }
    }

    /** Starts the main-thread watchdog once; the freeze trace goes to Timber and files/nflix-anr.txt. */
    fun startAnrWatch(context: Context) {
        if (started) return
        started = true
        val target = File(context.filesDir, "nflix-anr.txt")
        val main = Handler(Looper.getMainLooper())
        Thread({
            var reported = false
            while (true) {
                val tick = java.util.concurrent.atomic.AtomicBoolean(false)
                val posted = SystemClock.uptimeMillis()
                main.post { tick.set(true) }
                try {
                    Thread.sleep(ANR_MS)
                } catch (_: InterruptedException) {
                    return@Thread
                }
                if (!tick.get() && !reported) {
                    reported = true
                    val stack = Looper.getMainLooper().thread.stackTrace.joinToString("\n") { "  at $it" }
                    Timber.w("Main thread blocked > %d ms\n%s", SystemClock.uptimeMillis() - posted, stack)
                    runCatching { target.writeText("blocked>${ANR_MS}ms\n$stack") }
                } else if (tick.get()) {
                    reported = false
                }
            }
        }, "nflix-anr-watch").apply { isDaemon = true; priority = Thread.MIN_PRIORITY }.start()
    }
}
