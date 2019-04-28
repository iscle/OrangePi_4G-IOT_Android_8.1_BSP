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
 * limitations under the License.
 */

package com.android.tv.util;

import static com.android.tv.util.BitmapUtils.createScaledBitmapInfo;
import static org.junit.Assert.assertSame;

import android.graphics.Bitmap;
import android.support.test.filters.MediumTest;

import com.android.tv.util.BitmapUtils.ScaledBitmapInfo;

import org.junit.Before;
import org.junit.Test;

/**
 * Tests for {@link ImageCache}.
 */
@MediumTest
public class ImageCacheTest {
    private static final Bitmap ORIG = Bitmap.createBitmap(100, 100, Bitmap.Config.RGB_565);

    private static final String KEY = "same";
    private static final ScaledBitmapInfo INFO_200 = createScaledBitmapInfo(KEY, ORIG, 200, 200);
    private static final ScaledBitmapInfo INFO_100 = createScaledBitmapInfo(KEY, ORIG, 100, 100);
    private static final ScaledBitmapInfo INFO_50 = createScaledBitmapInfo(KEY, ORIG, 50, 50);
    private static final ScaledBitmapInfo INFO_25 = createScaledBitmapInfo(KEY, ORIG, 25, 25);

    private ImageCache mImageCache;

    @Before
    public void setUp() throws Exception {
        mImageCache = ImageCache.newInstance(0.1f);
    }

    //TODO: Empty the cache in the setup.  Try using @VisibleForTesting

    @Test
    public void testPutIfLarger_smaller() throws Exception {

        mImageCache.putIfNeeded( INFO_50);
        assertSame("before", INFO_50, mImageCache.get(KEY));

        mImageCache.putIfNeeded( INFO_25);
        assertSame("after", INFO_50, mImageCache.get(KEY));
    }

    @Test
    public void testPutIfLarger_larger() throws Exception {
        mImageCache.putIfNeeded( INFO_50);
        assertSame("before", INFO_50, mImageCache.get(KEY));

        mImageCache.putIfNeeded(INFO_100);
        assertSame("after", INFO_100, mImageCache.get(KEY));
    }

    @Test
    public void testPutIfLarger_alreadyMax() throws Exception {

        mImageCache.putIfNeeded( INFO_100);
        assertSame("before", INFO_100, mImageCache.get(KEY));

        mImageCache.putIfNeeded( INFO_200);
        assertSame("after", INFO_100, mImageCache.get(KEY));
    }
}