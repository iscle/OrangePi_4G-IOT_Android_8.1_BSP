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
#if __SIMTRACER__ || __PLATFORM__
#include <stdio.h>
#endif

enum CCU_MSDK_SENSOR_FEATURE_ENUM
{
    CCU_SENSOR_FEATURE_SET_ESHUTTER = 0,
    CCU_SENSOR_FEATURE_SET_GAIN        ,
    CCU_SENSOR_FEATURE_SET_SHUTTER_FRAME_TIME ,
    CCU_SENSOR_FEATURE_SET_FLICKER            ,
    CCU_SENSOR_FEATURE_SET_FPS                ,
    CCU_SENSOR_FEATURE_GET_FPS                ,
    CCU_SENSOR_FEATURE_GET_GDELAY             ,
    CCU_SENSOR_FEATURE_GET_SDELAY             ,
    CCU_SENSOR_FEATURE_GET_FDELAY             ,
    CCU_SENSOR_FEATURE_GET_TXLEN              ,
    CCU_SENSOR_FEATURE_GET_MIN_FL             ,
    CCU_SENSOR_FEATURE_GET_CCU_SUPPORT        ,
    CCU_SENSOR_FEATURE_GET_PCLK_LINELENGTH    ,
    CCU_SENSOR_FEATURE_SET_MIN_FL             ,
    CCU_SENSOR_FEATURE_GET_VBLANKING
};

/******************************************************************************
* Define types of function pointers
******************************************************************************/
typedef void (*PFN_SENSOR_DRIVER_CTRL)(enum CCU_MSDK_SENSOR_FEATURE_ENUM cmdType, void*, void*);
typedef kal_uint32 (*PFN_TIME_TO_LINE_COUNT)(kal_uint32);
typedef CCU_ERROR_T (*PFN_CONTROL)(SENSOR_SCENARIO_T);
typedef kal_uint32 (*PFN_SET_MAX_FRAMERATE_BY_SCENARIO)(SENSOR_SCENARIO_T, MUINT32);
typedef void (*PFN_I2C_WRITE_REG_FUNC)(char* , u16, u16);
typedef void (*PFN_I2C_WRITE_REG_TIMIMG_FUNC)(char* , u16, u16, u16, u16);
typedef void (*PFN_I2C_READ_REG_TIMING_FUNC)(char *, u16 , char *, u16, u16, u16);
typedef void (*PFN_LOG)(char*);
typedef void (*PFN_LOG_VALUE)(char*, MINT32);
typedef void (*PFN_ASSERT)(char*, int);
typedef void (*PFN_WARNING)(char*);


/******************************************************************************
* Sensor function table
******************************************************************************/
typedef struct SENSOR_FUNC_TABLE
{
    unsigned int u2TableSize;
    PFN_SENSOR_DRIVER_CTRL pfnSensorDriverCtrl;
    PFN_TIME_TO_LINE_COUNT pfnTimeToLineCount;
    PFN_CONTROL pfnControl;
    PFN_SET_MAX_FRAMERATE_BY_SCENARIO pfnSetMaxFramerateByScenario;
    PFN_I2C_WRITE_REG_FUNC pfnI2CWriteRegFunc;               // Initialized at run time.
	PFN_I2C_WRITE_REG_TIMIMG_FUNC pfnI2CWriteRegTimingFunc;  // Initialized at run time.
    PFN_I2C_READ_REG_TIMING_FUNC pfnI2CReadRegTimingFunc;
    PFN_LOG pfnLog;                                          // Initialized at run time.
    PFN_LOG_VALUE pfnLogValue;                               // Initialized at run time.
    PFN_ASSERT pfnAssert;                                    // Initialized at run time.
    PFN_WARNING pfnWarning;                                  // Initialized at run time.
} SENSOR_FUNC_TABLE_T;

typedef enum I2C_DRV_STATUS
{
    I2C_DRV_STAT_IDLE = 0,
    I2C_DRV_STAT_READY   ,
    I2C_DRV_STAT_WR      ,
    I2C_DRV_STAT_RD      ,
    I2C_DRV_STAT_DONE    ,
    I2C_DRV_STAT_DROP    ,
    I2C_DRV_STAT_ERR     
} I2C_DRV_STATUS_T;

typedef enum SENSOR_USER
{
    SENSOR_USER_DONTCARE   = 0x00,
    SENSOR_USER_MAIN_CAM   = 0x01,
    SENSOR_USER_SUB_CAM    = 0x02,
    SENSOR_USER_CAMA       = 0x03,
    SENSOR_USER_CAMB       = 0x04,
    SENSOR_USER_MAX        = 0xFF
} SENSOR_USER_T;

typedef enum SENSOR_IF_FUNC
{
    SENSOR_IF_INIT         = 0,
    SENSOR_IF_FIRSTTRIGGER    ,
    SENSOR_IF_SECONDTRIGGER   ,
    SENSOR_IF_STATUS          ,
    SENSOR_IF_GETSDELAY       ,
    SENSOR_IF_SETMAXFRAMERATE ,
    SENSOR_IF_SETGNS          ,
    SENSOR_IF_SETFLICKER      ,
    SENSOR_IF_SHUTTERTOLL
} SENSOR_IF_FUNC_T;

typedef struct SENSOR_IF_SETGNS_PARA
{
    uint32_t shutter;
    uint16_t gain;
} SENSOR_IF_SETGNS_PARA_T;

typedef enum SENSOR_STATUS_CTRL
{
    SENSOR_STATUS_CTRL_ISR = 0,
    SENSOR_STATUS_CTRL_CCU
} SENSOR_STATUS_CTRL_T;

uint32_t SENSOR_IF(SENSOR_USER_T eUser, SENSOR_IF_FUNC_T eFunc, void *paraIn, void *paraOut);
CCU_ERROR_T SENSOR_IF_Init(SENSOR_INFO_IN_T *prIn, SENSOR_INFO_OUT_T *prOut);
void SENSOR_IF_FirstTrigger(uint32_t *paraOut);
void SENSOR_IF_SecondTrigger(uint32_t *paraOut);
void SENSOR_IF_CheckI2CStatus(SENSOR_STATUS_CTRL_T *paraIn , I2C_DRV_STATUS_T *paraOut);
void SENSOR_IF_GetShutterDelay(uint32_t *paraOut);
void SENSOR_IF_SetMaxFramerateByScenario(uint32_t *paraIn);
void SENSOR_IF_SetGainAndShutter(SENSOR_IF_SETGNS_PARA_T *paraIn);
void SENSOR_IF_SetFlicker(uint8_t *paraIn);
uint16_t SENSOR_IF_ShutterToLineLength(uint32_t *paraIn, uint16_t *paraOut);

void SENSOR_Vsync(SENSOR_USER_T eUser);
#endif  // CCU_SENSOR_IF_H

