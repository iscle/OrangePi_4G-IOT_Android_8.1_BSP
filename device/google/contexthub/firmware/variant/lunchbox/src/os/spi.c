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

#include <plat/spi.h>
#include <util.h>

static const struct StmSpiBoardCfg mStmSpiBoardCfgs[] = {
    [0] = {
        .gpioMiso = GPIO_PA(6),
        .gpioMosi = GPIO_PA(7),
        .gpioSclk = GPIO_PA(5),
        .gpioNss = GPIO_PA(4),

        .gpioFunc = GPIO_AF_SPI1,
        .gpioSpeed = GPIO_SPEED_MEDIUM,

        .irqNss = -1,

        .dmaRx = SPI1_DMA_RX_CFG_B,
        .dmaTx = SPI1_DMA_TX_CFG_B,

        .sleepDev = -1,
    },
    [1] = {
        .gpioMiso = GPIO_PB(14),
        .gpioMosi = GPIO_PB(15),
        .gpioSclk = GPIO_PB(13),
        .gpioNss = GPIO_PB(12),

        .gpioSpeed = GPIO_SPEED_MEDIUM,
        .gpioFunc = GPIO_AF_SPI2_A,

        .irqNss = EXTI15_10_IRQn,

        .dmaRx = SPI2_DMA_RX_CFG,
        .dmaTx = SPI2_DMA_TX_CFG,

        .sleepDev = Stm32sleepDevSpi2,
    },
};

const struct StmSpiBoardCfg *boardStmSpiCfg(uint8_t busId)
{
    if (busId >= ARRAY_SIZE(mStmSpiBoardCfgs))
        return NULL;

    return &mStmSpiBoardCfgs[busId];
}
