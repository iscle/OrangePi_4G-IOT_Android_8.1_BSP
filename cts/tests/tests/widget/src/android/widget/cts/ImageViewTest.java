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

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.PorterDuff;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.graphics.drawable.PaintDrawable;
import android.net.Uri;
import android.support.test.annotation.UiThreadTest;
import android.support.test.filters.MediumTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.util.AttributeSet;
import android.util.Xml;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.cts.util.TestUtils;

import com.android.compatibility.common.util.WidgetTestUtils;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xmlpull.v1.XmlPullParser;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Test {@link ImageView}.
 */
@MediumTest
@RunWith(AndroidJUnit4.class)
public class ImageViewTest {
    private Activity mActivity;
    private ImageView mImageViewRegular;

    @Rule
    public ActivityTestRule<ImageViewCtsActivity> mActivityRule =
            new ActivityTestRule<>(ImageViewCtsActivity.class);

    @Before
    public void setup() {
        mActivity = mActivityRule.getActivity();
        mImageViewRegular = (ImageView) mActivity.findViewById(R.id.imageview_regular);
    }

    /**
     * Find the ImageView specified by id.
     *
     * @param id the id
     * @return the ImageView
     */
    private ImageView findImageViewById(int id) {
        return (ImageView) mActivity.findViewById(id);
    }

    private void createSampleImage(File imagefile, int resid) {
        try (InputStream source = mActivity.getResources().openRawResource(resid);
             OutputStream target = new FileOutputStream(imagefile)) {
            byte[] buffer = new byte[1024];
            for (int len = source.read(buffer); len > 0; len = source.read(buffer)) {
                target.write(buffer, 0, len);
            }
        } catch (IOException e) {
            fail(e.getMessage());
        }
    }

    @Test
    public void testConstructor() {
        new ImageView(mActivity);

        new ImageView(mActivity, null);

        new ImageView(mActivity, null, 0);

        new ImageView(mActivity, null, 0, 0);

        XmlPullParser parser = mActivity.getResources().getXml(R.layout.imageview_layout);
        AttributeSet attrs = Xml.asAttributeSet(parser);
        new ImageView(mActivity, attrs);
        new ImageView(mActivity, attrs, 0);
    }


    @Test(expected=NullPointerException.class)
    public void testConstructorNullContext1() {
        new ImageView(null);
    }

    @Test(expected=NullPointerException.class)
    public void testConstructorNullContext2() {
        new ImageView(null, null);
    }

    @Test(expected=NullPointerException.class)
    public void testConstructorNullContext3() {
        new ImageView(null, null, -1);
    }

    @UiThreadTest
    @Test
    public void testInvalidateDrawable() {
        mImageViewRegular.invalidateDrawable(null);
    }

    @UiThreadTest
    @Test
    public void testSetAdjustViewBounds() {
        mImageViewRegular.setScaleType(ScaleType.FIT_XY);

        mImageViewRegular.setAdjustViewBounds(false);
        assertFalse(mImageViewRegular.getAdjustViewBounds());
        assertEquals(ScaleType.FIT_XY, mImageViewRegular.getScaleType());

        mImageViewRegular.setAdjustViewBounds(true);
        assertTrue(mImageViewRegular.getAdjustViewBounds());
        assertEquals(ScaleType.FIT_CENTER, mImageViewRegular.getScaleType());
    }

    @UiThreadTest
    @Test
    public void testSetMaxWidth() {
        mImageViewRegular.setMaxWidth(120);
        mImageViewRegular.setMaxWidth(-1);
    }

    @UiThreadTest
    @Test
    public void testSetMaxHeight() {
        mImageViewRegular.setMaxHeight(120);
        mImageViewRegular.setMaxHeight(-1);
    }

