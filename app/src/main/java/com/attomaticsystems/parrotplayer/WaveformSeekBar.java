package com.attomaticsystems.parrotplayer;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatSeekBar;
import androidx.core.graphics.ColorUtils;

/**
 * Modern AIMP / Poweramp / SoundCloud style Audio Waveform SeekBar.
 * Extends AppCompatSeekBar for drop-in compatibility with SeekBar listeners,
 * while rendering rounded amplitude bars, magnetic scrub expansion, playhead indicator,
 * and A-B loop region markers.
 */
public class WaveformSeekBar extends AppCompatSeekBar {

    private static final int BAR_COUNT = 58;
    private static final int COLOR_UNPLAYED = Color.parseColor("#2A3140");
    private static final int COLOR_AB_UNPLAYED = Color.parseColor("#3B4559");

    private final float[] barAmplitudes = new float[BAR_COUNT];
    private final Paint barPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint playheadPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint abMarkerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF barRect = new RectF();

    private int accentColor = AccentColorHelper.DEFAULT_ACCENT_COLOR;
    private String currentSongKey = "";
    private int abLoopStartMs = -1;
    private int abLoopEndMs = -1;

    private boolean isScrubbing = false;
    private float scrubExpansion = 0f;
    private ValueAnimator scrubAnimator;

    public WaveformSeekBar(@NonNull Context context) {
        super(context);
        init(context);
    }

    public WaveformSeekBar(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public WaveformSeekBar(@NonNull Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        // Hide default platform thumb, track, and touch ripple glow; we render custom Canvas waveform
        setThumb(null);
        setProgressDrawable(null);
        setBackground(null);
        setSplitTrack(false);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            setDefaultFocusHighlightEnabled(false);
        }

        accentColor = AccentColorHelper.getAccentColor(context);
        barPaint.setStyle(Paint.Style.FILL);
        playheadPaint.setStyle(Paint.Style.FILL);
        abMarkerPaint.setStyle(Paint.Style.FILL);

        generateWaveformForKey("default_parrot_waveform");
    }

    /**
     * Generates a deterministic, natural musical waveform profile for the given song path.
     */
    public void setSongKey(@Nullable String songKey) {
        String safeKey = (songKey != null && !songKey.isEmpty()) ? songKey : "default_parrot_waveform";
        if (safeKey.equals(currentSongKey)) return;
        currentSongKey = safeKey;
        generateWaveformForKey(safeKey);
        invalidate();
    }

    public void setAccentColor(int color) {
        if (this.accentColor != color) {
            this.accentColor = color;
            invalidate();
        }
    }

    @Override
    public void setProgressTintList(@Nullable ColorStateList tint) {
        super.setProgressTintList(tint);
        if (tint != null) {
            setAccentColor(tint.getDefaultColor());
        }
    }

    public void setAbLoopRange(int startMs, int endMs) {
        if (this.abLoopStartMs != startMs || this.abLoopEndMs != endMs) {
            this.abLoopStartMs = startMs;
            this.abLoopEndMs = endMs;
            invalidate();
        }
    }

    private void generateWaveformForKey(String key) {
        long hash = 2166136261L;
        for (int i = 0; i < key.length(); i++) {
            hash ^= key.charAt(i);
            hash *= 16777619L;
        }

        double phase1 = ((hash & 0xFFFF) / 65535.0) * Math.PI * 2.0;
        double phase2 = (((hash >> 16) & 0xFFFF) / 65535.0) * Math.PI * 2.0;
        double phase3 = (((hash >> 32) & 0xFFFF) / 65535.0) * Math.PI * 2.0;
        double freq1 = 2.4 + ((hash & 0xFF) / 255.0) * 1.8;
        double freq2 = 5.5 + (((hash >> 8) & 0xFF) / 255.0) * 3.2;
        double freq3 = 11.0 + (((hash >> 24) & 0xFF) / 255.0) * 5.0;

        float[] raw = new float[BAR_COUNT];
        for (int i = 0; i < BAR_COUNT; i++) {
            float norm = i / (float) (BAR_COUNT - 1);
            // Musical intro/outro envelope
            float envelope = (float) Math.sin(Math.pow(norm, 0.78) * Math.PI);
            envelope = 0.35f + 0.65f * Math.max(0f, envelope);

            double w1 = Math.sin(norm * Math.PI * freq1 + phase1) * 0.36;
            double w2 = Math.cos(norm * Math.PI * freq2 + phase2) * 0.28;
            double w3 = Math.sin(norm * Math.PI * freq3 + phase3) * 0.22;

            // Deterministic per-bar rhythm transients
            long barSeed = hash ^ (i * 2654435761L);
            float pulse = ((barSeed & 0xFF) / 255.0f) * 0.32f;

            float amp = (float) (0.48 + w1 + w2 + w3) * 0.68f + pulse;
            amp = amp * envelope;
            raw[i] = Math.max(0.20f, Math.min(1.0f, amp));
        }

        // Light 3-tap smoothing with preserved dynamic peaks
        for (int i = 0; i < BAR_COUNT; i++) {
            float prev = raw[Math.max(0, i - 1)];
            float curr = raw[i];
            float next = raw[Math.min(BAR_COUNT - 1, i + 1)];
            float smoothed = curr * 0.62f + (prev + next) * 0.19f;
            barAmplitudes[i] = Math.max(0.20f, Math.min(1.0f, smoothed));
        }
    }

