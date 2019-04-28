/*
* Copyright (C) 2017 MediaTek Inc.
*
* This program is free software; you can redistribute it and/or modify
* it under the terms of the GNU General Public License version 2 as
* published by the Free Software Foundation.
*
* This program is distributed in the hope that it will be useful,
* but WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
* See http://www.gnu.org/licenses/gpl-2.0.html for more details.
*/

/*
 *
 * Author: MingHsien Hsieh <minghsien.hsieh@mediatek.com>
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

#include <linux/interrupt.h>
#include <linux/i2c.h>
#include <linux/slab.h>
#include <linux/irq.h>
#include <linux/miscdevice.h>
#include <linux/uaccess.h>
#include <linux/delay.h>
#include <linux/input.h>
#include <linux/workqueue.h>
#include <linux/kobject.h>
#if defined(CONFIG_HAS_EARLYSUSPEND)
#include <linux/earlysuspend.h>
#endif
#include <linux/platform_device.h>
#include <linux/atomic.h>

#include "hwmsensor.h"
#include "sensors_io.h"
#include <linux/io.h>
#include "cust_alsps_ltr559.h"
#include "hwmsen_helper.h"
#include "ltr559.h"
#include "sensor_dts.h"

#include <linux/wakelock.h>
#include <linux/sched.h>
#include <alsps.h>
#include <linux/mutex.h>

#undef CUSTOM_KERNEL_SENSORHUB
#ifdef CUSTOM_KERNEL_SENSORHUB
#include <SCP_sensorHub.h>
#endif

#include <linux/of.h>
#include <linux/of_address.h>
#include <linux/of_device.h>
#include <linux/of_gpio.h>
#include <linux/of_irq.h>


#define POWER_NONE_MACRO MT65XX_POWER_NONE

/******************************************************************************
 * configuration
*******************************************************************************/
/*----------------------------------------------------------------------------*/

#define LTR559_DEV_NAME   "LTR_559ALS"

/*----------------------------------------------------------------------------*/
#define APS_TAG                  "[ALS/PS] "
#define APS_FUN(f)               pr_debug(APS_TAG"%s\n", __func__)

#define APS_ERR(fmt, args...)    pr_err(APS_TAG"%s %d : "fmt, __func__, __LINE__, ##args)

#define APS_ERR_ST(f)    pr_err(APS_TAG"%s %d : ", __func__, __LINE__)


#define APS_LOG(fmt, args...)    pr_debug(APS_TAG fmt, ##args)
#define APS_DBG(fmt, args...)    pr_debug(APS_TAG fmt, ##args)

static struct i2c_client *ltr559_i2c_client;

/*----------------------------------------------------------------------------*/
static const struct i2c_device_id ltr559_i2c_id[] = { {LTR559_DEV_NAME, 0}, {} };

/*----------------------------------------------------------------------------*/
static int ltr559_i2c_probe(struct i2c_client *client, const struct i2c_device_id *id);
static int ltr559_i2c_remove(struct i2c_client *client);
static int ltr559_i2c_detect(struct i2c_client *client, struct i2c_board_info *info);
/*----------------------------------------------------------------------------*/
static int ltr559_i2c_suspend(struct i2c_client *client, pm_message_t msg);
static int ltr559_i2c_resume(struct i2c_client *client);
static int ltr559_init_device(void);

static int ltr559_ps_enable(int gainrange);

static int als_flush(void);
static int ps_flush(void);

static int dynamic_calibrate;

static int ps_trigger_high = 800;
/* static int ps_trigger_low = 760; */

static int ps_gainrange;
static int als_gainrange;

static int final_prox_val, prox_val;
static int final_lux_val;

/*----------------------------------------------------------------------------*/
/* static DEFINE_MUTEX(read_lock); */
static DEFINE_MUTEX(Ltr559_lock);

/*----------------------------------------------------------------------------*/
static int ltr559_als_read(int gainrange);
static int ltr559_ps_read(void);

/*----------------------------------------------------------------------------*/
enum {
	CMC_BIT_ALS = 1,
	CMC_BIT_PS = 2,
} CMC_BIT;

/*----------------------------------------------------------------------------*/
struct ltr559_i2c_addr {	/*define a series of i2c slave address */
	u8 write_addr;
	u8 ps_thd;		/*PS INT threshold */
};

/*----------------------------------------------------------------------------*/

struct ltr559_priv {
	struct alsps_hw hw;
	struct i2c_client *client;
	struct work_struct eint_work;
	struct mutex lock;
	/*i2c address group */
	struct ltr559_i2c_addr addr;

	/*misc */
	u16 als_modulus;
	atomic_t i2c_retry;
	atomic_t als_debounce;	/*debounce time after enabling als */
	atomic_t als_deb_on;	/*indicates if the debounce is on */
	atomic_t als_deb_end;	/*the jiffies representing the end of debounce */
	atomic_t ps_mask;	/*mask ps: always return far away */
	atomic_t ps_debounce;	/*debounce time after enabling ps */
	atomic_t ps_deb_on;	/*indicates if the debounce is on */
	atomic_t ps_deb_end;	/*the jiffies representing the end of debounce */
	atomic_t ps_suspend;
	atomic_t als_suspend;

	/*data */
	u16 als;
	u16 ps;
	u8 _align;
	/* u16         als_level_num; */
	/* u16         als_value_num; */
	/* u32         als_level[C_CUST_ALS_LEVEL]; */
	/* u32         als_value[C_CUST_ALS_LEVEL]; */
	int ps_cali;

	atomic_t als_cmd_val;	/*the cmd value can't be read, stored in ram */
	atomic_t ps_cmd_val;	/*the cmd value can't be read, stored in ram */
	atomic_t ps_thd_val;	/*the cmd value can't be read, stored in ram */
	atomic_t ps_thd_val_high;	/*the cmd value can't be read, stored in ram */
	atomic_t ps_thd_val_low;	/*the cmd value can't be read, stored in ram */
	ulong enable;		/*enable mask */
	ulong pending_intr;	/*pending interrupt */

	/*early suspend */
#if defined(CONFIG_HAS_EARLYSUSPEND)
	struct early_suspend early_drv;
#endif

#if defined(CONFIG_OF)
	struct device_node *irq_node;
	int irq;
#endif
	bool als_flush;
	bool ps_flush;

};

struct PS_CALI_DATA_STRUCT {
	int noice;
	int close;
	int far_away;
	int valid;
};

static struct PS_CALI_DATA_STRUCT ps_cali = { -1, 0, 0, 0 };
static int intr_flag_value;
static struct ltr559_priv *ltr559_obj;
/* static struct platform_driver ltr559_alsps_driver; */

static int ltr559_local_init(void);
static int ltr559_remove(void);
static int ltr559_init_flag = -1;	/* 0<==>OK -1 <==> fail */
static struct alsps_init_info ltr559_init_info = {
	.name = LTR559_DEV_NAME,
	.init = ltr559_local_init,
	.uninit = ltr559_remove,

};

static unsigned int current_tp;
static unsigned int current_color_temp = CWF_TEMP;
static unsigned int current_color_temp_first;
/* static unsigned int    als_level[TP_COUNT][TEMP_COUNT][C_CUST_ALS_LEVEL] = {0}; */
static unsigned int als_level[TP_COUNT][TEMP_COUNT][C_CUST_ALS_LEVEL];
static unsigned int als_value[C_CUST_ALS_LEVEL] = { 0 };

#include <linux/rtc.h>
static int current_color_ratio;

/*
 * #########
 * ## I2C ##
 * #########
 */

/* I2C Read */
static int ltr559_i2c_read_reg(u8 regnum)
{
	u8 buffer[1], reg_value[1];
	int res = 0;
	/* mutex_lock(&read_lock); */

	buffer[0] = regnum;
	res = i2c_master_send(ltr559_obj->client, buffer, 0x1);
	if (res <= 0) {
		APS_ERR("read reg send res = %d\n", res);
		return res;
	}
	res = i2c_master_recv(ltr559_obj->client, reg_value, 0x1);
	if (res <= 0) {
		APS_ERR("read reg recv res = %d\n", res);
		return res;
	}
	/* mutex_unlock(&read_lock); */
	return reg_value[0];
}

/* I2C Write */
static int ltr559_i2c_write_reg(u8 regnum, u8 value)
{
	u8 databuf[2];
	int res = 0;

	databuf[0] = regnum;
	databuf[1] = value;
	res = i2c_master_send(ltr559_obj->client, databuf, 0x2);

	if (res < 0) {
		APS_ERR("write reg send res = %d\n", res);
		return res;
	}

	else
		return 0;
}

