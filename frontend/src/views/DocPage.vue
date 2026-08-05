<template>
  <div class="doc-page">
    <!-- 工具栏 -->
    <div class="panel toolbar">
      <div class="panel-title">
        <span class="title-text">{{ meta?.name || '单据' }}</span>
      </div>

      <div class="toolbar-right">
        <div class="search-box">
          <el-input
            v-model="keyword"
            placeholder="全局搜索：编号 / 字段 / 明细内容"
            clearable
            class="search-input"
            @keyup.enter="onSearch"
            @clear="onSearch"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
          <el-button type="primary" @click="onSearch">搜索</el-button>
        </div>

        <div class="toolbar-actions">
          <el-upload
            ref="uploadRef"
            :show-file-list="false"
            :auto-upload="false"
            accept=".xlsx,.xls"
            :on-change="onFileChange"
          >
            <el-button>
              <el-icon style="margin-right: 4px"><Upload /></el-icon>导入
            </el-button>
          </el-upload>

          <el-button :loading="exporting" @click="handleExport">
            <el-icon style="margin-right: 4px"><Download /></el-icon>导出
          </el-button>

          <el-button @click="colVisible = true">
            <el-icon style="margin-right: 4px"><Setting /></el-icon>列设置
          </el-button>

          <el-button type="danger" plain :disabled="!selectedRows.length" :loading="deleting" @click="batchDelete">
            <el-icon style="margin-right: 4px"><Delete /></el-icon>批量删除
          </el-button>

          <el-button type="primary" @click="addDoc">
            <el-icon style="margin-right: 4px"><Plus /></el-icon>新增单据
          </el-button>
        </div>
      </div>
    </div>

    <!-- 列表 -->
    <div class="panel table-panel">
      <template v-if="meta">
        <el-table
          :data="list"
          v-loading="loading"
          row-key="id"
          empty-text="暂无数据，点击右上角“新增单据”创建"
          :header-cell-style="headerStyle"
          @selection-change="onSelectionChange"
        >
          <el-table-column type="selection" width="44" align="center" />

          <el-table-column
            v-for="f in headCols"
            :key="f.key"
            :label="f.label"
            min-width="130"
            show-overflow-tooltip
          >
            <template #default="{ row }">
              <span>{{ cellText(f, row) }}</span>
            </template>
          </el-table-column>

          <el-table-column label="明细行数" width="92" align="center">
            <template #default="{ row }">
              <span class="count-badge">{{ row.detailCount ?? 0 }}</span>
            </template>
          </el-table-column>

          <el-table-column label="更新时间" width="172">
            <template #default="{ row }">{{ fmtTime(row.updatedAt) }}</template>
          </el-table-column>

          <el-table-column label="操作" width="180" align="center" fixed="right" class-name="op-col">
            <template #default="{ row }">
              <div class="op-btns">
                <el-button link type="primary" size="small" @click="copyDoc(row)">复制</el-button>
                <el-button link type="primary" size="small" @click="editDoc(row)">编辑</el-button>
                <el-button link type="danger" size="small" @click="removeDoc(row)">删除</el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>

        <div class="pager">
          <el-pagination
            v-model:current-page="page"
            v-model:page-size="size"
            :total="total"
            :page-sizes="[10, 20, 50, 100]"
            layout="total, sizes, prev, pager, next, jumper"
            background
            @current-change="loadList"
            @size-change="onSizeChange"
          />
        </div>
      </template>

      <div v-else class="loading-meta">
        <el-skeleton :rows="6" animated />
      </div>
    </div>

    <!-- 编辑弹窗 -->
    <DocEditDialog
      v-model="editVisible"
      :doc-type="docType"
      :meta="meta"
      :doc="editingDoc"
      :copying="copying"
      @saved="onSaved"
    />

    <!-- 列选择器 -->
    <ColumnSelector
      v-model="colVisible"
      :meta="meta"
      :current="columns"
      @save="onColumnSave"
    />

    <!-- 导入结果 -->
    <ImportResultDialog
      v-model="importVisible"
      :total-rows="importResult.totalRows"
      :success-docs="importResult.successDocs"
      :fail-rows="importResult.failRows"
    />
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Search, Upload, Download, Setting, Plus, Delete } from '@element-plus/icons-vue'
import {
  getMeta,
  getPref,
  getDocList,
  getDocDetail,
  deleteDoc,
  importDoc,
  exportDoc,
  savePref,
} from '@/api'
import { formatCell, downloadBlob } from '@/utils/format'
import DocEditDialog from '@/components/DocEditDialog.vue'
import ColumnSelector from '@/components/ColumnSelector.vue'
import ImportResultDialog from '@/components/ImportResultDialog.vue'

