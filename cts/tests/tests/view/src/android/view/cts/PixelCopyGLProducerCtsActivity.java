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

package android.view.cts;

import static android.opengl.GLES20.GL_COLOR_BUFFER_BIT;
import static android.opengl.GLES20.GL_SCISSOR_TEST;
import static android.opengl.GLES20.glClear;
import static android.opengl.GLES20.glClearColor;
import static android.opengl.GLES20.glEnable;
import static android.opengl.GLES20.glScissor;

import android.graphics.Color;
import android.opengl.GLSurfaceView;

import java.util.concurrent.CountDownLatch;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

public class PixelCopyGLProducerCtsActivity extends GLSurfaceViewCtsActivity {
    private static class QuadColorGLRenderer implements GLSurfaceView.Renderer {

        private final int mTopLeftColor;
        private final int mTopRightColor;
        private final int mBottomLeftColor;
        private final int mBottomRightColor;

        private CountDownLatch mFence;

        private int mWidth, mHeight;

        public QuadColorGLRenderer(int topLeft, int topRight, int bottomLeft, int bottomRight) {
            mTopLeftColor = topLeft;
            mTopRightColor = topRight;
            mBottomLeftColor = bottomLeft;
            mBottomRightColor = bottomRight;
        }

        @Override
        public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        }

        @Override
        public void onSurfaceChanged(GL10 gl, int width, int height) {
            mWidth = width;
            mHeight = height;
        }

        @Override
        public void onDrawFrame(GL10 gl) {
            int cx = mWidth / 2;
            int cy = mHeight / 2;

            glEnable(GL_SCISSOR_TEST);

            glScissor(0, cy, cx, mHeight - cy);
            clearColor(mTopLeftColor);

            glScissor(cx, cy, mWidth - cx, mHeight - cy);
            clearColor(mTopRightColor);

            glScissor(0, 0, cx, cy);
            clearColor(mBottomLeftColor);

            glScissor(cx, 0, mWidth - cx, cy);
            clearColor(mBottomRightColor);

            if (mFence != null) {
                mFence.countDown();
            }
        }

        private void clearColor(int color) {
            glClearColor(Color.red(color) / 255.0f,
                    Color.green(color) / 255.0f,
                    Color.blue(color) / 255.0f,
                    Color.alpha(color) / 255.0f);
            glClear(GL_COLOR_BUFFER_BIT);
        }
    }

    private QuadColorGLRenderer mRenderer;

    @Override
    protected void configureGLSurfaceView() {
        mView.setEGLContextClientVersion(2);
        mRenderer = new QuadColorGLRenderer(
                Color.RED, Color.GREEN, Color.BLUE, Color.BLACK);
        mView.setRenderer(mRenderer);
        mView.setRenderMode(GLSurfaceView.RENDERMODE_WHEN_DIRTY);
        mView.getHolder().setFixedSize(100, 100);
    }

    public void setSwapFence(CountDownLatch swapFence) {
        mRenderer.mFence = swapFence;
    }
}
