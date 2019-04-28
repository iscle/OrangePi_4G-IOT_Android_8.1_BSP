#ifndef BUILD_LK
#include <linux/string.h>
#endif
#include "lcm_drv.h"

#ifdef BUILD_LK
#include <platform/mt_gpio.h>
#include <platform/mt_i2c.h>
#include <platform/mt_pmic.h>
#elif defined(BUILD_UBOOT)
#include <asm/arch/mt_gpio.h>
#else
/* #include <mach/mt_pm_ldo.h> */
/* #include <mach/mt_gpio.h> */
#endif

#ifdef BUILD_LK
#define LCD_DEBUG(fmt)  dprintf(CRITICAL, fmt)
#else
#define LCD_DEBUG(fmt)  printk(fmt)
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
#define FRAME_WIDTH  										(640)
#define FRAME_HEIGHT 										(1280)


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
//update initial param for IC rm68200 0.01
static struct LCM_setting_table rm68200_hd720_dsi_video_hf[] = {
	{0xFE, 1, {0x01}},
	{0x0E, 1, {0x31}},
	{0x0F, 1, {0xC8}},
	{0x24, 1, {0xC0}},
	{0x25, 1, {0x53}},
	{0x26, 1, {0x00}},
	{0x27, 1, {0x0A}},
	{0x29, 1, {0x0A}},
	{0x2B, 1, {0xE5}},
	{0x16, 1, {0x52}},
	{0x2F, 1, {0x55}},
	{0x34, 1, {0x57}},
	{0x1B, 1, {0x00}},
	{0x12, 1, {0x0B}},
	{0x1A, 1, {0x06}},
	{0x46, 1, {0x36}}, //VCOM
	{0x52, 1, {0x78}},
	{0x53, 1, {0x00}},
	{0x54, 1, {0x78}},
	{0x55, 1, {0x00}},
	//{0x5F, 1, {0x12}},  //11=2 LANE, 12=3 LANE, 13=4 LANE(default)
	//{0x0E, 1, {0x27}},  // Ðý×ª180¶È

