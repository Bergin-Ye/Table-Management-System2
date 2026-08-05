<template>
  <el-dialog
    :model-value="modelValue"
    title="设置显示列"
    width="560px"
    class="column-selector"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <p class="tip">勾选要在列表中显示的字段，保存后对当前用户持久生效。明细字段在列表中显示该单据第一条明细的值。</p>
    <div class="select-all">
      <el-checkbox
        :model-value="allChecked"
        :indeterminate="partial"
        @change="toggleAll"
      >
        全选
      </el-checkbox>
      <span class="count">已选 {{ checked.length }} / {{ totalCount }}</span>
    </div>
    <div class="group">
      <div class="group-title">单据头字段（{{ headFields.length }}）</div>
      <el-checkbox-group v-model="checked" class="cols">
        <el-checkbox v-for="f in headFields" :key="f.key" :value="f.key" class="col-item">
          {{ f.label }}
        </el-checkbox>
      </el-checkbox-group>
    </div>
    <div v-if="detailFields.length" class="group">
      <div class="group-title">明细字段（{{ detailFields.length }}）</div>
      <el-checkbox-group v-model="checked" class="cols">
        <el-checkbox v-for="f in detailFields" :key="f.key" :value="f.key" class="col-item">
          {{ f.label }}
        </el-checkbox>
      </el-checkbox-group>
    </div>

    <template #footer>
      <el-button @click="$emit('update:modelValue', false)">取 消</el-button>
      <el-button type="primary" @click="save">保 存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { ref, computed, watch } from 'vue'

const props = defineProps({
  modelValue: { type: Boolean, default: false },
  meta: { type: Object, required: true },
  // 当前生效的列 key 数组（defaultColumns 或已保存 pref）
  current: { type: Array, default: () => [] },
})
const emit = defineEmits(['update:modelValue', 'save'])

const checked = ref([])

// 全部头部字段
const headFields = computed(() => props.meta.headFields || [])

// 去重后的明细字段：key 与头部重复的（如"编号""运费"）以头部为准，明细组不重复显示
const detailFields = computed(() => {
  const headKeys = new Set(headFields.value.map((f) => f.key))
  return (props.meta.detailFields || []).filter((f) => !headKeys.has(f.key))
})

// 全部唯一字段（头部 + 去重后明细）
const allFields = computed(() => [...headFields.value, ...detailFields.value])

const totalCount = computed(() => allFields.value.length)

watch(
  () => props.modelValue,
  (v) => {
    if (v) {
      const uniqueKeys = new Set(allFields.value.map((f) => f.key))
      const defs = props.meta.defaultColumns || []
      const base = props.current && props.current.length ? [...props.current] : [...defs]
      // 过滤掉不在唯一字段列表中的历史 key（如旧 pref 里的重复明细 key）
      checked.value = base.filter((k) => uniqueKeys.has(k))
    }
  }
)

const allChecked = computed(() => {
  return totalCount.value > 0 && checked.value.length === totalCount.value
})
const partial = computed(() => {
  return checked.value.length > 0 && checked.value.length < totalCount.value
})

function toggleAll(val) {
  checked.value = val ? allFields.value.map((f) => f.key) : []
}

function save() {
  emit('save', [...checked.value])
  emit('update:modelValue', false)
}
</script>

<style scoped lang="scss">
@use '@/styles/variables' as *;

.tip {
  margin: 0 0 12px;
  font-size: 13px;
  color: $color-text-secondary;
}

.select-all {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  margin-bottom: 8px;
  border-radius: 10px;
  background: rgba(245, 246, 250, 0.7);

  .count {
    font-size: 12px;
    color: $color-text-tertiary;
  }
}

.group {
  margin-bottom: 10px;

  .group-title {
    margin: 6px 2px 4px;
    font-size: 12px;
    font-weight: 600;
    color: $color-text-tertiary;
  }
}

.cols {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  gap: 4px;
  max-height: 24vh;
  overflow-y: auto;
  padding: 4px 2px;

  .col-item {
    height: 34px;
    margin-right: 0;
  }
}
</style>
