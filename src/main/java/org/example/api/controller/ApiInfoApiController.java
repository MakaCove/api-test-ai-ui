package org.example.api.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.api.entity.ApiInfo;
import org.example.api.mapper.ApiInfoMapper;
import org.example.document.entity.Document;
import org.example.document.mapper.DocumentMapper;
import org.example.testcase.entity.TestCase;
import org.example.testcase.mapper.TestCaseMapper;
import org.example.testcase.service.TestCaseGenerateService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 项目下接口 REST API：列表（含用例数）、新建、详情、更新、启用、禁用/删除、AI 生成用例。
 */
@Slf4j
@RestController
@RequestMapping("/api/projects/{projectId}/apis")
@RequiredArgsConstructor
public class ApiInfoApiController {

    private final ApiInfoMapper apiInfoMapper;
    private final TestCaseMapper testCaseMapper;
    private final DocumentMapper documentMapper;
    private final TestCaseGenerateService testCaseGenerateService;

    @GetMapping
    public Map<String, Object> list(@PathVariable("projectId") Long projectId,
                                    @RequestParam(value = "page", defaultValue = "1") int pageNum,
                                    @RequestParam(value = "size", defaultValue = "10") int pageSize) {
        log.debug("查询项目接口列表 projectId={} page={}", projectId, pageNum);
        LambdaQueryWrapper<ApiInfo> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ApiInfo::getProjectId, projectId).orderByDesc(ApiInfo::getCreatedAt);

        Page<ApiInfo> page = apiInfoMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        List<ApiInfo> records = page.getRecords();

