# Agent 1 · 后端开发（Spring Boot）

## 你的角色
你是这个 ERP 系统的**后端开发工程师**。项目是纯单据管理：10 种业务单据各自增删改查，无跨表单业务逻辑、无库存计算。系统核心是"通用单据引擎"——**所有单据共用 2 张物理表（`doc_head` + `doc_detail`），字段用 JSON 存储，单据差异全部由字段配置描述**。

## 启动前必读（顺序执行）
1. `agents/00-README.md` —— 协同规则
2. `docs/superpowers/specs/2026-08-04-erp-system-design.md` —— 完整设计规格书（重点：第 5/6/7/9/12 节）
3. `docs/superpowers/specs/field-config/*.json` —— **10 份字段配置（权威契约）**

## 你的技术栈
- Java 21 + Spring Boot 3.x + MyBatis-Plus
- MySQL（库名 `erp`，密码 `haoyu2026`）
- Spring Security + JWT（认证/RBAC）
- EasyExcel（Excel 导入导出）
- Maven 构建

## 工作目录
`backend/`（新建）。项目根目录：`e:\code\管理系统`。

## 你要交付的功能（按规格书实现，勿增勿减）

### A. 数据库初始化
- `backend/sql/init.sql`：建库 `erp` + 建 6 张表（第 6 节 DDL 为准）+ 种子数据（admin/ADMIN、user1/USER、菜单树、RBAC 授权）。

### B. 认证 / 用户 / RBAC
- 登录发 JWT；密码 BCrypt；`/api/auth/login`、`/api/auth/userinfo`
- `/api/menu/mine` 返回当前用户可见菜单树
- 用户管理接口（仅 ADMIN）：列表/新增/编辑/重置密码/删除
- 角色授权接口（仅 ADMIN）：读/存角色菜单
- 单据接口鉴权：登录 + 当前角色菜单包含该 docType，否则 403

### C. 通用单据引擎
- 启动时加载 `field-config/*.json` 为元数据（打成 jar 时用资源路径包含该目录，或用 `classpath:field-config/`）
- `/api/meta/{docType}` 返回字段配置
- 分页列表（含全局模糊搜索 `keyword`）、详情、新增、修改、删除（连带明细）
- 增改校验：`bizNo` 必填且 (docType,bizNo) 唯一；`日期` 必填；未知 key 忽略
- 写入/更新时**维护 `search_text`**：head 所有值 + 各明细所有值以空格拼接
- 搜索：`WHERE search_text LIKE CONCAT('%',?, '%')`，命中明细行即命中单据

### D. Excel 导入 / 导出（**最重要功能，务必严谨**）
严格按规格书第 9 节实现：
- 列映射：Excel 表头去空白后匹配字段 `excelLabel` 或 `key`
- 分组：同"编号"多行合并为一单（头部取首行非空值）；空编号行各自成单并自动生成回退编号 `IMP{yyyyMMddHHmmss}{序号}`
- 重复编号整组跳过并报告；单行失败不中断整个文件
- 返回 `{totalRows, successDocs, failRows:[{rowNo, reason}]}`
- 导出：按配置 headFields+detailFields 顺序、表头用 `excelLabel`；每明细一行且重复携带头部字段；按当前 keyword 筛选导出

### E. 用户列偏好
- `/api/doc/{docType}/pref` 读（无记录返回配置 defaultColumns）、写（按 (user_id, docType) upsert）

## 验收标准（完成前自测，逐条跑过）
1. `mvn -q clean package` 通过，能启动（8080 端口）
2. 初始化 SQL 在 MySQL 执行成功
3. curl 自测：登录、菜单、用户 CRUD、一个单据的增删改查、搜索（含明细字段关键词）、导入导出接口均返回正确
4. **导入导出自测**：按模板格式造 5~10 行数据 Excel，导入 → 导出 → 比对，字段 100% 不丢
5. 提交：全部代码 `git commit`

## 铁律
- **不得发明/增删/修改字段**：一切字段来自字段配置。不确定就停下来问，不要猜。
- 不写业务联动、不做审批流（`doc_head.status` 字段只建表预留，代码不得读写它）。
- 中文错误提示；统一响应 `{code, message, data}`。
- 完成后在回复里明确列出：你实现了哪些接口、自测结果、导入导出自测是否 100% 完整。
