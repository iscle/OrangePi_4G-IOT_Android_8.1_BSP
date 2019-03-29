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

#include <plat/cmsis.h>
#include <plat/plat.h>
#include <plat/pwr.h>
#include <plat/wdt.h>
#include <syscall.h>
#include <string.h>
#include <seos.h>
#include <heap.h>
#include <cpu.h>
#include <util.h>
#include <reset.h>


#define HARD_FAULT_DROPBOX_MAGIC_MASK       0xFFFFC000
#define HARD_FAULT_DROPBOX_MAGIC_VAL        0x31414000
#define HARD_FAULT_DROPBOX_MAGIC_HAVE_DROP  0x00002000
#define HARD_FAULT_DROPBOX_MAGIC_DATA_MASK  0x00001FFF

union TidTrig {
    struct {
        uint32_t tid : 16;
        uint32_t trig : 2;
        uint32_t RFU : 14;
    };
    uint32_t rw;
};

// These registers only support word accesses (r/w)
// Make sure to only use uint32_t's
struct RamPersistedDataAndDropbox {
    uint32_t magic; // and part of dropbox
    uint32_t r[16];
    uint32_t sr_hfsr_cfsr_lo;
    uint32_t bits;
    union TidTrig tid_trig; // only access via tid_trig.rw
};

/* //if your device persists ram, you can use this instead:
 * static struct RamPersistedDataAndDropbox* getPersistedData(void)
 * {
 *     static struct RamPersistedDataAndDropbox __attribute__((section(".neverinit"))) dbx;
 *     return &dbx;
 * }
 */

static struct RamPersistedDataAndDropbox* getPersistedData(void)
{
    uint32_t bytes = 0;
    void *loc = platGetPersistentRamStore(&bytes);

    return bytes >= sizeof(struct RamPersistedDataAndDropbox) ? (struct RamPersistedDataAndDropbox*)loc : NULL;
}

static struct RamPersistedDataAndDropbox* getInitedPersistedData(void)
{
    struct RamPersistedDataAndDropbox* dbx = getPersistedData();

    if ((dbx->magic & HARD_FAULT_DROPBOX_MAGIC_MASK) != HARD_FAULT_DROPBOX_MAGIC_VAL) {
        dbx->bits = 0;
        dbx->magic = HARD_FAULT_DROPBOX_MAGIC_VAL;
    }

    return dbx;
}

void cpuInit(void)
{
    /* set SVC to be highest possible priority */
    NVIC_SetPriority(SVCall_IRQn, 0xff);

    /* FPU on */
    SCB->CPACR |= 0x00F00000;
}

//pack all our SR regs into 45 bits
static void cpuPackSrBits(uint32_t *dstLo, uint32_t *dstHi, uint32_t sr, uint32_t hfsr, uint32_t cfsr)
{
    //mask of useful bits:
    // SR:   11111111 00000000 11111101 11111111 (total of 23 bits)
    // HFSR: 01000000 00000000 00000000 00000010 (total of  2 bits)
    // CFSR: 00000011 00001111 10111111 10111111 (total of 20 bits)
    // so our total is 45 bits. we pack this into 2 longs (for now)

    sr   &= 0xFF00FDFF;
    hfsr &= 0x40000002;
    cfsr &= 0x030FBFBF;

    *dstLo = sr | ((cfsr << 4) & 0x00FF0000) | (hfsr >> 12) | (hfsr << 8);
    *dstHi = ((cfsr & 0x01000000) >> 18) | ((cfsr & 0x02000000) >> 13) | (cfsr & 0x00000fff);
}

//unpack the SR bits
static void cpuUnpackSrBits(uint32_t srcLo, uint32_t srcHi, uint32_t *srP, uint32_t *hfsrP, uint32_t *cfsrP)
{
    *srP = srcLo & 0xFF00FDFF;
    *hfsrP = ((srcLo << 12) & 0x40000000) | ((srcLo >> 8) & 0x00000002);
    *cfsrP = ((srcLo & 0x00FB0000) >> 4) | (srcHi & 0x0FBF) | ((srcHi << 13) & 0x02000000) | ((srcHi << 18) & 0x01000000);
}

