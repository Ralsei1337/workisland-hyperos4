/**
 * dsh-work-island —— DeepSeek Harness ⇄ 工作岛（HyperOS 超级岛 / ColorOS 流体云）
 *
 * 两种工作方式：
 *   1) 自动跟随：监听 session/event，把「回合开始 / 工具调用 / 清单进度 / 回合结束」
 *      翻译成岛上的状态，全程不需要模型主动做什么。
 *   2) 手动控制：注册 island 工具，模型想精确控制时调用。
 *
 * 只走本机 HTTP 桥（工作岛 App 监听 127.0.0.1:8799）。DSHA 的 Ubuntu 与 Android
 * 共享网络命名空间，所以不需要 adb / root / Shizuku。
 *
 * 三条纪律（照抄 dsh-status-overlay 的教训）：
 *   - 不写模块级 inject：缺服务会让整棵 plugin tree 加载失败。
 *   - 事件回调里一个异常都不能冒出去。
 *   - 发「当前完整状态」而不是增量：丢包下次自动对上。
 */

export const name = 'tool-work-island'

const BRIDGE = 'http://127.0.0.1:8799'
const MIN_INTERVAL_MS = 400
const TIMEOUT_MS = 1500

/** 工具名 → 岛上状态，越具体的放前面。 */
const TOOL_STATES = [
  [/todo|plan/i, 'thinking'],
  [/web|fetch|http|browse|url/i, 'searching'],
  [/write|edit|patch|apply|create/i, 'writing'],
  [/read|cat|view|glob|grep|search|find|list/i, 'reading'],
  [/ask|question|confirm|permission/i, 'waiting'],
  [/bash|shell|exec|terminal|command/i, 'running'],
]

const ARG_KEYS = ['command', 'cmd', 'script', 'path', 'file_path', 'filePath',
  'pattern', 'query', 'url', 'description', 'prompt']

const buckets = new Map()

function sessionKey(session) {
  try {
    const raw = session?.id ?? session?.sessionId ?? session?.key ?? ''
    const s = String(raw)
    return s ? s.slice(-8) : '-'
  } catch {
    return '-'
  }
}

function bucket(key) {
  let b = buckets.get(key)
  if (!b) {
    b = { last: '', lastAt: 0, todoDone: 0, todoTotal: 0, turnStartedAt: 0, timer: undefined }
    buckets.set(key, b)
  }
  return b
}

function stateFor(toolName) {
  const n = String(toolName || '')
  for (const [re, state] of TOOL_STATES) if (re.test(n)) return state
  return 'running'
}

function titleFor(state) {
  switch (state) {
    case 'reading': return '读文件'
    case 'writing': return '写文件'
    case 'searching': return '联网查'
    case 'waiting': return '等你确认'
    case 'thinking': return '思考中'
    default: return '执行中'
  }
}

function detailOf(argsJson) {
  try {
    if (!argsJson) return ''
    const o = typeof argsJson === 'string' ? JSON.parse(argsJson) : argsJson
    if (!o || typeof o !== 'object') return ''
    for (const key of ARG_KEYS) {
      const v = o[key]
      if (typeof v === 'string' && v.trim()) {
        const one = v.replace(/\s+/g, ' ').trim()
        return one.length > 26 ? one.slice(0, 25) + '…' : one
      }
    }
  } catch { /* 半截 JSON 就放弃 */ }
  return ''
}

async function push(path, params) {
  try {
    const qs = new URLSearchParams()
    for (const [k, v] of Object.entries(params || {})) {
      if (v === undefined || v === null || v === '') continue
      qs.set(k, String(v))
    }
    const url = `${BRIDGE}${path}${qs.size > 0 ? `?${qs}` : ''}`
    await fetch(url, { signal: AbortSignal.timeout(TIMEOUT_MS) })
  } catch { /* 工作岛没开就静默 */ }
}

function sendState(b, state, text) {
  const signature = `${state}|${text}|${b.todoDone}/${b.todoTotal}`
  const now = Date.now()
  const fire = () => {
    b.last = signature
    b.lastAt = Date.now()
    void push('/state', { state, text, todoDone: b.todoDone, todoTotal: b.todoTotal })
  }
  if (signature === b.last && now - b.lastAt < 6000) return
  if (now - b.lastAt < MIN_INTERVAL_MS) {
    if (b.timer) clearTimeout(b.timer)
    b.timer = setTimeout(() => { b.timer = undefined; fire() }, MIN_INTERVAL_MS)
    return
  }
  if (b.timer) { clearTimeout(b.timer); b.timer = undefined }
  fire()
}

