package app.marlboroadvance.mpvex.i18n

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Locale

class AppLocaleTest {
  /**
   * The picker's list has to match what we actually ship. Android offers no runtime API for
   * the generated `locale-config`, and `resources.properties` only names the default, so the
   * `values-*` directories are the one source of truth both sides can be checked against.
   */
  @Test
  fun `supported locales match the shipped translations`() {
    val resDir = File("src/main/res")
    val shipped =
      resDir
        .listFiles { file -> file.isDirectory && file.name.startsWith("values-") }
        .orEmpty()
        .map { AppLocale.toLanguageTag(it.name.removePrefix("values-")) }
        .filter { it != "night" && !it.contains("-v") }
        .toSet()

    val declared = AppLocale.supportedLocales.filter { it != "en-US" }.toSet()

    assertEquals(shipped, declared)
  }

  @Test
  fun `every supported locale is a well formed language tag`() {
    AppLocale.supportedLocales.forEach { tag ->
      assertTrue(tag, Locale.forLanguageTag(tag).language.isNotEmpty())
    }
  }

  @Test
  fun `the android region qualifier maps onto the language tag the picker uses`() {
    assertEquals("pt-BR", AppLocale.toLanguageTag("pt-rBR"))
    assertEquals("zh-CN", AppLocale.toLanguageTag("zh-rCN"))
    assertEquals("de", AppLocale.toLanguageTag("de"))
  }

  @Test
  fun `language names are shown in their own language`() {
    assertEquals("Deutsch", AppLocale.displayName("de"))
    assertEquals("日本語", AppLocale.displayName("ja"))
    assertEquals("Français", AppLocale.displayName("fr"))
  }
}
