package com.dafeiyu.workisland;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;

/**
 * 点岛 / 点通知要打开谁。
 *
 * 工作岛只是「显示层」，真正在干活的是 DSHA（DeepSeek Harness Android）。
 * 所以优先跳到 DSHA 的启动界面；没装 DSHA 时才回落到工作岛自己。
 */
final class Targets {
    /** 按优先级排：新版 DSHA 包名、旧版包名。 */
    private static final String[] CANDIDATES = {
            "dsh.android.client",
            "com.dsh.client",
    };

    private Targets() {}

    static Intent open(Context c) {
        PackageManager pm = c.getPackageManager();
        for (String pkg : CANDIDATES) {
            try {
                Intent i = pm.getLaunchIntentForPackage(pkg);
                if (i != null) {
                    i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
                    return i;
                }
            } catch (Throwable ignored) {}
        }
        return new Intent(c, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
    }
}
