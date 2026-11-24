package com.fieldbook.tracker.utilities

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import androidx.preference.PreferenceManager
import com.fieldbook.tracker.BuildConfig
import com.fieldbook.tracker.R
import com.fieldbook.tracker.preferences.GeneralKeys
import org.phenoapps.utils.BaseDocumentTreeUtil
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Streams this app's logcat to a timestamped text file in the root of the Field Book storage folder
 * for a limited time, then turns itself off.
 * Also writes uncaught exception stack traces directly to the file before the process dies.
 *
 * The session (end time and file) is persisted, so if the app is relaunched before the session
 * expires, recording continues in the same file instead of creating a new one.
 */
object LogcatRecorder {

    private const val TAG = "LogcatRecorder"
    private const val FILE_PREFIX = "fb_log_"

    /** Duration preference value meaning recording is off. */
    const val OFF = "0"

    private val lock = Any()
    private val handler by lazy { Handler(Looper.getMainLooper()) }
    private val timeoutRunnable = Runnable { checkTimeout() }

    private var appContext: Context? = null
    private var process: Process? = null
    private var readerThread: Thread? = null
    private var output: OutputStream? = null
    private var currentFileName: String? = null
    private var crashHandlerInstalled = false

    /** Wall-clock time (ms) when the current session ends. */
    @Volatile
    var endTime: Long = 0L
        private set

    val isRunning: Boolean
        get() = synchronized(lock) { output != null }

    /**
     * Starts a session lasting [minutes]. If a session is already running, its end time is reset
     * to [minutes] from now and it keeps writing to the same file.
     * @return the file name, or null if the storage folder is unavailable or recording failed
     */
    fun start(context: Context, minutes: Int): String? {
        val app = context.applicationContext
        synchronized(lock) {
            val name = if (output != null) currentFileName else openNewLocked(app)
            if (name == null) {
                clearSessionLocked(app)
                return null
            }
            val end = System.currentTimeMillis() + minutes * 60_000L
            prefs(app).edit()
                .putString(GeneralKeys.LOGCAT_CAPTURE_DURATION, minutes.toString())
                .putLong(GeneralKeys.LOGCAT_CAPTURE_END_TIME, end)
                .apply()
            scheduleTimeoutLocked(end)
            return name
        }
    }

    /**
     * Called at app launch. Continues an unexpired session in its existing file,
     * otherwise clears any stale session.
     */
    fun resume(context: Context) {
        val app = context.applicationContext
        val prefs = prefs(app)
        if (prefs.getString(GeneralKeys.LOGCAT_CAPTURE_DURATION, OFF) == OFF) return

        synchronized(lock) {
            if (output != null) return

            val end = prefs.getLong(GeneralKeys.LOGCAT_CAPTURE_END_TIME, 0L)
            if (end <= System.currentTimeMillis()) {
                clearSessionLocked(app)
                return
            }

            val uri = prefs.getString(GeneralKeys.LOGCAT_CAPTURE_FILE_URI, null)?.let(Uri::parse)
            val name = uri?.let { reopenLocked(app, it) } ?: openNewLocked(app)
            if (name == null) {
                clearSessionLocked(app)
                return
            }
            scheduleTimeoutLocked(end)
        }
    }

    /** Stops recording and ends the session. */
    fun stop(context: Context) {
        synchronized(lock) {
            stopLocked()
            clearSessionLocked(context.applicationContext)
        }
    }

    private fun prefs(context: Context): SharedPreferences =
        PreferenceManager.getDefaultSharedPreferences(context)

    private fun clearSessionLocked(app: Context) {
        endTime = 0L
        prefs(app).edit()
            .putString(GeneralKeys.LOGCAT_CAPTURE_DURATION, OFF)
            .remove(GeneralKeys.LOGCAT_CAPTURE_END_TIME)
            .remove(GeneralKeys.LOGCAT_CAPTURE_FILE_URI)
            .apply()
    }

    private fun scheduleTimeoutLocked(end: Long) {
        endTime = end
        handler.removeCallbacks(timeoutRunnable)
        handler.postDelayed(timeoutRunnable, (end - System.currentTimeMillis()).coerceAtLeast(0L))
    }

    /** Runs on the main thread when the timer fires; ends the session if it has expired. */
    private fun checkTimeout() {
        synchronized(lock) {
            if (output == null) return
            val remaining = endTime - System.currentTimeMillis()
            if (remaining > 0) {
                handler.postDelayed(timeoutRunnable, remaining)
            } else {
                expireLocked()
            }
        }
    }

