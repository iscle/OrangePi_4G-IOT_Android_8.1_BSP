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
#define LCD_DEBUG(fmt)  dprintf(CRITICAL, fmt)
#else
#define LCD_DEBUG(fmt)  pr_debug(fmt)
#endif
#ifdef CONFIG_MTK_LEGACY
#include <cust_i2c.h>
#include <mach/gpio_const.h>
#include <cust_gpio_usage.h>
#endif

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
#define FRAME_HEIGHT 										(1280)

#define LCD_ID_ST7703		0x3821

#define REGFLAG_DELAY             							0xFFFC
#define REGFLAG_END_OF_TABLE      							0xFFFD   // END OF REGISTERS MARKER


#ifndef TRUE
    #define TRUE 1
#endif

#ifndef FALSE
    #define FALSE 0
#endif

#define LCM_RM68200_ID (0x6820)
// ---------------------------------------------------------------------------
//  Local Variables
// ---------------------------------------------------------------------------

struct LCM_setting_table {
    unsigned int cmd;
    unsigned char count;
    unsigned char para_list[128];
};

static struct LCM_setting_table rm68200_hd720_dsi_video_hf[] = {
  {0xB9,3,{0xF1,0x12,0x83}}, 
  
  {0xBA,27,{0x33,0x81,0x05,0xF9,0x0E,0x0E,0x00,0x00,0x00,0x00,
  		  0x00,0x00,0x00,0x00,0x44,0x25,0x00,0x91,0x0A,0x00,
  		  0x00,0x02,0x4F,0xD1,0x00,0x00,0x37}}, 
  
  {0xB8,1,{0x26}},
  
  {0xBF,3,{0x02,0x11,0x00}},
  
  
  {0xB3,10,{0x0C,0x10,0x0A,0x50,0x03,0xFF,0x00,0x00,0x00,0x00}}, 
  
  {0xC0,9,{0x73,0x73,0x50,0x50,0x00,0x00,0x08,0x70,0x00}}, 
  
  
  {0xBC,1,{0x46}}, 
  
  {0xCC,1,{0x0B}},
        
  {0xB4,1,{0x80}},  
  
  {0xB2,3,{0xC8,0x12,0x30}},
  
  {0xE3,14,{0x07,0x07,0x0B,0x0B,0x03,0x0B,0x00,0x00,0x00,0x00,0xFF,0x00,0xc0,0x10}},  
          
  
  {0xC1,12,{0x25,0x00,0x1E,0x1E,0x77,0xE1,0xFF,0xFF,0xCC,0xCC,0x77,0x77}},
  
  
  {0xB5,2,{0x0A,0x0A}}, 
  
  {0xB6,2,{0x89,0x89}}, 
  
  
  {0xE9,63,{0xC2,0x10,0x08,0x00,0x00,0x41,0xB8,0x12,0x31,0x23,
  		  0x37,0x86,0x11,0xB8,0x37,0x2A,0x00,0x00,0x0C,0x00,
  		  0x00,0x00,0x00,0x00,0x0C,0x00,0x00,0x00,0x88,0x20,
  		  0x46,0x02,0x88,0x88,0x88,0x88,0x88,0x88,0xFF,0x88,
  		  0x31,0x57,0x13,0x88,0x88,0x88,0x88,0x88,0x88,0xFF,
  		  0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00}},  
  
  
  {0xEA,61,{0x00,0x1A,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,
  		  0x00,0x00,0x8F,0x13,0x31,0x75,0x88,0x88,0x88,0x88,
  		  0x88,0x88,0xF8,0x8F,0x02,0x20,0x64,0x88,0x88,0x88,
  		  0x88,0x88,0x88,0xF8,0x00,0x00,0x00,0x00,0x00,0x00,
  		  0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,
  		  0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00,0x00}},
  
  {0xE0,34,{0x03,0x19,0x1D,0x2E,0x32,0x38,0x4A,0x3E,0x07,0x0C,0x0F,0x12,0x14,0x12,0x13,0x0F,0x16,
  		  0x03,0x19,0x1D,0x2E,0x32,0x38,0x4A,0x3E,0x07,0x0C,0x0F,0x12,0x14,0x12,0x13,0x0F,0x16}},
  		  
	{0x36,1,{0x10}},		  

