package dev.tan.orderflow.pricing;

import java.math.BigDecimal;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/prices")
public class PriceController {

    private static final Map<String, BigDecimal> CATALOG = Map.of(
            "BOOK-001", new BigDecimal("12.50"),
            "MUG-001", new BigDecimal("8.75"),
            "BAG-001", new BigDecimal("24.00"));

    private final String version;

    public PriceController(@Value("${APP_VERSION:0.1.0}") String version) {
        this.version = version;
    }

    @GetMapping("/{sku}")
    public Price quote(@PathVariable String sku) {
        BigDecimal amount = CATALOG.get(sku);
        if (amount == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown SKU");
        }
        return new Price(sku, amount, "USD", version);
    }

    public record Price(String sku, BigDecimal amount, String currency, String catalogVersion) {}
}
