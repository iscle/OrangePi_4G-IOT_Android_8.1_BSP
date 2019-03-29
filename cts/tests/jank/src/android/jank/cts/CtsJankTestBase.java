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

package android.jank.cts;

import android.os.Bundle;
import android.support.test.jank.JankTestBase;
import android.support.test.jank.WindowContentFrameStatsMonitor;
import android.support.test.uiautomator.UiDevice;

import com.android.compatibility.common.util.DeviceReportLog;
import com.android.compatibility.common.util.ResultType;
import com.android.compatibility.common.util.ResultUnit;

public abstract class CtsJankTestBase extends JankTestBase {

    private static final String REPORT_LOG_NAME = "CtsJankDeviceTestCases";
    private UiDevice mDevice;
    private DeviceReportLog mLog;

    @Override
    public void afterTest(Bundle metrics) {
        String source = String.format("%s#%s", getClass().getCanonicalName(), getName());
        mLog.addValue(source, "frame_fps",
                metrics.getDouble(WindowContentFrameStatsMonitor.KEY_AVG_FPS),
                ResultType.HIGHER_BETTER, ResultUnit.FPS);
        mLog.addValue(source, "frame_max_frame_duration",
                metrics.getDouble(WindowContentFrameStatsMonitor.KEY_AVG_LONGEST_FRAME),
                ResultType.LOWER_BETTER, ResultUnit.MS);
        mLog.addValue(source, "frame_max_jank",
                metrics.getInt(WindowContentFrameStatsMonitor.KEY_MAX_NUM_JANKY),
                ResultType.LOWER_BETTER, ResultUnit.COUNT);
        mLog.setSummary("frame_avg_jank",
                metrics.getDouble(WindowContentFrameStatsMonitor.KEY_AVG_NUM_JANKY),
                ResultType.LOWER_BETTER, ResultUnit.COUNT);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        String streamName = "cts_device_jank_test";
        mLog = new DeviceReportLog(REPORT_LOG_NAME, streamName);
        // fix device orientation
        mDevice = UiDevice.getInstance(getInstrumentation());
        mDevice.setOrientationNatural();
    }

    @Override
    protected void tearDown() throws Exception {
        mLog.submit(getInstrumentation());
        // restore device orientation
        mDevice.unfreezeRotation();
        super.tearDown();
    }

    protected UiDevice getUiDevice() {
        return mDevice;
    }
}