const props = defineProps({
  docType: { type: String, required: true },
})

const meta = ref(null)
const columns = ref([])
const list = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const keyword = ref('')
const loading = ref(false)
const exporting = ref(false)
const deleting = ref(false)
const selectedRows = ref([])

const editVisible = ref(false)
const editingDoc = ref(null)
const copying = ref(false)
const colVisible = ref(false)
const importVisible = ref(false)
const importResult = ref({ totalRows: 0, successDocs: 0, failRows: [] })
const uploadRef = ref(null)

const headerStyle = () => ({ background: 'rgba(245,246,250,0.5)', color: '#6e6e73' })

// 可见头部列（按配置顺序 + 用户偏好）
const headCols = computed(() => {
  if (!meta.value) return []
  return columns.value
    .map((key) => meta.value.headFields.find((f) => f.key === key))
    .filter(Boolean)
})

function cellText(field, row) {
  const head = row.headData || {}
  let val = head[field.key]
  if ((val === undefined || val === null) && field.key === '编号') val = row.bizNo
  return formatCell(field, val)
}

function fmtTime(t) {
  if (!t) return ''
  const s = String(t).replace('T', ' ').slice(0, 19)
  return s
}

async function init() {
  meta.value = null
  columns.value = []
  list.value = []
  page.value = 1
  await loadMeta()
  await loadPref()
  await loadList()
}

async function loadMeta() {
  meta.value = await getMeta(props.docType)
}

async function loadPref() {
  try {
    const pref = await getPref(props.docType)
    const cols = Array.isArray(pref) ? pref : pref?.columns || []
    if (cols.length) columns.value = cols
    else columns.value = [...(meta.value?.defaultColumns || [])]
  } catch (e) {
    columns.value = [...(meta.value?.defaultColumns || [])]
  }
}