    private void animateScrubState(boolean active) {
        if (isScrubbing == active) return;
        isScrubbing = active;
        if (scrubAnimator != null) {
            scrubAnimator.cancel();
        }
        scrubAnimator = ValueAnimator.ofFloat(scrubExpansion, active ? 1f : 0f);
        scrubAnimator.setDuration(active ? 150L : 220L);
        scrubAnimator.setInterpolator(new DecelerateInterpolator());
        scrubAnimator.addUpdateListener(anim -> {
            scrubExpansion = (float) anim.getAnimatedValue();
            invalidate();
        });
        scrubAnimator.start();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            if (getParent() != null) {
                getParent().requestDisallowInterceptTouchEvent(true);
            }
            animateScrubState(true);
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            animateScrubState(false);
        }
        return super.onTouchEvent(event);
    }

    @Override
    protected synchronized void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        float density = getResources().getDisplayMetrics().density;
        int desiredHeight = Math.round(44f * density);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int heightSize = MeasureSpec.getSize(heightMeasureSpec);

        int finalHeight;
        if (heightMode == MeasureSpec.EXACTLY) {
            finalHeight = heightSize;
        } else if (heightMode == MeasureSpec.AT_MOST) {
            finalHeight = Math.min(desiredHeight, heightSize);
        } else {
            finalHeight = desiredHeight;
        }
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), finalHeight);
    }

    @Override
    protected synchronized void onDraw(Canvas canvas) {
        int width = getWidth();
        int height = getHeight();
        if (width <= 0 || height <= 0) return;

        float density = getResources().getDisplayMetrics().density;
        float horizontalPad = Math.max(getPaddingLeft(), 6f * density);
        float rightPad = Math.max(getPaddingRight(), 6f * density);
        float usableWidth = width - horizontalPad - rightPad;
        if (usableWidth <= 0) return;

        int max = getMax();
        int progress = getProgress();
        float progressRatio = (max > 0) ? Math.max(0f, Math.min(1f, progress / (float) max)) : 0f;

        float abStartRatio = (max > 0 && abLoopStartMs >= 0)
                ? Math.max(0f, Math.min(1f, abLoopStartMs / (float) max))
                : -1f;
        float abEndRatio = (max > 0 && abLoopEndMs > abLoopStartMs)
                ? Math.max(0f, Math.min(1f, abLoopEndMs / (float) max))
                : -1f;

        float centerY = height / 2f;
        float maxBarHeight = (height - 8f * density) * (0.86f + 0.14f * scrubExpansion);
        float minBarHeight = Math.max(4f * density, maxBarHeight * 0.18f);

        float slotWidth = usableWidth / BAR_COUNT;
        float gap = Math.max(1.6f * density, slotWidth * 0.36f);
        float barWidth = Math.max(2.0f * density, slotWidth - gap);
        float cornerRadius = barWidth / 2f;

        float exactBarPosition = progressRatio * BAR_COUNT;

        for (int i = 0; i < BAR_COUNT; i++) {
            float barCenterRatio = (i + 0.5f) / BAR_COUNT;
            float left = horizontalPad + i * slotWidth + (slotWidth - barWidth) * 0.5f;
            float right = left + barWidth;

            // Subtle magnetic wave lift near current position while scrubbing
            float distFromPlayhead = Math.abs((i + 0.5f) - exactBarPosition);
            float magneticBoost = 0f;
            if (scrubExpansion > 0.01f && distFromPlayhead < 3.2f) {
                float factor = 1f - (distFromPlayhead / 3.2f);
                magneticBoost = factor * factor * 0.18f * scrubExpansion;
            }

            float amp = Math.min(1f, barAmplitudes[i] + magneticBoost);
            float barH = minBarHeight + amp * (maxBarHeight - minBarHeight);
            float top = centerY - barH * 0.5f;
            float bottom = centerY + barH * 0.5f;

            boolean inAbLoop = (abStartRatio >= 0f && abEndRatio > abStartRatio
                    && barCenterRatio >= abStartRatio && barCenterRatio <= abEndRatio);

            int unplayedBase = inAbLoop ? COLOR_AB_UNPLAYED : COLOR_UNPLAYED;

            float barFillFraction = exactBarPosition - i;
            if (barFillFraction >= 1f) {
                barPaint.setColor(accentColor);
            } else if (barFillFraction <= 0f) {
                barPaint.setColor(unplayedBase);
            } else {
                // Only color transition between unplayed slate and accent color (no white glow or white line)
                int blended = ColorUtils.blendARGB(unplayedBase, accentColor, barFillFraction);
                barPaint.setColor(blended);
            }

            barRect.set(left, top, right, bottom);
            canvas.drawRoundRect(barRect, cornerRadius, cornerRadius, barPaint);
        }

        // Draw A-B loop marker pins in accent color if set
        if (abStartRatio >= 0f) {
            drawAbLoopPin(canvas, horizontalPad + abStartRatio * usableWidth, height, density);
        }
        if (abEndRatio > abStartRatio && abEndRatio >= 0f) {
            drawAbLoopPin(canvas, horizontalPad + abEndRatio * usableWidth, height, density);
        }
    }

    private void drawAbLoopPin(Canvas canvas, float x, int height, float density) {
        abMarkerPaint.setColor(accentColor);
        float lineW = 1.8f * density;
        barRect.set(x - lineW * 0.5f, 3f * density, x + lineW * 0.5f, height - 3f * density);
        canvas.drawRoundRect(barRect, lineW * 0.5f, lineW * 0.5f, abMarkerPaint);
        canvas.drawCircle(x, 4.5f * density, 3.0f * density, abMarkerPaint);
        canvas.drawCircle(x, height - 4.5f * density, 3.0f * density, abMarkerPaint);
    }
}
