package app.marlboroadvance.mpvex.utils.logging

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import timber.log.Timber

class MpvExLogTest {
  private val records = mutableListOf<LogRecord>()

  @Before
  fun setUp() {
    LogLevel.setVerbose(false)
    LogDestinations.install(listOf { records += it })
    Timber.uprootAll()
    Timber.plant(MpvExLogDestinationTree())
  }

  @After
  fun tearDown() {
    Timber.uprootAll()
    LogDestinations.install(emptyList())
    LogLevel.setVerbose(false)
  }

  @Test
  fun `a log call reaches the installed destination`() {
    MpvExLog.d("Player", "Opening %s", "file.mkv")

    assertEquals(1, records.size)
    assertEquals("Player", records.single().tag)
    assertEquals(3, records.single().priority)
    assertEquals("Opening file.mkv", records.single().message)
    assertEquals(LogSource.APP, records.single().source)
  }

  @Test
  fun `a throwable is appended to the message and comes second in the call`() {
    val error = IllegalStateException("boom")
    MpvExLog.e("Player", error, "Could not open %s", "file.mkv")

    val record = records.single()
    assertEquals(6, record.priority)
    assertTrue(record.message.startsWith("Could not open file.mkv"))
    assertTrue(record.message.contains("IllegalStateException: boom"))
  }

  @Test
  fun `verbose lines are dropped until verbose logging is on`() {
    MpvExLog.v("Player", "noise")
    assertTrue(records.isEmpty())

    LogLevel.setVerbose(true)
    MpvExLog.v("Player", "noise")

    assertEquals(1, records.size)
    assertEquals(2, records.single().priority)
  }

  @Test
  fun `a line produced outside app code keeps its own source`() {
    MpvExLog.external(5, LogSource.MPV, "mpv", "[cplayer] Playing")

    val record = records.single()
    assertEquals(LogSource.MPV, record.source)
    assertEquals("[cplayer] Playing", record.message)
  }

  @Test
  fun `the logcat tag keeps the app prefix inside logcat's limit`() {
    assertEquals("MpvEx/Player", MpvExLog.logcatTag("Player"))
    assertEquals("MpvEx/a_b", MpvExLog.logcatTag("a b"))
    assertTrue(MpvExLog.logcatTag("a".repeat(64)).length <= 23)
    assertFalse(MpvExLog.logcatTag("a".repeat(64)).endsWith("/"))
  }
}
