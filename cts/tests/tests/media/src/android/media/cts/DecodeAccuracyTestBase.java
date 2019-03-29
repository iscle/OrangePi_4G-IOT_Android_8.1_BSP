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

import android.media.cts.R;

import static org.junit.Assert.assertNotNull;

import com.android.compatibility.common.util.ApiLevelUtil;
import com.android.compatibility.common.util.MediaUtils;

import android.annotation.TargetApi;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.AssetFileDescriptor;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaCodec.CodecException;
import android.media.MediaCodecInfo.VideoCapabilities;
import android.media.MediaCodecList;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.opengl.EGL14;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.support.test.rule.ActivityTestRule;
import android.util.Log;
import android.util.Pair;
import android.util.SparseArray;
import android.view.PixelCopy;
import android.view.PixelCopy.OnPixelCopyFinishedListener;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.RelativeLayout;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.concurrent.TimeUnit;
import java.util.HashMap;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;

@TargetApi(16)
public class DecodeAccuracyTestBase {

    protected Context mContext;
    protected Resources mResources;
    protected DecodeAccuracyTestActivity mActivity;
    protected TestHelper testHelper;

    @Rule
    public ActivityTestRule<DecodeAccuracyTestActivity> mActivityRule =
            new ActivityTestRule<>(DecodeAccuracyTestActivity.class);

    @Before
    public void setUp() throws Exception {
        mActivity = mActivityRule.getActivity();
        mContext = mActivity.getApplicationContext();
        mResources = mActivity.getResources();
        testHelper = new TestHelper(mContext, mActivity);
    }

    @After
    public void tearDown() throws Exception {
        mActivity = null;
        mResources = null;
        mContext = null;
        mActivityRule = null;
    }

