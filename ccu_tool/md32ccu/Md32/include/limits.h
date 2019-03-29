/*
** Copyright (c) 2011 MediaTek Inc. All Rights Reserved.
** --------------------
** This software is protected by copyright and the information contained
** herein is confidential.  The software may not be copied and the information
** contained herein may no be used or disclosed except with the written
** permission of MediaTek Inc.
**
** float.h
*/

#ifndef _LIMITS_H
#define _LIMITS_H

#if defined(__MD32__)
#include <md32_bril.h>
#endif

#define CHAR_BIT        8

#define SCHAR_MIN       (-128)
#define SCHAR_MAX       127
#define UCHAR_MAX       255

#define CHAR_MIN        SCHAR_MIN
#define CHAR_MAX        SCHAR_MAX

#define MB_LEN_MAX      1

#define SHRT_MIN        (-32767-1)
#define SHRT_MAX        32767
#define USHRT_MAX       65535

#define INT_MIN         (-2147483647-1)
#define INT_MAX         2147483647
#define UINT_MAX        4294967295U

#define LONG_MIN        INT_MIN
#define LONG_MAX        INT_MAX
#define ULONG_MAX       UINT_MAX

#define LLONG_MIN       (-9223372036854775807LL - 1LL)
#define LLONG_MAX       9223372036854775807LL
#define ULLONG_MAX      18446744073709551615ULL

#endif /* limits.h */
