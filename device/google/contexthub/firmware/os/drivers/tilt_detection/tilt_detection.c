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
 */

#include <stdlib.h>
#include <string.h>
#include <timer.h>
#include <heap.h>
#include <plat/rtc.h>
#include <plat/syscfg.h>
#include <hostIntf.h>
#include <nanohubPacket.h>

#include <seos.h>

#include <nanohub_math.h>
#include <sensors.h>
#include <limits.h>

#define TILT_APP_VERSION 1

#define EVT_SENSOR_ANY_MOTION sensorGetMyEventType(SENS_TYPE_ANY_MOTION)
#define EVT_SENSOR_NO_MOTION sensorGetMyEventType(SENS_TYPE_NO_MOTION)
#define EVT_SENSOR_ACCEL sensorGetMyEventType(SENS_TYPE_ACCEL)

#define ACCEL_MIN_RATE    SENSOR_HZ(50)
#define ACCEL_MAX_LATENCY 250000000ull   // 250 ms

#define BATCH_TIME      2000000000ull // 2.0 seconds
#define ANGLE_THRESH    (0.819 * 9.81 * 9.81) // ~cos(35) * (1G in m/s^2)^2

struct TiltAlgoState {
    uint64_t this_batch_init_ts;
    uint32_t this_batch_num_samples;
    float this_batch_sample_sum[3];
    float this_batch_g[3];
    float last_ref_g_vector[3];
    bool last_ref_g_vector_valid;
    bool anamoly_this_batch;
    bool tilt_detected;
};

static struct TiltDetectionTask {
    struct TiltAlgoState algoState;
    uint32_t taskId;
    uint32_t handle;
    uint32_t anyMotionHandle;
    uint32_t noMotionHandle;
    uint32_t accelHandle;
    enum {
        STATE_DISABLED,
        STATE_AWAITING_ANY_MOTION,
        STATE_AWAITING_TILT,
    } taskState;
} mTask;

// *****************************************************************************

static void algoInit()
{
    // nothing here
}

static bool algoUpdate(struct TripleAxisDataEvent *ev)
{
    float dotProduct = 0.0f;
    uint64_t dt;
    bool latch_g_vector = false;
    bool tilt_detected = false;
    struct TiltAlgoState *state = &mTask.algoState;
    uint64_t sample_ts = ev->referenceTime;
    uint32_t numSamples = ev->samples[0].firstSample.numSamples;
    uint32_t i;
    struct TripleAxisDataPoint *sample;
    float invN;

    for (i = 0; i < numSamples; i++) {
        sample = &ev->samples[i];
        if (i > 0)
            sample_ts += sample->deltaTime;

        if (state->this_batch_init_ts == 0) {
            state->this_batch_init_ts = sample_ts;
        }

        state->this_batch_sample_sum[0] += sample->x;
        state->this_batch_sample_sum[1] += sample->y;
        state->this_batch_sample_sum[2] += sample->z;

        state->this_batch_num_samples++;

        dt = (sample_ts - state->this_batch_init_ts);

        if (dt > BATCH_TIME) {
            invN = 1.0f / state->this_batch_num_samples;
            state->this_batch_g[0] = state->this_batch_sample_sum[0] * invN;
            state->this_batch_g[1] = state->this_batch_sample_sum[1] * invN;
            state->this_batch_g[2] = state->this_batch_sample_sum[2] * invN;

            if (state->last_ref_g_vector_valid) {
                dotProduct = state->this_batch_g[0] * state->last_ref_g_vector[0] +
                    state->this_batch_g[1] * state->last_ref_g_vector[1] +
                    state->this_batch_g[2] * state->last_ref_g_vector[2];

                if (dotProduct < ANGLE_THRESH) {
                    tilt_detected = true;
                    latch_g_vector = true;
                }
            } else { // reference g vector not valid, first time computing
                latch_g_vector = true;
                state->last_ref_g_vector_valid = true;
            }

            // latch the first batch or when dotProduct < ANGLE_THRESH
            if (latch_g_vector) {
                state->last_ref_g_vector[0] = state->this_batch_g[0];
                state->last_ref_g_vector[1] = state->this_batch_g[1];
                state->last_ref_g_vector[2] = state->this_batch_g[2];
            }

            // Seed the next batch
            state->this_batch_init_ts = 0;
            state->this_batch_num_samples = 0;
            state->this_batch_sample_sum[0] = 0;
            state->this_batch_sample_sum[1] = 0;
            state->this_batch_sample_sum[2] = 0;
        }
    }

    return tilt_detected;
}

static void configAnyMotion(bool on) {
    if (on) {
        sensorRequest(mTask.taskId, mTask.anyMotionHandle, SENSOR_RATE_ONCHANGE, 0);
        osEventSubscribe(mTask.taskId, EVT_SENSOR_ANY_MOTION);
    } else {
        sensorRelease(mTask.taskId, mTask.anyMotionHandle);
        osEventUnsubscribe(mTask.taskId, EVT_SENSOR_ANY_MOTION);
    }
}

