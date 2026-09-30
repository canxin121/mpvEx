package app.marlboroadvance.mpvex.ui.preferences

import app.marlboroadvance.mpvex.i18n.AppLocale
import app.marlboroadvance.mpvex.i18n.localizedString
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastJoinToString
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import app.marlboroadvance.mpvex.R
import app.marlboroadvance.mpvex.database.MpvExDatabase
import app.marlboroadvance.mpvex.domain.thumbnail.ThumbnailRepository
import app.marlboroadvance.mpvex.environment.MpvExEnvironment
import app.marlboroadvance.mpvex.preferences.AdvancedPreferences
import app.marlboroadvance.mpvex.preferences.SettingsManager
import app.marlboroadvance.mpvex.preferences.preference.collectAsState
import app.marlboroadvance.mpvex.presentation.Screen
import app.marlboroadvance.mpvex.presentation.components.ConfirmDialog
import app.marlboroadvance.mpvex.presentation.crash.CrashActivity
import app.marlboroadvance.mpvex.ui.utils.LocalBackStack
import app.marlboroadvance.mpvex.utils.history.RecentlyPlayedOps
import app.marlboroadvance.mpvex.utils.logging.MpvExLog
import app.marlboroadvance.mpvex.utils.media.OpenDocumentTreeContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import me.zhanghai.compose.preference.Preference
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import me.zhanghai.compose.preference.SwitchPreference
import me.zhanghai.compose.preference.TwoTargetIconButtonPreference
import org.koin.compose.koinInject
import java.io.File
import kotlin.io.path.deleteIfExists
import kotlin.io.path.outputStream
import kotlin.io.path.readLines

