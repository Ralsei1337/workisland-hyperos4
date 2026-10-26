package com.dafeiyu.workisland;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;

/**
 * BotRenderer.swift 的 Java 版。归一化坐标：一个 mark 占 1×1，四周各留 0.3。
 * 通知小图标用 morph=1 的静态帧；应用内预览用同一套绘制循环逐帧动。
 */
final class BotPainter {
    static final float MARGIN = 0.3f;

    static final class Face {
        float open = 1, look = 0, lookY = 0, wide = 1, happy = 0, lean = 0, crossed = 0;
    }

    /** 与 BotMotion 的 pose 同义：归一化 mark 坐标里的位移、旋转和缩放。 */
    static final class Pose {
        float x, y, rotation, scaleX = 1, scaleY = 1;
    }

    private static final int INK = 0xFF0D0D0F;
    /** 通知上和预览里都要看得清，眼睛整体比 agent-hud 原版放大一点。 */
    private static final float EYE_W = 1.30f, EYE_H = 1.15f;

    private BotPainter() {}

    static Face faceFor(BotState.Look look, boolean failed) {
        Face face = new Face();
        if (failed) {
            face.crossed = 1;
        } else if (look == BotState.Look.DONE) {
            face.happy = 1;
        } else if (look == BotState.Look.PERMISSION || look == BotState.Look.QUESTION) {
            face.wide = 1.22f;
        } else if (look == BotState.Look.THINKING) {
            face.look = -0.55f;
            face.lookY = -0.45f;
        } else if (look == BotState.Look.ASLEEP) {
            face.open = 0;
        }
        return face;
    }

    private static final java.util.LinkedHashMap<String, Bitmap> ICONS =
            new java.util.LinkedHashMap<>(24, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(java.util.Map.Entry<String, Bitmap> eldest) {
                    return size() > 24;
                }
            };

    /** 通知小图标：128/160/192 都行，内容按 1.6 倍 mark 排布。 */
    static Bitmap icon(Context context, String key, BotState.Look look, boolean failed, int size) {
        return icon(context, key, look, failed, size, -1, 0);
    }

    /**
     * @param morphPercent -1 = 按状态取（待命 0、其余 100）；0..100 = 指定变形进度（做唤醒动画）
     * @param anim         思考时省略号的亮格帧（0..2）
     */
    static Bitmap icon(Context context, String key, BotState.Look look, boolean failed,
                       int size, int morphPercent, int anim) {
        String cacheKey = key + "|" + look + "|" + failed + "|" + size + "|" + morphPercent + "|" + anim;
        Bitmap cached = ICONS.get(cacheKey);
        if (cached != null && !cached.isRecycled()) return cached;

        BotForm form = BotForm.get(context, key, false);
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        // 身体占画布的六成左右，比 1.6 倍留白那版大一圈；再按实际内容居中。
        float px = size / 1.25f;
        BotState.Sign sign = BotState.sign(look, failed);
        RectF content = content(form, px, sign,
                look == BotState.Look.IDLE || morphPercent >= 0, true);
        // 岛上图标本来就小：把内容铺满画布，但永远给头顶标识留位置——
        // 这样出现/收起 ! ? ≡ 时，身体不会忽大忽小被「切」一下。
        float scale = Math.min(size * 0.94f / content.width(),
                size * 0.94f / content.height());
        canvas.translate((size - content.width() * scale) / 2 - content.left * scale,
                (size - content.height() * scale) / 2 - content.top * scale);
        canvas.scale(scale, scale);
        // 待命就是「刚醒来」：只显示 logo；一干活才变成 bot。
        float morph = look == BotState.Look.IDLE ? 0f
                : morphPercent >= 0 ? Math.max(0, Math.min(1, morphPercent / 100f)) : 1f;
        int lit = look == BotState.Look.THINKING ? Math.abs(anim) % 3 : 0;
        Face face = faceFor(look, failed);
        Pose pose = null;
        if (look == BotState.Look.THINKING) {
            // 小图标也要看得出在动：眼睛左右瞟 + 整个身体轻轻上下浮。
            face.look = -0.55f + (lit - 1) * 0.22f;
            pose = new Pose();
            pose.y = lit == 1 ? -0.018f : lit == 2 ? 0.018f : 0;
        }
        draw(canvas, form, px, face, morph, 0f, sign, lit, 1f, pose);
        ICONS.put(cacheKey, bitmap);
        return bitmap;
    }

