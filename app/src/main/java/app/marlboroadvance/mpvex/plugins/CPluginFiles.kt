package app.marlboroadvance.mpvex.plugins

/** Only plain `.so` names can be copied into mpv's private scripts directory. */
fun isSafeCPluginFileName(fileName: String): Boolean {
  if (fileName.isBlank() || fileName.contains('/') || fileName.contains('\\')) {
    return false
  }
  val base = fileName.substringBeforeLast('.', "")
  return base.isNotBlank() && fileName.endsWith(".so", ignoreCase = true)
}
