package app.marlboroadvance.mpvex.presentation.crash

import java.io.File

/**
 * Finds a native crash dump left behind by the system.
 *
 * Android writes tombstones to a place an ordinary app cannot read, but the
 * crash dialog and some emulators leave a copy next to the app's own files, and
 * libmpv's crash handling can drop one near its configuration directory. When
 * neither exists this is simply empty, which is the common case.
 */
object NativeLogFinder {
  private val NAME_PATTERNS = listOf("tombstone", "mpv-crash", "crash-", "native-crash")

  private const val MAX_SCAN_DEPTH = 3
  private const val MAX_REPORT_BYTES = 256 * 1024

  /** The most recently modified native crash dump, or an empty string. */
  fun find(vararg directories: File): String {
    val newest =
      directories
        .filter { it.isDirectory }
        .flatMap { candidates(it, MAX_SCAN_DEPTH) }
        .maxByOrNull { it.lastModified() }
        ?: return ""
    return runCatching { newest.readText().take(MAX_REPORT_BYTES) }.getOrDefault("")
  }

  private fun candidates(
    directory: File,
    depth: Int,
  ): List<File> {
    if (depth < 0) return emptyList()
    val children = directory.listFiles() ?: return emptyList()
    return children.flatMap { file ->
      when {
        file.isDirectory -> candidates(file, depth - 1)
        file.isFile && NAME_PATTERNS.any { pattern -> file.name.contains(pattern, ignoreCase = true) } ->
          listOf(file)
        else -> emptyList()
      }
    }
  }
}
