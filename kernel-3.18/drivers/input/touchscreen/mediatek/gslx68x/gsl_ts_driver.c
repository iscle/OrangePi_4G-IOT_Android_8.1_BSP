/******************************************************************************

  Copyright (C), 2010-2012, Silead, Inc.

 ******************************************************************************
Filename      : gsl1680-d0.c
Version       : R2.0
Aurthor       : mark_huang
Creattime     : 2012.6.20
Description   : Driver for Silead I2C touchscreen.

 ******************************************************************************/
#define CONFIG_OF_TOUCH
#include "tpd.h"
#include <linux/interrupt.h>
#ifdef CONFIG_OF_TOUCH
#include <linux/of_irq.h>
#else
#include <cust_eint.h>
#endif
#include <linux/i2c.h>
#include <linux/sched.h>
#include <linux/kthread.h>
#include <linux/rtpm_prio.h>
#include <linux/wait.h>
#include <linux/delay.h>
#include <linux/time.h>
#ifdef CONFIG_OF_TOUCH
#include <linux/seq_file.h>
#endif

#ifdef CONFIG_OF_TOUCH
#include "mt_boot_common.h"
#include "upmu_common.h"
#else
#include <mach/mt_pm_ldo.h>
#include <mach/mt_typedefs.h>
#include <mach/mt_boot.h>
#include <mach/upmu_common.h>
#include <mach/upmu_common_sw.h>
#include <mach/upmu_sw.h>
#include <mach/upmu_hw.h>
#endif

#ifdef CONFIG_MTK_I2C_EXTENSION
#define GTP_SUPPORT_I2C_DMA
#endif

#ifdef GTP_SUPPORT_I2C_DMA
#include <linux/dma-mapping.h>
#endif

#ifdef GTP_SUPPORT_I2C_DMA
	u8 *g_dma_buff_va = NULL;
	dma_addr_t g_dma_buff_pa = 0;
#endif

#ifndef CONFIG_OF_TOUCH
#include "cust_gpio_usage.h"
#endif
#include "gsl_ts_driver.h" 
//#define GSL_GESTURE
extern struct tpd_device *tpd;
//extern int gsl_obtain_gesture();

#define KEYCODE_CTPC	 100 //->58 ALT_RIGHT
#define KEYCODE_CTPE	 29 //->113 CTRL_LEFT
#define KEYCODE_CTPS	 97 //->114 CTRL_RIGHT
#define KEYCODE_CTPZ	 56  //->57 ALT_LEFT
#define KEYCODE_CTPW	 104 //->92 KEY_PAGE_UP
#define KEYCODE_CTPM	 105 //->21 KEY_DPAD_LEFT
#define KEYCODE_CTPO	 106 //->22 KEY_DPAD_RIGHT
#define KEYCODE_CTPUP    103 //->19 KEY_DPAD_UP
#define KEYCODE_CTPDOWM  108 //->20 KEY_DPAD_DOWN
#define KEYCODE_CTPLEFT  109 //->93  KEY_PAGEDOWN
#define KEYCODE_CTPRIGHT 110 //->124 INSERT

static struct gsl_ts_data *ddata = NULL;

//static int boot_mode = NORMAL_BOOT;
static int touch_irq=0;//add by crystal
#define GSL_DEV_NAME "gsl1680"

#define I2C_TRANS_SPEED 400	//100 khz or 400 khz
#define TPD_REG_BASE 0x00

#define MAX_CONTACTS	10	//add by sky[2015.11.30]

//static struct tpd_bootloader_data_t g_bootloader_data;
static const struct i2c_device_id gsl_device_id[] = {{"gsl_tp",0},{}};
static unsigned short force[] = {0,0x80,I2C_CLIENT_END,I2C_CLIENT_END};
static const unsigned short * const forces[] = { force, NULL };
//static struct i2c_board_info __initdata i2c_tpd = { I2C_BOARD_INFO("gsl_tp", (0x80>>1))};

static volatile int gsl_halt_flag = 0;
static struct mutex gsl_i2c_lock;

static const struct of_device_id tpd_of_match[] = {
	{.compatible = "mediatek,cap_touch"},
	{},
};


#ifdef GSL_GESTURE
typedef enum{
	GE_DISABLE = 0,
	GE_ENABLE = 1,
	GE_WAKEUP = 2,
	GE_NOWORK =3,
}GE_T;
static GE_T gsl_gesture_status = GE_DISABLE;
static volatile unsigned int gsl_gesture_flag = 1;
static char gsl_gesture_c = 0;
extern int gsl_obtain_gesture(void);
extern void gsl_FunIICRead(unsigned int (*fun) (unsigned int *,unsigned int,unsigned int));
extern void gsl_GestureExternInt(unsigned int *model,int len);
unsigned int gsl_model_extern[]={
	0x10,0x56,
	0x08000f00,0x0f0c2f1f,0x12125040,0x13127060,0x16149181,0x1a18b1a1,0x1c1bd2c2,0x201df2e2,
	0x3324f7fe,0x4f41e7ef,0x6d5ed8df,0x8a7bc8d0,0xa698b7c0,0xc3b4a6af,0xe0d2959d,0xffee848d,
	0x10,0x57,
	0x00062610,0x03015c41,0x06049277,0x0f09c8ad,0x2918f7e0,0x5142e4fb,0x685eb2cb,0x77707c97,
	0x857d9177,0x978dc5ab,0xb4a1f3de,0xdbcbd5ec,0xebe4a2bd,0xf4f06c87,0xfaf73651,0xfffd001b,
	
	0x10,0x49,
	0x0e00f4ff,0x2f1ee4ec,0x4f3ed4dc,0x6f5fc4cc,0x8f7fb3bc,0xae9ea3ab,0xcebe949b,0xf0df858d,
	0xf0ff707a,0xcfdf6268,0xadbe525a,0x8d9e434a,0x6d7d353c,0x4c5c262e,0x2c3c151e,0x0c3c000b,
	0x10,0x49,
	0x775df6f9,0xab91e6ef,0xdac3cedb,0xf9eda2b9,0xfdff6d88,0xf1f93a53,0xcce21424,0x9ab50209,
	0x65800101,0x354d1409,0x0f1f3a25,0x01056e53,0x0100a288,0x1407d3bc,0x4128f1e4,0x765bfffb,	

	0x10,0x1041,
	0xfdff859f,0xe0f2566c,0xbdcf2d41,0x90a90f1d,0x5a750106,0x253f0400,0x020d2b11,0x10015c46,
	0x3823806e,0x664e9f91,0x9a80b2ab,0xd0b5bab8,0xaac5bbbc,0x7590bebb,0x445bd3c4,0x244fffe5,
	0x10,0x1042,
	0xe5ff795e,0xb0cb4f54,0x7c96444a,0x4e63293a,0x223a0917,0x39271601,0x5c4a402c,0x7a6c6e55,
	0x837fa388,0x8084d7bd,0x5871fdef,0x293febfd,0x1019bcd5,0x040987a1,0x0101526d,0x11271e38,
	0x10,0x1044,
	0x86867995,0x8386415d,0x687a1026,0x324e0003,0x03151d07,0x04005539,0x240e836f,0x553ca293,
	0x8c70b5ac,0xc4a8bebb,0xfce0bfbf,0xcae6bfbf,0x91adbfbf,0x5975bdbe,0x253dccbf,0x0534ffe4,
	0x10,0x1041,
	0x0007775b,0x1004ae93,0x3520dac6,0x6b50f4e7,0xa487fdfe,0xdec1f3f9,0xfdf8c7e3,0xd7ee9dae,
	0xa4be818f,0x6f8a6672,0x3552595d,0x33185556,0x6d505555,0xa78a5053,0xddc43a48,0xdd05001d,
	0x10,0x1042,
	0x1a00d1d1,0x4f34d6d3,0x8369dfda,0xb89eebe4,0xecd2fef4,0xd5edecf9,0xb0bec6de,0x99a597af,
	0x8a8f637d,0x87882e49,0xa28c0214,0xd4bc0c01,0xece53b22,0xfcf56f55,0xfffea489,0xf91ed9be,
	0x10,0x1044,
	0x93958166,0x9a94b79c,0xb5a5e7d1,0xe6cbfff8,0xfefbd2ed,0xeef79eb8,0xcce07386,0x9fb75562,
	0x6b854049,0x35503539,0x02193331,0x381d3535,0x6f543736,0xa58a3938,0xdbc13239,0xf409001b,
	0x10,0x1045,
	0x6d6d1e00,0x6d6d5b3d,0x6e6d987a,0x7e73d4b7,0xab8ffef0,0xe4cae9fa,0xfcf3afcd,0xfdff7290,
	0xe2f03a55,0xb5ce0f21,0x79970104,0x3d5a0900,0x1324361c,0x02077153,0x0200ae90,0x1c2be9cc,
	0x10,0x1045,
	0x9999d9f8,0x97989ab9,0x90965b7a,0x7f8a1c3b,0x48680006,0x0d291704,0x00045535,0x07019375,
	0x2112ceb2,0x5638f5e6,0x9575fffc,0xd1b4f0fc,0xf5e7bbd9,0xfffc7c9b,0xf3fc3e5c,0xd407021f,

	0x10,0x47,        
	0xc9e00105,0x9ab11008,0x6f832c1c,0x525d533c,0x765f6c68,0xa68e5b64,0xd2bc4552,0xf8e62435,                  
	0xf2fa462e,0xdfea745d,0xc4d29e89,0xa4b5c5b3,0x8093e9d9,0x526bfef7,0x2139f2fc,0x002fcae0,	
	0x10,0x36,        
	0x18009fa5,0x4930969a,0x79619193,0xaa929190,0xdbc39893,0xfdf0baa3,0xeafae5d1,0xbfd6fbf4,        
	0x8ea6ffff,0x6477eaf9,0x5356bcd5,0x5f588ea5,0x756a6176,0x94843a4c,0xb9a61829,0xe5ed000a,  

	
	0x0a,0x36,
	0xebff0002,0xc1d60100,0x98ac0c06,0x75862116,0x53633b2e,0x35435949,0x1f297c6a,0x0c15a38f,
	0x0004ccb7,0x0e03f2e0,0x3621fffd,0x5b4aecf9,0x776acbdc,0x797ba1b6,0x566b8a90,0x2c61928b,
	0x0a,0x36,
	0x8f901700,0x8186452e,0x737b725c,0x536a9287,0x273da59c,0x0414c5b3,0x1905e9db,0x452efbf4,
	0x735cfffd,0xa28afaff,0xceb8ecf4,0xf7e4d5e2,0xf9ffa7be,0xcce3979b,0x9eb59495,0x6fa79593,
	0x0a,0x36,
	0xebff0900,0xc1d61e14,0x99ad372b,0x73875044,0x4c5f695c,0x2c398977,0x121eaf9b,0x0005d9c3,
	0x1d08f8ed,0x4b34fffd,0x7762f5fb,0xa38ee7ee,0xceb9d5de,0xdfe2b6ca,0xb3caa6aa,0x85bca5a5,	

	0x10,0x4f,
	0x5d76fefd,0x2d45e5f4,0x0b1abdd4,0x01058aa3,0x02005670,0x1b0b293d,0x4a320f19,0x7d630005,
	0xae960f05,0xd7c5311e,0xf4e85e46,0xfefc9278,0xf6fec4ac,0xcee4e8d7,0x9cb6fbf6,0x6882fefe,
	0x10,0x4f,
	0x795ffffc,0xaf94fdff,0xddc7dff0,0xf8eeafc9,0xfffd7a94,0xf8fe455f,0xd3e81c2f,0xa1bb0610,
	0x6b860600,0x3b521e0f,0x16264731,0x020a7a60,0x0000b095,0x1706e0cb,0x492efaf0,0x7f84fefe,

	0x10,0x00,
	0xf4ff0d00,0xe6ee2f1f,0xd8df5240,0xc8d17262,0xbac29483,0xacb3b6a4,0x9ea4d8c7,0x8f99f9ea,
	0x7883e7f6,0x666fc7d6,0x595fa4b5,0x49518495,0x383f6474,0x27304254,0x161e2132,0x012d0411,
	
	
};
#endif


