#include <linux/types.h>
#include <linux/init.h>		/* For init/exit macros */
#include <linux/module.h>	/* For MODULE_ marcros  */
#include <linux/platform_device.h>
#include <linux/i2c.h>
#include <linux/slab.h>
#include <linux/delay.h>
#ifdef CONFIG_OF
#include <linux/of.h>
#include <linux/of_irq.h>
#include <linux/of_address.h>
#endif

/**********************************************************
  *   [I2C Slave Setting]  
  *********************************************************/
#define PLATFORM_DRIVER_NAME "hdmi_lt8912b"
#define LT8912B_DRVNAME      "lt8912b"

static struct i2c_client *hdmi_i2c_client;
struct pinctrl *lt8912b_pinctrl = NULL;
struct pinctrl_state *rst_low_cfg = NULL;
struct pinctrl_state *rst_high_cfg = NULL;


static int lt8912b_i2c_probe(struct i2c_client *client, const struct i2c_device_id *id);
static int lt8912b_i2c_remove(struct i2c_client *client);
static const struct i2c_device_id lt8912b_i2c_id[] = { {LT8912B_DRVNAME, 0}, {} };

static const struct of_device_id lt8912b_of_match[] = {
	{.compatible = "mediatek,lt8912b"},
	{},
};

static struct i2c_driver lt8912b_i2c_driver = {
	.probe = lt8912b_i2c_probe,
	.remove = lt8912b_i2c_remove,
	.driver.name = LT8912B_DRVNAME,
	.driver.of_match_table = lt8912b_of_match,
	.id_table =lt8912b_i2c_id,
};


/*************  [I2C Function For Read/Write lt8912b] *********************/
int HDMI_WriteI2C_Byte( unsigned char i2c_addr, unsigned char reg_addr, unsigned char value)
{	
	int ret = 0;
	char write_data[2]={0};	
	struct i2c_client *client=hdmi_i2c_client;
	
	client->addr=i2c_addr;
	client->addr=client->addr >>1;
	write_data[0]= reg_addr;
	write_data[1] = value;
        
  ret=i2c_master_send(client, write_data, 2);
	return ret ;
}

int HDMI_ReadI2C_Byte(unsigned char i2c_addr, unsigned char reg_addr, unsigned char *dataBuffer) 
{	
	int ret = 0;
	struct i2c_client *client=hdmi_i2c_client;
	
	client->addr=i2c_addr;
	client->addr=client->addr >>1;	
	(*dataBuffer) = reg_addr;
	ret = i2c_master_send(client, dataBuffer, 1);
	ret = i2c_master_recv(client, dataBuffer, 1);	
   
	return ret ;	
}
/**************************** runyee zhou add lt8912b start***************************/
enum
{
H_act = 0,
V_act,
H_tol,
V_tol,
H_bp,
H_sync,
V_sync,
V_bp
};

void LT8912B_reset_board(void)
{
     pinctrl_select_state(lt8912b_pinctrl, rst_high_cfg);
     mdelay(50);
     pinctrl_select_state(lt8912b_pinctrl, rst_low_cfg);
     mdelay(150);
     pinctrl_select_state(lt8912b_pinctrl, rst_high_cfg);
     mdelay(120);
}

// 设置输入的MIPI信号的Lane数
#define MIPI_LANE_INPUT_THREE
//#define MIPI_LANE_INPUT_FOUR

#if defined(MIPI_LANE_INPUT_THREE)
	#define MIPI_Lane	3	// 3 Lane MIPI input
#elif defined(MIPI_LANE_INPUT_FOUR)
	#define MIPI_Lane	4	// 4 Lane MIPI input
#else
	#define MIPI_Lane	4	// 4 Lane MIPI input
#endif



#define LT8912B_NORMAL_MODE

//#define LT8912B_SELF_TEST_MODE_1920X1080
//#define LT8912B_SELF_TEST_MODE_1366X768
//#define LT8912B_SELF_TEST_MODE_1280X720
#define LT8912B_SELF_TEST_MODE_720X1280


// 根据前端MIPI输入信号的Timing修改以下数组的值:
//	static int MIPI_Timing[] = 
//	H_act	V_act	H_total	V_total	H_BP	H_sync	V_sync	V_BP
//	{1920,	1080,	2200,	1125,	148,	44, 	5,	    36};// 1080P  Vesa Timing
//	{1366,	768,	1500,	800,	64,	    56,	    3,	    28};// 1366x768 VESA Timing
//	{1280,	720,	1360,	760,	35,	    10, 	4,	    17};// 1280x720 Timing
//	{720,	1280,	800,	1317,	35,	    10, 	4,	    17};// 720x1280 Timing

#if defined(LT8912B_SELF_TEST_MODE_1920X1080)
static int MIPI_Timing[] = 
//	H_act	V_act	H_total	V_total	H_BP	H_sync	V_sync	V_BP
	{1920,	1080,	2200,	1125,	148,	44, 	5,	    36};// 1920x1080 Timing
#elif defined(LT8912B_SELF_TEST_MODE_1366X768)
static int MIPI_Timing[] = 
//	H_act	V_act	H_total	V_total	H_BP	H_sync	V_sync	V_BP
	{1366,	768,	1500,	800,	64,	    56,	    3,	    28};// 1366x768 Timing
