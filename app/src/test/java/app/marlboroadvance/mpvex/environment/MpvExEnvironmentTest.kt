package app.marlboroadvance.mpvex.environment

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MpvExEnvironmentTest {
  @Test
  fun `directory values use the actual Android paths passed to the player`() {
    val values = MpvExEnvironment.staticValues(
      appDir = "/data/user/0/app.marlboroadvance.mpvex",
      configDir = "/data/user/0/app.marlboroadvance.mpvex/files",
      cacheDir = "/data/user/0/app.marlboroadvance.mpvex/cache",
    )

    assertEquals(
      mapOf(
        MpvExEnvironment.APP_DIR to "/data/user/0/app.marlboroadvance.mpvex",
        MpvExEnvironment.CONFIG_DIR to "/data/user/0/app.marlboroadvance.mpvex/files",
        MpvExEnvironment.CACHE_DIR to "/data/user/0/app.marlboroadvance.mpvex/cache",
      ),
      values,
    )
    assertEquals(values.keys + MpvExEnvironment.MEDIA_PATH, MpvExEnvironment.reservedNames)
  }

  @Test
  fun `reapplying built ins clears an absent media path and preserves a loaded one`() {
    val environment = RecordingEnvironment()
    val directories = MpvExEnvironment.staticValues("/app", "/config", "/cache")

    MpvExEnvironment.applyValues(directories, null, environment)
    assertEquals(directories, environment.values)

    MpvExEnvironment.applyValues(directories, "https://example.org/video.mp4", environment)
    assertEquals("https://example.org/video.mp4", environment.values[MpvExEnvironment.MEDIA_PATH])

    MpvExEnvironment.applyValues(directories, "", environment)
    assertNull(environment.values[MpvExEnvironment.MEDIA_PATH])
    assertEquals(directories, environment.values)
  }

  @Test
  fun `invalid media paths cannot enter the process environment`() {
    assertNull(MpvExEnvironment.normalizeMediaPath(null))
    assertNull(MpvExEnvironment.normalizeMediaPath(" "))
    assertNull(MpvExEnvironment.normalizeMediaPath("bad\u0000path"))
    assertEquals("fd://42", MpvExEnvironment.normalizeMediaPath("fd://42"))
  }

  private class RecordingEnvironment : MpvExEnvironmentAccess {
    val values = mutableMapOf<String, String>()

    override fun set(name: String, value: String) {
      values[name] = value
    }

    override fun unset(name: String) {
      values.remove(name)
    }
  }
}
