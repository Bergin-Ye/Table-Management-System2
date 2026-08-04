<template>
  <el-dialog
    :model-value="modelValue"
    title="设置显示列"
    width="560px"
    class="column-selector"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <p class="tip">勾选要在列表中显示的字段，保存后对当前用户持久生效。</p>
    <div class="select-all">
      <el-checkbox
        :model-value="allChecked"
        :indeterminate="partial"
        @change="toggleAll"
      >
        全选
      </el-checkbox>
      <span class="count">已选 {{ checked.length }} / {{ meta.headFields.length }}</span>
    </div>
    <el-checkbox-group v-model="checked" class="cols">
      <el-checkbox v-for="f in meta.headFields" :key="f.key" :value="f.key" class="col-item">
        {{ f.label }}
      </el-checkbox>
    </el-checkbox-group>

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

watch(
  () => props.modelValue,
  (v) => {
    if (v) {
      const defs = props.meta.defaultColumns || []
      checked.value = props.current && props.current.length ? [...props.current] : [...defs]
    }
  }
)

const allChecked = computed(() => {
  return props.meta.headFields.length > 0 && checked.value.length === props.meta.headFields.length
})
const partial = computed(() => {
  return checked.value.length > 0 && checked.value.length < props.meta.headFields.length
})

function toggleAll(val) {
  checked.value = val ? props.meta.headFields.map((f) => f.key) : []
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

.cols {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(150px, 1fr));
  gap: 4px;
  max-height: 46vh;
  overflow-y: auto;
  padding: 4px 2px;

  .col-item {
    height: 34px;
    margin-right: 0;
  }
}
</style>
