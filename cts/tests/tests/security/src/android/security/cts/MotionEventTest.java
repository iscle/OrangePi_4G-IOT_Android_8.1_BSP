/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.security.cts;

import static org.junit.Assert.assertEquals;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.graphics.Color;
import android.graphics.Point;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.MediumTest;
import android.support.test.rule.ActivityTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import com.android.compatibility.common.util.PollingCheck;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class MotionEventTest {
    private static final String TAG = "MotionEventTest";
    private Activity mActivity;
    private Instrumentation mInstrumentation;

    @Rule
    public ActivityTestRule<MotionEventTestActivity> mActivityRule =
            new ActivityTestRule<>(MotionEventTestActivity.class);

    @Before
    public void setup() {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        mActivity = mActivityRule.getActivity();
        PollingCheck.waitFor(mActivity::hasWindowFocus);
    }

    /**
     * Test for whether ACTION_OUTSIDE events contain information about whether touches are
     * obscured.
     *
     * If ACTION_OUTSIDE_EVENTS contain information about whether the touch is obscured, then a
     * pattern of invisible, untouchable, unfocusable application overlays can be placed across the
     * screen to determine approximate locations of touch events without the user knowing.
     */
    @Test
    public void testActionOutsideDoesNotContainedObscuredInformation() throws Exception {
        enableAppOps();
        final OnTouchListener listener = new OnTouchListener();
        final Point size = new Point();
        final View[] viewHolder = new View[1];
        mActivity.runOnUiThread(() -> {
            final WindowManager wm = mActivity.getSystemService(WindowManager.class);
            wm.getDefaultDisplay().getSize(size);

            WindowManager.LayoutParams wmlp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH |
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
            wmlp.width = size.x / 4;
            wmlp.height = size.y / 4;
            wmlp.gravity = Gravity.TOP | Gravity.LEFT;
            wmlp.setTitle(mActivity.getPackageName());

            ViewGroup.LayoutParams vglp = new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT);

            View v = new View(mActivity);
            v.setOnTouchListener(listener);
            v.setBackgroundColor(Color.GREEN);
            v.setLayoutParams(vglp);
            wm.addView(v, wmlp);

            wmlp.gravity = Gravity.TOP | Gravity.RIGHT;

            v = new View(mActivity);
            v.setBackgroundColor(Color.BLUE);
            v.setOnTouchListener(listener);
            v.setLayoutParams(vglp);
            viewHolder[0] = v;

            wm.addView(v, wmlp);
        });
        mInstrumentation.waitForIdleSync();

        FutureTask<Point> task = new FutureTask<>(() -> {
            final int[] viewLocation = new int[2];
            viewHolder[0].getLocationOnScreen(viewLocation);
            return new Point(viewLocation[0], viewLocation[1]);
        });
        mActivity.runOnUiThread(task);
        Point viewLocation = task.get(5, TimeUnit.SECONDS);
        injectTap(viewLocation.x, viewLocation.y);

        List<MotionEvent> outsideEvents = listener.getOutsideEvents();
        assertEquals(2, outsideEvents.size());
        for (MotionEvent e : outsideEvents) {
            assertEquals(0, e.getFlags() & MotionEvent.FLAG_WINDOW_IS_OBSCURED);
        }
    }


    private void enableAppOps() {
        StringBuilder cmd = new StringBuilder();
        cmd.append("appops set ");
        cmd.append(mInstrumentation.getContext().getPackageName());
        cmd.append(" android:system_alert_window allow");
        mInstrumentation.getUiAutomation().executeShellCommand(cmd.toString());

        StringBuilder query = new StringBuilder();
        query.append("appops get ");
        query.append(mInstrumentation.getContext().getPackageName());
        query.append(" android:system_alert_window");
        String queryStr = query.toString();

        String result;
        do {
            ParcelFileDescriptor pfd =
                    mInstrumentation.getUiAutomation().executeShellCommand(queryStr);
            InputStream inputStream = new FileInputStream(pfd.getFileDescriptor());
            result = convertStreamToString(inputStream);
        } while (result.contains("No operations"));
    }

    private String convertStreamToString(InputStream is) {
        try (Scanner s = new Scanner(is).useDelimiter("\\A")) {
            return s.hasNext() ? s.next() : "";
        }
    }

    private void injectTap(int x, int y) {
        long downTime = SystemClock.uptimeMillis();
        injectEvent(MotionEvent.ACTION_DOWN, x, y, downTime);
        injectEvent(MotionEvent.ACTION_UP, x, y, downTime);
    }

    private void injectEvent(int action, int x, int y, long downTime) {
        final UiAutomation automation = mInstrumentation.getUiAutomation();
        final long eventTime = SystemClock.uptimeMillis();
        MotionEvent event = MotionEvent.obtain(downTime, eventTime, action, x, y, 0);
        event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        automation.injectInputEvent(event, true);
        event.recycle();
    }

    private static class OnTouchListener implements View.OnTouchListener {
        private List<MotionEvent> mOutsideEvents;

        public OnTouchListener() {
            mOutsideEvents = new ArrayList<>();
        }

        public boolean onTouch(View v, MotionEvent e) {
            if (e.getAction() == MotionEvent.ACTION_OUTSIDE) {
                mOutsideEvents.add(MotionEvent.obtain(e));
            }
            return true;
        }

        public List<MotionEvent> getOutsideEvents() {
            return mOutsideEvents;
        }
    }
}
