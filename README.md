# Spring Boot Microservice Platform

Full-stack microservice system with Keycloak-based user identity, Spring Cloud Gateway, Eureka service discovery, Redis caching/rate-limiting, and a Next.js frontend.

## Architecture Overview

```mermaid
flowchart LR
    U[User Browser] --> F[Next.js Frontend]
    F --> G[API Gateway :8080]
    G --> C[customer-service :8081]
    G --> O[order-service :8082]
    G --> CA[cart-service :8085]
    G --> WI[wishlist-service :8087]
    G --> AD[admin-service :8083]
    G --> PR[product-service :8084]
    G --> PO[poster-service :8089]
    G --> V[vendor-service :8090]
    C --> P1[(PostgreSQL customer_db)]
    O --> P2[(PostgreSQL order_db)]
    CA --> P4[(PostgreSQL cart_db)]
    WI --> P5[(PostgreSQL wishlist_db)]
    PR --> P3[(PostgreSQL product_db)]
    PO --> P6[(PostgreSQL poster_db)]
    V --> P7[(PostgreSQL vendor_db)]
    G --> R[(Redis)]
    C --> R
    O --> R
    CA --> R
    G --> E[Eureka :8761]
    C --> E
    O --> E
    CA --> E
    G --> A[Keycloak JWT Validation]
    C --> A2[Keycloak Admin API]
```

## Repository Structure

- `Services/discovery-server`: Eureka server
- `Services/api-gateway`: Spring Cloud Gateway (JWT auth, header relay, rate limiting)
- `Services/customer-service`: customer domain + Keycloak management integration
- `Services/order-service`: order domain + customer-service integration
- `Services/cart-service`: cart domain + product/order integration
- `Services/wishlist-service`: wishlist domain + product-service integration
- `Services/admin-service`: admin APIs (aggregates privileged order views)
- `Services/product-service`: product catalog domain (single/parent/variation products)
- `Services/poster-service`: marketing banners/posters (placements, links, image upload)
- `Services/vendor-service`: vendor records + vendor user memberships (multi-vendor scope)
- `microservce-frontend`: Next.js UI (Keycloak SPA flow)
- `env/*-sample.env`: environment variable templates
- `docker-compose.yml`: stack without PostgreSQL containers (external DB expected)
- `docker-compose-db.yml`: stack with PostgreSQL containers

## Tech Stack

- Java 21, Spring Boot 4.0.2, Spring Cloud 2025.1.1
- Spring Cloud Gateway (WebFlux), Spring MVC services
- Eureka discovery
- PostgreSQL (customer/order/cart/wishlist/product/poster/vendor services)
- Redis (gateway rate limit + service caches)
- Resilience4j (order-service -> customer-service calls)
- Next.js 16 + React 19 + Keycloak JS SDK
- Oracle Object Storage (S3-compatible) for product/poster images
- Docker multi-stage images for all services

## Service Responsibilities

### discovery-server
- Hosts service registry (`:8761`)
- Other services register and resolve by logical service ID

### api-gateway
- Public API entrypoint
- Validates Keycloak JWT issuer + audience
- Enforces role-based access for `super_admin`, `vendor_admin`, `customer`
- Exposes only user-scoped endpoints:
  - `/customers/register`, `/customers/register-identity`, `/customers/me`
  - `/orders/me`, `/orders/me/**`
  - `/cart/me`, `/cart/me/**`
  - `/wishlist/me`, `/wishlist/me/**`
- Enforces `email_verified=true` for:
  - `/customers/register-identity`, `/customers/me`
  - `/orders/me`, `/orders/me/**`
  - `/cart/me`, `/cart/me/**`
- Denies raw backend paths (`/customers/**`, `/orders/**`, `/cart/**`) by default
- Publicly exposes product catalog read APIs:
  - `GET /products`, `GET /products/{id}`
  - `GET /categories`, `GET /categories/{idOrSlug}`
  - `GET /vendors`, `GET /vendors/{idOrSlug}`
  - `GET /posters`, `GET /posters/{idOrSlug}`, `GET /posters/images/**`
