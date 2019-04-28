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
 * @file    mstar_drv_self_mp_test.h
 *
 * @brief   This file defines the interface of touch screen
 *
 *
 */

#ifndef __MSTAR_DRV_SELF_MP_TEST_H__
#define __MSTAR_DRV_SELF_MP_TEST_H__

/*--------------------------------------------------------------------------*/
/* INCLUDE FILE                                                             */
/*--------------------------------------------------------------------------*/

#include "mstar_drv_common.h"

#if defined(CONFIG_ENABLE_TOUCH_DRIVER_FOR_SELF_IC)
#ifdef CONFIG_ENABLE_ITO_MP_TEST

/*--------------------------------------------------------------------------*/
/* PREPROCESSOR CONSTANT DEFINITION                                         */
/*--------------------------------------------------------------------------*/

#define CTP_MP_TEST_RETRY_COUNT (3)


#define OPEN_TEST_NON_BORDER_AREA_THRESHOLD (35) // range : 25~60
#define OPEN_TEST_BORDER_AREA_THRESHOLD     (40) // range : 25~60

#define	SHORT_TEST_THRESHOLD                (3500)

#define	MP_TEST_MODE_OPEN_TEST              (0x01)
#define	MP_TEST_MODE_SHORT_TEST             (0x02)

#define MAX_CHANNEL_NUM   (48)

// define for MSG21XXA
#define PIN_GUARD_RING    (46) 
#define GPO_SETTING_SIZE  (3)  

// define for MSG22XX
#define RIU_BASE_ADDR       (0)   
#define RIU_WRITE_LENGTH    (144)  
#define CSUB_REF            (0) //(18)   
#define CSUB_REF_MAX        (0x3F) 

#define MAX_SUBFRAME_NUM    (24)
#define MAX_AFE_NUM         (4)


#define REG_INTR_FIQ_MASK           (0x04)          
#define FIQ_E_FRAME_READY_MASK      (1 << 8)


/*--------------------------------------------------------------------------*/
/* PREPROCESSOR MACRO DEFINITION                                            */
/*--------------------------------------------------------------------------*/

/*--------------------------------------------------------------------------*/
/* DATA TYPE DEFINITION                                                     */
/*--------------------------------------------------------------------------*/


/*--------------------------------------------------------------------------*/
/* GLOBAL VARIABLE DEFINITION                                               */
/*--------------------------------------------------------------------------*/


/*--------------------------------------------------------------------------*/
/* GLOBAL FUNCTION DECLARATION                                              */
/*--------------------------------------------------------------------------*/

extern void DrvMpTestCreateMpTestWorkQueue(void);
extern void DrvMpTestGetTestDataLog(ItoTestMode_e eItoTestMode, u8 *pDataLog, u32 *pLength);
extern void DrvMpTestGetTestFailChannel(ItoTestMode_e eItoTestMode, u8 *pFailChannel, u32 *pFailChannelCount);
extern s32 DrvMpTestGetTestResult(void);
extern void DrvMpTestScheduleMpTestWork(ItoTestMode_e eItoTestMode);

#endif //CONFIG_ENABLE_ITO_MP_TEST
#endif //CONFIG_ENABLE_TOUCH_DRIVER_FOR_SELF_IC

#endif  /* __MSTAR_DRV_SELF_MP_TEST_H__ */