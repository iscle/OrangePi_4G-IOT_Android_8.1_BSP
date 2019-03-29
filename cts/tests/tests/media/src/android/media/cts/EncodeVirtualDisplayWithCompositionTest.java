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


import android.app.Presentation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.SurfaceTexture;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.MediaCodec;
import android.media.MediaCodec.BufferInfo;
import android.media.MediaCodecInfo;
import android.media.MediaCodecInfo.CodecCapabilities;
import android.media.MediaCodecInfo.CodecProfileLevel;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Parcel;
import android.support.test.filters.SmallTest;
import android.platform.test.annotations.RequiresDevice;
import android.test.AndroidTestCase;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Size;
import android.view.Display;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import android.media.cts.R;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tests to check if MediaCodec encoding works with composition of multiple virtual displays
 * The test also tries to destroy and create virtual displays repeatedly to
 * detect any issues. The test itself does not check the output as it is already done in other
 * tests.
 */
@SmallTest
@RequiresDevice
public class EncodeVirtualDisplayWithCompositionTest extends AndroidTestCase {
    private static final String TAG = "EncodeVirtualDisplayWithCompositionTest";
    private static final boolean DBG = true;
    private static final String MIME_TYPE = MediaFormat.MIMETYPE_VIDEO_AVC;

    private static final long DEFAULT_WAIT_TIMEOUT_MS = 3000;
    private static final long DEFAULT_WAIT_TIMEOUT_US = 3000000;

    private static final int COLOR_RED =  makeColor(100, 0, 0);
    private static final int COLOR_BLUE =  makeColor(0, 100, 0);
    private static final int COLOR_GREEN =  makeColor(0, 0, 100);
    private static final int COLOR_GREY =  makeColor(100, 100, 100);

    private static final int BITRATE_1080p = 20000000;
    private static final int BITRATE_720p = 14000000;
    private static final int BITRATE_800x480 = 14000000;
    private static final int BITRATE_DEFAULT = 10000000;

    private static final int IFRAME_INTERVAL = 10;

    private static final int MAX_NUM_WINDOWS = 3;

    private static Handler sHandlerForRunOnMain = new Handler(Looper.getMainLooper());

    private Surface mEncodingSurface;
    private OutputSurface mDecodingSurface;
    private volatile boolean mCodecConfigReceived = false;
    private volatile boolean mCodecBufferReceived = false;
    private EncodingHelper mEncodingHelper;
    private MediaCodec mDecoder;
    private final ByteBuffer mPixelBuf = ByteBuffer.allocateDirect(4);
    private volatile boolean mIsQuitting = false;
    private Throwable mTestException;
    private VirtualDisplayPresentation mLocalPresentation;
    private RemoteVirtualDisplayPresentation mRemotePresentation;
    private ByteBuffer[] mDecoderInputBuffers;

    /** event listener for test without verifying output */
    private EncoderEventListener mEncoderEventListener = new EncoderEventListener() {
        @Override
        public void onCodecConfig(ByteBuffer data, MediaCodec.BufferInfo info) {
            mCodecConfigReceived = true;
        }
        @Override
        public void onBufferReady(ByteBuffer data, MediaCodec.BufferInfo info) {
            mCodecBufferReceived = true;
        }
        @Override
        public void onError(String errorMessage) {
            fail(errorMessage);
        }
    };

    /* TEST_COLORS static initialization; need ARGB for ColorDrawable */
    private static int makeColor(int red, int green, int blue) {
        return 0xff << 24 | (red & 0xff) << 16 | (green & 0xff) << 8 | (blue & 0xff);
    }

    public void testVirtualDisplayRecycles() throws Exception {
        doTestVirtualDisplayRecycles(3);
    }

    public void testRendering800x480Locally() throws Throwable {
        Log.i(TAG, "testRendering800x480Locally");
        if (isConcurrentEncodingDecodingSupported(800, 480, BITRATE_800x480)) {
            runTestRenderingInSeparateThread(800, 480, false, false);
        } else {
            Log.i(TAG, "SKIPPING testRendering800x480Locally(): codec not supported");
        }
    }

    public void testRenderingMaxResolutionLocally() throws Throwable {
        Log.i(TAG, "testRenderingMaxResolutionLocally");
        Size maxRes = checkMaxConcurrentEncodingDecodingResolution();
        if (maxRes == null) {
            Log.i(TAG, "SKIPPING testRenderingMaxResolutionLocally(): codec not supported");
        } else {
            Log.w(TAG, "Trying resolution " + maxRes);
            runTestRenderingInSeparateThread(maxRes.getWidth(), maxRes.getHeight(), false, false);
        }
    }

    public void testRendering800x480Remotely() throws Throwable {
        Log.i(TAG, "testRendering800x480Remotely");
        if (isConcurrentEncodingDecodingSupported(800, 480, BITRATE_800x480)) {
            runTestRenderingInSeparateThread(800, 480, true, false);
        } else {
            Log.i(TAG, "SKIPPING testRendering800x480Remotely(): codec not supported");
        }
    }

    public void testRenderingMaxResolutionRemotely() throws Throwable {
        Log.i(TAG, "testRenderingMaxResolutionRemotely");
        Size maxRes = checkMaxConcurrentEncodingDecodingResolution();
        if (maxRes == null) {
            Log.i(TAG, "SKIPPING testRenderingMaxResolutionRemotely(): codec not supported");
        } else {
            Log.w(TAG, "Trying resolution " + maxRes);
            runTestRenderingInSeparateThread(maxRes.getWidth(), maxRes.getHeight(), true, false);
        }
    }

    public void testRendering800x480RemotelyWith3Windows() throws Throwable {
        Log.i(TAG, "testRendering800x480RemotelyWith3Windows");
        if (isConcurrentEncodingDecodingSupported(800, 480, BITRATE_800x480)) {
            runTestRenderingInSeparateThread(800, 480, true, true);
        } else {
            Log.i(TAG, "SKIPPING testRendering800x480RemotelyWith3Windows(): codec not supported");
        }
    }