static void cpuDbxDump(struct RamPersistedDataAndDropbox *dbx)
{
    uint32_t i, hfsr, cfsr, sr;
    union TidTrig tid_trig;
    const char *trigName;
    static const char *trigNames[] = { "UNKNOWN", "HARD FAULT", "WDT", "MPU" };

    if (dbx) {
        for (i = 0; i < 8; i++)
            osLog(LOG_ERROR, "  R%02lu  = 0x%08lX  R%02lu  = 0x%08lX\n",
                  i, dbx->r[i], i + 8, dbx->r[i+8]);

        cpuUnpackSrBits(dbx->sr_hfsr_cfsr_lo, dbx->magic & HARD_FAULT_DROPBOX_MAGIC_DATA_MASK, &sr, &hfsr, &cfsr);

        osLog(LOG_ERROR, "  xPSR = 0x%08lX  HFSR = 0x%08lX\n", sr, hfsr);
        osLog(LOG_ERROR, "  CFSR = 0x%08lX  BITS = 0x%08lX\n", cfsr, dbx->bits);
        // reboot source (if known), reported as TRIG
        // so far we have 3 reboot sources reported here:
        // 1 - HARD FAULT
        // 2 - WDT
        // 3 - MPU
        tid_trig.rw = dbx->tid_trig.rw;
        trigName = trigNames[tid_trig.trig < ARRAY_SIZE(trigNames) ? tid_trig.trig : 0];
        osLog(LOG_ERROR, "  TID  = 0x%04" PRIX16 "  TRIG = 0x%04" PRIX16 " [%s]\n\n", tid_trig.tid, tid_trig.trig, trigName);
    }
}

void cpuInitLate(void)
{
    struct RamPersistedDataAndDropbox *dbx = getInitedPersistedData();
    uint32_t reason = pwrResetReason();
    const char *reasonDesc = "";
    enum LogLevel level = LOG_INFO;

    // if we detected WDT reboot reason, we are likely not having data in dropbox
    // All we can do is report that WDT reset happened
    if ((reason & (RESET_WINDOW_WATCHDOG|RESET_INDEPENDENT_WATCHDOG)) != 0) {
        reasonDesc = "; HW WDT RESET";
        level = LOG_ERROR;
    }

    osLog(level, "Reboot reason: 0x%08" PRIX32 "%s\n", reason, reasonDesc);

    /* print and clear dropbox */
    if (dbx->magic & HARD_FAULT_DROPBOX_MAGIC_HAVE_DROP) {
        osLog(LOG_INFO, "Dropbox not empty. Contents:\n");
        cpuDbxDump(dbx);
    }
    dbx->magic &=~ HARD_FAULT_DROPBOX_MAGIC_HAVE_DROP;
}

bool cpuRamPersistentBitGet(uint32_t which)
{
    struct RamPersistedDataAndDropbox *dbx = getInitedPersistedData();

    return (which < CPU_NUM_PERSISTENT_RAM_BITS) && ((dbx->bits >> which) & 1);
}

void cpuRamPersistentBitSet(uint32_t which, bool on)
{
    struct RamPersistedDataAndDropbox *dbx = getInitedPersistedData();

    if (which < CPU_NUM_PERSISTENT_RAM_BITS) {
        if (on)
            dbx->bits |= (1ULL << which);
        else
            dbx->bits &=~ (1ULL << which);
    }
}

uint64_t cpuIntsOff(void)
{
    uint32_t state;

    asm volatile (
        "mrs %0, PRIMASK    \n"
        "cpsid i            \n"
        :"=r"(state)
    );

    return state;
}

uint64_t cpuIntsOn(void)
{
    uint32_t state;

    asm volatile (
        "mrs %0, PRIMASK    \n"
        "cpsie i            \n"
        :"=r"(state)
    );

    return state;
}

void cpuIntsRestore(uint64_t state)
{

    asm volatile(
        "msr PRIMASK, %0   \n"
        ::"r"((uint32_t)state)
    );
}

