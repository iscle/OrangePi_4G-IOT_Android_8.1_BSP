/*
 * Copyright (C) 2015 MediaTek Inc.
 *
 * This file is free software; you can redistribute it and/or modify
 * it under the terms of version 2 of the GNU General Public License
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */
#include <linux/types.h>
#include <linux/init.h>		/* For init/exit macros */
#include <linux/module.h>	/* For MODULE_ marcros  */
#include <linux/platform_device.h>
#include <linux/i2c.h>
#include <linux/slab.h>
#ifdef CONFIG_OF
#include <linux/of.h>
#include <linux/of_irq.h>
#include <linux/of_address.h>
#endif
#include <mt-plat/charging.h>
#include "bq24157.h"

#if 0
#include <ontim/ontim_dev_dgb.h>
static  char charge_ic_vendor_name[50] = "bq24157";
DEV_ATTR_DECLARE(charge_ic)
DEV_ATTR_DEFINE("vendor", charge_ic_vendor_name)
DEV_ATTR_DECLARE_END;
ONTIM_DEBUG_DECLARE_AND_INIT(charge_ic, charge_ic, 8);
#endif

#define bq24157_SLAVE_ADDR_WRITE_ERROR   0xD8
#define bq24157_SLAVE_ADDR_READ    0xD5
#define bq24157_SLAVE_ADDR_WRITE   0xD4

#ifdef BQ24157_BUSNUM
#undef BQ24157_BUSNUM
#endif
#define BQ24157_BUSNUM	3

static struct i2c_client *new_client;
static const struct i2c_device_id bq24157_i2c_id[] = { {"bq24157", 0}, {} };

static int bq24157_driver_probe(struct i2c_client *client, const struct i2c_device_id *id);

#ifdef CONFIG_OF
static const struct of_device_id bq24157_of_match[] = {
	{.compatible = "bq24157",},
	{},
};

MODULE_DEVICE_TABLE(of, bq24157_of_match);
#endif

static struct i2c_driver bq24157_driver = {
	.driver = {
		   .name = "bq24157",
#ifdef CONFIG_OF
		   .of_match_table = bq24157_of_match,
#endif
	},
	.probe = bq24157_driver_probe,
	.id_table = bq24157_i2c_id,
};

/**********************************************************
  *
  *   [Global Variable]
  *
  *********************************************************/
unsigned char bq24157_reg[bq24157_REG_NUM] = { 0 };

static DEFINE_MUTEX(bq24157_i2c_access);

int g_bq24157_hw_exist = -1;

/**********************************************************
  *
  *   [I2C Function For Read/Write bq24157]
  *
  *********************************************************/
int bq24157_read_byte(unsigned char cmd, unsigned char *returnData)
{
	char cmd_buf[1] = { 0x00 };
	char readData = 0;
	int ret = 0;

	mutex_lock(&bq24157_i2c_access);

	new_client->ext_flag =
	    ((new_client->ext_flag) & I2C_MASK_FLAG) | I2C_WR_FLAG | I2C_DIRECTION_FLAG;

	cmd_buf[0] = cmd;
	ret = i2c_master_send(new_client, &cmd_buf[0], (1 << 8 | 1));
	if (ret < 0) {
		new_client->ext_flag = 0;

		mutex_unlock(&bq24157_i2c_access);
		return 0;
	}

	readData = cmd_buf[0];
	*returnData = readData;

	new_client->ext_flag = 0;

	mutex_unlock(&bq24157_i2c_access);
	return 1;
}

int bq24157_write_byte(unsigned char cmd, unsigned char writeData)
{
	char write_data[2] = { 0 };
	int ret = 0;

	mutex_lock(&bq24157_i2c_access);

	write_data[0] = cmd;
	write_data[1] = writeData;

	new_client->ext_flag = ((new_client->ext_flag) & I2C_MASK_FLAG) | I2C_DIRECTION_FLAG;

	ret = i2c_master_send(new_client, write_data, 2);
	if (ret < 0) {

		new_client->ext_flag = 0;
		mutex_unlock(&bq24157_i2c_access);
		return 0;
	}

	new_client->ext_flag = 0;
	mutex_unlock(&bq24157_i2c_access);
	return 1;
}

/**********************************************************
  *
  *   [Read / Write Function]
  *
  *********************************************************/
