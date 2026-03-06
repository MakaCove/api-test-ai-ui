package org.example.api.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_api_info")
public class ApiInfo {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;

    private Long documentId;

    private String apiName;

    private String apiPath;

    private String httpMethod;

    private String description;

    private String tags;

    /**
     * 请求参数结构（JSON 字符串）
     */
    private String requestParams;

    /**
     * 响应结构（JSON 字符串）
     */
    private String responseSchema;

    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}

