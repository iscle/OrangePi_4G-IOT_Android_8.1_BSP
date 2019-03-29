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

#include <eventnums.h>
#include <seos.h>
#include <timer.h>
#include <toolchain.h>
#include <crt_priv.h>
#include <string.h>

#include <chre.h>
#include <sensors.h>
#include <syscallDo.h>
#include <hostIntf.h>

#define SENSOR_TYPE(x)      ((x) & 0xFF)

/*
 * Common CHRE App support code
 */

static bool chreappStart(uint32_t tid)
{
    __crt_init();
    return nanoappStart();
}

static void chreappEnd(void)
{
    nanoappEnd();
    __crt_exit();
}

static void initDataHeader(struct chreSensorDataHeader *header, uint64_t timestamp, uint32_t sensorHandle) {
    header->baseTimestamp = timestamp;
    header->sensorHandle = sensorHandle;
    header->readingCount = 1;
    header->reserved[0] = header->reserved[1] = 0;
}

static void processTripleAxisData(const struct TripleAxisDataEvent *src, uint32_t sensorHandle, uint8_t sensorType)
{
    int i;
    struct chreSensorThreeAxisData three;

    initDataHeader(&three.header, src->referenceTime, sensorHandle);
    three.readings[0].timestampDelta = 0;

    for (i=0; i<src->samples[0].firstSample.numSamples; i++) {
        if (i > 0)
            three.header.baseTimestamp += src->samples[i].deltaTime;
        three.readings[0].x = src->samples[i].x;
        three.readings[0].y = src->samples[i].y;
        three.readings[0].z = src->samples[i].z;

        nanoappHandleEvent(CHRE_INSTANCE_ID, CHRE_EVENT_SENSOR_DATA_EVENT_BASE | sensorType, &three);
    }
}

static void processSingleAxisData(const struct SingleAxisDataEvent *src, uint32_t sensorHandle, uint8_t sensorType)
{
    int i;

    switch (sensorType) {
    case CHRE_SENSOR_TYPE_INSTANT_MOTION_DETECT:
    case CHRE_SENSOR_TYPE_STATIONARY_DETECT: {
        struct chreSensorOccurrenceData occ;

        initDataHeader(&occ.header, src->referenceTime, sensorHandle);
        occ.readings[0].timestampDelta = 0;

        for (i=0; i<src->samples[0].firstSample.numSamples; i++) {
            if (i > 0)
                occ.header.baseTimestamp += src->samples[i].deltaTime;

            nanoappHandleEvent(CHRE_INSTANCE_ID, CHRE_EVENT_SENSOR_DATA_EVENT_BASE | sensorType, &occ);
        }
        break;
    }
    case CHRE_SENSOR_TYPE_LIGHT:
    case CHRE_SENSOR_TYPE_PRESSURE: {
        struct chreSensorFloatData flt;

        initDataHeader(&flt.header, src->referenceTime, sensorHandle);
        flt.readings[0].timestampDelta = 0;

        for (i=0; i<src->samples[0].firstSample.numSamples; i++) {
            if (i > 0)
                flt.header.baseTimestamp += src->samples[i].deltaTime;
            flt.readings[0].value = src->samples[i].fdata;

            nanoappHandleEvent(CHRE_INSTANCE_ID, CHRE_EVENT_SENSOR_DATA_EVENT_BASE | sensorType, &flt);
        }
        break;
    }
    case CHRE_SENSOR_TYPE_PROXIMITY: {
        struct chreSensorByteData byte;

        initDataHeader(&byte.header, src->referenceTime, sensorHandle);
        byte.readings[0].timestampDelta = 0;

        for (i=0; i<src->samples[0].firstSample.numSamples; i++) {
            if (i > 0)
                byte.header.baseTimestamp += src->samples[i].deltaTime;
            byte.readings[0].isNear = src->samples[i].fdata == 0.0f;
            byte.readings[0].invalid = false;
            byte.readings[0].padding0 = 0;

            nanoappHandleEvent(CHRE_INSTANCE_ID, CHRE_EVENT_SENSOR_DATA_EVENT_BASE | sensorType, &byte);
        }
        break;
    }
    }
}

