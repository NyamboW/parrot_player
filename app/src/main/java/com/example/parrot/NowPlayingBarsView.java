package com.example.parrot;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;
import android.widget.TextView;

/**
 * Simple 3-bar equalizer indicator shown next to the currently playing song in lists.
 */
public class NowPlayingBarsView extends View {

    private static final int BAR_COUNT = 3;
    private static final long FRAME_DELAY_MS = 35L;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF barRect = new RectF();
    private boolean isPlaying = false;

    public NowPlayingBarsView(Context context) {
        super(context);
        init(context);
    }

    public NowPlayingBarsView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public NowPlayingBarsView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    private void init(Context context) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(AccentColorHelper.getAccentColor(context));
    }

    public void setBarColor(int color) {
        if (paint.getColor() != color) {
            paint.setColor(color);
            invalidate();
        }
    }

    public void setPlaying(boolean playing) {
        if (this.isPlaying != playing) {
            this.isPlaying = playing;
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float gap = w * 0.16f;
        float totalGaps = gap * (BAR_COUNT - 1);
        float barWidth = (w - totalGaps) / BAR_COUNT;
        float cornerRadius = barWidth / 2f;

        long now = SystemClock.uptimeMillis();

        for (int i = 0; i < BAR_COUNT; i++) {
            float fraction;
            if (isPlaying) {
                double speed = (i == 0) ? 0.0085 : (i == 1) ? 0.0118 : 0.0098;
                double phase = i * 1.9;
                float wave = (float) ((Math.sin(now * speed + phase) + 1.0) * 0.5);
                fraction = 0.22f + (0.78f * wave);
            } else {
                fraction = (i == 0) ? 0.35f : (i == 1) ? 0.62f : 0.45f;
            }

            float barHeight = Math.max(barWidth, h * fraction);
            float left = i * (barWidth + gap);
            float top = h - barHeight;
            barRect.set(left, top, left + barWidth, h);
            canvas.drawRoundRect(barRect, cornerRadius, cornerRadius, paint);
        }

        if (isPlaying && getVisibility() == VISIBLE && isAttachedToWindow()) {
            postInvalidateDelayed(FRAME_DELAY_MS);
        }
    }

    /**
     * Binds the now-playing bars and song title highlight for a song row in any list.
     */
    public static void bindRow(View rowView, String songPath, TextView txtSong) {
        if (rowView == null) return;
        NowPlayingBarsView bars = rowView.findViewById(R.id.nowPlayingBars);
        String activePath = MusicService.getActiveSongPath();
        boolean isCurrentSong = songPath != null && songPath.equals(activePath);
        boolean playing = isCurrentSong && MusicService.isActivelyPlaying();
        int accent = AccentColorHelper.getAccentColor(rowView.getContext());

        if (bars != null) {
            if (isCurrentSong) {
                bars.setBarColor(accent);
                bars.setVisibility(View.VISIBLE);
                bars.setPlaying(playing);
            } else {
                bars.setPlaying(false);
                bars.setVisibility(View.GONE);
            }
        }
        if (txtSong != null) {
            txtSong.setTextColor(isCurrentSong ? accent : 0xFFF5F7FA);
        }
        TrackMetadataHelper.bindDuration(rowView.findViewById(R.id.txtSongDuration), songPath);
    }
}
