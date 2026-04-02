package com.migd.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Preset {
    private Long id;
    private String name;
    private String description;
    private LocalDateTime createdAt;

    // DB에 저장되지 않는 연관 데이터 (조회 시 별도 로드)
    @Builder.Default
    private List<PresetTable> tables = new ArrayList<>();
}
