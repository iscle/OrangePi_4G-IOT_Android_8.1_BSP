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

#include <inttypes.h>
#include <stdint.h>
#include <sys/endian.h>
#include <string.h>
#include <alloca.h>

#include <variant/variant.h>
#include <eventnums.h>

#include <plat/pwr.h>

#include <nanohub/crc.h>

#include <platform.h>
#include <cpu.h>
#include <halIntf.h>
#include <hostIntf.h>
#include <hostIntf_priv.h>
#include <nanohubCommand.h>
#include <nanohubPacket.h>
#include <seos.h>
#include <util.h>
#include <atomicBitset.h>
#include <atomic.h>
#include <gpio.h>
#include <apInt.h>
#include <sensors.h>
#include <timer.h>
#include <heap.h>
#include <simpleQ.h>

#define HOSTINTF_MAX_ERR_MSG    8
#define MAX_NUM_BLOCKS          280         /* times 256 = 71680 bytes */
#define MIN_NUM_BLOCKS          10          /* times 256 = 2560 bytes */
#define SENSOR_INIT_DELAY       500000000   /* ns */
#define SENSOR_INIT_ERROR_MAX   4
#define CHECK_LATENCY_TIME      500000000   /* ns */
#define EVT_LATENCY_TIMER       EVT_NO_FIRST_USER_EVENT

static const uint32_t delta_time_multiplier_order = 9;
static const uint32_t delta_time_coarse_mask = ~1;
static const uint32_t delta_time_fine_mask = 1;
static const uint32_t delta_time_rounding = 0x200;      /* 1ul << delta_time_multiplier_order */
static const uint64_t delta_time_max = 0x1FFFFFFFE00;   /* UINT32_MAX << delta_time_multiplier_order */

enum ConfigCmds
{
    CONFIG_CMD_DISABLE      = 0,
    CONFIG_CMD_ENABLE       = 1,
    CONFIG_CMD_FLUSH        = 2,
    CONFIG_CMD_CFG_DATA     = 3,
    CONFIG_CMD_CALIBRATE    = 4,
    CONFIG_CMD_SELF_TEST    = 5,
};

struct ConfigCmd
{
    uint64_t latency;
    uint32_t rate;
    uint8_t sensType;
    uint8_t cmd;
    uint16_t flags;
} __attribute__((packed));

struct ActiveSensor
{
    uint64_t latency;
    uint64_t firstTime;
    uint64_t lastTime;
    struct HostIntfDataBuffer buffer;
    uint32_t rate;
    uint32_t sensorHandle;
    float rawScale;
    uint16_t minSamples;
    uint16_t curSamples;
    uint8_t numAxis;
    uint8_t interrupt;
    uint8_t numSamples;
    uint8_t packetSamples;
    // The sensorType used to report bias samples; normally the same as
    // buffer.sensorType, but in the case of raw, this gets set to the base
    // sensorType matching struct SensorInfo (because the sensor can have a
    // different rawType). Note that this is different than biasType in struct
    // SensorInfo.
    uint8_t biasReportType;
    uint8_t oneshot : 1;
    uint8_t discard : 1;
    uint8_t raw : 1;
    uint8_t reserved : 5;
} __attribute__((packed));

static uint8_t mSensorList[SENS_TYPE_LAST_USER];
static struct SimpleQueue *mOutputQ;
static struct ActiveSensor *mActiveSensorTable;
static uint8_t mNumSensors;
static uint8_t mLastSensor;

static const struct HostIntfComm *mComm;
static bool mBusy;
static uint64_t mRxTimestamp;
static uint8_t mRxBuf[NANOHUB_PACKET_SIZE_MAX];
static size_t mRxSize;
static struct
{
    const struct NanohubCommand *cmd;
    uint32_t seq;
    bool seqMatch;
} mTxRetrans;
static struct
{
    uint8_t pad; // packet header is 10 bytes. + 2 to word align
    uint8_t prePreamble;
    uint8_t buf[NANOHUB_PACKET_SIZE_MAX];
    uint8_t postPreamble;
} mTxBuf;
static struct
{
    uint8_t pad; // packet header is 10 bytes. + 2 to word align
    uint8_t prePreamble;
    uint8_t buf[NANOHUB_PACKET_SIZE_MIN];
    uint8_t postPreamble;
} mTxNakBuf;
static size_t mTxSize;
static uint8_t *mTxBufPtr;
static const struct NanohubCommand *mRxCmd;
ATOMIC_BITSET_DECL(mInterrupt, HOSTINTF_MAX_INTERRUPTS, static);
ATOMIC_BITSET_DECL(mInterruptMask, HOSTINTF_MAX_INTERRUPTS, static);
static uint32_t mInterruptCntWkup, mInterruptCntNonWkup;
static uint32_t mWakeupBlocks, mNonWakeupBlocks, mTotalBlocks;
static uint32_t mHostIntfTid;
static uint32_t mLatencyTimer;
static uint8_t mLatencyCnt;

static uint8_t mRxIdle;
static uint8_t mWakeActive;
static uint8_t mActiveWrite;
static uint8_t mRestartRx;
static uint8_t mIntErrMsgIdx;
static volatile uint32_t mIntErrMsgCnt;

enum hostIntfIntErrReason
{
    HOSTINTF_ERR_PKG_INCOMPELETE = 0,
    HOSTINTF_ERR_PGK_SIZE,
    HOSTINTF_ERR_PKG_PAYLOAD_SIZE,
    HOSTINTF_ERR_PKG_CRC,
    HOSTINTF_ERR_RECEIVE,
    HOSTINTF_ERR_SEND,
    HOSTINTF_ERR_ACK,
    HOSTINTF_ERR_NAK,
    HOSTINTF_ERR_UNKNOWN
};

struct hostIntfIntErrMsg
{
    enum LogLevel level;
    enum hostIntfIntErrReason reason;
    const char *func;
};
static struct hostIntfIntErrMsg mIntErrMsg[HOSTINTF_MAX_ERR_MSG];

static void hostIntfTxPacket(uint32_t reason, uint8_t len, uint32_t seq,
        HostIntfCommCallbackF callback);

static void hostIntfRxDone(size_t rx, int err);
static void hostIntfGenerateAck(void *cookie);

static void hostIntfTxAckDone(size_t tx, int err);
static void hostIntfGenerateResponse(void *cookie);

static void hostIntfTxPayloadDone(size_t tx, int err);

static inline void *hostIntfGetPayload(uint8_t *buf)
{
    struct NanohubPacket *packet = (struct NanohubPacket *)buf;
    return packet->data;
}

static inline uint8_t hostIntfGetPayloadLen(uint8_t *buf)
{
    struct NanohubPacket *packet = (struct NanohubPacket *)buf;
    return packet->len;
}

static inline struct NanohubPacketFooter *hostIntfGetFooter(uint8_t *buf)
{
    struct NanohubPacket *packet = (struct NanohubPacket *)buf;
    return (struct NanohubPacketFooter *)(buf + sizeof(*packet) + packet->len);
}

static inline __le32 hostIntfComputeCrc(uint8_t *buf)
{
    struct NanohubPacket *packet = (struct NanohubPacket *)buf;
    uint32_t crc = crc32(packet, packet->len + sizeof(*packet), CRC_INIT);
    return htole32(crc);
}

static void hostIntfPrintErrMsg(void *cookie)
{
    struct hostIntfIntErrMsg *msg = (struct hostIntfIntErrMsg *)cookie;
    osLog(msg->level, "%s failed with: %d\n", msg->func, msg->reason);
    atomicAdd32bits(&mIntErrMsgCnt, -1UL);
}

static void hostIntfDeferErrLog(enum LogLevel level, enum hostIntfIntErrReason reason, const char *func)
{
    // If the message buffer is full, we drop the newer messages.
    if (atomicRead32bits(&mIntErrMsgCnt) == HOSTINTF_MAX_ERR_MSG)
        return;

    mIntErrMsg[mIntErrMsgIdx].level = level;
    mIntErrMsg[mIntErrMsgIdx].reason = reason;
    mIntErrMsg[mIntErrMsgIdx].func = func;
    if (osDefer(hostIntfPrintErrMsg, &mIntErrMsg[mIntErrMsgIdx], false)) {
        atomicAdd32bits(&mIntErrMsgCnt, 1UL);
        mIntErrMsgIdx = (mIntErrMsgIdx + 1) % HOSTINTF_MAX_ERR_MSG;
    }
}

