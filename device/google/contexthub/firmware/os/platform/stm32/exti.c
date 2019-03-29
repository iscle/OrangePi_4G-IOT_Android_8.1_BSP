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

#include <errno.h>
#include <isr.h>
#include <platform.h>

#include <plat/cmsis.h>
#include <plat/exti.h>
#include <plat/pwr.h>

struct StmExti
{
    volatile uint32_t IMR;
    volatile uint32_t EMR;
    volatile uint32_t RTSR;
    volatile uint32_t FTSR;
    volatile uint32_t SWIER;
    volatile uint32_t PR;
};

#define EXTI ((struct StmExti*)EXTI_BASE)

void extiEnableIntLine(const enum ExtiLine line, enum ExtiTrigger trigger)
{
    if (trigger == EXTI_TRIGGER_BOTH) {
        EXTI->RTSR |= (1UL << line);
        EXTI->FTSR |= (1UL << line);
    } else if (trigger == EXTI_TRIGGER_RISING) {
        EXTI->RTSR |= (1UL << line);
        EXTI->FTSR &= ~(1UL << line);
    } else if (trigger == EXTI_TRIGGER_FALLING) {
        EXTI->RTSR &= ~(1UL << line);
        EXTI->FTSR |= (1UL << line);
    }

    /* Clear pending interrupt */
    extiClearPendingLine(line);

    /* Enable hardware interrupt */
    EXTI->IMR |= (1UL << line);
}

void extiDisableIntLine(const enum ExtiLine line)
{
    EXTI->IMR &= ~(1UL << line);
}

void extiClearPendingLine(const enum ExtiLine line)
{
    EXTI->PR = (1UL << line);
}

bool extiIsPendingLine(const enum ExtiLine line)
{
    return (EXTI->PR & (1UL << line)) ? true : false;
}

struct ExtiInterrupt
{
    struct ChainedInterrupt base;
    IRQn_Type irq;
};

static void extiInterruptEnable(struct ChainedInterrupt *irq)
{
    struct ExtiInterrupt *exti = container_of(irq, struct ExtiInterrupt, base);
    NVIC_EnableIRQ(exti->irq);
}

static void extiInterruptDisable(struct ChainedInterrupt *irq)
{
    struct ExtiInterrupt *exti = container_of(irq, struct ExtiInterrupt, base);
    NVIC_DisableIRQ(exti->irq);
}

#define DECLARE_SHARED_EXTI(i) {            \
    .base = {                               \
        .enable = extiInterruptEnable,      \
        .disable = extiInterruptDisable,    \
    },                                      \
    .irq = i,                               \
}

uint32_t mMaxLatency = 0;

static struct ExtiInterrupt mInterrupts[] = {
    DECLARE_SHARED_EXTI(EXTI0_IRQn),
    DECLARE_SHARED_EXTI(EXTI1_IRQn),
    DECLARE_SHARED_EXTI(EXTI2_IRQn),
    DECLARE_SHARED_EXTI(EXTI3_IRQn),
    DECLARE_SHARED_EXTI(EXTI4_IRQn),
    DECLARE_SHARED_EXTI(EXTI9_5_IRQn),
    DECLARE_SHARED_EXTI(EXTI15_10_IRQn),
};

static void extiUpdateMaxLatency(uint32_t maxLatencyNs)
{
    if (!maxLatencyNs && mMaxLatency)
        platReleaseDevInSleepMode(Stm32sleepDevExti);
    else if (maxLatencyNs && !mMaxLatency)
        platRequestDevInSleepMode(Stm32sleepDevExti, maxLatencyNs);
    else if (maxLatencyNs && mMaxLatency)
        platAdjustDevInSleepMode(Stm32sleepDevExti, maxLatencyNs);
    mMaxLatency = maxLatencyNs;
}

static void extiCalcMaxLatency()
{
    int i;
    uint32_t maxLatency, newMaxLatency = 0;
    struct ExtiInterrupt *exti = mInterrupts;

    for (i = 0; i < ARRAY_SIZE(mInterrupts); ++i, ++exti) {
        maxLatency = maxLatencyIsr(&exti->base);
        if (!newMaxLatency || (maxLatency && maxLatency < newMaxLatency))
            newMaxLatency = maxLatency;
    }
    extiUpdateMaxLatency(newMaxLatency);
}

static inline struct ExtiInterrupt *extiForIrq(IRQn_Type n)
{
    if (n >= EXTI0_IRQn && n <= EXTI4_IRQn)
        return &mInterrupts[n - EXTI0_IRQn];
    if (n == EXTI9_5_IRQn)
        return &mInterrupts[ARRAY_SIZE(mInterrupts) - 2];
    if (n == EXTI15_10_IRQn)
        return &mInterrupts[ARRAY_SIZE(mInterrupts) - 1];
    return NULL;
}

static void extiIrqHandler(IRQn_Type n)
{
    struct ExtiInterrupt *exti = extiForIrq(n);
    dispatchIsr(&exti->base);
}

#define DEFINE_SHARED_EXTI_ISR(i)           \
    void EXTI##i##_IRQHandler(void);        \
    void EXTI##i##_IRQHandler(void) {       \
        extiIrqHandler(EXTI##i##_IRQn);     \
    }                                       \

DEFINE_SHARED_EXTI_ISR(0)
DEFINE_SHARED_EXTI_ISR(1)
DEFINE_SHARED_EXTI_ISR(2)
DEFINE_SHARED_EXTI_ISR(3)
DEFINE_SHARED_EXTI_ISR(4)
DEFINE_SHARED_EXTI_ISR(9_5)
DEFINE_SHARED_EXTI_ISR(15_10)

int extiSetMaxLatency(struct ChainedIsr *isr, uint32_t maxLatencyNs)
{
    uint32_t latency;

    if (!isr)
        return -EINVAL;

    if (maxLatencyNs != isr->maxLatencyNs) {
        latency = isr->maxLatencyNs;
        isr->maxLatencyNs = maxLatencyNs;
        if (!mMaxLatency || latency == mMaxLatency || (maxLatencyNs && maxLatencyNs < mMaxLatency)) {
            extiCalcMaxLatency();
        }
    }

    return 0;
}

int extiChainIsr(IRQn_Type n, struct ChainedIsr *isr)
{
    struct ExtiInterrupt *exti = extiForIrq(n);
    if (!exti)
        return -EINVAL;
    else if (!list_is_empty(&isr->node))
        return -EINVAL;

    chainIsr(&exti->base, isr);
    if (!mMaxLatency || (isr->maxLatencyNs && isr->maxLatencyNs < mMaxLatency))
        extiUpdateMaxLatency(isr->maxLatencyNs);

    return 0;
}

int extiUnchainIsr(IRQn_Type n, struct ChainedIsr *isr)
{
    struct ExtiInterrupt *exti = extiForIrq(n);
    if (!exti)
        return -EINVAL;
    else if (list_is_empty(&isr->node))
        return -EINVAL;

    unchainIsr(&exti->base, isr);
    if (isr->maxLatencyNs && isr->maxLatencyNs == mMaxLatency)
        extiCalcMaxLatency();
    return 0;
}

int extiUnchainAll(uint32_t tid)
{
    int i, count = 0;
    struct ExtiInterrupt *exti = mInterrupts;

    for (i = 0; i < ARRAY_SIZE(mInterrupts); ++i, ++exti)
        count += unchainIsrAll(&exti->base, tid);
    extiCalcMaxLatency();

    return count;
}
