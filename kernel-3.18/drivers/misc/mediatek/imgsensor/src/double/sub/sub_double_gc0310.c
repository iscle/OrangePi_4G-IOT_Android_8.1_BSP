#include <linux/module.h>
#include <linux/i2c.h>
#include <linux/delay.h>
#include <linux/platform_device.h>
#include <linux/cdev.h>
#include <linux/uaccess.h>
#include <linux/fs.h>
#include <asm/atomic.h>
#ifdef CONFIG_COMPAT
#include <linux/compat.h>
#endif
#include <linux/slab.h>
#ifdef CONFIG_OF
#include <linux/of.h>
#include <linux/of_irq.h>
#include <linux/of_address.h>
#endif

#include "kd_camera_hw.h"
#include "kd_camera_typedef.h"
#include "kd_imgsensor.h"
#include "kd_imgsensor_define.h"
#include "kd_camera_feature.h"



#define SD_GC0310_SLAVE_ADDR_WRITE   0x42
#define SD_GC0310_SLAVE_ADDR_READ    0x43
//#define SD_DRIVER_DEBUG


struct class *sdouble_class;
struct device *sdouble_dev;
#define BOOL    int
#define SD_SENSOR_ID   0xa310
static int sdouble_als_value=0;
static int sdouble_sensor_id=0;

static int sdouble_als_open(void);
static int sdouble_sensor_open(void);
extern int kdCISModulePowerOn(CAMERA_DUAL_CAMERA_SENSOR_ENUM SensorIdx, char *currSensorName, BOOL On, char *mode_name); 
extern int iReadRegI2C(u8 *a_pSendData , u16 a_sizeSendData, u8 * a_pRecvData, u16 a_sizeRecvData, u16 i2cId);
extern int iWriteRegI2C(u8 *a_pSendData , u16 a_sizeSendData, u16 i2cId);

static struct i2c_client *sdouble_new_client;
static const struct i2c_device_id sd_gc0310yuv_i2c_id[] = { {"sd_gc0310yuv", 0}, {} };

static int sd_gc0310yuv_driver_probe(struct i2c_client *client, const struct i2c_device_id *id);

#ifdef CONFIG_OF
static const struct of_device_id sd_gc0310yuv_of_match[] = {
	{.compatible = "mediatek,sd_gc0310yuv",},
	{},
};

MODULE_DEVICE_TABLE(of, sd_gc0310yuv_of_match);
#endif

static struct i2c_driver sd_gc0310yuv_driver = {
	.driver = {
		   .name = "sd_gc0310yuv",
#ifdef CONFIG_OF
		   .of_match_table = sd_gc0310yuv_of_match,
#endif
	},
	.probe = sd_gc0310yuv_driver_probe,
	.id_table = sd_gc0310yuv_i2c_id,
};

/**********************************************************
  *
  *   [Global Variable]
  *
  *********************************************************/
static DEFINE_MUTEX(sd_gc0310yuv_i2c_access);

/**********************************************************
  *
  *   [I2C Function For Read/Write sd_gc0310yuv]
  *
  *********************************************************/
int sd_gc0310yuv_read_byte(unsigned char cmd, unsigned char *returnData)
{
	char cmd_buf[1] = { 0x00 };
	char readData = 0;
	int ret = 0;

	mutex_lock(&sd_gc0310yuv_i2c_access);

	sdouble_new_client->ext_flag =
	    ((sdouble_new_client->ext_flag) & I2C_MASK_FLAG) | I2C_WR_FLAG | I2C_DIRECTION_FLAG;

	cmd_buf[0] = cmd;
	ret = i2c_master_send(sdouble_new_client, &cmd_buf[0], (1 << 8 | 1));
	if (ret < 0) {
		sdouble_new_client->ext_flag = 0;

		mutex_unlock(&sd_gc0310yuv_i2c_access);
		return 0;
	}

	readData = cmd_buf[0];
	*returnData = readData;

	sdouble_new_client->ext_flag = 0;

	mutex_unlock(&sd_gc0310yuv_i2c_access);
	return 1;
}

int sd_gc0310yuv_write_byte(unsigned char cmd, unsigned char writeData)
{
	char write_data[2] = { 0 };
	int ret = 0;

	mutex_lock(&sd_gc0310yuv_i2c_access);

	write_data[0] = cmd;
	write_data[1] = writeData;

	sdouble_new_client->ext_flag = ((sdouble_new_client->ext_flag) & I2C_MASK_FLAG) | I2C_DIRECTION_FLAG;

	ret = i2c_master_send(sdouble_new_client, write_data, 2);
	if (ret < 0) {

		sdouble_new_client->ext_flag = 0;
		mutex_unlock(&sd_gc0310yuv_i2c_access);
		return 0;
	}

	sdouble_new_client->ext_flag = 0;
	mutex_unlock(&sd_gc0310yuv_i2c_access);
	return 1;
}

