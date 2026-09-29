package app.marlboroadvance.mpvex.ui.browser.cards

import app.marlboroadvance.mpvex.R
import app.marlboroadvance.mpvex.i18n.localizedString
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.marlboroadvance.mpvex.domain.network.NetworkConnection

@Composable
fun NetworkConnectionCard(
  connection: NetworkConnection,
  onConnect: (NetworkConnection) -> Unit,
  onDisconnect: (NetworkConnection) -> Unit,
  onEdit: (NetworkConnection) -> Unit,
  onDelete: (NetworkConnection) -> Unit,
  onBrowse: (NetworkConnection) -> Unit,
  onAutoConnectChange: (NetworkConnection, Boolean) -> Unit,
  modifier: Modifier = Modifier,
  isConnected: Boolean = false,
  isConnecting: Boolean = false,
  error: String? = null,
) {
  Card(
    modifier = modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(
      containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ),
  ) {
    Column(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
    ) {
      // Header with name and actions
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = connection.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
          )
          Text(
            text = "${connection.protocol.displayName} • ${connection.host}:${connection.port}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        Row {
          IconButton(onClick = { onEdit(connection) }) {
            Icon(
              Icons.Filled.Edit,
              contentDescription = localizedString(R.string.ui_edit),
              tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          IconButton(onClick = { onDelete(connection) }) {
            Icon(
              Icons.Filled.Delete,
              contentDescription = localizedString(R.string.delete),
              tint = MaterialTheme.colorScheme.error,
            )
          }
        }
      }

      // Connection details
      if (connection.path != "/") {
        Text(
          text = localizedString(R.string.ui_path_1_s, connection.path),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(top = 4.dp),
        )
      }

      if (connection.username.isNotEmpty() && !connection.isAnonymous) {
        Text(
          text = localizedString(R.string.ui_user_1_s, connection.username),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(top = 4.dp),
        )
      }

      // Error message
      if (error != null) {
        Text(
          text = localizedString(R.string.ui_error_1_s, error),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error,
          modifier = Modifier.padding(top = 8.dp),
        )
      }

      // Auto-connect checkbox
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Checkbox(
          checked = connection.autoConnect,
          onCheckedChange = { checked ->
            onAutoConnectChange(connection, checked)
          },
        )
        Text(
          text = localizedString(R.string.ui_connect_automatically_on_app_launch),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      // Connection button
      Row(
        modifier = Modifier
          .fillMaxWidth()
          .padding(top = 12.dp),
        horizontalArrangement = Arrangement.End,
      ) {
        when {
          isConnecting -> {
            FilledTonalButton(
              onClick = { },
              enabled = false,
            ) {
              Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
              ) {
                CircularProgressIndicator(
                  modifier = Modifier.size(16.dp),
                  strokeWidth = 2.dp,
                  color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                )
                Text(
                  localizedString(R.string.ui_connecting),
                  color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
                )
              }
            }
          }

          isConnected -> {
            Row(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              FilledTonalButton(
                onClick = { onBrowse(connection) },
                colors = ButtonDefaults.filledTonalButtonColors(
                  containerColor = MaterialTheme.colorScheme.primaryContainer,
                  contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
              ) {
                Icon(
                  Icons.Filled.FolderOpen,
                  contentDescription = null,
                  modifier = Modifier.padding(end = 8.dp),
                )
                Text(localizedString(R.string.browse))
              }

              FilledTonalButton(
                onClick = { onDisconnect(connection) },
                colors = ButtonDefaults.filledTonalButtonColors(
                  containerColor = MaterialTheme.colorScheme.secondaryContainer,
                  contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ),
              ) {
                Icon(
                  Icons.Filled.LinkOff,
                  contentDescription = null,
                  modifier = Modifier.padding(end = 8.dp),
                )
                Text(localizedString(R.string.disconnect))
              }
            }
          }

          else -> {
            FilledTonalButton(
              onClick = { onConnect(connection) },
              colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
              ),
            ) {
              Icon(
                Icons.Filled.Link,
                contentDescription = null,
                modifier = Modifier.padding(end = 8.dp),
              )
              Text(localizedString(R.string.connect))
            }
          }
        }
      }
    }
  }
}
