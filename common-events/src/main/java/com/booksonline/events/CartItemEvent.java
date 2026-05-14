package com.booksonline.events;

import java.math.BigDecimal;

public record CartItemEvent(
        Long bookId,
        String title,
        String isbn,
        BigDecimal price,
        int quantity
) {
}
