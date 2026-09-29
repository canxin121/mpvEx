package app.marlboroadvance.mpvex.ui.preferences

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import app.marlboroadvance.mpvex.preferences.AdvancedPreferences
import app.marlboroadvance.mpvex.preferences.preference.collectAsState
import app.marlboroadvance.mpvex.plugins.CPluginCatalogScanner
import app.marlboroadvance.mpvex.plugins.CPluginConfigurationCodec
import app.marlboroadvance.mpvex.plugins.CPluginDescriptor
import app.marlboroadvance.mpvex.plugins.CPluginManifest
import app.marlboroadvance.mpvex.presentation.Screen
import app.marlboroadvance.mpvex.ui.utils.LocalBackStack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import org.koin.compose.koinInject

@Serializable
data class CPluginSettingsScreen(
  val pluginFileName: String,
) : Screen {
  @OptIn(ExperimentalMaterial3Api::class)
  @Composable
  override fun Content() {
    val context = LocalContext.current
    val backStack = LocalBackStack.current
    val preferences = koinInject<AdvancedPreferences>()
    val mpvDirectoryUri by preferences.mpvConfStorageUri.collectAsState()
    val configurationContent by preferences.cPluginConfiguration.collectAsState()
    var loadState by remember(pluginFileName) {
      mutableStateOf<CPluginSettingsLoadState>(CPluginSettingsLoadState.Loading)
    }

    LaunchedEffect(mpvDirectoryUri, pluginFileName) {
      loadState = CPluginSettingsLoadState.Loading
      loadState =
        withContext(Dispatchers.IO) {
          runCatching {
            require(mpvDirectoryUri.isNotBlank()) { "MPV configuration directory is not set" }
            val root = DocumentFile.fromTreeUri(context, mpvDirectoryUri.toUri())
              ?: error("MPV configuration directory is unavailable")
            require(root.exists() && root.canRead()) { "MPV configuration directory is not readable" }
            val descriptor = CPluginCatalogScanner.scan(context, root).firstOrNull {
              it.fileName == pluginFileName
            } ?: error("Plugin '$pluginFileName' was not found")
            CPluginSettingsLoadState.Loaded(descriptor)
          }.getOrElse { error ->
            CPluginSettingsLoadState.Error(error.message ?: error::class.java.simpleName)
          }
        }
    }

    val loadedDescriptor = (loadState as? CPluginSettingsLoadState.Loaded)?.descriptor
    val title = loadedDescriptor?.manifest?.name ?: pluginFileName

    Scaffold(
      topBar = {
        TopAppBar(
          title = { Text(title) },
          navigationIcon = {
            IconButton(onClick = backStack::removeLastOrNull) {
              Icon(Icons.AutoMirrored.Default.ArrowBack, contentDescription = null)
            }
          },
        )
      },
    ) { padding ->
      when (val state = loadState) {
        CPluginSettingsLoadState.Loading ->
          Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center,
          ) {
            CircularProgressIndicator()
          }
        is CPluginSettingsLoadState.Error ->
          CPluginSettingsMessage(
            title = "Plugin settings unavailable",
            message = state.message,
            modifier = Modifier.fillMaxSize().padding(padding),
          )
        is CPluginSettingsLoadState.Loaded -> {
          val descriptor = state.descriptor
          when {
            descriptor.manifestError != null ->
              CPluginSettingsMessage(
                title = "Invalid plugin manifest",
                message = descriptor.manifestError,
                modifier = Modifier.fillMaxSize().padding(padding),
              )
            descriptor.manifest == null ->
              CPluginSettingsMessage(
                title = "No configuration panel",
                message = "This legacy plugin does not provide a .mpvex.json manifest.",
                modifier = Modifier.fillMaxSize().padding(padding),
              )
            descriptor.manifest.config.isEmpty() ->
              CPluginSettingsMessage(
                title = descriptor.manifest.name,
                message = "This plugin does not declare any configurable fields.",
                modifier = Modifier.fillMaxSize().padding(padding),
              )
            else -> {
              val allConfiguration = remember(configurationContent) {
                CPluginConfigurationCodec.decode(configurationContent)
              }
              CPluginSettingsEditor(
                descriptor = descriptor,
                configuredValues = allConfiguration[descriptor.manifest.id].orEmpty(),
                onSave = { values ->
                  preferences.cPluginConfiguration.set(
                    CPluginConfigurationCodec.updatePlugin(
                      content = preferences.cPluginConfiguration.get(),
                      pluginId = descriptor.manifest.id,
                      pluginValues = values,
                    ),
                  )
                  Toast.makeText(
                    context,
                    "${descriptor.manifest.name} settings saved",
                    Toast.LENGTH_SHORT,
                  ).show()
                },
                modifier = Modifier.fillMaxSize().padding(padding),
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun CPluginSettingsEditor(
  descriptor: CPluginDescriptor,
  configuredValues: Map<String, String>,
  onSave: (Map<String, String>) -> Unit,
  modifier: Modifier = Modifier,
) {
  val manifest = requireNotNull(descriptor.manifest)
  val initialValues = remember(manifest, configuredValues) {
    createCPluginEditorValues(manifest, configuredValues)
  }
  var values by remember(manifest, configuredValues) { mutableStateOf(initialValues) }
  val hasErrors = hasCPluginConfigurationErrors(manifest, values)
  val hasChanges = values != initialValues

  Column(
    modifier = modifier.verticalScroll(rememberScrollState()).padding(16.dp),
    verticalArrangement = Arrangement.spacedBy(20.dp),
  ) {
    CPluginMetadataCard(descriptor)
    Text(
      text = "Settings in this panel belong only to ${manifest.name}.",
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.outline,
    )
    CPluginConfigurationFields(
      manifest = manifest,
      values = values,
      onValueChange = { fieldId, value -> values = values + (fieldId to value) },
      modifier = Modifier.fillMaxWidth(),
    )
    Button(
      onClick = { onSave(normalizeCPluginConfiguration(manifest, values)) },
      enabled = !hasErrors && hasChanges,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Text("Save ${manifest.name} settings")
    }
    Text(
      text = "Changes take effect the next time the player is opened.",
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.outline,
    )
  }
}

@Composable
private fun CPluginMetadataCard(descriptor: CPluginDescriptor) {
  val manifest = requireNotNull(descriptor.manifest)
  Card(modifier = Modifier.fillMaxWidth()) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(16.dp),
      verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      Text(manifest.name, fontWeight = FontWeight.Bold)
      Text(
        text = buildString {
          append(descriptor.fileName)
          manifest.version?.takeIf(String::isNotBlank)?.let { append(" • v$it") }
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.outline,
      )
      Text(
        text = manifest.id,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.outline,
      )
    }
  }
}

@Composable
private fun CPluginSettingsMessage(
  title: String,
  message: String,
  modifier: Modifier = Modifier,
) {
  Box(modifier = modifier, contentAlignment = Alignment.Center) {
    Column(
      modifier = Modifier.fillMaxWidth().padding(24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
      Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
      Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.outline,
      )
    }
  }
}

private sealed interface CPluginSettingsLoadState {
  data object Loading : CPluginSettingsLoadState

  data class Loaded(
    val descriptor: CPluginDescriptor,
  ) : CPluginSettingsLoadState

  data class Error(
    val message: String,
  ) : CPluginSettingsLoadState
}