static void processEmbeddedData(const void *src, uint32_t sensorHandle, uint8_t sensorType)
{
    union EmbeddedDataPoint data = (union EmbeddedDataPoint)((void *)src);

    switch (sensorType) {
    case CHRE_SENSOR_TYPE_INSTANT_MOTION_DETECT:
    case CHRE_SENSOR_TYPE_STATIONARY_DETECT: {
        struct chreSensorOccurrenceData occ;

        initDataHeader(&occ.header, eOsSensorGetTime(), sensorHandle);
        occ.readings[0].timestampDelta = 0;

        nanoappHandleEvent(CHRE_INSTANCE_ID, CHRE_EVENT_SENSOR_DATA_EVENT_BASE | sensorType, &occ);
        break;
    }
    case CHRE_SENSOR_TYPE_LIGHT:
    case CHRE_SENSOR_TYPE_PRESSURE: {
        struct chreSensorFloatData flt;

        initDataHeader(&flt.header, eOsSensorGetTime(), sensorHandle);
        flt.readings[0].timestampDelta = 0;
        flt.readings[0].value = data.fdata;

        nanoappHandleEvent(CHRE_INSTANCE_ID, CHRE_EVENT_SENSOR_DATA_EVENT_BASE | sensorType, &flt);
        break;
    }
    case CHRE_SENSOR_TYPE_PROXIMITY: {
        struct chreSensorByteData byte;

        initDataHeader(&byte.header, eOsSensorGetTime(), sensorHandle);
        byte.readings[0].timestampDelta = 0;
        byte.readings[0].isNear = data.fdata == 0.0f;
        byte.readings[0].invalid = false;
        byte.readings[0].padding0 = 0;

        nanoappHandleEvent(CHRE_INSTANCE_ID, CHRE_EVENT_SENSOR_DATA_EVENT_BASE | sensorType, &byte);
        break;
    }
    }
}

static void chreappProcessSensorData(uint16_t evt, const void *eventData)
{
    const struct SensorInfo *si;
    uint32_t sensorHandle;

    if (eventData == SENSOR_DATA_EVENT_FLUSH)
        return;

    si = eOsSensorFind(SENSOR_TYPE(evt), 0, &sensorHandle);
    if (si && eOsSensorGetReqRate(sensorHandle)) {
        switch (si->numAxis) {
        case NUM_AXIS_EMBEDDED:
            processEmbeddedData(eventData, sensorHandle, SENSOR_TYPE(evt));
            break;
        case NUM_AXIS_ONE:
            processSingleAxisData(eventData, sensorHandle, SENSOR_TYPE(evt));
            break;
        case NUM_AXIS_THREE:
            processTripleAxisData(eventData, sensorHandle, SENSOR_TYPE(evt));
            break;
        }

        if (SENSOR_TYPE(evt) == CHRE_SENSOR_TYPE_INSTANT_MOTION_DETECT
            || SENSOR_TYPE(evt) == CHRE_SENSOR_TYPE_STATIONARY_DETECT) {
            // one-shot, disable after receiving sample
            chreSensorConfigure(sensorHandle, CHRE_SENSOR_CONFIGURE_MODE_DONE, CHRE_SENSOR_INTERVAL_DEFAULT, CHRE_SENSOR_LATENCY_DEFAULT);
        }
    }
}

