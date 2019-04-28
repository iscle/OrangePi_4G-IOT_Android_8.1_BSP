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

 /*******************************************************************************
*
* File Name: focaltech_core.c
*
*    Author: Tsai HsiangYu
*
*   Created: 2015-03-02
*
*  Abstract:
*
* Reference:
*
*******************************************************************************/

/*******************************************************************************
* 1.Included header files
*******************************************************************************/
#include "focaltech_core.h"

#include <linux/interrupt.h>
#include <linux/i2c.h>
#include <linux/sched.h>
#include <linux/kthread.h>
#include <linux/rtpm_prio.h>
#include <linux/wait.h>
#include <linux/time.h>
#include <linux/delay.h>
#include <linux/input.h>
#include <linux/input/mt.h>
#include <linux/dma-mapping.h>
#include "mt_boot_common.h"
#include <linux/of.h>
#include <linux/of_irq.h>
#ifdef CONFIG_ONTIM_DSM	

#include <ontim/ontim_dsm.h>

struct dsm_dev focaltech_dsm_dev=
{
	.type=OMTIM_DSM_DEV_TYPE_IO,
	.id=OMTIM_DSM_DEV_ID_TP,
	.name="Focaltech Ft6336s TP",
	.buff_size=1024,
};
static struct dsm_client *focaltech_dsm_client=NULL;
#endif

/*******************************************************************************
* 2.Private constant and macro definitions using #define
*******************************************************************************/
/*register define*/
#define FTS_RESET_PIN										GPIO_CTP_RST_PIN
#define TPD_OK 												0
#define DEVICE_MODE 										0x00
#define GEST_ID 												0x01
#define TD_STATUS 											0x02
#define TOUCH1_XH 											0x03
#define TOUCH1_XL 											0x04
#define TOUCH1_YH 											0x05
#define TOUCH1_YL 											0x06
#define TOUCH2_XH 											0x09
#define TOUCH2_XL 											0x0A
#define TOUCH2_YH 											0x0B
#define TOUCH2_YL 											0x0C
#define TOUCH3_XH 											0x0F
#define TOUCH3_XL 											0x10
#define TOUCH3_YH 											0x11
#define TOUCH3_YL 											0x12
#define TPD_MAX_RESET_COUNT 								3


static char ft6336s_vendor_name[50]="ofilm-ft6336s";
#ifdef CONFIG_ONTIM_DSM	
static u32  ft6336s_irq_count=0;
static u32  ft6336s_irq_run_count=0;
#endif

#ifdef CONFIG_ONTIM_DSM	
DEV_ATTR_VAL_DEFINE("irq_count",&ft6336s_irq_count,ONTIM_DEV_ARTTR_TYPE_VAL_RO)
DEV_ATTR_VAL_DEFINE("irq_run_count",&ft6336s_irq_run_count,ONTIM_DEV_ARTTR_TYPE_VAL_RO)
#endif

#define CTP_RST    0
#define CTP_INT    1

unsigned int touch_irq = 0;

/*
for tp esd check
*/

#if FT_ESD_PROTECT
	#define TPD_ESD_CHECK_CIRCLE        						200
	static struct delayed_work gtp_esd_check_work;
	static struct workqueue_struct *gtp_esd_check_workqueue 	= NULL;
	static int count_irq 										= 0;
	static u8 run_check_91_register 							= 0;
	static unsigned long esd_check_circle 						= TPD_ESD_CHECK_CIRCLE;
	static void gtp_esd_check_func(struct work_struct *);
	
	
	
#endif

int apk_debug_flag=0;
/* PROXIMITY */
#ifdef TPD_PROXIMITY
	#include <linux/hwmsensor.h>
	#include <linux/hwmsen_dev.h>
	#include <linux/sensors_io.h>
#endif

#ifdef TPD_PROXIMITY
	#define APS_ERR(fmt,arg...)           							printk("<<proximity>> "fmt"\n",##arg)
	#define TPD_PROXIMITY_DEBUG(fmt,arg...) 					printk("<<proximity>> "fmt"\n",##arg)
	#define TPD_PROXIMITY_DMESG(fmt,arg...) 					printk("<<proximity>> "fmt"\n",##arg)
	static u8 tpd_proximity_flag;
	static u8 tpd_proximity_flag_one;
	static u8 tpd_proximity_detect = 1;	/* 0-->close ; 1--> far away*/
#endif
/*dma declare, allocate and release*/
#define __MSG_DMA_MODE__
#ifdef __MSG_DMA_MODE__
	u8 *g_dma_buff_va = NULL;
	dma_addr_t g_dma_buff_pa = 0;
#endif

#ifdef __MSG_DMA_MODE__

	static void msg_dma_alloct(void)
	{
		g_dma_buff_va = (u8 *)dma_alloc_coherent(NULL, 128, &g_dma_buff_pa, GFP_KERNEL);	// DMA size 4096 for customer
	    	if(!g_dma_buff_va)
		{
	        	TPD_DMESG("[DMA][Error] Allocate DMA I2C Buffer failed!\n");
	    	}
	}
	static void msg_dma_release(void){
		if(g_dma_buff_va)
		{
	     		dma_free_coherent(NULL, 128, g_dma_buff_va, g_dma_buff_pa);
	        	g_dma_buff_va = NULL;
	        	g_dma_buff_pa = 0;
			TPD_DMESG("[DMA][release] Allocate DMA I2C Buffer release!\n");
	    	}
	}
#endif
#ifdef TPD_HAVE_BUTTON 
	static int tpd_keys_local[TPD_KEY_COUNT] 			= TPD_KEYS;
	static int tpd_keys_dim_local[TPD_KEY_COUNT][4] 	= TPD_KEYS_DIM;
#endif
#if (defined(TPD_WARP_START) && defined(TPD_WARP_END))
	static int tpd_wb_start_local[TPD_WARP_CNT] 		= TPD_WARP_START;
	static int tpd_wb_end_local[TPD_WARP_CNT]   		= TPD_WARP_END;
#endif
#if (defined(TPD_HAVE_CALIBRATION) && !defined(TPD_CUSTOM_CALIBRATION))
	static int tpd_calmat_local[8]     					= TPD_CALIBRATION_MATRIX;
	static int tpd_def_calmat_local[8] 					= TPD_CALIBRATION_MATRIX;
#endif
/*******************************************************************************
* 3.Private enumerations, structures and unions using typedef
*******************************************************************************/

/* register driver and device info */ 
static const struct i2c_device_id fts_tpd_id[] 		= {{"fts",0},{}};
 
/*******************************************************************************
* 4.Static variables
*******************************************************************************/
struct i2c_client *fts_i2c_client 				= NULL;
 struct input_dev *fts_input_dev				=NULL;
struct task_struct *thread 						= NULL;
int up_flag									=0;
int up_count									=0;
static int tpd_flag 								= 0;
static int tpd_halt								=0;

/*******************************************************************************
* 5.Global variable or extern global variabls/functions
*******************************************************************************/



/*******************************************************************************
* 6.Static function prototypes
*******************************************************************************/
static void fts_release_all_finger(void);







