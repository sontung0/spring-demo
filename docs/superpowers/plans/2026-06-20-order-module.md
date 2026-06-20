# Order Module Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a REST endpoint to create an order (unique client `code`, server-set status `NEW`, a list of SKU lines) that persists the order and publishes an externalized `OrderCreated` event to Kafka.

**Architecture:** A new Spring Modulith `order` module mirroring the existing `user` module: public API (`Order`, `OrderService`, `OrderCreated`) at the package root, and a package-private `internal/` sub-package for the controller, DTOs, and repository. Order creation saves the aggregate (order + child SKU rows) in one transaction, then publishes `OrderCreated`, which is externalized to Kafka via the Modulith outbox (already enabled).

**Tech Stack:** Java 25, Spring Boot 4.1.0, Spring Modulith 2.1.0, Spring Data JPA, Jakarta Validation, Kafka (via Modulith event externalization), JUnit 5 + Mockito + MockMvc (from `spring-boot-starter-webmvc-test`).

## Global Constraints

- Java version: 25 (`pom.xml` `<java.version>25</java.version>`).
- Entity style matches `User`: `public` fields, no getters/setters, `@GeneratedValue(strategy = GenerationType.IDENTITY)`.
- Module boundary: controller, DTOs, repository live in `order/internal/`; `Order`, `OrderService`, `OrderCreated` live in `order/`.
- Event externalization target format matches `UserCreated`: `@Externalized("orders::#{#this.order.id}")`.
- Use `@MockitoBean` (not the removed `@MockBean`) — Spring Boot 4.1.
- No new dependencies. `spring-modulith-starter-test` is NOT on the classpath; do not use `@ApplicationModuleTest` / `PublishedEvents`.
- Status of a newly created order is always `OrderStatus.NEW`; `status` is never read from the request.
- Base package: `com.example.demo`.

---

### Task 1: Domain entities and status enum

**Files:**
- Create: `src/main/java/com/example/demo/order/OrderStatus.java`
- Create: `src/main/java/com/example/demo/order/Order.java`
- Create: `src/main/java/com/example/demo/order/OrderSku.java`

**Interfaces:**
- Consumes: nothing.
- Produces:
  - `enum OrderStatus { NEW }`
  - `class Order` with public fields: `Integer id`, `String code`, `OrderStatus status`, `List<OrderSku> skus`; plus `void addSku(OrderSku sku)` that adds to the list and sets `sku.order = this`.
  - `class OrderSku` with public fields: `Integer id`, `Order order`, `String code`, `Integer quantity`.

- [ ] **Step 1: Create the status enum**

`src/main/java/com/example/demo/order/OrderStatus.java`:

```java
package com.example.demo.order;

public enum OrderStatus {
    NEW
}
```

- [ ] **Step 2: Create the OrderSku entity**

`src/main/java/com/example/demo/order/OrderSku.java`:

```java
package com.example.demo.order;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "order_skus")
public class OrderSku {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Integer id;

  @ManyToOne
  @JoinColumn(name = "order_id")
  public Order order;

  public String code;

  public Integer quantity;
}
```

- [ ] **Step 3: Create the Order entity**

`src/main/java/com/example/demo/order/Order.java`:

```java
package com.example.demo.order;

import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

@Entity
@Table(name = "orders")
public class Order {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  public Integer id;

  @Column(unique = true)
  public String code;

  public OrderStatus status;

  @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
  public List<OrderSku> skus = new ArrayList<>();

  public void addSku(OrderSku sku) {
    sku.order = this;
    this.skus.add(sku);
  }
}
```

- [ ] **Step 4: Compile to verify the entities are valid**

Run: `./mvnw -q compile`
Expected: BUILD SUCCESS (no compile errors).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/demo/order/OrderStatus.java src/main/java/com/example/demo/order/Order.java src/main/java/com/example/demo/order/OrderSku.java
git commit -m "feat(order): add Order/OrderSku entities and OrderStatus enum"
```

---

### Task 2: Externalized OrderCreated event

**Files:**
- Create: `src/main/java/com/example/demo/order/OrderCreated.java`

**Interfaces:**
- Consumes: `Order` (Task 1).
- Produces: `record OrderCreated(Order order)` with accessor `order()`, externalized to Kafka target `orders` keyed by `order.id`.

- [ ] **Step 1: Create the event record**

`src/main/java/com/example/demo/order/OrderCreated.java`:

```java
package com.example.demo.order;

