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

#include <plat/taggedPtr.h>
#include <plat/rtc.h>
#include <cpu/barrier.h>
#include <atomicBitset.h>
#include <inttypes.h>
#include <sensors.h>
#include <atomic.h>
#include <stdio.h>
#include <slab.h>
#include <seos.h>
#include <util.h>

#include <sensors_priv.h>


static struct Sensor mSensors[MAX_REGISTERED_SENSORS];
ATOMIC_BITSET_DECL(mSensorsUsed, MAX_REGISTERED_SENSORS, static);
static struct SlabAllocator *mInternalEvents;
static struct SlabAllocator *mCliSensMatrix;
static uint32_t mNextSensorHandle;
struct SingleAxisDataEvent singleAxisFlush = { .referenceTime = 0 };
struct TripleAxisDataEvent tripleAxisFlush = { .referenceTime = 0 };

static inline uint32_t newSensorHandle()
{
    // FIXME: only let lower 8 bits of counter to the id; should use all 16 bits, but this
    // somehow confuses upper layers; pending investigation
    return (osGetCurrentTid() << 16) | (atomicAdd32bits(&mNextSensorHandle, 1) & 0xFF);
}

bool sensorsInit(void)
{
    atomicBitsetInit(mSensorsUsed, MAX_REGISTERED_SENSORS);

    mInternalEvents = slabAllocatorNew(sizeof(struct SensorsInternalEvent), alignof(struct SensorsInternalEvent), MAX_INTERNAL_EVENTS);
    if (!mInternalEvents)
        return false;

    mCliSensMatrix = slabAllocatorNew(sizeof(struct SensorsClientRequest), alignof(struct SensorsClientRequest), MAX_CLI_SENS_MATRIX_SZ);
    if (mCliSensMatrix)
        return true;

    slabAllocatorDestroy(mInternalEvents);

    return false;
}

struct Sensor* sensorFindByHandle(uint32_t handle)
{
    uint32_t i;

    for (i = 0; i < MAX_REGISTERED_SENSORS; i++)
        if (mSensors[i].handle == handle)
            return mSensors + i;

    return NULL;
}

static struct SensorsClientRequest* sensorClientRequestFind(uint32_t sensorHandle, uint32_t clientTid)
{
    uint32_t i;

    for (i = 0; i < MAX_CLI_SENS_MATRIX_SZ; i++) {
        struct SensorsClientRequest *req = slabAllocatorGetNth(mCliSensMatrix, i);

        if (req && req->handle == sensorHandle && req->clientTid == clientTid)
            return req;
    }

    return NULL;
}

static uint32_t sensorRegisterEx(const struct SensorInfo *si, TaggedPtr callInfo, void *callData, bool initComplete)
{
    int32_t idx = atomicBitsetFindClearAndSet(mSensorsUsed);
    uint32_t handle, i;
    struct Sensor *s;

    /* grab a slot */
    if (idx < 0)
        return 0;

    /* grab a handle:
     * this is safe since nobody else could have "JUST" taken this handle,
     * we'll need to circle around 16 bits before that happens, and have the same TID
     */
    do {
        handle = newSensorHandle();
    } while (!handle || sensorFindByHandle(handle));

    /* fill the struct in and mark it valid (by setting handle) */
    s = mSensors + idx;
    s->si = si;
    s->currentRate = SENSOR_RATE_OFF;
    s->currentLatency = SENSOR_LATENCY_INVALID;
    s->callInfo = callInfo;
    // TODO: is internal app, callinfo is OPS struct; shall we validate it here?
    s->callData = callData;
    s->initComplete = initComplete ? 1 : 0;
    mem_reorder_barrier();
    s->handle = handle;
    s->hasOnchange = 0;
    s->hasOndemand = 0;

    if (si->supportedRates) {
        for (i = 0; si->supportedRates[i]; i++) {
            if (si->supportedRates[i] == SENSOR_RATE_ONCHANGE)
                s->hasOnchange = 1;
            if (si->supportedRates[i] == SENSOR_RATE_ONDEMAND)
                s->hasOndemand = 1;
        }
    }

    return handle;
}

uint32_t sensorRegister(const struct SensorInfo *si, const struct SensorOps *ops, void *callData, bool initComplete)
{
    return sensorRegisterEx(si, taggedPtrMakeFromPtr(ops), callData, initComplete);
}

