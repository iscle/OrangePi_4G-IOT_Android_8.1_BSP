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
 *
 * @file    mstar_drv_mutual_mp_test.h
 *
 * @brief   This file defines the interface of touch screen
 *
 *
 */

#ifndef __MSTAR_DRV_MUTUAL_MP_TEST_H__
#define __MSTAR_DRV_MUTUAL_MP_TEST_H__

/*--------------------------------------------------------------------------*/
/* INCLUDE FILE                                                             */
/*--------------------------------------------------------------------------*/

#include "mstar_drv_common.h"

#if defined(CONFIG_ENABLE_TOUCH_DRIVER_FOR_MUTUAL_IC)
#ifdef CONFIG_ENABLE_ITO_MP_TEST

/*--------------------------------------------------------------------------*/
/* PREPROCESSOR CONSTANT DEFINITION                                         */
/*--------------------------------------------------------------------------*/

#define MAX_CHANNEL_NUM  38
#define MAX_CHANNEL_DRV  28
#define MAX_CHANNEL_SEN  14
#define MAX_MUTUAL_NUM  (MAX_CHANNEL_DRV * MAX_CHANNEL_SEN)
#define ANA3_MUTUAL_CSUB_NUMBER (192) /* 192 = 14 * 13 + 10 */
#define ANA4_MUTUAL_CSUB_NUMBER (MAX_MUTUAL_NUM - ANA3_MUTUAL_CSUB_NUMBER) /* 200 = 392 - 192 */
#define FILTER1_MUTUAL_DELTA_C_NUMBER (190) /* 190 = (6 * 14 + 11) * 2 */
#define FILTER2_MUTUAL_DELTA_C_NUMBER (594) /* 594 = (MAX_MUTUAL_NUM - (6 * 14 + 11)) * 2 */

#define FIR_THRESHOLD    6553
#define FIR_RATIO    50

#define IIR_MAX		32600
#define SHORT_VALUE	2000
#define PIN_GUARD_RING    (38)

#define CTP_MP_TEST_RETRY_COUNT (3)

#define SHORTVALUE  3000
#define WATERVALUE 20000
#define DC_RANGE  30
#define DC_RATIO  10


/*--------------------------------------------------------------------------*/
/* PREPROCESSOR MACRO DEFINITION                                            */
/*--------------------------------------------------------------------------*/

/*--------------------------------------------------------------------------*/
/* DATA TYPE DEFINITION                                                     */
/*--------------------------------------------------------------------------*/

typedef struct {
	u8 nMy;
	u8 nMx;
	u8 nKeyNum;
} TestScopeInfo_t;

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
extern void DrvMpTestGetTestScope(TestScopeInfo_t *pInfo);
extern void DrvMpTestScheduleMpTestWork(ItoTestMode_e eItoTestMode);

#endif /* CONFIG_ENABLE_ITO_MP_TEST */
#endif /* CONFIG_ENABLE_TOUCH_DRIVER_FOR_MUTUAL_IC */

#endif  /* __MSTAR_DRV_MUTUAL_MP_TEST_H__ */
