# Order module — design

**Date:** 2026-06-20
**Status:** Approved

## Goal

Add a REST API to create an order. An order has a client-supplied unique
`code`, a server-set `status`, and a list of SKU lines (`code` + `quantity`).
Creating an order publishes an externalized `OrderCreated` event to Kafka,
mirroring the existing `user` → `noti` event flow.

## Request / response

```
POST /orders
Content-Type: application/json

{
  "code": "ORD-1",
  "skus": [
    { "code": "SKU-A", "quantity": 2 },
    { "code": "SKU-B", "quantity": 1 }
  ]
}
```

`status` is NOT accepted from the client. New orders are always created with
status `NEW`.

Success → `200 OK`:

```
{
  "id": 1,
  "code": "ORD-1",
  "status": "NEW",
  "skus": [
    { "code": "SKU-A", "quantity": 2 },
    { "code": "SKU-B", "quantity": 1 }
  ]
}
```

## Decisions

| Topic | Decision |
|-------|----------|
| SKUs | Stored as-is. No validation against any catalog/product module. |
| Status | Server-set to `NEW` on creation. Not part of the request. Modeled as an enum with a single value `NEW` for now. |
| Order code | Client-provided, must be unique. Duplicate → `409 Conflict`. |
| Event | Publish `OrderCreated`, marked `@Externalized` to Kafka, exactly like `UserCreated`. |
| Listener | None added in this module. The event is externalized to Kafka; in-process listeners are out of scope. |

## Module structure

Mirrors the existing `user` module, including the `internal/` boundary.

```
order/
├── Order.java              # @Entity, public fields, IDENTITY id (matches User style)
├── OrderSku.java           # @Entity child rows: code + quantity, @ManyToOne back to Order
├── OrderStatus.java        # enum { NEW }
├── OrderCreated.java       # @Externalized("orders::#{#this.order.id}") record
├── OrderService.java       # @Service, @Transactional createOrder(...)
└── internal/
    ├── OrderController.java     # @RestController @RequestMapping("/orders")
    ├── CreateOrderRequest.java  # record(code, List<SkuLine>) + nested SkuLine(code, quantity)
    ├── CreateOrderResponse.java # record built from Order
    └── OrderRepository.java     # extends CrudRepository, existsByCode(...)
```

Public API of the module: `Order`, `OrderService`, `OrderCreated`.
Package-private (in `internal/`): controller, DTOs, repository.

## Data flow

```
POST /orders
        │
        ▼
OrderController.create(@Valid CreateOrderRequest)
        │  delegates to
        ▼
OrderService.createOrder(code, skuLines)   @Transactional
        │  1. if orderRepository.existsByCode(code) → throw DuplicateOrderCodeException
        │  2. build Order(code, status=NEW) + OrderSku children
        │  3. orderRepository.save(order)
        │  4. eventPublisher.publishEvent(new OrderCreated(order))
        ▼
returns Order → 200 OK { id, code, status:"NEW", skus:[...] }
        │
        ▼
OrderCreated  ──@Externalized──►  Kafka topic "orders" (key = order.id)
                                   via Modulith outbox (EVENT_PUBLICATION table)
```

This mirrors `UserService.createUser`: `save` → `publishEvent` → externalized
to Kafka through the Modulith outbox (already enabled via
`spring.modulith.events.externalization.enabled=true`).

## Error handling

- **Duplicate code** → `DuplicateOrderCodeException`, mapped to `409 Conflict`.
  A new `@RestControllerAdvice` handler is added (the codebase has none yet)
  so the conflict returns a clean JSON body instead of a 500.
- **Validation** (`@Valid`, `jakarta.validation`, already on the classpath):
  - blank `code` → `400`
  - empty `skus` list → `400`
  - blank sku `code` → `400`
  - `quantity < 1` → `400`

## Persistence

Two tables, created by Hibernate (`spring.jpa.hibernate.ddl-auto=update`):

```
orders                      order_skus
──────                      ──────────
id     (PK, IDENTITY)       id        (PK, IDENTITY)
code   (unique)             order_id  (FK → orders.id)
status (varchar, "NEW")     code
                            quantity
```

- `Order` holds `@OneToMany(cascade = ALL, orphanRemoval = true) List<OrderSku> skus`
  so saving the order cascades the children in one transaction.
- `code` has a unique constraint (`@Column(unique = true)`) as a DB-level
  backstop behind the `existsByCode` check.
- Field style matches `User`: public fields, minimal boilerplate.

## Testing

- `OrderServiceTest` — `createOrder` persists order + skus and publishes
  `OrderCreated` (Modulith `@ApplicationModuleTest` + `PublishedEvents`, or a
  mocked `ApplicationEventPublisher`).
- Duplicate code throws `DuplicateOrderCodeException`.
- `OrderController` web-layer test: valid request → 200; empty `skus` /
  `quantity = 0` → 400; duplicate code → 409.

## Out of scope

- SKU/catalog validation.
- Status transitions beyond `NEW` (no update/cancel endpoints).
- In-process `OrderCreated` listeners.
- Order read/list endpoints (only create).