        // 每个接口的测试用例数量
        Map<Long, Long> testCaseCountByApiId = new HashMap<>();
        // 每个接口所属文档标题（documentId -> title）
        Map<Long, String> documentIdToTitle = new HashMap<>();
        if (records != null && !records.isEmpty()) {
            List<Long> apiIds = records.stream().map(ApiInfo::getId).collect(Collectors.toList());
            for (Long apiId : apiIds) {
                long count = testCaseMapper.selectCount(
                        new LambdaQueryWrapper<TestCase>().eq(TestCase::getApiId, apiId).isNull(TestCase::getDeletedAt));
                testCaseCountByApiId.put(apiId, count);
            }
            for (ApiInfo api : records) {
                if (api.getDocumentId() != null && !documentIdToTitle.containsKey(api.getDocumentId())) {
                    Document doc = documentMapper.selectById(api.getDocumentId());
                    documentIdToTitle.put(api.getDocumentId(), doc != null && doc.getTitle() != null ? doc.getTitle() : "");
                }
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("records", records);
        result.put("total", page.getTotal());
        result.put("page", page.getCurrent());
        result.put("size", page.getSize());
        result.put("testCaseCountByApiId", testCaseCountByApiId);
        result.put("documentIdToTitle", documentIdToTitle);
        return result;
    }

    @PostMapping
    public ApiInfo create(@PathVariable("projectId") Long projectId,
                          @RequestBody ApiInfo api) {
        log.info("新建接口 projectId={} apiName={}", projectId, api != null ? api.getApiName() : null);
        api.setId(null);
        api.setProjectId(projectId);
        if (api.getStatus() == null || api.getStatus().isBlank()) {
            api.setStatus("active");
        }
        if (api.getCaseGenStatus() == null || api.getCaseGenStatus().isBlank()) {
            api.setCaseGenStatus("pending");
        }
        LocalDateTime now = LocalDateTime.now();
        api.setCreatedAt(now);
        api.setUpdatedAt(now);
        apiInfoMapper.insert(api);
        return api;
    }

    /** 根据接口信息通过 AI 生成测试用例（先软删该接口下原有用例再插入新用例）。点击后先置为「生成中」防重复点击，异步执行生成，完成后置为「已生成」或「失败」。 */
    @PostMapping("/{id}/generate-cases")
    public Map<String, Object> generateCases(@PathVariable("projectId") Long projectId,
                                              @PathVariable("id") Long apiId) {
        log.info("AI 生成用例 projectId={} apiId={}", projectId, apiId);
        ApiInfo api = apiInfoMapper.selectById(apiId);
        if (api == null || !projectId.equals(api.getProjectId())) {
            throw new IllegalArgumentException("接口不存在或不属于当前项目");
        }
        if ("generating".equals(api.getCaseGenStatus())) {
            throw new IllegalArgumentException("正在生成中，请勿重复点击");
        }
        // 立即置为生成中，防止重复点击
        api.setCaseGenStatus("generating");
        api.setUpdatedAt(LocalDateTime.now());
        apiInfoMapper.updateById(api);

        CompletableFuture.runAsync(() -> {
            try {
                testCaseGenerateService.generateForApi(projectId, apiId);
                ApiInfo updated = apiInfoMapper.selectById(apiId);
                if (updated != null) {
                    updated.setCaseGenStatus("done");
                    updated.setUpdatedAt(LocalDateTime.now());
                    apiInfoMapper.updateById(updated);
                }
            } catch (Exception e) {
                log.warn("AI 生成用例失败 apiId={}: {}", apiId, e.getMessage());
                ApiInfo failed = apiInfoMapper.selectById(apiId);
                if (failed != null) {
                    failed.setCaseGenStatus("failed");
                    failed.setUpdatedAt(LocalDateTime.now());
                    apiInfoMapper.updateById(failed);
                }
            }
        });

        Map<String, Object> result = new HashMap<>();
        result.put("status", "generating");
        result.put("message", "已开始生成，请刷新列表查看进度");
        return result;
    }

    @GetMapping("/{id}")
    public ApiInfo detail(@PathVariable("id") Long id) {
        return apiInfoMapper.selectById(id);
    }

    @PutMapping("/{id}")
    public ApiInfo update(@PathVariable("projectId") Long projectId,
                          @PathVariable("id") Long id,
                          @RequestBody ApiInfo body) {
        ApiInfo existing = apiInfoMapper.selectById(id);
        if (existing == null || !projectId.equals(existing.getProjectId())) {
            throw new IllegalArgumentException("接口不存在或不属于当前项目");
        }
        existing.setApiName(body.getApiName());
        existing.setApiPath(body.getApiPath());
        existing.setHttpMethod(body.getHttpMethod());
        existing.setDescription(body.getDescription());
        existing.setTags(body.getTags());
        if (body.getRequestParams() != null) existing.setRequestParams(body.getRequestParams());
        if (body.getResponseSchema() != null) existing.setResponseSchema(body.getResponseSchema());
        if (body.getStatus() != null && !body.getStatus().isBlank()) {
            existing.setStatus(body.getStatus());
        }
        existing.setUpdatedAt(LocalDateTime.now());
        apiInfoMapper.updateById(existing);
        return existing;
    }

    @DeleteMapping("/{id}")
    public void disable(@PathVariable("projectId") Long projectId,
                        @PathVariable("id") Long id) {
        ApiInfo existing = apiInfoMapper.selectById(id);
        if (existing == null || !projectId.equals(existing.getProjectId())) {
            throw new IllegalArgumentException("接口不存在或不属于当前项目");
        }
        existing.setStatus("disabled");
        existing.setUpdatedAt(LocalDateTime.now());
        apiInfoMapper.updateById(existing);
    }

    /** 启用接口 */
    @PatchMapping("/{id}/enable")
    public ApiInfo enable(@PathVariable("projectId") Long projectId,
                         @PathVariable("id") Long id) {
        ApiInfo existing = apiInfoMapper.selectById(id);
        if (existing == null || !projectId.equals(existing.getProjectId())) {
            throw new IllegalArgumentException("接口不存在或不属于当前项目");
        }
        existing.setStatus("active");
        existing.setUpdatedAt(LocalDateTime.now());
        apiInfoMapper.updateById(existing);
        return existing;
    }
}

