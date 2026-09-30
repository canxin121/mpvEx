package app.marlboroadvance.mpvex.ui.preferences

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.marlboroadvance.mpvex.R
import app.marlboroadvance.mpvex.preferences.CustomShortcut
import app.marlboroadvance.mpvex.preferences.ShortcutPreferences
import app.marlboroadvance.mpvex.preferences.displayLabel
import app.marlboroadvance.mpvex.presentation.Screen
import app.marlboroadvance.mpvex.presentation.components.ConfirmDialog
import app.marlboroadvance.mpvex.ui.utils.LocalBackStack
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Serializable
object ShortcutPreferencesScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val backstack = LocalBackStack.current
    val preferences = koinInject<ShortcutPreferences>()
    val shortcutsState = remember(preferences) { preferences.shortcutsFlow() }
    val shortcuts by shortcutsState.collectAsState(initial = preferences.shortcuts())
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Both are hoisted out of items(): a recycled row must not be able to drop or
    // cross-wire the dialog it opened.
    var editingId by remember { mutableStateOf<String?>(null) }
    var deletingId by remember { mutableStateOf<String?>(null) }
    var showResetDialog by remember { mutableStateOf(false) }

    /** The summary row, plus the empty-state row when there is nothing to list. */
    val headerItemCount = if (shortcuts.isEmpty()) 2 else 1

    fun move(from: Int, to: Int) {
      val current = preferences.shortcuts()
      if (from !in current.indices || to !in current.indices || from == to) return
      preferences.save(current.toMutableList().apply { add(to, removeAt(from)) })
      scope.launch { listState.animateScrollToItem(headerItemCount + to) }
    }

    val reorderableState =
      rememberReorderableLazyListState(listState) { from, to ->
        // Rows key on the shortcut id, so the drag callbacks stay correct even though the
        // list sits behind a summary row and reorders while it is growing.
        val fromId = from.key as? String
        val toId = to.key as? String
        if (fromId != null && toId != null) {
          val current = preferences.shortcuts()
          val fromIndex = current.indexOfFirst { it.id == fromId }
          val toIndex = current.indexOfFirst { it.id == toId }
          if (fromIndex >= 0 && toIndex >= 0 && fromIndex != toIndex) {
            preferences.save(current.toMutableList().apply { add(toIndex, removeAt(fromIndex)) })
          }
        }
      }

    if (showResetDialog) {
      ConfirmDialog(
        title = stringResource(R.string.ui_reset_to_default),
        subtitle = stringResource(R.string.shortcut_reset_message),
        onConfirm = {
          preferences.save(emptyList())
          showResetDialog = false
        },
        onCancel = { showResetDialog = false },
      )
    }

    Scaffold(
      topBar = {
        TopAppBar(
          title = { Text(stringResource(R.string.shortcut_settings_title)) },
          navigationIcon = {
            IconButton(onClick = backstack::removeLastOrNull) {
              Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = null)
            }
          },
          actions = {
            IconButton(onClick = { showResetDialog = true }) {
              Icon(Icons.Outlined.Restore, contentDescription = stringResource(R.string.ui_reset_to_default))
            }
          },
        )
      },
    ) { padding ->
      LazyColumn(
        state = listState,
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
        if (shortcuts.isEmpty()) {
          item {
            Text(
              text = stringResource(R.string.shortcut_settings_empty),
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
          }
        }
        items(count = shortcuts.size, key = { shortcuts[it].id }) { index ->
          val shortcut = shortcuts[index]
          ReorderableItem(reorderableState, key = shortcut.id) { isDragging ->
            ShortcutRow(
              shortcut = shortcut,
              position = index + 1,
              isDragging = isDragging,
              canMoveUp = index > 0,
              canMoveDown = index < shortcuts.lastIndex,
              // The drag handle modifier only exists inside the reorderable item scope.
              dragHandleModifier = Modifier.draggableHandle(),
              onEdit = { editingId = shortcut.id },
              onMoveUp = { move(index, index - 1) },
              onMoveDown = { move(index, index + 1) },
            )
          }
        }
        item {
          PreferenceCard {
            PreferenceListItem(
              headlineContent = { Text(stringResource(R.string.shortcut_add)) },
              leadingContent = { Icon(Icons.Outlined.Add, contentDescription = null) },
              modifier =
                Modifier.clickable {
                  // Adding opens the editor straight away, so a new row is never left blank.
                  val (updated, added) = preferences.add(preferences.shortcuts())
                  preferences.save(updated)
                  editingId = added.id
                },
            )
          }
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

    val editingIndex = shortcuts.indexOfFirst { it.id == editingId }
    if (editingId != null && editingIndex >= 0) {
      val shortcut = shortcuts[editingIndex]
      ShortcutEditorDialog(
        shortcut = shortcut,
        position = editingIndex + 1,
        onSave = { label, key ->
          val current = preferences.shortcuts()
          preferences.save(
            current.map { if (it.id == shortcut.id) it.copy(label = label, key = key) else it },
          )
          editingId = null
        },
        onDeleteRequest = { deletingId = shortcut.id },
        onDismiss = { editingId = null },
      )
    }

    val deletingIndex = shortcuts.indexOfFirst { it.id == deletingId }
    if (deletingId != null) {
      ConfirmDialog(
        title = stringResource(R.string.shortcut_delete_title),
        subtitle = stringResource(
          R.string.ui_delete_script_confirm,
          if (deletingIndex >= 0) displayLabel(shortcuts[deletingIndex].label, deletingIndex + 1) else "",
        ),
        onConfirm = {
          preferences.save(preferences.shortcuts().filterNot { it.id == deletingId })
          deletingId = null
          editingId = null
        },
        onCancel = { deletingId = null },
      )
    }
  }
}

@Composable
private fun ShortcutRow(
  shortcut: CustomShortcut,
  position: Int,
  isDragging: Boolean,
  canMoveUp: Boolean,
  canMoveDown: Boolean,
  dragHandleModifier: Modifier,
  onEdit: () -> Unit,
  onMoveUp: () -> Unit,
  onMoveDown: () -> Unit,
) {
  PreferenceCard {
    PreferenceListItem(
      headlineContent = { Text(displayLabel(shortcut.label, position)) },
      supportingContent = {
        Text(
          if (shortcut.key.isBlank()) stringResource(R.string.shortcut_not_configured)
          else stringResource(R.string.shortcut_key_summary, shortcut.key),
        )
      },
      leadingContent = { Icon(Icons.Outlined.Keyboard, contentDescription = null) },
      trailingContent = {
        Row(verticalAlignment = Alignment.CenterVertically) {
          // Dragging alone is not enough: it is unreachable with TalkBack, so the same
          // reordering is also offered as two plain buttons. They are sized down to leave
          // the row's name enough width on a narrow screen.
          IconButton(
            onClick = onMoveUp,
            enabled = canMoveUp,
            modifier = Modifier.size(40.dp),
          ) {
            Icon(
              Icons.Outlined.KeyboardArrowUp,
              contentDescription = stringResource(R.string.ui_move_up),
            )
          }
          IconButton(
            onClick = onMoveDown,
            enabled = canMoveDown,
            modifier = Modifier.size(40.dp),
          ) {
            Icon(
              Icons.Outlined.KeyboardArrowDown,
              contentDescription = stringResource(R.string.ui_move_down),
            )
          }
          IconButton(onClick = {}, modifier = Modifier.size(40.dp).then(dragHandleModifier)) {
            Icon(
              Icons.Filled.DragHandle,
              contentDescription = stringResource(R.string.ui_drag_to_reorder),
              tint =
                if (isDragging) {
                  MaterialTheme.colorScheme.primary
                } else {
                  MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
          }
        }
      },
      modifier = Modifier.clickable(onClick = onEdit),
    )
  }
}

@Composable
private fun ShortcutEditorDialog(
  shortcut: CustomShortcut,
  position: Int,
  onSave: (label: String, key: String) -> Unit,
  onDeleteRequest: () -> Unit,
  onDismiss: () -> Unit,
) {
  var draftLabel by remember(shortcut.id) { mutableStateOf(shortcut.label) }
  var draftKey by remember(shortcut.id) { mutableStateOf(shortcut.key) }
  val validKey = draftKey.trim().none(Char::isWhitespace)

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(stringResource(R.string.shortcut_edit_title, position)) },
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
        onClick = { onSave(draftLabel.trim(), draftKey.trim()) },
      ) { Text(stringResource(R.string.generic_ok)) }
    },
    // This AlertDialog has a single trailing button slot, so delete shares the row with
    // cancel rather than getting a slot of its own.
    dismissButton = {
      Row {
        TextButton(onClick = onDeleteRequest) {
          Text(stringResource(R.string.environment_variable_delete))
        }
        TextButton(onClick = onDismiss) {
          Text(stringResource(R.string.generic_cancel))
        }
      }
    },
  )
}