#elif defined(LT8912B_SELF_TEST_MODE_1280X720)
static int MIPI_Timing[] = 
//	H_act	V_act	H_total	V_total	H_BP	H_sync	V_sync	V_BP
	{1280,	720,	1360,	760,	35,	    10, 	4,	    17};// 1280x720 Timing
#elif defined(LT8912B_SELF_TEST_MODE_720X1280)
static int MIPI_Timing[] = 
//	H_act	V_act	H_total	V_total	H_BP	H_sync	V_sync	V_BP
	#if defined(MIPI_LANE_INPUT_THREE)
	{720,	1280,	870,	1324,	70,	    10, 	4,	    20};// 720x1280 Timing nt35521
	#elif defined(MIPI_LANE_INPUT_FOUR)
	//{720,	1280,	800,	1317,	35,	    10, 	4,	    17};// 720x1280 Timing st7703
	#else
	//{720,	1280,	800,	1317,	35,	    10, 	4,	    17};// 720x1280 Timing st7703
	#endif
#else
static int MIPI_Timing[] = 
//	H_act	V_act	H_total V_total H_BP	H_sync	V_sync	V_BP
	{1920,	1080,	2200,	1125,	148,	44, 	5,		36};// 1920x1080 Timing
#endif

void LT8912B_Initial(void)
{ 
	unsigned char read_data[5]={0};
	
	//先Reset LT8912B
	//RESET_Lt8912chip(); // 拉低LT8912 的reset pin，delay 100 ms左右，再拉高
	LT8912B_reset_board();
	
	//I2CADR = 0x90; // IIC address
	HDMI_WriteI2C_Byte(0x90,0x08,0xff);// Register address : 0x08; 	Value : 0xff
	HDMI_WriteI2C_Byte(0x90,0x09,0x81);
	HDMI_WriteI2C_Byte(0x90,0x0a,0xff);
	HDMI_WriteI2C_Byte(0x90,0x0b,0x64);//
	HDMI_WriteI2C_Byte(0x90,0x0c,0xff);
	
	HDMI_ReadI2C_Byte(0x90,0x08,read_data);
	HDMI_ReadI2C_Byte(0x90,0x09,read_data+1);
	HDMI_ReadI2C_Byte(0x90,0x0a,read_data+2);
	HDMI_ReadI2C_Byte(0x90,0x0b,read_data+3);
	HDMI_ReadI2C_Byte(0x90,0x0c,read_data+4);
				
	
printk("HDMI_ReadI2C_Byte [0x90] read_data[0]=%x,read_data[1]=%x,read_data[2]=%x,read_data[3]=%x,read_data[4]=%x \n",read_data[0],read_data[1],read_data[2],read_data[3],read_data[4]);


	HDMI_WriteI2C_Byte(0x90,0x44,0x31);// Close LVDS ouput
	HDMI_WriteI2C_Byte(0x90,0x51,0x1f);

	//I2CADR = 0x90;
	HDMI_WriteI2C_Byte(0x90,0x31,0xa1);
	HDMI_WriteI2C_Byte(0x90,0x32,0xa1);
	HDMI_WriteI2C_Byte(0x90,0x33,0x03);// 0x03 Open HDMI Tx； 0x00 Close HDMI Tx
	HDMI_WriteI2C_Byte(0x90,0x37,0x00);
	HDMI_WriteI2C_Byte(0x90,0x38,0x22);
	HDMI_WriteI2C_Byte(0x90,0x60,0x82);

//------------------------------------------//
	//I2CADR = 0x90;
	HDMI_WriteI2C_Byte(0x90,0x39,0x45);
	HDMI_WriteI2C_Byte(0x90,0x3b,0x00);

//------------------------------------------//
	//I2CADR = 0x90;
	HDMI_WriteI2C_Byte(0x90,0x44,0x31);
	HDMI_WriteI2C_Byte(0x90,0x55,0x44);
	HDMI_WriteI2C_Byte(0x90,0x57,0x01);
	HDMI_WriteI2C_Byte(0x90,0x5a,0x02);

//------------------------------------------//
//	MipiBasicSet();
	//I2CADR = 0x92;
	HDMI_WriteI2C_Byte(0x92,0x10,0x01); // 0x05 
	HDMI_WriteI2C_Byte(0x92,0x11,0x08); // 0x12 
	HDMI_WriteI2C_Byte(0x92,0x12,0x04);  
	HDMI_WriteI2C_Byte(0x92,0x13,MIPI_Lane%0x04);  // 00 4 lane  // 01 lane // 02 2 lane //03 3 lane
	HDMI_WriteI2C_Byte(0x92,0x14,0x00);  
	HDMI_WriteI2C_Byte(0x92,0x15,0x00);
	HDMI_WriteI2C_Byte(0x92,0x1a,0x03);  
	HDMI_WriteI2C_Byte(0x92,0x1b,0x03);  

//------------------------------------------//
//	设置 MIPI Timing
	//I2CADR = 0x92;
	HDMI_WriteI2C_Byte(0x92,0x18,(u8)(MIPI_Timing[H_sync]%256)); // hwidth
	HDMI_WriteI2C_Byte(0x92,0x19,(u8)(MIPI_Timing[V_sync]%256)); // vwidth
	HDMI_WriteI2C_Byte(0x92,0x1c,(u8)(MIPI_Timing[H_act]%256)); // H_active[7:0]
	HDMI_WriteI2C_Byte(0x92,0x1d,(u8)(MIPI_Timing[H_act]/256)); // H_active[15:8]
                        
	HDMI_WriteI2C_Byte(0x92,0x1e,0x67); // hs/vs/de pol hdmi sel pll sel
	HDMI_WriteI2C_Byte(0x92,0x2f,0x0c); // fifo_buff_length 12
                       
	HDMI_WriteI2C_Byte(0x92,0x34,(u8)(MIPI_Timing[H_tol]%256)); // H_total[7:0]
	HDMI_WriteI2C_Byte(0x92,0x35,(u8)(MIPI_Timing[H_tol]/256)); // H_total[15:8]
	HDMI_WriteI2C_Byte(0x92,0x36,(u8)(MIPI_Timing[V_tol]%256)); // V_total[7:0]
	HDMI_WriteI2C_Byte(0x92,0x37,(u8)(MIPI_Timing[V_tol]/256)); // V_total[15:8]
	HDMI_WriteI2C_Byte(0x92,0x38,(u8)(MIPI_Timing[V_bp]%256)); // VBP[7:0]
	HDMI_WriteI2C_Byte(0x92,0x39,(u8)(MIPI_Timing[V_bp]/256)); // VBP[15:8]
	HDMI_WriteI2C_Byte(0x92,0x3a,(u8)((MIPI_Timing[V_tol]-MIPI_Timing[V_act]-MIPI_Timing[V_bp]-MIPI_Timing[V_sync])%256)); // VFP[7:0]
	HDMI_WriteI2C_Byte(0x92,0x3b,(u8)((MIPI_Timing[V_tol]-MIPI_Timing[V_act]-MIPI_Timing[V_bp]-MIPI_Timing[V_sync])/256)); // VFP[15:8]
	HDMI_WriteI2C_Byte(0x92,0x3c,(u8)(MIPI_Timing[H_bp]%256)); // HBP[7:0]
	HDMI_WriteI2C_Byte(0x92,0x3d,(u8)(MIPI_Timing[H_bp]/256)); // HBP[15:8]
	HDMI_WriteI2C_Byte(0x92,0x3e,(u8)((MIPI_Timing[H_tol]-MIPI_Timing[H_act]-MIPI_Timing[H_bp]-MIPI_Timing[H_sync])%256)); // HFP[7:0]
	HDMI_WriteI2C_Byte(0x92,0x3f,(u8)((MIPI_Timing[H_tol]-MIPI_Timing[H_act]-MIPI_Timing[H_bp]-MIPI_Timing[H_sync])/256)); // HFP[15:8]

//------------------------------------------//

	//I2CADR = 0x92;
	HDMI_WriteI2C_Byte(0x92,0x4e,0x52);
	HDMI_WriteI2C_Byte(0x92,0x4f,0xde);
	HDMI_WriteI2C_Byte(0x92,0x50,0xc0);
	HDMI_WriteI2C_Byte(0x92,0x51,0x80);
	HDMI_WriteI2C_Byte(0x92,0x51,0x00);

	HDMI_WriteI2C_Byte(0x92,0x1f,0x5e);
	HDMI_WriteI2C_Byte(0x92,0x20,0x01);
	HDMI_WriteI2C_Byte(0x92,0x21,0x2c);
	HDMI_WriteI2C_Byte(0x92,0x22,0x01);
	HDMI_WriteI2C_Byte(0x92,0x23,0xfa);
	HDMI_WriteI2C_Byte(0x92,0x24,0x00);
	HDMI_WriteI2C_Byte(0x92,0x25,0xc8);
	HDMI_WriteI2C_Byte(0x92,0x26,0x00);
	HDMI_WriteI2C_Byte(0x92,0x27,0x5e);
	HDMI_WriteI2C_Byte(0x92,0x28,0x01);
	HDMI_WriteI2C_Byte(0x92,0x29,0x2c);
	HDMI_WriteI2C_Byte(0x92,0x2a,0x01);
	HDMI_WriteI2C_Byte(0x92,0x2b,0xfa);
	HDMI_WriteI2C_Byte(0x92,0x2c,0x00);
	HDMI_WriteI2C_Byte(0x92,0x2d,0xc8);
	HDMI_WriteI2C_Byte(0x92,0x2e,0x00);

	//I2CADR = 0x90;
	HDMI_WriteI2C_Byte(0x90,0x03,0x7f);
	mdelay(10);
	HDMI_WriteI2C_Byte(0x90,0x03,0xff);

	//I2CADR = 0x92;
	HDMI_WriteI2C_Byte(0x92,0x51,0x00);

	HDMI_WriteI2C_Byte(0x92,0x42,0x64);
	HDMI_WriteI2C_Byte(0x92,0x43,0x00);
	HDMI_WriteI2C_Byte(0x92,0x44,0x04);
	HDMI_WriteI2C_Byte(0x92,0x45,0x00);
	HDMI_WriteI2C_Byte(0x92,0x46,0x59);
	HDMI_WriteI2C_Byte(0x92,0x47,0x00);
	HDMI_WriteI2C_Byte(0x92,0x48,0xf2);
	HDMI_WriteI2C_Byte(0x92,0x49,0x06);
	HDMI_WriteI2C_Byte(0x92,0x4a,0x00);
	HDMI_WriteI2C_Byte(0x92,0x4b,0x72);
	HDMI_WriteI2C_Byte(0x92,0x4c,0x45);
	HDMI_WriteI2C_Byte(0x92,0x4d,0x00);
	HDMI_WriteI2C_Byte(0x92,0x52,0x08);
	HDMI_WriteI2C_Byte(0x92,0x53,0x00);
	HDMI_WriteI2C_Byte(0x92,0x54,0xb2);
	HDMI_WriteI2C_Byte(0x92,0x55,0x00);
	HDMI_WriteI2C_Byte(0x92,0x56,0xe4);
	HDMI_WriteI2C_Byte(0x92,0x57,0x0d);
	HDMI_WriteI2C_Byte(0x92,0x58,0x00);
	HDMI_WriteI2C_Byte(0x92,0x59,0xe4);
	HDMI_WriteI2C_Byte(0x92,0x5a,0x8a);
	HDMI_WriteI2C_Byte(0x92,0x5b,0x00);
	
	HDMI_WriteI2C_Byte(0x92,0x5c,0x34);
	HDMI_WriteI2C_Byte(0x92,0x1e,0x4f);
	HDMI_WriteI2C_Byte(0x92,0x51,0x00);
	
	HDMI_ReadI2C_Byte(0x92,0x5c,read_data);
	HDMI_ReadI2C_Byte(0x92,0x1e,read_data+1);
	HDMI_ReadI2C_Byte(0x92,0x51,read_data+2);

	
printk("HDMI_ReadI2C_Byte [0x92] read_data[0]=%x,read_data[1]=%x,read_data[2]=%x\n",read_data[0],read_data[1],read_data[2]);	

//------------------------------------------//

//	AudioIIsEn(); // IIS Input
	//I2CADR = 0x90;
	HDMI_WriteI2C_Byte(0x90,0xB2,0x01);
	//I2CADR = 0x94;
	HDMI_WriteI2C_Byte(0x94,0x06,0x08);
	HDMI_WriteI2C_Byte(0x94,0x07,0xF0);
	HDMI_WriteI2C_Byte(0x94,0x34,0xD2);
	
	HDMI_ReadI2C_Byte(0x94,0x06,read_data);
	HDMI_ReadI2C_Byte(0x94,0x07,read_data+1);
	HDMI_ReadI2C_Byte(0x94,0x34,read_data+2);

	
printk("HDMI_ReadI2C_Byte [0x94] read_data[0]=%x,read_data[1]=%x,read_data[2]=%x\n",read_data[0],read_data[1],read_data[2]);
	

//	AudioSPDIFEn(); // SPDIF Input
//	I2CADR = 0x90; 
//	HDMI_WriteI2C_Byte(0xB2,0x01);
//	I2CADR = 0x94;
//	HDMI_WriteI2C_Byte(0x06,0x0e);
//	HDMI_WriteI2C_Byte(0x07,0x00);
//	HDMI_WriteI2C_Byte(0x34,0xD2);

//------------------------------------------//

//	MIPIRxLogicRes();
	//I2CADR = 0x90;
	HDMI_WriteI2C_Byte(0x90,0x03,0x7f);
	mdelay(10);
	HDMI_WriteI2C_Byte(0x90,0x03,0xff);
//------------------------------------------//

#if 1//add by jst for test
	HDMI_ReadI2C_Byte(0x90,0x9c,read_data);
	HDMI_ReadI2C_Byte(0x90,0x9d,read_data+1);
	HDMI_ReadI2C_Byte(0x90,0x9e,read_data+2);
	HDMI_ReadI2C_Byte(0x90,0x9f,read_data+3);

	printk("[runyee: IIC_ADDR = 0x90] reg_9c=%x,reg_9d=%x,reg_9e=%x,reg_9f=%x \n",read_data[0],read_data[1],read_data[2],read_data[3]);
#endif

#if 0
//check hpd
  HDMI_WriteI2C_Byte(0x90,0x0b,0x7c);// 要关掉一个clk

  //(0x90)0xc1[bit7]代表当前HPD是高还是低，实时检测。
  //I2CADR = 0x90;
  HDMI_ReadI2C_Byte(0x90,0xc1,read_data);
  
printk("HDMI_ReadI2C_Byte [0x90] read_data[0]=%x\n",read_data[0]);  

  if((read_data[0] & 0x80)==0x80)
    printk("HDMI_ReadI2C_Byte check hpd ok!!!\n");
  else
    printk("HDMI_ReadI2C_Byte check hpd fail!!!\n");
    
  	HDMI_WriteI2C_Byte(0x90,0x0b,0x64);//  
#endif
}	

