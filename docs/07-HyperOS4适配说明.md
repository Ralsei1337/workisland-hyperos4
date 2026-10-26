# HyperOS 4（小米澎湃 OS 4 / Android 17）适配说明

> 适配对象：`com.dafeiyu.workisland` 1.5
> 验证设备：Xiaomi 25113PN0EC · HyperOS 4.0.2.0.XPCCNXM（OS4.0）· Android 17（SDK 37）
> 验证时间：2026-10-26
> 本文所有结论都来自真机实验，截图见 `docs/hyperos/`。

---

## 一、结论先说

| 问题 | 结论 |
|---|---|
| HyperOS 4 能不能接住 Android 16 的 Live Update（promoted ongoing）？ | **能**。状态栏的超级岛会直接显示通知的 `smallIcon` + `shortCriticalText` |
| 要不要申请小米「超级岛」权限才能显示？ | **不需要**。走 AOSP 通道即可上岛；小米官方 `miui.focus.param` 通道才需要 APP_ID + 签名鉴权 |
| 官方 `miui.focus.param`（大岛/小岛/息屏文案）在未申请时生效吗？ | **不生效**。实测系统忽略这些 extras，回落成普通 promoted 通知（详见第四节实验） |
| 岛上能显示什么？ | 小图标（`smallIcon`，工作岛放的是 bot 位图）+ 一格文字（`shortCriticalText`，没有就用 `title`） |
| 分段进度条（`Notification.ProgressStyle` 的 Segment）上岛吗？ | **不上**。摘要态只显示图标 + 文字；进度请塞进文字里（工作岛已经这么做：`执行中 2/5`） |
| 完成态会自动收起吗？ | 会，但**不能只靠 `setTimeoutAfter`**：前台服务通知的 timeout 不生效。1.5 在 `IslandService` 里加了 8 秒 `Handler` 定时器切到 idle 通知，实测 10 秒后岛已收起（`island-collapsed.png`） |

一句话：**HyperOS 4 的超级岛接的是 AOSP 的 promoted ongoing 通知，工作岛只要把「小图标 + 一句短文本」打磨好，就能直接上岛。**

---

## 二、HyperOS 4 的岛长什么样（实测）

| 状态 | 岛上显示 | 截图 |
|---|---|---|
| 干活（清单 2/5） | 🤖 `执行中 2/5` | `docs/hyperos/island-progress.png` |
| 思考 | 🤖 `思考中 0/5` | `docs/hyperos/island-thinking.png` |
| 完成 | 😊 `任务完成`（8 秒后自动收起） | `docs/hyperos/island-done.png` |
| 收起后 | 岛上什么都没有 | `docs/hyperos/island-collapsed.png` |

- 胶囊整体居中，左边一个图标位、右边一格文字位；文字长了胶囊会自己变宽（11 个汉字左右到顶）。
- 图标用的就是通知的 `smallIcon`。工作岛把程序化 bot 画成 160px 位图塞进 `smallIcon`，在岛上显示正常。
- `largeIcon`、`contentTitle`、`contentText`、`subText`、计时器这些只出现在下拉通知栏，不上岛。

---

## 三、代码改了什么

新增 3 个类、改 3 个文件（都在 `source/app/src/com/dafeiyu/workisland/`）：

### 1. `HyperOs.java`（新增）

探测 HyperOS 能力，全部走反射 / 系统接口，没有依赖小米 SDK：

| 方法 | 依据 | 用途 |
|---|---|---|
| `isXiaomi()` | `ro.mi.os.version.name` / `ro.miui.ui.version.name` | 判断小米机型，切换文案和保活入口 |
| `osName()` / `majorVersion()` | `ro.mi.os.version.name = OS4.0` | 界面显示「HyperOS 4.0」 |
| `hasIsland()` | `persist.sys.feature.island`（本机 = 1） | 系统是否声明支持超级岛 |
| `focusProtocol(c)` | `Settings.System.getInt("notification_focus_protocol")`（本机 = 3） | 焦点通知协议版本，3 = OS3 及以上的岛 |
| `canShowFocus(c)` | `content://miui.statusbar.notification.public` 的 `canShowFocus`（本机 = 已开） | 是否被授予焦点通知权限 |
| `canUseFocusParam(c)` | 上面三个条件 | 决定要不要附官方 `miui.focus.param` |

属性查询与权限查询都做了缓存（权限 30 秒 TTL），避免每次发通知都做 IPC。
`onResume()` 里调用 `HyperOs.invalidate()`，用户在设置里改了开关能立刻反映到界面。

### 2. `IslandPayload.java`（新增）

按《小米超级岛通知模板库》拼 `miui.focus.param`（`param_v2`）：

