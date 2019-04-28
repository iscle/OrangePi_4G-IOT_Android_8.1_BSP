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
 * @file    mstar_drv_utility_adaption.c
 *
 * @brief   This file defines the interface of touch screen
 *
 *
 */

////////////////////////////////////////////////////////////
/// Included Files
////////////////////////////////////////////////////////////
#include "mstar_drv_utility_adaption.h"

#ifdef CONFIG_ONTIM_DSM	

#include <ontim/ontim_dsm.h>

extern struct dsm_client *msg2238a_dsm_client;
extern char msg2238a_vendor_name[];
extern bool tp_probe_ok;
#endif

////////////////////////////////////////////////////////////
/// Data Types
////////////////////////////////////////////////////////////


////////////////////////////////////////////////////////////
/// Constant
////////////////////////////////////////////////////////////


////////////////////////////////////////////////////////////
/// Variables
////////////////////////////////////////////////////////////
extern u32 SLAVE_I2C_ID_DBBUS;
extern u32 SLAVE_I2C_ID_DWI2C;

extern struct i2c_client *g_I2cClient;
extern struct input_dev *g_InputDevice;
extern struct mutex g_Mutex;

extern u8 g_ChipType;
extern u8 g_IsUpdateFirmware;
extern u8 g_IsBypassHotknot;

////////////////////////////////////////////////////////////
/// Macro
////////////////////////////////////////////////////////////


////////////////////////////////////////////////////////////
/// Function Prototypes
////////////////////////////////////////////////////////////


////////////////////////////////////////////////////////////
/// Function Implementation
////////////////////////////////////////////////////////////


#ifdef CONFIG_ENABLE_DMA_IIC
#include <linux/dma-mapping.h>
#include <linux/mm_types.h>
#include <linux/mm.h>
#include <asm/uaccess.h>
#include <asm/page.h>
#include <linux/vmalloc.h>

static unsigned char *I2CDMABuf_va = NULL;
static dma_addr_t I2CDMABuf_pa = 0;

void DmaAlloc(void)
{
    if (NULL == I2CDMABuf_va)
    {
        g_InputDevice->dev.coherent_dma_mask = DMA_BIT_MASK(32);
        I2CDMABuf_va = (u8 *)dma_alloc_coherent(&g_InputDevice->dev, MAX_I2C_TRANSACTION_LENGTH_LIMIT, &I2CDMABuf_pa, GFP_KERNEL);
    }
    
    if (NULL == I2CDMABuf_va)
    {
        DBG("DmaAlloc FAILED!\n");
    }
    else
    {
        DBG("DmaAlloc SUCCESS!\n");
    }
}

void DmaReset(void)
{
    DBG("Dma memory reset!\n");

    memset(I2CDMABuf_va, 0, MAX_I2C_TRANSACTION_LENGTH_LIMIT);
}

void DmaFree(void)
{
    if (NULL != I2CDMABuf_va)
    {
	      dma_free_coherent(&g_InputDevice->dev, MAX_I2C_TRANSACTION_LENGTH_LIMIT, I2CDMABuf_va, I2CDMABuf_pa);
	      I2CDMABuf_va = NULL;
	      I2CDMABuf_pa = 0;

        DBG("DmaFree SUCCESS!\n");
    }
}
#endif //CONFIG_ENABLE_DMA_IIC

//------------------------------------------------------------------------------//

u16 RegGet16BitValue(u16 nAddr)
{
    u8 tx_data[3] = {0x10, (nAddr >> 8) & 0xFF, nAddr & 0xFF};
    u8 rx_data[2] = {0};

    IicWriteData(SLAVE_I2C_ID_DBBUS, &tx_data[0], 3);
    IicReadData(SLAVE_I2C_ID_DBBUS, &rx_data[0], 2);

    return (rx_data[1] << 8 | rx_data[0]);
}

u8 RegGetLByteValue(u16 nAddr)
{
    u8 tx_data[3] = {0x10, (nAddr >> 8) & 0xFF, nAddr & 0xFF};
    u8 rx_data = {0};

    IicWriteData(SLAVE_I2C_ID_DBBUS, &tx_data[0], 3);
    IicReadData(SLAVE_I2C_ID_DBBUS, &rx_data, 1);

    return (rx_data);
}

u8 RegGetHByteValue(u16 nAddr)
{
    u8 tx_data[3] = {0x10, (nAddr >> 8) & 0xFF, (nAddr & 0xFF) + 1};
    u8 rx_data = {0};

    IicWriteData(SLAVE_I2C_ID_DBBUS, &tx_data[0], 3);
    IicReadData(SLAVE_I2C_ID_DBBUS, &rx_data, 1);

    return (rx_data);
}