    public void testRendering800x480LocallyWith3Windows() throws Throwable {
        Log.i(TAG, "testRendering800x480LocallyWith3Windows");
        if (isConcurrentEncodingDecodingSupported(800, 480, BITRATE_800x480)) {
            runTestRenderingInSeparateThread(800, 480, false, true);
        } else {
            Log.i(TAG, "SKIPPING testRendering800x480LocallyWith3Windows(): codec not supported");
        }
    }

    /**
     * Run rendering test in a separate thread. This is necessary as {@link OutputSurface} requires
     * constructing it in a non-test thread.
     * @param w
     * @param h
     * @throws Exception
     */
    private void runTestRenderingInSeparateThread(final int w, final int h,
            final boolean runRemotely, final boolean multipleWindows) throws Throwable {
        mTestException = null;
        Thread renderingThread = new Thread(new Runnable() {
            public void run() {
                try {
                    doTestRenderingOutput(w, h, runRemotely, multipleWindows);
                } catch (Throwable t) {
                    t.printStackTrace();
                    mTestException = t;
                }
            }
        });
        renderingThread.start();
        renderingThread.join(60000);
        assertTrue(!renderingThread.isAlive());
        if (mTestException != null) {
            throw mTestException;
        }
    }

    private void doTestRenderingOutput(int w, int h, boolean runRemotely, boolean multipleWindows)
            throws Throwable {
        if (DBG) {
            Log.i(TAG, "doTestRenderingOutput for w:" + w + " h:" + h);
        }
        try {
            mIsQuitting = false;
            mDecoder = MediaCodec.createDecoderByType(MIME_TYPE);
            MediaFormat decoderFormat = MediaFormat.createVideoFormat(MIME_TYPE, w, h);
            mDecodingSurface = new OutputSurface(w, h);
            mDecoder.configure(decoderFormat, mDecodingSurface.getSurface(), null, 0);
            mDecoder.start();
            mDecoderInputBuffers = mDecoder.getInputBuffers();

            mEncodingHelper = new EncodingHelper();
            mEncodingSurface = mEncodingHelper.startEncoding(w, h,
                    new EncoderEventListener() {
                @Override
                public void onCodecConfig(ByteBuffer data, BufferInfo info) {
                    if (DBG) {
                        Log.i(TAG, "onCodecConfig l:" + info.size);
                    }
                    handleEncodedData(data, info);
                }

                @Override
                public void onBufferReady(ByteBuffer data, BufferInfo info) {
                    if (DBG) {
                        Log.i(TAG, "onBufferReady l:" + info.size);
                    }
                    handleEncodedData(data, info);
                }

                @Override
                public void onError(String errorMessage) {
                    fail(errorMessage);
                }

                private void handleEncodedData(ByteBuffer data, BufferInfo info) {
                    if (mIsQuitting) {
                        if (DBG) {
                            Log.i(TAG, "ignore data as test is quitting");
                        }
                        return;
                    }
                    int inputBufferIndex = mDecoder.dequeueInputBuffer(DEFAULT_WAIT_TIMEOUT_US);
                    if (inputBufferIndex < 0) {
                        if (DBG) {
                            Log.i(TAG, "dequeueInputBuffer returned:" + inputBufferIndex);
                        }
                        return;
                    }
                    assertTrue(inputBufferIndex >= 0);
                    ByteBuffer inputBuffer = mDecoderInputBuffers[inputBufferIndex];
                    inputBuffer.clear();
                    inputBuffer.put(data);
                    mDecoder.queueInputBuffer(inputBufferIndex, 0, info.size,
                            info.presentationTimeUs, info.flags);
                }
            });
            GlCompositor compositor = new GlCompositor();
            if (DBG) {
                Log.i(TAG, "start composition");
            }
            compositor.startComposition(mEncodingSurface, w, h, multipleWindows ? 3 : 1);

            if (DBG) {
                Log.i(TAG, "create display");
            }

            Renderer renderer = null;
            if (runRemotely) {
                mRemotePresentation = new RemoteVirtualDisplayPresentation(getContext(),
                        compositor.getWindowSurface(multipleWindows? 1 : 0), w, h);
                mRemotePresentation.connect();
                mRemotePresentation.start();
                renderer = mRemotePresentation;
            } else {
                mLocalPresentation = new VirtualDisplayPresentation(getContext(),
                        compositor.getWindowSurface(multipleWindows? 1 : 0), w, h);
                mLocalPresentation.createVirtualDisplay();
                mLocalPresentation.createPresentation();
                renderer = mLocalPresentation;
            }

            if (DBG) {
                Log.i(TAG, "start rendering and check");
            }
            renderColorAndCheckResult(renderer, w, h, COLOR_RED);
            renderColorAndCheckResult(renderer, w, h, COLOR_BLUE);
            renderColorAndCheckResult(renderer, w, h, COLOR_GREEN);
            renderColorAndCheckResult(renderer, w, h, COLOR_GREY);

            mIsQuitting = true;
            if (runRemotely) {
                mRemotePresentation.disconnect();
            } else {
                mLocalPresentation.dismissPresentation();
                mLocalPresentation.destroyVirtualDisplay();
            }

            compositor.stopComposition();
        } finally {
            if (mEncodingHelper != null) {
                mEncodingHelper.stopEncoding();
                mEncodingHelper = null;
            }
            if (mDecoder != null) {
                mDecoder.stop();
                mDecoder.release();
                mDecoder = null;
            }
            if (mDecodingSurface != null) {
                mDecodingSurface.release();
                mDecodingSurface = null;
            }
        }
    }

    private static final int NUM_MAX_RETRY = 120;
    private static final int IMAGE_WAIT_TIMEOUT_MS = 1000;

    private void renderColorAndCheckResult(Renderer renderer, int w, int h,
            int color) throws Exception {
        BufferInfo info = new BufferInfo();
        for (int i = 0; i < NUM_MAX_RETRY; i++) {
            renderer.doRendering(color);
            int bufferIndex = mDecoder.dequeueOutputBuffer(info,  DEFAULT_WAIT_TIMEOUT_US);
            if (DBG) {
                Log.i(TAG, "decoder dequeueOutputBuffer returned " + bufferIndex);
            }
            if (bufferIndex < 0) {
                continue;
            }
            mDecoder.releaseOutputBuffer(bufferIndex, true);
            if (mDecodingSurface.checkForNewImage(IMAGE_WAIT_TIMEOUT_MS)) {
                mDecodingSurface.drawImage();
                if (checkSurfaceFrameColor(w, h, color)) {
                    Log.i(TAG, "color " + Integer.toHexString(color) + " matched");
                    return;
                }
            } else if(DBG) {
                Log.i(TAG, "no rendering yet");
            }
        }
        fail("Color did not match");
    }

