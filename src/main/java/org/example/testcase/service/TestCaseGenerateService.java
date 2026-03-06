package org.example.testcase.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.common.ai.AiClientService;
import org.example.api.entity.ApiInfo;
import org.example.api.mapper.ApiInfoMapper;
import org.example.common.constant.AiPrompt;
import org.example.testcase.entity.TestCase;
import org.example.testcase.mapper.TestCaseMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 根据接口信息通过 AI 生成测试用例（参考 api-test-generate 逻辑）。
 * 要求 AI 返回纯 JSON 数组，逐条插入用例表。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TestCaseGenerateService {

    private final ApiInfoMapper apiInfoMapper;
    private final TestCaseMapper testCaseMapper;
    private final AiClientService aiClientService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 先调用 AI 生成用例（不占事务），再在短事务内软删旧用例并插入新用例，避免锁等待超时。
     */
    public List<TestCase> generateForApi(Long projectId, Long apiId) {
        log.info("开始为接口生成测试用例 projectId={} apiId={}", projectId, apiId);
        ApiInfo api = apiInfoMapper.selectById(apiId);
        if (api == null || !projectId.equals(api.getProjectId())) {
            throw new IllegalArgumentException("接口不存在或不属于当前项目");
        }

        String apiInfoJson = buildApiInfoJson(api);
        String userPrompt = String.format(AiPrompt.USER_TEST_CASE_GENERATION, apiInfoJson);
        String aiResult = aiClientService.analyzeDocumentToApis(AiPrompt.SYSTEM_TEST_CASE_DESIGNER, userPrompt);
        String clean = cleanMarkdown(aiResult);
        clean = sanitizeJson(clean);

        JsonNode array = parseCasesArray(clean);
        LocalDateTime now = LocalDateTime.now();
        List<TestCase> toInsert = new ArrayList<>();
        array.elements().forEachRemaining(node -> toInsert.add(mapToTestCase(projectId, apiId, node, now)));

        return saveGeneratedCases(apiId, toInsert);
    }

    @Transactional(rollbackFor = Exception.class)
    protected List<TestCase> saveGeneratedCases(Long apiId, List<TestCase> toInsert) {
        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<TestCase> deleteWrapper = new LambdaUpdateWrapper<>();
        deleteWrapper.eq(TestCase::getApiId, apiId).isNull(TestCase::getDeletedAt)
                .set(TestCase::getDeletedAt, now).set(TestCase::getUpdatedAt, now);
        testCaseMapper.update(null, deleteWrapper);
        log.info("已软删除接口 {} 下原有测试用例", apiId);

        List<TestCase> saved = new ArrayList<>();
        for (TestCase tc : toInsert) {
            tc.setCreatedAt(now);
            tc.setUpdatedAt(now);
            testCaseMapper.insert(tc);
            saved.add(tc);
            log.debug("插入用例: {} - {}", tc.getCaseType(), tc.getCaseName());
        }
        log.info("接口 {} 写入 {} 条测试用例", apiId, saved.size());
        return saved;
    }

    private String buildApiInfoJson(ApiInfo api) {
        try {
            return objectMapper.writeValueAsString(new ApiInfoForPrompt(
                api.getId(), api.getApiName(), api.getApiPath(), api.getHttpMethod(),
                api.getDescription(), api.getRequestParams(), api.getResponseSchema()
            ));
        } catch (Exception e) {
            throw new RuntimeException("构建接口信息 JSON 失败", e);
        }
    }

    private String cleanMarkdown(String content) {
        if (content == null) return "[]";
        String t = content.trim();
        if (t.startsWith("```json")) t = t.substring(7).trim();
        else if (t.startsWith("```")) t = t.substring(3).trim();
        if (t.endsWith("```")) t = t.substring(0, t.length() - 3).trim();
        return t;
    }

    private String sanitizeJson(String json) {
        if (json == null || json.isBlank()) return json;
        json = Pattern.compile("(\\d+)(\\s+)(\")([a-zA-Z_])").matcher(json).replaceAll("$1,$2$3$4");
        json = Pattern.compile("(\\})(\\s+)(\")([a-zA-Z_])").matcher(json).replaceAll("$1,$2$3$4");
        return json;
    }

    /** 解析为用例数组：支持根节点为数组，或根节点为 {"cases": [...]} */
    private JsonNode parseCasesArray(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root.isArray()) return root;
            if (root.has("cases") && root.get("cases").isArray()) return root.get("cases");
            throw new IllegalArgumentException("AI 返回的 JSON 既不是数组也没有 cases 数组");
        } catch (Exception e) {
            log.error("解析用例 JSON 失败: {}", e.getMessage(), e);
            throw new RuntimeException("解析 AI 返回内容失败: " + e.getMessage(), e);
        }
    }

    private TestCase mapToTestCase(Long projectId, Long apiId, JsonNode node, LocalDateTime now) {
        TestCase tc = new TestCase();
        tc.setProjectId(projectId);
        tc.setApiId(apiId);
        tc.setCaseName(textOrNull(node, "caseName"));
        String caseType = textOrNull(node, "caseType");
        if ("exception".equalsIgnoreCase(caseType)) caseType = "error";
        tc.setCaseType(caseType != null ? caseType : "normal");
        tc.setDescription(textOrNull(node, "description"));
        tc.setRequestData(toJsonString(node.get("requestData")));
        tc.setExpectedResponse(toJsonString(node.get("expectedResponse")));
        tc.setValidationRules(toJsonString(node.get("validationRules")));
        tc.setPriority(node.has("priority") && !node.get("priority").isNull() ? node.get("priority").asInt(3) : 3);
        tc.setStatus("active");
        tc.setCreatedAt(now);
        tc.setUpdatedAt(now);
        return tc;
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v != null && !v.isNull() ? v.asText() : null;
    }

    /** 节点转为 JSON 字符串：文本直接取，对象/数组序列化 */
    private String toJsonString(JsonNode v) {
        if (v == null || v.isNull()) return null;
        if (v.isTextual()) return v.asText();
        try {
            return objectMapper.writeValueAsString(v);
        } catch (Exception e) {
            return v.toString();
        }
    }

    /** 仅用于序列化给 AI 的接口信息（避免循环等） */
    private static class ApiInfoForPrompt {
        public Long apiId;
        public String apiName, apiPath, httpMethod, description, requestParams, responseSchema;
        ApiInfoForPrompt(Long apiId, String apiName, String apiPath, String httpMethod,
                         String description, String requestParams, String responseSchema) {
            this.apiId = apiId;
            this.apiName = apiName;
            this.apiPath = apiPath;
            this.httpMethod = httpMethod;
            this.description = description;
            this.requestParams = requestParams;
            this.responseSchema = responseSchema;
        }
    }
}
