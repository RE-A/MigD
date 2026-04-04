package com.migd.integration;

import com.migd.domain.PresetTable;
import com.migd.dto.DbConnInfo;
import com.migd.dto.MigrationResult;
import com.migd.dto.SchemaResult;
import com.migd.service.DataMigrationService;
import com.migd.service.SchemaService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.MountableFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SpringBootTest
@DisplayName("PostgreSQL Docker 통합 테스트 — 스키마/데이터 이관")
class PostgresMigrationIntegrationTest {

    private static final String DB_NAME     = "testdb";
    private static final String DB_USER     = "testuser";
    private static final String DB_PASSWORD = "testpass";
    private static final String HR_SCHEMA   = "hr";

    /** 이관 순서 (FK 없으므로 순서 무관하나 논리적 순서 유지) */
    private static final List<String> ALL_TABLES = List.of(
            "departments", "job_titles", "employees", "salary_history",
            "projects", "project_assignments", "leave_requests", "attendance",
            "performance_reviews", "training_courses", "training_enrollments",
            "benefits", "employee_benefits", "assets", "asset_assignments",
            "announcements", "documents", "expense_reports", "expense_items",
            "audit_logs"
    );

    /** 테이블별 기대 행 수 */
    private static final Map<String, Integer> EXPECTED_COUNTS = Map.ofEntries(
            Map.entry("departments",          5),
            Map.entry("job_titles",           8),
            Map.entry("employees",           20),
            Map.entry("salary_history",      10),
            Map.entry("projects",             6),
            Map.entry("project_assignments", 15),
            Map.entry("leave_requests",      10),
            Map.entry("attendance",          30),
            Map.entry("performance_reviews", 12),
            Map.entry("training_courses",     5),
            Map.entry("training_enrollments",10),
            Map.entry("benefits",             4),
            Map.entry("employee_benefits",   12),
            Map.entry("assets",              10),
            Map.entry("asset_assignments",    8),
            Map.entry("announcements",        5),
            Map.entry("documents",            8),
            Map.entry("expense_reports",      6),
            Map.entry("expense_items",       15),
            Map.entry("audit_logs",          20)
    );

    private static final long TOTAL_ROWS = 219L;

    // ── 컨테이너 (static — 클래스 전체 1회 기동) ────────────────────────

    @org.testcontainers.junit.jupiter.Container
    static final PostgreSQLContainer<?> SOURCE = new PostgreSQLContainer<>("postgres:14")
            .withDatabaseName(DB_NAME)
            .withUsername(DB_USER)
            .withPassword(DB_PASSWORD)
            .withInitScript("db/init-source.sql");

    @org.testcontainers.junit.jupiter.Container
    static final PostgreSQLContainer<?> TARGET = new PostgreSQLContainer<>("postgres:14")
            .withDatabaseName(DB_NAME)
            .withUsername(DB_USER)
            .withPassword(DB_PASSWORD);

    // ── Spring Bean ─────────────────────────────────────────────────────

    @Autowired
    DataMigrationService dataMigrationService;

    @Autowired
    SchemaService schemaService;

    // ── DbConnInfo 팩토리 ────────────────────────────────────────────────

    private static DbConnInfo srcConn() {
        return new DbConnInfo(SOURCE.getHost(), SOURCE.getMappedPort(5432), DB_NAME, DB_USER, DB_PASSWORD);
    }

    private static DbConnInfo tgtConn() {
        return new DbConnInfo(TARGET.getHost(), TARGET.getMappedPort(5432), DB_NAME, DB_USER, DB_PASSWORD);
    }

    // ════════════════════════════════════════════════════════════════════
    // Order-1: pg_dump 스키마 이관
    // ════════════════════════════════════════════════════════════════════

