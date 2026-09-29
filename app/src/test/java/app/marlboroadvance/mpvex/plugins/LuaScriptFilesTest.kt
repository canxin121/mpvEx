package app.marlboroadvance.mpvex.plugins

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LuaScriptFilesTest {
  @Test
  fun `accepts plain Lua and JavaScript file names`() {
    assertTrue(isSafeScriptFileName("example.lua"))
    assertTrue(isSafeScriptFileName("my.script.LUA"))
    assertTrue(isSafeScriptFileName("example.js"))
  }

  @Test
  fun `rejects paths, control characters, and C plugins`() {
    listOf("", ".lua", "../example.lua", "sub/example.lua", "sub\\example.js", "bad\n.lua", "plugin.so")
      .forEach { assertFalse("Unexpectedly accepted $it", isSafeScriptFileName(it)) }
  }

  @Test
  fun `cleanup identifies old script files without touching C plugins`() {
    assertTrue(hasScriptFileExtension(".lua"))
    assertTrue(hasScriptFileExtension("OLD.JS"))
    assertFalse(hasScriptFileExtension("plugin.so"))
    assertFalse(hasScriptFileExtension("file.lua.bak"))
  }
}
