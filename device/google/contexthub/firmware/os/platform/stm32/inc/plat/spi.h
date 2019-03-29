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

#ifndef __PLAT_SPI_H
#define __PLAT_SPI_H

#include <gpio.h>
#include <platform.h>
#include <plat/cmsis.h>
#include <plat/gpio.h>
#include <plat/plat.h>

struct StmSpiDmaCfg {
    uint8_t channel;
    uint8_t stream;
};

struct StmSpiBoardCfg {
    uint8_t gpioMiso;
    uint8_t gpioMosi;
    uint8_t gpioSclk;
    uint8_t gpioNss;

    enum StmGpioAltFunc gpioFunc;
    enum StmGpioSpeed gpioSpeed;
    enum GpioPullMode gpioPull;

    IRQn_Type irqNss;

    struct StmSpiDmaCfg dmaRx;
    struct StmSpiDmaCfg dmaTx;

    enum PlatSleepDevID sleepDev;
};

#define SPI1_DMA_BUS        1
#define SPI1_DMA_RX_CFG_A   { .channel = 3, .stream = 0 }
#define SPI1_DMA_RX_CFG_B   { .channel = 3, .stream = 2 }
#define SPI1_DMA_TX_CFG_A   { .channel = 3, .stream = 3 }
#define SPI1_DMA_TX_CFG_B   { .channel = 3, .stream = 5 }

#define SPI2_DMA_BUS        0
#define SPI2_DMA_RX_CFG     { .channel = 0, .stream = 3 }
#define SPI2_DMA_TX_CFG     { .channel = 0, .stream = 4 }

#define SPI3_DMA_BUS        0
#define SPI3_DMA_RX_CFG_A   { .channel = 0, .stream = 0 }
#define SPI3_DMA_RX_CFG_B   { .channel = 0, .stream = 2 }
#define SPI3_DMA_TX_CFG_A   { .channel = 0, .stream = 5 }
#define SPI3_DMA_TX_CFG_B   { .channel = 0, .stream = 7 }

#define SPI4_DMA_BUS        1
#define SPI4_DMA_RX_CFG_A   { .channel = 4, .stream = 0 }
#define SPI4_DMA_RX_CFG_B   { .channel = 5, .stream = 3 }
#define SPI4_DMA_TX_CFG_A   { .channel = 4, .stream = 1 }
#define SPI4_DMA_TX_CFG_B   { .channel = 5, .stream = 4 }

#define SPI5_DMA_BUS        1
#define SPI5_DMA_RX_CFG_A   { .channel = 2, .stream = 3 }
#define SPI5_DMA_RX_CFG_B   { .channel = 7, .stream = 5 }
#define SPI5_DMA_TX_CFG_A   { .channel = 2, .stream = 4 }
#define SPI5_DMA_TX_CFG_B   { .channel = 7, .stream = 6 }

#define SPI6_DMA_BUS        1
#define SPI6_DMA_RX_CFG     { .channel = 1, .stream = 6 }
#define SPI6_DMA_TX_CFG     { .channel = 1, .stream = 5 }

extern const struct StmSpiBoardCfg *boardStmSpiCfg(uint8_t busId);
const enum IRQn spiRxIrq(uint8_t busId);
const enum IRQn spiTxIrq(uint8_t busId);

#endif /* __PLAT_SPI_H */