	{0xFE, 1, {0x03}},
	{0x00, 1, {0x05}},
	{0x01, 1, {0x16}},
	{0x02, 1, {0x09}},
	{0x03, 1, {0x0D}},
	{0x04, 1, {0x00}},
	{0x05, 1, {0x00}},
	{0x06, 1, {0x50}},
	{0x07, 1, {0x05}},
	{0x08, 1, {0x16}},
	{0x09, 1, {0x0B}},
	{0x0A, 1, {0x0F}},
	{0x0B, 1, {0x00}},
	{0x0C, 1, {0x00}},
	{0x0D, 1, {0x50}},
	{0x0E, 1, {0x04}},
	{0x0F, 1, {0x05}},
	{0x10, 1, {0x06}},
	{0x11, 1, {0x07}},
	{0x12, 1, {0x00}},
	{0x13, 1, {0x40}},
	{0x14, 1, {0x00}},
	{0x15, 1, {0xC4}},
	{0x16, 1, {0x08}},
	{0x17, 1, {0x08}},
	{0x18, 1, {0x09}},
	{0x19, 1, {0x0A}},
	{0x1A, 1, {0x0B}},
	{0x1B, 1, {0x00}},
	{0x1C, 1, {0x40}},
	{0x1D, 1, {0x00}},
	{0x1E, 1, {0x84}},
	{0x1F, 1, {0x08}},
	{0x20, 1, {0x00}},
	{0x21, 1, {0x00}},
	{0x22, 1, {0x04}},
	{0x23, 1, {0x1B}},
	{0x24, 1, {0x00}},
	{0x25, 1, {0x28}},
	{0x26, 1, {0x00}},
	{0x27, 1, {0x1F}},
	{0x28, 1, {0x00}},
	{0x29, 1, {0x28}},
	{0x2A, 1, {0x00}},
	{0x2B, 1, {0x00}},
	{0x2D, 1, {0x00}},
	{0x2F, 1, {0x00}},
	{0x30, 1, {0x00}},
	{0x31, 1, {0x00}},
	{0x32, 1, {0x00}},
	{0x33, 1, {0x00}},
	{0x34, 1, {0x00}},
	{0x35, 1, {0x00}},
	{0x36, 1, {0x00}},
	{0x37, 1, {0x00}},
	{0x38, 1, {0x00}},
	{0x39, 1, {0x00}},
	{0x3A, 1, {0x00}},
	{0x3B, 1, {0x00}},
	{0x3D, 1, {0x00}},
	{0x3F, 1, {0x00}},
	{0x40, 1, {0x00}},
	{0x3F, 1, {0x00}},
	{0x41, 1, {0x00}},
	{0x42, 1, {0x00}},
	{0x43, 1, {0x00}},
	{0x44, 1, {0x00}},
	{0x45, 1, {0x00}},
	{0x46, 1, {0x00}},
	{0x47, 1, {0x00}},
	{0x48, 1, {0x00}},
	{0x49, 1, {0x00}},
	{0x4A, 1, {0x00}},
	{0x4B, 1, {0x00}},
	{0x4C, 1, {0x00}},
	{0x4D, 1, {0x00}},
	{0x4E, 1, {0x00}},
	{0x4F, 1, {0x00}},
	{0x50, 1, {0x00}},
	{0x51, 1, {0x00}},
	{0x52, 1, {0x00}},
	{0x53, 1, {0x00}},
	{0x54, 1, {0x00}},
	{0x55, 1, {0x00}},
	{0x56, 1, {0x00}},
	{0x58, 1, {0x00}},
	{0x59, 1, {0x00}},
	{0x5A, 1, {0x00}},
	{0x5B, 1, {0x00}},
	{0x5C, 1, {0x00}},
	{0x5D, 1, {0x00}},
	{0x5E, 1, {0x00}},
	{0x5F, 1, {0x00}},
	{0x60, 1, {0x00}},
	{0x61, 1, {0x00}},
	{0x62, 1, {0x00}},
	{0x63, 1, {0x00}},
	{0x64, 1, {0x00}},
	{0x65, 1, {0x00}},
	{0x66, 1, {0x00}},
	{0x67, 1, {0x00}},
	{0x68, 1, {0x00}},
	{0x69, 1, {0x00}},
	{0x6A, 1, {0x00}},
	{0x6B, 1, {0x00}},
	{0x6C, 1, {0x00}},
	{0x6D, 1, {0x00}},
	{0x6E, 1, {0x00}},
	{0x6F, 1, {0x00}},
	{0x70, 1, {0x00}},
	{0x71, 1, {0x00}},
	{0x72, 1, {0x00}},
	{0x73, 1, {0x00}},
	{0x74, 1, {0x04}},
	{0x75, 1, {0x04}},
	{0x76, 1, {0x04}},
	{0x77, 1, {0x04}},
	{0x78, 1, {0x00}},
	{0x79, 1, {0x00}},
	{0x7A, 1, {0x00}},
	{0x7B, 1, {0x00}},
	{0x7C, 1, {0x00}},
	{0x7D, 1, {0x00}},
	{0x7E, 1, {0x86}},
	{0x7F, 1, {0x02}},
	{0x80, 1, {0x0E}},
	{0x81, 1, {0x0C}},
	{0x82, 1, {0x0A}},
	{0x83, 1, {0x08}},
	{0x84, 1, {0x3F}},
	{0x85, 1, {0x3F}},
	{0x86, 1, {0x3F}},
	{0x87, 1, {0x3F}},
	{0x88, 1, {0x3F}},
	{0x89, 1, {0x3F}},
	{0x8A, 1, {0x3F}},
	{0x8B, 1, {0x3F}},
	{0x8C, 1, {0x3F}},
	{0x8D, 1, {0x3F}},
	{0x8E, 1, {0x3F}},
	{0x8F, 1, {0x3F}},
	{0x90, 1, {0x00}},
	{0x91, 1, {0x04}},
	{0x92, 1, {0x3F}},
	{0x93, 1, {0x3F}},
	{0x94, 1, {0x3F}},
	{0x95, 1, {0x3F}},
	{0x96, 1, {0x05}},
	{0x97, 1, {0x01}},
	{0x98, 1, {0x3F}},
	{0x99, 1, {0x3F}},
	{0x9A, 1, {0x3F}},
	{0x9B, 1, {0x3F}},
	{0x9C, 1, {0x3F}},
	{0x9D, 1, {0x3F}},
	{0x9E, 1, {0x3F}},
	{0x9F, 1, {0x3F}},
	{0xA0, 1, {0x3F}},
	{0xA2, 1, {0x3F}},
	{0xA3, 1, {0x3F}},
	{0xA4, 1, {0x3F}},
	{0xA5, 1, {0x09}},
	{0xA6, 1, {0x0B}},
	{0xA7, 1, {0x0D}},
	{0xA9, 1, {0x0F}},
	{0xAA, 1, {0x03}},
	{0xAB, 1, {0x07}},
	{0xAC, 1, {0x01}},
	{0xAD, 1, {0x05}},
	{0xAE, 1, {0x0D}},
	{0xAF, 1, {0x0F}},
	{0xB0, 1, {0x09}},
	{0xB1, 1, {0x0B}},
	{0xB2, 1, {0x3F}},
	{0xB3, 1, {0x3F}},
	{0xB4, 1, {0x3F}},
	{0xB5, 1, {0x3F}},
	{0xB6, 1, {0x3F}},
	{0xB7, 1, {0x3F}},
	{0xB8, 1, {0x3F}},
	{0xB9, 1, {0x3F}},
	{0xBA, 1, {0x3F}},
	{0xBB, 1, {0x3F}},
	{0xBC, 1, {0x3F}},
	{0xBD, 1, {0x3F}},
	{0xBE, 1, {0x07}},
	{0xBF, 1, {0x03}},
	{0xC0, 1, {0x3F}},
	{0xC1, 1, {0x3F}},
	{0xC2, 1, {0x3F}},
	{0xC3, 1, {0x3F}},
	{0xC4, 1, {0x02}},
	{0xC5, 1, {0x06}},
	{0xC6, 1, {0x3F}},
	{0xC7, 1, {0x3F}},
	{0xC8, 1, {0x3F}},
	{0xC9, 1, {0x3F}},
	{0xCA, 1, {0x3F}},
	{0xCB, 1, {0x3F}},
	{0xCC, 1, {0x3F}},
	{0xCD, 1, {0x3F}},
	{0xCE, 1, {0x3F}},
	{0xCF, 1, {0x3F}},
	{0xD0, 1, {0x3F}},
	{0xD1, 1, {0x3F}},
	{0xD2, 1, {0x0A}},
	{0xD3, 1, {0x08}},
	{0xD4, 1, {0x0E}},
	{0xD5, 1, {0x0C}},
	{0xD6, 1, {0x04}},
	{0xD7, 1, {0x00}},
	{0xDC, 1, {0x02}},
	{0xDE, 1, {0x10}},
	{0xFE, 1, {0x0E}},
	{0x01, 1, {0x75}},
	{0x54, 1, {0x01}},
	{0xFE, 1, {0x04}},
	{0x62, 1, {0x0C}},
	{0x6D, 1, {0x16}},
	{0x65, 1, {0x13}},
	{0x6A, 1, {0x10}},
	{0x61, 1, {0x07}},
	{0x63, 1, {0x0D}},
	{0x64, 1, {0x05}},
	{0x66, 1, {0x0F}},
	{0x67, 1, {0x0B}},
	{0x68, 1, {0x16}},
	{0x69, 1, {0x0C}},
	{0x6B, 1, {0x07}},
	{0x6C, 1, {0x0E}},
	{0x6E, 1, {0x10}},
	{0x60, 1, {0x00}},
	{0x6F, 1, {0x00}},
	{0x72, 1, {0x0C}},
	{0x7D, 1, {0x16}},
	{0x75, 1, {0x13}},
	{0x7A, 1, {0x10}},
	{0x71, 1, {0x07}},
	{0x73, 1, {0x0D}},
	{0x74, 1, {0x05}},
	{0x76, 1, {0x0F}},
	{0x77, 1, {0x0B}},
	{0x78, 1, {0x16}},
	{0x79, 1, {0x0C}},
	{0x7B, 1, {0x07}},
	{0x7C, 1, {0x0E}},
	{0x7E, 1, {0x10}},
	{0x70, 1, {0x00}},
	{0x7F, 1, {0x00}},
	{0xFE, 1, {0x00}},
	{0x35, 1, {0x00}},
	{0x58, 1, {0x00}},
	{0x11, 0,{0x00}},
	{REGFLAG_DELAY, 200, {}},
	{0x29, 0,{0x00}},
	{REGFLAG_DELAY, 100, {}},


