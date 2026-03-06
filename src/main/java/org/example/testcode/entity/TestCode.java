package org.example.testcode.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("t_test_code")
public class TestCode {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long projectId;

    private Long apiId;

    /** 单条关联用例 ID（一条用例一条测试代码） */
    private Long testCaseId;

    /** 兼容：关联用例 ID 列表（逗号分隔） */
    private String testCaseIds;

    private String language;

    private String framework;

    private String className;

    /**
     * 生成的测试代码全文
     */
    private String codeContent;

    /**
     * 状态：generated / saved / deprecated 等
     */
    private String status;

    /** 软删除时间，非空表示已删除 */
    private LocalDateTime deletedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}

