<template>
  <div class="layout">
    <div class="ambient" aria-hidden="true"></div>

    <!-- 侧边栏：玻璃菜单 -->
    <aside class="sidebar glass">
      <div class="sidebar-brand">
        <div class="brand-mark">
          <svg viewBox="0 0 64 64" width="24" height="24" aria-hidden="true">
            <rect x="6" y="6" width="52" height="52" rx="15" fill="#0a84ff" opacity="0.92" />
            <rect x="19" y="16" width="15" height="20" rx="3" fill="#fff" opacity="0.95" />
            <rect x="38" y="16" width="7" height="7" rx="2" fill="#fff" opacity="0.55" />
            <rect x="38" y="27" width="7" height="9" rx="2" fill="#fff" opacity="0.55" />
            <rect x="19" y="42" width="26" height="6" rx="3" fill="#fff" opacity="0.85" />
          </svg>
        </div>
        <div class="brand-text">
          <span class="brand-name">ERP 单据管理</span>
          <span class="brand-role">{{ roleLabel }}</span>
        </div>
      </div>

      <nav class="menu" aria-label="主导航">
        <template v-if="auth.menus && auth.menus.length">
          <div
            v-for="group in auth.menus"
            :key="group.id ?? group.name"
            class="menu-group"
          >
            <div
              class="menu-group-head"
              :class="{ active: isGroupActive(group) }"
              @click="toggleGroup(group)"
            >
              <span class="menu-group-name">{{ group.name }}</span>
              <el-icon v-if="group.children?.length" class="caret" :class="{ open: isExpanded(group.name) }">
                <ArrowRight />
              </el-icon>
            </div>

            <transition name="sub">
              <div
                v-if="!group.children?.length || isExpanded(group.name)"
                class="menu-sub"
              >
                <template v-if="group.children?.length">
                  <div
                    v-for="leaf in group.children"
                    :key="leaf.id ?? leaf.name"
                    class="menu-leaf"
                    :class="{ active: route.path === leaf.path }"
                    @click="go(leaf)"
                  >
                    <span class="menu-leaf-dot" aria-hidden="true"></span>
                    <span class="menu-leaf-name">{{ leaf.name }}</span>
                  </div>
                </template>
                <div v-else class="menu-leaf" :class="{ active: route.path === group.path }" @click="go(group)">
                  <span class="menu-leaf-dot" aria-hidden="true"></span>
                  <span class="menu-leaf-name">{{ group.name }}</span>
                </div>
              </div>
            </transition>
          </div>
        </template>

        <div v-else class="menu-empty">
          <el-skeleton :rows="5" animated />
        </div>
      </nav>
    </aside>

    <!-- 主区域 -->
    <div class="main">
      <header class="topbar glass">
        <div class="topbar-title">
          <span class="page-title">{{ pageTitle }}</span>
        </div>
        <div class="topbar-user">
          <div class="user-chip">
            <div class="avatar">{{ avatarText }}</div>
            <div class="user-meta">
              <span class="nickname">{{ auth.user?.nickname || auth.user?.username || '用户' }}</span>
              <span class="role-tag" :class="isAdmin ? 'admin' : ''">
                {{ roleLabel }}
              </span>
            </div>
          </div>
          <el-tooltip content="退出登录" placement="bottom">
            <button class="icon-btn" @click="logout">
              <el-icon><SwitchButton /></el-icon>
            </button>
          </el-tooltip>
        </div>
      </header>

      <main class="content">
        <router-view v-slot="{ Component }">
          <transition name="page" mode="out-in">
            <component :is="Component" />
          </transition>
        </router-view>
      </main>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessageBox } from 'element-plus'
import { ArrowRight, SwitchButton } from '@element-plus/icons-vue'
import { getMenuMine } from '@/api'
import { useAuth, setMenus, clearAuth, flattenMenus, isAdmin } from '@/stores/auth'

