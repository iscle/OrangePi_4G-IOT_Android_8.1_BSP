/*
 * Copyright (C) 2012 The Android Open Source Project
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

import android.content.pm.PackageManager;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecInfo.CodecProfileLevel;
import android.media.MediaCodecInfo.CodecCapabilities;
import android.media.MediaCodecInfo.AudioCapabilities;
import android.media.MediaCodecInfo.VideoCapabilities;
import android.media.MediaCodecInfo.EncoderCapabilities;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.test.AndroidTestCase;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MediaCodecListTest extends AndroidTestCase {

    private static final String TAG = "MediaCodecListTest";
    private static final String MEDIA_CODEC_XML_FILE = "/etc/media_codecs.xml";
    private static final String VENDOR_MEDIA_CODEC_XML_FILE = "/vendor/etc/media_codecs.xml";
    private static final String ODM_MEDIA_CODEC_XML_FILE = "/odm/etc/media_codecs.xml";
    private final MediaCodecList mRegularCodecs =
            new MediaCodecList(MediaCodecList.REGULAR_CODECS);
    private final MediaCodecList mAllCodecs =
            new MediaCodecList(MediaCodecList.ALL_CODECS);
    private final MediaCodecInfo[] mRegularInfos =
            mRegularCodecs.getCodecInfos();
    private final MediaCodecInfo[] mAllInfos =
            mAllCodecs.getCodecInfos();

    class CodecType {
        CodecType(String type, boolean isEncoder, MediaFormat sampleFormat) {
            mMimeTypeName = type;
            mIsEncoder = isEncoder;
            mSampleFormat = sampleFormat;
        }

        boolean equals(CodecType codecType) {
            return (mMimeTypeName.compareTo(codecType.mMimeTypeName) == 0) &&
                    mIsEncoder == codecType.mIsEncoder;
        }

        boolean canBeFound() {
            return codecCanBeFound(mIsEncoder, mSampleFormat);
        }

        @Override
        public String toString() {
            return mMimeTypeName + (mIsEncoder ? " encoder" : " decoder") + " for " + mSampleFormat;
        }

        private String mMimeTypeName;
        private boolean mIsEncoder;
        private MediaFormat mSampleFormat;
    };

    class AudioCodec extends CodecType {
        AudioCodec(String mime, boolean isEncoder, int sampleRate) {
            super(mime, isEncoder, MediaFormat.createAudioFormat(
                    mime, sampleRate, 1 /* channelCount */));
        }
    }

    class VideoCodec extends CodecType {
        VideoCodec(String mime, boolean isEncoder) {
            // implicit assumption that QVGA video is always valid
            super(mime, isEncoder, MediaFormat.createVideoFormat(
                    mime, 176 /* width */, 144 /* height */));
        }
    }

    public static void testMediaCodecXmlFileExist() {
        File file = new File(MEDIA_CODEC_XML_FILE);
        File vendorFile = new File(VENDOR_MEDIA_CODEC_XML_FILE);
        File odmFile = new File(ODM_MEDIA_CODEC_XML_FILE);
        assertTrue("media_codecs.xml does not exist in /odm/etc, /vendor/etc or /etc.",
                file.exists() || vendorFile.exists() || odmFile.exists());
    }

    private MediaCodecInfo[] getLegacyInfos() {
        Log.d(TAG, "getLegacyInfos");

        int codecCount = MediaCodecList.getCodecCount();
        MediaCodecInfo[] res = new MediaCodecInfo[codecCount];

        for (int i = 0; i < codecCount; ++i) {
            res[i] = MediaCodecList.getCodecInfoAt(i);
        }
        return res;
    }

    public void assertEqualsOrSuperset(Set big, Set tiny, boolean superset) {
        if (!superset) {
            assertEquals(big, tiny);
        } else {
            assertTrue(big.containsAll(tiny));
        }
    }

    private static <T> Set<T> asSet(T[] array) {
        Set<T> s = new HashSet<T>();
        for (T el : array) {
            s.add(el);
        }
        return s;
    }

    private static Set<Integer> asSet(int[] array) {
        Set<Integer> s = new HashSet<Integer>();
        for (int el : array) {
            s.add(el);
        }
        return s;
    }

    public void assertEqualsOrSuperset(
            CodecCapabilities big, CodecCapabilities tiny, boolean superset) {
        // ordering of enumerations may differ
        assertEqualsOrSuperset(asSet(big.colorFormats), asSet(tiny.colorFormats), superset);
        assertEqualsOrSuperset(asSet(big.profileLevels), asSet(tiny.profileLevels), superset);
        AudioCapabilities bigAudCaps = big.getAudioCapabilities();
        VideoCapabilities bigVidCaps = big.getVideoCapabilities();
        EncoderCapabilities bigEncCaps = big.getEncoderCapabilities();
        AudioCapabilities tinyAudCaps = tiny.getAudioCapabilities();
        VideoCapabilities tinyVidCaps = tiny.getVideoCapabilities();
        EncoderCapabilities tinyEncCaps = tiny.getEncoderCapabilities();
        assertEquals(bigAudCaps != null, tinyAudCaps != null);
        assertEquals(bigAudCaps != null, tinyAudCaps != null);
        assertEquals(bigAudCaps != null, tinyAudCaps != null);
    }

    public void assertEqualsOrSuperset(
            MediaCodecInfo big, MediaCodecInfo tiny, boolean superset) {
        assertEquals(big.getName(), tiny.getName());
        assertEquals(big.isEncoder(), tiny.isEncoder());
        assertEqualsOrSuperset(
                asSet(big.getSupportedTypes()), asSet(tiny.getSupportedTypes()), superset);
        for (String type : big.getSupportedTypes()) {
            assertEqualsOrSuperset(
                    big.getCapabilitiesForType(type),
                    tiny.getCapabilitiesForType(type),
                    superset);
        }
    }

    public void assertSuperset(MediaCodecInfo big, MediaCodecInfo tiny) {
        assertEqualsOrSuperset(big, tiny, true /* superset */);
    }

    public void assertEquals(MediaCodecInfo big, MediaCodecInfo tiny) {
        assertEqualsOrSuperset(big, tiny, false /* superset */);
    }

    // Each component advertised by MediaCodecList should at least be
    // instantiable.
    private void testComponentInstantiation(MediaCodecInfo[] infos) throws IOException {
        for (MediaCodecInfo info : infos) {
            Log.d(TAG, "codec: " + info.getName());
            Log.d(TAG, "  isEncoder = " + info.isEncoder());

            MediaCodec codec = MediaCodec.createByCodecName(info.getName());

            assertEquals(codec.getName(), info.getName());

            assertEquals(codec.getCodecInfo(), info);

            codec.release();
            codec = null;
        }
    }

    public void testRegularComponentInstantiation() throws IOException {
        Log.d(TAG, "testRegularComponentInstantiation");
        testComponentInstantiation(mRegularInfos);
    }

    public void testAllComponentInstantiation() throws IOException {
        Log.d(TAG, "testAllComponentInstantiation");
        testComponentInstantiation(mAllInfos);
    }

    public void testLegacyComponentInstantiation() throws IOException {
        Log.d(TAG, "testLegacyComponentInstantiation");
        testComponentInstantiation(getLegacyInfos());
    }

    // For each type advertised by any of the components we should be able
    // to get capabilities.
    private void testGetCapabilities(MediaCodecInfo[] infos) {
        for (MediaCodecInfo info : infos) {
            Log.d(TAG, "codec: " + info.getName());
            Log.d(TAG, "  isEncoder = " + info.isEncoder());

            String[] types = info.getSupportedTypes();
            for (int j = 0; j < types.length; ++j) {
                Log.d(TAG, "calling getCapabilitiesForType " + types[j]);
                CodecCapabilities cap = info.getCapabilitiesForType(types[j]);
            }
        }
    }

    public void testGetRegularCapabilities() {
        Log.d(TAG, "testGetRegularCapabilities");
        testGetCapabilities(mRegularInfos);
    }

    public void testGetAllCapabilities() {
        Log.d(TAG, "testGetAllCapabilities");
        testGetCapabilities(mAllInfos);
    }

    public void testGetLegacyCapabilities() {
        Log.d(TAG, "testGetLegacyCapabilities");
        testGetCapabilities(getLegacyInfos());
    }

    public void testLegacyMediaCodecListIsSameAsRegular() {
        // regular codecs should be equivalent to legacy codecs, including
        // codec ordering
        MediaCodecInfo[] legacyInfos = getLegacyInfos();
        assertEquals(legacyInfos.length, mRegularInfos.length);
        for (int i = 0; i < legacyInfos.length; ++i) {
            assertEquals(legacyInfos[i], mRegularInfos[i]);
        }
    }

    public void testRegularMediaCodecListIsASubsetOfAll() {
        Log.d(TAG, "testRegularMediaCodecListIsASubsetOfAll");
        // regular codecs should be a subsequence of all codecs, including
        // codec ordering
        int ix = 0;
        for (MediaCodecInfo info : mAllInfos) {
            if (ix == mRegularInfos.length) {
                break;
            }
            if (!mRegularInfos[ix].getName().equals(info.getName())) {
                Log.d(TAG, "skipping non-regular codec " + info.getName());
                continue;
            }
            Log.d(TAG, "checking codec " + info.getName());
            assertSuperset(info, mRegularInfos[ix]);
            ++ix;
        }
        assertEquals(
                "some regular codecs are not listed in all codecs", ix, mRegularInfos.length);
    }

    public void testRequiredMediaCodecList() {
        List<CodecType> requiredList = getRequiredCodecTypes();
        List<CodecType> supportedList = getSupportedCodecTypes();
        assertTrue(areRequiredCodecTypesSupported(requiredList, supportedList));
        for (CodecType type : requiredList) {
            assertTrue("cannot find " + type, type.canBeFound());
        }
    }

    private boolean hasCamera() {
        PackageManager pm = getContext().getPackageManager();
        return pm.hasSystemFeature(pm.FEATURE_CAMERA_FRONT) ||
                pm.hasSystemFeature(pm.FEATURE_CAMERA);
    }

    private boolean hasMicrophone() {
        PackageManager pm = getContext().getPackageManager();
        return pm.hasSystemFeature(pm.FEATURE_MICROPHONE);
    }

    private boolean isWatch() {
        PackageManager pm = getContext().getPackageManager();
        return pm.hasSystemFeature(pm.FEATURE_WATCH);
    }

    private boolean isHandheld() {
        // handheld nature is not exposed to package manager, for now
        // we check for touchscreen and NOT watch and NOT tv
        PackageManager pm = getContext().getPackageManager();
        return pm.hasSystemFeature(pm.FEATURE_TOUCHSCREEN)
                && !pm.hasSystemFeature(pm.FEATURE_WATCH)
                && !pm.hasSystemFeature(pm.FEATURE_TELEVISION);
    }

    // Find whether the given codec can be found using MediaCodecList.find methods.
    private boolean codecCanBeFound(boolean isEncoder, MediaFormat format) {
        String codecName = isEncoder
                ? mRegularCodecs.findEncoderForFormat(format)
                : mRegularCodecs.findDecoderForFormat(format);
        return codecName != null;
    }

    /*
     * Find whether all required media codec types are supported
     */
    private boolean areRequiredCodecTypesSupported(
        List<CodecType> requiredList, List<CodecType> supportedList) {
        for (CodecType requiredCodec: requiredList) {
            boolean isSupported = false;
            for (CodecType supportedCodec: supportedList) {
                if (requiredCodec.equals(supportedCodec)) {
                    isSupported = true;
                }
            }
            if (!isSupported) {
                String codec = requiredCodec.mMimeTypeName
                                + ", " + (requiredCodec.mIsEncoder? "encoder": "decoder");
                Log.e(TAG, "Media codec (" + codec + ") is not supported");
                return false;
            }
        }
        return true;
    }

    /*
     * Find all the media codec types are supported.
     */
    private List<CodecType> getSupportedCodecTypes() {
        List<CodecType> supportedList = new ArrayList<CodecType>();
        for (MediaCodecInfo info : mRegularInfos) {
            String[] types = info.getSupportedTypes();
            assertTrue("Unexpected number of supported types", types.length > 0);
            boolean isEncoder = info.isEncoder();
            for (int j = 0; j < types.length; ++j) {
                supportedList.add(new CodecType(types[j], isEncoder, null /* sampleFormat */));
            }
        }
        return supportedList;
    }

    /*
     * This list should be kept in sync with the CCD document
     * See http://developer.android.com/guide/appendix/media-formats.html
     */
    private List<CodecType> getRequiredCodecTypes() {
        List<CodecType> list = new ArrayList<CodecType>(16);

        // Mandatory audio decoders

        // flac decoder is not omx-based yet
        // list.add(new CodecType(MediaFormat.MIMETYPE_AUDIO_FLAC, false, 8000));
        // list.add(new CodecType(MediaFormat.MIMETYPE_AUDIO_FLAC, false, 48000));
        list.add(new AudioCodec(MediaFormat.MIMETYPE_AUDIO_MPEG, false, 8000));  // mp3
        list.add(new AudioCodec(MediaFormat.MIMETYPE_AUDIO_MPEG, false, 48000)); // mp3
        list.add(new AudioCodec(MediaFormat.MIMETYPE_AUDIO_VORBIS, false, 8000));
        list.add(new AudioCodec(MediaFormat.MIMETYPE_AUDIO_VORBIS, false, 48000));
        list.add(new AudioCodec(MediaFormat.MIMETYPE_AUDIO_AAC, false, 8000));
        list.add(new AudioCodec(MediaFormat.MIMETYPE_AUDIO_AAC, false, 48000));
        list.add(new AudioCodec(MediaFormat.MIMETYPE_AUDIO_RAW, false, 8000));
        list.add(new AudioCodec(MediaFormat.MIMETYPE_AUDIO_RAW, false, 44100));
        list.add(new AudioCodec(MediaFormat.MIMETYPE_AUDIO_OPUS, false, 48000));

        // Mandatory audio encoders (for non-watch devices with camera)

        if (hasMicrophone() && !isWatch()) {
            list.add(new AudioCodec(MediaFormat.MIMETYPE_AUDIO_AAC, true, 8000));
            list.add(new AudioCodec(MediaFormat.MIMETYPE_AUDIO_AAC, true, 48000));
            // flac encoder is not required
            // list.add(new AudioCodec(MediaFormat.MIMETYPE_AUDIO_FLAC, true));  // encoder
        }

        // Mandatory audio encoders for handheld devices
        if (isHandheld()) {
            list.add(new AudioCodec(MediaFormat.MIMETYPE_AUDIO_AMR_NB, false, 8000));  // decoder
            list.add(new AudioCodec(MediaFormat.MIMETYPE_AUDIO_AMR_NB, true,  8000));  // encoder
            list.add(new AudioCodec(MediaFormat.MIMETYPE_AUDIO_AMR_WB, false, 16000)); // decoder
            list.add(new AudioCodec(MediaFormat.MIMETYPE_AUDIO_AMR_WB, true,  16000)); // encoder
        }

        // Mandatory video codecs (for non-watch devices)

        if (!isWatch()) {
            list.add(new VideoCodec(MediaFormat.MIMETYPE_VIDEO_AVC, false));   // avc decoder
            list.add(new VideoCodec(MediaFormat.MIMETYPE_VIDEO_AVC, true));    // avc encoder
            list.add(new VideoCodec(MediaFormat.MIMETYPE_VIDEO_VP8, false));   // vp8 decoder
            list.add(new VideoCodec(MediaFormat.MIMETYPE_VIDEO_VP8, true));    // vp8 encoder
            list.add(new VideoCodec(MediaFormat.MIMETYPE_VIDEO_VP9, false));   // vp9 decoder
            list.add(new VideoCodec(MediaFormat.MIMETYPE_VIDEO_HEVC, false));  // hevc decoder
            list.add(new VideoCodec(MediaFormat.MIMETYPE_VIDEO_MPEG4, false)); // m4v decoder
            list.add(new VideoCodec(MediaFormat.MIMETYPE_VIDEO_H263, false));  // h263 decoder
            if (hasCamera()) {
                list.add(new VideoCodec(MediaFormat.MIMETYPE_VIDEO_H263, true)); // h263 encoder
            }
        }

        return list;
    }

    public void testFindDecoderWithAacProfile() throws Exception {
        Log.d(TAG, "testFindDecoderWithAacProfile");
        MediaFormat format = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_AAC, 8000, 1);
        List<Integer> profiles = new ArrayList<>();
        profiles.add(MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        profiles.add(MediaCodecInfo.CodecProfileLevel.AACObjectHE);
        profiles.add(MediaCodecInfo.CodecProfileLevel.AACObjectHE_PS);
        // The API is added at 5.0, so the profile below must be supported.
        profiles.add(MediaCodecInfo.CodecProfileLevel.AACObjectELD);
        for (int profile : profiles) {
            format.setInteger(MediaFormat.KEY_AAC_PROFILE, profile);
            String codecName = mRegularCodecs.findDecoderForFormat(format);
            assertNotNull("Profile " + profile + " must be supported.", codecName);
        }
    }

    public void testFindEncoderWithAacProfile() throws Exception {
        Log.d(TAG, "testFindEncoderWithAacProfile");
        MediaFormat format = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_AAC, 8000, 1);
        List<Integer> profiles = new ArrayList<>();
        if (hasMicrophone() && !isWatch()) {
            profiles.add(MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            // The API is added at 5.0, so the profiles below must be supported.
            profiles.add(MediaCodecInfo.CodecProfileLevel.AACObjectHE);
            profiles.add(MediaCodecInfo.CodecProfileLevel.AACObjectELD);
        }
        for (int profile : profiles) {
            format.setInteger(MediaFormat.KEY_AAC_PROFILE, profile);
            String codecName = mRegularCodecs.findEncoderForFormat(format);
            assertNotNull("Profile " + profile + " must be supported.", codecName);
        }
    }

    public void testAudioCodecChannels() {
        for (MediaCodecInfo info : mAllInfos) {
            String[] types = info.getSupportedTypes();
            for (int j = 0; j < types.length; ++j) {
                CodecCapabilities cap = info.getCapabilitiesForType(types[j]);
                AudioCapabilities audioCap = cap.getAudioCapabilities();
                if (audioCap == null) {
                    continue;
                }
                int n = audioCap.getMaxInputChannelCount();
                Log.d(TAG, info.getName() + ": " + n);
                assertTrue(info.getName() + " max input channel not positive: " + n, n > 0);
            }
        }
    }
}
