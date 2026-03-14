import { createRouter, createWebHistory } from 'vue-router'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('@/views/LoginView.vue'),
      meta: { title: '登录' }
    },
    {
      path: '/',
      component: () => import('@/layouts/MainLayout.vue'),
      redirect: '/dashboard',
      children: [
        { path: 'dashboard', name: 'dashboard', component: () => import('@/views/DashboardView.vue'), meta: { title: '控制台' } },
        { path: 'chat', name: 'chat', component: () => import('@/views/ChatView.vue'), meta: { title: 'AI 助手' } },
        { path: 'rag', name: 'rag', component: () => import('@/views/RagView.vue'), meta: { title: '知识库' } },
        { path: 'gateway', name: 'gateway', component: () => import('@/views/GatewayView.vue'), meta: { title: '模型网关' } },
        { path: 'monitor', name: 'monitor', component: () => import('@/views/MonitorView.vue'), meta: { title: '监控告警' } },
        { path: 'users', name: 'users', component: () => import('@/views/UserView.vue'), meta: { title: '权限管理' } }
      ]
    }
  ]
})

router.beforeEach((to, _from, next) => {
  const token = localStorage.getItem('auth_token')
  if (to.name !== 'login' && !token) {
    next({ name: 'login' })
  } else if (to.name === 'login' && token) {
    next({ name: 'dashboard' })
  } else {
    next()
  }
})

export default router
