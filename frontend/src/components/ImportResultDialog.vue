<template>
  <el-dialog
    :model-value="modelValue"
    title="导入结果"
    width="620px"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <div class="summary">
      <div class="summary-item success">
        <span class="num">{{ successDocs }}</span>
        <span class="label">成功单据</span>
      </div>
      <div class="summary-item total">
        <span class="num">{{ totalRows }}</span>
        <span class="label">数据行</span>
      </div>
      <div class="summary-item fail" :class="{ zero: failRows.length === 0 }">
        <span class="num">{{ failRows.length }}</span>
        <span class="label">失败行</span>
      </div>
    </div>

    <div v-if="failRows.length" class="fail-wrap">
      <el-table :data="failRows" size="small" max-height="300">
        <el-table-column prop="rowNo" label="Excel 行号" width="120" align="center" />
        <el-table-column prop="reason" label="失败原因" min-width="220" />
      </el-table>
    </div>
    <div v-else class="all-ok">
      <el-icon :size="46" color="#34c759"><CircleCheckFilled /></el-icon>
      <p>全部导入成功</p>
    </div>

    <template #footer>
      <el-button type="primary" @click="$emit('update:modelValue', false)">知道了</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { CircleCheckFilled } from '@element-plus/icons-vue'

defineProps({
  modelValue: { type: Boolean, default: false },
  totalRows: { type: Number, default: 0 },
  successDocs: { type: Number, default: 0 },
  failRows: { type: Array, default: () => [] },
})
defineEmits(['update:modelValue'])
</script>

<style scoped lang="scss">
@use '@/styles/variables' as *;

.summary {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  margin-bottom: 18px;

  .summary-item {
    padding: 18px 12px;
    border-radius: 14px;
    text-align: center;
    background: rgba(245, 246, 250, 0.7);

    .num {
      display: block;
      font-size: 26px;
      font-weight: 700;
      line-height: 1.2;
    }
    .label {
      font-size: 12px;
      color: $color-text-tertiary;
    }

    &.success .num {
      color: #34c759;
    }
    &.total .num {
      color: $color-text-primary;
    }
    &.fail .num {
      color: $color-danger;
    }
    &.fail.zero .num {
      color: #34c759;
    }
  }
}

.fail-wrap {
  border-radius: 12px;
  overflow: hidden;
  border: 1px solid $color-border;
}

.all-ok {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 30px 0 10px;
  gap: 10px;
  color: $color-text-secondary;
}
</style>