static DECLARE_WAIT_QUEUE_HEAD(waiter);
static DEFINE_MUTEX(i2c_access);
static DEFINE_MUTEX(i2c_rw_access);
static int tpd_probe(struct i2c_client *client, const struct i2c_device_id *id);
static int tpd_detect (struct i2c_client *client, struct i2c_board_info *info);
static int tpd_remove(struct i2c_client *client);
static int touch_event_handler(void *unused);
extern void mt_eint_mask(unsigned int eint_num);
extern void mt_eint_unmask(unsigned int eint_num);
extern void mt_eint_set_hw_debounce(unsigned int eint_num, unsigned int ms);
extern void mt_eint_set_polarity(unsigned int eint_num, unsigned int pol);
extern unsigned int mt_eint_set_sens(unsigned int eint_num, unsigned int sens);
extern void mt_eint_registration(unsigned int eint_num, unsigned int flow, void (EINT_FUNC_PTR)(void), unsigned int is_auto_umask);



static const struct of_device_id ft6336s_dt_match[] = {
	{.compatible = "mediatek,cap_touch1"},
	{},
};
MODULE_DEVICE_TABLE(of, ft6336s_dt_match);

static struct i2c_driver tpd_i2c_driver = {
  .driver 		= {
	.of_match_table = of_match_ptr(ft6336s_dt_match),
  .name 		= "fts",
  },
  .probe 		= tpd_probe,
  .remove 	= __devexit_p(tpd_remove),
  .id_table 	= fts_tpd_id,
  .detect 		= tpd_detect,

 };



/*
* open/release/(I/O) control tpd device
*
*/
#ifdef VELOCITY_CUSTOM_fts
#include <linux/device.h>
#include <linux/miscdevice.h>
#include <asm/uaccess.h>

/* for magnify velocity */
#ifndef TPD_VELOCITY_CUSTOM_X
	#define TPD_VELOCITY_CUSTOM_X 							10
#endif
#ifndef TPD_VELOCITY_CUSTOM_Y
	#define TPD_VELOCITY_CUSTOM_Y 							10
#endif

#define TOUCH_IOC_MAGIC 									'A'
#define TPD_GET_VELOCITY_CUSTOM_X 						_IO(TOUCH_IOC_MAGIC,0)
#define TPD_GET_VELOCITY_CUSTOM_Y 						_IO(TOUCH_IOC_MAGIC,1)

int g_v_magnify_x =TPD_VELOCITY_CUSTOM_X;
int g_v_magnify_y =TPD_VELOCITY_CUSTOM_Y;


/************************************************************************
* Name: tpd_misc_open
* Brief: open node
* Input: node, file point
* Output: no
* Return: fail <0
***********************************************************************/
static int tpd_misc_open(struct inode *inode, struct file *file)
{
	return nonseekable_open(inode, file);
}
/************************************************************************
* Name: tpd_misc_release
* Brief: release node
* Input: node, file point
* Output: no
* Return: 0
***********************************************************************/
static int tpd_misc_release(struct inode *inode, struct file *file)
{
	return 0;
}
/************************************************************************
* Name: tpd_unlocked_ioctl
* Brief: I/O control for apk
* Input: file point, command
* Output: no
* Return: fail <0
***********************************************************************/

static long tpd_unlocked_ioctl(struct file *file, unsigned int cmd,
       unsigned long arg)
{

	void __user *data;
	
	long err = 0;
	
	if(_IOC_DIR(cmd) & _IOC_READ)
	{
		err = !access_ok(VERIFY_WRITE, (void __user *)arg, _IOC_SIZE(cmd));
	}
	else if(_IOC_DIR(cmd) & _IOC_WRITE)
	{
		err = !access_ok(VERIFY_READ, (void __user *)arg, _IOC_SIZE(cmd));
	}

	if(err)
	{
		printk("tpd: access error: %08X, (%2d, %2d)\n", cmd, _IOC_DIR(cmd), _IOC_SIZE(cmd));
		return -EFAULT;
	}

	switch(cmd)
	{
		case TPD_GET_VELOCITY_CUSTOM_X:
			data = (void __user *) arg;
			if(data == NULL)
			{
				err = -EINVAL;
				break;	  
			}			
			
			if(copy_to_user(data, &g_v_magnify_x, sizeof(g_v_magnify_x)))
			{
				err = -EFAULT;
				break;
			}				 
			break;

	   case TPD_GET_VELOCITY_CUSTOM_Y:
			data = (void __user *) arg;
			if(data == NULL)
			{
				err = -EINVAL;
				break;	  
			}			
			
			if(copy_to_user(data, &g_v_magnify_y, sizeof(g_v_magnify_y)))
			{
				err = -EFAULT;
				break;
			}				 
			break;


		default:
			printk("tpd: unknown IOCTL: 0x%08x\n", cmd);
			err = -ENOIOCTLCMD;
			break;
			
	}

	return err;
}


static struct file_operations tpd_fops = {
	.open 			= tpd_misc_open,
	.release 			= tpd_misc_release,
	.unlocked_ioctl 	= tpd_unlocked_ioctl,
};

static struct miscdevice tpd_misc_device = {
	.minor 			= MISC_DYNAMIC_MINOR,
	.name 			= "touch",
	.fops 			= &tpd_fops,
};
#endif

 
/************************************************************************
* Name: fts_i2c_read
* Brief: i2c read
* Input: i2c info, write buf, write len, read buf, read len
* Output: get data in the 3rd buf
* Return: fail <0
***********************************************************************/
int fts_i2c_read(struct i2c_client *client, char *writebuf,int writelen, char *readbuf, int readlen)
{
	int ret=0;

	// for DMA I2c transfer
	
	mutex_lock(&i2c_rw_access);
	
	if((NULL!=client) && (writelen>0) && (writelen<=128))
	{
		// DMA Write
		memcpy(g_dma_buff_va, writebuf, writelen);
		client->addr = (client->addr & I2C_MASK_FLAG) | I2C_DMA_FLAG;
		if((ret=i2c_master_send(client, (unsigned char *)g_dma_buff_pa, writelen))!=writelen)
			printk("i2c write failed\n");
		client->addr = client->addr & I2C_MASK_FLAG &(~ I2C_DMA_FLAG);
	}

	// DMA Read 

	if((NULL!=client) && (readlen>0) && (readlen<=128))

	{
		client->addr = (client->addr & I2C_MASK_FLAG) | I2C_DMA_FLAG;

		ret = i2c_master_recv(client, (unsigned char *)g_dma_buff_pa, readlen);

		memcpy(readbuf, g_dma_buff_va, readlen);

		client->addr = client->addr & I2C_MASK_FLAG &(~ I2C_DMA_FLAG);
	}
	
	mutex_unlock(&i2c_rw_access);
#ifdef CONFIG_ONTIM_DSM	
	if (tp_probe_ok && (ret<0))
	{
		int error=OMTIM_DSM_TP_READ_I2C_ERROR;
		int i=0;
	 	if ( (focaltech_dsm_client ) && dsm_client_ocuppy(focaltech_dsm_client))
	 	{
	 		if ((focaltech_dsm_client->dump_buff) && (focaltech_dsm_client->buff_size)&&(focaltech_dsm_client->buff_flag == OMTIM_DSM_BUFF_OK))
	 		{
				focaltech_dsm_client->used_size = sprintf(focaltech_dsm_client->dump_buff,"Type=%d; ID=%d; error_id=%d; CTP info:%s; read error = %d;{",focaltech_dsm_client->client_type,focaltech_dsm_client->client_id,error,ft6336s_vendor_name,ret );
				focaltech_dsm_client->used_size += sprintf(focaltech_dsm_client->dump_buff+focaltech_dsm_client->used_size,"Reg len=%d; ",writelen );
				if ( writelen && writebuf )
				{
					focaltech_dsm_client->used_size += sprintf(focaltech_dsm_client->dump_buff+focaltech_dsm_client->used_size,"Reg[" );
					for(i=0;i<writelen;i++)
					{
						focaltech_dsm_client->used_size += sprintf(focaltech_dsm_client->dump_buff+focaltech_dsm_client->used_size," 0x%x ",writebuf[i] );
					}
					focaltech_dsm_client->used_size += sprintf(focaltech_dsm_client->dump_buff+focaltech_dsm_client->used_size,"]" );
	 			}
				focaltech_dsm_client->used_size += sprintf(focaltech_dsm_client->dump_buff+focaltech_dsm_client->used_size,"Read len=%d}\n",readlen );
				dsm_client_notify(focaltech_dsm_client,error);
	 		}
	 	}
		else
		{
			printk(KERN_ERR "%s: dsm ocuppy error!!!",__func__);
		}
	}
#endif
	return ret;
}