    /** 内容（身体 + 头顶标识）在画布像素里的包围盒，用来把 bot 放大并居中。 */
    static RectF content(BotForm form, float px, BotState.Sign sign, boolean includeLogo) {
        return content(form, px, sign, includeLogo, sign != null);
    }

    static RectF content(BotForm form, float px, BotState.Sign sign, boolean includeLogo,
                         boolean reserveSign) {
        RectF box = new RectF(Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE);
        float cx = form.spec.center[0], cy = form.spec.center[1];
        if (form.spec.ownEyes != null) {
            box.set(0, 0, px, px);
        } else {
            for (int i = 0; i < 360; i++) {
                double a = Math.toRadians(i);
                float x = (MARGIN + cx + (float) Math.cos(a) * form.body[i]) * px;
                float y = (MARGIN + cy + (float) Math.sin(a) * form.body[i] + form.spec.settle) * px;
                box.left = Math.min(box.left, x);
                box.right = Math.max(box.right, x);
                box.top = Math.min(box.top, y);
                box.bottom = Math.max(box.bottom, y);
                if (includeLogo) {
                    float lx = (MARGIN + cx + (float) Math.cos(a) * form.logo[i]) * px;
                    float ly = (MARGIN + cy + (float) Math.sin(a) * form.logo[i]) * px;
                    box.left = Math.min(box.left, lx);
                    box.right = Math.max(box.right, lx);
                    box.top = Math.min(box.top, ly);
                    box.bottom = Math.max(box.bottom, ly);
                }
            }
        }
        if (sign != null || reserveSign) {
            RectF placed = signBox(form, px);
            box.union(placed);
        }
        return box;
    }

    static void draw(Canvas canvas, BotForm form, float px, Face face,
                     float morph, float turn, BotState.Sign sign) {
        draw(canvas, form, px, face, morph, turn, sign, 0, 1f);
    }

    /**
     * @param px     mark 的边长（像素）；画布至少 1.6*px
     * @param lit    ellipsis 亮起的那一格（0..2）
     * @param alpha  标识整体透明度（ellipsis / z 随 morph 淡入）
     */
    static void draw(Canvas canvas, BotForm form, float px, Face face,
                     float morph, float turn, BotState.Sign sign, int lit, float alpha) {
        draw(canvas, form, px, face, morph, turn, sign, lit, alpha, null);
    }

    static void draw(Canvas canvas, BotForm form, float px, Face face,
                     float morph, float turn, BotState.Sign sign, int lit, float alpha,
                     Pose pose) {
        canvas.save();
        canvas.translate(MARGIN * px, MARGIN * px);
        canvas.scale(px, px);
        if (pose != null) {
            canvas.translate(0.5f + pose.x, 0.5f + pose.y);
            canvas.rotate(pose.rotation);
            canvas.scale(pose.scaleX, pose.scaleY);
            canvas.translate(-0.5f, -0.5f);
        }
        body(canvas, form, face, morph, turn);
        canvas.restore();
        if (sign != null) paintSign(canvas, form, px, sign, lit, alpha);
    }

    private static float ease(float value) {
        return value < 0.5f ? 2 * value * value : 1 - (float) Math.pow(-2 * value + 2, 2) / 2;
    }

