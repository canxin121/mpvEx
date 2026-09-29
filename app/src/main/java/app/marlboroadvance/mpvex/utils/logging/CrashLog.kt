package app.marlboroadvance.mpvex.utils.logging

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Saves what the process was doing when it died.
 *
 * An uncaught exception ends the process without a chance to write anything
 * else, so the handler calls this first, synchronously: the log file is flushed
 * and the in-memory buffer, which holds lines that may not have reached disk
 * yet, is copied next to it. CrashActivity reads it back on the next launch.
 */
object CrashLog {
  const val FILE_NAME = "crash-last.txt"

  private val headerFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

  fun record(
    context: Context,
    throwable: Throwable,
  ): File? =
    runCatching {
      val directory = LogFiles.directoryOf(context.filesDir)
      if (!directory.exists()) directory.mkdirs()
      LogFiles.flush()

      val file = File(directory, FILE_NAME)
      file.writeText(compose(headerFormat.format(Date()), throwable, InMemoryLogBuffer.snapshot()))
      file
    }.getOrNull()

  fun read(filesDir: File): String? =
    runCatching {
      val file = File(LogFiles.directoryOf(filesDir), FILE_NAME)
      if (file.exists()) file.readText() else null
    }.getOrNull()

  fun clear(filesDir: File) {
    runCatching { File(LogFiles.directoryOf(filesDir), FILE_NAME).delete() }
  }

  private fun compose(
    timestamp: String,
    throwable: Throwable,
    records: List<LogRecord>,
  ): String =
    StringBuilder()
      .apply {
        appendLine("Crash at $timestamp")
        appendLine()
        appendLine("Exception:")
        appendLine(throwable.stackTraceToString())
        appendLine()
        appendLine("Recent logs (${records.size}):")
        append(renderLogRecords(records))
        appendLine()
      }.toString()
}
