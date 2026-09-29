package app.marlboroadvance.mpvex.ui.preferences

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import app.marlboroadvance.mpvex.plugins.CPluginConfigField
import app.marlboroadvance.mpvex.plugins.CPluginDescriptor
import app.marlboroadvance.mpvex.plugins.CPluginEnvironmentPlanBuilder
import app.marlboroadvance.mpvex.plugins.CPluginFieldType
import app.marlboroadvance.mpvex.plugins.CPluginManifest
import app.marlboroadvance.mpvex.plugins.CPluginValueValidator
import app.marlboroadvance.mpvex.plugins.defaultValueAsString

@Composable
fun CPluginSelectionDialog(
  availablePlugins: List<CPluginDescriptor>,
  selectedPlugins: Set<String>,
  configuration: Map<String, Map<String, String>>,
  onPluginsSelected: (Set<String>, Map<String, Map<String, String>>) -> Unit,
  onDismiss: () -> Unit,
) {
  var pendingSelection by remember(selectedPlugins, availablePlugins) {
    mutableStateOf(selectedPlugins.intersect(availablePlugins.mapTo(mutableSetOf()) { it.fileName }))
  }
  var pendingConfiguration by remember(configuration, availablePlugins) {
    mutableStateOf(configuration)
  }
  var pluginBeingConfigured by remember { mutableStateOf<CPluginDescriptor?>(null) }
  val pendingPlan =
    CPluginEnvironmentPlanBuilder.build(
      selectedPluginFileNames = pendingSelection,
      descriptors = availablePlugins,
      configuredValues = pendingConfiguration,
    )

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Manage C Plugins") },
    text = {
      Column(
        modifier =
          Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
      ) {
        if (availablePlugins.isEmpty()) {
          Text("No C plugins found in the configured MPV directory.")
        } else {
          Text(
            text = "Select plugins and configure fields declared by their .mpvex.json manifests.",
            color = MaterialTheme.colorScheme.outline,
          )
          pendingPlan.warnings.forEach { warning ->
            Text(
              text = warning,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.error,
            )
          }
          availablePlugins.forEach { descriptor ->
            val selected = descriptor.fileName in pendingSelection
            Column(modifier = Modifier.fillMaxWidth()) {
              Row(
                modifier =
                  Modifier
                    .fillMaxWidth()
                    .clickable {
                      pendingSelection =
                        if (selected) {
                          pendingSelection - descriptor.fileName
                        } else {
                          pendingSelection + descriptor.fileName
                        }
                    },
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Checkbox(
                  checked = selected,
                  onCheckedChange = { checked ->
                    pendingSelection =
                      if (checked) {
                        pendingSelection + descriptor.fileName
                      } else {
                        pendingSelection - descriptor.fileName
                      }
                  },
                )
                Column(modifier = Modifier.weight(1f)) {
                  Text(
                    text = descriptor.manifest?.name ?: descriptor.fileName,
                    fontWeight = FontWeight.Medium,
                  )
                  val details = buildList {
                    descriptor.manifest?.version?.takeIf(String::isNotBlank)?.let { add("v$it") }
                    if (descriptor.manifest != null) add(descriptor.fileName)
                  }.joinToString(" • ")
                  if (details.isNotBlank()) {
                    Text(
                      text = details,
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.outline,
                    )
                  }
                }
              }

              if (selected) {
                when {
                  descriptor.manifestError != null ->
                    Text(
                      text = "Invalid manifest: ${descriptor.manifestError}",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.error,
                      modifier = Modifier.padding(start = 48.dp, end = 8.dp),
                    )
                  descriptor.manifest == null ->
                    Text(
                      text = "No configuration manifest. The plugin will load in legacy mode.",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.outline,
                      modifier = Modifier.padding(start = 48.dp, end = 8.dp),
                    )
                  descriptor.manifest.config.isEmpty() ->
                    Text(
                      text = "This plugin declares no configurable fields.",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.outline,
                      modifier = Modifier.padding(start = 48.dp, end = 8.dp),
                    )
                  else ->
                    TextButton(
                      onClick = { pluginBeingConfigured = descriptor },
                      modifier = Modifier.padding(start = 36.dp),
                    ) {
                      val count = descriptor.manifest.config.size
                      Text("Configure ($count field${if (count == 1) "" else "s"})")
                    }
                }
              }
            }
          }
        }
      }
    },
    confirmButton = {
      TextButton(
        onClick = { onPluginsSelected(pendingSelection, pendingConfiguration) },
        enabled = pendingPlan.excludedPluginFileNames.isEmpty(),
      ) {
        Text("Save")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancel")
      }
    },
  )

  pluginBeingConfigured?.manifest?.let { manifest ->
    CPluginConfigurationDialog(
      manifest = manifest,
      configuredValues = pendingConfiguration[manifest.id].orEmpty(),
      onSave = { values ->
        pendingConfiguration = pendingConfiguration + (manifest.id to values)
        pluginBeingConfigured = null
      },
      onDismiss = { pluginBeingConfigured = null },
    )
  }
}

@Composable
private fun CPluginConfigurationDialog(
  manifest: CPluginManifest,
  configuredValues: Map<String, String>,
  onSave: (Map<String, String>) -> Unit,
  onDismiss: () -> Unit,
) {
  var values by remember(manifest, configuredValues) {
    mutableStateOf(createCPluginEditorValues(manifest, configuredValues))
  }
  val hasErrors = hasCPluginConfigurationErrors(manifest, values)

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(manifest.name) },
    text = {
      Column(
        modifier =
          Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
      ) {
        Text(
          text = "Changes take effect the next time the player is opened.",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.outline,
        )
        CPluginConfigurationFields(
          manifest = manifest,
          values = values,
          onValueChange = { fieldId, value -> values = values + (fieldId to value) },
        )
      }
    },
    confirmButton = {
      TextButton(
        onClick = {
          onSave(normalizeCPluginConfiguration(manifest, values))
        },
        enabled = !hasErrors,
      ) {
        Text("Save")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text("Cancel")
      }
    },
  )
}