unsigned int bq24157_read_interface(unsigned char RegNum, unsigned char *val, unsigned char MASK,
				  unsigned char SHIFT)
{
	unsigned char bq24157_reg = 0;
	int ret = 0;

	ret = bq24157_read_byte(RegNum, &bq24157_reg);

	battery_log(BAT_LOG_FULL, "[bq24157_read_interface] Reg[%x]=0x%x\n", RegNum, bq24157_reg);

	bq24157_reg &= (MASK << SHIFT);
	*val = (bq24157_reg >> SHIFT);

	/* battery_log(BAT_LOG_CRTI, "[bq24157_read_interface] val=0x%x\n", *val);*/

	return ret;
}

unsigned int bq24157_config_interface(unsigned char RegNum, unsigned char val, unsigned char MASK,
				  unsigned char SHIFT)
{
	unsigned char bq24157_reg = 0;
	int ret = 0;

	ret = bq24157_read_byte(RegNum, &bq24157_reg);
	battery_log(BAT_LOG_FULL, "[bq24157_config_interface] Reg[%x]=0x%x\n", RegNum, bq24157_reg);

	bq24157_reg &= ~(MASK << SHIFT);
	bq24157_reg |= (val << SHIFT);

	if (RegNum == bq24157_CON4 && val == 1 && MASK == CON4_RESET_MASK
	    && SHIFT == CON4_RESET_SHIFT) {
		/* RESET bit */
	} else if (RegNum == bq24157_CON4) {
		bq24157_reg &= ~0x80;	/* RESET bit read returs 1, so clear it */
		battery_log(BAT_LOG_CRTI, "[bq24157_config_interface] bq24157_reg:0x%x; line:%d\n",
			bq24157_reg, __LINE__);
	}

	ret = bq24157_write_byte(RegNum, bq24157_reg);
	battery_log(BAT_LOG_FULL, "[bq24157_config_interface] write Reg[%x]=0x%x\n", RegNum,
		    bq24157_reg);

	return ret;
}

/* write one register directly */
unsigned int bq24157_reg_config_interface(unsigned char RegNum, unsigned char val)
{
	int ret = 0;

	ret = bq24157_write_byte(RegNum, val);

	return ret;
}

/**********************************************************
  *
  *   [Internal Function]
  *
  *********************************************************/
/* CON0 */

void bq24157_set_tmr_rst(unsigned int val)
{
	unsigned int ret = 0;

	ret = bq24157_config_interface((unsigned char) (bq24157_CON0),
				       (unsigned char) (val),
				       (unsigned char) (CON0_TMR_RST_MASK),
				       (unsigned char) (CON0_TMR_RST_SHIFT)
	    );
}

unsigned int bq24157_get_otg_status(void)
{
	unsigned int ret = 0;
	unsigned char val = 0;

	ret = bq24157_read_interface((unsigned char) (bq24157_CON0),
				     (&val), (unsigned char) (CON0_OTG_MASK),
				     (unsigned char) (CON0_OTG_SHIFT)
	    );
	return val;
}

void bq24157_set_en_stat(unsigned int val)
{
	unsigned int ret = 0;

	ret = bq24157_config_interface((unsigned char) (bq24157_CON0),
				       (unsigned char) (val),
				       (unsigned char) (CON0_EN_STAT_MASK),
				       (unsigned char) (CON0_EN_STAT_SHIFT)
	    );
}

unsigned int bq24157_get_chip_status(void)
{
	unsigned int ret = 0;
	unsigned char val = 0;

	ret = bq24157_read_interface((unsigned char) (bq24157_CON0),
				     (&val), (unsigned char) (CON0_STAT_MASK),
				     (unsigned char) (CON0_STAT_SHIFT)
	    );
	return val;
}

unsigned int bq24157_get_boost_status(void)
{
	unsigned int ret = 0;
	unsigned char val = 0;

	ret = bq24157_read_interface((unsigned char) (bq24157_CON0),
				     (&val), (unsigned char) (CON0_BOOST_MASK),
				     (unsigned char) (CON0_BOOST_SHIFT)
	    );
	return val;
}

unsigned int bq24157_get_fault_status(void)
{
	unsigned int ret = 0;
	unsigned char val = 0;

	ret = bq24157_read_interface((unsigned char) (bq24157_CON0),
				     (&val), (unsigned char) (CON0_FAULT_MASK),
				     (unsigned char) (CON0_FAULT_SHIFT)
	    );
	return val;
}

/* CON1 */

void bq24157_set_input_charging_current(unsigned int val)
{
	unsigned int ret = 0;

	ret = bq24157_config_interface((unsigned char) (bq24157_CON1),
				       (unsigned char) (val),
				       (unsigned char) (CON1_LIN_LIMIT_MASK),
				       (unsigned char) (CON1_LIN_LIMIT_SHIFT)
	    );
}

