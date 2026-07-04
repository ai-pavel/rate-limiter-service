# Rate Limiter gRPC Service

[![CI](https://github.com/pavel-genai/rate-limiter-service/actions/workflows/ci.yml/badge.svg)](https://github.com/pavel-genai/rate-limiter-service/actions/workflows/ci.yml)

A Kotlin gRPC microservice implementing distributed rate limiting with Redis-backed state storage.

## Features

- **Three rate-limiting algorithms**, selectable per client/resource:
  - **Token Bucket** -- tokens refill at a constant rate; smooth burst handling
  - **Sliding Window Log** -- exact count using a sorted set of timestamps
  - **Sliding Window Counter** -- memory-efficient approximation using weighted sub-window counters
- **Redis (Lettuce)** for atomic, distributed state management via Lua scripts
- **Protobuf/gRPC API** with two RPCs:
  - `CheckRateLimit(client_id, resource)` -- returns `{allowed, remaining, retry_after_ms}`
  - `ConfigureLimit(client_id, resource, algorithm, limit, window_ms)` -- persists per-client config

## Prerequisites

- JDK 17+
- Docker (for Redis in development and Testcontainers in tests)

## Build

```bash
./gradlew build
```

To compile without running tests:

```bash
./gradlew build -x test
```

## Run

Start a local Redis instance:

```bash
docker run -d --name redis -p 6379:6379 redis:7-alpine
```

Run the service:

```bash
./gradlew run
```

The gRPC server listens on port `50051` by default. Override with the `GRPC_PORT` environment variable.

### Environment Variables

| Variable     | Default     | Description            |
|-------------|-------------|------------------------|
| `GRPC_PORT` | `50051`     | gRPC server port       |
| `REDIS_HOST`| `localhost` | Redis host             |
| `REDIS_PORT`| `6379`      | Redis port             |

## Testing

```bash
./gradlew test
```

Tests use [Testcontainers](https://www.testcontainers.org/) to spin up a Redis container automatically -- Docker must be running.

- **Unit tests**: one test class per algorithm (`TokenBucketRateLimiterTest`, `SlidingWindowLogRateLimiterTest`, `SlidingWindowCounterRateLimiterTest`)
- **Integration test**: `RateLimiterIntegrationTest` starts the full gRPC server with a Testcontainers Redis instance and exercises the complete configure-then-check flow

## Project Structure

```
src/
  main/
    proto/              Protobuf service definition
    kotlin/com/ratelimiter/
      Main.kt           Application entry point
      model/            Data classes (RateLimitConfig, RateLimitResult)
      algorithm/        RateLimiter interface + three implementations
      config/           ConfigStore (Redis-backed config persistence)
      redis/            RedisClientProvider (Lettuce connection management)
      grpc/             gRPC service implementation
  test/
    kotlin/com/ratelimiter/
      algorithm/        Unit tests for each algorithm
      integration/      End-to-end gRPC integration test
```

## API Usage (grpcurl)

```bash
# Configure a rate limit
grpcurl -plaintext -d '{
  "client_id": "my-app",
  "resource": "/api/users",
  "algorithm": "TOKEN_BUCKET",
  "limit": 100,
  "window_ms": 60000
}' localhost:50051 ratelimiter.RateLimiterService/ConfigureLimit

# Check rate limit
grpcurl -plaintext -d '{
  "client_id": "my-app",
  "resource": "/api/users"
}' localhost:50051 ratelimiter.RateLimiterService/CheckRateLimit
```