uint32_t sensorRegisterAsApp(const struct SensorInfo *si, uint32_t unusedTid, void *callData, bool initComplete)
{
    (void)unusedTid;
    return sensorRegisterEx(si, taggedPtrMakeFromUint(0), callData, initComplete);
}

bool sensorRegisterInitComplete(uint32_t handle)
{
    struct Sensor *s = sensorFindByHandle(handle);

    if (!s)
        return false;

    s->initComplete = true;
    mem_reorder_barrier();

    return true;
}

bool sensorUnregister(uint32_t handle)
{
    struct Sensor *s = sensorFindByHandle(handle);

    if (!s)
        return false;

    /* mark as invalid */
    s->handle = 0;
    mem_reorder_barrier();

    /* free struct */
    atomicBitsetClearBit(mSensorsUsed, s - mSensors);

    return true;
}

static void sensorCallFuncPowerEvtFreeF(void* event)
{
    slabAllocatorFree(mInternalEvents, event);
}

#define INVOKE_AS_OWNER_AND_RETURN(func, ...)                       \
{                                                                   \
    if (!func)                                                      \
        return false;                                               \
    uint16_t oldTid = osSetCurrentTid(HANDLE_TO_TID(s->handle));    \
    bool done = func(__VA_ARGS__);                                  \
    osSetCurrentTid(oldTid);                                        \
    return done;                                                    \
}

static bool sensorCallFuncPower(struct Sensor* s, bool on)
{
    if (IS_LOCAL_APP(s)) {
        INVOKE_AS_OWNER_AND_RETURN(LOCAL_APP_OPS(s)->sensorPower, on, s->callData);
    } else {
        struct SensorsInternalEvent *evt = (struct SensorsInternalEvent*)slabAllocatorAlloc(mInternalEvents);

        if (!evt)
            return false;

        evt->externalPowerEvt.on = on;
        evt->externalPowerEvt.callData = s->callData;

        if (osEnqueuePrivateEvt(EVT_APP_SENSOR_POWER, &evt->externalPowerEvt,
            sensorCallFuncPowerEvtFreeF, EXT_APP_TID(s)))
            return true;

        slabAllocatorFree(mInternalEvents, evt);
        return false;
    }
}

// the most common callback goes as a helper function
static bool sensorCallAsOwner(struct Sensor* s, bool (*callback)(void*))
{
    INVOKE_AS_OWNER_AND_RETURN(callback, s->callData);
}

static bool sensorCallFuncFwUpld(struct Sensor* s)
{
    if (IS_LOCAL_APP(s))
        return sensorCallAsOwner(s, LOCAL_APP_OPS(s)->sensorFirmwareUpload);
    else
        return osEnqueuePrivateEvt(EVT_APP_SENSOR_FW_UPLD, s->callData, NULL, EXT_APP_TID(s));
}

static void sensorCallFuncExternalEvtFreeF(void* event)
{
    slabAllocatorFree(mInternalEvents, event);
}

static bool sensorCallFuncSetRate(struct Sensor* s, uint32_t rate, uint64_t latency)
{
    if (IS_LOCAL_APP(s)) {
        INVOKE_AS_OWNER_AND_RETURN(LOCAL_APP_OPS(s)->sensorSetRate, rate, latency, s->callData);
    } else {
        struct SensorsInternalEvent *evt = (struct SensorsInternalEvent*)slabAllocatorAlloc(mInternalEvents);

        if (!evt)
            return false;

        evt->externalSetRateEvt.latency = latency;
        evt->externalSetRateEvt.rate = rate;
        evt->externalSetRateEvt.callData = s->callData;
        if (osEnqueuePrivateEvt(EVT_APP_SENSOR_SET_RATE, &evt->externalSetRateEvt,
            sensorCallFuncExternalEvtFreeF, EXT_APP_TID(s)))
            return true;

        slabAllocatorFree(mInternalEvents, evt);
        return false;
    }
}

static bool sensorCallFuncCalibrate(struct Sensor* s)
{
    if (IS_LOCAL_APP(s))
        return sensorCallAsOwner(s, LOCAL_APP_OPS(s)->sensorCalibrate);
    else
        return osEnqueuePrivateEvt(EVT_APP_SENSOR_CALIBRATE, s->callData, NULL, EXT_APP_TID(s));
}

