package com.migd.integration;

import com.migd.dto.DbConnInfo;
import com.migd.dto.FullSchemaDumpResult;
import com.migd.service.SchemaService;
import com.migd.util.SqlSplitter;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.File;
import java.sql.*;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * SchemaService.applyFullSchemaDump() 및 SqlSplitter 통합 테스트.
 *
 * Order-1,2: execInContainer로 추출한 pg_dump DDL을 SqlSplitter + JDBC로 타겟 적용 → DDL 검증.
 *            Docker만 있으면 실행 가능 (호스트 pg_dump 불필요).
 * Order-3:   SchemaService.applyFullSchemaDump() 전체 경로 검증.
 *            호스트에 pg_dump가 없으면 자동 스킵 (assumeTrue).
 */
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@SpringBootTest
@DisplayName("DDL 마이그레이션 통합 테스트 — SqlSplitter + SchemaService")
class SchemaDdlMigrationIntegrationTest {

    private static final String DB_NAME     = "testdb";
    private static final String DB_USER     = "testuser";
    private static final String DB_PASSWORD = "testpass";
    private static final String HR_SCHEMA   = "hr";

    private static final List<String> ALL_TABLES = List.of(
            "departments", "job_titles", "employees", "salary_history",
            "projects", "project_assignments", "leave_requests", "attendance",
            "performance_reviews", "training_courses", "training_enrollments",
            "benefits", "employee_benefits", "assets", "asset_assignments",
            "announcements", "documents", "expense_reports", "expense_items",
            "audit_logs"
    );

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

    @Autowired
    SchemaService schemaService;

    // ════════════════════════════════════════════════════════════════════
    // Order-1: execInContainer pg_dump → SqlSplitter → JDBC 적용 → 테이블 검증
    // ════════════════════════════════════════════════════════════════════

    @Test
    @Order(1)
    @DisplayName("[Order-1] pg_dump DDL을 SqlSplitter로 파싱 후 JDBC 직접 적용 — 테이블 20개 생성 확인")
    void ddl_extractedByPgDump_appliedViaJavaSplitter_tablesCreated() throws Exception {
        // 소스 컨테이너 내부 pg_dump로 hr 스키마 DDL 추출
        String rawDdl = dumpSchemaFromSource(HR_SCHEMA);
        assertThat(rawDdl).isNotBlank();

        // SqlSplitter로 파싱 (이것이 실제 버그 발생 코드 경로)
        String filtered = SqlSplitter.filterDdlLines(rawDdl);
        List<String> statements = SqlSplitter.splitStatements(filtered);

        assertThat(statements)
                .as("DDL 구문이 1개 이상이어야 함")
                .isNotEmpty();

        // 타겟 JDBC로 직접 실행
        try (Connection conn = DriverManager.getConnection(
                TARGET.getJdbcUrl(), TARGET.getUsername(), TARGET.getPassword());
             Statement stmt = conn.createStatement()) {
            for (String sql : statements) {
                String trimmed = sql.strip();
                if (!trimmed.isEmpty()) {
                    stmt.execute(trimmed);
                }
            }
        }

        // 검증: 20개 테이블 모두 존재
        for (String tbl : ALL_TABLES) {
            assertThat(tableExists(HR_SCHEMA, tbl))
                    .as("테이블 존재 확인: hr.%s", tbl)
                    .isTrue();
        }

        // 검증: 시퀀스 4개 존재
        assertThat(sequenceCount(HR_SCHEMA))
                .as("hr 스키마 시퀀스 수")
                .isGreaterThanOrEqualTo(4);

        // 검증: 인덱스 8개 이상 (init-source.sql 기준)
        assertThat(indexCount(HR_SCHEMA))
                .as("hr 스키마 인덱스 수")
                .isGreaterThanOrEqualTo(8);
    }

    // ════════════════════════════════════════════════════════════════════
    // Order-2: 루틴 존재 + 실행 가능성 검증
    // ════════════════════════════════════════════════════════════════════

