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


#if 1
#define MD_GC0310_SLAVE_ADDR_WRITE   0x42
#define MD_GC0310_SLAVE_ADDR_READ    0x43
//#define MD_DRIVER_DEBUG

struct class *mdouble_class;
struct device *mdouble_dev;
#define BOOL    int
#define MD_SENSOR_ID   0xa310
static int mdouble_als_value=0;
static int mdouble_sensor_id=0;

static int mdouble_als_open(void);
static int mdouble_sensor_open(void);
extern int kdCISModulePowerOn(CAMERA_DUAL_CAMERA_SENSOR_ENUM SensorIdx, char *currSensorName, BOOL On, char *mode_name); 
extern int iReadRegI2C(u8 *a_pSendData , u16 a_sizeSendData, u8 * a_pRecvData, u16 a_sizeRecvData, u16 i2cId);
extern int iWriteRegI2C(u8 *a_pSendData , u16 a_sizeSendData, u16 i2cId);

static struct i2c_client *mdouble_new_client;
static const struct i2c_device_id md_gc0310yuv_i2c_id[] = { {"md_gc0310yuv", 0}, {} };

static int md_gc0310yuv_driver_probe(struct i2c_client *client, const struct i2c_device_id *id);

#ifdef CONFIG_OF
static const struct of_device_id md_gc0310yuv_of_match[] = {
	{.compatible = "mediatek,md_gc0310yuv",},
	{},
};

MODULE_DEVICE_TABLE(of, md_gc0310yuv_of_match);
#endif

static struct i2c_driver md_gc0310yuv_driver = {
	.driver = {
		   .name = "md_gc0310yuv",
#ifdef CONFIG_OF
		   .of_match_table = md_gc0310yuv_of_match,
#endif
	},
	.probe = md_gc0310yuv_driver_probe,
	.id_table = md_gc0310yuv_i2c_id,
};

/**********************************************************
  *
  *   [Global Variable]
  *
  *********************************************************/
static DEFINE_MUTEX(md_gc0310yuv_i2c_access);

/**********************************************************
  *
  *   [I2C Function For Read/Write md_gc0310yuv]
  *
  *********************************************************/
int md_gc0310yuv_read_byte(unsigned char cmd, unsigned char *returnData)
{
	char cmd_buf[1] = { 0x00 };
	char readData = 0;
	int ret = 0;

	mutex_lock(&md_gc0310yuv_i2c_access);

	mdouble_new_client->ext_flag =
	    ((mdouble_new_client->ext_flag) & I2C_MASK_FLAG) | I2C_WR_FLAG | I2C_DIRECTION_FLAG;

	cmd_buf[0] = cmd;
	ret = i2c_master_send(mdouble_new_client, &cmd_buf[0], (1 << 8 | 1));
	if (ret < 0) {
		mdouble_new_client->ext_flag = 0;

		mutex_unlock(&md_gc0310yuv_i2c_access);
		return 0;
	}

	readData = cmd_buf[0];
	*returnData = readData;

	mdouble_new_client->ext_flag = 0;

	mutex_unlock(&md_gc0310yuv_i2c_access);
	return 1;
}

int md_gc0310yuv_write_byte(unsigned char cmd, unsigned char writeData)
{
	char write_data[2] = { 0 };
	int ret = 0;

	mutex_lock(&md_gc0310yuv_i2c_access);

	write_data[0] = cmd;
	write_data[1] = writeData;

	mdouble_new_client->ext_flag = ((mdouble_new_client->ext_flag) & I2C_MASK_FLAG) | I2C_DIRECTION_FLAG;

	ret = i2c_master_send(mdouble_new_client, write_data, 2);
	if (ret < 0) {

		mdouble_new_client->ext_flag = 0;
		mutex_unlock(&md_gc0310yuv_i2c_access);
		return 0;
	}

	mdouble_new_client->ext_flag = 0;
	mutex_unlock(&md_gc0310yuv_i2c_access);
	return 1;
}

