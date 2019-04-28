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

package com.android.documentsui;

import static junit.framework.Assert.assertEquals;

import android.annotation.StringRes;
import android.content.Context;
import android.content.res.Resources;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;

import libcore.net.MimeUtils;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@SmallTest
public class FileTypeMapTest {

    private Resources mRes;
    private FileTypeMap mMap;

    @Before
    public void setUp() {
        Context context = InstrumentationRegistry.getTargetContext();
        mRes = context.getResources();
        mMap = new FileTypeMap(context);
    }

    @Test
    public void testPlainTextType() {
        String expected = mRes.getString(R.string.txt_file_type);
        assertEquals(expected, mMap.lookup("text/plain"));
    }

    @Test
    public void testPortableDocumentFormatType() {
        String expected = mRes.getString(R.string.pdf_file_type);
        assertEquals(expected, mMap.lookup("application/pdf"));
    }

    @Test
    public void testMsWordType() {
        String expected = mRes.getString(R.string.word_file_type);
        assertEquals(expected, mMap.lookup("application/msword"));
    }

    @Test
    public void testGoogleDocType() {
        String expected = mRes.getString(R.string.gdoc_file_type);
        assertEquals(expected, mMap.lookup("application/vnd.google-apps.document"));
    }

    @Test
    public void testZipType() {
        final String mime = "application/zip";
        String expected = getExtensionTypeFromExtension(R.string.archive_file_type, "Zip");
        assertEquals(expected, mMap.lookup(mime));
    }

    @Test
    public void testMp3Type() {
        final String mime = "audio/mpeg";
        String expected = getExtensionTypeFromMime(R.string.audio_extension_file_type, mime);
        assertEquals(expected, mMap.lookup(mime));
    }

    @Test
    public void testMkvType() {
        final String mime = "video/avi";
        String expected = getExtensionTypeFromMime(R.string.video_extension_file_type, mime);
        assertEquals(expected, mMap.lookup(mime));
    }

    @Test
    public void testJpgType() {
        final String mime = "image/jpeg";
        String expected = getExtensionTypeFromMime(R.string.image_extension_file_type, mime);
        assertEquals(expected, mMap.lookup(mime));
    }

    @Test
    public void testOggType() {
        final String mime = "application/ogg";
        String expected = getExtensionTypeFromMime(R.string.audio_extension_file_type, mime);
        assertEquals(expected, mMap.lookup("application/ogg"));
    }

    @Test
    public void testFlacType() {
        final String mime = "application/x-flac";
        String expected = getExtensionTypeFromMime(R.string.audio_extension_file_type, mime);
        assertEquals(expected, mMap.lookup(mime));
    }

    private String getExtensionTypeFromMime(@StringRes int formatStringId, String mime) {
        final String extension = MimeUtils.guessExtensionFromMimeType(mime).toUpperCase();
        return getExtensionTypeFromExtension(formatStringId, extension);
    }

    private String getExtensionTypeFromExtension(@StringRes int formatStringId, String extension) {
        final String format = mRes.getString(formatStringId);
        return String.format(format, extension);
    }
}
