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

#ifndef _STDDEF_H
#define _STDDEF_H

#if defined(__MD32__)
#include <md32_bril.h>

#ifndef _YVALS
#include <yvals.h>
#endif /* _YVALS */

#if !defined(__SIZE_T_DEFINED) && !defined(_SIZE_T)
  #define __SIZE_T_DEFINED
  #define _SIZET
  typedef unsigned int  size_t;
#endif

#ifndef _WCHART
 #define _WCHART
typedef _Wchart wchar_t;
#endif /* _WCHART */


#if !defined(_PTRDIFF_T) && !defined(_PTRDIFFT)
#define _PTRDIFF_T
#define _PTRDIFFT
#define _STD_USING_PTRDIFF_T
typedef _Ptrdifft ptrdiff_t;
#endif /* !defined(_PTRDIFF_T) && !defined(_PTRDIFFT) */

#ifndef NULL
#define NULL   _NULL
#endif /* NULL */

#else

typedef signed int    ptrdiff_t;
typedef unsigned int  size_t;
typedef int           wchar_t;
#define NULL 0

#endif /* __MD32__ */

#define offsetof(s,m) (size_t)(&(((s *)0)->m))

#endif /* stddef.h */