void bq24157_set_v_low(unsigned int val)
{
	unsigned int ret = 0;

	ret = bq24157_config_interface((unsigned char) (bq24157_CON1),
				       (unsigned char) (val),
				       (unsigned char) (CON1_LOW_V_MASK),
				       (unsigned char) (CON1_LOW_V_SHIFT)
	    );
}

void bq24157_set_te(unsigned int val)
{
	unsigned int ret = 0;

	ret = bq24157_config_interface((unsigned char) (bq24157_CON1),
				       (unsigned char) (val),
				       (unsigned char) (CON1_TE_MASK),
				       (unsigned char) (CON1_TE_SHIFT)
	    );
}

void bq24157_set_ce(unsigned int val)
{
	unsigned int ret = 0;

	ret = bq24157_config_interface((unsigned char) (bq24157_CON1),
				       (unsigned char) (val),
				       (unsigned char) (CON1_CE_MASK),
				       (unsigned char) (CON1_CE_SHIFT)
	    );
}

void bq24157_set_hz_mode(unsigned int val)
{
	unsigned int ret = 0;

	ret = bq24157_config_interface((unsigned char) (bq24157_CON1),
				       (unsigned char) (val),
				       (unsigned char) (CON1_HZ_MODE_MASK),
				       (unsigned char) (CON1_HZ_MODE_SHIFT)
	    );
}

void bq24157_set_opa_mode(unsigned int val)
{
	unsigned int ret = 0;

	ret = bq24157_config_interface((unsigned char) (bq24157_CON1),
				       (unsigned char) (val),
				       (unsigned char) (CON1_OPA_MODE_MASK),
				       (unsigned char) (CON1_OPA_MODE_SHIFT)
	    );
}

/* CON2 */

void bq24157_set_oreg(unsigned int val)
{
	unsigned int ret = 0;

	ret = bq24157_config_interface((unsigned char) (bq24157_CON2),
				       (unsigned char) (val),
				       (unsigned char) (CON2_OREG_MASK),
				       (unsigned char) (CON2_OREG_SHIFT)
	    );
}

void bq24157_set_otg_pl(unsigned int val)
{
	unsigned int ret = 0;

	ret = bq24157_config_interface((unsigned char) (bq24157_CON2),
				       (unsigned char) (val),
				       (unsigned char) (CON2_OTG_PL_MASK),
				       (unsigned char) (CON2_OTG_PL_SHIFT)
	    );
}

void bq24157_set_otg_en(unsigned int val)
{
	unsigned int ret = 0;

	ret = bq24157_config_interface((unsigned char) (bq24157_CON2),
				       (unsigned char) (val),
				       (unsigned char) (CON2_OTG_EN_MASK),
				       (unsigned char) (CON2_OTG_EN_SHIFT)
	    );
}

/* CON3 */

unsigned int bq24157_get_vender_code(void)
{
	unsigned int ret = 0;
	unsigned char val = 0;

	ret = bq24157_read_interface((unsigned char) (bq24157_CON3),
				     (&val), (unsigned char) (CON3_VENDER_CODE_MASK),
				     (unsigned char) (CON3_VENDER_CODE_SHIFT)
	    );
	return val;
}

unsigned int bq24157_get_pn(void)
{
	unsigned int ret = 0;
	unsigned char val = 0;

	ret = bq24157_read_interface((unsigned char) (bq24157_CON3),
				     (&val), (unsigned char) (CON3_PIN_MASK),
				     (unsigned char) (CON3_PIN_SHIFT)
	    );
	return val;
}

unsigned int bq24157_get_revision(void)
{
	unsigned int ret = 0;
	unsigned char val = 0;

	ret = bq24157_read_interface((unsigned char) (bq24157_CON3),
				     (&val), (unsigned char) (CON3_REVISION_MASK),
				     (unsigned char) (CON3_REVISION_SHIFT)
	    );
	return val;
}

/* CON4 */

void bq24157_set_reset(unsigned int val)
{
	unsigned int ret = 0;

	ret = bq24157_config_interface((unsigned char) (bq24157_CON4),
				       (unsigned char) (val),
				       (unsigned char) (CON4_RESET_MASK),
				       (unsigned char) (CON4_RESET_SHIFT)
	    );
}

void bq24157_set_iocharge(unsigned int val)
{
	unsigned int ret = 0;

	ret = bq24157_config_interface((unsigned char) (bq24157_CON4),
				       (unsigned char) (val),
				       (unsigned char) (CON4_I_CHR_MASK),
				       (unsigned char) (CON4_I_CHR_SHIFT)
	    );
}

