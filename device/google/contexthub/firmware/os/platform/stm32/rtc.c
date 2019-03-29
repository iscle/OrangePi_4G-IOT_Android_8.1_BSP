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

#include <cpu/barrier.h>
#include <cpu/cpuMath.h>
#include <plat/rtc.h>
#include <plat/pwr.h>
#include <timer.h>
#include <platform.h>
#include <plat/exti.h>
#include <plat/cmsis.h>
#include <variant/variant.h>

#ifndef NS_PER_S
#define NS_PER_S                    UINT64_C(1000000000)
#endif


struct StmRtc
{
    volatile uint32_t TR;       /* 0x00 */
    volatile uint32_t DR;       /* 0x04 */
    volatile uint32_t CR;       /* 0x08 */
    volatile uint32_t ISR;      /* 0x0C */
    volatile uint32_t PRER;     /* 0x10 */
    volatile uint32_t WUTR;     /* 0x14 */
    volatile uint32_t CALIBR;   /* 0x18 */
    volatile uint32_t ALRMAR;   /* 0x1C */
    volatile uint32_t ALRMBR;   /* 0x20 */
    volatile uint32_t WPR;      /* 0x24 */
    volatile uint32_t SSR;      /* 0x28 */
    volatile uint32_t SHIFTR;   /* 0x2C */
    volatile uint32_t TSTR;     /* 0x30 */
    volatile uint32_t TSDR;     /* 0x34 */
    volatile uint32_t TSSSR;    /* 0x38 */
    volatile uint32_t CALR;     /* 0x3C */
    volatile uint32_t TAFCR;    /* 0x40 */
    volatile uint32_t ALRMASSR; /* 0x44 */
    volatile uint32_t ALRMBSSR; /* 0x48 */
    uint8_t unused0[4];         /* 0x4C */
    volatile uint32_t BKPR[20]; /* 0x50 - 0x9C */
};

#define RTC ((struct StmRtc*)RTC_BASE)

/* RTC bit defintions */
#define RTC_CR_WUCKSEL_MASK         0x00000007UL
#define RTC_CR_WUCKSEL_16DIV        0x00000000UL
#define RTC_CR_WUCKSEL_8DIV         0x00000001UL
#define RTC_CR_WUCKSEL_4DIV         0x00000002UL
#define RTC_CR_WUCKSEL_2DIV         0x00000003UL
#define RTC_CR_WUCKSEL_CK_SPRE      0x00000004UL
#define RTC_CR_WUCKSEL_CK_SPRE_2    0x00000006UL
#define RTC_CR_BYPSHAD              0x00000020UL
#define RTC_CR_FMT                  0x00000040UL
#define RTC_CR_ALRAE                0x00000100UL
#define RTC_CR_WUTE                 0x00000400UL
#define RTC_CR_ALRAIE               0x00001000UL
#define RTC_CR_WUTIE                0x00004000UL

#define RTC_ISR_ALRAWF              0x00000001UL
#define RTC_ISR_WUTWF               0x00000004UL
#define RTC_ISR_RSF                 0x00000020UL
#define RTC_ISR_INITF               0x00000040UL
#define RTC_ISR_INIT                0x00000080UL
#define RTC_ISR_WUTF                0x00000400UL

/* RTC internal values */
#define RTC_FREQ_HZ                 32768UL
#define RTC_WKUP_DOWNCOUNT_MAX      0x10000UL

/* TODO: Reset to crystal PPM once known */
#define RTC_PPM                     50UL

/* Default prescalars of P[async] = 127 and P[sync] = 255 are appropriate
 * produce a 1 Hz clock when using a 32.768kHZ clock source */
#ifndef RTC_PREDIV_A
#define RTC_PREDIV_A                31UL
#endif
#ifndef RTC_PREDIV_S
#define RTC_PREDIV_S                1023UL
#endif
#ifndef RTC_CALM
#define RTC_CALM                    0
#endif
#ifndef RTC_CALP
#define RTC_CALP                    0
#endif

/* Jitter = max wakeup timer resolution (61.035 us)
 * + 2 RTC cycles for synchronization (61.035 us) */
