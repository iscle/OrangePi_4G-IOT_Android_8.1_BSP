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

package android.uirendering.cts.testclasses;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.support.annotation.ColorInt;
import android.support.test.filters.MediumTest;
import android.support.test.runner.AndroidJUnit4;
import android.uirendering.cts.R;
import android.uirendering.cts.bitmapverifiers.ColorVerifier;
import android.uirendering.cts.testinfrastructure.ActivityTestBase;
import android.uirendering.cts.testinfrastructure.ViewInitializer;
import android.uirendering.cts.util.DrawCountDown;
import android.view.Surface;
import android.view.TextureView;
import android.view.TextureView.SurfaceTextureListener;
import android.view.ViewGroup;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class TextureViewTests extends ActivityTestBase {

    private static SurfaceTexture sRedTexture;

    @BeforeClass
    public static void setupClass() {
        sRedTexture = createSurfaceTexture(true, Color.RED);
    }

    @AfterClass
    public static void teardownClass() {
        sRedTexture.release();
        sRedTexture = null;
    }

    @After
    public void tearDown() {
        // TODO: Workaround for b/34231066
        DrawCountDown.cancelPending();
    }

    @Test
    public void testConstructDetachedSingleBuffered() {
        testConstructDetached(true);
    }
    @Test
    public void testConstructDetachedMultiBuffered() {
        testConstructDetached(false);
    }

    private void testConstructDetached(boolean singleBuffered) {
        final SurfaceTexture texture = createSurfaceTexture(singleBuffered, Color.RED);
        createTest()
                .addLayout(R.layout.textureview, (ViewInitializer) view -> {
                    TextureView textureview = (TextureView) view;
                    textureview.setSurfaceTexture(texture);
                }, true)
                .runWithVerifier(new ColorVerifier(Color.RED));
        Assert.assertTrue(texture.isReleased());
    }

    private static SurfaceTexture createSurfaceTexture(boolean singleBuffered,
            @ColorInt int fillColor) {
        SurfaceTexture texture = new SurfaceTexture(singleBuffered);
        texture.setDefaultBufferSize(TEST_WIDTH, TEST_HEIGHT);
        Surface producer = new Surface(texture);
        Canvas canvas = producer.lockCanvas(null);
        canvas.drawColor(fillColor);
        producer.unlockCanvasAndPost(canvas);
        return texture;
    }

    @Test
    public void testReuseSurfaceTexture() {
        final CountDownLatch fence = new CountDownLatch(1);
        SurfaceTextureListener stlistener = mock(SurfaceTextureListener.class);
        when(stlistener.onSurfaceTextureDestroyed(any(SurfaceTexture.class)))
                .thenReturn(false);
        createTest()
                .addLayout(R.layout.textureview, (ViewInitializer) view -> {
                    final TextureView textureview = (TextureView) view;
                    final ViewGroup parent = (ViewGroup) textureview.getParent();
                    textureview.setSurfaceTextureListener(stlistener);
                    textureview.setSurfaceTexture(sRedTexture);
                    DrawCountDown.countDownDraws(parent, 1, () -> {
                        parent.removeView(textureview);
                    });
                    DrawCountDown.countDownDraws(parent, 2, () -> {
                        parent.addView(textureview);
                        textureview.setSurfaceTexture(sRedTexture);
                        textureview.post(fence::countDown);
                    });
                }, true, fence)
                .runWithVerifier(new ColorVerifier(Color.RED));
        verify(stlistener, times(2)).onSurfaceTextureDestroyed(any(SurfaceTexture.class));
    }

    @Test
    public void testLockCanvas() {
        final CountDownLatch fence = new CountDownLatch(1);
        createTest()
                .addLayout(R.layout.textureview, (ViewInitializer) view -> {
                    final TextureView textureview = (TextureView) view;
                    textureview.setSurfaceTextureListener(new SurfaceTextureListener() {
                        @Override
                        public void onSurfaceTextureUpdated(SurfaceTexture surface) {
                            textureview.post(fence::countDown);
                        }
                        @Override
                        public void onSurfaceTextureSizeChanged(SurfaceTexture surface,
                                int width, int height) {}
                        @Override
                        public void onSurfaceTextureAvailable(SurfaceTexture surface,
                                int width, int height) {
                            Canvas canvas = textureview.lockCanvas();
                            canvas.drawColor(Color.BLUE);
                            textureview.unlockCanvasAndPost(canvas);
                        }
                        @Override
                        public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
                            return true;
                        }
                    });
                }, true, fence)
                .runWithVerifier(new ColorVerifier(Color.BLUE));
    }
}
