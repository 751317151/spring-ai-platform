<template>
  <div class="space-admin">
    <div class="page-hero">
      <div class="page-hero-main">
        <div class="eyebrow">Agent</div>
        <div class="page-title">Agent 工作台</div>
        <div class="page-subtitle">
          这里集中查看当前助手的运行健康、趋势变化、工具排行、失败样本和排障入口，让工作台从“诊断页”升级为“运维台”。
        </div>
        <div class="hero-tags">
          <span class="tag">{{ currentConfig.name }}</span>
          <span class="tag">{{ selectedAgent }}</span>
          <span class="tag">{{ workbenchSummary?.windowLabel || '最近 24 小时' }}</span>
          <span class="tag" :class="{ warning: workbenchSummary?.healthSummary?.warning }">
            {{ workbenchSummary?.healthSummary?.warning ? '需要关注' : '状态稳定' }}
          </span>
        </div>
      </div>
      <div class="page-hero-actions">
        <select v-model="selectedAgent" class="form-select agent-select">
          <option v-for="item in agentOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
        </select>
        <button class="btn btn-ghost btn-sm" type="button" @click="openChatWithAgent()">进入聊天页</button>
        <button class="btn btn-ghost btn-sm" type="button" @click="openMonitorTrace()">进入监控页</button>
        <button class="btn btn-primary btn-sm" type="button" @click="copyWorkbenchLink">复制当前链接</button>
      </div>
    </div>

    <div class="agent-overview-grid">
      <article class="card agent-overview-card">
        <div class="agent-overview-kicker">当前助手</div>
        <div class="agent-overview-title-row">
          <div class="agent-avatar" :style="{ background: `${currentConfig.color}18`, color: currentConfig.color }">
            {{ currentConfig.icon }}
          </div>
          <div>
            <div class="agent-overview-title">{{ currentConfig.name }}</div>
            <div class="agent-overview-desc">{{ currentConfig.desc }}</div>
          </div>
        </div>
      </article>

      <article class="card agent-overview-card" :class="{ warning: workbenchSummary?.healthSummary?.warning }">
        <div class="agent-overview-kicker">健康摘要</div>
        <div class="agent-overview-title">{{ workbenchSummary?.healthSummary?.warning ? '存在风险' : '运行稳定' }}</div>
        <div class="agent-overview-desc">{{ workbenchSummary?.healthSummary?.summary || '等待加载健康摘要' }}</div>
      </article>

      <button class="card agent-overview-card action-card" type="button" @click="openMonitorTrace()">
        <div class="agent-overview-kicker">联动监控</div>
        <div class="agent-overview-title">按助手带筛选进入监控页</div>
        <div class="agent-overview-desc">继续查看该助手的慢请求、失败样本和 Trace 明细。</div>
      </button>
    </div>

    <div class="card section-card workbench-summary-card">
      <div class="card-header">
        <div>
          <div class="card-title">运行概览</div>
          <div class="card-subtitle">聚合最近调用、失败率、工具调用、轨迹数量和健康摘要，先给出排障优先级。</div>
        </div>
      </div>

      <SkeletonBlock v-if="summaryLoading" :count="4" :height="88" variant="grid" />
      <EmptyState
        v-else-if="summaryError"
        icon="A"
        badge="Agent 工作台"
        title="Agent 聚合统计加载失败"
        :description="summaryError"
        action-text="重试"
        @action="reloadWorkbenchSummary"
      />
      <template v-else>
        <div class="workbench-metric-grid">
          <article class="card workbench-metric-item">
            <div class="workbench-metric-label">最近调用</div>
            <div class="workbench-metric-value">{{ workbenchSummary?.totalCalls ?? 0 }}</div>
            <div class="workbench-metric-sub">{{ workbenchSummary?.windowLabel || '最近 24 小时' }}</div>
          </article>
          <article class="card workbench-metric-item">
            <div class="workbench-metric-label">失败率</div>
            <div class="workbench-metric-value">{{ failureRateLabel }}</div>
            <div class="workbench-metric-sub">{{ workbenchSummary?.failureCalls ?? 0 }} 次失败</div>
          </article>
          <article class="card workbench-metric-item">
            <div class="workbench-metric-label">平均延迟</div>
            <div class="workbench-metric-value">{{ workbenchSummary?.avgLatencyMs ?? 0 }} ms</div>
            <div class="workbench-metric-sub">工具平均 {{ workbenchSummary?.avgToolLatencyMs ?? 0 }} ms</div>
          </article>
          <article class="card workbench-metric-item">
            <div class="workbench-metric-label">最慢工具</div>
            <div class="workbench-metric-value metric-sm">{{ workbenchSummary?.slowestToolName || '暂无' }}</div>
            <div class="workbench-metric-sub">{{ workbenchSummary?.slowestToolLatencyMs ?? 0 }} ms</div>
          </article>
        </div>

        <div class="workbench-focus-grid">
          <button class="workbench-focus-item" type="button" @click="openChatWithAgent()">
            <strong>继续复现</strong>
            <span>带当前助手跳回聊天页，继续复现问题和验证修复结果。</span>
          </button>
          <button class="workbench-focus-item" type="button" @click="openMonitorTrace()">
            <strong>继续联动监控</strong>
            <span>把当前助手筛选带到监控页，快速定位慢请求和失败链路。</span>
          </button>
          <button
            class="workbench-focus-item"
            type="button"
            :disabled="!workbenchSummary?.latestTraceId"
            @click="copyLatestTraceId"
          >
            <strong>复制最新 Trace</strong>
            <span>{{ workbenchSummary?.latestTraceId || '当前没有可复制的 Trace' }}</span>
          </button>
        </div>
      </template>
    </div>

    <div class="workbench-two-column">
      <div class="card section-card">
        <div class="card-header">
          <div>
            <div class="card-title">24 小时趋势</div>
            <div class="card-subtitle">按小时查看调用、失败和工具调用变化。</div>
          </div>
        </div>
        <div v-if="workbenchSummary?.last24hTrend?.length" class="trend-list">
          <article v-for="item in workbenchSummary.last24hTrend" :key="item.label" class="trend-item">
            <div class="trend-head">
              <strong>{{ item.label }}</strong>
              <span>{{ item.totalCalls }} 次调用</span>
            </div>
            <div class="trend-bar-track">
              <div class="trend-bar total" :style="{ width: `${barWidth(item.totalCalls, max24hCalls)}%` }"></div>
              <div class="trend-bar failure" :style="{ width: `${barWidth(item.failureCalls, max24hCalls)}%` }"></div>
            </div>
            <div class="trend-meta">
              <span>失败 {{ item.failureCalls }}</span>
              <span>工具 {{ item.toolCalls }}</span>
              <span>延迟 {{ item.avgLatencyMs }} ms</span>
            </div>
          </article>
        </div>
        <EmptyState
          v-else
          icon="T"
          badge="趋势"
          title="暂无 24 小时趋势数据"
          description="当前助手最近 24 小时没有足够的数据形成趋势。"
          variant="compact"
        />
      </div>

      <div class="card section-card">
        <div class="card-header">
          <div>
            <div class="card-title">7 天趋势</div>
            <div class="card-subtitle">按天查看近一周活跃度和失败变化。</div>
          </div>
        </div>
        <div v-if="workbenchSummary?.last7dTrend?.length" class="trend-list">
          <article v-for="item in workbenchSummary.last7dTrend" :key="item.label" class="trend-item">
            <div class="trend-head">
              <strong>{{ item.label }}</strong>
              <span>{{ item.totalCalls }} 次调用</span>
            </div>
            <div class="trend-bar-track">
              <div class="trend-bar total" :style="{ width: `${barWidth(item.totalCalls, max7dCalls)}%` }"></div>
              <div class="trend-bar failure" :style="{ width: `${barWidth(item.failureCalls, max7dCalls)}%` }"></div>
            </div>
            <div class="trend-meta">
              <span>失败 {{ item.failureCalls }}</span>
              <span>工具 {{ item.toolCalls }}</span>
              <span>延迟 {{ item.avgLatencyMs }} ms</span>
            </div>
          </article>
        </div>
        <EmptyState
          v-else
          icon="7"
          badge="趋势"
          title="暂无 7 天趋势数据"
          description="当前助手最近 7 天没有足够的数据形成趋势。"
          variant="compact"
        />
      </div>
    </div>

    <div class="workbench-two-column">
      <div class="card section-card">
        <div class="card-header">
          <div>
            <div class="card-title">工具排行榜</div>
            <div class="card-subtitle">聚焦最近最常用、最容易失败、平均最慢的工具。</div>
          </div>
        </div>
        <div v-if="workbenchSummary?.toolRanking?.length" class="rank-list">
          <button
            v-for="tool in workbenchSummary.toolRanking"
            :key="`${tool.toolName}-${tool.latestTraceId}`"
            class="rank-item"
            type="button"
            @click="openChatWithAgent(tool.latestTraceId)"
          >
            <div class="rank-item-head">
              <strong>{{ tool.toolName }}</strong>
              <span>{{ tool.callCount }} 次</span>
            </div>
            <div class="rank-item-meta">
              <span>失败 {{ tool.failureCount }}</span>
              <span>平均 {{ tool.avgLatencyMs }} ms</span>
              <span v-if="tool.latestTraceId">Trace {{ tool.latestTraceId }}</span>
            </div>
          </button>
        </div>
        <EmptyState
          v-else
          icon="R"
          badge="工具排行"
          title="暂无工具排行榜"
          description="当前助手最近没有足够的工具调用数据。"
          variant="compact"
        />
      </div>

      <div class="card section-card">
        <div class="card-header">
          <div>
            <div class="card-title">错误类型分布</div>
            <div class="card-subtitle">快速判断问题更偏权限、模型、工具还是外部依赖。</div>
          </div>
        </div>
        <div v-if="workbenchSummary?.errorTypes?.length" class="error-type-list">
          <article v-for="item in workbenchSummary.errorTypes" :key="item.type" class="error-type-item">
            <div class="error-type-head">
              <strong>{{ item.label }}</strong>
              <span>{{ item.count }}</span>
            </div>
            <div class="trend-bar-track">
              <div class="trend-bar failure" :style="{ width: `${barWidth(item.count, maxErrorCount)}%` }"></div>
            </div>
          </article>
        </div>
        <EmptyState
          v-else
          icon="E"
          badge="错误分布"
          title="暂无错误类型数据"
          description="当前助手最近没有失败样本。"
          variant="compact"
        />
      </div>
    </div>

    <div class="card section-card workbench-failure-card">
      <div class="card-header">
        <div>
          <div class="card-title">最近失败样本</div>
          <div class="card-subtitle">直接带着 agent / session / trace 跳转，减少手工复制上下文。</div>
        </div>
      </div>

      <EmptyState
        v-if="!summaryLoading && !summaryError && !workbenchSummary?.recentFailures?.length"
        icon="!"
        badge="失败样本"
        title="最近没有失败样本"
        description="当前助手最近 24 小时没有失败请求，可以优先关注趋势和工具排行。"
        variant="compact"
      />
      <div v-else class="failure-list">
        <article v-for="item in workbenchSummary?.recentFailures || []" :key="`${item.traceId}-${item.createdAt}`" class="failure-item">
          <div class="failure-head">
            <div>
              <div class="failure-title">{{ item.errorMessage || '未记录错误信息' }}</div>
              <div class="failure-meta">
                <span>用户 {{ item.userId || '-' }}</span>
                <span>会话 {{ item.sessionId || '-' }}</span>
                <span>延迟 {{ item.latencyMs ?? 0 }} ms</span>
              </div>
            </div>
            <div class="failure-actions">
              <button class="btn btn-ghost btn-sm" type="button" :disabled="!item.traceId" @click="copyTraceId(item.traceId || '')">
                复制 Trace
              </button>
              <button class="btn btn-ghost btn-sm" type="button" @click="openChatWithAgent(item.traceId || '', item.sessionId || '')">
                去聊天页
              </button>
              <button class="btn btn-ghost btn-sm" type="button" @click="openMonitorTrace(item.traceId || '')">
                去监控页
              </button>
            </div>
          </div>
          <div class="failure-summary">{{ item.summary || '未记录原始请求摘要' }}</div>
        </article>
      </div>
    </div>

    <div class="workbench-two-column">
      <div class="card section-card">
        <div class="card-header">
          <div>
            <div class="card-title">运行时策略</div>
            <div class="card-subtitle">把当前助手的资源隔离、工具隔离和数据范围策略集中展示，方便判断治理边界是否足够收敛。</div>
          </div>
        </div>
        <div class="workbench-focus-grid policy-grid single-column">
          <article class="card workbench-metric-item">
            <div class="workbench-metric-label">风险等级</div>
            <div class="workbench-metric-value metric-sm">{{ policyRiskLabel }}</div>
            <div class="workbench-metric-sub">{{ workbenchSummary?.runtimePolicySummary?.summary || '暂无策略摘要' }}</div>
          </article>
          <article class="card workbench-metric-item">
            <div class="workbench-metric-label">受限资源数</div>
            <div class="workbench-metric-value">{{ workbenchSummary?.runtimePolicySummary?.restrictedResourceCount ?? 0 }}</div>
            <div class="workbench-metric-sub">
              风险项 {{ workbenchSummary?.runtimePolicySummary?.riskCount ?? 0 }}
            </div>
          </article>
        </div>
      </div>

      <div class="card section-card">
        <div class="card-header">
          <div>
            <div class="card-title">最近变化</div>
            <div class="card-subtitle">把近 24 小时和近 7 天基线做对比，优先提醒更值得排查的变化。</div>
          </div>
        </div>
        <div v-if="workbenchSummary?.recentChanges?.length" class="failure-list">
          <article v-for="item in workbenchSummary.recentChanges" :key="`${item.type}-${item.label}`" class="failure-item">
            <div class="failure-head">
              <div>
                <div class="failure-title">{{ item.label }}</div>
                <div class="failure-meta">
                  <span>方向 {{ item.direction }}</span>
                  <span>级别 {{ item.severity }}</span>
                </div>
              </div>
            </div>
            <div class="failure-summary">{{ item.summary }}</div>
          </article>
        </div>
      </div>
    </div>

    <div class="workbench-two-column">
      <div class="card section-card">
        <div class="card-header">
          <div>
            <div class="card-title">4 周趋势</div>
            <div class="card-subtitle">{{ workbenchSummary?.weeklyDigest || '按周查看长期趋势变化。' }}</div>
          </div>
        </div>
        <div v-if="workbenchSummary?.last4wTrend?.length" class="trend-list">
          <article v-for="item in workbenchSummary.last4wTrend" :key="item.label" class="trend-item">
            <div class="trend-head">
              <strong>{{ item.label }}</strong>
              <span>{{ item.totalCalls }} 次调用</span>
            </div>
            <div class="trend-bar-track">
              <div class="trend-bar total" :style="{ width: `${barWidth(item.totalCalls, max4wCalls)}%` }"></div>
              <div class="trend-bar failure" :style="{ width: `${barWidth(item.failureCalls, max4wCalls)}%` }"></div>
            </div>
            <div class="trend-meta">
              <span>失败 {{ item.failureCalls }}</span>
              <span>工具 {{ item.toolCalls }}</span>
              <span>延迟 {{ item.avgLatencyMs }} ms</span>
            </div>
          </article>
        </div>
      </div>
    </div>

    <div class="workbench-two-column">
      <div class="card section-card">
        <div class="card-header">
          <div>
            <div class="card-title">Agent 对比视图</div>
            <div class="card-subtitle">把当前助手和另一个助手放在同一张卡里看调用量、失败率、延迟和工具表现。</div>
          </div>
          <select v-model="compareAgent" class="form-select agent-select compare-select">
            <option v-for="item in compareAgentOptions" :key="item.value" :value="item.value">{{ item.label }}</option>
          </select>
        </div>
        <div v-if="compareSummary" class="compare-grid">
          <article class="compare-card">
            <div class="compare-agent-name">{{ currentConfig.name }}</div>
            <div class="compare-metric-list">
              <div class="compare-metric-row">
                <span>调用量</span>
                <strong>{{ compareSummary.left.totalCalls }}</strong>
              </div>
              <div class="compare-metric-row">
                <span>失败率</span>
                <strong>{{ formatPercent(1 - compareSummary.left.successRate) }}</strong>
              </div>
              <div class="compare-metric-row">
                <span>平均延迟</span>
                <strong>{{ compareSummary.left.avgLatencyMs }} ms</strong>
              </div>
              <div class="compare-metric-row">
                <span>最慢工具</span>
                <strong>{{ compareSummary.left.slowestToolName || 'N/A' }}</strong>
              </div>
            </div>
          </article>
          <article class="compare-card">
            <div class="compare-agent-name">{{ getAgentConfig(compareAgent).name }}</div>
            <div class="compare-metric-list">
              <div class="compare-metric-row">
                <span>调用量</span>
                <strong>{{ compareSummary.right.totalCalls }}</strong>
              </div>
              <div class="compare-metric-row">
                <span>失败率</span>
                <strong>{{ formatPercent(1 - compareSummary.right.successRate) }}</strong>
              </div>
              <div class="compare-metric-row">
                <span>平均延迟</span>
                <strong>{{ compareSummary.right.avgLatencyMs }} ms</strong>
              </div>
              <div class="compare-metric-row">
                <span>最慢工具</span>
                <strong>{{ compareSummary.right.slowestToolName || 'N/A' }}</strong>
              </div>
            </div>
          </article>
        </div>
        <div v-if="compareSummary" class="compare-insights">
          <div class="compare-insights-title compare-insights-head">
            <span>Difference hints</span>
            <button class="btn btn-ghost btn-sm" type="button" @click="compareDetailsOpen = !compareDetailsOpen">
              {{ compareDetailsOpen ? 'Hide details' : 'Open details' }}
            </button>
          </div>
          <div class="compare-insight-list">
            <div v-for="item in compareInsightLines" :key="`${item.type}-${item.summary}`" class="compare-insight-item">
              <strong>{{ item.title }}</strong>
              <span>{{ item.summary }}</span>
            </div>
          </div>
        </div>
        <div v-if="compareSummary?.metrics?.length" class="compare-insights">
          <div class="compare-insights-title">Metric deltas</div>
          <div class="compare-insight-list">
            <div v-for="item in compareSummary.metrics" :key="item.key" class="compare-insight-item">
              <strong>{{ item.label }}: {{ item.leftValue }} vs {{ item.rightValue }}</strong>
              <span>{{ item.summary }} | delta {{ item.delta }}</span>
            </div>
          </div>
        </div>
        <div v-if="compareSummary && compareDetailsOpen" class="compare-insights compare-detail-panel">
          <div class="compare-insights-title">Compare details</div>
          <div class="compare-insight-list">
            <div v-for="item in compareInsightLines" :key="`${item.metricKey}-${item.title}`" class="compare-insight-item compare-detail-item">
              <strong>{{ item.title }}</strong>
              <span>{{ item.summary }}</span>
              <span>Left: {{ item.leftEvidence }} | Right: {{ item.rightEvidence }}</span>
              <span>{{ item.whyItMatters }}</span>
              <span>{{ item.suggestedAction }}</span>
            </div>
          </div>
        </div>
        <div v-if="compareSummary?.leftDetail || compareSummary?.rightDetail" class="compare-insights compare-detail-panel">
          <div class="compare-insights-title">Agent detail drawer</div>
          <div class="compare-grid">
            <div class="compare-insight-list">
              <div class="compare-insight-item">
                <strong>{{ compareSummary?.leftDetail?.agentType || selectedAgent }}</strong>
                <span>{{ compareSummary?.leftDetail?.summary || '-' }}</span>
                <span>{{ compareSummary?.leftDetail?.healthSummary || '-' }}</span>
                <span>{{ compareSummary?.leftDetail?.policySummary || '-' }}</span>
                <span>Calls {{ compareSummary?.leftDetail?.totalCalls ?? 0 }} | Failure {{ compareSummary?.leftDetail?.failureRateLabel || '0%' }} | Risk {{ compareSummary?.leftDetail?.riskLevel || 'low' }}</span>
              </div>
              <div v-for="item in compareSummary?.leftDetail?.topErrorTypes || []" :key="`left-error-${item}`" class="compare-insight-item">
                <span>{{ item }}</span>
              </div>
              <div v-for="item in compareSummary?.leftDetail?.highlights || []" :key="`left-${item}`" class="compare-insight-item">
                <span>{{ item }}</span>
              </div>
            </div>
            <div class="compare-insight-list">
              <div class="compare-insight-item">
                <strong>{{ compareSummary?.rightDetail?.agentType || compareAgent }}</strong>
                <span>{{ compareSummary?.rightDetail?.summary || '-' }}</span>
                <span>{{ compareSummary?.rightDetail?.healthSummary || '-' }}</span>
                <span>{{ compareSummary?.rightDetail?.policySummary || '-' }}</span>
                <span>Calls {{ compareSummary?.rightDetail?.totalCalls ?? 0 }} | Failure {{ compareSummary?.rightDetail?.failureRateLabel || '0%' }} | Risk {{ compareSummary?.rightDetail?.riskLevel || 'low' }}</span>
              </div>
              <div v-for="item in compareSummary?.rightDetail?.topErrorTypes || []" :key="`right-error-${item}`" class="compare-insight-item">
                <span>{{ item }}</span>
              </div>
              <div v-for="item in compareSummary?.rightDetail?.highlights || []" :key="`right-${item}`" class="compare-insight-item">
                <span>{{ item }}</span>
              </div>
            </div>
          </div>
        </div>
        <div v-if="compareSummary?.changeComparison?.length" class="compare-insights compare-detail-panel">
          <div class="compare-insights-title compare-insights-head">
            <span>Change comparison</span>
            <select v-model="compareChangeFilter" class="agent-select compare-filter-select">
              <option value="all">All changes</option>
              <option value="high">High severity</option>
              <option value="diverged">Diverged only</option>
            </select>
          </div>
          <div class="compare-insight-list">
            <div v-for="item in filteredCompareChanges" :key="`${item.type}-${item.label}`" class="compare-insight-item compare-detail-item">
              <strong>{{ item.label }}</strong>
              <span>Left: {{ item.leftSummary }}</span>
              <span>Right: {{ item.rightSummary }}</span>
              <span>{{ item.direction }} / {{ item.severity }}</span>
              <span>{{ item.suggestedAction }}</span>
            </div>
          </div>
        </div>
        <EmptyState
          v-else
          icon="C"
          badge="Compare"
          title="暂无可对比的 Agent 数据"
          description="至少需要两个可用助手，且对比接口要返回统计结果。"
          variant="compact"
        />
      </div>

      <div class="card section-card">
        <div class="card-header">
          <div>
            <div class="card-title">日志生命周期治理</div>
            <div class="card-subtitle">{{ lifecycleSummary?.summary || '按 active / archive / delete 三段查看日志保留策略。' }}</div>
          </div>
          <button class="btn btn-ghost btn-sm" type="button" @click="previewLifecycleCleanup">预览清理</button>
        </div>
        <button class="btn btn-ghost btn-sm" type="button" @click="findArchivedTrace">Find archived trace</button>
        <div class="lifecycle-automation" v-if="lifecycleSummary">
          <span class="tag" :class="{ warning: !lifecycleSummary.automationEnabled }">
            {{ lifecycleSummary.automationEnabled ? 'Auto lifecycle on' : 'Auto lifecycle off' }}
          </span>
          <span class="tag">{{ lifecycleSummary.automationDryRun ? 'Dry run' : 'Execute cleanup' }}</span>
          <span class="tag">Interval {{ formatInterval(lifecycleSummary.automationIntervalMs) }}</span>
          <span class="tag" v-if="lifecycleSummary.lastArchiveManifestAt">
            Last archive {{ formatDateTime(lifecycleSummary.lastArchiveManifestAt) }}
          </span>
        </div>
        <div v-if="lifecycleSummary?.lastArchiveManifestPath || lifecycleSummary?.archiveManifestDir" class="compare-insights lifecycle-archive-box">
          <div class="compare-insights-title">Archive manifest</div>
          <div class="compare-insight-list">
            <div class="compare-insight-item" v-if="lifecycleSummary?.archiveManifestDir">
              <strong>Manifest dir</strong>
              <span>{{ lifecycleSummary.archiveManifestDir }}</span>
            </div>
            <div class="compare-insight-item" v-if="lifecycleSummary?.lastArchiveBundleDir">
              <strong>Latest bundle</strong>
              <span>{{ lifecycleSummary.lastArchiveBundleDir }}</span>
            </div>
            <div class="compare-insight-item" v-if="lifecycleSummary?.lastArchiveManifestPath">
              <strong>Latest manifest</strong>
              <span>{{ lifecycleSummary.lastArchiveManifestPath }}</span>
            </div>
            <div class="compare-insight-item" v-if="(lifecycleSummary?.lastArchiveExportedRecordCount ?? 0) > 0">
              <strong>Exported records</strong>
              <span>{{ lifecycleSummary?.lastArchiveExportedRecordCount }}</span>
            </div>
          </div>
        </div>
        <div v-if="archiveDetail?.artifacts?.length" class="rank-list lifecycle-artifact-list">
          <article v-for="artifact in archiveDetail.artifacts" :key="artifact.path" class="rank-item lifecycle-item">
            <div class="rank-item-head">
              <strong>{{ lifecycleBucketLabel(artifact.type) }}</strong>
              <span>{{ artifact.recordCount }} records</span>
            </div>
            <div class="rank-item-meta lifecycle-meta">
              <span>{{ artifact.path }}</span>
              <button class="btn btn-ghost btn-sm" type="button" @click="previewArchiveArtifact(artifact.type)">Preview</button>
            </div>
          </article>
        </div>
        <div v-if="archiveDetail?.operationHints?.length || archiveDetail?.sampleLimit || archiveDetail?.exportBatchSize" class="compare-insights lifecycle-archive-box">
          <div class="compare-insights-title">Archive operations</div>
          <div class="compare-insight-list">
            <div class="compare-insight-item">
              <strong>Archive config</strong>
              <span>sampleLimit={{ archiveDetail?.sampleLimit ?? 0 }}, exportBatchSize={{ archiveDetail?.exportBatchSize ?? 0 }}</span>
            </div>
            <div v-for="item in archiveDetail?.operationHints || []" :key="item" class="compare-insight-item">
              <span>{{ item }}</span>
            </div>
          </div>
        </div>
        <div v-if="archivePreview?.items?.length" class="compare-insights lifecycle-archive-box">
          <div class="compare-insights-title">Archive preview</div>
          <div class="compare-insight-list">
            <div class="compare-insight-item">
              <strong>{{ lifecycleBucketLabel(archivePreview.artifactType) }}</strong>
              <span>{{ archivePreview.artifactPath || '-' }}</span>
            </div>
            <div v-for="item in archivePreview.items" :key="`${archivePreview.artifactType}-${item.lineNumber}`" class="compare-insight-item">
              <strong>Line {{ item.lineNumber }}</strong>
              <span class="archive-preview-line">{{ item.content }}</span>
            </div>
          </div>
        </div>
        <div v-if="archiveDetail?.samples?.length" class="compare-insights lifecycle-archive-box">
          <div class="compare-insights-title">Archive samples</div>
          <div class="compare-insight-list">
            <div v-for="sample in archiveDetail.samples" :key="`${sample.type}-${sample.id}-${sample.traceId}`" class="compare-insight-item">
              <strong>{{ lifecycleBucketLabel(sample.type) }} {{ sample.traceId || sample.id || '-' }}</strong>
              <span>{{ sample.summary || sample.sessionId || '-' }}</span>
            </div>
          </div>
        </div>
        <div class="workbench-metric-grid lifecycle-metrics">
          <article class="card workbench-metric-item">
            <div class="workbench-metric-label">Active</div>
            <div class="workbench-metric-value">{{ lifecycleSummary?.totalActiveCount ?? 0 }}</div>
            <div class="workbench-metric-sub">热数据保留中</div>
          </article>
          <article class="card workbench-metric-item">
            <div class="workbench-metric-label">Archive</div>
            <div class="workbench-metric-value">{{ lifecycleSummary?.totalArchiveCandidateCount ?? 0 }}</div>
            <div class="workbench-metric-sub">建议转冷数据</div>
          </article>
          <article class="card workbench-metric-item">
            <div class="workbench-metric-label">Cold</div>
            <div class="workbench-metric-value">{{ lifecycleSummary?.totalColdDataCount ?? 0 }}</div>
            <div class="workbench-metric-sub">Archived cold data</div>
          </article>
          <article class="card workbench-metric-item">
            <div class="workbench-metric-label">Delete</div>
            <div class="workbench-metric-value">{{ lifecycleSummary?.totalDeleteCandidateCount ?? 0 }}</div>
            <div class="workbench-metric-sub">已到清理窗口</div>
          </article>
        </div>
        <div v-if="archivedTrace" class="compare-insights lifecycle-archive-box">
          <div class="compare-insights-title">Archived trace lookup</div>
          <div class="compare-insight-list">
            <div class="compare-insight-item">
              <strong>{{ archivedTrace.found ? 'Found' : 'Not found' }}</strong>
              <span>{{ archivedTrace.traceId || '-' }}</span>
            </div>
            <div v-if="archivedTrace.found" class="compare-insight-item">
              <strong>{{ archivedTrace.artifactPath || '-' }}</strong>
              <span>{{ archivedTrace.summary || '-' }}</span>
            </div>
            <div v-if="archivedTrace?.trace" class="compare-insight-item">
              <strong>Replay source</strong>
              <span>{{ archivedTrace.trace.status }} / {{ archivedTrace.trace.createdAt || '-' }}</span>
            </div>
            <div v-if="archivedTrace?.found && archivedTrace?.replayable" class="compare-insight-item">
              <button class="btn btn-ghost btn-sm" type="button" @click="replayArchivedTrace">Replay archived trace</button>
              <span>{{ archivedTrace.traceId || '-' }}</span>
            </div>
          </div>
        </div>
        <div v-if="lifecycleSummary?.buckets?.length" class="rank-list">
          <article v-for="bucket in lifecycleSummary.buckets" :key="bucket.type" class="rank-item lifecycle-item">
            <div class="rank-item-head">
              <strong>{{ lifecycleBucketLabel(bucket.type) }}</strong>
              <span>{{ bucket.archiveAfterDays }}d / {{ bucket.deleteAfterDays }}d</span>
            </div>
            <div class="rank-item-meta lifecycle-meta">
              <span>Active {{ bucket.activeCount }}</span>
              <span>Archive {{ bucket.archiveCandidateCount }}</span>
              <span>Delete {{ bucket.deleteCandidateCount }}</span>
            </div>
          </article>
        </div>
        <EmptyState
          v-else
          icon="L"
          badge="Lifecycle"
          title="暂无日志生命周期数据"
          description="当前助手还没有可汇总的审计、工具日志或多智能体轨迹。"
          variant="compact"
        />
      </div>
    </div>

    <div class="card section-card agent-quick-panel">
      <div class="card-header">
        <div>
          <div class="card-title">助手切换</div>
          <div class="card-subtitle">先选助手，再统一查看它的健康、趋势、排行和排障入口。</div>
        </div>
      </div>
      <div class="agent-card-grid">
        <button
          v-for="item in agentCards"
          :key="item.value"
          type="button"
          class="agent-card"
          :class="{ active: item.value === selectedAgent }"
          @click="selectAgent(item.value)"
        >
          <div class="agent-card-icon" :style="{ background: `${item.color}18`, color: item.color }">{{ item.icon }}</div>
          <div class="agent-card-title">{{ item.name }}</div>
          <div class="agent-card-desc">{{ item.desc }}</div>
        </button>
      </div>
    </div>

    <ChatAgentDiagnosticsPanel :agent-type-override="selectedAgent" />
  </div>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { cleanupAgentLogs, compareAgentWorkbench, findLatestArchivedTrace, getAgentLogLifecycleSummary, getAgentWorkbenchSummary, getLatestAgentLogArchive, previewLatestAgentLogArchive, replayLatestArchivedTrace } from '@/api/agent'