    protected void bringActivityToFront() {
        Intent intent = new Intent(mContext, DecodeAccuracyTestActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
        mActivity.startActivity(intent);
    }

    protected TestHelper getHelper() {
        return testHelper;
    }

    public static <T> T checkNotNull(T reference) {
        assertNotNull(reference);
        return reference;
    }

    public static <T> T checkNotNull(String msg, T reference) {
        assertNotNull(msg, reference);
        return reference;
    }

    /* Simple Player that decodes a local video file only. */
    @TargetApi(16)
    static class SimplePlayer {

        public static final long MIN_MS_PER_FRAME = TimeUnit.SECONDS.toMillis(1) / 5; // 5 FPS
        public static final long STARTUP_ALLOW_MS = TimeUnit.SECONDS.toMillis(1) ;
        public static final int END_OF_STREAM = -1;
        public static final int DEQUEUE_SUCCESS = 1;
        public static final int DEQUEUE_FAIL = 0;

        private static final String TAG = SimplePlayer.class.getSimpleName();
        private static final int NO_TRACK_INDEX = -3;
        private static final long DEQUEUE_TIMEOUT_US = 20;

        private final Context context;
        private final MediaExtractor extractor;
        private final String codecName;
        private MediaCodec decoder;
        private byte[] outputBytes;
        private boolean renderToSurface;
        private MediaCodecList mediaCodecList;
        private Surface surface;

        public SimplePlayer(Context context) {
            this(context, null);
        }

        public SimplePlayer(Context context, String codecName) {
            this.context = checkNotNull(context);
            this.codecName = codecName;
            this.extractor = new MediaExtractor();
            this.renderToSurface = false;
            this.surface = null;
        }

        /**
         * The function play the corresponding file for certain number of frames.
         *
         * @param surface is the surface view of decoder output.
         * @param videoFormat is the format of the video to extract and decode.
         * @param numOfTotalFrames is the number of Frame wish to play.
         * @param msPerFrameCap is the maximum msec per frame. No cap is set if value is less than 1.
         * @return {@link PlayerResult} that consists the result.
         */
        public PlayerResult decodeVideoFrames(
                Surface surface, VideoFormat videoFormat, int numOfTotalFrames, long msPerFrameCap) {
            this.surface = surface;
            PlayerResult playerResult;
            if (prepareVideoDecode(videoFormat)) {
                if (startDecoder()) {
                    final long timeout =
                            Math.max(MIN_MS_PER_FRAME, msPerFrameCap) * numOfTotalFrames + STARTUP_ALLOW_MS;
                    playerResult = decodeFramesAndPlay(numOfTotalFrames, timeout, msPerFrameCap);
                } else {
                    playerResult = PlayerResult.failToStart();
                }
            } else {
                playerResult = new PlayerResult();
            }
            release();
            return new PlayerResult(playerResult);
        }

        public PlayerResult decodeVideoFrames(
                Surface surface, VideoFormat videoFormat, int numOfTotalFrames) {
            return decodeVideoFrames(surface, videoFormat, numOfTotalFrames, 0);
        }

        public PlayerResult decodeVideoFrames(VideoFormat videoFormat, int numOfTotalFrames) {
            return decodeVideoFrames(null, videoFormat, numOfTotalFrames, 0);
        }

        /**
         * The function sets up the extractor and video decoder with proper format.
         * This must be called before doing starting up the decoder.
         */
        private boolean prepareVideoDecode(VideoFormat videoFormat) {
            MediaFormat mediaFormat = prepareExtractor(videoFormat);
            if (mediaFormat == null) {
                return false;
            }
            configureVideoFormat(mediaFormat, videoFormat);
            setRenderToSurface(surface != null);
            return createDecoder(mediaFormat) && configureDecoder(surface, mediaFormat);
        }

        /**
         * Sets up the extractor and gets the {@link MediaFormat} of the track.
         */
        private MediaFormat prepareExtractor(VideoFormat videoFormat) {
            if (!setExtractorDataSource(videoFormat)) {
                return null;
            }
            final int trackNum = getFirstTrackIndexByType(videoFormat.getMediaFormat());
            if (trackNum == NO_TRACK_INDEX) {
                return null;
            }
            extractor.selectTrack(trackNum);
            return extractor.getTrackFormat(trackNum);
        }

        /**
         * The function decode video frames and display in a surface.
         *
         * @param numOfTotalFrames is the number of frames to be decoded.
         * @param timeOutMs is the time limit for decoding the frames.
         * @param msPerFrameCap is the maximum msec per frame. No cap is set if value is less than 1.
         * @return {@link PlayerResult} that consists the result.
         */
        private PlayerResult decodeFramesAndPlay(
                int numOfTotalFrames, long timeOutMs, long msPerFrameCap) {
            int numOfDecodedFrames = 0;
            long firstOutputTimeMs = 0;
            long lastFrameAt = 0;
            final long loopStart = SystemClock.elapsedRealtime();

            while (numOfDecodedFrames < numOfTotalFrames
                    && (SystemClock.elapsedRealtime() - loopStart < timeOutMs)) {
                try {
                    queueDecoderInputBuffer();
                } catch (IllegalStateException exception) {
                    Log.e(TAG, "IllegalStateException in queueDecoderInputBuffer", exception);
                    break;
                }
                try {
                    final int outputResult = dequeueDecoderOutputBuffer();
                    if (outputResult == SimplePlayer.END_OF_STREAM) {
                        break;
                    }
                    if (outputResult == SimplePlayer.DEQUEUE_SUCCESS) {
                        if (firstOutputTimeMs == 0) {
                            firstOutputTimeMs = SystemClock.elapsedRealtime();
                        }
                        if (msPerFrameCap > 0) {
                            // Slow down if cap is set and not reached.
                            final long delayMs =
                                    msPerFrameCap - (SystemClock.elapsedRealtime() - lastFrameAt);
                            if (lastFrameAt != 0 && delayMs > 0) {
                                final long threadDelayMs = 3; // In case of delay in thread.
                                if (delayMs > threadDelayMs) {
                                    try {
                                        Thread.sleep(delayMs - threadDelayMs);
                                    } catch (InterruptedException ex) { /* */}
                                }
                                while (SystemClock.elapsedRealtime() - lastFrameAt
                                        < msPerFrameCap) { /* */ }
                            }
                            lastFrameAt = SystemClock.elapsedRealtime();
                        }
                        numOfDecodedFrames++;
                    }
                } catch (IllegalStateException exception) {
                    Log.e(TAG, "IllegalStateException in dequeueDecoderOutputBuffer", exception);
                }
            }
            // NB: totalTime measures from "first output" instead of
            // "first INPUT", so does not include first frame latency
            // and therefore does not tell us if the timeout expired
            final long totalTime = SystemClock.elapsedRealtime() - firstOutputTimeMs;
            return new PlayerResult(true, true, numOfTotalFrames == numOfDecodedFrames, totalTime);
        }

        /**
         * Queues the input buffer with the media file one buffer at a time.
         *
         * @return true if success, fail otherwise.
         */
        private boolean queueDecoderInputBuffer() {
            ByteBuffer inputBuffer;
            final ByteBuffer[] inputBufferArray = decoder.getInputBuffers();
            final int inputBufferIndex = decoder.dequeueInputBuffer(DEQUEUE_TIMEOUT_US);
            if (inputBufferIndex >= 0) {
                if (ApiLevelUtil.isBefore(Build.VERSION_CODES.LOLLIPOP)) {
                    inputBuffer = inputBufferArray[inputBufferIndex];
                } else {
                    inputBuffer = decoder.getInputBuffer(inputBufferIndex);
                }
                final int sampleSize = extractor.readSampleData(inputBuffer, 0);
                if (sampleSize > 0) {
                    decoder.queueInputBuffer(
                            inputBufferIndex, 0, sampleSize, extractor.getSampleTime(), 0);
                    extractor.advance();
                }
                return true;
            }
            return false;
        }

        /**
         * Dequeues the output buffer.
         * For video decoder, renders to surface if provided.
         * For audio decoder, gets the bytes from the output buffer.
         *
         * @return an integer indicating its status (fail, success, or end of stream).
         */
        private int dequeueDecoderOutputBuffer() {
            final BufferInfo info = new BufferInfo();
            final int decoderStatus = decoder.dequeueOutputBuffer(info, DEQUEUE_TIMEOUT_US);
            if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                return END_OF_STREAM;
            }
            if (decoderStatus >= 0) {
                // For JELLY_BEAN_MR2- devices, when rendering to a surface,
                // info.size seems to always return 0 even if
                // the decoder successfully decoded the frame.
                if (info.size <= 0 && ApiLevelUtil.isAtLeast(Build.VERSION_CODES.JELLY_BEAN_MR2)) {
                    return DEQUEUE_FAIL;
                }
                if (!renderToSurface) {
                    ByteBuffer outputBuffer;
                    if (ApiLevelUtil.isBefore(Build.VERSION_CODES.LOLLIPOP)) {
                        outputBuffer = decoder.getOutputBuffers()[decoderStatus];
                    } else {
                        outputBuffer = decoder.getOutputBuffer(decoderStatus);
                    }
                    outputBytes = new byte[info.size];
                    outputBuffer.get(outputBytes);
                    outputBuffer.clear();
                }
                decoder.releaseOutputBuffer(decoderStatus, renderToSurface);
                return DEQUEUE_SUCCESS;
            }
            return DEQUEUE_FAIL;
        }

        private void release() {
            decoderRelease();
            extractorRelease();
        }

        private boolean setExtractorDataSource(VideoFormat videoFormat) {
            checkNotNull(videoFormat);
            try {
                final AssetFileDescriptor afd = videoFormat.getAssetFileDescriptor(context);
                extractor.setDataSource(
                        afd.getFileDescriptor(), afd.getStartOffset(), afd.getLength());
                afd.close();
            } catch (IOException exception) {
                Log.e(TAG, "IOException in setDataSource", exception);
                return false;
            }
            return true;
        }

        /**
         * Creates a decoder based on conditions.
         *
         * <p>If codec name is provided, {@link MediaCodec#createByCodecName(String)} is used.
         * If codec name is not provided, {@link MediaCodecList#findDecoderForFormat(MediaFormat)}
         * is preferred on LOLLIPOP and up for finding out the codec name that
         * supports the media format.
         * For OS older than LOLLIPOP, {@link MediaCodec#createDecoderByType(String)} is used.
         */
        private boolean createDecoder(MediaFormat mediaFormat) {
            try {
                if (codecName != null) {
                    decoder = MediaCodec.createByCodecName(codecName);
                } else if (ApiLevelUtil.isAtLeast(Build.VERSION_CODES.LOLLIPOP)) {
                    if (Build.VERSION.SDK_INT == Build.VERSION_CODES.LOLLIPOP) {
                        // On LOLLIPOP, format must not contain a frame rate.
                        mediaFormat.setString(MediaFormat.KEY_FRAME_RATE, null);
                    }
                    if (mediaCodecList == null) {
                        mediaCodecList = new MediaCodecList(MediaCodecList.ALL_CODECS);
                    }
                    decoder = MediaCodec.createByCodecName(
                            mediaCodecList.findDecoderForFormat(mediaFormat));
                } else {
                    decoder = MediaCodec.createDecoderByType(
                            mediaFormat.getString(MediaFormat.KEY_MIME));
                }
            } catch (Exception exception) {
                Log.e(TAG, "Exception during decoder creation", exception);
                decoderRelease();
                return false;
            }
            return true;
        }

        private boolean configureDecoder(Surface surface, MediaFormat mediaFormat) {
            try {
                decoder.configure(mediaFormat, surface, null, 0);
            } catch (Exception exception) {
                Log.e(TAG, "Exception during decoder configuration", exception);
                try {
                    decoder.reset();
                } catch (Exception resetException) {
                    Log.e(TAG, "Exception during decoder reset", resetException);
                }
                decoderRelease();
                return false;
            }
            return true;
        }

        private void setRenderToSurface(boolean render) {
            this.renderToSurface = render;
        }

        private boolean startDecoder() {
            try {
                decoder.start();
            } catch (Exception exception) {
                Log.e(TAG, "Exception during decoder start", exception);
                decoder.reset();
                decoderRelease();
                return false;
            }
            return true;
        }

        private void decoderRelease() {
            if (decoder == null) {
                return;
            }
            try {
                decoder.stop();
            } catch (IllegalStateException exception) {
                decoder.reset();
                // IllegalStateException happens when decoder fail to start.
                Log.e(TAG, "IllegalStateException during decoder stop", exception);
            } finally {
                try {
                    decoder.release();
                } catch (IllegalStateException exception) {
                    Log.e(TAG, "IllegalStateException during decoder release", exception);
                }
                decoder = null;
            }
        }

        private void extractorRelease() {
            if (extractor == null) {
                return;
            }
            try {
                extractor.release();
            } catch (IllegalStateException exception) {
                Log.e(TAG, "IllegalStateException during extractor release", exception);
            }
        }

        private static void configureVideoFormat(MediaFormat mediaFormat, VideoFormat videoFormat) {
            checkNotNull(mediaFormat);
            checkNotNull(videoFormat);
            videoFormat.setMimeType(mediaFormat.getString(MediaFormat.KEY_MIME));
            videoFormat.setWidth(mediaFormat.getInteger(MediaFormat.KEY_WIDTH));
            videoFormat.setHeight(mediaFormat.getInteger(MediaFormat.KEY_HEIGHT));
            mediaFormat.setInteger(MediaFormat.KEY_WIDTH, videoFormat.getWidth());
            mediaFormat.setInteger(MediaFormat.KEY_HEIGHT, videoFormat.getHeight());
            if (ApiLevelUtil.isBefore(Build.VERSION_CODES.KITKAT)) {
                return;
            }
            // Set KEY_MAX_WIDTH and KEY_MAX_HEIGHT when isAbrEnabled() is set.
            if (videoFormat.isAbrEnabled()) {
                try {
                    // Check for max resolution supported by the codec.
                    final MediaCodec decoder = MediaUtils.getDecoder(mediaFormat);
                    final VideoCapabilities videoCapabilities = MediaUtils.getVideoCapabilities(
                            decoder.getName(), videoFormat.getMimeType());
                    decoder.release();
                    final int maxWidth = videoCapabilities.getSupportedWidths().getUpper();
                    final int maxHeight =
                            videoCapabilities.getSupportedHeightsFor(maxWidth).getUpper();
                    if (maxWidth >= videoFormat.getWidth() && maxHeight >= videoFormat.getHeight()) {
                        mediaFormat.setInteger(MediaFormat.KEY_MAX_WIDTH, maxWidth);
                        mediaFormat.setInteger(MediaFormat.KEY_MAX_HEIGHT, maxHeight);
                        return;
                    }
                } catch (NullPointerException exception) { /* */ }
                // Set max width/height to current size if can't get codec's max supported
                // width/height or max is not greater than the current size.
                mediaFormat.setInteger(MediaFormat.KEY_MAX_WIDTH, videoFormat.getWidth());
                mediaFormat.setInteger(MediaFormat.KEY_MAX_HEIGHT, videoFormat.getHeight());
            }
        }

        /**
         * The function returns the first track found based on the media type.
         */
        private int getFirstTrackIndexByType(String format) {
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat trackMediaFormat = extractor.getTrackFormat(i);
                if (trackMediaFormat.getString(MediaFormat.KEY_MIME).startsWith(format + "/")) {
                    return i;
                }
            }
            Log.e(TAG, "couldn't get a " + format + " track");
            return NO_TRACK_INDEX;
        }