void mdouble_sensor_init(void)
{
	md_gc0310yuv_write_byte(0xfe,0xf0);
	md_gc0310yuv_write_byte(0xfe,0xf0);
	md_gc0310yuv_write_byte(0xfe,0x00);
	md_gc0310yuv_write_byte(0xfc,0x0e);
	md_gc0310yuv_write_byte(0xfc,0x0e);
	md_gc0310yuv_write_byte(0xf2,0x80);
	md_gc0310yuv_write_byte(0xf3,0x00);
	md_gc0310yuv_write_byte(0xf7,0x1b);
	md_gc0310yuv_write_byte(0xf8,0x04);  // from 03 to 04
	md_gc0310yuv_write_byte(0xf9,0x8e);
	md_gc0310yuv_write_byte(0xfa,0x11);
	/////////////////////////////////////////////////      
	///////////////////   MIPI   ////////////////////      
	/////////////////////////////////////////////////      
	md_gc0310yuv_write_byte(0xfe,0x03);
	md_gc0310yuv_write_byte(0x40,0x08);
	md_gc0310yuv_write_byte(0x42,0x00);
	md_gc0310yuv_write_byte(0x43,0x00);
	md_gc0310yuv_write_byte(0x01,0x03);
	md_gc0310yuv_write_byte(0x10,0x84);

	md_gc0310yuv_write_byte(0x01,0x03);             
	md_gc0310yuv_write_byte(0x02,0x00);             
	md_gc0310yuv_write_byte(0x03,0x94);             
	md_gc0310yuv_write_byte(0x04,0x01);            
	md_gc0310yuv_write_byte(0x05,0x40);  // 40      20     
	md_gc0310yuv_write_byte(0x06,0x80);             
	md_gc0310yuv_write_byte(0x11,0x1e);             
	md_gc0310yuv_write_byte(0x12,0x00);      
	md_gc0310yuv_write_byte(0x13,0x05);             
	md_gc0310yuv_write_byte(0x15,0x10);                                                                    
	md_gc0310yuv_write_byte(0x21,0x10);             
	md_gc0310yuv_write_byte(0x22,0x01);             
	md_gc0310yuv_write_byte(0x23,0x10);                                             
	md_gc0310yuv_write_byte(0x24,0x02);                                             
	md_gc0310yuv_write_byte(0x25,0x10);                                             
	md_gc0310yuv_write_byte(0x26,0x03);                                             
	md_gc0310yuv_write_byte(0x29,0x02); //02                                            
	md_gc0310yuv_write_byte(0x2a,0x0a);   //0a                                          
	md_gc0310yuv_write_byte(0x2b,0x04);                                             
	md_gc0310yuv_write_byte(0xfe,0x00);
	/////////////////////////////////////////////////
	/////////////////   CISCTL reg  /////////////////
	/////////////////////////////////////////////////
	md_gc0310yuv_write_byte(0x00,0x2f);
	md_gc0310yuv_write_byte(0x01,0x0f);
	md_gc0310yuv_write_byte(0x02,0x04);
	md_gc0310yuv_write_byte(0x03,0x04);
	md_gc0310yuv_write_byte(0x04,0xd0);
	md_gc0310yuv_write_byte(0x09,0x00);
	md_gc0310yuv_write_byte(0x0a,0x00);
	md_gc0310yuv_write_byte(0x0b,0x00);
	md_gc0310yuv_write_byte(0x0c,0x06);
	md_gc0310yuv_write_byte(0x0d,0x01);
	md_gc0310yuv_write_byte(0x0e,0xe8);
	md_gc0310yuv_write_byte(0x0f,0x02);
	md_gc0310yuv_write_byte(0x10,0x88);
	md_gc0310yuv_write_byte(0x16,0x00);
	md_gc0310yuv_write_byte(0x17,0x14);
	md_gc0310yuv_write_byte(0x18,0x1a);
	md_gc0310yuv_write_byte(0x19,0x14);
	md_gc0310yuv_write_byte(0x1b,0x48);
	md_gc0310yuv_write_byte(0x1e,0x6b);
	md_gc0310yuv_write_byte(0x1f,0x28);
	md_gc0310yuv_write_byte(0x20,0x8b);  // from 89 to 8b
	md_gc0310yuv_write_byte(0x21,0x49);
	md_gc0310yuv_write_byte(0x22,0xb0);
	md_gc0310yuv_write_byte(0x23,0x04);
	md_gc0310yuv_write_byte(0x24,0x16);
	md_gc0310yuv_write_byte(0x34,0x20);

	/////////////////////////////////////////////////
	////////////////////   BLK   ////////////////////
	/////////////////////////////////////////////////
	md_gc0310yuv_write_byte(0x26,0x23); 
	md_gc0310yuv_write_byte(0x28,0xff); 
	md_gc0310yuv_write_byte(0x29,0x00); 
	md_gc0310yuv_write_byte(0x33,0x10); 
	md_gc0310yuv_write_byte(0x37,0x20); 
	md_gc0310yuv_write_byte(0x38,0x10); 
	md_gc0310yuv_write_byte(0x47,0x80); 
	md_gc0310yuv_write_byte(0x4e,0x66); 
	md_gc0310yuv_write_byte(0xa8,0x02); 
	md_gc0310yuv_write_byte(0xa9,0x80);

	/////////////////////////////////////////////////
	//////////////////   ISP reg  ///////////////////
	/////////////////////////////////////////////////
	md_gc0310yuv_write_byte(0x40,0xff); 
	md_gc0310yuv_write_byte(0x41,0x21); 
	md_gc0310yuv_write_byte(0x42,0xcf); 
	md_gc0310yuv_write_byte(0x44,0x01); // 02 yuv 
	md_gc0310yuv_write_byte(0x45,0xa0); // from a8 - a4 a4-a0
	md_gc0310yuv_write_byte(0x46,0x03); 
	md_gc0310yuv_write_byte(0x4a,0x11);
	md_gc0310yuv_write_byte(0x4b,0x01);
	md_gc0310yuv_write_byte(0x4c,0x20); 
	md_gc0310yuv_write_byte(0x4d,0x05); 
	md_gc0310yuv_write_byte(0x4f,0x01);
	md_gc0310yuv_write_byte(0x50,0x01); 
	md_gc0310yuv_write_byte(0x55,0x01); 
	md_gc0310yuv_write_byte(0x56,0xe0);
	md_gc0310yuv_write_byte(0x57,0x02); 
	md_gc0310yuv_write_byte(0x58,0x80);

	/////////////////////////////////////////////////  
	///////////////////   GAIN   ////////////////////
	/////////////////////////////////////////////////
	md_gc0310yuv_write_byte(0x70,0x70); 
	md_gc0310yuv_write_byte(0x5a,0x84); 
	md_gc0310yuv_write_byte(0x5b,0xc9); 
	md_gc0310yuv_write_byte(0x5c,0xed); 
	md_gc0310yuv_write_byte(0x77,0x74); 
	md_gc0310yuv_write_byte(0x78,0x40); 
	md_gc0310yuv_write_byte(0x79,0x5f); 

	///////////////////////////////////////////////// 
	///////////////////   DNDD  /////////////////////
	///////////////////////////////////////////////// 
	md_gc0310yuv_write_byte(0x82,0x1f); 
	md_gc0310yuv_write_byte(0x83,0x0b);


	///////////////////////////////////////////////// 
	//////////////////   EEINTP  ////////////////////
	///////////////////////////////////////////////// 
	md_gc0310yuv_write_byte(0x8f,0xff); 
	md_gc0310yuv_write_byte(0x90,0x9f); 
	md_gc0310yuv_write_byte(0x91,0x90); 
	md_gc0310yuv_write_byte(0x92,0x03); 
	md_gc0310yuv_write_byte(0x93,0x03); 
	md_gc0310yuv_write_byte(0x94,0x05);
	md_gc0310yuv_write_byte(0x95,0x65); 
	md_gc0310yuv_write_byte(0x96,0xf0); 

	///////////////////////////////////////////////// 
	/////////////////////  ASDE  ////////////////////
	///////////////////////////////////////////////// 
	md_gc0310yuv_write_byte(0xfe,0x00);
	md_gc0310yuv_write_byte(0x9a,0x20);
	md_gc0310yuv_write_byte(0x9b,0x80);
	md_gc0310yuv_write_byte(0x9c,0x40);
	md_gc0310yuv_write_byte(0x9d,0x80);
	md_gc0310yuv_write_byte(0xa1,0x30);
	md_gc0310yuv_write_byte(0xa2,0x32);
	md_gc0310yuv_write_byte(0xa4,0x30);
	md_gc0310yuv_write_byte(0xa5,0x30);
	md_gc0310yuv_write_byte(0xaa,0x50);
	md_gc0310yuv_write_byte(0xac,0x22);

	/////////////////////////////////////////////////
	///////////////////   GAMMA   ///////////////////
	/////////////////////////////////////////////////
	md_gc0310yuv_write_byte(0xbf,0x08); 
	md_gc0310yuv_write_byte(0xc0,0x16); 
	md_gc0310yuv_write_byte(0xc1,0x28); 
	md_gc0310yuv_write_byte(0xc2,0x41); 
	md_gc0310yuv_write_byte(0xc3,0x5a); 
	md_gc0310yuv_write_byte(0xc4,0x6c); 
	md_gc0310yuv_write_byte(0xc5,0x7a); 
	md_gc0310yuv_write_byte(0xc6,0x96); 
	md_gc0310yuv_write_byte(0xc7,0xac); 
	md_gc0310yuv_write_byte(0xc8,0xbc); 
	md_gc0310yuv_write_byte(0xc9,0xc9); 
	md_gc0310yuv_write_byte(0xca,0xd3); 
	md_gc0310yuv_write_byte(0xcb,0xdd); 
	md_gc0310yuv_write_byte(0xcc,0xe5); 
	md_gc0310yuv_write_byte(0xcd,0xf1); 
	md_gc0310yuv_write_byte(0xce,0xfa); 
	md_gc0310yuv_write_byte(0xcf,0xff);

	/////////////////////////////////////////////////
	///////////////////   YCP  //////////////////////
	/////////////////////////////////////////////////
	md_gc0310yuv_write_byte(0xd0,0x40); 
	md_gc0310yuv_write_byte(0xd1,0x34); 
	md_gc0310yuv_write_byte(0xd2,0x34); 
	md_gc0310yuv_write_byte(0xd3,0x3c); 
	md_gc0310yuv_write_byte(0xd6,0xf2); 
	md_gc0310yuv_write_byte(0xd7,0x1b); 
	md_gc0310yuv_write_byte(0xd8,0x18); 
	md_gc0310yuv_write_byte(0xdd,0x03); 
	/////////////////////////////////////////////////
	////////////////////   AEC   ////////////////////
	/////////////////////////////////////////////////
	md_gc0310yuv_write_byte(0xfe,0x01);
	md_gc0310yuv_write_byte(0x05,0x30); 
	md_gc0310yuv_write_byte(0x06,0x75); 
	md_gc0310yuv_write_byte(0x07,0x40); 
	md_gc0310yuv_write_byte(0x08,0xb0); 
	md_gc0310yuv_write_byte(0x0a,0xc5); 
	md_gc0310yuv_write_byte(0x0b,0x11);
	md_gc0310yuv_write_byte(0x0c,0x00); 
	md_gc0310yuv_write_byte(0x12,0x52);
	md_gc0310yuv_write_byte(0x13,0x38); 
	md_gc0310yuv_write_byte(0x18,0x95);
	md_gc0310yuv_write_byte(0x19,0x96);
	md_gc0310yuv_write_byte(0x1f,0x20);
	md_gc0310yuv_write_byte(0x20,0xc0); 
	md_gc0310yuv_write_byte(0x3e,0x40); 
	md_gc0310yuv_write_byte(0x3f,0x57); 
	md_gc0310yuv_write_byte(0x40,0x7d); 
	md_gc0310yuv_write_byte(0x03,0x60); 
	md_gc0310yuv_write_byte(0x44,0x02); 
	/////////////////////////////////////////////////
	////////////////////   AWB   ////////////////////
	/////////////////////////////////////////////////
	md_gc0310yuv_write_byte(0x1c,0x91); 
	md_gc0310yuv_write_byte(0x21,0x15); 
	md_gc0310yuv_write_byte(0x50,0x80);
	md_gc0310yuv_write_byte(0x56,0x04);
	md_gc0310yuv_write_byte(0x58,0x08);    
	md_gc0310yuv_write_byte(0x59,0x08); 
	md_gc0310yuv_write_byte(0x5b,0x82);  // 02 to 82 to 02
	md_gc0310yuv_write_byte(0x61,0x8d); 
	md_gc0310yuv_write_byte(0x62,0xa7); 
	md_gc0310yuv_write_byte(0x63,0x00);   // d0 to  00
	md_gc0310yuv_write_byte(0x65,0x06);
	md_gc0310yuv_write_byte(0x66,0x06);   // 06 to 03
	md_gc0310yuv_write_byte(0x67,0x84); 
	md_gc0310yuv_write_byte(0x69,0x08);   // 08 to 20
	md_gc0310yuv_write_byte(0x6a,0x25); 
	md_gc0310yuv_write_byte(0x6b,0x01); 
	md_gc0310yuv_write_byte(0x6c,0x00);   // 00 to 0c
	md_gc0310yuv_write_byte(0x6d,0x02); 
	md_gc0310yuv_write_byte(0x6e,0x00);  // f0 to 00
	md_gc0310yuv_write_byte(0x6f,0x80); 
	md_gc0310yuv_write_byte(0x76,0x80); 
	md_gc0310yuv_write_byte(0x78,0xaf); 
	md_gc0310yuv_write_byte(0x79,0x75);
	md_gc0310yuv_write_byte(0x7a,0x40);
	md_gc0310yuv_write_byte(0x7b,0x50);
	md_gc0310yuv_write_byte(0x7c,0x08); //0c to 08 8.11

	md_gc0310yuv_write_byte(0xa4,0xb9); 
	md_gc0310yuv_write_byte(0xa5,0xa0);
	md_gc0310yuv_write_byte(0x90,0xc9); 
	md_gc0310yuv_write_byte(0x91,0xbe);
	md_gc0310yuv_write_byte(0xa6,0xb8); 
	md_gc0310yuv_write_byte(0xa7,0x95); 
	md_gc0310yuv_write_byte(0x92,0xe6); 
	md_gc0310yuv_write_byte(0x93,0xca); 
	md_gc0310yuv_write_byte(0xa9,0xb6); 
	md_gc0310yuv_write_byte(0xaa,0x89); 
	md_gc0310yuv_write_byte(0x95,0x23); 
	md_gc0310yuv_write_byte(0x96,0xe7); 
	md_gc0310yuv_write_byte(0xab,0x9d); 
	md_gc0310yuv_write_byte(0xac,0x80);
	md_gc0310yuv_write_byte(0x97,0x43); 
	md_gc0310yuv_write_byte(0x98,0x24); 
	md_gc0310yuv_write_byte(0xae,0xd0);   // b7 to d0
	md_gc0310yuv_write_byte(0xaf,0x9e); 
	md_gc0310yuv_write_byte(0x9a,0x43);
	md_gc0310yuv_write_byte(0x9b,0x24); 

	md_gc0310yuv_write_byte(0xb0,0xc0);  // c8 to c0
	md_gc0310yuv_write_byte(0xb1,0xa8);   // 97 to a8
	md_gc0310yuv_write_byte(0x9c,0xc4); 
	md_gc0310yuv_write_byte(0x9d,0x44); 
	md_gc0310yuv_write_byte(0xb3,0xb7); 
	md_gc0310yuv_write_byte(0xb4,0x7f);
	md_gc0310yuv_write_byte(0x9f,0xc7);
	md_gc0310yuv_write_byte(0xa0,0xc8); 
	md_gc0310yuv_write_byte(0xb5,0x00); 
	md_gc0310yuv_write_byte(0xb6,0x00);
	md_gc0310yuv_write_byte(0xa1,0x00);
	md_gc0310yuv_write_byte(0xa2,0x00);
	md_gc0310yuv_write_byte(0x86,0x60);
	md_gc0310yuv_write_byte(0x87,0x08);
	md_gc0310yuv_write_byte(0x88,0x00);
	md_gc0310yuv_write_byte(0x89,0x00);
	md_gc0310yuv_write_byte(0x8b,0xde);
	md_gc0310yuv_write_byte(0x8c,0x80);
	md_gc0310yuv_write_byte(0x8d,0x00);
	md_gc0310yuv_write_byte(0x8e,0x00);
	md_gc0310yuv_write_byte(0x94,0x55);
	md_gc0310yuv_write_byte(0x99,0xa6);
	md_gc0310yuv_write_byte(0x9e,0xaa);
	md_gc0310yuv_write_byte(0xa3,0x0a);
	md_gc0310yuv_write_byte(0x8a,0x0a);
	md_gc0310yuv_write_byte(0xa8,0x55);
	md_gc0310yuv_write_byte(0xad,0x55);
	md_gc0310yuv_write_byte(0xb2,0x55);
	md_gc0310yuv_write_byte(0xb7,0x05);
	md_gc0310yuv_write_byte(0x8f,0x05);
	md_gc0310yuv_write_byte(0xb8,0xcc);
	md_gc0310yuv_write_byte(0xb9,0x9a);

	/////////////////////////////////////
	////////////////////  CC ////////////
	/////////////////////////////////////
	md_gc0310yuv_write_byte(0xfe,0x01);
	md_gc0310yuv_write_byte(0xd0,0x38);
	md_gc0310yuv_write_byte(0xd1,0xfd);
	md_gc0310yuv_write_byte(0xd2,0x06);
	md_gc0310yuv_write_byte(0xd3,0xf0);
	md_gc0310yuv_write_byte(0xd4,0x40);
	md_gc0310yuv_write_byte(0xd5,0x08);
	md_gc0310yuv_write_byte(0xd6,0x30);
	md_gc0310yuv_write_byte(0xd7,0x00);
	md_gc0310yuv_write_byte(0xd8,0x0a);
	md_gc0310yuv_write_byte(0xd9,0x16);
	md_gc0310yuv_write_byte(0xda,0x39);
	md_gc0310yuv_write_byte(0xdb,0xf8);

	/////////////////////////////////////////////////
	////////////////////   LSC   ////////////////////
	/////////////////////////////////////////////////
	md_gc0310yuv_write_byte(0xfe,0x01); 
	md_gc0310yuv_write_byte(0xc1,0x3c); 
	md_gc0310yuv_write_byte(0xc2,0x50); 
	md_gc0310yuv_write_byte(0xc3,0x00); 
	md_gc0310yuv_write_byte(0xc4,0x40); 
	md_gc0310yuv_write_byte(0xc5,0x30); 
	md_gc0310yuv_write_byte(0xc6,0x30); 
	md_gc0310yuv_write_byte(0xc7,0x10); 
	md_gc0310yuv_write_byte(0xc8,0x00); 
	md_gc0310yuv_write_byte(0xc9,0x00); 
	md_gc0310yuv_write_byte(0xdc,0x20); 
	md_gc0310yuv_write_byte(0xdd,0x10); 
	md_gc0310yuv_write_byte(0xdf,0x00); 
	md_gc0310yuv_write_byte(0xde,0x00); 

	/////////////////////////////////////////////////
	///////////////////  Histogram  /////////////////
	/////////////////////////////////////////////////
	md_gc0310yuv_write_byte(0x01,0x10); 
	md_gc0310yuv_write_byte(0x0b,0x31); 
	md_gc0310yuv_write_byte(0x0e,0x50); 
	md_gc0310yuv_write_byte(0x0f,0x0f); 
	md_gc0310yuv_write_byte(0x10,0x6e); 
	md_gc0310yuv_write_byte(0x12,0xa0); 
	md_gc0310yuv_write_byte(0x15,0x60); 
	md_gc0310yuv_write_byte(0x16,0x60); 
	md_gc0310yuv_write_byte(0x17,0xe0); 

	/////////////////////////////////////////////////
	//////////////   Measure Window   ///////////////
	/////////////////////////////////////////////////
	md_gc0310yuv_write_byte(0xcc,0x0c);  
	md_gc0310yuv_write_byte(0xcd,0x10); 
	md_gc0310yuv_write_byte(0xce,0xa0); 
	md_gc0310yuv_write_byte(0xcf,0xe6); 

	/////////////////////////////////////////////////
	/////////////////   dark sun   //////////////////
	/////////////////////////////////////////////////
	md_gc0310yuv_write_byte(0x45,0xf7);
	md_gc0310yuv_write_byte(0x46,0xff); 
	md_gc0310yuv_write_byte(0x47,0x15);
	md_gc0310yuv_write_byte(0x48,0x03); 
	md_gc0310yuv_write_byte(0x4f,0x60); 

	/////////////////////////////////////////////////
	///////////////////  banding  ///////////////////
	/////////////////////////////////////////////////
	md_gc0310yuv_write_byte(0xfe,0x00);
	md_gc0310yuv_write_byte(0x05,0x01);
	md_gc0310yuv_write_byte(0x06,0x18); //HB
#if 1    
	md_gc0310yuv_write_byte(0x07,0x00);
	md_gc0310yuv_write_byte(0x08,0x10); //VB  from 10 to 50
#else
	md_gc0310yuv_write_byte(0x07,0x01);
	md_gc0310yuv_write_byte(0x08,0xe0); //VB
#endif
	md_gc0310yuv_write_byte(0xfe,0x01);
	md_gc0310yuv_write_byte(0x25,0x00); //step 
	md_gc0310yuv_write_byte(0x26,0x9a); 
	md_gc0310yuv_write_byte(0x27,0x01); //30fps
	md_gc0310yuv_write_byte(0x28,0xce);  
	md_gc0310yuv_write_byte(0x29,0x04); //12.5fps
	md_gc0310yuv_write_byte(0x2a,0x36); 
	md_gc0310yuv_write_byte(0x2b,0x06); //10fps
	md_gc0310yuv_write_byte(0x2c,0x04); 
	md_gc0310yuv_write_byte(0x2d,0x0c); //5fps
	md_gc0310yuv_write_byte(0x2e,0x08);
	md_gc0310yuv_write_byte(0x3c,0x20);

	/////////////////////////////////////////////////
	///////////////////   MIPI   ////////////////////
	/////////////////////////////////////////////////
	md_gc0310yuv_write_byte(0xfe,0x03);
	md_gc0310yuv_write_byte(0x10,0x94);  
	md_gc0310yuv_write_byte(0xfe,0x00); 
}
EXPORT_SYMBOL(mdouble_sensor_init);

