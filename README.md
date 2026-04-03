# ⚡ URL Shortener — Java 21 + Spring Boot 3 + Redis + MySQL

A high-performance, production-ready URL shortener backend built with a focus on low-latency redirects, zero-collision code generation, and scalable caching architecture.

---

## 📌 Table of Contents

- [Features](#-features)
- [Tech Stack](#-tech-stack)
- [Architecture](#-architecture)
- [Project Structure](#-project-structure)
- [Getting Started](#-getting-started)
- [API Reference](#-api-reference)
- [Configuration](#-configuration)
- [Running Tests](#-running-tests)
- [Performance Design Decisions](#-performance-design-decisions)
- [Future Improvements](#-future-improvements)

---

## ✨ Features

- 🔗 Shorten any valid URL to a **7-character Base62 code** (e.g., `short.ly/0aB3xZ1`)
- ⚡ Redirects served in **< 100ms** via Redis Cache-Aside pattern
- ♻️ **Deduplication** — same long URL always returns the same short code
- 📊 **Click analytics** — track how many times a short URL is accessed
- ⏳ **Optional URL expiry** — set a TTL in hours when shortening
- 🛡️ **Global error handling** — consistent JSON error responses for all failure cases
- 📖 **Swagger UI** — interactive API documentation at `/swagger-ui.html`
- 🐳 **Docker Compose** — one command to spin up MySQL + Redis locally

---

## 🛠 Tech Stack

| Layer         | Technology                        |
|---------------|-----------------------------------|
| Language       | Java 21                          |
| Framework      | Spring Boot 3.3                  |
| Database       | MySQL 8.0 (via Spring Data JPA)  |
| Cache          | Redis 7.2 (via Spring Data Redis)|
| ORM            | Hibernate / JPA                  |
| Validation     | Hibernate Validator              |
| Docs           | SpringDoc OpenAPI (Swagger UI)   |
| Testing        | JUnit 5 + Mockito + AssertJ      |
| Build Tool     | Maven                            |
| DevOps         | Docker + Docker Compose          |

---

## 🏗 Architecture

### Cache-Aside Pattern (Read Path)

```
Client → GET /{shortCode}
              │
              ▼
        ┌─────────────┐     HIT      ┌─────────┐
        │    Redis    │ ──────────►  │ Redirect │
        └─────────────┘              └─────────┘
              │ MISS
              ▼
        ┌─────────────┐
        │    MySQL    │ ──► Populate Redis ──► Redirect
        └─────────────┘
```

### Write Path (Shorten)

```
POST /api/urls
      │
      ▼
Check deduplication (findByLongUrl)
      │ Not found
      ▼
Save to MySQL → Get auto-increment ID
      │
      ▼
Encode ID → Base62 shortCode (e.g., id=1 → "0000001")
      │
      ▼
Update record + Warm Redis cache
      │
      ▼
Return ShortenResponse
```

### Blind Update (Click Count)

```
On every redirect:
  Main thread  → Return 302 redirect immediately
  @Async thread → INCREMENT click_count in MySQL (background, non-blocking)
```

---

## 📁 Project Structure

```
url-shortener/
├── src/
│   ├── main/
│   │   ├── java/com/vaishnavaachal/url_shortener
│   │   │   ├── UrlShortenerApplication.java   # Entry point
│   │   │   ├── config/
│   │   │   │   ├── RedisConfig.java           # RedisTemplate + CacheManager beans
│   │   │   │   └── AsyncConfig.java           # Thread pool for blind updates
│   │   │   ├── controller/
│   │   │   │   └── UrlShortenerController.java
│   │   │   ├── service/
│   │   │   │   └── UrlShortenerService.java   # Core business logic
│   │   │   ├── repository/
│   │   │   │   └── UrlMappingRepository.java
│   │   │   ├── model/
│   │   │   │   └── UrlMapping.java            # JPA entity
│   │   │   ├── dto/
│   │   │   │   └── UrlDtos.java               # Request/Response/Error DTOs
│   │   │   ├── exception/
│   │   │   │   ├── UrlExceptions.java         # Custom domain exceptions
│   │   │   │   └── GlobalExceptionHandler.java
│   │   │   └── util/
│   │   │       └── Base62Util.java            # Encoding algorithm
│   │   └── resources/
│   │       └── application.yml
│   └── test/
│       └── java/com/vaishnavaachal/url_shortener
│           └── UrlShortenerTests.java         # JUnit 5 + Mockito tests
├── docker-compose.yml
├── pom.xml
└── README.md
```

---

## 🚀 Getting Started

### Prerequisites

- Java 21+
- Maven 3.9+
- Docker + Docker Compose

### 1. Clone the repository

```bash
git clone https://github.com/aachal-vaishnav/url-shortner.git
cd url-shortner
```

### 2. Start MySQL and Redis

```bash
docker-compose up -d
```

This starts:
- MySQL 8.0 on port `3306` (database: `url_shortener_db`)
- Redis 7.2 on port `6379`

### 3. Configure the application

Edit `src/main/resources/application.yml` if needed:

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/url_shortener_db
    username: root
    password: your_password   # ← update this

app:
  base-url: http://localhost:8080  # ← change to your domain in production
```

### 4. Run the application

```bash
./mvnw spring-boot:run
```

The app starts on **http://localhost:8080**

### 5. Explore the API

Open Swagger UI: **http://localhost:8080/swagger-ui.html**

---

## 📡 API Reference

### `POST /api/urls` — Shorten a URL

**Request body:**
```json
{
  "longUrl": "https://www.example.com/some/very/long/path?ref=homepage",
  "expiryHours": 24
}
```

**Response `201 Created`:**
```json
{
  "shortCode": "0000001",
  "shortUrl": "http://localhost:8080/0000001",
  "longUrl": "https://www.example.com/some/very/long/path?ref=homepage",
  "createdAt": "2026-06-01T10:30:00",
  "expiresAt": "2026-06-02T10:30:00"
}
```

---

### `GET /{shortCode}` — Redirect

```
GET /0000001
→ HTTP 302 Location: https://www.example.com/some/very/long/path?ref=homepage
```

| Status | Meaning |
|--------|---------|
| `302`  | Redirecting to original URL |
| `400`  | Invalid short code format |
| `404`  | Short URL not found |
| `410`  | Short URL has expired |

---

### `GET /api/urls/{shortCode}/stats` — Analytics

**Response `200 OK`:**
```json
{
  "shortCode": "0000001",
  "shortUrl": "http://localhost:8080/0000001",
  "longUrl": "https://www.example.com/...",
  "clickCount": 142,
  "createdAt": "2026-06-01T10:30:00",
  "expiresAt": "2026-06-02T10:30:00",
  "expired": false
}
```

---

### `DELETE /api/urls/{shortCode}` — Delete

```
DELETE /api/urls/0000001
→ HTTP 204 No Content
```

Removes the mapping from both MySQL and Redis cache.

---

### Error Response Format

All errors return a consistent JSON envelope:

```json
{
  "status": 404,
  "error": "Not Found",
  "message": "Short URL 'abc1234' not found. It may have been deleted or never existed.",
  "path": "/abc1234",
  "timestamp": "2026-06-01T10:35:00"
}
```

---

## ⚙️ Configuration

| Property | Default | Description |
|----------|---------|-------------|
| `app.base-url` | `http://localhost:8080` | Base URL prepended to short codes |
| `app.short-code-length` | `7` | Length of generated short codes |
| `spring.data.redis.host` | `localhost` | Redis host |
| `spring.data.redis.port` | `6379` | Redis port |
| `spring.cache.redis.time-to-live` | `86400000` (24h) | Default Redis TTL in ms |
| `async.core-pool-size` | `4` | Core threads for async click updates |
| `async.max-pool-size` | `10` | Max threads for async click updates |

---

## 🧪 Running Tests

```bash
./mvnw test
```

The test suite covers:

| Test | Description |
|------|-------------|
| `encode_idOne_returns7CharCode` | Base62 encoding produces padded 7-char strings |
| `encodeAndDecode_areInverse` | Round-trip encode → decode returns original ID |
| `encode_differentIds_differentCodes` | No collisions across different IDs |
| `encode_maxCapacity_stillSevenChars` | Handles 62^7 - 1 without overflow |
| `shorten_newUrl_returnsResponse` | Service correctly saves and returns a short URL |
| `shorten_duplicateUrl_returnsCachedMapping` | Deduplication prevents duplicate DB rows |
| `resolve_cacheHit_returnsCachedUrl` | Redis hit path never touches the database |
| `resolve_cacheMiss_queriesMysqlAndCachesResult` | Cache miss populates Redis after DB query |
| `resolve_unknownCode_throwsNotFoundException` | 404 thrown for non-existent codes |
| `resolve_malformedCode_throwsInvalidUrlException` | Bloom filter rejects garbage input before DB |
| `resolve_expiredUrl_throwsExpiredException` | 410 thrown for expired URLs |

---

## ⚡ Performance Design Decisions

### 1. Base62 Counter Encoding (Zero Collisions)
Instead of hashing (which risks collisions), we encode MySQL's auto-increment `id` into Base62. Since every row gets a unique ID, every short code is mathematically guaranteed to be unique. Capacity: `62^7 = 3,521,614,606,207` unique codes.

### 2. Cache-Aside with Redis (80% traffic offload)
Short code → long URL mappings are stored in Redis with a 24-hour TTL. The hot redirect path (`GET /{shortCode}`) checks Redis first; only on a miss does it query MySQL. In steady state, the vast majority of redirects never reach the database.

### 3. Bloom Filter (Pre-filter for invalid codes)
Before touching Redis or MySQL, every short code is validated against the Base62 alphabet and expected length. Malformed or random inputs are rejected immediately, protecting the data layer from garbage requests. In production, this can be replaced with a Redis Bloom Filter (`RedisBloom` module) for probabilistic set membership checks.

### 4. Blind Updates via `@Async` (Non-blocking click counts)
Click count increments run in a dedicated background thread pool (`clickCountExecutor`). The HTTP redirect response is returned to the user immediately, without waiting for the database write. The counter update uses an atomic JPQL `UPDATE` statement to prevent lost updates under concurrent access.

### 5. Unique Index on `short_code`
The `short_code` column has a `UNIQUE INDEX` in MySQL, ensuring O(1) lookup performance regardless of table size. The `long_url` column is also indexed for deduplication queries.

### 6. HikariCP Connection Pool Tuning
The MySQL connection pool is configured with `maximum-pool-size: 20` and `minimum-idle: 5`, balancing connection overhead against burst traffic capacity.

---

## 🔮 Future Improvements

- [ ] **Rate Limiting** — add `spring-boot-starter-ratelimiter` or use Redis token buckets to prevent abuse
- [ ] **Custom Aliases** — allow users to choose their own short code (e.g., `/my-brand`)
- [ ] **QR Code Generation** — return a QR code image alongside the short URL
- [ ] **User Authentication** — JWT-based auth so users can manage their own URLs
- [ ] **Database Migrations** — replace `ddl-auto: update` with Flyway for production-safe schema versioning
- [ ] **Kubernetes Deployment** — add Helm charts and Horizontal Pod Autoscaler config
- [ ] **Metrics Dashboard** — expose Micrometer metrics to Prometheus + Grafana for real-time monitoring
- [ ] **True Bloom Filter** — integrate `RedisBloom` module for probabilistic O(1) existence checks