- `ticker` / `aodTitle`：状态栏与息屏文案
- `param_island.bigIslandArea.imageTextInfoLeft`：A 区图文组件（bot 图标 + 状态大字 + 进度小字）
- `param_island.bigIslandArea.textInfo`：B 区文本组件（阶段 / 动作 / 清单）
- `param_island.smallIslandArea.picInfo`：小岛图标
- `baseInfo`：焦点通知正文
- `miui.focus.pics` + `miui.focus.actions`：图标与「打开」按钮

**这段代码默认只在 `canUseFocusParam()` 为真时才附加，构造失败一律吞掉，绝不影响 AOSP 通道。** 现在的机器上它会被系统忽略（没有 APP_ID 鉴权），但保留着——将来应用申请到小米超级岛权限，把 `com.xiaomi.xms.APP_ID` 写进 Manifest 就能用上，不用再改代码。

`IslandPayload.TEST_ICON` 是调试开关：置 `true` 时大岛/小岛图标会变成纯色方块，用来判断系统到底有没有吃 `param`（本次实验就是靠它证伪的）。

### 3. `Notifier.java`（改）

- **岛上短文本按平台区分**：
  - HyperOS：`状态短名 + 进度`，例如 `执行中 2/5`、`思考中`、`任务完成`
  - ColorOS（原逻辑不变）：`原标题 + 进度`
- 满足条件时把 `IslandPayload` 的 extras 附到通知上。
- 日志里带上 `hyperos=HyperOS 4.0 · 岛支持 · 协议3 · 焦点通知已开`，排查问题时一眼能看出来。

### 4. `IslandService.java`（改）

**收工 8 秒自动收起**：前台服务通知的 `setTimeoutAfter()` 在系统里不生效，ColorOS 版靠 driver 发 IDLE；
HyperOS 上如果 driver 漏发，岛会一直停在「任务完成」。现在服务自己在 `finishRun()` 后挂一个 8 秒
`Handler` 定时器，到点切到 idle 通知并取消完成态通知；`startWorking()` / `ACTION_CANCEL` / `ACTION_IDLE`
都会先清掉这个定时器。

`startForeground()` 加了降级：Android 15+ / HyperOS 对「后台启动前台服务」有硬限制，
driver 从网关用 `am start-foreground-service` 拉起时可能直接抛异常。
现在被拒时退回 `NotificationManager.notify()`——promoted ongoing 照样能进岛，只是进程不再常驻。

### 5. `MainActivity.java`（改）

- 副标题按系统显示「显示在超级岛 / 灵动岛」。
- 状态行多一行 HyperOS 诊断：`HyperOS 4.0　超级岛：支持　焦点通知：已开`。
- 「后台保活设置」在小米机型上改成四选一菜单：自启动管理、电池与省电策略、应用通知设置、电池优化。
  （配色/布局没动，仍是原来那套纸张风格。）
- 新增两个测试按钮：
  - `下一种状态（岛上试文案）`：循环 thinking / reading / writing / waiting / running，每个都带清单进度，用来看岛上文案。
  - `服务方式开始（等同 driver）`：真走 `start-foreground-service → IslandService → 前台通知` 这条链路，验证 FGS 通知上岛。

### 6. `AndroidManifest.xml`（改）

- `targetSdkVersion` 36 → **37**（Android 17），`minSdkVersion` 保持 36。
- `versionCode` 5 → 7，`versionName` 1.4 → **1.5**。

### 7. `build-hyperos.sh`（新增）

没有 Gradle 的构建脚本：`aapt2 + javac + d8 + apksigner`，一条命令出签名 APK。
本机是 aarch64，官方 build-tools 的 `aapt2` 是 x86_64，所以脚本用 Termux 的 aarch64 `aapt2`（`/opt/termux-tools`），`d8`/`apksigner` 直接用 JDK 跑。

```bash
cd source/app
./build-hyperos.sh          # → build/WorkIsland-hyperos.apk
```

---

## 四、官方 `miui.focus.param` 为什么没生效（实验记录）

怀疑点：HyperOS 4 上 `persist.sys.feature.island=1`、`notification_focus_protocol=3`、
`canShowFocus=true`，看起来「有焦点通知权限」，那么官方参数是不是能用？

写了探针应用（`probe`，临时工程）逐项对照：

| 实验 | 通知内容 | 岛上结果 |
|---|---|---|
| A | `title=标题AAAA…`，`shortCriticalText=短B` | 显示 **短B**（确认岛上文字取 `shortCriticalText`） |
| B | 不给 `shortCriticalText`，`title=标题EEEE…` | 显示 **标题EEEE…**（回退到 title） |
| C | `shortCriticalText=分段HH` + `ProgressStyle` 5 段进度 2 | 只显示「分段HH」，**分段进度不显示** |
| E | `shortCriticalText=focusJJ` + 完整 `miui.focus.param`（大岛图文 + 小岛图标 + 焦点正文） | 仍然只显示「focusJJ」，**param 被忽略** |
| F | E 再加 `ticker` / `aodTitle` | 同上，**ticker/aod 也没用上** |
| — | 工作岛正式版把 param 的图标改成**纯红方块**再上岛 | 岛上还是 bot 图标，**再次证明 param 未生效** |

