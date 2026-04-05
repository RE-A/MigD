package com.migd.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class RoutineRefExtractorTest {

    private static final String SAMPLE_DDL = """
            CREATE OR REPLACE FUNCTION hr.calc_annual_salary(p_emp_id integer)
             RETURNS numeric
             LANGUAGE plpgsql
            AS $function$
            DECLARE
                v_salary NUMERIC;
            BEGIN
                -- salary lookup
                SELECT salary INTO v_salary FROM hr.employees WHERE emp_id = p_emp_id;
                UPDATE hr.salaries SET amount = v_salary WHERE emp_id = p_emp_id;
                INSERT INTO hr.salary_log(emp_id, amount) VALUES(p_emp_id, v_salary);
                RETURN hr.get_bonus(p_emp_id) + v_salary * 12;
            END;
            $function$
            """;

    @Test
    @DisplayName("FROM 절 테이블 참조 추출")
    void extractTableRefs_from() {
        Set<String> known = Set.of("employees", "salaries", "salary_log");
        Set<String> refs = RoutineRefExtractor.extractTableRefs(SAMPLE_DDL, known);
        assertThat(refs).contains("employees");
    }

    @Test
    @DisplayName("JOIN/UPDATE/INSERT INTO 테이블 참조 추출")
    void extractTableRefs_multipleKeywords() {
        Set<String> known = Set.of("employees", "salaries", "salary_log");
        Set<String> refs = RoutineRefExtractor.extractTableRefs(SAMPLE_DDL, known);
        assertThat(refs).containsExactlyInAnyOrder("employees", "salaries", "salary_log");
    }

    @Test
    @DisplayName("known 목록에 없는 테이블은 결과에서 제외")
    void extractTableRefs_unknownFiltered() {
        Set<String> known = Set.of("employees");
        Set<String> refs = RoutineRefExtractor.extractTableRefs(SAMPLE_DDL, known);
        assertThat(refs).containsOnly("employees");
        assertThat(refs).doesNotContain("salaries", "salary_log");
    }

    @Test
    @DisplayName("루틴 참조 추출 — 자기 자신(calc_annual_salary) 제외")
    void extractRoutineRefs_excludesSelf() {
        Set<String> known = Set.of("calc_annual_salary", "get_bonus");
        Set<String> refs = RoutineRefExtractor.extractRoutineRefs(
                SAMPLE_DDL, "calc_annual_salary", known);
        assertThat(refs).containsOnly("get_bonus");
        assertThat(refs).doesNotContain("calc_annual_salary");
    }

    @Test
    @DisplayName("known 목록에 없는 루틴은 결과에서 제외")
    void extractRoutineRefs_unknownFiltered() {
        Set<String> known = Set.of("some_other_func");
        Set<String> refs = RoutineRefExtractor.extractRoutineRefs(
                SAMPLE_DDL, "calc_annual_salary", known);
        assertThat(refs).isEmpty();
    }

    @Test
    @DisplayName("DDL이 null이면 빈 Set 반환")
    void extractTableRefs_nullDdl() {
        Set<String> refs = RoutineRefExtractor.extractTableRefs(null, Set.of("employees"));
        assertThat(refs).isEmpty();
    }

    @Test
    @DisplayName("DDL이 null이면 루틴 참조도 빈 Set 반환")
    void extractRoutineRefs_nullDdl() {
        Set<String> refs = RoutineRefExtractor.extractRoutineRefs(null, "func", Set.of("func"));
        assertThat(refs).isEmpty();
    }

    @Test
    @DisplayName("스키마 한정자(hr.) 제거 후 테이블명 매칭")
    void extractTableRefs_unqualified() {
        String ddl = "BEGIN SELECT * FROM hr.departments WHERE id = 1; END;";
        Set<String> known = Set.of("departments");
        Set<String> refs = RoutineRefExtractor.extractTableRefs(ddl, known);
        assertThat(refs).containsOnly("departments");
    }
}