#if 1
static ssize_t mdouble_alsvalue_store(struct device *dev,struct device_attribute *attr, const char *buf, size_t size)
{
    //int enable = 0;

    return size;
}
static ssize_t mdouble_alsvalue_show(struct device *dev, struct device_attribute *attr, char *buf)
{
#if defined(MD_DRIVER_DEBUG)
	mdouble_sensor_id=0;
	mdouble_sensor_id=mdouble_sensor_open();

	//mdouble_sensor_init();
	//mdelay(100);

    mdouble_als_value=mdouble_als_open();
            
    return scnprintf(buf, PAGE_SIZE, "mdouble_sensor_id=0x%x mdouble_als_value=0x%x\n", mdouble_sensor_id,mdouble_als_value);
#else
	mdouble_als_value=mdouble_als_open();
		
	return scnprintf(buf, PAGE_SIZE, "%d\n", mdouble_als_value);
#endif
}

static ssize_t mdouble_id_store(struct device *dev,struct device_attribute *attr, const char *buf, size_t size)
{
    //int enable = 0;

    return size;
}
static ssize_t mdouble_id_show(struct device *dev, struct device_attribute *attr, char *buf)
{
	//char currSensorName[20]="md_gc0310yuv"; 
	//char mode_name[20]="mdouble_sensor";  

	//kdCISModulePowerOn(2,currSensorName,1,mode_name);
	
    mdouble_sensor_id=mdouble_sensor_open();

	//kdCISModulePowerOn(2,currSensorName,0,mode_name);
            
    return scnprintf(buf, PAGE_SIZE, "%x\n", mdouble_sensor_id);
}


