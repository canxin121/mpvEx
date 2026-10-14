package app.marlboroadvance.mpvex.ui.player

import org.junit.Assert.*
import org.junit.Test

class PlaybackSessionOwnerTest {
  @Test fun `opening again before old onDestroy releases the old native handle first`() {
    val session = PlaybackSessionOwner()
    val events = mutableListOf<String>()
    val oldActivity = Any()
    val newActivity = Any()
    session.acquire(oldActivity) { events += "destroy old mpv" }
    events += "create old mpv"
    session.acquire(newActivity) { events += "destroy new mpv" }
    events += "create new mpv"
    assertEquals(listOf("create old mpv", "destroy old mpv", "create new mpv"), events)

    // Android finally destroys the old Activity after the replacement is playing.
    session.release(oldActivity)
    assertEquals(3, events.size)
    assertTrue(session.isOwner(newActivity))
    session.release(newActivity)
    assertEquals("destroy new mpv", events.last())
  }

  @Test fun `repeated activity cleanup cannot destroy mpv twice`() {
    val session = PlaybackSessionOwner()
    val activity = Any()
    var destroyed = 0
    session.acquire(activity) { destroyed++ }
    session.release(activity)
    session.release(activity)
    assertEquals(1, destroyed)
    assertFalse(session.isOwner(activity))
  }

  @Test fun `failed teardown prevents a second native initialization`() {
    val session = PlaybackSessionOwner()
    val oldActivity = Any()
    val newActivity = Any()
    session.acquire(oldActivity) { error("native handle still alive") }
    assertThrows(IllegalStateException::class.java) { session.acquire(newActivity) {} }
    assertTrue(session.isOwner(oldActivity))
    assertFalse(session.isOwner(newActivity))
  }

  @Test fun `same activity cannot initialize a second handle`() {
    val session = PlaybackSessionOwner()
    val activity = Any()
    session.acquire(activity) {}
    assertThrows(IllegalStateException::class.java) { session.acquire(activity) {} }
  }
}
