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
 * @file    mstar_drv_jni_interface.c
 *
 * @brief   This file defines the jni interface functions
 *
 *
 */

////////////////////////////////////////////////////////////
/// Included Files
////////////////////////////////////////////////////////////
#include <linux/init.h>
#include <linux/module.h>
#include <linux/kthread.h>
#include <asm/uaccess.h>
#include "mstar_drv_jni_interface.h"
#include "mstar_drv_utility_adaption.h"
#include "mstar_drv_platform_porting_layer.h"


#ifdef CONFIG_ENABLE_JNI_INTERFACE
////////////////////////////////////////////////////////////
/// EXTERN VARIABLE DECLARATION
////////////////////////////////////////////////////////////
extern u32 SLAVE_I2C_ID_DBBUS;
extern u32 SLAVE_I2C_ID_DWI2C;
extern u8 g_IsHotknotEnabled;
extern u8 g_IsBypassHotknot;
extern struct i2c_client *g_I2cClient;


////////////////////////////////////////////////////////////
/// LOCAL VARIABLE DEFINITION
////////////////////////////////////////////////////////////
#define CMD_DATA_BUFFER_SIZE 1024U

static MsgToolDrvCmd_t * _gMsgToolCmdIn = NULL;
static u8 * _gSndCmdData = NULL;
static u8 * _gRtnCmdData = NULL;



void _DebugJniShowArray(u8 *pBuf, u16 nLen)
{
    int i;

    for(i=0; i < nLen; i++)
    {
        DBG("%02X ", pBuf[i]);       

        if(i%16==15){  
            DBG("\n");
        }
    }
    DBG("\n");    
}


u64 PtrToU64(u8 * pValue)
{
	uintptr_t nValue = (uintptr_t)pValue;
    return (u64)(0xFFFFFFFFFFFFFFFF&nValue);
}

u8 * U64ToPtr(u64 nValue)
{
	uintptr_t pValue = (uintptr_t)nValue;
	return (u8 *)pValue;
}


ssize_t MsgToolRead(struct file *pFile, char __user *pBuffer, size_t nCount, loff_t *pPos)
{
    long nRet = 0;
    u8 nBusType = 0;
    u16 nReadLen = 0;
    u8 szCmdData[20] = {0};

    
    DBG("*** %s() ***\n", __func__);
    //DBG("*** nCount = %d ***\n", (int)nCount);       
    nBusType = nCount&0xFF;
    nReadLen = (nCount >> 8)&0xFFFF;
    if(nBusType == SLAVE_I2C_ID_DBBUS || nBusType == SLAVE_I2C_ID_DWI2C)
    {    
        IicReadData(nBusType, &szCmdData[0], nReadLen);
    }
    
	nRet = copy_to_user(pBuffer, &szCmdData[0], nReadLen); 

    return nRet;
}			  


ssize_t MsgToolWrite(struct file *pFile, const char __user *pBuffer, size_t nCount, loff_t *pPos)  
{
    long nRet = 0;	                       
    u8 nBusType = 0;  
    u16 nWriteLen = 0;    
    u8 szCmdData[20] = {0};    


    DBG("*** %s() ***\n", __func__);
    //DBG("*** nCount = %d ***\n", (int)nCount);        
    nBusType = nCount&0xFF;
    nWriteLen = (nCount >> 8)&0xFFFF;    
	if (nWriteLen > sizeof(szCmdData))
		return -EINVAL;
	nRet = copy_from_user(szCmdData, &pBuffer[0], nWriteLen);         
    if(nBusType == SLAVE_I2C_ID_DBBUS || nBusType == SLAVE_I2C_ID_DWI2C)
    {
        IicWriteData(nBusType, &szCmdData[0], nWriteLen);    
    }

    return nRet;
}


void _RegGetXByteData(MsgToolDrvCmd_t * pCmd)
{    
    u16 nAddr = 0;

    DBG("*** %s() ***\n", __func__);    
    nAddr = (_gSndCmdData[1]<<8)|_gSndCmdData[0];    
    RegGetXBitValue(nAddr, _gRtnCmdData, pCmd->nRtnCmdLen, MAX_I2C_TRANSACTION_LENGTH_LIMIT);
    //_DebugJniShowArray(_gRtnCmdData, pCmd->nRtnCmdLen);
}


void _ClearMsgToolMem(void)
{
    DBG("*** %s() ***\n", __func__);
  
	memset(_gMsgToolCmdIn, 0, sizeof(MsgToolDrvCmd_t));
	memset(_gSndCmdData, 0, CMD_DATA_BUFFER_SIZE);
	memset(_gRtnCmdData, 0, CMD_DATA_BUFFER_SIZE);
}


static MsgToolDrvCmd_t* _TransJniCmdFromUser( unsigned long nArg )
{
    long nRet; 
    MsgToolDrvCmd_t tCmdIn;    
    MsgToolDrvCmd_t *pTransCmd;

    DBG("*** %s() ***\n", __func__);  
    _ClearMsgToolMem();
    pTransCmd = (MsgToolDrvCmd_t *)_gMsgToolCmdIn;    
    nRet = copy_from_user( &tCmdIn, (void*)nArg, sizeof( MsgToolDrvCmd_t ) );
    pTransCmd->nCmdId = tCmdIn.nCmdId;

    //_DebugJniShowArray(&tCmdIn, sizeof( MsgToolDrvCmd_t));
    if(tCmdIn.nSndCmdLen > 0)
    {
		pTransCmd->nSndCmdLen = min_t(u64, tCmdIn.nSndCmdLen, CMD_DATA_BUFFER_SIZE);
		nRet = copy_from_user(_gSndCmdData, U64ToPtr(tCmdIn.nSndCmdDataPtr), pTransCmd->nSndCmdLen);
	} else {
		/*Set this to avoid potential information disclosure security issue.*/
		pTransCmd->nSndCmdLen = 0;
    }

    if(tCmdIn.nRtnCmdLen > 0)
    {
	    pTransCmd->nRtnCmdLen = min_t(u64, tCmdIn.nRtnCmdLen, CMD_DATA_BUFFER_SIZE);
        nRet = copy_from_user( _gRtnCmdData, U64ToPtr(tCmdIn.nRtnCmdDataPtr), pTransCmd->nRtnCmdLen );    	        
	} else {
		/* Set this to avoid potential information disclosure security issue.*/
		pTransCmd->nRtnCmdLen = 0;
    }
  
    return pTransCmd;
}


