export interface AgentConfig {
  name: string
  icon: string
  color: string
  desc: string
}

export const AGENT_CONFIG: Record<string, AgentConfig> = {
  rd: { name: '研发助手', icon: '🛠', color: '#4f8ef7', desc: '代码审查、技术文档、Bug 分析' },
  sales: { name: '销售助手', icon: '💼', color: '#3dd68c', desc: '报价查询、客户分析、产品推荐' },
  hr: { name: 'HR 助手', icon: '👥', color: '#9d7cf4', desc: '假期查询、审批申请、政策解答' },
  finance: { name: '财务助手', icon: '📊', color: '#f5a623', desc: '报表查询、收支分析、预算对比' },
  'supply-chain': { name: '供应链助手', icon: '📦', color: '#2dd4bf', desc: '库存查询、采购跟踪、补货建议' },
  qc: { name: '质控助手', icon: '🧪', color: '#f06060', desc: '不良品分析、质检报告、趋势预警' },
  weather: { name: '天气助手', icon: '⛅', color: '#38bdf8', desc: '实时天气、多日预报、出行建议' },
  search: { name: '搜索助手', icon: '🔎', color: '#6366f1', desc: '网络搜索、网页摘要、信息整合' },
  'data-analysis': { name: '数据分析助手', icon: '📈', color: '#f59e0b', desc: 'SQL 查询、图表生成、数据统计' },
  code: { name: '代码助手', icon: '💻', color: '#10b981', desc: '代码执行、仓库检索、代码审查' },
  mcp: { name: 'MCP 助手', icon: '🧩', color: '#8b5cf6', desc: 'MCP 协议、外部工具、动态能力' },
  multi: { name: 'Multi-Agent', icon: '🤝', color: '#f5a623', desc: '复杂跨域任务自动拆解' }
}

export const AGENT_LABELS: Record<string, string> = {
  RdAssistantAgent: '研发助手',
  SalesAssistantAgent: '销售助手',
  HrAssistantAgent: 'HR 助手',
  FinanceAssistantAgent: '财务助手',
  SupplyChainAssistantAgent: '供应链助手',
  QcAssistantAgent: '质控助手',
  WeatherAssistantAgent: '天气助手',
  SearchAssistantAgent: '搜索助手',
  DataAnalysisAssistantAgent: '数据分析助手',
  CodeAssistantAgent: '代码助手',
  McpAssistantAgent: 'MCP 助手',
  MultiAssistantAgent: 'Multi-Agent 协作',
  rd: '研发助手',
  sales: '销售助手',
  hr: 'HR 助手',
  finance: '财务助手',
  'supply-chain': '供应链助手',
  qc: '质控助手',
  weather: '天气助手',
  search: '搜索助手',
  'data-analysis': '数据分析助手',
  code: '代码助手',
  mcp: 'MCP 助手',
  multi: 'Multi-Agent 协作'
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
  hr: 'HR 助手',
  finance: '财务助手',
  'supply-chain': '供应链助手',
  qc: '质控助手',
  weather: '天气助手',
  search: '搜索助手',
  'data-analysis': '数据分析助手',
  code: '代码助手',
  mcp: 'MCP 助手',
  multi: 'Multi-Agent 协作'
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
  { value: 'auto', label: '🎯 自动路由' },
  { value: 'deepseek-chat', label: 'DeepSeek Chat' },
  { value: 'gpt-4o-mini', label: 'GPT-4o Mini' },
  { value: 'gpt-4o', label: 'GPT-4o' },
  { value: 'qwen-plus', label: '通义千问 Plus' },
  { value: 'llama3-local', label: 'Llama3（本地）' }
]

export const QUICK_PROMPTS = [
  { label: '代码审查', text: '帮我 review 这段代码，重点看潜在缺陷和边界条件。' },
  { label: '查 Bug', text: '查询 Jira 项目里最近需要优先处理的 Bug。' },
  { label: '查文档', text: '查询 Confluence 中和当前模块相关的设计文档。' },
  { label: '错误分析', text: '分析这段异常日志，给出可能原因和排查步骤。' }
]

