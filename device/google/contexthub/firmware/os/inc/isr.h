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

#ifndef __ISR_H
#define __ISR_H

#include <stdbool.h>
#include <stdint.h>

#include <cpu.h>
#include <list.h>
#include <util.h>
#include <seos.h>

struct ChainedInterrupt {
    link_t isrs;

    void (*const enable)(struct ChainedInterrupt *);
    void (*const disable)(struct ChainedInterrupt *);
};

struct ChainedIsr {
    link_t node;

    uint32_t maxLatencyNs;

    bool (*func)(struct ChainedIsr *);
    uint16_t tid;
};

static inline void chainIsr(struct ChainedInterrupt *interrupt, struct ChainedIsr *isr)
{
    interrupt->disable(interrupt);
    isr->tid = osGetCurrentTid();
    list_add_tail(&interrupt->isrs, &isr->node);
    interrupt->enable(interrupt);
}

static inline void unchainIsr(struct ChainedInterrupt *interrupt, struct ChainedIsr *isr)
{
    interrupt->disable(interrupt);
    isr->tid = 0;
    list_delete(&isr->node);
    if (!list_is_empty(&interrupt->isrs))
        interrupt->enable(interrupt);
}

static inline bool dispatchIsr(struct ChainedInterrupt *interrupt)
{
    struct link_t *cur, *tmp;
    bool handled = false;
    uint16_t oldTid = osGetCurrentTid();

    list_iterate(&interrupt->isrs, cur, tmp) {
        struct ChainedIsr *curIsr = container_of(cur, struct ChainedIsr, node);
        osSetCurrentTid(curIsr->tid);
        handled = curIsr->func(curIsr);
        if (handled)
            break;
    }
    osSetCurrentTid(oldTid);

    return handled;
}

static inline int unchainIsrAll(struct ChainedInterrupt *interrupt, uint32_t tid)
{
    int count = 0;
    struct link_t *cur, *tmp;

    list_iterate(&interrupt->isrs, cur, tmp) {
        struct ChainedIsr *curIsr = container_of(cur, struct ChainedIsr, node);
        if (curIsr->tid == tid) {
            unchainIsr(interrupt, curIsr);
            count++;
        }
    }

    return count;
}

static inline uint32_t maxLatencyIsr(struct ChainedInterrupt *interrupt)
{
    struct link_t *cur, *tmp;
    uint32_t latency = 0;

    list_iterate(&interrupt->isrs, cur, tmp) {
        struct ChainedIsr *curIsr = container_of(cur, struct ChainedIsr, node);
        if (!latency || (curIsr->maxLatencyNs && curIsr->maxLatencyNs < latency))
            latency = curIsr->maxLatencyNs;
    }

    return latency;
}

#endif /* __ISR_H */
