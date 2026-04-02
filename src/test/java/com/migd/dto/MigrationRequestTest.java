package com.migd.dto;

import com.migd.domain.PresetTable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MigrationRequest DTO 단위 테스트.
 */
class MigrationRequestTest {

    @Test
    @DisplayName("기본 포트는 5432")
    void 기본포트_5432() {
        MigrationRequest req = new MigrationRequest();
        assertThat(req.getTgtPort()).isEqualTo(5432);
    }

    @Test
    @DisplayName("toTgtConnInfo() - 필드가 1:1로 DbConnInfo에 매핑됨")
    void toTgtConnInfo_필드매핑() {
        MigrationRequest req = new MigrationRequest();
        req.setTgtHost("localhost");
        req.setTgtPort(5432);
        req.setTgtDb("testdb");
        req.setTgtUser("user");
        req.setTgtPassword("pass");

        DbConnInfo info = req.toTgtConnInfo();

        assertThat(info.host()).isEqualTo("localhost");
        assertThat(info.port()).isEqualTo(5432);
        assertThat(info.db()).isEqualTo("testdb");
        assertThat(info.user()).isEqualTo("user");
        assertThat(info.password()).isEqualTo("pass");
    }

    @Test
    @DisplayName("List<PresetTable> 생성자 - tables 방어적 복사")
    void 생성자_방어적복사() {
        List<PresetTable> original = new ArrayList<>();
        original.add(PresetTable.builder()
                .schemaName("public").tableName("users").whereCondition("id > 0").build());

        MigrationRequest req = new MigrationRequest(original);
        original.add(PresetTable.builder()
                .schemaName("public").tableName("orders").build());

        // 원본 리스트에 추가해도 req의 tables에 영향 없음
        assertThat(req.getTables()).hasSize(1);
    }

    @Test
    @DisplayName("null tables 생성자 - 빈 리스트로 초기화")
    void 생성자_null_빈리스트() {
        MigrationRequest req = new MigrationRequest((List<PresetTable>) null);
        assertThat(req.getTables()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("WHERE 조건이 포함된 테이블이 tables 리스트에 정상 보관됨")
    void WHERE조건_테이블_리스트보관() {
        List<PresetTable> tables = List.of(
                PresetTable.builder().schemaName("public").tableName("orders")
                        .whereCondition("status = 'A'").build(),
                PresetTable.builder().schemaName("public").tableName("users")
                        .whereCondition(null).build()
        );

        MigrationRequest req = new MigrationRequest(tables);

        assertThat(req.getTables()).hasSize(2);
        assertThat(req.getTables().get(0).getWhereCondition()).isEqualTo("status = 'A'");
        assertThat(req.getTables().get(1).getWhereCondition()).isNull();
    }
}