#if 1				/* def GN_MTK_BSP_PS_DYNAMIC_CALI */
static ssize_t ltr559_dynamic_calibrate(void)
{
	/* int ret=0; */
	int i = 0;
	int data;
	int data_total = 0;
	ssize_t len = 0;
	int noise = 0;
	int count = 5;
	int max = 0;
	struct ltr559_priv *obj = ltr559_obj;

	if (!ltr559_obj) {
		APS_ERR("ltr559_obj is null!!\n");
		return -1;
	}
	/* wait for register to be stable */
	usleep_range(10000, 20000);


	for (i = 0; i < count; i++) {
		/* wait for ps value be stable */
		usleep_range(10000, 20000);
		data = ltr559_ps_read();
		if (data < 0) {
			i--;
			continue;
		}

		if (data & 0x8000) {
			noise = 0;
			break;
		}
		noise = data;

		data_total += data;

		if (max++ > 100) {
			/* len = sprintf(buf,"adjust fail\n"); */
			return len;
		}
	}

	noise = data_total / count;
	dynamic_calibrate = noise;

	if ((ps_cali.noice > -1) && (ps_cali.close > ps_cali.far_away)) {
		int temp_high = ps_cali.close - ps_cali.noice + dynamic_calibrate;
		int temp_low = ps_cali.far_away - ps_cali.noice + dynamic_calibrate;

		if ((temp_high < 1600) && (temp_low > 10) && (dynamic_calibrate > ps_cali.noice)
		    && (dynamic_calibrate < ((ps_cali.close + ps_cali.far_away) / 2))) {
			atomic_set(&obj->ps_thd_val_high, temp_high);
			atomic_set(&obj->ps_thd_val_low, temp_low);
		} else {
			atomic_set(&obj->ps_thd_val_high, ps_cali.close);
			atomic_set(&obj->ps_thd_val_low, ps_cali.far_away);
		}
	}
	APS_LOG(
	       "%s:cali noice=%d; cali high=%d; cali low=%d; curr noice=%d; high=%d; low=%d\n",
	       __func__, ps_cali.noice, ps_cali.close, ps_cali.far_away, dynamic_calibrate,
	       atomic_read(&obj->ps_thd_val_high), atomic_read(&obj->ps_thd_val_low));
	return 0;
}
#endif

/*----------------------------------------------------------------------------*/
static ssize_t ltr559_show_als(struct device_driver *ddri, char *buf)
{
	int res;
	/* u8 dat = 0; */

	if (!ltr559_obj) {
		APS_ERR("ltr559_obj is null!!\n");
		return 0;
	}
	mutex_lock(&Ltr559_lock);
	res = ltr559_als_read(als_gainrange);
	mutex_unlock(&Ltr559_lock);
	return snprintf(buf, PAGE_SIZE, "0x%04X\n", res);

}

/*----------------------------------------------------------------------------*/
static ssize_t ltr559_show_ps(struct device_driver *ddri, char *buf)
{
	int res;

	if (!ltr559_obj) {
		APS_ERR("ltr559_obj is null!!\n");
		return 0;
	}
	mutex_lock(&Ltr559_lock);
	res = ltr559_ps_read();
	mutex_unlock(&Ltr559_lock);
	return snprintf(buf, PAGE_SIZE, "0x%04X\n", res);
}

/*----------------------------------------------------------------------------*/


/*----------------------------------------------------------------------------*/

static ssize_t ltr559_show_status(struct device_driver *ddri, char *buf)
{
	ssize_t len = 0;

	if (!ltr559_obj) {
		APS_ERR("ltr559_obj is null!!\n");
		return 0;
	}

	mutex_lock(&Ltr559_lock);

		len += snprintf(buf + len, PAGE_SIZE - len, "CUST: %d, (%d %d)\n",
				ltr559_obj->hw.i2c_num, ltr559_obj->hw.power_id,
				ltr559_obj->hw.power_vol);

	len +=
	    snprintf(buf + len, PAGE_SIZE - len, "MISC: %d %d\n",
		     atomic_read(&ltr559_obj->als_suspend), atomic_read(&ltr559_obj->ps_suspend));
	mutex_unlock(&Ltr559_lock);

	return len;
}

/*----------------------------------------------------------------------------*/
static ssize_t ltr559_store_status(struct device_driver *ddri, const char *buf, size_t count)
{
	int status1, ret;

	if (!ltr559_obj) {
		APS_ERR("ltr559_obj is null!!\n");
		return 0;
	}

	if (1 == sscanf(buf, "%d ", &status1)) {
		mutex_lock(&Ltr559_lock);
		ret = ltr559_ps_enable(ps_gainrange);
		mutex_unlock(&Ltr559_lock);
		APS_DBG("iret= %d, ps_gainrange = %d\n", ret, ps_gainrange);
	} else {
		APS_DBG("invalid content: '%s', length = %ld\n", buf, (long int)count);
	}
	return count;
}

/*----------------------------------------------------------------------------*/
static ssize_t ltr559_show_reg(struct device_driver *ddri, char *buf)
{
	int i, len = 0;
	int reg[] = { 0x80, 0x81, 0x82, 0x83, 0x84, 0x85, 0x86, 0x87, 0x88, 0x89, 0x8a, 0x8b, 0x8c,
		0x8d, 0x8e, 0x8f, 0x90, 0x91, 0x92, 0x93, 0x94, 0x95, 0x97, 0x98, 0x99, 0x9a, 0x9e
	};
	mutex_lock(&Ltr559_lock);
	for (i = 0; i < 27; i++) {
		len +=
		    snprintf(buf + len, PAGE_SIZE - len, "reg:0x%04X value: 0x%04X\n", reg[i],
			     ltr559_i2c_read_reg(reg[i]));

	}
	mutex_unlock(&Ltr559_lock);
	return len;
}

/*----------------------------------------------------------------------------*/
static ssize_t ltr559_store_reg(struct device_driver *ddri, const char *buf, size_t count)
{
	int ret, value;
	u32 reg;

	if (!ltr559_obj) {
		APS_ERR("ltr559_obj is null!!\n");
		return 0;
	}

	if (2 == sscanf(buf, "%x %x ", &reg, &value)) {
		mutex_lock(&Ltr559_lock);
		APS_DBG("before write reg: %x, reg_value = %x  write value=%x\n", reg,
			ltr559_i2c_read_reg(reg), value);
		ret = ltr559_i2c_write_reg(reg, value);
		APS_DBG("after write reg: %x, reg_value = %x\n", reg, ltr559_i2c_read_reg(reg));
		mutex_unlock(&Ltr559_lock);
	} else {
		APS_DBG("invalid content: '%s', length = %ld\n", buf, (long int)count);
	}
	return count;
}

/*----------------------------------------------------------------------------*/
static DRIVER_ATTR(als, S_IWUSR | S_IRUGO, ltr559_show_als, NULL);
static DRIVER_ATTR(ps, S_IWUSR | S_IRUGO, ltr559_show_ps, NULL);
static DRIVER_ATTR(status, S_IWUSR | S_IRUGO, ltr559_show_status, ltr559_store_status);
static DRIVER_ATTR(reg, S_IWUSR | S_IRUGO, ltr559_show_reg, ltr559_store_reg);
/*----------------------------------------------------------------------------*/
static struct driver_attribute *ltr559_attr_list[] = {
	&driver_attr_als,
	&driver_attr_ps,
	&driver_attr_status,
	&driver_attr_reg,
};

/*----------------------------------------------------------------------------*/
static int ltr559_create_attr(struct device_driver *driver)
{
	int idx, err = 0;
	int num = (int)(sizeof(ltr559_attr_list) / sizeof(ltr559_attr_list[0]));

	if (driver == NULL)
		return -EINVAL;

	for (idx = 0; idx < num; idx++) {
		err = driver_create_file(driver, ltr559_attr_list[idx]);
		if (err) {
			APS_ERR("driver_create_file (%s) = %d\n", ltr559_attr_list[idx]->attr.name,
				err);
			break;
		}
	}
	return err;
}

/*----------------------------------------------------------------------------*/
static int ltr559_delete_attr(struct device_driver *driver)
{
	int idx, err = 0;
	int num = (int)(sizeof(ltr559_attr_list) / sizeof(ltr559_attr_list[0]));

	if (!driver)
		return -EINVAL;

	for (idx = 0; idx < num; idx++)
		driver_remove_file(driver, ltr559_attr_list[idx]);
	return err;
}

/*----------------------------------------------------------------------------*/

/*
 * ###############
 * ## PS CONFIG ##
 * ###############

 */

static int ltr559_ps_set_thres(void)
{
	int res;
	u8 databuf[2];

	struct i2c_client *client = ltr559_obj->client;
	struct ltr559_priv *obj = ltr559_obj;

	APS_FUN();

	databuf[0] = LTR559_PS_THRES_LOW_0;
	databuf[1] = (u8) ((atomic_read(&obj->ps_thd_val_low)) & 0x00FF);
	res = i2c_master_send(client, databuf, 0x2);
	if (res <= 0) {
		goto EXIT_ERR;
		return ltr559_ERR_I2C;
	}
	databuf[0] = LTR559_PS_THRES_LOW_1;
	databuf[1] = (u8) ((atomic_read(&obj->ps_thd_val_low) >> 8) & 0x00FF);

	res = i2c_master_send(client, databuf, 0x2);
	if (res <= 0) {
		goto EXIT_ERR;
		return ltr559_ERR_I2C;
	}
	databuf[0] = LTR559_PS_THRES_UP_0;
	databuf[1] = (u8) ((atomic_read(&obj->ps_thd_val_high)) & 0x00FF);
	res = i2c_master_send(client, databuf, 0x2);
	if (res <= 0) {
		goto EXIT_ERR;
		return ltr559_ERR_I2C;
	}
	databuf[0] = LTR559_PS_THRES_UP_1;
	databuf[1] = (u8) ((atomic_read(&obj->ps_thd_val_high) >> 8) & 0x00FF);
	res = i2c_master_send(client, databuf, 0x2);
	if (res <= 0) {
		goto EXIT_ERR;
		return ltr559_ERR_I2C;
	}
	res = 0;
	return res;

EXIT_ERR:
	APS_ERR("set thres: %d\n", res);
	return res;

}


