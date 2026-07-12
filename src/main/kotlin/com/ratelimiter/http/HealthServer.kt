package com.ratelimiter.http

import com.sun.net.httpserver.HttpServer
import org.slf4j.LoggerFactory
import java.net.InetSocketAddress

/**
 * Minimal HTTP server exposing GET /health for container health checks.
 * Uses the JDK built-in HttpServer so no extra dependencies are required.
 */
class HealthServer(private val port: Int = 8080) {

    private val logger = LoggerFactory.getLogger(HealthServer::class.java)

    private val body = """{"status":"ok","service":"pacer"}""".toByteArray(Charsets.UTF_8)

    private val server: HttpServer = HttpServer.create(InetSocketAddress(port), 0).apply {
        createContext("/health") { exchange ->
            try {
                if (exchange.requestMethod == "GET") {
                    exchange.responseHeaders.add("Content-Type", "application/json")
                    exchange.sendResponseHeaders(200, body.size.toLong())
                    exchange.responseBody.write(body)
                } else {
                    exchange.sendResponseHeaders(405, -1)
                }
            } finally {
                exchange.close()
            }
        }
    }

    /** Actual port the server is bound to (useful when constructed with port 0). */
    val boundPort: Int
        get() = server.address.port

    fun start() {
        server.start()
        logger.info("Health HTTP server started on port {}", boundPort)
    }

    fun stop() {
        server.stop(0)
    }
}