import org.springframework.modulith.events.Externalized;

@Externalized("orders::#{#this.order.id}")
public record OrderCreated(Order order) {
}
```

- [ ] **Step 2: Compile to verify**

Run: `./mvnw -q compile`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/example/demo/order/OrderCreated.java
git commit -m "feat(order): add externalized OrderCreated event"
```

---

### Task 3: Repository and duplicate-code exception

**Files:**
- Create: `src/main/java/com/example/demo/order/internal/OrderRepository.java`
- Create: `src/main/java/com/example/demo/order/DuplicateOrderCodeException.java`

**Interfaces:**
- Consumes: `Order` (Task 1).
- Produces:
  - `interface OrderRepository extends CrudRepository<Order, Integer>` with `boolean existsByCode(String code)` and (inherited) `<S extends Order> S save(S entity)`.
  - `class DuplicateOrderCodeException extends RuntimeException` with constructor `DuplicateOrderCodeException(String code)` producing message `Order code already exists: <code>`.

- [ ] **Step 1: Create the repository**

`src/main/java/com/example/demo/order/internal/OrderRepository.java`:

```java
package com.example.demo.order.internal;

import org.springframework.data.repository.CrudRepository;

import com.example.demo.order.Order;

public interface OrderRepository extends CrudRepository<Order, Integer> {
  boolean existsByCode(String code);
}
```

- [ ] **Step 2: Create the exception**

`src/main/java/com/example/demo/order/DuplicateOrderCodeException.java`:

```java
package com.example.demo.order;

public class DuplicateOrderCodeException extends RuntimeException {
  public DuplicateOrderCodeException(String code) {
    super("Order code already exists: " + code);
  }
}
```

- [ ] **Step 3: Compile to verify**

Run: `./mvnw -q compile`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/example/demo/order/internal/OrderRepository.java src/main/java/com/example/demo/order/DuplicateOrderCodeException.java
git commit -m "feat(order): add OrderRepository and DuplicateOrderCodeException"
```

---

### Task 4: OrderService (TDD)

**Files:**
- Create: `src/main/java/com/example/demo/order/OrderService.java`
- Test: `src/test/java/com/example/demo/order/OrderServiceTest.java`

**Interfaces:**
- Consumes: `OrderRepository` (Task 3), `DuplicateOrderCodeException` (Task 3), `Order`/`OrderSku`/`OrderStatus` (Task 1), `OrderCreated` (Task 2), Spring `ApplicationEventPublisher`.
- Produces:
  - `record SkuLine(String code, Integer quantity)` — a nested public record on `OrderService`, used as the service-layer input type so the controller's DTO does not leak into the module's public API.
  - `Order createOrder(String code, List<OrderService.SkuLine> skus)` on `OrderService` — checks uniqueness (throws `DuplicateOrderCodeException` if `existsByCode`), builds an `Order` with status `NEW` and child `OrderSku` rows, saves it, publishes `OrderCreated`, returns the saved `Order`.

- [ ] **Step 1: Write the failing test**

`src/test/java/com/example/demo/order/OrderServiceTest.java`:

```java
package com.example.demo.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import com.example.demo.order.internal.OrderRepository;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

  @Mock
  OrderRepository orderRepository;

  @Mock
  ApplicationEventPublisher eventPublisher;

  @InjectMocks
  OrderService orderService;

  @Test
  void createOrder_persistsOrderWithNewStatusAndSkus_andPublishesEvent() {
    when(orderRepository.existsByCode("ORD-1")).thenReturn(false);
    when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

    Order result = orderService.createOrder(
        "ORD-1",
        List.of(new OrderService.SkuLine("SKU-A", 2), new OrderService.SkuLine("SKU-B", 1)));

    assertThat(result.code).isEqualTo("ORD-1");
    assertThat(result.status).isEqualTo(OrderStatus.NEW);
    assertThat(result.skus).hasSize(2);
    assertThat(result.skus).allSatisfy(s -> assertThat(s.order).isSameAs(result));
    assertThat(result.skus).extracting(s -> s.code).containsExactly("SKU-A", "SKU-B");
    assertThat(result.skus).extracting(s -> s.quantity).containsExactly(2, 1);

    verify(orderRepository).save(any(Order.class));

    ArgumentCaptor<OrderCreated> captor = ArgumentCaptor.forClass(OrderCreated.class);
    verify(eventPublisher).publishEvent(captor.capture());
    assertThat(captor.getValue().order()).isSameAs(result);
  }

  @Test
  void createOrder_throwsWhenCodeAlreadyExists_andDoesNotSaveOrPublish() {
    when(orderRepository.existsByCode("DUP")).thenReturn(true);

    assertThatThrownBy(() -> orderService.createOrder("DUP", List.of(new OrderService.SkuLine("SKU-A", 1))))
        .isInstanceOf(DuplicateOrderCodeException.class);

    verify(orderRepository, never()).save(any());
    verify(eventPublisher, never()).publishEvent(any());
  }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw -q -Dtest=OrderServiceTest test`
Expected: FAIL — compilation error / `OrderService` (and `OrderService.SkuLine`) do not exist.

- [ ] **Step 3: Write the minimal implementation**

`src/main/java/com/example/demo/order/OrderService.java`:

```java
package com.example.demo.order;