static int ltr559_ps_enable(int gainrange)
{
	struct i2c_client *client = ltr559_obj->client;
	struct ltr559_priv *obj = ltr559_obj;
	u8 databuf[2];
	int res;

	int data;
	/* struct hwm_sensor_data sensor_data; */


	int error;
	int setgain;

	APS_LOG("ltr559_ps_enable() ...start!\n");
	prox_val = 0;		/* add by hzb */
	gainrange = PS_RANGE16;	/* modify by hzb */
	switch (gainrange) {
	case PS_RANGE16:
		setgain = MODE_PS_ON_Gain16;
		break;

	case PS_RANGE32:
		setgain = MODE_PS_ON_Gain32;
		break;

	case PS_RANGE64:
		setgain = MODE_PS_ON_Gain64;
		break;


	default:
		setgain = MODE_PS_ON_Gain16;
		break;
	}

	APS_LOG("LTR559_PS setgain = %d!\n", setgain);

	error = ltr559_i2c_write_reg(LTR559_PS_CONTR, setgain);
	if (error < 0) {
		APS_LOG("ltr559_ps_enable() error1\n");
		return error;
	}
	/* wisky-lxh@20150108 */
	res = ltr559_init_device();
	if (res < 0) {
		APS_ERR("ltr559_init_devicet: %d\n", res);
		return res;
	}
	/* end-wisky-lxh */


	/* ===============
	 * ** IMPORTANT **
	 * ===============
	 * Other settings like timing and threshold to be set here, if required.
	 * Not set and kept as device default for now.
	 */

	data = ltr559_i2c_read_reg(LTR559_PS_CONTR);

	if (data & 0x02)
		ltr559_dynamic_calibrate();

	/*for interrupt work mode support  */
	if (0 == obj->hw.polling_mode_ps) {
		ltr559_ps_set_thres();
		databuf[0] = LTR559_INTERRUPT;
		databuf[1] = 0x01;
		res = i2c_master_send(client, databuf, 0x2);
		if (res <= 0) {
			goto EXIT_ERR;
			return ltr559_ERR_I2C;
		}

		databuf[0] = LTR559_INTERRUPT_PERSIST;
		databuf[1] = 0x20;
		res = i2c_master_send(client, databuf, 0x2);
		if (res <= 0) {
			goto EXIT_ERR;
			return ltr559_ERR_I2C;
		}
	}

	APS_LOG("ltr559_ps_enable ...OK!\n");
	return error;

EXIT_ERR:
	APS_ERR("set thres: %d\n", res);
	return res;
}

/* Put PS into Standby mode */
static int ltr559_ps_disable(void)
{
	int error;

	error = ltr559_i2c_write_reg(LTR559_PS_CONTR, MODE_PS_StdBy);
	if (error < 0)
		APS_LOG("ltr559_ps_disable ...ERROR\n");
	else
		APS_LOG("ltr559_ps_disable ...OK\n");
	return error;
}


static int ltr559_ps_read(void)
{
	int psval_lo, psval_hi, psdata;

	psval_lo = ltr559_i2c_read_reg(LTR559_PS_DATA_0);
	APS_DBG("ps_rawdata_psval_lo = %d\n", psval_lo);
	if (psval_lo < 0) {

		APS_DBG("psval_lo error\n");
		psdata = psval_lo;
		goto out;
	}
	psval_hi = ltr559_i2c_read_reg(LTR559_PS_DATA_1);
	APS_DBG("ps_rawdata_psval_hi = %d\n", psval_hi);

	if (psval_hi < 0) {
		APS_DBG("psval_hi error\n");
		psdata = psval_hi;
		goto out;
	}
/* +modify by hzb */
	if (psval_hi >= 127) {
		psdata = prox_val;
		APS_DBG("ps_rawdata_psval_hi error!!!Use last PS val %d.\n", psdata);
	} else {
		psdata = ((psval_hi & 7) * 256) + psval_lo;
		/* psdata = ((psval_hi&0x7)<<8) + psval_lo; */
	}
/* -modify by hzb */
	APS_DBG("ps_rawdata = %d\n", psdata);
	prox_val = psdata;

out:
	final_prox_val = psdata;

	return psdata;
}

/*
 * ################
 * ## ALS CONFIG ##
 * ################
 */

static int ltr559_als_enable(int gainrange)
{
	int error;

	gainrange = ALS_RANGE_64K;
	als_gainrange = gainrange;
	APS_LOG("gainrange = %d\n", gainrange);
	switch (gainrange) {
	case ALS_RANGE_64K:
		error = ltr559_i2c_write_reg(LTR559_ALS_CONTR, MODE_ALS_ON_Range1);
		break;

	case ALS_RANGE_32K:
		error = ltr559_i2c_write_reg(LTR559_ALS_CONTR, MODE_ALS_ON_Range2);
		break;

	case ALS_RANGE_16K:
		error = ltr559_i2c_write_reg(LTR559_ALS_CONTR, MODE_ALS_ON_Range3);
		break;

	case ALS_RANGE_8K:
		error = ltr559_i2c_write_reg(LTR559_ALS_CONTR, MODE_ALS_ON_Range4);
		break;

	case ALS_RANGE_1300:
		error = ltr559_i2c_write_reg(LTR559_ALS_CONTR, MODE_ALS_ON_Range5);
		break;

	case ALS_RANGE_600:
		error = ltr559_i2c_write_reg(LTR559_ALS_CONTR, MODE_ALS_ON_Range6);
		break;

	default:
		error = ltr559_i2c_write_reg(LTR559_ALS_CONTR, MODE_ALS_ON_Range1);
		APS_LOG("proxmy sensor gainrange %d!\n", gainrange);
		break;
	}

	mdelay(WAKEUP_DELAY);

	/* ===============
	 * ** IMPORTANT **
	 * ===============
	 * Other settings like timing and threshold to be set here, if required.
	 * Not set and kept as device default for now.
	 */
	if (error < 0)
		APS_LOG("ltr559_als_enable ...ERROR\n");
	else
		APS_LOG("ltr559_als_enable ...OK\n");

	return error;
}


/* Put ALS into Standby mode */
static int ltr559_als_disable(void)
{
	int error;

	error = ltr559_i2c_write_reg(LTR559_ALS_CONTR, MODE_ALS_StdBy);
	if (error < 0)
		APS_LOG("ltr559_als_disable ...ERROR\n");
	else
		APS_LOG("ltr559_als_disable ...OK\n");
	return error;
}

static int ltr559_als_read(int gainrange)
{
	int alsval_ch0_lo, alsval_ch0_hi, alsval_ch0;
	int alsval_ch1_lo, alsval_ch1_hi, alsval_ch1;
	int luxdata_int = -1;
	int ratio;
	static unsigned long current_time;

	if (current_color_temp_first == 0) {
		mm_segment_t orgfs = 0;
		struct file *filep = NULL;
		char buf[14] = { 0 };

		current_color_temp_first = 1;

		orgfs = get_fs();
		/* set_fs(KERNEL_DS); */
		set_fs(get_ds());

		filep = filp_open("/sys/ontim_dev_debug/touch_screen/vendor", O_RDONLY, 0600);
		if (IS_ERR(filep)) {
			APS_ERR("read, sys_open %s error!!.\n",
				"/sys/ontim_dev_debug/touch_screen/vendor");
			set_fs(orgfs);
		} else {
			memset(buf, 0, sizeof(buf));
			filep->f_op->read(filep, buf, 13 /*sizeof(buf) */ , &filep->f_pos);
			if (!strncmp(buf, "ofilm", 5)) {
				if (!strncmp(&buf[6], "ft6336s", 7))
					current_tp = 0;
			} else if (!strncmp(buf, "each", 4)) {
				if (!strncmp(&buf[5], "msg2238a", 8))
					current_tp = 1;
			}
			filp_close(filep, NULL);
			set_fs(orgfs);
		}
	}

	if (current_time) {
		if (time_after(jiffies, (current_time + 300 / (1000 / HZ))))
			current_time = 0;
		else {
			luxdata_int = final_lux_val;
			goto err;
		}
	}

	alsval_ch1_lo = ltr559_i2c_read_reg(LTR559_ALS_DATA_CH1_0);
	alsval_ch1_hi = ltr559_i2c_read_reg(LTR559_ALS_DATA_CH1_1);
	alsval_ch1 = (alsval_ch1_hi * 256) + alsval_ch1_lo;
	alsval_ch0_lo = ltr559_i2c_read_reg(LTR559_ALS_DATA_CH0_0);
	alsval_ch0_hi = ltr559_i2c_read_reg(LTR559_ALS_DATA_CH0_1);
	alsval_ch0 = (alsval_ch0_hi * 256) + alsval_ch0_lo;
	APS_DBG("alsval_ch0_lo = %d,alsval_ch0_hi=%d,alsval_ch0=%d\n", alsval_ch0_lo, alsval_ch0_hi,
		alsval_ch0);
	APS_DBG("alsval_ch1_lo = %d,alsval_ch1_hi=%d,alsval_ch1=%d\n", alsval_ch1_lo, alsval_ch1_hi,
		alsval_ch1);

	if ((alsval_ch1 == 0) || (alsval_ch0 == 0)) {
		luxdata_int = 0;
		goto err;
	}
	ratio = (alsval_ch1 * 100) / (alsval_ch0 + alsval_ch1);
	current_color_ratio = ratio;
	APS_DBG(" ratio = %d  gainrange = %d\n", ratio, gainrange);
	if (ratio < 45) {
		luxdata_int =
		    (((17743 * alsval_ch0) + (11059 * alsval_ch1))) / 10000 * 10 / als_gainrange;

		current_color_temp = CWF_TEMP;
		/* if( 33<=ratio &&  ratio <=40 ) */
		/* current_color_temp =TL84_TEMP; */
	} else if ((ratio < 64) && (ratio >= 45)) {
		luxdata_int =
		    (((42785 * alsval_ch0) - (19548 * alsval_ch1))) / 10000 * 10 / als_gainrange;
		current_color_temp = D65_TEMP;
	} else if ((ratio <= 100) && (ratio >= 64)) {
		luxdata_int =
		    (((5926 * alsval_ch0) + (1185 * alsval_ch1))) / 10000 * 10 / als_gainrange;
		current_color_temp = D65_TEMP;
	} else {
		luxdata_int = 0;
	}

	APS_DBG("als_value = %d\n", luxdata_int);

#if 1
	if (luxdata_int < (als_level[current_tp][current_color_temp][3] - 50)) {
		if (als_gainrange != ALS_RANGE_600) {
			ltr559_i2c_write_reg(LTR559_ALS_CONTR, MODE_ALS_ON_Range6);
			als_gainrange = ALS_RANGE_600;
			current_time = jiffies;
		}
	} else if (luxdata_int > (als_level[current_tp][current_color_temp][3] + 50)) {
		if (als_gainrange != ALS_RANGE_64K) {
			ltr559_i2c_write_reg(LTR559_ALS_CONTR, MODE_ALS_ON_Range1);
			als_gainrange = ALS_RANGE_64K;
			current_time = jiffies;
		}
	}
#endif
err:
	final_lux_val = luxdata_int;
	APS_DBG("%s; als_value = %d;%lu;\n", __func__, luxdata_int, current_time);
	return luxdata_int;
}



