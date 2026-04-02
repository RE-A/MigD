package com.migd.dto;

import com.migd.domain.PresetTable;

public record TableMigrationResult(
        PresetTable table,
        long rowsCopied,
        String errorMessage
) {
    public boolean isSuccess() {
        return errorMessage == null;
    }

    public String getFullTableName() {
        return table.getSchemaName() + "." + table.getTableName();
    }
}