import java.util.List;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.order.internal.OrderRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class OrderService {
    private final OrderRepository orderRepository;
    private final ApplicationEventPublisher eventPublisher;

    public record SkuLine(String code, Integer quantity) {
    }

    @Transactional
    public Order createOrder(String code, List<SkuLine> skus) {
        if (orderRepository.existsByCode(code)) {
            throw new DuplicateOrderCodeException(code);
        }

        Order order = new Order();
        order.code = code;
        order.status = OrderStatus.NEW;
        for (SkuLine line : skus) {
            OrderSku sku = new OrderSku();
            sku.code = line.code();
            sku.quantity = line.quantity();
            order.addSku(sku);
        }

        orderRepository.save(order);

        eventPublisher.publishEvent(new OrderCreated(order));

        return order;
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./mvnw -q -Dtest=OrderServiceTest test`
Expected: PASS (2 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/demo/order/OrderService.java src/test/java/com/example/demo/order/OrderServiceTest.java
git commit -m "feat(order): add OrderService with createOrder + tests"
```

---

### Task 5: Request/response DTOs

**Files:**
- Create: `src/main/java/com/example/demo/order/internal/CreateOrderRequest.java`
- Create: `src/main/java/com/example/demo/order/internal/CreateOrderResponse.java`

**Interfaces:**
- Consumes: `Order`, `OrderSku`, `OrderStatus` (Task 1).
- Produces:
  - `record CreateOrderRequest(String code, List<SkuLine> skus)` with nested `record SkuLine(String code, Integer quantity)`, both validated.
  - `record CreateOrderResponse(Integer id, String code, OrderStatus status, List<SkuView> skus)` with nested `record SkuView(String code, Integer quantity)` and a constructor `CreateOrderResponse(Order order)`.

- [ ] **Step 1: Create the request DTO**

`src/main/java/com/example/demo/order/internal/CreateOrderRequest.java`:

```java
package com.example.demo.order.internal;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

public record CreateOrderRequest(
    @NotBlank(message = "Order code is required") String code,
    @NotEmpty(message = "SKUs list cannot be empty") @Valid List<@Valid @NotNull(message = "SKU cannot be null") SkuLine> skus
) {
    public record SkuLine(
        @NotBlank(message = "SKU code is required") String code,
        @NotNull(message = "Quantity is required") @Min(value = 1, message = "Quantity must be at least 1") Integer quantity
    ) {
    }
}
```

- [ ] **Step 2: Create the response DTO**

`src/main/java/com/example/demo/order/internal/CreateOrderResponse.java`:

```java
package com.example.demo.order.internal;

import java.util.List;

import com.example.demo.order.Order;
import com.example.demo.order.OrderStatus;

public record CreateOrderResponse(
    Integer id,
    String code,
    OrderStatus status,
    List<SkuView> skus
) {
    public record SkuView(String code, Integer quantity) {
    }

    public CreateOrderResponse(Order order) {
        this(
            order.id,
            order.code,
            order.status,
            order.skus.stream().map(s -> new SkuView(s.code, s.quantity)).toList()
        );
    }
}
```

- [ ] **Step 3: Compile to verify**

Run: `./mvnw -q compile`
Expected: BUILD SUCCESS.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/example/demo/order/internal/CreateOrderRequest.java src/main/java/com/example/demo/order/internal/CreateOrderResponse.java
git commit -m "feat(order): add create order request/response DTOs"
```

---

### Task 6: Controller (TDD via web slice)

**Files:**
- Create: `src/main/java/com/example/demo/order/internal/OrderController.java`
- Test: `src/test/java/com/example/demo/order/internal/OrderControllerTest.java`

**Interfaces:**
- Consumes: `OrderService` + `OrderService.SkuLine` (Task 4), `CreateOrderRequest` (Task 5), `CreateOrderResponse` (Task 5), `Order`/`OrderSku`/`OrderStatus` (Task 1), `DuplicateOrderCodeException` (Task 3).
- Produces: `OrderController` with `POST /orders` → `200` + `CreateOrderResponse`; maps `CreateOrderRequest.SkuLine` → `OrderService.SkuLine` before delegating.

- [ ] **Step 1: Write the failing test**

`src/test/java/com/example/demo/order/internal/OrderControllerTest.java`:

```java
package com.example.demo.order.internal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.example.demo.order.DuplicateOrderCodeException;
import com.example.demo.order.Order;
import com.example.demo.order.OrderSku;
import com.example.demo.order.OrderService;
import com.example.demo.order.OrderStatus;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

  @Autowired
  MockMvc mockMvc;

  @MockitoBean
  OrderService orderService;

  @Test
  void create_returns200WithCreatedOrder() throws Exception {
    Order saved = new Order();
    saved.id = 1;
    saved.code = "ORD-1";
    saved.status = OrderStatus.NEW;
    OrderSku sku = new OrderSku();
    sku.code = "SKU-A";
    sku.quantity = 2;
    saved.addSku(sku);

    when(orderService.createOrder(eq("ORD-1"), anyList())).thenReturn(saved);

    mockMvc.perform(post("/orders")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"code":"ORD-1","skus":[{"code":"SKU-A","quantity":2}]}
                """))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(1))
        .andExpect(jsonPath("$.code").value("ORD-1"))
        .andExpect(jsonPath("$.status").value("NEW"))
        .andExpect(jsonPath("$.skus[0].code").value("SKU-A"))
        .andExpect(jsonPath("$.skus[0].quantity").value(2));
  }

  @Test
  void create_returns400WhenSkusEmpty() throws Exception {
    mockMvc.perform(post("/orders")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"code":"ORD-1","skus":[]}
                """))
        .andExpect(status().isBadRequest());
  }

  @Test
  void create_returns400WhenQuantityZero() throws Exception {
    mockMvc.perform(post("/orders")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"code":"ORD-1","skus":[{"code":"SKU-A","quantity":0}]}
                """))
        .andExpect(status().isBadRequest());
  }

  @Test
  void create_returns409WhenCodeDuplicate() throws Exception {
    when(orderService.createOrder(eq("DUP"), anyList()))
        .thenThrow(new DuplicateOrderCodeException("DUP"));

    mockMvc.perform(post("/orders")
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"code":"DUP","skus":[{"code":"SKU-A","quantity":1}]}
                """))
        .andExpect(status().isConflict());
  }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./mvnw -q -Dtest=OrderControllerTest test`
Expected: FAIL — `OrderController` does not exist (and the 409 test would fail without the advice from Task 7).

- [ ] **Step 3: Write the controller**

`src/main/java/com/example/demo/order/internal/OrderController.java`:

```java
package com.example.demo.order.internal;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.order.Order;
import com.example.demo.order.OrderService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping(path = "/orders")
@RequiredArgsConstructor
public class OrderController {
  private final OrderService orderService;