static void chreappProcessConfigEvt(uint16_t evt, const void *eventData)
{
    const struct SensorRateChangeEvent *msg = eventData;
    struct chreSensorSamplingStatusEvent change;

    change.sensorHandle = msg->sensorHandle;
    if (!msg->newRate) {
        change.status.enabled = 0;
        change.status.interval = 0;
        change.status.latency = 0;
    } else {
        change.status.enabled = true;
        if (msg->newRate == SENSOR_RATE_ONDEMAND
            || msg->newRate == SENSOR_RATE_ONCHANGE
            || msg->newRate == SENSOR_RATE_ONESHOT)
            change.status.interval = CHRE_SENSOR_INTERVAL_DEFAULT;
        else
            change.status.interval = (UINT32_C(1024000000) / msg->newRate) * UINT64_C(1000);

        if (msg->newLatency == SENSOR_LATENCY_NODATA)
            change.status.latency = CHRE_SENSOR_INTERVAL_DEFAULT;
        else
            change.status.latency = msg->newLatency;
    }

    nanoappHandleEvent(CHRE_INSTANCE_ID, CHRE_EVENT_SENSOR_SAMPLING_CHANGE, &change);
}

static void chreappHandle(uint32_t eventTypeAndTid, const void *eventData)
{
    uint16_t evt = eventTypeAndTid;
    uint16_t srcTid = eventTypeAndTid >> 16;
    const void *data = eventData;

    union EventLocalData {
    struct chreMessageFromHostData msg;
    } u;

    switch(evt) {
    case EVT_APP_TIMER:
        evt = CHRE_EVENT_TIMER;
        data = ((struct TimerEvent *)eventData)->data;
        break;
    case EVT_APP_FROM_HOST:
        srcTid = CHRE_INSTANCE_ID;
        evt = CHRE_EVENT_MESSAGE_FROM_HOST;
        data = &u.msg;
        u.msg.message = (uint8_t*)eventData + 1;
        u.msg.reservedMessageType = 0;
        u.msg.messageSize = *(uint8_t*)eventData;
        break;
    case EVT_APP_FROM_HOST_CHRE:
    {
        const struct NanohubMsgChreHdr *hdr = eventData;
        srcTid = CHRE_INSTANCE_ID;
        evt = CHRE_EVENT_MESSAGE_FROM_HOST;
        data = &u.msg;
        u.msg.message = hdr + 1;
        u.msg.reservedMessageType = hdr->appEvent;
        u.msg.messageSize = hdr->size;
        break;
    }
    case EVT_APP_SENSOR_SELF_TEST:
    case EVT_APP_SENSOR_MARSHALL:
    case EVT_APP_SENSOR_SEND_ONE_DIR_EVT:
    case EVT_APP_SENSOR_CFG_DATA:
    case EVT_APP_SENSOR_CALIBRATE:
    case EVT_APP_SENSOR_TRIGGER:
    case EVT_APP_SENSOR_FLUSH:
    case EVT_APP_SENSOR_SET_RATE:
    case EVT_APP_SENSOR_FW_UPLD:
    case EVT_APP_SENSOR_POWER:
        // sensor events; pass through
        break;
    default:
        // ignore any other system events; OS may send them to any app
        if (evt < EVT_NO_FIRST_USER_EVENT)
            return;
        else if (evt > EVT_NO_FIRST_SENSOR_EVENT && evt < EVT_NO_SENSOR_CONFIG_EVENT) {
            return chreappProcessSensorData(evt, data);
        } else if (evt > EVT_NO_SENSOR_CONFIG_EVENT && evt < EVT_APP_START) {
            return chreappProcessConfigEvt(evt, data);
        }
    }
    nanoappHandleEvent(srcTid, evt, data);
}

// Collect entry points
const struct AppFuncs SET_EXTERNAL_APP_ATTRIBUTES(used, section (".app_init"),visibility("default")) _mAppFuncs = {
    .init   = chreappStart,
    .end    = chreappEnd,
    .handle = chreappHandle,
};

// declare version for compatibility with current runtime
const uint32_t SET_EXTERNAL_APP_VERSION(used, section (".app_version"), visibility("default")) _mAppVer = 0;