static inline const struct NanohubCommand *hostIntfFindHandler(uint8_t *buf, size_t size, uint32_t *seq)
{
    struct NanohubPacket *packet = (struct NanohubPacket *)buf;
    struct NanohubPacketFooter *footer;
    __le32 packetCrc;
    uint32_t packetReason;
    const struct NanohubCommand *cmd;

    if (size < NANOHUB_PACKET_SIZE(0)) {
        hostIntfDeferErrLog(LOG_WARN, HOSTINTF_ERR_PKG_INCOMPELETE, __func__);
        return NULL;
    }

    if (size != NANOHUB_PACKET_SIZE(packet->len)) {
        hostIntfDeferErrLog(LOG_WARN, HOSTINTF_ERR_PGK_SIZE, __func__);
        return NULL;
    }

    footer = hostIntfGetFooter(buf);
    packetCrc = hostIntfComputeCrc(buf);
    if (footer->crc != packetCrc) {
        hostIntfDeferErrLog(LOG_WARN, HOSTINTF_ERR_PKG_CRC, __func__);
        return NULL;
    }

    if (mTxRetrans.seq == packet->seq) {
        mTxRetrans.seqMatch = true;
        return mTxRetrans.cmd;
    } else {
        mTxRetrans.seqMatch = false;
    }

    *seq = packet->seq;

    if (mBusy)
        return NULL;

    packetReason = le32toh(packet->reason);

    if ((cmd = nanohubFindCommand(packetReason)) != NULL) {
        if (packet->len < cmd->minDataLen || packet->len > cmd->maxDataLen) {
            hostIntfDeferErrLog(LOG_WARN, HOSTINTF_ERR_PKG_PAYLOAD_SIZE, __func__);
            return NULL;
        }

        return cmd;
    }

    hostIntfDeferErrLog(LOG_WARN, HOSTINTF_ERR_UNKNOWN, __func__);
    return NULL;
}

static void hostIntfTxBuf(int size, uint8_t *buf, HostIntfCommCallbackF callback)
{
    mTxSize = size;
    mTxBufPtr = buf;
    mComm->txPacket(mTxBufPtr, mTxSize, callback);
}

static void hostIntfTxPacket(__le32 reason, uint8_t len, uint32_t seq,
        HostIntfCommCallbackF callback)
{
    struct NanohubPacket *txPacket = (struct NanohubPacket *)(mTxBuf.buf);
    txPacket->reason = reason;
    txPacket->seq = seq;
    txPacket->sync = NANOHUB_SYNC_BYTE;
    txPacket->len = len;

    struct NanohubPacketFooter *txFooter = hostIntfGetFooter(mTxBuf.buf);
    txFooter->crc = hostIntfComputeCrc(mTxBuf.buf);

    // send starting with the prePremable byte
    hostIntfTxBuf(1+NANOHUB_PACKET_SIZE(len), &mTxBuf.prePreamble, callback);
}

static void hostIntfTxNakPacket(__le32 reason, uint32_t seq,
        HostIntfCommCallbackF callback)
{
    struct NanohubPacket *txPacket = (struct NanohubPacket *)(mTxNakBuf.buf);
    txPacket->reason = reason;
    txPacket->seq = seq;
    txPacket->sync = NANOHUB_SYNC_BYTE;
    txPacket->len = 0;

    struct NanohubPacketFooter *txFooter = hostIntfGetFooter(mTxNakBuf.buf);
    txFooter->crc = hostIntfComputeCrc(mTxNakBuf.buf);

    // send starting with the prePremable byte
    hostIntfTxBuf(1+NANOHUB_PACKET_SIZE_MIN, &mTxNakBuf.prePreamble, callback);
}

static inline bool hostIntfTxPacketDone(int err, size_t tx,
        HostIntfCommCallbackF callback)
{
    if (!err && tx < mTxSize) {
        mTxSize -= tx;
        mTxBufPtr += tx;

        mComm->txPacket(mTxBufPtr, mTxSize, callback);
        return false;
    }

    return true;
}

static bool hostIntfRequest(uint32_t tid)
{
    mHostIntfTid = tid;
    atomicBitsetInit(mInterrupt, HOSTINTF_MAX_INTERRUPTS);
    atomicBitsetInit(mInterruptMask, HOSTINTF_MAX_INTERRUPTS);
#ifdef AP_INT_NONWAKEUP
    hostIntfSetInterruptMask(NANOHUB_INT_NONWAKEUP);
#endif
    mTxBuf.prePreamble = NANOHUB_PREAMBLE_BYTE;
    mTxBuf.postPreamble = NANOHUB_PREAMBLE_BYTE;
    mTxNakBuf.prePreamble = NANOHUB_PREAMBLE_BYTE;
    mTxNakBuf.postPreamble = NANOHUB_PREAMBLE_BYTE;

    mComm = platHostIntfInit();
    if (mComm) {
        int err = mComm->request();
        if (!err) {
            nanohubInitCommand();
            mComm->rxPacket(mRxBuf, sizeof(mRxBuf), hostIntfRxDone);
            osEventSubscribe(mHostIntfTid, EVT_APP_START);
            return true;
        }
    }

    return false;
}

void hostIntfRxPacket(bool wakeupActive)
{
    if (mWakeActive) {
        if (atomicXchgByte(&mRxIdle, false)) {
            if (!wakeupActive)
                hostIntfClearInterrupt(NANOHUB_INT_WAKE_COMPLETE);
            mComm->rxPacket(mRxBuf, sizeof(mRxBuf), hostIntfRxDone);
            if (wakeupActive)
                hostIntfSetInterrupt(NANOHUB_INT_WAKE_COMPLETE);
        } else if (atomicReadByte(&mActiveWrite)) {
            atomicWriteByte(&mRestartRx, true);
        } else {
            if (!wakeupActive)
                hostIntfClearInterrupt(NANOHUB_INT_WAKE_COMPLETE);
            else
                hostIntfSetInterrupt(NANOHUB_INT_WAKE_COMPLETE);
        }
    } else if (wakeupActive && !atomicReadByte(&mActiveWrite))
        hostIntfSetInterrupt(NANOHUB_INT_WAKE_COMPLETE);

    mWakeActive = wakeupActive;
}

static void hostIntfRxDone(size_t rx, int err)
{
    mRxTimestamp = sensorGetTime();
    mRxSize = rx;

    if (err != 0) {
        hostIntfDeferErrLog(LOG_ERROR, HOSTINTF_ERR_RECEIVE, __func__);
        return;
    }

    hostIntfGenerateAck(NULL);
}

static void hostIntfTxSendAck(uint32_t resp)
{
    void *txPayload = hostIntfGetPayload(mTxBuf.buf);

    if (resp == NANOHUB_FAST_UNHANDLED_ACK) {
        hostIntfCopyInterrupts(txPayload, HOSTINTF_MAX_INTERRUPTS);
        hostIntfTxPacket(NANOHUB_REASON_ACK, 32, mTxRetrans.seq, hostIntfTxAckDone);
    } else if (resp == NANOHUB_FAST_DONT_ACK) {
        // do nothing. something else will do the ack
    } else {
        hostIntfTxPacket(mRxCmd->reason, resp, mTxRetrans.seq, hostIntfTxPayloadDone);
    }
}

void hostIntfTxAck(void *buffer, uint8_t len)
{
    void *txPayload = hostIntfGetPayload(mTxBuf.buf);

    memcpy(txPayload, buffer, len);

    hostIntfTxSendAck(len);
}

static void hostIntfGenerateAck(void *cookie)
{
    uint32_t seq = 0;
    void *txPayload = hostIntfGetPayload(mTxBuf.buf);
    void *rxPayload = hostIntfGetPayload(mRxBuf);
    uint8_t rx_len = hostIntfGetPayloadLen(mRxBuf);
    uint32_t resp = NANOHUB_FAST_UNHANDLED_ACK;

    atomicWriteByte(&mActiveWrite, true);
    hostIntfSetInterrupt(NANOHUB_INT_WAKE_COMPLETE);
    mRxCmd = hostIntfFindHandler(mRxBuf, mRxSize, &seq);

    if (mRxCmd) {
        if (mTxRetrans.seqMatch) {
            hostIntfTxBuf(mTxSize, &mTxBuf.prePreamble, hostIntfTxPayloadDone);
        } else {
            mTxRetrans.seq = seq;
            mTxRetrans.cmd = mRxCmd;
            if (mRxCmd->fastHandler)
                resp = mRxCmd->fastHandler(rxPayload, rx_len, txPayload, mRxTimestamp);

            hostIntfTxSendAck(resp);
        }
    } else {
        if (mBusy)
            hostIntfTxNakPacket(NANOHUB_REASON_NAK_BUSY, seq, hostIntfTxAckDone);
        else
            hostIntfTxNakPacket(NANOHUB_REASON_NAK, seq, hostIntfTxAckDone);
    }
}


