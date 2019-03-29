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

#include <stdbool.h>
#include <stdint.h>
#include <string.h>

#include <bl.h>
#include <eeData.h>

extern uint32_t __eedata_start[], __eedata_end[];

//STM32F4xx eedata stores data in 4-byte aligned chunks

static void* eeFind(uint32_t nameToFind, uint32_t *offset, bool findFirst, uint32_t *szP)
{
    uint32_t *p = __eedata_start + (offset ? *offset : 0);
    void *foundData = NULL;

    //find the last incarnation of "name" in flash area
    while (p < __eedata_end) {
        uint32_t info = *p++;
        uint32_t name = info & EE_DATA_NAME_MAX;
        uint32_t sz = info / (EE_DATA_NAME_MAX + 1);
        void *data = p;

        //skip over to next data chunk header
        p += (sz + 3) / 4;

        //check for a match
        if (nameToFind == name) {
            *szP = sz;
            foundData = data;

            if (findFirst)
                break;
        }

        //check for ending condition (name == max)
        if (name == EE_DATA_NAME_MAX)
            break;
    }

    if (offset)
        *offset = p - __eedata_start;

    return foundData;
}

static bool eeIsValidName(uint32_t name)
{
    return name && name < EE_DATA_NAME_MAX;
}

static void *eeDataGetEx(uint32_t name, uint32_t *offsetP, bool first, void *buf, uint32_t *szP)
{
    uint32_t sz = 0;
    void *data;

    if (!eeIsValidName(name))
        return false;

    //find the data item
    data = eeFind(name, offsetP, first, &sz);
    if (!data)
        return NULL;

    if (buf && szP) {    //get the data
        if (sz > *szP)
            sz = *szP;
        *szP = sz;
        memcpy(buf, data, sz);
    }
    else if (szP)        //get size
        *szP = sz;

    return (uint32_t*)data - 1;
}

bool eeDataGet(uint32_t name, void *buf, uint32_t *szP)
{
    uint32_t offset = 0;

    return eeDataGetEx(name, &offset, false, buf, szP) != NULL;
}

void *eeDataGetAllVersions(uint32_t name, void *buf, uint32_t *szP, void **stateP)
{
    uint32_t offset = *(uint32_t*)stateP;
    void *addr = eeDataGetEx(name, &offset, true, buf, szP);
    *(uint32_t*)stateP = offset;
    return addr;
}

static bool eeWrite(void *dst, const void *src, uint32_t len)
{
    return BL.blProgramEe(dst, src, len, BL_FLASH_KEY1, BL_FLASH_KEY2);
}

bool eeDataSet(uint32_t name, const void *buf, uint32_t len)
{
    uint32_t sz, effectiveSz, info = name + len * (EE_DATA_NAME_MAX + 1);
    bool ret = true;
    void *space;

    if (!eeIsValidName(name))
        return false;

    //find the empty space at the end of everything and make sure it is really empty (size == EE_DATA_LEN_MAX)
    space = eeFind(EE_DATA_NAME_MAX, NULL, false, &sz);
    if (!space || sz != EE_DATA_LEN_MAX)
        return false;

    //calculate effective size
    effectiveSz = (len + 3) &~ 3;

    //verify we have the space
    if ((uint8_t*)__eedata_end - (uint8_t*)space < effectiveSz)
        return false;

    //write it in
    ret = eeWrite(((uint32_t*)space) - 1, &info, sizeof(info)) && ret;
    ret = eeWrite(space, buf, len) && ret;

    return ret;
}

bool eeDataEraseOldVersion(uint32_t name, void *vaddr)
{
    uint32_t *addr = (uint32_t*)vaddr;
    uint32_t v;

    // sanity check
    if (!eeIsValidName(name) || addr < __eedata_start || addr >= (__eedata_end - 1))
        return false;

    v = *addr;

    //verify name
    if ((v & EE_DATA_NAME_MAX) != name)
        return false;

    //clear name
    v &=~ EE_DATA_NAME_MAX;

    //store result
    return eeWrite(addr, &v, sizeof(v));
}
