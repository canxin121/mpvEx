package app.marlboroadvance.mpvex.ui.preferences

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import app.marlboroadvance.mpvex.R
import app.marlboroadvance.mpvex.environment.MpvExEnvironment
import app.marlboroadvance.mpvex.preferences.AdvancedPreferences
import app.marlboroadvance.mpvex.preferences.preference.collectAsState
import app.marlboroadvance.mpvex.plugins.EnvironmentVariableExpander
import app.marlboroadvance.mpvex.plugins.EnvironmentVariableExpansionError
import app.marlboroadvance.mpvex.plugins.EnvironmentVariableExpansionErrorReason
import app.marlboroadvance.mpvex.plugins.EnvironmentVariablesCodec
import app.marlboroadvance.mpvex.presentation.Screen
import app.marlboroadvance.mpvex.ui.utils.LocalBackStack
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject

@Serializable
object EnvironmentVariablesScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val backStack = LocalBackStack.current
    val preferences = koinInject<AdvancedPreferences>()
    val encodedVariables by preferences.environmentVariables.collectAsState()
    val configTreeUri by preferences.mpvConfStorageUri.collectAsState()
    val variables = remember(encodedVariables) { EnvironmentVariablesCodec.decode(encodedVariables) }
    val builtIns = remember(configTreeUri) { MpvExEnvironment.builtInValues(configTreeUri) }
    // Errors only, so the list keeps hiding every value.
    val referenceErrors =
      remember(variables, builtIns) {
        EnvironmentVariableExpander
          .expand(variables, builtIns)
          .errors
          .groupBy(EnvironmentVariableExpansionError::name)
      }
    val builtInVariables = listOf(
      Triple(
        MpvExEnvironment.CONFIG_DIR,
        R.string.environment_variable_config_dir,
        builtIns[MpvExEnvironment.CONFIG_DIR],
      ),
    )
    // An empty string means a new entry; valid environment names are never empty.
    var editingName by remember { mutableStateOf<String?>(null) }

    Scaffold(
      topBar = {
        TopAppBar(
          title = { Text(stringResource(R.string.environment_variables_title)) },
          navigationIcon = {
            IconButton(onClick = backStack::removeLastOrNull) {
              Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null)
            }
          },
        )
      },
    ) { padding ->
      LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
        item {
          Text(
            text = stringResource(R.string.environment_variables_description),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
          )
        }
        item {
          Text(
            text = stringResource(R.string.environment_variables_built_in_header),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
          )
        }
        items(builtInVariables) { (name, labelRes, value) ->
          ListItem(
            overlineContent = { Text(stringResource(labelRes)) },
            headlineContent = { Text(name) },
            supportingContent = {
              SelectionContainer {
                Text(value ?: stringResource(R.string.environment_variable_config_unavailable))
              }
            },
          )
        }
        item {
          Text(
            text = stringResource(R.string.environment_variables_custom_header),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
          )
        }
        if (variables.isEmpty()) {
          item {
            Text(
              text = stringResource(R.string.environment_variables_empty),
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
          }
        }
        items(variables.keys.sorted()) { name ->
          ListItem(
            headlineContent = { Text(name) },
            supportingContent = {
              Column {
                Text(stringResource(R.string.environment_variable_value_hidden))
                referenceErrors[name].orEmpty().forEach { error ->
                  Text(
                    text = stringResource(error.reason.messageResource(), error.reference),
                    color = MaterialTheme.colorScheme.error,
                  )
                }
              }
            },
            modifier = Modifier.clickable { editingName = name },
          )
        }
        item {
          TextButton(
            onClick = { editingName = "" },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
          ) {
            Text(stringResource(R.string.environment_variable_add))
          }
        }
        item {
          Text(
            text = stringResource(R.string.environment_variables_backup_notice),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
          )
        }
      }
    }

    editingName?.let { originalName ->
      EnvironmentVariableEditorDialog(
        originalName = originalName.takeIf(String::isNotEmpty),
        initialValue = variables[originalName].orEmpty(),
        existingNames = variables.keys,
        otherVariables = variables.filterKeys { it != originalName },
        builtIns = builtIns,
        onSave = { name, value ->
          val current = EnvironmentVariablesCodec.decode(preferences.environmentVariables.get())
          val updated = current.toMutableMap()
          if (originalName.isNotEmpty()) updated.remove(originalName)
          updated[name] = value
          preferences.environmentVariables.set(EnvironmentVariablesCodec.encode(updated))
          editingName = null
        },
        onDelete = {
          val updated = EnvironmentVariablesCodec.decode(preferences.environmentVariables.get()) - originalName
          preferences.environmentVariables.set(EnvironmentVariablesCodec.encode(updated))
          editingName = null
        },
        onDismiss = { editingName = null },
      )
    }
  }
}

