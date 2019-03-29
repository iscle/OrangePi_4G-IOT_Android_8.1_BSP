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

#include "floatRt.h"
#include <stdbool.h>


#include <stdio.h>

/*
 * FLOAT:
 *  seeeeeee emmmmmmm mmmmmmmm mmmmmmmm
 *
 *  s = negative
 *  e = exponent
 *  m = mantissa (with one bit removed)
 *
 *   if (e == 0xFF)
 *      if (f)  val = inf
 *      else    val = nan
 *      goto valDone
 *   else if (e == 0x00)
 *      useLeadingOne = 0
 *      e = -126
 *   else
 *      e = e - 127
 *      useLeadingOne = 1
 *
 *   val = ((useLeadingOne << 24) + m) / (2 ^ 23)
 *   val *= 2 ^ e
 *
 * valDone:
 *
 *   if (s)
 *      val = -val;
 */

#define BIT_SIGN        0x80000000UL
#define MANTISSA_BITS   23
#define EXP_SHIFT       MANTISSA_BITS
#define EXP_ADJUST      127


#ifdef USE_NANOHUB_FLOAT_RUNTIME

uint64_t floatToUint64(float f)
{
    uint32_t e, word = *(const uint32_t*)&f;
    uint64_t ret;


    //all negatives become zero
    if (word & BIT_SIGN)
        return 0;

    //all values with exponent < 0 are less than one and thus become zero
    if (word < (EXP_ADJUST << EXP_SHIFT))
        return 0;

    //standard does not say what happens to NaNs, infs & other too-large values, we return a large value as an approximation (though a zero would be equally valid)
    if (word >= (EXP_ADJUST + 64) << EXP_SHIFT)
        return 0xFFFFFFFFFFFFFFFFULL;

    //get mantissa and the implied leading one
    ret = (word & ((1 << MANTISSA_BITS) - 1)) | (1 << MANTISSA_BITS);
    e = ((word >> EXP_SHIFT) - EXP_ADJUST);

    //shift it by the exp
    if (e < MANTISSA_BITS)
        ret >>= MANTISSA_BITS - e;
    else
        ret <<= e - MANTISSA_BITS;

    return ret;
}

int64_t floatToInt64(float f)
{
    uint32_t e, word = *(const uint32_t*)&f;
    bool neg = (word & BIT_SIGN);
    uint64_t ret;


    //all negatives become positive for now
    word &=~ BIT_SIGN;

    //all values with exponent < 0 are less than one and thus become zero
    if (word < (EXP_ADJUST << EXP_SHIFT))
        return 0;

    //standard does not say what happens to NaNs, infs & other too-large values, we return a large value as an approximation (though a zero would be equally valid)
    if (word >= (EXP_ADJUST + 63) << EXP_SHIFT)
        ret = 0x7FFFFFFFFFFFFFFFULL;

    else {
        //get mantissa and the implied leading one
        ret = (word & ((1 << MANTISSA_BITS) - 1)) | (1 << MANTISSA_BITS);
        e = ((word >> EXP_SHIFT) - EXP_ADJUST);

        //shift it by the exp
        if (e < MANTISSA_BITS)
            ret >>= MANTISSA_BITS - e;
        else
            ret <<= e - MANTISSA_BITS;
    }

    if (neg)
        ret = -ret;

    return ret;
}

float floatFromUint64(uint64_t v)
{
    uint32_t hi = v >> 32, lo = v;

    if (!hi) //this is very fast for cases where we fit into a uint32_t
        return(float)lo;
    else {
        return ((float)hi) * 4294967296.0f + (float)lo;
    }
}

float floatFromInt64(int64_t v)
{
    uint32_t hi = ((uint64_t)v) >> 32, lo = v;

    if ((hi == 0x00000000 && !(lo >> 31)) || (hi == 0xffffffff && (lo >> 31))) //this complex test is a lot faster then the simpler ((v >> 33) == -1 || (v >> 33) == 0)
        return (float)(int32_t)lo;
    else if (hi >> 31)  //the case of 0x8000000000000000 is handled here, as negated it remains the same
        return -floatFromUint64(-v);
    else
        return floatFromUint64(v);
}





#endif // USE_NANOHUB_FLOAT_RUNTIME
