package app.marlboroadvance.mpvex.environment

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MpvExEnvironmentTest {
  @Test
  fun `selected primary storage tree becomes the same editable directory shown in settings`() {
    val treeUri = "content://com.android.externalstorage.documents/tree/primary%3AmpvEx%2FMy%20Configs"

    assertEquals(
      "/storage/emulated/0/mpvEx/My Configs",
      MpvExEnvironment.selectedConfigLocation(treeUri, "/storage/emulated/0"),
    )
    assertEquals(setOf(MpvExEnvironment.CONFIG_DIR), MpvExEnvironment.reservedNames)
  }

  @Test
  fun `other providers keep their URI rather than inventing a filesystem path`() {
    val sdCard = "content://com.android.externalstorage.documents/tree/1234-ABCD%3AmpvEx"
    val cloud = "content://example.documents/tree/folder%3A42"

    assertEquals(sdCard, MpvExEnvironment.selectedConfigLocation(sdCard, "/storage/emulated/0"))
    assertEquals(cloud, MpvExEnvironment.selectedConfigLocation(cloud, "/storage/emulated/0"))
  }

  @Test
  fun `clearing the selected folder unsets the built in variable`() {
    val environment = RecordingEnvironment()
    val value = MpvExEnvironment.selectedConfigLocation(
      "content://com.android.externalstorage.documents/tree/primary%3AmpvEx",
      "/storage/emulated/0",
    )

    MpvExEnvironment.applyValue(value, environment)
    assertEquals("/storage/emulated/0/mpvEx", environment.values[MpvExEnvironment.CONFIG_DIR])

    MpvExEnvironment.applyValue(
      MpvExEnvironment.selectedConfigLocation("", "/storage/emulated/0"),
      environment,
    )
    assertNull(environment.values[MpvExEnvironment.CONFIG_DIR])
    assertNull(MpvExEnvironment.selectedConfigLocation("file:///data/user/0/config", "/storage/emulated/0"))
  }

  @Test
  fun `built in values expose the selected folder and nothing else`() {
    val treeUri = "content://com.android.externalstorage.documents/tree/primary%3AmpvEx"

    assertEquals(
      mapOf(MpvExEnvironment.CONFIG_DIR to "/storage/emulated/0/mpvEx"),
      MpvExEnvironment.builtInValues(treeUri, "/storage/emulated/0"),
    )
    assertTrue(MpvExEnvironment.builtInValues("", "/storage/emulated/0").isEmpty())
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