import type { AgentArchivedTraceLookupResponse, AgentLogArchiveDetailResponse, AgentLogArchivePreviewResponse, AgentLogLifecycleSummaryResponse, AgentWorkbenchCompareResponse, AgentWorkbenchSummaryResponse } from '@/api/types'
import ChatAgentDiagnosticsPanel from '@/components/chat/ChatAgentDiagnosticsPanel.vue'
import EmptyState from '@/components/common/EmptyState.vue'
import SkeletonBlock from '@/components/common/SkeletonBlock.vue'
import { useAgentMetadata } from '@/composables/useAgentMetadata'
import { useToast } from '@/composables/useToast'

const route = useRoute()
const router = useRouter()
const { showToast } = useToast()
const { agentList, getAgentConfig, loadAgentMetadata } = useAgentMetadata()

const fallbackAgent = 'rd'
const selectedAgent = ref(typeof route.query.agent === 'string' ? route.query.agent : fallbackAgent)
const compareAgent = ref('')
const summaryLoading = ref(false)
const summaryError = ref('')
const workbenchSummary = ref<AgentWorkbenchSummaryResponse | null>(null)
const compareSummary = ref<AgentWorkbenchCompareResponse | null>(null)
const compareDetailsOpen = ref(false)
const compareChangeFilter = ref<'all' | 'high' | 'diverged'>('all')
const lifecycleSummary = ref<AgentLogLifecycleSummaryResponse | null>(null)
const archiveDetail = ref<AgentLogArchiveDetailResponse | null>(null)
const archivePreview = ref<AgentLogArchivePreviewResponse | null>(null)
const archivedTrace = ref<AgentArchivedTraceLookupResponse | null>(null)

