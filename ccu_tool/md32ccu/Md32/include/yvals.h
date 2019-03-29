/************************************************************************
 *
 * yvals.h
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
 * (c) Copyright 2002-2008 Analog Devices, Inc.  All rights reserved.
 * (c) Copyright 1992-2005 by P.J. Plauger.  ALL RIGHTS RESERVED.
 * Consult your license regarding permissions and restrictions.
 * $Revision: 1.1.2.5 $
 */
#pragma once
#ifndef __NO_BUILTIN
#pragma system_header /* yvals.h */
#endif

/*
** yvals.h for library version 4.0.4 
*/

#ifndef _YVALS
#define _YVALS

#ifdef _MISRA_RULES
#pragma diag(push)
#pragma diag(suppress:misra_rule_2_4)
#pragma diag(suppress:misra_rule_5_2:"DSPTK header will re-use identfiers")
#pragma diag(suppress:misra_rule_5_3:"DSPTK header will re-use identfiers")
#pragma diag(suppress:misra_rule_5_4:"DSPTK header will re-use identfiers")
#pragma diag(suppress:misra_rule_5_5:"DSPTK header will re-use identfiers")
#pragma diag(suppress:misra_rule_5_6:"DSPTK header will re-use identfiers")
#pragma diag(suppress:misra_rule_5_7:"DSPTK header will re-use identfiers")
#pragma diag(suppress:misra_rule_6_3:"DSPTK header allows use of basic types")
#pragma diag(suppress:misra_rule_19_1)
#pragma diag(suppress:misra_rule_19_4)
#pragma diag(suppress:misra_rule_19_7)
#pragma diag(suppress:misra_rule_19_10)
#pragma diag(suppress:misra_rule_19_11)
#endif /* _MISRA_RULES */

#ifdef __DSPTK_LEGACY
#include <legacy/predef.h>
#endif

#define _CPPLIB_VER	404

/* You can predefine (on the compile command line, for example):

_ALT_NS=1 -- to use namespace _Dinkum_std for C++
_ALT_NS=2 -- to use namespace _Dinkum_std for C++ and C
_C_AS_CPP -- to compile C library as C++
_C_IN_NS -- to define C names in std/_Dinkum_std instead of global namespace
_C99 -- to turn ON C99 library support
_ABRCPP -- to turn ON Abridged C++ dialect (implies _ECPP)
_ECPP -- to turn ON Embedded C++ dialect
_NO_EX -- to turn OFF use of try/throw
_NO_MT -- to turn OFF thread synchronization
_NO_NS -- to turn OFF use of namespace declarations
_STL_DB (or _STLP_DEBUG) -- to turn ON iterator/range debugging
__NO_LONG_LONG -- to define _Longlong as long, not long long

You can change (in this header):

_ADDED_C_LIB -- from 1 to 0 to omit declarations for C extensions
_COMPILER_TLS -- from 0 to 1 if _TLS_QUAL is not nil
_EXFAIL -- from 1 to any nonzero value for EXIT_FAILURE
_FILE_OP_LOCKS -- from 0 to 1 for file atomic locks
_GLOBAL_LOCALE -- from 0 to 1 for shared locales instead of per-thread
_HAS_IMMUTABLE_SETS -- from 1 to 0 to permit alterable set elements
_HAS_STRICT_CONFORMANCE -- from 0 to 1 to disable nonconforming extensions
_HAS_TRADITIONAL_IOSTREAMS -- from 1 to 0 to omit old iostreams functions
_HAS_TRADITIONAL_ITERATORS -- from 0 to 1 for vector/string pointer iterators
_HAS_TRADITIONAL_POS_TYPE -- from 0 to 1 for streampos same as streamoff
_HAS_TRADITIONAL_STL -- from 1 to 0 to omit old STL functions
_IOSTREAM_OP_LOCKS -- from 0 to 1 for iostream atomic locks
_TLS_QUAL -- from nil to compiler TLS qualifier, such as __declspec(thread)
_USE_EXISTING_SYSTEM_NAMES -- from 1 to 0 to disable mappings (_Open to open)

Include directories needed to compile with Dinkum C:

C -- include/c
C99 -- include/c (define _C99)
Embedded C++ -- include/c include/embedded (define _ECPP)
Abridged C++ -- include/c include/embedded include (define _ABRCPP)
Standard C++ -- include/c include
Standard C++ with export -- include/c include/export include
	(--export --template_dir=lib/export)

Include directories needed to compile with native C:

C -- none
C99 -- N/A
Embedded C++ -- include/embedded (define _ECPP)
Abridged C++ -- include/embedded include (define _ABRCPP)
Standard C++ -- include
Standard C++ with export -- include/export include
	(--export --template_dir=lib/export)
*/

