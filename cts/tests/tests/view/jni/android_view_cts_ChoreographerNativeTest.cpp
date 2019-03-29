/*
 * Copyright (C) 2016 The Android Open Source Project
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
 *
 */

#include <android/choreographer.h>

#include <jni.h>
#include <sys/time.h>
#include <time.h>

#include <chrono>
#include <cstdlib>
#include <cstring>
#include <mutex>
#include <thread>

#define LOG_TAG "ChoreographerNative"

#define ASSERT(condition, format, args...) \
        if (!(condition)) { \
            fail(env, format, ## args); \
            return; \
        }


using namespace std::chrono_literals;

static constexpr std::chrono::nanoseconds NOMINAL_VSYNC_PERIOD{16ms};
static constexpr std::chrono::nanoseconds DELAY_PERIOD{NOMINAL_VSYNC_PERIOD * 5};

static std::mutex gLock;
struct Callback {
    int count{0};
    std::chrono::nanoseconds frameTime{0};
};

static void frameCallback(long frameTimeNanos, void* data) {
    std::lock_guard<std::mutex> _l(gLock);
    Callback* cb = static_cast<Callback*>(data);
    cb->count++;
    cb->frameTime = std::chrono::nanoseconds{frameTimeNanos};
}

static std::chrono::nanoseconds now() {
    return std::chrono::steady_clock::now().time_since_epoch();
}

static void fail(JNIEnv* env, const char* format, ...) {
    va_list args;

    va_start(args, format);
    char *msg;
    int rc = vasprintf(&msg, format, args);
    va_end(args);

    jclass exClass;
    const char *className = "java/lang/AssertionError";
    exClass = env->FindClass(className);
    env->ThrowNew(exClass, msg);
    free(msg);
}

static jlong android_view_cts_ChoreographerNativeTest_getChoreographer(JNIEnv*, jclass) {
    std::lock_guard<std::mutex> _l{gLock};
    return reinterpret_cast<jlong>(AChoreographer_getInstance());
}

static jboolean android_view_cts_ChoreographerNativeTest_prepareChoreographerTests(JNIEnv*, jclass,
        jlong choreographerPtr) {
    std::lock_guard<std::mutex> _l{gLock};
    AChoreographer* choreographer = reinterpret_cast<AChoreographer*>(choreographerPtr);
    return choreographer != nullptr;
}

static void android_view_cts_ChoreographerNativeTest_testPostCallbackWithoutDelayEventuallyRunsCallback(
        JNIEnv* env, jclass, jlong choreographerPtr) {
    AChoreographer* choreographer = reinterpret_cast<AChoreographer*>(choreographerPtr);
    Callback* cb1 = new Callback();
    Callback* cb2 = new Callback();
    auto start = now();

    AChoreographer_postFrameCallback(choreographer, frameCallback, cb1);
    AChoreographer_postFrameCallback(choreographer, frameCallback, cb2);
    std::this_thread::sleep_for(NOMINAL_VSYNC_PERIOD * 3);
    {
        std::lock_guard<std::mutex> _l{gLock};
        ASSERT(cb1->count == 1, "Choreographer failed to invoke callback 1");
        ASSERT(cb1->frameTime - start < NOMINAL_VSYNC_PERIOD * 3,
                "Callback 1 has incorect frame time on first invokation");
        ASSERT(cb2->count == 1, "Choreographer failed to invoke callback 2");
        ASSERT(cb2->frameTime - start < NOMINAL_VSYNC_PERIOD * 3,
                "Callback 2 has incorect frame time on first invokation");
        auto delta = cb2->frameTime - cb1->frameTime;
        ASSERT(delta == delta.zero() || delta > delta.zero() && delta < NOMINAL_VSYNC_PERIOD * 2,
                "Callback 1 and 2 have frame times too large of a delta in frame times");
    }

    AChoreographer_postFrameCallback(choreographer, frameCallback, cb1);
    start = now();
    std::this_thread::sleep_for(NOMINAL_VSYNC_PERIOD * 3);
    {
        std::lock_guard<std::mutex> _l{gLock};
        ASSERT(cb1->count == 2, "Choreographer failed to invoke callback 1 a second time");
        ASSERT(cb1->frameTime - start < NOMINAL_VSYNC_PERIOD * 3,
                "Callback 1 has incorect frame time on second invokation");
        ASSERT(cb2->count == 1, "Choreographer invoked callback 2 when not posted");
    }
}

static void android_view_cts_ChoreographerNativeTest_testPostCallbackWithDelayEventuallyRunsCallback(
        JNIEnv* env, jclass, jlong choreographerPtr) {
    AChoreographer* choreographer = reinterpret_cast<AChoreographer*>(choreographerPtr);
    Callback* cb1 = new Callback();
    auto start = now();

    auto delay = std::chrono::duration_cast<std::chrono::milliseconds>(DELAY_PERIOD).count();
    AChoreographer_postFrameCallbackDelayed(choreographer, frameCallback, cb1, delay);
    std::this_thread::sleep_for(NOMINAL_VSYNC_PERIOD * 3);
    {
        std::lock_guard<std::mutex> _l{gLock};
        ASSERT(cb1->count == 0,
                "Choreographer failed to delay callback for a sufficient period of time");
    }
    std::this_thread::sleep_for(DELAY_PERIOD);
    {
        std::lock_guard<std::mutex> _l{gLock};
        ASSERT(cb1->count == 1, "Choreographer failed to invoke delayed callback");
        ASSERT(cb1->frameTime - start < DELAY_PERIOD + NOMINAL_VSYNC_PERIOD * 3,
                "Frametime on callback is incorrect")
    }
}

static JNINativeMethod gMethods[] = {
    {  "nativeGetChoreographer", "()J",
            (void *) android_view_cts_ChoreographerNativeTest_getChoreographer},
    {  "nativePrepareChoreographerTests", "(J)Z",
            (void *) android_view_cts_ChoreographerNativeTest_prepareChoreographerTests},
    {  "nativeTestPostCallbackWithoutDelayEventuallyRunsCallbacks", "(J)V",
            (void *) android_view_cts_ChoreographerNativeTest_testPostCallbackWithoutDelayEventuallyRunsCallback},
    {  "nativeTestPostCallbackWithDelayEventuallyRunsCallbacks", "(J)V",
            (void *) android_view_cts_ChoreographerNativeTest_testPostCallbackWithDelayEventuallyRunsCallback},
};

int register_android_view_cts_ChoreographerNativeTest(JNIEnv* env)
{
    jclass clazz = env->FindClass("android/view/cts/ChoreographerNativeTest");
    return env->RegisterNatives(clazz, gMethods,
            sizeof(gMethods) / sizeof(JNINativeMethod));
}
