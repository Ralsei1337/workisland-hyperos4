package com.dafeiyu.workisland;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class IslandReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        String a = i.getAction();
        if (a == null) return;
        if (Notifier.ACTION_CANCEL.equals(a)) {
            Notifier.cancel(c);
            return;
        }
        Notifier.post(
            c,
            i.getStringExtra("title"),
            i.getStringExtra("text"),
            i.getLongExtra("since", System.currentTimeMillis()),
            Notifier.ACTION_DONE.equals(a),
            i.getStringExtra("state"),
            i.getStringExtra("face"),
            i.getIntExtra("step", 0),
            i.getIntExtra("todoDone", 0),
            i.getIntExtra("todoTotal", 0),
            i.getIntExtra("morph", -1),
            i.getIntExtra("anim", 0));
    }
}
