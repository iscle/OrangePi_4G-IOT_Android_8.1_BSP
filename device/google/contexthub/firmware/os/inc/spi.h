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

#ifndef __SPI_H
#define __SPI_H

#include <inttypes.h>
#include <seos.h>
#include <stdlib.h>

struct SpiDevice;

typedef uint8_t spi_cs_t;
typedef uint32_t SpiSpeed;

typedef void (*SpiCbkF)(void *cookie, int err);

struct SpiMode {
    enum {
        SPI_CPOL_IDLE_LO,
        SPI_CPOL_IDLE_HI,
    } cpol;

    enum {
        SPI_CPHA_LEADING_EDGE,
        SPI_CPHA_TRAILING_EDGE,
    } cpha;

    uint8_t bitsPerWord;
    enum {
        SPI_FORMAT_LSB_FIRST,
        SPI_FORMAT_MSB_FIRST,
    } format;

    uint16_t txWord;

    SpiSpeed speed;

    bool nssChange;
};

struct SpiPacket {
    void *rxBuf;
    const void *txBuf;
    size_t size;
    uint32_t delay;
};

/**
 * NOTE:
 *
 * To avoid copies, spiMasterRxTx() and spiSlaveRxTx() transfer ownership of
 * packets[] to the SPI driver.  The SPI driver returns ownership when the
 * callback is called.
 *
 * The caller MUST NOT pass packets[] allocated on the stack, and MUST NOT
 * deallocate or otherwise mutate packets[] in the meantime.
 */

int spiMasterRequest(uint8_t busId, struct SpiDevice **dev);

int spiMasterRxTx(struct SpiDevice *dev, spi_cs_t cs,
        const struct SpiPacket packets[], size_t n,
        const struct SpiMode *mode, SpiCbkF callback,
        void *cookie);

int spiMasterRelease(struct SpiDevice *dev);

int spiSlaveRequest(uint8_t busId, const struct SpiMode *mode,
        struct SpiDevice **dev);

int spiSlaveRxTx(struct SpiDevice *dev,
        const struct SpiPacket packets[], size_t n,
        SpiCbkF callback, void *cookie);

int spiSlaveWaitForInactive(struct SpiDevice *dev, SpiCbkF callback,
        void *cookie);

int spiSlaveRelease(struct SpiDevice *dev);
#endif /* __SPI_H */
