package app.marlboroadvance.mpvex.environment

import android.system.Os
import app.marlboroadvance.mpvex.plugins.EnvironmentVariableExpander
import app.marlboroadvance.mpvex.plugins.EnvironmentVariableExpansionError
import app.marlboroadvance.mpvex.plugins.EnvironmentVariablesCodec
import app.marlboroadvance.mpvex.utils.logging.MpvExLog

private const val TAG = "Environment"

data class UserEnvironmentApplyResult(
  val managedUserEnvironmentNames: Set<String>,
  val warnings: List<String>,
  /** User variables whose `${...}` references could not be resolved. */
  val userVariableErrors: List<EnvironmentVariableExpansionError> = emptyList(),
)

interface ProcessEnvironmentAccess {
  fun get(name: String): String? = null

  fun unset(name: String)

  fun set(
    name: String,
    value: String,
  )
}

private object AndroidProcessEnvironmentAccess : ProcessEnvironmentAccess {
  override fun get(name: String): String? = Os.getenv(name)

  override fun unset(name: String) {
    Os.unsetenv(name)
  }

  override fun set(
    name: String,
    value: String,
  ) {
    Os.setenv(name, value, true)
  }
}

/**
 * Writes the user's own environment variables into the process, so mpv, Lua
 * scripts, and C plugins see them through `getenv()`.
 *
 * The variables are applied at application start and again before libmpv
 * initializes. `${NAME}` references inside a value are resolved first; a value
 * that cannot be resolved is not exported at all, and the settings page shows
 * the reason. Names managed by a previous application of this function are
 * cleared, so removing a variable takes effect and an unchanged variable is not
 * left holding a stale value.
 */
object UserEnvironmentVariables {
  // Keep the value inherited by this Android process so removing a user entry
  // does not erase an existing variable such as PATH or TMPDIR.
  private val processOriginalValues = mutableMapOf<String, String?>()

  fun apply(
    userDefinedVariables: Map<String, String>,
    previouslyManagedUserEnvironmentNames: Set<String>,
    builtInVariables: Map<String, String> = emptyMap(),
    environment: ProcessEnvironmentAccess = AndroidProcessEnvironmentAccess,
    originalValues: MutableMap<String, String?> = processOriginalValues,
  ): UserEnvironmentApplyResult {
    val warnings = mutableListOf<String>()
    val failedToClear = mutableSetOf<String>()
    val expansion =
      EnvironmentVariableExpander.expand(
        userDefinedVariables.filter { (name, value) ->
          EnvironmentVariablesCodec.isCustomName(name) && EnvironmentVariablesCodec.isValidValue(value)
        },
        builtInVariables,
      )
    val resolvedVariables = expansion.values
    val namesToClear =
      (previouslyManagedUserEnvironmentNames + resolvedVariables.keys)
        .filter(EnvironmentVariablesCodec::isValidName)
    val currentNames = resolvedVariables.keys

    fun restoreOriginal(name: String) {
      runCatching {
        val original = originalValues[name]
        if (original == null) environment.unset(name) else environment.set(name, original)
      }.onFailure {
        failedToClear += name
        warnings += "Could not clear environment variable \"$name\""
      }
    }

    namesToClear.forEach { name ->
      if (name !in originalValues) {
        originalValues[name] = runCatching { environment.get(name) }.getOrNull()
      }
      if (name !in currentNames) {
        restoreOriginal(name)
        return@forEach
      }
      runCatching { environment.unset(name) }
        .onFailure {
          failedToClear += name
          warnings += "Could not clear environment variable \"$name\""
        }
    }

    val managedUserNames = mutableSetOf<String>()
    resolvedVariables.forEach { (name, value) ->
      runCatching { environment.set(name, value) }
        .onSuccess { managedUserNames += name }
        .onFailure {
          warnings += "Could not set environment variable \"$name\""
          restoreOriginal(name)
        }
    }

    return UserEnvironmentApplyResult(
      managedUserEnvironmentNames =
        managedUserNames + failedToClear.filter {
          it in previouslyManagedUserEnvironmentNames || it in resolvedVariables
        },
      warnings = warnings,
      userVariableErrors = expansion.errors,
    ).also { logOutcome(it, userDefinedVariables, builtInVariables) }
  }

  /**
   * Records what was injected and what went wrong. Only variable *names* are
   * logged — a value may be a credential, and an unresolved `${...}` reference
   * is reported by name for the same reason.
   */
  private fun logOutcome(
    result: UserEnvironmentApplyResult,
    userDefinedVariables: Map<String, String>,
    builtInVariables: Map<String, String>,
  ) {
    val names = result.managedUserEnvironmentNames.sorted()
    MpvExLog.d(
      TAG,
      "Applied ${names.size} environment variable(s) from " +
        "${userDefinedVariables.size} user-defined value(s) and ${builtInVariables.size} built-in value(s)",
    )
    if (names.isNotEmpty()) MpvExLog.v(TAG, "Environment variables: ${names.joinToString()}")
    result.warnings.forEach { MpvExLog.w(TAG, it) }
    result.userVariableErrors.forEach { error -> MpvExLog.w(TAG, error.description) }
  }
}