const EXPAND_KEY = 'erp_menu_expanded'

const route = useRoute()
const router = useRouter()
const auth = useAuth()

const expanded = ref(readExpanded())
const isAdminFlag = computed(() => isAdmin())

const roleLabel = computed(() => (isAdminFlag.value ? '管理员' : '普通用户'))
const avatarText = computed(() => {
  const name = auth.user?.nickname || auth.user?.username || '用'
  return name.slice(0, 1).toUpperCase()
})

function readExpanded() {
  try {
    return JSON.parse(localStorage.getItem(EXPAND_KEY) || '[]')
  } catch (e) {
    return []
  }
}
function persistExpanded() {
  localStorage.setItem(EXPAND_KEY, JSON.stringify(expanded.value))
}

function isExpanded(name) {
  return expanded.value.includes(name)
}

function toggleGroup(group) {
  if (!group.children?.length) {
    go(group)
    return
  }
  const i = expanded.value.indexOf(group.name)
  if (i >= 0) expanded.value.splice(i, 1)
  else expanded.value.push(group.name)
  persistExpanded()
}

function go(leaf) {
  if (leaf.path) router.push(leaf.path)
}

function isGroupActive(group) {
  return group.children?.some((leaf) => route.path === leaf.path)
}

const pageTitle = computed(() => {
  const flat = flattenMenus(auth.menus)
  const hit = flat.find((m) => m.path && route.path.startsWith(m.path))
  return hit?.name || route.meta.title || '工作台'
})

// 保证当前路由所在分组展开
function ensureActiveExpanded() {
  ;(auth.menus || []).forEach((group) => {
    if (group.children?.some((leaf) => leaf.path && route.path.startsWith(leaf.path))) {
      if (!expanded.value.includes(group.name)) {
        expanded.value.push(group.name)
        persistExpanded()
      }
    }
  })
}

async function loadMenu() {
  try {
    const menus = await getMenuMine()
    setMenus(menus)
    ensureActiveExpanded()
  } catch (e) {
    // 已缓存菜单则继续使用
    if (!auth.menus) clearAuth()
  }
}

async function logout() {
  try {
    await ElMessageBox.confirm('确定要退出登录吗？', '退出登录', {
      confirmButtonText: '退出',
      cancelButtonText: '取消',
      type: 'warning',
    })
  } catch (e) {
    return
  }
  clearAuth()
  router.replace('/login')
}

onMounted(() => {
  loadMenu()
})

watch(
  () => route.path,
  () => ensureActiveExpanded()
)
</script>

<style scoped lang="scss">
@use '@/styles/variables' as *;

.layout {
  position: relative;
  display: flex;
  height: 100%;
  overflow: hidden;
}

