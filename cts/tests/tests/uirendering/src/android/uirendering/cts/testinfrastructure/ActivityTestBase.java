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
package android.uirendering.cts.testinfrastructure;

import com.android.compatibility.common.util.SynchronousPixelCopy;

import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Point;
import android.graphics.Rect;
import android.support.annotation.Nullable;
import android.support.test.InstrumentationRegistry;
import android.uirendering.cts.bitmapcomparers.BitmapComparer;
import android.uirendering.cts.bitmapverifiers.BitmapVerifier;
import android.uirendering.cts.util.BitmapAsserter;
import android.util.Log;
import android.view.PixelCopy;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.rules.TestName;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * This class contains the basis for the graphics hardware test classes. Contained within this class
 * are several methods that help with the execution of tests, and should be extended to gain the
 * functionality built in.
 */
public abstract class ActivityTestBase {
    public static final String TAG = "ActivityTestBase";
    public static final boolean DEBUG = false;

    //The minimum height and width of a device
    public static final int TEST_WIDTH = 90;
    public static final int TEST_HEIGHT = 90;

    private TestCaseBuilder mTestCaseBuilder;
    private Screenshotter mScreenshotter;

    private static DrawActivity sActivity;

    @Rule
    public TestName name = new TestName();

    private BitmapAsserter mBitmapAsserter = new BitmapAsserter(this.getClass().getSimpleName(),
            name.getMethodName());

    protected String getName() {
        return name.getMethodName();
    }

    protected Instrumentation getInstrumentation() {
        return InstrumentationRegistry.getInstrumentation();
    }

