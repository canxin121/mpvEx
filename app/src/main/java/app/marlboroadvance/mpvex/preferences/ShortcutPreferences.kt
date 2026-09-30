package app.marlboroadvance.mpvex.preferences

import app.marlboroadvance.mpvex.R
import app.marlboroadvance.mpvex.i18n.localizedString
import app.marlboroadvance.mpvex.preferences.preference.Preference
import app.marlboroadvance.mpvex.preferences.preference.PreferenceStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * The custom mpv shortcuts, in the order the user arranged them.
 *
 * The list is stored as a single JSON string so it has no length limit and its order is
 * preserved; see [ShortcutListCodec].
 */
class ShortcutPreferences(
  private val preferenceStore: PreferenceStore,
) {
  /**
   * The saved list, or - while nothing has been saved yet - the four entries the previous
   * version kept in separate `shortcut_1..4_label` / `_key` preferences. Reading the old
   * keys without deleting them keeps a downgrade working.
   *
   * That fallback is recomputed on every read rather than captured once when Koin builds this
   * class, because on API 33+ the interface language lives in the framework store and a
   * language change recreates the activities but not the Koin singletons: a value captured at
   * construction time would still be in the old language afterwards. Merely opening the
   * settings page writes nothing, so `isSet()` stays false until the user edits.
   */
  val shortcuts: Preference<List<CustomShortcut>> =
    preferenceStore.getObject(
      key = ShortcutListCodec.KEY,
      defaultValue = emptyList(),
      serializer = ShortcutListCodec::encode,
      deserializer = ShortcutListCodec::decode,
    )

  /** The list to show, including the entries migrated from the previous version's keys. */
  fun shortcuts(): List<CustomShortcut> =
    if (this.shortcuts.isSet()) this.shortcuts.get() else legacyShortcuts(preferenceStore)

  /** The same list, re-read whenever the stored value changes. */
  fun shortcutsFlow(): Flow<List<CustomShortcut>> = this.shortcuts.changes().map { shortcuts() }

  fun save(shortcuts: List<CustomShortcut>) = this.shortcuts.set(shortcuts)

  fun add(shortcuts: List<CustomShortcut>): Pair<List<CustomShortcut>, CustomShortcut> {
    val added = CustomShortcut(id = newId(shortcuts))
    return (shortcuts + added) to added
  }

  private companion object {
    /** Ids only have to be unique within the list and stable across edits. */
    fun newId(existing: List<CustomShortcut>): String {
      val used = existing.mapTo(mutableSetOf()) { it.id }
      while (true) {
        val candidate = UUID.randomUUID().toString()
        if (used.add(candidate)) return candidate
      }
    }

    fun legacyShortcuts(preferenceStore: PreferenceStore): List<CustomShortcut> =
      (1..LEGACY_SLOT_COUNT).mapNotNull { number ->
        val label =
          ShortcutListCodec.sanitizeLabel(preferenceStore.getString("shortcut_${number}_label").get())
        val key = preferenceStore.getString("shortcut_${number}_key").get().trim()
        if (label.isBlank() && key.isBlank()) {
          null
        } else {
          CustomShortcut(id = "legacy-$number", label = label, key = key)
        }
      }

    /** The old UI always had exactly four slots. */
    const val LEGACY_SLOT_COUNT = 4
  }
}

fun displayLabel(
  label: String,
  position: Int,
): String = label.ifBlank { localizedString(R.string.shortcut_edit_title, position) }