/************************************************************************
* Name: fts_i2c_write
* Brief: i2c write
* Input: i2c info, write buf, write len
* Output: no
* Return: fail <0
***********************************************************************/
int fts_i2c_write(struct i2c_client *client, char *writebuf, int writelen)
{
	int ret=0;

	mutex_lock(&i2c_rw_access);
	
	if((NULL!=client) && (writelen>0) && (writelen<=128))
	{
		memcpy(g_dma_buff_va, writebuf, writelen);
		
		client->addr = (client->addr & I2C_MASK_FLAG) | I2C_DMA_FLAG;
		if((ret=i2c_master_send(client, (unsigned char *)g_dma_buff_pa, writelen))!=writelen)
			printk("i2c write failed\n");
		client->addr = client->addr & I2C_MASK_FLAG &(~ I2C_DMA_FLAG);
	}
	mutex_unlock(&i2c_rw_access);
#ifdef CONFIG_ONTIM_DSM	
	if (tp_probe_ok && (ret<0))
	{
		int error=OMTIM_DSM_TP_WRITE_I2C_ERROR;
		int i=0;
	 	if ( (focaltech_dsm_client ) && dsm_client_ocuppy(focaltech_dsm_client))
	 	{
	 		if ((focaltech_dsm_client->dump_buff) && (focaltech_dsm_client->buff_size)&&(focaltech_dsm_client->buff_flag == OMTIM_DSM_BUFF_OK))
	 		{
				focaltech_dsm_client->used_size = sprintf(focaltech_dsm_client->dump_buff,"Type=%d; ID=%d; error_id=%d; CTP info:%s; Write error = %d;{",focaltech_dsm_client->client_type,focaltech_dsm_client->client_id,error,ft6336s_vendor_name,ret );
				focaltech_dsm_client->used_size += sprintf(focaltech_dsm_client->dump_buff+focaltech_dsm_client->used_size,"Write len=%d; ",writelen );
				if ( writelen && writebuf )
				{
					focaltech_dsm_client->used_size += sprintf(focaltech_dsm_client->dump_buff+focaltech_dsm_client->used_size,"buff data [" );
					for(i=0;i<writelen;i++)
					{
						focaltech_dsm_client->used_size += sprintf(focaltech_dsm_client->dump_buff+focaltech_dsm_client->used_size," 0x%x ",writebuf[i] );
					}
					focaltech_dsm_client->used_size += sprintf(focaltech_dsm_client->dump_buff+focaltech_dsm_client->used_size,"]" );
	 			}
				focaltech_dsm_client->used_size += sprintf(focaltech_dsm_client->dump_buff+focaltech_dsm_client->used_size,"}\n" );
				dsm_client_notify(focaltech_dsm_client,error);
	 		}
	 	}
		else
		{
			printk(KERN_ERR "%s: dsm ocuppy error!!!",__func__);
		}
	}
#endif
	
	return ret;
}
/************************************************************************
* Name: fts_write_reg
* Brief: write register
* Input: i2c info, reg address, reg value
* Output: no
* Return: fail <0
***********************************************************************/
int fts_write_reg(struct i2c_client *client, u8 regaddr, u8 regvalue)
{
	unsigned char buf[2] = {0};

	buf[0] = regaddr;
	buf[1] = regvalue;

	return fts_i2c_write(client, buf, sizeof(buf));
}
/************************************************************************
* Name: fts_read_reg
* Brief: read register
* Input: i2c info, reg address, reg value
* Output: get reg value
* Return: fail <0
***********************************************************************/
int fts_read_reg(struct i2c_client *client, u8 regaddr, u8 *regvalue)
{

	return fts_i2c_read(client, &regaddr, 1, regvalue, 1);

}

/************************************************************************
* Name: fts_get_position_value
* Brief: Get a single position value from two registers
* Input: high and low registers with position
* Output: position value
* Return: position value
***********************************************************************/
static u16 fts_get_position_value(u8 high_register, u8 low_register)
{
	/* See data sheet for Pn_XH, Pn_XL, Pn_YH, and Pn_YL for explanation.*/
	return (((u16)high_register & 0x0F) << 8) | low_register;
}

/************************************************************************
* Name: fts_report_touch_point
* Brief: report the point information for a single touch point
* Input: byte buffer assumed to contain raw data from driver for a touch point,
*        pointer to counter of up events
* Output: touch point information reported to the system
* Return: 1 if this was a down event, 0 otherwise
***********************************************************************/
static int fts_report_touch_point(const struct fts_touch_point_registers *regs)
{
	u8 finger_id;
	u8 touch_event;

	finger_id = regs->yh >> 4;
	if (finger_id == 0x0F)
	{
		/* Invalid ID.*/
		return 0;
	}
	touch_event = regs->xh >> 6;

	input_mt_slot(tpd->dev, finger_id);

	if ((touch_event == 0) || (touch_event == 2))
	{
		/* Down event*/
		u16 x_pos, y_pos;

		x_pos = fts_get_position_value(regs->xh, regs->xl);
		y_pos = fts_get_position_value(regs->yh, regs->yl);

		/* 'true' to indicate down.*/
		input_mt_report_slot_state(tpd->dev, MT_TOOL_FINGER, true);
		input_report_abs(tpd->dev, ABS_MT_POSITION_X, x_pos);
		input_report_abs(tpd->dev, ABS_MT_POSITION_Y, y_pos);

		/* Note that we ignore the 'weight' and 'misc/area' data.
		   If we want to introduce this data, we'll want to track
		   in the fts_Upgrade_Info if the version of the hardware
		   supports this, as many chips don't.*/

		return 1;

	} else {

		/* 'false' to indicate up.*/

		input_mt_report_slot_state(tpd->dev, MT_TOOL_FINGER, false);
		return 0;
	}
}

 /************************************************************************
* Name: fts_handle_Touchdata
* Brief: report the point information
* Input: none
* Output: touch point information reported to the system
* Return: success is zero
***********************************************************************/

