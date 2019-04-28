/*
 * Copyright (C) 2015 MediaTek Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2 as
 * published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 */

#define LOG_TAG "SPLIT"
#include "ddp_log.h"

#include "ddp_clkmgr.h"

#include <linux/delay.h>

#include "ddp_info.h"
#include "ddp_hal.h"
#include "ddp_reg.h"
#include "primary_display.h"


static int split_clock_on(enum DISP_MODULE_ENUM module, void *handle)
{
	ddp_clk_prepare_enable(ddp_get_module_clk_id(module));

	return 0;
}

static int split_clock_off(enum DISP_MODULE_ENUM module, void *handle)
{
	ddp_clk_disable_unprepare(ddp_get_module_clk_id(module));

	return 0;
}

static int split_init(enum DISP_MODULE_ENUM module, void *handle)
{
	return 0;
}

static int split_deinit(enum DISP_MODULE_ENUM module, void *handle)
{
	return 0;
}

static int split_start(enum DISP_MODULE_ENUM module, void *handle)
{

	if (disp_helper_get_option(DISP_OPT_SHADOW_REGISTER)) {
		if (disp_helper_get_option(DISP_OPT_SHADOW_MODE) == 1) {
			/* force commit: force_commit, read working */
			DISP_REG_SET_FIELD(handle, FLD_SPLIT_FORCE_COMMIT, DISP_REG_SPLIT_SHADOW_CTRL, 0x1);
			DISP_REG_SET_FIELD(handle, FLD_SPLIT_RD_WRK_REG, DISP_REG_SPLIT_SHADOW_CTRL, 0x1);
		} else if (disp_helper_get_option(DISP_OPT_SHADOW_MODE) == 2) {
			/* bypass shadow: bypass_shadow, read working */
			DISP_REG_SET_FIELD(handle, FLD_SPLIT_BYPASS_SHADOW, DISP_REG_SPLIT_SHADOW_CTRL, 0x1);
			DISP_REG_SET_FIELD(handle, FLD_SPLIT_RD_WRK_REG, DISP_REG_SPLIT_SHADOW_CTRL, 0x1);
		}
	}

	DISP_REG_SET_FIELD(handle, ENABLE_FLD_SPLIT_EN, DISP_REG_SPLIT_ENABLE, 0x1);
	return 0;
}

static int split_stop(enum DISP_MODULE_ENUM module, void *handle)
{
	DISP_REG_SET_FIELD(handle, ENABLE_FLD_SPLIT_EN, DISP_REG_SPLIT_ENABLE, 0x0);
	return 0;
}

static int split_config(enum DISP_MODULE_ENUM module, struct disp_ddp_path_config *pConfig, void *handle)
{
	if (!pConfig->dst_dirty)
		return 0;

	/* set HSIZE & VSIZE */
	/* split l/r mode + dual dsi */
	DISP_REG_SET_FIELD(handle, REG_HSIZE_FLD_HSIZE_L, DISP_REG_SPLIT_HSIZE, pConfig->dst_w/2);
	DISP_REG_SET_FIELD(handle, REG_HSIZE_FLD_HSIZE_R, DISP_REG_SPLIT_HSIZE, pConfig->dst_w/2);
	DISP_REG_SET(handle, DISP_REG_SPLIT_VSIZE, pConfig->dst_h);
	return 0;
}


int split_reset(enum DISP_MODULE_ENUM module, void *handle)
{
	DISP_REG_SET(handle, DISP_REG_SPLIT_SW_RESET, 0x1);
	DISP_REG_SET(handle, DISP_REG_SPLIT_SW_RESET, 0x0);

	return 0;
}

static int split_dump_regs(enum DISP_MODULE_ENUM module)
{
	if (disp_helper_get_option(DISP_OPT_REG_PARSER_RAW_DUMP)) {
		unsigned long module_base = ddp_get_module_va(module);

		DDPDUMP("== START: DISP %s REGS ==\n", ddp_get_module_name(module));
		DDPDUMP("split: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x\n",
			0x000, INREG32(module_base + 0x000),
			0x004, INREG32(module_base + 0x004),
			0x008, INREG32(module_base + 0x008),
			0x00c, INREG32(module_base + 0x00c));
		DDPDUMP("split: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x\n",
			0x010, INREG32(module_base + 0x010),
			0x020, INREG32(module_base + 0x020),
			0x024, INREG32(module_base + 0x024),
			0x028, INREG32(module_base + 0x028));
		DDPDUMP("split: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x\n",
			0x02c, INREG32(module_base + 0x02c),
			0x030, INREG32(module_base + 0x030),
			0x034, INREG32(module_base + 0x034),
			0x038, INREG32(module_base + 0x038));
		DDPDUMP("split: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x\n",
			0x03c, INREG32(module_base + 0x03c),
			0x040, INREG32(module_base + 0x040),
			0x044, INREG32(module_base + 0x044),
			0x48, INREG32(module_base + 0x48));
		DDPDUMP("split: 0x%04x=0x%08x\n",
			0x080, INREG32(module_base + 0x080));

		DDPDUMP("-- END: DISP %s REGS --\n", ddp_get_module_name(module));
	}

	return 0;
}

static int split_dump_analysis(enum DISP_MODULE_ENUM module)
{

	DDPMSG("== DISP SPLIT0 ANALYSIS ==\n");
	return 0;
}

static int split_dump(enum DISP_MODULE_ENUM module, int level)
{
	split_dump_analysis(module);
	split_dump_regs(module);

	return 0;
}

struct DDP_MODULE_DRIVER ddp_driver_split = {
	.init		= split_init,
	.deinit		= split_deinit,
	.config		= split_config,
	.start		= split_start,
	.trigger	= NULL,
	.stop		= split_stop,
	.reset		= split_reset,
	.power_on	= split_clock_on,
	.power_off	= split_clock_off,
	.is_idle         = NULL,
	.is_busy         = NULL,
	.dump_info	= split_dump,
	.bypass		= NULL,
	.build_cmdq	= NULL,
	.set_lcm_utils	= NULL,
};
