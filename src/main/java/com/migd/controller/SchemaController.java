package com.migd.controller;

import com.migd.config.MigdProperties;
import com.migd.dto.DbConnInfo;
import com.migd.dto.FullSchemaDumpResult;
import com.migd.dto.SchemaExecuteRequest;
import com.migd.service.SchemaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Slf4j
@Controller
@RequestMapping("/schema")
@RequiredArgsConstructor
public class SchemaController {

    private final SchemaService schemaService;
    private final MigdProperties migdProperties;

    @GetMapping
    public String executePage(Model model) {
        MigdProperties.SourceDb src = migdProperties.getSourceDb();
        model.addAttribute("sourceDbHost", src.getHost());
        model.addAttribute("sourceDbPort", src.getPort());
        model.addAttribute("sourceDbDb", src.getDb());
        model.addAttribute("request", new SchemaExecuteRequest());
        return "schema/execute";
    }

    @PostMapping("/run")
    public String runSchema(@ModelAttribute SchemaExecuteRequest request, RedirectAttributes attrs) {
        try {
            validateTargetHost(request.getTgtHost());

            MigdProperties.SourceDb s = migdProperties.getSourceDb();
            DbConnInfo src = new DbConnInfo(s.getHost(), s.getPort(), s.getDb(), s.getUser(), s.getPassword());
            DbConnInfo tgt = new DbConnInfo(
                    request.getTgtHost(), request.getTgtPort(), request.getTgtDb(),
                    request.getTgtUser(), request.getTgtPassword());

            log.info("스키마 이관 시작 - schema={}, tgt: {}:{}/{}",
                    request.getSchemaName(), tgt.host(), tgt.port(), tgt.db());

            FullSchemaDumpResult result = schemaService.applyFullSchemaDump(
                    src, tgt, request.getSchemaName(), migdProperties.getPgDumpPath());
            attrs.addFlashAttribute("dumpResult", result);
            attrs.addFlashAttribute("tgtHost", request.getTgtHost());
            attrs.addFlashAttribute("tgtDb", request.getTgtDb());

        } catch (IllegalArgumentException e) {
            log.warn("스키마 이관 검증 오류: {}", e.getMessage());
            attrs.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            log.error("스키마 이관 오류", e);
            attrs.addFlashAttribute("error", "스키마 이관 처리 중 오류가 발생했습니다. 로그를 확인하세요.");
        }
        return "redirect:/schema";
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
