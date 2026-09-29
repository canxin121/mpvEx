package app.marlboroadvance.mpvex.plugins

enum class EnvironmentVariableExpansionErrorReason {
  UNDEFINED,
  CYCLE,
  MALFORMED,
}

data class EnvironmentVariableExpansionError(
  /** The user-defined variable whose value failed to resolve. */
  val name: String,
  /** The reference as written, for example `${MPVEX_CONFIG_DIR}/fonts`. */
  val reference: String,
  val reason: EnvironmentVariableExpansionErrorReason,
) {
  /** Value-free description for logs; it never contains a variable value. */
  val description: String
    get() =
      when (reason) {
        EnvironmentVariableExpansionErrorReason.UNDEFINED ->
          "Environment variable \"$name\" references undefined variable $reference"
        EnvironmentVariableExpansionErrorReason.CYCLE ->
          "Environment variable \"$name\" has a reference cycle at $reference"
        EnvironmentVariableExpansionErrorReason.MALFORMED ->
          "Environment variable \"$name\" contains an invalid reference $reference"
      }
}

data class EnvironmentVariableExpansionResult(
  /** Fully resolved values. A value that failed to resolve is absent. */
  val values: Map<String, String>,
  val errors: List<EnvironmentVariableExpansionError>,
)

/**
 * Resolves `${NAME}` references inside user-defined environment values.
 *
 * Supported syntax: `${NAME}`, `${NAME:-fallback}` and `$$` for a literal dollar
 * sign. A dollar sign followed by anything else stays literal, so a value such as
 * `https://host/$path` is untouched. References are looked up in the user's own
 * values first and then in [builtIns]; the process environment is never read,
 * because built-in values are not written yet when the application start applies
 * user variables.
 *
 * A value that cannot be resolved is left out of
 * [EnvironmentVariableExpansionResult.values], so no unresolved `${...}` text ever
 * reaches libmpv. Write `$${NAME}` for a literal `${NAME}`.
 */
object EnvironmentVariableExpander {
  fun expand(
    values: Map<String, String>,
    builtIns: Map<String, String> = emptyMap(),
  ): EnvironmentVariableExpansionResult {
    val resolver = Resolver(values, builtIns)
    val resolved = mutableMapOf<String, String>()
    val errors = mutableListOf<EnvironmentVariableExpansionError>()
    // Sorted so the result and the reported errors do not depend on map order.
    values.keys.sorted().forEach { name ->
      try {
        resolved[name] = resolver.resolveValue(name)
      } catch (failure: ExpansionFailure) {
        errors += EnvironmentVariableExpansionError(name, failure.reference, failure.reason)
      }
    }
    return EnvironmentVariableExpansionResult(resolved, errors)
  }

  private class ExpansionFailure(
    val reference: String,
    val reason: EnvironmentVariableExpansionErrorReason,
  ) : Exception()

  private class Resolver(
    private val values: Map<String, String>,
    private val builtIns: Map<String, String>,
  ) {
    private val resolved = mutableMapOf<String, String>()
    private val failures = mutableMapOf<String, ExpansionFailure>()
    private val visiting = mutableSetOf<String>()

    /** Expands the value of a user-defined variable. */
    fun resolveValue(name: String): String {
      resolved[name]?.let { return it }
      failures[name]?.let { throw it }
      if (name in visiting) throw cycle(name)
      visiting += name
      try {
        return expand(values.getValue(name)).also { resolved[name] = it }
      } catch (failure: ExpansionFailure) {
        failures[name] = failure
        throw failure
      } finally {
        visiting -= name
      }
    }

    private fun resolveReference(name: String): String? {
      resolved[name]?.let { return it }
      failures[name]?.let { throw it }
      if (name !in values) return builtIns[name]
      return resolveValue(name)
    }

    private fun expand(text: String): String {
      val output = StringBuilder(text.length)
      var index = 0
      while (index < text.length) {
        val character = text[index]
        if (character != '$') {
          output.append(character)
          index++
          continue
        }
        when (text.getOrNull(index + 1)) {
          '$' -> {
            output.append('$')
            index += 2
          }
          '{' -> index = expandReference(text, index, output)
          else -> {
            output.append('$')
            index++
          }
        }
      }
      return output.toString()
    }

    /** Appends one `${...}` replacement and returns the index after the reference. */
    private fun expandReference(
      text: String,
      start: Int,
      output: StringBuilder,
    ): Int {
      val contentStart = start + 2
      val close = findClosingBrace(text, contentStart)
      if (close < 0) throw failure(text.substring(start), EnvironmentVariableExpansionErrorReason.MALFORMED)
      val raw = text.substring(start, close + 1)
      val (name, fallback) = splitFallback(text.substring(contentStart, close))
      if (!EnvironmentVariablesCodec.isValidName(name)) {
        throw failure(raw, EnvironmentVariableExpansionErrorReason.MALFORMED)
      }
      val value =
        try {
          resolveReference(name)
        } catch (nested: ExpansionFailure) {
          // Report the reference as written here; the reason stays the root cause.
          throw failure(raw, nested.reason)
        }
      when {
        value != null && (value.isNotEmpty() || fallback == null) -> output.append(value)
        fallback != null -> output.append(expand(fallback))
        else -> throw failure(raw, EnvironmentVariableExpansionErrorReason.UNDEFINED)
      }
      return close + 1
    }

    private fun findClosingBrace(
      text: String,
      contentStart: Int,
    ): Int {
      var depth = 1
      var index = contentStart
      while (index < text.length) {
        when {
          text[index] == '$' && text.getOrNull(index + 1) == '{' -> {
            depth++
            index += 2
          }
          text[index] == '}' -> {
            depth--
            if (depth == 0) return index
            index++
          }
          else -> index++
        }
      }
      return -1
    }

    /** Splits `NAME:-fallback` at the first `:-` outside nested references. */
    private fun splitFallback(content: String): Pair<String, String?> {
      var depth = 0
      var index = 0
      while (index < content.length) {
        when {
          content[index] == '$' && content.getOrNull(index + 1) == '{' -> {
            depth++
            index += 2
          }
          content[index] == '}' -> {
            depth--
            index++
          }
          depth == 0 && content[index] == ':' && content.getOrNull(index + 1) == '-' ->
            return content.substring(0, index) to content.substring(index + 2)
          else -> index++
        }
      }
      return content to null
    }

    private fun cycle(name: String): ExpansionFailure =
      failure(reference(name), EnvironmentVariableExpansionErrorReason.CYCLE)

    private fun failure(
      reference: String,
      reason: EnvironmentVariableExpansionErrorReason,
    ): ExpansionFailure = ExpansionFailure(reference, reason)

    private fun reference(name: String): String = "$" + "{" + name + "}"
  }
}
