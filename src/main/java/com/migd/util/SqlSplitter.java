package com.migd.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * PostgreSQL DDL 문자열 파싱 유틸리티.
 * dollar-quote($$ 또는 $tag$) 블록 내부의 ';'는 구분자로 취급하지 않는다.
 */
public final class SqlSplitter {

    private SqlSplitter() {}

    /**
     * pg_dump 출력에서 불필요한 메타라인을 제거한다.
     * SET 명령, 주석, BEGIN/COMMIT 트랜잭션 구분자 제거.
     */
    public static String filterDdlLines(String ddl) {
        return Arrays.stream(ddl.split("\n"))
                .filter(line -> !line.startsWith("--"))
                .filter(line -> !line.startsWith("SET "))
                .filter(line -> !line.startsWith("\\"))   // psql 메타커맨드 (\connect 등)
                .filter(line -> !line.equals("BEGIN;"))
                .filter(line -> !line.equals("COMMIT;"))
                .collect(Collectors.joining("\n"));
    }

    /**
     * SQL 텍스트를 ';' 기준으로 분리하되 dollar-quote 블록 내부는 건너뜀.
     * PostgreSQL dollar-quote: $$ ... $$ 또는 $tag$ ... $tag$
     */
    public static List<String> splitStatements(String sql) {
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
