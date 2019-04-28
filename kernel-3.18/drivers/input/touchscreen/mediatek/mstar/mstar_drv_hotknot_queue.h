////////////////////////////////////////////////////////////////////////////////
//
// Copyright (c) 2006-2012 MStar Semiconductor, Inc.
// All rights reserved.
//
// Unless otherwise stipulated in writing, any and all information contained
// herein regardless in any format shall remain the sole proprietary of
// MStar Semiconductor Inc. and be kept in strict confidence
// (??MStar Confidential Information??) by the recipient.
// Any unauthorized act including without limitation unauthorized disclosure,
// copying, use, reproduction, sale, distribution, modification, disassembling,
// reverse engineering and compiling of the contents of MStar Confidential
// Information is unlawful and strictly prohibited. MStar hereby reserves the
// rights to any and all damages, losses, costs and expenses resulting therefrom.
//
////////////////////////////////////////////////////////////////////////////////

/**
 *
 * @file    mstar_drv_hotknot_queue.h
 *
 * @brief   This file defines the queue structure for hotknot
 *
 *
 */

#ifndef __MSTAR_DRV_HOTKNOT_QUEUE_H__
#define __MSTAR_DRV_HOTKNOT_QUEUE_H__


////////////////////////////////////////////////////////////
/// Included Files
////////////////////////////////////////////////////////////

#include "mstar_drv_common.h"


#ifdef CONFIG_ENABLE_HOTKNOT
////////////////////////////////////////////////////////////
/// Constant
////////////////////////////////////////////////////////////
#define HOTKNOT_QUEUE_SIZE               1024


////////////////////////////////////////////////////////////
/// Variables
////////////////////////////////////////////////////////////


////////////////////////////////////////////////////////////
/// Function Prototype
////////////////////////////////////////////////////////////
extern void CreateQueue(void);
extern void ClearQueue(void);
extern int PushQueue(u8 * pBuf, u16 nLength);
extern int PopQueue(u8 * pBuf, u16 nLength);
extern int ShowQueue(u8 * pBuf, u16 nLength);    //just show data, not fetch data
extern void ShowAllQueue(u8 * pBuf, u16 * pFront, u16 * pRear);    //just show data, not fetch data
extern void DeleteQueue(void);


#endif //CONFIG_ENABLE_HOTKNOT
#endif // __MSTAR_DRV_HOTKNOT_QUEUE_H__