const agentOptions = computed(() =>
  agentList.value.map((item) => ({
    value: item.agentType,
    label: item.name
  }))
)

const compareAgentOptions = computed(() =>
  agentList.value
    .filter((item) => item.agentType !== selectedAgent.value)
    .map((item) => ({
      value: item.agentType,
      label: item.name
    }))
)

const agentCards = computed(() =>
  agentList.value.map((item) => ({
    value: item.agentType,
    name: item.name,
    icon: item.icon,
    color: item.color,
    desc: item.desc
  }))
)

const currentConfig = computed(() => getAgentConfig(selectedAgent.value))
const max24hCalls = computed(() => Math.max(...(workbenchSummary.value?.last24hTrend?.map((item) => item.totalCalls) || [1])))
const max7dCalls = computed(() => Math.max(...(workbenchSummary.value?.last7dTrend?.map((item) => item.totalCalls) || [1])))
const max4wCalls = computed(() => Math.max(...(workbenchSummary.value?.last4wTrend?.map((item) => item.totalCalls) || [1])))
const maxErrorCount = computed(() => Math.max(...(workbenchSummary.value?.errorTypes?.map((item) => item.count) || [1])))
const compareInsightLines = computed(() => compareSummary.value?.insights || [])
const filteredCompareChanges = computed(() => {
  const items = compareSummary.value?.changeComparison || []
  if (compareChangeFilter.value === 'high') {
    return items.filter((item) => item.severity === 'high')
  }
  if (compareChangeFilter.value === 'diverged') {
    return items.filter((item) => item.direction === 'diverged')
  }
  return items
})
const policyRiskLabel = computed(() => {
  const level = workbenchSummary.value?.runtimePolicySummary?.riskLevel || 'low'
  if (level === 'high') return '高风险'
  if (level === 'medium') return '中风险'
  return '低风险'
})

