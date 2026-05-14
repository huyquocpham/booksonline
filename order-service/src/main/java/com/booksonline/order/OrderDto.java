package com.booksonline.order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderDto(
        Long id,
        String cartId,
        String customerId,
        BigDecimal totalAmount,
        String status,
        Instant createdAt,
        List<OrderLineDto> lines
) {
    public static OrderDto from(CustomerOrder order) {
        return new OrderDto(
                order.getId(),
                order.getCartId(),
                order.getCustomerId(),
                order.getTotalAmount(),
                order.getStatus(),
                order.getCreatedAt(),
                order.getLines().stream()
                        .map(line -> new OrderLineDto(
                                line.getBookId(),
                                line.getTitle(),
                                line.getIsbn(),
                                line.getPrice(),
                                line.getQuantity()))
                        .toList()
        );
    }
}
