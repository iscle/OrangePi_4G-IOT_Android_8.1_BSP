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
package android.view.cts;

import static android.opengl.GLES20.GL_COLOR_BUFFER_BIT;
import static android.opengl.GLES20.glClear;
import static android.opengl.GLES20.glClearColor;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.view.Display;
import android.view.TextureView;
import android.view.WindowManager;

import junit.framework.Assert;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

public class TextureViewStressTestActivity extends Activity
        implements TextureView.SurfaceTextureListener {
    public static int mFrames = -1;
    public static int mDelayMs = -1;

    private Thread mProducerThread;
    private final Semaphore mSemaphore = new Semaphore(0);

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Assert.assertTrue(mFrames > 0);
        Assert.assertTrue(mDelayMs > 0);
        TextureView texView = new TextureView(this);
        texView.setSurfaceTextureListener(this);
        setContentView(texView);
        ObjectAnimator rotate = ObjectAnimator.ofFloat(texView, "rotationY", 180);
        ObjectAnimator fadeIn = ObjectAnimator.ofFloat(texView, "alpha", 0.3f, 1f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(texView, "scaleY", 0.3f, 1f);
        AnimatorSet animSet = new AnimatorSet();
        animSet.play(rotate).with(fadeIn).with(scaleY);
        animSet.setDuration(mFrames * mDelayMs);
        animSet.start();
    }

    private static int addMargin(int a) {
        /* Worst case is 2 * actual refresh rate, in case when the delay pushes the frame off
         * VSYNC every frame.
         */
        return 2 * a;
    }

    private static int roundUpFrame(int a, int b) {
        /* Need to give time based on (frame duration limited by refresh rate + delay given
         * from the test)
         */
        return (a + b + 1);
    }


    public Boolean waitForCompletion() {
        Boolean success = false;
        int oneframeMs;

        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        float rate = display.getRefreshRate();
        oneframeMs = roundUpFrame(mDelayMs, (int) (1000.0f / rate));
        try {
            success = mSemaphore.tryAcquire(addMargin(oneframeMs * mFrames),
                    TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Assert.fail();
        }
        return success;
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        mProducerThread = new GLProducerThread(surface, new GLRendererImpl(),
                mFrames, mDelayMs, mSemaphore);
        mProducerThread.start();
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        mProducerThread = null;
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
    }

    public static class GLRendererImpl implements GLProducerThread.GLRenderer {
        private static final int NUM_COLORS = 4;
        private static final float[][] COLOR = {
                { 1.0f, 0.0f, 0.0f },
                { 0.0f, 1.0f, 0.0f },
                { 0.0f, 0.0f, 1.0f },
                { 1.0f, 1.0f, 1.0f }
        };

        @Override
        public void drawFrame(int frame) {
            int index = frame % NUM_COLORS;
            glClearColor(COLOR[index][0], COLOR[index][1], COLOR[index][2], 1.0f);
            glClear(GL_COLOR_BUFFER_BIT);
        }
    }
}
