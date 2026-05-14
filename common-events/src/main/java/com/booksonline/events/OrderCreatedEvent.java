package com.booksonline.events;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderCreatedEvent(
        Long orderId,
        String customerId,
        String cartId,
        BigDecimal totalAmount,
        String status,
        Instant createdAt
) {
}
