package app.marlboroadvance.mpvex.preferences

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * One user-defined mobile shortcut: an mpv key plus the name to show for it.
 *
 * [id] is what the settings list keys its rows and dialogs on, so renaming or reordering
 * never confuses Compose about which row it is looking at.
 */
@Serializable
data class CustomShortcut(
  val id: String,
  val label: String = "",
  val key: String = "",
)

/**
 * Reads and writes the whole shortcut list as one JSON string preference, so the list can
 * grow without limit and its order is simply the order in the array.
 */
object ShortcutListCodec {
  const val KEY = "custom_shortcuts"

  /**
   * Names the old four-slot UI wrote for an unnamed shortcut. Those were English literals
   * compared against the saved value, so they can no longer be produced; a stored one is
   * just an empty name that should follow the current position instead.
   */
  private val LEGACY_DEFAULT_LABEL = Regex("""^Shortcut \d+$""")

  private val json =
    Json {
      ignoreUnknownKeys = true
      encodeDefaults = true
    }

  /** Damaged or absent JSON yields an empty list rather than an exception. */
  fun decode(raw: String): List<CustomShortcut> {
    if (raw.isBlank()) return emptyList()
    val decoded =
      runCatching { json.decodeFromString<List<CustomShortcut>>(raw) }.getOrElse { return emptyList() }
    val seen = mutableSetOf<String>()
    return decoded.filter { it.id.isNotBlank() && seen.add(it.id) }
  }

  fun encode(shortcuts: List<CustomShortcut>): String = json.encodeToString(shortcuts)

  /** Turns a name left over from the old UI into a blank one. */
  fun sanitizeLabel(label: String): String =
    if (label.isBlank() || LEGACY_DEFAULT_LABEL.matches(label)) "" else label
}
