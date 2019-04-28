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
 * @file    mstar_drv_self_fw_control.c
 *
 * @brief   This file defines the interface of touch screen
 *
 *
 */
 
/*=============================================================*/
// INCLUDE FILE
/*=============================================================*/

#include "mstar_drv_self_fw_control.h"
#include "mstar_drv_utility_adaption.h"
#include "mstar_drv_platform_porting_layer.h"

#ifdef CONFIG_ONTIM_DSM	

#include <ontim/ontim_dsm.h>

extern struct dsm_client *msg2238a_dsm_client;
extern char msg2238a_vendor_name[];
#endif

#if defined(CONFIG_ENABLE_TOUCH_DRIVER_FOR_SELF_IC)

/*=============================================================*/
// EXTERN VARIABLE DECLARATION
/*=============================================================*/

extern u32 SLAVE_I2C_ID_DBBUS;
extern u32 SLAVE_I2C_ID_DWI2C;

#ifdef CONFIG_TP_HAVE_KEY
extern const int g_TpVirtualKey[];

#ifdef CONFIG_TOUCH_DRIVER_RUN_ON_MTK_PLATFORM
#ifdef CONFIG_ENABLE_REPORT_KEY_WITH_COORDINATE
extern const int g_TpVirtualKeyDimLocal[][4];
#endif //CONFIG_ENABLE_REPORT_KEY_WITH_COORDINATE
#endif //CONFIG_TOUCH_DRIVER_RUN_ON_MTK_PLATFORM
#endif //CONFIG_TP_HAVE_KEY

#if defined(CONFIG_TOUCH_DRIVER_RUN_ON_SPRD_PLATFORM) || defined(CONFIG_TOUCH_DRIVER_RUN_ON_QCOM_PLATFORM)
#ifdef CONFIG_ENABLE_PROXIMITY_DETECTION
extern struct input_dev *g_ProximityInputDevice;
#endif //CONFIG_ENABLE_PROXIMITY_DETECTION
#endif //CONFIG_TOUCH_DRIVER_RUN_ON_SPRD_PLATFORM || CONFIG_TOUCH_DRIVER_RUN_ON_QCOM_PLATFORM

extern struct input_dev *g_InputDevice;

extern u8 g_FwData[MAX_UPDATE_FIRMWARE_BUFFER_SIZE][1024];
extern u32 g_FwDataCount;

extern struct mutex g_Mutex;

extern u16 FIRMWARE_MODE_UNKNOWN_MODE;
extern u16 FIRMWARE_MODE_DEMO_MODE;
extern u16 FIRMWARE_MODE_DEBUG_MODE;
extern u16 FIRMWARE_MODE_RAW_DATA_MODE;

extern struct kobject *g_TouchKObj;
extern u8 g_IsSwitchModeByAPK;

extern u8 IS_FIRMWARE_DATA_LOG_ENABLED;
extern u8 IS_FORCE_TO_UPDATE_FIRMWARE_ENABLED;

#ifdef CONFIG_ENABLE_GESTURE_WAKEUP
#ifdef CONFIG_ENABLE_GESTURE_DEBUG_MODE
extern struct kobject *g_GestureKObj;
#endif //CONFIG_ENABLE_GESTURE_DEBUG_MODE
#endif //CONFIG_ENABLE_GESTURE_WAKEUP

#ifdef CONFIG_ENABLE_COUNT_REPORT_RATE
extern u32 g_IsEnableReportRate;
extern u32 g_InterruptCount;
extern u32 g_ValidTouchCount;

extern struct timeval g_StartTime;
#endif //CONFIG_ENABLE_COUNT_REPORT_RATE

/*=============================================================*/
// LOCAL VARIABLE DEFINITION
/*=============================================================*/

static u8 _gTpVendorCode[3] = {0};

//static u8 _gDwIicInfoData[1024];
static u8 _gOneDimenFwData[MSG22XX_FIRMWARE_MAIN_BLOCK_SIZE*1024+MSG22XX_FIRMWARE_INFO_BLOCK_SIZE] = {0}; // used for MSG22XX

#ifdef CONFIG_UPDATE_FIRMWARE_BY_SW_ID
/*
 * Note.
 * Please modify the name of the below .h depends on the vendor TP that you are using.
 */
//#include "msg21xxa_xxxx_update_bin.h" // for MSG21xxA
//#include "msg21xxa_yyyy_update_bin.h"

#include "msg22xx_xxxx_update_bin.h" // for MSG22xx
//#include "msg22xx_yyyy_update_bin.h"

static u32 _gUpdateRetryCount = UPDATE_FIRMWARE_RETRY_COUNT;
static struct work_struct _gUpdateFirmwareBySwIdWork;
static struct workqueue_struct *_gUpdateFirmwareBySwIdWorkQueue = NULL;

static u32 _gIsUpdateInfoBlockFirst = 0;
#endif //CONFIG_UPDATE_FIRMWARE_BY_SW_ID

#ifdef CONFIG_ENABLE_GESTURE_WAKEUP
static u32 _gGestureWakeupValue[2] = {0};
#endif //CONFIG_ENABLE_GESTURE_WAKEUP

#ifdef CONFIG_ENABLE_CHARGER_DETECTION
static u8 _gChargerPlugIn = 0;
#endif //CONFIG_ENABLE_CHARGER_DETECTION

#ifdef CONFIG_ENABLE_TYPE_B_PROTOCOL
static u8 _gCurrPress[MAX_TOUCH_NUM] = {0};
static u8 _gPrevTouchStatus = 0;
#endif //CONFIG_ENABLE_TYPE_B_PROTOCOL

static u8 _gIsDisableFinagerTouch = 0;

/*=============================================================*/
// GLOBAL VARIABLE DEFINITION
/*=============================================================*/

u8 g_ChipType = 0;
u8 g_DemoModePacket[DEMO_MODE_PACKET_LENGTH] = {0};

FirmwareInfo_t g_FirmwareInfo;
u8 g_LogModePacket[DEBUG_MODE_PACKET_LENGTH] = {0};
u16 g_FirmwareMode; 

#ifdef CONFIG_ENABLE_GESTURE_WAKEUP

#if defined(CONFIG_ENABLE_GESTURE_DEBUG_MODE)
u8 _gGestureWakeupPacket[GESTURE_DEBUG_MODE_PACKET_LENGTH] = {0};
#elif defined(CONFIG_ENABLE_GESTURE_INFORMATION_MODE)
u8 _gGestureWakeupPacket[GESTURE_WAKEUP_INFORMATION_PACKET_LENGTH] = {0};
#else
u8 _gGestureWakeupPacket[DEMO_MODE_PACKET_LENGTH] = {0}; // for MSG21XXA : packet length(DEMO_MODE_PACKET_LENGTH) , for MSG22XX : packet length(GESTURE_WAKEUP_PACKET_LENGTH)
#endif //CONFIG_ENABLE_GESTURE_DEBUG_MODE

#ifdef CONFIG_ENABLE_GESTURE_DEBUG_MODE
u8 g_GestureDebugFlag = 0x00;
u8 g_GestureDebugMode = 0x00;
u8 g_LogGestureDebug[GESTURE_DEBUG_MODE_PACKET_LENGTH] = {0};
#endif // CONFIG_ENABLE_GESTURE_DEBUG_MODE

#ifdef CONFIG_ENABLE_GESTURE_INFORMATION_MODE
u32 g_LogGestureInfor[GESTURE_WAKEUP_INFORMATION_PACKET_LENGTH] = {0};
#endif //CONFIG_ENABLE_GESTURE_INFORMATION_MODE

#ifdef CONFIG_SUPPORT_64_TYPES_GESTURE_WAKEUP_MODE // support at most 64 types of gesture wakeup mode
u32 g_GestureWakeupMode[2] = {0xFFFFFFFF, 0xFFFFFFFF};
#else                                              // support at most 16 types of gesture wakeup mode
u32 g_GestureWakeupMode[2] = {0x00000001, 0x00000000};
#endif //CONFIG_SUPPORT_64_TYPES_GESTURE_WAKEUP_MODE

u8 g_GestureWakeupFlag = 0;
#endif //CONFIG_ENABLE_GESTURE_WAKEUP

#ifdef CONFIG_ENABLE_PROXIMITY_DETECTION
u8 g_EnableTpProximity = 0;
#if defined(CONFIG_TOUCH_DRIVER_RUN_ON_SPRD_PLATFORM) || defined(CONFIG_TOUCH_DRIVER_RUN_ON_QCOM_PLATFORM)
u8 g_FaceClosingTp = 0; // for QCOM platform -> 1 : close to, 0 : far away 
#elif defined(CONFIG_TOUCH_DRIVER_RUN_ON_MTK_PLATFORM)
u8 g_FaceClosingTp = 1; // for MTK platform -> 0 : close to, 1 : far away 
#endif
#endif //CONFIG_ENABLE_PROXIMITY_DETECTION

#ifdef CONFIG_ENABLE_CHARGER_DETECTION
u8 g_ForceUpdate = 0;
#endif //CONFIG_ENABLE_CHARGER_DETECTION

u8 g_IsUpdateFirmware = 0x00;

/*=============================================================*/
// LOCAL FUNCTION DECLARATION
/*=============================================================*/

#ifdef CONFIG_UPDATE_FIRMWARE_BY_SW_ID
static void _DrvFwCtrlUpdateFirmwareBySwIdDoWork(struct work_struct *pWork);
#endif //CONFIG_UPDATE_FIRMWARE_BY_SW_ID

#ifdef CONFIG_ENABLE_GESTURE_WAKEUP
#ifdef CONFIG_ENABLE_GESTURE_INFORMATION_MODE
static void _DrvFwCtrlCoordinate(u8 *pRawData, u32 *pTranX, u32 *pTranY);
#endif //CONFIG_ENABLE_GESTURE_INFORMATION_MODE
#endif //CONFIG_ENABLE_GESTURE_WAKEUP

static s32 _DrvFwCtrlMsg22xxUpdateFirmware(u8 szFwData[][1024], EmemType_e eEmemType);

/*=============================================================*/
// LOCAL FUNCTION DEFINITION
/*=============================================================*/
#if 0
static void _DrvFwCtrlEraseEmemC32(void)
{
    DBG("*** %s() ***\n", __func__);

    /////////////////////////
    //Erase  all
    /////////////////////////
    
    // Enter gpio mode
    RegSet16BitValue(0x161E, 0xBEAF);

    // Before gpio mode, set the control pin as the orginal status
    RegSet16BitValue(0x1608, 0x0000);
    RegSetLByteValue(0x160E, 0x10);
    mdelay(10); 

    // ptrim = 1, h'04[2]
    RegSetLByteValue(0x1608, 0x04);
    RegSetLByteValue(0x160E, 0x10);
    mdelay(10); 

    // ptm = 6, h'04[12:14] = b'110
    RegSetLByteValue(0x1609, 0x60);
    RegSetLByteValue(0x160E, 0x10);

    // pmasi = 1, h'04[6]
    RegSetLByteValue(0x1608, 0x44);
    // pce = 1, h'04[11]
    RegSetLByteValue(0x1609, 0x68);
    // perase = 1, h'04[7]
    RegSetLByteValue(0x1608, 0xC4);
    // pnvstr = 1, h'04[5]
    RegSetLByteValue(0x1608, 0xE4);
    // pwe = 1, h'04[9]
    RegSetLByteValue(0x1609, 0x6A);
    // trigger gpio load
    RegSetLByteValue(0x160E, 0x10);
}
#endif
static void _DrvFwCtrlEraseEmemC33(EmemType_e eEmemType)
{
    DBG("*** %s() ***\n", __func__);

    // Stop mcu
    RegSet16BitValue(0x0FE6, 0x0001);

    // Disable watchdog
    RegSetLByteValue(0x3C60, 0x55);
    RegSetLByteValue(0x3C61, 0xAA);

    // Set PROGRAM password
    RegSetLByteValue(0x161A, 0xBA);
    RegSetLByteValue(0x161B, 0xAB);

    // Clear pce
    RegSetLByteValue(0x1618, 0x80);

    if (eEmemType == EMEM_ALL)
    {
        RegSetLByteValue(0x1608, 0x10); //mark
    }

    RegSetLByteValue(0x1618, 0x40);
    mdelay(10);

    RegSetLByteValue(0x1618, 0x80);

    // erase trigger
    if (eEmemType == EMEM_MAIN)
    {
        RegSetLByteValue(0x160E, 0x04); //erase main
    }
    else
    {
        RegSetLByteValue(0x160E, 0x08); //erase all block
    }
}

static void _DrvFwCtrlMsg22xxGetTpVendorCode(u8 *pTpVendorCode)
{
    DBG("*** %s() ***\n", __func__);

    if (g_ChipType == CHIP_TYPE_MSG22XX)
    {
        u16 nRegData1, nRegData2;

        DrvPlatformLyrTouchDeviceResetHw();
    
        DbBusEnterSerialDebugMode();
        DbBusStopMCU();
        DbBusIICUseBus();
        DbBusIICReshape();
        mdelay(100);
        
        // Stop mcu
        RegSetLByteValue(0x0FE6, 0x01); 

        // Stop watchdog
        RegSet16BitValue(0x3C60, 0xAA55);

        // RIU password
        RegSet16BitValue(0x161A, 0xABBA); 

        RegSet16BitValue(0x1600, 0xC1E9); // Set start address for tp vendor code on info block(Actually, start reading from 0xC1E8)
    
        // Enable burst mode
//        RegSet16BitValue(0x160C, (RegGet16BitValue(0x160C) | 0x01));

        RegSetLByteValue(0x160E, 0x01); 

        nRegData1 = RegGet16BitValue(0x1604);
        nRegData2 = RegGet16BitValue(0x1606);

        pTpVendorCode[0] = ((nRegData1 >> 8) & 0xFF);
        pTpVendorCode[1] = (nRegData2 & 0xFF);
        pTpVendorCode[2] = ((nRegData2 >> 8) & 0xFF);

        DBG("pTpVendorCode[0] = 0x%x , %c \n", pTpVendorCode[0], pTpVendorCode[0]); 
        DBG("pTpVendorCode[1] = 0x%x , %c \n", pTpVendorCode[1], pTpVendorCode[1]); 
        DBG("pTpVendorCode[2] = 0x%x , %c \n", pTpVendorCode[2], pTpVendorCode[2]); 
        
        // Clear burst mode
//        RegSet16BitValue(0x160C, RegGet16BitValue(0x160C) & (~0x01));      

        RegSet16BitValue(0x1600, 0x0000); 

        // Clear RIU password
        RegSet16BitValue(0x161A, 0x0000); 

        DbBusIICNotUseBus();
        DbBusNotStopMCU();
        DbBusExitSerialDebugMode();

        DrvPlatformLyrTouchDeviceResetHw();
    }
}

static u16 _DrvFwCtrlMsg22xxGetTrimByte1(void)
{
    u16 nRegData = 0;
    u16 nTrimByte1 = 0;
    
    DBG("*** %s() ***\n", __func__);

    RegSet16BitValue(0x161E, 0xBEAF); 
    RegSet16BitValue(0x1608, 0x0006); 
    RegSet16BitValue(0x160E, 0x0010); 
    RegSet16BitValue(0x1608, 0x1006); 
    RegSet16BitValue(0x1600, 0x0001); 
    RegSet16BitValue(0x160E, 0x0010);
    
    mdelay(10);
    
    RegSet16BitValue(0x1608, 0x1846);
    RegSet16BitValue(0x160E, 0x0010);
    
    mdelay(10);

    nRegData = RegGet16BitValue(0x1624);
    nRegData = nRegData & 0xFF;

    RegSet16BitValue(0x161E, 0x0000);

    nTrimByte1 = nRegData;

    DBG("nTrimByte1 = 0x%X ***\n", nTrimByte1);
    
    return nTrimByte1;
}

static void _DrvFwCtrlMsg22xxChangeVoltage(void)
{
    u16 nTrimValue = 0;
    u16 nNewTrimValue = 0;
    u16 nTempValue = 0;
    
    DBG("*** %s() ***\n", __func__);

    RegSet16BitValue(0x1840, 0xA55A);
	
    udelay(1000); // delay 1 ms

    nTrimValue = RegGet16BitValue(0x1820);

    udelay(1000); // delay 1 ms
    
    nTrimValue = nTrimValue & 0x1F;
    nTempValue = 0x1F & nTrimValue;
    nNewTrimValue = (nTempValue + 0x07);
    
    if (nNewTrimValue >= 0x20)
    {
        nNewTrimValue = nNewTrimValue - 0x20;
    }
    else
    {
        nNewTrimValue = nNewTrimValue;
    }
    
    if ((nTempValue & 0x10) != 0x10)
    {
        if (nNewTrimValue >= 0x0F && nNewTrimValue < 0x1F)
        {
            nNewTrimValue = 0x0F;
        }
        else
        {
            nNewTrimValue = nNewTrimValue;
        }
    }

    RegSet16BitValue(0x1842, nNewTrimValue);

    udelay(1000); // delay 1 ms

    RegSet16BitValueOn(0x1842, BIT5);

    udelay(1000); // delay 1 ms
}

static void _DrvFwCtrlMsg22xxRestoreVoltage(void)
{
    DBG("*** %s() ***\n", __func__);

    RegSet16BitValueOff(0x1842, BIT5);

    udelay(1000); // delay 1 ms

    RegSet16BitValue(0x1840, 0x0000);

    udelay(1000); // delay 1 ms
}

static void _DrvFwCtrlMsg22xxEraseEmem(EmemType_e eEmemType)
{
    u32 i = 0;
    u32 nEraseCount = 0;
    u32 nMaxEraseTimes = 0;
    u32 nTimeOut = 0;
    u16 nRegData = 0;
    u16 nTrimByte1 = 0;
    
    DBG("*** %s() eEmemType = %d ***\n", __func__, eEmemType);

    DbBusEnterSerialDebugMode();
    DbBusStopMCU();
    DbBusIICUseBus();
    DbBusIICReshape();

    DBG("Erase start\n");

    // Stop mcu
    RegSet16BitValue(0x0FE6, 0x0001);

    nTrimByte1 = _DrvFwCtrlMsg22xxGetTrimByte1();
    
    _DrvFwCtrlMsg22xxChangeVoltage();

    // Disable watchdog
    RegSetLByteValue(0x3C60, 0x55);
    RegSetLByteValue(0x3C61, 0xAA);

    // Set PROGRAM password
    RegSetLByteValue(0x161A, 0xBA);
    RegSetLByteValue(0x161B, 0xAB);

    if (nTrimByte1 == 0xCA)
    {
        nMaxEraseTimes = MAX_ERASE_EFLASH_TIMES;
    }
    else
    {
        nMaxEraseTimes = 1;	
    }

    for (nEraseCount = 0; nEraseCount < nMaxEraseTimes; nEraseCount ++)
    {
        if (eEmemType == EMEM_ALL) // 48KB + 512Byte
        {
            DBG("Erase all block %d times\n", nEraseCount);

            // Clear pce
            RegSetLByteValue(0x1618, 0x80);
            mdelay(100);

            // Chip erase
            RegSet16BitValue(0x160E, BIT3);

            DBG("Wait erase done flag\n");

            while (1) // Wait erase done flag
            {
                nRegData = RegGet16BitValue(0x1610); // Memory status
                nRegData = nRegData & BIT1;
            
                DBG("Wait erase done flag nRegData = 0x%x\n", nRegData);

                if (nRegData == BIT1)
                {
                    break;		
                }

                mdelay(50);

                if ((nTimeOut ++) > 30)
                {
                    DBG("Erase all block %d times failed. Timeout.\n", nEraseCount);

                    if (nEraseCount == (nMaxEraseTimes - 1))
                    {
                        goto EraseEnd;
                    }
                }
            }
        }
        else if (eEmemType == EMEM_MAIN) // 48KB (32+8+8)
        {
            DBG("Erase main block %d times\n", nEraseCount);

            for (i = 0; i < 3; i ++)
            {
                // Clear pce
                RegSetLByteValue(0x1618, 0x80);
                mdelay(10);
 
                if (i == 0)
                {
                    RegSet16BitValue(0x1600, 0x0000);
                }
                else if (i == 1)
                {
                    RegSet16BitValue(0x1600, 0x8000);
                }
                else if (i == 2)
                {
                    RegSet16BitValue(0x1600, 0xA000);
                }

                // Sector erase
                RegSet16BitValue(0x160E, (RegGet16BitValue(0x160E) | BIT2));

                DBG("Wait erase done flag\n");

                nRegData = 0;
                nTimeOut = 0;

                while (1) // Wait erase done flag
                {
                    nRegData = RegGet16BitValue(0x1610); // Memory status
                    nRegData = nRegData & BIT1;
            
                    DBG("Wait erase done flag nRegData = 0x%x\n", nRegData);

                    if (nRegData == BIT1)
                    {
                        break;		
                    }
                    mdelay(50);

                    if ((nTimeOut ++) > 30)
                    {
                        DBG("Erase main block %d times failed. Timeout.\n", nEraseCount);

                        if (nEraseCount == (nMaxEraseTimes - 1))
                        {
                            goto EraseEnd;
                        }
                    }
                }
            }   
        }
        else if (eEmemType == EMEM_INFO) // 512Byte
        {
            DBG("Erase info block %d times\n", nEraseCount);

            // Clear pce
            RegSetLByteValue(0x1618, 0x80);
            mdelay(10);

            RegSet16BitValue(0x1600, 0xC000);
        
            // Sector erase
            RegSet16BitValue(0x160E, (RegGet16BitValue(0x160E) | BIT2));

            DBG("Wait erase done flag\n");

            while (1) // Wait erase done flag
            {
                nRegData = RegGet16BitValue(0x1610); // Memory status
                nRegData = nRegData & BIT1;
            
                DBG("Wait erase done flag nRegData = 0x%x\n", nRegData);

                if (nRegData == BIT1)
                {
                    break;		
                }
                mdelay(50);

                if ((nTimeOut ++) > 30)
                {
                    DBG("Erase info block %d times failed. Timeout.\n", nEraseCount);

                    if (nEraseCount == (nMaxEraseTimes - 1))
                    {
                        goto EraseEnd;
                    }
                }
            }
        }
    }
    
    _DrvFwCtrlMsg22xxRestoreVoltage();

    EraseEnd:
    
    DBG("Erase end\n");

    DbBusIICNotUseBus();
    DbBusNotStopMCU();
    DbBusExitSerialDebugMode();
}

static void _DrvFwCtrlMsg22xxProgramEmem(EmemType_e eEmemType) 
{
    u32 i, j; 
    u32 nRemainSize = 0, nBlockSize = 0, nSize = 0, index = 0;
    u32 nTimeOut = 0;
    u16 nRegData = 0;
#if defined(CONFIG_TOUCH_DRIVER_RUN_ON_QCOM_PLATFORM) || defined(CONFIG_TOUCH_DRIVER_RUN_ON_MTK_PLATFORM)
    u8 szDbBusTxData[128] = {0};
#ifdef CONFIG_ENABLE_UPDATE_FIRMWARE_WITH_SUPPORT_I2C_SPEED_400K
    u32 nSizePerWrite = 1;
#else 
    u32 nSizePerWrite = 125;
#endif //CONFIG_ENABLE_UPDATE_FIRMWARE_WITH_SUPPORT_I2C_SPEED_400K
#elif defined(CONFIG_TOUCH_DRIVER_RUN_ON_SPRD_PLATFORM)
    u8 szDbBusTxData[1024] = {0};
#ifdef CONFIG_ENABLE_UPDATE_FIRMWARE_WITH_SUPPORT_I2C_SPEED_400K
    u32 nSizePerWrite = 1;
#else
    u32 nSizePerWrite = 1021;
#endif //CONFIG_ENABLE_UPDATE_FIRMWARE_WITH_SUPPORT_I2C_SPEED_400K
#endif

    DBG("*** %s() eEmemType = %d ***\n", __func__, eEmemType);

#ifdef CONFIG_TOUCH_DRIVER_RUN_ON_MTK_PLATFORM
#ifdef CONFIG_ENABLE_DMA_IIC
    DmaReset();
#endif //CONFIG_ENABLE_DMA_IIC
#endif //CONFIG_TOUCH_DRIVER_RUN_ON_MTK_PLATFORM

    DbBusEnterSerialDebugMode();
    DbBusStopMCU();
    DbBusIICUseBus();
    DbBusIICReshape();

    // Hold reset pin before program
    RegSetLByteValue(0x1E06, 0x00);

    DBG("Program start\n");

    RegSet16BitValue(0x161A, 0xABBA);
    RegSet16BitValue(0x1618, (RegGet16BitValue(0x1618) | 0x80));

    if (eEmemType == EMEM_MAIN)
    {
        DBG("Program main block\n");

        RegSet16BitValue(0x1600, 0x0000); // Set start address of main block
        nRemainSize = MSG22XX_FIRMWARE_MAIN_BLOCK_SIZE * 1024; //48KB
        index = 0;
    }
    else if (eEmemType == EMEM_INFO)
    {
        DBG("Program info block\n");

        RegSet16BitValue(0x1600, 0xC000); // Set start address of info block
        nRemainSize = MSG22XX_FIRMWARE_INFO_BLOCK_SIZE; //512Byte
        index = MSG22XX_FIRMWARE_MAIN_BLOCK_SIZE * 1024;
    }
    else
    {
        DBG("eEmemType = %d is not supported for program e-memory.\n", eEmemType);
        return;
    }

    RegSet16BitValue(0x160C, (RegGet16BitValue(0x160C) | 0x01)); // Enable burst mode

    // Program start
    szDbBusTxData[0] = 0x10;
    szDbBusTxData[1] = 0x16;
    szDbBusTxData[2] = 0x02;

    IicWriteData(SLAVE_I2C_ID_DBBUS, &szDbBusTxData[0], 3);    

    szDbBusTxData[0] = 0x20;

    IicWriteData(SLAVE_I2C_ID_DBBUS, &szDbBusTxData[0], 1);    

    i = 0;
    
    while (nRemainSize > 0)
    {
        if (nRemainSize > nSizePerWrite)
        {
            nBlockSize = nSizePerWrite;
        }
        else
        {
            nBlockSize = nRemainSize;
        }

        szDbBusTxData[0] = 0x10;
        szDbBusTxData[1] = 0x16;
        szDbBusTxData[2] = 0x02;

        nSize = 3;

        for (j = 0; j < nBlockSize; j ++)
        {
            szDbBusTxData[3+j] = _gOneDimenFwData[index+(i*nSizePerWrite)+j];
            nSize ++; 
        }
        i ++;

        IicWriteData(SLAVE_I2C_ID_DBBUS, &szDbBusTxData[0], nSize);

        nRemainSize = nRemainSize - nBlockSize;
    }

    // Program end
    szDbBusTxData[0] = 0x21;

    IicWriteData(SLAVE_I2C_ID_DBBUS, &szDbBusTxData[0], 1);    

    RegSet16BitValue(0x160C, RegGet16BitValue(0x160C) & (~0x01)); // Clear burst mode

    DBG("Wait write done flag\n");

    while (1) // Wait write done flag
    {
        // Polling 0x1610 is 0x0002
        nRegData = RegGet16BitValue(0x1610); // Memory status
        nRegData = nRegData & BIT1;
    
        DBG("Wait write done flag nRegData = 0x%x\n", nRegData);

        if (nRegData == BIT1)
        {
            break;		
        }
        mdelay(10);

        if ((nTimeOut ++) > 30)
        {
            DBG("Write failed. Timeout.\n");

            goto ProgramEnd;
        }
    }

    ProgramEnd:

    DBG("Program end\n");

    DbBusIICNotUseBus();
    DbBusNotStopMCU();
    DbBusExitSerialDebugMode();
}

static u32 _DrvFwCtrlMsg22xxGetFirmwareCrcByHardware(EmemType_e eEmemType) 
{
    u16 nCrcDown = 0;
    u32 nRetVal = 0; 

    DBG("*** %s() eEmemType = %d ***\n", __func__, eEmemType);

    DbBusEnterSerialDebugMode();
    DbBusStopMCU();
    DbBusIICUseBus();
    DbBusIICReshape();
    mdelay(100);

#ifdef CONFIG_ENABLE_UPDATE_FIRMWARE_WITH_SUPPORT_I2C_SPEED_400K
    // Stop MCU
    RegSetLByteValue(0x0FE6, 0x01); 
    
    // Change MCU clock deglich mux source
    RegSet16BitValue(0x1E54, (RegGet16BitValue(0x1E54) | BIT0)); 

    // Change PIU clock to 48 MHz
    RegSet16BitValue(0x1E22, (RegGet16BitValue(0x1E22) | BIT14)); 

    // Set MCU clock setting
    RegSet16BitValue(0x1E22, (RegGet16BitValue(0x1E22) & (~(BIT3 | BIT2)))); 

    // Set DB bus clock setting
    RegSet16BitValue(0x1E24, (RegGet16BitValue(0x1E24) & (~(BIT3 | BIT2)))); 
#endif //CONFIG_ENABLE_UPDATE_FIRMWARE_WITH_SUPPORT_I2C_SPEED_400K

    // RIU password
    RegSet16BitValue(0x161A, 0xABBA);      

    // Set PCE high
    RegSetLByteValue(0x1618, 0x40);      

    if (eEmemType == EMEM_MAIN)
    {
        // Set start address and end address for main block
        RegSet16BitValue(0x1600, 0x0000);      
        RegSet16BitValue(0x1640, 0xBFF8);      
    }
    else if (eEmemType == EMEM_INFO)
    {
        // Set start address and end address for info block
        RegSet16BitValue(0x1600, 0xC000);      
        RegSet16BitValue(0x1640, 0xC1F8);      
    }

    // CRC reset
    RegSet16BitValue(0x164E, 0x0001);      

    RegSet16BitValue(0x164E, 0x0000);   
    
    // Trigger CRC check
    RegSetLByteValue(0x160E, 0x20);   
    mdelay(10);
       
    nCrcDown = RegGet16BitValue(0x164E);
    
    while (nCrcDown != 2)
    {
        DBG("Wait CRC down\n");
        mdelay(10);
        nCrcDown = RegGet16BitValue(0x164E);
    }

    nRetVal = RegGet16BitValue(0x1652);
    nRetVal = (nRetVal << 16) | RegGet16BitValue(0x1650);

    DBG("Hardware CRC = 0x%x\n", nRetVal);

    DbBusIICNotUseBus();
    DbBusNotStopMCU();
    DbBusExitSerialDebugMode();

    return nRetVal;	
}

static void _DrvFwCtrlMsg22xxConvertFwDataTwoDimenToOneDimen(u8 szTwoDimenFwData[][1024], u8* pOneDimenFwData)
{
    u32 i, j;

    DBG("*** %s() ***\n", __func__);

    for (i = 0; i < (MSG22XX_FIRMWARE_MAIN_BLOCK_SIZE+1); i ++)
    {
        if (i < MSG22XX_FIRMWARE_MAIN_BLOCK_SIZE) // i < 48
        {
            for (j = 0; j < 1024; j ++)
            {
                pOneDimenFwData[i*1024+j] = szTwoDimenFwData[i][j];
            }
        }
        else // i == 48
        {
            for (j = 0; j < 512; j ++)
            {
                pOneDimenFwData[i*1024+j] = szTwoDimenFwData[i][j];
            }
        }
    }
}

