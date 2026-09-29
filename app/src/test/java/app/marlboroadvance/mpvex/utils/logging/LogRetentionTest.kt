package app.marlboroadvance.mpvex.utils.logging

import java.io.File
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LogRetentionTest {
  @get:Rule
  val folder = TemporaryFolder()

  private fun logFile(
    name: String,
    ageDays: Long,
  ): File =
    File(folder.root, name).apply {
      writeText(name)
      setLastModified(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(ageDays))
    }

  @Test
  fun `deletes files older than the retention window`() {
    val fresh = logFile("app.log", ageDays = 1)
    val stale = logFile("app.log.4", ageDays = 30)

    val removed = LogRetention.cleanup(folder.root, retentionDays = 7)

    assertEquals(1, removed)
    assertTrue(fresh.exists())
    assertTrue(!stale.exists())
  }

  @Test
  fun `keeps everything inside the retention window`() {
    val a = logFile("app.log", ageDays = 0)
    val b = logFile("app.log.1", ageDays = 6)

    assertEquals(0, LogRetention.cleanup(folder.root, retentionDays = 7))
    assertTrue(a.exists() && b.exists())
  }

  @Test
  fun `a missing directory is not an error`() {
    assertEquals(0, LogRetention.cleanup(File(folder.root, "absent"), retentionDays = 7))
  }
}
