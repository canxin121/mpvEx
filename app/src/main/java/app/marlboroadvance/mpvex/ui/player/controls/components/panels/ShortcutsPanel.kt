package app.marlboroadvance.mpvex.ui.player.controls.components.panels

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.marlboroadvance.mpvex.R
import app.marlboroadvance.mpvex.preferences.ShortcutPreferences
import app.marlboroadvance.mpvex.preferences.ShortcutSlot
import app.marlboroadvance.mpvex.preferences.preference.collectAsState
import app.marlboroadvance.mpvex.ui.player.pressShortcutKey
import org.koin.compose.koinInject

@Composable
fun ShortcutsPanel(onDismissRequest: () -> Unit) {
  DraggablePanel(
    header = {
      Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(stringResource(R.string.shortcut_settings_title), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onDismissRequest) {
          Icon(Icons.Default.Close, contentDescription = stringResource(R.string.generic_cancel))
        }
      }
    },
  ) {
    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
      ShortcutActionButtons()
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShortcutActionButtons() {
  val preferences = koinInject<ShortcutPreferences>()
  val configured = ShortcutSlot.entries.filter { preferences.key(it).get().isNotBlank() }

  if (configured.isEmpty()) {
    Text(
      stringResource(R.string.shortcut_sheet_empty),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    return
  }

  FlowRow(
    modifier = Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    configured.forEach { slot ->
      val label by preferences.label(slot).collectAsState()
      val key by preferences.key(slot).collectAsState()
      OutlinedButton(
        onClick = { pressShortcutKey(key) },
        modifier = Modifier.widthIn(max = 260.dp),
      ) {
        Icon(Icons.Outlined.Keyboard, contentDescription = null)
        Text(
          label.ifBlank { "Shortcut ${slot.number}" },
          modifier = Modifier.padding(start = 8.dp),
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
  }
}