    @UiThreadTest
    @Test
    public void testGetDrawable() {
        final PaintDrawable drawable1 = new PaintDrawable();
        final PaintDrawable drawable2 = new PaintDrawable();

        assertNull(mImageViewRegular.getDrawable());

        mImageViewRegular.setImageDrawable(drawable1);
        assertEquals(drawable1, mImageViewRegular.getDrawable());
        assertNotSame(drawable2, mImageViewRegular.getDrawable());
    }

    @UiThreadTest
    @Test
    public void testSetImageIcon() {
        mImageViewRegular.setImageIcon(null);
        assertNull(mImageViewRegular.getDrawable());

        Icon icon = Icon.createWithResource(mActivity, R.drawable.testimage);
        mImageViewRegular.setImageIcon(icon);
        assertTrue(mImageViewRegular.isLayoutRequested());
        assertNotNull(mImageViewRegular.getDrawable());
        Drawable drawable = mActivity.getDrawable(R.drawable.testimage);
        BitmapDrawable testimageBitmap = (BitmapDrawable) drawable;
        Drawable imageViewDrawable = mImageViewRegular.getDrawable();
        BitmapDrawable imageViewBitmap = (BitmapDrawable) imageViewDrawable;
        WidgetTestUtils.assertEquals(testimageBitmap.getBitmap(), imageViewBitmap.getBitmap());
    }

    @UiThreadTest
    @Test
    public void testSetImageResource() {
        mImageViewRegular.setImageResource(-1);
        assertNull(mImageViewRegular.getDrawable());

        mImageViewRegular.setImageResource(R.drawable.testimage);
        assertTrue(mImageViewRegular.isLayoutRequested());
        assertNotNull(mImageViewRegular.getDrawable());
        Drawable drawable = mActivity.getDrawable(R.drawable.testimage);
        BitmapDrawable testimageBitmap = (BitmapDrawable) drawable;
        Drawable imageViewDrawable = mImageViewRegular.getDrawable();
        BitmapDrawable imageViewBitmap = (BitmapDrawable) imageViewDrawable;
        WidgetTestUtils.assertEquals(testimageBitmap.getBitmap(), imageViewBitmap.getBitmap());
    }

    @UiThreadTest
    @Test
    public void testSetImageURI() {
        mImageViewRegular.setImageURI(null);
        assertNull(mImageViewRegular.getDrawable());

        File dbDir = mActivity.getDir("tests", Context.MODE_PRIVATE);
        File imagefile = new File(dbDir, "tempimage.jpg");
        if (imagefile.exists()) {
            imagefile.delete();
        }
        createSampleImage(imagefile, R.raw.testimage);
        final String path = imagefile.getPath();
        mImageViewRegular.setImageURI(Uri.parse(path));
        assertTrue(mImageViewRegular.isLayoutRequested());
        assertNotNull(mImageViewRegular.getDrawable());

        Drawable imageViewDrawable = mImageViewRegular.getDrawable();
        BitmapDrawable imageViewBitmap = (BitmapDrawable) imageViewDrawable;
        Bitmap.Config viewConfig = imageViewBitmap.getBitmap().getConfig();
        Bitmap testimageBitmap = WidgetTestUtils.getUnscaledAndDitheredBitmap(
                mActivity.getResources(), R.raw.testimage, viewConfig);

        WidgetTestUtils.assertEquals(testimageBitmap, imageViewBitmap.getBitmap());
    }

    @UiThreadTest
    @Test
    public void testSetImageDrawable() {
        mImageViewRegular.setImageDrawable(null);
        assertNull(mImageViewRegular.getDrawable());

        final Drawable drawable = mActivity.getDrawable(R.drawable.testimage);
        mImageViewRegular.setImageDrawable(drawable);
        assertTrue(mImageViewRegular.isLayoutRequested());
        assertNotNull(mImageViewRegular.getDrawable());
        BitmapDrawable testimageBitmap = (BitmapDrawable) drawable;
        Drawable imageViewDrawable = mImageViewRegular.getDrawable();
        BitmapDrawable imageViewBitmap = (BitmapDrawable) imageViewDrawable;
        WidgetTestUtils.assertEquals(testimageBitmap.getBitmap(), imageViewBitmap.getBitmap());
    }

