package com.booksonline.events;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record CartCheckedOutEvent(
        String cartId,
        String customerId,
        List<CartItemEvent> items,
        BigDecimal totalAmount,
        Instant checkedOutAt
) {
}