    @Test
    @Order(2)
    @DisplayName("[Order-2] 함수 3개 + 프로시저 4개 존재 확인 및 실제 호출 가능성 검증")
    void routines_existAndAreCallable() throws SQLException {
        // 함수 3개 존재
        assertThat(routineExists(HR_SCHEMA, "get_dept_headcount", "FUNCTION")).isTrue();
        assertThat(routineExists(HR_SCHEMA, "get_tenure_years",   "FUNCTION")).isTrue();
        assertThat(routineExists(HR_SCHEMA, "calc_annual_salary", "FUNCTION")).isTrue();

        // 프로시저 4개 존재
        assertThat(routineExists(HR_SCHEMA, "raise_salary",      "PROCEDURE")).isTrue();
        assertThat(routineExists(HR_SCHEMA, "transfer_employee", "PROCEDURE")).isTrue();
        assertThat(routineExists(HR_SCHEMA, "close_project",     "PROCEDURE")).isTrue();
        assertThat(routineExists(HR_SCHEMA, "archive_old_logs",  "PROCEDURE")).isTrue();

        // 함수 실제 호출 — 타겟엔 데이터가 없으므로 0 또는 NULL 반환이어야 함 (에러 없어야 함)
        try (Connection conn = DriverManager.getConnection(
                TARGET.getJdbcUrl(), TARGET.getUsername(), TARGET.getPassword());
             Statement stmt = conn.createStatement()) {

            // get_dept_headcount: 비어있으면 0 반환
            try (ResultSet rs = stmt.executeQuery("SELECT hr.get_dept_headcount(1)")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getLong(1)).isZero();
            }

            // calc_annual_salary: 없는 사원 ID → NULL 반환 (FOUND = false 분기)
            try (ResultSet rs = stmt.executeQuery("SELECT hr.calc_annual_salary(99999)")) {
                assertThat(rs.next()).isTrue();
                // NULL이거나 숫자 — 어떤 경우든 에러 없이 반환되면 OK
                rs.getObject(1); // 예외 없으면 통과
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════
    // Order-3: SchemaService.applyFullSchemaDump() 전체 경로
    //          호스트에 pg_dump 없으면 자동 스킵
    // ════════════════════════════════════════════════════════════════════

    @Test
    @Order(3)
    @DisplayName("[Order-3] SchemaService.applyDdlText() — 소스 컨테이너 pg_dump로 추출 후 SchemaService 적용 경로 검증")
    void schemaService_applyDdlText_fromContainerPgDump() throws Exception {
        // Order-1,2에서 생성된 타겟 스키마를 DROP하고 재적용하여 완전한 재현성 확보
        try (Connection conn = DriverManager.getConnection(
                TARGET.getJdbcUrl(), TARGET.getUsername(), TARGET.getPassword());
             Statement stmt = conn.createStatement()) {
            stmt.execute("DROP SCHEMA IF EXISTS hr CASCADE");
        }

        // 소스 컨테이너 내부 pg_dump로 DDL 추출 (호스트 pg_dump 불필요)
        String rawDdl = dumpSchemaFromSource(HR_SCHEMA);
        assertThat(rawDdl).isNotBlank();

        DbConnInfo tgt = new DbConnInfo(
                TARGET.getHost(), TARGET.getMappedPort(5432),
                DB_NAME, DB_USER, DB_PASSWORD);

        // SchemaService.applyDdlText() → 내부적으로 applyDdlToTarget() + SqlSplitter 사용
        int count = schemaService.applyDdlText(tgt, rawDdl);

        assertThat(count)
                .as("적용된 DDL 구문 수")
                .isGreaterThan(0);

        // 테이블 20개 존재
        for (String tbl : ALL_TABLES) {
            assertThat(tableExists(HR_SCHEMA, tbl))
                    .as("SchemaService.applyDdlText 경로로 생성된 테이블: hr.%s", tbl)
                    .isTrue();
        }

        // 함수 + 프로시저 존재 (dollar-quote 파싱 정상 작동 확인)
        assertThat(routineExists(HR_SCHEMA, "raise_salary",       "PROCEDURE")).isTrue();
        assertThat(routineExists(HR_SCHEMA, "get_dept_headcount", "FUNCTION")).isTrue();
    }

    // ── 내부 헬퍼 ────────────────────────────────────────────────────────

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

    private boolean tableExists(String schema, String table) throws SQLException {
        String sql = "SELECT COUNT(*) FROM information_schema.tables "
                + "WHERE table_schema = ? AND table_name = ?";
        try (Connection conn = DriverManager.getConnection(
                TARGET.getJdbcUrl(), TARGET.getUsername(), TARGET.getPassword());
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            ps.setString(2, table);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getLong(1) > 0;
            }
        }
    }

    private boolean routineExists(String schema, String name, String type) throws SQLException {
        String sql = "SELECT COUNT(*) FROM information_schema.routines "
                + "WHERE routine_schema = ? AND routine_name = ? AND routine_type = ?";
        try (Connection conn = DriverManager.getConnection(
                TARGET.getJdbcUrl(), TARGET.getUsername(), TARGET.getPassword());
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            ps.setString(2, name);
            ps.setString(3, type);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getLong(1) > 0;
            }
        }
    }

    private long sequenceCount(String schema) throws SQLException {
        String sql = "SELECT COUNT(*) FROM information_schema.sequences WHERE sequence_schema = ?";
        try (Connection conn = DriverManager.getConnection(
                TARGET.getJdbcUrl(), TARGET.getUsername(), TARGET.getPassword());
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        }
    }

    private long indexCount(String schema) throws SQLException {
        String sql = "SELECT COUNT(*) FROM pg_indexes WHERE schemaname = ?";
        try (Connection conn = DriverManager.getConnection(
                TARGET.getJdbcUrl(), TARGET.getUsername(), TARGET.getPassword());
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schema);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        }
    }

    /**
     * 호스트 머신에서 pg_dump 실행 파일 경로를 탐색.
     * Windows / Linux / Mac 공통.
     */
    private static String discoverPgDumpOnHost() {
        // Windows 일반 설치 경로
        String[] windowsCandidates = {
                "C:/Program Files/PostgreSQL/17/bin/pg_dump.exe",
                "C:/Program Files/PostgreSQL/16/bin/pg_dump.exe",
                "C:/Program Files/PostgreSQL/15/bin/pg_dump.exe",
                "C:/Program Files/PostgreSQL/14/bin/pg_dump.exe",
        };
        for (String path : windowsCandidates) {
            if (new File(path).exists()) return path;
        }
        // PATH 탐색 (Linux / Mac / Windows 공통)
        try {
            String os = System.getProperty("os.name", "").toLowerCase();
            String[] cmd = os.contains("win")
                    ? new String[]{"where", "pg_dump"}
                    : new String[]{"which", "pg_dump"};
            Process p = new ProcessBuilder(cmd).start();
            String out = new String(p.getInputStream().readAllBytes()).strip();
            p.waitFor();
            if (!out.isEmpty() && new File(out.split("\\r?\\n")[0]).exists()) {
                return out.split("\\r?\\n")[0];
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}
