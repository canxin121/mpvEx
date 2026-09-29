package app.marlboroadvance.mpvex.plugins

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CPluginManifestTest {
  @Test
  fun `parses and validates a version one manifest`() {
    val manifest = CPluginManifestParser.parse(VALID_MANIFEST, "example.so")

    assertEquals("org.example.plugin", manifest.id)
    assertEquals(3, manifest.config.size)
    assertEquals("https://example.com", manifest.config[0].defaultValueAsString())
    assertEquals("10", manifest.config[1].defaultValueAsString())
    assertEquals("normal", manifest.config[2].defaultValueAsString())
  }

  @Test
  fun `rejects a manifest whose entry does not match the plugin`() {
    val error = assertThrows(IllegalArgumentException::class.java) {
      CPluginManifestParser.parse(
        VALID_MANIFEST.replace("example.so", "other.so"),
        "example.so",
      )
    }

    assertTrue(error.message.orEmpty().contains("does not match"))
  }

  @Test
  fun `rejects a default whose JSON type does not match the field`() {
    val error = assertThrows(IllegalArgumentException::class.java) {
      CPluginManifestParser.parse(
        VALID_MANIFEST.replace(
          "\"default\": \"https://example.com\"",
          "\"default\": 42",
        ),
        "example.so",
      )
    }

    assertTrue(error.message.orEmpty().contains("invalid default"))
  }

  @Test
  fun `uses stored values and defaults when building an environment plan`() {
    val manifest = CPluginManifestParser.parse(VALID_MANIFEST, "example.so")
    val descriptor = CPluginDescriptor("example.so", manifest, "example.so.mpvex.json")

    val plan =
      CPluginEnvironmentPlanBuilder.build(
        selectedPluginFileNames = setOf("example.so"),
        descriptors = listOf(descriptor),
        configuredValues =
          mapOf(
            manifest.id to mapOf("serverUrl" to "https://custom.example", "timeout" to "25"),
          ),
      )

    assertTrue(plan.excludedPluginFileNames.isEmpty())
    assertEquals(
      mapOf(
        "MPVEX_EXAMPLE_SERVER_URL" to "https://custom.example",
        "MPVEX_EXAMPLE_TIMEOUT" to "25",
        "MPVEX_EXAMPLE_MODE" to "normal",
      ),
      plan.assignments.associate { it.name to it.value },
    )
  }

  @Test
  fun `excludes a plugin when a required field is missing`() {
    val content =
      VALID_MANIFEST.replace(
        "\"default\": \"https://example.com\",",
        "",
      )
    val manifest = CPluginManifestParser.parse(content, "example.so")

    val plan =
      CPluginEnvironmentPlanBuilder.build(
        selectedPluginFileNames = setOf("example.so"),
        descriptors = listOf(CPluginDescriptor("example.so", manifest, "example.so.mpvex.json")),
        configuredValues = emptyMap(),
      )

    assertEquals(setOf("example.so"), plan.excludedPluginFileNames)
    assertTrue(plan.assignments.isEmpty())
    assertTrue(plan.warnings.single().contains("required"))
  }

  @Test
  fun `excludes all plugins that claim the same environment variable`() {
    val first = CPluginManifestParser.parse(VALID_MANIFEST, "example.so")
    val secondContent =
      VALID_MANIFEST
        .replace("org.example.plugin", "org.example.second")
        .replace("example.so", "second.so")
        .replace("MPVEX_EXAMPLE_TIMEOUT", "MPVEX_SECOND_TIMEOUT")
        .replace("MPVEX_EXAMPLE_MODE", "MPVEX_SECOND_MODE")
    val second = CPluginManifestParser.parse(secondContent, "second.so")

    val plan =
      CPluginEnvironmentPlanBuilder.build(
        selectedPluginFileNames = setOf("example.so", "second.so"),
        descriptors =
          listOf(
            CPluginDescriptor("example.so", first, "example.so.mpvex.json"),
            CPluginDescriptor("second.so", second, "second.so.mpvex.json"),
          ),
        configuredValues = emptyMap(),
      )

    assertEquals(setOf("example.so", "second.so"), plan.excludedPluginFileNames)
    assertTrue(plan.assignments.isEmpty())
    assertTrue(plan.warnings.any { it.contains("MPVEX_EXAMPLE_SERVER_URL") })
  }

  @Test
  fun `validates ranges and optional empty values`() {
    val manifest = CPluginManifestParser.parse(VALID_MANIFEST, "example.so")
    val timeout = manifest.config.first { it.id == "timeout" }
    val optionalField = timeout.copy(required = false, defaultValue = null)

    assertEquals("Value must be at most 60", CPluginValueValidator.validate(timeout, "61").error)
    assertNull(CPluginValueValidator.validate(optionalField, "").normalizedValue)
    assertTrue(CPluginValueValidator.validate(optionalField, "").isValid)
  }

  @Test
  fun `configuration codec round trips plugin values`() {
    val values =
      mapOf(
        "org.example.plugin" to mapOf("serverUrl" to "https://example.com", "timeout" to "12"),
      )

    assertEquals(values, CPluginConfigurationCodec.decode(CPluginConfigurationCodec.encode(values)))
  }

  @Test
  fun `updating one plugin configuration preserves every other plugin`() {
    val original =
      CPluginConfigurationCodec.encode(
        mapOf(
          "org.example.first" to mapOf("mode" to "old"),
          "org.example.second" to mapOf("enabled" to "true"),
        ),
      )

    val updated =
      CPluginConfigurationCodec.decode(
        CPluginConfigurationCodec.updatePlugin(
          content = original,
          pluginId = "org.example.first",
          pluginValues = mapOf("mode" to "new"),
        ),
      )

    assertEquals(mapOf("mode" to "new"), updated["org.example.first"])
    assertEquals(mapOf("enabled" to "true"), updated["org.example.second"])
  }

  @Test
  fun `rejects plugin filenames that can escape the scripts directory`() {
    assertTrue(!isSafeCPluginFileName("../example.so"))
    assertTrue(!isSafeCPluginFileName("/example.so"))
    assertTrue(!isSafeCPluginFileName("folder\\example.so"))
    assertTrue(isSafeCPluginFileName("example.so"))
  }

  @Test
  fun `environment application clears stale names before setting current values`() {
    val environment = FakeEnvironment()
    val plan =
      CPluginEnvironmentPlan(
        assignments =
          listOf(
            CPluginEnvironmentAssignment("MPVEX_CURRENT", "value", "example.so"),
          ),
        excludedPluginFileNames = emptySet(),
        warnings = emptyList(),
      )

    val result =
      CPluginEnvironmentApplicator.apply(
        plan = plan,
        previouslyManagedEnvironmentNames = setOf("MPVEX_OLD"),
        environment = environment,
      )

    assertEquals(
      listOf("unset:MPVEX_OLD", "unset:MPVEX_CURRENT", "set:MPVEX_CURRENT=value"),
      environment.operations,
    )
    assertEquals(setOf("MPVEX_CURRENT"), result.managedEnvironmentNames)
    assertTrue(result.failedPluginFileNames.isEmpty())
  }

  @Test
  fun `environment application rolls back every value owned by a failed plugin`() {
    val environment = FakeEnvironment(failingSetName = "MPVEX_SECOND")
    val plan =
      CPluginEnvironmentPlan(
        assignments =
          listOf(
            CPluginEnvironmentAssignment("MPVEX_FIRST", "one", "example.so"),
            CPluginEnvironmentAssignment("MPVEX_SECOND", "two", "example.so"),
          ),
        excludedPluginFileNames = emptySet(),
        warnings = emptyList(),
      )

    val result =
      CPluginEnvironmentApplicator.apply(
        plan = plan,
        previouslyManagedEnvironmentNames = emptySet(),
        environment = environment,
      )

    assertEquals(setOf("example.so"), result.failedPluginFileNames)
    assertTrue(result.managedEnvironmentNames.isEmpty())
    assertTrue(environment.operations.takeLast(2).containsAll(listOf("unset:MPVEX_FIRST", "unset:MPVEX_SECOND")))
  }

  private class FakeEnvironment(
    private val failingSetName: String? = null,
  ) : CPluginEnvironmentAccess {
    val operations = mutableListOf<String>()

    override fun unset(name: String) {
      operations += "unset:$name"
    }

    override fun set(
      name: String,
      value: String,
    ) {
      operations += "set:$name=$value"
      if (name == failingSetName) error("set failed")
    }
  }

  companion object {
    private val VALID_MANIFEST =
      """
      {
        "schemaVersion": 1,
        "id": "org.example.plugin",
        "name": "Example Plugin",
        "version": "1.0.0",
        "entry": "example.so",
        "config": [
          {
            "id": "serverUrl",
            "title": "Server URL",
            "type": "string",
            "required": true,
            "default": "https://example.com",
            "binding": {
              "type": "environment",
              "name": "MPVEX_EXAMPLE_SERVER_URL"
            }
          },
          {
            "id": "timeout",
            "title": "Timeout",
            "type": "integer",
            "default": 10,
            "minimum": 1,
            "maximum": 60,
            "binding": {
              "type": "environment",
              "name": "MPVEX_EXAMPLE_TIMEOUT"
            }
          },
          {
            "id": "mode",
            "title": "Mode",
            "type": "enum",
            "default": "normal",
            "options": [
              { "value": "normal", "label": "Normal" },
              { "value": "compact", "label": "Compact" }
            ],
            "binding": {
              "type": "environment",
              "name": "MPVEX_EXAMPLE_MODE"
            }
          }
        ]
      }
      """.trimIndent()
  }
}
