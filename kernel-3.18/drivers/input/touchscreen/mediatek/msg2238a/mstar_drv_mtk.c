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
 * @file    mstar_drv_mtk.c
 *
 * @brief   This file defines the interface of touch screen
 *
 *
 */

/*=============================================================*/
// INCLUDE FILE
/*=============================================================*/

#include <linux/interrupt.h>
#include <linux/i2c.h>
#include <linux/sched.h>
#include <linux/kthread.h>
#include <linux/rtpm_prio.h>
#include <linux/wait.h>
#include <linux/time.h>
#include <linux/delay.h>
//#include <linux/hwmsen_helper.h>
//#include <linux/hw_module_info.h>

#include <linux/fs.h>
#include <asm/uaccess.h>
#include <linux/namei.h>
#include <linux/vmalloc.h>

//#include <mach/mt_pm_ldo.h>
//#include <mach/mt_typedefs.h>
//#include <mach/mt_boot.h>
//#include <mach/mt_gpio.h>

//#include <cust_eint.h>

#include "tpd.h"
//#include "cust_gpio_usage.h"

#include "mstar_drv_platform_interface.h"
#include "mstar_drv_self_fw_control.h"
//+add by hzb
//#include <ontim/ontim_dev_dgb.h>
//+add by hzb for dsm
#ifdef CONFIG_ONTIM_DSM	

#include <ontim/ontim_dsm.h>

struct dsm_dev msg2238a_dsm_dev=
{
	.type=OMTIM_DSM_DEV_TYPE_IO,
	.id=OMTIM_DSM_DEV_ID_TP,
	.name="msg2238a TP",
	.buff_size=1024,
};
struct dsm_client *msg2238a_dsm_client=NULL;
int msg2238a_upgread_ret=0;
u32  msg2238a_irq_count=0;
u32  msg2238a_irq_run_count=0;
#endif
//-add by hzb for dsm

//static char msg2238a_version[]="msg2238a ontim ver 1.0";
char msg2238a_vendor_name[50]="each-msg2238a";
char msg2238a_wakeup_enable=0;
//DEV_ATTR_DECLARE(touch_screen)
//DEV_ATTR_DEFINE("version",msg2238a_version)
//DEV_ATTR_DEFINE("vendor",msg2238a_vendor_name)
//DEV_ATTR_VAL_DEFINE("wakeup_enable",&msg2238a_wakeup_enable,ONTIM_DEV_ARTTR_TYPE_VAL_8BIT)
//+add by hzb for dsm
#ifdef CONFIG_ONTIM_DSM	
DEV_ATTR_VAL_DEFINE("irq_count",&msg2238a_irq_count,ONTIM_DEV_ARTTR_TYPE_VAL_RO)
DEV_ATTR_VAL_DEFINE("irq_run_count",&msg2238a_irq_run_count,ONTIM_DEV_ARTTR_TYPE_VAL_RO)
#endif
//-add by hzb for dsm
//DEV_ATTR_DECLARE_END;
//ONTIM_DEBUG_DECLARE_AND_INIT(touch_screen,touch_screen,8);
//-add by hzb

extern bool tp_probe_ok;//add by liuwei

/*=============================================================*/
// CONSTANT VALUE DEFINITION
/*=============================================================*/

#define MSG_TP_IC_NAME "msg2xxx" //"msg21xxA" or "msg22xx" or "msg26xxM" or "msg28xx" /* Please define the mstar touch ic name based on the mutual-capacitive ic or self capacitive ic that you are using */
#define I2C_BUS_ID   (1)       // i2c bus id : 0 or 1

#define TPD_OK (0)

/*=============================================================*/
// EXTERN VARIABLE DECLARATION
/*=============================================================*/

#ifdef CONFIG_TP_HAVE_KEY
extern const int g_TpVirtualKey[];

#ifdef CONFIG_ENABLE_REPORT_KEY_WITH_COORDINATE
extern const int g_TpVirtualKeyDimLocal[][4];
#endif //CONFIG_ENABLE_REPORT_KEY_WITH_COORDINATE
#endif //CONFIG_TP_HAVE_KEY

extern struct tpd_device *tpd;

/*=============================================================*/
// LOCAL VARIABLE DEFINITION
/*=============================================================*/

/*
#if (defined(TPD_WARP_START) && defined(TPD_WARP_END))
static int tpd_wb_start_local[TPD_WARP_CNT] = TPD_WARP_START;
static int tpd_wb_end_local[TPD_WARP_CNT] = TPD_WARP_END;
#endif

#if (defined(TPD_HAVE_CALIBRATION) && !defined(TPD_CUSTOM_CALIBRATION))
static int tpd_calmat_local[8] = TPD_CALIBRATION_MATRIX;
static int tpd_def_calmat_local[8] = TPD_CALIBRATION_MATRIX;
#endif
*/
struct i2c_client *g_I2cClient = NULL;