	// Note
	// Strongly recommend not to set Sleep out / Display On here. That will cause messed frame to be shown as later the backlight is on.


	// Setting ending by predefined flag
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

	params->dsi.mode   = BURST_VDO_MODE;	//BURST_VDO_MODE;	//SYNC_PULSE_VDO_MODE;

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
	params->dsi.vertical_backporch					= 36;
	params->dsi.vertical_frontporch					= 16;
	params->dsi.vertical_active_line				= FRAME_HEIGHT; 

	params->dsi.horizontal_sync_active				= 2;
	params->dsi.horizontal_backporch				= 36;
	params->dsi.horizontal_frontporch				= 26;
	params->dsi.horizontal_active_pixel				= FRAME_WIDTH;

	//improve clk quality
	params->dsi.PLL_CLOCK = 210; //this value 

	//params->dsi.ufoe_enable = 1;
    //params->dsi.ssc_disable = 1;

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
    //LCD_DEBUG("uboot:tm_rm68200_lcm_suspend\n");

}
static void lcm_resume(void)
{
	lcm_init();
    //LCD_DEBUG("uboot:tm_rm68200_lcm_resume\n");

}

static unsigned int lcm_compare_id(void)
{
#if 1
	//unsigned int id=0;
	unsigned char buffer[5];
	unsigned int array[4]; 
	unsigned char id_high=0;
	unsigned char id_low=0;
	unsigned int id1=0;
	unsigned int id2=0;


	SET_RESET_PIN(1);
	MDELAY(10); 
	SET_RESET_PIN(0);
	MDELAY(10); 
	SET_RESET_PIN(1);
	MDELAY(10);

	array[0]=0x01FE1500;
	dsi_set_cmdq(array,1, 1);

	array[0] = 0x00013700;
	dsi_set_cmdq(array, 1, 1);

	read_reg_v2(0xde, buffer, 1);//68
	id_high = buffer[0];
	read_reg_v2(0xdf, buffer, 1);//20
	id_low = buffer[0];
	id1 = (id_high<<8) | id_low;

#if defined(BUILD_LK)
	printf("[U-BOOT] rm68200a %s id1 = 0x%04x, id2 = 0x%04x\n", __func__, id1,id2);
#else
	printk("[KERNEL] rm68200a %s id1 = 0x%04x, id2 = 0x%04x\n", __func__, id1,id2);
#endif
	return (LCM_RM68200_ID == id1)?1:0;
#else
	return 1;
#endif
}
LCM_DRIVER rm68200_hd720_dsi_vdo_ivo_lcm_drv = 
{
    .name           = "rm68200_hd720_dsi_vdo_ivo",
    .set_util_funcs = lcm_set_util_funcs,
    .get_params     = lcm_get_params,
    .init           = lcm_init,
    .suspend        = lcm_suspend,
    .resume         = lcm_resume,
    .compare_id     = lcm_compare_id,
   
};
