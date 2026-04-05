package com.migd.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ColumnSearchResult {
    private String schemaName;
    private String tableName;
    private String columnName;
    private String dataType;
    private boolean isPk;
    private String columnComment;
    private Long catalogId;
    private Long tableId;
}
