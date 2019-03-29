#if defined (__MD32__)
#pragma once
#ifndef __NO_BUILTIN
#pragma system_header /* builtins.h */
#endif
#endif /* __MD32__ */

#ifndef __BUILTINS_DEFINED
#define __BUILTINS_DEFINED

#ifdef __cplusplus
extern "C" {
#endif

/* A macro to enclose 64-bit constants */

#if !defined (_MSC_VER)
#define __ULLCONST(X) X##ULL
#else
#define __ULLCONST(X) X
#endif

/* Where we have byte addressing, the following types need to be defined */

#if !defined (__NO_BYTE_ADDRESSING__)
typedef unsigned char __uint8;
typedef unsigned short int __uint16;
#endif

/* Define the following type on all platforms */

typedef unsigned int __uint32;

#if defined (_MSC_VER)

/* Visual C++ requires the following type to be defined */

typedef unsigned __int64 __uint64;

#else

/* Other compilers require the following type definitions */

#if !defined (__NO_BYTE_ADDRESSING__)
typedef char __int8;
typedef short int __int16;
#endif

typedef int __int32;
typedef long long int __int64;
typedef unsigned long long int __uint64;

#endif /* _MSC_VER */

#endif /* __OVERRIDE_RAW_TYPES__ */

#ifndef _RAW_TYPES
#define _RAW_TYPES
typedef __int32 _raw32;
typedef __int64 _raw64;
#endif

#if defined (__MD32__) && !defined (__USE_RAW_BUILTINS__)

#define md32_max            __builtin_md32_max
#define md32_min            __builtin_md32_min
#define md32_maxd           __builtin_md32_maxd
#define md32_mind           __builtin_md32_mind
#define md32_maxv           __builtin_md32_maxv
#define md32_minv           __builtin_md32_minv
#define md32_wfi            __builtin_md32_wfi
#define md32_iow            __builtin_md32_iow
#define md32_ior            __builtin_md32_ior
#define md32_mac            __builtin_md32_mac
#define md32_clz            __builtin_md32_clz
#define md32_rnds           __builtin_md32_rnds
#define md32_rndu           __builtin_md32_rndu
#define md32_ieon           __builtin_md32_ieon
#define md32_ieoff          __builtin_md32_ieoff
#define md32_rl             __builtin_md32_rl
#define md32_cw             __builtin_md32_cw
#define md32_bset           __builtin_md32_bset
#define md32_bclr           __builtin_md32_bclr
#define md32_btst           __builtin_md32_btst
#define md32_btgl           __builtin_md32_btgl
#define md32_ror            __builtin_md32_ror
#define md32_rol            __builtin_md32_rol
#define md32_osl            __builtin_md32_osl
#define md32_fup(a,b,c,d)   __builtin_md32_fup((a),(b),(d),(c))
#define md32_fxtu(a,b,c)    __builtin_md32_fxtu((a),(c),(b))
#define md32_fxts(a,b,c)    __builtin_md32_fxts((a),(c),(b))
#define md32_osl_ex         __builtin_md32_osl_ex
#define md32_clb            __builtin_md32_clb
#define md32_cinv           __builtin_md32_cinv

#else /* ! (defined (__APOLLO__) && !defined (__USE_RAW_BUILTINS__)) */

#endif

#ifdef __cplusplus
} /* extern "C" */
#endif
