# Missing Services & Features Roadmap

> **Reference document** for future platform expansion toward AliExpress-scale e-commerce.
> Generated: 2026-02-24
> Current state: 12 microservices operational

---

## Current Platform (12 Services)

| Service | Domain |
|---------|--------|
| product-service | Catalog, categories, variations, images, slug management |
| order-service | Order creation, vendor sub-orders, status tracking, status history |
| cart-service | Cart CRUD, checkout with coupon/preview, idempotency |
| customer-service | Registration (OAuth + manual), profiles, address management |
| vendor-service | Vendor CRUD, membership, lifecycle, stop/resume orders |
| promotion-service | Promotions (6 benefit types), coupons, budget/burn, two-phase reservation, analytics |
| wishlist-service | Wishlist CRUD |
| poster-service | Banner/poster management with placements, scheduling, image upload |
| admin-service | Admin BFF, RBAC scoping, orchestration, Keycloak user search |
| access-service | Fine-grained platform/vendor staff permissions, access audit log |
| api-gateway | JWT auth (Keycloak), routing, idempotency (Redis), CORS, header forwarding |
| discovery-server | Eureka service registry for load-balanced routing |

---

## Tier 1 — Critical (Cannot Operate Without)

### 1.1 payment-service

**Why:** No payment processing exists. Orders are created but no money changes hands.

**Scope:**
- Payment intent creation and lifecycle (pending → authorized → captured → failed)
- Provider integration (Stripe, PayPal, etc.) behind an adapter pattern
- Refund processing (full and partial)
- Vendor payout/settlement — split payments to vendors minus platform commission
- Payment method management (saved cards, wallets)
- Webhook handling for async payment events from providers
- Idempotent payment creation to prevent double charges
- Payment receipts and transaction history

**Integration points:**
- order-service: payment triggers on checkout, refund triggers on cancellation/dispute
- notification-service: payment confirmation/failure emails
- dispute-service: refund execution on dispute resolution
- admin-service: payment dashboard, manual refund capability

**Backend DTOs (suggested):**
- `CreatePaymentIntentRequest`: orderId, amount, currency, paymentMethod, returnUrl
- `PaymentResponse`: id, orderId, status, amount, currency, provider, providerRef, createdAt
- `RefundRequest`: paymentId, amount, reason
- `VendorPayoutRequest`: vendorId, periodStart, periodEnd

---

### 1.2 inventory-service

**Why:** No stock tracking. Products can be oversold. No warehouse concept.

**Scope:**
- Stock levels per product/variation per warehouse location
- Stock reservation on checkout (two-phase: reserve → commit/release)
- Stock decrement on order confirmation, increment on cancellation/return
- Out-of-stock enforcement — block add-to-cart and checkout for zero-stock items
- Low-stock alerts and thresholds
- Bulk stock import/update (CSV/API)
- Warehouse/fulfillment location management
- Stock transfer between warehouses
- Stock audit trail (who changed what, when)

**Integration points:**
- cart-service: stock check on add-to-cart and checkout
- order-service: reserve on order creation, commit on payment success, release on cancellation
- product-service: stock count displayed on product pages
- notification-service: low-stock alerts to vendors
- admin-service: inventory dashboard

**Backend DTOs (suggested):**
- `StockReservationRequest`: orderId, items[{productId, variationId, quantity}]
- `StockLevelResponse`: productId, variationId, warehouseId, available, reserved, total
- `BulkStockUpdateRequest`: items[{productId, variationId, warehouseId, quantity, operation}]

---

### 1.3 notification-service

**Why:** No emails, SMS, or push notifications. Users get no order updates.

**Scope:**
- Multi-channel delivery: email (SMTP/SES), SMS (Twilio/SNS), push notifications (FCM/APNs)
- Template engine with variable substitution (order details, customer name, tracking links)
- Event-driven architecture — listens to domain events from other services
- Notification preferences per customer (opt-in/out per channel per event type)
- Delivery tracking and retry with exponential backoff
- Batch/bulk notifications for promotions and marketing
- Admin notification management (create/edit templates, view delivery stats)

**Event triggers:**
- Order created, confirmed, shipped, delivered, cancelled
- Payment successful, failed, refunded
- Shipping label created, package in transit, delivered
- Promotion launched, coupon about to expire
- Account verification, password reset
- Low stock alert (vendor), new review (vendor)
- Dispute opened, resolved