void sdouble_sensor_init(void)
{
	sd_gc0310yuv_write_byte(0xfe,0xf0);
	sd_gc0310yuv_write_byte(0xfe,0xf0);
	sd_gc0310yuv_write_byte(0xfe,0x00);
	sd_gc0310yuv_write_byte(0xfc,0x0e);
	sd_gc0310yuv_write_byte(0xfc,0x0e);
	sd_gc0310yuv_write_byte(0xf2,0x80);
	sd_gc0310yuv_write_byte(0xf3,0x00);
	sd_gc0310yuv_write_byte(0xf7,0x1b);
	sd_gc0310yuv_write_byte(0xf8,0x04);  // from 03 to 04
	sd_gc0310yuv_write_byte(0xf9,0x8e);
	sd_gc0310yuv_write_byte(0xfa,0x11);
	/////////////////////////////////////////////////      
	///////////////////   MIPI   ////////////////////      
	/////////////////////////////////////////////////      
	sd_gc0310yuv_write_byte(0xfe,0x03);
	sd_gc0310yuv_write_byte(0x40,0x08);
	sd_gc0310yuv_write_byte(0x42,0x00);
	sd_gc0310yuv_write_byte(0x43,0x00);
	sd_gc0310yuv_write_byte(0x01,0x03);
	sd_gc0310yuv_write_byte(0x10,0x84);
	        
	sd_gc0310yuv_write_byte(0x01,0x03);             
	sd_gc0310yuv_write_byte(0x02,0x00);             
	sd_gc0310yuv_write_byte(0x03,0x94);             
	sd_gc0310yuv_write_byte(0x04,0x01);            
	sd_gc0310yuv_write_byte(0x05,0x40);  // 40      20     
	sd_gc0310yuv_write_byte(0x06,0x80);             
	sd_gc0310yuv_write_byte(0x11,0x1e);             
	sd_gc0310yuv_write_byte(0x12,0x00);      
	sd_gc0310yuv_write_byte(0x13,0x05);             
	sd_gc0310yuv_write_byte(0x15,0x10);                                                                    
	sd_gc0310yuv_write_byte(0x21,0x10);             
	sd_gc0310yuv_write_byte(0x22,0x01);             
	sd_gc0310yuv_write_byte(0x23,0x10);                                             
	sd_gc0310yuv_write_byte(0x24,0x02);                                             
	sd_gc0310yuv_write_byte(0x25,0x10);                                             
	sd_gc0310yuv_write_byte(0x26,0x03);                                             
	sd_gc0310yuv_write_byte(0x29,0x02); //02                                            
	sd_gc0310yuv_write_byte(0x2a,0x0a);   //0a                                          
	sd_gc0310yuv_write_byte(0x2b,0x04);                                             
	sd_gc0310yuv_write_byte(0xfe,0x00);
	/////////////////////////////////////////////////
	/////////////////   CISCTL reg  /////////////////
	/////////////////////////////////////////////////
	sd_gc0310yuv_write_byte(0x00,0x2f);
	sd_gc0310yuv_write_byte(0x01,0x0f);
	sd_gc0310yuv_write_byte(0x02,0x04);
	sd_gc0310yuv_write_byte(0x03,0x04);
	sd_gc0310yuv_write_byte(0x04,0xd0);
	sd_gc0310yuv_write_byte(0x09,0x00);
	sd_gc0310yuv_write_byte(0x0a,0x00);
	sd_gc0310yuv_write_byte(0x0b,0x00);
	sd_gc0310yuv_write_byte(0x0c,0x06);
	sd_gc0310yuv_write_byte(0x0d,0x01);
	sd_gc0310yuv_write_byte(0x0e,0xe8);
	sd_gc0310yuv_write_byte(0x0f,0x02);
	sd_gc0310yuv_write_byte(0x10,0x88);
	sd_gc0310yuv_write_byte(0x16,0x00);
	sd_gc0310yuv_write_byte(0x17,0x14);
	sd_gc0310yuv_write_byte(0x18,0x1a);
	sd_gc0310yuv_write_byte(0x19,0x14);
	sd_gc0310yuv_write_byte(0x1b,0x48);
	sd_gc0310yuv_write_byte(0x1e,0x6b);
	sd_gc0310yuv_write_byte(0x1f,0x28);
	sd_gc0310yuv_write_byte(0x20,0x8b);  // from 89 to 8b
	sd_gc0310yuv_write_byte(0x21,0x49);
	sd_gc0310yuv_write_byte(0x22,0xb0);
	sd_gc0310yuv_write_byte(0x23,0x04);
	sd_gc0310yuv_write_byte(0x24,0x16);
	sd_gc0310yuv_write_byte(0x34,0x20);

	/////////////////////////////////////////////////
	////////////////////   BLK   ////////////////////
	/////////////////////////////////////////////////
	sd_gc0310yuv_write_byte(0x26,0x23); 
	sd_gc0310yuv_write_byte(0x28,0xff); 
	sd_gc0310yuv_write_byte(0x29,0x00); 
	sd_gc0310yuv_write_byte(0x33,0x10); 
	sd_gc0310yuv_write_byte(0x37,0x20); 
	sd_gc0310yuv_write_byte(0x38,0x10); 
	sd_gc0310yuv_write_byte(0x47,0x80); 
	sd_gc0310yuv_write_byte(0x4e,0x66); 
	sd_gc0310yuv_write_byte(0xa8,0x02); 
	sd_gc0310yuv_write_byte(0xa9,0x80);

	/////////////////////////////////////////////////
	//////////////////   ISP reg  ///////////////////
	/////////////////////////////////////////////////
	sd_gc0310yuv_write_byte(0x40,0xff); 
	sd_gc0310yuv_write_byte(0x41,0x21); 
	sd_gc0310yuv_write_byte(0x42,0xcf); 
	sd_gc0310yuv_write_byte(0x44,0x01); // 02 yuv 
	sd_gc0310yuv_write_byte(0x45,0xa0); // from a8 - a4 a4-a0
	sd_gc0310yuv_write_byte(0x46,0x03); 
	sd_gc0310yuv_write_byte(0x4a,0x11);
	sd_gc0310yuv_write_byte(0x4b,0x01);
	sd_gc0310yuv_write_byte(0x4c,0x20); 
	sd_gc0310yuv_write_byte(0x4d,0x05); 
	sd_gc0310yuv_write_byte(0x4f,0x01);
	sd_gc0310yuv_write_byte(0x50,0x01); 
	sd_gc0310yuv_write_byte(0x55,0x01); 
	sd_gc0310yuv_write_byte(0x56,0xe0);
	sd_gc0310yuv_write_byte(0x57,0x02); 
	sd_gc0310yuv_write_byte(0x58,0x80);

	/////////////////////////////////////////////////  
	///////////////////   GAIN   ////////////////////
	/////////////////////////////////////////////////
	sd_gc0310yuv_write_byte(0x70,0x70); 
	sd_gc0310yuv_write_byte(0x5a,0x84); 
	sd_gc0310yuv_write_byte(0x5b,0xc9); 
	sd_gc0310yuv_write_byte(0x5c,0xed); 
	sd_gc0310yuv_write_byte(0x77,0x74); 
	sd_gc0310yuv_write_byte(0x78,0x40); 
	sd_gc0310yuv_write_byte(0x79,0x5f); 

	///////////////////////////////////////////////// 
	///////////////////   DNDD  /////////////////////
	///////////////////////////////////////////////// 
	sd_gc0310yuv_write_byte(0x82,0x1f); 
	sd_gc0310yuv_write_byte(0x83,0x0b);


	///////////////////////////////////////////////// 
	//////////////////   EEINTP  ////////////////////
	///////////////////////////////////////////////// 
	sd_gc0310yuv_write_byte(0x8f,0xff); 
	sd_gc0310yuv_write_byte(0x90,0x9f); 
	sd_gc0310yuv_write_byte(0x91,0x90); 
	sd_gc0310yuv_write_byte(0x92,0x03); 
	sd_gc0310yuv_write_byte(0x93,0x03); 
	sd_gc0310yuv_write_byte(0x94,0x05);
	sd_gc0310yuv_write_byte(0x95,0x65); 
	sd_gc0310yuv_write_byte(0x96,0xf0); 

	///////////////////////////////////////////////// 
	/////////////////////  ASDE  ////////////////////
	///////////////////////////////////////////////// 
	sd_gc0310yuv_write_byte(0xfe,0x00);
	sd_gc0310yuv_write_byte(0x9a,0x20);
	sd_gc0310yuv_write_byte(0x9b,0x80);
	sd_gc0310yuv_write_byte(0x9c,0x40);
	sd_gc0310yuv_write_byte(0x9d,0x80);
	sd_gc0310yuv_write_byte(0xa1,0x30);
	sd_gc0310yuv_write_byte(0xa2,0x32);
	sd_gc0310yuv_write_byte(0xa4,0x30);
	sd_gc0310yuv_write_byte(0xa5,0x30);
	sd_gc0310yuv_write_byte(0xaa,0x50);
	sd_gc0310yuv_write_byte(0xac,0x22);

	/////////////////////////////////////////////////
	///////////////////   GAMMA   ///////////////////
	/////////////////////////////////////////////////
	sd_gc0310yuv_write_byte(0xbf,0x08); 
	sd_gc0310yuv_write_byte(0xc0,0x16); 
	sd_gc0310yuv_write_byte(0xc1,0x28); 
	sd_gc0310yuv_write_byte(0xc2,0x41); 
	sd_gc0310yuv_write_byte(0xc3,0x5a); 
	sd_gc0310yuv_write_byte(0xc4,0x6c); 
	sd_gc0310yuv_write_byte(0xc5,0x7a); 
	sd_gc0310yuv_write_byte(0xc6,0x96); 
	sd_gc0310yuv_write_byte(0xc7,0xac); 
	sd_gc0310yuv_write_byte(0xc8,0xbc); 
	sd_gc0310yuv_write_byte(0xc9,0xc9); 
	sd_gc0310yuv_write_byte(0xca,0xd3); 
	sd_gc0310yuv_write_byte(0xcb,0xdd); 
	sd_gc0310yuv_write_byte(0xcc,0xe5); 
	sd_gc0310yuv_write_byte(0xcd,0xf1); 
	sd_gc0310yuv_write_byte(0xce,0xfa); 
	sd_gc0310yuv_write_byte(0xcf,0xff);

	/////////////////////////////////////////////////
	///////////////////   YCP  //////////////////////
	/////////////////////////////////////////////////
	sd_gc0310yuv_write_byte(0xd0,0x40); 
	sd_gc0310yuv_write_byte(0xd1,0x34); 
	sd_gc0310yuv_write_byte(0xd2,0x34); 
	sd_gc0310yuv_write_byte(0xd3,0x3c); 
	sd_gc0310yuv_write_byte(0xd6,0xf2); 
	sd_gc0310yuv_write_byte(0xd7,0x1b); 
	sd_gc0310yuv_write_byte(0xd8,0x18); 
	sd_gc0310yuv_write_byte(0xdd,0x03); 
	/////////////////////////////////////////////////
	////////////////////   AEC   ////////////////////
	/////////////////////////////////////////////////
	sd_gc0310yuv_write_byte(0xfe,0x01);
	sd_gc0310yuv_write_byte(0x05,0x30); 
	sd_gc0310yuv_write_byte(0x06,0x75); 
	sd_gc0310yuv_write_byte(0x07,0x40); 
	sd_gc0310yuv_write_byte(0x08,0xb0); 
	sd_gc0310yuv_write_byte(0x0a,0xc5); 
	sd_gc0310yuv_write_byte(0x0b,0x11);
	sd_gc0310yuv_write_byte(0x0c,0x00); 
	sd_gc0310yuv_write_byte(0x12,0x52);
	sd_gc0310yuv_write_byte(0x13,0x38); 
	sd_gc0310yuv_write_byte(0x18,0x95);
	sd_gc0310yuv_write_byte(0x19,0x96);
	sd_gc0310yuv_write_byte(0x1f,0x20);
	sd_gc0310yuv_write_byte(0x20,0xc0); 
	sd_gc0310yuv_write_byte(0x3e,0x40); 
	sd_gc0310yuv_write_byte(0x3f,0x57); 
	sd_gc0310yuv_write_byte(0x40,0x7d); 
	sd_gc0310yuv_write_byte(0x03,0x60); 
	sd_gc0310yuv_write_byte(0x44,0x02); 
	/////////////////////////////////////////////////
	////////////////////   AWB   ////////////////////
	/////////////////////////////////////////////////
	sd_gc0310yuv_write_byte(0x1c,0x91); 
	sd_gc0310yuv_write_byte(0x21,0x15); 
	sd_gc0310yuv_write_byte(0x50,0x80);
	sd_gc0310yuv_write_byte(0x56,0x04);
	sd_gc0310yuv_write_byte(0x58,0x08);    
	sd_gc0310yuv_write_byte(0x59,0x08); 
	sd_gc0310yuv_write_byte(0x5b,0x82);  // 02 to 82 to 02
	sd_gc0310yuv_write_byte(0x61,0x8d); 
	sd_gc0310yuv_write_byte(0x62,0xa7); 
	sd_gc0310yuv_write_byte(0x63,0x00);   // d0 to  00
	sd_gc0310yuv_write_byte(0x65,0x06);
	sd_gc0310yuv_write_byte(0x66,0x06);   // 06 to 03
	sd_gc0310yuv_write_byte(0x67,0x84); 
	sd_gc0310yuv_write_byte(0x69,0x08);   // 08 to 20
	sd_gc0310yuv_write_byte(0x6a,0x25); 
	sd_gc0310yuv_write_byte(0x6b,0x01); 
	sd_gc0310yuv_write_byte(0x6c,0x00);   // 00 to 0c
	sd_gc0310yuv_write_byte(0x6d,0x02); 
	sd_gc0310yuv_write_byte(0x6e,0x00);  // f0 to 00
	sd_gc0310yuv_write_byte(0x6f,0x80); 
	sd_gc0310yuv_write_byte(0x76,0x80); 
	sd_gc0310yuv_write_byte(0x78,0xaf); 
	sd_gc0310yuv_write_byte(0x79,0x75);
	sd_gc0310yuv_write_byte(0x7a,0x40);
	sd_gc0310yuv_write_byte(0x7b,0x50);
	sd_gc0310yuv_write_byte(0x7c,0x08); //0c to 08 8.11

	sd_gc0310yuv_write_byte(0xa4,0xb9); 
	sd_gc0310yuv_write_byte(0xa5,0xa0);
	sd_gc0310yuv_write_byte(0x90,0xc9); 
	sd_gc0310yuv_write_byte(0x91,0xbe);
	sd_gc0310yuv_write_byte(0xa6,0xb8); 
	sd_gc0310yuv_write_byte(0xa7,0x95); 
	sd_gc0310yuv_write_byte(0x92,0xe6); 
	sd_gc0310yuv_write_byte(0x93,0xca); 
	sd_gc0310yuv_write_byte(0xa9,0xb6); 
	sd_gc0310yuv_write_byte(0xaa,0x89); 
	sd_gc0310yuv_write_byte(0x95,0x23); 
	sd_gc0310yuv_write_byte(0x96,0xe7); 
	sd_gc0310yuv_write_byte(0xab,0x9d); 
	sd_gc0310yuv_write_byte(0xac,0x80);
	sd_gc0310yuv_write_byte(0x97,0x43); 
	sd_gc0310yuv_write_byte(0x98,0x24); 
	sd_gc0310yuv_write_byte(0xae,0xd0);   // b7 to d0
	sd_gc0310yuv_write_byte(0xaf,0x9e); 
	sd_gc0310yuv_write_byte(0x9a,0x43);
	sd_gc0310yuv_write_byte(0x9b,0x24); 

	sd_gc0310yuv_write_byte(0xb0,0xc0);  // c8 to c0
	sd_gc0310yuv_write_byte(0xb1,0xa8);   // 97 to a8
	sd_gc0310yuv_write_byte(0x9c,0xc4); 
	sd_gc0310yuv_write_byte(0x9d,0x44); 
	sd_gc0310yuv_write_byte(0xb3,0xb7); 
	sd_gc0310yuv_write_byte(0xb4,0x7f);
	sd_gc0310yuv_write_byte(0x9f,0xc7);
	sd_gc0310yuv_write_byte(0xa0,0xc8); 
	sd_gc0310yuv_write_byte(0xb5,0x00); 
	sd_gc0310yuv_write_byte(0xb6,0x00);
	sd_gc0310yuv_write_byte(0xa1,0x00);
	sd_gc0310yuv_write_byte(0xa2,0x00);
	sd_gc0310yuv_write_byte(0x86,0x60);
	sd_gc0310yuv_write_byte(0x87,0x08);
	sd_gc0310yuv_write_byte(0x88,0x00);
	sd_gc0310yuv_write_byte(0x89,0x00);
	sd_gc0310yuv_write_byte(0x8b,0xde);
	sd_gc0310yuv_write_byte(0x8c,0x80);
	sd_gc0310yuv_write_byte(0x8d,0x00);
	sd_gc0310yuv_write_byte(0x8e,0x00);
	sd_gc0310yuv_write_byte(0x94,0x55);
	sd_gc0310yuv_write_byte(0x99,0xa6);
	sd_gc0310yuv_write_byte(0x9e,0xaa);
	sd_gc0310yuv_write_byte(0xa3,0x0a);
	sd_gc0310yuv_write_byte(0x8a,0x0a);
	sd_gc0310yuv_write_byte(0xa8,0x55);
	sd_gc0310yuv_write_byte(0xad,0x55);
	sd_gc0310yuv_write_byte(0xb2,0x55);
	sd_gc0310yuv_write_byte(0xb7,0x05);
	sd_gc0310yuv_write_byte(0x8f,0x05);
	sd_gc0310yuv_write_byte(0xb8,0xcc);
	sd_gc0310yuv_write_byte(0xb9,0x9a);

	/////////////////////////////////////
	////////////////////  CC ////////////
	/////////////////////////////////////
	sd_gc0310yuv_write_byte(0xfe,0x01);
	sd_gc0310yuv_write_byte(0xd0,0x38);
	sd_gc0310yuv_write_byte(0xd1,0xfd);
	sd_gc0310yuv_write_byte(0xd2,0x06);
	sd_gc0310yuv_write_byte(0xd3,0xf0);
	sd_gc0310yuv_write_byte(0xd4,0x40);
	sd_gc0310yuv_write_byte(0xd5,0x08);
	sd_gc0310yuv_write_byte(0xd6,0x30);
	sd_gc0310yuv_write_byte(0xd7,0x00);
	sd_gc0310yuv_write_byte(0xd8,0x0a);
	sd_gc0310yuv_write_byte(0xd9,0x16);
	sd_gc0310yuv_write_byte(0xda,0x39);
	sd_gc0310yuv_write_byte(0xdb,0xf8);

	/////////////////////////////////////////////////
	////////////////////   LSC   ////////////////////
	/////////////////////////////////////////////////
	sd_gc0310yuv_write_byte(0xfe,0x01); 
	sd_gc0310yuv_write_byte(0xc1,0x3c); 
	sd_gc0310yuv_write_byte(0xc2,0x50); 
	sd_gc0310yuv_write_byte(0xc3,0x00); 
	sd_gc0310yuv_write_byte(0xc4,0x40); 
	sd_gc0310yuv_write_byte(0xc5,0x30); 
	sd_gc0310yuv_write_byte(0xc6,0x30); 
	sd_gc0310yuv_write_byte(0xc7,0x10); 
	sd_gc0310yuv_write_byte(0xc8,0x00); 
	sd_gc0310yuv_write_byte(0xc9,0x00); 
	sd_gc0310yuv_write_byte(0xdc,0x20); 
	sd_gc0310yuv_write_byte(0xdd,0x10); 
	sd_gc0310yuv_write_byte(0xdf,0x00); 
	sd_gc0310yuv_write_byte(0xde,0x00); 

	/////////////////////////////////////////////////
	///////////////////  Histogram  /////////////////
	/////////////////////////////////////////////////
	sd_gc0310yuv_write_byte(0x01,0x10); 
	sd_gc0310yuv_write_byte(0x0b,0x31); 
	sd_gc0310yuv_write_byte(0x0e,0x50); 
	sd_gc0310yuv_write_byte(0x0f,0x0f); 
	sd_gc0310yuv_write_byte(0x10,0x6e); 
	sd_gc0310yuv_write_byte(0x12,0xa0); 
	sd_gc0310yuv_write_byte(0x15,0x60); 
	sd_gc0310yuv_write_byte(0x16,0x60); 
	sd_gc0310yuv_write_byte(0x17,0xe0); 

	/////////////////////////////////////////////////
	//////////////   Measure Window   ///////////////
	/////////////////////////////////////////////////
	sd_gc0310yuv_write_byte(0xcc,0x0c);  
	sd_gc0310yuv_write_byte(0xcd,0x10); 
	sd_gc0310yuv_write_byte(0xce,0xa0); 
	sd_gc0310yuv_write_byte(0xcf,0xe6); 

	/////////////////////////////////////////////////
	/////////////////   dark sun   //////////////////
	/////////////////////////////////////////////////
	sd_gc0310yuv_write_byte(0x45,0xf7);
	sd_gc0310yuv_write_byte(0x46,0xff); 
	sd_gc0310yuv_write_byte(0x47,0x15);
	sd_gc0310yuv_write_byte(0x48,0x03); 
	sd_gc0310yuv_write_byte(0x4f,0x60); 

	/////////////////////////////////////////////////
	///////////////////  banding  ///////////////////
	/////////////////////////////////////////////////
	sd_gc0310yuv_write_byte(0xfe,0x00);
	sd_gc0310yuv_write_byte(0x05,0x01);
	sd_gc0310yuv_write_byte(0x06,0x18); //HB
#if 1    
	sd_gc0310yuv_write_byte(0x07,0x00);
	sd_gc0310yuv_write_byte(0x08,0x10); //VB  from 10 to 50
#else
	sd_gc0310yuv_write_byte(0x07,0x01);
	sd_gc0310yuv_write_byte(0x08,0xe0); //VB
#endif
	sd_gc0310yuv_write_byte(0xfe,0x01);
	sd_gc0310yuv_write_byte(0x25,0x00); //step 
	sd_gc0310yuv_write_byte(0x26,0x9a); 
	sd_gc0310yuv_write_byte(0x27,0x01); //30fps
	sd_gc0310yuv_write_byte(0x28,0xce);  
	sd_gc0310yuv_write_byte(0x29,0x04); //12.5fps
	sd_gc0310yuv_write_byte(0x2a,0x36); 
	sd_gc0310yuv_write_byte(0x2b,0x06); //10fps
	sd_gc0310yuv_write_byte(0x2c,0x04); 
	sd_gc0310yuv_write_byte(0x2d,0x0c); //5fps
	sd_gc0310yuv_write_byte(0x2e,0x08);
	sd_gc0310yuv_write_byte(0x3c,0x20);

	/////////////////////////////////////////////////
	///////////////////   MIPI   ////////////////////
	/////////////////////////////////////////////////
	sd_gc0310yuv_write_byte(0xfe,0x03);
	sd_gc0310yuv_write_byte(0x10,0x94);  
	sd_gc0310yuv_write_byte(0xfe,0x00); 
}
EXPORT_SYMBOL(sdouble_sensor_init);