@Serializable
object AdvancedPreferencesScreen : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val context = LocalContext.current
    val backStack = LocalBackStack.current
    val preferences = koinInject<AdvancedPreferences>()
    val settingsManager = koinInject<SettingsManager>()
    val scope = rememberCoroutineScope()
    var showImportDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var importStats by remember { mutableStateOf<SettingsManager.ImportStats?>(null) }
    var exportStats by remember { mutableStateOf<SettingsManager.ExportStats?>(null) }

    // Export settings launcher
    val exportLauncher =
      rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/xml"),
      ) { uri ->
        uri?.let {
          scope.launch {
            settingsManager.exportSettings(it).fold(
              onSuccess = { stats ->
                exportStats = stats
                showExportDialog = true
              },
              onFailure = { error ->
                Toast.makeText(
                  context,
                  localizedString(R.string.ui_export_failed_1_s, error.message),
                  Toast.LENGTH_LONG,
                ).show()
              },
            )
          }
        }
      }

    // Import settings launcher
    val importLauncher =
      rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
      ) { uri ->
        uri?.let {
          scope.launch {
            settingsManager.importSettings(it).fold(
              onSuccess = { stats ->
                importStats = stats
                showImportDialog = true
                // A restored backup may carry a different interface language, which would
                // otherwise sit in the preferences without ever being applied.
                val stored = AppLocale.storedTag(context)
                if (stored != AppLocale.selectedTag(context)) {
                  AppLocale.set(context, stored)
                }
              },
              onFailure = { error ->
                Toast.makeText(
                  context,
                  localizedString(R.string.ui_import_failed_1_s, error.message),
                  Toast.LENGTH_LONG,
                ).show()
              },
            )
          }
        }
      }

    // Export results dialog
    if (showExportDialog && exportStats != null) {
      AlertDialog(
        onDismissRequest = { showExportDialog = false },
        title = { Text(localizedString(R.string.ui_export_complete)) },
        text = {
          Column(
            modifier = Modifier
              .fillMaxWidth()
              .verticalScroll(rememberScrollState()),
          ) {
            Text(
              localizedString(R.string.ui_successfully_exported_1_s_items, exportStats?.totalExported)
            )
          }
        },
        confirmButton = {
          TextButton(onClick = { showExportDialog = false }) {
            Text(localizedString(R.string.generic_ok))
          }
        },
      )
    }

    // Import results dialog
    if (showImportDialog && importStats != null) {
      AlertDialog(
        onDismissRequest = { showImportDialog = false },
        title = { Text(localizedString(R.string.ui_import_complete)) },
        text = {
          Text(
            localizedString(
              R.string.ui_import_result,
              importStats?.imported,
              importStats?.failed,
              importStats?.version,
            ),
          )
        },
        confirmButton = {
          TextButton(onClick = { showImportDialog = false }) {
            Text(localizedString(R.string.generic_ok))
          }
        },
      )
    }

    Scaffold(
      topBar = {
        TopAppBar(
          title = { 
            Text(
              text = stringResource(R.string.pref_advanced),
              style = MaterialTheme.typography.headlineSmall,
              fontWeight = FontWeight.ExtraBold,
              color = MaterialTheme.colorScheme.primary,
            )
          },
          navigationIcon = {
            IconButton(onClick = backStack::removeLastOrNull) {
              Icon(
                Icons.AutoMirrored.Default.ArrowBack, 
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
              )
            }
          },
        )
      },
    ) { padding ->
      ProvidePreferenceLocals {
        val locationPicker =
          rememberLauncherForActivityResult(
            OpenDocumentTreeContract(),
          ) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult

            val flags = Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, flags)
            preferences.mpvConfStorageUri.set(uri.toString())

            // Auto-create standard MPV folder structure
            scope.launch(Dispatchers.IO) {
              runCatching {
                val tree = DocumentFile.fromTreeUri(context, uri)
                if (tree != null && tree.exists() && tree.canWrite()) {
                  val subdirs = listOf("fonts", "script-opts", "scripts", "shaders")
                  for (name in subdirs) {
                    val existing = tree.listFiles().firstOrNull {
                      it.isDirectory && it.name?.equals(name, ignoreCase = true) == true
                    }
                    if (existing == null) {
                      tree.createDirectory(name)
                    }
                  }
                  // Create default mpv.conf if missing
                  val hasConf = tree.listFiles().any {
                    it.isFile && it.name?.equals("mpv.conf", ignoreCase = true) == true
                  }
                  if (!hasConf) {
                    tree.createFile("application/octet-stream", "mpv.conf")
                  }
                  withContext(Dispatchers.Main) {
                    Toast.makeText(context, localizedString(R.string.ui_mpv_directory_ready), Toast.LENGTH_SHORT).show()
                  }
                }
              }.onFailure { e ->
                MpvExLog.e("AdvancedPrefs", e, "Error creating MPV directory structure")
              }
            }
          }
        val mpvConfStorageLocation by preferences.mpvConfStorageUri.collectAsState()
        LazyColumn(
          modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        ) {
          // Backup & Restore Section
          item {
            PreferenceSectionHeader(title = localizedString(R.string.ui_backup_restore))
          }
          
          item {
            PreferenceCard {
              Preference(
                title = { Text(text = localizedString(R.string.pref_export_settings_title)) },
                summary = { 
                  Text(
                    text = localizedString(R.string.pref_export_settings_summary),
                    color = MaterialTheme.colorScheme.outline,
                  ) 
                },
                icon = { 
                  Icon(
                    Icons.Outlined.FileUpload, 
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                  ) 
                },
                onClick = {
                  exportLauncher.launch(settingsManager.getDefaultExportFilename())
                },
              )
              
              PreferenceDivider()
              
              Preference(
                title = { Text(text = localizedString(R.string.pref_import_settings_title)) },
                summary = { 
                  Text(
                    text = localizedString(R.string.pref_import_settings_summary),
                    color = MaterialTheme.colorScheme.outline,
                  ) 
                },
                icon = { 
                  Icon(
                    Icons.Outlined.FileDownload, 
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                  ) 
                },
                onClick = {
                  importLauncher.launch(arrayOf("text/xml", "application/xml", "*/*"))
                },
              )
            }
          }
          
          // MPV Configuration Section
          item {
            PreferenceSectionHeader(title = localizedString(R.string.ui_mpv_configuration))
          }
          
          item {
            PreferenceCard {
              var mpvConf by remember { mutableStateOf(preferences.mpvConf.get()) }
              var inputConf by remember { mutableStateOf(preferences.inputConf.get()) }
              
              // Load config files when storage location changes
              LaunchedEffect(mpvConfStorageLocation) {
                if (mpvConfStorageLocation.isBlank()) return@LaunchedEffect
                withContext(Dispatchers.IO) {
                  val tempFile = kotlin.io.path.createTempFile()
                  runCatching {
                    val tree =
                      DocumentFile.fromTreeUri(
                        context,
                        mpvConfStorageLocation.toUri(),
                      )
                    val mpvConfFile = tree?.findFile("mpv.conf")
                    if (mpvConfFile != null && mpvConfFile.exists()) {
                      context.contentResolver
                        .openInputStream(
                          mpvConfFile.uri,
                        )?.copyTo(tempFile.outputStream())
                      val content = tempFile.readLines().fastJoinToString("\n")
                      preferences.mpvConf.set(content)
                      File(context.filesDir, "mpv.conf").writeText(content)
                      withContext(Dispatchers.Main) {
                        mpvConf = content
                      }
                    }
                  }
                  tempFile.deleteIfExists()
                }
              }
              
              // Load input.conf when storage location changes
              LaunchedEffect(mpvConfStorageLocation) {
                if (mpvConfStorageLocation.isBlank()) return@LaunchedEffect
                withContext(Dispatchers.IO) {
                  val tempFile = kotlin.io.path.createTempFile()
                  runCatching {
                    val tree =
                      DocumentFile.fromTreeUri(
                        context,
                        mpvConfStorageLocation.toUri(),
                      )
                    val inputConfFile = tree?.findFile("input.conf")
                    if (inputConfFile != null && inputConfFile.exists()) {
                      context.contentResolver
                        .openInputStream(
                          inputConfFile.uri,
                        )?.copyTo(tempFile.outputStream())
                      val content = tempFile.readLines().fastJoinToString("\n")
                      preferences.inputConf.set(content)
                      File(context.filesDir, "input.conf").writeText(content)
                      withContext(Dispatchers.Main) {
                        inputConf = content
                      }
                    }
                  }
                  tempFile.deleteIfExists()
                }
              }
              
              TwoTargetIconButtonPreference(
                title = { Text(stringResource(R.string.pref_advanced_mpv_conf_storage_location)) },
                summary = {
                  if (mpvConfStorageLocation.isNotBlank()) {
                    Text(
                      MpvExEnvironment.selectedConfigLocation(mpvConfStorageLocation)
                        ?: mpvConfStorageLocation,
                      color = MaterialTheme.colorScheme.outline,
                    )
                  }
                },
                onClick = { locationPicker.launch(null) },
                iconButtonIcon = { 
                  Icon(
                    Icons.Default.Clear, 
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                  ) 
                },
                onIconButtonClick = { preferences.mpvConfStorageUri.delete() },
                iconButtonEnabled = mpvConfStorageLocation.isNotBlank(),
              )
              
              PreferenceDivider()
              
              Preference(
                title = { Text(stringResource(R.string.pref_advanced_mpv_conf)) },
                summary = {
                  val firstLine = mpvConf.lines().firstOrNull()
                  if (firstLine != null && firstLine.isNotBlank()) {
                    Text(
                      firstLine,
                      color = MaterialTheme.colorScheme.outline,
                    )
                  } else {
                    Text(
                      localizedString(R.string.ui_tap_to_edit_configuration),
                      color = MaterialTheme.colorScheme.outline,
                    )
                  }
                },
                onClick = {
                  backStack.add(ConfigEditorScreen(ConfigEditorScreen.ConfigType.MPV_CONF))
                },
              )
              
              PreferenceDivider()
              
              Preference(
                title = { Text(stringResource(R.string.pref_advanced_input_conf)) },
                summary = {
                  val firstLine = inputConf.lines().firstOrNull()
                  if (firstLine != null && firstLine.isNotBlank()) {
                    Text(
                      firstLine,
                      color = MaterialTheme.colorScheme.outline,
                    )
                  } else {
                    Text(
                      localizedString(R.string.ui_tap_to_edit_configuration),
                      color = MaterialTheme.colorScheme.outline,
                    )
                  }
                },
                onClick = {
                  backStack.add(ConfigEditorScreen(ConfigEditorScreen.ConfigType.INPUT_CONF))
                },
              )

              PreferenceDivider()

              Preference(
                title = { Text(stringResource(R.string.environment_variables_title)) },
                summary = {
                  Text(
                    stringResource(R.string.environment_variables_summary),
                    color = MaterialTheme.colorScheme.outline,
                  )
                },
                onClick = { backStack.add(EnvironmentVariablesScreen) },
              )
            }
          }
          
          // Lua Scripts Section
          item {
            PreferenceSectionHeader(title = localizedString(R.string.ui_lua_scripts))
          }

          item {
            PreferenceCard {
              val enabled by preferences.enableLuaScripts.collectAsState()
              val selected by preferences.selectedLuaScripts.collectAsState()

              SwitchPreference(
                value = enabled,
                onValueChange = preferences.enableLuaScripts::set,
                title = { Text(stringResource(R.string.pref_enable_lua_scripts_title)) },
                summary = { Text(stringResource(R.string.pref_enable_lua_scripts_summary)) },
              )

              PreferenceDivider()

              Preference(
                title = { Text(stringResource(R.string.pref_manage_lua_scripts_title)) },
                summary = {
                  Text(
                    if (mpvConfStorageLocation.isBlank()) {
                      stringResource(R.string.pref_lua_pick_directory)
                    } else {
                      app.marlboroadvance.mpvex.i18n.localizedQuantityString(
                        R.plurals.lua_selected_count, selected.size, selected.size,
                      )
                    },
                    color = MaterialTheme.colorScheme.outline,
                  )
                },
                onClick = { backStack.add(LuaScriptsScreen) },
                enabled = mpvConfStorageLocation.isNotBlank(),
              )

              Text(
                stringResource(R.string.pref_lua_restart_summary),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
              )
            }
          }

          // C Plugins Section
          item {
            PreferenceSectionHeader(title = localizedString(R.string.ui_c_plugins))
          }

          item {
            PreferenceCard {
              var showCPluginDialog by remember { mutableStateOf(false) }
              var availableCPlugins by remember { mutableStateOf<List<String>>(emptyList()) }
              val selectedCPlugins by preferences.selectedCPlugins.collectAsState()
              val enableCPlugins by preferences.enableCPlugins.collectAsState()

              SwitchPreference(
                value = enableCPlugins,
                onValueChange = preferences.enableCPlugins::set,
                title = { Text(localizedString(R.string.ui_enable_c_plugins)) },
                summary = {
                  Text(
                    localizedString(R.string.ui_load_selected_mpv_c_plugins_so_when_the_player_starts),
                    color = MaterialTheme.colorScheme.outline,
                  )
                },
              )

              PreferenceDivider()

              Preference(
                title = { Text(localizedString(R.string.ui_manage_c_plugins)) },
                summary = {
                  when {
                    !enableCPlugins -> Text(
                      localizedString(R.string.ui_enable_c_plugins_first),
                      color = MaterialTheme.colorScheme.outline,
                    )
                    mpvConfStorageLocation.isBlank() -> Text(
                      localizedString(R.string.ui_set_mpv_config_storage_location_first),
                      color = MaterialTheme.colorScheme.outline,
                    )
                    selectedCPlugins.isEmpty() -> Text(
                      localizedString(R.string.ui_no_plugins_selected),
                      color = MaterialTheme.colorScheme.outline,
                    )
                    else -> Text(
                      "${selectedCPlugins.size} plugin(s) selected: ${selectedCPlugins.joinToString(", ")}",
                      color = MaterialTheme.colorScheme.outline,
                    )
                  }
                },
                onClick = {
                  scope.launch(Dispatchers.IO) {
                    val plugins = runCatching {
                      val tree = DocumentFile.fromTreeUri(context, mpvConfStorageLocation.toUri())
                      if (tree == null || !tree.exists() || !tree.canRead()) {
                        emptyList()
                      } else {
                        val sourceDirectories = buildList {
                          add(tree)
                          tree.listFiles().firstOrNull {
                            it.isDirectory && it.name?.equals("scripts", ignoreCase = true) == true
                          }?.let(::add)
                        }
                        sourceDirectories
                          .flatMap { it.listFiles().asList() }
                          .filter { file ->
                            val name = file.name
                            file.isFile &&
                              name != null &&
                              name.substringAfterLast('.', "").equals("so", ignoreCase = true)
                          }
                          .mapNotNull { it.name }
                          .distinct()
                          .sorted()
                      }
                    }.getOrElse { error ->
                      withContext(Dispatchers.Main) {
                        Toast.makeText(
                          context,
                          localizedString(R.string.ui_error_reading_plugins_directory_1_s, error.message),
                          Toast.LENGTH_LONG,
                        ).show()
                      }
                      emptyList()
                    }

                    withContext(Dispatchers.Main) {
                      availableCPlugins = plugins
                      if (plugins.isEmpty()) {
                        Toast.makeText(
                          context,
                          localizedString(R.string.ui_no_so_files_found_in_the_mpv_directory_or_its_scripts_folder),
                          Toast.LENGTH_SHORT,
                        ).show()
                      }
                      showCPluginDialog = true
                    }
                  }
                },
                enabled = enableCPlugins && mpvConfStorageLocation.isNotBlank(),
              )

              if (showCPluginDialog) {
                CPluginSelectionDialog(
                  availablePlugins = availableCPlugins,
                  selectedPlugins = selectedCPlugins,
                  onPluginsSelected = { newSelection ->
                    preferences.selectedCPlugins.set(newSelection)
                    showCPluginDialog = false
                  },
                  onDismiss = { showCPluginDialog = false },
                )
              }
            }
          }

          // History Section
          item {
            PreferenceSectionHeader(title = localizedString(R.string.ui_history))
          }
          
          item {
            PreferenceCard {
              var isConfirmDialogShown by remember { mutableStateOf(false) }
              val mpvexDatabase = koinInject<MpvExDatabase>()
              val enableRecentlyPlayed by preferences.enableRecentlyPlayed.collectAsState()
              
              SwitchPreference(
                value = enableRecentlyPlayed,
                onValueChange = preferences.enableRecentlyPlayed::set,
                title = { Text(stringResource(R.string.pref_advanced_enable_recently_played_title)) },
                summary = { 
                  Text(
                    stringResource(R.string.pref_advanced_enable_recently_played_summary),
                    color = MaterialTheme.colorScheme.outline,
                  ) 
                },
              )
              
              PreferenceDivider()
              
              Preference(
                title = { Text(stringResource(R.string.pref_advanced_clear_playback_history)) },
                onClick = { isConfirmDialogShown = true },
              )
              
              if (isConfirmDialogShown) {
                ConfirmDialog(
                  stringResource(R.string.pref_advanced_clear_playback_history_confirm_title),
                  stringResource(R.string.pref_advanced_clear_playback_history_confirm_subtitle),
                  onConfirm = {
                    scope.launch(Dispatchers.IO) {
                      runCatching {
                        mpvexDatabase.videoDataDao().clearAllPlaybackStates()
                        RecentlyPlayedOps.clearAll()
                      }.onSuccess {
                        withContext(Dispatchers.Main) {
                          isConfirmDialogShown = false
                          Toast
                            .makeText(
                              context,
                              localizedString(R.string.pref_advanced_cleared_playback_history),
                              Toast.LENGTH_SHORT,
                            ).show()
                        }
                      }.onFailure { error ->
                        withContext(Dispatchers.Main) {
                          isConfirmDialogShown = false
                          Toast
                            .makeText(
                              context,
                              localizedString(R.string.ui_failed_to_clear_1_s, error.message ?: localizedString(R.string.ui_unknown_error)),
                              Toast.LENGTH_LONG,
                            ).show()
                        }
                      }
                    }
                  },
                  onCancel = { isConfirmDialogShown = false },
                )
              }
            }
          }
          
          // Cache Section
          item {
            PreferenceSectionHeader(title = localizedString(R.string.ui_cache))
          }
          
          item {
            PreferenceCard {
              var mpvConf by remember { mutableStateOf(preferences.mpvConf.get()) }
              var isClearThumbsConfirmShown by remember { mutableStateOf(false) }
              val thumbnailRepository = koinInject<ThumbnailRepository>()
              
              Preference(
                title = { Text(text = localizedString(R.string.pref_clear_config_cache_title)) },
                summary = { 
                  Text(
                    text = localizedString(R.string.pref_clear_config_cache_summary),
                    color = MaterialTheme.colorScheme.outline,
                  ) 
                },
                onClick = {
                  scope.launch(Dispatchers.IO) {
                    val mpvConfFile = File(context.filesDir, "mpv.conf")
                    mpvConfFile.delete()
                    // Clear preferences too
                    preferences.mpvConf.delete()
                    withContext(Dispatchers.Main) {
                      mpvConf = ""
                      Toast
                        .makeText(
                          context,
                          localizedString(R.string.ui_config_cache_cleared),
                          Toast.LENGTH_SHORT,
                        ).show()
                    }
                  }
                },
              )
              
              PreferenceDivider()

              Preference(
                title = { Text(text = localizedString(R.string.pref_clear_thumbnail_cache_title)) },
                summary = {
                  Text(
                    text = localizedString(R.string.pref_clear_thumbnail_cache_summary),
                    color = MaterialTheme.colorScheme.outline,
                  )
                },
                onClick = { isClearThumbsConfirmShown = true },
              )

              if (isClearThumbsConfirmShown) {
                ConfirmDialog(
                  title = localizedString(R.string.ui_clear_thumbnail_cache),
                  subtitle = localizedString(R.string.ui_this_will_delete_cached_thumbnails_from_storage_and_memory),
                  onConfirm = {
                    scope.launch(Dispatchers.IO) {
                      runCatching {
                        thumbnailRepository.clearThumbnailCache()
                      }.onSuccess {
                        withContext(Dispatchers.Main) {
                          isClearThumbsConfirmShown = false
                          Toast.makeText(context, localizedString(R.string.ui_thumbnail_cache_cleared), Toast.LENGTH_SHORT).show()
                        }
                      }.onFailure { error ->
                        withContext(Dispatchers.Main) {
                          isClearThumbsConfirmShown = false
                          Toast.makeText(context, localizedString(R.string.ui_failed_to_clear_1_s, error.message), Toast.LENGTH_LONG).show()
                        }
                      }
                    }
                  },
                  onCancel = { isClearThumbsConfirmShown = false },
                )
              }
              
              PreferenceDivider()
              
              Preference(
                title = { Text(text = stringResource(id = R.string.pref_advanced_clear_fonts_cache)) },
                summary = { 
                  Text(
                    text = localizedString(R.string.ui_remove_all_cached_subtitle_fonts),
                    color = MaterialTheme.colorScheme.outline,
                  ) 
                },
                onClick = {
                  scope.launch(Dispatchers.IO) {
                    val fontsDir = File(context.filesDir.path + "/fonts")
                    if (fontsDir.exists()) {
                      fontsDir.listFiles()?.forEach { file ->
                        // Delete all font files
                        if (file.isFile &&
                          file.name
                            .lowercase()
                            .matches(".*\\.[ot]tf$".toRegex())
                        ) {
                          file.delete()
                        }
                      }
                    }
                    withContext(Dispatchers.Main) {
                      Toast
                        .makeText(
                          context,
                          localizedString(R.string.pref_advanced_cleared_fonts_cache),
                          Toast.LENGTH_SHORT,
                        ).show()
                    }
                  }
                },
              )
            }
          }
          
          // Logging Section
          item {
            PreferenceSectionHeader(title = localizedString(R.string.ui_logging))
          }
          
          item {
            PreferenceCard {
              val activity = LocalActivity.current!!
              @Suppress("DEPRECATION")
              val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
              val verboseLogging by preferences.verboseLogging.collectAsState()
              val fileLogging by preferences.enableFileLogging.collectAsState()
              val nativeLogging by preferences.captureNativeOutput.collectAsState()

              SwitchPreference(
                value = verboseLogging,
                onValueChange = preferences.verboseLogging::set,
                title = { Text(stringResource(R.string.pref_advanced_verbose_logging_title)) },
                summary = {
                  Text(
                    stringResource(R.string.pref_advanced_verbose_logging_summary),
                    color = MaterialTheme.colorScheme.outline,
                  )
                },
              )

              PreferenceDivider()

              SwitchPreference(
                value = fileLogging,
                onValueChange = preferences.enableFileLogging::set,
                title = { Text(stringResource(R.string.pref_advanced_save_logs_title)) },
                summary = {
                  Text(
                    stringResource(R.string.pref_advanced_save_logs_summary),
                    color = MaterialTheme.colorScheme.outline,
                  )
                },
              )

              PreferenceDivider()

              SwitchPreference(
                value = nativeLogging,
                enabled = fileLogging,
                onValueChange = preferences.captureNativeOutput::set,
                title = { Text(stringResource(R.string.pref_advanced_native_logs_title)) },
                summary = {
                  Text(
                    stringResource(R.string.pref_advanced_native_logs_summary),
                    color = MaterialTheme.colorScheme.outline,
                  )
                },
              )

              PreferenceDivider()

              Preference(
                title = { Text(stringResource(R.string.pref_advanced_view_logs_title)) },                summary = {
                  Text(
                    stringResource(R.string.pref_advanced_view_logs_summary),
                    color = MaterialTheme.colorScheme.outline,
                  )
                },
                onClick = { backStack.add(LogsScreen) },
              )

              PreferenceDivider()

              Preference(
                title = { Text(stringResource(R.string.pref_advanced_dump_logs_title)) },
                summary = { 
                  Text(
                    stringResource(R.string.pref_advanced_dump_logs_summary),
                    color = MaterialTheme.colorScheme.outline,
                  ) 
                },
                onClick = {
                  scope.launch(Dispatchers.IO) {
                    val deviceInfo = CrashActivity.collectDeviceInfo()
                    val logcat = CrashActivity.collectLogcat()
    
                    clipboard.setText(AnnotatedString(CrashActivity.concatLogs(deviceInfo, null, logcat)))
                    CrashActivity.shareLogs(deviceInfo, null, logcat, activity)
                  }
                },
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun CPluginSelectionDialog(
  availablePlugins: List<String>,
  selectedPlugins: Set<String>,
  onPluginsSelected: (Set<String>) -> Unit,
  onDismiss: () -> Unit,
) {
  var pendingSelection by remember(selectedPlugins) {
    mutableStateOf(selectedPlugins.toMutableSet())
  }

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text(localizedString(R.string.ui_select_c_plugins)) },
    text = {
      Column(
        modifier =
          Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
      ) {
        if (availablePlugins.isEmpty()) {
          Text(localizedString(R.string.ui_no_c_plugins_found_in_the_configured_mpv_directory))
        } else {
          Text(
            text = localizedString(R.string.ui_select_c_plugins_summary),
            modifier = Modifier.padding(bottom = 8.dp),
          )
          availablePlugins.forEach { plugin ->
            Row(
              modifier =
                Modifier
                  .fillMaxWidth()
                  .padding(vertical = 4.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Checkbox(
                checked = plugin in pendingSelection,
                onCheckedChange = { checked ->
                  pendingSelection =
                    if (checked) {
                      (pendingSelection + plugin).toMutableSet()
                    } else {
                      (pendingSelection - plugin).toMutableSet()
                    }
                },
              )
              Text(
                text = plugin,
                modifier = Modifier.padding(start = 8.dp),
              )
            }
          }
        }
      }
    },
    confirmButton = {
      TextButton(onClick = { onPluginsSelected(pendingSelection) }) {
        Text(stringResource(R.string.generic_ok))
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) {
        Text(stringResource(R.string.generic_cancel))
      }
    },
  )
}
