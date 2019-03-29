/************************************************************************
 *
 * xcycle_count.h
 *   Generic low level support to measure cycle counts
 *
 * This header file is not meant to be included directly in C/C++
 * programs.  Please include <cycle_count.h> instead.
 ************************************************************************/

/*
 * Copyright (c) 2008-2009 MediaTek Inc. All Rights Reserved.
 * --------------------
 * This software is protected by copyright and the information contained
 * herein is confidential.  The software may not be copied and the information
 * contained herein may not be used or disclosed except with the written
 * permission of MediaTek Inc.
 *
 * (c) Copyright 2004-2007 Analog Devices, Inc.  All rights reserved.
 *
 * $Revision: 1.11 $
 */

#ifndef __XCYCLE_COUNT_DEFINED
#define __XCYCLE_COUNT_DEFINED

#if !defined(_LANGUAGE_ASM)

#pragma once
#ifndef __NO_BUILTIN
#pragma system_header /* xcycle_count.h */
#endif

#include <limits.h>

#ifdef _MISRA_RULES
#pragma diag(push)
#pragma diag(suppress:misra_rule_5_3)
#pragma diag(suppress:misra_rule_6_3)
#pragma diag(suppress:misra_rule_8_5)
#pragma diag(suppress:misra_rule_8_9)
#pragma diag(suppress:misra_rule_16_3)
#pragma diag(suppress:misra_rule_19_1)
#pragma diag(suppress:misra_rule_19_4)
#pragma diag(suppress:misra_rule_19_7)
#pragma diag(suppress:misra_rule_19_10)
#endif /* _MISRA_RULES */

/* Add a trailing semi-colon for legacy purposes if requested */
#if defined( __USE_CYCLE_MACRO_REL45__ )
  #define __TRAILING_SC__ ;
#else
  #define __TRAILING_SC__
#endif

/* Define type used for cycle counting */

#if defined(__FCORE__)
typedef  volatile unsigned long long  _cycle_t;
#define  _CYCLES_T_MAX                ULLONG_MAX
#define  _PRINT_CYCLES(_STRG, _DAT)   printf("%s%llu\n", (_STRG), (_DAT)) __TRAILING_SC__

#elif defined(__MD32__)
typedef  volatile unsigned long long  _cycle_t;
#define  _CYCLES_T_MAX                ULLONG_MAX
#define  _PRINT_CYCLES(_STRG, _DAT)

#endif


/* The following low level macros are defined, operating on type _cycle_t

      _START_CYCLE_COUNT( S )    - Set S to the current value
                                   in the cycle count register(s)

      _STOP_CYCLE_COUNT( X, S )  - Return in S the elapsed cycle count
                                   since start counting
                                   X = current count
                                       - S (=start count)
                                       - measurement overhead
 */


/* Include platform specific implementation */

#if defined(__FCORE__)

#if defined(__DSPTK_LEGACY)
#include <legacy/cycle_count_fcore.h>
#else
#include <cycle_count_fcore.h>
#endif

#elif defined(__MD32__)
#include <cycle_count_md32.h>

#else
#error  ARCHITECTURE NOT SUPPORTED
#endif

/* Private Data from here (do not remove because it is used by time.h) */

extern volatile int _Processor_cycles_per_sec;

#ifdef _MISRA_RULES
#pragma diag(pop)
#endif /* _MISRA_RULES */

#else	/* _LANGUAGE_ASM */

#if defined(__FCORE__)

/* Supply an Assembly Language declaration of _Processor_cycles_per_sec */

.extern  __Processor_cycles_per_sec;

#endif

#endif	/* _LANGUAGE_ASM */
#endif	/* __XCYCLE_COUNT_DEFINED */

