export interface AgentConfig {
  name: string
  icon: string
  color: string
  desc: string
}

export const AGENT_CONFIG: Record<string, AgentConfig> = {
  rd: { name: '研发助手', icon: '🔬', color: '#4f8ef7', desc: '代码审查 · 技术文档 · Bug分析' },
  sales: { name: '销售助手', icon: '💼', color: '#3dd68c', desc: '报价查询 · 客户分析 · 产品推荐' },
  hr: { name: 'HR 助手', icon: '👥', color: '#9d7cf4', desc: '假期查询 · 审批申请 · 政策解答' },
  finance: { name: '财务助手', icon: '📊', color: '#f5a623', desc: '报表查询 · 收支分析 · 预算对比' },
  'supply-chain': { name: '供应链助手', icon: '📦', color: '#2dd4bf', desc: '库存查询 · 采购跟踪 · 补货建议' },
  qc: { name: '质控助手', icon: '🔍', color: '#f06060', desc: '不良品分析 · 质检报告 · 趋势预警' },
  multi: { name: 'Multi-Agent', icon: '🤝', color: '#f5a623', desc: '复杂跨域任务自动拆解' }
}

export const AGENT_LABELS: Record<string, string> = {
  RdAssistantAgent: '研发助手',
  SalesAssistantAgent: '销售助手',
  HrAssistantAgent: 'HR助手',
  FinanceAssistantAgent: '财务助手',
  SupplyChainAssistantAgent: '供应链助手',
  QcAssistantAgent: '质控助手',
  MultiAssistantAgent: '多Agent协作',
  rd: '研发助手',
  sales: '销售助手',
  hr: 'HR助手',
  finance: '财务助手',
  'supply-chain': '供应链助手',
  qc: '质控助手',
  multi: '多Agent协作'
}

export const PROVIDER_LABELS: Record<string, string> = {
  openai: 'OpenAI',
  deepseek: 'DeepSeek',
  alibaba: '阿里云',
  anthropic: 'Anthropic',
  local: '本地部署'
}

export const SCENE_LABELS: Record<string, string> = {
  code: '编程开发',
  analysis: '数据分析',
  document: '文档处理',
  default: '通用对话'
}

export const BOT_LABELS: Record<string, string> = {
  rd: '研发助手',
  sales: '销售助手',
  hr: 'HR助手',
  finance: '财务助手',
  'supply-chain': '供应链助手',
  qc: '质控助手',
  multi: '多Agent协作'
}

export const SCOPE_LABELS: Record<string, string> = {
  ALL: '全部数据',
  DEPARTMENT: '本部门',
  SELF: '仅本人'
}

export const ROLE_COLORS: Record<string, string> = {
  ROLE_ADMIN: 'blue',
  ROLE_RD: 'purple',
  ROLE_SALES: 'green',
  ROLE_HR: 'amber',
  ROLE_FINANCE: 'amber',
  ROLE_USER: 'blue'
}

export const KB_ICONS: Record<string, string> = {
  'kb-001': '📚',
  'kb-002': '💻',
  'kb-003': '📈'
}

export const MODEL_OPTIONS = [
  { value: 'auto', label: '🔀 自动路由' },
  { value: 'deepseek-chat', label: 'DeepSeek Chat' },
  { value: 'gpt-4o-mini', label: 'GPT-4o Mini' },
  { value: 'gpt-4o', label: 'GPT-4o' },
  { value: 'qwen-plus', label: '通义千问 Plus' },
  { value: 'llama3-local', label: 'Llama3 (本地)' }
]

export const QUICK_PROMPTS = [
  { label: '代码审查', text: '帮我review这段代码：' },
  { label: '查 Bug', text: '查询 Jira 项目的最新 Bug' },
  { label: '查文档', text: '查询 Confluence 中关于 ' },
  { label: '错误分析', text: '分析这个错误信息：' }
]

