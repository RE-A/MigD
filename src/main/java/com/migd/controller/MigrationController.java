package com.migd.controller;

import com.migd.config.MigdProperties;
import com.migd.dto.DbConnInfo;
import com.migd.dto.MigrationRequest;
import com.migd.dto.MigrationResult;
import com.migd.dto.SchemaResult;
import com.migd.service.DataMigrationService;
import com.migd.service.PresetService;
import com.migd.service.SchemaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Slf4j
@Controller
@RequestMapping("/migration")
@RequiredArgsConstructor
public class MigrationController {

    private final PresetService presetService;
    private final SchemaService schemaService;
    private final DataMigrationService dataMigrationService;
    private final MigdProperties migdProperties;

    @GetMapping
    public String executePage(@RequestParam(required = false) Long presetId, Model model) {
        MigdProperties.SourceDb src = migdProperties.getSourceDb();
        model.addAttribute("sourceDbHost", src.getHost());
        model.addAttribute("sourceDbPort", src.getPort());
        model.addAttribute("sourceDbDb", src.getDb());
        model.addAttribute("presets", presetService.findAll());

        MigrationRequest request = new MigrationRequest();
        if (presetId != null) {
            try {
                request = new MigrationRequest(presetService.findTablesByPresetId(presetId));
            } catch (Exception e) {
                log.warn("프리셋 로드 실패: id={}", presetId, e);
            }
        }
        model.addAttribute("request", request);
        return "migration/execute";
    }

    @PostMapping("/run")
    public String runMigration(@ModelAttribute MigrationRequest request, RedirectAttributes attrs) {
        try {
            validateTargetHost(request.getTgtHost());

            MigdProperties.SourceDb s = migdProperties.getSourceDb();
            DbConnInfo src = new DbConnInfo(s.getHost(), s.getPort(), s.getDb(), s.getUser(), s.getPassword());
            DbConnInfo tgt = request.toTgtConnInfo();

            log.info("이관 시작 - tgt: {}:{}/{}, 테이블 수: {}",
                    tgt.host(), tgt.port(), tgt.db(), request.getTables().size());

            List<SchemaResult> schemaResults = schemaService.ensureSchemas(
                    src, tgt, request.getTables(), migdProperties.getPgDumpPath());
            MigrationResult migrationResult = dataMigrationService.migrateAll(
                    src, tgt, request.getTables());

            attrs.addFlashAttribute("schemaResults", schemaResults);
            attrs.addFlashAttribute("migrationResult", migrationResult);
            attrs.addFlashAttribute("tgtHost", request.getTgtHost());
            attrs.addFlashAttribute("tgtDb", request.getTgtDb());
            return "redirect:/migration/result";

        } catch (Exception e) {
            log.error("이관 요청 처리 오류", e);
            attrs.addFlashAttribute("error", e.getMessage());
            return "redirect:/migration";
        }
    }

    @GetMapping("/result")
    public String result(Model model) {
        return "migration/result";
    }

    private static void validateTargetHost(String host) {
        if (host == null || host.isBlank()) return;
        String h = host.toLowerCase();
        if (h.matches(".*\\.amazonaws\\.com$")
                || h.contains(".rds.")
                || h.contains(".elasticache.")
                || h.matches(".*\\.aws\\..*")) {
            throw new IllegalArgumentException(
                    "AWS 클라우드 주소는 Target DB로 사용할 수 없습니다: " + host);
        }
    }
}
