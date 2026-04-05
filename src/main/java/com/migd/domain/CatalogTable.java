package com.migd.domain;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CatalogTable {
    private Long id;
    private Long catalogId;
    private String schemaName;
    private String tableName;
    private String tableComment;
}