#define RTC_DIV2_PERIOD_NS          UINT64_C(61035)
#define RTC_DIV4_PERIOD_NS          UINT64_C(122070)
#define RTC_DIV8_PERIOD_NS          UINT64_C(244141)
#define RTC_DIV16_PERIOD_NS         UINT64_C(488281)

#define RTC_VALID_DELAY_FOR_PERIOD(delay, period) \
    (delay < (period * (RTC_WKUP_DOWNCOUNT_MAX + 1)))

static void rtcSetDefaultDateTimeAndPrescalar(void)
{
    /* Enable writability of RTC registers */
    RTC->WPR = 0xCA;
    RTC->WPR = 0x53;

    /* Enter RTC init mode */
    RTC->ISR |= RTC_ISR_INIT;

    mem_reorder_barrier();
    /* Wait for initialization mode to be entered. */
    while ((RTC->ISR & RTC_ISR_INITF) == 0);

    /* Set prescalar rtc register.  Two writes required. */
    RTC->PRER = RTC_PREDIV_S;
    RTC->PRER |= (RTC_PREDIV_A << 16);
    RTC->CALR = (RTC_CALP << 15) | (RTC_CALM & 0x1FF);

    /* 24 hour format */
    RTC->CR &= ~RTC_CR_FMT;

    /* disable shadow registers */
    RTC->CR |= RTC_CR_BYPSHAD;

    /* Set time and date registers to defaults */
    /* Midnight */
    RTC->TR = 0x0;
    RTC->SSR = 0x0;
    /* Sat Jan 1st, 2000 BCD */
    RTC->DR = 0b1100000100000001;

    /* Exit init mode for RTC */
    RTC->ISR &= ~RTC_ISR_INIT;

    /* Re-enable register write protection.  RTC counting doesn't start for
     * 4 RTC cycles after set - must poll RSF before read DR or TR */
    RTC->WPR = 0xFF;

    extiEnableIntLine(EXTI_LINE_RTC_WKUP, EXTI_TRIGGER_RISING);
    NVIC_EnableIRQ(RTC_WKUP_IRQn);
}

void rtcInit(void)
{
    pwrEnableAndClockRtc(RTC_CLK);
    rtcSetDefaultDateTimeAndPrescalar();
}

/* Set calendar alarm to go off after delay has expired. uint64_t delay must
 * be in valid uint64_t format */
