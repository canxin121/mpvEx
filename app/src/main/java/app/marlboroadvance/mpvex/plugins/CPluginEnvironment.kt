package app.marlboroadvance.mpvex.plugins

import android.system.Os

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
  val failedPluginFileNames: Set<String>,
  val warnings: List<String>,
)

interface CPluginEnvironmentAccess {
  fun unset(name: String)

  fun set(
    name: String,
    value: String,
  )
}

private object AndroidCPluginEnvironmentAccess : CPluginEnvironmentAccess {
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
  ): CPluginEnvironmentPlan {
    val descriptorsByFileName = descriptors.associateBy { it.fileName }
    val excludedPlugins = mutableSetOf<String>()
    val warnings = mutableListOf<String>()
    val candidateAssignments = mutableListOf<CPluginEnvironmentAssignment>()

    val selectedDescriptors = selectedPluginFileNames.mapNotNull { descriptorsByFileName[it] }
    selectedPluginFileNames
      .filterNot(descriptorsByFileName::containsKey)
      .forEach { warnings += "Selected C plugin '$it' was not found" }

    selectedDescriptors
      .filter { it.manifest != null }
      .groupBy { it.manifest?.id }
      .filterValues { it.size > 1 }
      .forEach { (pluginId, duplicates) ->
        val files = duplicates.map { it.fileName }.sorted()
        excludedPlugins += files
        warnings += "Plugin id '$pluginId' is declared by multiple selected plugins: ${files.joinToString()}"
      }

    selectedDescriptors.sortedBy { it.fileName.lowercase() }.forEach { descriptor ->
      if (descriptor.manifestError != null) {
        excludedPlugins += descriptor.fileName
        warnings += "Skipping '${descriptor.fileName}': ${descriptor.manifestError}"
        return@forEach
      }

      val manifest = descriptor.manifest ?: return@forEach
      if (descriptor.fileName in excludedPlugins) return@forEach

      val pluginValues = configuredValues[manifest.id].orEmpty()
      val pluginAssignments = mutableListOf<CPluginEnvironmentAssignment>()
      val pluginErrors = mutableListOf<String>()

      manifest.config.forEach { field ->
        val rawValue = pluginValues[field.id] ?: field.defaultValueAsString()
        val validation = CPluginValueValidator.validate(field, rawValue)
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
        warnings += "Skipping '${descriptor.fileName}': ${pluginErrors.joinToString()}"
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
        warnings += "Environment variable '$environmentName' is declared by multiple plugins: ${owners.joinToString()}"
      }

    return CPluginEnvironmentPlan(
      assignments = candidateAssignments.filterNot { it.pluginFileName in excludedPlugins },
      excludedPluginFileNames = excludedPlugins,
      warnings = warnings,
    )
  }
}

object CPluginEnvironmentApplicator {
  fun apply(
    plan: CPluginEnvironmentPlan,
    previouslyManagedEnvironmentNames: Set<String>,
    environment: CPluginEnvironmentAccess = AndroidCPluginEnvironmentAccess,
  ): CPluginEnvironmentApplyResult {
    val warnings = mutableListOf<String>()
    val failedToClear = mutableSetOf<String>()
    val namesToClear = previouslyManagedEnvironmentNames + plan.assignments.map { it.name }

    namesToClear.forEach { name ->
      runCatching { environment.unset(name) }
        .onFailure {
          failedToClear += name
          warnings += "Could not clear plugin environment variable '$name'"
        }
    }

    val failedPlugins = mutableSetOf<String>()
    plan.assignments.forEach { assignment ->
      runCatching { environment.set(assignment.name, assignment.value) }
        .onFailure {
          failedPlugins += assignment.pluginFileName
          warnings += "Could not set environment variable '${assignment.name}' for '${assignment.pluginFileName}'"
        }
    }

    if (failedPlugins.isNotEmpty()) {
      plan.assignments
        .filter { it.pluginFileName in failedPlugins }
        .forEach { assignment ->
          runCatching { environment.unset(assignment.name) }
            .onFailure {
              failedToClear += assignment.name
              warnings += "Could not roll back environment variable '${assignment.name}'"
            }
        }
    }

    val successfullyManaged =
      plan.assignments
        .filterNot { it.pluginFileName in failedPlugins }
        .mapTo(mutableSetOf()) { it.name }

    return CPluginEnvironmentApplyResult(
      managedEnvironmentNames = successfullyManaged + failedToClear,
      failedPluginFileNames = failedPlugins,
      warnings = warnings,
    )
  }
}