    @Test
    @Order(1)
    @DisplayName("[Order-1] pg_dump으로 hr 스키마 전체 덤프 후 타겟에 적용")
    void test1_schemaDump_applyToTarget() throws Exception {
        // when: 소스 컨테이너 내부 pg_dump 실행 → DDL 추출
        String ddl = dumpSchemaFromSource(HR_SCHEMA);
        assertThat(ddl).isNotBlank();

        // DDL을 타겟 컨테이너에 파일로 복사 후 psql로 적용
        applyDdlToTarget(ddl);

        // then: 20개 테이블 모두 존재
        for (String tbl : ALL_TABLES) {
            assertThat(tableExists(TARGET, HR_SCHEMA, tbl))
                    .as("테이블 존재 확인: hr.%s", tbl)
                    .isTrue();
        }

        // then: 함수 3개 존재
        assertThat(routineExists(TARGET, HR_SCHEMA, "get_dept_headcount", "FUNCTION")).isTrue();
        assertThat(routineExists(TARGET, HR_SCHEMA, "get_tenure_years",   "FUNCTION")).isTrue();
        assertThat(routineExists(TARGET, HR_SCHEMA, "calc_annual_salary", "FUNCTION")).isTrue();

        // then: 프로시저 4개 존재
        assertThat(routineExists(TARGET, HR_SCHEMA, "raise_salary",      "PROCEDURE")).isTrue();
        assertThat(routineExists(TARGET, HR_SCHEMA, "transfer_employee", "PROCEDURE")).isTrue();
        assertThat(routineExists(TARGET, HR_SCHEMA, "close_project",     "PROCEDURE")).isTrue();
        assertThat(routineExists(TARGET, HR_SCHEMA, "archive_old_logs",  "PROCEDURE")).isTrue();

        // then: 데이터는 없어야 함 (스키마만 덤프)
        assertThat(countRows(TARGET, HR_SCHEMA, "departments")).isZero();
        assertThat(countRows(TARGET, HR_SCHEMA, "employees")).isZero();
    }

    // ════════════════════════════════════════════════════════════════════
    // Order-2: ensureSchemas — 이미 존재 시 SKIPPED
    // ════════════════════════════════════════════════════════════════════

    @Test
    @Order(2)
    @DisplayName("[Order-2] ensureSchemas: 타겟에 테이블 이미 존재 → 모두 SKIPPED 반환")
    void test2_ensureSchemas_skippedWhenExists() {
        // test1에서 타겟에 테이블 생성 완료
        // tableExistsOnTarget() == true → pg_dump 미실행, SKIPPED 바로 반환
        List<PresetTable> tables = List.of(
                table(HR_SCHEMA, "departments", null, 0),
                table(HR_SCHEMA, "employees",   null, 1),
                table(HR_SCHEMA, "projects",    null, 2),
                table(HR_SCHEMA, "audit_logs",  null, 3)
        );

        List<SchemaResult> results = schemaService.ensureSchemas(
                srcConn(), tgtConn(), tables,
                "pg_dump" // 실제 미실행 (테이블 이미 존재)
        );

        assertThat(results).hasSize(4);
        assertThat(results).allMatch(r -> r.status() == SchemaResult.Status.SKIPPED);
        assertThat(results).allMatch(SchemaResult::isSuccess);
    }

    // ════════════════════════════════════════════════════════════════════
    // Order-3: 20개 테이블 전체 이관
    // ════════════════════════════════════════════════════════════════════

    @Test
    @Order(3)
    @DisplayName("[Order-3] migrateAll: 20개 테이블 전체 이관 — 각 테이블 행 수 검증")
    void test3_migrateAll_fullTransfer() throws SQLException {
        // 타겟 스키마 보장 (test1 결과 위에 IF NOT EXISTS로 멱등 실행)
        ensureHrSchemaOnTarget();
        clearAllTablesOnTarget();

        List<PresetTable> tables = IntStream.range(0, ALL_TABLES.size())
                .mapToObj(i -> table(HR_SCHEMA, ALL_TABLES.get(i), null, i))
                .toList();

        // when
        MigrationResult result = dataMigrationService.migrateAll(srcConn(), tgtConn(), tables);

        // then: 전체 성공
        assertThat(result.hasErrors())
                .as("이관 오류 없어야 함. 실패: %s",
                    result.getTableResults().stream()
                          .filter(r -> !r.isSuccess())
                          .map(r -> r.getFullTableName() + ": " + r.errorMessage())
                          .toList())
                .isFalse();
        assertThat(result.getSuccessCount()).isEqualTo(20);

        // then: 테이블별 행 수 검증
        for (String tbl : ALL_TABLES) {
            assertThat(countRows(TARGET, HR_SCHEMA, tbl))
                    .as("hr.%s 행 수", tbl)
                    .isEqualTo((long) EXPECTED_COUNTS.get(tbl));
        }

        // then: 전체 이관 행 수
        assertThat(result.getTotalRowsCopied()).isEqualTo(TOTAL_ROWS);
    }

