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

package android.media.cts;

import android.media.AudioFormat;
import android.os.Parcel;

import com.android.compatibility.common.util.CtsAndroidTestCase;

public class AudioFormatTest extends CtsAndroidTestCase {

    // -----------------------------------------------------------------
    // AUDIOFORMAT TESTS:
    // ----------------------------------

    // -----------------------------------------------------------------
    // Builder tests
    // ----------------------------------

    // Test case 1: Use Builder to duplicate an AudioFormat with all fields supplied
    public void testBuilderForCopy() throws Exception {
        final int TEST_ENCODING = AudioFormat.ENCODING_PCM_16BIT;
        final int TEST_SR = 48000;
        final int TEST_CONF_POS = AudioFormat.CHANNEL_OUT_5POINT1;
        // 6ch, like in 5.1 above offset by a randomly chosen number
        final int TEST_CONF_IDX = 0x3F << 3;

        final AudioFormat formatToCopy = new AudioFormat.Builder()
                .setEncoding(TEST_ENCODING).setSampleRate(TEST_SR)
                .setChannelMask(TEST_CONF_POS).setChannelIndexMask(TEST_CONF_IDX).build();
        assertNotNull("Failure to create the AudioFormat to copy", formatToCopy);

        final AudioFormat copiedFormat = new AudioFormat.Builder(formatToCopy).build();
        assertNotNull("Failure to create AudioFormat copy with Builder", copiedFormat);
        assertEquals("New AudioFormat has wrong sample rate",
                TEST_SR, copiedFormat.getSampleRate());
        assertEquals("New AudioFormat has wrong encoding",
                TEST_ENCODING, copiedFormat.getEncoding());
        assertEquals("New AudioFormat has wrong channel mask",
                TEST_CONF_POS, copiedFormat.getChannelMask());
        assertEquals("New AudioFormat has wrong channel index mask",
                TEST_CONF_IDX, copiedFormat.getChannelIndexMask());
    }

    // Test case 2: Use Builder to duplicate an AudioFormat with only encoding supplied
    public void testPartialFormatBuilderForCopyEncoding() throws Exception {
        final int TEST_ENCODING = AudioFormat.ENCODING_PCM_16BIT;

        final AudioFormat formatToCopy = new AudioFormat.Builder()
                .setEncoding(TEST_ENCODING).build();
        assertNotNull("Failure to create the AudioFormat to copy", formatToCopy);

        final AudioFormat copiedFormat = new AudioFormat.Builder(formatToCopy).build();
        assertNotNull("Failure to create AudioFormat copy with Builder", copiedFormat);
        assertEquals("New AudioFormat has wrong encoding",
                TEST_ENCODING, copiedFormat.getEncoding());
        // test expected values when none has been set
        assertEquals("New AudioFormat doesn't report expected sample rate",
                0, copiedFormat.getSampleRate());
        assertEquals("New AudioFormat doesn't report expected channel mask",
                AudioFormat.CHANNEL_INVALID, copiedFormat.getChannelMask());
        assertEquals("New AudioFormat doesn't report expected channel index mask",
                AudioFormat.CHANNEL_INVALID, copiedFormat.getChannelIndexMask());
    }

    // Test case 3: Use Builder to duplicate an AudioFormat with only sample rate supplied
    public void testPartialFormatBuilderForCopyRate() throws Exception {
        final int TEST_SR = 48000;

        final AudioFormat formatToCopy = new AudioFormat.Builder()
                .setSampleRate(TEST_SR).build();
        assertNotNull("Failure to create the AudioFormat to copy", formatToCopy);

        final AudioFormat copiedFormat = new AudioFormat.Builder(formatToCopy).build();
        assertNotNull("Failure to create AudioFormat copy with Builder", copiedFormat);
        assertEquals("New AudioFormat has wrong sample rate",
                TEST_SR, copiedFormat.getSampleRate());
        // test expected values when none has been set
        assertEquals("New AudioFormat doesn't report expected encoding",
                AudioFormat.ENCODING_INVALID, copiedFormat.getEncoding());
        assertEquals("New AudioFormat doesn't report expected channel mask",
                AudioFormat.CHANNEL_INVALID, copiedFormat.getChannelMask());
        assertEquals("New AudioFormat doesn't report expected channel index mask",
                AudioFormat.CHANNEL_INVALID, copiedFormat.getChannelIndexMask());
    }

