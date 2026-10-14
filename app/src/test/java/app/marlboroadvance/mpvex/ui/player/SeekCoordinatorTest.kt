package app.marlboroadvance.mpvex.ui.player

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SeekCoordinatorTest {
  private val playing = SeekState(60.0, 600.0, seekable = true, precise = false)

  @Test fun `rapid drag keeps only latest target and flush does not repeat it`() = runTest {
    val seeks = mutableListOf<Double>()
    val coordinator = SeekCoordinator(this, { playing }, { p, _ -> seeks += p })
    repeat(500) { coordinator.seekTo(it.toDouble()) }
    assertTrue(coordinator.hasPendingSeek)
    coordinator.flush()
    advanceUntilIdle()
    assertEquals(listOf(499.0), seeks)
    assertFalse(coordinator.hasPendingSeek)
  }

  @Test fun `continuous scrubbing stays bounded and preserves the last position`() = runTest {
    val seeks = mutableListOf<Double>()
    val coordinator = SeekCoordinator(this, { playing }, { p, _ -> seeks += p })
    repeat(120) {
      coordinator.seekTo(it.toDouble())
      advanceTimeBy(8)
    }
    coordinator.flush()
    advanceUntilIdle()
    assertTrue("120 touch events should produce at most 10 native seeks", seeks.size <= 10)
    assertEquals(119.0, seeks.last(), 0.0)
  }

  @Test fun `absolute seek supersedes queued forward presses and later backward press is retained`() = runTest {
    val seeks = mutableListOf<Double>()
    val coordinator = SeekCoordinator(this, { playing }, { p, _ -> seeks += p })
    coordinator.seekBy(10.0)
    coordinator.seekBy(10.0)
    coordinator.seekTo(200.0)
    coordinator.seekBy(-30.0)
    advanceUntilIdle()
    assertEquals(listOf(170.0), seeks)
  }

  @Test fun `relative presses accumulate using position at execution time`() = runTest {
    var state = playing
    val seeks = mutableListOf<Double>()
    val coordinator = SeekCoordinator(this, { state }, { p, _ -> seeks += p })
    coordinator.seekBy(10.0)
    coordinator.seekBy(10.0)
    coordinator.seekBy(-5.0)
    state = playing.copy(position = 65.0)
    advanceUntilIdle()
    assertEquals(listOf(80.0), seeks)
  }

  @Test fun `destroy cancels pending work before any native read and rejects future requests`() = runTest {
    var reads = 0
    val coordinator = SeekCoordinator(this, { reads++; playing }, { _, _ -> fail("seek after destroy") })
    coordinator.seekTo(100.0)
    runCurrent()
    advanceTimeBy(99)
    coordinator.close()
    coordinator.seekBy(10.0)
    coordinator.seekTo(20.0)
    coordinator.flush()
    advanceUntilIdle()
    assertEquals(0, reads)
  }

  @Test fun `changing files cancels old requests but allows seeking the new file`() = runTest {
    val seeks = mutableListOf<Double>()
    val coordinator = SeekCoordinator(this, { playing }, { p, _ -> seeks += p })
    coordinator.seekTo(300.0)
    coordinator.cancel()
    advanceUntilIdle()
    assertTrue(seeks.isEmpty())
    coordinator.seekTo(20.0)
    advanceUntilIdle()
    assertEquals(listOf(20.0), seeks)
  }

  @Test fun `unloaded player drops the request without native command`() = runTest {
    val coordinator = SeekCoordinator(this, { null }, { _, _ -> fail("unloaded player") })
    coordinator.seekTo(100.0)
    advanceUntilIdle()
    assertFalse(coordinator.hasPendingSeek)
  }

  @Test fun `invalid input cannot poison the pending target`() = runTest {
    val seeks = mutableListOf<Double>()
    val coordinator = SeekCoordinator(this, { playing }, { p, _ -> seeks += p })
    coordinator.seekTo(100.0)
    coordinator.seekTo(Double.NaN)
    coordinator.seekBy(Double.POSITIVE_INFINITY)
    advanceUntilIdle()
    assertEquals(listOf(100.0), seeks)
  }

  @Test fun `seeks clamp to beginning and playable end without generating EOF`() {
    assertEquals(0.0, playing.target(-100.0)!!.position, 0.0)
    assertEquals(SeekTarget(599.0, true), playing.target(600.0))
    assertEquals(SeekTarget(599.0, true), playing.target(Int.MAX_VALUE.toDouble()))
    assertEquals(SeekTarget(0.2, true), playing.copy(duration = 0.4).target(10.0))
  }

  @Test fun `invalid duration and unseekable streams do not jump to zero`() {
    listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY).forEach {
      assertNull(playing.copy(duration = it).target(30.0))
    }
    assertNull(playing.copy(seekable = false).target(30.0))
    assertNull(playing.target(Double.NaN))
  }

  @Test fun `AB bounds preserve fractions support reversed markers and clamp relative seeks`() = runTest {
    val state = playing.copy(loopA = 20.75, loopB = 10.25)
    assertEquals(SeekTarget(10.25, true), state.target(0.0))
    assertEquals(SeekTarget(20.75, true), state.target(100.0))
    val seeks = mutableListOf<SeekTarget>()
    val coordinator = SeekCoordinator(this, { state }, { p, exact -> seeks += SeekTarget(p, exact) })
    coordinator.seekBy(100.0)
    advanceUntilIdle()
    assertEquals(listOf(SeekTarget(20.75, true)), seeks)
  }

  @Test fun `stale AB bounds cannot create an empty range`() {
    assertEquals(SeekTarget(599.0, true), playing.copy(loopA = 800.0, loopB = 900.0).target(30.0))
    assertNotNull(playing.copy(loopA = Double.NaN, loopB = 30.0).target(10.0))
  }

  @Test fun `normal keyframe preference and precise short clips are retained`() {
    assertFalse(playing.target(30.0)!!.exact)
    assertTrue(playing.copy(precise = true).target(30.0)!!.exact)
    assertTrue(playing.copy(duration = 60.0).target(30.0)!!.exact)
  }
}
