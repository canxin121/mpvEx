package app.marlboroadvance.mpvex.plugins

import androidx.annotation.StringRes
import app.marlboroadvance.mpvex.i18n.localizedString

/** Resolve plugin diagnostics at the UI boundary, using the device locale. */
fun interface CPluginMessages {
  fun get(@StringRes id: Int, vararg args: Any?): String
}

object AndroidCPluginMessages : CPluginMessages {
  override fun get(id: Int, vararg args: Any?): String = localizedString(id, *args)
}
