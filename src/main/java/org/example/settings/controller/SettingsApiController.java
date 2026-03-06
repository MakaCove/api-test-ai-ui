package org.example.settings.controller;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.settings.service.SettingsService;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/** 系统配置 REST API：读取/保存 AI 与测试代码输出路径等配置（存 t_system_setting）。 */
@Slf4j
@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingsApiController {

    private final SettingsService settingsService;

    @GetMapping
    public SettingsDto getSettings() {
        log.debug("读取系统配置");
        Map<String, String> all = settingsService.getAll();
        SettingsDto dto = new SettingsDto();
        dto.setAiEndpoint(all.getOrDefault("ai.endpoint", ""));
        dto.setAiApiKey(all.getOrDefault("ai.api-key", ""));
        dto.setAiModel(all.getOrDefault("ai.model", ""));
        dto.setAiTemperature(all.getOrDefault("ai.temperature", ""));
        dto.setAiMaxTokens(all.getOrDefault("ai.max-tokens", ""));
        dto.setCodeBaseDir(all.getOrDefault("code.base-dir", ""));
        dto.setCodeBasePackage(all.getOrDefault("code.base-package", ""));
        return dto;
    }

    @PutMapping
    public void saveSettings(@RequestBody SettingsDto dto) {
        log.info("保存系统配置");
        Map<String, String> map = new HashMap<>();
        map.put("ai.endpoint", dto.getAiEndpoint());
        map.put("ai.api-key", dto.getAiApiKey());
        map.put("ai.model", dto.getAiModel());
        map.put("ai.temperature", dto.getAiTemperature());
        map.put("ai.max-tokens", dto.getAiMaxTokens());
        map.put("code.base-dir", dto.getCodeBaseDir());
        map.put("code.base-package", dto.getCodeBasePackage());
        settingsService.save(map);
    }

    @Data
    public static class SettingsDto {
        private String aiEndpoint;
        private String aiApiKey;
        private String aiModel;
        private String aiTemperature;
        private String aiMaxTokens;
        private String codeBaseDir;
        private String codeBasePackage;
    }
}

