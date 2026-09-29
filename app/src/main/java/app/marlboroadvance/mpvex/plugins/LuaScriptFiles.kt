package app.marlboroadvance.mpvex.plugins

/** Only plain Lua/JavaScript file names can be copied into mpv's private scripts directory. */
fun isSafeScriptFileName(name: String): Boolean {
  if (name.isBlank() || name.contains('/') || name.contains('\\') || name.any { it.code < 0x20 || it.code == 0x7f }) {
    return false
  }
  val base = name.substringBeforeLast('.', "")
  return base.isNotBlank() && hasScriptFileExtension(name)
}

/** Also matches old script files whose names are no longer accepted for imports. */
fun hasScriptFileExtension(name: String): Boolean =
  name.endsWith(".lua", ignoreCase = true) || name.endsWith(".js", ignoreCase = true)
