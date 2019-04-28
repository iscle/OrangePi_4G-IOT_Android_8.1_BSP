/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.car.cluster.sample.cards;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.PorterDuff.Mode;
import android.graphics.PorterDuffXfermode;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.util.Log;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.ViewSwitcher;

import com.android.car.cluster.sample.DebugUtil;
import com.android.car.cluster.sample.R;

/**
 * View that responsible for displaying cards in instrument cluster (including media, phone and
 * maps).
 */
public class CardView extends FrameLayout implements Comparable<CardView> {

    private final static String TAG = DebugUtil.getTag(CardView.class);

    protected final static long SHOW_ANIMATION_DURATION = 1000 * DebugUtil.ANIMATION_FACTOR;

    protected ImageView mBackgroundImage;
    protected ViewGroup mDetailsPanel;
    protected ViewSwitcher mLeftIconSwitcher;
    protected ViewSwitcher mRightIconSwitcher;

    private Bitmap mBitmap = Bitmap.createBitmap(1, 1, Config.ARGB_8888);

    protected long mLastUpdated = SystemClock.elapsedRealtime();

    private Canvas mCanvas = new Canvas();
    private final Paint mBackgroundCirclePaint;

    private final static Handler mHandler = new Handler(Looper.getMainLooper());

    protected final int mCardWidth;
    protected final int mCardHeight;
    protected final int mCurveRadius;
    protected final int mTextPanelWidth;
    protected final float mLeftPadding;
    protected final float mIconSize;
    protected final float mIconsOverlap;

    protected int mPriority = 9;

    protected final static int PRIORITY_GARBAGE = 10;

    protected final static int PRIORITY_CALL_INCOMING = 3;
    protected final static int PRIORITY_CALL_ACTIVE = 5;
    protected final static int PRIORITY_MEDIA_NOTIFICATION = 3;
    protected final static int PRIORITY_MEDIA_ACTIVE = 6;
    protected final static int PRIORITY_WEATHER_CARD = 9;
    protected final static int PRIORITY_NAVIGATION_ACTIVE = 3;
    protected final static int PRIORITY_HANGOUT_NOTIFICATION = 3;

    @CardType
    private int mCardType;
    private final PriorityChangedListener mPriorityChangedListener;

    public @interface CardType {
        int WEATHER = 1;
        int MEDIA = 2;
        int PHONE_CALL = 3;
        int NAV = 4;
        int HANGOUT = 5;
    }

    public CardView(Context context, @CardType int cardType, PriorityChangedListener listener) {
        this(context, null, cardType, listener);
    }

    public CardView(Context context, AttributeSet attrs, @CardType int cardType,
            PriorityChangedListener listener) {
        super(context, attrs);
        mPriorityChangedListener = listener;
        if (DebugUtil.DEBUG) {
            Log.d(TAG, "ctor");
        }
        mCardType = cardType;

        setWillNotDraw(false);  // This will trigger onDraw method.

        mBackgroundCirclePaint = createBackgroundCirclePaint();
        mCardWidth = (int)getResources().getDimension(R.dimen.card_width);
        mCardHeight = (int) getResources().getDimension(R.dimen.card_height);
        mTextPanelWidth = (int)getResources().getDimension(R.dimen.card_message_panel_width);
        mLeftPadding = getResources().getDimension(R.dimen.card_content_left_padding);
        mIconSize = getResources().getDimension(R.dimen.card_icon_size);
        mIconsOverlap = mIconSize - mLeftPadding;
        mCurveRadius = (int)(mCardWidth * 0.643f);

        inflate(getContext(), R.layout.card_view, this);

        if (this.isInEditMode()) {
            return;
        }

        mLeftIconSwitcher = viewById(R.id.left_icon_switcher);
        mRightIconSwitcher = viewById(R.id.right_icon_switcher);
        mBackgroundImage = viewById(R.id.image_background);

        init();
    }

    protected void inflate(int layoutId) {
        inflate(getContext(), layoutId, (ViewGroup) getChildAt(0));
    }

    protected void init() {
    }

