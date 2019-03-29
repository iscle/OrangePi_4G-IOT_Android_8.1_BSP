/*
 * Copyright 2015 The Android Open Source Project
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

package android.media.cts;

import android.media.cts.R;

import android.content.res.AssetFileDescriptor;
import android.content.res.Resources;
import android.media.MediaDataSource;
import android.media.MediaExtractor;
import android.os.PersistableBundle;
import android.test.AndroidTestCase;

import java.io.IOException;
import java.nio.ByteBuffer;

public class MediaExtractorTest extends AndroidTestCase {
    protected Resources mResources;
    protected MediaExtractor mExtractor;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mResources = getContext().getResources();
        mExtractor = new MediaExtractor();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        mExtractor.release();
    }

    protected TestMediaDataSource getDataSourceFor(int resid) throws Exception {
        AssetFileDescriptor afd = mResources.openRawResourceFd(resid);
        return TestMediaDataSource.fromAssetFd(afd);
    }

    protected TestMediaDataSource setDataSource(int resid) throws Exception {
        TestMediaDataSource ds = getDataSourceFor(resid);
        mExtractor.setDataSource(ds);
        return ds;
    }

    public void testNullMediaDataSourceIsRejected() throws Exception {
        try {
            mExtractor.setDataSource((MediaDataSource)null);
            fail("Expected IllegalArgumentException.");
        } catch (IllegalArgumentException ex) {
            // Expected, test passed.
        }
    }

    public void testMediaDataSourceIsClosedOnRelease() throws Exception {
        TestMediaDataSource dataSource = setDataSource(R.raw.testvideo);
        mExtractor.release();
        assertTrue(dataSource.isClosed());
    }

    public void testExtractorFailsIfMediaDataSourceThrows() throws Exception {
        TestMediaDataSource dataSource = getDataSourceFor(R.raw.testvideo);
        dataSource.throwFromReadAt();
        try {
            mExtractor.setDataSource(dataSource);
            fail("Expected IOException.");
        } catch (IOException e) {
            // Expected.
        }
    }

    public void testExtractorFailsIfMediaDataSourceReturnsAnError() throws Exception {
        TestMediaDataSource dataSource = getDataSourceFor(R.raw.testvideo);
        dataSource.returnFromReadAt(-2);
        try {
            mExtractor.setDataSource(dataSource);
            fail("Expected IOException.");
        } catch (IOException e) {
            // Expected.
        }
    }

    // Smoke test MediaExtractor reading from a DataSource.
    public void testExtractFromAMediaDataSource() throws Exception {
        TestMediaDataSource dataSource = setDataSource(R.raw.testvideo);
        // 1MB is enough for any sample.
        final ByteBuffer buf = ByteBuffer.allocate(1024*1024);
        final int trackCount = mExtractor.getTrackCount();

        for (int i = 0; i < trackCount; i++) {
            mExtractor.selectTrack(i);
        }

        for (int i = 0; i < trackCount; i++) {
            assertTrue(mExtractor.readSampleData(buf, 0) > 0);
            assertTrue(mExtractor.advance());
        }

        // verify some getMetrics() behaviors while we're here.
        PersistableBundle metrics = mExtractor.getMetrics();
        if (metrics == null) {
            fail("getMetrics() returns no data");
        } else {
            // ensure existence of some known fields
            int tracks = metrics.getInt(MediaExtractor.MetricsConstants.TRACKS, -1);
            if (tracks != trackCount) {
                fail("getMetrics() trackCount expect " + trackCount + " got " + tracks);
            }
        }

    }
}
