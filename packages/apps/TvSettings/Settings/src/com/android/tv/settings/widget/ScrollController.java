/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.tv.settings.widget;

import android.content.Context;
import android.content.res.Configuration;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.LinearInterpolator;
import android.widget.Scroller;

/**
 * Maintains a Scroller object and two axis scrolling information
 */
public class ScrollController {
    /**
     * try to keep focused view kept in middle of viewport, focus move to the side of viewport when
     * scroll to the beginning or end, this will make sure you won't see blank space in viewport
     * {@link Axis.ItemWindow#setCount(int)} defines the size of window (how many items) we are
     * trying to keep in the middle. <p>
     * The middle point is calculated by "scrollCenterOffset" or "scrollCenterOffsetPercent";
     * if none of these two are defined,  default value is 1/2 of the size.
     *
     * @see Axis#setScrollCenterStrategy(int)
     * @see Axis#getSystemScrollPos(int)
     */
    public final static int SCROLL_CENTER_IN_MIDDLE = 0;

    /**
     * focus view kept at a fixed location, might see blank space. The distance of fixed location
     * to left/top is given by {@link Axis#setScrollCenterOffset(int)}
     *
     * @see Axis#setScrollCenterStrategy(int)
     * @see Axis#getSystemScrollPos(int)
     */
    public final static int SCROLL_CENTER_FIXED = 1;

    /**
     * focus view kept at a fixed percentage distance from the left/top of the view,
     * might see blank space. The offset percent is set by
     * {@link Axis#setScrollCenterOffsetPercent(int)}. A fixed offset from this
     * position may also be set with {@link Axis#setScrollCenterOffset(int)}.
     *
     * @see Axis#setScrollCenterStrategy(int)
     * @see Axis#getSystemScrollPos(int)
     */
    public final static int SCROLL_CENTER_FIXED_PERCENT = 2;

    /**
     * focus view kept at a fixed location, might see blank space. The distance of fixed location
     * to right/bottom is given by {@link Axis#setScrollCenterOffset(int)}
     *
     * @see Axis#setScrollCenterStrategy(int)
     * @see Axis#getSystemScrollPos(int)
     */
    public final static int SCROLL_CENTER_FIXED_TO_END = 3;

    /**
     * Align center of the item
     */
    public final static int SCROLL_ITEM_ALIGN_CENTER = 0;

    /**
     * Align left/top of the item
     */
    public final static int SCROLL_ITEM_ALIGN_LOW = 1;

    /**
     * Align right/bottom of the item
     */
    public final static int SCROLL_ITEM_ALIGN_HIGH = 2;

    /** operation not allowed */
    public final static int OPERATION_DISABLE = 0;

    /**
     * operation is using {@link Axis#mScrollMin} {@link Axis#mScrollMax}, see description in
     * {@link Axis#mScrollCenter}
     */
    public final static int OPERATION_NOTOUCH = 1;

    /**
     * operation is using {@link Axis#mTouchScrollMax} and {@link Axis#mTouchScrollMin}, see
     * description in {@link Axis#mScrollCenter}
     */
    public final static int OPERATION_TOUCH = 2;

    /**
     * maps to OPERATION_TOUCH for touchscreen, OPERATION_NORMAL for non-touchscreen
     */
    public final static int OPERATION_AUTO = 3;

    private static final int SCROLL_DURATION_MIN = 250;
    private static final int SCROLL_DURATION_MAX = 1500;
    private static final int SCROLL_DURATION_PAGE_MIN = 250;
    // millisecond per pixel
    private static final float SCROLL_DURATION_MS_PER_PIX = 0.25f;

