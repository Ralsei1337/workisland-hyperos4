package com.dafeiyu.workisland;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;

import java.io.File;
import java.io.FileOutputStream;

/** 调试用：把所有模型和所有 look 画成对照表，装机后拉出来看。 */
final class BotSheet {
    private static final int CELL = 220;
    private static final int LABEL = 34;

    private BotSheet() {}

    static File dir(Context context) {
        File dir = new File(context.getExternalFilesDir(null), "sheets");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    static void export(Context context) {
        save(models(context), new File(dir(context), "models.png"));
        save(looks(context), new File(dir(context), "looks.png"));
    }

    static Bitmap models(Context context) {
        String[] keys = {
                "hud", "deepseek", "claude", "openai", "grok", "cursor", "copilot",
                "antigravity", "kimi", "qwen", "opencode", "pi", "hermes", "glm",
                "zcode", "qoder", "codebuddy", "workbuddy", "openclaw", "devin", "fish"
        };
        Bitmap models = Bitmap.createBitmap(CELL * 7, (CELL + LABEL) * 3, Bitmap.Config.ARGB_8888);
        Canvas cm = new Canvas(models);
        cm.drawColor(0xFF151514);
        for (int i = 0; i < keys.length; i++) {
            int col = i % 7, row = i / 7;
            drawCell(cm, context, keys[i], BotState.Look.PLAIN, false,
                    col * CELL, row * (CELL + LABEL), keys[i]);
        }
        return models;
    }

    static Bitmap looks(Context context) {
        BotState.Look[] looks = {
                BotState.Look.IDLE, BotState.Look.PLAIN, BotState.Look.TOOL_COMMAND,
                BotState.Look.TOOL_READ, BotState.Look.TOOL_EDIT, BotState.Look.TOOL_LONG_COMMAND,
                BotState.Look.THINKING, BotState.Look.PERMISSION, BotState.Look.QUESTION,
                BotState.Look.PLAN, BotState.Look.COMPACTING, BotState.Look.ASLEEP,
                BotState.Look.NO_NEWS, BotState.Look.DONE, BotState.Look.PLAIN
        };
        Bitmap states = Bitmap.createBitmap(CELL * 5, (CELL + LABEL) * 3, Bitmap.Config.ARGB_8888);
        Canvas cs = new Canvas(states);
        cs.drawColor(0xFF151514);
        for (int i = 0; i < looks.length; i++) {
            int col = i % 5, row = i / 5;
            boolean failed = i == looks.length - 1;
            drawCell(cs, context, "deepseek", looks[i], failed,
                    col * CELL, row * (CELL + LABEL),
                    BotState.lookName(looks[i]) + (failed ? " ×" : ""));
        }
        return states;
    }

    private static void drawCell(Canvas canvas, Context context, String key,
                                 BotState.Look look, boolean failed, int x, int y, String label) {
        BotForm form = BotForm.get(context, key, false);
        float px = CELL / 1.25f;
        canvas.save();
        canvas.translate(x, y);
        BotState.Sign sign = BotState.sign(look, failed);
        android.graphics.RectF content = BotPainter.content(form, px, sign, look == BotState.Look.IDLE);
        canvas.translate((CELL - content.width()) / 2 - content.left,
                (CELL - content.height()) / 2 - content.top);
        float morph = look == BotState.Look.IDLE ? 0f : 1f;
        BotPainter.draw(canvas, form, px, BotPainter.faceFor(look, failed), morph, 0f,
                sign, 1, 1f);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(0xFFB9B4AA);
        paint.setTextSize(20);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
        canvas.drawText(label, CELL / 2f, CELL + 24, paint);
        canvas.restore();
    }

    private static void save(Bitmap bitmap, File file) {
        try (FileOutputStream out = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        } catch (Throwable ignored) {}
    }
}
