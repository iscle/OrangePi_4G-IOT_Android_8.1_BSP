#ifndef BUILD_LK
#include <linux/string.h>
#include <linux/kernel.h>
#endif

#include "lcm_drv.h"
/* --------------------------------------------------------------------------- */
/* Local Constants */
/* --------------------------------------------------------------------------- */

#define FRAME_WIDTH  										(720)
#define FRAME_HEIGHT 										(1280)

#define REGFLAG_DELAY             							0XFFE
#define REGFLAG_END_OF_TABLE      							0xFFF   // END OF REGISTERS MARKER

//#define LCM_DEBUG
#if defined(BUILD_LK)
	#if defined(BUILD_LK)
	#define LCM_LOG(fmt, args...)    printf(fmt, ##args)
	#else
	#define LCM_LOG(fmt, args...)    printk(fmt, ##args)	
	#endif
#else
#define LCM_LOG(fmt, args...)	 printk(fmt, ##args)	
#endif

#ifndef TRUE
    #define   TRUE     1
#endif
 
#ifndef FALSE
    #define   FALSE    0
#endif
// ---------------------------------------------------------------------------
//  Local Variables
// ---------------------------------------------------------------------------

static LCM_UTIL_FUNCS lcm_util = {0};

#define SET_RESET_PIN(v)    								(lcm_util.set_reset_pin((v)))

#define UDELAY(n) 											(lcm_util.udelay(n))
#define MDELAY(n) 											(lcm_util.mdelay(n))


// ---------------------------------------------------------------------------
//  Local Functions
// ---------------------------------------------------------------------------

#define dsi_set_cmdq_V2(cmd, count, ppara, force_update)	lcm_util.dsi_set_cmdq_V2(cmd, count, ppara, force_update)
#define dsi_set_cmdq(pdata, queue_size, force_update)		lcm_util.dsi_set_cmdq(pdata, queue_size, force_update)
#define wrtie_cmd(cmd)										lcm_util.dsi_write_cmd(cmd)
#define write_regs(addr, pdata, byte_nums)					lcm_util.dsi_write_regs(addr, pdata, byte_nums)
#define read_reg					 						lcm_util.dsi_read_reg()
#define read_reg_v2(cmd, buffer, buffer_size)				lcm_util.dsi_dcs_read_lcm_reg_v2(cmd, buffer, buffer_size)

struct LCM_setting_table {
    unsigned cmd;
    unsigned char count;
    unsigned char para_list[64];
};


