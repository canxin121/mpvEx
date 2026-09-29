package app.marlboroadvance.mpvex

import android.app.Application
import app.marlboroadvance.mpvex.database.repository.VideoMetadataCacheRepository
import app.marlboroadvance.mpvex.di.DatabaseModule
import app.marlboroadvance.mpvex.di.FileManagerModule
import app.marlboroadvance.mpvex.di.PreferencesModule
import app.marlboroadvance.mpvex.environment.MpvExEnvironment
import app.marlboroadvance.mpvex.environment.UserEnvironmentVariables
import app.marlboroadvance.mpvex.preferences.AdvancedPreferences
import app.marlboroadvance.mpvex.plugins.EnvironmentVariablesCodec
import app.marlboroadvance.mpvex.presentation.crash.CrashActivity
import app.marlboroadvance.mpvex.presentation.crash.GlobalExceptionHandler
import app.marlboroadvance.mpvex.utils.media.MediaLibraryEvents
import app.marlboroadvance.mpvex.utils.logging.LoggingSetup
import app.marlboroadvance.mpvex.utils.logging.MpvExLog
import `is`.xyz.mpv.FastThumbnails
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin
import org.koin.core.annotation.KoinExperimentalAPI

private const val TAG = "App"

@OptIn(KoinExperimentalAPI::class)
class App : Application() {
  companion object {
    lateinit var instance: App
      private set
  }

  private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  private val metadataCache: VideoMetadataCacheRepository by inject()
  private val advancedPreferences: AdvancedPreferences by inject()

  override fun onCreate() {
    super.onCreate()
    instance = this

    // Initialize Koin
    startKoin {
      androidContext(this@App)
      modules(
        PreferencesModule,
        DatabaseModule,
        FileManagerModule,
        app.marlboroadvance.mpvex.di.domainModule,
      )
    }

    // Logging needs the preferences, so it is installed right after Koin.
    LoggingSetup.install(filesDir, advancedPreferences, applicationScope)

    Thread.setDefaultUncaughtExceptionHandler(GlobalExceptionHandler(applicationContext, CrashActivity::class.java))

    // Native thumbnail code can load before a player is opened. Supply user
    // variables now; the player applies the same values again before mpv starts.
    val environmentResult = UserEnvironmentVariables.apply(
      userDefinedVariables = EnvironmentVariablesCodec.decode(advancedPreferences.environmentVariables.get()),
      previouslyManagedUserEnvironmentNames = advancedPreferences.managedUserEnvironmentNames.get(),
      builtInVariables = MpvExEnvironment.builtInValues(advancedPreferences.mpvConfStorageUri.get()),
    )
    advancedPreferences.managedUserEnvironmentNames.set(environmentResult.managedUserEnvironmentNames)
    environmentResult.warnings.forEach { warning -> MpvExLog.w(TAG, warning) }
    environmentResult.userVariableErrors.forEach { error -> MpvExLog.w(TAG, error.description) }
    MpvExLog.d(
      TAG,
      "User environment applied: %d variables managed",
      environmentResult.managedUserEnvironmentNames.size,
    )
    MpvExEnvironment.apply(advancedPreferences.mpvConfStorageUri.get())
    MpvExLog.d(TAG, "MPV environment applied")
    applicationScope.launch {
      advancedPreferences.mpvConfStorageUri.changes().collect(MpvExEnvironment::apply)
    }

    FastThumbnails.initialize(this)

    // Perform cache maintenance on app startup (non-blocking)
    applicationScope.launch {
      runCatching {
        metadataCache.performMaintenance()
      }
    }

    // Trigger media scan on app launch to detect new videos
    applicationScope.launch {
      runCatching {
        triggerMediaScanOnLaunch()
      }
    }
  }

  override fun onTerminate() {
    LoggingSetup.shutdown()
    super.onTerminate()
  }

  /**
   * Trigger a media scan on app launch to ensure MediaStore is up-to-date
   * This helps detect videos added by external apps while the app was closed
   */
  private fun triggerMediaScanOnLaunch() {
    try {
      val externalStorage = android.os.Environment.getExternalStorageDirectory()

      android.media.MediaScannerConnection.scanFile(
        this,
        arrayOf(externalStorage.absolutePath),
        null, // Let MediaScanner detect all media types
      ) { path, _ ->
        MpvExLog.d(TAG, "Launch media scan completed for: %s", path)
        // Notify the app that media library may have changed
        MediaLibraryEvents.notifyChanged()
      }

      MpvExLog.d(TAG, "Triggered media scan on app launch")
    } catch (e: Exception) {
      MpvExLog.e(TAG, e, "Failed to trigger media scan on launch")
    }
  }
}
