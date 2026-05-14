package com.booksonline.cart;

import com.booksonline.events.CartCheckedOutEvent;
import com.booksonline.events.CartItemEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class CartService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final BookCatalogClient bookCatalogClient;
    private final KafkaTemplate<String, CartCheckedOutEvent> kafkaTemplate;
    private final String checkoutTopic;

    public CartService(
            RedisTemplate<String, Object> redisTemplate,
            BookCatalogClient bookCatalogClient,
            KafkaTemplate<String, CartCheckedOutEvent> kafkaTemplate,
            @Value("${app.kafka.topics.cart-checkout:cart.checkout}") String checkoutTopic
    ) {
        this.redisTemplate = redisTemplate;
        this.bookCatalogClient = bookCatalogClient;
        this.kafkaTemplate = kafkaTemplate;
        this.checkoutTopic = checkoutTopic;
    }

    public Cart getCart(String customerId) {
        String key = cartKey(customerId);
        Cart existingCart = (Cart) redisTemplate.opsForValue().get(key);
        if (existingCart != null) {
            return existingCart;
        }

        Cart newCart = new Cart();
        newCart.setCartId(UUID.randomUUID().toString());
        newCart.setCustomerId(customerId);
        saveCart(newCart);
        return newCart;
    }

    public Cart addItem(String customerId, CartItemRequest request) {
        Cart cart = getCart(customerId);
        BookCatalogClient.BookSummary book = bookCatalogClient.getBook(request.bookId());

        CartItem existingItem = cart.getItems().stream()
                .filter(item -> item.getBookId().equals(book.id()))
                .findFirst()
                .orElse(null);

        if (existingItem == null) {
            existingItem = new CartItem();
            existingItem.setBookId(book.id());
            existingItem.setTitle(book.title());
            existingItem.setIsbn(book.isbn());
            existingItem.setUnitPrice(book.price());
            existingItem.setQuantity(request.quantity());
            existingItem.setLineTotal(book.price().multiply(BigDecimal.valueOf(request.quantity())));
            cart.getItems().add(existingItem);
        } else {
            int quantity = existingItem.getQuantity() + request.quantity();
            existingItem.setQuantity(quantity);
            existingItem.setLineTotal(existingItem.getUnitPrice().multiply(BigDecimal.valueOf(quantity)));
        }

        recalculateTotals(cart);
        saveCart(cart);
        return cart;
    }

    public Cart removeItem(String customerId, Long bookId) {
        Cart cart = getCart(customerId);
        cart.getItems().removeIf(item -> item.getBookId().equals(bookId));
        recalculateTotals(cart);
        saveCart(cart);
        return cart;
    }

    public Cart checkout(String customerId) {
        Cart cart = getCart(customerId);
        if (cart.getItems().isEmpty()) {
            throw new IllegalStateException("Cart is empty");
        }

        CartCheckedOutEvent event = new CartCheckedOutEvent(
                cart.getCartId(),
                cart.getCustomerId(),
                cart.getItems().stream()
                        .map(item -> new CartItemEvent(
                                item.getBookId(),
                                item.getTitle(),
                                item.getIsbn(),
                                item.getUnitPrice(),
                                item.getQuantity()))
                        .toList(),
                cart.getTotalAmount(),
                Instant.now()
        );

        kafkaTemplate.send(checkoutTopic, customerId, event);
        redisTemplate.delete(cartKey(customerId));
        return cart;
    }

    private void recalculateTotals(Cart cart) {
        BigDecimal total = cart.getItems().stream()
                .map(CartItem::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        cart.setTotalAmount(total);
    }

    private void saveCart(Cart cart) {
        redisTemplate.opsForValue().set(cartKey(cart.getCustomerId()), cart, Duration.ofHours(4));
    }

    private String cartKey(String customerId) {
        return "cart:" + customerId;
    }
}