static bool sensorCallFuncSelfTest(struct Sensor* s)
{
    if (IS_LOCAL_APP(s))
        return sensorCallAsOwner(s, LOCAL_APP_OPS(s)->sensorSelfTest);
    else
        return osEnqueuePrivateEvt(EVT_APP_SENSOR_SELF_TEST, s->callData, NULL, EXT_APP_TID(s));
}

static bool sensorCallFuncFlush(struct Sensor* s)
{
    if (IS_LOCAL_APP(s))
        return sensorCallAsOwner(s, LOCAL_APP_OPS(s)->sensorFlush);
    else
        return osEnqueuePrivateEvt(EVT_APP_SENSOR_FLUSH, s->callData, NULL, EXT_APP_TID(s));
}

static bool sensorCallFuncCfgData(struct Sensor* s, void* cfgData)
{
    if (IS_LOCAL_APP(s)) {
        INVOKE_AS_OWNER_AND_RETURN(LOCAL_APP_OPS(s)->sensorCfgData, cfgData, s->callData);
    } else {
        struct SensorsInternalEvent *evt = (struct SensorsInternalEvent*)slabAllocatorAlloc(mInternalEvents);

        if (!evt)
            return false;

        evt->externalCfgDataEvt.data = cfgData;
        evt->externalCfgDataEvt.callData = s->callData;
        if (osEnqueuePrivateEvt(EVT_APP_SENSOR_CFG_DATA, &evt->externalCfgDataEvt,
            sensorCallFuncExternalEvtFreeF, EXT_APP_TID(s)))
            return true;

        slabAllocatorFree(mInternalEvents, evt);
        return false;
    }
}

static bool sensorCallFuncMarshall(struct Sensor* s, uint32_t evtType, void *evtData, TaggedPtr *evtFreeingInfoP)
{
    if (IS_LOCAL_APP(s)) {
        INVOKE_AS_OWNER_AND_RETURN(LOCAL_APP_OPS(s)->sensorMarshallData, evtType, evtData, evtFreeingInfoP, s->callData);
    } else {
        struct SensorsInternalEvent *evt = (struct SensorsInternalEvent*)slabAllocatorAlloc(mInternalEvents);

        if (!evt)
            return false;

        evt->externalMarshallEvt.origEvtType = evtType;
        evt->externalMarshallEvt.origEvtData = evtData;
        evt->externalMarshallEvt.evtFreeingInfo = *evtFreeingInfoP;
        evt->externalMarshallEvt.callData = s->callData;
        if (osEnqueuePrivateEvt(EVT_APP_SENSOR_MARSHALL, &evt->externalMarshallEvt,
            sensorCallFuncExternalEvtFreeF, EXT_APP_TID(s)))
            return true;

        slabAllocatorFree(mInternalEvents, evt);
        return false;
    }
}

static bool sensorCallFuncTrigger(struct Sensor* s)
{
    if (IS_LOCAL_APP(s))
        return sensorCallAsOwner(s, LOCAL_APP_OPS(s)->sensorTriggerOndemand);
    else
        return osEnqueuePrivateEvt(EVT_APP_SENSOR_TRIGGER, s->callData, NULL, EXT_APP_TID(s));
}

static bool sensorCallFuncSendOneDirectEvt(struct Sensor* s, uint32_t tid)
{
    if (IS_LOCAL_APP(s)) {
        INVOKE_AS_OWNER_AND_RETURN(LOCAL_APP_OPS(s)->sensorSendOneDirectEvt, s->callData, tid);
    } else {
        struct SensorsInternalEvent *evt = (struct SensorsInternalEvent*)slabAllocatorAlloc(mInternalEvents);

        if (!evt)
            return false;

        evt->externalSendDirectEvt.tid = tid;
        evt->externalSendDirectEvt.callData = s->callData;
        if (osEnqueuePrivateEvt(EVT_APP_SENSOR_SEND_ONE_DIR_EVT, &evt->externalSendDirectEvt,
            sensorCallFuncExternalEvtFreeF, EXT_APP_TID(s)))
            return true;

        slabAllocatorFree(mInternalEvents, evt);
    }

    return false;
}

