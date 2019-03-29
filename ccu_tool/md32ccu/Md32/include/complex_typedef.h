/************************************************************************
 *
 * complex_typedef.h
 *
 ************************************************************************/

/*
 * Copyright (c) 2008-2009 MediaTek Inc. All Rights Reserved.
 * --------------------
 * This software is protected by copyright and the information
 * contained herein is confidential.  The software may not be
 * copied and the information contained herein may not be used or
 * disclosed except with the written permission of MediaTek Inc.
 *
 * (c) Copyright 2001-2006 Analog Devices, Inc.  All rights reserved.
 */

#pragma once
#ifndef __NO_BUILTIN
#pragma system_header /* complex_typedef.h */
#endif

/* Define complex data types (fractional and float) */

#ifndef _COMPLEX_TYPEDEF_H
#define _COMPLEX_TYPEDEF_H

#include <fract_typedef.h>

#ifdef _MISRA_RULES
#pragma diag(push)
#pragma diag(suppress:misra_rule_5_2:"DSPTK header will re-use identfiers")
#pragma diag(suppress:misra_rule_5_3:"DSPTK header will re-use identfiers")
#pragma diag(suppress:misra_rule_5_4:"DSPTK header will re-use identfiers")
#pragma diag(suppress:misra_rule_5_5:"DSPTK header will re-use identfiers")
#pragma diag(suppress:misra_rule_5_6:"DSPTK header will re-use identfiers")
#pragma diag(suppress:misra_rule_5_7:"DSPTK header will re-use identfiers")
#pragma diag(suppress:misra_rule_6_3:"DSPTK header allows use of basic types")
#pragma diag(suppress:misra_rule_18_4:"DSPTK header allows unions")
#pragma diag(suppress:misra_rule_19_4:"DSPTK header allows any substitution")
#pragma diag(suppress:misra_rule_19_7:"DSPTK header allows function macros")
#endif /* _MISRA_RULES */


typedef struct complex_fract16 {
#pragma align 4
    fract16 re;
    fract16 im;
} complex_fract16;

/* Composite type used by builtins */
typedef union composite_complex_fract16 {
    struct complex_fract16 x;
    long raw;
} composite_complex_fract16;

#define CFR16_RE(X) (X).x.re
#define CFR16_IM(X) (X).x.im
#define CFR16_RAW(X) (X).raw


typedef struct complex_fract32 {
    fract32 re;
    fract32 im;
} complex_fract32;

/* Composite type used by builtins */
typedef union composite_complex_fract32 {
    struct complex_fract32  x;
    long long raw;
} composite_complex_fract32;

#define CFR32_RE(X) (X).x.re
#define CFR32_IM(X) (X).x.im
#define CFR32_RAW(X) (X).raw

/* C++ Template class variant declared in complex */
typedef struct complex_float {
    float re;
    float im;
} complex_float;

typedef struct complex_long_double {
    long double re;
    long double im;
} complex_long_double;

#ifdef __DOUBLES_ARE_FLOATS__          /* 32-bit doubles */
  typedef complex_float          complex_double;
#else                                  /* 64-bit doubles */
  typedef complex_long_double    complex_double;
#endif

#ifdef _MISRA_RULES
#pragma diag(pop)
#endif /* _MISRA_RULES */
#endif /* _COMPLEX_TYPEDEF_H */
