#ifndef ERRATA_H
#define ERRATA_H

#define SAFE_READ(x) \
    __asm__ __volatile__( \
    "nop\n" \
    "nop\n" \
    "nop\n" \
    "nop\n" \
    "nop\n" \
    "nop\n" \
    "nop\n" \
    "nop\n" \
    "nop\n" \
    ); \
    x; \
    __asm__ __volatile__( \
    "nop\n" \
    );

#define SAFE_HEAD \
    { \
    __asm__ __volatile__( \
    "nop\n" \
    "nop\n" \
    "nop\n" \
    "nop\n" \
    "nop\n" \
    "nop\n" \
    "nop\n" \
    "nop\n" \
    "nop\n" \
    ); \

#define SAFE_TAIL \
    __asm__ __volatile__( \
    "nop\n" \
    ); \
    }

#endif