    private static void body(Canvas c, BotForm form, Face face, float morph, float turn) {
        BotSpecs.Spec s = form.spec;
        float share = Math.max(0, Math.min(1, morph));
        float eased = ease(share);
        float solid = Math.min(1, share / 0.22f);
        float dressed = Math.max(0, Math.min(1, (share - 0.45f) / 0.45f));

        if (s.ownEyes != null) {
            Paint art = new Paint(Paint.FILTER_BITMAP_FLAG);
            c.drawBitmap(form.artwork, null, new RectF(0, 0, 1, 1), art);
            if (share > 0) {
                Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
                p.setAlpha(Math.round(255 * share));
                ownEyes(c, s.ownEyes, face, INK, p);
            }
            return;
        }

        float artwork = 1 - Math.min(1, share / 0.4f);
        if (artwork > 0.001f) {
            Paint p = new Paint(Paint.FILTER_BITMAP_FLAG);
            p.setAlpha(Math.round(255 * artwork));
            c.drawBitmap(form.artwork, null, new RectF(0, 0, 1, 1), p);
        }

        c.save();
        c.translate(0, s.settle * eased);
        Path outline = form.outline(eased, turn);
        float ringShare = s.ring != null ? Math.max(0, Math.min(1, (share - 0.5f) / 0.5f)) : 0;

        if (s.ring != null && ringShare > 0) {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(s.ring.width * s.radius);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setColor(form.ink);
            p.setAlpha(Math.round(255 * ringShare));
            c.drawPath(ring(s, ringShare, false), p);
        }

        // 鳍和触须在身体后面
        if (dressed > 0 && s.decor != null) {
            for (BotSpecs.Decor item : s.decor) {
                if (item.kind != 5 && item.kind != 6) continue;
                c.save();
                c.translate(s.center[0], s.center[1]);
                c.scale(s.radius, s.radius);
                Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
                p.setAlpha(Math.round(255 * dressed));
                if (item.kind == 6) {
                    p.setStyle(Paint.Style.STROKE);
                    p.setColor(Color.parseColor(item.color));
                    p.setStrokeWidth(item.width);
                    p.setStrokeJoin(Paint.Join.ROUND);
                    p.setStrokeCap(Paint.Cap.ROUND);
                    Path path = new Path();
                    for (float[][] line : item.lines) {
                        for (int i = 0; i < line.length; i++) {
                            if (i == 0) path.moveTo(line[0][0], line[0][1]);
                            else path.lineTo(line[i][0], line[i][1]);
                        }
                    }
                    c.drawPath(path, p);
                } else {
                    p.setColor(Color.parseColor(item.color));
                    float grown = 0.3f + 0.7f * dressed;
                    Path path = new Path();
                    for (BotSpecs.Fin fin : item.fins) path.addPath(finPath(fin, grown, 0));
                    c.drawPath(path, p);
                }
                c.restore();
            }
        }

        c.save();
        c.clipPath(outline);
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        fill.setAlpha(Math.round(255 * solid));
        fillPaint(c, form, s.paint, fill, 0, 0);
        if (s.patch != null) patch(c, form, s.patch, ease(share), share);
        if (s.stroke != null) {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(0.07f);
            p.setStrokeJoin(Paint.Join.ROUND);
            p.setColor(Color.parseColor(s.stroke));
            c.drawPath(outline, p);
        }
        c.restore();

        if (s.ring != null && ringShare > 0) {
            c.save();
            c.clipPath(outline);
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(s.ring.width * s.radius);
            p.setColor(form.light ? 0xFFF5F5F7 : INK);
            p.setAlpha(Math.round(255 * ringShare));
            c.drawPath(ring(s, ringShare, true), p);
            c.restore();
        }

        if (dressed > 0 && s.decor != null) {
            for (BotSpecs.Decor item : s.decor) {
                if (item.kind == 5 || item.kind == 6) continue;
                if (item.kind == 1) {
                    wear(c, form, outline, item, dressed);
                } else {
                    decorate(c, item, s, dressed);
                }
            }
        }

        if (dressed > 0 && s.limbs != null) limbs(c, form, dressed);
        if (dressed > 0) eyes(c, s, face, form.ink);
        c.restore();
    }

