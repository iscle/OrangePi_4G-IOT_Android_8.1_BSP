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

#include <stdint.h>
#include <stdbool.h>
#include <string.h>
#include <nanohub/rsa.h>


static bool biModIterative(uint32_t *num, const uint32_t *denum, uint32_t *tmp, uint32_t *state1, uint32_t *state2, uint32_t step)
//num %= denum where num is RSA_LEN * 2 and denum is RSA_LEN and tmp is RSA_LEN + limb_sz
//will need to be called till it returns true (up to RSA_LEN * 2 + 2 times)
{
    uint32_t bitsh = *state1, limbsh = *state2;
    bool ret = false;
    int64_t t;
    int32_t i;

    //first step is init
    if (!step) {
        //initially set it up left shifted as far as possible
        memcpy(tmp + 1, denum, RSA_BYTES);
        tmp[0] = 0;
        bitsh = 32;
        limbsh = RSA_LIMBS - 1;
        goto out;
    }

    //second is shifting denum
    if (step == 1) {
        while (!(tmp[RSA_LIMBS] & 0x80000000)) {
            for (i = RSA_LIMBS; i > 0; i--) {
                tmp[i] <<= 1;
                if (tmp[i - 1] & 0x80000000)
                    tmp[i]++;
            }
            //no need to adjust tmp[0] as it is still zero
            bitsh++;
        }
        goto out;
    }

    //all future steps do the division

    //check if we should subtract (uses less space than subtracting and unroling it later)
    for (i = RSA_LIMBS; i >= 0; i--) {
        if (num[limbsh + i] < tmp[i])
            goto dont_subtract;
        if (num[limbsh + i] > tmp[i])
            break;
    }

    //subtract
    t = 0;
    for (i = 0; i <= RSA_LIMBS; i++) {
        t += (uint64_t)num[limbsh + i];
        t -= (uint64_t)tmp[i];
        num[limbsh + i] = t;
        t >>= 32;
    }

    //carry the subtraction's carry to the end
    for (i = RSA_LIMBS + limbsh + 1; i < RSA_LIMBS * 2; i++) {
        t += (uint64_t)num[i];
        num[i] = t;
        t >>= 32;
    }

dont_subtract:
    //handle bitshifts/refills
    if (!bitsh) {                          // tmp = denum << 32
        if (!limbsh) {
            ret = true;
            goto out;
        }

        memcpy(tmp + 1, denum, RSA_BYTES);
        tmp[0] = 0;
        bitsh = 32;
        limbsh--;
    }
    else {                                 // tmp >>= 1
        for (i = 0; i < RSA_LIMBS; i++) {
            tmp[i] >>= 1;
            if (tmp[i + 1] & 1)
                tmp[i] += 0x80000000;
        }
        tmp[i] >>= 1;
        bitsh--;
    }


out:
    *state1 = bitsh;
    *state2 = limbsh;
    return ret;
}

static void biMulIterative(uint32_t *ret, const uint32_t *a, const uint32_t *b, uint32_t step) //ret = a * b, call with step = [0..RSA_LIMBS)
{
    uint32_t j, c;
    uint64_t r;

    //zero the result on first call
    if (!step)
        memset(ret, 0, RSA_BYTES * 2);

    //produce a partial sum & add it in
    c = 0;
    for (j = 0; j < RSA_LIMBS; j++) {
        r = (uint64_t)a[step] * b[j] + c + ret[step + j];
        ret[step + j] = r;
        c = r >> 32;
    }

    //carry the carry to the end
    for (j = step + RSA_LIMBS; j < RSA_LIMBS * 2; j++) {
        r = (uint64_t)ret[j] + c;
        ret[j] = r;
        c = r >> 32;
    }
}

/*
 * Piecewise RSA:
 * normal RSA public op with 65537 exponent does 34 operations. 17 muls and 17 mods, as follows:
 * 16x {mul, mod} to calculate a ^ 65536 mod c
 * 1x {mul, mod} to calculate a ^ 65537 mod c
 * we break up each mul and mod itself into more steps. mul needs RSA_LIMBS steps, and mod needs up to RSA_LEN * 2 + 2 steps
 * so if we allocate RSA_LEN * 3 step values to mod, each mul-mod pair will use <= RSA_LEN * 4 step values
 * and the whole opetaion will need <= RSA_LEN * 4 * 34 step values, which fits into a uint32. cool. In fact
 * some values will be skipped, but this makes life easier, really. Call this func with *stepP = 0, and keep calling till
 * output stepP is zero. We'll call each of the RSA_LEN * 4 pieces a gigastep, and have 17 of them as seen above. Each
 * will be logically separated into 4 megasteps. First will contain the MUL, last 3 the MOD and maybe the memcpy.
 * In the first 16 gigasteps, the very last step of the gigastep will be used for the memcpy call.
 *
 * The initial non-iterative RSA logic looks as follows, shown here for clarity:
 *
 *   memcpy(state->tmpB, a, RSA_BYTES);
 *   for (i = 0; i < 16; i++) {
 *       biMul(state->tmpA, state->tmpB, state->tmpB);
 *       biMod(state->tmpA, c, state->tmpB);
 *       memcpy(state->tmpB, state->tmpA, RSA_BYTES);
 *   }
 *
 *   //calculate a ^ 65537 mod c into state->tmpA [ at this point this means do state->tmpA = (state->tmpB * a) % c ]
 *   biMul(state->tmpA, state->tmpB, a);
 *   biMod(state->tmpA, c, state->tmpB);
 *
 *   //return result
 *   return state->tmpA;
 *
 */