static void _TransJniCmdToUser( MsgToolDrvCmd_t *pTransCmd, unsigned long nArg )
{
    MsgToolDrvCmd_t tCmdOut;
    long nRet;

    DBG("*** %s() ***\n", __func__);      
    nRet = copy_from_user( &tCmdOut, (void*)nArg, sizeof( MsgToolDrvCmd_t ) );   

    //_DebugJniShowArray(&tCmdOut, sizeof( MsgToolDrvCmd_t));    
	if (tCmdOut.nRtnCmdLen > CMD_DATA_BUFFER_SIZE)
		tCmdOut.nRtnCmdLen = CMD_DATA_BUFFER_SIZE;
    nRet = copy_to_user( U64ToPtr(tCmdOut.nRtnCmdDataPtr), _gRtnCmdData, tCmdOut.nRtnCmdLen);
}


long MsgToolIoctl( struct file *pFile, unsigned int nCmd, unsigned long nArg )
{
    long nRet = 0;

    DBG("*** %s() ***\n", __func__);    
    switch ( nCmd )
    {
        case MSGTOOL_IOCTL_RUN_CMD:
            {      
                MsgToolDrvCmd_t *pTransCmd;			
                pTransCmd = _TransJniCmdFromUser( nArg );  
                switch (pTransCmd->nCmdId)
                {
                    case MSGTOOL_RESETHW:
                        DrvPlatformLyrTouchDeviceResetHw();
                        break;
                    case MSGTOOL_REGGETXBYTEVALUE:
                        _RegGetXByteData(pTransCmd);                       
	                    _TransJniCmdToUser(pTransCmd, nArg);                                                 
                        break;
                    case MSGTOOL_HOTKNOTSTATUS:
                        _gRtnCmdData[0] = g_IsHotknotEnabled;                       
                        _TransJniCmdToUser(pTransCmd, nArg);                                                 
                        break;
                    case MSGTOOL_FINGERTOUCH:
                        if(pTransCmd->nSndCmdLen == 1)
                        {
                            DBG("*** JNI enable touch ***\n");                        
                            DrvPlatformLyrEnableFingerTouchReport();
                        }
                        else if(pTransCmd->nSndCmdLen == 0)
                        {
                            DBG("*** JNI disable touch ***\n");                                                
                            DrvPlatformLyrDisableFingerTouchReport();
                        }
                        break;
                    case MSGTOOL_BYPASSHOTKNOT:
                        if(pTransCmd->nSndCmdLen == 1)
                        {
                            DBG("*** JNI enable bypass hotknot ***\n");                                                
                            g_IsBypassHotknot = 1;                                                      
                        }
                        else if(pTransCmd->nSndCmdLen == 0)
                        {
                            DBG("*** JNI disable bypass hotknot ***\n");                                                
                            g_IsBypassHotknot = 0;
                        }
                        break;
                    case MSGTOOL_DEVICEPOWEROFF:
                        DrvPlatformLyrTouchDevicePowerOff();
                        break;                        
                    case MSGTOOL_GETSMDBBUS:
                        DBG("*** MSGTOOL_GETSMDBBUS ***\n");
                        _gRtnCmdData[0] = SLAVE_I2C_ID_DBBUS&0xFF;                       
                        _gRtnCmdData[1] = SLAVE_I2C_ID_DWI2C&0xFF;                                               
                        _TransJniCmdToUser(pTransCmd, nArg);                                                 
                        break;
                    case MSGTOOL_SETIICDATARATE:
                        DBG("*** MSGTOOL_SETIICDATARATE ***\n");                        
                        DrvPlatformLyrSetIicDataRate(g_I2cClient, ((_gSndCmdData[1]<<8)|_gSndCmdData[0])*1000);
                        break;                        
                    default:  
                        break;
                }		            
            }   
		    break;
		
        default:
            nRet = -EINVAL;
            break;
    }

    return nRet;
}


void CreateMsgToolMem(void)
{
    DBG("*** %s() ***\n", __func__);

	_gMsgToolCmdIn = kmalloc(sizeof(MsgToolDrvCmd_t), GFP_KERNEL);
	_gSndCmdData = kmalloc(CMD_DATA_BUFFER_SIZE, GFP_KERNEL);
	_gRtnCmdData = kmalloc(CMD_DATA_BUFFER_SIZE, GFP_KERNEL);
}


void DeleteMsgToolMem(void)
{
    DBG("*** %s() ***\n", __func__);
 
    if (_gMsgToolCmdIn)
    {
        kfree(_gMsgToolCmdIn);
        _gMsgToolCmdIn = NULL;
    }
    
    if (_gSndCmdData)
    {
        kfree(_gSndCmdData);
        _gSndCmdData = NULL;
    }
    
    if (_gRtnCmdData)
    {
        kfree(_gRtnCmdData);
        _gRtnCmdData = NULL;
    }
}

#endif //CONFIG_ENABLE_JNI_INTERFACE