export const MOCK_RESPONSES: Record<string, (msg: string) => string> = {
  rd: (msg) => {
    if (/bug|jira/i.test(msg)) {
      return '已进入研发演示模式。\n\n示例结果：\n- PROJ-101 登录偶发超时，状态：处理中\n- PROJ-102 导出格式异常，状态：待验证\n\n接入真实后端后会返回实时工单数据。'
    }
    if (/代码|review/i.test(msg)) {
      return '已进入研发演示模式。\n\n你可以继续输入代码片段，我会按以下维度给出示例审查结果：\n1. 代码规范与命名\n2. 潜在 Bug 与边界条件\n3. 性能优化建议\n4. 安全风险检查'
    }
    return `已切换到研发助手演示模式。\n\n收到的问题：${msg.slice(0, 40)}\n\n当前返回的是本地模拟内容，真实环境会接入 Jira、Confluence、SonarQube 等系统。`
  },
  sales: (msg) => `销售助手演示模式已启用。\n\n收到的问题：${msg.slice(0, 40)}\n\n当前展示的是本地模拟结果，真实环境会返回报价、客户分层与推荐方案。`,
  hr: (msg) => `HR 助手演示模式已启用。\n\n收到的问题：${msg.slice(0, 40)}\n\n当前展示的是本地模拟结果，真实环境会返回假期余额、审批状态和制度问答。`,
  finance: (msg) => `财务助手演示模式已启用。\n\n收到的问题：${msg.slice(0, 40)}\n\n当前展示的是本地模拟结果，真实环境会返回财务报表和预算分析数据。`,
  'supply-chain': (msg) => `供应链助手演示模式已启用。\n\n收到的问题：${msg.slice(0, 40)}\n\n当前展示的是本地模拟结果，真实环境会返回库存、采购与补货建议。`,
  qc: (msg) => `质控助手演示模式已启用。\n\n收到的问题：${msg.slice(0, 40)}\n\n当前展示的是本地模拟结果，真实环境会返回质检报表和趋势预警。`,
  weather: (msg) => `天气助手演示模式已启用。\n\n收到的问题：${msg.slice(0, 40)}\n\n当前展示的是本地模拟结果，真实环境会返回实时天气和预报数据。`,
  search: (msg) => `搜索助手演示模式已启用。\n\n收到的问题：${msg.slice(0, 40)}\n\n当前展示的是本地模拟结果，真实环境会返回联网搜索摘要。`,
  'data-analysis': (msg) => `数据分析助手演示模式已启用。\n\n收到的问题：${msg.slice(0, 40)}\n\n当前展示的是本地模拟结果，真实环境会返回 SQL 查询与统计分析结果。`,
  code: (msg) => `代码助手演示模式已启用。\n\n收到的问题：${msg.slice(0, 40)}\n\n当前展示的是本地模拟结果，真实环境会返回仓库检索与代码审查结果。`,
  mcp: () => 'MCP 助手演示模式已启用。\n\n当前展示的是本地模拟结果，真实环境需要先在服务端启用 MCP 配置。',
  multi: () => 'Multi-Agent 演示模式已启用。\n\n当前展示的是本地模拟结果，真实环境会把复杂任务拆分给多个专业 Agent 协同处理。'
}

export const DEMO_KNOWLEDGE_BASES = [
  { id: 'kb-001', name: '企业通用知识库', description: '制度流程、HR 政策、行政规范等通用文档', status: 'active', documentCount: 142, totalChunks: 8924 },
  { id: 'kb-002', name: '研发知识库', description: '技术规范、API 文档、架构设计、开发指南', status: 'active', documentCount: 89, totalChunks: 5211 },
  { id: 'kb-003', name: '销售知识库', description: '产品手册、报价规则、竞品分析、案例库', status: 'active', documentCount: 63, totalChunks: 3456 }
]

export const MOCK_RAG_RESPONSES: Record<string, string> = {
  'kb-001': '当前为企业通用知识库演示模式。\n\n示例回答：员工年假需要至少提前 3 个工作日在 OA 系统提交申请，超过 5 天需部门负责人审批。',
  'kb-002': '当前为研发知识库演示模式。\n\n示例回答：接口命名应遵循 RESTful 风格，统一使用 `/api/v{n}/` 前缀，查询参数使用 camelCase。',
  'kb-003': '当前为销售知识库演示模式。\n\n示例回答：A 级客户可申请更高折扣和更长账期，特殊价格需要额外审批。'
}

export const MOCK_RAG_SOURCES = [
  { filename: '制度文件2024.pdf', content: '', score: 0.92 },
  { filename: '员工手册v3.docx', content: '', score: 0.87 },
  { filename: 'HR政策汇编.xlsx', content: '', score: 0.81 }
]
