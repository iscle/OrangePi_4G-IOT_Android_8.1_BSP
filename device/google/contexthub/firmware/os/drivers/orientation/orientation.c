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
#include <floatRt.h>

#include <seos.h>

#include <nanohub_math.h>
#include <algos/fusion.h>
#include <sensors.h>
#include <variant/sensType.h>
#include <limits.h>
#include <slab.h>

#define ORIENTATION_APP_VERSION 1

#define MAX_NUM_COMMS_EVENT_SAMPLES 15 // at most 15 samples can fit in one comms_event
#define NUM_COMMS_EVENTS_IN_FIFO    2  // This controls how often the hub needs to wake up
                                       // in batching

// needs to be greater than max raw sensor rate ratio
#define FIFO_DEPTH                  (NUM_COMMS_EVENTS_IN_FIFO * MAX_NUM_COMMS_EVENT_SAMPLES)

/*
 * FIFO_MARGIN: max raw sensor rate ratio is 8:1.
 * If 2 batchs of high rate data comes before 1 low rate data, there can be at max 15 samples left
 * in the FIFO
 */
#define FIFO_MARGIN                 15
#define MAX_NUM_SAMPLES             (FIFO_MARGIN + FIFO_DEPTH) // actual input sample fifo depth
#define EVT_SENSOR_ACC_DATA_RDY     sensorGetMyEventType(SENS_TYPE_ACCEL)
#define EVT_SENSOR_GYR_DATA_RDY     sensorGetMyEventType(SENS_TYPE_GYRO)
#define EVT_SENSOR_MAG_DATA_RDY     sensorGetMyEventType(SENS_TYPE_MAG)
#define EVT_SENSOR_MAG_BIAS         sensorGetMyEventType(SENS_TYPE_MAG_BIAS)

#define kGravityEarth               9.80665f
#define kRad2deg                    (180.0f / M_PI)
#define MIN_GYRO_RATE_HZ            SENSOR_HZ(100.0f)
#define MAX_MAG_RATE_HZ             SENSOR_HZ(50.0f)

enum
{
    FUSION_FLAG_ENABLED             = 0x01,
    FUSION_FLAG_INITIALIZED         = 0x08,
    FUSION_FLAG_GAME_ENABLED        = 0x10,
    FUSION_FLAG_GAME_INITIALIZED    = 0x20
};

enum RawSensorType
{
    ACC,
    GYR,
    MAG,
    NUM_OF_RAW_SENSOR
};

enum FusionSensorType
{
    ORIENT,
    GRAVITY,
    GEOMAG,
    LINEAR,
    GAME,
    ROTAT,
    NUM_OF_FUSION_SENSOR
};


struct FusionSensorSample {
    uint64_t time;
    float x, y, z;
};

struct FusionSensor {
    uint32_t handle;
    struct TripleAxisDataEvent *ev;
    uint64_t prev_time;
    uint64_t latency;
    uint32_t rate;
    bool active;
    bool use_gyro_data;
    bool use_mag_data;
    uint8_t idx;
};

struct FusionTask {
    uint32_t tid;
    uint32_t accelHandle;
    uint32_t gyroHandle;
    uint32_t magHandle;

    struct Fusion fusion;
    struct Fusion game;

    struct FusionSensor sensors[NUM_OF_FUSION_SENSOR];
    struct FusionSensorSample samples[NUM_OF_RAW_SENSOR][MAX_NUM_SAMPLES];
    size_t sample_indices[NUM_OF_RAW_SENSOR];
    size_t sample_counts[NUM_OF_RAW_SENSOR];
    uint32_t counters[NUM_OF_RAW_SENSOR];
    uint64_t ResamplePeriodNs[NUM_OF_RAW_SENSOR];
    uint64_t last_time[NUM_OF_RAW_SENSOR];
    struct TripleAxisDataPoint last_sample[NUM_OF_RAW_SENSOR];

    uint32_t flags;

    uint32_t raw_sensor_rate[NUM_OF_RAW_SENSOR];
    uint64_t raw_sensor_latency;

    uint8_t accel_client_cnt;
    uint8_t gyro_client_cnt;
    uint8_t mag_client_cnt;
};