void bq24157_set_iterm(unsigned int val)
{
	unsigned int ret = 0;

	ret = bq24157_config_interface((unsigned char) (bq24157_CON4),
				       (unsigned char) (val),
				       (unsigned char) (CON4_I_TERM_MASK),
				       (unsigned char) (CON4_I_TERM_SHIFT)
	    );
}

/* CON5 */

void bq24157_set_dis_vreg(unsigned int val)
{
	unsigned int ret = 0;

	ret = bq24157_config_interface((unsigned char) (bq24157_CON5),
				       (unsigned char) (val),
				       (unsigned char) (CON5_DIS_VREG_MASK),
				       (unsigned char) (CON5_DIS_VREG_SHIFT)
	    );
}

void bq24157_set_io_level(unsigned int val)
{
	unsigned int ret = 0;

	ret = bq24157_config_interface((unsigned char) (bq24157_CON5),
				       (unsigned char) (val),
				       (unsigned char) (CON5_IO_LEVEL_MASK),
				       (unsigned char) (CON5_IO_LEVEL_SHIFT)
	    );
}

unsigned int bq24157_get_sp_status(void)
{
	unsigned int ret = 0;
	unsigned char val = 0;

	ret = bq24157_read_interface((unsigned char) (bq24157_CON5),
				     (&val), (unsigned char) (CON5_SP_STATUS_MASK),
				     (unsigned char) (CON5_SP_STATUS_SHIFT)
	    );
	return val;
}

unsigned int bq24157_get_en_level(void)
{
	unsigned int ret = 0;
	unsigned char val = 0;

	ret = bq24157_read_interface((unsigned char) (bq24157_CON5),
				     (&val), (unsigned char) (CON5_EN_LEVEL_MASK),
				     (unsigned char) (CON5_EN_LEVEL_SHIFT)
	    );
	return val;
}

void bq24157_set_vsp(unsigned int val)
{
	unsigned int ret = 0;

	ret = bq24157_config_interface((unsigned char) (bq24157_CON5),
				       (unsigned char) (val),
				       (unsigned char) (CON5_VSP_MASK),
				       (unsigned char) (CON5_VSP_SHIFT)
	    );
}

/* CON6 */

void bq24157_set_i_safe(unsigned int val)
{
	unsigned int ret = 0;

	ret = bq24157_config_interface((unsigned char) (bq24157_CON6),
				       (unsigned char) (val),
				       (unsigned char) (CON6_ISAFE_MASK),
				       (unsigned char) (CON6_ISAFE_SHIFT)
	    );
}

void bq24157_set_v_safe(unsigned int val)
{
	unsigned int ret = 0;

	ret = bq24157_config_interface((unsigned char) (bq24157_CON6),
				       (unsigned char) (val),
				       (unsigned char) (CON6_VSAFE_MASK),
				       (unsigned char) (CON6_VSAFE_SHIFT)
	    );
}

/**********************************************************
  *
  *   [Internal Function]
  *
  *********************************************************/
void bq24157_hw_component_detect(void)
{
	unsigned int ret = 0;
	unsigned char val = 0;

	ret = bq24157_read_interface(0x03, &val, 0xFF, 0x0);

	if (val == 0x51 || val == 0x41)
		g_bq24157_hw_exist = 0;
	else
		g_bq24157_hw_exist = 1;

	battery_log(BAT_LOG_CRTI, "[bq24157_hw_component_detect] exist=%d, Reg[03]=0x%x\n", g_bq24157_hw_exist, val);
}

int is_bq24157_exist(void)
{
	battery_log(BAT_LOG_CRTI, "[is_bq24157_exist] g_bq24157_hw_exist=%d\n", g_bq24157_hw_exist);

	return g_bq24157_hw_exist;
}

void bq24157_dump_register(void)
{
	int i = 0;

	battery_log(BAT_LOG_CRTI, "[bq24157] ");
	for (i = 0; i < bq24157_REG_NUM; i++) {
		bq24157_read_byte(i, &bq24157_reg[i]);
		battery_log(BAT_LOG_CRTI, "[0x%x]=0x%x ", i, bq24157_reg[i]);
	}
	battery_log(BAT_LOG_CRTI, "\n");
}

static int bq24157_driver_probe(struct i2c_client *client, const struct i2c_device_id *id)
{
	int ret = 0;

	new_client = client;
	new_client->addr = bq24157_SLAVE_ADDR_WRITE;
	new_client->addr =  new_client->addr >> 1;

	bq24157_hw_component_detect();
	bq24157_dump_register();
	if (is_bq24157_exist() == 0)
		chargin_hw_init_done = KAL_TRUE;
	else
		ret = -1;

	battery_log(BAT_LOG_CRTI, "bq24157_driver_probe  line=%d\n", __LINE__);

	return ret;
}