static int fts_handle_Touchdata(void)
{
	struct ts_event event;
	int ret = -1;
	int i = 0;
	int touch_point_count = 0;
	int down_count = 0;
	size_t i2c_read_size;
	const size_t touch_points_length = ARRAY_SIZE(event.touch_points);
	/*
	 * We skip the first two registers, DEV_MODE and undocumented, and
	 * start reading from TD_STATUS, to get the event information.
	 */
	u8 start_register = 0x02;
	if (tpd_halt)
	{
		TPD_DMESG( "tpd_touchinfo return ..\n");
		return 1;
	}

	i2c_read_size = sizeof(event);
	/*
	 * Reading over I2C is expensive.  We don't want to read touch point
	 * data which our device doesn't even support.
	 * Note that for devices with many touch points, it might be more
	 * efficient to first read the status byte, and then only read as
	 * many points as we have data for.  Experimentation for a device
	 * with a maximum of two touch points showed performing two separate
	 * I2C transactions to be slightly slower.  But if optimizing for a
	 * device with 10 touch points, this should be considered.
	 */
	i2c_read_size -= (touch_points_length - fts_updateinfo_curr.TPD_MAX_POINTS) *
			sizeof(event.touch_points[0]);
	mutex_lock(&i2c_access);
	ret = fts_i2c_read(fts_i2c_client, &start_register, sizeof(start_register),
			   (u8 *)&event, i2c_read_size);
	if (ret < 0) 
	{
		dev_err(&fts_i2c_client->dev, "%s read touchdata failed.\n",__func__);
		mutex_unlock(&i2c_access);
		return ret;
	}
	mutex_unlock(&i2c_access);	

	touch_point_count = event.status & 0x0F;
	if (touch_point_count > fts_updateinfo_curr.TPD_MAX_POINTS) {
		/*printk(KERN_ERR "ERROR: Claimed %d touch points in event", touch_point_count);*/
		return 1;
	}
	for (i = 0; i < touch_point_count; i++)
		down_count += fts_report_touch_point(&event.touch_points[i]);

	if (down_count == 0)
	{
		/* We didn't get any down events.  Sometimes we don't get
		 explicit up events for each touch point, so we release
		 every finger just to be sure.*/
		fts_release_all_finger();
	} else {
		input_report_key(tpd->dev, BTN_TOUCH, 1);
		input_sync(tpd->dev);
	}

	return 0;
}