    /**
     * Maintains scroll information in one direction
     */
    public static class Axis {
        private int mOperationMode = OPERATION_NOTOUCH;
        /**
         * In {@link ScrollController#OPERATION_TOUCH} mode:<br>
         * {@link #mScrollCenter} changes from {@link #mTouchScrollMin} and
         * {@link #mTouchScrollMax}; focus won't moved to two sides when scroll to edge of view
         * port.
         * <p>
         * In {@link ScrollController#OPERATION_NOTOUCH} mode:<br>
         * mScrollCenter changes from {@link #mScrollMin} and {@link #mScrollMax}. It is different
         * than {@link View#getScrollX()} which starts from left edge of first child; mScrollCenter
         * starts from center of first child, ends at center of last child; expanded views are
         * excluded from calculating the mScrollCenter. We convert the mScrollCenter to system
         * scroll position (see {@link ScrollAdapterView#adjustSystemScrollPos}), note it's not
         * necessarily a linear transformation between system scrollX and mScrollCenter. <br>
         * For {@link #SCROLL_CENTER_IN_MIDDLE}: <br>
         * When mScrollCenter is close to {@link #mScrollMin}, {@link View#getScrollX()} will be
         * fixed 0, but mScrollCenter is still decreasing, so we can move focus from the item which
         * is at center of screen to the first child. <br>
         * For {@link #SCROLL_CENTER_FIXED} and
         * {@link #SCROLL_CENTER_FIXED_PERCENT}: It's a easy linear conversion
         * applied
         * <p>
         * mScrollCenter is also used to calculate dynamic transformation based on how far a view
         * is from the mScrollCenter. For example, the views with center close to mScrollCenter
         * will be scaled up in {@link ScrollAdapterView#applyTransformations}
         */
        private float mScrollCenter;
        /**
         * Maximum scroll value, initially unlimited until we will get the value when scroll to the
         * last item of ListAdapter and set the value to center of last child
         */
        private int mScrollMax;
        /**
         * scroll max for standard touch friendly operation, i.e. focus will not move to side when
         * scroll to two edges.
         */
        private int mTouchScrollMax;
        /** right/bottom edge of last child */
        private int mMaxEdge;
        /** left/top edge of first child, typically should be zero*/
        private int mMinEdge;
        /** Minimum scroll value, point to center of first child, typically half of child size */
        private int mScrollMin;
        /**
         * scroll min for standard touch friendly operation, i.e. focus will not move to side when
         * scroll to two edges.
         */
        private int mTouchScrollMin;

        private int mScrollItemAlign = SCROLL_ITEM_ALIGN_CENTER;

        private boolean mSelectedTakesMoreSpace = false;

        /** the offset set by a mouse dragging event */
        private float mDragOffset;

        /**
         * Total extra spaces.  Divided into four parts:<p>
         * 1.  extraSpace before scrollPosition, given by {@link #mExtraSpaceLow}
         *     This value is animating from the extra space of "transition from" to the value
         *     of "transition to"<p>
         * 2.  extraSpace after scrollPosition<p>
         * 3.  size of expanded view of "transition from"<p>
         * 4.  size of expanded view of "transition to"<p>
         * Among the four parts: 2,3,4 are after scroll position.<p>
         * 3,4 are included in mExpandedSize when {@link #mSelectedTakesMoreSpace} is true<p>
         * */
        private int mExpandedSize;
        /** extra space used before the scroll position */
        private int mExtraSpaceLow;
        private int mExtraSpaceHigh;

        private int mAlignExtraOffset;

        /**
         * Describes how to put the mScrollCenter in the view port different types affects how to
         * translate mScrollCenter to system scroll position, see details in getSystemScrollPos().
         */
        private int mScrollCenterStrategy;

        /**
         * used when {@link #mScrollCenterStrategy} is
         * {@link #SCROLL_CENTER_FIXED}, {@link #SCROLL_CENTER_FIXED_PERCENT} or
         * {@link #SCROLL_CENTER_FIXED_TO_END}, the offset for the fixed location of center
         * scroll position relative to left/top,  percentage or right/bottom
         */
        private int mScrollCenterOffset = -1;

        /**
         * used when {@link #mScrollCenterStrategy} is
         * {@link #SCROLL_CENTER_FIXED_PERCENT}. The ratio of the view's height
         * at which to place the scroll center from the top.
         */
        private float mScrollCenterOffsetPercent = -1;

        /** represents position information of child views, see {@link ItemWindow} */
        public static class Item {

            private int mIndex;
            private int mLow;
            private int mHigh;
            private int mCenter;

            public Item() {
                mIndex = -1;
            }

            final public int getLow() {
                return mLow;
            }

            final public int getHigh() {
                return mHigh;
            }

            final public int getCenter() {
                return mCenter;
            }

            final public int getIndex() {
                return mIndex;
            }

            /** set low bound, high bound and index for the item */
            final public void setValue(int index, int low, int high) {
                mIndex = index;
                mLow = low;
                mHigh = high;
                mCenter = (low + high) / 2;
            }

