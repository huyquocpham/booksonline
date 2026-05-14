package com.booksonline.catalog;

import jakarta.annotation.PostConstruct;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class BookCatalogService {

    private static final String TABLE_NAME = "books";

    private final JdbcClient jdbcClient;
    private final DataSource dataSource;

    private String idColumn = "id";
    private List<String> tableColumns = List.of();
    private List<BookColumnMetadata> columnMetadata = List.of();
    private Set<String> writableColumns = Set.of();

    public BookCatalogService(JdbcClient jdbcClient, DataSource dataSource) {
        this.jdbcClient = jdbcClient;
        this.dataSource = dataSource;
    }

    @PostConstruct
    void loadMetadata() {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            Set<String> primaryKeys = loadPrimaryKeys(metadata);
            this.columnMetadata = loadColumnMetadata(metadata, primaryKeys);
            this.tableColumns = columnMetadata.stream().map(BookColumnMetadata::name).toList();
            this.idColumn = primaryKeys.stream().findFirst().orElseGet(() ->
                    tableColumns.contains("id") ? "id" : tableColumns.getFirst());
            this.writableColumns = tableColumns.stream()
                    .filter(column -> !Objects.equals(column, idColumn))
                    .collect(Collectors.toCollection(LinkedHashSet::new));
        } catch (SQLException ex) {
            throw new IllegalStateException("Unable to read books table metadata", ex);
        }
    }

    @Cacheable(cacheNames = "books", key = "'all'")
    public List<Map<String, Object>> findAllBooks() {
        return jdbcClient.sql("select * from books order by " + idColumn).query().listOfRows();
    }

    @Cacheable(cacheNames = "books", key = "#bookId")
    public Optional<Map<String, Object>> findBookById(Long bookId) {
        return jdbcClient.sql("select * from books where " + idColumn + " = :id")
                .param("id", bookId)
                .query()
                .listOfRows()
                .stream()
                .findFirst();
    }

    @CacheEvict(cacheNames = "books", allEntries = true)
    public Map<String, Object> createBook(Map<String, Object> payload) {
        Map<String, Object> filteredPayload = sanitizePayload(payload, false);
        if (filteredPayload.isEmpty()) {
            throw new IllegalArgumentException("Payload does not contain writable book fields");
        }

        String columns = String.join(", ", filteredPayload.keySet());
        String values = filteredPayload.keySet().stream()
                .map(key -> ":" + key)
                .collect(Collectors.joining(", "));

        String sql = "insert into books (" + columns + ") values (" + values + ") returning *";
        return jdbcClient.sql(sql)
                .params(filteredPayload)
                .query()
                .singleRow();
    }

    @CacheEvict(cacheNames = "books", allEntries = true)
    public Optional<Map<String, Object>> updateBook(Long bookId, Map<String, Object> payload) {
        Map<String, Object> filteredPayload = sanitizePayload(payload, true);
        if (filteredPayload.isEmpty()) {
            return findBookById(bookId);
        }

        String assignments = filteredPayload.keySet().stream()
                .map(key -> key + " = :" + key)
                .collect(Collectors.joining(", "));

        Map<String, Object> params = new LinkedHashMap<>(filteredPayload);
        params.put("id", bookId);

        String sql = "update books set " + assignments + " where " + idColumn + " = :id returning *";
        List<Map<String, Object>> rows = jdbcClient.sql(sql)
                .params(params)
                .query()
                .listOfRows();

        return rows.stream().findFirst();
    }

    @CacheEvict(cacheNames = "books", allEntries = true)
    public boolean deleteBook(Long bookId) {
        int deleted = jdbcClient.sql("delete from books where " + idColumn + " = :id")
                .param("id", bookId)
                .update();
        return deleted > 0;
    }

    public List<BookColumnMetadata> getBookColumns() {
        return columnMetadata;
    }

    private Map<String, Object> sanitizePayload(Map<String, Object> payload, boolean allowNulls) {
        Map<String, Object> filtered = new LinkedHashMap<>();
        payload.forEach((key, value) -> {
            if (writableColumns.contains(key) && (allowNulls || value != null)) {
                filtered.put(key, value);
            }
        });
        return filtered;
    }

    private List<BookColumnMetadata> loadColumnMetadata(DatabaseMetaData metadata, Set<String> primaryKeys) throws SQLException {
        List<BookColumnMetadata> columns = new ArrayList<>();
        try (ResultSet resultSet = metadata.getColumns(null, null, TABLE_NAME, null)) {
            while (resultSet.next()) {
                String columnName = resultSet.getString("COLUMN_NAME");
                columns.add(new BookColumnMetadata(
                        columnName,
                        resultSet.getString("TYPE_NAME"),
                        "YES".equalsIgnoreCase(resultSet.getString("IS_NULLABLE")),
                        primaryKeys.contains(columnName)
                ));
            }
        }
        if (columns.isEmpty()) {
            throw new IllegalStateException("books table was not found");
        }
        return columns;
    }

    private Set<String> loadPrimaryKeys(DatabaseMetaData metadata) throws SQLException {
        Set<String> primaryKeys = new LinkedHashSet<>();
        try (ResultSet resultSet = metadata.getPrimaryKeys(null, null, TABLE_NAME)) {
            while (resultSet.next()) {
                primaryKeys.add(resultSet.getString("COLUMN_NAME"));
            }
        }
        return primaryKeys;
    }
}
