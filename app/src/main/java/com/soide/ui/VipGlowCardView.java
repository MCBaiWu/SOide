package com.soide.ui;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.animation.LinearInterpolator;

import com.google.android.material.card.MaterialCardView;

/**
 * v1.4.6: VIP 风格渐变 + 水平发光闪烁卡片
 * <p>
 * 在 MaterialCardView 之上叠加：
 *  - 三色对角渐变 (高级感)
 *  - 一道斜向高光带循环扫过 (花里胡哨)
 *  - 顶部 / 底部细微内阴影模拟玻璃质感
 */
public class VipGlowCardView extends MaterialCardView {

    private final Paint gradientPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private int colorTop = Color.parseColor("#FF1A6EF0");
    private int colorMid = Color.parseColor("#FF5C8DF5");
    private int colorBot = Color.parseColor("#FF004A9F");
    private int glowColor = Color.parseColor("#80FFFFFF");

    private float glowOffset = -1f;     // 0..1 范围内
    private ValueAnimator glowAnim;
    private boolean glowEnabled = true;
    private int cornerRadiusPx = -1;

    public VipGlowCardView(Context context) { super(context); init(); }
    public VipGlowCardView(Context context, AttributeSet attrs) { super(context, attrs); init(); }
    public VipGlowCardView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr); init();
    }

    private void init() {
        setWillNotDraw(false);
        // VIP 卡片默认色
        setCardBackgroundColor(Color.TRANSPARENT);
        setCardElevation(dp(8));
        setRadius(dp(20));
        setPreventCornerOverlap(true);
        startGlow();
    }

    public VipGlowCardView setColors(int top, int mid, int bottom) {
        this.colorTop = top;
        this.colorMid = mid;
        this.colorBot = bottom;
        invalidate();
        return this;
    }

    public VipGlowCardView setGlowColor(int color) {
        this.glowColor = color;
        return this;
    }

    public VipGlowCardView setGlowEnabled(boolean enabled) {
        this.glowEnabled = enabled;
        if (enabled) startGlow();
        else stopGlow();
        return this;
    }

    public VipGlowCardView setVipCornerRadius(int radiusPx) {
        this.cornerRadiusPx = radiusPx;
        setRadius(radiusPx);
        return this;
    }

    private void startGlow() {
        if (!glowEnabled) return;
        if (glowAnim != null && glowAnim.isRunning()) return;
        glowAnim = ValueAnimator.ofFloat(0f, 1f);
        glowAnim.setDuration(2600);
        glowAnim.setRepeatCount(ValueAnimator.INFINITE);
        glowAnim.setInterpolator(new LinearInterpolator());
        glowAnim.addUpdateListener(a -> {
            glowOffset = (float) a.getAnimatedValue();
            invalidate();
        });
        glowAnim.start();
    }

    private void stopGlow() {
        if (glowAnim != null) {
            glowAnim.cancel();
            glowAnim = null;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopGlow();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (glowEnabled) startGlow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) {
            super.onDraw(canvas);
            return;
        }
        float r = cornerRadiusPx > 0 ? cornerRadiusPx : getRadius();
        rect.set(0, 0, w, h);

        // 1) 三色对角渐变
        gradientPaint.setShader(new LinearGradient(
                0, 0, w, h,
                new int[]{colorTop, colorMid, colorBot},
                new float[]{0f, 0.5f, 1f},
                Shader.TileMode.CLAMP));
        canvas.drawRoundRect(rect, r, r, gradientPaint);

        // 2) 斜向高光带 (shimmer)
        if (glowEnabled) {
            // 高光带宽度 = w 的 60%, 倾斜 25° 沿 X 方向循环
            float bandW = w * 0.6f;
            float startX = -bandW + glowOffset * (w + bandW);
            int glowA = Color.alpha(glowColor);
            int glowRgb = glowColor & 0x00FFFFFF;
            int left = (glowA & 0xFF) << 24 | glowRgb;
            int mid = (Math.min(255, glowA + 80) & 0xFF) << 24 | glowRgb;
            int right = (0 & 0xFF) << 24 | glowRgb;
            glowPaint.setShader(new LinearGradient(
                    startX, 0, startX + bandW, h,
                    new int[]{right, mid, left},
                    new float[]{0f, 0.5f, 1f},
                    Shader.TileMode.CLAMP));
            // 用 path 倾斜 25°
            int save = canvas.save();
            canvas.clipRect(rect);
            canvas.rotate(20, w / 2f, h / 2f);
            canvas.drawRect(startX, -h, startX + bandW, h * 2, glowPaint);
            canvas.restoreToCount(save);
        }

        // 3) 顶部 / 底部细微玻璃边
        strokePaint.setShader(new LinearGradient(
                0, 0, 0, h,
                new int[]{
                        Color.argb(70, 255, 255, 255),
                        Color.argb(0, 255, 255, 255),
                        Color.argb(0, 0, 0, 0),
                        Color.argb(50, 0, 0, 0)
                },
                new float[]{0f, 0.15f, 0.85f, 1f},
                Shader.TileMode.CLAMP));
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(dp(1f));
        canvas.drawRoundRect(rect, r, r, strokePaint);

        super.onDraw(canvas);
    }

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
