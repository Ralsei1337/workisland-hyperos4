#!/usr/bin/env python3
"""把交付目录里的 Markdown 汇总成一个可打印的 HTML，并调用 Chromium 生成 PDF。"""
import pathlib
import subprocess
import sys

import markdown

ROOT = pathlib.Path(sys.argv[1])
DOCS = [
    ("STATUS.md", "测试状态"),
    ("README.md", "项目说明"),
    ("docs/01-方案与设计.md", "一、方案与设计"),
    ("docs/02-开发与部署.md", "二、开发与部署"),
    ("docs/03-测试报告.md", "三、测试报告"),
    ("docs/04-提交说明.md", "四、提交说明"),
    ("docs/05-开发过程与真机测试.md", "五、开发过程与真机测试"),
    ("docs/06-状态与保活.md", "六、状态与保活"),
]

def md(path: pathlib.Path) -> str:
    text = path.read_text(encoding="utf-8")
    return markdown.markdown(
        text,
        extensions=["tables", "fenced_code", "toc", "sane_lists"],
        output_format="html5",
    )

def main() -> None:
    cover = """
<section class="cover">
  <div class="kicker">ANDROID 16 · COLOROS 16 · LIVE UPDATES</div>
  <img class="logo" src="source/app/res/mipmap-xxxhdpi/ic_launcher.png" alt="工作岛图标">
  <h1>工作岛</h1>
  <p class="sub">把 Claude 的工作状态，放到灵动岛上</p>
  <div class="rule"></div>
  <div class="meta">
    <div><span>宿主</span>cccodroid 0.4.2</div>
    <div><span>设备</span>OnePlus PJA110 · ColorOS 16.0.3</div>
    <div><span>应用</span>com.dafeiyu.workisland 1.4</div>
    <div><span>状态</span>测试阶段 · 仅 ColorOS 16 / Android 16 验证</div>
    <div><span>日期</span>2026-10-26</div>
  </div>
  <div class="hash">APK SHA-256<br>6484f58e1c298784d765c18c1dc35aabe11004cdff037440774b074140caa8ca</div>
</section>
"""

    toc = """
<section class="toc">
  <h2>目录</h2>
  <p class="toc-lead">这份材料按“为什么做 → 怎么设计 → 怎么实现 → 怎么验证 → 怎么提交”组织。</p>
  <table class="toc-table">
    <tr><th>文档</th><th>内容</th></tr>
    <tr><td>测试状态</td><td>仅在 ColorOS 16 / Android 16 验证；未验证项、已知限制、反馈方式</td></tr>
    <tr><td>项目说明</td><td>一句话、交付物、快速开始、测试环境</td></tr>
    <tr><td>一、方案与设计</td><td>背景与问题、总体架构、状态机、事件文案、收工判定、关键技术、安全与限制</td></tr>
    <tr><td>二、开发与部署</td><td>目录落点、构建链、安装、网关接入、driver 部署、保活、调试、回滚</td></tr>
    <tr><td>三、测试报告</td><td>12 个用例、关键日志、截图索引、6 个问题与修复</td></tr>
    <tr><td>四、提交说明</td><td>验收步骤、设计要点、已知限制、变更记录</td></tr>
    <tr><td>五、开发过程与真机测试</td><td>时间线、踩坑与修法、全部真机测试记录、过程链接</td></tr>
    <tr><td>六、状态与保活</td><td>标题跟随动作、四态 bot 与标识、清单进度、后台保活与自动恢复</td></tr>
  </table>
  <div class="toc-note">
    <b>三个非显然结论</b>
    <ol>
      <li>ColorOS 16 的流体云直接接 Android 16 Live Updates，不需要 OPPO 私有白名单。</li>
      <li>ColorOS 会在后台冻结应用（连前台服务也冻），所以定时器必须放在网关 driver 里。</li>
      <li>收工要看 <code>runs</code> 而不是 <code>run</code>，并且要二次复查 + 3 秒宽限，否则会提前报完成。</li>
    </ol>
  </div>
</section>"""

    parts = [cover, "".join(toc)]
    for path, title in DOCS:
        body = md(ROOT / path)
        parts.append(
            f'<article class="doc page-break"><div class="doc-title">{title}</div>{body}</article>'
        )

    shots = [
        ("screenshots/01-working.png", "工作态：鱼图标 +「正在干活」"),
        ("screenshots/02-done.png", "完成态：「任务完成」+ 用时"),
        ("screenshots/03-idle.png", "8 秒后：岛上收起"),
        ("screenshots/04-background.png", "后台：两个应用都退到桌面，岛仍在"),
        ("screenshots/05-app.png", "应用界面：通知开 / 实时更新允许 / 电池不优化"),
        ("screenshots/06-final.png", "聊天界面 + 胶囊"),
        ("screenshots/09-bot-preview.png", "四态 bot：应用内预览（干活 · 执行命令）"),
        ("screenshots/10-bot-states.png", "21 个模型 + 14 个 look 的对照表"),
        ("screenshots/11-notification-bot.png", "通知面板：bot 大图标 + 头顶 !"),
        ("screenshots/12-island-ask.png", "岛上「等你确认」+ !"),
        ("screenshots/13-island-thinking.png", "思考三帧：胶囊「思考中 1/4」，省略号亮点在动"),
    ]
    gallery = ['<section class="gallery page-break"><h2>界面截图</h2><div class="grid">']
    for src, cap in shots:
        gallery.append(
            f'<figure><img src="{src}" alt="{cap}"><figcaption>{cap}</figcaption></figure>'
        )
    gallery.append("</div></section>")

    css = """
    @page { size: A4; margin: 17mm 16mm 18mm 16mm; }
    * { -webkit-print-color-adjust: exact; print-color-adjust: exact; }
    html, body { margin: 0; padding: 0; }
    body {
      font-family: "Noto Sans CJK SC", "Noto Sans SC", "Source Han Sans SC", "sans-serif";
      color: #24221f; font-size: 10.2pt; line-height: 1.72;
    }
    .cover {
      background: #1F1E1D; color: #FAF9F5; height: 240mm; padding: 26mm 20mm;
      box-sizing: border-box; page-break-after: always; position: relative;
    }
    .cover .kicker { color: #D97757; letter-spacing: 2.4px; font-size: 8.5pt; }
    .cover .logo { width: 34mm; height: 34mm; margin-top: 22mm; border-radius: 8mm; }
    .cover h1 { color: #FAF9F5; font-size: 46pt; margin: 4mm 0 2mm; letter-spacing: 2px; }
    .cover .sub { color: #C9C4BA; font-size: 14pt; margin: 0; }
    .cover .rule { width: 34mm; height: 2px; background: #D97757; margin: 12mm 0; }
    .cover .meta div { margin: 2.4mm 0; color: #E8E4DA; font-size: 9.5pt; }
    .cover .meta span { display: inline-block; width: 22mm; color: #8F8A80; }
    .cover .hash { position: absolute; bottom: 22mm; left: 20mm; right: 20mm;
      color: #6F6B64; font-size: 7.2pt; line-height: 1.6; word-break: break-all; }
    .toc { page-break-after: always; }
    .toc h2 { font-size: 22pt; color: #1F1E1D; border-bottom: 2px solid #D97757;
      padding-bottom: 2mm; margin-bottom: 5mm; }
    .toc-lead { color: #57534D; font-size: 10.5pt; margin: 0 0 7mm; }
    .toc-table td { font-size: 9.6pt; padding: 3mm 3mm; }
    .toc-table td:first-child { width: 30mm; font-weight: 600; color: #1F1E1D; }
    .toc-note { margin-top: 12mm; padding: 5mm 6mm; background: #F6F3EE;
      border-left: 4px solid #D97757; }
    .toc-note b { color: #1F1E1D; font-size: 11pt; }
    .toc-note li { margin: 2.8mm 0; font-size: 9.6pt; }
    .page-break { page-break-before: always; }
    .doc-title { font-size: 9pt; color: #D97757; letter-spacing: 2px;
      border-top: 1px solid #D97757; padding-top: 2mm; margin-bottom: 6mm; }
    h1 { font-size: 22pt; line-height: 1.3; margin: 0 0 4mm; color: #1F1E1D; }
    h2 { font-size: 15pt; margin: 8mm 0 3mm; color: #1F1E1D;
      border-left: 4px solid #D97757; padding-left: 3mm; }
    h3 { font-size: 12pt; margin: 6mm 0 2mm; color: #3A3835; }
    p { margin: 0 0 2.6mm; }
    ul, ol { margin: 0 0 3mm; padding-left: 6mm; }
    li { margin: 1.2mm 0; }
    blockquote { margin: 4mm 0; padding: 3mm 4mm; background: #F6F3EE;
      border-left: 3px solid #D97757; color: #57534D; }
    code { font-family: "Noto Sans Mono CJK SC", "monospace"; font-size: 8.8pt;
      background: #F1EEE8; padding: 0.4mm 1.2mm; border-radius: 2px; }
    pre { background: #F4F1EB; border: 1px solid #E3DED5; border-left: 3px solid #D97757;
      padding: 3.2mm 4mm; overflow-x: hidden; white-space: pre-wrap;
      word-break: break-all; page-break-inside: avoid; }
    pre code { background: none; padding: 0; font-size: 8.4pt; }
    table { width: 100%; border-collapse: collapse; margin: 3mm 0 4mm;
      font-size: 9pt; page-break-inside: avoid; }
    th { background: #1F1E1D; color: #FAF9F5; text-align: left;
      padding: 2mm 2.4mm; font-weight: 600; }
    td { border-bottom: 1px solid #E3DED5; padding: 1.8mm 2.4mm; vertical-align: top; }
    tr:nth-child(even) td { background: #FAF9F5; }
    a { color: #B85C3E; text-decoration: none; }
    hr { border: none; border-top: 1px solid #E3DED5; margin: 6mm 0; }
    .gallery h2 { border-left: 4px solid #D97757; padding-left: 3mm; }
    .grid { display: flex; flex-wrap: wrap; gap: 5mm; justify-content: center; }
    figure { margin: 0; width: 43%; page-break-inside: avoid; }
    figure img { width: 100%; border: 1px solid #D8D2C8; border-radius: 3mm; }
    figcaption { font-size: 8pt; color: #6F6B64; text-align: center; margin-top: 1.6mm; }
    """

    html = f"""<!doctype html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<title>工作岛 · 方案与设计</title>
<style>{css}</style>
</head>
<body>
{''.join(parts)}
{''.join(gallery)}
</body>
</html>
"""
    out_html = ROOT / "提交版.html"
    out_html.write_text(html, encoding="utf-8")
    out_pdf = ROOT / "pdf" / "工作岛-方案与设计.pdf"
    subprocess.run([
        "chromium-headless",
        "--headless",
        "--disable-gpu",
        "--no-sandbox",
        "--no-pdf-header-footer",
        "--run-all-compositor-stages-before-draw",
        "--virtual-time-budget=12000",
        f"--print-to-pdf={out_pdf}",
        out_html.as_uri(),
    ], check=True)
    print(out_pdf, out_pdf.stat().st_size)

if __name__ == "__main__":
    main()
