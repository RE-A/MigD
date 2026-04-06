package com.migd.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoutineSearchResult {
    private Long catalogId;
    private Long routineId;
    private String schemaName;
    private String routineName;
    private String routineType;
    private List<MatchedLine> matchedLines;

    public record MatchedLine(int lineNumber, String content, boolean inComment,
                              String contextBefore, String contextAfter) {}
}
