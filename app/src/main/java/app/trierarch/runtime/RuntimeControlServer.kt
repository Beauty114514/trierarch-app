package app.trierarch.runtime

import android.content.Context
import android.net.LocalServerSocket
import android.net.LocalSocket
import android.os.Handler
import android.os.Looper
import android.util.Base64
import app.trierarch.core.Command
import app.trierarch.core.CommandError
import app.trierarch.core.CommandOutcome
import app.trierarch.core.Core
import java.io.Closeable
import java.io.File
import java.security.SecureRandom
import java.util.concurrent.CountDownLatch
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
    private val core: Core,
) : Closeable {
    private val endpointFile = File(context.filesDir, "runtime/control")
    private val socketName = "trierarch-control-${randomToken()}"
    private val token = randomToken()
    private val server = LocalServerSocket(socketName)
    private val clients = Executors.newCachedThreadPool()
    private val acceptThread = Thread(::acceptClients, "trierarch-runtime-control")
    private val main = Handler(Looper.getMainLooper())
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
            val request = ControlProtocol.parse(token, input.readLine())
            val outcome = when (request) {
                is ControlProtocol.Request.Status -> awaitStatus(request.id)
                is ControlProtocol.Request.Execute -> awaitCommand(request.command)
                null -> CommandOutcome.Failure(CommandError.Failed("invalid control request"))
            }
            val output = socket.outputStream.bufferedWriter()
            val kind = if (outcome is CommandOutcome.Success) "OK" else "ERR"
            val message = when (outcome) {
                is CommandOutcome.Success -> outcome.message
                is CommandOutcome.Failure -> outcome.error.message
            }
            output.write(kind)
            output.write('\t'.code)
            output.write(message)
            output.write('\n'.code)
            output.flush()
            // These commands reply first and act afterwards, so the CLI always
            // receives its answer before the app changes the display.
            val deferred = request is ControlProtocol.Request.Execute &&
                request.command.isDeferred()
            if (deferred && runCatching { input.readLine() == "ACK" }.getOrDefault(false)) {
                afterReply = (outcome as? CommandOutcome.Success)?.afterReply
            }
        }
        afterReply?.invoke()
    }

    private fun awaitStatus(id: String?): CommandOutcome {
        var result: CommandOutcome? = null
        val latch = CountDownLatch(1)
        main.post {
            result = core.status(id)
            latch.countDown()
        }
        return await(latch) { result }
    }

    private fun awaitCommand(command: Command): CommandOutcome {
        var result: CommandOutcome? = null
        val latch = CountDownLatch(1)
        main.post {
            core.submit(command) { outcome ->
                result = outcome
                latch.countDown()
            }
        }
        return await(latch) { result }
    }

    private fun await(latch: CountDownLatch, result: () -> CommandOutcome?): CommandOutcome {
        if (!latch.await(DISPATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            return CommandOutcome.Failure(CommandError.Failed("runtime control timed out"))
        }
        return checkNotNull(result())
    }

    private fun Command.isDeferred(): Boolean =
        this is Command.Run || this is Command.Stop || this is Command.Open

    private fun randomToken(): String {
        val bytes = ByteArray(18)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    private companion object {
        const val DISPATCH_TIMEOUT_SECONDS = 30L
    }
}

/**
 * The tab-separated, token-prefixed wire protocol spoken by the app's own
 * shell children. It lives outside the socket so the request surface can be
 * unit tested without Android.
 */
internal object ControlProtocol {
    sealed interface Request {
        data class Status(val id: String?) : Request
        data class Execute(val command: Command) : Request
    }

    fun parse(token: String, request: String?): Request? {
        val fields = request?.split('\t') ?: return null
        if (fields.size !in 2..4 || fields[0] != token) return null
        return when (fields[1]) {
            "status" if (fields.size == 2) -> Request.Status(null)
            "status" if (fields.size == 3) -> Request.Status(fields[2])
            "run" if (fields.size == 3) -> Request.Execute(Command.Run(fields[2]))
            "open" if (fields.size == 3) -> Request.Execute(Command.Open(fields[2]))
            "stop" if (fields.size == 2) -> Request.Execute(Command.Stop(null))
            "stop" if (fields.size == 3) -> Request.Execute(Command.Stop(fields[2]))
            "gui.open" if (fields.size == 2) -> Request.Execute(Command.OpenGui)
            else -> null
        }
    }
}