static void hostIntfTxComplete(bool clearInt, bool restartRx)
{
    if (restartRx || clearInt || !mWakeActive)
        hostIntfClearInterrupt(NANOHUB_INT_WAKE_COMPLETE);
    atomicWriteByte(&mActiveWrite, false);
    atomicWriteByte(&mRestartRx, false);
    if (restartRx) {
        mComm->rxPacket(mRxBuf, sizeof(mRxBuf), hostIntfRxDone);
        hostIntfSetInterrupt(NANOHUB_INT_WAKE_COMPLETE);
    } else {
        atomicWriteByte(&mRxIdle, true);
    }
}

static void hostIntfTxAckDone(size_t tx, int err)
{
    hostIntfTxPacketDone(err, tx, hostIntfTxAckDone);

    if (err) {
        hostIntfDeferErrLog(LOG_ERROR, HOSTINTF_ERR_ACK, __func__);
        hostIntfTxComplete(false, false);
        return;
    }

    if (!mRxCmd) {
        if (!mBusy)
            hostIntfDeferErrLog(LOG_DEBUG, HOSTINTF_ERR_NAK, __func__);
        if (atomicReadByte(&mRestartRx))
            hostIntfTxComplete(false, true);
        else
            hostIntfTxComplete(false, false);
        return;
    } else if (atomicReadByte(&mRestartRx)) {
        mTxRetrans.seq = 0;
        mTxRetrans.cmd = NULL;
        hostIntfTxComplete(false, true);
    } else {
        if (!osDefer(hostIntfGenerateResponse, NULL, true)) {
            mTxRetrans.seq = 0;
            mTxRetrans.cmd = NULL;
            hostIntfTxComplete(false, false);
        }
    }
}

static void hostIntfGenerateResponse(void *cookie)
{
    void *rxPayload = hostIntfGetPayload(mRxBuf);
    uint8_t rx_len = hostIntfGetPayloadLen(mRxBuf);
    void *txPayload = hostIntfGetPayload(mTxBuf.buf);
    uint8_t respLen = mRxCmd->handler(rxPayload, rx_len, txPayload, mRxTimestamp);

    hostIntfTxPacket(mRxCmd->reason, respLen, mTxRetrans.seq, hostIntfTxPayloadDone);
}

static void hostIntfTxPayloadDone(size_t tx, int err)
{
    bool done = hostIntfTxPacketDone(err, tx, hostIntfTxPayloadDone);

    if (err)
        hostIntfDeferErrLog(LOG_ERROR, HOSTINTF_ERR_SEND, __func__);

    if (done) {
        if (atomicReadByte(&mRestartRx))
            hostIntfTxComplete(true, true);
        else
            hostIntfTxComplete(true, false);
    }
}

static void hostIntfRelease()
{
    mComm->release();
}

static void resetBuffer(struct ActiveSensor *sensor)
{
    sensor->discard = true;
    sensor->buffer.length = 0;
    memset(&sensor->buffer.firstSample, 0x00, sizeof(struct SensorFirstSample));
}

void hostIntfSetBusy(bool busy)
{
    mBusy = busy;
}

static inline struct ActiveSensor *getActiveSensorByType(uint32_t sensorType)
{
    struct ActiveSensor *sensor = NULL;

    if (sensorType > SENS_TYPE_INVALID && sensorType <= SENS_TYPE_LAST_USER &&
        mSensorList[sensorType - 1] < MAX_REGISTERED_SENSORS)
        sensor = mActiveSensorTable + mSensorList[sensorType - 1];

    return sensor;
}

bool hostIntfPacketDequeue(void *data, uint32_t *wakeup, uint32_t *nonwakeup)
{
    struct HostIntfDataBuffer *buffer = data;
    bool ret;
    struct ActiveSensor *sensor;
    uint32_t i;

    ret = simpleQueueDequeue(mOutputQ, buffer);
    while (ret) {
        sensor = getActiveSensorByType(buffer->sensType);
        if (sensor) {
            // do not sent sensor data if sensor is not requested; only maintain stats
            if (sensor->sensorHandle == 0 && !buffer->firstSample.biasPresent && !buffer->firstSample.numFlushes) {
                if (sensor->interrupt == NANOHUB_INT_WAKEUP)
                    mWakeupBlocks--;
                else if (sensor->interrupt == NANOHUB_INT_NONWAKEUP)
                    mNonWakeupBlocks--;
                sensor->curSamples -= buffer->firstSample.numSamples;
                ret = simpleQueueDequeue(mOutputQ, buffer);
            } else {
                break;
            }
        } else {
            break;
        }
    }

    if (!ret) {
        // nothing in queue. look for partial buffers to flush
        for (i = 0; i < mNumSensors; i++, mLastSensor = (mLastSensor + 1) % mNumSensors) {
            sensor = mActiveSensorTable + mLastSensor;

            if (sensor->curSamples != sensor->buffer.firstSample.numSamples) {
                osLog(LOG_ERROR, "hostIntfPacketDequeue: sensor(%d)->curSamples=%d != buffer->numSamples=%d\n", sensor->buffer.sensType, sensor->curSamples, sensor->buffer.firstSample.numSamples);
                sensor->curSamples = sensor->buffer.firstSample.numSamples;
            }

            if (sensor->buffer.length > 0) {
                memcpy(buffer, &sensor->buffer, sizeof(struct HostIntfDataBuffer));
                resetBuffer(sensor);
                ret = true;
                mLastSensor = (mLastSensor + 1) % mNumSensors;
                break;
            }
        }
    }

    if (ret) {
        sensor = getActiveSensorByType(buffer->sensType);
        if (sensor) {
            if (sensor->interrupt == NANOHUB_INT_WAKEUP)
                mWakeupBlocks--;
            else if (sensor->interrupt == NANOHUB_INT_NONWAKEUP)
                mNonWakeupBlocks--;
            sensor->curSamples -= buffer->firstSample.numSamples;
            sensor->firstTime = 0ull;
        } else {
            if (buffer->interrupt == NANOHUB_INT_WAKEUP)
                mWakeupBlocks--;
            else if (buffer->interrupt == NANOHUB_INT_NONWAKEUP)
                mNonWakeupBlocks--;
        }
    }

    *wakeup = mWakeupBlocks;
    *nonwakeup = mNonWakeupBlocks;

    return ret;
}

static void initCompleteCallback(uint32_t timerId, void *data)
{
    osEnqueuePrivateEvt(EVT_APP_START, NULL, NULL, mHostIntfTid);
}

static bool queueDiscard(void *data, bool onDelete)
{
    struct HostIntfDataBuffer *buffer = data;
    struct ActiveSensor *sensor = getActiveSensorByType(buffer->sensType);

    if (sensor) {
        if (sensor->curSamples - buffer->firstSample.numSamples >= sensor->minSamples || onDelete) {
            if (sensor->interrupt == NANOHUB_INT_WAKEUP)
                mWakeupBlocks--;
            else if (sensor->interrupt == NANOHUB_INT_NONWAKEUP)
                mNonWakeupBlocks--;
            sensor->curSamples -= buffer->firstSample.numSamples;

            return true;
        } else {
            return false;
        }
    } else {
        if (buffer->interrupt == NANOHUB_INT_WAKEUP)
            mWakeupBlocks--;
        else if (buffer->interrupt == NANOHUB_INT_NONWAKEUP)
            mNonWakeupBlocks--;
        return true;
    }
}

static void latencyTimerCallback(uint32_t timerId, void* data)
{
    osEnqueuePrivateEvt(EVT_LATENCY_TIMER, data, NULL, mHostIntfTid);
}

