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

#include <plat/gpio.h>
#include <plat/pwr.h>
#include <gpio.h>
#include <cpu.h>

struct StmGpio {
    volatile uint32_t MODER;
    volatile uint32_t OTYPER;
    volatile uint32_t OSPEEDR;
    volatile uint32_t PUPDR;
    volatile uint32_t IDR;
    volatile uint32_t ODR;
    volatile uint32_t BSRR;
    volatile uint32_t LCKR;
    volatile uint32_t AFR[2];
};

static const uint32_t mGpioPeriphs[] = {
    PERIPH_AHB1_GPIOA,
    PERIPH_AHB1_GPIOB,
    PERIPH_AHB1_GPIOC,
    PERIPH_AHB1_GPIOD,
    PERIPH_AHB1_GPIOE,
    PERIPH_AHB1_GPIOF,
    PERIPH_AHB1_GPIOG,
    PERIPH_AHB1_GPIOH,
    PERIPH_AHB1_GPIOI,
};

static const uint32_t mGpioBases[] = {
    GPIOA_BASE,
    GPIOB_BASE,
    GPIOC_BASE,
    GPIOD_BASE,
    GPIOE_BASE,
    GPIOF_BASE,
    GPIOG_BASE,
    GPIOH_BASE,
    GPIOI_BASE,
};

static void gpioSetWithNum(uint32_t gpioNum, bool value);


struct Gpio* gpioRequest(uint32_t number)
{
    return (struct Gpio*)(((uintptr_t)number) + GPIO_HANDLE_OFFSET);
}

void gpioRelease(struct Gpio* __restrict gpio)
{
    (void)gpio;
}

static enum StmGpioSpeed gpioSpeedFromRequestedSpeed(int32_t requestedSpeed)
{
    static const enum StmGpioSpeed mStandardSpeeds[] = {
        [-1 - GPIO_SPEED_BEST_POWER  ] = GPIO_SPEED_LOW,
        [-1 - GPIO_SPEED_BEST_SPEED  ] = GPIO_SPEED_HIGH,
        [-1 - GPIO_SPEED_DEFAULT     ] = GPIO_SPEED_MEDIUM,
        [-1 - GPIO_SPEED_1MHZ_PLUS   ] = GPIO_SPEED_LOW,
        [-1 - GPIO_SPEED_3MHZ_PLUS   ] = GPIO_SPEED_LOW,
        [-1 - GPIO_SPEED_5MHZ_PLUS   ] = GPIO_SPEED_MEDIUM,
        [-1 - GPIO_SPEED_10MHZ_PLUS  ] = GPIO_SPEED_MEDIUM,
        [-1 - GPIO_SPEED_15MHZ_PLUS  ] = GPIO_SPEED_MEDIUM,
        [-1 - GPIO_SPEED_20MHZ_PLUS  ] = GPIO_SPEED_MEDIUM,
        [-1 - GPIO_SPEED_30MHZ_PLUS  ] = GPIO_SPEED_FAST,
        [-1 - GPIO_SPEED_50MHZ_PLUS  ] = GPIO_SPEED_FAST,
        [-1 - GPIO_SPEED_100MHZ_PLUS ] = GPIO_SPEED_FAST,
        [-1 - GPIO_SPEED_150MHZ_PLUS ] = GPIO_SPEED_FAST,  //this is not fast enough, but it is all we can do
        [-1 - GPIO_SPEED_150MHZ_PLUS ] = GPIO_SPEED_FAST,  //this is not fast enough, but it is all we can do
    };

    if (requestedSpeed >= 0)
        return requestedSpeed;
    else
        return mStandardSpeeds[-requestedSpeed - 1];
}

static void gpioConfigWithNum(uint32_t gpioNum, int32_t gpioSpeed, enum GpioPullMode pull, enum GpioOpenDrainMode output)
{
    struct StmGpio *block = (struct StmGpio*)mGpioBases[gpioNum >> GPIO_PORT_SHIFT];
    const uint32_t shift_1b = gpioNum & GPIO_PIN_MASK;
    const uint32_t shift_2b = (gpioNum & GPIO_PIN_MASK) * 2;
    const uint32_t mask_1b = (1UL << shift_1b);
    const uint32_t mask_2b = (3UL << shift_2b);

    /* unit clock */
    pwrUnitClock(PERIPH_BUS_AHB1, mGpioPeriphs[gpioNum >> GPIO_PORT_SHIFT], true);

    /* speed */
    block->OSPEEDR = (block->OSPEEDR & ~mask_2b) | (((uint32_t)gpioSpeedFromRequestedSpeed(gpioSpeed)) << shift_2b);

    /* pull ups/downs */
    block->PUPDR = (block->PUPDR & ~mask_2b) | (((uint32_t)pull) << shift_2b);
    /* push/pull or open drain */
    if (output == GPIO_OUT_PUSH_PULL)
        block->OTYPER &= ~mask_1b;
    else
        block->OTYPER |= mask_1b;
}