int rtcSetWakeupTimer(uint64_t delay)
{
    uint64_t intState;
    uint64_t periodNsRecip;
    uint32_t wakeupClock;
    uint32_t periodNs;

    /* Minimum wakeup interrupt period is 122 us, max is 36.4 hours */
    if (delay < (RTC_DIV2_PERIOD_NS * 2)) {
        return RTC_ERR_TOO_SMALL;
    } else if (delay > (NS_PER_S * 2 * RTC_WKUP_DOWNCOUNT_MAX)) {
        delay = NS_PER_S * 2 * RTC_WKUP_DOWNCOUNT_MAX;
    }

    /* Get appropriate clock period for delay size.  Wakeup clock = RTC/x. */
    if (RTC_VALID_DELAY_FOR_PERIOD(delay, RTC_DIV2_PERIOD_NS)) {

        wakeupClock = RTC_CR_WUCKSEL_2DIV;
        periodNs = RTC_DIV2_PERIOD_NS;
        periodNsRecip = U64_RECIPROCAL_CALCULATE(RTC_DIV2_PERIOD_NS);
    }
    else if (RTC_VALID_DELAY_FOR_PERIOD(delay, RTC_DIV4_PERIOD_NS)) {

        wakeupClock = RTC_CR_WUCKSEL_4DIV;
        periodNs = RTC_DIV4_PERIOD_NS;
        periodNsRecip = U64_RECIPROCAL_CALCULATE(RTC_DIV4_PERIOD_NS);
    }
    else if (RTC_VALID_DELAY_FOR_PERIOD(delay, RTC_DIV8_PERIOD_NS)) {

        wakeupClock = RTC_CR_WUCKSEL_8DIV;
        periodNs = RTC_DIV8_PERIOD_NS;
        periodNsRecip = U64_RECIPROCAL_CALCULATE(RTC_DIV8_PERIOD_NS);
    }
    else if (RTC_VALID_DELAY_FOR_PERIOD(delay, RTC_DIV16_PERIOD_NS)) {

        wakeupClock = RTC_CR_WUCKSEL_16DIV;
        periodNs = RTC_DIV16_PERIOD_NS;
        periodNsRecip = U64_RECIPROCAL_CALCULATE(RTC_DIV16_PERIOD_NS);
    }
    else {

        if (RTC_VALID_DELAY_FOR_PERIOD(delay, NS_PER_S))
            wakeupClock = RTC_CR_WUCKSEL_CK_SPRE;
        else
            wakeupClock = RTC_CR_WUCKSEL_CK_SPRE_2;
        periodNs = NS_PER_S;
        periodNsRecip = U64_RECIPROCAL_CALCULATE(NS_PER_S);
    }

    intState = cpuIntsOff();

    /* Enable RTC register write */
    RTC->WPR = 0xCA;
    RTC->WPR = 0x53;

    /* Disable wakeup timer */
    RTC->CR &= ~RTC_CR_WUTE;

    /* Wait for access enabled for wakeup timer registers */
    while ((RTC->ISR & RTC_ISR_WUTWF) == 0);

    /* Clear wakeup clock source */
    RTC->CR &= ~RTC_CR_WUCKSEL_MASK;

    RTC->CR |= wakeupClock;
    /* Downcounter value for wakeup clock.  Wakeup flag is set every
     * RTC->WUTR[15:0] + 1 cycles of the WUT clock. */
    RTC->WUTR = cpuMathRecipAssistedUdiv64by32(delay, periodNs, periodNsRecip) - 1;

    /* Enable wakeup interrupts */
    RTC->CR |= RTC_CR_WUTIE;
    extiClearPendingLine(EXTI_LINE_RTC_WKUP);

    /* Enable wakeup timer */
    RTC->CR |= RTC_CR_WUTE;

    /* Clear overflow flag */
    RTC->ISR &= ~RTC_ISR_WUTF;

    /* Write-protect RTC registers */
    RTC->WPR = 0xFF;

    cpuIntsRestore(intState);

    return 0;
}

uint64_t rtcGetTime(void)
{
    int32_t time_s;
    uint32_t dr, tr, ssr;
    // cumulative adjustments from 32 day months (year 2000)
    //   31, 29, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31
    //    1,  3,  1,  2,  1,  2,  1,  1,  2,  1,  2,  1
    //  0   1,  4,  5,  7,  8, 10, 11, 12, 14, 15, 17
    static const uint8_t adjust[] = { 0, 1, 4, 5, 7, 8, 10, 11, 12, 14, 15, 17 };
    uint8_t month;

    // need to loop incase an interrupt occurs in the middle or ssr
    // decrements (which can propagate changes to tr and dr)
    do {
        ssr = RTC->SSR;
        tr = RTC->TR;
        dr = RTC->DR;
    } while (ssr != RTC->SSR);

    month = (((dr >> 12) & 0x1) * 10) + ((dr >> 8) & 0xf) - 1;
    time_s = (((((dr >> 4) & 0x3) * 10) + (dr & 0xF) - 1) + (month << 5) - adjust[month]) * 86400ULL;
    time_s += ((((tr >> 22) & 0x1) * 43200ULL) +
             (((tr >> 20) & 0x3) * 36000ULL) +
             (((tr >> 16) & 0xF) * 3600ULL) +
             (((tr >> 12) & 0x7) * 600ULL) +
             (((tr >> 8) & 0xF) * 60ULL) +
             (((tr >> 4) & 0x7) * 10ULL) +
             (((tr) & 0xF)));

    return (time_s * NS_PER_S) + U64_DIV_BY_CONST_U16(((RTC_PREDIV_S - ssr) * NS_PER_S), (RTC_PREDIV_S + 1));
}

void EXTI22_RTC_WKUP_IRQHandler(void);
void EXTI22_RTC_WKUP_IRQHandler(void)
{
    extiClearPendingLine(EXTI_LINE_RTC_WKUP);
    timIntHandler();
}

uint32_t* rtcGetBackupStorage(void)
{
    return (uint32_t*)RTC->BKPR;
}
