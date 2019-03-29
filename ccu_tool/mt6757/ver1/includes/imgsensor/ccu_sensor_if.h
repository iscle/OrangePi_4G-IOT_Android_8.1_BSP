/*****************************************************************************
*  Copyright Statement:
*  --------------------
*  This software is protected by Copyright and the information contained
*  herein is confidential. The software may not be copied and the information
*  contained herein may not be used or disclosed except with the written
*  permission of MediaTek Inc. (C) 2016
*
*  BY OPENING THIS FILE, BUYER HEREBY UNEQUIVOCALLY ACKNOWLEDGES AND AGREES
*  THAT THE SOFTWARE/FIRMWARE AND ITS DOCUMENTATIONS ("MEDIATEK SOFTWARE")
*  RECEIVED FROM MEDIATEK AND/OR ITS REPRESENTATIVES ARE PROVIDED TO BUYER ON
*  AN "AS-IS" BASIS ONLY. MEDIATEK EXPRESSLY DISCLAIMS ANY AND ALL WARRANTIES,
*  EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE IMPLIED WARRANTIES OF
*  MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE OR NONINFRINGEMENT.
*  NEITHER DOES MEDIATEK PROVIDE ANY WARRANTY WHATSOEVER WITH RESPECT TO THE
*  SOFTWARE OF ANY THIRD PARTY WHICH MAY BE USED BY, INCORPORATED IN, OR
*  SUPPLIED WITH THE MEDIATEK SOFTWARE, AND BUYER AGREES TO LOOK ONLY TO SUCH
*  THIRD PARTY FOR ANY WARRANTY CLAIM RELATING THERETO. MEDIATEK SHALL ALSO
*  NOT BE RESPONSIBLE FOR ANY MEDIATEK SOFTWARE RELEASES MADE TO BUYER'S
*  SPECIFICATION OR TO CONFORM TO A PARTICULAR STANDARD OR OPEN FORUM.
*
*  BUYER'S SOLE AND EXCLUSIVE REMEDY AND MEDIATEK'S ENTIRE AND CUMULATIVE
*  LIABILITY WITH RESPECT TO THE MEDIATEK SOFTWARE RELEASED HEREUNDER WILL BE,
*  AT MEDIATEK'S OPTION, TO REVISE OR REPLACE THE MEDIATEK SOFTWARE AT ISSUE,
*  OR REFUND ANY SOFTWARE LICENSE FEES OR SERVICE CHARGE PAID BY BUYER TO
*  MEDIATEK FOR SUCH MEDIATEK SOFTWARE AT ISSUE.
*
*  THE TRANSACTION CONTEMPLATED HEREUNDER SHALL BE CONSTRUED IN ACCORDANCE
*  WITH THE LAWS OF THE STATE OF CALIFORNIA, USA, EXCLUDING ITS CONFLICT OF
*  LAWS PRINCIPLES.  ANY DISPUTES, CONTROVERSIES OR CLAIMS ARISING THEREOF AND
*  RELATED THERETO SHALL BE SETTLED BY ARBITRATION IN SAN FRANCISCO, CA, UNDER
*  THE RULES OF THE INTERNATIONAL CHAMBER OF COMMERCE (ICC).
*
*****************************************************************************/

#ifndef CCU_SENSOR_IF_H
#define CCU_SENSOR_IF_H


#include "ccu_sensor_porting.h"
#include "ccu_main/ccu_debug.h"     // for CCU_ERROR_T


/******************************************************************************
* Major and minor version numbers are set in the function table.
******************************************************************************/
#define MAJOR_VERSION               1
#define MINOR_VERSION               0


