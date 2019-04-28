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
 * @file    mstar_drv_self_mp_test.c
 *
 * @brief   This file defines the interface of touch screen
 *
 *
 */

/*=============================================================*/
// INCLUDE FILE
/*=============================================================*/

#include "mstar_drv_self_mp_test.h"
#include "mstar_drv_utility_adaption.h"
#include "mstar_drv_self_fw_control.h"
#include "mstar_drv_platform_porting_layer.h"
#include "mstar_drv_ic_fw_porting_layer.h"

#if defined(CONFIG_ENABLE_TOUCH_DRIVER_FOR_SELF_IC)
#ifdef CONFIG_ENABLE_ITO_MP_TEST

// Modify.
#include "open_test_RIU1_YEJI.h"
#include "open_test_RIU2_YEJI.h"
#include "open_test_RIU3_YEJI.h"

// Modify.
#include "short_test_RIU1_YEJI.h"
#include "short_test_RIU2_YEJI.h"
#include "short_test_RIU4_YEJI.h"



/*=============================================================*/
// PREPROCESSOR CONSTANT DEFINITION
/*=============================================================*/

// Modify.
#define TP_OF_YEJI    (1) //(2)

/*=============================================================*/
// EXTERN VARIABLE DECLARATION
/*=============================================================*/

extern u32 SLAVE_I2C_ID_DBBUS;
extern u32 SLAVE_I2C_ID_DWI2C;

extern u8 g_ChipType;

extern struct i2c_client *g_I2cClient;
extern struct mutex g_Mutex;

/*=============================================================*/
// LOCAL VARIABLE DEFINITION
/*=============================================================*/

static u32 _gIsInMpTest = 0;
static u32 _gTestRetryCount = CTP_MP_TEST_RETRY_COUNT;
static ItoTestMode_e _gItoTestMode = 0;

static s32 _gCtpMpTestStatus = ITO_TEST_UNDER_TESTING;
static u8 _gTestFailChannel[MAX_CHANNEL_NUM] = {0};
static u32 _gTestFailChannelCount = 0;

static struct work_struct _gCtpItoTestWork;
static struct workqueue_struct *_gCtpMpTestWorkQueue = NULL;

static s16 _gRawData1[MAX_CHANNEL_NUM] = {0};
static s16 _gRawData2[MAX_CHANNEL_NUM] = {0};
static s16 _gRawData3[MAX_CHANNEL_NUM] = {0};
static s16 _gRawData4[MAX_CHANNEL_NUM] = {0};
static s8 _gDataFlag1[MAX_CHANNEL_NUM] = {0};
static s8 _gDataFlag2[MAX_CHANNEL_NUM] = {0};
static s8 _gDataFlag3[MAX_CHANNEL_NUM] = {0};
static s8 _gDataFlag4[MAX_CHANNEL_NUM] = {0};

static u8 _gItoTestKeyNum = 0;
static u8 _gItoTestDummyNum = 0;
static u8 _gItoTestTriangleNum = 0;
static u8 _gIsEnable2R = 0;


static u8 _gLTP = 1;	

// _gOpen1~_gOpen3 are for MSG21XXA
static u16 *_gOpen1 = NULL;
static u16 *_gOpen1B = NULL;
static u16 *_gOpen2 = NULL;
static u16 *_gOpen2B = NULL;
static u16 *_gOpen3 = NULL;

// _gShort_1~_gShort_4 are for MSG21XXA
static u16 *_gShort_1 = NULL;
static u16 *_gShort_2 = NULL;
static u16 *_gShort_3 = NULL;

// _gShort_1_GPO~_gShort_4_GPO are for MSG21XXA
static u16 *_gShort_1_GPO = NULL;
static u16 *_gShort_2_GPO = NULL;
static u16 *_gShort_3_GPO = NULL;

#ifdef CONFIG_ENABLE_MP_TEST_ITEM_FOR_2R_TRIANGLE
static u16 *_gShort_4 = NULL;
static u16 *_gShort_4_GPO = NULL;
#endif //CONFIG_ENABLE_MP_TEST_ITEM_FOR_2R_TRIANGLE


static u8 _gIsOldFirmwareVersion = 0;

// _gOpenRIU1~_gOpenRIU3 are for MSG22XX
static u32 *_gOpenRIU1 = NULL;
static u32 *_gOpenRIU2 = NULL;
static u32 *_gOpenRIU3 = NULL;

// _gShort_RIU1~_gShort_RIU4 are for MSG22XX
static u32 *_gShort_RIU1 = NULL;
static u32 *_gShort_RIU2 = NULL;
//static u32 *_gShort_RIU3 = NULL;

// _gOpenSubFrameNum1~_gShortSubFrameNum4 are for MSG22XX
static u8 _gOpenSubFrameNum1 = 0;
static u8 _gOpenSubFrameNum2 = 0;
static u8 _gOpenSubFrameNum3 = 0;
static u8 _gShortSubFrameNum1 = 0;
static u8 _gShortSubFrameNum2 = 0;
//static u8 _gShortSubFrameNum3 = 0;

#ifdef CONFIG_ENABLE_MP_TEST_ITEM_FOR_2R_TRIANGLE
static u32 *_gShort_RIU4 = NULL;
static u8 _gShortSubFrameNum4 = 0;
#endif //CONFIG_ENABLE_MP_TEST_ITEM_FOR_2R_TRIANGLE


static u8 *_gMAP1 = NULL;
static u8 *_gMAP2 = NULL;
static u8 *_gMAP3 = NULL;
static u8 *_gMAP40_1 = NULL;
static u8 *_gMAP40_2 = NULL;
static u8 *_gMAP41_1 = NULL;
static u8 *_gMAP41_2 = NULL;

static u8 *_gSHORT_MAP1 = NULL;
static u8 *_gSHORT_MAP2 = NULL;
//static u8 *_gSHORT_MAP3 = NULL;

#ifdef CONFIG_ENABLE_MP_TEST_ITEM_FOR_2R_TRIANGLE
static u8 *_gMAP40_3 = NULL;
static u8 *_gMAP40_4 = NULL;
static u8 *_gMAP41_3 = NULL;
static u8 *_gMAP41_4 = NULL;

static u8 *_gSHORT_MAP4 = NULL;
#endif //CONFIG_ENABLE_MP_TEST_ITEM_FOR_2R_TRIANGLE


ItoTestResult_e _gItoTestResult = ITO_TEST_OK;

u8 error_flag=1;
 
/*=============================================================*/
// EXTERN FUNCTION DECLARATION
/*=============================================================*/


/*=============================================================*/
// LOCAL FUNCTION DEFINITION
/*=============================================================*/

static u16 _DrvMpTestItoTestGetTpType(void)
{
    u16 nMajor = 0, nMinor = 0;

    DBG("*** %s() ***\n", __func__);
        
    if (g_ChipType == CHIP_TYPE_MSG21XXA)
    {
        u8 szDbBusTxData[3] = {0};
        u8 szDbBusRxData[4] = {0};

        szDbBusTxData[0] = 0x53;
        szDbBusTxData[1] = 0x00;
        szDbBusTxData[2] = 0x2A;

        IicWriteData(SLAVE_I2C_ID_DWI2C, &szDbBusTxData[0], 3);
        IicReadData(SLAVE_I2C_ID_DWI2C, &szDbBusRxData[0], 4);

        nMajor = (szDbBusRxData[1]<<8) + szDbBusRxData[0];
        nMinor = (szDbBusRxData[3]<<8) + szDbBusRxData[2];
    }
    else if (g_ChipType == CHIP_TYPE_MSG22XX)
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


        RegSet16BitValue(0x1600, 0xBFF4); // Set start address for customer firmware version on main block
    
        // Enable burst mode
//        RegSet16BitValue(0x160C, (RegGet16BitValue(0x160C) | 0x01));

    
        RegSetLByteValue(0x160E, 0x01); 

        nRegData1 = RegGet16BitValue(0x1604);
        nRegData2 = RegGet16BitValue(0x1606);

        nMajor = (((nRegData1 >> 8) & 0xFF) << 8) + (nRegData1 & 0xFF);
        nMinor = (((nRegData2 >> 8) & 0xFF) << 8) + (nRegData2 & 0xFF);

        // Clear burst mode
//        RegSet16BitValue(0x160C, RegGet16BitValue(0x160C) & (~0x01));      

        RegSet16BitValue(0x1600, 0x0000); 

        // Clear RIU password
        RegSet16BitValue(0x161A, 0x0000); 

        DbBusIICNotUseBus();
        DbBusNotStopMCU();
        DbBusExitSerialDebugMode();

        DrvPlatformLyrTouchDeviceResetHw();
        mdelay(100);
    }

    DBG("*** major = %d ***\n", nMajor);
    DBG("*** minor = %d ***\n", nMinor);
    
    return nMajor;
}

static u16 _DrvMpTestItoTestChooseTpType(void)
{
    u16 nTpType = 0;
    u32 i = 0;
    
    DBG("*** %s() ***\n", __func__);

    if (g_ChipType == CHIP_TYPE_MSG21XXA)   
    {
        // _gOpen1~_gOpen3 are for MSG21XXA
        _gOpen1 = NULL;
        _gOpen1B = NULL;
        _gOpen2 = NULL;
        _gOpen2B = NULL;
        _gOpen3 = NULL;

        // _gShort_1~_gShort_4 are for MSG21XXA
        _gShort_1 = NULL;
        _gShort_2 = NULL;
        _gShort_3 = NULL;

        _gShort_1_GPO = NULL;
        _gShort_2_GPO = NULL;
        _gShort_3_GPO = NULL;

#ifdef CONFIG_ENABLE_MP_TEST_ITEM_FOR_2R_TRIANGLE
        _gShort_4 = NULL;
        _gShort_4_GPO = NULL;
#endif //CONFIG_ENABLE_MP_TEST_ITEM_FOR_2R_TRIANGLE
    }
    else if (g_ChipType == CHIP_TYPE_MSG22XX)   
    {
        // _gOpenRIU1~_gOpenRIU3 are for MSG22XX
        _gOpenRIU1 = NULL;
        _gOpenRIU2 = NULL;
        _gOpenRIU3 = NULL;

        // _gShort_RIU1~_gShort_RIU4 are for MSG22XX
        _gShort_RIU1 = NULL;
        _gShort_RIU2 = NULL;

        _gOpenSubFrameNum1 = 0;
        _gOpenSubFrameNum2 = 0;
        _gOpenSubFrameNum3 = 0;
        _gShortSubFrameNum1 = 0;
        _gShortSubFrameNum2 = 0;

#ifdef CONFIG_ENABLE_MP_TEST_ITEM_FOR_2R_TRIANGLE
        _gShort_RIU4 = NULL;
        _gShortSubFrameNum4 = 0;
#endif //CONFIG_ENABLE_MP_TEST_ITEM_FOR_2R_TRIANGLE
    }

    _gMAP1 = NULL;
    _gMAP2 = NULL;
    _gMAP3 = NULL;
    _gMAP40_1 = NULL;
    _gMAP40_2 = NULL;
    _gMAP41_1 = NULL;
    _gMAP41_2 = NULL;
    
    _gSHORT_MAP1 = NULL;
    _gSHORT_MAP2 = NULL;

#ifdef CONFIG_ENABLE_MP_TEST_ITEM_FOR_2R_TRIANGLE
    _gMAP40_3 = NULL;
    _gMAP40_4 = NULL;
    _gMAP41_3 = NULL;
    _gMAP41_4 = NULL;

    _gSHORT_MAP4 = NULL;
#endif //CONFIG_ENABLE_MP_TEST_ITEM_FOR_2R_TRIANGLE
    
    _gItoTestKeyNum = 0;
    _gItoTestDummyNum = 0;
    _gItoTestTriangleNum = 0;
    _gIsEnable2R = 0;

    for (i = 0; i < 10; i ++)
    {
        nTpType = _DrvMpTestItoTestGetTpType();
        DBG("nTpType = %d, i = %d\n", nTpType, i);

        if (TP_OF_YEJI == nTpType) // Modify.
        {
            break;
        }
        else if (i < 5)
        {
            mdelay(100);  
        }
        else
        {
            DrvPlatformLyrTouchDeviceResetHw();
        }
    }
    
    if (TP_OF_YEJI== nTpType) // Modify. 
    {
        
        if (g_ChipType == CHIP_TYPE_MSG22XX)   
        {
            _gOpenRIU1 = open_1_YEJI;
            _gOpenRIU2 = open_2_YEJI;
            _gOpenRIU3 = open_3_YEJI;

            _gShort_RIU1 = short_1_YEJI;
            _gShort_RIU2 = short_2_YEJI;

            _gOpenSubFrameNum1 = NUM_OPEN_1_SENSOR_YEJI;
            _gOpenSubFrameNum2 = NUM_OPEN_2_SENSOR_YEJI;
            _gOpenSubFrameNum3 = NUM_OPEN_3_SENSOR_YEJI;
            _gShortSubFrameNum1 = NUM_SHORT_1_SENSOR_YEJI;
            _gShortSubFrameNum2 = NUM_SHORT_2_SENSOR_YEJI;

#ifdef CONFIG_ENABLE_MP_TEST_ITEM_FOR_2R_TRIANGLE
            _gShort_RIU4 = short_4_YEJI;
            _gShortSubFrameNum4 = NUM_SHORT_4_SENSOR_YEJI;
#endif //CONFIG_ENABLE_MP_TEST_ITEM_FOR_2R_TRIANGLE

            _gMAP1 = MAP1_YEJI;
            _gMAP2 = MAP2_YEJI;
            _gMAP3 = MAP3_YEJI;
            _gMAP40_1 = MAP40_1_YEJI;
            _gMAP40_2 = MAP40_2_YEJI;
            _gMAP41_1 = MAP41_1_YEJI;
            _gMAP41_2 = MAP41_2_YEJI;

            _gSHORT_MAP1 = SHORT_MAP1_YEJI;
            _gSHORT_MAP2 = SHORT_MAP2_YEJI;
     //       _gSHORT_MAP3 = SHORT_MAP3_YEJI;

#ifdef CONFIG_ENABLE_MP_TEST_ITEM_FOR_2R_TRIANGLE       
            _gMAP40_3 = MAP40_3_YEJI;
            _gMAP40_4 = MAP40_4_YEJI;
            _gMAP41_3 = MAP41_3_YEJI;
            _gMAP41_4 = MAP41_4_YEJI;

            _gSHORT_MAP4 = SHORT_MAP4_YEJI;
#endif //CONFIG_ENABLE_MP_TEST_ITEM_FOR_2R_TRIANGLE
 
            _gItoTestKeyNum = NUM_KEY_YEJI;
            _gItoTestDummyNum = NUM_DUMMY_YEJI;
            _gItoTestTriangleNum = NUM_SENSOR_YEJI;
            _gIsEnable2R = ENABLE_2R_YEJI;
        }
    }
    else
    {
        nTpType = 0;
    }
    
    return nTpType;
}

