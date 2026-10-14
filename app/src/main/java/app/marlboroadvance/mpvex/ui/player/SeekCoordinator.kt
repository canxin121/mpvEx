package app.marlboroadvance.mpvex.ui.player

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Coalesces drag frames and repeated buttons into one ordered seek. All methods,
 * including cancellation before libmpv is destroyed, run on the player's main thread.
 * No native call or pending request may outlive this coordinator.
 */
internal class SeekCoordinator(
  private val scope: CoroutineScope,
  private val readState: () -> SeekState?,
  private val seek: (position: Double, exact: Boolean) -> Unit,
  private val intervalMs: Long = 100L,
) {
  private var absolutePosition: Double? = null
  private var relativeOffset = 0.0
  private var job: Job? = null
  private var closed = false

  val hasPendingSeek: Boolean
    get() = absolutePosition != null || relativeOffset != 0.0

  fun seekTo(position: Double) {
    if (closed || !position.isFinite()) return
    absolutePosition = position
    relativeOffset = 0.0
    schedule()
  }

  fun seekBy(offset: Double) {
    if (closed || !offset.isFinite() || offset == 0.0) return
    relativeOffset += offset
    schedule()
  }

  private fun schedule() {
    if (job != null) return
    job = scope.launch {
      delay(intervalMs)
      job = null
      applyPendingSeek()
    }
  }

  /** Commit the last drag position before resuming playback. */
  fun flush() {
    job?.cancel()
    job = null
    applyPendingSeek()
  }

  /** Also used before loading another file, so old drag frames cannot seek it. */
  fun cancel() {
    job?.cancel()
    job = null
    absolutePosition = null
    relativeOffset = 0.0
  }

  fun close() {
    closed = true
    cancel()
  }

  private fun applyPendingSeek() {
    if (closed || !hasPendingSeek) return
    val absolute = absolutePosition
    val offset = relativeOffset
    absolutePosition = null
    relativeOffset = 0.0

    // Read at execution time: duration/seekability can change during network loading.
    val state = readState() ?: return
    val target = state.target((absolute ?: state.position) + offset) ?: return
    seek(target.position, target.exact)
  }
}

internal data class SeekState(
  val position: Double,
  val duration: Double,
  val seekable: Boolean,
  val precise: Boolean,
  val loopA: Double? = null,
  val loopB: Double? = null,
) {
  fun target(requested: Double): SeekTarget? {
    if (!seekable || !requested.isFinite() || !duration.isFinite() || duration <= 0) return null

    // Seeking to/past EOF used to close the player (and race outstanding JNI calls).
    // Leave room for the last frame, including sub-second clips, without forcing EOF.
    val lastPosition = (duration - minOf(1.0, duration / 2)).coerceAtLeast(0.0)
    var lower = 0.0
    var upper = lastPosition
    val hasLoop = loopA?.isFinite() == true && loopB?.isFinite() == true
    if (hasLoop) {
      lower = minOf(loopA!!, loopB!!).coerceIn(0.0, lastPosition)
      upper = maxOf(loopA, loopB).coerceIn(lower, lastPosition)
    }
    val clamped = requested.coerceIn(lower, upper)
    // A keyframe beyond the boundary can still trigger EOF or escape an A-B loop.
    return SeekTarget(clamped, precise || duration < 120 || hasLoop || clamped >= lastPosition)
  }
}

internal data class SeekTarget(val position: Double, val exact: Boolean)