	{0x11,1,{0x00}},
	{REGFLAG_DELAY, 150, {}},
	{0x29,1,{0x00}},
	{REGFLAG_DELAY, 50, {}},
	{REGFLAG_END_OF_TABLE, 0x00, {}}
	
};
				
static struct LCM_setting_table lcm_deep_sleep_mode_in_setting[] = {
    // Display off sequence
    {0x28, 1, {0x00}},
    {REGFLAG_DELAY, 20, {}},

    // Sleep Mode On
    {0x10, 1, {0x00}},
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
    params->type   = LCM_TYPE_DSI;
    params->width  = FRAME_WIDTH;
    params->height = FRAME_HEIGHT;

	  params->dsi.mode   = SYNC_PULSE_VDO_MODE;	//BURST_VDO_MODE;	

    // DSI
    /* Command mode setting */
    params->dsi.LANE_NUM				= LCM_FOUR_LANE;
    //The following defined the fomat for data coming from LCD engine.
    params->dsi.data_format.color_order = LCM_COLOR_ORDER_RGB;
    params->dsi.data_format.trans_seq   = LCM_DSI_TRANS_SEQ_MSB_FIRST;
    params->dsi.data_format.padding     = LCM_DSI_PADDING_ON_LSB;
    params->dsi.data_format.format      = LCM_DSI_FORMAT_RGB888;

   // Highly depends on LCD driver capability.
	  params->dsi.packet_size=256;
    params->dsi.PS=LCM_PACKED_PS_24BIT_RGB888;
   
	  params->dsi.vertical_sync_active				= 4;
    params->dsi.vertical_backporch					= 17;
    params->dsi.vertical_frontporch				  = 16;
    params->dsi.vertical_active_line				= FRAME_HEIGHT;
    
    params->dsi.horizontal_sync_active			= 10;
    params->dsi.horizontal_backporch				= 35;
    params->dsi.horizontal_frontporch				= 35;
	  params->dsi.PLL_CLOCK = 230; 	

}

static void lcm_init(void)
{
	SET_RESET_PIN(1);    
	MDELAY(10); 
	SET_RESET_PIN(0);
	MDELAY(10); 
	SET_RESET_PIN(1);
	MDELAY(120); 

    push_table(rm68200_hd720_dsi_video_hf, sizeof(rm68200_hd720_dsi_video_hf) / sizeof(struct LCM_setting_table), 1);
    //LCD_DEBUG("uboot:tm_rm68200_lcm_init\n");
}


static void lcm_suspend(void)
{
    push_table(lcm_deep_sleep_mode_in_setting, sizeof(lcm_deep_sleep_mode_in_setting) / sizeof(struct LCM_setting_table), 1);
    MDELAY(20); 
	  SET_RESET_PIN(0);
}
static void lcm_resume(void)
{
	lcm_init();
    //LCD_DEBUG("uboot:tm_rm68200_lcm_resume\n");

}

static unsigned int lcm_compare_id(void)
{
	unsigned char id[4];
	unsigned int lcd_id;
	unsigned int data_array[16];

	SET_RESET_PIN(1);
	MDELAY(10);
	SET_RESET_PIN(0);
	MDELAY(50);
	SET_RESET_PIN(1);
	MDELAY(120);

//	push_table(lcm_read_id, sizeof(lcm_read_id) / sizeof(struct LCM_setting_table), 1);
	data_array[0]=0x00043700;
	dsi_set_cmdq(data_array, 1, 1);

	read_reg_v2(0x04, id, 2);
	lcd_id = (id[0]<<8)|id[1];

#if defined(BUILD_LK)
	printf("[ST7703]:[%x],[%x] \n",id[0], id[1]);
#else
	printk("[ST7703]%s,  id[0]=%x,id[1]=%x,lcd_id = 0x%x \n", __func__,id[0],id[1], lcd_id);
#endif

	if(LCD_ID_ST7703==lcd_id)
		return 1;
	else
		return 0;
}

LCM_DRIVER st7703_hd720_dsi_vdo_boe_lcm_drv = 
{
    .name           = "st7703_hd720_dsi_vdo_boe",
    .set_util_funcs = lcm_set_util_funcs,
    .get_params     = lcm_get_params,
    .init           = lcm_init,
    .suspend        = lcm_suspend,
    .resume         = lcm_resume,
    .compare_id     = lcm_compare_id,
   
};