.sidebar {
  width: $sidebar-width;
  flex-shrink: 0;
  margin: 14px;
  margin-right: 0;
  border-radius: $radius-lg;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.sidebar-brand {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 20px 18px 16px;

  .brand-mark {
    width: 42px;
    height: 42px;
    flex-shrink: 0;
    display: flex;
    align-items: center;
    justify-content: center;
    border-radius: 12px;
    background: rgba(255, 255, 255, 0.72);
    box-shadow:
      inset 0 1px 0 rgba(255, 255, 255, 0.9),
      0 10px 24px rgba(10, 132, 255, 0.16);
  }

  .brand-text {
    display: flex;
    flex-direction: column;
    line-height: 1.3;
  }
  .brand-name {
    font-size: 15px;
    font-weight: 700;
    color: $color-text-primary;
    letter-spacing: 0.02em;
  }
  .brand-role {
    font-size: 12px;
    color: $color-text-tertiary;
  }
}

.menu {
  flex: 1;
  overflow-y: auto;
  padding: 6px 12px 16px;

  .menu-group {
    margin-bottom: 4px;
  }

  .menu-group-head {
    display: flex;
    align-items: center;
    justify-content: space-between;
    height: 40px;
    padding: 0 12px;
    border-radius: 10px;
    cursor: pointer;
    font-size: 13px;
    font-weight: 600;
    color: $color-text-secondary;
    transition: all 0.25s $ease-apple;

    &:hover {
      background: rgba(255, 255, 255, 0.6);
      color: $color-text-primary;
    }
    &.active {
      color: $color-primary;
    }

    .caret {
      font-size: 12px;
      transition: transform 0.25s $ease-apple;
      &.open {
        transform: rotate(90deg);
      }
    }
  }

  .menu-sub {
    overflow: hidden;
  }

  .menu-leaf {
    display: flex;
    align-items: center;
    gap: 10px;
    height: 38px;
    padding: 0 12px 0 22px;
    border-radius: 10px;
    cursor: pointer;
    font-size: 13px;
    color: $color-text-secondary;
    transition: all 0.25s $ease-apple;

    &:hover {
      background: rgba(255, 255, 255, 0.6);
      color: $color-text-primary;
    }

    &.active {
      background: linear-gradient(135deg, rgba(10, 132, 255, 0.14), rgba(10, 132, 255, 0.08));
      color: $color-primary;
      font-weight: 600;

      .menu-leaf-dot {
        background: $color-primary;
      }
    }

    .menu-leaf-dot {
      width: 5px;
      height: 5px;
      border-radius: 50%;
      background: rgba(20, 24, 36, 0.2);
      flex-shrink: 0;
      transition: background 0.25s;
    }
  }

  .menu-empty {
    padding: 12px;
  }
}

.main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.topbar {
  height: $topbar-height;
  margin: 14px 14px 0 18px;
  border-radius: $radius-md;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px 0 20px;
  flex-shrink: 0;
}

.topbar-title {
  .page-title {
    font-size: 16px;
    font-weight: 700;
    color: $color-text-primary;
    letter-spacing: 0.01em;
  }
}

.topbar-user {
  display: flex;
  align-items: center;
  gap: 14px;

  .user-chip {
    display: flex;
    align-items: center;
    gap: 10px;

    .avatar {
      width: 34px;
      height: 34px;
      border-radius: 50%;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 14px;
      font-weight: 600;
      color: #fff;
      background: linear-gradient(135deg, #0a84ff, #4aa8ff);
      box-shadow: 0 6px 16px rgba(10, 132, 255, 0.3);
    }

    .user-meta {
      display: flex;
      flex-direction: column;
      line-height: 1.25;
    }
    .nickname {
      font-size: 13px;
      font-weight: 600;
      color: $color-text-primary;
    }
    .role-tag {
      font-size: 11px;
      color: $color-text-tertiary;
      &.admin {
        color: $color-primary;
      }
    }
  }

  .icon-btn {
    width: 36px;
    height: 36px;
    border: none;
    border-radius: 10px;
    background: rgba(255, 255, 255, 0.55);
    color: $color-text-secondary;
    cursor: pointer;
    display: flex;
    align-items: center;
    justify-content: center;
    font-size: 17px;
    transition: all 0.25s $ease-apple;

    &:hover {
      background: rgba(255, 59, 48, 0.1);
      color: $color-danger;
    }
  }
}

.content {
  flex: 1;
  min-height: 0;
  padding: 16px 18px 18px;
  overflow-y: auto;
}

// 路由切换过渡
.page-enter-active,
.page-leave-active {
  transition: opacity 0.22s $ease-apple, transform 0.22s $ease-apple;
}
.page-enter-from {
  opacity: 0;
  transform: translateY(8px);
}
.page-leave-to {
  opacity: 0;
  transform: translateY(-6px);
}

// 子菜单展开收起
.sub-enter-active,
.sub-leave-active {
  transition: opacity 0.22s $ease-apple;
}
.sub-enter-from,
.sub-leave-to {
  opacity: 0;
}
</style>
