# ERP 后端（Spring Boot）实现计划

> **For agentic workers:** 由 agent01 本会话内联执行（inline execution）。Steps 用 checkbox 跟踪。

**Goal:** 实现 ERP 纯单据管理系统的完整后端 —— 认证/RBAC、通用单据引擎（10 种单据共用 CRUD + 全局模糊搜索）、Excel 导入导出（往返 100% 保真）、用户列偏好。

**Architecture:** 通用单据引擎 —— 所有单据共用 `doc_head` + `doc_detail` 两张物理表，字段以 JSON 存储；单据差异全部由 `field-config/{docType}.json` 描述，后端启动时从 classpath 加载为元数据，三端共用同一份契约。Spring Boot 3 + MyBatis-Plus + Spring Security(JWT) + EasyExcel。

**Tech Stack:** Java 21 · Spring Boot 3.3.x · MyBatis-Plus 3.5.x · MySQL 8.4 · EasyExcel 3.3.x · Spring Security 6 + jjwt 0.12.x · Lombok · Maven 3.8.8（JAVA_HOME 指向 `C:\Program Files\Java\latest\jdk-21`）

## Global Constraints

- 数据库：MySQL `erp`，用户名 `root`，密码 `haoyu2026`（放 application.yml）。
- **字段来源唯一**：一切字段（key/label/excelLabel/type/required）来自 `docs/superpowers/specs/field-config/*.json`，后端复制到 `backend/src/main/resources/field-config/`（原样复制，不得改动）。不得发明/增删/修改字段。
- 模板结构（已交叉验证 10 份）：单表头行，headFields 列在前、detailFields 列在后，顺序与配置逐列一致；head「编号」excelLabel=「编    号」带空格。
- `doc_head.status` 只建表预留，**代码不得读写**。
- 无跨表单业务逻辑、无库存计算、不做审批流。
- 统一响应 `{code, message, data}`：code=0 成功；业务错误 HTTP 200 + code!=0；401/403/404 用对应 HTTP 码。
- 中文错误提示。验收为 curl + 导入导出往返自测。
- git：每里程碑 commit，只提交 `backend/` 相关文件，不触碰 frontend/、不提交 `.skills/.agents/target`。

## 已确认决策（与用户敲定）

1. 导入时「日期」必填：分组合并后最终单据头部缺「日期」→ 整单记为失败行，reason=日期不能为空。
2. USER 角色授权 3 个业务父菜单 + 10 个子单据菜单（不含系统管理子树），菜单树完整渲染；单据接口按子菜单 doc_type 鉴权。

---

## File Structure

```
backend/
├── pom.xml
├── src/main/resources/
│   ├── application.yml
│   ├── field-config/{10 份}.json          # 从 docs 原样复制
│   └── sql/init.sql                        # 建库建表 + 种子数据
└── src/main/java/com/erp/
    ├── ErpApplication.java
    ├── common/
    │   ├── ApiResponse.java                # {code,message,data}
    │   ├── BizException.java               # 业务异常(带 httpStatus)
    │   └── GlobalExceptionHandler.java     # @RestControllerAdvice
    ├── config/
    │   ├── SecurityConfig.java             # Security 6 + CORS + 无状态
    │   ├── MybatisPlusConfig.java          # 分页插件 + 审计字段填充
    │   └── WebConfig.java                  # (如需)静态/拦截
    ├── security/
    │   ├── JwtUtil.java                    # 签发/解析
    │   ├── JwtAuthFilter.java              # Bearer 校验 → SecurityContext
    │   ├── LoginUser.java                  # principal {id,username,role}
    │   └── CurrentUser.java                # 取当前用户静态工具
    ├── entity/  SysUser, SysMenu, SysRoleMenu, DocHead, DocDetail, SysColumnPref
    ├── mapper/  SysUserMapper, SysMenuMapper, SysRoleMenuMapper,
    │            DocHeadMapper, DocDetailMapper, SysColumnPrefMapper
    ├── dto/
    │   ├── LoginRequest.java / LoginResponse.java
    │   ├── UserSaveRequest.java
    │   ├── RoleMenuSaveRequest.java
    │   ├── DocSaveRequest.java             # {head:Map, details:[{rowNo,detail:Map}]} / 或 List<Map>
    │   ├── DocListVO.java                  # {id,docType,bizNo,headData,updatedAt,detailCount}
    │   ├── DocDetailVO.java                # {head:{}, details:[]}
    │   └── ImportResult.java               # {totalRows, successDocs, failRows:[{rowNo,reason}]}
    ├── config/ FieldConfig.java            # 反序列化 field-config JSON 的模型
    ├── service/
    │   ├── FieldConfigService.java         # 启动加载、getByDocType、列映射、类型转换
    │   ├── AuthService.java
    │   ├── MenuService.java                # 树组装 + docType 鉴权
    │   ├── UserService.java                # ADMIN 用户 CRUD
    │   ├── RoleService.java                # 角色菜单读写
    │   ├── DocService.java                 # CRUD/搜索/search_text/校验
    │   ├── DocPrefService.java
    │   └── ExcelService.java               # 导入/导出
    └── controller/
        ├── AuthController.java             # /api/auth/login /api/auth/userinfo
        ├── MenuController.java             # /api/menu/mine
        ├── UserController.java             # /api/user/**
        ├── RoleController.java             # /api/role/menus
        ├── MetaController.java             # /api/meta/{docType}
        └── DocController.java              # /api/doc/** 含 pref
```

