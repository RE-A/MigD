package com.migd.domain;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CatalogColumn {
    private Long id;
    private Long catalogTableId;
    private Long catalogId;
    private String schemaName;
    private String tableName;
    private String columnName;
    private String columnNameLower;
    private String dataType;
    private int ordinalPosition;
    private boolean isNullable;
    private boolean isPk;
    private String columnComment;
}
