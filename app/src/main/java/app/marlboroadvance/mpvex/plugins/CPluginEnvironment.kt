package app.marlboroadvance.mpvex.plugins

import android.system.Os
import app.marlboroadvance.mpvex.R

data class CPluginEnvironmentAssignment(
  val name: String,
  val value: String,
  val pluginFileName: String,
)

data class CPluginEnvironmentPlan(
  val assignments: List<CPluginEnvironmentAssignment>,
  val excludedPluginFileNames: Set<String>,
  val warnings: List<String>,
)

data class CPluginEnvironmentApplyResult(
  val managedEnvironmentNames: Set<String>,
  val managedUserEnvironmentNames: Set<String>,
  val failedPluginFileNames: Set<String>,
  val warnings: List<String>,
)

interface CPluginEnvironmentAccess {
  fun get(name: String): String? = null

  fun unset(name: String)

  fun set(
    name: String,
    value: String,
  )
}

private object AndroidCPluginEnvironmentAccess : CPluginEnvironmentAccess {
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

object CPluginEnvironmentPlanBuilder {
  fun build(
    selectedPluginFileNames: Set<String>,
    descriptors: List<CPluginDescriptor>,
    configuredValues: Map<String, Map<String, String>>,
    messages: CPluginMessages = AndroidCPluginMessages,
  ): CPluginEnvironmentPlan {
    val descriptorsByFileName = descriptors.associateBy { it.fileName }
    val excludedPlugins = mutableSetOf<String>()
    val warnings = mutableListOf<String>()
    val candidateAssignments = mutableListOf<CPluginEnvironmentAssignment>()

    val selectedDescriptors = selectedPluginFileNames.mapNotNull { descriptorsByFileName[it] }
    selectedPluginFileNames
      .filterNot(descriptorsByFileName::containsKey)
      .forEach { warnings += messages.get(R.string.plugin_selected_not_found, it) }

    selectedDescriptors
      .filter { it.manifest != null }
      .groupBy { it.manifest?.id }
      .filterValues { it.size > 1 }
      .forEach { (pluginId, duplicates) ->
        val files = duplicates.map { it.fileName }.sorted()
        excludedPlugins += files
        warnings += messages.get(R.string.plugin_duplicate_selected_id, pluginId.orEmpty(), files.joinToString())
      }

    selectedDescriptors.sortedBy { it.fileName.lowercase() }.forEach { descriptor ->
      if (descriptor.manifestError != null) {
        excludedPlugins += descriptor.fileName
        warnings += messages.get(R.string.plugin_skipped_reason, descriptor.fileName, descriptor.manifestError)
        return@forEach
      }

      val manifest = descriptor.manifest ?: return@forEach
      if (descriptor.fileName in excludedPlugins) return@forEach

      val pluginValues = configuredValues[manifest.id].orEmpty()
      val pluginAssignments = mutableListOf<CPluginEnvironmentAssignment>()
      val pluginErrors = mutableListOf<String>()

      manifest.config.forEach { field ->
        val rawValue = pluginValues[field.id] ?: field.defaultValueAsString()
        val validation = CPluginValueValidator.validate(field, rawValue, messages)
        if (!validation.isValid) {
          pluginErrors += "${field.title}: ${validation.error}"
        } else {
          validation.normalizedValue?.let { value ->
            pluginAssignments +=
              CPluginEnvironmentAssignment(
                name = field.binding.name,
                value = value,
                pluginFileName = descriptor.fileName,
              )
          }
        }
      }

      if (pluginErrors.isNotEmpty()) {
        excludedPlugins += descriptor.fileName
        warnings += messages.get(R.string.plugin_skipped_reason, descriptor.fileName, pluginErrors.joinToString())
      } else {
        candidateAssignments += pluginAssignments
      }
    }

    candidateAssignments
      .groupBy { it.name }
      .filterValues { assignments -> assignments.map { it.pluginFileName }.distinct().size > 1 }
      .forEach { (environmentName, assignments) ->
        val owners = assignments.map { it.pluginFileName }.distinct().sorted()
        excludedPlugins += owners
        warnings += messages.get(R.string.plugin_environment_duplicate, environmentName, owners.joinToString())
      }

    return CPluginEnvironmentPlan(
      assignments = candidateAssignments.filterNot { it.pluginFileName in excludedPlugins },
      excludedPluginFileNames = excludedPlugins,
      warnings = warnings,
    )
  }
}

object CPluginEnvironmentApplicator {
  // Keep the value inherited by this Android process so removing a user entry
  // does not erase an existing variable such as PATH or TMPDIR.
  private val processOriginalValues = mutableMapOf<String, String?>()

