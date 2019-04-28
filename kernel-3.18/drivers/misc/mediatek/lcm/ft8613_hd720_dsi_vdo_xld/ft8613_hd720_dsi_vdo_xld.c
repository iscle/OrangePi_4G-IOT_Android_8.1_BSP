#ifndef BUILD_LK
#include <linux/string.h>
#include <linux/kernel.h>
#endif
#include "lcm_drv.h"

#ifdef BUILD_LK
	#include <platform/upmu_common.h>
	#include <platform/mt_gpio.h>
	#include <platform/mt_i2c.h>
	#include <platform/mt_pmic.h>
	#include <string.h>
#elif defined(BUILD_UBOOT)
	#include <asm/arch/mt_gpio.h>
#else
#ifdef CONFIG_MTK_LEGACY
	#include <mach/mt_pm_ldo.h>
	#include <mach/mt_gpio.h>
#endif
#endif
#ifdef BUILD_LK
#define LCD_DEBUG(fmt)  printf(fmt)
#else
#define LCD_DEBUG(fmt)  printk(fmt)
#endif
#ifdef CONFIG_MTK_LEGACY
#include <cust_i2c.h>
#include <mach/gpio_const.h>
#include <cust_gpio_usage.h>
#endif
#include <linux/gpio.h>
#include <linux/of_gpio.h>


static LCM_UTIL_FUNCS lcm_util;

#define SET_RESET_PIN(v)			(lcm_util.set_reset_pin((v)))
#define MDELAY(n)					(lcm_util.mdelay(n))

/* --------------------------------------------------------------------------- */
/* Local Functions */
/* --------------------------------------------------------------------------- */
#define dsi_set_cmdq_V2(cmd, count, ppara, force_update) \
	lcm_util.dsi_set_cmdq_V2(cmd, count, ppara, force_update)
#define dsi_set_cmdq(pdata, queue_size, force_update) \
	lcm_util.dsi_set_cmdq(pdata, queue_size, force_update)
#define wrtie_cmd(cmd) \
	lcm_util.dsi_write_cmd(cmd)
#define write_regs(addr, pdata, byte_nums) \
	lcm_util.dsi_write_regs(addr, pdata, byte_nums)
#define read_reg(cmd) \
	lcm_util.dsi_dcs_read_lcm_reg(cmd)
#define read_reg_v2(cmd, buffer, buffer_size) \
	lcm_util.dsi_dcs_read_lcm_reg_v2(cmd, buffer, buffer_size)

// ---------------------------------------------------------------------------
//  Local Constants
// ---------------------------------------------------------------------------
#define FRAME_WIDTH  										(720)
#define FRAME_HEIGHT 										(1440)


#define REGFLAG_DELAY             							0xFE
#define REGFLAG_END_OF_TABLE      							0xFD   // END OF REGISTERS MARKER


#ifndef TRUE
    #define TRUE 1
#endif

#ifndef FALSE
    #define FALSE 0
#endif

static unsigned int need_set_lcm_addr = 1;

#define LCM_DSI_CMD_MODE		0

#define LCM_FT8613_ID (0x8716)

#if defined(CONFIG_RUNYEE_LCM_BIAS_SUPPORT)
extern int lcm_bias_enable(void);
extern int lcm_bias_disable(void);
#endif

// ---------------------------------------------------------------------------
//  Local Variables
// ---------------------------------------------------------------------------

struct LCM_setting_table {
    unsigned char cmd;
    unsigned char count;
    unsigned char para_list[128];
};
//update initial param for IC ft8613 0.01
static struct LCM_setting_table ft8613_hd720_dsi_vdo_xld[] = {
	{0x00,1,{0x00}},
	{0xFF,3,{0x87,0x16,0x01}},
	{0x00,1,{0x80}},
	{0xFF,2,{0x87,0x16}},

	{0x00,1,{0x80}},
	{0xC0,15,{0x00,0xA5,0x00,0x10,0x10,0x00,0xA5,0x10,0x10,0x00,0xA5,0x00,0x10,0x10,0x00}}, //20170725

	{0x00,1,{0x80}},
	{0xF3,1,{0x70}},

	{0x00,1,{0x90}},
	{0xC5,1,{0x55}},

	{0x00,1,{0xA0}},
	{0xC0,7,{0x0D,0x01,0x01,0x01,0x01,0x24,0x09}},

