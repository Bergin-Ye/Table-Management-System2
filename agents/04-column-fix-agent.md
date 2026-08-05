# Agent 4 · 列设置修复（全选应包含明细字段）

## 你的角色
你是这个 ERP 系统的**前端 + 后端小改动工程师**。用户发现一个体验问题需要修复，你负责**改代码 + 自测 + 提交**。项目根目录：`e:\code\管理系统`。

## 启动前必读
1. `agents/00-README.md` —— 协同规则（git 纪律、只改自己的文件）
2. `docs/superpowers/specs/2026-08-04-erp-system-design.md` —— 设计规格书（了解系统结构）
3. 重点阅读现有代码：`frontend/src/components/ColumnSelector.vue`、`frontend/src/views/DocPage.vue`、`backend/src/main/java/com/erp/dto/DocListVO.java`、`backend/src/main/java/com/erp/service/DocService.java`

## 问题描述（用户反馈）
在任意单据页点"列设置"，点**全选**，只选中了**单据头字段**（销售订单只有 56 个），用户预期能选到**全部字段**（销售订单模板共 111 列：56 头 + 55 明细）。

## 根因（已定位）
`ColumnSelector.vue` 只渲染了 `meta.headFields`（56 个），**完全没包含 `meta.detailFields`**（55 个明细字段）。所以全选上限就是头部字段数。

## 修复方案（用户已确认：明细字段也可选作列表列）

### 后端（2 处改动）
1. **`DocListVO.java`**：新增字段 `Map<String, Object> firstDetail`（首条明细数据）。`@Data` 会自动生成 getter/setter。
2. **`DocService.java` 的 `page()` 方法**：
   - 列表除了已有的 `headData`、`detailCount`，再为该单据**第一条明细行**（`row_no` 升序第一行）的 `detail_data` 解析为 Map，set 到 `firstDetail`。
   - 参照现有的 `countDetails(ids)` 写一个 `firstDetails(List<Long> headIds)` 辅助方法：用 `docDetailMapper.selectMaps(new QueryWrapper<DocDetail>().select("head_id","detail_data").in("head_id", ids).orderByAsc("row_no").orderByAsc("id"))`，取每个 head_id 的第一条，`parseMap(...)` 后放入 `Map<Long, Map<String,Object>>`。
   - **不要动**其它任何逻辑（增删改查/搜索/导入导出保持原样）。

### 前端（2 处改动）
1. **`ColumnSelector.vue`**：
   - 列出**全部字段**，分两组展示：`单据头字段（N）` + `明细字段（M）`，每组带小标题。
   - **去重**：`detailFields` 中 key 与 `headFields` 重复的（如销售订单的"编号""运费"在头与明细都有），以头部为准，明细组不重复显示。
   - 全选勾选**全部唯一字段**；计数改为"已选 X / N"，N = 头部字段数 + 去重后明细字段数。
   - 顶部提示文案补充一句：`明细字段在列表中显示该单据第一条明细的值。`
   - 其余行为不变（保存/取消、默认勾选 defaultColumns、pref 持久化）。
2. **`DocPage.vue`**：
   - 把计算列 `headCols` 改为 `displayCols`：对每个已保存的列 key，先在 `headFields` 找、找不到再去 `detailFields` 找（重名以头部为准），过滤掉匹配不到的。
   - `cellText(field, row)`：若 field 是头部字段 → 读 `row.headData[field.key]`（"编号"为空时回退 `row.bizNo`）；否则（明细字段）→ 读 `row.firstDetail[field.key]`。
   - 模板 `v-for="f in headCols"` 改为 `v-for="f in displayCols"`。
   - `明细行数`/`更新时间` 固定列保持不变。
   - 不改导入导出、搜索、编辑弹窗逻辑。

## 自测要求（完成前逐条跑过）
1. 后端 `mvn -q -f backend/pom.xml clean package` 通过（或至少 compile 通过）。
2. 前端 `npm run build` 通过。
3. 启动后端（8080）+ 前端 dev（5173，`/api` 代理到 8080），MySQL 已建好库（密码 `haoyu2026`，库 `erp`）。
4. 登录 → 打开**销售订单** → 列设置：
   - 应显示两组：单据头字段（56）+ 明细字段（去重后 53），合计 109 个唯一字段
   - 点**全选** → 选中全部 109 个
   - 保存后列表能显示明细字段列，值为**该单据第一条明细**的值
5. **回归**：任选一张单据做新增（含多明细）/编辑/删除/搜索；再走一次**假 Excel 导入 → 导出往返**（参考 `acceptance-test/` 里的做法），确认导入导出未受影响。
6. `git add` + `git commit`（只提交你改动的 4 个文件）。

## 完成报告里必须写清
- 改动了哪些文件、各改了什么
- **去重说明**：销售订单模板 111 列中，哪些字段在头部/明细重复（如"编号""运费"），去重后 109 个唯一字段 —— 这会让"全选"不是 111 而是 109，要向用户解释清楚
- 自测结果（列设置全选/明细列显示/回归），是否全部通过

## 铁律
- 不改字段配置、不发明字段、不动其它功能逻辑。
- 只改上面列出的 4 个文件，提交前确认无多余改动。
- 完成即如实报告，不声称通过而未验证。