static void gpioConfigInputWithNum(uint32_t gpioNum, int32_t gpioSpeed, enum GpioPullMode pull)
{
    struct StmGpio *block = (struct StmGpio*)mGpioBases[gpioNum >> GPIO_PORT_SHIFT];
    const uint32_t shift_2b = (gpioNum & GPIO_PIN_MASK) * 2;
    const uint32_t mask_2b = (3UL << shift_2b);

    gpioConfigWithNum(gpioNum, gpioSpeed, pull, GPIO_OUT_PUSH_PULL);

    /* direction */
    block->MODER = (block->MODER & ~mask_2b) | (((uint32_t)GPIO_MODE_IN) << shift_2b);
}

void gpioConfigInput(const struct Gpio* __restrict gpioHandle, int32_t gpioSpeed, enum GpioPullMode pull)
{
    if (gpioHandle)
        gpioConfigInputWithNum((uint32_t)gpioHandle - GPIO_HANDLE_OFFSET, gpioSpeed, pull);
}

static void gpioConfigOutputWithNum(uint32_t gpioNum, int32_t gpioSpeed, enum GpioPullMode pull, enum GpioOpenDrainMode output, bool value)
{
    struct StmGpio *block = (struct StmGpio*)mGpioBases[gpioNum >> GPIO_PORT_SHIFT];
    const uint32_t shift_2b = (gpioNum & GPIO_PIN_MASK) * 2;
    const uint32_t mask_2b = (3UL << shift_2b);

    gpioConfigWithNum(gpioNum, gpioSpeed, pull, output);

    /* set the initial output value */
    gpioSetWithNum(gpioNum, value);

    /* direction */
    block->MODER = (block->MODER & ~mask_2b) | (((uint32_t)GPIO_MODE_OUT) << shift_2b);
}

void gpioConfigOutput(const struct Gpio* __restrict gpioHandle, int32_t gpioSpeed, enum GpioPullMode pull, enum GpioOpenDrainMode output, bool value)
{
    if (gpioHandle)
        gpioConfigOutputWithNum((uint32_t)gpioHandle - GPIO_HANDLE_OFFSET, gpioSpeed, pull, output, value);
}

static void gpioConfigAltWithNum(uint32_t gpioNum, int32_t gpioSpeed, enum GpioPullMode pull, enum GpioOpenDrainMode output, uint32_t altFunc)
{
    struct StmGpio *block = (struct StmGpio*)mGpioBases[gpioNum >> GPIO_PORT_SHIFT];
    const uint32_t pinNo = gpioNum & GPIO_PIN_MASK;
    const uint32_t regNo = pinNo >> (GPIO_PORT_SHIFT - 1);
    const uint32_t nibbleNo = pinNo & (GPIO_PIN_MASK >> 1);
    const uint32_t shift_2b = pinNo * 2;
    const uint32_t shift_4b = nibbleNo * 4;
    const uint32_t mask_2b = (3UL << shift_2b);
    const uint32_t mask_4b = (15UL << shift_4b);

    gpioConfigWithNum(gpioNum, gpioSpeed, pull, output);

    /* assign function */
    block->AFR[regNo] = (block->AFR[regNo] & ~mask_4b) | (((uint32_t)altFunc) << shift_4b);

    /* direction */
    block->MODER = (block->MODER & ~mask_2b) | (((uint32_t)GPIO_MODE_ALTERNATE) << shift_2b);
}

void gpioConfigAlt(const struct Gpio* __restrict gpioHandle, int32_t gpioSpeed, enum GpioPullMode pull, enum GpioOpenDrainMode output, uint32_t altFunc)
{
    if (gpioHandle)
        gpioConfigAltWithNum((uint32_t)gpioHandle - GPIO_HANDLE_OFFSET, gpioSpeed, pull, output, altFunc);
}

