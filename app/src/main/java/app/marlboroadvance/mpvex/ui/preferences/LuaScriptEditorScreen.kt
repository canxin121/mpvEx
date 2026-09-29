package app.marlboroadvance.mpvex.ui.preferences

import app.marlboroadvance.mpvex.R
import app.marlboroadvance.mpvex.i18n.localizedString
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme

import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import app.marlboroadvance.mpvex.preferences.AdvancedPreferences
import app.marlboroadvance.mpvex.plugins.isSafeScriptFileName
import app.marlboroadvance.mpvex.preferences.preference.collectAsState
import app.marlboroadvance.mpvex.presentation.Screen
import app.marlboroadvance.mpvex.presentation.components.ConfirmDialog
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import app.marlboroadvance.mpvex.ui.utils.LocalBackStack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject
import java.io.File

@Serializable
data class LuaScriptEditorScreen(
  val scriptName: String?
) : Screen {

  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val context = LocalContext.current
    val backStack = LocalBackStack.current
    val preferences = koinInject<AdvancedPreferences>()
    val scope = rememberCoroutineScope()

    val mpvConfStorageLocation by preferences.mpvConfStorageUri.collectAsState()

    val isNewScript = scriptName == null
    var scriptContent by remember { mutableStateOf("") }
    val extension = if (scriptName?.endsWith(".js", ignoreCase = true) == true) "js" else "lua"
    var fileName by remember { mutableStateOf(scriptName?.substringBeforeLast('.') ?: "") }
    var hasUnsavedChanges by remember { mutableStateOf(isNewScript) }
    var showDeleteDialog by remember { mutableStateOf(false) }

    // Load script content if editing existing script.
    LaunchedEffect(scriptName, mpvConfStorageLocation) {
      if (scriptName != null && mpvConfStorageLocation.isNotBlank()) {
        val result = withContext(Dispatchers.IO) {
          runCatching {
            val tree = DocumentFile.fromTreeUri(context, mpvConfStorageLocation.toUri())
            if (tree != null && tree.exists() && tree.canRead()) {
              // Try to find "scripts" subdirectory first (case-insensitive)
              val scriptsDir = tree.listFiles().firstOrNull {
                  it.isDirectory && it.name?.equals("scripts", ignoreCase = true) == true
              } ?: tree

              val scriptFile = checkNotNull(scriptsDir.findFile(scriptName)) { localizedString(R.string.ui_script_not_found) }
              checkNotNull(context.contentResolver.openInputStream(scriptFile.uri)) {
                localizedString(R.string.ui_could_not_read_script, scriptName)
              }.bufferedReader().use { it.readText() }
            } else {
              error(localizedString(R.string.ui_mpv_directory_unavailable))
            }
          }
        }
        result.onSuccess { content ->
          scriptContent = content
          hasUnsavedChanges = false
        }.onFailure { error ->
          Toast.makeText(context, localizedString(R.string.ui_failed_to_load_1_s, error.message), Toast.LENGTH_LONG).show()
        }
      }
    }

    fun saveScript() {
      val typedName = fileName.trim()
      val finalFileName = if (typedName.endsWith(".lua", ignoreCase = true) ||
        typedName.endsWith(".js", ignoreCase = true)) typedName else "$typedName.$extension"
      if (!isSafeScriptFileName(finalFileName)) {
        Toast.makeText(context, localizedString(R.string.ui_enter_a_valid_script_file_name), Toast.LENGTH_SHORT).show()
        return
      }

      scope.launch(Dispatchers.IO) {
        try {
          if (mpvConfStorageLocation.isBlank()) {
            withContext(Dispatchers.Main) {
              Toast.makeText(context, localizedString(R.string.ui_no_storage_location_set), Toast.LENGTH_LONG).show()
            }
            return@launch
          }

          val tree = DocumentFile.fromTreeUri(context, mpvConfStorageLocation.toUri())
          if (tree == null) {
            withContext(Dispatchers.Main) {
              Toast.makeText(context, localizedString(R.string.ui_no_storage_location_set), Toast.LENGTH_LONG).show()
            }
            return@launch
          }

          // Try to find "scripts" subdirectory first (case-insensitive)
          val scriptsDir = tree.listFiles().firstOrNull {
              it.isDirectory && it.name?.equals("scripts", ignoreCase = true) == true
          } ?: tree

          val isRenaming = scriptName != null && scriptName != finalFileName
          val existing = scriptsDir.findFile(finalFileName)
          if (existing != null && (isNewScript || isRenaming)) {
            withContext(Dispatchers.Main) {
              Toast.makeText(context, localizedString(R.string.ui_1_s_already_exists, finalFileName), Toast.LENGTH_LONG).show()
            }
            return@launch
          }
          val scriptFile = existing ?: scriptsDir.createFile("text/plain", finalFileName) ?: run {
            withContext(Dispatchers.Main) {
              Toast.makeText(context, localizedString(R.string.ui_failed_to_create_file), Toast.LENGTH_LONG).show()
            }
            return@launch
          }

          try {
            if (scriptFile.name != finalFileName) {
              check(existing == null && scriptFile.renameTo(finalFileName) && scriptFile.name == finalFileName) {
                localizedString(R.string.ui_could_not_name_script, finalFileName)
              }
            }
            checkNotNull(context.contentResolver.openOutputStream(scriptFile.uri, "wt")) {
              localizedString(R.string.ui_failed_open_output_stream)
            }.use { out ->
              out.write(scriptContent.toByteArray(Charsets.UTF_8))
              out.flush()
            }
          } catch (e: Exception) {
            if (existing == null) scriptFile.delete()
            throw e
          }

          if (isRenaming) {
            val oldFile = scriptsDir.findFile(scriptName)
            if (oldFile != null && !oldFile.delete()) {
              Log.w("LuaScriptEditor", "Could not remove renamed script: $scriptName")
            }
            val selected = preferences.selectedLuaScripts.get()
            if (scriptName in selected) {
              preferences.selectedLuaScripts.set(selected - scriptName + finalFileName)
            }
          }

          withContext(Dispatchers.Main) {
            hasUnsavedChanges = false
            Toast.makeText(context, localizedString(R.string.ui_1_s_saved_successfully, finalFileName), Toast.LENGTH_SHORT).show()
            backStack.removeLastOrNull()
          }
        } catch (e: Exception) {
          withContext(Dispatchers.Main) {
            Toast.makeText(context, localizedString(R.string.ui_failed_to_save_1_s, e.message), Toast.LENGTH_LONG).show()
          }
        }
      }
    }

    fun shareScript() {
      if (isNewScript) {
        Toast.makeText(context, localizedString(R.string.ui_save_the_script_first_before_sharing), Toast.LENGTH_SHORT).show()
        return
      }

      scope.launch(Dispatchers.IO) {
        try {
          val tree = DocumentFile.fromTreeUri(context, mpvConfStorageLocation.toUri())
          if (tree != null && tree.exists()) {
            // Try to find "scripts" subdirectory first (case-insensitive)
            val scriptsDir = tree.listFiles().firstOrNull {
                it.isDirectory && it.name?.equals("scripts", ignoreCase = true) == true
            } ?: tree

            val scriptFile = checkNotNull(scriptsDir.findFile(scriptName)) { localizedString(R.string.ui_script_not_found) }
            if (scriptFile.exists()) {
              // Copy to cache directory for sharing
              val cacheFile = File(context.cacheDir, scriptName)
              checkNotNull(context.contentResolver.openInputStream(scriptFile.uri)) {
                localizedString(R.string.ui_could_not_read_script, scriptName)
              }.use { input ->
                cacheFile.outputStream().use { output ->
                  input.copyTo(output)
                }
              }

              // Get content URI using FileProvider
              val contentUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                cacheFile
              )

              withContext(Dispatchers.Main) {
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                  type = "text/plain"
                  putExtra(Intent.EXTRA_STREAM, contentUri)
                  putExtra(Intent.EXTRA_SUBJECT, scriptName)
                  addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(shareIntent, localizedString(R.string.ui_share_script_named, scriptName)))
              }
            } else {
              error(localizedString(R.string.ui_script_not_found))
            }
          } else {
            error(localizedString(R.string.ui_mpv_directory_unavailable))
          }
        } catch (e: Exception) {
          withContext(Dispatchers.Main) {
            Toast.makeText(
              context,
              localizedString(R.string.ui_failed_to_share_1_s, e.message),
              Toast.LENGTH_LONG
            ).show()
          }
        }
      }
    }

    fun deleteScript() {
      if (isNewScript) {
        backStack.removeLastOrNull()
        return
      }

      scope.launch(Dispatchers.IO) {
        try {
          val tree = DocumentFile.fromTreeUri(context, mpvConfStorageLocation.toUri())
          if (tree != null && tree.exists()) {
              // Try to find "scripts" subdirectory first (case-insensitive)
              val scriptsDir = tree.listFiles().firstOrNull {
                  it.isDirectory && it.name?.equals("scripts", ignoreCase = true) == true
              } ?: tree

              val scriptFile = checkNotNull(scriptsDir.findFile(scriptName)) { localizedString(R.string.ui_script_not_found) }
              if (scriptFile.exists()) {
                val deleted = scriptFile.delete()
                if (deleted) {
                  // Remove from selected scripts if it was selected
                  val selectedScripts = preferences.selectedLuaScripts.get()
                  if (selectedScripts.contains(scriptName)) {
                    preferences.selectedLuaScripts.set(selectedScripts - scriptName)
                  }

                  withContext(Dispatchers.Main) {
                    Toast.makeText(context, localizedString(R.string.ui_1_s_deleted, scriptName), Toast.LENGTH_SHORT).show()
                    backStack.removeLastOrNull()
                  }
                } else {
                  error(localizedString(R.string.ui_could_not_delete_script, scriptName))
                }
              } else {
                error(localizedString(R.string.ui_script_not_found))
              }
          } else {
            error(localizedString(R.string.ui_mpv_directory_unavailable))
          }
        } catch (e: Exception) {
          withContext(Dispatchers.Main) {
            Toast.makeText(
              context,
              localizedString(R.string.ui_failed_to_delete_1_s, e.message),
              Toast.LENGTH_LONG
            ).show()
          }
        }
      }
    }

    Column(
      modifier = Modifier.fillMaxSize()
    ) {
      // Fixed TopAppBar
      TopAppBar(
        title = {
          Column {
            androidx.compose.foundation.text.BasicTextField(
              value = fileName,
              onValueChange = {
                fileName = it
                hasUnsavedChanges = true
              },
              textStyle = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
              ),
              cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
              decorationBox = { innerTextField ->
                Box {
                  if (fileName.isEmpty()) {
                    Text(
                      text = localizedString(R.string.ui_script_name),
                      style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                      )
                    )
                  }
                  innerTextField()
                }
              }
            )
            if (hasUnsavedChanges) {
              Text(
                text = localizedString(R.string.ui_unsaved_changes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.secondary,
              )
            }
          }
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
          // Share button (only for existing scripts)
          if (!isNewScript) {
            IconButton(
              onClick = { shareScript() },
              modifier = Modifier
                .padding(horizontal = 4.dp)
                .size(40.dp),
              colors = IconButtonDefaults.iconButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
              ),
              shape = RoundedCornerShape(8.dp),
            ) {
              Icon(
                Icons.Default.Share,
                contentDescription = localizedString(R.string.generic_share),
              )
            }
          }

          // Delete button (only for existing scripts)
          if (!isNewScript) {
            IconButton(
              onClick = { showDeleteDialog = true },
              modifier = Modifier
                .padding(horizontal = 4.dp)
                .size(40.dp),
              colors = IconButtonDefaults.iconButtonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
              ),
              shape = RoundedCornerShape(8.dp),
            ) {
              Icon(
                Icons.Default.Delete,
                contentDescription = localizedString(R.string.delete),
              )
            }
          }

          // Save button
          IconButton(
            onClick = { saveScript() },
            enabled = hasUnsavedChanges && fileName.isNotBlank(),
            modifier = Modifier
              .padding(horizontal = 4.dp)
              .size(40.dp),
            colors = IconButtonDefaults.iconButtonColors(
              containerColor = if (hasUnsavedChanges && fileName.isNotBlank()) {
                MaterialTheme.colorScheme.primaryContainer
              } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f)
              },
              contentColor = if (hasUnsavedChanges && fileName.isNotBlank()) {
                MaterialTheme.colorScheme.onPrimaryContainer
              } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
              },
              disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.38f),
              disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            ),
            shape = RoundedCornerShape(8.dp),
          ) {
            Icon(
              Icons.Default.Check,
              contentDescription = localizedString(R.string.ui_save),
            )
          }
        },
      )

      // Editor content with IME padding
      val scrollState = rememberScrollState()
      Box(
        modifier = Modifier
          .fillMaxSize()
          .weight(1f)
          .imePadding()
      ) {
        BasicTextField(
          value = scriptContent,
          onValueChange = {
            scriptContent = it
            hasUnsavedChanges = true
          },
          modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 12.dp),
          textStyle = TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurface,
          ),
          cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        )
      }
    }

    // Delete confirmation dialog
    if (showDeleteDialog) {
      ConfirmDialog(
        title = localizedString(R.string.ui_delete_script),
        subtitle = localizedString(R.string.ui_delete_script_confirm, scriptName ?: fileName),
        onConfirm = {
          deleteScript()
          showDeleteDialog = false
        },
        onCancel = {
          showDeleteDialog = false
        },
      )
    }
  }
}
