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

#include <cpu/cpuMath.h>
#include <plat/gpio.h>
#include <plat/usart.h>
#include <plat/cmsis.h>
#include <plat/pwr.h>
#include <plat/rtc.h>
#include <plat/plat.h>
#include <plat/exti.h>
#include <plat/wdt.h>
#include <plat/dma.h>
#include <stdbool.h>
#include <stdio.h>
#include <string.h>
#include <pthread.h>
#include <unistd.h>
#include <platform.h>
#include <seos.h>
#include <heap.h>
#include <timer.h>
#include <usart.h>
#include <gpio.h>
#include <mpu.h>
#include <cpu.h>
#include <hostIntf.h>
#include <atomic.h>
#include <hostIntf.h>
#include <nanohubPacket.h>
#include <sensType.h>
#include <variant/variant.h>


struct StmDbgmcu {
    volatile uint32_t IDCODE;
    volatile uint32_t CR;
    volatile uint32_t APB1FZ;
    volatile uint32_t APB2FZ;
};

struct StmTim {

    volatile uint16_t CR1;
    uint8_t unused0[2];
    volatile uint16_t CR2;
    uint8_t unused1[2];
    volatile uint16_t SMCR;
    uint8_t unused2[2];
    volatile uint16_t DIER;
    uint8_t unused3[2];
    volatile uint16_t SR;
    uint8_t unused4[2];
    volatile uint16_t EGR;
    uint8_t unused5[2];
    volatile uint16_t CCMR1;
    uint8_t unused6[2];
    volatile uint16_t CCMR2;
    uint8_t unused7[2];
    volatile uint16_t CCER;
    uint8_t unused8[2];
    volatile uint32_t CNT;
    volatile uint16_t PSC;
    uint8_t unused9[2];
    volatile uint32_t ARR;
    volatile uint16_t RCR;
    uint8_t unused10[2];
    volatile uint32_t CCR1;
    volatile uint32_t CCR2;
    volatile uint32_t CCR3;
    volatile uint32_t CCR4;
    volatile uint16_t BDTR;
    uint8_t unused11[2];
    volatile uint16_t DCR;
    uint8_t unused12[2];
    volatile uint16_t DMAR;
    uint8_t unused13[2];
    volatile uint16_t OR;
    uint8_t unused14[2];
};

#define TIM2        ((struct StmTim*)TIM2_BASE)
#define DBGMCU      ((struct StmDbgmcu*)DBGMCU_BASE)

/* RTC bit defintions */
#define TIM_EGR_UG  0x0001

/* DBGMCU bit definition */
#define DBG_SLEEP   0x00000001
#define DBG_STOP    0x00000002
#define DBG_STANDBY 0x00000004


#ifdef DEBUG_UART_UNITNO
static struct usart mDbgUart;
#endif

#ifdef DEBUG_LOG_EVT
#ifndef EARLY_LOG_BUF_SIZE
#define EARLY_LOG_BUF_SIZE      2048
#endif
#define HOSTINTF_HEADER_SIZE    4
uint8_t *mEarlyLogBuffer;
uint16_t mEarlyLogBufferCnt;
uint16_t mEarlyLogBufferOffset;
bool mLateBoot;
#endif

static uint64_t mTimeAccumulated = 0;
static uint32_t mMaxJitterPpm = 0, mMaxDriftPpm = 0, mMaxErrTotalPpm = 0;
static uint32_t mSleepDevsToKeepAlive = 0;
static uint64_t mWakeupTime = 0;
static uint32_t mDevsMaxWakeTime[PLAT_MAX_SLEEP_DEVS] = {0,};

void platUninitialize(void)
{
#ifdef DEBUG_UART_UNITNO
    usartClose(&mDbgUart);
#endif
}

void *platLogAllocUserData()
{
#if defined(DEBUG_LOG_EVT)
    struct HostIntfDataBuffer *userData = NULL;

    if (mLateBoot) {
        userData = heapAlloc(sizeof(struct HostIntfDataBuffer));
    } else if (mEarlyLogBufferOffset < EARLY_LOG_BUF_SIZE - HOSTINTF_HEADER_SIZE) {
        userData = (struct HostIntfDataBuffer *)(mEarlyLogBuffer + mEarlyLogBufferOffset);
        mEarlyLogBufferOffset += HOSTINTF_HEADER_SIZE;
    }
    if (userData) {
        userData->sensType = SENS_TYPE_INVALID;
        userData->length = 0;
        userData->dataType = HOSTINTF_DATA_TYPE_LOG;
        userData->interrupt = NANOHUB_INT_NONWAKEUP;
    }
    return userData;
#else
    return NULL;
#endif
}

