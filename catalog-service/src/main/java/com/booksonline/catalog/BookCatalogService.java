package com.booksonline.catalog;

import jakarta.annotation.PostConstruct;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
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
    private Map<String, BookColumnMetadata> columnMetadataByName = Map.of();
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
            this.columnMetadataByName = indexMetadataByName(columnMetadata);
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
        try {
            return jdbcClient.sql(sql)
                    .params(filteredPayload)
                    .query()
                    .singleRow();
        } catch (DataAccessException ex) {
            throw new IllegalArgumentException("Book payload could not be saved", ex);
        }
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
        List<Map<String, Object>> rows;
        try {
            rows = jdbcClient.sql(sql)
                    .params(params)
                    .query()
                    .listOfRows();
        } catch (DataAccessException ex) {
            throw new IllegalArgumentException("Book payload could not be saved", ex);
        }

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
            if (writableColumns.contains(key)) {
                Object normalizedValue = normalizeColumnValue(key, value);
                if (allowNulls || normalizedValue != null) {
                    filtered.put(key, normalizedValue);
                }
            }
        });
        return filtered;
    }

    private Object normalizeColumnValue(String columnName, Object value) {
        if (value == null) {
            return null;
        }

        if (value instanceof String textValue && textValue.isBlank()) {
            return null;
        }

        BookColumnMetadata metadata = columnMetadataByName.get(columnName);
        if (metadata == null) {
            return value;
        }

        String jdbcType = metadata.jdbcType().toLowerCase();
        try {
            if (jdbcType.contains("bool")) {
                return normalizeBoolean(value, columnName);
            }
            if (jdbcType.contains("date") && !jdbcType.contains("timestamp")) {
                return normalizeDate(value, columnName);
            }
            if (jdbcType.contains("timestamp")) {
                return normalizeTimestamp(value, columnName);
            }
            if (jdbcType.contains("numeric") || jdbcType.contains("decimal")) {
                return normalizeBigDecimal(value, columnName);
            }
            if (jdbcType.contains("int")) {
                return normalizeInteger(value, columnName);
            }
            if (jdbcType.contains("real") || jdbcType.contains("double") || jdbcType.contains("float")) {
                return normalizeFloatingPoint(value, columnName);
            }
        } catch (DateTimeParseException | NumberFormatException ex) {
            throw new IllegalArgumentException("Invalid value for " + columnName, ex);
        }

        return value;
    }

    private Boolean normalizeBoolean(Object value, String columnName) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        if (value instanceof String textValue) {
            if ("true".equalsIgnoreCase(textValue) || "false".equalsIgnoreCase(textValue)) {
                return Boolean.parseBoolean(textValue);
            }
        }
        throw new IllegalArgumentException("Invalid value for " + columnName);
    }

    private Date normalizeDate(Object value, String columnName) {
        if (value instanceof Date dateValue) {
            return dateValue;
        }
        if (value instanceof LocalDate localDate) {
            return Date.valueOf(localDate);
        }
        if (value instanceof String textValue) {
            return Date.valueOf(LocalDate.parse(textValue));
        }
        throw new IllegalArgumentException("Invalid value for " + columnName);
    }

    private Timestamp normalizeTimestamp(Object value, String columnName) {
        if (value instanceof Timestamp timestampValue) {
            return timestampValue;
        }
        if (value instanceof LocalDateTime localDateTime) {
            return Timestamp.valueOf(localDateTime);
        }
        if (value instanceof OffsetDateTime offsetDateTime) {
            return Timestamp.from(offsetDateTime.toInstant());
        }
        if (value instanceof String textValue) {
            try {
                return Timestamp.valueOf(LocalDateTime.parse(textValue));
            } catch (DateTimeParseException ignored) {
                return Timestamp.from(OffsetDateTime.parse(textValue).toInstant());
            }
        }
        throw new IllegalArgumentException("Invalid value for " + columnName);
    }

    private BigDecimal normalizeBigDecimal(Object value, String columnName) {
        if (value instanceof BigDecimal decimalValue) {
            return decimalValue;
        }
        if (value instanceof Number numberValue) {
            return BigDecimal.valueOf(numberValue.doubleValue());
        }
        if (value instanceof String textValue) {
            return new BigDecimal(textValue);
        }
        throw new IllegalArgumentException("Invalid value for " + columnName);
    }

    private Integer normalizeInteger(Object value, String columnName) {
        if (value instanceof Integer integerValue) {
            return integerValue;
        }
        if (value instanceof Number numberValue) {
            return numberValue.intValue();
        }
        if (value instanceof String textValue) {
            return Integer.valueOf(textValue);
        }
        throw new IllegalArgumentException("Invalid value for " + columnName);
    }

    private Double normalizeFloatingPoint(Object value, String columnName) {
        if (value instanceof Double doubleValue) {
            return doubleValue;
        }
        if (value instanceof Float floatValue) {
            return Double.valueOf(floatValue);
        }
        if (value instanceof Number numberValue) {
            return numberValue.doubleValue();
        }
        if (value instanceof String textValue) {
            return Double.valueOf(textValue);
        }
        throw new IllegalArgumentException("Invalid value for " + columnName);
    }

    private Map<String, BookColumnMetadata> indexMetadataByName(List<BookColumnMetadata> metadata) {
        Map<String, BookColumnMetadata> byName = new HashMap<>();
        for (BookColumnMetadata column : metadata) {
            byName.put(column.name(), column);
        }
        return byName;
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
