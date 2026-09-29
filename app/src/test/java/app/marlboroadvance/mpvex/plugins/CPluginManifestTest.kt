package app.marlboroadvance.mpvex.plugins

import app.marlboroadvance.mpvex.R
import app.marlboroadvance.mpvex.environment.MpvExEnvironment
import kotlinx.serialization.encodeToString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class CPluginManifestTest {
  @Test
  fun `parses and validates a version one manifest`() {
    val manifest = parseForTest(VALID_MANIFEST, "example.so")

    assertEquals("org.example.plugin", manifest.id)
    assertEquals(3, manifest.config.size)
    assertEquals("https://example.com", manifest.config[0].defaultValueAsString())
    assertEquals("10", manifest.config[1].defaultValueAsString())
    assertEquals("normal", manifest.config[2].defaultValueAsString())
  }

  @Test
  fun `rejects a manifest whose entry does not match the plugin`() {
    val error = assertThrows(IllegalArgumentException::class.java) {
      parseForTest(
        VALID_MANIFEST.replace("example.so", "other.so"),
        "example.so",
      )
    }

    assertTrue(error.message.orEmpty().contains("does not match"))
  }

  @Test
  fun `rejects a default whose JSON type does not match the field`() {
    val error = assertThrows(IllegalArgumentException::class.java) {
      parseForTest(
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
  fun `rejects every built in environment name in plugin manifests`() {
    MpvExEnvironment.reservedNames.forEach { name ->
      val error = assertThrows(IllegalArgumentException::class.java) {
        parseForTest(VALID_MANIFEST.replace("MPVEX_EXAMPLE_SERVER_URL", name), "example.so")
      }
      assertTrue(error.message.orEmpty().contains(name))
    }
  }

  @Test
  fun `uses stored values and defaults when building an environment plan`() {
    val manifest = parseForTest(VALID_MANIFEST, "example.so")
    val descriptor = CPluginDescriptor("example.so", manifest, "example.so.mpvex.json")

    val plan =
      buildForTest(
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
    val manifest = parseForTest(content, "example.so")

    val plan =
      buildForTest(
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
    val first = parseForTest(VALID_MANIFEST, "example.so")
    val secondContent =
      VALID_MANIFEST
        .replace("org.example.plugin", "org.example.second")
        .replace("example.so", "second.so")
        .replace("MPVEX_EXAMPLE_TIMEOUT", "MPVEX_SECOND_TIMEOUT")
        .replace("MPVEX_EXAMPLE_MODE", "MPVEX_SECOND_MODE")
    val second = parseForTest(secondContent, "second.so")

    val plan =
      buildForTest(
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
    val manifest = parseForTest(VALID_MANIFEST, "example.so")
    val timeout = manifest.config.first { it.id == "timeout" }
    val optionalField = timeout.copy(required = false, defaultValue = null)

    assertEquals("Value must be at most 60", validateForTest(timeout, "61").error)
    assertNull(validateForTest(optionalField, "").normalizedValue)
    assertTrue(validateForTest(optionalField, "").isValid)
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
      applyForTest(
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
      applyForTest(
        plan = plan,
        previouslyManagedEnvironmentNames = emptySet(),
        environment = environment,
      )

    assertEquals(setOf("example.so"), result.failedPluginFileNames)
    assertTrue(result.managedEnvironmentNames.isEmpty())
    assertTrue(environment.operations.takeLast(2).containsAll(listOf("unset:MPVEX_FIRST", "unset:MPVEX_SECOND")))
  }

  @Test
  fun `user variables are applied before plugins and plugin bindings take priority`() {
    val environment = FakeEnvironment()
    val result = CPluginEnvironmentApplicator.apply(
      plan = CPluginEnvironmentPlan(
        assignments = listOf(CPluginEnvironmentAssignment("SHARED", "plugin", "example.so")),
        excludedPluginFileNames = emptySet(),
        warnings = emptyList(),
      ),
      previouslyManagedEnvironmentNames = setOf("STALE_PLUGIN"),
      environment = environment,
      messages = testMessages,
      userDefinedVariables = mapOf("SHARED" to "user", "USER_ONLY" to "value"),
      previouslyManagedUserEnvironmentNames = setOf("STALE_USER"),
    )

    assertEquals(
      listOf(
        "unset:STALE_PLUGIN", "unset:SHARED", "unset:STALE_USER", "unset:USER_ONLY",
        "set:USER_ONLY=value", "set:SHARED=plugin",
      ),
      environment.operations,
    )
    assertEquals(setOf("SHARED"), result.managedEnvironmentNames)
    assertEquals(setOf("USER_ONLY"), result.managedUserEnvironmentNames)
  }

  @Test
  fun `user value returns when a plugin using the same name is disabled`() {
    val environment = FakeEnvironment()
    val result = CPluginEnvironmentApplicator.apply(
      plan = CPluginEnvironmentPlan(emptyList(), emptySet(), emptyList()),
      previouslyManagedEnvironmentNames = setOf("SHARED"),
      environment = environment,
      messages = testMessages,
      userDefinedVariables = mapOf("SHARED" to "user"),
    )

    assertEquals(listOf("unset:SHARED", "set:SHARED=user"), environment.operations)
    assertTrue(result.managedEnvironmentNames.isEmpty())
    assertEquals(setOf("SHARED"), result.managedUserEnvironmentNames)
  }

  @Test
  fun `failed plugin setup restores a user value for the same name`() {
    val environment = FakeEnvironment(failingSetValue = "plugin")
    val result = CPluginEnvironmentApplicator.apply(
      plan = CPluginEnvironmentPlan(
        assignments = listOf(CPluginEnvironmentAssignment("SHARED", "plugin", "example.so")),
        excludedPluginFileNames = emptySet(),
        warnings = emptyList(),
      ),
      previouslyManagedEnvironmentNames = emptySet(),
      environment = environment,
      messages = testMessages,
      userDefinedVariables = mapOf("SHARED" to "user"),
    )

    assertEquals(setOf("example.so"), result.failedPluginFileNames)
    assertEquals(setOf("SHARED"), result.managedUserEnvironmentNames)
    assertTrue(result.managedEnvironmentNames.isEmpty())
    assertEquals(listOf("unset:SHARED", "set:SHARED=plugin", "unset:SHARED", "set:SHARED=user"), environment.operations)
  }

  @Test
  fun `user environment codec preserves values and ignores invalid imported names`() {
    val values = mapOf("MPVEX_TOKEN" to "quote \" and ü", "EMPTY" to "")
    assertEquals(values, EnvironmentVariablesCodec.decode(EnvironmentVariablesCodec.encode(values)))
    val imported = kotlinx.serialization.json.Json.encodeToString(
      mapOf("VALID" to "value", "BAD-NAME" to "ignored", "NUL" to "\u0000"),
    )
    assertEquals(mapOf("VALID" to "value"), EnvironmentVariablesCodec.decode(imported))
    assertTrue(EnvironmentVariablesCodec.decode("not JSON").isEmpty())
  }

  @Test
  fun `built in names cannot be saved or applied as custom variables`() {
    val imported = kotlinx.serialization.json.Json.encodeToString(
      mapOf(MpvExEnvironment.CONFIG_DIR to "/wrong", "CUSTOM" to "value"),
    )
    assertEquals(mapOf("CUSTOM" to "value"), EnvironmentVariablesCodec.decode(imported))
    assertThrows(IllegalArgumentException::class.java) {
      EnvironmentVariablesCodec.encode(mapOf(MpvExEnvironment.CONFIG_DIR to "/wrong"))
    }

    val environment = FakeEnvironment()
    val result = CPluginEnvironmentApplicator.apply(
      plan = CPluginEnvironmentPlan(emptyList(), emptySet(), emptyList()),
      previouslyManagedEnvironmentNames = emptySet(),
      environment = environment,
      messages = testMessages,
      userDefinedVariables = mapOf(MpvExEnvironment.CONFIG_DIR to "/wrong", "CUSTOM" to "value"),
      originalValues = mutableMapOf(),
    )
    assertEquals(mapOf("CUSTOM" to "value"), environment.values)
    assertEquals(setOf("CUSTOM"), result.managedUserEnvironmentNames)
  }

  @Test
  fun `removing a user override restores the inherited process value`() {
    val environment = FakeEnvironment(initialValues = mutableMapOf("PATH" to "system-path"))
    val originalValues = mutableMapOf<String, String?>()
    val first = CPluginEnvironmentApplicator.apply(
      plan = CPluginEnvironmentPlan(emptyList(), emptySet(), emptyList()),
      previouslyManagedEnvironmentNames = emptySet(),
      environment = environment,
      messages = testMessages,
      userDefinedVariables = mapOf("PATH" to "custom-path"),
      originalValues = originalValues,
    )
    val second = CPluginEnvironmentApplicator.apply(
      plan = CPluginEnvironmentPlan(emptyList(), emptySet(), emptyList()),
      previouslyManagedEnvironmentNames = emptySet(),
      environment = environment,
      messages = testMessages,
      previouslyManagedUserEnvironmentNames = first.managedUserEnvironmentNames,
      originalValues = originalValues,
    )

    assertEquals("system-path", environment.values["PATH"])
    assertTrue(second.managedUserEnvironmentNames.isEmpty())
    assertEquals(listOf("unset:PATH", "set:PATH=custom-path", "set:PATH=system-path"), environment.operations)
  }

  private val testMessages = object : CPluginMessages {
    override fun get(id: Int, vararg args: Any?): String = when (id) {
      R.string.plugin_entry_mismatch -> "Manifest entry ${args[0]} does not match plugin file ${args[1]}"
      R.string.plugin_field_invalid_default -> "Field ${args[0]} has an invalid default value"
      R.string.plugin_value_required -> "A value is required"
      R.string.plugin_value_maximum -> "Value must be at most ${args[0]}"
      else -> "$id: ${args.joinToString()}"
    }
  }

  private fun parseForTest(content: String, fileName: String): CPluginManifest =
    CPluginManifestParser.parse(content, fileName, testMessages)

  private fun buildForTest(
    selectedPluginFileNames: Set<String>,
    descriptors: List<CPluginDescriptor>,
    configuredValues: Map<String, Map<String, String>>,
  ): CPluginEnvironmentPlan =
    CPluginEnvironmentPlanBuilder.build(selectedPluginFileNames, descriptors, configuredValues, testMessages)

  private fun validateForTest(field: CPluginConfigField, value: String?): CPluginValueValidation =
    CPluginValueValidator.validate(field, value, testMessages)

  private fun applyForTest(
    plan: CPluginEnvironmentPlan,
    previouslyManagedEnvironmentNames: Set<String>,
    environment: CPluginEnvironmentAccess,
  ): CPluginEnvironmentApplyResult =
    CPluginEnvironmentApplicator.apply(plan, previouslyManagedEnvironmentNames, environment, testMessages)

  private class FakeEnvironment(
    private val failingSetName: String? = null,
    private val failingSetValue: String? = null,
    initialValues: MutableMap<String, String> = mutableMapOf(),
  ) : CPluginEnvironmentAccess {
    val operations = mutableListOf<String>()
    val values = initialValues

    override fun get(name: String): String? = values[name]

    override fun unset(name: String) {
      operations += "unset:$name"
      values.remove(name)
    }

    override fun set(
      name: String,
      value: String,
    ) {
      operations += "set:$name=$value"
      if (name == failingSetName || value == failingSetValue) error("set failed")
      values[name] = value
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