#if defined(DEBUG_LOG_EVT)
static void platEarlyLogFree(void *buf)
{
    struct HostIntfDataBuffer *userData = (struct HostIntfDataBuffer *)buf;
    mEarlyLogBufferCnt += userData->length + HOSTINTF_HEADER_SIZE;
    if (mEarlyLogBufferCnt >= mEarlyLogBufferOffset) {
        heapFree(mEarlyLogBuffer);
    }
}
#endif

void platEarlyLogFlush(void)
{
#if defined(DEBUG_LOG_EVT)
    uint16_t i = 0;
    struct HostIntfDataBuffer *userData;

    mLateBoot = true;

    while (i < mEarlyLogBufferOffset) {
        userData = (struct HostIntfDataBuffer *)(mEarlyLogBuffer + i);
        osEnqueueEvt(EVENT_TYPE_BIT_DISCARDABLE | EVT_DEBUG_LOG, userData, platEarlyLogFree);
        i += HOSTINTF_HEADER_SIZE + userData->length;
    }
#endif
}

void platLogFlush(void *userData)
{
#ifdef DEBUG_UART_UNITNO
    usartFlush(&mDbgUart);
#endif
#if defined(DEBUG_LOG_EVT)
    if (userData && mLateBoot)
        osEnqueueEvtOrFree(EVENT_TYPE_BIT_DISCARDABLE | EVT_DEBUG_LOG, userData, heapFree);
#endif
}

bool platLogPutcharF(void *userData, char ch)
{
#if defined(DEBUG) && defined(DEBUG_UART_PIN)
    if (ch == '\n')
        gpioBitbangedUartOut('\r');
    gpioBitbangedUartOut(ch);
#endif
#if defined(DEBUG_UART_UNITNO)
    if (ch == '\n')
        usartPutchar(&mDbgUart, '\r');
    usartPutchar(&mDbgUart, ch);
#endif
#if defined(DEBUG_LOG_EVT)
    struct HostIntfDataBuffer *buffer;

    if (userData) {
        buffer = userData;
        size_t maxSize = sizeof(buffer->buffer);

        // if doing early logging, and early log buffer is full, ignore the rest of early output
        if (!mLateBoot && mEarlyLogBufferOffset >= EARLY_LOG_BUF_SIZE && buffer->length < maxSize)
            maxSize = buffer->length;

        if (buffer->length < maxSize) {
            buffer->buffer[buffer->length++] = ch;
            if (!mLateBoot)
                mEarlyLogBufferOffset++;
        } else {
            buffer->buffer[maxSize - 1] = '\n';
            return false;
        }
    }
#endif
    return true;
}