const uint32_t* rsaPubOpIterative(struct RsaState* state, const uint32_t *a, const uint32_t *c, uint32_t *state1, uint32_t *state2, uint32_t *stepP)
{
    uint32_t step = *stepP, gigastep, gigastepBase, gigastepSubstep, megaSubstep;

    //step 0: copy a -> tmpB
    if (!step) {
        memcpy(state->tmpB, a, RSA_BYTES);
        step = 1;
    }
    else { //subsequent steps: do real work


        gigastep = (step - 1) / (RSA_LEN * 4);
        gigastepSubstep = (step - 1) % (RSA_LEN * 4);
        gigastepBase = gigastep * (RSA_LEN * 4);
        megaSubstep = gigastepSubstep / RSA_LEN;

        if (!megaSubstep) { // first megastep of the gigastep - MUL
            biMulIterative(state->tmpA, state->tmpB, gigastep == 16 ? a : state->tmpB, gigastepSubstep);
            if (gigastepSubstep == RSA_LIMBS - 1) //MUL is done - do mod next
                step = gigastepBase + RSA_LEN + 1;
            else                                  //More of MUL is left to do
                step++;
        }
        else if (gigastepSubstep != RSA_LEN * 4 - 1){   // second part of gigastep - MOD
            if (biModIterative(state->tmpA, c, state->tmpB, state1, state2, gigastepSubstep - RSA_LEN)) { //MOD is done
                if (gigastep == 16) // we're done
                    step = 0;
                else              // last part of the gigastep is a copy
                    step = gigastepBase + RSA_LEN * 4 - 1 + 1;
            }
            else
                step++;
        }
        else {   //last part - memcpy
            memcpy(state->tmpB, state->tmpA, RSA_BYTES);
            step++;
        }
    }

    *stepP = step;
    return state->tmpA;
}

#if defined(RSA_SUPPORT_PRIV_OP_LOWRAM) || defined (RSA_SUPPORT_PRIV_OP_BIGRAM)
#include <stdio.h>
const uint32_t* rsaPubOp(struct RsaState* state, const uint32_t *a, const uint32_t *c)
{
    const uint32_t *ret;
    uint32_t state1 = 0, state2 = 0, step = 0, ns = 0;

    do {
        ret = rsaPubOpIterative(state, a, c, &state1, &state2, &step);
        ns++;
    } while(step);

fprintf(stderr, "steps: %u\n", ns);

    return ret;
}

static void biMod(uint32_t *num, const uint32_t *denum, uint32_t *tmp)
{
    uint32_t state1 = 0, state2 = 0, step;

    for (step = 0; !biModIterative(num, denum, tmp, &state1, &state2, step); step++);
}

static void biMul(uint32_t *ret, const uint32_t *a, const uint32_t *b)
{
    uint32_t step;

    for (step = 0; step < RSA_LIMBS; step++)
        biMulIterative(ret, a, b, step);
}

const uint32_t* rsaPrivOp(struct RsaState* state, const uint32_t *a, const uint32_t *b, const uint32_t *c)
{
    uint32_t i;

    memcpy(state->tmpC, a, RSA_BYTES);  //tC will hold our powers of a

    memset(state->tmpA, 0, RSA_BYTES * 2); //tA will hold result
    state->tmpA[0] = 1;

    for (i = 0; i < RSA_LEN; i++) {
        //if the bit is set, multiply the current power of A into result
        if (b[i / 32] & (1 << (i % 32))) {
            memcpy(state->tmpB, state->tmpA, RSA_BYTES);
            biMul(state->tmpA, state->tmpB, state->tmpC);
            biMod(state->tmpA, c, state->tmpB);
        }

        //calculate the next power of a and modulus it
#if defined(RSA_SUPPORT_PRIV_OP_LOWRAM)
        memcpy(state->tmpB, state->tmpA, RSA_BYTES); //save tA
        biMul(state->tmpA, state->tmpC, state->tmpC);
        biMod(state->tmpA, c, state->tmpC);
        memcpy(state->tmpC, state->tmpA, RSA_BYTES);
        memcpy(state->tmpA, state->tmpB, RSA_BYTES); //restore tA
#elif defined (RSA_SUPPORT_PRIV_OP_BIGRAM)
        memcpy(state->tmpB, state->tmpC, RSA_BYTES);
        biMul(state->tmpC, state->tmpB, state->tmpB);
        biMod(state->tmpC, c, state->tmpB);
#endif
    }

    return state->tmpA;
}
#endif








