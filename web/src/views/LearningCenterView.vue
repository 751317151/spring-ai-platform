<template>
  <div class="space-admin">
    <div class="page-hero">
      <div class="page-hero-main">
        <div class="eyebrow">个人学习中心</div>
        <div class="page-title">收藏、笔记与会话搜索</div>
        <div class="page-subtitle">
          把聊天中的高价值内容沉淀成可复用的个人资料库，并支持按标签、时间和关键词快速回找。
        </div>
        <div class="hero-tags">
          <span class="tag">{{ favorites.length }} 条收藏</span>
          <span class="tag">{{ notes.length }} 篇笔记</span>
          <span class="tag">{{ visibleLocalSearchResults.length }} 条本地命中</span>
          <span class="tag">{{ searchResults.length }} 条会话搜索结果</span>
          <span class="tag">{{ selectedFavoriteIds.length }} 条已选中</span>
        </div>
      </div>
    </div>

    <section class="card learning-section local-search-section">
      <div class="card-header">
        <div>
          <div class="card-title">学习资料检索</div>
          <div class="card-subtitle">优先在本地收藏和学习笔记里全文检索，快速回找你已经沉淀下来的内容。</div>
        </div>
      </div>

      <div class="search-toolbar">
        <input
          v-model.trim="localSearchKeyword"
          class="form-input"
          type="text"
          placeholder="搜索收藏、笔记、标签或会话标题，例如：复盘、登录、CORS"
        />
        <select v-model="localSearchType" class="form-input search-select">
          <option value="all">全部类型</option>
          <option value="favorite">仅收藏</option>
          <option value="note">仅笔记</option>
        </select>
        <select v-model="localSearchSort" class="form-input search-select">
          <option value="relevance">按相关度</option>
          <option value="latest">按时间</option>
        </select>
      </div>

      <div v-if="localSearchKeyword && visibleLocalSearchResults.length" class="learning-list">
        <article v-for="item in visibleLocalSearchResults" :key="`${item.type}-${item.id}`" class="learning-card">
          <div class="learning-card-head">
            <div>
              <div class="learning-card-title">{{ item.title }}</div>
              <div class="learning-card-meta">{{ item.meta }}</div>
            </div>
            <div class="learning-actions">
              <button
                v-if="item.type === 'favorite'"
                class="btn btn-ghost btn-sm"
                @click="openFavoriteSearchItem(item.id)"
              >
                打开会话
              </button>
              <button
                v-if="item.type === 'note'"
                class="btn btn-ghost btn-sm"
                @click="selectNote(item.id)"
              >
                打开笔记
              </button>
              <button
                v-if="item.type === 'note' && item.relatedSessionId && item.relatedAgentType"
                class="btn btn-primary btn-sm"
                @click="openNoteSession(item.id)"
              >
                回到来源会话
              </button>
              <button
                v-if="item.relatedSessionId && item.relatedAgentType"
                class="btn btn-ghost btn-sm local-source-btn"
                @click="openLocalSearchSource(item)"
              >
                打开来源
              </button>
              <button
                v-if="item.relatedSessionId && item.relatedAgentType"
                class="btn btn-ghost btn-sm"
                @click="appendLocalSearchToDraft(item)"
              >
                加入追问
              </button>
              <button
                v-if="item.relatedSessionId && item.relatedAgentType"
                class="btn btn-ghost btn-sm"
                @click="continueFromLocalSearch(item)"
              >
                继续追问
              </button>
            </div>
          </div>
          <div v-if="buildLocalSearchSourceSummary(item)" class="learning-source-line">
            {{ buildLocalSearchSourceSummary(item) }}
          </div>
          <div class="learning-card-content">{{ item.preview }}</div>
          <div v-if="item.tags.length" class="learning-card-tags">
            <span v-for="tag in item.tags" :key="tag" class="tag">{{ tag }}</span>
          </div>
        </article>
      </div>
      <EmptyState
        v-else
        title="还没有本地检索结果"
        :description="localSearchHint"
      />
    </section>

    <section class="card learning-section followup-draft-section">
      <div class="card-header">
        <div>
          <div class="card-title">追问草稿台</div>
          <div class="card-subtitle">把多条收藏、笔记和搜索结果组合成一份追问草稿，再一次性带到聊天页继续处理。</div>
        </div>
      </div>

      <div v-if="followUpDraftItems.length" class="followup-draft-list">
        <article v-for="item in followUpDraftItems" :key="item.id" class="followup-draft-item">
          <div class="followup-draft-head">
            <div>
              <div class="learning-card-title">{{ item.title }}</div>
              <div class="learning-source-line">{{ item.sourceLabel }}</div>
            </div>
            <button class="btn btn-ghost btn-sm" @click="removeFollowUpDraftItem(item.id)">移除</button>
          </div>
          <div class="learning-card-content">{{ item.content }}</div>
          <div class="learning-actions followup-draft-row-actions">
            <button class="btn btn-ghost btn-sm" :disabled="!canMoveFollowUpDraftItem(item.id, 'up')" @click="moveFollowUpDraftItem(item.id, 'up')">上移</button>
            <button class="btn btn-ghost btn-sm" :disabled="!canMoveFollowUpDraftItem(item.id, 'down')" @click="moveFollowUpDraftItem(item.id, 'down')">下移</button>
            <button class="btn btn-ghost btn-sm" @click="removeFollowUpDraftItem(item.id)">移除</button>
          </div>
        </article>
      </div>
      <EmptyState
        v-else
        title="还没有追问草稿"
        description="先从下方的收藏、笔记或搜索结果中加入资料，再统一发送到聊天页。"
      />

      <textarea
        v-if="followUpDraftItems.length"
        :value="followUpDraftText"
        class="note-textarea followup-draft-preview"
        rows="8"
        placeholder="可在这里编辑整合后的追问内容"
        @input="handleFollowUpDraftInput(($event.target as HTMLTextAreaElement).value)"
      ></textarea>
      <div v-if="followUpDraftItems.length" class="learning-actions followup-template-toolbar">
        <input
          v-model.trim="followUpTemplateName"
          class="form-input"
          type="text"
          placeholder="????????????????????????"
        />
        <button class="btn btn-ghost btn-sm" :disabled="!canSaveFollowUpTemplate" @click="saveFollowUpTemplate">?????</button>
      </div>
      <div v-if="followUpTemplates.length" class="followup-template-list">
        <article v-for="template in followUpTemplates" :key="template.id" class="followup-template-card">
          <div class="followup-template-head">
            <div>
              <div class="learning-card-title">{{ template.name }}</div>
              <div class="learning-card-meta">{{ formatTime(template.updatedAt) }} / {{ template.sourceCount }} ?????</div>
            </div>
            <div class="learning-actions">
              <button class="btn btn-ghost btn-sm" @click="applyFollowUpTemplate(template.id)">????</button>
              <button class="btn btn-ghost btn-sm" @click="removeFollowUpTemplate(template.id)">????</button>
            </div>
          </div>
          <div class="learning-card-content compact">{{ template.content }}</div>
        </article>
      </div>
      <div v-if="followUpDraftItems.length" class="followup-draft-meta">
        <div class="learning-actions followup-draft-actions">
          <button class="btn btn-primary btn-sm" :disabled="!canSubmitFollowUpDraft" @click="sendFollowUpDraftToChat">发送到聊天页</button>
          <button class="btn btn-ghost btn-sm" :disabled="!followUpDraftItems.length || !followUpDraftDirty" @click="resetFollowUpDraftText">恢复自动草稿</button>
          <button class="btn btn-ghost btn-sm" :disabled="!canSubmitFollowUpDraft" @click="saveFollowUpDraftAsNote">保存为笔记</button>
          <button class="btn btn-ghost btn-sm" :disabled="!followUpDraftItems.length" @click="clearFollowUpDraft">清空草稿</button>
        </div>
        <span>{{ followUpDraftDirty ? '已手动编辑' : '自动生成草稿' }}</span>
        <span>{{ followUpDraftItems.length }} 条资料</span>
      </div>
      <div class="learning-actions">
        <button class="btn btn-primary btn-sm" :disabled="!followUpDraftItems.length" @click="sendFollowUpDraftToChat">发送到聊天页</button>
        <button class="btn btn-ghost btn-sm" :disabled="!followUpDraftItems.length" @click="clearFollowUpDraft">清空草稿</button>
      </div>
    </section>

    <div class="learning-grid">
      <section class="card learning-section">
        <div class="card-header">
          <div>
            <div class="card-title">收藏消息</div>
            <div class="card-subtitle">从聊天里沉淀值得复用的回答、提示词和分析结论。</div>
          </div>
        </div>

        <div class="learning-toolbar">
          <div class="toolbar-block">
            <span class="toolbar-label">时间范围</span>
            <div class="chip-group">
              <button
                v-for="option in rangeOptions"
                :key="option.value"
                class="chip-btn"
                :class="{ active: favoriteRange === option.value }"
                @click="favoriteRange = option.value"
              >
                {{ option.label }}
              </button>
            </div>
          </div>
          <div class="toolbar-block">
            <span class="toolbar-label">标签过滤</span>
            <input
              v-model.trim="favoriteTagQuery"
              class="form-input"
              type="text"
              placeholder="输入标签或关键词过滤收藏"
            />
          </div>
        </div>

        <div class="learning-toolbar compact">
          <label class="check-inline">
            <input
              type="checkbox"
              :checked="allVisibleFavoritesSelected"
              :disabled="!filteredFavorites.length"
              @change="toggleSelectAllFavorites(($event.target as HTMLInputElement).checked)"
            />
            <span>全选当前筛选结果</span>
          </label>
          <div class="learning-actions">
            <button class="btn btn-ghost btn-sm" :disabled="!selectedFavoriteIds.length" @click="exportFavorites(false)">导出选中收藏</button>
            <button class="btn btn-ghost btn-sm" :disabled="!filteredFavorites.length" @click="exportFavorites(true)">导出当前收藏</button>
            <button class="btn btn-primary btn-sm" :disabled="!favorites.length && !notes.length" @click="exportLearningBundle">导出学习资料包</button>
          </div>
        </div>

        <div v-if="filteredFavorites.length" class="learning-list">
          <article v-for="item in filteredFavorites" :key="item.id" class="learning-card">
            <div class="learning-card-head">
              <div class="learning-card-main">
                <label class="check-inline">
                  <input
                    type="checkbox"
                    :checked="selectedFavoriteIds.includes(item.id)"
                    @change="toggleFavoriteSelection(item.id, ($event.target as HTMLInputElement).checked)"
                  />
                  <span class="learning-card-title">{{ item.sessionSummary || '未命名会话' }}</span>
                </label>
                <div class="learning-card-meta">
                  {{ item.agentType || 'chat' }} / {{ item.role === 'assistant' ? '助手' : '用户' }} / {{ formatTime(item.createdAt) }}
                </div>
                <div v-if="item.sessionId && item.agentType" class="learning-source-line">
                  {{ buildSourceSummary(item.sessionSummary, item.agentType, item.sourceMessageIndex ?? null) }}
                </div>
              </div>
              <div class="learning-actions">
                <button class="btn btn-ghost btn-sm" @click="createNoteFromFavorite(item)">转笔记</button>
                <button class="btn btn-ghost btn-sm" @click="openChat(item)">打开会话</button>
                <button class="btn btn-ghost btn-sm" @click="appendFavoriteToDraft(item)">加入追问</button>
                <button class="btn btn-ghost btn-sm" @click="continueFromFavorite(item)">继续追问</button>
                <button class="btn btn-ghost btn-sm" @click="removeFavorite(item.id)">移除</button>
              </div>
            </div>
            <div class="learning-card-content">{{ item.content }}</div>
            <div class="learning-card-tags">
              <span v-for="tag in item.tags || []" :key="tag" class="tag">{{ tag }}</span>
              <span v-if="favoriteConfigLabel(item)" class="tag">{{ favoriteConfigLabel(item) }}</span>
              <span v-if="(item.duplicateCount || 1) > 1" class="tag tag-warn">重复收藏 {{ item.duplicateCount }} 次</span>
            </div>
          </article>
        </div>
        <EmptyState
          v-else
          title="当前筛选条件下没有收藏"
          description="可以调整时间范围、标签过滤条件，或在聊天消息里继续收藏高价值内容。"
        />
      </section>

      <section class="card learning-section">
        <div class="card-header">
          <div>
            <div class="card-title">学习笔记</div>
            <div class="card-subtitle">把收藏内容整理成自己的长期笔记，可手动补充总结和标签。</div>
          </div>
          <button class="btn btn-primary btn-sm" @click="createEmptyNote">新建笔记</button>
        </div>

        <div class="learning-toolbar">
          <div class="toolbar-block">
            <span class="toolbar-label">时间范围</span>
            <div class="chip-group">
              <button
                v-for="option in rangeOptions"
                :key="`note-${option.value}`"
                class="chip-btn"
                :class="{ active: noteRange === option.value }"
                @click="noteRange = option.value"
              >
                {{ option.label }}
              </button>
            </div>
          </div>
          <div class="toolbar-block">
            <span class="toolbar-label">标签过滤</span>
            <input
              v-model.trim="noteTagQuery"
              class="form-input"
              type="text"
              placeholder="输入标签或关键词过滤笔记"
            />
          </div>
        </div>

        <div class="note-editor">
          <input v-model.trim="noteForm.title" class="form-input" type="text" placeholder="笔记标题" />
          <input v-model.trim="noteForm.tagsInput" class="form-input" type="text" placeholder="标签，使用逗号分隔，例如：登录、排查、RAG" />
          <textarea
            v-model="noteForm.content"
            class="note-textarea"
            rows="8"
            placeholder="记录结论、经验、坑点和后续计划"
          ></textarea>
          <div v-if="noteFormTags.length" class="learning-card-tags">
            <span v-for="tag in noteFormTags" :key="tag" class="tag">{{ tag }}</span>
          </div>
          <div class="learning-actions">
            <button class="btn btn-primary btn-sm" :disabled="!noteForm.title || !noteForm.content.trim()" @click="saveNote">
              保存笔记
            </button>
            <button class="btn btn-ghost btn-sm" :disabled="!selectedNoteId" @click="deleteNote">删除当前笔记</button>
            <button class="btn btn-ghost btn-sm" @click="resetNoteForm">清空编辑区</button>
          </div>
        </div>

        <div v-if="filteredNotes.length" class="learning-list note-list">
          <article
            v-for="item in filteredNotes"
            :key="item.id"
            class="learning-card note-card"
            :class="{ active: selectedNoteId === item.id }"
            @click="selectNote(item.id)"
          >
            <div class="learning-card-title">{{ item.title }}</div>
            <div class="learning-card-meta">{{ formatTime(item.updatedAt) }}</div>
            <div v-if="buildNoteSourceSummary(item)" class="learning-source-line">
              {{ buildNoteSourceSummary(item) }}
            </div>
            <div v-if="item.tags?.length" class="learning-card-tags">
              <span v-for="tag in item.tags" :key="tag" class="tag">{{ tag }}</span>
            </div>
            <div class="learning-card-content">{{ item.content }}</div>
            <div v-if="item.relatedSessionId && item.relatedAgentType" class="learning-actions note-source-actions">
              <button class="btn btn-ghost btn-sm" @click.stop="openNoteSession(item.id)">回到来源会话</button>
              <button class="btn btn-ghost btn-sm" @click.stop="appendNoteToDraft(item)">加入追问</button>
              <button class="btn btn-ghost btn-sm" @click.stop="continueFromNote(item)">继续追问</button>
            </div>
          </article>
        </div>
        <EmptyState
          v-else
          title="当前筛选条件下没有笔记"
          description="可以新建笔记，或把收藏、搜索结果直接沉淀为带标签的学习笔记。"
        />
      </section>
    </div>

    <section class="card learning-section search-section">
      <div class="card-header">
        <div>
          <div class="card-title">会话搜索</div>
          <div class="card-subtitle">按关键词搜索历史会话标题和消息内容，适合回找以前聊过的结论。</div>
        </div>
      </div>

      <div class="search-toolbar">
        <input
          v-model.trim="searchKeyword"
          class="form-input"
          type="text"
          placeholder="输入关键词，例如：CORS、登录跳转、知识库、提示词"
          @keydown.enter.prevent="runSearch"
        />
        <button class="btn btn-primary" :disabled="searching || !searchKeyword" @click="runSearch">
          {{ searching ? '搜索中...' : '开始搜索' }}
        </button>
      </div>

      <div v-if="searchResults.length" class="learning-list">
        <article v-for="item in searchResults" :key="`${item.agentType}-${item.sessionId}-${item.matchedContent}`" class="learning-card">
          <div class="learning-card-head">
            <div>
              <div class="learning-card-title">{{ item.sessionSummary || '未命名会话' }}</div>
              <div class="learning-card-meta">
                {{ item.agentType }} / {{ item.matchedRole === 'assistant' ? '助手回复' : '用户提问' }}
              </div>
              <div class="learning-source-line">
                {{ buildSearchResultMeta(item) }}
              </div>
            </div>
            <div class="learning-actions">
              <button class="btn btn-ghost btn-sm" @click="saveSearchResultAsNote(item)">记为笔记</button>
              <button class="btn btn-primary btn-sm" @click="openSearchResult(item)">打开会话</button>
              <button class="btn btn-ghost btn-sm" @click="appendSearchResultToDraft(item)">加入追问</button>
              <button class="btn btn-ghost btn-sm" @click="continueFromSearchResult(item)">继续追问</button>
            </div>
          </div>
          <div v-if="item.excerpt" class="search-result-excerpt">
            {{ item.excerpt }}
          </div>
          <div class="learning-card-content" :class="{ compact: !isSearchResultExpanded(item) }">
            {{ item.matchedContent }}
          </div>
          <div v-if="item.contextBefore || item.contextAfter" class="search-result-context">
            <button class="btn btn-ghost btn-sm" @click="toggleSearchResultExpanded(item)">
              {{ isSearchResultExpanded(item) ? '收起上下文' : '展开上下文' }}
            </button>
            <div v-if="isSearchResultExpanded(item)" class="search-result-context-body">
              <div v-if="item.contextBefore" class="search-result-context-line">
                <span class="search-result-context-label">前文</span>
                <span>{{ item.contextBefore }}</span>
              </div>
              <div class="search-result-context-line active">
                <span class="search-result-context-label">命中</span>
                <span>{{ item.matchedContent }}</span>
              </div>
              <div v-if="item.contextAfter" class="search-result-context-line">
                <span class="search-result-context-label">后文</span>
                <span>{{ item.contextAfter }}</span>
              </div>
            </div>
          </div>
          <div class="learning-card-tags">
            <span v-for="tag in buildSearchResultTags(item)" :key="tag" class="tag">{{ tag }}</span>
          </div>
        </article>
      </div>
      <EmptyState
        v-else
        title="还没有搜索结果"
        :description="searchHint"
      />
    </section>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import * as agentApi from '@/api/agent'
