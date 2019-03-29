#ifndef _CPU_MATH_H_
#define _CPU_MATH_H_

#include <stdint.h>

uint32_t cpuMathUint44Div1000ToUint32_slow_path(uint64_t val);

static inline uint32_t cpuMathUint44Div1000ToUint32(uint64_t val)
{
    if (val >> 32)
        return cpuMathUint44Div1000ToUint32_slow_path(val);
    else
        return (uint32_t)val / 1000;
}

uint64_t cpuMathU64DivByU16(uint64_t val, uint32_t divBy /* 16 bits max*/);

//DO NOT USE ON NON_COMPILE-TIME-CONSTANT VALUES OF u16, use cpuMathU64DivByU16()

#define U64_DIV_BY_CONST_U16(u64, u16)                        \
    ({                                                        \
        const uint16_t divBy = u16;                           \
        const uint64_t _num = u64;                            \
        const uint32_t numHi = _num >> 32, numLo = _num;      \
        uint32_t t1, t2, t3, t4, t5;                          \
                                                              \
        t1 = numHi / divBy;                                   \
        t2 = numHi % divBy;                                   \
        t2 <<= 16;                                            \
        t2 += numLo >> 16;                                    \
        t3 = t2 / divBy;                                      \
        t4 = t2 % divBy;                                      \
        t4 <<= 16;                                            \
        t4 += numLo & 0xFFFF;                                 \
        t5 = t4 / divBy;                                      \
                                                              \
        (((uint64_t)t1) << 32) + (((uint64_t)t3) << 16) + t5; \
    })

//correctly handles 0, 1, powers of 2, and all else to calculate "(1 << 64) / val"
//do not even think of using this on non-compile-time-constant values!
#define U64_RECIPROCAL_CALCULATE(val)  ((val) & ((val) - 1)) ? (0xffffffffffffffffull / (val)) : (((val) <= 1) ? 0xffffffffffffffffull : (0x8000000000000000ull / ((val) >> 1)))

uint64_t cpuMathRecipAssistedUdiv64by64(uint64_t num, uint64_t denom, uint64_t denomRecip);
uint64_t cpuMathRecipAssistedUdiv64by32(uint64_t num, uint32_t denom, uint64_t denomRecip);

#define U64_DIV_BY_U64_CONSTANT(val, constantVal)  cpuMathRecipAssistedUdiv64by64((val), (constantVal), U64_RECIPROCAL_CALCULATE(constantVal))
#define I64_DIV_BY_I64_CONSTANT(val, constantVal)                                                  \
    ({                                                                                             \
        char neg = ((uint32_t)((val) >> 32) ^ (uint32_t)(((uint64_t)(constantVal)) >> 32)) >> 31;  \
        uint64_t valP = (val < 0) ? -val : val;                                                    \
        const uint64_t conP = (constantVal < 0) ? -constantVal : constantVal;                      \
        uint64_t ret = cpuMathRecipAssistedUdiv64by64(valP, conP, U64_RECIPROCAL_CALCULATE(conP)); \
        if (neg)                                                                                   \
            ret =-ret;                                                                             \
        ret;                                                                                       \
    })


#endif

