/* This file is free software; you can redistribute it and/or modify
 * it under the terms of version 2 of the GNU General Public License
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301, USA.
*/

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
