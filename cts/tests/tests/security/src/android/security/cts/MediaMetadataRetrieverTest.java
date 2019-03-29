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

package android.security.cts;

import android.security.cts.R;

import android.content.res.AssetFileDescriptor;
import android.content.res.Resources;
import android.media.MediaMetadataRetriever;
import android.platform.test.annotations.SecurityTest;
import android.test.AndroidTestCase;

import java.io.IOException;

@SecurityTest
public class MediaMetadataRetrieverTest extends AndroidTestCase {
    protected Resources mResources;
    protected MediaMetadataRetriever mRetriever;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mResources = getContext().getResources();
        mRetriever = new MediaMetadataRetriever();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        mRetriever.release();
    }

    protected void setDataSourceFd(int resid) {
        try {
            AssetFileDescriptor afd = mResources.openRawResourceFd(resid);
            mRetriever.setDataSource(afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
            afd.close();
        } catch (Exception e) {
            fail("Unable to open file");
        }
    }

    public void testID3v2EmbeddedPicture() {
        setDataSourceFd(R.raw.id3v2_3_extended_header_overflow_padding);

        assertEquals("EmbeddedPicture was other than expected null array",
                null, mRetriever.getEmbeddedPicture());
    }
}
