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


struct StmFlash {
    volatile uint32_t ACR;
    volatile uint32_t KEYR;
    volatile uint32_t OPTKEYR;
    volatile uint32_t SR;
    volatile uint32_t CR;
    volatile uint32_t OPTCR;
};

#define FLASH	((struct StmFlash*)0x40023C00UL)

static void flashEraseAll(void);
static void flashWriteOneK(uint32_t addr, const uint8_t* data);


//i am too lazy to make a linker script. if this is first here, gcc will place it first in the file...live with it
void __attribute__((naked)) _start(void) {
    asm volatile (
        "b.w flashEraseAll    \n"
        "b.w flashWriteOneK   \n"
    );
}

static void flashUnlock(void)
{
    //this will PURPOSEFULLY hang in case of unlock error (since chip will not unlock till reset anyways)
    while (FLASH->CR & 0x80000000) {
        FLASH->KEYR = 0x45670123;
        FLASH->KEYR = 0xCDEF89AB;
    }
}

static void flashWait(void)
{
    while (FLASH->SR & 0x00010000);
}

static void __attribute__((used)) flashEraseAll(void)
{
    flashUnlock();
    FLASH->CR = 0x00010004;		//erase it all
    flashWait();
}

static void __attribute__((used)) flashWriteOneK(uint32_t addr, const uint8_t* data)
{
    const uint32_t *data32 = (const uint32_t*)data;
    uint32_t i;

    flashUnlock();
    FLASH->CR = 0x201;	//program word at a time

    for (i = 0; i < 1024; i += 4) {
        *(volatile uint32_t*)(addr + i) = *data32++;
        flashWait();
    }
}


