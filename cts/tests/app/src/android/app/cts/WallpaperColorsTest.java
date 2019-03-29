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
 * limitations under the License
 */

package android.app.cts;

import android.app.WallpaperColors;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.ColorDrawable;
import android.os.Parcel;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.util.Pair;
import android.util.Size;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class WallpaperColorsTest {

    @Test
    public void getWallpaperColorsTest() {
        ArrayList<Color> colorList = new ArrayList<>();
        colorList.add(Color.valueOf(Color.WHITE));
        colorList.add(Color.valueOf(Color.BLACK));
        colorList.add(Color.valueOf(Color.GREEN));

        WallpaperColors colors = new WallpaperColors(colorList.get(0), colorList.get(1),
                colorList.get(2));
        Assert.assertSame(colors.getPrimaryColor(), colorList.get(0));
        Assert.assertSame(colors.getSecondaryColor(), colorList.get(1));
        Assert.assertSame(colors.getTertiaryColor(), colorList.get(2));
    }

    @Test
    public void equalsTest() {
        WallpaperColors colors1 = new WallpaperColors(Color.valueOf(Color.BLACK), null, null);
        WallpaperColors colors2 = new WallpaperColors(Color.valueOf(Color.WHITE), null, null);

        // Different colors
        Assert.assertNotEquals(colors1, colors2);

        // Same colors
        WallpaperColors colors3 = new WallpaperColors(Color.valueOf(Color.BLACK), null, null);
        Assert.assertEquals(colors1, colors3);
    }

    @Test
    public void parcelTest() {
        WallpaperColors wallpaperColors = new WallpaperColors(Color.valueOf(Color.WHITE),
                Color.valueOf(Color.BLACK), Color.valueOf(Color.GREEN));

        Parcel parcel = Parcel.obtain();
        wallpaperColors.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        WallpaperColors newColors = new WallpaperColors(parcel);
        Assert.assertEquals(wallpaperColors, newColors);
        Assert.assertEquals(parcel.dataPosition(), parcel.dataSize());
        parcel.recycle();
    }

    @Test
    public void fromBitmapTest() {
        Bitmap bmp = Bitmap.createBitmap(30, 30, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        canvas.drawColor(Color.GREEN);

        WallpaperColors colors = WallpaperColors.fromBitmap(bmp);
        Assert.assertNotNull(colors.getPrimaryColor());
        Assert.assertNull(colors.getSecondaryColor());
    }

    @Test
    public void fromDrawableTest() {
        ColorDrawable drawable = new ColorDrawable(Color.GREEN);
        drawable.setBounds(0, 0, 30, 30);

        WallpaperColors colors = WallpaperColors.fromDrawable(drawable);
        Assert.assertNotNull(colors.getPrimaryColor());
        Assert.assertNull(colors.getSecondaryColor());
    }

}
