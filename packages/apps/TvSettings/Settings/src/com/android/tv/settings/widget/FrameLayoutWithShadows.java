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
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RectShape;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewDebug.ExportedProperty;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.FrameLayout;
import android.widget.ImageView;

import com.android.tv.settings.R;

import java.util.ArrayList;

/**
 * Allows a drawable to be added for shadowing views in this layout. The shadows
 * will automatically be sized to wrap their corresponding view. The default
 * drawable to use can be set in xml by defining the namespace and then using
 * defaultShadow="@drawable/reference"
 * <p>
 * In code views can then have Shadows added to them via
 * {@link #addShadowView(View)} to use the default drawable or with
 * {@link #addShadowView(View, Drawable)}.
 */
public class FrameLayoutWithShadows extends FrameLayout {

    private static final int MAX_RECYCLE = 12;

    static class ShadowView extends View {

        private View shadowedView;
        private Drawable mDrawableBottom;
        private float mAlpha = 1f;

        ShadowView(Context context) {
            super(context);
            setWillNotDraw(false);
        }

        void init() {
            shadowedView = null;
            mDrawableBottom = null;
        }

        @Override
        public void setBackground(Drawable background) {
            super.setBackground(background);
            if (background != null) {
                // framework adds a callback on background to trigger a repaint
                // when call Drawable.setAlpha(),  this is not desired when we override
                // setAlpha();  if we call Drawable.setAlpha() in the overriden
                // setAlpha(),  it will trigger another repaint event thus cause system
                // never stop rendering.
                background.setCallback(null);
                background.setAlpha((int)(255 * mAlpha));
            }
        }

        @Override
        public void setAlpha(float alpha) {
            if (mAlpha != alpha) {
                mAlpha = alpha;
                Drawable d = getBackground();
                int alphaMulitplied = (int)(alpha * 255);
                if (d != null) {
                    d.setAlpha(alphaMulitplied);
                }
                if (mDrawableBottom != null) {
                    mDrawableBottom.setAlpha(alphaMulitplied);
                }
                invalidate();
            }
        }

        @Override
        @ExportedProperty(category = "drawing")
        public float getAlpha() {
            return mAlpha;
        }

        @Override
        protected boolean onSetAlpha(int alpha) {
            return true;
        }

        public void setDrawableBottom(Drawable drawable) {
            mDrawableBottom = drawable;
            if (mAlpha >= 0) {
                mDrawableBottom.setAlpha((int)(255 * mAlpha));
            }
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            // draw background 9 patch
            super.onDraw(canvas);
            // draw bottom
            if (mDrawableBottom != null) {
                mDrawableBottom.setBounds(getPaddingLeft(), getHeight() - getPaddingBottom(),
                        getWidth() - getPaddingRight(), getHeight() - getPaddingBottom()
                        + mDrawableBottom.getIntrinsicHeight());
                mDrawableBottom.draw(canvas);
            }
        }
    }

    private final Rect rect = new Rect();
    private final RectF rectf = new RectF();
    private int mShadowResourceId;
    private int mBottomResourceId;
    private float mShadowsAlpha = 1f;
    private final ArrayList<ShadowView> mRecycleBin = new ArrayList<>(MAX_RECYCLE);

    public FrameLayoutWithShadows(Context context) {
        this(context, null);
    }

    public FrameLayoutWithShadows(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FrameLayoutWithShadows(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        initFromAttributes(context, attrs);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        layoutShadows();
    }

    private void initFromAttributes(Context context, AttributeSet attrs) {
        if (attrs == null) {
            return;
        }
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.FrameLayoutWithShadows);

        setDefaultShadowResourceId(a.getResourceId(
                R.styleable.FrameLayoutWithShadows_defaultShadow, 0));
        setDrawableBottomResourceId(a.getResourceId(
                R.styleable.FrameLayoutWithShadows_drawableBottom, 0));

        a.recycle();
    }

    public void setDefaultShadowResourceId(int id) {
        mShadowResourceId = id;
    }

    public int getDefaultShadowResourceId() {
        return mShadowResourceId;
    }

    public void setDrawableBottomResourceId(int id) {
        mBottomResourceId = id;
    }

    public int getDrawableBottomResourceId() {
        return mBottomResourceId;
    }