#ifdef CONFIG_ENABLE_TYPE_B_PROTOCOL 
static u32 _DrvFwCtrlPointDistance(u16 nX, u16 nY, u16 nPrevX, u16 nPrevY)
{ 
    u32 nRetVal = 0;
	
    nRetVal = (((nX-nPrevX)*(nX-nPrevX))+((nY-nPrevY)*(nY-nPrevY)));
    
    return nRetVal;
}
#endif //CONFIG_ENABLE_TYPE_B_PROTOCOL

static s32 _DrvFwCtrlParsePacket(u8 *pPacket, u16 nLength, TouchInfo_t *pInfo)
{
    u8 nCheckSum = 0;
    u32 nDeltaX = 0, nDeltaY = 0;
    u32 nX = 0;
    u32 nY = 0;
#ifdef CONFIG_SWAP_X_Y
    u32 nTempX;
    u32 nTempY;
#endif
#ifdef CONFIG_ENABLE_TYPE_B_PROTOCOL
    static u8 nPrevTouchNum = 0; 
    static u16 szPrevX[MAX_TOUCH_NUM] = {0xFFFF, 0xFFFF};
    static u16 szPrevY[MAX_TOUCH_NUM] = {0xFFFF, 0xFFFF};
    static u8  szPrevPress[MAX_TOUCH_NUM] = {0};
    u32 i = 0;
    u16 szX[MAX_TOUCH_NUM] = {0};
    u16 szY[MAX_TOUCH_NUM] = {0};
    u16 nTemp = 0;
    u8  nChangePoints = 0;
#endif //CONFIG_ENABLE_TYPE_B_PROTOCOL
    u8 nCheckSumIndex = nLength-1; //Set default checksum index for demo mode

    DBG("*** %s() ***\n", __func__);

#ifdef CONFIG_ENABLE_TYPE_B_PROTOCOL
    _gCurrPress[0] = 0;
    _gCurrPress[1] = 0;
#endif //CONFIG_ENABLE_TYPE_B_PROTOCOL

#ifdef CONFIG_ENABLE_COUNT_REPORT_RATE
    if (g_IsEnableReportRate == 1)
    {
        if (g_InterruptCount == 4294967296)
        {
            g_InterruptCount = 0; // Reset count if overflow
            DBG("g_InterruptCount reset to 0\n");
        }	

        if (g_InterruptCount == 0)
        {
            // Get start time
            do_gettimeofday(&g_StartTime);
    
            DBG("Start time : %lu sec, %lu msec\n", g_StartTime.tv_sec,  g_StartTime.tv_usec); 
        }
        
        g_InterruptCount ++;

        DBG("g_InterruptCount = %d\n", g_InterruptCount);
    }
#endif //CONFIG_ENABLE_COUNT_REPORT_RATE

    if (IS_FIRMWARE_DATA_LOG_ENABLED)
    {
        if (g_FirmwareMode == FIRMWARE_MODE_DEMO_MODE)
        {
            nCheckSumIndex = 7;
        }
        else if (g_FirmwareMode == FIRMWARE_MODE_DEBUG_MODE || g_FirmwareMode == FIRMWARE_MODE_RAW_DATA_MODE)
        {
            nCheckSumIndex = 31;
        }

#ifdef CONFIG_ENABLE_GESTURE_WAKEUP
        if (g_GestureWakeupFlag == 1)
        {
            nCheckSumIndex = nLength-1;
        }
#endif //CONFIG_ENABLE_GESTURE_WAKEUP
    } //IS_FIRMWARE_DATA_LOG_ENABLED
    
    nCheckSum = DrvCommonCalculateCheckSum(&pPacket[0], nCheckSumIndex);
    DBG("check sum : [%x] == [%x]? \n", pPacket[nCheckSumIndex], nCheckSum);

#ifdef CONFIG_ENABLE_GESTURE_WAKEUP
    if (g_GestureWakeupFlag == 1)
    {
        u8 nWakeupMode = 0;
        u8 bIsCorrectFormat = 0;

        DBG("received raw data from touch panel as following:\n");
        DBG("pPacket[0]=%x \n pPacket[1]=%x pPacket[2]=%x pPacket[3]=%x pPacket[4]=%x pPacket[5]=%x \n", \
            pPacket[0], pPacket[1], pPacket[2], pPacket[3], pPacket[4], pPacket[5]);

        if (g_ChipType == CHIP_TYPE_MSG22XX && pPacket[0] == 0xA7 && pPacket[1] == 0x00 && pPacket[2] == 0x06 && pPacket[3] == PACKET_TYPE_GESTURE_WAKEUP)
        {
            nWakeupMode = pPacket[4];
            bIsCorrectFormat = 1;
        } 
#ifdef CONFIG_ENABLE_GESTURE_DEBUG_MODE
        else if (g_ChipType == CHIP_TYPE_MSG22XX && pPacket[0] == 0xA7 && pPacket[1] == 0x00 && pPacket[2] == 0x80 && pPacket[3] == PACKET_TYPE_GESTURE_DEBUG)
        {
            u32 a = 0;

            nWakeupMode = pPacket[4];
            bIsCorrectFormat = 1;
            
            for (a = 0; a < 0x80; a ++)
            {
                g_LogGestureDebug[a] = pPacket[a];
            }
            
            if (!(pPacket[5] >> 7))// LCM Light Flag = 0
            {
                nWakeupMode = 0xFE;
                DBG("gesture debug mode LCM flag = 0\n");
            }
        }
#endif //CONFIG_ENABLE_GESTURE_DEBUG_MODE

#ifdef CONFIG_ENABLE_GESTURE_INFORMATION_MODE
        else if (g_ChipType == CHIP_TYPE_MSG22XX && pPacket[0] == 0xA7 && pPacket[1] == 0x00 && pPacket[2] == 0x80 && pPacket[3] == PACKET_TYPE_GESTURE_INFORMATION)
        {
            u32 a = 0;
            u32 nTmpCount = 0;

            nWakeupMode = pPacket[4];
            bIsCorrectFormat = 1;

            for (a = 0; a < 6; a ++)//header
            {
                g_LogGestureInfor[nTmpCount] = pPacket[a];
                nTmpCount++;
            }

            for (a = 6; a < 126; a = a+3)//parse packet to coordinate
            {
                u32 nTranX = 0;
                u32 nTranY = 0;
                
                _DrvFwCtrlCoordinate(&pPacket[a], &nTranX, &nTranY);
                g_LogGestureInfor[nTmpCount] = nTranX;
                nTmpCount++;
                g_LogGestureInfor[nTmpCount] = nTranY;
                nTmpCount++;
            }
            
            g_LogGestureInfor[nTmpCount] = pPacket[126]; //Dummy
            nTmpCount++;
            g_LogGestureInfor[nTmpCount] = pPacket[127]; //checksum
            nTmpCount++;
            DBG("gesture information mode Count = %d\n", nTmpCount);
        }
#endif //CONFIG_ENABLE_GESTURE_INFORMATION_MODE
        else if (g_ChipType == CHIP_TYPE_MSG21XXA && pPacket[0] == 0x52 && pPacket[1] == 0xFF && pPacket[2] == 0xFF && pPacket[3] == 0xFF && pPacket[4] == 0xFF && pPacket[6] == 0xFF)
        {
            nWakeupMode = pPacket[5];
            bIsCorrectFormat = 1;
        }
        
        if (bIsCorrectFormat) 
        {
            DBG("nWakeupMode = 0x%x\n", nWakeupMode);

            switch (nWakeupMode)
            {
                case 0x58:
                    _gGestureWakeupValue[0] = GESTURE_WAKEUP_MODE_DOUBLE_CLICK_FLAG;

                    DBG("Light up screen by DOUBLE_CLICK gesture wakeup.\n");

                    input_report_key(g_InputDevice, KEY_U, 1);
                    input_sync(g_InputDevice);
                    input_report_key(g_InputDevice, KEY_U, 0);
                    input_sync(g_InputDevice);
                    break;		
                case 0x60:
                    _gGestureWakeupValue[0] = GESTURE_WAKEUP_MODE_UP_DIRECT_FLAG;
                    
                    DBG("Light up screen by UP_DIRECT gesture wakeup.\n");

//                    input_report_key(g_InputDevice, KEY_UP, 1);
                    input_report_key(g_InputDevice, KEY_POWER, 1);
                    input_sync(g_InputDevice);
//                    input_report_key(g_InputDevice, KEY_UP, 0);
                    input_report_key(g_InputDevice, KEY_POWER, 0);
                    input_sync(g_InputDevice);
                    break;		
                case 0x61:
                    _gGestureWakeupValue[0] = GESTURE_WAKEUP_MODE_DOWN_DIRECT_FLAG;

                    DBG("Light up screen by DOWN_DIRECT gesture wakeup.\n");

//                    input_report_key(g_InputDevice, KEY_DOWN, 1);
                    input_report_key(g_InputDevice, KEY_POWER, 1);
                    input_sync(g_InputDevice);
//                    input_report_key(g_InputDevice, KEY_DOWN, 0);
                    input_report_key(g_InputDevice, KEY_POWER, 0);
                    input_sync(g_InputDevice);
                    break;		
                case 0x62:
                    _gGestureWakeupValue[0] = GESTURE_WAKEUP_MODE_LEFT_DIRECT_FLAG;

                    DBG("Light up screen by LEFT_DIRECT gesture wakeup.\n");

//                  input_report_key(g_InputDevice, KEY_LEFT, 1);
                    input_report_key(g_InputDevice, KEY_POWER, 1);
                    input_sync(g_InputDevice);
//                    input_report_key(g_InputDevice, KEY_LEFT, 0);
                    input_report_key(g_InputDevice, KEY_POWER, 0);
                    input_sync(g_InputDevice);
                    break;		
                case 0x63:
                    _gGestureWakeupValue[0] = GESTURE_WAKEUP_MODE_RIGHT_DIRECT_FLAG;

                    DBG("Light up screen by RIGHT_DIRECT gesture wakeup.\n");

//                    input_report_key(g_InputDevice, KEY_RIGHT, 1);
                    input_report_key(g_InputDevice, KEY_POWER, 1);
                    input_sync(g_InputDevice);
//                    input_report_key(g_InputDevice, KEY_RIGHT, 0);
                    input_report_key(g_InputDevice, KEY_POWER, 0);
                    input_sync(g_InputDevice);
                    break;		
                case 0x64:
                    _gGestureWakeupValue[0] = GESTURE_WAKEUP_MODE_m_CHARACTER_FLAG;

                    DBG("Light up screen by m_CHARACTER gesture wakeup.\n");

//                    input_report_key(g_InputDevice, KEY_M, 1);
                    input_report_key(g_InputDevice, KEY_POWER, 1);
                    input_sync(g_InputDevice);
//                    input_report_key(g_InputDevice, KEY_M, 0);
                    input_report_key(g_InputDevice, KEY_POWER, 0);
                    input_sync(g_InputDevice);
                    break;		
                case 0x65:
                    _gGestureWakeupValue[0] = GESTURE_WAKEUP_MODE_W_CHARACTER_FLAG;

                    DBG("Light up screen by W_CHARACTER gesture wakeup.\n");

//                    input_report_key(g_InputDevice, KEY_W, 1);
                    input_report_key(g_InputDevice, KEY_POWER, 1);
                    input_sync(g_InputDevice);
//                    input_report_key(g_InputDevice, KEY_W, 0);
                    input_report_key(g_InputDevice, KEY_POWER, 0);
                    input_sync(g_InputDevice);
                    break;		
                case 0x66:
                    _gGestureWakeupValue[0] = GESTURE_WAKEUP_MODE_C_CHARACTER_FLAG;

                    DBG("Light up screen by C_CHARACTER gesture wakeup.\n");

//                    input_report_key(g_InputDevice, KEY_C, 1);
                    input_report_key(g_InputDevice, KEY_POWER, 1);
                    input_sync(g_InputDevice);
//                    input_report_key(g_InputDevice, KEY_C, 0);
                    input_report_key(g_InputDevice, KEY_POWER, 0);
                    input_sync(g_InputDevice);
                    break;
                case 0x67:
                    _gGestureWakeupValue[0] = GESTURE_WAKEUP_MODE_e_CHARACTER_FLAG;

                    DBG("Light up screen by e_CHARACTER gesture wakeup.\n");

//                    input_report_key(g_InputDevice, KEY_E, 1);
                    input_report_key(g_InputDevice, KEY_POWER, 1);
                    input_sync(g_InputDevice);
//                    input_report_key(g_InputDevice, KEY_E, 0);
                    input_report_key(g_InputDevice, KEY_POWER, 0);
                    input_sync(g_InputDevice);
                    break;
                case 0x68:
                    _gGestureWakeupValue[0] = GESTURE_WAKEUP_MODE_V_CHARACTER_FLAG;

                    DBG("Light up screen by V_CHARACTER gesture wakeup.\n");

//                    input_report_key(g_InputDevice, KEY_V, 1);
                    input_report_key(g_InputDevice, KEY_POWER, 1);
                    input_sync(g_InputDevice);
//                    input_report_key(g_InputDevice, KEY_V, 0);
                    input_report_key(g_InputDevice, KEY_POWER, 0);
                    input_sync(g_InputDevice);
                    break;
                case 0x69:
                    _gGestureWakeupValue[0] = GESTURE_WAKEUP_MODE_O_CHARACTER_FLAG;

                    DBG("Light up screen by O_CHARACTER gesture wakeup.\n");

//                    input_report_key(g_InputDevice, KEY_O, 1);
                    input_report_key(g_InputDevice, KEY_POWER, 1);
                    input_sync(g_InputDevice);
//                    input_report_key(g_InputDevice, KEY_O, 0);
                    input_report_key(g_InputDevice, KEY_POWER, 0);
                    input_sync(g_InputDevice);
                    break;
                case 0x6A:
                    _gGestureWakeupValue[0] = GESTURE_WAKEUP_MODE_S_CHARACTER_FLAG;

                    DBG("Light up screen by S_CHARACTER gesture wakeup.\n");

//                    input_report_key(g_InputDevice, KEY_S, 1);
                    input_report_key(g_InputDevice, KEY_POWER, 1);
                    input_sync(g_InputDevice);
//                    input_report_key(g_InputDevice, KEY_S, 0);
                    input_report_key(g_InputDevice, KEY_POWER, 0);
                    input_sync(g_InputDevice);
                    break;
                case 0x6B:
                    _gGestureWakeupValue[0] = GESTURE_WAKEUP_MODE_Z_CHARACTER_FLAG;

                    DBG("Light up screen by Z_CHARACTER gesture wakeup.\n");

//                    input_report_key(g_InputDevice, KEY_Z, 1);
                    input_report_key(g_InputDevice, KEY_POWER, 1);
                    input_sync(g_InputDevice);
//                    input_report_key(g_InputDevice, KEY_Z, 0);
                    input_report_key(g_InputDevice, KEY_POWER, 0);
                    input_sync(g_InputDevice);
                    break;
                case 0x6C:
                    _gGestureWakeupValue[0] = GESTURE_WAKEUP_MODE_RESERVE1_FLAG;

                    DBG("Light up screen by GESTURE_WAKEUP_MODE_RESERVE1_FLAG gesture wakeup.\n");

//                    input_report_key(g_InputDevice, RESERVER1, 1);
                    input_report_key(g_InputDevice, KEY_POWER, 1);
                    input_sync(g_InputDevice);
//                    input_report_key(g_InputDevice, RESERVER1, 0);
                    input_report_key(g_InputDevice, KEY_POWER, 0);
                    input_sync(g_InputDevice);
                    break;
                case 0x6D:
                    _gGestureWakeupValue[0] = GESTURE_WAKEUP_MODE_RESERVE2_FLAG;

                    DBG("Light up screen by GESTURE_WAKEUP_MODE_RESERVE2_FLAG gesture wakeup.\n");

//                    input_report_key(g_InputDevice, RESERVER2, 1);
                    input_report_key(g_InputDevice, KEY_POWER, 1);
                    input_sync(g_InputDevice);
//                    input_report_key(g_InputDevice, RESERVER2, 0);
                    input_report_key(g_InputDevice, KEY_POWER, 0);
                    input_sync(g_InputDevice);
                    break;
                case 0x6E:
                    _gGestureWakeupValue[0] = GESTURE_WAKEUP_MODE_RESERVE3_FLAG;

                    DBG("Light up screen by GESTURE_WAKEUP_MODE_RESERVE3_FLAG gesture wakeup.\n");

//                    input_report_key(g_InputDevice, RESERVER3, 1);
                    input_report_key(g_InputDevice, KEY_POWER, 1);
                    input_sync(g_InputDevice);
//                    input_report_key(g_InputDevice, RESERVER3, 0);
                    input_report_key(g_InputDevice, KEY_POWER, 0);
                    input_sync(g_InputDevice);
                    break;
#ifdef CONFIG_SUPPORT_64_TYPES_GESTURE_WAKEUP_MODE
                case 0x6F:
                    _gGestureWakeupValue[0] = GESTURE_WAKEUP_MODE_RESERVE4_FLAG;

                    DBG("Light up screen by GESTURE_WAKEUP_MODE_RESERVE4_FLAG gesture wakeup.\n");

//                    input_report_key(g_InputDevice, RESERVER4, 1);
                    input_report_key(g_InputDevice, KEY_POWER, 1);
                    input_sync(g_InputDevice);
//                    input_report_key(g_InputDevice, RESERVER4, 0);
                    input_report_key(g_InputDevice, KEY_POWER, 0);
                    input_sync(g_InputDevice);
                    break;
                case 0x70:
                    _gGestureWakeupValue[0] = GESTURE_WAKEUP_MODE_RESERVE5_FLAG;

                    DBG("Light up screen by GESTURE_WAKEUP_MODE_RESERVE5_FLAG gesture wakeup.\n");

//                    input_report_key(g_InputDevice, RESERVER5, 1);
                    input_report_key(g_InputDevice, KEY_POWER, 1);
                    input_sync(g_InputDevice);
//                    input_report_key(g_InputDevice, RESERVER5, 0);
                    input_report_key(g_InputDevice, KEY_POWER, 0);
                    input_sync(g_InputDevice);
                    break;
                case 0x71:
                    _gGestureWakeupValue[0] = GESTURE_WAKEUP_MODE_RESERVE6_FLAG;

                    DBG("Light up screen by GESTURE_WAKEUP_MODE_RESERVE6_FLAG gesture wakeup.\n");

//                    input_report_key(g_InputDevice, RESERVER6, 1);
                    input_report_key(g_InputDevice, KEY_POWER, 1);
                    input_sync(g_InputDevice);
//                    input_report_key(g_InputDevice, RESERVER6, 0);
                    input_report_key(g_InputDevice, KEY_POWER, 0);
                    input_sync(g_InputDevice);
                    break;
                case 0x72:
                    _gGestureWakeupValue[0] = GESTURE_WAKEUP_MODE_RESERVE7_FLAG;

                    DBG("Light up screen by GESTURE_WAKEUP_MODE_RESERVE7_FLAG gesture wakeup.\n");

//                    input_report_key(g_InputDevice, RESERVER7, 1);
                    input_report_key(g_InputDevice, KEY_POWER, 1);
                    input_sync(g_InputDevice);
//                    input_report_key(g_InputDevice, RESERVER7, 0);
                    input_report_key(g_InputDevice, KEY_POWER, 0);
                    input_sync(g_InputDevice);
                    break;
                case 0x73:
                    _gGestureWakeupValue[0] = GESTURE_WAKEUP_MODE_RESERVE8_FLAG;

                    DBG("Light up screen by GESTURE_WAKEUP_MODE_RESERVE8_FLAG gesture wakeup.\n");

//                    input_report_key(g_InputDevice, RESERVER8, 1);
                    input_report_key(g_InputDevice, KEY_POWER, 1);
                    input_sync(g_InputDevice);
//                    input_report_key(g_InputDevice, RESERVER8, 0);
                    input_report_key(g_InputDevice, KEY_POWER, 0);
                    input_sync(g_InputDevice);
                    break;
                case 0x74:
                    _gGestureWakeupValue[0] = GESTURE_WAKEUP_MODE_RESERVE9_FLAG;

                    DBG("Light up screen by GESTURE_WAKEUP_MODE_RESERVE9_FLAG gesture wakeup.\n");

//                    input_report_key(g_InputDevice, RESERVER9, 1);
                    input_report_key(g_InputDevice, KEY_POWER, 1);
                    input_sync(g_InputDevice);
//                    input_report_key(g_InputDevice, RESERVER9, 0);
                    input_report_key(g_InputDevice, KEY_POWER, 0);
                    input_sync(g_InputDevice);
                    break;
                case 0x75:
                    _gGestureWakeupValue[0] = GESTURE_WAKEUP_MODE_RESERVE10_FLAG;

                    DBG("Light up screen by GESTURE_WAKEUP_MODE_RESERVE10_FLAG gesture wakeup.\n");

//                    input_report_key(g_InputDevice, RESERVER10, 1);
                    input_report_key(g_InputDevice, KEY_POWER, 1);
                    input_sync(g_InputDevice);
//                    input_report_key(g_InputDevice, RESERVER10, 0);
                    input_report_key(g_InputDevice, KEY_POWER, 0);
                    input_sync(g_InputDevice);
                    break;
                case 0x76:
                    _gGestureWakeupValue[0] = GESTURE_WAKEUP_MODE_RESERVE11_FLAG;

                    DBG("Light up screen by GESTURE_WAKEUP_MODE_RESERVE11_FLAG gesture wakeup.\n");

//                    input_report_key(g_InputDevice, RESERVER11, 1);
                    input_report_key(g_InputDevice, KEY_POWER, 1);
                    input_sync(g_InputDevice);
//                    input_report_key(g_InputDevice, RESERVER11, 0);
                    input_report_key(g_InputDevice, KEY_POWER, 0);
                    input_sync(g_InputDevice);
                    break;
                case 0x77:
                    _gGestureWakeupValue[0] = GESTURE_WAKEUP_MODE_RESERVE12_FLAG;

                    DBG("Light up screen by GESTURE_WAKEUP_MODE_RESERVE12_FLAG gesture wakeup.\n");

//                    input_report_key(g_InputDevice, RESERVER12, 1);
                    input_report_key(g_InputDevice, KEY_POWER, 1);
                    input_sync(g_InputDevice);
//                    input_report_key(g_InputDevice, RESERVER12, 0);
                    input_report_key(g_InputDevice, KEY_POWER, 0);
                    input_sync(g_InputDevice);
                    break;
                case 0x78:
                    _gGestureWakeupValue[0] = GESTURE_WAKEUP_MODE_RESERVE13_FLAG;

                    DBG("Light up screen by GESTURE_WAKEUP_MODE_RESERVE13_FLAG gesture wakeup.\n");

//                    input_report_key(g_InputDevice, RESERVER13, 1);
                    input_report_key(g_InputDevice, KEY_POWER, 1);
                    input_sync(g_InputDevice);
//                    input_report_key(g_InputDevice, RESERVER13, 0);
                    input_report_key(g_InputDevice, KEY_POWER, 0);
                    input_sync(g_InputDevice);
                    break;
                case 0x79:
                    _gGestureWakeupValue[0] = GESTURE_WAKEUP_MODE_RESERVE14_FLAG;

                    DBG("Light up screen by GESTURE_WAKEUP_MODE_RESERVE14_FLAG gesture wakeup.\n");

//                    input_report_key(g_InputDevice, RESERVER14, 1);
                    input_report_key(g_InputDevice, KEY_POWER, 1);
                    input_sync(g_InputDevice);
//                    input_report_key(g_InputDevice, RESERVER14, 0);
                    input_report_key(g_InputDevice, KEY_POWER, 0);
                    input_sync(g_InputDevice);
                    break;
                case 0x7A:
                    _gGestureWakeupValue[0] = GESTURE_WAKEUP_MODE_RESERVE15_FLAG;

                    DBG("Light up screen by GESTURE_WAKEUP_MODE_RESERVE15_FLAG gesture wakeup.\n");

//                    input_report_key(g_InputDevice, RESERVER15, 1);
                    input_report_key(g_InputDevice, KEY_POWER, 1);
                    input_sync(g_InputDevice);
//                    input_report_key(g_InputDevice, RESERVER15, 0);
                    input_report_key(g_InputDevice, KEY_POWER, 0);
                    input_sync(g_InputDevice);
                    break;
                case 0x7B:
                    _gGestureWakeupValue[0] = GESTURE_WAKEUP_MODE_RESERVE16_FLAG;

                    DBG("Light up screen by GESTURE_WAKEUP_MODE_RESERVE16_FLAG gesture wakeup.\n");

//                    input_report_key(g_InputDevice, RESERVER16, 1);
                    input_report_key(g_InputDevice, KEY_POWER, 1);
                    input_sync(g_InputDevice);
//                    input_report_key(g_InputDevice, RESERVER16, 0);
                    input_report_key(g_InputDevice, KEY_POWER, 0);
                    input_sync(g_InputDevice);
                    break;
                case 0x7C:
                    _gGestureWakeupValue[0] = GESTURE_WAKEUP_MODE_RESERVE17_FLAG;

                    DBG("Light up screen by GESTURE_WAKEUP_MODE_RESERVE17_FLAG gesture wakeup.\n");

//                    input_report_key(g_InputDevice, RESERVER17, 1);
                    input_report_key(g_InputDevice, KEY_POWER, 1);
                    input_sync(g_InputDevice);
//                    input_report_key(g_InputDevice, RESERVER17, 0);
                    input_report_key(g_InputDevice, KEY_POWER, 0);
                    input_sync(g_InputDevice);
                    break;
                case 0x7D:
                    _gGestureWakeupValue[0] = GESTURE_WAKEUP_MODE_RESERVE18_FLAG;

                    DBG("Light up screen by GESTURE_WAKEUP_MODE_RESERVE18_FLAG gesture wakeup.\n");

//                    input_report_key(g_InputDevice, RESERVER18, 1);
                    input_report_key(g_InputDevice, KEY_POWER, 1);
                    input_sync(g_InputDevice);
//                    input_report_key(g_InputDevice, RESERVER18, 0);
                    input_report_key(g_InputDevice, KEY_POWER, 0);
                    input_sync(g_InputDevice);
                    break;
                case 0x7E:
                    _gGestureWakeupValue[0] = GESTURE_WAKEUP_MODE_RESERVE19_FLAG;

                    DBG("Light up screen by GESTURE_WAKEUP_MODE_RESERVE19_FLAG gesture wakeup.\n");

//                    input_report_key(g_InputDevice, RESERVER19, 1);
                    input_report_key(g_InputDevice, KEY_POWER, 1);
                    input_sync(g_InputDevice);
//                    input_report_key(g_InputDevice, RESERVER19, 0);
                    input_report_key(g_InputDevice, KEY_POWER, 0);
                    input_sync(g_InputDevice);
                    break;
                case 0x7F:
                    _gGestureWakeupValue[1] = GESTURE_WAKEUP_MODE_RESERVE20_FLAG;

                    DBG("Light up screen by GESTURE_WAKEUP_MODE_RESERVE20_FLAG gesture wakeup.\n");

//                    input_report_key(g_InputDevice, RESERVER20, 1);
                    input_report_key(g_InputDevice, KEY_POWER, 1);
                    input_sync(g_InputDevice);
//                    input_report_key(g_InputDevice, RESERVER20, 0);
                    input_report_key(g_InputDevice, KEY_POWER, 0);
                    input_sync(g_InputDevice);
                    break;
                case 0x80:
                    _gGestureWakeupValue[1] = GESTURE_WAKEUP_MODE_RESERVE21_FLAG;

                    DBG("Light up screen by GESTURE_WAKEUP_MODE_RESERVE21_FLAG gesture wakeup.\n");

//                    input_report_key(g_InputDevice, RESERVER21, 1);
                    input_report_key(g_InputDevice, KEY_POWER, 1);
                    input_sync(g_InputDevice);
//                    input_report_key(g_InputDevice, RESERVER21, 0);
                    input_report_key(g_InputDevice, KEY_POWER, 0);
                    input_sync(g_InputDevice);
                    break;
                case 0x81:
                    _gGestureWakeupValue[1] = GESTURE_WAKEUP_MODE_RESERVE22_FLAG;

                    DBG("Light up screen by GESTURE_WAKEUP_MODE_RESERVE22_FLAG gesture wakeup.\n");

//                    input_report_key(g_InputDevice, RESERVER22, 1);
                    input_report_key(g_InputDevice, KEY_POWER, 1);
                    input_sync(g_InputDevice);
//                    input_report_key(g_InputDevice, RESERVER22, 0);
                    input_report_key(g_InputDevice, KEY_POWER, 0);
                    input_sync(g_InputDevice);
                    break;
                case 0x82:
                    _gGestureWakeupValue[1] = GESTURE_WAKEUP_MODE_RESERVE23_FLAG;

                    DBG("Light up screen by GESTURE_WAKEUP_MODE_RESERVE23_FLAG gesture wakeup.\n");

//                    input_report_key(g_InputDevice, RESERVER23, 1);
                    input_report_key(g_InputDevice, KEY_POWER, 1);
                    input_sync(g_InputDevice);
//                    input_report_key(g_InputDevice, RESERVER23, 0);
                    input_report_key(g_InputDevice, KEY_POWER, 0);
                    input_sync(g_InputDevice);
                    break;
                case 0x83:
                    _gGestureWakeupValue[1] = GESTURE_WAKEUP_MODE_RESERVE24_FLAG;

                    DBG("Light up screen by GESTURE_WAKEUP_MODE_RESERVE24_FLAG gesture wakeup.\n");

//                    input_report_key(g_InputDevice, RESERVER24, 1);
                    input_report_key(g_InputDevice, KEY_POWER, 1);
                    input_sync(g_InputDevice);
//                    input_report_key(g_InputDevice, RESERVER24, 0);
                    input_report_key(g_InputDevice, KEY_POWER, 0);
                    input_sync(g_InputDevice);
                    break;
                case 0x84:
                    _gGestureWakeupValue[1] = GESTURE_WAKEUP_MODE_RESERVE25_FLAG;

                    DBG("Light up screen by GESTURE_WAKEUP_MODE_RESERVE25_FLAG gesture wakeup.\n");

//                    input_report_key(g_InputDevice, RESERVER25, 1);
                    input_report_key(g_InputDevice, KEY_POWER, 1);
                    input_sync(g_InputDevice);
//                    input_report_key(g_InputDevice, RESERVER25, 0);
                    input_report_key(g_InputDevice, KEY_POWER, 0);
                    input_sync(g_InputDevice);
                    break;
                case 0x85:
                    _gGestureWakeupValue[1] = GESTURE_WAKEUP_MODE_RESERVE26_FLAG;

                    DBG("Light up screen by GESTURE_WAKEUP_MODE_RESERVE26_FLAG gesture wakeup.\n");

//                    input_report_key(g_InputDevice, RESERVER26, 1);
                    input_report_key(g_InputDevice, KEY_POWER, 1);
                    input_sync(g_InputDevice);
//                    input_report_key(g_InputDevice, RESERVER26, 0);
                    input_report_key(g_InputDevice, KEY_POWER, 0);
                    input_sync(g_InputDevice);
                    break;
                case 0x86:
                    _gGestureWakeupValue[1] = GESTURE_WAKEUP_MODE_RESERVE27_FLAG;

                    DBG("Light up screen by GESTURE_WAKEUP_MODE_RESERVE27_FLAG gesture wakeup.\n");

//                    input_report_key(g_InputDevice, RESERVER27, 1);
                    input_report_key(g_InputDevice, KEY_POWER, 1);
                    input_sync(g_InputDevice);
//                    input_report_key(g_InputDevice, RESERVER27, 0);
                    input_report_key(g_InputDevice, KEY_POWER, 0);
                    input_sync(g_InputDevice);
                    break;
                case 0x87:
                    _gGestureWakeupValue[1] = GESTURE_WAKEUP_MODE_RESERVE28_FLAG;

                    DBG("Light up screen by GESTURE_WAKEUP_MODE_RESERVE28_FLAG gesture wakeup.\n");

//                    input_report_key(g_InputDevice, RESERVER28, 1);
                    input_report_key(g_InputDevice, KEY_POWER, 1);
                    input_sync(g_InputDevice);
//                    input_report_key(g_InputDevice, RESERVER28, 0);
                    input_report_key(g_InputDevice, KEY_POWER, 0);
                    input_sync(g_InputDevice);
                    break;
                case 0x88:
                    _gGestureWakeupValue[1] = GESTURE_WAKEUP_MODE_RESERVE29_FLAG;

                    DBG("Light up screen by GESTURE_WAKEUP_MODE_RESERVE29_FLAG gesture wakeup.\n");

//                    input_report_key(g_InputDevice, RESERVER29, 1);
                    input_report_key(g_InputDevice, KEY_POWER, 1);
                    input_sync(g_InputDevice);
//                    input_report_key(g_InputDevice, RESERVER29, 0);
                    input_report_key(g_InputDevice, KEY_POWER, 0);
                    input_sync(g_InputDevice);
                    break;
                case 0x89:
                    _gGestureWakeupValue[1] = GESTURE_WAKEUP_MODE_RESERVE30_FLAG;

                    DBG("Light up screen by GESTURE_WAKEUP_MODE_RESERVE30_FLAG gesture wakeup.\n");

//                    input_report_key(g_InputDevice, RESERVER30, 1);
                    input_report_key(g_InputDevice, KEY_POWER, 1);
                    input_sync(g_InputDevice);
//                    input_report_key(g_InputDevice, RESERVER30, 0);
                    input_report_key(g_InputDevice, KEY_POWER, 0);
                    input_sync(g_InputDevice);
                    break;
                case 0x8A:
                    _gGestureWakeupValue[1] = GESTURE_WAKEUP_MODE_RESERVE31_FLAG;

                    DBG("Light up screen by GESTURE_WAKEUP_MODE_RESERVE31_FLAG gesture wakeup.\n");

//                    input_report_key(g_InputDevice, RESERVER31, 1);
                    input_report_key(g_InputDevice, KEY_POWER, 1);
                    input_sync(g_InputDevice);
//                    input_report_key(g_InputDevice, RESERVER31, 0);
                    input_report_key(g_InputDevice, KEY_POWER, 0);
                    input_sync(g_InputDevice);
                    break;
                case 0x8B:
                    _gGestureWakeupValue[1] = GESTURE_WAKEUP_MODE_RESERVE32_FLAG;

                    DBG("Light up screen by GESTURE_WAKEUP_MODE_RESERVE32_FLAG gesture wakeup.\n");

//                    input_report_key(g_InputDevice, RESERVER32, 1);
                    input_report_key(g_InputDevice, KEY_POWER, 1);
                    input_sync(g_InputDevice);
//                    input_report_key(g_InputDevice, RESERVER32, 0);
                    input_report_key(g_InputDevice, KEY_POWER, 0);
                    input_sync(g_InputDevice);
                    break;
                case 0x8C:
                    _gGestureWakeupValue[1] = GESTURE_WAKEUP_MODE_RESERVE33_FLAG;

                    DBG("Light up screen by GESTURE_WAKEUP_MODE_RESERVE33_FLAG gesture wakeup.\n");

//                    input_report_key(g_InputDevice, RESERVER33, 1);
                    input_report_key(g_InputDevice, KEY_POWER, 1);
                    input_sync(g_InputDevice);
//                    input_report_key(g_InputDevice, RESERVER33, 0);
                    input_report_key(g_InputDevice, KEY_POWER, 0);
                    input_sync(g_InputDevice);
                    break;
                case 0x8D:
                    _gGestureWakeupValue[1] = GESTURE_WAKEUP_MODE_RESERVE34_FLAG;

                    DBG("Light up screen by GESTURE_WAKEUP_MODE_RESERVE34_FLAG gesture wakeup.\n");

//                    input_report_key(g_InputDevice, RESERVER34, 1);
                    input_report_key(g_InputDevice, KEY_POWER, 1);
                    input_sync(g_InputDevice);
//                    input_report_key(g_InputDevice, RESERVER34, 0);
                    input_report_key(g_InputDevice, KEY_POWER, 0);
                    input_sync(g_InputDevice);
                    break;
                case 0x8E:
                    _gGestureWakeupValue[1] = GESTURE_WAKEUP_MODE_RESERVE35_FLAG;

                    DBG("Light up screen by GESTURE_WAKEUP_MODE_RESERVE35_FLAG gesture wakeup.\n");

//                    input_report_key(g_InputDevice, RESERVER35, 1);
                    input_report_key(g_InputDevice, KEY_POWER, 1);
                    input_sync(g_InputDevice);
//                    input_report_key(g_InputDevice, RESERVER35, 0);
                    input_report_key(g_InputDevice, KEY_POWER, 0);
                    input_sync(g_InputDevice);
                    break;
                case 0x8F:
                    _gGestureWakeupValue[1] = GESTURE_WAKEUP_MODE_RESERVE36_FLAG;

                    DBG("Light up screen by GESTURE_WAKEUP_MODE_RESERVE36_FLAG gesture wakeup.\n");

//                    input_report_key(g_InputDevice, RESERVER36, 1);
                    input_report_key(g_InputDevice, KEY_POWER, 1);
                    input_sync(g_InputDevice);
//                    input_report_key(g_InputDevice, RESERVER36, 0);
                    input_report_key(g_InputDevice, KEY_POWER, 0);
                    input_sync(g_InputDevice);
                    break;
                case 0x90:
                    _gGestureWakeupValue[1] = GESTURE_WAKEUP_MODE_RESERVE37_FLAG;

                    DBG("Light up screen by GESTURE_WAKEUP_MODE_RESERVE37_FLAG gesture wakeup.\n");

//                    input_report_key(g_InputDevice, RESERVER37, 1);
                    input_report_key(g_InputDevice, KEY_POWER, 1);
                    input_sync(g_InputDevice);
//                    input_report_key(g_InputDevice, RESERVER37, 0);
                    input_report_key(g_InputDevice, KEY_POWER, 0);
                    input_sync(g_InputDevice);
                    break;
                case 0x91:
                    _gGestureWakeupValue[1] = GESTURE_WAKEUP_MODE_RESERVE38_FLAG;

                    DBG("Light up screen by GESTURE_WAKEUP_MODE_RESERVE38_FLAG gesture wakeup.\n");

//                    input_report_key(g_InputDevice, RESERVER38, 1);
                    input_report_key(g_InputDevice, KEY_POWER, 1);
                    input_sync(g_InputDevice);
//                    input_report_key(g_InputDevice, RESERVER38, 0);
                    input_report_key(g_InputDevice, KEY_POWER, 0);
                    input_sync(g_InputDevice);
                    break;
                case 0x92:
                    _gGestureWakeupValue[1] = GESTURE_WAKEUP_MODE_RESERVE39_FLAG;

                    DBG("Light up screen by GESTURE_WAKEUP_MODE_RESERVE39_FLAG gesture wakeup.\n");

//                    input_report_key(g_InputDevice, RESERVER39, 1);
                    input_report_key(g_InputDevice, KEY_POWER, 1);
                    input_sync(g_InputDevice);
//                    input_report_key(g_InputDevice, RESERVER39, 0);
                    input_report_key(g_InputDevice, KEY_POWER, 0);
                    input_sync(g_InputDevice);
                    break;
                case 0x93:
                    _gGestureWakeupValue[1] = GESTURE_WAKEUP_MODE_RESERVE40_FLAG;

                    DBG("Light up screen by GESTURE_WAKEUP_MODE_RESERVE40_FLAG gesture wakeup.\n");

//                    input_report_key(g_InputDevice, RESERVER40, 1);
                    input_report_key(g_InputDevice, KEY_POWER, 1);
                    input_sync(g_InputDevice);
//                    input_report_key(g_InputDevice, RESERVER40, 0);
                    input_report_key(g_InputDevice, KEY_POWER, 0);
                    input_sync(g_InputDevice);
                    break;
                case 0x94:
                    _gGestureWakeupValue[1] = GESTURE_WAKEUP_MODE_RESERVE41_FLAG;

                    DBG("Light up screen by GESTURE_WAKEUP_MODE_RESERVE41_FLAG gesture wakeup.\n");

//                    input_report_key(g_InputDevice, RESERVER41, 1);
                    input_report_key(g_InputDevice, KEY_POWER, 1);
                    input_sync(g_InputDevice);
//                    input_report_key(g_InputDevice, RESERVER41, 0);
                    input_report_key(g_InputDevice, KEY_POWER, 0);
                    input_sync(g_InputDevice);
                    break;
                case 0x95:
                    _gGestureWakeupValue[1] = GESTURE_WAKEUP_MODE_RESERVE42_FLAG;

                    DBG("Light up screen by GESTURE_WAKEUP_MODE_RESERVE42_FLAG gesture wakeup.\n");

//                    input_report_key(g_InputDevice, RESERVER42, 1);
                    input_report_key(g_InputDevice, KEY_POWER, 1);
                    input_sync(g_InputDevice);
//                    input_report_key(g_InputDevice, RESERVER42, 0);
                    input_report_key(g_InputDevice, KEY_POWER, 0);
                    input_sync(g_InputDevice);
                    break;
                case 0x96:
                    _gGestureWakeupValue[1] = GESTURE_WAKEUP_MODE_RESERVE43_FLAG;

                    DBG("Light up screen by GESTURE_WAKEUP_MODE_RESERVE43_FLAG gesture wakeup.\n");

//                    input_report_key(g_InputDevice, RESERVER43, 1);
                    input_report_key(g_InputDevice, KEY_POWER, 1);
                    input_sync(g_InputDevice);
//                    input_report_key(g_InputDevice, RESERVER43, 0);
                    input_report_key(g_InputDevice, KEY_POWER, 0);
                    input_sync(g_InputDevice);
                    break;
                case 0x97:
                    _gGestureWakeupValue[1] = GESTURE_WAKEUP_MODE_RESERVE44_FLAG;

                    DBG("Light up screen by GESTURE_WAKEUP_MODE_RESERVE44_FLAG gesture wakeup.\n");

//                    input_report_key(g_InputDevice, RESERVER44, 1);
                    input_report_key(g_InputDevice, KEY_POWER, 1);
                    input_sync(g_InputDevice);
//                    input_report_key(g_InputDevice, RESERVER44, 0);
                    input_report_key(g_InputDevice, KEY_POWER, 0);
                    input_sync(g_InputDevice);
                    break;
                case 0x98:
                    _gGestureWakeupValue[1] = GESTURE_WAKEUP_MODE_RESERVE45_FLAG;

                    DBG("Light up screen by GESTURE_WAKEUP_MODE_RESERVE45_FLAG gesture wakeup.\n");

//                    input_report_key(g_InputDevice, RESERVER45, 1);
                    input_report_key(g_InputDevice, KEY_POWER, 1);
                    input_sync(g_InputDevice);
//                    input_report_key(g_InputDevice, RESERVER45, 0);
                    input_report_key(g_InputDevice, KEY_POWER, 0);
                    input_sync(g_InputDevice);
                    break;
                case 0x99:
                    _gGestureWakeupValue[1] = GESTURE_WAKEUP_MODE_RESERVE46_FLAG;

                    DBG("Light up screen by GESTURE_WAKEUP_MODE_RESERVE46_FLAG gesture wakeup.\n");

//                    input_report_key(g_InputDevice, RESERVER46, 1);
                    input_report_key(g_InputDevice, KEY_POWER, 1);
                    input_sync(g_InputDevice);
//                    input_report_key(g_InputDevice, RESERVER46, 0);
                    input_report_key(g_InputDevice, KEY_POWER, 0);
                    input_sync(g_InputDevice);
                    break;
                case 0x9A:
                    _gGestureWakeupValue[1] = GESTURE_WAKEUP_MODE_RESERVE47_FLAG;

                    DBG("Light up screen by GESTURE_WAKEUP_MODE_RESERVE47_FLAG gesture wakeup.\n");

//                    input_report_key(g_InputDevice, RESERVER47, 1);
                    input_report_key(g_InputDevice, KEY_POWER, 1);
                    input_sync(g_InputDevice);
//                    input_report_key(g_InputDevice, RESERVER47, 0);
                    input_report_key(g_InputDevice, KEY_POWER, 0);
                    input_sync(g_InputDevice);
                    break;
                case 0x9B:
                    _gGestureWakeupValue[1] = GESTURE_WAKEUP_MODE_RESERVE48_FLAG;

                    DBG("Light up screen by GESTURE_WAKEUP_MODE_RESERVE48_FLAG gesture wakeup.\n");

//                    input_report_key(g_InputDevice, RESERVER48, 1);
                    input_report_key(g_InputDevice, KEY_POWER, 1);
                    input_sync(g_InputDevice);
//                    input_report_key(g_InputDevice, RESERVER48, 0);
                    input_report_key(g_InputDevice, KEY_POWER, 0);
                    input_sync(g_InputDevice);
                    break;
                case 0x9C:
                    _gGestureWakeupValue[1] = GESTURE_WAKEUP_MODE_RESERVE49_FLAG;

                    DBG("Light up screen by GESTURE_WAKEUP_MODE_RESERVE49_FLAG gesture wakeup.\n");

//                    input_report_key(g_InputDevice, RESERVER49, 1);
                    input_report_key(g_InputDevice, KEY_POWER, 1);
                    input_sync(g_InputDevice);
//                    input_report_key(g_InputDevice, RESERVER49, 0);
                    input_report_key(g_InputDevice, KEY_POWER, 0);
                    input_sync(g_InputDevice);
                    break;
                case 0x9D:
                    _gGestureWakeupValue[1] = GESTURE_WAKEUP_MODE_RESERVE50_FLAG;

                    DBG("Light up screen by GESTURE_WAKEUP_MODE_RESERVE50_FLAG gesture wakeup.\n");

//                    input_report_key(g_InputDevice, RESERVER50, 1);
                    input_report_key(g_InputDevice, KEY_POWER, 1);
                    input_sync(g_InputDevice);
//                    input_report_key(g_InputDevice, RESERVER50, 0);
                    input_report_key(g_InputDevice, KEY_POWER, 0);
                    input_sync(g_InputDevice);
                    break;
                case 0x9E:
                    _gGestureWakeupValue[1] = GESTURE_WAKEUP_MODE_RESERVE51_FLAG;

                    DBG("Light up screen by GESTURE_WAKEUP_MODE_RESERVE51_FLAG gesture wakeup.\n");

//                    input_report_key(g_InputDevice, RESERVER51, 1);
                    input_report_key(g_InputDevice, KEY_POWER, 1);
                    input_sync(g_InputDevice);
//                    input_report_key(g_InputDevice, RESERVER51, 0);
                    input_report_key(g_InputDevice, KEY_POWER, 0);
                    input_sync(g_InputDevice);
                    break;
#endif //CONFIG_SUPPORT_64_TYPES_GESTURE_WAKEUP_MODE

#ifdef CONFIG_ENABLE_GESTURE_DEBUG_MODE
				case 0xFF://Gesture Fail
	            	_gGestureWakeupValue[1] = 0xFF;

                    DBG("Light up screen by GESTURE_WAKEUP_MODE_FAIL gesture wakeup.\n");

//                    input_report_key(g_InputDevice, RESERVER51, 1);
                    input_report_key(g_InputDevice, KEY_POWER, 1);
                    input_sync(g_InputDevice);
//                    input_report_key(g_InputDevice, RESERVER51, 0);
                    input_report_key(g_InputDevice, KEY_POWER, 0);
                    input_sync(g_InputDevice);
                    break;
#endif //CONFIG_ENABLE_GESTURE_DEBUG_MODE
                default:
                    _gGestureWakeupValue[0] = 0;
                    _gGestureWakeupValue[1] = 0;
                    DBG("Un-supported gesture wakeup mode. Please check your device driver code.\n");
                    break;		
            }

            DBG("_gGestureWakeupValue[0] = 0x%x\n", _gGestureWakeupValue[0]);
            DBG("_gGestureWakeupValue[1] = 0x%x\n", _gGestureWakeupValue[1]);
        }
        else
        {
            DBG("gesture wakeup packet format is incorrect.\n");
        }
        
#ifdef CONFIG_ENABLE_GESTURE_DEBUG_MODE
		// Notify android application to retrieve log data mode packet from device driver by sysfs.
		if (g_GestureKObj != NULL && pPacket[3] == PACKET_TYPE_GESTURE_DEBUG)
		{
			char *pEnvp[2];
			s32 nRetVal = 0;

			pEnvp[0] = "STATUS=GET_GESTURE_DEBUG";
			pEnvp[1] = NULL;

			nRetVal = kobject_uevent_env(g_GestureKObj, KOBJ_CHANGE, pEnvp);
			DBG("kobject_uevent_env() nRetVal = %d\n", nRetVal);

		}
#endif //CONFIG_ENABLE_GESTURE_DEBUG_MODE

        return -1;
    }
#endif //CONFIG_ENABLE_GESTURE_WAKEUP

    DBG("received raw data from touch panel as following:\n");
    DBG("pPacket[0]=%x \n pPacket[1]=%x pPacket[2]=%x pPacket[3]=%x pPacket[4]=%x \n pPacket[5]=%x pPacket[6]=%x pPacket[7]=%x \n", \
                pPacket[0], pPacket[1], pPacket[2], pPacket[3], pPacket[4], pPacket[5], pPacket[6], pPacket[7]);

    if ((pPacket[nCheckSumIndex] == nCheckSum) && (pPacket[0] == 0x52))   // check the checksum of packet
    {
        nX = (((pPacket[1] & 0xF0) << 4) | pPacket[2]);         // parse the packet to coordinate
        nY = (((pPacket[1] & 0x0F) << 8) | pPacket[3]);

        nDeltaX = (((pPacket[4] & 0xF0) << 4) | pPacket[5]);
        nDeltaY = (((pPacket[4] & 0x0F) << 8) | pPacket[6]);

        DBG("[x,y]=[%d,%d]\n", nX, nY);
        DBG("[delta_x,delta_y]=[%d,%d]\n", nDeltaX, nDeltaY);

#ifdef CONFIG_SWAP_X_Y
        nTempY = nX;
        nTempX = nY;
        nX = nTempX;
        nY = nTempY;
        
        nTempY = nDeltaX;
        nTempX = nDeltaY;
        nDeltaX = nTempX;
        nDeltaY = nTempY;
#endif

#ifdef CONFIG_REVERSE_X
        nX = 2047 - nX;
        nDeltaX = 4095 - nDeltaX;
#endif

#ifdef CONFIG_REVERSE_Y
        nY = 2047 - nY;
        nDeltaY = 4095 - nDeltaY;
#endif

        /*
         * pPacket[0]:id, pPacket[1]~pPacket[3]:the first point abs, pPacket[4]~pPacket[6]:the relative distance between the first point abs and the second point abs
         * when pPacket[1]~pPacket[4], pPacket[6] is 0xFF, keyevent, pPacket[5] to judge which key press.
         * pPacket[1]~pPacket[6] all are 0xFF, release touch
        */
        if ((pPacket[1] == 0xFF) && (pPacket[2] == 0xFF) && (pPacket[3] == 0xFF) && (pPacket[4] == 0xFF) && (pPacket[6] == 0xFF))
        {
            pInfo->tPoint[0].nX = 0; // final X coordinate
            pInfo->tPoint[0].nY = 0; // final Y coordinate

            if ((pPacket[5] != 0x00) && (pPacket[5] != 0xFF)) /* pPacket[5] is key value */
            {   /* 0x00 is key up, 0xff is touch screen up */
#ifdef CONFIG_ENABLE_PROXIMITY_DETECTION
#if defined(CONFIG_TOUCH_DRIVER_RUN_ON_SPRD_PLATFORM) || defined(CONFIG_TOUCH_DRIVER_RUN_ON_QCOM_PLATFORM)
                DBG("g_EnableTpProximity = %d, pPacket[5] = 0x%x\n", g_EnableTpProximity, pPacket[5]);

                if (g_EnableTpProximity && ((pPacket[5] == 0x80) || (pPacket[5] == 0x40)))
                {
                    if (pPacket[5] == 0x80) // close to
                    {
                        g_FaceClosingTp = 1;

                        input_report_abs(g_ProximityInputDevice, ABS_DISTANCE, 0);
                        input_sync(g_ProximityInputDevice);
                    }
                    else if (pPacket[5] == 0x40) // far away
                    {
                        g_FaceClosingTp = 0;

                        input_report_abs(g_ProximityInputDevice, ABS_DISTANCE, 1);
                        input_sync(g_ProximityInputDevice);
                    }

                    DBG("g_FaceClosingTp = %d\n", g_FaceClosingTp);
                   
                    return -1;
                }
#elif defined(CONFIG_TOUCH_DRIVER_RUN_ON_MTK_PLATFORM)
                if (g_EnableTpProximity && ((pPacket[5] == 0x80) || (pPacket[5] == 0x40)))
                {
                    int nErr;
                    hwm_sensor_data tSensorData;

                    if (pPacket[5] == 0x80) // close to
                    {
                        g_FaceClosingTp = 0;
                    }
                    else if (pPacket[5] == 0x40) // far away
                    {
                        g_FaceClosingTp = 1;
                    }
                    
                    DBG("g_FaceClosingTp = %d\n", g_FaceClosingTp);

                    // map and store data to hwm_sensor_data
                    tSensorData.values[0] = DrvPlatformLyrGetTpPsData();
                    tSensorData.value_divide = 1;
                    tSensorData.status = SENSOR_STATUS_ACCURACY_MEDIUM;
                    // let up layer to know
                    if ((nErr = hwmsen_get_interrupt_data(ID_PROXIMITY, &tSensorData)))
                    {
                        DBG("call hwmsen_get_interrupt_data() failed = %d\n", nErr);
                    }
                    
                    return -1;
                }
#endif               
#endif //CONFIG_ENABLE_PROXIMITY_DETECTION

                /* 0x00 is key up, 0xff is touch screen up */
                DBG("touch key down pPacket[5]=%d\n", pPacket[5]);

                pInfo->nFingerNum = 1;
                pInfo->nTouchKeyCode = pPacket[5];
                pInfo->nTouchKeyMode = 1;

#ifdef CONFIG_TOUCH_DRIVER_RUN_ON_MTK_PLATFORM
#ifdef CONFIG_ENABLE_REPORT_KEY_WITH_COORDINATE
                pInfo->nFingerNum = 1;
                pInfo->nTouchKeyCode = 0;
                pInfo->nTouchKeyMode = 0;

                if (pPacket[5] == 4) // TOUCH_KEY_HOME
                {
                    pInfo->tPoint[0].nX = g_TpVirtualKeyDimLocal[1][0];
                    pInfo->tPoint[0].nY = g_TpVirtualKeyDimLocal[1][1];
                }
                else if (pPacket[5] == 1) // TOUCH_KEY_MENU
                {
                    pInfo->tPoint[0].nX = g_TpVirtualKeyDimLocal[0][0];
                    pInfo->tPoint[0].nY = g_TpVirtualKeyDimLocal[0][1];
                }           
                else if (pPacket[5] == 2) // TOUCH_KEY_BACK
                {
                    pInfo->tPoint[0].nX = g_TpVirtualKeyDimLocal[2][0];
                    pInfo->tPoint[0].nY = g_TpVirtualKeyDimLocal[2][1];
                }           
                else if (pPacket[5] == 8) // TOUCH_KEY_SEARCH 
                {	
                    pInfo->tPoint[0].nX = g_TpVirtualKeyDimLocal[3][0];
                    pInfo->tPoint[0].nY = g_TpVirtualKeyDimLocal[3][1];
                }
                else
                {
                    DBG("multi-key is pressed.\n");

                    return -1;
                }
#endif //CONFIG_ENABLE_REPORT_KEY_WITH_COORDINATE
#endif //CONFIG_TOUCH_DRIVER_RUN_ON_MTK_PLATFORM
            }
            else
            {   /* key up or touch up */
                DBG("touch end\n");
                pInfo->nFingerNum = 0; //touch end
                pInfo->nTouchKeyCode = 0;
                pInfo->nTouchKeyMode = 0;    
            }

#ifdef CONFIG_ENABLE_TYPE_B_PROTOCOL
            _gPrevTouchStatus = 0;
#endif //CONFIG_ENABLE_TYPE_B_PROTOCOL 
        }
        else
        {
            pInfo->nTouchKeyMode = 0; //Touch on screen...

//            if ((nDeltaX == 0) && (nDeltaY == 0))
            if (
#ifdef CONFIG_REVERSE_X
                (nDeltaX == 4095)
#else
                (nDeltaX == 0)
#endif
                &&
#ifdef CONFIG_REVERSE_Y
                (nDeltaY == 4095)
#else
                (nDeltaY == 0)
#endif
                )
            {   /* one touch point */
                pInfo->nFingerNum = 1; // one touch
                pInfo->tPoint[0].nX = (nX * TOUCH_SCREEN_X_MAX) / TPD_WIDTH;
                pInfo->tPoint[0].nY = (nY * TOUCH_SCREEN_Y_MAX) / TPD_HEIGHT;
                DBG("[%s]: [x,y]=[%d,%d]\n", __func__, nX, nY);
                DBG("[%s]: point[x,y]=[%d,%d]\n", __func__, pInfo->tPoint[0].nX, pInfo->tPoint[0].nY);

#ifdef CONFIG_ENABLE_TYPE_B_PROTOCOL
                _gCurrPress[0] = 1;
                _gCurrPress[1] = 0;
#endif //CONFIG_ENABLE_TYPE_B_PROTOCOL
            }
            else
            {   /* two touch points */
                u32 nX2, nY2;
                
                pInfo->nFingerNum = 2; // two touch
                /* Finger 1 */
                pInfo->tPoint[0].nX = (nX * TOUCH_SCREEN_X_MAX) / TPD_WIDTH;
                pInfo->tPoint[0].nY = (nY * TOUCH_SCREEN_Y_MAX) / TPD_HEIGHT;
                DBG("[%s]: point1[x,y]=[%d,%d]\n", __func__, pInfo->tPoint[0].nX, pInfo->tPoint[0].nY);
                /* Finger 2 */
                if (nDeltaX > 2048)     // transform the unsigned value to signed value
                {
                    nDeltaX -= 4096;
                }
                
                if (nDeltaY > 2048)
                {
                    nDeltaY -= 4096;
                }

                nX2 = (u32)(nX + nDeltaX);
                nY2 = (u32)(nY + nDeltaY);

                pInfo->tPoint[1].nX = (nX2 * TOUCH_SCREEN_X_MAX) / TPD_WIDTH; 
                pInfo->tPoint[1].nY = (nY2 * TOUCH_SCREEN_Y_MAX) / TPD_HEIGHT;
                DBG("[%s]: point2[x,y]=[%d,%d]\n", __func__, pInfo->tPoint[1].nX, pInfo->tPoint[1].nY);

#ifdef CONFIG_ENABLE_TYPE_B_PROTOCOL
                _gCurrPress[0] = 1;
                _gCurrPress[1] = 1;
#endif //CONFIG_ENABLE_TYPE_B_PROTOCOL
            }

#ifdef CONFIG_ENABLE_TYPE_B_PROTOCOL
            if (_gPrevTouchStatus == 1)
            {
                for (i = 0; i < MAX_TOUCH_NUM; i ++)
                {
                    szX[i] = pInfo->tPoint[i].nX;
                    szY[i] = pInfo->tPoint[i].nY;
                }
			
                if (/*(pInfo->nFingerNum == 1)&&*/(nPrevTouchNum == 2))
                {
                    if (_DrvFwCtrlPointDistance(szX[0], szY[0], szPrevX[0], szPrevY[0]) > _DrvFwCtrlPointDistance(szX[0], szY[0], szPrevX[1], szPrevY[1]))
                    {
                        nChangePoints = 1;
                    }
                }
                else if ((pInfo->nFingerNum == 2) && (nPrevTouchNum == 1))
                {
                    if (szPrevPress[0] == 1)
                    {
                        if(_DrvFwCtrlPointDistance(szX[0], szY[0], szPrevX[0] ,szPrevY[0]) > _DrvFwCtrlPointDistance(szX[1], szY[1], szPrevX[0], szPrevY[0]))
                        {
                            nChangePoints = 1;
                        }
                    }
                    else
                    {
                        if (_DrvFwCtrlPointDistance(szX[0], szY[0], szPrevX[1], szPrevY[1]) < _DrvFwCtrlPointDistance(szX[1], szY[1], szPrevX[1], szPrevY[1]))
                        {
                            nChangePoints = 1;
                        }
                    }
                }
                else if ((pInfo->nFingerNum == 1) && (nPrevTouchNum == 1))
                {
                    if (_gCurrPress[0] != szPrevPress[0])
                    {
                        nChangePoints = 1;
                    }
                }
//                else if ((pInfo->nFingerNum == 2) && (nPrevTouchNum == 2))
//                {
//                }

                if (nChangePoints == 1)
                {
                    nTemp = _gCurrPress[0];
                    _gCurrPress[0] = _gCurrPress[1];
                    _gCurrPress[1] = nTemp;

                    nTemp = pInfo->tPoint[0].nX;
                    pInfo->tPoint[0].nX = pInfo->tPoint[1].nX;
                    pInfo->tPoint[1].nX = nTemp;

                    nTemp = pInfo->tPoint[0].nY;
                    pInfo->tPoint[0].nY = pInfo->tPoint[1].nY;
                    pInfo->tPoint[1].nY = nTemp;
                }
            }

            // Save current status
            for (i = 0; i < MAX_TOUCH_NUM; i ++)
            {
                szPrevPress[i] = _gCurrPress[i];
                szPrevX[i] = pInfo->tPoint[i].nX;
                szPrevY[i] = pInfo->tPoint[i].nY;
            }
            nPrevTouchNum = pInfo->nFingerNum;

            _gPrevTouchStatus = 1;
#endif //CONFIG_ENABLE_TYPE_B_PROTOCOL
        }
    }
    else if (pPacket[nCheckSumIndex] == nCheckSum && pPacket[0] == 0x62)
    {
        nX = ((pPacket[1] << 8) | pPacket[2]);  // Position_X
        nY = ((pPacket[3] << 8) | pPacket[4]);  // Position_Y

        nDeltaX = ((pPacket[13] << 8) | pPacket[14]); // Distance_X
        nDeltaY = ((pPacket[15] << 8) | pPacket[16]); // Distance_Y

        DBG("[x,y]=[%d,%d]\n", nX, nY);
        DBG("[delta_x,delta_y]=[%d,%d]\n", nDeltaX, nDeltaY);

#ifdef CONFIG_SWAP_X_Y
        nTempY = nX;
        nTempX = nY;
        nX = nTempX;
        nY = nTempY;
        
        nTempY = nDeltaX;
        nTempX = nDeltaY;
        nDeltaX = nTempX;
        nDeltaY = nTempY;
#endif

#ifdef CONFIG_REVERSE_X
        nX = 2047 - nX;
        nDeltaX = 4095 - nDeltaX;
#endif

#ifdef CONFIG_REVERSE_Y
        nY = 2047 - nY;
        nDeltaY = 4095 - nDeltaY;
#endif

        /*
         * pPacket[0]:id, pPacket[1]~pPacket[4]:the first point abs, pPacket[13]~pPacket[16]:the relative distance between the first point abs and the second point abs
         * when pPacket[1]~pPacket[7] is 0xFF, keyevent, pPacket[8] to judge which key press.
         * pPacket[1]~pPacket[8] all are 0xFF, release touch
         */
        if ((pPacket[1] == 0xFF) && (pPacket[2] == 0xFF) && (pPacket[3] == 0xFF) && (pPacket[4] == 0xFF) && (pPacket[5] == 0xFF) && (pPacket[6] == 0xFF) && (pPacket[7] == 0xFF))
        {
            pInfo->tPoint[0].nX = 0; // final X coordinate
            pInfo->tPoint[0].nY = 0; // final Y coordinate

            if ((pPacket[8] != 0x00) && (pPacket[8] != 0xFF)) /* pPacket[8] is key value */
            {   /* 0x00 is key up, 0xff is touch screen up */
                DBG("touch key down pPacket[8]=%d\n", pPacket[8]);
                pInfo->nFingerNum = 1;
                pInfo->nTouchKeyCode = pPacket[8];
                pInfo->nTouchKeyMode = 1;

#ifdef CONFIG_TOUCH_DRIVER_RUN_ON_MTK_PLATFORM
#ifdef CONFIG_ENABLE_REPORT_KEY_WITH_COORDINATE
                pInfo->nFingerNum = 1;
                pInfo->nTouchKeyCode = 0;
                pInfo->nTouchKeyMode = 0;

                if (pPacket[8] == 4) // TOUCH_KEY_HOME
                {
                    pInfo->tPoint[0].nX = g_TpVirtualKeyDimLocal[1][0];
                    pInfo->tPoint[0].nY = g_TpVirtualKeyDimLocal[1][1];
                }
                else if (pPacket[8] == 1) // TOUCH_KEY_MENU
                {
                    pInfo->tPoint[0].nX = g_TpVirtualKeyDimLocal[0][0];
                    pInfo->tPoint[0].nY = g_TpVirtualKeyDimLocal[0][1];
                }           
                else if (pPacket[8] == 2) // TOUCH_KEY_BACK
                {
                    pInfo->tPoint[0].nX = g_TpVirtualKeyDimLocal[2][0];
                    pInfo->tPoint[0].nY = g_TpVirtualKeyDimLocal[2][1];
                }           
                else if (pPacket[8] == 8) // TOUCH_KEY_SEARCH 
                {	
                    pInfo->tPoint[0].nX = g_TpVirtualKeyDimLocal[3][0];
                    pInfo->tPoint[0].nY = g_TpVirtualKeyDimLocal[3][1];
                }
                else
                {
                    DBG("multi-key is pressed.\n");

                    return -1;
                }
#endif //CONFIG_ENABLE_REPORT_KEY_WITH_COORDINATE
#endif //CONFIG_TOUCH_DRIVER_RUN_ON_MTK_PLATFORM
            }
            else
            {   /* key up or touch up */
                DBG("touch end\n");
                pInfo->nFingerNum = 0; //touch end
                pInfo->nTouchKeyCode = 0;
                pInfo->nTouchKeyMode = 0;    
            }

#ifdef CONFIG_ENABLE_TYPE_B_PROTOCOL
            _gPrevTouchStatus = 0;
#endif //CONFIG_ENABLE_TYPE_B_PROTOCOL 
        }
        else
        {
            pInfo->nTouchKeyMode = 0; //Touch on screen...

//            if ((nDeltaX == 0) && (nDeltaY == 0))
            if (
#ifdef CONFIG_REVERSE_X
                (nDeltaX == 4095)
#else
                (nDeltaX == 0)
#endif
                &&
#ifdef CONFIG_REVERSE_Y
                (nDeltaY == 4095)
#else
                (nDeltaY == 0)
#endif
                )
            {   /* one touch point */
                pInfo->nFingerNum = 1; // one touch
                pInfo->tPoint[0].nX = (nX * TOUCH_SCREEN_X_MAX) / TPD_WIDTH;
                pInfo->tPoint[0].nY = (nY * TOUCH_SCREEN_Y_MAX) / TPD_HEIGHT;
                DBG("[%s]: [x,y]=[%d,%d]\n", __func__, nX, nY);
                DBG("[%s]: point[x,y]=[%d,%d]\n", __func__, pInfo->tPoint[0].nX, pInfo->tPoint[0].nY);

#ifdef CONFIG_ENABLE_TYPE_B_PROTOCOL
                _gCurrPress[0] = 1;
                _gCurrPress[1] = 0;
#endif //CONFIG_ENABLE_TYPE_B_PROTOCOL
            }
            else
            {   /* two touch points */
                u32 nX2, nY2;
                
                pInfo->nFingerNum = 2; // two touch
                /* Finger 1 */
                pInfo->tPoint[0].nX = (nX * TOUCH_SCREEN_X_MAX) / TPD_WIDTH;
                pInfo->tPoint[0].nY = (nY * TOUCH_SCREEN_Y_MAX) / TPD_HEIGHT;
                DBG("[%s]: point1[x,y]=[%d,%d]\n", __func__, pInfo->tPoint[0].nX, pInfo->tPoint[0].nY);
                /* Finger 2 */
                if (nDeltaX > 2048)     // transform the unsigned value to signed value
                {
                    nDeltaX -= 4096;
                }
                
                if (nDeltaY > 2048)
                {
                    nDeltaY -= 4096;
                }

                nX2 = (u32)(nX + nDeltaX);
                nY2 = (u32)(nY + nDeltaY);

                pInfo->tPoint[1].nX = (nX2 * TOUCH_SCREEN_X_MAX) / TPD_WIDTH; 
                pInfo->tPoint[1].nY = (nY2 * TOUCH_SCREEN_Y_MAX) / TPD_HEIGHT;
                DBG("[%s]: point2[x,y]=[%d,%d]\n", __func__, pInfo->tPoint[1].nX, pInfo->tPoint[1].nY);

#ifdef CONFIG_ENABLE_TYPE_B_PROTOCOL
                _gCurrPress[0] = 1;
                _gCurrPress[1] = 1;
#endif //CONFIG_ENABLE_TYPE_B_PROTOCOL
            }

#ifdef CONFIG_ENABLE_TYPE_B_PROTOCOL
            if (_gPrevTouchStatus == 1)
            {
                for (i = 0; i < MAX_TOUCH_NUM; i ++)
                {
                    szX[i] = pInfo->tPoint[i].nX;
                    szY[i] = pInfo->tPoint[i].nY;
                }
			
                if (/*(pInfo->nFingerNum == 1)&&*/(nPrevTouchNum == 2))
                {
                    if (_DrvFwCtrlPointDistance(szX[0], szY[0], szPrevX[0], szPrevY[0]) > _DrvFwCtrlPointDistance(szX[0], szY[0], szPrevX[1], szPrevY[1]))
                    {
                        nChangePoints = 1;
                    }
                }
                else if ((pInfo->nFingerNum == 2) && (nPrevTouchNum == 1))
                {
                    if (szPrevPress[0] == 1)
                    {
                        if(_DrvFwCtrlPointDistance(szX[0], szY[0], szPrevX[0] ,szPrevY[0]) > _DrvFwCtrlPointDistance(szX[1], szY[1], szPrevX[0], szPrevY[0]))
                        {
                            nChangePoints = 1;
                        }
                    }
                    else
                    {
                        if (_DrvFwCtrlPointDistance(szX[0], szY[0], szPrevX[1], szPrevY[1]) < _DrvFwCtrlPointDistance(szX[1], szY[1], szPrevX[1], szPrevY[1]))
                        {
                            nChangePoints = 1;
                        }
                    }
                }
                else if ((pInfo->nFingerNum == 1) && (nPrevTouchNum == 1))
                {
                    if (_gCurrPress[0] != szPrevPress[0])
                    {
                        nChangePoints = 1;
                    }
                }
//                else if ((pInfo->nFingerNum == 2) && (nPrevTouchNum == 2))
//                {
//                }

                if (nChangePoints == 1)
                {
                    nTemp = _gCurrPress[0];
                    _gCurrPress[0] = _gCurrPress[1];
                    _gCurrPress[1] = nTemp;

                    nTemp = pInfo->tPoint[0].nX;
                    pInfo->tPoint[0].nX = pInfo->tPoint[1].nX;
                    pInfo->tPoint[1].nX = nTemp;

                    nTemp = pInfo->tPoint[0].nY;
                    pInfo->tPoint[0].nY = pInfo->tPoint[1].nY;
                    pInfo->tPoint[1].nY = nTemp;
                }
            }

            // Save current status
            for (i = 0; i < MAX_TOUCH_NUM; i ++)
            {
                szPrevPress[i] = _gCurrPress[i];
                szPrevX[i] = pInfo->tPoint[i].nX;
                szPrevY[i] = pInfo->tPoint[i].nY;
            }
            nPrevTouchNum = pInfo->nFingerNum;

            _gPrevTouchStatus = 1;
#endif //CONFIG_ENABLE_TYPE_B_PROTOCOL

            // Notify android application to retrieve log data mode packet from device driver by sysfs.   
            if (g_TouchKObj != NULL)
            {
                char *pEnvp[2];
                s32 nRetVal = 0; 

                pEnvp[0] = "STATUS=GET_PACKET";  
                pEnvp[1] = NULL;  
    
                nRetVal = kobject_uevent_env(g_TouchKObj, KOBJ_CHANGE, pEnvp); 
                DBG("kobject_uevent_env() nRetVal = %d\n", nRetVal);
            }
        }
    }
    else
    {
        DBG("pPacket[0]=0x%x, pPacket[7]=0x%x, nCheckSum=0x%x\n", pPacket[0], pPacket[7], nCheckSum);

        if (pPacket[nCheckSumIndex] != nCheckSum)
        {
            DBG("WRONG CHECKSUM\n");
            return -1;
        }

        if (g_FirmwareMode == FIRMWARE_MODE_DEMO_MODE && pPacket[0] != 0x52)
        {
            DBG("WRONG DEMO MODE HEADER\n");
            return -1;
        }
        else if (g_FirmwareMode == FIRMWARE_MODE_DEBUG_MODE && pPacket[0] != 0x62)
        {
            DBG("WRONG DEBUG MODE HEADER\n");
            return -1;
        }
        else if (g_FirmwareMode == FIRMWARE_MODE_RAW_DATA_MODE && pPacket[0] != 0x62)
        {
            DBG("WRONG RAW DATA MODE HEADER\n");
            return -1;
        }
    }

    return 0;
}