const failureRateLabel = computed(() => {
  const failureCalls = workbenchSummary.value?.failureCalls ?? 0
  const totalCalls = workbenchSummary.value?.totalCalls ?? 0
  if (!totalCalls) return '0%'
  return `${Math.round((failureCalls / totalCalls) * 100)}%`
})

function normalizeSelectedAgent(agentType?: string | null) {
  if (agentType && agentList.value.some((item) => item.agentType === agentType)) {
    return agentType
  }
  return agentList.value[0]?.agentType || fallbackAgent
}

function selectAgent(agentType: string) {
  selectedAgent.value = normalizeSelectedAgent(agentType)
}

function normalizeCompareAgent(agentType?: string | null) {
  const next = normalizeSelectedAgent(agentType)
  if (next === selectedAgent.value) {
    return agentList.value.find((item) => item.agentType !== selectedAgent.value)?.agentType || next
  }
  return next
}

function formatPercent(value?: number) {
  if (!value) return '0%'
  return `${Math.round(value * 100)}%`
}

function lifecycleBucketLabel(type?: string) {
  if (type === 'audit') return 'Chat Audit'
  if (type === 'tool-audit') return 'Tool Audit'
  if (type === 'trace') return 'Multi-Agent Trace'
  return type || '-'
}

function formatInterval(ms?: number) {
  if (!ms) return 'manual'
  if (ms % 3600000 === 0) return `${ms / 3600000}h`
  if (ms % 60000 === 0) return `${ms / 60000}m`
  return `${ms}ms`
}

