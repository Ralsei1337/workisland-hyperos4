package com.dafeiyu.workisland;

import android.content.Context;
import android.graphics.Canvas;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

import java.util.Random;

/**
 * 应用内的 bot 舞台：同一套 BotPainter 逐帧动给你看。
 * 0.5 秒从 logo 变成 bot、呼吸、眨眼、请求时蹦一下、完成时笑。
 */
public final class BotView extends View {
    private String key = "hud";
    private BotState.Look look = BotState.Look.IDLE;
    private boolean failed;
    private long since = SystemClock.uptimeMillis();
    private long lastFrame = 0;
    private float morph = 0;

    private BotForm form;
    private float px;
    private final Random random = new Random();
    private float nextBlink = 2.4f;
    private float blinkStart = -1;

    public BotView(Context context) {
        super(context);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    public BotView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
    }

    void setBot(String key, BotState.Look look, boolean failed) {
        boolean changed = !lookName().equals(look.name()) || failed != this.failed
                || key == null || !key.equals(this.key);
        this.key = key == null ? "hud" : key;
        this.look = look == null ? BotState.Look.IDLE : look;
        this.failed = failed;
        form = BotForm.get(getContext(), this.key, false);
        if (changed) {
            since = SystemClock.uptimeMillis();
            blinkStart = -1;
            nextBlink = 2.4f;
            invalidate();
        }
    }

    private String lookName() {
        return look == null ? "" : look.name();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        px = Math.min(w, h) / 1.25f;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (form == null) form = BotForm.get(getContext(), key, false);
        if (px <= 0) px = Math.min(getWidth(), getHeight()) / 1.25f;
        long now = SystemClock.uptimeMillis();
        float seconds = (now - since) / 1000f;
        if (lastFrame == 0) lastFrame = now;
        float dt = Math.min(0.1f, (now - lastFrame) / 1000f);
        lastFrame = now;
        // 醒来是 logo；一开工就变成 bot（0.5 秒），回到待命再变回 logo。
        float target = look == BotState.Look.IDLE ? 0f : 1f;
        morph += (target - morph) * Math.min(1, dt / 0.12f);
        if (Math.abs(target - morph) < 0.005f) morph = target;
        float eased = easeInOut(morph);

        BotPainter.Pose pose = new BotPainter.Pose();
        BotPainter.Face face = BotPainter.faceFor(look, failed);
        boolean attention = BotState.isAttention(look);
        boolean done = look == BotState.Look.DONE && !failed;

        if (attention) {
            if (seconds < 0.45f) {
                float x = seconds / 0.45f;
                float arc = (float) Math.sin(Math.PI * x);
                pose.y = -0.26f * 4 * x * (1 - x);
                pose.scaleY = 1 + 0.08f * arc;
                pose.scaleX = 1 - 0.05f * arc;
            }
            float raised = 1 + 0.12f * easeOutBack(Math.min(1, seconds / 0.35f));
            pose.scaleX *= raised;
            pose.scaleY *= raised;
            face.wide = 1.22f;
            if (look == BotState.Look.QUESTION && seconds < 0.4f) {
                face.look = (float) Math.sin(seconds * 6);
            }
        } else if (done) {
            if (seconds < 0.6f) {
                float pop = (float) Math.sin(Math.PI * seconds / 0.6);
                pose.scaleX = pose.scaleY = 1 + 0.2f * pop;
                pose.y = -0.1f * pop;
            }
            if (seconds < 1.25f) face.happy = 1;
        } else {
            float breath = (float) (2 * Math.PI * seconds / 3);
            pose.scaleX = 1 + 0.03f * (float) Math.sin(2 * breath);
            pose.scaleY = 1 - 0.03f * (float) Math.sin(2 * breath);
        }

        if (look == BotState.Look.THINKING && !failed) {
            int side = (int) Math.floor(seconds / 2.5) % 2 == 0 ? 1 : -1;
            face.look = 0.4f * side;
            face.lookY = -0.7f;
            pose.rotation = 5 * Math.max(-1, Math.min(1, face.look / 0.4f));
        } else if (!attention && !done && !failed && look != BotState.Look.ASLEEP) {
            blink(seconds, face);
        }

        if (failed) {
            face.crossed = 1;
        }
        if (look == BotState.Look.ASLEEP) {
            face.open = 0;
        }

        BotState.Sign sign = BotState.sign(look, failed);
        int lit = (int) (seconds * 2.5f) % 3;
        float alpha = sign == BotState.Sign.ELLIPSIS || sign == BotState.Sign.Z ? eased : 1;

        android.graphics.RectF content = BotPainter.content(form, px, sign, morph < 0.98f);
        canvas.save();
        canvas.translate((getWidth() - content.width()) / 2 - content.left,
                (getHeight() - content.height()) / 2 - content.top);
        BotPainter.draw(canvas, form, px, face, morph, 0, sign, lit, alpha, pose);
        canvas.restore();

        if (getWindowVisibility() == VISIBLE && isShown()) postInvalidateOnAnimation();
    }

    private void blink(float seconds, BotPainter.Face face) {
        if (seconds > nextBlink) {
            blinkStart = seconds;
            nextBlink = seconds + 2.2f + random.nextFloat() * 2.3f;
        }
        if (blinkStart >= 0 && seconds - blinkStart < 0.12f) face.open = 0.08f;
    }

    private static float easeInOut(float x) {
        return x < 0.5f ? 2 * x * x : 1 - (float) Math.pow(-2 * x + 2, 2) / 2;
    }

    private static float easeOutBack(float x) {
        return 1 + 2.70158f * (x - 1) * (x - 1) * (x - 1)
                + 1.70158f * (x - 1) * (x - 1);
    }
}