import * as authApi from '@/api/auth'
import type { FavoriteMessageRecord, LearningNoteRecord, SessionSearchResult } from '@/api/types'
import EmptyState from '@/components/common/EmptyState.vue'
import { useToast } from '@/composables/useToast'
import { AGENT_CONFIG } from '@/utils/constants'
import { listFavoriteMessages, listLearningNotes, normalizeTags, removeFavoriteMessage, removeLearningNote, saveLearningNote } from '@/utils/learning'

type RangeValue = 'all' | '7d' | '30d' | '90d'

interface LocalLearningSearchItem {
  id: string
  type: 'favorite' | 'note'
  title: string
  preview: string
  meta: string
  updatedAt: number
  searchScore: number
  tags: string[]
  relatedSessionId?: string | null
  relatedAgentType?: string | null
  relatedMessageIndex?: number | null
  relatedSessionSummary?: string | null
}

interface FollowUpDraftItem {
  id: string
  title: string
  sourceLabel: string
  content: string
  query: {
    agent: string
    session: string
    message?: string
  }
}

interface FollowUpTemplateRecord {
  id: string
  name: string
  content: string
  sourceCount: number
  updatedAt: number
}

const router = useRouter()
const { showToast } = useToast()
const FOLLOW_UP_TEMPLATES_KEY = 'learning_center_followup_templates'

