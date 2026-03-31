<template>
  <div class="dashboard-shell">
    <section class="dashboard-hero card">
      <div class="hero-copy">
        <div class="eyebrow">工作台</div>
        <h1 class="hero-title">从主任务开始，不再先看复杂看板</h1>
        <p class="hero-subtitle">
          这里保留最常用的入口和必要摘要。日常使用优先进入 AI 助手与知识库，管理信息只做轻量提示。
        </p>
      </div>
      <div class="hero-actions">
        <button class="btn btn-primary" type="button" @click="router.push('/chat')">进入 AI 助手</button>
        <button class="btn btn-ghost" type="button" @click="router.push('/rag')">进入知识库</button>
      </div>
    </section>

    <section class="dashboard-grid">
      <button class="workspace-card workspace-card-primary" type="button" @click="router.push('/chat')">
        <span class="workspace-icon">AI</span>
        <span class="workspace-kicker">高频入口</span>
        <span class="workspace-title">AI 助手</span>
        <span class="workspace-desc">继续会话、切换助手、直接开始提问。</span>
      </button>

      <button class="workspace-card" type="button" @click="router.push('/rag')">
        <span class="workspace-icon">KB</span>
        <span class="workspace-kicker">知识工作</span>
        <span class="workspace-title">知识库</span>
        <span class="workspace-desc">先选知识库，再进入详情页进行问答、上传与排查。</span>
      </button>

      <button class="workspace-card" type="button" @click="router.push('/learning')">
        <span class="workspace-icon">LC</span>
        <span class="workspace-kicker">沉淀内容</span>
        <span class="workspace-title">学习中心</span>
        <span class="workspace-desc">收藏有用回答、整理模板和个人笔记。</span>
      </button>
    </section>

    <section class="snapshot-grid">
      <div class="snapshot-card card">
        <div class="snapshot-label">知识库数量</div>
        <div class="snapshot-value">{{ ragStore.knowledgeBases.length }}</div>
        <div class="snapshot-subtitle">已加载的知识库入口</div>
      </div>

      <div class="snapshot-card card">
        <div class="snapshot-label">文档总数</div>
        <div class="snapshot-value">{{ totalDocuments }}</div>
        <div class="snapshot-subtitle">所有知识库中的文档汇总</div>
      </div>

      <div class="snapshot-card card">
        <div class="snapshot-label">系统状态</div>
        <div class="snapshot-value">{{ overview ? '在线' : '待加载' }}</div>
        <div class="snapshot-subtitle">这里只保留简要状态，不再默认展示复杂图表</div>
      </div>
    </section>

    <section v-if="isAdmin" class="admin-strip card">
      <div>
        <div class="card-title">管理员入口</div>
        <div class="card-subtitle">只有需要时再进入监控、网关和权限管理。</div>
      </div>
      <div class="admin-actions">
        <button class="btn btn-ghost" type="button" @click="router.push('/monitor')">运行监控</button>
        <button class="btn btn-ghost" type="button" @click="router.push('/gateway')">模型网关</button>
        <button class="btn btn-ghost" type="button" @click="router.push('/users')">用户权限</button>
      </div>
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import { useMonitorStore } from '@/stores/monitor'
import { useRagStore } from '@/stores/rag'

const router = useRouter()
const authStore = useAuthStore()
const monitorStore = useMonitorStore()
const ragStore = useRagStore()

const isAdmin = computed(() => (authStore.roles || '').includes('ROLE_ADMIN'))
const overview = computed(() => monitorStore.overview)
const totalDocuments = computed(() =>
  ragStore.knowledgeBases.reduce((sum, kb) => sum + (kb.documentCount ?? 0), 0)
)

onMounted(async () => {
  await Promise.all([
    ragStore.loadKnowledgeBases(),
    monitorStore.loadDashboardData()
  ])
})
</script>

<style scoped>
.dashboard-shell {
  display: grid;
  gap: 18px;
}

.dashboard-hero {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  gap: 18px;
  padding: 24px;
  border-radius: 28px;
  background:
    radial-gradient(circle at top left, rgba(59, 130, 246, 0.16), transparent 30%),
    radial-gradient(circle at top right, rgba(16, 185, 129, 0.12), transparent 28%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.05), rgba(255, 255, 255, 0.02));
}

.hero-copy {
  max-width: 760px;
}

.eyebrow {
  color: var(--text3);
  font-size: 12px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.hero-title {
  margin: 8px 0 0;
  color: var(--text);
  font-size: 34px;
  line-height: 1.1;
}

.hero-subtitle {
  margin: 12px 0 0;
  color: var(--text2);
  font-size: 14px;
  line-height: 1.8;
}

.hero-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.dashboard-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.workspace-card {
  display: grid;
  gap: 8px;
  padding: 22px;
  border-radius: 24px;
  border: 1px solid var(--border);
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.045), rgba(255, 255, 255, 0.02));
  text-align: left;
  cursor: pointer;
  transition: transform var(--transition), border-color var(--transition), box-shadow var(--transition);
}

.workspace-card:hover {
  transform: translateY(-2px);
  border-color: rgba(59, 130, 246, 0.24);
  box-shadow: 0 16px 28px rgba(15, 23, 42, 0.1);
}

.workspace-card-primary {
  border-color: rgba(59, 130, 246, 0.24);
  background:
    radial-gradient(circle at top right, rgba(59, 130, 246, 0.16), transparent 38%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.06), rgba(255, 255, 255, 0.02));
}

.workspace-icon {
  width: 42px;
  height: 42px;
  border-radius: 14px;
  display: grid;
  place-items: center;
  background: rgba(255, 255, 255, 0.06);
  color: var(--text);
  font-size: 13px;
  font-weight: 700;
}

.workspace-kicker {
  color: var(--text3);
  font-size: 11px;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.workspace-title {
  color: var(--text);
  font-size: 20px;
  font-weight: 700;
}

.workspace-desc {
  color: var(--text2);
  font-size: 13px;
  line-height: 1.7;
}

.snapshot-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.snapshot-card {
  padding: 18px;
  border-radius: 22px;
}

.snapshot-label {
  color: var(--text3);
  font-size: 12px;
}

.snapshot-value {
  margin-top: 10px;
  color: var(--text);
  font-size: 26px;
  font-weight: 700;
}

.snapshot-subtitle {
  margin-top: 8px;
  color: var(--text3);
  font-size: 12px;
  line-height: 1.6;
}

.admin-strip {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  padding: 18px;
}

.admin-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

@media (max-width: 960px) {
  .dashboard-hero,
  .admin-strip {
    flex-direction: column;
    align-items: flex-start;
  }

  .dashboard-grid,
  .snapshot-grid {
    grid-template-columns: 1fr;
  }
}
</style>