static uint32_t FusionRates[] = {
    SENSOR_HZ(12.5f),
    SENSOR_HZ(25.0f),
    SENSOR_HZ(50.0f),
    SENSOR_HZ(100.0f),
    SENSOR_HZ(200.0f),
    0,
};

//should match "supported rates in length" and be the timer length for that rate in nanosecs
static const uint64_t rateTimerVals[] = {
    1000000000ULL / 12.5f,
    1000000000ULL / 25,
    1000000000ULL / 50,
    1000000000ULL / 100,
    1000000000ULL / 200,
};

static struct FusionTask mTask;

#define DEC_INFO_RATE(name, rates, type, axis, inter, samples) \
    .sensorName = name, \
    .supportedRates = rates, \
    .sensorType = type, \
    .numAxis = axis, \
    .interrupt = inter, \
    .minSamples = samples

static const struct SensorInfo mSi[NUM_OF_FUSION_SENSOR] =
{
    { DEC_INFO_RATE("Orientation", FusionRates, SENS_TYPE_ORIENTATION, NUM_AXIS_THREE,
            NANOHUB_INT_NONWAKEUP, 20) },
    { DEC_INFO_RATE("Gravity", FusionRates, SENS_TYPE_GRAVITY, NUM_AXIS_THREE,
            NANOHUB_INT_NONWAKEUP, 20) },
    { DEC_INFO_RATE("Geomagnetic Rotation Vector", FusionRates, SENS_TYPE_GEO_MAG_ROT_VEC,
            NUM_AXIS_THREE, NANOHUB_INT_NONWAKEUP, 20) },
    { DEC_INFO_RATE("Linear Acceleration", FusionRates, SENS_TYPE_LINEAR_ACCEL, NUM_AXIS_THREE,
            NANOHUB_INT_NONWAKEUP, 20) },
    { DEC_INFO_RATE("Game Rotation Vector", FusionRates, SENS_TYPE_GAME_ROT_VECTOR, NUM_AXIS_THREE,
            NANOHUB_INT_NONWAKEUP, 300) },
    { DEC_INFO_RATE("Rotation Vector", FusionRates, SENS_TYPE_ROTATION_VECTOR, NUM_AXIS_THREE,
            NANOHUB_INT_NONWAKEUP, 20) },
};

static struct SlabAllocator *mDataSlab;

static void dataEvtFree(void *ptr)
{
    slabAllocatorFree(mDataSlab, ptr);
}

static void fillSamples(struct TripleAxisDataEvent *ev, enum RawSensorType index)
{
    bool bad_timestamp;
    size_t i, w, n, num_samples;
    struct TripleAxisDataPoint *curr_sample, *next_sample;
    uint32_t counter;
    uint64_t ResamplePeriodNs, curr_time, next_time;
    uint64_t sample_spacing_ns;
    float weight_next;

    if (index == GYR && mTask.gyro_client_cnt == 0) {
        return;
    }
    if (index == MAG && mTask.mag_client_cnt == 0) {
        return;
    }

    n = mTask.sample_counts[index];
    i = mTask.sample_indices[index];
    counter = mTask.counters[index];
    ResamplePeriodNs = mTask.ResamplePeriodNs[index];
    w = (mTask.sample_indices[index] + n) % MAX_NUM_SAMPLES;

    // check if this sensor was used before
    if (mTask.last_time[index] == ULONG_LONG_MAX) {
        curr_sample = ev->samples;
        next_sample = curr_sample + 1;
        num_samples = ev->samples[0].firstSample.numSamples;
        curr_time = ev->referenceTime;
    } else {
        curr_sample = &mTask.last_sample[index];
        next_sample = ev->samples;
        num_samples = ev->samples[0].firstSample.numSamples + 1;
        curr_time = mTask.last_time[index];
    }

    while (num_samples > 1) {

        if (next_sample == ev->samples)
            next_time = ev->referenceTime;
        else
            next_time = curr_time + next_sample->deltaTime;

        // error handling for non-chronological accel timestamps
        sample_spacing_ns = (next_time > curr_time) ?  (next_time - curr_time) : 0;

        // This can happen during sensor config changes
        bad_timestamp = (sample_spacing_ns > 10 * ResamplePeriodNs);

        // Check to see if we need to move the interpolation window or
        // interpolate
        if ((counter >= sample_spacing_ns) || bad_timestamp) {
            num_samples--;
            counter -= (bad_timestamp ? counter : sample_spacing_ns);
            curr_sample = next_sample;
            next_sample++;

            curr_time = next_time;
        } else {
            weight_next = (float)counter / floatFromUint64(sample_spacing_ns);

            mTask.samples[index][w].x = curr_sample->x + weight_next *
                (next_sample->x - curr_sample->x);
            mTask.samples[index][w].y = curr_sample->y + weight_next *
                (next_sample->y - curr_sample->y);
            mTask.samples[index][w].z = curr_sample->z + weight_next *
                (next_sample->z - curr_sample->z);
            mTask.samples[index][w].time = curr_time + counter;

            // Move the read index when buffer is full
            if (++n > MAX_NUM_SAMPLES) {
                n = MAX_NUM_SAMPLES;

                if (++i == MAX_NUM_SAMPLES) {
                    i = 0;
                }
            }

            // Reset the write index
            if (++w == MAX_NUM_SAMPLES) {
                w = 0;
            }

            // Move to the next resample
            counter += ResamplePeriodNs;
        }
    }

    mTask.sample_counts[index] = n;
    mTask.sample_indices[index] = i;
    mTask.counters[index] = counter;
    mTask.last_sample[index] = *curr_sample;
    mTask.last_time[index] = curr_time;
}