/*----------------------------------------------------------------------------*/
int ltr559_get_addr(struct alsps_hw *hw, struct ltr559_i2c_addr *addr)
{
	return 0;
}


/*-----------------------------------------------------------------------------*/
void ltr559_eint_func(void)
{
	struct ltr559_priv *obj;

	APS_FUN();
	obj = ltr559_obj;
	if (!obj)
		return;
	/* mutex_lock(&Ltr559_lock); */
	schedule_work(&obj->eint_work);
}



/*----------------------------------------------------------------------------*/
/*for interrupt work mode support -- by liaoxl.lenovo 12.08.2011*/

#if defined(CONFIG_OF)
static irqreturn_t ltr559_eint_handler(int irq, void *desc)
{
	disable_irq_nosync(ltr559_obj->irq);
	ltr559_eint_func();

	return IRQ_HANDLED;
}
#endif


int ltr559_setup_eint(struct i2c_client *client)
{
#if defined(CONFIG_OF)
	int ret;
	struct pinctrl *pinctrl;
	struct pinctrl_state *pins_default;
	struct pinctrl_state *pins_cfg;
#endif

#if defined(CONFIG_OF)
	/* u32 ints[2] = {0, 0}; */

/* gpio setting */
	pinctrl = devm_pinctrl_get(&client->dev);
	if (IS_ERR(pinctrl)) {
		ret = PTR_ERR(pinctrl);
		APS_ERR("Cannot find alsps pinctrl!\n");
	}
	pins_default = pinctrl_lookup_state(pinctrl, "pin_default");
	if (IS_ERR(pins_default)) {
		ret = PTR_ERR(pins_default);
		APS_ERR("Cannot find alsps pinctrl default!\n");

	}

	pins_cfg = pinctrl_lookup_state(pinctrl, "pin_cfg");
	if (IS_ERR(pins_cfg)) {
		ret = PTR_ERR(pins_cfg);
		APS_ERR("Cannot find alsps pinctrl pin_cfg!\n");

	}
/* eint request */
	if (ltr559_obj->irq_node) {
#if 0
		of_property_read_u32_array(ltr559_obj->irq_node, "debounce", ints,
					   ARRAY_SIZE(ints));
		gpio_request(ints[0], "p-sensor");
		gpio_set_debounce(ints[0], ints[1]);
		APS_LOG("ints[0] = %d, ints[1] = %d!!\n", ints[0], ints[1]);
#endif
		pinctrl_select_state(pinctrl, pins_cfg);

		ltr559_obj->irq = irq_of_parse_and_map(ltr559_obj->irq_node, 0);
		APS_LOG("ltr559_obj->irq = %d\n", ltr559_obj->irq);
		if (!ltr559_obj->irq) {
			APS_ERR("irq_of_parse_and_map fail!!\n");
			return -EINVAL;
		}
		if (request_irq
		    (ltr559_obj->irq, ltr559_eint_handler, IRQF_TRIGGER_LOW, "ALS-eint", NULL)) {
			APS_ERR("IRQ LINE NOT AVAILABLE!!\n");
			return -EINVAL;
		}
		enable_irq(ltr559_obj->irq);
	} else {
		APS_ERR("null irq node!!\n");
		return -EINVAL;
	}

#else

	mt_set_gpio_dir(GPIO_ALS_EINT_PIN, GPIO_DIR_IN);
	mt_set_gpio_mode(GPIO_ALS_EINT_PIN, GPIO_ALS_EINT_PIN_M_EINT);
	mt_set_gpio_pull_enable(GPIO_ALS_EINT_PIN, TRUE);
	mt_set_gpio_pull_select(GPIO_ALS_EINT_PIN, GPIO_PULL_UP);

	mt_eint_set_hw_debounce(CUST_EINT_ALS_NUM, CUST_EINT_ALS_DEBOUNCE_CN);
	mt_eint_registration(CUST_EINT_ALS_NUM, CUST_EINT_ALS_TYPE, ltr559_eint_func, 0);
	mt_eint_unmask(CUST_EINT_ALS_NUM);

#endif

	return 0;
}

/*----------------------------------------------------------------------------*/
/*for interrupt work mode support -- by liaoxl.lenovo 12.08.2011*/
static int ltr559_check_and_clear_intr(struct i2c_client *client)
{
/* *** */
	int res, intp, intl;
	u8 buffer[2];
	u8 temp;

	APS_FUN();
	buffer[0] = LTR559_ALS_PS_STATUS;
	res = i2c_master_send(client, buffer, 0x1);
	if (res <= 0)
		goto EXIT_ERR;
	res = i2c_master_recv(client, buffer, 0x1);
	if (res <= 0)
		goto EXIT_ERR;
	temp = buffer[0];
	res = 1;
	intp = 0;
	intl = 0;
	if (0 != (buffer[0] & 0x02)) {
		res = 0;
		intp = 1;
	}
	if (0 != (buffer[0] & 0x08)) {
		res = 0;
		intl = 1;
	}

	if (0 == res) {
		if ((1 == intp) && (0 == intl))
			buffer[1] = buffer[0] & 0xfD;
		else if ((0 == intp) && (1 == intl))
			buffer[1] = buffer[0] & 0xf7;
		else
			buffer[1] = buffer[0] & 0xf5;
		buffer[0] = LTR559_ALS_PS_STATUS;
		res = i2c_master_send(client, buffer, 0x2);
		if (res <= 0)
			goto EXIT_ERR;
		else
			res = 0;
	}
	return res;

EXIT_ERR:
	APS_ERR("ltr559_check_and_clear_intr fail\n");
	return 1;

}

/*----------------------------------------------------------------------------*/


static int ltr559_check_intr(struct i2c_client *client)
{
	int res, intp, intl;
	u8 buffer[2];

	APS_FUN();

	buffer[0] = LTR559_ALS_PS_STATUS;
	res = i2c_master_send(client, buffer, 0x1);
	if (res <= 0)
		goto EXIT_ERR;
	res = i2c_master_recv(client, buffer, 0x1);
	if (res <= 0)
		goto EXIT_ERR;
	res = 1;
	intp = 0;
	intl = 0;
	if (0 != (buffer[0] & 0x02)) {
		res = 0;	/* Ps int */
		intp = 1;
	}
	if (0 != (buffer[0] & 0x08)) {
		res = 0;
		intl = 1;
	}

	return res;

EXIT_ERR:
	APS_ERR("ltr559_check_intr fail\n");
	return 1;
}

static int ltr559_clear_intr(struct i2c_client *client)
{
	int res;
	u8 buffer[2];

	APS_FUN();

	buffer[0] = LTR559_ALS_PS_STATUS;
	res = i2c_master_send(client, buffer, 0x1);
	if (res <= 0)
		goto EXIT_ERR;
	res = i2c_master_recv(client, buffer, 0x1);
	if (res <= 0)
		goto EXIT_ERR;
	APS_DBG("buffer[0] = %d\n", buffer[0]);
	buffer[1] = buffer[0] & 0x01;
	buffer[0] = LTR559_ALS_PS_STATUS;

	res = i2c_master_send(client, buffer, 0x2);
	if (res <= 0)
		goto EXIT_ERR;
	else
		res = 0;
	return res;

EXIT_ERR:
	APS_ERR("ltr559_check_and_clear_intr fail\n");
	return 1;
}

/* wisky-lxh@20150108 */
static int ltr559_init_device(void)
{
	int error = 0;

	error = ltr559_i2c_write_reg(LTR559_PS_LED, 0x7F);
	if (error < 0) {
		APS_LOG("ltr559_ps_enable() error3...\n");
		return error;
	}

	error = ltr559_i2c_write_reg(LTR559_PS_N_PULSES, 0x4);	/* modify by hzb */
	if (error < 0) {
		APS_LOG("ltr559_ps_enable() error2\n");
		return error;
	}

	error = ltr559_i2c_write_reg(LTR559_ALS_MEAS_RATE, 0x1);
	if (error < 0) {
		APS_LOG("ltr559_ps_enable() error2\n");
		return error;
	}

	error = ltr559_i2c_write_reg(LTR559_PS_THRES_UP_0, ps_trigger_high & 0xff);
	if (error < 0) {
		APS_LOG("ltr559_ps_enable() error2\n");
		return error;
	}
	error = ltr559_i2c_write_reg(LTR559_PS_THRES_UP_1, (ps_trigger_high >> 8) & 0X07);
	if (error < 0) {
		APS_LOG("ltr559_ps_enable() error2\n");
		return error;
	}
	error = ltr559_i2c_write_reg(LTR559_PS_THRES_LOW_0, 0x0);
	if (error < 0) {
		APS_LOG("ltr559_ps_enable() error2\n");
		return error;
	}
	error = ltr559_i2c_write_reg(LTR559_PS_THRES_LOW_1, 0x0);
	if (error < 0) {
		APS_LOG("ltr559_ps_enable() error2\n");
		return error;
	}

	mdelay(WAKEUP_DELAY);

	return error;

}

