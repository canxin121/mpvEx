package app.marlboroadvance.mpvex.presentation.crash

import android.content.Context
import android.content.Intent
import app.marlboroadvance.mpvex.utils.logging.CrashLog
import app.marlboroadvance.mpvex.utils.logging.MpvExLog
import kotlin.system.exitProcess

private const val TAG = "crash"

class GlobalExceptionHandler(
  private val context: Context,
  private val activity: Class<*>,
) : Thread.UncaughtExceptionHandler {
  override fun uncaughtException(
    t: Thread,
    e: Throwable,
  ) {
    // Written before anything else: the process is about to end and this is the
    // last chance to get the buffered log lines onto disk.
    val crashFile = CrashLog.record(context, e)
    MpvExLog.e(TAG, e, "Uncaught exception on thread %s", t.name)

    val intent = Intent(context, activity)
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
    intent.putExtra("exception", e.stackTraceToString())
    intent.putExtra("crash_log", crashFile?.absolutePath)
    context.startActivity(intent)
    exitProcess(0)
  }
}
