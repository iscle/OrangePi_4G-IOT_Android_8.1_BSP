/************************************************************************
 *
 * cycle_count_md32.h
 *   Platform specific functions to measure cycle counts
 *
 * This header file is not meant to be included directly.  Please
 * include <cycle_count.h> instead.
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
 * (c) Copyright 2004-2008 Analog Devices, Inc.  All rights reserved.
 *
 * $Revision: 1.2 $
 */

#pragma once
#ifndef __NO_BUILTIN
#pragma system_header /* cycle_count_md32.h */
#endif

#ifndef __CYCLE_COUNT_MD32_DEFINED
#define __CYCLE_COUNT_MD32_DEFINED

/*  Md32 Processor Speed (figures denote maximum performance possible) */
#ifndef __PROCESSOR_SPEED__
#define  __PROCESSOR_SPEED__       300000000      /* 300 MHz */
#endif  /* !defined __PROCESSOR_SPEED__ */

/* Define low level macros to handle cycle counts */

/* Return current value in cycle count registers     
   When reading CYCLES, the contents of CYCLES2 is stored with a shadow
   write at the same time (thus reading the cycle count registers is an
   atomic operation). Reading CYCLES2 thereafter will return the upper
   half of the cycle count register at the time CYCLES has been read
   until CYCLES is read again.
 */

#define _GET_CYCLE_COUNT( _CURR_COUNT )
#define _START_CYCLE_COUNT( _START_COUNT )
#define _STOP_CYCLE_COUNT ( _CURR_COUNT, _START_COUNT )

#endif  /* __CYCLE_COUNT_MD32_DEFINED */
