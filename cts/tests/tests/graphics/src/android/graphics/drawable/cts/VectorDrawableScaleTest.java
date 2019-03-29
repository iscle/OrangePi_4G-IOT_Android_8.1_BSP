/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.graphics.drawable.cts;

import android.app.Activity;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.graphics.cts.R;
import android.support.test.filters.MediumTest;
import android.support.test.rule.ActivityTestRule;
import android.view.PixelCopy;
import android.widget.ImageView;

import com.android.compatibility.common.util.SynchronousPixelCopy;
import com.android.compatibility.common.util.WidgetTestUtils;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

@MediumTest
public class VectorDrawableScaleTest {
    private static final boolean DBG_SCREENSHOT = false;
    @Rule
    public final ActivityTestRule<DrawableStubActivity> mActivityRule =
            new ActivityTestRule<>(DrawableStubActivity.class);

    private Activity mActivity = null;
    private Resources mResources = null;

    public VectorDrawableScaleTest() throws Throwable {
    }

    @Before
    public void setup() {
        mActivity = mActivityRule.getActivity();
        mResources = mActivity.getResources();
    }

    @Test
    public void testVectorDrawableInImageView() throws Throwable {
        mActivityRule.runOnUiThread(() -> {
            mActivity.setContentView(R.layout.vector_drawable_scale_layout);
        });

        Bitmap screenShot = null;
        WidgetTestUtils.runOnMainAndDrawSync(mActivityRule,
                mActivity.findViewById(R.id.scaletest),
                () -> setupImageViews());
        final Rect srcRect = new Rect();
        mActivityRule.runOnUiThread(() -> {
            mActivity.findViewById(R.id.imageview1).getGlobalVisibleRect(srcRect);
        });

        screenShot = takeScreenshot(srcRect);
        if (DBG_SCREENSHOT) {
            String outputFolder = mActivity.getExternalFilesDir(null).getAbsolutePath();
            DrawableTestUtils.saveVectorDrawableIntoPNG(screenShot, outputFolder, "scale");
        }

        Bitmap golden = BitmapFactory.decodeResource(mResources,
                R.drawable.vector_drawable_scale_golden);
        DrawableTestUtils.compareImages("vectorDrawableScale", screenShot, golden,
                DrawableTestUtils.PIXEL_ERROR_THRESHOLD,
                DrawableTestUtils.PIXEL_ERROR_COUNT_THRESHOLD,
                DrawableTestUtils.PIXEL_ERROR_TOLERANCE);
    }

    // Setup 2 imageviews, one big and one small. The purpose of this test is to make sure that the
    // imageview with smaller scale will not affect the appearance in the imageview with larger
    // scale.
    private void setupImageViews() {
        ImageView imageView = (ImageView) mActivity.findViewById(R.id.imageview1);
        imageView.setImageResource(R.drawable.vector_icon_create);
        imageView = (ImageView) mActivity.findViewById(R.id.imageview2);
        imageView.setImageResource(R.drawable.vector_icon_create);
    }

    // Copy the source rectangle from the screen into the returned bitmap.
    private Bitmap takeScreenshot(Rect srcRect) {
        SynchronousPixelCopy copy = new SynchronousPixelCopy();
        Bitmap dest = Bitmap.createBitmap(
                srcRect.width(), srcRect.height(), Bitmap.Config.ARGB_8888);
        int copyResult = copy.request(mActivity.getWindow(), srcRect, dest);
        Assert.assertEquals(PixelCopy.SUCCESS, copyResult);
        return dest;
    }
}