void RegGetXBitValue(u16 nAddr, u8 * pRxData, u16 nLength, u16 nMaxI2cLengthLimit)
{
    u16 nReadAddr = nAddr;    
    u16 nReadSize = 0;
    u16 nLeft = nLength;    
    u16 nOffset = 0; 
    u8 tx_data[3] = {0};

    tx_data[0] = 0x10;

    mutex_lock(&g_Mutex);

#ifdef CONFIG_TOUCH_DRIVER_RUN_ON_MTK_PLATFORM
#ifdef CONFIG_ENABLE_DMA_IIC
    DmaReset();
#endif //CONFIG_ENABLE_DMA_IIC
#endif //CONFIG_TOUCH_DRIVER_RUN_ON_MTK_PLATFORM

    while(nLeft > 0)
    {
        if(nLeft >= nMaxI2cLengthLimit)
        {
            nReadSize = nMaxI2cLengthLimit;
            //DBG("*** RegGetXBitValue# Length >= I2cMax   nReadAddr=%x, nReadSize=%d ***\n", nReadAddr, nReadSize);

            tx_data[1] = (nReadAddr >> 8) & 0xFF;
            tx_data[2] = nReadAddr & 0xFF;            
        
            IicWriteData(SLAVE_I2C_ID_DBBUS, &tx_data[0], 3);
            IicReadData(SLAVE_I2C_ID_DBBUS, &pRxData[nOffset], nReadSize);
        
            nReadAddr = nReadAddr + nReadSize;    //set next read address
            nLeft = nLeft - nReadSize;
            nOffset = nOffset + nReadSize;
            //DBG("*** RegGetXBitValue# Length >= I2cMax   nLeft=%d, nOffset=%d ***\n", nLeft, nOffset);
        }
        else
        {
            nReadSize = nLeft;
            //DBG("*** RegGetXBitValue# Length < I2cMax   nReadAddr=%x, nReadSize=%d ***\n", nReadAddr, nReadSize);

            tx_data[1] = (nReadAddr >> 8) & 0xFF;
            tx_data[2] = nReadAddr & 0xFF;            
        
            IicWriteData(SLAVE_I2C_ID_DBBUS, &tx_data[0], 3);
            IicReadData(SLAVE_I2C_ID_DBBUS, &pRxData[nOffset], nReadSize);
            
            nLeft = 0;
            nOffset = nOffset + nReadSize;            
            //DBG("*** RegGetXBitValue# Length < I2cMax   nLeft=%d, nOffset=%d ***\n", nLeft, nOffset);
        }
    }

    mutex_unlock(&g_Mutex);    
}

void RegSet16BitValue(u16 nAddr, u16 nData)
{
    u8 tx_data[5] = {0x10, (nAddr >> 8) & 0xFF, nAddr & 0xFF, nData & 0xFF, nData >> 8};
    IicWriteData(SLAVE_I2C_ID_DBBUS, &tx_data[0], 5);
}

void RegSetLByteValue(u16 nAddr, u8 nData)
{
    u8 tx_data[4] = {0x10, (nAddr >> 8) & 0xFF, nAddr & 0xFF, nData};
    IicWriteData(SLAVE_I2C_ID_DBBUS, &tx_data[0], 4);
}

void RegSetHByteValue(u16 nAddr, u8 nData)
{
    u8 tx_data[4] = {0x10, (nAddr >> 8) & 0xFF, (nAddr & 0xFF) + 1, nData};
    IicWriteData(SLAVE_I2C_ID_DBBUS, &tx_data[0], 4);
}

void RegSet16BitValueOn(u16 nAddr, u16 nData) //Set bit on nData from 0 to 1
{
    u16 rData = RegGet16BitValue(nAddr);
    rData |= nData;
    RegSet16BitValue(nAddr, rData);
}

void RegSet16BitValueOff(u16 nAddr, u16 nData) //Set bit on nData from 1 to 0
{
    u16 rData = RegGet16BitValue(nAddr);
    rData &= (~nData);
    RegSet16BitValue(nAddr, rData);
}

u16 RegGet16BitValueByAddressMode(u16 nAddr, AddressMode_e eAddressMode)
{
    u16 nData = 0;
    
    if (eAddressMode == ADDRESS_MODE_16BIT)
    {
        nAddr = nAddr - (nAddr & 0xFF) + ((nAddr & 0xFF) << 1);
    }
    
    nData = RegGet16BitValue(nAddr);
    
    return nData;
}
	
void RegSet16BitValueByAddressMode(u16 nAddr, u16 nData, AddressMode_e eAddressMode)
{
    if (eAddressMode == ADDRESS_MODE_16BIT)
    {
        nAddr = nAddr - (nAddr & 0xFF) + ((nAddr & 0xFF) << 1);
    }
    
    RegSet16BitValue(nAddr, nData);
}

void RegMask16BitValue(u16 nAddr, u16 nMask, u16 nData, AddressMode_e eAddressMode) 
{
    u16 nTmpData = 0;
    
    if (nData > nMask)
    {
        return;
    }

    nTmpData = RegGet16BitValueByAddressMode(nAddr, eAddressMode);
    nTmpData = (nTmpData & (~nMask));
    nTmpData = (nTmpData | nData);
    RegSet16BitValueByAddressMode(nAddr, nTmpData, eAddressMode);
}

s32 DbBusEnterSerialDebugMode(void)
{
    s32 rc = 0;
    u8 data[5];

    // Enter the Serial Debug Mode
    data[0] = 0x53;
    data[1] = 0x45;
    data[2] = 0x52;
    data[3] = 0x44;
    data[4] = 0x42;

    rc = IicWriteData(SLAVE_I2C_ID_DBBUS, data, 5);
    
    return rc;
}

void DbBusExitSerialDebugMode(void)
{
    u8 data[1];

    // Exit the Serial Debug Mode
    data[0] = 0x45;

    IicWriteData(SLAVE_I2C_ID_DBBUS, data, 1);

    // Delay some interval to guard the next transaction
//    udelay(200);        // delay about 0.2ms
}

void DbBusIICUseBus(void)
{
    u8 data[1];

    // IIC Use Bus
    data[0] = 0x35;

    IicWriteData(SLAVE_I2C_ID_DBBUS, data, 1);
}