- Admin-only catalog writes:
  - `POST /admin/products`
  - `POST /admin/products/{parentId}/variations`
  - `PUT /admin/products/{id}`
  - `DELETE /admin/products/{id}` (soft delete)
  - `GET /admin/products/deleted`
  - `POST /admin/products/{id}/restore`
- Exposes admin endpoints:
  - `/admin/orders`
  - `/admin/vendors/**`
  - `/admin/posters/**`
- Adds/propagates:
  - `X-Request-Id`
  - `X-User-Sub`
  - `X-User-Email`
  - `X-Internal-Auth` (shared secret for internal trust)
- Applies Redis-backed route-aware rate limits:
  - register
  - customer-me
  - orders-me-read
  - orders-me-write
  - cart-me-read
  - cart-me-write
  - cart-me-checkout
  - wishlist-me-read
  - wishlist-me-write
  - admin-orders
  - products-read
  - admin-products-read
  - admin-products-write
  - posters-read
  - admin-posters-read
  - admin-posters-write
  - admin-vendors-read
  - admin-vendors-write
- Applies Redis-backed idempotency for mutating requests (`POST`, `PUT`, `PATCH`, `DELETE`) when `Idempotency-Key` is provided:
  - First request with key -> forwarded and cached
  - Same key + same payload -> cached response replayed (`X-Idempotency-Status: HIT`)
  - Same key + different payload -> `409 Conflict`

### customer-service
- Customer CRUD/register logic
- Supports:
  - direct register (`/customers/register`) using Keycloak Admin API
  - token-based register (`/customers/register-identity`) for logged-in user bootstrap
- Caches `customerByKeycloak` in Redis
- Verifies internal trust header on `/customers/me` and `/customers/register-identity`

### order-service
- Create/list/order-details domain operations
- `/orders/me*` endpoints are user-scoped
- Supports both single-line and multi-line order creation payloads (`items[]`) for cart checkout
- Calls customer-service through load-balanced `RestClient`
- Resilience:
  - `@Retry(customerService)`
  - `@CircuitBreaker(customerService)`
  - fallback -> `ServiceUnavailableException`
- Caches:
  - `ordersByKeycloak`
  - `orderDetailsByKeycloak`

### cart-service
- Owns customer cart lifecycle (`/cart/me`)
- Enforces purchasable-only items (rejects `PARENT`, inactive, invalid price)
- Stores product snapshots in cart rows and revalidates against product-service during checkout
- Checkout orchestration:
  - sends multi-item order payload to order-service
  - clears cart only after successful order creation
- Caches:
  - `cartByKeycloak`

### wishlist-service
- Owns customer wishlist lifecycle (`/wishlist/me`)
- Stores one distinct product per customer (`keycloak_id + product_id` unique)
- Validates product existence/availability via product-service before add
- Allows parent and variation products in wishlist, while purchase eligibility remains cart/checkout concern
- Caches:
  - `wishlistByKeycloak`

### admin-service
- Admin-only APIs exposed through gateway
- Current endpoints include:
  - `GET /admin/orders` (supports `page`, `size`, `sort`, optional `customerId`, optional `vendorId`)
  - `/admin/posters/**` (poster admin proxy/orchestration)
  - `/admin/vendors/**` (vendor admin proxy/orchestration)
  - `POST /admin/vendors/{vendorId}/users/onboard` (Keycloak user + role + vendor membership orchestration)
- Fetches data from downstream services via service discovery and forwards validated payloads
- Verifies internal trust header (`X-Internal-Auth`)
- Caches admin list responses in Redis (`adminOrders`)

### product-service
- Manages product catalog CRUD
- Supports product types:
  - `SINGLE`, `PARENT`, `VARIATION`
- Product model includes:
  - name, shortDescription, description
  - ordered image names (first image = main image)
  - regularPrice, discountedPrice
  - sellingPrice (`discountedPrice` when present, otherwise `regularPrice`)
  - vendorId (admin products use `00000000-0000-0000-0000-000000000000`)
  - categories (multi-value)
  - required SKU
  - soft delete (`is_deleted`, `deleted_at`)
  - variation attributes (only for `VARIATION` type)
