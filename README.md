# API 自动化测试 AI 平台

基于 AI 的接口自动化测试管理平台：从接口文档或手工录入的接口信息出发，自动生成测试用例与 JUnit 5 风格的 Java 自动化测试代码，并通过统一 Web 界面完成项目管理、文档解析、用例维护与代码导出。

**形态**：单体 Spring Boot 应用，前后端不分离（Thymeleaf 服务端渲染 + 原生 JavaScript 调用 REST API）。

---

## 目录

- [功能概览](#功能概览)
- [技术栈](#技术栈)
- [环境要求与快速开始](#环境要求与快速开始)
- [配置说明](#配置说明)
- [项目结构](#项目结构)
- [核心业务流程](#核心业务流程)
- [界面与导航](#界面与导航)
- [数据库](#数据库)
- [日志与异常](#日志与异常)
- [构建与运行](#构建与运行)

---

## 功能概览

| 模块 | 功能说明 |
|------|-----------|
| **用户与认证** | 注册、登录、登出；BCrypt 密码；未登录访问业务页跳转登录 |
| **工作台** | 总数统计（项目/接口/用例/代码）、各项目明细统计、快捷入口、最近接口与用例列表 |
| **项目管理** | 新建（弹窗）/ 列表 / 详情 / 编辑 / 删除（软删）；项目下进入文档管理、接口管理；列表支持勾选、全选 |
| **文档管理** | 粘贴文档（弹窗）或上传文件（.txt / .doc / .docx / .md）；保存后异步 AI 标准化；「AI提取接口信息」异步解析为接口；状态：待处理 / 已标准化 / AI提取中 / 接口提取完成 / 失败；列表支持勾选、分页 |
| **接口管理** | 接口列表（分页、所属文档、用例状态、状态中文）；新建/修改（弹窗）、禁用/启用、删除；「AI生成用例」受用例状态控制；「用例列表」进入该接口的用例列表；列表支持勾选 |
| **全局接口列表** | 与项目下接口管理同字段与操作；详情跳转至该接口的用例列表页 |
| **用例管理（按接口）** | 用例列表（分页、代码状态）；新建/编辑/禁用/启用；单条「AI生成代码」/「重新生成代码」/「生成中」；「批量生成代码」需勾选用例后可用；列表支持勾选 |
| **全局用例列表** | 分页、详情（单条用例详情）、代码（单条用例代码详情）；支持单条/批量生成代码 |
| **测试代码管理** | 全局「用例自动化代码列表」：分页、所属接口、接口地址、关联用例名、详情（单条用例代码详情页）；某接口下测试代码列表：查看、保存到工程、下载；单条代码详情页含基本信息区与代码区，支持「AI生成代码」/「重新生成代码」 |
| **系统配置** | AI 配置（Endpoint、API Key、模型、Temperature、Max Tokens）；测试代码输出（工程根目录、默认包名）；配置持久化到库 |

- 所有列表均为**每页 10 条**，支持分页；日期统一格式 `yyyy-MM-dd HH:mm:ss`。
- 列表**操作列固定右侧**，内容区可横向滚动；支持**勾选列、全选**（部分列表用于批量生成代码）。
- 测试用例、测试代码支持**软删除**（`deleted_at`）；再次生成时先软删再插入。
- REST 错误响应统一为 `{"message": "..."}`，配合 400/404/500 等状态码；全局异常由 `@RestControllerAdvice` 处理。

---

## 技术栈

| 类别 | 技术 |
|------|------|
| 语言 | Java 17 |
| 后端框架 | Spring Boot 3.2.x、Spring Web MVC、Spring Security、Spring Validation |
| 持久层 | MyBatis-Plus（分页插件）、MySQL、HikariCP |
| 视图层 | Thymeleaf、原生 JavaScript、自定义 CSS（`layout.css`） |
| AI 接入 | openai-java SDK，对接通义千问兼容接口（可配置 Endpoint / Key / Model） |
| 文档解析 | Apache POI（.doc / .docx） |
| 生成测试框架 | JUnit 5（生成的代码风格） |
| 日志 | Logback（SLF4J），可配 logback-spring.xml |

---

## 环境要求与快速开始

### 环境要求

- JDK 17+
- Maven 3.6+
- MySQL 5.7+ / 8.0+（建议 utf8mb4）

### 1. 创建数据库并执行建表脚本

```bash
mysql -u root -p < src/main/resources/schema/schema.sql
```

或手动创建数据库后，在 MySQL 客户端中执行 `schema/schema.sql` 内容。数据库名默认为 `api_test_ai_ui`。

### 2. 修改数据源与 AI 配置

编辑 `src/main/resources/application.yml`：

- **数据源**：`spring.datasource.url`、`username`、`password` 改为你的 MySQL 连接信息。
- **AI**：`ai.endpoint`、`ai.api-key`、`ai.model` 等；也可在应用内「系统配置」页填写，会持久化到库并优先生效。

### 3. 启动应用

```bash
mvn spring-boot:run
```

或先打包再运行：

```bash
mvn clean package -DskipTests
java -jar target/api-test-ai-ui-1.0-SNAPSHOT.jar
```

浏览器访问：`http://localhost:8080`（若配置了 `server.servlet.context-path` 则需加上该路径）。

### 4. 首次使用

- 打开注册页完成用户注册，再登录。
- 在「系统配置」中填写 AI 的 Endpoint、API Key、模型名称等（若未在 yml 中配置）。
- 新建项目（弹窗）→ 进入文档管理 → 粘贴或上传接口文档 → 保存（触发异步标准化）→ 点击「AI提取接口信息」→ 进入接口管理查看并维护接口 → 在接口或接口用例列表页「AI生成用例」→ 在用例列表中勾选用例后「批量生成代码」或对单条「AI生成代码」/「重新生成代码」，最后在测试代码列表或详情页保存/下载。

---

## 配置说明

主要配置集中在 `src/main/resources/application.yml`。

| 配置项 | 说明 | 示例 |
|--------|------|------|
| `server.port` | 服务端口 | 8080 |
| `server.servlet.context-path` | 上下文路径（若有） | /samweb/v1 |
| `spring.datasource.*` | 数据源 URL、用户名、密码 | 见文件 |
| `spring.thymeleaf.cache` | 模板缓存（开发可 false） | false |
| `logging.file.name` | 日志文件路径（相对项目根） | logs/application.log |
| `logging.level.root` / `org.example` | 日志级别 | INFO / DEBUG |
| `ai.endpoint` | AI 接口地址 | https://dashscope.aliyuncs.com/... |
| `ai.api-key` | API Key | 你的 Key |
| `ai.model` | 模型名称 | qwen-plus、gpt-4 等 |
| `ai.temperature` | 温度 | 0.3 |
| `ai.max-tokens` | 最大 token | 4096 |

系统配置页中填写的 AI 与测试代码输出路径会写入表 `t_system_setting`，运行时优先生效；未配置时回退到 yml 中的 `ai.*` 默认值。

---

## 项目结构

```
src/main/java/org/example/
├── ApiTestAiUiApplication.java     # 启动类
├── config/                         # 配置类
│   ├── AiConfig.java
│   ├── GlobalExceptionHandler.java # 统一 REST 错误响应 400/500
│   ├── JacksonConfig.java
│   ├── LayoutModelAdvice.java
│   ├── MybatisPlusConfig.java      # 分页插件
│   ├── SecurityConfig.java
│   └── ThymeleafConfig.java
├── common/                         # 公共
│   ├── constant/AiPrompt.java     # AI 提示词常量
│   └── ai/AiClientService.java    # AI 调用封装
├── security/                       # 认证
├── web/controller/HomeController.java   # 工作台、全局列表页（含统计）
├── project/                       # 项目模块
├── document/                      # 文档模块
├── api/                           # 接口模块
├── testcase/                      # 用例模块
├── testcode/                      # 测试代码模块
├── settings/                      # 系统配置模块
└── user/                          # 用户模块
```

各业务模块通常包含：`controller`（页面 + REST）、`entity`、`mapper`，部分含 `service`。  
前端：`templates/` 下按功能分目录（auth、dashboard、project、api、testcase、testcode、settings）；`static/css/layout.css` 为全局样式；列表与表单通过 JS 调用 `/api/*` 获取数据与提交。

---

## 核心业务流程

1. **文档 → 接口**  
   文档保存后异步调用 AI 生成「标准化内容」；用户在文档列表点击「AI提取接口信息」，后端先将状态置为「AI提取中」，再异步解析标准化内容为接口列表并写入 `t_api_info`。

2. **接口 → 用例**  
   在接口管理或某接口的用例列表页点击「AI生成用例」；后端根据接口信息调 AI 生成用例数组，先软删该接口下原有用例，再插入新用例；接口的「用例状态」控制按钮可用性（生成中时禁用）。

3. **用例 → 测试代码**  
   - **单条生成**：在用例列表、用例详情、代码详情页点击「AI生成代码」或「重新生成代码」，仅针对当前一条用例生成一条代码；生成中按钮显示「生成中」并禁用。
   - **批量生成**：仅在「用例列表」页（全局或某接口下）勾选多条用例后点击「批量生成代码」，对勾选项逐条调用单条生成接口。
   - 生成时先软删该用例已有代码记录再插入；用例的「代码状态」与按钮文案（未生成/已生成/失败 → 生成中）全局一致。

4. **保存到工程**  
   使用系统配置中的「测试工程根目录」与「默认包名」，将生成的测试类写入对应包路径下；若配置未填则无法保存到工程。

---

## 界面与导航

- **工作台**：`/`，总数统计、各项目统计、快捷入口、最近接口与用例。
- **项目列表**：`/projects`，新建项目（弹窗）、进入项目详情；详情内进入文档管理、接口管理。
- **接口列表**：`/apis`，全局接口列表；详情跳转到该接口的用例列表页。
- **用例列表**：`/testcases`，全局用例列表；详情为单条用例详情；「代码」进入该用例的代码详情页（可在此页生成/重新生成代码）。
- **用例自动化代码列表**：`/testcodes`，全局测试代码列表；详情跳转到**单条用例的代码详情页**（`/testcases/{caseId}/code`）；列表展示接口地址、关联用例名等。
- **系统配置**：`/settings`，AI 与测试代码输出配置。

侧栏统一：工作台、项目列表、接口列表、用例列表、用例自动化代码列表、系统配置；底部为「退出登录」。

---

## 数据库

- 建表脚本：`src/main/resources/schema/schema.sql`。
- 主要表：`t_user`、`t_project`、`t_document`、`t_api_info`、`t_test_case`、`t_test_code`、`t_system_setting`。
- 文档状态：`t_document.status`（pending / standardized / extracting / done / failed）；接口用例状态：`t_api_info.case_gen_status`；用例代码状态：`t_test_case.code_gen_status`。
- 字符集建议：`utf8mb4`；若连接串使用 `characterEncoding=utf8mb4` 报错，可改为 `utf-8`。

---

## 日志与异常

- 日志同时输出到控制台与项目目录下 `logs/application.log`；按日滚动，保留 30 天；配置见 `application.yml` 与 `logback-spring.xml`；`logs/` 已加入 `.gitignore`。
- REST 异常：`GlobalExceptionHandler` 统一处理参数/校验/非法状态返回 400、未知异常返回 500，响应体均为 `{"message": "..."}`；500 时记录日志且对前端返回通用提示「服务异常，请稍后重试」。

---

## 构建与运行

```bash
mvn clean package -DskipTests
java -jar target/api-test-ai-ui-1.0-SNAPSHOT.jar
# 或
mvn spring-boot:run
```

---

## 其他说明

- **软删除**：`t_test_case`、`t_test_code` 使用 `deleted_at`；`t_project` 使用 `deleted`；列表与详情查询均过滤已软删数据。
- **分页**：所有列表接口支持 `page`、`size`（默认 10），返回 `records`、`total`、`page`、`size`；后端依赖 MyBatis-Plus 分页插件（`MybatisPlusConfig`、mybatis-plus-jsqlparser）。
- **安全**：业务接口需登录；密码 BCrypt；敏感配置建议通过环境变量或外部配置管理，不要提交到仓库。
- **扩展**：Prompt 集中在 `common.constant.AiPrompt`；AI 调用封装在 `common.ai.AiClientService`；新增业务可参考现有模块的 controller/service/mapper 分层。

如遇问题，可先查看控制台与 `logs/application.log` 中的报错信息，并确认数据库、数据源、AI 配置是否正确。

---

## 相关文档

| 文档 | 说明 |
|------|------|
| [docs/REQUIREMENTS.md](docs/REQUIREMENTS.md) | 完整需求文档：功能边界、业务规则、领域模型 |
| [docs/TECHNICAL.md](docs/TECHNICAL.md) | 技术文档：技术栈、架构、REST API、数据库、部署与扩展 |
| [docs/USER_GUIDE.md](docs/USER_GUIDE.md) | 使用文档：从注册到导出代码的完整操作说明与常见问题 |
| [docs/PROJECT_LOGIC.md](docs/PROJECT_LOGIC.md) | 项目逻辑说明：前后端流程、分层与数据流 |