static void sensorReconfig(struct Sensor* s, uint32_t newHwRate, uint64_t newHwLatency)
{
    if (s->currentRate == newHwRate && s->currentLatency == newHwLatency) {
        /* do nothing */
    }
    else if (s->currentRate == SENSOR_RATE_OFF) {
        /* if it was off or is off, tell it to come on */
        if (sensorCallFuncPower(s, true)) {
            s->currentRate = SENSOR_RATE_POWERING_ON;
            s->currentLatency = SENSOR_LATENCY_INVALID;
        }
    }
    else if (s->currentRate == SENSOR_RATE_POWERING_OFF) {
        /* if it was going to be off or is off, tell it to come back on */
        s->currentRate = SENSOR_RATE_POWERING_ON;
        s->currentLatency = SENSOR_LATENCY_INVALID;
    }
    else if (s->currentRate == SENSOR_RATE_POWERING_ON || s->currentRate == SENSOR_RATE_FW_UPLOADING) {
        /* if it is powering on - do nothing - all will be done for us */
    }
    else if (newHwRate > SENSOR_RATE_OFF || newHwLatency < SENSOR_LATENCY_INVALID) {
        /* simple rate change - > do it, there is nothing we can do if this fails, so we ignore the immediate errors :( */
        (void)sensorCallFuncSetRate(s, newHwRate, newHwLatency);
    }
    else {
        /* powering off */
        if (sensorCallFuncPower(s, false)) {
            s->currentRate = SENSOR_RATE_POWERING_OFF;
            s->currentLatency = SENSOR_LATENCY_INVALID;
        }
    }
}

static uint64_t sensorCalcHwLatency(struct Sensor* s)
{
    uint64_t smallestLatency = SENSOR_LATENCY_INVALID;
    uint32_t i;

    for (i = 0; i < MAX_CLI_SENS_MATRIX_SZ; i++) {
        struct SensorsClientRequest *req = slabAllocatorGetNth(mCliSensMatrix, i);

        /* we only care about this sensor's stuff */
        if (!req || req->handle != s->handle)
            continue;

        if (smallestLatency > req->latency)
            smallestLatency = req->latency;
    }

    return smallestLatency;
}

static uint32_t sensorCalcHwRate(struct Sensor* s, uint32_t extraReqedRate, uint32_t removedRate)
{
    bool haveUsers = false, haveOnChange = extraReqedRate == SENSOR_RATE_ONCHANGE;
    uint32_t highestReq = 0;
    uint32_t i;

    if (s->si->supportedRates &&
        ((extraReqedRate == SENSOR_RATE_ONCHANGE && !s->hasOnchange) ||
         (extraReqedRate == SENSOR_RATE_ONDEMAND && !s->hasOndemand))) {
        osLog(LOG_WARN, "Bad rate 0x%08" PRIX32 " for sensor %u", extraReqedRate, s->si->sensorType);
        return SENSOR_RATE_IMPOSSIBLE;
    }

    if (extraReqedRate) {
        haveUsers = true;
        highestReq = (extraReqedRate == SENSOR_RATE_ONDEMAND || extraReqedRate == SENSOR_RATE_ONCHANGE) ? 0 : extraReqedRate;
    }

    for (i = 0; i < MAX_CLI_SENS_MATRIX_SZ; i++) {
        struct SensorsClientRequest *req = slabAllocatorGetNth(mCliSensMatrix, i);

        /* we only care about this sensor's stuff */
        if (!req || req->handle != s->handle)
            continue;

        /* skip an instance of a removed rate if one was given */
        if (req->rate == removedRate) {
            removedRate = SENSOR_RATE_OFF;
            continue;
        }

        haveUsers = true;

        /* we can always do ondemand and if we see an on-change then we already checked and do allow it */
        if (req->rate == SENSOR_RATE_ONDEMAND)
            continue;
        if (req->rate == SENSOR_RATE_ONCHANGE) {
            haveOnChange = true;
            continue;
        }

        if (highestReq < req->rate)
            highestReq = req->rate;
    }

    if (!highestReq) {   /* no requests -> we can definitely do that */
        if (!haveUsers)
            return SENSOR_RATE_OFF;
        else if (haveOnChange)
            return SENSOR_RATE_ONCHANGE;
        else
            return SENSOR_RATE_ONDEMAND;
    }

    for (i = 0; s->si->supportedRates && s->si->supportedRates[i]; i++)
        if (s->si->supportedRates[i] >= highestReq)
            return s->si->supportedRates[i];

    return SENSOR_RATE_IMPOSSIBLE;
}

static void sensorInternalEvtFreeF(void *evtP)
{
    slabAllocatorFree(mInternalEvents, evtP);
}

