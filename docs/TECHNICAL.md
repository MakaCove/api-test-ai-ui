# API 自动化测试 AI 平台 — 技术文档

本文档面向开发与运维，说明技术选型、架构、接口、数据库与部署等实现细节。

---

## 一、技术栈与版本

| 类别 | 技术 | 版本/说明 |
|------|------|-----------|
| 语言 | Java | 17 |
| 基础框架 | Spring Boot | 3.2.x |
| Web | Spring Web MVC | 随 Spring Boot |
| 安全 | Spring Security | 表单登录、BCrypt |
| 校验 | Spring Validation | 参数校验 |
| 模板引擎 | Thymeleaf | 服务端渲染 |
| 时间扩展 | thymeleaf-extras-java8time | 3.0.4（#temporals 等） |
| ORM | MyBatis-Plus | 3.5.15（spring-boot3-starter） |
| 数据库 | MySQL | 5.7+ / 8.0+，驱动 mysql-connector-j |
| 连接池 | HikariCP | Spring Boot 默认 |
| AI 客户端 | openai-java | 2.12.0（兼容 OpenAI/通义千问等） |
| 文档解析 | Apache POI | 5.2.5（poi-ooxml、poi-scratchpad） |
| 工具 | Lombok | 简化实体与构造 |
| 日志 | Logback | Spring Boot 默认，可配 logback-spring.xml |

---

## 二、项目结构

### 2.1 后端包结构（Java）

```
org.example
├── ApiTestAiUiApplication.java     # 启动类，@EnableConfigurationProperties(AiConfig.class)
├── config/                         # 配置
│   ├── AiConfig.java               # ai.* 配置属性绑定
│   ├── JacksonConfig.java          # LocalDateTime 序列化 yyyy-MM-dd HH:mm:ss
│   ├── LayoutModelAdvice.java     # 全局注入 contextPath 供 Thymeleaf 使用
│   ├── SecurityConfig.java        # Spring Security 表单登录、放行路径、PasswordEncoder
│   └── ThymeleafConfig.java       # 注册 Java8TimeDialect
├── common/
│   ├── constant/AiPrompt.java     # AI 提示词常量（文档标准化、接口提取、用例生成、代码生成）
│   └── ai/AiClientService.java    # 封装 AI 调用（endpoint、apiKey、model 可配置）
├── security/
│   ├── CustomUserDetails.java
│   ├── CustomUserDetailsService.java   # 按 username 查 User 转 UserDetails
│   └── controller/AuthController.java  # /login、/register 页面与注册提交
├── web/controller/HomeController.java  # /、/apis、/testcases、/testcases/detail/{id}、/testcases/{caseId}/code、/testcodes
├── project/
│   ├── controller/
│   │   ├── ProjectController.java       # 页面：/projects、/projects/{id}、documents、apis
│   │   └── ProjectApiController.java    # REST：GET/POST /api/projects
│   ├── entity/Project.java
│   └── mapper/ProjectMapper.java
├── document/
│   ├── controller/DocumentApiController.java  # REST：/api/projects/{id}/documents
│   ├── service/DocumentAnalyzeService.java    # 标准化 + 解析接口
│   ├── entity/Document.java
│   └── mapper/DocumentMapper.java
├── api/
│   ├── controller/
│   │   ├── ApiPageController.java      # 页面：/projects/{pid}/apis/{apiId}/testcases
│   │   └── ApiInfoApiController.java   # REST：/api/projects/{pid}/apis
│   ├── entity/ApiInfo.java
│   └── mapper/ApiInfoMapper.java
├── testcase/
│   ├── controller/TestCaseApiController.java  # REST：/api/apis/{apiId}/testcases
│   ├── service/TestCaseGenerateService.java   # AI 生成用例
│   ├── entity/TestCase.java
│   └── mapper/TestCaseMapper.java
├── testcode/
│   ├── controller/
│   │   ├── TestCodePageController.java  # 页面：/projects/{pid}/apis/{apiId}/testcodes
│   │   └── TestCodeApiController.java   # REST：/api/apis/{apiId}/testcodes
│   ├── service/TestCodeGenerateService.java   # AI 生成代码
│   ├── entity/TestCode.java
│   └── mapper/TestCodeMapper.java
├── settings/
│   ├── controller/
│   │   ├── SettingsPageController.java  # 页面：/settings
│   │   └── SettingsApiController.java  # REST：/api/settings GET/PUT
│   ├── service/SettingsService.java     # 读写 t_system_setting
│   ├── entity/SystemSetting.java
│   └── mapper/SystemSettingMapper.java
└── user/
    ├── entity/User.java
    └── mapper/UserMapper.java
```

