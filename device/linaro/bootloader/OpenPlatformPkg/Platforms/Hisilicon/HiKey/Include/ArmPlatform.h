/** @file
*
*  Copyright (c) 2014-2015, Linaro Limited. All rights reserved.
*  Copyright (c) 2014-2015, Hisilicon Limited. All rights reserved.
*
*  This program and the accompanying materials
*  are licensed and made available under the terms and conditions of the BSD License
*  which accompanies this distribution.  The full text of the license may be found at
*  http://opensource.org/licenses/bsd-license.php
*
*  THE PROGRAM IS DISTRIBUTED UNDER THE BSD LICENSE ON AN "AS IS" BASIS,
*  WITHOUT WARRANTIES OR REPRESENTATIONS OF ANY KIND, EITHER EXPRESS OR IMPLIED.
*
**/

#ifndef __PLATFORM_H__
#define __PLATFORM_H__

//
// We don't care about this value, but the PL031 driver depends on the macro
// to exist: it will pass it on to our ArmPlatformSysConfigLib:ConfigGet()
// function, which just returns EFI_UNSUPPORTED.
//
//
#define SYS_CFG_RTC                             0

#endif	/* __PLATFORM_H__ */