const rangeOptions: Array<{ label: string; value: RangeValue }> = [
  { label: '全部', value: 'all' },
  { label: '7 天', value: '7d' },
  { label: '30 天', value: '30d' },
  { label: '90 天', value: '90d' }
]

const favorites = ref<FavoriteMessageRecord[]>([])
const notes = ref<LearningNoteRecord[]>([])
const selectedFavoriteIds = ref<string[]>([])
const favoriteRange = ref<RangeValue>('all')
const noteRange = ref<RangeValue>('all')
const favoriteTagQuery = ref('')
const noteTagQuery = ref('')
const localSearchKeyword = ref('')
const localSearchType = ref<'all' | 'favorite' | 'note'>('all')
const localSearchSort = ref<'relevance' | 'latest'>('relevance')
const selectedNoteId = ref<string | null>(null)
const noteForm = reactive({
  title: '',
  content: '',
  tagsInput: '',
  relatedFavoriteId: null as string | null,
  relatedSessionId: null as string | null,
  relatedAgentType: null as string | null,
  relatedSessionSummary: null as string | null,
  relatedMessageIndex: null as number | null,
  sourceType: 'manual' as LearningNoteRecord['sourceType']
})
const searchKeyword = ref('')
const searchResults = ref<SessionSearchResult[]>([])
const searching = ref(false)
const expandedSearchResultKeys = ref<string[]>([])
const followUpDraftItems = ref<FollowUpDraftItem[]>([])
const followUpDraftText = ref('')
const followUpDraftDirty = ref(false)
const lastAutoFollowUpDraftPrompt = ref('')
const followUpTemplateName = ref('')
const followUpTemplates = ref<FollowUpTemplateRecord[]>([])

const searchHint = computed(() => (
  searchKeyword.value
    ? '没有找到匹配内容，可以换一个更具体的关键词。'
    : '输入关键词后可搜索历史会话标题和消息内容。'
))

const localSearchHint = computed(() => (
  localSearchKeyword.value
    ? '本地收藏和笔记里没有命中结果，可以换个关键词或去会话搜索中继续查找。'
    : '输入关键词后，可直接搜索你已经沉淀到本地的收藏和学习笔记。'
))

const noteFormTags = computed(() => normalizeTags(noteForm.tagsInput))

