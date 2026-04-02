package com.migd.dto;

public record FullSchemaDumpResult(
        String schemaName,
        boolean success,
        String message,
        int statementCount
) {
}