static void _DrvFwCtrlStoreFirmwareData(u8 *pBuf, u32 nSize)
{
    u32 nCount = nSize / 1024;
    u32 nRemainder = nSize % 1024;
    u32 i;

    DBG("*** %s() ***\n", __func__);

    if (nCount > 0) // nSize >= 1024
   	{
        for (i = 0; i < nCount; i ++)
        {
            memcpy(g_FwData[g_FwDataCount], pBuf+(i*1024), 1024);

            g_FwDataCount ++;
        }
        
        if (nRemainder > 0) // Handle special firmware size like MSG22XX(48.5KB)
        {
            DBG("nRemainder = %d\n", nRemainder);

            memcpy(g_FwData[g_FwDataCount], pBuf+(i*1024), nRemainder);

            g_FwDataCount ++;
        }
    }
    else // nSize < 1024
    {
        if (nSize > 0)
        {
            memcpy(g_FwData[g_FwDataCount], pBuf, nSize);

            g_FwDataCount ++;
        }
    }

    DBG("*** g_FwDataCount = %d ***\n", g_FwDataCount);

    if (pBuf != NULL)
    {
        DBG("*** buf[0] = %c ***\n", pBuf[0]);
    }
}

static u16 _DrvFwCtrlMsg21xxaGetSwId(EmemType_e eEmemType) 
{
    u16 nRetVal = 0; 
    u16 nRegData = 0;
    u8 szDbBusTxData[5] = {0};
    u8 szDbBusRxData[4] = {0};

    DBG("*** %s() eEmemType = %d ***\n", __func__, eEmemType);

    DbBusEnterSerialDebugMode();
    DbBusStopMCU();
    DbBusIICUseBus();
    DbBusIICReshape();
    mdelay(100);

    // Stop mcu
    RegSetLByteValue(0x0FE6, 0x01); //bank:mheg5, addr:h0073

    // Stop watchdog
    RegSet16BitValue(0x3C60, 0xAA55); //bank:reg_PIU_MISC_0, addr:h0030

    // cmd
    RegSet16BitValue(0x3CE4, 0xA4AB); //bank:reg_PIU_MISC_0, addr:h0072

    // TP SW reset
    RegSet16BitValue(0x1E04, 0x7d60); //bank:chip, addr:h0002
    RegSet16BitValue(0x1E04, 0x829F);

    // Start mcu
    RegSetLByteValue(0x0FE6, 0x00); //bank:mheg5, addr:h0073
    
    mdelay(100);

    // Polling 0x3CE4 is 0x5B58
    do
    {
        nRegData = RegGet16BitValue(0x3CE4); //bank:reg_PIU_MISC_0, addr:h0072
    } while (nRegData != 0x5B58);

    szDbBusTxData[0] = 0x72;
    if (eEmemType == EMEM_MAIN) // Read SW ID from main block
    {
        szDbBusTxData[1] = 0x7F;
        szDbBusTxData[2] = 0x55;
    }
    else if (eEmemType == EMEM_INFO) // Read SW ID from info block
    {
        szDbBusTxData[1] = 0x83;
        szDbBusTxData[2] = 0x00;
    }
    szDbBusTxData[3] = 0x00;
    szDbBusTxData[4] = 0x04;

    IicWriteData(SLAVE_I2C_ID_DWI2C, &szDbBusTxData[0], 5);
    IicReadData(SLAVE_I2C_ID_DWI2C, &szDbBusRxData[0], 4);

    DBG("szDbBusRxData[0,1,2,3] = 0x%x,0x%x,0x%x,0x%x\n", szDbBusRxData[0], szDbBusRxData[1], szDbBusRxData[2], szDbBusRxData[3]);

    if ((szDbBusRxData[0] >= 0x30 && szDbBusRxData[0] <= 0x39)
        &&(szDbBusRxData[1] >= 0x30 && szDbBusRxData[1] <= 0x39)
        &&(szDbBusRxData[2] >= 0x31 && szDbBusRxData[2] <= 0x39))  
    {
        nRetVal = (szDbBusRxData[0]-0x30)*100+(szDbBusRxData[1]-0x30)*10+(szDbBusRxData[2]-0x30);
    }
    
    DBG("SW ID = 0x%x\n", nRetVal);

    DbBusIICNotUseBus();
    DbBusNotStopMCU();
    DbBusExitSerialDebugMode();

    return nRetVal;		
}		
     