#if 1
static ssize_t sdouble_alsvalue_store(struct device *dev,struct device_attribute *attr, const char *buf, size_t size)
{
    //int enable = 0;

    return size;
}
static ssize_t sdouble_alsvalue_show(struct device *dev, struct device_attribute *attr, char *buf)
{
#if defined(SD_DRIVER_DEBUG)
	sdouble_sensor_id=0;
	sdouble_sensor_id=sdouble_sensor_open();

	//sdouble_sensor_init();
	//mdelay(100);

    sdouble_als_value=sdouble_als_open();
            
    return scnprintf(buf, PAGE_SIZE, "sdouble_sensor_id=0x%x sdouble_als_value=0x%x\n", sdouble_sensor_id,sdouble_als_value);
#else
	sdouble_als_value=sdouble_als_open();
		
	return scnprintf(buf, PAGE_SIZE, "%d\n", sdouble_als_value);
#endif
}

static ssize_t sdouble_id_store(struct device *dev,struct device_attribute *attr, const char *buf, size_t size)
{
    //int enable = 0;

    return size;
}
static ssize_t sdouble_id_show(struct device *dev, struct device_attribute *attr, char *buf)
{
	//char currSensorName[20]="sd_gc0310yuv"; 
	//char mode_name[20]="sdouble_sensor";  

	//kdCISModulePowerOn(3,currSensorName,1,mode_name); 
	
    sdouble_sensor_id=sdouble_sensor_open();

	//kdCISModulePowerOn(3,currSensorName,0,mode_name);
            
    return scnprintf(buf, PAGE_SIZE, "%x\n", sdouble_sensor_id);
}


