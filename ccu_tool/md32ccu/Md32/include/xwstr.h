/************************************************************************
 *
 * xwstr.h internal header
 *
 ************************************************************************/
/*
 * Copyright (c) 2009 MediaTek Inc. All Rights Reserved.
 * --------------------
 * This software is protected by copyright and the information
 * contained herein is confidential.  The software may not be
 * copied and the information contained herein may not be used or
 * disclosed except with the written permission of MediaTek Inc.
 *
 * Copyright (c) 1992-2005 by P.J. Plauger.  ALL RIGHTS RESERVED.
 * Consult your license regarding permissions and restrictions.
 */

#ifndef _WSTR
#define _WSTR

#ifdef _MISRA_RULES
#pragma diag(push)
#pragma diag(suppress:misra_rule_2_2)
#pragma diag(suppress:misra_rule_6_3)
#pragma diag(suppress:misra_rule_19_7)
#endif /* _MISRA_RULES */

#ifdef __DSPTK_LEGACY
#include <legacy/predef.h>
#endif

  #if defined(__cplusplus) && !defined(_NO_CPP_INLINES)
		/* INLINES AND OVERLOADS, FOR C++ */
_C_LIB_DECL
const wchar_t *wcschr(const wchar_t *, wchar_t);
const wchar_t *wcspbrk(const wchar_t *, const wchar_t *);
const wchar_t *wcsrchr(const wchar_t *, wchar_t);
const wchar_t *wcsstr(const wchar_t *, const wchar_t *);
_END_C_LIB_DECL

#pragma always_inline
inline wchar_t *wcschr(wchar_t *_Str, wchar_t _Ch)
	{return ((wchar_t *)wcschr((const wchar_t *)_Str, _Ch));
	}

#pragma always_inline
inline wchar_t *wcspbrk(wchar_t *_Str1, const wchar_t *_Str2)
	{return ((wchar_t *)wcspbrk((const wchar_t *)_Str1, _Str2));
	}

#pragma always_inline
inline wchar_t *wcsrchr(wchar_t *_Str, wchar_t _Ch)
	{return ((wchar_t *)wcsrchr((const wchar_t *)_Str, _Ch));
	}

#pragma always_inline
inline wchar_t *wcsstr(wchar_t *_Str1, const wchar_t *_Str2)
	{return ((wchar_t *)wcsstr((const wchar_t *)_Str1, _Str2));
	}

_C_LIB_DECL
#if !defined(_DSPTK_LIBIO)
#pragma always_inline
inline wint_t btowc(int _By)
	{	// convert single byte to wide character
	return (_Btowc(_By));
	}

#pragma always_inline
inline int wctob(wint_t _Wc)
	{	// convert wide character to single byte
	return (_Wctob(_Wc));
	}
#endif  /* !defined(_DSPTK_LIBIO) */

 #if defined(_HAS_C9X)
inline float wcstof(const wchar_t *_Restrict _Str,
	wchar_t **_Restrict _Endptr)
	{	// convert wide string to float
	return (_WStof(_Str, _Endptr, 0));
	}

#pragma always_inline
inline long double wcstold(const wchar_t *_Restrict _Str,
	wchar_t **_Restrict _Endptr)
	{	// convert wide string to double
	return (_WStold(_Str, _Endptr, 0));
	}
 #endif /* _IS_C9X */

_END_C_LIB_DECL

  #else /* defined(__cplusplus) && !defined(_NO_CPP_INLINES) */
   #define _WConst_return

_C_LIB_DECL
wchar_t *wcschr(const wchar_t *_s, wchar_t _c);
wchar_t *wcspbrk(const wchar_t *_s1, const wchar_t *_s2);
wchar_t *wcsrchr(const wchar_t *_s, wchar_t _c);
wchar_t *wcsstr(const wchar_t *_s1, const wchar_t *_s2);
wint_t btowc(int _c);
int wctob(wint_t _wc);

 #if defined(_HAS_C9X) 
float wcstof(const wchar_t *_Restrict,
	wchar_t **_Restrict);
long double wcstold(const wchar_t *_Restrict,
	wchar_t **_Restrict);
 #endif /* _IS_C9X */

_END_C_LIB_DECL

   #define btowc(by)	_Btowc(by)
   #define wcstof(str, endptr)	_WStof((str), (endptr), 0)
   #define wcstold(str, endptr)	_WStold((str), (endptr), 0)
   #define wctob(wc)	_Wctob(wc)
  #endif /* defined(__cplusplus) && !defined(_NO_CPP_INLINES) */

#ifdef _MISRA_RULES
#pragma diag(pop)
#endif /* _MISRA_RULES */

#endif /* _WSTR */

/*
 * Copyright (c) 1992-2005 by P.J. Plauger.  ALL RIGHTS RESERVED.
 * Consult your license regarding permissions and restrictions.
V4.04:1134 */
