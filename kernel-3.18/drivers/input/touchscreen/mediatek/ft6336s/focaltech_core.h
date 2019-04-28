/*
 *
 * FocalTech TouchScreen driver.
 * 
 * Copyright (c) 2010-2015, Focaltech Ltd. All rights reserved.
 *
 * This software is licensed under the terms of the GNU General Public
 * License version 2, as published by the Free Software Foundation, and
 * may be copied, distributed, and modified under those terms.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 */
#ifndef __LINUX_FTXXXX_H__
#define __LINUX_FTXXXX_H__
 /*******************************************************************************
*
* File Name: focaltech_core.h
*
*    Author: Xu YongFeng
*
*   Created: 2015-01-29
*
*  Abstract:
*
* Reference:
*
*******************************************************************************/

/*******************************************************************************
* 1.Included header files
*******************************************************************************/
#include <linux/version.h>
#if (LINUX_VERSION_CODE < KERNEL_VERSION(3, 18, 19))
#include <pmic_drv.h>
#endif
#include "tpd.h"
//#include "tpd_custom_fts.h"
#if (LINUX_VERSION_CODE < KERNEL_VERSION(3, 18, 19))
#include "cust_gpio_usage.h"
#endif
#include <linux/hrtimer.h>
#include <linux/string.h>
#include <linux/vmalloc.h>
#include <linux/i2c.h>
#include <linux/input.h>
#include <linux/slab.h>
#include <linux/gpio.h>
#include <linux/bitops.h>
#include <linux/delay.h>
#include <linux/semaphore.h>
#include <linux/mutex.h>
#include <linux/syscalls.h>
#include <linux/byteorder/generic.h>
#include <linux/interrupt.h>
#include <linux/time.h>
#include <linux/rtpm_prio.h>
#include <asm/unistd.h>
#if (LINUX_VERSION_CODE < KERNEL_VERSION(3, 18, 19))
#include <mach/mt_pm_ldo.h>
#include <mach/mt_typedefs.h>
#include <mach/mt_boot.h>
#endif
#include <mach/irqs.h>
#if (LINUX_VERSION_CODE < KERNEL_VERSION(3, 18, 19))
#include <cust_eint.h>
#endif
#include <linux/jiffies.h>
#ifdef CONFIG_HAS_EARLYSUSPEND
	#include <linux/earlysuspend.h>
#endif
//#include <linux/version.h>
#include <linux/types.h>
#include <linux/sched.h>
#include <linux/kthread.h>
#include <linux/kernel.h>
#include <linux/module.h>
#include <linux/init.h>
#include <linux/fs.h>
#include <linux/cdev.h>
#include <linux/device.h>
#include <asm/uaccess.h>
#include <linux/mount.h>
#include <linux/unistd.h>
#include <linux/proc_fs.h>
#include <linux/netdevice.h>
#include <../fs/proc/internal.h>
/*******************************************************************************
* Private constant and macro definitions using #define
*******************************************************************************/

/**********************Custom define begin**********************************************/


#if (LINUX_VERSION_CODE > KERNEL_VERSION(3, 8, 0))
	#if defined(MODULE) || defined(CONFIG_HOTPLUG)
		#define __devexit_p(x) 				x
	#else
		#define __devexit_p(x) 				NULL
	#endif
	// Used for HOTPLUG
	#define __devinit        					__section(.devinit.text) __cold notrace
	#define __devinitdata    					__section(.devinit.data)
	#define __devinitconst   					__section(.devinit.rodata)
	#define __devexit        					__section(.devexit.text) __exitused __cold notrace
	#define __devexitdata    					__section(.devexit.data)
	#define __devexitconst   					__section(.devexit.rodata)
#endif


#define TPD_POWER_SOURCE_CUSTOM         	PMIC_APP_CAP_TOUCH_VDD//MT6323_POWER_LDO_VGP1
#define IIC_PORT                   					1				// MT6572: 1  MT6589:0 , Based on the I2C index you choose for TPM
#define TPD_HAVE_BUTTON									// if have virtual key,need define the MACRO
#define TPD_BUTTON_HEIGH        				(40)  			// 100
#define TPD_KEY_COUNT           				3    				// 4
#define TPD_KEYS                					{ KEY_BACK, KEY_HOMEPAGE, KEY_MENU}
#define TPD_KEYS_DIM            					{{80,900,20,TPD_BUTTON_HEIGH}, {240,900,20,TPD_BUTTON_HEIGH}, {400,900,20,TPD_BUTTON_HEIGH}}
#define FT_ESD_PROTECT  									0
/*********************Custom Define end*************************************************/
#define MT_PROTOCOL_B
#define A_TYPE												2
#define TPD_TYPE_CAPACITIVE
#define TPD_TYPE_RESISTIVE
#define TPD_POWER_SOURCE    
#define TPD_NAME    							"FTS"
#define TPD_I2C_NUMBER           				0
#define TPD_WAKEUP_TRIAL         				60
#define TPD_WAKEUP_DELAY         				100
#define TPD_VELOCITY_CUSTOM_X 				15
#define TPD_VELOCITY_CUSTOM_Y 				20

#define CFG_MAX_TOUCH_POINTS				10
#define FT_FW_NAME_MAX_LEN				50
#define TPD_DELAY                					(2*HZ/100)
#define TPD_RES_X                					480//1080//480
#define TPD_RES_Y                					854//1280//800
#define TPD_CALIBRATION_MATRIX  			{962,0,0,0,1600,0,0,0};
#define FT_PROXIMITY_ENABLE				0
//#define TPD_AUTO_UPGRADE
//#define TPD_HAVE_CALIBRATION
//#define TPD_HAVE_TREMBLE_ELIMINATION
//#define TPD_CLOSE_POWER_IN_SLEEP
/******************************************************************************/
/* Chip Device Type */
#define IC_FT5X06							0				/* x=2,3,4 */
#define IC_FT5606							1				/* ft5506/FT5606/FT5816 */
#define IC_FT5316							2				/* ft5x16 */
#define IC_FT6208							3	  			/* ft6208 */
#define IC_FT6x06     							4				/* ft6206/FT6306 */
#define IC_FT5x06i     						5				/* ft5306i */
#define IC_FT5x36     							6				/* ft5336/ft5436/FT5436i */

