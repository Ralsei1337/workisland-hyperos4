// ── 工作岛：网关启动时拉起 driver，并每 30 秒看一次它还活着没有 ──────────────
// driver 跑在网关的进程树里（不是单轮 claude 里），订阅 /api/events，
// 把状态经 am start-foreground-service 送给「工作岛」前台服务。
// 完成 8 秒后的 IDLE 也由 driver 发 —— 工作岛自己会被 ColorOS 冻结，不能自己计时。
import fs from "node:fs";
import path from "node:path";
import { spawn } from "node:child_process";
import { CFG, log } from "./config.mjs";

const DRIVER = path.join(CFG.home, "workspace", "task-island", "driver.mjs");
const PIDFILE = path.join(CFG.home, ".work-island.pid");

let started = false;

function alive(pid) {
  try {
    const cmd = fs.readFileSync(`/proc/${pid}/cmdline`, "utf8");
    return cmd.includes("driver.mjs");
  } catch {
    return false;
  }
}

function spawnDriver() {
  let old = 0;
  try {
    old = parseInt(fs.readFileSync(PIDFILE, "utf8").trim(), 10);
  } catch {}
  if (old && alive(old)) return;               // 已在跑，不重复拉
  try {
    const env = { ...process.env };
    delete env.CCAPP_RUN;                      // 别被网关自己的孤儿清理误杀
    const child = spawn(process.execPath, [DRIVER], { detached: true, stdio: "ignore", env });
    child.unref();
    log(`[work-island] driver 已启动 pid=${child.pid}`);
  } catch (e) {
    log(`[work-island] driver 启动失败：${e.message}`);
  }
}

export function startWorkIsland() {
  if (started) return;
  started = true;
  spawnDriver();
  setInterval(spawnDriver, 30000).unref?.();   // 挂了 30 秒内自己回来
}