@Composable
internal fun CPluginConfigurationFields(
  manifest: CPluginManifest,
  values: Map<String, String>,
  onValueChange: (String, String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val validation = manifest.config.associateWith { field ->
    CPluginValueValidator.validate(field, values[field.id])
  }
  Column(
    modifier = modifier,
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    manifest.config.forEach { field ->
      CPluginConfigurationField(
        field = field,
        value = values[field.id].orEmpty(),
        error = validation[field]?.error,
        onValueChange = { value -> onValueChange(field.id, value) },
      )
    }
  }
}

internal fun createCPluginEditorValues(
  manifest: CPluginManifest,
  configuredValues: Map<String, String>,
): Map<String, String> =
  manifest.config.associate { field ->
    field.id to (configuredValues[field.id] ?: field.defaultValueAsString().orEmpty())
  }

internal fun hasCPluginConfigurationErrors(
  manifest: CPluginManifest,
  values: Map<String, String>,
): Boolean =
  manifest.config.any { field ->
    !CPluginValueValidator.validate(field, values[field.id]).isValid
  }

internal fun normalizeCPluginConfiguration(
  manifest: CPluginManifest,
  values: Map<String, String>,
): Map<String, String> =
  manifest.config.mapNotNull { field ->
    CPluginValueValidator.validate(field, values[field.id]).normalizedValue?.let { value ->
      field.id to value
    }
  }.toMap()

@Composable
private fun CPluginConfigurationField(
  field: CPluginConfigField,
  value: String,
  error: String?,
  onValueChange: (String) -> Unit,
) {
  Column(
    modifier = Modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    when (field.type) {
      CPluginFieldType.BOOLEAN -> BooleanConfigurationField(field, value, onValueChange)
      CPluginFieldType.ENUM -> EnumConfigurationField(field, value, onValueChange)
      else -> TextConfigurationField(field, value, error, onValueChange)
    }

    field.description?.takeIf(String::isNotBlank)?.let { description ->
      Text(
        text = description,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.outline,
      )
    }
    Text(
      text = "Environment: ${field.binding.name}",
      style = MaterialTheme.typography.labelSmall,
      color = MaterialTheme.colorScheme.outline,
    )
    if (error != null && field.type in setOf(CPluginFieldType.BOOLEAN, CPluginFieldType.ENUM)) {
      Text(
        text = error,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
      )
    }
    TextButton(
      onClick = { onValueChange(field.defaultValueAsString().orEmpty()) },
      modifier = Modifier.align(Alignment.End),
    ) {
      Text(if (field.defaultValueAsString() == null) "Use plugin default" else "Reset to default")
    }
  }
}

@Composable
private fun BooleanConfigurationField(
  field: CPluginConfigField,
  value: String,
  onValueChange: (String) -> Unit,
) {
  val state =
    when (value) {
      "true" -> ToggleableState.On
      "false" -> ToggleableState.Off
      else -> ToggleableState.Indeterminate
    }
  Row(
    modifier =
      Modifier
        .fillMaxWidth()
        .clickable {
          onValueChange(
            when (state) {
              ToggleableState.Indeterminate -> "true"
              ToggleableState.On -> "false"
              ToggleableState.Off -> ""
            },
          )
        },
    verticalAlignment = Alignment.CenterVertically,
  ) {
    TriStateCheckbox(
      state = state,
      onClick = {
        onValueChange(
          when (state) {
            ToggleableState.Indeterminate -> "true"
            ToggleableState.On -> "false"
            ToggleableState.Off -> ""
          },
        )
      },
    )
    Column {
      Text(field.displayTitle())
      if (state == ToggleableState.Indeterminate) {
        Text(
          text = "Plugin default",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.outline,
        )
      }
    }
  }
}

@Composable
private fun EnumConfigurationField(
  field: CPluginConfigField,
  value: String,
  onValueChange: (String) -> Unit,
) {
  Text(field.displayTitle(), fontWeight = FontWeight.Medium)
  field.options.forEach { option ->
    Row(
      modifier =
        Modifier
          .fillMaxWidth()
          .clickable { onValueChange(option.value) },
      verticalAlignment = Alignment.CenterVertically,
    ) {
      RadioButton(
        selected = value == option.value,
        onClick = { onValueChange(option.value) },
      )
      Text(option.label)
    }
  }
}

@Composable
private fun TextConfigurationField(
  field: CPluginConfigField,
  value: String,
  error: String?,
  onValueChange: (String) -> Unit,
) {
  val keyboardType =
    when (field.type) {
      CPluginFieldType.INTEGER -> KeyboardType.Number
      CPluginFieldType.NUMBER -> KeyboardType.Decimal
      else -> KeyboardType.Text
    }
  OutlinedTextField(
    value = value,
    onValueChange = onValueChange,
    label = { Text(field.displayTitle()) },
    singleLine = field.type != CPluginFieldType.STRING,
    isError = error != null,
    supportingText =
      if (error != null) {
        { Text(error) }
      } else {
        null
      },
    keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
    visualTransformation =
      if (field.sensitive) PasswordVisualTransformation() else VisualTransformation.None,
    modifier = Modifier.fillMaxWidth(),
  )
}

private fun CPluginConfigField.displayTitle(): String =
  if (required) "$title *" else title