    private fun expireLocked() {
        val app = appContext ?: return
        stopLocked()
        clearSessionLocked(app)
        handler.post {
            Toast.makeText(app, R.string.pref_debug_logcat_stopped, Toast.LENGTH_SHORT).show()
        }
    }

    /** Creates a new log file and starts recording into it. */
    private fun openNewLocked(app: Context): String? {
        val root = BaseDocumentTreeUtil.getRoot(app)
        if (root == null || !root.exists()) return null

        val now = Date()
        val timestamp = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US).format(now)
        val fileName = "$FILE_PREFIX$timestamp.txt"

        val file = root.createFile("text/plain", fileName) ?: return null
        val name = file.name ?: fileName

        if (!beginLocked(app, file.uri, name, header(now), null)) return null

        prefs(app).edit()
            .putString(GeneralKeys.LOGCAT_CAPTURE_FILE_URI, file.uri.toString())
            .apply()

        return name
    }

    /** Reopens an existing session's log file and appends to it. */
    private fun reopenLocked(app: Context, uri: Uri): String? {
        val file = try {
            DocumentFile.fromSingleUri(app, uri)?.takeIf { it.exists() }
        } catch (e: Exception) {
            null
        } ?: return null

        val name = file.name ?: uri.lastPathSegment ?: return null
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSZ", Locale.US).format(Date())
        val marker = "\n--- App restarted: $time ---\n"

        // only read logs from this process onward, previous lines are already in the file
        return if (beginLocked(app, uri, name, marker, processStartTime())) name else null
    }

    /**
     * Opens [uri] for appending, writes [preamble], and starts streaming logcat into it.
     * @param since if not null, only logs at or after this wall-clock time (ms) are read
     */
    private fun beginLocked(app: Context, uri: Uri, name: String, preamble: String, since: Long?): Boolean {
        try {
            val stream = app.contentResolver.openOutputStream(uri, "wa") ?: return false
            output = stream

            stream.write(preamble.toByteArray())
            stream.flush()

            val command = mutableListOf("logcat", "-v", "threadtime")
            if (since != null) {
                command += "-T"
                command += SimpleDateFormat("MM-dd HH:mm:ss.SSS", Locale.US).format(Date(since))
            }
            val logcat = Runtime.getRuntime().exec(command.toTypedArray())

            process = logcat
            readerThread = Thread({ readLoop(logcat, stream) }, TAG).apply {
                isDaemon = true
                start()
            }

            installCrashHandler()

            appContext = app
            currentFileName = name
            return true

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start logcat recording.", e)
            stopLocked()
            return false
        }
    }

    private fun processStartTime(): Long =
        System.currentTimeMillis() - (SystemClock.elapsedRealtime() - android.os.Process.getStartElapsedRealtime())

    private fun stopLocked() {
        handler.removeCallbacks(timeoutRunnable)
        try {
            process?.destroy()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop logcat process.", e)
        }
        readerThread?.interrupt()
        try {
            output?.flush()
            output?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to close log file.", e)
        }
        process = null
        readerThread = null
        output = null
        currentFileName = null
    }

    private fun readLoop(logcat: Process, stream: OutputStream) {
        try {
            BufferedReader(InputStreamReader(logcat.inputStream)).use { reader ->
                while (!Thread.currentThread().isInterrupted) {
                    val line = reader.readLine() ?: break
                    synchronized(lock) {
                        // stopped or restarted with a different file
                        if (output !== stream) return
                        // backstop for the timer, which does not advance while the device sleeps
                        if (System.currentTimeMillis() >= endTime) {
                            expireLocked()
                            return
                        }
                        stream.write(line.toByteArray())
                        stream.write('\n'.code)
                        if (!reader.ready()) stream.flush()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Logcat recording ended.", e)
        }
    }

    private fun installCrashHandler() {
        if (crashHandlerInstalled) return
        crashHandlerInstalled = true

        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                synchronized(lock) {
                    output?.let { stream ->
                        val trace = "\n--- Uncaught exception in thread ${thread.name} ---\n" +
                                Log.getStackTraceString(throwable) + "\n"
                        stream.write(trace.toByteArray())
                        stream.flush()
                    }
                }
            } catch (_: Exception) {
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    private fun header(now: Date): String {
        val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSZ", Locale.US).format(now)
        return "Field Book debug log\n" +
                "Started: $time\n" +
                "Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n" +
                "Device: ${Build.MANUFACTURER} ${Build.MODEL}\n" +
                "Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})\n" +
                "----------------------------------------\n"
    }
}