static bool initSensors()
{
    uint32_t i, j, blocks, maxBlocks, numAxis, packetSamples;
    bool present, error;
    const struct SensorInfo *si;
    uint32_t handle;
    static uint8_t errorCnt = 0;
    uint32_t totalBlocks = 0;
    uint8_t numSensors = 0;
    ATOMIC_BITSET_DECL(sensorPresent, SENS_TYPE_LAST_USER - SENS_TYPE_INVALID,);

    atomicBitsetInit(sensorPresent, SENS_TYPE_LAST_USER - SENS_TYPE_INVALID);

    for (i = SENS_TYPE_INVALID + 1; i <= SENS_TYPE_LAST_USER; i++) {
        for (j = 0, present = 0, error = 0; (si = sensorFind(i, j, &handle)) != NULL; j++) {
            if (!sensorGetInitComplete(handle)) {
                if (errorCnt >= SENSOR_INIT_ERROR_MAX) {
                    osLog(LOG_ERROR, "initSensors: %s not ready - skipping!\n", si->sensorName);
                    continue;
                } else {
                    osLog(LOG_INFO, "initSensors: %s not ready!\n", si->sensorName);
                    timTimerSet(SENSOR_INIT_DELAY, 0, 50, initCompleteCallback, NULL, true);
                    errorCnt ++;
                    return false;
                }
            } else if (!(si->flags1 & SENSOR_INFO_FLAGS1_LOCAL_ONLY)) {
                if (!present) {
                    present = 1;
                    numAxis = si->numAxis;
                    switch (si->numAxis) {
                    case NUM_AXIS_EMBEDDED:
                    case NUM_AXIS_ONE:
                        packetSamples = HOSTINTF_SENSOR_DATA_MAX / sizeof(struct SingleAxisDataPoint);
                        break;
                    case NUM_AXIS_THREE:
                        if (si->flags1 & SENSOR_INFO_FLAGS1_RAW)
                            packetSamples = HOSTINTF_SENSOR_DATA_MAX / sizeof(struct RawTripleAxisDataPoint);
                        else
                            packetSamples = HOSTINTF_SENSOR_DATA_MAX / sizeof(struct TripleAxisDataPoint);
                        break;
                    default:
                        packetSamples = 1;
                        error = true;
                    }
                    if (si->minSamples > MAX_MIN_SAMPLES)
                        maxBlocks = (MAX_MIN_SAMPLES + packetSamples - 1) / packetSamples;
                    else
                        maxBlocks = (si->minSamples + packetSamples - 1) / packetSamples;
                } else {
                    if (si->numAxis != numAxis) {
                        error = true;
                    } else {
                        if (si->minSamples > MAX_MIN_SAMPLES)
                            blocks = (MAX_MIN_SAMPLES + packetSamples - 1) / packetSamples;
                        else
                            blocks = (si->minSamples + packetSamples - 1) / packetSamples;

                        maxBlocks = maxBlocks > blocks ? maxBlocks : blocks;
                    }
                }
            }
        }

        if (present && !error) {
            atomicBitsetSetBit(sensorPresent, i - 1);
            numSensors++;
            totalBlocks += maxBlocks;
        }
    }

    if (totalBlocks > MAX_NUM_BLOCKS) {
        osLog(LOG_INFO, "initSensors: totalBlocks of %ld exceeds maximum of %d\n", totalBlocks, MAX_NUM_BLOCKS);
        totalBlocks = MAX_NUM_BLOCKS;
    } else if (totalBlocks < MIN_NUM_BLOCKS) {
        totalBlocks = MIN_NUM_BLOCKS;
    }

    mOutputQ = simpleQueueAlloc(totalBlocks, sizeof(struct HostIntfDataBuffer), queueDiscard);
    mActiveSensorTable = heapAlloc(numSensors * sizeof(struct ActiveSensor));
    memset(mActiveSensorTable, 0x00, numSensors * sizeof(struct ActiveSensor));

    for (i = SENS_TYPE_INVALID; i < SENS_TYPE_LAST_USER; i++) {
        mSensorList[i] = MAX_REGISTERED_SENSORS;
    }

    for (i = SENS_TYPE_INVALID + 1, j = 0; i <= SENS_TYPE_LAST_USER && j < numSensors; i++) {
        if (atomicBitsetGetBit(sensorPresent, i - 1)
            && (si = sensorFind(i, 0, &handle)) != NULL
            && !(si->flags1 & SENSOR_INFO_FLAGS1_LOCAL_ONLY)) {
            mSensorList[i - 1] = j;
            resetBuffer(mActiveSensorTable + j);
            mActiveSensorTable[j].buffer.sensType = i;
            mActiveSensorTable[j].biasReportType = 0;
            mActiveSensorTable[j].rate = 0;
            mActiveSensorTable[j].latency = 0;
            mActiveSensorTable[j].numAxis = si->numAxis;
            mActiveSensorTable[j].interrupt = si->interrupt;
            if (si->flags1 & SENSOR_INFO_FLAGS1_RAW) {
                mSensorList[si->rawType - 1] = j;
                mActiveSensorTable[j].buffer.sensType = si->rawType;
                mActiveSensorTable[j].raw = true;
                mActiveSensorTable[j].rawScale = si->rawScale;
            }
            if (si->flags1 & SENSOR_INFO_FLAGS1_BIAS) {
                mSensorList[si->biasType - 1] = j;
                mActiveSensorTable[j].biasReportType = i;
                osEventSubscribe(mHostIntfTid, sensorGetMyEventType(si->biasType));
            }
            if (si->minSamples > MAX_MIN_SAMPLES) {
                mActiveSensorTable[j].minSamples = MAX_MIN_SAMPLES;
                osLog(LOG_INFO, "initSensors: %s: minSamples of %d exceeded max of %d\n", si->sensorName, si->minSamples, MAX_MIN_SAMPLES);
            } else {
                mActiveSensorTable[j].minSamples = si->minSamples;
            }
            mActiveSensorTable[j].curSamples = 0;
            mActiveSensorTable[j].oneshot = false;
            mActiveSensorTable[j].firstTime = 0ull;
            switch (si->numAxis) {
            case NUM_AXIS_EMBEDDED:
            case NUM_AXIS_ONE:
                mActiveSensorTable[j].packetSamples = HOSTINTF_SENSOR_DATA_MAX / sizeof(struct SingleAxisDataPoint);
                break;
            case NUM_AXIS_THREE:
                if (mActiveSensorTable[j].raw)
                    mActiveSensorTable[j].packetSamples = HOSTINTF_SENSOR_DATA_MAX / sizeof(struct RawTripleAxisDataPoint);
                else
                    mActiveSensorTable[j].packetSamples = HOSTINTF_SENSOR_DATA_MAX / sizeof(struct TripleAxisDataPoint);
                break;
            }
            j++;
        }
    }

    mTotalBlocks = totalBlocks;
    mNumSensors = numSensors;

    return true;
}

static inline int16_t floatToInt16(float val)
{
    if (val < (INT16_MIN + 0.5f))
        return INT16_MIN;
    else if (val > (INT16_MAX - 0.5f))
        return INT16_MAX;
    else if (val >= 0.0f)
        return val + 0.5f;
    else
        return val - 0.5f;
}

static uint32_t encodeDeltaTime(uint64_t time)
{
    uint32_t deltaTime;

    if (time <= UINT32_MAX) {
        deltaTime = time | delta_time_fine_mask;
    } else {
        deltaTime = ((time + delta_time_rounding) >> delta_time_multiplier_order) & delta_time_coarse_mask;
    }
    return deltaTime;
}

static bool enqueueSensorBuffer(struct ActiveSensor *sensor)
{
    bool queued = simpleQueueEnqueue(mOutputQ, &sensor->buffer,
                                     sizeof(uint32_t) + sensor->buffer.length, sensor->discard);

    if (!queued) {
        // undo counters if failed to add buffer
        if (sensor->interrupt == NANOHUB_INT_WAKEUP)
            mWakeupBlocks--;
        else if (sensor->interrupt == NANOHUB_INT_NONWAKEUP)
            mNonWakeupBlocks--;
        sensor->curSamples -= sensor->buffer.firstSample.numSamples;
    }
    resetBuffer(sensor);
    return queued;
}

