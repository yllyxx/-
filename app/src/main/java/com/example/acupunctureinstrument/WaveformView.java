package com.example.acupunctureinstrument;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;
import android.os.Handler;
import android.os.Looper;

public class WaveformView extends View {
    private Paint paint;
    private Path path;
    private int waveformType = 0; // 0: 连续波, 1: 断续波, 2: 疏密波

    // 波形参数
    private static final float WAVE_AMPLITUDE = 0.7f; // 波形振幅（相对于视图高度的比例）
    private static final int VISIBLE_CYCLES = 12; // 可见的周期数
    private static final int TOTAL_PATTERN_CYCLES = 12; // 完整模式的周期数（断续波和疏密波的完整模式）

    // 动画相关
    private float animationOffset = 0; // 动画偏移量（0-1之间）
    private Handler animationHandler;
    private Runnable animationRunnable;
    private static final int ANIMATION_DELAY = 15; // 动画更新间隔（毫秒）
    private static final float ANIMATION_SPEED = 0.0025f; // 动画速度（每帧移动的比例）
    private boolean isAnimating = false;

    /**
     * 波形显示说明：
     * 连续波：连续的三角波
     * 断续波：模式为[4个三角波周期 + 2个横线周期 + 4个三角波周期 + 2个横线周期]，循环滚动
     * 疏密波：模式为[4个正常频率 + 2个高频 + 4个正常频率 + 2个高频]，循环滚动
     */

    public WaveformView(Context context) {
        super(context);
        init();
    }

    public WaveformView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public WaveformView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        paint = new Paint();
        paint.setColor(Color.parseColor("#4CAF50")); // 绿色
        paint.setStrokeWidth(3f); // 稍微粗一点，让三角波更清晰
        paint.setStyle(Paint.Style.STROKE);
        paint.setAntiAlias(true);

        path = new Path();

