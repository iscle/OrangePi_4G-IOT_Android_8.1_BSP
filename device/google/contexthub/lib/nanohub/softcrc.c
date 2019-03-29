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
#include <nanohub/crc.h>

/* this implements crc32 as crc.h defines it. It is not a normal CRC by any measure, so be careful with it */

static const uint32_t crctab[] =
{
        0x00000000, 0x04C11DB7, 0x09823B6E, 0x0D4326D9,
        0x130476DC ,0x17C56B6B ,0x1A864DB2 ,0x1E475005,
        0x2608EDB8, 0x22C9F00F, 0x2F8AD6D6, 0x2B4BCB61,
        0x350C9B64, 0x31CD86D3, 0x3C8EA00A, 0x384FBDBD
};

static uint32_t crcOneWord(uint32_t crc, uint32_t data, int cnt)
{
        int i;

        crc = crc ^ data;
        for (i = 0; i < cnt; i++)
                crc = (crc << 4) ^ crctab[crc >> 28];

        return crc;
}

uint32_t soft_crc32(const void *buf, size_t size, uint32_t crc)
{
        const uint32_t *data32 = (const uint32_t *)buf;
        const uint8_t *data8;
        uint32_t word;
        size_t i;

        // word by word crc32
        for (i = 0; i < size / 4; i++)
                crc = crcOneWord(crc, *data32++, 8);

        data8 = (const uint8_t*)data32;

        // zero pad last word if required
        if (size & 0x3) {
                for (i *= 4, word = 0; i < size; i++)
                        word |= (*data8++) << ((i & 0x3) * 8);
                crc = crcOneWord(crc, word, 8);
        }

        return crc;
}