    @UiThreadTest
    @Test
    public void testSetImageBitmap() {
        mImageViewRegular.setImageBitmap(null);
        // A BitmapDrawable is always created for the ImageView.
        assertNotNull(mImageViewRegular.getDrawable());

        final Bitmap bitmap =
            BitmapFactory.decodeResource(mActivity.getResources(), R.drawable.testimage);
        mImageViewRegular.setImageBitmap(bitmap);
        assertTrue(mImageViewRegular.isLayoutRequested());
        assertNotNull(mImageViewRegular.getDrawable());
        Drawable imageViewDrawable = mImageViewRegular.getDrawable();
        BitmapDrawable imageViewBitmap = (BitmapDrawable) imageViewDrawable;
        WidgetTestUtils.assertEquals(bitmap, imageViewBitmap.getBitmap());
    }

    @UiThreadTest
    @Test
    public void testSetImageState() {
        int[] state = new int[8];
        mImageViewRegular.setImageState(state, false);
        assertSame(state, mImageViewRegular.onCreateDrawableState(0));
    }

    @UiThreadTest
    @Test
    public void testSetSelected() {
        assertFalse(mImageViewRegular.isSelected());

        mImageViewRegular.setSelected(true);
        assertTrue(mImageViewRegular.isSelected());

        mImageViewRegular.setSelected(false);
        assertFalse(mImageViewRegular.isSelected());
    }

    @UiThreadTest
    @Test
    public void testSetImageLevel() {
        PaintDrawable drawable = new PaintDrawable();
        drawable.setLevel(0);

        mImageViewRegular.setImageDrawable(drawable);
        mImageViewRegular.setImageLevel(1);
        assertEquals(1, drawable.getLevel());
    }

    @UiThreadTest
    @Test
    public void testAccessScaleType() {
        assertNotNull(mImageViewRegular.getScaleType());

        mImageViewRegular.setScaleType(ImageView.ScaleType.CENTER);
        assertEquals(ImageView.ScaleType.CENTER, mImageViewRegular.getScaleType());

        mImageViewRegular.setScaleType(ImageView.ScaleType.MATRIX);
        assertEquals(ImageView.ScaleType.MATRIX, mImageViewRegular.getScaleType());

        mImageViewRegular.setScaleType(ImageView.ScaleType.FIT_START);
        assertEquals(ImageView.ScaleType.FIT_START, mImageViewRegular.getScaleType());

        mImageViewRegular.setScaleType(ImageView.ScaleType.FIT_END);
        assertEquals(ImageView.ScaleType.FIT_END, mImageViewRegular.getScaleType());

        mImageViewRegular.setScaleType(ImageView.ScaleType.CENTER_CROP);
        assertEquals(ImageView.ScaleType.CENTER_CROP, mImageViewRegular.getScaleType());

        mImageViewRegular.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        assertEquals(ImageView.ScaleType.CENTER_INSIDE, mImageViewRegular.getScaleType());
    }

    @UiThreadTest
    @Test(expected=NullPointerException.class)
    public void testSetNullScaleType() {
        mImageViewRegular.setScaleType(null);
    }

    @UiThreadTest
    @Test
    public void testAccessImageMatrix() {
        mImageViewRegular.setImageMatrix(null);
        assertNotNull(mImageViewRegular.getImageMatrix());

        final Matrix matrix = new Matrix();
        mImageViewRegular.setImageMatrix(matrix);
        assertEquals(matrix, mImageViewRegular.getImageMatrix());
    }