const filteredFavorites = computed(() => favorites.value.filter((item) => {
  if (!matchRange(item.lastCollectedAt || item.createdAt, favoriteRange.value)) {
    return false
  }
  return matchTagQuery(
    favoriteTagQuery.value,
    item.tags,
    item.content,
    item.sessionSummary,
    item.agentType
  )
}))

const filteredNotes = computed(() => notes.value.filter((item) => {
  if (!matchRange(item.updatedAt, noteRange.value)) {
    return false
  }
  return matchTagQuery(
    noteTagQuery.value,
    item.tags,
    item.title,
    item.content,
    item.relatedSessionSummary || '',
    item.relatedAgentType || ''
  )
}))

const localSearchResults = computed<LocalLearningSearchItem[]>(() => {
  const keyword = localSearchKeyword.value.trim().toLowerCase()
  if (!keyword) {
    return []
  }

  const favoriteResults = favorites.value
    .filter((item) => [
      item.content,
      item.sessionSummary,
      item.agentType,
      ...(item.tags || [])
    ].join(' ').toLowerCase().includes(keyword))
    .map<LocalLearningSearchItem>((item) => ({
      id: item.id,
      type: 'favorite',
      title: item.sessionSummary || '未命名收藏',
      preview: item.content,
      meta: `收藏 / ${item.agentType || 'chat'} / ${formatTime(item.createdAt)}`,
      tags: item.tags || [],
      relatedSessionId: item.sessionId || null,
      relatedAgentType: item.agentType || null,
      relatedMessageIndex: item.sourceMessageIndex ?? null,
      relatedSessionSummary: item.sessionSummary || null
    }))

  const noteResults = notes.value
    .filter((item) => [
      item.title,
      item.content,
      item.relatedSessionSummary,
      item.relatedAgentType,
      ...(item.tags || [])
    ].join(' ').toLowerCase().includes(keyword))
    .map<LocalLearningSearchItem>((item) => ({
      id: item.id,
      type: 'note',
      title: item.title,
      preview: item.content,
      meta: `笔记 / ${formatTime(item.updatedAt)}`,
      tags: item.tags || [],
      relatedSessionId: item.relatedSessionId || null,
      relatedAgentType: item.relatedAgentType || null,
      relatedMessageIndex: item.relatedMessageIndex ?? null,
      relatedSessionSummary: item.relatedSessionSummary || null
    }))

  return [...noteResults, ...favoriteResults]
})

const visibleLocalSearchResults = computed<LocalLearningSearchItem[]>(() => {
  const keyword = localSearchKeyword.value.trim().toLowerCase()
  const enriched = localSearchResults.value
    .map((item) => ({
      ...item,
      updatedAt: item.type === 'favorite'
        ? favorites.value.find((entry) => entry.id === item.id)?.lastCollectedAt
          || favorites.value.find((entry) => entry.id === item.id)?.createdAt
          || 0
        : notes.value.find((entry) => entry.id === item.id)?.updatedAt || 0,
      searchScore: countKeywordMatches([
        item.title,
        item.preview,
        item.meta,
        item.relatedSessionSummary,
        ...(item.tags || [])
      ], keyword)
    }))
    .filter((item) => localSearchType.value === 'all' || item.type === localSearchType.value)

  if (localSearchSort.value === 'latest') {
    return enriched.sort((left, right) => right.updatedAt - left.updatedAt)
  }

  return enriched.sort((left, right) => {
    if (right.searchScore !== left.searchScore) {
      return right.searchScore - left.searchScore
    }
    return right.updatedAt - left.updatedAt
  })
})

const allVisibleFavoritesSelected = computed(() => (
  Boolean(filteredFavorites.value.length)
  && filteredFavorites.value.every((item) => selectedFavoriteIds.value.includes(item.id))
))
const followUpDraftPrompt = computed(() => {
  if (!followUpDraftItems.value.length) {
    return ''
  }
  return [
    '请基于以下多条学习资料继续推进，并输出整合后的下一步建议：',
    '',
    ...followUpDraftItems.value.flatMap((item, index) => [
      `${index + 1}. ${item.title}`,
      `来源：${item.sourceLabel}`,
      item.content.trim(),
      ''
    ])
  ].join('\n').trim()
})
const canSubmitFollowUpDraft = computed(() => (
  Boolean(followUpDraftItems.value.length) && Boolean(followUpDraftText.value.trim())
))
const canSaveFollowUpTemplate = computed(() => (
  Boolean(followUpDraftItems.value.length)
  && Boolean(followUpDraftText.value.trim())
  && Boolean(followUpTemplateName.value.trim())
))

watch(followUpDraftItems, (items) => {
  if (!items.length) {
    followUpTemplateName.value = ''
    return
  }
  if (!followUpTemplateName.value.trim()) {
    followUpTemplateName.value = `追问模板 ${new Date().toLocaleDateString('zh-CN')}`
  }
}, { deep: true })

watch(followUpDraftPrompt, (value) => {
  if (!value) {
    followUpDraftText.value = ''
    followUpDraftDirty.value = false
    lastAutoFollowUpDraftPrompt.value = ''
    return
  }

  const shouldSync = !followUpDraftDirty.value
    || !followUpDraftText.value.trim()
    || followUpDraftText.value === lastAutoFollowUpDraftPrompt.value

  lastAutoFollowUpDraftPrompt.value = value
  if (shouldSync) {
    followUpDraftText.value = value
    followUpDraftDirty.value = false
  }
}, { immediate: true })

function reloadLearningData() {
  favorites.value = listFavoriteMessages()
  notes.value = listLearningNotes()
  selectedFavoriteIds.value = selectedFavoriteIds.value.filter((id) => favorites.value.some((item) => item.id === id))
  followUpTemplates.value = readFollowUpTemplates()
}

function readFollowUpTemplates() {
  try {
    const raw = localStorage.getItem(FOLLOW_UP_TEMPLATES_KEY)
    return raw ? JSON.parse(raw) as FollowUpTemplateRecord[] : []
  } catch {
    return []
  }
}

function persistFollowUpTemplates(value: FollowUpTemplateRecord[]) {
  localStorage.setItem(FOLLOW_UP_TEMPLATES_KEY, JSON.stringify(value.slice(0, 20)))
}

function matchRange(timestamp: number, range: RangeValue) {
  if (range === 'all') {
    return true
  }
  const dayCount = range === '7d' ? 7 : range === '30d' ? 30 : 90
  return Date.now() - timestamp <= dayCount * 24 * 60 * 60 * 1000
}

function matchTagQuery(query: string, tags?: string[], ...texts: Array<string | undefined>) {
  if (!query) {
    return true
  }
  const keyword = query.trim().toLowerCase()
  const source = [...(tags || []), ...texts.filter(Boolean) as string[]]
    .join(' ')
    .toLowerCase()
  return source.includes(keyword)
}

function countKeywordMatches(values: Array<string | undefined | null>, keyword: string) {
  if (!keyword) {
    return 0
  }
  const target = values
    .filter(Boolean)
    .join(' ')
    .toLowerCase()
  return target.split(keyword).length - 1
}

