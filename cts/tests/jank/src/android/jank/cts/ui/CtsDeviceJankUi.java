/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package android.jank.cts.ui;

import android.content.ComponentName;
import android.content.Intent;
import android.jank.cts.CtsJankTestBase;
import android.os.SystemClock;
import android.support.test.jank.JankTest;
import android.support.test.jank.WindowContentFrameStatsMonitor;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.Direction;
import android.support.test.uiautomator.Until;
import android.widget.ListView;

import java.io.IOException;

public class CtsDeviceJankUi extends CtsJankTestBase {
    private final static int NUM_ELEMENTS = 1000;
    private static final long DEFAULT_ANIMATION_TIME = 2 * 1000;
    private static final long POST_SCROLL_IDLE_TIME = 2 *1000;
    private final static String PACKAGE = "android.ui.cts";
    private final static String CLASS = PACKAGE + ".ScrollingActivity";

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        // launch the activity as part of the set up
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setComponent(new ComponentName(PACKAGE, CLASS));
        intent.putExtra("num_elements", NUM_ELEMENTS);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        getInstrumentation().getTargetContext().startActivity(intent);
        getUiDevice().wait(Until.hasObject(By.pkg(PACKAGE)), DEFAULT_ANIMATION_TIME);
    }

    @Override
    protected void tearDown() throws Exception {
        getUiDevice().pressHome();
        super.tearDown();
    }

    @JankTest(expectedFrames=50, defaultIterationCount=5)
    @WindowContentFrameStatsMonitor
    public void testScrolling() throws IOException {
        getUiDevice().findObject(By.clazz(ListView.class)).fling(Direction.DOWN);
        SystemClock.sleep(POST_SCROLL_IDLE_TIME);
    }
}