            final public boolean isValid() {
                return mIndex >= 0;
            }

            @Override
            final public String toString() {
                return mIndex + "[" + mLow + "," + mHigh + "]";
            }
        }

        private int mSize;

        private int mPaddingLow;

        private int mPaddingHigh;

        private final Lerper mLerper;

        private final String mName; // for debugging

        public Axis(Lerper lerper, String name) {
            mScrollCenterStrategy = SCROLL_CENTER_IN_MIDDLE;
            mLerper = lerper;
            reset();
            mName = name;
        }

        final public int getScrollCenterStrategy() {
            return mScrollCenterStrategy;
        }

        final public void setScrollCenterStrategy(int scrollCenterStrategy) {
            mScrollCenterStrategy = scrollCenterStrategy;
        }

        final public int getScrollCenterOffset() {
            return mScrollCenterOffset;
        }

        final public void setScrollCenterOffset(int scrollCenterOffset) {
            mScrollCenterOffset = scrollCenterOffset;
        }

        final public void setScrollCenterOffsetPercent(int scrollCenterOffsetPercent) {
            if (scrollCenterOffsetPercent < 0) {
                scrollCenterOffsetPercent = 0;
            } else if (scrollCenterOffsetPercent > 100) {
                scrollCenterOffsetPercent = 100;
            }
            mScrollCenterOffsetPercent =  ( scrollCenterOffsetPercent / 100.0f);
        }

        final public void setSelectedTakesMoreSpace(boolean selectedTakesMoreSpace) {
            mSelectedTakesMoreSpace = selectedTakesMoreSpace;
        }

        final public boolean getSelectedTakesMoreSpace() {
            return mSelectedTakesMoreSpace;
        }

        final public void setScrollItemAlign(int align) {
            mScrollItemAlign = align;
        }

        final public int getScrollItemAlign() {
            return mScrollItemAlign;
        }

        final public int getScrollCenter() {
            return (int) mScrollCenter;
        }

        final public void setOperationMode(int mode) {
            mOperationMode = mode;
        }

        private int scrollMin() {
            return mOperationMode == OPERATION_TOUCH ? mTouchScrollMin : mScrollMin;
        }

        private int scrollMax() {
            return mOperationMode == OPERATION_TOUCH ? mTouchScrollMax : mScrollMax;
        }

        /** update scroll min and minEdge,  Integer.MIN_VALUE means unknown*/
        final public void updateScrollMin(int scrollMin, int minEdge) {
            mScrollMin = scrollMin;
            if (mScrollCenter < mScrollMin) {
                mScrollCenter = mScrollMin;
            }
            mMinEdge = minEdge;
            if (mScrollCenterStrategy != SCROLL_CENTER_IN_MIDDLE
                    || mScrollMin == Integer.MIN_VALUE) {
                mTouchScrollMin = mScrollMin;
            } else {
                mTouchScrollMin = Math.max(mScrollMin, mMinEdge + mSize / 2);
            }
        }

        public void invalidateScrollMin() {
            mScrollMin = Integer.MIN_VALUE;
            mMinEdge = Integer.MIN_VALUE;
            mTouchScrollMin = Integer.MIN_VALUE;
        }

        /** update scroll max and maxEdge,  Integer.MAX_VALUE means unknown*/
        final public void updateScrollMax(int scrollMax, int maxEdge) {
            mScrollMax = scrollMax;
            if (mScrollCenter > mScrollMax) {
                mScrollCenter = mScrollMax;
            }
            mMaxEdge = maxEdge;
            if (mScrollCenterStrategy != SCROLL_CENTER_IN_MIDDLE
                    || mScrollMax == Integer.MAX_VALUE) {
                mTouchScrollMax = mScrollMax;
            } else {
                mTouchScrollMax = Math.min(mScrollMax, mMaxEdge - mSize / 2);
            }
        }

        public void invalidateScrollMax() {
            mScrollMax = Integer.MAX_VALUE;
            mMaxEdge = Integer.MAX_VALUE;
            mTouchScrollMax = Integer.MAX_VALUE;
        }

        final public boolean canScroll(boolean forward) {
            if (forward) {
                if (mScrollCenter >= mScrollMax) {
                    return false;
                }
            } else {
                if (mScrollCenter <= mScrollMin) {
                    return false;
                }
            }
            return true;
        }

