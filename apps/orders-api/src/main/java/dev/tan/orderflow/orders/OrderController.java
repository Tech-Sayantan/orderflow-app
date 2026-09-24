package dev.tan.orderflow.orders;

import java.util.Map;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class OrderController {

    private final OrderService service;
    private final String version;

    public OrderController(OrderService service, @Value("${app.version}") String version) {
        this.service = service;
        this.version = version;
    }

    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public Order create(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @Valid @RequestBody CreateOrderRequest request) {
        return service.create(idempotencyKey, request);
    }

    @GetMapping("/orders/{id}")
    public Order get(@PathVariable UUID id) {
        return service.get(id);
    }

    @GetMapping("/version")
    public Map<String, String> version() {
        return Map.of("service", "orders-api", "version", version);
    }
}