// Mock responses for offline mode
export const MOCK_RESPONSES: Record<string, (msg: string) => string> = {
  rd: (msg) => {
    if (/bug|jira/i.test(msg)) return '查询到以下 Bug：\n\n**PROJ-101** [严重] 用户登录偶发超时\n状态：处理中 | 负责人：张三\n\n**PROJ-102** [一般] 报表导出格式异常\n状态：待验证 | 负责人：李四'
    if (/代码|review/i.test(msg)) return '请把需要 review 的代码粘贴过来，我会从以下几个方面进行审查：\n\n1. 代码规范与命名\n2. 潜在 Bug 与边界条件\n3. 性能优化建议\n4. 安全风险检查'
    return `收到你的问题：「${msg.slice(0, 30)}」\n\n我正在分析中...这是研发助手的智能回复。在实际环境中，我会连接到 Jira、Confluence、SonarQube 等工具来为你提供更精确的答案。`
  },
  sales: (msg) => {
    if (/报价|价格/i.test(msg)) return '**智能传感器 Model X 报价：**\n\n| 客户等级 | 单价 | 折扣 |\n|---------|------|------|\n| 标准 | ¥1,999 | - |\n| A级客户 | ¥1,699 | 15% |\n| B级客户 | ¥1,799 | 10% |\n\n*大宗采购(>1000台)可额外申请特殊折扣*'
    return `销售助手已收到：「${msg.slice(0, 30)}」\n\n我可以帮你查询报价、分析客户、推荐产品方案。`
  },
  hr: (msg) => {
    if (/假期|年假/i.test(msg)) return '**你的假期余额：**\n\n- 年假：12天（已用3天，余9天）\n- 调休：2天\n- 病假：按实际天数\n\n年假需提前3个工作日申请，超过3天需部门经理审批。'
    return `HR助手已收到：「${msg.slice(0, 30)}」\n\n我可以帮你查询假期、提交申请、解答HR政策。`
  },
  finance: (msg) => {
    if (/报表|收支/i.test(msg)) return '**2024年度财务概览：**\n\n- 营业收入：¥1,250万\n- 营业利润：¥270万\n- 利润率：21.6%\n- 同比增长：+8.3%\n\n详细报表可在财务系统中导出。'
    return `财务助手已收到：「${msg.slice(0, 30)}」\n\n我可以帮你查询报表、分析收支、对比预算。`
  },
  'supply-chain': (msg) => {
    if (/库存|存货/i.test(msg)) return '**当前库存概况：**\n\n- 总库存：5,000件\n- 可用：3,200件\n- 在途：1,800件\n\n⚠️ **高精度传感器芯片** 库存低于安全线，建议立即补货。'
    return `供应链助手已收到：「${msg.slice(0, 30)}」\n\n我可以帮你查询库存、跟踪采购、提供补货建议。`
  },
  qc: (msg) => {
    if (/不良|质检|良率/i.test(msg)) return '**本月质检概况：**\n\n- 良率：98.6%（目标 99%）\n- 主要不良：焊接虚焊 0.8%、外观划痕 0.4%\n- 趋势：良率较上月下降 0.3%\n\n🔴 建议检查焊接工位温度参数。'
    return `质控助手已收到：「${msg.slice(0, 30)}」\n\n我可以帮你分析不良品、查看质检报告、预警趋势。`
  },
  multi: () => '**多Agent协作模式启动**\n\n我会将你的任务自动拆解为子任务，分配给对应的专业Agent：\n\n1. 🔬 **Planner**: 分析任务，制定执行计划\n2. ⚡ **Executor**: 调用工具，执行具体操作\n3. 🔍 **Critic**: 检验结果，确保质量\n\n请描述你的复杂任务，我来协调处理。'
}

// Fallback demo knowledge bases
export const DEMO_KNOWLEDGE_BASES = [
  { id: 'kb-001', name: '企业通用知识库', description: '制度流程、HR政策、行政规范等通用文档', status: 'active', documentCount: 142, totalChunks: 8924 },
  { id: 'kb-002', name: '研发知识库', description: '技术规范、API文档、架构设计、开发指南', status: 'active', documentCount: 89, totalChunks: 5211 },
  { id: 'kb-003', name: '销售知识库', description: '产品手册、报价规则、竞品分析、案例库', status: 'active', documentCount: 63, totalChunks: 3456 }
]

// Mock RAG responses
export const MOCK_RAG_RESPONSES: Record<string, string> = {
  'kb-001': '根据《员工手册2024版》第3章第2节规定：\n\n**年假标准：**\n- 工作满1年不满10年：10天\n- 工作满10年不满20年：15天\n- 工作满20年以上：20天\n\n年假需提前3个工作日在OA系统提交申请，超过5天需部门总监审批。',
  'kb-002': '根据《API开发规范v2.3》：\n\n**接口命名规范：**\n- 使用 RESTful 风格命名\n- URL 路径使用 `/api/v{n}/` 前缀\n- 资源名使用复数形式\n- 查询参数使用 camelCase\n\n**示例：**\n`GET /api/v1/users/{id}/orders?pageSize=20`',
  'kb-003': '根据《客户分级管理办法》：\n\n| 等级 | 年采购额 | 折扣 | 账期 |\n|------|---------|------|------|\n| A级 | >500万 | 85折 | 60天 |\n| B级 | 100-500万 | 90折 | 45天 |\n| C级 | <100万 | 95折 | 30天 |\n\n特殊折扣需总经理审批，单笔超100万可申请专项价格。'
}

export const MOCK_RAG_SOURCES = [
  { filename: '制度文件2024.pdf', content: '', score: 0.92 },
  { filename: '员工手册v3.docx', content: '', score: 0.87 },
  { filename: 'HR政策汇编.xlsx', content: '', score: 0.81 }
]