        private boolean updateScrollCenter(float scrollTarget, boolean lerper) {
            mDragOffset = 0;
            int scrollMin = scrollMin();
            int scrollMax = scrollMax();
            boolean overScroll = false;
            if (scrollMin >= scrollMax) {
                scrollTarget = mScrollCenter;
                overScroll = true;
            } else if (scrollTarget < scrollMin) {
                scrollTarget = scrollMin;
                overScroll = true;
            } else if (scrollTarget > scrollMax) {
                scrollTarget = scrollMax;
                overScroll = true;
            }
            if (lerper) {
                mScrollCenter = mLerper.getValue(mScrollCenter, scrollTarget);
            } else {
                mScrollCenter = scrollTarget;
            }
            return overScroll;
        }

        private void updateFromDrag() {
            updateScrollCenter(mScrollCenter + mDragOffset, false);
        }

        private void dragBy(float distanceX) {
            mDragOffset += distanceX;
        }

        private void reset() {
            mScrollCenter = Integer.MIN_VALUE;
            mScrollMin = Integer.MIN_VALUE;
            mMinEdge = Integer.MIN_VALUE;
            mTouchScrollMin = Integer.MIN_VALUE;
            mScrollMax = Integer.MAX_VALUE;
            mMaxEdge = Integer.MAX_VALUE;
            mTouchScrollMax = Integer.MAX_VALUE;
            mExpandedSize = 0;
            mDragOffset = 0;
        }

        final public boolean isMinUnknown() {
            return mScrollMin == Integer.MIN_VALUE;
        }

        final public boolean isMaxUnknown() {
            return mScrollMax == Integer.MAX_VALUE;
        }

        final public int getSizeForExpandableItem() {
            return mSize - mPaddingLow - mPaddingHigh - mExpandedSize;
        }

        final public void setSize(int size) {
            mSize = size;
        }

        final public void setExpandedSize(int expandedSize) {
            mExpandedSize = expandedSize;
        }

        final public void setExtraSpaceLow(int extraSpaceLow) {
            mExtraSpaceLow = extraSpaceLow;
        }

        final public void setExtraSpaceHigh(int extraSpaceHigh) {
            mExtraSpaceHigh = extraSpaceHigh;
        }

        final public void setAlignExtraOffset(int extraOffset) {
            mAlignExtraOffset = extraOffset;
        }

        final public void setPadding(int paddingLow, int paddingHigh) {
            mPaddingLow = paddingLow;
            mPaddingHigh = paddingHigh;
        }

        final public int getPaddingLow() {
            return mPaddingLow;
        }

        final public int getPaddingHigh() {
            return mPaddingHigh;
        }

        final public int getSystemScrollPos() {
            return getSystemScrollPos((int) mScrollCenter);
        }

