# 项目逻辑说明 · 如何看这个项目

本文档梳理项目整体逻辑，并说明如何阅读代码、各模块职责，便于新人或后续维护时按顺序理解。

---

## 一、项目是干啥的（一句话）

**从接口文档或手工录入的接口信息出发，用 AI 自动生成测试用例和 JUnit5 测试代码，在一个 Web 里完成文档→接口→用例→代码的全流程管理。**

核心链路：**文档（粘贴/上传）→ AI 标准化 → AI 提取接口 → 接口管理 → AI 生成用例 → 用例管理 → AI 生成测试代码（单条/批量）→ 保存/下载**。

---

## 二、如何看这个项目（阅读顺序建议）

### 2.1 从哪里入手

1. **先跑起来**  
   执行 `schema.sql` 建库 → 改 `application.yml` 里数据库和 AI 配置 → `mvn spring-boot:run` → 浏览器访问 `/login`，注册用户并登录，把侧栏里每个菜单点一遍，建立“页面长什么样、点完会到哪”的感性认识。

2. **看入口和路由**  
   - **后端**：请求先经 **Spring Security**（`config/SecurityConfig`）决定是否需登录；再根据 URL 进不同 Controller。  
   - **页面型 URL**（如 `/`、`/projects`、`/apis`）：在 **HomeController**、**ProjectController**、**ApiPageController**、**TestCodePageController**、**SettingsPageController**、**AuthController** 里，返回 Thymeleaf 视图名，对应 `templates/` 下同名目录里的 `.html`。  
   - **数据型 URL**（如 `/api/projects`、`/api/projects/1/documents`）：在各类 **XxxApiController** 里返回 JSON，供前端 fetch 使用。  
   - **统一错误响应**：REST 异常由 **config/GlobalExceptionHandler** 处理，返回 `{"message": "..."}` 和 400/500。

3. **看前端怎么拿数据**  
   列表页多为：**服务端只渲染一个“壳”页面**，表格与分页由 **页面内嵌的 JavaScript** 通过 `fetch('/api/...')` 拉取 JSON，再拼 HTML 塞进 DOM。需同时看：  
   - 后端：哪个 Controller 的哪个方法提供该 `/api/xxx`；  
   - 前端：对应 `.html` 里 `<script>` 调用的接口与参数（如 `page`、`size`）。  
   列表普遍带**勾选列、全选**；操作列**固定右侧**，内容区可横向滚动。

4. **看业务核心：三条 AI 链路**  
   - **文档 → 接口**：`DocumentAnalyzeService`（先标准化，再 `analyzeAndGenerateApis` 写 `t_api_info`；文档状态：pending/standardized/extracting/done/failed）。  
   - **接口 → 用例**：`TestCaseGenerateService.generateForApi`，写 `t_test_case`；接口有 `case_gen_status`。  
   - **用例 → 代码**：`TestCodeGenerateService.generateForTestCase`（单条），写 `t_test_code`；用例有 `code_gen_status`。  
   三处都通过 **AiClientService** 调 AI；配置优先从 **SettingsService（表）** 读取，缺失再用 **AiConfig（yml）**。

### 2.2 请求怎么走（前后端分工）

- **打开一个“页面”**：浏览器 GET `/projects` → `ProjectController.listPage()` → 返回视图名 `project/project-list` → Thymeleaf 渲染对应 html（引入 `layout.html` 的侧栏）。  
- **页面要“列表数据”**：该 html 里的 JS 执行 `fetch('/api/projects?page=1&size=10')` → `ProjectApiController.list()` → 查库、返回 JSON → JS 把 `records` 渲染成表格，分页条根据 `total`/`page`/`size` 渲染。  
- **提交表单（新建/编辑）**：JS 里 `fetch('/api/projects', { method: 'POST', body: JSON.stringify(...) })` → 对应 Controller 的 `@PostMapping` 或 `@PutMapping` 处理，写库后返回，前端刷新列表或关弹窗。  
- **接口报错**：Controller 或 Service 抛出异常 → `GlobalExceptionHandler` 捕获，返回 400/500 与 `{"message": "..."}`。