#ifdef TPD_PROXIMITY
 /************************************************************************
* Name: tpd_read_ps
* Brief: read proximity value
* Input: no
* Output: no
* Return: 0
***********************************************************************/
int tpd_read_ps(void)
{
	tpd_proximity_detect;
	return 0;    
}
 /************************************************************************
* Name: tpd_get_ps_value
* Brief: get proximity value
* Input: no
* Output: no
* Return: 0
***********************************************************************/
static int tpd_get_ps_value(void)
{
	return tpd_proximity_detect;
}
 /************************************************************************
* Name: tpd_enable_ps
* Brief: enable proximity
* Input: enable or not
* Output: no
* Return: 0
***********************************************************************/
static int tpd_enable_ps(int enable)
{
	u8 state;
	int ret = -1;
	
	ret = fts_read_reg(fts_i2c_client, 0xB0,&state);
	if (ret<0) 
	{
		printk("[Focal][Touch] read value fail");
	}
	
	printk("[proxi_fts]read: 999 0xb0's value is 0x%02X\n", state);

	if (enable)
	{
		state |= 0x01;
		tpd_proximity_flag = 1;
		TPD_PROXIMITY_DEBUG("[proxi_fts]ps function is on\n");	
	}
	else
	{
		state &= 0x00;	
		tpd_proximity_flag = 0;
		TPD_PROXIMITY_DEBUG("[proxi_fts]ps function is off\n");
	}
	
	ret = fts_write_reg(fts_i2c_client, 0xB0,state);
	if (ret<0) 
	{
		printk("[Focal][Touch] write value fail");
	}
	TPD_PROXIMITY_DEBUG("[proxi_fts]write: 0xB0's value is 0x%02X\n", state);
	return 0;
}
 /************************************************************************
* Name: tpd_ps_operate
* Brief: operate function for proximity 
* Input: point, which operation, buf_in , buf_in len, buf_out , buf_out len, no use
* Output: buf_out
* Return: fail <0
***********************************************************************/
int tpd_ps_operate(void* self, uint32_t command, void* buff_in, int size_in,

		void* buff_out, int size_out, int* actualout)
{
	int err = 0;
	int value;
	hwm_sensor_data *sensor_data;
	TPD_DEBUG("[proxi_fts]command = 0x%02X\n", command);		
	
	switch (command)
	{
		case SENSOR_DELAY:
			if((buff_in == NULL) || (size_in < sizeof(int)))
			{
				APS_ERR("Set delay parameter error!\n");
				err = -EINVAL;
			}
			// Do nothing
			break;
		case SENSOR_ENABLE:
			if((buff_in == NULL) || (size_in < sizeof(int)))
			{
				APS_ERR("Enable sensor parameter error!\n");
				err = -EINVAL;
			}
			else
			{				
				value = *(int *)buff_in;
				if(value)
				{		
					if((tpd_enable_ps(1) != 0))
					{
						APS_ERR("enable ps fail: %d\n", err); 
						return -1;
					}
				}
				else
				{
					if((tpd_enable_ps(0) != 0))
					{
						APS_ERR("disable ps fail: %d\n", err); 
						return -1;
					}
				}
			}
			break;
		case SENSOR_GET_DATA:
			if((buff_out == NULL) || (size_out< sizeof(hwm_sensor_data)))
			{
				APS_ERR("get sensor data parameter error!\n");
				err = -EINVAL;
			}
			else
			{
				sensor_data = (hwm_sensor_data *)buff_out;				
				if((err = tpd_read_ps()))
				{
					err = -1;;
				}
				else
				{
					sensor_data->values[0] = tpd_get_ps_value();
					TPD_PROXIMITY_DEBUG("huang sensor_data->values[0] 1082 = %d\n", sensor_data->values[0]);
					sensor_data->value_divide = 1;
					sensor_data->status = SENSOR_STATUS_ACCURACY_MEDIUM;
				}					
			}
			break;
		default:
			APS_ERR("proxmy sensor operate function no this parameter %d!\n", command);
			err = -1;
			break;
	}
	return err;	
}
#endif
#if FT_ESD_PROTECT
void esd_switch(s32 on)
{
    if (1 == on) // switch on esd 
    {
            queue_delayed_work(gtp_esd_check_workqueue, &gtp_esd_check_work, esd_check_circle);
    }
    else // switch off esd
    {
            cancel_delayed_work(&gtp_esd_check_work);
    }
}
/************************************************************************
* Name: force_reset_guitar
* Brief: reset
* Input: no
* Output: no
* Return: 0
***********************************************************************/
static void force_reset_guitar(void)
{
    	s32 i;
    	s32 ret;

	disable_irq(touch_irq);

	tpd_gpio_output(CTP_RST,0);

	msleep(10);
    	TPD_DMESG("force_reset_guitar\n");

	hwPowerDown(MT6323_POWER_LDO_VGP1,  "TP");
	msleep(200);
	hwPowerOn(MT6323_POWER_LDO_VGP1, VOL_2800, "TP");

	msleep(10);
	TPD_DMESG(" fts ic reset\n");

	tpd_gpio_output(CTP_RST,1);
	tpd_gpio_as_int(CTP_INT);
	msleep(TRSI_MS);
	
#ifdef TPD_PROXIMITY
	if (FT_PROXIMITY_ENABLE == tpd_proximity_flag) 
	{
		tpd_enable_ps(FT_PROXIMITY_ENABLE);
	}
#endif
	enable_irq(touch_irq);
}

 
#define A3_REG_VALUE								0x54
#define RESET_91_REGVALUE_SAMECOUNT 				5
static u8 g_old_91_Reg_Value 							= 0x00;
static u8 g_first_read_91 								= 0x01;
static u8 g_91value_same_count 						= 0;
/************************************************************************
* Name: gtp_esd_check_func
* Brief: esd check function
* Input: struct work_struct
* Output: no
* Return: 0
***********************************************************************/
static void gtp_esd_check_func(struct work_struct *work)
{
	int i;
	int ret = -1;
	u8 data, data_old;
	u8 flag_error = 0;
	int reset_flag = 0;
	u8 check_91_reg_flag = 0;
	if (tpd_halt ) 
	{
		return;
	}
	if(is_update)
	{
		return;
	}
	if(apk_debug_flag) 
	{
		return;
	}

	run_check_91_register = 0;
	for (i = 0; i < 3; i++) 
	{
		ret = fts_read_reg(fts_i2c_client, 0xA3,&data);
		if (ret<0) 
		{
			printk("[FTS][Touch] read value fail");
		}
		if (ret==1 && A3_REG_VALUE==data) 
		{
		    break;
		}
	}

	if (i >= 3) 
	{
		force_reset_guitar();
		printk("FTS--tpd reset. i >= 3  ret = %d	A3_Reg_Value = 0x%02x\n ", ret, data);
		reset_flag = 1;
		goto FOCAL_RESET_A3_REGISTER;
	}

	ret = fts_read_reg(fts_i2c_client, 0x8F,&data);
	if (ret<0) 
	{
		printk("[FTS][Touch] read value fail");
	}
	printk("FTS 0x8F:%d, count_irq is %d\n", data, count_irq);
			
	flag_error = 0;
	if((count_irq - data) > 10) 
	{
		if((data+200) > (count_irq+10) )
		{
			flag_error = 1;
		}
	}
	
	if((data - count_irq ) > 10) 
	{
		flag_error = 1;		
	}
		
	if(1 == flag_error) 
	{	
		printk("FTS--tpd reset.1 == flag_error...data=%d	count_irq\n ", data, count_irq);
	    	force_reset_guitar();
		reset_flag = 1;
		goto FOCAL_RESET_INT;
	}

	run_check_91_register = 1;
	ret = fts_read_reg(fts_i2c_client, 0x91,&data);
	if (ret<0) 
	{
		printk("[FTS][Touch] read value fail");
	}
	printk("FTS focal---------91 register value = 0x%02x	old value = 0x%02x\n",	data, g_old_91_Reg_Value);
	if(0x01 == g_first_read_91) 
	{
		g_old_91_Reg_Value = data;
		g_first_read_91 = 0x00;
	} 
	else 
	{
		if(g_old_91_Reg_Value == data)
		{
			g_91value_same_count++;
			printk("\n FTS focal 91 value ==============, g_91value_same_count=%d\n", g_91value_same_count);
			if(RESET_91_REGVALUE_SAMECOUNT == g_91value_same_count) 
			{
				force_reset_guitar();
				printk("focal--tpd reset. g_91value_same_count = 5\n");
				g_91value_same_count = 0;
				reset_flag = 1;
			}
			
			esd_check_circle = TPD_ESD_CHECK_CIRCLE / 2;
			g_old_91_Reg_Value = data;
		} 
		else 
		{
			g_old_91_Reg_Value = data;
			g_91value_same_count = 0;
			esd_check_circle = TPD_ESD_CHECK_CIRCLE;
		}
	}
FOCAL_RESET_INT:
FOCAL_RESET_A3_REGISTER:
	count_irq=0;
	data=0;
	ret = fts_write_reg(fts_i2c_client, 0x8F,data);
	if (ret<0) 
	{
		printk("[FTS][Touch] write value fail");
	}
	if(0 == run_check_91_register)
	{
		g_91value_same_count = 0;
	}
	#ifdef TPD_PROXIMITY
	if( (1 == reset_flag) && ( FT_PROXIMITY_ENABLE == tpd_proximity_flag) )
	{
		if((tpd_enable_ps(FT_PROXIMITY_ENABLE) != 0))
		{
			APS_ERR("FTS enable ps fail\n"); 
			return -1;
		}
	}
	#endif
	// end esd check for count

    	if (!tpd_halt)
    	{
        	queue_delayed_work(gtp_esd_check_workqueue, &gtp_esd_check_work, esd_check_circle);
    	}

    	return;
}
#endif
 /************************************************************************
* Name: touch_event_handler
* Brief: interrupt event from TP, and read/report data to Android system 
* Input: no use
* Output: no
* Return: 0
***********************************************************************/
 static int touch_event_handler(void *unused)
 {
	struct sched_param param = { .sched_priority = RTPM_PRIO_TPD };
	sched_setscheduler(current, SCHED_RR, &param);
 
	#ifdef TPD_PROXIMITY
		int err;
		hwm_sensor_data sensor_data;
		u8 proximity_status;
	#endif
	
	do
	{
		 set_current_state(TASK_INTERRUPTIBLE); 
		 wait_event_interruptible(waiter,tpd_flag!=0);
						 
		 tpd_flag = 0;
			 
		 set_current_state(TASK_RUNNING);
#ifdef CONFIG_ONTIM_DSM	
		 ft6336s_irq_run_count++;
#endif
		 #ifdef TPD_PROXIMITY

			 if (tpd_proximity_flag == 1)
			 {

				ret = fts_read_reg(fts_i2c_client, 0xB0,&state);
				if (ret<0) 
				{
					printk("[Focal][Touch] read value fail");
				}
	           		TPD_PROXIMITY_DEBUG("proxi_fts 0xB0 state value is 1131 0x%02X\n", state);
				if(!(state&0x01))
				{
					tpd_enable_ps(1);
				}
				ret = fts_read_reg(fts_i2c_client, 0x01,&proximity_status);
				if (ret<0) 
				{
					printk("[Focal][Touch] read value fail");
				}
	            		TPD_PROXIMITY_DEBUG("proxi_fts 0x01 value is 1139 0x%02X\n", proximity_status);
				if (proximity_status == 0xC0)
				{
					tpd_proximity_detect = 0;	
				}
				else if(proximity_status == 0xE0)
				{
					tpd_proximity_detect = 1;
				}

				TPD_PROXIMITY_DEBUG("tpd_proximity_detect 1149 = %d\n", tpd_proximity_detect);
				if ((err = tpd_read_ps()))
				{
					TPD_PROXIMITY_DMESG("proxi_fts read ps data 1156: %d\n", err);	
				}
				sensor_data.values[0] = tpd_get_ps_value();
				sensor_data.value_divide = 1;
				sensor_data.status = SENSOR_STATUS_ACCURACY_MEDIUM;
			}  

		#endif
                #if FT_ESD_PROTECT
		esd_switch(0);
		apk_debug_flag = 1;
		#endif
		fts_handle_Touchdata();
		#if FT_ESD_PROTECT
		esd_switch(1);
		apk_debug_flag = 0;
		#endif
	} while (!kthread_should_stop());
	return 0;
 }
  /************************************************************************
* Name: fts_reset_tp
* Brief: reset TP
* Input: pull low or high
* Output: no
* Return: 0
***********************************************************************/
void fts_reset_tp(int HighOrLow)
{
	
	if(HighOrLow)
	{
		tpd_gpio_output(CTP_RST,1);
	}
	else
	{
		tpd_gpio_output(CTP_RST,0);
	}
	
}

