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

#ifndef CCU_SENSOR_PORTING_H
#define CCU_SENSOR_PORTING_H


#include "ccu_ext_interface/ccu_sensor_extif.h"
#include "imgsensor/ccu_sensor_fctrl.h"
/******************************************************************************
* For easier sensor customization/porting.
* To save one byte of memory space, define DEFINE_SPINLOCK to be an empty macro.
* Even though macro parameters are not used, it may be defined to be a
* type-casting expression to eliminate build warning.
******************************************************************************/
#define DEFINE_SPINLOCK(X)              /* Empty! or U8 X = 0 */
#define LOG_INF(X, ...)                 /* Empty! */
#define spin_lock(X)                    /* Empty! */
#define spin_lock_irq(X)                /* Empty! */
#define spin_lock_irqsave(X, Y)         /* Empty! or ((void*)Y) */
#define spin_unlock(X)                  /* Empty! */
#define spin_unlock_irq(X)              /* Empty! */
#define spin_unlock_irqrestore(X, Y)    /* Empty! or ((void*)Y) */
#define SENSORDB(X, ...)                /* Empty! */
#define mdelay(X)                       /* Empty! */
// #define KAL_FALSE                       0
// #define KAL_TRUE                        1


/******************************************************************************
* For easier sensor customization/porting.
* The KAL data types are defined in
* "vendor/mediatek/proprietary/external/mbimd/include/kal.h".
******************************************************************************/
#ifndef __CCU_SENSOR_PORTING_TYPES__
#define __CCU_SENSOR_PORTING_TYPES__
typedef long                LONG;
typedef unsigned char       UBYTE;
typedef short               SHORT;
typedef signed char         S8;
typedef signed short        S16;
typedef signed int          S32;
typedef signed long long    S64;
typedef unsigned char       UINT8;
typedef unsigned short      UINT16;
typedef unsigned int        UINT32;
typedef unsigned short      USHORT;
typedef signed char         INT8;
typedef signed short        INT16;
//typedef signed int          INT32;
typedef unsigned int        DWORD;
//typedef void                VOID;
typedef unsigned char       BYTE;
typedef float               FLOAT;
// typedef unsigned int            *UINT32P;
// typedef volatile unsigned short *UINT16P;
// typedef volatile unsigned char  *UINT8P;
// typedef unsigned char           *U8P;
typedef unsigned char       kal_bool;
typedef unsigned char       kal_uint8;
typedef unsigned short      kal_uint16;
typedef unsigned int        kal_uint32;
typedef unsigned long long  kal_uint64;
typedef char                kal_int8;
typedef short               kal_int16;
typedef int                 kal_int32;
typedef long long           kal_int64;
typedef char                kal_char;
#define KAL_FALSE           (0)
#define KAL_TRUE            (1)

typedef unsigned char       u8;
typedef unsigned short      u16;
typedef unsigned int        u32;
#endif

/******************************************************************************
******************************************************************************/
#define BASEGAIN                    0x40


/******************************************************************************
* Function prototypes
*
* These functions can be used in sensor drivers and they will be directed to the
* corresponding functions in the CCU Main binary.
******************************************************************************/
int iWriteRegI2C(char *pu1SendData , u16 u2DataSize, u16 u2I2CID);
int iWriteRegI2CTiming(char *pu1SendData , u16 wrLength, u16 wrPerCycle, u16 u2I2CID, u16 timing);
void iReadRegI2CTiming(char *wData, u16 wLen, char *rData, u16 rLen, u16 u2I2CID, u16 timing);
void MyLog(char *msg);
void MyLogValue(char *name, MINT32 value);
void MyAssert(char *msg, int errno);
void MyWarning(char *msg);


