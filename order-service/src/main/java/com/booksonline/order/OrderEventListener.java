package com.booksonline.order;

import com.booksonline.events.CartCheckedOutEvent;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class OrderEventListener {

    private final OrderService orderService;

    public OrderEventListener(OrderService orderService) {
        this.orderService = orderService;
    }

    @KafkaListener(
            topics = "${app.kafka.topics.cart-checkout:cart.checkout}",
            groupId = "${spring.kafka.consumer.group-id:order-service}",
            containerFactory = "cartCheckoutKafkaListenerContainerFactory"
    )
    public void consumeCartCheckout(CartCheckedOutEvent event) {
        orderService.createOrder(event);
    }
}
