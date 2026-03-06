package org.example.web.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.api.entity.ApiInfo;
import org.example.api.mapper.ApiInfoMapper;
import org.example.document.entity.Document;
import org.example.document.mapper.DocumentMapper;
import org.example.project.entity.Project;
import org.example.project.mapper.ProjectMapper;
import org.example.testcase.entity.TestCase;
import org.example.testcase.mapper.TestCaseMapper;
import org.example.testcode.entity.TestCode;
import org.example.testcode.mapper.TestCodeMapper;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工作台与全局列表页控制器。
 * 提供：工作台首页、全局接口列表、全局用例列表、用例详情、用例代码详情、全局测试代码列表。
 * 数据由服务端分页查询，视图位于 templates/dashboard、api、testcase、testcode。
 */
@Slf4j
@Controller
@RequestMapping("/")
@RequiredArgsConstructor
public class HomeController {

    private final ApiInfoMapper apiInfoMapper;
    private final TestCaseMapper testCaseMapper;
    private final ProjectMapper projectMapper;
    private final TestCodeMapper testCodeMapper;
    private final DocumentMapper documentMapper;

    private static final int RECENT_LIMIT = 10;
    private static final int PAGE_SIZE = 10;

    /** 工作台首页：快捷入口 + 最近接口/最近用例 */
    @GetMapping
    public String index(Model model) {
        log.debug("访问工作台首页");
        model.addAttribute("title", "API 自动化测试 AI 平台");
        model.addAttribute("welcomeMessage", "在这里，你可以从接口文档出发，一路生成接口信息、测试用例和自动化测试代码。");
        fillRecentLists(model);
        return "dashboard/index";
    }

    /** 全局接口列表页，每页 10 条分页 */
    @GetMapping("/apis")
    public String apisPage(@RequestParam(value = "page", defaultValue = "1") int pageNum, Model model) {
        log.debug("访问全局接口列表 page={}", pageNum);
        LambdaQueryWrapper<ApiInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(ApiInfo::getCreatedAt);
        Page<ApiInfo> page = apiInfoMapper.selectPage(new Page<>(pageNum, PAGE_SIZE), wrapper);
        List<ApiInfo> list = page.getRecords() != null ? page.getRecords() : new ArrayList<>();
        model.addAttribute("recentApis", list);
        long total = page.getTotal();
        model.addAttribute("totalApis", total);
        model.addAttribute("currentPage", page.getCurrent());
        model.addAttribute("totalPages", total == 0 ? 1 : (int) ((total + PAGE_SIZE - 1) / PAGE_SIZE));
        Map<Long, String> projectIdToName = new LinkedHashMap<>();
        Map<Long, String> documentIdToName = new LinkedHashMap<>();
        for (ApiInfo api : list) {
            if (api.getProjectId() != null && !projectIdToName.containsKey(api.getProjectId())) {
                Project p = projectMapper.selectById(api.getProjectId());
                projectIdToName.put(api.getProjectId(), p != null ? p.getName() : "");
            }
            if (api.getDocumentId() != null && !documentIdToName.containsKey(api.getDocumentId())) {
                Document doc = documentMapper.selectById(api.getDocumentId());
                documentIdToName.put(api.getDocumentId(), doc != null && doc.getTitle() != null ? doc.getTitle() : "");
            }
        }
        model.addAttribute("projectIdToName", projectIdToName);
        model.addAttribute("documentIdToName", documentIdToName);
        Map<String, String> caseGenStatusDisplay = new LinkedHashMap<>();
        caseGenStatusDisplay.put("pending", "未生成");
        caseGenStatusDisplay.put("generating", "生成中");
        caseGenStatusDisplay.put("done", "已生成");
        caseGenStatusDisplay.put("failed", "失败");
        model.addAttribute("caseGenStatusDisplay", caseGenStatusDisplay);
        return "api/apis";
    }

