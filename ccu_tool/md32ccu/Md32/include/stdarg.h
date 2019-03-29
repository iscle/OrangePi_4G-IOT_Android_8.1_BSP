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

#ifndef _STDARG_H
#define _STDARG_H

#if defined(__MD32__)
#include <md32_bril.h>
#endif

#include <yvals.h>
#include <stdint.h>

#if __MD32__
#define va_start(ap, A) \
	(void)((ap) = (va_list)__builtin_va_start(&(A), _Bnd(A, _AUPBND)))
#define _Bnd(X, bnd)	(sizeof (X) + (bnd) & ~(bnd))
#define va_arg(va,TT) (*(TT*)(va = (TT*)((uintptr_t)va - _Bnd(TT, _AUPBND))))
#endif

#define va_copy(d,s) (d) = (s);

#define va_end(va) (void)0

#endif /* stdarg.h */