static void configNoMotion(bool on) {
    if (on) {
        sensorRequest(mTask.taskId, mTask.noMotionHandle, SENSOR_RATE_ONCHANGE, 0);
        osEventSubscribe(mTask.taskId, EVT_SENSOR_NO_MOTION);
    } else {
        sensorRelease(mTask.taskId, mTask.noMotionHandle);
        osEventUnsubscribe(mTask.taskId, EVT_SENSOR_NO_MOTION);
    }
}

static void configAccel(bool on) {
    if (on) {
        sensorRequest(mTask.taskId, mTask.accelHandle, ACCEL_MIN_RATE,
                      ACCEL_MAX_LATENCY);
        osEventSubscribe(mTask.taskId, EVT_SENSOR_ACCEL);
    } else {
        sensorRelease(mTask.taskId, mTask.accelHandle);
        osEventUnsubscribe(mTask.taskId, EVT_SENSOR_ACCEL);
    }

}

// *****************************************************************************

static const struct SensorInfo mSi =
{
    .sensorName = "Tilt Detection",
    .sensorType = SENS_TYPE_TILT,
    .numAxis = NUM_AXIS_EMBEDDED,
    .interrupt = NANOHUB_INT_WAKEUP,
    .minSamples = 20
};

static bool tiltDetectionPower(bool on, void *cookie)
{
    if (on) {
        configAnyMotion(true);
        mTask.taskState = STATE_AWAITING_ANY_MOTION;
    } else {
        configAnyMotion(false);
        configNoMotion(false);
        configAccel(false);
        mTask.taskState = STATE_DISABLED;
    }

    sensorSignalInternalEvt(mTask.handle, SENSOR_INTERNAL_EVT_POWER_STATE_CHG,
                            on, 0);
    return true;
}

static bool tiltDetectionSetRate(uint32_t rate, uint64_t latency, void *cookie)
{
    sensorSignalInternalEvt(mTask.handle, SENSOR_INTERNAL_EVT_RATE_CHG, rate,
                            latency);
    return true;
}

static bool tiltDetectionFirmwareUpload(void *cookie)
{
    sensorSignalInternalEvt(mTask.handle, SENSOR_INTERNAL_EVT_FW_STATE_CHG,
            1, 0);
    return true;
}

static bool tiltDetectionFlush(void *cookie)
{
    return osEnqueueEvt(sensorGetMyEventType(SENS_TYPE_TILT),
                        SENSOR_DATA_EVENT_FLUSH, NULL);
}

static void tiltDetectionHandleEvent(uint32_t evtType, const void* evtData)
{
    if (evtData == SENSOR_DATA_EVENT_FLUSH)
        return;

    switch (evtType) {
    case EVT_APP_START:
        osEventUnsubscribe(mTask.taskId, EVT_APP_START);
        sensorFind(SENS_TYPE_ANY_MOTION, 0, &mTask.anyMotionHandle);
        sensorFind(SENS_TYPE_NO_MOTION, 0, &mTask.noMotionHandle);
        sensorFind(SENS_TYPE_ACCEL, 0, &mTask.accelHandle);
        break;

    case EVT_SENSOR_ANY_MOTION:
        if (mTask.taskState == STATE_AWAITING_ANY_MOTION) {
            configAnyMotion(false);
            configNoMotion(true);
            configAccel(true);

            mTask.taskState = STATE_AWAITING_TILT;
        }
        break;

    case EVT_SENSOR_NO_MOTION:
        if (mTask.taskState == STATE_AWAITING_TILT) {
            configNoMotion(false);
            configAccel(false);
            configAnyMotion(true);

            mTask.taskState = STATE_AWAITING_ANY_MOTION;
        }
        break;

    case EVT_SENSOR_ACCEL:
        if (mTask.taskState == STATE_AWAITING_TILT) {
            if (algoUpdate((struct TripleAxisDataEvent *)evtData)) {
                union EmbeddedDataPoint sample;
                sample.idata = 1;
                osEnqueueEvt(sensorGetMyEventType(SENS_TYPE_TILT), sample.vptr, NULL);
            }
        }
        break;
    }
}

static const struct SensorOps mSops =
{
    .sensorPower = tiltDetectionPower,
    .sensorFirmwareUpload = tiltDetectionFirmwareUpload,
    .sensorSetRate = tiltDetectionSetRate,
    .sensorFlush = tiltDetectionFlush,
};

static bool tiltDetectionStart(uint32_t taskId)
{
    mTask.taskId = taskId;
    mTask.handle = sensorRegister(&mSi, &mSops, NULL, true);
    algoInit();
    osEventSubscribe(taskId, EVT_APP_START);
    return true;
}

static void tiltDetectionEnd()
{
}

INTERNAL_APP_INIT(
        APP_ID_MAKE(NANOHUB_VENDOR_GOOGLE, 8),
        TILT_APP_VERSION,
        tiltDetectionStart,
        tiltDetectionEnd,
        tiltDetectionHandleEvent);
