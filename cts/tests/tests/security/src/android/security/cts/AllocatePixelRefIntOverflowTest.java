/*
 * Copyright (C) 2016 The Android Open Source Project
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

package android.security.cts;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.platform.test.annotations.SecurityTest;
import android.test.AndroidTestCase;

import java.io.InputStream;

import android.security.cts.R;

@SecurityTest
public class AllocatePixelRefIntOverflowTest extends AndroidTestCase {

    /**
     * Verifies that the device is not vulnerable to ANDROID-19270126: Android
     * BitmapFactory.decodeStream JPG allocPixelRef integer overflow
     */
    public void testAllocateJavaPixelRefIntOverflow() {
        InputStream exploitImage = mContext.getResources().openRawResource(
                R.raw.cve_2015_1531_b_19270126);
        /**
         * The decodeStream method results in SIGSEGV (Segmentation fault) on unpatched devices
         * while decoding the exploit image which will lead to process crash
         */
        BitmapFactory.decodeStream(exploitImage);
    }
}
