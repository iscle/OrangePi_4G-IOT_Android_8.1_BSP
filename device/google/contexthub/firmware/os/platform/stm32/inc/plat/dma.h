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

#ifndef _DMA_H
#define _DMA_H

#include <stdint.h>

typedef void (*DmaCallbackF)(void *cookie, uint16_t bytesLeft, int err);

struct dmaMode {
    enum {
        DMA_SIZE_8_BITS = 0,
        DMA_SIZE_16_BITS = 1,
        DMA_SIZE_32_BITS = 2,
    } psize, msize;

    enum {
        DMA_BURST_SINGLE = 0,
        DMA_BURST_INCR4 = 1,
        DMA_BURST_INCR8 = 2,
        DMA_BURST_INCR16 = 3,
    } pburst, mburst;

    enum {
        DMA_PRIORITY_LOW = 0,
        DMA_PRIORITY_MEDIUM = 1,
        DMA_PRIORITY_HIGH = 2,
        DMA_PRIORITY_VERY_HIGH = 3,
    } priority;

    enum {
        DMA_DIRECTION_PERIPH_TO_MEM = 0,
        DMA_DIRECTION_MEM_TO_PERIPH = 1,
    } direction;

    bool minc;

    uint32_t periphAddr;
    uint8_t channel;
};

int dmaStart(uint8_t busId, uint8_t stream, const void *buf, uint16_t size,
        const struct dmaMode *mode, DmaCallbackF callback, void *cookie);
uint16_t dmaBytesLeft(uint8_t busId, uint8_t stream);
void dmaStop(uint8_t busId, uint8_t stream);
const enum IRQn dmaIrq(uint8_t busId, uint8_t stream);
int dmaStopAll(uint32_t tid);

#endif /* _DMA_H */