- Multi-vendor behavior:
  - `vendorId` is supported for ownership
  - `VARIATION` products inherit vendor from parent and cannot override it

### poster-service
- Manages storefront posters/banners with placement-based rendering
- Supports public poster reads (active + in schedule window) and admin CRUD
- Supports poster image upload to object storage (`posters/<uuid>.<ext>`) and image fetch via `/posters/images/**`
- Supports slug availability checks and soft delete/restore

### vendor-service
- Manages vendor business records and vendor-user memberships
- Supports public active vendor listing/detail
- Supports admin vendor CRUD, soft delete/restore, and vendor membership CRUD
- Provides internal membership lookup by Keycloak user id for backend vendor scoping

### microservce-frontend
- Keycloak login/signup/logout using redirect flow
- Gets access token silently and sends `Authorization: Bearer ...` to gateway
- On authenticated sessions, auto-bootstrap customer profile:
  - GET `/customers/me`
  - if 404 -> POST `/customers/register-identity`
- If email is unverified, shows resend verification action:
  - POST `/auth/resend-verification`
- UI routes:
  - `/` landing/login/signup
  - `/cart` cart review + checkout
  - `/profile` customer profile
  - `/orders` create/list/detail for own orders
  - `/admin/orders` admin paginated order view
  - `/admin/products` admin product management
  - `/admin/posters` super-admin poster management
  - `/admin/vendors` super-admin vendor setup + vendor admin onboarding

## API Map (Gateway-Exposed)

### Customer
- `POST /customers/register` (public)
- `POST /customers/register-identity` (authenticated)
- `GET /customers/me` (authenticated)

### Orders
- `GET /orders/me` (authenticated)
- `POST /orders/me` (authenticated)
- `GET /orders/me/{id}` (authenticated)

### Cart
- `GET /cart/me` (authenticated)
- `POST /cart/me/items` (authenticated)
- `PUT /cart/me/items/{itemId}` (authenticated)
- `DELETE /cart/me/items/{itemId}` (authenticated)
- `DELETE /cart/me` (authenticated)
- `POST /cart/me/checkout` (authenticated)

### Wishlist
- `GET /wishlist/me` (authenticated)
- `POST /wishlist/me/items` (authenticated)
- `DELETE /wishlist/me/items/{itemId}` (authenticated)
- `DELETE /wishlist/me/items/by-product/{productId}` (authenticated)
- `DELETE /wishlist/me` (authenticated)

### Admin
- `GET /admin/orders` (authenticated + `super_admin` or `vendor_admin`; vendor admins are backend-scoped)
- `GET /admin/vendors` (authenticated + `super_admin`)
- `GET /admin/vendors/deleted` (authenticated + `super_admin`)
- `POST /admin/vendors` (authenticated + `super_admin`)
- `PUT /admin/vendors/{id}` (authenticated + `super_admin`)
- `DELETE /admin/vendors/{id}` (authenticated + `super_admin`, soft delete)
- `POST /admin/vendors/{id}/restore` (authenticated + `super_admin`)
- `GET /admin/vendors/{vendorId}/users` (authenticated + `super_admin`)
- `DELETE /admin/vendors/{vendorId}/users/{membershipId}` (authenticated + `super_admin`)
- `POST /admin/vendors/{vendorId}/users/onboard` (authenticated + `super_admin`)
- `GET /admin/posters` (authenticated + `super_admin`)
- `GET /admin/posters/deleted` (authenticated + `super_admin`)
- `POST /admin/posters` (authenticated + `super_admin`)
- `PUT /admin/posters/{id}` (authenticated + `super_admin`)
- `DELETE /admin/posters/{id}` (authenticated + `super_admin`, soft delete)
- `POST /admin/posters/{id}/restore` (authenticated + `super_admin`)
- `POST /admin/posters/images/names` (authenticated + `super_admin`)
- `POST /admin/posters/images` (authenticated + `super_admin`)

