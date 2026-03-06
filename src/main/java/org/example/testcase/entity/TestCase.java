package org.example.testcase.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_test_case")
public class TestCase {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;

    private Long apiId;

    private String caseName;

    /**
     * 用例类型：normal / error / boundary 等
     */
    private String caseType;

    private String description;

    /**
     * 请求数据（JSON 字符串）
     */
    private String requestData;

    /**
     * 预期响应（JSON 字符串）
     */
    private String expectedResponse;

    /**
     * 校验规则（JSON 字符串）
     */
    private String validationRules;

    private Integer priority;

    private String status;

    /** 软删除时间，非空表示已删除 */
    private LocalDateTime deletedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}

