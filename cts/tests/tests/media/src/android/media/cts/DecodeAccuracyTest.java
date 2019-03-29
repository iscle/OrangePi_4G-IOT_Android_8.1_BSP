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
package android.media.cts;

import static junit.framework.TestCase.assertTrue;

import static org.junit.Assert.fail;

import android.media.cts.R;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Bitmap;
import android.media.MediaFormat;
import android.util.Log;
import android.view.View;

import com.android.compatibility.common.util.MediaUtils;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.junit.Test;

@TargetApi(24)
@RunWith(Parameterized.class)
public class DecodeAccuracyTest extends DecodeAccuracyTestBase {

    private static final String TAG = DecodeAccuracyTest.class.getSimpleName();
    private static final Field[] fields = R.raw.class.getFields();
    private static final int ALLOWED_GREATEST_PIXEL_DIFFERENCE = 90;
    private static final int OFFSET = 10;
    private static final long PER_TEST_TIMEOUT_MS = 60000;
    private static final String[] VIDEO_FILES = {
        // 144p
        "video_decode_accuracy_and_capability-h264_256x108_30fps.mp4",
        "video_decode_accuracy_and_capability-h264_256x144_30fps.mp4",
        "video_decode_accuracy_and_capability-h264_192x144_30fps.mp4",
        "video_decode_accuracy_and_capability-h264_82x144_30fps.mp4",
        "video_decode_accuracy_and_capability-vp9_256x108_30fps.webm",
        "video_decode_accuracy_and_capability-vp9_256x144_30fps.webm",
        "video_decode_accuracy_and_capability-vp9_192x144_30fps.webm",
        "video_decode_accuracy_and_capability-vp9_82x144_30fps.webm",
        // 240p
        "video_decode_accuracy_and_capability-h264_426x182_30fps.mp4",
        "video_decode_accuracy_and_capability-h264_426x240_30fps.mp4",
        "video_decode_accuracy_and_capability-h264_320x240_30fps.mp4",
        "video_decode_accuracy_and_capability-h264_136x240_30fps.mp4",
        "video_decode_accuracy_and_capability-vp9_426x182_30fps.webm",
        "video_decode_accuracy_and_capability-vp9_426x240_30fps.webm",
        "video_decode_accuracy_and_capability-vp9_320x240_30fps.webm",
        "video_decode_accuracy_and_capability-vp9_136x240_30fps.webm",
        // 360p
        "video_decode_accuracy_and_capability-h264_640x272_30fps.mp4",
        "video_decode_accuracy_and_capability-h264_640x360_30fps.mp4",
        "video_decode_accuracy_and_capability-h264_480x360_30fps.mp4",
        "video_decode_accuracy_and_capability-h264_202x360_30fps.mp4",
        "video_decode_accuracy_and_capability-vp9_640x272_30fps.webm",
        "video_decode_accuracy_and_capability-vp9_640x360_30fps.webm",
        "video_decode_accuracy_and_capability-vp9_480x360_30fps.webm",
        "video_decode_accuracy_and_capability-vp9_202x360_30fps.webm",
        // 480p
        "video_decode_accuracy_and_capability-h264_854x362_30fps.mp4",
        "video_decode_accuracy_and_capability-h264_854x480_30fps.mp4",
        "video_decode_accuracy_and_capability-h264_640x480_30fps.mp4",
        "video_decode_accuracy_and_capability-h264_270x480_30fps.mp4",
        "video_decode_accuracy_and_capability-vp9_854x362_30fps.webm",
        "video_decode_accuracy_and_capability-vp9_854x480_30fps.webm",
        "video_decode_accuracy_and_capability-vp9_640x480_30fps.webm",
        "video_decode_accuracy_and_capability-vp9_270x480_30fps.webm",
        // 720p
        "video_decode_accuracy_and_capability-h264_1280x544_30fps.mp4",
        "video_decode_accuracy_and_capability-h264_1280x720_30fps.mp4",
        "video_decode_accuracy_and_capability-h264_960x720_30fps.mp4",
        "video_decode_accuracy_and_capability-h264_406x720_30fps.mp4",
        "video_decode_accuracy_and_capability-vp9_1280x544_30fps.webm",
        "video_decode_accuracy_and_capability-vp9_1280x720_30fps.webm",
        "video_decode_accuracy_and_capability-vp9_960x720_30fps.webm",
        "video_decode_accuracy_and_capability-vp9_406x720_30fps.webm",
        // 1080p
        "video_decode_accuracy_and_capability-h264_1920x818_30fps.mp4",
        "video_decode_accuracy_and_capability-h264_1920x1080_30fps.mp4",
        "video_decode_accuracy_and_capability-h264_1440x1080_30fps.mp4",
        "video_decode_accuracy_and_capability-h264_608x1080_30fps.mp4",
        "video_decode_accuracy_and_capability-vp9_1920x818_30fps.webm",
        "video_decode_accuracy_and_capability-vp9_1920x1080_30fps.webm",
        "video_decode_accuracy_and_capability-vp9_1440x1080_30fps.webm",
        "video_decode_accuracy_and_capability-vp9_608x1080_30fps.webm",
        // 1440p
        "video_decode_accuracy_and_capability-h264_2560x1090_30fps.mp4",
        "video_decode_accuracy_and_capability-h264_2560x1440_30fps.mp4",
        "video_decode_accuracy_and_capability-h264_1920x1440_30fps.mp4",
        "video_decode_accuracy_and_capability-h264_810x1440_30fps.mp4",
        "video_decode_accuracy_and_capability-vp9_2560x1090_30fps.webm",
        "video_decode_accuracy_and_capability-vp9_2560x1440_30fps.webm",
        "video_decode_accuracy_and_capability-vp9_1920x1440_30fps.webm",
        "video_decode_accuracy_and_capability-vp9_810x1440_30fps.webm",
        // 2160p
        "video_decode_accuracy_and_capability-h264_3840x1634_30fps.mp4",
        "video_decode_accuracy_and_capability-h264_3840x2160_30fps.mp4",
        "video_decode_accuracy_and_capability-h264_2880x2160_30fps.mp4",
        "video_decode_accuracy_and_capability-h264_1216x2160_30fps.mp4",
        "video_decode_accuracy_and_capability-vp9_3840x1634_30fps.webm",
        "video_decode_accuracy_and_capability-vp9_3840x2160_30fps.webm",
        "video_decode_accuracy_and_capability-vp9_2880x2160_30fps.webm",
        "video_decode_accuracy_and_capability-vp9_1216x2160_30fps.webm",
        // cropped
        "video_decode_with_cropping-h264_520x360_60fps.mp4",
        "video_decode_with_cropping-vp9_520x360_60fps.webm"
    };