    @UiThreadTest
    @Test
    public void testAccessBaseline() {
        mImageViewRegular.setImageDrawable(null);
        assertNull(mImageViewRegular.getDrawable());

        final Drawable drawable = mActivity.getDrawable(R.drawable.testimage);
        mImageViewRegular.setImageDrawable(drawable);

        assertEquals(-1, mImageViewRegular.getBaseline());

        mImageViewRegular.setBaseline(50);
        assertEquals(50, mImageViewRegular.getBaseline());

        mImageViewRegular.setBaselineAlignBottom(true);
        assertTrue(mImageViewRegular.getBaselineAlignBottom());
        assertEquals(mImageViewRegular.getMeasuredHeight(), mImageViewRegular.getBaseline());

        mImageViewRegular.setBaselineAlignBottom(false);
        assertFalse(mImageViewRegular.getBaselineAlignBottom());
        assertEquals(50, mImageViewRegular.getBaseline());
    }

    @UiThreadTest
    @Test
    public void testSetColorFilter1() {
        final Drawable drawable = mActivity.getDrawable(R.drawable.testimage);
        mImageViewRegular.setImageDrawable(drawable);

        mImageViewRegular.setColorFilter(null);
        assertNull(drawable.getColorFilter());

        mImageViewRegular.setColorFilter(0, PorterDuff.Mode.CLEAR);
        assertNotNull(drawable.getColorFilter());
        assertNotNull(mImageViewRegular.getColorFilter());
    }

    @UiThreadTest
    @Test
    public void testClearColorFilter() {
        final Drawable drawable = mActivity.getDrawable(R.drawable.testimage);
        mImageViewRegular.setImageDrawable(drawable);

        ColorFilter cf = new ColorFilter();
        mImageViewRegular.setColorFilter(cf);

        mImageViewRegular.clearColorFilter();
        assertNull(drawable.getColorFilter());
        assertNull(mImageViewRegular.getColorFilter());
    }

    @UiThreadTest
    @Test
    public void testSetColorFilter2() {
        final Drawable drawable = mActivity.getDrawable(R.drawable.testimage);
        mImageViewRegular.setImageDrawable(drawable);

        mImageViewRegular.setColorFilter(null);
        assertNull(drawable.getColorFilter());
        assertNull(mImageViewRegular.getColorFilter());

        ColorFilter cf = new ColorFilter();
        mImageViewRegular.setColorFilter(cf);
        assertSame(cf, drawable.getColorFilter());
        assertSame(cf, mImageViewRegular.getColorFilter());
    }

    @Test
    public void testDrawableStateChanged() {
        MockImageView imageView = spy(new MockImageView(mActivity));
        Drawable selectorDrawable = mActivity.getDrawable(R.drawable.statelistdrawable);
        imageView.setImageDrawable(selectorDrawable);

        // We shouldn't have been called on state change yet
        verify(imageView, never()).drawableStateChanged();
        // Mark image view as selected. Since our selector drawable has an "entry" for selected
        // state, that should cause a call to drawableStateChanged()
        imageView.setSelected(true);
        // Test that our image view has indeed called its own drawableStateChanged()
        verify(imageView, times(1)).drawableStateChanged();
        // And verify that image view's state matches that of our drawable
        assertArrayEquals(imageView.getDrawableState(), selectorDrawable.getState());
    }

    @Test
    public void testOnCreateDrawableState() {
        MockImageView mockImageView = new MockImageView(mActivity);

        assertArrayEquals(MockImageView.getEnabledStateSet(),
                mockImageView.onCreateDrawableState(0));

        int[] expected = new int[]{1, 2, 3};
        mockImageView.setImageState(expected, false);
        assertArrayEquals(expected, mockImageView.onCreateDrawableState(1));

        mockImageView.setImageState(expected, true);
    }

    @Test(expected=IndexOutOfBoundsException.class)
    public void testOnCreateDrawableStateInvalid() {
        MockImageView mockImageView = (MockImageView) findImageViewById(R.id.imageview_custom);
        mockImageView.setImageState(new int[] {1, 2, 3}, true);
        mockImageView.onCreateDrawableState(-1);
    }

