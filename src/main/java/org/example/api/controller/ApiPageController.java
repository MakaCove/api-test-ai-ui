package org.example.api.controller;

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

/** 接口下用例列表页：由项目详情或全局接口列表进入，from 参数控制侧栏高亮与面包屑。 */
@Slf4j
@Controller
@RequestMapping("/projects/{projectId}/apis")
@RequiredArgsConstructor
public class ApiPageController {

    private final ProjectMapper projectMapper;
    private final ApiInfoMapper apiInfoMapper;

    @GetMapping("/{apiId}/testcases")
    public String testCasePage(@PathVariable("projectId") Long projectId,
                               @PathVariable("apiId") Long apiId,
                               @RequestParam(value = "from", required = false) String from,
                               Model model) {
        log.debug("访问接口用例列表页 projectId={} apiId={} from={}", projectId, apiId, from);
        Project project = projectMapper.selectById(projectId);
        ApiInfo apiInfo = apiInfoMapper.selectById(apiId);
        model.addAttribute("projectId", projectId);
        model.addAttribute("apiId", apiId);
        model.addAttribute("projectName", project != null ? project.getName() : "项目");
        model.addAttribute("apiName", apiInfo != null ? apiInfo.getApiName() : "接口");
        model.addAttribute("apiCaseGenStatus", apiInfo != null && apiInfo.getCaseGenStatus() != null ? apiInfo.getCaseGenStatus() : "pending");
        // 从「接口列表」点详情进入时高亮接口列表并简化面包屑
        model.addAttribute("fromApis", "apis".equalsIgnoreCase(from));
        return "testcase/testcase-list";
    }
}

