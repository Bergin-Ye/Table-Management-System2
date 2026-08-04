// ============================================================
// 路由：hash 模式，避免刷新 404（无需服务端 SPA 回退配置）
// ============================================================
import { createRouter, createWebHashHistory } from 'vue-router'
import { getToken } from '@/stores/auth'
import MainLayout from '@/layouts/MainLayout.vue'
import Login from '@/views/Login.vue'
import HomeView from '@/views/HomeView.vue'
import DynamicView from '@/views/DynamicView.vue'

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: Login,
      meta: { public: true, title: '登录' },
    },
    {
      path: '/',
      component: MainLayout,
      redirect: '/home',
      children: [
        { path: 'home', name: 'home', component: HomeView, meta: { title: '工作台' } },
        // 其余路径（单据页 / 系统管理页）统一由 DynamicView 按菜单解析
        { path: ':pathMatch(.*)*', name: 'dynamic', component: DynamicView },
      ],
    },
  ],
})

router.beforeEach((to) => {
  const token = getToken()
  if (!to.meta.public && !token) {
    return { path: '/login', query: to.fullPath !== '/' ? { redirect: to.fullPath } : {} }
  }
  if (to.path === '/login' && token) {
    return '/'
  }
  document.title = to.meta.title ? `${to.meta.title} · ERP 单据管理系统` : 'ERP 单据管理系统'
  return true
})

export default router
