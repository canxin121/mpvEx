package app.marlboroadvance.mpvex.ui.preferences

import android.content.Context
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.marlboroadvance.mpvex.R
import app.marlboroadvance.mpvex.i18n.localizedString
import app.marlboroadvance.mpvex.presentation.Screen
import app.marlboroadvance.mpvex.ui.utils.LocalBackStack
import app.marlboroadvance.mpvex.utils.logging.LogRecord
import app.marlboroadvance.mpvex.utils.logging.LogSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.Serializable

/**
 * Everything the app logged while it was running, with filters.
 *
 * The list is fed by the in-memory ring buffer, so it works even when file
 * logging is off; the export action is the one that needs a log file on disk.
 */
@Serializable
object LogsScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val context = LocalContext.current
    val backStack = LocalBackStack.current
    val scope = rememberCoroutineScope()
    val viewModel = viewModel<LogsViewModel>(factory = LogsViewModel.Factory)

    val records by viewModel.records.collectAsStateWithLifecycle()
    val search by viewModel.search.collectAsStateWithLifecycle()
    val tagFilter by viewModel.tagFilter.collectAsStateWithLifecycle()
    val minimumPriority by viewModel.minimumPriority.collectAsStateWithLifecycle()
    val sourceFilter by viewModel.sourceFilter.collectAsStateWithLifecycle()
    val follow by viewModel.follow.collectAsStateWithLifecycle()
    val paused by viewModel.paused.collectAsStateWithLifecycle()

    var showClearDialog by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    // Keep the newest line in view while following, unless the user scrolled up.
    LaunchedEffect(records.size, follow) {
      if (follow && records.isNotEmpty()) {
        listState.scrollToItem(records.lastIndex)
      }
    }

    Scaffold(
      topBar = {
        TopAppBar(
          title = {
            Text(
              text = localizedString(R.string.logs_screen_title),
              style = MaterialTheme.typography.headlineSmall,
              fontWeight = FontWeight.ExtraBold,
              color = MaterialTheme.colorScheme.primary,
            )
          },
          navigationIcon = {
            IconButton(onClick = backStack::removeLastOrNull) {
              Icon(
                Icons.AutoMirrored.Default.ArrowBack,
                contentDescription = localizedString(R.string.back),
                tint = MaterialTheme.colorScheme.secondary,
              )
            }
          },
          actions = {
            IconButton(onClick = viewModel::togglePause) {
              Icon(
                if (paused) Icons.Default.PlayArrow else Icons.Default.Pause,
                contentDescription = localizedString(R.string.logs_screen_follow),
                tint = MaterialTheme.colorScheme.secondary,
              )
            }
            IconButton(onClick = { copyVisibleLogs(context, viewModel.renderVisible()) }) {
              Icon(
                Icons.Default.ContentCopy,
                contentDescription = localizedString(R.string.logs_screen_copy),
                tint = MaterialTheme.colorScheme.secondary,
              )
            }
            IconButton(onClick = { shareVisibleLogs(context, scope, viewModel.renderVisible()) }) {
              Icon(
                Icons.Default.Share,
                contentDescription = localizedString(R.string.logs_screen_share),
                tint = MaterialTheme.colorScheme.secondary,
              )
            }
            IconButton(onClick = { exportLogFiles(context, scope) }) {
              Icon(
                Icons.Default.Share,
                contentDescription = localizedString(R.string.logs_screen_export),
                tint = MaterialTheme.colorScheme.secondary,
              )
            }
            IconButton(onClick = { showClearDialog = true }) {
              Icon(
                Icons.Default.Delete,
                contentDescription = localizedString(R.string.logs_screen_clear),
                tint = MaterialTheme.colorScheme.secondary,
              )
            }
          },
        )
      },
    ) { padding ->
      Column(
        modifier =
          Modifier
            .fillMaxSize()
            .padding(padding),
      ) {
        FilterBar(
          search = search,
          onSearchChange = viewModel::setSearch,
          tagFilter = tagFilter,
          onTagChange = viewModel::setTagFilter,
          minimumPriority = minimumPriority,
          onMinimumPriorityChange = viewModel::setMinimumPriority,
          sourceFilter = sourceFilter,
          onSourceChange = viewModel::setSource,
          follow = follow,
          onFollowChange = viewModel::setFollow,
        )

        Text(
          text = localizedString(R.string.logs_screen_entries_quantity, records.size.toString()),
          style = MaterialTheme.typography.labelMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )

        if (records.isEmpty()) {
          Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
          ) {
            Text(
              localizedString(R.string.logs_screen_empty),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        } else {
          LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
          ) {
            items(records, key = { it.id }) { record ->
              LogRow(record)
              HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
          }
        }
      }
    }

    if (showClearDialog) {
      AlertDialog(
        onDismissRequest = { showClearDialog = false },
        title = { Text(localizedString(R.string.logs_screen_clear_confirm_title)) },
        confirmButton = {
          TextButton(
            onClick = {
              viewModel.clear()
              showClearDialog = false
              Toast.makeText(context, localizedString(R.string.logs_screen_cleared), Toast.LENGTH_SHORT).show()
            },
          ) { Text(localizedString(R.string.logs_screen_clear)) }
        },
        dismissButton = {
          TextButton(onClick = { showClearDialog = false }) {
            Text(localizedString(R.string.generic_cancel))
          }
        },
      )
    }
  }

  @Composable
  private fun FilterBar(
    search: String,
    onSearchChange: (String) -> Unit,
    tagFilter: String,
    onTagChange: (String) -> Unit,
    minimumPriority: Int,
    onMinimumPriorityChange: (Int) -> Unit,
    sourceFilter: LogSource?,
    onSourceChange: (LogSource?) -> Unit,
    follow: Boolean,
    onFollowChange: (Boolean) -> Unit,
  ) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
      Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        OutlinedTextField(
          value = search,
          onValueChange = onSearchChange,
          label = { Text(localizedString(R.string.logs_screen_search_hint)) },
          leadingIcon = { Icon(Icons.Default.Search, null) },
          singleLine = true,
          modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
          value = tagFilter,
          onValueChange = onTagChange,
          label = { Text(localizedString(R.string.logs_screen_tag_hint)) },
          singleLine = true,
          modifier = Modifier.weight(1f),
        )
      }

      Row(
        modifier =
          Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        LevelFilterChip(minimumPriority, onMinimumPriorityChange)
        SourceFilterChip(sourceFilter, onSourceChange)

        Row(verticalAlignment = Alignment.CenterVertically) {
          Checkbox(checked = follow, onCheckedChange = onFollowChange)
          Text(localizedString(R.string.logs_screen_follow), style = MaterialTheme.typography.labelLarge)
        }
      }
    }
  }

  @Composable
  private fun LevelFilterChip(
    minimumPriority: Int,
    onMinimumPriorityChange: (Int) -> Unit,
  ) {
    var expanded by remember { mutableStateOf(false) }
    Box {
      AssistChip(
        onClick = { expanded = true },
        label = { Text(levelLabel(minimumPriority)) },
      )
      DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        LogsViewModel.LEVEL_OPTIONS.forEach { priority ->
          DropdownMenuItem(
            text = { Text(levelLabel(priority)) },
            onClick = {
              onMinimumPriorityChange(priority)
              expanded = false
            },
          )
        }
      }
    }
  }

  @Composable
  private fun SourceFilterChip(
    sourceFilter: LogSource?,
    onSourceChange: (LogSource?) -> Unit,
  ) {
    var expanded by remember { mutableStateOf(false) }
    Box {
      AssistChip(
        onClick = { expanded = true },
        label = { Text(sourceLabel(sourceFilter)) },
      )
      DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        DropdownMenuItem(
          text = { Text(localizedString(R.string.logs_screen_source_all)) },
          onClick = {
            onSourceChange(null)
            expanded = false
          },
        )
        LogSource.entries.forEach { source ->
          DropdownMenuItem(
            text = { Text(sourceLabel(source)) },
            onClick = {
              onSourceChange(source)
              expanded = false
            },
          )
        }
      }
    }
  }

  @Composable
  private fun LogRow(record: LogRecord) {
    val color =
      when (record.priority) {
        Log.ERROR, Log.ASSERT -> MaterialTheme.colorScheme.error
        Log.WARN -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurface
      }
    SelectionContainer(
      modifier =
        Modifier
          .fillMaxWidth()
          .padding(horizontal = 16.dp, vertical = 6.dp),
    ) {
      Text(
        text = record.message,
        color = color,
        fontFamily = FontFamily.Monospace,
        style = MaterialTheme.typography.labelSmall,
      )
    }
  }
}

private fun copyVisibleLogs(
  context: Context,
  text: String,
) {
  copyTextToClipboard(context, text)
  Toast.makeText(context, context.getString(R.string.logs_screen_copied), Toast.LENGTH_SHORT).show()
}

@Composable
private fun levelLabel(priority: Int): String =
  when (priority) {
    Log.VERBOSE -> localizedString(R.string.logs_screen_level_verbose)
    Log.DEBUG -> localizedString(R.string.logs_screen_level_debug)
    Log.INFO -> localizedString(R.string.logs_screen_level_info)
    Log.WARN -> localizedString(R.string.logs_screen_level_warn)
    else -> localizedString(R.string.logs_screen_level_error)
  }

@Composable
private fun sourceLabel(source: LogSource?): String =
  when (source) {
    null -> localizedString(R.string.logs_screen_source_all)
    LogSource.APP -> localizedString(R.string.logs_screen_source_app)
    LogSource.MPV -> localizedString(R.string.logs_screen_source_mpv)
    LogSource.NATIVE -> localizedString(R.string.logs_screen_source_native)
    LogSource.CRASH -> localizedString(R.string.logs_screen_source_crash)
  }
