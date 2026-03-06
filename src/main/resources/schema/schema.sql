-- ============================================================
-- API Test AI 平台 - 完整数据库建表脚本 (MySQL 5.7+ / 8.0+)
-- 字符集: utf8mb4，排序: utf8mb4_unicode_ci
-- ============================================================

-- 创建数据库（按需使用，若已存在可注释）
CREATE DATABASE IF NOT EXISTS api_test_ai_ui
   DEFAULT CHARACTER SET utf8mb4
   DEFAULT COLLATE utf8mb4_unicode_ci;
USE api_test_ai_ui;

-- ------------------------------------------------------------
-- 1. 用户表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_user;
CREATE TABLE t_user (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    username    VARCHAR(64)  NOT NULL COMMENT '登录名',
    password    VARCHAR(255) NOT NULL COMMENT '密码（加密存储）',
    role        VARCHAR(32)  DEFAULT 'USER' COMMENT '角色：ADMIN / USER',
    enabled     TINYINT(1)   DEFAULT 1 COMMENT '是否启用：0否 1是',
    created_at  DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

-- ------------------------------------------------------------
-- 2. 项目表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_project;
CREATE TABLE t_project (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    name        VARCHAR(128) NOT NULL COMMENT '项目名称',
    description VARCHAR(512) DEFAULT NULL COMMENT '项目描述',
    deleted     INT          DEFAULT 0 COMMENT '逻辑删除：0未删除 1已删除',
    created_at  DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目表';

-- ------------------------------------------------------------
-- 3. 文档表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_document;
CREATE TABLE t_document (
    id                   BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    project_id            BIGINT       NOT NULL COMMENT '所属项目ID',
    title                 VARCHAR(256) DEFAULT NULL COMMENT '文档标题',
    source_type           VARCHAR(32)  DEFAULT 'text' COMMENT '来源：file / text',
    document_type         VARCHAR(32)  DEFAULT 'markdown' COMMENT '类型：markdown / word / text 等',
    original_content      LONGTEXT     DEFAULT NULL COMMENT '原始文档内容',
    standardized_content  LONGTEXT     DEFAULT NULL COMMENT '标准化后的文档内容',
    status                VARCHAR(32)  DEFAULT 'pending' COMMENT '状态：pending=待处理 standardized=已标准化 extracting=AI提取中 done=接口提取完成 failed=失败',
    error_message         VARCHAR(1024) DEFAULT NULL COMMENT '失败原因',
    created_at            DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at            DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_project_id (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文档表';

-- ------------------------------------------------------------
-- 4. 接口信息表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_api_info;
CREATE TABLE t_api_info (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    project_id      BIGINT       NOT NULL COMMENT '所属项目ID',
    document_id     BIGINT       DEFAULT NULL COMMENT '来源文档ID',
    api_name        VARCHAR(128) DEFAULT NULL COMMENT '接口名称',
    api_path        VARCHAR(512) DEFAULT NULL COMMENT '接口路径',
    http_method     VARCHAR(16)  DEFAULT 'GET' COMMENT 'HTTP 方法',
    description     VARCHAR(1024) DEFAULT NULL COMMENT '描述',
    tags            VARCHAR(256) DEFAULT NULL COMMENT '标签，逗号分隔',
    request_params  TEXT         DEFAULT NULL COMMENT '请求参数结构（JSON）',
    response_schema TEXT         DEFAULT NULL COMMENT '响应结构（JSON）',
    status          VARCHAR(32)  DEFAULT 'active' COMMENT '状态：active / disabled',
    case_gen_status VARCHAR(32)  DEFAULT 'pending' COMMENT '用例生成状态：pending=未生成 generating=生成中 done=已生成 failed=失败',
    created_at      DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at      DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_project_id (project_id),
    KEY idx_document_id (document_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='接口信息表';

-- 已有库升级：ALTER TABLE t_api_info ADD COLUMN case_gen_status VARCHAR(32) DEFAULT 'pending' COMMENT '用例生成状态：pending=未生成 generating=生成中 done=已生成 failed=失败';

-- ------------------------------------------------------------
-- 5. 测试用例表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_test_case;
CREATE TABLE t_test_case (
    id                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    project_id         BIGINT       NOT NULL COMMENT '所属项目ID',
    api_id             BIGINT       NOT NULL COMMENT '所属接口ID',
    case_name          VARCHAR(128) DEFAULT NULL COMMENT '用例名称',
    case_type          VARCHAR(32)  DEFAULT 'normal' COMMENT '类型：normal / error / boundary',
    description        VARCHAR(512) DEFAULT NULL COMMENT '描述',
    request_data       TEXT         DEFAULT NULL COMMENT '请求数据（JSON）',
    expected_response  TEXT         DEFAULT NULL COMMENT '预期响应（JSON）',
    validation_rules   TEXT         DEFAULT NULL COMMENT '校验规则（JSON）',
    priority           INT          DEFAULT 3 COMMENT '优先级：1-4',
    status             VARCHAR(32)  DEFAULT 'active' COMMENT '状态',
    code_gen_status    VARCHAR(32)  DEFAULT 'pending' COMMENT '代码生成状态：pending=未生成 generating=生成中 done=已生成 failed=失败',
    deleted_at         DATETIME     DEFAULT NULL COMMENT '软删除时间，NULL 表示未删除',
    created_at         DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at         DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_project_id (project_id),
    KEY idx_api_id (api_id),
    KEY idx_api_deleted (api_id, deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='测试用例表';

-- 已有库升级：ALTER TABLE t_test_case ADD COLUMN code_gen_status VARCHAR(32) DEFAULT 'pending' COMMENT '代码生成状态：pending=未生成 generating=生成中 done=已生成 failed=失败';

-- ------------------------------------------------------------
-- 6. 测试代码表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_test_code;
CREATE TABLE t_test_code (
    id             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    project_id     BIGINT       NOT NULL COMMENT '所属项目ID',
    api_id         BIGINT       NOT NULL COMMENT '所属接口ID',
    test_case_id   BIGINT       DEFAULT NULL COMMENT '单条关联用例ID（一条用例一条代码）',
    test_case_ids  VARCHAR(512) DEFAULT NULL COMMENT '兼容：关联用例ID，逗号分隔',
    language       VARCHAR(32)  DEFAULT 'java' COMMENT '语言',
    framework      VARCHAR(64)  DEFAULT 'junit5' COMMENT '框架：junit5 等',
    class_name     VARCHAR(128) DEFAULT NULL COMMENT '生成类名',
    code_content   LONGTEXT     DEFAULT NULL COMMENT '生成的测试代码全文',
    status         VARCHAR(32)  DEFAULT 'generated' COMMENT '状态：generated / saved / deprecated',
    deleted_at     DATETIME     DEFAULT NULL COMMENT '软删除时间，NULL 表示未删除',
    created_at     DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at     DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    KEY idx_project_id (project_id),
    KEY idx_api_id (api_id),
    KEY idx_test_case_id (test_case_id),
    KEY idx_api_deleted (api_id, deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='测试代码表';

-- ------------------------------------------------------------
-- 7. 系统配置表
-- ------------------------------------------------------------
DROP TABLE IF EXISTS t_system_setting;
CREATE TABLE t_system_setting (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    config_key  VARCHAR(128) NOT NULL COMMENT '配置键',
    config_value TEXT        DEFAULT NULL COMMENT '配置值',
    created_at  DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_config_key (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统配置表';