static void _DrvMpTestItoTestAnaSwReset(void)
{
    DBG("*** %s() ***\n", __func__);

    RegSet16BitValue(0x1100, 0xFFFF); // reset ANA - analog

    if (g_ChipType == CHIP_TYPE_MSG22XX)
    {
        RegSet16BitValueOn(0x130C, BIT1); // reset Filter - digital 
    }
    
    RegSet16BitValue(0x1100, 0x0000);
    mdelay(15);
}

//------------------------------------------------------------------------------//


static u16 _DrvMpTestItoTestGetNum(void)
{
    u32 i;
    u16 nSensorNum = 0;
    u16 nRegVal1, nRegVal2;
 
    DBG("*** %s() ***\n", __func__);

    nRegVal1 = RegGet16BitValue(0x114A); //bank:ana, addr:h0025  
    DBG("nRegValue1 = %d\n", nRegVal1);
    
    if ((nRegVal1 & BIT1) == BIT1)
    {
        nRegVal1 = RegGet16BitValue(0x120A); //bank:ana2, addr:h0005  			
        nRegVal1 = nRegVal1 & 0x0F;
    	
        nRegVal2 = RegGet16BitValue(0x1216); //bank:ana2, addr:h000b    		
        nRegVal2 = ((nRegVal2 >> 1) & 0x0F) + 1;
    	
        nSensorNum = nRegVal1 * nRegVal2;
    }
    else
    {
        for (i = 0; i < 4; i ++)
        {
            nSensorNum += (RegGet16BitValue(0x120A)>>(4*i))&0x0F; //bank:ana2, addr:h0005  
        }
    }
    DBG("nSensorNum = %d\n", nSensorNum);

    return nSensorNum;        
}

static void _DrvMpTestItoTestDisableFilterNoiseDetect(void)
{
    u16 nRegValue;

    DBG("*** %s() ***\n", __func__);
     
    // Disable DIG/ANA drop
    nRegValue = RegGet16BitValue(0x1302); 
      
    RegSet16BitValue(0x1302, nRegValue & (~(BIT2 | BIT1 | BIT0)));      
}

static void _DrvMpTestItoTestPolling(void)
{
    u16 nRegInt = 0x0000;
    u16 nRegVal;

    DBG("*** %s() ***\n", __func__);

    RegSet16BitValue(0x130C, BIT15); //bank:fir, addr:h0006         
    RegSet16BitValue(0x1214, (RegGet16BitValue(0x1214) | BIT0)); //bank:ana2, addr:h000a        
            
    DBG("polling start\n");

    do
    {
        nRegInt = RegGet16BitValue(0x3D18); //bank:intr_ctrl, addr:h000c
    } while(( nRegInt & FIQ_E_FRAME_READY_MASK ) == 0x0000);

    DBG("polling end\n");
    
    nRegVal = RegGet16BitValue(0x3D18); 
    RegSet16BitValue(0x3D18, nRegVal & (~FIQ_E_FRAME_READY_MASK));      
}

static void _DrvMpTestItoOpenTestSetV(u8 nEnable, u8 nPrs)	
{
    u16 nRegVal;        
    
    DBG("*** %s() nEnable = %d, nPrs = %d ***\n", __func__, nEnable, nPrs);
    
    nRegVal = RegGet16BitValue(0x1208); //bank:ana2, addr:h0004
    nRegVal = nRegVal & 0xF1; 							
    
    if (nPrs == 0)
    {
        RegSet16BitValue(0x1208, nRegVal|0x0C); 		
    }
    else if (nPrs == 1)
    {
        RegSet16BitValue(0x1208, nRegVal|0x0E); 		     	
    }
    else
    {
        RegSet16BitValue(0x1208, nRegVal|0x02); 			
    }    
    
    if (nEnable)
    {
        nRegVal = RegGet16BitValue(0x1106); //bank:ana, addr:h0003  
        RegSet16BitValue(0x1106, nRegVal|0x03);   	
    }
    else
    {
        nRegVal = RegGet16BitValue(0x1106);    
        nRegVal = nRegVal & 0xFC;					
        RegSet16BitValue(0x1106, nRegVal);         
    }
}

static u16 _DrvMpTestItoTestMsg21xxaGetDataOut(s16 *pRawData)
{
    u32 i;
    u16 szRawData[MAX_CHANNEL_NUM] = {0};
    u16 nSensorNum;
    u16 nRegInt;
    u8  szDbBusTxData[8] = {0};
    u8  szDbBusRxData[MAX_CHANNEL_NUM*2] = {0};

    DBG("*** %s() ***\n", __func__);

    nSensorNum = _DrvMpTestItoTestGetNum();
    
    if ((nSensorNum*2) > (MAX_CHANNEL_NUM*2))
    {
        DBG("Danger. nSensorNum = %d\n", nSensorNum);
        return nSensorNum;
    }

    nRegInt = RegGet16BitValue((0x3d<<8) | (REG_INTR_FIQ_MASK<<1)); 
    RegSet16BitValue((0x3d<<8) | (REG_INTR_FIQ_MASK<<1), (nRegInt & (u16)(~FIQ_E_FRAME_READY_MASK))); 
    
    _DrvMpTestItoTestPolling();
    
    szDbBusTxData[0] = 0x10;
    szDbBusTxData[1] = 0x13; //bank:fir, addr:h0020 
    szDbBusTxData[2] = 0x40;
    IicWriteData(SLAVE_I2C_ID_DBBUS, &szDbBusTxData[0], 3);
    mdelay(20);
    IicReadData(SLAVE_I2C_ID_DBBUS, &szDbBusRxData[0], (nSensorNum * 2));
    mdelay(100);
    
    for (i = 0; i < nSensorNum * 2; i ++)
    {
        DBG("szDbBusRxData[%d] = %d\n", i, szDbBusRxData[i]); // add for debug
    }
 
    nRegInt = RegGet16BitValue((0x3d<<8) | (REG_INTR_FIQ_MASK<<1)); 
    RegSet16BitValue((0x3d<<8) | (REG_INTR_FIQ_MASK<<1), (nRegInt | (u16)FIQ_E_FRAME_READY_MASK)); 

    for (i = 0; i < nSensorNum; i ++)
    {
        szRawData[i] = (szDbBusRxData[2 * i + 1] << 8 ) | (szDbBusRxData[2 * i]);
        pRawData[i] = (s16)szRawData[i];
    }
    
    return nSensorNum;
}

static void _DrvMpTestItoTestMsg21xxaSendDataIn(u8 nStep)
{
    u32	i;
    u16 *pType = NULL;        
    u8 	szDbBusTxData[512] = {0};

    DBG("*** %s() nStep = %d ***\n", __func__, nStep);

    if (nStep == 1) //39-1
    {
        pType = &_gShort_1[0];      	
    }
    else if (nStep == 2) //39-2
    {
        pType = &_gShort_2[0];      	
    }
    else if (nStep == 3) //39-3
    {
        pType = &_gShort_3[0];        
    }
#ifdef CONFIG_ENABLE_MP_TEST_ITEM_FOR_2R_TRIANGLE
    else if (nStep == 0) //39-4 (2R)
    {
        pType = &_gShort_4[0];
    }
#endif //CONFIG_ENABLE_MP_TEST_ITEM_FOR_2R_TRIANGLE
    else if (nStep == 4)
    {
        pType = &_gOpen1[0];        
    }
    else if (nStep == 5)
    {
        pType = &_gOpen2[0];      	
    }
    else if (nStep == 6)
    {
        pType = &_gOpen3[0];      	
    }
    else if (nStep == 9)
    {
        pType = &_gOpen1B[0];        
    }
    else if (nStep == 10)
    {
        pType = &_gOpen2B[0];      	
    } 
     
    szDbBusTxData[0] = 0x10;
    szDbBusTxData[1] = 0x11; //bank:ana, addr:h0000
    szDbBusTxData[2] = 0x00;    
    for (i = 0; i <= 0x3E ; i ++)
    {
        szDbBusTxData[3+2*i] = pType[i] & 0xFF;
        szDbBusTxData[4+2*i] = (pType[i] >> 8) & 0xFF;    	
    }
    IicWriteData(SLAVE_I2C_ID_DBBUS, &szDbBusTxData[0], 3+0x3F*2);

    szDbBusTxData[2] = 0x7A * 2; //bank:ana, addr:h007a
    for (i = 0x7A; i <= 0x7D ; i ++)
    {
        szDbBusTxData[3+2*(i-0x7A)] = 0;
        szDbBusTxData[4+2*(i-0x7A)] = 0;    	    	
    }
    IicWriteData(SLAVE_I2C_ID_DBBUS, &szDbBusTxData[0], 3+8);
    
    szDbBusTxData[0] = 0x10;
    szDbBusTxData[1] = 0x12; //bank:ana2, addr:h0005
      
    szDbBusTxData[2] = 0x05 * 2; 
    szDbBusTxData[3] = pType[128+0x05] & 0xFF;
    szDbBusTxData[4] = (pType[128+0x05] >> 8) & 0xFF;
    IicWriteData(SLAVE_I2C_ID_DBBUS, &szDbBusTxData[0], 5);
    
    szDbBusTxData[2] = 0x0B * 2; //bank:ana2, addr:h000b
    szDbBusTxData[3] = pType[128+0x0B] & 0xFF;
    szDbBusTxData[4] = (pType[128+0x0B] >> 8) & 0xFF;
    IicWriteData(SLAVE_I2C_ID_DBBUS, &szDbBusTxData[0], 5);
    
    szDbBusTxData[2] = 0x12 * 2; //bank:ana2, addr:h0012
    szDbBusTxData[3] = pType[128+0x12] & 0xFF;
    szDbBusTxData[4] = (pType[128+0x12] >> 8) & 0xFF;
    IicWriteData(SLAVE_I2C_ID_DBBUS, &szDbBusTxData[0], 5);
    
    szDbBusTxData[2] = 0x15 * 2; //bank:ana2, addr:h0015
    szDbBusTxData[3] = pType[128+0x15] & 0xFF;
    szDbBusTxData[4] = (pType[128+0x15] >> 8) & 0xFF;
    IicWriteData(SLAVE_I2C_ID_DBBUS, &szDbBusTxData[0], 5);

    if (_gItoTestMode == ITO_TEST_MODE_OPEN_TEST)
    {    	
//#if 1 //for AC mod
        szDbBusTxData[1] = 0x13;
        szDbBusTxData[2] = 0x12 * 2;
        szDbBusTxData[3] = 0x30;
        szDbBusTxData[4] = 0x30;
        IicWriteData(SLAVE_I2C_ID_DBBUS, szDbBusTxData, 5);        
        
        szDbBusTxData[2] = 0x14 * 2;
        szDbBusTxData[3] = 0X30;
        szDbBusTxData[4] = 0X30;
        IicWriteData(SLAVE_I2C_ID_DBBUS, szDbBusTxData, 5);     
        
        szDbBusTxData[1] = 0x12;
        for (i = 0x0D; i <= 0x10; i ++) //for AC noise(++)
        {
            szDbBusTxData[2] = i * 2;
            szDbBusTxData[3] = pType[128+i] & 0xFF;
            szDbBusTxData[4] = (pType[128+i] >> 8) & 0xFF;
            IicWriteData(SLAVE_I2C_ID_DBBUS, szDbBusTxData, 5);  
        }

        for (i = 0x16; i <= 0x18; i ++) //for AC noise
        {
	          szDbBusTxData[2] = i * 2;
	          szDbBusTxData[3] = pType[128+i] & 0xFF;
	          szDbBusTxData[4] = (pType[128+i] >> 8) & 0xFF;
	          IicWriteData(SLAVE_I2C_ID_DBBUS, szDbBusTxData, 5);  
        }
//#endif
    }
}

