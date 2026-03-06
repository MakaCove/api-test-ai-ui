# 项目逻辑说明 · 如何看这个项目

本文档做两件事：**梳理项目整体逻辑**，**教你怎么看代码、每个模块干啥**。新人或后续维护时可按此顺序阅读。

---

## 一、项目是干啥的（一句话）

**从接口文档或手工录入的接口信息出发，用 AI 自动生成测试用例和 JUnit5 测试代码，在一个 Web 里完成文档→接口→用例→代码的全流程管理。**

核心链路：**文档（粘贴/上传）→ AI 标准化 → AI 提取接口 → 接口管理 → AI 生成用例 → 用例管理 → AI 生成测试代码 → 保存/下载**。

---

## 二、如何看这个项目（阅读顺序建议）

### 2.1 从哪里入手

1. **先跑起来**  
   执行 `schema.sql` 建库 → 改 `application.yml` 里数据库和 AI 配置 → `mvn spring-boot:run` → 浏览器访问 `/login`，注册一个用户再登录，把侧栏里每个菜单点一遍，建立“页面长什么样、点完会到哪”的感性认识。

2. **看入口和路由**  
   - **后端**：从 `org.example` 包往下看。请求进来先经过 **Spring Security**（`config/SecurityConfig`）决定要不要登录；再根据 URL 进不同 Controller。  
   - **页面型 URL**（如 `/`、`/projects`、`/apis`）：在 **HomeController**、**ProjectController**、**ApiPageController**、**TestCodePageController**、**SettingsPageController**、**AuthController** 里，返回的是 Thymeleaf 视图名（如 `dashboard/index`、`project/project-list`），对应 `templates/` 下同名目录里的 `.html`。  
   - **数据型 URL**（如 `/api/projects`、`/api/projects/1/documents`）：在各类 **XxxApiController** 里，返回 JSON，给前端 fetch 用。

3. **看前端怎么拿数据**  
   列表页（项目列表、文档列表、接口列表、用例列表、测试代码列表）一般是：**服务端只渲染一个“壳”页面**，页面里的表格、分页由 **页面内嵌的 JavaScript** 通过 `fetch('/api/...')` 拉取 JSON，再拼 HTML 塞进 DOM。所以看“列表从哪来”要同时看：  
   - 后端：哪个 Controller 的哪个方法在写这个 `/api/xxx`；  
   - 前端：对应 `.html` 里 `<script>` 里调的是哪个接口、参数是什么（如 `page`、`size`）。

4. **看业务核心：三条 AI 链路**  
   - **文档 → 接口**：`DocumentAnalyzeService`（先标准化，再 `analyzeAndGenerateApis` 写 `t_api_info`）。  
   - **接口 → 用例**：`TestCaseGenerateService.generateForApi`，写 `t_test_case`。  
   - **用例 → 代码**：`TestCodeGenerateService.generateForApi` / `generateForTestCase`，写 `t_test_code`。  
   这三处都会调 **AiClientService**，而 AiClientService 的 endpoint、apiKey、model 等来自 **SettingsService（表） + AiConfig（yml）**。

### 2.2 请求怎么走（前后端分工）

- **打开一个“页面”**：浏览器请求 GET `/projects` → Spring MVC 匹配到 `ProjectController.listPage()` → 返回视图名 `project/project-list` → Thymeleaf 渲染 `templates/project/project-list.html`（里面会引入 `layout.html` 的侧栏）。  
- **页面要“列表数据”**：该 html 里的 JS 执行 `fetch('/api/projects?page=1&size=10')` → 匹配到 `ProjectApiController.list()` → 查库、返回 JSON → JS 把 `records` 渲染成表格，分页条根据 `total`/`page`/`size` 渲染。  
- **提交表单（新建/编辑）**：JS 里 `fetch('/api/projects', { method: 'POST', body: JSON.stringify(...) })` → 对应 Controller 的 `@PostMapping` 或 `@PutMapping` 处理，写库后返回，前端再刷新列表或关弹窗。

所以：**页面 = 服务端渲染的壳 + 前端 JS 调 REST API 拿数据、改数据**；没有独立的前端工程，所有前端逻辑都在 `templates/**/*.html` 的 `<script>` 里。

