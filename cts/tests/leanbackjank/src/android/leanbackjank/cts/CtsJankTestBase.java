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

package android.leanbackjank.cts;

import android.os.Bundle;
import android.support.test.jank.GfxMonitor;
import android.support.test.jank.JankTestBase;
import android.support.test.jank.WindowContentFrameStatsMonitor;
import android.support.test.uiautomator.UiDevice;

import com.android.compatibility.common.util.DeviceReportLog;
import com.android.compatibility.common.util.ResultType;
import com.android.compatibility.common.util.ResultUnit;

public abstract class CtsJankTestBase extends JankTestBase {

    private final String REPORT_LOG_NAME = "CtsLeanbackJankTestCases";

    private UiDevice mDevice;
    private DeviceReportLog mLog;

    private void printIntValueWithKey(String source, Bundle metrics, String key,
            ResultType resultType, ResultUnit resultUnit) {
        if (!metrics.containsKey(key)) {
            return;
        }
        mLog.addValue(source, formatKeyForTestMetrics(key), metrics.getInt(key), resultType, resultUnit);
    }

    private void printDoubleValueWithKey(String source, Bundle metrics, String key,
            ResultType resultType, ResultUnit resultUnit) {
        if (!metrics.containsKey(key)) {
            return;
        }
        mLog.addValue(source, formatKeyForTestMetrics(key), metrics.getDouble(key), resultType,
                resultUnit);
    }

    private String formatKeyForTestMetrics(String key) {
        return key.toLowerCase().replace('-', '_');
    }

    @Override
    public void afterTest(Bundle metrics) {
        String source = String.format("%s#%s", getClass().getCanonicalName(), getName());
        printDoubleValueWithKey(source, metrics, WindowContentFrameStatsMonitor.KEY_AVG_FPS,
                ResultType.HIGHER_BETTER, ResultUnit.FPS);
        printDoubleValueWithKey(source, metrics,
                WindowContentFrameStatsMonitor.KEY_AVG_LONGEST_FRAME,
                ResultType.LOWER_BETTER, ResultUnit.MS);
        printIntValueWithKey(source, metrics, WindowContentFrameStatsMonitor.KEY_MAX_NUM_JANKY,
                ResultType.LOWER_BETTER, ResultUnit.COUNT);
        mLog.setSummary(formatKeyForTestMetrics(WindowContentFrameStatsMonitor.KEY_AVG_NUM_JANKY),
                metrics.getDouble(WindowContentFrameStatsMonitor.KEY_AVG_NUM_JANKY),
                ResultType.LOWER_BETTER, ResultUnit.COUNT);

        printDoubleValueWithKey(source, metrics, GfxMonitor.KEY_AVG_NUM_JANKY,
                ResultType.LOWER_BETTER, ResultUnit.COUNT);
        printDoubleValueWithKey(source, metrics, GfxMonitor.KEY_AVG_FRAME_TIME_90TH_PERCENTILE,
                ResultType.LOWER_BETTER, ResultUnit.MS);
        printDoubleValueWithKey(source, metrics, GfxMonitor.KEY_AVG_FRAME_TIME_95TH_PERCENTILE,
                ResultType.LOWER_BETTER, ResultUnit.MS);
        printDoubleValueWithKey(source, metrics, GfxMonitor.KEY_AVG_FRAME_TIME_99TH_PERCENTILE,
                ResultType.LOWER_BETTER, ResultUnit.MS);
        printDoubleValueWithKey(source, metrics, GfxMonitor.KEY_AVG_MISSED_VSYNC,
                ResultType.LOWER_BETTER, ResultUnit.COUNT);
        printDoubleValueWithKey(source, metrics, GfxMonitor.KEY_AVG_SLOW_UI_THREAD,
                ResultType.LOWER_BETTER, ResultUnit.COUNT);
        printDoubleValueWithKey(source, metrics, GfxMonitor.KEY_AVG_SLOW_BITMAP_UPLOADS,
                ResultType.LOWER_BETTER, ResultUnit.COUNT);
        printDoubleValueWithKey(source, metrics, GfxMonitor.KEY_AVG_SLOW_DRAW,
                ResultType.LOWER_BETTER, ResultUnit.COUNT);
        printDoubleValueWithKey(source, metrics, GfxMonitor.KEY_AVG_HIGH_INPUT_LATENCY,
                ResultType.LOWER_BETTER, ResultUnit.COUNT);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        String streamName = "cts_leanback_jank";
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
