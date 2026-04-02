package com.migd.controller;

import com.migd.domain.Preset;
import com.migd.domain.PresetTable;
import com.migd.service.PresetService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

@Slf4j
@Controller
@RequestMapping("/presets")
@RequiredArgsConstructor
public class PresetController {

    private final PresetService presetService;

    @GetMapping
    public String list(Model model) {
        model.addAttribute("presets", presetService.findAll());
        return "preset/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        model.addAttribute("preset", new Preset());
        model.addAttribute("isNew", true);
        return "preset/form";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable Long id, Model model) {
        model.addAttribute("preset", presetService.findById(id));
        model.addAttribute("isNew", false);
        return "preset/form";
    }

    @PostMapping
    public String save(@ModelAttribute Preset preset, RedirectAttributes attrs) {
        presetService.save(preset);
        attrs.addFlashAttribute("success", "프리셋이 저장되었습니다.");
        return "redirect:/presets";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes attrs) {
        presetService.delete(id);
        attrs.addFlashAttribute("success", "프리셋이 삭제되었습니다.");
        return "redirect:/presets";
    }

    /**
     * 프리셋의 테이블 목록 조회 (이관 화면 AJAX 불러오기용).
     */
    @GetMapping("/{id}/tables")
    @ResponseBody
    public ResponseEntity<List<PresetTable>> getPresetTables(@PathVariable Long id) {
        return ResponseEntity.ok(presetService.findTablesByPresetId(id));
    }

    /**
     * 현재 테이블 목록을 프리셋으로 저장/업데이트 (이관 화면 AJAX 저장용).
     * id가 null이면 신규, 있으면 업데이트.
     */
    @PostMapping("/save")
    @ResponseBody
    public ResponseEntity<?> savePreset(@RequestBody Preset preset) {
        try {
            Preset saved = presetService.save(preset);
            return ResponseEntity.ok(Map.of("id", saved.getId(), "name", saved.getName()));
        } catch (Exception e) {
            log.warn("프리셋 저장 실패: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Target DB 연결 테스트 AJAX 엔드포인트.
     */
    @PostMapping("/test-connection")
    @ResponseBody
    public ResponseEntity<Map<String, String>> testConnection(
            @RequestParam String host,
            @RequestParam int port,
            @RequestParam String db,
            @RequestParam String user,
            @RequestParam String password) {
        try {
            String version = presetService.testConnection(host, port, db, user, password);
            return ResponseEntity.ok(Map.of("status", "ok", "version", version));
        } catch (SQLException e) {
            log.warn("연결 테스트 실패: {}:{}/{}", host, port, db);
            return ResponseEntity.badRequest()
                    .body(Map.of("status", "error", "message", e.getMessage()));
        }
    }
}