static void gpioConfigAnalogWithNum(uint32_t gpioNum)
{
    struct StmGpio *block = (struct StmGpio*)mGpioBases[gpioNum >> GPIO_PORT_SHIFT];
    const uint32_t pinNo = gpioNum & GPIO_PIN_MASK;
    const uint32_t shift_2b = pinNo * 2;
    const uint32_t mask_2b = (3UL << shift_2b);

    gpioConfigWithNum(gpioNum, GPIO_SPEED_LOW, GPIO_PULL_NONE, GPIO_OUT_OPEN_DRAIN);

    /* I/O configuration */
    block->MODER = (block->MODER & ~mask_2b) | (((uint32_t)GPIO_MODE_ANALOG) << shift_2b);
}

void gpioConfigAnalog(const struct Gpio* __restrict gpioHandle)
{
    if (gpioHandle)
        gpioConfigAnalogWithNum((uint32_t)gpioHandle - GPIO_HANDLE_OFFSET);
}

static void gpioSetWithNum(uint32_t gpioNum, bool value)
{
    struct StmGpio *block = (struct StmGpio*)mGpioBases[gpioNum >> GPIO_PORT_SHIFT];
    const uint32_t shift_1b = gpioNum & GPIO_PIN_MASK;
    const uint32_t mask_set_1b = (1UL << (0  + shift_1b));
    const uint32_t mask_clr_1b = (1UL << (16 + shift_1b));

    block->BSRR = value ? mask_set_1b : mask_clr_1b;
}

void gpioSet(const struct Gpio* __restrict gpioHandle, bool value)
{
    if (gpioHandle)
        gpioSetWithNum((uint32_t)gpioHandle - GPIO_HANDLE_OFFSET, value);
}

static bool gpioGetWithNum(uint32_t gpioNum)
{
    struct StmGpio *block = (struct StmGpio*)mGpioBases[gpioNum >> GPIO_PORT_SHIFT];
    const uint32_t shift_1b = gpioNum & GPIO_PIN_MASK;
    const uint32_t mask_1b = (1UL << shift_1b);

    return !!(block->IDR & mask_1b);
}

bool gpioGet(const struct Gpio* __restrict gpioHandle)
{
    return gpioHandle ? gpioGetWithNum((uint32_t)gpioHandle - GPIO_HANDLE_OFFSET) : 0;
}


#ifdef DEBUG_UART_PIN

//this function makes more assumptions than i'd care to list, sorry...
void gpioBitbangedUartOut(uint32_t chr)
{
    static const uint32_t bsrrVals[] = {(1 << (DEBUG_UART_PIN & GPIO_PIN_MASK)) << 16, (1 << (DEBUG_UART_PIN & GPIO_PIN_MASK))};
    struct StmGpio *block = (struct StmGpio*)mGpioBases[DEBUG_UART_PIN >> GPIO_PORT_SHIFT];
    uint32_t bits[10], *bitsP = bits, base = (uint32_t)&block->BSRR;
    static bool setup = 0;
    uint64_t state;
    uint32_t i;

    if (!setup) {
        struct Gpio *gpio = gpioRequest(DEBUG_UART_PIN);

        if (!gpio)
            return;

        setup = true;
        gpioConfigOutput(gpio, GPIO_SPEED_HIGH, GPIO_PULL_NONE, GPIO_OUT_PUSH_PULL, true);
    }

    bits[0] = bsrrVals[0];
    for (i = 0; i < 8; i++, chr >>= 1)
        bits[i + 1] = bsrrVals[chr & 1];
    bits[9] = bsrrVals[1];

    #define SENDBIT "ldr %0, [%1], #4   \n\t"   \
   "str %0, [%2]   \n\t"   \
   "nop    \n\t"   \
   "nop    \n\t"   \
   "nop    \n\t"   \
   "nop    \n\t"   \
   "nop    \n\t"   \
   "nop    \n\t"

   state = cpuIntsOff();
   asm volatile(
       SENDBIT
       SENDBIT
       SENDBIT
       SENDBIT
       SENDBIT
       SENDBIT
       SENDBIT
       SENDBIT
       SENDBIT
       SENDBIT
       :"=r"(i), "=r"(bitsP), "=r"(base)
       :"0"(i), "1"(bitsP), "2"(base)
       :"memory","cc"
    );
    cpuIntsRestore(state);
}


#endif




