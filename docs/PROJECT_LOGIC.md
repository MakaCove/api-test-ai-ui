# 项目前后端逻辑说明

## 一、整体流程

1. **用户** 登录/注册（Spring Security 表单登录，密码 BCrypt）。
2. **工作台** 首页展示快捷入口与最近接口/用例；列表类页面由前端请求 REST API 分页拉取（每页 10 条）。
3. **项目** → **文档管理**：粘贴或上传文档后自动 AI 生成标准化内容；点击「AI提取接口信息」将标准化文档解析为接口列表写入 `t_api_info`。
4. **项目** → **接口管理**：维护接口信息；可「AI生成用例」写入 `t_test_case`（先软删该接口下原有用例）；可进入某接口的**用例列表**。
5. **用例列表**（某接口下）：可新建/编辑/禁用/启用用例；可「AI生成测试用例自动化代码」写入 `t_test_code`（一条用例一条代码，先软删该用例原有代码）。
6. **用例自动化代码列表**（全局）：展示所有测试代码，详情跳转到对应接口的测试代码页；测试代码可保存到本地工程或下载。
7. **系统配置**：配置 AI 端点/Key/模型与测试代码输出目录、包名，存 `t_system_setting`，供 AI 调用与「保存到工程」使用。

---

## 二、后端分层

| 层级 | 包/类 | 说明 |
|------|--------|------|
| 配置 | `config` | Security、Jackson、Thymeleaf、AI 默认配置、全局 Model 注入 contextPath |
| 公共 | `common.constant`、`common.ai` | 提示词常量、AI 调用封装 |
| 安全 | `security` | 登录/注册页、UserDetails、认证 |
| 入口 | `web.controller` | 工作台、全局接口/用例/测试代码列表与详情页 |
| 业务 | `project`、`document`、`api`、`testcase`、`testcode`、`settings`、`user` | 各模块 controller（页面 + REST）、service、entity、mapper |

- **页面控制器**：返回 Thymeleaf 视图名（如 `project/project-list`），模板在 `templates/` 下按模块分目录。
- **REST 控制器**：返回 JSON，供前端 fetch 分页列表、新建、编辑、删除、AI 生成等。

---

## 三、前端结构

- **布局**：`templates/layout.html` 提供侧栏片段，各页 `th:replace="~{layout :: sidebar(activeKey)}"` 引入。
- **静态资源**：`static/css/layout.css` 全局样式；页面内联脚本请求 `/api/*` 拉数据、分页、提交表单。
- **模板目录**：`auth`（登录/注册）、`dashboard`（首页）、`project`（项目列表/详情/文档/接口）、`api`（全局接口列表）、`testcase`、`testcode`、`settings`。

---

## 四、关键数据流

- **文档 → 接口**：`DocumentAnalyzeService.analyzeAndGenerateApis` 读标准化内容，调 AI 解析为 JSON 接口列表，写入 `t_api_info`。
- **接口 → 用例**：`TestCaseGenerateService.generateForApi` 调 AI 生成用例 JSON 数组，短事务内软删该接口下原用例并插入新用例。
- **用例 → 代码**：`TestCodeGenerateService.generateForApi` / `generateForTestCase` 按条调 AI 生成 Java 代码，短事务内软删旧代码并插入新记录；保存到工程时读配置表 `code.base-dir`、`code.base-package` 写文件。

---

## 五、软删除与分页

- `t_test_case`、`t_test_code` 含 `deleted_at`，列表与详情均过滤 `deleted_at IS NULL`。
- 所有列表接口支持 `page`、`size`（默认 10），返回 `records`、`total`、`page`、`size`；前端按需渲染分页条。
