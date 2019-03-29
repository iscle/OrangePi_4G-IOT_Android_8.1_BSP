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
package android.graphics.cts;

import android.app.Instrumentation;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.io.InputStream;

import static android.support.test.InstrumentationRegistry.getInstrumentation;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class BitmapFactory_OptionsTest {
    @Test
    public void testOptions() {
        new BitmapFactory.Options();
    }

    @Test
    public void testRequestCancelDecode() {
        BitmapFactory.Options option = new BitmapFactory.Options();

        assertFalse(option.mCancel);
        option.requestCancelDecode();
        assertTrue(option.mCancel);
    }

    @Test
    public void testExtractMetaData() {
        Bitmap b;

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;

        Instrumentation instrumentation = getInstrumentation();
        Resources resources = instrumentation.getTargetContext().getResources();

        // Config from source file, RGBA_F16
        AssetManager assets = resources.getAssets();
        try (InputStream in = assets.open("prophoto-rgba16f.png")) {
            b = BitmapFactory.decodeStream(in, null, options);
        } catch (IOException e) {
            throw new RuntimeException("Test failed: ", e);
        }
        assertNull(b);
        assertEquals(64, options.outWidth);
        assertEquals(64, options.outHeight);
        assertEquals("image/png", options.outMimeType);
        assertEquals(Bitmap.Config.RGBA_F16, options.outConfig);

        // Config from source file, ARGB_8888
        b = BitmapFactory.decodeResource(resources, R.drawable.alpha, options);
        assertNull(b);
        assertEquals(Bitmap.Config.ARGB_8888, options.outConfig);

        // Force config to 565
        options.inPreferredConfig = Bitmap.Config.RGB_565;
        b = BitmapFactory.decodeResource(resources, R.drawable.icon_green, options);
        assertNull(b);
        assertEquals(Bitmap.Config.RGB_565, options.outConfig);

        options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;

        // Unscaled, indexed bitmap
        b = BitmapFactory.decodeResource(resources, R.drawable.bitmap_indexed, options);
        assertNull(b);
        assertEquals("image/gif", options.outMimeType);

        // Scaled, indexed bitmap
        options.inScaled = true;
        options.inDensity = 160;
        options.inScreenDensity = 480;
        options.inTargetDensity = 320;

        b = BitmapFactory.decodeResource(resources, R.drawable.bitmap_indexed, options);
        assertNull(b);
        assertEquals(Bitmap.Config.ARGB_8888, options.outConfig);
    }
}
