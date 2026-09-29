package app.marlboroadvance.mpvex.utils.logging

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

private const val NEWLINE = '\n'.code.toByte()

/**
 * Splits a byte stream into lines.
 *
 * Kept separate from the file descriptor plumbing so the line handling can be
 * unit tested: partial reads, a final line without a newline, and a runaway
 * writer that never emits a newline are all covered here.
 *
 * The split happens on bytes and each complete line is decoded on its own. A
 * multi-byte character that arrives split across two reads is therefore held in
 * the byte buffer until its remaining bytes show up, instead of turning into a
 * replacement character.
 */
class NativeStreamLineReader(
  private val maxPendingBytes: Int = 8 * 1024,
  private val sink: (String) -> Unit,
) {
  private val pending = ByteArrayOutputStream()

  /** Feed one chunk of raw bytes. Complete lines are emitted to [sink]. */
  fun onBytes(
    buffer: ByteArray,
    length: Int,
  ) {
    if (length <= 0) return
    var start = 0
    for (index in 0 until length) {
      if (buffer[index] == NEWLINE) {
        pending.write(buffer, start, index - start)
        emit(pending.toByteArray())
        pending.reset()
        start = index + 1
      }
    }
    if (start < length) pending.write(buffer, start, length - start)

    // A writer that never emits a newline must not grow the buffer forever.
    if (pending.size() > maxPendingBytes) {
      emit(pending.toByteArray())
      pending.reset()
    }
  }

  /** Emit whatever is left, used when the stream ends. */
  fun flush() {
    if (pending.size() > 0) {
      emit(pending.toByteArray())
      pending.reset()
    }
  }

  private fun emit(bytes: ByteArray) {
    val line = String(bytes, StandardCharsets.UTF_8).trimEnd('\r')
    if (line.isBlank()) return
    sink(line)
  }
}
