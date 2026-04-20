package com.migd.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SqlSplitter — dollar-quote 인식 SQL 분리 단위 테스트")
class SqlSplitterTest {

    // ── splitStatements ──────────────────────────────────────────────

    @Test
    @DisplayName("단순 세미콜론 → 2개 구문 분리")
    void plainSemicolon_splitsTwoStatements() {
        List<String> stmts = SqlSplitter.splitStatements("SELECT 1; SELECT 2");
        assertThat(stmts).hasSize(2);
        assertThat(stmts.get(0)).isEqualTo("SELECT 1");
        assertThat(stmts.get(1)).isEqualTo("SELECT 2");
    }

    @Test
    @DisplayName("$$ dollar-quote 내부 세미콜론 → 분리 안 됨 (함수 전체 1개)")
    void dollarQuote_semicolonInside_notSplit() {
        String sql = """
                CREATE OR REPLACE FUNCTION hr.test() RETURNS void LANGUAGE plpgsql AS $$
                DECLARE x INT;
                BEGIN
                    x := 1;
                    RETURN;
                END;
                $$;
                """;
        List<String> stmts = SqlSplitter.splitStatements(sql);
        assertThat(stmts).hasSize(1);
        assertThat(stmts.get(0)).contains("$$").contains("BEGIN").contains("END;");
    }

    @Test
    @DisplayName("$func$ 태그 dollar-quote 내부 세미콜론 → 분리 안 됨")
    void taggedDollarQuote_semicolonInside_notSplit() {
        String sql = """
                CREATE OR REPLACE FUNCTION hr.test2() RETURNS void LANGUAGE plpgsql AS $func$
                BEGIN
                    UPDATE hr.employees SET is_active = FALSE WHERE emp_id = 1;
                    INSERT INTO hr.audit_logs(table_name, operation, row_id, changed_by)
                    VALUES ('employees', 'UPDATE', 1, 'test');
                END;
                $func$;
                """;
        List<String> stmts = SqlSplitter.splitStatements(sql);
        assertThat(stmts).hasSize(1);
        assertThat(stmts.get(0)).contains("$func$").contains("UPDATE").contains("INSERT");
    }

    @Test
    @DisplayName("실제 raise_salary 프로시저 DDL — 1개로 유지")
    void realProcedureDdl_notSplit() {
        String sql = """
                CREATE OR REPLACE PROCEDURE hr.raise_salary(
                    p_emp_id     BIGINT,
                    p_raise_pct  NUMERIC,
                    p_changed_by VARCHAR DEFAULT 'system'
                )
                LANGUAGE plpgsql AS $$
                DECLARE
                    v_old NUMERIC(12,2);
                    v_new NUMERIC(12,2);
                BEGIN
                    SELECT salary INTO v_old FROM hr.employees WHERE emp_id = p_emp_id FOR UPDATE;
                    IF NOT FOUND THEN
                        RAISE EXCEPTION 'Employee % not found', p_emp_id;
                    END IF;
                    v_new := ROUND(v_old * (1 + p_raise_pct / 100.0), 2);
                    UPDATE hr.employees SET salary = v_new WHERE emp_id = p_emp_id;
                    INSERT INTO hr.salary_history(emp_id, old_salary, new_salary, changed_by)
                    VALUES (p_emp_id, v_old, v_new, p_changed_by);
                END;
                $$;
                """;
        List<String> stmts = SqlSplitter.splitStatements(sql);
        assertThat(stmts).hasSize(1);
        assertThat(stmts.get(0)).contains("CREATE OR REPLACE PROCEDURE");
        assertThat(stmts.get(0)).contains("END;");
    }

    @Test
    @DisplayName("프로시저 2개 연속 → 각각 1개씩 총 2개 분리")
    void multipleRoutines_splitCorrectly() {
        String sql = """
                CREATE OR REPLACE FUNCTION hr.f1() RETURNS INT LANGUAGE plpgsql AS $$
                BEGIN
                    RETURN 1;
                END;
                $$;
                CREATE OR REPLACE FUNCTION hr.f2() RETURNS INT LANGUAGE plpgsql AS $$
                BEGIN
                    RETURN 2;
                END;
                $$;
                """;
        List<String> stmts = SqlSplitter.splitStatements(sql);
        assertThat(stmts).hasSize(2);
        assertThat(stmts.get(0)).contains("hr.f1");
        assertThat(stmts.get(1)).contains("hr.f2");
    }

    @Test
    @DisplayName("빈 입력 → 빈 리스트")
    void emptyInput_returnsEmptyList() {
        assertThat(SqlSplitter.splitStatements("")).isEmpty();
        assertThat(SqlSplitter.splitStatements("   \n  ")).isEmpty();
    }

    // ── filterDdlLines ───────────────────────────────────────────────

    @Test
    @DisplayName("SET / 주석 / BEGIN; / COMMIT; 라인 제거")
    void filterDdlLines_removesMetaLines() {
        String ddl = """
                -- PostgreSQL database dump
                SET statement_timeout = 0;
                SET client_encoding = 'UTF8';
                BEGIN;
                CREATE SCHEMA IF NOT EXISTS hr;
                CREATE TABLE hr.departments (dept_id BIGSERIAL PRIMARY KEY);
                COMMIT;
                """;
        String filtered = SqlSplitter.filterDdlLines(ddl);
        assertThat(filtered).doesNotContain("SET ");
        assertThat(filtered).doesNotContain("-- ");
        assertThat(filtered).doesNotContain("BEGIN;");
        assertThat(filtered).doesNotContain("COMMIT;");
        assertThat(filtered).contains("CREATE SCHEMA");
        assertThat(filtered).contains("CREATE TABLE");
    }

    @Test
    @DisplayName("psql 메타커맨드 (\\connect 등) 제거")
    void filterDdlLines_removesBackslashCommands() {
        String ddl = "\\connect testdb\nCREATE SCHEMA hr;\n\\set ON_ERROR_STOP on";
        String filtered = SqlSplitter.filterDdlLines(ddl);
        assertThat(filtered).doesNotContain("\\connect");
        assertThat(filtered).doesNotContain("\\set");
        assertThat(filtered).contains("CREATE SCHEMA hr");
    }

    @Test
    @DisplayName("SET으로 시작하지 않는 일반 라인은 유지")
    void filterDdlLines_keepsNonSetLines() {
        String ddl = "SELECT pg_catalog.set_config('search_path', '', false);\nCREATE TABLE t (id INT);";
        String filtered = SqlSplitter.filterDdlLines(ddl);
        // SELECT로 시작하는 set_config 호출은 SET으로 시작하지 않으므로 유지
        assertThat(filtered).contains("pg_catalog.set_config");
        assertThat(filtered).contains("CREATE TABLE");
    }
}