/************************************************************************
 * Name: tpd_init_registers
 * Brief: Put the device's registers in the appropriate initial state
 * Input: i2c info
 * Output: None
 * Return: None
***********************************************************************/
static void tpd_init_registers(struct i2c_client *client)
{
	/*
	 * The undocumented firmware sets most of the registers to values
	 * that we want.  However, there are some (currently one) registers
	 * which don't have the value we want.  Since the firmware is
	 * undocumented, rather than modify the firmware, we initialize the
	 * registers here.
	 */

	int ret;
	/*
	 * Turn off device gesture calculations.
	 *
	 * Register 0xDO is ID_G_SPEC_GESTURE_ENABLE.  The documentation claims
	 * that the value of 0x00 is Disable, and 0x01 is Enable.  However, from
	 * testing with a WALT device, we see that setting this register to 0x01
	 * speeds up our ACTION_DOWN latency from about 70 milliseconds, to
	 * 15-20 milliseconds.  So we strongly suspect that 0x01 is actually
	 * disabling this on-device calculation, and giving us the results
	 * more quickly.  Modifying ID_G_PERIODACTIVE (register 0x88) results
	 * in non-linear latency changes with 0xD0 set to 0x00.  This is highly
	 * suggestive of an algorithm running on the device, and between this
	 * observation and the significant latency improvements, we conclude the
	 * documentation is incorrect, and we set this register to 0x01 for a
	 * better touchscreen experience.
	 */
	ret = fts_write_reg(client, 0xD0, 0x01);
	if (ret < 0) {
		/*
		 * We don't want to take drastic action here, as the
		 * touchscreen should still be usable, albeit a bit laggy.
		 * So we log this and continue.
		 */
		/*printk(KERN_ERR "Failed to set register 0xD0 to 0x01 (%d)", ret);*/
	}
}

   /************************************************************************
* Name: tpd_detect
* Brief: copy device name
* Input: i2c info, board info
* Output: no
* Return: 0
***********************************************************************/
 static int tpd_detect (struct i2c_client *client, struct i2c_board_info *info) 
 {
	 	strcpy(info->type, TPD_DEVICE);	
	  	return 0;
 }
/************************************************************************
* Name: tpd_eint_interrupt_handler
* Brief: deal with the interrupt event
* Input: no
* Output: no
* Return: no
***********************************************************************/
static irqreturn_t tpd_eint_interrupt_handler(unsigned irq, struct irq_desc *desc)
 {
	 TPD_DEBUG_PRINT_INT;
	 tpd_flag = 1;
	 #if FT_ESD_PROTECT
		count_irq ++;
	 #endif
#ifdef CONFIG_ONTIM_DSM	
	 ft6336s_irq_count++;
#endif
	 wake_up_interruptible(&waiter);
	return IRQ_HANDLED;
 }
/************************************************************************
* Name: fts_init_gpio_hw
* Brief: initial gpio
* Input: no
* Output: no
* Return: 0
***********************************************************************/
 static int fts_init_gpio_hw(void)
{

	int ret = 0;
	tpd_gpio_output(CTP_RST,1);
	return ret;
}
   
static int tpd_irq_registration(void)
{
        struct device_node *node = NULL;
        int ret = 0;
        u32 ints[2] = { 0, 0 };

        TPD_DMESG("Device Tree Tpd_irq_registration!");

        node = of_find_matching_node(node, touch_of_match);
        if (node) {
                of_property_read_u32_array(node, "debounce", ints, ARRAY_SIZE(ints));

                touch_irq = irq_of_parse_and_map(node, 0);
                ret =request_irq(touch_irq, (irq_handler_t) tpd_eint_interrupt_handler, IRQF_TRIGGER_FALLING,"TOUCH_PANEL-eint", NULL);
                if (ret > 0) {
                	ret = -1;
                        TPD_DMESG("tpd request_irq IRQ LINE NOT AVAILABLE!.");
               	}
        } else {
                TPD_DMESG("tpd request_irq can not find touch eint device node!.");
                ret = -1;
        }
        TPD_DMESG("[%s]irq:%d, debounce:%d-%d:", __func__, touch_irq, ints[0], ints[1]);
        return ret;
}