    private boolean checkSurfaceFrameColor(int w, int h, int color) {
        // Read a pixel from the center of the surface.  Might want to read from multiple points
        // and average them together.
        int x = w / 2;
        int y = h / 2;
        GLES20.glReadPixels(x, y, 1, 1, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, mPixelBuf);
        int r = mPixelBuf.get(0) & 0xff;
        int g = mPixelBuf.get(1) & 0xff;
        int b = mPixelBuf.get(2) & 0xff;

        int redExpected = (color >> 16) & 0xff;
        int greenExpected = (color >> 8) & 0xff;
        int blueExpected = color & 0xff;
        if (approxEquals(redExpected, r) && approxEquals(greenExpected, g)
                && approxEquals(blueExpected, b)) {
            return true;
        }
        Log.i(TAG, "expected 0x" + Integer.toHexString(color) + " got 0x"
                + Integer.toHexString(makeColor(r, g, b)));
        return false;
    }

    /**
     * Determines if two color values are approximately equal.
     */
    private static boolean approxEquals(int expected, int actual) {
        final int MAX_DELTA = 4;
        return Math.abs(expected - actual) <= MAX_DELTA;
    }

    private static final int NUM_CODEC_CREATION = 5;
    private static final int NUM_DISPLAY_CREATION = 10;
    private static final int NUM_RENDERING = 10;
    private void doTestVirtualDisplayRecycles(int numDisplays) throws Exception {
        Size maxSize = getMaxSupportedEncoderSize();
        if (maxSize == null) {
            Log.i(TAG, "no codec found, skipping");
            return;
        }
        VirtualDisplayPresentation[] virtualDisplays = new VirtualDisplayPresentation[numDisplays];
        for (int i = 0; i < NUM_CODEC_CREATION; i++) {
            mCodecConfigReceived = false;
            mCodecBufferReceived = false;
            if (DBG) {
                Log.i(TAG, "start encoding");
            }
            EncodingHelper encodingHelper = new EncodingHelper();
            try {
                mEncodingSurface = encodingHelper.startEncoding(
                        maxSize.getWidth(), maxSize.getHeight(), mEncoderEventListener);
                GlCompositor compositor = new GlCompositor();
                if (DBG) {
                    Log.i(TAG, "start composition");
                }
                compositor.startComposition(mEncodingSurface,
                        maxSize.getWidth(), maxSize.getHeight(), numDisplays);
                for (int j = 0; j < NUM_DISPLAY_CREATION; j++) {
                    if (DBG) {
                        Log.i(TAG, "create display");
                    }
                    for (int k = 0; k < numDisplays; k++) {
                        virtualDisplays[k] =
                            new VirtualDisplayPresentation(getContext(),
                                    compositor.getWindowSurface(k),
                                    maxSize.getWidth()/numDisplays, maxSize.getHeight());
                        virtualDisplays[k].createVirtualDisplay();
                        virtualDisplays[k].createPresentation();
                    }
                    if (DBG) {
                        Log.i(TAG, "start rendering");
                    }
                    for (int k = 0; k < NUM_RENDERING; k++) {
                        for (int l = 0; l < numDisplays; l++) {
                            virtualDisplays[l].doRendering(COLOR_RED);
                        }
                        // do not care how many frames are actually rendered.
                        Thread.sleep(1);
                    }
                    for (int k = 0; k < numDisplays; k++) {
                        virtualDisplays[k].dismissPresentation();
                        virtualDisplays[k].destroyVirtualDisplay();
                    }
                    compositor.recreateWindows();
                }
                if (DBG) {
                    Log.i(TAG, "stop composition");
                }
                compositor.stopComposition();
            } finally {
                if (DBG) {
                    Log.i(TAG, "stop encoding");
                }
                encodingHelper.stopEncoding();
                assertTrue(mCodecConfigReceived);
                assertTrue(mCodecBufferReceived);
            }
        }
    }

    interface EncoderEventListener {
        public void onCodecConfig(ByteBuffer data, MediaCodec.BufferInfo info);
        public void onBufferReady(ByteBuffer data, MediaCodec.BufferInfo info);
        public void onError(String errorMessage);
    }

    private class EncodingHelper {
        private MediaCodec mEncoder;
        private volatile boolean mStopEncoding = false;
        private EncoderEventListener mEventListener;
        private int mW;
        private int mH;
        private Thread mEncodingThread;
        private Surface mEncodingSurface;
        private Semaphore mInitCompleted = new Semaphore(0);

