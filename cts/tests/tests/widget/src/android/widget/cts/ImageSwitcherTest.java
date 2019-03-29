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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.support.test.annotation.UiThreadTest;
import android.support.test.filters.MediumTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.util.AttributeSet;
import android.util.Xml;
import android.widget.ImageSwitcher;
import android.widget.ImageView;

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
 * Test {@link ImageSwitcher}.
 */
@MediumTest
@RunWith(AndroidJUnit4.class)
public class ImageSwitcherTest {
    private Activity mActivity;
    private ImageSwitcher mImageSwitcher;

    @Rule
    public ActivityTestRule<ImageSwitcherCtsActivity> mActivityRule =
            new ActivityTestRule<>(ImageSwitcherCtsActivity.class);

    @Before
    public void setup() {
        mActivity = mActivityRule.getActivity();
        mImageSwitcher = (ImageSwitcher) mActivity.findViewById(R.id.switcher);
    }

    @Test
    public void testConstructor() {
        new ImageSwitcher(mActivity);

        new ImageSwitcher(mActivity, null);

        XmlPullParser parser = mActivity.getResources().getXml(R.layout.imageswitcher_layout);
        AttributeSet attrs = Xml.asAttributeSet(parser);
        assertNotNull(attrs);
        new ImageSwitcher(mActivity, attrs);
    }

    @UiThreadTest
    @Test
    public void testSetImageResource() {
        ImageView iv = new ImageView(mActivity);
        mImageSwitcher.addView(iv);
        ImageView iv1 = new ImageView(mActivity);
        mImageSwitcher.addView(iv1);

        assertSame(iv, mImageSwitcher.getCurrentView());
        mImageSwitcher.setImageResource(R.drawable.scenery);
        assertSame(iv1, mImageSwitcher.getCurrentView());
        Resources resources = mActivity.getResources();
        Drawable drawable = resources.getDrawable(R.drawable.scenery);
        BitmapDrawable sceneryBitmap = (BitmapDrawable) drawable;
        BitmapDrawable currViewBitmap =
            (BitmapDrawable) ((ImageView) mImageSwitcher.getCurrentView()).getDrawable();
        WidgetTestUtils.assertEquals(sceneryBitmap.getBitmap(), currViewBitmap.getBitmap());

        mImageSwitcher.setImageResource(R.drawable.testimage);
        assertSame(iv, mImageSwitcher.getCurrentView());
        drawable = resources.getDrawable(R.drawable.testimage);
        BitmapDrawable testimageBitmap = (BitmapDrawable) drawable;
        currViewBitmap =
            (BitmapDrawable) ((ImageView) mImageSwitcher.getCurrentView()).getDrawable();
        WidgetTestUtils.assertEquals(testimageBitmap.getBitmap(), currViewBitmap.getBitmap());

        mImageSwitcher.setImageResource(-1);
        assertNull(((ImageView) mImageSwitcher.getCurrentView()).getDrawable());
    }

    @UiThreadTest
    @Test
    public void testSetImageURI() {
        ImageView iv = new ImageView(mActivity);
        mImageSwitcher.addView(iv);
        ImageView iv1 = new ImageView(mActivity);
        mImageSwitcher.addView(iv1);

        File dbDir = mActivity.getDir("tests", Context.MODE_PRIVATE);
        File imagefile = new File(dbDir, "tempimage.jpg");
        if (imagefile.exists()) {
            imagefile.delete();
        }
        createSampleImage(imagefile, R.raw.testimage);

        assertSame(iv, mImageSwitcher.getCurrentView());
        Uri uri = Uri.parse(imagefile.getPath());
        mImageSwitcher.setImageURI(uri);
        assertSame(iv1, mImageSwitcher.getCurrentView());

        BitmapDrawable currViewBitmap =
            (BitmapDrawable) ((ImageView) mImageSwitcher.getCurrentView()).getDrawable();
        Bitmap testImageBitmap = WidgetTestUtils.getUnscaledAndDitheredBitmap(
                mActivity.getResources(), R.raw.testimage,
                currViewBitmap.getBitmap().getConfig());
        WidgetTestUtils.assertEquals(testImageBitmap, currViewBitmap.getBitmap());

        createSampleImage(imagefile, R.raw.scenery);
        uri = Uri.parse(imagefile.getPath());
        mImageSwitcher.setImageURI(uri);
        assertSame(iv, mImageSwitcher.getCurrentView());
        Bitmap sceneryImageBitmap = WidgetTestUtils.getUnscaledAndDitheredBitmap(
                mActivity.getResources(), R.raw.scenery,
                currViewBitmap.getBitmap().getConfig());
        currViewBitmap =
            (BitmapDrawable) ((ImageView) mImageSwitcher.getCurrentView()).getDrawable();
        WidgetTestUtils.assertEquals(sceneryImageBitmap, currViewBitmap.getBitmap());

        imagefile.delete();

        mImageSwitcher.setImageURI(null);
    }

    @UiThreadTest
    @Test
    public void testSetImageDrawable() {
        ImageView iv = new ImageView(mActivity);
        mImageSwitcher.addView(iv);
        ImageView iv1 = new ImageView(mActivity);
        mImageSwitcher.addView(iv1);

        Resources resources = mActivity.getResources();
        assertSame(iv, mImageSwitcher.getCurrentView());
        Drawable drawable = resources.getDrawable(R.drawable.scenery);
        mImageSwitcher.setImageDrawable(drawable);
        assertSame(iv1, mImageSwitcher.getCurrentView());
        assertSame(drawable, ((ImageView) mImageSwitcher.getCurrentView()).getDrawable());

        drawable = resources.getDrawable(R.drawable.testimage);
        mImageSwitcher.setImageDrawable(drawable);
        assertSame(iv, mImageSwitcher.getCurrentView());
        assertSame(drawable, ((ImageView) mImageSwitcher.getCurrentView()).getDrawable());

        mImageSwitcher.setImageDrawable(null);
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
}
