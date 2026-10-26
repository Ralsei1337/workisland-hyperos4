package com.dafeiyu.workisland;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;

/**
 * HyperOS（小米澎湃 OS）能力探测。
 *
 * 工作岛原本只面向 ColorOS 的流体云（Android 16 Live Update / promoted ongoing）。
 * HyperOS 3 起把同一套 promoted ongoing 通知画进「小米超级岛」，但另外有一层
 * 官方「超级岛」参数（通知 extras 里的 miui.focus.param），需要向小米申请
 * APP_ID + 签名鉴权后系统才会采用。两条通道互不冲突：
 *
 *   - 通道一：AOSP promoted ongoing。只要系统支持 Live Update 就会进岛，
 *     岛上取 smallIcon + shortCriticalText（真机实测 HyperOS 4.0 / Android 17）。
 *   - 通道二：miui.focus.param。需要系统 focusProtocol >= 1 且应用被授予
 *     焦点通知权限，才能自定义大岛/小岛/息屏文案。
 *
 * 所以适配策略是：通道一必须永远可用；通道二有能力才附加，出错不影响通道一。
 */
final class HyperOs {
    private static final String KEY_OS = "ro.mi.os.version.name";
    private static final String KEY_MIUI = "ro.miui.ui.version.name";
    private static final String KEY_ISLAND = "persist.sys.feature.island";
    private static final String KEY_FOCUS_PROTOCOL = "notification_focus_protocol";
    private static final Uri FOCUS_PROVIDER = Uri.parse("content://miui.statusbar.notification.public");

    /** 属性与权限查询都要走系统调用，通知更新很频繁，缓存起来别每次问。 */
    private static int cachedIsland = -1;
    private static long focusCheckedAt = 0;
    private static boolean focusCached = false;
    private static final long FOCUS_TTL_MS = 30_000L;

    private HyperOs() {}

    /** 读系统属性（android.os.SystemProperties 是隐藏类，只能反射）。 */
    static String prop(String key, String def) {
        try {
            Class<?> c = Class.forName("android.os.SystemProperties");
            Object v = c.getMethod("get", String.class, String.class).invoke(null, key, def);
            return v == null ? def : String.valueOf(v);
        } catch (Throwable t) {
            return def;
        }
    }

    /** 是否小米/红米机型（HyperOS 或更老的 MIUI）。 */
    static boolean isXiaomi() {
        String os = prop(KEY_OS, "");
        String miui = prop(KEY_MIUI, "");
        return !os.isEmpty() || !miui.isEmpty();
    }

    /** "OS4.0" / "V816" 这类版本串，用于界面展示。 */
    static String osName() {
        String os = prop(KEY_OS, "");
        if (!os.isEmpty()) return "HyperOS " + os.replace("OS", "");
        String miui = prop(KEY_MIUI, "");
        return miui.isEmpty() ? "HyperOS" : "MIUI " + miui.replace("V", "");
    }

    /** HyperOS 主版本号：OS4.0 -> 4；取不到返回 0。 */
    static int majorVersion() {
        String os = prop(KEY_OS, "");
        if (os.startsWith("OS")) {
            int i = 2;
            int n = 0;
            boolean any = false;
            while (i < os.length() && Character.isDigit(os.charAt(i))) {
                n = n * 10 + (os.charAt(i) - '0');
                i++;
                any = true;
            }
            if (any) return n;
        }
        // 老 MIUI：V816 / V14 之类，HyperOS 之前的机器没有岛，返回 0 即可。
        return 0;
    }

    /** 系统是否声明支持超级岛（persist.sys.feature.island）。 */
    static boolean hasIsland() {
        if (cachedIsland < 0) {
            boolean v = "1".equals(prop(KEY_ISLAND, "0"))
                    || "true".equalsIgnoreCase(prop(KEY_ISLAND, "false"));
            cachedIsland = v ? 1 : 0;
        }
        return cachedIsland == 1;
    }

    /**
     * 小米焦点通知协议版本：
     * 0 = 不支持；1 = OS1；2 = OS2；3 = OS3 及以上的超级岛。
     */
    static int focusProtocol(Context c) {
        try {
            return Settings.System.getInt(c.getContentResolver(), KEY_FOCUS_PROTOCOL, 0);
        } catch (Throwable t) {
            return 0;
        }
    }

    /** 应用是否被授予焦点通知权限（系统侧开关）。取不到一律当作 false。 */
    static boolean canShowFocus(Context c) {
        long now = System.currentTimeMillis();
        if (now - focusCheckedAt < FOCUS_TTL_MS) return focusCached;
        focusCheckedAt = now;
        try {
            Bundle extras = new Bundle();
            extras.putString("package", c.getPackageName());
            Bundle out = c.getContentResolver().call(FOCUS_PROVIDER, "canShowFocus", null, extras);
            focusCached = out != null && out.getBoolean("canShowFocus", false);
        } catch (Throwable t) {
            focusCached = false;
        }
        return focusCached;
    }

    /** 用户可能在设置里改开关，回到前台时手动让缓存失效。 */
    static void invalidate() {
        focusCheckedAt = 0;
    }

    /** 设备通道一是否可用：系统支持 promoted ongoing 通知。 */
    static boolean supportsPromoted(Context c) {
        try {
            return c.getSystemService(android.app.NotificationManager.class)
                    .canPostPromotedNotifications();
        } catch (Throwable t) {
            return false;
        }
    }

    /** 这条通知能不能走 miui.focus.param 通道二。 */
    static boolean canUseFocusParam(Context c) {
        return hasIsland() && focusProtocol(c) >= 1 && canShowFocus(c);
    }

    /** 界面上怎么称呼这个位置。 */
    static String islandName(Context c) {
        if (isXiaomi() && hasIsland()) return "超级岛";
        if (isXiaomi()) return "实时通知";
        return "灵动岛";
    }

    /** 一句话诊断，给应用首页和日志用。 */
    static String describe(Context c) {
        if (!isXiaomi()) return "";
        StringBuilder sb = new StringBuilder();
        sb.append(osName());
        sb.append(" · 岛").append(hasIsland() ? "支持" : "不支持");
        sb.append(" · 协议").append(focusProtocol(c));
        sb.append(" · 焦点通知").append(canShowFocus(c) ? "已开" : "未开");
        return sb.toString();
    }
}