/******************************************************************************
******************************************************************************/
#define GENERATE_FUNC_TABLE_CODE()                                                                        \
    /*** ConvertedShutter = PC_CLK / LINE_LENGTH * shutter / 10^6 ***/                                    \
    static kal_uint32 time_to_line_count(kal_uint32 mtime)                                                \
    {                                                                                                     \
        unsigned long long line_count = imgsensor.pclk / 1000000;                                         \
        line_count *= mtime;                                                                              \
        line_count /= imgsensor.line_length;                                                              \
        return (kal_uint32)line_count;                                                                    \
    }                                                                                                     \
                                                                                                          \
    static void sensor_driver_ctrl(enum CCU_MSDK_SENSOR_FEATURE_ENUM cmdType, void *prIn, void *prOut)    \
    {                                                                                                     \
        switch(cmdType)                                                                                   \
        {                                                                                                 \
            case CCU_SENSOR_FEATURE_SET_ESHUTTER:                                                         \
                MyLog("CCU_SENSOR_FEATURE_SET_ESHUTTER");                                                 \
                feature_control(SENSOR_FEATURE_SET_ESHUTTER, prIn, NULL);                                 \
                break;                                                                                    \
            case CCU_SENSOR_FEATURE_SET_GAIN:                                                             \
                MyLog("CCU_SENSOR_FEATURE_SET_GAIN");                                                     \
                feature_control(SENSOR_FEATURE_SET_GAIN, prIn, NULL);                                     \
                break;                                                                                    \
            case CCU_SENSOR_FEATURE_SET_FLICKER:                                                          \
                imgsensor.autoflicker_en = (bool)prIn;                                                    \
                break;                                                                                    \
            case CCU_SENSOR_FEATURE_SET_FPS:                                                              \
                imgsensor.current_fps = (uint16_t)prIn;                                                   \
                break;                                                                                    \
            case CCU_SENSOR_FEATURE_GET_FPS:                                                              \
                *(uint16_t *)prOut = imgsensor.current_fps;                                               \
                break;                                                                                    \
            case CCU_SENSOR_FEATURE_GET_GDELAY:                                                           \
                *(uint8_t *)prOut = imgsensor.u8GainDelay;                                                \
                break;                                                                                    \
            case CCU_SENSOR_FEATURE_GET_SDELAY:                                                           \
                *(uint8_t *)prOut = imgsensor.u8ShutterDelay;                                             \
                break;                                                                                    \
            case CCU_SENSOR_FEATURE_GET_FDELAY:                                                           \
                *(uint8_t *)prOut = 1;/*imgsensor_info.frame_time_delay_frame;*/                          \
                break;                                                                                    \
            case CCU_SENSOR_FEATURE_GET_TXLEN:                                                            \
                *(uint16_t *)prOut = imgsensor.u8I2CTransferLength;                                       \
                break;                                                                                    \
            case CCU_SENSOR_FEATURE_GET_MIN_FL:                                                           \
                *(uint16_t *)prOut = imgsensor.min_frame_length;                                          \
                break;                                                                                    \
            case CCU_SENSOR_FEATURE_GET_CCU_SUPPORT:                                                      \
                *(uint8_t *)prOut = (imgsensor.u8I2CTransferLength != 0);                                 \
                break;                                                                                    \
            default:                                                                                      \
                MyLog("CCU_SENSOR_FEATURE_DEFAULT");                                                      \
                break;                                                                                    \
        }                                                                                                 \
    }                                                                                                     \
                                                                                                          \
    /* Functions cannot be removed without recompilation of the Main */                                   \
    /* binary. New functions must be appended to the end of this table. */                                \
    static SENSOR_FUNC_TABLE_T func_table =                                                               \
    {                                                                                                     \
        sizeof(func_table),                                                                               \
        sensor_driver_ctrl,                                                                               \
        time_to_line_count,                                                                               \
        control,                                                                                          \
        set_max_framerate_by_scenario,                                                                    \
        (PFN_I2C_WRITE_REG_FUNC)0xFFFFFFFF,         /* To be fixed at run time */                         \
        (PFN_I2C_WRITE_REG_TIMIMG_FUNC)0xFFFFFFFF,  /* To be fixed at run time */                         \
        (PFN_I2C_READ_REG_TIMING_FUNC)0xFFFFFFFF,   /* To be fixed at run time */                         \
        (PFN_LOG)0xFFFFFFFF,                        /* To be fixed at run time */                         \
        (PFN_LOG_VALUE)0xFFFFFFFF,                  /* To be fixed at run time */                         \
        (PFN_ASSERT)0xFFFFFFFF,                     /* To be fixed at run time */                         \
        (PFN_WARNING)0xFFFFFFFF                     /* To be fixed at run time */                         \
    };                                                                                                    \
                                                                                                          \
    static int iWriteRegI2C(char *pu1Data , u16 u2Size, u16 u2I2CID)                                      \
    {                                                                                                     \
        func_table.pfnI2CWriteRegFunc(pu1Data, u2Size, u2I2CID);                                          \
        return false;                                                                                     \
    }                                                                                                     \
	                                                                                                      \
    static int iWriteRegI2CTiming(char *pu1Data, u16 u2Size, u16 wrCycle, u16 u2I2CID, u16 timing)        \
    {                                                                                                     \
        func_table.pfnI2CWriteRegTimingFunc(pu1Data, u2Size, wrCycle, u2I2CID, timing);                   \
		return false;                                                                                     \
    }                                                                                                     \
    static void iReadRegI2CTiming(char *wData, u16 wLen, char *rData, u16 rLen, u16 u2I2CID, u16 timing)  \
    {                                                                                                     \
        func_table.pfnI2CReadRegTimingFunc(wData, wLen, rData, rLen, u2I2CID, timing);                    \
    }                                                                                                     \
                                                                                                          \
    static void MyLog(char *msg)                                                                          \
    {                                                                                                     \
        func_table.pfnLog(msg);                                                                           \
    }                                                                                                     \
                                                                                                          \
    static void MyLogValue(char *name, MINT32 value)                                                      \
    {                                                                                                     \
        func_table.pfnLogValue(name, value);                                                              \
    }                                                                                                     \
                                                                                                          \
    static void MyAssert(char *msg, int errno)                                                            \
    {                                                                                                     \
        func_table.pfnAssert(msg, errno);                                                                 \
    }                                                                                                     \
                                                                                                          \
    static void MyWarning(char *msg)                                                                      \
    {                                                                                                     \
        func_table.pfnWarning(msg);                                                                       \
    }                                                                                                     \
                                                                                                          \
    /* Do NOT define GetFuncTable() as a static function!!! */                                            \
    void __attribute__ ((section(".text.getfunctable")))                                                  \
        GetFuncTable(SENSOR_FUNC_TABLE_T **ppFuncTable)                                                   \
    {                                                                                                     \
        *ppFuncTable = &func_table;                                                                       \
    }


#endif  // CCU_SENSOR_PORTING_H

