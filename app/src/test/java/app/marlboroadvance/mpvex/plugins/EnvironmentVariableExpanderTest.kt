package app.marlboroadvance.mpvex.plugins

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EnvironmentVariableExpanderTest {
  private val builtIns = mapOf("MPVEX_CONFIG_DIR" to "/storage/emulated/0/mpvEx")

  @Test
  fun `resolves a built in variable and another custom variable`() {
    val result =
      EnvironmentVariableExpander.expand(
        mapOf(
          "FONTS_DIR" to "${'$'}{MPVEX_CONFIG_DIR}/fonts",
          "SHADERS_DIR" to "${'$'}{FONTS_DIR}/shaders",
        ),
        builtIns,
      )

    assertEquals(
      mapOf(
        "FONTS_DIR" to "/storage/emulated/0/mpvEx/fonts",
        "SHADERS_DIR" to "/storage/emulated/0/mpvEx/fonts/shaders",
      ),
      result.values,
    )
    assertTrue(result.errors.isEmpty())
  }

  @Test
  fun `keeps a dollar sign literal unless it starts a reference or an escape`() {
    val result =
      EnvironmentVariableExpander.expand(
        mapOf(
          "ESCAPED" to "${'$'}${'$'}ENV",
          "LITERAL_REFERENCE" to "${'$'}${'$'}{FOO}",
          "TRAILING" to "value${'$'}",
          "BARE_NAME" to "${'$'}HOME/x",
          "URL" to "https://host/${'$'}path/x",
        ),
      )

    assertEquals("${'$'}ENV", result.values["ESCAPED"])
    assertEquals("${'$'}{FOO}", result.values["LITERAL_REFERENCE"])
    assertEquals("value${'$'}", result.values["TRAILING"])
    assertEquals("${'$'}HOME/x", result.values["BARE_NAME"])
    assertEquals("https://host/${'$'}path/x", result.values["URL"])
    assertTrue(result.errors.isEmpty())
  }

  @Test
  fun `uses the fallback only when a variable is missing or empty`() {
    val result =
      EnvironmentVariableExpander.expand(
        mapOf(
          "MISSING_FALLBACK" to "${'$'}{NOT_DEFINED:-/tmp}",
          "EMPTY_FALLBACK" to "${'$'}{EMPTY:-/tmp}",
          "FALLBACK_WITH_REFERENCE" to "${'$'}{NOT_DEFINED:-${'$'}{MPVEX_CONFIG_DIR}/cache}",
          "NESTED_FALLBACK" to "${'$'}{NOT_DEFINED:-${'$'}{ALSO_NOT_DEFINED:-fallback}}",
          "UNUSED_FALLBACK" to "${'$'}{MPVEX_CONFIG_DIR:-${'$'}{NOT_DEFINED}}",
          "EMPTY" to "",
        ),
        builtIns,
      )

    assertEquals("/tmp", result.values["MISSING_FALLBACK"])
    assertEquals("/tmp", result.values["EMPTY_FALLBACK"])
    assertEquals("/storage/emulated/0/mpvEx/cache", result.values["FALLBACK_WITH_REFERENCE"])
    assertEquals("fallback", result.values["NESTED_FALLBACK"])
    assertEquals("/storage/emulated/0/mpvEx", result.values["UNUSED_FALLBACK"])
    assertEquals("", result.values["EMPTY"])
    assertTrue(result.errors.isEmpty())
  }

  @Test
  fun `a fallback may contain a colon and a hyphen`() {
    val result = EnvironmentVariableExpander.expand(mapOf("VALUE" to "${'$'}{NOT_DEFINED:-a:-b}"))

    assertEquals("a:-b", result.values["VALUE"])
  }

  @Test
  fun `a defined empty variable keeps its value without a fallback`() {
    val result = EnvironmentVariableExpander.expand(mapOf("VALUE" to "${'$'}{EMPTY}", "EMPTY" to ""))

    assertEquals("", result.values["VALUE"])
    assertTrue(result.errors.isEmpty())
  }

  @Test
  fun `drops the variable and reports an undefined reference`() {
    val result =
      EnvironmentVariableExpander.expand(
        mapOf(
          "GOOD" to "kept",
          "BROKEN" to "${'$'}{NOT_DEFINED}/fonts",
        ),
        builtIns,
      )

    assertEquals(mapOf("GOOD" to "kept"), result.values)
    assertEquals(
      listOf(EnvironmentVariableExpansionError("BROKEN", "${'$'}{NOT_DEFINED}", EnvironmentVariableExpansionErrorReason.UNDEFINED)),
      result.errors,
    )
  }

  @Test
  fun `reports the missing built in variable when no folder is selected`() {
    val result = EnvironmentVariableExpander.expand(mapOf("FONTS" to "${'$'}{MPVEX_CONFIG_DIR}/fonts"), emptyMap())

    assertTrue(result.values.isEmpty())
    assertEquals(EnvironmentVariableExpansionErrorReason.UNDEFINED, result.errors.single().reason)
  }

  @Test
  fun `detects a self reference and an indirect cycle`() {
    val selfCycle = EnvironmentVariableExpander.expand(mapOf("SELF" to "${'$'}{SELF}"))
    val indirectCycle =
      EnvironmentVariableExpander.expand(
        mapOf(
          "FIRST" to "${'$'}{SECOND}",
          "SECOND" to "${'$'}{FIRST}",
        ),
      )

    assertTrue(selfCycle.values.isEmpty())
    assertEquals(EnvironmentVariableExpansionErrorReason.CYCLE, selfCycle.errors.single().reason)
    assertEquals(EnvironmentVariableExpansionError("SELF", "${'$'}{SELF}", EnvironmentVariableExpansionErrorReason.CYCLE), selfCycle.errors.single())

    assertTrue(indirectCycle.values.isEmpty())
    // One error per failed top-level variable, in sorted order.
    assertEquals(listOf("FIRST", "SECOND"), indirectCycle.errors.map { it.name })
    assertTrue(indirectCycle.errors.all { it.reason == EnvironmentVariableExpansionErrorReason.CYCLE })
    assertEquals(listOf("${'$'}{SECOND}", "${'$'}{FIRST}"), indirectCycle.errors.map { it.reference })
  }

  @Test
  fun `a fallback does not rescue a self reference`() {
    val result = EnvironmentVariableExpander.expand(mapOf("SELF" to "${'$'}{SELF:-fallback}"))

    assertTrue(result.values.isEmpty())
    assertEquals(EnvironmentVariableExpansionErrorReason.CYCLE, result.errors.single().reason)
  }

  @Test
  fun `rejects references that are not a plain name`() {
    val malformed =
      listOf(
        "${'$'}{}",
        "${'$'}{ }",
        "${'$'}{ A }",
        "${'$'}{1A}",
        "${'$'}{A-B}",
        "${'$'}{A:+x}",
        "${'$'}{A:2}",
        "${'$'}{A",
        "prefix${'$'}{A}suffix${'$'}{B",
      )

    malformed.forEach { value ->
      val result = EnvironmentVariableExpander.expand(mapOf("VALUE" to value, "A" to "a", "B" to "b"))
      assertTrue("expected \"$value\" to be dropped", "VALUE" !in result.values)
      assertEquals(
        "expected one malformed error for \"$value\"",
        listOf(EnvironmentVariableExpansionErrorReason.MALFORMED),
        result.errors.map { it.reason },
      )
      assertEquals("VALUE", result.errors.single().name)
    }

    // The reported reference is the offending fragment, not the whole value.
    assertEquals(
      "${'$'}{B",
      EnvironmentVariableExpander
        .expand(mapOf("VALUE" to "prefix${'$'}{A}suffix${'$'}{B", "A" to "a"))
        .errors
        .single()
        .reference,
    )
  }

  @Test
  fun `a shared reference resolves the same for every dependent`() {
    val result =
      EnvironmentVariableExpander.expand(
        mapOf(
          "BASE" to "${'$'}{MPVEX_CONFIG_DIR}/base",
          "LEFT" to "${'$'}{BASE}/left",
          "RIGHT" to "${'$'}{BASE}/right",
        ),
        builtIns,
      )

    assertEquals(
      mapOf(
        "BASE" to "/storage/emulated/0/mpvEx/base",
        "LEFT" to "/storage/emulated/0/mpvEx/base/left",
        "RIGHT" to "/storage/emulated/0/mpvEx/base/right",
      ),
      result.values,
    )
    assertTrue(result.errors.isEmpty())
  }

  @Test
  fun `a fallback that is never used does not report the missing variable`() {
    val result =
      EnvironmentVariableExpander.expand(
        mapOf(
          "VALUE" to "${'$'}{DEFINED:-${'$'}{NOT_DEFINED}}",
          "DEFINED" to "kept",
        ),
      )

    assertEquals(mapOf("VALUE" to "kept", "DEFINED" to "kept"), result.values)
    assertTrue(result.errors.isEmpty())
  }
}
