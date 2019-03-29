/************************************************************************
 *
 * xwcstod.h internal header
 *
 ************************************************************************/

/*
 * Copyright (c) 2009 MediaTek Inc. All Rights Reserved.
 * --------------------
 * This software is protected by copyright and the information 
 * contained herein is confidential.  The software may not be 
 * copied and the information contained herein may not be used or 
 * disclosed except with the written permission of MediaTek Inc.
 */
 
#ifndef _WCSTOD
#define _WCSTOD

#ifdef __DSPTK_LEGACY
#include <legacy/predef.h>
#endif


#ifdef _MISRA_RULES
#pragma diag(push)
#pragma diag(suppress:misra_rule_6_3)
#pragma diag(suppress:misra_rule_19_7)
#endif /* _MISRA_RULES */

#if !defined(_DSPTK_LIBIO)
  #if defined(__cplusplus) && !defined(_NO_CPP_INLINES)
		/* INLINES, FOR C++ */
_C_LIB_DECL
#pragma always_inline
inline double wcstod(const wchar_t *_Restrict _Str,
	wchar_t **_Restrict _Endptr)  {
#if defined(__DOUBLES_ARE_FLOATS__)
        return (_WStof(_Str, _Endptr, 0));
#else
        return (_WStold(_Str, _Endptr, 0));
#endif
	}

#pragma always_inline
inline unsigned long wcstoul(const wchar_t *_Restrict _Str,
	wchar_t **_Restrict _Endptr, int _Base)
	{return (_WStoul(_Str, _Endptr, _Base));
	}
_END_C_LIB_DECL

  #else /* defined(__cplusplus) && !defined(_NO_CPP_INLINES) */
		/* MACROS AND DECLARATIONS, FOR C */
_C_LIB_DECL
double wcstod(const wchar_t *_s, wchar_t **_endptr);
unsigned long wcstoul(const wchar_t *_s, wchar_t **_endptr, int _base);
_END_C_LIB_DECL

#if defined(__DOUBLES_ARE_FLOATS__)
#define wcstod(str, endptr)          _WStof((str), (endptr), 0)
#else
#define wcstod(str, endptr)          _WStold((str), (endptr), 0)
#endif
   #define wcstoul(str, endptr, base)	_WStoul((str), (endptr), (base))
  #endif /* defined(__cplusplus) && !defined(_NO_CPP_INLINES) */

#endif /* !defined(_DSPTK_LIBIO) */

#ifdef _MISRA_RULES
#pragma diag(pop)
#endif /* _MISRA_RULES */

#endif /* _WCSTOD */

/*
 * Copyright (c) 1992-2005 by P.J. Plauger.  ALL RIGHTS RESERVED.
 * Consult your license regarding permissions and restrictions.
V4.04:1134 */
