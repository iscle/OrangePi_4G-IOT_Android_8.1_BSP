/* ===-- fixunssfdi.c - Implement __fixunssfdi -----------------------------===
 *
 *                     The LLVM Compiler Infrastructure
 *
 * This file is dual licensed under the MIT and the University of Illinois Open
 * Source Licenses. See LICENSE.TXT for details.
 *
 * ===----------------------------------------------------------------------===
 */

#include "int_lib.h"

du_int __aeabi_f2ulz(float a);

/* Support for systems that have hardware floating-point; can set the invalid
 * flag as a side-effect of computation.
 */

du_int
__aeabi_f2ulz(float a)
{
    if (a <= 0.0f) return 0;
    float da = a;
    su_int high = da / 4294967296.f;               /* da / 0x1p32f; */
    su_int low = da - (float)high * 4294967296.f; /* high * 0x1p32f; */
    return ((du_int)high << 32) | low;
}
