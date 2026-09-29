package app.marlboroadvance.mpvex.utils.logging

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NativeStreamLineReaderTest {
  private val lines = mutableListOf<String>()
  private val reader = NativeStreamLineReader { line -> lines.add(line) }

  private fun feed(vararg chunks: String) {
    chunks.forEach { chunk ->
      val bytes = chunk.toByteArray(Charsets.UTF_8)
      reader.onBytes(bytes, bytes.size)
    }
  }

  @Test
  fun `emits each complete line`() {
    feed("one\ntwo\nthree\n")

    assertEquals(listOf("one", "two", "three"), lines)
  }

  @Test
  fun `holds a partial line until its newline arrives`() {
    feed("Hel", "lo, ", "world\n")

    assertEquals(listOf("Hello, world"), lines)
  }

  @Test
  fun `flush emits the trailing line without a newline`() {
    feed("last line without newline")
    assertTrue(lines.isEmpty())

    reader.flush()

    assertEquals(listOf("last line without newline"), lines)
  }

  @Test
  fun `strips carriage returns so windows line endings do not leak in`() {
    feed("windows\r\nline\r\n")

    assertEquals(listOf("windows", "line"), lines)
  }

  @Test
  fun `skips blank lines`() {
    feed("a\n\n\nb\n")

    assertEquals(listOf("a", "b"), lines)
  }

  @Test
  fun `a runaway writer is force flushed past the pending limit`() {
    val small = NativeStreamLineReader(maxPendingBytes = 16) { line -> lines.add(line) }
    val chunk = "x".repeat(32).toByteArray(Charsets.UTF_8)

    small.onBytes(chunk, chunk.size)

    assertEquals(1, lines.size)
    assertEquals(32, lines.single().length)
  }

  @Test
  fun `a partially delivered multi byte character survives until its bytes arrive`() {
    val encoded = "日志\n".toByteArray(Charsets.UTF_8)

    // The first three bytes are one character; the second read only completes it.
    reader.onBytes(encoded, 1)
    reader.onBytes(encoded.copyOfRange(1, encoded.size), encoded.size - 1)

    assertEquals(listOf("日志"), lines)
  }
}
