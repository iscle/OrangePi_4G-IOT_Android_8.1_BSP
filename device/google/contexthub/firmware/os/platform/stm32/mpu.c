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

#include <bl.h>
#include <cpu.h>
#include <mpu.h>
#include <platform.h>

#include <plat/cmsis.h>

#define MPU_REG_DEFAULT     0
#define MPU_REG_ROM         1
#define MPU_REG_RAM         2
#define MPU_REG_PERIPH      3
#define MPU_REG_PRIV_PERIPH 4

#define MPU_RASR_S          0x00040000
#define MPU_RASR_C          0x00020000
#define MPU_RASR_B          0x00010000

/* region type */
#define MPU_TYPE_SRAM       (MPU_RASR_S | MPU_RASR_C)
#define MPU_TYPE_FLASH      (MPU_RASR_C)
#define MPU_TYPE_PERIPH     (MPU_RASR_S | MPU_RASR_B)

/* region execute priviledges */
#define MPU_BIT_XN          (1UL << 28) /* no execute */

/* region access priviledges */
#define MPU_NA              (0UL << 24) /* S: no access   U: no access */
#define MPU_U_NA_S_RW       (1UL << 24) /* S: RW          U: no access */
#define MPU_U_RO_S_RW       (2UL << 24) /* S: RW          U: RO        */
#define MPU_RW              (3UL << 24) /* S: RW          U: RW        */
#define MPU_U_NA_S_RO       (5UL << 24) /* S: RO          U: no access */
#define MPU_U_RO_S_RO       (6UL << 24) /* S: RO          U: RO        */

/* subregion disable (not used so all zeroes) */
#define MPU_SRD_BITS        0x0000UL
#define MPU_BIT_ENABLE      1UL

/* these define rom */
extern uint8_t __shared_end[];
extern uint8_t __ram_start[];
extern uint8_t __ram_end[];

void MemoryManagemntFault_Handler(void);
void __attribute__((naked)) MemoryManagemntFault_Handler(void)
{
    asm volatile(
        "mov    r0, #3                    \n"
        "b      cpuCommonFaultCode        \n"
    );
}

static bool mpuRegionCfg(uint32_t regionNo, uint32_t start, uint32_t end, uint32_t attrs) /* region will be rounded to acceptable boundaries (32B minimum, self-aligned) by GROWTH */
{
    uint32_t proposedStart, lenVal = 1;
    uint64_t len, proposedLen, intState;

    if (start > end)
        return false;
    else
        len = end - start + UINT64_C(1);

    /* expand until it works */
    do {
        proposedStart = start &~ ((UINT64_C(1) << lenVal) - 1);
        proposedLen = start + len - proposedStart;
        if (proposedLen < 32)
            proposedLen = 32;
        lenVal = (proposedLen & (proposedLen - UINT64_C(1))) ? 64 - __builtin_clzll(proposedLen) : 63 - __builtin_clzll(proposedLen);

    } while (proposedStart & ((UINT64_C(1) << lenVal) - UINT64_C(1)));

    /* minimum size: 32 bytes */
    if (lenVal < 5)
        lenVal = 5;

    intState = cpuIntsOff();
    asm volatile("dsb\nisb");

    MPU->RNR = regionNo;
    MPU->RASR = 0; /* disable region before changing it */
    MPU->RBAR = proposedStart;
    MPU->RASR = MPU_SRD_BITS | MPU_BIT_ENABLE | attrs | ((lenVal-1) << 1);

    asm volatile("dsb\nisb");
    cpuIntsRestore(intState);

    return true;
}

static void mpuCfgRom(bool allowSvcWrite)
{
    mpuRegionCfg(MPU_REG_ROM, (uint32_t)&BL, (uint32_t)&__shared_end - 1, MPU_TYPE_FLASH | (allowSvcWrite ? MPU_U_RO_S_RW : MPU_U_RO_S_RO));
}

static void mpuCfgRam(bool allowSvcExecute)
{
    mpuRegionCfg(MPU_REG_RAM, (uint32_t)&__ram_start, (uint32_t)&__ram_end - 1, MPU_TYPE_SRAM | MPU_RW | (allowSvcExecute ? 0 : MPU_BIT_XN));
}