void LT8912B_test_1920x1080_Initial(void)
{ 
//-------------------LT8912B Initital---------------------//
  	LT8912B_reset_board(); //add reset
  	
//	DigitalClockEn(void)
//	I2CADR = 0x90;
	HDMI_WriteI2C_Byte(0x90,0x08,0xff);
	HDMI_WriteI2C_Byte(0x90,0x09,0xff);
	HDMI_WriteI2C_Byte(0x90,0x0a,0xff);
	HDMI_WriteI2C_Byte(0x90,0x0b,0xff);
	HDMI_WriteI2C_Byte(0x90,0x0c,0xff);

//	TxAnalog(void)
	//I2CADR = 0x90;
	HDMI_WriteI2C_Byte(0x90,0x31,0xa1);
	HDMI_WriteI2C_Byte(0x90,0x32,0xa1);
	HDMI_WriteI2C_Byte(0x90,0x33,0x03);
	HDMI_WriteI2C_Byte(0x90,0x37,0x00);
	HDMI_WriteI2C_Byte(0x90,0x38,0x22);
	HDMI_WriteI2C_Byte(0x90,0x60,0x82);

//	CbusAnalog(void)
	//I2CADR = 0x90;
	HDMI_WriteI2C_Byte(0x90,0x39,0x45);
	HDMI_WriteI2C_Byte(0x90,0x3b,0x00);


//	HDMIPllAnalog(void)
	//I2CADR = 0x90;
	HDMI_WriteI2C_Byte(0x90,0x44,0x31);
	HDMI_WriteI2C_Byte(0x90,0x55,0x44);
	HDMI_WriteI2C_Byte(0x90,0x57,0x01);
	HDMI_WriteI2C_Byte(0x90,0x5a,0x02);

	HDMI_WriteI2C_Byte(0x90,0xb2,0x01);
//--------------1080P test pattern------------------------//

	// Test pattern 1080P 60Hz
	//I2CADR = 0x92;
	HDMI_WriteI2C_Byte(0x92,0x72,0x12);
	HDMI_WriteI2C_Byte(0x92,0x73,0xc0);//RGD_PTN_DE_DLY[7:0]
	HDMI_WriteI2C_Byte(0x92,0x74,0x00);//RGD_PTN_DE_DLY[11:8]  192
	HDMI_WriteI2C_Byte(0x92,0x75,0x29);//RGD_PTN_DE_TOP[6:0]  41
	HDMI_WriteI2C_Byte(0x92,0x76,0x80);//RGD_PTN_DE_CNT[7:0]
	HDMI_WriteI2C_Byte(0x92,0x77,0x38);//RGD_PTN_DE_LIN[7:0]
	HDMI_WriteI2C_Byte(0x92,0x78,0x47);//RGD_PTN_DE_LIN[10:8],RGD_PTN_DE_CNT[11:8]
	HDMI_WriteI2C_Byte(0x92,0x79,0x98);//RGD_PTN_H_TOTAL[7:0]
	HDMI_WriteI2C_Byte(0x92,0x7a,0x65);//RGD_PTN_V_TOTAL[7:0]
	HDMI_WriteI2C_Byte(0x92,0x7b,0x48);//RGD_PTN_V_TOTAL[10:8],RGD_PTN_H_TOTAL[11:8]
	HDMI_WriteI2C_Byte(0x92,0x7c,0x2c);//RGD_PTN_HWIDTH[7:0]
	HDMI_WriteI2C_Byte(0x92,0x7d,0x05);//RGD_PTN_HWIDTH[9:8],RGD_PTN_VWIDTH[5:0]

	
	HDMI_WriteI2C_Byte(0x92,0x70,0x80);
	HDMI_WriteI2C_Byte(0x92,0x71,0x76);

	// 148.5M CLK
	HDMI_WriteI2C_Byte(0x92,0x4e,0x33);
	HDMI_WriteI2C_Byte(0x92,0x4f,0x33);
	HDMI_WriteI2C_Byte(0x92,0x50,0xd3);
	HDMI_WriteI2C_Byte(0x92,0x51,0x80);

//-------------------------------------------------------//
}

