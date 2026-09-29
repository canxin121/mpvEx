package app.marlboroadvance.mpvex.utils.logging

import android.util.Log

/**
 * The single place that decides which log calls are recorded.
 *
 * Every destination asks here, so raising or lowering the level at runtime (the
 * "Verbose logging" switch) takes effect for logcat, the log file and the
 * in-memory buffer at once.
 */
object LogLevel {
  private const val VERBOSE = Log.VERBOSE
  private const val NORMAL = Log.DEBUG

  @Volatile
  private var minimum: Int = NORMAL

  fun minimumPriority(): Int = minimum

  fun setVerbose(verbose: Boolean) {
    minimum = if (verbose) VERBOSE else NORMAL
  }
}
