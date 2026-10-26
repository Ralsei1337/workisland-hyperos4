# DSHA / DSH 集成：本地 HTTP 桥 + `dsh-work-island` 插件

> 目标：让跑在 DSHA（`dsh.android.client`）里的 DeepSeek，把「正在干什么」推到工作岛，
> 最终显示在 HyperOS 4 的超级岛上。
> **不修改 DSHA 本身**：只给工作岛加了一个本地 HTTP 口，再装一个用户插件。

---

## 一、为什么不能直接沿用 cccodroid 的驱动方式

工作岛原来的状态入口是 `am start-foreground-service`（网关 → 应用）。
在 DSHA 里这条路走不通：

```text
$ adb-shell am start-foreground-service ...
[POLICY_BLOCKED] 不认识的命令不执行：am
```

DSHA 的设备通道只放行白名单命令（`am force-stop`、`pm` 查询、`dumpsys` 的少数参数、`logcat -d`…），
`am start/broadcast` 都不在其中。写死在网关侧的 cccodroid driver 也就无法复用。

**结论：进程间通信必须绕开设备命令通道。** 而 DSHA 的 Ubuntu 是 proot 出来的容器，
与 Android 共享网络命名空间 —— `127.0.0.1` 上的端口就是最干净的通道。

---

## 二、方案

```text
 DSHA（Ubuntu, proot）                         Android
┌──────────────────────────┐          ┌────────────────────────────┐
│ DeepSeek agent           │          │ 工作岛 App                  │
│   └─ island 工具（插件）  │  HTTP    │   └─ IslandService          │
│        或 curl            │ ───────▶ │        ├─ HttpBridge :8799  │
│                          │ 127.0.0.1│        ├─ Notifier           │
└──────────────────────────┘          │        └─ 超级岛通知          │
                                      └────────────────────────────┘
```

两半：

| 半边 | 位置 | 作用 |
|---|---|---|
| `HttpBridge`（Java） | 工作岛 App 内 | 监听 `127.0.0.1:8799`，把请求翻译成 `IslandService` 的状态更新 |
| `dsh-work-island`（插件） | DSHA 用户插件 | 注册模型可调用的 `island` 工具，内部 fetch 上面的口 |

两边都不需要 root、不需要 Shizuku、不需要改 DSHA 的代码或设备策略。

---

## 三、HTTP 桥

端口：`8799`（可在 `Prefs.httpPort` 改；`island` 脚本用 `ISLAND_PORT` 覆盖）。

| 路径 | 参数 | 作用 |
|---|---|---|
| `GET /ping` | — | 探活，返回 `{"ok":true,"app":"workisland","port":8799}` |
| `GET /state` | `state`、`text`、`title`、`todoDone`、`todoTotal` | 更新工作态（岛上显示「读文件 2/5」这类短文案） |
| `GET /start` | `title`、`text`、`state`、`todoDone`、`todoTotal` | 用指定标题开始一轮 |
| `GET /done` | `title`、`text` | 完成态（8 秒后自动收起） |
| `GET /idle` | — | 回到待机，岛收起，通知栏留一条安静通知 |
| `GET /cancel` | — | 清掉通知并停服务 |

鉴权：如果 `Prefs.token` 非空，请求必须带 `?token=xxx`；默认 token 为空，只绑 `127.0.0.1`。

示例：

```bash
curl -s "127.0.0.1:8799/state?state=reading&text=Notifier.java&todoDone=2&todoTotal=5"
curl -s "127.0.0.1:8799/done?title=任务完成&text=用时 3 分"
curl -s "127.0.0.1:8799/idle"
```

>`island` 命令行（`/usr/local/bin/island`，本仓库 `tools/island`）是它的薄封装：
>
>```bash
>island state reading "Notifier.java" 2 5
>island done "任务完成" "用时 3 分"
>island idle
>```

**前提**：工作岛 App 至少被打开过一次，`IslandService` 起来后 HTTP 桥才会监听。
之后即使界面在后台，只要前台服务还在，端口就一直可用。

---

## 四、`dsh-work-island` 插件

### 结构

