package com.dafeiyu.workisland;

import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.Bundle;

import org.json.JSONArray;
import org.json.JSONObject;


/**
 * 小米超级岛（HyperOS 3/4）的官方参数构造器。
 *
 * 结构照着《小米超级岛通知模板库》来：
 *   param_v2
 *    ├─ ticker / aodTitle        状态栏与息屏文案
 *    ├─ param_island
 *    │   ├─ bigIslandArea        A 区图文组件1 + B 区文本组件
 *    │   └─ smallIslandArea      图标组件
 *    └─ baseInfo                 焦点通知正文
 *
 * 只有 canShowFocus() 为真（系统给了焦点通知权限）才值得发送；否则系统会
 * 忽略这些参数、只按普通 promoted 通知展示。构造失败也绝不抛异常——
 * 通道一（AOSP promoted ongoing）必须不受影响。
 */
final class IslandPayload {
    private static final String KEY_PARAM = "miui.focus.param";
    private static final String KEY_PICS = "miui.focus.pics";
    private static final String KEY_ACTIONS = "miui.focus.actions";
    private static final String PIC_BOT = "miui.focus.pic_bot";
    private static final String PIC_DONE = "miui.focus.pic_done";
    private static final String ACTION_OPEN = "miui.focus.action_open";
    private static final String ACTION_DONE = "miui.focus.action_done";

    /** 调试用：把大岛/小岛图标换成纯色方块，用来判断系统到底吃不吃 param。 */
    static final boolean TEST_ICON = false;

    private IslandPayload() {}

    /** 状态 → 大岛 A 区的大字（≤4 个中文字，多了系统会裁切）。 */
    static String headline(String state, boolean done) {
        if (done) return "完成";
        BotState.Look look = BotState.look(state);
        switch (look) {
            case PERMISSION: return "等确认";
            case QUESTION: return "有问题";
            case PLAN: return "理清单";
            case COMPACTING: return "压缩中";
            case THINKING: return "思考中";
            case TOOL_EDIT: return "写文件";
            case TOOL_COMMAND: return "执行中";
            case TOOL_READ: return "读文件";
            case TOOL_LONG_COMMAND: return "长任务";
            case ASLEEP: return "睡着了";
            case IDLE: return "待命";
            case DONE: return "完成";
            default: return "干活中";
        }
    }

    /**
     * 组装 extras。icon 用于小岛与大岛 A 区；done 时换成完成图标。
     */
    static Bundle build(Context c, String shortText, String title, String text,
                        String state, int todoDone, int todoTotal, boolean done,
                        Icon icon, Icon doneIcon) {
        Bundle extras = new Bundle();
        try {
            String json = json(c, shortText, title, text, state, todoDone, todoTotal, done);
            extras.putString(KEY_PARAM, json);

            Bundle pics = new Bundle();
            Icon shown = TEST_ICON ? testIcon(c, done) : icon;
            if (shown != null) pics.putParcelable(PIC_BOT, shown);
            if (doneIcon != null) pics.putParcelable(PIC_DONE, doneIcon);
            extras.putBundle(KEY_PICS, pics);

            bundleActions(c, extras);
        } catch (Throwable t) {
            // 通道二要失败就整体退回去，别弄坏通知本身。
            extras.clear();
        }
        return extras;
    }

    private static void bundleActions(Context c, Bundle extras) {
        Intent open = Targets.open(c);
        android.app.PendingIntent pi = android.app.PendingIntent.getActivity(
                c, 0, open,
                android.app.PendingIntent.FLAG_IMMUTABLE | android.app.PendingIntent.FLAG_UPDATE_CURRENT);
        android.app.Notification.Action action = new android.app.Notification.Action.Builder(
                Icon.createWithResource(c, R.drawable.ic_done), "打开", pi).build();
        Bundle actions = new Bundle();
        actions.putParcelable(ACTION_OPEN, action);
        extras.putBundle(KEY_ACTIONS, actions);
    }

