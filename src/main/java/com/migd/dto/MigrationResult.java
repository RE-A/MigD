package com.migd.dto;

import java.time.LocalDateTime;
import java.util.List;

public class MigrationResult {
    private final List<TableMigrationResult> tableResults;
    private final LocalDateTime executedAt;

    public MigrationResult(List<TableMigrationResult> tableResults) {
        this.tableResults = tableResults;
        this.executedAt = LocalDateTime.now();
    }

    public List<TableMigrationResult> getTableResults() {
        return tableResults;
    }

    public LocalDateTime getExecutedAt() {
        return executedAt;
    }

    public long getTotalRowsCopied() {
        return tableResults.stream()
                .filter(TableMigrationResult::isSuccess)
                .mapToLong(TableMigrationResult::rowsCopied)
                .sum();
    }

    public long getSuccessCount() {
        return tableResults.stream().filter(TableMigrationResult::isSuccess).count();
    }

    public long getFailCount() {
        return tableResults.stream().filter(r -> !r.isSuccess()).count();
    }

    public boolean hasErrors() {
        return tableResults.stream().anyMatch(r -> !r.isSuccess());
    }
}
