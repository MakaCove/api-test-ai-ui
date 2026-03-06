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

    /** 接口启用状态：active / disabled */
    private String status;

    /** 用例生成状态：pending=未生成 generating=生成中 done=已生成 failed=失败，用于控制「AI生成用例」是否可点击 */
    private String caseGenStatus;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}