    public void setShadowsAlpha(float alpha) {
        mShadowsAlpha = alpha;
        for (int i = getChildCount() - 1; i >= 0; i--) {
            View shadow = getChildAt(i);
            if (shadow instanceof ShadowView) {
                shadow.setAlpha(alpha);
            }
        }
    }

    /**
     * prune shadow views whose related view was detached from FrameLayoutWithShadows
     */
    private void prune() {
        if (getWindowToken() ==null) {
            return;
        }
        for (int i = getChildCount() - 1; i >= 0; i--) {
            View shadow = getChildAt(i);
            if (shadow instanceof ShadowView) {
                ShadowView shadowView = (ShadowView) shadow;
                View view = shadowView.shadowedView;
                if (this != findParentShadowsView(view)) {
                    view.setTag(R.id.ShadowView, null);
                    shadowView.shadowedView = null;
                    removeView(shadowView);
                    addToRecycleBin(shadowView);
                }
            }
        }
    }

    /**
     * Perform a layout of the shadow views. This is done as part of the layout
     * pass for the view but may also be triggered manually if the borders of a
     * child view has changed.
     */
    public void layoutShadows() {
        prune();
        for (int i = getChildCount() - 1; i >= 0; i--) {
            View shadow = getChildAt(i);
            if (!(shadow instanceof ShadowView)) {
                continue;
            }
            ShadowView shadowView = (ShadowView) shadow;
            View view = shadowView.shadowedView;
            if (view != null) {
                if (this != findParentShadowsView(view)) {
                    continue;
                }
                boolean isImageMatrix = false;
                if (view instanceof ImageView) {
                    // For ImageView, we get the draw bounds of the image drawable,
                    // which could be smaller than the imageView depending on ScaleType.
                    Matrix matrix = ((ImageView) view).getImageMatrix();
                    Drawable drawable = ((ImageView) view).getDrawable();
                    if (drawable != null) {
                        isImageMatrix = true;
                        rect.set(drawable.getBounds());
                        rectf.set(rect);
                        matrix.mapRect(rectf);
                        rectf.offset(view.getPaddingLeft(), view.getPaddingTop());
                        rectf.intersect(view.getPaddingLeft(), view.getPaddingTop(),
                                view.getWidth() - view.getPaddingLeft() - view.getPaddingRight(),
                                view.getHeight() - view.getPaddingTop() - view.getPaddingBottom());
                        rectf.left -= shadow.getPaddingLeft();
                        rectf.top -= shadow.getPaddingTop();
                        rectf.right += shadow.getPaddingRight();
                        rectf.bottom += shadow.getPaddingBottom();
                        rect.left = (int) (rectf.left + 0.5f);
                        rect.top = (int) (rectf.top + 0.5f);
                        rect.right = (int) (rectf.right + 0.5f);
                        rect.bottom = (int) (rectf.bottom + 0.5f);
                    }
                }
                if (!isImageMatrix){
                    rect.left = view.getPaddingLeft() - shadow.getPaddingLeft();
                    rect.top = view.getPaddingTop() - shadow.getPaddingTop();
                    rect.right = view.getWidth() + view.getPaddingRight()
                            + shadow.getPaddingRight();
                    rect.bottom = view.getHeight() + view.getPaddingBottom()
                            + shadow.getPaddingBottom();
                }
                offsetDescendantRectToMyCoords(view, rect);
                shadow.layout(rect.left, rect.top, rect.right, rect.bottom);
            }
        }
    }

    /**
     * Add a shadow view to FrameLayoutWithShadows. This will use the drawable
     * specified for the shadow view and will also handle clean-up of any
     * previous shadow set for this view.
     */
    public View addShadowView(View view, Drawable shadow) {
        ShadowView shadowView = (ShadowView) view.getTag(R.id.ShadowView);
        if (shadowView == null) {
            shadowView = getFromRecycleBin();
            if (shadowView == null) {
                shadowView = new ShadowView(getContext());
                shadowView.setLayoutParams(new LayoutParams(0, 0));
            }
            view.setTag(R.id.ShadowView, shadowView);
            shadowView.shadowedView = view;
            addView(shadowView, 0);
        }
        shadow.mutate();
        shadowView.setAlpha(mShadowsAlpha);
        shadowView.setBackground(shadow);
        if (mBottomResourceId != 0) {
            Drawable d = getContext().getDrawable(mBottomResourceId);
            shadowView.setDrawableBottom(d.mutate());
        }
        return shadowView;
    }

