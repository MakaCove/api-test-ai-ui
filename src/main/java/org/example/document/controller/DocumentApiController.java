package org.example.document.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.hwpf.HWPFDocument;
import org.apache.poi.hwpf.extractor.WordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.example.api.entity.ApiInfo;
import org.example.document.entity.Document;
import org.example.document.mapper.DocumentMapper;
import org.example.document.service.DocumentAnalyzeService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 项目下文档 REST API：分页列表、粘贴保存、文件上传、重命名、删除、AI 分析提取接口。
 * 粘贴/上传保存后会自动触发 AI 生成标准化内容；分析接口将标准化内容解析为接口列表写入 t_api_info。
 */
@Slf4j
@RestController
@RequestMapping("/api/projects/{projectId}/documents")
@RequiredArgsConstructor
public class DocumentApiController {

    private final DocumentMapper documentMapper;
    private final DocumentAnalyzeService documentAnalyzeService;

    @GetMapping
    public Map<String, Object> list(@PathVariable("projectId") Long projectId,
                                    @RequestParam(value = "page", defaultValue = "1") int pageNum,
                                    @RequestParam(value = "size", defaultValue = "10") int pageSize) {
        log.debug("查询项目文档列表 projectId={} page={}", projectId, pageNum);
        LambdaQueryWrapper<Document> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Document::getProjectId, projectId).orderByDesc(Document::getCreatedAt);
        Page<Document> page = documentMapper.selectPage(new Page<>(pageNum, pageSize), wrapper);
        Map<String, Object> result = new HashMap<>();
        result.put("records", page.getRecords());
        result.put("total", page.getTotal());
        result.put("page", page.getCurrent());
        result.put("size", page.getSize());
        return result;
    }

    /** 粘贴文本保存文档，保存后自动调用 AI 生成标准化内容 */
    @PostMapping("/text")
    public Document createFromText(@PathVariable("projectId") Long projectId,
                                   @RequestBody CreateTextDocumentRequest request) {
        log.info("粘贴保存文档 projectId={} title={}", projectId, request != null ? request.getTitle() : null);
        Document doc = new Document();
        doc.setProjectId(projectId);
        doc.setTitle(request.getTitle());
        doc.setSourceType("text");
        doc.setDocumentType(request.getDocumentType() != null ? request.getDocumentType() : "markdown");
        doc.setOriginalContent(request.getContent());
        doc.setStatus("pending");
        LocalDateTime now = LocalDateTime.now();
        doc.setCreatedAt(now);
        doc.setUpdatedAt(now);
        documentMapper.insert(doc);
        try {
            documentAnalyzeService.generateStandardizedContent(projectId, doc.getId());
        } catch (Exception e) {
            log.warn("保存后自动生成标准化文档失败: {}", e.getMessage());
            doc.setStatus("failed");
            doc.setErrorMessage(e.getMessage() != null ? e.getMessage() : "生成标准化文档失败");
            doc.setUpdatedAt(LocalDateTime.now());
            documentMapper.updateById(doc);
        }
        return documentMapper.selectById(doc.getId());
    }

    /** 上传文件创建文档，支持 .txt .doc .docx .md；保存后自动调用 AI 生成标准化内容 */
    @PostMapping("/upload")
    public Document createFromFile(@PathVariable("projectId") Long projectId,
                                   @RequestParam("file") MultipartFile file,
                                   @RequestParam(value = "title", required = false) String title) {
        log.info("上传文档 projectId={} filename={}", projectId, file.getOriginalFilename());
        if (file.isEmpty()) {
            throw new IllegalArgumentException("请选择要上传的文件");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || filename.isBlank()) {
            filename = "未命名";
        }
        String content = readFileContent(file, filename);
        String documentType = inferDocumentType(filename);
        String docTitle = (title != null && !title.isBlank()) ? title : filename;
        Document doc = new Document();
        doc.setProjectId(projectId);
        doc.setTitle(docTitle);
        doc.setSourceType("file");
        doc.setDocumentType(documentType);
        doc.setOriginalContent(content);
        doc.setStatus("pending");
        LocalDateTime now = LocalDateTime.now();
        doc.setCreatedAt(now);
        doc.setUpdatedAt(now);
        documentMapper.insert(doc);
        try {
            documentAnalyzeService.generateStandardizedContent(projectId, doc.getId());
        } catch (Exception e) {
            log.warn("上传后自动生成标准化文档失败: {}", e.getMessage());
            doc.setStatus("failed");
            doc.setErrorMessage(e.getMessage() != null ? e.getMessage() : "生成标准化文档失败");
            doc.setUpdatedAt(LocalDateTime.now());
            documentMapper.updateById(doc);
        }
        return documentMapper.selectById(doc.getId());
    }

    private static String inferDocumentType(String filename) {
        if (filename == null) return "text";
        String lower = filename.toLowerCase();
        if (lower.endsWith(".md") || lower.endsWith(".markdown")) return "markdown";
        if (lower.endsWith(".doc") || lower.endsWith(".docx")) return "word";
        if (lower.endsWith(".txt")) return "text";
        return "text";
    }

    /**
     * 根据扩展名读取文件内容：
     * - .docx 使用 XWPFDocument
     * - .doc  使用 HWPFDocument
     * - 其他按 UTF-8 文本读取
     */
    private static String readFileContent(MultipartFile file, String filename) {
        String lower = filename != null ? filename.toLowerCase() : "";
        try {
            if (lower.endsWith(".docx")) {
                try (InputStream is = file.getInputStream(); XWPFDocument docx = new XWPFDocument(is)) {
                    StringBuilder sb = new StringBuilder();
                    docx.getParagraphs().forEach(p -> sb.append(p.getText()).append("\n"));
                    return sb.toString();
                }
            } else if (lower.endsWith(".doc")) {
                try (InputStream is = file.getInputStream(); HWPFDocument doc = new HWPFDocument(is)) {
                    try (WordExtractor extractor = new WordExtractor(doc)) {
                        return extractor.getText();
                    }
                }
            } else {
                return new String(file.getBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("无法读取文件内容，请确认文件为文本或 Word 文档（.txt/.doc/.docx/.md）");
        }
    }

    @PostMapping("/{documentId}/analyze")
    public Map<String, Object> analyze(@PathVariable("projectId") Long projectId,
                                       @PathVariable("documentId") Long documentId) {
        List<ApiInfo> apis = documentAnalyzeService.analyzeAndGenerateApis(projectId, documentId);
        Map<String, Object> result = new HashMap<>();
        result.put("apiCount", apis.size());
        return result;
    }

    @PutMapping("/{documentId}")
    public Document rename(@PathVariable("projectId") Long projectId,
                           @PathVariable("documentId") Long documentId,
                           @RequestBody Map<String, String> body) {
        Document doc = documentMapper.selectById(documentId);
        if (doc == null || !projectId.equals(doc.getProjectId())) {
            throw new IllegalArgumentException("文档不存在或不属于当前项目");
        }
        String title = body.get("title");
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("标题不能为空");
        }
        doc.setTitle(title);
        doc.setUpdatedAt(LocalDateTime.now());
        documentMapper.updateById(doc);
        return doc;
    }

    @DeleteMapping("/{documentId}")
    public void delete(@PathVariable("projectId") Long projectId,
                       @PathVariable("documentId") Long documentId) {
        Document doc = documentMapper.selectById(documentId);
        if (doc == null || !projectId.equals(doc.getProjectId())) {
            throw new IllegalArgumentException("文档不存在或不属于当前项目");
        }
        documentMapper.deleteById(documentId);
    }

    @Data
    public static class CreateTextDocumentRequest {
        private String title;
        private String content;
        private String documentType;
    }
}

