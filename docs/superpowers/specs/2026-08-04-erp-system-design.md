# ERP 单据管理系统 · 设计规格书

- 日期：2026-08-04
- 状态：已与需求方确认
- 定位：**纯单据管理**系统 —— 10 个表单各自独立增删改查，无跨表单业务逻辑、无库存计算。

---

## 1. 项目概述

一个非常简单的进销存单据管理系统。系统用数据库存储 10 种业务单据（金蝶 KIS 风格模板），每个单据页面支持**新增/编辑/删除/查看、Excel 导入导出、全局模糊搜索、列显示选择**。含**登录 + RBAC 权限**。

**核心设计决策：方案 B —— 通用单据引擎。**
系统只有 2 张物理单据表（`doc_head` 单据头 + `doc_detail` 明细行），每张单据的全部字段以 JSON 存储。单据之间的差异全部由**字段配置**（`docs/superpowers/specs/field-config/*.json`）描述，该配置**由脚本从模板文件自动生成**，前端、后端、导入导出**三端共用同一份配置**，从根本上杜绝"agent 记错/漏字段"。

## 2. 技术栈

| 层 | 技术 | 版本/说明 |
|----|------|----------|
| 后端 | Java / Spring Boot 3 | JDK 21 |
| 后端 | MyBatis-Plus | ORM |
| 后端 | EasyExcel（阿里） | Excel 导入导出 |
| 后端 | Spring Security + JWT | 认证与权限 |
| 数据库 | MySQL | 密码 `haoyu2026`，库名 `erp` |
| 前端 | Vue 3 + Vite + JS（不使用 TS） | 构建工具 |
| 前端 | Element Plus | 组件库 |
| 前端 | SCSS | 样式预处理器 |
| 前端 | **Apple 苹果风格 · 白色 · 液体玻璃（玻璃拟态）** | 见第 8 节 UI 规范 |

## 3. 已确认需求清单（与用户逐条确认）

| # | 需求 | 结论 |
|---|------|------|
| 1 | 业务范围 | 纯单据管理，10 表单独立增删改查，**无跨表单逻辑、无库存计算** |
| 2 | 单据类型 | 见第 4 节，10 种，分属销售/采购/库存三大菜单 |
| 3 | 字段处理 | **全部列入库**，导入导出一比一还原模板列 |
| 4 | 列显示 | 界面默认显示核心列 + **列选择器**，按用户**保存偏好** |
| 5 | 登录权限 | **RBAC**：管理员 / 普通用户，按菜单授权（见第 10 节） |
| 6 | 审核字段 | 普通文本字段，**预留审批流扩展位**（不实现，见第 14 节） |
| 7 | 导入格式 | 同编号多行合并成一单 **或** 空/不同编号每行成单，**两种都支持** |
| 8 | 单据编号 | **手动填写**（界面），校验唯一；导入用 Excel 编号 |
| 9 | 搜索 | 每表单页**一个全局搜索框**，模糊匹配所有字段（含明细行） |
| 10 | 前端风格 | Apple 苹果风格 / 白色 / 液体玻璃（玻璃拟态），用 taste-skill 系列实现 |

**用户强调**：Excel 导入导出是**最重要**的功能，必须做**假数据往返测试**验证导入完整性。

## 4. 单据类型与菜单结构

菜单为上下级结构，点击上级展开/收起下级。

| 上级菜单 | 子菜单 | docType 代码 | 头部字段数 | 明细字段数 |
|---------|--------|-------------|-----------|-----------|
| 销售管理 | 销售订单 | `xsdd` | 56 | 55 |
| 销售管理 | 销售出库 | `xsck` | 29 | 42 |
| 采购管理 | 采购申请 | `cgsq` | 14 | 27 |
| 采购管理 | 采购订单 | `cgdd` | 33 | 52 |
| 库存管理 | 外购入库 | `wgrk` | 18 | 40 |
| 库存管理 | 生产领料单 | `scld` | 15 | 37 |
| 库存管理 | 产品入库 | `cprk` | 11 | 30 |
| 库存管理 | 其他出库 | `qtck` | 17 | 32 |
| 库存管理 | 其他入库单 | `qtrk` | 16 | 33 |
| 库存管理 | 调拨单 | `dbd` | 23 | 36 |

