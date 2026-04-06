package com.migd.controller;

import com.migd.domain.CatalogColumn;
import com.migd.domain.CatalogRoutine;
import com.migd.domain.CatalogTable;
import com.migd.domain.SchemaCatalog;
import com.migd.dto.ColumnSearchResult;
import com.migd.dto.RoutineSearchResult;
import com.migd.service.CatalogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;
import java.util.Map;

@Slf4j
@Controller
@RequestMapping("/catalog")
@RequiredArgsConstructor
public class CatalogController {

    private final CatalogService catalogService;

    @GetMapping
    public String index(Model model) {
        List<SchemaCatalog> catalogs = catalogService.findAllCatalogs();
        model.addAttribute("catalogs", catalogs);
        // 재분석 경고를 위해 기존 스키마명 목록 전달
        model.addAttribute("existingSchemas", catalogs.stream()
                .map(SchemaCatalog::getSchemaName).toList());
        return "catalog/index";
    }

    @PostMapping("/{catalogId}/delete")
    public String delete(@PathVariable Long catalogId, RedirectAttributes attrs) {
        try {
            catalogService.deleteCatalog(catalogId);
            attrs.addFlashAttribute("success", "카탈로그가 삭제되었습니다.");
        } catch (Exception e) {
            attrs.addFlashAttribute("error", "삭제 실패: " + e.getMessage());
        }
        return "redirect:/catalog";
    }

    @PostMapping("/analyze")
    public String analyze(@RequestParam String schemaName,
                          @RequestParam(required = false) String excludePattern,
                          RedirectAttributes attrs) {
        if (schemaName == null || schemaName.isBlank()) {
            attrs.addFlashAttribute("error", "스키마명을 입력하세요.");
            return "redirect:/catalog";
        }
        try {
            log.info("카탈로그 분석 요청: schema={}, excludePattern={}", schemaName, excludePattern);
            SchemaCatalog result = catalogService.analyze(schemaName.trim(),
                    excludePattern != null ? excludePattern.trim() : "");
            attrs.addFlashAttribute("success",
                    String.format("'%s' 스키마 분석 완료 — 테이블 %d개, 루틴 %d개",
                            result.getSchemaName(), result.getTableCount(), result.getRoutineCount()));
        } catch (Exception e) {
            log.error("카탈로그 분석 실패: schema={}", schemaName, e);
            attrs.addFlashAttribute("error", "분석 실패: " + e.getMessage());
        }
        return "redirect:/catalog";
    }

    @GetMapping("/{catalogId}/tables")
    public String tables(@PathVariable Long catalogId, Model model) {
        SchemaCatalog catalog = catalogService.findCatalogById(catalogId);
        if (catalog == null) return "redirect:/catalog";

        List<CatalogTable> tables = catalogService.findTablesByCatalogId(catalogId);
        model.addAttribute("catalog", catalog);
        model.addAttribute("tables", tables);
        model.addAttribute("catalogs", catalogService.findAllCatalogs());
        return "catalog/tables";
    }

    @GetMapping("/{catalogId}/tables/{tableId}")
    public String tableColumns(@PathVariable Long catalogId,
                               @PathVariable Long tableId,
                               Model model) {
        SchemaCatalog catalog = catalogService.findCatalogById(catalogId);
        if (catalog == null) return "redirect:/catalog";

        List<CatalogTable> tables = catalogService.findTablesByCatalogId(catalogId);
        model.addAttribute("catalog", catalog);
        model.addAttribute("tables", tables);
        model.addAttribute("selectedTableId", tableId);
        model.addAttribute("catalogs", catalogService.findAllCatalogs());
        return "catalog/tables";
    }

    /** 컬럼 목록 JSON API — 사이드바 AJAX용 */
    @GetMapping("/{catalogId}/tables/{tableId}/columns")
    @ResponseBody
    public ResponseEntity<List<CatalogColumn>> tableColumnsApi(@PathVariable Long catalogId,
                                                               @PathVariable Long tableId) {
        SchemaCatalog catalog = catalogService.findCatalogById(catalogId);
        if (catalog == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(catalogService.findColumnsByTableId(tableId));
    }

    @GetMapping("/{catalogId}/routines")
    public String routines(@PathVariable Long catalogId, Model model) {
        SchemaCatalog catalog = catalogService.findCatalogById(catalogId);
        if (catalog == null) return "redirect:/catalog";
        List<CatalogRoutine> routines = catalogService.findRoutinesByCatalogId(catalogId);
        model.addAttribute("catalog", catalog);
        model.addAttribute("routines", routines);
        model.addAttribute("catalogs", catalogService.findAllCatalogs());
        return "catalog/routines";
    }

    @GetMapping("/{catalogId}/routines/{routineId}")
    public String routineDetail(@PathVariable Long catalogId,
                                @PathVariable Long routineId, Model model) {
        SchemaCatalog catalog = catalogService.findCatalogById(catalogId);
        if (catalog == null) return "redirect:/catalog";
        List<CatalogRoutine> routines = catalogService.findRoutinesByCatalogId(catalogId);
        CatalogRoutine selected = catalogService.findRoutineById(routineId);
        Map<String, Object> refs = (selected != null)
                ? catalogService.analyzeRoutineRefs(selected, catalogId) : Map.of();
        model.addAttribute("catalog", catalog);
        model.addAttribute("routines", routines);
        model.addAttribute("selectedRoutineId", routineId);
        model.addAttribute("selected", selected);
        model.addAttribute("refTables",       refs.get("tables"));
        model.addAttribute("refRoutines",     refs.get("routines"));
        model.addAttribute("crossSchemaRefs", refs.get("crossSchemaRefs"));
        model.addAttribute("catalogs", catalogService.findAllCatalogs());
        return "catalog/routines";
    }

    /**
     * 통합 검색.
     * type: "column" | "routine" | "" (둘 다)
     * catalogId: null이면 전체 카탈로그 대상
     */
    @GetMapping("/search")
    public String search(@RequestParam(required = false) String keyword,
                         @RequestParam(required = false) Long catalogId,
                         @RequestParam(required = false, defaultValue = "") String type,
                         Model model) {
        model.addAttribute("catalogs", catalogService.findAllCatalogs());
        model.addAttribute("keyword", keyword);
        model.addAttribute("catalogId", catalogId);
        model.addAttribute("type", type);

        if (keyword == null || keyword.isBlank()) {
            return "catalog/search";
        }

        String kw = keyword.trim();
        boolean searchColumn  = type.isEmpty() || type.equals("column");
        boolean searchRoutine = type.isEmpty() || type.equals("routine");

        if (searchColumn) {
            List<ColumnSearchResult> colResults = catalogService.searchByColumnName(catalogId, kw);
            model.addAttribute("columnResults", colResults);

            // 컬럼 전용 모드에서만 연관 루틴 표시 (전체 모드는 routineResults와 중복이므로 제외)
            if (type.equals("column")) {
                model.addAttribute("relatedRoutines",
                        catalogService.searchRoutinesByColumnKeyword(catalogId, kw));
            }
        }

        if (searchRoutine) {
            List<RoutineSearchResult> routineResults = catalogService.searchRoutineBody(catalogId, kw);
            model.addAttribute("routineResults", routineResults);
        }

        return "catalog/search";
    }
}
