<template>
  <el-dialog
    :model-value="modelValue"
    :title="dialogTitle"
    width="min(96vw, 1380px)"
    top="4vh"
    destroy-on-close
    class="doc-edit-dialog"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <div class="dialog-body">
      <!-- 头部字段表单 -->
      <section class="form-section">
        <div class="section-title">
          <span class="title-text">单据信息</span>
          <span class="title-hint">带 <i class="req">*</i> 为必填</span>
        </div>
        <div class="head-form">
          <el-form-item
            v-for="f in meta.headFields"
            :key="f.key"
            :label="f.label"
            :class="{ required: isRequired(f) }"
          >
            <el-date-picker
              v-if="f.type === 'date'"
              v-model="headForm[f.key]"
              type="date"
              value-format="YYYY-MM-DD"
              placeholder="选择日期"
              style="width: 100%"
            />
            <el-input-number
              v-else-if="f.type === 'number' || f.type === 'int'"
              v-model="headForm[f.key]"
              :precision="f.type === 'int' ? 0 : undefined"
              :step="f.type === 'int' ? 1 : undefined"
              controls-position="right"
              style="width: 100%"
            />
            <el-input
              v-else
              v-model="headForm[f.key]"
              :placeholder="`请输入${f.label}`"
              clearable
            />
          </el-form-item>
        </div>
      </section>

      <!-- 明细行可编辑表格 -->
      <section class="form-section">
        <div class="section-title">
          <span class="title-text">明细行（{{ details.length }}）</span>
          <el-button size="small" type="primary" plain @click="addRow">
            <el-icon style="margin-right: 4px"><Plus /></el-icon>添加行
          </el-button>
        </div>

        <div class="detail-wrap">
          <el-table :data="details" size="small" border height="340">
            <el-table-column
              v-for="f in meta.detailFields"
              :key="f.key"
              :label="f.label"
              min-width="140"
            >
              <template #default="{ row }">
                <el-date-picker
                  v-if="f.type === 'date'"
                  v-model="row[f.key]"
                  type="date"
                  value-format="YYYY-MM-DD"
                  placeholder="日期"
                  style="width: 100%"
                />
                <el-input-number
                  v-else-if="f.type === 'number' || f.type === 'int'"
                  v-model="row[f.key]"
                  :precision="f.type === 'int' ? 0 : undefined"
                  :step="f.type === 'int' ? 1 : undefined"
                  controls-position="right"
                  style="width: 100%"
                />
                <el-input
                  v-else
                  v-model="row[f.key]"
                  placeholder=""
                  clearable
                />
              </template>
            </el-table-column>
            <el-table-column label="操作" width="120" align="center" fixed="right">
              <template #default="{ $index }">
                <div class="row-ops">
                  <el-button link type="primary" size="small" @click="copyRow($index)">复制</el-button>
                  <el-button link type="danger" size="small" @click="removeRow($index)">删除</el-button>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </div>
      </section>
    </div>

    <template #footer>
      <div class="dialog-footer">
        <el-button @click="close">取 消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保 存</el-button>
      </div>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import { createDoc, updateDoc } from '@/api'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  docType: { type: String, required: true },
  meta: { type: Object, required: true },
  // null 表示新增；编辑时传 { id, head:{}, details:[] }；复制时传 { head:{}, details:[] } + copying=true
  doc: { type: Object, default: null },
  copying: { type: Boolean, default: false },
})
const emit = defineEmits(['update:modelValue', 'saved'])

const headForm = ref({})
const details = ref([])
const saving = ref(false)

const isEdit = computed(() => !!props.doc && !props.copying)
const dialogTitle = computed(() => {
  if (props.copying) return `复制新增${props.meta?.name || ''}`
  return `${isEdit.value ? '编辑' : '新增'}${props.meta?.name || ''}`
})