static void _DrvMpTestItoOpenTestMsg21xxaSetC(u8 nCSubStep)
{
    u32 i;
    u8 szDbBusTxData[MAX_CHANNEL_NUM+3];
    u8 nHighLevelCSub = 0;
    u8 nCSubNew;
     
    DBG("*** %s() nCSubStep = %d ***\n", __func__, nCSubStep);

    szDbBusTxData[0] = 0x10;
    szDbBusTxData[1] = 0x11; //bank:ana, addr:h0042       
    szDbBusTxData[2] = 0x84;        
    
    for (i = 0; i < MAX_CHANNEL_NUM; i ++)
    {
        nCSubNew = nCSubStep;        
        nHighLevelCSub = 0;   
        
        if (nCSubNew > 0x1F)
        {
            nCSubNew = nCSubNew - 0x14;
            nHighLevelCSub = 1;
        }
           
        szDbBusTxData[3+i] = nCSubNew & 0x1F;        
        if (nHighLevelCSub == 1)
        {
            szDbBusTxData[3+i] |= BIT5;
        }
    }
    IicWriteData(SLAVE_I2C_ID_DBBUS, &szDbBusTxData[0], MAX_CHANNEL_NUM+3);

    szDbBusTxData[2] = 0xB4; //bank:ana, addr:h005a        
    IicWriteData(SLAVE_I2C_ID_DBBUS, &szDbBusTxData[0], MAX_CHANNEL_NUM+3);
}

static void _DrvMpTestItoOpenTestMsg21xxaFirst(u8 nItemId, s16 *pRawData, s8 *pDataFlag)		
{
    u32 i, j;
    s16 szTmpRawData[MAX_CHANNEL_NUM] = {0};
    u16	nRegVal;
    u8  nLoop;
    u8  nSensorNum1 = 0, nSensorNum2 = 0, nTotalSensor = 0;
    u8 	*pMapping = NULL;
    
    DBG("*** %s() nItemId = %d ***\n", __func__, nItemId);
	
    // Stop cpu
    RegSet16BitValue(0x0FE6, 0x0001); //bank:mheg5, addr:h0073

    RegSet16BitValue(0x1E24, 0x0500); //bank:chip, addr:h0012
    RegSet16BitValue(0x1E2A, 0x0000); //bank:chip, addr:h0015
    RegSet16BitValue(0x1EE6, 0x6E00); //bank:chip, addr:h0073
    RegSet16BitValue(0x1EE8, 0x0071); //bank:chip, addr:h0074
	    
    if (nItemId == 40)    			
    {
        pMapping = &_gMAP1[0];
        if (_gIsEnable2R)
        {
            nTotalSensor = _gItoTestTriangleNum/2; 
        }
        else
        {
            nTotalSensor = _gItoTestTriangleNum/2 + _gItoTestKeyNum + _gItoTestDummyNum;
        }
    }
    else if (nItemId == 41)    		
    {
        pMapping = &_gMAP2[0];
        if (_gIsEnable2R)
        {
            nTotalSensor = _gItoTestTriangleNum/2; 
        }
        else
        {
            nTotalSensor = _gItoTestTriangleNum/2 + _gItoTestKeyNum + _gItoTestDummyNum;
        }
    }
    else if (nItemId == 42)    		
    {
        pMapping = &_gMAP3[0];      
        nTotalSensor = _gItoTestTriangleNum + _gItoTestKeyNum + _gItoTestDummyNum; 
    }
        	    
    nLoop = 1;
    if (nItemId != 42)
    {
        if (nTotalSensor > 11)
        {
            nLoop = 2;
        }
    }	
    
    DBG("nLoop = %d\n", nLoop);
	
    for (i = 0; i < nLoop; i ++)
    {
        if (i == 0)
        {
            _DrvMpTestItoTestMsg21xxaSendDataIn(nItemId - 36);
        }
        else
        { 
            if (nItemId == 40)
            { 
                _DrvMpTestItoTestMsg21xxaSendDataIn(9);
            }
            else
            { 		
                _DrvMpTestItoTestMsg21xxaSendDataIn(10);
            }
        }
	
        _DrvMpTestItoTestDisableFilterNoiseDetect();
	
        _DrvMpTestItoOpenTestSetV(1, 0);    
        nRegVal = RegGet16BitValue(0x110E); //bank:ana, addr:h0007   			
        RegSet16BitValue(0x110E, nRegVal | BIT11);				 		
	
        if (_gLTP == 1)
        {
            _DrvMpTestItoOpenTestMsg21xxaSetC(32);
        }	    	
        else
        {	    	
	    	_DrvMpTestItoOpenTestMsg21xxaSetC(0);
        }
        
        _DrvMpTestItoTestAnaSwReset();
		
        if (i == 0)	 
        {      
            nSensorNum1 = _DrvMpTestItoTestMsg21xxaGetDataOut(szTmpRawData);
            DBG("nSensorNum1 = %d\n", nSensorNum1);
        }
        else	
        {      
            nSensorNum2 = _DrvMpTestItoTestMsg21xxaGetDataOut(&szTmpRawData[nSensorNum1]);
            DBG("nSensorNum1 = %d, nSensorNum2 = %d\n", nSensorNum1, nSensorNum2);
        }
    }
    
    for (j = 0; j < nTotalSensor; j ++)
    {
        if (_gLTP == 1)
        {
            pRawData[pMapping[j]] = szTmpRawData[j] + 4096;
            pDataFlag[pMapping[j]] = 1;
        }
        else
        {
            pRawData[pMapping[j]] = szTmpRawData[j];	
            pDataFlag[pMapping[j]] = 1;
        }
    }	
}

static void _DrvMpTestItoShortTestChangeGPOSetting(u8 nItemId)
{
    u8 szDbBusTxData[3+GPO_SETTING_SIZE*2] = {0};
    u16 szGPOSetting[3] = {0};
    u32 i;
    
    DBG("*** %s() nItemId = %d ***\n", __func__, nItemId);
    
    if (nItemId == 1) // 39-1
    {
        szGPOSetting[0] = _gShort_1_GPO[0];		
        szGPOSetting[1] = _gShort_1_GPO[1];		
        szGPOSetting[2] = _gShort_1_GPO[2];		
        szGPOSetting[2] |= (1 << (int)(PIN_GUARD_RING % 16));
    }
    else if (nItemId == 2) // 39-2
    {
        szGPOSetting[0] = _gShort_2_GPO[0];		
        szGPOSetting[1] = _gShort_2_GPO[1];		
        szGPOSetting[2] = _gShort_2_GPO[2];		
        szGPOSetting[2] |= (1 << (int)(PIN_GUARD_RING % 16));
    }
    else if (nItemId == 3) // 39-3
    {
        szGPOSetting[0] = _gShort_3_GPO[0];		
        szGPOSetting[1] = _gShort_3_GPO[1];		
        szGPOSetting[2] = _gShort_3_GPO[2];		
        szGPOSetting[2] |= (1 << (int)(PIN_GUARD_RING % 16));
    }
#ifdef CONFIG_ENABLE_MP_TEST_ITEM_FOR_2R_TRIANGLE
    else if (nItemId == 0) // 39-4 (2R)
    {
        szGPOSetting[0] = _gShort_4_GPO[0];		
        szGPOSetting[1] = _gShort_4_GPO[1];		
        szGPOSetting[2] = _gShort_4_GPO[2];		
        szGPOSetting[2] |= (1 << (int)(PIN_GUARD_RING % 16));
    }
#endif //CONFIG_ENABLE_MP_TEST_ITEM_FOR_2R_TRIANGLE
    else
    {
        DBG("Invalid item id for changing GPIO setting of short test.\n");

        return;
    }

    szDbBusTxData[0] = 0x10;
    szDbBusTxData[1] = 0x12;
    szDbBusTxData[2] = 0x48;

    for (i = 0; i < GPO_SETTING_SIZE; i ++)
    {
        szDbBusTxData[3+2*i] = szGPOSetting[i] & 0xFF;
        szDbBusTxData[4+2*i] = (szGPOSetting[i] >> 8) & 0xFF;    	
    }

    IicWriteData(SLAVE_I2C_ID_DBBUS, &szDbBusTxData[0], 3+GPO_SETTING_SIZE*2);    
}

static void _DrvMpTestItoShortTestChangeRmodeSetting(u8 nMode)
{
    u8 szDbBusTxData[6] = {0};

    DBG("*** %s() nMode = %d ***\n", __func__, nMode);

    // AFE R-mode enable(Bit-12)
    RegSetLByteValue(0x1103, 0x10);

    // drv_mux_OV (Bit-8 1:enable)
    RegSetLByteValue(0x1107, 0x55);
    
    if (nMode == 1) // P_CODE: 0V
    {
        RegSet16BitValue(0x110E, 0x073A);
    }
    else if (nMode == 0) // N_CODE: 2.4V
    {
        RegSet16BitValue(0x110E, 0x073B);
    }

    // SW2 rising & SW3 rising return to 0
    RegSetLByteValue(0x1227, 0x01);
    // turn off the chopping
    RegSetLByteValue(0x1208, 0x0C);
    // idle driver ov
    RegSetLByteValue(0x1241, 0xC0);
	  
	  // AFE ov
    szDbBusTxData[0] = 0x10;
    szDbBusTxData[1] = 0x12;
    szDbBusTxData[2] = 0x44;
    szDbBusTxData[3] = 0xFF;
    szDbBusTxData[4] = 0xFF;
    szDbBusTxData[5] = 0xFF;

    IicWriteData(SLAVE_I2C_ID_DBBUS, &szDbBusTxData[0], 6);        
}	

static void _DrvMpTestItoShortTestMsg21xxaFirst(u8 nItemId, s16 *pRawData, s8 *pDataFlag)		
{
    u32 i;
    s16 szTmpRawData[MAX_CHANNEL_NUM] = {0};
    s16 szTmpRawData2[MAX_CHANNEL_NUM] = {0};
    u8  nSensorNum, nSensorNum2, nNumOfSensorMapping1, nNumOfSensorMapping2, nSensorCount = 0;
    u8 	*pMapping = NULL;
    
    DBG("*** %s() nItemId = %d ***\n", __func__, nItemId);

    // Stop cpu
    RegSet16BitValue(0x0FE6, 0x0001); //bank:mheg5, addr:h0073
    // chip top op0
    RegSet16BitValue(0x1E24, 0x0500); //bank:chip, addr:h0012
    RegSet16BitValue(0x1E2A, 0x0000); //bank:chip, addr:h0015
    RegSet16BitValue(0x1EE6, 0x6E00); //bank:chip, addr:h0073
    RegSet16BitValue(0x1EE8, 0x0071); //bank:chip, addr:h0074
	    
    if ((_gItoTestTriangleNum + _gItoTestKeyNum + _gItoTestDummyNum) % 2 != 0)
    {
        nNumOfSensorMapping1 = (_gItoTestTriangleNum + _gItoTestKeyNum + _gItoTestDummyNum) / 2 + 1;
        nNumOfSensorMapping2 = nNumOfSensorMapping1;
    }
    else
    {
        nNumOfSensorMapping1 = (_gItoTestTriangleNum + _gItoTestKeyNum + _gItoTestDummyNum) / 2;
        nNumOfSensorMapping2 = nNumOfSensorMapping1;
        if (nNumOfSensorMapping2 % 2 != 0)
        {	
            nNumOfSensorMapping2 ++;
        }
    }        

    if (nItemId == 1) // 39-1    			    		
    {
        pMapping = &_gSHORT_MAP1[0];
        nSensorCount = nNumOfSensorMapping1; 
    }
    else if (nItemId == 2) // 39-2   		
    {
        pMapping = &_gSHORT_MAP2[0];      
        nSensorCount = nNumOfSensorMapping2; 
    }

#ifdef CONFIG_ENABLE_MP_TEST_ITEM_FOR_2R_TRIANGLE
    else if (nItemId == 0) // 39-4 (2R)    			
    {
        pMapping = &_gSHORT_MAP4[0];
        nSensorCount = _gItoTestTriangleNum/2; 
    }
#endif //CONFIG_ENABLE_MP_TEST_ITEM_FOR_2R_TRIANGLE

    DBG("nSensorCount = %d\n", nSensorCount);
        	    
    _DrvMpTestItoTestMsg21xxaSendDataIn(nItemId);
    
    _DrvMpTestItoTestDisableFilterNoiseDetect();

    _DrvMpTestItoShortTestChangeRmodeSetting(1);
    _DrvMpTestItoShortTestChangeGPOSetting(nItemId);
    _DrvMpTestItoTestAnaSwReset();
    nSensorNum = _DrvMpTestItoTestMsg21xxaGetDataOut(szTmpRawData);
    DBG("nSensorNum = %d\n", nSensorNum);

    _DrvMpTestItoShortTestChangeRmodeSetting(0);
    _DrvMpTestItoShortTestChangeGPOSetting(nItemId);
    _DrvMpTestItoTestAnaSwReset();
    nSensorNum2 = _DrvMpTestItoTestMsg21xxaGetDataOut(szTmpRawData2);
    DBG("nSensorNum2 = %d\n", nSensorNum2);
    
    for (i = 0; i < nSensorCount; i ++)
    {
        pRawData[pMapping[i]] = szTmpRawData[i] - szTmpRawData2[i];	
        pDataFlag[pMapping[i]] = 1;
    }	
}


