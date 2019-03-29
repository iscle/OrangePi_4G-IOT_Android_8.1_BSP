/*
 * Copyright (C) 2014 The Android Open Source Project
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

package android.opengl.cts;

import android.graphics.Bitmap;
import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLExt;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.opengl.GLES30;
import android.test.AndroidTestCase;
import android.util.Log;
import android.view.Surface;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * Test some GLES framebuffer stuff.
 */
public class FramebufferTest extends AndroidTestCase {
    private static final String TAG = "FramebufferTest";


    /**
     * Tests very basic glBlitFramebuffer() features by copying from one offscreen framebuffer
     * to another.
     * <p>
     * Requires GLES3.
     */
    public void testBlitFramebuffer() throws Throwable {
        final int WIDTH = 640;
        final int HEIGHT = 480;
        final int BYTES_PER_PIXEL = 4;
        final int TEST_RED = 255;
        final int TEST_GREEN = 0;
        final int TEST_BLUE = 127;
        final int TEST_ALPHA = 255;
        final byte expectedBytes[] = new byte[] {
                (byte) TEST_RED, (byte) TEST_GREEN, (byte) TEST_BLUE, (byte) TEST_ALPHA
        };
        EglCore eglCore = null;
        OffscreenSurface surface1 = null;
        OffscreenSurface surface2 = null;

        try {
            eglCore = new EglCore(null, EglCore.FLAG_TRY_GLES3);
            if (eglCore.getGlVersion() < 3) {
                Log.d(TAG, "GLES3 not available, skipping test");
                return;
            }

            // Create two surfaces, and clear surface1
            surface1 = new OffscreenSurface(eglCore, WIDTH, HEIGHT);
            surface2 = new OffscreenSurface(eglCore, WIDTH, HEIGHT);
            surface1.makeCurrent();
            GLES30.glClearColor(TEST_RED / 255.0f, TEST_GREEN / 255.0f, TEST_BLUE / 255.0f,
                    TEST_ALPHA / 255.0f);
            GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT);
            checkGlError("glClear");

            // Set surface2 as "draw", surface1 as "read", and blit.
            surface2.makeCurrentReadFrom(surface1);
            GLES30.glBlitFramebuffer(0, 0, WIDTH, HEIGHT, 0, 0, WIDTH, HEIGHT,
                    GLES30.GL_COLOR_BUFFER_BIT, GLES30.GL_NEAREST);
            checkGlError("glBlitFramebuffer");

            ByteBuffer pixelBuf = ByteBuffer.allocateDirect(WIDTH * HEIGHT * BYTES_PER_PIXEL);
            pixelBuf.order(ByteOrder.LITTLE_ENDIAN);
            byte testBytes[] = new byte[4];

            // Confirm that surface1 has the color by testing a pixel from the center.
            surface1.makeCurrent();
            pixelBuf.clear();
            GLES30.glReadPixels(0, 0, WIDTH, HEIGHT, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE,
                    pixelBuf);
            checkGlError("glReadPixels");
            pixelBuf.position((WIDTH * (HEIGHT / 2) + (WIDTH / 2)) * BYTES_PER_PIXEL);
            pixelBuf.get(testBytes, 0, 4);
            Log.v(TAG, "testBytes1 = " + Arrays.toString(testBytes));
            assertTrue(Arrays.equals(testBytes, expectedBytes));

            // Confirm that surface2 has the color.
            surface2.makeCurrent();
            pixelBuf.clear();
            GLES30.glReadPixels(0, 0, WIDTH, HEIGHT, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE,
                    pixelBuf);
            checkGlError("glReadPixels");
            pixelBuf.position((WIDTH * (HEIGHT / 2) + (WIDTH / 2)) * BYTES_PER_PIXEL);
            pixelBuf.get(testBytes, 0, 4);
            Log.v(TAG, "testBytes2 = " + Arrays.toString(testBytes));
            assertTrue(Arrays.equals(testBytes, expectedBytes));
        } finally {
            if (surface1 != null) {
                surface1.release();
            }
            if (surface2 != null) {
                surface2.release();
            }
            if (eglCore != null) {
                eglCore.release();
            }
        }
    }

    /**
     * Checks to see if a GLES error has been raised.
     */
    private static void checkGlError(String op) {
        int error = GLES20.glGetError();
        if (error != GLES20.GL_NO_ERROR) {
            String msg = op + ": glError 0x" + Integer.toHexString(error);
            Log.e(TAG, msg);
            throw new RuntimeException(msg);
        }
    }


    /**
     * Core EGL state (display, context, config).
     */
    private static final class EglCore {
        /**
         * Constructor flag: surface must be recordable.  This discourages EGL from using a
         * pixel format that cannot be converted efficiently to something usable by the video
         * encoder.
         */
        public static final int FLAG_RECORDABLE = 0x01;

        /**
         * Constructor flag: ask for GLES3, fall back to GLES2 if not available.  Without this
         * flag, GLES2 is used.
         */
        public static final int FLAG_TRY_GLES3 = 0x02;

        private EGLDisplay mEGLDisplay = EGL14.EGL_NO_DISPLAY;
        private EGLContext mEGLContext = EGL14.EGL_NO_CONTEXT;
        private EGLConfig mEGLConfig = null;
        private int mGlVersion = -1;


        /**
         * Prepares EGL display and context.
         * <p>
         * Equivalent to EglCore(null, 0).
         */
        public EglCore() {
            this(null, 0);
        }

        /**
         * Prepares EGL display and context.
         * <p>
         * @param sharedContext The context to share, or null if sharing is not desired.
         * @param flags Configuration bit flags, e.g. FLAG_RECORDABLE.
         */
        public EglCore(EGLContext sharedContext, int flags) {
            if (mEGLDisplay != EGL14.EGL_NO_DISPLAY) {
                throw new RuntimeException("EGL already set up");
            }

            if (sharedContext == null) {
                sharedContext = EGL14.EGL_NO_CONTEXT;
            }

            mEGLDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
            if (mEGLDisplay == EGL14.EGL_NO_DISPLAY) {
                throw new RuntimeException("unable to get EGL14 display");
            }
            int[] version = new int[2];
            if (!EGL14.eglInitialize(mEGLDisplay, version, 0, version, 1)) {
                mEGLDisplay = null;
                throw new RuntimeException("unable to initialize EGL14");
            }

            // Try to get a GLES3 context, if requested.
            if ((flags & FLAG_TRY_GLES3) != 0) {
                //Log.d(TAG, "Trying GLES 3");
                EGLConfig config = getConfig(flags, 3);
                if (config != null) {
                    int[] attrib3_list = {
                            EGL14.EGL_CONTEXT_CLIENT_VERSION, 3,
                            EGL14.EGL_NONE
                    };
                    EGLContext context = EGL14.eglCreateContext(mEGLDisplay, config, sharedContext,
                            attrib3_list, 0);

                    if (EGL14.eglGetError() == EGL14.EGL_SUCCESS) {
                        //Log.d(TAG, "Got GLES 3 config");
                        mEGLConfig = config;
                        mEGLContext = context;
                        mGlVersion = 3;
                    }
                }
            }
            if (mEGLContext == EGL14.EGL_NO_CONTEXT) {  // GLES 2 only, or GLES 3 attempt failed
                //Log.d(TAG, "Trying GLES 2");
                EGLConfig config = getConfig(flags, 2);
                if (config == null) {
                    throw new RuntimeException("Unable to find a suitable EGLConfig");
                }
                int[] attrib2_list = {
                        EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                        EGL14.EGL_NONE
                };
                EGLContext context = EGL14.eglCreateContext(mEGLDisplay, config, sharedContext,
                        attrib2_list, 0);
                checkEglError("eglCreateContext");
                mEGLConfig = config;
                mEGLContext = context;
                mGlVersion = 2;
            }

            // Confirm with query.
            int[] values = new int[1];
            EGL14.eglQueryContext(mEGLDisplay, mEGLContext, EGL14.EGL_CONTEXT_CLIENT_VERSION,
                    values, 0);
            Log.d(TAG, "EGLContext created, client version " + values[0]);
        }

        /**
         * Finds a suitable EGLConfig.
         *
         * @param flags Bit flags from constructor.
         * @param version Must be 2 or 3.
         */
        private EGLConfig getConfig(int flags, int version) {
            int renderableType = EGL14.EGL_OPENGL_ES2_BIT;
            if (version >= 3) {
                renderableType |= EGLExt.EGL_OPENGL_ES3_BIT_KHR;
            }

            // The actual surface is generally RGBA or RGBX, so situationally omitting alpha
            // doesn't really help.  It can also lead to a huge performance hit on glReadPixels()
            // when reading into a GL_RGBA buffer.
            int[] attribList = {
                    EGL14.EGL_RED_SIZE, 8,
                    EGL14.EGL_GREEN_SIZE, 8,
                    EGL14.EGL_BLUE_SIZE, 8,
                    EGL14.EGL_ALPHA_SIZE, 8,
                    //EGL14.EGL_DEPTH_SIZE, 16,
                    //EGL14.EGL_STENCIL_SIZE, 8,
                    EGL14.EGL_RENDERABLE_TYPE, renderableType,
                    EGL14.EGL_NONE, 0,      // placeholder for recordable [@-3]
                    EGL14.EGL_NONE
            };
            if ((flags & FLAG_RECORDABLE) != 0) {
                attribList[attribList.length - 3] = EGLExt.EGL_RECORDABLE_ANDROID;
                attribList[attribList.length - 2] = 1;
            }
            EGLConfig[] configs = new EGLConfig[1];
            int[] numConfigs = new int[1];
            if (!EGL14.eglChooseConfig(mEGLDisplay, attribList, 0, configs, 0, configs.length,
                    numConfigs, 0)) {
                Log.w(TAG, "unable to find RGB8888 / " + version + " EGLConfig");
                return null;
            }
            return configs[0];
        }

        /**
         * Discard all resources held by this class, notably the EGL context.
         */
        public void release() {
            if (mEGLDisplay != EGL14.EGL_NO_DISPLAY) {
                // Android is unusual in that it uses a reference-counted EGLDisplay.  So for
                // every eglInitialize() we need an eglTerminate().
                EGL14.eglDestroyContext(mEGLDisplay, mEGLContext);
                EGL14.eglReleaseThread();
                EGL14.eglTerminate(mEGLDisplay);
            }

            mEGLDisplay = EGL14.EGL_NO_DISPLAY;
            mEGLContext = EGL14.EGL_NO_CONTEXT;
            mEGLConfig = null;
        }

        @Override
        protected void finalize() throws Throwable {
            try {
                if (mEGLDisplay != EGL14.EGL_NO_DISPLAY) {
                    // We're limited here -- finalizers don't run on the thread that holds
                    // the EGL state, so if a surface or context is still current on another
                    // thread we can't fully release it here.  Exceptions thrown from here
                    // are quietly discarded.  Complain in the log file.
                    Log.w(TAG, "WARNING: EglCore was not explicitly released -- state may be leaked");
                    release();
                }
            } finally {
                super.finalize();
            }
        }

        /**
         * Destroys the specified surface.  Note the EGLSurface won't actually be destroyed if it's
         * still current in a context.
         */
        public void releaseSurface(EGLSurface eglSurface) {
            EGL14.eglDestroySurface(mEGLDisplay, eglSurface);
        }

        /**
         * Creates an EGL surface associated with a Surface.
         * <p>
         * If this is destined for MediaCodec, the EGLConfig should have the "recordable" attribute.
         */
        public EGLSurface createWindowSurface(Object surface) {
            if (!(surface instanceof Surface) && !(surface instanceof SurfaceTexture)) {
                throw new RuntimeException("invalid surface: " + surface);
            }

            // Create a window surface, and attach it to the Surface we received.
            int[] surfaceAttribs = {
                    EGL14.EGL_NONE
            };
            EGLSurface eglSurface = EGL14.eglCreateWindowSurface(mEGLDisplay, mEGLConfig, surface,
                    surfaceAttribs, 0);
            checkEglError("eglCreateWindowSurface");
            if (eglSurface == null) {
                throw new RuntimeException("surface was null");
            }
            return eglSurface;
        }

        /**
         * Creates an EGL surface associated with an offscreen buffer.
         */
        public EGLSurface createOffscreenSurface(int width, int height) {
            int[] surfaceAttribs = {
                    EGL14.EGL_WIDTH, width,
                    EGL14.EGL_HEIGHT, height,
                    EGL14.EGL_NONE
            };
            EGLSurface eglSurface = EGL14.eglCreatePbufferSurface(mEGLDisplay, mEGLConfig,
                    surfaceAttribs, 0);
            checkEglError("eglCreatePbufferSurface");
            if (eglSurface == null) {
                throw new RuntimeException("surface was null");
            }
            return eglSurface;
        }

        /**
         * Makes our EGL context current, using the supplied surface for both "draw" and "read".
         */
        public void makeCurrent(EGLSurface eglSurface) {
            if (mEGLDisplay == EGL14.EGL_NO_DISPLAY) {
                // called makeCurrent() before create?
                Log.d(TAG, "NOTE: makeCurrent w/o display");
            }
            if (!EGL14.eglMakeCurrent(mEGLDisplay, eglSurface, eglSurface, mEGLContext)) {
                throw new RuntimeException("eglMakeCurrent failed");
            }
        }

        /**
         * Makes our EGL context current, using the supplied "draw" and "read" surfaces.
         */
        public void makeCurrent(EGLSurface drawSurface, EGLSurface readSurface) {
            if (mEGLDisplay == EGL14.EGL_NO_DISPLAY) {
                // called makeCurrent() before create?
                Log.d(TAG, "NOTE: makeCurrent w/o display");
            }
            if (!EGL14.eglMakeCurrent(mEGLDisplay, drawSurface, readSurface, mEGLContext)) {
                throw new RuntimeException("eglMakeCurrent(draw,read) failed");
            }
        }

        /**
         * Makes no context current.
         */
        public void makeNothingCurrent() {
            if (!EGL14.eglMakeCurrent(mEGLDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE,
                    EGL14.EGL_NO_CONTEXT)) {
                throw new RuntimeException("eglMakeCurrent failed");
            }
        }

        /**
         * Calls eglSwapBuffers.  Use this to "publish" the current frame.
         *
         * @return false on failure
         */
        public boolean swapBuffers(EGLSurface eglSurface) {
            return EGL14.eglSwapBuffers(mEGLDisplay, eglSurface);
        }

        /**
         * Sends the presentation time stamp to EGL.  Time is expressed in nanoseconds.
         */
        public void setPresentationTime(EGLSurface eglSurface, long nsecs) {
            EGLExt.eglPresentationTimeANDROID(mEGLDisplay, eglSurface, nsecs);
        }

        /**
         * Returns true if our context and the specified surface are current.
         */
        public boolean isCurrent(EGLSurface eglSurface) {
            return mEGLContext.equals(EGL14.eglGetCurrentContext()) &&
                    eglSurface.equals(EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW));
        }

        /**
         * Performs a simple surface query.
         */
        public int querySurface(EGLSurface eglSurface, int what) {
            int[] value = new int[1];
            EGL14.eglQuerySurface(mEGLDisplay, eglSurface, what, value, 0);
            return value[0];
        }

        /**
         * Returns the GLES version this context is configured for (2 or 3).
         */
        public int getGlVersion() {
            return mGlVersion;
        }

        /**
         * Writes the current display, context, and surface to the log.
         */
        public static void logCurrent(String msg) {
            EGLDisplay display;
            EGLContext context;
            EGLSurface surface;

            display = EGL14.eglGetCurrentDisplay();
            context = EGL14.eglGetCurrentContext();
            surface = EGL14.eglGetCurrentSurface(EGL14.EGL_DRAW);
            Log.i(TAG, "Current EGL (" + msg + "): display=" + display + ", context=" + context +
                    ", surface=" + surface);
        }

        /**
         * Checks for EGL errors.
         */
        private void checkEglError(String msg) {
            int error;
            if ((error = EGL14.eglGetError()) != EGL14.EGL_SUCCESS) {
                throw new RuntimeException(msg + ": EGL error: 0x" + Integer.toHexString(error));
            }
        }
    }


    /**
     * Common base class for EGL surfaces.
     * <p>
     * There can be multiple surfaces associated with a single context.
     */
    private static class EglSurfaceBase {
        // EglCore object we're associated with.  It may be associated with multiple surfaces.
        protected EglCore mEglCore;

        private EGLSurface mEGLSurface = EGL14.EGL_NO_SURFACE;
        private int mWidth = -1;
        private int mHeight = -1;

        protected EglSurfaceBase(EglCore eglCore) {
            mEglCore = eglCore;
        }

        /**
         * Creates a window surface.
         * <p>
         * @param surface May be a Surface or SurfaceTexture.
         */
        public void createWindowSurface(Object surface) {
            if (mEGLSurface != EGL14.EGL_NO_SURFACE) {
                throw new IllegalStateException("surface already created");
            }
            mEGLSurface = mEglCore.createWindowSurface(surface);
            mWidth = mEglCore.querySurface(mEGLSurface, EGL14.EGL_WIDTH);
            mHeight = mEglCore.querySurface(mEGLSurface, EGL14.EGL_HEIGHT);
        }

        /**
         * Creates an off-screen surface.
         */
        public void createOffscreenSurface(int width, int height) {
            if (mEGLSurface != EGL14.EGL_NO_SURFACE) {
                throw new IllegalStateException("surface already created");
            }
            mEGLSurface = mEglCore.createOffscreenSurface(width, height);
            mWidth = width;
            mHeight = height;
        }

        /**
         * Returns the surface's width, in pixels.
         */
        public int getWidth() {
            return mWidth;
        }

        /**
         * Returns the surface's height, in pixels.
         */
        public int getHeight() {
            return mHeight;
        }

        /**
         * Release the EGL surface.
         */
        public void releaseEglSurface() {
            mEglCore.releaseSurface(mEGLSurface);
            mEGLSurface = EGL14.EGL_NO_SURFACE;
            mWidth = mHeight = -1;
        }

        /**
         * Makes our EGL context and surface current.
         */
        public void makeCurrent() {
            mEglCore.makeCurrent(mEGLSurface);
        }

        /**
         * Makes our EGL context and surface current for drawing, using the supplied surface
         * for reading.
         */
        public void makeCurrentReadFrom(EglSurfaceBase readSurface) {
            mEglCore.makeCurrent(mEGLSurface, readSurface.mEGLSurface);
        }

        /**
         * Calls eglSwapBuffers.  Use this to "publish" the current frame.
         *
         * @return false on failure
         */
        public boolean swapBuffers() {
            boolean result = mEglCore.swapBuffers(mEGLSurface);
            if (!result) {
                Log.d(TAG, "WARNING: swapBuffers() failed");
            }
            return result;
        }

        /**
         * Sends the presentation time stamp to EGL.
         *
         * @param nsecs Timestamp, in nanoseconds.
         */
        public void setPresentationTime(long nsecs) {
            mEglCore.setPresentationTime(mEGLSurface, nsecs);
        }

        /**
         * Saves the EGL surface to a file.
         * <p>
         * Expects that this object's EGL surface is current.
         */
        public void saveFrame(File file) throws IOException {
            if (!mEglCore.isCurrent(mEGLSurface)) {
                throw new RuntimeException("Expected EGL context/surface is not current");
            }

            // glReadPixels gives us a ByteBuffer filled with what is essentially big-endian RGBA
            // data (i.e. a byte of red, followed by a byte of green...).  We need an int[] filled
            // with little-endian ARGB data to feed to Bitmap.
            //
            // If we implement this as a series of buf.get() calls, we can spend 2.5 seconds just
            // copying data around for a 720p frame.  It's better to do a bulk get() and then
            // rearrange the data in memory.  (For comparison, the PNG compress takes about 500ms
            // for a trivial frame.)
            //
            // So... we set the ByteBuffer to little-endian, which should turn the bulk IntBuffer
            // get() into a straight memcpy on most Android devices.  Our ints will hold ABGR data.
            // Swapping B and R gives us ARGB.
            //
            // Making this even more interesting is the upside-down nature of GL, which means
            // our output will look upside-down relative to what appears on screen if the
            // typical GL conventions are used.

            String filename = file.toString();

            ByteBuffer buf = ByteBuffer.allocateDirect(mWidth * mHeight * 4);
            buf.order(ByteOrder.LITTLE_ENDIAN);
            GLES20.glReadPixels(0, 0, mWidth, mHeight,
                    GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf);
            checkGlError("glReadPixels");
            buf.rewind();

            int pixelCount = mWidth * mHeight;
            int[] colors = new int[pixelCount];
            buf.asIntBuffer().get(colors);
            for (int i = 0; i < pixelCount; i++) {
                int c = colors[i];
                colors[i] = (c & 0xff00ff00) | ((c & 0x00ff0000) >> 16) | ((c & 0x000000ff) << 16);
            }

            BufferedOutputStream bos = null;
            try {
                bos = new BufferedOutputStream(new FileOutputStream(filename));
                Bitmap bmp = Bitmap.createBitmap(colors, mWidth, mHeight, Bitmap.Config.ARGB_8888);
                bmp.compress(Bitmap.CompressFormat.PNG, 90, bos);
                bmp.recycle();
            } finally {
                if (bos != null) bos.close();
            }
            Log.d(TAG, "Saved " + mWidth + "x" + mHeight + " frame as '" + filename + "'");
        }
    }

    /**
     * Off-screen EGL surface (pbuffer).
     * <p>
     * It's good practice to explicitly release() the surface, preferably from a "finally" block.
     */
    private static class OffscreenSurface extends EglSurfaceBase {
        /**
         * Creates an off-screen surface with the specified width and height.
         */
        public OffscreenSurface(EglCore eglCore, int width, int height) {
            super(eglCore);
            createOffscreenSurface(width, height);
        }

        /**
         * Releases any resources associated with the surface.
         */
        public void release() {
            releaseEglSurface();
        }
    }
}
