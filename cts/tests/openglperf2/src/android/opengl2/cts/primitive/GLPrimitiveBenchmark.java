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
package android.opengl2.cts.primitive;

import android.content.Intent;
import android.opengl2.cts.GLActivityIntentKeys;
import android.test.ActivityInstrumentationTestCase2;

import com.android.compatibility.common.util.DeviceReportLog;
import com.android.compatibility.common.util.ResultType;
import com.android.compatibility.common.util.ResultUnit;

/**
 * Runs the Primitive OpenGL ES 2.0 Benchmarks.
 */
public class GLPrimitiveBenchmark extends ActivityInstrumentationTestCase2<GLPrimitiveActivity> {

    private static final int NUM_FRAMES = 100;
    private static final int NUM_ITERATIONS = 8;
    private static final int TIMEOUT = 1000000;
    private static final String REPORT_LOG_NAME = "CtsOpenGlPerf2TestCases";

    public GLPrimitiveBenchmark() {
        super(GLPrimitiveActivity.class);
    }

    /**
     * Runs the full OpenGL ES 2.0 pipeline test offscreen.
     */
    public void testFullPipelineOffscreen() throws Exception {
        String streamName = "test_full_pipeline_offscreen";
        runBenchmark(BenchmarkName.FullPipeline, true, NUM_FRAMES, NUM_ITERATIONS, TIMEOUT,
                streamName);
    }

    /**
     * Runs the full OpenGL ES 2.0 pipeline test onscreen.
     */
    public void testFullPipelineOnscreen() throws Exception {
        String streamName = "test_full_pipeline_onscreen";
        runBenchmark(BenchmarkName.FullPipeline, false, NUM_FRAMES, NUM_ITERATIONS, TIMEOUT,
                streamName);
    }

    /**
     * Runs the pixel output test offscreen.
     */
    public void testPixelOutputOffscreen() throws Exception {
        String streamName = "test_pixel_output_offscreen";
        runBenchmark(BenchmarkName.PixelOutput, true, NUM_FRAMES, NUM_ITERATIONS, TIMEOUT,
                streamName);
    }

    /**
     * Runs the pixel output test onscreen.
     */
    public void testPixelOutputOnscreen() throws Exception {
        String streamName = "test_pixel_output_onscreen";
        runBenchmark(BenchmarkName.PixelOutput, false, NUM_FRAMES, NUM_ITERATIONS, TIMEOUT,
                streamName);
    }

    /**
     * Runs the shader performance test offscreen.
     */
    public void testShaderPerfOffscreen() throws Exception {
        String streamName = "test_shader_perf_offscreen";
        runBenchmark(BenchmarkName.ShaderPerf, true, NUM_FRAMES, NUM_ITERATIONS, TIMEOUT,
                streamName);
    }

    /**
     * Runs the shader performance test onscreen.
     */
    public void testShaderPerfOnscreen() throws Exception {
        String streamName = "test_shader_perf_onscreen";
        runBenchmark(BenchmarkName.ShaderPerf, false, NUM_FRAMES, NUM_ITERATIONS, TIMEOUT,
                streamName);
    }

    /**
     * Runs the context switch overhead test offscreen.
     */
    public void testContextSwitchOffscreen() throws Exception {
        String streamName = "test_context_switch_offscreen";
        runBenchmark(BenchmarkName.ContextSwitch, true, NUM_FRAMES, NUM_ITERATIONS, TIMEOUT,
                streamName);
    }

    /**
     * Runs the context switch overhead test onscreen.
     */
    public void testContextSwitchOnscreen() throws Exception {
        String streamName = "test_context_switch_onscreen";
        runBenchmark(BenchmarkName.ContextSwitch, false, NUM_FRAMES, NUM_ITERATIONS, TIMEOUT,
                streamName);
    }

    /**
     * Runs the specified test.
     *
     * @param benchmark An enum representing the benchmark to run.
     * @param offscreen Whether to render to an offscreen framebuffer rather than the screen.
     * @param numFrames The number of frames to render.
     * @param numIterations The number of iterations to run, each iteration has a bigger workload.
     * @param timeout The milliseconds to wait for an iteration of the benchmark before timing out.
     * @param streamName The name of the stream of test metrics.
     * @throws Exception If the benchmark could not be run.
     */
    private void runBenchmark(BenchmarkName benchmark, boolean offscreen, int numFrames,
            int numIterations, int timeout, String streamName) throws Exception {
        String benchmarkName = benchmark.toString();
        Intent intent = new Intent();
        intent.putExtra(GLActivityIntentKeys.INTENT_EXTRA_BENCHMARK_NAME, benchmarkName);
        intent.putExtra(GLActivityIntentKeys.INTENT_EXTRA_OFFSCREEN, offscreen);
        intent.putExtra(GLActivityIntentKeys.INTENT_EXTRA_NUM_FRAMES, numFrames);
        intent.putExtra(GLActivityIntentKeys.INTENT_EXTRA_NUM_ITERATIONS, numIterations);
        intent.putExtra(GLActivityIntentKeys.INTENT_EXTRA_TIMEOUT, timeout);

        setActivityIntent(intent);
        GLPrimitiveActivity activity = getActivity();
        if (activity != null) {
            activity.waitForCompletion();
            double[] fpsValues = activity.mFpsValues;
            double score = 0;
            for (double d : fpsValues) {
                score += d;
            }
            score /= numIterations;// Average.

            // TODO: maybe standard deviation / RMSE will be useful?

            DeviceReportLog report = new DeviceReportLog(REPORT_LOG_NAME, streamName);
            report.setSummary("average_fps", score, ResultType.HIGHER_BETTER, ResultUnit.SCORE);
            report.submit(getInstrumentation());
        }
    }
}
