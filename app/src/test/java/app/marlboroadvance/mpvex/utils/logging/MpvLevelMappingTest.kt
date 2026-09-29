package app.marlboroadvance.mpvex.utils.logging

import org.junit.Assert.assertEquals
import org.junit.Test

class MpvLevelMappingTest {
  @Test
  fun `error and fatal both land on the error level`() {
    assertEquals(6, mpvLevelToPriority(MpvLogLevels.FATAL))
    assertEquals(6, mpvLevelToPriority(MpvLogLevels.ERROR))
  }

  @Test
  fun `warning and info keep their own levels`() {
    assertEquals(5, mpvLevelToPriority(MpvLogLevels.WARN))
    assertEquals(4, mpvLevelToPriority(MpvLogLevels.INFO))
  }

  @Test
  fun `mpv's two extra levels fold onto the nearest logcat level`() {
    // mpv "V" is noisier than logcat's verbose, and trace noisier still, so
    // both only survive when verbose logging is on.
    assertEquals(2, mpvLevelToPriority(MpvLogLevels.VERBOSE))
    assertEquals(2, mpvLevelToPriority(MpvLogLevels.TRACE))
    assertEquals(3, mpvLevelToPriority(MpvLogLevels.DEBUG))
  }

  @Test
  fun `an unknown level is not silently swallowed`() {
    assertEquals(3, mpvLevelToPriority(MpvLogLevels.NONE))
    assertEquals(3, mpvLevelToPriority(999))
    assertEquals(3, mpvLevelToPriority(-1))
  }
}