u16 _DrvFwCtrlMsg22xxGetSwId(EmemType_e eEmemType) 
{
    u16 nRetVal = 0; 
    u16 nRegData1 = 0;

    DBG("*** %s() eEmemType = %d ***\n", __func__, eEmemType);

    DbBusEnterSerialDebugMode();
    DbBusStopMCU();
    DbBusIICUseBus();
    DbBusIICReshape();
    mdelay(100);

#ifdef CONFIG_ENABLE_UPDATE_FIRMWARE_WITH_SUPPORT_I2C_SPEED_400K
    // Stop MCU
    RegSetLByteValue(0x0FE6, 0x01); 
    
    // Change MCU clock deglich mux source
    RegSet16BitValue(0x1E54, (RegGet16BitValue(0x1E54) | BIT0)); 

    // Change PIU clock to 48 MHz
    RegSet16BitValue(0x1E22, (RegGet16BitValue(0x1E22) | BIT14)); 

    // Set MCU clock setting
    RegSet16BitValue(0x1E22, (RegGet16BitValue(0x1E22) & (~(BIT3 | BIT2)))); 

    // Set DB bus clock setting
    RegSet16BitValue(0x1E24, (RegGet16BitValue(0x1E24) & (~(BIT3 | BIT2)))); 
#else
    // Stop MCU
    RegSetLByteValue(0x0FE6, 0x01); 
#endif //CONFIG_ENABLE_UPDATE_FIRMWARE_WITH_SUPPORT_I2C_SPEED_400K

    // Stop watchdog
    RegSet16BitValue(0x3C60, 0xAA55);

    // RIU password
    RegSet16BitValue(0x161A, 0xABBA); 

    if (eEmemType == EMEM_MAIN) // Read SW ID from main block
    {
        RegSet16BitValue(0x1600, 0xBFF4); // Set start address for main block SW ID
    }
    else if (eEmemType == EMEM_INFO) // Read SW ID from info block
    {
        RegSet16BitValue(0x1600, 0xC1EC); // Set start address for info block SW ID
    }

    /*
      Ex. SW ID in Main Block :
          Major low byte at address 0xBFF4
          Major high byte at address 0xBFF5
          
          SW ID in Info Block :
          Major low byte at address 0xC1EC
          Major high byte at address 0xC1ED
    */
    
    // Enable burst mode
//    RegSet16BitValue(0x160C, (RegGet16BitValue(0x160C) | 0x01));

    RegSetLByteValue(0x160E, 0x01); 

    nRegData1 = RegGet16BitValue(0x1604);
//    nRegData2 = RegGet16BitValue(0x1606);

    nRetVal = ((nRegData1 >> 8) & 0xFF) << 8;
    nRetVal |= (nRegData1 & 0xFF);
    
    // Clear burst mode
//    RegSet16BitValue(0x160C, RegGet16BitValue(0x160C) & (~0x01));      

    RegSet16BitValue(0x1600, 0x0000); 

    // Clear RIU password
    RegSet16BitValue(0x161A, 0x0000); 
    
    DBG("SW ID = 0x%x\n", nRetVal);

    DbBusIICNotUseBus();
    DbBusNotStopMCU();
    DbBusExitSerialDebugMode();

    return nRetVal;		
}

u32 DrvFwCtrlReadDQMemValue(u16 nAddr)
{
    // TODO : not support yet
    
    return 0;	
}	

void DrvFwCtrlWriteDQMemValue(u16 nAddr, u32 nData)
{
    // TODO : not support yet	
}

//------------------------------------------------------------------------------//

#ifdef CONFIG_UPDATE_FIRMWARE_BY_SW_ID

//-------------------------Start of SW ID for MSG22XX----------------------------//

static u32 _DrvFwCtrlMsg22xxRetrieveFirmwareCrcFromEFlash(EmemType_e eEmemType) 
{
    u32 nRetVal = 0; 
    u16 nRegData1 = 0, nRegData2 = 0;

    DBG("*** %s() eEmemType = %d ***\n", __func__, eEmemType);

    DbBusEnterSerialDebugMode();
    DbBusStopMCU();
    DbBusIICUseBus();
    DbBusIICReshape();
    mdelay(100);

#ifdef CONFIG_ENABLE_UPDATE_FIRMWARE_WITH_SUPPORT_I2C_SPEED_400K
    // Stop MCU
    RegSetLByteValue(0x0FE6, 0x01); 
    
    // Change MCU clock deglich mux source
    RegSet16BitValue(0x1E54, (RegGet16BitValue(0x1E54) | BIT0)); 

    // Change PIU clock to 48 MHz
    RegSet16BitValue(0x1E22, (RegGet16BitValue(0x1E22) | BIT14)); 

    // Set MCU clock setting
    RegSet16BitValue(0x1E22, (RegGet16BitValue(0x1E22) & (~(BIT3 | BIT2)))); 

    // Set DB bus clock setting
    RegSet16BitValue(0x1E24, (RegGet16BitValue(0x1E24) & (~(BIT3 | BIT2)))); 
#else
    // Stop MCU
    RegSetLByteValue(0x0FE6, 0x01); 
#endif //CONFIG_ENABLE_UPDATE_FIRMWARE_WITH_SUPPORT_I2C_SPEED_400K

    // Stop watchdog
    RegSet16BitValue(0x3C60, 0xAA55);

    // RIU password
    RegSet16BitValue(0x161A, 0xABBA); 

    if (eEmemType == EMEM_MAIN) // Read main block CRC(48KB-4) from main block
    {
        RegSet16BitValue(0x1600, 0xBFFC); // Set start address for main block CRC
    }
    else if (eEmemType == EMEM_INFO) // Read info block CRC(512Byte-4) from info block
    {
        RegSet16BitValue(0x1600, 0xC1FC); // Set start address for info block CRC
    }
    
    // Enable burst mode
    RegSet16BitValue(0x160C, (RegGet16BitValue(0x160C) | 0x01));

    RegSetLByteValue(0x160E, 0x01); 

    nRegData1 = RegGet16BitValue(0x1604);
    nRegData2 = RegGet16BitValue(0x1606);

    nRetVal  = ((nRegData2 >> 8) & 0xFF) << 24;
    nRetVal |= (nRegData2 & 0xFF) << 16;
    nRetVal |= ((nRegData1 >> 8) & 0xFF) << 8;
    nRetVal |= (nRegData1 & 0xFF);
    
    DBG("CRC = 0x%x\n", nRetVal);

    // Clear burst mode
    RegSet16BitValue(0x160C, RegGet16BitValue(0x160C) & (~0x01));      

    RegSet16BitValue(0x1600, 0x0000); 

    // Clear RIU password
    RegSet16BitValue(0x161A, 0x0000); 

    DbBusIICNotUseBus();
    DbBusNotStopMCU();
    DbBusExitSerialDebugMode();

    return nRetVal;	
}

static u32 _DrvFwCtrlMsg22xxRetrieveFrimwareCrcFromBinFile(u8 szTmpBuf[], EmemType_e eEmemType) 
{
    u32 nRetVal = 0; 
    
    DBG("*** %s() eEmemType = %d ***\n", __func__, eEmemType);
    
    if (szTmpBuf != NULL)
    {
        if (eEmemType == EMEM_MAIN) // Read main block CRC(48KB-4) from bin file
        {
            nRetVal  = szTmpBuf[0xBFFF] << 24;
            nRetVal |= szTmpBuf[0xBFFE] << 16;
            nRetVal |= szTmpBuf[0xBFFD] << 8;
            nRetVal |= szTmpBuf[0xBFFC];
        }
        else if (eEmemType == EMEM_INFO) // Read info block CRC(512Byte-4) from bin file
        {
            nRetVal  = szTmpBuf[0xC1FF] << 24;
            nRetVal |= szTmpBuf[0xC1FE] << 16;
            nRetVal |= szTmpBuf[0xC1FD] << 8;
            nRetVal |= szTmpBuf[0xC1FC];
        }
    }

    return nRetVal;
}

static s32 _DrvFwCtrlMsg22xxUpdateFirmwareBySwId(void) 
{
    s32 nRetVal = -1;
    u32 nCrcInfoA = 0, nCrcInfoB = 0, nCrcMainA = 0, nCrcMainB = 0;
    
    DBG("*** %s() ***\n", __func__);
    
    DBG("_gIsUpdateInfoBlockFirst = %d, g_IsUpdateFirmware = 0x%x\n", _gIsUpdateInfoBlockFirst, g_IsUpdateFirmware);

    _DrvFwCtrlMsg22xxConvertFwDataTwoDimenToOneDimen(g_FwData, _gOneDimenFwData);
    
    if (_gIsUpdateInfoBlockFirst == 1)
    {
        if ((g_IsUpdateFirmware & 0x10) == 0x10)
        {
            _DrvFwCtrlMsg22xxEraseEmem(EMEM_INFO);
            _DrvFwCtrlMsg22xxProgramEmem(EMEM_INFO);
 
            nCrcInfoA = _DrvFwCtrlMsg22xxRetrieveFrimwareCrcFromBinFile(_gOneDimenFwData, EMEM_INFO);
            nCrcInfoB = _DrvFwCtrlMsg22xxGetFirmwareCrcByHardware(EMEM_INFO);

            DBG("nCrcInfoA = 0x%x, nCrcInfoB = 0x%x\n", nCrcInfoA, nCrcInfoB);
        
            if (nCrcInfoA == nCrcInfoB)
            {
                _DrvFwCtrlMsg22xxEraseEmem(EMEM_MAIN);
                _DrvFwCtrlMsg22xxProgramEmem(EMEM_MAIN);

                nCrcMainA = _DrvFwCtrlMsg22xxRetrieveFrimwareCrcFromBinFile(_gOneDimenFwData, EMEM_MAIN);
                nCrcMainB = _DrvFwCtrlMsg22xxGetFirmwareCrcByHardware(EMEM_MAIN);

                DBG("nCrcMainA = 0x%x, nCrcMainB = 0x%x\n", nCrcMainA, nCrcMainB);
        		
                if (nCrcMainA == nCrcMainB)
                {
                    g_IsUpdateFirmware = 0x00;
                    nRetVal = 0;
                }
                else
                {
                    g_IsUpdateFirmware = 0x01;
                }
            }
            else
            {
                g_IsUpdateFirmware = 0x11;
            }
        }
        else if ((g_IsUpdateFirmware & 0x01) == 0x01)
        {
            _DrvFwCtrlMsg22xxEraseEmem(EMEM_MAIN);
            _DrvFwCtrlMsg22xxProgramEmem(EMEM_MAIN);

            nCrcMainA = _DrvFwCtrlMsg22xxRetrieveFrimwareCrcFromBinFile(_gOneDimenFwData, EMEM_MAIN);
            nCrcMainB = _DrvFwCtrlMsg22xxGetFirmwareCrcByHardware(EMEM_MAIN);

            DBG("nCrcMainA=0x%x, nCrcMainB=0x%x\n", nCrcMainA, nCrcMainB);
    		
            if (nCrcMainA == nCrcMainB)
            {
                g_IsUpdateFirmware = 0x00;
                nRetVal = 0;
            }
            else
            {
                g_IsUpdateFirmware = 0x01;
            }
        }
    }
    else //_gIsUpdateInfoBlockFirst == 0
    {
        if ((g_IsUpdateFirmware & 0x10) == 0x10)
        {
            _DrvFwCtrlMsg22xxEraseEmem(EMEM_MAIN);
            _DrvFwCtrlMsg22xxProgramEmem(EMEM_MAIN);

            nCrcMainA = _DrvFwCtrlMsg22xxRetrieveFrimwareCrcFromBinFile(_gOneDimenFwData, EMEM_MAIN);
            nCrcMainB = _DrvFwCtrlMsg22xxGetFirmwareCrcByHardware(EMEM_MAIN);

            DBG("nCrcMainA=0x%x, nCrcMainB=0x%x\n", nCrcMainA, nCrcMainB);

            if (nCrcMainA == nCrcMainB)
            {
                _DrvFwCtrlMsg22xxEraseEmem(EMEM_INFO);
                _DrvFwCtrlMsg22xxProgramEmem(EMEM_INFO);

                nCrcInfoA = _DrvFwCtrlMsg22xxRetrieveFrimwareCrcFromBinFile(_gOneDimenFwData, EMEM_INFO);
                nCrcInfoB = _DrvFwCtrlMsg22xxGetFirmwareCrcByHardware(EMEM_INFO);
                
                DBG("nCrcInfoA=0x%x, nCrcInfoB=0x%x\n", nCrcInfoA, nCrcInfoB);

                if (nCrcInfoA == nCrcInfoB)
                {
                    g_IsUpdateFirmware = 0x00;
                    nRetVal = 0;
                }
                else
                {
                    g_IsUpdateFirmware = 0x01;
                }
            }
            else
            {
                g_IsUpdateFirmware = 0x11;
            }
        }
        else if ((g_IsUpdateFirmware & 0x01) == 0x01)
        {
            _DrvFwCtrlMsg22xxEraseEmem(EMEM_INFO);
            _DrvFwCtrlMsg22xxProgramEmem(EMEM_INFO);

            nCrcInfoA = _DrvFwCtrlMsg22xxRetrieveFrimwareCrcFromBinFile(_gOneDimenFwData, EMEM_INFO);
            nCrcInfoB = _DrvFwCtrlMsg22xxGetFirmwareCrcByHardware(EMEM_INFO);

            DBG("nCrcInfoA=0x%x, nCrcInfoB=0x%x\n", nCrcInfoA, nCrcInfoB);

            if (nCrcInfoA == nCrcInfoB)
            {
                g_IsUpdateFirmware = 0x00;
                nRetVal = 0;
            }
            else
            {
                g_IsUpdateFirmware = 0x01;
            }
        }    		
    }
    
    return nRetVal;	
}