        /**
         * Stores the result from SimplePlayer.
         */
        public static final class PlayerResult {

            public static final int UNSET = -1;
            private final boolean configureSuccess;
            private final boolean startSuccess;
            private final boolean decodeSuccess;
            private final long totalTime;

            public PlayerResult(
                    boolean configureSuccess, boolean startSuccess,
                    boolean decodeSuccess, long totalTime) {
                this.configureSuccess = configureSuccess;
                this.startSuccess = startSuccess;
                this.decodeSuccess = decodeSuccess;
                this.totalTime = totalTime;
            }

            public PlayerResult(PlayerResult playerResult) {
                this(playerResult.configureSuccess, playerResult.startSuccess,
                        playerResult.decodeSuccess, playerResult.totalTime);
            }

            public PlayerResult() {
                // Dummy PlayerResult.
                this(false, false, false, UNSET);
            }

            public static PlayerResult failToStart() {
                return new PlayerResult(true, false, false, UNSET);
            }

            public String getFailureMessage() {
                if (!configureSuccess) {
                    return "Failed to configure decoder.";
                } else if (!startSuccess) {
                    return "Failed to start decoder.";
                } else if (!decodeSuccess) {
                    return "Failed to decode the expected number of frames.";
                } else {
                    return "Failed to finish decoding.";
                }
            }

            public boolean isConfigureSuccess() {
                return configureSuccess;
            }

            public boolean isSuccess() {
                return configureSuccess && startSuccess && decodeSuccess && getTotalTime() != UNSET;
            }

            public long getTotalTime() {
                return totalTime;
            }

        }

    }

    /* Utility class for collecting common test case functionality. */
    class TestHelper {

        private final String TAG =  TestHelper.class.getSimpleName();

        private final Context context;
        private final Handler handler;
        private final Activity activity;

        public TestHelper(Context context, Activity activity) {
            this.context = checkNotNull(context);
            this.handler = new Handler(Looper.getMainLooper());
            this.activity = activity;
        }

        public Bitmap generateBitmapFromImageResourceId(int resourceId) {
            return BitmapFactory.decodeStream(context.getResources().openRawResource(resourceId));
        }

        public Context getContext() {
            return context;
        }

        public void rotateOrientation() {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    final int orientation = context.getResources().getConfiguration().orientation;
                    if (orientation == Configuration.ORIENTATION_PORTRAIT) {
                        activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                    } else {
                        activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                    }
                }
            });
        }

        public void unsetOrientation() {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    activity.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
                }
            });
        }

        public void generateView(View view) {
            RelativeLayout relativeLayout =
                    (RelativeLayout) activity.findViewById(R.id.attach_view);
            ViewGenerator viewGenerator = new ViewGenerator(relativeLayout, view);
            handler.post(viewGenerator);
        }

        public void cleanUpView(View view) {
            ViewCleaner viewCleaner = new ViewCleaner(view);
            handler.post(viewCleaner);
        }

        public synchronized Bitmap generateBitmapFromVideoViewSnapshot(VideoViewSnapshot snapshot) {
            final long timeOutMs = TimeUnit.SECONDS.toMillis(30);
            final long start = SystemClock.elapsedRealtime();
            handler.post(snapshot);
            try {
                while (!snapshot.isBitmapReady()
                        && (SystemClock.elapsedRealtime() - start < timeOutMs)) {
                    Thread.sleep(100);
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
                return null;
            }
            if (!snapshot.isBitmapReady()) {
                Log.e(TAG, "Time out in generateBitmapFromVideoViewSnapshot().");
                return null;
            }
            return snapshot.getBitmap();
        }

        private class ViewGenerator implements Runnable {

            private final View view;
            private final RelativeLayout relativeLayout;

            public ViewGenerator(RelativeLayout relativeLayout, View view) {
                this.view = checkNotNull(view);
                this.relativeLayout = checkNotNull(relativeLayout);
            }

            @Override
            public void run() {
                if (view.getParent() != null) {
                    ((ViewGroup) view.getParent()).removeView(view);
                }
                RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
                        VideoViewFactory.VIEW_WIDTH, VideoViewFactory.VIEW_HEIGHT);
                view.setLayoutParams(params);
                relativeLayout.addView(view);
            }

        }

        private class ViewCleaner implements Runnable {

            private final View view;

            public ViewCleaner(View view) {
                this.view = checkNotNull(view);
            }

            @Override
            public void run() {
                if (view.getParent() != null) {
                    ((ViewGroup) view.getParent()).removeView(view);
                }
            }

        }

    }

}

/* Factory for manipulating a {@link View}. */
abstract class VideoViewFactory {

    public static final long VIEW_WAITTIME_MS = TimeUnit.SECONDS.toMillis(1);
    public static final long DEFAULT_VIEW_AVAILABLE_TIMEOUT_MS = TimeUnit.SECONDS.toMillis(3);
    public static final int VIEW_WIDTH = 480;
    public static final int VIEW_HEIGHT = 360;

    public VideoViewFactory() {}

    public abstract void release();

    public abstract String getName();

    public abstract View createView(Context context);

    public void waitForViewIsAvailable() throws Exception {
        waitForViewIsAvailable(DEFAULT_VIEW_AVAILABLE_TIMEOUT_MS);
    };

    public abstract void waitForViewIsAvailable(long timeOutMs) throws Exception;

    public abstract Surface getSurface();

    public abstract VideoViewSnapshot getVideoViewSnapshot();

    public boolean hasLooper() {
        return Looper.myLooper() != null;
    }

}

/* Factory for building a {@link TextureView}. */
@TargetApi(16)
class TextureViewFactory extends VideoViewFactory implements TextureView.SurfaceTextureListener {

    private static final String TAG = TextureViewFactory.class.getSimpleName();
    private static final String NAME = "TextureView";

    private final Object syncToken = new Object();
    private TextureView textureView;

    public TextureViewFactory() {}

    @Override
    public TextureView createView(Context context) {
        Log.i(TAG, "Creating a " + NAME);
        textureView = DecodeAccuracyTestBase.checkNotNull(new TextureView(context));
        textureView.setSurfaceTextureListener(this);
        return textureView;
    }

    @Override
    public void release() {
        textureView = null;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public Surface getSurface() {
        return new Surface(textureView.getSurfaceTexture());
    }

    @Override
    public TextureViewSnapshot getVideoViewSnapshot() {
        return new TextureViewSnapshot(textureView);
    }

    @Override
    public void waitForViewIsAvailable(long timeOutMs) throws Exception {
        final long start = SystemClock.elapsedRealtime();
        while (SystemClock.elapsedRealtime() - start < timeOutMs && !textureView.isAvailable()) {
            synchronized (syncToken) {
                try {
                    syncToken.wait(VIEW_WAITTIME_MS);
                } catch (InterruptedException e) {
                    Log.e(TAG, "Exception occurred when attaching a TextureView to a window.", e);
                    throw new InterruptedException(e.getMessage());
                }
            }
        }
        if (!textureView.isAvailable()) {
            throw new InterruptedException("Taking too long to attach a TextureView to a window.");
        }
        Log.i(TAG, NAME + " is available.");
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
        synchronized (syncToken) {
            syncToken.notify();
        }
    }

    @Override
    public void onSurfaceTextureSizeChanged(
            SurfaceTexture surfaceTexture, int width, int height) {}

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {}

}

/**
 * Factory for building a {@link SurfaceView}
 */
@TargetApi(24)
class SurfaceViewFactory extends VideoViewFactory implements SurfaceHolder.Callback {