void platInitialize(void)
{
    const uint32_t debugStateInSleepMode = DBG_SLEEP | DBG_STOP | DBG_STANDBY;
    uint32_t i;

    pwrSystemInit();

    //prepare for sleep mode(s)
    SCB->SCR &=~ SCB_SCR_SLEEPONEXIT_Msk;

    //set ints up for a sane state
    //3 bits preemptPriority, 1 bit subPriority
    NVIC_SetPriorityGrouping(4);
    for (i = 0; i < NUM_INTERRUPTS; i++) {
        NVIC_SetPriority(i, NVIC_EncodePriority(4, 2, 1));
        NVIC_DisableIRQ(i);
        NVIC_ClearPendingIRQ(i);
    }

    /* disable pins */
    for (i = 0; i < 16; i++) {
#if defined(DEBUG) && defined(DEBUG_SWD)
        /* pins PA13 and PA14 are used for SWD */
        if ((i != 13) && (i != 14))
            gpioConfigAnalog(gpioRequest(GPIO_PA(i)));
#else
        gpioConfigAnalog(gpioRequest(GPIO_PA(i)));
#endif
        gpioConfigAnalog(gpioRequest(GPIO_PB(i)));
        gpioConfigAnalog(gpioRequest(GPIO_PC(i)));
        gpioConfigAnalog(gpioRequest(GPIO_PD(i)));
        gpioConfigAnalog(gpioRequest(GPIO_PE(i)));
        gpioConfigAnalog(gpioRequest(GPIO_PH(i)));
    }

#ifdef DEBUG_UART_UNITNO
    /* Open mDbgUart on PA2 and PA3 */
    usartOpen(&mDbgUart, DEBUG_UART_UNITNO, DEBUG_UART_GPIO_TX, DEBUG_UART_GPIO_RX,
               115200, USART_DATA_BITS_8,
               USART_STOP_BITS_1_0, USART_PARITY_NONE,
               USART_FLOW_CONTROL_NONE);
#endif

    /* set up debugging */
#if defined(DEBUG) && defined(DEBUG_SWD)
    DBGMCU->CR |= debugStateInSleepMode;
#else
    DBGMCU->CR &=~ debugStateInSleepMode;
#endif

    /* enable MPU */
    mpuStart();

    /* set up timer used for alarms */
    pwrUnitClock(PERIPH_BUS_APB1, PERIPH_APB1_TIM2, true);
    TIM2->CR1 = (TIM2->CR1 &~ 0x03E1) | 0x0010; //count down mode with no clock division, disabled
    TIM2->PSC = 15; // prescale by 16, so that at 16MHz CPU clock, we get 1MHz timer
    TIM2->DIER |= 1; // interrupt when updated (underflowed)
    TIM2->ARR = 0xffffffff;
    TIM2->EGR = TIM_EGR_UG; // force a reload of the prescaler
    NVIC_EnableIRQ(TIM2_IRQn);

    rtcInit();

    /* bring up systick */
    SysTick->CTRL = 0;
    SysTick->LOAD = 0x00FFFFFF;
    SysTick->VAL = 0;
    SysTick->CTRL = SysTick_CTRL_CLKSOURCE_Msk | SysTick_CTRL_TICKINT_Msk | SysTick_CTRL_ENABLE_Msk;

#ifdef DEBUG_LOG_EVT
    /* allocate buffer for early boot log message*/
    mEarlyLogBuffer = heapAlloc(EARLY_LOG_BUF_SIZE);
#endif

}

static uint64_t platsystickTicksToNs(uint32_t systickTicks)
{
    return (uint64_t)systickTicks * 125 / 2;
}

uint64_t platGetTicks(void)
{
    uint64_t ret;
    uint32_t val;

    do {
        mem_reorder_barrier(); //mTimeAccumulated may change since it was read in condition check

        ret = mTimeAccumulated;
        val = SysTick->VAL;

        mem_reorder_barrier(); //mTimeAccumulated may change since it was read above

    } while (mTimeAccumulated != ret || SysTick->VAL > val);

    return platsystickTicksToNs(0x01000000 - val) + ret;
}

/* Timer interrupt handler */
void TIM2_IRQHandler(void);
void TIM2_IRQHandler(void)
{
    struct StmTim *tim = (struct StmTim*)TIM2_BASE;

    /* int clear */
    tim->SR &=~ 1;

    /* timer off */
    tim->CR1 &=~ 1;

    /* call timer handler since it might need to reschedule an interrupt (eg: in case where initial delay was too far off & we were limited by timer length) */
    timIntHandler();
}

/* SysTick interrupt handler */
void SysTick_Handler(void);
void SysTick_Handler(void)
{
    mTimeAccumulated += platsystickTicksToNs(SysTick->LOAD + 1); //todo - incremenet by actual elapsed nanoseconds and not just "1"
}

bool platRequestDevInSleepMode(uint32_t sleepDevID, uint32_t maxWakeupTime)
{
    if (sleepDevID >= PLAT_MAX_SLEEP_DEVS || sleepDevID >= Stm32sleepDevNum)
        return false;

    mDevsMaxWakeTime[sleepDevID] = maxWakeupTime;
    while (!atomicCmpXchg32bits(&mSleepDevsToKeepAlive, mSleepDevsToKeepAlive, mSleepDevsToKeepAlive | (1UL << sleepDevID)));

    return true;
}

bool platAdjustDevInSleepMode(uint32_t sleepDevID, uint32_t maxWakeupTime)
{
    if (sleepDevID >= PLAT_MAX_SLEEP_DEVS || sleepDevID >= Stm32sleepDevNum)
        return false;

    mDevsMaxWakeTime[sleepDevID] = maxWakeupTime;

    return true;
}

bool platReleaseDevInSleepMode(uint32_t sleepDevID)
{
    if (sleepDevID >= PLAT_MAX_SLEEP_DEVS || sleepDevID >= Stm32sleepDevNum)
        return false;

    while (!atomicCmpXchg32bits(&mSleepDevsToKeepAlive, mSleepDevsToKeepAlive, mSleepDevsToKeepAlive &~ (1UL << sleepDevID)));

    return true;
}