void DbBusIICNotUseBus(void)
{
    u8 data[1];

    // IIC Not Use Bus
    data[0] = 0x34;

    IicWriteData(SLAVE_I2C_ID_DBBUS, data, 1);
}

void DbBusIICReshape(void)
{
    u8 data[1];

    // IIC Re-shape
    data[0] = 0x71;

    IicWriteData(SLAVE_I2C_ID_DBBUS, data, 1);
}

void DbBusStopMCU(void)
{
    u8 data[1];

    // Stop the MCU
    data[0] = 0x37;

    IicWriteData(SLAVE_I2C_ID_DBBUS, data, 1);
}

void DbBusNotStopMCU(void)
{
    u8 data[1];

    // Not Stop the MCU
    data[0] = 0x36;

    IicWriteData(SLAVE_I2C_ID_DBBUS, data, 1);
}

void DbBusResetSlave(void)
{
    u8 data[1];

    // IIC Reset Slave
    data[0] = 0x00;

    IicWriteData(SLAVE_I2C_ID_DBBUS, data, 1);
}

void DbBusWaitMCU(void)
{
    u8 data[1];

    // Stop the MCU
    data[0] = 0x37;
    IicWriteData(SLAVE_I2C_ID_DBBUS, data, 1);

    data[0] = 0x61;
    IicWriteData(SLAVE_I2C_ID_DBBUS, data, 1);    
}

s32 IicWriteData(u8 nSlaveId, u8* pBuf, u16 nSize)
{
    s32 rc = 0;

#if defined(CONFIG_TOUCH_DRIVER_RUN_ON_SPRD_PLATFORM) || defined(CONFIG_TOUCH_DRIVER_RUN_ON_QCOM_PLATFORM)
    struct i2c_msg msgs[] =
    {
        {
            .addr = nSlaveId,
            .flags = 0, // if read flag is undefined, then it means write flag.
            .len = nSize,
            .buf = pBuf,
        },
    };

    /* If everything went ok (i.e. 1 msg transmitted), return #bytes
       transmitted, else error code. */
    if (g_I2cClient != NULL)
    {
        if (g_ChipType == CHIP_TYPE_MSG28XX && nSlaveId == SLAVE_I2C_ID_DWI2C && (g_IsUpdateFirmware != 0 || g_IsBypassHotknot != 0))
        {
            PRINTF_ERR("Not allow to execute SmBus command while update firmware.\n");
        }
        else
        {
            rc = i2c_transfer(g_I2cClient->adapter, msgs, 1);

            if (rc == 1)
            {
                rc = nSize;
            }
            else // rc < 0
            {
                PRINTF_ERR("IicWriteData() error %d\n", rc);
            }
        }
    }
    else
    {
        PRINTF_ERR("i2c client is NULL\n");
    }
#elif defined(CONFIG_TOUCH_DRIVER_RUN_ON_MTK_PLATFORM)
    if (g_I2cClient != NULL)
    {
        if (g_ChipType == CHIP_TYPE_MSG28XX && nSlaveId == SLAVE_I2C_ID_DWI2C && (g_IsUpdateFirmware != 0 || g_IsBypassHotknot != 0))
        {
            PRINTF_ERR("Not allow to execute SmBus command while update firmware.\n");
        }
        else
        {
            u8 nAddrBefore = g_I2cClient->addr;
            g_I2cClient->addr = nSlaveId;

#ifdef CONFIG_ENABLE_DMA_IIC
            if (nSize > 8 && NULL != I2CDMABuf_va)
            {
                s32 i = 0;
	          
                for (i = 0; i < nSize; i ++)
                {
                    I2CDMABuf_va[i] = pBuf[i];
                }
                g_I2cClient->ext_flag = g_I2cClient->ext_flag | I2C_DMA_FLAG;
                rc = i2c_master_send(g_I2cClient, (unsigned char *)I2CDMABuf_pa, nSize);
            }
            else
            {
                g_I2cClient->ext_flag = g_I2cClient->ext_flag & (~I2C_DMA_FLAG);	
                rc = i2c_master_send(g_I2cClient, pBuf, nSize);
            }
#else
            rc = i2c_master_send(g_I2cClient, pBuf, nSize);
#endif //CONFIG_ENABLE_DMA_IIC
            g_I2cClient->addr = nAddrBefore;

            if (rc < 0)
            {
                PRINTF_ERR("IicWriteData() error %d, nSlaveId=%d, nSize=%d\n", rc, nSlaveId, nSize);
            }
        }            
    }
    else
    {
        PRINTF_ERR("i2c client is NULL\n");
    }
#endif
#ifdef CONFIG_ONTIM_DSM	
	if (tp_probe_ok && (rc<0))
	{
		int error=OMTIM_DSM_TP_READ_I2C_ERROR;
		int i=0;
	 	if ( (msg2238a_dsm_client ) && dsm_client_ocuppy(msg2238a_dsm_client))
	 	{
	 		if ((msg2238a_dsm_client->dump_buff) && (msg2238a_dsm_client->buff_size)&&(msg2238a_dsm_client->buff_flag == OMTIM_DSM_BUFF_OK))
	 		{
				msg2238a_dsm_client->used_size = sprintf(msg2238a_dsm_client->dump_buff,"Type=%d; ID=%d; error_id=%d; CTP info:%s; Write error = %d;{",msg2238a_dsm_client->client_type,msg2238a_dsm_client->client_id,error,msg2238a_vendor_name,rc );
				msg2238a_dsm_client->used_size += sprintf(msg2238a_dsm_client->dump_buff+msg2238a_dsm_client->used_size,"SlaveId=0x%x; Write len=%d; ",nSlaveId,nSize);
				if ( nSize && pBuf)
				{
					msg2238a_dsm_client->used_size += sprintf(msg2238a_dsm_client->dump_buff+msg2238a_dsm_client->used_size,"Buff[");
					for(i=0;i<nSize;i++)
					{
						msg2238a_dsm_client->used_size += sprintf(msg2238a_dsm_client->dump_buff+msg2238a_dsm_client->used_size," 0x%x ",pBuf[i] );
					}
					msg2238a_dsm_client->used_size += sprintf(msg2238a_dsm_client->dump_buff+msg2238a_dsm_client->used_size,"]");
	 			}
				msg2238a_dsm_client->used_size += sprintf(msg2238a_dsm_client->dump_buff+msg2238a_dsm_client->used_size,"}\n");
				dsm_client_notify(msg2238a_dsm_client,error);
	 		}
	 	}
		else
		{
			printk(KERN_ERR "%s: dsm ocuppy error!!!",__func__);
		}
	}
#endif
    
    return rc;
}