void mpuStart(void)
{
    MPU->CTRL = 0x00; // disable MPU

    /* 0x00000000 - 0xFFFFFFFF */
    mpuRegionCfg(MPU_REG_DEFAULT, 0, 0xFFFFFFFF, MPU_NA | MPU_BIT_XN);

    mpuCfgRom(false);
    mpuCfgRam(false);

    /* 0x40000000 - 0x4003FFFF */
    mpuRegionCfg(MPU_REG_PERIPH, 0x40000000, 0x4003FFFF, MPU_TYPE_PERIPH | MPU_U_NA_S_RW | MPU_BIT_XN);

    /* 0xE0000000 - 0xE00FFFFF */
    mpuRegionCfg(MPU_REG_PRIV_PERIPH, 0xE0000000, 0xE00FFFFF, MPU_TYPE_PERIPH | MPU_U_NA_S_RW | MPU_BIT_XN);

    //MPU on, even during faults, supervisor default: allow, user default: default deny
    MPU->CTRL = MPU_CTRL_ENABLE_Msk | MPU_CTRL_HFNMIENA_Msk | MPU_CTRL_PRIVDEFENA_Msk;
    SCB->SHCSR |= SCB_SHCSR_MEMFAULTENA_Msk;
}

void mpuAllowRamExecution(bool allowSvcExecute)
{
    mpuCfgRam(allowSvcExecute);
}

void mpuAllowRomWrite(bool allowSvcWrite)
{
    mpuCfgRom(allowSvcWrite);
}

void mpuShow()
{
    int i, regions = (MPU->TYPE & MPU_TYPE_DREGION_Msk) >> MPU_TYPE_DREGION_Pos;
    uint32_t addr, rasr;
    uint8_t ap;
    bool xn;
    char *s, *u;

    osLog(LOG_INFO, "MPU: %d HFNMIENA: %d PRIVDEFENA: %d\n",
        !!(MPU->CTRL & MPU_CTRL_ENABLE_Msk),
        !!(MPU->CTRL & MPU_CTRL_HFNMIENA_Msk),
        !!(MPU->CTRL & MPU_CTRL_PRIVDEFENA_Msk));
    for (i=0; i<regions; i++) {
        MPU->RNR = i;
        addr = MPU->RBAR & MPU_RBAR_ADDR_Msk;
        rasr = MPU->RASR;
        xn = rasr & MPU_RASR_XN_Msk;
        ap = (rasr & MPU_RASR_AP_Msk) >> MPU_RASR_AP_Pos;
        if (ap == 0) {
            s = "---";
        } else if (ap == 1 || ap == 2 || ap == 3) {
            if (xn)
                s = "RW-";
            else
                s = "RWX";
        } else if (ap == 5 || ap == 6 || ap == 7) {
            if (xn)
                s = "R--";
            else
                s = "R-X";
        } else {
            s = "???";
        }
        if (ap == 0 || ap == 1 || ap == 5) {
            u = "---";
        } else if (ap == 3) {
            if (xn)
                u = "RW-";
            else
                u = "RWX";
        } else if (ap == 2 || ap == 6 || ap == 7) {
            if (xn)
                u = "R--";
            else
                u = "R-X";
        } else {
            u = "???";
        }
        osLog(LOG_INFO,
            "%d: %c %08lx-%08lx S: %s U: %s TEX: %ld %c%c%c %02lx\n",
            i, (rasr & MPU_RASR_ENABLE_Msk) ? 'E' : 'D',
            addr,
            addr + (1 << (((rasr & MPU_RASR_SIZE_Msk) >> MPU_RASR_SIZE_Pos) + 1))-1,
            s, u,
            (rasr & MPU_RASR_TEX_Msk) >> MPU_RASR_TEX_Pos,
            (rasr & MPU_RASR_S_Msk) ? 'S' : ' ',
            (rasr & MPU_RASR_C_Msk) ? 'C' : ' ',
            (rasr & MPU_RASR_B_Msk) ? 'B' : ' ',
            (rasr & MPU_RASR_SRD_Msk) >> MPU_RASR_SRD_Pos);
    }
}
