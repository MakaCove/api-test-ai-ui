package org.example.document.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.common.ai.AiClientService;
import org.example.api.entity.ApiInfo;
import org.example.api.mapper.ApiInfoMapper;
import org.example.document.entity.Document;
import org.example.document.mapper.DocumentMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * 文档分析服务：① 保存后自动生成标准化内容（AI 整理原文）；② 点击「AI提取接口信息」时解析标准化文档为接口列表写入 t_api_info。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentAnalyzeService {

    private final DocumentMapper documentMapper;
    private final ApiInfoMapper apiInfoMapper;
    private final AiClientService aiClientService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 上传/粘贴保存后自动调用：用 AI 将原始文档整理成标准化文档，写入 standardizedContent，状态改为 standardized。
     */
    @Transactional
    public void generateStandardizedContent(Long projectId, Long documentId) {
        log.info("生成标准化文档 projectId={} documentId={}", projectId, documentId);
        Document doc = documentMapper.selectById(documentId);
        if (doc == null || !projectId.equals(doc.getProjectId())) {
            throw new IllegalArgumentException("文档不存在或不属于当前项目");
        }
        String raw = doc.getOriginalContent();
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("文档内容为空，无法生成标准化文档");
        }
        String systemPrompt = """
                你是一个接口文档整理助手。请将用户提供的接口文档整理成标准化、结构清晰的格式。
                要求：保留所有接口信息（路径、方法、参数、说明等），使用统一的 Markdown 或纯文本结构，
                便于人类阅读和后续程序解析。只输出整理后的文档正文，不要输出 JSON，不要加多余解释。
                """;
        String userPrompt = "请将以下接口文档整理成标准化格式：\n\n" + raw;
        String result = aiClientService.analyzeDocumentToApis(systemPrompt, userPrompt);
        if (result != null && !result.isBlank()) {
            doc.setStandardizedContent(result.trim());
            doc.setStatus("standardized");
            doc.setErrorMessage(null);
        } else {
            doc.setStatus("failed");
            doc.setErrorMessage("AI 未返回标准化内容");
        }
        doc.setUpdatedAt(LocalDateTime.now());
        documentMapper.updateById(doc);
        log.info("文档 {} 已生成标准化内容，状态: {}", documentId, doc.getStatus());
    }

    /**
     * 点击「分析」时：基于标准化文档（若有）用 AI 提取接口信息并写入 t_api_info。
     */
    @Transactional
    public List<ApiInfo> analyzeAndGenerateApis(Long projectId, Long documentId) {
        log.info("AI 提取接口信息 projectId={} documentId={}", projectId, documentId);
        Document doc = documentMapper.selectById(documentId);
        if (doc == null || !projectId.equals(doc.getProjectId())) {
            throw new IllegalArgumentException("文档不存在或不属于当前项目");
        }

        // 优先使用标准化文档，若无则使用原始内容
        String content = (doc.getStandardizedContent() != null && !doc.getStandardizedContent().isBlank())
                ? doc.getStandardizedContent()
                : doc.getOriginalContent();
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("文档内容为空，请先完成标准化或填写原始内容");
        }

        // 构造提示词（系统 + 用户），要求模型输出标准 JSON
        String systemPrompt = """
                你是一个接口文档分析助手。请从提供的接口文档中识别所有 HTTP 接口，
                按照固定 JSON 结构输出，不要输出多余解释或 Markdown，仅输出 JSON。

                JSON 顶层结构示例：
                {
                  "apis": [
                    {
                      "apiName": "获取用户列表",
                      "apiPath": "/api/user/list",
                      "httpMethod": "GET",
                      "description": "返回用户列表",
                      "tags": ["user", "list"],
                      "requestParams": { ... 任意嵌套 JSON ... },
                      "responseSchema": { ... 任意嵌套 JSON ... }
                    }
                  ]
                }
                """;

        String userPrompt = "请根据以下文档内容提取接口信息并按上述 JSON 结构返回：\n\n" + content;

        String aiResult = aiClientService.analyzeDocumentToApis(systemPrompt, userPrompt);

        // 简单清理 markdown 代码块标记
        String clean = cleanMarkdown(aiResult);

        List<ApiInfo> apiInfos = parseAndSaveApis(projectId, documentId, clean);

        // 生成标准化文档（Markdown），便于预览与导出
        String standardized = buildStandardizedMarkdown(doc.getTitle(), apiInfos);
        doc.setStandardizedContent(standardized);
        doc.setStatus("done");
        doc.setUpdatedAt(LocalDateTime.now());
        documentMapper.updateById(doc);

        return apiInfos;
    }

    private String cleanMarkdown(String content) {
        if (content == null) return "";
        String trimmed = content.trim();
        if (trimmed.startsWith("```json")) {
            trimmed = trimmed.substring(7).trim();
        } else if (trimmed.startsWith("```")) {
            trimmed = trimmed.substring(3).trim();
        }
        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3).trim();
        }
        return trimmed;
    }

    private List<ApiInfo> parseAndSaveApis(Long projectId, Long documentId, String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            JsonNode apisNode = root.get("apis");
            if (apisNode == null || !apisNode.isArray()) {
                throw new IllegalArgumentException("AI 返回结果中没有 apis 数组");
            }

            // 可选：先删除该文档下旧的接口信息
            LambdaQueryWrapper<ApiInfo> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(ApiInfo::getProjectId, projectId)
                    .eq(ApiInfo::getDocumentId, documentId);
            apiInfoMapper.delete(wrapper);

            List<ApiInfo> result = new ArrayList<>();
            Iterator<JsonNode> it = apisNode.elements();
            while (it.hasNext()) {
                JsonNode node = it.next();
                ApiInfo api = new ApiInfo();
                api.setProjectId(projectId);
                api.setDocumentId(documentId);
                api.setApiName(textOrNull(node, "apiName"));
                api.setApiPath(textOrNull(node, "apiPath"));
                api.setHttpMethod(textOrNull(node, "httpMethod"));
                api.setDescription(textOrNull(node, "description"));

                if (node.has("tags") && node.get("tags").isArray()) {
                    List<String> tags = new ArrayList<>();
                    node.get("tags").forEach(t -> tags.add(t.asText()));
                    api.setTags(String.join(",", tags));
                }
                if (node.has("requestParams")) {
                    api.setRequestParams(node.get("requestParams").toString());
                }
                if (node.has("responseSchema")) {
                    api.setResponseSchema(node.get("responseSchema").toString());
                }

                api.setStatus("active");
                LocalDateTime now = LocalDateTime.now();
                api.setCreatedAt(now);
                api.setUpdatedAt(now);
                apiInfoMapper.insert(api);
                result.add(api);
            }
            log.info("文档 {} 分析完成，共生成 {} 个接口", documentId, result.size());
            return result;
        } catch (Exception e) {
            log.error("解析 AI 返回的接口 JSON 失败: {}", e.getMessage(), e);
            throw new RuntimeException("解析 AI 返回内容失败: " + e.getMessage(), e);
        }
    }

    private String textOrNull(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v != null && !v.isNull() ? v.asText() : null;
    }

    /**
     * 根据分析得到的接口列表生成标准化 Markdown 文档，用于预览与导出。
     */
    private String buildStandardizedMarkdown(String docTitle, List<ApiInfo> apis) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ").append(docTitle != null && !docTitle.isBlank() ? docTitle : "接口文档").append("\n\n");
        sb.append("本文档由系统根据原始文档自动分析生成，共 ").append(apis.size()).append(" 个接口。\n\n");
        sb.append("---\n\n");
        for (int i = 0; i < apis.size(); i++) {
            ApiInfo api = apis.get(i);
            sb.append("## ").append(i + 1).append(". ").append(api.getApiName() != null ? api.getApiName() : "未命名接口").append("\n\n");
            sb.append("- **路径**: `").append(api.getHttpMethod() != null ? api.getHttpMethod() : "").append(" ").append(api.getApiPath() != null ? api.getApiPath() : "").append("`\n");
            if (api.getDescription() != null && !api.getDescription().isBlank()) {
                sb.append("- **描述**: ").append(api.getDescription()).append("\n");
            }
            if (api.getTags() != null && !api.getTags().isBlank()) {
                sb.append("- **标签**: ").append(api.getTags()).append("\n");
            }
            if (api.getRequestParams() != null && !api.getRequestParams().isBlank()) {
                sb.append("- **请求参数**: 见下方 JSON\n");
                sb.append("```json\n").append(api.getRequestParams()).append("\n```\n");
            }
            if (api.getResponseSchema() != null && !api.getResponseSchema().isBlank()) {
                sb.append("- **响应结构**: 见下方 JSON\n");
                sb.append("```json\n").append(api.getResponseSchema()).append("\n```\n");
            }
            sb.append("\n");
        }
        return sb.toString();
    }
}