    protected DrawActivity getActivity() {
        if (sActivity == null) {
            Instrumentation instrumentation = getInstrumentation();
            instrumentation.setInTouchMode(true);
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.setClass(instrumentation.getTargetContext(), DrawActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtra(DrawActivity.EXTRA_WIDE_COLOR_GAMUT, isWideColorGamut());
            sActivity = (DrawActivity) instrumentation.startActivitySync(intent);
        }
        return sActivity;
    }

    protected boolean isWideColorGamut() {
        return false;
    }

    @AfterClass
    public static void tearDownClass() {
        if (sActivity != null) {
            // All tests are finished, tear down the activity
            sActivity.allTestsFinished();
            sActivity = null;
        }
    }

    @Before
    public void setUp() {
        mBitmapAsserter.setUp(getActivity());
    }

    @After
    public void tearDown() {
        if (mTestCaseBuilder != null) {
            List<TestCase> testCases = mTestCaseBuilder.getTestCases();

            if (testCases.size() == 0) {
                throw new IllegalStateException("Must have at least one test case");
            }

            for (TestCase testCase : testCases) {
                if (!testCase.wasTestRan) {
                    Log.w(TAG, getName() + " not all of the tests ran");
                    break;
                }
            }
            mTestCaseBuilder = null;
        }
    }

    public Bitmap takeScreenshot(Point testOffset) {
        if (mScreenshotter == null) {
            SynchronousPixelCopy copy = new SynchronousPixelCopy();
            Bitmap dest = Bitmap.createBitmap(
                    TEST_WIDTH, TEST_HEIGHT,
                    getActivity().getWindow().isWideColorGamut()
                            ? Config.RGBA_F16 : Config.ARGB_8888);
            Rect srcRect = new Rect(testOffset.x, testOffset.y,
                    testOffset.x + TEST_WIDTH, testOffset.y + TEST_HEIGHT);
            Log.d("UiRendering", "capturing screenshot of " + srcRect.toShortString());
            int copyResult = copy.request(getActivity().getWindow(), srcRect, dest);
            Assert.assertEquals(PixelCopy.SUCCESS, copyResult);
            return dest;
        } else {
            return mScreenshotter.takeScreenshot(testOffset);
        }
    }

    protected Point runRenderSpec(TestCase testCase) {
        Point testOffset = getActivity().enqueueRenderSpecAndWait(
                testCase.layoutID, testCase.canvasClient,
                testCase.viewInitializer, testCase.useHardware, testCase.usePicture);
        testCase.wasTestRan = true;
        if (testCase.readyFence != null) {
            try {
                testCase.readyFence.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                throw new RuntimeException("readyFence didn't signal within 5 seconds");
            }
        }
        return testOffset;
    }

    /**
     * Used to execute a specific part of a test and get the resultant bitmap
     */
    protected Bitmap captureRenderSpec(TestCase testCase) {
        Point testOffset = runRenderSpec(testCase);
        return takeScreenshot(testOffset);
    }

    protected TestCaseBuilder createTest() {
        mTestCaseBuilder = new TestCaseBuilder();
        mScreenshotter = null;
        return mTestCaseBuilder;
    }

    public interface Screenshotter {
        Bitmap takeScreenshot(Point point);
    }

    /**
     * Defines a group of CanvasClients, XML layouts, and WebView html files for testing.
     */
    protected class TestCaseBuilder {
        private List<TestCase> mTestCases;

        private TestCaseBuilder() {
            mTestCases = new ArrayList<>();
        }

        /**
         * Runs a test where the first test case is considered the "ideal" image and from there,
         * every test case is tested against it.
         */
        public void runWithComparer(BitmapComparer bitmapComparer) {
            if (mTestCases.size() == 0) {
                throw new IllegalStateException("Need at least one test to run");
            }

            Bitmap idealBitmap = captureRenderSpec(mTestCases.remove(0));

            for (TestCase testCase : mTestCases) {
                Bitmap testCaseBitmap = captureRenderSpec(testCase);
                mBitmapAsserter.assertBitmapsAreSimilar(idealBitmap, testCaseBitmap, bitmapComparer,
                        getName(), testCase.getDebugString());
            }
        }

        /**
         * Runs a test where each testcase is independent of the others and each is checked against
         * the verifier given.
         */
        public void runWithVerifier(BitmapVerifier bitmapVerifier) {
            if (mTestCases.size() == 0) {
                throw new IllegalStateException("Need at least one test to run");
            }

            for (TestCase testCase : mTestCases) {
                Bitmap testCaseBitmap = captureRenderSpec(testCase);
                mBitmapAsserter.assertBitmapIsVerified(testCaseBitmap, bitmapVerifier,
                        getName(), testCase.getDebugString());
            }
            getActivity().reset();
        }

        private static final int VERIFY_ANIMATION_LOOP_COUNT = 20;
        private static final int VERIFY_ANIMATION_SLEEP_MS = 100;

        /**
         * Runs a test where each testcase is independent of the others and each is checked against
         * the verifier given in a loop.
         *
         * A screenshot is captured several times in a loop, to ensure that valid output is produced
         * at many different times during the animation.
         */
        public void runWithAnimationVerifier(BitmapVerifier bitmapVerifier) {
            if (mTestCases.size() == 0) {
                throw new IllegalStateException("Need at least one test to run");
            }

            for (TestCase testCase : mTestCases) {
                Point testOffset = runRenderSpec(testCase);

                for (int i = 0; i < VERIFY_ANIMATION_LOOP_COUNT; i++) {
                    try {
                        Thread.sleep(VERIFY_ANIMATION_SLEEP_MS);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    Bitmap testCaseBitmap = takeScreenshot(testOffset);
                    mBitmapAsserter.assertBitmapIsVerified(testCaseBitmap, bitmapVerifier,
                            getName(), testCase.getDebugString());
                }
            }
        }

        /**
         * Runs a test where each testcase is run without verification. Should only be used
         * where custom CanvasClients, Views, or ViewInitializers do their own internal
         * test assertions.
         */
        public void runWithoutVerification() {
            runWithVerifier(new BitmapVerifier() {
                @Override
                public boolean verify(int[] bitmap, int offset, int stride, int width, int height) {
                    return true;
                }
            });
        }

        public TestCaseBuilder withScreenshotter(Screenshotter screenshotter) {
            Assert.assertNull("Screenshotter is already set!", mScreenshotter);
            mScreenshotter = screenshotter;
            return this;
        }

        public TestCaseBuilder addLayout(int layoutId, @Nullable ViewInitializer viewInitializer) {
            return addLayout(layoutId, viewInitializer, false)
                    .addLayout(layoutId, viewInitializer, true);
        }

        public TestCaseBuilder addLayout(int layoutId, @Nullable ViewInitializer viewInitializer,
                                         boolean useHardware) {
            mTestCases.add(new TestCase(layoutId, viewInitializer, useHardware));
            return this;
        }

        public TestCaseBuilder addLayout(int layoutId, @Nullable ViewInitializer viewInitializer,
                boolean useHardware, CountDownLatch readyFence) {
            TestCase test = new TestCase(layoutId, viewInitializer, useHardware);
            test.readyFence = readyFence;
            mTestCases.add(test);
            return this;
        }

        public TestCaseBuilder addCanvasClient(CanvasClient canvasClient) {
            return addCanvasClient(null, canvasClient);
        }

        public TestCaseBuilder addCanvasClient(CanvasClient canvasClient, boolean useHardware) {
            return addCanvasClient(null, canvasClient, useHardware);
        }

        public TestCaseBuilder addCanvasClient(String debugString, CanvasClient canvasClient) {
            return addCanvasClient(debugString, canvasClient, false)
                    .addCanvasClient(debugString, canvasClient, true);
        }

        public TestCaseBuilder addCanvasClient(String debugString,
                    CanvasClient canvasClient, boolean useHardware) {
            return addCanvasClientInternal(debugString, canvasClient, useHardware, false)
                    .addCanvasClientInternal(debugString, canvasClient, useHardware, true);
        }

        public TestCaseBuilder addCanvasClientWithoutUsingPicture(CanvasClient canvasClient) {
            return addCanvasClientWithoutUsingPicture(null, canvasClient);
        }

        public TestCaseBuilder addCanvasClientWithoutUsingPicture(String debugString,
                CanvasClient canvasClient) {
            return addCanvasClientInternal(debugString, canvasClient, false, false)
                    .addCanvasClientInternal(debugString, canvasClient, true, false);
        }

        public TestCaseBuilder addCanvasClientWithoutUsingPicture(CanvasClient canvasClient,
                boolean useHardware) {
            return addCanvasClientInternal(null, canvasClient, useHardware, false);
        }

        private TestCaseBuilder addCanvasClientInternal(String debugString,
                CanvasClient canvasClient, boolean useHardware, boolean usePicture) {
            mTestCases.add(new TestCase(canvasClient, debugString, useHardware, usePicture));
            return this;
        }

        private List<TestCase> getTestCases() {
            return mTestCases;
        }
    }

    private class TestCase {
        public int layoutID;
        public ViewInitializer viewInitializer;
        /** After launching the test case this fence is used to signal when
         * to proceed with capture & verification. If this is null the test
         * proceeds immediately to verification */
        @Nullable
        public CountDownLatch readyFence;

        public CanvasClient canvasClient;
        public String canvasClientDebugString;

        public boolean useHardware;
        public boolean usePicture = false;
        public boolean wasTestRan = false;

        public TestCase(int layoutId, ViewInitializer viewInitializer, boolean useHardware) {
            this.layoutID = layoutId;
            this.viewInitializer = viewInitializer;
            this.useHardware = useHardware;
        }

        public TestCase(CanvasClient client, String debugString, boolean useHardware,
                boolean usePicture) {
            this.canvasClient = client;
            this.canvasClientDebugString = debugString;
            this.useHardware = useHardware;
            this.usePicture = usePicture;
        }

        public String getDebugString() {
            String debug = "";
            if (canvasClient != null) {
                debug += "CanvasClient : ";
                if (canvasClientDebugString != null) {
                    debug += canvasClientDebugString;
                } else {
                    debug += "no debug string given";
                }
            } else {
                debug += "Layout resource : " +
                        getActivity().getResources().getResourceName(layoutID);
            }
            debug += "\nTest ran in " + (useHardware ? "hardware" : "software") +
                    (usePicture ? " with picture" : " without picture") + "\n";
            return debug;
        }
    }
}