static bool allocateDataEvt(struct FusionSensor *mSensor, uint64_t time)
{
    mSensor->ev = slabAllocatorAlloc(mDataSlab);
    if (mSensor->ev == NULL) {
        // slab allocation failed, need to stop draining raw samples for now.
        osLog(LOG_INFO, "ORIENTATION: slabAllocatorAlloc() Failed\n");
        return false;
    }

    // delta time for the first sample is sample count
    memset(&mSensor->ev->samples[0].firstSample, 0x00, sizeof(struct SensorFirstSample));
    mSensor->ev->referenceTime = time;
    mSensor->prev_time = time;

    return true;
}

// returns false if addSample() fails
static bool addSample(struct FusionSensor *mSensor, uint64_t time, float x, float y, float z)
{
    struct TripleAxisDataPoint *sample;

    // Bypass processing this accel sample.
    // This is needed after recovering from a slab shortage.
    if (mSensor->prev_time == time) {
        osLog(LOG_INFO, "Accel sample has been processed by fusion sensor %d\n",
              mSensor->idx);
        return true;
    }

    if (mSensor->ev == NULL) {
        if (!allocateDataEvt(mSensor, time))
            return false;
    }

    if (mSensor->ev->samples[0].firstSample.numSamples >= MAX_NUM_COMMS_EVENT_SAMPLES) {
        osLog(LOG_ERROR, "ORIENTATION: BAD_INDEX\n");
        return false;
    }

    sample = &mSensor->ev->samples[mSensor->ev->samples[0].firstSample.numSamples++];

    if (mSensor->ev->samples[0].firstSample.numSamples > 1) {
        sample->deltaTime = time > mSensor->prev_time ? (time - mSensor->prev_time) : 0;
        mSensor->prev_time = time;
    }

    sample->x = x;
    sample->y = y;
    sample->z = z;

    if (mSensor->ev->samples[0].firstSample.numSamples == MAX_NUM_COMMS_EVENT_SAMPLES) {
        osEnqueueEvtOrFree(
                EVENT_TYPE_BIT_DISCARDABLE | sensorGetMyEventType(mSi[mSensor->idx].sensorType),
                mSensor->ev, dataEvtFree);
        mSensor->ev = NULL;
    }
    return true;
}

