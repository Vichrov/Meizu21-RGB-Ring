package com.vichrov.meizu21rgbring;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/** Lightweight 16-segment preview of the physical Meizu camera ring. */
public final class RingPreviewView extends View {
    public static final int MODE_STATIC = 0;
    public static final int MODE_RAINBOW = 1;
    public static final int MODE_MUSIC = 2;

    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lamp = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private int color = 0xFFFF5757;
    private int trackColor = 0x22000000;
    private int brightness = 255;
    private int mode = MODE_STATIC;
    private float phase;
    private boolean animate;

    public RingPreviewView(Context context) { super(context); init(); }
    public RingPreviewView(Context context, AttributeSet attrs) { super(context, attrs); init(); }
    public RingPreviewView(Context context, AttributeSet attrs, int defStyleAttr) { super(context, attrs, defStyleAttr); init(); }

    private void init() {
        track.setStyle(Paint.Style.STROKE);
        track.setStrokeCap(Paint.Cap.ROUND);
        lamp.setStyle(Paint.Style.STROKE);
        lamp.setStrokeCap(Paint.Cap.ROUND);
        setContentDescription("Предпросмотр RGB-кольца");
    }

    public void setTrackColor(int value) {
        trackColor = value;
        invalidate();
    }

    public void setRingColor(int argb, int softwareBrightness) {
        color = argb;
        brightness = Math.max(0, Math.min(255, softwareBrightness));
        invalidate();
    }

    public void setMode(int value) {
        mode = value;
        animate = mode != MODE_STATIC;
        if (animate) postOnAnimation(tick);
        invalidate();
    }

    @Override protected void onDetachedFromWindow() {
        animate = false;
        removeCallbacks(tick);
        super.onDetachedFromWindow();
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (!animate || !isAttachedToWindow()) return;
            phase += mode == MODE_MUSIC ? 5.5f : 2.2f;
            if (phase >= 360f) phase -= 360f;
            invalidate();
            postOnAnimationDelayed(this, 34);
        }
    };

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        float size = Math.min(w, h);
        float stroke = Math.max(dp(14), size * 0.105f);
        float inset = stroke * 0.75f + dp(4);
        oval.set((w - size) / 2f + inset, (h - size) / 2f + inset,
                (w + size) / 2f - inset, (h + size) / 2f - inset);
        track.setStrokeWidth(stroke);
        lamp.setStrokeWidth(stroke);

        float step = 360f / 16f;
        float gap = 4.3f;
        float sweep = step - gap;
        float pulse = mode == MODE_MUSIC ? (0.62f + 0.38f * (float)((Math.sin(Math.toRadians(phase * 2.6f)) + 1.0) / 2.0)) : 1f;

        for (int i = 0; i < 16; i++) {
            float start = -90f + i * step + gap / 2f;
            track.setColor(trackColor);
            canvas.drawArc(oval, start, sweep, false, track);

            int c;
            if (mode == MODE_STATIC) {
                c = scale(color, brightness / 255f);
            } else {
                float hue = (phase + i * 360f / 16f) % 360f;
                c = Color.HSVToColor(new float[]{hue, 0.88f, Math.max(0.18f, pulse)});
            }
            lamp.setColor(c);
            canvas.drawArc(oval, start, sweep, false, lamp);
        }
    }

    private static int scale(int argb, float factor) {
        factor = Math.max(0f, Math.min(1f, factor));
        int r = Math.round(Color.red(argb) * factor);
        int g = Math.round(Color.green(argb) * factor);
        int b = Math.round(Color.blue(argb) * factor);
        return Color.rgb(r, g, b);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
