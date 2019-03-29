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

#include <hostIntf.h>
#include <hostIntf_priv.h>
#include <nanohubPacket.h>
#include <i2c.h>

#define NANOHUB_I2C_SLAVE_ADDRESS     0x55
static uint32_t gBusId;

static void hostIntfI2cPreambleCallback(void *cookie, size_t tx, size_t rx, int err)
{
}

static void hostIntfI2cRxCallback(void *cookie, size_t tx, size_t rx, int err)
{
    HostIntfCommCallbackF callback = cookie;
    i2cSlaveTxPreamble(gBusId, NANOHUB_PREAMBLE_BYTE,
            hostIntfI2cPreambleCallback, NULL);
    callback(rx, err);
}

static void hostIntfI2cTxCallback(void *cookie, size_t tx, size_t rx, int err)
{
    HostIntfCommCallbackF callback = cookie;
    i2cSlaveTxPreamble(gBusId, NANOHUB_PREAMBLE_BYTE,
            hostIntfI2cPreambleCallback, NULL);
    callback(tx, err);
}

static int hostIntfI2cRequest()
{
    return i2cSlaveRequest(gBusId, NANOHUB_I2C_SLAVE_ADDRESS);
}

static int hostIntfI2cRxPacket(void *rxBuf, size_t rxSize,
        HostIntfCommCallbackF callback)
{
    i2cSlaveEnableRx(gBusId, rxBuf, rxSize, hostIntfI2cRxCallback,
            callback);
    return 0;
}

static int hostIntfI2cTxPacket(const void *txBuf, size_t txSize,
        HostIntfCommCallbackF callback)
{
    return i2cSlaveTxPacket(gBusId, txBuf, txSize, hostIntfI2cTxCallback,
            callback);
}

static int hostIntfI2cRelease(void)
{
    return i2cSlaveRelease(gBusId);
}

static const struct HostIntfComm gI2cComm = {
   .request = hostIntfI2cRequest,
   .rxPacket = hostIntfI2cRxPacket,
   .txPacket = hostIntfI2cTxPacket,
   .release = hostIntfI2cRelease,
};

const struct HostIntfComm *hostIntfI2cInit(uint32_t busId)
{
    gBusId = busId;
    return &gI2cComm;
}
