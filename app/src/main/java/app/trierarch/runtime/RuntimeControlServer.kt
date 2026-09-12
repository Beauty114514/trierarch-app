package app.trierarch.runtime

import android.content.Context
import android.net.LocalServerSocket
import android.net.LocalSocket
import android.util.Base64
import java.io.Closeable
import java.io.File
import java.security.SecureRandom
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Private control endpoint for the app's own shell child processes.
 *
 * Android's LocalServerSocket uses an abstract Unix-domain name, so the native
 * CLI reads the name and an unguessable capability token from the app-private
 * endpoint file before connecting.
 */
class RuntimeControlServer(
    context: Context,
    private val dispatch: (RuntimeCommand, (RuntimeCommandResult) -> Unit) -> Unit,
) : Closeable {
    private val endpointFile = File(context.filesDir, "runtime/control")
    private val socketName = "trierarch-control-${randomToken()}"
    private val token = randomToken()
    private val server = LocalServerSocket(socketName)
    private val clients = Executors.newCachedThreadPool()
    private val acceptThread = Thread(::acceptClients, "trierarch-runtime-control")
    @Volatile private var closed = false

    init {
        endpointFile.parentFile?.mkdirs()
        endpointFile.writeText("$socketName\t$token\n")
        acceptThread.isDaemon = true
        acceptThread.start()
    }

    override fun close() {
        closed = true
        runCatching { server.close() }
        clients.shutdownNow()
        endpointFile.delete()
    }

    private fun acceptClients() {
        while (!closed) {
            val client = runCatching { server.accept() }.getOrElse { break }
            clients.execute { handle(client) }
        }
    }

    private fun handle(client: LocalSocket) {
        var afterReply: (() -> Unit)? = null
        client.use { socket ->
            val input = socket.inputStream.bufferedReader()
            val request = input.readLine()
            val command = parseRequest(request)
            val result = command?.let { awaitDispatch(it) }
                ?: RuntimeCommandResult(false, "invalid control request")
            val output = socket.outputStream.bufferedWriter()
            val kind = if (result.success) "OK" else "ERR"
            output.write(kind)
            output.write('\t'.code)
            output.write(result.message)
            output.write('\n'.code)
            output.flush()
            val lifecycleCommand = command is RuntimeCommand.Run ||
                command is RuntimeCommand.Stop || command is RuntimeCommand.Rerun
            if (lifecycleCommand && runCatching { input.readLine() == "ACK" }.getOrDefault(false)) {
                afterReply = result.afterReply
            }
        }
        afterReply?.invoke()
    }

    private fun parseRequest(request: String?): RuntimeCommand? {
        val fields = request?.split('\t') ?: return null
        if (fields.size !in 2..3 || fields[0] != token) return null
        return when (fields[1]) {
            "status" if (fields.size == 2) -> RuntimeCommand.Status(null)
            "status" if (fields.size == 3) -> RuntimeCommand.Status(fields[2])
            "run" if (fields.size == 3) -> RuntimeCommand.Run(fields[2])
            "stop" if (fields.size == 2) -> RuntimeCommand.Stop(null)
            "stop" if (fields.size == 3) -> RuntimeCommand.Stop(fields[2])
            "rerun" if (fields.size == 3) -> RuntimeCommand.Rerun(fields[2])
            else -> null
        }
    }

    private fun awaitDispatch(command: RuntimeCommand): RuntimeCommandResult {
        var result: RuntimeCommandResult? = null
        val completion = java.util.concurrent.CountDownLatch(1)
        dispatch(command) {
            result = it
            completion.countDown()
        }
        if (!completion.await(DISPATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            return RuntimeCommandResult(false, "runtime control timed out")
        }
        return checkNotNull(result)
    }

    private fun randomToken(): String {
        val bytes = ByteArray(18)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    private companion object {
        const val DISPATCH_TIMEOUT_SECONDS = 30L
    }
}
