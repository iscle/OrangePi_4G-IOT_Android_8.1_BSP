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
#include <jni.h>

#include <stdlib.h>
#include <android/log.h>

#include <android/native_window.h>
#include <android/native_window_jni.h>

#include <graphics/GLUtils.h>
#include <graphics/Renderer.h>

#include "fullpipeline/FullPipelineRenderer.h"
#include "pixeloutput/PixelOutputRenderer.h"
#include "shaderperf/ShaderPerfRenderer.h"
#include "contextswitch/ContextSwitchRenderer.h"

// Holds the current benchmark's renderer.
Renderer* gRenderer = NULL;
ANativeWindow* gNativeWindow = NULL;

enum {
    FULL_PIPELINE_BENCHMARK = 0,
    PIXEL_OUTPUT_BENCHMARK = 1,
    SHADER_PERF_BENCHMARK = 2,
    CONTEXT_SWITCH_BENCHMARK = 3
};

extern "C" JNIEXPORT jboolean JNICALL
Java_android_opengl2_cts_primitive_GLPrimitiveActivity_startBenchmark(
        JNIEnv* env, jclass /*clazz*/, jint workload, jint numFrames, jdoubleArray frameTimes) {
    if (gRenderer == NULL) {
        return false;
    }

    // Sets up the renderer.
    bool success = gRenderer->setUp(workload);

    // Records the start time.
    double start = GLUtils::currentTimeMillis();

    // Offscreen renders 100 tiles per frame so reduce the number of frames to render.
    if (gRenderer->mOffscreen) {
        numFrames /= Renderer::OFFSCREEN_INNER_FRAMES;
    }

    // Draw off the screen.
    for (int i = 0; i < numFrames && success; i++) {
        // Draw a frame.
        success = gRenderer->draw();
    }

    // Records the end time.
    double end = GLUtils::currentTimeMillis();

    // Sets the times in the Java array.
    double times[] = {start, end};
    env->SetDoubleArrayRegion(frameTimes, 0, 2, times);

    success = gRenderer->tearDown() && success;
    return success;
}

// The following functions create the renderers for the various benchmarks.
extern "C" JNIEXPORT void JNICALL
Java_android_opengl2_cts_primitive_GLPrimitiveActivity_setupBenchmark(
        JNIEnv* env, jclass /*clazz*/, jobject surface, jint benchmark,
        jboolean offscreen) {
    gNativeWindow = ANativeWindow_fromSurface(env, surface);
    switch (benchmark) {
        case FULL_PIPELINE_BENCHMARK:
            gRenderer = new FullPipelineRenderer(gNativeWindow, offscreen);
            break;
        case PIXEL_OUTPUT_BENCHMARK:
            gRenderer = new PixelOutputRenderer(gNativeWindow, offscreen);
            break;
        case SHADER_PERF_BENCHMARK:
            gRenderer = new ShaderPerfRenderer(gNativeWindow, offscreen);
            break;
        case CONTEXT_SWITCH_BENCHMARK:
            gRenderer = new ContextSwitchRenderer(gNativeWindow, offscreen);
            break;
        default:
            __android_log_print(ANDROID_LOG_ERROR, "GLPrimitive",
                    "Unknown benchmark '%d'", benchmark);
            ANativeWindow_release(gNativeWindow);
            gNativeWindow = NULL;
            return;
    }

    // Set up call will log error conditions
    if (!gRenderer->eglSetUp()) {
        delete gRenderer;
        gRenderer = NULL;

        ANativeWindow_release(gNativeWindow);
        gNativeWindow = NULL;
    }
}

extern "C" JNIEXPORT void JNICALL
Java_android_opengl2_cts_primitive_GLPrimitiveActivity_tearDownBenchmark(
        JNIEnv* /*env*/, jclass /*clazz*/) {
    if (gRenderer == NULL) {
        return;
    }
    gRenderer->eglTearDown();
    delete gRenderer;
    gRenderer = NULL;

    ANativeWindow_release(gNativeWindow);
    gNativeWindow = NULL;
}
