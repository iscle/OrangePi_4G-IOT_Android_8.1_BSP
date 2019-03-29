/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.security.cts;

import android.graphics.BitmapFactory;
import android.platform.test.annotations.SecurityTest;
import android.test.AndroidTestCase;

import android.security.cts.R;

import java.io.BufferedInputStream;
import java.io.InputStream;

@SecurityTest
public class BitmapFactoryDecodeStreamTest extends AndroidTestCase {
    /*
     * This test case reproduces the bug in CVE-2015-1532.
     * It verifies that the BitmapFactory:decodeStream method is not vulnerable
     * to heap corruption by trying to open a crafted PNG image with incorrect
     * npTc chunk.
     */
    public void testNinePatchHeapOverflow() throws Exception {
        InputStream inStream = new BufferedInputStream(mContext.getResources().openRawResource(
                R.raw.cve_2015_1532));
        BitmapFactory.decodeStream(inStream);

    }

    @SecurityTest
    public void testPocCVE_2017_0691() throws Exception {
        InputStream exploitImage = new BufferedInputStream(mContext.getResources().openRawResource(
                R.raw.cve_2017_0691));
        BitmapFactory.decodeStream(exploitImage);
    }

    public void test_b65290323() throws Exception {
        InputStream exploitImage = new BufferedInputStream(mContext.getResources().openRawResource(
                R.raw.b65290323));
        BitmapFactory.decodeStream(exploitImage);
    }
}