function formatDateTime(value?: string) {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')} ${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
}

function barWidth(value: number, max: number) {
  if (!max) return 0
  return Math.max(8, Math.round((value / max) * 100))
}

async function reloadWorkbenchSummary() {
  summaryLoading.value = true
  summaryError.value = ''
  try {
    workbenchSummary.value = await getAgentWorkbenchSummary(selectedAgent.value)
    lifecycleSummary.value = await getAgentLogLifecycleSummary(selectedAgent.value)
    archiveDetail.value = await getLatestAgentLogArchive(selectedAgent.value)
    archivePreview.value = null
    archivedTrace.value = null
    if (compareAgent.value) {
      compareSummary.value = await compareAgentWorkbench(selectedAgent.value, compareAgent.value)
      compareDetailsOpen.value = false
      compareChangeFilter.value = 'all'
    }
  } catch (error) {
    summaryError.value = error instanceof Error ? error.message : 'Agent 聚合统计加载失败'
  } finally {
    summaryLoading.value = false
  }
}

async function findArchivedTrace() {
  const traceId = workbenchSummary.value?.latestTraceId
  if (!traceId) {
    showToast('No trace id available for archive lookup')
    return
  }
  try {
    archivedTrace.value = await findLatestArchivedTrace(selectedAgent.value, traceId)
  } catch (error) {
    showToast(error instanceof Error ? error.message : 'Archive trace lookup failed')
  }
}

