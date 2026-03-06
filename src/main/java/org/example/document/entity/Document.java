package org.example.document.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_document")
public class Document {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;

    /**
     * 文档标题（展示用），可来自文件名或自定义
     */
    private String title;

    /**
     * 来源类型：file / text
     */
    private String sourceType;

    /**
     * 文档类型：markdown / word / text / swagger / openapi 等
     */
    private String documentType;

    /**
     * 原始文档内容
     */
    private String originalContent;

    /**
     * 标准化后的文档内容（可选）
     */
    private String standardizedContent;

    /**
     * 文档状态（存英文，前端展示为中文）：
     * pending=待处理，standardized=已标准化，extracting=AI提取中（点击提取后立即设置，禁止重复点击），done=接口提取完成，failed=失败
     */
    private String status;

    /**
     * 失败原因（如果有）
     */
    private String errorMessage;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}