void LT8912B_test_720x1280_Initial(void)
{
	LT8912B_reset_board(); //add reset
	
	//	DigitalClockEn(void)
	//I2CADR = 0x90;
	HDMI_WriteI2C_Byte(0x90,0x08,0xff);
	HDMI_WriteI2C_Byte(0x90,0x09,0xff);
	HDMI_WriteI2C_Byte(0x90,0x0a,0xff);
	HDMI_WriteI2C_Byte(0x90,0x0b,0xff);
	HDMI_WriteI2C_Byte(0x90,0x0c,0xff);

	//	TxAnalog(void)
	//I2CADR = 0x90;
	HDMI_WriteI2C_Byte(0x90,0x31,0xa1);
	HDMI_WriteI2C_Byte(0x90,0x32,0xa1);
	HDMI_WriteI2C_Byte(0x90,0x33,0x03);
	HDMI_WriteI2C_Byte(0x90,0x37,0x00);
	HDMI_WriteI2C_Byte(0x90,0x38,0x22);
	HDMI_WriteI2C_Byte(0x90,0x60,0x82);

	//	CbusAnalog(void)
	//I2CADR = 0x90;
	HDMI_WriteI2C_Byte(0x90,0x39,0x45);
	HDMI_WriteI2C_Byte(0x90,0x3b,0x00);


	//	HDMIPllAnalog(void)
	//I2CADR = 0x90;
	HDMI_WriteI2C_Byte(0x90,0x44,0x31);
	HDMI_WriteI2C_Byte(0x90,0x55,0x44);
	HDMI_WriteI2C_Byte(0x90,0x57,0x01);
	HDMI_WriteI2C_Byte(0x90,0x5a,0x02);

	HDMI_WriteI2C_Byte(0x90,0xb2,0x01);
	//--------------720x1280 test pattern------------------------//

	// Test pattern 720x1280 60Hz
	//I2CADR = 0x92;
	HDMI_WriteI2C_Byte(0x92,0x72,0x12);
	HDMI_WriteI2C_Byte(0x92,0x73,0x2d);//RGD_PTN_DE_DLY[7:0]
	HDMI_WriteI2C_Byte(0x92,0x74,0x00);//RGD_PTN_DE_DLY[11:8]  192
	HDMI_WriteI2C_Byte(0x92,0x75,0x15);//RGD_PTN_DE_TOP[6:0]  41
	HDMI_WriteI2C_Byte(0x92,0x76,0xd0);//RGD_PTN_DE_CNT[7:0]
	HDMI_WriteI2C_Byte(0x92,0x77,0x00);//RGD_PTN_DE_LIN[7:0]
	HDMI_WriteI2C_Byte(0x92,0x78,0x52);//RGD_PTN_DE_LIN[10:8],RGD_PTN_DE_CNT[11:8]
	HDMI_WriteI2C_Byte(0x92,0x79,0x20);//RGD_PTN_H_TOTAL[7:0]
	HDMI_WriteI2C_Byte(0x92,0x7a,0x25);//RGD_PTN_V_TOTAL[7:0]
	HDMI_WriteI2C_Byte(0x92,0x7b,0x53);//RGD_PTN_V_TOTAL[10:8],RGD_PTN_H_TOTAL[11:8]
	HDMI_WriteI2C_Byte(0x92,0x7c,0x0a);//RGD_PTN_HWIDTH[7:0]
	HDMI_WriteI2C_Byte(0x92,0x7d,0x04);//RGD_PTN_HWIDTH[9:8],RGD_PTN_VWIDTH[5:0]


	HDMI_WriteI2C_Byte(0x92,0x70,0x80);
	HDMI_WriteI2C_Byte(0x92,0x71,0x76);

	// 63.2M CLK
	HDMI_WriteI2C_Byte(0x92,0x4e,0xb2);
	HDMI_WriteI2C_Byte(0x92,0x4f,0xe9);
	HDMI_WriteI2C_Byte(0x92,0x50,0x59);
	HDMI_WriteI2C_Byte(0x92,0x51,0x80);

}

