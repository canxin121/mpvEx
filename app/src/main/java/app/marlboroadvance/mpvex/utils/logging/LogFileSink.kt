package app.marlboroadvance.mpvex.utils.logging

import android.util.Log
import java.io.File
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import kotlin.concurrent.thread

private const val QUEUE_CAPACITY = 4096
private const val DROPPED_REPORT_INTERVAL_MS = 30_000L

/**
 * Persists log lines to a rolling file without ever blocking the caller.
 *
 * Log calls hand the record to a bounded queue and return; a single writer
 * thread does the file I/O. When the queue is full (a log storm) records are
 * dropped and counted instead of stalling playback. Level filtering already
 * happened in [LogLevel], so everything offered here is meant to be written.
 */
class LogFileSink(
  private val directory: File,
  baseName: String = "app.log",
  maxSizeBytes: Long = 2L * 1024 * 1024,
  maxFileCount: Int = 5,
) {
  private val file = RotatingLogFile(directory, baseName, maxSizeBytes, maxFileCount)
  private val queue = ArrayBlockingQueue<LogRecord>(QUEUE_CAPACITY)
  private val dropped = AtomicLong(0L)

  @Volatile
  private var running = true

  private val writer =
    thread(name = "mpvex-log-writer", isDaemon = true) {
      writeLoop()
    }

  fun offer(record: LogRecord) {
    if (!running) return
    if (!queue.offer(record)) dropped.incrementAndGet()
  }

  /** Wait until everything queued so far has been written, for crash reports and exports. */
  fun flush(timeoutMillis: Long = 1500L) {
    val deadline = System.currentTimeMillis() + timeoutMillis
    while (queue.isNotEmpty() && System.currentTimeMillis() < deadline) {
      Thread.sleep(20)
    }
    file.flush()
  }

  fun close() {
    running = false
    writer.interrupt()
    file.close()
  }

  fun currentFile(): File = file.currentFile()

  private fun writeLoop() {
    var lastDropReportAt = 0L
    try {
      while (running) {
        val record = queue.poll(500, TimeUnit.MILLISECONDS) ?: continue
        file.append(LogLineFormatter.format(record))

        val now = System.currentTimeMillis()
        if (dropped.get() > 0 && now - lastDropReportAt > DROPPED_REPORT_INTERVAL_MS) {
          lastDropReportAt = now
          val count = dropped.getAndSet(0L)
          file.append(
            LogLineFormatter.format(
              LogRecord(
                id = 0L,
                timeMillis = now,
                priority = Log.WARN,
                source = LogSource.APP,
                tag = "logging",
                message = "Dropped $count log lines: the log queue was full",
              ),
            ),
          )
        }
      }
    } catch (_: InterruptedException) {
      // Closing.
    } finally {
      file.close()
    }
  }
}

/**
 * Deletes log files that are older than [retentionDays], and rotates away any
 * file beyond what the sink keeps.
 *
 * Called on application start next to the other cache maintenance work.
 */
object LogRetention {
  private const val RETENTION_INTERVAL_MS = 24L * 60 * 60 * 1000

  fun cleanup(
    directory: File,
    retentionDays: Int,
    now: Long = System.currentTimeMillis(),
  ): Int {
    if (!directory.exists() || !directory.isDirectory) return 0
    val cutoff = now - retentionDays * RETENTION_INTERVAL_MS
    var removed = 0
    directory.listFiles()?.forEach { file ->
      if (file.isFile && file.lastModified() < cutoff && file.delete()) removed++
    }
    return removed
  }
}