### 2.3 侧栏和“当前在哪一栏”

- 公共侧栏在 **`templates/layout.html`** 里，是一个 **fragment**：`th:fragment="sidebar(activeKey)"`。  
- 每个业务页用 `th:replace="~{layout :: sidebar('projects')}"` 这种形式引入，传入的 `activeKey`（如 `dashboard`、`projects`、`apis`、`testcases`、`testcodes`、`settings`）用来高亮当前菜单。  
- 从“接口列表”或“用例自动化代码列表”点进某接口的用例列表/测试代码页时，会带 `from=apis` 或 `from=testcodes`，页面用这个参数决定侧栏高亮和面包屑，让用户知道是从哪个入口进来的。

---

## 三、整体流程（用户视角）

1. **登录/注册**：Spring Security 表单登录；注册在 `AuthController` 写 `t_user`，密码 BCrypt。
2. **工作台**：首页，快捷入口 + 最近接口/用例；列表数据来自后端分页接口，每页 10 条。
3. **项目 → 文档管理**：粘贴或上传文档，保存后自动 AI 生成标准化内容；再点「AI提取接口信息」把标准化内容解析成接口写入 `t_api_info`。
4. **项目 → 接口管理**：维护接口；可「AI生成用例」写 `t_test_case`（会先软删该接口下原有用例）；可进某接口的「用例列表」。
5. **用例列表（某接口下）**：新建/编辑/禁用/启用用例；可对单条用例「AI生成测试用例自动化代码」写 `t_test_code`（一条用例一条代码，会先软删该用例原有代码）。
6. **用例自动化代码列表（全局）**：看所有测试代码；详情跳到对应接口的测试代码页；可保存到工程、下载。
7. **系统配置**：配 AI 的 endpoint、apiKey、模型和测试代码输出目录、包名，存 `t_system_setting`，AI 调用和「保存到工程」都会用。

---

## 四、后端各模块是干啥的（按包看）

### 4.1 入口与配置层