void LT8912B_test_1280x720_Initial(void)
{
	LT8912B_reset_board(); //add reset
	
	//	DigitalClockEn(void)
	//I2CADR = 0x90;
	HDMI_WriteI2C_Byte(0x90,0x08,0xff);
	HDMI_WriteI2C_Byte(0x90,0x09,0xff);
	HDMI_WriteI2C_Byte(0x90,0x0a,0xff);
	HDMI_WriteI2C_Byte(0x90,0x0b,0xff);
	HDMI_WriteI2C_Byte(0x90,0x0c,0xff);
	
	//	TxAnalog(void)
	//I2CADR = 0x90;
	HDMI_WriteI2C_Byte(0x90,0x31,0xa1);
	HDMI_WriteI2C_Byte(0x90,0x32,0xa1);
	HDMI_WriteI2C_Byte(0x90,0x33,0x03);
	HDMI_WriteI2C_Byte(0x90,0x37,0x00);
	HDMI_WriteI2C_Byte(0x90,0x38,0x22);
	HDMI_WriteI2C_Byte(0x90,0x60,0x82);
	
	//	CbusAnalog(void)
	//I2CADR = 0x90;
	HDMI_WriteI2C_Byte(0x90,0x39,0x45);
	HDMI_WriteI2C_Byte(0x90,0x3b,0x00);
	
	
	//	HDMIPllAnalog(void)
	//I2CADR = 0x90;
	HDMI_WriteI2C_Byte(0x90,0x44,0x31);
	HDMI_WriteI2C_Byte(0x90,0x55,0x44);
	HDMI_WriteI2C_Byte(0x90,0x57,0x01);
	HDMI_WriteI2C_Byte(0x90,0x5a,0x02);
	
	
	HDMI_WriteI2C_Byte(0x90,0xb2,0x00);// 0x01: HDMI,0x00:DVI;有些显示器需要设置成DVI才有显示
	
	//I2CADR = 0x94;
	HDMI_WriteI2C_Byte(0x94,0x3c,0x41);// Null packet enable
	
	//--------------720P test pattern------------------------//
	
	// Test pattern 1280x720 60Hz
	//I2CADR = 0x92;
	HDMI_WriteI2C_Byte(0x92,0x72,0x12);
	HDMI_WriteI2C_Byte(0x92,0x73,0x04);//RGD_PTN_DE_DLY[7:0]
	HDMI_WriteI2C_Byte(0x92,0x74,0x01);//RGD_PTN_DE_DLY[11:8]  192
	HDMI_WriteI2C_Byte(0x92,0x75,0x19);//RGD_PTN_DE_TOP[6:0]  41
	HDMI_WriteI2C_Byte(0x92,0x76,0x00);//RGD_PTN_DE_CNT[7:0]
	HDMI_WriteI2C_Byte(0x92,0x77,0xd0);//RGD_PTN_DE_LIN[7:0]
	HDMI_WriteI2C_Byte(0x92,0x78,0x25);//RGD_PTN_DE_LIN[10:8],RGD_PTN_DE_CNT[11:8]
	HDMI_WriteI2C_Byte(0x92,0x79,0x72);//RGD_PTN_H_TOTAL[7:0]
	HDMI_WriteI2C_Byte(0x92,0x7a,0xee);//RGD_PTN_V_TOTAL[7:0]
	HDMI_WriteI2C_Byte(0x92,0x7b,0x26);//RGD_PTN_V_TOTAL[10:8],RGD_PTN_H_TOTAL[11:8]
	HDMI_WriteI2C_Byte(0x92,0x7c,0x28);//RGD_PTN_HWIDTH[7:0]
	HDMI_WriteI2C_Byte(0x92,0x7d,0x05);//RGD_PTN_HWIDTH[9:8],RGD_PTN_VWIDTH[5:0]
	
	
	HDMI_WriteI2C_Byte(0x92,0x70,0x80);
	HDMI_WriteI2C_Byte(0x92,0x71,0x76);
	
	// 74.25M CLK
	HDMI_WriteI2C_Byte(0x92,0x4e,0x99);
	HDMI_WriteI2C_Byte(0x92,0x4f,0x99);
	HDMI_WriteI2C_Byte(0x92,0x50,0x69);
	HDMI_WriteI2C_Byte(0x92,0x51,0x80);


}

 
void LT8912B_Standby(void)
{
	//I2CADR = 0x90;
	HDMI_WriteI2C_Byte(0x90,0x08,0x00);
	HDMI_WriteI2C_Byte(0x90,0x09,0x81);
	HDMI_WriteI2C_Byte(0x90,0x0a,0x00);
	HDMI_WriteI2C_Byte(0x90,0x0b,0x20);
	HDMI_WriteI2C_Byte(0x90,0x0c,0x00);

	HDMI_WriteI2C_Byte(0x90,0x54,0x1d);
	HDMI_WriteI2C_Byte(0x90,0x51,0x15);

	HDMI_WriteI2C_Byte(0x90,0x44,0x31);
	HDMI_WriteI2C_Byte(0x90,0x41,0xbd);
	HDMI_WriteI2C_Byte(0x90,0x5c,0x11);

	HDMI_WriteI2C_Byte(0x90,0x30,0x08);
	HDMI_WriteI2C_Byte(0x90,0x31,0x00);
	HDMI_WriteI2C_Byte(0x90,0x32,0x00);
	HDMI_WriteI2C_Byte(0x90,0x33,0x00);
	HDMI_WriteI2C_Byte(0x90,0x34,0x00);
	HDMI_WriteI2C_Byte(0x90,0x35,0x00);
	HDMI_WriteI2C_Byte(0x90,0x36,0x00);
	HDMI_WriteI2C_Byte(0x90,0x37,0x00);
	HDMI_WriteI2C_Byte(0x90,0x38,0x00);
}