static u16 _DrvMpTestItoTestMsg22xxGetDataOut(s16 *pRawData, u16 nSubFrameNum)
{
    u32 i;
    u16 szRawData[MAX_CHANNEL_NUM*2] = {0};
    u16 nRegInt = 0x0000;
    u16 nSize = nSubFrameNum * 4;  // 1SF 4AFE
    u8  szDbBusTxData[8] = {0};
    u8  szDbBusRxData[MAX_CHANNEL_NUM*4] = {0};

    DBG("*** %s() ***\n", __func__);

    RegSet16BitValueOff(0x120A, BIT1); //one-shot mode
    RegSet16BitValueOff(0x3D08, BIT8); //FIQ_E_FRAME_READY_MASK
    //RegSet16BitValue(0x130C, BIT15); //MCU read done
    RegSet16BitValueOn(0x120A, BIT0); //trigger one-shot 

    DBG("polling start\n");

    //Polling frame-ready interrupt status
    i=0;
    do 
    {
    	 i++;
        nRegInt = RegGet16BitValue(0x3D18); //bank:intr_ctrl, addr:h000c

	  DBG("*** nRegInt=  %x ***\n",nRegInt);

	  DBG("*** try count i=  %d ***\n",i);

	  if(i>20)  //sway 20151219
		{
			error_flag=0;
	  		return 0;
	  	}
	  
    } while(( nRegInt & FIQ_E_FRAME_READY_MASK ) == 0x0000);

    DBG("polling end\n");

    RegSet16BitValueOff(0x3D18, BIT8); //Clear frame-ready interrupt status

    //ReadRegBurst start   
    szDbBusTxData[0] = 0x10;
    szDbBusTxData[1] = 0x15; //bank:fout, addr:h0000 
    szDbBusTxData[2] = 0x00;
    IicWriteData(SLAVE_I2C_ID_DBBUS, &szDbBusTxData[0], 3);
    mdelay(20);
    IicReadData(SLAVE_I2C_ID_DBBUS, &szDbBusRxData[0], (nSubFrameNum * 4 * 2));
    mdelay(100);

    for (i = 0; i < (nSubFrameNum * 4 * 2); i ++)
    {
        DBG("szDbBusRxData[%d] = %d\n", i, szDbBusRxData[i]); // add for debug
    }
    //ReadRegBurst stop
 
    RegSet16BitValueOn(0x3D08, BIT8); //FIQ_E_FRAME_READY_MASK

    for (i = 0; i < nSize; i ++)
    {
        szRawData[i] = (szDbBusRxData[2 * i + 1] << 8 ) | (szDbBusRxData[2 * i]);
        pRawData[i] = (s16)szRawData[i];
    }
    
    return nSize;
}

static void _DrvMpTestItoTestMsg22xxSendDataIn(u8 nStep, u16 nRiuWriteLength)
{
    u32	i;
    u32 *pType = NULL;

    DBG("*** %s() nStep = %d, nRiuWriteLength = %d ***\n", __func__, nStep, nRiuWriteLength);

    if (nStep == 1) //39-1
    {
        pType = &_gShort_RIU1[0];      	
    }
    else if (nStep == 2) //39-2
    {
        pType = &_gShort_RIU2[0];      	
    }

#ifdef CONFIG_ENABLE_MP_TEST_ITEM_FOR_2R_TRIANGLE
    else if (nStep == 0) //39-4 (2R)
    {
        pType = &_gShort_RIU4[0];
    }
#endif //CONFIG_ENABLE_MP_TEST_ITEM_FOR_2R_TRIANGLE
    else if (nStep == 4)
    {
        pType = &_gOpenRIU1[0];        
    }
    else if (nStep == 5)
    {
        pType = &_gOpenRIU2[0];      	
    }
    else if (nStep == 6)
    {
        pType = &_gOpenRIU3[0];      	
    }

    RegSet16BitValueOn(0x1192, BIT4); // force on enable sensor mux and csub sel sram clock
    RegSet16BitValueOff(0x1192, BIT5); // mem clk sel 
    RegSet16BitValueOff(0x1100, BIT3); // tgen soft rst
    RegSet16BitValue(0x1180, RIU_BASE_ADDR); // sensor mux sram read/write base address
    RegSet16BitValue(0x1182, nRiuWriteLength); // sensor mux sram write length
    RegSet16BitValueOn(0x1188, BIT0); // reg_mem0_w_start

    for (i = RIU_BASE_ADDR; i < (RIU_BASE_ADDR + nRiuWriteLength); i ++)
    {
        RegSet16BitValue(0x118A, (u16)(pType[i]));
        RegSet16BitValue(0x118C, (u16)(pType[i] >> 16));
    }
}

static void _DrvMpTestItoTestMsg22xxSetC(u8 nCSubStep)
{
    u32 i;
    u16 nRegVal;
    u32 nCSubNew; 
     
    DBG("*** %s() nCSubStep = %d ***\n", __func__, nCSubStep);
    
    nCSubNew = (nCSubStep > CSUB_REF_MAX) ? CSUB_REF_MAX : nCSubStep; // 6 bits
    nCSubNew = (nCSubNew | (nCSubNew << 8) | (nCSubNew << 16) | (nCSubNew << 24));

    nRegVal = RegGet16BitValue(0x11C8); // csub sel overwrite enable, will referance value of 11C0

    if (nRegVal == 0x000F)
    {
        RegSet16BitValue(0x11C0, nCSubNew);         // prs 0
        RegSet16BitValue(0x11C2, (nCSubNew >> 16)); // prs 0
        RegSet16BitValue(0x11C4, nCSubNew);         // prs 1
        RegSet16BitValue(0x11C6, (nCSubNew >> 16)); // prs 1
    }
    else
    {
        RegSet16BitValueOn(0x1192, BIT4);   // force on enable sensor mux  and csub sel sram clock
        RegSet16BitValueOff(0x1192, BIT5);   // mem clk sel 
        RegSet16BitValueOff(0x1100, BIT3);   // tgen soft rst
        RegSet16BitValue(0x1184, 0);          // nAddr
        RegSet16BitValue(0x1186, MAX_CHANNEL_NUM);         // nLen
        RegSet16BitValueOn(0x1188, BIT2);   // reg_mem0_w_start

        for (i = 0; i < MAX_CHANNEL_NUM; i ++)
        {
            RegSet16BitValue(0x118E, nCSubNew);
            RegSet16BitValue(0x1190, (nCSubNew >> 16));
        }
    }
}

static void _DrvMpTestItoTestMsg22xxRegisterReset(void)
{
    DBG("*** %s() ***\n", __func__);

    RegSet16BitValueOn(0x1102, (BIT3 | BIT4 | BIT5 | BIT6 | BIT7));
    RegSet16BitValueOff(0x1130, (BIT0 | BIT1 | BIT2 | BIT3 | BIT8));

    RegSet16BitValueOn(0x1312, BIT15);
    RegSet16BitValueOff(0x1312, BIT13);

    RegMask16BitValue(0x1250, 0x007F, ((5 << 0) & 0x007F), ADDRESS_MODE_8BIT); 

    RegMask16BitValue(0x1250, 0x7F00, ((1 << 8) & 0x7F00), ADDRESS_MODE_8BIT); 

    RegSet16BitValueOff(0x1250, 0x8080);

    RegSet16BitValueOff(0x1312, (BIT12 | BIT14));

    RegSet16BitValueOn(0x1300, BIT15);
    RegSet16BitValueOff(0x1300, (BIT10 | BIT11 | BIT12 | BIT13 | BIT14));

    RegSet16BitValueOn(0x1130, BIT9);

    RegSet16BitValueOn(0x1318, (BIT12 | BIT13));

    RegSet16BitValue(0x121A, ((8 - 1) & 0x01FF));       // sampling number group A
    RegSet16BitValue(0x121C, ((8 - 1) & 0x01FF));       // sampling number group B
    
    RegMask16BitValue(0x131A, 0x3FFF, 0x2000, ADDRESS_MODE_8BIT); 
    
    RegMask16BitValue(0x131C, (BIT8 | BIT9 | BIT10), (2 << 8), ADDRESS_MODE_8BIT); 

    RegSet16BitValueOff(0x1174, 0x0F00);

    RegSet16BitValueOff(0x1240, 0xFFFF);       // mutual cap mode selection for total 24 subframes, 0: normal sense, 1: mutual cap sense
    RegSet16BitValueOff(0x1242, 0x00FF);       // mutual cap mode selection for total 24 subframes, 0: normal sense, 1: mutual cap sense

    RegSet16BitValueOn(0x1212, 0xFFFF);       // timing group A/B selection for total 24 subframes, 0: group A, 1: group B
    RegSet16BitValueOn(0x1214, 0x00FF);       // timing group A/B selection for total 24 subframes, 0: group A, 1: group B

    RegSet16BitValueOn(0x121E, 0xFFFF);   //sample number group A/B selection for total 24 subframes, 0: group A, 1: group B       
    RegSet16BitValueOn(0x1220, 0x00FF);   //sample number group A/B selection for total 24 subframes, 0: group A, 1: group B    

    RegSet16BitValueOff(0x120E, 0xFFFF);  // noise sense mode selection for total 24 subframes
    RegSet16BitValueOff(0x1210, 0x00FF);  // noise sense mode selection for total 24 subframes

    RegSet16BitValue(0x128C, 0x0F);  // ADC afe gain correction bypass
    RegSet16BitValueOff(0x1104, BIT12); 
}

static void _DrvMpTestItoTestMsg22xxRegisterResetPatch(void)
{
    DBG("*** %s() *** _gIsOldFirmwareVersion = %d\n", __func__, _gIsOldFirmwareVersion);
    
    if (_gIsOldFirmwareVersion == 0) // Only need to patch register reset for MSG22XX firmware older than V01.005.02;
    {
        return;		
    }

    RegMask16BitValue(0x1101, 0xFFFF, 0x501E, ADDRESS_MODE_16BIT); 
    RegMask16BitValue(0x1102, 0x7FFF, 0x06FF, ADDRESS_MODE_16BIT); 
    RegMask16BitValue(0x1104, 0x0FFF, 0x0772, ADDRESS_MODE_16BIT); 
    RegMask16BitValue(0x1105, 0x0FFF, 0x0770, ADDRESS_MODE_16BIT); 
    RegMask16BitValue(0x1106, 0x00FF, 0x0000, ADDRESS_MODE_16BIT); 
    RegMask16BitValue(0x1107, 0x0003, 0x0000, ADDRESS_MODE_16BIT); 
    RegMask16BitValue(0x1108, 0x0073, 0x0000, ADDRESS_MODE_16BIT); 
    RegMask16BitValue(0x1109, 0xFFFF, 0x0000, ADDRESS_MODE_16BIT); 
    RegMask16BitValue(0x110A, 0x7FFF, 0x1087, ADDRESS_MODE_16BIT); 
    RegMask16BitValue(0x110E, 0xFFFF, 0x0000, ADDRESS_MODE_16BIT); 
    RegMask16BitValue(0x110F, 0xFFFF, 0x0000, ADDRESS_MODE_16BIT); 
    RegMask16BitValue(0x1117, 0xFF0F, 0x0000, ADDRESS_MODE_16BIT); 
    RegMask16BitValue(0x1118, 0xFFFF, 0x0200, ADDRESS_MODE_16BIT); 
    RegMask16BitValue(0x1119, 0x00FF, 0x000E, ADDRESS_MODE_16BIT); 
    RegMask16BitValue(0x111E, 0xFFFF, 0x0000, ADDRESS_MODE_16BIT); 
    RegMask16BitValue(0x111F, 0x00FF, 0x0000, ADDRESS_MODE_16BIT); 
    RegMask16BitValue(0x1133, 0x000F, 0x0000, ADDRESS_MODE_16BIT); 
    RegMask16BitValue(0x113A, 0x0F37, 0x0000, ADDRESS_MODE_16BIT); 
    RegMask16BitValue(0x113B, 0x0077, 0x0077, ADDRESS_MODE_16BIT); 
    RegMask16BitValue(0x113C, 0xFF00, 0xA000, ADDRESS_MODE_16BIT); 
    RegMask16BitValue(0x113D, 0x0077, 0x0000, ADDRESS_MODE_16BIT); 
    RegMask16BitValue(0x113E, 0x00FF, 0x0000, ADDRESS_MODE_16BIT); 

    RegMask16BitValue(0x1204, 0x0006, 0x0000, ADDRESS_MODE_16BIT); 
    RegMask16BitValue(0x1205, 0x00FF, 0x0000, ADDRESS_MODE_16BIT); 
    RegMask16BitValue(0x1207, 0xFFFF, 0x0000, ADDRESS_MODE_16BIT); 
    RegMask16BitValue(0x1208, 0x00FF, 0x0000, ADDRESS_MODE_16BIT); 
    RegMask16BitValue(0x1209, 0xFFFF, 0x0000, ADDRESS_MODE_16BIT); 
    RegMask16BitValue(0x120A, 0x00FF, 0x0000, ADDRESS_MODE_16BIT); 
    RegMask16BitValue(0x120B, 0x003F, 0x002E, ADDRESS_MODE_16BIT); 
    RegMask16BitValue(0x120D, 0x001F, 0x0005, ADDRESS_MODE_16BIT); 
    RegMask16BitValue(0x120E, 0x001F, 0x0005, ADDRESS_MODE_16BIT); 
    RegMask16BitValue(0x120F, 0xFFFF, 0x0000, ADDRESS_MODE_16BIT); 
    RegMask16BitValue(0x1210, 0x00FF, 0x0000, ADDRESS_MODE_16BIT); 
    RegMask16BitValue(0x1211, 0x0FFF, 0x0000, ADDRESS_MODE_16BIT); 
    RegMask16BitValue(0x1212, 0x1F87, 0x0000, ADDRESS_MODE_16BIT); 
    RegMask16BitValue(0x1213, 0x0F7F, 0x0014, ADDRESS_MODE_16BIT); 
    RegMask16BitValue(0x1214, 0xFF9F, 0x090A, ADDRESS_MODE_16BIT); 
    RegMask16BitValue(0x1215, 0x0F7F, 0x0F0C, ADDRESS_MODE_16BIT); 
    RegMask16BitValue(0x1216, 0x0FFF, 0x0700, ADDRESS_MODE_16BIT); 
    RegMask16BitValue(0x1217, 0xFF1F, 0x5C0A, ADDRESS_MODE_16BIT); 
    RegMask16BitValue(0x1218, 0x1F7F, 0x0A14, ADDRESS_MODE_16BIT); 
    RegMask16BitValue(0x1219, 0xFFFF, 0x218C, ADDRESS_MODE_16BIT); 
    RegMask16BitValue(0x121E, 0x1F1F, 0x0712, ADDRESS_MODE_16BIT); 
    RegMask16BitValue(0x121F, 0x3F3F, 0x0000, ADDRESS_MODE_16BIT); 
    RegMask16BitValue(0x1220, 0xFFFF, 0x0000, ADDRESS_MODE_16BIT); 
    RegMask16BitValue(0x1221, 0x00FF, 0x0000, ADDRESS_MODE_16BIT); 
    RegMask16BitValue(0x1223, 0x3F3F, 0x0002, ADDRESS_MODE_16BIT); 
    RegMask16BitValue(0x1224, 0x003F, 0x0000, ADDRESS_MODE_16BIT); 
    RegMask16BitValue(0x1228, 0xFFFF, 0x8183, ADDRESS_MODE_16BIT); 
    RegMask16BitValue(0x122D, 0x0001, 0x0001, ADDRESS_MODE_16BIT); 
    RegMask16BitValue(0x1250, 0xFFFF, 0x0000, ADDRESS_MODE_16BIT); 
    RegMask16BitValue(0x1251, 0x00FF, 0x0000, ADDRESS_MODE_16BIT); 
    RegMask16BitValue(0x1270, 0x0003, 0x0003, ADDRESS_MODE_16BIT); 

    RegSet16BitValueByAddressMode(0x115C, 0x0000, ADDRESS_MODE_16BIT); //sensor ov enable
    RegSet16BitValueByAddressMode(0x115D, 0x0000, ADDRESS_MODE_16BIT); //sensor ov enable
    RegSet16BitValueByAddressMode(0x115E, 0x0000, ADDRESS_MODE_16BIT); //sensor and gr ov enable

    RegSet16BitValueByAddressMode(0x124E, 0x000F, ADDRESS_MODE_16BIT); //bypass mode
}