    @CardType
    public int getCardType() {
        return mCardType;
    }

    public void setLeftIcon(Bitmap bitmap) {
        setLeftIcon(bitmap, false /* animated */);
    }

    public void setLeftIcon(Bitmap bitmap, boolean animated) {
        if (DebugUtil.DEBUG) {
            Log.d(TAG, "setLeftIcon, bitmap: " + bitmap);
        }
        switchImageViewBitmpa(bitmap, mLeftIconSwitcher, animated);
    }


    public void setRightIcon(Bitmap bitmap) {
        setRightIcon(bitmap, false /* animated */);
    }

    /**
     * @param bitmap if null, the image won't be displayed and message panel will be placed
     * accordingly.
     */
    public void setRightIcon(Bitmap bitmap, boolean animated) {
        if (DebugUtil.DEBUG) {
            Log.d(TAG, "setRightIcon, bitmap: " + bitmap);
        }
        if (bitmap == null && mRightIconSwitcher.getVisibility() == VISIBLE) {
            mRightIconSwitcher.setVisibility(GONE);
        } else if (bitmap != null && mRightIconSwitcher.getVisibility() == GONE) {
            mRightIconSwitcher.setVisibility(VISIBLE);
        }

        switchImageViewBitmpa(bitmap, mRightIconSwitcher, animated);
    }

    private void switchImageViewBitmpa(Bitmap bitmap, ViewSwitcher switcher, boolean animated) {
        ImageView icon = (ImageView) (animated
                ? switcher.getNextView() : switcher.getCurrentView());

        icon.setBackground(null);
        icon.setImageBitmap(bitmap);

        if (animated) {
            switcher.showNext();
        }
    }

    /** Called by {@code ClusterView} when card should go away using unreveal animation */
    public void onPlayUnrevealAnimation() {
        if (DebugUtil.DEBUG) {
            Log.d(TAG, "onPlayUnrevealAnimation");
        }
    }

    public void onPlayRevealAnimation() {
        if (DebugUtil.DEBUG) {
            Log.d(TAG, "onPlayRevealAnimation");
        }

        if (mLeftIconSwitcher.getVisibility() == VISIBLE) {
            mLeftIconSwitcher.setTranslationX(mCardWidth / 2);
            mLeftIconSwitcher.animate()
                    .translationX(getLeftIconTargetX())
                    .setDuration(SHOW_ANIMATION_DURATION)
                    .setInterpolator(getDecelerateInterpolator());
        }

        if (mRightIconSwitcher.getVisibility() == VISIBLE) {
            mRightIconSwitcher.setTranslationX( mCardWidth - mTextPanelWidth / 2 - mIconSize);
            mRightIconSwitcher.animate()
                    .translationX(getRightIconTargetX())
                    .setDuration(SHOW_ANIMATION_DURATION)
                    .setInterpolator(getDecelerateInterpolator());
        }

        showDetailsPanelAnimation(getDetailsPanelTargetX());
    }

    protected float getLeftIconTargetX() {
        return mLeftIconSwitcher.getVisibility() == VISIBLE ? mLeftPadding : 0;
    }

    protected float getRightIconTargetX() {
        if (mRightIconSwitcher.getVisibility() != VISIBLE) {
            return 0;
        }
        float x = mLeftPadding;
        if (mLeftIconSwitcher.getVisibility() == VISIBLE) {
            x += mIconsOverlap;
        }
        return  x;
    }

    protected float getDetailsPanelTargetX() {
        return Math.max(getLeftIconTargetX(), getRightIconTargetX()) + mIconSize + mLeftPadding;
    }

    protected void showDetailsPanelAnimation(float textX) {
        if (mDetailsPanel != null) {
            mDetailsPanel.setTranslationX(mCardWidth - mTextPanelWidth / 2);
            mDetailsPanel.animate()
                    .translationX(textX)
                    .setDuration(SHOW_ANIMATION_DURATION)
                    .setInterpolator(getDecelerateInterpolator());
        }
    }

    protected DecelerateInterpolator getDecelerateInterpolator() {
        return new DecelerateInterpolator(2f);
    }