static uint64_t platSetTimerAlarm(uint64_t delay) //delay at most that many nsec
{
    struct StmTim *tim = (struct StmTim*)TIM2_BASE;
    uint32_t delayInUsecs;

    //turn off timer to prevent interrupts now
    tim->CR1 &=~ 1;

    if (delay >= (1000ULL << 32)) //it is only a 32-bit counter - we cannot set delays bigger than that
        delayInUsecs = 0xffffffff;
    else
        delayInUsecs = cpuMathUint44Div1000ToUint32(delay);

    tim->CNT = delayInUsecs;
    tim->SR &=~ 1; //clear int
    tim->CR1 |= 1;

    return delayInUsecs;
}

bool platSleepClockRequest(uint64_t wakeupTime, uint32_t maxJitterPpm, uint32_t maxDriftPpm, uint32_t maxErrTotalPpm)
{
    uint64_t intState, curTime = timGetTime();

    if (wakeupTime && curTime >= wakeupTime)
        return false;

    intState = cpuIntsOff();

    mMaxJitterPpm = maxJitterPpm;
    mMaxDriftPpm = maxDriftPpm;
    mMaxErrTotalPpm = maxErrTotalPpm;
    mWakeupTime = wakeupTime;

    //TODO: set an actual alarm here so that if we keep running and do not sleep till this is due, we still fire an interrupt for it!
    if (wakeupTime)
        platSetTimerAlarm(wakeupTime - curTime);

    cpuIntsRestore(intState);

    return true;
}

static bool sleepClockRtcPrepare(uint64_t delay, uint32_t acceptableJitter, uint32_t acceptableDrift, uint32_t maxAcceptableError, void *userData, uint64_t *savedData)
{
    pwrSetSleepType((uint32_t)userData);
    *savedData = rtcGetTime();

    if (delay && rtcSetWakeupTimer(delay) < 0)
        return false;

    //sleep with systick off (for timing) and interrupts off (for power due to HWR errata)
    SysTick->CTRL &= ~(SysTick_CTRL_TICKINT_Msk | SysTick_CTRL_ENABLE_Msk);
    return true;
}

static void sleepClockRtcWake(void *userData, uint64_t *savedData)
{
    //re-enable Systic and its interrupt
    SysTick->CTRL |= SysTick_CTRL_TICKINT_Msk | SysTick_CTRL_ENABLE_Msk;

    mTimeAccumulated += rtcGetTime() - *savedData;
}


static bool sleepClockTmrPrepare(uint64_t delay, uint32_t acceptableJitter, uint32_t acceptableDrift, uint32_t maxAcceptableError, void *userData, uint64_t *savedData)
{
    pwrSetSleepType(stm32f411SleepModeSleep);
    platRequestDevInSleepMode(Stm32sleepDevTim2, 0);

    *savedData = platSetTimerAlarm(delay ?: ~0ull);

    //sleep with systick off (for timing) and interrupts off (for power due to HWR errata)
    SysTick->CTRL &= ~(SysTick_CTRL_TICKINT_Msk | SysTick_CTRL_ENABLE_Msk);
    return true;
}

static void sleepClockTmrWake(void *userData, uint64_t *savedData)
{
    struct StmTim *tim = (struct StmTim*)TIM2_BASE;
    uint32_t cnt;
    uint16_t sr;
    uint64_t leftTicks;

    //re-enable Systic and its interrupt
    SysTick->CTRL |= SysTick_CTRL_TICKINT_Msk | SysTick_CTRL_ENABLE_Msk;

    //stop the timer counting;
    tim->CR1 &=~ 1;

    //If we are within one time tick of overflow, it is possible for SR to
    //not indicate a pending overflow, but CNT contain 0xFFFFFFFF or vice versa,
    //depending on the read order of SR and CNT
    //read both values until they are stable
    do {
        sr = tim->SR;
        cnt = tim->CNT;
    } while (sr != tim->SR || cnt != tim->CNT);

    leftTicks = cnt; //if we wake NOT from timer, only count the ticks that actually ticked as "time passed"
    if (sr & 1) //if there was an overflow, account for it
        leftTicks -= 0x100000000ull;

    mTimeAccumulated += (*savedData - leftTicks) * 1000; //this clock runs at 1MHz

    platReleaseDevInSleepMode(Stm32sleepDevTim2);
}


static bool sleepClockJustWfiPrepare(uint64_t delay, uint32_t acceptableJitter, uint32_t acceptableDrift, uint32_t maxAcceptableError, void *userData, uint64_t *savedData)
{
    pwrSetSleepType(stm32f411SleepModeSleep);

    return true;
}

