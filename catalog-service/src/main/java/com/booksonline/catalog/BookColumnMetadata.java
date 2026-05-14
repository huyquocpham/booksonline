package com.booksonline.catalog;

public record BookColumnMetadata(
        String name,
        String jdbcType,
        boolean nullable,
        boolean primaryKey
) {
}
