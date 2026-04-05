package com.migd.service;

import com.migd.config.MigdProperties;
import com.migd.domain.CatalogColumn;
import com.migd.domain.CatalogRoutine;
import com.migd.domain.CatalogTable;
import com.migd.domain.SchemaCatalog;
import com.migd.dto.ColumnSearchResult;
import com.migd.dto.RoutineSearchResult;
import com.migd.dto.RoutineSearchResult.MatchedLine;
import com.migd.mapper.CatalogColumnMapper;
import com.migd.mapper.CatalogRoutineMapper;
import com.migd.mapper.CatalogTableMapper;
import com.migd.mapper.SchemaCatalogMapper;
import com.migd.util.CommentRangeDetector;
import com.migd.util.JdbcConnectionUtil;
import com.migd.util.RoutineRefExtractor;
import com.migd.dto.DbConnInfo;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CatalogService {

    private static final int BATCH_SIZE = 200;

    private final SchemaCatalogMapper schemaCatalogMapper;
    private final CatalogTableMapper catalogTableMapper;
    private final CatalogColumnMapper catalogColumnMapper;
    private final CatalogRoutineMapper catalogRoutineMapper;
    private final MigdProperties migdProperties;

    @Transactional
    public void deleteCatalog(Long catalogId) {
        catalogColumnMapper.deleteByCatalogId(catalogId);
        catalogTableMapper.deleteByCatalogId(catalogId);
        catalogRoutineMapper.deleteByCatalogId(catalogId);
        schemaCatalogMapper.deleteById(catalogId);
    }

    @Transactional(readOnly = true)
    public List<SchemaCatalog> findAllCatalogs() {
        return schemaCatalogMapper.findAll();
    }

    @Transactional(readOnly = true)
    public SchemaCatalog findCatalogById(Long id) {
        return schemaCatalogMapper.findById(id);
    }

    @Transactional(readOnly = true)
    public List<CatalogTable> findTablesByCatalogId(Long catalogId) {
        return catalogTableMapper.findByCatalogId(catalogId);
    }

    @Transactional(readOnly = true)
    public List<CatalogColumn> findColumnsByTableId(Long tableId) {
        return catalogColumnMapper.findByTableId(tableId);
    }

    /**
     * 소스 DB의 특정 스키마를 분석해 H2에 저장한다.
     * 기존 카탈로그가 있으면 삭제 후 재삽입 (수동 CASCADE: FK 없음).
     */
    @Transactional
    public SchemaCatalog analyze(String schemaName) {
        MigdProperties.SourceDb s = migdProperties.getSourceDb();
        DbConnInfo srcConn = new DbConnInfo(s.getHost(), s.getPort(), s.getDb(), s.getUser(), s.getPassword());

        log.info("스키마 분석 시작: schema={}, db={}:{}/{}", schemaName, s.getHost(), s.getPort(), s.getDb());

        // 기존 카탈로그 수동 CASCADE 삭제
        SchemaCatalog existing = schemaCatalogMapper.findByKey(schemaName, s.getHost(), s.getDb());
        if (existing != null) {
            log.info("기존 카탈로그 삭제: id={}", existing.getId());
            catalogColumnMapper.deleteByCatalogId(existing.getId());
            catalogTableMapper.deleteByCatalogId(existing.getId());
            catalogRoutineMapper.deleteByCatalogId(existing.getId());
            schemaCatalogMapper.deleteById(existing.getId());
        }

        SchemaCatalog catalog = new SchemaCatalog();
        catalog.setSchemaName(schemaName);
        catalog.setDbHost(s.getHost());
        catalog.setDbName(s.getDb());
        schemaCatalogMapper.insert(catalog);
        Long catalogId = catalog.getId();

        try (Connection conn = JdbcConnectionUtil.open(srcConn)) {
            // 테이블 분석
            List<CatalogTable> tables = fetchTables(conn, schemaName, catalogId);
            log.info("테이블 {}개 발견", tables.size());
            if (!tables.isEmpty()) {
                batchInsertTables(tables);
            }

            // 컬럼 분석 (테이블별)
            List<CatalogColumn> allColumns = new ArrayList<>();
            for (CatalogTable table : tables) {
                Set<String> pkCols = fetchPkColumns(conn, schemaName, table.getTableName());
                Map<String, String> comments = fetchColumnComments(conn, schemaName, table.getTableName());
                List<CatalogColumn> cols = fetchColumns(conn, schemaName, table, pkCols, comments, catalogId);
                allColumns.addAll(cols);
            }
            log.info("컬럼 {}개 발견", allColumns.size());
            batchInsertColumns(allColumns);

            // 루틴 분석
            List<CatalogRoutine> routines = fetchRoutines(conn, schemaName, catalogId);
            log.info("루틴 {}개 발견", routines.size());
            if (!routines.isEmpty()) {
                batchInsertRoutines(routines);
            }

            schemaCatalogMapper.updateStats(catalogId, tables.size(), routines.size());
        } catch (SQLException e) {
            throw new RuntimeException("소스 DB 분석 실패: " + e.getMessage(), e);
        }

        log.info("스키마 분석 완료: schema={}", schemaName);
        return schemaCatalogMapper.findById(catalogId);
    }

    /**
     * 컬럼명 키워드 검색.
     * catalogId=null이면 전체 카탈로그 대상.
     */
    @Transactional(readOnly = true)
    public List<ColumnSearchResult> searchByColumnName(Long catalogId, String keyword) {
        List<CatalogColumn> cols = catalogColumnMapper.searchByColumnName(catalogId, keyword);
        return cols.stream()
                .map(c -> new ColumnSearchResult(
                        c.getSchemaName(), c.getTableName(), c.getColumnName(),
                        c.getDataType(), c.isPk(), c.getColumnComment(),
                        c.getCatalogId(), c.getCatalogTableId()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CatalogRoutine> findRoutinesByCatalogId(Long catalogId) {
        return catalogRoutineMapper.findByCatalogId(catalogId);
    }

    @Transactional(readOnly = true)
    public CatalogRoutine findRoutineById(Long routineId) {
        return catalogRoutineMapper.findById(routineId);
    }

    /**
     * DDL에서 참조하는 테이블·루틴을 분석한다.
     * 카탈로그 내 실존하는 항목만 반환하며 각 항목에 ID가 포함되어 링크 생성에 사용된다.
     */
    @Transactional(readOnly = true)
    public Map<String, Object> analyzeRoutineRefs(CatalogRoutine routine, Long catalogId) {
        List<CatalogTable> allTables = catalogTableMapper.findByCatalogId(catalogId);
        List<CatalogRoutine> allRoutines = catalogRoutineMapper.findByCatalogId(catalogId);

        Set<String> tableNames   = allTables.stream().map(CatalogTable::getTableName).collect(Collectors.toSet());
        Set<String> routineNames = allRoutines.stream().map(CatalogRoutine::getRoutineName).collect(Collectors.toSet());

        Set<String> refTables   = RoutineRefExtractor.extractTableRefs(routine.getDdlBody(), tableNames);
        Set<String> refRoutines = RoutineRefExtractor.extractRoutineRefs(
                routine.getDdlBody(), routine.getRoutineName(), routineNames);

        List<CatalogTable>   tblObjs = allTables.stream()
                .filter(t -> refTables.contains(t.getTableName())).toList();
        List<CatalogRoutine> rtnObjs = allRoutines.stream()
                .filter(r -> refRoutines.contains(r.getRoutineName())).toList();

        Map<String, Object> result = new HashMap<>();
        result.put("tables", tblObjs);
        result.put("routines", rtnObjs);
        return result;
    }

    /**
     * 컬럼명과 연관된 루틴 검색 (루틴명에 키워드가 포함되거나 DDL 본문에 등장).
     */
    @Transactional(readOnly = true)
    public List<RoutineSearchResult> searchRoutinesByColumnKeyword(Long catalogId, String keyword) {
        List<CatalogRoutine> routines = catalogRoutineMapper.searchByBody(catalogId, keyword);
        return buildRoutineResults(routines, keyword);
    }

    /**
     * 프로시저 본문 키워드 검색.
     * catalogId=null이면 전체 카탈로그 대상.
     */
    @Transactional(readOnly = true)
    public List<RoutineSearchResult> searchRoutineBody(Long catalogId, String keyword) {
        List<CatalogRoutine> routines = catalogRoutineMapper.searchByBody(catalogId, keyword);
        return buildRoutineResults(routines, keyword);
    }

    /**
     * CatalogRoutine 목록에서 RoutineSearchResult 목록을 구성한다.
     * 각 루틴의 DDL에서 키워드가 포함된 라인과 주석 여부를 추출.
     */
    private List<RoutineSearchResult> buildRoutineResults(List<CatalogRoutine> routines, String keyword) {
        String lowerKeyword = keyword.toLowerCase();
        List<RoutineSearchResult> results = new ArrayList<>();

        for (CatalogRoutine routine : routines) {
            String ddl = routine.getDdlBody();
            Set<Integer> commentLines = CommentRangeDetector.commentLineNumbers(ddl);
            String[] lines = ddl.split("\n", -1);

            List<Integer> matchedIdx = new ArrayList<>();
            for (int i = 0; i < lines.length; i++) {
                if (lines[i].toLowerCase().contains(lowerKeyword)) matchedIdx.add(i);
            }
            Set<Integer> matchedSet = new HashSet<>(matchedIdx);

            List<MatchedLine> matched = new ArrayList<>();
            for (int i : matchedIdx) {
                String before = (i > 0 && !matchedSet.contains(i - 1)) ? lines[i - 1] : null;
                String after  = (i < lines.length - 1 && !matchedSet.contains(i + 1)) ? lines[i + 1] : null;
                matched.add(new MatchedLine(i + 1, lines[i], commentLines.contains(i), before, after));
            }

            if (!matched.isEmpty()) {
                results.add(new RoutineSearchResult(
                        routine.getSchemaName(), routine.getRoutineName(),
                        routine.getRoutineType(), matched));
            }
        }
        return results;
    }

    // ── PostgreSQL 분석 쿼리 ──────────────────────────────────────────

    private List<CatalogTable> fetchTables(Connection conn, String schemaName, Long catalogId)
            throws SQLException {
        String sql = """
                SELECT c.relname AS table_name,
                       obj_description(c.oid, 'pg_class') AS table_comment
                FROM pg_class c
                JOIN pg_namespace n ON n.oid = c.relnamespace
                WHERE n.nspname = ? AND c.relkind = 'r'
                ORDER BY c.relname
                """;
        List<CatalogTable> tables = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schemaName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    CatalogTable t = new CatalogTable();
                    t.setCatalogId(catalogId);
                    t.setSchemaName(schemaName);
                    t.setTableName(rs.getString("table_name"));
                    t.setTableComment(rs.getString("table_comment"));
                    tables.add(t);
                }
            }
        }
        return tables;
    }

    private Set<String> fetchPkColumns(Connection conn, String schemaName, String tableName)
            throws SQLException {
        String sql = """
                SELECT kcu.column_name
                FROM information_schema.table_constraints tc
                JOIN information_schema.key_column_usage kcu
                  ON tc.constraint_name = kcu.constraint_name
                 AND tc.table_schema    = kcu.table_schema
                WHERE tc.constraint_type = 'PRIMARY KEY'
                  AND tc.table_schema = ?
                  AND tc.table_name   = ?
                """;
        Set<String> pks = new HashSet<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schemaName);
            ps.setString(2, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) pks.add(rs.getString("column_name"));
            }
        }
        return pks;
    }

    private Map<String, String> fetchColumnComments(Connection conn, String schemaName, String tableName)
            throws SQLException {
        String sql = """
                SELECT a.attname AS column_name,
                       col_description(c.oid, a.attnum) AS comment
                FROM pg_class c
                JOIN pg_namespace n ON n.oid = c.relnamespace
                JOIN pg_attribute a ON a.attrelid = c.oid
                WHERE n.nspname = ? AND c.relname = ?
                  AND a.attnum > 0 AND NOT a.attisdropped
                """;
        Map<String, String> map = new HashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schemaName);
            ps.setString(2, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String comment = rs.getString("comment");
                    if (comment != null && !comment.isBlank()) {
                        map.put(rs.getString("column_name"), comment);
                    }
                }
            }
        }
        return map;
    }

    private List<CatalogColumn> fetchColumns(Connection conn, String schemaName,
                                              CatalogTable table, Set<String> pkCols,
                                              Map<String, String> comments, Long catalogId)
            throws SQLException {
        String sql = """
                SELECT column_name, data_type, ordinal_position,
                       is_nullable
                FROM information_schema.columns
                WHERE table_schema = ? AND table_name = ?
                ORDER BY ordinal_position
                """;
        List<CatalogColumn> cols = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schemaName);
            ps.setString(2, table.getTableName());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String colName = rs.getString("column_name");
                    CatalogColumn col = new CatalogColumn();
                    col.setCatalogTableId(table.getId());
                    col.setCatalogId(catalogId);
                    col.setSchemaName(schemaName);
                    col.setTableName(table.getTableName());
                    col.setColumnName(colName);
                    col.setColumnNameLower(colName.toLowerCase());
                    col.setDataType(rs.getString("data_type"));
                    col.setOrdinalPosition(rs.getInt("ordinal_position"));
                    col.setNullable("YES".equalsIgnoreCase(rs.getString("is_nullable")));
                    col.setPk(pkCols.contains(colName));
                    col.setColumnComment(comments.get(colName));
                    cols.add(col);
                }
            }
        }
        return cols;
    }

    private List<CatalogRoutine> fetchRoutines(Connection conn, String schemaName, Long catalogId)
            throws SQLException {
        String sql = """
                SELECT p.proname AS routine_name,
                       CASE p.prokind
                           WHEN 'f' THEN 'FUNCTION'
                           WHEN 'p' THEN 'PROCEDURE'
                           ELSE 'FUNCTION'
                       END AS routine_type,
                       pg_get_functiondef(p.oid) AS ddl_body
                FROM pg_proc p
                JOIN pg_namespace n ON n.oid = p.pronamespace
                WHERE n.nspname = ?
                  AND p.prokind IN ('f', 'p')
                ORDER BY p.proname
                """;
        List<CatalogRoutine> routines = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, schemaName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    CatalogRoutine r = new CatalogRoutine();
                    r.setCatalogId(catalogId);
                    r.setSchemaName(schemaName);
                    r.setRoutineName(rs.getString("routine_name"));
                    r.setRoutineType(rs.getString("routine_type"));
                    r.setDdlBody(rs.getString("ddl_body"));
                    routines.add(r);
                }
            }
        }
        return routines;
    }

    // ── 배치 INSERT 헬퍼 ─────────────────────────────────────────────

    private void batchInsertTables(List<CatalogTable> tables) {
        // 개별 insert 필수 — useGeneratedKeys로 각 객체에 id가 채워져야
        // 이후 컬럼 insert 시 catalogTableId(= table.getId())로 사용됨
        for (CatalogTable table : tables) {
            catalogTableMapper.insert(table);
        }
    }

    private void batchInsertColumns(List<CatalogColumn> columns) {
        for (int i = 0; i < columns.size(); i += BATCH_SIZE) {
            catalogColumnMapper.batchInsert(columns.subList(i, Math.min(i + BATCH_SIZE, columns.size())));
        }
    }

    private void batchInsertRoutines(List<CatalogRoutine> routines) {
        for (int i = 0; i < routines.size(); i += BATCH_SIZE) {
            catalogRoutineMapper.batchInsert(routines.subList(i, Math.min(i + BATCH_SIZE, routines.size())));
        }
    }
}