#define FTS_REG_FW_MIN_VER                                                                      0xB2
#define FTS_REG_FW_SUB_MIN_VER                                                          0xB3
#define   FTS_REG_FW_VENDOR_ID 0xA8
/************************************************************************
* Name: tpd_probe
* Brief: driver entrance function for initial/power on/create channel 
* Input: i2c info, device id
* Output: no
* Return: 0
***********************************************************************/
static int tpd_probe(struct i2c_client *client, const struct i2c_device_id *id)
{
	int retval = TPD_OK;
	char data;
	s32 errval=0;
	u8 fw_ver[3];
        u8 reg_addr;
        int err1;
	u8 ft6336s_fw_version[20]={'\0'};
	#ifdef TPD_PROXIMITY
		int err;
		struct hwmsen_object obj_ps;
	#endif
	
		fts_i2c_client = client;
		fts_input_dev=tpd->dev;
         #ifdef TPD_CLOSE_POWER_IN_SLEEP	 
		
	#else
		tpd_gpio_output(CTP_RST,1);
		tpd_gpio_output(CTP_RST,0);

		msleep(10);
		
	#endif	
   	
	// power on, need confirm with SA
	#ifdef TPD_POWER_SOURCE_CUSTOM
		tpd->reg = regulator_get(tpd->tpd_dev, "vtouch");
	        retval = regulator_set_voltage(tpd->reg, 2800000, 2800000);        /*set 2.8v*/
        	if (retval) {
		if (!IS_ERR(tpd->reg))
		regulator_put(tpd->reg);
                	TPD_DMESG("regulator_set_voltage(%d) failed!\n", retval);
                	return -1;
        	}	

		retval = regulator_enable(tpd->reg);       /*enable regulator*/
                if (retval)
		{
			regulator_put(tpd->reg);
			TPD_DMESG("regulator_enable() failed!\n");
			return -1;
		}
	#else
		hwPowerOn(MT65XX_POWER_LDO_VGP2, VOL_2800, "TP");
	#endif
	#ifdef TPD_POWER_SOURCE_1800
		hwPowerOn(TPD_POWER_SOURCE_1800, VOL_1800, "TP");
	#endif 


	#ifdef TPD_CLOSE_POWER_IN_SLEEP	 
		hwPowerDown(TPD_POWER_SOURCE,"TP");
		hwPowerOn(TPD_POWER_SOURCE,VOL_3300,"TP");
		msleep(100);
	#else
		
		msleep(10);
		TPD_DMESG(" fts reset\n");
	    	printk(" fts reset\n");
		tpd_gpio_output(CTP_RST,1);
	#endif	

	tpd_gpio_as_int(CTP_INT);

	msleep(200);

 	errval = i2c_smbus_read_i2c_block_data(fts_i2c_client, 0x00, 1, &data);// if auto upgrade fail, it will not read right value next upgrade.

	TPD_DMESG("gao_i2c:err %d,data:%d\n", errval,data);
	if(errval < 0 || data!=0)	// reg0 data running state is 0; other state is not 0
	{
		TPD_DMESG("I2C transfer error, line: %d\n", __LINE__);
	#ifdef TPD_POWER_SOURCE_CUSTOM
		retval = regulator_disable(tpd->reg);      /*disable regulator*/
                if (retval)
                	TPD_DMESG("regulator_disable() failed!\n");
		regulator_put(tpd->reg);
	#else
		hwPowerDown(MT65XX_POWER_LDO_VGP2, "TP");
	#endif

		return -1; 
	}
        if(tpd_irq_registration() < 0)
	{
	retval = regulator_disable(tpd->reg);      /*disable regulator*/
        if (retval)
        	TPD_DMESG("regulator_disable() failed!\n");
        regulator_put(tpd->reg);

	return -1;
	}
        disable_irq(touch_irq);
	msg_dma_alloct();
	
       fts_init_gpio_hw();
	
	tpd_load_status = 1;
	
    	#ifdef VELOCITY_CUSTOM_fts
		if((err = misc_register(&tpd_misc_device)))
		{
			printk("mtk_tpd: tpd_misc_device register failed\n");
		
		}
	#endif

	thread = kthread_run(touch_event_handler, 0, TPD_DEVICE);
	 if (IS_ERR(thread))
	{ 
		  retval = PTR_ERR(thread);
		  TPD_DMESG(TPD_DEVICE " failed to create kernel thread: %d\n", retval);
	}


	
	#ifdef SYSFS_DEBUG
                fts_create_sysfs(fts_i2c_client);
	#endif
	HidI2c_To_StdI2c(fts_i2c_client);
	fts_get_upgrade_array();
	#ifdef FTS_CTL_IIC
		 if (fts_rw_iic_drv_init(fts_i2c_client) < 0)
			 dev_err(&client->dev, "%s:[FTS] create fts control iic driver failed\n", __func__);
	#endif
	
	#ifdef FTS_APK_DEBUG
		fts_create_apk_debug_channel(fts_i2c_client);
	#endif
	
	#ifdef TPD_AUTO_UPGRADE
		printk("********************Enter CTP Auto Upgrade********************\n");
		is_update = true;
		upgread_ret = fts_ctpm_auto_upgrade(fts_i2c_client);
		is_update = false;
	#endif

	#ifdef TPD_PROXIMITY
		{
			obj_ps.polling = 1; // 0--interrupt mode;1--polling mode;
			obj_ps.sensor_operate = tpd_ps_operate;
			if ((err = hwmsen_attach(ID_PROXIMITY, &obj_ps)))
			{
				TPD_DEBUG("hwmsen attach fail, return:%d.", err);
			}
		}
	#endif
	#if FT_ESD_PROTECT
   		INIT_DELAYED_WORK(&gtp_esd_check_work, gtp_esd_check_func);
    		gtp_esd_check_workqueue = create_workqueue("gtp_esd_check");
    		queue_delayed_work(gtp_esd_check_workqueue, &gtp_esd_check_work, TPD_ESD_CHECK_CIRCLE);
	#endif

	input_set_abs_params(tpd->dev, ABS_MT_POSITION_X, 0, TPD_RES_X, 0, 0);
	input_set_abs_params(tpd->dev, ABS_MT_POSITION_Y, 0, TPD_RES_Y, 0, 0);
	input_mt_init_slots(tpd->dev, fts_updateinfo_curr.TPD_MAX_POINTS, (INPUT_MT_POINTER | INPUT_MT_DIRECT));
	enable_irq(touch_irq);
	
        reg_addr = FTS_REG_FW_VER;
        err1 = fts_i2c_read(fts_i2c_client, &reg_addr, 1, &fw_ver[0], 1);
        if (err1 < 0)
                dev_err(&client->dev, "fw major version read failed");

        reg_addr = FTS_REG_FW_MIN_VER;
        err1 = fts_i2c_read(fts_i2c_client, &reg_addr, 1, &fw_ver[1], 1);
        if (err1 < 0)
                dev_err(&client->dev, "fw minor version read failed");

        reg_addr = FTS_REG_FW_SUB_MIN_VER;
        err1 = fts_i2c_read(fts_i2c_client, &reg_addr, 1, &fw_ver[2], 1);
        if (err1 < 0)
                dev_err(&client->dev, "fw sub minor version read failed");

        dev_info(&client->dev, "Firmware version = %d.%d.%d\n",
                fw_ver[0], fw_ver[1], fw_ver[2]);

	{
		u8 fw_vendor_id = 0x00;
        	u8 reg_addr;
        	int err;

        	reg_addr = FTS_REG_FW_VENDOR_ID;
        	err = fts_i2c_read(fts_i2c_client, &reg_addr, 1, &fw_vendor_id, 1);
        	if (err < 0)
                	dev_err(&client->dev, "fw vendor id read failed");
		else
		{
			if(0x51 == fw_vendor_id)
			{
				sprintf(ft6336s_vendor_name,"%s","ofilm-ft6336s");
			}
		}
	}	
     
	sprintf(ft6336s_fw_version,"-fw:%d.%d.%d",fw_ver[0],fw_ver[1],fw_ver[2]);
	strcat(ft6336s_vendor_name,ft6336s_fw_version);
	
	printk("fts Touch Panel Device Probe %s\n", (retval < TPD_OK) ? "FAIL" : "PASS");

#ifdef CONFIG_ONTIM_DSM	
	tp_probe_ok = 1;
	focaltech_dsm_client=dsm_register_client (&focaltech_dsm_dev);
	if (upgread_ret<0)
	{
		int error=OMTIM_DSM_TP_FW_UPGRAED_ERROR;
	 	if ( (focaltech_dsm_client ) && dsm_client_ocuppy(focaltech_dsm_client))
	 	{
	 		if ((focaltech_dsm_client->dump_buff) && (focaltech_dsm_client->buff_size)&&(focaltech_dsm_client->buff_flag == OMTIM_DSM_BUFF_OK))
	 		{
				focaltech_dsm_client->used_size = sprintf(focaltech_dsm_client->dump_buff,"Type=%d; ID=%d; error_id=%d; CTP info:%s; FW upgread error = %d\n",focaltech_dsm_client->client_type,focaltech_dsm_client->client_id,error,ft6336s_vendor_name,upgread_ret );
				dsm_client_notify(focaltech_dsm_client,error);
	 		}
	 	}
		else
		{
			printk(KERN_ERR "%s: dsm ocuppy error!!!",__func__);
		}
	}
	if (retval<0)
	{
		int error=OMTIM_DSM_TP_CREATE_THREAD_ERROR;
	 	if ( (focaltech_dsm_client ) && dsm_client_ocuppy(focaltech_dsm_client))
	 	{
	 		if ((focaltech_dsm_client->dump_buff) && (focaltech_dsm_client->buff_size)&&(focaltech_dsm_client->buff_flag == OMTIM_DSM_BUFF_OK))
	 		{
				focaltech_dsm_client->used_size = sprintf(focaltech_dsm_client->dump_buff,"Type=%d; ID=%d; error_id=%d; CTP info:%s; Create kernel thread error = %d\n",focaltech_dsm_client->client_type,focaltech_dsm_client->client_id,error,ft6336s_vendor_name,retval );
				dsm_client_notify(focaltech_dsm_client,error);
	 		}
	 	}
		else
		{
			printk(KERN_ERR "%s: dsm ocuppy error!!!",__func__);
		}
	}
#endif
	tpd_init_registers(fts_i2c_client);
	
   	return 0;
   
}
/************************************************************************
* Name: tpd_remove
* Brief: remove driver/channel
* Input: i2c info
* Output: no
* Return: 0
***********************************************************************/
 static int __devexit tpd_remove(struct i2c_client *client)
 
 {
     int retval = TPD_OK;

     msg_dma_release();

     #ifdef FTS_CTL_IIC
     		fts_rw_iic_drv_exit();
     #endif
     #ifdef SYSFS_DEBUG
     		fts_remove_sysfs(client);
     #endif
     #if FT_ESD_PROTECT
    		destroy_workqueue(gtp_esd_check_workqueue);
     #endif

     #ifdef FTS_APK_DEBUG
     		fts_release_apk_debug_channel();
     #endif
     free_irq(touch_irq, NULL);
     
     retval = regulator_disable(tpd->reg);      //disable regulator
     if (retval)
     	TPD_DMESG("regulator_disable() failed!\n");
     regulator_put(tpd->reg);
	TPD_DMESG("TPD removed\n");
 
   return 0;
 }


 /************************************************************************
* Name: tpd_local_init
* Brief: add driver info
* Input: no
* Output: no
* Return: fail <0
***********************************************************************/
 static int tpd_local_init(void)
 {
   	if(i2c_add_driver(&tpd_i2c_driver)!=0)
   	{
        	TPD_DMESG("fts unable to add i2c driver.\n");
      		return -1;
    	}
    	if(tpd_load_status == 0) 
    	{
       	TPD_DMESG("fts add error touch panel driver.\n");
    		i2c_del_driver(&tpd_i2c_driver);
    		return -1;
    	}
	
	
   	#ifdef TPD_HAVE_BUTTON     
		// initialize tpd button data
    	 	tpd_button_setting(TPD_KEY_COUNT, tpd_keys_local, tpd_keys_dim_local);
	#endif   
  
	#if (defined(TPD_WARP_START) && defined(TPD_WARP_END))    
    		TPD_DO_WARP = 1;
    		memcpy(tpd_wb_start, tpd_wb_start_local, TPD_WARP_CNT*4);
    		memcpy(tpd_wb_end, tpd_wb_start_local, TPD_WARP_CNT*4);
	#endif 

	#if (defined(TPD_HAVE_CALIBRATION) && !defined(TPD_CUSTOM_CALIBRATION))
    		memcpy(tpd_calmat, tpd_def_calmat_local, 8*4);
    		memcpy(tpd_def_calmat, tpd_def_calmat_local, 8*4);	
	#endif  
	TPD_DMESG("end %s, %d\n", __FUNCTION__, __LINE__);  
	tpd_type_cap = 1;
    	return 0; 
 }
