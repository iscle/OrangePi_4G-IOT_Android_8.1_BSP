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

#ifndef __PLAT_I2C_H
#define __PLAT_I2C_H

#include <gpio.h>
#include <platform.h>
#include <plat/cmsis.h>
#include <plat/gpio.h>
#include <plat/plat.h>

struct StmI2cDmaCfg {
    uint8_t channel;
    uint8_t stream;
};

struct StmI2cGpioCfg {
    uint8_t gpioNum;
    enum StmGpioAltFunc func;
};

struct StmI2cBoardCfg {
    struct StmI2cGpioCfg gpioScl;
    struct StmI2cGpioCfg gpioSda;

    enum StmGpioSpeed gpioSpeed;
    enum GpioPullMode gpioPull;

    struct StmI2cDmaCfg dmaRx;
    struct StmI2cDmaCfg dmaTx;

    enum PlatSleepDevID sleepDev;
};

#define I2C_DMA_BUS         0

#define I2C1_GPIO_SCL_PB6   { .gpioNum = GPIO_PB(6), .func = GPIO_AF_I2C1 }
#define I2C1_GPIO_SCL_PB8   { .gpioNum = GPIO_PB(8), .func = GPIO_AF_I2C1 }
#define I2C1_GPIO_SDA_PB7   { .gpioNum = GPIO_PB(7), .func = GPIO_AF_I2C1 }
#define I2C1_GPIO_SDA_PB9   { .gpioNum = GPIO_PB(9), .func = GPIO_AF_I2C1 }
#define I2C1_DMA_RX_CFG_A   { .channel = 1, .stream = 0 }
#define I2C1_DMA_RX_CFG_B   { .channel = 1, .stream = 5 }
#define I2C1_DMA_TX_CFG_A   { .channel = 0, .stream = 1 }
#define I2C1_DMA_TX_CFG_B   { .channel = 1, .stream = 6 }
#define I2C1_DMA_TX_CFG_C   { .channel = 1, .stream = 7 }

#define I2C2_GPIO_SCL_PB10  { .gpioNum = GPIO_PB(10), .func = GPIO_AF_I2C2_A }
#define I2C2_GPIO_SDA_PB3   { .gpioNum = GPIO_PB(3), .func = GPIO_AF_I2C2_B }
#define I2C2_GPIO_SDA_PB9   { .gpioNum = GPIO_PB(9), .func = GPIO_AF_I2C2_B }
#define I2C2_GPIO_SDA_PB11  { .gpioNum = GPIO_PB(11), .func = GPIO_AF_I2C2_A }
#define I2C2_DMA_RX_CFG_A   { .channel = 7, .stream = 2 }
#define I2C2_DMA_RX_CFG_B   { .channel = 7, .stream = 3 }
#define I2C2_DMA_TX_CFG     { .channel = 7, .stream = 7 }

#define I2C3_GPIO_SCL_PA8   { .gpioNum = GPIO_PA(8), .func = GPIO_AF_I2C3_A }
#define I2C3_GPIO_SDA_PB4   { .gpioNum = GPIO_PB(4), .func = GPIO_AF_I2C3_B }
#define I2C3_GPIO_SDA_PC9   { .gpioNum = GPIO_PC(9), .func = GPIO_AF_I2C3_A }
#define I2C3_DMA_RX_CFG_A   { .channel = 1, .stream = 1 }
#define I2C3_DMA_RX_CFG_B   { .channel = 3, .stream = 2 }
#define I2C3_DMA_TX_CFG_A   { .channel = 3, .stream = 4 }
#define I2C3_DMA_TX_CFG_B   { .channel = 6, .stream = 5 }

extern const struct StmI2cBoardCfg *boardStmI2cCfg(uint8_t busId);

#endif /* __PLAT_I2C_H */
