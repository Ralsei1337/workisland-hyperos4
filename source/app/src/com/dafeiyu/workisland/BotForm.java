package com.dafeiyu.workisland;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * BotForm.swift 的 Java 版：从素材算出 360 个径向轮廓点，再从轮廓采样出身体形状。
 * 这样形状永远跟着素材走，和 agent-hud 的「灵魂」是同一套几何。
 */
final class BotForm {
    private static final int RES = 256;
    private static final int FIELD = 60;
    private static final int CACHE = 8;
    private static final Map<String, BotForm> cache = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, BotForm> eldest) {
            return size() > CACHE;
        }
    };

    final String key;
    final BotSpecs.Spec spec;
    final boolean light;
    final Bitmap artwork;
    final Bitmap field;
    final float[] logo = new float[360];
    final float[] body = new float[360];
    final float top;
    final float logoTop;
    final int ink;

    private BotForm(String key, BotSpecs.Spec spec, boolean light, Bitmap artwork, Bitmap field,
                    float[] logo, float[] body, float top, float logoTop, int ink) {
        this.key = key;
        this.spec = spec;
        this.light = light;
        this.artwork = artwork;
        this.field = field;
        System.arraycopy(logo, 0, this.logo, 0, 360);
        System.arraycopy(body, 0, this.body, 0, 360);
        this.top = top;
        this.logoTop = logoTop;
        this.ink = ink;
    }

    static synchronized BotForm get(Context context, String key, boolean light) {
        if (key == null || key.isEmpty()) key = "hud";
        String cacheKey = key + "|" + light;
        BotForm cached = cache.get(cacheKey);
        if (cached != null) return cached;
        BotSpecs.Spec spec = BotSpecs.of(key, light);
        Bitmap art = artwork(context, key);
        if (art == null) art = Bitmap.createBitmap(RES, RES, Bitmap.Config.ARGB_8888);
        float[] logo = reaches(art, spec.center[0], spec.center[1]);
        float[] body = bodyReaches(spec, logo);
        Bitmap field = spec.paint == null ? colorField(art) : null;
        float top = highest(spec, spec.ownEyes != null ? logo : body)
                + (spec.ownEyes != null ? 0 : spec.settle);
        float logoTop = highest(spec, logo);
        BotForm form = new BotForm(key, spec, light, art, field, logo, body, top, logoTop,
                light ? 0xFF1D1D1F : 0xFFFFFFFF);
        cache.put(cacheKey, form);
        return form;
    }

    private static float highest(BotSpecs.Spec spec, float[] ring) {
        float min = Float.MAX_VALUE;
        for (int i = 0; i < 360; i++) {
            float y = spec.center[1] + (float) Math.sin(Math.toRadians(i)) * ring[i];
            if (y < min) min = y;
        }
        return min;
    }

    // ---- 素材 ----

    private static int drawable(String key) {
        switch (key) {
            case "hud": return R.drawable.ic_hud;
            case "fish": return R.drawable.ic_fish;
            case "deepseek": return R.drawable.model_deepseek;
            case "claude": return R.drawable.model_claude;
            case "openai": return R.drawable.model_chatgpt;
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
            default: return R.drawable.ic_hud;
        }
    }

    private static String tint(String key) {
        if ("openai".equals(key)) return "#10a37f";
        switch (key) {
            case "grok": case "cursor": case "kimi": case "pi":
            case "copilot": case "hermes": case "zcode":
                return "#ffffff"; // 暗色地面上的模板白
            default:
                return null;
        }
    }

    private static Bitmap artwork(Context context, String key) {
        try {
            Drawable drawable = context.getDrawable(drawable(key));
            if (drawable == null) return null;
            Bitmap bitmap = Bitmap.createBitmap(RES, RES, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            drawable.setBounds(0, 0, RES, RES);
            drawable.draw(canvas);
            String tint = tint(key);
            if (tint != null) {
                // 先画素材再在它上面刷一层 SRC_IN，透明的眼窝/镂空会保留。
                Paint paint = new Paint();
                paint.setColor(Color.parseColor(tint));
                paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
                canvas.drawRect(0, 0, RES, RES, paint);
            }
            return bitmap;
        } catch (Throwable ignored) {
            return null;
        }
    }

    // ---- 轮廓 ----

    private static float[] reaches(Bitmap art, float cx, float cy) {
        int[] pixels = new int[RES * RES];
        art.getPixels(pixels, 0, RES, 0, 0, RES, RES);
        float[] out = new float[360];
        for (int i = 0; i < 360; i++) {
            double a = Math.toRadians(i);
            float dx = (float) Math.cos(a), dy = (float) Math.sin(a);
            int last = 0;
            for (int d = 0; d < 384; d++) {
                int x = Math.round(cx * RES + dx * d);
                int y = Math.round(cy * RES + dy * d);
                if (x < 0 || y < 0 || x >= RES || y >= RES) break;
                if ((pixels[y * RES + x] >>> 24) > 127) last = d;
            }
            out[i] = Math.max(0.02f, last / (float) RES);
        }
        return out;
    }

    private static float[] bodyReaches(BotSpecs.Spec spec, float[] logo) {
        BotSpecs.Shape shape = spec.shape;
        if (shape.kind == BotSpecs.CIRCLE) {
            float[] out = new float[360];
            java.util.Arrays.fill(out, spec.radius);
            return out;
        }
        if (shape.kind == BotSpecs.OUTLINE) {
            float[] current = logo;
            int width = Math.max(1, Math.round(shape.smooth));
            for (int pass = 0; pass < 2; pass++) {
                float[] next = new float[360];
                for (int i = 0; i < 360; i++) {
                    float sum = 0;
                    for (int off = -width; off <= width; off++)
                        sum += current[(i + off + 360) % 360];
                    next[i] = sum / (width * 2 + 1);
                }
                current = next;
            }
            float mean = 0;
            for (float v : current) mean += v;
            mean /= 360f;
            float[] out = new float[360];
            for (int i = 0; i < 360; i++)
                out[i] = Math.max(spec.radius * 0.55f,
                        spec.radius * (1 + shape.keep * (current[i] / mean - 1)));
            return out;
        }
        Bitmap bitmap = Bitmap.createBitmap(RES, RES, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.scale(RES, RES);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(0xFFFFFFFF);
        paint.setStyle(Paint.Style.FILL);
        Path path = primitive(spec);
        canvas.drawPath(path, paint);
        if (shape.kind == BotSpecs.CAT) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(0.16f * spec.radius);
            paint.setStrokeJoin(Paint.Join.ROUND);
            float cx = spec.center[0], cy = spec.center[1], r = spec.radius;
            for (int side = -1; side <= 1; side += 2) {
                Path ear = new Path();
                ear.moveTo(cx + side * 0.94f * r, cy - 0.02f * r);
                ear.lineTo(cx + side * 0.78f * r, cy - 1.04f * r);
                ear.lineTo(cx + side * 0.16f * r, cy - 0.6f * r);
                ear.close();
                canvas.drawPath(ear, paint);
            }
        }
        return reaches(bitmap, spec.center[0], spec.center[1]);
    }

    static Path primitive(BotSpecs.Spec s) {
        float cx = s.center[0], cy = s.center[1], r = s.radius;
        BotSpecs.Shape shape = s.shape;
        Path path = new Path();
        if (shape.kind == BotSpecs.CIRCLE || shape.kind == BotSpecs.OUTLINE) {
            path.addCircle(cx, cy, r, Path.Direction.CW);
            return path;
        }
        if (shape.kind == BotSpecs.POLYGON) {
            float[][] points = new float[shape.sides][2];
            for (int i = 0; i < shape.sides; i++) {
                double a = Math.toRadians(shape.rotation + (i * 360.0) / shape.sides);
                points[i][0] = cx + r * (float) Math.cos(a);
                points[i][1] = cy + r * (float) Math.sin(a);
            }
            return rounded(points, shape.round);
        }
        if (shape.kind == BotSpecs.RECT) {
            float w = shape.width / 2, h = shape.height / 2, skew = shape.skew / 2;
            return rounded(new float[][]{
                    {cx - w + skew, cy - h}, {cx + w + skew, cy - h},
                    {cx + w - skew, cy + h}, {cx - w - skew, cy + h}}, shape.round);
        }
        if (shape.kind == BotSpecs.BOB) {
            float[][] at = new float[2][2];
            path.moveTo(cx - 1.14f * r, cy + 0.98f * r);
            path.quadTo(cx - 0.92f * r, cy + 0.72f * r, cx - 0.98f * r, cy + 0.3f * r);
            for (int step = 0; step <= 48; step++) {
                double a = Math.PI + (step / 48.0) * Math.PI;
                path.lineTo(cx + 0.98f * r * (float) Math.cos(a),
                        cy + (-0.08f + 0.96f * (float) Math.sin(a)) * r);
            }
            path.lineTo(cx + 0.98f * r, cy + 0.3f * r);
            path.quadTo(cx + 0.92f * r, cy + 0.72f * r, cx + 1.14f * r, cy + 0.98f * r);
            path.lineTo(cx + 0.62f * r, cy + 0.9f * r);
            path.quadTo(cx, cy + 1.06f * r, cx - 0.62f * r, cy + 0.9f * r);
            path.close();
            return path;
        }
        // cat
        RectF body = new RectF(cx - 1.02f * r, cy + 0.1f * r - 0.88f * r,
                cx + 1.02f * r, cy + 0.1f * r + 0.88f * r);
        path.addOval(body, Path.Direction.CW);
        for (int side = -1; side <= 1; side += 2) {
            path.moveTo(cx + side * 0.94f * r, cy - 0.02f * r);
            path.lineTo(cx + side * 0.78f * r, cy - 1.04f * r);
            path.lineTo(cx + side * 0.16f * r, cy - 0.6f * r);
            path.close();
        }
        return path;
    }

    private static Path rounded(float[][] points, float radius) {
        Path path = new Path();
        int n = points.length;
        float[] first = mid(points[n - 1], points[0]);
        path.moveTo(first[0], first[1]);
        for (int i = 0; i < n; i++) {
            float[] point = points[i];
            float[] next = mid(point, points[(i + 1) % n]);
            path.quadTo(point[0], point[1], next[0], next[1]);
        }
        path.close();
        return path;
    }

    private static float[] mid(float[] a, float[] b) {
        return new float[]{(a[0] + b[0]) / 2, (a[1] + b[1]) / 2};
    }

    /** 60×60 配色场：采样、横向/纵向补洞、三次盒式模糊。 */
    private static Bitmap colorField(Bitmap art) {
        int[] pixels = new int[RES * RES];
        art.getPixels(pixels, 0, RES, 0, 0, RES, RES);
        float[] values = new float[FIELD * FIELD * 3];
        boolean[] known = new boolean[FIELD * FIELD];
        float[] low = {255, 255, 255}, high = {0, 0, 0}, sum = {0, 0, 0};
        int seen = 0;
        for (int y = 0; y < 40; y++) {
            for (int x = 0; x < 40; x++) {
                int sx = (int) Math.floor((x + 0.5) * RES / 40);
                int sy = (int) Math.floor((y + 0.5) * RES / 40);
                int source = pixels[sy * RES + sx];
                if ((source >>> 24) <= 200) continue;
                int target = (y + 10) * FIELD + x + 10;
                known[target] = true;
                for (int c = 0; c < 3; c++) {
                    int value = channel(source, c);
                    values[target * 3 + c] = value;
                    low[c] = Math.min(low[c], value);
                    high[c] = Math.max(high[c], value);
                    sum[c] += value;
                }
                seen++;
            }
        }
        if (seen == 0) return null;
        boolean flat = true;
        for (int c = 0; c < 3; c++) if (high[c] - low[c] >= 14) flat = false;
        if (flat) {
            Bitmap swatch = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
            swatch.setPixel(0, 0, Color.rgb(Math.round(sum[0] / seen),
                    Math.round(sum[1] / seen), Math.round(sum[2] / seen)));
            return swatch;
        }
        boolean[] filled = known.clone();
        for (int vertical = 0; vertical < 2; vertical++) {
            boolean[] current = known.clone();
            for (int row = 0; row < FIELD; row++) {
                int before = -1, position = 0;
                while (position < FIELD) {
                    if (current[at(row, position, vertical)]) {
                        before = position++;
                        continue;
                    }
                    int after = position;
                    while (after < FIELD && !current[at(row, after, vertical)]) after++;
                    if (before >= 0 || after < FIELD) {
                        for (int x = position; x < after; x++) {
                            int from = before < 0 ? at(row, after, vertical) : at(row, before, vertical);
                            int to = after < FIELD ? at(row, after, vertical) : from;
                            float share = before < 0 ? 1 : after >= FIELD ? 0
                                    : (x - before) / (float) (after - before);
                            int index = at(row, x, vertical);
                            for (int c = 0; c < 3; c++)
                                values[index * 3 + c] = values[from * 3 + c] * (1 - share)
                                        + values[to * 3 + c] * share;
                            filled[index] = true;
                        }
                    }
                    position = after;
                }
            }
            known = filled.clone();
        }
        float[] other = values.clone();
        for (int pass = 0; pass < 3; pass++) {
            for (int y = 0; y < FIELD; y++)
                for (int x = 0; x < FIELD; x++)
                    for (int c = 0; c < 3; c++)
                        other[(y * FIELD + x) * 3 + c] =
                                (values[(y * FIELD + Math.max(0, x - 1)) * 3 + c]
                                        + values[(y * FIELD + x) * 3 + c]
                                        + values[(y * FIELD + Math.min(FIELD - 1, x + 1)) * 3 + c]) / 3;
            for (int y = 0; y < FIELD; y++)
                for (int x = 0; x < FIELD; x++)
                    for (int c = 0; c < 3; c++)
                        values[(y * FIELD + x) * 3 + c] =
                                (other[(Math.max(0, y - 1) * FIELD + x) * 3 + c]
                                        + other[(y * FIELD + x) * 3 + c]
                                        + other[(Math.min(FIELD - 1, y + 1) * FIELD + x) * 3 + c]) / 3;
        }
        int[] out = new int[FIELD * FIELD];
        for (int i = 0; i < FIELD * FIELD; i++) {
            out[i] = Color.rgb(clamp(values[i * 3]), clamp(values[i * 3 + 1]), clamp(values[i * 3 + 2]));
        }
        Bitmap bitmap = Bitmap.createBitmap(FIELD, FIELD, Bitmap.Config.ARGB_8888);
        bitmap.setPixels(out, 0, FIELD, 0, 0, FIELD, FIELD);
        return bitmap;
    }

    private static int at(int row, int offset, int vertical) {
        return vertical == 0 ? row * FIELD + offset : offset * FIELD + row;
    }

    private static int channel(int color, int index) {
        return index == 0 ? Color.red(color) : index == 1 ? Color.green(color) : Color.blue(color);
    }

    private static int clamp(float value) {
        return Math.max(0, Math.min(255, Math.round(value)));
    }

    /** formOutline：logo → body 的 360 点变形。 */
    Path outline(float eased, float turn) {
        Path path = new Path();
        float cx = spec.center[0], cy = spec.center[1];
        for (int i = 0; i < 360; i++) {
            float sample = ((i - turn) % 360 + 360) % 360;
            int low = (int) Math.floor(sample);
            float part = sample - low;
            float b = body[low] + (body[(low + 1) % 360] - body[low]) * part;
            float r = logo[i] + (b - logo[i]) * eased;
            double a = Math.toRadians(i);
            float x = cx + (float) Math.cos(a) * r;
            float y = cy + (float) Math.sin(a) * r;
            if (i == 0) path.moveTo(x, y);
            else path.lineTo(x, y);
        }
        path.close();
        return path;
    }

    /** 把 60×60 配色场画进 1.5×1.5 的归一化区域。 */
    void drawField(Canvas canvas, Paint paint) {
        if (field == null) {
            paint.setShader(null);
            paint.setColor(ink);
            canvas.drawRect(-0.3f, -0.3f, 1.3f, 1.3f, paint);
            return;
        }
        paint.setShader(null);
        canvas.drawBitmap(field, null, new RectF(-0.25f, -0.25f, 1.25f, 1.25f), paint);
    }

    int bodyColor() {
        if (field == null) return spec.paint != null && spec.paint.color != null
                ? Color.parseColor(spec.paint.color) : ink;
        int p = field.getWidth() == 1
                ? field.getPixel(0, 0)
                : field.getPixel(FIELD / 2, FIELD / 2);
        return p;
    }

    void recycle() {
        // 缓存由 LRU 管，这里只给手动清理留个口子。
    }

    static void clearCache() {
        for (BotForm form : new ArrayList<>(cache.values())) {
            if (form.artwork != null && !form.artwork.isRecycled()) form.artwork.recycle();
            if (form.field != null && !form.field.isRecycled()) form.field.recycle();
        }
        cache.clear();
    }
}