//static int boot_mode = 0;

/*=============================================================*/
// FUNCTION DECLARATION
/*=============================================================*/

/*=============================================================*/
// FUNCTION DEFINITION
/*=============================================================*/
extern void DrvFwCtrlGetCustomerFirmwareVersionCutdown(u16 *pMajor, u16 *pMinor);
extern u16 _DrvFwCtrlMsg22xxGetSwId(EmemType_e eEmemType);
extern void DrvPlatformLyrTouchDeviceResetHw(void);
/* probe function is used for matching and initializing input device */
static int /*__devinit*/ tpd_probe(struct i2c_client *client, const struct i2c_device_id *id)
{
    s32 nRetVal = 0;
    u16 nMajor = 0, nMinor = 0;
    u8 msg2238a_fw_version[20]={'\0'};


    //if(CHECK_THIS_DEV_DEBUG_AREADY_EXIT()==0)
    //{
    //   return -EIO;
    //}

    TPD_DMESG("TPD probe\n");   
    
    if (client == NULL)
    {
        TPD_DMESG("i2c client is NULL\n");
        return -1;
    }
    g_I2cClient = client;
    
    MsDrvInterfaceTouchDeviceSetIicDataRate(g_I2cClient, 100000); // 100 KHz

    nRetVal = MsDrvInterfaceTouchDeviceProbe(g_I2cClient, id);
    if (nRetVal == 0) // If probe is success, then enable the below flag.
    {
        tpd_load_status = 1;
    }    
    else
    {
	return -1;
    }	
    DrvFwCtrlGetCustomerFirmwareVersionCutdown(&nMajor, &nMinor);
    {
	Msg22xxSwId_e eSwId = MSG22XX_SW_ID_UNDEFINED;
	eSwId = _DrvFwCtrlMsg22xxGetSwId(EMEM_INFO);
	DrvPlatformLyrTouchDeviceResetHw();
        if (eSwId == MSG22XX_SW_ID_XXXX)
	{
		sprintf(msg2238a_vendor_name,"%s","each-msg2238a");
	}
    }
    sprintf(msg2238a_fw_version,"-fw:%d.%d",nMajor,nMinor);
    strcat(msg2238a_vendor_name,msg2238a_fw_version);

#ifdef CONFIG_ENABLE_ESD_CHECK
	esd_check_init();
#endif

    TPD_DMESG("TPD probe done\n");
    
    //REGISTER_AND_INIT_ONTIM_DEBUG_FOR_THIS_DEV();

#ifdef CONFIG_ONTIM_DSM	
    tp_probe_ok=1;//add by liuwei
	msg2238a_dsm_client=dsm_register_client (&msg2238a_dsm_dev);
#endif

    return TPD_OK;   
}

static int tpd_detect(struct i2c_client *client, struct i2c_board_info *info) 
{
    strcpy(info->type, TPD_DEVICE);    
//    strcpy(info->type, MSG_TP_IC_NAME);
    
    return TPD_OK;
}

static int /*__devexit*/ tpd_remove(struct i2c_client *client)
{   
    TPD_DEBUG("TPD removed\n");
    
    MsDrvInterfaceTouchDeviceRemove(client);
    
    return TPD_OK;
}

//static struct i2c_board_info __initdata i2c_tpd = {I2C_BOARD_INFO(MSG_TP_IC_NAME, (0x4C>>1))};

/* The I2C device list is used for matching I2C device and I2C device driver. */
static const struct i2c_device_id tpd_device_id[] =
{
    {MSG_TP_IC_NAME, 0},
    {}, /* should not omitted */ 
};

MODULE_DEVICE_TABLE(i2c, tpd_device_id);

static const struct of_device_id tpd_of_match[] = {
	{.compatible = "mediatek,cap_touch"},
	{},
};

static struct i2c_driver tpd_i2c_driver = {
    .driver = {
        .name = MSG_TP_IC_NAME,
	 .of_match_table = tpd_of_match,
    },
    .probe = tpd_probe,
    //.remove = __devexit_p(tpd_remove),
    .remove = tpd_remove,
    .id_table = tpd_device_id,
    .detect = tpd_detect,
};

