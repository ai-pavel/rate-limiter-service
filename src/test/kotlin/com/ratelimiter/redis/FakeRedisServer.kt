package com.ratelimiter.redis

import java.io.BufferedInputStream
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/**
 * A minimal in-process TCP server that speaks just enough of the RESP protocol
 * for a Lettuce client to establish a connection during tests where a real
 * Redis (or Docker/Testcontainers) is not available.
 *
 * It parses inbound RESP arrays of bulk strings and returns simple canned
 * replies. The RESP3 `HELLO` handshake is answered with an error so that
 * Lettuce transparently falls back to RESP2, after which every other command
 * is acknowledged with a generic OK/PONG reply.
 */
class FakeRedisServer : AutoCloseable {

    private val serverSocket = ServerSocket(0)
    val port: Int get() = serverSocket.localPort

    @Volatile
    private var running = true
    private val clients = mutableListOf<Socket>()

    init {
        thread(isDaemon = true, name = "fake-redis-accept") {
            while (running) {
                val socket = try {
                    serverSocket.accept()
                } catch (e: Exception) {
                    break
                }
                synchronized(clients) { clients.add(socket) }
                thread(isDaemon = true, name = "fake-redis-client") { handle(socket) }
            }
        }
    }

    private fun handle(socket: Socket) {
        try {
            val input = BufferedInputStream(socket.getInputStream())
            val output = socket.getOutputStream()
            while (running && !socket.isClosed) {
                val command = readCommand(input) ?: break
                respond(command, output)
            }
        } catch (_: Exception) {
            // connection closed / reset – ignore in tests
        } finally {
            try {
                socket.close()
            } catch (_: Exception) {
            }
        }
    }

    /** Reads a single RESP command (array of bulk strings) and returns its parts. */
    private fun readCommand(input: BufferedInputStream): List<String>? {
        val first = input.read()
        if (first == -1) return null
        // Expect an array header '*'
        if (first.toChar() != '*') {
            // Inline / unexpected – consume the rest of the line and treat as empty
            readLine(input)
            return emptyList()
        }
        val count = readLine(input).toIntOrNull() ?: return emptyList()
        val parts = ArrayList<String>(count)
        repeat(count) {
            val type = input.read()
            if (type == -1) return parts
            if (type.toChar() == '$') {
                val len = readLine(input).toIntOrNull() ?: -1
                if (len < 0) {
                    parts.add("")
                } else {
                    val buf = ByteArray(len)
                    var read = 0
                    while (read < len) {
                        val r = input.read(buf, read, len - read)
                        if (r == -1) break
                        read += r
                    }
                    // consume trailing CRLF
                    input.read(); input.read()
                    parts.add(String(buf, Charsets.UTF_8))
                }
            } else {
                readLine(input)
            }
        }
        return parts
    }

    private fun readLine(input: BufferedInputStream): String {
        val sb = StringBuilder()
        while (true) {
            val c = input.read()
            if (c == -1) break
            if (c.toChar() == '\r') {
                input.read() // consume '\n'
                break
            }
            sb.append(c.toChar())
        }
        return sb.toString()
    }

    private fun respond(command: List<String>, output: OutputStream) {
        val name = command.firstOrNull()?.uppercase() ?: ""
        val reply = when (name) {
            // Force RESP2 fallback so we don't have to build a RESP3 map reply.
            "HELLO" -> "-ERR unknown command 'HELLO'\r\n"
            "PING" -> "+PONG\r\n"
            "QUIT" -> "+OK\r\n"
            else -> "+OK\r\n"
        }
        output.write(reply.toByteArray(Charsets.UTF_8))
        output.flush()
    }

    override fun close() {
        running = false
        synchronized(clients) {
            clients.forEach { runCatching { it.close() } }
            clients.clear()
        }
        runCatching { serverSocket.close() }
    }
}