/******************************************************************************
* Define types of function pointers
******************************************************************************/
typedef kal_uint16 (*PFN_SET_GAIN)(kal_uint16);
typedef void (*PFN_SET_SHUTTER)(kal_uint32);
typedef void (*PFN_SET_FPS)(kal_uint16);
typedef void (*PFN_SET_FLICKER)(kal_bool);
typedef CCU_ERROR_T (*PFN_CONTROL)(SENSOR_SCENARIO_T);
typedef kal_uint32 (*PFN_SET_MAX_FRAMERATE_BY_SCENARIO)(SENSOR_SCENARIO_T, MUINT32);
typedef kal_uint8 (*PFN_GET_GAIN_DELAY)(void);
typedef kal_uint8 (*PFN_GET_SHUTTER_DELAY)(void);
typedef void (*PFN_I2C_WRITE_REG_FUNC)(char* , u16, u16);
typedef void (*PFN_I2C_WRITE_REG_TIMIMG_FUNC)(char* , u16, u16, u16);
typedef kal_uint8 (*PFN_I2C_GET_TRANSFER_LEN)(void);
typedef kal_uint8 (*PFN_I2C_GET_SLAVE_ADDR)(void);
typedef kal_bool (*PFN_I2C_GET_SUPPORTED_BY_CCU)(void);
typedef void (*PFN_LOG)(char*);
typedef void (*PFN_LOG_VALUE)(char*, MINT32);
typedef void (*PFN_ASSERT)(char*, int);
typedef void (*PFN_WARNING)(char*);


/******************************************************************************
* Sensor function table
******************************************************************************/
typedef struct SENSOR_FUNC_TABLE
{
    unsigned char u1MajorVersion;
    unsigned char u1MinorVersion;
    unsigned short u2TableSize;
    PFN_SET_GAIN pfnSetGain;
    PFN_SET_SHUTTER pfnSetShutter;
    PFN_SET_FPS pfnSetFPS;
    PFN_SET_FLICKER pfnSetFlicker;
    PFN_CONTROL pfnControl;
    PFN_SET_MAX_FRAMERATE_BY_SCENARIO pfnSetMaxFramerateByScenario;
    PFN_GET_GAIN_DELAY pfnGetGainDelay;
    PFN_GET_SHUTTER_DELAY pfnGetShutterDelay;
    PFN_I2C_GET_TRANSFER_LEN pfnI2CGetTransferLen;
    PFN_I2C_GET_SUPPORTED_BY_CCU pfnI2CGetSupportedByCCU;
    PFN_I2C_WRITE_REG_FUNC pfnI2CWriteRegFunc;               // Initialized at run time.
	PFN_I2C_WRITE_REG_TIMIMG_FUNC pfnI2CWriteRegTimingFunc;  // Initialized at run time.
    PFN_LOG pfnLog;                                          // Initialized at run time.
    PFN_LOG_VALUE pfnLogValue;                               // Initialized at run time.
    PFN_ASSERT pfnAssert;                                    // Initialized at run time.
    PFN_WARNING pfnWarning;                                  // Initialized at run time.
} SENSOR_FUNC_TABLE_T;

CCU_ERROR_T SENSOR_InitInfo(SENSOR_INFO_IN_T *prIn, SENSOR_INFO_OUT_T *prOut);
#if 0
void SENSOR_Control(WHICH_SENSOR_T eWhichSensor, SENSOR_SCENARIO_T eScenario);
void SENSOR_SetFPS(WHICH_SENSOR_T eWhichSensor, U16 u16FPS);
#endif  // 0
/* u8State in SENSOR_SetFlicker() is either 0 (Disable) or 1 ((Enable flicker). */
void SENSOR_SetFlicker(WHICH_SENSOR_T eWhichSensor, U8 u8State);
void SENSOR_SetGainAndShutter(WHICH_SENSOR_T eWhichSensor, U16 u16Gain, U32 u32Shutter);
I32 SENSOR_SecondInvoke(WHICH_SENSOR_T eWhichSensor);
I32 SENSOR_FirstInvoke(WHICH_SENSOR_T eWhichSensor);
void SENSOR_SetMaxFramerateByScenario(WHICH_SENSOR_T eWhichSensor, MUINT32 framerate);

U32 SENSOR_GetShutterDelay(WHICH_SENSOR_T eWhichSensor);

#endif  // CCU_SENSOR_IF_H