static int tpd_local_init(void)
{  
	int retval;
/*
    // Software reset mode will be treated as normal boot
    boot_mode = get_boot_mode();
    if (boot_mode == 3) 
    {
        boot_mode = NORMAL_BOOT;    
    }
*/
	tpd->reg = regulator_get(tpd->tpd_dev, "vtouch");
	retval = regulator_set_voltage(tpd->reg, 2800000, 2800000);
	if (retval != 0) {
		TPD_DMESG("Failed to set reg-vgp6 voltage: %d\n", retval);
		return -1;
	}

    if (i2c_add_driver(&tpd_i2c_driver) != 0)
    {
        TPD_DMESG("unable to add i2c driver.\n");
         
        return -1;
    }
    
    if (tpd_load_status == 0) 
    {
        TPD_DMESG("add error touch panel driver.\n");

        i2c_del_driver(&tpd_i2c_driver);
        return -1;
    }

#ifdef CONFIG_TP_HAVE_KEY
#ifdef CONFIG_ENABLE_REPORT_KEY_WITH_COORDINATE     
    // initialize tpd button data
	tpd_button_setting(3, (void *)g_TpVirtualKey, (void *)g_TpVirtualKeyDimLocal); /*MAX_KEY_NUM*/
#endif //CONFIG_ENABLE_REPORT_KEY_WITH_COORDINATE  
#endif //CONFIG_TP_HAVE_KEY  

/*
#if (defined(TPD_WARP_START) && defined(TPD_WARP_END))    
    TPD_DO_WARP = 1;
    memcpy(tpd_wb_start, tpd_wb_start_local, TPD_WARP_CNT*4);
    memcpy(tpd_wb_end, tpd_wb_start_local, TPD_WARP_CNT*4);
#endif 

#if (defined(TPD_HAVE_CALIBRATION) && !defined(TPD_CUSTOM_CALIBRATION))
    memcpy(tpd_calmat, tpd_def_calmat_local, 8*4);
    memcpy(tpd_def_calmat, tpd_def_calmat_local, 8*4);    
#endif  
*/
    TPD_DMESG("TPD init done %s, %d\n", __FUNCTION__, __LINE__);  
        
    return TPD_OK; 
}

static void tpd_resume(struct device *h)
{
    TPD_DMESG("TPD wake up\n");
    
    MsDrvInterfaceTouchDeviceResume(h);
    
    TPD_DMESG("TPD wake up done\n");
}

static void tpd_suspend(struct device *h)
{
    TPD_DMESG("TPD enter sleep\n");
	
#ifdef CONFIG_ENABLE_ESD_CHECK
	esd_check_disable();
#endif

//+add by hzb for dsm
#ifdef CONFIG_ONTIM_DSM	
 	if ( (msg2238a_dsm_client ) && dsm_client_ocuppy(msg2238a_dsm_client))
 	{
		int error=OMTIM_DSM_TP_INFO;
 		if ((msg2238a_dsm_client->dump_buff) && (msg2238a_dsm_client->buff_size)&&(msg2238a_dsm_client->buff_flag == OMTIM_DSM_BUFF_OK))
 		{
			msg2238a_dsm_client->used_size = sprintf(msg2238a_dsm_client->dump_buff,"Type=%d; ID=%d; error_id=%d;  CTP info:%s; irq_count = %d; irq_run_count =%d\n",msg2238a_dsm_client->client_type,msg2238a_dsm_client->client_id,error,msg2238a_vendor_name,msg2238a_irq_count,msg2238a_irq_run_count );
			dsm_client_notify(msg2238a_dsm_client,error);
 		}
 	}
	else
	{
		printk(KERN_ERR "%s: dsm ocuppy error!!!",__func__);
	}
#endif
//-add by hzb for dsm

    MsDrvInterfaceTouchDeviceSuspend(h);

    TPD_DMESG("TPD enter sleep done\n");
} 

static struct tpd_driver_t tpd_device_driver = {
     .tpd_device_name = MSG_TP_IC_NAME,
     .tpd_local_init = tpd_local_init,
     .suspend = tpd_suspend,
     .resume = tpd_resume,
#ifdef CONFIG_TP_HAVE_KEY
#ifdef CONFIG_ENABLE_REPORT_KEY_WITH_COORDINATE
     .tpd_have_button = 1,
#else
     .tpd_have_button = 0,
#endif //CONFIG_ENABLE_REPORT_KEY_WITH_COORDINATE        
#endif //CONFIG_TP_HAVE_KEY        
};

static int __init tpd_driver_init(void) 
{
    TPD_DMESG("mstar touch panel driver init\n");

	printk(KERN_ERR"mstar touch panel");
   // i2c_register_board_info(I2C_BUS_ID, &i2c_tpd, 1);
    tpd_get_dts_info();
    if (tpd_driver_add(&tpd_device_driver) < 0)
    {
        TPD_DMESG("TPD add driver failed\n");
    }
     
    return 0;
}
 
static void __exit tpd_driver_exit(void) 
{
    TPD_DMESG("touch panel driver exit\n");
    
    tpd_driver_remove(&tpd_device_driver);
}

module_init(tpd_driver_init);
module_exit(tpd_driver_exit);
MODULE_LICENSE("GPL");