/*
** define target specific configurations
**   _HAS_EXCEPTIONS -- defined for try/throw logic
**   _USING_DINKUM_C_LIBRARY -- defined when target also uses Dinkum C library
**   _DSPTK_FLOAT_ENDIAN -- 0 for big endian, 3 for little endian floating-point
**   _DSPTK_SUPPORT_LONG_DOUBLE -- defined if target supports long double type
**   _DSPTK_SUPPORT_WCHAR_WCSFTIME -- defined if target supports wcsftime()
**   _DSPTK_WCHART_UNSIGNED -- define to unsigned if wchar_t is unsigned
**   _DSPTK_ABRTNUM -- number of SIGABRT
**   _DSPTK_SIGMAX -- one more than last signal code
**   _DSPTK_TARG_INT16 -- define to 1 when target ints are 16-bit, otherwise 0
**   _DSPTK_TARG_MBMAX -- max multi-char bytes for target, to define MB_LEN_MAX
**   _DSPTK_ALIGNA -- storage alignment adjustment
**   _DSPTK_BYTE_BITS -- bits per target byte
**   _Sizet -- size_t type
**   _Ptrdifft -- ptrdiff_t type
*/
#ifdef __EXCEPTIONS
#  define _HAS_EXCEPTIONS	1
#endif
#  define _USING_DINKUM_C_LIBRARY 1
#  define _HAS_DINKUM_CLIB 1

#if defined(__DOUBLES_ARE_FLOATS__)
#  /* Array index for MSB:  union {float;short[2];} */
#  define _DSPTK_FLOAT_ENDIAN               1
#else
#  /* Array index for MSB:  union {long double;short[4];} */
#  define _DSPTK_FLOAT_ENDIAN               3
#endif

#  define _DSPTK_SUPPORT_LONG_DOUBLE        1
#  define _DSPTK_WCHART_UNSIGNED            unsigned
#  define _DSPTK_ABRTNUM                    22
#  define _DSPTK_SIGMAX                     23
#  define _DSPTK_TARG_INT16                 0
#  define _DSPTK_TARG_MBMAX                 8
#  define _DSPTK_ALIGNA                     3U /* even-byte boundaries (2^^1) */
#  define _DSPTK_BYTE_BITS                  8

#  define _NSETJMP 39   /* size of jmpbuf */

typedef long int _Ptrdifft;
typedef long unsigned int _Sizet;

#ifndef __EDG_VERSION__
#  error __EDG_VERSION__ not defined??
#endif

typedef signed   int _Int32t;
typedef unsigned int _Uint32t;

 #if !defined(_HAS_C9X) && defined(_C99)
  #define _HAS_C9X	1
 #endif /* !defined(_HAS_C9X) etc. */

 #define _HAS_C9X_IMAGINARY_TYPE	(defined(_HAS_C9X) && __EDG__ \
	&& !defined(__cplusplus))

# define _ABRCPP		/* Abridged C++ (implies embedded) */
# define _ECPP			/* Embedded C++ dialiect */
# define _IS_EMBEDDED	1	/* 1 for Embedded C++ */