    @UiThreadTest
    @Test
    public void testOnDraw() {
        MockImageView mockImageView = (MockImageView) findImageViewById(R.id.imageview_custom);

        Drawable drawable = spy(mActivity.getDrawable(R.drawable.icon_red));
        mockImageView.setImageDrawable(drawable);
        mockImageView.onDraw(new Canvas());

        verify(drawable, atLeastOnce()).draw(any(Canvas.class));
    }

    @UiThreadTest
    @Test
    public void testOnMeasure() {
        mImageViewRegular.measure(200, 150);
        assertTrue(mImageViewRegular.getMeasuredWidth() <= 200);
        assertTrue(mImageViewRegular.getMeasuredHeight() <= 150);
    }

    @Test
    public void testSetFrame() {
        MockImageView mockImageView = spy(new MockImageView(mActivity));
        verify(mockImageView, never()).onSizeChanged(anyInt(), anyInt(), anyInt(), anyInt());

        assertTrue(mockImageView.setFrame(5, 10, 100, 200));
        assertEquals(5, mockImageView.getLeft());
        assertEquals(10, mockImageView.getTop());
        assertEquals(100, mockImageView.getRight());
        assertEquals(200, mockImageView.getBottom());
        verify(mockImageView, times(1)).onSizeChanged(95, 190, 0, 0);

        assertFalse(mockImageView.setFrame(5, 10, 100, 200));
        // Verify that there were no more calls to onSizeChanged (since the new frame is the
        // same frame as we had before).
        verify(mockImageView, times(1)).onSizeChanged(anyInt(), anyInt(), anyInt(), anyInt());
    }

    @UiThreadTest
    @Test
    public void testVerifyDrawable() {
        MockImageView mockImageView = (MockImageView) findImageViewById(R.id.imageview_custom);

        Drawable drawable = new ColorDrawable(0xFFFF0000);
        mockImageView.setImageDrawable(drawable);
        Drawable backgroundDrawable = new ColorDrawable(0xFF0000FF);
        mockImageView.setBackgroundDrawable(backgroundDrawable);

        assertFalse(mockImageView.verifyDrawable(new ColorDrawable(0xFF00FF00)));
        assertTrue(mockImageView.verifyDrawable(drawable));
        assertTrue(mockImageView.verifyDrawable(backgroundDrawable));
    }

    @UiThreadTest
    @Test
    public void testImageTintBasics() {
        ImageView imageViewTinted = (ImageView) mActivity.findViewById(R.id.imageview_tint);

        assertEquals("Image tint inflated correctly",
                Color.WHITE, imageViewTinted.getImageTintList().getDefaultColor());
        assertEquals("Image tint mode inflated correctly",
                PorterDuff.Mode.SRC_OVER, imageViewTinted.getImageTintMode());

        imageViewTinted.setImageTintMode(PorterDuff.Mode.SRC_IN);
        assertEquals(PorterDuff.Mode.SRC_IN, imageViewTinted.getImageTintMode());
    }

    @UiThreadTest
    @Test
    public void testImageTintDrawableUpdates() {
        Drawable drawable = spy(mActivity.getDrawable(R.drawable.icon_red));

        mImageViewRegular.setImageDrawable(drawable);
        // No image tint applied by default
        verify(drawable, never()).setTintList(any(ColorStateList.class));

        mImageViewRegular.setImageTintList(ColorStateList.valueOf(Color.WHITE));
        // Image tint applied when setImageTintList() called after setImageDrawable()
        verify(drawable, times(1)).setTintList(any(ColorStateList.class));

        mImageViewRegular.setImageDrawable(null);
        mImageViewRegular.setImageDrawable(drawable);
        // Image tint applied when setImageTintList() called before setImageDrawable()
        verify(drawable, times(2)).setTintList(any(ColorStateList.class));
    }

