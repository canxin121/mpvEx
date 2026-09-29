package app.marlboroadvance.mpvex.ui.preferences

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import app.marlboroadvance.mpvex.BuildConfig
import app.marlboroadvance.mpvex.R
import app.marlboroadvance.mpvex.utils.logging.LogFiles
import app.marlboroadvance.mpvex.utils.logging.MpvExLog
import app.marlboroadvance.mpvex.utils.logging.exportLogsToZip
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "LogsScreen"

/** Copy the given log text to the clipboard, with feedback. */
@Suppress("DEPRECATION")
fun copyTextToClipboard(
  context: Context,
  text: String,
) {
  val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
  clipboard.setPrimaryClip(ClipData.newPlainText(null, text))
}

/** Share the log lines currently on screen as a text file. */
fun shareVisibleLogs(
  context: Context,
  scope: CoroutineScope,
  text: String,
) {
  if (text.isBlank()) return
  scope.launch {
    withContext(Dispatchers.IO) {
      runCatching {
        val file = File(context.cacheDir, "mpvex-visible-logs.txt")
        file.writeText(text)
        file
      }
    }.onSuccess { file -> shareFile(context, file, "text/plain") }
      .onFailure { error -> reportFailure(context, R.string.logs_screen_export_failed_1_s, error) }
  }
}

/** Zip every file in the log directory and offer it through the share sheet. */
fun exportLogFiles(
  context: Context,
  scope: CoroutineScope,
) {
  scope.launch {
    val archive =
      withContext(Dispatchers.IO) {
        exportLogsToZip(context.filesDir, context.cacheDir)
      }
    if (archive == null) {
      Toast.makeText(
        context,
        context.getString(R.string.logs_screen_file_logging_disabled),
        Toast.LENGTH_SHORT,
      ).show()
      return@launch
    }
    shareFile(context, archive, "application/zip")
  }
}

private fun shareFile(
  context: Context,
  file: File,
  mimeType: String,
) {
  runCatching {
    val uri = FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.provider", file)
    val intent =
      Intent(Intent.ACTION_SEND).apply {
        type = mimeType
        putExtra(Intent.EXTRA_STREAM, uri)
        putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.logs_screen_title))
        clipData = ClipData.newRawUri(null, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }
    context.startActivity(
      Intent.createChooser(intent, context.getString(R.string.logs_screen_share)),
    )
  }.onFailure { error -> reportFailure(context, R.string.logs_screen_export_failed_1_s, error) }
}

private fun reportFailure(
  context: Context,
  messageRes: Int,
  error: Throwable,
) {
  MpvExLog.w(TAG, error, "Sharing logs failed")
  Toast.makeText(
    context,
    context.getString(messageRes, error.message ?: error::class.java.simpleName),
    Toast.LENGTH_LONG,
  ).show()
}

/** The directory the app writes its logs to, used by the crash screen. */
fun logDirectoryOf(context: Context): File = LogFiles.directoryOf(context.filesDir)
