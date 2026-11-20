package com.attomaticsystems.parrotplayer;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;
import android.view.animation.DecelerateInterpolator;
import android.widget.AbsListView;
import android.widget.GridView;

import androidx.viewpager2.widget.ViewPager2;

/**
 * Attaches to any ListView or GridView to auto-hide top and bottom overlay bars using pure
 * GPU-accelerated translationY animations (zero requestLayout() passes, zero scroll jitter).
 */
public final class AutoHideScrollHelper {

    public interface ScrollBarController {
        void setBarsVisible(boolean visible);
    }

    private static final long TOGGLE_COOLDOWN_MS = 240L;
    private static long lastToggleElapsedMs = 0L;

    private AutoHideScrollHelper() {}

    @SuppressLint("ClickableViewAccessibility")
    public static void attach(final AbsListView listView,
                              final Activity activity,
                              final AbsListView.OnScrollListener extraListener) {
        if (listView == null || activity == null) return;

        final float density = activity.getResources().getDisplayMetrics().density;
        final float hideTouchThresholdPx = 18f * density;
        final float showTouchThresholdPx = 14f * density;

        // If inside HomeFragment's ViewPager2, apply top padding so item 0 sits cleanly below the pill tabs
        if (isInsideViewPager2(listView)) {
            int topPadDp = (listView instanceof GridView) ? 62 : 56;
            int topPadPx = (int) (topPadDp * density);
            if (listView.getPaddingTop() < topPadPx) {
                listView.setClipToPadding(false);
                listView.setPadding(
                        listView.getPaddingLeft(),
                        topPadPx,
                        listView.getPaddingRight(),
                        listView.getPaddingBottom()
                );
            }
        }

        final boolean[] isUserTouching = new boolean[]{false};
        final int[] currentScrollState = new int[]{AbsListView.OnScrollListener.SCROLL_STATE_IDLE};

        listView.setOnTouchListener(new View.OnTouchListener() {
            private float lastRawY = 0f;
            private float accumulatedDy = 0f;
            private boolean tracking = false;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                int action = event.getActionMasked();
                if (action == MotionEvent.ACTION_DOWN) {
                    isUserTouching[0] = true;
                    lastRawY = event.getRawY();
                    accumulatedDy = 0f;
                    tracking = true;
                } else if (action == MotionEvent.ACTION_MOVE) {
                    isUserTouching[0] = true;
                    float rawY = event.getRawY();
                    if (!tracking) {
                        lastRawY = rawY;
                        accumulatedDy = 0f;
                        tracking = true;
                        return false;
                    }
                    float dy = lastRawY - rawY; // dy > 0 = finger moving UP (scrolling down list)
                    lastRawY = rawY;

                    if (Math.abs(dy) < 1f || Math.abs(dy) > 300f) {
                        return false;
                    }

                    if ((dy > 0 && accumulatedDy < 0) || (dy < 0 && accumulatedDy > 0)) {
                        accumulatedDy = 0f;
                    }
                    accumulatedDy += dy;

                    if (accumulatedDy >= hideTouchThresholdPx) {
                        accumulatedDy = 0f;
                        if (canScrollOrNotAtTop(listView)) {
                            notifyControllerDebounced(activity, false);
                        }
                    } else if (accumulatedDy <= -showTouchThresholdPx) {
                        accumulatedDy = 0f;
                        notifyControllerDebounced(activity, true);
                    }
                } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    isUserTouching[0] = false;
                    tracking = false;
                    accumulatedDy = 0f;
                }
                return false;
            }
        });

        listView.setOnScrollListener(new AbsListView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(AbsListView view, int scrollState) {
                currentScrollState[0] = scrollState;
                if (extraListener != null) {
                    extraListener.onScrollStateChanged(view, scrollState);
                }
            }

            @Override
            public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
                if (extraListener != null) {
                    extraListener.onScroll(view, firstVisibleItem, visibleItemCount, totalItemCount);
                }

                if (!isUserTouching[0] && currentScrollState[0] == SCROLL_STATE_IDLE) {
                    return;
                }
                if (!view.isShown() || view.getChildCount() == 0) {
                    return;
                }

                // Automatically restore bars when scrolling back to the very top of the list
                if (firstVisibleItem == 0 && view.getChildAt(0).getTop() >= 0) {
                    notifyControllerDebounced(activity, true);
                }
            }
        });
    }

    private static boolean isInsideViewPager2(View view) {
        ViewParent parent = view.getParent();
        while (parent != null) {
            if (parent instanceof ViewPager2) {
                return true;
            }
            parent = parent.getParent();
        }
        return false;
    }

    private static boolean canScrollOrNotAtTop(AbsListView listView) {
        if (listView.getChildCount() == 0) return false;
        if (listView.getFirstVisiblePosition() > 0) return true;
        if (listView.getChildAt(0).getTop() < listView.getPaddingTop() - 8) return true;
        return listView.canScrollVertically(1);
    }

    private static void notifyControllerDebounced(Activity activity, boolean visible) {
        if (!(activity instanceof ScrollBarController)) return;
        long now = SystemClock.uptimeMillis();
        if (now - lastToggleElapsedMs < TOGGLE_COOLDOWN_MS) return;
        lastToggleElapsedMs = now;
        ((ScrollBarController) activity).setBarsVisible(visible);
    }

    /**
     * Smoothly slides an overlay top bar up/down using GPU translationY without triggering requestLayout().
     */
    public static void animateTopBarCollapse(final View barView, final boolean show) {
        if (barView == null) return;

        int height = barView.getHeight();
        if (height <= 0) {
            barView.measure(
                    View.MeasureSpec.makeMeasureSpec(
                            barView.getResources().getDisplayMetrics().widthPixels,
                            View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            );
            height = barView.getMeasuredHeight();
        }
        if (height <= 0) return;

        float targetY = show ? 0f : -height;
        barView.animate().cancel();
        barView.animate()
                .translationY(targetY)
                .alpha(show ? 1f : 0f)
                .setDuration(220)
                .setInterpolator(new DecelerateInterpolator())
                .withStartAction(() -> {
                    if (show) {
                        barView.setClickable(true);
                    }
                })
                .withEndAction(() -> {
                    if (!show) {
                        barView.setClickable(false);
                    }
                })
                .start();
    }
}
