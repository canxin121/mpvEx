package app.marlboroadvance.mpvex.ui.preferences

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Keyboard
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
import androidx.compose.ui.unit.dp
import app.marlboroadvance.mpvex.R
import app.marlboroadvance.mpvex.preferences.ShortcutPreferences
import app.marlboroadvance.mpvex.preferences.ShortcutSlot
import app.marlboroadvance.mpvex.preferences.preference.collectAsState
import app.marlboroadvance.mpvex.presentation.Screen
import app.marlboroadvance.mpvex.ui.utils.LocalBackStack
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject

@Serializable
object ShortcutPreferencesScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val backstack = LocalBackStack.current
    val preferences = koinInject<ShortcutPreferences>()

    Scaffold(
      topBar = {
        TopAppBar(
          title = { Text(stringResource(R.string.shortcut_settings_title)) },
          navigationIcon = {
            IconButton(onClick = backstack::removeLastOrNull) {
              Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null)
            }
          },
        )
      },
    ) { padding ->
      LazyColumn(
        modifier = Modifier.fillMaxSize().padding(padding),
      ) {
        item {
          Text(
            text = stringResource(R.string.shortcut_settings_summary),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(16.dp),
          )
        }
        items(ShortcutSlot.entries.size) { index ->
          val slot = ShortcutSlot.entries[index]
          ShortcutSlotItem(slot, preferences)
        }
        item {
          TextButton(
            onClick = { backstack.add(ConfigEditorScreen(ConfigEditorScreen.ConfigType.INPUT_CONF)) },
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
          ) {
            Icon(Icons.Outlined.Keyboard, contentDescription = null)
            Text(stringResource(R.string.pref_advanced_input_conf), modifier = Modifier.padding(start = 8.dp))
          }
        }
      }
    }
  }
}

@Composable
private fun ShortcutSlotItem(slot: ShortcutSlot, preferences: ShortcutPreferences) {
  val labelPreference = preferences.label(slot)
  val keyPreference = preferences.key(slot)
  val label by labelPreference.collectAsState()
  val key by keyPreference.collectAsState()
  var editing by remember { mutableStateOf(false) }

  ListItem(
    headlineContent = { Text(label.ifBlank { "Shortcut ${slot.number}" }) },
    supportingContent = {
      Text(
        if (key.isBlank()) stringResource(R.string.shortcut_not_configured)
        else stringResource(R.string.shortcut_key_summary, key),
      )
    },
    leadingContent = { Icon(Icons.Outlined.Keyboard, contentDescription = null) },
    modifier = Modifier.clickable { editing = true },
  )

  if (editing) {
    var draftLabel by remember(label, editing) { mutableStateOf(label) }
    var draftKey by remember(key, editing) { mutableStateOf(key) }
    val validKey = draftKey.trim().none(Char::isWhitespace)

    AlertDialog(
      onDismissRequest = { editing = false },
      title = { Text(stringResource(R.string.shortcut_edit_title, slot.number)) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          OutlinedTextField(
            value = draftLabel,
            onValueChange = { draftLabel = it },
            label = { Text(stringResource(R.string.shortcut_label)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
          )
          OutlinedTextField(
            value = draftKey,
            onValueChange = { draftKey = it },
            label = { Text(stringResource(R.string.shortcut_key)) },
            supportingText = { Text(stringResource(R.string.shortcut_key_example)) },
            isError = !validKey,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
          )
        }
      },
      confirmButton = {
        TextButton(
          enabled = validKey,
          onClick = {
            labelPreference.set(draftLabel.trim().ifBlank { "Shortcut ${slot.number}" })
            keyPreference.set(draftKey.trim())
            editing = false
          },
        ) { Text(stringResource(R.string.generic_ok)) }
      },
      dismissButton = {
        TextButton(onClick = { editing = false }) {
          Text(stringResource(R.string.generic_cancel))
        }
      },
    )
  }
}
