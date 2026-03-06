package org.example.settings.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/** 系统配置页：渲染 templates/settings/settings.html，配置数据由前端请求 /api/settings 获取与保存。 */
@Slf4j
@Controller
@RequestMapping("/settings")
public class SettingsPageController {

    @GetMapping
    public String page() {
        log.debug("访问系统配置页");
        return "settings/settings";
    }
}

