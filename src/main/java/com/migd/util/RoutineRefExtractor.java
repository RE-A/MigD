package com.migd.util;

import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * DDL 본문에서 참조하는 테이블·루틴 이름을 정규식으로 추출한다.
 * 추출된 후보 이름을 실제 카탈로그 목록과 교차(intersection)하여 유효 참조만 반환한다.
 */
public final class RoutineRefExtractor {

    // FROM/JOIN/UPDATE/INSERT INTO 키워드 뒤 식별자 (스키마.테이블 또는 테이블)
    private static final Pattern TABLE_REF = Pattern.compile(
            "(?:FROM|JOIN|UPDATE|INSERT\\s+INTO)\\s+((?:\\w+\\.)?\\w+)",
            Pattern.CASE_INSENSITIVE);

    // 식별자 뒤에 '(' 가 오는 패턴 — 루틴 호출
    private static final Pattern ROUTINE_REF = Pattern.compile(
            "((?:\\w+\\.)?\\w+)\\s*\\(",
            Pattern.CASE_INSENSITIVE);

    private RoutineRefExtractor() {}

    /**
     * DDL에서 참조 테이블명 추출 후 knownTableNames와 교차.
     * schemaName.tableName 형식이면 tableName 부분만 비교한다.
     */
    public static Set<String> extractTableRefs(String ddl, Set<String> knownTableNames) {
        if (ddl == null || ddl.isBlank()) return Set.of();
        Set<String> result = new HashSet<>();
        Matcher m = TABLE_REF.matcher(ddl);
        while (m.find()) {
            String candidate = unqualify(m.group(1));
            if (knownTableNames.contains(candidate)) {
                result.add(candidate);
            }
        }
        return result;
    }

    /**
     * DDL에서 참조 루틴명 추출 후 knownRoutineNames와 교차.
     * 자기 자신(selfName)은 제외한다.
     */
    public static Set<String> extractRoutineRefs(String ddl, String selfName,
                                                  Set<String> knownRoutineNames) {
        if (ddl == null || ddl.isBlank()) return Set.of();
        Set<String> result = new HashSet<>();
        Matcher m = ROUTINE_REF.matcher(ddl);
        while (m.find()) {
            String candidate = unqualify(m.group(1));
            if (knownRoutineNames.contains(candidate)
                    && !candidate.equalsIgnoreCase(selfName)) {
                result.add(candidate);
            }
        }
        return result;
    }

    /**
     * DDL에서 schema.tablename 형태의 참조 중 knownTableNames에 없는 것을 반환한다.
     * 타 스키마 참조 표시용 — 원본 "schema.tablename" 형태로 반환.
     */
    public static Set<String> extractCrossSchemaRefs(String ddl, Set<String> knownTableNames) {
        if (ddl == null || ddl.isBlank()) return Set.of();
        Set<String> result = new HashSet<>();
        Matcher m = TABLE_REF.matcher(ddl);
        while (m.find()) {
            String raw = m.group(1);
            if (raw.contains(".")) {
                String simple = unqualify(raw);
                if (!knownTableNames.contains(simple)) {
                    result.add(raw);
                }
            }
        }
        return result;
    }

    /** "schema.name" → "name", "name" → "name" */
    private static String unqualify(String identifier) {
        int dot = identifier.lastIndexOf('.');
        return dot >= 0 ? identifier.substring(dot + 1) : identifier;
    }
}
