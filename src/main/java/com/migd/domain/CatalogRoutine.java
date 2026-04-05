package com.migd.domain;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CatalogRoutine {
    private Long id;
    private Long catalogId;
    private String schemaName;
    private String routineName;
    private String routineType;
    private String ddlBody;
}