        final public int getSystemScrollPos(int scrollCenter) {
            scrollCenter += mAlignExtraOffset;

            // For the "FIXED" strategy family:
            int compensate = mSelectedTakesMoreSpace ? mExtraSpaceLow : -mExtraSpaceLow;
            if (mScrollCenterStrategy == SCROLL_CENTER_FIXED) {
                return scrollCenter - mScrollCenterOffset + compensate;
            } else if (mScrollCenterStrategy == SCROLL_CENTER_FIXED_TO_END) {
                return scrollCenter - (mSize - mScrollCenterOffset) + compensate;
            } else if (mScrollCenterStrategy == SCROLL_CENTER_FIXED_PERCENT) {
                return (int) (scrollCenter - mScrollCenterOffset - mSize
                        * mScrollCenterOffsetPercent) + compensate;
            }
            int clientSize = mSize - mPaddingLow - mPaddingHigh;
            // For SCROLL_CENTER_IN_MIDDLE, first calculate the middle point:
            // if the scrollCenterOffset or scrollCenterOffsetPercent is specified,
            // use it for middle point,  otherwise, use 1/2 of the size
            int middlePosition;
            if (mScrollCenterOffset >= 0) {
                middlePosition = mScrollCenterOffset - mPaddingLow;
            } else if (mScrollCenterOffsetPercent >= 0) {
                middlePosition = (int) (mSize * mScrollCenterOffsetPercent) - mPaddingLow;
            } else {
                middlePosition = clientSize / 2;
            }
            int afterMiddlePosition = clientSize - middlePosition;
            // Following code for mSelectedTakesMoreSpace = true/false is quite similar,
            // but it's still more clear and easier to debug when separating them.
            boolean isMinUnknown = isMinUnknown();
            boolean isMaxUnknown = isMaxUnknown();
            if (mSelectedTakesMoreSpace) {
                int extraSpaceLow;
                switch (getScrollItemAlign()) {
                    case SCROLL_ITEM_ALIGN_LOW:
                        extraSpaceLow = 0;
                        break;
                    case SCROLL_ITEM_ALIGN_HIGH:
                        extraSpaceLow = mExtraSpaceLow + mExtraSpaceHigh;
                        break;
                    case SCROLL_ITEM_ALIGN_CENTER:
                    default:
                        extraSpaceLow = mExtraSpaceLow;
                        break;
                }
                if (!isMinUnknown && !isMaxUnknown) {
                    if (mMaxEdge - mMinEdge + mExpandedSize <= clientSize) {
                        // total children size is less than view port: align the left edge
                        // of first child to view port's left edge
                        return mMinEdge - mPaddingLow;
                    }
                }
                if (!isMinUnknown) {
                    if (scrollCenter - mMinEdge + extraSpaceLow <= middlePosition) {
                        // scroll center is within half of view port size: align the left edge
                        // of first child to the left edge of view port
                        return mMinEdge - mPaddingLow;
                    }
                }
                if (!isMaxUnknown) {
                    int spaceAfterScrollCenter = mExpandedSize - extraSpaceLow;
                    if (mMaxEdge - scrollCenter + spaceAfterScrollCenter <= afterMiddlePosition) {
                        // scroll center is very close to the right edge of view port : align the
                        // right edge of last children (plus expanded size) to view port's right
                        return mMaxEdge -mPaddingLow - (clientSize - mExpandedSize );
                    }
                }
                // else put scroll center in middle of view port
                return scrollCenter - middlePosition - mPaddingLow + extraSpaceLow;
            } else {
                int shift;
                switch (getScrollItemAlign()) {
                    case SCROLL_ITEM_ALIGN_LOW:
                        shift = - mExtraSpaceLow;
                        break;
                    case SCROLL_ITEM_ALIGN_HIGH:
                        shift = + mExtraSpaceHigh;
                        break;
                    case SCROLL_ITEM_ALIGN_CENTER:
                    default:
                        shift = 0;
                        break;
                }
                if (!isMinUnknown && !isMaxUnknown) {
                    if (mMaxEdge - mMinEdge + mExpandedSize <= clientSize) {
                        // total children size is less than view port: align the left edge
                        // of first child to view port's left edge
                        return mMinEdge - mPaddingLow;
                    }
                }
                if (!isMinUnknown) {
                    if (scrollCenter + shift - mMinEdge <= middlePosition) {
                        // scroll center is within half of view port size: align the left edge
                        // of first child to the left edge of view port
                        return mMinEdge - mPaddingLow;
                    }
                }
                if (!isMaxUnknown) {
                    if (mMaxEdge - scrollCenter - shift + mExpandedSize <= afterMiddlePosition) {
                        // scroll center is very close to the right edge of view port : align the
                        // right edge of last children (plus expanded size) to view port's right
                        return mMaxEdge -mPaddingLow - (clientSize - mExpandedSize );
                    }
                }
                // else put scroll center in middle of view port
                return scrollCenter - middlePosition - mPaddingLow + shift;
            }
        }

        @Override
        public String toString() {
            return "center: " + mScrollCenter + " min:" + mMinEdge + "," + mScrollMin +
                    " max:" + mScrollMax + "," + mMaxEdge;
        }

    }

    private final Context mContext;

    // we separate Scrollers for scroll animation and fling animation; this is because we want a
    // flywheel feature for fling animation, ScrollAdapterView inserts scroll animation between
    // fling animations, the fling animation will mistakenly continue the old velocity of scroll
    // animation: that's wrong, we want fling animation pickup the old velocity of last fling.
    private final Scroller mScrollScroller;
    private final Scroller mFlingScroller;

    private final static int STATE_NONE = 0;

    /** using fling scroller */
    private final static int STATE_FLING = 1;

    /** using scroll scroller */
    private final static int STATE_SCROLL = 2;

    /** using drag */
    private final static int STATE_DRAG = 3;

