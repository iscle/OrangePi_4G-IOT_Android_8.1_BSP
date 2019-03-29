/*
 * Copyright (C) 2013 The Android Open Source Project
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

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.content.res.Resources;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMetadataRetriever;
import android.media.MediaMuxer;
import android.os.ParcelFileDescriptor;
import android.test.AndroidTestCase;
import android.util.Log;

import android.media.cts.R;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.util.HashMap;

public class MediaMuxerTest extends AndroidTestCase {
    private static final String TAG = "MediaMuxerTest";
    private static final boolean VERBOSE = false;
    private static final int MAX_SAMPLE_SIZE = 256 * 1024;
    private static final float LATITUDE = 0.0000f;
    private static final float LONGITUDE  = -180.0f;
    private static final float BAD_LATITUDE = 91.0f;
    private static final float BAD_LONGITUDE = -181.0f;
    private static final float TOLERANCE = 0.0002f;
    private Resources mResources;

    @Override
    public void setContext(Context context) {
        super.setContext(context);
        mResources = context.getResources();
    }

    /**
     * Test: make sure the muxer handles both video and audio tracks correctly.
     */
    public void testVideoAudio() throws Exception {
        int source = R.raw.video_176x144_3gp_h263_300kbps_25fps_aac_stereo_128kbps_11025hz;
        String outputFile = File.createTempFile("MediaMuxerTest_testAudioVideo", ".mp4")
                .getAbsolutePath();
        cloneAndVerify(source, outputFile, 2, 90, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
    }

    public void testDualVideoTrack() throws Exception {
        int source = R.raw.video_176x144_h264_408kbps_30fps_352x288_h264_122kbps_30fps;
        String outputFile = File.createTempFile("MediaMuxerTest_testDualVideo", ".mp4")
                .getAbsolutePath();
        cloneAndVerify(source, outputFile, 2, 90, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
    }

    public void testDualAudioTrack() throws Exception {
        int source = R.raw.audio_aac_mono_70kbs_44100hz_aac_mono_70kbs_44100hz;
        String outputFile = File.createTempFile("MediaMuxerTest_testDualAudio", ".mp4")
                .getAbsolutePath();
        cloneAndVerify(source, outputFile, 2, 90, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
    }

    public void testDualVideoAndAudioTrack() throws Exception {
        int source = R.raw.video_h264_30fps_video_h264_30fps_aac_44100hz_aac_44100hz;
        String outputFile = File.createTempFile("MediaMuxerTest_testDualVideoAudio", ".mp4")
                .getAbsolutePath();
        cloneAndVerify(source, outputFile, 4, 90, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
    }

    /**
     * Test: make sure the muxer handles video, audio and metadata tracks correctly.
     */
    public void testVideoAudioMedatadata() throws Exception {
        int source =
                R.raw.video_176x144_3gp_h263_300kbps_25fps_aac_stereo_128kbps_11025hz_metadata_gyro;
        String outputFile = File.createTempFile("MediaMuxerTest_testAudioVideoMetadata", ".mp4")
                .getAbsolutePath();
        cloneAndVerify(source, outputFile, 3, 90, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
    }

    /**
     * Test: make sure the muxer handles audio track only file correctly.
     */
    public void testAudioOnly() throws Exception {
        int source = R.raw.sinesweepm4a;
        String outputFile = File.createTempFile("MediaMuxerTest_testAudioOnly", ".mp4")
                .getAbsolutePath();
        cloneAndVerify(source, outputFile, 1, -1, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
    }

    /**
     * Test: make sure the muxer handles video track only file correctly.
     */
    public void testVideoOnly() throws Exception {
        int source = R.raw.video_only_176x144_3gp_h263_25fps;
        String outputFile = File.createTempFile("MediaMuxerTest_videoOnly", ".mp4")
                .getAbsolutePath();
        cloneAndVerify(source, outputFile, 1, 180, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
    }

    public void testWebmOutput() throws Exception {
        int source = R.raw.video_480x360_webm_vp9_333kbps_25fps_vorbis_stereo_128kbps_48000hz;
        String outputFile = File.createTempFile("testWebmOutput", ".webm")
                .getAbsolutePath();
        cloneAndVerify(source, outputFile, 2, 90, MediaMuxer.OutputFormat.MUXER_OUTPUT_WEBM);
    }

    public void testThreegppOutput() throws Exception {
        int source = R.raw.video_176x144_3gp_h263_300kbps_12fps_aac_stereo_128kbps_22050hz;
        String outputFile = File.createTempFile("testThreegppOutput", ".3gp")
                .getAbsolutePath();
        cloneAndVerify(source, outputFile, 2, 90, MediaMuxer.OutputFormat.MUXER_OUTPUT_3GPP);
    }

    /**
     * Tests: make sure the muxer handles exceptions correctly.
     * <br> Throws exception b/c start() is not called.
     * <br> Throws exception b/c 2 video tracks were added.
     * <br> Throws exception b/c 2 audio tracks were added.
     * <br> Throws exception b/c 3 tracks were added.
     * <br> Throws exception b/c no tracks was added.
     * <br> Throws exception b/c a wrong format.
     */
    public void testIllegalStateExceptions() throws IOException {
        String outputFile = File.createTempFile("MediaMuxerTest_testISEs", ".mp4")
                .getAbsolutePath();
        MediaMuxer muxer;

        // Throws exception b/c start() is not called.
        muxer = new MediaMuxer(outputFile, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        muxer.addTrack(MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, 480, 320));

        try {
            muxer.stop();
            fail("should throw IllegalStateException.");
        } catch (IllegalStateException e) {
            // expected
        } finally {
            muxer.release();
        }

        // Should not throw exception when 2 video tracks were added.
        muxer = new MediaMuxer(outputFile, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        muxer.addTrack(MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, 480, 320));

        try {
            muxer.addTrack(MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, 480, 320));
        } catch (IllegalStateException e) {
            fail("should not throw IllegalStateException.");
        } finally {
            muxer.release();
        }

        // Should not throw exception when 2 audio tracks were added.
        muxer = new MediaMuxer(outputFile, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        muxer.addTrack(MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, 48000, 1));
        try {
            muxer.addTrack(MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, 48000, 1));
        } catch (IllegalStateException e) {
            fail("should not throw IllegalStateException.");
        } finally {
            muxer.release();
        }

        // Should not throw exception when 3 tracks were added.
        muxer = new MediaMuxer(outputFile, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        muxer.addTrack(MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, 480, 320));
        muxer.addTrack(MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, 48000, 1));
        try {
            muxer.addTrack(MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, 480, 320));
        } catch (IllegalStateException e) {
            fail("should not throw IllegalStateException.");
        } finally {
            muxer.release();
        }

        // Throws exception b/c no tracks was added.
        muxer = new MediaMuxer(outputFile, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        try {
            muxer.start();
            fail("should throw IllegalStateException.");
        } catch (IllegalStateException e) {
            // expected
        } finally {
            muxer.release();
        }

        // Throws exception b/c a wrong format.
        muxer = new MediaMuxer(outputFile, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        try {
            muxer.addTrack(MediaFormat.createVideoFormat("vidoe/mp4", 480, 320));
            fail("should throw IllegalStateException.");
        } catch (IllegalStateException e) {
            // expected
        } finally {
            muxer.release();
        }

        // Test FileDescriptor Constructor expect sucess.
        RandomAccessFile file = null;
        try {
            file = new RandomAccessFile(outputFile, "rws");
            muxer = new MediaMuxer(file.getFD(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            muxer.addTrack(MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, 480, 320));
        } finally {
            file.close();
            muxer.release();
        }

        // Test FileDescriptor Constructor expect exception with read only mode.
        RandomAccessFile file2 = null;
        try {
            file2 = new RandomAccessFile(outputFile, "r");
            muxer = new MediaMuxer(file2.getFD(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            fail("should throw IOException.");
        } catch (IOException e) {
            // expected
        } finally {
            file2.close();
            // No need to release the muxer.
        }

        // Test FileDescriptor Constructor expect NO exception with write only mode.
        ParcelFileDescriptor out = null;
        try {
            out = ParcelFileDescriptor.open(new File(outputFile),
                    ParcelFileDescriptor.MODE_WRITE_ONLY | ParcelFileDescriptor.MODE_CREATE);
            muxer = new MediaMuxer(out.getFileDescriptor(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        } catch (IllegalArgumentException e) {
            fail("should not throw IllegalArgumentException.");
        } catch (IOException e) {
            fail("should not throw IOException.");
        } finally {
            out.close();
            muxer.release();
        }

        new File(outputFile).delete();
    }

    /**
     * Using the MediaMuxer to clone a media file.
     */
    private void cloneMediaUsingMuxer(int srcMedia, String dstMediaPath,
            int expectedTrackCount, int degrees, int fmt) throws IOException {
        // Set up MediaExtractor to read from the source.
        AssetFileDescriptor srcFd = mResources.openRawResourceFd(srcMedia);
        MediaExtractor extractor = new MediaExtractor();
        extractor.setDataSource(srcFd.getFileDescriptor(), srcFd.getStartOffset(),
                srcFd.getLength());

        int trackCount = extractor.getTrackCount();
        assertEquals("wrong number of tracks", expectedTrackCount, trackCount);

        // Set up MediaMuxer for the destination.
        MediaMuxer muxer;
        muxer = new MediaMuxer(dstMediaPath, fmt);

        // Set up the tracks.
        HashMap<Integer, Integer> indexMap = new HashMap<Integer, Integer>(trackCount);
        for (int i = 0; i < trackCount; i++) {
            extractor.selectTrack(i);
            MediaFormat format = extractor.getTrackFormat(i);
            int dstIndex = muxer.addTrack(format);
            indexMap.put(i, dstIndex);
        }

        // Copy the samples from MediaExtractor to MediaMuxer.
        boolean sawEOS = false;
        int bufferSize = MAX_SAMPLE_SIZE;
        int frameCount = 0;
        int offset = 100;

        ByteBuffer dstBuf = ByteBuffer.allocate(bufferSize);
        BufferInfo bufferInfo = new BufferInfo();

        if (degrees >= 0) {
            muxer.setOrientationHint(degrees);
        }

        if (fmt == MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4 ||
            fmt == MediaMuxer.OutputFormat.MUXER_OUTPUT_3GPP) {
            // Test setLocation out of bound cases
            try {
                muxer.setLocation(BAD_LATITUDE, LONGITUDE);
                fail("setLocation succeeded with bad argument: [" + BAD_LATITUDE + "," + LONGITUDE
                    + "]");
            } catch (IllegalArgumentException e) {
                // Expected
            }
            try {
                muxer.setLocation(LATITUDE, BAD_LONGITUDE);
                fail("setLocation succeeded with bad argument: [" + LATITUDE + "," + BAD_LONGITUDE
                    + "]");
            } catch (IllegalArgumentException e) {
                // Expected
            }

            muxer.setLocation(LATITUDE, LONGITUDE);
        }

        muxer.start();
        while (!sawEOS) {
            bufferInfo.offset = offset;
            bufferInfo.size = extractor.readSampleData(dstBuf, offset);

            if (bufferInfo.size < 0) {
                if (VERBOSE) {
                    Log.d(TAG, "saw input EOS.");
                }
                sawEOS = true;
                bufferInfo.size = 0;
            } else {
                bufferInfo.presentationTimeUs = extractor.getSampleTime();
                bufferInfo.flags = extractor.getSampleFlags();
                int trackIndex = extractor.getSampleTrackIndex();

                muxer.writeSampleData(indexMap.get(trackIndex), dstBuf,
                        bufferInfo);
                extractor.advance();

                frameCount++;
                if (VERBOSE) {
                    Log.d(TAG, "Frame (" + frameCount + ") " +
                            "PresentationTimeUs:" + bufferInfo.presentationTimeUs +
                            " Flags:" + bufferInfo.flags +
                            " TrackIndex:" + trackIndex +
                            " Size(KB) " + bufferInfo.size / 1024);
                }
            }
        }

        muxer.stop();
        muxer.release();
        srcFd.close();
        return;
    }

    /**
     * Clones a media file and then compares against the source file to make
     * sure they match.
     */
    private void cloneAndVerify(int srcMedia, String outputMediaFile,
            int expectedTrackCount, int degrees, int fmt) throws IOException {
        try {
            cloneMediaUsingMuxer(srcMedia, outputMediaFile, expectedTrackCount,
                    degrees, fmt);
            if (fmt == MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4 ||
                    fmt == MediaMuxer.OutputFormat.MUXER_OUTPUT_3GPP) {
                verifyAttributesMatch(srcMedia, outputMediaFile, degrees);
                verifyLocationInFile(outputMediaFile);
            }
            // Check the sample on 1s and 0.5s.
            verifySamplesMatch(srcMedia, outputMediaFile, 1000000);
            verifySamplesMatch(srcMedia, outputMediaFile, 500000);
        } finally {
            new File(outputMediaFile).delete();
        }
    }

    /**
     * Compares some attributes using MediaMetadataRetriever to make sure the
     * cloned media file matches the source file.
     */
    private void verifyAttributesMatch(int srcMedia, String testMediaPath,
            int degrees) {
        AssetFileDescriptor testFd = mResources.openRawResourceFd(srcMedia);

        MediaMetadataRetriever retrieverSrc = new MediaMetadataRetriever();
        retrieverSrc.setDataSource(testFd.getFileDescriptor(),
                testFd.getStartOffset(), testFd.getLength());

        MediaMetadataRetriever retrieverTest = new MediaMetadataRetriever();
        retrieverTest.setDataSource(testMediaPath);

        String testDegrees = retrieverTest.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION);
        if (testDegrees != null) {
            assertEquals("Different degrees", degrees,
                    Integer.parseInt(testDegrees));
        }

        String heightSrc = retrieverSrc.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
        String heightTest = retrieverTest.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
        assertEquals("Different height", heightSrc,
                heightTest);

        String widthSrc = retrieverSrc.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
        String widthTest = retrieverTest.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
        assertEquals("Different height", widthSrc,
                widthTest);

        String durationSrc = retrieverSrc.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
        String durationTest = retrieverTest.extractMetadata(
                MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
        assertEquals("Different height", durationSrc,
                durationTest);

        retrieverSrc.release();
        retrieverTest.release();
    }

    /**
     * Uses 2 MediaExtractor, seeking to the same position, reads the sample and
     * makes sure the samples match.
     */
    private void verifySamplesMatch(int srcMedia, String testMediaPath,
            int seekToUs) throws IOException {
        AssetFileDescriptor testFd = mResources.openRawResourceFd(srcMedia);
        MediaExtractor extractorSrc = new MediaExtractor();
        extractorSrc.setDataSource(testFd.getFileDescriptor(),
                testFd.getStartOffset(), testFd.getLength());
        int trackCount = extractorSrc.getTrackCount();

        MediaExtractor extractorTest = new MediaExtractor();
        extractorTest.setDataSource(testMediaPath);

        assertEquals("wrong number of tracks", trackCount,
                extractorTest.getTrackCount());

        // Make sure the format is the same and select them
        for (int i = 0; i < trackCount; i++) {
            MediaFormat formatSrc = extractorSrc.getTrackFormat(i);
            MediaFormat formatTest = extractorTest.getTrackFormat(i);

            String mimeIn = formatSrc.getString(MediaFormat.KEY_MIME);
            String mimeOut = formatTest.getString(MediaFormat.KEY_MIME);
            if (!(mimeIn.equals(mimeOut))) {
                fail("format didn't match on track No." + i +
                        formatSrc.toString() + "\n" + formatTest.toString());
            }
            extractorSrc.selectTrack(i);
            extractorTest.selectTrack(i);
        }
        // Pick a time and try to compare the frame.
        extractorSrc.seekTo(seekToUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
        extractorTest.seekTo(seekToUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC);

        int bufferSize = MAX_SAMPLE_SIZE;
        ByteBuffer byteBufSrc = ByteBuffer.allocate(bufferSize);
        ByteBuffer byteBufTest = ByteBuffer.allocate(bufferSize);

        extractorSrc.readSampleData(byteBufSrc, 0);
        extractorTest.readSampleData(byteBufTest, 0);

        if (!(byteBufSrc.equals(byteBufTest))) {
            fail("byteBuffer didn't match");
        }
    }

    private void verifyLocationInFile(String fileName) {
        MediaMetadataRetriever retriever = new MediaMetadataRetriever();
        retriever.setDataSource(fileName);
        String location = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_LOCATION);
        assertNotNull("No location information found in file " + fileName, location);


        // parsing String location and recover the location information in floats
        // Make sure the tolerance is very small - due to rounding errors.

        // Get the position of the -/+ sign in location String, which indicates
        // the beginning of the longitude.
        int minusIndex = location.lastIndexOf('-');
        int plusIndex = location.lastIndexOf('+');

        assertTrue("+ or - is not found or found only at the beginning [" + location + "]",
                (minusIndex > 0 || plusIndex > 0));
        int index = Math.max(minusIndex, plusIndex);

        float latitude = Float.parseFloat(location.substring(0, index - 1));
        int lastIndex = location.lastIndexOf('/', index);
        if (lastIndex == -1) {
            lastIndex = location.length();
        }
        float longitude = Float.parseFloat(location.substring(index, lastIndex - 1));
        assertTrue("Incorrect latitude: " + latitude + " [" + location + "]",
                Math.abs(latitude - LATITUDE) <= TOLERANCE);
        assertTrue("Incorrect longitude: " + longitude + " [" + location + "]",
                Math.abs(longitude - LONGITUDE) <= TOLERANCE);
        retriever.release();
    }
}

