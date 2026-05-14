package com.booksonline.cart;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record CartItemRequest(
        @NotNull Long bookId,
        @Min(1) int quantity
) {
}
