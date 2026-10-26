package com.dafeiyu.workisland;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

/**
 * 应用内的「岛预览」。
 *
 * HyperOS 在应用自己处于前台时会把它的超级岛收起（系统策略，应用侧改不了），
 * 所以打开工作岛时状态栏反而看不到岛。这里按系统胶囊的样子画一个实时预览：
 * 左图标 + 一格文字，数据跟最后一次真正发出去的通知一致。
 */
final class IslandPreviewView extends View {
    private static final int BG = 0xFF000000;
    private static final int FG = 0xFFFAF9F5;
    private static final int MUTED = 0xFF9A9A9A;

    private final Paint body = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF capsule = new RectF();
    private final float density;

    private Bitmap icon;
    private String label = "待机中";
    private boolean idle = true;
    private boolean done = false;

    IslandPreviewView(Context c) {
        super(c);
        density = getResources().getDisplayMetrics().density;
        body.setColor(BG);
        text.setColor(FG);
        text.setTextSize(sp(15));
        text.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        text.setTextAlign(Paint.Align.LEFT);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    void update(String state, String shortText, boolean isDone) {
        String st = state == null || state.isEmpty() ? "idle" : state;
        String tx = shortText == null || shortText.isEmpty() ? "待机中" : shortText;
        boolean nowIdle = "idle".equals(st) && "待机中".equals(tx);
        if (tx.equals(label) && nowIdle == idle && isDone == done) return;
        label = tx;
        idle = nowIdle;
        done = isDone;
        try {
            icon = BotPainter.icon(getContext(), Prefs.icon(getContext()),
                    BotState.look(st), BotState.failed(st), 128);
        } catch (Throwable t) {
            icon = null;
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;

        float padH = dp(18);
        float iconSize = dp(34);
        float gap = dp(10);
        float maxText = w - iconSize - gap - padH * 2 - dp(4);
        String shown = label;
        float textW = text.measureText(shown);
        if (textW > maxText) {
            while (shown.length() > 1 && text.measureText(shown + "…") > maxText) {
                shown = shown.substring(0, shown.length() - 1);
            }
            shown = shown + "…";
            textW = text.measureText(shown);
        }

        float capW = Math.min(w, iconSize + gap + textW + padH * 2);
        float capH = dp(46);
        float left = (w - capW) / 2f;
        float top = (h - capH) / 2f;
        capsule.set(left, top, left + capW, top + capH);

        body.setColor(idle ? 0xFF1A1A1A : BG);
        canvas.drawRoundRect(capsule, capH / 2f, capH / 2f, body);

        float iconLeft = left + padH;
        float iconTop = top + (capH - iconSize) / 2f;
        if (icon != null) {
            canvas.drawBitmap(icon, null, new RectF(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize), null);
        } else {
            // bot 绘制失败时至少留个圆，别让预览缺一块
            Paint fallback = new Paint(Paint.ANTI_ALIAS_FLAG);
            fallback.setColor(0xFF3F5AE6);
            canvas.drawCircle(iconLeft + iconSize / 2f, iconTop + iconSize / 2f, iconSize / 2f, fallback);
        }

        Paint.FontMetrics fm = text.getFontMetrics();
        float baseline = top + capH / 2f - (fm.ascent + fm.descent) / 2f;
        text.setColor(idle ? MUTED : FG);
        canvas.drawText(shown, iconLeft + iconSize + gap, baseline, text);
    }

    private float dp(float v) {
        return v * density;
    }

    private float sp(float v) {
        return v * getResources().getDisplayMetrics().scaledDensity;
    }
}