private fun EnvironmentVariableExpansionErrorReason.messageResource(): Int =
  when (this) {
    EnvironmentVariableExpansionErrorReason.UNDEFINED -> R.string.environment_variable_reference_undefined
    EnvironmentVariableExpansionErrorReason.CYCLE -> R.string.environment_variable_reference_cycle
    EnvironmentVariableExpansionErrorReason.MALFORMED -> R.string.environment_variable_reference_malformed
  }

@Composable
private fun EnvironmentVariableEditorDialog(
  originalName: String?,
  initialValue: String,
  existingNames: Set<String>,
  otherVariables: Map<String, String>,
  builtIns: Map<String, String>,
  onSave: (String, String) -> Unit,
  onDelete: () -> Unit,
  onDismiss: () -> Unit,
) {
  var name by remember(originalName) { mutableStateOf(originalName.orEmpty()) }
  var value by remember(originalName, initialValue) { mutableStateOf(initialValue) }
  var valueVisible by remember(originalName) { mutableStateOf(false) }
  val normalizedName = name.trim()
  val nameError = when {
    normalizedName.isEmpty() -> R.string.environment_variable_name_required
    !EnvironmentVariablesCodec.isValidName(normalizedName) -> R.string.environment_variable_name_invalid
    normalizedName in MpvExEnvironment.reservedNames -> R.string.environment_variable_name_reserved
    normalizedName != originalName && normalizedName in existingNames -> R.string.environment_variable_name_duplicate
    else -> null
  }
  val valueError = if (EnvironmentVariablesCodec.isValidValue(value)) null
  else R.string.environment_variable_value_invalid
  // Preview only: saving keeps the original text. The entry itself is excluded
  // from the resolution set because it is not saved yet.
  val resolvedValue =
    remember(normalizedName, value, otherVariables, builtIns) {
      EnvironmentVariableExpander
        .expand(otherVariables + (normalizedName to value), builtIns)
        .values[normalizedName]
    }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = {
      Text(stringResource(if (originalName == null) R.string.environment_variable_add else R.string.environment_variable_edit))
    },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
          value = name,
          onValueChange = { name = it },
          label = { Text(stringResource(R.string.environment_variable_name)) },
          supportingText = nameError?.let { error -> { Text(stringResource(error)) } },
          isError = nameError != null && name.isNotEmpty(),
          singleLine = true,
          modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
          value = value,
          onValueChange = { value = it },
          label = { Text(stringResource(R.string.environment_variable_value)) },
          supportingText = valueError?.let { error -> { Text(stringResource(error)) } },
          isError = valueError != null,
          visualTransformation = if (valueVisible) VisualTransformation.None else PasswordVisualTransformation(),
          trailingIcon = {
            TextButton(onClick = { valueVisible = !valueVisible }) {
              Text(stringResource(if (valueVisible) R.string.environment_variable_hide_value else R.string.environment_variable_show_value))
            }
          },
          minLines = 1,
          maxLines = 4,
          modifier = Modifier.fillMaxWidth(),
        )
        Text(
          text = stringResource(R.string.environment_variable_resolved_value),
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val resolvedText =
          when {
            resolvedValue == null -> stringResource(R.string.environment_variable_reference_unresolved)
            // The same toggle as the raw value: a resolved value can embed a secret.
            !valueVisible -> "•".repeat(minOf(resolvedValue.length, 64))
            else -> resolvedValue
          }
        Text(
          text = resolvedText,
          style = MaterialTheme.typography.bodyMedium,
          color = if (resolvedValue == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    },
    confirmButton = {
      TextButton(
        enabled = nameError == null && valueError == null,
        onClick = { onSave(normalizedName, value) },
      ) { Text(stringResource(R.string.ui_save)) }
    },
    dismissButton = {
      Row {
        if (originalName != null) {
          TextButton(onClick = onDelete) {
            Text(stringResource(R.string.environment_variable_delete))
          }
        }
        TextButton(onClick = onDismiss) {
          Text(stringResource(R.string.generic_cancel))
        }
      }
    },
  )
}
