package com.migd.domain;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SchemaCatalog {
    private Long id;
    private String schemaName;
    private String dbHost;
    private String dbName;
    private LocalDateTime analyzedAt;
    private int tableCount;
    private int routineCount;
}
