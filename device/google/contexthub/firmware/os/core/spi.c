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

#include <errno.h>
#include <heap.h>
#include <string.h>

#include <cpu.h>
#include <spi.h>
#include <spi_priv.h>
#include <timer.h>

#define INFO_PRINT(fmt, ...) do { \
        osLog(LOG_INFO, "%s " fmt, "[spi]", ##__VA_ARGS__); \
    } while (0);

#define ERROR_PRINT(fmt, ...) do { \
        osLog(LOG_ERROR, "%s " fmt, "[spi] ERROR:", ##__VA_ARGS__); \
    } while (0);

struct SpiDeviceState {
    struct SpiDevice dev;

    const struct SpiPacket *packets;
    size_t n;
    size_t currentBuf;
    struct SpiMode mode;
    uint16_t tid;

    SpiCbkF rxTxCallback;
    void *rxTxCookie;

    SpiCbkF finishCallback;
    void *finishCookie;

    int err;
};
#define SPI_DEVICE_TO_STATE(p) ((struct SpiDeviceState *)p)

static void spiMasterNext(struct SpiDeviceState *state);
static void spiMasterStop(struct SpiDeviceState *state);
static void spiMasterDone(struct SpiDeviceState *state, int err);

static void spiSlaveNext(struct SpiDeviceState *state);
static void spiSlaveIdle(struct SpiDeviceState *state, int err);
static void spiSlaveDone(struct SpiDeviceState *state);

static int spiMasterStart(struct SpiDeviceState *state,
        spi_cs_t cs, const struct SpiMode *mode)
{
    struct SpiDevice *dev = &state->dev;

    if (dev->ops->masterStartAsync)
        return dev->ops->masterStartAsync(dev, cs, mode);

    if (dev->ops->masterStartSync) {
        int err = dev->ops->masterStartSync(dev, cs, mode);
        if (err < 0)
            return err;
    }

    return dev->ops->masterRxTx(dev, state->packets[0].rxBuf,
            state->packets[0].txBuf, state->packets[0].size, mode);
}

void spi_masterStartAsync_done(struct SpiDevice *dev, int err)
{
    struct SpiDeviceState *state = SPI_DEVICE_TO_STATE(dev);
    if (err)
        spiMasterDone(state, err);
    else
        spiMasterNext(state);
}

static void spiDelayCallback(uint32_t timerId, void *data)
{
    spiMasterNext((struct SpiDeviceState *)data);
}

static void spiMasterNext(struct SpiDeviceState *state)
{
    struct SpiDevice *dev = &state->dev;

    if (state->currentBuf == state->n) {
        spiMasterStop(state);
        return;
    }

    size_t i = state->currentBuf;
    void *rxBuf = state->packets[i].rxBuf;
    const void *txBuf = state->packets[i].txBuf;
    size_t size = state->packets[i].size;
    const struct SpiMode *mode = &state->mode;

    int err = dev->ops->masterRxTx(dev, rxBuf, txBuf, size, mode);
    if (err)
        spiMasterDone(state, err);
}

void spiMasterRxTxDone(struct SpiDevice *dev, int err)
{
    struct SpiDeviceState *state = SPI_DEVICE_TO_STATE(dev);
    if (err) {
        spiMasterDone(state, err);
    } else {
        size_t i = state->currentBuf++;

        if (state->packets[i].delay > 0) {
            if (!timTimerSet(state->packets[i].delay, 0, 50, spiDelayCallback, state, true)) {
                ERROR_PRINT("Cannot do delayed spi, timer depleted\n");
                spiMasterDone(state, -ENOMEM); // should be out of timer; out of mem is close enough
            }
        } else {
            spiMasterNext(state);
        }
    }
}

static void spiMasterStop(struct SpiDeviceState *state)
{
    struct SpiDevice *dev = &state->dev;

    if (dev->ops->masterStopSync) {
        int err = dev->ops->masterStopSync(dev);
        spiMasterDone(state, err);
    } else if (dev->ops->masterStopAsync) {
        int err = dev->ops->masterStopAsync(dev);
        if (err < 0)
            spiMasterDone(state, err);
    } else {
        spiMasterDone(state, 0);
    }
}

void spiMasterStopAsyncDone(struct SpiDevice *dev, int err)
{
    struct SpiDeviceState *state = SPI_DEVICE_TO_STATE(dev);
    spiMasterDone(state, err);
}

