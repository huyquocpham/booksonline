package com.booksonline.order;

import com.booksonline.events.CartCheckedOutEvent;
import com.booksonline.events.OrderCreatedEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class OrderService {

    private final CustomerOrderRepository customerOrderRepository;
    private final KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate;
    private final String orderCreatedTopic;

    public OrderService(
            CustomerOrderRepository customerOrderRepository,
            KafkaTemplate<String, OrderCreatedEvent> kafkaTemplate,
            @Value("${app.kafka.topics.order-created:order.created}") String orderCreatedTopic
    ) {
        this.customerOrderRepository = customerOrderRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.orderCreatedTopic = orderCreatedTopic;
    }

    @Transactional
    public OrderDto createOrder(CartCheckedOutEvent event) {
        CustomerOrder existing = customerOrderRepository.findByCartId(event.cartId()).orElse(null);
        if (existing != null) {
            return OrderDto.from(existing);
        }

        CustomerOrder order = new CustomerOrder();
        order.setCartId(event.cartId());
        order.setCustomerId(event.customerId());
        order.setTotalAmount(event.totalAmount());
        order.setStatus("CREATED");
        order.setCreatedAt(event.checkedOutAt());

        event.items().forEach(item -> {
            OrderLine line = new OrderLine();
            line.setBookId(item.bookId());
            line.setTitle(item.title());
            line.setIsbn(item.isbn());
            line.setPrice(item.price());
            line.setQuantity(item.quantity());
            order.addLine(line);
        });

        CustomerOrder savedOrder = customerOrderRepository.save(order);

        kafkaTemplate.send(orderCreatedTopic, savedOrder.getCustomerId(), new OrderCreatedEvent(
                savedOrder.getId(),
                savedOrder.getCustomerId(),
                savedOrder.getCartId(),
                savedOrder.getTotalAmount(),
                savedOrder.getStatus(),
                savedOrder.getCreatedAt()
        ));

        return OrderDto.from(savedOrder);
    }

    public List<OrderDto> getOrders(String customerId) {
        return customerOrderRepository.findByCustomerIdOrderByCreatedAtDesc(customerId).stream()
                .map(OrderDto::from)
                .toList();
    }
}
