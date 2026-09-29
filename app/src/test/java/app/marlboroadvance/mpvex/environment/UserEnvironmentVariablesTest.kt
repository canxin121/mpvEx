package app.marlboroadvance.mpvex.environment

import app.marlboroadvance.mpvex.plugins.EnvironmentVariableExpansionErrorReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UserEnvironmentVariablesTest {
  private val builtIns = mapOf(MpvExEnvironment.CONFIG_DIR to "/storage/emulated/0/mpvEx")

  @Test
  fun `applies custom variables and clears the names managed last time`() {
    val environment = FakeEnvironment(mapOf("KEPT" to "inherited"))
    val originalValues = mutableMapOf<String, String?>()

    val first =
      UserEnvironmentVariables.apply(
        userDefinedVariables = mapOf("FIRST" to "one"),
        previouslyManagedUserEnvironmentNames = emptySet(),
        environment = environment,
        originalValues = originalValues,
      )
    assertEquals(setOf("FIRST"), first.managedUserEnvironmentNames)
    assertEquals("one", environment.values["FIRST"])

    UserEnvironmentVariables.apply(
      userDefinedVariables = emptyMap(),
      previouslyManagedUserEnvironmentNames = first.managedUserEnvironmentNames,
      environment = environment,
      originalValues = originalValues,
    )
    assertNull(environment.values["FIRST"])
    assertEquals("inherited", environment.values["KEPT"])
  }

  @Test
  fun `removing a custom variable restores the value inherited by the process`() {
    val environment = FakeEnvironment(mapOf("MPVEX_TEST" to "inherited"))
    val originalValues = mutableMapOf<String, String?>()

    UserEnvironmentVariables.apply(
      userDefinedVariables = mapOf("MPVEX_TEST" to "configured"),
      previouslyManagedUserEnvironmentNames = emptySet(),
      environment = environment,
      originalValues = originalValues,
    )
    assertEquals("configured", environment.values["MPVEX_TEST"])

    UserEnvironmentVariables.apply(
      userDefinedVariables = emptyMap(),
      previouslyManagedUserEnvironmentNames = setOf("MPVEX_TEST"),
      environment = environment,
      originalValues = originalValues,
    )
    assertEquals("inherited", environment.values["MPVEX_TEST"])
  }

  @Test
  fun `ignores invalid names, the built in name, and values with a NUL`() {
    val environment = FakeEnvironment()

    val result =
      UserEnvironmentVariables.apply(
        userDefinedVariables =
          mapOf(
            "1BAD" to "value",
            "HAS-DASH" to "value",
            MpvExEnvironment.CONFIG_DIR to "hijacked",
            "NUL" to "a\u0000b",
            "GOOD" to "kept",
          ),
        previouslyManagedUserEnvironmentNames = emptySet(),
        environment = environment,
        originalValues = mutableMapOf(),
      )

    assertEquals(setOf("GOOD"), result.managedUserEnvironmentNames)
    assertEquals(mapOf("GOOD" to "kept"), environment.values)
  }

  @Test
  fun `resolves references against built in and other custom values`() {
    val environment = FakeEnvironment()

    UserEnvironmentVariables.apply(
      userDefinedVariables =
        mapOf(
          "FONTS_DIR" to "\${${MpvExEnvironment.CONFIG_DIR}}/fonts",
          "SHADERS_DIR" to "\${FONTS_DIR}/shaders",
        ),
      previouslyManagedUserEnvironmentNames = emptySet(),
      builtInVariables = builtIns,
      environment = environment,
      originalValues = mutableMapOf(),
    )

    assertEquals("/storage/emulated/0/mpvEx/fonts", environment.values["FONTS_DIR"])
    assertEquals("/storage/emulated/0/mpvEx/fonts/shaders", environment.values["SHADERS_DIR"])
  }

  @Test
  fun `an unresolvable value is dropped instead of exported as literal text`() {
    val environment = FakeEnvironment()

    val result =
      UserEnvironmentVariables.apply(
        userDefinedVariables =
          mapOf(
            "GOOD" to "kept",
            "BROKEN" to "\${NOT_DEFINED}/fonts",
          ),
        previouslyManagedUserEnvironmentNames = emptySet(),
        builtInVariables = builtIns,
        environment = environment,
        originalValues = mutableMapOf(),
      )

    assertEquals(mapOf("GOOD" to "kept"), environment.values)
    assertEquals(setOf("GOOD"), result.managedUserEnvironmentNames)
    assertEquals(listOf("BROKEN"), result.userVariableErrors.map { it.name })
    assertEquals(
      EnvironmentVariableExpansionErrorReason.UNDEFINED,
      result.userVariableErrors.single().reason,
    )
  }

  @Test
  fun `a value that stops resolving is cleared rather than kept as stale text`() {
    val environment = FakeEnvironment()
    val originalValues = mutableMapOf<String, String?>()

    UserEnvironmentVariables.apply(
      userDefinedVariables = mapOf("FONTS_DIR" to "\${${MpvExEnvironment.CONFIG_DIR}}/fonts"),
      previouslyManagedUserEnvironmentNames = emptySet(),
      builtInVariables = builtIns,
      environment = environment,
      originalValues = originalValues,
    )
    assertEquals("/storage/emulated/0/mpvEx/fonts", environment.values["FONTS_DIR"])

    // The configuration folder was cleared, so the reference no longer resolves.
    UserEnvironmentVariables.apply(
      userDefinedVariables = mapOf("FONTS_DIR" to "\${${MpvExEnvironment.CONFIG_DIR}}/fonts"),
      previouslyManagedUserEnvironmentNames = setOf("FONTS_DIR"),
      builtInVariables = emptyMap(),
      environment = environment,
      originalValues = originalValues,
    )
    assertNull(environment.values["FONTS_DIR"])
  }

  @Test
  fun `a failed set restores the inherited value and keeps the name managed`() {
    // The first set fails, so the inherited value must be written back.
    val environment = FakeEnvironment(mapOf("PATH" to "/system/bin"), failSets = 1)

    val result =
      UserEnvironmentVariables.apply(
        userDefinedVariables = mapOf("PATH" to "/custom"),
        previouslyManagedUserEnvironmentNames = emptySet(),
        environment = environment,
        originalValues = mutableMapOf(),
      )

    assertEquals("/system/bin", environment.values["PATH"])
    assertTrue(result.managedUserEnvironmentNames.isEmpty())
    assertTrue(result.warnings.single().contains("PATH"))
  }

  private class FakeEnvironment(
    initial: Map<String, String> = emptyMap(),
    private var failSets: Int = 0,
  ) : ProcessEnvironmentAccess {
    val values = initial.toMutableMap()

    override fun get(name: String): String? = values[name]

    override fun unset(name: String) {
      values.remove(name)
    }

    override fun set(
      name: String,
      value: String,
    ) {
      if (failSets > 0) {
        failSets--
        throw IllegalStateException("setenv failed: $name")
      }
      values[name] = value
    }
  }
}
