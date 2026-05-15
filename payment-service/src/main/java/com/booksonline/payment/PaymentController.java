package com.booksonline.payment;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/checkout/{customerId}")
    public StripeCheckoutSessionResponse createCheckout(@PathVariable String customerId) {
        return paymentService.createCheckoutSession(customerId);
    }

    @PostMapping("/confirm/{customerId}")
    public PaymentConfirmationResponse confirmPayment(
            @PathVariable String customerId,
            @RequestParam("sessionId") String sessionId
    ) {
        return paymentService.confirmPayment(customerId, sessionId);
    }

    @GetMapping("/session/{sessionId}")
    public StripeCheckoutSessionResponse getSession(@PathVariable String sessionId) {
        return paymentService.getSession(sessionId);
    }
}