function onEvent(session, event) {
  const type = event?.type
  const data = event?.data
  const key = sessionKey(session)
  const b = bucket(key)

  if (type === 'turn/start') {
    b.turnStartedAt = Date.now()
    sendState(b, 'thinking', '收到任务')
    return
  }

  if (type === 'todo/write') {
    const todos = Array.isArray(data?.todos) ? data.todos : []
    if (todos.length > 0) {
      b.todoTotal = todos.length
      b.todoDone = todos.filter((t) => t?.status === 'completed').length
      const doing = todos.find((t) => t?.status === 'in_progress')
      sendState(b, 'running', doing?.content ? String(doing.content).slice(0, 24) : '整理清单')
    }
    return
  }

  if (type === 'tool/call') {
    const toolName = String(data?.name || '')
    const state = stateFor(toolName)
    const detail = detailOf(data?.arguments)
    sendState(b, state, detail || titleFor(state))
    return
  }

  if (type === 'turn/end') {
    if (b.timer) { clearTimeout(b.timer); b.timer = undefined }
    const ms = b.turnStartedAt > 0 ? Date.now() - b.turnStartedAt : 0
    const human = ms <= 0 ? '' : ms < 60000 ? `${Math.round(ms / 1000)} 秒` : `${(ms / 60000).toFixed(1)} 分`
    void push('/done', { title: '任务完成', text: human ? `用时 ${human}` : '' })
    buckets.delete(key)
  }
}

export function apply(ctx, config) {
  // ---- 手动工具（tools 缺失也不影响自动跟随） ----
  try {
    if (ctx?.tools?.register) {
      ctx.tools.register({
        name: 'island',
        description:
          '把当前工作状态推到手机状态栏的「工作岛」：HyperOS 4 显示在超级岛，ColorOS 显示在流体云。' +
          '插件已按回合/工具自动跟随，一般不用手动调用；只有想覆盖当前显示时才用。',
        parameters: {
          type: 'object',
          properties: {
            action: {
              type: 'string',
              enum: ['state', 'start', 'done', 'idle', 'cancel', 'ping'],
              description: 'state=更新状态；start=开始一轮；done=完成；idle=待机；cancel=清岛；ping=探活。',
            },
            title: { type: 'string', description: '岛上标题（start/done 用）。' },
            text: { type: 'string', description: '副标题/正文。' },
            state: {
              type: 'string',
              enum: ['thinking', 'reading', 'writing', 'running', 'waiting', 'searching', 'error', 'done'],
              description: '状态名。',
            },
            todoDone: { type: 'integer', description: '清单已完成条数。' },
            todoTotal: { type: 'integer', description: '清单总条数。' },
          },
          required: ['action'],
          additionalProperties: false,
        },
        output: {
          schema: {
            type: 'object',
            properties: { ok: { type: 'boolean' }, message: { type: 'string' } },
            required: ['ok', 'message'],
            additionalProperties: false,
          },
          render: (_args, value) => [{ type: 'text', text: value.message }],
        },
        async execute(args) {
          const action = args.action
          if (action === 'ping') {
            await push('/ping', {})
            return { ok: true, message: '工作岛在线' }
          }
          if (action === 'state' || action === 'start') {
            await push(action === 'start' ? '/start' : '/state', {
              state: args.state || 'running',
              title: args.title,
              text: args.text,
              todoDone: args.todoDone,
              todoTotal: args.todoTotal,
            })
            return { ok: true, message: `已推送：${args.state || 'running'}${args.text ? ` · ${args.text}` : ''}` }
          }
          if (action === 'done') {
            await push('/done', { title: args.title || '任务完成', text: args.text })
            return { ok: true, message: '已标记完成' }
          }
          if (action === 'idle') {
            await push('/idle', {})
            return { ok: true, message: '已回待机' }
          }
          if (action === 'cancel') {
            await push('/cancel', {})
            return { ok: true, message: '已清除' }
          }
          throw new Error(`不支持的 action：${action}`)
        },
        presentCall: (args) => ({
          card: 'generic',
          title: `工作岛 · ${args.action}`,
          kind: 'other',
          rawInput: args,
        }),
      })
    }
  } catch { /* 工具注册失败不影响自动跟随 */ }

  // ---- 自动跟随 ----
  try {
    if (config?.auto === false) return
    ctx.on('session/event', (session, event) => {
      try {
        onEvent(session, event)
      } catch { /* 状态显示不能中断会话 */ }
    })
    ctx.on('dispose', () => {
      for (const b of buckets.values()) if (b.timer) clearTimeout(b.timer)
      buckets.clear()
    })
  } catch { /* 订阅失败就只用手动工具 */ }
}
