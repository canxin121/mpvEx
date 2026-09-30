package app.marlboroadvance.mpvex.preferences

import app.marlboroadvance.mpvex.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayerButtonTest {
  /**
   * Saved control layouts store the enum name, so renaming these silently drops the button
   * from every existing layout.
   */
  @Test
  fun `the numbered shortcut buttons keep their names`() {
    assertEquals(
      listOf("SHORTCUT_1", "SHORTCUT_2", "SHORTCUT_3", "SHORTCUT_4"),
      (1..4).map { PlayerButton.valueOf("SHORTCUT_$it").name },
    )
  }

  @Test
  fun `the numbered shortcut buttons keep their labels`() {
    assertEquals(
      listOf(
        R.string.button_shortcut_1,
        R.string.button_shortcut_2,
        R.string.button_shortcut_3,
        R.string.button_shortcut_4,
      ),
      listOf(
        playerButtonLabelRes(PlayerButton.SHORTCUT_1),
        playerButtonLabelRes(PlayerButton.SHORTCUT_2),
        playerButtonLabelRes(PlayerButton.SHORTCUT_3),
        playerButtonLabelRes(PlayerButton.SHORTCUT_4),
      ),
    )
  }

  @Test
  fun `each numbered shortcut button maps to the list position it stands for`() {
    assertEquals(1, PlayerButton.SHORTCUT_1.shortcutIndex)
    assertEquals(2, PlayerButton.SHORTCUT_2.shortcutIndex)
    assertEquals(3, PlayerButton.SHORTCUT_3.shortcutIndex)
    assertEquals(4, PlayerButton.SHORTCUT_4.shortcutIndex)
  }

  @Test
  fun `no other button claims a shortcut position`() {
    val others = PlayerButton.values().filter { it !in listOf(PlayerButton.SHORTCUT_1, PlayerButton.SHORTCUT_2, PlayerButton.SHORTCUT_3, PlayerButton.SHORTCUT_4) }

    assertEquals(emptyList<PlayerButton>(), others.filter { it.shortcutIndex != null })
    assertNull(PlayerButton.BACK_ARROW.shortcutIndex)
  }

  @Test
  fun `every button has a label`() {
    // playerButtonLabelRes is exhaustive over the enum, so this only guards against a
    // future button being given a resource that does not exist.
    PlayerButton.values().forEach { playerButtonLabelRes(it) }
  }

  @Test
  fun `the shortcuts panel button is offered in the control layout editor`() {
    assertEquals(true, PlayerButton.SHORTCUTS in allPlayerButtons)
  }
}