	{0x00,1,{0xD0}},
	{0xC0,7,{0x0D,0x01,0x01,0x01,0x01,0x24,0x09}},

	{0x00,1,{0xA8}},
	{0xF5,1,{0x22}},

	{0x00,1,{0x82}},
	{0xA5,3,{0x00,0x00,0x0C}},

	{0x00,1,{0x87}},
	{0xA5,4,{0x00,0x07,0x77,0x00}},

	{0x00,1,{0xA0}},
	{0xCE,8,{0x00,0x05,0x01,0x01,0x01,0x01,0x3F,0x0A}},

	{0x00,1,{0xA0}},
	{0xB3,1,{0x32}},

	{0x00,1,{0xA6}},
	{0xB3,1,{0x58}}, //20170725

	//----- GOA -----
	// VST
	{0x00,1,{0x80}},
	{0xC2,12,{0x82,0x00,0x00,0x81,0x81,0x00,0x00,0x81,0x83,0x00,0x3f,0xB0}},//20170816 VST RST gap

	// CKV
	{0x00,1,{0xB0}},
	{0xC2,15,{0x82,0x00,0x00,0x07,0x88,0x81,0x01,0x00,0x07,0x88,0x00,0x02,0x00,0x07,0x88}}, //20170816 ckv gap

	{0x00,1,{0xC0}},
	{0xC2,5,{0x01,0x03,0x00,0x07,0x88}},

	// CKV period
	{0x00,1,{0xDA}},
	{0xC2,2,{0x33,0x33}},


	// CKH BP FB
	{0x00,1,{0x82}},
	{0xA5,3,{0x00,0x00,0x00}},

	// CKH TP term
	{0x00,1,{0x87}},
	{0xA5,4,{0x00,0x07,0x77,0x77}},

	// EQ function
	{0x00,1,{0x88}},
	{0xC3,2,{0x22,0x22}},

	{0x00,1,{0x98}},
	{0xC3,2,{0x22,0x22}},

	// CKV TP ctrl
	{0x00,1,{0xAA}},
	{0xC3,2,{0x99,0x9C}},

	{0x00,1,{0x90}},
	{0xC5,1,{0x55}},

	//----- tcon TP setting -----
	{0x00,1,{0x80}},
	{0xCE,9,{0x25,0x00,0x74,0x00,0x78,0xFF,0x00,0x00,0x05}}, 

	{0x00,1,{0x90}},
	{0xCE,8,{0x00,0x5C,0x0C,0xE4,0x00,0x5C,0x00,0x00}},

	{0x00,1,{0xB0}},
	{0xCE,6,{0x00,0x00,0x60,0x60,0x00,0x60}},

#if 0
	{0x00,1,{0xC0}},
	{0xF4,1,{0x93,0x36}},

	{0x00,1,{0xb0}},
	{0xF6,3,{0x69,0x16,0x1F}},
#endif

