# dsh-work-island

把 DeepSeek Harness 的运行状态推到手机状态栏的「工作岛」——HyperOS 4 显示在**小米超级岛**，
ColorOS 显示在**流体云**。

## 它做什么

注册一个模型可调用的 `island` 工具：

```text
island(action="state", state="reading", text="Notifier.java", todoDone=2, todoTotal=5)
island(action="done",  title="任务完成", text="用时 3 分")
island(action="idle")
```

内部只是 `fetch` 本机 `127.0.0.1:8799` 上的工作岛 HTTP 桥 —— 不需要 adb、root、Shizuku，
也不需要修改 DSHA / DSH 的任何文件。

## 前置条件

1. 安装并打开一次「工作岛」App（包名 `com.dafeiyu.workisland`，1.5.1+）。
   它的前台服务起来后才会监听 8799 端口。
2. 安装本插件后重启一次 DSHA Web。

## 安装

```bash
dsha-plugin import dsh-work-island-0.1.2.tar.gz
# 用输出里的 previewId / confirmationSha256 确认：
python3 /root/.dsh/plugin-manager.py install-preview <previewId> <confirmationSha256>
```

或者把仓库地址 / Release 压缩包贴到 DSHA 的插件市场入口。

## 端口

默认 `127.0.0.1:8799`；改端口需要同时改工作岛 App 的 `Prefs.httpPort`。

## 许可

MIT