async function loadList() {
  loading.value = true
  try {
    const data = await getDocList(props.docType, {
      page: page.value,
      size: size.value,
      keyword: keyword.value.trim(),
    })
    list.value = data?.records || data?.list || data?.rows || (Array.isArray(data) ? data : [])
    total.value = data?.total ?? list.value.length
  } catch (e) {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

function onSearch() {
  page.value = 1
  loadList()
}

function onSizeChange() {
  page.value = 1
  loadList()
}

function addDoc() {
  editingDoc.value = null
  copying.value = false
  editVisible.value = true
}

async function editDoc(row) {
  try {
    const detail = await getDocDetail(props.docType, row.id)
    // 详情接口返回 {head, details}，不含 id，这里补上列表行的 id
    editingDoc.value = { ...detail, id: row.id }
    copying.value = false
    editVisible.value = true
  } catch (e) {
    // 拦截器已提示
  }
}

// 复制：拉取当前行详情 → 以「新增」模式打开弹窗并回填（编号留空引导填新号）
async function copyDoc(row) {
  try {
    const detail = await getDocDetail(props.docType, row.id)
    editingDoc.value = { head: detail.head, details: detail.details }
    copying.value = true
    editVisible.value = true
  } catch (e) {
    // 拦截器已提示
  }
}

function onSelectionChange(rows) {
  selectedRows.value = rows
}

// 批量删除：逐条调用删除接口，统计成功/失败
async function batchDelete() {
  if (!selectedRows.value.length) return
  try {
    await ElMessageBox.confirm(
      `确定删除选中的 ${selectedRows.value.length} 张单据吗？删除后不可恢复。`,
      '批量删除确认',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' }
    )
  } catch (e) {
    return
  }
  deleting.value = true
  try {
    let ok = 0
    for (const row of selectedRows.value) {
      try {
        await deleteDoc(props.docType, row.id)
        ok++
      } catch (e) {
        // 单条失败不中断，继续删其余
      }
    }
    const fail = selectedRows.value.length - ok
    ElMessage.success(`已删除 ${ok} 张${fail ? `，失败 ${fail} 张` : ''}`)
    if (list.value.length === selectedRows.value.length && page.value > 1) page.value -= 1
    selectedRows.value = []
    loadList()
  } finally {
    deleting.value = false
  }
}

async function removeDoc(row) {
  try {
    await ElMessageBox.confirm(`确定删除单据「${row.bizNo || row.headData?.['编号'] || row.id}」吗？删除后不可恢复。`, '删除确认', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch (e) {
    return
  }
  await deleteDoc(props.docType, row.id)
  ElMessage.success('删除成功')
  if (list.value.length === 1 && page.value > 1) page.value -= 1
  loadList()
}

function onSaved() {
  loadList()
}

async function onColumnSave(cols) {
  columns.value = cols
  try {
    await savePref(props.docType, cols)
    ElMessage.success('列偏好已保存')
  } catch (e) {
    // 拦截器已提示
  }
}

function onFileChange(uploadFile) {
  if (!uploadFile?.raw) return
  doImport(uploadFile.raw)
}

async function doImport(file) {
  const fd = new FormData()
  fd.append('file', file)
  const loadingMsg = ElMessage({ message: '正在导入，请稍候...', type: 'info', duration: 0 })
  try {
    const res = await importDoc(props.docType, fd)
    importResult.value = {
      totalRows: res?.totalRows ?? 0,
      successDocs: res?.successDocs ?? 0,
      failRows: res?.failRows || [],
    }
    importVisible.value = true
    loadList()
  } catch (e) {
    // 拦截器已提示
  } finally {
    loadingMsg.close()
    uploadRef.value?.clearFiles()
  }
}

async function handleExport() {
  exporting.value = true
  try {
    const res = await exportDoc(props.docType, keyword.value.trim())
    const blob = res.data
    const ctype = String(res.headers['content-type'] || '')
    if (ctype.includes('application/json')) {
      let msg = '导出失败'
      try {
        const j = JSON.parse(await blob.text())
        msg = j.message || msg
      } catch (e) {
        /* ignore */
      }
      ElMessage.error(msg)
      return
    }
    let filename = `${meta.value?.name || '单据'}_导出_${new Date().toISOString().slice(0, 10)}.xlsx`
    const cd = String(res.headers['content-disposition'] || '')
    const m = cd.match(/filename\*=UTF-8''([^;]+)/) || cd.match(/filename="?([^";]+)"?/)
    if (m) filename = decodeURIComponent(m[1])
    downloadBlob(blob, filename)
    ElMessage.success('导出成功')
  } catch (e) {
    // 拦截器已提示
  } finally {
    exporting.value = false
  }
}

watch(
  () => props.docType,
  () => init()
)

onMounted(() => init())
</script>

<style scoped lang="scss">
@use '@/styles/variables' as *;

.doc-page {
  display: flex;
  flex-direction: column;
  gap: 14px;
  height: 100%;
  min-height: 0;
}

.panel {
  background: rgba(255, 255, 255, 0.7);
  border: 1px solid $color-border;
  border-radius: $radius-md;
  box-shadow: $shadow-card;
  backdrop-filter: blur(18px) saturate(150%);
  -webkit-backdrop-filter: blur(18px) saturate(150%);
}

.toolbar {
  padding: 12px 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  flex-wrap: wrap;

  .panel-title {
    .title-text {
      font-size: 16px;
      font-weight: 700;
      color: $color-text-primary;
      letter-spacing: 0.01em;
    }
  }

  .toolbar-right {
    display: flex;
    align-items: center;
    gap: 12px;
    flex-wrap: wrap;
  }

  .search-box {
    display: flex;
    align-items: center;
    gap: 8px;

    .search-input {
      width: 300px;
    }
  }

  .toolbar-actions {
    display: flex;
    align-items: center;
    gap: 8px;
  }
}

.table-panel {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  padding: 10px 16px 14px;

  :deep(.el-table) {
    flex: 1;
    min-height: 0;
  }

  .count-badge {
    display: inline-block;
    min-width: 26px;
    padding: 1px 8px;
    border-radius: 20px;
    text-align: center;
    font-size: 12px;
    font-weight: 600;
    color: $color-primary;
    background: rgba(10, 132, 255, 0.1);
  }

  .op-btns {
    display: flex;
    align-items: center;
    justify-content: center;
    white-space: nowrap;

    :deep(.el-button + .el-button) {
      margin-left: 2px;
    }
  }

  .pager {
    display: flex;
    justify-content: flex-end;
    padding-top: 12px;
  }

  .loading-meta {
    padding: 18px 6px;
  }
}
</style>