**Integration points:**
- All services publish events to a message broker (Kafka/RabbitMQ)
- notification-service consumes events and dispatches notifications

---

### 1.4 shipping-service

**Why:** No shipping rates, carrier integration, tracking, or label generation.

**Scope:**
- Shipping rate calculation based on weight, dimensions, origin, destination
- Carrier integration (FedEx, UPS, DHL, local carriers) behind adapter pattern
- Shipping label generation and PDF download
- Shipment tracking with status updates (picked up, in transit, out for delivery, delivered)
- Tracking webhook ingestion from carriers
- Shipping zone and rate rule management
- Free shipping threshold configuration (integrates with promotion-service)
- Multi-package support for large orders
- Estimated delivery date calculation
- Return shipping label generation

**Integration points:**
- cart-service: shipping rate display at checkout, preview shipping cost
- order-service: shipment creation on order confirmation, status sync
- notification-service: shipping status update emails/push
- promotion-service: free shipping promotion validation

**Backend DTOs (suggested):**
- `ShippingRateRequest`: originAddress, destinationAddress, items[{weight, dimensions, quantity}]
- `ShippingRateResponse`: carrier, service, rate, estimatedDays, labelAvailable
- `CreateShipmentRequest`: orderId, carrierId, serviceLevel, packages[{weight, dimensions, items}]
- `TrackingEvent`: shipmentId, status, location, timestamp, carrierRef

---

## Tier 2 — Expected by Users

### 2.1 review-service

**Why:** No product reviews, ratings, or vendor reputation. Critical for buyer trust and conversion.

**Scope:**
- Product reviews with star rating (1-5), text, and photo/video uploads
- Vendor reputation score (aggregated from all product reviews)
- Review moderation (auto-flag profanity, admin approve/reject)
- Verified purchase badge (only buyers who ordered can review)
- Helpful vote system (was this review helpful?)
- Review response by vendor
- Review summary/statistics per product (average rating, distribution, total count)
- Sort/filter reviews (most recent, most helpful, by rating)
- Prevent duplicate reviews (one review per order item per customer)

**Integration points:**
- order-service: verify purchase before allowing review
- product-service: display average rating on product cards
- vendor-service: aggregate vendor reputation
- notification-service: "review your purchase" email after delivery
- admin-service: moderation dashboard

**Backend DTOs (suggested):**
- `CreateReviewRequest`: orderItemId, productId, rating, title, body, imageKeys[]
- `ReviewResponse`: id, productId, customerId, customerName, rating, title, body, images[], vendorReply, helpful, verified, status, createdAt
- `ProductReviewSummary`: productId, averageRating, totalReviews, distribution{1: count, 2: count, ...}

---

### 2.2 search-service

**Why:** Only basic DB filtering exists. Full-text search, faceting, and relevance ranking are essential at scale.

**Scope:**
- Elasticsearch (or OpenSearch) backed full-text product search
- Autocomplete/typeahead suggestions
- Faceted search (filter by category, price range, brand, rating, vendor, attributes)
- Relevance ranking with boost factors (sales velocity, rating, recency)
- Synonym and typo tolerance
- Search analytics (popular queries, zero-result queries, click-through rates)
- Real-time index sync from product-service (event-driven or CDC)
- Category browsing with faceted navigation
- Recently viewed products per customer
- Trending/popular products

**Integration points:**
- product-service: index products on create/update/delete events
- review-service: include average rating in search index
- inventory-service: filter out-of-stock from results (or rank lower)
- promotion-service: boost promoted products in results
- analytics-service: feed search analytics data

**Tech stack suggestion:** Elasticsearch 8.x with Spring Data Elasticsearch, Kafka for CDC

---

### 2.3 dispute-service

**Why:** No returns, refunds, or buyer protection. Buyers have no recourse after purchase.

**Scope:**
- Dispute creation by buyer (reason: not received, not as described, damaged, wrong item)
- Evidence submission (photos, screenshots, tracking info) by both buyer and seller
- Dispute status workflow: OPENED → SELLER_RESPONSE → ESCALATED → RESOLVED
- Auto-escalation after seller response timeout
- Resolution outcomes: full refund, partial refund, return & refund, rejected
- Return merchandise authorization (RMA) generation
- Return shipping tracking
- Platform mediation for escalated disputes
- Dispute time limits (e.g., 15 days after delivery to open)
- Dispute history and statistics per vendor (for vendor health scoring)