    private View videoView;
    private VideoViewFactory videoViewFactory;
    private String fileName;

    @After
    @Override
    public void tearDown() throws Exception {
        if (videoView != null) {
            getHelper().cleanUpView(videoView);
        }
        if (videoViewFactory != null) {
            videoViewFactory.release();
        }
        super.tearDown();
    }

    @Parameters(name = "{0}")
    public static Collection<Object[]> data() {
        final List<Object[]> testParams = new ArrayList<>();
        for (int i = 0; i < VIDEO_FILES.length; i++) {
            final String file = VIDEO_FILES[i];
            Pattern regex = Pattern.compile("^\\w+-(\\w+)_\\d+fps.\\w+");
            Matcher matcher = regex.matcher(file);
            String testName = "";
            if (matcher.matches()) {
                testName = matcher.group(1);
            }
            testParams.add(new Object[] { testName.replace("_", " ").toUpperCase(), file });
        }
        return testParams;
    }

    public DecodeAccuracyTest(String testname, String fileName) {
        this.fileName = fileName;
    }

    @Test(timeout=PER_TEST_TIMEOUT_MS)
    public void testGLViewDecodeAccuracy() throws Exception {
        runTest(new GLSurfaceViewFactory(), new VideoFormat(fileName));
    }

    @Test(timeout=PER_TEST_TIMEOUT_MS)
    public void testGLViewLargerHeightDecodeAccuracy() throws Exception {
        runTest(new GLSurfaceViewFactory(), getLargerHeightVideoFormat(new VideoFormat(fileName)));
    }

    @Test(timeout=PER_TEST_TIMEOUT_MS)
    public void testGLViewLargerWidthDecodeAccuracy() throws Exception {
        runTest(new GLSurfaceViewFactory(), getLargerWidthVideoFormat(new VideoFormat(fileName)));
    }

    @Test(timeout=PER_TEST_TIMEOUT_MS)
    public void testSurfaceViewVideoDecodeAccuracy() throws Exception {
        runTest(new SurfaceViewFactory(), new VideoFormat(fileName));
    }

    @Test(timeout=PER_TEST_TIMEOUT_MS)
    public void testSurfaceViewLargerHeightDecodeAccuracy() throws Exception {
        runTest(new SurfaceViewFactory(), getLargerHeightVideoFormat(new VideoFormat(fileName)));
    }

