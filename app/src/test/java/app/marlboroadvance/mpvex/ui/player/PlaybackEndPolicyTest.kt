package app.marlboroadvance.mpvex.ui.player

import org.junit.Assert.*
import org.junit.Test

class PlaybackEndPolicyTest {
  @Test fun `HTTP read ending halfway through a video must not close or advance player`() {
    assertFalse(isPlaybackComplete(true, false, 42.0, 180.0))
  }

  @Test fun `queued EOF cleared by a newer seek must be ignored`() {
    assertFalse(isPlaybackComplete(false, false, 180.0, 180.0))
  }

  @Test fun `EOF during a pending or active seek must be ignored`() {
    assertFalse(isPlaybackComplete(true, true, 180.0, 180.0))
  }

  @Test fun `natural completion still honors autoplay and close after EOF`() {
    assertTrue(isPlaybackComplete(true, false, 179.98, 180.0))
    assertTrue(isPlaybackComplete(true, false, 180.0, 180.0))
    assertTrue(isPlaybackComplete(true, false, 42.0, null))
  }

  @Test fun `missing position during reload cannot report a completed known video`() {
    assertFalse(isPlaybackComplete(true, false, null, 180.0))
    assertFalse(isPlaybackComplete(true, false, Double.NaN, 180.0))
  }

  @Test fun `subsecond videos are not considered complete at the beginning`() {
    assertFalse(isPlaybackComplete(true, false, 0.0, 0.4))
    assertTrue(isPlaybackComplete(true, false, 0.399, 0.4))
  }
}
