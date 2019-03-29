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

import static org.junit.Assert.assertEquals;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.cts.R;
import android.graphics.drawable.VectorDrawable;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.MediumTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

import java.util.Arrays;

@MediumTest
@RunWith(Parameterized.class)
public class VectorDrawableSizeTest {
    private Context mContext = null;
    private Resources mResources = null;
    private int mResId;
    private int mDpSize;

    @Parameters ( name = "{0}" )
    public static Iterable<Object[]> data() {
        return Arrays.asList(new Object[][]{
                {"size_1", R.drawable.vector_icon_size_1, 7},
                {"size_2", R.drawable.vector_icon_size_2, 9}
        });
    }

    public VectorDrawableSizeTest(String name, int resId, int dp) throws Throwable {
        mResId = resId;
        mDpSize = dp;
    }

    @Before
    public void setup() {
        mContext = InstrumentationRegistry.getTargetContext();
        mResources = mContext.getResources();

    }

    @Test
    public void testVectorDrawableSize() throws Throwable {
        // This test makes sure the size computation for VectorDrawable is using round, instead of
        // rounding down.
        final int densityDpi = mResources.getConfiguration().densityDpi;

        VectorDrawable drawable = (VectorDrawable) mResources.getDrawable(mResId, null);
        assertEquals(Math.round(mDpSize * densityDpi / 160f),  drawable.getIntrinsicWidth());
        assertEquals(Math.round(mDpSize * densityDpi / 160f),  drawable.getIntrinsicHeight());
    }
}