async function replayArchivedTrace() {
  const traceId = archivedTrace.value?.traceId
  if (!traceId) {
    showToast('No archived trace selected')
    return
  }
  try {
    const replayed = await replayLatestArchivedTrace(selectedAgent.value, traceId)
    showToast(`Archived trace replayed: ${replayed.traceId}`)
    archivedTrace.value = {
      ...(archivedTrace.value || { agentType: selectedAgent.value, found: true }),
      trace: replayed
    }
  } catch (error) {
    showToast(error instanceof Error ? error.message : 'Archived trace replay failed')
  }
}

async function previewArchiveArtifact(artifactType: string) {
  try {
    archivePreview.value = await previewLatestAgentLogArchive(selectedAgent.value, artifactType, 5)
  } catch (error) {
    showToast(error instanceof Error ? error.message : 'Archive preview load failed')
  }
}

async function previewLifecycleCleanup() {
  try {
    const result = await cleanupAgentLogs(selectedAgent.value, { dryRun: true })
    showToast(`日志清理预览：${result.summary}`)
  } catch (error) {
    showToast(error instanceof Error ? error.message : '日志清理预览失败')
  }
}

function openChatWithAgent(traceId = '', sessionId = '') {
  router.push({
    name: 'chat',
    query: {
      agent: selectedAgent.value,
      source: 'agent-workbench',
      traceId: traceId || undefined,
      sessionId: sessionId || undefined
    }
  })
}