static DEVICE_ATTR(sdouble_alsvalue, 0644, sdouble_alsvalue_show, sdouble_alsvalue_store);
static DEVICE_ATTR(sdouble_id, 0644, sdouble_id_show, sdouble_id_store);

#endif

static int sdouble_als_open(void)
{
	int als_value = 0;
	char read_id[3];
	int i=0;

	sd_gc0310yuv_write_byte(0xfe,0x01);
	sd_gc0310yuv_write_byte(0x21,0x12);//bit[2:0]  default:0x15   	
   	/* check_sensor_als */
	do
	{
	    for ( i=0; i < 3; i++)
	    {
	    	sd_gc0310yuv_write_byte(0xfe,0x00);
			sd_gc0310yuv_read_byte(0xef,&read_id[0]);

			als_value = read_id[0];
	     	printk("[SDOUBLE_GC0310YUV]:als_value == %d\n", als_value);
	    }
	    mdelay(20);
	}while(0);     

	return als_value;
}


static int sdouble_sensor_open(void)
{
	int id = 0;
	char read_id[3];
	int i=0;
		
   	/* check_sensor_ID */
   
	do
	{
	    for ( i=0; i < 3; i++)
	    {
			sd_gc0310yuv_read_byte(0xf0,&read_id[0]);
			sd_gc0310yuv_read_byte(0xf1,&read_id[1]);

			id = (read_id[0] << 8) | read_id[1];
	     	printk("[SDOUBLE_GC0310YUV]:check_sensor_ID id==0x%x\n", id);
	    }
	    mdelay(20);
	}while(0);     

	return id;
}