    public void setBackground(Bitmap bmpPicture, int backgroundColor) {
        Bitmap bmpBackground = Bitmap.createBitmap(
                mCardWidth,
                (int)getResources().getDimension(R.dimen.card_height),
                Config.ARGB_8888);
        Canvas canvas = new Canvas(bmpBackground);
        //clear previous drawings
        canvas.drawColor(Color.TRANSPARENT, Mode.CLEAR);

        Paint p = new Paint();
        p.setColor(backgroundColor);
        p.setAntiAlias(true);
        p.setStyle(Style.FILL);
        // Draw curved background.
        canvas.drawCircle(mCurveRadius, (int)getResources().getDimension(
                R.dimen.card_height) / 2,
                mCurveRadius, p);

        // Draw image respecting curved background.
        p.setXfermode(new PorterDuffXfermode(Mode.SRC_IN));
        float x = canvas.getWidth() - bmpPicture.getWidth();
        float y = canvas.getHeight() - bmpPicture.getHeight();
        if (y < 0) {
            y = y / 2; // Center image if it is not fitting.
        }
        canvas.drawBitmap(bmpPicture, x, y, p);

        mBackgroundImage.setScaleType(ScaleType.CENTER_CROP);
        mBackgroundImage.setImageBitmap(bmpBackground);
        if (mBackgroundImage.getVisibility() != VISIBLE) {
            mBackgroundImage.setVisibility(VISIBLE);
        }
    }

    public void setBackgroundColor(int color) {
        mBackgroundCirclePaint.setColor(color);
    }

    private Paint createBackgroundCirclePaint() {
        Paint p = new Paint();
        p.setAntiAlias(true);
        p.setXfermode(new PorterDuffXfermode(Mode.ADD));
        p.setStyle(Style.FILL);
        p.setColor(getResources().getColor(R.color.cluster_active_area_background, null));
        return p;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if(mBitmap.isRecycled() || mBitmap.getWidth() != canvas.getWidth()
                || mBitmap.getHeight() != canvas.getHeight()) {
            Log.d(TAG, "creating bitmap...");
            mBitmap.recycle();
            mBitmap = Bitmap.createBitmap(canvas.getWidth(), canvas.getHeight(), Config.ARGB_8888);
            mCanvas.setBitmap(mBitmap);
        }

        //clear previous drawings
        mCanvas.drawColor(Color.TRANSPARENT, Mode.CLEAR);

        mCanvas.drawCircle(mCurveRadius, getHeight() / 2, mCurveRadius, mBackgroundCirclePaint);

        canvas.drawBitmap(mBitmap, 0, 0, null);
    }

    public int getIconSize() {
        return (int)mIconSize;
    }

    public static void runDelayed(long delay, final Runnable task) {
        mHandler.postDelayed(task, delay);
    }

    public void setPriority(int priority) {
        mPriority = priority;
        mPriorityChangedListener.onPriorityChanged(this, priority);
    }

    /**
     * Should return number from 0 to 9. 0 - is most important, 9 is less important.
     */
    public int getPriority() {
        return mPriority;
    }

    public boolean isGarbage() {
        return getPriority() == PRIORITY_GARBAGE;
    }

    public void removeGracefully() {
        setPriority(PRIORITY_GARBAGE);
    }

    @Override
    public int compareTo(CardView another) {
        int res = this.getPriority() - another.getPriority();
        if (res == 0) {
            // If objects have the same priorities, check the last time they were updated.
            res = this.mLastUpdated > another.mLastUpdated ? -1 : 1;
            if (DebugUtil.DEBUG) {
                Log.d(TAG, "Found card with the same priority: " + this + " and " + another + ","
                        + "this.mLastUpdated: " + mLastUpdated
                        + ", another.mLastUpdated:" + another.mLastUpdated + ", res: " + res);

            }
        }
        return res;
    }

    @SuppressWarnings("unchecked")
    protected <E> E viewById(int id) {
        return (E) findViewById(id);
    }

    public interface PriorityChangedListener {
        void onPriorityChanged(CardView cardView, int newPriority);
    }
}