    private static final String TAG = SurfaceViewFactory.class.getSimpleName();
    private static final String NAME = "SurfaceView";

    private final Object syncToken = new Object();
    private SurfaceViewSnapshot surfaceViewSnapshot;
    private SurfaceView surfaceView;
    private SurfaceHolder surfaceHolder;

    public SurfaceViewFactory() {}

    @Override
    public void release() {
        if (surfaceViewSnapshot != null) {
            surfaceViewSnapshot.release();
        }
        surfaceView = null;
        surfaceHolder = null;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public View createView(Context context) {
        Log.i(TAG, "Creating a " + NAME);
        if (!super.hasLooper()) {
            Looper.prepare();
        }
        surfaceView = new SurfaceView(context);
        surfaceHolder = surfaceView.getHolder();
        surfaceHolder.addCallback(this);
        return surfaceView;
    }

    @Override
    public void waitForViewIsAvailable(long timeOutMs) throws Exception {
        final long start = SystemClock.elapsedRealtime();
        while (SystemClock.elapsedRealtime() - start < timeOutMs && !getSurface().isValid()) {
            synchronized (syncToken) {
                try {
                    syncToken.wait(VIEW_WAITTIME_MS);
                } catch (InterruptedException e) {
                    Log.e(TAG, "Exception occurred when attaching a SurfaceView to a window.", e);
                    throw new InterruptedException(e.getMessage());
                }
            }
        }
        if (!getSurface().isValid()) {
            throw new InterruptedException("Taking too long to attach a SurfaceView to a window.");
        }
        Log.i(TAG, NAME + " is available.");
    }

    @Override
    public Surface getSurface() {
        return surfaceHolder == null ? null : surfaceHolder.getSurface();
    }

    @Override
    public VideoViewSnapshot getVideoViewSnapshot() {
        surfaceViewSnapshot = new SurfaceViewSnapshot(surfaceView, VIEW_WIDTH, VIEW_HEIGHT);
        return surfaceViewSnapshot;
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {}

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        synchronized (syncToken) {
            syncToken.notify();
        }
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {}

}

/**
 * Factory for building EGL and GLES that could render to GLSurfaceView.
 * {@link GLSurfaceView} {@link EGL10} {@link GLES20}.
 */
@TargetApi(16)
class GLSurfaceViewFactory extends VideoViewFactory {

    private static final String TAG = GLSurfaceViewFactory.class.getSimpleName();
    private static final String NAME = "GLSurfaceView";

    private final Object surfaceSyncToken = new Object();

    private GLSurfaceViewThread glSurfaceViewThread;
    private boolean byteBufferIsReady = false;

    public GLSurfaceViewFactory() {}

    @Override
    public void release() {
        glSurfaceViewThread.release();
        glSurfaceViewThread = null;
    }

    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public View createView(Context context) {
        Log.i(TAG, "Creating a " + NAME);
        // Do all GL rendering in the GL thread.
        glSurfaceViewThread = new GLSurfaceViewThread();
        glSurfaceViewThread.start();
        // No necessary view to display, return null.
        return null;
    }

    @Override
    public void waitForViewIsAvailable(long timeOutMs) throws Exception {
        final long start = SystemClock.elapsedRealtime();
        while (SystemClock.elapsedRealtime() - start < timeOutMs
                && glSurfaceViewThread.getSurface() == null) {
            synchronized (surfaceSyncToken) {
                try {
                    surfaceSyncToken.wait(VIEW_WAITTIME_MS);
                } catch (InterruptedException e) {
                    Log.e(TAG, "Exception occurred when waiting for the surface from"
                            + " GLSurfaceView to become available.", e);
                    throw new InterruptedException(e.getMessage());
                }
            }
        }
        if (glSurfaceViewThread.getSurface() == null) {
            throw new InterruptedException("Taking too long for the surface from"
                    + " GLSurfaceView to become available.");
        }
        Log.i(TAG, NAME + " is available.");
    }

    @Override
    public Surface getSurface() {
        return glSurfaceViewThread.getSurface();
    }

    @Override
    public VideoViewSnapshot getVideoViewSnapshot() {
        return new GLSurfaceViewSnapshot(this, VIEW_WIDTH, VIEW_HEIGHT);
    }

    public boolean byteBufferIsReady() {
        return byteBufferIsReady;
    }

    public ByteBuffer getByteBuffer() {
        return glSurfaceViewThread.getByteBuffer();
    }

    /* Does all GL operations. */
    private class GLSurfaceViewThread extends Thread
            implements SurfaceTexture.OnFrameAvailableListener {

        private static final int FLOAT_SIZE_BYTES = 4;
        private static final int TRIANGLE_VERTICES_DATA_STRIDE_BYTES = 5 * FLOAT_SIZE_BYTES;
        private static final int TRIANGLE_VERTICES_DATA_POS_OFFSET = 0;
        private static final int TRIANGLE_VERTICES_DATA_UV_OFFSET = 3;
        private FloatBuffer triangleVertices;
        private float[] textureTransform = new float[16];

        private float[] triangleVerticesData = {
            // X, Y, Z, U, V
            -1f, -1f,  0f,  0f,  1f,
             1f, -1f,  0f,  1f,  1f,
            -1f,  1f,  0f,  0f,  0f,
             1f,  1f,  0f,  1f,  0f,
        };
        // Make the top-left corner corresponds to texture coordinate
        // (0, 0). This complies with the transformation matrix obtained from
        // SurfaceTexture.getTransformMatrix.

        private static final String VERTEX_SHADER =
                "attribute vec4 aPosition;\n"
                + "attribute vec4 aTextureCoord;\n"
                + "uniform mat4 uTextureTransform;\n"
                + "varying vec2 vTextureCoord;\n"
                + "void main() {\n"
                + "    gl_Position = aPosition;\n"
                + "    vTextureCoord = (uTextureTransform * aTextureCoord).xy;\n"
                + "}\n";

        private static final String FRAGMENT_SHADER =
                "#extension GL_OES_EGL_image_external : require\n"
                + "precision mediump float;\n"      // highp here doesn't seem to matter
                + "varying vec2 vTextureCoord;\n"
                + "uniform samplerExternalOES sTexture;\n"
                + "void main() {\n"
                + "    gl_FragColor = texture2D(sTexture, vTextureCoord);\n"
                + "}\n";

        private int glProgram;
        private int textureID = -1;
        private int aPositionHandle;
        private int aTextureHandle;
        private int uTextureTransformHandle;
        private EGLDisplay eglDisplay = null;
        private EGLContext eglContext = null;
        private EGLSurface eglSurface = null;
        private EGL10 egl10;
        private Surface surface = null;
        private SurfaceTexture surfaceTexture;
        private ByteBuffer byteBuffer;
        private Looper looper;

        public GLSurfaceViewThread() {}

        @Override
        public void run() {
            Looper.prepare();
            looper = Looper.myLooper();
            triangleVertices = ByteBuffer
                    .allocateDirect(triangleVerticesData.length * FLOAT_SIZE_BYTES)
                    .order(ByteOrder.nativeOrder()).asFloatBuffer();
            triangleVertices.put(triangleVerticesData).position(0);

            eglSetup();
            makeCurrent();
            eglSurfaceCreated();

            surfaceTexture = new SurfaceTexture(getTextureId());
            surfaceTexture.setOnFrameAvailableListener(this);
            surface = new Surface(surfaceTexture);
            synchronized (surfaceSyncToken) {
                surfaceSyncToken.notify();
            }
            // Store pixels from surface
            byteBuffer = ByteBuffer.allocateDirect(VIEW_WIDTH * VIEW_HEIGHT * 4);
            byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
            Looper.loop();
        }

        @Override
        public void onFrameAvailable(SurfaceTexture st) {
            checkGlError("before updateTexImage");
            surfaceTexture.updateTexImage();
            st.getTransformMatrix(textureTransform);
            drawFrame();
            saveFrame();
        }

        /* Prepares EGL to use GLES 2.0 context and a surface that supports pbuffer. */
        public void eglSetup() {
            egl10 = (EGL10) EGLContext.getEGL();
            eglDisplay = egl10.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);
            if (eglDisplay == EGL10.EGL_NO_DISPLAY) {
                throw new RuntimeException("unable to get egl10 display");
            }
            int[] version = new int[2];
            if (!egl10.eglInitialize(eglDisplay, version)) {
                eglDisplay = null;
                throw new RuntimeException("unable to initialize egl10");
            }
            // Configure EGL for pbuffer and OpenGL ES 2.0, 24-bit RGB.
            int[] configAttribs = {
                EGL10.EGL_RED_SIZE, 8,
                EGL10.EGL_GREEN_SIZE, 8,
                EGL10.EGL_BLUE_SIZE, 8,
                EGL10.EGL_ALPHA_SIZE, 8,
                EGL10.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL10.EGL_SURFACE_TYPE, EGL10.EGL_PBUFFER_BIT,
                EGL10.EGL_NONE
            };
            EGLConfig[] configs = new EGLConfig[1];
            int[] numConfigs = new int[1];
            if (!egl10.eglChooseConfig(
                    eglDisplay, configAttribs, configs, configs.length, numConfigs)) {
                throw new RuntimeException("unable to find RGB888+recordable ES2 EGL config");
            }
            // Configure EGL context for OpenGL ES 2.0.
            int[] contextAttribs = {
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL10.EGL_NONE
            };
            eglContext = egl10.eglCreateContext(
                    eglDisplay, configs[0], EGL10.EGL_NO_CONTEXT, contextAttribs);
            checkEglError("eglCreateContext");
            if (eglContext == null) {
                throw new RuntimeException("null context");
            }
            // Create a pbuffer surface.
            int[] surfaceAttribs = {
                EGL10.EGL_WIDTH, VIEW_WIDTH,
                EGL10.EGL_HEIGHT, VIEW_HEIGHT,
                EGL10.EGL_NONE
            };
            eglSurface = egl10.eglCreatePbufferSurface(eglDisplay, configs[0], surfaceAttribs);
            checkEglError("eglCreatePbufferSurface");
            if (eglSurface == null) {
                throw new RuntimeException("surface was null");
            }
        }

        public void release() {
            looper.quit();
            if (eglDisplay != EGL10.EGL_NO_DISPLAY) {
                egl10.eglMakeCurrent(eglDisplay,
                        EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT);
                egl10.eglDestroySurface(eglDisplay, eglSurface);
                egl10.eglDestroyContext(eglDisplay, eglContext);
                egl10.eglTerminate(eglDisplay);
            }
            eglDisplay = EGL10.EGL_NO_DISPLAY;
            eglContext = EGL10.EGL_NO_CONTEXT;
            eglSurface = EGL10.EGL_NO_SURFACE;
            surface.release();
            surfaceTexture.release();
            byteBufferIsReady = false;
            byteBuffer =  null;
        }

        /* Makes our EGL context and surface current. */
        public void makeCurrent() {
            if (!egl10.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
                throw new RuntimeException("eglMakeCurrent failed");
            }
            checkEglError("eglMakeCurrent");
        }

        /* Call this after the EGL Surface is created and made current. */
        public void eglSurfaceCreated() {
            glProgram = createProgram(VERTEX_SHADER, FRAGMENT_SHADER);
            if (glProgram == 0) {
                throw new RuntimeException("failed creating program");
            }
            aPositionHandle = GLES20.glGetAttribLocation(glProgram, "aPosition");
            checkLocation(aPositionHandle, "aPosition");
            aTextureHandle = GLES20.glGetAttribLocation(glProgram, "aTextureCoord");
            checkLocation(aTextureHandle, "aTextureCoord");
            uTextureTransformHandle = GLES20.glGetUniformLocation(glProgram, "uTextureTransform");
            checkLocation(uTextureTransformHandle, "uTextureTransform");

            int[] textures = new int[1];
            GLES20.glGenTextures(1, textures, 0);
            checkGlError("glGenTextures");
            textureID = textures[0];
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureID);
            checkGlError("glBindTexture");

            GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER,
                    GLES20.GL_LINEAR);
            GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER,
                    GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S,
                    GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T,
                    GLES20.GL_CLAMP_TO_EDGE);
            checkGlError("glTexParameter");
        }

