package com.example.parrot;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.AbsListView;

/**
 * Attaches to any ListView or GridView to auto-hide top and bottom bars when scrolling down
 * and auto-show them when scrolling up or reaching the top of the list.
 */
public final class AutoHideScrollHelper {

    public interface ScrollBarController {
        void setBarsVisible(boolean visible);
    }

    private AutoHideScrollHelper() {}

    @SuppressLint("ClickableViewAccessibility")
    public static void attach(final AbsListView listView,
                              final Activity activity,
                              final AbsListView.OnScrollListener extraListener) {
        if (listView == null || activity == null) return;

        final float density = activity.getResources().getDisplayMetrics().density;
        final float hideTouchThresholdPx = 16f * density;
        final float showTouchThresholdPx = 14f * density;

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
                            notifyController(activity, false);
                        }
                    } else if (accumulatedDy <= -showTouchThresholdPx) {
                        accumulatedDy = 0f;
                        notifyController(activity, true);
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
            private int lastFirstVisibleItem = 0;

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

                // Ignore layout-pass onScroll events from idle or offscreen ViewPager2 pages
                if (!isUserTouching[0] && currentScrollState[0] == SCROLL_STATE_IDLE) {
                    lastFirstVisibleItem = firstVisibleItem;
                    return;
                }
                if (!view.isShown() || view.getChildCount() == 0) {
                    return;
                }

                View firstChild = view.getChildAt(0);
                int currentTop = firstChild.getTop();

                // Restore bars when actively scrolling back to the very top of the list
                if (firstVisibleItem == 0 && currentTop >= -6 && lastFirstVisibleItem > 0) {
                    notifyController(activity, true);
                    lastFirstVisibleItem = 0;
                    return;
                }

                // Handle fast fling across multiple items
                if (firstVisibleItem > lastFirstVisibleItem + 1) {
                    notifyController(activity, false);
                } else if (firstVisibleItem < lastFirstVisibleItem - 1) {
                    notifyController(activity, true);
                }
                lastFirstVisibleItem = firstVisibleItem;
            }
        });
    }

    private static boolean canScrollOrNotAtTop(AbsListView listView) {
        if (listView.getChildCount() == 0) return false;
        if (listView.getFirstVisiblePosition() > 0) return true;
        if (listView.getChildAt(0).getTop() < -4) return true;
        return listView.canScrollVertically(1);
    }

    private static void notifyController(Activity activity, boolean visible) {
        if (activity instanceof ScrollBarController) {
            ((ScrollBarController) activity).setBarsVisible(visible);
        }
    }

    /**
     * Smoothly slides a top bar view out of view by animating its topMargin between 0 and -fullHeight.
     * Because the parent is a vertical LinearLayout, this pulls the content below upward smoothly
     * without squishing the bar's internal text/buttons.
     */
    public static void animateTopBarCollapse(final View barView, final boolean show) {
        if (barView == null) return;

        Object runningAnim = barView.getTag(R.id.layoutToolBar);
        if (runningAnim instanceof ValueAnimator) {
            ((ValueAnimator) runningAnim).cancel();
        }

        Integer storedFullHeight = (Integer) barView.getTag(R.id.miniPlayer);
        int currentHeight = barView.getHeight();
        if (storedFullHeight == null || storedFullHeight <= 0) {
            if (currentHeight > 0) {
                storedFullHeight = currentHeight;
            } else {
                barView.measure(
                        View.MeasureSpec.makeMeasureSpec(
                                barView.getResources().getDisplayMetrics().widthPixels,
                                View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
                );
                storedFullHeight = barView.getMeasuredHeight();
            }
            if (storedFullHeight > 0) {
                barView.setTag(R.id.miniPlayer, storedFullHeight);
            }
        }

        if (storedFullHeight == null || storedFullHeight <= 0) return;

        final int fullHeight = storedFullHeight;
        final ViewGroup.LayoutParams rawLp = barView.getLayoutParams();
        if (!(rawLp instanceof ViewGroup.MarginLayoutParams)) return;
        final ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) rawLp;

        final int startMargin = lp.topMargin;
        final int targetMargin = show ? 0 : -fullHeight;

        if (startMargin == targetMargin) {
            barView.setAlpha(show ? 1f : 0f);
            return;
        }

        ValueAnimator animator = ValueAnimator.ofInt(startMargin, targetMargin);
        animator.setDuration(200);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(anim -> {
            int margin = (int) anim.getAnimatedValue();
            lp.topMargin = margin;
            barView.setLayoutParams(lp);
            float fractionVisible = 1f - Math.min(1f, Math.abs(margin) / (float) fullHeight);
            barView.setAlpha(fractionVisible);
        });
        animator.addListener(new AnimatorListenerAdapter() {
            private boolean cancelled = false;

            @Override
            public void onAnimationCancel(Animator animation) {
                cancelled = true;
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                barView.setTag(R.id.layoutToolBar, null);
                if (!cancelled) {
                    lp.topMargin = targetMargin;
                    barView.setLayoutParams(lp);
                    barView.setAlpha(show ? 1f : 0f);
                }
            }
        });
        barView.setTag(R.id.layoutToolBar, animator);
        animator.start();
    }
}