void _DrvFwCtrlMsg22xxCheckFirmwareUpdateBySwId(void)
{
    u32 nCrcMainA, nCrcInfoA, nCrcMainB, nCrcInfoB;
    u32 i;
    u16 nUpdateBinMajor = 0, nUpdateBinMinor = 0;
    u16 nMajor = 0, nMinor = 0;
    u8 *pVersion = NULL;
    Msg22xxSwId_e eSwId = MSG22XX_SW_ID_UNDEFINED;
    
    DBG("*** %s() ***\n", __func__);

    DrvPlatformLyrDisableFingerTouchReport();

    nCrcMainA = _DrvFwCtrlMsg22xxGetFirmwareCrcByHardware(EMEM_MAIN);
    nCrcMainB = _DrvFwCtrlMsg22xxRetrieveFirmwareCrcFromEFlash(EMEM_MAIN);

    nCrcInfoA = _DrvFwCtrlMsg22xxGetFirmwareCrcByHardware(EMEM_INFO);
    nCrcInfoB = _DrvFwCtrlMsg22xxRetrieveFirmwareCrcFromEFlash(EMEM_INFO);
    
    _gUpdateFirmwareBySwIdWorkQueue = create_singlethread_workqueue("update_firmware_by_sw_id");
    INIT_WORK(&_gUpdateFirmwareBySwIdWork, _DrvFwCtrlUpdateFirmwareBySwIdDoWork);

    DBG("nCrcMainA=0x%x, nCrcInfoA=0x%x, nCrcMainB=0x%x, nCrcInfoB=0x%x\n", nCrcMainA, nCrcInfoA, nCrcMainB, nCrcInfoB);
               
    if (nCrcMainA == nCrcMainB && nCrcInfoA == nCrcInfoB) // Case 1. Main Block:OK, Info Block:OK
    {
        eSwId = _DrvFwCtrlMsg22xxGetSwId(EMEM_MAIN);
    		
        if (eSwId == MSG22XX_SW_ID_XXXX)
        {
            nUpdateBinMajor = msg22xx_xxxx_update_bin[0xBFF5]<<8 | msg22xx_xxxx_update_bin[0xBFF4];
            nUpdateBinMinor = msg22xx_xxxx_update_bin[0xBFF7]<<8 | msg22xx_xxxx_update_bin[0xBFF6];
        }
        else if (eSwId == MSG22XX_SW_ID_YYYY)
        {
            //nUpdateBinMajor = msg22xx_yyyy_update_bin[0xBFF5]<<8 | msg22xx_yyyy_update_bin[0xBFF4];
            //nUpdateBinMinor = msg22xx_yyyy_update_bin[0xBFF7]<<8 | msg22xx_yyyy_update_bin[0xBFF6];
        }
        else //eSwId == MSG22XX_SW_ID_UNDEFINED
        {
            DBG("eSwId = 0x%x is an undefined SW ID.\n", eSwId);

            eSwId = MSG22XX_SW_ID_UNDEFINED;
            nUpdateBinMajor = 0;
            nUpdateBinMinor = 0;    		        						
        }
    		
        DrvFwCtrlGetCustomerFirmwareVersion(&nMajor, &nMinor, &pVersion);

        DBG("eSwId=0x%x, nMajor=%d, nMinor=%d, nUpdateBinMajor=%d, nUpdateBinMinor=%d\n", eSwId, nMajor, nMinor, nUpdateBinMajor, nUpdateBinMinor);

        if (nUpdateBinMinor > nMinor)
        {
            if (eSwId == MSG22XX_SW_ID_XXXX)
            {
                for (i = 0; i < (MSG22XX_FIRMWARE_MAIN_BLOCK_SIZE+1); i ++)
                {
                    if (i < MSG22XX_FIRMWARE_MAIN_BLOCK_SIZE) // i < 48
                    {
                        _DrvFwCtrlStoreFirmwareData(&(msg22xx_xxxx_update_bin[i*1024]), 1024);
                    }
                    else // i == 48
                    {
                        _DrvFwCtrlStoreFirmwareData(&(msg22xx_xxxx_update_bin[i*1024]), 512);
                    }
                }
            }
            else if (eSwId == MSG22XX_SW_ID_YYYY)
            {
                for (i = 0; i < (MSG22XX_FIRMWARE_MAIN_BLOCK_SIZE+1); i ++)
                {
                    if (i < MSG22XX_FIRMWARE_MAIN_BLOCK_SIZE) // i < 48
                    {
                        //_DrvFwCtrlStoreFirmwareData(&(msg22xx_yyyy_update_bin[i*1024]), 1024);
                    }
                    else // i == 48
                    {
                        //_DrvFwCtrlStoreFirmwareData(&(msg22xx_yyyy_update_bin[i*1024]), 512);
                    }
                }
            }
            else
            {
                DBG("eSwId = 0x%x is an undefined SW ID.\n", eSwId);

                eSwId = MSG22XX_SW_ID_UNDEFINED;
            }

            if (eSwId < MSG22XX_SW_ID_UNDEFINED && eSwId != 0x0000 && eSwId != 0xFFFF)
            {
                g_FwDataCount = 0; // Reset g_FwDataCount to 0 after copying update firmware data to temp buffer

                _gUpdateRetryCount = UPDATE_FIRMWARE_RETRY_COUNT;
                _gIsUpdateInfoBlockFirst = 1; // Set 1 for indicating main block is complete 
                g_IsUpdateFirmware = 0x11;
                queue_work(_gUpdateFirmwareBySwIdWorkQueue, &_gUpdateFirmwareBySwIdWork);
                return;
            }
            else
            {
                DBG("The sw id is invalid.\n");
                DBG("Go to normal boot up process.\n");
            }
        }
        else
        {
            DBG("The update bin version is older than or equal to the current firmware version on e-flash.\n");
            DBG("Go to normal boot up process.\n");
        }
    }
    else if (nCrcMainA == nCrcMainB && nCrcInfoA != nCrcInfoB) // Case 2. Main Block:OK, Info Block:FAIL
    {
        eSwId = _DrvFwCtrlMsg22xxGetSwId(EMEM_MAIN);
    		
        DBG("eSwId=0x%x\n", eSwId);

        if (eSwId == MSG22XX_SW_ID_XXXX)
        {
            for (i = 0; i < (MSG22XX_FIRMWARE_MAIN_BLOCK_SIZE+1); i ++)
            {
                if (i < MSG22XX_FIRMWARE_MAIN_BLOCK_SIZE) // i < 48
                {
                    _DrvFwCtrlStoreFirmwareData(&(msg22xx_xxxx_update_bin[i*1024]), 1024);
                }
                else // i == 48
                {
                    _DrvFwCtrlStoreFirmwareData(&(msg22xx_xxxx_update_bin[i*1024]), 512);
                }
            }
        }
        else if (eSwId == MSG22XX_SW_ID_YYYY)
        {
            for (i = 0; i < (MSG22XX_FIRMWARE_MAIN_BLOCK_SIZE+1); i ++)
            {
                if (i < MSG22XX_FIRMWARE_MAIN_BLOCK_SIZE) // i < 48
                {
                    //_DrvFwCtrlStoreFirmwareData(&(msg22xx_yyyy_update_bin[i*1024]), 1024);
                }
                else // i == 48
                {
                    //_DrvFwCtrlStoreFirmwareData(&(msg22xx_yyyy_update_bin[i*1024]), 512);
                }
            }
        }
        else
        {
            DBG("eSwId = 0x%x is an undefined SW ID.\n", eSwId);

            eSwId = MSG22XX_SW_ID_UNDEFINED;
        }

        if (eSwId < MSG22XX_SW_ID_UNDEFINED && eSwId != 0x0000 && eSwId != 0xFFFF)
        {
            g_FwDataCount = 0; // Reset g_FwDataCount to 0 after copying update firmware data to temp buffer

            _gUpdateRetryCount = UPDATE_FIRMWARE_RETRY_COUNT;
            _gIsUpdateInfoBlockFirst = 1; // Set 1 for indicating main block is complete 
            g_IsUpdateFirmware = 0x11;
            queue_work(_gUpdateFirmwareBySwIdWorkQueue, &_gUpdateFirmwareBySwIdWork);
            return;
        }
        else
        {
            DBG("The sw id is invalid.\n");
            DBG("Go to normal boot up process.\n");
        }
    }
    else if (nCrcMainA != nCrcMainB && nCrcInfoA == nCrcInfoB) // Case 3. Main Block:FAIL, Info Block:OK
    {
        eSwId = _DrvFwCtrlMsg22xxGetSwId(EMEM_INFO);
		
        DBG("eSwId=0x%x\n", eSwId);

        if (eSwId == MSG22XX_SW_ID_XXXX)
        {
            for (i = 0; i < (MSG22XX_FIRMWARE_MAIN_BLOCK_SIZE+1); i ++)
            {
                if (i < MSG22XX_FIRMWARE_MAIN_BLOCK_SIZE) // i < 48
                {
                    _DrvFwCtrlStoreFirmwareData(&(msg22xx_xxxx_update_bin[i*1024]), 1024);
                }
                else // i == 48
                {
                    _DrvFwCtrlStoreFirmwareData(&(msg22xx_xxxx_update_bin[i*1024]), 512);
                }
            }
        }
        else if (eSwId == MSG22XX_SW_ID_YYYY)
        {
            for (i = 0; i < (MSG22XX_FIRMWARE_MAIN_BLOCK_SIZE+1); i ++)
            {
                if (i < MSG22XX_FIRMWARE_MAIN_BLOCK_SIZE) // i < 48
                {
                    //_DrvFwCtrlStoreFirmwareData(&(msg22xx_yyyy_update_bin[i*1024]), 1024);
                }
                else // i == 48
                {
                    //_DrvFwCtrlStoreFirmwareData(&(msg22xx_yyyy_update_bin[i*1024]), 512);
                }
            }
        }
        else
        {
            DBG("eSwId = 0x%x is an undefined SW ID.\n", eSwId);

            eSwId = MSG22XX_SW_ID_UNDEFINED;
        }

        if (eSwId < MSG22XX_SW_ID_UNDEFINED && eSwId != 0x0000 && eSwId != 0xFFFF)
        {
            g_FwDataCount = 0; // Reset g_FwDataCount to 0 after copying update firmware data to temp buffer

            _gUpdateRetryCount = UPDATE_FIRMWARE_RETRY_COUNT;
            _gIsUpdateInfoBlockFirst = 0; // Set 0 for indicating main block is broken 
            g_IsUpdateFirmware = 0x11;
            queue_work(_gUpdateFirmwareBySwIdWorkQueue, &_gUpdateFirmwareBySwIdWork);
            return;
        }
        else
        {
            DBG("The sw id is invalid.\n");
            DBG("Go to normal boot up process.\n");
        }
    }
    else // Case 4. Main Block:FAIL, Info Block:FAIL
    {
        DBG("Main block and Info block are broken.\n");
        DBG("Go to normal boot up process.\n");
    }

    DrvPlatformLyrTouchDeviceResetHw();

    DrvPlatformLyrEnableFingerTouchReport();
}

//-------------------------End of SW ID for MSG22XX----------------------------//

//-------------------------Start of SW ID for MSG21XXA----------------------------//

static u32 _DrvFwCtrlMsg21xxaCalculateMainCrcFromEFlash(void) 
{
    u32 nRetVal = 0; 
    u16 nRegData = 0;

    DBG("*** %s() ***\n", __func__);

    DbBusEnterSerialDebugMode();
    DbBusStopMCU();
    DbBusIICUseBus();
    DbBusIICReshape();
    mdelay(100);

    // Stop mcu
    RegSetLByteValue(0x0FE6, 0x01); //bank:mheg5, addr:h0073

    // Stop Watchdog
    RegSet16BitValue(0x3C60, 0xAA55); //bank:reg_PIU_MISC_0, addr:h0030

    // cmd
    RegSet16BitValue(0x3CE4, 0xDF4C); //bank:reg_PIU_MISC_0, addr:h0072

    // TP SW reset
    RegSet16BitValue(0x1E04, 0x7d60); //bank:chip, addr:h0002
    RegSet16BitValue(0x1E04, 0x829F);

    // Start mcu
    RegSetLByteValue(0x0FE6, 0x00); //bank:mheg5, addr:h0073
    
    mdelay(100);

    // Polling 0x3CE4 is 0x9432
    do
    {
        nRegData = RegGet16BitValue(0x3CE4); //bank:reg_PIU_MISC_0, addr:h0072
//        DBG("*** reg(0x3C, 0xE4) = 0x%x ***\n", nRegData); // add for debug

    } while (nRegData != 0x9432);

    // Read calculated main block CRC from register
    nRetVal = RegGet16BitValue(0x3C80);
    nRetVal = (nRetVal << 16) | RegGet16BitValue(0x3C82);
        
    DBG("Main Block CRC = 0x%x\n", nRetVal);

    DbBusIICNotUseBus();
    DbBusNotStopMCU();
    DbBusExitSerialDebugMode();

    return nRetVal;	
}

static u32 _DrvFwCtrlMsg21xxaRetrieveMainCrcFromMainBlock(void) 
{
    u32 nRetVal = 0; 
    u16 nRegData = 0;
    u8 szDbBusTxData[5] = {0};
    u8 szDbBusRxData[4] = {0};

    DBG("*** %s() ***\n", __func__);

    DbBusEnterSerialDebugMode();
    DbBusStopMCU();
    DbBusIICUseBus();
    DbBusIICReshape();
    mdelay(100);

    // Stop mcu
    RegSetLByteValue(0x0FE6, 0x01); //bank:mheg5, addr:h0073

    // Stop watchdog
    RegSet16BitValue(0x3C60, 0xAA55); //bank:reg_PIU_MISC_0, addr:h0030

    // cmd
    RegSet16BitValue(0x3CE4, 0xA4AB); //bank:reg_PIU_MISC_0, addr:h0072

    // TP SW reset
    RegSet16BitValue(0x1E04, 0x7d60); //bank:chip, addr:h0002
    RegSet16BitValue(0x1E04, 0x829F);

    // Start mcu
    RegSetLByteValue(0x0FE6, 0x00); //bank:mheg5, addr:h0073
    
    mdelay(100);

    // Polling 0x3CE4 is 0x5B58
    do
    {
        nRegData = RegGet16BitValue(0x3CE4); //bank:reg_PIU_MISC_0, addr:h0072
    } while (nRegData != 0x5B58);

     // Read main block CRC from main block
    szDbBusTxData[0] = 0x72;
    szDbBusTxData[1] = 0x7F;
    szDbBusTxData[2] = 0xFC;
    szDbBusTxData[3] = 0x00;
    szDbBusTxData[4] = 0x04;
    
    IicWriteData(SLAVE_I2C_ID_DWI2C, &szDbBusTxData[0], 5);
    IicReadData(SLAVE_I2C_ID_DWI2C, &szDbBusRxData[0], 4);

    nRetVal = szDbBusRxData[0];
    nRetVal = (nRetVal << 8) | szDbBusRxData[1];
    nRetVal = (nRetVal << 8) | szDbBusRxData[2];
    nRetVal = (nRetVal << 8) | szDbBusRxData[3];
   
    DBG("CRC = 0x%x\n", nRetVal);

    DbBusIICNotUseBus();
    DbBusNotStopMCU();
    DbBusExitSerialDebugMode();

    return nRetVal;	
}

static s32 _DrvFwCtrlMsg21xxaUpdateFirmwareBySwId(u8 szFwData[][1024], EmemType_e eEmemType) 
{
    u32 i, j, nCalculateCrcSize;
    u32 nCrcMain = 0, nCrcMainTp = 0;
    u32 nCrcInfo = 0, nCrcInfoTp = 0;
    u32 nCrcTemp = 0;
    u16 nRegData = 0;

    DBG("*** %s() ***\n", __func__);

    nCrcMain = 0xffffffff;
    nCrcInfo = 0xffffffff;

#ifdef CONFIG_TOUCH_DRIVER_RUN_ON_MTK_PLATFORM
#ifdef CONFIG_ENABLE_DMA_IIC
    DmaReset();
#endif //CONFIG_ENABLE_DMA_IIC
#endif //CONFIG_TOUCH_DRIVER_RUN_ON_MTK_PLATFORM

    DrvPlatformLyrTouchDeviceResetHw();

    DbBusEnterSerialDebugMode();
    DbBusStopMCU();
    DbBusIICUseBus();
    DbBusIICReshape();
    mdelay(100);

    // erase main
    _DrvFwCtrlEraseEmemC33(EMEM_MAIN);
    mdelay(1000);

    DrvPlatformLyrTouchDeviceResetHw();

    DbBusEnterSerialDebugMode();
    DbBusStopMCU();
    DbBusIICUseBus();
    DbBusIICReshape();
    mdelay(100);

    /////////////////////////
    // Program
    /////////////////////////

    // Polling 0x3CE4 is 0x1C70
    if ((eEmemType == EMEM_ALL) || (eEmemType == EMEM_MAIN))
    {
        do
        {
            nRegData = RegGet16BitValue(0x3CE4);
        } while (nRegData != 0x1C70);
    }

    switch (eEmemType)
    {
        case EMEM_ALL:
            RegSet16BitValue(0x3CE4, 0xE38F);  // for all blocks
            break;
        case EMEM_MAIN:
            RegSet16BitValue(0x3CE4, 0x7731);  // for main block
            break;
        case EMEM_INFO:
            RegSet16BitValue(0x3CE4, 0x7731);  // for info block

            RegSetLByteValue(0x0FE6, 0x01);

            RegSetLByteValue(0x3CE4, 0xC5); 
            RegSetLByteValue(0x3CE5, 0x78); 

            RegSetLByteValue(0x1E04, 0x9F);
            RegSetLByteValue(0x1E05, 0x82);

            RegSetLByteValue(0x0FE6, 0x00);
            mdelay(100);
            break;
    }

    // Polling 0x3CE4 is 0x2F43
    do
    {
        nRegData = RegGet16BitValue(0x3CE4);
    } while (nRegData != 0x2F43);

    // Calculate CRC 32
    DrvCommonCrcInitTable();

    if (eEmemType == EMEM_ALL)
    {
        nCalculateCrcSize = MSG21XXA_FIRMWARE_WHOLE_SIZE;
    }
    else if (eEmemType == EMEM_MAIN)
    {
        nCalculateCrcSize = MSG21XXA_FIRMWARE_MAIN_BLOCK_SIZE;
    }
    else if (eEmemType == EMEM_INFO)
    {
        nCalculateCrcSize = MSG21XXA_FIRMWARE_INFO_BLOCK_SIZE;
    }
    else
    {
        nCalculateCrcSize = 0;
    }
		
    for (i = 0; i < nCalculateCrcSize; i ++)
    {
        if (eEmemType == EMEM_INFO)
        {
            i = 32;
        }

        if (i < 32)   // emem_main
        {
            if (i == 31)
            {
                szFwData[i][1014] = 0x5A;
                szFwData[i][1015] = 0xA5;

                for (j = 0; j < 1016; j ++)
                {
                    nCrcMain = DrvCommonCrcGetValue(szFwData[i][j], nCrcMain);
                }

                nCrcTemp = nCrcMain;
                nCrcTemp = nCrcTemp ^ 0xffffffff;

                DBG("nCrcTemp=%x\n", nCrcTemp); // add for debug

                for (j = 0; j < 4; j ++)
                {
                    szFwData[i][1023-j] = ((nCrcTemp>>(8*j)) & 0xFF);

                    DBG("((nCrcTemp>>(8*%d)) & 0xFF)=%x\n", j, ((nCrcTemp>>(8*j)) & 0xFF)); // add for debug
                    DBG("Update main clock crc32 into bin buffer szFwData[%d][%d]=%x\n", i, (1020+j), szFwData[i][1020+j]);
                }
            }
            else
            {
                for (j = 0; j < 1024; j ++)
                {
                    nCrcMain = DrvCommonCrcGetValue(szFwData[i][j], nCrcMain);
                }
            }
        }
        else  // emem_info
        {
            for (j = 0; j < 1024; j ++)
            {
                nCrcInfo = DrvCommonCrcGetValue(szFwData[i][j], nCrcInfo);
            }
            
            if (eEmemType == EMEM_MAIN)
            {
                break;
            }
        }

#if defined(CONFIG_TOUCH_DRIVER_RUN_ON_QCOM_PLATFORM) || defined(CONFIG_TOUCH_DRIVER_RUN_ON_MTK_PLATFORM)
        for (j = 0; j < 8; j ++)
        {
            IicWriteData(SLAVE_I2C_ID_DWI2C, &szFwData[i][j*128], 128);
        }
#elif defined(CONFIG_TOUCH_DRIVER_RUN_ON_SPRD_PLATFORM)
        IicWriteData(SLAVE_I2C_ID_DWI2C, szFwData[i], 1024);
#endif

        // Polling 0x3CE4 is 0xD0BC
        do
        {
            nRegData = RegGet16BitValue(0x3CE4);
        } while (nRegData != 0xD0BC);

        RegSet16BitValue(0x3CE4, 0x2F43);
    }

    if ((eEmemType == EMEM_ALL) || (eEmemType == EMEM_MAIN))
    {
        // write file done and check crc
        RegSet16BitValue(0x3CE4, 0x1380);
    }
    mdelay(10);

    if ((eEmemType == EMEM_ALL) || (eEmemType == EMEM_MAIN))
    {
        // Polling 0x3CE4 is 0x9432
        do
        {
            nRegData = RegGet16BitValue(0x3CE4);
        } while (nRegData != 0x9432);
    }

    nCrcMain = nCrcMain ^ 0xffffffff;
    nCrcInfo = nCrcInfo ^ 0xffffffff;

    if ((eEmemType == EMEM_ALL) || (eEmemType == EMEM_MAIN))
    {
        // CRC Main from TP
        nCrcMainTp = RegGet16BitValue(0x3C80);
        nCrcMainTp = (nCrcMainTp << 16) | RegGet16BitValue(0x3C82);
    }

    if (eEmemType == EMEM_ALL)
    {
        // CRC Info from TP
        nCrcInfoTp = RegGet16BitValue(0x3CA0);
        nCrcInfoTp = (nCrcInfoTp << 16) | RegGet16BitValue(0x3CA2);
    }

    DBG("nCrcMain=0x%x, nCrcInfo=0x%x, nCrcMainTp=0x%x, nCrcInfoTp=0x%x\n", nCrcMain, nCrcInfo, nCrcMainTp, nCrcInfoTp);

    g_FwDataCount = 0; // Reset g_FwDataCount to 0 after update firmware

    DbBusIICNotUseBus();
    DbBusNotStopMCU();
    DbBusExitSerialDebugMode();

    DrvPlatformLyrTouchDeviceResetHw();
    
    if ((eEmemType == EMEM_ALL) || (eEmemType == EMEM_MAIN))
    {
        if (nCrcMainTp != nCrcMain)
        {
            DBG("Update FAILED\n");

            return -1;
        }
    }

    if (eEmemType == EMEM_ALL)
    {
        if (nCrcInfoTp != nCrcInfo)
        {
            DBG("Update FAILED\n");

            return -1;
        }
    }

    DBG("Update SUCCESS\n");

    return 0;
} 

void _DrvFwCtrlMsg21xxaCheckFirmwareUpdateBySwId(void) 
{
    u32 nCrcMainA, nCrcMainB;
    u32 i;
    u16 nUpdateBinMajor = 0, nUpdateBinMinor = 0;
    u16 nMajor = 0, nMinor = 0;
    u8 nIsCompareVersion = 0;
    u8 *pVersion = NULL; 
    Msg21xxaSwId_e eMainSwId = MSG21XXA_SW_ID_UNDEFINED, eInfoSwId = MSG21XXA_SW_ID_UNDEFINED, eSwId = MSG21XXA_SW_ID_UNDEFINED;

    DBG("*** %s() ***\n", __func__);

    DrvPlatformLyrDisableFingerTouchReport();

    nCrcMainA = _DrvFwCtrlMsg21xxaCalculateMainCrcFromEFlash();
    nCrcMainB = _DrvFwCtrlMsg21xxaRetrieveMainCrcFromMainBlock();

    _gUpdateFirmwareBySwIdWorkQueue = create_singlethread_workqueue("update_firmware_by_sw_id");
    INIT_WORK(&_gUpdateFirmwareBySwIdWork, _DrvFwCtrlUpdateFirmwareBySwIdDoWork);

    DBG("nCrcMainA=0x%x, nCrcMainB=0x%x\n", nCrcMainA, nCrcMainB);
               
    if (nCrcMainA == nCrcMainB) 
    {
        eMainSwId = _DrvFwCtrlMsg21xxaGetSwId(EMEM_MAIN);
        eInfoSwId = _DrvFwCtrlMsg21xxaGetSwId(EMEM_INFO);
    		
        DBG("Check firmware integrity success\n");
        DBG("eMainSwId=0x%x, eInfoSwId=0x%x\n", eMainSwId, eInfoSwId);

        if (eMainSwId == eInfoSwId)
        {
        		eSwId = eMainSwId;
        		nIsCompareVersion = 1;
        }
        else
        {
        		eSwId = eInfoSwId;
        		nIsCompareVersion = 0;
        }
        
        if (eSwId == MSG21XXA_SW_ID_XXXX)
        {
#ifdef CONFIG_UPDATE_FIRMWARE_BY_TWO_DIMENSIONAL_ARRAY // By two dimensional array
            //nUpdateBinMajor = msg21xxa_xxxx_update_bin[31][0x34F]<<8 | msg21xxa_xxxx_update_bin[31][0x34E];
            //nUpdateBinMinor = msg21xxa_xxxx_update_bin[31][0x351]<<8 | msg21xxa_xxxx_update_bin[31][0x350];
#else // By one dimensional array
            //nUpdateBinMajor = msg21xxa_xxxx_update_bin[0x7F4F]<<8 | msg21xxa_xxxx_update_bin[0x7F4E];
            //nUpdateBinMinor = msg21xxa_xxxx_update_bin[0x7F51]<<8 | msg21xxa_xxxx_update_bin[0x7F50];
#endif
        }
        else if (eSwId == MSG21XXA_SW_ID_YYYY)
        {
#ifdef CONFIG_UPDATE_FIRMWARE_BY_TWO_DIMENSIONAL_ARRAY // By two dimensional array
            //nUpdateBinMajor = msg21xxa_yyyy_update_bin[31][0x34F]<<8 | msg21xxa_yyyy_update_bin[31][0x34E];
            //nUpdateBinMinor = msg21xxa_yyyy_update_bin[31][0x351]<<8 | msg21xxa_yyyy_update_bin[31][0x350];
#else // By one dimensional array
            //nUpdateBinMajor = msg21xxa_yyyy_update_bin[0x7F4F]<<8 | msg21xxa_yyyy_update_bin[0x7F4E];
            //nUpdateBinMinor = msg21xxa_yyyy_update_bin[0x7F51]<<8 | msg21xxa_yyyy_update_bin[0x7F50];
#endif
        }
        else //eSwId == MSG21XXA_SW_ID_UNDEFINED
        {
            DBG("eSwId = 0x%x is an undefined SW ID.\n", eSwId);

            eSwId = MSG21XXA_SW_ID_UNDEFINED;
            nUpdateBinMajor = 0;
            nUpdateBinMinor = 0;    		        						
        }

        DrvFwCtrlGetCustomerFirmwareVersion(&nMajor, &nMinor, &pVersion);
    		        
        DBG("eSwId=0x%x, nMajor=%d, nMinor=%d, nUpdateBinMajor=%d, nUpdateBinMinor=%d\n", eSwId, nMajor, nMinor, nUpdateBinMajor, nUpdateBinMinor);

        if ((nUpdateBinMinor > nMinor && nIsCompareVersion == 1) || (nIsCompareVersion == 0))
        {
            if (eSwId == MSG21XXA_SW_ID_XXXX)
            {
                for (i = 0; i < MSG21XXA_FIRMWARE_MAIN_BLOCK_SIZE; i ++)
                {
#ifdef CONFIG_UPDATE_FIRMWARE_BY_TWO_DIMENSIONAL_ARRAY // By two dimensional array
                    //_DrvFwCtrlStoreFirmwareData(msg21xxa_xxxx_update_bin[i], 1024);
#else // By one dimensional array
                    //_DrvFwCtrlStoreFirmwareData(&(msg21xxa_xxxx_update_bin[i*1024]), 1024);
#endif
                }
            }
            else if (eSwId == MSG21XXA_SW_ID_YYYY)
            {
                for (i = 0; i < MSG21XXA_FIRMWARE_MAIN_BLOCK_SIZE; i ++)
                {
#ifdef CONFIG_UPDATE_FIRMWARE_BY_TWO_DIMENSIONAL_ARRAY // By two dimensional array
                    //_DrvFwCtrlStoreFirmwareData(msg21xxa_yyyy_update_bin[i], 1024);
#else // By one dimensional array
                    //_DrvFwCtrlStoreFirmwareData(&(msg21xxa_yyyy_update_bin[i*1024]), 1024);
#endif
                }
            }
            else
            {
                DBG("eSwId = 0x%x is an undefined SW ID.\n", eSwId);

                eSwId = MSG21XXA_SW_ID_UNDEFINED;
            }

            if (eSwId < MSG21XXA_SW_ID_UNDEFINED && eSwId != 0xFFFF)
            {
                g_FwDataCount = 0; // Reset g_FwDataCount to 0 after copying update firmware data to temp buffer

                _gUpdateRetryCount = UPDATE_FIRMWARE_RETRY_COUNT;
                queue_work(_gUpdateFirmwareBySwIdWorkQueue, &_gUpdateFirmwareBySwIdWork);
                return;
            }
            else
            {
                DBG("The sw id is invalid.\n");
                DBG("Go to normal boot up process.\n");
            }
        }
        else
        {
            DBG("The update bin version is older than or equal to the current firmware version on e-flash.\n");
            DBG("Go to normal boot up process.\n");
        }
    }
    else
    {
        eSwId = _DrvFwCtrlMsg21xxaGetSwId(EMEM_INFO);
    		
        DBG("Check firmware integrity failed\n");
        DBG("eSwId=0x%x\n", eSwId);

        if (eSwId == MSG21XXA_SW_ID_XXXX)
        {
            for (i = 0; i < MSG21XXA_FIRMWARE_MAIN_BLOCK_SIZE; i ++)
            {
#ifdef CONFIG_UPDATE_FIRMWARE_BY_TWO_DIMENSIONAL_ARRAY // By two dimensional array
                //_DrvFwCtrlStoreFirmwareData(msg21xxa_xxxx_update_bin[i], 1024);
#else // By one dimensional array
                //_DrvFwCtrlStoreFirmwareData(&(msg21xxa_xxxx_update_bin[i*1024]), 1024);
#endif
            }
        }
        else if (eSwId == MSG21XXA_SW_ID_YYYY)
        {
            for (i = 0; i < MSG21XXA_FIRMWARE_MAIN_BLOCK_SIZE; i ++)
            {
#ifdef CONFIG_UPDATE_FIRMWARE_BY_TWO_DIMENSIONAL_ARRAY // By two dimensional array
                //_DrvFwCtrlStoreFirmwareData(msg21xxa_yyyy_update_bin[i], 1024);
#else // By one dimensional array
                //_DrvFwCtrlStoreFirmwareData(&(msg21xxa_yyyy_update_bin[i*1024]), 1024);
#endif
            }
        }
        else
        {
            DBG("eSwId = 0x%x is an undefined SW ID.\n", eSwId);

            eSwId = MSG21XXA_SW_ID_UNDEFINED;
        }

        if (eSwId < MSG21XXA_SW_ID_UNDEFINED && eSwId != 0xFFFF)
        {
            g_FwDataCount = 0; // Reset g_FwDataCount to 0 after copying update firmware data to temp buffer

            _gUpdateRetryCount = UPDATE_FIRMWARE_RETRY_COUNT;
            queue_work(_gUpdateFirmwareBySwIdWorkQueue, &_gUpdateFirmwareBySwIdWork);
            return;
        }
        else
        {
            DBG("The sw id is invalid.\n");
            DBG("Go to normal boot up process.\n");
        }
    }

    DrvPlatformLyrTouchDeviceResetHw();
    
    DrvPlatformLyrEnableFingerTouchReport();
}

