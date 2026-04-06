package com.migd.dto;

import lombok.Data;

@Data
public class CatalogAnalyzeRequest {
    private String schemaName;
    /** 콤마 구분 glob 패턴 목록. 일치하는 테이블명은 분석에서 제외. (예: tmp_*, *_bak) */
    private String excludePattern;
}
