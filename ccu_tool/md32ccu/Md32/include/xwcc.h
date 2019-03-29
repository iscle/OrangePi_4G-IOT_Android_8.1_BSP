/************************************************************************
 *
 * xwcc.h internal header
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
 
#ifndef _XWCC
#define _XWCC

#ifdef _MISRA_RULES
#pragma diag(push)
#pragma diag(suppress:misra_rule_2_2:"DSPTK standard header uses C++ comments")
#pragma diag(suppress:misra_rule_5_3:"DSPTK header will re-use identfiers")
#pragma diag(suppress:misra_rule_5_4:"DSPTK header will re-use identfiers")
#pragma diag(suppress:misra_rule_5_5:"DSPTK header will re-use identfiers")
#pragma diag(suppress:misra_rule_5_6:"DSPTK header will re-use identfiers")
#pragma diag(suppress:misra_rule_5_7:"DSPTK header will re-use identfiers")
#pragma diag(suppress:misra_rule_6_3:"DSPTK header allows use of basic types")
#pragma diag(suppress:misra_rule_19_4:"DSPTK header allows any substitution")
#pragma diag(suppress:misra_rule_19_7:"DSPTK header allows function macros")
#pragma diag(suppress:misra_rule_6_3)
#endif /* _MISRA_RULES */


_C_LIB_DECL
int _Iswctype(wint_t _wc, wctype_t _off);
wint_t _Towctrans(wint_t _wc, wctrans_t _off);
_END_C_LIB_DECL

  #if defined(__cplusplus) && !defined(_NO_CPP_INLINES)
		// INLINES, FOR C++
_C_LIB_DECL
#pragma always_inline
inline int iswalnum(wint_t _Wc)
	{	// test for alnum
	return (_Iswctype(_Wc, 1));
	}

#pragma always_inline
inline int iswalpha(wint_t _Wc)
	{	// test for alpha
	return (_Iswctype(_Wc, 2));
	}

#pragma always_inline
inline int iswcntrl(wint_t _Wc)
	{	// test for cntrl
	return (_Iswctype(_Wc, 3));
	}

#pragma always_inline
inline int iswctype(wint_t _Wc, wctype_t _Off)
	{	// test for ctype category
	return (_Iswctype(_Wc, _Off));
	}

#pragma always_inline
inline int iswdigit(wint_t _Wc)
	{	// test for digit
	return (_Iswctype(_Wc, 4));
	}

#pragma always_inline
inline int iswgraph(wint_t _Wc)
	{	// test for graph
	return (_Iswctype(_Wc, 5));
	}

#pragma always_inline
inline int iswlower(wint_t _Wc)
	{	// test for lower
	return (_Iswctype(_Wc, 6));
	}

#pragma always_inline
inline int iswprint(wint_t _Wc)
	{	// test for print
	return (_Iswctype(_Wc, 7));
	}

#pragma always_inline
inline int iswpunct(wint_t _Wc)
	{	// test for punct
	return (_Iswctype(_Wc, 8));
	}

#pragma always_inline
inline int iswspace(wint_t _Wc)
	{	// test for space
	return (_Iswctype(_Wc, 9));
	}

#pragma always_inline
inline int iswupper(wint_t _Wc)
	{	// test for upper
	return (_Iswctype(_Wc, 10));
	}

#pragma always_inline
inline int iswxdigit(wint_t _Wc)
	{	// test for xdigit
	return (_Iswctype(_Wc, 11));
	}

#pragma always_inline
inline wint_t towlower(wint_t _Wc)
	{	// translate to lower
	return (_Towctrans(_Wc, 1));
	}

#pragma always_inline
inline wint_t towupper(wint_t _Wc)
	{	// translate to upper
	return (_Towctrans(_Wc, 2));
	}

 #if defined(_HAS_C9X)
#pragma always_inline
inline int iswblank(wint_t _Wc)
	{	// check blank bits
	return (_Iswctype(_Wc, 12));
	}
 #endif /* _IS_C9X */

_END_C_LIB_DECL

  #else /* defined(__cplusplus) && !defined(_NO_CPP_INLINES) */
	/* DECLARATIONS AND MACRO OVERRIDES, FOR C */
_C_LIB_DECL
int iswalnum(wint_t _wc);
int iswalpha(wint_t _wc);
int iswcntrl(wint_t _wc);
int iswctype(wint_t _wc, wctype_t _off);
int iswdigit(wint_t _wc);
int iswgraph(wint_t _wc);
int iswlower(wint_t _wc);
int iswprint(wint_t _wc);
int iswpunct(wint_t _wc);
int iswspace(wint_t _wc);
int iswupper(wint_t _wc);
int iswxdigit(wint_t _wc);

wint_t towlower(wint_t _wc);
wint_t towupper(wint_t _wc);

 #if defined(_HAS_C9X)
int iswblank(wint_t _wc);
 #endif /* _IS_C9X */

_END_C_LIB_DECL

 #define iswalnum(wc)	_Iswctype((wc), 1)
 #define iswalpha(wc)	_Iswctype((wc), 2)
 #define iswcntrl(wc)	_Iswctype((wc), 3)
 #define iswctype(wc, off)	_Iswctype((wc), (off))
 #define iswdigit(wc)	_Iswctype((wc), 4)
 #define iswgraph(wc)	_Iswctype((wc), 5)
 #define iswlower(wc)	_Iswctype((wc), 6)
 #define iswprint(wc)	_Iswctype((wc), 7)
 #define iswpunct(wc)	_Iswctype((wc), 8)
 #define iswspace(wc)	_Iswctype((wc), 9)
 #define iswupper(wc)	_Iswctype((wc), 10)
 #define iswxdigit(wc)	_Iswctype((wc), 11)
 #define towctrans(wc, off)	_Towctrans((wc), (off))
 #define towlower(wc)	_Towctrans((wc), 1)
 #define towupper(wc)	_Towctrans((wc), 2)

 #if defined(_HAS_C9X)
 #define iswblank(wc)	_Iswctype((wc), 12)
 #endif /* _IS_C9X */

  #endif /* defined(__cplusplus) && !defined(_NO_CPP_INLINES) */

#ifdef _MISRA_RULES
#pragma diag(pop)
#endif /* _MISRA_RULES */

#endif /* _XWCC */

/*
 * Copyright (c) 1992-2005 by P.J. Plauger.  ALL RIGHTS RESERVED.
 * Consult your license regarding permissions and restrictions.
V4.04:1134 */
