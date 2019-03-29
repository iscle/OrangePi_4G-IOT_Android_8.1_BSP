/************************************************************************
 *
 * assert.h
 *
 ************************************************************************/

/*
 * Copyright (c) 2008-2009 MediaTek Inc. All Rights Reserved.
 * --------------------
 * This software is protected by copyright and the information
 * contained herein is confidential.  The software may not be
 * copied and the information contained herein may not be used or
 * disclosed except with the written permission of MediaTek Inc.
 *
 * (c) Copyright 2001-2007 Analog Devices, Inc.  All rights reserved.
 *
 * Copyright (c) 1992-2001 by P.J. Plauger.  ALL RIGHTS RESERVED.
 * Consult your license regarding permissions and restrictions.
 * V3.10:1134 
 */


/* NOTE: No pramga once as assert can be redefined during compilation. */
#ifndef __NO_BUILTIN
#pragma system_header /* assert.h */
#endif

#ifdef _MISRA_RULES
#pragma diag(push)
#pragma diag(suppress:misra_rule_6_3)
#pragma diag(suppress:misra_rule_8_8)
#pragma diag(suppress:misra_rule_19_4)
#pragma diag(suppress:misra_rule_19_6)
#pragma diag(suppress:misra_rule_19_7)
#pragma diag(suppress:misra_rule_19_13)
#pragma diag(suppress:misra_rule_19_15)
#pragma diag(suppress:misra_rule_20_1)
#endif /* _MISRA_RULES */


/* assert.h standard header */
#ifndef _YVALS
 #include <yvals.h>
#endif
_C_STD_BEGIN

#undef assert	/* remove existing definition */

 #ifdef NDEBUG
  #define assert(test)	((void)0)
 #else /* NDEBUG */
_C_LIB_DECL
void _Assert(char *mesg);
_END_C_LIB_DECL

		/* MACROS */
 #define _STRIZE(x)	_VAL(x)
 #define _VAL(x)	#x
 #ifdef _VERBOSE_DEBUGGING
  #include <stdio.h>
  #define assert(test)	((test) ? (void)_STD fprintf(stderr, \
	__FILE__ ":" _STRIZE(__LINE__) " " #test " -- OK\n") \
	: _CSTD _Assert(__FILE__ ":" _STRIZE(__LINE__) " " #test))
 #else
  #define assert(test)	((test) ? (void)0 \
	: _CSTD _Assert(__FILE__ ":" _STRIZE(__LINE__) " " #test))
 #endif
 #endif /* NDEBUG */
_C_STD_END

#ifdef _MISRA_RULES
#pragma diag(pop)
#endif /* _MISRA_RULES */
/*
 * Copyright (c) 1992-2001 by P.J. Plauger.  ALL RIGHTS RESERVED.
 * Consult your license regarding permissions and restrictions.
 * V3.10:1134
 */
