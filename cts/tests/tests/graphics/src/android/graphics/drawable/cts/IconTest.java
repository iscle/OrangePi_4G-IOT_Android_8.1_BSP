/*
 * Copyright (C) 2015 The Android Open Source Project
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
 * limitations under the License
 */

package android.graphics.drawable.cts;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.cts.ImageViewCtsActivity;
import android.graphics.cts.R;
import android.graphics.drawable.Icon;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.os.Parcel;
import android.support.test.filters.MediumTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class IconTest {
    private static final long TIMEOUT_MS = 1000;

    private Activity mActivity;
    private Icon mIcon;

    @Rule
    public ActivityTestRule<ImageViewCtsActivity> mActivityRule =
            new ActivityTestRule<>(ImageViewCtsActivity.class);

    @Before
    public void setup() {
        mActivity = mActivityRule.getActivity();
    }

    @Test
    public void testBitmapIcon() {
        verifyIconValidity(
                Icon.createWithBitmap(Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888)));
    }

    @Test
    public void testDataIcon() {
        byte[] data = new byte[4];
        data[0] = data[1] = data[2] = data[3] = (byte)255;
        verifyIconValidity(Icon.createWithData(data, 0, 4));
    }

    @Test
    public void testFileIcon() throws IOException {
        File file = new File(mActivity.getFilesDir(), "testimage.jpg");
        try {
            writeSampleImage(file);
            assertTrue(file.exists());

            verifyIconValidity(Icon.createWithFilePath(file.getPath()));

            verifyIconValidity(Icon.createWithContentUri(Uri.fromFile(file)));

            verifyIconValidity(Icon.createWithContentUri(file.toURI().toString()));
        } finally {
            file.delete();
        }
    }

    @Test
    public void testResourceIcon() {
        verifyIconValidity(Icon.createWithResource(mActivity, R.drawable.bmp_test));

        verifyIconValidity(Icon.createWithResource(
                mActivity.getPackageName(), R.drawable.bmp_test));
    }

    @Test
    public void testLoadDrawableAsync() throws Throwable {
        mIcon = Icon.createWithBitmap(Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888));

        Icon.OnDrawableLoadedListener mockListener = mock(Icon.OnDrawableLoadedListener.class);
        mActivityRule.runOnUiThread(
                () -> mIcon.loadDrawableAsync(mActivity, mockListener, new Handler()));
        // Verify that there was exactly one call to the passed listener's callback within the
        // predetermined timeout
        Thread.sleep(TIMEOUT_MS);
        verify(mockListener, times(1)).onDrawableLoaded(any());
    }

    @Test
    public void testLoadDrawableAsyncWithMessage() throws Throwable {
        mIcon = Icon.createWithBitmap(Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888));

        Runnable mockRunnable = mock(Runnable.class);
        mActivityRule.runOnUiThread(
                () -> mIcon.loadDrawableAsync(mActivity, Message.obtain(new Handler(),
                        mockRunnable)));
        // Verify that there was exactly one call to the passed Runnable's run within the
        // predetermined timeout
        Thread.sleep(TIMEOUT_MS);
        verify(mockRunnable, times(1)).run();
    }

    private void writeSampleImage(File imagefile) throws IOException {
        try (InputStream source = mActivity.getResources().openRawResource(R.drawable.testimage);
             OutputStream target = new FileOutputStream(imagefile)) {
            byte[] buffer = new byte[1024];
            for (int len = source.read(buffer); len >= 0; len = source.read(buffer)) {
                target.write(buffer, 0, len);
            }
        }
    }

    // Check if the created icon is valid and doesn't cause crashes for the public methods.
    private void verifyIconValidity(Icon icon) {
        assertNotNull(icon);

        // tint properties.
        icon.setTint(Color.BLUE);
        icon.setTintList(ColorStateList.valueOf(Color.RED));
        icon.setTintMode(PorterDuff.Mode.XOR);

        // Parcelable methods.
        icon.describeContents();
        Parcel parcel = Parcel.obtain();
        icon.writeToParcel(parcel, 0);

        parcel.setDataPosition(0);
        assertNotNull(Icon.CREATOR.createFromParcel(parcel));

        // loading drawable synchronously.
        assertNotNull(icon.loadDrawable(mActivity));
    }
}
