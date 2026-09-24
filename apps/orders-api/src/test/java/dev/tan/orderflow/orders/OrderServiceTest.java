package dev.tan.orderflow.orders;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderServiceTest {

    private OrderRepository repository;

    @BeforeEach
    void migrateFreshDatabase() {
        var dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:orders-" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "sa", "");
        Flyway.configure().dataSource(dataSource).load().migrate();
        repository = new OrderRepository(new JdbcTemplate(dataSource));
    }

    @Test
    void replayReturnsPersistedOrderWithoutCallingPricingAgain() {
        AtomicInteger calls = new AtomicInteger();
        PricingClient pricing = sku -> {
            calls.incrementAndGet();
            return new PriceQuote(sku, new BigDecimal("12.50"), "USD", "0.1.0");
        };
        var service = new OrderService(repository, pricing);
        var request = new CreateOrderRequest("BOOK-001", 2);

        Order first = service.create("checkout-123", request);
        Order replay = new OrderService(repository, sku -> {
            throw new AssertionError("Replay must not call pricing");
        }).create("checkout-123", request);

        assertEquals(first.id(), replay.id());
        assertEquals(new BigDecimal("25.00"), replay.total());
        assertEquals(1, calls.get());
        assertEquals(first.id(), repository.findById(first.id()).orElseThrow().id());
    }

    @Test
    void reusedKeyWithDifferentPayloadIsRejected() {
        var service = new OrderService(repository,
                sku -> new PriceQuote(sku, new BigDecimal("8.75"), "USD", "0.1.0"));
        service.create("checkout-456", new CreateOrderRequest("MUG-001", 1));

        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> service.create("checkout-456", new CreateOrderRequest("MUG-001", 2)));

        assertEquals(HttpStatus.CONFLICT, error.getStatusCode());
    }
}