function openMonitorTrace(traceId = '') {
  router.push({
    name: 'monitor',
    query: {
      agent: selectedAgent.value,
      source: 'agent-workbench',
      traceId: traceId || undefined
    }
  })
}

async function copyWorkbenchLink() {
  const query = new URLSearchParams({ agent: selectedAgent.value })
  const url = `${window.location.origin}/agents?${query.toString()}`
  try {
    await navigator.clipboard.writeText(url)
    showToast('Agent 工作台链接已复制')
  } catch {
    showToast('链接复制失败')
  }
}

async function copyTraceId(traceId: string) {
  if (!traceId) return
  try {
    await navigator.clipboard.writeText(traceId)
    showToast('TraceId 已复制')
  } catch {
    showToast('TraceId 复制失败')
  }
}

async function copyLatestTraceId() {
  await copyTraceId(workbenchSummary.value?.latestTraceId || '')
}

void loadAgentMetadata()

watch(
  () => route.query.agent,
  (agent) => {
    if (typeof agent === 'string') {
      selectedAgent.value = normalizeSelectedAgent(agent)
    }
  },
  { immediate: true }
)

watch(
  agentList,
  (items) => {
    if (!items.length) return
    selectedAgent.value = normalizeSelectedAgent(selectedAgent.value)
    compareAgent.value = normalizeCompareAgent(compareAgent.value)
  },
  { immediate: true }
)

watch(
  compareAgent,
  async (agent) => {
    if (!agentList.value.length) return
    const normalized = normalizeCompareAgent(agent)
    if (normalized !== agent) {
      compareAgent.value = normalized
      return
    }
    if (!normalized || normalized === selectedAgent.value) {
      compareSummary.value = null
      compareDetailsOpen.value = false
      return
    }
    try {
      compareSummary.value = await compareAgentWorkbench(selectedAgent.value, normalized)
      compareDetailsOpen.value = false
    } catch (error) {
      showToast(error instanceof Error ? error.message : 'Agent 对比数据加载失败')
    }
  },
  { immediate: true }
)

watch(
  selectedAgent,
  async (agent) => {
    const normalized = normalizeSelectedAgent(agent)
    if (normalized !== agent) {
      selectedAgent.value = normalized
      return
    }
    const currentQueryAgent = typeof route.query.agent === 'string' ? route.query.agent : ''
    if (currentQueryAgent !== normalized) {
      await router.replace({ name: 'agents', query: { agent: normalized } })
    }
    await reloadWorkbenchSummary()
  },
  { immediate: true }
)
</script>

