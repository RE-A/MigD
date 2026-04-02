package com.migd.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PresetTable {
    private Long id;
    private Long presetId;
    private String schemaName;
    private String tableName;
    private String whereCondition; // null이면 전체 이관
    private int orderNum;
}