    private int mState = STATE_NONE;

    private int mOrientation = ScrollAdapterView.HORIZONTAL;

    private final Lerper mLerper = new Lerper();

    final public Axis vertical = new Axis(mLerper, "vertical");

    final public Axis horizontal = new Axis(mLerper, "horizontal");

    private Axis mMainAxis = horizontal;

    private Axis mSecondAxis = vertical;

    /** fling operation mode */
    private int mFlingMode = OPERATION_AUTO;

    /** drag operation mode */
    private int mDragMode = OPERATION_AUTO;

    /** scroll operation mode (for DPAD) */
    private int mScrollMode = OPERATION_NOTOUCH;

    /** the major movement is in horizontal or vertical */
    private boolean mMainHorizontal;
    private boolean mHorizontalForward = true;
    private boolean mVerticalForward = true;

    final public Lerper lerper() {
        return mLerper;
    }

    final public Axis mainAxis() {
        return mMainAxis;
    }

    final public Axis secondAxis() {
        return mSecondAxis;
    }

    final public void setLerperDivisor(float divisor) {
        mLerper.setDivisor(divisor);
    }

    public ScrollController(Context context) {
        mContext = context;
        // Quint easeOut
        mScrollScroller = new Scroller(mContext, new DecelerateInterpolator(2));
        mFlingScroller = new Scroller(mContext, new LinearInterpolator());
    }

    final public void setOrientation(int orientation) {
        int align = mainAxis().getScrollItemAlign();
        boolean selectedTakesMoreSpace = mainAxis().getSelectedTakesMoreSpace();
        mOrientation = orientation;
        if (mOrientation == ScrollAdapterView.HORIZONTAL) {
            mMainAxis = horizontal;
            mSecondAxis = vertical;
        } else {
            mMainAxis = vertical;
            mSecondAxis = horizontal;
        }
        mMainAxis.setScrollItemAlign(align);
        mSecondAxis.setScrollItemAlign(SCROLL_ITEM_ALIGN_CENTER);
        mMainAxis.setSelectedTakesMoreSpace(selectedTakesMoreSpace);
        mSecondAxis.setSelectedTakesMoreSpace(false);
    }

    public void setScrollItemAlign(int align) {
        mainAxis().setScrollItemAlign(align);
    }

    public int getScrollItemAlign() {
        return mainAxis().getScrollItemAlign();
    }

    final public int getOrientation() {
        return mOrientation;
    }

    final public int getFlingMode() {
        return mFlingMode;
    }

    final public void setFlingMode(int mode) {
        this.mFlingMode = mode;
    }

    final public int getDragMode() {
        return mDragMode;
    }

    final public void setDragMode(int mode) {
        this.mDragMode = mode;
    }

    final public int getScrollMode() {
        return mScrollMode;
    }

    final public void setScrollMode(int mode) {
        this.mScrollMode = mode;
    }

    final public float getCurrVelocity() {
        if (mState == STATE_FLING) {
            return mFlingScroller.getCurrVelocity();
        } else if (mState == STATE_SCROLL) {
            return mScrollScroller.getCurrVelocity();
        }
        return 0;
    }

    final public boolean canScroll(int dx, int dy) {
        if (dx == 0 && dy == 0) {
            return false;
        }
        return (dx == 0 || horizontal.canScroll(dx < 0)) &&
                (dy == 0 || vertical.canScroll(dy < 0));
    }

    private int getMode(int mode) {
        if (mode == OPERATION_AUTO) {
            if (mContext.getResources().getConfiguration().touchscreen
                    == Configuration.TOUCHSCREEN_NOTOUCH) {
                mode = OPERATION_NOTOUCH;
            } else {
                mode = OPERATION_TOUCH;
            }
        }
        return mode;
    }

    private void updateDirection(float dx, float dy) {
        mMainHorizontal = Math.abs(dx) >= Math.abs(dy);
        if (dx > 0) {
            mHorizontalForward = true;
        } else if (dx < 0) {
            mHorizontalForward = false;
        }
        if (dy > 0) {
            mVerticalForward = true;
        } else if (dy < 0) {
            mVerticalForward = false;
        }
    }