```text
source/dsha-plugin/dsh-work-island/
├── package.json          # name/version + dsh.bundle.patch
├── cordis.patch.yml      # - insert: [{ id: work-island, name: dsh-work-island }]
└── lib/index.js          # Cordis 插件：注册 island 工具，内部 fetch 127.0.0.1:8799
```

`package.json` 里最关键的一行：

```json
"dsh": { "bundle": { "patch": "./cordis.patch.yml" } }
```

DSHA 的插件管理器要求：合法 npm 名 + 合法 semver + `dsh.bundle.patch` 指向真实存在的补丁文件，
否则会报「普通 npm 包不是 dsh 插件」。`cordis.patch.yml` 负责把插件挂进 profile 的 bundle 列表。

### 自动跟随（0.2.1）

0.2.1 起插件自己监听 `session/event`，不需要模型主动调用工具：

| DSH 事件 | 岛上自动显示 |
|---|---|
| `turn/start` | `思考中` |
| `tool/call` → bash/exec | `执行中` |
| `tool/call` → read/grep/glob | `读文件` |
| `tool/call` → write/edit/patch | `写文件` |
| `tool/call` → web/fetch | `联网查` |
| `todo/write` | `执行中 2/5`（带清单进度） |
| `turn/end` | `任务完成`（8 秒后自动收起） |

`island` 工具仍然保留，用于想覆盖当前显示的场景。

### 工具签名

```text
island(
  action:  "state" | "start" | "done" | "idle" | "cancel" | "ping",
  title?:  string,
  text?:   string,
  state?:  "thinking" | "reading" | "writing" | "running" | "waiting" | "searching" | "error" | "done",
  todoDone?:  integer,
  todoTotal?: integer
)
```

模型侧看到的一句话说明：

> 把当前工作状态推到手机状态栏的「工作岛」……开始一个多步任务时调 action=state 或 start，
> 干完调 action=done，收工回 idle。文案要短，进度用 todoDone/todoTotal 传。

### 安装

```bash
# 1) 打包（在 source/dsha-plugin 下）
tar czf dsh-work-island-0.2.1.tar.gz dsh-work-island

# 2) 用 DSHA 自带的插件管理器导入（不需要改任何 DSHA 文件）
dsha-plugin import /root/dsh-plugins/dsh-work-island-0.2.1.tar.gz
#    输出里会带 previewId 和 confirmationSha256

# 3) 确认安装（预览 → 提交）
python3 /root/.dsh/plugin-manager.py install-preview <previewId> <confirmationSha256>

# 4) 重启一次 DSHA Web（插件在启动时加载）
dsha-plugin list        # 应能看到 dsh-work-island  enabled=true
```

安装后 `dsha-plugin list` 里会出现：

```json
{"name":"dsh-work-island","enabled":true,"origin":"user","version":"0.2.1","loadState":"queued"}
```

`loadState: queued` 表示已登记、等 Web 下次启动加载。

### 使用

模型会自己看到 `island` 工具，也可以在对话里直接要求：

> 用 island 工具把「正在读 Notifier.java，清单 2/5」推上去。

---

## 五、验证记录

| 项 | 结果 |
|---|---|
| 工作岛 HTTP 桥 `/ping` | `{"ok":true,"app":"workisland","port":8799}` |
| `/state?state=reading&todoDone=2&todoTotal=5` | 超级岛显示「读文件 2/5」（截图见 `docs/hyperos/island-progress.png` 同款布局） |
| 插件工具 `island` 五个 action | 本地 Node 直接调用全部返回 ok（`ping/state/start/done/idle`） |
| 插件安装 | `origin=user`、`enabled=true`、`loadState=queued` |
| DSHA 本身 | 未改动任何文件；只新增用户插件与一个外部脚本 |

---

## 六、限制

- **需要重启一次 Web**：DSH 插件在启动时加载，热更新不保证。重启只影响 Web 前端，会话与工作区数据不丢。
- 工作岛进程被杀 / 未启动时，`island` 工具会报「连不上工作岛」，提示先打开 App。
- 端口只绑 `127.0.0.1`；同机其它应用原则上也能访问，介意的话在 App 里设置 token。
- 插件只是「让 agent 能推状态」，不监听 agent 生命周期；若要全自动（每次工具调用自动更新），
  需要再监听 DSH 的会话事件，属于后续可选项。