### Products
- `GET /products` (public)
- `GET /products/{id}` (public)
- `GET /products/images/**` (public)
- `POST /admin/products` (authenticated + admin authority)
- `POST /admin/products/{parentId}/variations` (authenticated + admin authority)
- `PUT /admin/products/{id}` (authenticated + admin authority)
- `DELETE /admin/products/{id}` (authenticated + admin authority, soft delete)
- `GET /admin/products/deleted` (authenticated + admin authority)
- `POST /admin/products/{id}/restore` (authenticated + admin authority)

### Categories
- `GET /categories` (public)
- `GET /categories/{idOrSlug}` (public)

### Vendors
- `GET /vendors` (public)
- `GET /vendors/{idOrSlug}` (public)
- `GET /vendors/slug-available` (public)

### Posters
- `GET /posters` (public)
- `GET /posters/{idOrSlug}` (public)
- `GET /posters/slug-available` (public)
- `GET /posters/images/**` (public)

### Auth
- `POST /auth/logout` (authenticated)
- `POST /auth/resend-verification` (authenticated)

## Auth and Trust Model

```mermaid
sequenceDiagram
    participant UI as Frontend
    participant KC as Keycloak
    participant GW as API Gateway
    participant CS as customer-service
    participant OS as order-service
    participant CART as cart-service
    participant ADS as admin-service

    UI->>KC: Login/Signup redirect
    KC-->>UI: Access token (JWT)
    UI->>GW: API call + Bearer token
    GW->>GW: Validate issuer + audience
    GW->>GW: Strip client-forged internal headers
    GW->>CS: Forward + X-User-Sub/X-User-Email/X-Internal-Auth
    GW->>CS: Forward + X-User-Email-Verified
    GW->>OS: Forward + X-User-Sub/X-User-Email-Verified/X-Internal-Auth
    GW->>CART: Forward + X-User-Sub/X-User-Email-Verified/X-Internal-Auth
    CART->>OS: Internal service call + shared secret
    CART->>PR: Internal product validation call
    GW->>ADS: Forward + X-Internal-Auth
    ADS->>OS: Internal service call + shared secret
    OS->>CS: Internal service call + shared secret
```

Key points:
- Backend services do **not** trust incoming internal headers from clients.
- Gateway sanitizes and rewrites trusted headers.
- `INTERNAL_AUTH_SHARED_SECRET` must be identical across gateway/customer/order/cart/admin services.

## Data and Caching Design

### Persistence
- `customer-service` -> `customer_db`
- `order-service` -> `order_db`
- `cart-service` -> `cart_db`

### Redis usage
- Gateway: token bucket state for rate limiting
- Gateway: idempotency key state/response replay cache
- customer-service: `customerByKeycloak`
- order-service: `ordersByKeycloak`, `orderDetailsByKeycloak`
- cart-service: `cartByKeycloak`
- product-service: `productById`, `productsList`, `deletedProductsList`, `categoriesList`, `deletedCategoriesList`

### Serialization note
- Cache serializers are configured with app `ObjectMapper` and type metadata.
- `order-service` includes a `PageImpl` mixin for paged cache deserialization.

## Rate Limiting Policies

Configured by environment variables:
- `RATE_LIMIT_REGISTER_REPLENISH`, `RATE_LIMIT_REGISTER_BURST`
- `RATE_LIMIT_CUSTOMER_ME_REPLENISH`, `RATE_LIMIT_CUSTOMER_ME_BURST`
- `RATE_LIMIT_ORDERS_ME_REPLENISH`, `RATE_LIMIT_ORDERS_ME_BURST`
- `RATE_LIMIT_ORDERS_ME_WRITE_REPLENISH`, `RATE_LIMIT_ORDERS_ME_WRITE_BURST`
- `RATE_LIMIT_CART_ME_REPLENISH`, `RATE_LIMIT_CART_ME_BURST`
- `RATE_LIMIT_CART_ME_WRITE_REPLENISH`, `RATE_LIMIT_CART_ME_WRITE_BURST`
- `RATE_LIMIT_CART_ME_CHECKOUT_REPLENISH`, `RATE_LIMIT_CART_ME_CHECKOUT_BURST`
- `RATE_LIMIT_ADMIN_ORDERS_REPLENISH`, `RATE_LIMIT_ADMIN_ORDERS_BURST`
- `RATE_LIMIT_PRODUCTS_REPLENISH`, `RATE_LIMIT_PRODUCTS_BURST`
- `RATE_LIMIT_ADMIN_PRODUCTS_REPLENISH`, `RATE_LIMIT_ADMIN_PRODUCTS_BURST`
- `RATE_LIMIT_ADMIN_PRODUCTS_WRITE_REPLENISH`, `RATE_LIMIT_ADMIN_PRODUCTS_WRITE_BURST`
- Optional defaults:
  - `RATE_LIMIT_DEFAULT_REPLENISH`
  - `RATE_LIMIT_DEFAULT_BURST`