**Integration points:**
- order-service: dispute can only be opened for delivered/completed orders
- payment-service: refund execution on resolution
- shipping-service: return label generation
- notification-service: dispute status updates to both parties
- vendor-service: vendor dispute rate affects reputation
- admin-service: dispute mediation dashboard

---

### 2.4 messaging-service

**Why:** No buyer-seller communication. AliExpress has built-in chat for pre-sale questions and post-sale support.

**Scope:**
- 1:1 conversation threads between buyer and seller
- Message types: text, image, product link, order reference
- Pre-sale inquiries (linked to product)
- Post-sale support (linked to order)
- Read receipts and typing indicators (WebSocket)
- Message history with pagination
- Conversation list with unread count
- Auto-translation for cross-language communication (future)
- Spam/abuse detection and reporting
- Admin monitoring capability

**Integration points:**
- notification-service: push notification on new message
- order-service: link conversations to orders
- product-service: link conversations to products
- admin-service: message monitoring/moderation

**Tech stack suggestion:** WebSocket (Spring WebFlux), message persistence in MongoDB or PostgreSQL, Redis for presence/typing indicators

---

## Tier 3 — Competitive Advantage

### 3.1 recommendation-service

**Why:** Personalized product recommendations drive significant revenue on marketplace platforms.

**Scope:**
- "You may also like" — collaborative filtering based on purchase history
- "Frequently bought together" — association rules from order data
- "Customers who viewed X also viewed Y" — browsing behavior
- Personalized homepage feed
- "Recently viewed" products
- "Based on your wishlist" recommendations
- Category-level trending products
- New arrival highlights per followed vendor
- A/B testing framework for recommendation algorithms

**Integration points:**
- order-service: purchase history data
- product-service: product catalog and categories
- search-service: search/click behavior
- wishlist-service: interest signals
- analytics-service: recommendation performance metrics

**Tech stack suggestion:** Apache Spark or simple collaborative filtering initially, graduate to ML models

---

### 3.2 analytics-service

**Why:** No platform-wide reporting. Vendors and admins need sales dashboards.

**Scope:**
- Platform dashboard: GMV, order count, revenue, active users, conversion rate
- Vendor dashboard: sales, top products, revenue, order fulfillment rate
- Product analytics: views, add-to-cart rate, conversion rate, revenue
- Customer analytics: cohort analysis, lifetime value, repeat purchase rate
- Time-series data with configurable date ranges
- Export to CSV/PDF
- Real-time vs batch analytics
- Anomaly detection (sudden drop in orders, spike in cancellations)

**Integration points:**
- All services emit analytics events
- Event-driven ingestion via message broker
- Materialized views or OLAP datastore (ClickHouse, TimescaleDB)

---

### 3.3 seller-onboarding-service

**Why:** Vendor creation is admin-only. No self-service application and approval pipeline.

**Scope:**
- Public seller registration form (business info, tax ID, bank details)
- Document upload (business license, identity verification)
- Multi-step application review workflow: SUBMITTED → UNDER_REVIEW → APPROVED/REJECTED
- Automated checks (business registry validation, sanctions screening)
- Onboarding checklist (add first product, set up shipping, configure payment)
- Seller agreement / terms acceptance
- Tiered seller levels based on performance (new, verified, premium)
- Seller dashboard with setup progress

**Integration points:**
- vendor-service: auto-create vendor on approval
- access-service: auto-grant vendor_admin role
- notification-service: application status updates
- payment-service: bank account verification for payouts

---

### 3.4 tax-service

**Why:** No tax calculation. Required for legal compliance in most jurisdictions.

**Scope:**
- Tax rate lookup by jurisdiction (country, state/province, city)
- Product tax category mapping (standard, reduced, exempt, digital goods)
- Tax calculation per line item and order total
- Tax-inclusive vs tax-exclusive pricing modes
- VAT/GST handling for international orders
- Tax exemption certificate management
- Tax reporting and export for filing
- Integration with tax providers (Avalara, TaxJar) behind adapter pattern

**Integration points:**
- cart-service: calculate tax at checkout preview
- order-service: finalize tax on order creation
- payment-service: tax amounts in payment records
- invoice generation