static void _DrvMpTestItoTestMsg22xxGetChargeDumpTime(u16 nMode, u16 *pChargeTime, u16 *pDumpTime)
{
    u16 nChargeTime = 0, nDumpTime = 0;
    u16 nMinChargeTime = 0xFFFF, nMinDumpTime = 0xFFFF, nMaxChargeTime = 0x0000, nMaxDumpTime = 0x0000;
    
    DBG("*** %s() ***\n", __func__);

    nChargeTime = RegGet16BitValue(0x1226);
    nDumpTime = RegGet16BitValue(0x122A);

    if (nMinChargeTime > nChargeTime)
    {
        nMinChargeTime = nChargeTime;
    }

    if (nMaxChargeTime < nChargeTime)
    {
        nMaxChargeTime = nChargeTime;
    }

    if (nMinDumpTime > nDumpTime)
    {
        nMinDumpTime = nDumpTime;
    }

    if (nMaxDumpTime < nDumpTime)
    {
        nMaxDumpTime = nDumpTime;
    }

    DBG("nChargeTime = %d, nDumpTime = %d\n", nChargeTime, nDumpTime);
    
    if (nMode == 1)
    {
        *pChargeTime = nMaxChargeTime;
        *pDumpTime = nMaxDumpTime;
    }
    else
    {
        *pChargeTime = nMinChargeTime;
        *pDumpTime = nMinDumpTime;
    }
}

static void _DrvMpTestItoOpenTestMsg22xxFirst(u8 nItemId, s16 *pRawData, s8 *pDataFlag)		
{
    u32 i;
    s16 szTmpRawData[MAX_CHANNEL_NUM*2] = {0};
    u16 nSubFrameNum = 0;
    u16 nChargeTime, nDumpTime;
    u8 	*pMapping = NULL;
    
    DBG("*** %s() nItemId = %d ***\n", __func__, nItemId);
	
    if (nItemId == 40)
    {
        // Stop cpu
        RegSet16BitValue(0x0FE6, 0x0001); //bank:mheg5, addr:h0073

        _DrvMpTestItoTestMsg22xxRegisterResetPatch();
        _DrvMpTestItoTestMsg22xxRegisterReset();
    }

    switch (nItemId)
    {
        case 40:
            pMapping = &_gMAP1[0];
            nSubFrameNum = _gOpenSubFrameNum1;
            break;
        case 41:
            pMapping = &_gMAP2[0];
            nSubFrameNum = _gOpenSubFrameNum2;
            break;
        case 42:
            pMapping = &_gMAP3[0];
            nSubFrameNum = _gOpenSubFrameNum3;
            break;
    }

    if (nSubFrameNum > 24) // MAX_CHANNEL_NUM/2
    {
        nSubFrameNum = 24;
    }

    _DrvMpTestItoTestMsg22xxSendDataIn(nItemId - 36, nSubFrameNum * 6);
    RegSet16BitValue(0x1216, (nSubFrameNum - 1) << 1); // subframe numbers, 0:1subframe, 1:2subframe  

    if (nItemId == 40)
    {
        if (0)
        {
            RegSet16BitValue(0x1110, 0x0060); //2.4V -> 1.2V    
        }
        else
        {
            RegSet16BitValue(0x1110, 0x0020); //3.0V -> 0.6V
        }
    
        RegSet16BitValue(0x11C8, 0x000F); //csub sel overwrite enable, will referance value of 11C0
        RegSet16BitValue(0x1174, 0x0F06); // 1 : sel idel driver for sensor pad that connected to AFE
        RegSet16BitValue(0x1208, 0x0006); //PRS1
        RegSet16BitValue(0x1240, 0xFFFF); //mutual cap mode selection for total 24 subframes, 0: normal sense, 1: mutual cap sense
        RegSet16BitValue(0x1242, 0x00FF); //mutual cap mode selection for total 24 subframes, 0: normal sense, 1: mutual cap sense

        _DrvMpTestItoTestMsg22xxGetChargeDumpTime(1, &nChargeTime, &nDumpTime);

        RegSet16BitValue(0x1226, nChargeTime);
        RegSet16BitValue(0x122A, nDumpTime);

        _DrvMpTestItoTestMsg22xxSetC(CSUB_REF);
        _DrvMpTestItoTestAnaSwReset();
    }

    _DrvMpTestItoTestMsg22xxGetDataOut(szTmpRawData, nSubFrameNum);

	DBG("error_flag = %d\n",  error_flag);
    	if(error_flag==0)
   	{		
		return;
    	} 

    for (i = 0; i < nSubFrameNum; i ++)
    {
//        DBG("szTmpRawData[%d * 4] >> 3 = %d\n", i, szTmpRawData[i * 4] >> 3); // add for debug
//        DBG("pMapping[%d] = %d\n", i, pMapping[i]); // add for debug

        pRawData[pMapping[i]] = (szTmpRawData[i * 4] >> 3);  // Filter to ADC 
        pDataFlag[pMapping[i]] = 1;
    }
}

static void _DrvMpTestItoShortTestMsg22xxFirst(u8 nItemId, s16 *pRawData, s8 *pDataFlag)		
{
    u32 i, j;
    s16 szIIR1[MAX_SUBFRAME_NUM*MAX_AFE_NUM] = {32767};
    s16 szIIR2[MAX_SUBFRAME_NUM*MAX_AFE_NUM] = {32767};
    s16 szIIRTmp[MAX_SUBFRAME_NUM*MAX_AFE_NUM] = {32767};
    u16 nSensorNum = 0, nSubFrameNum = 0;
    u8 	*pMapping = NULL;
    
    DBG("*** %s() nItemId = %d ***\n", __func__, nItemId);

    if ((nItemId == 0 && _gIsEnable2R == 1) || (nItemId == 1 && _gIsEnable2R == 0))
    {
        // Stop cpu
        RegSet16BitValue(0x0FE6, 0x0001); //bank:mheg5, addr:h0073
        
        _DrvMpTestItoTestMsg22xxRegisterResetPatch();
        _DrvMpTestItoTestMsg22xxRegisterReset();
    }

    switch(nItemId)
    {
#ifdef CONFIG_ENABLE_MP_TEST_ITEM_FOR_2R_TRIANGLE
        case 0: // 39-4 (2R)
            pMapping = &_gSHORT_MAP4[0];
            nSensorNum = _gShortSubFrameNum4;
            break;
#endif //CONFIG_ENABLE_MP_TEST_ITEM_FOR_2R_TRIANGLE
        case 1: // 39-1
            pMapping = &_gSHORT_MAP1[0];
            nSensorNum = _gShortSubFrameNum1;
            break;
        case 2: // 39-2
            pMapping = &_gSHORT_MAP2[0];
            nSensorNum = _gShortSubFrameNum2;
            break;
       
    }
    
    if (nSensorNum > 24) // MAX_CHANNEL_NUM/2
    {
        nSubFrameNum = 24;
    }
    else
    {
        nSubFrameNum = nSensorNum;
    }

    _DrvMpTestItoTestMsg22xxSendDataIn(nItemId, nSubFrameNum * 6);
    RegSet16BitValue(0x1216, (nSubFrameNum - 1) << 1); // subframe numbers, 0:1subframe, 1:2subframe

    if ((nItemId == 0 && _gIsEnable2R == 1) || (nItemId == 1 && _gIsEnable2R == 0))
    {
        RegSet16BitValue(0x1110, 0x0030); // [6:4}  011 : 2.1V -> 1.5V
//        RegSet16BitValue(0x1110, 0x0060); // 2.4V -> 1.2V
        RegSet16BitValue(0x11C8, 0x000F); // csub sel overwrite enable, will referance value of 11C0
        RegSet16BitValue(0x1174, 0x0000); // [11:8] 000 : sel active driver for sensor pad that connected to AFE
        RegSet16BitValue(0x1208, 0x0006); // PRS 1
        RegSet16BitValueOn(0x1104, BIT14); // R mode 
        RegSet16BitValue(0x1176, 0x0000); // CFB 10p
        
        _DrvMpTestItoTestMsg22xxSetC(CSUB_REF);

        RegMask16BitValue(0x1213, 0x007F, 0x003B, ADDRESS_MODE_16BIT); // Charge 20ns (group A)
        RegMask16BitValue(0x1218, 0x007F, 0x003B, ADDRESS_MODE_16BIT); // Charge 20ns (group B)
    }    

    RegMask16BitValue(0x1215, 0x007F, 0x000B, ADDRESS_MODE_16BIT); // Dump 4ns (group A)
    RegMask16BitValue(0x1219, 0x007F, 0x000B, ADDRESS_MODE_16BIT); // Dump 4ns (group B)

    _DrvMpTestItoTestAnaSwReset();
    _DrvMpTestItoTestMsg22xxGetDataOut(szIIRTmp, nSubFrameNum);
    	if(error_flag==0)
   	{		
		return;
    	} 
    
    if (nSensorNum > MAX_SUBFRAME_NUM)
    {
        j = 0;

        for (i = 0; i < MAX_SUBFRAME_NUM; i ++)
        {
            szIIR1[j] = (szIIRTmp[i * 4] >> 3);
            j ++;
            
            if ((nSensorNum - MAX_SUBFRAME_NUM) > i)
            {
                szIIR1[j] = (szIIRTmp[i * 4 + 1] >> 3);
                j ++;
            }    
        }
    }
    else
    {
        for (i = 0; i < nSensorNum; i ++)
        {
            szIIR1[i] = (szIIRTmp[i * 4 + 1] >> 3);
        }
    }

    RegMask16BitValue(0x1215, 0x007F, 0x0017, ADDRESS_MODE_16BIT); // Dump 8ns (group A)
    RegMask16BitValue(0x1219, 0x007F, 0x0017, ADDRESS_MODE_16BIT); // Dump 8ns (group B)

    _DrvMpTestItoTestAnaSwReset();
    _DrvMpTestItoTestMsg22xxGetDataOut(szIIRTmp, nSubFrameNum);

    if (nSensorNum > MAX_SUBFRAME_NUM)
    {
        j = 0;

        for (i = 0; i < MAX_SUBFRAME_NUM; i ++)
        {
            szIIR2[j] = (szIIRTmp[i * 4] >> 3);
            j ++;
            
            if ((nSensorNum - MAX_SUBFRAME_NUM) > i)
            {
                szIIR2[j] = (szIIRTmp[i * 4 + 1] >> 3);
                j ++;
            }    
        }
    }
    else
    {
        for (i = 0; i < nSensorNum; i ++)
        {
            szIIR2[i] = (szIIRTmp[i * 4 + 1] >> 3);
        }
    }

    for (i = 0; i < nSensorNum; i ++)
    {
        if ((abs(szIIR1[i]) > 4000) || (abs(szIIR2[i]) > 4000))
        {
            pRawData[pMapping[i]] = 8192;
        }
        else
        {
            pRawData[pMapping[i]] = ((szIIR2[i] - szIIR1[i]) * 4);
        }
        pDataFlag[pMapping[i]] = 1;

//        DBG("szIIR1[%d] = %d, szIIR2[%d] = %d\n", i, szIIR1[i], i, szIIR2[i]); // add for debug
//        DBG("pRawData[%d] = %d\n", pMapping[i], pRawData[pMapping[i]]); // add for debug
//        DBG("pMapping[%d] = %d\n", i, pMapping[i]); // add for debug
    }
}

