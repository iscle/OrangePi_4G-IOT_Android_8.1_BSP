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

#include <nanohub/appRelocFormat.h>

#include <string.h>
#include <stdint.h>
#include <heap.h>
#include <seos.h>
#include <cpu.h>

#include <plat/cmsis.h>

//reloc types for this cpu type
#define NANO_RELOC_TYPE_RAM	0
#define NANO_RELOC_TYPE_FLASH	1

#define APP_FLASH_RELOC(_base, _offset) ((uint32_t)(_base) + FLASH_RELOC_OFFSET + (_offset))
#define APP_FLASH_RELOC_BASE(_base) APP_FLASH_RELOC(_base, 0)
#define APP_VEC(_app) ((struct AppFuncs*)&((_app)->vec))

static bool handleRelNumber(uint32_t *ofstP, uint32_t type, uint32_t flashAddr, uint32_t ramAddr, uint32_t *mem, uint32_t value)
{
    uint32_t base, where;

    switch (type) {

    case NANO_RELOC_TYPE_RAM:
        base = ramAddr;
        break;

    case NANO_RELOC_TYPE_FLASH:
        base = flashAddr;
        break;

    default:
        return false;
    }

    where = *ofstP + value;
    *ofstP = where + 1;

    mem[where] += base;

    return true;
}

static bool handleRelocs(const uint8_t *relStart, const uint8_t *relEnd, uint32_t flashStart, uint32_t ramStart, void *mem)
{
    uint32_t ofst = 0;
    uint32_t type = 0;

    while (relStart != relEnd) {

        uint32_t rel = *relStart++;

        if (rel <= MAX_8_BIT_NUM) {

            if (!handleRelNumber(&ofst, type, flashStart, ramStart, mem, rel))
                return false;
        }
        else switch (rel) {

        case TOKEN_32BIT_OFST:
            if (relEnd - relStart < 4)
                return false;
            rel = *(uint32_t*)relStart;
            relStart += sizeof(uint32_t);
            if (!handleRelNumber(&ofst, type, flashStart, ramStart, mem, rel))
                return false;
            break;

        case TOKEN_24BIT_OFST:
            if (relEnd - relStart < 3)
                return false;
            rel = *(uint16_t*)relStart;
            relStart += sizeof(uint16_t);
            rel += ((uint32_t)(*relStart++)) << 16;
            if (!handleRelNumber(&ofst, type, flashStart, ramStart, mem, rel + MAX_16_BIT_NUM))
                return false;
            break;

        case TOKEN_16BIT_OFST:
            if (relEnd - relStart < 2)
                return false;
            rel = *(uint16_t*)relStart;
            relStart += sizeof(uint16_t);
            if (!handleRelNumber(&ofst, type, flashStart, ramStart, mem, rel + MAX_8_BIT_NUM))
                return false;
            break;

        case TOKEN_CONSECUTIVE:
            if (relEnd - relStart < 1)
                return false;
            rel = *relStart++;
            rel += MIN_RUN_LEN;
            while (rel--)
                if (!handleRelNumber(&ofst, type, flashStart, ramStart, mem, 0))
                    return false;
            break;

        case TOKEN_RELOC_TYPE_CHG:
            if (relEnd - relStart < 1)
                return false;
            rel = *relStart++;
            rel++;
            type += rel;
            ofst = 0;
            break;

        case TOKEN_RELOC_TYPE_NEXT:
            type++;
            ofst = 0;
            break;
        }
    }

    return true;
}

bool cpuInternalAppLoad(const struct AppHdr *appHdr, struct PlatAppInfo *platInfo)
{
    platInfo->data = 0x00000000;

    return true;
}

bool cpuAppLoad(const struct AppHdr *app, struct PlatAppInfo *platInfo)
{
    const struct SectInfo *sect = &app->sect;
    const uint8_t *relocsStart = (const uint8_t*)APP_FLASH_RELOC(app, sect->rel_start);
    const uint8_t *relocsEnd = (const uint8_t*)APP_FLASH_RELOC(app, sect->rel_end);
    uint8_t *mem = heapAlloc(sect->bss_end);

    if (!mem)
        return false;

    //calculate and assign .DATA org (TODO: data_start must be always zero, exclude it form APP structures)
    platInfo->data = mem + sect->data_start;

    //clear .BSS
    memset(mem + sect->bss_start, 0, sect->bss_end - sect->bss_start);

    //copy initialized data and initialized .GOT
    memcpy(mem + sect->data_start, (uint8_t*)APP_FLASH_RELOC(app, sect->data_data), sect->got_end - sect->data_start);

    //perform relocs
    if (!handleRelocs(relocsStart, relocsEnd, (uintptr_t)APP_FLASH_RELOC_BASE(app), (uintptr_t)mem, (void*)mem)) {
        osLog(LOG_ERROR, "Relocs are invalid in this app. Aborting app load\n");
        heapFree(mem);
        return false;
    }

    return true;
}

void cpuAppUnload(const struct AppHdr *app, struct PlatAppInfo *platInfo)
{
    if (platInfo->data)
        heapFree((uint8_t*)platInfo->data - app->sect.data_start);
}

static uintptr_t __attribute__((naked)) callWithR9(const void *base, uint32_t offset, void *data, uintptr_t arg1, uintptr_t arg2)
{
    asm volatile (
        "add  r12, r0, r1  \n"
        "mov  r0,  r3      \n"
        "ldr  r1,  [sp]    \n"
        "push {r9, lr}     \n"
        "mov  r9, r2       \n"
        "blx  r12          \n"
        "pop  {r9, pc}     \n"
    );

    return 0; //dummy to fool gcc
}

bool cpuAppInit(const struct AppHdr *app, struct PlatAppInfo *platInfo, uint32_t tid)
{
    if (platInfo->data)
        return callWithR9((const void*)APP_FLASH_RELOC_BASE(app), app->vec.init, platInfo->data, tid, 0);
    else
        return APP_VEC(app)->init(tid);
}

void cpuAppEnd(const struct AppHdr *app, struct PlatAppInfo *platInfo)
{
    if (platInfo->data)
        (void)callWithR9((const void*)APP_FLASH_RELOC_BASE(app), app->vec.end, platInfo->data, 0, 0);
    else
        APP_VEC(app)->end();
    osLog(LOG_INFO, "App ID %016" PRIX64 "; TID=%04" PRIX32 " terminated\n", app->hdr.appId, osGetCurrentTid());
}

void cpuAppHandle(const struct AppHdr *app, struct PlatAppInfo *platInfo, uint32_t evtType, const void* evtData)
{
    if (platInfo->data)
        (void)callWithR9((const void*)APP_FLASH_RELOC_BASE(app), app->vec.handle, platInfo->data, evtType, (uintptr_t)evtData);
    else
        APP_VEC(app)->handle(evtType, evtData);
}

void cpuAppInvoke(const struct AppHdr *app, struct PlatAppInfo *platInfo,
                  void (*method)(uintptr_t, uintptr_t),uintptr_t arg1, uintptr_t arg2)
{
    if (platInfo->data) {
        uint32_t hasSvcAct = SCB->SHCSR & SCB_SHCSR_SVCALLACT_Msk;

        SCB->SHCSR &= ~SCB_SHCSR_SVCALLACT_Msk;
        (void)callWithR9(0, (uint32_t)method, platInfo->data, arg1, arg2);
        SCB->SHCSR |= hasSvcAct;
    } else {
        method(arg1, arg2);
    }
}
