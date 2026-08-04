<template>
  <div class="login">
    <div class="ambient" aria-hidden="true"></div>

    <div class="login-shell shell">
      <div class="login-core core">
        <div class="brand">
          <div class="brand-mark">
            <svg viewBox="0 0 64 64" width="30" height="30" aria-hidden="true">
              <rect x="6" y="6" width="52" height="52" rx="15" fill="#0a84ff" opacity="0.92" />
              <rect x="19" y="16" width="15" height="20" rx="3" fill="#fff" opacity="0.95" />
              <rect x="38" y="16" width="7" height="7" rx="2" fill="#fff" opacity="0.55" />
              <rect x="38" y="27" width="7" height="9" rx="2" fill="#fff" opacity="0.55" />
              <rect x="19" y="42" width="26" height="6" rx="3" fill="#fff" opacity="0.85" />
            </svg>
          </div>
          <h1 class="brand-title">ERP 单据管理系统</h1>
          <p class="brand-sub">进销存单据 · 金蝶模板 · 统一入口</p>
        </div>

        <el-form
          class="login-form"
          :model="form"
          size="large"
          @submit.prevent
        >
          <el-form-item>
            <el-input
              v-model="form.username"
              placeholder="请输入账号"
              autocomplete="username"
              clearable
              :prefix-icon="User"
            />
          </el-form-item>
          <el-form-item>
            <el-input
              v-model="form.password"
              type="password"
              placeholder="请输入密码"
              autocomplete="current-password"
              show-password
              :prefix-icon="Lock"
              @keyup.enter="submit"
            />
          </el-form-item>
          <el-button
            type="primary"
            size="large"
            class="login-btn"
            :loading="loading"
            @click="submit"
          >
            登 录
          </el-button>
        </el-form>

        <p class="login-hint">默认账号：admin / admin123</p>
      </div>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { login, getMenuMine } from '@/api'
import { setAuth, setMenus } from '@/stores/auth'

const route = useRoute()
const router = useRouter()

const form = reactive({ username: '', password: '' })
const loading = ref(false)

async function submit() {
  if (!form.username.trim() || !form.password) {
    ElMessage.warning('请输入账号和密码')
    return
  }
  loading.value = true
  try {
    const data = await login({ username: form.username.trim(), password: form.password })
    setAuth(data.token, data.user)
    try {
      const menus = await getMenuMine()
      setMenus(menus)
    } catch (e) {
      // 菜单拉取失败不阻塞登录，布局页会重试
    }
    ElMessage.success('登录成功')
    const redirect = route.query.redirect
    router.replace(typeof redirect === 'string' ? redirect : '/')
  } catch (e) {
    // 错误提示已由拦截器统一处理
  } finally {
    loading.value = false
  }
}
</script>

<style scoped lang="scss">
@use '@/styles/variables' as *;

.login {
  position: relative;
  min-height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 24px;
  background:
    radial-gradient(60% 70% at 50% 0%, rgba(240, 248, 255, 0.9), transparent 70%),
    linear-gradient(160deg, #eef4fb 0%, #f6f8fc 45%, #eef2f9 100%);
  overflow: hidden;
}

.login-shell {
  width: 400px;
  max-width: 100%;
  animation: rise 0.7s $ease-apple both;
}

.login-core {
  padding: 40px 38px 30px;
}

.brand {
  text-align: center;
  margin-bottom: 30px;

  .brand-mark {
    width: 58px;
    height: 58px;
    margin: 0 auto 16px;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: 16px;
    background: rgba(255, 255, 255, 0.7);
    box-shadow:
      inset 0 1px 0 rgba(255, 255, 255, 0.9),
      0 12px 30px rgba(10, 132, 255, 0.18);
    backdrop-filter: blur(14px);
    -webkit-backdrop-filter: blur(14px);
  }

  .brand-title {
    margin: 0;
    font-size: 22px;
    font-weight: 700;
    letter-spacing: 0.02em;
    color: $color-text-primary;
  }

  .brand-sub {
    margin: 8px 0 0;
    font-size: 13px;
    color: $color-text-tertiary;
    letter-spacing: 0.04em;
  }
}

.login-form {
  .el-form-item {
    margin-bottom: 18px;
  }

  .login-btn {
    width: 100%;
    height: 46px;
    font-size: 15px;
    font-weight: 600;
    letter-spacing: 0.2em;
    border-radius: 12px;
    margin-top: 4px;
  }
}

.login-hint {
  margin: 22px 0 0;
  text-align: center;
  font-size: 12px;
  color: $color-text-tertiary;
}

@keyframes rise {
  from {
    opacity: 0;
    transform: translateY(18px) scale(0.98);
  }
  to {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

@media (prefers-reduced-motion: reduce) {
  .login-shell {
    animation: none;
  }
}
</style>