    @Test(timeout=PER_TEST_TIMEOUT_MS)
    public void testSurfaceViewLargerWidthDecodeAccuracy() throws Exception {
        runTest(new SurfaceViewFactory(), getLargerWidthVideoFormat(new VideoFormat(fileName)));
    }

    private void runTest(VideoViewFactory videoViewFactory, VideoFormat vf) {
        if (!MediaUtils.canDecodeVideo(vf.getMimeType(), vf.getWidth(), vf.getHeight(), 30)) {
            MediaUtils.skipTest(TAG, "No supported codec is found.");
            return;
        }
        this.videoViewFactory = checkNotNull(videoViewFactory);
        this.videoView = videoViewFactory.createView(getHelper().getContext());
        final int maxRetries = 3;
        for (int retry = 1; retry <= maxRetries; retry++) {
            // If view is intended and available to display.
            if (videoView != null) {
                getHelper().generateView(videoView);
            }
            try {
                videoViewFactory.waitForViewIsAvailable();
                break;
            } catch (Exception exception) {
                Log.e(TAG, exception.getMessage());
                if (retry == maxRetries) {
                    fail("Timeout waiting for a valid surface.");
                } else {
                    Log.w(TAG, "Try again...");
                    bringActivityToFront();
                }
            }
        }
        final int golden = getGoldenId(vf.getDescription(), vf.getOriginalSize());
        assertTrue("No golden found.", golden != 0);
        final VideoViewSnapshot videoViewSnapshot = videoViewFactory.getVideoViewSnapshot();
        decodeVideo(vf, videoViewFactory);
        validateResult(vf, videoViewSnapshot, golden);
    }

    private void decodeVideo(VideoFormat videoFormat, VideoViewFactory videoViewFactory) {
        final SimplePlayer player = new SimplePlayer(getHelper().getContext());
        final SimplePlayer.PlayerResult playerResult = player.decodeVideoFrames(
                videoViewFactory.getSurface(), videoFormat, 10);
        assertTrue(playerResult.getFailureMessage(), playerResult.isSuccess());
    }

    private void validateResult(
            VideoFormat videoFormat, VideoViewSnapshot videoViewSnapshot, int goldenId) {
        final Bitmap result = checkNotNull("The expected bitmap from snapshot is null",
                getHelper().generateBitmapFromVideoViewSnapshot(videoViewSnapshot));
        final Bitmap golden = getHelper().generateBitmapFromImageResourceId(goldenId);
        final BitmapCompare.Difference difference = BitmapCompare.computeMinimumDifference(
                result, golden, videoFormat.getOriginalWidth(), videoFormat.getOriginalHeight());
        assertTrue("With the best matched border crop ("
                + difference.bestMatchBorderCrop.first + ", "
                + difference.bestMatchBorderCrop.second + "), "
                + "greatest pixel difference is "
                + difference.greatestPixelDifference
                + (difference.greatestPixelDifferenceCoordinates != null
                        ? " at (" + difference.greatestPixelDifferenceCoordinates.first + ", "
                            + difference.greatestPixelDifferenceCoordinates.second + ")" : "")
                + " which is over the allowed difference " + ALLOWED_GREATEST_PIXEL_DIFFERENCE,
                difference.greatestPixelDifference <= ALLOWED_GREATEST_PIXEL_DIFFERENCE);
    }

    private static VideoFormat getLargerHeightVideoFormat(VideoFormat videoFormat) {
        return new VideoFormat(videoFormat) {
            @Override
            public int getHeight() {
                return super.getHeight() + OFFSET;
            }

            @Override
            public boolean isAbrEnabled() {
                return true;
            }
        };
    }

    private static VideoFormat getLargerWidthVideoFormat(VideoFormat videoFormat) {
        return new VideoFormat(videoFormat) {
            @Override
            public int getWidth() {
                return super.getWidth() + OFFSET;
            }

            @Override
            public boolean isAbrEnabled() {
                return true;
            }
        };
    }

    /**
     * Returns the resource id by matching parts of the video and golden file name.
     */
    private static int getGoldenId(String description, String size) {
        for (Field field : fields) {
            try {
                final String name = field.getName();
                if (name.contains("golden") && name.contains(description) && name.contains(size)) {
                    int id = field.getInt(null);
                    return field.getInt(null);
                }
            } catch (IllegalAccessException | NullPointerException e) {
                // No file found.
            }
        }
        return 0;
    }

}
