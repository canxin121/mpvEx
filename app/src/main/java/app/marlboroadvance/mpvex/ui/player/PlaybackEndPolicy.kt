package app.marlboroadvance.mpvex.ui.player

/** An exhausted network read or an obsolete EOF callback is not a completed video. */
internal fun isPlaybackComplete(
  eof: Boolean,
  seeking: Boolean,
  position: Double?,
  duration: Double?,
): Boolean {
  if (!eof || seeking) return false
  if (duration == null || !duration.isFinite() || duration <= 0) return true
  if (position == null || !position.isFinite()) return false
  return position >= duration - minOf(1.0, duration * 0.01)
}