    final public boolean fling(int velocity_x, int velocity_y){
        if (mFlingMode == OPERATION_DISABLE) {
            return false;
        }
        final int operationMode = getMode(mFlingMode);
        horizontal.setOperationMode(operationMode);
        vertical.setOperationMode(operationMode);
        mState = STATE_FLING;
        mFlingScroller.fling((int)(horizontal.mScrollCenter),
                (int)(vertical.mScrollCenter),
                velocity_x,
                velocity_y,
                Integer.MIN_VALUE,
                Integer.MAX_VALUE,
                Integer.MIN_VALUE,
                Integer.MAX_VALUE);
        updateDirection(velocity_x, velocity_y);
        return true;
    }

    final public void startScroll(int dx, int dy, boolean easeFling, int duration, boolean page) {
        if (mScrollMode == OPERATION_DISABLE) {
            return;
        }
        final int operationMode = getMode(mScrollMode);
        horizontal.setOperationMode(operationMode);
        vertical.setOperationMode(operationMode);
        Scroller scroller;
        if (easeFling) {
            mState = STATE_FLING;
            scroller = mFlingScroller;
        } else {
            mState = STATE_SCROLL;
            scroller = mScrollScroller;
        }
        int basex = horizontal.getScrollCenter();
        int basey = vertical.getScrollCenter();
        if (!scroller.isFinished()) {
            // during scrolling, we should continue from getCurrX/getCurrY() (might be different
            // than current Scroll Center due to Lerper)
            dx = basex + dx - scroller.getCurrX();
            dy = basey + dy - scroller.getCurrY();
            basex = scroller.getCurrX();
            basey = scroller.getCurrY();
        }
        updateDirection(dx, dy);
        if (easeFling) {
            float curDx = Math.abs(mFlingScroller.getFinalX() - mFlingScroller.getStartX());
            float curDy = Math.abs(mFlingScroller.getFinalY() - mFlingScroller.getStartY());
            float hyp = (float) Math.sqrt(curDx * curDx + curDy * curDy);
            float velocity = mFlingScroller.getCurrVelocity();
            float velocityX = velocity * curDx / hyp;
            float velocityY = velocity * curDy / hyp;
            int durationX = velocityX ==0 ? 0 : (int)((Math.abs(dx) * 1000) / velocityX);
            int durationY = velocityY ==0 ? 0 : (int)((Math.abs(dy) * 1000) / velocityY);
            if (duration == 0) duration = Math.max(durationX, durationY);
        } else {
            if (duration == 0) {
                duration = getScrollDuration((int) Math.sqrt(dx * dx + dy * dy), page);
            }
        }
        scroller.startScroll(basex, basey, dx, dy, duration);
    }

    final public int getCurrentAnimationDuration() {
        Scroller scroller;
        if (mState == STATE_FLING) {
            scroller = mFlingScroller;
        } else if (mState == STATE_SCROLL) {
            scroller = mScrollScroller;
        } else {
            return 0;
        }
        return scroller.getDuration();
    }

    final public void startScrollByMain(int deltaMain, int deltaSecond, boolean easeFling,
            int duration, boolean page) {
        int dx, dy;
        if (mOrientation == ScrollAdapterView.HORIZONTAL) {
            dx = deltaMain;
            dy = deltaSecond;
        } else {
            dx = deltaSecond;
            dy = deltaMain;
        }
        startScroll(dx, dy, easeFling, duration, page);
    }

    final public boolean dragBy(float distanceX, float distanceY) {
        if (mDragMode == OPERATION_DISABLE) {
            return false;
        }
        final int operationMode = getMode(mDragMode);
        horizontal.setOperationMode(operationMode);
        vertical.setOperationMode(operationMode);
        horizontal.dragBy(distanceX);
        vertical.dragBy(distanceY);
        mState = STATE_DRAG;
        return true;
    }

    final public void stopDrag() {
        mState = STATE_NONE;
        vertical.mDragOffset = 0;
        horizontal.mDragOffset = 0;
    }

    final public void setScrollCenterByMain(int centerMain, int centerSecond) {
        if (mOrientation == ScrollAdapterView.HORIZONTAL) {
            setScrollCenter(centerMain, centerSecond);
        } else {
            setScrollCenter(centerSecond, centerMain);
        }
    }

