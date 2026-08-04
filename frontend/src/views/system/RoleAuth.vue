<template>
  <div class="role-page">
    <div class="panel role-panel">
      <div class="panel-head">
        <div class="panel-title">
          <span class="title-text">角色授权</span>
          <span class="title-sub">勾选角色可访问的菜单，保存后立即生效</span>
        </div>

        <div class="role-switch">
          <el-radio-group v-model="currentRole" @change="loadChecked">
            <el-radio-button value="ADMIN">管理员</el-radio-button>
            <el-radio-button value="USER">普通用户</el-radio-button>
          </el-radio-group>
        </div>
      </div>

      <div class="tree-toolbar">
        <el-button size="small" @click="checkAll">全选</el-button>
        <el-button size="small" @click="uncheckAll">全不选</el-button>
        <span class="tree-hint">已勾选 {{ checkedKeys.length }} 项菜单</span>
      </div>

      <div class="tree-wrap" v-loading="loading">
        <el-tree
          ref="treeRef"
          :data="treeData"
          node-key="id"
          show-checkbox
          check-strictly
          :props="{ label: 'name', children: 'children' }"
          :default-expand-all="true"
        />
      </div>

      <div class="panel-footer">
        <el-button type="primary" :loading="saving" @click="save">
          保 存 授 权
        </el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getRoleMenus, saveRoleMenus } from '@/api'
import { useAuth } from '@/stores/auth'

const auth = useAuth()

const currentRole = ref('ADMIN')
const treeRef = ref(null)
const checkedKeys = ref([])
const loading = ref(false)
const saving = ref(false)

// 管理员菜单树即全部菜单（管理员对全部菜单可见）
const treeData = computed(() => auth.menus || [])

async function loadChecked() {
  if (!treeRef.value) {
    // 等待树渲染后 setCheckedKeys
    await new Promise((r) => setTimeout(r, 0))
  }
  loading.value = true
  try {
    const data = await getRoleMenus(currentRole.value)
    const ids = Array.isArray(data) ? data : data?.menuIds || data?.ids || []
    checkedKeys.value = ids.map(String)
    treeRef.value?.setCheckedKeys(checkedKeys.value)
  } catch (e) {
    // 拦截器已提示
  } finally {
    loading.value = false
  }
}

function collectChecked() {
  if (!treeRef.value) return []
  return treeRef.value
    .getCheckedKeys(false)
    .concat(treeRef.value.getHalfCheckedKeys())
    .map(String)
}

function checkAll() {
  checkedKeys.value = allIds(treeData.value)
  treeRef.value?.setCheckedKeys(checkedKeys.value)
}
function uncheckAll() {
  checkedKeys.value = []
  treeRef.value?.setCheckedKeys([])
}

function allIds(list) {
  const ids = []
  const walk = (nodes) => {
    ;(nodes || []).forEach((n) => {
      ids.push(n.id)
      if (n.children?.length) walk(n.children)
    })
  }
  walk(list)
  return ids.map(String)
}

async function save() {
  const menuIds = collectChecked().map(Number)
  saving.value = true
  try {
    await saveRoleMenus({ role: currentRole.value, menuIds })
    checkedKeys.value = menuIds.map(String)
    ElMessage.success('授权已保存')
  } catch (e) {
    // 拦截器已提示
  } finally {
    saving.value = false
  }
}

onMounted(loadChecked)
</script>

<style scoped lang="scss">
@use '@/styles/variables' as *;

.role-page {
  height: 100%;
  min-height: 0;
}

.panel {
  height: 100%;
  display: flex;
  flex-direction: column;
  background: rgba(255, 255, 255, 0.7);
  border: 1px solid $color-border;
  border-radius: $radius-md;
  box-shadow: $shadow-card;
  backdrop-filter: blur(18px) saturate(150%);
  -webkit-backdrop-filter: blur(18px) saturate(150%);
}

.role-panel {
  padding: 18px 20px;
}

.panel-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;

  .panel-title {
    display: flex;
    flex-direction: column;
    gap: 4px;
  }
  .title-text {
    font-size: 16px;
    font-weight: 700;
    color: $color-text-primary;
  }
  .title-sub {
    font-size: 12px;
    color: $color-text-tertiary;
  }
}

.tree-toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;

  .tree-hint {
    margin-left: 8px;
    font-size: 12px;
    color: $color-text-tertiary;
  }
}

.tree-wrap {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 12px;
  border-radius: 12px;
  background: rgba(245, 246, 250, 0.55);
  border: 1px solid $color-border;

  :deep(.el-tree) {
    background: transparent;
    --el-tree-node-hover-bg-color: rgba(10, 132, 255, 0.06);
  }
}

.panel-footer {
  display: flex;
  justify-content: flex-end;
  padding-top: 14px;
}
</style>
