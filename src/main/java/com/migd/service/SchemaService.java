package com.migd.service;

import com.migd.domain.PresetTable;
import com.migd.dto.DbConnInfo;
import com.migd.dto.FullSchemaDumpResult;
import com.migd.dto.SchemaResult;
import com.migd.util.JdbcConnectionUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SchemaService {

    /**
     * 테이블 목록의 스키마를 Target DB에 보장.
     * 테이블이 없으면 pg_dump로 DDL 추출 후 적용, 있으면 SKIPPED.
     */
    public List<SchemaResult> ensureSchemas(DbConnInfo src, DbConnInfo tgt,
                                             List<PresetTable> tables, String pgDumpPath) {
        List<SchemaResult> results = new ArrayList<>();

        try (Connection tgtConn = JdbcConnectionUtil.open(tgt)) {
            for (PresetTable table : tables) {
                results.add(processTable(src, tgtConn, table, pgDumpPath));
            }
        } catch (SQLException e) {
            log.error("Target DB 연결 실패", e);
            tables.forEach(t ->
                    results.add(new SchemaResult(t.getSchemaName(), t.getTableName(),
                            SchemaResult.Status.FAILED, "Target DB 연결 실패: " + e.getMessage()))
            );
        }

        return results;
    }

    /**
     * 지정한 스키마의 모든 오브젝트를 데이터 없이 Target DB에 적용.
     * pg_dump -s -n schemaName 사용.
     */
    public FullSchemaDumpResult applyFullSchemaDump(DbConnInfo src, DbConnInfo tgt,
                                                     String schemaName, String pgDumpPath) {
        log.info("전체 스키마 Dump 시작: schema={}", schemaName);
        try (Connection tgtConn = JdbcConnectionUtil.open(tgt)) {
            String ddl = dumpFullSchema(src, schemaName, pgDumpPath);
            int count = applyDdlToTarget(tgtConn, ddl);
            log.info("전체 스키마 Dump 완료: schema={}, statements={}", schemaName, count);
            return new FullSchemaDumpResult(schemaName, true, "적용 완료", count);
        } catch (Exception e) {
            log.error("전체 스키마 Dump 실패: schema={}", schemaName, e);
            return new FullSchemaDumpResult(schemaName, false, e.getMessage(), 0);
        }
    }

    private SchemaResult processTable(DbConnInfo src, Connection tgtConn,
                                       PresetTable table, String pgDumpPath) {
        String schema = table.getSchemaName();
        String tbl = table.getTableName();

        try {
            if (tableExistsOnTarget(tgtConn, schema, tbl)) {
                log.info("테이블 존재 - SKIP: {}.{}", schema, tbl);
                return new SchemaResult(schema, tbl, SchemaResult.Status.SKIPPED, "이미 존재");
            }

            log.info("테이블 없음 - pg_dump 실행: {}.{}", schema, tbl);
            String ddl = dumpDdlFromSource(src, schema, tbl, pgDumpPath);
            applyDdlToTarget(tgtConn, ddl);

            log.info("스키마 생성 완료: {}.{}", schema, tbl);
            return new SchemaResult(schema, tbl, SchemaResult.Status.CREATED, "DDL 적용 완료");

        } catch (Exception e) {
            log.error("스키마 동기화 실패: {}.{}", schema, tbl, e);
            return new SchemaResult(schema, tbl, SchemaResult.Status.FAILED, e.getMessage());
        }
    }

    private boolean tableExistsOnTarget(Connection conn,
                                         String schemaName, String tableName) throws SQLException {
        String sql = """
                SELECT COUNT(*) FROM information_schema.tables
                WHERE table_schema = ? AND table_name = ?
                """;
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schemaName);
            ps.setString(2, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getInt(1) > 0;
            }
        }
    }

    /**
     * pg_dump -s -t 로 테이블 단위 DDL 추출.
     */
    private String dumpDdlFromSource(DbConnInfo src, String schemaName,
                                      String tableName, String pgDumpPath)
            throws IOException, InterruptedException {

        List<String> command = List.of(
                pgDumpPath,
                "-h", src.host(),
                "-p", String.valueOf(src.port()),
                "-U", src.user(),
                "-d", src.db(),
                "-s",
                "-t", schemaName + "." + tableName,
                "--no-owner",
                "--no-acl",
                "--no-comments"
        );

        return runPgDump(src.password(), command);
    }

    /**
     * pg_dump -s -n 으로 스키마 전체 DDL 추출.
     */
    private String dumpFullSchema(DbConnInfo src, String schemaName, String pgDumpPath)
            throws IOException, InterruptedException {

        List<String> command = List.of(
                pgDumpPath,
                "-h", src.host(),
                "-p", String.valueOf(src.port()),
                "-U", src.user(),
                "-d", src.db(),
                "-s",
                "-n", schemaName,
                "--no-owner",
                "--no-acl",
                "--no-comments"
        );

        return runPgDump(src.password(), command);
    }

    /**
     * ProcessBuilder로 pg_dump 실행.
     * stdout/stderr를 가상 스레드로 동시에 읽어 OS 파이프 버퍼 블락 방지.
     */
    private String runPgDump(String srcPassword, List<String> command)
            throws IOException, InterruptedException {

        log.debug("pg_dump 명령: {}", String.join(" ", command));

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.environment().put("PGPASSWORD", srcPassword);
        pb.environment().put("PGCLIENTENCODING", "UTF8");
        pb.redirectErrorStream(false);

        Process process = pb.start();

        StringBuilder stdout = new StringBuilder();
        StringBuilder stderr = new StringBuilder();

        Thread stdoutReader = Thread.ofVirtual().start(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                reader.lines().forEach(line -> stdout.append(line).append('\n'));
            } catch (IOException e) {
                log.error("pg_dump stdout 읽기 오류", e);
            }
        });

        Thread stderrReader = Thread.ofVirtual().start(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                reader.lines().forEach(line -> stderr.append(line).append('\n'));
            } catch (IOException e) {
                log.error("pg_dump stderr 읽기 오류", e);
            }
        });

        try {
            int exitCode = process.waitFor();
            stdoutReader.join();
            stderrReader.join();

            if (exitCode != 0) {
                throw new RuntimeException("pg_dump 실패 (exit " + exitCode + "): " + stderr);
            }
            if (!stderr.isEmpty()) {
                log.warn("pg_dump stderr: {}", stderr);
            }

            return stdout.toString();
        } finally {
            process.destroy();
        }
    }

    /**
     * pg_dump 출력에서 메타데이터 제거 후 Target DB에 DDL 적용.
     * dollar-quote($$ 또는 $tag$) 블록 내부의 ';'는 구분자로 취급하지 않는다.
     */
    private int applyDdlToTarget(Connection targetConn, String ddlText) throws SQLException {
        String filtered = Arrays.stream(ddlText.split("\n"))
                .filter(line -> !line.startsWith("--"))
                .filter(line -> !line.startsWith("SET "))
                .filter(line -> !line.equals("BEGIN;"))
                .filter(line -> !line.equals("COMMIT;"))
                .collect(Collectors.joining("\n"));

        List<String> statements = splitStatements(filtered);
        int count = 0;
        try (Statement stmt = targetConn.createStatement()) {
            for (String sql : statements) {
                String trimmed = sql.strip();
                if (!trimmed.isEmpty()) {
                    log.debug("DDL 실행: {}", trimmed.substring(0, Math.min(80, trimmed.length())));
                    stmt.execute(trimmed);
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * SQL 텍스트를 ';' 기준으로 분리하되, dollar-quote 블록 내부는 건너뛴다.
     * PostgreSQL dollar-quote: $$ ... $$ 또는 $tag$ ... $tag$
     */
    private List<String> splitStatements(String sql) {
        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        String dollarTag = null;
        int i = 0;
        while (i < sql.length()) {
            char c = sql.charAt(i);
            if (dollarTag == null) {
                if (c == '$') {
                    // $tag$ 패턴 탐색
                    int end = sql.indexOf('$', i + 1);
                    if (end != -1) {
                        dollarTag = sql.substring(i, end + 1);
                        current.append(dollarTag);
                        i = end + 1;
                        continue;
                    }
                } else if (c == ';') {
                    String stmt = current.toString().strip();
                    if (!stmt.isEmpty()) result.add(stmt);
                    current = new StringBuilder();
                    i++;
                    continue;
                }
            } else {
                // dollar-quote 종료 탐색
                if (sql.startsWith(dollarTag, i)) {
                    current.append(dollarTag);
                    i += dollarTag.length();
                    dollarTag = null;
                    continue;
                }
            }
            current.append(c);
            i++;
        }
        String last = current.toString().strip();
        if (!last.isEmpty()) result.add(last);
        return result;
    }
}