  @PostMapping
  public ResponseEntity<CreateOrderResponse> create(@Valid @RequestBody CreateOrderRequest request) {
    var skuLines = request.skus().stream()
        .map(s -> new OrderService.SkuLine(s.code(), s.quantity()))
        .toList();

    Order order = orderService.createOrder(request.code(), skuLines);

    return ResponseEntity.ok(new CreateOrderResponse(order));
  }
}
```

- [ ] **Step 4: Run the test (200 + 400 cases pass; 409 still fails)**

Run: `./mvnw -q -Dtest=OrderControllerTest test`
Expected: `create_returns200WithCreatedOrder`, `create_returns400WhenSkusEmpty`, `create_returns400WhenQuantityZero` PASS; `create_returns409WhenCodeDuplicate` FAILS (duplicate maps to 500 until Task 7). This is expected — the advice is added next.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/example/demo/order/internal/OrderController.java src/test/java/com/example/demo/order/internal/OrderControllerTest.java
git commit -m "feat(order): add OrderController POST /orders"
```

---

### Task 7: Exception handler for 409

**Files:**
- Create: `src/main/java/com/example/demo/order/internal/OrderExceptionHandler.java`

**Interfaces:**
- Consumes: `DuplicateOrderCodeException` (Task 3).
- Produces: a `@RestControllerAdvice` that maps `DuplicateOrderCodeException` to HTTP `409` with a JSON body `{ "error": "<message>" }`.