// returns false if addSample fails for any fusion sensor
// (most likely due to slab allocation failure)
static bool updateOutput(ssize_t last_accel_sample_index, uint64_t last_sensor_time)
{
    struct Vec4 attitude;
    struct Vec3 g, a;
    struct Mat33 R;  // direction-cosine/rotation matrix, inertial -> device
    bool   rInited;  // indicates if matrix R has been initialzed. for avoiding repeated computation
    bool ret = true;

    if (fusionHasEstimate(&mTask.game)) {
        rInited = false;
        if (mTask.sensors[GAME].active) {
            fusionGetAttitude(&mTask.game, &attitude);
            if (!addSample(&mTask.sensors[GAME],
                    last_sensor_time,
                    attitude.x,
                    attitude.y,
                    attitude.z)) {
                ret = false;
            }
        }

        if (mTask.sensors[GRAVITY].active) {
            fusionGetRotationMatrix(&mTask.game, &R);
            rInited = true;
            initVec3(&g, R.elem[0][2], R.elem[1][2], R.elem[2][2]);
            vec3ScalarMul(&g, kGravityEarth);
            if (!addSample(&mTask.sensors[GRAVITY],
                    last_sensor_time,
                    g.x,
                    g.y,
                    g.z)) {
                ret = false;
            }
        }

        if (last_accel_sample_index >= 0
                && mTask.sensors[LINEAR].active) {
            if (!rInited) {
                fusionGetRotationMatrix(&mTask.game, &R);
            }
            initVec3(&g, R.elem[0][2], R.elem[1][2], R.elem[2][2]);
            vec3ScalarMul(&g, kGravityEarth);
            initVec3(&a,
                    mTask.samples[0][last_accel_sample_index].x,
                    mTask.samples[0][last_accel_sample_index].y,
                    mTask.samples[0][last_accel_sample_index].z);

            if (!addSample(&mTask.sensors[LINEAR],
                    mTask.samples[0][last_accel_sample_index].time,
                    a.x - g.x,
                    a.y - g.y,
                    a.z - g.z)) {
                ret = false;
            }
        }
    }

    if (fusionHasEstimate(&mTask.fusion)) {
        fusionGetAttitude(&mTask.fusion, &attitude);

        if (mTask.sensors[ORIENT].active) {
            fusionGetRotationMatrix(&mTask.fusion, &R);
            // x, y, z = yaw, pitch, roll
            float x = atan2f(-R.elem[0][1], R.elem[0][0]) * kRad2deg;
            float y = atan2f(-R.elem[1][2], R.elem[2][2]) * kRad2deg;
            float z = asinf(R.elem[0][2]) * kRad2deg;

            if (x < 0.0f) {
                x += 360.0f;
            }

            if (!addSample(&mTask.sensors[ORIENT],
                    last_sensor_time,
                    x,
                    y,
                    z)) {
                ret = false;
            }
        }

        if (mTask.sensors[GEOMAG].active) {
            if (!addSample(&mTask.sensors[GEOMAG],
                    last_sensor_time,
                    attitude.x,
                    attitude.y,
                    attitude.z)) {
                ret = false;
            }
        }

        if (mTask.sensors[ROTAT].active) {
            if (!addSample(&mTask.sensors[ROTAT],
                    last_sensor_time,
                    attitude.x,
                    attitude.y,
                    attitude.z)) {
                ret = false;
            }
        }

    }
    return ret;
}