/* end-wisky-lxh */

static int ltr559_devinit(void)
{
	int res;
	/* int init_ps_gain; */
	/* int init_als_gain; */
	u8 databuf[2];

	struct i2c_client *client = ltr559_obj->client;

	struct ltr559_priv *obj = ltr559_obj;

	mdelay(PON_DELAY);

	/* soft reset when device init add by steven */
	databuf[0] = LTR559_ALS_CONTR;
	databuf[1] = 0x02;
	res = i2c_master_send(client, databuf, 0x2);
	if (res <= 0) {
		goto EXIT_ERR;
		return ltr559_ERR_I2C;
	}

	/*for interrupt work mode support */
	if (0 == obj->hw.polling_mode_ps) {
		APS_LOG("eint enable");
		ltr559_ps_set_thres();

		databuf[0] = LTR559_INTERRUPT;
		databuf[1] = 0x01;
		res = i2c_master_send(client, databuf, 0x2);
		if (res <= 0) {
			goto EXIT_ERR;
			return ltr559_ERR_I2C;
		}

		databuf[0] = LTR559_INTERRUPT_PERSIST;
		databuf[1] = 0x20;
		res = i2c_master_send(client, databuf, 0x2);
		if (res <= 0) {
			goto EXIT_ERR;
			return ltr559_ERR_I2C;
		}

	}
	/* wisky-lxh@20150108 */
	res = ltr559_init_device();
	if (res < 0) {
		APS_ERR("ltr559_init_devicet: %d\n", res);
		return res;
	}
	/* end-wisky-lxh */
	res = ltr559_check_and_clear_intr(client);
	if (res < 0) {
		APS_ERR("check/clear intr fail: %d\n", res);
		return res;
	}
	res = 0;

EXIT_ERR:
	APS_ERR("init dev: %d\n", res);
	return res;

}

/*----------------------------------------------------------------------------*/
static int ltr559_get_als_value(struct ltr559_priv *obj, u16 als)
{
	int idx;
	int invalid;
	unsigned int lum;
	static unsigned int last_lum;
	static int last_ratio;
	struct timeval tv = { 0 };
	struct rtc_time tm_android;
	struct timeval tv_android = { 0 };

	do_gettimeofday(&tv);
	tv_android = tv;
	tv_android.tv_sec -= sys_tz.tz_minuteswest * 60;
	rtc_time_to_tm(tv_android.tv_sec, &tm_android);

	invalid = 0;
	last_lum = 0;
	last_ratio = 0;
	for (idx = 0; idx < C_CUST_ALS_LEVEL; idx++) {
		if (als < als_level[current_tp][current_color_temp][idx])
			break;
	}

	APS_DBG("als  = %d; idx=%d;current_color_temp=%d;current_tp=%d;\n", als, idx,
		current_color_temp, current_tp);
	if (idx >= C_CUST_ALS_LEVEL) {
		APS_LOG("exceed range\n");
		idx = C_CUST_ALS_LEVEL - 1;
	}

	if (1 == atomic_read(&obj->als_deb_on)) {
		unsigned long endt = atomic_read(&obj->als_deb_end);

		if (time_after(jiffies, endt))
			atomic_set(&obj->als_deb_on, 0);
		if (1 == atomic_read(&obj->als_deb_on))
			invalid = 1;
	}

	if (!invalid) {
		APS_DBG("ALS: %05d < %05d\n", als, als_level[current_tp][current_color_temp][idx]);

		lum = (als_value[idx] - als_value[idx - 1]) * (als - als_level[current_tp]
							       [current_color_temp][idx -
										    1]) /
		    (als_level[current_tp][current_color_temp][idx] -
		     als_level[current_tp][current_color_temp][idx - 1]);

		lum += als_value[idx - 1];

		if (lum != last_lum || current_color_ratio != last_ratio) {
			last_lum = lum;
			last_ratio = current_color_ratio;
			APS_DBG("ALS: %02d:%02d:%02d.%3d. LSY lum=%05d ;ratio=%d;als=%d;%d;%d,\n",
				tm_android.tm_hour, tm_android.tm_min, tm_android.tm_sec,
				(unsigned int)tv_android.tv_usec, lum, current_color_ratio, als,
				current_color_temp, current_tp);
		}
		return lum;
		/* return obj->hw.als_value[idx]; */
	} else {
		APS_ERR("ALS error: %05d => %05d (-1)\n", als, als_value[idx]);
		return -1;
	}
}

/*----------------------------------------------------------------------------*/
static int ltr559_get_ps_value(struct ltr559_priv *obj, u16 ps)
{
	int val;
	/* mask = atomic_read(&obj->ps_mask); */
	int invalid = 0;

	static int val_temp = 5;

	if (ps > atomic_read(&obj->ps_thd_val_high)) {
		val = 0;	/*close */
		val_temp = 0;
		intr_flag_value = 0;
	}
	/* else if((ps < atomic_read(&obj->ps_thd_val_low))&&(temp_ps[0]  < atomic_read(&obj->ps_thd_val_low))) */
	else if (ps < atomic_read(&obj->ps_thd_val_low)) {
		val = 1;	/*far away */
		val_temp = 1;
		intr_flag_value = 1;
	} else
		val = val_temp;

	if (atomic_read(&obj->ps_suspend)) {
		invalid = 1;
	} else if (1 == atomic_read(&obj->ps_deb_on)) {
		unsigned long endt = atomic_read(&obj->ps_deb_end);

		if (time_after(jiffies, endt))
			atomic_set(&obj->ps_deb_on, 0);
		if (1 == atomic_read(&obj->ps_deb_on))
			invalid = 1;
	} else if (obj->als > 50000) {
		/* invalid = 1; */
		APS_DBG("ligh too high will result to failt proximiy\n");
		return 1;	/*far away */
	}

	if (!invalid) {
		APS_DBG("PS:  %05d => %05d\n", ps, val);
		return val;
	} else
		return -1;
}

/*----------------------------------------------------------------------------*/


/*----------------------------------------------------------------------------*/
/*for interrupt work mode support */
static void ltr559_eint_work(struct work_struct *work)
{
	struct ltr559_priv *obj =
	    (struct ltr559_priv *)container_of(work, struct ltr559_priv, eint_work);
	int err;
	struct hwm_sensor_data sensor_data;
/* int temp_noise; */
/* u8 buffer[1]; */
/* u8 reg_value[1]; */
	u8 databuf[2];
	int res = 0;

	mutex_lock(&Ltr559_lock);
	APS_FUN();
	err = ltr559_check_intr(obj->client);
	if (err < 0) {
		APS_ERR("ltr559_eint_work error check intrs: %d\n", err);
	} else {
		/* get raw data */
		obj->ps = ltr559_ps_read();
		if (obj->ps < 0) {
			err = -1;
			goto fun_out;
		}

		APS_DBG("ltr559_eint_work rawdata ps=%d als_ch0=%d!\n", obj->ps, obj->als);
		sensor_data.values[0] = ltr559_get_ps_value(obj, obj->ps);
		/* sensor_data.values[1] = obj->ps; */
		sensor_data.value_divide = 1;
		sensor_data.status = SENSOR_STATUS_ACCURACY_MEDIUM;
		/*sigal interrupt function add*/
		APS_DBG("intr_flag_value=%d\n", intr_flag_value);
		if (!intr_flag_value) {
			APS_DBG(" interrupt value ps will < ps_threshold_low");

			databuf[0] = LTR559_PS_THRES_LOW_0;
			databuf[1] = (u8) ((atomic_read(&obj->ps_thd_val_low)) & 0x00FF);
			res = i2c_master_send(obj->client, databuf, 0x2);
			if (res <= 0)
				goto fun_out;
			databuf[0] = LTR559_PS_THRES_LOW_1;
			databuf[1] = (u8) (((atomic_read(&obj->ps_thd_val_low)) & 0xFF00) >> 8);
			res = i2c_master_send(obj->client, databuf, 0x2);
			if (res <= 0)
				goto fun_out;
			databuf[0] = LTR559_PS_THRES_UP_0;
			databuf[1] = (u8) (0x00FF);
			res = i2c_master_send(obj->client, databuf, 0x2);
			if (res <= 0)
				goto fun_out;
			databuf[0] = LTR559_PS_THRES_UP_1;
			databuf[1] = (u8) ((0xFF00) >> 8);
			res = i2c_master_send(obj->client, databuf, 0x2);
			/* APS_DBG("obj->ps_thd_val_low=%ld !\n",obj->ps_thd_val_low); */
			if (res <= 0)
				goto fun_out;
		} else {
			if ((ps_cali.noice > -1) && (ps_cali.close > ps_cali.far_away)
			    && (obj->ps < dynamic_calibrate)) {
				int temp_high = ps_cali.close - ps_cali.noice + obj->ps;
				int temp_low = ps_cali.far_away - ps_cali.noice + obj->ps;

				if ((temp_high < 1600) && (temp_low > 10)
				    && (obj->ps > ps_cali.noice) && (dynamic_calibrate <
					((ps_cali.close + ps_cali.far_away) / 2))) {
					atomic_set(&obj->ps_thd_val_high, temp_high);
					atomic_set(&obj->ps_thd_val_low, temp_low);
				} else {
					atomic_set(&obj->ps_thd_val_high, ps_cali.close);
					atomic_set(&obj->ps_thd_val_low, ps_cali.far_away);
				}
				dynamic_calibrate = obj->ps;
			}
			APS_LOG(
			       "%s:cali noice=%d; cali high=%d; cali low=%d; curr noice=%d; high=%d; low=%d\n",
			       __func__, ps_cali.noice, ps_cali.close, ps_cali.far_away,
			       dynamic_calibrate, atomic_read(&obj->ps_thd_val_high),
			       atomic_read(&obj->ps_thd_val_low));
			/* wake_lock_timeout(&ps_wake_lock,ps_wakeup_timeout*HZ); */
			databuf[0] = LTR559_PS_THRES_LOW_0;
			databuf[1] = (u8) (0 & 0x00FF);	/* get the noise one time */
			res = i2c_master_send(obj->client, databuf, 0x2);
			if (res <= 0)
				goto fun_out;
			databuf[0] = LTR559_PS_THRES_LOW_1;
			databuf[1] = (u8) ((0 & 0xFF00) >> 8);
			res = i2c_master_send(obj->client, databuf, 0x2);
			if (res <= 0)
				goto fun_out;
			databuf[0] = LTR559_PS_THRES_UP_0;
			databuf[1] = (u8) ((atomic_read(&obj->ps_thd_val_high)) & 0x00FF);
			res = i2c_master_send(obj->client, databuf, 0x2);
			if (res <= 0)
				goto fun_out;
			databuf[0] = LTR559_PS_THRES_UP_1;
			databuf[1] = (u8) (((atomic_read(&obj->ps_thd_val_high)) & 0xFF00) >> 8);
			res = i2c_master_send(obj->client, databuf, 0x2);
/* APS_DBG("obj->ps_thd_val_high=%ld !\n",obj->ps_thd_val_high); */
			if (res <= 0)
				goto fun_out;
		}

		sensor_data.value_divide = 1;
		sensor_data.status = SENSOR_STATUS_ACCURACY_MEDIUM;
		/* let up layer to know */
#ifdef USE_HWMSEN
		err = hwmsen_get_interrupt_data(ID_PROXIMITY, &sensor_data);
		if (err)
			APS_ERR("call hwmsen_get_interrupt_data fail = %d\n", err);
#else
		err = ps_report_interrupt_data(intr_flag_value);
#endif
	}
	ltr559_clear_intr(obj->client);
	enable_irq(obj->irq);
fun_out:
	mutex_unlock(&Ltr559_lock);
}



