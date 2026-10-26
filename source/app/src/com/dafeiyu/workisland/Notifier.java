package com.dafeiyu.workisland;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.Bundle;
import android.util.Log;

public final class Notifier {
    public static final String ACTION_START = "com.dafeiyu.workisland.START";
    public static final String ACTION_UPDATE = "com.dafeiyu.workisland.UPDATE";
    public static final String ACTION_DONE = "com.dafeiyu.workisland.DONE";
    public static final String ACTION_CANCEL = "com.dafeiyu.workisland.CANCEL";

    public static final int ID = 1001;
    private static final String CHANNEL = "work";
    private static final String EXTRA_PROMOTED = "android.requestPromotedOngoing";
    private static final int ACCENT = 0xFFD97757;

    private Notifier() {}

    static Notification build(Context c, String title, String text, long since, boolean done, String state, String face) {
        return build(c, title, text, since, done, state, face, 0, 0, 0);
    }

    /**
     * @param step      本轮第几个工具动作（0 = 还没开始）
     * @param todoDone  TodoWrite 已完成的条数
     * @param todoTotal TodoWrite 总条数（0 = 没有清单）
     */
    static Notification build(Context c, String title, String text, long since, boolean done,
                              String state, String face, int step, int todoDone, int todoTotal) {
        return build(c, title, text, since, done, state, face, step, todoDone, todoTotal, -1, 0);
    }

    static Notification build(Context c, String title, String text, long since, boolean done,
                              String state, String face, int step, int todoDone, int todoTotal,
                              int morph, int anim) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        ensureChannel(nm);

        String st = state == null || state.isEmpty() ? (done ? "done" : "working") : state;
        Icon small = smallIcon(c, st, face, morph, anim);
        Icon large = largeIcon(c, st, face, morph, anim);
        int color = statusColor(st);

        Intent open = new Intent(c, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, 0, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        Notification.ProgressStyle style = new Notification.ProgressStyle()
                .setProgressTrackerIcon(small)
                .setStyledByProgress(true);
        if (done) {
            style.setProgress(todoTotal > 0 ? todoTotal : 100);
            style.setProgressEndIcon(Icon.createWithResource(c, R.drawable.ic_done));
        } else if (todoTotal > 0 && todoTotal <= 8) {
            // 清单就是「进行到哪一步」：一条一段，已完成的段亮起来。
            java.util.List<Notification.ProgressStyle.Segment> segments = new java.util.ArrayList<>();
            for (int i = 0; i < todoTotal; i++)
                segments.add(new Notification.ProgressStyle.Segment(1)
                        .setColor(i < todoDone ? color : 0x33FFFFFF));
            style.setProgressSegments(segments);
            style.setProgressIndeterminate(false);
            style.setProgress(Math.max(0, Math.min(todoTotal, todoDone)));
        } else {
            style.setProgressIndeterminate(true);
        }

        if (title == null || title.isEmpty()) title = done ? "任务完成" : "正在干活";
        if (text == null) text = "";
        BotState.Look look = BotState.look(st);
        // 折叠胶囊显示的是 shortCriticalText：放回原来的工作状态，只在有清单时补一个进度。
        String baseTitle = title;
        String progress = done ? "" : todoTotal > 0 ? todoDone + "/" + todoTotal : "";
        if (!progress.isEmpty() && !title.contains(progress)) title = baseTitle + " · " + progress;
        StringBuilder sub = new StringBuilder(text.isEmpty() ? BotState.lookName(look) : text);
        if (todoTotal > 0 && !sub.toString().contains("清单"))
            sub.append(" · 清单 ").append(todoDone).append("/").append(todoTotal);
        // ColorOS 的流体云用 title，HyperOS 的超级岛只有一格文字，塞「状态 进度」更值钱。
        boolean hyper = HyperOs.hasIsland();
        String shortText = progress.isEmpty() ? baseTitle : baseTitle + " " + progress;
        if (hyper) {
            shortText = done ? "任务完成" : IslandPayload.headline(st, false)
                    + (progress.isEmpty() ? "" : " " + progress);
        }

        // HyperOS 展开态只认系统默认布局：标题 + 正文 + 大图。
        // 叠 subText 会和正文重复，ProgressStyle 的 AOSP 2025 模板它也没实现，
        // 实测会渲染成空白/错位，所以 HyperOS 分支不挂 style、不设 subText。
        String body = text;
        if (todoTotal > 0) {
            body = body.isEmpty() ? "清单 " + todoDone + "/" + todoTotal
                    : body + " · 清单 " + todoDone + "/" + todoTotal;
        }

        Notification.Builder b = new Notification.Builder(c, CHANNEL)
                .setSmallIcon(small)
                .setLargeIcon(large)
                .setContentTitle(hyper ? baseTitle : title)
                .setContentText(hyper ? body : text)
                .setColor(color)
                .setCategory(hyper ? Notification.CATEGORY_STATUS : Notification.CATEGORY_PROGRESS)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setTimeoutAfter(done ? 8000L : 30 * 60_000L)
                .setShowWhen(true)
                .setWhen(since > 0 ? since : System.currentTimeMillis())
                .setUsesChronometer(!done)
                .setContentIntent(pi);
        if (!hyper) {
            b.setSubText(sub).setStyle(style);
        } else if (!done) {
            // HyperOS 不认 ProgressStyle 的展开态模板，但经典进度条能在通知栏/锁屏正常画出来。
            if (todoTotal > 0) b.setProgress(todoTotal, Math.max(0, Math.min(todoDone, todoTotal)), false);
            else b.setProgress(0, 0, true);
        }
        if (!shortText.isEmpty()) b.setShortCriticalText(shortText);

        Bundle extras = new Bundle();
        extras.putBoolean(EXTRA_PROMOTED, true);
        b.addExtras(extras);
        try {
            Notification.Builder.class.getMethod("setRequestPromotedOngoing", boolean.class).invoke(b, true);
        } catch (Throwable ignored) {}

        if (Prefs.islandParam(c) && HyperOs.canUseFocusParam(c)) {
            Icon doneIcon = Icon.createWithResource(c, R.drawable.ic_done);
            Bundle island = IslandPayload.build(c, shortText, title, text, st,
                    todoDone, todoTotal, done, small, doneIcon);
            if (!island.isEmpty()) b.addExtras(island);
        }

        Notification n = b.build();
        Log.d("WorkIsland", "build title=" + title + " text=" + text + " state=" + st
                + " face=" + face + " icon=" + Prefs.icon(c)
                + " promotable=" + n.hasPromotableCharacteristics()
                + " canPost=" + nm.canPostPromotedNotifications()
                + " hyperos=" + HyperOs.describe(c));
        return n;
    }

