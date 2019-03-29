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
#include <spi.h>

static uint8_t gBusId;
static struct SpiDevice *gSpi;

static void *gRxBuf;
static size_t gTxSize;

static struct SpiPacket gPacket;

static const struct SpiMode gSpiMode = {
    .cpol = SPI_CPOL_IDLE_LO,
    .cpha = SPI_CPHA_LEADING_EDGE,
    .bitsPerWord = 8,
    .format = SPI_FORMAT_MSB_FIRST,
    .txWord = NANOHUB_PREAMBLE_BYTE,
};

static void hostIntfSpiRxCallback(void *cookie, int err)
{
    struct NanohubPacket *packet = gRxBuf;
    HostIntfCommCallbackF callback = cookie;
    callback(NANOHUB_PACKET_SIZE(packet->len), err);
}

static void hostIntfSpiTxCallback(void *cookie, int err)
{
    HostIntfCommCallbackF callback = cookie;
    callback(gTxSize, err);
}

static int hostIntfSpiRequest()
{
    return spiSlaveRequest(gBusId, &gSpiMode, &gSpi);
}

static int hostIntfSpiRxPacket(void *rxBuf, size_t rxSize,
        HostIntfCommCallbackF callback)
{
    int err;

    gPacket.rxBuf = gRxBuf = rxBuf;
    gPacket.txBuf = NULL;
    gPacket.size = rxSize;

    err = spiSlaveRxTx(gSpi, &gPacket, 1, hostIntfSpiRxCallback, callback);
    if (err < 0)
        callback(0, err);

    return 0;
}

static int hostIntfSpiTxPacket(const void *txBuf, size_t txSize,
        HostIntfCommCallbackF callback)
{
    ((uint8_t *)txBuf)[txSize] = NANOHUB_PREAMBLE_BYTE;
    gTxSize = txSize;

    gPacket.rxBuf = NULL;
    gPacket.txBuf = txBuf;
    gPacket.size = txSize + 1;

    return spiSlaveRxTx(gSpi, &gPacket, 1, hostIntfSpiTxCallback,
            callback);
}

static int hostIntfSpiRelease(void)
{
    return spiSlaveRelease(gSpi);
}

static const struct HostIntfComm gSpiComm = {
   .request = hostIntfSpiRequest,
   .rxPacket = hostIntfSpiRxPacket,
   .txPacket = hostIntfSpiTxPacket,
   .release = hostIntfSpiRelease,
};

const struct HostIntfComm *hostIntfSpiInit(uint8_t busId)
{
    gBusId = busId;
    return &gSpiComm;
}
