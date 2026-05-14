package com.booksonline.order;

import java.math.BigDecimal;

public record OrderLineDto(
        Long bookId,
        String title,
        String isbn,
        BigDecimal price,
        int quantity
) {
}