static DEVICE_ATTR(mdouble_alsvalue, 0644, mdouble_alsvalue_show, mdouble_alsvalue_store);
static DEVICE_ATTR(mdouble_id, 0644, mdouble_id_show, mdouble_id_store);

#endif

static int mdouble_als_open(void)
{
	int als_value = 0;
	char read_id[3];
	int i=0;

	md_gc0310yuv_write_byte(0xfe,0x01);
	md_gc0310yuv_write_byte(0x21,0x12);//bit[2:0]  default:0x15   
   	/* check_sensor_als */
	do
	{
	    for ( i=0; i < 3; i++)
	    {
	    	md_gc0310yuv_write_byte(0xfe,0x00);
			md_gc0310yuv_read_byte(0xef,&read_id[0]);

			als_value = read_id[0];
	     	printk("[MDOUBLE_GC0310YUV]:als_value == %d\n", als_value);
	    }
	    mdelay(20);
	}while(0);     

	return als_value;
}


static int mdouble_sensor_open(void)
{
	int id = 0;
	char read_id[3];
	int i=0;
		
   	/* check_sensor_ID */
   
	do
	{
	    for ( i=0; i < 3; i++)
	    {
			md_gc0310yuv_read_byte(0xf0,&read_id[0]);
			md_gc0310yuv_read_byte(0xf1,&read_id[1]);

			id = (read_id[0] << 8) | read_id[1];
	     	printk("[MDOUBLE_GC0310YUV]:check_sensor_ID id==0x%x\n", id);
	    }
	    mdelay(20);
	}while(0);     

	return id;
}

