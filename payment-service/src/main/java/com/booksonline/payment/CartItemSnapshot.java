package com.booksonline.payment;

import java.math.BigDecimal;

public record CartItemSnapshot(
        Long bookId,
        String title,
        String isbn,
        BigDecimal unitPrice,
        int quantity,
        BigDecimal lineTotal
) {
}
