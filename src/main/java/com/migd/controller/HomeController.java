package com.migd.controller;

import com.migd.mapper.PresetMapper;
import com.migd.mapper.PresetTableMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class HomeController {

    private final PresetMapper presetMapper;
    private final PresetTableMapper presetTableMapper;

    @GetMapping("/")
    public String index(Model model) {
        var presets = presetMapper.findAll();
        model.addAttribute("presetCount", presets.size());
        model.addAttribute("tableCount", presetTableMapper.countAll());
        model.addAttribute("recentPresets", presets.stream().limit(5).toList());
        return "index";
    }
}
