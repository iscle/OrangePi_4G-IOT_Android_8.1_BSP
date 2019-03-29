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

#ifndef _HWR_H_
#define _HWR_H_

#ifdef __cplusplus
extern "C" {
#endif

#include <stdint.h>
#include <stdbool.h>
#include <plat/rtc.h>

/* busses */
#define PERIPH_BUS_AHB1               0
#define PERIPH_BUS_AHB2               1
#define PERIPH_BUS_AHB3               2
#define PERIPH_BUS_APB1               3
#define PERIPH_BUS_APB2               4


/* AHB 1 peripherals */
#define PERIPH_AHB1_GPIOA             0x00000001UL
#define PERIPH_AHB1_GPIOB             0x00000002UL
#define PERIPH_AHB1_GPIOC             0x00000004UL
#define PERIPH_AHB1_GPIOD             0x00000008UL
#define PERIPH_AHB1_GPIOE             0x00000010UL
#define PERIPH_AHB1_GPIOF             0x00000020UL
#define PERIPH_AHB1_GPIOG             0x00000040UL
#define PERIPH_AHB1_GPIOH             0x00000080UL
#define PERIPH_AHB1_GPIOI             0x00000100UL
#define PERIPH_AHB1_CRC               0x00001000UL
#define PERIPH_AHB1_FLITF             0x00008000UL
#define PERIPH_AHB1_SRAM1             0x00010000UL
#define PERIPH_AHB1_SRAM2             0x00020000UL
#define PERIPH_AHB1_BKPSRAM           0x00040000UL
#define PERIPH_AHB1_SRAM3             0x00080000UL
#define PERIPH_AHB1_CCMDATARAMEN      0x00100000UL
#define PERIPH_AHB1_DMA1              0x00200000UL
#define PERIPH_AHB1_DMA2              0x00400000UL
#define PERIPH_AHB1_ETH_MAC           0x02000000UL
#define PERIPH_AHB1_ETH_MAC_TX        0x04000000UL
#define PERIPH_AHB1_ETH_MAC_RX        0x08000000UL
#define PERIPH_AHB1_ETH_MAC_PTP       0x10000000UL
#define PERIPH_AHB1_OTG_HS            0x20000000UL
#define PERIPH_AHB1_OTG_HS_ULPI       0x40000000UL

/* AHB 2 peripherals */
#define PERIPH_AHB2_DCMI              0x00000001UL
#define PERIPH_AHB2_CRYP              0x00000010UL
#define PERIPH_AHB2_HASH              0x00000020UL
#define PERIPH_AHB2_RNG               0x00000040UL
#define PERIPH_AHB2_OTG_FS            0x00000080UL

/* AHB 3 peripherals */
#define PERIPH_AHB3_FSMC              0x00000001UL

/* APB 1 peripherals */
#define PERIPH_APB1_TIM2              0x00000001UL
#define PERIPH_APB1_TIM3              0x00000002UL
#define PERIPH_APB1_TIM4              0x00000004UL
#define PERIPH_APB1_TIM5              0x00000008UL
#define PERIPH_APB1_TIM6              0x00000010UL
#define PERIPH_APB1_TIM7              0x00000020UL
#define PERIPH_APB1_TIM12             0x00000040UL
#define PERIPH_APB1_TIM13             0x00000080UL
#define PERIPH_APB1_TIM14             0x00000100UL
#define PERIPH_APB1_WWDG              0x00000800UL
#define PERIPH_APB1_SPI2              0x00004000UL
#define PERIPH_APB1_SPI3              0x00008000UL
#define PERIPH_APB1_USART2            0x00020000UL
#define PERIPH_APB1_USART3            0x00040000UL
#define PERIPH_APB1_UART4             0x00080000UL
#define PERIPH_APB1_UART5             0x00100000UL
#define PERIPH_APB1_I2C1              0x00200000UL
#define PERIPH_APB1_I2C2              0x00400000UL
#define PERIPH_APB1_I2C3              0x00800000UL
#define PERIPH_APB1_CAN1              0x02000000UL
#define PERIPH_APB1_CAN2              0x04000000UL
#define PERIPH_APB1_PWR               0x10000000UL
#define PERIPH_APB1_DAC               0x20000000UL
#define PERIPH_APB1_UART7             0x40000000UL
#define PERIPH_APB1_UART8             0x80000000UL

/* APB 2 peripherals */
#define PERIPH_APB2_TIM1              0x00000001UL
#define PERIPH_APB2_TIM8              0x00000002UL
#define PERIPH_APB2_USART1            0x00000010UL
#define PERIPH_APB2_USART6            0x00000020UL
#define PERIPH_APB2_ADC               0x00000100UL
#define PERIPH_APB2_ADC1              0x00000100UL
#define PERIPH_APB2_ADC2              0x00000200UL
#define PERIPH_APB2_ADC3              0x00000400UL
#define PERIPH_APB2_SDIO              0x00000800UL
#define PERIPH_APB2_SPI1              0x00001000UL
#define PERIPH_APB2_SPI4              0x00002000UL
#define PERIPH_APB2_SYSCFG            0x00004000UL
#define PERIPH_APB2_TIM9              0x00010000UL
#define PERIPH_APB2_TIM10             0x00020000UL
#define PERIPH_APB2_TIM11             0x00040000UL
#define PERIPH_APB2_SPI5              0x00100000UL
#define PERIPH_APB2_SPI6              0x00200000UL




/* base addrs */
#define UDID_BASE                     0x1FFF7A10UL
#define TIM2_BASE                     0x40000000UL
#define TIM3_BASE                     0x40000400UL
#define TIM4_BASE                     0x40000800UL
#define TIM5_BASE                     0x40000C00UL
#define RTC_BASE                      0x40002800UL
#define WWDG_BASE                     0x40002C00UL
#define IWDG_BASE                     0x40003000UL
#define SPI2_BASE                     0x40003800UL
#define SPI3_BASE                     0x40003C00UL
#define USART2_BASE                   0x40004400UL
#define USART3_BASE                   0x40004800UL
#define UART4_BASE                    0x40004C00UL
#define UART5_BASE                    0x40005000UL
#define I2C1_BASE                     0x40005400UL
#define I2C2_BASE                     0x40005800UL
#define I2C3_BASE                     0x40005C00UL
#define PWR_BASE                      0x40007000UL
#define TIM1_BASE                     0x40010000UL
#define USART1_BASE                   0x40011000UL
#define USART6_BASE                   0x40011400UL
#define SPI1_BASE                     0x40013000UL
#define SPI4_BASE                     0x40013400UL
#define SYSCFG_BASE                   0x40013800UL
#define EXTI_BASE                     0x40013C00UL
#define TIM9_BASE                     0x40014000UL
#define TIM10_BASE                    0x40014400UL
#define TIM11_BASE                    0x40014800UL
#define SPI5_BASE                     0x40015000UL
#define SPI6_BASE                     0x40015400UL
#define GPIOA_BASE                    0x40020000UL
#define GPIOB_BASE                    0x40020400UL
#define GPIOC_BASE                    0x40020800UL
#define GPIOD_BASE                    0x40020C00UL
#define GPIOE_BASE                    0x40021000UL
#define GPIOF_BASE                    0x40021400UL
#define GPIOG_BASE                    0x40021800UL
#define GPIOH_BASE                    0x40021C00UL
#define GPIOI_BASE                    0x40022000UL
#define CRC_BASE                      0x40023000UL
#define RCC_BASE                      0x40023800UL
#define FLASH_BASE                    0x40023C00UL
#define DMA1_BASE                     0x40026000UL
#define DMA2_BASE                     0x40026400UL
#define DBGMCU_BASE                   0xE0042000UL


enum Stm32F4xxSleepType {       //current       power          wkup way       wkup speed   (typ/max)
    stm32f411SleepModeSleep,    //2.7-5.9mA     all-core       interrupt      1 cy
    stm32f411SleepModeStopMR,   //111uA         RTC,flash,reg  EXTI           13.5/14.5us
    stm32f411SleepModeStopMRFPD,// 73uA         RTC,reg        EXTI           105/111us
    stm32f411SleepModeStopLPFD, // 42uA         RTC,lpreg      EXTI           113/130us
    stm32f411SleepModeStopLPLV, // 10uA         RTC            EXTT           314/407us (actually lower, but not quoted)
};

/* funcs */
void pwrSystemInit(void);
uint32_t pwrResetReason(void);
void pwrUnitClock(uint32_t bus, uint32_t unit, bool on);
void pwrUnitReset(uint32_t bus, uint32_t unit, bool on);
uint32_t pwrGetBusSpeed(uint32_t bus);
void pwrEnableAndClockRtc(enum RtcClock);
void pwrEnableWriteBackupDomainRegs(void);

/* internal to platform */
void pwrSetSleepType(enum Stm32F4xxSleepType sleepType);

#ifdef __cplusplus
}
#endif

#endif

