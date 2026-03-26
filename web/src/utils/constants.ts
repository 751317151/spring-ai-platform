export interface AgentConfig {
  name: string
  icon: string
  color: string
  desc: string
}

export const AGENT_CONFIG: Record<string, AgentConfig> = {
  rd: { name: '研发助手', icon: 'RD', color: '#4f8ef7', desc: '代码审查、技术文档和缺陷分析' },
  sales: { name: '销售助手', icon: 'SA', color: '#3dd68c', desc: '报价查询、客户分析和产品推荐' },
  hr: { name: 'HR 助手', icon: 'HR', color: '#9d7cf4', desc: '请假政策、审批状态和制度问答' },
  finance: { name: '财务助手', icon: 'FN', color: '#f5a623', desc: '报表分析、费用查询和预算对比' },
  'supply-chain': { name: '供应链助手', icon: 'SC', color: '#2dd4bf', desc: '库存、采购和补货跟踪' },
  qc: { name: '质控助手', icon: 'QC', color: '#f06060', desc: '质量事件、检验报告和趋势预警' },
  weather: { name: '天气助手', icon: 'WX', color: '#38bdf8', desc: '实时天气、预报和出行建议' },
  search: { name: '搜索助手', icon: 'SE', color: '#6366f1', desc: '联网搜索、网页摘要和信息收集' },
  'data-analysis': { name: '数据分析助手', icon: 'DA', color: '#f59e0b', desc: 'SQL 协助、图表生成和统计分析' },
  code: { name: '代码助手', icon: 'CO', color: '#10b981', desc: '仓库检索、代码执行和实现审查' },
  mcp: { name: 'MCP 助手', icon: 'MC', color: '#8b5cf6', desc: 'MCP 工具、协议集成和运行时能力' },
  multi: { name: '多智能体', icon: 'MA', color: '#f5a623', desc: '将复杂任务拆给多个智能体协作处理' }
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
  MultiAssistantAgent: '多智能体',
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
  multi: '多智能体'
}

export const PROVIDER_LABELS: Record<string, string> = {
  openai: 'OpenAI',
  deepseek: 'DeepSeek',
  alibaba: '阿里云',
  anthropic: 'Anthropic',
  local: '本地运行时'
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
  multi: '多智能体'
}

export const SCOPE_LABELS: Record<string, string> = {
  ALL: '全部数据',
  DEPARTMENT: '仅本部门',
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
  'kb-001': '通',
  'kb-002': '研',
  'kb-003': '销'
}

export const MODEL_OPTIONS = [
  { value: 'auto', label: '自动路由' },
  { value: 'deepseek-chat', label: 'DeepSeek Chat' },
  { value: 'gpt-4o-mini', label: 'GPT-4o Mini' },
  { value: 'gpt-4o', label: 'GPT-4o' },
  { value: 'qwen-plus', label: '通义千问 Plus' },
  { value: 'llama3-local', label: 'Llama3 本地版' }
]

export const QUICK_PROMPTS = [
  { label: '代码审查', text: '帮我审查这段代码，重点看潜在缺陷、风险假设和边界情况。' },
  { label: '排查 Bug', text: '查看最近的问题记录，找出当前应该优先处理的 Bug。' },
  { label: '查设计文档', text: '查找当前模块相关的设计文档，并概括核心约束和接口说明。' },
  { label: '分析日志', text: '分析这段异常日志，并给出最可能的原因和下一步排查建议。' }
]