//-------------------------End of SW ID for MSG21XXA----------------------------//

static void _DrvFwCtrlUpdateFirmwareBySwIdDoWork(struct work_struct *pWork)
{
    s32 nRetVal = 0;
    
    DBG("*** %s() _gUpdateRetryCount = %d ***\n", __func__, _gUpdateRetryCount);

    if (g_ChipType == CHIP_TYPE_MSG21XXA)   
    {
        nRetVal = _DrvFwCtrlMsg21xxaUpdateFirmwareBySwId(g_FwData, EMEM_MAIN);
    }
    else if (g_ChipType == CHIP_TYPE_MSG22XX)    
    {
        _DrvFwCtrlMsg22xxGetTpVendorCode(_gTpVendorCode);
        
        if (_gTpVendorCode[0] == 'C' && _gTpVendorCode[1] == 'N' && _gTpVendorCode[2] == 'T') // for specific TP vendor which store some important information in info block, only update firmware for main block, do not update firmware for info block.
        {
            nRetVal = _DrvFwCtrlMsg22xxUpdateFirmware(g_FwData, EMEM_MAIN);
        }
        else
        {
            nRetVal = _DrvFwCtrlMsg22xxUpdateFirmwareBySwId();
        }
    }
    else
    {
        DBG("This chip type (%d) does not support update firmware by sw id\n", g_ChipType);

        DrvPlatformLyrTouchDeviceResetHw(); 

        DrvPlatformLyrEnableFingerTouchReport();

        nRetVal = -1;
        return;
    }
    
    DBG("*** update firmware by sw id result = %d ***\n", nRetVal);
    
    if (nRetVal == 0)
    {
        DBG("update firmware by sw id success\n");

        DrvPlatformLyrTouchDeviceResetHw();

        DrvPlatformLyrEnableFingerTouchReport();

        if (g_ChipType == CHIP_TYPE_MSG22XX)    
        {
            _gIsUpdateInfoBlockFirst = 0;
            g_IsUpdateFirmware = 0x00;
        }
    }
    else //nRetVal == -1
    {
        _gUpdateRetryCount --;
        if (_gUpdateRetryCount > 0)
        {
            DBG("_gUpdateRetryCount = %d\n", _gUpdateRetryCount);
            queue_work(_gUpdateFirmwareBySwIdWorkQueue, &_gUpdateFirmwareBySwIdWork);
        }
        else
        {
            DBG("update firmware by sw id failed\n");

            DrvPlatformLyrTouchDeviceResetHw();

            DrvPlatformLyrEnableFingerTouchReport();

            if (g_ChipType == CHIP_TYPE_MSG22XX)    
            {
                _gIsUpdateInfoBlockFirst = 0;
                g_IsUpdateFirmware = 0x00;
            }

#ifdef CONFIG_ONTIM_DSM	
		{
			int error=OMTIM_DSM_TP_FW_UPGRAED_ERROR;
		 	if ( (msg2238a_dsm_client ) && dsm_client_ocuppy(msg2238a_dsm_client))
		 	{
		 		if ((msg2238a_dsm_client->dump_buff) && (msg2238a_dsm_client->buff_size)&&(msg2238a_dsm_client->buff_flag == OMTIM_DSM_BUFF_OK))
		 		{
					msg2238a_dsm_client->used_size = sprintf(msg2238a_dsm_client->dump_buff,"Type=%d; ID=%d; error_id=%d; CTP info:%s; FW upgread error = %d\n",msg2238a_dsm_client->client_type,msg2238a_dsm_client->client_id,error,msg2238a_vendor_name,nRetVal );
					dsm_client_notify(msg2238a_dsm_client,error);
		 		}
		 	}
			else
			{
				printk(KERN_ERR "%s: dsm ocuppy error!!!",__func__);
			}
		}
#endif
			
        }
    }
}

#endif //CONFIG_UPDATE_FIRMWARE_BY_SW_ID

//------------------------------------------------------------------------------//
#if 0
static void _DrvFwCtrlReadInfoC33(void)
{
    u8 szDbBusTxData[5] = {0};
    u16 nRegData = 0;
#if defined(CONFIG_TOUCH_DRIVER_RUN_ON_QCOM_PLATFORM) || defined(CONFIG_TOUCH_DRIVER_RUN_ON_MTK_PLATFORM)
    u32 i;
#endif 

    DBG("*** %s() ***\n", __func__);
    
    mdelay(300);

    // Stop Watchdog
    RegSetLByteValue(0x3C60, 0x55);
    RegSetLByteValue(0x3C61, 0xAA);

    RegSet16BitValue(0x3CE4, 0xA4AB);

    RegSet16BitValue(0x1E04, 0x7d60);

    // TP SW reset
    RegSet16BitValue(0x1E04, 0x829F);
    mdelay(1);
    
    szDbBusTxData[0] = 0x10;
    szDbBusTxData[1] = 0x0F;
    szDbBusTxData[2] = 0xE6;
    szDbBusTxData[3] = 0x00;
    IicWriteData(SLAVE_I2C_ID_DBBUS, szDbBusTxData, 4);    
    mdelay(100);

    do
    {
        nRegData = RegGet16BitValue(0x3CE4);
    } while (nRegData != 0x5B58);

#if defined(CONFIG_TOUCH_DRIVER_RUN_ON_QCOM_PLATFORM) || defined(CONFIG_TOUCH_DRIVER_RUN_ON_MTK_PLATFORM)
    szDbBusTxData[0] = 0x72;
    szDbBusTxData[3] = 0x00;
    szDbBusTxData[4] = 0x80; // read 128 bytes

    for (i = 0; i < 8; i ++)
    {
        szDbBusTxData[1] = 0x80 + (((i*128)&0xff00)>>8);
        szDbBusTxData[2] = (i*128)&0x00ff;

        IicWriteData(SLAVE_I2C_ID_DWI2C, &szDbBusTxData[0], 5);

        mdelay(50);

        // Receive info data
        IicReadData(SLAVE_I2C_ID_DWI2C, &_gDwIicInfoData[i*128], 128);
    }
#elif defined(CONFIG_TOUCH_DRIVER_RUN_ON_SPRD_PLATFORM)
    szDbBusTxData[0] = 0x72;
    szDbBusTxData[1] = 0x80;
    szDbBusTxData[2] = 0x00;
    szDbBusTxData[3] = 0x04; // read 1024 bytes
    szDbBusTxData[4] = 0x00;
    
    IicWriteData(SLAVE_I2C_ID_DWI2C, szDbBusTxData, 5);

    mdelay(50);

    // Receive info data
    IicReadData(SLAVE_I2C_ID_DWI2C, &_gDwIicInfoData[0], 1024);
#endif
}
#endif
#if 0
static s32 _DrvFwCtrlUpdateFirmwareC32(u8 szFwData[][1024], EmemType_e eEmemType)
{
    u32 i, j;
    u32 nCrcMain, nCrcMainTp;
    u32 nCrcInfo, nCrcInfoTp;
    u32 nCrcTemp;
    u16 nRegData = 0;

    DBG("*** %s() ***\n", __func__);

    nCrcMain = 0xffffffff;
    nCrcInfo = 0xffffffff;

#ifdef CONFIG_TOUCH_DRIVER_RUN_ON_MTK_PLATFORM
#ifdef CONFIG_ENABLE_DMA_IIC
    DmaReset();
#endif //CONFIG_ENABLE_DMA_IIC
#endif //CONFIG_TOUCH_DRIVER_RUN_ON_MTK_PLATFORM

    /////////////////////////
    // Erase  all
    /////////////////////////
    _DrvFwCtrlEraseEmemC32();
    mdelay(1000); 

    DrvPlatformLyrTouchDeviceResetHw();

    DbBusEnterSerialDebugMode();
    DbBusStopMCU();
    DbBusIICUseBus();
    DbBusIICReshape();
    mdelay(100);

    // Reset watch dog
    RegSetLByteValue(0x3C60, 0x55);
    RegSetLByteValue(0x3C61, 0xAA);

    /////////////////////////
    // Program
    /////////////////////////

    // Polling 0x3CE4 is 0x1C70
    do
    {
        nRegData = RegGet16BitValue(0x3CE4);
    } while (nRegData != 0x1C70);

    RegSet16BitValue(0x3CE4, 0xE38F);  // for all-blocks

    // Polling 0x3CE4 is 0x2F43
    do
    {
        nRegData = RegGet16BitValue(0x3CE4);
    } while (nRegData != 0x2F43);

    // Calculate CRC 32
    DrvCommonCrcInitTable();

    for (i = 0; i < 33; i ++) // total  33 KB : 2 byte per R/W
    {
        if (i < 32)   // emem_main
        {
            if (i == 31)
            {
                szFwData[i][1014] = 0x5A;
                szFwData[i][1015] = 0xA5;

                for (j = 0; j < 1016; j ++)
                {
                    nCrcMain = DrvCommonCrcGetValue(szFwData[i][j], nCrcMain);
                }

                nCrcTemp = nCrcMain;
                nCrcTemp = nCrcTemp ^ 0xffffffff;

                DBG("nCrcTemp=%x\n", nCrcTemp); // add for debug

                for (j = 0; j < 4; j ++)
                {
                    szFwData[i][1023-j] = ((nCrcTemp>>(8*j)) & 0xFF);

                    DBG("((nCrcTemp>>(8*%d)) & 0xFF)=%x\n", j, ((nCrcTemp>>(8*j)) & 0xFF)); // add for debug
                    DBG("Update main clock crc32 into bin buffer szFwData[%d][%d]=%x\n", i, (1020+j), szFwData[i][1020+j]);
                }
            }
            else
            {
                for (j = 0; j < 1024; j ++)
                {
                    nCrcMain = DrvCommonCrcGetValue(szFwData[i][j], nCrcMain);
                }
            }
        }
        else  // emem_info
        {
            for (j = 0; j < 1024; j ++)
            {
                nCrcInfo = DrvCommonCrcGetValue(szFwData[i][j], nCrcInfo);
            }
        }

#if defined(CONFIG_TOUCH_DRIVER_RUN_ON_QCOM_PLATFORM) || defined(CONFIG_TOUCH_DRIVER_RUN_ON_MTK_PLATFORM)
        for (j = 0; j < 8; j ++)
        {
            IicWriteData(SLAVE_I2C_ID_DWI2C, &szFwData[i][j*128], 128);
        }
#elif defined(CONFIG_TOUCH_DRIVER_RUN_ON_SPRD_PLATFORM)
        IicWriteData(SLAVE_I2C_ID_DWI2C, szFwData[i], 1024);
#endif

        // Polling 0x3CE4 is 0xD0BC
        do
        {
            nRegData = RegGet16BitValue(0x3CE4);
        } while (nRegData != 0xD0BC);

        RegSet16BitValue(0x3CE4, 0x2F43);
    }

    // Write file done
    RegSet16BitValue(0x3CE4, 0x1380);

    mdelay(10); 
    // Polling 0x3CE4 is 0x9432
    do
    {
        nRegData = RegGet16BitValue(0x3CE4);
    } while (nRegData != 0x9432);

    nCrcMain = nCrcMain ^ 0xffffffff;
    nCrcInfo = nCrcInfo ^ 0xffffffff;

    // CRC Main from TP
    nCrcMainTp = RegGet16BitValue(0x3C80);
    nCrcMainTp = (nCrcMainTp << 16) | RegGet16BitValue(0x3C82);
 
    // CRC Info from TP
    nCrcInfoTp = RegGet16BitValue(0x3CA0);
    nCrcInfoTp = (nCrcInfoTp << 16) | RegGet16BitValue(0x3CA2);

    DBG("nCrcMain=0x%x, nCrcInfo=0x%x, nCrcMainTp=0x%x, nCrcInfoTp=0x%x\n",
               nCrcMain, nCrcInfo, nCrcMainTp, nCrcInfoTp);

    g_FwDataCount = 0; // Reset g_FwDataCount to 0 after update firmware

    DbBusIICNotUseBus();
    DbBusNotStopMCU();
    DbBusExitSerialDebugMode();

    DrvPlatformLyrTouchDeviceResetHw();


    if ((nCrcMainTp != nCrcMain) || (nCrcInfoTp != nCrcInfo))
    {
        DBG("Update FAILED\n");

        return -1;
    }

    DBG("Update SUCCESS\n");

    return 0;
}
#endif
#if 0
static s32 _DrvFwCtrlUpdateFirmwareC33(u8 szFwData[][1024], EmemType_e eEmemType)
{
    u8 szLifeCounter[2];
    u32 i, j;
    u32 nCrcMain, nCrcMainTp;
    u32 nCrcInfo, nCrcInfoTp;
    u32 nCrcTemp;
    u16 nRegData = 0;

    DBG("*** %s() ***\n", __func__);

    nCrcMain = 0xffffffff;
    nCrcInfo = 0xffffffff;

#ifdef CONFIG_TOUCH_DRIVER_RUN_ON_MTK_PLATFORM
#ifdef CONFIG_ENABLE_DMA_IIC
    DmaReset();
#endif //CONFIG_ENABLE_DMA_IIC
#endif //CONFIG_TOUCH_DRIVER_RUN_ON_MTK_PLATFORM

    _DrvFwCtrlReadInfoC33();

    if (_gDwIicInfoData[0] == 'M' && _gDwIicInfoData[1] == 'S' && _gDwIicInfoData[2] == 'T' && _gDwIicInfoData[3] == 'A' && _gDwIicInfoData[4] == 'R' && _gDwIicInfoData[5] == 'T' && _gDwIicInfoData[6] == 'P' && _gDwIicInfoData[7] == 'C')
    {
        _gDwIicInfoData[8] = szFwData[32][8];
        _gDwIicInfoData[9] = szFwData[32][9];
        _gDwIicInfoData[10] = szFwData[32][10];
        _gDwIicInfoData[11] = szFwData[32][11];
        // updata life counter
        szLifeCounter[1] = ((((_gDwIicInfoData[13] << 8) | _gDwIicInfoData[12]) + 1) >> 8) & 0xFF;
        szLifeCounter[0] = (((_gDwIicInfoData[13] << 8) | _gDwIicInfoData[12]) + 1) & 0xFF;
        _gDwIicInfoData[12] = szLifeCounter[0];
        _gDwIicInfoData[13] = szLifeCounter[1];
        
        RegSet16BitValue(0x3CE4, 0x78C5);
        RegSet16BitValue(0x1E04, 0x7d60);
        // TP SW reset
        RegSet16BitValue(0x1E04, 0x829F);

        mdelay(50);

        // Polling 0x3CE4 is 0x2F43
        do
        {
            nRegData = RegGet16BitValue(0x3CE4);
        } while (nRegData != 0x2F43);

        // Transmit lk info data
#if defined(CONFIG_TOUCH_DRIVER_RUN_ON_QCOM_PLATFORM) || defined(CONFIG_TOUCH_DRIVER_RUN_ON_MTK_PLATFORM)
        for (j = 0; j < 8; j ++)
        {
            IicWriteData(SLAVE_I2C_ID_DWI2C, &_gDwIicInfoData[j*128], 128);
        }
#elif defined(CONFIG_TOUCH_DRIVER_RUN_ON_SPRD_PLATFORM)
        IicWriteData(SLAVE_I2C_ID_DWI2C, &_gDwIicInfoData[0], 1024);
#endif

        // Polling 0x3CE4 is 0xD0BC
        do
        {
            nRegData = RegGet16BitValue(0x3CE4);
        } while (nRegData != 0xD0BC);
    }

    // erase main
    _DrvFwCtrlEraseEmemC33(EMEM_MAIN);
    mdelay(1000);

    DrvPlatformLyrTouchDeviceResetHw();

    DbBusEnterSerialDebugMode();
    DbBusStopMCU();
    DbBusIICUseBus();
    DbBusIICReshape();
    mdelay(100);

    /////////////////////////
    // Program
    /////////////////////////

    // Polling 0x3CE4 is 0x1C70
    if ((eEmemType == EMEM_ALL) || (eEmemType == EMEM_MAIN))
    {
        do
        {
            nRegData = RegGet16BitValue(0x3CE4);
        } while (nRegData != 0x1C70);
    }

    switch (eEmemType)
    {
        case EMEM_ALL:
            RegSet16BitValue(0x3CE4, 0xE38F);  // for all blocks
            break;
        case EMEM_MAIN:
            RegSet16BitValue(0x3CE4, 0x7731);  // for main block
            break;
        case EMEM_INFO:
            RegSet16BitValue(0x3CE4, 0x7731);  // for info block

            RegSetLByteValue(0x0FE6, 0x01);

            RegSetLByteValue(0x3CE4, 0xC5); 
            RegSetLByteValue(0x3CE5, 0x78); 

            RegSetLByteValue(0x1E04, 0x9F);
            RegSetLByteValue(0x1E05, 0x82);

            RegSetLByteValue(0x0FE6, 0x00);
            mdelay(100);
            break;
    }

    // Polling 0x3CE4 is 0x2F43
    do
    {
        nRegData = RegGet16BitValue(0x3CE4);
    } while (nRegData != 0x2F43);

    // Calculate CRC 32
    DrvCommonCrcInitTable();

    for (i = 0; i < 33; i ++) // total 33 KB : 2 byte per R/W
    {
        if (eEmemType == EMEM_INFO)
        {
            i = 32;
        }

        if (i < 32)   // emem_main
        {
            if (i == 31)
            {
                szFwData[i][1014] = 0x5A;
                szFwData[i][1015] = 0xA5;

                for (j = 0; j < 1016; j ++)
                {
                    nCrcMain = DrvCommonCrcGetValue(szFwData[i][j], nCrcMain);
                }

                nCrcTemp = nCrcMain;
                nCrcTemp = nCrcTemp ^ 0xffffffff;

                DBG("nCrcTemp=%x\n", nCrcTemp); // add for debug

                for (j = 0; j < 4; j ++)
                {
                    szFwData[i][1023-j] = ((nCrcTemp>>(8*j)) & 0xFF);

                    DBG("((nCrcTemp>>(8*%d)) & 0xFF)=%x\n", j, ((nCrcTemp>>(8*j)) & 0xFF)); // add for debug
                    DBG("Update main clock crc32 into bin buffer szFwData[%d][%d]=%x\n", i, (1020+j), szFwData[i][1020+j]); // add for debug
                }
            }
            else
            {
                for (j = 0; j < 1024; j ++)
                {
                    nCrcMain = DrvCommonCrcGetValue(szFwData[i][j], nCrcMain);
                }
            }
        }
        else  // emem_info
        {
            for (j = 0; j < 1024; j ++)
            {
                nCrcInfo = DrvCommonCrcGetValue(_gDwIicInfoData[j], nCrcInfo);
            }
            
            if (eEmemType == EMEM_MAIN)
            {
                break;
            }
        }

#if defined(CONFIG_TOUCH_DRIVER_RUN_ON_QCOM_PLATFORM) || defined(CONFIG_TOUCH_DRIVER_RUN_ON_MTK_PLATFORM)
        for (j = 0; j < 8; j ++)
        {
            IicWriteData(SLAVE_I2C_ID_DWI2C, &szFwData[i][j*128], 128);
        }
#elif defined(CONFIG_TOUCH_DRIVER_RUN_ON_SPRD_PLATFORM)
        IicWriteData(SLAVE_I2C_ID_DWI2C, szFwData[i], 1024);
#endif

        // Polling 0x3CE4 is 0xD0BC
        do
        {
            nRegData = RegGet16BitValue(0x3CE4);
        } while (nRegData != 0xD0BC);

        RegSet16BitValue(0x3CE4, 0x2F43);
    }

    if ((eEmemType == EMEM_ALL) || (eEmemType == EMEM_MAIN))
    {
        // write file done and check crc
        RegSet16BitValue(0x3CE4, 0x1380);
    }
    mdelay(10);

    if ((eEmemType == EMEM_ALL) || (eEmemType == EMEM_MAIN))
    {
        // Polling 0x3CE4 is 0x9432
        do
        {
            nRegData = RegGet16BitValue(0x3CE4);
        } while (nRegData != 0x9432);
    }

    nCrcMain = nCrcMain ^ 0xffffffff;
    nCrcInfo = nCrcInfo ^ 0xffffffff;

    if ((eEmemType == EMEM_ALL) || (eEmemType == EMEM_MAIN))
    {
        // CRC Main from TP
        nCrcMainTp = RegGet16BitValue(0x3C80);
        nCrcMainTp = (nCrcMainTp << 16) | RegGet16BitValue(0x3C82);

        // CRC Info from TP
        nCrcInfoTp = RegGet16BitValue(0x3CA0);
        nCrcInfoTp = (nCrcInfoTp << 16) | RegGet16BitValue(0x3CA2);
    }
    DBG("nCrcMain=0x%x, nCrcInfo=0x%x, nCrcMainTp=0x%x, nCrcInfoTp=0x%x\n", nCrcMain, nCrcInfo, nCrcMainTp, nCrcInfoTp);

    g_FwDataCount = 0; // Reset g_FwDataCount to 0 after update firmware

    DbBusIICNotUseBus();
    DbBusNotStopMCU();
    DbBusExitSerialDebugMode();

    DrvPlatformLyrTouchDeviceResetHw();

    if ((eEmemType == EMEM_ALL) || (eEmemType == EMEM_MAIN))
    {
        if ((nCrcMainTp != nCrcMain) || (nCrcInfoTp != nCrcInfo))
        {
            DBG("Update FAILED\n");

            return -1;
        }
    }
    
    DBG("Update SUCCESS\n");

    return 0;
}
#endif
static s32 _DrvFwCtrlMsg22xxUpdateFirmware(u8 szFwData[][1024], EmemType_e eEmemType)
{
    u32 i = 0, index = 0;
    u32 nEraseCount = 0;
    u32 nMaxEraseTimes = 0;
    u32 nCrcMain = 0, nCrcMainTp = 0;
    u32 nCrcInfo = 0, nCrcInfoTp = 0;
    u32 nRemainSize, nBlockSize, nSize;
    u32 nTimeOut = 0;
    u16 nRegData = 0;
    u16 nTrimByte1 = 0;
#if defined(CONFIG_TOUCH_DRIVER_RUN_ON_QCOM_PLATFORM) || defined(CONFIG_TOUCH_DRIVER_RUN_ON_MTK_PLATFORM)
    u8 szDbBusTxData[128] = {0};
#ifdef CONFIG_ENABLE_UPDATE_FIRMWARE_WITH_SUPPORT_I2C_SPEED_400K
    u32 nSizePerWrite = 1;
#else 
    u32 nSizePerWrite = 125;
#endif //CONFIG_ENABLE_UPDATE_FIRMWARE_WITH_SUPPORT_I2C_SPEED_400K
#elif defined(CONFIG_TOUCH_DRIVER_RUN_ON_SPRD_PLATFORM)
    u8 szDbBusTxData[1024] = {0};
#ifdef CONFIG_ENABLE_UPDATE_FIRMWARE_WITH_SUPPORT_I2C_SPEED_400K
    u32 nSizePerWrite = 1;
#else
    u32 nSizePerWrite = 1021;
#endif //CONFIG_ENABLE_UPDATE_FIRMWARE_WITH_SUPPORT_I2C_SPEED_400K
#endif

    DBG("*** %s() ***\n", __func__);

#ifdef CONFIG_TOUCH_DRIVER_RUN_ON_MTK_PLATFORM
#ifdef CONFIG_ENABLE_DMA_IIC
    DmaReset();
#endif //CONFIG_ENABLE_DMA_IIC
#endif //CONFIG_TOUCH_DRIVER_RUN_ON_MTK_PLATFORM

    _DrvFwCtrlMsg22xxConvertFwDataTwoDimenToOneDimen(szFwData, _gOneDimenFwData);

    DrvPlatformLyrTouchDeviceResetHw();

    DbBusEnterSerialDebugMode();
    DbBusStopMCU();
    DbBusIICUseBus();
    DbBusIICReshape();
    
    DBG("Erase start\n");

    // Stop mcu
    RegSet16BitValue(0x0FE6, 0x0001);

    nTrimByte1 = _DrvFwCtrlMsg22xxGetTrimByte1();
    
    _DrvFwCtrlMsg22xxChangeVoltage();

    // Disable watchdog
    RegSetLByteValue(0x3C60, 0x55);
    RegSetLByteValue(0x3C61, 0xAA);

    // Set PROGRAM password
    RegSetLByteValue(0x161A, 0xBA);
    RegSetLByteValue(0x161B, 0xAB);

    if (nTrimByte1 == 0xCA)
    {
        nMaxEraseTimes = MAX_ERASE_EFLASH_TIMES;
    }
    else
    {
        nMaxEraseTimes = 1;	
    }
    
    for (nEraseCount = 0; nEraseCount < nMaxEraseTimes; nEraseCount ++)
    {
        if (eEmemType == EMEM_ALL) // 48KB + 512Byte
        {
            DBG("Erase all block %d times\n", nEraseCount);

            // Clear pce
            RegSetLByteValue(0x1618, 0x80);
            mdelay(100);

            // Chip erase
            RegSet16BitValue(0x160E, BIT3);

            DBG("Wait erase done flag\n");

            while (1) // Wait erase done flag
            {
                nRegData = RegGet16BitValue(0x1610); // Memory status
                nRegData = nRegData & BIT1;
            
                DBG("Wait erase done flag nRegData = 0x%x\n", nRegData);

                if (nRegData == BIT1)
                {
                    break;		
                }

                mdelay(50);

                if ((nTimeOut ++) > 30)
                {
                    DBG("Erase all block %d times failed. Timeout.\n", nEraseCount);

                    if (nEraseCount == (nMaxEraseTimes - 1))
                    {
                        goto UpdateEnd;
                    }
                }
            }
        }
        else if (eEmemType == EMEM_MAIN) // 48KB (32+8+8)
        {
            DBG("Erase main block %d times\n", nEraseCount);

            for (i = 0; i < 3; i ++)
            {
                // Clear pce
                RegSetLByteValue(0x1618, 0x80);
                mdelay(10);
 
                if (i == 0)
                {
                    RegSet16BitValue(0x1600, 0x0000);
                }
                else if (i == 1)
                {
                    RegSet16BitValue(0x1600, 0x8000);
                }
                else if (i == 2)
                {
                    RegSet16BitValue(0x1600, 0xA000);
                }

                // Sector erase
                RegSet16BitValue(0x160E, (RegGet16BitValue(0x160E) | BIT2));

                DBG("Wait erase done flag\n");

                nRegData = 0;
                nTimeOut = 0;

                while (1) // Wait erase done flag
                {
                    nRegData = RegGet16BitValue(0x1610); // Memory status
                    nRegData = nRegData & BIT1;
            
                    DBG("Wait erase done flag nRegData = 0x%x\n", nRegData);

                    if (nRegData == BIT1)
                    {
                        break;		
                    }
                    mdelay(50);

                    if ((nTimeOut ++) > 30)
                    {
                        DBG("Erase main block %d times failed. Timeout.\n", nEraseCount);

                        if (nEraseCount == (nMaxEraseTimes - 1))
                        {
                            goto UpdateEnd;
                        }
                    }
                }
            }   
        }
        else if (eEmemType == EMEM_INFO) // 512Byte
        {
            DBG("Erase info block %d times\n", nEraseCount);

            // Clear pce
            RegSetLByteValue(0x1618, 0x80);
            mdelay(10);

            RegSet16BitValue(0x1600, 0xC000);
        
            // Sector erase
            RegSet16BitValue(0x160E, (RegGet16BitValue(0x160E) | BIT2));

            DBG("Wait erase done flag\n");

            while (1) // Wait erase done flag
            {
                nRegData = RegGet16BitValue(0x1610); // Memory status
                nRegData = nRegData & BIT1;
            
                DBG("Wait erase done flag nRegData = 0x%x\n", nRegData);

                if (nRegData == BIT1)
                {
                    break;		
                }
                mdelay(50);

                if ((nTimeOut ++) > 30)
                {
                    DBG("Erase info block %d times failed. Timeout.\n", nEraseCount);

                    if (nEraseCount == (nMaxEraseTimes - 1))
                    {
                        goto UpdateEnd;
                    }
                }
            }
        }
    }

    _DrvFwCtrlMsg22xxRestoreVoltage();
    
    DBG("Erase end\n");
    
    // Hold reset pin before program
    RegSetLByteValue(0x1E06, 0x00);

    /////////////////////////
    // Program
    /////////////////////////

    if (eEmemType == EMEM_ALL || eEmemType == EMEM_MAIN) // 48KB
    {
        DBG("Program main block start\n");
		
        // Program main block
        RegSet16BitValue(0x161A, 0xABBA);
        RegSet16BitValue(0x1618, (RegGet16BitValue(0x1618) | 0x80));
		
        RegSet16BitValue(0x1600, 0x0000); // Set start address of main block
        RegSet16BitValue(0x160C, (RegGet16BitValue(0x160C) | 0x01)); // Enable burst mode
		
        // Program start
        szDbBusTxData[0] = 0x10;
        szDbBusTxData[1] = 0x16;
        szDbBusTxData[2] = 0x02;
		
        IicWriteData(SLAVE_I2C_ID_DBBUS, &szDbBusTxData[0], 3);    
		
        szDbBusTxData[0] = 0x20;
		
        IicWriteData(SLAVE_I2C_ID_DBBUS, &szDbBusTxData[0], 1);    
		
        nRemainSize = MSG22XX_FIRMWARE_MAIN_BLOCK_SIZE * 1024; //48KB
        index = 0;
		    
        while (nRemainSize > 0)
        {
            if (nRemainSize > nSizePerWrite)
            {
                nBlockSize = nSizePerWrite;
            }
            else
            {
                nBlockSize = nRemainSize;
            }
		
            szDbBusTxData[0] = 0x10;
            szDbBusTxData[1] = 0x16;
            szDbBusTxData[2] = 0x02;
		
            nSize = 3;
		
            for (i = 0; i < nBlockSize; i ++)
            {
                szDbBusTxData[3+i] = _gOneDimenFwData[index*nSizePerWrite+i];
                nSize ++; 
            }
            index ++;
		
            IicWriteData(SLAVE_I2C_ID_DBBUS, &szDbBusTxData[0], nSize);
		        
            nRemainSize = nRemainSize - nBlockSize;
        }
		
        // Program end
        szDbBusTxData[0] = 0x21;
		
        IicWriteData(SLAVE_I2C_ID_DBBUS, &szDbBusTxData[0], 1);    
		
        RegSet16BitValue(0x160C, RegGet16BitValue(0x160C) & (~0x01)); // Clear burst mode

        DBG("Wait main block write done flag\n");
		
        nRegData = 0;
        nTimeOut = 0;

        while (1) // Wait write done flag
        {
            // Polling 0x1610 is 0x0002
            nRegData = RegGet16BitValue(0x1610); // Memory status
            nRegData = nRegData & BIT1;
    
            DBG("Wait write done flag nRegData = 0x%x\n", nRegData);

            if (nRegData == BIT1)
            {
                break;		
            }
            mdelay(10);

            if ((nTimeOut ++) > 30)
            {
                DBG("Write failed. Timeout.\n");

                goto UpdateEnd;
            }
        }
    
        DBG("Program main block end\n");
    }
    
    if (eEmemType == EMEM_ALL || eEmemType == EMEM_INFO) // 512 Byte
    {
        DBG("Program info block start\n");

        // Program info block
        RegSet16BitValue(0x161A, 0xABBA);
        RegSet16BitValue(0x1618, (RegGet16BitValue(0x1618) | 0x80));

        RegSet16BitValue(0x1600, 0xC000); // Set start address of info block
        RegSet16BitValue(0x160C, (RegGet16BitValue(0x160C) | 0x01)); // Enable burst mode

        // Program start
        szDbBusTxData[0] = 0x10;
        szDbBusTxData[1] = 0x16;
        szDbBusTxData[2] = 0x02;

        IicWriteData(SLAVE_I2C_ID_DBBUS, &szDbBusTxData[0], 3);    

        szDbBusTxData[0] = 0x20;

        IicWriteData(SLAVE_I2C_ID_DBBUS, &szDbBusTxData[0], 1);    

        nRemainSize = MSG22XX_FIRMWARE_INFO_BLOCK_SIZE; //512Byte
        index = 0;
    
        while (nRemainSize > 0)
        {
            if (nRemainSize > nSizePerWrite)
            {
                nBlockSize = nSizePerWrite;
            }
            else
            {
                nBlockSize = nRemainSize;
            }

            szDbBusTxData[0] = 0x10;
            szDbBusTxData[1] = 0x16;
            szDbBusTxData[2] = 0x02;

            nSize = 3;

            for (i = 0; i < nBlockSize; i ++)
            {
                szDbBusTxData[3+i] = _gOneDimenFwData[(MSG22XX_FIRMWARE_MAIN_BLOCK_SIZE*1024)+(index*nSizePerWrite)+i];
                nSize ++; 
            }
            index ++;

            IicWriteData(SLAVE_I2C_ID_DBBUS, &szDbBusTxData[0], nSize);
        
            nRemainSize = nRemainSize - nBlockSize;
        }

        // Program end
        szDbBusTxData[0] = 0x21;

        IicWriteData(SLAVE_I2C_ID_DBBUS, &szDbBusTxData[0], 1);    

        RegSet16BitValue(0x160C, RegGet16BitValue(0x160C) & (~0x01)); // Clear burst mode

        DBG("Wait info block write done flag\n");

        nRegData = 0;
        nTimeOut = 0;

        while (1) // Wait write done flag
        {
            // Polling 0x1610 is 0x0002
            nRegData = RegGet16BitValue(0x1610); // Memory status
            nRegData = nRegData & BIT1;
    
            DBG("Wait write done flag nRegData = 0x%x\n", nRegData);

            if (nRegData == BIT1)
            {
                break;		
            }
            mdelay(10);

            if ((nTimeOut ++) > 30)
            {
                DBG("Write failed. Timeout.\n");

                goto UpdateEnd;
            }
        }

        DBG("Program info block end\n");
    }
    
    UpdateEnd:

    if (eEmemType == EMEM_ALL || eEmemType == EMEM_MAIN)
    {
        // Get CRC 32 from updated firmware bin file
        nCrcMain  = _gOneDimenFwData[0xBFFF] << 24;
        nCrcMain |= _gOneDimenFwData[0xBFFE] << 16;
        nCrcMain |= _gOneDimenFwData[0xBFFD] << 8;
        nCrcMain |= _gOneDimenFwData[0xBFFC];

        // CRC Main from TP
        DBG("Get Main CRC from TP\n");

        nCrcMainTp = _DrvFwCtrlMsg22xxGetFirmwareCrcByHardware(EMEM_MAIN);
    
        DBG("nCrcMain=0x%x, nCrcMainTp=0x%x\n", nCrcMain, nCrcMainTp);
    }

    if (eEmemType == EMEM_ALL || eEmemType == EMEM_INFO)
    {
        nCrcInfo  = _gOneDimenFwData[0xC1FF] << 24;
        nCrcInfo |= _gOneDimenFwData[0xC1FE] << 16;
        nCrcInfo |= _gOneDimenFwData[0xC1FD] << 8;
        nCrcInfo |= _gOneDimenFwData[0xC1FC];

        // CRC Info from TP
        DBG("Get Info CRC from TP\n");

        nCrcInfoTp = _DrvFwCtrlMsg22xxGetFirmwareCrcByHardware(EMEM_INFO);

        DBG("nCrcInfo=0x%x, nCrcInfoTp=0x%x\n", nCrcInfo, nCrcInfoTp);
    }

    g_FwDataCount = 0; // Reset g_FwDataCount to 0 after update firmware

    DbBusIICNotUseBus();
    DbBusNotStopMCU();
    DbBusExitSerialDebugMode();

    DrvPlatformLyrTouchDeviceResetHw();

    if (eEmemType == EMEM_ALL)
    {
        if ((nCrcMainTp != nCrcMain) || (nCrcInfoTp != nCrcInfo))
        {
            DBG("Update FAILED\n");
          
            return -1;
        } 
    }
    else if (eEmemType == EMEM_MAIN)
    {
        if (nCrcMainTp != nCrcMain)
        {
            DBG("Update FAILED\n");
          
            return -1;
        } 
    }
    else if (eEmemType == EMEM_INFO)
    {
        if (nCrcInfoTp != nCrcInfo)
        {
            DBG("Update FAILED\n");
          
            return -1;
        } 
    }
    
    DBG("Update SUCCESS\n");

    return 0;
}

