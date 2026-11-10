package com.fieldbook.tracker.utilities

import android.content.Context
import android.os.Build
import android.util.Log
import com.fieldbook.tracker.BuildConfig
import org.phenoapps.utils.BaseDocumentTreeUtil
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Streams this app's logcat to a timestamped text file in the root of the Field Book storage folder.
 * Also writes uncaught exception stack traces directly to the file before the process dies.
 */
object LogcatRecorder {

    private const val TAG = "LogcatRecorder"
    private const val FILE_PREFIX = "fb_log_"

    private val lock = Any()

    private var process: Process? = null
    private var readerThread: Thread? = null
    private var output: OutputStream? = null
    private var currentFileName: String? = null
    private var crashHandlerInstalled = false

    val isRunning: Boolean
        get() = synchronized(lock) { output != null }

    /**
     * Starts recording, or returns the current file name if already running.
     * @return the created file name, or null if the storage folder is unavailable or recording failed
     */
    fun start(context: Context): String? {
        synchronized(lock) {
            if (output != null) return currentFileName

            try {
                val root = BaseDocumentTreeUtil.getRoot(context)
                if (root == null || !root.exists()) return null

                val now = Date()
                val timestamp = SimpleDateFormat("yyyy-MM-dd-HH-mm-ss", Locale.US).format(now)
                val fileName = "$FILE_PREFIX$timestamp.txt"

                val file = root.createFile("text/plain", fileName) ?: return null
                val stream = context.contentResolver.openOutputStream(file.uri, "wa") ?: return null
                output = stream

                stream.write(header(now).toByteArray())
                stream.flush()

                val logcat = Runtime.getRuntime().exec(arrayOf("logcat", "-v", "threadtime"))

                process = logcat
                readerThread = Thread({ readLoop(logcat, stream) }, TAG).apply {
                    isDaemon = true
                    start()
                }

                installCrashHandler()

                currentFileName = file.name ?: fileName
                return currentFileName

            } catch (e: Exception) {
                Log.e(TAG, "Failed to start logcat recording.", e)
                stopLocked()
                return null
            }
        }
    }

    fun stop() {
        synchronized(lock) { stopLocked() }
    }

    private fun stopLocked() {
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
