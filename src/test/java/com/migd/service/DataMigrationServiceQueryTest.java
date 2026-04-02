package com.migd.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DataMigrationService의 SQL 조립 로직 단위 테스트.
 * - buildCopyOutQuery: WHERE 조건이 COPY OUT용 SELECT에 올바르게 조립되는지 검증
 * - buildCopyInQuery:  COPY IN 쿼리 형태 검증
 * - 최종 COPY OUT SQL 전체 형태(COPY (...) TO STDOUT WITH (FORMAT BINARY)) 검증
 */
class DataMigrationServiceQueryTest {

    private DataMigrationService service;
    private Method buildCopyOutQuery;
    private Method buildCopyInQuery;

    @BeforeEach
    void setUp() throws Exception {
        service = new DataMigrationService();

        buildCopyOutQuery = DataMigrationService.class
                .getDeclaredMethod("buildCopyOutQuery", String.class, String.class, String.class);
        buildCopyOutQuery.setAccessible(true);

        buildCopyInQuery = DataMigrationService.class
                .getDeclaredMethod("buildCopyInQuery", String.class, String.class);
        buildCopyInQuery.setAccessible(true);
    }

    // ── buildCopyOutQuery ──────────────────────────────────────────────────

    @Test
    @DisplayName("WHERE 조건이 null이면 전체 SELECT")
    void whereNull_전체SELECT() throws Exception {
        String sql = invoke(buildCopyOutQuery, "public", "users", null);
        assertThat(sql).isEqualTo("SELECT * FROM \"public\".\"users\"");
    }

    @Test
    @DisplayName("WHERE 조건이 빈 문자열이면 전체 SELECT")
    void whereEmpty_전체SELECT() throws Exception {
        String sql = invoke(buildCopyOutQuery, "public", "users", "");
        assertThat(sql).isEqualTo("SELECT * FROM \"public\".\"users\"");
    }

    @Test
    @DisplayName("WHERE 조건이 공백만 있으면 전체 SELECT")
    void whereBlank_전체SELECT() throws Exception {
        String sql = invoke(buildCopyOutQuery, "public", "users", "   ");
        assertThat(sql).isEqualTo("SELECT * FROM \"public\".\"users\"");
    }

    @Test
    @DisplayName("단순 WHERE 조건이 SELECT WHERE절로 조립됨")
    void where단순조건() throws Exception {
        String sql = invoke(buildCopyOutQuery, "public", "orders", "status = 'A'");
        assertThat(sql).isEqualTo("SELECT * FROM \"public\".\"orders\" WHERE status = 'A'");
    }

    @Test
    @DisplayName("복합 AND 조건이 WHERE절로 조립됨")
    void where복합AND조건() throws Exception {
        String cond = "created_at > '2024-01-01' AND status = 'A'";
        String sql = invoke(buildCopyOutQuery, "public", "orders", cond);
        assertThat(sql).isEqualTo("SELECT * FROM \"public\".\"orders\" WHERE " + cond);
    }

    @Test
    @DisplayName("스키마명이 public이 아닌 경우 인용부호로 정상 처리됨")
    void where다른스키마() throws Exception {
        String sql = invoke(buildCopyOutQuery, "myschema", "order_detail", "id > 100");
        assertThat(sql).isEqualTo("SELECT * FROM \"myschema\".\"order_detail\" WHERE id > 100");
    }

    @Test
    @DisplayName("숫자 비교 WHERE 조건 조립")
    void where숫자조건() throws Exception {
        String sql = invoke(buildCopyOutQuery, "public", "product", "price >= 1000");
        assertThat(sql).isEqualTo("SELECT * FROM \"public\".\"product\" WHERE price >= 1000");
    }

    @Test
    @DisplayName("IN 절 포함 WHERE 조건 조립")
    void whereIN절() throws Exception {
        String cond = "status IN ('A', 'B', 'C')";
        String sql = invoke(buildCopyOutQuery, "public", "users", cond);
        assertThat(sql).isEqualTo("SELECT * FROM \"public\".\"users\" WHERE " + cond);
    }

    // ── 최종 COPY OUT SQL 전체 형태 검증 ──────────────────────────────────

    @Test
    @DisplayName("WHERE 없을 때 최종 COPY OUT SQL 전체 형태")
    void copyOut전체SQL_WHERE없음() throws Exception {
        String selectSql = invoke(buildCopyOutQuery, "public", "users", null);
        String copyOutSql = "COPY (" + selectSql + ") TO STDOUT WITH (FORMAT BINARY)";

        assertThat(copyOutSql)
                .isEqualTo("COPY (SELECT * FROM \"public\".\"users\") TO STDOUT WITH (FORMAT BINARY)");
    }

    @Test
    @DisplayName("WHERE 있을 때 최종 COPY OUT SQL 전체 형태")
    void copyOut전체SQL_WHERE있음() throws Exception {
        String selectSql = invoke(buildCopyOutQuery, "public", "orders", "status = 'A'");
        String copyOutSql = "COPY (" + selectSql + ") TO STDOUT WITH (FORMAT BINARY)";

        assertThat(copyOutSql)
                .isEqualTo("COPY (SELECT * FROM \"public\".\"orders\" WHERE status = 'A') TO STDOUT WITH (FORMAT BINARY)");
    }

    @Test
    @DisplayName("복합 WHERE 있을 때 최종 COPY OUT SQL 전체 형태")
    void copyOut전체SQL_복합WHERE() throws Exception {
        String cond = "created_at > '2024-01-01' AND status = 'A'";
        String selectSql = invoke(buildCopyOutQuery, "myschema", "order_detail", cond);
        String copyOutSql = "COPY (" + selectSql + ") TO STDOUT WITH (FORMAT BINARY)";

        assertThat(copyOutSql)
                .isEqualTo("COPY (SELECT * FROM \"myschema\".\"order_detail\" WHERE "
                        + cond + ") TO STDOUT WITH (FORMAT BINARY)");
    }

    // ── buildCopyInQuery ───────────────────────────────────────────────────

    @Test
    @DisplayName("COPY IN SQL 형태 검증")
    void copyIn쿼리형태() throws Exception {
        String sql = invoke(buildCopyInQuery, "public", "users");
        assertThat(sql).isEqualTo("COPY \"public\".\"users\" FROM STDIN WITH (FORMAT BINARY)");
    }

    @Test
    @DisplayName("COPY IN SQL - 다른 스키마")
    void copyIn다른스키마() throws Exception {
        String sql = invoke(buildCopyInQuery, "myschema", "order_detail");
        assertThat(sql).isEqualTo("COPY \"myschema\".\"order_detail\" FROM STDIN WITH (FORMAT BINARY)");
    }

    // ── 헬퍼 ──────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private <T> T invoke(Method method, Object... args) throws Exception {
        return (T) method.invoke(service, args);
    }
}
