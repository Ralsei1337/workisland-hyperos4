package com.dafeiyu.workisland;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

public class MainActivity extends Activity {
    private static final int BG = 0xFF1F1E1D;
    private static final int CARD = 0xFF282725;
    private static final int STROKE = 0xFF3B3936;
    private static final int FG = 0xFFFAF9F5;
    private static final int MUTED = 0xFFA9A49B;
    private static final int ACCENT = 0xFFD97757;

    private static final String[] LOOK_NAMES = {
            "待命（醒来）", "干活", "执行命令", "读文件", "写文件", "长命令",
            "思考中", "等你确认", "有问题", "整理清单", "压缩上下文",
            "睡着了", "没有新消息", "完成", "出错（×）"
    };
    private static final BotState.Look[] LOOKS = {
            BotState.Look.IDLE, BotState.Look.PLAIN, BotState.Look.TOOL_COMMAND,
            BotState.Look.TOOL_READ, BotState.Look.TOOL_EDIT, BotState.Look.TOOL_LONG_COMMAND,
            BotState.Look.THINKING, BotState.Look.PERMISSION, BotState.Look.QUESTION,
            BotState.Look.PLAN, BotState.Look.COMPACTING, BotState.Look.ASLEEP,
            BotState.Look.NO_NEWS, BotState.Look.DONE, BotState.Look.PLAIN
    };
    private static final boolean[] LOOK_FAILED = {
            false, false, false, false, false, false, false, false, false,
            false, false, false, false, false, true
    };

