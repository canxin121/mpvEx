package app.marlboroadvance.mpvex.utils.logging

import android.os.SystemClock
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Thread-safe ring buffer of recent log records, kept in memory so the Logs
 * screen can show history without reading a file.
 *
 * Writers are never blocked: when the buffer is full the oldest record is
 * dropped. State emission is throttled because log calls can arrive at
 * thousands per second and recomposing the list for each one would stall the
 * UI thread.
 */
object InMemoryLogBuffer {
  private const val CAPACITY = 4000
  private const val EMIT_INTERVAL_MS = 250L

  private val nextId = AtomicLong(0L)
  private val lock = Any()
  private val records = ArrayDeque<LogRecord>(CAPACITY)

  private val mutableLines = MutableStateFlow<List<LogRecord>>(emptyList())
  val lines: StateFlow<List<LogRecord>> = mutableLines.asStateFlow()

  private var lastEmitElapsed = 0L

  fun append(record: LogRecord) {
    val identified = if (record.id == 0L) record.copy(id = nextId.incrementAndGet()) else record
    synchronized(lock) {
      records.addLast(identified)
      while (records.size > CAPACITY) {
        records.removeFirst()
      }
      val now = SystemClock.elapsedRealtime()
      if (now - lastEmitElapsed < EMIT_INTERVAL_MS) return
      lastEmitElapsed = now
      mutableLines.value = records.toList()
    }
  }

  /** Publish the current contents immediately, ignoring the emission throttle. */
  fun publish() {
    synchronized(lock) {
      lastEmitElapsed = 0L
      mutableLines.value = records.toList()
    }
  }

  /** All retained records, oldest first. */
  fun snapshot(): List<LogRecord> = synchronized(lock) { records.toList() }

  fun clear() {
    synchronized(lock) {
      records.clear()
      lastEmitElapsed = 0L
      mutableLines.value = emptyList()
    }
  }

  fun size(): Int = synchronized(lock) { records.size }
}

/**
 * Renders the buffer as plain text, used for copy and share actions and for the
 * crash report.
 */
fun renderLogRecords(records: List<LogRecord>): String =
  records.joinToString(separator = "\n") { LogLineFormatter.format(it) }
