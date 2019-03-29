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
package android.ui.cts;

import android.test.ActivityInstrumentationTestCase2;

import com.android.compatibility.common.util.DeviceReportLog;
import com.android.compatibility.common.util.MeasureRun;
import com.android.compatibility.common.util.MeasureTime;
import com.android.compatibility.common.util.ResultType;
import com.android.compatibility.common.util.ResultUnit;
import com.android.compatibility.common.util.Stat;

import java.io.IOException;

public class ScrollingTest extends ActivityInstrumentationTestCase2<ScrollingActivity> {

    private static final String REPORT_LOG_NAME = "CtsUiDeviceTestCases";

    private ScrollingActivity mActivity;

    public ScrollingTest() {
        super(ScrollingActivity.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        mActivity = getActivity();
        getInstrumentation().waitForIdleSync();
        try {
            runTestOnUiThread(new Runnable() {
                @Override
                public void run() {
                }
            });
        } catch (Throwable e) {
            e.printStackTrace();
            fail();
        }
    }

    @Override
    protected void tearDown() throws Exception {
        mActivity = null;
        super.tearDown();
    }

    public void testFullScrolling() throws Exception {
        final int NUMBER_REPEAT = 10;
        final ScrollingActivity activity = mActivity;
        double[] results = MeasureTime.measure(NUMBER_REPEAT, new MeasureRun() {

            @Override
            public void run(int i) throws IOException {
                assertTrue(activity.scrollToBottom());
                assertTrue(activity.scrollToTop());
            }
        });
        String streamName = "test_full_scrolling";
        DeviceReportLog report = new DeviceReportLog(REPORT_LOG_NAME, streamName);
        report.addValues("scrolling_time", results, ResultType.LOWER_BETTER,ResultUnit.MS);
        Stat.StatResult stat = Stat.getStat(results);
        report.setSummary("scrolling_time_average", stat.mAverage,
                ResultType.LOWER_BETTER,ResultUnit.MS);
        report.submit(getInstrumentation());
    }
}