---

### Task 1: Maven 骨架 + 配置 + 应用启动类

**Files:**
- Create: `backend/pom.xml`
- Create: `backend/src/main/resources/application.yml`
- Create: `backend/src/main/java/com/erp/ErpApplication.java`
- Create: `backend/src/main/java/com/erp/common/ApiResponse.java`、`BizException.java`、`GlobalExceptionHandler.java`

**Interfaces:**
- Produces: 可 `mvn -q clean package` 的骨架；`ApiResponse.ok(data)` / `ApiResponse.error(code,msg)` / `ApiResponse.unauthorized(msg)` / `ApiResponse.forbidden(msg)`；`BizException(msg)` → HTTP200+code1、`BizException(403,msg)` → HTTP403。

pom 关键依赖：`spring-boot-starter-web`、`spring-boot-starter-security`、`mybatis-plus-spring-boot3-starter`、`mysql-connector-j`、`easyexcel`、`jjwt-api/impl/jackson`、`lombok`、`spring-boot-starter-test`（test 范围）。java.version=21。

---

### Task 2: 初始化 SQL 与建库验证

**Files:**
- Create: `backend/src/main/resources/sql/init.sql`

**Produces:** 可执行 SQL —— `CREATE DATABASE IF NOT EXISTS erp`、6 张表（第 6 节 DDL 原样）+ 种子数据。

种子数据细节：
- 用户：`admin`（ADMIN，BCrypt(`admin123`)）、`user1`（USER，BCrypt(`123456`)）。
- 菜单（id 固定便于授权）：1 销售管理(顶级)、2 采购管理、3 库存管理、4 系统管理；
  - 子菜单 10..19：xsdd(销售订单)、xsck(销售出库) → 1；cgsq(采购申请)、cgdd(采购订单) → 2；wgrk、scld、cprk、qtck、qtrk、dbd → 3；doc_type=代码，path 用 `/doc/{code}`。
  - 系统管理下：20 用户管理、21 角色授权（doc_type NULL）。
- 角色菜单：ADMIN → 全部 id；USER → 1,2,3,10..19。

验证：`mysql -uroot -phaoyu2026 < init.sql` 成功；`SHOW TABLES` 6 张。

---

### Task 3: 字段配置加载（FieldConfigService）

**Files:**
- Copy: `docs/superpowers/specs/field-config/*.json` → `backend/src/main/resources/field-config/`（原样，`cp` 不做任何改动）
- Create: `backend/src/main/java/com/erp/config/FieldConfig.java`
- Create: `backend/src/main/java/com/erp/service/FieldConfigService.java`

**Interfaces:**
- `FieldConfigService.get(String docType)` → FieldConfig（未找到抛 404）
- `FieldConfigService.getHeadKeySet(docType)` / `getDetailKeySet(docType)` → 合法 key 集合（校验用）
- `FieldConfigService.buildColumnMap(docType, List<String> excelHeaders)` → 列索引 → Field 的映射（表头去空白后按"headFields 顺序、再 detailFields 顺序，贪心匹配第一个未用字段的 excelLabel 或 key"）
- `FieldConfigService.convertByType(Field, Object cellValue)` → 规范化值（date→"yyyy-MM-dd"、number/int→数值、text→去空格字符串）

**Produces:** 启动时 `@PostConstruct` 从 `classpath:field-config/*.json` 读入内存 Map<String,FieldConfig>。10 个 docType 全量加载成功作为验收。

---

### Task 4: 实体 + Mapper + MyBatis-Plus 配置

