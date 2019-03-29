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

#include <stdint.h>
#include <seos.h>

#define VEC_(nm, pfx)    void nm##pfx(void) __attribute__ ((weak, alias ("IntDefaultHandler")))
#define VEC(nm)        VEC_(nm, _Handler)
#define VECI(nm)    VEC_(nm, _IRQHandler)



#ifndef OS_STACK_SIZE
#define OS_STACK_SIZE 2048
#endif

void __attribute__ ((weak)) IntDefaultHandler(void);
VEC(NMI);
VEC(HardFault);
VEC(MemoryManagemntFault);
VEC(BusFault);
VEC(UsageFault);
VEC(SVC);
VEC(DebugMonitor);
VEC(PendSV);
VEC(SysTick);

VECI(WWDG);
VECI(EXTI16_PVD);
VECI(EXTI21_TAMP_STAMP);
VECI(EXTI22_RTC_WKUP);
VECI(FLASH);
VECI(RCC);
VECI(EXTI0);
VECI(EXTI1);
VECI(EXTI2);
VECI(EXTI3);
VECI(EXTI4);
VECI(DMA1_Stream0);
VECI(DMA1_Stream1);
VECI(DMA1_Stream2);
VECI(DMA1_Stream3);
VECI(DMA1_Stream4);
VECI(DMA1_Stream5);
VECI(DMA1_Stream6);
VECI(ADC);
VECI(EXTI9_5);
VECI(TIM1_BRK_TIM9);
VECI(TIM1_UP_TIM10);
VECI(TIM1_TRG_COM_TIM11);
VECI(TIM1_CC);
VECI(TIM2);
VECI(TIM3);
VECI(TIM4);
VECI(I2C1_EV);
VECI(I2C1_ER);
VECI(I2C2_EV);
VECI(I2C2_ER);
VECI(SPI1);
VECI(SPI2);
VECI(USART1);
VECI(USART2);
VECI(EXTI15_10);
VECI(EXTI17_RTC_ALARM);
VECI(EXTI18_OTG_FS_WKUP);
VECI(DMA1_Stream7);
VECI(SDIO);
VECI(TIM5);
VECI(SPI3);
VECI(DMA2_Stream0);
VECI(DMA2_Stream1);
VECI(DMA2_Stream2);
VECI(DMA2_Stream3);
VECI(DMA2_Stream4);
VECI(OTG_FS);
VECI(DMA2_Stream5);
VECI(DMA2_Stream6);
VECI(DMA2_Stream7);
VECI(USART6);
VECI(I2C3_EV);
VECI(I2C3_ER);
VECI(FPU);
VECI(SPI4);
VECI(SPI5);


//stack top (provided by linker)
extern uint32_t __stack_top[];
extern uint32_t __data_data[];
extern uint32_t __data_start[];
extern uint32_t __data_end[];
extern uint32_t __bss_start[];
extern uint32_t __bss_end[];




//OS stack
uint64_t __attribute__ ((section (".stack"))) _STACK[OS_STACK_SIZE / sizeof(uint64_t)];

void __attribute__((noreturn)) IntDefaultHandler(void)
{
    while (1) {
        //ints off
        asm("cpsid i");

        //spin/sleep/whatever forever
        asm("wfi":::"memory");
    }
}

void __attribute__((noreturn)) ResetISR(void);
void __attribute__((noreturn)) ResetISR(void)
{
    uint32_t *dst, *src, *end;

    //copy data
    dst = __data_start;
    src = __data_data;
    end = __data_end;
    while(dst != end)
        *dst++ = *src++;

    //init bss
    dst = __bss_start;
    end = __bss_end;
    while(dst != end)
        *dst++ = 0;

    //call code
    osMain();

    //if main returns => bad
    while(1);
}