    @UiThreadTest
    @Test
    public void testImageTintVisuals() {
        ImageView imageViewTinted = (ImageView) mActivity.findViewById(
                R.id.imageview_tint_with_source);

        TestUtils.assertAllPixelsOfColor("All pixels should be white", imageViewTinted,
                0xFFFFFFFF, 1, false);

        // Use translucent white tint. Together with SRC_OVER mode (defined in XML) the end
        // result should be a fully opaque image view with solid fill color in between red
        // and white.
        imageViewTinted.setImageTintList(ColorStateList.valueOf(0x80FFFFFF));
        TestUtils.assertAllPixelsOfColor("All pixels should be light red", imageViewTinted,
                0xFFFF8080, 1, false);

        // Switch to SRC_IN mode. This should completely ignore the original drawable set on
        // the image view and use the last set tint color (50% alpha white).
        imageViewTinted.setImageTintMode(PorterDuff.Mode.SRC_IN);
        TestUtils.assertAllPixelsOfColor("All pixels should be 50% alpha white", imageViewTinted,
                0x80FFFFFF, 1, false);

        // Switch to DST mode. This should completely ignore the last set tint color and use the
        // the original drawable set on the image view.
        imageViewTinted.setImageTintMode(PorterDuff.Mode.DST);
        TestUtils.assertAllPixelsOfColor("All pixels should be red", imageViewTinted,
                0xFFFF0000, 1, false);
    }

    @UiThreadTest
    @Test
    public void testAlpha() {
        mImageViewRegular.setImageResource(R.drawable.blue_fill);

        TestUtils.assertAllPixelsOfColor("All pixels should be blue", mImageViewRegular,
                0xFF0000FF, 1, false);

        mImageViewRegular.setAlpha(128);
        TestUtils.assertAllPixelsOfColor("All pixels should be 50% alpha blue", mImageViewRegular,
                0x800000FF, 1, false);

        mImageViewRegular.setAlpha(0);
        TestUtils.assertAllPixelsOfColor("All pixels should be transparent", mImageViewRegular,
                0x00000000, 1, false);

        mImageViewRegular.setAlpha(255);
        TestUtils.assertAllPixelsOfColor("All pixels should be blue", mImageViewRegular,
                0xFF0000FF, 1, false);
    }

    @UiThreadTest
    @Test
    public void testImageAlpha() {
        mImageViewRegular.setImageResource(R.drawable.blue_fill);

        assertEquals(255, mImageViewRegular.getImageAlpha());
        TestUtils.assertAllPixelsOfColor("All pixels should be blue", mImageViewRegular,
                0xFF0000FF, 1, false);

        mImageViewRegular.setImageAlpha(128);
        assertEquals(128, mImageViewRegular.getImageAlpha());
        TestUtils.assertAllPixelsOfColor("All pixels should be 50% alpha blue", mImageViewRegular,
                0x800000FF, 1, false);

        mImageViewRegular.setImageAlpha(0);
        assertEquals(0, mImageViewRegular.getImageAlpha());
        TestUtils.assertAllPixelsOfColor("All pixels should be transparent", mImageViewRegular,
                0x00000000, 1, false);

        mImageViewRegular.setImageAlpha(255);
        assertEquals(255, mImageViewRegular.getImageAlpha());
        TestUtils.assertAllPixelsOfColor("All pixels should be blue", mImageViewRegular,
                0xFF0000FF, 1, false);
    }

    public static class MockImageView extends ImageView {
        public MockImageView(Context context) {
            super(context);
        }

        public MockImageView(Context context, AttributeSet attrs) {
            super(context, attrs);
        }

        public MockImageView(Context context, AttributeSet attrs, int defStyle) {
            super(context, attrs, defStyle);
        }

        public static int[] getEnabledStateSet() {
            return ENABLED_STATE_SET;
        }

        @Override
        protected void drawableStateChanged() {
            super.drawableStateChanged();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }

        @Override
        protected boolean onSetAlpha(int alpha) {
            return super.onSetAlpha(alpha);
        }

        @Override
        protected boolean setFrame(int l, int t, int r, int b) {
            return super.setFrame(l, t, r, b);
        }

        @Override
        protected boolean verifyDrawable(Drawable dr) {
            return super.verifyDrawable(dr);
        }

        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
        }
    }
}
