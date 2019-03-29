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

//#define LOG_NDEBUG 0
#define LOG_TAG "SL-Utils"

#include "sl-utils.h"
#include <utils/Mutex.h>

#define ARRAY_SIZE(a) (sizeof(a) / sizeof((a)[0]))

// These will wind up in <SLES/OpenSLES_Android.h>
#define SL_ANDROID_SPEAKER_QUAD (SL_SPEAKER_FRONT_LEFT | SL_SPEAKER_FRONT_RIGHT \
 | SL_SPEAKER_BACK_LEFT | SL_SPEAKER_BACK_RIGHT)

#define SL_ANDROID_SPEAKER_5DOT1 (SL_SPEAKER_FRONT_LEFT | SL_SPEAKER_FRONT_RIGHT \
 | SL_SPEAKER_FRONT_CENTER  | SL_SPEAKER_LOW_FREQUENCY| SL_SPEAKER_BACK_LEFT \
 | SL_SPEAKER_BACK_RIGHT)

#define SL_ANDROID_SPEAKER_7DOT1 (SL_ANDROID_SPEAKER_5DOT1 | SL_SPEAKER_SIDE_LEFT \
 |SL_SPEAKER_SIDE_RIGHT)

namespace android {

static Mutex gLock;
static SLObjectItf gEngineObject;
static unsigned gRefCount;

static const char *gErrorStrings[] = {
    "SL_RESULT_SUCCESS",                // 0
    "SL_RESULT_PRECONDITIONS_VIOLATE",  // 1
    "SL_RESULT_PARAMETER_INVALID",      // 2
    "SL_RESULT_MEMORY_FAILURE",         // 3
    "SL_RESULT_RESOURCE_ERROR",         // 4
    "SL_RESULT_RESOURCE_LOST",          // 5
    "SL_RESULT_IO_ERROR",               // 6
    "SL_RESULT_BUFFER_INSUFFICIENT",    // 7
    "SL_RESULT_CONTENT_CORRUPTED",      // 8
    "SL_RESULT_CONTENT_UNSUPPORTED",    // 9
    "SL_RESULT_CONTENT_NOT_FOUND",      // 10
    "SL_RESULT_PERMISSION_DENIED",      // 11
    "SL_RESULT_FEATURE_UNSUPPORTED",    // 12
    "SL_RESULT_INTERNAL_ERROR",         // 13
    "SL_RESULT_UNKNOWN_ERROR",          // 14
    "SL_RESULT_OPERATION_ABORTED",      // 15
    "SL_RESULT_CONTROL_LOST",           // 16
};

const char *getSLErrStr(int code) {
    if ((size_t)code >= ARRAY_SIZE(gErrorStrings)) {
        return "SL_RESULT_UNKNOWN";
    }
    return gErrorStrings[code];
}

SLuint32 channelCountToMask(unsigned channelCount) {
    switch (channelCount) {
    case 1:
        return SL_SPEAKER_FRONT_LEFT; // we prefer left over center
    case 2:
        return SL_SPEAKER_FRONT_LEFT | SL_SPEAKER_FRONT_RIGHT;
    case 3:
        return SL_SPEAKER_FRONT_LEFT | SL_SPEAKER_FRONT_RIGHT | SL_SPEAKER_FRONT_CENTER;
    case 4:
        return SL_ANDROID_SPEAKER_QUAD;
    case 5:
        return SL_ANDROID_SPEAKER_QUAD | SL_SPEAKER_FRONT_CENTER;
    case 6:
        return SL_ANDROID_SPEAKER_5DOT1;
    case 7:
        return SL_ANDROID_SPEAKER_5DOT1 | SL_SPEAKER_BACK_CENTER;
    case 8:
        return SL_ANDROID_SPEAKER_7DOT1;
    default:
        return 0;
    }
}

static SLObjectItf createEngine() {
    static SLEngineOption EngineOption[] = {
        {
            (SLuint32) SL_ENGINEOPTION_THREADSAFE,
            (SLuint32) SL_BOOLEAN_TRUE
        },
    };
    // create engine in thread-safe mode
    SLObjectItf engine;
    SLresult result = slCreateEngine(&engine,
            1 /* numOptions */, EngineOption /* pEngineOptions */,
            0 /* numInterfaces */, NULL /* pInterfaceIds */, NULL /* pInterfaceRequired */);
    if (result != SL_RESULT_SUCCESS) {
        ALOGE("slCreateEngine() failed: %s", getSLErrStr(result));
        return NULL;
    }
    // realize the engine
    result = (*engine)->Realize(engine, SL_BOOLEAN_FALSE /* async */);
    if (result != SL_RESULT_SUCCESS) {
        ALOGE("Realize() failed: %s", getSLErrStr(result));
        (*engine)->Destroy(engine);
        return NULL;
    }
    return engine;
}

SLObjectItf OpenSLEngine(bool global) {

    if (!global) {
        return createEngine();
    }
    Mutex::Autolock l(gLock);
    if (gRefCount == 0) {
        gEngineObject = createEngine();
    }
    gRefCount++;
    return gEngineObject;
}

void CloseSLEngine(SLObjectItf engine) {
    Mutex::Autolock l(gLock);
    if (engine == gEngineObject) {
        if (gRefCount == 0) {
            ALOGE("CloseSLEngine(%p): refcount already 0", engine);
            return;
        }
        if (--gRefCount != 0) {
            return;
        }
        gEngineObject = NULL;
    }
    (*engine)->Destroy(engine);
}

} // namespace android