**Files:**
- Create: `entity/{SysUser,SysMenu,SysRoleMenu,DocHead,DocDetail,SysColumnPref}.java`
- Create: `mapper/{SysUser,SysMenu,SysRoleMenu,DocHead,DocDetail,SysColumnPref}Mapper.java`
- Create: `config/MybatisPlusConfig.java`（分页插件 `PaginationInnerInterceptor(DbType.MYSQL)` + MetaObjectHandler 自动填充 createdAt/updatedAt）

**Interfaces:**
- `DocHead.headData` 为 `String`（JSON 文本），`DocDetail.detailData` 同；列 `head_data`/`detail_data`/`search_text`/`biz_no`/`doc_type`/`row_no` 由下划线转驼峰映射。
- `SysColumnPref.columns` 为 String（JSON 数组文本）。
- Meta 填充：insert 设 createdAt/updatedAt=now；update 设 updatedAt=now。

---

### Task 5: JWT 认证 + 登录接口

**Files:**
- Create: `security/{JwtUtil,JwtAuthFilter,LoginUser,CurrentUser}.java`
- Create: `config/SecurityConfig.java`
- Create: `service/AuthService.java`
- Create: `controller/AuthController.java`
- Modify: `pom.xml` 已含 jjwt；`application.yml` 加 `jwt.secret/expire-hours`

**Interfaces:**
- `JwtUtil.generate(user)` → token；`JwtUtil.parse(token)` → LoginUser 或 null。
- `SecurityConfig`：`/api/auth/login` permitAll，其余 authenticated；`sessionCreationPolicy(STATELESS)`；`addFilterBefore(JwtAuthFilter, UsernamePasswordAuthenticationFilter)`；CORS 允许 `http://localhost:5173` 与 `http://127.0.0.1:5173`；`LoginUser` 实现 `UserDetails`，authorities=`ROLE_{role}`，使 `@PreAuthorize("hasRole('ADMIN')")` 可用。
- `POST /api/auth/login` body `{username,password}` → `{token, user:{id,username,nickname,role}}`；失败 "用户名或密码错误"。
- `GET /api/auth/userinfo` → `{id,username,nickname,role}`。

验收 curl：登录拿 token；带 token 取 userinfo；无 token 401。

---

### Task 6: 菜单树 + RBAC + 角色授权接口

**Files:**
- Create: `service/MenuService.java`、`controller/MenuController.java`
- Create: `service/RoleService.java`、`controller/RoleController.java`

**Interfaces:**
- `GET /api/menu/mine` → 按当前用户角色过滤的菜单树 `[{id,name,path,docType,children:[...]}]`（按 sort 排序，只含该角色已授权菜单）。
- `MenuService.hasDocTypePermission(user, docType)` → boolean；`MenuService.assertDocPermission(docType)` → 抛 403 BizException。
- `GET /api/role/menus?role=USER`（ADMIN）→ 该角色已授权 menuId 列表。
- `PUT /api/role/menus` body `{role, menuIds[]}`（ADMIN）→ 先删后插 `sys_role_menu`。
- 用户接口/角色接口 @PreAuthorize("hasRole('ADMIN')")。

---

### Task 7: 用户管理（ADMIN）

**Files:**
- Create: `dto/UserSaveRequest.java`
- Create: `service/UserService.java`、`controller/UserController.java`

**Interfaces:**
- `GET /api/user/list?page&size&keyword` → 分页（用户名/昵称模糊）；不返回 password。
- `POST /api/user` {username,password,nickname,role,status} → 用户名重复 "用户名已存在"；BCrypt 存密。
- `PUT /api/user/{id}` {nickname,role,status,password?} → password 非空则重置。
- `DELETE /api/user/{id}` → 连带删其列偏好（sys_column_pref），不可删自己。
- 不校验密码强度（简单系统）。

---

### Task 8: 通用单据引擎 —— 元数据 + CRUD + 搜索

**Files:**
- Create: `dto/{DocSaveRequest,DocListVO,DocDetailVO}.java`
- Create: `service/DocService.java`、`controller/MetaController.java`、`controller/DocController.java`
- Modify: `DocHeadMapper.java`/`DocDetailMapper.java`（计数查询）

