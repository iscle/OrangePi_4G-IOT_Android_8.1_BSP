/* ===-- int_lib.h - configuration header for compiler-rt  -----------------===
 *
 *                     The LLVM Compiler Infrastructure
 *
 * This file is dual licensed under the MIT and the University of Illinois Open
 * Source Licenses. See LICENSE.TXT for details.
 *
 * ===----------------------------------------------------------------------===
 *
 * This file is a configuration header for compiler-rt.
 * This file is not part of the interface of this library.
 *
 * ===----------------------------------------------------------------------===
 */

#ifndef INT_LIB_H
#define INT_LIB_H

#define FLT_MANT_DIG    __FLT_MANT_DIG__
#define CHAR_BIT        8

typedef unsigned su_int;
typedef int si_int;

typedef unsigned long long du_int;
typedef long long di_int;

typedef union
{
    di_int all;
    struct
    {
        su_int low;
        si_int high;
    } s;
} dwords;

typedef union
{
    du_int all;
    struct
    {
        su_int low;
        su_int high;
    } s;
} udwords;

typedef union
{
    su_int u;
    float f;
} float_bits;

/* Assumption: Signed integral is 2's complement. */
/* Assumption: Right shift of signed negative is arithmetic shift. */

di_int __divdi3(di_int a, di_int b);
di_int __moddi3(di_int a, di_int b);
du_int __umoddi3(du_int a, du_int b);
di_int __divmoddi4(di_int a, di_int b, di_int* rem);
du_int __udivmoddi4(du_int a, du_int b, du_int* rem);

#endif /* INT_LIB_H */