/* NAMING PROPERTIES */
/* #define _STD_LINKAGE	defines C names as extern "C++" */
/* #define _STD_USING	defines C names in namespace std or _Dinkum_std */


 #if !defined(_STD_USING) && defined(__cplusplus) \
	&& (defined(_C_IN_NS) || (defined(_ALT_NS) && (1 < _ALT_NS)))
  #define _STD_USING	/* exports C names to global, else reversed */

 #elif defined(_STD_USING) && !defined(__cplusplus)
  #undef _STD_USING	/* define only for C++ */
 #endif /* !defined(_STD_USING) */

 #if !defined(_HAS_STRICT_LINKAGE) \
	&& __EDG__ && !defined(_WIN32_C_LIB)
  #define _HAS_STRICT_LINKAGE	1	/* extern "C" in function type */
 #endif /* !defined(_HAS_STRICT_LINKAGE) */

		/* THREAD AND LOCALE CONTROL */
# if defined(_DSPTK_THREADS) || defined(_DSPTK_MULTICORE)
#  define _MULTI_THREAD
# endif


#define _GLOBAL_LOCALE	0	/* 0 for per-thread locales, 1 for shared */
#define _FILE_OP_LOCKS	0	/* 0 for no FILE locks, 1 for atomic */
#define _IOSTREAM_OP_LOCKS	0	/* 0 for no iostream locks, 1 for atomic */

		/* THREAD-LOCAL STORAGE */
/* #define _COMPILER_TLS	0    	 1 if compiler supports TLS directly */
#define _TLS_QUAL	/* TLS qualifier, such as __declspec(thread), if any */


 #define _ADDED_C_LIB	1
 #define _HAS_IMMUTABLE_SETS	1
 /* #define _HAS_TRADITIONAL_IOSTREAMS	1 */
 #define _HAS_TRADITIONAL_IOSTREAMS	0
 #define _HAS_TRADITIONAL_ITERATORS	0
 #define _HAS_TRADITIONAL_POS_TYPE	0
 #define _HAS_TRADITIONAL_STL	1
 /* #define _USE_EXISTING_SYSTEM_NAMES	1  _Open => open etc. */

  #define _HAS_STRICT_CONFORMANCE	0	/* enable nonconforming extensions */

 #define _HAS_ITERATOR_DEBUGGING	0   	/* 1 for range checks, etc. */

/* NAMESPACE CONTROL */

 #if defined(__cplusplus)

/* Compiler now predefines the _HAS_NAMESPACE macro dependent on -ignore-std 
 * flag. 
 */
 #if _HAS_NAMESPACE
namespace std {}

 #if defined(_C_AS_CPP)
  #define _NO_CPP_INLINES	/* just for compiling C library as C++ */
 #endif /* _C_AS_CPP */

 #if defined(_STD_USING)

  #if defined(_C_AS_CPP)	/* define library in std */
   #define _STD_BEGIN	namespace std {_C_LIB_DECL
   #define _STD_END		_END_C_LIB_DECL }

  #else /* _C_AS_CPP */
   #define _STD_BEGIN	namespace std {
   #define _STD_END		}
  #endif /* _C_AS_CPP */

   #define _C_STD_BEGIN	namespace std {
   #define _C_STD_END	}
   #define _CSTD		::std::
   #define _STD			::std::

 #else /* !defined(_STD_USING) */

  #if defined(_C_AS_CPP)	/* define C++ library in std, C in global */
   #define _STD_BEGIN	_C_LIB_DECL
   #define _STD_END		_END_C_LIB_DECL

  #else /* _C_AS_CPP */
   #define _STD_BEGIN	namespace std {
   #define _STD_END		}
  #endif /* _C_AS_CPP */

   #define _C_STD_BEGIN	
   #define _C_STD_END	
   #define _CSTD		::
   #define _STD			::std::
 #endif /* _STD_USING */

  #define _X_STD_BEGIN	namespace std {
  #define _X_STD_END	}
  #define _XSTD			::std::

  #if defined(_STD_USING)
   #undef _GLOBAL_USING		/* c* in std namespace, *.h imports to global */
  #elif !defined(_MSC_VER) || 1300 <= _MSC_VER
   #define _GLOBAL_USING	/* *.h in global namespace, c* imports to std */
  #endif /* defined(_STD_USING) */

  #if defined(_STD_LINKAGE)
   #define _C_LIB_DECL		extern "C++" {	/* C has extern "C++" linkage */
  #else /* defined(_STD_LINKAGE) */
   #define _C_LIB_DECL		extern "C" {	/* C has extern "C" linkage */
  #endif /* defined(_STD_LINKAGE) */

  #define _END_C_LIB_DECL	}
  #define _EXTERN_C			extern "C" {
  #define _END_EXTERN_C		}

 #else /* _HAS_NAMESPACE */
  #define _STD_BEGIN
  #define _STD_END
  #define _STD	::

  #define _X_STD_BEGIN
  #define _X_STD_END
  #define _XSTD	::

  #define _C_STD_BEGIN
  #define _C_STD_END
  #define _CSTD	::

  #define _C_LIB_DECL		extern "C" {
  #define _END_C_LIB_DECL	}
  #define _EXTERN_C			extern "C" {
  #define _END_EXTERN_C		}
 #endif /* _HAS_NAMESPACE */

 #else /* __cplusplus */
  #define _STD_BEGIN
  #define _STD_END
  #define _STD

  #define _X_STD_BEGIN
  #define _X_STD_END
  #define _XSTD

  #define _C_STD_BEGIN
  #define _C_STD_END
  #define _CSTD

  #define _C_LIB_DECL
  #define _END_C_LIB_DECL
  #define _EXTERN_C
  #define _END_EXTERN_C
 #endif /* __cplusplus */

 /* Define _Restrict to be nothing */
 #define _Restrict