	//----- panel interface ----- 
	{0x00,1,{0x80}},	// U 2 D	CC80	
	{0xCC,12,{0x02,0x03,0x06,0x07,0x08,0x09,0x0A,0x0B,0x0C,0x0D,0x0E,0x0F}},
	{0x00,1,{0x90}},	// D 2 U	CC90	
	{0xCC,12,{0x03,0x02,0x09,0x08,0x07,0x06,0x0F,0x0E,0x0D,0x0C,0x0B,0x0A}},
	{0x00,1,{0xA0}},	// no dir 1	CCA0	
	{0xCC,15,{0x1A,0x1B,0x1C,0x1D,0x1E,0x1F,0x18,0x19,0x20,0x21,0x14,0x15,0x16,0x17,0x04}},
	{0x00,1,{0xB0}},	// no dir 2	CCB0	
	{0xCC,5,{0x22,0x22,0x22,0x22,0x22}},
	{0x00,1,{0x80}},	// slp in	CB80	
	{0xCB,8,{0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00}},
	{0x00,1,{0x90}},	// power on 1	CB90	
	{0xCB,15,{0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00}},
	{0x00,1,{0xA0}},	// power on 2	CBA0	
	{0xCB,15,{0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00}},
	{0x00,1,{0xB0}},	// power on 3	CBB0	
	{0xCB,2,{0x00,0x00}},
	{0x00,1,{0xC0}},	// power off 1	CBC0	
	{0xCB,15,{0x05,0x05,0x05,0x05,0x05,0x05,0x00,0x00,0x00,0x00,0x00,0x00,0x05,0x05,0x05}}, //20170727
	{0x00,1,{0xD0}},	// power off 2	CBD0	
	{0xCB,15,{0x00,0x00,0x00,0x05,0x05,0x00,0x00,0x00,0x00,0x00,0x00,0x0A,0x00,0x00,0x00}}, //20170727
	{0x00,1,{0xE0}},	// power off 3	CBE0	
	{0xCB,2,{0x00,0x00}},
	{0x00,1,{0xF0}},	// L V D	CBF0	
	{0xCB,8,{0xFF,0x0F,0x00,0x3F,0x30,0x30,0x30,0x00}},
	{0x00,1,{0x80}},	// CGOUT R 1	CD80	
	{0xCD,15,{0x22,0x22,0x22,0x22,0x22,0x01,0x13,0x14,0x1B,0x05,0x03,0x17,0x18,0x18,0x22}},
	{0x00,1,{0x90}},	// CGOUT R 2	CD90	
	{0xCD,3,{0x0F,0x0E,0x0D}},
	{0x00,1,{0xA0}},	// CGOUT L 1	CDA0	
	{0xCD,15,{0x22,0x22,0x22,0x22,0x22,0x02,0x13,0x14,0x1B,0x06,0x04,0x17,0x18,0x18,0x22}},
	{0x00,1,{0xB0}},	// CGOUT L 2	CDB0	
	{0xCD,3,{0x0F,0x0E,0x0D}},

	{0x00,1,{0x81}},	// All gate on off	F381 20170816
	{0xF3,12,{0x40,0x89,0xC0,0x40,0x89,0xC0,0x40,0x89,0xC0,0x40,0x89,0xC0}},


	//----- other -----
	{0x00,1,{0x00}},
	{0xE1,24,{0x03,0x09,0x1A,0x2B,0x36,0x42,0x55,0x64,0x6c,0x76,0x7F,0x89,0x6E,0x67,0x64,0x5d,0x50,0x44,0x36,0x2C,0x24,0x18,0x06,0x06}},

	{0x00,1,{0x00}},
	{0xE2,24,{0x03,0x09,0x1A,0x2B,0x36,0x42,0x55,0x64,0x6c,0x76,0x7F,0x89,0x6E,0x67,0x64,0x5d,0x50,0x44,0x36,0x2C,0x24,0x18,0x06,0x06}},

	{0x00,1,{0x80}},
	{0xC5,10,{0x00,0xC1,0xDD,0xC4,0x14,0x1E,0x00,0x55,0x50,0x05}},

	{0x00,1,{0x90}},
	{0xC5,10,{0x77,0x1E,0x14,0x00,0x88,0x10,0x4C,0x53,0x55,0x50}}, //20170725

	{0x00,1,{0x00}},
	{0xD8,2,{0x31,0x31}},// modified 20160704 GVDD 5.3V

	{0x00,1,{0x00}},
	{0xD9,5,{0x80,0xBC,0xBC,0xBC,0xBC}},

	{0x00,1,{0xd1}},
	{0xcf,12,{0x02,0x04,0x00,0x0A,0x00,0x14,0x02,0x04,0x00,0x0C,0x00,0x12}},

	{0x00,1,{0xc0}},
	{0xC0,2,{0x11,0x11}}, //poweroff2 en  2frame 20170816

	//For 720x1440
	{0x00,1,{0x00}},
	{0x2A,4,{0x00,0x00,0x02,0xcf}}, //719
	{0x00,1,{0x00}},
	{0x2B,4,{0x00,0x00,0x05,0x9f}}, //1439

	{0x00,1,{0xa1}},
	{0xB3,4,{0x02,0xd0,0x05,0xa0}},

#if 0
	//add id 20170821
	{0x00,1,{0x00}},
	{0xD1,2,{0x00,0x01}},
#endif

	//add vcom pull GND  20170822
	{0x00,1,{0x8d}},
	{0xF5,1,{0x21}},


	//CMD2 DISABLE
	{0x00,1,{0x00}},
	{0xFF,3,{0x00,0x00,0x00}},
	{0x00,1,{0x80}},
	{0xFF,2,{0x00,0x00}},

