package app.marlboroadvance.mpvex.utils.logging

import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.io.Writer
import java.nio.charset.StandardCharsets

/**
 * Appends formatted log lines to one file, rotating it when it grows past
 * [maxSizeBytes].
 *
 * Rotation renames `app.log` to `app.log.1`, shifting older files up, and
 * deletes the file that falls past [maxFileCount]. It happens *before* the line
 * that would overflow is written, so the file on disk never grows past the
 * limit and `app.log` exists after every append.
 *
 * Everything is wrapped in `runCatching`; a failing log file must never break
 * playback.
 */
class RotatingLogFile(
  private val directory: File,
  private val baseName: String = "app.log",
  private val maxSizeBytes: Long = 2L * 1024 * 1024,
  private val maxFileCount: Int = 5,
) {
  private var writer: Writer? = null
  private var currentSize = 0L
  private var failed = false

  @Synchronized
  fun append(line: String) {
    if (failed) return
    runCatching {
      // Count bytes, not characters: a non-ASCII line is longer on disk than
      // its length suggests.
      val written = line.toByteArray(StandardCharsets.UTF_8).size + 1
      if (writer == null) openWriter()
      // A line longer than the whole budget is still written rather than
      // rotated around forever.
      if (currentSize > 0 && currentSize + written > maxSizeBytes) {
        closeWriter()
        rotate()
        openWriter()
      }
      val out = writer ?: return@runCatching
      out.append(line).append('\n')
      currentSize += written
    }.onFailure {
      failed = true
      writer = null
    }
  }

  @Synchronized
  fun flush() {
    runCatching { writer?.flush() }
  }

  @Synchronized
  fun close() {
    closeWriter()
  }

  /** The file currently being written, for export and for the UI. */
  fun currentFile(): File = File(directory, baseName)

  private fun closeWriter() {
    runCatching {
      writer?.flush()
      writer?.close()
    }
    writer = null
  }

  private fun openWriter() {
    if (!directory.exists()) directory.mkdirs()
    val file = File(directory, baseName)
    currentSize = if (file.exists()) file.length() else 0L
    val stream = FileOutputStream(file, true)
    writer = OutputStreamWriter(stream, StandardCharsets.UTF_8).buffered()
  }

  private fun rotate() {
    if (!directory.exists()) return
    val oldest = File(directory, "$baseName.${maxFileCount - 1}")
    if (oldest.exists()) oldest.delete()
    for (index in maxFileCount - 2 downTo 1) {
      val from = File(directory, "$baseName.$index")
      if (from.exists()) move(from, File(directory, "$baseName.${index + 1}"))
    }
    val current = File(directory, baseName)
    if (current.exists()) move(current, File(directory, "$baseName.1"))
    currentSize = 0L
  }

  /**
   * Renames a file, falling back to a copy when the rename is refused. Some
   * Android storage providers reject `renameTo` across directories.
   */
  private fun move(
    from: File,
    to: File,
  ) {
    if (from.renameTo(to)) return
    if (to.exists()) to.delete()
    from.copyTo(to, overwrite = true)
    from.delete()
  }
}
