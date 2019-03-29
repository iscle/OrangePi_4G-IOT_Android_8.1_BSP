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

package android.security.cts;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.platform.test.annotations.SecurityTest;
import android.test.AndroidTestCase;

import java.io.InputStream;

import android.security.cts.R;

@SecurityTest
public class ZeroHeightTiffTest extends AndroidTestCase {
    /**
     * Verifies that the device fails to decode a zero height tiff file.
     *
     * Prior to fixing bug 33300701, decoding resulted in undefined behavior (divide by zero).
     * With the fix, decoding will fail, without dividing by zero.
     */
    @SecurityTest
    public void test_android_bug_33300701() {
        InputStream exploitImage = mContext.getResources().openRawResource(R.raw.bug_33300701);
        Bitmap bitmap = BitmapFactory.decodeStream(exploitImage);
        assertNull(bitmap);
    }
}