static void spiMasterDone(struct SpiDeviceState *state, int err)
{
    SpiCbkF callback = state->rxTxCallback;
    void *cookie = state->rxTxCookie;

    uint16_t oldTid = osSetCurrentTid(state->tid);
    callback(cookie, err);
    osSetCurrentTid(oldTid);
}

static int spiSlaveStart(struct SpiDeviceState *state,
        const struct SpiMode *mode)
{
    struct SpiDevice *dev = &state->dev;

    if (dev->ops->slaveStartAsync)
        return dev->ops->slaveStartAsync(dev, mode);

    if (dev->ops->slaveStartSync) {
        int err = dev->ops->slaveStartSync(dev, mode);
        if (err < 0)
            return err;
    }

    return dev->ops->slaveIdle(dev, mode);
}

void spiSlaveStartAsyncDone(struct SpiDevice *dev, int err)
{
    struct SpiDeviceState *state = SPI_DEVICE_TO_STATE(dev);

    if (err)
        state->err = err;
    else
        state->err = dev->ops->slaveIdle(dev, &state->mode);
}

void spiSlaveRxTxDone(struct SpiDevice *dev, int err)
{
    struct SpiDeviceState *state = SPI_DEVICE_TO_STATE(dev);

    if (err) {
        spiSlaveIdle(state, err);
    } else {
        state->currentBuf++;
        spiSlaveNext(state);
    }
}

void spiSlaveCsInactive(struct SpiDevice *dev)
{
    struct SpiDeviceState *state = SPI_DEVICE_TO_STATE(dev);

    dev->ops->slaveSetCsInterrupt(dev, false);

    if (!state->finishCallback) {
        osLog(LOG_WARN, "%s called without callback\n", __func__);
        return;
    }

    SpiCbkF callback = state->finishCallback;
    void *cookie = state->finishCookie;
    state->finishCallback = NULL;
    state->finishCookie = NULL;

    uint16_t oldTid = osSetCurrentTid(state->tid);
    callback(cookie, 0);
    osSetCurrentTid(oldTid);
}

static void spiSlaveNext(struct SpiDeviceState *state)
{
    struct SpiDevice *dev = &state->dev;

    if (state->currentBuf == state->n) {
        spiSlaveIdle(state, 0);
        return;
    }

    size_t i = state->currentBuf;
    void *rxBuf = state->packets[i].rxBuf;
    const void *txBuf = state->packets[i].txBuf;
    size_t size = state->packets[i].size;
    const struct SpiMode *mode = &state->mode;

    int err = dev->ops->slaveRxTx(dev, rxBuf, txBuf, size, mode);
    if (err)
        spiSlaveIdle(state, err);
}

static void spiSlaveIdle(struct SpiDeviceState *state, int err)
{
    struct SpiDevice *dev = &state->dev;
    SpiCbkF callback = state->rxTxCallback;
    void *cookie = state->rxTxCookie;

    if (!err)
        err = dev->ops->slaveIdle(dev, &state->mode);

    uint16_t oldTid = osSetCurrentTid(state->tid);
    callback(cookie, err);
    osSetCurrentTid(oldTid);
}

void spiSlaveStopAsyncDone(struct SpiDevice *dev, int err)
{
    struct SpiDeviceState *state = SPI_DEVICE_TO_STATE(dev);
    spiSlaveDone(state);
}

static void spiSlaveDone(struct SpiDeviceState *state)
{
    struct SpiDevice *dev = &state->dev;

    if (dev->ops->release)
        dev->ops->release(dev);
    heapFree(state);
}

static int spiSetupRxTx(struct SpiDeviceState *state,
        const struct SpiPacket packets[], size_t n,
        SpiCbkF callback, void *cookie)
{
    state->packets = packets;
    state->n = n;
    state->currentBuf = 0;
    state->rxTxCallback = callback;
    state->rxTxCookie = cookie;
    state->tid = osGetCurrentTid();

    return 0;
}

int spiMasterRequest(uint8_t busId, struct SpiDevice **dev_out)
{
    int ret = 0;

    struct SpiDeviceState *state = heapAlloc(sizeof(*state));
    if (!state)
        return -ENOMEM;
    struct SpiDevice *dev = &state->dev;

    ret = spiRequest(dev, busId);
    if (ret < 0)
        goto err_request;

    if (!dev->ops->masterRxTx) {
        ret = -EOPNOTSUPP;
        goto err_opsupp;
    }

    *dev_out = dev;
    return 0;

err_opsupp:
    if (dev->ops->release)
        dev->ops->release(dev);
err_request:
    heapFree(state);
    return ret;
}