function formatTime(value: number) {
  return new Date(value).toLocaleString('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  })
}

function favoriteConfigLabel(item: FavoriteMessageRecord) {
  if (!item.sessionConfigSnapshot?.model) {
    return ''
  }
  return `模型 ${item.sessionConfigSnapshot.model}`
}

function buildSourceSummary(sessionSummary?: string | null, agentType?: string | null, messageIndex?: number | null) {
  const sessionLabel = sessionSummary || '未命名会话'
  const agentLabel = agentType || 'chat'
  const messageLabel = messageIndex !== null && messageIndex !== undefined
    ? `消息 #${messageIndex + 1}`
    : '会话级来源'
  return `${sessionLabel} / ${agentLabel} / ${messageLabel}`
}

function buildFavoriteMeta(item: FavoriteMessageRecord) {
  const parts = [
    item.agentType || 'chat',
    item.role === 'assistant' ? '助手' : '用户',
    formatTime(item.createdAt)
  ]
  if (item.sourceMessageIndex !== null && item.sourceMessageIndex !== undefined) {
    parts.push(`消息 #${item.sourceMessageIndex + 1}`)
  }
  return parts.join(' / ')
}

function buildLocalSearchSourceSummary(item: LocalLearningSearchItem) {
  if (!item.relatedSessionId || !item.relatedAgentType) {
    return ''
  }
  return buildSourceSummary(item.relatedSessionSummary, item.relatedAgentType, item.relatedMessageIndex)
}

function buildNoteSourceSummary(item: LearningNoteRecord) {
  if (!item.relatedSessionId || !item.relatedAgentType) {
    return ''
  }
  return buildSourceSummary(item.relatedSessionSummary, item.relatedAgentType, item.relatedMessageIndex)
}

function favoriteToNoteTags(item: FavoriteMessageRecord) {
  return normalizeTags([...(item.tags || []), item.agentType || '', '收藏'])
}

function buildSearchResultTags(item: SessionSearchResult) {
  return normalizeTags([
    item.agentType,
    item.matchedRole === 'assistant' ? '助手回复' : '用户提问',
    item.matchedField === 'title' ? '标题命中' : '消息命中',
    item.additionalMatchCount ? `额外命中 ${item.additionalMatchCount} 处` : '',
    '搜索结果'
  ])
}

function buildSearchResultMeta(item: SessionSearchResult) {
  const parts = [
    item.agentType,
    item.matchedRole === 'assistant' ? '助手回复' : '用户提问'
  ]
  if (item.matchedField === 'title') {
    parts.push('命中会话标题')
  } else if (item.matchedMessageIndex !== null && item.matchedMessageIndex !== undefined) {
    parts.push(`命中消息 #${item.matchedMessageIndex + 1}`)
  } else {
    parts.push('命中会话标题')
  }
  if (item.updatedAt) {
    parts.push(item.updatedAt)
  }
  return parts.join(' / ')
}

function createNoteFromFavorite(item: FavoriteMessageRecord) {
  selectedNoteId.value = null
  noteForm.title = item.sessionSummary || '聊天摘录'
  noteForm.content = item.content
  noteForm.tagsInput = favoriteToNoteTags(item).join(', ')
  noteForm.relatedFavoriteId = item.id
  noteForm.relatedSessionId = item.sessionId || null
  noteForm.relatedAgentType = item.agentType || null
  noteForm.relatedSessionSummary = item.sessionSummary || null
  noteForm.relatedMessageIndex = item.sourceMessageIndex ?? null
  noteForm.sourceType = 'favorite'
  showToast('已把收藏内容带入笔记编辑区')
}

function createEmptyNote() {
  selectedNoteId.value = null
  noteForm.title = ''
  noteForm.content = ''
  noteForm.tagsInput = ''
  noteForm.relatedFavoriteId = null
  noteForm.relatedSessionId = null
  noteForm.relatedAgentType = null
  noteForm.relatedSessionSummary = null
  noteForm.relatedMessageIndex = null
  noteForm.sourceType = 'manual'
}

function saveNote() {
  const now = Date.now()
  const record: LearningNoteRecord = {
    id: selectedNoteId.value || `note-${now}`,
    title: noteForm.title,
    content: noteForm.content.trim(),
    sourceType: noteForm.sourceType,
    relatedFavoriteId: noteForm.relatedFavoriteId,
    relatedSessionId: noteForm.relatedSessionId,
    relatedAgentType: noteForm.relatedAgentType,
    relatedSessionSummary: noteForm.relatedSessionSummary,
    relatedMessageIndex: noteForm.relatedMessageIndex,
    tags: noteFormTags.value,
    createdAt: selectedNoteId.value
      ? notes.value.find((item) => item.id === selectedNoteId.value)?.createdAt || now
      : now,
    updatedAt: now
  }
  saveLearningNote(record)
  selectedNoteId.value = record.id
  reloadLearningData()
  showToast('学习笔记已保存')
}

function deleteNote() {
  if (!selectedNoteId.value) {
    return
  }
  removeLearningNote(selectedNoteId.value)
  resetNoteForm()
  reloadLearningData()
  showToast('笔记已删除')
}

function resetNoteForm() {
  selectedNoteId.value = null
  noteForm.title = ''
  noteForm.content = ''
  noteForm.tagsInput = ''
  noteForm.relatedFavoriteId = null
  noteForm.relatedSessionId = null
  noteForm.relatedAgentType = null
  noteForm.relatedSessionSummary = null
  noteForm.relatedMessageIndex = null
  noteForm.sourceType = 'manual'
}

function selectNote(id: string) {
  const item = notes.value.find((note) => note.id === id)
  if (!item) {
    return
  }
  selectedNoteId.value = item.id
  noteForm.title = item.title
  noteForm.content = item.content
  noteForm.tagsInput = (item.tags || []).join(', ')
  noteForm.relatedFavoriteId = item.relatedFavoriteId || null
  noteForm.relatedSessionId = item.relatedSessionId || null
  noteForm.relatedAgentType = item.relatedAgentType || null
  noteForm.relatedSessionSummary = item.relatedSessionSummary || null
  noteForm.relatedMessageIndex = item.relatedMessageIndex ?? null
  noteForm.sourceType = item.sourceType || 'manual'
}

function removeFavorite(id: string) {
  removeFavoriteMessage(id)
  reloadLearningData()
  showToast('收藏已移除')
}

function openChat(item: FavoriteMessageRecord) {
  router.push({
    name: 'chat',
    query: {
      agent: item.agentType || 'rd',
      session: item.sessionId,
      message: item.sourceMessageIndex !== null && item.sourceMessageIndex !== undefined ? String(item.sourceMessageIndex) : undefined
    }
  })
}

function openNoteSession(noteId: string) {
  const note = notes.value.find((item) => item.id === noteId)
  if (!note?.relatedSessionId || !note.relatedAgentType) {
    showToast('当前笔记没有关联来源会话')
    return
  }
  router.push({
    name: 'chat',
    query: {
      agent: note.relatedAgentType,
      session: note.relatedSessionId,
      message: note.relatedMessageIndex !== null && note.relatedMessageIndex !== undefined ? String(note.relatedMessageIndex) : undefined
    }
  })
}

function openFavoriteSearchItem(favoriteId: string) {
  const item = favorites.value.find((favorite) => favorite.id === favoriteId)
  if (!item) {
    return
  }
  openChat(item)
}

function openLocalSearchSource(item: LocalLearningSearchItem) {
  if (!item.relatedSessionId || !item.relatedAgentType) {
    showToast('当前条目没有关联来源会话')
    return
  }
  router.push({
    name: 'chat',
    query: {
      agent: item.relatedAgentType,
      session: item.relatedSessionId,
      message: item.relatedMessageIndex !== null && item.relatedMessageIndex !== undefined
        ? String(item.relatedMessageIndex)
        : undefined
    }
  })
}

function buildFollowUpPrompt(sourceLabel: string, content: string) {
  return `请基于这段来源内容继续推进，并给出下一步建议：\n来源：${sourceLabel}\n\n${content.trim()}`
}

function compactSearchSnippet(value?: string | null, limit = 88) {
  const source = (value || '').replace(/\s+/g, ' ').trim()
  if (!source) {
    return ''
  }
  return source.length > limit ? `${source.slice(0, limit)}...` : source
}

function buildKeywordExcerpt(content: string, keyword: string, radius = 36) {
  const source = (content || '').replace(/\s+/g, ' ').trim()
  if (!source) {
    return ''
  }
  const normalizedKeyword = keyword.trim()
  const lowerSource = source.toLowerCase()
  const lowerKeyword = normalizedKeyword.toLowerCase()
  const hitIndex = lowerKeyword ? lowerSource.indexOf(lowerKeyword) : -1
  if (hitIndex === -1) {
    return compactSearchSnippet(source, radius * 2)
  }

  const start = Math.max(0, hitIndex - radius)
  const end = Math.min(source.length, hitIndex + normalizedKeyword.length + radius)
  const prefix = start > 0 ? '...' : ''
  const suffix = end < source.length ? '...' : ''
  return `${prefix}${source.slice(start, end)}${suffix}`
}

function buildSearchResultKey(item: SessionSearchResult) {
  return `${item.agentType}-${item.sessionId}-${item.matchedMessageIndex ?? 'title'}-${item.matchedField || 'message'}`
}

function isSearchResultExpanded(item: SessionSearchResult) {
  return expandedSearchResultKeys.value.includes(buildSearchResultKey(item))
}

function toggleSearchResultExpanded(item: SessionSearchResult) {
  const key = buildSearchResultKey(item)
  expandedSearchResultKeys.value = expandedSearchResultKeys.value.includes(key)
    ? expandedSearchResultKeys.value.filter((entry) => entry !== key)
    : [...expandedSearchResultKeys.value, key]
}

function appendFollowUpDraftItem(item: FollowUpDraftItem) {
  followUpDraftItems.value = [
    item,
    ...followUpDraftItems.value.filter((entry) => entry.id !== item.id)
  ].slice(0, 8)
  showToast('已加入追问草稿')
}

function removeFollowUpDraftItem(id: string) {
  followUpDraftItems.value = followUpDraftItems.value.filter((item) => item.id !== id)
}

function clearFollowUpDraft() {
  followUpDraftItems.value = []
  showToast('已清空追问草稿')
}

function sendFollowUpDraftToChat() {
  const prompt = followUpDraftText.value.trim()
  if (!followUpDraftItems.value.length || !prompt) {
    return
  }
  const primary = followUpDraftItems.value[0]
  router.push({
    name: 'chat',
    query: {
      agent: primary.query.agent,
      session: primary.query.session,
      message: primary.query.message,
      source: 'learning',
      prompt
    }
  })
}

function handleFollowUpDraftInput(value: string) {
  followUpDraftText.value = value
  followUpDraftDirty.value = value !== lastAutoFollowUpDraftPrompt.value
}

function resetFollowUpDraftText() {
  followUpDraftText.value = followUpDraftPrompt.value
  followUpDraftDirty.value = false
  showToast('已恢复自动生成的追问草稿')
}

function canMoveFollowUpDraftItem(id: string, direction: 'up' | 'down') {
  const index = followUpDraftItems.value.findIndex((item) => item.id === id)
  if (index === -1) {
    return false
  }
  return direction === 'up'
    ? index > 0
    : index < followUpDraftItems.value.length - 1
}

function moveFollowUpDraftItem(id: string, direction: 'up' | 'down') {
  const index = followUpDraftItems.value.findIndex((item) => item.id === id)
  if (index === -1) {
    return
  }
  const targetIndex = direction === 'up' ? index - 1 : index + 1
  if (targetIndex < 0 || targetIndex >= followUpDraftItems.value.length) {
    return
  }
  const next = [...followUpDraftItems.value]
  const [current] = next.splice(index, 1)
  next.splice(targetIndex, 0, current)
  followUpDraftItems.value = next
}

function buildFollowUpDraftNoteTitle() {
  const primary = followUpDraftItems.value[0]
  if (!primary) {
    return `追问草稿 ${new Date().toLocaleDateString('zh-CN')}`
  }
  return `追问草稿：${primary.title}`
}

function saveFollowUpDraftAsNote() {
  const content = followUpDraftText.value.trim()
  if (!followUpDraftItems.value.length || !content) {
    return
  }
  const primary = followUpDraftItems.value[0]
  const now = Date.now()
  const record: LearningNoteRecord = {
    id: `note-${now}`,
    title: buildFollowUpDraftNoteTitle(),
    content,
    sourceType: 'manual',
    relatedFavoriteId: null,
    relatedSessionId: primary.query.session,
    relatedAgentType: primary.query.agent,
    relatedSessionSummary: primary.title,
    relatedMessageIndex: primary.query.message ? Number(primary.query.message) : null,
    tags: normalizeTags(['追问草稿', '学习中心', primary.query.agent]),
    createdAt: now,
    updatedAt: now
  }
  saveLearningNote(record)
  reloadLearningData()
  selectedNoteId.value = record.id
  selectNote(record.id)
  showToast('追问草稿已保存为学习笔记')
}

function saveFollowUpTemplate() {
  const content = followUpDraftText.value.trim()
  const name = followUpTemplateName.value.trim()
  if (!content || !name || !followUpDraftItems.value.length) {
    return
  }

  const now = Date.now()
  const record: FollowUpTemplateRecord = {
    id: `template-${now}`,
    name,
    content,
    sourceCount: followUpDraftItems.value.length,
    updatedAt: now
  }
  const next = [record, ...followUpTemplates.value.filter((item) => item.name !== name)].slice(0, 20)
  followUpTemplates.value = next
  persistFollowUpTemplates(next)
  showToast('追问模板已保存')
}

function applyFollowUpTemplate(id: string) {
  const target = followUpTemplates.value.find((item) => item.id === id)
  if (!target) {
    return
  }
  followUpDraftText.value = target.content
  followUpTemplateName.value = target.name
  followUpDraftDirty.value = target.content !== lastAutoFollowUpDraftPrompt.value
  showToast('已套用追问模板')
}

function removeFollowUpTemplate(id: string) {
  followUpTemplates.value = followUpTemplates.value.filter((item) => item.id !== id)
  persistFollowUpTemplates(followUpTemplates.value)
  showToast('追问模板已删除')
}

function legacySendFollowUpDraftToChat() {
  if (!followUpDraftItems.value.length) {
    return
  }
  const primary = followUpDraftItems.value[0]
  router.push({
    name: 'chat',
    query: {
      agent: primary.query.agent,
      session: primary.query.session,
      message: primary.query.message,
      source: 'learning',
      prompt: followUpDraftPrompt.value
    }
  })
}

function appendFavoriteToDraft(item: FavoriteMessageRecord) {
  if (!item.sessionId || !item.agentType) {
    showToast('当前收藏没有关联来源会话')
    return
  }
  appendFollowUpDraftItem({
    id: `favorite-${item.id}`,
    title: item.sessionSummary || '收藏内容',
    sourceLabel: buildSourceSummary(item.sessionSummary, item.agentType, item.sourceMessageIndex ?? null),
    content: item.content,
    query: {
      agent: item.agentType,
      session: item.sessionId,
      message: item.sourceMessageIndex !== null && item.sourceMessageIndex !== undefined ? String(item.sourceMessageIndex) : undefined
    }
  })
}

function appendNoteToDraft(item: LearningNoteRecord) {
  if (!item.relatedSessionId || !item.relatedAgentType) {
    showToast('当前笔记没有关联来源会话')
    return
  }
  appendFollowUpDraftItem({
    id: `note-${item.id}`,
    title: item.title,
    sourceLabel: buildSourceSummary(item.relatedSessionSummary, item.relatedAgentType, item.relatedMessageIndex),
    content: item.content,
    query: {
      agent: item.relatedAgentType,
      session: item.relatedSessionId,
      message: item.relatedMessageIndex !== null && item.relatedMessageIndex !== undefined ? String(item.relatedMessageIndex) : undefined
    }
  })
}

function appendLocalSearchToDraft(item: LocalLearningSearchItem) {
  if (!item.relatedSessionId || !item.relatedAgentType) {
    showToast('当前条目没有关联来源会话')
    return
  }
  appendFollowUpDraftItem({
    id: `local-${item.type}-${item.id}`,
    title: item.title,
    sourceLabel: buildLocalSearchSourceSummary(item),
    content: item.preview,
    query: {
      agent: item.relatedAgentType,
      session: item.relatedSessionId,
      message: item.relatedMessageIndex !== null && item.relatedMessageIndex !== undefined ? String(item.relatedMessageIndex) : undefined
    }
  })
}

function appendSearchResultToDraft(item: SessionSearchResult) {
  appendFollowUpDraftItem({
    id: `search-${item.agentType}-${item.sessionId}-${item.matchedMessageIndex ?? 'title'}`,
    title: item.sessionSummary || '搜索结果',
    sourceLabel: buildSearchResultMeta(item),
    content: item.matchedContent,
    query: {
      agent: item.agentType,
      session: item.sessionId,
      message: item.matchedMessageIndex !== null && item.matchedMessageIndex !== undefined ? String(item.matchedMessageIndex) : undefined
    }
  })
}

function continueFromFavorite(item: FavoriteMessageRecord) {
  if (!item.sessionId || !item.agentType) {
    showToast('当前收藏没有关联来源会话')
    return
  }
  router.push({
    name: 'chat',
    query: {
      agent: item.agentType,
      session: item.sessionId,
      message: item.sourceMessageIndex !== null && item.sourceMessageIndex !== undefined ? String(item.sourceMessageIndex) : undefined,
      source: 'learning',
      prompt: buildFollowUpPrompt(buildSourceSummary(item.sessionSummary, item.agentType, item.sourceMessageIndex ?? null), item.content)
    }
  })
}

function continueFromNote(item: LearningNoteRecord) {
  if (!item.relatedSessionId || !item.relatedAgentType) {
    showToast('当前笔记没有关联来源会话')
    return
  }
  router.push({
    name: 'chat',
    query: {
      agent: item.relatedAgentType,
      session: item.relatedSessionId,
      message: item.relatedMessageIndex !== null && item.relatedMessageIndex !== undefined ? String(item.relatedMessageIndex) : undefined,
      source: 'learning',
      prompt: buildFollowUpPrompt(buildSourceSummary(item.relatedSessionSummary, item.relatedAgentType, item.relatedMessageIndex), item.content)
    }
  })
}

function continueFromLocalSearch(item: LocalLearningSearchItem) {
  if (!item.relatedSessionId || !item.relatedAgentType) {
    showToast('当前条目没有关联来源会话')
    return
  }
  router.push({
    name: 'chat',
    query: {
      agent: item.relatedAgentType,
      session: item.relatedSessionId,
      message: item.relatedMessageIndex !== null && item.relatedMessageIndex !== undefined ? String(item.relatedMessageIndex) : undefined,
      source: 'learning',
      prompt: buildFollowUpPrompt(buildLocalSearchSourceSummary(item), item.preview)
    }
  })
}

function continueFromSearchResult(item: SessionSearchResult) {
  router.push({
    name: 'chat',
    query: {
      agent: item.agentType,
      session: item.sessionId,
      message: item.matchedMessageIndex !== null && item.matchedMessageIndex !== undefined ? String(item.matchedMessageIndex) : undefined,
      source: 'learning',
      prompt: buildFollowUpPrompt(buildSearchResultMeta(item), item.matchedContent)
    }
  })
}

function toggleFavoriteSelection(id: string, checked: boolean) {
  if (checked) {
    selectedFavoriteIds.value = Array.from(new Set([...selectedFavoriteIds.value, id]))
    return
  }
  selectedFavoriteIds.value = selectedFavoriteIds.value.filter((item) => item !== id)
}

function toggleSelectAllFavorites(checked: boolean) {
  if (checked) {
    selectedFavoriteIds.value = Array.from(new Set([
      ...selectedFavoriteIds.value,
      ...filteredFavorites.value.map((item) => item.id)
    ]))
    return
  }
  const visibleIds = new Set(filteredFavorites.value.map((item) => item.id))
  selectedFavoriteIds.value = selectedFavoriteIds.value.filter((id) => !visibleIds.has(id))
}

function downloadFile(fileName: string, content: string, mimeType: string) {
  const blob = new Blob([content], { type: mimeType })
  const url = URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = fileName
  link.click()
  URL.revokeObjectURL(url)
}

function exportFavorites(includeFiltered: boolean) {
  const target = includeFiltered
    ? filteredFavorites.value
    : favorites.value.filter((item) => selectedFavoriteIds.value.includes(item.id))
  if (!target.length) {
    showToast('没有可导出的收藏')
    return
  }
  downloadFile(
    `learning-favorites-${Date.now()}.json`,
    JSON.stringify(target, null, 2),
    'application/json;charset=utf-8'
  )
  showToast(includeFiltered ? '当前收藏已导出' : '选中收藏已导出')
}

function exportLearningBundle() {
  const payload = {
    exportedAt: new Date().toISOString(),
    favorites: filteredFavorites.value,
    notes: filteredNotes.value
  }
  downloadFile(
    `learning-bundle-${Date.now()}.json`,
    JSON.stringify(payload, null, 2),
    'application/json;charset=utf-8'
  )
  showToast('学习资料包已导出')
}

async function runSearch() {
  if (!searchKeyword.value) {
    return
  }
  searching.value = true
  searchResults.value = []
  expandedSearchResultKeys.value = []

  try {
    const bots = await authApi.getMyBots().catch(() => [])
    const agentTypes = (bots.length ? bots.map((item) => item.botType) : Object.keys(AGENT_CONFIG))
      .filter((value, index, list) => list.indexOf(value) === index)

    const keyword = searchKeyword.value.toLowerCase()
    const results: SessionSearchResult[] = []

    for (const agentType of agentTypes) {
      const sessions = await agentApi.getSessions(agentType).catch(() => [])
      for (const session of sessions) {
        if ((session.summary || '').toLowerCase().includes(keyword)) {
          results.push({
            agentType,
            sessionId: session.sessionId,
            sessionSummary: session.summary,
            matchedRole: 'assistant',
            matchedContent: `命中会话标题：${session.summary}`,
            matchedField: 'title',
            excerpt: buildKeywordExcerpt(session.summary || '', keyword),
            updatedAt: session.updatedAt
          })
          continue
        }

        const history = await agentApi.getHistory(agentType, session.sessionId).catch(() => [])
        const matchedIndexes = history
          .map((item, index) => ((item.content || '').toLowerCase().includes(keyword) ? index : -1))
          .filter((index) => index >= 0)
        const matched = matchedIndexes.length ? history[matchedIndexes[0]] : null
        if (matched) {
          const matchedIndex = matchedIndexes[0]
          const previous = matchedIndex > 0 ? history[matchedIndex - 1] : null
          const next = matchedIndex < history.length - 1 ? history[matchedIndex + 1] : null
          results.push({
            agentType,
            sessionId: session.sessionId,
            sessionSummary: session.summary,
            matchedRole: matched.role,
            matchedContent: matched.content,
            matchedMessageIndex: matchedIndex,
            matchedField: 'message',
            excerpt: buildKeywordExcerpt(matched.content, keyword),
            contextBefore: previous ? `${previous.role === 'assistant' ? '助手' : '用户'}：${compactSearchSnippet(previous.content)}` : '',
            contextAfter: next ? `${next.role === 'assistant' ? '助手' : '用户'}：${compactSearchSnippet(next.content)}` : '',
            additionalMatchCount: Math.max(0, matchedIndexes.length - 1),
            updatedAt: session.updatedAt
          })
        }
      }
    }

    searchResults.value = results
    showToast(results.length ? `已找到 ${results.length} 条匹配结果` : '没有找到匹配内容')
  } finally {
    searching.value = false
  }
}

function openSearchResult(item: SessionSearchResult) {
  router.push({
    name: 'chat',
    query: {
      agent: item.agentType,
      session: item.sessionId,
      message: item.matchedMessageIndex !== null && item.matchedMessageIndex !== undefined ? String(item.matchedMessageIndex) : undefined
    }
  })
}

function saveSearchResultAsNote(item: SessionSearchResult) {
  selectedNoteId.value = null
  noteForm.title = item.sessionSummary || '搜索结果笔记'
  noteForm.content = item.matchedContent
  noteForm.tagsInput = buildSearchResultTags(item).join(', ')
  noteForm.relatedFavoriteId = null
  noteForm.relatedSessionId = item.sessionId
  noteForm.relatedAgentType = item.agentType
  noteForm.relatedSessionSummary = item.sessionSummary
  noteForm.relatedMessageIndex = item.matchedMessageIndex ?? null
  noteForm.sourceType = 'session-search'
  showToast('已把搜索结果带入笔记编辑区')
}

onMounted(() => {
  reloadLearningData()
})
</script>

<style scoped>
.learning-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(0, 0.9fr);
  gap: 16px;
  margin-bottom: 16px;
}

