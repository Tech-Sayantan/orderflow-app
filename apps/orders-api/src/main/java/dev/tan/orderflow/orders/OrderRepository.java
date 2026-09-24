package dev.tan.orderflow.orders;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class OrderRepository {

    private static final String SELECT = """
            SELECT id, idempotency_key, request_fingerprint, sku, quantity, unit_price,
                   total, currency, catalog_version, receipt_status, created_at
            FROM orders
            """;

    private final JdbcTemplate jdbc;

    public OrderRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(Order order, String idempotencyKey, String fingerprint) {
        jdbc.update("""
                INSERT INTO orders (id, idempotency_key, request_fingerprint, sku, quantity,
                                    unit_price, total, currency, catalog_version,
                                    receipt_status, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                order.id(), idempotencyKey, fingerprint, order.sku(), order.quantity(),
                order.unitPrice(), order.total(), order.currency(), order.catalogVersion(),
                order.receiptStatus(), order.createdAt());
    }

    public Optional<StoredOrder> findByIdempotencyKey(String key) {
        return jdbc.query(SELECT + " WHERE idempotency_key = ?", this::mapStored, key)
                .stream().findFirst();
    }

    public Optional<Order> findById(UUID id) {
        return jdbc.query(SELECT + " WHERE id = ?", (rs, rowNum) -> mapStored(rs, rowNum).order(), id)
                .stream().findFirst();
    }

    public List<Order> findPendingReceipts() {
        return jdbc.query(SELECT + " WHERE receipt_status = 'PENDING' ORDER BY created_at LIMIT 20",
                (rs, rowNum) -> mapStored(rs, rowNum).order());
    }

    public void markReceiptStored(UUID id) {
        jdbc.update("UPDATE orders SET receipt_status = 'STORED' WHERE id = ? AND receipt_status = 'PENDING'", id);
    }

    private StoredOrder mapStored(ResultSet rs, int rowNum) throws SQLException {
        Order order = new Order(
                rs.getObject("id", UUID.class),
                rs.getString("sku"),
                rs.getInt("quantity"),
                rs.getBigDecimal("unit_price"),
                rs.getBigDecimal("total"),
                rs.getString("currency"),
                rs.getString("catalog_version"),
                rs.getString("receipt_status"),
                rs.getTimestamp("created_at").toInstant().atOffset(ZoneOffset.UTC));
        return new StoredOrder(order, rs.getString("request_fingerprint"));
    }

    public record StoredOrder(Order order, String fingerprint) {}
}
