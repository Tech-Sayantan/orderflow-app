package dev.tan.orderflow.orders;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateOrderRequest(
        @NotBlank @Pattern(regexp = "[A-Z0-9-]{1,64}") String sku,
        @Min(1) @Max(100) int quantity) {}