static s32 _DrvFwCtrlUpdateFirmwareCash(u8 szFwData[][1024])
{
    DBG("*** %s() ***\n", __func__);

    DBG("g_ChipType = 0x%x\n", g_ChipType);
    
    if (g_ChipType == CHIP_TYPE_MSG21XXA) // (0x02)
    {
//        u16 nChipType;
        u8 nChipVersion = 0;

        DrvPlatformLyrTouchDeviceResetHw();

        // Erase TP Flash first
        DbBusEnterSerialDebugMode();
        DbBusStopMCU();
        DbBusIICUseBus();
        DbBusIICReshape();
        mdelay(100);
    
        // Stop MCU
        RegSetLByteValue(0x0FE6, 0x01);

        // Disable watchdog
        RegSet16BitValue(0x3C60, 0xAA55);
    
        /////////////////////////
        // Difference between C2 and C3
        /////////////////////////
        // c2:MSG2133(1) c32:MSG2133A(2) c33:MSG2138A(2)
        // check ic type
//        nChipType = RegGet16BitValue(0x1ECC) & 0xFF;
            
        // check ic version
        nChipVersion = RegGet16BitValue(0x3CEA) & 0xFF;

        DBG("chip version = 0x%x\n", nChipVersion);
        
        if (nChipVersion == 3)
        {
#ifdef CONFIG_UPDATE_FIRMWARE_BY_SW_ID
            return _DrvFwCtrlMsg21xxaUpdateFirmwareBySwId(szFwData, EMEM_MAIN);
#else
            return _DrvFwCtrlUpdateFirmwareC33(szFwData, EMEM_MAIN);
#endif        
        }
        else
        {
#ifdef CONFIG_UPDATE_FIRMWARE_BY_SW_ID
            return _DrvFwCtrlMsg21xxaUpdateFirmwareBySwId(szFwData, EMEM_MAIN);
#else
            return _DrvFwCtrlUpdateFirmwareC32(szFwData, EMEM_ALL);
#endif        
        }
    }
    else if (g_ChipType == CHIP_TYPE_MSG22XX) // (0x7A)
    {
        _DrvFwCtrlMsg22xxGetTpVendorCode(_gTpVendorCode);
        
        if (_gTpVendorCode[0] == 'C' && _gTpVendorCode[1] == 'N' && _gTpVendorCode[2] == 'T') // for specific TP vendor which store some important information in info block, only update firmware for main block, do not update firmware for info block.
        {
            return _DrvFwCtrlMsg22xxUpdateFirmware(szFwData, EMEM_MAIN);
        }
        else
        {
            return _DrvFwCtrlMsg22xxUpdateFirmware(szFwData, EMEM_ALL);
        }
    }
    else // CHIP_TYPE_MSG21XX (0x01)
    {
        DBG("Can not update firmware. Catch-2 is no need to be maintained now.\n");
        g_FwDataCount = 0; // Reset g_FwDataCount to 0 after update firmware

        return -1;
    }
}

static s32 _DrvFwCtrlUpdateFirmwareBySdCard(const char *pFilePath)
{
    s32 nRetVal = 0;
    struct file *pfile = NULL;
    struct inode *inode;
    s32 fsize = 0;
    u8 *pbt_buf = NULL;
    mm_segment_t old_fs;
    loff_t pos;
    u16 eSwId = 0x0000;
    u16 eVendorId = 0x0000;
    
    DBG("*** %s() ***\n", __func__);

    pfile = filp_open(pFilePath, O_RDONLY, 0);
    if (IS_ERR(pfile))
    {
        DBG("Error occured while opening file %s.\n", pFilePath);
        return -1;
    }

    inode = pfile->f_dentry->d_inode;
    fsize = inode->i_size;

    DBG("fsize = %d\n", fsize);

    if (fsize <= 0)
    {
        filp_close(pfile, NULL);
        return -1;
    }

    // read firmware
    pbt_buf = kmalloc(fsize, GFP_KERNEL);

    old_fs = get_fs();
    set_fs(KERNEL_DS);
  
    pos = 0;
    vfs_read(pfile, pbt_buf, fsize, &pos);
  
    filp_close(pfile, NULL);
    set_fs(old_fs);

    _DrvFwCtrlStoreFirmwareData(pbt_buf, fsize);

    kfree(pbt_buf);

    DrvPlatformLyrDisableFingerTouchReport();
    
    if (g_ChipType == CHIP_TYPE_MSG21XXA)    
    {
        eVendorId = g_FwData[31][0x34F] <<8 | g_FwData[31][0x34E];
        eSwId = _DrvFwCtrlMsg21xxaGetSwId(EMEM_MAIN);
    }
    else if (g_ChipType == CHIP_TYPE_MSG22XX)    
    {
        eVendorId = g_FwData[47][1013] <<8 | g_FwData[47][1012];
        eSwId = _DrvFwCtrlMsg22xxGetSwId(EMEM_MAIN);
    }

    DBG("eVendorId = 0x%x, eSwId = 0x%x\n", eVendorId, eSwId);
    DBG("IS_FORCE_TO_UPDATE_FIRMWARE_ENABLED = %d\n", IS_FORCE_TO_UPDATE_FIRMWARE_ENABLED);
    		
    if ((eSwId == eVendorId) || (IS_FORCE_TO_UPDATE_FIRMWARE_ENABLED))
    {
        if ((g_ChipType == CHIP_TYPE_MSG21XXA && fsize == 33792/* 33KB */) || (g_ChipType == CHIP_TYPE_MSG22XX && fsize == 49664/* 48.5KB */))
        {
    	      nRetVal = _DrvFwCtrlUpdateFirmwareCash(g_FwData);
        }
        else
       	{
            DBG("The file size of the update firmware bin file is not supported, fsize = %d\n", fsize);
            nRetVal = -1;
        }
    }
    else 
    {
        DBG("The vendor id of the update firmware bin file is different from the vendor id on e-flash.\n");
        nRetVal = -1;
    }

    g_FwDataCount = 0; // Reset g_FwDataCount to 0 after update firmware
    
    DrvPlatformLyrEnableFingerTouchReport();

    return nRetVal;
}

/*=============================================================*/
// GLOBAL FUNCTION DEFINITION
/*=============================================================*/

void DrvFwCtrlVariableInitialize(void)
{
    DBG("*** %s() ***\n", __func__);

    if (g_ChipType == CHIP_TYPE_MSG21XXA)
    {
//        FIRMWARE_MODE_UNKNOWN_MODE = MSG21XXA_FIRMWARE_MODE_UNKNOWN_MODE;
        FIRMWARE_MODE_DEMO_MODE = MSG21XXA_FIRMWARE_MODE_DEMO_MODE;
        FIRMWARE_MODE_DEBUG_MODE = MSG21XXA_FIRMWARE_MODE_DEBUG_MODE;
        FIRMWARE_MODE_RAW_DATA_MODE = MSG21XXA_FIRMWARE_MODE_RAW_DATA_MODE;

        g_FirmwareMode = FIRMWARE_MODE_DEMO_MODE;
    }
    else if (g_ChipType == CHIP_TYPE_MSG22XX)
    {
//        FIRMWARE_MODE_UNKNOWN_MODE = MSG22XX_FIRMWARE_MODE_UNKNOWN_MODE;
        FIRMWARE_MODE_DEMO_MODE = MSG22XX_FIRMWARE_MODE_DEMO_MODE;
        FIRMWARE_MODE_DEBUG_MODE = MSG22XX_FIRMWARE_MODE_DEBUG_MODE;
        FIRMWARE_MODE_RAW_DATA_MODE = MSG22XX_FIRMWARE_MODE_RAW_DATA_MODE;

        g_FirmwareMode = FIRMWARE_MODE_DEMO_MODE;
    }	
}	

void DrvFwCtrlOptimizeCurrentConsumption(void)
{
    u32 i;
    u8 szDbBusTxData[27] = {0};

    DBG("g_ChipType = 0x%x\n", g_ChipType);
    
#ifdef CONFIG_ENABLE_GESTURE_WAKEUP
    if (g_GestureWakeupFlag == 1)
    {
        return;
    }
#endif //CONFIG_ENABLE_GESTURE_WAKEUP

    if (g_ChipType == CHIP_TYPE_MSG22XX)
    {
        DBG("*** %s() ***\n", __func__);

        mutex_lock(&g_Mutex);

#ifdef CONFIG_TOUCH_DRIVER_RUN_ON_MTK_PLATFORM
#ifdef CONFIG_ENABLE_DMA_IIC
        DmaReset();
#endif //CONFIG_ENABLE_DMA_IIC
#endif //CONFIG_TOUCH_DRIVER_RUN_ON_MTK_PLATFORM

        DrvPlatformLyrTouchDeviceResetHw(); 

        DbBusEnterSerialDebugMode();
        DbBusStopMCU();
        DbBusIICUseBus();
        DbBusIICReshape();

        RegSet16BitValue(0x1618, (RegGet16BitValue(0x1618) | 0x80));

        // Enable burst mode
        RegSet16BitValue(0x160C, (RegGet16BitValue(0x160C) | 0x01));

        szDbBusTxData[0] = 0x10;
        szDbBusTxData[1] = 0x11;
        szDbBusTxData[2] = 0xA0; //bank:0x11, addr:h0050
    
        for (i = 0; i < 24; i ++)
        {
            szDbBusTxData[i+3] = 0x11;
        }
		
        IicWriteData(SLAVE_I2C_ID_DBBUS, &szDbBusTxData[0], 3+24);  // Write 0x11 for reg 0x1150~0x115B

        szDbBusTxData[0] = 0x10;
        szDbBusTxData[1] = 0x11;
        szDbBusTxData[2] = 0xB8; //bank:0x11, addr:h005C
    
        for (i = 0; i < 6; i ++)
        {
            szDbBusTxData[i+3] = 0xFF;
        }
		
        IicWriteData(SLAVE_I2C_ID_DBBUS, &szDbBusTxData[0], 3+6);   // Write 0xFF for reg 0x115C~0x115E 
    
        // Clear burst mode
        RegSet16BitValue(0x160C, RegGet16BitValue(0x160C) & (~0x01)); 
    
        DbBusIICNotUseBus();
        DbBusNotStopMCU();
        DbBusExitSerialDebugMode();

        mutex_unlock(&g_Mutex);
    }
}

u8 DrvFwCtrlGetChipType(void)
{
    s32 rc =0;
    u8 nChipType = 0;

    DBG("*** %s() ***\n", __func__);

    // Erase TP Flash first
    rc = DbBusEnterSerialDebugMode();
    if (rc < 0)
    {
        DBG("*** DbBusEnterSerialDebugMode() failed, rc = %d ***\n", rc);
        return nChipType;
    }
    DbBusStopMCU();
    DbBusIICUseBus();
    DbBusIICReshape();

    // Stop MCU
    RegSetLByteValue(0x0FE6, 0x01);

    // Disable watchdog
    RegSet16BitValue(0x3C60, 0xAA55);
    
    /////////////////////////
    // Difference between C2 and C3
    /////////////////////////
    // c2:MSG2133(1) c32:MSG2133A(2) c33:MSG2138A(2)
    // check ic type
    nChipType = RegGet16BitValue(0x1ECC) & 0xFF;

    if (nChipType != CHIP_TYPE_MSG21XX &&   // (0x01) 
        nChipType != CHIP_TYPE_MSG21XXA &&  // (0x02) 
        nChipType != CHIP_TYPE_MSG26XXM &&  // (0x03) 
        nChipType != CHIP_TYPE_MSG22XX &&   // (0x7A) 
        nChipType != CHIP_TYPE_MSG28XX)     // (0x85) 
    {
        nChipType = 0;
    }

    DBG("*** Chip Type = 0x%x ***\n", nChipType);

    DbBusIICNotUseBus();
    DbBusNotStopMCU();
    DbBusExitSerialDebugMode();
    
    return nChipType;
}

void DrvFwCtrlGetCustomerFirmwareVersionCutdown(u16 *pMajor, u16 *pMinor)
{
        u16 nRegData1, nRegData2;

        mutex_lock(&g_Mutex);

        DrvPlatformLyrTouchDeviceResetHw();

        DbBusEnterSerialDebugMode();
        DbBusStopMCU();
        DbBusIICUseBus();
        DbBusIICReshape();
        mdelay(100);

#ifdef CONFIG_ENABLE_UPDATE_FIRMWARE_WITH_SUPPORT_I2C_SPEED_400K
        // Stop MCU
        RegSetLByteValue(0x0FE6, 0x01);

        // Change MCU clock deglich mux source
        RegSet16BitValue(0x1E54, (RegGet16BitValue(0x1E54) | BIT0));

        // Change PIU clock to 48 MHz
        RegSet16BitValue(0x1E22, (RegGet16BitValue(0x1E22) | BIT14));

        // Set MCU clock setting
        RegSet16BitValue(0x1E22, (RegGet16BitValue(0x1E22) & (~(BIT3 | BIT2))));

        // Set DB bus clock setting
        RegSet16BitValue(0x1E24, (RegGet16BitValue(0x1E24) & (~(BIT3 | BIT2))));
#else
        // Stop MCU
        RegSetLByteValue(0x0FE6, 0x01);
#endif //CONFIG_ENABLE_UPDATE_FIRMWARE_WITH_SUPPORT_I2C_SPEED_400K

        // Stop watchdog
        RegSet16BitValue(0x3C60, 0xAA55);

        // RIU password
        RegSet16BitValue(0x161A, 0xABBA);

        RegSet16BitValue(0x1600, 0xBFF4); // Set start address for customer firmware version on main block

        // Enable burst mode
//        RegSet16BitValue(0x160C, (RegGet16BitValue(0x160C) | 0x01));

	RegSetLByteValue(0x160E, 0x01);

        nRegData1 = RegGet16BitValue(0x1604);
        nRegData2 = RegGet16BitValue(0x1606);

        *pMajor = (((nRegData1 >> 8) & 0xFF) << 8) + (nRegData1 & 0xFF);
        *pMinor = (((nRegData2 >> 8) & 0xFF) << 8) + (nRegData2 & 0xFF);

        // Clear burst mode
//        RegSet16BitValue(0x160C, RegGet16BitValue(0x160C) & (~0x01));      

        RegSet16BitValue(0x1600, 0x0000);

        // Clear RIU password
        RegSet16BitValue(0x161A, 0x0000);

        DbBusIICNotUseBus();
        DbBusNotStopMCU();
        DbBusExitSerialDebugMode();

        DrvPlatformLyrTouchDeviceResetHw();

        mutex_unlock(&g_Mutex);
}

void DrvFwCtrlGetCustomerFirmwareVersion(u16 *pMajor, u16 *pMinor, u8 **ppVersion)
{
    DBG("*** %s() ***\n", __func__);
    
    if (g_ChipType == CHIP_TYPE_MSG21XXA || g_ChipType == CHIP_TYPE_MSG21XX)
    {
        u8 szDbBusTxData[3] = {0};
        u8 szDbBusRxData[4] = {0};

        szDbBusTxData[0] = 0x53;
        szDbBusTxData[1] = 0x00;

        if (g_ChipType == CHIP_TYPE_MSG21XXA)
        {    
            szDbBusTxData[2] = 0x2A;
        }
        else if (g_ChipType == CHIP_TYPE_MSG21XX)
        {
            szDbBusTxData[2] = 0x74;
        }
        else
        {
            szDbBusTxData[2] = 0x2A;
        }

        mutex_lock(&g_Mutex);

        DrvPlatformLyrTouchDeviceResetHw();

        IicWriteData(SLAVE_I2C_ID_DWI2C, &szDbBusTxData[0], 3);
        IicReadData(SLAVE_I2C_ID_DWI2C, &szDbBusRxData[0], 4);

        mutex_unlock(&g_Mutex);

        *pMajor = (szDbBusRxData[1]<<8) + szDbBusRxData[0];
        *pMinor = (szDbBusRxData[3]<<8) + szDbBusRxData[2];
    }
    else if (g_ChipType == CHIP_TYPE_MSG22XX)
    {
        u16 nRegData1, nRegData2;

        mutex_lock(&g_Mutex);

        DrvPlatformLyrTouchDeviceResetHw();
    
        DbBusEnterSerialDebugMode();
        DbBusStopMCU();
        DbBusIICUseBus();
        DbBusIICReshape();
        mdelay(100);
        
#ifdef CONFIG_ENABLE_UPDATE_FIRMWARE_WITH_SUPPORT_I2C_SPEED_400K
        // Stop MCU
        RegSetLByteValue(0x0FE6, 0x01); 
    
        // Change MCU clock deglich mux source
        RegSet16BitValue(0x1E54, (RegGet16BitValue(0x1E54) | BIT0)); 

        // Change PIU clock to 48 MHz
        RegSet16BitValue(0x1E22, (RegGet16BitValue(0x1E22) | BIT14)); 

        // Set MCU clock setting
        RegSet16BitValue(0x1E22, (RegGet16BitValue(0x1E22) & (~(BIT3 | BIT2)))); 

        // Set DB bus clock setting
        RegSet16BitValue(0x1E24, (RegGet16BitValue(0x1E24) & (~(BIT3 | BIT2)))); 
#else
        // Stop MCU
        RegSetLByteValue(0x0FE6, 0x01); 
#endif //CONFIG_ENABLE_UPDATE_FIRMWARE_WITH_SUPPORT_I2C_SPEED_400K

        // Stop watchdog
        RegSet16BitValue(0x3C60, 0xAA55);

        // RIU password
        RegSet16BitValue(0x161A, 0xABBA); 

        RegSet16BitValue(0x1600, 0xBFF4); // Set start address for customer firmware version on main block
    
        // Enable burst mode
//        RegSet16BitValue(0x160C, (RegGet16BitValue(0x160C) | 0x01));

        RegSetLByteValue(0x160E, 0x01); 

        nRegData1 = RegGet16BitValue(0x1604);
        nRegData2 = RegGet16BitValue(0x1606);

        *pMajor = (((nRegData1 >> 8) & 0xFF) << 8) + (nRegData1 & 0xFF);
        *pMinor = (((nRegData2 >> 8) & 0xFF) << 8) + (nRegData2 & 0xFF);

        // Clear burst mode
//        RegSet16BitValue(0x160C, RegGet16BitValue(0x160C) & (~0x01));      

        RegSet16BitValue(0x1600, 0x0000); 

        // Clear RIU password
        RegSet16BitValue(0x161A, 0x0000); 

        DbBusIICNotUseBus();
        DbBusNotStopMCU();
        DbBusExitSerialDebugMode();

        DrvPlatformLyrTouchDeviceResetHw();

        mutex_unlock(&g_Mutex);
    }

    DBG("*** major = %d ***\n", *pMajor);
    DBG("*** minor = %d ***\n", *pMinor);

    if (*ppVersion == NULL)
    {
        *ppVersion = kzalloc(sizeof(u8)*6, GFP_KERNEL);
    }
    
    sprintf(*ppVersion, "%03d%03d", *pMajor, *pMinor);
}

void DrvFwCtrlGetPlatformFirmwareVersion(u8 **ppVersion)
{
    u32 i;
    u16 nRegData1, nRegData2;
    u8 szDbBusRxData[12] = {0};

    DBG("*** %s() ***\n", __func__);

    mutex_lock(&g_Mutex);

    DrvPlatformLyrTouchDeviceResetHw();
    
    DbBusEnterSerialDebugMode();
    DbBusStopMCU();
    DbBusIICUseBus();
    DbBusIICReshape();
    mdelay(100);

    if (g_ChipType == CHIP_TYPE_MSG22XX) // Only MSG22XX support platform firmware version
    {
#ifdef CONFIG_ENABLE_UPDATE_FIRMWARE_WITH_SUPPORT_I2C_SPEED_400K
        // Stop MCU
        RegSetLByteValue(0x0FE6, 0x01); 
    
        // Change MCU clock deglich mux source
        RegSet16BitValue(0x1E54, (RegGet16BitValue(0x1E54) | BIT0)); 

        // Change PIU clock to 48 MHz
        RegSet16BitValue(0x1E22, (RegGet16BitValue(0x1E22) | BIT14)); 

        // Set MCU clock setting
        RegSet16BitValue(0x1E22, (RegGet16BitValue(0x1E22) & (~(BIT3 | BIT2)))); 

        // Set DB bus clock setting
        RegSet16BitValue(0x1E24, (RegGet16BitValue(0x1E24) & (~(BIT3 | BIT2)))); 
#else
        // Stop MCU
        RegSetLByteValue(0x0FE6, 0x01); 
#endif //CONFIG_ENABLE_UPDATE_FIRMWARE_WITH_SUPPORT_I2C_SPEED_400K

        // Stop watchdog
        RegSet16BitValue(0x3C60, 0xAA55);

        // RIU password
        RegSet16BitValue(0x161A, 0xABBA); 

        RegSet16BitValue(0x1600, 0xC1F2); // Set start address for platform firmware version on info block(Actually, start reading from 0xC1F0)
    
        // Enable burst mode
        RegSet16BitValue(0x160C, (RegGet16BitValue(0x160C) | 0x01));

        for (i = 0; i < 3; i ++)
        {
            RegSetLByteValue(0x160E, 0x01); 

            nRegData1 = RegGet16BitValue(0x1604);
            nRegData2 = RegGet16BitValue(0x1606);

            szDbBusRxData[i*4+0] = (nRegData1 & 0xFF);
            szDbBusRxData[i*4+1] = ((nRegData1 >> 8 ) & 0xFF);
            
//            DBG("szDbBusRxData[%d] = 0x%x , %c \n", i*4+0, szDbBusRxData[i*4+0], szDbBusRxData[i*4+0]); // add for debug
//            DBG("szDbBusRxData[%d] = 0x%x , %c \n", i*4+1, szDbBusRxData[i*4+1], szDbBusRxData[i*4+1]); // add for debug
            
            szDbBusRxData[i*4+2] = (nRegData2 & 0xFF);
            szDbBusRxData[i*4+3] = ((nRegData2 >> 8 ) & 0xFF);

//            DBG("szDbBusRxData[%d] = 0x%x , %c \n", i*4+2, szDbBusRxData[i*4+2], szDbBusRxData[i*4+2]); // add for debug
//            DBG("szDbBusRxData[%d] = 0x%x , %c \n", i*4+3, szDbBusRxData[i*4+3], szDbBusRxData[i*4+3]); // add for debug
        }

        // Clear burst mode
        RegSet16BitValue(0x160C, RegGet16BitValue(0x160C) & (~0x01));      

        RegSet16BitValue(0x1600, 0x0000); 

        // Clear RIU password
        RegSet16BitValue(0x161A, 0x0000); 

        if (*ppVersion == NULL)
        {
            *ppVersion = kzalloc(sizeof(u8)*10, GFP_KERNEL);
        }
    
        sprintf(*ppVersion, "%c%c%c%c%c%c%c%c%c%c", szDbBusRxData[2], szDbBusRxData[3], szDbBusRxData[4],
            szDbBusRxData[5], szDbBusRxData[6], szDbBusRxData[7], szDbBusRxData[8], szDbBusRxData[9], szDbBusRxData[10], szDbBusRxData[11]);
    }
    else
    {
        if (*ppVersion == NULL)
        {
            *ppVersion = kzalloc(sizeof(u8)*10, GFP_KERNEL);
        }
    
        sprintf(*ppVersion, "%s", "N/A");
    }

    DbBusIICNotUseBus();
    DbBusNotStopMCU();
    DbBusExitSerialDebugMode();

    DrvPlatformLyrTouchDeviceResetHw();

    mutex_unlock(&g_Mutex);
    
    DBG("*** platform firmware version = %s ***\n", *ppVersion);
}

s32 DrvFwCtrlUpdateFirmware(u8 szFwData[][1024], EmemType_e eEmemType)
{
    DBG("*** %s() ***\n", __func__);

    return _DrvFwCtrlUpdateFirmwareCash(szFwData);
}	

s32 DrvFwCtrlUpdateFirmwareBySdCard(const char *pFilePath)
{
    s32 nRetVal = -1;
    
    DBG("*** %s() ***\n", __func__);

    if (g_ChipType == CHIP_TYPE_MSG21XXA || g_ChipType == CHIP_TYPE_MSG22XX)    
    {
        nRetVal = _DrvFwCtrlUpdateFirmwareBySdCard(pFilePath);
    }
    else
    {
        DBG("This chip type (%d) does not support update firmware by sd card\n", g_ChipType);
    }
    
    return nRetVal;
}	