const today = () => {
  const d = new Date()
  const m = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${d.getFullYear()}-${m}-${day}`
}

const isRequired = (f) => f.key === '编号' || f.required === true

const blankHead = () => {
  const obj = {}
  props.meta.headFields.forEach((f) => {
    obj[f.key] = f.type === 'number' || f.type === 'int' ? null : ''
  })
  return obj
}

const blankRow = () => {
  const obj = {}
  props.meta.detailFields.forEach((f) => {
    obj[f.key] = f.type === 'number' || f.type === 'int' ? null : ''
  })
  return obj
}

// 若配置含"行号"字段，按 1..n 顺序自动编号
function numberRows() {
  if (!props.meta.detailFields.some((f) => f.key === '行号')) return
  details.value.forEach((r, i) => {
    r['行号'] = i + 1
  })
}

function openForCreate() {
  headForm.value = blankHead()
  headForm.value['日期'] = today()
  details.value = [blankRow()]
  numberRows()
}

function openForEdit() {
  const d = props.doc
  const head = blankHead()
  ;(props.meta.headFields || []).forEach((f) => {
    if (d.head && d.head[f.key] !== undefined && d.head[f.key] !== null) {
      head[f.key] = d.head[f.key]
    }
  })
  headForm.value = head
  details.value = (d.details || []).map((row) => {
    const r = blankRow()
    props.meta.detailFields.forEach((f) => {
      if (row[f.key] !== undefined && row[f.key] !== null) r[f.key] = row[f.key]
    })
    return r
  })
  if (!details.value.length) details.value = [blankRow()]
  numberRows()
}

// 复制：预填当前行全部信息，但清空「编号」引导填新号（编号唯一）
function openForCopy() {
  openForEdit()
  if ('编号' in headForm.value) headForm.value['编号'] = ''
}

watch(
  () => props.modelValue,
  (v) => {
    if (!v) return
    if (props.copying) openForCopy()
    else if (props.doc) openForEdit()
    else openForCreate()
  }
)

function addRow() {
  details.value.push(blankRow())
  numberRows()
}
function copyRow(index) {
  const src = details.value[index]
  const copy = blankRow()
  props.meta.detailFields.forEach((f) => {
    copy[f.key] = src[f.key] !== undefined ? src[f.key] : (f.type === 'number' || f.type === 'int' ? null : '')
  })
  details.value.splice(index + 1, 0, copy)
  numberRows()
}
function removeRow(index) {
  details.value.splice(index, 1)
  numberRows()
}

function validate() {
  // 编号业务必填（配置里标 false，但后端要求唯一且必填）
  const bizNo = headForm.value['编号']
  if (bizNo === undefined || String(bizNo).trim() === '') {
    ElMessage.warning('请填写编号')
    return false
  }
  const headRequired = (props.meta.headFields || []).filter((f) => f.required === true)
  for (const f of headRequired) {
    const v = headForm.value[f.key]
    if (v === undefined || v === null || String(v).trim() === '') {
      ElMessage.warning(`请填写${f.label}`)
      return false
    }
  }
  return true
}

const normVal = (f, v) => {
  if (f.type === 'number' || f.type === 'int') {
    return v === undefined || v === null || v === '' ? null : Number(v)
  }
  return v === undefined || v === null ? '' : v
}

async function save() {
  if (!validate()) return
  saving.value = true
  try {
    const head = {}
    props.meta.headFields.forEach((f) => {
      head[f.key] = normVal(f, headForm.value[f.key])
    })
    const rows = details.value.map((row, i) => {
      const d = {}
      props.meta.detailFields.forEach((f) => {
        d[f.key] = normVal(f, row[f.key])
      })
      if (props.meta.detailFields.some((f) => f.key === '行号')) d['行号'] = i + 1
      return d
    })
    const payload = { head, details: rows }
    if (isEdit.value) {
      await updateDoc(props.docType, props.doc.id, payload)
    } else {
      await createDoc(props.docType, payload)
    }
    ElMessage.success('保存成功')
    emit('saved')
    close()
  } catch (e) {
    // 拦截器已提示
  } finally {
    saving.value = false
  }
}

function close() {
  emit('update:modelValue', false)
}
</script>

<style scoped lang="scss">
@use '@/styles/variables' as *;

.dialog-body {
  max-height: calc(88vh - 150px);
  overflow-y: auto;
  padding-right: 4px;
}

.form-section {
  margin-bottom: 22px;

  .section-title {
    display: flex;
    align-items: center;
    justify-content: space-between;
    margin-bottom: 12px;

    .title-text {
      font-size: 14px;
      font-weight: 700;
      color: $color-text-primary;
    }
    .title-hint {
      font-size: 12px;
      color: $color-text-tertiary;
      .req {
        color: $color-danger;
        font-style: normal;
      }
    }
  }
}

.head-form {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 2px 16px;
  padding: 4px 2px;
  border-radius: 12px;
  background: rgba(245, 246, 250, 0.5);

  :deep(.el-form-item) {
    margin-bottom: 10px;
    padding: 0 8px;
  }
}

// 必填红星
:deep(.el-form-item.required .el-form-item__label)::before {
  content: '*';
  color: $color-danger;
  margin-right: 4px;
}

.detail-wrap {
  border-radius: 12px;
  overflow: hidden;
  border: 1px solid $color-border;
  background: rgba(255, 255, 255, 0.7);
}

// 明细行操作按钮：flex 强制同一行，避免窄列内折行
.row-ops {
  display: flex;
  align-items: center;
  justify-content: center;
  white-space: nowrap;

  :deep(.el-button + .el-button) {
    margin-left: 6px;
  }
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}
</style>