- Proxy IP handling:
  - `RATE_LIMIT_TRUSTED_PROXY_IPS`

## Idempotency

- Header: `Idempotency-Key`
- Scope: user-or-ip + method + path + provided key
- Behavior:
  - `MISS`: request executed and response cached
  - `HIT`: cached response replayed
  - `CONFLICT`: key reused with different payload, or request is still processing
- Config:
  - `IDEMPOTENCY_ENABLED`
  - `IDEMPOTENCY_REQUIRE_KEY_FOR_MUTATING_REQUESTS`
  - `IDEMPOTENCY_KEY_HEADER_NAME`
  - `IDEMPOTENCY_RESPONSE_TTL`
  - `IDEMPOTENCY_PENDING_TTL`
  - `IDEMPOTENCY_KEY_PREFIX`

## Environment Setup

Create concrete env files from samples:

```powershell
Copy-Item env/common-sample.env env/common.env
Copy-Item env/eureka-sample.env env/eureka.env
Copy-Item env/customer-service-sample.env env/customer-service.env
Copy-Item env/order-service-sample.env env/order-service.env
Copy-Item env/cart-service-sample.env env/cart-service.env
Copy-Item env/wishlist-service-sample.env env/wishlist-service.env
Copy-Item env/product-service-sample.env env/product-service.env
Copy-Item env/poster-service-sample.env env/poster-service.env
Copy-Item env/vendor-service-sample.env env/vendor-service.env
Copy-Item env/admin-service-sample.env env/admin-service.env
Copy-Item env/payment-service-sample.env env/payment-service.env
Copy-Item env/search-service-sample.env env/search-service.env
Copy-Item env/frontend-sample.env env/frontend.env
```

Fill required values:
- Keycloak:
  - `KEYCLOAK_ISSUER_URI`
  - `KEYCLOAK_AUDIENCE`
  - `KEYCLOAK_REALM`
  - `KEYCLOAK_ADMIN_CLIENT_ID`
  - `KEYCLOAK_ADMIN_CLIENT_SECRET`
  - `KEYCLOAK_ADMIN_REALM`
  - `KEYCLOAK_VENDOR_ADMIN_ROLE` (default `vendor_admin` in `admin-service`)
  - `NEXT_PUBLIC_KEYCLOAK_URL`
  - `NEXT_PUBLIC_KEYCLOAK_REALM`
  - `NEXT_PUBLIC_KEYCLOAK_CLIENT_ID`
  - `NEXT_PUBLIC_KEYCLOAK_AUDIENCE`
- Internal trust:
  - `INTERNAL_AUTH_SHARED_SECRET` (same value across gateway and all internal services that verify it)
- Elasticsearch:
  - `ELASTIC_PASSWORD` (used by the `elasticsearch` container)
  - `ELASTICSEARCH_USERNAME` (usually `elastic`)
  - `ELASTICSEARCH_PASSWORD` (must match `ELASTIC_PASSWORD`)
- Cart DB:
  - `CART_DB_URL`
  - `CART_DB_USER`
  - `CART_DB_PASS`
- Wishlist DB:
  - `WISHLIST_DB_URL`
  - `WISHLIST_DB_USER`
  - `WISHLIST_DB_PASS`
- Product DB:
  - `PRODUCT_DB_URL`
  - `PRODUCT_DB_USER`
  - `PRODUCT_DB_PASS`
  - `SAMPLE_CATALOG_SEED_ENABLED` (default `true`, seeds sample categories/products on empty DB startup)
