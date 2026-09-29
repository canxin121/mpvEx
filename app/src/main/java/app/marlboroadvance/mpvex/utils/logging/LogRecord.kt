package app.marlboroadvance.mpvex.utils.logging

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Which part of the process produced a log line. */
enum class LogSource(
  val serializedName: String,
) {
  APP("app"),
  MPV("mpv"),
  NATIVE("stdout"),
  CRASH("crash"),
}

/**
 * One log line, from anywhere in the process.
 *
 * [priority] uses the `android.util.Log` scale so a single level filter works
 * for every source, mpv and captured process stdout included.
 */
data class LogRecord(
  val id: Long,
  val timeMillis: Long,
  val priority: Int,
  val source: LogSource,
  val tag: String,
  val message: String,
) {
  val levelLetter: Char get() = LogLineFormatter.levelLetter(priority)
}

/** Formats log lines for the log file and for exported logs. */
object LogLineFormatter {
  private const val MAX_TAG_WIDTH = 26
  private val timeFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

  fun format(record: LogRecord): String =
    String.format(
      Locale.US,
      "%s %c %-${MAX_TAG_WIDTH}s %s",
      timeFormat.format(Date(record.timeMillis)),
      record.levelLetter,
      "${record.source.serializedName}/${record.tag}".take(MAX_TAG_WIDTH),
      record.message,
    )

  fun levelLetter(priority: Int): Char =
    when (priority) {
      Log.VERBOSE -> 'V'
      Log.DEBUG -> 'D'
      Log.INFO -> 'I'
      Log.WARN -> 'W'
      Log.ERROR -> 'E'
      Log.ASSERT -> 'A'
      else -> '?'
    }
}
