<template>
  <div class="home">
    <div class="welcome">
      <div class="logo-ring">
        <svg viewBox="0 0 64 64" width="40" height="40" aria-hidden="true">
          <rect x="6" y="6" width="52" height="52" rx="15" fill="#0a84ff" opacity="0.92" />
          <rect x="19" y="16" width="15" height="20" rx="3" fill="#fff" opacity="0.95" />
          <rect x="38" y="16" width="7" height="7" rx="2" fill="#fff" opacity="0.55" />
          <rect x="38" y="27" width="7" height="9" rx="2" fill="#fff" opacity="0.55" />
          <rect x="19" y="42" width="26" height="6" rx="3" fill="#fff" opacity="0.85" />
        </svg>
      </div>
      <h1 class="title">欢迎使用 ERP 单据管理系统</h1>
      <p class="sub">正在为你打开{{ targetName || '单据页面' }}...</p>
      <div class="spinner">
        <el-icon class="is-loading"><Loading /></el-icon>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { Loading } from '@element-plus/icons-vue'
import { useAuth, firstLeaf } from '@/stores/auth'

const router = useRouter()
const auth = useAuth()

const target = computed(() => firstLeaf(auth.menus))
const targetName = computed(() => target.value?.name || '')

let jumped = false

function jump() {
  if (jumped || !target.value?.path) return
  jumped = true
  router.replace(target.value.path)
}

onMounted(() => {
  // 等待菜单加载后自动进入第一个可访问页面
  if (!auth.menus) return
  jump()
})

watch(
  () => auth.menus,
  () => jump()
)
</script>

<style scoped lang="scss">
@use '@/styles/variables' as *;

.home {
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.welcome {
  text-align: center;
  animation: fade 0.5s $ease-apple both;

  .logo-ring {
    width: 88px;
    height: 88px;
    margin: 0 auto 22px;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: 26px;
    background: rgba(255, 255, 255, 0.72);
    box-shadow:
      inset 0 1px 0 rgba(255, 255, 255, 0.9),
      0 20px 50px rgba(10, 132, 255, 0.18);
    backdrop-filter: blur(16px);
    -webkit-backdrop-filter: blur(16px);
  }

  .title {
    margin: 0;
    font-size: 22px;
    font-weight: 700;
    color: $color-text-primary;
    letter-spacing: 0.02em;
  }

  .sub {
    margin: 10px 0 20px;
    font-size: 14px;
    color: $color-text-secondary;
  }

  .spinner {
    color: $color-primary;
    font-size: 22px;
  }
}

@keyframes fade {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>
