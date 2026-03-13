import { createRouter, createWebHistory } from 'vue-router'
import DashboardView from '../views/DashboardView.vue'
import ModelsView from '../views/ModelsView.vue'
import RagView from '../views/RagView.vue'
import AgentView from '../views/AgentView.vue'

const routes = [
  { path: '/', redirect: '/dashboard' },
  { path: '/dashboard', component: DashboardView },
  { path: '/models', component: ModelsView },
  { path: '/rag', component: RagView },
  { path: '/agent', component: AgentView }
]

export default createRouter({
  history: createWebHistory(),
  routes
})
