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

#include <errno.h>

#include <heap.h>
#include <seos.h>
#include <util.h>

#include <plat/cmsis.h>
#include <plat/dma.h>
#include <plat/pwr.h>

#define DMA_VERBOSE_DEBUG 0

#if DMA_VERBOSE_DEBUG
#define dmaLogDebug(x) osLog(LOG_DEBUG, x "\n")
#else
#define dmaLogDebug(x) do {} while(0)
#endif

#define STM_DMA_NUM_DEVS 2
#define STM_DMA_NUM_STREAMS 8

struct StmDmaStreamRegs {
    volatile uint32_t CR;
    volatile uint32_t NDTR;
    volatile uint32_t PAR;
    volatile uint32_t M0AR;
    volatile uint32_t M1AR;
    volatile uint32_t FCR;
};

struct StmDmaRegs {
    volatile uint32_t LISR;
    volatile uint32_t HISR;
    volatile uint32_t LIFCR;
    volatile uint32_t HIFCR;
    struct StmDmaStreamRegs Sx[STM_DMA_NUM_STREAMS];
};

#define STM_DMA_ISR_FEIFx               (1 << 0)
#define STM_DMA_ISR_DMEIFx              (1 << 2)
#define STM_DMA_ISR_TEIFx               (1 << 3)
#define STM_DMA_ISR_HTIFx               (1 << 4)
#define STM_DMA_ISR_TCIFx               (1 << 5)
#define STM_DMA_ISR_MASK \
    (STM_DMA_ISR_FEIFx | STM_DMA_ISR_DMEIFx | STM_DMA_ISR_TEIFx | \
     STM_DMA_ISR_HTIFx | STM_DMA_ISR_TCIFx)

#define STM_DMA_CR_EN                   (1 << 0)
#define STM_DMA_CR_DMEIE                (1 << 1)
#define STM_DMA_CR_TEIE                 (1 << 2)
#define STM_DMA_CR_HTIE                 (1 << 3)
#define STM_DMA_CR_TCIE                 (1 << 4)
#define STM_DMA_CR_PFCTRL               (1 << 5)

#define STM_DMA_CR_DIR(x)               ((x) << 6)

#define STM_DMA_CR_MINC                 (1 << 10)

#define STM_DMA_CR_PSIZE(x)             ((x) << 11)

#define STM_DMA_CR_MSIZE(x)             ((x) << 13)

#define STM_DMA_CR_PL(x)                ((x) << 16)
#define STM_DMA_CR_PBURST(x)            ((x) << 21)
#define STM_DMA_CR_MBURST(x)            ((x) << 23)

#define STM_DMA_CR_CHSEL(x)             ((x) << 25)
#define STM_DMA_CR_CHSEL_MASK           STM_DMA_CR_CHSEL(0x7)

struct StmDmaStreamState {
    DmaCallbackF callback;
    void *cookie;
    uint16_t tid;
    uint16_t reserved;
};

struct StmDmaDev {
    struct StmDmaRegs *const regs;
    struct StmDmaStreamState streams[STM_DMA_NUM_STREAMS];
};

static void dmaIsr(uint8_t busId, uint8_t stream);

#define DECLARE_IRQ_HANDLER(_n, _s)                             \
    extern void DMA##_n##_Stream##_s##_IRQHandler(void);        \
    void DMA##_n##_Stream##_s##_IRQHandler(void)                \
    {                                                           \
        dmaIsr(_n - 1, _s);                                     \
    }

