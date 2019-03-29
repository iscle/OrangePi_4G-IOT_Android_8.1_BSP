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

#ifndef _NANOHUB_SHA2_H_
#define _NANOHUB_SHA2_H_

//this is neither the fastest nor the smallest. but it is simple and matches the spec. cool.

#include <stdint.h>

#define SHA2_BLOCK_SIZE         64U //in bytes
#define SHA2_WORDS_STATE_SIZE   64U //in words

#define SHA2_HASH_SIZE          32U //in bytes
#define SHA2_HASH_WORDS         8U  //in words

struct Sha2state {
    uint32_t h[8];
    uint64_t msgLen;
    union {
        uint32_t w[SHA2_WORDS_STATE_SIZE];
        uint8_t b[SHA2_BLOCK_SIZE];
    };
    uint8_t bufBytesUsed;
};

void sha2init(struct Sha2state *state);
void sha2processBytes(struct Sha2state *state, const void *bytes, uint32_t numBytes);
const uint32_t* sha2finish(struct Sha2state *state); //returned hash pointer is only valid as long as "state" is!





#endif // _NANOHUB_SHA2_H_