- Poster service:
  - `POSTER_DB_URL`
  - `POSTER_DB_USER`
  - `POSTER_DB_PASS`
  - `SAMPLE_POSTER_SEED_ENABLED` (default `true`, seeds test posters on empty DB startup)
  - `OBJECT_STORAGE_ENABLED`
  - `OBJECT_STORAGE_ENDPOINT`
  - `OBJECT_STORAGE_REGION`
  - `OBJECT_STORAGE_ACCESS_KEY`
  - `OBJECT_STORAGE_SECRET_KEY`
  - `OBJECT_STORAGE_BUCKET`
- Vendor service:
  - `VENDOR_DB_URL`
  - `VENDOR_DB_USER`
  - `VENDOR_DB_PASS`
  - `SAMPLE_VENDOR_SEED_ENABLED` (default `true`, seeds test vendors on empty DB startup)
- Admin service (vendor onboarding / Keycloak admin):
  - `KEYCLOAK_ADMIN_CLIENT_ID`
  - `KEYCLOAK_ADMIN_CLIENT_SECRET`
  - `KEYCLOAK_ADMIN_REALM`
  - `KEYCLOAK_VENDOR_ADMIN_ROLE`
- Cart cache:
  - `CACHE_CART_BY_KEYCLOAK_TTL` (example: `30s`)
- Wishlist cache:
  - `CACHE_WISHLIST_BY_KEYCLOAK_TTL` (example: `45s`)
- Admin cache:
  - `CACHE_ADMIN_ORDERS_TTL` (example: `30s`)
- Product cache:
  - `CACHE_PRODUCT_BY_ID_TTL` (example: `120s`)
  - `CACHE_PRODUCT_LIST_TTL` (example: `45s`)
  - `CACHE_PRODUCT_DELETED_LIST_TTL` (example: `30s`)
- API base for frontend:
  - `NEXT_PUBLIC_API_BASE` (for local compose: `http://localhost:8080` with `docker-compose-db.yml`, or `http://localhost:8095` with `docker-compose.yml`)
- Frontend image/CDN (optional but recommended):
  - `NEXT_PUBLIC_PRODUCT_IMAGE_BASE_URL` (example: `https://cdn.rumalg.me`)
  - `NEXT_PUBLIC_POSTER_IMAGE_BASE_URL` (example: `https://cdn.rumalg.me`)

## Running with Docker

### Option A: with PostgreSQL containers

```bash
docker compose -f docker-compose-db.yml up --build
```

Ports:
- Eureka: `http://localhost:8761`
- Gateway: `http://localhost:8080`
- Frontend: `http://localhost:8086`
- Customer DB: `localhost:5433`
- Order DB: `localhost:5434`
- Product DB: `localhost:5435`
- Cart DB: `localhost:5436`
- Wishlist DB: `localhost:5437`
- Poster DB: `localhost:5438`
- Vendor DB: `localhost:5439`

### Option B: without PostgreSQL containers

```bash
docker compose up --build
```

Ports:
- Eureka: `http://localhost:8761`
- Gateway: `http://localhost:8095`
- Frontend: `http://localhost:8086`

Use this only if your DB endpoints in env files point to reachable external databases.

## Running Services Locally (without Docker)

Start infra first (at least Redis, PostgreSQL, Eureka), then in separate terminals:

```bash
cd Services/discovery-server && ./mvnw spring-boot:run
cd Services/customer-service && ./mvnw spring-boot:run
cd Services/order-service && ./mvnw spring-boot:run
cd Services/cart-service && ./mvnw spring-boot:run
cd Services/wishlist-service && ./mvnw spring-boot:run
cd Services/product-service && ./mvnw spring-boot:run
cd Services/poster-service && ./mvnw spring-boot:run
cd Services/vendor-service && ./mvnw spring-boot:run
cd Services/admin-service && ./mvnw spring-boot:run
cd Services/api-gateway && ./mvnw spring-boot:run
cd microservce-frontend && npm ci && npm run dev
```

## Build and Verify

