package com.booksonline.cart;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/carts/{customerId}")
public class CartController {

    private final CartService cartService;

    public CartController(CartService cartService) {
        this.cartService = cartService;
    }

    @GetMapping
    public Cart getCart(@PathVariable String customerId) {
        return cartService.getCart(customerId);
    }

    @PostMapping("/items")
    public Cart addItem(@PathVariable String customerId, @Valid @RequestBody CartItemRequest request) {
        return cartService.addItem(customerId, request);
    }

    @DeleteMapping("/items/{bookId}")
    public Cart removeItem(@PathVariable String customerId, @PathVariable Long bookId) {
        return cartService.removeItem(customerId, bookId);
    }

    @PostMapping("/checkout")
    public ResponseEntity<Cart> checkout(@PathVariable String customerId) {
        return ResponseEntity.accepted().body(cartService.checkout(customerId));
    }
}