static void copySingleSamples(struct ActiveSensor *sensor, const struct SingleAxisDataEvent *single)
{
    int i;
    uint32_t deltaTime;
    uint8_t numSamples;
    uint8_t evtNumSamples = single->samples[0].firstSample.numSamples;

    for (i = 0; i < evtNumSamples; i++) {
        if (sensor->buffer.firstSample.numSamples == sensor->packetSamples)
            enqueueSensorBuffer(sensor);

        if (sensor->buffer.firstSample.numSamples == 0) {
            if (i == 0) {
                sensor->lastTime = sensor->buffer.referenceTime = single->referenceTime;
            } else {
                sensor->lastTime += single->samples[i].deltaTime;
                sensor->buffer.referenceTime = sensor->lastTime;
            }
            sensor->buffer.length = sizeof(struct SingleAxisDataEvent) + sizeof(struct SingleAxisDataPoint);
            sensor->buffer.single[0].idata = single->samples[i].idata;
            if (sensor->interrupt == NANOHUB_INT_WAKEUP)
                mWakeupBlocks++;
            else if (sensor->interrupt == NANOHUB_INT_NONWAKEUP)
                mNonWakeupBlocks++;
            sensor->buffer.firstSample.numSamples = 1;
            sensor->buffer.firstSample.interrupt = sensor->interrupt;
            if (sensor->curSamples++ == 0)
                sensor->firstTime = sensor->buffer.referenceTime;
        } else {
            if (i == 0) {
                if (sensor->lastTime > single->referenceTime) {
                    // shouldn't happen. flush current packet
                    enqueueSensorBuffer(sensor);
                    i--;
                } else if (single->referenceTime - sensor->lastTime >= delta_time_max) {
                    enqueueSensorBuffer(sensor);
                    i--;
                } else {
                    deltaTime = encodeDeltaTime(single->referenceTime - sensor->lastTime);
                    numSamples = sensor->buffer.firstSample.numSamples;

                    sensor->buffer.length += sizeof(struct SingleAxisDataPoint);
                    sensor->buffer.single[numSamples].deltaTime = deltaTime;
                    sensor->buffer.single[numSamples].idata = single->samples[0].idata;
                    sensor->lastTime = single->referenceTime;
                    sensor->buffer.firstSample.numSamples++;
                    sensor->curSamples++;
                }
            } else {
                deltaTime = single->samples[i].deltaTime;
                numSamples = sensor->buffer.firstSample.numSamples;

                sensor->buffer.length += sizeof(struct SingleAxisDataPoint);
                sensor->buffer.single[numSamples].deltaTime = deltaTime | delta_time_fine_mask;
                sensor->buffer.single[numSamples].idata = single->samples[i].idata;
                sensor->lastTime += deltaTime;
                sensor->buffer.firstSample.numSamples++;
                sensor->curSamples++;
            }
        }
    }
}

static void copyTripleSamples(struct ActiveSensor *sensor, const struct TripleAxisDataEvent *triple)
{
    int i;
    uint32_t deltaTime;
    uint8_t numSamples;

    for (i = 0; i < triple->samples[0].firstSample.numSamples; i++) {
        if (sensor->buffer.firstSample.numSamples == sensor->packetSamples)
            enqueueSensorBuffer(sensor);

        if (sensor->buffer.firstSample.numSamples == 0) {
            if (i == 0) {
                sensor->lastTime = sensor->buffer.referenceTime = triple->referenceTime;
            } else {
                sensor->lastTime += triple->samples[i].deltaTime;
                sensor->buffer.referenceTime = sensor->lastTime;
            }
            sensor->buffer.length = sizeof(struct TripleAxisDataEvent) + sizeof(struct TripleAxisDataPoint);
            sensor->buffer.triple[0].ix = triple->samples[i].ix;
            sensor->buffer.triple[0].iy = triple->samples[i].iy;
            sensor->buffer.triple[0].iz = triple->samples[i].iz;
            if (triple->samples[0].firstSample.biasPresent && triple->samples[0].firstSample.biasSample == i) {
                sensor->buffer.firstSample.biasCurrent = triple->samples[0].firstSample.biasCurrent;
                sensor->buffer.firstSample.biasPresent = 1;
                sensor->buffer.firstSample.biasSample = 0;
                sensor->discard = false;
            }
            if (sensor->interrupt == NANOHUB_INT_WAKEUP)
                mWakeupBlocks++;
            else if (sensor->interrupt == NANOHUB_INT_NONWAKEUP)
                mNonWakeupBlocks++;
            sensor->buffer.firstSample.numSamples = 1;
            sensor->buffer.firstSample.interrupt = sensor->interrupt;
            if (sensor->curSamples++ == 0)
                sensor->firstTime = sensor->buffer.referenceTime;
        } else {
            if (i == 0) {
                if (sensor->lastTime > triple->referenceTime) {
                    // shouldn't happen. flush current packet
                    enqueueSensorBuffer(sensor);
                    i--;
                } else if (triple->referenceTime - sensor->lastTime >= delta_time_max) {
                    enqueueSensorBuffer(sensor);
                    i--;
                } else {
                    deltaTime = encodeDeltaTime(triple->referenceTime - sensor->lastTime);
                    numSamples = sensor->buffer.firstSample.numSamples;

                    sensor->buffer.length += sizeof(struct TripleAxisDataPoint);
                    sensor->buffer.triple[numSamples].deltaTime = deltaTime;
                    sensor->buffer.triple[numSamples].ix = triple->samples[0].ix;
                    sensor->buffer.triple[numSamples].iy = triple->samples[0].iy;
                    sensor->buffer.triple[numSamples].iz = triple->samples[0].iz;
                    sensor->lastTime = triple->referenceTime;
                    if (triple->samples[0].firstSample.biasPresent && triple->samples[0].firstSample.biasSample == 0) {
                        sensor->buffer.firstSample.biasCurrent = triple->samples[0].firstSample.biasCurrent;
                        sensor->buffer.firstSample.biasPresent = 1;
                        sensor->buffer.firstSample.biasSample = numSamples;
                        sensor->discard = false;
                    }
                    sensor->buffer.firstSample.numSamples++;
                    sensor->curSamples++;
                }
            } else {
                deltaTime = triple->samples[i].deltaTime;
                numSamples = sensor->buffer.firstSample.numSamples;

                sensor->buffer.length += sizeof(struct TripleAxisDataPoint);
                sensor->buffer.triple[numSamples].deltaTime = deltaTime | delta_time_fine_mask;
                sensor->buffer.triple[numSamples].ix = triple->samples[i].ix;
                sensor->buffer.triple[numSamples].iy = triple->samples[i].iy;
                sensor->buffer.triple[numSamples].iz = triple->samples[i].iz;
                sensor->lastTime += deltaTime;
                if (triple->samples[0].firstSample.biasPresent && triple->samples[0].firstSample.biasSample == i) {
                    sensor->buffer.firstSample.biasCurrent = triple->samples[0].firstSample.biasCurrent;
                    sensor->buffer.firstSample.biasPresent = 1;
                    sensor->buffer.firstSample.biasSample = numSamples;
                    sensor->discard = false;
                }
                sensor->buffer.firstSample.numSamples++;
                sensor->curSamples++;
            }
        }
    }
}

static void copyTripleSamplesBias(struct ActiveSensor *sensor, const struct TripleAxisDataEvent *triple)
{
    uint8_t sensType = sensor->buffer.sensType;

    if (sensType == sensor->biasReportType) {
        copyTripleSamples(sensor, triple);
    } else {
        // Bias needs to be sent with a different sensType, so enqueue any pending buffer, enqueue
        // bias with a different sensor type, then restore the sensType
        if (sensor->buffer.firstSample.numSamples > 0)
            enqueueSensorBuffer(sensor);
        sensor->buffer.sensType = sensor->biasReportType;
        copyTripleSamples(sensor, triple);
        if (sensor->buffer.firstSample.numSamples > 0)
            enqueueSensorBuffer(sensor);
        sensor->buffer.sensType = sensType;
    }
}