s32 IicReadData(u8 nSlaveId, u8* pBuf, u16 nSize)
{
    s32 rc = 0;

#if defined(CONFIG_TOUCH_DRIVER_RUN_ON_SPRD_PLATFORM) || defined(CONFIG_TOUCH_DRIVER_RUN_ON_QCOM_PLATFORM)
    struct i2c_msg msgs[] =
    {
        {
            .addr = nSlaveId,
            .flags = I2C_M_RD, // read flag
            .len = nSize,
            .buf = pBuf,
        },
    };

    /* If everything went ok (i.e. 1 msg transmitted), return #bytes
       transmitted, else error code. */
    if (g_I2cClient != NULL)
    {
        if (g_ChipType == CHIP_TYPE_MSG28XX && nSlaveId == SLAVE_I2C_ID_DWI2C && (g_IsUpdateFirmware != 0 || g_IsBypassHotknot != 0))
        {
            PRINTF_ERR("Not allow to execute SmBus command while update firmware.\n");
        }
        else
        {
            rc = i2c_transfer(g_I2cClient->adapter, msgs, 1);
        
            if (rc == 1)
            {
                rc = nSize;
            }
            else // rc < 0
            {
                PRINTF_ERR("IicReadData() error %d\n", rc);
            }
        }
    }
    else
    {
        PRINTF_ERR("i2c client is NULL\n");
    }
#elif defined(CONFIG_TOUCH_DRIVER_RUN_ON_MTK_PLATFORM)
    if (g_I2cClient != NULL)
    {
        if (g_ChipType == CHIP_TYPE_MSG28XX && nSlaveId == SLAVE_I2C_ID_DWI2C && (g_IsUpdateFirmware != 0 || g_IsBypassHotknot != 0))
        {
            PRINTF_ERR("Not allow to execute SmBus command while update firmware.\n");
        }
        else
        {
            u8 nAddrBefore = g_I2cClient->addr;
            g_I2cClient->addr = nSlaveId;

#ifdef CONFIG_ENABLE_DMA_IIC
            if (nSize > 8 && NULL != I2CDMABuf_va)
            {
                s32 i = 0;
        
                g_I2cClient->ext_flag = g_I2cClient->ext_flag | I2C_DMA_FLAG;
                rc = i2c_master_recv(g_I2cClient, (unsigned char *)I2CDMABuf_pa, nSize);
        
                for (i = 0; i < nSize; i ++)
                {
                    pBuf[i] = I2CDMABuf_va[i];
                }
            }
            else
            {
                g_I2cClient->ext_flag = g_I2cClient->ext_flag & (~I2C_DMA_FLAG);	
                rc = i2c_master_recv(g_I2cClient, pBuf, nSize);
            }
#else
            rc = i2c_master_recv(g_I2cClient, pBuf, nSize);
#endif //CONFIG_ENABLE_DMA_IIC
            g_I2cClient->addr = nAddrBefore;

            if (rc < 0)
            {
                PRINTF_ERR("IicReadData() error %d, nSlaveId=%d, nSize=%d\n", rc, nSlaveId, nSize);
            }
        }            
    }
    else
    {
        PRINTF_ERR("i2c client is NULL\n");
    }
#endif
#ifdef CONFIG_ONTIM_DSM	
	if (tp_probe_ok && (rc<0))
	{
		int error=OMTIM_DSM_TP_READ_I2C_ERROR;
		int i=0;
	 	if ( (msg2238a_dsm_client ) && dsm_client_ocuppy(msg2238a_dsm_client))
	 	{
	 		if ((msg2238a_dsm_client->dump_buff) && (msg2238a_dsm_client->buff_size)&&(msg2238a_dsm_client->buff_flag == OMTIM_DSM_BUFF_OK))
	 		{
				msg2238a_dsm_client->used_size = sprintf(msg2238a_dsm_client->dump_buff,"Type=%d; ID=%d; error_id=%d; CTP info:%s; Read error = %d;{",msg2238a_dsm_client->client_type,msg2238a_dsm_client->client_id,error,msg2238a_vendor_name,rc );
				msg2238a_dsm_client->used_size += sprintf(msg2238a_dsm_client->dump_buff+msg2238a_dsm_client->used_size,"SlaveId=0x%x; Read len=%d}\n ",nSlaveId,nSize);
				dsm_client_notify(msg2238a_dsm_client,error);
	 		}
	 	}
		else
		{
			printk(KERN_ERR "%s: dsm ocuppy error!!!",__func__);
		}
	}
#endif
    
    return rc;
}