static void drainSamples()
{
    struct Vec3 a, w, m;
    uint64_t a_time, g_time, m_time;
    size_t i = mTask.sample_indices[ACC];
    size_t j = 0;
    size_t k = 0;
    size_t which;
    float dT;
    bool success = true;

    if (mTask.gyro_client_cnt > 0)
        j = mTask.sample_indices[GYR];

    if (mTask.mag_client_cnt > 0)
        k = mTask.sample_indices[MAG];

    // Keep draining raw samples and producing fusion samples only if
    // 1) all raw sensors needed are present (to compare timestamp) and
    // 2) updateOutput() succeeded (no slab shortage)
    // Otherwise, wait till next raw sample event.
    while (mTask.sample_counts[ACC] > 0
            && (!(mTask.gyro_client_cnt > 0) || mTask.sample_counts[GYR] > 0)
            && (!(mTask.mag_client_cnt > 0) || mTask.sample_counts[MAG] > 0)
            && success) {
        a_time = mTask.samples[ACC][i].time;
        g_time = mTask.gyro_client_cnt > 0 ? mTask.samples[GYR][j].time
                            : ULONG_LONG_MAX;
        m_time = mTask.mag_client_cnt > 0 ? mTask.samples[MAG][k].time
                            : ULONG_LONG_MAX;

        // priority with same timestamp: gyro > acc > mag
        if (g_time <= a_time && g_time <= m_time) {
            which = GYR;
        } else if (a_time <= m_time) {
            which = ACC;
        } else {
            which = MAG;
        }

        dT = floatFromUint64(mTask.ResamplePeriodNs[which]) * 1e-9f;
        switch (which) {
        case ACC:
            initVec3(&a, mTask.samples[ACC][i].x, mTask.samples[ACC][i].y, mTask.samples[ACC][i].z);

            if (mTask.flags & FUSION_FLAG_ENABLED)
                fusionHandleAcc(&mTask.fusion, &a, dT);

            if (mTask.flags & FUSION_FLAG_GAME_ENABLED)
                fusionHandleAcc(&mTask.game, &a, dT);

            success = updateOutput(i, mTask.samples[ACC][i].time);

            // Do not remove the accel sample until all active fusion sesnsors
            // successfully updated the output.
            // Fusion sensors that have processed this accel sample will bypass
            // it in addSample().
            if (success) {
                --mTask.sample_counts[ACC];
                if (++i == MAX_NUM_SAMPLES) {
                    i = 0;
                }
            }
            break;
        case GYR:
            initVec3(&w, mTask.samples[GYR][j].x, mTask.samples[GYR][j].y, mTask.samples[GYR][j].z);

            if (mTask.flags & FUSION_FLAG_ENABLED)
                fusionHandleGyro(&mTask.fusion, &w, dT);

            if (mTask.flags & FUSION_FLAG_GAME_ENABLED)
                fusionHandleGyro(&mTask.game, &w, dT);

            --mTask.sample_counts[GYR];
            if (++j == MAX_NUM_SAMPLES)
                j = 0;
            break;
        case MAG:
            initVec3(&m, mTask.samples[MAG][k].x, mTask.samples[MAG][k].y, mTask.samples[MAG][k].z);

            fusionHandleMag(&mTask.fusion, &m, dT);

            --mTask.sample_counts[MAG];
            if (++k == MAX_NUM_SAMPLES)
                k = 0;
            break;
        }
    }

    mTask.sample_indices[ACC] = i;

    if (mTask.gyro_client_cnt > 0)
        mTask.sample_indices[GYR] = j;

    if (mTask.mag_client_cnt > 0)
        mTask.sample_indices[MAG] = k;

    for (i = ORIENT; i < NUM_OF_FUSION_SENSOR; i++) {
        if (mTask.sensors[i].ev != NULL) {
            osEnqueueEvtOrFree(EVENT_TYPE_BIT_DISCARDABLE | sensorGetMyEventType(mSi[i].sensorType),
                               mTask.sensors[i].ev, dataEvtFree);
            mTask.sensors[i].ev = NULL;
        }
    }
}

static void configureFusion()
{
    if (mTask.sensors[ORIENT].active
            || mTask.sensors[ROTAT].active
            || mTask.sensors[GEOMAG].active) {
        mTask.flags |= FUSION_FLAG_ENABLED;
        initFusion(&mTask.fusion,
                (mTask.mag_client_cnt > 0 ? FUSION_USE_MAG : 0) |
                (mTask.gyro_client_cnt > 0 ? FUSION_USE_GYRO : 0) |
                ((mTask.flags & FUSION_FLAG_INITIALIZED) ? 0 : FUSION_REINITIALIZE));
        mTask.flags |= FUSION_FLAG_INITIALIZED;
    } else {
        mTask.flags &= ~FUSION_FLAG_ENABLED;
        mTask.flags &= ~FUSION_FLAG_INITIALIZED;
    }
}

static void configureGame()
{
    if (mTask.sensors[GAME].active || mTask.sensors[GRAVITY].active ||
            mTask.sensors[LINEAR].active) {
        mTask.flags |= FUSION_FLAG_GAME_ENABLED;
        initFusion(&mTask.game, FUSION_USE_GYRO |
                ((mTask.flags & FUSION_FLAG_INITIALIZED) ? 0 : FUSION_REINITIALIZE));
        mTask.flags |= FUSION_FLAG_GAME_INITIALIZED;
    } else {
        mTask.flags &= ~FUSION_FLAG_GAME_ENABLED;
        mTask.flags &= ~FUSION_FLAG_GAME_INITIALIZED;
    }
}