/******************************************************************************
 * Function Configuration
******************************************************************************/
static int ltr559_i2c_suspend(struct i2c_client *client, pm_message_t msg)
{
	return 0;
}

/*----------------------------------------------------------------------------*/
static int ltr559_i2c_resume(struct i2c_client *client)
{
	return 0;
}

#if defined(CONFIG_HAS_EARLYSUSPEND)
static void ltr559_early_suspend(struct early_suspend *h)
{				/*early_suspend is only applied for ALS */
	struct ltr559_priv *obj = container_of(h, struct ltr559_priv, early_drv);
	int err;

	APS_FUN();

	if (!obj) {
		APS_ERR("null pointer!!\n");
		return;
	}

	mutex_lock(&Ltr559_lock);
	atomic_set(&obj->als_suspend, 1);
	err = ltr559_als_disable();
	mutex_unlock(&Ltr559_lock);
	if (err < 0)
		APS_ERR("disable als fail: %d\n", err);
}

static void ltr559_late_resume(struct early_suspend *h)
{				/*early_suspend is only applied for ALS */
	struct ltr559_priv *obj = container_of(h, struct ltr559_priv, early_drv);
	int err;

	APS_FUN();

	if (!obj) {
		APS_ERR("null pointer!!\n");
		return;
	}

	mutex_lock(&Ltr559_lock);
	atomic_set(&obj->als_suspend, 0);
	if (test_bit(CMC_BIT_ALS, &obj->enable)) {
		err = ltr559_als_enable(als_gainrange);
		if (err < 0)
			APS_ERR("enable als fail: %d\n", err);
	}
	mutex_unlock(&Ltr559_lock);
}
#endif
int ltr559_ps_operate(void *self, uint32_t command, void *buff_in, int size_in,
		      void *buff_out, int size_out, int *actualout)
{
	int err = 0;
	int value;
	struct hwm_sensor_data *sensor_data;
	struct ltr559_priv *obj = (struct ltr559_priv *)self;

	mutex_lock(&Ltr559_lock);

	switch (command) {
	case SENSOR_DELAY:
		if ((buff_in == NULL) || (size_in < sizeof(int))) {
			APS_ERR("Set delay parameter error!\n");
			err = -EINVAL;
		}
		/* Do nothing */
		break;

	case SENSOR_ENABLE:
		if ((buff_in == NULL) || (size_in < sizeof(int))) {
			APS_ERR("Enable sensor parameter error!\n");
			err = -EINVAL;
		} else {
			value = *(int *)buff_in;
			if (value) {
				err = ltr559_ps_enable(ps_gainrange);
				if (err < 0) {
					APS_ERR("enable ps fail: %d\n", err);
					return -1;
				}
				set_bit(CMC_BIT_PS, &obj->enable);
			} else {
				err = ltr559_ps_disable();
				if (err < 0) {
					APS_ERR("disable ps fail: %d\n", err);
					return -1;
				}
				clear_bit(CMC_BIT_PS, &obj->enable);
			}
		}
		break;

	case SENSOR_GET_DATA:
		if ((buff_out == NULL) || (size_out < sizeof(struct hwm_sensor_data))) {
			APS_ERR("get sensor data parameter error!\n");
			err = -EINVAL;
		} else {
			APS_LOG("get sensor ps data !\n");
			sensor_data = (struct hwm_sensor_data *)buff_out;
			obj->ps = ltr559_ps_read();
			if (obj->ps < 0) {
				err = -1;
				break;
			}
			sensor_data->values[0] = ltr559_get_ps_value(obj, obj->ps);
			/* sensor_data->values[1] = obj->ps;             //steven polling mode *#*#3646633#*#* */
			sensor_data->value_divide = 1;
			sensor_data->status = SENSOR_STATUS_ACCURACY_MEDIUM;
		}
		break;
	default:
		APS_ERR("proxmy sensor operate function no this parameter %d!\n", command);
		err = -1;
		break;
	}

	mutex_unlock(&Ltr559_lock);

	return err;
}

int ltr559_als_operate(void *self, uint32_t command, void *buff_in, int size_in,
		       void *buff_out, int size_out, int *actualout)
{
	int err = 0;
	int value;
	struct hwm_sensor_data *sensor_data;
	struct ltr559_priv *obj = (struct ltr559_priv *)self;

	mutex_lock(&Ltr559_lock);
	switch (command) {
	case SENSOR_DELAY:
		if ((buff_in == NULL) || (size_in < sizeof(int))) {
			APS_ERR("Set delay parameter error!\n");
			err = -EINVAL;
		}
		/* Do nothing */
		break;

	case SENSOR_ENABLE:
		if ((buff_in == NULL) || (size_in < sizeof(int))) {
			APS_ERR("Enable sensor parameter error!\n");
			err = -EINVAL;
		} else {
			value = *(int *)buff_in;
			if (value) {
				err = ltr559_als_enable(als_gainrange);
				if (err < 0) {
					APS_ERR("enable als fail: %d\n", err);
					return -1;
				}
				set_bit(CMC_BIT_ALS, &obj->enable);
			} else {
				err = ltr559_als_disable();
				if (err < 0) {
					APS_ERR("disable als fail: %d\n", err);
					return -1;
				}
				clear_bit(CMC_BIT_ALS, &obj->enable);
			}

		}
		break;

	case SENSOR_GET_DATA:
		if ((buff_out == NULL) || (size_out < sizeof(struct hwm_sensor_data))) {
			APS_ERR("get sensor data parameter error!\n");
			err = -EINVAL;
		} else {
			APS_ERR("get sensor als data !\n");
			sensor_data = (struct hwm_sensor_data *)buff_out;
			obj->als = ltr559_als_read(als_gainrange);
#if defined(MTK_AAL_SUPPORT)
			sensor_data->values[0] = ltr559_get_als_value(obj, obj->als);	/* wisky-lxh@20150206 */
#else
			sensor_data->values[0] = ltr559_get_als_value(obj, obj->als);
#endif
			sensor_data->value_divide = 1;
			sensor_data->status = SENSOR_STATUS_ACCURACY_MEDIUM;
		}
		break;
	default:
		APS_ERR("light sensor operate function no this parameter %d!\n", command);
		err = -1;
		break;
	}

	mutex_unlock(&Ltr559_lock);
	return err;
}


/*----------------------------------------------------------------------------*/
static int ltr559_i2c_detect(struct i2c_client *client, struct i2c_board_info *info)
{
	strlcpy(info->type, LTR559_DEV_NAME, sizeof(info->type));
	return 0;
}

/* if use  this typ of enable , Gsensor should report inputEvent(x, y, z ,stats, div) to HAL */
static int als_open_report_data(int open)
{
	/* should queuq work to report event if  is_report_input_direct=true */
	return 0;
}

/* if use  this typ of enable , Gsensor only enabled but not report inputEvent to HAL */