    /** 收工后留在通知栏的安静版前台通知：不再请求上岛。 */
    static Notification buildIdle(Context c, String title, String text) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        ensureChannel(nm);
        Intent open = new Intent(c, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, 0, open,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(c, CHANNEL)
                .setSmallIcon(smallIcon(c, "idle", null))
                .setLargeIcon(largeIcon(c))
                .setContentTitle(title == null || title.isEmpty() ? "工作岛" : title)
                .setContentText(text == null || text.isEmpty() ? "在后台等下一轮" : text)
                .setColor(0xFF8A8A8A)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setContentIntent(pi)
                .build();
    }

    /** 小图标：程序化 bot（四态 + 标识 + 每个 agent 的体征）；关掉开关就用旧的简笔图标。 */
    private static Icon smallIcon(Context c, String state, String face) {
        return smallIcon(c, state, face, -1, 0);
    }

    private static Icon smallIcon(Context c, String state, String face, int morph, int anim) {
        String choice = Prefs.icon(c);
        int size = 160;
        if (Prefs.soul(c)) {
            BotState.Look look = BotState.look(state);
            return Icon.createWithBitmap(BotPainter.icon(c, specKey(choice), look,
                    BotState.failed(state), size, morph, anim));
        }
        int legacy = 128;
        Bitmap bmp = Bitmap.createBitmap(legacy, legacy, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        int body = bodyColor(choice);
        drawBody(canvas, choice, body, legacy);
        if (!"idle".equals(state)) drawEyes(canvas, state, legacy, body, face);
        return Icon.createWithBitmap(bmp);
    }

    /** 应用里的图标名 → agent-hud 的 BotSpec 键。 */
    private static String specKey(String choice) {
        if ("codex".equals(choice)) return "openai";
        return choice == null || choice.isEmpty() ? "hud" : choice;
    }

    private static int bodyColor(String choice) {
        switch (choice == null ? "hud" : choice) {
            case "deepseek": return 0xFF3F5AE6;
            case "claude": return 0xFFD97757;
            case "codex": return 0xFF10A37F;
            case "grok": return 0xFF111111;
            case "cursor": return 0xFF111111;
            case "copilot": return 0xFF111111;
            case "antigravity": return 0xFF4C6FFF;
            case "kimi": return 0xFF111111;
            case "qwen": return 0xFF615CED;
            case "opencode": return 0xFF211E1E;
            case "pi": return 0xFF111111;
            case "hermes": return 0xFF111111;
            case "glm": return 0xFF2F6BFF;
            case "zcode": return 0xFF111111;
            case "qoder": return 0xFF2FBF71;
            case "codebuddy": return 0xFF4D6BFE;
            case "workbuddy": return 0xFF2FBF71;
            case "openclaw": return 0xFFE5484D;
            case "devin": return 0xFF111111;
            case "fish": return 0xFFD97757;
            default: return 0xFF1F1E1D;
        }
    }

    private static float luminance(int color) {
        return (0.299f * Color.red(color) + 0.587f * Color.green(color) + 0.114f * Color.blue(color)) / 255f;
    }

    private static void drawBody(Canvas c, String choice, int color, int size) {
        String k = choice == null ? "hud" : choice;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        p.setStyle(Paint.Style.FILL);
        if ("hud".equals(k)) {
            c.drawRoundRect(18, 44, 110, 84, 20, 20, p);
        } else if ("claude".equals(k)) {
            c.drawRoundRect(22, 40, 106, 88, 8, 8, p);
        } else if ("cursor".equals(k)) {
            Path path = new Path();
            for (int i = 0; i < 6; i++) {
                double a = Math.toRadians(-90 + i * 60);
                float x = 64 + (float) (48 * Math.cos(a));
                float y = 64 + (float) (48 * Math.sin(a));
                if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
            }
            path.close();
            c.drawPath(path, p);
        } else if ("opencode".equals(k)) {
            c.drawRoundRect(26, 26, 102, 102, 10, 10, p);
        } else if ("deepseek".equals(k)) {
            c.drawCircle(64, 62, 48, p);
            Path fins = new Path();
            fins.moveTo(20, 68); fins.lineTo(2, 40); fins.lineTo(24, 44); fins.close();
            fins.moveTo(108, 68); fins.lineTo(126, 40); fins.lineTo(104, 44); fins.close();
            c.drawPath(fins, p);
            Paint belly = new Paint(Paint.ANTI_ALIAS_FLAG);
            belly.setColor(0xFFC9D4FF);
            c.drawOval(30, 72, 98, 105, belly);
        } else if ("grok".equals(k)) {
            c.drawCircle(64, 58, 40, p);
            Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
            ring.setStyle(Paint.Style.STROKE);
            ring.setStrokeWidth(6);
            ring.setColor(color);
            c.drawOval(8, 72, 120, 94, ring);
        } else {
            c.drawCircle(64, 64, 48, p);
        }
        if (luminance(color) < 0.22f) {
            Paint o = new Paint(Paint.ANTI_ALIAS_FLAG);
            o.setStyle(Paint.Style.STROKE);
            o.setStrokeWidth(3);
            o.setColor(0xFFFAF9F5);
            if ("hud".equals(k)) c.drawRoundRect(18, 44, 110, 84, 20, 20, o);
            else if ("claude".equals(k)) c.drawRoundRect(22, 40, 106, 88, 8, 8, o);
            else if ("opencode".equals(k)) c.drawRoundRect(26, 26, 102, 102, 10, 10, o);
            else if ("grok".equals(k)) c.drawCircle(64, 58, 40, o);
            else c.drawCircle(64, 64, 48, o);
        }
    }

    /** 在 bot 身体上画表情：干活竖眼、等待问号、完成闭眼、出错 X 眼；face 控制眨眼/看左右。 */
    private static void drawEyes(Canvas c, String state, int size, int body, String face) {
        int eye = luminance(body) > 0.72f ? 0xFF1F1E1D : 0xFFFAF9F5;
        float cy = size * 0.48f;
        float left = size * 0.40f, right = size * 0.60f;
        float w = size * 0.075f, h = size * 0.105f;
        if ("blink".equals(face)) h = size * 0.018f;
        if ("left".equals(face)) { left -= size * 0.025f; right -= size * 0.025f; }
        if ("right".equals(face)) { left += size * 0.025f; right += size * 0.025f; }
        if ("wide".equals(face)) h = size * 0.135f;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(eye);
        if ("done".equals(state) || "happy".equals(face)) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(size * 0.055f);
            p.setStrokeCap(Paint.Cap.ROUND);
            c.drawArc(left - w * 1.4f, cy - h, left + w * 1.4f, cy + h, 200, 140, false, p);
            c.drawArc(right - w * 1.4f, cy - h, right + w * 1.4f, cy + h, 200, 140, false, p);
        } else if ("error".equals(state)) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(size * 0.050f);
            p.setStrokeCap(Paint.Cap.ROUND);
            for (float x : new float[]{left, right}) {
                c.drawLine(x - w, cy - h, x + w, cy + h, p);
                c.drawLine(x + w, cy - h, x - w, cy + h, p);
            }
        } else {
            p.setStyle(Paint.Style.FILL);
            c.drawRoundRect(left - w, cy - h, left + w, cy + h, w * 0.6f, w * 0.6f, p);
            c.drawRoundRect(right - w, cy - h, right + w, cy + h, w * 0.6f, w * 0.6f, p);
            if ("waiting".equals(state)) {
                Paint q = new Paint(Paint.ANTI_ALIAS_FLAG);
                q.setColor(eye);
                q.setTextSize(size * 0.22f);
                q.setTypeface(Typeface.DEFAULT_BOLD);
                q.setTextAlign(Paint.Align.CENTER);
                c.drawText("?", size * 0.80f, size * 0.30f, q);
            }
        }
    }

    private static int iconRes(String choice) {
        switch (choice == null ? "hud" : choice) {
            case "deepseek": return R.drawable.model_deepseek;
            case "claude": return R.drawable.model_claude;
            case "codex": return R.drawable.model_chatgpt;
            case "grok": return R.drawable.model_grok;
            case "cursor": return R.drawable.model_cursor;
            case "copilot": return R.drawable.model_copilot;
            case "antigravity": return R.drawable.model_antigravity;
            case "kimi": return R.drawable.model_kimi;
            case "qwen": return R.drawable.model_qwen;
            case "opencode": return R.drawable.model_opencode;
            case "pi": return R.drawable.model_pi;
            case "hermes": return R.drawable.model_hermes;
            case "glm": return R.drawable.model_glm;
            case "zcode": return R.drawable.model_zcode;
            case "qoder": return R.drawable.model_qoder;
            case "codebuddy": return R.drawable.model_codebuddy;
            case "workbuddy": return R.drawable.model_workbuddy;
            case "openclaw": return R.drawable.model_openclaw;
            case "devin": return R.drawable.model_devin;
            case "fish": return R.drawable.ic_fish;
            default: return R.drawable.ic_hud;
        }
    }

    private static Drawable iconDrawable(Context c, String choice) {
        return c.getDrawable(iconRes(choice));
    }

    private static Icon largeIcon(Context c) {
        return largeIcon(c, "idle", null);
    }

    /** 通知面板里的大图标也用同一只 bot，比品牌 logo 更有「人」。 */
    private static Icon largeIcon(Context c, String state, String face) {
        return largeIcon(c, state, face, -1, 0);
    }

    private static Icon largeIcon(Context c, String state, String face, int morph, int anim) {
        if (Prefs.soul(c)) {
            return Icon.createWithBitmap(BotPainter.icon(c, specKey(Prefs.icon(c)),
                    BotState.look(state), BotState.failed(state), 256, morph, anim));
        }
        String choice = Prefs.icon(c);
        int res = "hud".equals(choice) ? R.mipmap.ic_launcher : iconRes(choice);
        return Icon.createWithResource(c, res);
    }

    private static int statusColor(String state) {
        switch (state == null ? "" : state) {
            case "thinking": return 0xFF5B8DEF;
            case "reading": return 0xFF7C6CF0;
            case "writing": return 0xFFD97757;
            case "running": return 0xFF4C9AFF;
            case "searching": return 0xFF00A6A6;
            case "waiting": return 0xFFE6A23C;
            case "done": return 0xFF4CAF50;
            case "error": return 0xFFE5484D;
            case "idle": return 0xFF8A8A8A;
            default: return ACCENT;
        }
    }

    static void post(Context c, String title, String text, long since, boolean done, String state, String face) {
        post(c, title, text, since, done, state, face, 0, 0, 0);
    }

    static void post(Context c, String title, String text, long since, boolean done,
                     String state, String face, int step, int todoDone, int todoTotal) {
        post(c, title, text, since, done, state, face, step, todoDone, todoTotal, -1, 0);
    }

    static void post(Context c, String title, String text, long since, boolean done,
                     String state, String face, int step, int todoDone, int todoTotal,
                     int morph, int anim) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        nm.notify(ID, build(c, title, text, since, done, state, face, step, todoDone, todoTotal, morph, anim));
    }

    static void post(Context c, String title, String text, long since, boolean done) {
        post(c, title, text, since, done, done ? "done" : "working", done ? "happy" : "open");
    }

    static void cancel(Context c) {
        c.getSystemService(NotificationManager.class).cancel(ID);
    }

    private static void ensureChannel(NotificationManager nm) {
        NotificationChannel ch = new NotificationChannel(
                CHANNEL, "工作状态", NotificationManager.IMPORTANCE_LOW);
        ch.setDescription("DeepSeek 正在工作、任务完成时显示在超级岛 / 灵动岛");
        ch.setShowBadge(false);
        ch.setSound(null, null);
        nm.createNotificationChannel(ch);
    }
}