/**************************** runyee zhou add lt8912b end***************************/


/**************************get hdmi dts pams***************************/
void lt8912b_get_dts(struct platform_device *pdev)
{
    // dts read
    int ret=0;
    pdev->dev.of_node = of_find_compatible_node(NULL, NULL, "mediatek,lt8912b-hdmi");

	  lt8912b_pinctrl = devm_pinctrl_get(&pdev->dev);
	  
	rst_low_cfg = pinctrl_lookup_state(lt8912b_pinctrl, "rst_low_cfg");
	if (IS_ERR(rst_low_cfg)) {
		ret = PTR_ERR(rst_low_cfg);
		pr_debug("%s : pinctrl err, lt8912b rst_low_cfg \n", __func__);
	}	                 
                 
	rst_high_cfg = pinctrl_lookup_state(lt8912b_pinctrl, "rst_high_cfg");
	if (IS_ERR(rst_high_cfg)) {
		ret = PTR_ERR(rst_high_cfg);
		pr_debug("%s : pinctrl err, lt8912b rst_high_cfg \n", __func__);
	}	  

}
/*************  [I2C lt8912b_driver_probe] *********************/

static int lt8912b_i2c_probe(struct i2c_client *client, const struct i2c_device_id *id)
{
	int err = 0;

	printk( "[lt8912b_driver_probe]\n");

	hdmi_i2c_client = kmalloc(sizeof(struct i2c_client), GFP_KERNEL);

	if (!hdmi_i2c_client) {
		err = -1;
		goto exit;
	}
	memset(hdmi_i2c_client, 0, sizeof(struct i2c_client));

	hdmi_i2c_client = client;

	/* --------------------- */

#if defined(LT8912B_NORMAL_MODE)
	LT8912B_Initial();
#else
	#if defined(LT8912B_SELF_TEST_MODE_1920X1080)
		LT8912B_test_1920x1080_Initial();
	#elif defined(LT8912B_SELF_TEST_MODE_1366X768)

	#elif defined(LT8912B_SELF_TEST_MODE_1280X720)
		LT8912B_test_1280x720_Initial();
	#elif defined(LT8912B_SELF_TEST_MODE_720X1280)
		LT8912B_test_720x1280_Initial();
	#else
		LT8912B_Initial();
	#endif
#endif
  
	return 0;

exit:
	return err;

}
static int lt8912b_i2c_remove(struct i2c_client *client)
{
	return 0;
}

