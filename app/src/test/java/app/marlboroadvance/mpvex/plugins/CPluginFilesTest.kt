package app.marlboroadvance.mpvex.plugins

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CPluginFilesTest {
  @Test
  fun `a plain shared object name is accepted`() {
    assertTrue(isSafeCPluginFileName("libmpv_stt_plugin_rs.so"))
    assertTrue(isSafeCPluginFileName("android-arm64-v8a-libmpv_stt_plugin_rs.so"))
    assertTrue(isSafeCPluginFileName("plugin.SO"))
    assertTrue(isSafeCPluginFileName("a.b.so"))
  }

  @Test
  fun `anything that is not a shared object is refused`() {
    assertFalse(isSafeCPluginFileName("plugin.lua"))
    assertFalse(isSafeCPluginFileName("plugin.so.txt"))
    assertFalse(isSafeCPluginFileName(".so"))
    assertFalse(isSafeCPluginFileName(""))
    assertFalse(isSafeCPluginFileName("   "))
  }

  @Test
  fun `a name that could escape the scripts directory is refused`() {
    assertFalse(isSafeCPluginFileName("../plugin.so"))
    assertFalse(isSafeCPluginFileName("dir/plugin.so"))
    assertFalse(isSafeCPluginFileName("dir\\plugin.so"))
  }

  @Test
  fun `the client name drops the extension and folds the characters mpv rejects`() {
    assertEquals("android_arm64_v8a_libmpv_stt_plugin_rs", mpvScriptClientName("android-arm64-v8a-libmpv_stt_plugin_rs.so"))
    assertEquals("libmpv_stt_plugin_rs", mpvScriptClientName("libmpv_stt_plugin_rs.so"))
    assertEquals("plugin", mpvScriptClientName("plugin.lua"))
    // Only the last dot starts the extension, and the remaining dot is folded
    // to `_` like every other character mpv does not accept.
    assertEquals("a_b", mpvScriptClientName("a.b.so"))
  }

  @Test
  fun `a path is reduced to its basename before the extension is dropped`() {
    assertEquals("libmpv_stt_plugin_rs", mpvScriptClientName("/data/user/0/app/files/scripts/libmpv_stt_plugin_rs.so"))
  }
}