**Interfaces:**
- `GET /api/meta/{docType}` → FieldConfig JSON（需登录 + docType 权限）。
- `GET /api/doc/{docType}?page&size&keyword` → 分页 `{records:[DocListVO], total, page, size}`；keyword 非空 → `search_text LIKE '%kw%'`；detailCount 按 head_id IN 分组计数；排序 `id DESC`。
- `GET /api/doc/{docType}/{id}` → `{head:Map, details:[{rowNo, detail:Map}]}`，head/detail 为解析后的 JSON 对象。
- `POST /api/doc/{docType}` body `{head:{key:val}, details:[{rowNo?,detail:{...}}]}`：
  - 过滤未知 key；`bizNo` 来自 head["编号"]，必填非空（"编号不能为空"）；`(docType,bizNo)` 唯一（"编号已存在"）；head["日期"] 必填（"日期不能为空"）；details 空则不允许？→ 允许 0 行（导出时输出 1 行仅头）。
  - rowNo 未传则按顺序 1..n；写 head_data/detail_data JSON；维护 search_text；created_by=当前用户名。
- `PUT /api/doc/{docType}/{id}` 同校验（编号唯一排除自身）+ 先删后插明细 + 重算 search_text。
- `DELETE /api/doc/{docType}/{id}` → 删 head + 关联 details。
- search_text 规则：headFields 按配置顺序取 head 各值 + 每个 detail 按配置顺序取各值，非空值以空格拼接。
- 所有 doc 接口入口先 `MenuService.assertDocPermission(docType)`。

---

### Task 9: 用户列偏好

**Files:**
- Create: `service/DocPrefService.java`（并入 DocController）

**Interfaces:**
- `GET /api/doc/{docType}/pref` → 有记录返回 `{columns:[...]}`；无记录返回配置 `defaultColumns`。
- `PUT /api/doc/{docType}/pref` body `{columns:[...]}` → 按 (user_id,doc_type) upsert。

---

### Task 10: Excel 导出

**Files:**
- Create: `service/ExcelService.java`

**Interfaces:**
- `GET /api/doc/{docType}/export?keyword=`：
  - 查当前 keyword 全部命中单据（不分页，限 5000 条防爆）。
  - 表头 = headFields.excelLabel + detailFields.excelLabel（配置顺序）；数据：每单据每明细 1 行（头字段 + 该明细字段）；无明细输出 1 行仅头。
  - 写为字符串单元格（保证往返、保留前导零）；文件名 `{name}_导出_{yyyyMMdd}.xlsx`，Content-Type xlsx。

---

### Task 11: Excel 导入（核心）

**Files:**
- Modify: `service/ExcelService.java`、`controller/DocController.java`

**Interfaces:**
- `POST /api/doc/{docType}/import`（multipart `file`，仅 .xls/.xlsx）：
  1. 用 EasyExcel 读第一个 sheet，headRowNumber=1，拿到 headMap(列索引→表头文本) + 数据行(列索引→cell 值)。
  2. `FieldConfigService.buildColumnMap` 做列映射（去空白匹配 excelLabel/key，贪心未用字段）→ 每数据行解析为 `{head:Map, detail:Map}`；按字段 type 转换，转换失败该行记 `failRows[{rowNo=物理行号, reason}]` 并跳过。
  3. 全空行跳过。分组：head["编号"] 非空 → 同编号合并为一单（头取组内首行非空值，其余为明细）；空编号 → 每行独立成单，bizNo=`IMP{yyyyMMddHHmmss}{序号}`。
  4. 逐单写入：`(docType,bizNo)` 已存在（含本文件先插入的）→ 整组跳过记失败；头缺「日期」→ 整组跳过记失败；合法 → 插 head + details + search_text。
  5. 返回 `{totalRows, successDocs, failRows:[{rowNo,reason}]}`（totalRows=非空数据行数，successDocs=成功单据数）。

---

### Task 12: 全量自测 + 提交

**Files:**
- Create: `backend/src/test/java/com/erp/ErpApplicationTests.java`（contextLoads 冒烟）

**Verification:**
1. `JAVA_HOME=<jdk21> mvn -q clean package` 通过；`java -jar` 启动 8080。
2. MySQL 执行 init.sql 成功。
3. curl 冒烟：登录→menu→user CRUD→meta→单据增删改查→搜索(含明细词)→pref。
4. 导入导出自测：Python 造 10 条假数据 xlsx（覆盖同编号多行/空编号/单行/中文/数字/日期/空/特殊字符/非法数字行）→ 导入 → 导出 → 逐字段比对 100% 完整。
5. `git add backend/` + commit。

---

## Self-Review 记录

- 规格书 §7.1/7.2 全部接口：Task 5(login/userinfo)、6(menu/role)、7(user)、8(doc/meta)、9(pref)、10/11(export/import) —— 全覆盖。
- §6 建表/种子：Task 2。
- §9.1 导入分组/重复/回退编号/报告：Task 11。
- §9.2 导出列/往返：Task 10。
- 铁律（status 不读写、字段不发明）：写入 Global Constraints + Task 8 校验逻辑。