static int md_gc0310yuv_driver_probe(struct i2c_client *client, const struct i2c_device_id *id)
{
	//char currSensorName[20]="md_gc0310yuv"; 
	//char mode_name[20]="mdouble_sensor";  
	
	mdouble_new_client = client;

#if 0
	printk("%s create mdouble_1 \n", __func__);
	mdouble_class = class_create(THIS_MODULE, "mdouble");
	printk("%s create mdouble_2 \n", __func__);
	mdouble_dev = device_create(mdouble_class,NULL, 0, NULL,  "mdouble");
	printk("%s create mdouble_3 \n", __func__);
	device_create_file(mdouble_dev, &dev_attr_mdouble_alsvalue);
	device_create_file(mdouble_dev, &dev_attr_mdouble_id);

	printk("%s create mdouble sys files success.....\n", __func__);
#endif	

#if 0
	/*kdCISModulePowerOn*/   
	// kdCISModulePowerOn(CAMERA_DUAL_CAMERA_SENSOR_ENUM SensorIdx, char *currSensorName, BOOL On, char *mode_name);
	printk("%s begin....\n", __func__);
	kdCISModulePowerOn(2,currSensorName,1,mode_name);   
	printk("%s end....\n", __func__);

	mdelay(20);   

	mdouble_sensor_open();    
#endif	

	return 0;
}




