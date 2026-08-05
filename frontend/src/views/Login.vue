<template>
  <div class="login login-shell">
    <div class="stage">
      <!-- 左侧品牌区：深蓝渐变 + 漂浮光斑 + 白卡 logo + 公司名 -->
      <aside class="brand-panel">
        <span class="glow g1" aria-hidden="true"></span>
        <span class="glow g2" aria-hidden="true"></span>
        <span class="glow g3" aria-hidden="true"></span>

        <div class="brand-inner">
          <div class="brand-logo">
            <img :src="logoImg" alt="深圳市昊昱精密机电有限公司" />
          </div>
          <h1 class="brand-title">深圳市昊昱精密机电有限公司</h1>
          <div class="brand-line"></div>
          <p class="brand-tagline">销售 · 采购 · 库存<br />十类单据 一页掌控</p>
        </div>

        <p class="brand-foot">© 2026 深圳市昊昱精密机电有限公司 · ERP 管理系统</p>
      </aside>

      <!-- 右侧表单区：液体玻璃登录卡 -->
      <div class="form-panel">
        <div class="form-card">
          <div class="form-head">
            <h2 class="form-title">登录工作台</h2>
            <p class="form-sub">使用您的账号进入系统</p>
          </div>

          <el-form class="login-form" :model="form" size="large" @submit.prevent>
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
          </el-form>

          <div class="form-row">
            <el-checkbox v-model="remember" size="small">记住账号</el-checkbox>
            <button type="button" class="demo-link" @click="fillDemo">填入演示账号</button>
          </div>

          <el-button
            type="primary"
            size="large"
            class="login-btn"
            :loading="loading"
            @click="submit"
          >
            登 录
          </el-button>

          <p class="login-hint">默认账号 admin / admin123</p>
        </div>
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
import logoImg from '@/assess/logo.png'

const REMEMBER_KEY = 'erp_remembered_account'

const route = useRoute()
const router = useRouter()

function remembered() {
  try {
    return localStorage.getItem(REMEMBER_KEY) || ''
  } catch (e) {
    return ''
  }
}

const form = reactive({ username: remembered(), password: '' })
const remember = ref(!!remembered())
const loading = ref(false)

function fillDemo() {
  form.username = 'admin'
  form.password = 'admin123'
  remember.value = true
}

