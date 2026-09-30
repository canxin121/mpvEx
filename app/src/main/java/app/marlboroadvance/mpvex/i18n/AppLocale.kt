package app.marlboroadvance.mpvex.i18n

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.annotation.RequiresApi
import androidx.preference.PreferenceManager
import java.util.Locale

/**
 * The interface language the user picked, and the machinery that applies it.
 *
 * The framework's own per-app language API is authoritative where it exists: on API 33+ the
 * system's "App language" page writes to the same [android.app.LocaleManager] this reads, so
 * letting it win keeps the two in sync. Below API 33 there is no such store (AppCompat's
 * auto-storing needs a service that is not in our merged manifest), so the choice lives in a
 * plain preference that travels in backups like every other setting.
 */
object AppLocale {
  const val KEY = "app_language"

  /** "Follow the system language". */
  const val SYSTEM = ""

  /**
   * Every language we ship, matching the `values-*` directories and the generated locale
   * config. Android exposes no runtime API for that config, so the list lives here and a unit
   * test keeps it aligned with the directories.
   */
  val supportedLocales =
    listOf("en-US", "de", "es", "fr", "ja", "ko", "pt-BR", "ru", "zh-CN", "zh-TW")

  /**
   * Seeded by `App.attachBaseContext` and refreshed by [set], so [localizedString] can reach
   * the right resources without every one of its call sites holding a context.
   */
  @Volatile
  internal var resourcesContext: Context? = null

  /** Android writes `pt-rBR`; BCP-47 and the framework API write `pt-BR`. */
  private val REGION_QUALIFIER = Regex("^([a-z]{2,3})-r([A-Z]{2})$")

  fun toLanguageTag(tag: String): String {
    val match = REGION_QUALIFIER.matchEntire(tag) ?: return tag
    return "${match.groupValues[1]}-${match.groupValues[2]}"
  }

  /** The framework's per-app locale list, empty below API 33 where the API does not exist. */
  @RequiresApi(Build.VERSION_CODES.TIRAMISU)
  private fun frameworkLocales(context: Context): LocaleList =
    context.getSystemService(android.app.LocaleManager::class.java)?.applicationLocales
      ?: LocaleList.getEmptyLocaleList()

  @RequiresApi(Build.VERSION_CODES.TIRAMISU)
  private fun setFrameworkLocales(
    context: Context,
    locales: LocaleList,
  ) {
    // The framework rejects a tag that is not in the generated locale config.
    runCatching { context.getSystemService(android.app.LocaleManager::class.java)?.applicationLocales = locales }
  }

  /** The saved choice, or [SYSTEM]. */
  fun selectedTag(context: Context): String {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      // The framework value is the whole truth here: an empty list means "follow the
      // system", and falling back to our own preference would fight the system setting.
      val locale = frameworkLocales(context).get(0)
      return if (locale == null) SYSTEM else matchSupported(locale.toLanguageTag())
    }
    val stored = storedTag(context)
    return if (stored.isBlank()) SYSTEM else matchSupported(stored)
  }

  /**
   * The preference value alone, ignoring the framework store. Used to spot a language that
   * arrived inside a restored backup, which only takes effect once [set] applies it.
   */
  fun storedTag(context: Context): String =
    PreferenceManager.getDefaultSharedPreferences(context).getString(KEY, SYSTEM).orEmpty()

  /**
   * Persists the choice and applies it to the running process. The resources wrapper is
   * refreshed, so both the Compose screens and the plain `localizedString` call sites follow
   * along as soon as the activities that display them are recreated - which the caller does,
   * by asking the activity to `recreate()` once the selection dialog closes.
   *
   * @return true when the choice actually changed.
   */
  fun set(context: Context, tag: String): Boolean {
    val context = context.applicationContext
    val normalized = if (tag.isBlank()) SYSTEM else matchSupported(tag)
    val changed = normalized != selectedTag(context)
    PreferenceManager.getDefaultSharedPreferences(context).edit().putString(KEY, normalized).apply()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      val locales =
        if (normalized == SYSTEM) {
          LocaleList.getEmptyLocaleList()
        } else {
          LocaleList.forLanguageTags(normalized)
        }
      setFrameworkLocales(context, locales)
    }
    resourcesContext = wrap(context)
    return changed
  }

  /**
   * Wraps [context] so its resources resolve in the chosen language. [configure] applies any
   * extra configuration the caller needs to the same [Configuration], so a caller that also
   * adjusts something else does not end up building the context twice.
   */
  fun wrap(
    context: Context,
    configure: (Configuration.() -> Unit)? = null,
  ): Context {
    val configuration = Configuration(context.resources.configuration)
    val tag = selectedTag(context)
    if (tag != SYSTEM) {
      val locale = Locale.forLanguageTag(tag)
      configuration.setLocale(locale)
      configuration.setLocales(LocaleList(locale))
    }
    configure?.invoke(configuration)
    return if (configuration == context.resources.configuration) {
      // Nothing to override: wrapping would only add a layer.
      context
    } else {
      context.createConfigurationContext(configuration)
    }
  }

  /**
   * The language's name in that language, so the picker costs no translations. Falls back to
   * the raw tag for something the platform does not know.
   */
  fun displayName(tag: String): String {
    if (tag == SYSTEM) return tag
    val locale = Locale.forLanguageTag(tag)
    return locale.getDisplayName(locale).replaceFirstChar { it.uppercase() }
  }

  /** Folds a tag onto one of [supportedLocales], or the first one if it is unknown. */
  private fun matchSupported(tag: String): String {
    val locale = Locale.forLanguageTag(toLanguageTag(tag))
    return supportedLocales.firstOrNull { Locale.forLanguageTag(it) == locale } ?: supportedLocales.first()
  }
}