    // ---- 填充 ----

    private static void fillPaint(Canvas c, BotForm form, BotSpecs.Paint paint, Paint p,
                                  float ox, float oy) {
        if (paint == null) {
            if (form.field != null) {
                form.drawField(c, p);
            } else {
                int alpha = p.getAlpha();
                p.setShader(null);
                p.setColor(form.ink);
                p.setAlpha(alpha);
                c.drawRect(-0.3f, -0.3f, 1.3f, 1.3f, p);
            }
            return;
        }
        BotSpecs.Spec s = form.spec;
        if (paint.color != null) {
            p.setShader(null);
            p.setColor(Color.parseColor(paint.color));
        } else if (paint.colors != null && paint.colors.length >= 2) {
            float r = s.radius;
            if (paint.radial) {
                float[] at = at(s, paint.center, ox, oy);
                float outer = paint.outer * 2 * r;
                float start = outer > 0 ? Math.max(0, Math.min(1, paint.inner / paint.outer)) : 0;
                int n = paint.colors.length;
                float[] stops = new float[n];
                for (int i = 0; i < n; i++) stops[i] = start + (1 - start) * i / (n - 1f);
                p.setShader(new RadialGradient(at[0], at[1], outer,
                        ints(paint.colors), stops, Shader.TileMode.CLAMP));
            } else {
                float[] from = at(s, paint.from, ox, oy), to = at(s, paint.to, ox, oy);
                p.setShader(new LinearGradient(from[0], from[1], to[0], to[1],
                        ints(paint.colors), null, Shader.TileMode.CLAMP));
            }
        }
        c.drawRect(-0.3f, -0.3f, 1.3f, 1.3f, p);
    }

    private static float[] at(BotSpecs.Spec s, float[] point, float ox, float oy) {
        return new float[]{ox + s.center[0] + (point[0] - 0.5f) * 2 * s.radius,
                oy + s.center[1] + (point[1] - 0.5f) * 2 * s.radius};
    }

    private static int[] ints(String[] colors) {
        int[] out = new int[colors.length];
        for (int i = 0; i < colors.length; i++) out[i] = Color.parseColor(colors[i]);
        return out;
    }

    private static void patch(Canvas c, BotForm form, BotSpecs.Patch patch, float eased, float share) {
        BotSpecs.Spec s = form.spec;
        float[] b = patch.box;
        float width = (b[2] - b[0]) * s.radius, height = (b[3] - b[1]) * s.radius;
        float px = s.center[0] + (b[0] + b[2]) / 2 * s.radius;
        float py = s.center[1] + (b[1] + b[3]) / 2 * s.radius;
        c.save();
        c.translate(px, py);
        float scale = 0.4f + 0.6f * eased;
        c.scale(scale, scale);
        Path clip = new Path();
        float corner = Math.min(patch.round * s.radius, Math.min(width, height) / 2);
        clip.addRoundRect(new RectF(-width / 2, -height / 2, width / 2, height / 2),
                corner, corner, Path.Direction.CW);
        c.clipPath(clip);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        p.setAlpha(Math.round(255 * Math.max(0, Math.min(1, (share - 0.2f) / 0.4f))));
        fillPaint(c, form, patch.paint, p, -px, -py);
        c.restore();
    }

    private static void wear(Canvas c, BotForm form, Path outline,
                             BotSpecs.Decor item, float strength) {
        BotSpecs.Spec s = form.spec;
        c.save();
        c.clipPath(outline);
        c.translate(s.center[0], s.center[1]);
        c.scale(s.radius, s.radius);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setAlpha(Math.round(255 * strength));
        p.setColor(Color.parseColor(item.color));
        c.drawPath(boxesPath(item.boxes, item.round), p);
        c.restore();
    }

