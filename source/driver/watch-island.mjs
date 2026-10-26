// 把 cccodroid 网关里的 Claude 运行状态，投到「工作岛」的实时通知上。
// 跑在 cccodroid 的 uid 里，能读到网关令牌；岛屿只负责画，数据来自真实事件流。
import http from 'node:http';
import fs from 'node:fs';
import { execFile } from 'node:child_process';

const HOME = '/data/user/0/cc.codroid/files/home';
const TOKEN = fs.readFileSync(HOME + '/.gateway-token', 'utf8').trim();
const PORT = 8791;
const PKG = 'com.dafeiyu.workisland';
const SVC = PKG + '/.IslandService';
const A = {
  start: PKG + '.START',
  update: PKG + '.UPDATE',
  done: PKG + '.DONE',
  cancel: PKG + '.CANCEL',
};

const TOOL_CN = {
  Bash: '执行命令', Read: '读文件', Write: '写文件', Edit: '改文件',
  Glob: '找文件', Grep: '搜代码', WebSearch: '查资料', WebFetch: '读网页',
  Task: '子任务', TodoWrite: '整理清单', NotebookEdit: '改笔记',
  Agent: '子任务', Skill: '调用技能', KillShell: '收尾命令',
};

let active = null;
let cancelTimer = null;

const log = (...a) => console.log(new Date().toISOString(), ...a);

function send(action, title, text, since) {
  const args = ['start-foreground-service', '--user', '0', '-n', SVC, '-a', action];
  if (title) args.push('--es', 'title', title);
  if (text) args.push('--es', 'text', text);
  if (since) args.push('--el', 'since', String(since));
  execFile('am', args, { timeout: 8000 }, (e, out, err) => {
    if (e) log('am failed', e.message, String(err || '').slice(0, 120));
  });
}

function start(run, text) {
  if (active) return update(text);
  if (cancelTimer) { clearTimeout(cancelTimer); cancelTimer = null; }
  active = { startedAt: Number(run && run.startedAt) || Date.now(), last: '' };
  log('start', active.startedAt);
  update(text || '正在处理…', '正在干活');
}

function update(text, title) {
  if (!active) return;
  const t = title || '正在干活';
  const key = t + '|' + text;
  if (key === active.last) return;
  active.last = key;
  send(A.update, t, text, active.startedAt);
}

function finish(reason) {
  if (!active) return;
  const ms = Math.max(0, Date.now() - active.startedAt);
  active = null;
  const sec = Math.round(ms / 1000);
  const human = sec < 60 ? `${sec} 秒` : `${Math.floor(sec / 60)} 分 ${sec % 60} 秒`;
  log('done', human, reason || '');
  send(A.done, '任务完成', '用时 ' + human + (reason ? ' · ' + reason : ''), Date.now());
  if (cancelTimer) clearTimeout(cancelTimer);
  cancelTimer = setTimeout(() => { cancelTimer = null; send(A.cancel); }, 8000);
}

function handleEvent(ev) {
  if (!ev || !ev.type) return;
  switch (ev.type) {
    case 'hello':
      if (ev.run) start(ev.run, '正在处理…');
      break;
    case 'run_start':
      start(ev, '正在处理…');
      break;
    case 'run_busy':
      update('继续处理…');
      break;
    case 'phase':
      if (ev.phase === 'wait') update('等模型回复…');
      else if (ev.phase === 'think') update('正在想…');
      else if (ev.phase === 'text') update('正在写回复…');
      else if (ev.phase === 'tool') update('用 ' + (TOOL_CN[ev.name] || ev.name || '工具') + '…');
      else if (ev.phase === 'retry') update('重试中…');
      break;
    case 'tool_use':
      update('用 ' + (TOOL_CN[ev.name] || ev.name || '工具') + '…');
      break;
    case 'perm_request':
      update('需要你确认：' + (ev.tool || ev.name || '操作'), '等你确认');
      break;
    case 'perm_resolved':
      update('继续…', '正在干活');
      break;
    case 'compacting':
      update('整理上下文…');
      break;
    case 'sub_progress':
      update('子任务：' + (ev.desc || '进行中') + '…');
      break;
    case 'run_idle':
      if (ev.bg > 0) update('后台任务还在跑…');
      break;
    case 'error':
      update('出错了：' + String(ev.message || '').slice(0, 60));
      break;
    case 'run_end':
      finish(ev.aborted ? '已中止' : '');
      break;
  }
}

function connect() {
  const req = http.get({
    host: '127.0.0.1', port: PORT, path: '/api/events',
    headers: { 'X-CC-Token': TOKEN, Accept: 'text/event-stream' },
  }, (res) => {
    if (res.statusCode !== 200) {
      log('sse status', res.statusCode);
      res.resume();
      return setTimeout(connect, 3000);
    }
    log('sse connected');
    let buf = '';
    res.setEncoding('utf8');
    res.on('data', (chunk) => {
      buf += chunk;
      let i;
      while ((i = buf.indexOf('\n\n')) >= 0) {
        const block = buf.slice(0, i);
        buf = buf.slice(i + 2);
        for (const line of block.split('\n')) {
          if (!line.startsWith('data:')) continue;
          const raw = line.slice(5).trim();
          if (!raw) continue;
          try { handleEvent(JSON.parse(raw)); } catch (e) { log('bad event', raw.slice(0, 80)); }
        }
      }
      if (buf.length > 2_000_000) buf = '';
    });
    res.on('end', () => { log('sse ended'); setTimeout(connect, 2000); });
    res.on('error', () => setTimeout(connect, 2000));
  });
  req.on('error', (e) => { log('sse error', e.message); setTimeout(connect, 3000); });
  req.setTimeout(0);
}

function getState() {
  return new Promise((resolve) => {
    const req = http.get({
      host: '127.0.0.1', port: PORT, path: '/api/state',
      headers: { 'X-CC-Token': TOKEN },
    }, (res) => {
      let s = '';
      res.setEncoding('utf8');
      res.on('data', (d) => { s += d; });
      res.on('end', () => { try { resolve(JSON.parse(s)); } catch { resolve(null); } });
    });
    req.on('error', () => resolve(null));
    req.setTimeout(4000, () => { req.destroy(); resolve(null); });
  });
}

async function poll() {
  const st = await getState();
  if (!st) return;
  if (st.run && !active) start(st.run, '正在处理…');
  else if (!st.run && active) finish('');
}

log('work island watcher starting, port', PORT);
connect();
setInterval(poll, 5000);
poll();
