/************************************************************************
 *
 * cycle_count.h
 *
 * $Revision: 1.7 $
 ************************************************************************/

/*
 * Copyright (c) 2009 MediaTek Inc. All Rights Reserved.
 * --------------------
 * This software is protected by copyright and the information
 * contained herein is confidential.  The software may not be
 * copied and the information contained herein may not be used or
 * disclosed except with the written permission of MediaTek Inc.
 * 
 * (c) Copyright 2004-2007 Analog Devices, Inc.  All rights reserved.
 */

/*
   Generic low level interface to measure cycles counts
 */

#pragma once
#ifndef __NO_BUILTIN
#pragma system_header /* cycle_count.h */
#endif

#ifndef __CYCLE_COUNT_DEFINED
#define __CYCLE_COUNT_DEFINED

/* Include low level support */
#include <xcycle_count.h>

typedef  volatile unsigned long long  _cycle_t;
typedef  _cycle_t    cycle_t;

/* The following low level macros are defined, operating on type cycle_t 

      START_CYCLE_COUNT( S )    - Set S to the current value  
                                  in the cycle count register(s) 

      STOP_CYCLE_COUNT( X, S )  - Return in X the elapsed cycle count 
                                  since start counting 
                                  X =   current count 
                                      - S (=start count)
                                      - measurement overhead
      PRINT_CYCLES( STRG, X )   - Print string STRG followed by X
 */



#define  START_CYCLE_COUNT( _S )
#define  STOP_CYCLE_COUNT( _X, _S )
#define  PRINT_CYCLES( _STRG, _X )


#endif   /* __CYCLE_COUNT_DEFINED */
