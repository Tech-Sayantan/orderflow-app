package dev.tan.orderflow.orders;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import tools.jackson.databind.json.JsonMapper;

@Component
public class ReceiptWorker {

    private static final Logger LOG = LoggerFactory.getLogger(ReceiptWorker.class);

    private final OrderRepository repository;
    private final ReceiptStore store;
    private final JsonMapper jsonMapper;

    public ReceiptWorker(OrderRepository repository, ReceiptStore store, JsonMapper jsonMapper) {
        this.repository = repository;
        this.store = store;
        this.jsonMapper = jsonMapper;
    }

    @Scheduled(fixedDelayString = "${orderflow.receipts.poll-ms}")
    public void deliverPending() {
        for (Order order : repository.findPendingReceipts()) {
            try {
                byte[] body = jsonMapper.writeValueAsBytes(order);
                store.put(order.id(), body);
                repository.markReceiptStored(order.id());
            } catch (Exception ex) {
                // The row remains PENDING and the deterministic object key makes retry safe.
                LOG.warn("Receipt delivery failed for orderId={}; will retry", order.id(), ex);
            }
        }
    }
}
