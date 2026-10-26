# 第三方素材与许可

## agent-hud-open（图标 + bot 模型）

- 来源：[jazzenchen/agent-hud-open](https://github.com/jazzenchen/agent-hud-open)
- 许可：Apache-2.0，全文见
  [`LICENSES/agent-hud-open-LICENSE.txt`](LICENSES/agent-hud-open-LICENSE.txt)。
- 用途：
  - 工作岛的启动图标，以及岛上「Agent HUD」选项的默认图标；
  - 1.4 起，四态 bot 的模型也移植自该项目：`BotState`（14 look / 四态）、
    `BotSigns`（5×7 点阵标识）、`BotSpec`（每个 agent 的形状/配色/眼睛/鳍）、
    `BotForm`（360 点轮廓 / 60×60 配色场）、`BotRenderer`（身体、装饰、表情）。
  - 移植的是行为与几何常量，语言从 Swift/TypeScript 改为 Java，运行在 Android Canvas 上。

## 模型图标（21 个）

- 来源：`agent-hud-open` 的 `src/packages/hud/src/assets/*.png`，原始图标来自
  [LobeHub](https://github.com/lobehub/lobe-icons)。
- 许可：MIT，全文见 [`LICENSES/LobeIcons-LICENSE.txt`](LICENSES/LobeIcons-LICENSE.txt)。
- DeepSeek、Claude、OpenAI、Grok、Cursor、GitHub Copilot、Antigravity、Kimi、Qwen、
  OpenCode、Pi、Hermes、GLM、ZCode、Qoder、CodeBuddy、WorkBuddy、OpenClaw、Devin
  等标识归各自所有者；这里只用于**标识当前模型**，不代表任何官方关联或背书。
- Claude 图标是可选项，默认图标是 Agent HUD；上游 cccodroid 的集成不包含这些图标。

## 未使用

- `clawd-sprites.json` 是 Anthropic PBC 的美术素材（all rights reserved），
  本仓库**没有**包含或使用它。

## 说明

模型图标的商标权与图标文件的 MIT 许可是两回事：MIT 允许复制图标文件，但不授予商标使用权。
如果你要公开发布带这些图标的版本，请自行确认商标使用是否合适，或改用自定义图标。