static struct LCM_setting_table lcm_initialization_setting[] = {
	{0xFF, 4, {0xAA,0x55,0xA5,0x80}},
	{0x6F, 2, {0x11,0x00}},
	{0xF7, 2, {0x20,0x00}},
	{0x6F, 1, {0x06}},
	{0xF7, 1, {0xA0}},
	{0x6F, 1, {0x19}},
	{0xF7, 1, {0x12}},
	{0xF0, 5, {0x55,0xAA,0x52,0x08,0x00}},
	{0xFF, 4, {0xAA,0x55,0xA5,0x80}},
	{0x6F, 2, {0x11,0x00}},
	{0xF7, 2, {0x20,0x00}},
	{0x6F, 1, {0x02}},
	{0xB8, 1, {0x08}},
	{0xBB, 2, {0x74,0x44}},
	{0xBD, 5, {0x01,0xAC,0x10,0x10,0x01}},
	{0xBC, 2, {0x00,0x00}},
	{0xB6, 1, {0x08}},
	{0xB1, 2, {0x6C,0x21}},
	{0xC8, 1, {0x80}},
	{0xF0, 5, {0x55,0xAA,0x52,0x08,0x01}},
	{0xB0, 2, {0x05,0x05}},
	{0xB1, 2, {0x05,0x05}},
	{0xBC, 2, {0xb8,0x00}},
	{0xBD, 2, {0xb8,0x00}},
	{0xCA, 1, {0x00}},
	{0xC0, 1, {0x04}},
	{0xBE, 1, {0x5C}},
	{0xB3, 2, {0x28,0x28}},
	{0xB4, 2, {0x0F,0x0F}},
	{0xB9, 2, {0x34,0x34}},
	{0xBA, 2, {0x14,0x14}},
	{0xF0, 5, {0x55,0xAA,0x52,0x08,0x06}},
	{0xB0, 2, {0x29,0x2A}},
	{0xB1, 2, {0x10,0x12}},
	{0xB2, 2, {0x14,0x16}},
	{0xB3, 2, {0x18,0x1A}},
	{0xB4, 2, {0x08,0x0A}},
	{0xB5, 2, {0x2E,0x2E}},
	{0xB6, 2, {0x2E,0x2E}},
	{0xB7, 2, {0x2E,0x2E}},
	{0xB8, 2, {0x2E,0x00}},
	{0xB9, 2, {0x2E,0x2E}},
	{0xBA, 2, {0x2E,0x2E}},
	{0xBB, 2, {0x01,0x2E}},
	{0xBC, 2, {0x2E,0x2E}},
	{0xBD, 2, {0x2E,0x2E}},
	{0xBE, 2, {0x2E,0x2E}},
	{0xBF, 2, {0x0B,0x09}},
	{0xC0, 2, {0x1B,0x19}},
	{0xC1, 2, {0x17,0x15}},
	{0xC2, 2, {0x13,0x11}},
	{0xC3, 2, {0x2A,0x29}},
	{0xE5, 2, {0x2E,0x2E}},
	{0xC4, 2, {0x29,0x2A}},
	{0xC5, 2, {0x1B,0x19}},
	{0xC6, 2, {0x17,0x15}},
	{0xC7, 2, {0x13,0x11}},
	{0xC8, 2, {0x01,0x0B}},
	{0xC9, 2, {0x2E,0x2E}},
	{0xCA, 2, {0x2E,0x2E}},
	{0xCB, 2, {0x2E,0x2E}},
	{0xCC, 2, {0x2E,0x09}},
	{0xCD, 2, {0x2E,0x2E}},
	{0xCE, 2, {0x2E,0x2E}},
	{0xCF, 2, {0x08,0x2E}},
	{0xD0, 2, {0x2E,0x2E}},
	{0xD1, 2, {0x2E,0x2E}},
	{0xD2, 2, {0x2E,0x2E}},
	{0xD3, 2, {0x0A,0x00}},
	{0xD4, 2, {0x10,0x12}},
	{0xD5, 2, {0x14,0x16}},
	{0xD6, 2, {0x18,0x1A}},
	{0xD7, 2, {0x2A,0x29}},
	{0xE6, 2, {0x2E,0x2E}},
	{0xD8, 5, {0x00,0x00,0x00,0x00,0x00}},
	{0xD9, 5, {0x00,0x00,0x00,0x00,0x00}},
	{0xE7, 1, {0x00}},
	{0xF0, 5, {0x55,0xAA,0x52,0x08,0x03}},
	{0xB0, 2, {0x00,0x00}},
	{0xB1, 2, {0x00,0x00}},
	{0xB2, 5, {0x05,0x00,0x00,0x00,0x00}},
	{0xB6, 5, {0x05,0x00,0x00,0x00,0x00}},
	{0xB7, 5, {0x05,0x00,0x00,0x00,0x00}},
	{0xBA, 5, {0x57,0x00,0x00,0x00,0x00}},
	{0xBB, 5, {0x57,0x00,0x00,0x00,0x00}},
	{0xC0, 4, {0x00,0x00,0x00,0x00}},
	{0xC1, 4, {0x00,0x00,0x00,0x00}},
	{0xC4, 1, {0x60}},
	{0xC5, 1, {0x40}},
	{0xF0, 5, {0x55,0xAA,0x52,0x08,0x05}},
	{0xBD, 5, {0x03,0x01,0x03,0x03,0x03}},
	{0xB0, 2, {0x17,0x06}},
	{0xB1, 2, {0x17,0x06}},
	{0xB2, 2, {0x17,0x06}},
	{0xB3, 2, {0x17,0x06}},
	{0xB4, 2, {0x17,0x06}},
	{0xB5, 2, {0x17,0x06}},
	{0xB8, 1, {0x00}},
	{0xB9, 1, {0x00}},
	{0xBA, 1, {0x00}},
	{0xBB, 1, {0x02}},
	{0xBC, 1, {0x00}},
	{0xC0, 1, {0x07}},
	{0xC4, 1, {0x80}},
	{0xC5, 1, {0xA4}},
	{0xC8, 2, {0x05,0x30}},
	{0xC9, 2, {0x01,0x31}},
	{0xCC, 3, {0x00,0x00,0x3C}},
	{0xCD, 3, {0x00,0x00,0x3C}},
	{0xD1, 5, {0x00,0x05,0x09,0x07,0x10}},
	{0xD2, 5, {0x00,0x05,0x0E,0x07,0x10}},
	{0xE5, 1, {0x06}},
	{0xE6, 1, {0x06}},
	{0xE7, 1, {0x06}},
	{0xE8, 1, {0x06}},
	{0xE9, 1, {0x06}},
	{0xEA, 1, {0x06}},
	{0xED, 1, {0x30}},
	{0x6F, 1, {0x11}},
	{0xF3, 1, {0x01}},
	{0x36, 1, {0x00}},

	{0x11,1,{0x00}},
	{REGFLAG_DELAY, 120, {}},

	{0x29,1,{0x00}},  
	{REGFLAG_DELAY, 20, {}},
	{REGFLAG_END_OF_TABLE, 0x00, {}}
};
/*
static struct LCM_setting_table lcm_sleep_out_setting[] = {
    // Sleep Out
	{0x11, 1, {0x00}},
    {REGFLAG_DELAY, 200, {}},

    // Display ON
	{0x29, 1, {0x00}},
	{REGFLAG_DELAY, 50, {}},
	{REGFLAG_END_OF_TABLE, 0x00, {}}
};
*/

