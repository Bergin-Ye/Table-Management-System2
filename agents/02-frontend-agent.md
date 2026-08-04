# Agent 2 · 前端开发（Vue3 · ElementPlus · Apple 苹果风格）

## 你的角色
你是这个 ERP 系统的**前端开发工程师**。系统是纯单据管理：10 种业务单据页面，**所有单据共用一个通用组件**，差异全部由字段配置描述。你需要实现全部页面，并做成 **Apple 苹果风格 · 白色 · 液体玻璃（玻璃拟态）**。

## 启动前必读（顺序执行）
1. `agents/00-README.md` —— 协同规则
2. `docs/superpowers/specs/2026-08-04-erp-system-design.md` —— 完整设计规格书（重点：第 7/8/9/11 节）
3. `docs/superpowers/specs/field-config/*.json` —— **10 份字段配置（权威契约）**

## 前端设计 skills（必须使用）
- **`design-taste-frontend`**（taste-skill 系列）—— 用它实现整体设计品味
- **`high-end-visual-design`** —— 高级视觉质感
- **`minimalist-ui`** —— 极简、留白
- 风格关键词：**白色 / 液体玻璃（玻璃拟态）/ 苹果风**：半透明白卡片 + `backdrop-filter: blur()` 毛玻璃 + 柔和阴影 + 大圆角 + 细字重系统字体（`-apple-system, "PingFang SC", "Microsoft YaHei"`），强调色浅蓝（`#0a84ff`），Element Plus 主题用 SCSS 变量覆盖。

## 你的技术栈
- Vue 3 + Vite + **JavaScript（不用 TypeScript）**
- Element Plus
- SCSS
- axios（封装请求：统一前缀 `/api`、统一带上 JWT、统一错误提示）

## 工作目录
`frontend/`（新建）。项目根目录：`e:\code\管理系统`。

## 你要交付的页面

### 1. 登录页
Apple 白底 + 液体玻璃卡片，账号/密码，登录成功跳主界面，失败 ElMessage 提示。

### 2. 主布局
- 左侧上下级菜单：销售管理（销售订单/销售出库）、采购管理（采购申请/采购订单）、库存管理（外购入库/生产领料单/产品入库/其他出库/其他入库单/调拨单）、系统管理（用户管理/角色授权）
- **点击上级菜单展开/收起下级**，展开状态 localStorage 记忆
- 顶部用户区：昵称、退出登录
- 菜单数据来自 `/api/menu/mine`（按 RBAC 过滤），所以系统管理对普通用户自动不可见
- 路由：由菜单驱动或按 docType 动态生成

### 3. 通用单据页（核心，一个组件复用 10 次）
- 顶部工具栏：**全局搜索框** + **导入** + **导出** + **列选择器**
- 表格列：`编号`、`日期`、字段配置中的 head 字段、`明细行数`、`更新时间`、操作（编辑/删除）
- **列选择器**：弹窗勾选要显示的列，默认勾选配置 `defaultColumns`；点保存调 `/api/doc/{docType}/pref`，**按用户持久化**；打开页面时读 pref（无则用 defaultColumns）
- 导入：el-upload 上传 xlsx → 后端返回结果报告 → 弹窗展示成功/失败行（含每行原因）
- 导出：调导出接口下载文件（携带当前搜索关键词）
- **编辑弹窗**：上半部头部字段表单（按配置 headFields 渲染：date 用日期选择器、number 用数字输入框、其余文本框，required 标红星），下半部**明细行可编辑表格**（按配置 detailFields 渲染，支持增行/删行/复制行），保存校验后提交
- 分页表格；删除需二次确认；搜索为全局模糊（后端 `keyword`）

### 4. 系统管理（仅管理员，靠菜单隐藏 + 后端 403 兜底）
- 用户管理：列表/新增/编辑/删除/重置密码/设角色（ADMIN/USER）
- 角色授权：勾选角色可访问的菜单并保存

## 与后端协作的接口约定（对齐后端 agent）
- 统一前缀 `/api`，Vite dev 代理到 `http://localhost:8080`
- 响应格式 `{code, message, data}`，`code===0` 为成功
- 单据列表字段：`{id, docType, bizNo, headData, updatedAt, detailCount}`，`headData` 内 key 即配置 key
- 详情：`{head:{...}, details:[{...}]}`
- 元数据：`/api/meta/{docType}` 返回字段配置（含 headFields/detailFields/defaultColumns）

## 验收标准（完成前自测）
1. `npm run build` 通过，dev 启动正常
2. 登录/登出、菜单展开收起正常
3. 至少 2 个单据页面走通：新增（含多明细）、编辑、删除、搜索、导入、导出、列选择器保存
4. 视觉符合 Apple/白色/液体玻璃风格（毛玻璃卡片、大圆角、浅色）
5. 提交：全部代码 `git commit`（只提交 `frontend/`）

## 铁律
- **不得发明字段**：页面渲染的列/表单全部来自字段配置 JSON 和元数据接口，不做任何硬编码字段。
- 不实现任何业务逻辑（无库存、无审核流）。
- 中文界面；交互符合 Element Plus 习惯。
- 完成后在回复里明确列出：实现了哪些页面、自测结果、截图或路径说明。
