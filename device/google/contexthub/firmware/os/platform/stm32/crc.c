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

#include <string.h>

#include <nanohub/crc.h>
#include <seos.h>

#include <plat/pwr.h>

struct StmCrcRegs {
    volatile uint32_t DR;
    volatile uint32_t IDR;
    volatile uint32_t CR;
};

#define STM_CRC_CR_RESET        1

static uint32_t mRevCrcTab[] =
{
        0x00000000, 0xB2B4BCB6, 0x61A864DB, 0xD31CD86D,
        0xC350C9B6, 0x71E47500, 0xA2F8AD6D, 0x104C11DB,
        0x82608EDB, 0x30D4326D, 0xE3C8EA00 ,0x517C56B6,
        0x4130476D, 0xF384FBDB, 0x209823B6, 0x922C9F00
};

static uint32_t revCrc32Word(uint32_t crc, uint32_t data, uint32_t cnt)
{
    uint32_t i;

    for (i = 0; i < cnt; i++)
            crc = (crc >> 4) ^ mRevCrcTab[crc & 0x0F];

    return crc ^ data;
}

static struct StmCrcRegs *mCrcRegs = (struct StmCrcRegs *)CRC_BASE;

uint32_t crc32(const void *buf, size_t size, uint32_t crc)
{
    const uint32_t *words = (const uint32_t *)buf;
    size_t numWords = size / 4;
    unsigned int leftoverBytes = size % 4;
    size_t i;

    pwrUnitClock(PERIPH_BUS_AHB1, PERIPH_AHB1_CRC, true);

    if (mCrcRegs->DR == crc)
        ;
    else if (crc == CRC_INIT)
        mCrcRegs->CR = STM_CRC_CR_RESET;
    else
        mCrcRegs->DR = revCrc32Word(crc, mCrcRegs->DR, 8);

    for (i = 0; i < numWords; i++)
        mCrcRegs->DR = words[i];

    if (leftoverBytes) {
        uint32_t word = 0;
        memcpy(&word, words + numWords, leftoverBytes);
        /* n.b.: no shifting is needed, since the CRC block looks at the
         * lowest byte first (i.e., we need the padding in the upper bytes)
         */
        mCrcRegs->DR = word;
    }

    crc = mCrcRegs->DR;
    pwrUnitClock(PERIPH_BUS_AHB1, PERIPH_AHB1_CRC, false);
    return crc;
}
