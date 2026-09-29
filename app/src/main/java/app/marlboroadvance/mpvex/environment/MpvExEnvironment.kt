package app.marlboroadvance.mpvex.environment

import android.os.Environment
import android.system.Os
import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import app.marlboroadvance.mpvex.utils.logging.MpvExLog

internal interface MpvExEnvironmentAccess {
  fun set(name: String, value: String)
  fun unset(name: String)
}

private object AndroidMpvExEnvironmentAccess : MpvExEnvironmentAccess {
  override fun set(name: String, value: String) = Os.setenv(name, value, true)
  override fun unset(name: String) = Os.unsetenv(name)
}

/** The user-selected configuration location, shared by settings and the process environment. */
object MpvExEnvironment {
  const val CONFIG_DIR = "MPVEX_CONFIG_DIR"
  val reservedNames = setOf(CONFIG_DIR)

  /** Resolve local primary storage to a path. Keep other providers as their original tree URI. */
  fun selectedConfigLocation(treeUri: String): String? =
    selectedConfigLocation(treeUri, Environment.getExternalStorageDirectory().absolutePath)

  internal fun selectedConfigLocation(treeUri: String, primaryStoragePath: String): String? {
    if (treeUri.isBlank() || '\u0000' in treeUri) return null
    val uri = runCatching { URI(treeUri) }.getOrNull() ?: return null
    if (uri.scheme != "content" || uri.host.isNullOrBlank()) return null
    if (uri.host != "com.android.externalstorage.documents") return treeUri

    val rawPath = uri.rawPath ?: return treeUri
    if (!rawPath.startsWith("/tree/")) return treeUri
    val rawTreeId = rawPath.removePrefix("/tree/")
    if ('/' in rawTreeId) return treeUri
    val documentId = runCatching {
      URLDecoder.decode(rawTreeId.replace("+", "%2B"), StandardCharsets.UTF_8.name())
    }.getOrNull() ?: return treeUri
    if (!documentId.startsWith("primary:")) return treeUri

    val relativePath = documentId.removePrefix("primary:")
    if ('\u0000' in relativePath || relativePath.startsWith('/') ||
      relativePath.split('/').any { it == "." || it == ".." }
    ) return treeUri
    val root = primaryStoragePath.trimEnd('/')
    return if (relativePath.isEmpty()) root else "$root/$relativePath"
  }

  /** Values of the built-in variables, which user-defined values may reference. */
  fun builtInValues(treeUri: String): Map<String, String> =
    builtInValues(treeUri, Environment.getExternalStorageDirectory().absolutePath)

  internal fun builtInValues(
    treeUri: String,
    primaryStoragePath: String,
  ): Map<String, String> =
    selectedConfigLocation(treeUri, primaryStoragePath)?.let { mapOf(CONFIG_DIR to it) }.orEmpty()

  /** Reapply after user and plugin settings so the reserved name stays accurate. */
  fun apply(treeUri: String) {
    applyValue(selectedConfigLocation(treeUri), AndroidMpvExEnvironmentAccess)
  }

  internal fun applyValue(value: String?, environment: MpvExEnvironmentAccess) {
    runCatching {
      if (value == null) environment.unset(CONFIG_DIR) else environment.set(CONFIG_DIR, value)
    }.onFailure { MpvExLog.w("MpvExEnvironment", it, "Could not update $CONFIG_DIR") }
  }
}
