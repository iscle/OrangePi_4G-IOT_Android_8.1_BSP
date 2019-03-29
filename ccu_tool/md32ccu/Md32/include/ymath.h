/************************************************************************
 *
 * ymath.h
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
 * $Revision: 1.1.2.2 $
 */


/* ymath.h internal header */
#ifndef _YMATH
#define _YMATH

		/* MACROS FOR _FPP_TYPE */
#define _FPP_NONE	0	/* software emulation of FPP */
#define _FPP_X86	1	/* Intel Pentium */
#define _FPP_SPARC	2	/* Sun SPARC */
#define _FPP_MIPS	3	/* SGI MIPS */
#define _FPP_S390	4	/* IBM S/390 */
#define _FPP_PPC	5	/* Motorola PowerPC */
#define _FPP_HPPA	6	/* Hewlett-Packard PA-RISC */
#define _FPP_ALPHA	7	/* Compaq Alpha */
#define _FPP_ARM	8	/* ARM ARM */
#define _FPP_M68K	9	/* Motorola 68xxx */
#define _FPP_SH4	10	/* Hitachi SH4 */
#define _FPP_IA64	11	/* Intel IA64 */
#define _FPP_WCE	12	/* EDG Windows CE */

		/* MACROS FOR _Dtest RETURN (0 => ZERO) */
#define _DENORM		(-2)	/* C9X only */
#define _FINITE		(-1)
#define _INFCODE	1
#define _NANCODE	2

		/* MACROS FOR _Feraise ARGUMENT */

#define _FE_DIVBYZERO	0x04	/* dummy same as Pentium */
#define _FE_INEXACT	0x20
#define _FE_INVALID	0x01
#define _FE_OVERFLOW	0x08
#define _FE_UNDERFLOW	0x10

		/* TYPE DEFINITIONS */
typedef union
	{	/* pun float types as integer array */
                /* Use short to match Dinkum sources    */
        unsigned short _Word[4];
	float _Float;
	double _Double;
	long double _Long_double;
	} _Dconst;

		/* ERROR REPORTING */
void _Feraise(int _except);

		/* double DECLARATIONS */
#if defined(__DOUBLES_ARE_FLOATS__) && !defined(__FCORE__)
#pragma linkage_name __ffloat__FCosh___ffloat___ffloat;
#endif
double _Cosh(double _x, double _y);
#if defined(__DOUBLES_ARE_FLOATS__)
#pragma linkage_name __sshort__FDtest___P__ffloat
#else
#pragma linkage_name __sshort__LDtest___P__flongdouble
#endif
short _Dtest(double *_px);
short _Exp(double *_p1, double _d, long _l);
#if defined(__DOUBLES_ARE_FLOATS__) && !defined(__FCORE__)
#pragma linkage_name __FLog
#endif
double _Log(double _d, int _i);
#if defined(__DOUBLES_ARE_FLOATS__) && !defined(__FCORE__)
#pragma linkage_name __FSin
#endif
double _Sin(double _d, unsigned int _u);
#if defined(__DOUBLES_ARE_FLOATS__) && !defined(__FCORE__)
#pragma linkage_name __FSinh
#endif
double _Sinh(double _d1, double _d2);
extern _Dconst _Denorm, _Hugeval, _Inf,
	_Nan, _Snan;

		/* float DECLARATIONS */
float _FCosh(float _f1, float _f2);
short _FDtest(float *_px);
short _FExp(float *_px, float _f, long _eoff);
float _FLog(float _f, int _i);
float _FSin(float _f, unsigned int _u);
float _FSinh(float _f1, float _f2);
extern  _Dconst _FDenorm, _FHugeval, _FInf, _FNan, _FSnan;

		/* long double DECLARATIONS */
long double _LCosh(long double _ld1, long double _ld2);
short _LDtest(long double *_ld);
short _LExp(long double *_p, long double _ld, long _l);
long double _LLog(long double _ld, int _i);
long double _LSin(long double _ld, unsigned int _u);
long double _LSinh(long double _ld1, long double _ld2);
extern  _Dconst _LDenorm, _LHugeval, _LInf, _LNan, _LSnan;

#endif /* _YMATH */