    /**
     * Add a shadow view using the default shadow. This will also handle
     * clean-up of any previous shadow set for this view.
     */
    public View addShadowView(View view) {
        final Drawable shadow;
        if (mShadowResourceId != 0) {
            shadow = getContext().getDrawable(mShadowResourceId);
        } else {
            return null;
        }
        return addShadowView(view, shadow);
    }

    /**
     * Get the shadow associated with the given view. Returns null if the view
     * does not have a shadow.
     */
    public static View getShadowView(View view) {
        View shadowView = (View) view.getTag(R.id.ShadowView);
        if (shadowView != null) {
            return shadowView;
        }
        return null;
    }

    public void setShadowViewUnderline(View shadowView, int underlineColor, int heightInPx) {
        ShapeDrawable drawable = new ShapeDrawable();
        drawable.setShape(new RectShape());
        drawable.setIntrinsicHeight(heightInPx);
        drawable.getPaint().setColor(underlineColor);
        ((ShadowView) shadowView).setDrawableBottom(drawable);
    }

    public void setShadowViewUnderline(View shadowView, Drawable drawable) {
        ((ShadowView) shadowView).setDrawableBottom(drawable);
    }

    /**
     * Makes the shadow associated with the given view draw above other views.
     * Subsequent calls to this or changes to the z-order may move the shadow
     * back down in the z-order.
     */
    public void bringViewShadowToTop(View view) {
        View shadowView = (View) view.getTag(R.id.ShadowView);
        if (shadowView == null) {
            return;
        }
        int index = indexOfChild(shadowView);
        if (index < 0) {
            // not found
            return;
        }
        int lastIndex = getChildCount() - 1;
        if (lastIndex == index) {
            // already last one
            return;
        }
        View lastShadowView = getChildAt(lastIndex);
        if (!(lastShadowView instanceof ShadowView)) {
            removeView(shadowView);
            addView(shadowView);
        } else {
            removeView(lastShadowView);
            removeView(shadowView);
            addView(lastShadowView, 0);
            addView(shadowView);
        }
    }

    /**
     * Utility function to remove the shadow associated with the given view.
     */
    public static void removeShadowView(View view) {
        ShadowView shadowView = (ShadowView) view.getTag(R.id.ShadowView);
        if (shadowView != null) {
            view.setTag(R.id.ShadowView, null);
            shadowView.shadowedView = null;
            if (shadowView.getRootView() != null) {
                ViewParent parent = shadowView.getParent();
                if (parent instanceof ViewGroup) {
                    ((ViewGroup) parent).removeView(shadowView);
                    if (parent instanceof FrameLayoutWithShadows) {
                        ((FrameLayoutWithShadows) parent).addToRecycleBin(shadowView);
                    }
                }
            }
        }
    }

    private void addToRecycleBin(ShadowView shadowView) {
        if (mRecycleBin.size() < MAX_RECYCLE) {
            mRecycleBin.add(shadowView);
        }
    }

    public ShadowView getFromRecycleBin() {
        int size = mRecycleBin.size();
        if (size > 0) {
            ShadowView view = mRecycleBin.remove(size - 1);
            view.init();
        }
        return null;
    }

    /**
     * Sets the visibility of the shadow associated with the given view. This
     * should be called when the view's visibility changes to keep the shadow's
     * visibility in sync.
     */
    public void setShadowVisibility(View view, int visibility) {
        View shadowView = (View) view.getTag(R.id.ShadowView);
        if (shadowView != null) {
            shadowView.setVisibility(visibility);
            return;
        }
    }

    /**
     * Finds the first parent of this view that is a FrameLayoutWithShadows and
     * returns that or null if there is none.
     */
    public static FrameLayoutWithShadows findParentShadowsView(View view) {
        ViewParent nextView = view.getParent();
        while (nextView != null && !(nextView instanceof FrameLayoutWithShadows)) {
            nextView = nextView.getParent();
        }
        return (FrameLayoutWithShadows) nextView;
    }
}
