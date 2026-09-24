package dev.tan.orderflow.orders;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record Order(
        UUID id,
        String sku,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal total,
        String currency,
        String catalogVersion,
        String receiptStatus,
        OffsetDateTime createdAt) {}
