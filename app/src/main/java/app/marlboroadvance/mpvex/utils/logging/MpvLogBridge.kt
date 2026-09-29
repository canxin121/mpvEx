package app.marlboroadvance.mpvex.utils.logging

import android.os.SystemClock
import android.util.Log
import `is`.xyz.mpv.MPVLib

private const val TAG = "mpv"
private const val FATAL_PREFIX = "fatal"
private const val TRACE_PREFIX = "trace"

/** Collapse a run of identical consecutive lines once it reaches this count. */
private const val REPEAT_THRESHOLD = 5

/** mpv's noisiest levels are capped to this many lines per second. */
private const val VERBOSE_LIMIT_PER_SECOND = 500

/**
 * Routes mpv's own log messages into the app's log.
 *
 * libmpv emits its startup banner, decoder statistics, ffmpeg diagnostics and
 * anything Lua scripts print through its logging system. The native library
 * never writes those to logcat, so without this observer the only record of
 * them is whatever the "verbose logging" mpv option happens to print on screen.
 *
 * `prefix` names the mpv subsystem that produced the line (cplayer, ffmpeg, a
 * script name) and is kept in the message so the origin stays visible.
 *
 * The level mapping lives in [mpvLevelToPriority].
 */
class MpvLogBridge : MPVLib.LogObserver {
  private val lock = Any()
  private var lastKey: String? = null
  private var repeats = 0
  private var installed = false

  private var verboseWindowStart = 0L
  private var verboseCount = 0

  /**
   * Start receiving mpv's log messages. Safe to call more than once; the
   * observer is registered once per process.
   */
  fun start() {
    synchronized(lock) {
      if (installed) return
      installed = true
    }
    runCatching {
      MPVLib.addLogObserver(this)
      MpvExLog.i(TAG, "mpv log observer registered")
    }.onFailure { error ->
      synchronized(lock) { installed = false }
      MpvExLog.w(TAG, error, "Could not register the mpv log observer")
    }
  }

  /** Stop receiving mpv's log messages. */
  fun stop() {
    synchronized(lock) {
      if (!installed) return
      installed = false
    }
    runCatching { MPVLib.removeLogObserver(this) }
  }

  val isInstalled: Boolean get() = synchronized(lock) { installed }

  override fun logMessage(
    prefix: String,
    level: Int,
    text: String,
  ) {
    val priority = mpvLevelToPriority(level)
    val message = annotate(prefix, level, text)

    if (priority == Log.VERBOSE && !allowVerbose()) return

    synchronized(lock) {
      val key = "$priority\u0000$message"
      if (key == lastKey) {
        repeats++
        if (repeats < REPEAT_THRESHOLD) return
        // Report the collapse once, then start counting the run again.
        repeats = 0
        MpvExLog.external(priority, LogSource.MPV, TAG, "$message  (repeated)")
        return
      }
      lastKey = key
      repeats = 0
    }

    MpvExLog.external(priority, LogSource.MPV, TAG, message)
  }

  /** Delivers whatever the repeat detector was holding, for shutdown and crash reports. */
  fun flush() {
    synchronized(lock) { lastKey = null; repeats = 0 }
  }

  private fun annotate(
    prefix: String,
    level: Int,
    text: String,
  ): String {
    val marker =
      when (level) {
        MpvLogLevels.FATAL -> "$FATAL_PREFIX "
        MpvLogLevels.TRACE -> "$TRACE_PREFIX "
        else -> ""
      }
    return "[$prefix] $marker$text"
  }

  private fun allowVerbose(): Boolean {
    val now = SystemClock.elapsedRealtime()
    synchronized(lock) {
      if (now - verboseWindowStart >= 1_000L) {
        verboseWindowStart = now
        verboseCount = 0
      }
      verboseCount++
      return verboseCount <= VERBOSE_LIMIT_PER_SECOND
    }
  }
}
