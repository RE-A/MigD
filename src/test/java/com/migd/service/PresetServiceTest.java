package com.migd.service;

import com.migd.domain.Preset;
import com.migd.domain.PresetTable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PresetService 통합 테스트 — H2 인메모리 + MyBatis.
 * WHERE 조건 포함 테이블이 DB에 저장/로드 과정에서 손실 없이 유지되는지 검증.
 */
@SpringBootTest
@Transactional
class PresetServiceTest {

    @Autowired
    private PresetService presetService;

    // ── 기본 저장/조회 ──────────────────────────────────────────────────────

    @Test
    @DisplayName("프리셋 저장 후 findById로 동일 데이터 조회")
    void 프리셋_저장_조회() {
        Preset preset = new Preset();
        preset.setName("테스트 프리셋");
        preset.setDescription("테스트용");

        Preset saved = presetService.save(preset);
        Preset found = presetService.findById(saved.getId());

        assertThat(found.getName()).isEqualTo("테스트 프리셋");
        assertThat(found.getDescription()).isEqualTo("테스트용");
        assertThat(found.getId()).isNotNull();
    }

    // ── WHERE 조건 저장/로드 ────────────────────────────────────────────────

    @Test
    @DisplayName("WHERE 조건 있는 테이블이 저장 후 그대로 로드됨")
    void WHERE조건_저장후_로드() {
        Preset preset = new Preset();
        preset.setName("where 테스트");
        preset.setTables(List.of(
                PresetTable.builder()
                        .schemaName("public").tableName("orders")
                        .whereCondition("status = 'A'").orderNum(0).build()
        ));

        Preset saved = presetService.save(preset);
        Preset found = presetService.findById(saved.getId());

        assertThat(found.getTables()).hasSize(1);
        assertThat(found.getTables().get(0).getWhereCondition()).isEqualTo("status = 'A'");
    }

    @Test
    @DisplayName("WHERE 조건 없는 테이블은 null로 저장/로드됨")
    void WHERE조건없음_null로_로드() {
        Preset preset = new Preset();
        preset.setName("no-where 테스트");
        preset.setTables(List.of(
                PresetTable.builder()
                        .schemaName("public").tableName("users")
                        .whereCondition(null).orderNum(0).build()
        ));

        Preset saved = presetService.save(preset);
        Preset found = presetService.findById(saved.getId());

        assertThat(found.getTables().get(0).getWhereCondition()).isNull();
    }

    @Test
    @DisplayName("WHERE 조건 있는 테이블과 없는 테이블이 혼합 저장 후 각각 올바르게 로드됨")
    void WHERE조건_혼합_저장_로드() {
        Preset preset = new Preset();
        preset.setName("혼합 테스트");
        preset.setTables(List.of(
                PresetTable.builder().schemaName("public").tableName("t1")
                        .whereCondition("created_at > '2024-01-01' AND status = 'A'").orderNum(0).build(),
                PresetTable.builder().schemaName("public").tableName("t2")
                        .whereCondition(null).orderNum(1).build(),
                PresetTable.builder().schemaName("myschema").tableName("t3")
                        .whereCondition("id IN (1, 2, 3)").orderNum(2).build()
        ));

        Preset saved = presetService.save(preset);
        List<PresetTable> tables = presetService.findTablesByPresetId(saved.getId());

        assertThat(tables).hasSize(3);
        assertThat(tables.get(0).getWhereCondition()).isEqualTo("created_at > '2024-01-01' AND status = 'A'");
        assertThat(tables.get(1).getWhereCondition()).isNull();
        assertThat(tables.get(2).getWhereCondition()).isEqualTo("id IN (1, 2, 3)");
    }

    @Test
    @DisplayName("findTablesByPresetId는 order_num 오름차순으로 반환됨")
    void 테이블_orderNum_정렬() {
        Preset preset = new Preset();
        preset.setName("정렬 테스트");
        preset.setTables(List.of(
                PresetTable.builder().schemaName("public").tableName("c").orderNum(2).build(),
                PresetTable.builder().schemaName("public").tableName("a").orderNum(0).build(),
                PresetTable.builder().schemaName("public").tableName("b").orderNum(1).build()
        ));

        Preset saved = presetService.save(preset);
        List<PresetTable> tables = presetService.findTablesByPresetId(saved.getId());

        assertThat(tables).extracting(PresetTable::getTableName)
                .containsExactly("a", "b", "c");
    }

    // ── 수정 (테이블 교체) ──────────────────────────────────────────────────

    @Test
    @DisplayName("프리셋 수정 시 테이블 목록 전체 교체됨")
    void 테이블_전체교체() {
        Preset preset = new Preset();
        preset.setName("교체 테스트");
        preset.setTables(List.of(
                PresetTable.builder().schemaName("public").tableName("old_table")
                        .whereCondition("id > 0").orderNum(0).build()
        ));
        Preset saved = presetService.save(preset);

        // 새 테이블 목록으로 교체
        saved.setTables(List.of(
                PresetTable.builder().schemaName("public").tableName("new_table")
                        .whereCondition("status = 'B'").orderNum(0).build()
        ));
        presetService.save(saved);

        List<PresetTable> tables = presetService.findTablesByPresetId(saved.getId());
        assertThat(tables).hasSize(1);
        assertThat(tables.get(0).getTableName()).isEqualTo("new_table");
        assertThat(tables.get(0).getWhereCondition()).isEqualTo("status = 'B'");
    }

    // ── 삭제 ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("프리셋 삭제 시 findById에서 예외 발생")
    void 프리셋_삭제후_조회_예외() {
        Preset preset = new Preset();
        preset.setName("삭제 테스트");
        Preset saved = presetService.save(preset);
        Long id = saved.getId();

        presetService.delete(id);

        assertThatThrownBy(() -> presetService.findById(id))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("존재하지 않는 ID로 findById 시 예외 발생")
    void 존재하지않는ID_예외() {
        assertThatThrownBy(() -> presetService.findById(999999L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("프리셋을 찾을 수 없습니다");
    }
}
