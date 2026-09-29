package app.marlboroadvance.mpvex.i18n

import androidx.annotation.StringRes
import androidx.annotation.PluralsRes
import app.marlboroadvance.mpvex.App

/** Access app resources from UI callbacks and model code that have no Compose context. */
fun localizedString(@StringRes id: Int, vararg args: Any?): String =
  App.instance.getString(id, *args)

fun localizedQuantityString(@PluralsRes id: Int, quantity: Int, vararg args: Any?): String =
  App.instance.resources.getQuantityString(id, quantity, *args)