static struct LCM_setting_table lcm_deep_sleep_mode_in_setting[] = {
	// Display off sequence
	{0x28, 1, {0x00}},
	{REGFLAG_DELAY, 50, {}},

    // Sleep Mode On
	{0x10, 1, {0x00}},
	{REGFLAG_DELAY, 200, {}},
	{REGFLAG_END_OF_TABLE, 0x00, {}}
};

static void push_table(struct LCM_setting_table *table, unsigned int count, unsigned char force_update)
{
	unsigned int i;

    for(i = 0; i < count; i++) {
		
        unsigned int cmd;
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

		params->dsi.mode   = SYNC_PULSE_VDO_MODE;

	
		// DSI
		/* Command mode setting */
		params->dsi.LANE_NUM				= LCM_THREE_LANE; //LCM_TWO_LANE;

		//The following defined the fomat for data coming from LCD engine.
		params->dsi.data_format.color_order = LCM_COLOR_ORDER_RGB;		
		params->dsi.data_format.trans_seq	= LCM_DSI_TRANS_SEQ_MSB_FIRST;	
		params->dsi.data_format.padding 	= LCM_DSI_PADDING_ON_LSB;		
		params->dsi.data_format.format	  = LCM_DSI_FORMAT_RGB888;
		params->dsi.PS=LCM_PACKED_PS_24BIT_RGB888;



		params->dsi.vertical_sync_active				= 4;// 3 12 2
		params->dsi.vertical_backporch					= 20;
		params->dsi.vertical_frontporch					= 20;
		params->dsi.vertical_active_line				= FRAME_HEIGHT;

		params->dsi.horizontal_sync_active				= 10;
		params->dsi.horizontal_backporch				= 70;
		params->dsi.horizontal_frontporch				= 70;
		params->dsi.horizontal_active_pixel				= FRAME_WIDTH;

		params->dsi.PLL_CLOCK = 232;

		
}

static void lcm_init(void)
{

    SET_RESET_PIN(1);
    MDELAY(10);
    SET_RESET_PIN(0);
    MDELAY(10);
    SET_RESET_PIN(1);
    MDELAY(120);
    push_table(lcm_initialization_setting, sizeof(lcm_initialization_setting) / sizeof(struct LCM_setting_table), 1);

}

static void lcm_suspend(void)
{
    SET_RESET_PIN(1);
    MDELAY(10);
    SET_RESET_PIN(0);
    MDELAY(10);//Must > 5ms
    SET_RESET_PIN(1);
    MDELAY(50);//Must > 50ms

	push_table(lcm_deep_sleep_mode_in_setting, sizeof(lcm_deep_sleep_mode_in_setting) / sizeof(struct LCM_setting_table), 1);
}

static void lcm_resume(void)
{
	lcm_init();
	
	//push_table(lcm_sleep_out_setting, sizeof(lcm_sleep_out_setting) / sizeof(struct LCM_setting_table), 1);
}  

static unsigned int lcm_compare_id(void)
{
	unsigned int id=0;
	unsigned char buffer[2];
	unsigned int array[16];  
 	
	//Do reset here
	SET_RESET_PIN(1);
	MDELAY(10);
	SET_RESET_PIN(0);
	MDELAY(20);
	SET_RESET_PIN(1);
	MDELAY(50);       
  
	array[0]=0x00063902;
	array[1]=0x52aa55f0;
	array[2]=0x00000108;
	dsi_set_cmdq(array, 3, 1);
	MDELAY(10);

	array[0] = 0x00023700;
	dsi_set_cmdq(array, 1, 1);

	//read_reg_v2(0x04, buffer, 3);//if read 0x04,should get 0x008000,that is both OK.
	read_reg_v2(0xc5, buffer,2);

	id = buffer[0]<<8 |buffer[1]; 
	      
#if defined(BUILD_LK)
	  printf("[nt35521]%s,  buffer[0]=%x,buffer[1]=%x,id = 0x%x \n", __func__,buffer[0],buffer[1], id);
#else
    printk("[nt35521]%s,  buffer[0]=%x,buffer[1]=%x,id = 0x%x \n", __func__,buffer[0],buffer[1], id);
#endif

    if(id == 0x5521)
    	return 1;
    else
    	return 0;
}

LCM_DRIVER nt35521_hd720_dsi_vdo_cmi_tps65132_lcm_drv = 
{
  .name		= "nt35521_hd720_dsi_vdo_cmi_tps65132",
	.set_util_funcs = lcm_set_util_funcs,
	.get_params     = lcm_get_params,
	.init           = lcm_init,
	.suspend        = lcm_suspend,
	.resume         = lcm_resume,
	.compare_id    = lcm_compare_id,
    };