因此：**页面 = 服务端渲染的壳 + 前端 JS 调 REST API 拿数据、改数据**；无独立前端工程，前端逻辑都在 `templates/**/*.html` 的 `<script>` 里。

### 2.3 侧栏和“当前在哪一栏”

- 公共侧栏在 **`templates/layout.html`**，片段：`th:fragment="sidebar(activeKey)"`。  
- 各业务页用 `th:replace="~{layout :: sidebar('projects')}"` 等形式引入，传入的 `activeKey`（如 `dashboard`、`projects`、`apis`、`testcases`、`testcodes`、`settings`）用于高亮当前菜单。  
- 从「接口列表」或「用例自动化代码列表」点进某接口的用例列表/测试代码页时，会带 `from=apis` 或 `from=testcodes`，用于侧栏高亮和面包屑。

---

## 三、整体流程（用户视角）

1. **登录/注册**：Spring Security 表单登录；注册在 `AuthController` 写 `t_user`，密码 BCrypt。
2. **工作台**：首页展示**总数统计**（项目/接口/用例/代码）、**各项目统计**（每项目下的接口数、用例数、代码数）、快捷入口与最近接口/用例；列表每页 10 条。
3. **项目 → 文档管理**：粘贴（弹窗）或上传文档，保存后**异步** AI 标准化；再点「AI提取接口信息」将状态置为「AI提取中」并**异步**解析为接口写入 `t_api_info`。
4. **项目 → 接口管理**：维护接口（新建/修改弹窗、禁用/启用、删除）；「AI生成用例」受用例状态控制（生成中禁用）；进某接口「用例列表」。
5. **用例列表（某接口下或全局）**：新建/编辑/禁用/启用用例；单条「AI生成代码」/「重新生成代码」/「生成中」；**勾选用例后「批量生成代码」**（仅在此类页面提供）。
6. **用例自动化代码列表（全局）**：看所有测试代码；**详情**跳到**单条用例的代码详情页**（`/testcases/{caseId}/code`），可在此页生成/重新生成代码；某接口下测试代码页可保存到工程、下载。
7. **系统配置**：配 AI 与测试代码输出目录、包名，存 `t_system_setting`。

---

## 四、后端各模块是干啥的（按包看）

### 4.1 入口与配置层