        // 初始化动画
        animationHandler = new Handler(Looper.getMainLooper());
        startAnimation();
    }

    public void setWaveformType(int type) {
        this.waveformType = type;
        invalidate();
    }

    private void startAnimation() {
        if (!isAnimating) {
            isAnimating = true;
            animationRunnable = new Runnable() {
                @Override
                public void run() {
                    if (isAnimating) {
                        // 更新动画偏移量
                        animationOffset += ANIMATION_SPEED;
                        if (animationOffset >= 1.0f) {
                            animationOffset -= 1.0f; // 循环
                        }

                        // 重绘视图
                        invalidate();

                        // 继续动画
                        animationHandler.postDelayed(this, ANIMATION_DELAY);
                    }
                }
            };
            animationHandler.post(animationRunnable);
        }
    }

    private void stopAnimation() {
        isAnimating = false;
        if (animationHandler != null && animationRunnable != null) {
            animationHandler.removeCallbacks(animationRunnable);
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startAnimation();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopAnimation();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int width = getWidth();
        int height = getHeight();
        int centerY = height / 2;

        // 清空路径
        path.reset();

        // 根据波形类型绘制
        switch (waveformType) {
            case 0: // 连续波
                drawContinuousWave(canvas, width, height, centerY);
                break;
            case 1: // 断续波
                drawIntermittentWave(canvas, width, height, centerY);
                break;
            case 2: // 疏密波
                drawSparseWave(canvas, width, height, centerY);
                break;
        }
    }

    private void drawContinuousWave(Canvas canvas, int width, int height, int centerY) {
        // 绘制连续的三角波
        float cycleWidth = (float) width / VISIBLE_CYCLES;
        float amplitude = height * WAVE_AMPLITUDE / 2;

        // 修改：让连续波也按完整模式宽度来计算偏移
        float patternWidth = cycleWidth * TOTAL_PATTERN_CYCLES;
        float offsetPixels = animationOffset * patternWidth;

        // 为了实现无缝循环，需要绘制更多周期
        int totalCyclesToDraw = TOTAL_PATTERN_CYCLES * 2 + 2;

        for (int cycle = -1; cycle < totalCyclesToDraw; cycle++) {
            float startX = cycle * cycleWidth - offsetPixels;

            // 三角波的关键点
            float x1 = startX;
            float x2 = startX + cycleWidth * 0.25f;
            float x3 = startX + cycleWidth * 0.5f;
            float x4 = startX + cycleWidth * 0.75f;
            float x5 = startX + cycleWidth;

            if (cycle == -1) {
                path.moveTo(x1, centerY);
            }

            path.lineTo(x2, centerY - amplitude);
            path.lineTo(x3, centerY);
            path.lineTo(x4, centerY + amplitude);
            path.lineTo(x5, centerY);
        }

        // 裁剪只显示可见区域
        canvas.clipRect(0, 0, width, height);
        canvas.drawPath(path, paint);
    }

    private void drawIntermittentWave(Canvas canvas, int width, int height, int centerY) {
        // 断续波模式：4个三角波 + 2个横线 + 4个三角波 + 2个横线（共12个周期为一个完整模式）
        float cycleWidth = (float) width / VISIBLE_CYCLES;
        float amplitude = height * WAVE_AMPLITUDE / 2;

        // 计算模式偏移（一个完整模式的偏移）
        float patternWidth = cycleWidth * TOTAL_PATTERN_CYCLES;
        float offsetPixels = animationOffset * patternWidth;

        boolean firstPoint = true;

        // 绘制两个完整的模式以确保无缝循环
        for (int patternIndex = 0; patternIndex < 2; patternIndex++) {
            float patternStartX = patternIndex * patternWidth - offsetPixels;

            // 在每个模式内绘制12个周期
            for (int cycle = 0; cycle < TOTAL_PATTERN_CYCLES; cycle++) {
                float startX = patternStartX + cycle * cycleWidth;
                float endX = startX + cycleWidth;

                // 判断是三角波还是横线（0-3:波，4-5:线，6-9:波，10-11:线）
                boolean isWave = (cycle < 4) || (cycle >= 6 && cycle < 10);

                if (isWave) {
                    // 绘制三角波
                    float x1 = startX;
                    float x2 = startX + cycleWidth * 0.25f;
                    float x3 = startX + cycleWidth * 0.5f;
                    float x4 = startX + cycleWidth * 0.75f;
                    float x5 = endX;

                    if (firstPoint) {
                        path.moveTo(x1, centerY);
                        firstPoint = false;
                    } else {
                        path.lineTo(x1, centerY);
                    }

                    path.lineTo(x2, centerY - amplitude);
                    path.lineTo(x3, centerY);
                    path.lineTo(x4, centerY + amplitude);
                    path.lineTo(x5, centerY);
                } else {
                    // 绘制横线
                    if (firstPoint) {
                        path.moveTo(startX, centerY);
                        firstPoint = false;
                    } else {
                        path.lineTo(startX, centerY);
                    }
                    path.lineTo(endX, centerY);
                }
            }
        }

        // 裁剪只显示可见区域
        canvas.clipRect(0, 0, width, height);
        canvas.drawPath(path, paint);
    }

    private void drawSparseWave(Canvas canvas, int width, int height, int centerY) {
        // 疏密波模式：4个正常 + 2个高频 + 4个正常 + 2个高频（共12个周期为一个完整模式）
        float cycleWidth = (float) width / VISIBLE_CYCLES;
        float amplitude = height * WAVE_AMPLITUDE / 2;

        // 计算模式偏移
        float patternWidth = cycleWidth * TOTAL_PATTERN_CYCLES;
        float offsetPixels = animationOffset * patternWidth;

        boolean firstPoint = true;

        // 绘制两个完整的模式以确保无缝循环
        for (int patternIndex = 0; patternIndex < 2; patternIndex++) {
            float patternStartX = patternIndex * patternWidth - offsetPixels;

            // 在每个模式内绘制12个周期
            for (int cycle = 0; cycle < TOTAL_PATTERN_CYCLES; cycle++) {
                float startX = patternStartX + cycle * cycleWidth;
                float endX = startX + cycleWidth;

                // 判断是正常频率还是高频（0-3:正常，4-5:高频，6-9:正常，10-11:高频）
                boolean isHighFreq = (cycle >= 4 && cycle < 6) || (cycle >= 10 && cycle < 12);

                if (isHighFreq) {
                    // 高频三角波（在一个周期内绘制5个小三角波）
                    float miniCycleWidth = cycleWidth / 5;
                    for (int mini = 0; mini < 5; mini++) {
                        float miniStartX = startX + mini * miniCycleWidth;
                        float x1 = miniStartX;
                        float x2 = miniStartX + miniCycleWidth * 0.25f;
                        float x3 = miniStartX + miniCycleWidth * 0.5f;
                        float x4 = miniStartX + miniCycleWidth * 0.75f;
                        float x5 = miniStartX + miniCycleWidth;

                        if (firstPoint) {
                            path.moveTo(x1, centerY);
                            firstPoint = false;
                        } else if (mini == 0) {
                            path.lineTo(x1, centerY);
                        }

                        // 高频波形的振幅稍微小一点
//                        float smallAmplitude = amplitude * 0.7f;
                        path.lineTo(x2, centerY - amplitude);

                        path.lineTo(x3, centerY);
                        path.lineTo(x4, centerY + amplitude);
//                        path.lineTo(x4, centerY + smallAmplitude);
                        path.lineTo(x5, centerY);
                    }
                } else {
                    // 正常频率三角波
                    float x1 = startX;
                    float x2 = startX + cycleWidth * 0.25f;
                    float x3 = startX + cycleWidth * 0.5f;
                    float x4 = startX + cycleWidth * 0.75f;
                    float x5 = endX;

                    if (firstPoint) {
                        path.moveTo(x1, centerY);
                        firstPoint = false;
                    } else {
                        path.lineTo(x1, centerY);
                    }

                    path.lineTo(x2, centerY - amplitude);
                    path.lineTo(x3, centerY);
                    path.lineTo(x4, centerY + amplitude);
                    path.lineTo(x5, centerY);
                }
            }
        }

        // 裁剪只显示可见区域
        canvas.clipRect(0, 0, width, height);
        canvas.drawPath(path, paint);
    }

    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);
        if (visibility == VISIBLE) {
            startAnimation();
        } else {
            stopAnimation();
        }
    }
}