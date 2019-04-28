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

#include <linux/gfp.h>
#include <linux/types.h>
#include <linux/string.h> /* for test cases */
#include <asm/cacheflush.h>
#include <linux/slab.h>
#include <linux/dma-mapping.h>
#include <asm/uaccess.h>
#include <asm/bitops.h>
#include <linux/time.h>
#include <linux/delay.h>
#include <linux/sched.h>
#include <linux/module.h>
#include <linux/kthread.h>
#include <linux/mutex.h>

/* #include <mach/mt_spm_idle.h> */
#ifdef CONFIG_MTK_CLKMGR
#include <mach/mt_clkmgr.h>
#else
#include <ddp_clkmgr.h>
#endif
#include <m4u.h>
#include <ddp_drv.h>
#include <ddp_reg.h>
#include <lcm_drv.h>
#include <ddp_dither.h>
#include <ddp_od.h>
#include <ddp_path.h>
#include <ddp_dump.h>
#include "ddp_od_reg.h"

#if defined(COMMON_DISP_LOG)
#include <disp_debug.h>
#include <disp_log.h>
#else
#include <disp_drv_log.h>
#include <ddp_log.h>
#endif

#define OD_REG_SET_FIELD(cmdq, reg, val, field) DISP_REG_SET_FIELD(cmdq, field, (unsigned long)(reg), val)
#define OD_REG_GET(reg32) DISP_REG_GET((unsigned long)(reg32))
#define OD_REG_SET(handle, reg32, val) DISP_REG_SET(handle, (unsigned long)(reg32), val)

#define ABS(a) ((a > 0) ? a : -a)

/* debug macro */
#define ODERR(fmt, arg...) pr_err("[OD] " fmt "\n", ##arg)
#define ODNOTICE(fmt, arg...) pr_warn("[OD] " fmt "\n", ##arg)