//vector table
__attribute__ ((section(".vectors"))) __attribute__((naked)) void __VECTORS(void);
__attribute__ ((section(".vectors"))) __attribute__((naked)) void __VECTORS(void)
{
    asm volatile (
        ".word __stack_top                      \n"
        ".word ResetISR + 1                     \n"
        ".word NMI_Handler + 1                  \n"
        ".word HardFault_Handler + 1            \n"
        ".word MemoryManagemntFault_Handler + 1 \n"
        ".word BusFault_Handler + 1             \n"
        ".word UsageFault_Handler + 1           \n"
        ".word 0                                \n"
        ".word 0                                \n"
        ".word 0                                \n"
        ".word 0                                \n"
        ".word SVC_Handler + 1                  \n"
        ".word DebugMonitor_Handler + 1         \n"
        ".word 0                                \n"
        ".word PendSV_Handler + 1               \n"
        ".word SysTick_Handler + 1              \n"

        ".word WWDG_IRQHandler + 1              \n"
        ".word EXTI16_PVD_IRQHandler + 1        \n"
        ".word EXTI21_TAMP_STAMP_IRQHandler + 1 \n"
        ".word EXTI22_RTC_WKUP_IRQHandler + 1   \n"
        ".word FLASH_IRQHandler + 1             \n"
        ".word RCC_IRQHandler + 1               \n"
        ".word EXTI0_IRQHandler + 1             \n"
        ".word EXTI1_IRQHandler + 1             \n"
        ".word EXTI2_IRQHandler + 1             \n"
        ".word EXTI3_IRQHandler + 1             \n"
        ".word EXTI4_IRQHandler + 1             \n"
        ".word DMA1_Stream0_IRQHandler + 1      \n"
        ".word DMA1_Stream1_IRQHandler + 1      \n"
        ".word DMA1_Stream2_IRQHandler + 1      \n"
        ".word DMA1_Stream3_IRQHandler + 1      \n"
        ".word DMA1_Stream4_IRQHandler + 1      \n"
        ".word DMA1_Stream5_IRQHandler + 1      \n"
        ".word DMA1_Stream6_IRQHandler + 1      \n"
        ".word ADC_IRQHandler + 1               \n"
        ".word 0                                \n"
        ".word 0                                \n"
        ".word 0                                \n"
        ".word 0                                \n"
        ".word EXTI9_5_IRQHandler + 1           \n"
        ".word TIM1_BRK_TIM9_IRQHandler + 1     \n"
        ".word TIM1_UP_TIM10_IRQHandler + 1     \n"
        ".word TIM1_TRG_COM_TIM11_IRQHandler + 1\n"
        ".word TIM1_CC_IRQHandler + 1           \n"
        ".word TIM2_IRQHandler + 1              \n"
        ".word TIM3_IRQHandler + 1              \n"
        ".word TIM4_IRQHandler + 1              \n"
        ".word I2C1_EV_IRQHandler + 1           \n"
        ".word I2C1_ER_IRQHandler + 1           \n"
        ".word I2C2_EV_IRQHandler + 1           \n"
        ".word I2C2_ER_IRQHandler + 1           \n"
        ".word SPI1_IRQHandler + 1              \n"
        ".word SPI2_IRQHandler + 1              \n"
        ".word USART1_IRQHandler + 1            \n"
        ".word USART2_IRQHandler + 1            \n"
        ".word 0                                \n"
        ".word EXTI15_10_IRQHandler + 1         \n"
        ".word EXTI17_RTC_ALARM_IRQHandler + 1  \n"
        ".word EXTI18_OTG_FS_WKUP_IRQHandler + 1\n"
        ".word 0                                \n"
        ".word 0                                \n"
        ".word 0                                \n"
        ".word 0                                \n"
        ".word DMA1_Stream7_IRQHandler + 1      \n"
        ".word 0                                \n"
        ".word SDIO_IRQHandler + 1              \n"
        ".word TIM5_IRQHandler + 1              \n"
        ".word SPI3_IRQHandler + 1              \n"
        ".word 0                                \n"
        ".word 0                                \n"
        ".word 0                                \n"
        ".word 0                                \n"
        ".word DMA2_Stream0_IRQHandler + 1      \n"
        ".word DMA2_Stream1_IRQHandler + 1      \n"
        ".word DMA2_Stream2_IRQHandler + 1      \n"
        ".word DMA2_Stream3_IRQHandler + 1      \n"
        ".word DMA2_Stream4_IRQHandler + 1      \n"
        ".word 0                                \n"
        ".word 0                                \n"
        ".word 0                                \n"
        ".word 0                                \n"
        ".word 0                                \n"
        ".word 0                                \n"
        ".word OTG_FS_IRQHandler + 1            \n"
        ".word DMA2_Stream5_IRQHandler + 1      \n"
        ".word DMA2_Stream6_IRQHandler + 1      \n"
        ".word DMA2_Stream7_IRQHandler + 1      \n"
        ".word USART6_IRQHandler + 1            \n"
        ".word I2C3_EV_IRQHandler + 1           \n"
        ".word I2C3_ER_IRQHandler + 1           \n"
        ".word 0                                \n"
        ".word 0                                \n"
        ".word 0                                \n"
        ".word 0                                \n"
        ".word 0                                \n"
        ".word 0                                \n"
        ".word 0                                \n"
        ".word FPU_IRQHandler + 1               \n"
        ".word 0                                \n"
        ".word 0                                \n"
        ".word SPI4_IRQHandler + 1              \n"
        ".word SPI5_IRQHandler + 1              \n"
    );
};


