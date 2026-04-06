package com.migd.service;

import com.migd.domain.PresetTable;
import com.migd.dto.DbConnInfo;
import com.migd.dto.MigrationResult;
import com.migd.dto.TableMigrationResult;
import com.migd.exception.MigrationException;
import com.migd.util.JdbcConnectionUtil;
import lombok.extern.slf4j.Slf4j;
import org.postgresql.copy.CopyManager;
import org.postgresql.core.BaseConnection;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
public class DataMigrationService {

    private static final int PIPE_BUFFER_SIZE = 65536; // 64KB

    /**
     * 테이블 목록을 순서대로 이관.
     * 각 테이블은 독립적인 커넥션과 트랜잭션으로 처리.
     */
    public MigrationResult migrateAll(DbConnInfo src, DbConnInfo tgt, List<PresetTable> tables) {
        List<TableMigrationResult> results = new ArrayList<>();

        for (PresetTable table : tables) {
            TableMigrationResult result = migrateTable(src, tgt, table);
            results.add(result);
            if (!result.isSuccess()) {
                log.warn("테이블 이관 실패, 이후 테이블 중단: {}", result.getFullTableName());
                int failedIdx = tables.indexOf(table);
                for (int i = failedIdx + 1; i < tables.size(); i++) {
                    PresetTable skipped = tables.get(i);
                    results.add(new TableMigrationResult(skipped, 0, "이전 테이블 실패로 건너뜀"));
                }
                break;
            }
        }

        return new MigrationResult(results);
    }

    /**
     * 단일 테이블 이관.
     * CopyManager + PipedStream 두 스레드 패턴으로 메모리 OOM 없이 스트리밍.
     * setAutoCommit(false) 수동 트랜잭션 - 에러 시 롤백 보장.
     */
    private TableMigrationResult migrateTable(DbConnInfo src, DbConnInfo tgt, PresetTable table) {
        String copyOutSql = buildCopyOutQuery(table.getSchemaName(),
                table.getTableName(), table.getWhereCondition());
        String copyInSql = buildCopyInQuery(table.getSchemaName(), table.getTableName());

        log.info("이관 시작: {}.{} | COPY OUT SQL: {}",
                table.getSchemaName(), table.getTableName(), copyOutSql);

        try (Connection srcConn = JdbcConnectionUtil.open(src);
             Connection tgtConn = JdbcConnectionUtil.open(tgt)) {

            tgtConn.setAutoCommit(false);

            PipedOutputStream pipedOut = new PipedOutputStream();
            PipedInputStream pipedIn;
            try {
                pipedIn = new PipedInputStream(pipedOut, PIPE_BUFFER_SIZE);
            } catch (IOException e) {
                throw new MigrationException("파이프 스트림 초기화 실패", e);
            }

            AtomicReference<Exception> copyOutError = new AtomicReference<>();

            CopyManager srcCopyManager = new CopyManager(srcConn.unwrap(BaseConnection.class));
            CopyManager tgtCopyManager = new CopyManager(tgtConn.unwrap(BaseConnection.class));

            // Thread A: Source → PipedOutputStream (가상 스레드)
            Thread copyOutThread = Thread.ofVirtual().start(() -> {
                try {
                    String copyOutFull = "COPY (" + copyOutSql + ") TO STDOUT WITH (FORMAT BINARY)";
                    srcCopyManager.copyOut(copyOutFull, pipedOut);
                } catch (Exception e) {
                    copyOutError.set(e);
                    log.error("COPY OUT 오류: {}.{}", table.getSchemaName(), table.getTableName(), e);
                } finally {
                    try {
                        pipedOut.close();
                    } catch (IOException ignored) {
                    }
                }
            });

            // Thread B (현재 스레드): PipedInputStream → Target
            long rowsCopied = 0;
            Exception copyInException = null;
            try {
                rowsCopied = tgtCopyManager.copyIn(copyInSql, pipedIn);
            } catch (Exception e) {
                copyInException = e;
                log.error("COPY IN 오류: {}.{}", table.getSchemaName(), table.getTableName(), e);
            } finally {
                try {
                    pipedIn.close();
                } catch (IOException ignored) {
                }
            }

            copyOutThread.join();

            Exception outErr = copyOutError.get();
            Exception inErr = copyInException;

            if (outErr != null || inErr != null) {
                try {
                    tgtConn.rollback();
                    log.info("롤백 완료: {}.{}", table.getSchemaName(), table.getTableName());
                } catch (SQLException rollbackEx) {
                    log.error("롤백 실패", rollbackEx);
                }

                Exception primary = inErr != null ? inErr : outErr;

                if (primary instanceof SQLException sqle && "23505".equals(sqle.getSQLState())) {
                    String msg = String.format(
                            "PK 중복 오류 [%s.%s]: Target DB에 이미 데이터가 존재합니다. 직접 삭제 후 재시도하세요.",
                            table.getSchemaName(), table.getTableName());
                    return new TableMigrationResult(table, 0, msg);
                }

                return new TableMigrationResult(table, 0, primary.getMessage());
            }

            tgtConn.commit();
            log.info("이관 완료: {}.{} - {}건", table.getSchemaName(), table.getTableName(), rowsCopied);
            return new TableMigrationResult(table, rowsCopied, null);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new MigrationException("이관 중단됨 (interrupted)", e);
        } catch (SQLException e) {
            throw new MigrationException("DB 연결 오류: " + e.getMessage(), e);
        }
    }

    private String buildCopyOutQuery(String schemaName, String tableName, String whereCondition) {
        String qualified = "\"" + schemaName + "\".\"" + tableName + "\"";
        if (whereCondition == null || whereCondition.isBlank()) {
            return "SELECT * FROM " + qualified;
        }
        validateWhereCondition(whereCondition);
        return "SELECT * FROM " + qualified + " WHERE " + whereCondition;
    }

    private static void validateWhereCondition(String where) {
        if (where.contains(";")) {
            throw new IllegalArgumentException("WHERE 조건에 세미콜론(;)은 허용되지 않습니다.");
        }
        String upper = where.stripLeading().toUpperCase();
        // 문장 시작에 오는 DDL/DML 키워드 차단
        for (String kw : List.of("DROP ", "TRUNCATE ", "DELETE ", "INSERT ", "UPDATE ",
                "ALTER ", "CREATE ", "EXEC ", "EXECUTE ", "CALL ")) {
            if (upper.startsWith(kw)) {
                throw new IllegalArgumentException("WHERE 조건에 허용되지 않는 구문이 포함되어 있습니다: " + kw.trim());
            }
        }
    }

    private String buildCopyInQuery(String schemaName, String tableName) {
        return "COPY \"" + schemaName + "\".\"" + tableName + "\" FROM STDIN WITH (FORMAT BINARY)";
    }
}
