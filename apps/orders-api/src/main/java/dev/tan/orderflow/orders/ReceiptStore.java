package dev.tan.orderflow.orders;

import java.util.UUID;

public interface ReceiptStore {
    void put(UUID orderId, byte[] json);
}