- [ ] **Step 1: Create the advice**

`src/main/java/com/example/demo/order/internal/OrderExceptionHandler.java`:

```java
package com.example.demo.order.internal;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.example.demo.order.DuplicateOrderCodeException;

@RestControllerAdvice
public class OrderExceptionHandler {

  @ExceptionHandler(DuplicateOrderCodeException.class)
  public ResponseEntity<Map<String, String>> handleDuplicate(DuplicateOrderCodeException ex) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", ex.getMessage()));
  }
}
```

- [ ] **Step 2: Run the controller test — all four cases pass now**

The `@WebMvcTest(OrderController.class)` slice does not load `@RestControllerAdvice` beans by default. Add the advice to the slice by annotating the test class. Apply this edit to `src/test/java/com/example/demo/order/internal/OrderControllerTest.java`:

Change the annotation line:

```java
@WebMvcTest(OrderController.class)
```

to:

```java
@WebMvcTest(controllers = OrderController.class)
@Import(OrderExceptionHandler.class)
```

and add the import near the other imports:

```java
import org.springframework.context.annotation.Import;
```

- [ ] **Step 3: Run the controller test**

Run: `./mvnw -q -Dtest=OrderControllerTest test`
Expected: PASS (all 4 tests, including `create_returns409WhenCodeDuplicate`).

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/example/demo/order/internal/OrderExceptionHandler.java src/test/java/com/example/demo/order/internal/OrderControllerTest.java
git commit -m "feat(order): map DuplicateOrderCodeException to 409"
```

---

### Task 8: Full build verification

**Files:** none (verification only).

**Interfaces:**
- Consumes: everything from Tasks 1–7.
- Produces: a green full build.

- [ ] **Step 1: Run the complete test suite**

Run: `./mvnw -q test`
Expected: BUILD SUCCESS — `OrderServiceTest` (2), `OrderControllerTest` (4), and the existing `DemoApplicationTests.contextLoads` all pass.

> Note: `DemoApplicationTests` is a full `@SpringBootTest`. If it already failed before this work (e.g. needs Kafka/MySQL via docker-compose), that is pre-existing and out of scope — confirm the order tests pass with `./mvnw -q -Dtest=OrderServiceTest,OrderControllerTest test` and note the pre-existing failure rather than fixing it here.

- [ ] **Step 2: Final commit (if any uncommitted changes remain)**

```bash
git status
# if clean, nothing to do
```

---

## Self-Review

**Spec coverage:**
- Endpoint `POST /orders` with `code` + `skus[{code,quantity}]` → Tasks 5, 6.
- SKUs stored as-is → Tasks 1, 4.
- Status server-set to `NEW`, not from request → Task 4 (`OrderStatus.NEW`); request DTO (Task 5) has no `status` field.
- Unique code, 409 on duplicate → Tasks 3 (`existsByCode` + exception), 4 (throw), 7 (409 mapping).
- `OrderCreated` externalized to Kafka like `UserCreated` → Task 2.
- Validation (blank code, empty skus, blank sku code, quantity < 1 → 400) → Task 5 constraints, Task 6 tests.
- Two tables `orders`/`order_skus`, cascade children, unique constraint on code → Task 1.
- Tests: service persist+publish, duplicate throws, controller 200/400/409 → Tasks 4, 6, 7.
- Module `internal/` boundary; public API `Order`/`OrderService`/`OrderCreated` → all tasks place files accordingly.

**Placeholder scan:** No TBD/TODO; every code step contains complete code; no "add error handling" hand-waves.

**Type consistency:** `OrderService.SkuLine(String, Integer)` is the single service input type, defined in Task 4 and consumed by the controller in Task 6. `CreateOrderRequest.SkuLine` (Task 5) is mapped to `OrderService.SkuLine` in the controller. `existsByCode`, `createOrder`, `OrderStatus.NEW`, `addSku`, and `OrderCreated.order()` are used with consistent signatures across tasks.