    // ════════════════════════════════════════════════════════════════════
    // Order-4: WHERE 조건 부분 이관
    // ════════════════════════════════════════════════════════════════════

    @Test
    @Order(4)
    @DisplayName("[Order-4] migrateAll: WHERE is_active=TRUE → 비활성 직원 3명 제외")
    void test4_migrateAll_whereCondition() throws SQLException {
        // 타겟 employees만 초기화 (FK 없으므로 단독 DELETE 가능)
        clearTableOnTarget(HR_SCHEMA, "employees");

        List<PresetTable> tables = List.of(
                table(HR_SCHEMA, "employees", "is_active = TRUE", 0)
        );

        // when
        MigrationResult result = dataMigrationService.migrateAll(srcConn(), tgtConn(), tables);

        // then
        assertThat(result.hasErrors()).isFalse();

        // 소스 20건 중 is_active=FALSE 3명(carol.park, jake.kang, noah.jang) 제외 → 17건
        assertThat(countRows(TARGET, HR_SCHEMA, "employees")).isEqualTo(17);

        // 비활성 3명이 타겟에 없어야 함
        assertThat(countRowsWhere(TARGET, HR_SCHEMA, "employees",
                "email = 'carol.park@example.com'")).isZero();
        assertThat(countRowsWhere(TARGET, HR_SCHEMA, "employees",
                "email = 'jake.kang@example.com'")).isZero();
        assertThat(countRowsWhere(TARGET, HR_SCHEMA, "employees",
                "email = 'noah.jang@example.com'")).isZero();

        // TableMigrationResult 복사 행 수
        assertThat(result.getTableResults().get(0).rowsCopied()).isEqualTo(17);
    }

    // ════════════════════════════════════════════════════════════════════
    // Order-5: PK 중복 → 롤백 + 에러 메시지
    // ════════════════════════════════════════════════════════════════════

    @Test
    @Order(5)
    @DisplayName("[Order-5] migrateAll: 타겟에 데이터 존재 시 PK 중복 → 롤백 + 에러 메시지")
    void test5_migrateAll_pkDuplicateRollback() throws SQLException {
        // test3에서 departments 5건 이관 완료
        long beforeCount = countRows(TARGET, HR_SCHEMA, "departments");
        assertThat(beforeCount).isEqualTo(5);

        List<PresetTable> tables = List.of(
                table(HR_SCHEMA, "departments", null, 0)
        );

        // when: 동일 데이터 재이관 시도
        MigrationResult result = dataMigrationService.migrateAll(srcConn(), tgtConn(), tables);

        // then: 실패
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getFailCount()).isEqualTo(1);
        assertThat(result.getSuccessCount()).isZero();

        // then: PK 중복 오류 메시지
        String errorMsg = result.getTableResults().get(0).errorMessage();
        assertThat(errorMsg).contains("PK 중복 오류");
        assertThat(errorMsg).contains("departments");

