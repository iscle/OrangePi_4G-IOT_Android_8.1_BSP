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

#ifndef GPIO_H_
#define GPIO_H_

#ifdef __cplusplus
extern "C" {
#endif

#include <stdint.h>
#include <stdbool.h>

enum GpioMode
{
    GPIO_MODE_IN = 0,
    GPIO_MODE_OUT,
    GPIO_MODE_ALTERNATE,
    GPIO_MODE_ANALOG,
};

enum GpioOpenDrainMode
{
    GPIO_OUT_PUSH_PULL = 0,
    GPIO_OUT_OPEN_DRAIN,
};

enum GpioPullMode
{
    GPIO_PULL_NONE = 0,
    GPIO_PULL_UP,
    GPIO_PULL_DOWN,
};

#define GPIO_SPEED_BEST_POWER      -1 //all non-negative values are platform specific, YMMV
#define GPIO_SPEED_BEST_SPEED      -2
#define GPIO_SPEED_DEFAULT         -3
#define GPIO_SPEED_1MHZ_PLUS       -4
#define GPIO_SPEED_3MHZ_PLUS       -5
#define GPIO_SPEED_5MHZ_PLUS       -6
#define GPIO_SPEED_10MHZ_PLUS      -7
#define GPIO_SPEED_15MHZ_PLUS      -8
#define GPIO_SPEED_20MHZ_PLUS      -9
#define GPIO_SPEED_30MHZ_PLUS      -10
#define GPIO_SPEED_50MHZ_PLUS      -11
#define GPIO_SPEED_100MHZ_PLUS     -12
#define GPIO_SPEED_150MHZ_PLUS     -13
#define GPIO_SPEED_200MHZ_PLUS     -14


struct Gpio;

/* Requests a GPIO and allocates the gpio handle/struct/etc */
struct Gpio* gpioRequest(uint32_t gpioNum);
void gpioRelease(struct Gpio* __restrict gpio);

/* Configures the direction and pull type of a GPIO */
void gpioConfigInput(const struct Gpio* __restrict gpio, int32_t gpioSpeed, enum GpioPullMode pull);
void gpioConfigOutput(const struct Gpio* __restrict gpio, int32_t gpioSpeed, enum GpioPullMode pull, enum GpioOpenDrainMode odrMode, bool value);
void gpioConfigAlt(const struct Gpio* __restrict gpio, int32_t gpioSpeed, enum GpioPullMode pull, enum GpioOpenDrainMode odrMode, uint32_t altFunc);
void gpioConfigAnalog(const struct Gpio* __restrict gpio);

/* Sets and gets a value for a specific GPIO pin */
void gpioSet(const struct Gpio* __restrict gpio, bool value);
bool gpioGet(const struct Gpio* __restrict gpio);

#ifdef __cplusplus
}
#endif

#endif
