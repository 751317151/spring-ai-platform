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
      redirect: '/chat',
      children: [
        {
          path: 'dashboard',
          name: 'dashboard',
          component: () => import('@/views/DashboardView.vue'),
          meta: { title: '工作台' }
        },
        {
          path: 'chat',
          name: 'chat',
          component: () => import('@/views/ChatView.vue'),
          meta: { title: 'AI 助手' }
        },
        {
          path: 'rag',
          name: 'rag',
          component: () => import('@/views/RagView.vue'),
          meta: { title: '知识库' }
        },
        {
          path: 'rag/:kbId',
          name: 'rag-detail',
          component: () => import('@/views/RagDetailView.vue'),
          meta: { title: '知识库详情' }
        },
        {
          path: 'learning',
          name: 'learning',
          component: () => import('@/views/LearningCenterView.vue'),
          meta: { title: '学习中心' }
        },
        {
          path: 'agents',
          name: 'agents',
          component: () => import('@/views/AgentWorkbenchView.vue'),
          meta: { title: 'Agent 工作台', requiresAdmin: true }
        },
        {
          path: 'gateway',
          name: 'gateway',
          component: () => import('@/views/GatewayView.vue'),
          meta: { title: '模型网关', requiresAdmin: true }
        },
        {
          path: 'monitor',
          name: 'monitor',
          component: () => import('@/views/MonitorView.vue'),
          meta: { title: '运行监控', requiresAdmin: true, allowGuest: true }
        },
        {
          path: 'screen',
          name: 'screen',
          component: () => import('@/views/ScreenView.vue'),
          meta: { title: '大屏指挥台', requiresAdmin: true, allowGuest: true }
        },
        {
          path: 'mcp',
          name: 'mcp',
          component: () => import('@/views/McpView.vue'),
          meta: { title: 'MCP 管理', requiresAdmin: true }
        },
        {
          path: 'users',
          name: 'users',
          component: () => import('@/views/UserView.vue'),
          meta: { title: '用户与权限', requiresAdmin: true }
        }
      ]
    }
  ]
})

router.beforeEach((to, _from, next) => {
  const token = localStorage.getItem('auth_token')
  const authMode = localStorage.getItem('auth_mode') || 'live'
  const isGuest = authMode === 'guest'

  if (to.name !== 'login' && !token) {
    next({
      name: 'login',
      query: {
        redirect: to.fullPath
      }
    })
    return
  }

  if (to.name === 'login' && token) {
    const redirect = typeof to.query.redirect === 'string' ? to.query.redirect : ''
    next(redirect || { name: 'chat' })
    return
  }

  if (to.meta?.requiresAdmin) {
    const roles = localStorage.getItem('auth_roles') || ''
    const allowGuest = Boolean(to.meta?.allowGuest)
    const hasAdminRole = roles.includes('ROLE_ADMIN')
    if (!hasAdminRole && !(allowGuest && isGuest)) {
      next({ name: 'chat' })
      return
    }
  }

  next()
})

export default router
