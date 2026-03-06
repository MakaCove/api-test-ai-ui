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
     * 状态：pending / analyzing / done / failed
     */
    private String status;

    /**
     * 失败原因（如果有）
     */
    private String errorMessage;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}