	{0x35,1,{0x00}},
	//{0x36,1,{0x03}},
	{0x11,1,{0x00}},
	{REGFLAG_DELAY, 120, {}}, 
	{0x29,1,{0x00}},
	{REGFLAG_DELAY, 50, {}},

	// Setting ending by predefined flag
	{REGFLAG_END_OF_TABLE, 0x00, {}}	

	
};
				
static struct LCM_setting_table lcm_deep_sleep_mode_in_setting[] = {
	// Display off sequence
	{0x28, 0, {0x00}},
	{REGFLAG_DELAY, 20, {}},

    // Sleep Mode On
	{0x10, 0, {0x00}},
	{REGFLAG_DELAY, 120, {}},
	{REGFLAG_END_OF_TABLE, 0x00, {}}
};

static void push_table(struct LCM_setting_table *table, unsigned int count, unsigned char force_update)
{
    unsigned int i;

    for(i = 0; i < count; i++)
    {
        unsigned cmd;
        cmd = table[i].cmd;

        switch (cmd) {

            case REGFLAG_DELAY :
                MDELAY(table[i].count);
                break;

            case REGFLAG_END_OF_TABLE :
                break;

            default:
                dsi_set_cmdq_V2(cmd, table[i].count, table[i].para_list, force_update);
        }
    }
}

// ---------------------------------------------------------------------------
//  LCM Driver Implementations
// ---------------------------------------------------------------------------

static void lcm_set_util_funcs(const LCM_UTIL_FUNCS *util)
{
    memcpy(&lcm_util, util, sizeof(LCM_UTIL_FUNCS));
}


static void lcm_get_params(LCM_PARAMS *params)
{
		memset(params, 0, sizeof(LCM_PARAMS));
		params->type							   = LCM_TYPE_DSI;
		params->width							   = FRAME_WIDTH;
		params->height							   = FRAME_HEIGHT;
#if (LCM_DSI_CMD_MODE)	
		params->dsi.mode = SYNC_PULSE_VDO_MODE;	
#else	
		params->dsi.mode						   = BURST_VDO_MODE;//SYNC_PULSE_VDO_MODE;
#endif		
		params->dsi.switch_mode 				   = CMD_MODE;
		params->dsi.switch_mode_enable			   = 0;
		/* DSI */
		/* Command mode setting */
		params->dsi.LANE_NUM					   = LCM_FOUR_LANE;
		/* The following defined the fomat for data coming from LCD engine. */
		params->dsi.data_format.color_order 	   = LCM_COLOR_ORDER_RGB;
		params->dsi.data_format.trans_seq		   = LCM_DSI_TRANS_SEQ_MSB_FIRST;
		params->dsi.data_format.padding 		   = LCM_DSI_PADDING_ON_LSB;
		params->dsi.data_format.format			   = LCM_DSI_FORMAT_RGB888;

		//params->dsi.intermediat_buffer_num = 2;
		/* Highly depends on LCD driver capability. */
		params->dsi.packet_size 				   = 256;
		/* video mode timing */
		params->dsi.PS							   = LCM_PACKED_PS_24BIT_RGB888;
	
		params->dsi.vertical_sync_active		   = 4;
		params->dsi.vertical_backporch			   = 16;
		params->dsi.vertical_frontporch 		   = 16;
		params->dsi.vertical_active_line		   = FRAME_HEIGHT;

		params->dsi.horizontal_sync_active		   = 4;
		params->dsi.horizontal_backporch		   = 32;
		params->dsi.horizontal_frontporch		   = 32;
		params->dsi.horizontal_active_pixel 	   = FRAME_WIDTH;

	
		// params->dsi.ssc_disable = 1;
	
		params->dsi.PLL_CLOCK					   = 226;  /* this value must be in MTK suggested table */
	
}


static void lcm_init(void)
{
#if defined(CONFIG_RUNYEE_LCM_BIAS_SUPPORT)
	lcm_bias_enable();
	MDELAY(20);
#endif
	SET_RESET_PIN(1);  //NOTE:should reset LCM firstly
	MDELAY(10);
	SET_RESET_PIN(0);
	MDELAY(50);
	SET_RESET_PIN(1);
	MDELAY(120); 

    push_table(ft8613_hd720_dsi_vdo_xld, sizeof(ft8613_hd720_dsi_vdo_xld) / sizeof(struct LCM_setting_table), 1);
	need_set_lcm_addr = 1;
    LCD_DEBUG("kernel:tm_ft8613_lcm_init\n");
}


