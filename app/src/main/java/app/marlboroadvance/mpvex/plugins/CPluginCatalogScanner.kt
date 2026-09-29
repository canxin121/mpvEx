package app.marlboroadvance.mpvex.plugins

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import app.marlboroadvance.mpvex.R
import java.io.IOException

object CPluginCatalogScanner {
  private const val MAX_MANIFEST_CHARS = 256 * 1024

  fun scan(
    context: Context,
    root: DocumentFile,
  ): List<CPluginDescriptor> {
    val sourceDirectories = buildList {
      root.listFiles().firstOrNull {
        it.isDirectory && it.name?.equals("scripts", ignoreCase = true) == true
      }?.let(::add)
      add(root)
    }.distinctBy { it.uri }

    val descriptors = linkedMapOf<String, CPluginDescriptor>()
    sourceDirectories.forEach { directory ->
      val children = directory.listFiles().toList()
      val filesByLowercaseName = children.mapNotNull { file ->
        file.name?.lowercase()?.let { it to file }
      }.toMap()

      children.forEach { pluginFile ->
        val pluginFileName = pluginFile.name ?: return@forEach
        if (!pluginFile.isFile || !isSafeCPluginFileName(pluginFileName)) {
          return@forEach
        }
        if (descriptors.containsKey(pluginFileName)) return@forEach

        val exactSidecarName = "$pluginFileName.mpvex.json"
        val shortSidecarName = "${pluginFileName.substringBeforeLast('.')}.mpvex.json"
        val manifestFile =
          filesByLowercaseName[exactSidecarName.lowercase()]
            ?: filesByLowercaseName[shortSidecarName.lowercase()]

        if (manifestFile == null) {
          descriptors[pluginFileName] = CPluginDescriptor(fileName = pluginFileName)
          return@forEach
        }

        val manifestFileName = manifestFile.name ?: exactSidecarName
        descriptors[pluginFileName] =
          runCatching {
            val content = readManifest(context, manifestFile)
            val manifest = CPluginManifestParser.parse(content, pluginFileName)
            CPluginDescriptor(
              fileName = pluginFileName,
              manifest = manifest,
              manifestFileName = manifestFileName,
            )
          }.getOrElse { error ->
            CPluginDescriptor(
              fileName = pluginFileName,
              manifestFileName = manifestFileName,
              manifestError = error.message ?: error::class.java.simpleName,
            )
          }
      }
    }

    return descriptors.values.sortedBy { it.fileName.lowercase() }
  }

  private fun readManifest(
    context: Context,
    file: DocumentFile,
  ): String {
    val input = context.contentResolver.openInputStream(file.uri)
      ?: throw IOException(context.getString(R.string.plugin_could_not_open_manifest, file.name ?: context.getString(R.string.plugin_manifest)))
    return input.bufferedReader(Charsets.UTF_8).use { reader ->
      val content = CharArray(MAX_MANIFEST_CHARS + 1)
      var total = 0
      while (total < content.size) {
        val read = reader.read(content, total, content.size - total)
        if (read < 0) break
        total += read
      }
      if (total > MAX_MANIFEST_CHARS || reader.read() >= 0) {
        throw IOException(context.getString(R.string.plugin_manifest_too_large))
      }
      String(content, 0, total)
    }
  }
}