static void fts_release_all_finger ( void )
{
	unsigned int finger_count=0;

	for(finger_count = 0; finger_count < fts_updateinfo_curr.TPD_MAX_POINTS; finger_count++)
	{
		input_mt_slot( tpd->dev, finger_count);
		input_mt_report_slot_state( tpd->dev, MT_TOOL_FINGER, false);
	}
	 input_report_key(tpd->dev, BTN_TOUCH, 0);
	input_sync ( tpd->dev );

}
 /************************************************************************
* Name: tpd_resume
* Brief: system wake up 
* Input: no use
* Output: no
* Return: no
***********************************************************************/
static void tpd_resume(struct device *h )
 {
 	TPD_DMESG("TPD wake up\n");
	
  	#ifdef TPD_PROXIMITY	
		if (tpd_proximity_flag == 1)
		{
			if(tpd_proximity_flag_one == 1)
			{
				tpd_proximity_flag_one = 0;	
				TPD_DMESG(TPD_DEVICE " tpd_proximity_flag_one \n"); 
				return;
			}
		}
	#endif	

	#ifdef TPD_CLOSE_POWER_IN_SLEEP	
		hwPowerOn(TPD_POWER_SOURCE,VOL_3300,"TP");
	#else
		tpd_gpio_output(CTP_RST, 0);
		msleep(1);
		tpd_gpio_output(CTP_RST, 1);
	#endif
	enable_irq(touch_irq);
	msleep(TRSI_MS);
	/*
	 * Our registers get wiped during a reset, so we need to init their
	 * values again.
	 */
	tpd_init_registers(fts_i2c_client);
	fts_release_all_finger();
	tpd_halt = 0;
	
	#if FT_ESD_PROTECT
                count_irq = 0;
    		queue_delayed_work(gtp_esd_check_workqueue, &gtp_esd_check_work, TPD_ESD_CHECK_CIRCLE);
	#endif

	TPD_DMESG("TPD wake up done\n");

 }
 /************************************************************************
* Name: tpd_suspend
* Brief: system sleep
* Input: no use
* Output: no
* Return: no
***********************************************************************/
static void tpd_suspend( struct device *h )
 {
	static char data = 0x3;
	int ret = 0;
			
	TPD_DMESG("TPD enter sleep\n");
#ifdef CONFIG_ONTIM_DSM	
 	if ( (focaltech_dsm_client ) && dsm_client_ocuppy(focaltech_dsm_client))
 	{
		int error=OMTIM_DSM_TP_INFO;
 		if ((focaltech_dsm_client->dump_buff) && (focaltech_dsm_client->buff_size)&&(focaltech_dsm_client->buff_flag == OMTIM_DSM_BUFF_OK))
 		{
			focaltech_dsm_client->used_size = sprintf(focaltech_dsm_client->dump_buff,"Type=%d; ID=%d; error_id=%d;  CTP info:%s; irq_count = %d; irq_run_count =%d\n",focaltech_dsm_client->client_type,focaltech_dsm_client->client_id,error,ft6336s_vendor_name,ft6336s_irq_count,ft6336s_irq_run_count );
			dsm_client_notify(focaltech_dsm_client,error);
 		}
 	}
	else
	{
		printk(KERN_ERR "%s: dsm ocuppy error!!!",__func__);
	}
#endif
	#ifdef TPD_PROXIMITY
	if (tpd_proximity_flag == 1)
	{
		tpd_proximity_flag_one = 1;	
		return;
	}
	#endif

	#if FT_ESD_PROTECT
    		cancel_delayed_work_sync(&gtp_esd_check_work);
	#endif
 	 tpd_halt = 1;
	disable_irq(touch_irq);
	 mutex_lock(&i2c_access);
	#ifdef TPD_CLOSE_POWER_IN_SLEEP	
		hwPowerDown(TPD_POWER_SOURCE,"TP");
	#else
		if ((fts_updateinfo_curr.CHIP_ID==0x59))
		{
			data = 0x02;
			ret = fts_write_reg(fts_i2c_client, 0xA5,data);
			
			if (ret<0) 
			{
				printk("[Focal][Touch] write value fail");
			}
		}
		else
		{
			
			ret = fts_write_reg(fts_i2c_client, 0xA5,data);
			if (ret<0) 
			{
				printk("[Focal][Touch] write value fail");
			}
		}
		msleep(10);
	#endif
	mutex_unlock(&i2c_access);
	fts_release_all_finger();
	
    	TPD_DMESG("TPD enter sleep done\n");

 } 


 static struct tpd_driver_t tpd_device_driver = {
       	 .tpd_device_name 	= "fts",
		 .tpd_local_init 		= tpd_local_init,
		 .suspend 			= tpd_suspend,
		 .resume 				= tpd_resume,
	
	#ifdef TPD_HAVE_BUTTON
		 .tpd_have_button 		= 1,
	#else
		 .tpd_have_button 		= 0,
	#endif
	
 };

  /************************************************************************
* Name: tpd_suspend
* Brief:  called when loaded into kernel
* Input: no
* Output: no
* Return: 0
***********************************************************************/
 static int __init tpd_driver_init(void) {
        printk("MediaTek fts touch panel driver init\n");
	tpd_get_dts_info();
	if(tpd_driver_add(&tpd_device_driver) < 0)
       	TPD_DMESG("add fts driver failed\n");
	 return 0;
 }
 
 
/************************************************************************
* Name: tpd_driver_exit
* Brief:  should never be called
* Input: no
* Output: no
* Return: 0
***********************************************************************/
 static void __exit tpd_driver_exit(void) 
 {
        TPD_DMESG("MediaTek fts touch panel driver exit\n");
	 tpd_driver_remove(&tpd_device_driver);
 }
 
 module_init(tpd_driver_init);
 module_exit(tpd_driver_exit);
