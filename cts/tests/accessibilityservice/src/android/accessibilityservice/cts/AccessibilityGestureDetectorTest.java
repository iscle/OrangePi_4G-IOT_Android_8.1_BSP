/**
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.accessibilityservice.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.accessibilityservice.GestureDescription.StrokeDescription;
import android.app.Instrumentation;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Path;
import android.graphics.Point;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import java.util.ArrayList;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Verify that motion events are recognized as accessibility gestures.
 */
@RunWith(AndroidJUnit4.class)
public class AccessibilityGestureDetectorTest {

    // Constants
    static final float GESTURE_LENGTH_INCHES = 1.0f;
    static final long STROKE_MS = 400;
    static final long GESTURE_DISPATCH_TIMEOUT_MS = 3000;
    static final long GESTURE_RECOGNIZE_TIMEOUT_MS = 3000;

    // Member variables
    StubService mService;  // Test AccessibilityService that collects gestures.
    boolean mHasTouchScreen;
    boolean mScreenBigEnough;
    int mStrokeLenPxX;  // Gesture stroke size, in pixels
    int mStrokeLenPxY;
    Point mCenter;  // Center of screen. Gestures all start from this point.
    @Mock AccessibilityService.GestureResultCallback mGestureDispatchCallback;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        // Check that device has a touch screen.
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        PackageManager pm = instrumentation.getContext().getPackageManager();
        mHasTouchScreen = pm.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
                || pm.hasSystemFeature(PackageManager.FEATURE_FAKETOUCH);
        if (!mHasTouchScreen) {
            return;
        }

        // Find screen size, check that it is big enough for gestures.
        // Gestures will start in the center of the screen, so we need enough horiz/vert space.
        WindowManager windowManager = (WindowManager) instrumentation.getContext()
                .getSystemService(Context.WINDOW_SERVICE);
        final DisplayMetrics metrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getRealMetrics(metrics);
        mCenter = new Point((int) metrics.widthPixels / 2, (int) metrics.heightPixels / 2);
        mStrokeLenPxX = (int)(GESTURE_LENGTH_INCHES * metrics.xdpi);
        mStrokeLenPxY = (int)(GESTURE_LENGTH_INCHES * metrics.ydpi);
        mScreenBigEnough = (metrics.widthPixels / (2 * metrics.xdpi) > GESTURE_LENGTH_INCHES)
                && (metrics.heightPixels / (2 * metrics.ydpi) > GESTURE_LENGTH_INCHES);
        if (!mScreenBigEnough) {
            return;
        }

