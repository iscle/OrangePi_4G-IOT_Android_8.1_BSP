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

package android.text.style.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.text.cts.R;
import android.text.style.DynamicDrawableSpan;
import android.text.style.ImageSpan;

import com.android.compatibility.common.util.WidgetTestUtils;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class ImageSpanTest {
    private Context mContext;

    @Before
    public void setup() {
        mContext = InstrumentationRegistry.getTargetContext();
    }

    @Test
    public void testConstructor() {
        int width = 80;
        int height = 120;
        int[] color = new int[width * height];
        Bitmap b = Bitmap.createBitmap(color, width, height, Bitmap.Config.RGB_565);

        new ImageSpan(b);
        new ImageSpan(b, DynamicDrawableSpan.ALIGN_BOTTOM);
        new ImageSpan(b, DynamicDrawableSpan.ALIGN_BASELINE);

        new ImageSpan(mContext, b);
        new ImageSpan(mContext, b, DynamicDrawableSpan.ALIGN_BOTTOM);
        new ImageSpan(mContext, b, DynamicDrawableSpan.ALIGN_BASELINE);

        Drawable d = mContext.getResources().getDrawable(R.drawable.pass);
        new ImageSpan(d);
        new ImageSpan(d, DynamicDrawableSpan.ALIGN_BOTTOM);
        new ImageSpan(d, DynamicDrawableSpan.ALIGN_BASELINE);

        new ImageSpan(d, "cts test.");
        new ImageSpan(d, "cts test.", DynamicDrawableSpan.ALIGN_BOTTOM);
        new ImageSpan(d, "cts test.", DynamicDrawableSpan.ALIGN_BASELINE);

        new ImageSpan(mContext, Uri.parse("content://user/a/b"));
        new ImageSpan(mContext, Uri.parse("content://user/a/b"),
                DynamicDrawableSpan.ALIGN_BOTTOM);
        new ImageSpan(mContext, Uri.parse("content://user/a/b"),
                DynamicDrawableSpan.ALIGN_BASELINE);

        new ImageSpan(mContext, R.drawable.pass);
        new ImageSpan(mContext, R.drawable.pass, DynamicDrawableSpan.ALIGN_BOTTOM);
        new ImageSpan(mContext, R.drawable.pass, DynamicDrawableSpan.ALIGN_BASELINE);

        new ImageSpan((Bitmap) null);
        new ImageSpan((Drawable) null);
        new ImageSpan((Drawable) null, (String) null);
        new ImageSpan((Context) null, -1);
        new ImageSpan((Bitmap) null, -1);
        new ImageSpan((Drawable) null, -1);
        new ImageSpan((Drawable) null, (String) null, -1);
        new ImageSpan((Context) null, -1, -1);
    }

    @Test
    public void testGetSource() {
        Drawable d = mContext.getDrawable(R.drawable.pass);

        ImageSpan imageSpan = new ImageSpan(d);
        assertNull(imageSpan.getSource());

        String source = "cts test.";
        imageSpan = new ImageSpan(d, source);
        assertEquals(source, imageSpan.getSource());

        source = "content://user/a/b";
        imageSpan = new ImageSpan(mContext, Uri.parse(source));
        assertEquals(source, imageSpan.getSource());
    }

    @Test
    public void testGetDrawable() {
        Drawable drawable = mContext.getDrawable(R.drawable.pass);

        ImageSpan imageSpan = new ImageSpan(drawable);
        assertSame(drawable, imageSpan.getDrawable());

        BitmapDrawable bitmapDrawable = (BitmapDrawable) drawable;
        imageSpan = new ImageSpan(mContext, R.drawable.pass);
        BitmapDrawable resultDrawable = (BitmapDrawable) imageSpan.getDrawable();
        WidgetTestUtils.assertEquals(bitmapDrawable.getBitmap(), resultDrawable.getBitmap());

        imageSpan = new ImageSpan(mContext, Uri.parse("unknown uri."));
        assertNull(imageSpan.getDrawable());

        imageSpan = new ImageSpan((Context) null, -1);
        assertNull(imageSpan.getDrawable());
    }
}
