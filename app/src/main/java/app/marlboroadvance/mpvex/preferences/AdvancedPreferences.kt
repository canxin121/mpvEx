package app.marlboroadvance.mpvex.preferences

import app.marlboroadvance.mpvex.BuildConfig
import app.marlboroadvance.mpvex.preferences.preference.PreferenceStore

class AdvancedPreferences(
  preferenceStore: PreferenceStore,
) {
  val mpvConfStorageUri = preferenceStore.getString("mpv_conf_storage_location_uri")
  val mpvConf = preferenceStore.getString("mpv.conf")
  val inputConf = preferenceStore.getString("input.conf")
  val environmentVariables = preferenceStore.getString("environment_variables", "{}")
  val managedUserEnvironmentNames =
    preferenceStore.getStringSet("managed_user_environment_names", emptySet())
  val enableLuaScripts = preferenceStore.getBoolean("enable_lua_scripts", false)
  val selectedLuaScripts = preferenceStore.getStringSet("selected_lua_scripts", emptySet())

  val verboseLogging = preferenceStore.getBoolean("verbose_logging", BuildConfig.BUILD_TYPE != "release")

  /**
   * Keeps a rolling log file under `filesDir/logs`, on by default in every build
   * type so a problem reported by a release user still comes with a log.
   */
  val enableFileLogging = preferenceStore.getBoolean("enable_file_logging", true)

  /** Also capture what native code and the MPV C plugins write to stdout/stderr. */
  val captureNativeOutput = preferenceStore.getBoolean("capture_native_output", true)

  val logFileMaxSizeKb = preferenceStore.getInt("log_file_max_size_kb", 2048)
  val logFileMaxCount = preferenceStore.getInt("log_file_max_count", 5)
  val logRetentionDays = preferenceStore.getInt("log_retention_days", 7)

  val enabledStatisticsPage = preferenceStore.getInt("enabled_stats_page", 0)

  val enableRecentlyPlayed = preferenceStore.getBoolean("enable_recently_played", true)

  val enableCPlugins = preferenceStore.getBoolean("enable_c_plugins", false)
  val selectedCPlugins = preferenceStore.getStringSet("selected_c_plugins", emptySet())

}