/* Time of starting to report point after resetting, per the hardware data
 * sheet, in milliseconds. */
#define TRSI_MS  300

/*register address*/
#define FTS_REG_CHIP_ID	0xA3 /*chip ID*/

#define FTS_REG_FW_VER 0xA6 /*FW version*/
#define FTS_REG_VENDOR_ID 0xA8 /*TP vendor ID*/
#define FTS_REG_POINT_RATE 0x88 /*report rate*/
#define TPD_MAX_POINTS_2 2
#define TPD_MAX_POINTS_5 5
#define TPD_MAX_POINTS_10 10
#define AUTO_CLB_NEED 1
#define AUTO_CLB_NONEED	0
#define LEN_FLASH_ECC_MAX 0xFFFE
#define FTS_PACKET_LENGTH 120
/*******************************************************************************
* Private enumerations, structures and unions using typedef
*******************************************************************************/
/* IC info */
struct fts_Upgrade_Info 
{
        u8 CHIP_ID;
        u8 TPD_MAX_POINTS;
        u8 AUTO_CLB;
	 u16 delay_aa;			/* delay of write FT_UPGRADE_AA */
	 u16 delay_55;			/* delay of write FT_UPGRADE_55 */
	 u8 upgrade_id_1;		/* upgrade id 1 */
	 u8 upgrade_id_2;		/* upgrade id 2 */
	 u16 delay_readid;		/* delay of read id */
	 u16 delay_earse_flash; 	/* delay of earse flash */
};

/* See the data sheet for the details on
Pn_XH, Pn_XL, Pn_YH, and Pn_YL registers.*/

struct fts_touch_point_registers {
	u8 xh;
	u8 xl;
	u8 yh;
	u8 yl;
	u8 weight;  /* Note: Currently ignored by driver*/
	u8 misc;  /* Note: Currently ignored by driver*/
};

struct ts_event
{
	u8 status;
	struct fts_touch_point_registers touch_points[CFG_MAX_TOUCH_POINTS];
};

struct fts_ts_data {
	struct i2c_client *client;
	struct input_dev *input_dev;
	struct ts_event event;
	const struct ftxxxx_ts_platform_data *pdata;
	struct work_struct 	touch_event_work;
	struct workqueue_struct *ts_workqueue;
	struct regulator *vdd;
	struct regulator *vcc_i2c;
	char fw_name[FT_FW_NAME_MAX_LEN];
	bool loading_fw;
	u8 family_id;
	struct dentry *dir;
	u16 addr;
	bool suspended;
	char *ts_info;
	u8 *tch_data;
	u32 tch_data_len;
	u8 fw_ver[3];
	u8 fw_vendor_id;
#if defined(CONFIG_FB)
	struct notifier_block fb_notif;
#elif defined(CONFIG_HAS_EARLYSUSPEND)
	struct early_suspend early_suspend;
#endif
};
/*******************************************************************************
* Static variables
*******************************************************************************/




/*******************************************************************************
* Global variable or extern global variabls/functions
*******************************************************************************/
// Function Switchs: define to open,  comment to close
#define FT_TP									1
//#define CONFIG_TOUCHPANEL_PROXIMITY_SENSOR
//#if FT_ESD_PROTECT
//extern int apk_debug_flag;
//#endif

extern bool tp_probe_ok;
extern struct i2c_client *fts_i2c_client;
extern struct input_dev *fts_input_dev;
extern struct tpd_device *tpd;


extern struct fts_Upgrade_Info fts_updateinfo_curr;
int fts_rw_iic_drv_init(struct i2c_client *client);
void  fts_rw_iic_drv_exit(void);
void fts_get_upgrade_array(void);
extern int fts_write_reg(struct i2c_client *client, u8 regaddr, u8 regvalue);
extern int fts_read_reg(struct i2c_client *client, u8 regaddr, u8 *regvalue);
extern int fts_i2c_read(struct i2c_client *client, char *writebuf,int writelen, char *readbuf, int readlen);
extern int fts_i2c_write(struct i2c_client *client, char *writebuf, int writelen);
extern int HidI2c_To_StdI2c(struct i2c_client * client);
extern int fts_ctpm_fw_upgrade_with_app_file(struct i2c_client *client,
				       char *firmware_name);
extern int fts_ctpm_auto_clb(struct i2c_client *client);
extern int fts_ctpm_fw_upgrade_with_i_file(struct i2c_client *client);
extern int fts_ctpm_get_i_file_ver(void);
extern int fts_remove_sysfs(struct i2c_client *client);
extern void fts_release_apk_debug_channel(void);
extern int fts_ctpm_auto_upgrade(struct i2c_client *client);
#if FT_ESD_PROTECT
extern void esd_switch(s32 on);
#endif
extern void fts_reset_tp(int HighOrLow);
extern int fts_create_sysfs(struct i2c_client *client);
// Apk and ADB functions
extern int fts_create_apk_debug_channel(struct i2c_client * client);


/*******************************************************************************
* Static function prototypes
*******************************************************************************/

#define FTS_DEBUG
#ifdef FTS_DEBUG
	#define FTS_DBG(fmt, args...) 				printk("[FTS]" fmt, ## args)
#else
	#define FTS_DBG(fmt, args...) 				do{}while(0)
#endif
#endif
