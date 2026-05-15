package com.booksonline.payment;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class PaymentService {

    private final RestClient cartClient;
    private final RestClient stripeClient;
    private final JdbcClient jdbcClient;
    private final PaymentProperties paymentProperties;

    public PaymentService(RestClient.Builder restClientBuilder, JdbcClient jdbcClient, PaymentProperties paymentProperties) {
        this.jdbcClient = jdbcClient;
        this.paymentProperties = paymentProperties;
        this.cartClient = restClientBuilder.baseUrl(paymentProperties.cartServiceBaseUrl()).build();
        this.stripeClient = restClientBuilder.baseUrl(paymentProperties.stripeBaseUrl()).build();
    }

    @Transactional
    public StripeCheckoutSessionResponse createCheckoutSession(String customerId) {
        validateStripeConfiguration();
        CartSnapshot cart = getCart(customerId);
        if (cart.items().isEmpty()) {
            throw new IllegalArgumentException("Cart is empty");
        }

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("mode", "payment");
        form.add("success_url", paymentProperties.frontendBaseUrl() + "/payment/success?session_id={CHECKOUT_SESSION_ID}&customer_id=" + customerId);
        form.add("cancel_url", paymentProperties.frontendBaseUrl() + "/payment/cancel?customer_id=" + customerId);
        form.add("client_reference_id", customerId);
        form.add("metadata[customerId]", customerId);
        form.add("metadata[cartId]", cart.cartId());

        for (int i = 0; i < cart.items().size(); i++) {
            CartItemSnapshot item = cart.items().get(i);
            form.add("line_items[" + i + "][quantity]", String.valueOf(item.quantity()));
            form.add("line_items[" + i + "][price_data][currency]", paymentProperties.currency());
            form.add("line_items[" + i + "][price_data][unit_amount]", toStripeAmount(item.unitPrice()).toString());
            form.add("line_items[" + i + "][price_data][product_data][name]", item.title());
            form.add("line_items[" + i + "][price_data][product_data][description]", "ISBN " + item.isbn());
        }

        Map<String, Object> response;
        try {
            response = stripeClient.post()
                    .uri("/v1/checkout/sessions")
                    .header("Authorization", "Bearer " + paymentProperties.stripeSecretKey())
                    .contentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(Map.class);
        } catch (RestClientResponseException ex) {
            throw stripeError("Stripe rejected checkout session creation", ex);
        }

        if (response == null) {
            throw new IllegalStateException("Stripe did not return a checkout session");
        }

        StripeCheckoutSessionResponse session = mapSessionResponse(response);
        storePaymentRecord(customerId, cart, session);
        return session;
    }

    @Transactional
    public PaymentConfirmationResponse confirmPayment(String customerId, String sessionId) {
        validateStripeConfiguration();
        PaymentRecord record = findPaymentRecord(sessionId);
        if (record == null) {
            throw new IllegalArgumentException("Unknown payment session");
        }

        if (!record.customerId().equals(customerId)) {
            throw new IllegalArgumentException("Payment session does not belong to this customer");
        }

        if (record.finalizedAt() != null) {
            return new PaymentConfirmationResponse(record.sessionId(), true, true, "Payment already confirmed");
        }

        StripeCheckoutSessionResponse session = getSession(sessionId);
        if (!"paid".equalsIgnoreCase(session.paymentStatus())) {
            updatePaymentStatus(sessionId, session.paymentStatus(), null);
            return new PaymentConfirmationResponse(sessionId, false, false, "Payment is not completed");
        }

        CartSnapshot cart = getCart(customerId);
        if (!record.cartId().equals(cart.cartId())) {
            throw new IllegalArgumentException("The cart changed after the payment session was created");
        }

        cartClient.post()
                .uri("/api/carts/{customerId}/checkout", customerId)
                .retrieve()
                .toBodilessEntity();

        updatePaymentStatus(sessionId, "paid", Timestamp.valueOf(LocalDateTime.now()));
        return new PaymentConfirmationResponse(sessionId, true, true, "Payment confirmed and checkout completed");
    }

    public StripeCheckoutSessionResponse getSession(String sessionId) {
        validateStripeConfiguration();
        Map<String, Object> response;
        try {
            response = stripeClient.get()
                    .uri("/v1/checkout/sessions/{sessionId}", sessionId)
                    .header("Authorization", "Bearer " + paymentProperties.stripeSecretKey())
                    .retrieve()
                    .body(Map.class);
        } catch (RestClientResponseException ex) {
            throw stripeError("Stripe rejected checkout session lookup", ex);
        }

        if (response == null) {
            throw new IllegalStateException("Stripe did not return the checkout session");
        }

        return mapSessionResponse(response);
    }

    private CartSnapshot getCart(String customerId) {
        try {
            return cartClient.get()
                    .uri("/api/carts/{customerId}", customerId)
                    .retrieve()
                    .body(CartSnapshot.class);
        } catch (RestClientException ex) {
            throw new IllegalStateException("Cart service is unavailable", ex);
        }
    }

    private void storePaymentRecord(String customerId, CartSnapshot cart, StripeCheckoutSessionResponse session) {
        jdbcClient.sql("""
                insert into payment_records (session_id, customer_id, cart_id, amount_total, stripe_status, created_at)
                values (:sessionId, :customerId, :cartId, :amountTotal, :stripeStatus, :createdAt)
                on conflict (session_id) do update
                  set stripe_status = excluded.stripe_status,
                      amount_total = excluded.amount_total
                """)
                .param("sessionId", session.id())
                .param("customerId", customerId)
                .param("cartId", cart.cartId())
                .param("amountTotal", cart.totalAmount())
                .param("stripeStatus", session.paymentStatus())
                .param("createdAt", Timestamp.valueOf(LocalDateTime.now()))
                .update();
    }

    private PaymentRecord findPaymentRecord(String sessionId) {
        List<Map<String, Object>> rows = jdbcClient.sql("""
                select session_id, customer_id, cart_id, stripe_status, finalized_at
                from payment_records
                where session_id = :sessionId
                """)
                .param("sessionId", sessionId)
                .query()
                .listOfRows();

        if (rows.isEmpty()) {
            return null;
        }

        Map<String, Object> row = rows.getFirst();
        return new PaymentRecord(
                String.valueOf(row.get("session_id")),
                String.valueOf(row.get("customer_id")),
                String.valueOf(row.get("cart_id")),
                String.valueOf(row.get("stripe_status")),
                (Timestamp) row.get("finalized_at")
        );
    }

    private void updatePaymentStatus(String sessionId, String stripeStatus, Timestamp finalizedAt) {
        jdbcClient.sql("""
                update payment_records
                set stripe_status = :stripeStatus,
                    finalized_at = :finalizedAt
                where session_id = :sessionId
                """)
                .param("stripeStatus", stripeStatus)
                .param("finalizedAt", finalizedAt)
                .param("sessionId", sessionId)
                .update();
    }

    private StripeCheckoutSessionResponse mapSessionResponse(Map<String, Object> response) {
        return new StripeCheckoutSessionResponse(
                String.valueOf(response.get("id")),
                response.get("url") == null ? null : String.valueOf(response.get("url")),
                response.get("payment_status") == null ? "" : String.valueOf(response.get("payment_status")),
                response.get("status") == null ? "" : String.valueOf(response.get("status"))
        );
    }

    private void validateStripeConfiguration() {
        String secretKey = paymentProperties.stripeSecretKey();
        if (secretKey == null || secretKey.isBlank() || "sk_test_replace_me".equals(secretKey)) {
            throw new IllegalStateException("Stripe secret key is not configured. Set STRIPE_SECRET_KEY for payment-service.");
        }
    }

    private IllegalArgumentException stripeError(String prefix, RestClientResponseException ex) {
        return new IllegalArgumentException(prefix + ": " + extractStripeErrorMessage(ex), ex);
    }

    private String extractStripeErrorMessage(RestClientResponseException ex) {
        String body = ex.getResponseBodyAsString();
        if (body == null || body.isBlank()) {
            return ex.getStatusText();
        }

        int messageIndex = body.indexOf("\"message\"");
        if (messageIndex < 0) {
            return body;
        }

        int colonIndex = body.indexOf(':', messageIndex);
        int firstQuote = body.indexOf('"', colonIndex + 1);
        int secondQuote = body.indexOf('"', firstQuote + 1);
        if (colonIndex < 0 || firstQuote < 0 || secondQuote < 0) {
            return body;
        }

        return body.substring(firstQuote + 1, secondQuote);
    }

    private Long toStripeAmount(BigDecimal amount) {
        return amount.multiply(BigDecimal.valueOf(100))
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    private record PaymentRecord(
            String sessionId,
            String customerId,
            String cartId,
            String stripeStatus,
            Timestamp finalizedAt
    ) {
    }
}