        public void drawFrame() {
            GLES20.glUseProgram(glProgram);
            checkGlError("glUseProgram");
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            checkGlError("glActiveTexture");
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureID);
            checkGlError("glBindTexture");

            triangleVertices.position(TRIANGLE_VERTICES_DATA_POS_OFFSET);
            GLES20.glVertexAttribPointer(aPositionHandle, 3, GLES20.GL_FLOAT, false,
                    TRIANGLE_VERTICES_DATA_STRIDE_BYTES, triangleVertices);
            checkGlError("glVertexAttribPointer aPositionHandle");
            GLES20.glEnableVertexAttribArray(aPositionHandle);
            checkGlError("glEnableVertexAttribArray aPositionHandle");

            triangleVertices.position(TRIANGLE_VERTICES_DATA_UV_OFFSET);
            GLES20.glVertexAttribPointer(aTextureHandle, 2, GLES20.GL_FLOAT, false,
                    TRIANGLE_VERTICES_DATA_STRIDE_BYTES, triangleVertices);
            checkGlError("glVertexAttribPointer aTextureHandle");
            GLES20.glEnableVertexAttribArray(aTextureHandle);
            checkGlError("glEnableVertexAttribArray aTextureHandle");

            GLES20.glUniformMatrix4fv(uTextureTransformHandle, 1, false, textureTransform, 0);
            checkGlError("glUniformMatrix uTextureTransformHandle");

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
            checkGlError("glDrawArrays");
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0);
        }

        /* Reads the pixels to a ByteBuffer. */
        public void saveFrame() {
            byteBufferIsReady = false;
            byteBuffer.clear();
            GLES20.glReadPixels(0, 0, VIEW_WIDTH, VIEW_HEIGHT, GLES20.GL_RGBA,
                    GLES20.GL_UNSIGNED_BYTE, byteBuffer);
            byteBufferIsReady = true;
        }

        public int getTextureId() {
            return textureID;
        }

        public Surface getSurface() {
            return surface;
        }

        public ByteBuffer getByteBuffer() {
            return byteBuffer;
        }

        private int loadShader(int shaderType, String source) {
            int shader = GLES20.glCreateShader(shaderType);
            checkGlError("glCreateShader type=" + shaderType);
            GLES20.glShaderSource(shader, source);
            GLES20.glCompileShader(shader);
            int[] compiled = new int[1];
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);

            if (compiled[0] == 0) {
                Log.e(TAG, "Could not compile shader " + shaderType + ":");
                Log.e(TAG, " " + GLES20.glGetShaderInfoLog(shader));
                GLES20.glDeleteShader(shader);
                shader = 0;
            }
            return shader;
        }

        private int createProgram(String vertexSource, String fragmentSource) {
            int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource);
            if (vertexShader == 0) {
                return 0;
            }
            int pixelShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource);
            if (pixelShader == 0) {
                return 0;
            }
            int program = GLES20.glCreateProgram();
            if (program == 0) {
                Log.e(TAG, "Could not create program");
            }
            GLES20.glAttachShader(program, vertexShader);
            checkGlError("glAttachShader");
            GLES20.glAttachShader(program, pixelShader);
            checkGlError("glAttachShader");
            GLES20.glLinkProgram(program);
            int[] linkStatus = new int[1];
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0);

            if (linkStatus[0] != GLES20.GL_TRUE) {
                Log.e(TAG, "Could not link program: ");
                Log.e(TAG, GLES20.glGetProgramInfoLog(program));
                GLES20.glDeleteProgram(program);
                program = 0;
            }
            return program;
        }

        private void checkEglError(String msg) {
            int error;
            if ((error = egl10.eglGetError()) != EGL10.EGL_SUCCESS) {
                throw new RuntimeException(msg + ": EGL error: 0x" + Integer.toHexString(error));
            }
        }

        public void checkGlError(String op) {
            int error;
            if ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
                Log.e(TAG, op + ": glError " + error);
                throw new RuntimeException(op + ": glError " + error);
            }
        }

        public void checkLocation(int location, String label) {
            if (location < 0) {
                throw new RuntimeException("Unable to locate '" + label + "' in program");
            }
        }
    }

}

