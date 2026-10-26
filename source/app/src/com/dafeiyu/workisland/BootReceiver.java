package com.dafeiyu.workisland;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(i.getAction())) return;
        try {
            c.startForegroundService(new Intent(c, IslandService.class)
                    .setAction(IslandService.ACTION_IDLE));
        } catch (Throwable ignored) {}
    }
}