static u8 _DrvMpTestMsg22xxCheckFirmwareVersion(void) // Only MSG22XX support platform firmware version
{
    u32 i;
    s32 nDiff;
    u16 nRegData1, nRegData2;
    u8 szDbBusRxData[12] = {0};
    u8 szCompareFwVersion[10] = {0x56, 0x30, 0x31, 0x2E, 0x30, 0x30, 0x35, 0x2E, 0x30, 0x32}; //{'V', '0', '1', '.', '0', '0', '5', '.', '0', '2'}
    u8 nIsOldFirmwareVersion = 0;

    DBG("*** %s() ***\n", __func__);

    mutex_lock(&g_Mutex);

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
        
        DBG("szDbBusRxData[%d] = 0x%x , %c \n", i*4+0, szDbBusRxData[i*4+0], szDbBusRxData[i*4+0]); // add for debug
        DBG("szDbBusRxData[%d] = 0x%x , %c \n", i*4+1, szDbBusRxData[i*4+1], szDbBusRxData[i*4+1]); // add for debug
        
        szDbBusRxData[i*4+2] = (nRegData2 & 0xFF);
        szDbBusRxData[i*4+3] = ((nRegData2 >> 8 ) & 0xFF);

        DBG("szDbBusRxData[%d] = 0x%x , %c \n", i*4+2, szDbBusRxData[i*4+2], szDbBusRxData[i*4+2]); // add for debug
        DBG("szDbBusRxData[%d] = 0x%x , %c \n", i*4+3, szDbBusRxData[i*4+3], szDbBusRxData[i*4+3]); // add for debug
    }

    // Clear burst mode
    RegSet16BitValue(0x160C, RegGet16BitValue(0x160C) & (~0x01));      

    RegSet16BitValue(0x1600, 0x0000); 

    // Clear RIU password
    RegSet16BitValue(0x161A, 0x0000); 
    
    for (i = 0; i < 10; i ++)
    {
        nDiff = szDbBusRxData[2+i] - szCompareFwVersion[i];
        
        DBG("i = %d, nDiff = %d\n", i, nDiff);
        
        if (nDiff < 0)
        {
            nIsOldFirmwareVersion = 1;
            break;
        }
        else if (nDiff > 0)
        {
            nIsOldFirmwareVersion = 0;
            break;
        }
    }

    DbBusIICNotUseBus();
    DbBusNotStopMCU();
    DbBusExitSerialDebugMode();

    DrvPlatformLyrTouchDeviceResetHw();

    mutex_unlock(&g_Mutex);

    return nIsOldFirmwareVersion;
}


//------------------------------------------------------------------------------//

static ItoTestResult_e _DrvMpTestItoOpenTestSecond(u8 nItemId)
{
    ItoTestResult_e nRetVal = ITO_TEST_OK;
    u32 i;
    s32 nTmpRawDataJg1 = 0;
    s32 nTmpRawDataJg2 = 0;
    s32 nTmpJgAvgThMax1 = 0;
    s32 nTmpJgAvgThMin1 = 0;
    s32 nTmpJgAvgThMax2 = 0;
    s32 nTmpJgAvgThMin2 = 0;

    DBG("*** %s() nItemId = %d ***\n", __func__, nItemId);

    if (nItemId == 40)    			
    {
        for (i = 0; i < (_gItoTestTriangleNum/2)-2; i ++)
        {
            nTmpRawDataJg1 += _gRawData1[_gMAP40_1[i]];
        }

        for (i = 0; i < 2; i ++)
        {
            nTmpRawDataJg2 += _gRawData1[_gMAP40_2[i]];
        }
    }
    else if (nItemId == 41)    		
    {
        for (i = 0; i < (_gItoTestTriangleNum/2)-2; i ++)
        {
            nTmpRawDataJg1 += _gRawData2[_gMAP41_1[i]];
        }
		
        for (i = 0; i < 2; i ++)
        {
            nTmpRawDataJg2 += _gRawData2[_gMAP41_2[i]];
        }
    }

    nTmpJgAvgThMax1 = (nTmpRawDataJg1 / ((_gItoTestTriangleNum/2)-2)) * ( 100 + OPEN_TEST_NON_BORDER_AREA_THRESHOLD) / 100;
    nTmpJgAvgThMin1 = (nTmpRawDataJg1 / ((_gItoTestTriangleNum/2)-2)) * ( 100 - OPEN_TEST_NON_BORDER_AREA_THRESHOLD) / 100;
    nTmpJgAvgThMax2 = (nTmpRawDataJg2 / 2) * ( 100 + OPEN_TEST_BORDER_AREA_THRESHOLD) / 100;
    nTmpJgAvgThMin2 = (nTmpRawDataJg2 / 2 ) * ( 100 - OPEN_TEST_BORDER_AREA_THRESHOLD) / 100;
	
    DBG("nItemId = %d, nTmpRawDataJg1 = %d, nTmpJgAvgThMax1 = %d, nTmpJgAvgThMin1 = %d, nTmpRawDataJg2 = %d, nTmpJgAvgThMax2 = %d, nTmpJgAvgThMin2 = %d\n", nItemId, nTmpRawDataJg1, nTmpJgAvgThMax1, nTmpJgAvgThMin1, nTmpRawDataJg2, nTmpJgAvgThMax2, nTmpJgAvgThMin2);

    if (nItemId == 40) 
    {
        for (i = 0; i < (_gItoTestTriangleNum/2)-2; i ++)
        {
            if (_gRawData1[_gMAP40_1[i]] > nTmpJgAvgThMax1 || _gRawData1[_gMAP40_1[i]] < nTmpJgAvgThMin1)
            { 
                _gTestFailChannel[_gTestFailChannelCount] = _gMAP40_1[i];
                _gTestFailChannelCount ++; 
                nRetVal = ITO_TEST_FAIL;
            }
        }
		
        for (i = 0; i < 2; i ++)
        {
            if (_gRawData1[_gMAP40_2[i]] > nTmpJgAvgThMax2 || _gRawData1[_gMAP40_2[i]] < nTmpJgAvgThMin2)
            { 
                _gTestFailChannel[_gTestFailChannelCount] = _gMAP40_2[i];
                _gTestFailChannelCount ++; 
                nRetVal = ITO_TEST_FAIL;
            }
        } 
    }
    else if (nItemId == 41) 
    {
        for (i = 0; i < (_gItoTestTriangleNum/2)-2; i ++)
        {
            if (_gRawData2[_gMAP41_1[i]] > nTmpJgAvgThMax1 || _gRawData2[_gMAP41_1[i]] < nTmpJgAvgThMin1) 
            { 
                _gTestFailChannel[_gTestFailChannelCount] = _gMAP41_1[i];
                _gTestFailChannelCount ++; 
                nRetVal = ITO_TEST_FAIL;
            }
        }
        
        for (i = 0; i < 2; i ++)
        {
            if (_gRawData2[_gMAP41_2[i]] > nTmpJgAvgThMax2 || _gRawData2[_gMAP41_2[i]] < nTmpJgAvgThMin2) 
            { 
                _gTestFailChannel[_gTestFailChannelCount] = _gMAP41_2[i];
                _gTestFailChannelCount ++; 
                nRetVal = ITO_TEST_FAIL;
            }
        }
    }

    return nRetVal;
}

#ifdef CONFIG_ENABLE_MP_TEST_ITEM_FOR_2R_TRIANGLE
static ItoTestResult_e _DrvMpTestItoOpenTestSecond2r(u8 nItemId)
{
    ItoTestResult_e nRetVal = ITO_TEST_OK;
    u32 i;
    s32 nTmpRawDataJg1 = 0;
    s32 nTmpRawDataJg2 = 0;
    s32 nTmpRawDataJg3 = 0;
    s32 nTmpRawDataJg4 = 0;
    s32 nTmpJgAvgThMax1 = 0;
    s32 nTmpJgAvgThMin1 = 0;
    s32 nTmpJgAvgThMax2 = 0;
    s32 nTmpJgAvgThMin2 = 0;
    s32 nTmpJgAvgThMax3 = 0;
    s32 nTmpJgAvgThMin3 = 0;
    s32 nTmpJgAvgThMax4 = 0;
    s32 nTmpJgAvgThMin4 = 0;

    DBG("*** %s() nItemId = %d ***\n", __func__, nItemId);

    if (nItemId == 40)    			
    {
        for (i = 0; i < (_gItoTestTriangleNum/4)-2; i ++)
        {
            nTmpRawDataJg1 += _gRawData1[_gMAP40_1[i]];  //first region: non-border 
        }
		
        for (i = 0; i < 2; i ++)
        {
            nTmpRawDataJg2 += _gRawData1[_gMAP40_2[i]];  //first region: border
        }

        for (i = 0; i < (_gItoTestTriangleNum/4)-2; i ++)
        {
            nTmpRawDataJg3 += _gRawData1[_gMAP40_3[i]];  //second region: non-border
        }
		
        for (i = 0; i < 2; i ++)
        {
            nTmpRawDataJg4 += _gRawData1[_gMAP40_4[i]];  //second region: border
        }
    }
    else if (nItemId == 41)    		
    {
        for (i = 0; i < (_gItoTestTriangleNum/4)-2; i ++)
        {
            nTmpRawDataJg1 += _gRawData2[_gMAP41_1[i]];  //first region: non-border
        }
		
        for (i = 0; i < 2; i ++)
        {
            nTmpRawDataJg2 += _gRawData2[_gMAP41_2[i]];  //first region: border
        }
		
        for (i = 0; i < (_gItoTestTriangleNum/4)-2; i ++)
        {
            nTmpRawDataJg3 += _gRawData2[_gMAP41_3[i]];  //second region: non-border
        }
		
        for (i = 0; i < 2; i ++)
        {
            nTmpRawDataJg4 += _gRawData2[_gMAP41_4[i]];  //second region: border
        }
    }

    nTmpJgAvgThMax1 = (nTmpRawDataJg1 / ((_gItoTestTriangleNum/4)-2)) * ( 100 + OPEN_TEST_NON_BORDER_AREA_THRESHOLD) / 100;
    nTmpJgAvgThMin1 = (nTmpRawDataJg1 / ((_gItoTestTriangleNum/4)-2)) * ( 100 - OPEN_TEST_NON_BORDER_AREA_THRESHOLD) / 100;
    nTmpJgAvgThMax2 = (nTmpRawDataJg2 / 2) * ( 100 + OPEN_TEST_BORDER_AREA_THRESHOLD) / 100;
    nTmpJgAvgThMin2 = (nTmpRawDataJg2 / 2) * ( 100 - OPEN_TEST_BORDER_AREA_THRESHOLD) / 100;
    nTmpJgAvgThMax3 = (nTmpRawDataJg3 / ((_gItoTestTriangleNum/4)-2)) * ( 100 + OPEN_TEST_NON_BORDER_AREA_THRESHOLD) / 100;
    nTmpJgAvgThMin3 = (nTmpRawDataJg3 / ((_gItoTestTriangleNum/4)-2)) * ( 100 - OPEN_TEST_NON_BORDER_AREA_THRESHOLD) / 100;
    nTmpJgAvgThMax4 = (nTmpRawDataJg4 / 2) * ( 100 + OPEN_TEST_BORDER_AREA_THRESHOLD) / 100;
    nTmpJgAvgThMin4 = (nTmpRawDataJg4 / 2) * ( 100 - OPEN_TEST_BORDER_AREA_THRESHOLD) / 100;
		
    DBG("nItemId = %d, nTmpRawDataJg1 = %d, nTmpJgAvgThMax1 = %d, nTmpJgAvgThMin1 = %d, nTmpRawDataJg2 = %d, nTmpJgAvgThMax2 = %d, nTmpJgAvgThMin2 = %d\n", nItemId, nTmpRawDataJg1, nTmpJgAvgThMax1, nTmpJgAvgThMin1, nTmpRawDataJg2, nTmpJgAvgThMax2, nTmpJgAvgThMin2);
    DBG("nTmpRawDataJg3 = %d, nTmpJgAvgThMax3 = %d, nTmpJgAvgThMin3 = %d, nTmpRawDataJg4 = %d, nTmpJgAvgThMax4 = %d, nTmpJgAvgThMin4 = %d\n", nTmpRawDataJg3, nTmpJgAvgThMax3, nTmpJgAvgThMin3, nTmpRawDataJg4, nTmpJgAvgThMax4, nTmpJgAvgThMin4);

    if (nItemId == 40) 
    {
        for (i = 0; i < (_gItoTestTriangleNum/4)-2; i ++)
        {
            if (_gRawData1[_gMAP40_1[i]] > nTmpJgAvgThMax1 || _gRawData1[_gMAP40_1[i]] < nTmpJgAvgThMin1) 
            {
                _gTestFailChannel[_gTestFailChannelCount] = _gMAP40_1[i];
                _gTestFailChannelCount ++; 
                nRetVal = ITO_TEST_FAIL;
            }
        }
		
        for (i = 0; i < 2; i ++)
        {
            if (_gRawData1[_gMAP40_2[i]] > nTmpJgAvgThMax2 || _gRawData1[_gMAP40_2[i]] < nTmpJgAvgThMin2) 
            {
                _gTestFailChannel[_gTestFailChannelCount] = _gMAP40_2[i];
                _gTestFailChannelCount ++; 
                nRetVal = ITO_TEST_FAIL;
            }
        } 
		
        for (i = 0; i < (_gItoTestTriangleNum/4)-2; i ++)
        {
            if (_gRawData1[_gMAP40_3[i]] > nTmpJgAvgThMax3 || _gRawData1[_gMAP40_3[i]] < nTmpJgAvgThMin3) 
            {
                _gTestFailChannel[_gTestFailChannelCount] = _gMAP40_3[i];
                _gTestFailChannelCount ++; 
                nRetVal = ITO_TEST_FAIL;
            }
        }
		
        for (i = 0; i < 2; i ++)
        {
            if (_gRawData1[_gMAP40_4[i]] > nTmpJgAvgThMax4 || _gRawData1[_gMAP40_4[i]] < nTmpJgAvgThMin4) 
            {
                _gTestFailChannel[_gTestFailChannelCount] = _gMAP40_4[i];
                _gTestFailChannelCount ++; 
                nRetVal = ITO_TEST_FAIL;
            }
        } 
    }
    else if (nItemId == 41) 
    {
        for (i = 0; i < (_gItoTestTriangleNum/4)-2; i ++)
        {
            if (_gRawData2[_gMAP41_1[i]] > nTmpJgAvgThMax1 || _gRawData2[_gMAP41_1[i]] < nTmpJgAvgThMin1) 
            {
                _gTestFailChannel[_gTestFailChannelCount] = _gMAP41_1[i];
                _gTestFailChannelCount ++; 
                nRetVal = ITO_TEST_FAIL;
            }
        }
		
        for (i = 0; i < 2; i ++)
        {
            if (_gRawData2[_gMAP41_2[i]] > nTmpJgAvgThMax2 || _gRawData2[_gMAP41_2[i]] < nTmpJgAvgThMin2) 
            {
                _gTestFailChannel[_gTestFailChannelCount] = _gMAP41_2[i];
                _gTestFailChannelCount ++; 
                nRetVal = ITO_TEST_FAIL;
            }
        }
		
        for (i = 0; i < (_gItoTestTriangleNum/4)-2; i ++)
        {
            if (_gRawData2[_gMAP41_3[i]] > nTmpJgAvgThMax3 || _gRawData2[_gMAP41_3[i]] < nTmpJgAvgThMin3) 
            {
                _gTestFailChannel[_gTestFailChannelCount] = _gMAP41_3[i];
                _gTestFailChannelCount ++; 
                nRetVal = ITO_TEST_FAIL;
            }
        }
		
        for (i = 0; i < 2; i ++)
        {
            if (_gRawData2[_gMAP41_4[i]] > nTmpJgAvgThMax4 || _gRawData2[_gMAP41_4[i]] < nTmpJgAvgThMin4) 
            {
                _gTestFailChannel[_gTestFailChannelCount] = _gMAP41_4[i];
                _gTestFailChannelCount ++; 
                nRetVal = ITO_TEST_FAIL;
            }
        } 
    }

    return nRetVal;
}
#endif //CONFIG_ENABLE_MP_TEST_ITEM_FOR_2R_TRIANGLE

