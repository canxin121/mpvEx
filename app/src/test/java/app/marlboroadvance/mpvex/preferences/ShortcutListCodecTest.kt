package app.marlboroadvance.mpvex.preferences

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShortcutListCodecTest {
  @Test
  fun `round trips a list and keeps its order`() {
    val shortcuts =
      listOf(
        CustomShortcut(id = "a", label = "Screenshot", key = "F9"),
        CustomShortcut(id = "b", label = "", key = "Ctrl+Alt+s"),
      )

    assertEquals(shortcuts, ShortcutListCodec.decode(ShortcutListCodec.encode(shortcuts)))
  }

  @Test
  fun `damaged or absent json decodes to an empty list`() {
    assertEquals(emptyList<CustomShortcut>(), ShortcutListCodec.decode(""))
    assertEquals(emptyList<CustomShortcut>(), ShortcutListCodec.decode("   "))
    assertEquals(emptyList<CustomShortcut>(), ShortcutListCodec.decode("not json at all"))
    assertEquals(emptyList<CustomShortcut>(), ShortcutListCodec.decode("""{"key":"F9"}"""))
  }

  @Test
  fun `entries without an id are dropped and duplicate ids collapse`() {
    val decoded =
      ShortcutListCodec.decode(
        """[{"id":"","key":"F9"},{"id":"a","key":"F8"},{"id":"a","key":"F7"}]""",
      )

    assertEquals(listOf(CustomShortcut(id = "a", label = "", key = "F8")), decoded)
  }

  @Test
  fun `unknown keys in stored json are ignored`() {
    val decoded = ShortcutListCodec.decode("""[{"id":"a","key":"F9","future":"x"}]""")

    assertEquals(listOf(CustomShortcut(id = "a", label = "", key = "F9")), decoded)
  }

  @Test
  fun `the old english placeholder name is sanitized away`() {
    assertEquals("", ShortcutListCodec.sanitizeLabel("Shortcut 3"))
    assertEquals("", ShortcutListCodec.sanitizeLabel("   "))
    // Anything the user typed themselves is kept, including lookalikes.
    assertEquals("Shortcut", ShortcutListCodec.sanitizeLabel("Shortcut"))
    assertEquals("My shortcut", ShortcutListCodec.sanitizeLabel("My shortcut"))
  }

  @Test
  fun `encoding an empty list yields a decodable empty list`() {
    val encoded = ShortcutListCodec.encode(emptyList())

    assertTrue(encoded.isNotBlank())
    assertEquals(emptyList<CustomShortcut>(), ShortcutListCodec.decode(encoded))
  }
}
