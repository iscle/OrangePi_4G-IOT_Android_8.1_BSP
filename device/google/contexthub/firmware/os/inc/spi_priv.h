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

#ifndef __SPI_PRIV_H
#define __SPI_PRIV_H

#include <spi.h>
#include <seos.h>

struct SpiDevice {
    const struct SpiDevice_ops *ops;
    void *pdata;
};

struct SpiDevice_ops {
    int (*masterStartSync)(struct SpiDevice *dev, spi_cs_t cs,
            const struct SpiMode *mode);
    int (*masterStartAsync)(struct SpiDevice *dev, spi_cs_t cs,
            const struct SpiMode *mode);

    int (*masterRxTx)(struct SpiDevice *dev, void *rxBuf, const void *txBuf,
            size_t size, const struct SpiMode *mode);

    int (*masterStopSync)(struct SpiDevice *dev);
    int (*masterStopAsync)(struct SpiDevice *dev);

    int (*slaveStartSync)(struct SpiDevice *dev, const struct SpiMode *mode);
    int (*slaveStartAsync)(struct SpiDevice *dev, const struct SpiMode *mode);

    int (*slaveIdle)(struct SpiDevice *dev, const struct SpiMode *mode);
    int (*slaveRxTx)(struct SpiDevice *dev, void *rxBuf, const void *txBuf,
            size_t size, const struct SpiMode *mode);

    void (*slaveSetCsInterrupt)(struct SpiDevice *dev, bool enabled);
    bool (*slaveCsIsActive)(struct SpiDevice *dev);

    int (*slaveStopSync)(struct SpiDevice *dev);
    int (*slaveStopAsync)(struct SpiDevice *dev);

    int (*release)(struct SpiDevice *dev);
};

int spiRequest(struct SpiDevice *dev, uint8_t busId);

void spi_masterStartAsync_done(struct SpiDevice *dev, int err);
void spiMasterRxTxDone(struct SpiDevice *dev, int err);
void spiMasterStopAsyncDone(struct SpiDevice *dev, int err);

void spiSlaveStartAsyncDone(struct SpiDevice *dev, int err);
void spiSlaveRxTxDone(struct SpiDevice *dev, int err);
void spiSlaveCsInactive(struct SpiDevice *dev);
void spiSlaveStopAsyncDone(struct SpiDevice *dev, int err);

#endif /* __SPI_PRIV_H */