| 包/类 | 干啥的 |
|-------|--------|
| **ApiTestAiUiApplication** | 启动类；`@EnableConfigurationProperties(AiConfig.class)` 让 yml 里的 `ai.*` 绑定到 AiConfig。 |
| **config/** | 全局配置，不写业务逻辑。 |
| **config/SecurityConfig** | Spring Security：哪些路径放行（登录页、注册页、静态资源、错误页），其余要登录；表单登录/登出；PasswordEncoder 用 BCrypt。 |
| **config/AiConfig** | 绑定 `application.yml` 里 `ai.*`（endpoint、api-key、model、temperature、max-tokens），作为 AI 的默认/回退配置。 |
| **config/JacksonConfig** | 全局 JSON 里 `LocalDateTime` 序列化成 `yyyy-MM-dd HH:mm:ss`。 |
| **config/ThymeleafConfig** | 注册 Java8TimeDialect，模板里能用 `#temporals` 等。 |
| **config/LayoutModelAdvice** | 给所有请求的 Model 里塞一个 `contextPath`，Thymeleaf 里用 `${contextPath}` 拼链接，避免用已禁用的 request 对象。 |

### 4.2 公共与安全（被多处复用）

| 包/类 | 干啥的 |
|-------|--------|
| **common/constant/AiPrompt** | 所有 AI 用到的提示词常量：文档标准化、接口提取、用例生成、代码生成；改 prompt 只改这里。 |
| **common/ai/AiClientService** | 封装调用 AI：用 SettingsService（表）拿 endpoint、apiKey、model 等，没有则用 AiConfig（yml）；对外提供 `chat`、`generateTestCode` 等，内部建 OpenAIClient、发请求、取回复文本。 |
| **security/CustomUserDetails** | Spring Security 的 UserDetails 实现，包装 `User` 实体。 |
| **security/CustomUserDetailsService** | 按用户名查 `t_user`，转成 UserDetails，给 Security 做认证用。 |
| **security/controller/AuthController** | 只负责“页面”和“注册”：GET `/login`、`/register` 返回登录/注册页；POST `/register` 校验后写 `t_user` 并跳转登录页。登录校验是 Security 做的，不在这里。 |
| **user/** | 只有 `User` 实体和 `UserMapper`，供 Security 和注册用，没有独立 Controller。 |

### 4.3 工作台与全局列表（不按项目/接口拆）

| 包/类 | 干啥的 |
|-------|--------|
| **web/controller/HomeController** | 处理“整站级”页面：`/` 工作台、`/apis` 全局接口列表、`/testcases` 全局用例列表、`/testcases/detail/{id}` 用例详情、`/testcases/{caseId}/code` 单条用例的代码详情、`/testcodes` 全局测试代码列表。这些页面的数据要么直接在这里查库塞进 Model（如工作台最近列表、全局列表分页），要么只带 projectId/apiId 等，真实列表由前端再调 `/api/...`。 |

### 4.4 项目与文档（项目为维度）

| 包/类 | 干啥的 |
|-------|--------|
| **project/controller/ProjectController** | 只做“页面跳转 + 简单 Model”：`/projects` 项目列表页、`/projects/{id}` 项目详情、`/projects/{id}/documents` 文档管理页、`/projects/{id}/apis` 接口管理页。列表数据不在这里查，由前端调 ProjectApiController、DocumentApiController、ApiInfoApiController。 |
| **project/controller/ProjectApiController** | REST：`GET /api/projects` 分页列表，`POST /api/projects` 新建项目。 |
| **project/entity/Project**、**project/mapper/ProjectMapper** | 项目表实体与 MyBatis-Plus Mapper。 |
| **document/controller/DocumentApiController** | REST：文档的增删改查、粘贴保存、文件上传、AI 分析（即「AI提取接口信息」）。上传/粘贴保存后会触发标准化；分析接口调 DocumentAnalyzeService 写 `t_api_info`。 |
| **document/service/DocumentAnalyzeService** | 核心：① 文档保存后调 AI 生成标准化内容写回 `t_document`；② 用户点「AI提取接口信息」时，读标准化内容、调 AI 解析成接口列表 JSON，写入 `t_api_info`。 |
| **document/entity/Document**、**document/mapper/DocumentMapper** | 文档表实体与 Mapper。 |

### 4.5 接口与用例（接口为维度）

| 包/类 | 干啥的 |
|-------|--------|
| **api/controller/ApiPageController** | 只做一个页面：`/projects/{pid}/apis/{apiId}/testcases`，即“某接口下的用例列表”页；可带 `from` 参数控制侧栏高亮。 |
| **api/controller/ApiInfoApiController** | REST：项目下接口的 CRUD、禁用/启用、`POST .../generate-cases` 调 TestCaseGenerateService 生成用例。 |
| **api/entity/ApiInfo**、**api/mapper/ApiInfoMapper** | 接口表实体与 Mapper。 |
| **testcase/controller/TestCaseApiController** | REST：某接口下用例的 CRUD、禁用/启用；列表和详情都会过滤 `deleted_at`。 |
| **testcase/service/TestCaseGenerateService** | 根据接口信息调 AI 生成用例 JSON 数组；在短事务内软删该接口下原用例再插入新用例。 |
| **testcase/entity/TestCase**、**testcase/mapper/TestCaseMapper** | 用例表实体与 Mapper。 |

### 4.6 测试代码

| 包/类 | 干啥的 |
|-------|--------|
| **testcode/controller/TestCodePageController** | 只做一个页面：`/projects/{pid}/apis/{apiId}/testcodes`，即“某接口下的测试代码列表”页；可带 `from` 参数。 |
| **testcode/controller/TestCodeApiController** | REST：某接口下测试代码的列表、批量生成、单条用例生成、保存到工程、下载。 |
| **testcode/service/TestCodeGenerateService** | 按接口批量或按单条用例调 AI 生成 JUnit5 代码；短事务内软删再插入；保存到工程时读配置表写文件。 |
| **testcode/entity/TestCode**、**testcode/mapper/TestCodeMapper** | 测试代码表实体与 Mapper。 |

### 4.7 系统配置

| 包/类 | 干啥的 |
|-------|--------|
| **settings/controller/SettingsPageController** | 页面：`GET /settings` 返回系统配置页。 |
| **settings/controller/SettingsApiController** | REST：`GET /api/settings` 读配置（从表）、`PUT /api/settings` 写配置进表。 |
| **settings/service/SettingsService** | 读写的都是 `t_system_setting` 的 key-value；AiClientService 和保存到工程都会用。 |
| **settings/entity/SystemSetting**、**settings/mapper/SystemSettingMapper** | 配置表实体与 Mapper。 |

---

## 五、前端结构（怎么配合后端）

- **布局**：`templates/layout.html` 里只有侧栏片段，没有整页框架。各业务页自己写主内容区，用 `th:replace="~{layout :: sidebar(activeKey)}"` 把侧栏嵌进来，再引同一个 `layout.css`，所以风格统一。
- **静态资源**：`static/css/layout.css` 全局样式；没有独立前端构建，JS 都写在各个 `.html` 的 `<script>` 里，用 `fetch` 调 `/api/*`。
- **模板按功能分目录**：`auth/` 登录注册，`dashboard/` 工作台，`project/` 项目列表/详情/文档列表/接口列表，`api/` 全局接口列表，`testcase/` 用例列表与详情，`testcode/` 测试代码列表与单条代码详情，`settings/` 系统配置。看“这个页面长什么样、调了哪些接口”就打开对应目录下的 html。
- **列表与分页**：列表接口统一返回 `{ records, total, page, size }`；前端用 `page`、`size` 请求，拿到后再渲染表格和分页条（每页 10 条）。

---

## 六、关键数据流（三条 AI 链）

| 链路 | 入口（用户操作） | 后端核心 | 写库 |
|------|------------------|----------|------|
| **文档 → 接口** | 文档保存后自动标准化；再点「AI提取接口信息」 | DocumentAnalyzeService：先标准化，再 analyzeAndGenerateApis 调 AI 解析 | 写回 t_document.standardized_content；写 t_api_info |
| **接口 → 用例** | 在接口管理或接口用例列表页点「AI生成用例」 | TestCaseGenerateService.generateForApi；AI 在事务外，短事务内软删该接口下原用例再插新用例 | t_test_case |
| **用例 → 代码** | 在用例列表点「AI生成测试用例自动化代码」或接口测试代码页批量生成 | TestCodeGenerateService.generateForApi / generateForTestCase；短事务内软删再插入 | t_test_code；保存到工程时读 t_system_setting 写文件 |

三条链都通过 **AiClientService** 调 AI；AiClientService 的 endpoint、apiKey、model 等优先用 **SettingsService** 从表里读，没有再用 **AiConfig** 的 yml 默认值。

---

## 七、软删除与分页约定

- **软删除**：`t_test_case`、`t_test_code` 用 `deleted_at`，非空即视为已删；列表和详情查询都带 `deleted_at IS NULL`。`t_project` 用 `deleted` 字段（0/1）。
- **分页**：所有列表类接口支持 `page`、`size`（默认 10），返回 `records`、`total`、`page`、`size`；前端按这个画分页条和表格。
- **排序**：列表统一按 `created_at` 倒序。

---

## 八、小结：按“我想找…”怎么找

- **改登录/权限** → `config/SecurityConfig`、`security/`。
- **改 AI 行为（prompt、模型）** → `common/constant/AiPrompt`、`common/ai/AiClientService`；配置项看 `config/AiConfig`、`settings/`。
- **改“文档 → 接口”** → `document/service/DocumentAnalyzeService`、`document/controller/DocumentApiController`。
- **改“接口 → 用例”** → `testcase/service/TestCaseGenerateService`、`api/controller/ApiInfoApiController`（generate-cases）。
- **改“用例 → 代码”** → `testcode/service/TestCodeGenerateService`、`testcode/controller/TestCodeApiController`。
- **改某列表页的数据或分页** → 先找对应页面是哪个 Controller 返回的（看 URL），再找该页 html 里 fetch 的 `/api/xxx`，去对应的 XxxApiController 和 Mapper 改。
- **改侧栏/全局样式** → `templates/layout.html`、`static/css/layout.css`。

按上述顺序看代码，即可快速理清项目逻辑并定位到具体模块。
