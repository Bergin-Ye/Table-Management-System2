// ============================================================
// 登录态 + 菜单 全局状态（localStorage 持久化）
// ============================================================
import { reactive } from 'vue'

const TOKEN_KEY = 'erp_token'
const USER_KEY = 'erp_user'
const MENU_KEY = 'erp_menu'

function readJson(key) {
  try {
    const raw = localStorage.getItem(key)
    return raw ? JSON.parse(raw) : null
  } catch (e) {
    return null
  }
}

const state = reactive({
  token: localStorage.getItem(TOKEN_KEY) || '',
  user: readJson(USER_KEY),
  menus: readJson(MENU_KEY), // 菜单树：{id,name,path,docType,children[]}
})

export function getToken() {
  return state.token
}

export function getUser() {
  return state.user
}

export function isAdmin() {
  return state.user?.role === 'ADMIN'
}

export function setAuth(token, user) {
  state.token = token
  state.user = user
  localStorage.setItem(TOKEN_KEY, token)
  localStorage.setItem(USER_KEY, JSON.stringify(user))
}

export function setMenus(menus) {
  state.menus = menus || []
  localStorage.setItem(MENU_KEY, JSON.stringify(state.menus))
}

export function clearAuth() {
  state.token = ''
  state.user = null
  state.menus = null
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_KEY)
  localStorage.removeItem(MENU_KEY)
}

// 扁平化菜单树 → 叶子节点数组
export function flattenMenus(menus) {
  const flat = []
  const walk = (list) => {
    ;(list || []).forEach((m) => {
      flat.push(m)
      if (m.children && m.children.length) walk(m.children)
    })
  }
  walk(menus)
  return flat
}

// 取第一个可导航的叶子菜单（父菜单虽有 path 但不可导航）
export function firstLeaf(menus) {
  const flat = flattenMenus(menus || state.menus)
  return flat.find((m) => m.path && (!m.children || m.children.length === 0)) || null
}

export function useAuth() {
  return state
}
