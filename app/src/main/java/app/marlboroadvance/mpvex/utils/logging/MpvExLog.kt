package app.marlboroadvance.mpvex.utils.logging

import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import timber.log.Timber

/** The throwable's stack trace as text. */
internal fun stackTraceText(throwable: Throwable): String {
  val writer = StringWriter()
  PrintWriter(writer).use { throwable.printStackTrace(it) }
  return writer.toString().trimEnd()
}

/**
 * Destinations for log records, other than logcat.
 *
 * The logging setup installs the in-memory buffer and the log file here. A
 * single list keeps logcat, the buffer and the file in agreement: everything
 * passes through [MpvExLog], which calls [dispatch] exactly once per record.
 */
object LogDestinations {
  @Volatile
  private var destinations: List<(LogRecord) -> Unit> = emptyList()

  fun install(destinations: List<(LogRecord) -> Unit>) {
    this.destinations = destinations
  }

  fun dispatch(record: LogRecord) {
    val current = destinations
    if (current.isEmpty()) return
    current.forEach { it(record) }
  }
}

/**
 * The logging facade every call site uses.
 *
 * A call is handed to [Timber], whose planted trees write to logcat with a
 * stable `MpvEx/` tag prefix, to the Logs screen's in-memory buffer and to the
 * rolling log file.
 *
 * ```kotlin
 * MpvExLog.d(TAG, "Opening %s", path)
 * MpvExLog.e(TAG, throwable, "Failed to open %s", path)
 * ```
 *
 * The throwable comes second, matching the `android.util.Log` argument order
 * the call sites were migrated from. `Timber` puts it first; that reordering
 * happens here, once.
 */
object MpvExLog {
  private const val TAG_PREFIX = "MpvEx/"

  fun v(
    tag: String,
    message: String,
    vararg args: Any?,
  ) = withTag(tag).v(message, *args)

  fun d(
    tag: String,
    message: String,
    vararg args: Any?,
  ) = withTag(tag).d(message, *args)

  fun i(
    tag: String,
    message: String,
    vararg args: Any?,
  ) = withTag(tag).i(message, *args)

  fun w(
    tag: String,
    message: String,
    vararg args: Any?,
  ) = withTag(tag).w(message, *args)

  fun w(
    tag: String,
    throwable: Throwable?,
    message: String,
    vararg args: Any?,
  ) = withTag(tag).w(throwable, message, *args)

  fun e(
    tag: String,
    message: String,
    vararg args: Any?,
  ) = withTag(tag).e(message, *args)

  fun e(
    tag: String,
    throwable: Throwable?,
    message: String,
    vararg args: Any?,
  ) = withTag(tag).e(throwable, message, *args)

  /**
   * Record a line produced outside app code, such as by mpv's log observer or
   * by native code writing to stdout.
   *
   * The destination tree is bypassed: the source is not `APP`, and a native line
   * is already formatted text rather than a format string with arguments.
   */
  fun external(
    priority: Int,
    source: LogSource,
    tag: String,
    message: String,
  ) {
    if (priority < LogLevel.minimumPriority()) return
    val record =
      LogRecord(
        id = 0L,
        timeMillis = System.currentTimeMillis(),
        priority = priority,
        source = source,
        tag = tag.ifBlank { "app" },
        message = message,
      )
    Log.println(priority, logcatTag(tag), record.message)
    LogDestinations.dispatch(record)
  }

  /** `MpvEx/<tag>`, short enough for logcat's 23 character tag limit. */
  fun logcatTag(tag: String): String = (TAG_PREFIX + tag.replace(' ', '_')).take(23)

  private fun withTag(tag: String): Timber.Tree = Timber.tag(tag.ifBlank { "app" })
}

/** Handle on the log file, used by the Logs screen and the crash handler. */
object LogFiles {
  const val APP_LOG_NAME = "app.log"
  const val MPV_LOG_NAME = "mpv.log"

  private var sink: LogFileSink? = null
  private var directory: File? = null

  fun install(
    sink: LogFileSink?,
    directory: File?,
  ) {
    this.sink = sink
    this.directory = directory
  }

  val isActive: Boolean get() = sink != null

  /** Directory holding `app.log` and mpv's own log file. */
  fun directoryOf(filesDir: File): File = directory ?: File(filesDir, "logs")

  fun currentFile(): File? {
    val directory = directory ?: return null
    return File(directory, APP_LOG_NAME)
  }

  /** Wait for queued records to reach disk, e.g. before a crash report is built. */
  fun flush() {
    sink?.flush()
  }
}
