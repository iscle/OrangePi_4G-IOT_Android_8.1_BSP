package android.uirendering.cts.util;

import android.view.View;
import android.view.ViewTreeObserver.OnPreDrawListener;

import java.util.HashSet;
import java.util.Set;

public class DrawCountDown implements OnPreDrawListener {
    private static Set<DrawCountDown> sPendingCallbacks = new HashSet<>();

    private int mDrawCount;
    private View mTargetView;
    private Runnable mRunnable;

    private DrawCountDown(View targetView, int countFrames, Runnable countReachedListener) {
        mTargetView = targetView;
        mDrawCount = countFrames;
        mRunnable = countReachedListener;
    }

    @Override
    public boolean onPreDraw() {
        if (mDrawCount <= 0) {
            synchronized (sPendingCallbacks) {
                sPendingCallbacks.remove(this);
            }
            mTargetView.getViewTreeObserver().removeOnPreDrawListener(this);
            mRunnable.run();
        } else {
            mDrawCount--;
            mTargetView.postInvalidate();
        }
        return true;
 
    }

    public static void countDownDraws(View targetView, int countFrames,
            Runnable onDrawCountReachedListener) {
        DrawCountDown counter = new DrawCountDown(targetView, countFrames,
                onDrawCountReachedListener);
        synchronized (sPendingCallbacks) {
            sPendingCallbacks.add(counter);
        }
        targetView.getViewTreeObserver().addOnPreDrawListener(counter);
    }

    public static void cancelPending() {
        synchronized (sPendingCallbacks) {
            for (DrawCountDown counter : sPendingCallbacks) {
                counter.mTargetView.getViewTreeObserver().removeOnPreDrawListener(counter);
            }
            sPendingCallbacks.clear();
        }
    }
}