另有**系统管理**菜单（仅管理员，`doc_type` 为 NULL）：用户管理、角色授权。

## 5. 系统架构

```
┌─ 前端 (Vue3 + ElementPlus + SCSS, Apple/液体玻璃) ─────────┐
│  登录页 │ 主布局(上下级菜单) │ 通用单据页 │ 系统管理页        │
│  所有单据共用一个组件，差异全部来自"字段配置"                  │
└──────────────┬─────────────────────────────────────────────┘
               │ REST API (JSON, JWT Bearer)
┌──────────────▼─────────────────────────────────────────────┐
│ 后端 (Spring Boot 3 + JDK21 + MyBatis-Plus)                 │
│  ① 认证/用户/RBAC   ② 通用单据引擎(CRUD/搜索)               │
│  ③ Excel 导入导出   ④ 用户列偏好                            │
└──────────────┬─────────────────────────────────────────────┘
               │
┌──────────────▼─────────────────────────────────────────────┐
│ MySQL (库名 erp)                                            │
│  sys_user/sys_menu/sys_role_menu + doc_head/doc_detail +   │
│  sys_column_pref                                            │
└────────────────────────────────────────────────────────────┘
```

**字段配置是系统的"权威契约"**：
- 路径：`docs/superpowers/specs/field-config/{docType}.json`
- 内容：`docType`、`name`、`menu`、`headFields[]`、`detailFields[]`、`defaultColumns[]`
- 每个字段：`key`（规范化中文名，如"编号"）、`label`（显示名，同 key）、`excelLabel`（模板原始表头，如"编    号"，用于 Excel 表头匹配）、`type`（`text`/`number`/`int`/`date`）、`required`
- **三端规则**：前端渲染列/表单、后端校验/存取、导入导出映射，全部只读这份配置，**任何 agent 不得自行增删字段**。配置由生成脚本维护。

## 6. 数据库设计（MySQL，库名 erp）

```sql
CREATE DATABASE IF NOT EXISTS erp DEFAULT CHARACTER SET utf8mb4;

-- 用户
CREATE TABLE sys_user (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  username   VARCHAR(50)  NOT NULL UNIQUE,
  password   VARCHAR(255) NOT NULL,          -- BCrypt 加密
  nickname   VARCHAR(50),
  role       VARCHAR(20)  NOT NULL DEFAULT 'USER', -- ADMIN / USER
  status     TINYINT      DEFAULT 1,         -- 1 启用 0 停用
  created_at DATETIME,
  updated_at DATETIME
);

-- 菜单（含上下级）
CREATE TABLE sys_menu (
  id        BIGINT AUTO_INCREMENT PRIMARY KEY,
  parent_id BIGINT       DEFAULT 0,          -- 0 = 顶级
  name      VARCHAR(50),
  path      VARCHAR(100),                    -- 前端路由
  doc_type  VARCHAR(20),                     -- 单据类型；非单据菜单为 NULL
  sort      INT          DEFAULT 0
);

-- 角色-菜单授权（RBAC）
CREATE TABLE sys_role_menu (
  id      BIGINT AUTO_INCREMENT PRIMARY KEY,
  role    VARCHAR(20) NOT NULL,              -- ADMIN / USER
  menu_id BIGINT      NOT NULL
);

-- 单据头（所有单据共 1 张表）
CREATE TABLE doc_head (
  id         BIGINT AUTO_INCREMENT PRIMARY KEY,
  doc_type   VARCHAR(20) NOT NULL,
  biz_no     VARCHAR(100) NOT NULL,          -- 编号（手动填写/导入）
  head_data  JSON,                           -- 头部字段 KV，key = 字段配置中的 key
  status     VARCHAR(20) DEFAULT NULL,       -- 【预留】审批流扩展位，当前恒为 NULL
  search_text TEXT,                          -- 搜索文本：head + 全部明细值拼接，供全局模糊搜索
  created_by VARCHAR(50),
  created_at DATETIME,
  updated_at DATETIME,
  UNIQUE KEY uk_doc_bizno (doc_type, biz_no) -- 编号唯一
);

-- 明细行
CREATE TABLE doc_detail (
  id          BIGINT AUTO_INCREMENT PRIMARY KEY,
  doc_type    VARCHAR(20) NOT NULL,
  head_id     BIGINT NOT NULL,               -- 关联 doc_head.id
  row_no      INT DEFAULT 0,                 -- 行号
  detail_data JSON,                          -- 明细字段 KV
  KEY idx_head (head_id)
);

-- 用户列偏好（每用户每单据）
CREATE TABLE sys_column_pref (
  id      BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT      NOT NULL,
  doc_type VARCHAR(20) NOT NULL,
  columns JSON,                             -- 可见列 key 数组
  UNIQUE KEY uk_user_doc (user_id, doc_type)
);
```

