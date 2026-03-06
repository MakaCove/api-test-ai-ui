package org.example.testcase.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.api.entity.ApiInfo;
import org.example.api.mapper.ApiInfoMapper;
import org.example.testcase.entity.TestCase;
import org.example.testcase.mapper.TestCaseMapper;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/** 接口下测试用例 REST API：分页列表、新建、详情、更新、禁用、启用。 */
@Slf4j
@RestController
@RequestMapping("/api/apis/{apiId}/testcases")
@RequiredArgsConstructor
public class TestCaseApiController {

    private final TestCaseMapper testCaseMapper;
    private final ApiInfoMapper apiInfoMapper;

    @GetMapping
    public Map<String, Object> list(@PathVariable("apiId") Long apiId,
                                    @RequestParam(value = "page", defaultValue = "1") int pageNum,
                                    @RequestParam(value = "size", defaultValue = "10") int pageSize) {
        log.debug("查询接口用例列表 apiId={} page={}", apiId, pageNum);
        LambdaQueryWrapper<TestCase> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TestCase::getApiId, apiId).isNull(TestCase::getDeletedAt)
                .orderByDesc(TestCase::getCreatedAt);
        Page<TestCase> page = testCaseMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        Map<String, Object> result = new HashMap<>();
        result.put("records", page.getRecords());
        result.put("total", page.getTotal());
        result.put("page", page.getCurrent());
        result.put("size", page.getSize());
        return result;
    }

    @PostMapping
    public TestCase create(@PathVariable("apiId") Long apiId,
                           @RequestBody TestCase testCase) {
        log.info("新建用例 apiId={} caseName={}", apiId, testCase != null ? testCase.getCaseName() : null);
        ApiInfo api = apiInfoMapper.selectById(apiId);
        if (api == null) {
            throw new IllegalArgumentException("接口不存在");
        }
        testCase.setId(null);
        testCase.setProjectId(api.getProjectId());
        testCase.setApiId(apiId);
        if (testCase.getStatus() == null || testCase.getStatus().isBlank()) {
            testCase.setStatus("active");
        }
        if (testCase.getCodeGenStatus() == null || testCase.getCodeGenStatus().isBlank()) {
            testCase.setCodeGenStatus("pending");
        }
        if (testCase.getPriority() == null) {
            testCase.setPriority(3);
        }
        LocalDateTime now = LocalDateTime.now();
        testCase.setCreatedAt(now);
        testCase.setUpdatedAt(now);
        testCaseMapper.insert(testCase);
        return testCase;
    }

    @GetMapping("/{id}")
    public TestCase detail(@PathVariable("apiId") Long apiId,
                           @PathVariable("id") Long id) {
        TestCase testCase = testCaseMapper.selectById(id);
        if (testCase == null || !apiId.equals(testCase.getApiId()) || testCase.getDeletedAt() != null) {
            throw new IllegalArgumentException("测试用例不存在或不属于当前接口");
        }
        return testCase;
    }

    @PutMapping("/{id}")
    public TestCase update(@PathVariable("apiId") Long apiId,
                           @PathVariable("id") Long id,
                           @RequestBody TestCase body) {
        TestCase existing = testCaseMapper.selectById(id);
        if (existing == null || !apiId.equals(existing.getApiId()) || existing.getDeletedAt() != null) {
            throw new IllegalArgumentException("测试用例不存在或不属于当前接口");
        }
        existing.setCaseName(body.getCaseName());
        existing.setCaseType(body.getCaseType());
        existing.setDescription(body.getDescription());
        existing.setRequestData(body.getRequestData());
        existing.setExpectedResponse(body.getExpectedResponse());
        existing.setValidationRules(body.getValidationRules());
        existing.setPriority(body.getPriority() != null ? body.getPriority() : existing.getPriority());
        existing.setStatus(body.getStatus() != null ? body.getStatus() : existing.getStatus());
        existing.setUpdatedAt(LocalDateTime.now());
        testCaseMapper.updateById(existing);
        return existing;
    }

    @DeleteMapping("/{id}")
    public void disable(@PathVariable("apiId") Long apiId,
                        @PathVariable("id") Long id) {
        TestCase existing = testCaseMapper.selectById(id);
        if (existing == null || !apiId.equals(existing.getApiId()) || existing.getDeletedAt() != null) {
            throw new IllegalArgumentException("测试用例不存在或不属于当前接口");
        }
        existing.setStatus("disabled");
        existing.setUpdatedAt(LocalDateTime.now());
        testCaseMapper.updateById(existing);
    }

    @PatchMapping("/{id}/enable")
    public TestCase enable(@PathVariable("apiId") Long apiId,
                          @PathVariable("id") Long id) {
        TestCase existing = testCaseMapper.selectById(id);
        if (existing == null || !apiId.equals(existing.getApiId()) || existing.getDeletedAt() != null) {
            throw new IllegalArgumentException("测试用例不存在或不属于当前接口");
        }
        existing.setStatus("active");
        existing.setUpdatedAt(LocalDateTime.now());
        testCaseMapper.updateById(existing);
        return existing;
    }
}

