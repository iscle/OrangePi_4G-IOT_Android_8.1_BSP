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

#ifndef __NANOHUBCOMMAND_H
#define __NANOHUBCOMMAND_H

#include <stdint.h>

#define NANOHUB_FAST_DONT_ACK       0xFFFFFFFE
#define NANOHUB_FAST_UNHANDLED_ACK  0xFFFFFFFF

struct NanohubCommand {
    uint32_t reason;
    uint32_t (*fastHandler)(void *, uint8_t, void *, uint64_t);
    uint32_t (*handler)(void *, uint8_t, void *, uint64_t);
    uint8_t minDataLen;
    uint8_t maxDataLen;
};

void nanohubInitCommand(void);
void nanohubPrefetchTx(uint32_t interrupt, uint32_t wakeup, uint32_t nonwakeup);
const struct NanohubCommand *nanohubFindCommand(uint32_t packetReason);

struct NanohubHalCommand {
    uint8_t msg;
    void (*handler)(void *, uint8_t);
};

const struct NanohubHalCommand *nanohubHalFindCommand(uint8_t msg);
uint64_t hostGetTime(void);

#endif /* __NANOHUBCOMMAND_H */
