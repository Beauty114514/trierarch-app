package app.trierarch.input

import android.content.Context
import android.util.Log
import app.trierarch.wayland.WaylandBridge
import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.EOFException
import java.io.IOException

/**
 * Runs the root evdev reader while a graphical Wayland surface is visible.
 *
 * Android's input policy processes Meta/Super before an ordinary View can see
 * it. The helper takes an exclusive evdev grab and sends raw key events back to
 * the app; those events are forwarded directly to the Wayland host.
 */
class RootKeyboardCapture(private val context: Context) {
    private val lock = Any()
    private var process: Process? = null
    private var readerThread: Thread? = null
    private var errorThread: Thread? = null

    /** Starts the helper and returns false when root execution is unavailable. */
    fun start(): Boolean = synchronized(lock) {
        if (process?.isAlive == true) return true
        stopLocked()
        return try {
            val executable = RootInputReaderRuntime.executable(context)
            val command = "exec ${shellQuote(executable.absolutePath)} " +
                "--owner-pid ${android.os.Process.myPid()} --verbose"
            val started = ProcessBuilder("su", "-c", command).start()
            process = started
            errorThread = Thread(
                { drainErrors(started) },
                "trierarch-input-stderr",
            ).apply {
                isDaemon = true
                start()
            }
            readerThread = Thread(
                { readFrames(started) },
                "trierarch-input-reader",
            ).apply {
                isDaemon = true
                start()
            }
            Log.i(TAG, "Root input reader started")
            true
        } catch (error: Exception) {
            Log.w(TAG, "Unable to start root input reader", error)
            stopLocked()
            false
        }
    }

    fun stop() = synchronized(lock) { stopLocked() }

    private fun stopLocked() {
        val running = process ?: return
        process = null
        // The helper polls stdin for EOF. Closing this pipe is the clean
        // shutdown path; destroy() is only a fallback for a wedged helper.
        try {
            running.outputStream.close()
        } catch (_: IOException) {
        }
        running.destroy()
        readerThread?.let { thread ->
            thread.join(700)
            if (thread.isAlive) thread.interrupt()
        }
        if (running.isAlive) running.destroyForcibly()
        errorThread?.interrupt()
        readerThread = null
        errorThread = null
        Log.i(TAG, "Root input reader stopped")
    }

    private fun readFrames(running: Process) {
        try {
            DataInputStream(BufferedInputStream(running.inputStream)).use { input ->
                while (true) {
                    val magic = try {
                        input.readLittleInt()
                    } catch (_: EOFException) {
                        break
                    }
                    val key = input.readLittleInt()
                    val value = input.readLittleInt()
                    val seconds = input.readLittleInt()
                    val micros = input.readLittleInt()
                    if (magic != FRAME_MAGIC) {
                        Log.w(TAG, "Unexpected root input frame: 0x${magic.toString(16)}")
                        break
                    }
                    if (key <= 0) continue
                    val timeMillis = (seconds.toLong() * 1000L + micros / 1000L).toInt()
                    WaylandBridge.setKeyboardEvdevKey(
                        evdevKey = key,
                        pressed = value != 0,
                        timeMillis = timeMillis,
                    )
                }
            }
        } catch (error: IOException) {
            if (process?.isAlive == true) {
                Log.w(TAG, "Root input reader pipe closed", error)
            }
        }
    }

    private fun drainErrors(running: Process) {
        try {
            running.errorStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    Log.d(TAG, "helper: $line")
                }
            }
        } catch (_: IOException) {
            // The process exited; its status is handled by readFrames/stop.
        }
    }

    private fun DataInputStream.readLittleInt(): Int {
        var result = 0
        repeat(4) { byte ->
            result = result or (readUnsignedByte() shl (byte * 8))
        }
        return result
    }

    private fun shellQuote(value: String): String =
        "'" + value.replace("'", "'\\''") + "'"

    private companion object {
        const val TAG = "TrierarchRootInput"
        const val FRAME_MAGIC = 0x54524b42
    }
}