static void copyTripleSamplesRaw(struct ActiveSensor *sensor, const struct TripleAxisDataEvent *triple)
{
    int i;
    uint32_t deltaTime;
    uint8_t numSamples;

    // Bias not supported in raw format; treat as regular format triple samples (potentially
    // handling alternate bias report type)
    if (triple->samples[0].firstSample.biasPresent) {
        copyTripleSamplesBias(sensor, triple);
        return;
    }

    for (i = 0; i < triple->samples[0].firstSample.numSamples; i++) {
        if (sensor->buffer.firstSample.numSamples == sensor->packetSamples)
            enqueueSensorBuffer(sensor);

        if (sensor->buffer.firstSample.numSamples == 0) {
            if (i == 0) {
                sensor->lastTime = sensor->buffer.referenceTime = triple->referenceTime;
            } else {
                sensor->lastTime += triple->samples[i].deltaTime;
                sensor->buffer.referenceTime = sensor->lastTime;
            }
            sensor->buffer.length = sizeof(struct RawTripleAxisDataEvent) + sizeof(struct RawTripleAxisDataPoint);
            sensor->buffer.rawTriple[0].ix = floatToInt16(triple->samples[i].x * sensor->rawScale);
            sensor->buffer.rawTriple[0].iy = floatToInt16(triple->samples[i].y * sensor->rawScale);
            sensor->buffer.rawTriple[0].iz = floatToInt16(triple->samples[i].z * sensor->rawScale);
            if (sensor->interrupt == NANOHUB_INT_WAKEUP)
                mWakeupBlocks++;
            else if (sensor->interrupt == NANOHUB_INT_NONWAKEUP)
                mNonWakeupBlocks++;
            sensor->buffer.firstSample.numSamples = 1;
            sensor->buffer.firstSample.interrupt = sensor->interrupt;
            if (sensor->curSamples++ == 0)
                sensor->firstTime = sensor->buffer.referenceTime;
        } else {
            if (i == 0) {
                if (sensor->lastTime > triple->referenceTime) {
                    // shouldn't happen. flush current packet
                    enqueueSensorBuffer(sensor);
                    i--;
                } else if (triple->referenceTime - sensor->lastTime >= delta_time_max) {
                    enqueueSensorBuffer(sensor);
                    i--;
                } else {
                    deltaTime = encodeDeltaTime(triple->referenceTime - sensor->lastTime);
                    numSamples = sensor->buffer.firstSample.numSamples;

                    sensor->buffer.length += sizeof(struct RawTripleAxisDataPoint);
                    sensor->buffer.rawTriple[numSamples].deltaTime = deltaTime;
                    sensor->buffer.rawTriple[numSamples].ix = floatToInt16(triple->samples[0].x * sensor->rawScale);
                    sensor->buffer.rawTriple[numSamples].iy = floatToInt16(triple->samples[0].y * sensor->rawScale);
                    sensor->buffer.rawTriple[numSamples].iz = floatToInt16(triple->samples[0].z * sensor->rawScale);
                    sensor->lastTime = triple->referenceTime;
                    sensor->buffer.firstSample.numSamples++;
                    sensor->curSamples++;
                }
            } else {
                deltaTime = triple->samples[i].deltaTime;
                numSamples = sensor->buffer.firstSample.numSamples;

                sensor->buffer.length += sizeof(struct RawTripleAxisDataPoint);
                sensor->buffer.rawTriple[numSamples].deltaTime = deltaTime | delta_time_fine_mask;
                sensor->buffer.rawTriple[numSamples].ix = floatToInt16(triple->samples[i].x * sensor->rawScale);
                sensor->buffer.rawTriple[numSamples].iy = floatToInt16(triple->samples[i].y * sensor->rawScale);
                sensor->buffer.rawTriple[numSamples].iz = floatToInt16(triple->samples[i].z * sensor->rawScale);
                sensor->lastTime += deltaTime;
                sensor->buffer.firstSample.numSamples++;
                sensor->curSamples++;
            }
        }
    }
}

static void hostIntfAddBlock(struct HostIntfDataBuffer *data, bool discardable, bool interrupt)
{
    if (!simpleQueueEnqueue(mOutputQ, data, sizeof(uint32_t) + data->length, discardable))
        return;

    if (data->interrupt == NANOHUB_INT_WAKEUP)
        mWakeupBlocks++;
    else if (data->interrupt == NANOHUB_INT_NONWAKEUP)
        mNonWakeupBlocks++;
    nanohubPrefetchTx(interrupt ? data->interrupt : HOSTINTF_MAX_INTERRUPTS, mWakeupBlocks, mNonWakeupBlocks);
}

static void hostIntfNotifyReboot(uint32_t reason)
{
    struct NanohubHalRebootTx *resp = heapAlloc(sizeof(*resp));
    __le32 raw_reason = htole32(reason);

    if (resp) {
        resp->hdr = (struct NanohubHalHdr){
            .appId = APP_ID_MAKE(NANOHUB_VENDOR_GOOGLE, 0),
            .len = sizeof(*resp) - sizeof(resp->hdr) + sizeof(resp->hdr.msg),
            .msg = NANOHUB_HAL_REBOOT,
        };
        memcpy(&resp->reason, &raw_reason, sizeof(resp->reason));
        osEnqueueEvtOrFree(EVT_APP_TO_HOST, resp, heapFree);
    }
}

static void queueFlush(struct ActiveSensor *sensor)
{
    if (sensor->buffer.length == 0) {
        sensor->buffer.length = sizeof(sensor->buffer.referenceTime) + sizeof(struct SensorFirstSample);
        sensor->buffer.referenceTime = 0ull;
        if (sensor->interrupt == NANOHUB_INT_WAKEUP)
            mWakeupBlocks++;
        else if (sensor->interrupt == NANOHUB_INT_NONWAKEUP)
            mNonWakeupBlocks++;
        sensor->buffer.firstSample.numFlushes = 1;
    } else {
        sensor->buffer.firstSample.numFlushes++;
    }
    sensor->discard = false;
    hostIntfSetInterrupt(sensor->interrupt);
}

static void fakeFlush(struct ConfigCmd *cmd)
{
    struct HostIntfDataBuffer *buffer;
    uint8_t size = sizeof(buffer->evtType) + sizeof(buffer->referenceTime) + sizeof(struct SensorFirstSample);
    buffer = alloca(size);
    memset(buffer, 0x00, size);

    buffer->sensType = cmd->sensType;
    buffer->length = sizeof(buffer->referenceTime) + sizeof(struct SensorFirstSample);
    buffer->interrupt = NANOHUB_INT_WAKEUP;
    mWakeupBlocks++;
    buffer->firstSample.numFlushes = 1;
    if (!simpleQueueEnqueue(mOutputQ, buffer, size, false))
        mWakeupBlocks--;
}

static void onEvtAppStart(const void *evtData)
{
    if (initSensors()) {
        uint32_t reason;
        struct HostIntfDataBuffer *data;

        osEventUnsubscribe(mHostIntfTid, EVT_APP_START);
        osEventSubscribe(mHostIntfTid, EVT_NO_SENSOR_CONFIG_EVENT);
        osEventSubscribe(mHostIntfTid, EVT_APP_TO_SENSOR_HAL_DATA);
        osEventSubscribe(mHostIntfTid, EVT_APP_TO_HOST);
#ifdef DEBUG_LOG_EVT
        osEventSubscribe(mHostIntfTid, EVT_DEBUG_LOG);
        platEarlyLogFlush();
#endif
        reason = pwrResetReason();
        data = alloca(sizeof(uint32_t) + sizeof(reason));
        data->sensType = SENS_TYPE_INVALID;
        data->length = sizeof(reason);
        data->dataType = HOSTINTF_DATA_TYPE_RESET_REASON;
        data->interrupt = NANOHUB_INT_WAKEUP;
        memcpy(data->buffer, &reason, sizeof(reason));
        hostIntfAddBlock(data, false, true);
        hostIntfNotifyReboot(reason);
    }
}

static void onEvtAppToHost(const void *evtData)
{
    const struct HostHubRawPacket *hostMsg = evtData;

    if (hostMsg->dataLen <= HOST_HUB_RAW_PACKET_MAX_LEN) {
        struct HostIntfDataBuffer *data = alloca(sizeof(uint32_t) + sizeof(*hostMsg) + hostMsg->dataLen);

        data->sensType = SENS_TYPE_INVALID;
        data->length = sizeof(*hostMsg) + hostMsg->dataLen;
        data->dataType = HOSTINTF_DATA_TYPE_APP_TO_HOST;
        data->interrupt = NANOHUB_INT_WAKEUP;
        memcpy(data->buffer, evtData, data->length);
        hostIntfAddBlock(data, false, true);
    }
}