static int sd_gc0310yuv_driver_probe(struct i2c_client *client, const struct i2c_device_id *id)
{
	//char currSensorName[20]="sd_gc0310yuv"; 
	//char mode_name[20]="sdouble_sensor";  
	
	sdouble_new_client = client;

#if 0
	printk("%s create sdouble_1 \n", __func__);
	sdouble_class = class_create(THIS_MODULE, "sdouble");
	printk("%s create sdouble_2 \n", __func__);
	sdouble_dev = device_create(sdouble_class,NULL, 0, NULL,  "sdouble");
	printk("%s create sdouble_3 \n", __func__);
	device_create_file(sdouble_dev, &dev_attr_sdouble_alsvalue);
	device_create_file(sdouble_dev, &dev_attr_sdouble_id);
	
	printk("%s create sdouble sys files success.....\n", __func__);
#endif	

#if 0
	/*kdCISModulePowerOn*/   
	// kdCISModulePowerOn(CAMERA_DUAL_CAMERA_SENSOR_ENUM SensorIdx, char *currSensorName, BOOL On, char *mode_name);
	printk("%s begin....\n", __func__);
	kdCISModulePowerOn(3,currSensorName,1,mode_name);   
	printk("%s end....\n", __func__);

	mdelay(20);   

	sdouble_sensor_open();    
#endif	

	return 0;
}