  fun apply(
    plan: CPluginEnvironmentPlan,
    previouslyManagedEnvironmentNames: Set<String>,
    environment: CPluginEnvironmentAccess = AndroidCPluginEnvironmentAccess,
    messages: CPluginMessages = AndroidCPluginMessages,
    userDefinedVariables: Map<String, String> = emptyMap(),
    previouslyManagedUserEnvironmentNames: Set<String> = emptySet(),
    originalValues: MutableMap<String, String?> = processOriginalValues,
  ): CPluginEnvironmentApplyResult {
    val warnings = mutableListOf<String>()
    val failedToClear = mutableSetOf<String>()
    val pluginNames = plan.assignments.mapTo(mutableSetOf()) { it.name }
    val validUserVariables =
      userDefinedVariables.filter { (name, value) ->
        EnvironmentVariablesCodec.isCustomName(name) && EnvironmentVariablesCodec.isValidValue(value)
      }
    val namesToClear =
      (previouslyManagedEnvironmentNames + pluginNames +
        previouslyManagedUserEnvironmentNames + validUserVariables.keys)
        .filter(EnvironmentVariablesCodec::isValidName)
    val currentNames = pluginNames + validUserVariables.keys

    fun restoreOriginal(name: String) {
      runCatching {
        val original = originalValues[name]
        if (original == null) environment.unset(name) else environment.set(name, original)
      }.onFailure {
        failedToClear += name
        warnings += messages.get(R.string.environment_variable_clear_failed, name)
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
          warnings += messages.get(R.string.environment_variable_clear_failed, name)
        }
    }

    val managedUserNames = mutableSetOf<String>()
    fun setUserVariable(name: String, value: String) {
      runCatching { environment.set(name, value) }
        .onSuccess { managedUserNames += name }
        .onFailure {
          warnings += messages.get(R.string.environment_variable_set_failed, name)
          restoreOriginal(name)
        }
    }

    // A selected plugin owns its manifest bindings. A matching user-defined
    // value remains saved and takes effect again when the plugin is disabled.
    validUserVariables
      .filterKeys { it !in pluginNames }
      .forEach { (name, value) -> setUserVariable(name, value) }

    val failedPlugins = mutableSetOf<String>()
    plan.assignments.forEach { assignment ->
      runCatching { environment.set(assignment.name, assignment.value) }
        .onFailure {
          failedPlugins += assignment.pluginFileName
          warnings += messages.get(R.string.plugin_environment_set_failed, assignment.name, assignment.pluginFileName)
        }
    }

    if (failedPlugins.isNotEmpty()) {
      plan.assignments
        .filter { it.pluginFileName in failedPlugins }
        .forEach { assignment ->
          val userValue = validUserVariables[assignment.name]
          if (userValue == null) {
            restoreOriginal(assignment.name)
          } else {
            runCatching { environment.unset(assignment.name) }
              .onSuccess { setUserVariable(assignment.name, userValue) }
              .onFailure {
                failedToClear += assignment.name
                warnings += messages.get(R.string.plugin_environment_rollback_failed, assignment.name)
              }
          }
        }
    }

    val successfullyManaged =
      plan.assignments
        .filterNot { it.pluginFileName in failedPlugins }
        .mapTo(mutableSetOf()) { it.name }

    return CPluginEnvironmentApplyResult(
      managedEnvironmentNames =
        successfullyManaged + failedToClear.filter { it in previouslyManagedEnvironmentNames || it in pluginNames },
      managedUserEnvironmentNames =
        managedUserNames + failedToClear.filter {
          it in previouslyManagedUserEnvironmentNames || it in validUserVariables
        },
      failedPluginFileNames = failedPlugins,
      warnings = warnings,
    )
  }
}
