package dev.tan.orderflow.orders;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:orders-startup;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "orderflow.receipts.local-dir=./target/test-receipts",
        "orderflow.receipts.poll-ms=100"
}, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrdersApiStartupTest {

    @Value("${local.server.port}")
    int port;

    private final JsonMapper jsonMapper;
    private final JdbcTemplate jdbc;

    @Autowired
    OrdersApiStartupTest(JsonMapper jsonMapper, JdbcTemplate jdbc) {
        this.jsonMapper = jsonMapper;
        this.jdbc = jdbc;
    }

    @Test
    void httpCreateReplayAndReceiptDelivery() throws Exception {
        try (HttpClient http = HttpClient.newHttpClient()) {
            URI endpoint = URI.create("http://localhost:" + port + "/api/v1/orders");
            HttpRequest request = HttpRequest.newBuilder(endpoint)
                    .header("Content-Type", "application/json")
                    .header("Idempotency-Key", "http-checkout-123")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"sku\":\"BOOK-001\",\"quantity\":2}"))
                    .build();
            HttpResponse<String> first = http.send(request, HttpResponse.BodyHandlers.ofString());
            HttpResponse<String> replay = http.send(request, HttpResponse.BodyHandlers.ofString());

            assertEquals(201, first.statusCode(), first.body());
            assertEquals(201, replay.statusCode(), replay.body());
            String id = jsonMapper.readTree(first.body()).get("id").asText();
            assertEquals(id, jsonMapper.readTree(replay.body()).get("id").asText());

            long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
            String status = "";
            while (System.nanoTime() < deadline) {
                status = jdbc.queryForObject(
                        "SELECT receipt_status FROM orders WHERE idempotency_key = ?",
                        String.class, "http-checkout-123");
                if ("STORED".equals(status)) {
                    break;
                }
                Thread.sleep(50);
            }
            assertEquals("STORED", status);
            assertTrue(java.nio.file.Files.exists(
                    java.nio.file.Path.of("./target/test-receipts/" + id + ".json")));
        }
    }

    @TestConfiguration
    static class FakePricing {
        @Bean
        @Primary
        PricingClient pricingClient() {
            return sku -> new PriceQuote(sku, new java.math.BigDecimal("12.50"), "USD", "0.1.0");
        }
    }
}
