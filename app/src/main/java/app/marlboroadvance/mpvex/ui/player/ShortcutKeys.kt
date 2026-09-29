package app.marlboroadvance.mpvex.ui.player

import `is`.xyz.mpv.MPVLib

/** Sends one mpv key name through the normal input.conf key binding path. */
fun pressShortcutKey(key: String): Boolean {
  val name = key.trim()
  if (name.isEmpty() || name.any(Char::isWhitespace)) return false
  MPVLib.command("keypress", name)
  return true
}