    /** 全局用例列表页，每页 10 条分页 */
    @GetMapping("/testcases")
    public String testcasesPage(@RequestParam(value = "page", defaultValue = "1") int pageNum, Model model) {
        log.debug("访问全局用例列表 page={}", pageNum);
        LambdaQueryWrapper<TestCase> wrapper = new LambdaQueryWrapper<>();
        wrapper.isNull(TestCase::getDeletedAt).orderByDesc(TestCase::getCreatedAt);
        Page<TestCase> page = testCaseMapper.selectPage(new Page<>(pageNum, PAGE_SIZE), wrapper);
        List<TestCase> list = page.getRecords() != null ? page.getRecords() : new ArrayList<>();
        model.addAttribute("recentCases", list);
        long total = page.getTotal();
        model.addAttribute("totalCases", total);
        model.addAttribute("currentPage", page.getCurrent());
        model.addAttribute("totalPages", total == 0 ? 1 : (int) ((total + PAGE_SIZE - 1) / PAGE_SIZE));
        Map<Long, String> projectIdToName = new LinkedHashMap<>();
        Map<Long, String> apiIdToName = new LinkedHashMap<>();
        for (TestCase tc : list) {
            if (tc.getProjectId() != null && !projectIdToName.containsKey(tc.getProjectId())) {
                Project p = projectMapper.selectById(tc.getProjectId());
                projectIdToName.put(tc.getProjectId(), p != null ? p.getName() : "");
            }
            if (tc.getApiId() != null && !apiIdToName.containsKey(tc.getApiId())) {
                ApiInfo api = apiInfoMapper.selectById(tc.getApiId());
                apiIdToName.put(tc.getApiId(), api != null ? api.getApiName() : "");
            }
        }
        model.addAttribute("projectIdToName", projectIdToName);
        model.addAttribute("apiIdToName", apiIdToName);
        Map<String, String> codeGenStatusDisplay = new LinkedHashMap<>();
        codeGenStatusDisplay.put("pending", "未生成");
        codeGenStatusDisplay.put("generating", "生成中");
        codeGenStatusDisplay.put("done", "已生成");
        codeGenStatusDisplay.put("failed", "失败");
        model.addAttribute("codeGenStatusDisplay", codeGenStatusDisplay);
        Map<String, String> statusDisplay = new LinkedHashMap<>();
        statusDisplay.put("active", "启用");
        statusDisplay.put("disabled", "禁用");
        model.addAttribute("statusDisplay", statusDisplay);
        return "testcase/testcases";
    }

    /** 单个用例详情页：展示 t_test_case 全部字段，可跳转代码页或触发 AI 生成代码 */
    @GetMapping("/testcases/detail/{id}")
    public String testcaseDetail(@PathVariable("id") Long id, Model model) {
        log.debug("访问用例详情 id={}", id);
        TestCase tc = testCaseMapper.selectById(id);
        if (tc == null || tc.getDeletedAt() != null) {
            log.warn("用例不存在或已删除 id={}", id);
            return "redirect:/testcases";
        }
        Project project = projectMapper.selectById(tc.getProjectId());
        ApiInfo api = apiInfoMapper.selectById(tc.getApiId());
        model.addAttribute("case", tc);
        model.addAttribute("projectId", tc.getProjectId());
        model.addAttribute("apiId", tc.getApiId());
        model.addAttribute("projectName", project != null ? project.getName() : "项目");
        model.addAttribute("apiName", api != null ? api.getApiName() : "接口");
        Map<String, String> codeGenStatusDisplay = new LinkedHashMap<>();
        codeGenStatusDisplay.put("pending", "未生成");
        codeGenStatusDisplay.put("generating", "生成中");
        codeGenStatusDisplay.put("done", "已生成");
        codeGenStatusDisplay.put("failed", "失败");
        model.addAttribute("codeGenStatusDisplay", codeGenStatusDisplay);
        Map<String, String> statusDisplay = new LinkedHashMap<>();
        statusDisplay.put("active", "启用");
        statusDisplay.put("disabled", "禁用");
        model.addAttribute("statusDisplay", statusDisplay);
        return "testcase/testcase-detail";
    }

