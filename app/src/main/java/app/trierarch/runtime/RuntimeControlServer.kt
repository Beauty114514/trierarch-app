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
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.io.OutputStream
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
            val input = socket.inputStream
            val payload = readFrame(input) ?: return
            val request = ControlProtocol.parse(token, payload)
            val reply = when (request) {
                is ControlProtocol.Request.Status -> statusReply(request.id)
                ControlProtocol.Request.ListDocuments -> listReply()
                is ControlProtocol.Request.ReadDocument -> readReply(request.id)
                is ControlProtocol.Request.Execute -> Reply(awaitCommand(request.command))
                null -> Reply(CommandOutcome.Failure(CommandError.Failed("invalid control request")))
            }
            writeFrame(socket.outputStream, encode(reply))
            // These commands reply first and act afterwards, so the CLI always
            // receives its answer before the app changes the display.
            val deferred = request is ControlProtocol.Request.Execute &&
                request.command.isDeferred()
            if (deferred && runCatching { readLine(input) == "ACK" }.getOrDefault(false)) {
                afterReply = (reply.outcome as? CommandOutcome.Success)?.afterReply
            }
        }
        afterReply?.invoke()
    }

    private fun statusReply(id: String?): Reply {
        var outcome: CommandOutcome? = null
        val latch = CountDownLatch(1)
        main.post {
            outcome = core.status(id)
            latch.countDown()
        }
        if (!latch.await(DISPATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)) return timeout()
        return Reply(checkNotNull(outcome))
    }

    private fun listReply(): Reply = runCatching {
        val documents = core.documents()
        Reply(CommandOutcome.Success(documents.joinToString("\n") { it.id }))
    }.getOrElse {
        Reply(CommandOutcome.Failure(CommandError.Failed(it.message ?: "unable to list profiles")))
    }

    private fun readReply(id: String): Reply = runCatching {
        val document = core.read(id)
            ?: return@runCatching Reply(CommandOutcome.Failure(CommandError.NotFound(id)))
        Reply(CommandOutcome.Success(document.revision), document.text)
    }.getOrElse {
        Reply(CommandOutcome.Failure(CommandError.Failed(it.message ?: "unable to read profile $id")))
    }

    private fun awaitCommand(command: Command): CommandOutcome {
        var outcome: CommandOutcome? = null
        val latch = CountDownLatch(1)
        main.post {
            core.submit(command) {
                outcome = it
                latch.countDown()
            }
        }
        if (!latch.await(DISPATCH_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            return CommandOutcome.Failure(CommandError.Failed("runtime control timed out"))
        }
        return checkNotNull(outcome)
    }

    private fun timeout(): Reply =
        Reply(CommandOutcome.Failure(CommandError.Failed("runtime control timed out")))

    private fun Command.isDeferred(): Boolean = when (this) {
        is Command.Session.Run, is Command.Session.Stop, is Command.Session.Open -> true
        else -> false
    }

    private fun encode(reply: Reply): String = when (val outcome = reply.outcome) {
        is CommandOutcome.Success -> buildString {
            append("OK\t").append(outcome.message)
            reply.extra?.let { append('\t').append(it) }
        }
        is CommandOutcome.Failure -> "ERR\t${outcome.error.code}\t${outcome.error.message}"
    }

    private fun readFrame(input: InputStream): String? {
        val lengthLine = readLine(input) ?: return null
        val size = lengthLine.trim().toIntOrNull() ?: return null
        if (size < 0 || size > MAX_FRAME_BYTES) return null
        val bytes = ByteArray(size)
        var offset = 0
        while (offset < size) {
            val read = input.read(bytes, offset, size - offset)
            if (read < 0) return null
            offset += read
        }
        return String(bytes, Charsets.UTF_8)
    }

    private fun readLine(input: InputStream): String? {
        val bytes = ByteArrayOutputStream()
        while (true) {
            val next = input.read()
            if (next < 0) return if (bytes.size() == 0) null else bytes.toString("US-ASCII")
            if (next == '\n'.code) return bytes.toString("US-ASCII")
            if (next == '\r'.code) continue
            bytes.write(next)
            if (bytes.size() > MAX_LINE_BYTES) return null
        }
    }

    private fun writeFrame(output: OutputStream, payload: String) {
        val bytes = payload.toByteArray(Charsets.UTF_8)
        output.write("${bytes.size}\n".toByteArray(Charsets.US_ASCII))
        output.write(bytes)
        output.flush()
    }

    private fun randomToken(): String {
        val bytes = ByteArray(18)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }

    private data class Reply(val outcome: CommandOutcome, val extra: String? = null)

    private companion object {
        const val DISPATCH_TIMEOUT_SECONDS = 30L
        const val MAX_FRAME_BYTES = 1 shl 20
        const val MAX_LINE_BYTES = 64
    }
}

/**
 * The length-prefixed, tab-separated request protocol spoken by the app's own
 * shell children. The payload is UTF-8 and may contain tabs/newlines; the frame
 * length makes text fields unambiguous. Kept separate from the socket so the
 * request surface can be unit tested without Android.
 *
 * Request:  `<token>\t<method>[\t<arg>...]`
 * Response: `OK\t<message>[\t<text>]` or `ERR\t<code>\t<message>`
 */
internal object ControlProtocol {
    sealed interface Request {
        data class Status(val id: String?) : Request
        data object ListDocuments : Request
        data class ReadDocument(val id: String) : Request
        data class Execute(val command: Command) : Request
    }

    fun parse(token: String, payload: String?): Request? {
        if (payload == null) return null
        // The last field of documents.save is free-form text, so keep at most
        // five fields and let the final one contain the rest verbatim.
        val fields = payload.split('\t', limit = 5)
        if (fields.size < 2 || fields[0] != token) return null
        return when (fields[1]) {
            "status" if (fields.size == 2) -> Request.Status(null)
            "status" if (fields.size == 3) -> Request.Status(fields[2])
            "run" if (fields.size == 3) -> Request.Execute(Command.Session.Run(fields[2]))
            "open" if (fields.size == 3) -> Request.Execute(Command.Session.Open(fields[2]))
            "stop" if (fields.size == 2) -> Request.Execute(Command.Session.Stop(null))
            "stop" if (fields.size == 3) -> Request.Execute(Command.Session.Stop(fields[2]))
            "gui.open" if (fields.size == 2) -> Request.Execute(Command.Session.OpenGui)
            "documents.list" if (fields.size == 2) -> Request.ListDocuments
            "documents.read" if (fields.size == 3) -> Request.ReadDocument(fields[2])
            "documents.create" if (fields.size == 3) ->
                Request.Execute(Command.Document.Create(fields[2]))
            "documents.save" if (fields.size == 5) -> Request.Execute(
                Command.Document.Save(
                    id = fields[2],
                    text = fields[4],
                    expectedRevision = fields[3],
                ),
            )
            "documents.delete" if (fields.size == 4) -> Request.Execute(
                Command.Document.Delete(fields[2], fields[3]),
            )
            else -> null
        }
    }
}
