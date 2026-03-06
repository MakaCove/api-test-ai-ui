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
- [日志](#日志)
- [构建与运行](#构建与运行)

---

## 功能概览

| 模块 | 功能说明 |
|------|-----------|
| **用户与认证** | 注册、登录、登出；BCrypt 密码；未登录访问业务页跳转登录 |
| **工作台** | 首页快捷入口、最近接口与用例列表 |
| **项目管理** | 新建 / 列表 / 详情 / 编辑 / 删除（软删）；项目下进入文档管理、接口管理 |
| **文档管理** | 粘贴文本或上传文件（.txt / .doc / .docx / .md）；保存后自动 AI 标准化；点击「AI提取接口信息」解析为接口写入库 |
| **接口管理** | 接口列表（分页）；新建 / 修改（弹窗）/ 禁用 / 启用 / 删除；「AI生成用例」；进入某接口的用例列表 |
| **用例管理** | 按接口展示用例列表（分页）；新建 / 编辑 / 禁用 / 启用；「AI生成用例」；单条用例「AI生成测试用例自动化代码」、查看代码详情 |
| **测试代码管理** | 全局「用例自动化代码列表」分页；按接口的测试代码页：生成、保存到工程、下载 .java；单条用例代码详情页 |
| **系统配置** | AI 配置（Endpoint、API Key、模型、Temperature、Max Tokens）；测试代码输出（工程根目录、默认包名）；配置持久化到库 |

- 所有列表均为**每页 10 条**，支持分页；日期统一格式 `yyyy-MM-dd HH:mm:ss`。
- 测试用例、测试代码支持**软删除**（`deleted_at`），重新生成时先软删再插入。

---

## 技术栈

| 类别 | 技术 |
|------|------|
| 语言 | Java 17 |
| 后端框架 | Spring Boot 3.2.x、Spring Web MVC、Spring Security、Spring Validation |
| 持久层 | MyBatis-Plus、MySQL、HikariCP |
| 视图层 | Thymeleaf、原生 JavaScript、自定义 CSS（`layout.css`） |
| AI 接入 | openai-java SDK，对接通义千问兼容接口（可配置 Endpoint / Key / Model） |
| 文档解析 | Apache POI（.doc / .docx） |
| 生成测试框架 | JUnit 5（生成的代码风格） |

---

## 环境要求与快速开始

### 环境要求

- JDK 17+
- Maven 3.6+
- MySQL 5.7+ / 8.0+（建议 utf8mb4）

### 1. 创建数据库并执行建表脚本

```bash
# 执行项目中的建表脚本
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

浏览器访问：`http://localhost:8080`（若在 `application.yml` 中配置了 `server.servlet.context-path`，则需加上该路径，如 `/samweb/v1`）。

### 4. 首次使用

- 打开注册页完成用户注册，再登录。
- 在「系统配置」中填写 AI 的 Endpoint、API Key、模型名称等（若未在 yml 中配置）。
- 新建项目 → 进入文档管理 → 粘贴或上传接口文档 → 保存（触发标准化）→ 点击「AI提取接口信息」→ 进入接口管理查看并维护接口 → 在接口下「AI生成用例」→ 在用例列表中对单条用例「AI生成测试用例自动化代码」或批量生成后保存/下载。

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
│   ├── JacksonConfig.java
│   ├── LayoutModelAdvice.java
│   ├── SecurityConfig.java
│   └── ThymeleafConfig.java
├── common/                         # 公共
│   ├── constant/AiPrompt.java     # AI 提示词常量
│   └── ai/AiClientService.java    # AI 调用封装
├── security/                       # 认证
│   ├── CustomUserDetails.java
│   ├── CustomUserDetailsService.java
│   └── controller/AuthController.java
├── web/controller/HomeController.java   # 工作台、全局列表页
├── project/                       # 项目模块
├── document/                      # 文档模块
├── api/                           # 接口模块
├── testcase/                      # 用例模块
├── testcode/                      # 测试代码模块
├── settings/                      # 系统配置模块
└── user/                          # 用户模块
```

各业务模块通常包含：`controller`（页面 + REST）、`entity`、`mapper`，部分含 `service`。  
前端：`templates/` 下按功能放置 Thymeleaf 模板；`static/css/layout.css` 为全局样式；列表与表单通过 JS 调用 `/api/*` 获取数据与提交。

---

## 核心业务流程

1. **文档 → 接口**  
   文档保存后自动调用 AI 生成「标准化内容」；用户在文档列表点击「AI提取接口信息」，后端根据标准化内容再调 AI 解析出接口列表 JSON，写入 `t_api_info`。

2. **接口 → 用例**  
   在接口管理或某接口的用例列表页点击「AI生成用例」；后端根据接口信息（含 requestParams、responseSchema）调 AI 生成用例数组，先软删该接口下原有用例，再插入新用例。

3. **用例 → 测试代码**  
   - 在接口的测试代码页可「生成测试代码」（该接口下全部用例，每条用例一条代码）。  
   - 在用例列表中可对单条用例「AI生成测试用例自动化代码」。  
   生成时先软删该用例已有代码记录，再插入新记录；支持保存到本地工程目录与下载 .java。

4. **保存到工程**  
   使用系统配置中的「测试工程根目录」与「默认包名」，将生成的测试类写入对应包路径下；若配置未填则无法保存到工程。

---

## 界面与导航

- **工作台**：`/`，快捷入口与最近接口、用例。
- **项目列表**：`/projects`，新建项目、进入项目详情；详情内进入文档管理、接口管理。
- **接口列表**：`/apis`，全局接口列表；详情跳转到该接口的用例列表。
- **用例列表**：`/testcases`，全局用例列表；详情为单条用例详情页；「代码」进入该用例的代码详情页（可在此页生成代码）。
- **用例自动化代码列表**：`/testcodes`，全局测试代码列表；详情跳转到对应接口的测试代码页。
- **系统配置**：`/settings`，AI 与测试代码输出配置。

侧栏统一：工作台、项目列表、接口列表、用例列表、用例自动化代码列表、系统配置；底部为「退出登录」。

---

## 数据库

- 建表脚本：`src/main/resources/schema/schema.sql`。
- 主要表：`t_user`、`t_project`、`t_document`、`t_api_info`、`t_test_case`、`t_test_code`、`t_system_setting` 等。
- 若在已有库上增加软删除或新字段，可使用 `schema/migration_soft_delete.sql` 等迁移脚本（若有）。
- 字符集建议：`utf8mb4`；若连接串使用 `characterEncoding=utf8mb4` 报错，可改为 `utf-8`。

---

## 日志

- 日志同时输出到控制台与项目目录下 `logs/application.log`。
- 按日滚动：`logs/application.yyyy-MM-dd.log`，保留 30 天。
- 配置见 `application.yml` 的 `logging.*` 及 `logback-spring.xml`。
- `logs/` 已加入 `.gitignore`，不会提交到版本库。

---

## 构建与运行

```bash
# 编译并跳过测试
mvn clean package -DskipTests

# 运行
java -jar target/api-test-ai-ui-1.0-SNAPSHOT.jar

# 或直接以 Spring Boot 方式运行
mvn spring-boot:run
```

---

## 其他说明

- **软删除**：`t_test_case`、`t_test_code` 使用 `deleted_at`；列表与详情查询均过滤已软删数据。
- **分页**：所有列表接口支持 `page`、`size`（默认 10），返回 `records`、`total`、`page`、`size`。
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