结论与官方文档一致：**超级岛的官方参数要经过小米侧「开通服务 + APP_ID + 签名鉴权」**，
未鉴权的应用即使带着 `miui.focus.param`，系统也按普通 promoted 通知处理。
因此适配的正确姿势是把 AOSP 通道做好，`param` 当作「有权限时的增强」预埋。

---

## 五、HyperOS 4 上的安装与保活

### 1. 安装

普通 APK 安装即可；HyperOS 会提示「文件管理正尝试安装应用」→ 继续 → 安装。

### 2. 三处必须开（HyperOS 比 ColorOS 多一道「自启动」）

| 项 | 位置 | 说明 |
|---|---|---|
| 通知 | 设置 → 通知与状态栏 → 工作岛 → 允许通知 | 必须 |
| 实时活动 / 超级岛 | 同上页面 → 「焦点通知 / 实时活动」开关 | 工作岛本机默认已开；关掉就只剩下拉通知栏 |
| 自启动 | 设置 → 应用设置 → 授权管理 → 自启动 → 工作岛 | 不开发送 `BOOT_COMPLETED` 后无法恢复 |
| 省电策略 | 应用信息 → 省电策略 → 无限制 | 否则后台容易被冻结 |

应用首页的「后台保活设置」按钮会把这几项列成菜单，点进去直达。

### 3. 关于 driver 的启动方式

- `am start-foreground-service`（原方案）：在 HyperOS 上可能被「后台启动 FGS」限制拒绝。
  工作岛已加降级——被拒就用普通通知，岛照样亮，只是进程不常驻。
- 更稳的做法：driver 改用 `am broadcast -a com.dafeiyu.workisland.UPDATE`（`IslandReceiver` 是 exported 的），
  通知同样走 promoted ongoing 上岛。

---

## 五点五、展开态 / 锁屏的重复与错位（1.5.1 修复）

真机截图暴露出两个同源问题（用户提供：锁屏卡片 + 点击岛后的展开态）：

| 现象 | 原因 | 修复 |
|---|---|---|
| 卡片里第三行和上面一行一模一样（`在想下一步…` 出现两次） | `setContentText(text)` 与 `setSubText(text + " · 清单 x/y")` 同时存在，展开态两行都画了出来 | HyperOS 分支不再设置 `subText`，清单进度并进 `contentText` |
| 标题里 `0/5` 与正文「清单 0/5」重复 | 标题被拼成了 `baseTitle · 进度` | HyperOS 上标题用 `baseTitle`，进度只留在正文 |
| 展开态左上角出现一个黑色圆点 tracker 图标、底部进度条与文字挤在一起 | `Notification.ProgressStyle`（AOSP 2025 promoted 模板）HyperOS 只实现了一部分 | HyperOS 不再使用 `ProgressStyle`，改用经典 `setProgress()`，进度条画在通知栏/锁屏，展开态回到系统默认布局 |
| 锁屏卡片上的 `miui.focus.param` 相关渲染 | 未鉴权时系统会解析这些 extras，属于无效负担 | `miui.focus.param` 改为默认关闭（设置页开关），只有拿到小米 APP_ID 才建议打开 |

另外应用内所有面向用户的 `Claude` 文案改成 `DeepSeek`（应用名、副标题、通知渠道描述、FGS 描述）。

## 六、已验证 / 未验证

**已验证（真机）**

- 工作态 / 带清单进度 / 思考态 / 完成态 四种文案在超级岛上显示正确
- 完成 8 秒后岛自动收起（移除 driver 依赖后实测）
- 通知栏：大图标（bot）、标题、正文、计时器显示正常
- FGS 通道（`start-foreground-service`）与广播通道都能上岛
- `HyperOs` 探测值：`OS4.0`、`island=1`、`protocol=3`、`canShowFocus=true`

**未验证**

- 锁屏 / 息屏（AOD）上的岛样式
- 点击岛展开后的样式（需要在主屏上点，本次没有动用户主屏）
- HyperOS 3（Android 16）——探测逻辑兼容，但没有真机
- 重启后自启动（`BOOT_COMPLETED` 已声明）
- 长时间 Doze 下的存活
- 官方 `miui.focus.param` 拿到 APP_ID 后的真实效果

---

## 七、给上游的建议

1. 把 `HyperOs` / `IslandPayload` 两个类当通用「平台适配层」，ColorOS / OriginOS / 其他厂商可以照这个模式各加一个。
2. 岛文案的长度上限按「4 个汉字 + 进度」设计比较稳；`shortCriticalText` 一旦过长，胶囊会挤掉状态栏其他图标。
3. 不要在摘要态指望进度条；进度要写进文字里。
4. `IslandService` 的 FGS 降级建议保留——Android 15+ 各家的后台启动限制只会更紧。