    final public void setScrollCenter(int centerX, int centerY) {
        horizontal.updateScrollCenter(centerX, false);
        vertical.updateScrollCenter(centerY, false);
        // centerX, centerY might be clipped by min/max
        centerX = horizontal.getScrollCenter();
        centerY = vertical.getScrollCenter();
        mFlingScroller.setFinalX(centerX);
        mFlingScroller.setFinalY(centerY);
        mFlingScroller.abortAnimation();
        mScrollScroller.setFinalX(centerX);
        mScrollScroller.setFinalY(centerY);
        mScrollScroller.abortAnimation();
    }

    final public int getFinalX() {
        if (mState == STATE_FLING) {
            return mFlingScroller.getFinalX();
        } else if (mState == STATE_SCROLL) {
            return mScrollScroller.getFinalX();
        }
        return horizontal.getScrollCenter();
    }

    final public int getFinalY() {
        if (mState == STATE_FLING) {
            return mFlingScroller.getFinalY();
        } else if (mState == STATE_SCROLL) {
            return mScrollScroller.getFinalY();
        }
        return vertical.getScrollCenter();
    }

    final public void setFinalX(int finalX) {
        if (mState == STATE_FLING) {
            mFlingScroller.setFinalX(finalX);
        } else if (mState == STATE_SCROLL) {
            mScrollScroller.setFinalX(finalX);
        }
    }

    final public void setFinalY(int finalY) {
        if (mState == STATE_FLING) {
            mFlingScroller.setFinalY(finalY);
        } else if (mState == STATE_SCROLL) {
            mScrollScroller.setFinalY(finalY);
        }
    }

    /** return true if scroll/fling animation or lerper is not stopped */
    final public boolean isFinished() {
        Scroller scroller;
        if (mState == STATE_FLING) {
            scroller = mFlingScroller;
        } else if (mState == STATE_SCROLL) {
            scroller = mScrollScroller;
        } else if (mState == STATE_DRAG){
            return false;
        } else {
            return true;
        }
        if (scroller.isFinished()) {
            return (horizontal.getScrollCenter() == scroller.getCurrX() &&
                    vertical.getScrollCenter() == scroller.getCurrY());
        }
        return false;
    }

    final public boolean isMainAxisMovingForward() {
        return mOrientation == ScrollAdapterView.HORIZONTAL ?
                mHorizontalForward : mVerticalForward;
    }

    final public boolean isSecondAxisMovingForward() {
        return mOrientation == ScrollAdapterView.HORIZONTAL ?
                mVerticalForward : mHorizontalForward;
    }

    final public int getLastDirection() {
        if (mMainHorizontal) {
            return mHorizontalForward ? View.FOCUS_RIGHT : View.FOCUS_LEFT;
        } else {
            return mVerticalForward ? View.FOCUS_DOWN : View.FOCUS_UP;
        }
    }

    /**
     * update scroller position, this is either trigger by fling()/startScroll() on the
     * scroller object,  or lerper, or can be caused by a dragBy()
     */
    final public void computeAndSetScrollPosition() {
        Scroller scroller;
        if (mState == STATE_FLING) {
            scroller = mFlingScroller;
        } else if (mState == STATE_SCROLL) {
            scroller = mScrollScroller;
        } else if (mState == STATE_DRAG) {
            if (horizontal.mDragOffset != 0 || vertical.mDragOffset !=0 ) {
                horizontal.updateFromDrag();
                vertical.updateFromDrag();
            }
            return;
        } else {
            return;
        }
        if (!isFinished()) {
            scroller.computeScrollOffset();
            horizontal.updateScrollCenter(scroller.getCurrX(), true);
            vertical.updateScrollCenter(scroller.getCurrY(), true);
        }
    }

    /** get Scroll animation duration in ms for given pixels */
    final public int getScrollDuration(int distance, boolean isPage) {
        int ms = (int)(distance * SCROLL_DURATION_MS_PER_PIX);
        int minValue = isPage ? SCROLL_DURATION_PAGE_MIN : SCROLL_DURATION_MIN;
        if (ms < minValue) {
            ms = minValue;
        } else if (ms > SCROLL_DURATION_MAX) {
            ms = SCROLL_DURATION_MAX;
        }
        return ms;
    }

    final public void reset() {
        mainAxis().reset();
    }

    @Override
    public String toString() {
        return new StringBuffer().append("horizontal=")
                .append(horizontal.toString())
                .append("vertical=")
                .append(vertical.toString())
                .toString();
    }

}