enum OD_LOG_LEVEL {
	OD_LOG_ALWAYS = 0,
	OD_LOG_VERBOSE,
	OD_LOG_DEBUG
};
static int od_log_level = -1;
#define ODDBG(level, fmt, arg...) \
	do { \
		if (od_log_level >= (level)) \
			pr_debug("[OD] " fmt "\n", ##arg); \
	} while (0)

static ddp_module_notify g_od_ddp_notify;

void od_debug_reg(void)
{
	ODDBG(OD_LOG_ALWAYS, "==DISP OD REGS==");
	ODDBG(OD_LOG_ALWAYS, "OD:0x000=0x%08x,0x004=0x%08x,0x008=0x%08x,0x00c=0x%08x",
		OD_REG_GET(DISP_REG_OD_EN),
		OD_REG_GET(DISP_REG_OD_RESET),
		OD_REG_GET(DISP_REG_OD_INTEN),
		OD_REG_GET(DISP_REG_OD_INTSTA));

	ODDBG(OD_LOG_ALWAYS, "OD:0x010=0x%08x,0x020=0x%08x,0x024=0x%08x,0x028=0x%08x",
		OD_REG_GET(DISP_REG_OD_STATUS),
		OD_REG_GET(DISP_REG_OD_CFG),
		OD_REG_GET(DISP_REG_OD_INPUT_COUNT),
		OD_REG_GET(DISP_REG_OD_OUTPUT_COUNT));

	ODDBG(OD_LOG_ALWAYS, "OD:0x02c=0x%08x,0x030=0x%08x,0x040=0x%08x,0x044=0x%08x",
		OD_REG_GET(DISP_REG_OD_CHKSUM),
		OD_REG_GET(DISP_REG_OD_SIZE),
		OD_REG_GET(DISP_REG_OD_HSYNC_WIDTH),
		OD_REG_GET(DISP_REG_OD_VSYNC_WIDTH));

	ODDBG(OD_LOG_ALWAYS, "OD:0x048=0x%08x,0x0C0=0x%08x",
		OD_REG_GET(DISP_REG_OD_MISC),
		OD_REG_GET(DISP_REG_OD_DUMMY_REG));

	ODDBG(OD_LOG_ALWAYS, "OD:0x684=0x%08x,0x688=0x%08x,0x68c=0x%08x,0x690=0x%08x",
		OD_REG_GET(DISPSYS_OD_BASE + 0x684),
		OD_REG_GET(DISPSYS_OD_BASE + 0x688),
		OD_REG_GET(DISPSYS_OD_BASE + 0x68c),
		OD_REG_GET(DISPSYS_OD_BASE + 0x690));

	ODDBG(OD_LOG_ALWAYS, "OD:0x694=0x%08x,0x698=0x%08x,0x700=0x%08x,0x704=0x%08x",
		OD_REG_GET(DISPSYS_OD_BASE + 0x694),
		OD_REG_GET(DISPSYS_OD_BASE + 0x698),
		OD_REG_GET(DISPSYS_OD_BASE + 0x700),
		OD_REG_GET(DISPSYS_OD_BASE + 0x704));

	ODDBG(OD_LOG_ALWAYS, "OD:0x708=0x%08x,0x778=0x%08x,0x78c=0x%08x,0x790=0x%08x",
		OD_REG_GET(DISPSYS_OD_BASE + 0x708),
		OD_REG_GET(DISPSYS_OD_BASE + 0x778),
		OD_REG_GET(DISPSYS_OD_BASE + 0x78c),
		OD_REG_GET(DISPSYS_OD_BASE + 0x790));

	ODDBG(OD_LOG_ALWAYS, "OD:0x7a0=0x%08x,0x7dc=0x%08x,0x7e8=0x%08x",
		OD_REG_GET(DISPSYS_OD_BASE + 0x7a0),
		OD_REG_GET(DISPSYS_OD_BASE + 0x7dc),
		OD_REG_GET(DISPSYS_OD_BASE + 0x7e8));

}

/* NOTE: OD is not really enabled here until disp_od_start_read() is called */
void _od_core_set_enabled(void *cmdq, int enabled)
{
#if defined(CONFIG_MTK_OD_SUPPORT)
	DISP_REG_MASK(cmdq, DISP_REG_OD_CFG, 0x1, 0x3); /* Relay mode */
#endif
}

void disp_od_irq_handler(void)
{
	DISP_REG_SET(NULL, DISP_REG_OD_INTSTA, 0);
}


int disp_rdma_notify(unsigned int reg)
{
	return 0;
}

int disp_od_update_status(void *cmdq) /* Linked from primary_display.c */
{
	/* Do nothing */
	return 0;
}

#if defined(CONFIG_ARCH_MT6797)
static int _od_partial_update(DISP_MODULE_ENUM module, void *arg, void *cmdq)
{
	struct disp_rect *roi = (struct disp_rect *) arg;
	int width = roi->width;
	int height = roi->height;

	DISP_REG_SET(cmdq, DISP_REG_OD_SIZE, (width << 16) | height);
	return 0;
}

static int disp_od_io(DISP_MODULE_ENUM module, void *handle,
		DDP_IOCTL_NAME ioctl_cmd, void *params)
{
	int ret = -1;

	if (ioctl_cmd == DDP_PARTIAL_UPDATE) {
		_od_partial_update(module, params, handle);
		ret = 0;
	}
	return ret;
}
#endif

static int disp_od_ioctl(DISP_MODULE_ENUM module, int msg, unsigned long arg, void *cmdq)
{
	ODDBG(OD_LOG_ALWAYS, "OD not support");

	return 0;
}


static void ddp_bypass_od(unsigned int width, unsigned int height, void *handle)
{
	ODNOTICE("ddp_bypass_od");
	DISP_REG_SET(handle, DISP_REG_OD_SIZE, (width << 16) | height);
	DISP_REG_SET(handle, DISP_REG_OD_CFG, 0x1);
	DISP_REG_SET(handle, DISP_REG_OD_EN, 0x1);
}

static int od_config_od(DISP_MODULE_ENUM module, disp_ddp_path_config *pConfig, void *cmdq)
{
	if (pConfig->dst_dirty) {
		/* Not support OD */
		ddp_bypass_od(pConfig->dst_w, pConfig->dst_h, cmdq);
		ODDBG(OD_LOG_ALWAYS, "od_config_od: Not support od bypass");
	}

	return 0;
}

static int od_clock_on(DISP_MODULE_ENUM module, void *handle)
{
	M4U_PORT_STRUCT m4u_port;

#ifdef ENABLE_CLK_MGR
#ifdef CONFIG_MTK_CLKMGR
	enable_clock(MT_CG_DISP0_DISP_OD, "od");
	DISPMSG("od_clock on CG 0x%x\n", DISP_REG_GET(DISP_REG_CONFIG_MMSYS_CG_CON0));
#else
	ddp_clk_enable(DISP0_DISP_OD);
#endif /* CONFIG_MTK_CLKMGR */
#endif /* ENABLE_CLK_MGR */

	m4u_port.ePortID = M4U_PORT_DISP_OD_R;
	m4u_port.Virtuality = 0;
	m4u_port.Security = 0;
	m4u_port.domain = 0;
	m4u_port.Distance = 1;
	m4u_port.Direction = 0;
	m4u_config_port(&m4u_port);
	m4u_port.ePortID = M4U_PORT_DISP_OD_W;
	m4u_config_port(&m4u_port);

	return 0;
}


static int od_clock_off(DISP_MODULE_ENUM module, void *handle)
{
#ifdef ENABLE_CLK_MGR
#ifdef CONFIG_MTK_CLKMGR
	disable_clock(MT_CG_DISP0_DISP_OD , "od");
	DISPMSG("od_clock off CG 0x%x\n", DISP_REG_GET(DISP_REG_CONFIG_MMSYS_CG_CON0));
#else
	ddp_clk_disable(DISP0_DISP_OD);
#endif /* CONFIG_MTK_CLKMGR */
#endif /* ENABLE_CLK_MGR */

	return 0;
}


/* for SODI to check OD is enabled or not, this will be called when screen is on and disp clock is enabled */
int disp_od_is_enabled(void)
{
	return (DISP_REG_GET(DISP_REG_OD_CFG) & (1 << 1)) ? 1 : 0;
}


static int od_set_listener(DISP_MODULE_ENUM module, ddp_module_notify notify)
{
	g_od_ddp_notify = notify;
	return 0;
}


/* OD driver module */
DDP_MODULE_DRIVER ddp_driver_od = {
	.init            = od_clock_on,
	.deinit          = od_clock_off,
	.config          = od_config_od,
	.trigger         = NULL,
	.stop            = NULL,
	.reset           = NULL,
	.power_on        = od_clock_on,
	.power_off       = od_clock_off,
	.is_idle         = NULL,
	.is_busy         = NULL,
	.dump_info       = NULL,
	.bypass          = NULL,
	.build_cmdq      = NULL,
	.set_lcm_utils   = NULL,
	.cmd             = disp_od_ioctl,
	.set_listener    = od_set_listener,
#if defined(CONFIG_ARCH_MT6797)
	.ioctl           = disp_od_io
#endif
};

void od_test(const char *cmd, char *debug_output)
{
	ODDBG(OD_LOG_ALWAYS, "OD not support");
}
