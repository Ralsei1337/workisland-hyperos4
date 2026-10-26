# 上游集成完整源码（PR #21 + #22）

这些文件来自 cccodroid 上游的工作岛集成：

- **PR #21（已合并）**：网关内订阅、`bus.mjs` / `settings.mjs`、`server.mjs`、设置页、`AndroidManifest.xml`、上游文档。分支 `feat/work-island-signed`。
- **PR #22（OPEN，基于 v0.4.9 main）**：标题跟随动作、清单进度（`tasks` 事件 / `TaskCreate` / `TaskUpdate`）、`state` extra，以及 review 后的修正（MCP 工具名通用化、固定错误正文）。分支 `feat/work-island-followup`。

本目录里的 `gateway/gw/work-island-core.mjs`、`gateway/gw/work-island.mjs`、`gateway/tests/work-island.test.mjs` 已更新到 **PR #22 的最新提交**；单测 **19 条**。其余文件保持 #21 合并时的版本。

完整 diff：

- `../upstream-pr-review.patch`：PR #21
- `../upstream-pr-review-22.patch`：PR #22

上游项目与这些文件使用 Apache-2.0。
