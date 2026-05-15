package com.booksonline.payment;

import java.math.BigDecimal;
import java.util.List;

public record CartSnapshot(
        String cartId,
        String customerId,
        List<CartItemSnapshot> items,
        BigDecimal totalAmount
) {
}
