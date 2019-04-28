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
 * @file    mstar_drv_ic_fw_porting_layer.c
 *
 * @brief   This file defines the interface of touch screen
 *
 *
 */
 
/*=============================================================*/
// INCLUDE FILE
/*=============================================================*/

#include "mstar_drv_ic_fw_porting_layer.h"


/*=============================================================*/
// EXTERN VARIABLE DECLARATION
/*=============================================================*/

#ifdef CONFIG_ENABLE_GESTURE_WAKEUP
extern u32 g_GestureWakeupMode[2];
extern u8 g_GestureWakeupFlag;
#endif //CONFIG_ENABLE_GESTURE_WAKEUP

/*=============================================================*/
// GLOBAL FUNCTION DEFINITION
/*=============================================================*/

void DrvIcFwLyrVariableInitialize(void)
{
//    DBG("*** %s() ***\n", __func__);

    DrvFwCtrlVariableInitialize();
}

void DrvIcFwLyrOptimizeCurrentConsumption(void)
{
//    DBG("*** %s() ***\n", __func__);

    DrvFwCtrlOptimizeCurrentConsumption();
}

u8 DrvIcFwLyrGetChipType(void)
{
//    DBG("*** %s() ***\n", __func__);

    return DrvFwCtrlGetChipType();
}

void DrvIcFwLyrGetCustomerFirmwareVersion(u16 *pMajor, u16 *pMinor, u8 **ppVersion)
{
//    DBG("*** %s() ***\n", __func__);

    DrvFwCtrlGetCustomerFirmwareVersion(pMajor, pMinor, ppVersion);
}

void DrvIcFwLyrGetPlatformFirmwareVersion(u8 **ppVersion)
{
//    DBG("*** %s() ***\n", __func__);

    DrvFwCtrlGetPlatformFirmwareVersion(ppVersion);
}

s32 DrvIcFwLyrUpdateFirmware(u8 szFwData[][1024], EmemType_e eEmemType)
{
//    DBG("*** %s() ***\n", __func__);

    return DrvFwCtrlUpdateFirmware(szFwData, eEmemType);
}	

s32 DrvIcFwLyrUpdateFirmwareBySdCard(const char *pFilePath)
{
//    DBG("*** %s() ***\n", __func__);

    return DrvFwCtrlUpdateFirmwareBySdCard(pFilePath);
}

u32 DrvIcFwLyrIsRegisterFingerTouchInterruptHandler(void)
{
    DBG("*** %s() ***\n", __func__);

    return 1; // Indicate that it is necessary to register interrupt handler with GPIO INT pin when firmware is running on IC
}

void DrvIcFwLyrHandleFingerTouch(u8 *pPacket, u16 nLength)
{
//    DBG("*** %s() ***\n", __func__);

    DrvFwCtrlHandleFingerTouch();
}

//------------------------------------------------------------------------------//

#ifdef CONFIG_ENABLE_GESTURE_WAKEUP

void DrvIcFwLyrOpenGestureWakeup(u32 *pWakeupMode)
{
//    DBG("*** %s() ***\n", __func__);

    DrvFwCtrlOpenGestureWakeup(pWakeupMode);
}	

void DrvIcFwLyrCloseGestureWakeup(void)
{
//    DBG("*** %s() ***\n", __func__);

    DrvFwCtrlCloseGestureWakeup();
}	

#ifdef CONFIG_ENABLE_GESTURE_DEBUG_MODE
void DrvIcFwLyrOpenGestureDebugMode(u8 nGestureFlag)
{
//    DBG("*** %s() ***\n", __func__);

    DrvFwCtrlOpenGestureDebugMode(nGestureFlag);
}

void DrvIcFwLyrCloseGestureDebugMode(void)
{
//	  DBG("*** %s() ***\n", __func__);

    DrvFwCtrlCloseGestureDebugMode();
}
#endif //CONFIG_ENABLE_GESTURE_DEBUG_MODE

#endif //CONFIG_ENABLE_GESTURE_WAKEUP

u32 DrvIcFwLyrReadDQMemValue(u16 nAddr)
{
//	  DBG("*** %s() ***\n", __func__);

    return DrvFwCtrlReadDQMemValue(nAddr);
}

void DrvIcFwLyrWriteDQMemValue(u16 nAddr, u32 nData)
{
//	  DBG("*** %s() ***\n", __func__);

    DrvFwCtrlWriteDQMemValue(nAddr, nData);
}

//------------------------------------------------------------------------------//

#if defined(CONFIG_ENABLE_TOUCH_DRIVER_FOR_MUTUAL_IC) // use for MSG26XXM only
u16 DrvIcFwLyrGetFirmwareMode(void)
{
//    DBG("*** %s() ***\n", __func__);

    return DrvFwCtrlGetFirmwareMode();
}
#endif //CONFIG_ENABLE_TOUCH_DRIVER_FOR_MUTUAL_IC