/*
** bool type
*/
 #ifdef __cplusplus
typedef bool _Bool;
 #endif /* __cplusplus */

/* MISCELLANEOUS MACROS */
# define _CRTIMP
# define _CDECL

_C_STD_BEGIN

/*
** errno values
*/
# define _EDOM     33
# define _ERANGE   34
# define _EFPOS    35
# define _EILSEQ   36
# define _ERRMAX   37


/* FLOATING-POINT PROPERTIES */
# define _D0     _DSPTK_FLOAT_ENDIAN
# define _FBIAS  0x7e
# define _FOFF   7
# define _FRND   1
# if defined(__DOUBLES_ARE_FLOATS__)
#  define _DBIAS _FBIAS
#  define _DOFF  _FOFF
# else
#  define _DBIAS 0x3fe   /* IEEE format double and float */
#  define _DOFF  4
# endif
# if defined(_DSPTK_SUPPORT_LONG_DOUBLE)
#  define _DLONG  0       /* 1 if 80-bit long double */
#  define _LBIAS  0x3fe   /* 0x3ffe if 80-bit long double */
#  define _LOFF  4        /* 15 if 80-bit long double */
# else
#  define _DLONG  0       /* 1 if 80-bit long double */
#  define _LBIAS  _FBIAS
#  define _LOFF   _FOFF
#endif

/* INTEGER PROPERTIES */
#define _BITS_BYTE	_DSPTK_BYTE_BITS
#define _C2		1			/* 0 if not 2's complement */
#define _MBMAX		_DSPTK_TARG_MBMAX	/* MB_LEN_MAX */
#define _CSIGN	0				/* 0 if char is not signed */
#ifdef _DSPTK_TARG_INT16
# define _ILONG		1			/* 0 if 16-bit int */
#else
# define _ILONG		0			/* 0 if 16-bit int */
#endif /* defined(_DSPTK_TARG_INT16) */

#define _MAX_EXP_DIG	8	/* for parsing numerics */
#define _MAX_INT_DIG	32
#define _MAX_SIG_DIG	48

/*
** Atexit properties
*/

#define NATS 40


/*
** long long support
*/
# if !defined(__NO_LONG_LONG)
  #define _LONGLONG     long long
  #define _ULONGLONG    unsigned long long
  #define _LLONG_MAX    0x7fffffffffffffffLL
  #define _ULLONG_MAX   0xffffffffffffffffULL
#define  _LLONG_MIN   (-_LLONG_MAX -_C2)
  typedef _LONGLONG   _Longlong;
  typedef _ULONGLONG  _ULonglong;
