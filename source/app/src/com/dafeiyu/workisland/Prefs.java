package com.dafeiyu.workisland;

import android.content.Context;
import android.content.SharedPreferences;

final class Prefs {
    private static final String FILE = "island";
    private static final String TOKEN = "token";
    private static final String PORT = "port";
    private static final String ICON = "icon";
    private static final String SOUL = "soul";
    private static final String ISLAND_PARAM = "island_param";
    private static final String HTTP_PORT = "http_port";
    private static final String LAST_TEXT = "last_island_text";
    private static final String LAST_STATE = "last_island_state";
    private static final String LAST_DONE = "last_island_done";

    private Prefs() {}

    static void save(Context c, String token, int port) {
        SharedPreferences p = c.getSharedPreferences(FILE, Context.MODE_PRIVATE);
        p.edit().putString(TOKEN, token).putInt(PORT, port).apply();
    }

    static String token(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(TOKEN, "");
    }

    static int port(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE).getInt(PORT, 8791);
    }

    static String icon(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(ICON, "hud");
    }

    static void setIcon(Context c, String value) {
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(ICON, value).apply();
    }

    /** 程序化 bot（四态 + 标识 + 每个 agent 的体征）；关掉就退回旧的简笔图标。 */
    static boolean soul(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(SOUL, true);
    }

    static void setSoul(Context c, boolean value) {
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(SOUL, value).apply();
    }

    /**
     * 是否给通知附加小米官方 miui.focus.param。
     * 默认关：没申请到小米超级岛权限时系统会忽略它，个别 HyperOS 版本还会因此
     * 把展开态渲染坏；等应用真正拿到 APP_ID 再打开。
     */
    /** 记下最后一次真正发出去的通知文案，应用内预览用。 */
    static void setLastIsland(Context c, String shortText, String state, boolean done) {
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit()
                .putString(LAST_TEXT, shortText == null ? "" : shortText)
                .putString(LAST_STATE, state == null ? "" : state)
                .putBoolean(LAST_DONE, done)
                .apply();
    }

    static String lastIslandText(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(LAST_TEXT, "待机中");
    }

    static String lastIslandState(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString(LAST_STATE, "idle");
    }

    static boolean lastIslandDone(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(LAST_DONE, false);
    }

    /** 本地 HTTP 桥端口（给 DSHA / 脚本推状态用）。 */
    static int httpPort(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE).getInt(HTTP_PORT, HttpBridge.DEFAULT_PORT);
    }

    static void setHttpPort(Context c, int value) {
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putInt(HTTP_PORT, value).apply();
    }

    static boolean islandParam(Context c) {
        return c.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(ISLAND_PARAM, false);
    }

    static void setIslandParam(Context c, boolean value) {
        c.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(ISLAND_PARAM, value).apply();
    }
}
