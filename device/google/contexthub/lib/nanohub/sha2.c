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

#include <string.h>
#include <nanohub/sha2.h>


void sha2init(struct Sha2state *state)
{
    state->h[0] = 0x6a09e667;
    state->h[1] = 0xbb67ae85;
    state->h[2] = 0x3c6ef372;
    state->h[3] = 0xa54ff53a;
    state->h[4] = 0x510e527f;
    state->h[5] = 0x9b05688c;
    state->h[6] = 0x1f83d9ab;
    state->h[7] = 0x5be0cd19;
    state->msgLen = 0;
    state->bufBytesUsed = 0;
}

#ifdef ARM

    #define STRINFIGY2(b) #b
    #define STRINGIFY(b) STRINFIGY2(b)
    #define ror(v, b) ({uint32_t ret; if (b) asm("ror %0, #" STRINGIFY(b) :"=r"(ret):"0"(v)); else ret = v; ret;})

#else

    inline static uint32_t ror(uint32_t val, uint32_t by)
    {
        if (!by)
            return val;

        val = (val >> by) | (val << (32 - by));

        return val;
    }

#endif


static void sha2processBlock(struct Sha2state *state)
{
    static const uint32_t k[] = {
        0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
        0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
        0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
        0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
        0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
        0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
        0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
        0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2,
    };
    uint32_t i, a, b, c, d, e, f, g, h;

    //byteswap the input (if we're on a little endian cpu, as we are)
    for (i = 0; i < SHA2_BLOCK_SIZE / sizeof(uint32_t); i++)
        state->w[i] = __builtin_bswap32(state->w[i]);

    //expand input
    for (;i < SHA2_WORDS_STATE_SIZE; i++) {
        uint32_t s0 = ror(state->w[i-15], 7) ^ ror(state->w[i-15], 18) ^ (state->w[i-15] >> 3);
        uint32_t s1 = ror(state->w[i-2], 17) ^ ror(state->w[i-2], 19) ^ (state->w[i-2] >> 10);
        state->w[i] = state->w[i - 16] + s0 + state->w[i - 7] + s1;
    }

    //init working variables
    a = state->h[0];
    b = state->h[1];
    c = state->h[2];
    d = state->h[3];
    e = state->h[4];
    f = state->h[5];
    g = state->h[6];
    h = state->h[7];

    //64 rounds
    for (i = 0; i < 64; i++) {
        uint32_t s1 = ror(e, 6) ^ ror(e, 11) ^ ror(e, 25);
        uint32_t ch = (e & f) ^ ((~e) & g);
        uint32_t temp1 = h + s1 + ch + k[i] + state->w[i];
        uint32_t s0 = ror(a, 2) ^ ror(a, 13) ^ ror(a, 22);
        uint32_t maj = (a & b) ^ (a & c) ^ (b & c);
        uint32_t temp2 = s0 + maj;

        h = g;
        g = f;
        f = e;
        e = d + temp1;
        d = c;
        c = b;
        b = a;
        a = temp1 + temp2;
    }

    //put result back into state
    state->h[0] += a;
    state->h[1] += b;
    state->h[2] += c;
    state->h[3] += d;
    state->h[4] += e;
    state->h[5] += f;
    state->h[6] += g;
    state->h[7] += h;
}

void sha2processBytes(struct Sha2state *state, const void *bytes, uint32_t numBytes)
{
    const uint8_t *inBytes = (const uint8_t*)bytes;

    state->msgLen += numBytes;
    while (numBytes) {
        uint32_t bytesToCopy;

        //step 1: copy data into state if there is space & there is data
        bytesToCopy = numBytes;
        if (bytesToCopy > SHA2_BLOCK_SIZE - state->bufBytesUsed)
            bytesToCopy = SHA2_BLOCK_SIZE - state->bufBytesUsed;
        memcpy(state->b + state->bufBytesUsed, inBytes, bytesToCopy);
        inBytes += bytesToCopy;
        numBytes -= bytesToCopy;
        state->bufBytesUsed += bytesToCopy;

        //step 2: if there is a full block, process it
        if (state->bufBytesUsed == SHA2_BLOCK_SIZE) {
            sha2processBlock(state);
            state->bufBytesUsed = 0;
        }
    }
}

const uint32_t* sha2finish(struct Sha2state *state)
{
    uint8_t appendend = 0x80;
    uint64_t dataLenInBits = state->msgLen * 8;
    uint32_t i;

    //append the one
    sha2processBytes(state, &appendend, 1);

    //append the zeroes
    appendend = 0;
    while (state->bufBytesUsed != 56)
        sha2processBytes(state, &appendend, 1);

    //append the length in bits (we can safely write into state since we're sure where to write to (we're definitely 56-bytes into a block)
    for (i = 0; i < 8; i++, dataLenInBits >>= 8)
        state->b[63 - i] = dataLenInBits;

    //process last block
    sha2processBlock(state);

    //return pointer to hash
    return state->h;
}






