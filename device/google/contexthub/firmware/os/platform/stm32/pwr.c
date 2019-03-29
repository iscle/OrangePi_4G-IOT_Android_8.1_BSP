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
#include <plat/cmsis.h>
#include <plat/pwr.h>
#include <plat/rtc.h>
#include <reset.h>
#include <stddef.h>

struct StmRcc {
    volatile uint32_t CR;
    volatile uint32_t PLLCFGR;
    volatile uint32_t CFGR;
    volatile uint32_t CIR;
    volatile uint32_t AHB1RSTR;
    volatile uint32_t AHB2RSTR;
    volatile uint32_t AHB3RSTR;
    uint8_t unused0[4];
    volatile uint32_t APB1RSTR;
    volatile uint32_t APB2RSTR;
    uint8_t unused1[8];
    volatile uint32_t AHB1ENR;
    volatile uint32_t AHB2ENR;
    volatile uint32_t AHB3ENR;
    uint8_t unused2[4];
    volatile uint32_t APB1ENR;
    volatile uint32_t APB2ENR;
    uint8_t unused3[8];
    volatile uint32_t AHB1LPENR;
    volatile uint32_t AHB2LPENR;
    volatile uint32_t AHB3LPENR;
    uint8_t unused4[4];
    volatile uint32_t APB1LPENR;
    volatile uint32_t APB2LPENR;
    uint8_t unused5[8];
    volatile uint32_t BDCR;
    volatile uint32_t CSR;
    uint8_t unused6[8];
    volatile uint32_t SSCGR;
    volatile uint32_t PLLI2SCFGR;
};

struct StmPwr {
    volatile uint32_t CR;
    volatile uint32_t CSR;
};

#define RCC ((struct StmRcc*)RCC_BASE)
#define PWR ((struct StmPwr*)PWR_BASE)

/* RCC bit definitions */
#define RCC_BDCR_LSEON      0x00000001UL
#define RCC_BDCR_LSERDY     0x00000002UL
#define RCC_BDCR_LSEBYP     0x00000004UL
#define RCC_BDCR_LSEMOD     0x00000008UL
#define RCC_BDCR_RTCSEL_LSE 0x00000100UL
#define RCC_BDCR_RTCSEL_LSI 0x00000200UL
#define RCC_BDCR_RTCEN      0x00008000UL
#define RCC_BDCR_BDRST      0x00010000UL

#define RCC_CSR_LSION       0x00000001UL
#define RCC_CSR_LSIRDY      0x00000002UL
#define RCC_CSR_RMVF        0x01000000UL
#define RCC_CSR_BORRSTF     0x02000000UL
#define RCC_CSR_PINRSTF     0x04000000UL
#define RCC_CSR_PORRSTF     0x08000000UL
#define RCC_CSR_SFTRSTF     0x10000000UL
#define RCC_CSR_IWDGRSTF    0x20000000UL
#define RCC_CSR_WWDGRSTF    0x40000000UL
#define RCC_CSR_LPWRRSTF    0x80000000UL

/* PWR bit definitions */
#define PWR_CR_MRVLDS       0x00000800UL
#define PWR_CR_LPLVDS       0x00000400UL
#define PWR_CR_FPDS         0x00000200UL
#define PWR_CR_DBP          0x00000100UL
#define PWR_CR_PDDS         0x00000002UL
#define PWR_CR_LPDS         0x00000001UL


static uint32_t mResetReason;
static uint32_t mSysClk = 16000000UL;

