package app.marlboroadvance.mpvex.utils.logging

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RotatingLogFileTest {
  @get:Rule
  val folder = TemporaryFolder()

  private val directory: File get() = folder.root

  private fun file(name: String) = File(directory, name)

  private fun lines(file: File): List<String> = file.readLines()

  @Test
  fun `appends the lines it is given`() {
    val log = RotatingLogFile(directory, "app.log", maxSizeBytes = 1024, maxFileCount = 3)

    log.append("first")
    log.append("second")
    log.close()

    assertEquals(listOf("first", "second"), lines(file("app.log")))
  }

  @Test
  fun `rotates once the file passes the size limit`() {
    val log = RotatingLogFile(directory, "app.log", maxSizeBytes = 30, maxFileCount = 3)

    log.append("x".repeat(30))
    // Shorter than the limit, so this line stays in the fresh file rather than
    // rotating again.
    log.append("after rotation")
    log.close()

    assertTrue(file("app.log.1").exists())
    assertEquals(listOf("x".repeat(30)), lines(file("app.log.1")))
    assertEquals(listOf("after rotation"), lines(file("app.log")))
  }

  @Test
  fun `keeps at most maxFileCount files and drops the oldest generations`() {
    // Two seven byte lines fill a fourteen byte file, so every second append
    // rotates.
    val log = RotatingLogFile(directory, "app.log", maxSizeBytes = 14, maxFileCount = 3)

    repeat(10) { index -> log.append("entry$index") }
    log.close()

    assertTrue(file("app.log.1").exists())
    assertTrue(file("app.log.2").exists())
    // The generation beyond the limit is dropped rather than kept forever.
    assertFalse(file("app.log.3").exists())
    // Three files survive: the newest pair is still in app.log, and the two
    // generations before it have shifted up.
    assertEquals(listOf("entry8", "entry9"), lines(file("app.log")))
    assertEquals(listOf("entry6", "entry7"), lines(file("app.log.1")))
    assertEquals(listOf("entry4", "entry5"), lines(file("app.log.2")))
  }

  @Test
  fun `reopens and keeps appending to an existing file`() {
    RotatingLogFile(directory, "app.log", maxSizeBytes = 1024, maxFileCount = 3).apply {
      append("before restart")
      close()
    }

    RotatingLogFile(directory, "app.log", maxSizeBytes = 1024, maxFileCount = 3).apply {
      append("after restart")
      close()
    }

    assertEquals(listOf("before restart", "after restart"), lines(file("app.log")))
  }

  @Test
  fun `an unwritable directory does not throw`() {
    val log = RotatingLogFile(File(directory, "missing/child"), "app.log", maxSizeBytes = 1024, maxFileCount = 3)

    log.append("still fine")
    log.close()
  }
}