struct PlatSleepAndClockInfo {
    uint64_t resolution;
    uint64_t resolutionReciprocal; // speed up runtime by using 48 more code bytes? yes please!
    uint32_t maxCounter;
    uint32_t jitterPpm;
    uint32_t driftPpm;
    uint32_t maxWakeupTime;
    uint32_t devsAvail; //what is available in sleep mode?
    bool (*prepare)(uint64_t delay, uint32_t acceptableJitter, uint32_t acceptableDrift, uint32_t maxAcceptableError, void *userData, uint64_t *savedData);
    void (*wake)(void *userData, uint64_t *savedData);
    void *userData;
} static const platSleepClocks[] = {
#ifndef STM32F4xx_DISABLE_LPLV_SLEEP
    { /* RTC + LPLV STOP MODE */
        .resolution = 1000000000ull/32768,
        .resolutionReciprocal = U64_RECIPROCAL_CALCULATE(1000000000ull/32768),
        .maxCounter = 0xffffffff,
        .jitterPpm = 0,
        .driftPpm = 50,
        .maxWakeupTime = 407000ull,
        .devsAvail = (1 << Stm32sleepDevExti),
        .prepare = sleepClockRtcPrepare,
        .wake = sleepClockRtcWake,
        .userData = (void*)stm32f411SleepModeStopLPLV,
    },
#endif
#ifndef STM32F4xx_DISABLE_LPFD_SLEEP
    { /* RTC + LPFD STOP MODE */
        .resolution = 1000000000ull/32768,
        .resolutionReciprocal = U64_RECIPROCAL_CALCULATE(1000000000ull/32768),
        .maxCounter = 0xffffffff,
        .jitterPpm = 0,
        .driftPpm = 50,
        .maxWakeupTime = 130000ull,
        .devsAvail = (1 << Stm32sleepDevExti),
        .prepare = sleepClockRtcPrepare,
        .wake = sleepClockRtcWake,
        .userData = (void*)stm32f411SleepModeStopLPFD,
    },
#endif
#ifndef STM32F4xx_DISABLE_MRFPD_SLEEP
    { /* RTC + MRFPD STOP MODE */
        .resolution = 1000000000ull/32768,
        .resolutionReciprocal = U64_RECIPROCAL_CALCULATE(1000000000ull/32768),
        .maxCounter = 0xffffffff,
        .jitterPpm = 0,
        .driftPpm = 50,
        .maxWakeupTime = 111000ull,
        .devsAvail = (1 << Stm32sleepDevExti),
        .prepare = sleepClockRtcPrepare,
        .wake = sleepClockRtcWake,
        .userData = (void*)stm32f411SleepModeStopMRFPD,
    },
#endif
#ifndef STM32F4xx_DISABLE_MR_SLEEP
    { /* RTC + MR STOP MODE */
        .resolution = 1000000000ull/32768,
        .resolutionReciprocal = U64_RECIPROCAL_CALCULATE(1000000000ull/32768),
        .maxCounter = 0xffffffff,
        .jitterPpm = 0,
        .driftPpm = 50,
        .maxWakeupTime = 14500ull,
        .devsAvail = (1 << Stm32sleepDevExti),
        .prepare = sleepClockRtcPrepare,
        .wake = sleepClockRtcWake,
        .userData = (void*)stm32f411SleepModeStopMR,
    },
#endif
#ifndef STM32F4xx_DISABLE_TIM2_SLEEP
    { /* TIM2 + SLEEP MODE */
        .resolution = 1000000000ull/1000000,
        .resolutionReciprocal = U64_RECIPROCAL_CALCULATE(1000000000ull/1000000),
        .maxCounter = 0xffffffff,
        .jitterPpm = 0,
        .driftPpm = 30,
        .maxWakeupTime = 12ull,
        .devsAvail = (1 << Stm32sleepDevTim2) | (1 << Stm32sleepDevTim4) | (1 << Stm32sleepDevTim5) | (1 << Stm32sleepDevTim9) | (1 << Stm32sleepWakeup) | (1 << Stm32sleepDevSpi2) | (1 << Stm32sleepDevSpi3) | (1 << Stm32sleepDevI2c1) | (1 << Stm32sleepDevI2c2) | (1 << Stm32sleepDevI2c3) | (1 << Stm32sleepDevExti),
        .prepare = sleepClockTmrPrepare,
        .wake = sleepClockTmrWake,
    },
#endif
    { /* just WFI */
        .resolution = 16000000000ull/1000000,
        .resolutionReciprocal = U64_RECIPROCAL_CALCULATE(16000000000ull/1000000),
        .maxCounter = 0xffffffff,
        .jitterPpm = 0,
        .driftPpm = 0,
        .maxWakeupTime = 0,
        .devsAvail = (1 << Stm32sleepDevTim2) | (1 << Stm32sleepDevTim4) | (1 << Stm32sleepDevTim5) | (1 << Stm32sleepDevTim9) | (1 << Stm32sleepWakeup) | (1 << Stm32sleepDevSpi2) | (1 << Stm32sleepDevSpi3) | (1 << Stm32sleepDevI2c1) | (1 << Stm32sleepDevI2c2) | (1 << Stm32sleepDevI2c3) | (1 << Stm32sleepDevExti),
        .prepare = sleepClockJustWfiPrepare,
    },

    /* terminator */
    {0},
};