/**********************************************************
  *
  *   [platform_driver API]
  *
  *********************************************************/
unsigned char g_reg_value_bq24157 = 0;
static ssize_t show_bq24157_access(struct device *dev, struct device_attribute *attr, char *buf)
{
	battery_log(BAT_LOG_CRTI, "[show_bq24157_access] 0x%x\n", g_reg_value_bq24157);
	return sprintf(buf, "%u\n", g_reg_value_bq24157);
}

static ssize_t store_bq24157_access(struct device *dev, struct device_attribute *attr,
				    const char *buf, size_t size)
{
	int ret = 0;
	char *pvalue = NULL, *addr, *val;
	unsigned int reg_value = 0;
	unsigned int reg_address = 0;

	battery_log(BAT_LOG_CRTI, "[store_bq24157_access]\n");

	if (buf != NULL && size != 0) {

		pvalue = (char *)buf;
		if (size > 3) {
			addr = strsep(&pvalue, " ");
			ret = kstrtou32(addr, 16, (unsigned int *)&reg_address);
		} else
			ret = kstrtou32(pvalue, 16, (unsigned int *)&reg_address);

		if (size > 3) {
			val = strsep(&pvalue, " ");
			ret = kstrtou32(val, 16, (unsigned int *)&reg_value);

			battery_log(BAT_LOG_CRTI,
			    "[store_bq24157_access] write bq24157 reg 0x%x with value 0x%x !\n",
			     reg_address, reg_value);
			ret = bq24157_config_interface(reg_address, reg_value, 0xFF, 0x0);
		} else {
			ret = bq24157_read_interface(reg_address, &g_reg_value_bq24157, 0xFF, 0x0);
			battery_log(BAT_LOG_CRTI,
			    "[store_bq24157_access] read bq24157 reg 0x%x with value 0x%x !\n",
			     reg_address, g_reg_value_bq24157);
			battery_log(BAT_LOG_CRTI,
			    "[store_bq24157_access] Please use \"cat bq24157_access\" to get value\r\n");
		}
	}
	return size;
}

static DEVICE_ATTR(bq24157_access, 0664, show_bq24157_access, store_bq24157_access);	/* 664 */

static int bq24157_user_space_probe(struct platform_device *dev)
{
	int ret_device_file = 0;

	battery_log(BAT_LOG_CRTI, "******** bq24157_user_space_probe!! ********\n");

	ret_device_file = device_create_file(&(dev->dev), &dev_attr_bq24157_access);

	return 0;
}

struct platform_device bq24157_user_space_device = {
	.name = "bq24157-user",
	.id = -1,
};

static struct platform_driver bq24157_user_space_driver = {
	.probe = bq24157_user_space_probe,
	.driver = {
		   .name = "bq24157-user",
	},
};

static struct i2c_board_info i2c_bq24157 __initdata = { I2C_BOARD_INFO("bq24157",
							(bq24157_SLAVE_ADDR_WRITE_ERROR >> 1))};
static int __init bq24157_init(void)
{
	int ret = 0;
	struct device_node *node = of_find_compatible_node(NULL, NULL, "bq24157");

	battery_log(BAT_LOG_CRTI, "[bq24157_init] init start\n");

	if (!node)
		i2c_register_board_info(BQ24157_BUSNUM, &i2c_bq24157, 1);

	if (i2c_add_driver(&bq24157_driver) != 0) {
		battery_log(BAT_LOG_CRTI,
			    "[bq24157_init] failed to register bq24157 i2c driver.\n");
	} else {
		battery_log(BAT_LOG_CRTI,
			    "[bq24157_init] Success to register bq24157 i2c driver.\n");
	}

	/* bq24157 user space access interface */
	ret = platform_device_register(&bq24157_user_space_device);
	if (ret) {
		battery_log(BAT_LOG_CRTI, "****[bq24157_init] Unable to device register(%d)\n",
			    ret);
		return ret;
	}
	ret = platform_driver_register(&bq24157_user_space_driver);
	if (ret) {
		battery_log(BAT_LOG_CRTI, "****[bq24157_init] Unable to register driver (%d)\n",
			    ret);
		return ret;
	}

	return 0;
}

static void __exit bq24157_exit(void)
{
	i2c_del_driver(&bq24157_driver);
}

subsys_initcall(bq24157_init);

MODULE_LICENSE("GPL");
MODULE_DESCRIPTION("I2C bq24157 Driver");
MODULE_AUTHOR("James Lo<james.lo@mediatek.com>");
