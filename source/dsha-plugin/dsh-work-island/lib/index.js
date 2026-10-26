/**
 * dsh-work-island —— 把 DeepSeek Harness 的运行状态推到手机上的「工作岛」。
 *
 * 工作岛 App 在本机 127.0.0.1:8799 开了一个 HTTP 桥；DSHA 的 Ubuntu 与 Android
 * 共享网络命名空间，所以插件只要 fetch 这个地址就行 —— 不需要 adb、不需要 root、
 * 不需要改 DSHA，也不受设备命令白名单限制。
 *
 * 注册了一个模型可调用的工具 `island`：
 *   island({ action: "state", state: "reading", text: "Notifier.java", todoDone: 2, todoTotal: 5 })
 *   island({ action: "done", title: "任务完成", text: "用时 3 分" })
 *   island({ action: "idle" }) / island({ action: "cancel" }) / island({ action: "ping" })
 */

const name = 'tool-work-island';
const inject = ['tools'];

const DEFAULT_HOST = '127.0.0.1';
const DEFAULT_PORT = 8799;

function baseUrl(config) {
  const host = (config && config.host) || DEFAULT_HOST;
  const port = (config && config.port) || DEFAULT_PORT;
  return `http://${host}:${port}`;
}

async function callIsland(config, path, params) {
  const query = new URLSearchParams();
  for (const [key, value] of Object.entries(params || {})) {
    if (value === undefined || value === null || value === '') continue;
    query.set(key, String(value));
  }
  const url = `${baseUrl(config)}${path}${query.size > 0 ? `?${query}` : ''}`;
  let response;
  try {
    response = await fetch(url, { signal: AbortSignal.timeout(2500) });
  } catch (error) {
    throw new Error(
      `连不上工作岛（${baseUrl(config)}）：先在手机上打开一次工作岛 App，让它把前台服务和 HTTP 桥跑起来。原始错误：${error.message}`,
    );
  }
  const text = await response.text();
  if (!response.ok) throw new Error(`工作岛返回 HTTP ${response.status}：${text.slice(0, 160)}`);
  let parsed;
  try {
    parsed = JSON.parse(text);
  } catch {
    throw new Error(`工作岛返回的不是 JSON：${text.slice(0, 160)}`);
  }
  if (parsed.ok === false) throw new Error(`工作岛拒绝请求：${parsed.error || text.slice(0, 160)}`);
  return parsed;
}

const parameters = {
  type: 'object',
  properties: {
    action: {
      type: 'string',
      enum: ['state', 'start', 'done', 'idle', 'cancel', 'ping'],
      description:
        'state=更新状态（读文件/思考/执行命令等）；start=用给定 title 开始一轮；done=任务完成（8 秒后自动收起）；idle=回到待机；cancel=清掉岛；ping=探活。',
    },
    title: { type: 'string', description: '岛上标题（start/done 用）；留空则用默认文案。' },
    text: { type: 'string', description: '副标题/正文，比如正在处理哪个文件。' },
    state: {
      type: 'string',
      enum: ['thinking', 'reading', 'writing', 'running', 'waiting', 'searching', 'error', 'done'],
      description:
        '状态名，决定岛上的短文案：reading=读文件、thinking=思考中、writing=写文件、running=执行中、waiting=等确认、error=出错。',
    },
    todoDone: { type: 'integer', description: '清单已完成条数（配合 todoTotal）。' },
    todoTotal: { type: 'integer', description: '清单总条数。' },
  },
  required: ['action'],
  additionalProperties: false,
};

const outputSchema = {
  type: 'object',
  properties: {
    ok: { type: 'boolean' },
    message: { type: 'string' },
  },
  required: ['ok', 'message'],
  additionalProperties: false,
};

function describeResult(args, value) {
  return [{ type: 'text', text: value.message }];
}

export function apply(ctx, config) {
  ctx.tools.register({
    name: 'island',
    description:
      '把当前工作状态推到手机状态栏的「工作岛」：HyperOS 4 显示在超级岛，ColorOS 显示在流体云。' +
      '开始一个多步任务时调 action=state 或 start，干完调 action=done，收工回 idle。' +
      '文案要短（中文 4~8 字），进度用 todoDone/todoTotal 传。',
    parameters,
    output: {
      schema: outputSchema,
      render: (args, value) => describeResult(args, value),
    },
    async execute(args) {
      const action = args.action;
      if (action === 'ping') {
        const info = await callIsland(config, '/ping', {});
        return { ok: true, message: `工作岛在线：${JSON.stringify(info)}` };
      }
      if (action === 'state') {
        await callIsland(config, '/state', {
          state: args.state || 'running',
          text: args.text,
          title: args.title,
          todoDone: args.todoDone,
          todoTotal: args.todoTotal,
        });
        return { ok: true, message: `已推送状态：${args.state || 'running'}${args.text ? ` · ${args.text}` : ''}` };
      }
      if (action === 'start') {
        await callIsland(config, '/start', {
          title: args.title || '正在干活',
          text: args.text,
          state: args.state || 'running',
          todoDone: args.todoDone,
          todoTotal: args.todoTotal,
        });
        return { ok: true, message: `已开始：${args.title || '正在干活'}` };
      }
      if (action === 'done') {
        await callIsland(config, '/done', {
          title: args.title || '任务完成',
          text: args.text,
        });
        return { ok: true, message: `已标记完成：${args.title || '任务完成'}` };
      }
      if (action === 'idle') {
        await callIsland(config, '/idle', {});
        return { ok: true, message: '工作岛已回到待机（岛收起）' };
      }
      if (action === 'cancel') {
        await callIsland(config, '/cancel', {});
        return { ok: true, message: '已清除工作岛通知' };
      }
      throw new Error(`不支持的 action：${action}`);
    },
    presentCall(args) {
      return {
        card: 'generic',
        title: `工作岛 · ${args.action}`,
        kind: 'other',
        rawInput: args,
      };
    },
  });
}

export { name, inject };
