package app.marlboroadvance.mpvex.utils.logging

import android.util.Log

/**
 * mpv's log levels, mirroring `MPVLib.MpvLogLevel`.
 *
 * Declared here rather than read from the library so the mapping can be unit
 * tested on the JVM, where loading the native MPV classes is not possible.
 */
object MpvLogLevels {
  const val NONE = 0
  const val FATAL = 10
  const val ERROR = 20
  const val WARN = 30
  const val INFO = 40
  const val VERBOSE = 50
  const val DEBUG = 60
  const val TRACE = 70
}

/**
 * Maps an mpv log level onto the `android.util.Log` scale.
 *
 * mpv has two levels logcat has no equivalent for: everything below ERROR is
 * folded onto the nearest one, and the caller adds a word to the message so the
 * distinction is not lost.
 */
fun mpvLevelToPriority(level: Int): Int =
  when (level) {
    MpvLogLevels.FATAL -> Log.ERROR
    MpvLogLevels.ERROR -> Log.ERROR
    MpvLogLevels.WARN -> Log.WARN
    MpvLogLevels.INFO -> Log.INFO
    MpvLogLevels.VERBOSE -> Log.VERBOSE
    MpvLogLevels.DEBUG -> Log.DEBUG
    MpvLogLevels.TRACE -> Log.VERBOSE
    else -> Log.DEBUG
  }
