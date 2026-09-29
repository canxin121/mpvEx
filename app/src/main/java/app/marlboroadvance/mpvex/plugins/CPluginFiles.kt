package app.marlboroadvance.mpvex.plugins

/** Only plain `.so` names can be copied into mpv's private scripts directory. */
fun isSafeCPluginFileName(fileName: String): Boolean {
  if (fileName.isBlank() || fileName.contains('/') || fileName.contains('\\')) {
    return false
  }
  val base = fileName.substringBeforeLast('.', "")
  return base.isNotBlank() && fileName.endsWith(".so", ignoreCase = true)
}

/**
 * The name mpv gives a script file's client, which is what `script-message-to`
 * targets.
 *
 * Mirrors `script_name_from_filename` in mpv's `player/scripting.c`: the file's
 * basename with the extension dropped, and every character outside `[A-Za-z0-9]`
 * replaced by `_`. Parsing the host's mpv log is the only way the app sees what
 * a plugin reported, and the client name is how those lines can be told apart
 * from a Lua script's, so the rule is reproduced here rather than guessed at the
 * call site.
 */
fun mpvScriptClientName(fileName: String): String {
  val base = fileName.substringAfterLast('/').substringAfterLast('\\').removePrefix("@").substringBeforeLast('.')
  return base.map { character -> if (character.isLetterOrDigit() && character.code < 128) character else '_' }.joinToString("")
}