static struct StmDmaDev gDmaDevs[STM_DMA_NUM_DEVS] = {
    [0] = {
        .regs = (struct StmDmaRegs *)DMA1_BASE,
    },
    [1] = {
        .regs = (struct StmDmaRegs *)DMA2_BASE,
    },
};
DECLARE_IRQ_HANDLER(1, 0)
DECLARE_IRQ_HANDLER(1, 1)
DECLARE_IRQ_HANDLER(1, 2)
DECLARE_IRQ_HANDLER(1, 3)
DECLARE_IRQ_HANDLER(1, 4)
DECLARE_IRQ_HANDLER(1, 5)
DECLARE_IRQ_HANDLER(1, 6)
DECLARE_IRQ_HANDLER(1, 7)
DECLARE_IRQ_HANDLER(2, 0)
DECLARE_IRQ_HANDLER(2, 1)
DECLARE_IRQ_HANDLER(2, 2)
DECLARE_IRQ_HANDLER(2, 3)
DECLARE_IRQ_HANDLER(2, 4)
DECLARE_IRQ_HANDLER(2, 5)
DECLARE_IRQ_HANDLER(2, 6)
DECLARE_IRQ_HANDLER(2, 7)

static const enum IRQn STM_DMA_IRQ[STM_DMA_NUM_DEVS][STM_DMA_NUM_STREAMS] = {
    [0] = {
        DMA1_Stream0_IRQn,
        DMA1_Stream1_IRQn,
        DMA1_Stream2_IRQn,
        DMA1_Stream3_IRQn,
        DMA1_Stream4_IRQn,
        DMA1_Stream5_IRQn,
        DMA1_Stream6_IRQn,
        DMA1_Stream7_IRQn,
    },
    [1] = {
        DMA2_Stream0_IRQn,
        DMA2_Stream1_IRQn,
        DMA2_Stream2_IRQn,
        DMA2_Stream3_IRQn,
        DMA2_Stream4_IRQn,
        DMA2_Stream5_IRQn,
        DMA2_Stream6_IRQn,
        DMA2_Stream7_IRQn,
    },
};


static const uint32_t STM_DMA_CLOCK_UNIT[STM_DMA_NUM_DEVS] = {
    PERIPH_AHB1_DMA1,
    PERIPH_AHB1_DMA2
};

static inline struct StmDmaStreamState *dmaGetStreamState(uint8_t busId,
        uint8_t stream)
{
    return &gDmaDevs[busId].streams[stream];
}

static inline struct StmDmaStreamRegs *dmaGetStreamRegs(uint8_t busId,
        uint8_t stream)
{
    return &gDmaDevs[busId].regs->Sx[stream];
}

static const unsigned int STM_DMA_FEIFx_OFFSET[] = { 0, 6, 16, 22 };

static inline uint8_t dmaGetIsr(uint8_t busId, uint8_t stream)
{
    struct StmDmaDev *dev = &gDmaDevs[busId];
    if (stream < 4)
        return (dev->regs->LISR >> STM_DMA_FEIFx_OFFSET[stream]) & STM_DMA_ISR_MASK;
    else
        return (dev->regs->HISR >> STM_DMA_FEIFx_OFFSET[stream - 4]) & STM_DMA_ISR_MASK;
}

static inline void dmaClearIsr(uint8_t busId, uint8_t stream, uint8_t mask)
{
    struct StmDmaDev *dev = &gDmaDevs[busId];
    if (stream < 4)
        dev->regs->LIFCR = mask << STM_DMA_FEIFx_OFFSET[stream];
    else
        dev->regs->HIFCR = mask << STM_DMA_FEIFx_OFFSET[stream - 4];
}

static void dmaIsrTeif(uint8_t busId, uint8_t stream)
{
    struct StmDmaStreamState *state = dmaGetStreamState(busId, stream);
    struct StmDmaStreamRegs *regs = dmaGetStreamRegs(busId, stream);

    dmaLogDebug("teif");
    dmaStop(busId, stream);

    uint16_t oldTid = osSetCurrentTid(state->tid);
    state->callback(state->cookie, regs->NDTR, EIO);
    osSetCurrentTid(oldTid);
}

static void dmaIsrTcif(uint8_t busId, uint8_t stream)
{
    struct StmDmaStreamState *state = dmaGetStreamState(busId, stream);
    struct StmDmaStreamRegs *regs = dmaGetStreamRegs(busId, stream);

    dmaLogDebug("tcif");
    dmaStop(busId, stream);

    uint16_t oldTid = osSetCurrentTid(state->tid);
    state->callback(state->cookie, regs->NDTR, 0);
    osSetCurrentTid(oldTid);
}

