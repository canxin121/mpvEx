package app.marlboroadvance.mpvex.i18n

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import app.marlboroadvance.mpvex.App

/**
 * Access app resources from UI callbacks and model code that have no Compose context.
 *
 * Resolution goes through whichever context has the interface language applied - the wrapper
 * seeded by `App.attachBaseContext` - and only falls back to the Application itself, which is
 * also what happens in plain JVM tests where no context exists at all.
 */
private val resourcesContext: android.content.Context
  get() = AppLocale.resourcesContext ?: App.instance

fun localizedString(@StringRes id: Int, vararg args: Any?): String =
  resourcesContext.getString(id, *args)

fun localizedQuantityString(@PluralsRes id: Int, quantity: Int, vararg args: Any?): String =
  resourcesContext.resources.getQuantityString(id, quantity, *args)
