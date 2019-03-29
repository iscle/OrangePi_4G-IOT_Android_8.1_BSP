/*
 * Copyright (C) 2013 The Android Open Source Project
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
package android.opengl2.cts.reference;

import android.content.Intent;
import android.opengl2.cts.GLActivityIntentKeys;
import android.test.ActivityInstrumentationTestCase2;

import com.android.compatibility.common.util.DeviceReportLog;
import com.android.compatibility.common.util.ResultType;
import com.android.compatibility.common.util.ResultUnit;

/**
 * Runs the Reference OpenGL ES 2.0 Benchmark.
 */
public class GLReferenceBenchmark extends ActivityInstrumentationTestCase2<GLReferenceActivity> {

    private static final int NUM_FRAMES_PER_SCENE = 500;
    private static final int NUM_SCENES = 2;
    private static final int NUM_FRAMES = NUM_FRAMES_PER_SCENE * NUM_SCENES;
    private static final int TIMEOUT = 2000000;
    private static final String REPORT_LOG_NAME = "CtsOpenGlPerf2TestCases";

    public GLReferenceBenchmark() {
        super(GLReferenceActivity.class);
    }

    /**
     * Runs the reference benchmark.
     */
    public void testReferenceBenchmark() throws Exception {
        Intent intent = new Intent();
        intent.putExtra(GLActivityIntentKeys.INTENT_EXTRA_NUM_FRAMES, NUM_FRAMES);
        intent.putExtra(GLActivityIntentKeys.INTENT_EXTRA_TIMEOUT, TIMEOUT);

        GLReferenceActivity activity = null;
        setActivityIntent(intent);
        activity = getActivity();
        if (activity != null) {
            activity.waitForCompletion();
            double totalTime = 0;
            double[] setUpTimes = activity.mSetUpTimes;
            double[] updateTimes = activity.mUpdateTimes;
            double[] renderTimes = activity.mRenderTimes;

            // Calculate update and render average.
            double updateSum = updateTimes[0];
            double renderSum = renderTimes[0];
            for (int i = 0; i < NUM_FRAMES - 1; i++) {
                updateSum += updateTimes[i + 1];
                renderSum += renderTimes[i + 1];
            }
            double updateAverage = updateSum / NUM_FRAMES;
            double renderAverage = renderSum / NUM_FRAMES;

            String streamName = "test_reference_benchmark";
            DeviceReportLog report = new DeviceReportLog(REPORT_LOG_NAME, streamName);
            report.addValues("set_up_times", setUpTimes, ResultType.LOWER_BETTER, ResultUnit.MS);
            report.addValue("update_time_average", updateAverage, ResultType.LOWER_BETTER,
                    ResultUnit.MS);
            report.addValue("render_time_average", renderAverage, ResultType.LOWER_BETTER,
                    ResultUnit.MS);
            totalTime = setUpTimes[0] + setUpTimes[1] + setUpTimes[2] + setUpTimes[3] +
                    updateAverage + renderAverage;
            report.setSummary("total_time_average", totalTime, ResultType.LOWER_BETTER,
                    ResultUnit.MS);
            report.submit(getInstrumentation());
        }
    }
}
