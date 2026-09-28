package app.marlboroadvance.mpvex.preferences

import app.marlboroadvance.mpvex.preferences.preference.Preference
import app.marlboroadvance.mpvex.preferences.preference.PreferenceStore

enum class ShortcutSlot(val number: Int) {
  ONE(1),
  TWO(2),
  THREE(3),
  FOUR(4),
}

class ShortcutPreferences(preferenceStore: PreferenceStore) {
  private val labels = ShortcutSlot.entries.associateWith { slot ->
    preferenceStore.getString("shortcut_${slot.number}_label", "Shortcut ${slot.number}")
  }
  private val keys = ShortcutSlot.entries.associateWith { slot ->
    preferenceStore.getString("shortcut_${slot.number}_key")
  }

  fun label(slot: ShortcutSlot): Preference<String> = labels.getValue(slot)

  fun key(slot: ShortcutSlot): Preference<String> = keys.getValue(slot)
}

fun PlayerButton.shortcutSlot(): ShortcutSlot? = when (this) {
  PlayerButton.SHORTCUT_1 -> ShortcutSlot.ONE
  PlayerButton.SHORTCUT_2 -> ShortcutSlot.TWO
  PlayerButton.SHORTCUT_3 -> ShortcutSlot.THREE
  PlayerButton.SHORTCUT_4 -> ShortcutSlot.FOUR
  else -> null
}
