package app.marlboroadvance.mpvex.utils.logging

import android.util.Log
import timber.log.Timber

/** Used when a log call arrives without a tag. */
private const val FALLBACK_TAG = "app"

/**
 * Writes log records to logcat.
 *
 * The tag is `MpvEx/<tag>` so `adb logcat -s MpvEx:V` selects everything the app
 * logged, including the lines forwarded from mpv and from native code. The
 * prefix is truncated to logcat's 23 character limit here rather than at the
 * call site, so the Logs screen can still show the full tag.
 */
class MpvExLogcatTree : Timber.Tree() {
  override fun isLoggable(priority: Int): Boolean = priority >= LogLevel.minimumPriority()

  override fun log(
    priority: Int,
    tag: String?,
    message: String,
    throwable: Throwable?,
  ) {
    val body = if (throwable == null) message else "$message\n${stackTraceText(throwable)}"
    Log.println(priority, MpvExLog.logcatTag(tag ?: FALLBACK_TAG), body)
  }
}

/**
 * Hands log records to whatever [LogDestinations] currently holds: the Logs
 * screen's in-memory buffer, and the rolling log file when it is enabled.
 *
 * A destination that is not installed yet simply receives nothing, so logging
 * before [LoggingSetup.install] runs is harmless.
 */
class MpvExLogDestinationTree : Timber.Tree() {
  override fun isLoggable(priority: Int): Boolean = priority >= LogLevel.minimumPriority()

  override fun log(
    priority: Int,
    tag: String?,
    message: String,
    throwable: Throwable?,
  ) {
    LogDestinations.dispatch(
      LogRecord(
        id = 0L,
        timeMillis = System.currentTimeMillis(),
        priority = priority,
        source = LogSource.APP,
        tag = tag?.ifBlank { null } ?: FALLBACK_TAG,
        message = if (throwable == null) message else "$message\n${stackTraceText(throwable)}",
      ),
    )
  }
}