static void lcm_suspend(void)
{
    push_table(lcm_deep_sleep_mode_in_setting, sizeof(lcm_deep_sleep_mode_in_setting) / sizeof(struct LCM_setting_table), 1);

#if defined(CONFIG_RUNYEE_LCM_BIAS_SUPPORT)
	lcm_bias_disable();
	MDELAY(20);
#endif	
	
    LCD_DEBUG("kernel:tm_ft8613_lcm_suspend\n");
	//SET_RESET_PIN(0);
	//MDELAY(10); 
}
static void lcm_resume(void)
{
	lcm_init();
    LCD_DEBUG("kernel:tm_ft8613_lcm_resume\n");

}

#if (LCM_DSI_CMD_MODE)
static void lcm_update(unsigned int x, unsigned int y,
                       unsigned int width, unsigned int height)
{
	unsigned int x0 = x;
	unsigned int y0 = y;
	unsigned int x1 = x0 + width - 1;
	unsigned int y1 = y0 + height - 1;

	unsigned char x0_MSB = ((x0>>8)&0xFF);
	unsigned char x0_LSB = (x0&0xFF);
	unsigned char x1_MSB = ((x1>>8)&0xFF);
	unsigned char x1_LSB = (x1&0xFF);
	unsigned char y0_MSB = ((y0>>8)&0xFF);
	unsigned char y0_LSB = (y0&0xFF);
	unsigned char y1_MSB = ((y1>>8)&0xFF);
	unsigned char y1_LSB = (y1&0xFF);

	unsigned int data_array[16];

	// need update at the first time
	if(need_set_lcm_addr)
	{
		data_array[0]= 0x00053902;
		data_array[1]= (x1_MSB<<24)|(x0_LSB<<16)|(x0_MSB<<8)|0x2a;
		data_array[2]= (x1_LSB);
		dsi_set_cmdq(data_array, 3, 1);
		
		data_array[0]= 0x00053902;
		data_array[1]= (y1_MSB<<24)|(y0_LSB<<16)|(y0_MSB<<8)|0x2b;
		data_array[2]= (y1_LSB);
		dsi_set_cmdq(data_array, 3, 1);
		
		//need_set_lcm_addr = 0;
	}
	
	data_array[0]= 0x002c3909;
	dsi_set_cmdq(data_array, 1, 0);

}
#endif

static unsigned int lcm_compare_id(void)
{
#if 1
		unsigned int id = 0;
		unsigned char buffer[10];
		unsigned int array[16];
		
		SET_RESET_PIN(1);  //NOTE:should reset LCM firstly
		MDELAY(10);
		SET_RESET_PIN(0);
		MDELAY(50);
		SET_RESET_PIN(1);
		MDELAY(120);

		//lcm_bias_enable();
		//MDELAY(20);		
	
		array[0] = 0x00063700; // read id return two byte,version and id
		dsi_set_cmdq(array, 1, 1);
		
		read_reg_v2(0xA1, buffer, 6);
		MDELAY(10);
	
		id = (buffer[2] << 8) | buffer[3]; 
	
#ifdef BUILD_LK
		printf("[LK]------ft8613 read id =	0x%x, 0x%x---------\n", buffer[2], buffer[3]);
#else
		printk("[KERNEL]------ft8613 read id =	0x%x, 0x%x---------\n", buffer[2], buffer[3]);
#endif
	
		return (LCM_FT8613_ID == id) ? 1 : 0;
#else
		return 1;
#endif	
}

LCM_DRIVER ft8613_hd720_dsi_vdo_xld_lcm_drv = 
{
    .name           = "ft8613_hd720_dsi_vdo_xld",
    .set_util_funcs = lcm_set_util_funcs,
    .get_params     = lcm_get_params,
    .init           = lcm_init,
    .suspend        = lcm_suspend,
    .resume         = lcm_resume,
    .compare_id     = lcm_compare_id,
#if (LCM_DSI_CMD_MODE)
	 .update = lcm_update,
#endif   
};