s32 IicSegmentReadDataByDbBus(u8 nRegBank, u8 nRegAddr, u8* pBuf, u16 nSize, u16 nMaxI2cLengthLimit)
{
    s32 rc = 0;
    u16 nLeft = nSize;
    u16 nOffset = 0;
    u16 nSegmentLength = 0;
    u16 nReadSize = 0;
    u16 nOver = 0;
    u8  szWriteBuf[3] = {0};
    u8  nNextRegBank = nRegBank;
    u8  nNextRegAddr = nRegAddr;

#if defined(CONFIG_TOUCH_DRIVER_RUN_ON_SPRD_PLATFORM) || defined(CONFIG_TOUCH_DRIVER_RUN_ON_QCOM_PLATFORM)
    struct i2c_msg msgs[2] =
    {
        {
            .addr = SLAVE_I2C_ID_DBBUS,
            .flags = 0, // write flag
            .len = 3,
            .buf = szWriteBuf,
        },
        {
            .addr = SLAVE_I2C_ID_DBBUS,
            .flags =  I2C_M_RD, // read flag
        },
    };

    // If everything went ok (i.e. 1 msg transmitted), return #bytes transmitted, else error code. 
    if (g_I2cClient != NULL)
    {
        if (nMaxI2cLengthLimit >= 256)
        {
            nSegmentLength = 256;
        }
        else
        {
            nSegmentLength = 128;
        }
        
        PRINTF_ERR("nSegmentLength = %d\n", nSegmentLength);	// add for debug

        while (nLeft > 0)
        {
            szWriteBuf[0] = 0x10;
            nRegBank = nNextRegBank;
            szWriteBuf[1] = nRegBank;
            nRegAddr = nNextRegAddr;
            szWriteBuf[2] = nRegAddr;

            PRINTF_ERR("nRegBank = 0x%x\n", nRegBank);	// add for debug
            PRINTF_ERR("nRegAddr = 0x%x\n", nRegAddr);	// add for debug

            msgs[1].buf = &pBuf[nOffset];

            if (nLeft > nSegmentLength)
            {
                if ((nRegAddr + nSegmentLength) < MAX_TOUCH_IC_REGISTER_BANK_SIZE)
                {
                    nNextRegAddr = nRegAddr + nSegmentLength; 
                    
                    PRINTF_ERR("nNextRegAddr = 0x%x\n", nNextRegAddr);	// add for debug

                    msgs[1].len = nSegmentLength;
                    nLeft -= nSegmentLength;
                    nOffset += msgs[1].len;
                }
                else if ((nRegAddr + nSegmentLength) == MAX_TOUCH_IC_REGISTER_BANK_SIZE)
                {
                    nNextRegAddr = 0x00; 		
                    nNextRegBank = nRegBank + 1; // shift to read data from next register bank

                    PRINTF_ERR("nNextRegBank = 0x%x\n", nNextRegBank);	// add for debug

                    msgs[1].len = nSegmentLength;
                    nLeft -= nSegmentLength;
                    nOffset += msgs[1].len;
                }
                else // ((nRegAddr + nSegmentLength) > MAX_TOUCH_IC_REGISTER_BANK_SIZE)
                {
                    nNextRegAddr = 0x00;
                    nNextRegBank = nRegBank + 1; // shift to read data from next register bank

                    PRINTF_INFO("nNextRegBank = 0x%x\n", nNextRegBank);	// add for debug
                    
                    nOver = (nRegAddr + nSegmentLength) - MAX_TOUCH_IC_REGISTER_BANK_SIZE;

                    PRINTF_ERR("nOver = 0x%x\n", nOver);	// add for debug

                    msgs[1].len = nSegmentLength - nOver; 		
                    nLeft -= msgs[1].len;
                    nOffset += msgs[1].len;
                }
            }
            else
            {
                if ((nRegAddr + nLeft) < MAX_TOUCH_IC_REGISTER_BANK_SIZE)
                {
                    nNextRegAddr = nRegAddr + nLeft; 
                    
                    PRINTF_ERR("nNextRegAddr = 0x%x\n", nNextRegAddr);	// add for debug

                    msgs[1].len = nLeft;
                    nLeft = 0;
//                    nOffset += msgs[1].len;
                }
                else if ((nRegAddr + nLeft) == MAX_TOUCH_IC_REGISTER_BANK_SIZE)
                {
                    nNextRegAddr = 0x00; 		
                    nNextRegBank = nRegBank + 1; // shift to read data from next register bank

                    PRINTF_ERR("nNextRegBank = 0x%x\n", nNextRegBank);	// add for debug

                    msgs[1].len = nLeft;
                    nLeft = 0;
//                    nOffset += msgs[1].len;
                }
                else // ((nRegAddr + nLeft) > MAX_TOUCH_IC_REGISTER_BANK_SIZE)
                {
                    nNextRegAddr = 0x00;
                    nNextRegBank = nRegBank + 1; // shift to read data from next register bank

                    PRINTF_ERR("nNextRegBank = 0x%x\n", nNextRegBank);	// add for debug
                    
                    nOver = (nRegAddr + nLeft) - MAX_TOUCH_IC_REGISTER_BANK_SIZE;

                    PRINTF_ERR("nOver = 0x%x\n", nOver);	// add for debug

                    msgs[1].len = nLeft - nOver; 		
                    nLeft -= msgs[1].len;
                    nOffset += msgs[1].len;
                }
            }

            rc = i2c_transfer(g_I2cClient->adapter, &msgs[0], 2);
            if (rc == 2)
            {
                nReadSize = nReadSize + msgs[1].len;
            }
            else // rc < 0
            {
                PRINTF_ERR("IicSegmentReadDataByDbBus() -> i2c_transfer() error %d\n", rc);
                
                return rc;
            }
        }
    }
    else
    {
        PRINTF_ERR("i2c client is NULL\n");
    }
#elif defined(CONFIG_TOUCH_DRIVER_RUN_ON_MTK_PLATFORM)
    if (g_I2cClient != NULL)
    {
        //u8 *pReadBuf = NULL;
        u16 nLength = 0;
        u8 nAddrBefore = g_I2cClient->addr;
        
        g_I2cClient->addr = SLAVE_I2C_ID_DBBUS;

        if (nMaxI2cLengthLimit >= 256)
        {
            nSegmentLength = 256;
        }
        else
        {
            nSegmentLength = 128;
        }

        PRINTF_ERR("nSegmentLength = %d\n", nSegmentLength);	// add for debug

#ifdef CONFIG_ENABLE_DMA_IIC
        if (NULL != I2CDMABuf_va)
        {
            s32 i = 0;

            while (nLeft > 0)
            {
                szWriteBuf[0] = 0x10;
                nRegBank = nNextRegBank;
                szWriteBuf[1] = nRegBank;
                nRegAddr = nNextRegAddr;
                szWriteBuf[2] = nRegAddr;

                PRINTF_ERR("nRegBank = 0x%x\n", nRegBank);	// add for debug
                PRINTF_ERR("nRegAddr = 0x%x\n", nRegAddr);	// add for debug

                if (nLeft > nSegmentLength)
                {
                    if ((nRegAddr + nSegmentLength) < MAX_TOUCH_IC_REGISTER_BANK_SIZE)
                    {
                        nNextRegAddr = nRegAddr + nSegmentLength; 
                    
                        PRINTF_ERR("nNextRegAddr = 0x%x\n", nNextRegAddr);	// add for debug

                        nLength = nSegmentLength;
                        nLeft -= nSegmentLength;
                    }
                    else if ((nRegAddr + nSegmentLength) == MAX_TOUCH_IC_REGISTER_BANK_SIZE)
                    {
                        nNextRegAddr = 0x00; 		
                        nNextRegBank = nRegBank + 1; // shift to read data from next register bank

                        PRINTF_ERR("nNextRegBank = 0x%x\n", nNextRegBank);	// add for debug

                        nLength = nSegmentLength;
                        nLeft -= nSegmentLength;
                    }
                    else // ((nRegAddr + nSegmentLength) > MAX_TOUCH_IC_REGISTER_BANK_SIZE)
                    {
                        nNextRegAddr = 0x00;
                        nNextRegBank = nRegBank + 1; // shift to read data from next register bank

                        PRINTF_INFO("nNextRegBank = 0x%x\n", nNextRegBank);	// add for debug
                    
                        nOver = (nRegAddr + nSegmentLength) - MAX_TOUCH_IC_REGISTER_BANK_SIZE;

                        PRINTF_ERR("nOver = 0x%x\n", nOver);	// add for debug

                        nLength = nSegmentLength - nOver; 		
                        nLeft -= nLength;
                    }
                }
                else
                {
                    if ((nRegAddr + nLeft) < MAX_TOUCH_IC_REGISTER_BANK_SIZE)
                    {
                        nNextRegAddr = nRegAddr + nLeft; 
                    
                        PRINTF_ERR("nNextRegAddr = 0x%x\n", nNextRegAddr);	// add for debug

                        nLength = nLeft;
                        nLeft = 0;
                    }
                    else if ((nRegAddr + nLeft) == MAX_TOUCH_IC_REGISTER_BANK_SIZE)
                    {
                        nNextRegAddr = 0x00; 		
                        nNextRegBank = nRegBank + 1; // shift to read data from next register bank

                        PRINTF_ERR("nNextRegBank = 0x%x\n", nNextRegBank);	// add for debug

                        nLength = nLeft;
                        nLeft = 0;
                    }
                    else // ((nRegAddr + nLeft) > MAX_TOUCH_IC_REGISTER_BANK_SIZE)
                    {
                        nNextRegAddr = 0x00;
                        nNextRegBank = nRegBank + 1; // shift to read data from next register bank

                        PRINTF_ERR("nNextRegBank = 0x%x\n", nNextRegBank);	// add for debug
                    
                        nOver = (nRegAddr + nLeft) - MAX_TOUCH_IC_REGISTER_BANK_SIZE;

                        PRINTF_ERR("nOver = 0x%x\n", nOver);	// add for debug

                        nLength = nLeft - nOver; 		
                        nLeft -= nLength;
                    }
                }

                g_I2cClient->ext_flag = g_I2cClient->ext_flag & (~I2C_DMA_FLAG);
                rc = i2c_master_send(g_I2cClient, &szWriteBuf[0], 3);
                if (rc < 0)
                {
                    PRINTF_ERR("IicSegmentReadDataByDbBus() -> i2c_master_send() error %d\n", rc);

                    return rc;
                }

                g_I2cClient->ext_flag = g_I2cClient->ext_flag | I2C_DMA_FLAG;
                rc = i2c_master_recv(g_I2cClient, (unsigned char *)I2CDMABuf_pa, nLength);
                if (rc < 0)
                {
                    PRINTF_ERR("IicSegmentReadDataByDbBus() -> i2c_master_recv() error %d\n", rc);
                    
                    return rc;
                }
                else
                {
                    for (i = 0; i < nLength; i ++)
                    {
                        pBuf[i+nOffset] = I2CDMABuf_va[i];
                    }
                    nOffset += nLength;

                    nReadSize = nReadSize + nLength;
                }
            }
        }
        else
        {
            PRINTF_ERR("IicSegmentReadDataByDbBus() -> I2CDMABuf_va is NULL\n");
        }
#else
        while (nLeft > 0)
        {
            szWriteBuf[0] = 0x10;
            nRegBank = nNextRegBank;
            szWriteBuf[1] = nRegBank;
            nRegAddr = nNextRegAddr;
            szWriteBuf[2] = nRegAddr;

            PRINTF_ERR("nRegBank = 0x%x\n", nRegBank);	// add for debug
            PRINTF_ERR("nRegAddr = 0x%x\n", nRegAddr);	// add for debug

            pReadBuf = &pBuf[nOffset];

            if (nLeft > nSegmentLength)
            {
                if ((nRegAddr + nSegmentLength) < MAX_TOUCH_IC_REGISTER_BANK_SIZE)
                {
                    nNextRegAddr = nRegAddr + nSegmentLength; 
                    
                    PRINTF_ERR("nNextRegAddr = 0x%x\n", nNextRegAddr);	// add for debug

                    nLength = nSegmentLength;
                    nLeft -= nSegmentLength;
                    nOffset += nLength;
                }
                else if ((nRegAddr + nSegmentLength) == MAX_TOUCH_IC_REGISTER_BANK_SIZE)
                {
                    nNextRegAddr = 0x00; 		
                    nNextRegBank = nRegBank + 1; // shift to read data from next register bank

                    PRINTF_ERR("nNextRegBank = 0x%x\n", nNextRegBank);	// add for debug

                    nLength = nSegmentLength;
                    nLeft -= nSegmentLength;
                    nOffset += nLength;
                }
                else // ((nRegAddr + nSegmentLength) > MAX_TOUCH_IC_REGISTER_BANK_SIZE)
                {
                    nNextRegAddr = 0x00;
                    nNextRegBank = nRegBank + 1; // shift to read data from next register bank

                    PRINTF_INFO("nNextRegBank = 0x%x\n", nNextRegBank);	// add for debug
                    
                    nOver = (nRegAddr + nSegmentLength) - MAX_TOUCH_IC_REGISTER_BANK_SIZE;

                    PRINTF_ERR("nOver = 0x%x\n", nOver);	// add for debug

                    nLength = nSegmentLength - nOver; 		
                    nLeft -= nLength;
                    nOffset += nLength;
                }
            }
            else
            {
                if ((nRegAddr + nLeft) < MAX_TOUCH_IC_REGISTER_BANK_SIZE)
                {
                    nNextRegAddr = nRegAddr + nLeft; 
                    
                    PRINTF_ERR("nNextRegAddr = 0x%x\n", nNextRegAddr);	// add for debug

                    nLength = nLeft;
                    nLeft = 0;
//                    nOffset += nLength;
                }
                else if ((nRegAddr + nLeft) == MAX_TOUCH_IC_REGISTER_BANK_SIZE)
                {
                    nNextRegAddr = 0x00; 		
                    nNextRegBank = nRegBank + 1; // shift to read data from next register bank

                    PRINTF_ERR("nNextRegBank = 0x%x\n", nNextRegBank);	// add for debug

                    nLength = nLeft;
                    nLeft = 0;
//                    nOffset += nLength;
                }
                else // ((nRegAddr + nLeft) > MAX_TOUCH_IC_REGISTER_BANK_SIZE)
                {
                    nNextRegAddr = 0x00;
                    nNextRegBank = nRegBank + 1; // shift to read data from next register bank

                    PRINTF_ERR("nNextRegBank = 0x%x\n", nNextRegBank);	// add for debug
                    
                    nOver = (nRegAddr + nLeft) - MAX_TOUCH_IC_REGISTER_BANK_SIZE;

                    PRINTF_ERR("nOver = 0x%x\n", nOver);	// add for debug

                    nLength = nLeft - nOver; 		
                    nLeft -= nLength;
                    nOffset += nLength;
                }
            }

            rc = i2c_master_send(g_I2cClient, &szWriteBuf[0], 3);
            if (rc < 0)
            {
                PRINTF_ERR("IicSegmentReadDataByDbBus() -> i2c_master_send() error %d\n", rc);

                return rc;
            }

            rc = i2c_master_recv(g_I2cClient, pReadBuf, nLength);
            if (rc < 0)
            {
                PRINTF_ERR("IicSegmentReadDataByDbBus() -> i2c_master_recv() error %d\n", rc);

                return rc;
            }
            else
            {
                nReadSize = nReadSize + nLength;
            }
        }
#endif //CONFIG_ENABLE_DMA_IIC
        g_I2cClient->addr = nAddrBefore;
    }
    else
    {
        PRINTF_ERR("i2c client is NULL\n");
    }
#endif
    
    return nReadSize;
}

