package com.migd.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * CatalogController 통합 테스트.
 * H2 인메모리 DB 사용 (카탈로그 데이터 없는 초기 상태 기준).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CatalogControllerTest {

    @Autowired
    MockMvc mockMvc;

    @Test
    @DisplayName("GET /catalog — 카탈로그 목록 페이지 렌더링")
    void catalogIndex() throws Exception {
        mockMvc.perform(get("/catalog"))
               .andExpect(status().isOk())
               .andExpect(view().name("catalog/index"))
               .andExpect(model().attributeExists("catalogs", "existingSchemas"));
    }

    @Test
    @DisplayName("GET /catalog/search — 검색어 없으면 결과 섹션 없음")
    void searchNoKeyword() throws Exception {
        mockMvc.perform(get("/catalog/search"))
               .andExpect(status().isOk())
               .andExpect(view().name("catalog/search"))
               .andExpect(model().attributeDoesNotExist("columnResults"))
               .andExpect(model().attributeDoesNotExist("routineResults"));
    }

    @Test
    @DisplayName("GET /catalog/search?keyword=x — 결과 섹션 포함 (데이터 없으면 빈 리스트)")
    void searchWithKeyword() throws Exception {
        mockMvc.perform(get("/catalog/search").param("keyword", "emp_id"))
               .andExpect(status().isOk())
               .andExpect(view().name("catalog/search"))
               .andExpect(model().attributeExists("columnResults", "routineResults"));
    }

    @Test
    @DisplayName("GET /catalog/search?keyword=x&type=column — column 모드는 relatedRoutines 포함")
    void searchColumnMode() throws Exception {
        mockMvc.perform(get("/catalog/search").param("keyword", "emp").param("type", "column"))
               .andExpect(status().isOk())
               .andExpect(model().attributeExists("columnResults", "relatedRoutines"))
               .andExpect(model().attributeDoesNotExist("routineResults"));
    }

    @Test
    @DisplayName("GET /catalog/{id}/tables — 존재하지 않는 카탈로그 → /catalog 리다이렉트")
    void tablesInvalidCatalog() throws Exception {
        mockMvc.perform(get("/catalog/99999/tables"))
               .andExpect(status().is3xxRedirection())
               .andExpect(redirectedUrl("/catalog"));
    }

    @Test
    @DisplayName("GET /catalog/{id}/routines — 존재하지 않는 카탈로그 → /catalog 리다이렉트")
    void routinesInvalidCatalog() throws Exception {
        mockMvc.perform(get("/catalog/99999/routines"))
               .andExpect(status().is3xxRedirection())
               .andExpect(redirectedUrl("/catalog"));
    }

    @Test
    @DisplayName("GET /catalog/{id}/routines/{rid} — 존재하지 않는 카탈로그 → /catalog 리다이렉트")
    void routineDetailInvalidCatalog() throws Exception {
        mockMvc.perform(get("/catalog/99999/routines/1"))
               .andExpect(status().is3xxRedirection())
               .andExpect(redirectedUrl("/catalog"));
    }
}
