package dev.tan.orderflow.orders;

public interface PricingClient {
    PriceQuote quote(String sku);
}