/* Definition of a VideoViewSnapshot and a runnable to get a bitmap from a view. */
abstract class VideoViewSnapshot implements Runnable {

    public abstract Bitmap getBitmap();

    public abstract boolean isBitmapReady();

}

/* Runnable to get a bitmap from a texture view on the UI thread via a handler. */
class TextureViewSnapshot extends VideoViewSnapshot {

    private final TextureView tv;
    private Bitmap bitmap = null;

    public TextureViewSnapshot(TextureView tv) {
        this.tv = DecodeAccuracyTestBase.checkNotNull(tv);
    }

    @Override
    public synchronized void run() {
        bitmap = tv.getBitmap();
    }

    @Override
    public Bitmap getBitmap() {
        return bitmap;
    }

    @Override
    public boolean isBitmapReady() {
        return bitmap != null;
    }

}

/**
 * Method to get bitmap of a {@link SurfaceView}.
 */
class SurfaceViewSnapshot extends VideoViewSnapshot  {

    private static final String TAG = SurfaceViewSnapshot.class.getSimpleName();
    private static final int PIXELCOPY_REQUEST_SLEEP_MS = 30;
    private static final int PIXELCOPY_TIMEOUT_MS = 1000;

    private Thread copyThread;
    private SynchronousPixelCopy copyHelper;
    private Bitmap bitmap;
    private int copyResult;

    public SurfaceViewSnapshot(final SurfaceView surfaceView, final int width, final int height) {
        this.copyThread = new Thread(new Runnable() {
            @Override
            public void run() {
                copyHelper = new SynchronousPixelCopy();
                bitmap = Bitmap.createBitmap(width, height, Config.ARGB_8888);
                try {
                    // Wait for SurfaceView to be available.
                    while ((copyResult = copyHelper.request(surfaceView, bitmap))
                            != PixelCopy.SUCCESS) {
                        Thread.sleep(PIXELCOPY_REQUEST_SLEEP_MS);
                    }
                } catch (InterruptedException e) {
                    Log.e(TAG, "Pixel Copy is stopped/interrupted before it finishes.", e);
                    bitmap = null;
                }
                copyHelper.release();
            }
        });
        copyThread.start();
    }

    @Override
    public synchronized void run() {}

    @Override
    public Bitmap getBitmap() {
        return bitmap;
    }

    @Override
    public boolean isBitmapReady() {
        return copyResult == PixelCopy.SUCCESS;
    }

    public void release() {
        if (copyThread.isAlive()) {
            copyThread.interrupt();
        }
        copyThread = null;
        if (copyHelper != null) {
            copyHelper.release();
            copyHelper = null;
        }
        bitmap = null;
    }

    private static class SynchronousPixelCopy implements OnPixelCopyFinishedListener {

        private final Handler handler;
        private final HandlerThread thread;

        private int status = -1;

        public SynchronousPixelCopy() {
            this.thread = new HandlerThread("PixelCopyHelper");
            thread.start();
            this.handler = new Handler(thread.getLooper());
        }

        public void release() {
            if (thread.isAlive()) {
                thread.quit();
            }
        }

        public int request(SurfaceView source, Bitmap dest) {
            synchronized (this) {
                try {
                    PixelCopy.request(source, dest, this, handler);
                    return getResultLocked();
                } catch (Exception e) {
                    Log.e(TAG, "Exception occurred when copying a SurfaceView.", e);
                    return -1;
                }
            }
        }

        private int getResultLocked() {
            try {
                this.wait(PIXELCOPY_TIMEOUT_MS);
            } catch (InterruptedException e) { /* PixelCopy request didn't complete within 1s */ }
            return status;
        }

        @Override
        public void onPixelCopyFinished(int copyResult) {
            synchronized (this) {
                status = copyResult;
                this.notify();
            }
        }

    }

}

/**
 * Runnable to get a bitmap from a GLSurfaceView on the UI thread via a handler.
 * Note, because of how the bitmap is captured in GLSurfaceView,
 * this method does not have to be a runnable.
 */
class GLSurfaceViewSnapshot extends VideoViewSnapshot {

    private static final String TAG = GLSurfaceViewSnapshot.class.getSimpleName();
    private static final int GET_BYTEBUFFER_SLEEP_MS = 30;
    private static final int GET_BYTEBUFFER_MAX_ATTEMPTS = 30;

    private final GLSurfaceViewFactory glSurfaceViewFactory;
    private final int width;
    private final int height;

    private Bitmap bitmap = null;
    private boolean bitmapIsReady = false;

    public GLSurfaceViewSnapshot(GLSurfaceViewFactory glSurfaceViewFactory, int width, int height) {
        this.glSurfaceViewFactory = DecodeAccuracyTestBase.checkNotNull(glSurfaceViewFactory);
        this.width = width;
        this.height = height;
    }

    @Override
    public synchronized void run() {
        try {
            waitForByteBuffer();
        } catch (InterruptedException exception) {
            Log.e(TAG, exception.getMessage());
            bitmap = null;
            return;
        }
        try {
            final ByteBuffer byteBuffer = glSurfaceViewFactory.getByteBuffer();
            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            byteBuffer.rewind();
            bitmap.copyPixelsFromBuffer(byteBuffer);
            bitmapIsReady = true;
            byteBuffer.clear();
        } catch (NullPointerException exception) {
            Log.e(TAG, "glSurfaceViewFactory or byteBuffer may have been released", exception);
            bitmap = null;
        }
    }

    @Override
    public Bitmap getBitmap() {
        return bitmap;
    }

    @Override
    public boolean isBitmapReady() {
        return bitmapIsReady;
    }

    public void waitForByteBuffer() throws InterruptedException {
        // Wait for byte buffer to be ready.
        for (int i = 0; i < GET_BYTEBUFFER_MAX_ATTEMPTS; i++) {
            if (glSurfaceViewFactory.byteBufferIsReady()) {
                return;
            }
            Thread.sleep(GET_BYTEBUFFER_SLEEP_MS);
        }
        throw new InterruptedException("Taking too long to read pixels into a ByteBuffer.");
    }

}

/* Stores information of a video file. */
class VideoFormat {

    public static final String STRING_UNSET = "UNSET";
    public static final int INT_UNSET = -1;

    private final String filename;

    private String mimeType = STRING_UNSET;
    private int width = INT_UNSET;
    private int height = INT_UNSET;
    private int maxWidth = INT_UNSET;
    private int maxHeight = INT_UNSET;
    private FilenameParser filenameParser;

    public VideoFormat(String filename) {
        this.filename = filename;
    }

    public VideoFormat(VideoFormat videoFormat) {
        this(videoFormat.filename);
    }

    private FilenameParser getParsedName() {
        if (filenameParser == null) {
            filenameParser = new FilenameParser(filename);
        }
        return filenameParser;
    }

    public String getMediaFormat() {
        return "video";
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }

