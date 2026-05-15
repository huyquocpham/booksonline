package com.booksonline.payment;

public record PaymentConfirmationResponse(
        String sessionId,
        boolean paid,
        boolean checkoutCompleted,
        String message
) {
}
