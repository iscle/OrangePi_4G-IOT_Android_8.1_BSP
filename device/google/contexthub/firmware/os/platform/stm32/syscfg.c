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

#include <plat/pwr.h>
#include <plat/syscfg.h>

#define SYSCFG_REG_SHIFT 2

struct StmSyscfg
{
    volatile uint32_t MEMRMP;
    volatile uint32_t PMC;
    volatile uint32_t EXTICR[4];
    volatile uint32_t CMPCR;
};

void syscfgSetExtiPort(const struct Gpio *__restrict gpioHandle)
{
    if (gpioHandle) {
        uint32_t gpioNum = (uint32_t)gpioHandle - GPIO_HANDLE_OFFSET;
        struct StmSyscfg *block = (struct StmSyscfg *)SYSCFG_BASE;
        const uint32_t bankNo = gpioNum >> GPIO_PORT_SHIFT;
        const uint32_t pinNo = gpioNum & GPIO_PIN_MASK;
        const uint32_t regNo = pinNo >> SYSCFG_REG_SHIFT;
        const uint32_t nibbleNo = pinNo & ((1UL << SYSCFG_REG_SHIFT) - 1UL);
        const uint32_t shift_4b = nibbleNo << 2UL;
        const uint32_t mask_4b = 0x0FUL << shift_4b;

        pwrUnitClock(PERIPH_BUS_APB2, PERIPH_APB2_SYSCFG, true);

        block->EXTICR[regNo] = (block->EXTICR[regNo] & ~mask_4b) | (bankNo << shift_4b);
    }
}