static void fusionSetRateAcc(void)
{
    int i;
    if  (mTask.accelHandle == 0) {
        mTask.sample_counts[ACC] = 0;
        mTask.sample_indices[ACC] = 0;
        mTask.counters[ACC] = 0;
        mTask.last_time[ACC] = ULONG_LONG_MAX;
        for (i = 0; sensorFind(SENS_TYPE_ACCEL, i, &mTask.accelHandle) != NULL; i++) {
            if (sensorRequest(mTask.tid, mTask.accelHandle, mTask.raw_sensor_rate[ACC],
                        mTask.raw_sensor_latency)) {
                osEventSubscribe(mTask.tid, EVT_SENSOR_ACC_DATA_RDY);
                break;
            }
        }
    } else {
        sensorRequestRateChange(mTask.tid, mTask.accelHandle, mTask.raw_sensor_rate[ACC],
                mTask.raw_sensor_latency);
    }
}

static void fusionSetRateGyr(void)
{
    int i;
    if (mTask.gyroHandle == 0) {
        mTask.sample_counts[GYR] = 0;
        mTask.sample_indices[GYR] = 0;
        mTask.counters[GYR] = 0;
        mTask.last_time[GYR] = ULONG_LONG_MAX;
        for (i = 0; sensorFind(SENS_TYPE_GYRO, i, &mTask.gyroHandle) != NULL; i++) {
            if (sensorRequest(mTask.tid, mTask.gyroHandle, mTask.raw_sensor_rate[GYR],
                        mTask.raw_sensor_latency)) {
                osEventSubscribe(mTask.tid, EVT_SENSOR_GYR_DATA_RDY);
                break;
            }
        }
    } else {
        sensorRequestRateChange(mTask.tid, mTask.gyroHandle, mTask.raw_sensor_rate[GYR],
                mTask.raw_sensor_latency);
    }
}

static void fusionSetRateMag(void)
{
    int i;
    if (mTask.magHandle == 0) {
        mTask.sample_counts[MAG] = 0;
        mTask.sample_indices[MAG] = 0;
        mTask.counters[MAG] = 0;
        mTask.last_time[MAG] = ULONG_LONG_MAX;
        for (i = 0; sensorFind(SENS_TYPE_MAG, i, &mTask.magHandle) != NULL; i++) {
            if (sensorRequest(mTask.tid, mTask.magHandle, mTask.raw_sensor_rate[MAG],
                        mTask.raw_sensor_latency)) {
                osEventSubscribe(mTask.tid, EVT_SENSOR_MAG_DATA_RDY);
                osEventSubscribe(mTask.tid, EVT_SENSOR_MAG_BIAS);
                break;
            }
        }
    } else {
        sensorRequestRateChange(mTask.tid, mTask.magHandle, mTask.raw_sensor_rate[MAG],
                mTask.raw_sensor_latency);
    }
}

static bool fusionSetRate(uint32_t rate, uint64_t latency, void *cookie)
{
    struct FusionSensor *mSensor = &mTask.sensors[(int)cookie];
    int i;
    uint32_t max_rate = 0;
    uint32_t gyr_rate, mag_rate;
    uint64_t min_resample_period = ULONG_LONG_MAX;

    mSensor->rate = rate;
    mSensor->latency = latency;

    for (i = ORIENT; i < NUM_OF_FUSION_SENSOR; i++) {
        if (mTask.sensors[i].active) {
            max_rate = max_rate > mTask.sensors[i].rate ? max_rate : mTask.sensors[i].rate;
        }
    }

    if (mTask.accel_client_cnt > 0) {
        mTask.raw_sensor_rate[ACC] = max_rate;
        mTask.ResamplePeriodNs[ACC] = sensorTimerLookupCommon(FusionRates, rateTimerVals, max_rate);
        min_resample_period = mTask.ResamplePeriodNs[ACC] < min_resample_period ?
            mTask.ResamplePeriodNs[ACC] : min_resample_period;
    }

    if (mTask.gyro_client_cnt > 0) {
        gyr_rate = max_rate > MIN_GYRO_RATE_HZ ? max_rate : MIN_GYRO_RATE_HZ;
        mTask.raw_sensor_rate[GYR] = gyr_rate;
        mTask.ResamplePeriodNs[GYR] = sensorTimerLookupCommon(FusionRates, rateTimerVals, gyr_rate);
        min_resample_period = mTask.ResamplePeriodNs[GYR] < min_resample_period ?
            mTask.ResamplePeriodNs[GYR] : min_resample_period;
    }

    if (mTask.mag_client_cnt > 0) {
        mag_rate = max_rate < MAX_MAG_RATE_HZ ? max_rate : MAX_MAG_RATE_HZ;
        mTask.raw_sensor_rate[MAG] = mag_rate;
        mTask.ResamplePeriodNs[MAG] = sensorTimerLookupCommon(FusionRates, rateTimerVals, mag_rate);
        min_resample_period = mTask.ResamplePeriodNs[MAG] < min_resample_period ?
            mTask.ResamplePeriodNs[MAG] : min_resample_period;
    }

    // This guarantees that local raw sensor FIFOs won't overflow.
    mTask.raw_sensor_latency = min_resample_period * (FIFO_DEPTH - 1);

    for (i = ORIENT; i < NUM_OF_FUSION_SENSOR; i++) {
        if (mTask.sensors[i].active) {
            mTask.raw_sensor_latency = mTask.sensors[i].latency < mTask.raw_sensor_latency ?
                mTask.sensors[i].latency : mTask.raw_sensor_latency;
        }
    }

    if (mTask.accel_client_cnt > 0)
        fusionSetRateAcc();
    if (mTask.gyro_client_cnt > 0)
        fusionSetRateGyr();
    if (mTask.mag_client_cnt > 0)
        fusionSetRateMag();
    if (mSensor->rate > 0)
        sensorSignalInternalEvt(mSensor->handle, SENSOR_INTERNAL_EVT_RATE_CHG, rate, latency);

    return true;
}

