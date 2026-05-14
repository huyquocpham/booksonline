package com.booksonline.cart;

import java.math.BigDecimal;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class BookCatalogClient {

    private final WebClient webClient;

    public BookCatalogClient(
            WebClient.Builder webClientBuilder,
            @Value("${catalog-service.base-url:http://localhost:8081}") String catalogServiceBaseUrl
    ) {
        this.webClient = webClientBuilder.baseUrl(catalogServiceBaseUrl).build();
    }

    public BookSummary getBook(Long bookId) {
        Map<String, Object> book = webClient.get()
                .uri("/api/books/{bookId}", bookId)
                .retrieve()
                .bodyToMono(Map.class)
                .block();

        if (book == null) {
            throw new IllegalArgumentException("Book not found: " + bookId);
        }

        return new BookSummary(
                bookId,
                String.valueOf(book.getOrDefault("title", "")),
                String.valueOf(book.getOrDefault("isbn", "")),
                new BigDecimal(String.valueOf(book.getOrDefault("price", "0")))
        );
    }

    public record BookSummary(Long id, String title, String isbn, BigDecimal price) {
    }
}