static void onEvtAppFromHost(const void *evtData)
{
    const uint8_t *halMsg = evtData;
    const struct NanohubHalCommand *halCmd = nanohubHalFindCommand(halMsg[1]);
    if (halCmd)
        halCmd->handler((void *)&halMsg[2], halMsg[0] - 1);
}

#ifdef DEBUG_LOG_EVT
static void onEvtDebugLog(const void *evtData)
{
    struct HostIntfDataBuffer *data = (struct HostIntfDataBuffer *)evtData;

    if (data->sensType == SENS_TYPE_INVALID && data->dataType == HOSTINTF_DATA_TYPE_LOG)
        hostIntfAddBlock(data, true, true);
}
#endif

static void onEvtLatencyTimer(const void *evtData)
{
    uint64_t sensorTime = sensorGetTime();
    uint32_t i, cnt;

    for (i = 0, cnt = 0; i < mNumSensors && cnt < mLatencyCnt; i++) {
        if (mActiveSensorTable[i].latency > 0) {
            cnt++;
            if (mActiveSensorTable[i].firstTime &&
                sensorTime >= mActiveSensorTable[i].firstTime + mActiveSensorTable[i].latency) {
                hostIntfSetInterrupt(mActiveSensorTable[i].interrupt);
            }
        }
    }
}

static void onConfigCmdFlushOne(struct ActiveSensor *sensor, struct ConfigCmd *cmd)
{
    sensorFlush(sensor->sensorHandle);
}

static void onConfigCmdEnableOne(struct ActiveSensor *sensor, struct ConfigCmd *cmd)
{
    if (sensorRequestRateChange(mHostIntfTid, sensor->sensorHandle, cmd->rate, cmd->latency)) {
        sensor->rate = cmd->rate;
        if (sensor->latency != cmd->latency) {
            if (!sensor->latency) {
                if (mLatencyCnt++ == 0)
                    mLatencyTimer = timTimerSet(CHECK_LATENCY_TIME, 100, 100, latencyTimerCallback, NULL, false);
            } else if (!cmd->latency) {
                if (--mLatencyCnt == 0) {
                    timTimerCancel(mLatencyTimer);
                    mLatencyTimer = 0;
                }
            }
            sensor->latency = cmd->latency;
        }
    }
}

static void onConfigCmdEnableAll(struct ActiveSensor *sensor, struct ConfigCmd *cmd)
{
    for (uint32_t i = 0; sensorFind(cmd->sensType, i, &sensor->sensorHandle) != NULL; i++) {
        if (cmd->rate == SENSOR_RATE_ONESHOT) {
            cmd->rate = SENSOR_RATE_ONCHANGE;
            sensor->oneshot = true;
        } else {
            sensor->oneshot = false;
        }

        if (sensorRequest(mHostIntfTid, sensor->sensorHandle, cmd->rate, cmd->latency)) {
            if (cmd->latency) {
                if (mLatencyCnt++ == 0)
                    mLatencyTimer = timTimerSet(CHECK_LATENCY_TIME, 100, 100, latencyTimerCallback, NULL, false);
            }
            sensor->rate = cmd->rate;
            sensor->latency = cmd->latency;
            osEventSubscribe(mHostIntfTid, sensorGetMyEventType(cmd->sensType));
            break;
        } else {
            sensor->sensorHandle = 0;
        }
    }
}

static void onConfigCmdDisableOne(struct ActiveSensor *sensor, struct ConfigCmd *cmd)
{
    sensorRelease(mHostIntfTid, sensor->sensorHandle);
    osEventUnsubscribe(mHostIntfTid, sensorGetMyEventType(cmd->sensType));
    if (sensor->latency) {
        if (--mLatencyCnt == 0) {
            timTimerCancel(mLatencyTimer);
            mLatencyTimer = 0;
        }
    }
    sensor->rate = 0;
    sensor->latency = 0;
    sensor->oneshot = false;
    sensor->sensorHandle = 0;
    if (sensor->buffer.length) {
        enqueueSensorBuffer(sensor);
        hostIntfSetInterrupt(sensor->interrupt);
    }
}

static void onConfigCmdCalibrateAll(struct ActiveSensor *sensor, struct ConfigCmd *cmd)
{
    uint32_t tempSensorHandle;
    for (uint32_t i = 0; sensorFind(cmd->sensType, i, &tempSensorHandle) != NULL; i++)
        sensorCalibrate(tempSensorHandle);
}

static void onConfigCmdSelfTestAll(struct ActiveSensor *sensor, struct ConfigCmd *cmd)
{
    uint32_t tempSensorHandle;
    for (uint32_t i = 0; sensorFind(cmd->sensType, i, &tempSensorHandle) != NULL; i++)
        sensorSelfTest(tempSensorHandle);
}

static void onConfigCmdCfgDataAll(struct ActiveSensor *sensor, struct ConfigCmd *cmd)
{
    uint32_t tempSensorHandle;
    for (uint32_t i = 0; sensorFind(cmd->sensType, i, &tempSensorHandle) != NULL; i++)
        sensorCfgData(tempSensorHandle, (void *)(cmd+1));
}

static void onEvtNoSensorConfigEvent(const void *evtData)
{
    struct ConfigCmd *cmd = (struct ConfigCmd *)evtData;
    struct ActiveSensor *sensor = getActiveSensorByType(cmd->sensType);
    if (sensor) {
        if (sensor->sensorHandle) {
            switch (cmd->cmd) {
            case CONFIG_CMD_FLUSH:
                onConfigCmdFlushOne(sensor, cmd);
                break;
            case CONFIG_CMD_ENABLE:
                onConfigCmdEnableOne(sensor, cmd);
                break;
            case CONFIG_CMD_DISABLE:
                onConfigCmdDisableOne(sensor, cmd);
                break;
            case CONFIG_CMD_CFG_DATA:
                onConfigCmdCfgDataAll(sensor, cmd);
                break;
            }
        } else {
            switch (cmd->cmd) {
            case CONFIG_CMD_ENABLE:
                onConfigCmdEnableAll(sensor, cmd);
                break;
            case CONFIG_CMD_CALIBRATE:
                onConfigCmdCalibrateAll(sensor, cmd);
                break;
            case CONFIG_CMD_SELF_TEST:
                onConfigCmdSelfTestAll(sensor, cmd);
                break;
            case CONFIG_CMD_CFG_DATA:
                onConfigCmdCfgDataAll(sensor, cmd);
                break;
            case CONFIG_CMD_FLUSH:
                queueFlush(sensor);
                break;
            }
        }
    } else if (cmd->cmd == CONFIG_CMD_FLUSH && cmd->sensType > SENS_TYPE_INVALID) {
        // if a flush event is for an unknown sensor, we just return a fake flush event.
        osLog(LOG_INFO, "Flush request from unrecognized sensor, returning a fake flush\n");
        fakeFlush(cmd);
    }
}

static void onEvtAppToSensorHalData(const void *evtData)
{
    struct HostIntfDataBuffer *data = (struct HostIntfDataBuffer *)evtData;
    if (data->sensType == SENS_TYPE_INVALID
            && data->dataType == HOSTINTF_DATA_TYPE_APP_TO_SENSOR_HAL) {
        struct AppToSensorHalDataBuffer *buffer = (struct AppToSensorHalDataBuffer *)data;
        hostIntfAddBlock(data, (buffer->payload.type & EVENT_TYPE_BIT_DISCARDABLE) != 0, false);
    }
}

static void copyEmbeddedSamples(struct ActiveSensor *sensor, const void* evtData)
{
    uint64_t sensorTime = sensorGetTime();

    if (sensor->buffer.length > 0 && sensorTime - sensor->lastTime >= delta_time_max)
        enqueueSensorBuffer(sensor);

    if (sensor->buffer.length == 0) {
        sensor->buffer.length = sizeof(struct SingleAxisDataEvent) + sizeof(struct SingleAxisDataPoint);
        sensor->lastTime = sensor->buffer.referenceTime = sensorTime;
        if (sensor->interrupt == NANOHUB_INT_WAKEUP)
            mWakeupBlocks++;
        else if (sensor->interrupt == NANOHUB_INT_NONWAKEUP)
            mNonWakeupBlocks++;
        sensor->buffer.firstSample.numSamples = 1;
        sensor->buffer.firstSample.interrupt = sensor->interrupt;
        sensor->buffer.single[0].idata = (uint32_t)evtData;
    } else {
        sensor->buffer.length += sizeof(struct SingleAxisDataPoint);
        sensor->buffer.single[sensor->buffer.firstSample.numSamples].deltaTime =
                encodeDeltaTime(sensorTime - sensor->lastTime);
        sensor->lastTime = sensorTime;
        sensor->buffer.single[sensor->buffer.firstSample.numSamples].idata = (uint32_t)evtData;
        sensor->buffer.firstSample.numSamples++;
    }
    if (sensor->curSamples++ == 0)
        sensor->firstTime = sensor->buffer.referenceTime;
}

