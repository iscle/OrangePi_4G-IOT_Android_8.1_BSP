/*
 * Copyright (C) 2008 The Android Open Source Project
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

package android.widget.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.graphics.Rect;
import android.os.SystemClock;
import android.support.test.InstrumentationRegistry;
import android.support.test.annotation.UiThreadTest;
import android.support.test.filters.MediumTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.test.ViewAsserts;
import android.util.AttributeSet;
import android.util.Xml;
import android.view.ContextMenu;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.animation.Transformation;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Gallery;
import android.widget.ImageView;

import com.android.compatibility.common.util.WidgetTestUtils;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;

/**
 * Test {@link Gallery}.
 */
@MediumTest
@RunWith(AndroidJUnit4.class)
public class GalleryTest  {
    private final static float DELTA = 0.01f;

    private Instrumentation mInstrumentation;
    private Activity mActivity;
    private Gallery mGallery;

    @Rule
    public ActivityTestRule<GalleryCtsActivity> mActivityRule =
            new ActivityTestRule<>(GalleryCtsActivity.class);

    @Before
    public void setup() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mActivity = mActivityRule.getActivity();
        mGallery = (Gallery) mActivity.findViewById(R.id.gallery_test);
    }

    @UiThreadTest
    @Test
    public void testConstructor() {
        new Gallery(mActivity);

        new Gallery(mActivity, null);

        new Gallery(mActivity, null, 0);

        XmlPullParser parser = mActivity.getResources().getXml(R.layout.gallery_test);
        AttributeSet attrs = Xml.asAttributeSet(parser);
        new Gallery(mActivity, attrs);
        new Gallery(mActivity, attrs, 0);
    }

    @UiThreadTest
    @Test(expected=NullPointerException.class)
    public void testConstructorNullContext1() {
        new Gallery(null);
    }

    @UiThreadTest
    @Test(expected=NullPointerException.class)
    public void testConstructorNullContext2() {
        new Gallery(null, null);
    }

    @UiThreadTest
    @Test(expected=NullPointerException.class)
    public void testConstructorNullContext3() {
        new Gallery(null, null, 0);
    }

    private void setSpacingAndCheck(final int spacing) throws Throwable {
        mActivityRule.runOnUiThread(() -> {
            mGallery.setSpacing(spacing);
            mGallery.requestLayout();
        });
        mInstrumentation.waitForIdleSync();

        View v0 = mGallery.getChildAt(0);
        View v1 = mGallery.getChildAt(1);
        assertEquals(v0.getRight() + spacing, v1.getLeft());
    }

    @Test
    public void testSetSpacing() throws Throwable {
        setSpacingAndCheck(0);

        setSpacingAndCheck(5);

        setSpacingAndCheck(-1);
    }

    private void checkUnselectedAlpha(float alpha) {
        final float DEFAULT_ALPHA = 1.0f;
        View v0 = mGallery.getChildAt(0);
        View v1 = mGallery.getChildAt(1);

        mGallery.setUnselectedAlpha(alpha);
        Transformation t = new Transformation();
        ((MyGallery) mGallery).getChildStaticTransformation(v0, t);
        // v0 is selected by default.
        assertEquals(DEFAULT_ALPHA, t.getAlpha(), DELTA);
        ((MyGallery) mGallery).getChildStaticTransformation(v1, t);
        assertEquals(alpha, t.getAlpha(), DELTA);
    }

    @UiThreadTest
    @Test
    public void testSetUnselectedAlpha() {
        checkUnselectedAlpha(0.0f);

        checkUnselectedAlpha(0.5f);
    }

    @UiThreadTest
    @Test
    public void testGenerateLayoutParams() throws XmlPullParserException, IOException {
        final int width = 320;
        final int height = 240;
        LayoutParams lp = new LayoutParams(width, height);
        MyGallery gallery = new MyGallery(mActivity);
        LayoutParams layoutParams = gallery.generateLayoutParams(lp);
        assertEquals(width, layoutParams.width);
        assertEquals(height, layoutParams.height);

        XmlPullParser parser = mActivity.getResources().getXml(R.layout.gallery_test);
        WidgetTestUtils.beginDocument(parser, "LinearLayout");
        AttributeSet attrs = Xml.asAttributeSet(parser);
        mGallery = new Gallery(mActivity, attrs);

        layoutParams = mGallery.generateLayoutParams(attrs);
        assertEquals(LayoutParams.MATCH_PARENT, layoutParams.width);
        assertEquals(LayoutParams.MATCH_PARENT, layoutParams.height);
    }

    @UiThreadTest
    @Test
    public void testDispatchKeyEvent() {
        mGallery = new Gallery(mActivity);
        final KeyEvent validKeyEvent = new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER);
        assertTrue(mGallery.dispatchKeyEvent(validKeyEvent));
        final long time = SystemClock.uptimeMillis();
        final KeyEvent invalidKeyEvent
                = new KeyEvent(time, time, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_A, 5);
        assertFalse(mGallery.dispatchKeyEvent(invalidKeyEvent));
    }

    private void setGalleryGravity(final int gravity) throws Throwable {
        mActivityRule.runOnUiThread(() -> {
            mGallery.setGravity(gravity);
            mGallery.invalidate();
            mGallery.requestLayout();
        });
        mInstrumentation.waitForIdleSync();
    }

    @Test
    public void testSetGravity() throws Throwable {
        setGalleryGravity(Gravity.CENTER_HORIZONTAL);
        View v0 = mGallery.getChildAt(0);
        ViewAsserts.assertHorizontalCenterAligned(mGallery, v0);

        setGalleryGravity(Gravity.TOP);
        v0 = mGallery.getChildAt(0);
        ViewAsserts.assertTopAligned(mGallery, v0, mGallery.getPaddingTop());

        setGalleryGravity(Gravity.BOTTOM);
        v0 = mGallery.getChildAt(0);
        ViewAsserts.assertBottomAligned(mGallery, v0, mGallery.getPaddingBottom());
    }

    @UiThreadTest
    @Test
    public void testCheckLayoutParams() {
        MyGallery gallery = new MyGallery(mActivity);
        ViewGroup.LayoutParams p1 = new ViewGroup.LayoutParams(320, 480);
        assertFalse(gallery.checkLayoutParams(p1));

        Gallery.LayoutParams p2 = new Gallery.LayoutParams(320, 480);
        assertTrue(gallery.checkLayoutParams(p2));
    }

    @UiThreadTest
    @Test
    public void testComputeHorizontalScrollExtent() {
        MyGallery gallery = new MyGallery(mActivity);

        // only one item is considered to be selected.
        assertEquals(1, gallery.computeHorizontalScrollExtent());
    }

    @UiThreadTest
    @Test
    public void testComputeHorizontalScrollOffset() {
        MyGallery gallery = new MyGallery(mActivity);
        assertEquals(AdapterView.INVALID_POSITION, gallery.computeHorizontalScrollOffset());
        gallery.setAdapter(new ImageAdapter(mActivity));

        // Current scroll position is the same as the selected position
        assertEquals(gallery.getSelectedItemPosition(), gallery.computeHorizontalScrollOffset());
    }

    @UiThreadTest
    @Test
    public void testComputeHorizontalScrollRange() {
        MyGallery gallery = new MyGallery(mActivity);
        ImageAdapter adapter = new ImageAdapter(mActivity);
        gallery.setAdapter(adapter);

        // Scroll range is the same as the item count
        assertEquals(adapter.getCount(), gallery.computeHorizontalScrollRange());
    }

    @UiThreadTest
    @Test
    public void testDispatchSetPressed() {
        mGallery.setSelection(0);
        ((MyGallery) mGallery).dispatchSetPressed(true);
        assertTrue(mGallery.getSelectedView().isPressed());
        assertFalse(mGallery.getChildAt(1).isPressed());

        ((MyGallery) mGallery).dispatchSetPressed(false);
        assertFalse(mGallery.getSelectedView().isPressed());
        assertFalse(mGallery.getChildAt(1).isPressed());
    }

    @UiThreadTest
    @Test
    public void testGenerateDefaultLayoutParams() {
        MyGallery gallery = new MyGallery(mActivity);
        ViewGroup.LayoutParams p = gallery.generateDefaultLayoutParams();
        assertNotNull(p);
        assertTrue(p instanceof Gallery.LayoutParams);
        assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, p.width);
        assertEquals(ViewGroup.LayoutParams.WRAP_CONTENT, p.height);
    }

    @Test
    public void testGetChildDrawingOrder() {
        int childCount = 3;
        int index = 2;
        assertEquals(mGallery.getSelectedItemPosition(),
                ((MyGallery) mGallery).getChildDrawingOrder(childCount, index));

        childCount = 5;
        index = 2;
        assertEquals(index + 1, ((MyGallery) mGallery).getChildDrawingOrder(childCount, index));

        childCount = 5;
        index = 3;
        assertEquals(index + 1, ((MyGallery) mGallery).getChildDrawingOrder(childCount, index));
    }

    private static class ImageAdapter extends BaseAdapter {
        public ImageAdapter(Context c) {
            mContext = c;
        }

        public int getCount() {
            return mImageIds.length;
        }

        public Object getItem(int position) {
            return position;
        }

        public long getItemId(int position) {
            return position;
        }

        public View getView(int position, View convertView, ViewGroup parent) {
            ImageView i = new ImageView(mContext);

            i.setImageResource(mImageIds[position]);
            i.setScaleType(ImageView.ScaleType.FIT_XY);
            i.setLayoutParams(new Gallery.LayoutParams(136, 88));

            return i;
        }

        private Context mContext;

        private Integer[] mImageIds = {
                R.drawable.faces,
                R.drawable.scenery,
                R.drawable.testimage,
                R.drawable.faces,
                R.drawable.scenery,
                R.drawable.testimage,
                R.drawable.faces,
                R.drawable.scenery,
                R.drawable.testimage,
        };
    }

    public static class MyGallery extends Gallery {
        private ContextMenu.ContextMenuInfo mContextMenuInfo;

        public MyGallery(Context context) {
            super(context);
        }

        public MyGallery(Context context, AttributeSet attrs) {
            super(context, attrs);
        }

        public MyGallery(Context context, AttributeSet attrs, int defStyle) {
            super(context, attrs, defStyle);
        }

        @Override
        protected boolean getChildStaticTransformation(View child, Transformation t) {
            return super.getChildStaticTransformation(child, t);
        }

        @Override
        protected boolean checkLayoutParams(ViewGroup.LayoutParams p) {
            return super.checkLayoutParams(p);
        }

        @Override
        protected int computeHorizontalScrollExtent() {
            return super.computeHorizontalScrollExtent();
        }

        @Override
        protected int computeHorizontalScrollOffset() {
            return super.computeHorizontalScrollOffset();
        }

        @Override
        protected int computeHorizontalScrollRange() {
            return super.computeHorizontalScrollRange();
        }

        @Override
        protected void dispatchSetPressed(boolean pressed) {
            super.dispatchSetPressed(pressed);
        }

        @Override
        protected ViewGroup.LayoutParams generateDefaultLayoutParams() {
            return super.generateDefaultLayoutParams();
        }

        @Override
        protected ViewGroup.LayoutParams generateLayoutParams(ViewGroup.LayoutParams p) {
            return super.generateLayoutParams(p);
        }

        @Override
        protected int getChildDrawingOrder(int childCount, int i) {
            return super.getChildDrawingOrder(childCount, i);
        }

        @Override
        protected ContextMenu.ContextMenuInfo getContextMenuInfo() {
            if (mContextMenuInfo == null) {
                mContextMenuInfo = new MyContextMenuInfo();
            }
            return mContextMenuInfo;
        }

        @Override
        protected void onFocusChanged(boolean gainFocus, int direction,
                Rect previouslyFocusedRect) {
            super.onFocusChanged(gainFocus, direction, previouslyFocusedRect);
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            super.onLayout(changed, l, t, r, b);
        }

        private static class MyContextMenuInfo implements ContextMenu.ContextMenuInfo {
        }
    }
}
