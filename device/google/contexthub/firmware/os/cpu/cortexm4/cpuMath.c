#include <cpu/cpuMath.h>

uint32_t cpuMathUint44Div1000ToUint32_slow_path(uint64_t val)
{
    uint64_t mult = 0x4189374BCULL;
    uint32_t multLo = mult;
    uint32_t multHi = mult >> 32;
    uint64_t ret;

    ret = val * multLo;
    ret >>= 32;
    ret += val * multHi;

    return ret >> 12;
}

uint64_t cpuMathU64DivByU16(uint64_t val, uint32_t divBy_ /* 16 bits max*/)
{
    //this is OK here, but not elsewhere
    return U64_DIV_BY_CONST_U16(val, divBy_);
}

static uint64_t __attribute__((naked)) cpuMathUint64TimesUint64Lsr64(uint64_t a, uint64_t b)
{
    asm volatile(
        "push  {r4 - r7}        \n"
        "umull r12, r4, r0, r2  \n"
        "umull r5, r6, r0, r3   \n"
        "umull r7, r12, r1, r2  \n"
        "movs  r2, #0           \n"
        "adds  r5, r4           \n"
        "adcs  r6, r2           \n"
        "adds  r5, r7           \n"
        "adc   r0, r6, r12      \n"
        "umlal r0, r2, r1, r3   \n"
        "movs  r1, r2           \n"
        "pop   {r4 - r7}        \n"
        "bx    lr               \n"
    );

    //we never get here, it is only here to please GCC
    return 0;
}

//this will be specialized for both cases (compiler can use denom being 32-bit long easily)
#define cpuMathRecipAssistedUdiv64_common                  \
    uint64_t try, ret;                                     \
                                                           \
    if (denom <= 1)                                        \
        return num;                                        \
                                                           \
    ret = cpuMathUint64TimesUint64Lsr64(num, denomRecip);  \
    try = (ret + 1) * denom;                               \
    if (try <= num && try >= ret * denom)                  \
        ret++;                                             \
                                                           \
    return ret;

uint64_t cpuMathRecipAssistedUdiv64by64(uint64_t num, uint64_t denom, uint64_t denomRecip)
{
    cpuMathRecipAssistedUdiv64_common
}

uint64_t cpuMathRecipAssistedUdiv64by32(uint64_t num, uint32_t denom, uint64_t denomRecip)
{
    cpuMathRecipAssistedUdiv64_common
}