### 2.2 前端与静态资源

```
src/main/resources/
├── templates/
│   ├── layout.html                 # 公共侧栏片段 sidebar(activeKey)
│   ├── auth/
│   │   ├── login.html
│   │   └── register.html
│   ├── dashboard/
│   │   └── index.html              # 工作台
│   ├── project/
│   │   ├── project-list.html
│   │   ├── project-detail.html
│   │   ├── document-list.html
│   │   └── api-list.html
│   ├── api/
│   │   └── apis.html               # 全局接口列表
│   ├── testcase/
│   │   ├── testcase-list.html      # 某接口下用例列表
│   │   ├── testcase-detail.html    # 单条用例详情
│   │   └── testcases.html         # 全局用例列表
│   ├── testcode/
│   │   ├── testcode-list.html      # 某接口下测试代码列表
│   │   ├── testcode-case-detail.html  # 单条用例的代码详情
│   │   └── testcodes.html         # 全局测试代码列表
│   └── settings/
│       └── settings.html
├── static/
│   └── css/
│       └── layout.css              # 全局样式（侧栏、卡片、按钮、分页等）
├── schema/
│   ├── schema.sql                  # 全量建表
│   └── migration_soft_delete.sql   # 软删除等迁移（若有）
├── application.yml
└── logback-spring.xml              # 日志格式与滚动（可选）
```

- 页面通过 `th:replace="~{layout :: sidebar(activeKey)}"` 引入统一侧栏。
- 列表与表单通过原生 JavaScript 调用 `/api/*` 获取数据、分页、提交；无单独前端工程。

---

## 三、配置说明

### 3.1 application.yml 主要项

| 配置项 | 说明 | 示例 |
|--------|------|------|
| server.port | 服务端口 | 8080 |
| spring.datasource.url | 数据库连接串 | jdbc:mysql://localhost:3306/api_test_ai_ui?useUnicode=true&characterEncoding=utf8&... |
| spring.datasource.username / password | 数据库账号密码 | - |
| spring.thymeleaf.cache | 模板缓存，开发可关 | false |
| logging.file.name | 日志文件路径（相对项目根） | logs/application.log |
| logging.level.root / org.example | 日志级别 | INFO / DEBUG |
| mybatis-plus.global-config.db-config.id-type | 主键策略 | auto |
| ai.endpoint | AI 接口地址 | https://dashscope.aliyuncs.com/... 或 OpenAI 兼容地址 |
| ai.api-key | API Key | - |
| ai.model | 模型名 | qwen-plus、gpt-4 等 |
| ai.temperature | 温度 | 0.3 |
| ai.max-tokens | 最大 token | 4096 |

### 3.2 系统配置表（覆盖 yml）

- 表 `t_system_setting` 存 key-value。
- 系统配置页写入的 AI 与代码输出配置会持久化到此表；运行时优先读表，缺失时用 yml 中 `ai.*` 等默认值。
- 常用 key 示例：`ai.endpoint`、`ai.api-key`、`ai.model`、`code.base-dir`、`code.base-package` 等（以实际代码为准）。

### 3.3 安全与放行路径

