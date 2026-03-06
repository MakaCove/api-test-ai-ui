package org.example.project.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.project.entity.Project;
import org.example.project.mapper.ProjectMapper;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * 项目相关页面控制器：项目列表、项目详情、文档管理页、接口管理页。
 * 仅做页面跳转与模型填充，列表数据由前端请求 /api/projects、/api/projects/{id}/documents 等获取。
 */
@Slf4j
@Controller
@RequestMapping("/projects")
@RequiredArgsConstructor
public class ProjectController {

    private final ProjectMapper projectMapper;

    @GetMapping
    public String listPage(Model model) {
        log.debug("访问项目列表页");
        return "project/project-list";
    }

    @GetMapping("/{projectId}")
    public String detailPage(@PathVariable("projectId") Long projectId, Model model) {
        log.debug("访问项目详情页 projectId={}", projectId);
        Project project = projectMapper.selectById(projectId);
        model.addAttribute("projectId", projectId);
        model.addAttribute("projectName", project != null ? project.getName() : "项目");
        return "project/project-detail";
    }

    @GetMapping("/{projectId}/documents")
    public String documentPage(@PathVariable("projectId") Long projectId, Model model) {
        log.debug("访问文档管理页 projectId={}", projectId);
        Project project = projectMapper.selectById(projectId);
        model.addAttribute("projectId", projectId);
        model.addAttribute("projectName", project != null ? project.getName() : "项目");
        return "project/document-list";
    }

    @GetMapping("/{projectId}/apis")
    public String apiPage(@PathVariable("projectId") Long projectId, Model model) {
        log.debug("访问接口管理页 projectId={}", projectId);
        Project project = projectMapper.selectById(projectId);
        model.addAttribute("projectId", projectId);
        model.addAttribute("projectName", project != null ? project.getName() : "项目");
        return "project/api-list";
    }
}

