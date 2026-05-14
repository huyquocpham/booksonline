package com.booksonline.catalog;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/books")
public class BookController {

    private final BookCatalogService bookCatalogService;

    public BookController(BookCatalogService bookCatalogService) {
        this.bookCatalogService = bookCatalogService;
    }

    @GetMapping
    public List<Map<String, Object>> getBooks() {
        return bookCatalogService.findAllBooks();
    }

    @GetMapping("/{bookId}")
    public ResponseEntity<Map<String, Object>> getBook(@PathVariable Long bookId) {
        return bookCatalogService.findBookById(bookId)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/metadata/columns")
    public List<BookColumnMetadata> getBookColumns() {
        return bookCatalogService.getBookColumns();
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createBook(@RequestBody Map<String, Object> payload) {
        return ResponseEntity.status(HttpStatus.CREATED).body(bookCatalogService.createBook(payload));
    }

    @PutMapping("/{bookId}")
    public ResponseEntity<Map<String, Object>> updateBook(@PathVariable Long bookId, @RequestBody Map<String, Object> payload) {
        return bookCatalogService.updateBook(bookId, payload)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{bookId}")
    public ResponseEntity<Void> deleteBook(@PathVariable Long bookId) {
        return bookCatalogService.deleteBook(bookId)
                ? ResponseEntity.noContent().build()
                : ResponseEntity.notFound().build();
    }
}