static void sensorInternalFwStateChanged(void *evtP)
{
    struct SensorsInternalEvent *evt = (struct SensorsInternalEvent*)evtP;
    struct Sensor* s = sensorFindByHandle(evt->handle);

    if (s) {

        if (!evt->value1) {                                       //we failed -> give up
            s->currentRate = SENSOR_RATE_POWERING_OFF;
            s->currentLatency = SENSOR_LATENCY_INVALID;
            sensorCallFuncPower(s, false);
        }
        else if (s->currentRate == SENSOR_RATE_FW_UPLOADING) {    //we're up
            s->currentRate = evt->value1;
            s->currentLatency = evt->value2;
            sensorReconfig(s, sensorCalcHwRate(s, 0, 0), sensorCalcHwLatency(s));
        }
        else if (s->currentRate == SENSOR_RATE_POWERING_OFF) {    //we need to power off
            sensorCallFuncPower(s, false);
        }
    }
    slabAllocatorFree(mInternalEvents, evt);
}

static void sensorInternalPowerStateChanged(void *evtP)
{
    struct SensorsInternalEvent *evt = (struct SensorsInternalEvent*)evtP;
    struct Sensor* s = sensorFindByHandle(evt->handle);

    if (s) {

        if (s->currentRate == SENSOR_RATE_POWERING_ON && evt->value1) {          //we're now on - upload firmware
            s->currentRate = SENSOR_RATE_FW_UPLOADING;
            s->currentLatency = SENSOR_LATENCY_INVALID;
            sensorCallFuncFwUpld(s);
        }
        else if (s->currentRate == SENSOR_RATE_POWERING_OFF && !evt->value1) {   //we're now off
            s->currentRate = SENSOR_RATE_OFF;
            s->currentLatency = SENSOR_LATENCY_INVALID;
            osEnqueueEvtOrFree(sensorGetMyCfgEventType(s->si->sensorType), evt, sensorInternalEvtFreeF);
            return;
        }
        else if (s->currentRate == SENSOR_RATE_POWERING_ON && !evt->value1) {    //we need to power back on
            sensorCallFuncPower(s, true);
        }
        else if (s->currentRate == SENSOR_RATE_POWERING_OFF && evt->value1) {    //we need to power back off
            sensorCallFuncPower(s, false);
        }
    }
    slabAllocatorFree(mInternalEvents, evt);
}

static void sensorInternalRateChanged(void *evtP)
{
    struct SensorsInternalEvent *evt = (struct SensorsInternalEvent*)evtP;
    struct Sensor* s = sensorFindByHandle(evt->handle);

    /* If the current rate is a state, do not change the rate */
    if (s && s->currentRate != SENSOR_RATE_OFF && s->currentRate < SENSOR_RATE_POWERING_ON) {
        s->currentRate = evt->value1;
        s->currentLatency = evt->value2;
        sensorReconfig(s, sensorCalcHwRate(s, 0, 0), sensorCalcHwLatency(s));
        osEnqueueEvtOrFree(sensorGetMyCfgEventType(s->si->sensorType), evt, sensorInternalEvtFreeF);
    } else {
        slabAllocatorFree(mInternalEvents, evt);
    }
}

bool sensorSignalInternalEvt(uint32_t handle, uint32_t intEvtNum, uint32_t value1, uint64_t value2)
{
    static const OsDeferCbkF internalEventCallbacks[] = {
        [SENSOR_INTERNAL_EVT_POWER_STATE_CHG] = sensorInternalPowerStateChanged,
        [SENSOR_INTERNAL_EVT_FW_STATE_CHG] = sensorInternalFwStateChanged,
        [SENSOR_INTERNAL_EVT_RATE_CHG] = sensorInternalRateChanged,
    };
    struct SensorsInternalEvent *evt = (struct SensorsInternalEvent*)slabAllocatorAlloc(mInternalEvents);

    if (!evt)
        return false;

    evt->handle = handle;
    evt->value1 = value1;
    evt->value2 = value2;

    if (osDefer(internalEventCallbacks[intEvtNum], evt, false))
        return true;

    slabAllocatorFree(mInternalEvents, evt);
    return false;
}

const struct SensorInfo* sensorFind(uint32_t sensorType, uint32_t idx, uint32_t *handleP)
{
    uint32_t i;

    for (i = 0; i < MAX_REGISTERED_SENSORS; i++) {
        if (mSensors[i].handle && mSensors[i].si->sensorType == sensorType && !idx--) {
            if (handleP)
                *handleP = mSensors[i].handle;
            return mSensors[i].si;
        }
    }

    return NULL;
}

