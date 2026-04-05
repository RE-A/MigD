package com.migd.util;

import java.util.HashSet;
import java.util.Set;

/**
 * PL/pgSQL DDL 텍스트에서 주석 라인 번호(0-based)를 감지한다.
 *
 * 규칙:
 * - 블록 주석(/* ... *&#47;) 내부의 라인은 주석 라인으로 표시
 * - 한줄 주석(--)으로 시작하는 라인은 주석 라인으로 표시
 * - 라인에 코드와 한줄 주석이 혼재하는 경우(SELECT x -- comment),
 *   해당 라인은 "코드 내" 로 분류 (키워드가 코드 영역에 있을 가능성 높음)
 */
public final class CommentRangeDetector {

    private CommentRangeDetector() {}

    /**
     * DDL 텍스트에서 주석 내부에 해당하는 라인 번호 Set을 반환한다 (0-based).
     */
    public static Set<Integer> commentLineNumbers(String ddl) {
        Set<Integer> result = new HashSet<>();
        if (ddl == null || ddl.isEmpty()) return result;

        String[] lines = ddl.split("\n", -1);
        boolean inBlockComment = false;

        for (int lineNum = 0; lineNum < lines.length; lineNum++) {
            String line = lines[lineNum];
            String trimmed = line.stripLeading();

            // 블록 주석 내부에서 라인이 시작된 경우 → 주석 라인
            if (inBlockComment) {
                result.add(lineNum);
            } else if (trimmed.startsWith("--")) {
                // 한줄 주석으로만 구성된 라인
                result.add(lineNum);
            }

            // 라인 내부를 파싱해 inBlockComment 상태 갱신
            int i = 0;
            boolean inLineCommentOnThisLine = false;
            while (i < line.length()) {
                char c = line.charAt(i);
                if (inBlockComment) {
                    if (c == '*' && i + 1 < line.length() && line.charAt(i + 1) == '/') {
                        inBlockComment = false;
                        i += 2;
                        continue;
                    }
                } else if (inLineCommentOnThisLine) {
                    break; // 줄 끝까지 주석, 다음 라인으로
                } else {
                    if (c == '/' && i + 1 < line.length() && line.charAt(i + 1) == '*') {
                        inBlockComment = true;
                        // 블록 주석 시작 — 이 라인도 주석으로 표시
                        result.add(lineNum);
                        i += 2;
                        continue;
                    } else if (c == '-' && i + 1 < line.length() && line.charAt(i + 1) == '-') {
                        inLineCommentOnThisLine = true;
                        i += 2;
                        continue;
                    }
                }
                i++;
            }
        }

        return result;
    }

    /**
     * 특정 라인 번호가 주석 내부인지 확인한다.
     */
    public static boolean isCommentLine(String ddl, int lineNumber) {
        return commentLineNumbers(ddl).contains(lineNumber);
    }
}