    private static Icon testIcon(Context c, boolean done) {
        android.graphics.Bitmap bmp = android.graphics.Bitmap.createBitmap(96, 96,
                android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas canvas = new android.graphics.Canvas(bmp);
        canvas.drawColor(done ? 0xFF00FF00 : 0xFFFF0000);
        return Icon.createWithBitmap(bmp);
    }

    private static String json(Context c, String shortText, String title, String text,
                               String state, int todoDone, int todoTotal, boolean done)
            throws Exception {
        JSONObject root = new JSONObject();
        JSONObject v2 = new JSONObject();
        root.put("param_v2", v2);

        v2.put("protocol", 1);
        v2.put("business", "workisland");
        v2.put("updatable", true);
        v2.put("islandFirstFloat", false);
        v2.put("enableFloat", false);
        v2.put("islandTimeout", done ? 8 : 3600);
        if (shortText != null && !shortText.isEmpty()) {
            v2.put("ticker", shortText);
            v2.put("aodTitle", shortText);
        }

        JSONObject island = new JSONObject();
        v2.put("param_island", island);
        island.put("islandProperty", 1);
        island.put("islandTimeout", done ? 8 : 3600);
        island.put("islandOrder", true);

        // 大岛 A 区：bot 图标 + 状态大字 + 进度小字。
        JSONObject big = new JSONObject();
        island.put("bigIslandArea", big);
        JSONObject left = new JSONObject();
        big.put("imageTextInfoLeft", left);
        left.put("type", 1);
        JSONObject pic = new JSONObject();
        pic.put("type", 1);
        pic.put("pic", done ? PIC_DONE : PIC_BOT);
        left.put("picInfo", pic);
        JSONObject textInfo = new JSONObject();
        textInfo.put("title", headline(state, done));
        String progress = todoTotal > 0 ? todoDone + "/" + todoTotal : "";
        if (!progress.isEmpty()) textInfo.put("content", progress);
        textInfo.put("showHighlightColor", false);
        textInfo.put("narrowFont", true);
        left.put("textInfo", textInfo);

        // 大岛 B 区：文本组件（模板 2）。标题取动作，正文取清单/用时。
        JSONObject right = new JSONObject();
        big.put("textInfo", right);
        right.put("frontTitle", phaseWord(state, done));
        right.put("title", trim(title, 6));
        String sub = todoTotal > 0 ? "清单 " + todoDone + "/" + todoTotal : trim(text, 10);
        right.put("content", sub);
        right.put("showHighlightColor", false);
        right.put("narrowFont", false);
        right.put("showDivider", true);

        // 小岛：只放 bot 图标。
        JSONObject small = new JSONObject();
        island.put("smallIslandArea", small);
        JSONObject smallPic = new JSONObject();
        smallPic.put("type", 1);
        smallPic.put("pic", done ? PIC_DONE : PIC_BOT);
        small.put("picInfo", smallPic);

        // 焦点通知正文（OS2 也认这一段）。
        JSONObject base = new JSONObject();
        v2.put("baseInfo", base);
        base.put("type", 2);
        base.put("title", trim(title, 12));
        base.put("content", trim(text, 24));
        if (todoTotal > 0) base.put("specialTitle", todoDone + "/" + todoTotal);

        // 动作表：打开应用。
        JSONArray acts = new JSONArray();
        JSONObject act = new JSONObject();
        act.put("action", ACTION_OPEN);
        act.put("type", 1);
        acts.put(act);
        v2.put("actions", acts);

        return root.toString();
    }

    private static String phaseWord(String state, boolean done) {
        if (done) return "完成";
        switch (BotState.phase(BotState.look(state))) {
            case WAKES: return "待命";
            case ASKS: return "等你";
            case DONE: return "完成";
            default: return "进行中";
        }
    }

    private static String trim(String s, int max) {
        if (s == null) return "";
        String t = s.trim();
        if (t.length() <= max) return t;
        return t.substring(0, Math.max(0, max - 1)) + "…";
    }
}