int spiMasterRxTx(struct SpiDevice *dev, spi_cs_t cs,
        const struct SpiPacket packets[], size_t n,
        const struct SpiMode *mode, SpiCbkF callback,
        void *cookie)
{
    struct SpiDeviceState *state = SPI_DEVICE_TO_STATE(dev);
    int ret = 0;

    if (!n)
        return -EINVAL;

    ret = spiSetupRxTx(state, packets, n, callback, cookie);
    if (ret < 0)
        return ret;

    state->mode = *mode;

    return spiMasterStart(state, cs, mode);
}

int spiMasterRelease(struct SpiDevice *dev)
{
    struct SpiDeviceState *state = SPI_DEVICE_TO_STATE(dev);

    if (dev->ops->release) {
        int ret = dev->ops->release(dev);
        if (ret < 0)
            return ret;
    }

    heapFree(state);
    return 0;
}

int spiSlaveRequest(uint8_t busId, const struct SpiMode *mode,
        struct SpiDevice **dev_out)
{
    int ret = 0;

    struct SpiDeviceState *state = heapAlloc(sizeof(*state));
    if (!state)
        return -ENOMEM;
    struct SpiDevice *dev = &state->dev;

    ret = spiRequest(dev, busId);
    if (ret < 0)
        goto err_request;

    if (!dev->ops->slaveIdle || !dev->ops->slaveRxTx) {
        ret = -EOPNOTSUPP;
        goto err_opsupp;
    }

    state->mode = *mode;
    state->err = 0;

    ret = spiSlaveStart(state, mode);
    if (ret < 0)
        goto err_opsupp;

    *dev_out = dev;
    return 0;

err_opsupp:
    if (dev->ops->release)
        dev->ops->release(dev);
err_request:
    heapFree(state);
    return ret;
}

int spiSlaveRxTx(struct SpiDevice *dev,
        const struct SpiPacket packets[], size_t n,
        SpiCbkF callback, void *cookie)
{
    struct SpiDeviceState *state = SPI_DEVICE_TO_STATE(dev);

    if (!n)
        return -EINVAL;

    if (state->err)
        return state->err;

    int ret = spiSetupRxTx(state, packets, n, callback, cookie);
    if (ret < 0)
        return ret;

    return dev->ops->slaveRxTx(dev, state->packets[0].rxBuf,
            state->packets[0].txBuf, state->packets[0].size, &state->mode);
}

int spiSlaveWaitForInactive(struct SpiDevice *dev, SpiCbkF callback,
        void *cookie)
{
    struct SpiDeviceState *state = SPI_DEVICE_TO_STATE(dev);

    if (!dev->ops->slaveSetCsInterrupt || !dev->ops->slaveCsIsActive)
        return -EOPNOTSUPP;

    state->finishCallback = callback;
    state->finishCookie = cookie;

    uint64_t flags = cpuIntsOff();
    dev->ops->slaveSetCsInterrupt(dev, true);

    /* CS may already be inactive before enabling the interrupt.  In this case
     * roll back and fire the callback immediately.
     *
     * Interrupts must be off while checking for this.  Otherwise there is a
     * (very unlikely) race where the CS interrupt fires between calling
     * slaveSetCsInterrupt(true) and the rollback
     * slaveSetCsInterrupt(false), causing the event to be handled twice.
     *
     * Likewise the check must come after enabling the interrupt.  Otherwise
     * there is an (also unlikely) race where CS goes inactive between reading
     * CS and enabling the interrupt, causing the event to be lost.
     */

    bool cs = dev->ops->slaveCsIsActive(dev);
    if (!cs) {
        dev->ops->slaveSetCsInterrupt(dev, false);
        cpuIntsRestore(flags);

        state->finishCallback = NULL;
        state->finishCookie = NULL;
        callback(cookie, 0);
        return 0;
    }

    cpuIntsRestore(flags);
    return 0;
}

int spiSlaveRelease(struct SpiDevice *dev)
{
    struct SpiDeviceState *state = SPI_DEVICE_TO_STATE(dev);
    int ret;

    if (dev->ops->slaveStopSync) {
        ret = dev->ops->slaveStopSync(dev);
        if (ret < 0)
            return ret;
    } else if (dev->ops->slaveStopAsync) {
        return dev->ops->slaveStopAsync(dev);
    }

    spiSlaveDone(state);
    return 0;
}