static void dmaIsr(uint8_t busId, uint8_t stream)
{
    struct StmDmaStreamState *state = dmaGetStreamState(busId, stream);

    if (UNLIKELY(!state->callback)) {
        osLog(LOG_WARN, "DMA %u stream %u ISR fired while disabled\n",
                busId, stream);
        dmaStop(busId, stream);
        return;
    }

    uint8_t isr = dmaGetIsr(busId, stream);

    if (isr & STM_DMA_ISR_TEIFx)
        dmaIsrTeif(busId, stream);
    else if (isr & STM_DMA_ISR_TCIFx)
        dmaIsrTcif(busId, stream);
}

int dmaStart(uint8_t busId, uint8_t stream, const void *buf, uint16_t size,
        const struct dmaMode *mode, DmaCallbackF callback, void *cookie)
{
    if (busId >= STM_DMA_NUM_DEVS || stream >= STM_DMA_NUM_STREAMS)
        return -EINVAL;

    struct StmDmaStreamState *state = dmaGetStreamState(busId, stream);
    state->callback = callback;
    state->cookie = cookie;
    state->tid = osGetCurrentTid();

    pwrUnitClock(PERIPH_BUS_AHB1, STM_DMA_CLOCK_UNIT[busId], true);

    struct StmDmaStreamRegs *regs = dmaGetStreamRegs(busId, stream);
    dmaClearIsr(busId, stream, STM_DMA_ISR_TEIFx);
    dmaClearIsr(busId, stream, STM_DMA_ISR_TCIFx);

    regs->NDTR = size;
    regs->PAR = mode->periphAddr;
    regs->M0AR = (uintptr_t)buf;
    regs->FCR = 0;
    regs->CR = STM_DMA_CR_TEIE |
            STM_DMA_CR_TCIE |
            STM_DMA_CR_DIR(mode->direction) |
            STM_DMA_CR_PSIZE(mode->psize) |
            STM_DMA_CR_MSIZE(mode->msize) |
            STM_DMA_CR_PL(mode->priority) |
            STM_DMA_CR_PBURST(mode->pburst) |
            STM_DMA_CR_MBURST(mode->mburst) |
            STM_DMA_CR_CHSEL(mode->channel);
    if (mode->minc)
        regs->CR |= STM_DMA_CR_MINC;

    NVIC_EnableIRQ(STM_DMA_IRQ[busId][stream]);

    regs->CR |= STM_DMA_CR_EN;
    return 0;
}

uint16_t dmaBytesLeft(uint8_t busId, uint8_t stream)
{
    struct StmDmaStreamRegs *regs = dmaGetStreamRegs(busId, stream);
    return regs->NDTR;
}

void dmaStop(uint8_t busId, uint8_t stream)
{
    struct StmDmaStreamRegs *regs = dmaGetStreamRegs(busId, stream);
    struct StmDmaStreamState *state = dmaGetStreamState(busId, stream);

    state->tid = 0;
    dmaClearIsr(busId, stream, STM_DMA_ISR_TEIFx);
    dmaClearIsr(busId, stream, STM_DMA_ISR_TCIFx);
    NVIC_DisableIRQ(STM_DMA_IRQ[busId][stream]);

    regs->CR &= ~STM_DMA_CR_EN;
    while (regs->CR & STM_DMA_CR_EN)
        ;

}

const enum IRQn dmaIrq(uint8_t busId, uint8_t stream)
{
    return STM_DMA_IRQ[busId][stream];
}

int dmaStopAll(uint32_t tid)
{
    int busId, stream, count = 0;

    for (busId = 0; busId < STM_DMA_NUM_DEVS; ++busId) {
        for (stream = 0; stream < STM_DMA_NUM_STREAMS; ++stream) {
            struct StmDmaStreamState *state = dmaGetStreamState(busId, stream);
            if (state->tid == tid) {
                dmaStop(busId, stream);
                count++;
            }
        }
    }

    return count;
}