static int als_enable_nodata(int en)
{
	int res = 0;
#ifdef CUSTOM_KERNEL_SENSORHUB
	SCP_SENSOR_HUB_DATA req;
	int len;
#endif				/* #ifdef CUSTOM_KERNEL_SENSORHUB */

	mutex_lock(&Ltr559_lock);
	APS_LOG("ltr559_obj als enable value = %d\n", en);
	if (ltr559_obj->als_flush) {
		if (en) {
			APS_LOG("is not flush, will call als_flush in als_enable_nodata\n");
			als_flush();
		} else
			ltr559_obj->als_flush = false;
	}
#ifdef CUSTOM_KERNEL_SENSORHUB
	if (atomic_read(&ltr559_obj->init_done)) {
		req.activate_req.sensorType = ID_LIGHT;
		req.activate_req.action = SENSOR_HUB_ACTIVATE;
		req.activate_req.enable = en;
		len = sizeof(req.activate_req);
		res = SCP_sensorHub_req_send(&req, &len, 1);
	} else {
		APS_LOG("sensor hub has not been ready!!\n");
	}
	/* mutex_lock(&read_lock); */
	if (en)
		set_bit(CMC_BIT_ALS, &ltr559_obj->enable);
	else
		clear_bit(CMC_BIT_ALS, &ltr559_obj->enable);
	/* mutex_unlock(&read_lock); */
	/* return 0; */
#else				/* #ifdef CUSTOM_KERNEL_SENSORHUB */
	if (!ltr559_obj) {
		APS_ERR("ltr559_obj is null!!\n");
		goto err_out;
	}

	if (en) {
		res = ltr559_als_enable(als_gainrange);
		if (res < 0)
			goto err_out;
		set_bit(CMC_BIT_ALS, &ltr559_obj->enable);
	} else {
		res = ltr559_als_disable();
		if (res < 0)
			goto err_out;
		clear_bit(CMC_BIT_ALS, &ltr559_obj->enable);
	}
#endif				/* #ifdef CUSTOM_KERNEL_SENSORHUB */
	mutex_unlock(&Ltr559_lock);
	return 0;
err_out:
	APS_ERR("als_enable_nodata is failed!!\n");
	mutex_unlock(&Ltr559_lock);
	return -1;
}

static int als_set_delay(u64 ns)
{
	return 0;
}

static int als_get_data(int *value, int *status)
{
	int err = 0;
#ifdef CUSTOM_KERNEL_SENSORHUB
	SCP_SENSOR_HUB_DATA req;
	int len;
#else
	struct ltr559_priv *obj = NULL;
#endif				/* #ifdef CUSTOM_KERNEL_SENSORHUB */
	APS_FUN(f);
	mutex_lock(&Ltr559_lock);
#ifdef CUSTOM_KERNEL_SENSORHUB
	if (atomic_read(&ltr559_obj->init_done)) {
		req.get_data_req.sensorType = ID_LIGHT;
		req.get_data_req.action = SENSOR_HUB_GET_DATA;
		len = sizeof(req.get_data_req);
		err = SCP_sensorHub_req_send(&req, &len, 1);
		if (err) {
			APS_ERR("SCP_sensorHub_req_send fail!\n");
		} else {
			*value = req.get_data_rsp.int16_Data[0];
			*status = SENSOR_STATUS_ACCURACY_MEDIUM;
		}

		if (atomic_read(&ltr559_obj->trace) & CMC_TRC_PS_DATA) {
			APS_LOG("value = %d\n", *value);
			/* show data */
		}
	} else {
		APS_ERR("sensor hub hat not been ready!!\n");
		err = -1;
	}
#else				/* #ifdef CUSTOM_KERNEL_SENSORHUB */
	if (!ltr559_obj) {
		APS_ERR("ltr559_obj is null!!\n");
		goto err_out;
	}
	obj = ltr559_obj;

	obj->als = ltr559_als_read(als_gainrange);
	if (obj->als < 0)
		goto err_out;

	*value = ltr559_get_als_value(obj, obj->als);
	*status = SENSOR_STATUS_ACCURACY_MEDIUM;
#endif				/* #ifdef CUSTOM_KERNEL_SENSORHUB */
	mutex_unlock(&Ltr559_lock);
	return err;

err_out:
	APS_ERR("als_get_data is failed!!\n");
	mutex_unlock(&Ltr559_lock);
	return -1;
}

/* if use  this typ of enable , Gsensor should report inputEvent(x, y, z ,stats, div) to HAL */
static int ps_open_report_data(int open)
{
	/* should queuq work to report event if  is_report_input_direct=true */
	return 0;
}

/* if use  this typ of enable , Gsensor only enabled but not report inputEvent to HAL */

static int ps_enable_nodata(int en)
{
	int res = 0;
#ifdef CUSTOM_KERNEL_SENSORHUB
	SCP_SENSOR_HUB_DATA req;
	int len;
#endif				/* #ifdef CUSTOM_KERNEL_SENSORHUB */

	mutex_lock(&Ltr559_lock);
	APS_LOG("ltr559_obj ps enable value = %d\n", en);
	if (ltr559_obj->ps_flush) {
		if (en) {
			APS_LOG("is not flush, will call ps_flush in als_enable_nodata\n");
			ps_flush();
		} else
			ltr559_obj->ps_flush = false;
	}
#ifdef CUSTOM_KERNEL_SENSORHUB
	if (atomic_read(&ltr559_obj->init_done)) {
		req.activate_req.sensorType = ID_PROXIMITY;
		req.activate_req.action = SENSOR_HUB_ACTIVATE;
		req.activate_req.enable = en;
		len = sizeof(req.activate_req);
		res = SCP_sensorHub_req_send(&req, &len, 1);
	} else
		APS_ERR("sensor hub has not been ready!!\n");
	/* mutex_lock(&read_lock); */
	if (en)
		set_bit(CMC_BIT_PS, &ltr559_obj->enable);
	else
		clear_bit(CMC_BIT_PS, &ltr559_obj->enable);
	/* mutex_unlock(&read_lock); */
#else				/* #ifdef CUSTOM_KERNEL_SENSORHUB */

	if (!ltr559_obj) {
		APS_ERR("ltr559_obj is null!!\n");
		goto err_out;
	}

	if (en) {
		res = ltr559_ps_enable(ps_gainrange);
		if (res < 0)
			goto err_out;
		set_bit(CMC_BIT_PS, &ltr559_obj->enable);
	} else {
		res = ltr559_ps_disable();
		if (res < 0)
			goto err_out;
		clear_bit(CMC_BIT_PS, &ltr559_obj->enable);
	}
#endif				/* #ifdef CUSTOM_KERNEL_SENSORHUB */
	mutex_unlock(&Ltr559_lock);
	return 0;
err_out:
	APS_ERR("ps_enable_nodata is failed!!\n");
	mutex_unlock(&Ltr559_lock);
	return -1;
}

static int ps_set_delay(u64 ns)
{
	return 0;
}

static int ps_get_data(int *value, int *status)
{
	int err = 0;
#ifdef CUSTOM_KERNEL_SENSORHUB
	SCP_SENSOR_HUB_DATA req;
	int len;
#endif				/* #ifdef CUSTOM_KERNEL_SENSORHUB */
	mutex_lock(&Ltr559_lock);
	APS_FUN(f);
#ifdef CUSTOM_KERNEL_SENSORHUB
	if (atomic_read(&ltr559_obj->init_done)) {
		req.get_data_req.sensorType = ID_PROXIMITY;
		req.get_data_req.action = SENSOR_HUB_GET_DATA;
		len = sizeof(req.get_data_req);
		err = SCP_sensorHub_req_send(&req, &len, 1);
		if (err) {
			APS_ERR("SCP_sensorHub_req_send fail!\n");
			*value = -1;
			err = -1;
		} else {
			*value = req.get_data_rsp.int16_Data[0];
			*status = SENSOR_STATUS_ACCURACY_MEDIUM;
		}

		if (atomic_read(&ltr559_obj->trace) & CMC_TRC_PS_DATA)
			APS_LOG("value = %d\n", *value);
	} else {
		APS_ERR("sensor hub has not been ready!!\n");
		err = -1;
	}
#else				/* #ifdef CUSTOM_KERNEL_SENSORHUB */
	if (!ltr559_obj) {
		APS_ERR("ltr559_obj is null!!\n");
		return -1;
	}

	ltr559_obj->ps = ltr559_ps_read();
	if (ltr559_obj->ps < 0)
		goto err_out;

	*value = ltr559_get_ps_value(ltr559_obj, ltr559_obj->ps);
	*status = SENSOR_STATUS_ACCURACY_MEDIUM;

#endif				/* #ifdef CUSTOM_KERNEL_SENSORHUB */
	mutex_unlock(&Ltr559_lock);
	return err;
err_out:
	APS_ERR("ps_get_data is failed!!\n");
	mutex_unlock(&Ltr559_lock);
	return -1;
}

static int als_batch(int flag, int64_t samplingPeriodNs, int64_t maxBatchReportLatencyNs)
{
	int value = 0;

	value = (int)samplingPeriodNs / 1000 / 1000;
	/*FIX  ME */

	APS_LOG("ltr559 als set delay = (%d) ok.\n", value);
	return 0;
}

static int als_flush(void)
{
	int err = 0;
	/*Only flush after sensor was enabled*/
	if (!test_bit(CMC_BIT_ALS, &ltr559_obj->enable)) {
		ltr559_obj->als_flush = true;
		return 0;
	}
	err = als_flush_report();
	if (err >= 0)
		ltr559_obj->als_flush = false;
	return err;
}

static int ps_batch(int flag, int64_t samplingPeriodNs, int64_t maxBatchReportLatencyNs)
{
	int value = 0;

	value = (int)samplingPeriodNs / 1000 / 1000;
	/*FIX  ME */

	APS_LOG("ltr559 ps set delay = (%d) ok.\n", value);
	return 0;
}

static int ps_flush(void)
{
	int err = 0;
	/*Only flash after sensor was enabled*/
	if (!test_bit(CMC_BIT_PS, &ltr559_obj->enable)) {
		ltr559_obj->ps_flush = true;
		return 0;
	}
	err = ps_flush_report();
	if (err >= 0)
		ltr559_obj->ps_flush = false;
	return err;
}