        // then: 롤백 확인 — 행 수 불변
        assertThat(countRows(TARGET, HR_SCHEMA, "departments")).isEqualTo(beforeCount);
    }

    // ── 내부 헬퍼 ────────────────────────────────────────────────────────

    /** 소스 컨테이너 내부 pg_dump로 스키마 DDL 추출 */
    private String dumpSchemaFromSource(String schema) throws Exception {
        Container.ExecResult r = SOURCE.execInContainer(
                "bash", "-c",
                String.format(
                        "PGPASSWORD=%s pg_dump -h localhost -U %s -d %s -n %s -s --no-owner --no-acl --no-comments",
                        DB_PASSWORD, DB_USER, DB_NAME, schema
                )
        );
        if (r.getExitCode() != 0) {
            throw new RuntimeException("pg_dump 실패:\n" + r.getStderr());
        }
        return r.getStdout();
    }

    /** DDL을 호스트 임시 파일 → 타겟 컨테이너 복사 후 psql로 실행 */
    private void applyDdlToTarget(String ddl) throws Exception {
        Path tmpFile = Files.createTempFile("schema_dump", ".sql");
        try {
            Files.writeString(tmpFile, ddl, StandardCharsets.UTF_8);
            TARGET.copyFileToContainer(MountableFile.forHostPath(tmpFile), "/tmp/schema_dump.sql");
            Container.ExecResult r = TARGET.execInContainer(
                    "bash", "-c",
                    String.format(
                            "PGPASSWORD=%s psql -h localhost -U %s -d %s -f /tmp/schema_dump.sql",
                            DB_PASSWORD, DB_USER, DB_NAME
                    )
            );
            if (r.getExitCode() != 0) {
                throw new RuntimeException("psql 적용 실패:\n" + r.getStderr());
            }
        } finally {
            Files.deleteIfExists(tmpFile);
        }
    }

    /** 타겟에 hr 스키마 + 20개 테이블 보장 (IF NOT EXISTS 멱등) */
    private void ensureHrSchemaOnTarget() throws SQLException {
        try (Connection conn = DriverManager.getConnection(
                TARGET.getJdbcUrl(), TARGET.getUsername(), TARGET.getPassword())) {
            ScriptUtils.executeSqlScript(conn, new ClassPathResource("db/create-target-schema.sql"));
        }
    }

    /** 20개 테이블 전체 데이터 삭제 (FK 없으므로 순서 무관) */
    private void clearAllTablesOnTarget() throws SQLException {
        try (Connection conn = DriverManager.getConnection(
                TARGET.getJdbcUrl(), TARGET.getUsername(), TARGET.getPassword());
             Statement stmt = conn.createStatement()) {
            for (String tbl : ALL_TABLES) {
                stmt.execute("DELETE FROM hr.\"" + tbl + "\"");
            }
        }
    }

    private void clearTableOnTarget(String schema, String table) throws SQLException {
        try (Connection conn = DriverManager.getConnection(
                TARGET.getJdbcUrl(), TARGET.getUsername(), TARGET.getPassword());
             Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM \"" + schema + "\".\"" + table + "\"");
        }
    }

    private long countRows(PostgreSQLContainer<?> container,
                           String schema, String table) throws SQLException {
        try (Connection conn = DriverManager.getConnection(
                container.getJdbcUrl(), container.getUsername(), container.getPassword());
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT COUNT(*) FROM \"" + schema + "\".\"" + table + "\"")) {
            return rs.next() ? rs.getLong(1) : 0L;
        }
    }

    private long countRowsWhere(PostgreSQLContainer<?> container,
                                String schema, String table, String where) throws SQLException {
        try (Connection conn = DriverManager.getConnection(
                container.getJdbcUrl(), container.getUsername(), container.getPassword());
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT COUNT(*) FROM \"" + schema + "\".\"" + table + "\" WHERE " + where)) {
            return rs.next() ? rs.getLong(1) : 0L;
        }
    }

    private boolean tableExists(PostgreSQLContainer<?> container,
                                String schema, String table) throws SQLException {
        String sql = "SELECT COUNT(*) FROM information_schema.tables "
                + "WHERE table_schema = '" + schema + "' AND table_name = '" + table + "'";
        try (Connection conn = DriverManager.getConnection(
                container.getJdbcUrl(), container.getUsername(), container.getPassword());
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            return rs.next() && rs.getLong(1) > 0;
        }
    }

    private boolean routineExists(PostgreSQLContainer<?> container,
                                  String schema, String name, String type) throws SQLException {
        String sql = "SELECT COUNT(*) FROM information_schema.routines "
                + "WHERE routine_schema = '" + schema + "' "
                + "AND routine_name = '" + name + "' "
                + "AND routine_type = '" + type + "'";
        try (Connection conn = DriverManager.getConnection(
                container.getJdbcUrl(), container.getUsername(), container.getPassword());
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            return rs.next() && rs.getLong(1) > 0;
        }
    }

    private static PresetTable table(String schema, String tableName, String where, int order) {
        return PresetTable.builder()
                .schemaName(schema)
                .tableName(tableName)
                .whereCondition(where)
                .orderNum(order)
                .build();
    }
}