/*
 * Stack layout for HW interrupt handlers [ARM7-M]
 * ===============================================
 * orig_SP[8...25] = FPU state (S0..S15, FPSCR, Reserved) [if enabled]
 * orig_SP[7] = xPSR
 * orig_SP[6] = ReturnAddress
 * orig_SP[5] = LR (R14)
 * orig_SP[4] = R12
 * orig_SP[3..0] = R3..R0
 *
 * upon entry, new value of LR is calculated as follows:
 * LR := 0xFFFFFFE0 | <Control.FPCA ? 0 : 0x10> | <Mode_Handler ? 0 : 8> | <SPSEL ? 4 : 0> | 0x01
 *
 * upon exit, PC value is interpreted  according to the same rule (see LR above)
 * if bits 28..31 of PC equal 0xF, return from interrupt is executed
 * this way, "bx lr" would perform return from interrupt to the previous state
 */

static void __attribute__((used)) syscallHandler(uintptr_t *excRegs)
{
    uint16_t *svcPC = ((uint16_t *)(excRegs[6])) - 1;
    uint32_t svcNo = (*svcPC) & 0xFF;
    uint32_t syscallNr = excRegs[0];
    SyscallFunc handler;
    va_list args_long = *(va_list*)(excRegs + 1);
    uintptr_t *fastParams = excRegs + 1;
    va_list args_fast = *(va_list*)(&fastParams);

    if (svcNo > 1)
        osLog(LOG_WARN, "Unknown SVC 0x%02lX called at 0x%08lX\n", svcNo, (unsigned long)svcPC);
    else if (!(handler = syscallGetHandler(syscallNr)))
        osLog(LOG_WARN, "Unknown syscall 0x%08lX called at 0x%08lX\n", (unsigned long)syscallNr, (unsigned long)svcPC);
    else
        handler(excRegs, svcNo ? args_fast : args_long);
}

void SVC_Handler(void);
void __attribute__((naked)) SVC_Handler(void)
{
    asm volatile(
        "tst lr, #4         \n"
        "ite eq             \n"
        "mrseq r0, msp      \n"
        "mrsne r0, psp      \n"
        "b syscallHandler   \n"
    );
}

static void __attribute__((used)) logHardFault(uintptr_t *excRegs, uintptr_t* otherRegs, bool tinyStack, uint32_t code)
{
    struct RamPersistedDataAndDropbox *dbx = getInitedPersistedData();
    uint32_t i, hi;
    union TidTrig tid_trig;

    wdtPing();

    for (i = 0; i < 4; i++)
        dbx->r[i] = excRegs[i];
    for (i = 0; i < 8; i++)
        dbx->r[i + 4] = otherRegs[i];
    dbx->r[12] = excRegs[4];
    dbx->r[13] = (uint32_t)(excRegs + 8);
    dbx->r[14] = excRegs[5];
    dbx->r[15] = excRegs[6];

    cpuPackSrBits(&dbx->sr_hfsr_cfsr_lo, &hi, excRegs[7], SCB->HFSR, SCB->CFSR);
    dbx->magic |= HARD_FAULT_DROPBOX_MAGIC_HAVE_DROP | (hi & HARD_FAULT_DROPBOX_MAGIC_DATA_MASK);
    tid_trig.tid = osGetCurrentTid();
    tid_trig.trig = code;
    dbx->tid_trig.rw = tid_trig.rw;

    if (!tinyStack) {
        osLog(LOG_ERROR, "*HARD FAULT*\n");
        cpuDbxDump(dbx);
    }

    NVIC_SystemReset();
}

static uint32_t  __attribute__((used)) hfStack[16];

void cpuCommonFaultCode(void);
void __attribute__((naked)) cpuCommonFaultCode(void)
{
    // r0 contains Fault IRQ code
    asm volatile(
        "ldr r3, =__stack_bottom + 64 \n"
        "cmp sp, r3                \n"
        "itte le                   \n"
        "ldrle sp, =hfStack + 64   \n"
        "movle r2, #1              \n"
        "movgt r2, #0              \n"
        "mov r3, r0                \n"
        "tst lr, #4                \n"
        "ite eq                    \n"
        "mrseq r0, msp             \n"
        "mrsne r0, psp             \n"
        "push  {r4-r11}            \n"
        "mov   r1, sp              \n"
        "b     logHardFault        \n"
    );
}

void HardFault_Handler(void);
void __attribute__((naked)) HardFault_Handler(void)
{
    asm volatile(
        "mov    r0, #1             \n"
        "b      cpuCommonFaultCode \n"
    );
}