/*----------------------------------------------------------------------------*/
static int ltr559_i2c_probe(struct i2c_client *client, const struct i2c_device_id *id)
{
	struct ltr559_priv *obj;
#ifdef USE_HWMSEN
	struct hwmsen_object obj_ps, obj_als;
#endif
	int err = 0;

	struct als_control_path als_ctl = { 0 };
	struct als_data_path als_data = { 0 };
	struct ps_control_path ps_ctl = { 0 };
	struct ps_data_path ps_data = { 0 };

	obj = kzalloc(sizeof(*obj), GFP_KERNEL);

	if (!obj) {
		err = -ENOMEM;
		goto exit;
	}
	err = get_alsps_dts_func_ltr559(client->dev.of_node, &obj->hw);
	if (err < 0) {
		APS_ERR("get dts info fail\n");
		goto exit_init_failed;
	}
	ltr559_obj = obj;

	INIT_WORK(&obj->eint_work, ltr559_eint_work);
	obj->client = client;
	i2c_set_clientdata(client, obj);
	atomic_set(&obj->als_debounce, 300);
	atomic_set(&obj->als_deb_on, 0);
	atomic_set(&obj->als_deb_end, 0);
	atomic_set(&obj->ps_debounce, 300);
	atomic_set(&obj->ps_deb_on, 0);
	atomic_set(&obj->ps_deb_end, 0);
	atomic_set(&obj->ps_mask, 0);
	atomic_set(&obj->als_suspend, 0);
	atomic_set(&obj->ps_thd_val_high, obj->hw.ps_threshold_high);
	atomic_set(&obj->ps_thd_val_low, obj->hw.ps_threshold_low);
	/* atomic_set(&obj->als_cmd_val, 0xDF); */
	/* atomic_set(&obj->ps_cmd_val,  0xC1); */
	atomic_set(&obj->ps_thd_val, obj->hw.ps_threshold);
	obj->enable = 0;
	obj->pending_intr = 0;
	memset(als_level, 0, sizeof(als_level));
	/* obj->als_level_num = sizeof(obj->hw.als_level)/sizeof(obj->hw.als_level[0]); */
	/* obj->als_value_num = sizeof(obj->hw.als_value)/sizeof(obj->hw.als_value[0]); */
	/* (1/Gain)*(400/Tine), this value is fix after init ATIME and CONTROL register value */
	obj->als_modulus = (400 * 100) / (16 * 150);
	/* (400)/16*2.72 here is amplify *100 */
	BUG_ON(sizeof(als_level) != sizeof(obj->hw.als_level));
	memcpy(als_level, obj->hw.als_level, sizeof(als_level));
	BUG_ON(sizeof(als_value) != sizeof(obj->hw.als_value));
	memcpy(als_value, obj->hw.als_value, sizeof(als_value));
	atomic_set(&obj->i2c_retry, 3);
	set_bit(CMC_BIT_ALS, &obj->enable);
	set_bit(CMC_BIT_PS, &obj->enable);

	APS_LOG("ltr559_devinit() start...!\n");
	ltr559_i2c_client = client;
	obj->irq_node = client->dev.of_node;
	err = ltr559_devinit();
	if (err)
		goto exit_init_failed;
	APS_LOG("ltr559_devinit() ...OK!\n");

	/* printk("@@@@@@ manufacturer value:%x\n",ltr559_i2c_read_reg(0x87)); */
	/* Register sysfs attribute */
	err = ltr559_create_attr(&ltr559_init_info.platform_diver_addr->driver);
	if (err) {
		APS_ERR("create attribute err = %d\n", err);
		goto exit_create_attr_failed;
	}
#ifdef USE_HWMSEN
	obj_ps.self = ltr559_obj;
	/*for interrupt work mode support -- by liaoxl.lenovo 12.08.2011 */
	if (1 == obj->hw.polling_mode_ps)
		obj_ps.polling = 1;
	else
		obj_ps.polling = 0;
	obj_ps.sensor_operate = ltr559_ps_operate;
	err = hwmsen_attach(ID_PROXIMITY, &obj_ps)
	if (err) {
		APS_ERR("attach fail = %d\n", err);
		goto exit_create_attr_failed;
	}

	obj_als.self = ltr559_obj;
	obj_als.polling = 1;
	obj_als.sensor_operate = ltr559_als_operate;
	err = hwmsen_attach(ID_LIGHT, &obj_als);
	if (err) {
		APS_ERR("attach fail = %d\n", err);
		goto exit_create_attr_failed;
	}
#else

	als_ctl.is_use_common_factory = false;
	ps_ctl.is_use_common_factory = false;

	als_ctl.open_report_data = als_open_report_data;
	als_ctl.enable_nodata = als_enable_nodata;
	als_ctl.set_delay = als_set_delay;
	als_ctl.batch = als_batch;
	als_ctl.flush = als_flush;
	als_ctl.is_report_input_direct = false;
#ifdef CUSTOM_KERNEL_SENSORHUB
	als_ctl.is_support_batch = obj->hw.is_batch_supported_als;
#else
	als_ctl.is_support_batch = false;
#endif

	err = als_register_control_path(&als_ctl);
	if (err) {
		APS_ERR("register fail = %d\n", err);
		goto exit_sensor_obj_attach_fail;
	}

	als_data.get_data = als_get_data;
	als_data.vender_div = 100;
	err = als_register_data_path(&als_data);
	if (err) {
		APS_ERR("tregister fail = %d\n", err);
		goto exit_sensor_obj_attach_fail;
	}


	ps_ctl.open_report_data = ps_open_report_data;
	ps_ctl.enable_nodata = ps_enable_nodata;
	ps_ctl.set_delay = ps_set_delay;
	ps_ctl.batch = ps_batch;
	ps_ctl.flush = ps_flush;
	ps_ctl.is_report_input_direct = false;
#ifdef CUSTOM_KERNEL_SENSORHUB
	ps_ctl.is_support_batch = obj->hw.is_batch_supported_ps;
#else
	ps_ctl.is_support_batch = false;
#endif

	err = ps_register_control_path(&ps_ctl);
	if (err) {
		APS_ERR("register fail = %d\n", err);
		goto exit_sensor_obj_attach_fail;
	}

	ps_data.get_data = ps_get_data;
	ps_data.vender_div = 100;
	err = ps_register_data_path(&ps_data);
	if (err) {
		APS_ERR("tregister fail = %d\n", err);
		goto exit_sensor_obj_attach_fail;
	}
#endif

	if ((ltr559_setup_eint(client)) != 0) {
		APS_ERR("setup eint fail\n");
		goto setup_eint_fail;
	}
#if defined(CONFIG_HAS_EARLYSUSPEND)
	obj->early_drv.level = EARLY_SUSPEND_LEVEL_DISABLE_FB - 1,
	    obj->early_drv.suspend = ltr559_early_suspend,
	    obj->early_drv.resume = ltr559_late_resume, register_early_suspend(&obj->early_drv);
#endif
	APS_LOG("%s: OK\n", __func__);
	ltr559_init_flag = 1;
	return 0;

setup_eint_fail:

exit_create_attr_failed:
exit_sensor_obj_attach_fail:
exit_init_failed:

exit:
	ltr559_i2c_client = NULL;
	obj = NULL;
	ltr559_obj = NULL;
	APS_ERR("%s: err = %d\n", __func__, err);
	ltr559_init_flag = -1;
	return err;
}

/*----------------------------------------------------------------------------*/

static int ltr559_i2c_remove(struct i2c_client *client)
{
	int err;

	APS_FUN();
	err = ltr559_delete_attr(&ltr559_init_info.platform_diver_addr->driver);
	if (err)
		APS_ERR("ltr559_delete_attr fail: %d\n", err);

	ltr559_i2c_client = NULL;
	i2c_unregister_device(client);
	kfree(i2c_get_clientdata(client));

	return 0;
}

/*----------------------------------------------------------------------------*/
#ifdef CONFIG_OF
static const struct of_device_id alsps_of_match[] = {
	{.compatible = "mediatek,alsps"},
	{},
};
#endif

static struct i2c_driver ltr559_i2c_driver = {
	.probe = ltr559_i2c_probe,
	.remove = ltr559_i2c_remove,
	.detect = ltr559_i2c_detect,
	.suspend = ltr559_i2c_suspend,
	.resume = ltr559_i2c_resume,
	.id_table = ltr559_i2c_id,
	/* .address_data = &ltr559_addr_data, */
	.driver = {
		   /* .owner          = THIS_MODULE, */
		   .name = LTR559_DEV_NAME,
#ifdef CONFIG_OF
		   .of_match_table = alsps_of_match,
#endif
		   },
};

static int ltr559_local_init(void)
{
	APS_FUN();
	if (i2c_add_driver(&ltr559_i2c_driver)) {
		APS_ERR("add driver error\n");
		return -1;
	}
	if (-1 == ltr559_init_flag)
		return -1;
	return 0;
}

static int ltr559_remove(void)
{
	APS_FUN();
	i2c_del_driver(&ltr559_i2c_driver);
	return 0;
}

/*----------------------------------------------------------------------------*/
static int __init ltr559_init(void)
{
	APS_FUN();
	alsps_driver_add(&ltr559_init_info);
	return 0;
}

/*----------------------------------------------------------------------------*/
static void __exit ltr559_exit(void)
{
	APS_FUN();
}

/*----------------------------------------------------------------------------*/
module_init(ltr559_init);
module_exit(ltr559_exit);
/*----------------------------------------------------------------------------*/
MODULE_AUTHOR("XX Xx");
MODULE_DESCRIPTION("LTR-559ALS Driver");
MODULE_LICENSE("GPL");
