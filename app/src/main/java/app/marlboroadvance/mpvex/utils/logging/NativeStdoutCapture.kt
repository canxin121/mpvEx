package app.marlboroadvance.mpvex.utils.logging

import android.os.ParcelFileDescriptor
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import android.util.Log
import java.io.FileDescriptor
import kotlin.concurrent.thread

private const val TAG = "stdout"
private const val READ_BUFFER_SIZE = 4096

/**
 * Routes everything native code writes to stdout/stderr into the log.
 *
 * libmpv and the MPV C plugins are loaded into this process and write their
 * diagnostics straight to file descriptors 1 and 2. Android does not forward
 * those to logcat, so without this the output is lost. Redirecting both
 * descriptors into a pipe and reading it on a dedicated thread makes plugin
 * output visible in the Logs screen and in the log file.
 *
 * The redirection is process-wide. It is only installed when the user asks for
 * file logging, and [stop] puts the original descriptors back.
 */
object NativeStdoutCapture {
  private var savedStdout: FileDescriptor? = null
  private var savedStderr: FileDescriptor? = null
  private var readEnd: ParcelFileDescriptor? = null

  /**
   * The write end, kept so [stop] can close the last reference to the pipe.
   *
   * Descriptors 1 and 2 are copies of it. Closing this one while they are still
   * redirected would change nothing, but after [stop] puts the originals back it
   * is the only reference left, and closing it is what gives the reader thread
   * its end of file.
   */
  private var writeEnd: ParcelFileDescriptor? = null

  @Volatile
  private var running = false

  val isRunning: Boolean get() = running

  /**
   * Install the redirection. Returns false when the platform refuses, in which
   * case logging carries on without native stdout capture.
   */
  fun start(): Boolean {
    if (running) return true
    return runCatching {
      // Copy the real streams aside first. Those copies take the lowest free
      // descriptors, which keeps the pipe below from landing on 0, 1 or 2.
      val originalOut = copyOfDescriptor(OsConstants.STDOUT_FILENO)
      val originalErr = copyOfDescriptor(OsConstants.STDERR_FILENO)
      savedStdout = originalOut
      savedStderr = originalErr

      val pipe = ParcelFileDescriptor.createPipe()
      val read = pipe[0]
      val write = pipe[1]
      if (read.fd <= OsConstants.STDERR_FILENO || write.fd <= OsConstants.STDERR_FILENO) {
        read.close()
        write.close()
        error("pipe landed on a standard descriptor")
      }

      Os.dup2(write.fileDescriptor, OsConstants.STDOUT_FILENO)
      Os.dup2(write.fileDescriptor, OsConstants.STDERR_FILENO)

      readEnd = read
      writeEnd = write
      running = true
      thread(name = "mpvex-native-stdout", isDaemon = true) { readLoop(read.fileDescriptor) }
      true
    }.getOrElse { error ->
      Log.w(MpvExLog.logcatTag(TAG), "Native stdout capture unavailable", error)
      // Nothing was installed, so put back anything that was copied aside; a
      // descriptor left dangling here would outlive the attempt.
      discardPendingRedirect()
      running = false
      false
    }
  }

  /** Release descriptors taken by a [start] that did not finish. */
  private fun discardPendingRedirect() {
    savedStdout?.let { saved ->
      runCatching { Os.dup2(saved, OsConstants.STDOUT_FILENO) }
      runCatching { Os.close(saved) }
    }
    savedStdout = null
    savedStderr?.let { saved ->
      runCatching { Os.dup2(saved, OsConstants.STDERR_FILENO) }
      runCatching { Os.close(saved) }
    }
    savedStderr = null
    runCatching { writeEnd?.close() }
    writeEnd = null
    runCatching { readEnd?.close() }
    readEnd = null
  }

  /**
   * Put the original stdout/stderr back and release everything.
   *
   * Restoring matters: leaving descriptor 1 pointed at a pipe nobody reads
   * would turn the next native write into a fatal SIGPIPE.
   */
  fun stop() {
    if (!running) return
    running = false
    savedStdout?.let { saved ->
      runCatching { Os.dup2(saved, OsConstants.STDOUT_FILENO) }
      runCatching { Os.close(saved) }
      savedStdout = null
    }
    savedStderr?.let { saved ->
      runCatching { Os.dup2(saved, OsConstants.STDERR_FILENO) }
      runCatching { Os.close(saved) }
      savedStderr = null
    }
    // Now that nothing points at the pipe any more, closing the write end
    // unblocks the reader thread with an end of file.
    runCatching { writeEnd?.close() }
    writeEnd = null
    runCatching { readEnd?.close() }
    readEnd = null
  }

  /**
   * A duplicate of the descriptor [fd], on a descriptor of its own.
   *
   * android.system.Os works on [FileDescriptor] objects, and a wrapper for a
   * standard descriptor can only be obtained from [ParcelFileDescriptor], which
   * would take ownership of descriptor 1 or 2 and close it when collected. The
   * wrapper is detached immediately after the copy is taken so it never owns
   * the original.
   */
  private fun copyOfDescriptor(fd: Int): FileDescriptor {
    val holder = ParcelFileDescriptor.fromFd(fd)
    return try {
      Os.dup(holder.fileDescriptor)
    } finally {
      holder.detachFd()
    }
  }

  private fun readLoop(descriptor: FileDescriptor) {
    val reader = NativeStreamLineReader { line -> MpvExLog.external(Log.VERBOSE, LogSource.NATIVE, TAG, line) }
    val buffer = ByteArray(READ_BUFFER_SIZE)
    while (running) {
      val count =
        try {
          Os.read(descriptor, buffer, 0, buffer.size)
        } catch (interrupted: ErrnoException) {
          if (interrupted.errno == OsConstants.EINTR) continue else break
        } catch (_: Throwable) {
          break
        }
      if (count <= 0) break
      reader.onBytes(buffer, count)
    }
    reader.flush()
  }
}