    private TextView status;
    private TextView botLabel;
    private BotView botView;
    private String botKey = "hud";
    private BotState.Look botLook = BotState.Look.TOOL_COMMAND;
    private boolean botFailed = false;
    private boolean sheetMode = false;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(BG);
        getWindow().setNavigationBarColor(BG);
        if (getIntent() != null && getIntent().getBooleanExtra("sheet", false)) {
            sheetMode = true;
            showSheets();
            return;
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.setPadding(dp(24), dp(32), dp(24), dp(24));
        root.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView title = new TextView(this);
        title.setText("工作岛");
        title.setTextColor(FG);
        title.setTextSize(30);
        title.setGravity(Gravity.CENTER);
        root.addView(title, lp(0, 6, 0, 0));

        TextView sub = new TextView(this);
        sub.setText("DeepSeek 的工作状态 · 显示在" + HyperOs.islandName(this));
        sub.setTextColor(MUTED);
        sub.setTextSize(14);
        sub.setGravity(Gravity.CENTER);
        root.addView(sub, lp(0, 0, 0, 18));

        status = new TextView(this);
        status.setTextColor(ACCENT);
        status.setTextSize(13);
        status.setGravity(Gravity.CENTER);
        status.setLineSpacing(dp(3), 1f);
        root.addView(status, lp(0, 0, 0, 16));

        // ---- bot 舞台 ----
        // 不装进卡片：让 bot 直接站在这页纸上，像一枚自己躺着的感叹号。
        LinearLayout stage = new LinearLayout(this);
        stage.setOrientation(LinearLayout.VERTICAL);
        stage.setGravity(Gravity.CENTER_HORIZONTAL);

        botView = new BotView(this);
        botView.setBot(botKey, botLook, botFailed);
        stage.addView(botView, new LinearLayout.LayoutParams(dp(340), dp(340)));

        botLabel = new TextView(this);
        botLabel.setTextColor(MUTED);
        botLabel.setTextSize(13);
        botLabel.setGravity(Gravity.CENTER);
        stage.addView(botLabel, lp(0, 2, 0, 4));
        root.addView(stage, lp(0, 0, 0, 12));

        LinearLayout phases = new LinearLayout(this);
        phases.setOrientation(LinearLayout.HORIZONTAL);
        phases.setGravity(Gravity.CENTER);
        phases.addView(phaseChip("醒来", BotState.Look.IDLE, false));
        phases.addView(phaseChip("干活", BotState.Look.TOOL_COMMAND, false));
        phases.addView(phaseChip("请求", BotState.Look.PERMISSION, false));
        phases.addView(phaseChip("完成", BotState.Look.DONE, false));
        root.addView(phases, lp(0, 0, 0, 10));

        Spinner lookSpinner = new Spinner(this);
        ArrayAdapter<String> lookAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, LOOK_NAMES);
        lookSpinner.setAdapter(lookAdapter);
        lookSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                show(LOOKS[position], LOOK_FAILED[position]);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        // 等控件挂上去再选中「执行命令」，否则首帧会被默认的「待命」覆盖。
        lookSpinner.post(() -> lookSpinner.setSelection(2));
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, -2);
        slp.setMargins(dp(12), 0, dp(12), dp(8));
        root.addView(lookSpinner, slp);

        // ---- 开关：程序化 bot ----
        Switch soul = new Switch(this);
        soul.setText("程序化 bot（四态 + 标识 + 体征）");
        soul.setTextColor(FG);
        soul.setTextSize(14);
        soul.setChecked(Prefs.soul(this));
        soul.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean checked) {
                Prefs.setSoul(MainActivity.this, checked);
                refreshLabels();
                try {
                    startForegroundService(new Intent(MainActivity.this, IslandService.class)
                            .setAction(IslandService.ACTION_IDLE));
                } catch (Throwable ignored) {}
            }
        });
        LinearLayout.LayoutParams klp = new LinearLayout.LayoutParams(-1, -2);
        klp.setMargins(dp(8), 0, dp(8), dp(4));
        root.addView(soul, klp);

        Switch param = new Switch(this);
        param.setText("给超级岛附官方参数（需小米鉴权，默认关）");
        param.setTextSize(13);
        param.setTextColor(FG);
        param.setChecked(Prefs.islandParam(this));
        param.setOnCheckedChangeListener((buttonView, checked) -> Prefs.setIslandParam(MainActivity.this, checked));
        root.addView(param, klp);

        TextView soulNote = new TextView(this);
        soulNote.setText("关掉就退回旧的简笔图标。标识：! 请求　? 提问　≡ 清单　× 出错　⧗ 限额　… 思考　z 睡着");
        soulNote.setTextColor(0xFF6F6B64);
        soulNote.setTextSize(11);
        soulNote.setGravity(Gravity.CENTER);
        root.addView(soulNote, lp(0, 0, 0, 14));

        root.addView(button("开始一个测试任务", ACCENT, BG, v -> demo(Notifier.ACTION_START)));
        root.addView(button("更新状态", CARD, FG, v -> demo(Notifier.ACTION_UPDATE)));
        root.addView(button("下一种状态（岛上试文案）", CARD, FG, v -> demoNextState()));
        root.addView(button("服务方式开始（等同 driver）", CARD, FG, v -> demoService()));
        root.addView(button("完成", CARD, FG, v -> demo(Notifier.ACTION_DONE)));
        root.addView(button("清除通知", CARD, MUTED, v -> demo(Notifier.ACTION_CANCEL)));
        root.addView(button("后台保活设置", CARD, FG, v -> keepAlive()));

        TextView iconLabel = new TextView(this);
        iconLabel.setText("岛上图标（按模型自选）");
        iconLabel.setTextColor(MUTED);
        iconLabel.setTextSize(13);
        iconLabel.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(-2, -2);
        ilp.setMargins(0, dp(12), 0, dp(6));
        root.addView(iconLabel, ilp);

        final String[] iconNames = {
                "Agent HUD", "DeepSeek", "Claude", "Codex", "Grok", "Cursor", "Copilot",
                "Antigravity", "Kimi", "Qwen", "OpenCode", "Pi", "Hermes", "GLM",
                "ZCode", "Qoder", "CodeBuddy", "WorkBuddy", "OpenClaw", "Devin", "小鱼"
        };
        final String[] iconKeys = {
                "hud", "deepseek", "claude", "codex", "grok", "cursor", "copilot",
                "antigravity", "kimi", "qwen", "opencode", "pi", "hermes", "glm",
                "zcode", "qoder", "codebuddy", "workbuddy", "openclaw", "devin", "fish"
        };
        Spinner iconSpinner = new Spinner(this);
        ArrayAdapter<String> iconAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, iconNames);
        iconSpinner.setAdapter(iconAdapter);
        int selected = 0;
        String current = Prefs.icon(this);
        for (int i = 0; i < iconKeys.length; i++) if (iconKeys[i].equals(current)) selected = i;
        iconSpinner.setSelection(selected);
        iconSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Prefs.setIcon(MainActivity.this, iconKeys[position]);
                botKey = iconKeys[position];
                botView.setBot(botKey, botLook, botFailed);
                try {
                    startForegroundService(new Intent(MainActivity.this, IslandService.class)
                            .setAction(IslandService.ACTION_IDLE));
                } catch (Throwable ignored) {}
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });
        LinearLayout.LayoutParams islp = new LinearLayout.LayoutParams(-1, -2);
        islp.setMargins(dp(12), 0, dp(12), dp(10));
        root.addView(iconSpinner, islp);

        TextView iconNote = new TextView(this);
        iconNote.setText("模型图标来自 LobeHub（MIT）；商标归各自所有者，非官方关联。");
        iconNote.setTextColor(0xFF6F6B64);
        iconNote.setTextSize(11);
        iconNote.setGravity(Gravity.CENTER);
        root.addView(iconNote, lp(0, 0, 0, 8));

        TextView hint = new TextView(this);
        hint.setText("如果岛上没出现：系统设置 → 通知 → 工作岛 → 允许实时更新");
        hint.setTextColor(0xFF6F6B64);
        hint.setTextSize(12);
        hint.setGravity(Gravity.CENTER);
        root.addView(hint, lp(0, 22, 0, 0));

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        scroll.addView(root);
        setContentView(scroll);
        // 预览放大后内容会超出一屏，别让 ScrollView 自己滚到底把标题顶进状态栏。
        scroll.post(() -> scroll.scrollTo(0, 0));
        ensureService();

        if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 7);
        }
        botKey = Prefs.icon(this);
        botView.setBot(botKey, botLook, botFailed);
        refreshLabels();
        refresh();

    }

    private void showSheets() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFF151514);
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        int width = getResources().getDisplayMetrics().widthPixels;
        for (Bitmap sheet : new Bitmap[]{BotSheet.models(this), BotSheet.looks(this)}) {
            android.widget.ImageView view = new android.widget.ImageView(this);
            view.setImageBitmap(sheet);
            view.setAdjustViewBounds(true);
            view.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
            column.addView(view, new LinearLayout.LayoutParams(width, -2));
        }
        scroll.addView(column);
        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        HyperOs.invalidate();
        if (!sheetMode) refresh();
    }

    private View phaseChip(String text, BotState.Look look, boolean failed) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(13);
        b.setTextColor(FG);
        GradientDrawable d = new GradientDrawable();
        d.setColor(CARD);
        d.setCornerRadius(dp(14));
        d.setStroke(dp(1), STROKE);
        b.setBackground(d);
        b.setPadding(dp(14), 0, dp(14), 0);
        b.setMinHeight(dp(40));
        b.setMinimumHeight(dp(40));
        b.setOnClickListener(v -> show(look, failed));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1f);
        p.setMargins(dp(3), 0, dp(3), 0);
        b.setLayoutParams(p);
        return b;
    }

    private void show(BotState.Look look, boolean failed) {
        botLook = look;
        botFailed = failed;
        botView.setBot(botKey, look, failed);
        refreshLabels();
    }

    private void refreshLabels() {
        BotState.Look look = botLook;
        BotState.Sign sign = BotState.sign(look, botFailed);
        String signName = sign == null ? "无标识"
                : sign == BotState.Sign.EXCLAMATION ? "叹号"
                : sign == BotState.Sign.QUESTION ? "问号"
                : sign == BotState.Sign.LINES ? "清单线"
                : sign == BotState.Sign.CROSS ? "叉"
                : sign == BotState.Sign.HOURGLASS ? "沙漏"
                : sign == BotState.Sign.ELLIPSIS ? "省略号"
                : "z";
        botLabel.setText(BotState.phaseName(BotState.phase(look)) + " · "
                + BotState.lookName(look) + " · " + signName
                + (Prefs.soul(this) ? "" : "（开关已关）"));
    }

    private void refresh() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        boolean post = nm.areNotificationsEnabled();
        boolean promoted = nm.canPostPromotedNotifications();
        PowerManager pm = getSystemService(PowerManager.class);
        boolean battery = pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
        StringBuilder line = new StringBuilder();
        line.append("通知：").append(post ? "开" : "关")
            .append("　实时更新：").append(promoted ? "允许" : "未允许")
            .append("\n电池：").append(battery ? "不优化" : "还在优化");
        if (HyperOs.isXiaomi()) {
            line.append("\n").append(HyperOs.osName())
                .append("　超级岛：").append(HyperOs.hasIsland() ? "支持" : "不支持")
                .append("　焦点通知：").append(HyperOs.canShowFocus(this) ? "已开" : "未开");
        }
        status.setText(line.toString());
    }

    private void ensureService() {
        try {
            startForegroundService(new Intent(this, IslandService.class)
                    .setAction(IslandService.ACTION_IDLE));
        } catch (Throwable ignored) {}
    }

    private void keepAlive() {
        if (HyperOs.isXiaomi()) {
            hyperKeepAlive();
            return;
        }
        PowerManager pm = getSystemService(PowerManager.class);
        if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
            Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            i.setData(Uri.parse("package:" + getPackageName()));
            try { startActivity(i); return; } catch (Throwable ignored) {}
        }
        startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + getPackageName())));
    }

    /**
     * HyperOS 的后台存活是三道门：自启动、省电策略、通知权限。
     * 三处分散在不同页面，所以先给一个明确的入口列表，别让用户自己找。
     */
    private void hyperKeepAlive() {
        final String[] items = {"自启动管理", "电池与省电策略", "应用通知设置", "电池优化（不优化）"};
        new android.app.AlertDialog.Builder(this)
                .setTitle("HyperOS 后台保活")
                .setItems(items, (d, which) -> {
                    switch (which) {
                        case 0: openHyperPage("com.miui.securitycenter",
                                "com.miui.permcenter.autostart.AutoStartManagementActivity"); break;
                        case 1: openHyperPage("com.miui.powerkeeper",
                                "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"); break;
                        case 2:
                            startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                    .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
                            break;
                        default: requestIgnoreBattery(); break;
                    }
                })
                .setNegativeButton("关闭", null)
                .show();
    }

    private void openHyperPage(String pkg, String cls) {
        try {
            Intent i = new Intent();
            i.setComponent(new android.content.ComponentName(pkg, cls));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Throwable t) {
            // HyperOS 各版本页面名有差异，退回到应用详情页。
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName())));
        }
    }

    private void requestIgnoreBattery() {
        Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
        i.setData(Uri.parse("package:" + getPackageName()));
        try {
            startActivity(i);
        } catch (Throwable t) {
            startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + getPackageName())));
        }
    }

    private static final String[] DEMO_STATES = {"thinking", "reading", "writing", "waiting", "running"};
    private static final String[] DEMO_TITLES = {"正在思考", "正在读代码", "正在改文件", "等你确认", "正在执行"};
    private static final String[] DEMO_TEXTS = {"在想下一步…", "AppInfo 的 3 个文件", "IslandPayload.java", "继续执行构建？", "bash build.sh"};
    private int demoIndex = 0;

    private void demoNextState() {
        int i = demoIndex++ % DEMO_STATES.length;
        Intent intent = new Intent(Notifier.ACTION_UPDATE);
        intent.setClass(this, IslandReceiver.class);
        intent.putExtra("title", DEMO_TITLES[i]);
        intent.putExtra("text", DEMO_TEXTS[i]);
        intent.putExtra("state", DEMO_STATES[i]);
        intent.putExtra("face", "open");
        intent.putExtra("since", System.currentTimeMillis());
        intent.putExtra("todoDone", i);
        intent.putExtra("todoTotal", DEMO_STATES.length);
        sendBroadcast(intent);
    }

    /** 走真实链路：start-foreground-service → IslandService → 前台通知上岛。 */
    private void demoService() {
        try {
            startForegroundService(new Intent(this, IslandService.class)
                    .setAction(Notifier.ACTION_UPDATE)
                    .putExtra("title", "DeepSeek 正在干活")
                    .putExtra("text", "正在用 Bash…")
                    .putExtra("state", "running")
                    .putExtra("since", System.currentTimeMillis())
                    .putExtra("step", 3)
                    .putExtra("todoDone", 2)
                    .putExtra("todoTotal", 5));
        } catch (Throwable t) {
            // 后台启动 FGS 被拒时退回普通通知
            demo(Notifier.ACTION_UPDATE);
        }
    }

    /** 测试按钮走真实链路：start-foreground-service → IslandService → 通知上岛。 */
    private void demo(String action) {
        boolean done = Notifier.ACTION_DONE.equals(action);
        Intent i = new Intent(this, IslandService.class).setAction(action)
                .putExtra("title", done ? "任务完成" : "DeepSeek 正在干活")
                .putExtra("text", done ? "测试完成" : "正在用 Bash…")
                .putExtra("state", done ? "done" : "running")
                .putExtra("face", done ? "happy" : "open")
                .putExtra("since", System.currentTimeMillis())
                .putExtra("step", 3)
                .putExtra("todoDone", 2)
                .putExtra("todoTotal", 5);
        try {
            startForegroundService(i);
        } catch (Throwable t) {
            sendBroadcast(new Intent(action).setClass(this, IslandReceiver.class)
                    .putExtras(i));
        }
    }

    private View button(String text, int bg, int fg, View.OnClickListener click) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setTextSize(15);
        b.setTextColor(fg);
        GradientDrawable d = new GradientDrawable();
        d.setColor(bg);
        d.setCornerRadius(dp(16));
        d.setStroke(dp(1), STROKE);
        b.setBackground(d);
        b.setOnClickListener(click);
        b.setPadding(0, dp(14), 0, dp(14));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
        p.setMargins(0, 0, 0, dp(10));
        b.setLayoutParams(p);
        return b;
    }

    private LinearLayout.LayoutParams lp(int t, int b) {
        return lp(0, t, 0, b);
    }

    private LinearLayout.LayoutParams lp(int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, -2);
        p.setMargins(dp(l), dp(t), dp(r), dp(b));
        return p;
    }

    private int dp(int v) {
        return Math.round(getResources().getDisplayMetrics().density * v);
    }
}