#ifdef TPD_PROXIMITY
#if 0
#include <linux/hwmsensor.h>
#include <linux/hwmsen_dev.h>
#include <linux/sensors_io.h>
#else
#include <hwmsensor.h>
#include <hwmsen_dev.h>
#include <sensors_io.h>
#endif
#include <linux/wakelock.h>
static u8 tpd_proximity_flag = 0; //flag whether start alps
static u8 tpd_proximity_detect = 1;//0-->close ; 1--> far away
static struct wake_lock ps_lock;
static u8 gsl_psensor_data[8]={0};
#endif

static int gsl_ps_enable = 0;
#ifdef GSL_TIMER
#define GSL_TIMER_CHECK_CIRCLE        200
static struct delayed_work gsl_timer_check_work;
static struct workqueue_struct *gsl_timer_workqueue = NULL;
static char int_1st[4];
static char int_2nd[4];
static int i2c_lock_flag = 0;
#endif

#ifdef TPD_PROC_DEBUG
#include <linux/proc_fs.h>
#include <asm/uaccess.h>
#include <linux/seq_file.h>
//static struct proc_dir_entry *gsl_config_proc = NULL;
#define GSL_CONFIG_PROC_FILE "gsl_config"
#define CONFIG_LEN 31
static char gsl_read[CONFIG_LEN];
static u8 gsl_data_proc[8] = {0};
static u8 gsl_proc_flag = 0;
#endif
//static u8 int_type = 0;


static DECLARE_WAIT_QUEUE_HEAD(waiter);
static struct task_struct *thread = NULL;
static int tpd_flag = 0;


