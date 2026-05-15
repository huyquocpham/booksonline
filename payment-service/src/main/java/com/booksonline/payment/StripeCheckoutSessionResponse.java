package com.booksonline.payment;

public record StripeCheckoutSessionResponse(
        String id,
        String url,
        String paymentStatus,
        String status
) {
}
