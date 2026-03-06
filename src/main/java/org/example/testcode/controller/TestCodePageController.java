package org.example.testcode.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.api.entity.ApiInfo;
import org.example.api.mapper.ApiInfoMapper;
import org.example.project.entity.Project;
import org.example.project.mapper.ProjectMapper;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** 接口下测试代码列表页：from=testcodes 时侧栏高亮「用例自动化代码列表」并简化面包屑。 */
@Slf4j
@Controller
@RequestMapping("/projects/{projectId}/apis/{apiId}/testcodes")
@RequiredArgsConstructor
public class TestCodePageController {

    private final ProjectMapper projectMapper;
    private final ApiInfoMapper apiInfoMapper;

    @GetMapping
    public String page(@PathVariable("projectId") Long projectId,
                       @PathVariable("apiId") Long apiId,
                       @RequestParam(value = "from", required = false) String from,
                       Model model) {
        log.debug("访问测试代码列表页 projectId={} apiId={} from={}", projectId, apiId, from);
        Project project = projectMapper.selectById(projectId);
        ApiInfo api = apiInfoMapper.selectById(apiId);
        model.addAttribute("projectId", projectId);
        model.addAttribute("apiId", apiId);
        model.addAttribute("projectName", project != null ? project.getName() : "项目");
        model.addAttribute("apiName", api != null ? api.getApiName() : "接口");
        // 从「用例自动化代码列表」点详情进入时高亮该菜单并简化面包屑
        model.addAttribute("fromTestcodes", "testcodes".equalsIgnoreCase(from));
        return "testcode/testcode-list";
    }
}