void platSleep(void)
{
    uint64_t predecrement = 0, curTime = timGetTime(), length = mWakeupTime - curTime, intState;
    const struct PlatSleepAndClockInfo *sleepClock, *leastBadOption = NULL;
    uint64_t savedData;
    uint32_t i;

    //shortcut the sleep if it is time to wake up already
    if (mWakeupTime && mWakeupTime < curTime)
        return;

    for (sleepClock = platSleepClocks; sleepClock->maxCounter; sleepClock++) {

        bool potentialLeastBadOption = false;

        //if we have timers, consider them
        if (mWakeupTime) {

            //calculate how much we WOULD predecerement by
            predecrement = sleepClock->resolution + sleepClock->maxWakeupTime;

            //skip options with too much jitter (after accounting for error
            if (sleepClock->jitterPpm > mMaxJitterPpm)
                continue;

            //skip options that will take too long to wake up to be of use
            if (predecrement > length)
                continue;

            //skip options with too much drift
            if (sleepClock->driftPpm > mMaxDriftPpm)
                continue;

            //skip options that do not let us sleep enough, but save them for later if we simply must pick something
            if (cpuMathRecipAssistedUdiv64by64(length, sleepClock->resolution, sleepClock->resolutionReciprocal) > sleepClock->maxCounter && !leastBadOption)
                potentialLeastBadOption = true;
        }

        //skip all options that do not keep enough deviceas awake
        if ((sleepClock->devsAvail & mSleepDevsToKeepAlive) != mSleepDevsToKeepAlive)
            continue;

        //skip all options that wake up too slowly
        for (i = 0; i < Stm32sleepDevNum; i++) {
            if (!(mSleepDevsToKeepAlive & (1 << i)))
                continue;
            if (mDevsMaxWakeTime[i] < sleepClock->maxWakeupTime)
                break;
        }
        if (i != Stm32sleepDevNum)
            continue;

        //if it will not let us sleep long enough save it as a possibility and go on
        if (potentialLeastBadOption && !leastBadOption)
            leastBadOption = sleepClock;
        else //if it fits us perfectly, pick it
            break;
    }
    if (!sleepClock->maxCounter)
        sleepClock = leastBadOption;

    if (!sleepClock) {
        //should never happen - this will spin the CPU and be bad, but it WILL work in all cases
        return;
    }

    //turn ints off in prep for sleep
    wdtDisableClk();
    intState = cpuIntsOff();

    //options? config it
    if (sleepClock->prepare &&
        sleepClock->prepare(mWakeupTime ? length - sleepClock->maxWakeupTime : 0,
                            mMaxJitterPpm, mMaxDriftPpm, mMaxErrTotalPpm,
                            sleepClock->userData, &savedData)) {

        asm volatile ("wfi\n"
            "nop" :::"memory");

        //wakeup
        if (sleepClock->wake)
            sleepClock->wake(sleepClock->userData, &savedData);
    }
    //re-enable interrupts and let the handlers run
    cpuIntsRestore(intState);
    wdtEnableClk();
}

void* platGetPersistentRamStore(uint32_t *bytes)
{
    *bytes = sizeof(uint32_t[RTC_NUM_BACKUP_REGS]);
    return rtcGetBackupStorage();
}

uint32_t platFreeResources(uint32_t tid)
{
    uint32_t dmaCount = dmaStopAll(tid);
    uint32_t irqCount = extiUnchainAll(tid);

    return (dmaCount << 8) | irqCount;
}

void platPeriodic()
{
    wdtPing();
}
