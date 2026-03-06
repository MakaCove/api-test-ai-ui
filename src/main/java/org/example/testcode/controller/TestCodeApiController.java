package org.example.testcode.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.settings.service.SettingsService;
import org.example.testcode.entity.TestCode;
import org.example.testcode.mapper.TestCodeMapper;
import org.example.testcode.service.TestCodeGenerateService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import org.example.testcase.entity.TestCase;
import org.example.testcase.mapper.TestCaseMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 接口下测试代码 REST API：分页列表、整接口批量生成、单用例生成、保存到工程、下载。
 */
@Slf4j
@RestController
@RequestMapping("/api/apis/{apiId}/testcodes")
@RequiredArgsConstructor
public class TestCodeApiController {

    private final TestCodeMapper testCodeMapper;
    private final TestCodeGenerateService testCodeGenerateService;
    private final SettingsService settingsService;
    private final TestCaseMapper testCaseMapper;

    @GetMapping
    public Map<String, Object> list(@PathVariable("apiId") Long apiId,
                                    @RequestParam(value = "page", defaultValue = "1") int pageNum,
                                    @RequestParam(value = "size", defaultValue = "10") int pageSize) {
        log.debug("查询接口测试代码列表 apiId={} page={}", apiId, pageNum);
        LambdaQueryWrapper<TestCode> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TestCode::getApiId, apiId).isNull(TestCode::getDeletedAt)
                .orderByDesc(TestCode::getCreatedAt);
        Page<TestCode> page = testCodeMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        Map<String, Object> result = new HashMap<>();
        result.put("records", page.getRecords());
        result.put("total", page.getTotal());
        result.put("page", page.getCurrent());
        result.put("size", page.getSize());
        return result;
    }

    /** 为当前接口下所有用例批量生成测试代码（先软删原代码再插入） */
    @PostMapping
    public Map<String, Object> generate(@PathVariable("apiId") Long apiId) {
        log.info("批量生成测试代码 apiId={}", apiId);
        List<TestCode> records = testCodeGenerateService.generateForApi(apiId);
        Map<String, Object> result = new HashMap<>();
        result.put("records", records);
        result.put("count", records.size());
        return result;
    }

    /** 为单条用例生成一条测试代码（先软删该用例原有代码再插入）。先置为生成中防重复点击，异步执行后置为已生成/失败。 */
    @PostMapping("/generate-case/{testCaseId}")
    public Map<String, Object> generateForCase(@PathVariable("apiId") Long apiId,
                                               @PathVariable("testCaseId") Long testCaseId) {
        log.info("为单用例生成测试代码 apiId={} testCaseId={}", apiId, testCaseId);
        TestCase tc = testCaseMapper.selectById(testCaseId);
        if (tc == null || !apiId.equals(tc.getApiId()) || tc.getDeletedAt() != null) {
            throw new IllegalArgumentException("测试用例不存在或不属于当前接口");
        }
        if ("generating".equals(tc.getCodeGenStatus())) {
            throw new IllegalArgumentException("正在生成中，请勿重复点击");
        }
        tc.setCodeGenStatus("generating");
        tc.setUpdatedAt(LocalDateTime.now());
        testCaseMapper.updateById(tc);

        CompletableFuture.runAsync(() -> {
            try {
                testCodeGenerateService.generateForTestCase(apiId, testCaseId);
            } catch (Exception e) {
                log.warn("为用例生成测试代码失败 testCaseId={}: {}", testCaseId, e.getMessage());
                TestCase failed = testCaseMapper.selectById(testCaseId);
                if (failed != null) {
                    failed.setCodeGenStatus("failed");
                    failed.setUpdatedAt(LocalDateTime.now());
                    testCaseMapper.updateById(failed);
                }
            }
        });

        Map<String, Object> result = new HashMap<>();
        result.put("status", "generating");
        result.put("message", "已开始生成，请刷新列表查看进度");
        return result;
    }

    @PostMapping("/{id}/save")
    public void saveToFile(@PathVariable("apiId") Long apiId,
                           @PathVariable("id") Long id) throws Exception {
        TestCode code = testCodeMapper.selectById(id);
        if (code == null || !apiId.equals(code.getApiId()) || code.getDeletedAt() != null) {
            throw new IllegalArgumentException("测试代码不存在或不属于当前接口");
        }
        String baseDir = settingsService.get("code.base-dir");
        String basePackage = settingsService.get("code.base-package");
        if (baseDir == null || baseDir.isBlank() || basePackage == null || basePackage.isBlank()) {
            throw new IllegalStateException("未配置测试代码输出目录或包名，请先在系统配置中设置");
        }
        String className = code.getClassName();
        if (className == null || className.isBlank()) {
            className = "ApiTest_" + apiId;
        }
        String packagePath = basePackage.replace('.', '/');
        Path dir = Paths.get(baseDir, "src", "test", "java", packagePath);
        Files.createDirectories(dir);
        Path filePath = dir.resolve(className + ".java");
        Files.writeString(filePath, code.getCodeContent(), StandardCharsets.UTF_8);

        code.setStatus("saved");
        testCodeMapper.updateById(code);
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<byte[]> download(@PathVariable("apiId") Long apiId,
                                           @PathVariable("id") Long id) {
        TestCode code = testCodeMapper.selectById(id);
        if (code == null || !apiId.equals(code.getApiId()) || code.getDeletedAt() != null) {
            throw new IllegalArgumentException("测试代码不存在或不属于当前接口");
        }
        String className = code.getClassName() != null ? code.getClassName() : "ApiTest_" + apiId;
        String content = code.getCodeContent() != null ? code.getCodeContent() : "";
        byte[] bytes = content.getBytes(StandardCharsets.UTF_8);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_PLAIN);
        headers.set(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"" + className + ".java\"");

        return ResponseEntity.ok()
                .headers(headers)
                .body(bytes);
    }
}