#else
# define _LLONG_MAX   0x7fffffffL
# define _ULLONG_MAX  0xffffffffUL
  typedef long           _Longlong;
  typedef unsigned long  _ULonglong;
# endif /* __NO_LONG_LONG */

/* wchar_t AND wint_t PROPERTIES */
# if defined(_DSPTK_WCHART_UNSIGNED)
  typedef unsigned int _Wintt;
  typedef unsigned int _Wchart;
#  define _WCMIN     0
#  if _DSPTK_TARG_INT16
#   define _WCMAX    0xffff
#  else
#   define _WCMAX    0xffffffff
#  endif
# else
  typedef int _Wintt;
  typedef int _Wchart;
#  if _DSPTK_TARG_INT16
#   define _WCMAX    0x7fff
#  else
#   define _WCMAX    0x7fffffff
#  endif
#  define _WCMIN     (-_WCMAX - _C2)
#endif

#define _HAS_POINTER_CLIB 1

/* POINTER PROPERTIES */
#ifdef _MISRA_RULES
#define _NULL		((void *)0)	/* MISRA requirement */
#else
#define _NULL		0	/* 0L if pointer same as long */
#endif /* _MISRA_RULES */

/* signal PROPERTIES */
#define _SIGABRT	_DSPTK_ABRTNUM
#define _SIGMAX		_DSPTK_SIGMAX

/* stdarg PROPERTIES */
# ifndef _VA_LIST_DEFINED
  typedef void* va_list;
#  define _VA_LIST_DEFINED
#endif
typedef va_list _Va_list;
# if defined(_HAS_C9X)
#  undef va_copy
  _EXTERN_C
  void _Vacopy(va_list *, va_list);
  _END_EXTERN_C
#  define va_copy(apd, aps)	_Vacopy(&(apd), aps)
# endif /* _HAS_C9X */

/* stdlib PROPERTIES */
#define _EXFAIL	1	/* EXIT_FAILURE */

_EXTERN_C
void _Atexit(void (*func)(void));
_END_EXTERN_C

typedef struct _Mbstatet {
   _Wchart _Wchar;
   char _State;
   } _Mbstatet;
#define _MBSTATET

/* stdio PROPERTIES */
#define _Filet   FILE
#define _FNAMAX	64
#define _FOPMAX	40
#define _TNAMAX	40
typedef struct _Fpost {
   long _Off;   /* can be system dependent */
   _Mbstatet _Wstate;
   } _Fpost;
#ifndef _FPOSOFF
 #define _FPOSOFF(fp)   ((fp)._Off)
#endif

#define _FD_TYPE	signed char
#define _FD_NO(str) ((str)->_Handle)
#define _FD_VALID(fd)	(0 <= (fd))	/* fd is signed integer */
#define _FD_INVALID	(-1)
#define _SYSCH(x)	x
typedef char _Sysch_t;

/* STORAGE ALIGNMENT PROPERTIES */
#define _MEMBND	_DSPTK_ALIGNA /* eight-byte boundaries (2^^3) */
# define _AUPBND   _DSPTK_ALIGNA
# define _ADNBND   _DSPTK_ALIGNA

/* time PROPERTIES */
#define _CPS	1
#define _TBIAS	((70 * 365LU + 17) * 86400)
_C_STD_END

		/* MISCELLANEOUS MACROS */
#define _ATEXIT_T	void

#ifndef _TEMPLATE_STAT
 #define _TEMPLATE_STAT
#endif /* */

 #define _NO_RETURN(fun)	void fun

 #if _HAS_NAMESPACE

 #ifdef __cplusplus

  #if defined(_STD_USING)
_STD_BEGIN
using ::va_list;
_STD_END
  #endif /* !defined(_C_AS_CPP) && defined(_GLOBAL_USING) */

 #endif /* __cplusplus */
 #endif /* _HAS_NAMESPACE */

#ifdef __DSPTK_LEGACY
#include <legacy/yvals.h>
#endif

#ifdef _MISRA_RULES
#pragma diag(pop)
#endif /* _MISRA_RULES */

#endif /* _YVALS */