/*************  [platform lt8912b_driver_probe] *********************/
static int platform_lt8912b_probe(struct platform_device *pdev)
{
    printk(" lt8912b_probe in!\n ");	
     
	  lt8912b_get_dts(pdev);
	  return i2c_add_driver(&lt8912b_i2c_driver);
}

static int platform_lt8912b_remove(struct platform_device *pdev)
{
	  i2c_del_driver(&lt8912b_i2c_driver);
	  return 0;
}

static int platform_lt8912b_suspend(struct platform_device *pdev, pm_message_t mesg)
{
    LT8912B_Standby(); 
    mdelay(100);
    pinctrl_select_state(lt8912b_pinctrl, rst_low_cfg);
	  return 0;
}

static int platform_lt8912b_resume(struct platform_device *pdev)
{
#if defined(LT8912B_SELF_TEST_MODE_1920X1080)
	LT8912B_test_1920x1080_Initial();
#elif defined(LT8912B_SELF_TEST_MODE_1366X768)
	
#elif defined(LT8912B_SELF_TEST_MODE_1280X720)
	LT8912B_test_1280x720_Initial();
#elif defined(LT8912B_SELF_TEST_MODE_720X1280)
	LT8912B_test_720x1280_Initial();
#else
	LT8912B_Initial();
#endif

	  return 0;
}

