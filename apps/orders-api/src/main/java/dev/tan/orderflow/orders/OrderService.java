package dev.tan.orderflow.orders;

import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OrderService {

    private final OrderRepository repository;
    private final PricingClient pricing;

    public OrderService(OrderRepository repository, PricingClient pricing) {
        this.repository = repository;
        this.pricing = pricing;
    }

    public Order create(String idempotencyKey, CreateOrderRequest request) {
        if (idempotencyKey == null || !idempotencyKey.matches("[A-Za-z0-9._-]{8,120}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Idempotency-Key must contain 8-120 letters, digits, dots, underscores or hyphens");
        }
        String fingerprint = request.sku() + "|" + request.quantity();
        var previous = repository.findByIdempotencyKey(idempotencyKey);
        if (previous.isPresent()) {
            return checkedReplay(previous.get(), fingerprint);
        }

        PriceQuote quote = pricing.quote(request.sku());
        if (!request.sku().equals(quote.sku()) || !"USD".equals(quote.currency())
                || quote.amount() == null || quote.amount().signum() < 0) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Invalid pricing response");
        }
        var unitPrice = quote.amount().setScale(2, RoundingMode.UNNECESSARY);
        var order = new Order(UUID.randomUUID(), request.sku(), request.quantity(), unitPrice,
                unitPrice.multiply(java.math.BigDecimal.valueOf(request.quantity())),
                quote.currency(), quote.catalogVersion(), "PENDING",
                OffsetDateTime.now(ZoneOffset.UTC));
        try {
            repository.insert(order, idempotencyKey, fingerprint);
            return order;
        } catch (DuplicateKeyException duplicate) {
            // A concurrent request won the unique-key race. The insert was one atomic statement.
            return repository.findByIdempotencyKey(idempotencyKey)
                    .map(stored -> checkedReplay(stored, fingerprint))
                    .orElseThrow(() -> duplicate);
        }
    }

    private Order checkedReplay(OrderRepository.StoredOrder stored, String fingerprint) {
        if (!stored.fingerprint().equals(fingerprint)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Idempotency-Key was already used for a different order");
        }
        return stored.order();
    }

    public Order get(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found"));
    }
}