static bool fusionPower(bool on, void *cookie)
{
    struct FusionSensor *mSensor = &mTask.sensors[(int)cookie];
    int idx;

    mSensor->active = on;
    if (on == false) {
        mTask.accel_client_cnt--;
        if (mSensor->use_gyro_data)
            mTask.gyro_client_cnt--;
        if (mSensor->use_mag_data)
            mTask.mag_client_cnt--;

        // if client_cnt == 0 and Handle == 0, nothing need to be done.
        // if client_cnt > 0 and Handle == 0, something else is turning it on, all will be done.
        if (mTask.accel_client_cnt == 0 && mTask.accelHandle != 0) {
            sensorRelease(mTask.tid, mTask.accelHandle);
            mTask.accelHandle = 0;
            osEventUnsubscribe(mTask.tid, EVT_SENSOR_ACC_DATA_RDY);
        }

        if (mTask.gyro_client_cnt == 0 && mTask.gyroHandle != 0) {
            sensorRelease(mTask.tid, mTask.gyroHandle);
            mTask.gyroHandle = 0;
            osEventUnsubscribe(mTask.tid, EVT_SENSOR_GYR_DATA_RDY);
        }

        if (mTask.mag_client_cnt == 0 && mTask.magHandle != 0) {
            sensorRelease(mTask.tid, mTask.magHandle);
            mTask.magHandle = 0;
            osEventUnsubscribe(mTask.tid, EVT_SENSOR_MAG_DATA_RDY);
        }

        idx = mSensor->idx;
        (void) fusionSetRate(0, ULONG_LONG_MAX, (void *)idx);
    } else {
        mTask.accel_client_cnt++;
        if (mSensor->use_gyro_data)
            mTask.gyro_client_cnt++;
        if (mSensor->use_mag_data)
            mTask.mag_client_cnt++;
    }

    configureFusion();
    configureGame();
    sensorSignalInternalEvt(mSensor->handle, SENSOR_INTERNAL_EVT_POWER_STATE_CHG, on, 0);

    return true;
}

static bool fusionFirmwareUpload(void *cookie)
{
    struct FusionSensor *mSensor = &mTask.sensors[(int)cookie];

    sensorSignalInternalEvt(mSensor->handle, SENSOR_INTERNAL_EVT_FW_STATE_CHG, 1, 0);
    return true;
}

static bool fusionFlush(void *cookie)
{
    struct FusionSensor *mSensor = &mTask.sensors[(int)cookie];
    uint32_t evtType = sensorGetMyEventType(mSi[mSensor->idx].sensorType);

    osEnqueueEvt(evtType, SENSOR_DATA_EVENT_FLUSH, NULL);
    return true;
}