```bash
cd Services/api-gateway && ./mvnw -q -DskipTests compile
cd Services/customer-service && ./mvnw -q -DskipTests compile
cd Services/order-service && ./mvnw -q -DskipTests compile
cd Services/cart-service && ./mvnw -q -DskipTests compile
cd Services/wishlist-service && ./mvnw -q -DskipTests compile
cd Services/product-service && ./mvnw -q -DskipTests compile
cd Services/poster-service && ./mvnw -q -DskipTests compile
cd Services/vendor-service && ./mvnw -q -DskipTests compile
cd Services/admin-service && ./mvnw -q -DskipTests compile
cd Services/discovery-server && ./mvnw -q -DskipTests compile
cd microservce-frontend && npm run lint
```

## Vendor Onboarding (Super Admin Flow)

The `/admin/vendors` page combines two workflows:

1. **Create Vendor (business record)**
- Uses the left-side form
- Creates a row in `vendor-service` (`vendors` table)

2. **Onboard Vendor Admin (user + role + membership)**
- Uses the right-side panel after selecting a vendor
- Calls `POST /admin/vendors/{vendorId}/users/onboard`
- `admin-service` orchestrates:
  - validate vendor exists (`vendor-service`)
  - find/create Keycloak user
  - assign realm role `vendor_admin`
  - create/update vendor membership in `vendor-service` (`vendor_users`)
  - send Keycloak action email (`VERIFY_EMAIL`, `UPDATE_PASSWORD`) for newly created users

### Keycloak Dashboard Setup for Vendor Onboarding

In your Keycloak realm (for example `microservice-realm`):

1. Create realm roles
- `super_admin`
- `vendor_admin`
- `customer`

2. Create/configure a confidential admin client (machine-to-machine)
- Example client ID: `gateway-admin` (or a dedicated admin-service client)
- Enable:
  - `Client authentication`
  - `Service accounts roles`
- Recommended:
  - disable standard flow/direct access grants for this M2M client

3. Assign `realm-management` roles to the service account
- `manage-users`
- `view-users`
- `query-users`
- `view-realm`

4. Configure SMTP in Keycloak (required for onboarding email)
- Without SMTP, vendor onboarding can fail when trying to send the action email for new users

5. Set `admin-service` envs to match Keycloak
- `KEYCLOAK_ISSUER_URI`
- `KEYCLOAK_REALM`
- `KEYCLOAK_ADMIN_REALM`
- `KEYCLOAK_ADMIN_CLIENT_ID`
- `KEYCLOAK_ADMIN_CLIENT_SECRET`
- `KEYCLOAK_VENDOR_ADMIN_ROLE=vendor_admin`

## Troubleshooting

- Gateway startup fails with multiple `RateLimiter` or `KeyResolver` beans:
  - Ensure one default bean is marked `@Primary` (already implemented in `RateLimitConfig`).
- Redis `Instant` serialization errors:
  - Ensure cache serializers use configured `ObjectMapper` (already implemented).
- Redis `PageImpl` deserialization errors:
  - Ensure `PageImpl` mixin in `order-service` is present (already implemented).
  - Clear stale Redis keys after serializer changes.
- `401 Invalid internal authentication header`:
  - `INTERNAL_AUTH_SHARED_SECRET` mismatch or missing.
- Keycloak signup/login generic errors:
  - Check tenant settings and prompt customization; verify app/connection config.
- Vendor onboarding fails with Keycloak email/action error:
  - Verify Keycloak SMTP is configured.
  - Verify the admin service-account client has `realm-management` roles (`manage-users`, `view-users`, `query-users`, `view-realm`).
- Vendor service seeder fails with fixed UUID errors:
  - Ensure `vendor-service` is running the updated `Vendor` entity (`@Id` + `@PrePersist` UUID generation, no `@GeneratedValue`).

## Production Hardening Checklist

- Set `ddl-auto` away from `create-drop` for persistent environments.
- Use managed Redis/PostgreSQL with backups.
- Lock down CORS to exact origins.
- Use HTTPS for frontend/gateway and set trusted proxy headers correctly.
- Rotate `INTERNAL_AUTH_SHARED_SECRET`.
- Disable insecure default Keycloak credentials and require strong client secrets.
- Add structured central logging and metrics dashboards.