/**********************************************************
  *
  *   [platform_driver API]
  *
  *********************************************************/
static int sd_gc0310yuv_user_space_probe(struct platform_device *dev)
{
	printk( "******** sd_gc0310yuv_user_space_probe!! ********\n");

#if 1
		printk("%s create sdouble_1 \n", __func__);
		sdouble_class = class_create(THIS_MODULE, "sdouble");
		printk("%s create sdouble_2 \n", __func__);
		sdouble_dev = device_create(sdouble_class,NULL, 0, NULL,  "sdouble");
		printk("%s create sdouble_3 \n", __func__);
		device_create_file(sdouble_dev, &dev_attr_sdouble_alsvalue);
		device_create_file(sdouble_dev, &dev_attr_sdouble_id);
		
		printk("%s create sdouble sys files success.....\n", __func__);
#endif	
	

	return 0;
}

struct platform_device sd_gc0310yuv_user_space_device = {
	.name = "sd_gc0310yuv-user",
	.id = -1,
};

static struct platform_driver sd_gc0310yuv_user_space_driver = {
	.probe = sd_gc0310yuv_user_space_probe,
	.driver = {
		   .name = "sd_gc0310yuv-user",
	},
};

static int __init sd_gc0310yuv_init(void)
{
	int ret = 0;

	if (i2c_add_driver(&sd_gc0310yuv_driver) != 0) {
		printk("[sd_gc0310yuv_init] failed to register sd_gc0310yuv i2c driver.\n");
	} else {
		printk("[sd_gc0310yuv_init] Success to register sd_gc0310yuv i2c driver.\n");
	}

	/* sd_gc0310yuv user space access interface */
	ret = platform_device_register(&sd_gc0310yuv_user_space_device);
	if (ret) {
		printk( "****[sd_gc0310yuv_init] Unable to device register(%d)\n",
			    ret);
		return ret;
	}
	ret = platform_driver_register(&sd_gc0310yuv_user_space_driver);
	if (ret) {
		printk( "****[sd_gc0310yuv_init] Unable to register driver (%d)\n",
			    ret);
		return ret;
	}

	return 0;
}

static void __exit sd_gc0310yuv_exit(void)
{
	i2c_del_driver(&sd_gc0310yuv_driver);
}

//module_init(sd_gc0310yuv_init);
late_initcall(sd_gc0310yuv_init);
module_exit(sd_gc0310yuv_exit);

MODULE_LICENSE("GPL");
MODULE_DESCRIPTION("I2C sd_gc0310yuv Driver");
MODULE_AUTHOR("aren.jiang<aren.jiang@runyee.com.cn>");
