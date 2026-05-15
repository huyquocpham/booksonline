package com.booksonline.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.payment")
public record PaymentProperties(
        String stripeSecretKey,
        String stripeBaseUrl,
        String frontendBaseUrl,
        String cartServiceBaseUrl,
        String currency
) {
}
