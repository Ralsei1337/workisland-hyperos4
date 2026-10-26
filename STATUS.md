# 测试状态

> **当前状态：测试阶段（Pre-release / Alpha）**
>
> **目前在两种设备组合上测试成功：**
> **OnePlus PJA110 · ColorOS 16.0.3 · Android 16（SDK 36）· cccodroid 0.4.2；**
> **Xiaomi 25113PN0EC · HyperOS 4.0.2（OS4.0）· Android 17（SDK 37）· 应用 1.5。**

项目仍在测试阶段：接口、通知文案、网关接入方式和文件布局都可能继续变动，**不建议用于生产或关键任务**。

---

## 已验证

- ColorOS 16 流体云能接住 Android 16 Live Update（`PROMOTED_ONGOING` 实测出现）
- 工作态「正在干活」、完成态「任务完成」、8 秒后收起
- 并行轮次结束不会误报完成
- driver 被杀后由守护循环/网关看门狗拉起
- cccodroid 与工作岛都退到后台 8 秒，driver、网关、前台服务仍存活
- 构建、签名、安装、通知授权在设备上通过
- 四态 bot（Wakes/Works/Asks/Done）、14 look、7 个标识在真机上目视通过
- 21 个模型的轮廓/配色/眼睛/装饰全部渲染通过（对照表 `screenshots/10-bot-states.png`）
- 通知大/小图标与岛上胶囊显示同一只 bot；`!` / `?` / `≡` / `×` / `z` 实测可见

## HyperOS 4 已验证（1.5）

- HyperOS 4 的超级岛接住 AOSP promoted ongoing：岛上显示 `smallIcon`（bot 位图）+ `shortCriticalText`
- 岛上文案：`执行中`、`执行中 2/5`、`思考中 0/5`、`任务完成` 四种实测正确
- 完成 8 秒后岛自动收起
- FGS 通道（`am start-foreground-service`）与广播通道都能上岛
- 官方 `miui.focus.param`（大岛/小岛/息屏文案）在未申请 APP_ID 时被系统忽略（探针实验证伪，详见 docs/07）
- `HyperOs` 探测值：`OS4.0` / `island=1` / `protocol=3` / `canShowFocus=true`
- 通知栏大图标、标题、正文、计时器显示正常

## 未验证

- 其他 ColorOS 版本（如 15、17）与其他机型
- 其他厂商的“灵动岛 / 实时活动”实现（OriginOS、One UI、MagicOS 等）
- HyperOS 3（Android 16）与 HyperOS 4 的其他小版本
- HyperOS 上的锁屏 / 息屏岛样式、点击岛的展开态
- 官方超级岛权限拿到 APP_ID 后的 `miui.focus.param` 实际效果
- 开发端网关（8790）与 cccodroid（8791）的差异场景
- 重启开机自启（`BOOT_COMPLETED` 已声明，未做整机重启验证）
- 长时间 Doze（30 分钟以上）
- force-stop 后的自动恢复
- 多轮并行时的文案归属（收工判定已按 `runs` 处理，文案可能跟随最后事件）

## 已知限制

- cccodroid 被强行停止后，网关与 driver 都会消失，岛上会停在最后状态；重开 cccodroid 恢复。
- ColorOS 会在后台冻结应用（`do_freezer_trap`，前台服务也冻），所以定时器放在网关 driver，而不是应用里。
- cccodroid 升级可能覆盖 `files/app` 下的网关文件，需要重新接入。
- 工作岛的服务是 exported 的，同机应用可以伪造显示状态（仅影响显示）；生产化需加签名级权限。
- 胶囊外观由系统绘制，未使用 OPPO UPK 自定义卡片。
- 通知图标是静态帧：四态和标识准确，但呼吸/眨眼/起跳只在应用内预览里动。
- 21 个模型图标是 LobeHub 的品牌标识，公开发布前请自行确认商标使用。

## 反馈

如果你在别的 ColorOS 版本或机型上试了，欢迎带上：

- 机型、ColorOS 版本、Android 版本
- `dumpsys notification --noredact | grep -A1 "pkg=com.dafeiyu.workisland"` 的 flags 行
- 现象截图

这些信息足够判断是“没推广”“被冻结”还是“收工判定”的问题。
