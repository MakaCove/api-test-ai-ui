package org.example.testcode.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.common.ai.AiClientService;
import org.example.api.entity.ApiInfo;
import org.example.api.mapper.ApiInfoMapper;
import org.example.common.constant.AiPrompt;
import org.example.testcase.entity.TestCase;
import org.example.testcase.mapper.TestCaseMapper;
import org.example.testcode.entity.TestCode;
import org.example.testcode.mapper.TestCodeMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 测试代码生成服务：按接口批量或按单用例调用 AI 生成 JUnit5 代码，短事务内软删旧记录并插入新记录。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TestCodeGenerateService {

    private final ApiInfoMapper apiInfoMapper;
    private final TestCaseMapper testCaseMapper;
    private final TestCodeMapper testCodeMapper;
    private final AiClientService aiClientService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 按用例逐条生成测试代码（AI 在事务外调用）；再在短事务内软删旧代码并批量插入，避免锁等待超时。
     */
    public List<TestCode> generateForApi(Long apiId) {
        log.info("开始为接口批量生成测试代码 apiId={}", apiId);
        ApiInfo api = apiInfoMapper.selectById(apiId);
        if (api == null) {
            throw new IllegalArgumentException("接口不存在，id=" + apiId);
        }
        LambdaQueryWrapper<TestCase> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TestCase::getApiId, apiId).isNull(TestCase::getDeletedAt);
        List<TestCase> cases = testCaseMapper.selectList(wrapper);
        if (cases.isEmpty()) {
            throw new IllegalArgumentException("该接口下没有测试用例，无法生成测试代码");
        }

        List<TestCode> toInsert = new ArrayList<>();
        for (TestCase c : cases) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("apiId", api.getId());
            payload.put("apiName", api.getApiName());
            payload.put("apiPath", api.getApiPath());
            payload.put("httpMethod", api.getHttpMethod());
            payload.put("description", api.getDescription());
            payload.put("testCases", List.of(caseToMap(c)));
            String testCaseJson;
            try {
                testCaseJson = objectMapper.writeValueAsString(payload);
            } catch (Exception e) {
                throw new RuntimeException("构建用例 JSON 失败", e);
            }
            String userPrompt = String.format(AiPrompt.USER_TEST_CODE_GENERATION, testCaseJson);
            String raw = aiClientService.generateTestCode(AiPrompt.SYSTEM_TEST_CODE_GENERATOR, userPrompt);
            String clean = cleanCode(raw);
            String className = extractClassName(clean);
            if (className == null || className.isBlank()) {
                className = "ApiTest_" + c.getId();
            }
            TestCode code = new TestCode();
            code.setApiId(apiId);
            code.setProjectId(api.getProjectId());
            code.setTestCaseId(c.getId());
            code.setTestCaseIds(String.valueOf(c.getId()));
            code.setLanguage("java");
            code.setFramework("junit5");
            code.setClassName(className);
            code.setCodeContent(clean);
            code.setStatus("generated");
            toInsert.add(code);
            log.debug("为用例 {} 生成测试代码内容", c.getId());
        }
        return saveGeneratedCodes(apiId, toInsert);
    }

    @Transactional(rollbackFor = Exception.class)
    protected List<TestCode> saveGeneratedCodes(Long apiId, List<TestCode> toInsert) {
        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<TestCode> deleteWrapper = new LambdaUpdateWrapper<>();
        deleteWrapper.eq(TestCode::getApiId, apiId).isNull(TestCode::getDeletedAt)
                .set(TestCode::getDeletedAt, now).set(TestCode::getUpdatedAt, now);
        testCodeMapper.update(null, deleteWrapper);
        log.info("已软删除接口 {} 下原有测试代码", apiId);

        List<TestCode> saved = new ArrayList<>();
        for (TestCode code : toInsert) {
            code.setCreatedAt(now);
            code.setUpdatedAt(now);
            testCodeMapper.insert(code);
            saved.add(code);
        }
        for (TestCode code : saved) {
            if (code.getTestCaseId() != null) {
                TestCase tc = testCaseMapper.selectById(code.getTestCaseId());
                if (tc != null) {
                    tc.setCodeGenStatus("done");
                    tc.setUpdatedAt(now);
                    testCaseMapper.updateById(tc);
                }
            }
        }
        log.info("接口 {} 写入 {} 条测试代码", apiId, saved.size());
        return saved;
    }

    /** 为单条用例生成一条测试代码（重复生成时软删该用例的旧代码再插入） */
    /** 为单条用例生成一条测试代码，先软删该用例原有代码再插入。 */
    public TestCode generateForTestCase(Long apiId, Long testCaseId) {
        log.info("为单用例生成测试代码 apiId={} testCaseId={}", apiId, testCaseId);
        ApiInfo api = apiInfoMapper.selectById(apiId);
        if (api == null) throw new IllegalArgumentException("接口不存在，id=" + apiId);
        TestCase c = testCaseMapper.selectById(testCaseId);
        if (c == null || !apiId.equals(c.getApiId()) || c.getDeletedAt() != null)
            throw new IllegalArgumentException("测试用例不存在或不属于当前接口");
        Map<String, Object> payload = new HashMap<>();
        payload.put("apiId", api.getId());
        payload.put("apiName", api.getApiName());
        payload.put("apiPath", api.getApiPath());
        payload.put("httpMethod", api.getHttpMethod());
        payload.put("description", api.getDescription());
        payload.put("testCases", List.of(caseToMap(c)));
        String testCaseJson;
        try {
            testCaseJson = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            throw new RuntimeException("构建用例 JSON 失败", e);
        }
        String userPrompt = String.format(AiPrompt.USER_TEST_CODE_GENERATION, testCaseJson);
        String raw = aiClientService.generateTestCode(AiPrompt.SYSTEM_TEST_CODE_GENERATOR, userPrompt);
        String clean = cleanCode(raw);
        String className = extractClassName(clean);
        if (className == null || className.isBlank()) className = "ApiTest_" + c.getId();
        TestCode code = new TestCode();
        code.setApiId(apiId);
        code.setProjectId(api.getProjectId());
        code.setTestCaseId(c.getId());
        code.setTestCaseIds(String.valueOf(c.getId()));
        code.setLanguage("java");
        code.setFramework("junit5");
        code.setClassName(className);
        code.setCodeContent(clean);
        code.setStatus("generated");
        List<TestCode> list = saveGeneratedCodesForOneCase(apiId, testCaseId, List.of(code));
        return list.isEmpty() ? null : list.get(0);
    }

    @Transactional(rollbackFor = Exception.class)
    protected List<TestCode> saveGeneratedCodesForOneCase(Long apiId, Long testCaseId, List<TestCode> toInsert) {
        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<TestCode> deleteWrapper = new LambdaUpdateWrapper<>();
        deleteWrapper.eq(TestCode::getApiId, apiId).eq(TestCode::getTestCaseId, testCaseId).isNull(TestCode::getDeletedAt)
                .set(TestCode::getDeletedAt, now).set(TestCode::getUpdatedAt, now);
        testCodeMapper.update(null, deleteWrapper);
        List<TestCode> saved = new ArrayList<>();
        for (TestCode code : toInsert) {
            code.setCreatedAt(now);
            code.setUpdatedAt(now);
            testCodeMapper.insert(code);
            saved.add(code);
        }
        TestCase tc = testCaseMapper.selectById(testCaseId);
        if (tc != null) {
            tc.setCodeGenStatus("done");
            tc.setUpdatedAt(now);
            testCaseMapper.updateById(tc);
        }
        return saved;
    }

    private String cleanCode(String content) {
        if (content == null) return "";
        String trimmed = content.trim();
        if (trimmed.startsWith("```java")) {
            trimmed = trimmed.substring(7).trim();
        } else if (trimmed.startsWith("```")) {
            trimmed = trimmed.substring(3).trim();
        }
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3).trim();
        }
        return trimmed;
    }

    private String extractClassName(String code) {
        if (code == null) return null;
        String[] lines = code.split("\\R");
        for (String line : lines) {
            line = line.trim();
            if (line.startsWith("public class") || line.startsWith("class ")) {
                String[] parts = line.split("\\s+");
                for (int i = 0; i < parts.length - 1; i++) {
                    if ("class".equals(parts[i])) {
                        String name = parts[i + 1].replace("{", "").trim();
                        return name;
                    }
                }
            }
        }
        return null;
    }

    /** 单条用例转为 Map，便于 JSON 序列化传给 AI（含 caseName、requestData、expectedResponse、validationRules 等） */
    private Map<String, Object> caseToMap(TestCase c) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", c.getId());
        m.put("caseName", c.getCaseName());
        m.put("caseType", c.getCaseType());
        m.put("description", c.getDescription());
        m.put("requestData", c.getRequestData());
        m.put("expectedResponse", c.getExpectedResponse());
        m.put("validationRules", c.getValidationRules());
        m.put("priority", c.getPriority());
        return m;
    }
}