s32 IicSegmentReadDataBySmBus(u16 nAddr, u8* pBuf, u16 nSize, u16 nMaxI2cLengthLimit)
{
    s32 rc = 0;
    u16 nLeft = nSize;
    u16 nOffset = 0;
    u16 nReadSize = 0;
    u8  szWriteBuf[3] = {0};

#if defined(CONFIG_TOUCH_DRIVER_RUN_ON_SPRD_PLATFORM) || defined(CONFIG_TOUCH_DRIVER_RUN_ON_QCOM_PLATFORM)
    struct i2c_msg msgs[2] =
    {
        {
            .addr = SLAVE_I2C_ID_DWI2C,
            .flags = 0, // write flag
            .len = 3,
            .buf = szWriteBuf,
        },
        {
            .addr = SLAVE_I2C_ID_DWI2C,
            .flags =  I2C_M_RD, // read flag
        },
    };

    // If everything went ok (i.e. 1 msg transmitted), return #bytes transmitted, else error code. 
    if (g_I2cClient != NULL)
    {
        while (nLeft > 0)
        {
            szWriteBuf[0] = 0x53;
            szWriteBuf[1] = ((nAddr + nOffset) >> 8) & 0xFF;
            szWriteBuf[2] = (nAddr + nOffset) & 0xFF;

            msgs[1].buf = &pBuf[nOffset];

            if (nLeft > nMaxI2cLengthLimit)
            {
                msgs[1].len = nMaxI2cLengthLimit;
                nLeft -= nMaxI2cLengthLimit;
                nOffset += msgs[1].len;
            }
            else
            {
                msgs[1].len = nLeft;
                nLeft = 0;
//                nOffset += msgs[1].len;
            }

            rc = i2c_transfer(g_I2cClient->adapter, &msgs[0], 2);
            if (rc == 2)
            {
                nReadSize = nReadSize + msgs[1].len;
            }
            else // rc < 0
            {
                PRINTF_ERR("IicSegmentReadDataBySmBus() -> i2c_transfer() error %d\n", rc);
                
                return rc;
            }
        }
    }
    else
    {
        PRINTF_ERR("i2c client is NULL\n");
    }
#elif defined(CONFIG_TOUCH_DRIVER_RUN_ON_MTK_PLATFORM)
    if (g_I2cClient != NULL)
    {
        //u8 *pReadBuf = NULL;
        u16 nLength = 0;
        u8 nAddrBefore = g_I2cClient->addr;
        
        g_I2cClient->addr = SLAVE_I2C_ID_DWI2C;

#ifdef CONFIG_ENABLE_DMA_IIC
        while (nLeft > 0)
        {
            s32 i = 0;

            szWriteBuf[0] = 0x53;
            szWriteBuf[1] = ((nAddr + nOffset) >> 8) & 0xFF;
            szWriteBuf[2] = (nAddr + nOffset) & 0xFF;

            if (nLeft > nMaxI2cLengthLimit)
            {
                nLength = nMaxI2cLengthLimit;
                nLeft -= nMaxI2cLengthLimit;
            }
            else
            {
                nLength = nLeft;
                nLeft = 0;
            }
            
            g_I2cClient->ext_flag = g_I2cClient->ext_flag & (~I2C_DMA_FLAG);
            rc = i2c_master_send(g_I2cClient, &szWriteBuf[0], 3);
            if (rc < 0)
            {
                PRINTF_ERR("IicSegmentReadDataBySmBus() -> i2c_master_send() error %d\n", rc);

                return rc;
            }

            g_I2cClient->ext_flag = g_I2cClient->ext_flag | I2C_DMA_FLAG;
            rc = i2c_master_recv(g_I2cClient, (unsigned char *)I2CDMABuf_pa, nLength);
            if (rc < 0)
            {
                PRINTF_ERR("IicSegmentReadDataBySmBus() -> i2c_master_recv() error %d\n", rc);

                return rc;
            }
            else
            {
                for (i = 0; i < nLength; i ++)
                {
                    pBuf[i+nOffset] = I2CDMABuf_va[i];
                }
                nOffset += nLength;

                nReadSize = nReadSize + nLength;
            }
        }
#else
        while (nLeft > 0)
        {
            szWriteBuf[0] = 0x53;
            szWriteBuf[1] = ((nAddr + nOffset) >> 8) & 0xFF;
            szWriteBuf[2] = (nAddr + nOffset) & 0xFF;

            pReadBuf = &pBuf[nOffset];

            if (nLeft > nMaxI2cLengthLimit)
            {
                nLength = nMaxI2cLengthLimit;
                nLeft -= nMaxI2cLengthLimit;
                nOffset += nLength;
            }
            else
            {
                nLength = nLeft;
                nLeft = 0;
//                nOffset += nLength;
            }
            
            rc = i2c_master_send(g_I2cClient, &szWriteBuf[0], 3);
            if (rc < 0)
            {
                PRINTF_ERR("IicSegmentReadDataBySmBus() -> i2c_master_send() error %d\n", rc);

                return rc;
            }

            rc = i2c_master_recv(g_I2cClient, pReadBuf, nLength);
            if (rc < 0)
            {
                PRINTF_ERR("IicSegmentReadDataBySmBus() -> i2c_master_recv() error %d\n", rc);

                return rc;
            }
            else
            {
                nReadSize = nReadSize + nLength;
            }
        }
#endif //CONFIG_ENABLE_DMA_IIC
        g_I2cClient->addr = nAddrBefore;
   }
   else
   {
       PRINTF_ERR("i2c client is NULL\n");
   }
#endif
	
   return nReadSize;
}

void mstpMemSet(void *pDst, s8 nVal, u32 nSize)
{
    memset(pDst, nVal, nSize);
}

void mstpMemCopy(void *pDst, void *pSource, u32 nSize)
{
    memcpy(pDst, pSource, nSize);
}

void mstpDelay(u32 nTime)
{
    mdelay(nTime);
}

//------------------------------------------------------------------------------//
