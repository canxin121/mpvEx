package app.marlboroadvance.mpvex.utils.logging

import app.marlboroadvance.mpvex.preferences.AdvancedPreferences
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import timber.log.Timber

private const val TAG = "logging"

/**
 * Brings the logging stack up and keeps it following the user's preferences.
 *
 * Everything here is best effort: a log file that cannot be opened, or a pipe
 * the platform refuses, disables that one destination and leaves the rest of
 * the app untouched.
 */
object LoggingSetup {
  private var fileSink: LogFileSink? = null
  private var started = false

  val mpvBridge = MpvLogBridge()

  /** True while log lines are being written to a file. */
  val isWritingToFile: Boolean get() = fileSink != null

  /** The file the app is appending to, when file logging is on. */
  fun currentFile(): File? = fileSink?.currentFile()

  fun install(
    filesDir: File,
    preferences: AdvancedPreferences,
    scope: CoroutineScope,
  ) {
    if (started) return
    started = true

    // Trees first: the setup below logs as it runs, and that output belongs in
    // the same places as everything else.
    Timber.plant(MpvExLogcatTree(), MpvExLogDestinationTree())

    applyLevel(preferences.verboseLogging.get())
    applyFileLogging(filesDir, preferences)
    applyNativeCapture(preferences)

    scope.launch {
      preferences.verboseLogging.changes().collect { verbose ->
        applyLevel(verbose)
        MpvExLog.i(TAG, "Verbose logging %s", if (verbose) "enabled" else "disabled")
      }
    }
    scope.launch {
      preferences.enableFileLogging.changes().collect {
        applyFileLogging(filesDir, preferences)
        applyNativeCapture(preferences)
      }
    }
    scope.launch {
      preferences.captureNativeOutput.changes().collect { applyNativeCapture(preferences) }
    }

    MpvExLog.i(
      TAG,
      "Logging ready: level=%s file=%s directory=%s",
      if (preferences.verboseLogging.get()) "verbose" else "normal",
      if (isWritingToFile) "on" else "off",
      LogFiles.directoryOf(filesDir).absolutePath,
    )
  }

  /**
   * Stop writing and hand back the process's stdout/stderr. Called from
   * [android.app.Application.onTerminate], which only runs on emulators and in
   * tests, but it keeps the state honest.
   */
  fun shutdown() {
    if (!started) return
    started = false
    Timber.uprootAll()
    mpvBridge.stop()
    NativeStdoutCapture.stop()
    fileSink?.flush()
    fileSink?.close()
    fileSink = null
    LogFiles.install(null, null)
    LogDestinations.install(emptyList())
  }

  private fun applyLevel(verbose: Boolean) {
    LogLevel.setVerbose(verbose)
  }

  private fun applyFileLogging(
    filesDir: File,
    preferences: AdvancedPreferences,
  ) {
    val enabled = preferences.enableFileLogging.get()
    if (!enabled) {
      fileSink?.flush()
      fileSink?.close()
      fileSink = null
      LogFiles.install(null, null)
      installDestinations()
      return
    }
    if (fileSink != null) return

    val directory = File(filesDir, "logs")
    val sink =
      runCatching {
        LogRetention.cleanup(directory, preferences.logRetentionDays.get())
        LogFileSink(
          directory = directory,
          baseName = LogFiles.APP_LOG_NAME,
          maxSizeBytes = preferences.logFileMaxSizeKb.get().toLong() * 1024,
          maxFileCount = preferences.logFileMaxCount.get(),
        )
      }.getOrElse { error ->
        MpvExLog.w(TAG, error, "Could not open %s", directory.absolutePath)
        null
      }
    fileSink = sink
    LogFiles.install(sink, if (sink == null) null else directory)
    installDestinations()
  }

  private fun applyNativeCapture(preferences: AdvancedPreferences) {
    val wanted = preferences.enableFileLogging.get() && preferences.captureNativeOutput.get()
    if (wanted) {
      if (NativeStdoutCapture.start()) {
        MpvExLog.i(TAG, "Capturing native stdout and stderr")
      }
    } else if (NativeStdoutCapture.isRunning) {
      NativeStdoutCapture.stop()
      MpvExLog.i(TAG, "Native stdout capture stopped")
    }
  }

  private fun installDestinations() {
    val buffer: (LogRecord) -> Unit = InMemoryLogBuffer::append
    val sink = fileSink
    LogDestinations.install(if (sink == null) listOf(buffer) else listOf(buffer, sink::offer))
  }
}

/**
 * Packs every log file into a single archive that can be shared.
 *
 * The archive lands in the cache directory, which the file provider already
 * exposes, so the share sheet can hand it to another app.
 */
fun exportLogsToZip(
  filesDir: File,
  destinationDirectory: File,
  now: Long = System.currentTimeMillis(),
): File? {
  val source = LogFiles.directoryOf(filesDir)
  val files = source.listFiles()?.filter { it.isFile }?.sortedBy { it.name }.orEmpty()
  if (files.isEmpty()) return null

  if (!destinationDirectory.exists()) destinationDirectory.mkdirs()
  val target = File(destinationDirectory, "mpvex-logs-$now.zip")
  return runCatching {
    ZipOutputStream(FileOutputStream(target)).use { zip ->
      files.forEach { file ->
        runCatching {
          zip.putNextEntry(ZipEntry(file.name))
          FileInputStream(file).use { input -> input.copyTo(zip) }
          zip.closeEntry()
        }
      }
    }
    target
  }.getOrElse { error ->
    MpvExLog.w(TAG, error, "Could not build the log archive")
    target.delete()
    null
  }
}