static void fusionHandleEvent(uint32_t evtType, const void* evtData)
{
    struct TripleAxisDataEvent *ev;
    int i;

    if (evtData == SENSOR_DATA_EVENT_FLUSH)
        return;

    switch (evtType) {
    case EVT_APP_START:
        // check for gyro and mag
        osEventUnsubscribe(mTask.tid, EVT_APP_START);
        if (!sensorFind(SENS_TYPE_GYRO, 0, &mTask.gyroHandle)) {
            for (i = ORIENT; i < NUM_OF_FUSION_SENSOR; i++)
                mTask.sensors[i].use_gyro_data = false;
        }
        mTask.gyroHandle = 0;
        if (!sensorFind(SENS_TYPE_MAG, 0, &mTask.magHandle)) {
            for (i = ORIENT; i < NUM_OF_FUSION_SENSOR; i++)
                mTask.sensors[i].use_mag_data = false;
        }
        mTask.magHandle = 0;
        break;
    case EVT_SENSOR_ACC_DATA_RDY:
        ev = (struct TripleAxisDataEvent *)evtData;
        fillSamples(ev, ACC);
        drainSamples();
        break;
    case EVT_SENSOR_GYR_DATA_RDY:
        ev = (struct TripleAxisDataEvent *)evtData;
        fillSamples(ev, GYR);
        drainSamples();
        break;
    case EVT_SENSOR_MAG_BIAS:
        ev = (struct TripleAxisDataEvent *)evtData;
        if (ev->samples[0].firstSample.biasPresent && mTask.flags & FUSION_FLAG_ENABLED) {
            //it is a user initiated mag cal event
            fusionSetMagTrust(&mTask.fusion, MANUAL_MAG_CAL);
        }
        break;
    case EVT_SENSOR_MAG_DATA_RDY:
        ev = (struct TripleAxisDataEvent *)evtData;
        fillSamples(ev, MAG);
        drainSamples();
        break;
    }
}

static const struct SensorOps mSops =
{
    .sensorPower = fusionPower,
    .sensorFirmwareUpload = fusionFirmwareUpload,
    .sensorSetRate = fusionSetRate,
    .sensorFlush = fusionFlush,
};

static bool fusionStart(uint32_t tid)
{
    size_t i, slabSize;

    mTask.tid = tid;
    mTask.flags = 0;

    for (i = 0; i < NUM_OF_RAW_SENSOR; i++) {
         mTask.sample_counts[i] = 0;
         mTask.sample_indices[i] = 0;
    }

    for (i = ORIENT; i < NUM_OF_FUSION_SENSOR; i++) {
        mTask.sensors[i].handle = sensorRegister(&mSi[i], &mSops, (void *)i, true);
        mTask.sensors[i].idx = i;
        mTask.sensors[i].use_gyro_data = true;
        mTask.sensors[i].use_mag_data = true;
    }

    mTask.sensors[GEOMAG].use_gyro_data = false;
    mTask.sensors[GAME].use_mag_data = false;
    mTask.sensors[GRAVITY].use_mag_data = false;
    mTask.sensors[LINEAR].use_mag_data = false;

    mTask.accel_client_cnt = 0;
    mTask.gyro_client_cnt = 0;
    mTask.mag_client_cnt = 0;

    slabSize = sizeof(struct TripleAxisDataEvent)
        + MAX_NUM_COMMS_EVENT_SAMPLES * sizeof(struct TripleAxisDataPoint);

    // worst case 6 output sensors * (N + 1) comms_events
    mDataSlab = slabAllocatorNew(slabSize, 4, 6 * (NUM_COMMS_EVENTS_IN_FIFO + 1));
    if (!mDataSlab) {
        osLog(LOG_ERROR, "ORIENTATION: slabAllocatorNew() FAILED\n");
        return false;
    }

    osEventSubscribe(mTask.tid, EVT_APP_START);

    return true;
}

static void fusionEnd()
{
    mTask.flags &= ~FUSION_FLAG_INITIALIZED;
    mTask.flags &= ~FUSION_FLAG_GAME_INITIALIZED;
    slabAllocatorDestroy(mDataSlab);
}

INTERNAL_APP_INIT(
        APP_ID_MAKE(NANOHUB_VENDOR_GOOGLE, 4),
        ORIENTATION_APP_VERSION,
        fusionStart,
        fusionEnd,
        fusionHandleEvent);