static bool sensorAddRequestor(uint32_t sensorHandle, uint32_t clientTid, uint32_t rate, uint64_t latency)
{
    struct SensorsClientRequest *req = slabAllocatorAlloc(mCliSensMatrix);

    if (!req)
        return false;

    req->handle = sensorHandle;
    req->clientTid = clientTid;
    mem_reorder_barrier();
    req->rate = rate;
    req->latency = latency;

    return true;
}

static bool sensorGetCurRequestorRate(uint32_t sensorHandle, uint32_t clientTid, uint32_t *rateP, uint64_t *latencyP)
{
    struct SensorsClientRequest *req = sensorClientRequestFind(sensorHandle, clientTid);

    if (req) {
        if (rateP)
            *rateP = req->rate;
        if (*latencyP)
            *latencyP = req->latency;
        return true;
    } else {
        return false;
    }
}

static bool sensorAmendRequestor(uint32_t sensorHandle, uint32_t clientTid, uint32_t newRate, uint64_t newLatency)
{
    struct SensorsClientRequest *req = sensorClientRequestFind(sensorHandle, clientTid);

    if (req) {
        req->rate = newRate;
        req->latency = newLatency;
        return true;
    } else {
        return false;
    }
}

static bool sensorDeleteRequestor(uint32_t sensorHandle, uint32_t clientTid)
{
    struct SensorsClientRequest *req = sensorClientRequestFind(sensorHandle, clientTid);

    if (req) {
        req->rate = SENSOR_RATE_OFF;
        req->latency = SENSOR_LATENCY_INVALID;
        req->clientTid = 0;
        req->handle = 0;
        mem_reorder_barrier();
        slabAllocatorFree(mCliSensMatrix, req);
        return true;
    } else {
        return false;
    }
}

bool sensorRequest(uint32_t unusedTid, uint32_t sensorHandle, uint32_t rate, uint64_t latency)
{
    struct Sensor* s = sensorFindByHandle(sensorHandle);
    uint32_t newSensorRate;
    uint64_t samplingPeriod;
    uint32_t clientTid;

    (void)unusedTid;

    if (!s || !s->initComplete)
        return false;

    clientTid = osGetCurrentTid();

    /* verify the rate is possible */
    newSensorRate = sensorCalcHwRate(s, rate, 0);
    if (newSensorRate == SENSOR_RATE_IMPOSSIBLE)
        return false;

    /* the latency should be lower bounded by sampling period */
    samplingPeriod = ((uint64_t)(1000000000 / rate)) << 10;
    latency = latency > samplingPeriod ? latency : samplingPeriod;

    /* record the request */
    if (!sensorAddRequestor(sensorHandle, clientTid, rate, latency))
        return false;

    /* update actual sensor if needed */
    sensorReconfig(s, newSensorRate, sensorCalcHwLatency(s));

    /* if onchange request, ask sensor to send last state */
    if (s->hasOnchange && !sensorCallFuncSendOneDirectEvt(s, clientTid))
        osLog(LOG_WARN, "Cannot send last state for onchange sensor: enqueue fail");

    return true;
}

bool sensorRequestRateChange(uint32_t unusedTid, uint32_t sensorHandle, uint32_t newRate, uint64_t newLatency)
{
    struct Sensor* s = sensorFindByHandle(sensorHandle);
    uint32_t oldRate, newSensorRate;
    uint64_t oldLatency, samplingPeriod;
    uint32_t clientTid;

    (void)unusedTid;

    if (!s)
        return false;

    clientTid = osGetCurrentTid();
    /* get current rate */
    if (!sensorGetCurRequestorRate(sensorHandle, clientTid, &oldRate, &oldLatency))
        return false;

    /* verify the new rate is possible given all other ongoing requests */
    newSensorRate = sensorCalcHwRate(s, newRate, oldRate);
    if (newSensorRate == SENSOR_RATE_IMPOSSIBLE)
        return false;

    /* the latency should be lower bounded by sampling period */
    samplingPeriod = ((uint64_t)(1000000000 / newRate)) << 10;
    newLatency = newLatency > samplingPeriod ? newLatency : samplingPeriod;

    /* record the request */
    if (!sensorAmendRequestor(sensorHandle, clientTid, newRate, newLatency))
        return false;

    /* update actual sensor if needed */
    sensorReconfig(s, newSensorRate, sensorCalcHwLatency(s));
    return true;
}