async function submit() {
  if (!form.username.trim() || !form.password) {
    ElMessage.warning('请输入账号和密码')
    return
  }
  // 记住账号
  try {
    if (remember.value) localStorage.setItem(REMEMBER_KEY, form.username.trim())
    else localStorage.removeItem(REMEMBER_KEY)
  } catch (e) {
    /* ignore */
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
  min-height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 28px;
  background:
    radial-gradient(70% 80% at 12% 0%, rgba(234, 244, 255, 0.9), transparent 60%),
    radial-gradient(60% 70% at 90% 100%, rgba(229, 241, 255, 0.8), transparent 55%),
    linear-gradient(160deg, #f2f6fc 0%, #f7f9fd 50%, #eef2f9 100%);
  overflow: hidden;
}

.stage {
  width: min(1080px, 100%);
  height: min(600px, 92vh);
  display: flex;
  border-radius: 28px;
  overflow: hidden;
  background: #fff;
  box-shadow: 0 32px 90px rgba(23, 45, 96, 0.18);
}

/* ---------- 左侧品牌区 ---------- */
.brand-panel {
  position: relative;
  flex: 1.2;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 40px 44px 28px;
  color: #fff;
  overflow: hidden;
  background:
    radial-gradient(120% 90% at 0% 0%, rgba(255, 255, 255, 0.18), transparent 55%),
    linear-gradient(150deg, #1d8cff 0%, #0a6ce8 48%, #0a3f9e 100%);
}

.glow {
  position: absolute;
  border-radius: 50%;
  filter: blur(46px);
  animation: drift 16s ease-in-out infinite alternate;
  will-change: transform;
}
.g1 {
  width: 300px;
  height: 300px;
  top: -70px;
  left: -60px;
  background: rgba(255, 255, 255, 0.16);
}
.g2 {
  width: 230px;
  height: 230px;
  right: -40px;
  bottom: 6%;
  background: rgba(120, 200, 255, 0.4);
  animation-delay: -5s;
}
.g3 {
  width: 150px;
  height: 150px;
  left: 28%;
  bottom: -40px;
  background: rgba(255, 255, 255, 0.12);
  animation-delay: -9s;
}

.brand-inner {
  position: relative;
  text-align: center;
  animation: rise 0.8s $ease-apple both;
}

// 白色玻璃卡片承载深红 logo，保证在深蓝背景上的可读性
.brand-logo {
  width: 116px;
  height: 100px;
  margin: 0 auto 26px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 22px;
  background: rgba(255, 255, 255, 0.96);
  box-shadow:
    inset 0 1px 0 rgba(255, 255, 255, 0.9),
    0 18px 44px rgba(10, 40, 120, 0.35);

  img {
    max-width: 84%;
    max-height: 84%;
    object-fit: contain;
  }
}

.brand-title {
  margin: 0;
  font-size: 26px;
  font-weight: 700;
  letter-spacing: 0.02em;
}

.brand-line {
  width: 34px;
  height: 2px;
  margin: 18px auto 16px;
  border-radius: 2px;
  background: rgba(255, 255, 255, 0.55);
}

.brand-tagline {
  margin: 0;
  font-size: 14px;
  line-height: 1.9;
  color: rgba(255, 255, 255, 0.78);
  letter-spacing: 0.06em;
}

.brand-foot {
  position: relative;
  margin: 0;
  font-size: 11px;
  color: rgba(255, 255, 255, 0.45);
  animation: rise 0.8s $ease-apple 0.25s both;
}

/* ---------- 右侧表单区 ---------- */
.form-panel {
  position: relative;
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px;
  background:
    radial-gradient(80% 70% at 100% 0%, rgba(229, 241, 255, 0.85), transparent 60%),
    radial-gradient(60% 60% at 0% 100%, rgba(229, 241, 255, 0.7), transparent 55%),
    #ffffff;
}

.form-card {
  width: 100%;
  max-width: 384px;
  padding: 34px 36px 30px;
  border-radius: 24px;
  border: 1px solid $color-border;
  background: rgba(255, 255, 255, 0.78);
  box-shadow: $shadow-card;
  backdrop-filter: blur(18px) saturate(150%);
  -webkit-backdrop-filter: blur(18px) saturate(150%);
  animation: rise 0.8s $ease-apple 0.12s both;
}

.form-head {
  margin-bottom: 24px;
}

.form-title {
  margin: 0;
  font-size: 22px;
  font-weight: 700;
  color: $color-text-primary;
  letter-spacing: 0.01em;
}

.form-sub {
  margin: 6px 0 0;
  font-size: 13px;
  color: $color-text-tertiary;
}

.login-form {
  .el-form-item {
    margin-bottom: 16px;
  }

  :deep(.el-input__wrapper) {
    border-radius: 12px;
  }

  // 聚焦光晕
  :deep(.el-input__wrapper.is-focus) {
    box-shadow:
      0 0 0 1px $color-primary inset,
      0 0 0 4px rgba(10, 132, 255, 0.12);
  }
}

.form-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: -4px 0 18px;

  .demo-link {
    border: none;
    background: none;
    padding: 0;
    font-size: 12px;
    color: $color-primary;
    cursor: pointer;
    transition: opacity 0.2s;

    &:hover {
      opacity: 0.75;
    }
  }
}

.login-btn {
  width: 100%;
  height: 46px;
  font-size: 15px;
  font-weight: 600;
  letter-spacing: 0.2em;
  border-radius: 12px;
  border: none;
  background: linear-gradient(135deg, #0a84ff, #0071e3);
  transition: transform 0.25s $ease-apple, box-shadow 0.25s $ease-apple;

  &:hover:not(.is-disabled) {
    transform: translateY(-1px);
    box-shadow: 0 12px 28px rgba(10, 132, 255, 0.35);
  }
}

.login-hint {
  margin: 18px 0 0;
  text-align: center;
  font-size: 12px;
  color: $color-text-tertiary;
}

@keyframes drift {
  from {
    transform: translate(0, 0) scale(1);
  }
  to {
    transform: translate(34px, 26px) scale(1.12);
  }
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
  .glow,
  .brand-inner,
  .brand-foot,
  .form-card {
    animation: none;
  }
}

@media (max-width: 860px) {
  .stage {
    flex-direction: column;
    height: auto;
  }
  .brand-panel {
    min-height: 250px;
    padding: 30px 24px 22px;
  }
  .brand-title {
    font-size: 21px;
  }
  .brand-foot {
    display: none;
  }
  .form-panel {
    padding: 28px 20px 36px;
  }
}
</style>
