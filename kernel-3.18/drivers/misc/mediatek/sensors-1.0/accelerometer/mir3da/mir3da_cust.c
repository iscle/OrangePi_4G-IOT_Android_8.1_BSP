/* For MTK android platform.
 *
 * mir3da.c - Linux kernel modules for 3-Axis Accelerometer
 *
 * Copyright (C) 2011-2013 MiraMEMS Sensing Technology Co., Ltd.
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
#include "cust_acc.h"
#include "accel.h"
#include "mir3da_core.h"
#include "mir3da_cust.h"

#define MIR3DA_DRV_NAME                 	"mir3da"

#define MIR3DA_AXIS_X          			0
#define MIR3DA_AXIS_Y         		 	1
#define MIR3DA_AXIS_Z          			2
#define MIR3DA_AXES_NUM        		    3

struct mir3da_i2c_data {
    struct i2c_client 		*client;
    struct acc_hw 		hw;
    struct hwmsen_convert   	cvt;

	bool flush;
	
    atomic_t                		trace;
    atomic_t                		suspend;
    atomic_t                		selftest;
    s16                     		cali_sw[MIR3DA_AXES_NUM+1];
	s16                             offset[MIR3DA_AXES_NUM + 1];
    s16                     		data[MIR3DA_AXES_NUM+1];
};
static bool sensor_power = false;
static struct GSENSOR_VECTOR3D gsensor_gain;
static MIR_HANDLE              mir_handle;
static int mir3da_init_flag =0;

#ifdef CONFIG_CUSTOM_KERNEL_ACCELEROMETER_MODULE
extern bool success_Flag;
#endif
#if MIR3DA_OFFSET_TEMP_SOLUTION
static int bCaliResult=-1;
#endif

static struct i2c_client		*mir3da_i2c_client;
static struct mir3da_i2c_data   *mir3da_obj_i2c_data;

extern int Log_level;

#define MIR3DA_SUPPORT_CONCURRENCY_PROTECTION_ 1

/*----------------------------------------------------------------------------*/
#define MI_DATA(format, ...)            if(DEBUG_DATA&Log_level){printk(KERN_ERR MI_TAG format "\n", ## __VA_ARGS__);}
#define MI_MSG(format, ...)             if(DEBUG_MSG&Log_level){printk(KERN_ERR MI_TAG format "\n", ## __VA_ARGS__);}
#define MI_ERR(format, ...)             if(DEBUG_ERR&Log_level){printk(KERN_ERR MI_TAG format "\n", ## __VA_ARGS__);}
#define MI_FUN                          if(DEBUG_FUNC&Log_level){printk(KERN_ERR MI_TAG "%s is called, line: %d\n", __FUNCTION__,__LINE__);}
#define MI_ASSERT(expr)                 \
	if (!(expr)) {\
		printk(KERN_ERR "Assertion failed! %s,%d,%s,%s\n",\
			__FILE__, __LINE__, __func__, #expr);\
	}


static int mir3da_flush(void);
static int mir3da_local_init(void);
static int mir3da_local_remove(void);

#ifdef MIR3DA_SUPPORT_CONCURRENCY_PROTECTION_

static struct semaphore s_tSemaProtect;

static void mir3da_mutex_init(void)
{
	sema_init(&s_tSemaProtect, 1);
}

static int mir3da_mutex_lock(void)
{
	if (down_interruptible(&s_tSemaProtect))
			return (-ERESTARTSYS);
	return 0;
}

static void mir3da_mutex_unlock(void)
{
	up(&s_tSemaProtect);
}
#else
	#define mir3da_mutex_init()				do {} while (0)
	#define mir3da_mutex_lock()				do {} while (0)
	#define mir3da_mutex_unlock()			  do {} while (0)
#endif
	
/*----------------------------------------------------------------------------*/
static int get_address(PLAT_HANDLE handle)
{
    if(NULL == handle){
        MI_ERR("chip init failed !\n");
		    return -1;
    }
			
	return ((struct i2c_client *)handle)->addr; 		
}
/*----------------------------------------------------------------------------*/
static int mir3da_resetCalibration(struct i2c_client *client)
{
    struct mir3da_i2c_data *obj = i2c_get_clientdata(client);    

    MI_FUN;
  
    memset(obj->cali_sw, 0x00, sizeof(obj->cali_sw));
	memset(obj->offset, 0x00, sizeof(obj->offset));
	
    return 0;     
}
/*----------------------------------------------------------------------------*/
static int mir3da_readCalibration(struct i2c_client *client, int *dat)
{
    struct mir3da_i2c_data *obj = i2c_get_clientdata(client);

    MI_FUN;

    dat[obj->cvt.map[MIR3DA_AXIS_X]] = obj->cvt.sign[MIR3DA_AXIS_X]*obj->cali_sw[MIR3DA_AXIS_X];
    dat[obj->cvt.map[MIR3DA_AXIS_Y]] = obj->cvt.sign[MIR3DA_AXIS_Y]*obj->cali_sw[MIR3DA_AXIS_Y];
    dat[obj->cvt.map[MIR3DA_AXIS_Z]] = obj->cvt.sign[MIR3DA_AXIS_Z]*obj->cali_sw[MIR3DA_AXIS_Z];                        
                                       
    return 0;
}
/*----------------------------------------------------------------------------*/
static int mir3da_writeCalibration(struct i2c_client *client, int dat[MIR3DA_AXES_NUM])
{
    struct mir3da_i2c_data *obj = i2c_get_clientdata(client);
    int err = 0;
    int cali[MIR3DA_AXES_NUM];

    MI_FUN;
    if(!obj || ! dat)
    {
        MI_ERR("null ptr!!\n");
        return -EINVAL;
    }
    else
    {
	    err = mir3da_readCalibration(client, cali);
        if (0 != err) {	
		    MI_ERR("read offset fail, %d\n", err);
		    return err;
        }

		MI_MSG("write_cali  raw cali_sw[%d][%d][%d] dat[%d][%d][%d]",cali[0],cali[1],cali[2],dat[0],dat[1],dat[2]);

        cali[MIR3DA_AXIS_X] += dat[MIR3DA_AXIS_X];
        cali[MIR3DA_AXIS_Y] += dat[MIR3DA_AXIS_Y];
        cali[MIR3DA_AXIS_Z] += dat[MIR3DA_AXIS_Z];	
	
        obj->cali_sw[MIR3DA_AXIS_X] = obj->cvt.sign[MIR3DA_AXIS_X]*cali[obj->cvt.map[MIR3DA_AXIS_X]];
        obj->cali_sw[MIR3DA_AXIS_Y] = obj->cvt.sign[MIR3DA_AXIS_Y]*cali[obj->cvt.map[MIR3DA_AXIS_Y]];
        obj->cali_sw[MIR3DA_AXIS_Z] = obj->cvt.sign[MIR3DA_AXIS_Z]*cali[obj->cvt.map[MIR3DA_AXIS_Z]];

		MI_MSG("write_cali  new cali_sw[%d][%d][%d] ",obj->cali_sw[0],obj->cali_sw[1],obj->cali_sw[2]);
    } 
	
	mdelay(1);
	
    return err;
}
/*----------------------------------------------------------------------------*/
static int mir3da_setPowerMode(struct i2c_client *client, bool enable)
{
    int ret;
    
    MI_MSG ("mir3da_setPowerMode(), enable = %d", enable);

	if (enable == sensor_power)
		MI_ERR("Sensor power status should not be set again!!!\n");

	
    ret = mir3da_set_enable(client,enable);  
    if (ret == 0){
        sensor_power = enable;
    }else{
        return -1;
    }
	if (mir3da_obj_i2c_data->flush) {
		if (sensor_power) {
		    MI_MSG("remain flush, will call mir3da_flush in setPowerMode\n");
			mir3da_flush();
		} else{
			mir3da_obj_i2c_data->flush = false;
                }
	}

    return ret;
}
/*----------------------------------------------------------------------------*/
static int mir3da_readSensorData(struct i2c_client *client, char *buf)
{    
    struct mir3da_i2c_data *obj = (struct mir3da_i2c_data*)i2c_get_clientdata(client);
    unsigned char databuf[20];
    int acc[MIR3DA_AXES_NUM];
    int res = 0;

    memset(databuf, 0, sizeof(unsigned char)*10);	

    if(NULL == buf)
    {
        return -1;
    }
    if(NULL == client)
    {
        *buf = 0;
        return -2;
    }
    if(sensor_power == false)
    {
        res = mir3da_setPowerMode(client, true);
        if(res)
        {
            MI_ERR("Power on mir3da error %d!\n", res);
        }
        msleep(20);
    }

    res = mir3da_read_data(client, &(obj->data[MIR3DA_AXIS_X]),&(obj->data[MIR3DA_AXIS_Y]),&(obj->data[MIR3DA_AXIS_Z]));

    if(res) 
    {        
        MI_ERR("I2C error: ret value=%d", res);
        return -3;
    }
    else
    {
        MI_MSG("read_sensor_data map[%d][%d][%d] sign[%d][%d][%d]",obj->cvt.map[0],obj->cvt.map[1],obj->cvt.map[2],obj->cvt.sign[0],obj->cvt.sign[1],obj->cvt.sign[2]);
		MI_MSG("read_sensor_data xyz_0[%d][%d][%d] cali_sw[%d][%d][%d]",obj->data[0],obj->data[1],obj->data[2],obj->cali_sw[0],obj->cali_sw[1],obj->cali_sw[2]);
	
        obj->data[MIR3DA_AXIS_X] += obj->cali_sw[MIR3DA_AXIS_X];
        obj->data[MIR3DA_AXIS_Y] += obj->cali_sw[MIR3DA_AXIS_Y];
        obj->data[MIR3DA_AXIS_Z] += obj->cali_sw[MIR3DA_AXIS_Z];
		
		MI_MSG("read_sensor_data xyz_1[%d][%d][%d]",obj->data[0],obj->data[1],obj->data[2]);
		
        acc[obj->cvt.map[MIR3DA_AXIS_X]] = obj->cvt.sign[MIR3DA_AXIS_X]*obj->data[MIR3DA_AXIS_X];
        acc[obj->cvt.map[MIR3DA_AXIS_Y]] = obj->cvt.sign[MIR3DA_AXIS_Y]*obj->data[MIR3DA_AXIS_Y];
        acc[obj->cvt.map[MIR3DA_AXIS_Z]] = obj->cvt.sign[MIR3DA_AXIS_Z]*obj->data[MIR3DA_AXIS_Z];
			
		MI_MSG("read_sensor_data xyz_2[%d][%d][%d]",acc[obj->cvt.map[MIR3DA_AXIS_X]],acc[obj->cvt.map[MIR3DA_AXIS_Y]],acc[obj->cvt.map[MIR3DA_AXIS_Z]]);

        if(abs(obj->cali_sw[MIR3DA_AXIS_Z])> 1300)
           acc[obj->cvt.map[MIR3DA_AXIS_Z]] = acc[obj->cvt.map[MIR3DA_AXIS_Z]] - 2048;         
#if MIR3DA_STK_TEMP_SOLUTION
        if(bzstk)
           acc[MIR3DA_AXIS_Z] =squareRoot(1024*1024 - acc[MIR3DA_AXIS_X]*acc[MIR3DA_AXIS_X] - acc[MIR3DA_AXIS_Y]*acc[MIR3DA_AXIS_Y]); 	
#endif
        MI_DATA("mir3da data map: %d, %d, %d!\n", acc[MIR3DA_AXIS_X], acc[MIR3DA_AXIS_Y], acc[MIR3DA_AXIS_Z]);
        
        acc[MIR3DA_AXIS_X] = acc[MIR3DA_AXIS_X] * GRAVITY_EARTH_1000 / gsensor_gain.x;
        acc[MIR3DA_AXIS_Y] = acc[MIR3DA_AXIS_Y] * GRAVITY_EARTH_1000 / gsensor_gain.y;
        acc[MIR3DA_AXIS_Z] = acc[MIR3DA_AXIS_Z] * GRAVITY_EARTH_1000 / gsensor_gain.z;        

		MI_MSG("read_sensor_data xyz_3[%d][%d][%d]",acc[MIR3DA_AXIS_X],acc[MIR3DA_AXIS_Y],acc[MIR3DA_AXIS_Z]);

        sprintf(buf, "%04x %04x %04x", acc[MIR3DA_AXIS_X], acc[MIR3DA_AXIS_Y], acc[MIR3DA_AXIS_Z]);
        
        MI_DATA( "mir3da data mg: x= %d, y=%d, z=%d\n",  acc[MIR3DA_AXIS_X],acc[MIR3DA_AXIS_Y],acc[MIR3DA_AXIS_Z]); 
    }
    
    return 0;
}
/*----------------------------------------------------------------------------*/
static int mir3da_readRawData(struct i2c_client *client, char *buf)
{
	struct mir3da_i2c_data *obj = (struct mir3da_i2c_data*)i2c_get_clientdata(client);
	int res = 0;

	if (!buf || !client) {
		return EINVAL;
	}
	
       res = mir3da_read_data(client, &(obj->data[MIR3DA_AXIS_X]),&(obj->data[MIR3DA_AXIS_Y]),&(obj->data[MIR3DA_AXIS_Z])); 
       if(res) {        
		MI_ERR("I2C error: ret value=%d", res);
		return EIO;
	}
	else {
		sprintf(buf, "%04x %04x %04x", (obj->data[MIR3DA_AXIS_X]), (obj->data[MIR3DA_AXIS_Y]), (obj->data[MIR3DA_AXIS_Z]));
	}
	
	return 0;
}
/*----------------------------------------------------------------------------*/
static ssize_t mir3da_enable_show(struct device_driver *ddri, char *buf)
{
    int ret;
    char bEnable;
    struct i2c_client *client = mir_handle;

    MI_FUN;
	
    ret = mir3da_get_enable(client, &bEnable);   
    if (ret < 0){
        ret = -EINVAL;
    }
    else{
        ret = sprintf(buf, "%d\n", bEnable);
    }

    return ret;
}
/*----------------------------------------------------------------------------*/
static ssize_t mir3da_enable_store(struct device_driver *ddri, const char *buf, size_t count)
{
    int ret;
    char bEnable;
    unsigned long enable;
    struct i2c_client *client = mir_handle;

    if (buf == NULL){
        return -1;
    }

    enable = simple_strtoul(buf, NULL, 10);    
    bEnable = (enable > 0) ? true : false;

    ret = mir3da_set_enable (client, bEnable);
    if (ret < 0){
        ret = -EINVAL;
    }
    else{
        ret = count;
    }

    return ret;
}
/*----------------------------------------------------------------------------*/
static ssize_t mir3da_axis_data_show(struct device_driver *ddri, char *buf)
{
    int result;
    short x,y,z;
    int count = 0;

    result = mir3da_read_data(mir_handle, &x, &y, &z);
    if (result == 0)
        count += sprintf(buf+count, "x= %d;y=%d;z=%d\n", x,y,z);
    else
        count += sprintf(buf+count, "reading failed!");

    return count;
}
/*----------------------------------------------------------------------------*/
static ssize_t mir3da_reg_data_show(struct device_driver *ddri, char *buf)
{
    MIR_HANDLE          handle = mir_handle;
        
    return mir3da_get_reg_data(handle, buf);
}
/*----------------------------------------------------------------------------*/
static ssize_t mir3da_reg_data_store(struct device_driver *ddri, const char *buf, size_t count)
{
    int                 addr, data;
    int                 result;

    sscanf(buf, "0x%x, 0x%x\n", &addr, &data);
    
    result = mir3da_register_write(mir_handle, addr, data);
    
    MI_ASSERT(result==0);

    MI_MSG("set[0x%x]->[0x%x]\n",addr,data);	

    return count;
}
/*----------------------------------------------------------------------------*/
static ssize_t mir3da_log_level_show(struct device_driver *ddri, char *buf)
{
    int ret;

    ret = sprintf(buf, "%d\n", Log_level);

    return ret;
}
/*----------------------------------------------------------------------------*/
static ssize_t mir3da_log_level_store(struct device_driver *ddri, const char *buf, size_t count)
{
    Log_level = simple_strtoul(buf, NULL, 10);
    return count;
}
/*----------------------------------------------------------------------------*/
static ssize_t mir3da_primary_offset_show(struct device_driver *ddri, char *buf){    
    int x=0,y=0,z=0;
   
    mir3da_get_primary_offset(mir_handle,&x,&y,&z);

	  return sprintf(buf, "x=%d ,y=%d ,z=%d\n",x,y,z);
}
/*----------------------------------------------------------------------------*/
static ssize_t mir3da_version_show(struct device_driver *ddri, char *buf)
{
	return sprintf(buf, "%s_%s\n", DRI_VER, CORE_VER);
}
/*----------------------------------------------------------------------------*/
static ssize_t mir3da_vendor_show(struct device_driver *ddri, char *buf)
{
    return sprintf(buf, "%s\n", "MiraMEMS");
}
/*----------------------------------------------------------------------------*/
#if MIR3DA_OFFSET_TEMP_SOLUTION
/*----------------------------------------------------------------------------*/
static ssize_t mir3da_calibrate_show(struct device_driver *ddri, char *buf)
{
    int ret;       

    ret = sprintf(buf, "%d\n", bCaliResult);   
    return ret;
}
/*----------------------------------------------------------------------------*/
static ssize_t mir3da_calibrate_store(struct device_driver *ddri, const char *buf, size_t count)
{
    signed char     z_dir = 0;
   
    z_dir = simple_strtol(buf, NULL, 10);
    bCaliResult = mir3da_calibrate(mir_handle,z_dir);
    
    return count;
}
#endif
/*----------------------------------------------------------------------------*/
#if FILTER_AVERAGE_ENHANCE
static ssize_t mir3da_average_enhance_show(struct device_driver *ddri, char *buf)
{
    int       ret = 0;
    struct mir3da_filter_param_s    param = {0};

    ret = mir3da_get_filter_param(&param);
    ret |= sprintf(buf, "%d %d %d\n", param.filter_param_l, param.filter_param_h, param.filter_threhold);

    return ret;
}
/*----------------------------------------------------------------------------*/
static ssize_t mir3da_average_enhance_store(struct device_driver *ddri, const char *buf, size_t count)
{ 
    int       ret = 0;
    struct mir3da_filter_param_s    param = {0};
    
    sscanf(buf, "%d %d %d\n", &param.filter_param_l, &param.filter_param_h, &param.filter_threhold);
    
    ret = mir3da_set_filter_param(&param);
    
    return count;
}
#endif 
/*----------------------------------------------------------------------------*/
static DRIVER_ATTR(enable,          S_IRUGO | S_IWUSR,  mir3da_enable_show,             mir3da_enable_store);
static DRIVER_ATTR(axis_data,       S_IRUGO,  mir3da_axis_data_show,          NULL);
static DRIVER_ATTR(reg_data,        S_IWUSR | S_IRUGO,  mir3da_reg_data_show,           mir3da_reg_data_store);
static DRIVER_ATTR(log_level,       S_IWUSR | S_IRUGO,  mir3da_log_level_show,          mir3da_log_level_store);
static DRIVER_ATTR(primary_offset,  S_IRUGO,  mir3da_primary_offset_show,     NULL);
static DRIVER_ATTR(vendor,          S_IRUGO,  mir3da_vendor_show,             NULL);
static DRIVER_ATTR(version,         S_IRUGO,  mir3da_version_show,            NULL); 
#if MIR3DA_OFFSET_TEMP_SOLUTION
static DRIVER_ATTR(calibrate_miraGSensor,       S_IWUSR | S_IRUGO,  mir3da_calibrate_show,          mir3da_calibrate_store);
#endif
#if FILTER_AVERAGE_ENHANCE
static DRIVER_ATTR(average_enhance, S_IWUGO | S_IRUGO,  mir3da_average_enhance_show,    mir3da_average_enhance_store);
#endif 
/*----------------------------------------------------------------------------*/
static struct driver_attribute *mir3da_attributes[] = { 
    &driver_attr_enable,
    &driver_attr_axis_data,
    &driver_attr_reg_data,
    &driver_attr_log_level,
    &driver_attr_primary_offset,    
    &driver_attr_vendor,
    &driver_attr_version,
#if MIR3DA_OFFSET_TEMP_SOLUTION
    &driver_attr_calibrate_miraGSensor,
#endif
#if FILTER_AVERAGE_ENHANCE
    &driver_attr_average_enhance,
#endif     
};
/*----------------------------------------------------------------------------*/
static int mir3da_create_attr(struct device_driver *driver) 
{
    int idx, err = 0;
    int num = (int)(sizeof(mir3da_attributes)/sizeof(mir3da_attributes[0]));
    if (driver == NULL)
    {
        return -EINVAL;
    }

    for(idx = 0; idx < num; idx++)
    {
		err = driver_create_file(driver, mir3da_attributes[idx]);
        if(err)
        {            
            MI_MSG("driver_create_file (%s) = %d\n", mir3da_attributes[idx]->attr.name, err);
            break;
        }
    }    
    return err;
}
/*----------------------------------------------------------------------------*/
static int mir3da_delete_attr(struct device_driver *driver)
{
    int idx ,err = 0;
    int num = (int)(sizeof(mir3da_attributes)/sizeof(mir3da_attributes[0]));

    if(driver == NULL)
    {
        return -EINVAL;
    }

    for(idx = 0; idx < num; idx++)
    {
        driver_remove_file(driver, mir3da_attributes[idx]);
    }

    return err;
}
/*----------------------------------------------------------------------------*/
static int mir3da_suspend(struct i2c_client *client, pm_message_t msg)  
{
	struct mir3da_i2c_data *obj = i2c_get_clientdata(client);    
	int err = 0;

	MI_FUN;    

	if(msg.event == PM_EVENT_SUSPEND)
	{   
		if(obj == NULL)
		{
			MI_ERR("null pointer!!\n");
			return -EINVAL;
		}
		
		atomic_set(&obj->suspend, 1);
        mir3da_mutex_lock();
		err = mir3da_setPowerMode(client, false);
        mir3da_mutex_unlock();  

		if(err) {			
			MI_ERR("write power control fail!!\n");
            return err;
        }
	}
	return err;
}
/*----------------------------------------------------------------------------*/
static int mir3da_resume(struct i2c_client *client)
{
    struct mir3da_i2c_data *obj = i2c_get_clientdata(client);        
    int err;
    MI_FUN;

    if(obj == NULL){
        MI_ERR("null pointer!!\n");
        return -EINVAL;
    }

	mir3da_mutex_lock();	
    err = mir3da_chip_resume(obj->client);
    if(err) {
        MI_ERR("chip resume fail!!\n");
        return err;
    }

    err = mir3da_setPowerMode(obj->client, true);
    mir3da_mutex_unlock();

    if(err != 0) {
		MI_ERR("write power control fail!!\n");
        return err;
    }		

	atomic_set(&obj->suspend, 0);

	return 0;
}
/*----------------------------------------------------------------------------*/
int i2c_smbus_read(PLAT_HANDLE handle, u8 addr, u8 *data)
{
    int                 res = 0;
    struct i2c_client   *client = (struct i2c_client*)handle;
    
    *data = i2c_smbus_read_byte_data(client, addr);
    
    return res;
}
/*----------------------------------------------------------------------------*/
int i2c_smbus_read_block(PLAT_HANDLE handle, u8 addr, u8 count, u8 *data)
{
    int                 res = 0;
    struct i2c_client   *client = (struct i2c_client*)handle;
    
    res = i2c_smbus_read_i2c_block_data(client, addr, count, data);
    
    return res;
}
/*----------------------------------------------------------------------------*/
int i2c_smbus_write(PLAT_HANDLE handle, u8 addr, u8 data)
{
    int                 res = 0;
    struct i2c_client   *client = (struct i2c_client*)handle;
    
    res = i2c_smbus_write_byte_data(client, addr, data);
    
    return res;
}
/*----------------------------------------------------------------------------*/
void msdelay(int ms)
{
    mdelay(ms);
}
/*----------------------------------------------------------------------------*/
MIR_GENERAL_OPS_DECLARE(ops_handle, i2c_smbus_read, i2c_smbus_read_block, i2c_smbus_write, NULL, NULL, NULL,get_address,NULL,msdelay, printk, sprintf);
/*----------------------------------------------------------------------------*/
static void mir3da_SetGain(void)
{
	gsensor_gain.x = gsensor_gain.y = gsensor_gain.z = 1024;

    MI_MSG("[%s] gain: %d  %d  %d\n", __func__, gsensor_gain.x, gsensor_gain.y, gsensor_gain.z);
}
/*----------------------------------------------------------------------------*/
static int mir3da_open_report_data(int open)
{
	return 0;
}
/*----------------------------------------------------------------------------*/
static int mir3da_enable_nodata(int en)
{
	int res = 0;
	int retry = 0;
	char bEnable = false;
MI_ERR("mir3da_enable_nodata 0");

	if (1 == en)
		bEnable = true;
	if (0 == en)
		bEnable = false;

	for (retry = 0; retry < 3; retry++) {
		res = mir3da_setPowerMode(mir3da_i2c_client, bEnable);
		if (res == 0) {
			MI_ERR("mir3da_SetPowerMode done\n");
			break;
		}
		MI_ERR("mir3da_SetPowerMode fail\n");
	}
MI_ERR("mir3da_enable_nodata 1");
	if (res != 0) {
		MI_ERR("mir3da_SetPowerMode fail!\n");
		return -1;
	}
	MI_MSG("mir3da_enable_nodata OK!\n");
MI_ERR("mir3da_enable_nodata 2");
	return 0;
}
/*----------------------------------------------------------------------------*/
static int mir3da_set_delay(u64 ns)
{
	int value = 0;
MI_ERR("mir3da_set_delay ");
	value = (int)ns/1000/1000;
	MI_MSG("mir3daset_delay (%d), chip only use 1024HZ\n", value);
	return 0;
}
/*----------------------------------------------------------------------------*/
static int mir3da_get_data(int *x , int *y, int *z, int *status)
{
	char buff[MIR3DA_BUFSIZE];
	int ret;

	mir3da_mutex_lock();

	mir3da_readSensorData(mir3da_i2c_client, buff);

	mir3da_mutex_unlock();

	ret = sscanf(buff, "%x %x %x", x, y, z);
	*status = SENSOR_STATUS_ACCURACY_MEDIUM;

	return 0;
}
/*----------------------------------------------------------------------------*/
static int mir3da_batch(int flag, int64_t samplingPeriodNs, int64_t maxBatchReportLatencyNs)
{
MI_ERR("mir3da_batch 0");
	return 0;
}
/*----------------------------------------------------------------------------*/
static int mir3da_flush(void)
{
	int err = 0;
MI_ERR("mir3da_flush 0");
	if (!sensor_power) {
		mir3da_obj_i2c_data->flush = true;
		return 0;
	}

	err = acc_flush_report();
	if (err >= 0)
		mir3da_obj_i2c_data->flush = false;
MI_ERR("mir3da_flush 1");
	return err;
}
/*----------------------------------------------------------------------------*/
static int mir3da_factory_enable_sensor(bool enabledisable, int64_t sample_periods_ms)
{
	int err;
	
    MI_FUN;

	err = mir3da_enable_nodata(enabledisable == true ? 1 : 0);
	if (err) {
		MI_ERR("%s enable sensor failed!\n", __func__);
		return -1;
	}
	err = mir3da_batch(0, sample_periods_ms * 1000000, 0);
	if (err) {
		MI_ERR("%s enable set batch failed!\n", __func__);
		return -1;
	}

	return 0;
}
/*----------------------------------------------------------------------------*/
static int mir3da_factory_get_data(int32_t data[3], int *status)
{
	int ret;

    MI_FUN;

	ret =mir3da_get_data(&data[0], &data[1], &data[2], status);

	MI_MSG("mir3da_factory_get_data %d %d %d",data[0],data[1],data[2]);

	return ret;

}
/*----------------------------------------------------------------------------*/
static int mir3da_factory_get_raw_data(int32_t data[3])
{
    char strbuf[MIR3DA_BUFSIZE] = { 0 };

    MI_FUN;
		
    mir3da_readRawData(mir3da_i2c_client, strbuf);
    MI_MSG("support mir3da_factory_get_raw_data!\n");

	return 0;
}
/*----------------------------------------------------------------------------*/
static int mir3da_factory_enable_calibration(void)
{
    MI_FUN;

	return 0;
}
/*----------------------------------------------------------------------------*/
static int mir3da_factory_clear_cali(void)
{
	int err = 0;

    MI_FUN;
	
	err = mir3da_resetCalibration(mir3da_i2c_client);
	if (err) {
		MI_ERR("mir3da_ResetCalibration failed!\n");
		return -1;
	}
	return 0;
}
/*----------------------------------------------------------------------------*/
static int mir3da_factory_set_cali(int32_t data[3])
{
	int err = 0;
	int cali[3] = { 0 };
	
    MI_FUN;

    MI_MSG("mir3da_factory_set_cali ori %d %d %d",data[0],data[1],data[2]);

	cali[MIR3DA_AXIS_X] = data[0] * gsensor_gain.x / GRAVITY_EARTH_1000;
	cali[MIR3DA_AXIS_Y] = data[1] * gsensor_gain.y / GRAVITY_EARTH_1000;
	cali[MIR3DA_AXIS_Z] = data[2] * gsensor_gain.z / GRAVITY_EARTH_1000;
	
    MI_MSG("mir3da_factory_set_cali new %d %d %d",cali[0],cali[1],cali[2]);

	err = mir3da_writeCalibration(mir3da_i2c_client, cali);
	if (err) {
		MI_ERR("mir3da_WriteCalibration failed!\n");
		return -1;
	}
	return 0;
}
/*----------------------------------------------------------------------------*/
static int mir3da_factory_get_cali(int32_t data[3])
{
	data[0] = mir3da_obj_i2c_data->cali_sw[MIR3DA_AXIS_X] *GRAVITY_EARTH_1000 /gsensor_gain.x ;
	data[1] = mir3da_obj_i2c_data->cali_sw[MIR3DA_AXIS_Y] *GRAVITY_EARTH_1000 /gsensor_gain.x;
	data[2] = mir3da_obj_i2c_data->cali_sw[MIR3DA_AXIS_Z] *GRAVITY_EARTH_1000 /gsensor_gain.x;	

	
    MI_MSG("mir3da_factory_get_cali %d %d %d",data[0],data[1],data[2]);
	return 0;
}
/*----------------------------------------------------------------------------*/
static int mir3da_factory_do_self_test(void)
{
	return 0;
}
/*----------------------------------------------------------------------------*/
static struct accel_factory_fops mir3da_factory_fops = {
	.enable_sensor = mir3da_factory_enable_sensor,
	.get_data = mir3da_factory_get_data,
	.get_raw_data = mir3da_factory_get_raw_data,
	.enable_calibration = mir3da_factory_enable_calibration,
	.clear_cali = mir3da_factory_clear_cali,
	.set_cali = mir3da_factory_set_cali,
	.get_cali = mir3da_factory_get_cali,
	.do_self_test = mir3da_factory_do_self_test,
};

static struct accel_factory_public mir3da_factory_device = {
	.gain = 1,
	.sensitivity = 1,
	.fops = &mir3da_factory_fops,
};

/*----------------------------------------------------------------------------*/
static struct acc_init_info mir3da_init_info = {
    .name = MIR3DA_DRV_NAME,
    .init = mir3da_local_init,
    .uninit = mir3da_local_remove,
};
/*----------------------------------------------------------------------------*/
static int mir3da_probe(struct i2c_client *client, const struct i2c_device_id *id)
{
    int                 result;
    struct mir3da_i2c_data *obj=NULL;
	unsigned char chip_id=0;
	unsigned char i=0;	
	struct acc_control_path ctl = {0};
	struct acc_data_path data = {0};

    MI_FUN;

    
 	if(client->addr != 0x26)
	{
		client->addr = 0x26;
	}

	obj = kzalloc(sizeof(*obj), GFP_KERNEL);
	if (!obj) 
    {
        MI_ERR("kzalloc failed!");
        result = -ENOMEM;
        goto exit;
    }   
 
	result = get_accel_dts_func(client->dev.of_node, &obj->hw);
	if (result < 0) {
		MI_ERR("get cust_baro dts info fail\n");
		goto exit_kfree;
	}
	obj->hw.direction = 1;
	result = hwmsen_get_convert(obj->hw.direction, &obj->cvt);
	if (result) {
		MI_ERR("invalid direction: %d\n", obj->hw.direction);
		goto exit_kfree;
	}	
 
    obj->client = client;
    i2c_set_clientdata(client,obj);	
    atomic_set(&obj->trace, 0);
    atomic_set(&obj->suspend, 0);

	mir3da_obj_i2c_data = obj;		
	mir3da_i2c_client = obj->client;

	mir3da_mutex_init();
 
    if(mir3da_install_general_ops(&ops_handle)){
        MI_ERR("Install ops failed !\n");
        goto exit_init_failed;
    }

	i2c_smbus_read((PLAT_HANDLE)mir3da_i2c_client, NSA_REG_WHO_AM_I, &chip_id);	
	if(chip_id != 0x13){
        for(i=0;i<5;i++){
			mdelay(5); 
		    i2c_smbus_read((PLAT_HANDLE)mir3da_i2c_client, NSA_REG_WHO_AM_I, &chip_id);
            if(chip_id == 0x13)
                break;				
		}
		if(i == 5)
	        client->addr = 0x27;
	}
 
    mir_handle = mir3da_core_init((PLAT_HANDLE)mir3da_i2c_client);
    if(NULL == mir_handle){
        MI_ERR("chip init failed !\n");
        goto exit_init_failed;        
    }	

	mir3da_SetGain();

    ctl.is_use_common_factory = false;

	result = accel_factory_device_register(&mir3da_factory_device);
	if (result) {
		MI_ERR("acc_factory register failed.\n");
		goto exit_misc_device_register_failed;
	}

    result = mir3da_create_attr(&(mir3da_init_info.platform_diver_addr->driver));
    if(result)
    {
        MI_ERR("create attribute result = %d\n", result);
        result = -EINVAL;
	 goto exit_create_attr_failed;
    }
 
	ctl.open_report_data = mir3da_open_report_data;
	ctl.enable_nodata = mir3da_enable_nodata;
	ctl.batch = mir3da_batch;
	ctl.flush = mir3da_flush;
	ctl.set_delay  = mir3da_set_delay;
	ctl.is_report_input_direct = false;
	ctl.is_support_batch = obj->hw.is_batch_supported;
	result = acc_register_control_path(&ctl);
	if (result) {
		MI_ERR("register acc control path err\n");
		goto exit_kfree;
	}

	data.get_data = mir3da_get_data;
	data.vender_div = 1000;
	result = acc_register_data_path(&data);
	if (result) {
		MI_ERR("register acc data path err= %d\n", result);
		goto exit_kfree;
	}

    mir3da_init_flag = 0;

    return result;
exit_create_attr_failed:
exit_init_failed:
exit_misc_device_register_failed:		
exit_kfree:
	kfree(obj);
	exit:
	MI_ERR("%s: err = %d\n", __func__, result); 
	obj = NULL;
	mir_handle = NULL;	
	mir3da_i2c_client = NULL;
	mir3da_obj_i2c_data = NULL;
	mir3da_init_flag = -1;	
	return result;
}
/*----------------------------------------------------------------------------*/
static int  mir3da_remove(struct i2c_client *client)
{
    int err = 0;	

    err = mir3da_delete_attr(&(mir3da_init_info.platform_diver_addr->driver));
    if(err)
    {
        MI_ERR("mir3da_delete_attr fail: %d\n", err);
    }

	mir3da_i2c_client = NULL;
	i2c_unregister_device(client);
	accel_factory_device_deregister(&mir3da_factory_device);
	kfree(i2c_get_clientdata(client));  
    
    return 0;
}

#ifdef CONFIG_OF
static const struct of_device_id accel_of_match[] = {
	{.compatible = "mediatek,gsensor"},
	{},
};
#endif

static const struct i2c_device_id mir3da_id[] = {
    { MIR3DA_DRV_NAME, 0 },
    { }
};

static struct i2c_driver mir3da_driver = {
    .driver = {
        .name    = MIR3DA_DRV_NAME,
#ifdef CONFIG_OF
	    .of_match_table = accel_of_match,
#endif
    },    
    .probe       = mir3da_probe,
    .remove      = mir3da_remove,
    .suspend     = mir3da_suspend,
    .resume      = mir3da_resume,

    .id_table    = mir3da_id,
};

/*----------------------------------------------------------------------------*/
static int mir3da_local_init(void) 
{	
    MI_FUN;

    if(i2c_add_driver(&mir3da_driver))
    {
        MI_ERR("add driver error\n");
        return -1;
    }

	if(-1 == mir3da_init_flag)
	{
	   return -1;
	}		

    return 0;
}
/*----------------------------------------------------------------------------*/
static int mir3da_local_remove(void)
{
    MI_FUN;    
	
    i2c_del_driver(&mir3da_driver);
	
    return 0;
}

/*----------------------------------------------------------------------------*/
static int __init mir3da_init(void)
{    

    MI_FUN;

	acc_driver_add(&mir3da_init_info);

    return 0;
}
/*----------------------------------------------------------------------------*/
static void __exit mir3da_exit(void)
{    
    MI_FUN;

#ifdef CONFIG_CUSTOM_KERNEL_ACCELEROMETER_MODULE
	success_Flag = false;
#endif	
}
/*----------------------------------------------------------------------------*/

module_init(mir3da_init);
module_exit(mir3da_exit);

MODULE_AUTHOR("MiraMEMS <lschen@miramems.com>");
MODULE_DESCRIPTION("MirMEMS 3-Axis Accelerometer driver");
MODULE_LICENSE("GPL");
MODULE_VERSION("1.0");

