package com.migd.dto;

public record SchemaResult(
        String schemaName,
        String tableName,
        Status status,
        String message
) {
    public enum Status {
        SKIPPED,  // 이미 테이블 존재
        CREATED,  // pg_dump 후 생성 완료
        FAILED    // 에러 발생
    }

    public boolean isSuccess() {
        return status != Status.FAILED;
    }

    public String getFullTableName() {
        return schemaName + "." + tableName;
    }
}