#ifdef GSL_DEBUG 
#define print_info(fmt, args...)   \
		do{                              \
		    printk("[tp-gsl][%s]"fmt,__func__, ##args);     \
		}while(0)
#else
#define print_info(fmt, args...)
#endif

#ifdef TPD_HAVE_BUTTON
extern void tpd_button(unsigned int x, unsigned int y, unsigned int down);
//static int tpd_keys_local[TPD_KEY_COUNT] = TPD_KEYS;
//static int tpd_keys_dim_local[TPD_KEY_COUNT][4] = TPD_KEYS_DIM;
#endif

#ifdef GTP_SUPPORT_I2C_DMA
	static void msg_dma_alloct(void)
	{
	    if (NULL == g_dma_buff_va)
    		{
       		 tpd->dev->dev.coherent_dma_mask = DMA_BIT_MASK(32);
       		 g_dma_buff_va = (u8 *)dma_alloc_coherent(&tpd->dev->dev, 128, &g_dma_buff_pa, GFP_KERNEL);
    		}

	    	if(!g_dma_buff_va)
		{
	        	print_info("[DMA][Error] Allocate DMA I2C Buffer failed!\n");
	    	}
	}
	static void msg_dma_release(void){
		if(g_dma_buff_va)
		{
	     		dma_free_coherent(NULL, 128, g_dma_buff_va, g_dma_buff_pa);
	        	g_dma_buff_va = NULL;
	        	g_dma_buff_pa = 0;
			print_info("[DMA][release] Allocate DMA I2C Buffer release!\n");
	    	}
	}
#endif


#ifdef GTP_SUPPORT_I2C_DMA
static int gsl_read_interface(struct i2c_client *client,
        u8 reg, u8 *buf, u32 num)
{
	int err = 0;
	int i;
	u8 temp[1];
	temp[0]= reg;
	mutex_lock(&gsl_i2c_lock);

	if((NULL!=client) && (num>0) && (num<=128))
	{
		client->addr = (client->addr & I2C_MASK_FLAG) | I2C_DMA_FLAG;
	if(temp[0] < 0x80)
	{
		temp[0] = (temp[0]+8)&0x5c;
		memcpy(g_dma_buff_va, temp, 1);

		i2c_master_send(client,(unsigned char *)g_dma_buff_pa,1);	
		err = i2c_master_recv(client,(unsigned char *)g_dma_buff_pa,4);

		temp[0] = reg;
		memcpy(g_dma_buff_va, temp, 1);
		i2c_master_send(client,(unsigned char *)g_dma_buff_pa,1);
		err = i2c_master_recv(client,(unsigned char *)g_dma_buff_pa,4);
	}
	for(i=0;i<num;i++)
	{	
		temp[0] = reg + i;
		memcpy(g_dma_buff_va, temp, 1);

		i2c_master_send(client,(unsigned char *)g_dma_buff_pa,1);
		if((i+8)<num)
			err = i2c_master_recv(client,((unsigned char *)g_dma_buff_pa+i),8);
		else
			err = i2c_master_recv(client,((unsigned char *)g_dma_buff_pa+i),(num-i));
		i+=8;
	}
	}

	memcpy(buf, g_dma_buff_va, num);
	mutex_unlock(&gsl_i2c_lock);

	return err;
}
#else
static int gsl_read_interface(struct i2c_client *client,
        u8 reg, u8 *buf, u32 num)
{
	int err = 0;
	int i;
	u8 temp = reg;
	mutex_lock(&gsl_i2c_lock);
	if(temp < 0x80)
	{
		temp = (temp+8)&0x5c;
			i2c_master_send(client,&temp,1);	
			err = i2c_master_recv(client,&buf[0],4);
		temp = reg;
		i2c_master_send(client,&temp,1);
		err = i2c_master_recv(client,&buf[0],4);
	}
	for(i=0;i<num;i++)
	{	
		temp = reg + i;
		i2c_master_send(client,&temp,1);
		if((i+8)<num)
			err = i2c_master_recv(client,(buf+i),8);
		else
			err = i2c_master_recv(client,(buf+i),(num-i));
		i+=8;
	}
	mutex_unlock(&gsl_i2c_lock);

	return err;
}

#endif

#ifdef GTP_SUPPORT_I2C_DMA
s32 gsl_write_interface(struct i2c_client *client, u8 reg, u8 *buf, u32 num)
{

	int i = 0;
	int ret = 0;
	s32 writelen;
	u8 *wr_buf = g_dma_buff_va;
	writelen=num+1;

	wr_buf[0] = reg;

	if (buf == NULL) {
		mutex_unlock(&gsl_i2c_lock);
		return -1;
	}
	memcpy(wr_buf + 1, buf, num);

	if (0){//(writelen <= 8) {
	    client->ext_flag = client->ext_flag & (~I2C_DMA_FLAG);
		return i2c_master_send(client, wr_buf, writelen);
	}
	else if( 1 &&(NULL != g_dma_buff_va))
	{
		for (i = 0; i < writelen; i++)
		{
			g_dma_buff_va[i] = wr_buf[i];
			//print_info("[DMA][gsl_write_interface] g_dma_buff_va[%d]=%x\n",i,g_dma_buff_va[i]);
		}

	    client->addr = (client->addr & I2C_MASK_FLAG )| I2C_DMA_FLAG;
	    ret = i2c_master_send(client, (unsigned char *)g_dma_buff_pa, writelen);
	    client->addr = client->addr & I2C_MASK_FLAG & ~I2C_DMA_FLAG;

	    return ret;
	}
	return 1;
}
#else

static int gsl_write_interface(struct i2c_client *client,
        const u8 reg, u8 *buf, u32 num)
{
	struct i2c_msg xfer_msg[1]= {{0}};//modify crystal
	int err;
	u8 tmp_buf[num + 1];

	tmp_buf[0] = reg;
	memcpy(tmp_buf + 1, buf, num);

	xfer_msg[0].addr = client->addr;
	xfer_msg[0].len = num + 1;
	xfer_msg[0].flags = 0;//client->flags & I2C_M_TEN;
	xfer_msg[0].buf = tmp_buf;
	xfer_msg[0].timing = 400;//I2C_TRANS_SPEED;
	mutex_lock(&gsl_i2c_lock);

	err = i2c_transfer(client->adapter, xfer_msg, 1);
	mutex_unlock(&gsl_i2c_lock);

	
	return err;
}
#endif


#if 0
static unsigned int gsl_read_oneframe_data(unsigned int *data,
				unsigned int addr,unsigned int len)
{
	u8 buf[4];
	int i;
	printk("tp-gsl-gesture %s\n",__func__);
	printk("gsl_read_oneframe_data:::addr=%x,len=%x\n",addr,len);
	for(i=0;i<len/2;i++){
		buf[0] = ((addr+i*8)/0x80)&0xff;
		buf[1] = (((addr+i*8)/0x80)>>8)&0xff;
		buf[2] = (((addr+i*8)/0x80)>>16)&0xff;
		buf[3] = (((addr+i*8)/0x80)>>24)&0xff;
		gsl_write_interface(ddata->client,0xf0,buf,4);
		gsl_read_interface(ddata->client,(addr+i*8)%0x80,(char *)&data[i*2],8);
	}
	if(len%2){
		buf[0] = ((addr+len*4 - 4)/0x80)&0xff;
		buf[1] = (((addr+len*4 - 4)/0x80)>>8)&0xff;
		buf[2] = (((addr+len*4 - 4)/0x80)>>16)&0xff;
		buf[3] = (((addr+len*4 - 4)/0x80)>>24)&0xff;
		gsl_write_interface(ddata->client,0xf0,buf,4);
		gsl_read_interface(ddata->client,(addr+len*4 - 4)%0x80,(char *)&data[len-1],4);
	}
	#if 1
	for(i=0;i<len;i++){
	printk("gsl_read_oneframe_data =%x\n",data[i]);	
	//printk("gsl_read_oneframe_data =%x\n",data[len-1]);
	}
	#endif
	
	return len;
}
#endif

#ifdef GSL_GPIO_IDT_TP
static int gsl_read_TotalAdr(struct i2c_client *client,u32 addr,u32 *data)
{
	u8 buf[4];
	int err;
	buf[3]=(u8)((addr/0x80)>>24);
	buf[2]=(u8)((addr/0x80)>>16);
	buf[1]=(u8)((addr/0x80)>>8);
	buf[0]=(u8)((addr/0x80));
	gsl_write_interface(client,0xf0,buf,4);
	err = gsl_read_interface(client,addr%0x80,buf,4);
	if(err > 0){
		*data = (buf[3]<<24)|(buf[2]<<16)|(buf[1]<<8)|buf[0];
	}
	return err;
}
static int gsl_write_TotalAdr(struct i2c_client *client,u32 addr,u32 *data)
{
	int err;
	u8 buf[4];
	u32 value = *data;
	buf[3]=(u8)((addr/0x80)>>24);
	buf[2]=(u8)((addr/0x80)>>16);
	buf[1]=(u8)((addr/0x80)>>8);
	buf[0]=(u8)((addr/0x80));
	gsl_write_interface(client,0xf0,buf,4);
	buf[3]=(u8)((value)>>24);
	buf[2]=(u8)((value)>>16);
	buf[1]=(u8)((value)>>8);
	buf[0]=(u8)((value));
	err = gsl_write_interface(client,addr%0x80,buf,4);
	return err;
}
static int gsl_gpio_idt_tp(struct gsl_ts_data *ts)
{
	int i;
	u32 value = 0;
	u32 ru,rd,tu,td;
	u8 rstate,tstate;
	value = 0x1;
	gsl_write_TotalAdr(ts->client,0xff000084,&value);
	for(i=0;i<3;i++){
		gsl_read_TotalAdr(ts->client,0xff020004,&value);
	}
	ru = value & 0x1;
	value = 0x00011112;
	gsl_write_TotalAdr(ts->client,0xff080058,&value);

	for(i=0;i<3;i++){
		gsl_read_TotalAdr(ts->client,0xff020004,&value);
	}
	tu = (value & (0x1 << 1))>>1;

	value = 0x2;
	gsl_write_TotalAdr(ts->client,0xff000084,&value);
	for(i=0;i<3;i++){
		gsl_read_TotalAdr(ts->client,0xff020004,&value);
	}
	rd = value & 0x1;
	value = 0x00011110;
	gsl_write_TotalAdr(ts->client,0xff080058,&value);

	for(i=0;i<3;i++){
		gsl_read_TotalAdr(ts->client,0xff020004,&value);
	}
	td = (value & (0x1 << 1))>>1;
	print_info("[tpd_gsl][%s] [ru,rd]=[%d,%d]\n",__func__,ru,rd);
	print_info("[tpd_gsl][%s] [tu,td]=[%d,%d]\n",__func__,tu,td);
	if(ru == 0 && rd == 0)
		rstate = 0;
	else if(ru == 1 && rd == 1)
		rstate = 1;
	else if(ru == 1 && rd == 0)
		rstate = 2;

	if(tu == 0 && td == 0)
		tstate = 0;
	else if(tu == 1 && td == 1)
		tstate = 1;
	else if(tu == 1 && td == 0)
		tstate = 2;
	if(rstate==1&&tstate==2){
		gsl_cfg_index = 0;
	}
	else if(rstate==2&&tstate==2){
		gsl_cfg_index = 1;
	}
	else if(rstate==0&&tstate==0){
		gsl_cfg_index = 2;
	}
	print_info("[tpd-gsl][%s] [rstate,status]=[%d,%d]\n",__func__,rstate,tstate);
	return 1;
}
#endif
static int gsl_test_i2c(struct i2c_client *client)
{
	int i,err;
	u8 buf[4]={0};
	for(i=0;i<5;i++)
	{
		err=gsl_read_interface(client,0xfc,buf,4);
		if(err>0)
		{
			printk("[tp-gsl] i2c read 0xfc = 0x%02x%02x%02x%02x\n",
				buf[3],buf[2],buf[1],buf[0]);
			break;
		}
	}
	return (err<0?-1:0);
}

static void gsl_io_control(struct i2c_client *client)
{
//	u8 buf[4] = {0};
//	int i;
#if GSL9XX_VDDIO_1800
	for(i=0;i<5;i++){
		buf[0] = 0;
		buf[1] = 0;
		buf[2] = 0xfe;
		buf[3] = 0x1;
		gsl_write_interface(client,0xf0,buf,4);
		buf[0] = 0x5;
		buf[1] = 0;
		buf[2] = 0;
		buf[3] = 0x80;
		gsl_write_interface(client,0x78,buf,4);
		msleep(5);
	}
	msleep(50);
#endif
}
static void gsl_start_core(struct i2c_client *client)
{
	u8 buf[4] = {0};

	//add by sayid  litianfeng 151125
	buf[3] = 0x01;
	buf[2] = 0xfe;
	buf[1] = 0x0e;
	buf[0] = 0x02;
	gsl_write_interface(client,0xf0,buf,4);
	buf[3] = 0x0;
	buf[2] = 0xa;
	buf[1] = 0x0;
	buf[0] = 0x70;
	gsl_write_interface(client,0x4,buf,4);
	//add by sayid  litianfeng 151125
	buf[0]=0x01;
	gsl_write_interface(client,0x88,buf,4);
	buf[0]=0;
	gsl_write_interface(client,0xe0,buf,4);
#ifdef GSL_ALG_ID
	gsl_DataInit(gsl_cfg_table[gsl_cfg_index].data_id);
#endif

}

static void gsl_reset_core(struct i2c_client *client)
{
	u8 buf[4] = {0x00};
	
	buf[0] = 0x88;
	gsl_write_interface(client,0xe0,buf,4);
	msleep(5);

	buf[0] = 0x04;
	gsl_write_interface(client,0xe4,buf,4);
	msleep(5);
	
	buf[0] = 0;
	gsl_write_interface(client,0xbc,buf,4);
	msleep(5);
	gsl_io_control(client);
}

static void gsl_clear_reg(struct i2c_client *client)
{
	u8 buf[4]={0};
	//clear reg
	buf[0]=0x88;
	gsl_write_interface(client,0xe0,buf,4);
	msleep(20);
	buf[0]=0x3;
	gsl_write_interface(client,0x80,buf,4);
	msleep(5);
	buf[0]=0x4;
	gsl_write_interface(client,0xe4,buf,4);
	msleep(5);
	buf[0]=0x0;
	gsl_write_interface(client,0xe0,buf,4);
	msleep(20);
	//clear reg
}

#if 0
#define DMA_TRANS_LEN 0x20
static void gsl_load_fw(struct i2c_client *client,const struct fw_data *GSL_DOWNLOAD_DATA,int data_len)
{
	u8 buf[DMA_TRANS_LEN*4] = {0};
	u8 send_flag = 1;
	u8 addr=0;
	u32 source_line = 0;
	u32 source_len = data_len;//ARRAY_SIZE(GSL_DOWNLOAD_DATA);

	print_info("=============gsl_load_fw start==============\n");

	for (source_line = 0; source_line < source_len; source_line++) 
	{
		/* init page trans, set the page val */
		if (0xf0 == GSL_DOWNLOAD_DATA[source_line].offset)
		{
			memcpy(buf,&GSL_DOWNLOAD_DATA[source_line].val,4);	
			gsl_write_interface(client, 0xf0, buf, 4);
			send_flag = 1;
		}
		else 
		{
			if (1 == send_flag % (DMA_TRANS_LEN < 0x20 ? DMA_TRANS_LEN : 0x20))
	    			addr = (u8)GSL_DOWNLOAD_DATA[source_line].offset;

			memcpy((buf+send_flag*4 -4),&GSL_DOWNLOAD_DATA[source_line].val,4);	

			if (0 == send_flag % (DMA_TRANS_LEN < 0x20 ? DMA_TRANS_LEN : 0x20)) 
			{
	    		gsl_write_interface(client, addr, buf, DMA_TRANS_LEN * 4);
				send_flag = 0;
			}

			send_flag++;
		}
	}

	print_info("=============gsl_load_fw end==============\n");

}
#else 
static void gsl_load_fw(struct i2c_client *client,const struct fw_data *GSL_DOWNLOAD_DATA,int data_len)
{
	u8 buf[4] = {0};
	//u8 send_flag = 1;
	u8 addr=0;
	u32 source_line = 0;
	u32 source_len = data_len;//ARRAY_SIZE(GSL_DOWNLOAD_DATA);

	print_info("=============gsl_load_fw start==============\n");

	for (source_line = 0; source_line < source_len; source_line++) 
	{
		/* init page trans, set the page val */
    	addr = (u8)GSL_DOWNLOAD_DATA[source_line].offset;
		memcpy(buf,&GSL_DOWNLOAD_DATA[source_line].val,4);
    	gsl_write_interface(client, addr, buf, 4);	
	}
}
#endif

static void gsl_sw_init(struct i2c_client *client)
{
	int temp;
	char write_buf[4] ;
	char read_buf[4] = {0};
	static volatile int gsl_sw_flag=0;
	if(1==gsl_sw_flag)
		return;
	gsl_sw_flag=1;
#if 0 //modify crystal
	mt_set_gpio_out(GPIO_CTP_RST_PIN, GPIO_OUT_ZERO);
	msleep(20);
	mt_set_gpio_out(GPIO_CTP_RST_PIN, GPIO_OUT_ONE);
	msleep(20);
#else

	tpd_gpio_output(GTP_RST_PORT, 0);	
	msleep(20);
	tpd_gpio_output(GTP_RST_PORT, 1);
	msleep(20);
#endif
	temp = gsl_test_i2c(client);
	if(temp<0){
		gsl_sw_flag = 0;
		return;
	}

	gsl_clear_reg(client);
	gsl_reset_core(client);

	gsl_io_control(client);
	gsl_load_fw(client,gsl_cfg_table[gsl_cfg_index].fw,gsl_cfg_table[gsl_cfg_index].fw_size);
	gsl_io_control(client);

	gsl_start_core(client);
	gsl_sw_flag=0;
	
	write_buf[3] = 0;
	write_buf[2] = 0;
	write_buf[1] = 0;
	write_buf[0] = 0xb6;
	gsl_write_interface(client, 0xf0, write_buf, 4);
	gsl_read_interface(client, 0x7c, read_buf, 4);
	print_info("[gsl1680][%s] addr = (b6,7c); read_buf = %02x%02x%02x%02x\n",
		__func__, read_buf[3], read_buf[2], read_buf[1], read_buf[0]);

		
}

static void check_mem_data(struct i2c_client *client)
{
	char read_buf[4] = {0};
	gsl_read_interface(client, 0xb0, read_buf, 4);

	print_info("[gsl1680][%s] addr = 0xb0; read_buf = %02x%02x%02x%02x\n",
		__func__, read_buf[3], read_buf[2], read_buf[1], read_buf[0]);
	if (read_buf[3] != 0x5a || read_buf[2] != 0x5a || read_buf[1] != 0x5a || read_buf[0] != 0x5a)
	{
		#ifdef TPD_POWER_VTP28_USE_VCAMA  //add for esd
			{
			pmic_set_register_value(PMIC_RG_VCAMA_EN,0);
			msleep(10);
			pmic_set_register_value(PMIC_RG_VCAMA_EN,1);
		    pmic_set_register_value(PMIC_RG_VCAMA_VOSEL,0x03);
			}
		#else
			{
	    	pmic_set_register_value(PMIC_RG_VGP1_EN,0);
			msleep(10);
			pmic_set_register_value(PMIC_RG_VGP1_EN,1);
			pmic_set_register_value(PMIC_RG_VGP1_VOSEL,0x5);
			}
		#endif	    
		msleep(10);
		gsl_sw_init(client);
	}
}

#ifdef TPD_PROC_DEBUG
#define GSL_APPLICATION
#ifdef GSL_APPLICATION
static int gsl_read_MorePage(struct i2c_client *client,u32 addr,u8 *buf,u32 num)
{
	int i;
	u8 tmp_buf[4] = {0};
	u8 tmp_addr;
	for(i=0;i<num/8;i++){
		tmp_buf[0]=(char)((addr+i*8)/0x80);
		tmp_buf[1]=(char)(((addr+i*8)/0x80)>>8);
		tmp_buf[2]=(char)(((addr+i*8)/0x80)>>16);
		tmp_buf[3]=(char)(((addr+i*8)/0x80)>>24);
		gsl_write_interface(client,0xf0,tmp_buf,4);
		tmp_addr = (char)((addr+i*8)%0x80);
		gsl_read_interface(client,tmp_addr,(buf+i*8),8);
	}
	if(i*8<num){
		tmp_buf[0]=(char)((addr+i*8)/0x80);
		tmp_buf[1]=(char)(((addr+i*8)/0x80)>>8);
		tmp_buf[2]=(char)(((addr+i*8)/0x80)>>16);
		tmp_buf[3]=(char)(((addr+i*8)/0x80)>>24);
		gsl_write_interface(client,0xf0,tmp_buf,4);
		tmp_addr = (char)((addr+i*8)%0x80);
		gsl_read_interface(client,tmp_addr,(buf+i*8),4);
	}
	return 0;//modify crystal
}
#endif
static int char_to_int(char ch)
{
	if(ch>='0' && ch<='9')
		return (ch-'0');
	else
		return (ch-'a'+10);
}

//static int gsl_config_read_proc(char *page, char **start, off_t off, int count, int *eof, void *data)
static int gsl_config_read_proc(struct seq_file *m,void *v)
{
	char temp_data[5] = {0};
	//int i;
	unsigned int tmp=0;
	if('v'==gsl_read[0]&&'s'==gsl_read[1])
	{
#ifdef GSL_ALG_ID
		tmp=gsl_version_id();
#else 
		tmp=0x20121215;
#endif
		seq_printf(m,"version:%x\n",tmp);
	}
	else if('r'==gsl_read[0]&&'e'==gsl_read[1])
	{
		if('i'==gsl_read[3])
		{
#ifdef GSL_ALG_ID 
			tmp=(gsl_data_proc[5]<<8) | gsl_data_proc[4];
			seq_printf(m,"gsl_config_data_id[%d] = ",tmp);
			if(tmp>=0&&tmp<gsl_cfg_table[gsl_cfg_index].data_size)
				seq_printf(m,"%d\n",gsl_cfg_table[gsl_cfg_index].data_id[tmp]); 
#endif
		}
		else 
		{
			gsl_write_interface(ddata->client,0xf0,&gsl_data_proc[4],4);
			gsl_read_interface(ddata->client,gsl_data_proc[0],temp_data,4);
			seq_printf(m,"offset : {0x%02x,0x",gsl_data_proc[0]);
			seq_printf(m,"%02x",temp_data[3]);
			seq_printf(m,"%02x",temp_data[2]);
			seq_printf(m,"%02x",temp_data[1]);
			seq_printf(m,"%02x};\n",temp_data[0]);
		}
	}
#ifdef GSL_APPLICATION
	else if('a'==gsl_read[0]&&'p'==gsl_read[1]){
		char *buf;
		int temp1;
		tmp = (unsigned int)(((gsl_data_proc[2]<<8)|gsl_data_proc[1])&0xffff);
		buf=kzalloc(tmp,GFP_KERNEL);
		if(buf==NULL)
			return -1;
		if(3==gsl_data_proc[0]){
			gsl_read_interface(ddata->client,gsl_data_proc[3],buf,tmp);
			if(tmp < m->size){
				memcpy(m->buf,buf,tmp);
			}
		}else if(4==gsl_data_proc[0]){
			temp1=((gsl_data_proc[6]<<24)|(gsl_data_proc[5]<<16)|
				(gsl_data_proc[4]<<8)|gsl_data_proc[3]);
			gsl_read_MorePage(ddata->client,temp1,buf,tmp);
			if(tmp < m->size){
				memcpy(m->buf,buf,tmp);
			}
		}
		kfree(buf);
	}
#endif
	return 0;
}
//static int gsl_config_write_proc(struct file *file, const char *buffer, unsigned long count, void *data)
static ssize_t  gsl_config_write_proc(struct file *file, const char __user  *buffer, size_t  count, loff_t *data)
{
	u8 buf[8] = {0};
	char temp_buf[CONFIG_LEN];
	char *path_buf;
	int tmp = 0;
	int tmp1 = 0;
	print_info("[tp-gsl][%s] \n",__func__);
	if(count > 512)
	{
//		print_info("size not match [%d:%ld]\n", CONFIG_LEN, count);
        	return -EFAULT;
	}
	path_buf=kzalloc(count,GFP_KERNEL);
	if(!path_buf)
	{
		printk("alloc path_buf memory error \n");
		return -1;
	}	
	if(copy_from_user(path_buf, buffer, count))
	{
		print_info("copy from user fail\n");
		goto exit_write_proc_out;
	}
	memcpy(temp_buf,path_buf,(count<CONFIG_LEN?count:CONFIG_LEN));
	print_info("[tp-gsl][%s][%s]\n",__func__,temp_buf);
#ifdef GSL_APPLICATION
	if('a'!=temp_buf[0]||'p'!=temp_buf[1]){
#endif
	buf[3]=char_to_int(temp_buf[14])<<4 | char_to_int(temp_buf[15]);	
	buf[2]=char_to_int(temp_buf[16])<<4 | char_to_int(temp_buf[17]);
	buf[1]=char_to_int(temp_buf[18])<<4 | char_to_int(temp_buf[19]);
	buf[0]=char_to_int(temp_buf[20])<<4 | char_to_int(temp_buf[21]);
	
	buf[7]=char_to_int(temp_buf[5])<<4 | char_to_int(temp_buf[6]);
	buf[6]=char_to_int(temp_buf[7])<<4 | char_to_int(temp_buf[8]);
	buf[5]=char_to_int(temp_buf[9])<<4 | char_to_int(temp_buf[10]);
	buf[4]=char_to_int(temp_buf[11])<<4 | char_to_int(temp_buf[12]);
#ifdef GSL_APPLICATION
	}
#endif
	if('v'==temp_buf[0]&& 's'==temp_buf[1])//version //vs
	{
		memcpy(gsl_read,temp_buf,4);
		printk("gsl version\n");
	}
	else if('s'==temp_buf[0]&& 't'==temp_buf[1])//start //st
	{
		gsl_proc_flag = 1;
		gsl_reset_core(ddata->client);
	}
	else if('e'==temp_buf[0]&&'n'==temp_buf[1])//end //en
	{
		msleep(20);
		gsl_reset_core(ddata->client);
		gsl_start_core(ddata->client);
		gsl_proc_flag = 0;
	}
	else if('r'==temp_buf[0]&&'e'==temp_buf[1])//read buf //
	{
		memcpy(gsl_read,temp_buf,4);
		memcpy(gsl_data_proc,buf,8);
	}
	else if('w'==temp_buf[0]&&'r'==temp_buf[1])//write buf
	{
		gsl_write_interface(ddata->client,buf[4],buf,4);
	}
	
#ifdef GSL_ALG_ID
	else if('i'==temp_buf[0]&&'d'==temp_buf[1])//write id config //
	{
		tmp1=(buf[7]<<24)|(buf[6]<<16)|(buf[5]<<8)|buf[4];
		tmp=(buf[3]<<24)|(buf[2]<<16)|(buf[1]<<8)|buf[0];
		if(tmp1>=0 && tmp1<gsl_cfg_table[gsl_cfg_index].data_size)
		{
			gsl_cfg_table[gsl_cfg_index].data_id[tmp1] = tmp;
		}
	}
#endif
#ifdef GSL_APPLICATION
	else if('a'==temp_buf[0]&&'p'==temp_buf[1]){
		if(1==path_buf[3]){
			tmp=((path_buf[5]<<8)|path_buf[4]);
			gsl_write_interface(ddata->client,path_buf[6],&path_buf[10],tmp);
		}else if(2==path_buf[3]){
			tmp = ((path_buf[5]<<8)|path_buf[4]);
			tmp1=((path_buf[9]<<24)|(path_buf[8]<<16)|(path_buf[7]<<8)
				|path_buf[6]);
			buf[0]=(char)((tmp1/0x80)&0xff);
			buf[1]=(char)(((tmp1/0x80)>>8)&0xff);
			buf[2]=(char)(((tmp1/0x80)>>16)&0xff);
			buf[3]=(char)(((tmp1/0x80)>>24)&0xff);
			buf[4]=(char)(tmp1%0x80);
			gsl_write_interface(ddata->client,0xf0,buf,4);
			gsl_write_interface(ddata->client,buf[4],&path_buf[10],tmp);
		}else if(3==path_buf[3]||4==path_buf[3]){
			memcpy(gsl_read,temp_buf,4);
			memcpy(gsl_data_proc,&path_buf[3],7);
		}
	}
#endif
exit_write_proc_out:
	kfree(path_buf);
	return count;
}

#endif

#ifdef GSL_TIMER
static void gsl_timer_check_func(struct work_struct *work)
{
	struct gsl_ts_data *ts = ddata;
	struct i2c_client *gsl_client = ts->client;
	//static int i2c_lock_flag = 0;
	char read_buf[4]  = {0};
	char init_chip_flag = 0;
	int i,flag;
	print_info("----------------gsl_monitor_worker-----------------\n");	
#if defined(MX2116_JC_C12)||defined(MX2116_JC_C11)||defined(MX2116_WPF_D5019)
	//if(i2c_lock_flag != 0)
		//return;
	//else
		//i2c_lock_flag = 1;
#else
	if(i2c_lock_flag != 0)
		return;
	else
		i2c_lock_flag = 1;
#endif
	gsl_read_interface(gsl_client, 0xb4, read_buf, 4);
	memcpy(int_2nd,int_1st,4);
	memcpy(int_1st,read_buf,4);

	if(int_1st[3] == int_2nd[3] && int_1st[2] == int_2nd[2] &&
		int_1st[1] == int_2nd[1] && int_1st[0] == int_2nd[0])
	{
		printk("======int_1st: %x %x %x %x , int_2nd: %x %x %x %x ======\n",
			int_1st[3], int_1st[2], int_1st[1], int_1st[0], 
			int_2nd[3], int_2nd[2],int_2nd[1],int_2nd[0]);
		memset(read_buf,0xff,sizeof(read_buf));

		msleep(20);
		gsl_read_interface(gsl_client,0xb4,read_buf,4);
		if(int_1st[0] == read_buf[0] && int_1st[1] == read_buf[1] &&
			int_1st[2] == read_buf[2] && int_1st[3] == read_buf[3]){
			init_chip_flag = 1;
			goto queue_monitor_work;
		}
	}
	/*check 0xb0 register,check firmware if ok*/
	for(i=0;i<5;i++){
		gsl_read_interface(gsl_client, 0xb0, read_buf, 4);
		if(read_buf[3] != 0x5a || read_buf[2] != 0x5a || 
			read_buf[1] != 0x5a || read_buf[0] != 0x5a){
			printk("gsl_monitor_worker 0xb0 = {0x%02x%02x%02x%02x};\n",
				read_buf[3],read_buf[2],read_buf[1],read_buf[0]);
			flag = 1;
		}else{
			flag = 0;
			break;
		}

	}
	if(flag == 1){
		init_chip_flag = 1;
		goto queue_monitor_work;
	}
	
	/*check 0xbc register,check dac if normal*/
	for(i=0;i<5;i++){
		gsl_read_interface(gsl_client, 0xbc, read_buf, 4);
		if(read_buf[3] != 0 || read_buf[2] != 0 || 
			read_buf[1] != 0 || read_buf[0] != 0){
			flag = 1;
		}else{
			flag = 0;
			break;
		}
	}
	if(flag == 1){
		//gsl_reset_core(gsl_client);
		//gsl_start_core(gsl_client);
		init_chip_flag = 1;
		goto queue_monitor_work;
	}
queue_monitor_work:
	if(init_chip_flag){

#ifdef TPD_POWER_VTP28_USE_VCAMA		//add for esd
		
		pmic_set_register_value(PMIC_RG_VCAMA_EN,0);
		msleep(10);
		pmic_set_register_value(PMIC_RG_VCAMA_EN,1);
	    pmic_set_register_value(PMIC_RG_VCAMA_VOSEL,0x03);

#else
    	pmic_set_register_value(PMIC_RG_VGP1_EN,0);
		msleep(10);
		pmic_set_register_value(PMIC_RG_VGP1_EN,1);
		pmic_set_register_value(PMIC_RG_VGP1_VOSEL,0x5);
#endif	    
		msleep(10);
		gsl_sw_init(gsl_client);
		memset(int_1st,0xff,sizeof(int_1st));
	}
	
	if(gsl_halt_flag==0){
		queue_delayed_work(gsl_timer_workqueue, &gsl_timer_check_work, 200);
	}
	i2c_lock_flag = 0;

}
#endif

#ifdef TPD_PROXIMITY

static int tpd_get_ps_value(void)
{
	return tpd_proximity_detect;
}
static int tpd_enable_ps(int enable)
{
	u8 buf[4];
	gsl_ps_enable = enable;
	printk("tpd_enable_ps:gsl_ps_enable = %d\n",gsl_ps_enable);
	if (enable) {
		wake_lock(&ps_lock);
		buf[3] = 0x00;
		buf[2] = 0x00;
		buf[1] = 0x00;
		buf[0] = 0x4;
		gsl_write_interface(ddata->client, 0xf0, buf, 4);
		buf[3] = 0x0;
		buf[2] = 0x0;
		buf[1] = 0x0;
		buf[0] = 0x2;
		gsl_write_interface(ddata->client, 0, buf, 4);
		
		tpd_proximity_flag = 1;
		//add alps of function
		printk("tpd-ps function is on\n");
	} else {
		tpd_proximity_flag = 0;
		wake_unlock(&ps_lock);
		buf[3] = 0x00;
		buf[2] = 0x00;
		buf[1] = 0x00;
		buf[0] = 0x4;
		gsl_write_interface(ddata->client, 0xf0, buf, 4);
		buf[3] = 0x00;
		buf[2] = 0x00;
		buf[1] = 0x00;
		buf[0] = 0x00;
		gsl_write_interface(ddata->client, 0, buf, 4);
		printk("tpd-ps function is off\n");
	}
	return 0;
}

static int tpd_ps_operate(void* self, uint32_t command, void* buff_in, int size_in,
        void* buff_out, int size_out, int* actualout)
{
	int err = 0;
	int value;
	hwm_sensor_data *sensor_data;

	switch (command)
	{
		case SENSOR_DELAY:
			if((buff_in == NULL) || (size_in < sizeof(int)))
			{
				printk("Set delay parameter error!\n");
				err = -EINVAL;
			}
			// Do nothing
			break;

		case SENSOR_ENABLE:
			if((buff_in == NULL) || (size_in < sizeof(int)))
			{
				printk("Enable sensor parameter error!\n");
				err = -EINVAL;
			}
			else
			{
				value = *(int *)buff_in;
				err = tpd_enable_ps(value);
			}
			break;

		case SENSOR_GET_DATA:
			if((buff_out == NULL) || (size_out< sizeof(hwm_sensor_data)))
			{
				printk("get sensor data parameter error!\n");
				err = -EINVAL;
			}
			else
			{
				sensor_data = (hwm_sensor_data *)buff_out;

				sensor_data->values[0] = tpd_get_ps_value();
				sensor_data->value_divide = 1;
				sensor_data->status = SENSOR_STATUS_ACCURACY_MEDIUM;
			}
			break;

		default:
			printk("proxmy sensor operate function no this parameter %d!\n", command);
			err = -1;
			break;
	}

	return err;

}
#endif
#define GSL_CHIP_NAME	"gslx68x"

#ifdef GSL_GESTURE
static void gsl_enter_doze(struct gsl_ts_data *ts)
{
	u8 buf[4] = {0};
#if 0
	u32 tmp;
	gsl_reset_core(ts->client);
	temp = ARRAY_SIZE(GSLX68X_FW_GESTURE);
	gsl_load_fw(ts->client,GSLX68X_FW_GESTURE,temp);
	gsl_start_core(ts->client);
	msleep(1000);		
#endif

	buf[0] = 0xa;
	buf[1] = 0;
	buf[2] = 0;
	buf[3] = 0;
	gsl_write_interface(ts->client,0xf0,buf,4);
	buf[0] = 0;
	buf[1] = 0;
	buf[2] = 0x1;
	buf[3] = 0x5a;
	gsl_write_interface(ts->client,0x8,buf,4);
	//gsl_gesture_status = GE_NOWORK;
	msleep(10);
	gsl_gesture_status = GE_ENABLE;

}
static void gsl_quit_doze(struct gsl_ts_data *ts)
{
	u8 buf[4] = {0};
	//u32 tmp;

	gsl_gesture_status = GE_DISABLE;
	#ifdef CONFIG_OF_TOUCH	
	tpd_gpio_output(GTP_RST_PORT, 0);	
	msleep(20);
	tpd_gpio_output(GTP_RST_PORT, 1);
	#else
	mt_set_gpio_out(GPIO_CTP_RST_PIN, GPIO_OUT_ZERO);
	msleep(20);
	mt_set_gpio_out(GPIO_CTP_RST_PIN, GPIO_OUT_ONE);
	msleep(5);
	#endif
	
	buf[0] = 0xa;
	buf[1] = 0;
	buf[2] = 0;
	buf[3] = 0;
	gsl_write_interface(ts->client,0xf0,buf,4);
	buf[0] = 0;
	buf[1] = 0;
	buf[2] = 0;
	buf[3] = 0x5a;
	gsl_write_interface(ts->client,0x8,buf,4);
	msleep(10);

#if 0
	gsl_reset_core(ddata->client);
	temp = ARRAY_SIZE(GSLX68X_FW_CONFIG);
	//gsl_load_fw();
	gsl_load_fw(ddata->client,GSLX68X_FW_CONFIG,temp);
	gsl_start_core(ddata->client);
#endif
}
#endif
/*
static ssize_t gsl_sysfs_tpgesture_show(struct device *dev,
		struct device_attribute *attr, char *buf)
{
	u32 count = 0;
	count += scnprintf(buf,PAGE_SIZE,"tp gesture is on/off:\n");
	if(gsl_gesture_flag == 1){
		count += scnprintf(buf+count,PAGE_SIZE-count,
				" on \n");
	}else if(gsl_gesture_flag == 0){
		count += scnprintf(buf+count,PAGE_SIZE-count,
				" off \n");
	}
	count += scnprintf(buf+count,PAGE_SIZE-count,"tp gesture:");
	count += scnprintf(buf+count,PAGE_SIZE-count,
			"%c\n",gsl_gesture_c);
    	return count;
}
static ssize_t gsl_sysfs_tpgesturet_store(struct device *dev,
		struct device_attribute *attr, const char *buf, size_t count)
{
#if 1
	if(buf[0] == '0'){
		gsl_gesture_flag = 0;  
	}else if(buf[0] == '1'){
		gsl_gesture_flag = 1;
	}
#endif
	return count;
}


static ssize_t gsl_sysfs_version_show(struct device *dev,struct device_attribute *attr, char *buf)
{
//	ssize_t len=0;
	u32 tmp;
	u8 buf_tmp[4];
	char *ptr = buf;
	ptr += sprintf(ptr,"sileadinc:");
	ptr += sprintf(ptr,GSL_CHIP_NAME);
#ifdef GSL_ALG_ID
	tmp = gsl_version_id();
	ptr += sprintf(ptr,":%08x:",tmp);
	ptr += sprintf(ptr,"%08x:",
		gsl_cfg_table[gsl_cfg_index].data_id[0]);
#endif
	buf_tmp[0]=0x3;buf_tmp[1]=0;buf_tmp[2]=0;buf_tmp[3]=0;
	gsl_write_interface(ddata->client,0xf0,buf_tmp,4);
	gsl_read_interface(ddata->client,0,buf_tmp,4);
	ptr += sprintf(ptr,"%02x%02x%02x%02x\n",buf_tmp[3],buf_tmp[2],buf_tmp[1],buf_tmp[0]);
		
    	return (ptr-buf);
}
static DEVICE_ATTR(version, 0444, gsl_sysfs_version_show, NULL);
static struct attribute *gsl_attrs[] = {
	&dev_attr_version.attr,
	NULL
};
static const struct attribute_group gsl_attr_group = {
	.attrs = gsl_attrs,
};



static unsigned int gsl_sysfs_init(void)
{
	int ret;
	struct kobject *gsl_debug_kobj;
	gsl_debug_kobj = kobject_create_and_add("gsl_touchscreen", NULL) ;
	if (gsl_debug_kobj == NULL)
	{
		printk("%s: subsystem_register failed\n", __func__);
		return -ENOMEM;
	}
	ret = sysfs_create_file(gsl_debug_kobj, &dev_attr_version.attr);
	if (ret)
	{
		printk("%s: sysfs_create_version_file failed\n", __func__);
		return ret;
	}
}
*/
static void gsl_report_point(struct gsl_touch_info *ti)
{
	int tmp = 0;
	static int gsl_up_flag = 0; //prevent more up event
	print_info("gsl_report_point %d \n", ti->finger_num);

	if (unlikely(ti->finger_num == 0)) 
	{
		if(gsl_up_flag == 0)
			return;
	    	gsl_up_flag = 0;
        	input_report_key(tpd->dev, BTN_TOUCH, 0);
        	input_mt_sync(tpd->dev);
		if (FACTORY_BOOT == get_boot_mode()|| 
			RECOVERY_BOOT == get_boot_mode())
		{   

			tpd_button(ti->x[tmp], ti->y[tmp], 0);

		}
	} 
	else 
	{
		gsl_up_flag = 1;
		for (tmp = 0; ti->finger_num > tmp; tmp++) 
		{
			print_info("[lifan_gsl1680](x[%d],y[%d]) = (%d,%d);\n", 
				ti->id[tmp], ti->id[tmp], ti->x[tmp], ti->y[tmp]);
			input_report_key(tpd->dev, BTN_TOUCH, 1);
			input_report_abs(tpd->dev, ABS_MT_TOUCH_MAJOR, 1);

			if (FACTORY_BOOT == get_boot_mode()|| RECOVERY_BOOT == get_boot_mode())
			{ 
				tpd_button(ti->x[tmp], ti->y[tmp], 1);  
			}
			input_report_abs(tpd->dev, ABS_MT_TRACKING_ID, ti->id[tmp] - 1);
			input_report_abs(tpd->dev, ABS_MT_POSITION_X, ti->x[tmp]);
			input_report_abs(tpd->dev, ABS_MT_POSITION_Y, ti->y[tmp]);

			input_mt_sync(tpd->dev);
		}
	}
	input_sync(tpd->dev);
}

static void gsl_report_work(void)
{

	u8 buf[4] = {0};
	//u8 i = 0;
	//u16 ret = 0;
	u16 tmp = 0;
	int tmp1 = 0; //modify
	struct gsl_touch_info cinfo={{0}};
	u8 tmp_buf[44] ={0};
//	static struct timeval time_now;
//	static struct timeval time_last={0,0};
	print_info("enter gsl_report_work\n");
	
#ifdef GSL_TIMER
    if(i2c_lock_flag != 0)
	    goto i2c_lock_schedule;
    else
	    i2c_lock_flag = 1;
#endif


#ifdef TPD_PROXIMITY
	int err;
	hwm_sensor_data sensor_data;
    /*added by bernard*/
	if (tpd_proximity_flag == 1)
	{
	
		gsl_read_interface(ddata->client,0xac,buf,4);
		if (buf[0] == 1 && buf[1] == 0 && buf[2] == 0 && buf[3] == 0)
		{
			tpd_proximity_detect = 0;
			//sensor_data.values[0] = 0;
		}
		else
		{
			tpd_proximity_detect = 1;
			//sensor_data.values[0] = 1;
		}
		//get raw data
		print_info(" ps change\n");
		//map and store data to hwm_sensor_data
		sensor_data.values[0] = tpd_get_ps_value();
		sensor_data.value_divide = 1;
		sensor_data.status = SENSOR_STATUS_ACCURACY_MEDIUM;
		//let up layer to know
		if((err = hwmsen_get_interrupt_data(ID_PROXIMITY, &sensor_data)))
		{
			print_info("call hwmsen_get_interrupt_data fail = %d\n", err);
		}
	}
	/*end of added*/
#endif

#ifdef TPD_PROC_DEBUG
	if(gsl_proc_flag == 1){
		return;
	}
#endif	

 	gsl_read_interface(ddata->client, 0x80, tmp_buf, 8);
	if(tmp_buf[0]>=2&&tmp_buf[0]<=10)
		gsl_read_interface(ddata->client, 0x88, &tmp_buf[8], (tmp_buf[0]*4-4));
	cinfo.finger_num = tmp_buf[0] & 0x0f;
	#ifdef GSL_GESTURE
		printk("GSL:::0x80=%02x%02x%02x%02x\n",tmp_buf[3],tmp_buf[2],tmp_buf[1],tmp_buf[0]);
		printk("GSL:::0x84=%02x%02x%02x%02x\n",tmp_buf[7],tmp_buf[6],tmp_buf[5],tmp_buf[4]);
		printk("GSL:::0x88=%02x%02x%02x%02x\n",tmp_buf[11],tmp_buf[10],tmp_buf[9],tmp_buf[8]);
//	if(GE_ENABLE == gsl_gesture_status && gsl_gesture_flag == 1){
//		for(tmp=0;tmp<3;tmp++){
//			printk("tp-gsl-gesture 0x%2x=0x%02x%02x%02x%02x;\n",
//					tmp*4+0x80,tmp_buf[tmp*4+3],tmp_buf[tmp*4+2],tmp_buf[tmp*4+1],
//					tmp_buf[tmp*4]);
//		}
//	}
	#endif
	print_info("tp-gsl  finger_num = %d\n",cinfo.finger_num);
	for(tmp=0;tmp<(cinfo.finger_num>10?10:cinfo.finger_num);tmp++)
	{
		cinfo.id[tmp] = tmp_buf[tmp*4+7] >> 4;
		cinfo.y[tmp] = (tmp_buf[tmp*4+4] | ((tmp_buf[tmp*4+5])<<8));
		cinfo.x[tmp] = (tmp_buf[tmp*4+6] | ((tmp_buf[tmp*4+7] & 0x0f)<<8));
		print_info("tp-gsl  x = %d y = %d \n",cinfo.x[tmp],cinfo.y[tmp]);
	}

#ifdef GSL_ALG_ID
	//int tmp1 = 0;
	cinfo.finger_num = (tmp_buf[3]<<24)|(tmp_buf[2]<<16)|(tmp_buf[1]<<8)|(tmp_buf[0]);
	gsl_alg_id_main(&cinfo);
	
	tmp1=gsl_mask_tiaoping();
	print_info("[tp-gsl] tmp1=%x\n",tmp1);
	if(tmp1>0&&tmp1<0xffffffff)
	{
		buf[0]=0xa;
		buf[1]=0;
		buf[2]=0;
		buf[3]=0;
		gsl_write_interface(ddata->client,0xf0,buf,4);
		buf[0]=(u8)(tmp1 & 0xff);
		buf[1]=(u8)((tmp1>>8) & 0xff);
		buf[2]=(u8)((tmp1>>16) & 0xff);
		buf[3]=(u8)((tmp1>>24) & 0xff);
		printk("tmp1=%08x,buf[0]=%02x,buf[1]=%02x,buf[2]=%02x,buf[3]=%02x\n",
			tmp1,buf[0],buf[1],buf[2],buf[3]);
		gsl_write_interface(ddata->client,0x8,buf,4);
	}
#endif
#ifdef GSL_GESTURE
		printk("gsl_gesture_status=%d,gsl_gesture_flag=%d\n",gsl_gesture_status,gsl_gesture_flag);
	
		if(GE_ENABLE == gsl_gesture_status && gsl_gesture_flag == 1){
			int tmp_c;
			u8 key_data = 0;
			tmp_c = gsl_obtain_gesture();
			printk("gsl_obtain_gesture():tmp_c=0x%x\n",tmp_c);
			print_info("gsl_obtain_gesture():tmp_c=0x%x\n",tmp_c);
			switch(tmp_c){
			case (int)'C':
				key_data = KEYCODE_CTPC;
				break;
			case (int)'E':
				key_data = KEYCODE_CTPE;
				break;
			case (int)'W':
				key_data = KEYCODE_CTPW;
				break;
			case (int)'O':
				key_data = KEYCODE_CTPO;
				break;
			case (int)'M':
				key_data = KEYCODE_CTPM;
				break;
			case (int)'Z':
				key_data = KEYCODE_CTPZ;
				break;
		/*
			case (int)'V':
				key_data = KEYCODE_CTPV;
				break;
		*/
			case (int)'S':
				key_data = KEYCODE_CTPS;
				break;
//			case (int)'*':	
//				key_data = KEY_POWER;
//				break;/* double click */
//			case (int)0xa1fa:
//				key_data = KEYCODE_CTPRIGHT;
//				break;/* right */
//			case (int)0xa1fd:
//				key_data = KEYCODE_CTPDOWM;
//				break;/* down */
//			case (int)0xa1fc:	
//				key_data = KEYCODE_CTPUP;
//				break;/* up */
//			case (int)0xa1fb:	
//				key_data = KEYCODE_CTPLEFT;
//				break;	/* left */
			
			}
	
			if(key_data != 0){
				gsl_gesture_c = (char)(tmp_c & 0xff);
				//gsl_gesture_status = GE_WAKEUP; //zxw modify for mx1091_hyf_9300 先划无效的手势然后划有效的手势不起作用
			do_gettimeofday(&time_now);
			if(time_now.tv_sec - time_last.tv_sec <= 1 
			&& (time_now.tv_sec - time_last.tv_sec)*1000*1000 + (time_now.tv_usec - time_last.tv_usec) < 700*1000)
				return;

			#ifdef CONFIG_OF_TOUCH	
			tpd_gpio_output(GTP_RST_PORT, 0);	
			msleep(5);
			tpd_gpio_output(GTP_RST_PORT, 1);
			msleep(2);
			#else
			mt_set_gpio_out(GPIO_CTP_RST_PIN, GPIO_OUT_ZERO);
			msleep(5);
			mt_set_gpio_out(GPIO_CTP_RST_PIN, GPIO_OUT_ONE);
			msleep(2);
			#endif
			gsl_reset_core(ddata->client);
			gsl_start_core(ddata->client);
			msleep(2);
			do_gettimeofday(&time_last);
				//gsl_gesture_status = GE_WAKEUP; //zxw modify for mx1091_hyf_9300 先划无效的手势然后划有效的手势不起作用
				printk("gsl_obtain_gesture():tmp_c=%c\n",gsl_gesture_c);
				input_report_key(tpd->dev,key_data,1);
				//input_report_key(tpd->dev,KEY_POWER,1);
				input_sync(tpd->dev);
				input_report_key(tpd->dev,key_data,0);
				//input_report_key(tpd->dev,KEY_POWER,0);
				input_sync(tpd->dev);
				msleep(400);
			}
			return;
		}
#endif

	gsl_report_point(&cinfo);



#ifdef GSL_TIMER
i2c_lock_schedule:
	i2c_lock_flag = 0;
	
#endif




}


#if 1//ndef CONFIG_OF_TOUCH
static int touch_event_handler(void *unused)
{

	struct sched_param param = { .sched_priority = RTPM_PRIO_TPD };
	sched_setscheduler(current, SCHED_RR, &param);
print_info("[tpd-gsl][%s] 6666666666666666666\n",__func__);
	do
	{
print_info("[tpd-gsl][%s] 7777777777777777777777777\n",__func__);
	#ifdef CONFIG_OF_TOUCH
	enable_irq(touch_irq);
	#else
	mt_eint_unmask(CUST_EINT_TOUCH_PANEL_NUM);
	#endif
		set_current_state(TASK_INTERRUPTIBLE);
		wait_event_interruptible(waiter, tpd_flag != 0);

	#ifdef CONFIG_OF_TOUCH
	//disable_irq(touch_irq);
	#else
	mt_eint_mask(CUST_EINT_TOUCH_PANEL_NUM);
	#endif

		tpd_flag = 0;
		TPD_DEBUG_SET_TIME;
		set_current_state(TASK_RUNNING);
		 gsl_report_work();
	} while (!kthread_should_stop());	
	return 0;
}
#endif

#ifdef GSL_DRV_WIRE_IDT_TP

#define GSL_C		100
#define GSL_CHIP_1	0x07ff0ff5  //dacheng
#define GSL_CHIP_2	0x0fff0f55  //qiankong
#define GSL_CHIP_3	0x0fff0fff  //mwd
#define GSL_CHIP_4	0xffffffff

static unsigned int gsl_count_one(unsigned int flag)
{
	unsigned int tmp=0;	
	int i =0;
	for(i=0;i<32;i++){
		if(flag&(0x1<<i))
			tmp++;
	}
	return tmp;
}
static int gsl_DrvWire_idt_tp(struct gsl_ts_data *ts)
{
	u8 buf[4];
	int i,err=1;
	int flag=0;
	u16 count0,count1;
	unsigned int tmp,tmp0;
	unsigned int tmp1,tmp2,tmp3,tmp4;
	u32 num;
identify_tp_repeat:
	gsl_clear_reg(ts->client);
	gsl_reset_core(ts->client);
	num = ARRAY_SIZE(GSL_IDT_FW);
	gsl_load_fw(ts->client,GSL_IDT_FW,num);
	gsl_start_core(ts->client);
	msleep(200);
	for(i=0;i<3;i++){
		gsl_read_interface(ts->client,0xb4,buf,4);

		print_info("i = %d count0 the test 0xb4 = {0x%02x%02x%02x%02x}\n",i,buf[3],buf[2],buf[1],buf[0]);

		count0 = (buf[3]<<8)|buf[2];
		msleep(5);
		gsl_read_interface(ts->client,0xb4,buf,4);

		print_info("i = %d count1 the test 0xb4 = {0x%02x%02x%02x%02x}\n",i,buf[3],buf[2],buf[1],buf[0]);

		count1 = (buf[3]<<8)|buf[2];
		if((count0 > 1) && (count0 != count1))
			break;
	}
	if((count0 > 1) && count0 != count1){

		print_info("[TP-GSL][%s] is start ok\n",__func__);

		gsl_read_interface(ts->client,0xb8,buf,4);
		tmp = (buf[3]<<24)|(buf[2]<<16)|(buf[1]<<8)|buf[0];

		print_info("the test 0xb8 = {0x%02x%02x%02x%02x}\n",buf[3],buf[2],buf[1],buf[0]);
		tmp1 = gsl_count_one(GSL_CHIP_1^tmp);
		tmp0 = gsl_count_one((tmp&GSL_CHIP_1)^GSL_CHIP_1);
		tmp1 += tmp0*GSL_C;
		print_info("[TP-GSL] tmp1 = %d\n",tmp1);
		
		tmp2 = gsl_count_one(GSL_CHIP_2^tmp);
		tmp0 = gsl_count_one((tmp&GSL_CHIP_2)^GSL_CHIP_2);
		tmp2 += tmp0*GSL_C;
		print_info("[TP-GSL] tmp2 = %d\n",tmp2);	
	
		tmp3 = gsl_count_one(GSL_CHIP_3^tmp);
		tmp0 = gsl_count_one((tmp&GSL_CHIP_3)^GSL_CHIP_3);
		tmp3 += tmp0*GSL_C;
		print_info("[TP-GSL] tmp3 = %d\n",tmp3);	
	
		tmp4 = gsl_count_one(GSL_CHIP_4^tmp);
		tmp0 = gsl_count_one((tmp&GSL_CHIP_4)^GSL_CHIP_4);
		tmp4 += tmp0*GSL_C;
		print_info("[TP-GSL] tmp4 = %d\n",tmp4);

		if(0xffffffff==GSL_CHIP_1)
		{
			tmp1=0xffff;
		}
		if(0xffffffff==GSL_CHIP_2)
		{
			tmp2=0xffff;
		}
		if(0xffffffff==GSL_CHIP_3)
		{
			tmp3=0xffff;
		}
		if(0xffffffff==GSL_CHIP_4)
		{
			tmp4=0xffff;
		}
		print_info("[TP-GSL] tmp1 = %d\n",tmp1);
		print_info("[TP-GSL] tmp2 = %d\n",tmp2);
		print_info("[TP-GSL] tmp3 = %d\n",tmp3);
		print_info("[TP-GSL] tmp4 = %d\n",tmp4);
		tmp = tmp1;
		if(tmp1>tmp2){
			tmp = tmp2;	
		}
		if(tmp > tmp3){
			tmp = tmp3;
		}
		if(tmp>tmp4){
			tmp = tmp4;
		}
	
		if(tmp == tmp1){
			gsl_cfg_index = 0;	
		}else if(tmp == tmp2){
			gsl_cfg_index = 0;
		}else if(tmp == tmp3){
			gsl_cfg_index = 0;
		}else if(tmp == tmp4){
			gsl_cfg_index = 0;
		}
		err = 1;
	}else {
		flag++;
		if(flag < 3)
			goto identify_tp_repeat;
		err = 0;
	}
	return err;	
}
#endif


#ifdef CONFIG_OF_TOUCH
static irqreturn_t tpd_eint_interrupt_handler(unsigned irq, struct irq_desc *desc);
#else
static void tpd_eint_interrupt_handler(void);
#endif

#ifdef CONFIG_OF_TOUCH
static irqreturn_t tpd_eint_interrupt_handler(unsigned irq, struct irq_desc *desc)
{
	//TPD_DEBUG_PRINT_INT;

	tpd_flag = 1;
	/* enter EINT handler disable INT, make sure INT is disable when handle touch event including top/bottom half */
	/* use _nosync to avoid deadlock */
	print_info("[tpd-gsl][%s][%d] cccccccccccccccccccccccccccccc\n",__func__,__LINE__);
	disable_irq_nosync(touch_irq);
	wake_up_interruptible(&waiter);
	return IRQ_HANDLED;
}

#else

static void tpd_eint_interrupt_handler(void)
{
    tpd_flag=1;
    wake_up_interruptible(&waiter);

}
#endif


static void gsl_hw_init(void)
{
#ifdef CONFIG_OF_TOUCH
	int ret = 0;
        ret = regulator_set_voltage(tpd->reg, 2800000, 2800000);    /* set 2.8v */
        if (ret)
            printk("regulator_set_voltage() failed!\n");
        ret = regulator_enable(tpd->reg);   /* enable regulator */
        if (ret)
            printk("regulator_enable() failed!\n");
			
    #ifdef TPD_POWER_VTP28_USE_VCAMA
	        pmic_set_register_value(PMIC_RG_VCAMA_EN,1);
	        pmic_set_register_value(PMIC_RG_VCAMA_VOSEL,0x03);//1.8
    #else
            hwPowerOn(MT6328_POWER_LDO_VGP1, VOL_2800, "TP");
    #endif
	
	tpd_gpio_as_int(GTP_RST_PORT);
	tpd_gpio_output(GTP_RST_PORT, 0);
	tpd_gpio_output(GTP_RST_PORT, 1);
	msleep(100);
	tpd_gpio_as_int(GTP_INT_PORT);
	msleep(100);
#else
	//power on
    #ifdef TPD_POWER_VTP28_USE_VCAMA
	        pmic_set_register_value(PMIC_RG_VCAMA_EN,1);
	        pmic_set_register_value(PMIC_RG_VCAMA_VOSEL,0x03);//1.8
    #else
            hwPowerOn(MT6328_POWER_LDO_VGP1, VOL_2800, "TP");
    #endif

	/* reset ctp gsl1680 */
	mt_set_gpio_mode(GPIO_CTP_RST_PIN, GPIO_MODE_00);
	mt_set_gpio_dir(GPIO_CTP_RST_PIN, GPIO_DIR_OUT);
	mt_set_gpio_out(GPIO_CTP_RST_PIN, GPIO_OUT_ZERO);
	msleep(20);
	mt_set_gpio_out(GPIO_CTP_RST_PIN, GPIO_OUT_ONE);
	/* set interrupt work mode */
	mt_set_gpio_mode(GPIO_CTP_EINT_PIN, GPIO_CTP_EINT_PIN_M_EINT);
	mt_set_gpio_dir(GPIO_CTP_EINT_PIN, GPIO_DIR_IN);
	mt_set_gpio_pull_enable(GPIO_CTP_EINT_PIN, GPIO_PULL_ENABLE);
	mt_set_gpio_pull_select(GPIO_CTP_EINT_PIN, GPIO_PULL_UP);
	msleep(100);
#endif
}

//modify by crystal
#ifdef TPD_PROC_DEBUG
static int gsl_server_list_open(struct inode *inode,struct file *file)
{
	return single_open(file,gsl_config_read_proc,NULL);
}
static const struct file_operations gsl_seq_fops = {
	.open = gsl_server_list_open,
	.read = seq_read,
	.release = single_release,
	.write = gsl_config_write_proc,
	.owner = THIS_MODULE,
};
#endif

static int  gsl_probe(struct i2c_client *client, const struct i2c_device_id *id)
{
	int err;
	s32 ret = 0;
	//unsigned char tp_data[4];
#ifdef TPD_PROXIMITY
	struct hwmsen_object obj_ps;
#endif
	struct device_node *node = NULL; //crystal
   	client->timing = 400; //add by litianfeng 20160106
	input_set_abs_params(tpd->dev,ABS_MT_TRACKING_ID, 0,(MAX_CONTACTS+1), 0, 0);//add by sky [2015.11.30]

	print_info();

	ddata = kzalloc(sizeof(struct gsl_ts_data), GFP_KERNEL);
	if (!ddata) {
		print_info("alloc ddata memory error\n");
		return -ENOMEM;
	}
	mutex_init(&gsl_i2c_lock);
	ddata->client = client;
	print_info("ddata->client->addr = 0x%x \n",ddata->client->addr);
	gsl_hw_init();
	#ifdef CONFIG_OF_TOUCH
	//disable_irq(touch_irq);
	#else
	mt_eint_mask(CUST_EINT_TOUCH_PANEL_NUM);
	#endif

	i2c_set_clientdata(ddata->client, ddata);

	err = gsl_test_i2c(ddata->client);
	if(err<0)
		goto  err_malloc;
#ifdef GSL_DRV_WIRE_IDT_TP
	gsl_DrvWire_idt_tp(ddata);
#endif
#ifdef GSL_GPIO_IDT_TP
	gsl_gpio_idt_tp(ddata);
#endif

	print_info("[tpd-gsl][%s] gsl_cfg_index=%d\n",__func__,gsl_cfg_index);
#ifdef GSL_GESTURE
	gsl_FunIICRead(gsl_read_oneframe_data);
    	gsl_GestureExternInt(gsl_model_extern,sizeof(gsl_model_extern)/sizeof(unsigned int)/18);
#endif

	gsl_sw_init(ddata->client);
	msleep(20);
	check_mem_data(ddata->client);


	print_info("[tpd-gsl][%s] 111111111111111\n",__func__);
	thread = kthread_run(touch_event_handler, 0, TPD_DEVICE);
	if (IS_ERR(thread)) {
	print_info("[tpd-gsl][%s] 222222222222222\n",__func__);
		//err = PTR_ERR(thread);
		//TPD_DMESG(TPD_DEVICE " failed to create kernel thread: %d\n", PTR_ERR(thread));
	}
	print_info("[tpd-gsl][%s] 3333333333333333\n",__func__);



#ifndef CONFIG_OF_TOUCH
	mt_eint_registration(CUST_EINT_TOUCH_PANEL_NUM, EINTF_TRIGGER_RISING, tpd_eint_interrupt_handler, 1);
	//mt_eint_unmask(CUST_EINT_TOUCH_PANEL_NUM);
#else
	node = of_find_matching_node(node, touch_of_match);
	print_info("[tpd-gsl][%s] 4444444444444444\n",__func__);
	//node = of_find_compatible_node(NULL, NULL, "mediatek,TOUCH-eint");
	//node = of_find_compatible_node(NULL, NULL, "mediatek,cap_touch");
	if (node) {
	print_info("[tpd-gsl][%s] 44444444444444445555555555555555\n",__func__);
	touch_irq = irq_of_parse_and_map(node, 0);
	ret = request_irq(touch_irq, (irq_handler_t) tpd_eint_interrupt_handler,
			    IRQF_TRIGGER_RISING, "touch-eint", NULL);
			if (ret > 0) {
				ret = -1;
				print_info("tpd request_irq IRQ LINE NOT AVAILABLE!.");
		
				/* mt_eint_unmask(CUST_EINT_TOUCH_PANEL_NUM); */
//				enable_irq(touch_irq);
				}
	}
#endif

#ifdef GSL_TIMER
	INIT_DELAYED_WORK(&gsl_timer_check_work, gsl_timer_check_func);
	gsl_timer_workqueue = create_workqueue("gsl_timer_check");

	queue_delayed_work(gsl_timer_workqueue, &gsl_timer_check_work, GSL_TIMER_CHECK_CIRCLE);

#endif
	print_info("[tpd-gsl][%s] 555555555555555555\n",__func__);
#ifdef TPD_PROC_DEBUG
#if 0
	gsl_config_proc = create_proc_entry(GSL_CONFIG_PROC_FILE, 0666, NULL);
	if (gsl_config_proc == NULL)
	{
		print_info("create_proc_entry %s failed\n", GSL_CONFIG_PROC_FILE);
	}
	else
	{
		gsl_config_proc->read_proc = gsl_config_read_proc;
		gsl_config_proc->write_proc = gsl_config_write_proc;
	}
#else
	#ifdef TPD_PROC_DEBUG
	proc_create(GSL_CONFIG_PROC_FILE,0666,NULL,&gsl_seq_fops);
	#endif
#endif
	gsl_proc_flag = 0;
#endif


#ifdef GSL_ALG_ID
	gsl_DataInit(gsl_cfg_table[gsl_cfg_index].data_id);
#endif
#ifdef TPD_PROXIMITY
	//obj_ps.self = gsl1680p_obj;
	//	obj_ps.self = cm3623_obj;
	obj_ps.polling = 0;//interrupt mode
	//obj_ps.polling = 1;//need to confirm what mode is!!!
	obj_ps.sensor_operate = tpd_ps_operate;//gsl1680p_ps_operate;
	if((err = hwmsen_attach(ID_PROXIMITY, &obj_ps)))
	{
		printk("attach fail = %d\n", err);
	}
	
	wake_lock_init(&ps_lock, WAKE_LOCK_SUSPEND, "ps wakelock");
#endif 
		input_set_abs_params(tpd->dev,ABS_MT_TRACKING_ID,0,5,0,0);
#ifdef GSL_GESTURE
		input_set_capability(tpd->dev, EV_KEY, KEY_POWER);//\A1\C1\A1\E92\A8\A2\A8\BA?\A8\A8?\A1\C1\A8\AE?\A6̨\AA3
		input_set_capability(tpd->dev, EV_KEY, KEYCODE_CTPC);
		input_set_capability(tpd->dev, EV_KEY, KEYCODE_CTPE);
		input_set_capability(tpd->dev, EV_KEY, KEYCODE_CTPO);
		input_set_capability(tpd->dev, EV_KEY, KEYCODE_CTPW);
		input_set_capability(tpd->dev, EV_KEY, KEYCODE_CTPM);
		input_set_capability(tpd->dev, EV_KEY, KEYCODE_CTPZ);
		//input_set_capability(tpd->dev, EV_KEY, KEYCODE_CTPV);
		input_set_capability(tpd->dev, EV_KEY, KEYCODE_CTPS);
		/*input_set_capability(tpd->dev, EV_KEY, KEYCODE_CTPUP);
		input_set_capability(tpd->dev, EV_KEY, KEYCODE_CTPDOWM);
		input_set_capability(tpd->dev, EV_KEY, KEYCODE_CTPRIGHT);
		input_set_capability(tpd->dev, EV_KEY, KEYCODE_CTPLEFT);  */
#endif

	//gsl_sysfs_init();
	#ifdef CONFIG_OF_TOUCH
	print_info("[tpd-gsl][%s][%d] bbbbbbbbbbbbbbbbbbbbbbbbbbbb\n",__func__,__LINE__);
	disable_irq(touch_irq);
	print_info("[tpd-gsl][%s][%d] dddddddddddddddddddddddddddd\n",__func__,__LINE__);
	enable_irq(touch_irq);
	#else
	mt_eint_unmask(CUST_EINT_TOUCH_PANEL_NUM);
	#endif
	
	tpd_load_status = 1;

	return 0;

err_malloc:
	if (ddata)
		kfree(ddata);

	#ifdef GTP_SUPPORT_I2C_DMA
	msg_dma_release();
	#endif

	return err;
}

/*****************************************************************************
Prototype    : gsl_remove
Description  : remove gsl1680 driver
Input        : struct i2c_client *client
Output       : int
Return Value : static

 *****************************************************************************/
static int  gsl_remove(struct i2c_client *client)
{
#if defined(MX2116_JC_C12)||defined(MX2116_JC_C11)||defined(MX2116_WPF_D5019) 
#ifdef GSL_TIMER
			cancel_delayed_work_sync(&gsl_timer_check_work);
			destroy_workqueue(gsl_timer_workqueue);
#endif
	#ifdef GTP_SUPPORT_I2C_DMA
		msg_dma_release();
	#endif
#endif
	print_info("[gsl1680] TPD removed\n");
	return 0;
}

/*****************************************************************************
Prototype    : gsl_detect
Description  : gsl1680 driver local setup without board file
Input        : struct i2c_client *client
int kind
struct i2c_board_info *info
Output       : int
Return Value : static

 *****************************************************************************/
static int gsl_detect (struct i2c_client *client, struct i2c_board_info *info)
{
	//int error;

	print_info("%s, %d\n", __FUNCTION__, __LINE__);
	strcpy(info->type, TPD_DEVICE);
	return 0;
}

static struct i2c_driver gsl_i2c_driver = {
    .driver = {
		.name = TPD_DEVICE,
		.owner = THIS_MODULE,
		#ifdef CONFIG_OF_TOUCH
		.of_match_table = tpd_of_match,
		#endif
    },
	.probe = gsl_probe,
	.remove =gsl_remove,
	.id_table = gsl_device_id,
	.detect = gsl_detect,
};

/*****************************************************************************
Prototype    : gsl_local_init
Description  : setup gsl1680 driver
Input        : None
Output       : None
Return Value : static

 *****************************************************************************/
static int gsl_local_init(void)
{
	int ret;
	print_info();
	#ifdef CONFIG_OF_TOUCH
		printk("[wsl] gsl_local_init start \n");

	printk("[wsl] regulator_get start \n");
    tpd->reg = regulator_get(tpd->tpd_dev, "vtouch");
    if (IS_ERR(tpd->reg))
	print_info("tpd->reg error\n");
	printk("[wsl] regulator_get end \n");
	
       input_set_abs_params(tpd->dev, ABS_MT_TRACKING_ID, 0, 10, 0, 0);
	if (tpd_dts_data.use_tpd_button) {
		/*initialize tpd button data */
		tpd_button_setting(tpd_dts_data.tpd_key_num,
				   tpd_dts_data.tpd_key_local,
				   tpd_dts_data.tpd_key_dim_local);
	}
	#else
	boot_mode = get_boot_mode();
	print_info("boot_mode == %d \n", boot_mode);

	if (boot_mode == SW_REBOOT)
	boot_mode = NORMAL_BOOT;

#ifdef TPD_HAVE_BUTTON
	print_info("TPD_HAVE_BUTTON\n");
	tpd_button_setting(TPD_KEY_COUNT, tpd_keys_local, tpd_keys_dim_local);
#endif
	#endif

	#ifdef GTP_SUPPORT_I2C_DMA
	msg_dma_alloct();
	#endif

	ret = i2c_add_driver(&gsl_i2c_driver);

	if (ret < 0) {
		print_info("unable to i2c_add_driver\n");
		return -ENODEV;
	}

	if (tpd_load_status == 0) 
	{
		print_info("tpd_load_status == 0, gsl_probe failed\n");
		i2c_del_driver(&gsl_i2c_driver);
		return -ENODEV;
	}

	/* define in tpd_debug.h */
	tpd_type_cap = 1;
	print_info("end %s, %d\n", __FUNCTION__, __LINE__);
	return 0;
}

static void gsl_suspend(struct device *h)
{

	int tmp;
	print_info();
	printk("gsl_suspend11111111111111111111\n");
	printk("gsl_suspend:gsl_ps_enable = %d\n",gsl_ps_enable);
#if defined(MX2116_JC_C12)||defined(MX2116_JC_C11)||defined(MX2116_WPF_D5019)  	
#if  0//def GSL_TIMER	
	cancel_delayed_work_sync(&gsl_timer_check_work);
	i2c_lock_flag=0;
#endif
#else
#ifdef GSL_TIMER	
	cancel_delayed_work_sync(&gsl_timer_check_work);
	i2c_lock_flag=0;
#endif
#endif
#ifdef TPD_PROXIMITY
	if (tpd_proximity_flag == 1)
	{
	    return 0;
	}
#endif
	gsl_halt_flag = 1;
	//version info
	printk("[tp-gsl]the last time of debug:%x\n",TPD_DEBUG_TIME);
#ifdef GSL_ALG_ID
	tmp = gsl_version_id();	
	printk("[tp-gsl]the version of alg_id:%x\n",tmp);
#endif
#if defined(MX2116_JC_C12)||defined(MX2116_JC_C11)||defined(MX2116_WPF_D5019)  
#ifdef GSL_TIMER
    i2c_lock_flag = 0;	
	cancel_delayed_work_sync(&gsl_timer_check_work);
#endif
#endif
	
	//version info

#ifdef TPD_PROC_DEBUG
	if(gsl_proc_flag == 1){
		return;
	}
#endif

#ifdef GSL_GESTURE
	if(gsl_gesture_flag == 1){
		gsl_enter_doze(ddata);
		return;
	}
#endif

	#ifdef CONFIG_OF_TOUCH
	print_info("[tpd-gsl][%s][%d] cccccccccccccccccccccccccccccc\n",__func__,__LINE__);
	disable_irq(touch_irq);
	#else
	mt_eint_mask(CUST_EINT_TOUCH_PANEL_NUM);
	#endif
//	gsl_reset_core(ddata->client);
//	msleep(20);
	#ifdef CONFIG_OF_TOUCH
	tpd_gpio_output(GTP_RST_PORT, 0);
	#else
	mt_set_gpio_out(GPIO_CTP_RST_PIN, GPIO_OUT_ZERO);
	#endif
}

static void gsl_resume(struct device *h)
{
	print_info();

		
#ifdef TPD_PROXIMITY
	if (tpd_proximity_flag == 1&&gsl_halt_flag == 0)
	{
		tpd_enable_ps(1);
		return;
	}
#endif


#ifdef TPD_PROC_DEBUG
	if(gsl_proc_flag == 1){
		return;
	}
#endif
#ifdef GSL_GESTURE
			if(gsl_gesture_flag == 1){
				gsl_quit_doze(ddata);
			}
#endif

	#ifdef CONFIG_OF_TOUCH
	tpd_gpio_output(GTP_RST_PORT, 1);
	#else
	mt_set_gpio_out(GPIO_CTP_RST_PIN, GPIO_OUT_ONE);
	#endif
	msleep(20);
	gsl_reset_core(ddata->client);

	
	
#ifdef GSL_GESTURE	
#ifdef GSL_ALG_ID
	gsl_DataInit(gsl_cfg_table[gsl_cfg_index].data_id);
#endif
#endif
	gsl_start_core(ddata->client);
	msleep(20);
	check_mem_data(ddata->client);
	#ifdef CONFIG_OF_TOUCH
	print_info("[tpd-gsl][%s][%d] aaaaaaaaaaaaaaaaaaaaaaaaaa\n",__func__,__LINE__);
	enable_irq(touch_irq);
	#else
	mt_eint_unmask(CUST_EINT_TOUCH_PANEL_NUM);
	#endif
#ifdef GSL_TIMER
#if defined(MX2116_JC_C12)||defined(MX2116_JC_C11)||defined(MX2116_WPF_D5019)  
i2c_lock_flag=0;
#endif
	queue_delayed_work(gsl_timer_workqueue, &gsl_timer_check_work, GSL_TIMER_CHECK_CIRCLE);
#endif

	gsl_halt_flag = 0;
#ifdef TPD_PROXIMITY
	if (tpd_proximity_flag == 1)
	{
		tpd_enable_ps(1);
		return;
	}
#endif

}


static struct tpd_driver_t gsl_driver = {
	.tpd_device_name = GSL_DEV_NAME,
	.tpd_local_init = gsl_local_init,
	.suspend = gsl_suspend,
	.resume = gsl_resume,
#ifdef TPD_HAVE_BUTTON
	.tpd_have_button = 1,
#else
 	.tpd_have_button = 0,
#endif
};

static int __init gsl_driver_init(void)
{
	//int ret;
	print_info();
	#ifdef CONFIG_OF_TOUCH
	tpd_get_dts_info();
	#else
	i2c_register_board_info(1, &i2c_tpd, 1);
	#endif
	if(tpd_driver_add(&gsl_driver) < 0)
	printk("gsl_driver init error, return num is  \n");

	return 0;
}

static void __exit gsl_driver_exit(void)
{
	print_info();
	tpd_driver_remove(&gsl_driver);
}

module_init(gsl_driver_init);
module_exit(gsl_driver_exit);