    /** 单个用例的测试代码详情：有则展示，无则展示空状态并支持一键生成 */
    @GetMapping("/testcases/{caseId}/code")
    public String testcaseCodeDetail(@PathVariable("caseId") Long caseId, Model model) {
        log.debug("访问用例代码详情 caseId={}", caseId);
        TestCase tc = testCaseMapper.selectById(caseId);
        if (tc == null || tc.getDeletedAt() != null) {
            log.warn("用例不存在或已删除 caseId={}", caseId);
            return "redirect:/testcases";
        }
        Project project = projectMapper.selectById(tc.getProjectId());
        ApiInfo api = apiInfoMapper.selectById(tc.getApiId());
        model.addAttribute("case", tc);
        model.addAttribute("projectId", tc.getProjectId());
        model.addAttribute("apiId", tc.getApiId());
        model.addAttribute("projectName", project != null ? project.getName() : "项目");
        model.addAttribute("apiName", api != null ? api.getApiName() : "接口");

        LambdaQueryWrapper<TestCode> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TestCode::getTestCaseId, caseId).eq(TestCode::getApiId, tc.getApiId()).isNull(TestCode::getDeletedAt)
                .orderByDesc(TestCode::getCreatedAt).last("LIMIT 1");
        TestCode code = testCodeMapper.selectOne(wrapper);
        model.addAttribute("testCode", code);
        return "testcode/testcode-case-detail";
    }

    /** 全局测试代码列表页，每页 10 条分页，详情跳转至对应接口的测试代码页 */
    @GetMapping("/testcodes")
    public String testcodesPage(@RequestParam(value = "page", defaultValue = "1") int pageNum, Model model) {
        log.debug("访问全局测试代码列表 page={}", pageNum);
        LambdaQueryWrapper<TestCode> wrapper = new LambdaQueryWrapper<>();
        wrapper.isNull(TestCode::getDeletedAt).orderByDesc(TestCode::getCreatedAt);
        Page<TestCode> page = testCodeMapper.selectPage(new Page<>(pageNum, PAGE_SIZE), wrapper);
        List<TestCode> list = page.getRecords() != null ? page.getRecords() : new ArrayList<>();
        model.addAttribute("recentCodes", list);
        long total = page.getTotal();
        model.addAttribute("totalCodes", total);
        model.addAttribute("currentPage", page.getCurrent());
        model.addAttribute("totalPages", total == 0 ? 1 : (int) ((total + PAGE_SIZE - 1) / PAGE_SIZE));
        Map<Long, String> projectIdToName = new LinkedHashMap<>();
        Map<Long, String> apiIdToName = new LinkedHashMap<>();
        Map<Long, String> testCaseIdToCaseName = new LinkedHashMap<>();
        for (TestCode c : list) {
            if (c.getProjectId() != null && !projectIdToName.containsKey(c.getProjectId())) {
                Project p = projectMapper.selectById(c.getProjectId());
                projectIdToName.put(c.getProjectId(), p != null ? p.getName() : "");
            }
            if (c.getApiId() != null && !apiIdToName.containsKey(c.getApiId())) {
                ApiInfo a = apiInfoMapper.selectById(c.getApiId());
                apiIdToName.put(c.getApiId(), a != null ? a.getApiName() : "");
            }
            if (c.getTestCaseId() != null && !testCaseIdToCaseName.containsKey(c.getTestCaseId())) {
                TestCase tc = testCaseMapper.selectById(c.getTestCaseId());
                testCaseIdToCaseName.put(c.getTestCaseId(), tc != null && tc.getCaseName() != null ? tc.getCaseName() : "用例#" + c.getTestCaseId());
            }
        }
        model.addAttribute("projectIdToName", projectIdToName);
        model.addAttribute("apiIdToName", apiIdToName);
        model.addAttribute("testCaseIdToCaseName", testCaseIdToCaseName);
        Map<String, String> codeStatusDisplay = new LinkedHashMap<>();
        codeStatusDisplay.put("generated", "已生成");
        codeStatusDisplay.put("saved", "已保存");
        codeStatusDisplay.put("deprecated", "已废弃");
        model.addAttribute("codeStatusDisplay", codeStatusDisplay);
        return "testcode/testcodes";
    }

    /** 为工作台首页填充最近接口、最近用例及项目/接口名称映射 */
    private void fillRecentLists(Model model) {
        LambdaQueryWrapper<ApiInfo> apiWrapper = new LambdaQueryWrapper<>();
        apiWrapper.orderByDesc(ApiInfo::getCreatedAt).last("LIMIT " + RECENT_LIMIT);
        List<ApiInfo> recentApis = apiInfoMapper.selectList(apiWrapper);
        model.addAttribute("recentApis", recentApis != null ? recentApis : new ArrayList<>());

        LambdaQueryWrapper<TestCase> caseWrapper = new LambdaQueryWrapper<>();
        caseWrapper.isNull(TestCase::getDeletedAt).orderByDesc(TestCase::getCreatedAt).last("LIMIT " + RECENT_LIMIT);
        List<TestCase> recentCases = testCaseMapper.selectList(caseWrapper);
        model.addAttribute("recentCases", recentCases != null ? recentCases : new ArrayList<>());

        Map<Long, String> projectIdToName = new LinkedHashMap<>();
        for (ApiInfo api : recentApis) {
            if (api.getProjectId() != null && !projectIdToName.containsKey(api.getProjectId())) {
                Project p = projectMapper.selectById(api.getProjectId());
                projectIdToName.put(api.getProjectId(), p != null ? p.getName() : "");
            }
        }
        for (TestCase tc : recentCases) {
            if (tc.getProjectId() != null && !projectIdToName.containsKey(tc.getProjectId())) {
                Project p = projectMapper.selectById(tc.getProjectId());
                projectIdToName.put(tc.getProjectId(), p != null ? p.getName() : "");
            }
        }
        model.addAttribute("projectIdToName", projectIdToName);

        Map<Long, String> apiIdToName = new LinkedHashMap<>();
        for (TestCase tc : recentCases) {
            if (tc.getApiId() != null && !apiIdToName.containsKey(tc.getApiId())) {
                ApiInfo api = apiInfoMapper.selectById(tc.getApiId());
                apiIdToName.put(tc.getApiId(), api != null ? api.getApiName() : "");
            }
        }
        model.addAttribute("apiIdToName", apiIdToName);
    }
}
