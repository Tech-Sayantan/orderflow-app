package dev.tan.orderflow.pricing;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PriceControllerTest {

    private final PriceController controller = new PriceController("0.1.0");

    @Test
    void quoteExposesStableContractAndVersion() {
        var quote = controller.quote("BOOK-001");
        assertEquals(new BigDecimal("12.50"), quote.amount());
        assertEquals("USD", quote.currency());
        assertEquals("0.1.0", quote.catalogVersion());
    }

    @Test
    void unknownSkuReturnsNotFound() {
        var error = assertThrows(ResponseStatusException.class,
                () -> controller.quote("UNKNOWN"));
        assertEquals(HttpStatus.NOT_FOUND, error.getStatusCode());
    }
}
