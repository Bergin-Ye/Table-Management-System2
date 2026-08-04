# ERP 系统 · 多 Agent 分工拓扑与协同规则

> 由用户自行启动各 agent。**先读本文件 + 设计规格书，再动手。**

## 1. 分工拓扑（3 个 Agent）

```
                    ┌─────────────────────────────┐
                    │  你（用户）先读 00-README      │
                    └──────────────┬──────────────┘
                                   │
              ┌────────────────────┼────────────────────┐
              ▼                    ▼                    ▼
     Agent 1：后端               Agent 2：前端          （Agent 3 最后启动）
     01-backend-agent.md         02-frontend-agent.md   03-integration-agent.md
   Spring Boot 全后端            Vue3 全部页面 +        统筹：建库/启动/联调/
   (可独立并行)                  Apple/液体玻璃风格      假Excel往返测试/验收
              │                    │                    │
              └────────┬───────────┘                    │
                       ▼                                ▼
              两个代码目录完全独立（backend/ + frontend/），无冲突          验收报告
```

- **Agent 1（后端）与 Agent 2（前端）可完全并行**——代码目录不同（`backend/` 与 `frontend/`），互不干扰。
- **Agent 3（统筹）必须等 1、2 都完成后再启动**。
- 若你想加快后端，可把后端拆成两个 agent（认证/RBAC 模块 + 单据引擎模块）分别提示词，见本文件第 3 节。

## 2. 通用协同规则（每个 agent 都必须遵守）

1. **先读再写**：启动后第一件事是读 `docs/superpowers/specs/2026-08-04-erp-system-design.md` 和 `docs/superpowers/specs/field-config/*.json`。
2. **字段来源唯一**：所有字段（key/label/type/required）一律来自字段配置 JSON。**不得自己发明字段、不得增删字段、不得改配置**。配置有问题就停下问用户，不要猜。
3. **git 纪律**：项目已 `git init`。每完成一个可运行的小里程碑就 `git add` + `git commit`。后端只提交 `backend/`，前端只提交 `frontend/`，**不要互相触碰对方目录**，也不要提交 `.skills/`、`.agents/`、`node_modules/`、`target/`（已在 `.gitignore`）。
4. **只改自己的包**：后端 agent 只写 `com.erp.*` 下自己负责的包；遇到共享文件（pom.xml / application.yml）有冲突时，先 commit 再沟通，不覆盖别人改动。
5. **明确说"完成"前必须自测**：后端编译通过 + 关键接口 curl 自测；前端 `npm run build` 通过 + 页面自测。不得声称完成而未验证。
6. **数据库密码 `haoyu2026`**，库名 `erp`。不要写死在代码里（放 `application.yml`，可 gitignore 或用环境变量覆盖）。

## 3.（可选）后端拆两个 agent 的方式

后端默认 1 个 agent。如要拆 2 个并行：
- **后端A：认证与系统管理**（建骨架 + 初始化 SQL + 登录/JWT + 用户/角色/菜单接口）
- **后端B：单据引擎**（通用 CRUD/搜索/导入导出/列偏好/元数据接口）
- ⚠️ 必须**先由后端A建好项目骨架并 commit**，后端B再在其上新增单据引擎模块（只在 `doc` 相关包内改动），避免 pom/配置冲突。两个提示词请基于 `01-backend-agent.md` 自行裁剪，并把"负责范围"明确为各自模块。