| 包/类 | 职责 |
|-------|------|
| **ApiTestAiUiApplication** | 启动类；`@EnableConfigurationProperties(AiConfig.class)` 绑定 yml 中 `ai.*`。 |
| **config/** | 全局配置，不写业务逻辑。 |
| **config/SecurityConfig** | Spring Security：放行路径、表单登录/登出、BCrypt。 |
| **config/GlobalExceptionHandler** | `@RestControllerAdvice`：统一处理 REST 异常，返回 400/500 与 `{"message": "..."}`；500 时打日志。 |
| **config/MybatisPlusConfig** | 注册 MyBatis-Plus 分页插件（PaginationInnerInterceptor），列表分页生效依赖此配置及 mybatis-plus-jsqlparser。 |
| **config/AiConfig** | 绑定 `application.yml` 里 `ai.*`，作为 AI 默认/回退配置。 |
| **config/JacksonConfig** | 全局 JSON 中 `LocalDateTime` 序列化为 `yyyy-MM-dd HH:mm:ss`。 |
| **config/ThymeleafConfig** | 注册 Java8TimeDialect，模板可用 `#temporals` 等。 |
| **config/LayoutModelAdvice** | 给所有请求的 Model 注入 `contextPath`，供 Thymeleaf 拼链接。 |

### 4.2 公共与安全

| 包/类 | 职责 |
|-------|------|
| **common/constant/AiPrompt** | 所有 AI 用到的提示词常量；改 prompt 只改这里。 |
| **common/ai/AiClientService** | 封装 AI 调用；从 SettingsService（表）或 AiConfig（yml）取配置；对外提供 `chat`、`generateTestCode` 等。 |
| **security/** | CustomUserDetails、CustomUserDetailsService、AuthController（/login、/register 页面与注册提交）。 |
| **user/** | User 实体与 UserMapper，供 Security 和注册使用。 |

### 4.3 工作台与全局列表

| 包/类 | 职责 |
|-------|------|
| **web/controller/HomeController** | 处理整站级页面：`/` 工作台（含 `fillDashboardStats` 总数与各项目统计）、`/apis` 全局接口列表、`/testcases` 全局用例列表、`/testcases/detail/{id}` 用例详情、`/testcases/{caseId}/code` 单条用例代码详情、`/testcodes` 全局测试代码列表；为 Thymeleaf 准备分页数据与展示用 Map（如状态中文、接口地址、用例名等）。 |

### 4.4 项目与文档

| 包/类 | 职责 |
|-------|------|
| **project/controller/ProjectController** | 页面跳转与简单 Model：`/projects`、`/projects/{id}`、文档管理页、接口管理页。 |
| **project/controller/ProjectApiController** | REST：`GET /api/projects` 分页列表，`POST /api/projects` 新建项目。 |
| **document/controller/DocumentApiController** | REST：文档增删改查、粘贴保存、文件上传、AI 分析（「AI提取接口信息」）；上传/粘贴保存后触发异步标准化；分析时先置状态再异步解析。 |
| **document/service/DocumentAnalyzeService** | 标准化 + 解析接口；维护文档状态（pending/standardized/extracting/done/failed）。 |
| **document/entity/Document**、**document/mapper/DocumentMapper** | 文档表实体与 Mapper。 |

### 4.5 接口与用例

| 包/类 | 职责 |
|-------|------|
| **api/controller/ApiPageController** | 页面：`/projects/{pid}/apis/{apiId}/testcases`（某接口下用例列表），可带 `from` 控制侧栏与面包屑。 |
| **api/controller/ApiInfoApiController** | REST：项目下接口 CRUD、禁用/启用、`POST .../generate-cases` 调 TestCaseGenerateService 生成用例（异步、用例状态控制）。 |
| **api/entity/ApiInfo**、**api/mapper/ApiInfoMapper** | 接口表实体与 Mapper；ApiInfo 含 `case_gen_status`。 |
| **testcase/controller/TestCaseApiController** | REST：某接口下用例的 CRUD、禁用/启用；列表与详情过滤 `deleted_at`；新建时设置 `code_gen_status`。 |
| **testcase/service/TestCaseGenerateService** | 根据接口信息调 AI 生成用例；短事务内软删该接口下原用例再插入新用例。 |
| **testcase/entity/TestCase**、**testcase/mapper/TestCaseMapper** | 用例表实体与 Mapper；TestCase 含 `code_gen_status`。 |

### 4.6 测试代码

| 包/类 | 职责 |
|-------|------|
| **testcode/controller/TestCodePageController** | 页面：`/projects/{pid}/apis/{apiId}/testcodes`（某接口下测试代码列表），可带 `from`。 |
| **testcode/controller/TestCodeApiController** | REST：某接口下测试代码列表、**单条**生成（`POST .../testcodes/generate-case/{testCaseId}`）、保存到工程、下载；单条生成异步、更新用例 `code_gen_status`。 |
| **testcode/service/TestCodeGenerateService** | 按单条用例调 AI 生成 JUnit5 代码；短事务内软删该用例原有代码再插入；保存到工程时读配置表写文件。 |
| **testcode/entity/TestCode**、**testcode/mapper/TestCodeMapper** | 测试代码表实体与 Mapper。 |

### 4.7 系统配置

| 包/类 | 职责 |
|-------|------|
| **settings/controller/SettingsPageController** | 页面：`GET /settings`。 |
| **settings/controller/SettingsApiController** | REST：`GET/PUT /api/settings` 读写配置。 |
| **settings/service/SettingsService** | 读写 `t_system_setting`；AiClientService 与保存到工程都会用。 |
| **settings/entity/SystemSetting**、**settings/mapper/SystemSettingMapper** | 配置表实体与 Mapper。 |

---

## 五、前端结构（怎么配合后端）

- **布局**：`templates/layout.html` 提供侧栏片段；各业务页自行写主内容区，用 `th:replace="~{layout :: sidebar(activeKey)}"` 引入侧栏，共用一个 `layout.css`，风格统一。  
- **静态资源**：`static/css/layout.css` 全局样式；列表操作列固定、内容区横向滚动、分页与勾选样式在此统一。  
- **模板按功能分目录**：`auth/`、`dashboard/`、`project/`、`api/`、`testcase/`、`testcode/`、`settings/`。  
- **列表与分页**：列表接口统一返回 `{ records, total, page, size }`；前端用 `page`、`size` 请求，渲染表格与分页条（每页 10 条）；多数列表带勾选列与全选，用例列表支持「批量生成代码」。  
- **错误与成功提示**：前端优先使用接口返回的 `message`，否则使用统一文案（如「操作失败，请稍后重试」）。

---

## 六、关键数据流（三条 AI 链）

| 链路 | 入口（用户操作） | 后端核心 | 写库与状态 |
|------|------------------|----------|------------|
| **文档 → 接口** | 保存后自动异步标准化；再点「AI提取接口信息」 | DocumentAnalyzeService：先标准化，再 analyzeAndGenerateApis 解析 | 写回 t_document.standardized_content、status；写 t_api_info；状态 extracting→done/failed |
| **接口 → 用例** | 在接口管理或接口用例列表页点「AI生成用例」 | TestCaseGenerateService.generateForApi；异步；短事务内软删该接口下原用例再插新用例 | t_test_case；t_api_info.case_gen_status |
| **用例 → 代码** | 在用例列表/详情/代码详情点「AI生成代码」或「重新生成代码」；或用例列表勾选后「批量生成代码」 | TestCodeGenerateService 单条生成；短事务内软删该用例原代码再插入 | t_test_code；t_test_case.code_gen_status |

三条链都通过 **AiClientService** 调 AI；配置优先 **SettingsService**，再 **AiConfig**。

---

## 七、软删除、分页与状态约定

- **软删除**：`t_test_case`、`t_test_code` 用 `deleted_at`，非空即视为已删；`t_project` 用 `deleted`（0/1）。列表与详情查询均过滤已删数据。  
- **分页**：所有列表类接口支持 `page`、`size`（默认 10），返回 `records`、`total`、`page`、`size`；依赖 MybatisPlusConfig 分页插件。  
- **排序**：列表统一按 `created_at` 倒序。  
- **状态**：文档 `status`（pending/standardized/extracting/done/failed）；接口 `case_gen_status`（pending/generating/done/failed）；用例 `code_gen_status`（pending/generating/done/failed）；前端展示为中文并控制按钮可用性。

---

## 八、按“我想找…”怎么找

- **改登录/权限** → `config/SecurityConfig`、`security/`。  
- **改统一错误返回** → `config/GlobalExceptionHandler`。  
- **改分页行为** → `config/MybatisPlusConfig`、各 Mapper 分页查询。  
- **改 AI 行为（prompt、模型）** → `common/constant/AiPrompt`、`common/ai/AiClientService`；配置项看 `config/AiConfig`、`settings/`。  
- **改“文档 → 接口”** → `document/service/DocumentAnalyzeService`、`document/controller/DocumentApiController`。  
- **改“接口 → 用例”** → `testcase/service/TestCaseGenerateService`、`api/controller/ApiInfoApiController`（generate-cases）。  
- **改“用例 → 代码”** → `testcode/service/TestCodeGenerateService`、`testcode/controller/TestCodeApiController`（generate-case）。  
- **改某列表页的数据或分页** → 先找该页面对应的 Controller 与返回的 html，再看该页 fetch 的 `/api/xxx`，到对应 XxxApiController 和 Mapper 修改。  
- **改侧栏/全局样式/列表样式** → `templates/layout.html`、`static/css/layout.css`。

按上述顺序阅读，即可快速理清项目逻辑并定位到具体模块。