**种子数据**（初始化 SQL 必须含）：
- 用户：`admin`（ADMIN，密码建议 `admin123`，BCrypt 加密）、`user1`（USER，密码 `123456`）
- 菜单：3 个顶级菜单 + 10 个单据子菜单 + 系统管理（含用户管理/角色授权子菜单），`doc_type` 按第 4 节
- 授权：ADMIN → 全部菜单；USER → 10 个单据菜单（不含系统管理）

**search_text 维护**：写入/更新单据时，将 `head_data` 的所有值 + 所有明细行的 `detail_data` 所有值，用空格拼接后写入 `doc_head.search_text`。搜索直接 `WHERE search_text LIKE CONCAT('%', ?, '%')`，避免 JSON 键名误命中。

## 7. 后端接口设计

统一响应格式：`{ "code": 0, "message": "ok", "data": ... }`，`code != 0` 为错误。

### 7.1 认证 / 用户 / RBAC

| 方法 | 路径 | 说明 | 权限 |
|------|------|------|------|
| POST | `/api/auth/login` | 登录，返回 `{token, user:{id,username,nickname,role}}` | 公开 |
| GET | `/api/auth/userinfo` | 当前用户信息 | 登录 |
| GET | `/api/menu/mine` | 当前用户可见菜单树（按 RBAC 过滤） | 登录 |
| GET | `/api/user/list` | 用户分页列表 | ADMIN |
| POST | `/api/user` | 新增用户 | ADMIN |
| PUT | `/api/user/{id}` | 修改用户 / 重置密码 | ADMIN |
| DELETE | `/api/user/{id}` | 删除用户 | ADMIN |
| GET | `/api/role/menus?role=USER` | 角色已授权的菜单 id 列表 | ADMIN |
| PUT | `/api/role/menus` | 保存角色授权 `{role, menuIds[]}` | ADMIN |

登录返回 JWT；后端用 Filter/Interceptor 校验 `Authorization: Bearer <token>`。`/api/user/**`、`/api/role/**` 要求 `role=ADMIN`；`/api/doc/**` 要求登录且**当前角色的菜单包含该 docType**，否则 403。

### 7.2 通用单据接口（10 个单据共用）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/meta/{docType}` | 返回该单据的**字段配置**（即 field-config JSON） |
| GET | `/api/doc/{docType}?page=1&size=20&keyword=` | 分页列表，`keyword` 非空时全局模糊搜索 |
| GET | `/api/doc/{docType}/{id}` | 单据详情 `{head:{...}, details:[{...}]}` |
| POST | `/api/doc/{docType}` | 新增单据（body：`{head:{...}, details:[{...}]}`，key 用配置中的 key） |
| PUT | `/api/doc/{docType}/{id}` | 修改单据 |
| DELETE | `/api/doc/{docType}/{id}` | 删除单据（连带删除明细） |
| POST | `/api/doc/{docType}/import` | Excel 导入（multipart `file`） |
| GET | `/api/doc/{docType}/export?keyword=` | Excel 导出（按当前搜索条件） |
| GET | `/api/doc/{docType}/pref` | 读当前用户列偏好（无则返回配置 `defaultColumns`） |
| PUT | `/api/doc/{docType}/pref` | 存当前用户列偏好 `{columns:["编号","日期",...]}` |

**列表返回**：每行 = 一个单据头 `{id, docType, bizNo, headData, updatedAt, detailCount}`（`detailCount` 为明细行数，界面默认显示）。

**增改校验**：`bizNo` 必填且当前 docType 下唯一（新增冲突返回"编号已存在"）；`日期` 必填；`head`/`details` 中出现的 key 必须存在于配置，未知 key 忽略。

## 8. 前端页面设计