<style scoped>
.section-card { margin-bottom: 16px; }
.agent-select { min-width: 180px; }
.agent-overview-grid,
.workbench-metric-grid,
.workbench-focus-grid,
.workbench-two-column {
  display: grid;
  gap: 12px;
  margin-bottom: 16px;
}
.agent-overview-grid,
.workbench-metric-grid,
.workbench-focus-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}
.workbench-two-column {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}
.single-column {
  grid-template-columns: 1fr;
}
.agent-overview-card,
.workbench-metric-item {
  padding: 16px;
  border-radius: 18px;
  border: 1px solid var(--border);
  background:
    radial-gradient(circle at top right, rgba(79, 142, 247, 0.08), transparent 38%),
    rgba(255, 255, 255, 0.03);
}
.agent-overview-card.warning {
  border-color: rgba(245, 158, 11, 0.3);
  background: rgba(245, 158, 11, 0.08);
}
.agent-overview-kicker,
.workbench-metric-label {
  font-size: 11px;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  color: var(--text3);
}
.agent-overview-title-row {
  display: flex;
  gap: 14px;
  align-items: center;
  margin-top: 12px;
}
.agent-avatar,
.agent-card-icon {
  width: 48px;
  height: 48px;
  border-radius: 16px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-weight: 700;
  flex-shrink: 0;
}
.agent-overview-title,
.agent-card-title,
.workbench-metric-value {
  color: var(--text);
  font-size: 16px;
  font-weight: 700;
}
.workbench-metric-value.metric-sm { font-size: 14px; }
.agent-overview-desc,
.agent-card-desc,
.workbench-metric-sub {
  margin-top: 6px;
  color: var(--text3);
  font-size: 12px;
  line-height: 1.7;
}
.action-card,
.workbench-focus-item,
.rank-item {
  text-align: left;
  cursor: pointer;
  transition: transform var(--transition), border-color var(--transition), box-shadow var(--transition);
}
.action-card:hover,
.agent-card:hover,
.workbench-focus-item:hover,
.rank-item:hover {
  transform: translateY(-2px);
  border-color: rgba(79, 142, 247, 0.24);
  box-shadow: 0 12px 24px rgba(15, 23, 42, 0.08);
}
.workbench-focus-item,
.rank-item {
  padding: 16px;
  border-radius: 18px;
  border: 1px solid var(--border);
  background: rgba(255, 255, 255, 0.03);
  display: grid;
  gap: 6px;
}
.workbench-focus-item strong { color: var(--text); font-size: 14px; }
.workbench-focus-item span { color: var(--text3); font-size: 12px; line-height: 1.7; }
.compare-select { min-width: 200px; }
.compare-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}
.compare-card {
  border: 1px solid var(--border);
  border-radius: 18px;
  padding: 16px;
  background:
    radial-gradient(circle at top right, rgba(34, 197, 94, 0.08), transparent 40%),
    rgba(255, 255, 255, 0.03);
}
.compare-agent-name {
  color: var(--text);
  font-size: 15px;
  font-weight: 700;
}
.compare-metric-list {
  display: grid;
  gap: 10px;
  margin-top: 14px;
}
.compare-metric-row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  color: var(--text2);
  font-size: 13px;
}
.compare-metric-row strong {
  color: var(--text);
  font-weight: 700;
}
.compare-insights {
  margin-top: 12px;
  padding: 14px 16px;
  border: 1px dashed rgba(79, 142, 247, 0.24);
  border-radius: 18px;
  background: rgba(79, 142, 247, 0.05);
}
.compare-insights-title {
  color: var(--text);
  font-size: 13px;
  font-weight: 700;
}
.compare-insights-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.compare-insight-list {
  display: grid;
  gap: 8px;
  margin-top: 10px;
}
.compare-insight-item {
  color: var(--text2);
  font-size: 13px;
  line-height: 1.6;
  display: grid;
  gap: 4px;
}
.compare-insight-item strong {
  color: var(--text);
  font-size: 13px;
}
.compare-insight-item span {
  color: var(--text2);
}
.compare-detail-panel {
  border-style: solid;
}
.compare-detail-item {
  padding-bottom: 8px;
  border-bottom: 1px dashed rgba(148, 163, 184, 0.2);
}
.compare-detail-item:last-child {
  padding-bottom: 0;
  border-bottom: 0;
}
.lifecycle-automation {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}
.lifecycle-metrics {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}
.lifecycle-archive-box {
  margin-bottom: 12px;
}
.archive-preview-line {
  word-break: break-all;
  font-family: Consolas, 'Courier New', monospace;
}
.lifecycle-item {
  cursor: default;
}
.lifecycle-meta {
  margin-top: 10px;
}
.trend-list,
.rank-list,
.error-type-list,
.failure-list {
  display: grid;
  gap: 12px;
}
.trend-item,
.error-type-item {
  padding: 12px 14px;
  border-radius: 16px;
  border: 1px solid var(--border);
  background: rgba(255, 255, 255, 0.03);
}
.trend-head,
.rank-item-head,
.error-type-head {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
}
.trend-head strong,
.rank-item-head strong,
.error-type-head strong {
  color: var(--text);
}
.trend-head span,
.rank-item-head span,
.error-type-head span,
.trend-meta,
.rank-item-meta {
  color: var(--text3);
  font-size: 12px;
}
.trend-meta,
.rank-item-meta {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  margin-top: 8px;
}
.trend-bar-track {
  position: relative;
  height: 10px;
  border-radius: 999px;
  background: rgba(148, 163, 184, 0.14);
  overflow: hidden;
  margin-top: 10px;
}
.trend-bar {
  position: absolute;
  left: 0;
  top: 0;
  height: 100%;
  border-radius: 999px;
}
.trend-bar.total { background: rgba(59, 130, 246, 0.45); }
.trend-bar.failure { background: rgba(239, 68, 68, 0.5); }
.failure-item {
  border: 1px solid rgba(239, 68, 68, 0.14);
  background: rgba(239, 68, 68, 0.04);
  border-radius: 18px;
  padding: 14px 16px;
}
.failure-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}
.failure-title {
  color: #b91c1c;
  font-size: 14px;
  font-weight: 700;
}
.failure-meta,
.failure-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  margin-top: 8px;
  color: var(--text3);
  font-size: 12px;
}
.failure-summary {
  margin-top: 10px;
  color: var(--text2);
  font-size: 13px;
  line-height: 1.7;
}
.agent-card-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}
.agent-card {
  padding: 16px;
  border-radius: 18px;
  border: 1px solid var(--border);
  background: rgba(255, 255, 255, 0.03);
  text-align: left;
  cursor: pointer;
  transition: transform var(--transition), border-color var(--transition), background var(--transition), box-shadow var(--transition);
}
.agent-card.active {
  border-color: rgba(79, 142, 247, 0.28);
  background: rgba(79, 142, 247, 0.08);
  box-shadow: 0 14px 30px rgba(79, 142, 247, 0.12);
}
.agent-card-title { margin-top: 12px; font-size: 14px; }
.tag.warning {
  background: rgba(245, 158, 11, 0.12);
  color: #b45309;
}
@media (max-width: 1100px) {
  .agent-card-grid,
  .compare-grid,
  .workbench-two-column {
    grid-template-columns: 1fr;
  }
}
@media (max-width: 960px) {
  .agent-overview-grid,
  .agent-card-grid,
  .compare-grid,
  .lifecycle-metrics,
  .workbench-metric-grid,
  .workbench-focus-grid,
  .workbench-two-column {
    grid-template-columns: 1fr;
  }
  .page-hero-actions {
    width: 100%;
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
  }
  .agent-select { width: 100%; }
  .failure-head { flex-direction: column; align-items: stretch; }
}
</style>
