package app.marlboroadvance.mpvex.ui.player

/**
 * libmpv's JNI wrapper has one process-wide handle. Android can create a new
 * player Activity before the previous one's onDestroy, so ownership must be
 * transferred explicitly. Accessed only on the main thread.
 */
internal class PlaybackSessionOwner {
  private var owner: Any? = null
  private var releaseOwner: (() -> Unit)? = null

  fun acquire(newOwner: Any, release: () -> Unit) {
    check(owner !== newOwner) { "Player already owns the mpv session" }
    // Keep the old owner if cleanup fails; initializing another handle is unsafe.
    releaseOwner?.invoke()
    owner = newOwner
    releaseOwner = release
  }

  fun release(currentOwner: Any) {
    if (owner !== currentOwner) return
    releaseOwner?.invoke()
    owner = null
    releaseOwner = null
  }

  fun isOwner(candidate: Any): Boolean = owner === candidate
}