static ItoTestResult_e _DrvMpTestItoShortTestSecond(u8 nItemId)
{
    ItoTestResult_e nRetVal = ITO_TEST_OK;
    u32 i;
    u8 nSensorCount = 0;
    u8 nNumOfSensorMapping1 = 0, nNumOfSensorMapping2 = 0;
	
    DBG("*** %s() nItemId = %d ***\n", __func__, nItemId);

    if (g_ChipType == CHIP_TYPE_MSG21XXA)
    {
        if ((_gItoTestTriangleNum + _gItoTestKeyNum + _gItoTestDummyNum) % 2 != 0)
        {
            nNumOfSensorMapping1 = (_gItoTestTriangleNum + _gItoTestKeyNum + _gItoTestDummyNum) / 2 + 1;
            nNumOfSensorMapping2 = nNumOfSensorMapping1;
        }
        else
        {
            nNumOfSensorMapping1 = (_gItoTestTriangleNum + _gItoTestKeyNum + _gItoTestDummyNum) / 2;
            nNumOfSensorMapping2 = nNumOfSensorMapping1;
            if (nNumOfSensorMapping2 % 2 != 0)
            {	
                nNumOfSensorMapping2 ++;
            }
        }        
    }

    if (nItemId == 1) // 39-1
    {
        if (g_ChipType == CHIP_TYPE_MSG21XXA)
        {
            nSensorCount = nNumOfSensorMapping1;
        }
        else if (g_ChipType == CHIP_TYPE_MSG22XX)
        {
            nSensorCount = _gShortSubFrameNum1;
        }        
        
        for (i = 0; i < nSensorCount; i ++)
        {
            if (_gRawData1[_gSHORT_MAP1[i]] > SHORT_TEST_THRESHOLD)
            {
                _gTestFailChannel[_gTestFailChannelCount] = _gSHORT_MAP1[i];
                _gTestFailChannelCount ++; 
                nRetVal = ITO_TEST_FAIL;
            }
        }
    }
    else if (nItemId == 2) // 39-2
    {
        if (g_ChipType == CHIP_TYPE_MSG21XXA)
        {
            nSensorCount = nNumOfSensorMapping2;
        }
        else if (g_ChipType == CHIP_TYPE_MSG22XX)
        {
            nSensorCount = _gShortSubFrameNum2;
        }        
        
        for (i = 0; i < nSensorCount; i ++)
        {
            if (_gRawData2[_gSHORT_MAP2[i]] > SHORT_TEST_THRESHOLD)
            {
                _gTestFailChannel[_gTestFailChannelCount] = _gSHORT_MAP2[i];
                _gTestFailChannelCount ++; 
                nRetVal = ITO_TEST_FAIL;
            }
        }
    }
    
#ifdef CONFIG_ENABLE_MP_TEST_ITEM_FOR_2R_TRIANGLE
    else if (nItemId == 0) // 39-4 (2R)   
    {
        if (g_ChipType == CHIP_TYPE_MSG21XXA)
        {
            nSensorCount = _gItoTestTriangleNum/2;
        }
        else if (g_ChipType == CHIP_TYPE_MSG22XX)
        {
            nSensorCount = _gShortSubFrameNum4;
        }        
        
        for (i = 0; i < nSensorCount; i ++)
        {
            if (_gRawData4[_gSHORT_MAP4[i]] > SHORT_TEST_THRESHOLD)
            {
                _gTestFailChannel[_gTestFailChannelCount] = _gSHORT_MAP4[i];
                _gTestFailChannelCount ++; 
                nRetVal = ITO_TEST_FAIL;
            }
        }
    }
#endif //CONFIG_ENABLE_MP_TEST_ITEM_FOR_2R_TRIANGLE
 
    DBG("nSensorCount = %d\n", nSensorCount);

    return nRetVal;
}

static ItoTestResult_e _DrvMpTestItoOpenTest(void)
{
    ItoTestResult_e nRetVal1 = ITO_TEST_OK, nRetVal2 = ITO_TEST_OK, nRetVal3 = ITO_TEST_OK;
    u32 i;

    DBG("*** %s() ***\n", __func__);

#ifdef CONFIG_TOUCH_DRIVER_RUN_ON_MTK_PLATFORM
#ifdef CONFIG_ENABLE_DMA_IIC
    DmaReset();
#endif //CONFIG_ENABLE_DMA_IIC
#endif //CONFIG_TOUCH_DRIVER_RUN_ON_MTK_PLATFORM

    DBG("open test start\n");

	error_flag=1;

	DBG("error_flag = %d\n",  error_flag);

    DrvPlatformLyrSetIicDataRate(g_I2cClient, 50000); //50 KHz
  
    DrvPlatformLyrDisableFingerTouchReport();
    DrvPlatformLyrTouchDeviceResetHw();
    
    if (!_DrvMpTestItoTestChooseTpType())
    {
        DBG("Choose Tp Type failed\n");
        nRetVal1 = ITO_TEST_GET_TP_TYPE_ERROR;
        goto ITO_TEST_END;
    }
    
    DbBusEnterSerialDebugMode();
    DbBusStopMCU();
    DbBusIICUseBus();
    DbBusIICReshape();
    mdelay(100);

    // Stop cpu
    RegSet16BitValue(0x0FE6, 0x0001); //bank:mheg5, addr:h0073

    // Stop watchdog
    RegSet16BitValue(0x3C60, 0xAA55); //bank:reg_PIU_MISC_0, addr:h0030

    mdelay(50);
    
    for (i = 0; i < MAX_CHANNEL_NUM; i ++)
    {
        _gRawData1[i] = 0;
        _gRawData2[i] = 0;
        _gRawData3[i] = 0;
        _gDataFlag1[i] = 0;
        _gDataFlag2[i] = 0;
        _gDataFlag3[i] = 0;
        _gTestFailChannel[i] = 0;
    }	
	
    _gTestFailChannelCount = 0; // Reset _gTestFailChannelCount to 0 before test start

    if (g_ChipType == CHIP_TYPE_MSG21XXA)
    {
        _DrvMpTestItoOpenTestMsg21xxaFirst(40, _gRawData1, _gDataFlag1);
    }
    else if (g_ChipType == CHIP_TYPE_MSG22XX)
    {
        _DrvMpTestItoOpenTestMsg22xxFirst(40, _gRawData1, _gDataFlag1);
    }

    if (_gIsEnable2R)
    {
#ifdef CONFIG_ENABLE_MP_TEST_ITEM_FOR_2R_TRIANGLE
        nRetVal2 = _DrvMpTestItoOpenTestSecond2r(40);
#endif //CONFIG_ENABLE_MP_TEST_ITEM_FOR_2R_TRIANGLE
    }
    else
    {
        nRetVal2 = _DrvMpTestItoOpenTestSecond(40);
    }
    
	
	DBG("error_flag = %d\n", error_flag);
		
	if(error_flag==0)
	{		
		nRetVal2 = ITO_TEST_FAIL;
		nRetVal3 = ITO_TEST_FAIL;
			goto ITO_TEST_END;
	}
	
    if (g_ChipType == CHIP_TYPE_MSG21XXA)
    {
        _DrvMpTestItoOpenTestMsg21xxaFirst(41, _gRawData2, _gDataFlag2);
    }
    else if (g_ChipType == CHIP_TYPE_MSG22XX)
    {
        _DrvMpTestItoOpenTestMsg22xxFirst(41, _gRawData2, _gDataFlag2);
    }

    if (_gIsEnable2R)
    {
#ifdef CONFIG_ENABLE_MP_TEST_ITEM_FOR_2R_TRIANGLE
        nRetVal3 = _DrvMpTestItoOpenTestSecond2r(41);
#endif //CONFIG_ENABLE_MP_TEST_ITEM_FOR_2R_TRIANGLE
    }
    else
    {
        nRetVal3 = _DrvMpTestItoOpenTestSecond(41);
    }
    
    ITO_TEST_END:

    DrvPlatformLyrSetIicDataRate(g_I2cClient, 100000); //100 KHz
    
    DrvPlatformLyrTouchDeviceResetHw();
    DrvPlatformLyrEnableFingerTouchReport();

    DBG("open test end\n");

    if ((nRetVal1 != ITO_TEST_OK) && (nRetVal2 == ITO_TEST_OK) && (nRetVal3 == ITO_TEST_OK))
    {
        return ITO_TEST_GET_TP_TYPE_ERROR;		
    }
    else if ((nRetVal1 == ITO_TEST_OK) && ((nRetVal2 != ITO_TEST_OK) || (nRetVal3 != ITO_TEST_OK)))
    {
        return ITO_TEST_FAIL;	
    }
    else
    {
        return ITO_TEST_OK;	
    }
}