/* test interface */
struct class *lt8912b_class;
struct device *lt8912b_dev;

static ssize_t lt8912b_streg_show(struct device *dev, struct device_attribute *attr, char *buf)
{
	int i;
	int len = 0;
	unsigned char read_data[5]={0};
	int reg[]={0x9c,0x9d,0x9e,0x9f};

	HDMI_ReadI2C_Byte(0x90,0x9c,read_data);
	HDMI_ReadI2C_Byte(0x90,0x9d,read_data+1);
	HDMI_ReadI2C_Byte(0x90,0x9e,read_data+2);
	HDMI_ReadI2C_Byte(0x90,0x9f,read_data+3);

	printk("[IIC_ADDR = 0x90] reg_9c=%x,reg_9d=%x,reg_9e=%x,reg_9f=%x \n",read_data[0],read_data[1],read_data[2],read_data[3]);

	#if 0
	for(i=0;i<4;i++)
	{
		len += snprintf(buf+len, PAGE_SIZE-len, "reg:0x%04X value: 0x%04X\n", reg[i],read_data[i]);	
	}	
	#else
	for(i=0;i<4;i++)
	{
		len += sprintf(buf + len, "reg:0x%04X value: 0x%04X\n", reg[i],read_data[i]);
	}
	#endif
	
	return  len;
}

static ssize_t lt8912b_streg_store(struct device *dev,struct device_attribute *attr, const char *buf, size_t size)
{
    int enable = 0;
	
    if(buf != NULL && size != 0)
    {
        enable = (int)simple_strtoul(buf, NULL, 0);
    }
    if (enable)
    {
		///
    }
    else
    {
        ///
    }
    return size;
}
static DEVICE_ATTR(lt8912b_enable, 0644, lt8912b_streg_show, lt8912b_streg_store);


/* platform structure */
static struct platform_driver g_stlt8912b_Driver = {
	.probe = platform_lt8912b_probe,
	.remove = platform_lt8912b_remove,
	.suspend = platform_lt8912b_suspend,
	.resume = platform_lt8912b_resume,
	.driver = {
		   .name = PLATFORM_DRIVER_NAME,
		   .owner = THIS_MODULE,
		   }
};

static struct platform_device g_stlt8912b_device = {
	.name = PLATFORM_DRIVER_NAME,
	.id = 0,
	.dev = {}
};

static int __init platform_lt8912b_i2C_init(void)
{
	lt8912b_class = class_create(THIS_MODULE, "lt8912b");
	lt8912b_dev = device_create(lt8912b_class,NULL, 0, NULL,  "lt8912b");
    device_create_file(lt8912b_dev, &dev_attr_lt8912b_enable);	

	if (platform_device_register(&g_stlt8912b_device)) {
		printk("failed to register hdmi lt8912b device\n");
		return -1;
	}

	if (platform_driver_register(&g_stlt8912b_Driver)) {
		printk("failed to register hdmi lt8912b driver\n");
		return -1;
	}

	return 0;
}

static void __exit platform_lt8912b_i2C_exit(void)
{
	platform_driver_unregister(&g_stlt8912b_Driver);
}
module_init(platform_lt8912b_i2C_init);
module_exit(platform_lt8912b_i2C_exit);

MODULE_LICENSE("GPL");
MODULE_DESCRIPTION("I2C lt8912b Driver");
MODULE_AUTHOR("zhou cheng<cheng.zhou@runyee.com.cn>");
