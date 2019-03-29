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

#ifndef _VARIANT_NUCLEO_H_
#define _VARIANT_NUCLEO_H_

#ifdef __cplusplus
extern "C" {
#endif

#define VARIANT_VER                 0x00000000

//we have LSE in nucleo
#define RTC_CLK                     RTC_CLK_LSE

//spi bus for comms
#define PLATFORM_HOST_INTF_SPI_BUS  0

#define SH_INT_WAKEUP               GPIO_PA(0)
#define SH_EXTI_WAKEUP_IRQ          EXTI0_IRQn
#define AP_INT_WAKEUP               GPIO_PA(1)
#undef AP_INT_NONWAKEUP

#define DEBUG_UART_UNITNO           2
#define DEBUG_UART_GPIO_TX          GPIO_PA(2)
#define DEBUG_UART_GPIO_RX          GPIO_PA(3)

#define BMP280_I2C_BUS_ID           0

#define BMI160_TO_ANDROID_COORDINATE(x, y, z)   \
    do {                                        \
        float xi = x, yi = y, zi = z;           \
        x = xi; y = yi; z = zi;                 \
    } while (0)

#define BMM150_TO_ANDROID_COORDINATE(x, y, z)   \
    do {                                        \
        float xi = x, yi = y, zi = z;           \
        x = xi; y = -yi; z = -zi;               \
    } while (0)

#define HALL_PIN        GPIO_PB(5)
#define HALL_IRQ        EXTI9_5_IRQn

#define VSYNC_PIN       GPIO_PB(1)
#define VSYNC_IRQ       EXTI1_IRQn

#define PROX_INT_PIN    GPIO_PB(10)
#define PROX_IRQ        EXTI15_10_IRQn
#define PROX_I2C_BUS_ID 0

//define tap sensor threshold
#define TAP_THRESHOLD   0x01

//define Accelerometer fast compensation config
#define ACC_FOC_CONFIG  0x3d

#ifdef __cplusplus
}
#endif

#endif