    // Test case 4: Use Builder to duplicate an AudioFormat with only channel mask supplied
    public void testPartialFormatBuilderForCopyChanMask() throws Exception {
        final int TEST_CONF_POS = AudioFormat.CHANNEL_OUT_5POINT1;

        final AudioFormat formatToCopy = new AudioFormat.Builder()
                .setChannelMask(TEST_CONF_POS).build();
        assertNotNull("Failure to create the AudioFormat to copy", formatToCopy);

        final AudioFormat copiedFormat = new AudioFormat.Builder(formatToCopy).build();
        assertNotNull("Failure to create AudioFormat copy with Builder", copiedFormat);
        assertEquals("New AudioFormat has wrong channel mask",
                TEST_CONF_POS, copiedFormat.getChannelMask());
        // test expected values when none has been set
        assertEquals("New AudioFormat doesn't report expected encoding",
                AudioFormat.ENCODING_INVALID, copiedFormat.getEncoding());
        assertEquals("New AudioFormat doesn't report expected sample rate",
                0, copiedFormat.getSampleRate());
        assertEquals("New AudioFormat doesn't report expected channel index mask",
                AudioFormat.CHANNEL_INVALID, copiedFormat.getChannelIndexMask());
    }


    // Test case 5: Use Builder to duplicate an AudioFormat with only channel index mask supplied
    public void testPartialFormatBuilderForCopyChanIdxMask() throws Exception {
        final int TEST_CONF_IDX = 0x30;

        final AudioFormat formatToCopy = new AudioFormat.Builder()
                .setChannelIndexMask(TEST_CONF_IDX).build();
        assertNotNull("Failure to create the AudioFormat to copy", formatToCopy);

        final AudioFormat copiedFormat = new AudioFormat.Builder(formatToCopy).build();
        assertNotNull("Failure to create AudioFormat copy with Builder", copiedFormat);
        assertEquals("New AudioFormat has wrong channel mask",
                TEST_CONF_IDX, copiedFormat.getChannelIndexMask());
        // test expected values when none has been set
        assertEquals("New AudioFormat doesn't report expected encoding",
                AudioFormat.ENCODING_INVALID, copiedFormat.getEncoding());
        assertEquals("New AudioFormat doesn't report expected sample rate",
                0, copiedFormat.getSampleRate());
        assertEquals("New AudioFormat doesn't report expected channel mask",
                AudioFormat.CHANNEL_INVALID, copiedFormat.getChannelMask());
    }

    // Test case 6: create an instance, marshall it and create a new instance,
    //      check for equality
    public void testParcel() throws Exception {
        final int TEST_ENCODING = AudioFormat.ENCODING_PCM_16BIT;
        final int TEST_SR = 48000;
        final int TEST_CONF_POS = AudioFormat.CHANNEL_OUT_5POINT1;
        // 6ch, like in 5.1 above offset by a randomly chosen number
        final int TEST_CONF_IDX = 0x3F << 3;

        final AudioFormat formatToMarshall = new AudioFormat.Builder()
                .setEncoding(TEST_ENCODING).setSampleRate(TEST_SR)
                .setChannelMask(TEST_CONF_POS).setChannelIndexMask(TEST_CONF_IDX).build();
        assertNotNull("Failure to create the AudioFormat to marshall", formatToMarshall);
        assertEquals(0, formatToMarshall.describeContents());

        final Parcel srcParcel = Parcel.obtain();
        final Parcel dstParcel = Parcel.obtain();

        formatToMarshall.writeToParcel(srcParcel, 0 /*no public flags for marshalling*/);
        final byte[] mbytes = srcParcel.marshall();
        dstParcel.unmarshall(mbytes, 0, mbytes.length);
        dstParcel.setDataPosition(0);
        final AudioFormat unmarshalledFormat = AudioFormat.CREATOR.createFromParcel(dstParcel);

        assertNotNull("Failure to unmarshall AudioFormat", unmarshalledFormat);
        assertEquals("Source and destination AudioFormat not equal",
                formatToMarshall, unmarshalledFormat);
    }
}
