package app.marlboroadvance.mpvex.environment

import android.content.Context
import android.system.Os
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal interface MpvExEnvironmentAccess {
  fun set(name: String, value: String)
  fun unset(name: String)
}

private object AndroidMpvExEnvironmentAccess : MpvExEnvironmentAccess {
  override fun set(name: String, value: String) = Os.setenv(name, value, true)
  override fun unset(name: String) = Os.unsetenv(name)
}

/** Process variables supplied by mpvEx independently of user and plugin settings. */
object MpvExEnvironment {
  const val APP_DIR = "MPVEX_APP_DIR"
  const val CONFIG_DIR = "MPVEX_CONFIG_DIR"
  const val CACHE_DIR = "MPVEX_CACHE_DIR"
  const val MEDIA_PATH = "MPVEX_MEDIA_PATH"

  val reservedNames = setOf(APP_DIR, CONFIG_DIR, CACHE_DIR, MEDIA_PATH)

  private val currentMediaPath = MutableStateFlow<String?>(null)
  val mediaPath = currentMediaPath.asStateFlow()

  fun staticValues(context: Context): Map<String, String> =
    staticValues(
      appDir = context.applicationInfo.dataDir,
      configDir = context.filesDir.absolutePath,
      cacheDir = context.cacheDir.absolutePath,
    )

  internal fun staticValues(
    appDir: String,
    configDir: String,
    cacheDir: String,
  ): Map<String, String> =
    linkedMapOf(APP_DIR to appDir, CONFIG_DIR to configDir, CACHE_DIR to cacheDir)

  /** Reapply after user and plugin settings so reserved names keep their actual values. */
  fun apply(context: Context) {
    applyValues(staticValues(context), currentMediaPath.value, AndroidMpvExEnvironmentAccess)
  }

  /** A path, content URI, or network URL; unset when no media is loaded. */
  fun setMediaPath(path: String?) {
    val normalized = normalizeMediaPath(path)
    currentMediaPath.value = normalized
    applyMediaPath(normalized, AndroidMpvExEnvironmentAccess)
  }

  internal fun applyValues(
    staticValues: Map<String, String>,
    mediaPath: String?,
    environment: MpvExEnvironmentAccess,
  ) {
    staticValues.forEach { (name, value) -> set(name, value, environment) }
    applyMediaPath(normalizeMediaPath(mediaPath), environment)
  }

  internal fun normalizeMediaPath(path: String?): String? =
    path?.takeIf { it.isNotBlank() && '\u0000' !in it }

  private fun applyMediaPath(path: String?, environment: MpvExEnvironmentAccess) {
    if (path == null) {
      runCatching { environment.unset(MEDIA_PATH) }
        .onFailure { Log.w("MpvExEnvironment", "Could not clear $MEDIA_PATH", it) }
    } else {
      set(MEDIA_PATH, path, environment)
    }
  }

  private fun set(name: String, value: String, environment: MpvExEnvironmentAccess) {
    runCatching { environment.set(name, value) }
      .onFailure { Log.w("MpvExEnvironment", "Could not set $name", it) }
  }
}