#define RCC_REG(_bus, _type) ({                                 \
        static const uint32_t clockRegOfsts[] = {               \
            offsetof(struct StmRcc, AHB1##_type),               \
            offsetof(struct StmRcc, AHB2##_type),               \
            offsetof(struct StmRcc, AHB3##_type),               \
            offsetof(struct StmRcc, APB1##_type),               \
            offsetof(struct StmRcc, APB2##_type)                \
        }; /* indexed by PERIPH_BUS_* */                        \
        (volatile uint32_t *)(RCC_BASE + clockRegOfsts[_bus]);  \
    })                                                          \

void pwrUnitClock(uint32_t bus, uint32_t unit, bool on)
{
    volatile uint32_t *reg = RCC_REG(bus, ENR);

    if (on)
        *reg |= unit;
    else
        *reg &=~ unit;
}

void pwrUnitReset(uint32_t bus, uint32_t unit, bool on)
{
    volatile uint32_t *reg = RCC_REG(bus, RSTR);

    if (on)
        *reg |= unit;
    else
        *reg &=~ unit;
}

uint32_t pwrGetBusSpeed(uint32_t bus)
{
    uint32_t cfg = RCC->CFGR;
    uint32_t ahbDiv, apb1Div, apb2Div;
    uint32_t ahbSpeed, apb1Speed, apb2Speed;
    static const uint8_t ahbSpeedShifts[] = {1, 2, 3, 4, 6, 7, 8, 9};

    ahbDiv = (cfg >> 4) & 0x0F;
    apb1Div = (cfg >> 10) & 0x07;
    apb2Div = (cfg >> 13) & 0x07;

    ahbSpeed = (ahbDiv & 0x08) ? (mSysClk >> ahbSpeedShifts[ahbDiv & 0x07]) : mSysClk;
    apb1Speed = (apb1Div & 0x04) ? (ahbSpeed >> ((apb1Div & 0x03) + 1)) : ahbSpeed;
    apb2Speed = (apb2Div & 0x04) ? (ahbSpeed >> ((apb2Div & 0x03) + 1)) : ahbSpeed;

    if (bus == PERIPH_BUS_AHB1 || bus == PERIPH_BUS_AHB2 || bus == PERIPH_BUS_AHB3)
        return ahbSpeed;

    if (bus == PERIPH_BUS_APB1)
        return apb1Speed;

    if (bus == PERIPH_BUS_APB2)
        return apb2Speed;

    /* WTF...? */
    return 0;
}

static uint32_t pwrParseCsr(uint32_t csr)
{
    uint32_t reason = 0;

    if (csr & RCC_CSR_LPWRRSTF)
        reason |= RESET_POWER_MANAGEMENT;
    if (csr & RCC_CSR_WWDGRSTF)
        reason |= RESET_WINDOW_WATCHDOG;
    if (csr & RCC_CSR_IWDGRSTF)
        reason |= RESET_INDEPENDENT_WATCHDOG;
    if (csr & RCC_CSR_SFTRSTF)
        reason |= RESET_SOFTWARE;
    if (csr & RCC_CSR_PORRSTF)
        reason |= RESET_POWER_ON;
    if (csr & RCC_CSR_PINRSTF)
        reason |= RESET_HARDWARE;
    if (csr & RCC_CSR_BORRSTF)
        reason |= RESET_BROWN_OUT;

    return reason;
}

void pwrEnableAndClockRtc(enum RtcClock rtcClock)
{
    uint32_t backupRegs[RTC_NUM_BACKUP_REGS], i, *regs = rtcGetBackupStorage();

    /* Enable power clock */
    pwrUnitClock(PERIPH_BUS_APB1, PERIPH_APB1_PWR, true);

    /* Enable write permission for backup domain */
    pwrEnableWriteBackupDomainRegs();
    /* Prevent compiler reordering across this boundary. */
    mem_reorder_barrier();

    /* backup the backup regs (they have valuable data we want to persist) */
    for (i = 0; i < RTC_NUM_BACKUP_REGS; i++)
        backupRegs[i] = regs[i];

    /* save and reset reset flags */
    mResetReason = pwrParseCsr(RCC->CSR);
    RCC->CSR |= RCC_CSR_RMVF;

    /* Reset backup domain */
    RCC->BDCR |= RCC_BDCR_BDRST;
    /* Exit reset of backup domain */
    RCC->BDCR &= ~RCC_BDCR_BDRST;

    /* restore the backup regs */
    for (i = 0; i < RTC_NUM_BACKUP_REGS; i++)
        regs[i] = backupRegs[i];

    if (rtcClock == RTC_CLK_LSE || rtcClock == RTC_CLK_LSE_BYPASS) {
        /* Disable LSI */
        RCC->CSR &= ~RCC_CSR_LSION;
        if (rtcClock == RTC_CLK_LSE) {
            /* Set LSE as backup domain clock source */
            RCC->BDCR |= RCC_BDCR_LSEON;
        } else {
            /* Set LSE as backup domain clock source and enable bypass */
            RCC->BDCR |= RCC_BDCR_LSEON | RCC_BDCR_LSEBYP;
        }
        /* Wait for LSE to be ready */
        while ((RCC->BDCR & RCC_BDCR_LSERDY) == 0);
        /* Set LSE as RTC clock source */
        RCC->BDCR |= RCC_BDCR_RTCSEL_LSE;
    } else {
        /* Enable LSI */
        RCC->CSR |= RCC_CSR_LSION;
        /* Wait for LSI to be ready */
        while ((RCC->CSR & RCC_CSR_LSIRDY) == 0);
        /* Set LSI as RTC clock source */
        RCC->BDCR |= RCC_BDCR_RTCSEL_LSI;
    }
    /* Enable RTC */
    RCC->BDCR |= RCC_BDCR_RTCEN;
}

void pwrEnableWriteBackupDomainRegs(void)
{
    PWR->CR |= PWR_CR_DBP;
}

void pwrSetSleepType(enum Stm32F4xxSleepType sleepType)
{
    uint32_t cr = PWR->CR &~ (PWR_CR_MRVLDS | PWR_CR_LPLVDS | PWR_CR_FPDS | PWR_CR_PDDS | PWR_CR_LPDS);

    switch (sleepType) {
    case stm32f411SleepModeSleep:
        SCB->SCR &=~ SCB_SCR_SLEEPDEEP_Msk;
        break;
    case stm32f411SleepModeStopMR:
        SCB->SCR |= SCB_SCR_SLEEPDEEP_Msk;
        break;
    case stm32f411SleepModeStopMRFPD:
        SCB->SCR |= SCB_SCR_SLEEPDEEP_Msk;
        cr |= PWR_CR_FPDS;
        break;
    case stm32f411SleepModeStopLPFD:
        SCB->SCR |= SCB_SCR_SLEEPDEEP_Msk;
        cr |= PWR_CR_FPDS | PWR_CR_LPDS;
        break;
    case stm32f411SleepModeStopLPLV:
        SCB->SCR |= SCB_SCR_SLEEPDEEP_Msk;
        cr |= PWR_CR_LPLVDS | PWR_CR_LPDS;
        break;
    }

    PWR->CR = cr;
}

void pwrSystemInit(void)
{
    RCC->CR |= 1;                             //HSI on
    while (!(RCC->CR & 2));                    //wait for HSI
    RCC->CFGR = 0x00000000;                   //all busses at HSI speed
    RCC->CR &= 0x0000FFF1;                    //HSI on, all else off
}

uint32_t pwrResetReason(void)
{
    return mResetReason;
}