### 8.1 页面清单
1. **登录页**：Apple 风格，白底 + 液体玻璃卡片，居中表单，账号/密码。
2. **主布局**：左侧菜单栏（上下级，点击上级展开/收起，展开状态 localStorage 记忆），顶部用户区（昵称/退出），右侧内容区。
3. **通用单据页**（一个组件按 `docType` 复用）：
   - 顶部工具栏：全局搜索框 + **导入**按钮 + **导出**按钮
   - 表格：默认显示 `defaultColumns`；**列选择器**（按钮或"设置列"图标 → 弹出勾选列表 → 保存到 `pref` 接口，按用户持久化）
   - 表格列：`编号`、`日期`、配置中的 head 字段（列选择器控制）、`明细行数`、`更新时间`（固定系统列，不进列选择器）、操作列（编辑/删除）
   - **编辑弹窗**：上半部头部字段表单（按配置 `headFields` 渲染，日期用日期选择器、数字用数字输入框、其余文本框），下半部**明细行可编辑表格**（增行/删行/复制行，按配置 `detailFields` 渲染列），保存校验后提交。
4. **系统管理**（仅管理员）：用户管理（列表/新增/编辑/删除/重置密码/设角色）、角色授权（勾选菜单）。

### 8.2 UI 规范（Apple 苹果风格 · 白色 · 液体玻璃）⭐
- 前端 agent **必须加载并使用以下 skills**：`design-taste-frontend`（taste-skill 系列）、`high-end-visual-design`、`minimalist-ui`。
- 视觉基调：
  - 整体**白色**为主，浅灰分隔，无重边框
  - **液体玻璃**：半透明白卡片 + `backdrop-filter: blur()` 毛玻璃 + 柔和阴影 + 大圆角（12~16px）
  - 字体栈：`-apple-system, "PingFang SC", "Microsoft YaHei", sans-serif`（细字重优先）
  - 强调色建议浅蓝/灰蓝（如 `#0a84ff` 点缀），按钮圆角，动效轻量
  - Element Plus 组件通过 SCSS 变量覆盖默认主题（`--el-color-primary` 等）
- 遵循 taste-skill 中关于留白、层级、字距的原则；不做重工业风、不用深色大背景。

## 9. Excel 导入导出（核心功能）

### 9.1 导入 `POST /api/doc/{docType}/import`
1. 读取上传 Excel 第一个 sheet，表头行取第一行。
2. **列映射**：对每个 Excel 列，去掉空白后与配置中字段的 `excelLabel`（同样去空白）或 `key` 匹配；匹配到的列参与解析，未匹配的列忽略。
3. 数据行逐行解析成 `{head: {key:val}, detail: {key:val}}`，按字段 `type` 做类型转换（数字/日期），转换失败该行记失败原因。
4. **分组**：
   - 有 `编号` 的行 → 按 `编号` 分组，同编号所有行合并为**一个单据**（头部字段取组内第一行的非空值，其余行依次为明细行）
   - `编号` 为空的行 → 各自成为**独立单据**（1 个明细行），`biz_no` 自动生成回退编号：`IMP{yyyyMMddHHmmss}{序号}`
5. **重复校验**：某组 `编号` 在库中已存在 → **整组跳过**，记为失败，原因"编号已存在"。
6. 写入 `doc_head` + 多条 `doc_detail`，同步维护 `search_text`。
7. 返回结果报告：`{ totalRows, successDocs, failRows: [{rowNo, reason}] }`。失败原因逐行列出，前端弹窗展示。

### 9.2 导出 `GET /api/doc/{docType}/export`
1. 按当前 `keyword` 查询（与列表同条件），导出**当前筛选结果**。
2. Excel 列 = 配置 `headFields` + `detailFields`（按模板原始顺序），表头用 `excelLabel`。
3. 每个单据：**每一条明细行输出一行**，行内头部字段（从单据头）与明细字段（从该明细）合并填写；单据无明细时输出 1 行（仅头部字段）。
4. 文件名：`{单据名}_导出_{yyyyMMdd}.xlsx`。
5. **往返保证**：导出的文件再次导入，能通过"按编号分组"完整还原为原单据（因为每行重复携带头部字段）。

