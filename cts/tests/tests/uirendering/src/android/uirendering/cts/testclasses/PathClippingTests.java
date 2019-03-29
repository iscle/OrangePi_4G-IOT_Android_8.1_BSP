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

package android.uirendering.cts.testclasses;

import static org.junit.Assert.assertNotNull;

import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.Typeface;
import android.support.test.filters.LargeTest;
import android.support.test.filters.MediumTest;
import android.support.test.runner.AndroidJUnit4;
import android.uirendering.cts.R;
import android.uirendering.cts.bitmapcomparers.MSSIMComparer;
import android.uirendering.cts.bitmapverifiers.GoldenImageVerifier;
import android.uirendering.cts.bitmapverifiers.SamplePointVerifier;
import android.uirendering.cts.testinfrastructure.ActivityTestBase;
import android.uirendering.cts.testinfrastructure.CanvasClient;
import android.uirendering.cts.testinfrastructure.CanvasClientDrawable;
import android.uirendering.cts.testinfrastructure.ViewInitializer;
import android.uirendering.cts.util.WebViewReadyHelper;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class PathClippingTests extends ActivityTestBase {
    // draw circle with hole in it, with stroked circle
    static final CanvasClient sTorusDrawCanvasClient = (canvas, width, height) -> {
        Paint paint = new Paint();
        paint.setAntiAlias(false);
        paint.setColor(Color.BLUE);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(20);
        canvas.drawCircle(30, 30, 40, paint);
    };

    // draw circle with hole in it, by path operations + path clipping
    static final CanvasClient sTorusClipCanvasClient = (canvas, width, height) -> {
        canvas.save();

        Path path = new Path();
        path.addCircle(30, 30, 50, Path.Direction.CW);
        path.addCircle(30, 30, 30, Path.Direction.CCW);

        canvas.clipPath(path);
        canvas.drawColor(Color.BLUE);

        canvas.restore();
    };

    // draw circle with hole in it, by path operations + path clipping
    static final CanvasClient sTorusClipOutCanvasClient = (canvas, width, height) -> {
        canvas.save();

        Path path1 = new Path();
        path1.addCircle(30, 30, 50, Path.Direction.CW);

        Path path2 = new Path();
        path2.addCircle(30, 30, 30, Path.Direction.CW);

        canvas.clipPath(path1);
        canvas.clipOutPath(path2);
        canvas.drawColor(Color.BLUE);

        canvas.restore();
    };

    @Test
    public void testCircleWithCircle() {
        createTest()
                .addCanvasClient("TorusDraw", sTorusDrawCanvasClient, false)
                .addCanvasClient("TorusClip", sTorusClipCanvasClient)
                .addCanvasClient("TorusClipOut", sTorusClipOutCanvasClient)
                .runWithVerifier(new GoldenImageVerifier(getActivity(),
                        R.drawable.pathclippingtest_torus, new MSSIMComparer(0.95)));
    }

    @Test
    public void testCircleWithPoints() {
        createTest()
                .addCanvasClient("TorusClip", sTorusClipCanvasClient)
                .addCanvasClient("TorusClipOut", sTorusClipOutCanvasClient)
                .runWithVerifier(new SamplePointVerifier(
                        new Point[] {
                                // inside of circle
                                new Point(30, 50),
                                // on circle
                                new Point(30 + 32, 30 + 32),
                                // outside of circle
                                new Point(30 + 38, 30 + 38),
                                new Point(80, 80)
                        },
                        new int[] {
                                Color.WHITE,
                                Color.BLUE,
                                Color.WHITE,
                                Color.WHITE,
                        }));
    }

    @Test
    public void testViewRotate() {
        createTest()
                .addLayout(R.layout.blue_padded_layout, view -> {
                    ViewGroup rootView = (ViewGroup) view;
                    rootView.setClipChildren(true);
                    View childView = rootView.getChildAt(0);
                    childView.setPivotX(40);
                    childView.setPivotY(40);
                    childView.setRotation(45f);
                })
                .runWithVerifier(new SamplePointVerifier(
                        new Point[] {
                                // inside of rotated rect
                                new Point(40, 40),
                                new Point(40 + 25, 40 + 25),
                                // outside of rotated rect
                                new Point(40 + 31, 40 + 31),
                                new Point(80, 80)
                        },
                        new int[] {
                                Color.BLUE,
                                Color.BLUE,
                                Color.WHITE,
                                Color.WHITE,
                        }));
    }

    @Test
    public void testPathScale() {
        createTest()
                .addLayout(R.layout.frame_layout, view -> {
                    Path path = new Path();
                    path.addCircle(TEST_WIDTH / 2, TEST_HEIGHT / 2,
                            TEST_WIDTH / 4, Path.Direction.CW);
                    view.setBackground(new CanvasClientDrawable((canvas, width, height) -> {
                        canvas.clipPath(path);
                        canvas.drawColor(Color.BLUE);
                    }));
                    view.setScaleX(2);
                    view.setScaleY(2);
                })
                .runWithComparer(new MSSIMComparer(0.90));
    }

    @Test
    public void testTextClip() {
        createTest()
                .addCanvasClient((canvas, width, height) -> {
                    canvas.save();

                    Path path = new Path();
                    path.addCircle(0, 45, 45, Path.Direction.CW);
                    path.addCircle(90, 45, 45, Path.Direction.CW);
                    canvas.clipPath(path);

                    Paint paint = new Paint();
                    paint.setAntiAlias(true);
                    paint.setTextSize(90);
                    paint.setTypeface(Typeface.defaultFromStyle(Typeface.BOLD));
                    canvas.drawText("STRING", 0, 90, paint);

                    canvas.restore();
                })
                .runWithComparer(new MSSIMComparer(0.90));
    }

    private ViewInitializer initBlueWebView(final CountDownLatch fence) {
        return view -> {
            WebView webview = (WebView)view.findViewById(R.id.webview);
            assertNotNull(webview);
            WebViewReadyHelper helper = new WebViewReadyHelper(webview, fence);
            helper.loadData("<body style=\"background-color:blue\">");
        };
    }

    @LargeTest
    @Test
    public void testWebViewClipWithCircle() {
        if (!getActivity().getPackageManager().hasSystemFeature(PackageManager.FEATURE_WEBVIEW)) {
            return; // no WebView to run test on
        }
        CountDownLatch hwFence = new CountDownLatch(1);
        CountDownLatch swFence = new CountDownLatch(1);
        createTest()
                // golden client - draw a simple non-AA circle
                .addCanvasClient((canvas, width, height) -> {
                    Paint paint = new Paint();
                    paint.setAntiAlias(false);
                    paint.setColor(Color.BLUE);
                    canvas.drawOval(0, 0, width, height, paint);
                }, false)
                // verify against solid color webview, clipped to its parent oval
                .addLayout(R.layout.circle_clipped_webview,
                        initBlueWebView(hwFence), true, hwFence)
                .addLayout(R.layout.circle_clipped_webview,
                        initBlueWebView(swFence), false, swFence)
                .runWithComparer(new MSSIMComparer(0.95));
    }
}