- 登录页、注册页、静态资源（/css/** 等）、错误页等放行；其余需认证。
- 表单登录：`/login`（GET 展示页，POST 由 Spring Security 处理）；登出：`/logout`。
- 密码使用 BCrypt 加密存储。

---

## 四、REST API 一览

除特别说明外，均为 JSON；列表类统一支持 `page`、`size`（默认 10），返回结构含 `records`、`total`、`page`、`size`。

### 4.1 认证与用户

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /login | 登录页 |
| GET | /register | 注册页 |
| POST | /register | 注册提交（username、password 等） |
| POST | /logout | 登出（Spring Security） |

### 4.2 项目

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/projects | 分页列表，按创建时间倒序 |
| POST | /api/projects | 新建项目（body: name、description） |

### 4.3 文档（项目下）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/projects/{projectId}/documents | 分页列表 |
| POST | /api/projects/{projectId}/documents/text | 粘贴保存（body: title、content） |
| POST | /api/projects/{projectId}/documents/upload | 上传文件（file、可选 title） |
| POST | /api/projects/{projectId}/documents/{documentId}/analyze | AI 提取接口信息 |
| PUT | /api/projects/{projectId}/documents/{documentId} | 重命名（body: title） |
| DELETE | /api/projects/{projectId}/documents/{documentId} | 删除文档 |

### 4.4 接口（项目下）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/projects/{projectId}/apis | 分页列表（含用例数等） |
| POST | /api/projects/{projectId}/apis | 新建接口 |
| GET | /api/projects/{projectId}/apis/{id} | 接口详情 |
| PUT | /api/projects/{projectId}/apis/{id} | 更新接口（弹窗编辑） |
| POST | /api/projects/{projectId}/apis/{id}/generate-cases | AI 生成用例（先软删该接口下原用例） |
| PATCH | /api/projects/{projectId}/apis/{id}/disable | 禁用接口 |
| PATCH | /api/projects/{projectId}/apis/{id}/enable | 启用接口 |
| DELETE | /api/projects/{projectId}/apis/{id} | 删除接口 |

### 4.5 测试用例（接口下）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/apis/{apiId}/testcases | 分页列表（过滤 deleted_at） |
| POST | /api/apis/{apiId}/testcases | 新建用例 |
| GET | /api/apis/{apiId}/testcases/{id} | 用例详情 |
| PUT | /api/apis/{apiId}/testcases/{id} | 更新用例 |
| PATCH | /api/apis/{apiId}/testcases/{id}/disable | 禁用用例 |
| PATCH | /api/apis/{apiId}/testcases/{id}/enable | 启用用例 |
| DELETE | /api/apis/{apiId}/testcases/{id} | 软删用例（设置 deleted_at） |

### 4.6 测试代码（接口下）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/apis/{apiId}/testcodes | 分页列表（过滤 deleted_at） |
| POST | /api/apis/{apiId}/testcodes | 批量生成该接口下所有用例的测试代码（先软删再插入） |
| POST | /api/apis/{apiId}/testcodes/generate-case/{testCaseId} | 为单条用例生成一条测试代码（先软删该用例原代码） |
| POST | /api/apis/{apiId}/testcodes/{id}/save | 保存到本地工程（使用配置的 base-dir、base-package） |
| GET | /api/apis/{apiId}/testcodes/{id}/download | 下载 .java 文件 |

### 4.7 系统配置

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/settings | 获取当前配置（AI + 代码输出等） |
| PUT | /api/settings | 保存配置（body：各 key-value） |

---

## 五、页面路由（GET）

| 路径 | 说明 |
|------|------|
| / | 工作台 |
| /projects | 项目列表 |
| /projects/{projectId} | 项目详情 |
| /projects/{projectId}/documents | 文档管理 |
| /projects/{projectId}/apis | 接口管理 |
| /projects/{projectId}/apis/{apiId}/testcases | 某接口的用例列表（from 参数可带 apis/testcodes 等控制面包屑） |
| /projects/{projectId}/apis/{apiId}/testcodes | 某接口的测试代码列表 |
| /apis | 全局接口列表 |
| /testcases | 全局用例列表 |
| /testcases/detail/{id} | 单条用例详情 |
| /testcases/{caseId}/code | 单条用例的代码详情（可在此页生成） |
| /testcodes | 全局测试代码列表 |
| /settings | 系统配置 |

---

## 六、核心服务与数据流

### 6.1 文档 → 接口

- **DocumentAnalyzeService**
  - `generateStandardizedContent(projectId, documentId)`：根据文档原文调 AI 生成标准化内容，写回 `t_document.standardized_content`。
  - `analyzeAndGenerateApis(projectId, documentId)`：读取标准化内容，调 AI 解析为接口列表 JSON，写入 `t_api_info`（关联 project_id、document_id）。
- 文档保存（粘贴/上传）后自动调用标准化；「AI提取接口信息」由用户点击触发，调用解析并写库。

### 6.2 接口 → 用例

- **TestCaseGenerateService.generateForApi(projectId, apiId)**
  - 在事务外调 AI 生成用例 JSON 数组。
  - 在短事务内：软删该接口下原有用例（更新 deleted_at），再逐条插入新用例。
- 避免长事务持锁导致锁等待超时。

### 6.3 用例 → 测试代码

- **TestCodeGenerateService**
  - `generateForApi(apiId)`：对该接口下所有用例逐条调 AI 生成代码，短事务内软删该接口下原有代码再插入新记录。
  - `generateForTestCase(apiId, testCaseId)`：对单条用例生成一条代码，短事务内软删该用例原有代码再插入。
- 一条用例对应一条 `t_test_code` 记录（test_case_id 关联）。
- 保存到工程：读取 `t_system_setting` 中 `code.base-dir`、`code.base-package` 写文件。

### 6.4 AI 调用

- **AiClientService**：统一封装请求（endpoint、apiKey、model、temperature、maxTokens）；可从 AiConfig（yml）或 SettingsService（表）取配置。
- **AiPrompt**：集中存放文档标准化、接口提取、用例生成、代码生成的 prompt 常量，便于维护与调优。
- AI 返回的 JSON 会做清洗（如补逗号、去 markdown 代码块），解析失败时有日志与友好提示。

---

## 七、数据库设计概要

### 7.1 表清单

| 表名 | 说明 |
|------|------|
| t_user | 用户（username 唯一，password 加密） |
| t_project | 项目（deleted 逻辑删除） |
| t_document | 文档（project_id、original_content、standardized_content、status） |
| t_api_info | 接口（project_id、document_id、request_params、response_schema、status） |
| t_test_case | 测试用例（project_id、api_id、deleted_at 软删） |
| t_test_code | 测试代码（project_id、api_id、test_case_id、deleted_at 软删） |
| t_system_setting | 系统配置（config_key、config_value） |

### 7.2 索引与约束

- 各表主键自增；外键仅逻辑关联，未建物理 FK。
- 常用查询索引：project_id、document_id、api_id、test_case_id；(api_id, deleted_at) 等组合索引以支持列表过滤。
- 建表脚本见 `src/main/resources/schema/schema.sql`；字符集 utf8mb4。

### 7.3 软删除约定

- **t_test_case**：deleted_at 非空表示已删，列表与详情查询条件 `deleted_at IS NULL`。
- **t_test_code**：同上。
- **t_project**：deleted=1 表示已删，列表过滤 deleted=0。

---

## 八、日志

- 使用 Logback；可通过 `logback-spring.xml` 配置控制台与文件输出、按日滚动、保留天数。
- 应用日志输出到 `logs/application.log`（路径可由 logging.file.name 修改）。
- 建议：关键操作（注册、登录、文档保存、AI 调用、用例/代码生成）打 INFO/DEBUG；异常与失败打 WARN/ERROR；不记录敏感信息。

---

## 九、构建与部署

### 9.1 构建

```bash
mvn clean package -DskipTests
```

产物：`target/api-test-ai-ui-1.0-SNAPSHOT.jar`（可执行 Jar）。

### 9.2 运行

```bash
java -jar target/api-test-ai-ui-1.0-SNAPSHOT.jar
```

或开发时：

```bash
mvn spring-boot:run
```

### 9.3 环境要求

- JDK 17+
- MySQL 5.7+ / 8.0+，已创建库并执行 schema.sql
- 若使用 AI 功能，需配置有效的 endpoint、api-key、model（yml 或系统配置页）

### 9.4 可选配置

- `server.servlet.context-path`：若设则所有路径前加该前缀。
- JVM 参数：如 `-Xmx512m`、`-Dspring.profiles.active=prod` 等按需设置。

---

## 十、扩展与二次开发

- **新增业务模块**：可参考 project/document/api 等，建 entity、mapper、controller（页面 + REST）、必要时 service。
- **调整 AI 行为**：改 `AiPrompt` 常量或 AiClientService 的请求构造；新增模型可扩展 AiConfig 与配置表。
- **新增配置项**：在 t_system_setting 存 key-value，SettingsService 读取；前端在系统配置页增加表单项并调用 PUT /api/settings。
- **Thymeleaf 公共片段**：除 layout 的 sidebar 外，可在 layout.html 或单独片段中增加 header、footer 等，各页 th:replace 引入。
- **列表过滤与排序**：在现有 GET 列表接口上增加请求参数（如 keyword、status），在 Mapper 的 QueryWrapper 中拼接条件即可。