        Surface startEncoding(int w, int h, EncoderEventListener eventListener) {
            mStopEncoding = false;
            mW = w;
            mH = h;
            mEventListener = eventListener;
            mEncodingThread = new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        doEncoding();
                    } catch (Exception e) {
                        e.printStackTrace();
                        mEventListener.onError(e.toString());
                    }
                }
            });
            mEncodingThread.start();
            try {
                if (DBG) {
                    Log.i(TAG, "wait for encoder init");
                }
                mInitCompleted.acquire();
                if (DBG) {
                    Log.i(TAG, "wait for encoder done");
                }
            } catch (InterruptedException e) {
                fail("should not happen");
            }
            return mEncodingSurface;
        }

        void stopEncoding() {
            try {
                mStopEncoding = true;
                mEncodingThread.join();
            } catch(InterruptedException e) {
                // just ignore
            } finally {
                mEncodingThread = null;
            }
        }

        private void doEncoding() throws Exception {
            final int TIMEOUT_USEC_NORMAL = 1000000;
            MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, mW, mH);
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            int bitRate = BITRATE_DEFAULT;
            if (mW == 1920 && mH == 1080) {
                bitRate = BITRATE_1080p;
            } else if (mW == 1280 && mH == 720) {
                bitRate = BITRATE_720p;
            } else if (mW == 800 && mH == 480) {
                bitRate = BITRATE_800x480;
            }
            format.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);
            format.setInteger(MediaFormat.KEY_FRAME_RATE, 30);
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL);

            MediaCodecList mcl = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
            String codecName = null;
            if ((codecName = mcl.findEncoderForFormat(format)) == null) {
                throw new RuntimeException("encoder "+ MIME_TYPE + " not support : " + format.toString());
            }

            try {
                mEncoder = MediaCodec.createByCodecName(codecName);
                mEncoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
                mEncodingSurface = mEncoder.createInputSurface();
                mEncoder.start();
                mInitCompleted.release();
                if (DBG) {
                    Log.i(TAG, "starting encoder");
                }
                ByteBuffer[] encoderOutputBuffers = mEncoder.getOutputBuffers();
                MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
                while (!mStopEncoding) {
                    int index = mEncoder.dequeueOutputBuffer(info, TIMEOUT_USEC_NORMAL);
                    if (DBG) {
                        Log.i(TAG, "encoder dequeOutputBuffer returned " + index);
                    }
                    if (index >= 0) {
                        if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0) {
                            Log.i(TAG, "codec config data");
                            ByteBuffer encodedData = encoderOutputBuffers[index];
                            encodedData.position(info.offset);
                            encodedData.limit(info.offset + info.size);
                            mEventListener.onCodecConfig(encodedData, info);
                            mEncoder.releaseOutputBuffer(index, false);
                        } else if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            Log.i(TAG, "EOS, stopping encoding");
                            break;
                        } else {
                            ByteBuffer encodedData = encoderOutputBuffers[index];
                            encodedData.position(info.offset);
                            encodedData.limit(info.offset + info.size);
                            mEventListener.onBufferReady(encodedData, info);
                            mEncoder.releaseOutputBuffer(index, false);
                        }
                    } else if (index == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED){
                        Log.i(TAG, "output buffer changed");
                        encoderOutputBuffers = mEncoder.getOutputBuffers();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
                throw e;
            } finally {
                if (mEncoder != null) {
                    mEncoder.stop();
                    mEncoder.release();
                    mEncoder = null;
                }
                if (mEncodingSurface != null) {
                    mEncodingSurface.release();
                    mEncodingSurface = null;
                }
            }
        }
    }

    /**
     * Handles composition of multiple SurfaceTexture into a single Surface
     */
    private class GlCompositor implements SurfaceTexture.OnFrameAvailableListener {
        private Surface mSurface;
        private int mWidth;
        private int mHeight;
        private volatile int mNumWindows;
        private GlWindow mTopWindow;
        private Thread mCompositionThread;
        private Semaphore mStartCompletionSemaphore;
        private Semaphore mRecreationCompletionSemaphore;
        private Looper mLooper;
        private Handler mHandler;
        private InputSurface mEglHelper;
        private int mGlProgramId = 0;
        private int mGluMVPMatrixHandle;
        private int mGluSTMatrixHandle;
        private int mGlaPositionHandle;
        private int mGlaTextureHandle;
        private float[] mMVPMatrix = new float[16];
        private TopWindowVirtualDisplayPresentation mTopPresentation;

        private static final String VERTEX_SHADER =
                "uniform mat4 uMVPMatrix;\n" +
                "uniform mat4 uSTMatrix;\n" +
                "attribute vec4 aPosition;\n" +
                "attribute vec4 aTextureCoord;\n" +
                "varying vec2 vTextureCoord;\n" +
                "void main() {\n" +
                "  gl_Position = uMVPMatrix * aPosition;\n" +
                "  vTextureCoord = (uSTMatrix * aTextureCoord).xy;\n" +
                "}\n";

        private static final String FRAGMENT_SHADER =
                "#extension GL_OES_EGL_image_external : require\n" +
                "precision mediump float;\n" +
                "varying vec2 vTextureCoord;\n" +
                "uniform samplerExternalOES sTexture;\n" +
                "void main() {\n" +
                "  gl_FragColor = texture2D(sTexture, vTextureCoord);\n" +
                "}\n";

        void startComposition(Surface surface, int w, int h, int numWindows) throws Exception {
            mSurface = surface;
            mWidth = w;
            mHeight = h;
            mNumWindows = numWindows;
            mCompositionThread = new Thread(new CompositionRunnable());
            mStartCompletionSemaphore = new Semaphore(0);
            mCompositionThread.start();
            waitForStartCompletion();
        }

        void stopComposition() {
            try {
                if (mLooper != null) {
                    mLooper.quit();
                    mCompositionThread.join();
                }
            } catch (InterruptedException e) {
                // don't care
            }
            mCompositionThread = null;
            mSurface = null;
            mStartCompletionSemaphore = null;
        }

        Surface getWindowSurface(int windowIndex) {
            return mTopPresentation.getSurface(windowIndex);
        }

        void recreateWindows() throws Exception {
            mRecreationCompletionSemaphore = new Semaphore(0);
            Message msg = mHandler.obtainMessage(CompositionHandler.DO_RECREATE_WINDOWS);
            mHandler.sendMessage(msg);
            if(!mRecreationCompletionSemaphore.tryAcquire(DEFAULT_WAIT_TIMEOUT_MS,
                    TimeUnit.MILLISECONDS)) {
                fail("recreation timeout");
            }
            mTopPresentation.waitForSurfaceReady(DEFAULT_WAIT_TIMEOUT_MS);
        }

        @Override
        public void onFrameAvailable(SurfaceTexture surface) {
            if (DBG) {
                Log.i(TAG, "onFrameAvailable " + surface);
            }
            GlWindow w = mTopWindow;
            if (w != null) {
                w.markTextureUpdated();
                requestUpdate();
            } else {
                Log.w(TAG, "top window gone");
            }
        }

        private void requestUpdate() {
            Thread compositionThread = mCompositionThread;
            if (compositionThread == null || !compositionThread.isAlive()) {
                return;
            }
            Message msg = mHandler.obtainMessage(CompositionHandler.DO_RENDERING);
            mHandler.sendMessage(msg);
        }

        private int loadShader(int shaderType, String source) throws GlException {
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

        private int createProgram(String vertexSource, String fragmentSource) throws GlException {
            int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource);
            if (vertexShader == 0) {
                return 0;
            }
            int pixelShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource);
            if (pixelShader == 0) {
                return 0;
            }

            int program = GLES20.glCreateProgram();
            checkGlError("glCreateProgram");
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

        private void initGl() throws GlException {
            mEglHelper = new InputSurface(mSurface);
            mEglHelper.makeCurrent();
            mGlProgramId = createProgram(VERTEX_SHADER, FRAGMENT_SHADER);
            mGlaPositionHandle = GLES20.glGetAttribLocation(mGlProgramId, "aPosition");
            checkGlError("glGetAttribLocation aPosition");
            if (mGlaPositionHandle == -1) {
                throw new RuntimeException("Could not get attrib location for aPosition");
            }
            mGlaTextureHandle = GLES20.glGetAttribLocation(mGlProgramId, "aTextureCoord");
            checkGlError("glGetAttribLocation aTextureCoord");
            if (mGlaTextureHandle == -1) {
                throw new RuntimeException("Could not get attrib location for aTextureCoord");
            }
            mGluMVPMatrixHandle = GLES20.glGetUniformLocation(mGlProgramId, "uMVPMatrix");
            checkGlError("glGetUniformLocation uMVPMatrix");
            if (mGluMVPMatrixHandle == -1) {
                throw new RuntimeException("Could not get attrib location for uMVPMatrix");
            }
            mGluSTMatrixHandle = GLES20.glGetUniformLocation(mGlProgramId, "uSTMatrix");
            checkGlError("glGetUniformLocation uSTMatrix");
            if (mGluSTMatrixHandle == -1) {
                throw new RuntimeException("Could not get attrib location for uSTMatrix");
            }
            Matrix.setIdentityM(mMVPMatrix, 0);
            Log.i(TAG, "initGl w:" + mWidth + " h:" + mHeight);
            GLES20.glViewport(0, 0, mWidth, mHeight);
            float[] vMatrix = new float[16];
            float[] projMatrix = new float[16];
            // max window is from (0,0) to (mWidth - 1, mHeight - 1)
            float wMid = mWidth / 2f;
            float hMid = mHeight / 2f;
            // look from positive z to hide windows in lower z
            Matrix.setLookAtM(vMatrix, 0, wMid, hMid, 5f, wMid, hMid, 0f, 0f, 1.0f, 0.0f);
            Matrix.orthoM(projMatrix, 0, -wMid, wMid, -hMid, hMid, 1, 10);
            Matrix.multiplyMM(mMVPMatrix, 0, projMatrix, 0, vMatrix, 0);
            createWindows();

        }

        private void createWindows() throws GlException {
            mTopWindow = new GlWindow(this, 0, 0, mWidth, mHeight);
            mTopWindow.init();
            mTopPresentation = new TopWindowVirtualDisplayPresentation(mContext,
                    mTopWindow.getSurface(), mWidth, mHeight, mNumWindows);
            mTopPresentation.createVirtualDisplay();
            mTopPresentation.createPresentation();
            ((TopWindowPresentation) mTopPresentation.getPresentation()).populateWindows();
        }

        private void cleanupGl() {
            if (mTopPresentation != null) {
                mTopPresentation.dismissPresentation();
                mTopPresentation.destroyVirtualDisplay();
                mTopPresentation = null;
            }
            if (mTopWindow != null) {
                mTopWindow.cleanup();
                mTopWindow = null;
            }
            if (mEglHelper != null) {
                mEglHelper.release();
                mEglHelper = null;
            }
        }

        private void doGlRendering() throws GlException {
            if (DBG) {
                Log.i(TAG, "doGlRendering");
            }
            mTopWindow.updateTexImageIfNecessary();
            GLES20.glClearColor(0.0f, 1.0f, 0.0f, 1.0f);
            GLES20.glClear(GLES20.GL_DEPTH_BUFFER_BIT | GLES20.GL_COLOR_BUFFER_BIT);

            GLES20.glUseProgram(mGlProgramId);
            GLES20.glUniformMatrix4fv(mGluMVPMatrixHandle, 1, false, mMVPMatrix, 0);
            mTopWindow.onDraw(mGluSTMatrixHandle, mGlaPositionHandle, mGlaTextureHandle);
            checkGlError("window draw");
            if (DBG) {
                final IntBuffer pixels = IntBuffer.allocate(1);
                GLES20.glReadPixels(mWidth / 2, mHeight / 2, 1, 1,
                        GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, pixels);
                Log.i(TAG, "glReadPixels returned 0x" + Integer.toHexString(pixels.get(0)));
            }
            mEglHelper.swapBuffers();
        }

        private void doRecreateWindows() throws GlException {
            mTopPresentation.dismissPresentation();
            mTopPresentation.destroyVirtualDisplay();
            mTopWindow.cleanup();
            createWindows();
            mRecreationCompletionSemaphore.release();
        }

        private void waitForStartCompletion() throws Exception {
            if (!mStartCompletionSemaphore.tryAcquire(DEFAULT_WAIT_TIMEOUT_MS,
                    TimeUnit.MILLISECONDS)) {
                fail("start timeout");
            }
            mStartCompletionSemaphore = null;
            mTopPresentation.waitForSurfaceReady(DEFAULT_WAIT_TIMEOUT_MS);
        }

        private class CompositionRunnable implements Runnable {
            @Override
            public void run() {
                try {
                    Looper.prepare();
                    mLooper = Looper.myLooper();
                    mHandler = new CompositionHandler();
                    initGl();
                    // init done
                    mStartCompletionSemaphore.release();
                    Looper.loop();
                } catch (GlException e) {
                    e.printStackTrace();
                    fail("got gl exception");
                } finally {
                    cleanupGl();
                    mHandler = null;
                    mLooper = null;
                }
            }
        }

        private class CompositionHandler extends Handler {
            private static final int DO_RENDERING = 1;
            private static final int DO_RECREATE_WINDOWS = 2;

            @Override
            public void handleMessage(Message msg) {
                try {
                    switch(msg.what) {
                        case DO_RENDERING: {
                            doGlRendering();
                        } break;
                        case DO_RECREATE_WINDOWS: {
                            doRecreateWindows();
                        } break;
                    }
                } catch (GlException e) {
                    //ignore as this can happen during tearing down
                }
            }
        }

        private class GlWindow {
            private static final int FLOAT_SIZE_BYTES = 4;
            private static final int TRIANGLE_VERTICES_DATA_STRIDE_BYTES = 5 * FLOAT_SIZE_BYTES;
            private static final int TRIANGLE_VERTICES_DATA_POS_OFFSET = 0;
            private static final int TRIANGLE_VERTICES_DATA_UV_OFFSET = 3;
            private int mBlX;
            private int mBlY;
            private int mWidth;
            private int mHeight;
            private int mTextureId = 0; // 0 is invalid
            private volatile SurfaceTexture mSurfaceTexture;
            private volatile Surface mSurface;
            private FloatBuffer mVerticesData;
            private float[] mSTMatrix = new float[16];
            private AtomicInteger mNumTextureUpdated = new AtomicInteger(0);
            private GlCompositor mCompositor;

            /**
             * @param blX X coordinate of bottom-left point of window
             * @param blY Y coordinate of bottom-left point of window
             * @param w window width
             * @param h window height
             */
            public GlWindow(GlCompositor compositor, int blX, int blY, int w, int h) {
                mCompositor = compositor;
                mBlX = blX;
                mBlY = blY;
                mWidth = w;
                mHeight = h;
                int trX = blX + w;
                int trY = blY + h;
                float[] vertices = new float[] {
                        // x, y, z, u, v
                        mBlX, mBlY, 0, 0, 0,
                        trX, mBlY, 0, 1, 0,
                        mBlX, trY, 0, 0, 1,
                        trX, trY, 0, 1, 1
                };
                Log.i(TAG, "create window " + this + " blX:" + mBlX + " blY:" + mBlY + " trX:" +
                        trX + " trY:" + trY);
                mVerticesData = ByteBuffer.allocateDirect(
                        vertices.length * FLOAT_SIZE_BYTES)
                                .order(ByteOrder.nativeOrder()).asFloatBuffer();
                mVerticesData.put(vertices).position(0);
            }

            /**
             * initialize the window for composition. counter-part is cleanup()
             * @throws GlException
             */
            public void init() throws GlException {
                int[] textures = new int[1];
                GLES20.glGenTextures(1, textures, 0);

                mTextureId = textures[0];
                GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mTextureId);
                checkGlError("glBindTexture mTextureID");

                GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                        GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST);
                GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                        GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST);
                GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S,
                        GLES20.GL_CLAMP_TO_EDGE);
                GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T,
                        GLES20.GL_CLAMP_TO_EDGE);
                checkGlError("glTexParameter");
                mSurfaceTexture = new SurfaceTexture(mTextureId);
                mSurfaceTexture.setDefaultBufferSize(mWidth, mHeight);
                mSurface = new Surface(mSurfaceTexture);
                mSurfaceTexture.setOnFrameAvailableListener(mCompositor);
            }

            public void cleanup() {
                mNumTextureUpdated.set(0);
                if (mTextureId != 0) {
                    int[] textures = new int[] {
                            mTextureId
                    };
                    GLES20.glDeleteTextures(1, textures, 0);
                }
                GLES20.glFinish();
                if (mSurface != null) {
                    mSurface.release();
                    mSurface = null;
                }
                if (mSurfaceTexture != null) {
                    mSurfaceTexture.release();
                    mSurfaceTexture = null;
                }
            }

            /**
             * make texture as updated so that it can be updated in the next rendering.
             */
            public void markTextureUpdated() {
                mNumTextureUpdated.incrementAndGet();
            }

            /**
             * update texture for rendering if it is updated.
             */
            public void updateTexImageIfNecessary() {
                int numTextureUpdated = mNumTextureUpdated.getAndDecrement();
                if (numTextureUpdated > 0) {
                    if (DBG) {
                        Log.i(TAG, "updateTexImageIfNecessary " + this);
                    }
                    mSurfaceTexture.updateTexImage();
                    mSurfaceTexture.getTransformMatrix(mSTMatrix);
                }
                if (numTextureUpdated < 0) {
                    fail("should not happen");
                }
            }

            /**
             * draw the window. It will not be drawn at all if the window is not visible.
             * @param uSTMatrixHandle shader handler for the STMatrix for texture coordinates
             * mapping
             * @param aPositionHandle shader handle for vertex position.
             * @param aTextureHandle shader handle for texture
             */
            public void onDraw(int uSTMatrixHandle, int aPositionHandle, int aTextureHandle) {
                GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
                GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, mTextureId);
                mVerticesData.position(TRIANGLE_VERTICES_DATA_POS_OFFSET);
                GLES20.glVertexAttribPointer(aPositionHandle, 3, GLES20.GL_FLOAT, false,
                    TRIANGLE_VERTICES_DATA_STRIDE_BYTES, mVerticesData);
                GLES20.glEnableVertexAttribArray(aPositionHandle);

                mVerticesData.position(TRIANGLE_VERTICES_DATA_UV_OFFSET);
                GLES20.glVertexAttribPointer(aTextureHandle, 2, GLES20.GL_FLOAT, false,
                    TRIANGLE_VERTICES_DATA_STRIDE_BYTES, mVerticesData);
                GLES20.glEnableVertexAttribArray(aTextureHandle);
                GLES20.glUniformMatrix4fv(uSTMatrixHandle, 1, false, mSTMatrix, 0);
                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
            }

            public SurfaceTexture getSurfaceTexture() {
                return mSurfaceTexture;
            }

            public Surface getSurface() {
                return mSurface;
            }
        }
    }

    static void checkGlError(String op) throws GlException {
        int error;
        while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
            Log.e(TAG, op + ": glError " + error);
            throw new GlException(op + ": glError " + error);
        }
    }

    public static class GlException extends Exception {
        public GlException(String msg) {
            super(msg);
        }
    }

    private interface Renderer {
        void doRendering(final int color) throws Exception;
    }

    private static class VirtualDisplayPresentation implements Renderer {
        protected final Context mContext;
        protected final Surface mSurface;
        protected final int mWidth;
        protected final int mHeight;
        protected VirtualDisplay mVirtualDisplay;
        protected TestPresentationBase mPresentation;
        private final DisplayManager mDisplayManager;

        VirtualDisplayPresentation(Context context, Surface surface, int w, int h) {
            mContext = context;
            mSurface = surface;
            mWidth = w;
            mHeight = h;
            mDisplayManager = (DisplayManager)context.getSystemService(Context.DISPLAY_SERVICE);
        }

        void createVirtualDisplay() {
            runOnMainSync(new Runnable() {
                @Override
                public void run() {
                    mVirtualDisplay = mDisplayManager.createVirtualDisplay(
                            TAG, mWidth, mHeight, 200, mSurface,
                            DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY |
                            DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION);
                }
            });
        }

        void destroyVirtualDisplay() {
            runOnMainSync(new Runnable() {
                @Override
                public void run() {
                    mVirtualDisplay.release();
                }
            });
        }

        void createPresentation() {
            runOnMainSync(new Runnable() {
                @Override
                public void run() {
                    mPresentation = doCreatePresentation();
                    mPresentation.show();
                }
            });
        }

        protected TestPresentationBase doCreatePresentation() {
            return new TestPresentation(mContext, mVirtualDisplay.getDisplay());
        }

        TestPresentationBase getPresentation() {
            return mPresentation;
        }

        void dismissPresentation() {
            runOnMainSync(new Runnable() {
                @Override
                public void run() {
                    mPresentation.dismiss();
                }
            });
        }

        @Override
        public void doRendering(final int color) throws Exception {
            runOnMainSync(new Runnable() {
                @Override
                public void run() {
                    mPresentation.doRendering(color);
                }
            });
        }
    }

    private static class TestPresentationBase extends Presentation {

        public TestPresentationBase(Context outerContext, Display display) {
            // This theme is required to prevent an extra view from obscuring the presentation
            super(outerContext, display,
                    android.R.style.Theme_Holo_Light_NoActionBar_TranslucentDecor);
            getWindow().setType(WindowManager.LayoutParams.TYPE_PRIVATE_PRESENTATION);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_LOCAL_FOCUS_MODE);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
        }

        public void doRendering(int color) {
            // to be implemented by child
        }
    }

    private static class TestPresentation extends TestPresentationBase {
        private ImageView mImageView;

        public TestPresentation(Context outerContext, Display display) {
            super(outerContext, display);
        }

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            mImageView = new ImageView(getContext());
            mImageView.setImageDrawable(new ColorDrawable(COLOR_RED));
            mImageView.setLayoutParams(new LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
            setContentView(mImageView);
        }

        public void doRendering(int color) {
            if (DBG) {
                Log.i(TAG, "doRendering " + Integer.toHexString(color));
            }
            mImageView.setImageDrawable(new ColorDrawable(color));
        }
    }

    private static class TopWindowPresentation extends TestPresentationBase {
        private FrameLayout[] mWindowsLayout = new FrameLayout[MAX_NUM_WINDOWS];
        private CompositionTextureView[] mWindows = new CompositionTextureView[MAX_NUM_WINDOWS];
        private final int mNumWindows;
        private final Semaphore mWindowWaitSemaphore = new Semaphore(0);

        public TopWindowPresentation(int numWindows, Context outerContext, Display display) {
            super(outerContext, display);
            mNumWindows = numWindows;
        }

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            if (DBG) {
                Log.i(TAG, "TopWindowPresentation onCreate, numWindows " + mNumWindows);
            }
            setContentView(R.layout.composition_layout);
            mWindowsLayout[0] = (FrameLayout) findViewById(R.id.window0);
            mWindowsLayout[1] = (FrameLayout) findViewById(R.id.window1);
            mWindowsLayout[2] = (FrameLayout) findViewById(R.id.window2);
        }

        public void populateWindows() {
            runOnMain(new Runnable() {
                public void run() {
                    for (int i = 0; i < mNumWindows; i++) {
                        mWindows[i] = new CompositionTextureView(getContext());
                        mWindows[i].setLayoutParams(new ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT));
                        mWindowsLayout[i].setVisibility(View.VISIBLE);
                        mWindowsLayout[i].addView(mWindows[i]);
                        mWindows[i].startListening();
                    }
                    mWindowWaitSemaphore.release();
                }
            });
        }

        public void waitForSurfaceReady(long timeoutMs) throws Exception {
            mWindowWaitSemaphore.tryAcquire(DEFAULT_WAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            for (int i = 0; i < mNumWindows; i++) {
                if(!mWindows[i].waitForSurfaceReady(timeoutMs)) {
                    fail("surface wait timeout");
                }
            }
        }

        public Surface getSurface(int windowIndex) {
            Surface surface = mWindows[windowIndex].getSurface();
            assertNotNull(surface);
            return surface;
        }
    }

    private static class TopWindowVirtualDisplayPresentation extends VirtualDisplayPresentation {
        private final int mNumWindows;

        TopWindowVirtualDisplayPresentation(Context context, Surface surface, int w, int h,
                int numWindows) {
            super(context, surface, w, h);
            assertNotNull(surface);
            mNumWindows = numWindows;
        }

        void waitForSurfaceReady(long timeoutMs) throws Exception {
            ((TopWindowPresentation) mPresentation).waitForSurfaceReady(timeoutMs);
        }

        Surface getSurface(int windowIndex) {
            return ((TopWindowPresentation) mPresentation).getSurface(windowIndex);
        }

        protected TestPresentationBase doCreatePresentation() {
            return new TopWindowPresentation(mNumWindows, mContext, mVirtualDisplay.getDisplay());
        }
    }

    private static class RemoteVirtualDisplayPresentation implements Renderer {
        /** argument: Surface, int w, int h, return none */
        private static final int BINDER_CMD_START = IBinder.FIRST_CALL_TRANSACTION;
        /** argument: int color, return none */
        private static final int BINDER_CMD_RENDER = IBinder.FIRST_CALL_TRANSACTION + 1;

        private final Context mContext;
        private final Surface mSurface;
        private final int mWidth;
        private final int mHeight;

        private IBinder mService;
        private final Semaphore mConnectionWait = new Semaphore(0);
        private final ServiceConnection mConnection = new ServiceConnection() {

            public void onServiceConnected(ComponentName arg0, IBinder arg1) {
                mService = arg1;
                mConnectionWait.release();
            }

            public void onServiceDisconnected(ComponentName arg0) {
                //ignore
            }

        };

        RemoteVirtualDisplayPresentation(Context context, Surface surface, int w, int h) {
            mContext = context;
            mSurface = surface;
            mWidth = w;
            mHeight = h;
        }

        void connect() throws Exception {
            Intent intent = new Intent();
            intent.setClassName("android.media.cts",
                    "android.media.cts.RemoteVirtualDisplayService");
            mContext.bindService(intent, mConnection, Context.BIND_AUTO_CREATE);
            if (!mConnectionWait.tryAcquire(DEFAULT_WAIT_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                fail("cannot bind to service");
            }
        }

        void disconnect() {
            mContext.unbindService(mConnection);
        }

        void start() throws Exception {
            Parcel parcel = Parcel.obtain();
            mSurface.writeToParcel(parcel, 0);
            parcel.writeInt(mWidth);
            parcel.writeInt(mHeight);
            mService.transact(BINDER_CMD_START, parcel, null, 0);
        }

        @Override
        public void doRendering(int color) throws Exception {
            Parcel parcel = Parcel.obtain();
            parcel.writeInt(color);
            mService.transact(BINDER_CMD_RENDER, parcel, null, 0);
        }
    }

    private static Size getMaxSupportedEncoderSize() {
        final Size[] standardSizes = new Size[] {
            new Size(1920, 1080),
            new Size(1280, 720),
            new Size(720, 480),
            new Size(352, 576)
        };

        MediaCodecList mcl = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
        for (Size sz : standardSizes) {
            MediaFormat format = MediaFormat.createVideoFormat(
                MIME_TYPE, sz.getWidth(), sz.getHeight());
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            int bitRate = BITRATE_DEFAULT;
            if (sz.getWidth() == 1920 && sz.getHeight() == 1080) {
                bitRate = BITRATE_1080p;
            } else if (sz.getWidth() == 1280 && sz.getHeight() == 720) {
                bitRate = BITRATE_720p;
            } else if (sz.getWidth() == 800 && sz.getHeight() == 480) {
                bitRate = BITRATE_800x480;
            }
            format.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);
            format.setInteger(MediaFormat.KEY_FRAME_RATE, 30);
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL);
            Log.i(TAG,"format = " + format.toString());
            if (mcl.findEncoderForFormat(format) != null) {
                return sz;
            }
        }
        return null;
    }

    /**
     * Check maximum concurrent encoding / decoding resolution allowed.
     * Some H/Ws cannot support maximum resolution reported in encoder if decoder is running
     * at the same time.
     * Check is done for 4 different levels: 1080p, 720p, 800x480, 480p
     * (The last one is required by CDD.)
     */
    private Size checkMaxConcurrentEncodingDecodingResolution() {
        if (isConcurrentEncodingDecodingSupported(1920, 1080, BITRATE_1080p)) {
            return new Size(1920, 1080);
        } else if (isConcurrentEncodingDecodingSupported(1280, 720, BITRATE_720p)) {
            return new Size(1280, 720);
        } else if (isConcurrentEncodingDecodingSupported(800, 480, BITRATE_800x480)) {
            return new Size(800, 480);
        } else if (isConcurrentEncodingDecodingSupported(720, 480, BITRATE_DEFAULT)) {
            return new Size(720, 480);
        }
        Log.i(TAG, "SKIPPING test: concurrent encoding and decoding is not supported");
        return null;
    }

    private boolean isConcurrentEncodingDecodingSupported(int w, int h, int bitRate) {
        MediaCodecList mcl = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
        MediaFormat testFormat = MediaFormat.createVideoFormat(MIME_TYPE, w, h);
        testFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);
        testFormat.setInteger(MediaFormat.KEY_FRAME_RATE, 30);
        if (mcl.findDecoderForFormat(testFormat) == null
                || mcl.findEncoderForFormat(testFormat) == null) {
            return false;
        }

        MediaCodec decoder = null;
        OutputSurface decodingSurface = null;
        MediaCodec encoder = null;
        Surface encodingSurface = null;
        try {
            decoder = MediaCodec.createDecoderByType(MIME_TYPE);
            MediaFormat decoderFormat = MediaFormat.createVideoFormat(MIME_TYPE, w, h);
            decodingSurface = new OutputSurface(w, h);
            decodingSurface.makeCurrent();
            decoder.configure(decoderFormat, decodingSurface.getSurface(), null, 0);
            decoder.start();

            MediaFormat format = MediaFormat.createVideoFormat(MIME_TYPE, w, h);
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
            format.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);
            format.setInteger(MediaFormat.KEY_FRAME_RATE, 30);
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, IFRAME_INTERVAL);
            encoder = MediaCodec.createEncoderByType(MIME_TYPE);;
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            encodingSurface = encoder.createInputSurface();
            encoder.start();

            encoder.stop();
            decoder.stop();
        } catch (Exception e) {
            e.printStackTrace();
            Log.i(TAG, "This H/W does not support w:" + w + " h:" + h);
            return false;
        } finally {
            if (encodingSurface != null) {
                encodingSurface.release();
            }
            if (encoder != null) {
                encoder.release();
            }
            if (decoder != null) {
                decoder.release();
            }
            if (decodingSurface != null) {
                decodingSurface.release();
            }
        }
        return true;
    }

    private static void runOnMain(Runnable runner) {
        sHandlerForRunOnMain.post(runner);
    }

    private static void runOnMainSync(Runnable runner) {
        SyncRunnable sr = new SyncRunnable(runner);
        sHandlerForRunOnMain.post(sr);
        sr.waitForComplete();
    }

    private static final class SyncRunnable implements Runnable {
        private final Runnable mTarget;
        private boolean mComplete;

        public SyncRunnable(Runnable target) {
            mTarget = target;
        }

        public void run() {
            mTarget.run();
            synchronized (this) {
                mComplete = true;
                notifyAll();
            }
        }

        public void waitForComplete() {
            synchronized (this) {
                while (!mComplete) {
                    try {
                        wait();
                    } catch (InterruptedException e) {
                        //ignore
                    }
                }
            }
        }
    }
}