    private static void decorate(Canvas c, BotSpecs.Decor item, BotSpecs.Spec s, float strength) {
        c.save();
        c.translate(s.center[0], s.center[1]);
        c.scale(s.radius, s.radius);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setAlpha(Math.round(255 * strength));
        if (item.kind == 0) {
            p.setColor(Color.parseColor(item.color));
            c.drawPath(boxesPath(item.boxes, item.round), p);
        } else if (item.kind == 2 || item.kind == 3) {
            p.setColor(item.color != null ? Color.parseColor(item.color) : 0xFF000000);
            p.setXfermode(new android.graphics.PorterDuffXfermode(android.graphics.PorterDuff.Mode.DST_OUT));
            c.drawPath(pointsPath(item.points, true), p);
        } else if (item.kind == 4) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(item.width);
            p.setStrokeJoin(Paint.Join.ROUND);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setColor(INK);
            c.drawPath(pointsPath(item.points, false), p);
        }
        c.restore();
    }

    private static Path pointsPath(float[][] points, boolean close) {
        Path p = new Path();
        for (int i = 0; i < points.length; i++) {
            if (i == 0) p.moveTo(points[0][0], points[0][1]);
            else p.lineTo(points[i][0], points[i][1]);
        }
        if (close) p.close();
        return p;
    }

    private static Path boxesPath(float[][] boxes, float round) {
        Path p = new Path();
        for (float[] b : boxes) {
            float w = b[2] - b[0], h = b[3] - b[1];
            float corner = (Math.min(w, h) / 2) * round;
            p.addRoundRect(new RectF(b[0], b[1], b[2], b[3]), corner, corner, Path.Direction.CW);
        }
        return p;
    }

    // ---- 鳍 / 环 ----

    private static Path finPath(BotSpecs.Fin fin, float grown, float raised) {
        double angle = Math.toRadians(fin.angle - fin.hand * raised);
        float[] along = {(float) Math.cos(angle), (float) Math.sin(angle)};
        float[] across = {(float) Math.sin(angle), -(float) Math.cos(angle)};
        float length = fin.length * grown;
        float[] a = fin.root;
        float ra = fin.base * grown, rb = fin.tip * grown;
        float[] b = {a[0] + along[0] * (length - rb) + across[0] * fin.sweep * grown,
                a[1] + along[1] * (length - rb) + across[1] * fin.sweep * grown};
        float dx = b[0] - a[0], dy = b[1] - a[1];
        float d = Math.max(1e-6f, (float) Math.hypot(dx, dy));
        float sv = Math.max(-1, Math.min(1, (ra - rb) / d));
        float cv = (float) Math.sqrt(1 - sv * sv);
        double toward = Math.atan2(dy, dx), spread = Math.atan2(cv, sv);
        Path path = new Path();
        lineArc(path, b, rb, toward - spread, toward + spread);
        lineArc(path, a, ra, toward + spread, toward + 2 * Math.PI - spread);
        path.close();
        return path;
    }

    private static void lineArc(Path path, float[] center, float radius, double start, double end) {
        int count = Math.max(2, (int) Math.ceil((end - start) / (Math.PI / 16)));
        for (int i = 0; i <= count; i++) {
            double a = start + (end - start) * i / count;
            float x = center[0] + radius * (float) Math.cos(a);
            float y = center[1] + radius * (float) Math.sin(a);
            if (i == 0) path.moveTo(x, y);
            else path.lineTo(x, y);
        }
    }

    private static Path ring(BotSpecs.Spec s, float grown, boolean near) {
        BotSpecs.Ring ring = s.ring;
        Path p = new Path();
        float r = s.radius, size = 0.5f + 0.5f * grown;
        double tilt = Math.toRadians(ring.tilt);
        double sweep = near ? Math.PI : 2 * Math.PI;
        for (int step = 0; step <= 48; step++) {
            double a = sweep * step / 48;
            float x = ring.radiusX * r * size * (float) Math.cos(a);
            float y = ring.radiusY * r * size * (float) Math.sin(a);
            float px = s.center[0] + x * (float) Math.cos(tilt) - y * (float) Math.sin(tilt);
            float py = s.center[1] + ring.drop * r + x * (float) Math.sin(tilt) + y * (float) Math.cos(tilt);
            if (step == 0) p.moveTo(px, py);
            else p.lineTo(px, py);
        }
        if (!near) p.close();
        return p;
    }

    // ---- 四肢 ----

    private static void limbs(Canvas c, BotForm form, float strength) {
        BotSpecs.Spec s = form.spec;
        BotSpecs.Limbs limbs = s.limbs;
        Path pieces = new Path();
        for (float[] arm : limbs.arms) {
            pieces.addRect(arm[0], arm[1], arm[2], arm[3], Path.Direction.CW);
        }
        for (float[] leg : limbs.legs) {
            pieces.addRect(leg[0], leg[1], leg[2], leg[3], Path.Direction.CW);
        }
        Matrix matrix = new Matrix();
        matrix.setScale(s.radius, s.radius);
        matrix.postTranslate(s.center[0], s.center[1]);
        Path placed = new Path();
        pieces.transform(matrix, placed);
        c.save();
        c.clipPath(placed);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setAlpha(Math.round(255 * strength));
        if (s.paint != null && s.paint.color != null) p.setColor(Color.parseColor(s.paint.color));
        else p.setColor(form.bodyColor());
        c.drawRect(-0.3f, -0.3f, 1.3f, 1.3f, p);
        c.restore();
    }

    // ---- 眼睛 ----

    private static void eyes(Canvas c, BotSpecs.Spec s, Face face, int ink) {
        float r = s.radius, size = 0.78f, spread = s.eyeSpread;
        boolean flat = !Float.isNaN(s.eyeAcross);
        float grain = s.limbs != null ? s.limbs.pixel / 2 : 0;
        float turn = s.reach * 0.72f;
        float yaw = Math.max(-1.15f, Math.min(1.15f, face.look)) * turn;
        float pitch = Math.max(-1.15f, Math.min(1.15f, face.lookY)) * turn;
        float apart = flat ? 2 * s.eyeAcross * r
                : ((float) Math.sin(yaw + spread) - (float) Math.sin(yaw - spread)) * r * 0.76f;
        int color = s.eyeColor != null ? Color.parseColor(s.eyeColor) : INK;
        for (int side = -1; side <= 1; side += 2) {
            float angle = flat ? 0 : yaw + side * spread;
            float x = s.center[0] + s.face[0] * r
                    + (flat ? side * s.eyeAcross * r + snap((float) Math.sin(yaw) * 0.76f, grain) * r
                    : (float) Math.sin(angle) * r * 0.76f);
            float y = s.center[1] + s.face[1] * r
                    + (flat ? snap((float) Math.sin(pitch) * 0.62f, grain)
                    : (float) Math.sin(pitch) * 0.62f) * r;
            float narrow = (float) Math.pow(Math.max(0.25f, (float) Math.cos(angle)), 1.4);
            float tall = (0.72f + 0.28f * (float) Math.cos(angle))
                    * (float) Math.sqrt(Math.max(0, Math.cos(pitch)));
            float big = (1 + 0.26f * side * face.lean) * face.wide
                    * (0.6f + 0.4f * face.open) * size;
            float width = s.eyeWidth * r * big * narrow * EYE_W;
            float height = s.eyeHeight * r * big * tall
                    * Math.max(0.08f, face.open) * EYE_H;
            c.save();
            c.translate(x, y);
            if (face.happy < 0.98f && face.crossed < 0.98f) {
                Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
                p.setColor(color);
                p.setAlpha(Math.round(255 * (1 - face.happy) * (1 - face.crossed)));
                c.drawPath(pill(width, Math.max(height, width * 0.16f), s.eyeRound), p);
                if (s.pupil != null && height > width * 0.45f) {
                    Paint pupil = new Paint(Paint.ANTI_ALIAS_FLAG);
                    pupil.setColor(Color.parseColor(s.pupil));
                    float across = width * 0.21f, down = Math.min(height, width) * 0.21f;
                    c.drawOval(new RectF(face.look * width * 0.2f - across,
                            face.lookY * height * 0.2f - height * 0.06f - down,
                            face.look * width * 0.2f + across,
                            face.lookY * height * 0.2f - height * 0.06f + down), pupil);
                }
            }
            if (face.crossed > 0.02f) {
                float reach = s.eyeHeight * r * size * 0.32f * (0.6f + 0.4f * face.open);
                Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(s.eyeWidth * r * size * 0.55f);
                p.setStrokeCap(Paint.Cap.ROUND);
                p.setColor(color);
                p.setAlpha(Math.round(255 * face.crossed));
                Path cross = new Path();
                cross.moveTo(-reach, -reach); cross.lineTo(reach, reach);
                cross.moveTo(-reach, reach); cross.lineTo(reach, -reach);
                c.drawPath(cross, p);
            }
            if (face.happy > 0.02f) {
                float stroke = Math.min(s.eyeWidth * r * 0.58f * size, apart * 0.2f);
                float half = Math.max(stroke * 0.7f,
                        Math.min(s.eyeWidth * r * 0.8f * size, apart / 2 - stroke * 1.05f))
                        * Math.max(0.6f, narrow);
                smile(c, half, stroke, color, face.happy);
            }
            c.restore();
        }
    }

    private static float snap(float value, float grain) {
        return grain > 0 ? Math.round(value / grain) * grain : value;
    }

    private static Path pill(float width, float height, float round) {
        Path p = new Path();
        float corner = (Math.min(width, height) / 2) * round;
        if (corner > 0.0005f) {
            p.addRoundRect(new RectF(-width / 2, -height / 2, width / 2, height / 2),
                    corner, corner, Path.Direction.CW);
        } else {
            p.addRect(-width / 2, -height / 2, width / 2, height / 2, Path.Direction.CW);
        }
        return p;
    }

    private static void smile(Canvas c, float half, float stroke, int color, float strength) {
        float rise = half * 0.95f;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(stroke);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setColor(color);
        p.setAlpha(Math.round(255 * strength));
        Path path = new Path();
        path.moveTo(-half, rise * 0.45f);
        path.quadTo(0, -rise * 1.1f, half, rise * 0.45f);
        c.drawPath(path, p);
    }

    private static void ownEyes(Canvas c, float[][] boxes, Face face, int ink, Paint p) {
        float middle = (center(boxes[0]) + center(boxes[boxes.length - 1])) / 2;
        float apart = Math.abs(center(boxes[boxes.length - 1]) - center(boxes[0]));
        for (float[] b : boxes) {
            float side = center(b) > middle ? 1 : -1;
            float big = (1 + 0.26f * side * face.lean) * face.wide;
            float width = (b[2] - b[0]) * big;
            float height = (b[3] - b[1]) * big * Math.max(0.06f, face.open);
            c.save();
            c.translate(center(b) + Math.min(0.04f, (b[2] - b[0]) * 0.45f) * face.look,
                    (b[1] + b[3]) / 2 + Math.min(0.03f, (b[3] - b[1]) * 0.14f) * face.lookY);
            if (face.happy < 0.98f) {
                Paint eye = new Paint(Paint.ANTI_ALIAS_FLAG);
                eye.setColor(ink);
                eye.setAlpha(Math.round(255 * (1 - face.happy)));
                c.drawPath(pill(width, Math.max(height, width * 0.16f), 1), eye);
            }
            if (face.happy > 0.02f) {
                smile(c, Math.min((b[2] - b[0]) * 0.95f, apart * 0.3f),
                        Math.min((b[2] - b[0]) * 0.6f, apart * 0.2f), ink, face.happy);
            }
            c.restore();
        }
    }

    private static float center(float[] b) {
        return (b[0] + b[2]) / 2;
    }

    // ---- 头顶标识 ----

    static float signCell(float side) {
        return Math.max(1, Math.min(Math.round(side / 20f), Math.round(0.3f * side / 7f)));
    }

    private static RectF placed(float left, float bottom, float ceiling, float cell, float right) {
        float width = 5 * cell, height = 7 * cell;
        bottom = Math.max(Math.round(bottom), (float) Math.ceil(ceiling) + height);
        left = Math.max(1, Math.min(Math.round(left), right - 1 - width));
        return new RectF(left, bottom - height, left + width, bottom);
    }

    private static RectF signBox(BotForm form, float px) {
        float inner = (float) Math.ceil(px * 1.6f);
        float unit = inner / 1.6f;
        float[] foot = form.spec.signAt != null ? form.spec.signAt
                : new float[]{form.spec.center[0] + 0.14f, form.top - 0.04f};
        return placed((foot[0] + 0.3f) * unit, (foot[1] + 0.3f) * unit,
                (float) Math.ceil((-0.28f + 0.3f) * unit), signCell(px), inner);
    }

    private static void paintSign(Canvas c, BotForm form, float px, BotState.Sign sign,
                                  int lit, float alpha) {
        RectF rect = signBox(form, px);
        int cell = Math.max(1, Math.round(rect.width() / 5));
        float left = Math.round(rect.left), top = Math.round(rect.top);
        java.util.List<RectF> bright = new java.util.ArrayList<>();
        java.util.List<RectF> dim = new java.util.ArrayList<>();
        lit = Math.max(0, Math.min(2, lit));
        String[] rows = BotState.SIGN_ROWS[sign.ordinal()];
        for (int y = 0; y < 7; y++) {
            for (int x = 0; x < 5; x++) {
                if (rows[y].charAt(x) != '#') continue;
                RectF r = new RectF(left + x * cell, top + y * cell,
                        left + (x + 1) * cell, top + (y + 1) * cell);
                if (sign == BotState.Sign.ELLIPSIS && x != 2 * lit) dim.add(r);
                else bright.add(r);
            }
        }
        c.save();
        if (!form.light) {
            Paint ringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            Path halo = new Path();
            for (RectF r : dim) halo.addRect(r.left - 1, r.top - 1, r.right + 1, r.bottom + 1, Path.Direction.CW);
            for (RectF r : dim) halo.addRect(r.left, r.top, r.right, r.bottom, Path.Direction.CW);
            halo.setFillType(Path.FillType.EVEN_ODD);
            ringPaint.setColor(INK);
            ringPaint.setAlpha(Math.round(255 * 0.21f * alpha));
            c.drawPath(halo, ringPaint);

            Path brightHalo = new Path();
            for (RectF r : bright) brightHalo.addRect(r.left - 1, r.top - 1, r.right + 1, r.bottom + 1, Path.Direction.CW);
            ringPaint.setAlpha(Math.round(255 * 0.6f * alpha));
            c.drawPath(brightHalo, ringPaint);
        }
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(form.light ? 0xFF0D0D0F : 0xFFFFFFFF);
        p.setAlpha(Math.round(255 * alpha));
        Path path = new Path();
        for (RectF r : bright) path.addRect(r, Path.Direction.CW);
        c.drawPath(path, p);
        if (!dim.isEmpty()) {
            p.setAlpha(Math.round(255 * 0.20f * alpha));
            Path faint = new Path();
            for (RectF r : dim) faint.addRect(r, Path.Direction.CW);
            c.drawPath(faint, p);
        }
        c.restore();
    }
}
