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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Movie;
import android.graphics.Paint;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.WidgetTestUtils;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class MovieTest {
    private final int MOVIE = R.drawable.animated;

    private Context mContext;
    private Resources mResources;
    private Movie mMovie;

    @Before
    public void setup() {
        mContext = InstrumentationRegistry.getTargetContext();
        mResources = mContext.getResources();
        mMovie = mResources.getMovie(MOVIE);
    }

    @Test
    public void testDraw1() {
        Canvas c = new Canvas();
        Paint p = new Paint();
        mMovie.draw(c, 100, 200, p);
    }

    @Test
    public void testDraw2() {
        Canvas c = new Canvas();
        mMovie.draw(c, 100, 200);
    }

    @Test
    public void testDecodeFile() throws Exception {
        File dbDir = mContext.getDir("tests", Context.MODE_PRIVATE);
        File imagefile = new File(dbDir, "animated.gif");
        if (imagefile.exists()) {
            imagefile.delete();
        }
        writeSampleImage(imagefile);
        mMovie = Movie.decodeFile(imagefile.getPath());
        assertNotNull(mMovie);

        mMovie = Movie.decodeFile("/no file path");
        assertNull(mMovie);
    }

    private void writeSampleImage(File imagefile) throws Exception {
        try (InputStream source = mResources.openRawResource(MOVIE);
             OutputStream target = new FileOutputStream(imagefile)) {

            byte[] buffer = new byte[1024];
            for (int len = source.read(buffer); len > 0; len = source
                    .read(buffer)) {
                target.write(buffer, 0, len);
            }
        }
    }

    private byte[] inputStreamToBytes(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream(1024);
        byte[] buffer = new byte[1024];
        int len;
        while ((len = in.read(buffer)) >= 0) {
            out.write(buffer, 0, len);
        }
        in.close();
        out.close();
        return out.toByteArray();

    }

    @Test
    public void testDecodeByteArray() throws Exception {
        InputStream is = mResources.openRawResource(MOVIE);
        byte[] bytes = inputStreamToBytes(is);
        mMovie = Movie.decodeByteArray(bytes, 0, bytes.length);
        is.close();
        assertNotNull(mMovie);
    }

    @Test
    public void testDecodeStream() throws IOException {
        assertFalse(mMovie.isOpaque());
        mMovie = null;
        try (InputStream is = mResources.openRawResource(MOVIE)) {
            mMovie = Movie.decodeStream(is);
        }
        assertNotNull(mMovie);
    }

    @Test
    public void testSetTime() {
        assertTrue(mMovie.setTime(1000));
        assertFalse(mMovie.setTime(Integer.MAX_VALUE));
        assertFalse(mMovie.setTime(Integer.MIN_VALUE));
        assertFalse(mMovie.setTime(-1));
    }

    @Test
    public void testGetMovieProperties() {
        assertEquals(1000, mMovie.duration());
        assertFalse(mMovie.isOpaque());

        int expectedHeight = mResources.getDrawable(MOVIE).getIntrinsicHeight();
        int scaledHeight = WidgetTestUtils.convertDipToPixels(mContext, mMovie.height());
        assertEquals(expectedHeight, scaledHeight);

        int expectedWidth = mResources.getDrawable(MOVIE).getIntrinsicWidth();
        int scaledWidth = WidgetTestUtils.convertDipToPixels(mContext, mMovie.width());
        assertEquals(expectedWidth, scaledWidth);
    }
}