/**********************************************************
  *
  *   [platform_driver API]
  *
  *********************************************************/
static int md_gc0310yuv_user_space_probe(struct platform_device *dev)
{
	printk( "******** md_gc0310yuv_user_space_probe!! ********\n");

#if 1
		printk("%s create mdouble_1 \n", __func__);
		mdouble_class = class_create(THIS_MODULE, "mdouble");
		printk("%s create mdouble_2 \n", __func__);
		mdouble_dev = device_create(mdouble_class,NULL, 0, NULL,  "mdouble");
		printk("%s create mdouble_3 \n", __func__);
		device_create_file(mdouble_dev, &dev_attr_mdouble_alsvalue);
		device_create_file(mdouble_dev, &dev_attr_mdouble_id);
	
		printk("%s create mdouble sys files success.....\n", __func__);
#endif	
	

	return 0;
}

struct platform_device md_gc0310yuv_user_space_device = {
	.name = "md_gc0310yuv-user",
	.id = -1,
};

static struct platform_driver md_gc0310yuv_user_space_driver = {
	.probe = md_gc0310yuv_user_space_probe,
	.driver = {
		   .name = "md_gc0310yuv-user",
	},
};

static int __init md_gc0310yuv_init(void)
{
	int ret = 0;

	if (i2c_add_driver(&md_gc0310yuv_driver) != 0) {
		printk("[md_gc0310yuv_init] failed to register md_gc0310yuv i2c driver.\n");
	} else {
		printk("[md_gc0310yuv_init] Success to register md_gc0310yuv i2c driver.\n");
	}

	/* md_gc0310yuv user space access interface */
	ret = platform_device_register(&md_gc0310yuv_user_space_device);
	if (ret) {
		printk( "****[md_gc0310yuv_init] Unable to device register(%d)\n",
			    ret);
		return ret;
	}
	ret = platform_driver_register(&md_gc0310yuv_user_space_driver);
	if (ret) {
		printk( "****[md_gc0310yuv_init] Unable to register driver (%d)\n",
			    ret);
		return ret;
	}

	return 0;
}

static void __exit md_gc0310yuv_exit(void)
{
	i2c_del_driver(&md_gc0310yuv_driver);
}

//module_init(md_gc0310yuv_init);
late_initcall(md_gc0310yuv_init);
module_exit(md_gc0310yuv_exit);

MODULE_LICENSE("GPL");
MODULE_DESCRIPTION("I2C md_gc0310yuv Driver");
MODULE_AUTHOR("aren.jiang<aren.jiang@runyee.com.cn>");
#endif