        // Start stub accessibility service.
        mService = StubService.enableSelf(instrumentation);
    }

    @After
    public void tearDown() throws Exception {
        if (!mHasTouchScreen || !mScreenBigEnough) {
            return;
        }
        mService.runOnServiceSync(() -> mService.disableSelf());
    }

    @Test
    public void testRecognizeGesturePath() {
        if (!mHasTouchScreen || !mScreenBigEnough) {
            return;
        }

        // Compute gesture stroke lengths, in pixels.
        final int dx = mStrokeLenPxX;
        final int dy = mStrokeLenPxY;

        // Test recognizing various gestures.
        testPath(p(-dx, +0), AccessibilityService.GESTURE_SWIPE_LEFT);
        testPath(p(+dx, +0), AccessibilityService.GESTURE_SWIPE_RIGHT);
        testPath(p(+0, -dy), AccessibilityService.GESTURE_SWIPE_UP);
        testPath(p(+0, +dy), AccessibilityService.GESTURE_SWIPE_DOWN);

        testPath(p(-dx, +0), p(+0, +0), AccessibilityService.GESTURE_SWIPE_LEFT_AND_RIGHT);
        testPath(p(-dx, +0), p(-dx, -dy), AccessibilityService.GESTURE_SWIPE_LEFT_AND_UP);
        testPath(p(-dx, +0), p(-dx, +dy), AccessibilityService.GESTURE_SWIPE_LEFT_AND_DOWN);

        testPath(p(+dx, +0), p(+0, +0), AccessibilityService.GESTURE_SWIPE_RIGHT_AND_LEFT);
        testPath(p(+dx, +0), p(+dx, -dy), AccessibilityService.GESTURE_SWIPE_RIGHT_AND_UP);
        testPath(p(+dx, +0), p(+dx, +dy), AccessibilityService.GESTURE_SWIPE_RIGHT_AND_DOWN);

        testPath(p(+0, -dy), p(-dx, -dy), AccessibilityService.GESTURE_SWIPE_UP_AND_LEFT);
        testPath(p(+0, -dy), p(+dx, -dy), AccessibilityService.GESTURE_SWIPE_UP_AND_RIGHT);
        testPath(p(+0, -dy), p(+0, +0), AccessibilityService.GESTURE_SWIPE_UP_AND_DOWN);

        testPath(p(+0, +dy), p(-dx, +dy), AccessibilityService.GESTURE_SWIPE_DOWN_AND_LEFT);
        testPath(p(+0, +dy), p(+dx, +dy), AccessibilityService.GESTURE_SWIPE_DOWN_AND_RIGHT);
        testPath(p(+0, +dy), p(+0, +0), AccessibilityService.GESTURE_SWIPE_DOWN_AND_UP);
    }

    /** Convenient short alias to make a Point. */
    private static Point p(int x, int y) {
        return new Point(x, y);
    }

    /** Test recognizing path from PATH_START to PATH_START+delta. */
    private void testPath(Point delta, int gestureId) {
        testPath(delta, null, gestureId);
    }

    /** Test recognizing path from PATH_START to PATH_START+delta1 to PATH_START+delta2. */
    private void testPath(Point delta1, Point delta2, int gestureId) {
        // Create gesture motions.
        int numPathSegments = (delta2 == null) ? 1 : 2;
        long pathDurationMs = numPathSegments * STROKE_MS;
        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(new StrokeDescription(
                        linePath(mCenter, delta1, delta2), 0, pathDurationMs, false))
                .build();

        // Dispatch gesture motions.
        // Use AccessibilityService.dispatchGesture() instead of Instrumentation.sendPointerSync()
        // because accessibility services read gesture events upstream from the point where
        // sendPointerSync() injects events.
        mService.clearGestures();
        mService.runOnServiceSync(() ->
                mService.dispatchGesture(gesture, mGestureDispatchCallback, null));
        verify(mGestureDispatchCallback,
                timeout(GESTURE_DISPATCH_TIMEOUT_MS).atLeastOnce())
                .onCompleted(any());

        // Wait for gesture recognizer, and check recognized gesture.
        mService.waitUntilGesture();
        assertEquals(1, mService.getGesturesSize());
        assertEquals(gestureId, mService.getGesture(0));
    }

    /** Create a path from startPoint, moving by delta1, then delta2. (delta2 may be null.) */
    Path linePath(Point startPoint, Point delta1, Point delta2) {
        Path path = new Path();
        path.moveTo(startPoint.x, startPoint.y);
        path.lineTo(startPoint.x + delta1.x, startPoint.y + delta1.y);
        if (delta2 != null) {
            path.lineTo(startPoint.x + delta2.x, startPoint.y + delta2.y);
        }
        return path;
    }

    /** Acessibility service stub, which will collect recognized gestures. */
    public static class StubService extends InstrumentedAccessibilityService {

        private ArrayList<Integer> mCollectedGestures = new ArrayList();

        public static StubService enableSelf(Instrumentation instrumentation) {
            return InstrumentedAccessibilityService.enableService(
                    instrumentation, StubService.class);
        }

        @Override
        protected boolean onGesture(int gestureId) {
            synchronized (mCollectedGestures) {
                mCollectedGestures.add(gestureId);
                mCollectedGestures.notifyAll();  // Stop waiting for gesture.
            }
            return true;
        }

        public void clearGestures() {
            synchronized (mCollectedGestures) {
                mCollectedGestures.clear();
            }
        }

        public int getGesturesSize() {
            synchronized (mCollectedGestures) {
                return mCollectedGestures.size();
            }
        }

        public int getGesture(int index) {
            synchronized (mCollectedGestures) {
                return mCollectedGestures.get(index);
            }
        }

        /** Wait for onGesture() to collect next gesture. */
        public void waitUntilGesture() {
            synchronized (mCollectedGestures) {
                if (mCollectedGestures.size() > 0) {
                  return;
                }
                try {
                    mCollectedGestures.wait(GESTURE_RECOGNIZE_TIMEOUT_MS);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

}