export const MOCK_RESPONSES: Record<string, (msg: string) => string> = {
  rd: (msg) => {
    if (/bug|jira/i.test(msg)) {
      return '当前为研发演示模式。\n\n示例结果：\n- PROJ-101 登录偶发超时，状态：处理中\n- PROJ-102 导出格式异常，状态：待验证\n\n接入真实后端后会返回实时工单数据。'
    }
    if (/code|review|代码|审查/i.test(msg)) {
      return '当前为研发演示模式。\n\n你可以继续粘贴代码片段，我会从以下维度给出示例审查结果：\n1. 命名与规范\n2. 潜在 Bug 与边界情况\n3. 性能优化点\n4. 安全风险'
    }
    return `当前为研发演示模式。\n\n收到的问题：${msg.slice(0, 40)}\n\n当前返回的是本地模拟内容，真实环境中会接入 Jira、Confluence 和 SonarQube。`
  },
  sales: (msg) => `当前为销售演示模式。\n\n收到的问题：${msg.slice(0, 40)}\n\n当前返回的是本地模拟内容，真实环境中会返回报价、客户分层和方案推荐。`,
  hr: (msg) => `当前为 HR 演示模式。\n\n收到的问题：${msg.slice(0, 40)}\n\n当前返回的是本地模拟内容，真实环境中会返回假期余额、审批状态和政策说明。`,
  finance: (msg) => `当前为财务演示模式。\n\n收到的问题：${msg.slice(0, 40)}\n\n当前返回的是本地模拟内容，真实环境中会返回报表和预算分析数据。`,
  'supply-chain': (msg) => `当前为供应链演示模式。\n\n收到的问题：${msg.slice(0, 40)}\n\n当前返回的是本地模拟内容，真实环境中会返回库存、采购和补货建议。`,
  qc: (msg) => `当前为质控演示模式。\n\n收到的问题：${msg.slice(0, 40)}\n\n当前返回的是本地模拟内容，真实环境中会返回检验报告和质量趋势。`,
  weather: (msg) => `当前为天气演示模式。\n\n收到的问题：${msg.slice(0, 40)}\n\n当前返回的是本地模拟内容，真实环境中会返回实时天气和预报数据。`,
  search: (msg) => `当前为搜索演示模式。\n\n收到的问题：${msg.slice(0, 40)}\n\n当前返回的是本地模拟内容，真实环境中会返回联网搜索摘要。`,
  'data-analysis': (msg) => `当前为数据分析演示模式。\n\n收到的问题：${msg.slice(0, 40)}\n\n当前返回的是本地模拟内容，真实环境中会返回 SQL 和统计分析结果。`,
  code: (msg) => `当前为代码演示模式。\n\n收到的问题：${msg.slice(0, 40)}\n\n当前返回的是本地模拟内容，真实环境中会返回仓库检索和代码审查结果。`,
  mcp: () => '当前为 MCP 演示模式。\n\n当前返回的是本地模拟内容，真实环境中需要先启用 MCP 服务。',
  multi: () => '当前为多智能体演示模式。\n\n当前返回的是本地模拟内容，真实环境中会把复杂任务拆分给多个智能体协作处理。'
}

export const DEMO_KNOWLEDGE_BASES = [
  {
    id: 'kb-001',
    name: '企业通用知识库',
    description: '制度、流程、HR 指南和共享内部参考文档。',
    status: 'active',
    documentCount: 142,
    totalChunks: 8924
  },
  {
    id: 'kb-002',
    name: '研发知识库',
    description: '技术规范、API 文档、架构说明和工程开发指引。',
    status: 'active',
    documentCount: 89,
    totalChunks: 5211
  },
  {
    id: 'kb-003',
    name: '销售知识库',
    description: '产品手册、报价规则、竞品分析和案例库。',
    status: 'active',
    documentCount: 63,
    totalChunks: 3456
  }
]

export const MOCK_RAG_RESPONSES: Record<string, string> = {
  'kb-001': '当前为企业通用知识库演示模式。\n\n示例回答：员工请年假需至少提前 3 个工作日在 OA 系统中提交申请，超过 5 天需部门负责人审批。',
  'kb-002': '当前为研发知识库演示模式。\n\n示例回答：接口命名应遵循 RESTful 规范，统一使用 `/api/v{n}/` 前缀，查询参数使用 camelCase。',
  'kb-003': '当前为销售知识库演示模式。\n\n示例回答：A 级客户可申请更高折扣和更长账期，特殊价格仍需额外审批。'
}

export const MOCK_RAG_SOURCES = [
  { filename: '制度文件2024.pdf', content: '', score: 0.92 },
  { filename: '员工手册v3.docx', content: '', score: 0.87 },
  { filename: 'HR政策汇编.xlsx', content: '', score: 0.81 }
]
