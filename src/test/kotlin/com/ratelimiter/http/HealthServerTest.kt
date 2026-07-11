package com.ratelimiter.http

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class HealthServerTest {

    private lateinit var server: HealthServer
    private lateinit var client: HttpClient
    private var port: Int = 0

    @BeforeAll
    fun setup() {
        // Port 0 asks the OS for an ephemeral port so tests never collide.
        server = HealthServer(port = 0)
        server.start()
        port = server.boundPort
        client = HttpClient.newHttpClient()
    }

    @AfterAll
    fun teardown() {
        server.stop()
    }

    private fun healthUri(): URI = URI.create("http://localhost:$port/health")

    @Test
    fun `binds an ephemeral port when constructed with port 0`() {
        assertTrue(port > 0, "expected a real bound port, got $port")
    }

    @Test
    fun `GET health returns 200`() {
        val request = HttpRequest.newBuilder(healthUri()).GET().build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())

        assertEquals(200, response.statusCode())
    }

    @Test
    fun `GET health returns application json content type`() {
        val request = HttpRequest.newBuilder(healthUri()).GET().build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())

        assertEquals("application/json", response.headers().firstValue("Content-Type").orElse(null))
    }

    @Test
    fun `GET health returns expected body`() {
        val request = HttpRequest.newBuilder(healthUri()).GET().build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())

        assertEquals("""{"status":"ok","service":"rate-limiter-service"}""", response.body())
    }

    @Test
    fun `POST health returns 405 method not allowed`() {
        val request = HttpRequest.newBuilder(healthUri())
            .POST(HttpRequest.BodyPublishers.noBody())
            .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())

        assertEquals(405, response.statusCode())
        assertEquals("", response.body())
    }

    @Test
    fun `DELETE health returns 405 method not allowed`() {
        val request = HttpRequest.newBuilder(healthUri()).DELETE().build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())

        assertEquals(405, response.statusCode())
    }

    @Test
    fun `stop shuts the server down`() {
        val local = HealthServer(port = 0)
        local.start()
        val localPort = local.boundPort

        local.stop()

        val request = HttpRequest.newBuilder(URI.create("http://localhost:$localPort/health")).GET().build()
        assertThrows(java.io.IOException::class.java) {
            client.send(request, HttpResponse.BodyHandlers.ofString())
        }
    }
}
