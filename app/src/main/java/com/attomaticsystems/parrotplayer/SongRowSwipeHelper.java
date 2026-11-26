package com.attomaticsystems.parrotplayer;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.Nullable;

/**
 * Adds AIMP/Poweramp-style horizontal swipe gestures to song ListView rows:
 * - Swipe Right: Play Next in queue
 * - Swipe Left: Toggle Favourite
 */
public final class SongRowSwipeHelper {

    public interface SongPathProvider {
        @Nullable
        String getSongPathAt(int position);
        boolean isSwipeAllowed();
    }

    private SongRowSwipeHelper() {}

    @SuppressLint("ClickableViewAccessibility")
    public static void attach(final ListView listView,
                              final Context context,
                              final SongPathProvider pathProvider,
                              @Nullable final Runnable onFavouriteToggled) {
        if (listView == null || context == null || pathProvider == null) return;

        final int touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        final float density = context.getResources().getDisplayMetrics().density;
        final float triggerDistancePx = 68f * density;
        final float maxTranslatePx = 96f * density;

        listView.setOnTouchListener(new View.OnTouchListener() {
            private float downX = 0f;
            private float downY = 0f;
            private int targetPos = ListView.INVALID_POSITION;
            private View targetRowView = null;
            private boolean isHorizontalSwiping = false;
            private boolean isVerticalScrolling = false;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (!pathProvider.isSwipeAllowed()) {
                    resetRow(false);
                    return false;
                }

                int action = event.getActionMasked();
                switch (action) {
                    case MotionEvent.ACTION_DOWN:
                        downX = event.getX();
                        downY = event.getY();
                        isHorizontalSwiping = false;
                        isVerticalScrolling = false;
                        targetPos = listView.pointToPosition((int) downX, (int) downY);
                        if (targetPos != ListView.INVALID_POSITION) {
                            int childIdx = targetPos - listView.getFirstVisiblePosition();
                            if (childIdx >= 0 && childIdx < listView.getChildCount()) {
                                targetRowView = listView.getChildAt(childIdx);
                            } else {
                                targetRowView = null;
                            }
                        } else {
                            targetRowView = null;
                        }
                        break;

                    case MotionEvent.ACTION_MOVE:
                        if (isVerticalScrolling || targetRowView == null) {
                            break;
                        }
                        float dx = event.getX() - downX;
                        float dy = event.getY() - downY;

                        if (!isHorizontalSwiping) {
                            if (Math.abs(dy) > touchSlop && Math.abs(dy) > Math.abs(dx)) {
                                isVerticalScrolling = true;
                                break;
                            }
                            if (Math.abs(dx) > touchSlop * 1.4f && Math.abs(dx) > Math.abs(dy) * 1.4f) {
                                isHorizontalSwiping = true;
                                if (listView.getParent() != null) {
                                    listView.getParent().requestDisallowInterceptTouchEvent(true);
                                }
                                MotionEvent cancelListPress = MotionEvent.obtain(event);
                                cancelListPress.setAction(MotionEvent.ACTION_CANCEL);
                                listView.onTouchEvent(cancelListPress);
                                cancelListPress.recycle();
                            }
                        }

                        if (isHorizontalSwiping) {
                            float clampedDx = Math.max(-maxTranslatePx, Math.min(maxTranslatePx, dx * 0.72f));
                            targetRowView.setTranslationX(clampedDx);
                            float progress = Math.min(1f, Math.abs(dx) / triggerDistancePx);
                            targetRowView.setAlpha(1f - 0.25f * progress);
                            return true;
                        }
                        break;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (isHorizontalSwiping && targetRowView != null) {
                            float totalDx = event.getX() - downX;
                            String songPath = pathProvider.getSongPathAt(targetPos);
                            if (action == MotionEvent.ACTION_UP && songPath != null && !songPath.isEmpty()) {
                                if (totalDx >= triggerDistancePx) {
                                    // Swipe Right -> Play Next
                                    Intent intent = new Intent(context, MusicService.class);
                                    intent.setAction(MusicService.ACTION_PLAY_NEXT);
                                    intent.putExtra("songPath", songPath);
                                    context.startService(intent);
                                    Toast.makeText(context, "⏩ Will play next", Toast.LENGTH_SHORT).show();
                                } else if (totalDx <= -triggerDistancePx) {
                                    // Swipe Left -> Toggle Favourite
                                    FavouritesManager fm = new FavouritesManager(context);
                                    fm.toggleFavourite(songPath);
                                    boolean isFav = fm.isFavourite(songPath);
                                    Toast.makeText(context,
                                            isFav ? "♥ Added to Favourites" : "Removed from Favourites",
                                            Toast.LENGTH_SHORT).show();
                                    if (onFavouriteToggled != null) {
                                        onFavouriteToggled.run();
                                    }
                                }
                            }
                            resetRow(true);
                            isHorizontalSwiping = false;
                            return true;
                        }
                        resetRow(false);
                        break;
                }
                return false;
            }

            private void resetRow(boolean animate) {
                if (targetRowView != null) {
                    if (animate) {
                        targetRowView.animate()
                                .translationX(0f)
                                .alpha(1f)
                                .setDuration(180)
                                .setInterpolator(new OvershootInterpolator(1.1f))
                                .start();
                    } else {
                        targetRowView.setTranslationX(0f);
                        targetRowView.setAlpha(1f);
                    }
                    targetRowView = null;
                }
            }
        });
    }
}
