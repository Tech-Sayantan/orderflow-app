package dev.tan.orderflow.orders;

import java.math.BigDecimal;

public record PriceQuote(String sku, BigDecimal amount, String currency, String catalogVersion) {}