---

## Tier 4 — Scale & Internationalization

### 4.1 Multi-Currency Support

**Where:** Across product-service, cart-service, order-service, payment-service

**Scope:**
- Product prices stored in base currency with real-time conversion
- Currency selection per customer session
- Exchange rate management (manual or API-driven: Open Exchange Rates, Fixer.io)
- Display prices in customer's local currency
- Settle payments in seller's preferred currency
- Currency conversion fees and transparency

---

### 4.2 Localization / i18n

**Where:** Frontend + content services

**Scope:**
- Multi-language UI (frontend i18n with next-intl or similar)
- Product titles/descriptions in multiple languages
- Category names translated
- Email templates per language
- Right-to-left (RTL) layout support
- Locale-aware date, number, and currency formatting

---

### 4.3 CMS Service

**Why:** Only banners (poster-service) exist. No general content management.

**Scope:**
- Landing page builder (marketing campaigns, seasonal events)
- Static content pages (about, FAQ, terms, privacy policy)
- Content scheduling (publish/unpublish by date)
- Rich text editor with image embedding
- SEO metadata management (title, description, OG tags)
- Content versioning and draft/publish workflow

---

### 4.4 Flash Sales / Daily Deals

**Why:** No time-limited deal infrastructure surfaced to customers.

**Scope:**
- Flash sale creation with start/end time, limited quantity
- Countdown timer display on product pages and homepage
- Queue/fairness system for high-demand items
- Stock reservation specific to flash sale allocation
- Deal-of-the-day scheduling
- Flash sale landing page
- Push notification for upcoming deals (wishlist-based)

**Integration points:**
- promotion-service: flash sale is a promotion type with tight time window
- inventory-service: reserved flash sale stock allocation
- notification-service: deal alerts
- search-service: boost flash sale items

---

### 4.5 Customer-Facing Promotion Discovery

**Why:** Customers can't browse active promotions or validate coupons before checkout.

**Scope:**
- Public promotions listing page (active, non-coupon-required promotions)
- Coupon center — browse available coupons, claim/save to account
- "Promotions for you" — personalized based on cart/history
- Promotion badge on product cards (e.g., "20% OFF")
- Promotion details page with terms and conditions
- Saved coupons in customer account

**Integration points:**
- promotion-service: new public endpoints for browsable promotions
- product-service: promotion badge data on product responses
- customer-service: saved coupons per customer

---

### 4.6 Rate Limiting & Abuse Protection

**Where:** api-gateway + dedicated service

**Scope:**
- Request rate limiting per user/IP (token bucket or sliding window)
- Endpoint-specific limits (login attempts, checkout, review submission)
- Bot detection and CAPTCHA integration
- IP blocking and geo-restriction
- Account-level abuse scoring
- DDoS protection (CloudFlare/AWS Shield at infra level)

**Tech stack suggestion:** Redis-based rate limiter in gateway, Spring Cloud Gateway `RequestRateLimiter` filter

---

## Implementation Priority Order

| # | Service/Feature | Tier | Reason |
|---|-----------------|------|--------|
| 1 | payment-service | T1 | Cannot transact without payments |
| 2 | inventory-service | T1 | Overselling destroys trust |
| 3 | notification-service | T1 | Users expect order confirmation emails |
| 4 | shipping-service | T1 | Physical goods need delivery tracking |
| 5 | review-service | T2 | Trust and conversion driver |
| 6 | search-service | T2 | Essential once catalog grows beyond ~1000 products |
| 7 | dispute-service | T2 | Buyer protection is legally required in many jurisdictions |
| 8 | tax-service | T3 | Legal compliance for cross-border sales |
| 9 | analytics-service | T3 | Vendor retention and platform health |
| 10 | seller-onboarding-service | T3 | Scale vendor acquisition |
| 11 | messaging-service | T2 | Buyer-seller communication |
| 12 | recommendation-service | T3 | Revenue growth through personalization |
| 13 | Multi-currency | T4 | Required for international expansion |
| 14 | Localization | T4 | Market-specific growth |
| 15 | Flash sales | T4 | Marketing and engagement |
| 16 | CMS service | T4 | Marketing flexibility |
| 17 | Rate limiting | T4 | Operational safety at scale |
| 18 | Customer promo discovery | T4 | Marketing and engagement |