static ItoTestResult_e _DrvMpTestItoShortTest(void)
{
    ItoTestResult_e nRetVal1 = ITO_TEST_OK, nRetVal2 = ITO_TEST_OK, nRetVal3 = ITO_TEST_OK, nRetVal4 = ITO_TEST_OK, nRetVal5 = ITO_TEST_OK;
    u32 i = 0;

    DBG("*** %s() ***\n", __func__);

#ifdef CONFIG_TOUCH_DRIVER_RUN_ON_MTK_PLATFORM
#ifdef CONFIG_ENABLE_DMA_IIC
    DmaReset();
#endif //CONFIG_ENABLE_DMA_IIC
#endif //CONFIG_TOUCH_DRIVER_RUN_ON_MTK_PLATFORM

    DBG("short test start\n");

    DrvPlatformLyrSetIicDataRate(g_I2cClient, 50000); //50 KHz
  
    DrvPlatformLyrDisableFingerTouchReport();
    DrvPlatformLyrTouchDeviceResetHw(); 
    
    if (!_DrvMpTestItoTestChooseTpType())
    {
        DBG("Choose Tp Type failed\n");
        nRetVal1 = ITO_TEST_GET_TP_TYPE_ERROR;
        goto ITO_TEST_END;
    }
    
    DbBusEnterSerialDebugMode();
    DbBusStopMCU();
    DbBusIICUseBus();
    DbBusIICReshape();
    mdelay(100);

    // Stop cpu
    RegSet16BitValue(0x0FE6, 0x0001); //bank:mheg5, addr:h0073

    // Stop watchdog
    RegSet16BitValue(0x3C60, 0xAA55); //bank:reg_PIU_MISC_0, addr:h0030

    mdelay(50);
    
    for (i = 0; i < MAX_CHANNEL_NUM; i ++)
    {
        _gRawData1[i] = 0;
        _gRawData2[i] = 0;
        _gRawData4[i] = 0;
        _gDataFlag1[i] = 0;
        _gDataFlag2[i] = 0;
        _gDataFlag4[i] = 0;
        _gTestFailChannel[i] = 0;
    }	
	
    _gTestFailChannelCount = 0; // Reset _gTestFailChannelCount to 0 before test start
	
    if (_gIsEnable2R)
    {
        if (g_ChipType == CHIP_TYPE_MSG21XXA)
        {
            _DrvMpTestItoShortTestMsg21xxaFirst(0, _gRawData4, _gDataFlag4);
        }
        else if (g_ChipType == CHIP_TYPE_MSG22XX)
        {
            _DrvMpTestItoShortTestMsg22xxFirst(0, _gRawData4, _gDataFlag4);
        }
        
        nRetVal2 = _DrvMpTestItoShortTestSecond(0);
    }

    if (g_ChipType == CHIP_TYPE_MSG21XXA)
    {
        _DrvMpTestItoShortTestMsg21xxaFirst(1, _gRawData1, _gDataFlag1);
    }
    else if (g_ChipType == CHIP_TYPE_MSG22XX)
    {
        _DrvMpTestItoShortTestMsg22xxFirst(1, _gRawData1, _gDataFlag1);
    }

    nRetVal3 = _DrvMpTestItoShortTestSecond(1);

    if (g_ChipType == CHIP_TYPE_MSG21XXA)
    {
        _DrvMpTestItoShortTestMsg21xxaFirst(2, _gRawData2, _gDataFlag2);
    }
    else if (g_ChipType == CHIP_TYPE_MSG22XX)
    {
        _DrvMpTestItoShortTestMsg22xxFirst(2, _gRawData2, _gDataFlag2);
    }

    nRetVal4 = _DrvMpTestItoShortTestSecond(2);

    ITO_TEST_END:

    DrvPlatformLyrSetIicDataRate(g_I2cClient, 100000); //100 KHz
    
    DrvPlatformLyrTouchDeviceResetHw();
    DrvPlatformLyrEnableFingerTouchReport();

    DBG("short test end\n");
    
    if ((nRetVal1 != ITO_TEST_OK) && (nRetVal2 == ITO_TEST_OK) && (nRetVal3 == ITO_TEST_OK) && (nRetVal4 == ITO_TEST_OK) && (nRetVal5 == ITO_TEST_OK))
    {
        return ITO_TEST_GET_TP_TYPE_ERROR;		
    }
    else if ((nRetVal1 == ITO_TEST_OK) && ((nRetVal2 != ITO_TEST_OK) || (nRetVal3 != ITO_TEST_OK) || (nRetVal4 != ITO_TEST_OK) || (nRetVal5 != ITO_TEST_OK)))
    {
        return ITO_TEST_FAIL;
    }
    else
    {
        return ITO_TEST_OK;	
    }
}

static void _DrvMpTestItoTestDoWork(struct work_struct *pWork)
{
    ItoTestResult_e nRetVal = ITO_TEST_OK;
    
    DBG("*** %s() _gIsInMpTest = %d, _gTestRetryCount = %d ***\n", __func__, _gIsInMpTest, _gTestRetryCount);

    if (_gItoTestMode == ITO_TEST_MODE_OPEN_TEST)
    {
        nRetVal = _DrvMpTestItoOpenTest();
    }
    else if (_gItoTestMode == ITO_TEST_MODE_SHORT_TEST)
    {
        nRetVal = _DrvMpTestItoShortTest();
    }
    else
    {
        DBG("*** Undefined Mp Test Mode = %d ***\n", _gItoTestMode);
        return;
    }

    DBG("*** ctp mp test result = %d ***\n", nRetVal);
    
    if (nRetVal == ITO_TEST_OK)
    {
        _gCtpMpTestStatus = ITO_TEST_OK;
        _gIsInMpTest = 0;
        DBG("mp test success\n");
    }
    else
    {
        _gTestRetryCount --;
        if (_gTestRetryCount > 0)
        {
            DBG("_gTestRetryCount = %d\n", _gTestRetryCount);
            queue_work(_gCtpMpTestWorkQueue, &_gCtpItoTestWork);
        }
        else
        {
            if (nRetVal == ITO_TEST_FAIL)
            {
                _gCtpMpTestStatus = ITO_TEST_FAIL;
            }
            else if (nRetVal == ITO_TEST_GET_TP_TYPE_ERROR)
            {
                _gCtpMpTestStatus = ITO_TEST_GET_TP_TYPE_ERROR;
            }
            else
            {
                _gCtpMpTestStatus = ITO_TEST_UNDEFINED_ERROR;
            }
              
            _gIsInMpTest = 0;
            DBG("mp test failed\n");
        }
    }
}

/*=============================================================*/
// GLOBAL FUNCTION DEFINITION
/*=============================================================*/

s32 DrvMpTestGetTestResult(void)
{
    DBG("*** %s() ***\n", __func__);
    DBG("_gCtpMpTestStatus = %d\n", _gCtpMpTestStatus);

    return _gCtpMpTestStatus;
}

void DrvMpTestGetTestFailChannel(ItoTestMode_e eItoTestMode, u8 *pFailChannel, u32 *pFailChannelCount)
{
    u32 i;
    
    DBG("*** %s() ***\n", __func__);
    DBG("_gTestFailChannelCount = %d\n", _gTestFailChannelCount);
    
    for (i = 0; i < _gTestFailChannelCount; i ++)
    {
    	  pFailChannel[i] = _gTestFailChannel[i];
    }
    
    *pFailChannelCount = _gTestFailChannelCount;
}

void DrvMpTestGetTestDataLog(ItoTestMode_e eItoTestMode, u8 *pDataLog, u32 *pLength)
{
    u32 i;
    u8 nHighByte, nLowByte;
    
    DBG("*** %s() ***\n", __func__);
    
    if (eItoTestMode == ITO_TEST_MODE_OPEN_TEST)
    {
        for (i = 0; i < MAX_CHANNEL_NUM; i ++)
        {
            nHighByte = (_gRawData1[i] >> 8) & 0xFF;
            nLowByte = (_gRawData1[i]) & 0xFF;
    	  
            if (_gDataFlag1[i] == 1)
            {
                pDataLog[i*4] = 1; // indicate it is a on-use channel number
            }
            else
            {
                pDataLog[i*4] = 0; // indicate it is a non-use channel number
            }
            
            if (_gRawData1[i] >= 0)
            {
                pDataLog[i*4+1] = 0; // + : a positive number
            }
            else
            {
                pDataLog[i*4+1] = 1; // - : a negative number
            }

            pDataLog[i*4+2] = nHighByte;
            pDataLog[i*4+3] = nLowByte;
        }

        for (i = 0; i < MAX_CHANNEL_NUM; i ++)
        {
            nHighByte = (_gRawData2[i] >> 8) & 0xFF;
            nLowByte = (_gRawData2[i]) & 0xFF;
        
            if (_gDataFlag2[i] == 1)
            {
                pDataLog[i*4+MAX_CHANNEL_NUM*4] = 1; // indicate it is a on-use channel number
            }
            else
            {
                pDataLog[i*4+MAX_CHANNEL_NUM*4] = 0; // indicate it is a non-use channel number
            }

            if (_gRawData2[i] >= 0)
            {
                pDataLog[(i*4+1)+MAX_CHANNEL_NUM*4] = 0; // + : a positive number
            }
            else
            {
                pDataLog[(i*4+1)+MAX_CHANNEL_NUM*4] = 1; // - : a negative number
            }

            pDataLog[(i*4+2)+MAX_CHANNEL_NUM*4] = nHighByte;
            pDataLog[(i*4+3)+MAX_CHANNEL_NUM*4] = nLowByte;
        }

        *pLength = MAX_CHANNEL_NUM*8;
    }
    else if (eItoTestMode == ITO_TEST_MODE_SHORT_TEST)
    {
        for (i = 0; i < MAX_CHANNEL_NUM; i ++)
        {
            nHighByte = (_gRawData1[i] >> 8) & 0xFF;
            nLowByte = (_gRawData1[i]) & 0xFF;

            if (_gDataFlag1[i] == 1)
            {
                pDataLog[i*4] = 1; // indicate it is a on-use channel number
            }
            else
            {
                pDataLog[i*4] = 0; // indicate it is a non-use channel number
            }

            if (_gRawData1[i] >= 0)
            {
                pDataLog[i*4+1] = 0; // + : a positive number
            }
            else
            {
                pDataLog[i*4+1] = 1; // - : a negative number
            }

            pDataLog[i*4+2] = nHighByte;
            pDataLog[i*4+3] = nLowByte;
        }

        for (i = 0; i < MAX_CHANNEL_NUM; i ++)
        {
            nHighByte = (_gRawData2[i] >> 8) & 0xFF;
            nLowByte = (_gRawData2[i]) & 0xFF;
        
            if (_gDataFlag2[i] == 1)
            {
                pDataLog[i*4+MAX_CHANNEL_NUM*4] = 1; // indicate it is a on-use channel number
            }
            else
            {
                pDataLog[i*4+MAX_CHANNEL_NUM*4] = 0; // indicate it is a non-use channel number
            }

            if (_gRawData2[i] >= 0)
            {
                pDataLog[(i*4+1)+MAX_CHANNEL_NUM*4] = 0; // + : a positive number
            }
            else
            {
                pDataLog[(i*4+1)+MAX_CHANNEL_NUM*4] = 1; // - : a negative number
            }

            pDataLog[i*4+2+MAX_CHANNEL_NUM*4] = nHighByte;
            pDataLog[i*4+3+MAX_CHANNEL_NUM*4] = nLowByte;
        }

        for (i = 0; i < MAX_CHANNEL_NUM; i ++)
        {
            nHighByte = (_gRawData3[i] >> 8) & 0xFF;
            nLowByte = (_gRawData3[i]) & 0xFF;
        
            if (_gDataFlag3[i] == 1)
            {
                pDataLog[i*4+MAX_CHANNEL_NUM*8] = 1; // indicate it is a on-use channel number
            }
            else
            {
                pDataLog[i*4+MAX_CHANNEL_NUM*8] = 0; // indicate it is a non-use channel number
            }

            if (_gRawData3[i] >= 0)
            {
                pDataLog[(i*4+1)+MAX_CHANNEL_NUM*8] = 0; // + : a positive number
            }
            else
            {
                pDataLog[(i*4+1)+MAX_CHANNEL_NUM*8] = 1; // - : a negative number
            }

            pDataLog[(i*4+2)+MAX_CHANNEL_NUM*8] = nHighByte;
            pDataLog[(i*4+3)+MAX_CHANNEL_NUM*8] = nLowByte;
        }

        if (_gIsEnable2R)
        {
            for (i = 0; i < MAX_CHANNEL_NUM; i ++)
            {
                nHighByte = (_gRawData4[i] >> 8) & 0xFF;
                nLowByte = (_gRawData4[i]) & 0xFF;
        
                if (_gDataFlag4[i] == 1)
                {
                    pDataLog[i*4+MAX_CHANNEL_NUM*12] = 1; // indicate it is a on-use channel number
                }
                else
                {
                    pDataLog[i*4+MAX_CHANNEL_NUM*12] = 0; // indicate it is a non-use channel number
                }

                if (_gRawData4[i] >= 0)
                {
                    pDataLog[(i*4+1)+MAX_CHANNEL_NUM*12] = 0; // + : a positive number
                }
                else
                {
                    pDataLog[(i*4+1)+MAX_CHANNEL_NUM*12] = 1; // - : a negative number
                }

                pDataLog[(i*4+2)+MAX_CHANNEL_NUM*12] = nHighByte;
                pDataLog[(i*4+3)+MAX_CHANNEL_NUM*12] = nLowByte;
            }
        }
        
        *pLength = MAX_CHANNEL_NUM*16;
    }
    else 
    {
        DBG("*** Undefined MP Test Mode ***\n");
    }
}

void DrvMpTestScheduleMpTestWork(ItoTestMode_e eItoTestMode)
{
    DBG("*** %s() ***\n", __func__);

    if (_gIsInMpTest == 0)
    {
        DBG("ctp mp test start\n");
        
        if (g_ChipType == CHIP_TYPE_MSG22XX)
        {
            _gIsOldFirmwareVersion = _DrvMpTestMsg22xxCheckFirmwareVersion();
        }

        _gItoTestMode = eItoTestMode;
        _gIsInMpTest = 1;
        _gTestRetryCount = CTP_MP_TEST_RETRY_COUNT;
        _gCtpMpTestStatus = ITO_TEST_UNDER_TESTING;

        queue_work(_gCtpMpTestWorkQueue, &_gCtpItoTestWork);
    }
}

void DrvMpTestCreateMpTestWorkQueue(void)
{
    DBG("*** %s() ***\n", __func__);

    _gCtpMpTestWorkQueue = create_singlethread_workqueue("ctp_mp_test");
    INIT_WORK(&_gCtpItoTestWork, _DrvMpTestItoTestDoWork);
}

#endif //CONFIG_ENABLE_ITO_MP_TEST
#endif //CONFIG_ENABLE_TOUCH_DRIVER_FOR_SELF_IC
