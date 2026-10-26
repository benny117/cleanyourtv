package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/**
 * stats_button: a round top-bar button with three rings, CPU (outer), memory used (middle) and storage used
 * (inner). Focused, it widens to the left into a pill and lists the three figures beside the rings. Same 42dp
 * height, background (pill_bg) and focus colours as the other top-bar buttons; MainActivity's stats reader feeds it.
 */
final class StatsButton extends View {

    static final int CPU = 0xFF5AA9FF, MEMORY = 0xFF3DDC84, STORAGE = 0xFFFFB547;
    /** A ring above this share turns red. */
    static final float HIGH = 0.9f;
    private static final int HIGH_COLOR = 0xFFFF6B6B;
    private static final int[] COLORS = {CPU, MEMORY, STORAGE};

    private final float dp;
    private final float collapsed, minExpanded, ringStroke, dotRadius;
    private final float[] radii;
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private final Rect bounds = new Rect();
    private final boolean animate;

    /** Ring shares 0..1 as drawn and as last read; -1 hides a ring (its figure could not be read). */
    private final float[] shown = {-1, -1, -1};
    private final float[] from = new float[3];
    private final float[] target = {-1, -1, -1};
    private final String[] lines = {"", "", ""};
    private ValueAnimator rings;
    /** 0 at rest (a circle), 1 focused (the pill with its three lines). */
    private float open;
    private ValueAnimator opening;

    StatsButton(Context context, Typeface font, boolean animate) {
        super(context);
        this.animate = animate;
        dp = context.getResources().getDisplayMetrics().density;
        collapsed = 42 * dp;
        minExpanded = 145 * dp;
        ringStroke = 2.75f * dp;
        dotRadius = 2.25f * dp;
        radii = new float[]{16.5f * dp, 12.5f * dp, 8.5f * dp};
        track.setStyle(Paint.Style.STROKE);
        track.setStrokeWidth(ringStroke);
        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeWidth(ringStroke);
        arc.setStrokeCap(Paint.Cap.ROUND);
        text.setTextSize(7.5f * context.getResources().getDisplayMetrics().scaledDensity);
        if (font != null) text.setTypeface(font);
        setBackgroundResource(R.drawable.pill_bg);
        setFocusable(true);
        setClickable(true);
    }

    /** New readings: shares 0..1, -1 for one that could not be read, and the three lines shown when focused. */
    void setValues(float cpu, float memory, float storage, String cpuLine, String memoryLine, String storageLine) {
        lines[0] = cpuLine;
        lines[1] = memoryLine;
        lines[2] = storageLine;
        float[] next = {cpu, memory, storage};
        boolean changed = false;
        for (int i = 0; i < 3; i++) {
            if (next[i] != target[i]) changed = true;
            target[i] = next[i];
        }
        if (open > 0) requestLayout();   // a longer line can widen the pill
        if (!changed) {
            invalidate();
            return;
        }
        if (rings != null) rings.cancel();
        for (int i = 0; i < 3; i++) from[i] = shown[i] < 0 ? 0 : shown[i];
        if (!animate) {
            System.arraycopy(target, 0, shown, 0, 3);
            invalidate();
            return;
        }
        rings = ValueAnimator.ofFloat(0f, 1f).setDuration(400);
        rings.setInterpolator(new DecelerateInterpolator());
        rings.addUpdateListener(a -> {
            float t = (float) a.getAnimatedValue();
            for (int i = 0; i < 3; i++) shown[i] = target[i] < 0 ? -1 : from[i] + (target[i] - from[i]) * t;
            invalidate();
        });
        rings.start();
    }

    @Override
    protected void onFocusChanged(boolean focused, int direction, Rect previous) {
        super.onFocusChanged(focused, direction, previous);
        if (opening != null) opening.cancel();
        float to = focused ? 1f : 0f;
        if (!animate) {
            open = to;
            requestLayout();
            return;
        }
        opening = ValueAnimator.ofFloat(open, to).setDuration(200);
        opening.setInterpolator(new DecelerateInterpolator());
        opening.addUpdateListener(a -> {
            open = (float) a.getAnimatedValue();
            requestLayout();
            invalidate();
        });
        opening.start();
    }

    private float expandedWidth() {
        float widest = 0;
        for (String line : lines) widest = Math.max(widest, text.measureText(line));
        // rings, 7dp, dot, 4dp, the widest line, 12dp
        return Math.max(minExpanded, collapsed + 7 * dp + 2 * dotRadius + 4 * dp + widest + 12 * dp);
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int w = Math.round(collapsed + (expandedWidth() - collapsed) * open);
        setMeasuredDimension(w, Math.round(collapsed));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        boolean focused = isFocused();
        float c = collapsed / 2f;
        track.setColor(focused ? 0x1F000000 : 0x1FFFFFFF);
        for (int i = 0; i < 3; i++) {
            float r = radii[i];
            oval.set(c - r, c - r, c + r, c + r);
            canvas.drawCircle(c, c, r, track);
            float share = shown[i];
            if (share <= 0) continue;   // no arc at 0: a round cap alone would leave a dot
            arc.setColor(target[i] > HIGH ? HIGH_COLOR : COLORS[i]);
            canvas.drawArc(oval, -90f, 360f * Math.min(1f, share), false, arc);
        }
        // The lines fade in once the pill is mostly open.
        float alpha = Math.max(0f, Math.min(1f, (open - 0.4f) / 0.6f));
        if (alpha <= 0f) return;
        text.setColor(focused ? 0xFF111111 : 0xFFFFFFFF);
        text.setAlpha(Math.round(255 * alpha));
        float x = collapsed + 7 * dp;
        float lineHeight = 9 * dp;
        // A figure that could not be read (CPU on some Android 8+ TVs) has no line: centre the ones left.
        int shownLines = 0;
        for (String line : lines) if (!line.isEmpty()) shownLines++;
        float top = c - lineHeight * shownLines / 2f;
        int row = 0;
        for (int i = 0; i < 3; i++) {
            if (lines[i].isEmpty()) continue;
            float mid = top + lineHeight * row++ + lineHeight / 2f;
            dot.setColor(target[i] > HIGH ? HIGH_COLOR : COLORS[i]);
            dot.setAlpha(Math.round(255 * alpha));
            canvas.drawCircle(x + dotRadius, mid, dotRadius, dot);
            String line = lines[i];
            text.getTextBounds("0", 0, 1, bounds);
            canvas.drawText(line, x + 2 * dotRadius + 4 * dp, mid + bounds.height() / 2f, text);
        }
    }
}