static uint32_t getSensorInterrupt(struct ActiveSensor *sensor)
{
    uint32_t interrupt = HOSTINTF_MAX_INTERRUPTS;
    uint64_t sensorTime = sensorGetTime();

    if (sensor->firstTime &&
        ((sensorTime >= sensor->firstTime + sensor->latency) ||
         ((sensor->latency > sensorGetCurLatency(sensor->sensorHandle)) &&
          (sensorTime + sensorGetCurLatency(sensor->sensorHandle) > sensor->firstTime + sensor->latency)))) {
        interrupt = sensor->interrupt;
    } else if (mWakeupBlocks + mNonWakeupBlocks >= mTotalBlocks) {
        interrupt = sensor->interrupt;
    }

    return interrupt;
}

static void onEvtSensorDataActive(struct ActiveSensor *sensor, uint32_t evtType, const void* evtData)
{
    if (evtData == SENSOR_DATA_EVENT_FLUSH) {
        queueFlush(sensor);
    } else {
        bool haveFlush = sensor->buffer.firstSample.numFlushes > 0;
        if (sensor->buffer.length > 0 &&
            (haveFlush || sensor->buffer.firstSample.numSamples == sensor->packetSamples)) {
                // processing will be aborted if we have pending flush and are not able to send
                // in this case, send eventually will be retried, otherwise data will be lost
                if (!enqueueSensorBuffer(sensor) && haveFlush)
                    return;
        }

        switch (sensor->numAxis) {
        case NUM_AXIS_EMBEDDED:
            copyEmbeddedSamples(sensor, evtData);
            break;
        case NUM_AXIS_ONE:
            copySingleSamples(sensor, evtData);
            break;
        case NUM_AXIS_THREE:
            if (sensor->raw)
                copyTripleSamplesRaw(sensor, evtData);
            else
                copyTripleSamples(sensor, evtData);
            break;
        default:
            return;
        }
    }

    nanohubPrefetchTx(getSensorInterrupt(sensor), mWakeupBlocks, mNonWakeupBlocks);

    if (sensor->oneshot) {
        sensorRelease(mHostIntfTid, sensor->sensorHandle);
        osEventUnsubscribe(mHostIntfTid, evtType);
        sensor->sensorHandle = 0;
        sensor->oneshot = false;
    }
}

static void onEvtSensorDataInactive(struct ActiveSensor *sensor, uint32_t evtType, const void* evtData)
{
    if (evtData != SENSOR_DATA_EVENT_FLUSH) {
        // handle bias data which can be generated for sensors that are
        // not currently requested by the AP
        switch (sensor->numAxis) {
        case NUM_AXIS_THREE:
            if (((const struct TripleAxisDataEvent *)evtData)->samples[0].firstSample.biasPresent) {
                copyTripleSamplesBias(sensor, evtData);
                nanohubPrefetchTx(HOSTINTF_MAX_INTERRUPTS, mWakeupBlocks, mNonWakeupBlocks);
            }
            break;
        }
    }
}

static void onEvtSensorData(uint32_t evtType, const void* evtData)
{
    if (evtType > EVT_NO_FIRST_SENSOR_EVENT && evtType < EVT_NO_SENSOR_CONFIG_EVENT) {
        struct ActiveSensor *sensor = getActiveSensorByType(evtType & 0xFF);
        if (sensor) {
            if (sensor->sensorHandle)
                onEvtSensorDataActive(sensor, evtType, evtData);
            else
                onEvtSensorDataInactive(sensor, evtType, evtData);
        }
    }
}

static void hostIntfHandleEvent(uint32_t evtType, const void* evtData)
{
    switch (evtType) {
    case EVT_APP_START:
        onEvtAppStart(evtData);
        break;
    case EVT_APP_TO_HOST:
        onEvtAppToHost(evtData);
        break;
    case EVT_APP_FROM_HOST:
        onEvtAppFromHost(evtData);
        break;
#ifdef DEBUG_LOG_EVT
    case EVT_DEBUG_LOG:
        onEvtDebugLog(evtData);
        break;
#endif
    case EVT_LATENCY_TIMER:
        onEvtLatencyTimer(evtData);
        break;
    case EVT_NO_SENSOR_CONFIG_EVENT:
        onEvtNoSensorConfigEvent(evtData);
        break;
    case EVT_APP_TO_SENSOR_HAL_DATA:
        onEvtAppToSensorHalData(evtData);
        break;
    default:
        onEvtSensorData(evtType, evtData);
        break;
    }
}

void hostIntfCopyInterrupts(void *dst, uint32_t numBits)
{
    if (mInterrupt->numBits != numBits)
        return;

    atomicBitsetBulkRead(mInterrupt, dst, numBits);
}

void hostIntfClearInterrupts()
{
    uint32_t i;

    for (i = 0; i < HOSTINTF_MAX_INTERRUPTS; i++) {
        if (atomicBitsetGetBit(mInterrupt, i))
            hostIntfClearInterrupt(i);
    }
}

void hostIntfSetInterrupt(uint32_t bit)
{
    uint64_t state = cpuIntsOff();
    if (mHostIntfTid) {
        if (!atomicBitsetGetBit(mInterrupt, bit)) {
            atomicBitsetSetBit(mInterrupt, bit);
            if (!atomicBitsetGetBit(mInterruptMask, bit)) {
                if (mInterruptCntWkup++ == 0)
                    apIntSet(true);
            } else {
                if (mInterruptCntNonWkup++ == 0)
                    apIntSet(false);
            }
        }
    }
    cpuIntsRestore(state);
}

bool hostIntfGetInterrupt(uint32_t bit)
{
    return atomicBitsetGetBit(mInterrupt, bit);
}

void hostIntfClearInterrupt(uint32_t bit)
{
    uint64_t state = cpuIntsOff();
    if (mHostIntfTid) {
        if (atomicBitsetGetBit(mInterrupt, bit)) {
            atomicBitsetClearBit(mInterrupt, bit);
            if (!atomicBitsetGetBit(mInterruptMask, bit)) {
                if (--mInterruptCntWkup == 0)
                    apIntClear(true);
            } else {
                if (--mInterruptCntNonWkup == 0)
                    apIntClear(false);
            }
        }
    }
    cpuIntsRestore(state);
}

void hostIntfSetInterruptMask(uint32_t bit)
{
    uint64_t state = cpuIntsOff();
    if (mHostIntfTid) {
        if (!atomicBitsetGetBit(mInterruptMask, bit)) {
            atomicBitsetSetBit(mInterruptMask, bit);
            if (atomicBitsetGetBit(mInterrupt, bit)) {
                if (--mInterruptCntWkup == 0)
                    apIntClear(true);
                if (mInterruptCntNonWkup++ == 0)
                    apIntSet(false);
            }
        }
    }
    cpuIntsRestore(state);
}

bool hostIntfGetInterruptMask(uint32_t bit)
{
    return atomicBitsetGetBit(mInterruptMask, bit);
}

void hostIntfClearInterruptMask(uint32_t bit)
{
    uint64_t state = cpuIntsOff();
    if (mHostIntfTid) {
        if (atomicBitsetGetBit(mInterruptMask, bit)) {
            atomicBitsetClearBit(mInterruptMask, bit);
            if (atomicBitsetGetBit(mInterrupt, bit)) {
                if (mInterruptCntWkup++ == 0)
                    apIntSet(true);
                if (--mInterruptCntNonWkup == 0)
                    apIntClear(false);
            }
        }
    }
    cpuIntsRestore(state);
}

INTERNAL_APP_INIT(APP_ID_MAKE(NANOHUB_VENDOR_GOOGLE, 0), 0, hostIntfRequest, hostIntfRelease, hostIntfHandleEvent);