bool sensorRelease(uint32_t unusedTid, uint32_t sensorHandle)
{
    struct Sensor* s = sensorFindByHandle(sensorHandle);

    (void) unusedTid;

    if (!s)
        return false;

    /* record the request */
    if (!sensorDeleteRequestor(sensorHandle, osGetCurrentTid()))
        return false;

    /* update actual sensor if needed */
    sensorReconfig(s, sensorCalcHwRate(s, 0, 0), sensorCalcHwLatency(s));
    return true;
}

uint32_t sensorFreeAll(uint32_t clientTid)
{
    int i;
    uint16_t count1 = 0, count2 = 0;
    struct Sensor *s;

    for (i = 0; i < MAX_REGISTERED_SENSORS; i++) {
        if (mSensors[i].handle) {
            s = mSensors + i;
            if (sensorDeleteRequestor(s->handle, clientTid)) {
                sensorReconfig(s, sensorCalcHwRate(s, 0, 0), sensorCalcHwLatency(s));
                count1 ++;
            }
            if (HANDLE_TO_TID(s->handle) == clientTid) {
                sensorUnregister(s->handle);
                count2 ++;
            }
        }
    }

    return (count1 << 16) | count2;
}

bool sensorTriggerOndemand(uint32_t unusedTid, uint32_t sensorHandle)
{
    struct Sensor* s = sensorFindByHandle(sensorHandle);

    (void)unusedTid;

    if (!s || !s->hasOndemand)
        return false;

    struct SensorsClientRequest *req = sensorClientRequestFind(sensorHandle, osGetCurrentTid());

    if (req)
        return sensorCallFuncTrigger(s);

    // not found -> do not report
    return false;
}

bool sensorFlush(uint32_t sensorHandle)
{
    struct Sensor* s = sensorFindByHandle(sensorHandle);

    if (!s)
        return false;

    return sensorCallFuncFlush(s);
}

bool sensorCalibrate(uint32_t sensorHandle)
{
    struct Sensor* s = sensorFindByHandle(sensorHandle);

    if (!s)
        return false;

    return sensorCallFuncCalibrate(s);
}

bool sensorSelfTest(uint32_t sensorHandle)
{
    struct Sensor* s = sensorFindByHandle(sensorHandle);

    if (!s)
        return false;

    return sensorCallFuncSelfTest(s);
}

bool sensorCfgData(uint32_t sensorHandle, void* cfgData)
{
    struct Sensor* s = sensorFindByHandle(sensorHandle);

    if (!s)
        return false;

    return sensorCallFuncCfgData(s, cfgData);
}

uint32_t sensorGetCurRate(uint32_t sensorHandle)
{
    struct Sensor* s = sensorFindByHandle(sensorHandle);

    return s ? s->currentRate : SENSOR_RATE_OFF;
}

uint64_t sensorGetCurLatency(uint32_t sensorHandle)
{
    struct Sensor* s = sensorFindByHandle(sensorHandle);

    return s ? s->currentLatency : SENSOR_LATENCY_INVALID;
}

uint32_t sensorGetReqRate(uint32_t sensorHandle)
{
    struct SensorsClientRequest *req = sensorClientRequestFind(sensorHandle, osGetCurrentTid());

    return req ? req->rate : SENSOR_RATE_OFF;
}

uint64_t sensorGetReqLatency(uint32_t sensorHandle)
{
    struct SensorsClientRequest *req = sensorClientRequestFind(sensorHandle, osGetCurrentTid());

    return req ? req->latency : SENSOR_LATENCY_INVALID;
}

uint64_t sensorGetTime(void)
{
    return rtcGetTime();
}

bool sensorGetInitComplete(uint32_t sensorHandle)
{
    struct Sensor* s = sensorFindByHandle(sensorHandle);

    return s ? s->initComplete : false;
}

bool sensorMarshallEvent(uint32_t sensorHandle, uint32_t evtType, void *evtData, TaggedPtr *evtFreeingInfoP)
{
    struct Sensor* s = sensorFindByHandle(sensorHandle);

    if (!s)
        return false;

    return sensorCallFuncMarshall(s, evtType, evtData, evtFreeingInfoP);
}
