package dev.tan.orderflow.orders;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Configuration
public class ReceiptStoreConfiguration {

    @Bean
    @ConditionalOnProperty(name = "orderflow.receipts.store", havingValue = "local", matchIfMissing = true)
    ReceiptStore localReceiptStore(@Value("${orderflow.receipts.local-dir}") String directory) {
        Path root = Path.of(directory).toAbsolutePath().normalize();
        return (orderId, json) -> {
            try {
                Files.createDirectories(root);
                Path temporary = Files.createTempFile(root, "receipt-", ".tmp");
                try {
                    Files.write(temporary, json);
                    Files.move(temporary, root.resolve(orderId + ".json"),
                            StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } finally {
                    Files.deleteIfExists(temporary);
                }
            } catch (IOException ex) {
                throw new IllegalStateException("Could not write local receipt", ex);
            }
        };
    }

    @Bean(destroyMethod = "close")
    @ConditionalOnProperty(name = "orderflow.receipts.store", havingValue = "s3")
    S3Client receiptS3Client() {
        // The default credential chain picks up EKS Pod Identity in the cloud.
        return S3Client.create();
    }

    @Bean
    @ConditionalOnProperty(name = "orderflow.receipts.store", havingValue = "s3")
    ReceiptStore s3ReceiptStore(
            S3Client s3,
            @Value("${orderflow.receipts.bucket}") String bucket,
            @Value("${orderflow.receipts.prefix}") String prefix) {
        if (bucket == null || bucket.isBlank()) {
            throw new IllegalArgumentException("RECEIPT_BUCKET is required when RECEIPT_STORE=s3");
        }
        String normalizedPrefix = prefix.endsWith("/") ? prefix : prefix + "/";
        return (UUID orderId, byte[] json) -> s3.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(normalizedPrefix + orderId + ".json")
                        .contentType("application/json")
                        .build(),
                RequestBody.fromBytes(json));
    }
}
