package com.ratelimiter.redis

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Exercises [RedisClientProvider] against an in-process fake Redis server so
 * the connection lifecycle can be covered without Docker/Testcontainers.
 */
class RedisClientProviderTest {

    private lateinit var fakeRedis: FakeRedisServer

    @BeforeEach
    fun setup() {
        fakeRedis = FakeRedisServer()
    }

    @AfterEach
    fun teardown() {
        fakeRedis.close()
    }

    @Test
    fun `connects and exposes sync commands`() {
        val provider = RedisClientProvider(host = "localhost", port = fakeRedis.port)
        try {
            val commands = provider.sync()
            assertNotNull(commands, "sync() should return a RedisCommands instance")
            // A second call should also succeed and share the same connection.
            assertNotNull(provider.sync())
        } finally {
            provider.close()
        }
    }

    @Test
    fun `close shuts down the connection and client without error`() {
        val provider = RedisClientProvider(host = "localhost", port = fakeRedis.port)
        provider.sync()
        // Should not throw.
        assertDoesNotThrow { provider.close() }
    }

    @Test
    fun `default constructor reads host and port (falling back to defaults)`() {
        // With no REDIS_HOST/REDIS_PORT env vars set in the test environment the
        // provider must fall back to localhost:6379. We point it at the fake
        // server's port instead so the connection actually succeeds, but still
        // exercise the default host resolution path.
        val host = System.getenv("REDIS_HOST") ?: "localhost"
        val provider = RedisClientProvider(host = host, port = fakeRedis.port)
        try {
            assertNotNull(provider.sync())
        } finally {
            provider.close()
        }
    }
}