void DrvFwCtrlHandleFingerTouch(void)
{
    TouchInfo_t tInfo;
    u32 i;
    u8 nTouchKeyCode = 0;
    static u32 nLastKeyCode = 0;
    u8 *pPacket = NULL;
    u16 nReportPacketLength = 0;
    s32 rc;

//    DBG("*** %s() ***\n", __func__);  // add for debug
    
    if (_gIsDisableFinagerTouch == 1)
    {
        DBG("Skip finger touch for handling get firmware info or change firmware mode\n");
        return;
    }
    
    mutex_lock(&g_Mutex);
    DBG("*** %s() *** mutex_lock(&g_Mutex)\n", __func__);  // add for debug

    memset(&tInfo, 0x0, sizeof(TouchInfo_t));

    if (IS_FIRMWARE_DATA_LOG_ENABLED)
    { 	
        if (g_FirmwareMode == FIRMWARE_MODE_DEMO_MODE)
        {
            DBG("FIRMWARE_MODE_DEMO_MODE\n");

            nReportPacketLength = DEMO_MODE_PACKET_LENGTH;
            pPacket = g_DemoModePacket;
        }
        else if (g_FirmwareMode == FIRMWARE_MODE_DEBUG_MODE)
        {
            DBG("FIRMWARE_MODE_DEBUG_MODE\n");

            if (g_FirmwareInfo.nLogModePacketHeader != 0x62)
            {
                DBG("WRONG DEBUG MODE HEADER : 0x%x\n", g_FirmwareInfo.nLogModePacketHeader);
                goto TouchHandleEnd;		
            }

            nReportPacketLength = g_FirmwareInfo.nLogModePacketLength;
            pPacket = g_LogModePacket;
        }
        else if (g_FirmwareMode == FIRMWARE_MODE_RAW_DATA_MODE)
        {
            DBG("FIRMWARE_MODE_RAW_DATA_MODE\n");

            if (g_FirmwareInfo.nLogModePacketHeader != 0x62)
            {
                DBG("WRONG RAW DATA MODE HEADER : 0x%x\n", g_FirmwareInfo.nLogModePacketHeader);
                goto TouchHandleEnd;		
            }

            nReportPacketLength = g_FirmwareInfo.nLogModePacketLength;
            pPacket = g_LogModePacket;
        }
        else
        {
            DBG("WRONG FIRMWARE MODE : 0x%x\n", g_FirmwareMode);
            goto TouchHandleEnd;		
        }
    }
    else
    {
        DBG("FIRMWARE_MODE_DEMO_MODE\n");

        nReportPacketLength = DEMO_MODE_PACKET_LENGTH;
        pPacket = g_DemoModePacket;
    } //IS_FIRMWARE_DATA_LOG_ENABLED

#ifdef CONFIG_ENABLE_GESTURE_WAKEUP

#ifdef CONFIG_ENABLE_GESTURE_DEBUG_MODE
    if (g_GestureDebugMode == 1 && g_GestureWakeupFlag == 1)
    {
        DBG("Set gesture debug mode packet length, g_ChipType=%d\n", g_ChipType);

        if (g_ChipType == CHIP_TYPE_MSG22XX)
        {
            nReportPacketLength = GESTURE_DEBUG_MODE_PACKET_LENGTH;
            pPacket = _gGestureWakeupPacket;
        }
        else
        {
            DBG("This chip type does not support gesture debug mode.\n");
            goto TouchHandleEnd;		
        }
    }
    else if (g_GestureWakeupFlag == 1)
    {
        DBG("Set gesture wakeup packet length, g_ChipType=%d\n", g_ChipType);
      
        if (g_ChipType == CHIP_TYPE_MSG22XX)
        {
#ifdef CONFIG_ENABLE_GESTURE_INFORMATION_MODE
            nReportPacketLength = GESTURE_WAKEUP_INFORMATION_PACKET_LENGTH;
#else
            nReportPacketLength = GESTURE_WAKEUP_PACKET_LENGTH;
#endif //CONFIG_ENABLE_GESTURE_INFORMATION_MODE
            pPacket = _gGestureWakeupPacket;
        }
        else if (g_ChipType == CHIP_TYPE_MSG21XXA)
        {
            nReportPacketLength = DEMO_MODE_PACKET_LENGTH;
            pPacket = _gGestureWakeupPacket;
        }
        else
        {
            DBG("This chip type does not support gesture wakeup.\n");
            goto TouchHandleEnd;
        }
	}

#else

    if (g_GestureWakeupFlag == 1)
    {
        DBG("Set gesture wakeup packet length, g_ChipType=%d\n", g_ChipType);
        
        if (g_ChipType == CHIP_TYPE_MSG22XX)
        {
#ifdef CONFIG_ENABLE_GESTURE_INFORMATION_MODE
            nReportPacketLength = GESTURE_WAKEUP_INFORMATION_PACKET_LENGTH;
#else
            nReportPacketLength = GESTURE_WAKEUP_PACKET_LENGTH;
#endif //CONFIG_ENABLE_GESTURE_INFORMATION_MODE

            pPacket = _gGestureWakeupPacket;
        } 
        else if (g_ChipType == CHIP_TYPE_MSG21XXA)
        {
            nReportPacketLength = DEMO_MODE_PACKET_LENGTH;
            pPacket = _gGestureWakeupPacket;
        }
        else
        {
            DBG("This chip type does not support gesture wakeup.\n");
            goto TouchHandleEnd;		
        }
    }
#endif //CONFIG_ENABLE_GESTURE_DEBUG_MODE

#endif //CONFIG_ENABLE_GESTURE_WAKEUP

#if defined(CONFIG_TOUCH_DRIVER_RUN_ON_SPRD_PLATFORM)
#ifdef CONFIG_ENABLE_GESTURE_WAKEUP
    if (g_GestureWakeupFlag == 1)
    {
        u32 i = 0
        
        while (i < 5)
        {
            mdelay(50);

            rc = IicReadData(SLAVE_I2C_ID_DWI2C, &pPacket[0], nReportPacketLength);
            
            if (rc > 0)
            {
                break;
            }
            
            i ++;
        }
        if (i == 5)
        {
            DBG("I2C read packet data failed, rc = %d\n", rc);
            goto TouchHandleEnd;		
        }
    else
    {
        rc = IicReadData(SLAVE_I2C_ID_DWI2C, &pPacket[0], nReportPacketLength);
        if (rc < 0)
        {
            DBG("I2C read packet data failed, rc = %d\n", rc);
            goto TouchHandleEnd;		
        }
    }
#else
    rc = IicReadData(SLAVE_I2C_ID_DWI2C, &pPacket[0], nReportPacketLength);
    if (rc < 0)
    {
        DBG("I2C read packet data failed, rc = %d\n", rc);
        goto TouchHandleEnd;		
    }
#endif //CONFIG_ENABLE_GESTURE_WAKEUP   
#elif defined(CONFIG_TOUCH_DRIVER_RUN_ON_QCOM_PLATFORM)
    rc = IicReadData(SLAVE_I2C_ID_DWI2C, &pPacket[0], nReportPacketLength);
    if (rc < 0)
    {
        DBG("I2C read packet data failed, rc = %d\n", rc);
        goto TouchHandleEnd;		
    }
#elif defined(CONFIG_TOUCH_DRIVER_RUN_ON_MTK_PLATFORM)
    if (nReportPacketLength > 8)
    {
#ifdef CONFIG_ENABLE_DMA_IIC
        DmaReset();
#endif //CONFIG_ENABLE_DMA_IIC
        rc = IicReadData(SLAVE_I2C_ID_DWI2C, &pPacket[0], nReportPacketLength);
    }
    else
    {
        rc = IicReadData(SLAVE_I2C_ID_DWI2C, &pPacket[0], nReportPacketLength);
    }

    if (rc < 0)
    {
        DBG("I2C read packet data failed, rc = %d\n", rc);
        goto TouchHandleEnd;		
    }
#endif
    
    if (0 == _DrvFwCtrlParsePacket(pPacket, nReportPacketLength, &tInfo))
    {
        //report...
        if ((tInfo.nFingerNum) == 0)   //touch end
        {
            if (nLastKeyCode != 0)
            {
                DBG("key touch released\n");

                input_report_key(g_InputDevice, BTN_TOUCH, 0);
                input_report_key(g_InputDevice, nLastKeyCode, 0);

                input_sync(g_InputDevice);
                    
                nLastKeyCode = 0; //clear key status..
            }
            else
            {
#ifdef CONFIG_ENABLE_TYPE_B_PROTOCOL // TYPE B PROTOCOL
                for (i = 0; i < MAX_TOUCH_NUM; i ++) 
                {
                    DrvPlatformLyrFingerTouchReleased(0, 0, i);
                }
                
                input_mt_sync_frame(g_InputDevice);
#else // TYPE A PROTOCOL
                DrvPlatformLyrFingerTouchReleased(0, 0, 0);
#endif //CONFIG_ENABLE_TYPE_B_PROTOCOL

                input_sync(g_InputDevice);
            }
        }
        else //touch on screen
        {
            if (tInfo.nTouchKeyCode != 0)
            {
#ifdef CONFIG_TP_HAVE_KEY
                if (tInfo.nTouchKeyCode == 4) // TOUCH_KEY_HOME
                {
                    nTouchKeyCode = g_TpVirtualKey[1];           
                }
                else if (tInfo.nTouchKeyCode == 1) // TOUCH_KEY_MENU
                {
                    nTouchKeyCode = g_TpVirtualKey[0];
                }           
                else if (tInfo.nTouchKeyCode == 2) // TOUCH_KEY_BACK
                {
                    nTouchKeyCode = g_TpVirtualKey[2];
                }           
                else if (tInfo.nTouchKeyCode == 8) // TOUCH_KEY_SEARCH 
                {	
                    nTouchKeyCode = g_TpVirtualKey[3];           
                }

                if (nLastKeyCode != nTouchKeyCode)
                {
                    DBG("key touch pressed\n");
                    DBG("nTouchKeyCode = %d, nLastKeyCode = %d\n", nTouchKeyCode, nLastKeyCode);
                    
                    nLastKeyCode = nTouchKeyCode;

                    input_report_key(g_InputDevice, BTN_TOUCH, 1);
                    input_report_key(g_InputDevice, nTouchKeyCode, 1);

                    input_sync(g_InputDevice);

#ifdef CONFIG_ENABLE_TYPE_B_PROTOCOL 
                    _gPrevTouchStatus = 0;
#endif //CONFIG_ENABLE_TYPE_B_PROTOCOL
                }
#endif //CONFIG_TP_HAVE_KEY
            }
            else
            {
                DBG("tInfo->nFingerNum = %d...............\n", tInfo.nFingerNum);
                
#ifdef CONFIG_ENABLE_TYPE_B_PROTOCOL
                for (i = 0; i < MAX_TOUCH_NUM; i ++) 
                {
                    if (tInfo.nFingerNum != 0)
                    {
                        if (_gCurrPress[i])
                        {
                            DrvPlatformLyrFingerTouchPressed(tInfo.tPoint[i].nX, tInfo.tPoint[i].nY, 0, i);
                        }
                        else
                        {
                            DrvPlatformLyrFingerTouchReleased(0, 0, i);
                        }
                    }
                }
                
                input_mt_sync_frame(g_InputDevice);
#else // TYPE A PROTOCOL
                for (i = 0; i < tInfo.nFingerNum; i ++) 
                {
                    DrvPlatformLyrFingerTouchPressed(tInfo.tPoint[i].nX, tInfo.tPoint[i].nY, 0, 0);
                }
#endif //CONFIG_ENABLE_TYPE_B_PROTOCOL

                input_sync(g_InputDevice);
            }
        }

#ifdef CONFIG_ENABLE_COUNT_REPORT_RATE
        if (g_IsEnableReportRate == 1)
        {
            if (g_ValidTouchCount == 4294967296)
            {
                g_ValidTouchCount = 0; // Reset count if overflow
                DBG("g_ValidTouchCount reset to 0\n");
            } 	

            g_ValidTouchCount ++;

            DBG("g_ValidTouchCount = %d\n", g_ValidTouchCount);
        }
#endif //CONFIG_ENABLE_COUNT_REPORT_RATE
    }

    TouchHandleEnd: 
    	
    DBG("*** %s() *** mutex_unlock(&g_Mutex)\n", __func__);  // add for debug
    mutex_unlock(&g_Mutex);
}

//------------------------------------------------------------------------------//

#ifdef CONFIG_ENABLE_GESTURE_WAKEUP

void DrvFwCtrlOpenGestureWakeup(u32 *pMode)
{
    u8 szDbBusTxData[4] = {0};
    u32 i = 0;
    s32 rc;

    DBG("*** %s() ***\n", __func__);

    DBG("wakeup mode 0 = 0x%x\n", pMode[0]);
    DBG("wakeup mode 1 = 0x%x\n", pMode[1]);

#ifdef CONFIG_SUPPORT_64_TYPES_GESTURE_WAKEUP_MODE
    szDbBusTxData[0] = 0x59;
    szDbBusTxData[1] = 0x00;
    szDbBusTxData[2] = ((pMode[1] & 0xFF000000) >> 24);
    szDbBusTxData[3] = ((pMode[1] & 0x00FF0000) >> 16);

    while (i < 5)
    {
        mdelay(I2C_WRITE_COMMAND_DELAY_FOR_FIRMWARE);
        rc = IicWriteData(SLAVE_I2C_ID_DWI2C, &szDbBusTxData[0], 4);
        if (rc > 0)
        {
            DBG("Enable gesture wakeup index 0 success\n");
            break;
        }

        i++;
    }
    if (i == 5)
    {
        DBG("Enable gesture wakeup index 0 failed\n");
    }

    szDbBusTxData[0] = 0x59;
    szDbBusTxData[1] = 0x01;
    szDbBusTxData[2] = ((pMode[1] & 0x0000FF00) >> 8);
    szDbBusTxData[3] = ((pMode[1] & 0x000000FF) >> 0);
	
    while (i < 5)
    {
        mdelay(I2C_WRITE_COMMAND_DELAY_FOR_FIRMWARE);
        rc = IicWriteData(SLAVE_I2C_ID_DWI2C, &szDbBusTxData[0], 4);
        if (rc > 0)
        {
            DBG("Enable gesture wakeup index 1 success\n");
            break;
        }

        i++;
    }
    if (i == 5)
    {
        DBG("Enable gesture wakeup index 1 failed\n");
    }

    szDbBusTxData[0] = 0x59;
    szDbBusTxData[1] = 0x02;
    szDbBusTxData[2] = ((pMode[0] & 0xFF000000) >> 24);
    szDbBusTxData[3] = ((pMode[0] & 0x00FF0000) >> 16);
    
    while (i < 5)
    {
        mdelay(I2C_WRITE_COMMAND_DELAY_FOR_FIRMWARE);
        rc = IicWriteData(SLAVE_I2C_ID_DWI2C, &szDbBusTxData[0], 4);
        if (rc > 0)
        {
            DBG("Enable gesture wakeup index 2 success\n");
            break;
        }

        i++;
    }
    if (i == 5)
    {
        DBG("Enable gesture wakeup index 2 failed\n");
    }

    szDbBusTxData[0] = 0x59;
    szDbBusTxData[1] = 0x03;
    szDbBusTxData[2] = ((pMode[0] & 0x0000FF00) >> 8);
    szDbBusTxData[3] = ((pMode[0] & 0x000000FF) >> 0);
    
    while (i < 5)
    {
        mdelay(I2C_WRITE_COMMAND_DELAY_FOR_FIRMWARE);
        rc = IicWriteData(SLAVE_I2C_ID_DWI2C, &szDbBusTxData[0], 4);
        if (rc > 0)
        {
            DBG("Enable gesture wakeup index 3 success\n");
            break;
        }

        i++;
    }
    if (i == 5)
    {
        DBG("Enable gesture wakeup index 3 failed\n");
    }

    g_GestureWakeupFlag = 1; // gesture wakeup is enabled

#else
	
    szDbBusTxData[0] = 0x58;
    szDbBusTxData[1] = ((pMode[0] & 0x0000FF00) >> 8);
    szDbBusTxData[2] = ((pMode[0] & 0x000000FF) >> 0);

    while (i < 5)
    {
        mdelay(I2C_WRITE_COMMAND_DELAY_FOR_FIRMWARE);
        rc = IicWriteData(SLAVE_I2C_ID_DWI2C, &szDbBusTxData[0], 3);
        if (rc > 0)
        {
            DBG("Enable gesture wakeup success\n");
            break;
        }

        i++;
    }
    if (i == 5)
    {
        DBG("Enable gesture wakeup failed\n");
    }

    g_GestureWakeupFlag = 1; // gesture wakeup is enabled
#endif //CONFIG_SUPPORT_64_TYPES_GESTURE_WAKEUP_MODE
}

void DrvFwCtrlCloseGestureWakeup(void)
{
    DBG("*** %s() ***\n", __func__);

    g_GestureWakeupFlag = 0; // gesture wakeup is disabled
}

#ifdef CONFIG_ENABLE_GESTURE_DEBUG_MODE
void DrvFwCtrlOpenGestureDebugMode(u8 nGestureFlag)
{
    u8 szDbBusTxData[3] = {0};
    s32 rc;

    DBG("*** %s() ***\n", __func__);

    DBG("Gesture Flag = 0x%x\n", nGestureFlag);

    szDbBusTxData[0] = 0x30;
    szDbBusTxData[1] = 0x01;
    szDbBusTxData[2] = nGestureFlag;

    mdelay(I2C_WRITE_COMMAND_DELAY_FOR_FIRMWARE);
    rc = IicWriteData(SLAVE_I2C_ID_DWI2C, &szDbBusTxData[0], 3);
    if (rc < 0)
    {
        DBG("Enable gesture debug mode failed\n");
    }
    else
    {
        g_GestureDebugMode = 1; // gesture debug mode is enabled

        DBG("Enable gesture debug mode success\n");
    }
}

void DrvFwCtrlCloseGestureDebugMode(void)
{
    u8 szDbBusTxData[3] = {0};
    s32 rc;

    DBG("*** %s() ***\n", __func__);

    szDbBusTxData[0] = 0x30;
    szDbBusTxData[1] = 0x00;
    szDbBusTxData[2] = 0x00;

    mdelay(I2C_WRITE_COMMAND_DELAY_FOR_FIRMWARE);
    rc = IicWriteData(SLAVE_I2C_ID_DWI2C, &szDbBusTxData[0], 3);
    if (rc < 0)
    {
        DBG("Disable gesture debug mode failed\n");
    }
    else
    {
        g_GestureDebugMode = 0; // gesture debug mode is disabled

        DBG("Disable gesture debug mode success\n");
    }
}
#endif //CONFIG_ENABLE_GESTURE_DEBUG_MODE

#ifdef CONFIG_ENABLE_GESTURE_INFORMATION_MODE
static void _DrvFwCtrlCoordinate(u8 *pRawData, u32 *pTranX, u32 *pTranY)
{
    u32 nX;
    u32 nY;
#ifdef CONFIG_SWAP_X_Y
    u32 nTempX;
    u32 nTempY;
#endif

   	nX = (((pRawData[0] & 0xF0) << 4) | pRawData[1]);         // parse the packet to coordinate
    nY = (((pRawData[0] & 0x0F) << 8) | pRawData[2]);

    DBG("[x,y]=[%d,%d]\n", nX, nY);

#ifdef CONFIG_SWAP_X_Y
    nTempY = nX;
    nTempX = nY;
    nX = nTempX;
    nY = nTempY;
#endif

#ifdef CONFIG_REVERSE_X
    nX = 2047 - nX;
#endif

#ifdef CONFIG_REVERSE_Y
    nY = 2047 - nY;
#endif

    /*
     * pRawData[0]~nRawData[2] : the point abs,
     * pRawData[0]~nRawData[2] all are 0xFF, release touch
     */
    if ((pRawData[0] == 0xFF) && (pRawData[1] == 0xFF) && (pRawData[2] == 0xFF))
    {
        *pTranX = 0; // final X coordinate
        *pTranY = 0; // final Y coordinate
    }
    else
    {
        /* one touch point */
        *pTranX = (nX * TOUCH_SCREEN_X_MAX) / TPD_WIDTH;
        *pTranY = (nY * TOUCH_SCREEN_Y_MAX) / TPD_HEIGHT;
        DBG("[%s]: [x,y]=[%d,%d]\n", __func__, nX, nY);
        DBG("[%s]: point[x,y]=[%d,%d]\n", __func__, *pTranX, *pTranY);
    }
}
#endif //CONFIG_ENABLE_GESTURE_INFORMATION_MODE

#endif //CONFIG_ENABLE_GESTURE_WAKEUP

//------------------------------------------------------------------------------//

u16 DrvFwCtrlChangeFirmwareMode(u16 nMode)
{
    u8 szDbBusTxData[2] = {0};
    u32 i = 0;
    s32 rc;

    DBG("*** %s() *** nMode = 0x%x\n", __func__, nMode);

    _gIsDisableFinagerTouch = 1; // Disable finger touch ISR handling temporarily for device driver can send change firmware mode i2c command to firmware. 

    szDbBusTxData[0] = 0x02;
    szDbBusTxData[1] = (u8)nMode;

    mutex_lock(&g_Mutex);

    while (i < 5)
    {
        mdelay(I2C_WRITE_COMMAND_DELAY_FOR_FIRMWARE);
        rc = IicWriteData(SLAVE_I2C_ID_DWI2C, &szDbBusTxData[0], 2);
        if (rc > 0)
        {
            DBG("Change firmware mode success\n");
            break;
        }

        i++;
    }
    if (i == 5)
    {
        DBG("Change firmware mode failed, rc = %d\n", rc);
    }

    mutex_unlock(&g_Mutex);

    _gIsDisableFinagerTouch = 0;

    return nMode;
}

void DrvFwCtrlGetFirmwareInfo(FirmwareInfo_t *pInfo)
{
    u8 szDbBusTxData[1] = {0};
    u8 szDbBusRxData[8] = {0};
    u32 i = 0;
    s32 rc;
    
    DBG("*** %s() ***\n", __func__);

    _gIsDisableFinagerTouch = 1; // Disable finger touch ISR handling temporarily for device driver can send get firmware info i2c command to firmware. 

    szDbBusTxData[0] = 0x01;

    mutex_lock(&g_Mutex);
    
    while (i < 5)
    {
        mdelay(I2C_WRITE_COMMAND_DELAY_FOR_FIRMWARE);
        rc = IicWriteData(SLAVE_I2C_ID_DWI2C, &szDbBusTxData[0], 1);
        if (rc > 0)
        {
            DBG("Get firmware info IicWriteData() success\n");
        }
        mdelay(I2C_WRITE_COMMAND_DELAY_FOR_FIRMWARE);
        rc = IicReadData(SLAVE_I2C_ID_DWI2C, &szDbBusRxData[0], 8);
        if (rc > 0)
        {
            DBG("Get firmware info IicReadData() success\n");
            break;
        }

        i++;
    }
    if (i == 5)
    {
        DBG("Get firmware info failed, rc = %d\n", rc);
    }

    mutex_unlock(&g_Mutex);
    
    if ((szDbBusRxData[1] & 0x80) == 0x80)
    {
        pInfo->nIsCanChangeFirmwareMode = 0;	
    }
    else
    {
        pInfo->nIsCanChangeFirmwareMode = 1;	
    }
    
    pInfo->nFirmwareMode = szDbBusRxData[1] & 0x7F;
    pInfo->nLogModePacketHeader = szDbBusRxData[2];
    pInfo->nLogModePacketLength = (szDbBusRxData[3]<<8) + szDbBusRxData[4];

    DBG("pInfo->nFirmwareMode=0x%x, pInfo->nLogModePacketHeader=0x%x, pInfo->nLogModePacketLength=%d, pInfo->nIsCanChangeFirmwareMode=%d\n", pInfo->nFirmwareMode, pInfo->nLogModePacketHeader, pInfo->nLogModePacketLength, pInfo->nIsCanChangeFirmwareMode);

    _gIsDisableFinagerTouch = 0;
}

void DrvFwCtrlRestoreFirmwareModeToLogDataMode(void)
{
    DBG("*** %s() g_IsSwitchModeByAPK = %d ***\n", __func__, g_IsSwitchModeByAPK);

    if (g_IsSwitchModeByAPK == 1)
    {
        FirmwareInfo_t tInfo;
    
        memset(&tInfo, 0x0, sizeof(FirmwareInfo_t));

        DrvFwCtrlGetFirmwareInfo(&tInfo);

        DBG("g_FirmwareMode = 0x%x, tInfo.nFirmwareMode = 0x%x\n", g_FirmwareMode, tInfo.nFirmwareMode);

        // Since reset_hw() will reset the firmware mode to demo mode, we must reset the firmware mode again after reset_hw().
        if (g_FirmwareMode == FIRMWARE_MODE_DEBUG_MODE && FIRMWARE_MODE_DEBUG_MODE != tInfo.nFirmwareMode)
        {
            g_FirmwareMode = DrvFwCtrlChangeFirmwareMode(FIRMWARE_MODE_DEBUG_MODE);
        }
        else if (g_FirmwareMode == FIRMWARE_MODE_RAW_DATA_MODE && FIRMWARE_MODE_RAW_DATA_MODE != tInfo.nFirmwareMode)
        {
            g_FirmwareMode = DrvFwCtrlChangeFirmwareMode(FIRMWARE_MODE_RAW_DATA_MODE);
        }
        else
        {
            DBG("firmware mode is not restored\n");
        }
    }
}

//------------------------------------------------------------------------------//

#ifdef CONFIG_UPDATE_FIRMWARE_BY_SW_ID

void DrvFwCtrlCheckFirmwareUpdateBySwId(void)
{
    if (g_ChipType == CHIP_TYPE_MSG21XXA)   
    {
        _DrvFwCtrlMsg21xxaCheckFirmwareUpdateBySwId();
    }
    else if (g_ChipType == CHIP_TYPE_MSG22XX)    
    {
        _DrvFwCtrlMsg22xxCheckFirmwareUpdateBySwId();
    }
    else
    {
        DBG("This chip type (%d) does not support update firmware by sw id\n", g_ChipType);
    }
}	

#endif //CONFIG_UPDATE_FIRMWARE_BY_SW_ID

//------------------------------------------------------------------------------//

#ifdef CONFIG_ENABLE_PROXIMITY_DETECTION

s32 DrvFwCtrlEnableProximity(void)
{
    u8 szDbBusTxData[4] = {0};
    s32 rc;

    DBG("*** %s() ***\n", __func__);

    szDbBusTxData[0] = 0x52;
    szDbBusTxData[1] = 0x00;
    
    if (g_ChipType == CHIP_TYPE_MSG21XX)
    {
        szDbBusTxData[2] = 0x62; 
    }
    else if (g_ChipType == CHIP_TYPE_MSG21XXA || g_ChipType == CHIP_TYPE_MSG22XX)
    {
        szDbBusTxData[2] = 0x4a; 
    }
    else
    {
        DBG("*** Un-recognized chip type = 0x%x ***\n", g_ChipType);
        return -1;
    }
    
    szDbBusTxData[3] = 0xa0;
    
    mutex_lock(&g_Mutex);

    mdelay(I2C_WRITE_COMMAND_DELAY_FOR_FIRMWARE);
    rc = IicWriteData(SLAVE_I2C_ID_DWI2C, &szDbBusTxData[0], 4);
    if (rc > 0)
    {
        g_EnableTpProximity = 1;
    
        DBG("Enable proximity detection success\n");
    }
    else
    {
        DBG("Enable proximity detection failed\n");
    }

    mutex_unlock(&g_Mutex);

    return rc;
}

s32 DrvFwCtrlDisableProximity(void)
{
    u8 szDbBusTxData[4] = {0};
    s32 rc;

    DBG("*** %s() ***\n", __func__);

    szDbBusTxData[0] = 0x52;
    szDbBusTxData[1] = 0x00;

    if (g_ChipType == CHIP_TYPE_MSG21XX)
    {
        szDbBusTxData[2] = 0x62; 
    }
    else if (g_ChipType == CHIP_TYPE_MSG21XXA || g_ChipType == CHIP_TYPE_MSG22XX)
    {
        szDbBusTxData[2] = 0x4a; 
    }
    else
    {
        DBG("*** Un-recognized chip type = 0x%x ***\n", g_ChipType);
        return -1;
    }

    szDbBusTxData[3] = 0xa1;
    
    mutex_lock(&g_Mutex);

    mdelay(I2C_WRITE_COMMAND_DELAY_FOR_FIRMWARE);
    rc = IicWriteData(SLAVE_I2C_ID_DWI2C, &szDbBusTxData[0], 4);
    if (rc > 0)
    {
        g_EnableTpProximity = 0;

        DBG("Disable proximity detection success\n");
    }
    else
    {
        DBG("Disable proximity detection failed\n");
    }

#if defined(CONFIG_TOUCH_DRIVER_RUN_ON_SPRD_PLATFORM) || defined(CONFIG_TOUCH_DRIVER_RUN_ON_QCOM_PLATFORM)
    g_FaceClosingTp = 0;
#endif //CONFIG_TOUCH_DRIVER_RUN_ON_SPRD_PLATFORM || CONFIG_TOUCH_DRIVER_RUN_ON_QCOM_PLATFORM

    mutex_unlock(&g_Mutex);

    return rc;
}

#endif //CONFIG_ENABLE_PROXIMITY_DETECTION

//------------------------------------------------------------------------------//

#ifdef CONFIG_ENABLE_CHARGER_DETECTION

void DrvFwCtrlChargerDetection(u8 nChargerStatus)
{
    u32 i = 0;
    u8 szDbBusTxData[2] = {0};
    s32 rc = 0;

    DBG("*** %s() ***\n", __func__);

    DBG("_gChargerPlugIn = %d, nChargerStatus = %d, g_ForceUpdate = %d\n", _gChargerPlugIn, nChargerStatus, g_ForceUpdate);

    mutex_lock(&g_Mutex);
    
    szDbBusTxData[0] = 0x09;

    if (nChargerStatus) // charger plug in
    {
        if (_gChargerPlugIn == 0 || g_ForceUpdate == 1)
        {
          	szDbBusTxData[1] = 0xA5;
            
            while (i < 5)
            {
                mdelay(I2C_WRITE_COMMAND_DELAY_FOR_FIRMWARE);
                rc = IicWriteData(SLAVE_I2C_ID_DWI2C, &szDbBusTxData[0], 2);
                if (rc > 0)
                {
                    _gChargerPlugIn = 1;

                    DBG("Update status for charger plug in success.\n");
                    break;
                }

                i ++;
            }
            if (i == 5)
            {
                DBG("Update status for charger plug in failed, rc = %d\n", rc);
            }

            g_ForceUpdate = 0; // Clear flag after force update charger status
        }
    }
    else  // charger plug out
    {
        if (_gChargerPlugIn == 1 || g_ForceUpdate == 1)
        {
          	szDbBusTxData[1] = 0x5A;
            
            while (i < 5)
            {
                mdelay(I2C_WRITE_COMMAND_DELAY_FOR_FIRMWARE);
                rc = IicWriteData(SLAVE_I2C_ID_DWI2C, &szDbBusTxData[0], 2);
                if (rc > 0)
                {
                    _gChargerPlugIn = 0;

                    DBG("Update status for charger plug out success.\n");
                    break;
                }
                
                i ++;
            }
            if (i == 5)
            {
                DBG("Update status for charger plug out failed, rc = %d\n", rc);
            }

            g_ForceUpdate = 0; // Clear flag after force update charger status
        }
    }	

    mutex_unlock(&g_Mutex);
}	

#endif //CONFIG_ENABLE_CHARGER_DETECTION

//------------------------------------------------------------------------------//

#endif //CONFIG_ENABLE_TOUCH_DRIVER_FOR_SELF_IC