u16 DrvIcFwLyrChangeFirmwareMode(u16 nMode)
{
//    DBG("*** %s() ***\n", __func__);

    return DrvFwCtrlChangeFirmwareMode(nMode); 
}

void DrvIcFwLyrGetFirmwareInfo(FirmwareInfo_t *pInfo)
{
//    DBG("*** %s() ***\n", __func__);

    DrvFwCtrlGetFirmwareInfo(pInfo);
}

void DrvIcFwLyrRestoreFirmwareModeToLogDataMode(void)
{
//    DBG("*** %s() ***\n", __func__);

    DrvFwCtrlRestoreFirmwareModeToLogDataMode();
}	

//------------------------------------------------------------------------------//

#ifdef CONFIG_UPDATE_FIRMWARE_BY_SW_ID
void DrvIcFwLyrCheckFirmwareUpdateBySwId(void)
{
//    DBG("*** %s() ***\n", __func__);

    DrvFwCtrlCheckFirmwareUpdateBySwId();
}
#endif //CONFIG_UPDATE_FIRMWARE_BY_SW_ID

//------------------------------------------------------------------------------//

#ifdef CONFIG_ENABLE_ITO_MP_TEST

void DrvIcFwLyrCreateMpTestWorkQueue(void)
{
//    DBG("*** %s() ***\n", __func__);
	
    DrvMpTestCreateMpTestWorkQueue();
}

void DrvIcFwLyrScheduleMpTestWork(ItoTestMode_e eItoTestMode)
{
//    DBG("*** %s() ***\n", __func__);
	
    DrvMpTestScheduleMpTestWork(eItoTestMode);
}

s32 DrvIcFwLyrGetMpTestResult(void)
{
//    DBG("*** %s() ***\n", __func__);
	
    return DrvMpTestGetTestResult();
}

void DrvIcFwLyrGetMpTestFailChannel(ItoTestMode_e eItoTestMode, u8 *pFailChannel, u32 *pFailChannelCount)
{
//    DBG("*** %s() ***\n", __func__);
	
    return DrvMpTestGetTestFailChannel(eItoTestMode, pFailChannel, pFailChannelCount);
}

void DrvIcFwLyrGetMpTestDataLog(ItoTestMode_e eItoTestMode, u8 *pDataLog, u32 *pLength)
{
//    DBG("*** %s() ***\n", __func__);

    return DrvMpTestGetTestDataLog(eItoTestMode, pDataLog, pLength);
}

#if defined(CONFIG_ENABLE_TOUCH_DRIVER_FOR_MUTUAL_IC)
void DrvIcFwLyrGetMpTestScope(TestScopeInfo_t *pInfo)
{
//    DBG("*** %s() ***\n", __func__);

    return DrvMpTestGetTestScope(pInfo);
}
#endif //CONFIG_ENABLE_TOUCH_DRIVER_FOR_MUTUAL_IC

#endif //CONFIG_ENABLE_ITO_MP_TEST		

//------------------------------------------------------------------------------//

#ifdef CONFIG_ENABLE_SEGMENT_READ_FINGER_TOUCH_DATA

#if defined(CONFIG_ENABLE_TOUCH_DRIVER_FOR_MUTUAL_IC)
void DrvIcFwLyrGetTouchPacketAddress(u16 *pDataAddress, u16 *pFlagAddress)
{
//    DBG("*** %s() ***\n", __func__);

    return DrvFwCtrlGetTouchPacketAddress(pDataAddress, pFlagAddress);
}
#endif //CONFIG_ENABLE_TOUCH_DRIVER_FOR_MUTUAL_IC

#endif //CONFIG_ENABLE_SEGMENT_READ_FINGER_TOUCH_DATA

//------------------------------------------------------------------------------//

#ifdef CONFIG_ENABLE_PROXIMITY_DETECTION

s32 DrvIcFwLyrEnableProximity(void)
{
//    DBG("*** %s() ***\n", __func__);

    return DrvFwCtrlEnableProximity();
}

s32 DrvIcFwLyrDisableProximity(void)
{
//    DBG("*** %s() ***\n", __func__);

    return DrvFwCtrlDisableProximity();
}

#endif //CONFIG_ENABLE_PROXIMITY_DETECTION

//------------------------------------------------------------------------------//

#ifdef CONFIG_ENABLE_GLOVE_MODE
void DrvIcFwLyrOpenGloveMode(void)
{
//    DBG("*** %s() ***\n", __func__);

    DrvFwCtrlOpenGloveMode();
}

void DrvIcFwLyrCloseGloveMode(void)
{
//	  DBG("*** %s() ***\n", __func__);
    DrvFwCtrlCloseGloveMode();
}

void DrvIcFwLyrGetGloveInfo(u8 *pGloveMode)
{
//    DBG("*** %s() ***\n", __func__);

    DrvFwCtrlGetGloveInfo(pGloveMode);
}
#endif //CONFIG_ENABLE_GLOVE_MODE
//------------------------------------------------------------------------------//