    public String getMimeType() {
        if (mimeType.equals(STRING_UNSET)) {
            return getParsedName().getMimeType();
        }
        return mimeType;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public void setMaxWidth(int maxWidth) {
        this.maxWidth = maxWidth;
    }

    public int getWidth() {
        if (width == INT_UNSET) {
            return getParsedName().getWidth();
        }
        return width;
    }

    public int getMaxWidth() {
        return maxWidth;
    }

    public int getOriginalWidth() {
        return getParsedName().getWidth();
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public void setMaxHeight(int maxHeight) {
        this.maxHeight = maxHeight;
    }

    public int getHeight() {
        if (height == INT_UNSET) {
            return getParsedName().getHeight();
        }
        return height;
    }

    public int getMaxHeight() {
        return maxHeight;
    }

    public int getOriginalHeight() {
        return getParsedName().getHeight();
    }

    public boolean isAbrEnabled() {
        return false;
    }

    public String getOriginalSize() {
        if (width == INT_UNSET || height == INT_UNSET) {
            return getParsedName().getSize();
        }
        return width + "x" + height;
    }

    public String getDescription() {
        return getParsedName().getDescription();
    }

    public String toPrettyString() {
        return getParsedName().toPrettyString();
    }

    public AssetFileDescriptor getAssetFileDescriptor(Context context) {
        try {
            return context.getAssets().openFd(filename);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

}

/* File parser for filenames with format of {description}-{mimeType}_{size}_{framerate}.{format} */
class FilenameParser {

    static final String VP9 = "vp9";
    static final String H264 = "h264";

    private final String filename;

    private String codec = VideoFormat.STRING_UNSET;
    private String description = VideoFormat.STRING_UNSET;
    private int width = VideoFormat.INT_UNSET;
    private int height = VideoFormat.INT_UNSET;

    FilenameParser(String filename) {
        this.filename = filename;
        parseFilename(filename);
    }

    public String getCodec() {
        return codec;
    }

    public String getMimeType() {
        switch (codec) {
            case H264:
                return MimeTypes.VIDEO_H264;
            case VP9:
                return MimeTypes.VIDEO_VP9;
            default:
                return null;
        }
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public String getSize() {
        return width + "x" + height;
    }

    public String getDescription() {
        return description;
    }

    String toPrettyString() {
        if (codec != null) {
            return codec.toUpperCase() + " " + getSize();
        }
        return filename;
    }

    private void parseFilename(String filename) {
        final String descriptionDelimiter = "-";
        final String infoDelimiter = "_";
        final String sizeDelimiter = "x";
        try {
            this.description = filename.split(descriptionDelimiter)[0];
            final String[] fileInfo = filename.split(descriptionDelimiter)[1].split(infoDelimiter);
            this.codec = fileInfo[0];
            this.width = Integer.parseInt(fileInfo[1].split(sizeDelimiter)[0]);
            this.height = Integer.parseInt(fileInfo[1].split(sizeDelimiter)[1]);
        } catch (Exception exception) { /* Filename format does not match. */ }
    }

}

/**
 * Compares bitmaps to determine if they are similar.
 *
 * <p>To determine greatest pixel difference we transform each pixel into the
 * CIE L*a*b* color space. The euclidean distance formula is used to determine pixel differences.
 */
class BitmapCompare {

    private static final int RED = 0;
    private static final int GREEN = 1;
    private static final int BLUE = 2;
    private static final int X = 0;
    private static final int Y = 1;
    private static final int Z = 2;

    private BitmapCompare() {}

    /**
     * Produces greatest pixel between two bitmaps. Used to determine bitmap similarity.
     *
     * @param bitmap1 A bitmap to compare to bitmap2.
     * @param bitmap2 A bitmap to compare to bitmap1.
     * @return A {@link Difference} with an integer describing the greatest pixel difference,
     *     using {@link Integer#MAX_VALUE} for completely different bitmaps, and an optional
     *     {@link Pair<Integer, Integer>} of the (col, row) pixel coordinate where it was first found.
     */
    @TargetApi(12)
    public static Difference computeDifference(Bitmap bitmap1, Bitmap bitmap2) {
        if (bitmap1 == null || bitmap2 == null) {
            return new Difference(Integer.MAX_VALUE);
        }
        if (bitmap1.equals(bitmap2) || bitmap1.sameAs(bitmap2)) {
            return new Difference(0);
        }
        if (bitmap1.getHeight() != bitmap2.getHeight() || bitmap1.getWidth() != bitmap2.getWidth()) {
            return new Difference(Integer.MAX_VALUE);
        }
        // Convert all pixels to CIE L*a*b* color space so we can do a direct color comparison using
        // euclidean distance formula.
        final double[][] pixels1 = convertRgbToCieLab(bitmap1);
        final double[][] pixels2 = convertRgbToCieLab(bitmap2);
        int greatestDifference = 0;
        int greatestDifferenceIndex = -1;
        for (int i = 0; i < pixels1.length; i++) {
            final int difference = euclideanDistance(pixels1[i], pixels2[i]);
            if (difference > greatestDifference) {
                greatestDifference = difference;
                greatestDifferenceIndex = i;
            }
        }
        return new Difference(greatestDifference, Pair.create(
            greatestDifferenceIndex % bitmap1.getWidth(),
            greatestDifferenceIndex / bitmap1.getHeight()));
    }

    @SuppressLint("UseSparseArrays")
    private static double[][] convertRgbToCieLab(Bitmap bitmap) {
        final HashMap<Integer, double[]> pixelTransformCache = new HashMap<>();
        final double[][] result = new double[bitmap.getHeight() * bitmap.getWidth()][3];
        final int[] pixels = new int[bitmap.getHeight() * bitmap.getWidth()];
        bitmap.getPixels(pixels, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
        for (int i = 0; i < pixels.length; i++) {
            final double[] transformedColor = pixelTransformCache.get(pixels[i]);
            if (transformedColor != null) {
                result[i] = transformedColor;
            } else {
                result[i] = convertXyzToCieLab(convertRgbToXyz(pixels[i]));
                pixelTransformCache.put(pixels[i], result[i]);
            }
        }
        return result;
    }

    /**
     * Conversion from RGB to XYZ based algorithm as defined by:
     * http://www.easyrgb.com/index.php?X=MATH&H=02#text2
     *
     * <p><pre>{@code
     *   var_R = ( R / 255 )        //R from 0 to 255
     *   var_G = ( G / 255 )        //G from 0 to 255
     *   var_B = ( B / 255 )        //B from 0 to 255
     *
     *   if ( var_R > 0.04045 ) var_R = ( ( var_R + 0.055 ) / 1.055 ) ^ 2.4
     *   else                   var_R = var_R / 12.92
     *   if ( var_G > 0.04045 ) var_G = ( ( var_G + 0.055 ) / 1.055 ) ^ 2.4
     *   else                   var_G = var_G / 12.92
     *   if ( var_B > 0.04045 ) var_B = ( ( var_B + 0.055 ) / 1.055 ) ^ 2.4
     *   else                   var_B = var_B / 12.92
     *
     *   var_R = var_R * 100
     *   var_G = var_G * 100
     *   var_B = var_B * 100
     *
     *   // Observer. = 2°, Illuminant = D65
     *   X = var_R * 0.4124 + var_G * 0.3576 + var_B * 0.1805
     *   Y = var_R * 0.2126 + var_G * 0.7152 + var_B * 0.0722
     *   Z = var_R * 0.0193 + var_G * 0.1192 + var_B * 0.9505
     * }</pre>
     *
     * @param rgbColor A packed int made up of 4 bytes: alpha, red, green, blue.
     * @return An array of doubles where each value is a component of the XYZ color space.
     */
    private static double[] convertRgbToXyz(int rgbColor) {
        final double[] comp = {Color.red(rgbColor), Color.green(rgbColor), Color.blue(rgbColor)};
        for (int i = 0; i < comp.length; i++) {
            comp[i] /= 255.0;
            if (comp[i] > 0.04045) {
                comp[i] = Math.pow((comp[i] + 0.055) / 1.055, 2.4);
            } else {
                comp[i] /= 12.92;
            }
            comp[i] *= 100;
        }
        final double x = (comp[RED] * 0.4124) + (comp[GREEN] * 0.3576) + (comp[BLUE] * 0.1805);
        final double y = (comp[RED] * 0.2126) + (comp[GREEN] * 0.7152) + (comp[BLUE] * 0.0722);
        final double z = (comp[RED] * 0.0193) + (comp[GREEN] * 0.1192) + (comp[BLUE] * 0.9505);
        return new double[] {x, y, z};
    }

    /**
     * Conversion from XYZ to CIE-L*a*b* based algorithm as defined by:
     * http://www.easyrgb.com/index.php?X=MATH&H=07#text7
     *
     * <p><pre>
     * {@code
     *   var_X = X / ref_X          //ref_X =  95.047   Observer= 2°, Illuminant= D65
     *   var_Y = Y / ref_Y          //ref_Y = 100.000
     *   var_Z = Z / ref_Z          //ref_Z = 108.883
     *
     *   if ( var_X > 0.008856 ) var_X = var_X ^ ( 1/3 )
     *   else                    var_X = ( 7.787 * var_X ) + ( 16 / 116 )
     *   if ( var_Y > 0.008856 ) var_Y = var_Y ^ ( 1/3 )
     *   else                    var_Y = ( 7.787 * var_Y ) + ( 16 / 116 )
     *   if ( var_Z > 0.008856 ) var_Z = var_Z ^ ( 1/3 )
     *   else                    var_Z = ( 7.787 * var_Z ) + ( 16 / 116 )
     *
     *   CIE-L* = ( 116 * var_Y ) - 16
     *   CIE-a* = 500 * ( var_X - var_Y )
     *   CIE-b* = 200 * ( var_Y - var_Z )
     * }
     * </pre>
     *
     * @param comp An array of doubles where each value is a component of the XYZ color space.
     * @return An array of doubles where each value is a component of the CIE-L*a*b* color space.
     */
    private static double[] convertXyzToCieLab(double[] comp) {
        comp[X] /= 95.047;
        comp[Y] /= 100.0;
        comp[Z] /= 108.883;
        for (int i = 0; i < comp.length; i++) {
            if (comp[i] > 0.008856) {
                comp[i] = Math.pow(comp[i], (1.0 / 3.0));
            } else {
                comp[i] = (7.787 * comp[i]) + (16.0 / 116.0);
            }
        }
        final double l = (116 * comp[Y]) - 16;
        final double a = 500 * (comp[X] - comp[Y]);
        final double b = 200 * (comp[Y] - comp[Z]);
        return new double[] {l, a, b};
    }

    private static int euclideanDistance(double[] p1, double[] p2) {
        if (p1.length != p2.length) {
            return Integer.MAX_VALUE;
        }
        double result = 0;
        for (int i = 0; i < p1.length; i++) {
            result += Math.pow(p1[i] - p2[i], 2);
        }
        return (int) Math.round(Math.sqrt(result));
    }

    /**
     * Crops the border of the array representing an image by hBorderSize
     * pixels on the left and right borders, and by vBorderSize pixels on the
     * top and bottom borders (so the width is 2 * hBorderSize smaller and
     * the height is 2 * vBorderSize smaller), then scales the image up to
     * match the original size using bilinear interpolation.
     */
    private static Bitmap shrinkAndScaleBilinear(
            Bitmap input, double hBorderSize, double vBorderSize) {

        int width = input.getWidth();
        int height = input.getHeight();

        // Compute the proper step sizes
        double xInc = ((double) width - 1 - hBorderSize * 2) / (double) (width - 1);
        double yInc = ((double) height - 1 - vBorderSize * 2) / (double) (height - 1);

        // Read the input bitmap into RGB arrays.
        int[] inputPixels = new int[width * height];
        input.getPixels(inputPixels, 0, width, 0, 0, width, height);
        int[][] inputRgb = new int[width * height][3];
        for (int i = 0; i < width * height; ++i) {
            inputRgb[i][0] = Color.red(inputPixels[i]);
            inputRgb[i][1] = Color.green(inputPixels[i]);
            inputRgb[i][2] = Color.blue(inputPixels[i]);
        }
        inputPixels = null;

        // Prepare the output buffer.
        int[] outputPixels = new int[width * height];

        // Start the iteration. The first y coordinate is vBorderSize.
        double y = vBorderSize;
        for (int yIndex = 0; yIndex < height; ++yIndex) {
            // The first x coordinate is hBorderSize.
            double x = hBorderSize;
            for (int xIndex = 0; xIndex < width; ++xIndex) {
                // Determine the square of interest.
                int left = (int)x;    // This is floor(x).
                int top = (int)y;     // This is floor(y).
                int right = left + 1;
                int bottom = top + 1;

                // (u, v) is the fractional part of (x, y).
                double u = x - (double)left;
                double v = y - (double)top;

                // Precompute necessary products to save time.
                double p00 = (1.0 - u) * (1.0 - v);
                double p01 = (1.0 - u) * v;
                double p10 = u * (1.0 - v);
                double p11 = u * v;

                // Clamp the indices to prevent out-of-bound that may be caused
                // by round-off error.
                if (left >= width) left = width - 1;
                if (top >= height) top = height - 1;
                if (right >= width) right = width - 1;
                if (bottom >= height) bottom = height - 1;

                // Sample RGB values from the four corners.
                int[] rgb00 = inputRgb[top * width + left];
                int[] rgb01 = inputRgb[bottom * width + left];
                int[] rgb10 = inputRgb[top * width + right];
                int[] rgb11 = inputRgb[bottom * width + right];

                // Interpolate each component of RGB separately.
                int[] mixedColor = new int[3];
                for (int k = 0; k < 3; ++k) {
                    mixedColor[k] = (int)Math.round(
                            p00 * (double) rgb00[k] + p01 * (double) rgb01[k]
                            + p10 * (double) rgb10[k] + p11 * (double) rgb11[k]);
                }
                // Convert RGB to bitmap Color format and store.
                outputPixels[yIndex * width + xIndex] = Color.rgb(
                        mixedColor[0], mixedColor[1], mixedColor[2]);
                x += xInc;
            }
            y += yInc;
        }
        // Assemble the output buffer into a Bitmap object.
        return Bitmap.createBitmap(outputPixels, width, height, input.getConfig());
    }

    /**
     * Calls computeDifference on multiple cropped-and-scaled versions of
     * bitmap2.
     */
    @TargetApi(12)
    public static Difference computeMinimumDifference(
            Bitmap bitmap1, Bitmap bitmap2, Pair<Double, Double>[] borderCrops) {

        // Compute the difference with the original image (bitmap2) first.
        Difference minDiff = computeDifference(bitmap1, bitmap2);
        // Then go through the list of borderCrops.
        for (Pair<Double, Double> borderCrop : borderCrops) {
            // Compute the difference between bitmap1 and a transformed
            // version of bitmap2.
            Bitmap bitmap2s = shrinkAndScaleBilinear(bitmap2, borderCrop.first, borderCrop.second);
            Difference d = computeDifference(bitmap1, bitmap2s);
            // Keep the minimum difference.
            if (d.greatestPixelDifference < minDiff.greatestPixelDifference) {
                minDiff = d;
                minDiff.bestMatchBorderCrop = borderCrop;
            }
        }
        return minDiff;
    }

    /**
     * Calls computeMinimumDifference on a default list of borderCrop.
     */
    @TargetApi(12)
    public static Difference computeMinimumDifference(
            Bitmap bitmap1, Bitmap bitmap2, int trueWidth, int trueHeight) {

        double hBorder = (double) bitmap1.getWidth() / (double) trueWidth;
        double vBorder = (double) bitmap1.getHeight() / (double) trueHeight;
        double hBorderH = 0.5 * hBorder; // Half-texel horizontal border
        double vBorderH = 0.5 * vBorder; // Half-texel vertical border
        return computeMinimumDifference(
                bitmap1,
                bitmap2,
                new Pair[] {
                    Pair.create(hBorderH, 0.0),
                    Pair.create(hBorderH, vBorderH),
                    Pair.create(0.0, vBorderH),
                    Pair.create(hBorder, 0.0),
                    Pair.create(hBorder, vBorder),
                    Pair.create(0.0, vBorder)
                });
        // This default list of borderCrop comes from the behavior of
        // GLConsumer.computeTransformMatrix().
    }

    /* Describes the difference between two {@link Bitmap} instances. */
    public static final class Difference {

        public final int greatestPixelDifference;
        public final Pair<Integer, Integer> greatestPixelDifferenceCoordinates;
        public Pair<Double, Double> bestMatchBorderCrop;

        private Difference(int greatestPixelDifference) {
            this(greatestPixelDifference, null, Pair.create(0.0, 0.0));
        }

        private Difference(
                int greatestPixelDifference,
                Pair<Integer, Integer> greatestPixelDifferenceCoordinates) {
            this(greatestPixelDifference, greatestPixelDifferenceCoordinates,
                    Pair.create(0.0, 0.0));
        }

        private Difference(
                int greatestPixelDifference,
                Pair<Integer, Integer> greatestPixelDifferenceCoordinates,
                Pair<Double, Double> bestMatchBorderCrop) {
            this.greatestPixelDifference = greatestPixelDifference;
            this.greatestPixelDifferenceCoordinates = greatestPixelDifferenceCoordinates;
            this.bestMatchBorderCrop = bestMatchBorderCrop;
        }
    }

}

/* Wrapper for MIME types. */
final class MimeTypes {

    private MimeTypes() {}

    public static final String VIDEO_VP9 = "video/x-vnd.on2.vp9";
    public static final String VIDEO_H264 = "video/avc";

    public static boolean isVideo(String mimeType) {
        return mimeType.startsWith("video");
    }

}
