<template>
  <component :is="current" :doc-type="leaf?.docType || ''" v-if="current" />
  <div v-else class="notfound">
    <div class="code">404</div>
    <p class="text">页面不存在或无权访问</p>
    <el-button type="primary" plain @click="goHome">返回工作台</el-button>
  </div>
</template>

<script setup>
import { computed, defineAsyncComponent } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuth, flattenMenus } from '@/stores/auth'

const DocPage = defineAsyncComponent(() => import('@/views/DocPage.vue'))
const UserManage = defineAsyncComponent(() => import('@/views/system/UserManage.vue'))
const RoleAuth = defineAsyncComponent(() => import('@/views/system/RoleAuth.vue'))

const route = useRoute()
const router = useRouter()
const auth = useAuth()

// 将当前 URL 还原为菜单 path（如 /xsdd、/system/user）
const menuPath = computed(() => {
  const parts = route.params.pathMatch || []
  return '/' + (Array.isArray(parts) ? parts.join('/') : String(parts))
})

const leaf = computed(() => {
  const flat = flattenMenus(auth.menus)
  return flat.find((m) => m.path === menuPath.value) || null
})

const current = computed(() => {
  const l = leaf.value
  if (!l) return null
  if (l.docType) return DocPage
  // 系统管理：按菜单名分发
  if (l.name === '用户管理') return UserManage
  if (l.name === '角色授权') return RoleAuth
  return null
})

function goHome() {
  router.push('/')
}
</script>

<style scoped lang="scss">
@use '@/styles/variables' as *;

.notfound {
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;

  .code {
    font-size: 64px;
    font-weight: 800;
    letter-spacing: -0.02em;
    background: linear-gradient(135deg, $color-primary, #7db8ff);
    -webkit-background-clip: text;
    background-clip: text;
    color: transparent;
  }
  .text {
    color: $color-text-secondary;
    margin-bottom: 14px;
  }
}
</style>