.learning-section {
  padding: 18px;
}

.local-search-section {
  margin-bottom: 16px;
}

.followup-draft-section {
  margin-bottom: 16px;
}

.followup-draft-section > .learning-actions {
  display: none;
}

.search-select {
  min-width: 140px;
}

.learning-toolbar {
  display: grid;
  gap: 12px;
  margin-bottom: 14px;
}

.learning-toolbar.compact {
  grid-template-columns: minmax(0, 1fr) auto;
  align-items: center;
}

.toolbar-block {
  display: grid;
  gap: 8px;
}

.toolbar-label {
  color: var(--text3);
  font-size: 12px;
}

.chip-group {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.chip-btn {
  border: 1px solid var(--border);
  background: rgba(255, 255, 255, 0.03);
  color: var(--text3);
  border-radius: 999px;
  padding: 6px 12px;
  font-size: 12px;
  cursor: pointer;
}

.chip-btn.active {
  color: var(--accent2);
  border-color: rgba(79, 142, 247, 0.22);
  background: rgba(79, 142, 247, 0.08);
}

.check-inline {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  color: var(--text2);
  font-size: 13px;
}

.learning-list {
  display: grid;
  gap: 12px;
}

.followup-draft-list {
  display: grid;
  gap: 12px;
  margin-bottom: 12px;
}

.followup-draft-item {
  padding: 14px 16px;
  border-radius: 16px;
  border: 1px solid var(--border);
  background: rgba(255, 255, 255, 0.03);
}

.followup-draft-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.followup-draft-head > .btn {
  display: none;
}

.followup-draft-row-actions {
  margin-top: 12px;
}

.followup-draft-preview {
  margin-bottom: 12px;
}

.followup-template-toolbar {
  margin-bottom: 12px;
  align-items: stretch;
}

.followup-template-toolbar .form-input {
  min-width: min(320px, 100%);
}

.followup-template-list {
  display: grid;
  gap: 12px;
  margin-bottom: 12px;
}

.followup-template-card {
  padding: 14px 16px;
  border-radius: 16px;
  border: 1px solid var(--border);
  background: rgba(255, 255, 255, 0.03);
}

.followup-template-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.followup-draft-meta {
  display: grid;
  gap: 10px;
  margin-bottom: 12px;
  color: var(--text3);
  font-size: 12px;
}

.followup-draft-actions {
  margin-bottom: 12px;
}

.followup-draft-meta > span {
  display: inline-flex;
  align-items: center;
  gap: 8px;
}

.learning-card {
  padding: 14px 16px;
  border-radius: 18px;
  border: 1px solid var(--border);
  background: rgba(255, 255, 255, 0.03);
}

.learning-card-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.learning-card-main {
  display: grid;
  gap: 6px;
}

.learning-card-title {
  color: var(--text);
  font-size: 15px;
  font-weight: 700;
}

.learning-card-meta {
  margin-top: 4px;
  color: var(--text3);
  font-size: 12px;
}

.learning-source-line {
  margin-top: 8px;
  padding: 8px 10px;
  border-radius: 12px;
  border: 1px dashed rgba(79, 142, 247, 0.18);
  background: rgba(79, 142, 247, 0.05);
  color: var(--text2);
  font-size: 12px;
  line-height: 1.6;
}

.learning-card-content {
  margin-top: 10px;
  color: var(--text2);
  font-size: 13px;
  line-height: 1.7;
  white-space: pre-wrap;
}

.learning-card-content.compact {
  display: -webkit-box;
  -webkit-line-clamp: 4;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.search-result-excerpt {
  margin-top: 10px;
  padding: 10px 12px;
  border-radius: 12px;
  background: rgba(79, 142, 247, 0.08);
  border: 1px solid rgba(79, 142, 247, 0.18);
  color: var(--text);
  font-size: 12px;
  line-height: 1.6;
}

.search-result-context {
  margin-top: 10px;
  display: grid;
  gap: 10px;
}

.search-result-context-body {
  display: grid;
  gap: 8px;
}

.search-result-context-line {
  display: grid;
  gap: 6px;
  padding: 10px 12px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.03);
  border: 1px solid var(--border);
  color: var(--text2);
  font-size: 12px;
  line-height: 1.6;
}

.search-result-context-line.active {
  border-color: rgba(79, 142, 247, 0.24);
  background: rgba(79, 142, 247, 0.08);
}

.search-result-context-label {
  color: var(--text3);
  font-size: 11px;
  letter-spacing: 0.04em;
}

.learning-card-tags {
  margin-top: 10px;
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.tag-warn {
  color: #d97706;
  border-color: rgba(217, 119, 6, 0.18);
  background: rgba(217, 119, 6, 0.08);
}

.learning-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}

.note-editor {
  display: grid;
  gap: 12px;
  margin-bottom: 14px;
}

.note-textarea {
  min-height: 180px;
  resize: vertical;
  border: 1px solid var(--border);
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.04);
  color: var(--text);
  padding: 12px 14px;
  font: inherit;
}

.note-card {
  cursor: pointer;
  transition: border-color var(--transition), background var(--transition), transform var(--transition);
}

.note-card.active,
.note-card:hover {
  border-color: rgba(79, 142, 247, 0.22);
  background: rgba(79, 142, 247, 0.08);
  transform: translateY(-1px);
}

.note-source-actions {
  margin-top: 10px;
}

.search-section {
  padding: 18px;
}

.search-toolbar {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto auto;
  gap: 12px;
  margin-bottom: 16px;
}

@media (max-width: 960px) {
  .learning-grid,
  .search-toolbar,
  .learning-toolbar.compact {
    grid-template-columns: 1fr;
  }

  .learning-card-head {
    flex-direction: column;
  }
}
</style>
