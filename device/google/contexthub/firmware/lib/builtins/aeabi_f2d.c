//===-- lib/extendsfdf2.c - single -> double conversion -----------*- C -*-===//
//
//                     The LLVM Compiler Infrastructure
//
// This file is dual licensed under the MIT and the University of Illinois Open
// Source Licenses. See LICENSE.TXT for details.
//
//===----------------------------------------------------------------------===//
//

#define SRC_SINGLE
#define DST_DOUBLE
#include "fp_extend_impl.inc"

double __aeabi_f2d(float a);

double __aeabi_f2d(float a) {
    return __extendXfYf2__(a);
}