### 9.3 假数据往返测试（验收重点，统筹 agent 执行）
用脚本按模板结构生成 10 条假单据的 Excel，覆盖：
- 1 个"同编号多行"单据（如采购订单 3 行明细）
- 几个单行单据、1 个空编号行（验证自动回退编号）
- 字段值覆盖：中文、英文、数字、日期、空单元格、特殊字符（`-_.、（）%`）
- 某行故意缺"编号"或写非法数字（验证失败行报告）
然后：**导入 → 校验数量与字段值 → 导出 → 与原 Excel 逐字段比对**，验证 100% 完整。此项不过关即验收不通过。

## 10. 认证与 RBAC

- JWT 登录态；密码 BCrypt。
- 角色：`ADMIN`、`USER`。
- `sys_menu` 存菜单树；`sys_role_menu` 存角色可访问菜单。
- 前端登录后拉取 `/api/menu/mine` 渲染菜单；无权限菜单不显示。
- 后端对 `系统管理` 相关接口强制 `ADMIN`；对单据接口校验角色菜单含该 `docType`。
- 默认：ADMIN 全部可见；USER 仅 10 个单据页。

## 11. 用户列偏好

- `sys_column_pref` 表按 (user_id, doc_type) 唯一存 `columns` 数组。
- 打开列表页：读 `/api/doc/{docType}/pref`；无记录 → 用配置 `defaultColumns`。
- 用户勾选列后点保存 → `PUT` 该接口；下次登录依旧生效。

## 12. 错误处理

- 统一响应 `code/message`；业务错误用明确中文提示（如"编号已存在""编号不能为空"）。
- 导入：不因单行失败中断整个文件，逐行报告。
- 前后端联调：后端返回 401（未登录/过期）、403（无权限）、404（单据不存在）。
- 前端：接口失败统一 ElMessage 提示；删除、导出、导入均有确认/结果反馈。

## 13. 测试与验收计划（统筹 agent）

1. **环境**：JDK21、MySQL（建库 `erp`，密码 `haoyu2026`，执行初始化 SQL）、Node 启动前端。后端端口 8080，前端 Vite 端口 5173（`/api` 代理到 8080）。
2. **假 Excel 往返测试**（第 9.3 节，最高优先级）。
3. **功能冒烟**：
   - 10 个单据各自：新增（含明细多行）/编辑/删除/查看
   - 全局模糊搜索：搜头部字段值、**搜明细行字段值**均能命中
   - 列选择器 + 偏好保存 + 换用户后偏好独立
   - 登录/登出；`user1` 看不到系统管理；`user1` 访问 ADMIN 接口返回 403
4. **边界测试**：空 Excel、表头缺列、编号重复、超大文件、编号含特殊字符。
5. 输出**验收报告**（通过/问题清单），问题修复后回归。

## 14. 预留扩展点（不实现）

- `doc_head.status`：已预留，未来审批流使用；当前任何代码不得依赖/写入。
- 字段配置结构允许新增字段（重新跑生成脚本即可），**无需改表结构**（JSON 存储）。
- 若未来需要跨单据/库存逻辑，可在此架构上新增模块，不影响现有单据数据。

## 15. 交付物清单

| 文件 | 说明 |
|------|------|
| `docs/superpowers/specs/2026-08-04-erp-system-design.md` | 本设计规格书 |
| `docs/superpowers/specs/field-config/{cgsq,cgdd,xsdd,wgrk,cprk,qtrk,xsck,scld,qtck,dbd}.json` | 10 份字段配置（权威契约，勿手改） |
| `kis模板/*.xls` | 原始模板（导入导出格式基准） |
| `agents/00-README.md` | agent 分工拓扑与协同规则 |
| `agents/01-backend-agent.md` | 后端 agent 提示词 |
| `agents/02-frontend-agent.md` | 前端 agent 提示词 |
| `agents/03-integration-agent.md` | 统筹 agent 提示词 |

## 16. 各 agent 分工（详见 `agents/` 目录）

| Agent | 负责部分 | 关键输入 |
|-------|---------|---------|
| 后端 agent | Spring Boot 全后端：DB 初始化 SQL、认证/RBAC、通用单据引擎、搜索、导入导出、列偏好 | 本规格书 + 字段配置 |
| 前端 agent | Vue3 全部页面 + Apple/液体玻璃风格 | 本规格书 + 字段配置 + taste-skill |
| 统筹 agent | 联调、启动、假 Excel 往返测试、验收报告 | 本规格书第 9/13 节 |
