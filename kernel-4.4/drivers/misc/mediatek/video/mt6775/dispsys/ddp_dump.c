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

#define LOG_TAG "dump"

#include "ddp_reg.h"
#include "ddp_log.h"
#include "ddp_dump.h"
#include "ddp_ovl.h"
#include "ddp_wdma.h"
#include "ddp_wdma_ex.h"
#include "ddp_rdma.h"
#include "ddp_rdma_ex.h"
#include "ddp_dsi.h"
#include "ddp_rsz.h"
#include "disp_helper.h"


static char *ddp_signal_0(int bit)
{
	switch (bit) {
	case 0:
		return "aal0__to__gamma0";
	case 1:
		return "ccorr0__to__aal0";
	case 2:
		return "color0__to__color_out_sel_in0";
	case 3:
		return "color_out_sel__to__ccorr0";
	case 4:
		return "path0_sel__to__rdma0";
	case 5:
		return "rdma0__to__rdma0_rsz_in_sout";
	case 6:
		return "rdma0_sout_out0__to__dsi0_sel_in1";
	case 7:
		return "rdma0_sout_out1__to__color0";
	case 8:
		return "rdma0_sout_out2__to__color_out_sel_in1";
	case 9:
		return "rdma0_sout_out3__to__dsi1_sel_in1";
	case 10:
		return "rdma1__to__rdma1_sout";
	case 11:
		return "rdma1_sout_out0__to__dsi1_sel_in2";
	case 12:
		return "rdma1_sout_out1__to__dsi0_sel_in3";
	case 13:
		return "rsz__to__rsz_mout";
	case 14:
		return "rsz_mout0__to__ovl0";
	case 15:
		return "rsz_mout1__to__ovl0_2L";
	case 16:
		return "rsz_mout2__to__ovl1_2L";
	case 17:
		return "rsz_mout3__to__path0_sel_in3";
	case 18:
		return "rsz_mout4__to__ovl_to_wdma_sel_in3";
	case 19:
		return "rsz_sel__to__rsz";
	case 20:
		return "split_out0__to__dsi0_sel_in2";
	case 21:
		return "split_out1__to__dsi1_sel_in0";
	case 22:
		return "wdma_pre_sel__to__to_wdma";
	case 23:
		return "dither0__to__dither0_mout";
	case 24:
		return "dither0_mout0__to__dsi0_sel_in0";
	case 25:
		return "dither0_mout1__to__split";
	case 26:
		return "dither0_mout2__to__dsi1_sel_in3";
	case 27:
		return "dither0_mout3__to__wdma0_pre_sel_in1";
	case 28:
		return "dsi0_sel__to__dsi0";
	case 29:
		return "dsi1_sel__to__dsi1";
	case 30:
		return "gamma0__to__dither0";

	default:
		return NULL;
	}
}

static char *ddp_signal_1(int bit)
{
	switch (bit) {
	case 6:
		return "ovl0_2L__to__ovl0_2L_mout";
	case 7:
		return "ovl0_2L_mout0__to__path0_sel_in1";
	case 8:
		return "ovl0_2L_mout1__to__ovl_to_wrot_sel_in1";
	case 9:
		return "ovl0_2L_mout2__to__ovl_to_wdma_sel_in1";
	case 10:
		return "ovl0_2L_mout3__to__ovl_to_rsz_sel_in1";
	case 11:
		return "ovl0_2L_mout4__to__ovl1_2L";
	case 12:
		return "ovl0_2L_mout5__to__rsz_sel_in1";
	case 13:
		return "ovl0_2L_sel__to__ovl0_2L";
	case 14:
		return "ovl0__to__ovl0_mout";
	case 15:
		return "ovl0_mout0__to__path0_sel_in0";
	case 16:
		return "ovl0_mout1__to__ovl_to_wrot_sel_in0";
	case 17:
		return "ovl0_mout2__to__ovl_to_wdma_sel_in0";
	case 18:
		return "ovl0_mout3__to__ovl_to_rsz_sel_in0";
	case 19:
		return "ovl0_mout4__to__ovl0_2L";
	case 20:
		return "ovl0_mout5__to__rsz_sel_in0";
	case 21:
		return "ovl0_sel__to__ovl0";
	case 22:
		return "ovl1_2L__to__ovl1_2L_mout";
	case 23:
		return "ovl1_2L_mout0__to__path0_sel_in2";
	case 24:
		return "ovl1_2L_mout1__to__ovl_to_wrot_sel_in2";
	case 25:
		return "ovl1_2L_mout2__to__ovl_to_wdma_sel_in2";
	case 26:
		return "ovl1_2L_mout3__to__ovl_to_rsz_sel_in2";
	case 27:
		return "ovl1_2L_mout4__to__rdma1";
	case 28:
		return "ovl1_2L_mout5__to__rsz_sel_in2";
	case 29:
		return "ovl_to_rsz_sel__to__to_rsz";
	case 30:
		return "ovl_to_wdma_sel__to__wdma0_pre_sel_in0";
	case 31:
		return "ovl_to_wrot_sel__to__to_wrot_ready";

	default:
		return NULL;
	}
}

static char *ddp_signal_2(int bit)
{
	switch (bit) {
	case 0:
		return "rdma0_rsz_in_sout_out0__to__rdma0_rsz_out_sel_in0";
	case 1:
		return "rdma0_rsz_in_sout_out1__to__rsz_rsz_sel_in3";
	case 2:
		return "rdma0_rsz_out_sel__to__rdma0_sout";
	case 3:
		return "rsz_mout5__to__rdma0_rsz_out_sel_in1";

	default:
		return NULL;
	}
}


static char *ddp_greq_name(int bit)
{
	switch (bit) {
	case 0:
		return "OVL0";
	case 1:
		return "OVL0_2L_LARB0";
	case 2:
		return "RDMA0";
	case 3:
		return "WDMA0";
	case 4:
		return "MDP_RDMA0";
	case 5:
		return "MDP_WROT0";
	case 6:
		return "DISP_FAKE0";
	case 16:
		return "OVL1";
	case 17:
		return "RDMA1";
	case 18:
		return "OVL0_2L_LARB1";
	case 19:
		return "MDP_RDMA1";
	case 20:
		return "MDP_WROT1";
	case 21:
		return "DISP_FAKE1";
	default:
		return NULL;
	}
}

static char *ddp_get_mutex_module0_name(unsigned int bit)
{
	switch (bit) {
	case 0:  return "rdma0";
	case 1:  return "rdma1";
	case 2:  return "mdp_rdma0";
	case 4:  return "mdp_rsz0";
	case 5:  return "mdp_rsz1";
	case 6:  return "mdp_tdshp";
	case 7: return "mdp_wrot0";
	case 8: return "mdp_wrot1";
	case 9: return "ovl0";
	case 10: return "ovl0_2L";
	case 11: return "ovl1_2L";
	case 12: return "wdma0";
	case 13: return "color0";
	case 14: return "ccorr0";
	case 15: return "aal0";
	case 16: return "gamma0";
	case 17: return "dither0";
	case 18: return "PWM0";
	case 19: return "DSI";
	case 20: return "DPI";
	case 22: return "RSZ";
	default: return "mutex-unknown";
	}
}



char *ddp_get_fmt_name(enum DISP_MODULE_ENUM module, unsigned int fmt)
{
	if (module == DISP_MODULE_WDMA0) {
		switch (fmt) {
		case 0:
			return "rgb565";
		case 1:
			return "rgb888";
		case 2:
			return "rgba8888";
		case 3:
			return "argb8888";
		case 4:
			return "uyvy";
		case 5:
			return "yuy2";
		case 7:
			return "y-only";
		case 8:
			return "iyuv";
		case 12:
			return "nv12";
		default:
			DDPDUMP("ddp_get_fmt_name, unknown fmt=%d, module=%d\n", fmt, module);
			return "unknown";
		}
	} else if (module == DISP_MODULE_OVL0) {
		switch (fmt) {
		case 0:
			return "rgb565";
		case 1:
			return "rgb888";
		case 2:
			return "rgba8888";
		case 3:
			return "argb8888";
		case 4:
			return "uyvy";
		case 5:
			return "yuyv";
		default:
			DDPDUMP("ddp_get_fmt_name, unknown fmt=%d, module=%d\n", fmt, module);
			return "unknown";
		}
	} else if (module == DISP_MODULE_RDMA0 || module == DISP_MODULE_RDMA1) {
		switch (fmt) {
		case 0:
			return "rgb565";
		case 1:
			return "rgb888";
		case 2:
			return "rgba8888";
		case 3:
			return "argb8888";
		case 4:
			return "uyvy";
		case 5:
			return "yuyv";
		default:
			DDPDUMP("ddp_get_fmt_name, unknown fmt=%d, module=%d\n", fmt, module);
			return "unknown";
		}
	} else {
		DDPDUMP("ddp_get_fmt_name, unknown module=%d\n", module);
	}

	return "unknown";
}

static char *ddp_clock_0(int bit)
{
	switch (bit) {
	case 0:
		return "smi_common(cg), ";
	case 1:
		return "smi_larb0(cg), ";
	case 2:
		return "smi_larb1(cg), ";
	case 3:
		return "gals_common0(cg), ";
	case 4:
		return "gals_common1(cg), ";
	case 17:
		return "mdp-wrot0, ";
	case 18:
		return "mdp-wrot1, ";
	case 20:
		return "ovl0, ";
	case 21:
		return "ovl0_2L, ";
	case 22:
		return "ovl1_2L, ";
	case 23:
		return "rdma0, ";
	case 24:
		return "rdma1, ";
	case 25:
		return "wdma0, ";
	case 26:
		return "color, ";
	case 27:
		return "ccorr, ";
	case 28:
		return "aal, ";
	case 29:
		return "gamma, ";
	case 30:
		return "dither, ";
	case 31:
		return "split, ";
	default:
		return NULL;
	}
}

static char *ddp_clock_1(int bit)
{
	switch (bit) {
	case 0:
		return "dsi0_mm(cg), ";
	case 1:
		return "dsi0_interface(cg), ";
	case 2:
		return "dsi1_mm(cg), ";
	case 3:
		return "dsi1_interface, ";
	case 7:
		return "26M, ";
	default:
		return NULL;
	}
}

static void mutex_dump_reg(void)
{
	unsigned long module_base = DISPSYS_MUTEX_BASE;

	DDPDUMP("== START: DISP MUTEX registers ==\n");
	DDPDUMP("MUTEX: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x\n",
		0x0, INREG32(module_base + 0x0),
		0x4, INREG32(module_base + 0x4),
		0x8, INREG32(module_base + 0x8),
		0xC, INREG32(module_base + 0xC));
	DDPDUMP("MUTEX: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x\n",
		0x10, INREG32(module_base + 0x10),
		0x18, INREG32(module_base + 0x18),
		0x1C, INREG32(module_base + 0x1C),
		0x020, INREG32(module_base + 0x020));
	DDPDUMP("MUTEX: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x\n",
		0x024, INREG32(module_base + 0x024),
		0x028, INREG32(module_base + 0x028),
		0x02C, INREG32(module_base + 0x02C),
		0x030, INREG32(module_base + 0x030));
	DDPDUMP("MUTEX: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x\n",
		0x040, INREG32(module_base + 0x040),
		0x044, INREG32(module_base + 0x044),
		0x048, INREG32(module_base + 0x048),
		0x04C, INREG32(module_base + 0x04C));
	DDPDUMP("MUTEX: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x\n",
		0x050, INREG32(module_base + 0x050),
		0x060, INREG32(module_base + 0x060),
		0x064, INREG32(module_base + 0x064),
		0x068, INREG32(module_base + 0x068));
	DDPDUMP("MUTEX: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x\n",
		0x06C, INREG32(module_base + 0x06C),
		0x070, INREG32(module_base + 0x070),
		0x080, INREG32(module_base + 0x080),
		0x084, INREG32(module_base + 0x084));
	DDPDUMP("MUTEX: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x\n",
		0x088, INREG32(module_base + 0x088),
		0x08C, INREG32(module_base + 0x08C),
		0x090, INREG32(module_base + 0x090),
		0x0A0, INREG32(module_base + 0x0A0));
	DDPDUMP("MUTEX: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x\n",
		0x0A4, INREG32(module_base + 0x0A4),
		0x0A8, INREG32(module_base + 0x0A8),
		0x0AC, INREG32(module_base + 0x0AC),
		0x0B0, INREG32(module_base + 0x0B0));
	DDPDUMP("MUTEX: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x\n",
		0x0C0, INREG32(module_base + 0x0C0),
		0x0C4, INREG32(module_base + 0x0C4),
		0x0C8, INREG32(module_base + 0x0C8),
		0x0CC, INREG32(module_base + 0x0CC));
	DDPDUMP("MUTEX: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x\n",
		0x0D0, INREG32(module_base + 0x0D0),
		0x0E0, INREG32(module_base + 0x0E0),
		0x0E4, INREG32(module_base + 0x0E4),
		0x0E8, INREG32(module_base + 0x0E8));
	DDPDUMP("MUTEX: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x\n",
		0x0EC, INREG32(module_base + 0x0EC),
		0x0F0, INREG32(module_base + 0x0F0),
		0x100, INREG32(module_base + 0x100),
		0x104, INREG32(module_base + 0x104));
	DDPDUMP("MUTEX: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x\n",
		0x108, INREG32(module_base + 0x108),
		0x10C, INREG32(module_base + 0x10C),
		0x110, INREG32(module_base + 0x110),
		0x120, INREG32(module_base + 0x120));
	DDPDUMP("MUTEX: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x\n",
		0x124, INREG32(module_base + 0x124),
		0x128, INREG32(module_base + 0x128),
		0x12C, INREG32(module_base + 0x12C),
		0x130, INREG32(module_base + 0x130));
	DDPDUMP("MUTEX: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x\n",
		0x140, INREG32(module_base + 0x140),
		0x144, INREG32(module_base + 0x144),
		0x148, INREG32(module_base + 0x148),
		0x14C, INREG32(module_base + 0x14C));
	DDPDUMP("MUTEX: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x\n",
		0x150, INREG32(module_base + 0x150),
		0x160, INREG32(module_base + 0x160),
		0x164, INREG32(module_base + 0x164),
		0x168, INREG32(module_base + 0x168));
	DDPDUMP("MUTEX: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x\n",
		0x16C, INREG32(module_base + 0x16C),
		0x170, INREG32(module_base + 0x170),
		0x180, INREG32(module_base + 0x180),
		0x184, INREG32(module_base + 0x184));
	DDPDUMP("MUTEX: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x\n",
		0x188, INREG32(module_base + 0x188),
		0x18C, INREG32(module_base + 0x18C),
		0x190, INREG32(module_base + 0x190),
		0x300, INREG32(module_base + 0x300));
	DDPDUMP("MUTEX: 0x%04x=0x%08x, 0x%04x=0x%08x\n",
		0x304, INREG32(module_base + 0x304),
		0x30C, INREG32(module_base + 0x30C));
	DDPDUMP("-- END: DISP MUTEX registers --\n");

}


static void mutex_dump_analysis(void)
{
	int i = 0;
	int j = 0;
	char mutex_module[512] = { '\0' };
	char *p = NULL;
	int len = 0;
	unsigned int val;

	DDPDUMP("== DISP Mutex Analysis ==\n");
	for (i = 0; i < 5; i++) {
		p = mutex_module;
		len = 0;
		if (DISP_REG_GET(DISP_REG_CONFIG_MUTEX_MOD0(i)) == 0)
			continue;

		val = DISP_REG_GET(DISP_REG_CONFIG_MUTEX_SOF(i));
		len = sprintf(p, "MUTEX%d:SOF=%s,EOF=%s,WAIT=%d,module=(",
			      i,
			      ddp_get_mutex_sof_name(REG_FLD_VAL_GET(SOF_FLD_MUTEX0_SOF, val)),
			      ddp_get_mutex_sof_name(REG_FLD_VAL_GET(SOF_FLD_MUTEX0_EOF, val)),
				REG_FLD_VAL_GET(SOF_FLD_MUTEX0_SOF_WAIT, val));

		p += len;
		for (j = 0; j < 32; j++) {
			unsigned int regval = DISP_REG_GET(DISP_REG_CONFIG_MUTEX_MOD0(i));

			if ((regval & (1 << j))) {
				len = sprintf(p, "%s,", ddp_get_mutex_module0_name(j));
				p += len;
			}
		}
		DDPDUMP("%s)\n", mutex_module);
	}
}

static void mmsys_config_dump_reg(void)
{
	unsigned long module_base = DISPSYS_CONFIG_BASE;

	DDPDUMP("== START: DISP MMSYS REGS ==\n");
	DDPDUMP("mmsys: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x\n",
		0x000, INREG32(module_base + 0x000),
		0x004, INREG32(module_base + 0x004),
		0x00c, INREG32(module_base + 0x00c),
		0x010, INREG32(module_base + 0x010));
	DDPDUMP("mmsys: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x\n",
		0x014, INREG32(module_base + 0x014),
		0x018, INREG32(module_base + 0x018),
		0x020, INREG32(module_base + 0x020),
		0x024, INREG32(module_base + 0x024));
	DDPDUMP("mmsys: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x\n",
		0x028, INREG32(module_base + 0x028),
		0x02c, INREG32(module_base + 0x02c),
		0x030, INREG32(module_base + 0x030),
		0x034, INREG32(module_base + 0x034));
	DDPDUMP("mmsys: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x\n",
		0x038, INREG32(module_base + 0x038),
		0x048, INREG32(module_base + 0x048),
		0x0f0, INREG32(module_base + 0x0f0),
		0x0f4, INREG32(module_base + 0x0f4));
	DDPDUMP("mmsys: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x\n",
		0x0f8, INREG32(module_base + 0x0f8),
		0x100, INREG32(module_base + 0x100),
		0x104, INREG32(module_base + 0x104),
		0x108, INREG32(module_base + 0x108));
	DDPDUMP("mmsys: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x\n",
		0x110, INREG32(module_base + 0x110),
		0x114, INREG32(module_base + 0x114),
		0x118, INREG32(module_base + 0x118),
		0x120, INREG32(module_base + 0x120));
	DDPDUMP("mmsys: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x\n",
		0x124, INREG32(module_base + 0x124),
		0x128, INREG32(module_base + 0x128),
		0x130, INREG32(module_base + 0x130),
		0x134, INREG32(module_base + 0x134));
	DDPDUMP("mmsys: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x\n",
		0x138, INREG32(module_base + 0x138),
		0x13c, INREG32(module_base + 0x13c),
		0x140, INREG32(module_base + 0x140),
		0x144, INREG32(module_base + 0x144));
	DDPDUMP("mmsys: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x\n",
		0x150, INREG32(module_base + 0x150),
		0x180, INREG32(module_base + 0x180),
		0x184, INREG32(module_base + 0x184),
		0x190, INREG32(module_base + 0x190));
	DDPDUMP("mmsys: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x\n",
		0x200, INREG32(module_base + 0x200),
		0x204, INREG32(module_base + 0x204),
		0x208, INREG32(module_base + 0x208),
		0x20c, INREG32(module_base + 0x20c));
	DDPDUMP("mmsys: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x\n",
		0x210, INREG32(module_base + 0x210),
		0x214, INREG32(module_base + 0x214),
		0x218, INREG32(module_base + 0x218),
		0x220, INREG32(module_base + 0x220));
	DDPDUMP("mmsys: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x\n",
		0x224, INREG32(module_base + 0x224),
		0x228, INREG32(module_base + 0x228),
		0x22c, INREG32(module_base + 0x22c),
		0x230, INREG32(module_base + 0x230));
	DDPDUMP("mmsys: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x\n",
		0x234, INREG32(module_base + 0x234),
		0x238, INREG32(module_base + 0x238),
		0x800, INREG32(module_base + 0x800),
		0x804, INREG32(module_base + 0x804));
	DDPDUMP("mmsys: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x\n",
		0x808, INREG32(module_base + 0x808),
		0x80c, INREG32(module_base + 0x80c),
		0x810, INREG32(module_base + 0x810),
		0x814, INREG32(module_base + 0x814));
	DDPDUMP("mmsys: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x\n",
		0x818, INREG32(module_base + 0x818),
		0x820, INREG32(module_base + 0x820),
		0x824, INREG32(module_base + 0x824),
		0x828, INREG32(module_base + 0x828));
	DDPDUMP("mmsys: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x\n",
		0x82c, INREG32(module_base + 0x82c),
		0x830, INREG32(module_base + 0x830),
		0x834, INREG32(module_base + 0x834),
		0x838, INREG32(module_base + 0x838));
	DDPDUMP("mmsys: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x\n",
		0x83c, INREG32(module_base + 0x83c),
		0x840, INREG32(module_base + 0x840),
		0x844, INREG32(module_base + 0x844),
		0x848, INREG32(module_base + 0x848));
	DDPDUMP("mmsys: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x\n",
		0x84c, INREG32(module_base + 0x84c),
		0x850, INREG32(module_base + 0x850),
		0x854, INREG32(module_base + 0x854),
		0x858, INREG32(module_base + 0x858));
	DDPDUMP("mmsys: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x\n",
		0x85c, INREG32(module_base + 0x85c),
		0x860, INREG32(module_base + 0x860),
		0x864, INREG32(module_base + 0x864),
		0x868, INREG32(module_base + 0x868));
	DDPDUMP("mmsys: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x\n",
		0x870, INREG32(module_base + 0x870),
		0x874, INREG32(module_base + 0x874),
		0x878, INREG32(module_base + 0x878),
		0x88c, INREG32(module_base + 0x88c));
	DDPDUMP("mmsys: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x\n",
		0x890, INREG32(module_base + 0x890),
		0x894, INREG32(module_base + 0x894),
		0x898, INREG32(module_base + 0x898),
		0x89c, INREG32(module_base + 0x89c));
	DDPDUMP("mmsys: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x\n",
		0x8a0, INREG32(module_base + 0x8a0),
		0x8a4, INREG32(module_base + 0x8a4),
		0x8a8, INREG32(module_base + 0x8a8),
		0x8ac, INREG32(module_base + 0x8ac));
	DDPDUMP("mmsys: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x\n",
		0x8b0, INREG32(module_base + 0x8b0),
		0x8b4, INREG32(module_base + 0x8b4),
		0x8b8, INREG32(module_base + 0x8b8),
		0x8bc, INREG32(module_base + 0x8bc));
	DDPDUMP("mmsys: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x\n",
		0x8c0, INREG32(module_base + 0x8c0),
		0x8c4, INREG32(module_base + 0x8c4),
		0x8c8, INREG32(module_base + 0x8c8),
		0x8cc, INREG32(module_base + 0x8cc));
	DDPDUMP("mmsys: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x\n",
		0x8d0, INREG32(module_base + 0x8d0),
		0x8d4, INREG32(module_base + 0x8d4),
		0x8d8, INREG32(module_base + 0x8d8),
		0x8dc, INREG32(module_base + 0x8dc));
	DDPDUMP("mmsys: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x\n",
		0x8e0, INREG32(module_base + 0x8e0),
		0x8e4, INREG32(module_base + 0x8e4),
		0x8e8, INREG32(module_base + 0x8e8),
		0x8ec, INREG32(module_base + 0x8ec));
	DDPDUMP("mmsys: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x\n",
		0x8f0, INREG32(module_base + 0x8f0),
		0x908, INREG32(module_base + 0x908),
		0x90c, INREG32(module_base + 0x90c),
		0x910, INREG32(module_base + 0x910));
	DDPDUMP("mmsys: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x\n",
		0x914, INREG32(module_base + 0x914),
		0x918, INREG32(module_base + 0x918),
		0x91c, INREG32(module_base + 0x91c),
		0x920, INREG32(module_base + 0x920));
	DDPDUMP("mmsys: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x\n",
		0x924, INREG32(module_base + 0x924),
		0x928, INREG32(module_base + 0x928),
		0x934, INREG32(module_base + 0x934),
		0x938, INREG32(module_base + 0x938));
	DDPDUMP("mmsys: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x\n",
		0x93c, INREG32(module_base + 0x93c),
		0x940, INREG32(module_base + 0x940),
		0x944, INREG32(module_base + 0x944),
		0xf00, INREG32(module_base + 0xf00));
	DDPDUMP("mmsys: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x\n",
		0xf04, INREG32(module_base + 0xf04),
		0xf08, INREG32(module_base + 0xf08),
		0xf0c, INREG32(module_base + 0xf0c),
		0xf10, INREG32(module_base + 0xf10));
	DDPDUMP("mmsys: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x\n",
		0xf20, INREG32(module_base + 0xf20),
		0xf24, INREG32(module_base + 0xf24),
		0xf28, INREG32(module_base + 0xf28),
		0xf2c, INREG32(module_base + 0xf2c));
	DDPDUMP("mmsys: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x\n",
		0xf30, INREG32(module_base + 0xf30),
		0xf34, INREG32(module_base + 0xf34),
		0xf38, INREG32(module_base + 0xf38),
		0xf3c, INREG32(module_base + 0xf3c));
	DDPDUMP("mmsys: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x\n",
		0xf40, INREG32(module_base + 0xf40),
		0xf44, INREG32(module_base + 0xf44),
		0xf48, INREG32(module_base + 0xf48),
		0xf50, INREG32(module_base + 0xf50));
	DDPDUMP("mmsys: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x\n",
		0xf54, INREG32(module_base + 0xf54),
		0xf58, INREG32(module_base + 0xf58),
		0xf5c, INREG32(module_base + 0xf5c),
		0xf60, INREG32(module_base + 0xf60));
	DDPDUMP("mmsys: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x\n",
		0xf64, INREG32(module_base + 0xf64),
		0xf80, INREG32(module_base + 0xf80),
		0xf84, INREG32(module_base + 0xf84),
		0xf88, INREG32(module_base + 0xf88));
	DDPDUMP("mmsys: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x\n",
		0xf8c, INREG32(module_base + 0xf8c),
		0xf90, INREG32(module_base + 0xf90),
		0xf94, INREG32(module_base + 0xf94),
		0xf98, INREG32(module_base + 0xf98));
	DDPDUMP("mmsys: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x\n",
		0xf9c, INREG32(module_base + 0xf9c),
		0xfa0, INREG32(module_base + 0xfa0),
		0xfa4, INREG32(module_base + 0xfa4),
		0xfa8, INREG32(module_base + 0xfa8));
	DDPDUMP("mmsys: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x\n",
		0xfac, INREG32(module_base + 0xfac),
		0xfb0, INREG32(module_base + 0xfb0),
		0xfb4, INREG32(module_base + 0xfb4),
		0xfc0, INREG32(module_base + 0xfc0));
	DDPDUMP("mmsys: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x\n",
		0xfc4, INREG32(module_base + 0xfc4),
		0xfc8, INREG32(module_base + 0xfc8),
		0xfcc, INREG32(module_base + 0xfcc),
		0xfd0, INREG32(module_base + 0xfd0));
	DDPDUMP("mmsys: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x\n",
		0xfd4, INREG32(module_base + 0xfd4),
		0xfd8, INREG32(module_base + 0xfd8),
		0xfdc, INREG32(module_base + 0xfdc),
		0xfe0, INREG32(module_base + 0xfe0));
	DDPDUMP("mmsys: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x\n",
		0xfe4, INREG32(module_base + 0xfe4),
		0xfe8, INREG32(module_base + 0xfe8),
		0xfec, INREG32(module_base + 0xfec));

	DDPDUMP("-- END: DISP MMSYS REGS --\n");

}


/*  ------ clock:
  * Before power on mmsys:
  * CLK_CFG_0_CLR (address is 0x10000048) = 0x80000000 (bit 31).
  * Before using DISP_PWM0 or DISP_PWM1:
  * CLK_CFG_1_CLR(address is 0x10000058)=0x80 (bit 7).
  * Before using DPI pixel clock:
  * CLK_CFG_6_CLR(address is 0x100000A8)=0x80 (bit 7).
  *
  * Only need to enable the corresponding bits of MMSYS_CG_CON0 and MMSYS_CG_CON1 for the modules:
  * smi_common, larb0, mdp_crop, fake_eng, mutex_32k, pwm0, pwm1, dsi0, dsi1, dpi.
  * Other bits could keep 1. Suggest to keep smi_common and larb0 always clock on.
  *
  * --------valid & ready
  * example:
  * ovl0 -> ovl0_mout_ready=1 means engines after ovl_mout are ready for receiving data
  *	ovl0_mout_ready=0 means ovl0_mout can not receive data, maybe ovl0_mout or after engines config error
  * ovl0 -> ovl0_mout_valid=1 means engines before ovl0_mout is OK,
  *	ovl0_mout_valid=0 means ovl can not transfer data to ovl0_mout, means ovl0 or before engines are not ready.
  */

static void mmsys_config_dump_analysis(void)
{
	unsigned int i = 0;
	unsigned int reg = 0;
	char clock_on[512] = { '\0' };
	char *pos = NULL;
	char *name;
	/* int len = 0; */

	unsigned int valid0 = DISP_REG_GET(DISP_REG_CONFIG_DISP_DL_VALID_0);
	unsigned int valid1 = DISP_REG_GET(DISP_REG_CONFIG_DISP_DL_VALID_1);
	unsigned int ready0 = DISP_REG_GET(DISP_REG_CONFIG_DISP_DL_READY_0);
	unsigned int ready1 = DISP_REG_GET(DISP_REG_CONFIG_DISP_DL_READY_1);
	unsigned int greq = DISP_REG_GET(DISP_REG_CONFIG_SMI_LARB0_GREQ);
	unsigned int sram_flag = (DISP_REG_GET(DISP_REG_CONFIG_MMSYS_MISC) >> 5) & 3;

	DDPDUMP("== DISP MMSYS_CONFIG ANALYSIS ==\n");
#if 0 /* TODO: mmsys clk?? */
	DDPDUMP("mmsys clock=0x%x, CG_CON0=0x%x, CG_CON1=0x%x\n",
		DISP_REG_GET(DISP_REG_CLK_CFG_0_MM_CLK),
		DISP_REG_GET(DISP_REG_CONFIG_MMSYS_CG_CON0),
		DISP_REG_GET(DISP_REG_CONFIG_MMSYS_CG_CON1));
	if ((DISP_REG_GET(DISP_REG_CLK_CFG_0_MM_CLK) >> 31) & 0x1)
		DDPERR("mmsys clock abnormal!!\n");
#endif

	reg = DISP_REG_GET(DISP_REG_CONFIG_MMSYS_CG_CON0);
	for (i = 0; i < 32; i++) {
		if ((reg & (1 << i)) == 0) {
			name = ddp_clock_0(i);
			if (name)
				strncat(clock_on, name, (sizeof(clock_on) - strlen(clock_on) - 1));
		}
	}

	reg = DISP_REG_GET(DISP_REG_CONFIG_MMSYS_CG_CON1);
	for (i = 0; i < 32; i++) {
		if ((reg & (1 << i)) == 0) {
			name = ddp_clock_1(i);
			if (name)
				strncat(clock_on, name, (sizeof(clock_on) - strlen(clock_on) - 1));
		}
	}
	DDPDUMP("clock on modules:%s\n", clock_on);
	DDPDUMP("sram share map (rdma0<->mdp-wrot0, disp_rsz<->mdp-wrot1) is %s, value[6:5] is %d\n",
		sram_flag == 2 ? "right":"err", sram_flag);
	DDPDUMP("valid0=0x%x, valid1=0x%x, ready0=0x%x, ready1=0x%x, greq=0%x\n",
		valid0, valid1, ready0, ready1, greq);

	for (i = 0; i < 32; i++) {
		name = ddp_signal_0(i);
		if (!name)
			continue;

		pos = clock_on;

		if ((valid0 & (1 << i)))
			pos += sprintf(pos, "%s,", "v");
		else
			pos += sprintf(pos, "%s,", "n");

		if ((ready0 & (1 << i)))
			pos += sprintf(pos, "%s", "r");
		else
			pos += sprintf(pos, "%s", "n");

		pos += sprintf(pos, ": %s", name);

		DDPDUMP("%s\n", clock_on);
	}

	for (i = 0; i < 32; i++) {
		name = ddp_signal_1(i);
		if (!name)
			continue;

		pos = clock_on;

		if ((valid1 & (1 << i)))
			pos += sprintf(pos, "%s,", "v");
		else
			pos += sprintf(pos, "%s,", "n");

		if ((ready1 & (1 << i)))
			pos += sprintf(pos, "%s", "r");
		else
			pos += sprintf(pos, "%s", "n");

		pos += sprintf(pos, ": %s", name);

		DDPDUMP("%s\n", clock_on);
	}

	for (i = 0; i < 32; i++) {
		name = ddp_signal_2(i);
		if (!name)
			continue;

		pos = clock_on;

		if ((valid1 & (1 << i)))
			pos += sprintf(pos, "%s,", "v");
		else
			pos += sprintf(pos, "%s,", "n");

		if ((ready1 & (1 << i)))
			pos += sprintf(pos, "%s", "r");
		else
			pos += sprintf(pos, "%s", "n");

		pos += sprintf(pos, ": %s", name);

		DDPDUMP("%s\n", clock_on);
	}

	/* greq: 1 means SMI dose not grant, maybe SMI hang */
	if (greq)
		DDPDUMP("smi greq not grant module: (greq: 1 means SMI dose not grant, maybe SMI hang)");

	clock_on[0] = '\0';
	for (i = 0; i < 32; i++) {
		if (greq & (1 << i)) {
			name = ddp_greq_name(i);
			if (!name)
				continue;
			strncat(clock_on, name, (sizeof(clock_on) - strlen(clock_on) - 1));
		}
	}
	DDPDUMP("%s\n", clock_on);
}

static void gamma_dump_reg(enum DISP_MODULE_ENUM module)
{
	int i;
	unsigned int offset = 0x1000;

	if (module == DISP_MODULE_GAMMA0)
		i = 0;
	else
		i = 1;


	DDPDUMP("== DISP GAMMA%d REGS ==\n", i);
	DDPDUMP("(0x000)GA_EN=0x%x\n", DISP_REG_GET(DISP_REG_GAMMA_EN + i * offset));
	DDPDUMP("(0x004)GA_RESET=0x%x\n", DISP_REG_GET(DISP_REG_GAMMA_RESET + i * offset));
	DDPDUMP("(0x008)GA_INTEN=0x%x\n", DISP_REG_GET(DISP_REG_GAMMA_INTEN + i * offset));
	DDPDUMP("(0x00c)GA_INTSTA=0x%x\n", DISP_REG_GET(DISP_REG_GAMMA_INTSTA + i * offset));
	DDPDUMP("(0x010)GA_STATUS=0x%x\n", DISP_REG_GET(DISP_REG_GAMMA_STATUS + i * offset));
	DDPDUMP("(0x020)GA_CFG=0x%x\n", DISP_REG_GET(DISP_REG_GAMMA_CFG + i * offset));
	DDPDUMP("(0x024)GA_IN_COUNT=0x%x\n", DISP_REG_GET(DISP_REG_GAMMA_INPUT_COUNT + i * offset));
	DDPDUMP("(0x028)GA_OUT_COUNT=0x%x\n", DISP_REG_GET(DISP_REG_GAMMA_OUTPUT_COUNT + i * offset));
	DDPDUMP("(0x02c)GA_CHKSUM=0x%x\n", DISP_REG_GET(DISP_REG_GAMMA_CHKSUM + i * offset));
	DDPDUMP("(0x030)GA_SIZE=0x%x\n", DISP_REG_GET(DISP_REG_GAMMA_SIZE + i * offset));
	DDPDUMP("(0x0c0)GA_DUMMY_REG=0x%x\n", DISP_REG_GET(DISP_REG_GAMMA_DUMMY_REG + i * offset));
	DDPDUMP("(0x800)GA_LUT=0x%x\n", DISP_REG_GET(DISP_REG_GAMMA_LUT + i * offset));
}

static void gamma_dump_analysis(enum DISP_MODULE_ENUM module)
{
	int i;
	unsigned int offset = 0x1000;

	if (module == DISP_MODULE_GAMMA0)
		i = 0;
	else
		i = 1;


	DDPDUMP("== DISP GAMMA%d ANALYSIS ==\n", i);
	DDPDUMP("gamma: en=%d, w=%d, h=%d, in_p_cnt=%d, in_l_cnt=%d, out_p_cnt=%d, out_l_cnt=%d\n",
		DISP_REG_GET(DISP_REG_GAMMA_EN + i * offset),
		(DISP_REG_GET(DISP_REG_GAMMA_SIZE + i * offset) >> 16) & 0x1fff,
		DISP_REG_GET(DISP_REG_GAMMA_SIZE + i * offset) & 0x1fff,
		DISP_REG_GET(DISP_REG_GAMMA_INPUT_COUNT + i * offset) & 0x1fff,
		(DISP_REG_GET(DISP_REG_GAMMA_INPUT_COUNT + i * offset) >> 16) & 0x1fff,
		DISP_REG_GET(DISP_REG_GAMMA_OUTPUT_COUNT + i * offset) & 0x1fff,
		(DISP_REG_GET(DISP_REG_GAMMA_OUTPUT_COUNT + i * offset) >> 16) & 0x1fff);
}


static void color_dump_reg(enum DISP_MODULE_ENUM module)
{
	int idx = 0;
	unsigned long module_base = DISPSYS_COLOR0_BASE;

	DDPDUMP("== START: DISP COLOR%d registers ==\n", idx);
	DDPDUMP("COLOR%d: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x,	0x%04x=0x%08x\n",
		idx,
		0x00000400, INREG32(module_base + 0x00000400),
		0x00000404, INREG32(module_base + 0x00000404),
		0x00000408, INREG32(module_base + 0x00000408),
		0x0000040C, INREG32(module_base + 0x0000040C));
	DDPDUMP("COLOR%d: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x,	0x%04x=0x%08x\n",
		idx,
		0x00000410, INREG32(module_base + 0x00000410),
		0x00000418, INREG32(module_base + 0x00000418),
		0x0000041C, INREG32(module_base + 0x0000041C),
		0x00000420, INREG32(module_base + 0x00000420));
	DDPDUMP("COLOR%d: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x,	0x%04x=0x%08x\n",
		idx,
		0x00000428, INREG32(module_base + 0x00000428),
		0x0000042C, INREG32(module_base + 0x0000042C),
		0x00000430, INREG32(module_base + 0x00000430),
		0x00000434, INREG32(module_base + 0x00000434));
	DDPDUMP("COLOR%d: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x,	0x%04x=0x%08x\n",
		idx,
		0x00000438, INREG32(module_base + 0x00000438),
		0x00000484, INREG32(module_base + 0x00000484),
		0x00000488, INREG32(module_base + 0x00000488),
		0x0000048C, INREG32(module_base + 0x0000048C));
	DDPDUMP("COLOR%d: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x,	0x%04x=0x%08x\n",
		idx,
		0x00000490, INREG32(module_base + 0x00000490),
		0x00000494, INREG32(module_base + 0x00000494),
		0x00000498, INREG32(module_base + 0x00000498),
		0x0000049C, INREG32(module_base + 0x0000049C));
	DDPDUMP("COLOR%d: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x,	0x%04x=0x%08x\n",
		idx,
		0x000004A0, INREG32(module_base + 0x000004A0),
		0x000004A4, INREG32(module_base + 0x000004A4),
		0x000004A8, INREG32(module_base + 0x000004A8),
		0x000004AC, INREG32(module_base + 0x000004AC));
	DDPDUMP("COLOR%d: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x,	0x%04x=0x%08x\n",
		idx,
		0x000004B0, INREG32(module_base + 0x000004B0),
		0x000004B4, INREG32(module_base + 0x000004B4),
		0x000004B8, INREG32(module_base + 0x000004B8),
		0x000004BC, INREG32(module_base + 0x000004BC));
	DDPDUMP("COLOR%d: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x,	0x%04x=0x%08x\n",
		idx,
		0x00000620, INREG32(module_base + 0x00000620),
		0x00000624, INREG32(module_base + 0x00000624),
		0x00000628, INREG32(module_base + 0x00000628),
		0x0000062C, INREG32(module_base + 0x0000062C));
	DDPDUMP("COLOR%d: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x,	0x%04x=0x%08x\n",
		idx,
		0x00000630, INREG32(module_base + 0x00000630),
		0x00000740, INREG32(module_base + 0x00000740),
		0x0000074C, INREG32(module_base + 0x0000074C),
		0x00000768, INREG32(module_base + 0x00000768));
	DDPDUMP("COLOR%d: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x,	0x%04x=0x%08x\n",
		idx,
		0x0000076C, INREG32(module_base + 0x0000076C),
		0x0000079C, INREG32(module_base + 0x0000079C),
		0x000007E0, INREG32(module_base + 0x000007E0),
		0x000007E4, INREG32(module_base + 0x000007E4));
	DDPDUMP("COLOR%d: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x,	0x%04x=0x%08x\n",
		idx,
		0x000007E8, INREG32(module_base + 0x000007E8),
		0x000007EC, INREG32(module_base + 0x000007EC),
		0x000007F0, INREG32(module_base + 0x000007F0),
		0x000007FC, INREG32(module_base + 0x000007FC));
	DDPDUMP("COLOR%d: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x,	0x%04x=0x%08x\n",
		idx,
		0x00000800, INREG32(module_base + 0x00000800),
		0x00000804, INREG32(module_base + 0x00000804),
		0x00000808, INREG32(module_base + 0x00000808),
		0x0000080C, INREG32(module_base + 0x0000080C));
	DDPDUMP("COLOR%d: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x,	0x%04x=0x%08x\n",
		idx,
		0x00000810, INREG32(module_base + 0x00000810),
		0x00000814, INREG32(module_base + 0x00000814),
		0x00000818, INREG32(module_base + 0x00000818),
		0x0000081C, INREG32(module_base + 0x0000081C));
	DDPDUMP("COLOR%d: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x,	0x%04x=0x%08x\n",
		idx,
		0x00000820, INREG32(module_base + 0x00000820),
		0x00000824, INREG32(module_base + 0x00000824),
		0x00000828, INREG32(module_base + 0x00000828),
		0x0000082C, INREG32(module_base + 0x0000082C));
	DDPDUMP("COLOR%d: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x,	0x%04x=0x%08x\n",
		idx,
		0x00000830, INREG32(module_base + 0x00000830),
		0x00000834, INREG32(module_base + 0x00000834),
		0x00000838, INREG32(module_base + 0x00000838),
		0x0000083C, INREG32(module_base + 0x0000083C));
	DDPDUMP("COLOR%d: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x,	0x%04x=0x%08x\n",
		idx,
		0x00000840, INREG32(module_base + 0x00000840),
		0x00000844, INREG32(module_base + 0x00000844),
		0x00000848, INREG32(module_base + 0x00000848),
		0x0000084C, INREG32(module_base + 0x0000084C));
	DDPDUMP("COLOR%d: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x,	0x%04x=0x%08x\n",
		idx,
		0x00000850, INREG32(module_base + 0x00000850),
		0x00000854, INREG32(module_base + 0x00000854),
		0x00000858, INREG32(module_base + 0x00000858),
		0x0000085C, INREG32(module_base + 0x0000085C));
	DDPDUMP("COLOR%d: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x,	0x%04x=0x%08x\n",
		idx,
		0x00000860, INREG32(module_base + 0x00000860),
		0x00000864, INREG32(module_base + 0x00000864),
		0x00000868, INREG32(module_base + 0x00000868),
		0x0000086C, INREG32(module_base + 0x0000086C));
	DDPDUMP("COLOR%d: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x,	0x%04x=0x%08x\n",
		idx,
		0x00000870, INREG32(module_base + 0x00000870),
		0x00000874, INREG32(module_base + 0x00000874),
		0x00000878, INREG32(module_base + 0x00000878),
		0x0000087C, INREG32(module_base + 0x0000087C));
	DDPDUMP("COLOR%d: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x,	0x%04x=0x%08x\n",
		idx,
		0x00000880, INREG32(module_base + 0x00000880),
		0x00000884, INREG32(module_base + 0x00000884),
		0x00000888, INREG32(module_base + 0x00000888),
		0x0000088C, INREG32(module_base + 0x0000088C));
	DDPDUMP("COLOR%d: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x,	0x%04x=0x%08x\n",
		idx,
		0x00000890, INREG32(module_base + 0x00000890),
		0x00000894, INREG32(module_base + 0x00000894),
		0x00000898, INREG32(module_base + 0x00000898),
		0x0000089C, INREG32(module_base + 0x0000089C));
	DDPDUMP("COLOR%d: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x,	0x%04x=0x%08x\n",
		idx,
		0x000008A0, INREG32(module_base + 0x000008A0),
		0x000008A4, INREG32(module_base + 0x000008A4),
		0x000008A8, INREG32(module_base + 0x000008A8),
		0x000008AC, INREG32(module_base + 0x000008AC));
	DDPDUMP("COLOR%d: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x,	0x%04x=0x%08x\n",
		idx,
		0x000008B0, INREG32(module_base + 0x000008B0),
		0x000008B4, INREG32(module_base + 0x000008B4),
		0x000008B8, INREG32(module_base + 0x000008B8),
		0x000008BC, INREG32(module_base + 0x000008BC));
	DDPDUMP("COLOR%d: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x,	0x%04x=0x%08x\n",
		idx,
		0x000008C0, INREG32(module_base + 0x000008C0),
		0x000008C4, INREG32(module_base + 0x000008C4),
		0x000008C8, INREG32(module_base + 0x000008C8),
		0x000008CC, INREG32(module_base + 0x000008CC));
	DDPDUMP("COLOR%d: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x,	0x%04x=0x%08x\n",
		idx,
		0x000008D0, INREG32(module_base + 0x000008D0),
		0x000008D4, INREG32(module_base + 0x000008D4),
		0x000008D8, INREG32(module_base + 0x000008D8),
		0x000008DC, INREG32(module_base + 0x000008DC));
	DDPDUMP("COLOR%d: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x,	0x%04x=0x%08x\n",
		idx,
		0x000008E0, INREG32(module_base + 0x000008E0),
		0x000008E4, INREG32(module_base + 0x000008E4),
		0x000008E8, INREG32(module_base + 0x000008E8),
		0x000008EC, INREG32(module_base + 0x000008EC));
	DDPDUMP("COLOR%d: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x,	0x%04x=0x%08x\n",
		idx,
		0x000008F0, INREG32(module_base + 0x000008F0),
		0x000008F4, INREG32(module_base + 0x000008F4),
		0x000008F8, INREG32(module_base + 0x000008F8),
		0x000008FC, INREG32(module_base + 0x000008FC));
	DDPDUMP("COLOR%d: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x,	0x%04x=0x%08x\n",
		idx,
		0x00000900, INREG32(module_base + 0x00000900),
		0x00000904, INREG32(module_base + 0x00000904),
		0x00000908, INREG32(module_base + 0x00000908),
		0x0000090C, INREG32(module_base + 0x0000090C));
	DDPDUMP("COLOR%d: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x,	0x%04x=0x%08x\n",
		idx,
		0x00000910, INREG32(module_base + 0x00000910),
		0x00000914, INREG32(module_base + 0x00000914),
		0x00000C00, INREG32(module_base + 0x00000C00),
		0x00000C04, INREG32(module_base + 0x00000C04));
	DDPDUMP("COLOR%d: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x,	0x%04x=0x%08x\n",
		idx,
		0x00000C08, INREG32(module_base + 0x00000C08),
		0x00000C0C, INREG32(module_base + 0x00000C0C),
		0x00000C10, INREG32(module_base + 0x00000C10),
		0x00000C14, INREG32(module_base + 0x00000C14));
	DDPDUMP("COLOR%d: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x,	0x%04x=0x%08x\n",
		idx,
		0x00000C18, INREG32(module_base + 0x00000C18),
		0x00000C28, INREG32(module_base + 0x00000C28),
		0x00000C50, INREG32(module_base + 0x00000C50),
		0x00000C54, INREG32(module_base + 0x00000C54));
	DDPDUMP("COLOR%d: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x,	0x%04x=0x%08x\n",
		idx,
		0x00000C60, INREG32(module_base + 0x00000C60),
		0x00000CA0, INREG32(module_base + 0x00000CA0),
		0x00000CB0, INREG32(module_base + 0x00000CB0),
		0x00000CF0, INREG32(module_base + 0x00000CF0));
	DDPDUMP("COLOR%d: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x,	0x%04x=0x%08x\n",
		idx,
		0x00000CF4, INREG32(module_base + 0x00000CF4),
		0x00000CF8, INREG32(module_base + 0x00000CF8),
		0x00000CFC, INREG32(module_base + 0x00000CFC),
		0x00000D00, INREG32(module_base + 0x00000D00));
	DDPDUMP("COLOR%d: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x,	0x%04x=0x%08x\n",
		idx,
		0x00000D04, INREG32(module_base + 0x00000D04),
		0x00000D08, INREG32(module_base + 0x00000D08),
		0x00000D0C, INREG32(module_base + 0x00000D0C),
		0x00000D10, INREG32(module_base + 0x00000D10));
	DDPDUMP("COLOR%d: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x,	0x%04x=0x%08x\n",
		idx,
		0x00000D14, INREG32(module_base + 0x00000D14),
		0x00000D18, INREG32(module_base + 0x00000D18),
		0x00000D1C, INREG32(module_base + 0x00000D1C),
		0x00000D20, INREG32(module_base + 0x00000D20));
	DDPDUMP("COLOR%d: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x,	0x%04x=0x%08x\n",
		idx,
		0x00000D24, INREG32(module_base + 0x00000D24),
		0x00000D28, INREG32(module_base + 0x00000D28),
		0x00000D2C, INREG32(module_base + 0x00000D2C),
		0x00000D30, INREG32(module_base + 0x00000D30));
	DDPDUMP("COLOR%d: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x,	0x%04x=0x%08x\n",
		idx,
		0x00000D34, INREG32(module_base + 0x00000D34),
		0x00000D38, INREG32(module_base + 0x00000D38),
		0x00000D3C, INREG32(module_base + 0x00000D3C),
		0x00000D40, INREG32(module_base + 0x00000D40));
	DDPDUMP("COLOR%d: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x,	0x%04x=0x%08x\n",
		idx,
		0x00000D44, INREG32(module_base + 0x00000D44),
		0x00000D48, INREG32(module_base + 0x00000D48),
		0x00000D4C, INREG32(module_base + 0x00000D4C),
		0x00000D50, INREG32(module_base + 0x00000D50));
	DDPDUMP("COLOR%d: 0x%04x=0x%08x, 0x%04x=0x%08x,  0x%04x=0x%08x\n",
		idx,
		0x00000D54, INREG32(module_base + 0x00000D54),
		0x00000D58, INREG32(module_base + 0x00000D58),
		0x00000D5C, INREG32(module_base + 0x00000D5C));
	DDPDUMP("-- END: DISP COLOR%d registers --\n", idx);

}

static void color_dump_analysis(enum DISP_MODULE_ENUM module)
{
	int index = 0;

	DDPDUMP("== DISP COLOR%d ANALYSIS ==\n", index);
	DDPDUMP("color%d: bypass=%d, w=%d, h=%d, pixel_cnt=%d, line_cnt=%d,\n",
		index,
		(DISP_REG_GET(DISP_COLOR_CFG_MAIN) >> 7) & 0x1,
		DISP_REG_GET(DISP_COLOR_INTERNAL_IP_WIDTH),
		DISP_REG_GET(DISP_COLOR_INTERNAL_IP_HEIGHT),
		DISP_REG_GET(DISP_COLOR_PXL_CNT_MAIN) & 0xffff,
		(DISP_REG_GET(DISP_COLOR_LINE_CNT_MAIN) >> 16) & 0x1fff);

}

static void aal_dump_reg(enum DISP_MODULE_ENUM module)
{
	int i;
	unsigned long module_base = DISPSYS_AAL0_BASE;

	if (module == DISP_MODULE_AAL0)
		i = 0;
	else
		i = 1;

	DDPDUMP("== START: DISP AAL%d registers ==\n", i);
	DDPDUMP("AAL: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x\n",
		0x00000000, INREG32(module_base + 0x00000000),
		0x00000004, INREG32(module_base + 0x00000004),
		0x00000008, INREG32(module_base + 0x00000008),
		0x0000000C, INREG32(module_base + 0x0000000C));
	DDPDUMP("AAL: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x\n",
		0x00000010, INREG32(module_base + 0x00000010),
		0x00000020, INREG32(module_base + 0x00000020),
		0x00000024, INREG32(module_base + 0x00000024),
		0x00000028, INREG32(module_base + 0x00000028));
	DDPDUMP("AAL: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x\n",
		0x0000002C, INREG32(module_base + 0x0000002C),
		0x00000030, INREG32(module_base + 0x00000030),
		0x000000B0, INREG32(module_base + 0x000000B0),
		0x000000C0, INREG32(module_base + 0x000000C0));
	DDPDUMP("AAL: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x\n",
		0x000000FC, INREG32(module_base + 0x000000FC),
		0x00000204, INREG32(module_base + 0x00000204),
		0x0000020C, INREG32(module_base + 0x0000020C),
		0x00000214, INREG32(module_base + 0x00000214));
	DDPDUMP("AAL: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x\n",
		0x0000021C, INREG32(module_base + 0x0000021C),
		0x00000224, INREG32(module_base + 0x00000224),
		0x00000228, INREG32(module_base + 0x00000228),
		0x0000022C, INREG32(module_base + 0x0000022C));
	DDPDUMP("AAL: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x\n",
		0x00000230, INREG32(module_base + 0x00000230),
		0x00000234, INREG32(module_base + 0x00000234),
		0x00000238, INREG32(module_base + 0x00000238),
		0x0000023C, INREG32(module_base + 0x0000023C));
	DDPDUMP("AAL: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x\n",
		0x00000240, INREG32(module_base + 0x00000240),
		0x00000244, INREG32(module_base + 0x00000244),
		0x00000248, INREG32(module_base + 0x00000248),
		0x0000024C, INREG32(module_base + 0x0000024C));
	DDPDUMP("AAL: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x\n",
		0x00000250, INREG32(module_base + 0x00000250),
		0x00000254, INREG32(module_base + 0x00000254),
		0x00000258, INREG32(module_base + 0x00000258),
		0x0000025C, INREG32(module_base + 0x0000025C));
	DDPDUMP("AAL: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x\n",
		0x00000260, INREG32(module_base + 0x00000260),
		0x00000264, INREG32(module_base + 0x00000264),
		0x00000268, INREG32(module_base + 0x00000268),
		0x0000026C, INREG32(module_base + 0x0000026C));
	DDPDUMP("AAL: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x\n",
		0x00000270, INREG32(module_base + 0x00000270),
		0x00000274, INREG32(module_base + 0x00000274),
		0x00000278, INREG32(module_base + 0x00000278),
		0x0000027C, INREG32(module_base + 0x0000027C));
	DDPDUMP("AAL: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x\n",
		0x00000280, INREG32(module_base + 0x00000280),
		0x00000284, INREG32(module_base + 0x00000284),
		0x00000288, INREG32(module_base + 0x00000288),
		0x0000028C, INREG32(module_base + 0x0000028C));
	DDPDUMP("AAL: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x\n",
		0x00000290, INREG32(module_base + 0x00000290),
		0x00000294, INREG32(module_base + 0x00000294),
		0x00000298, INREG32(module_base + 0x00000298),
		0x0000029C, INREG32(module_base + 0x0000029C));
	DDPDUMP("AAL: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x\n",
		0x000002A0, INREG32(module_base + 0x000002A0),
		0x000002A4, INREG32(module_base + 0x000002A4),
		0x00000358, INREG32(module_base + 0x00000358),
		0x0000035C, INREG32(module_base + 0x0000035C));
	DDPDUMP("AAL: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x\n",
		0x00000360, INREG32(module_base + 0x00000360),
		0x00000364, INREG32(module_base + 0x00000364),
		0x00000368, INREG32(module_base + 0x00000368),
		0x0000036C, INREG32(module_base + 0x0000036C));
	DDPDUMP("AAL: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x\n",
		0x00000370, INREG32(module_base + 0x00000370),
		0x00000374, INREG32(module_base + 0x00000374),
		0x00000378, INREG32(module_base + 0x00000378),
		0x0000037C, INREG32(module_base + 0x0000037C));
	DDPDUMP("AAL: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x\n",
		0x00000380, INREG32(module_base + 0x00000380),
		0x000003B0, INREG32(module_base + 0x000003B0),
		0x0000040C, INREG32(module_base + 0x0000040C),
		0x00000410, INREG32(module_base + 0x00000410));
	DDPDUMP("AAL: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x\n",
		0x00000414, INREG32(module_base + 0x00000414),
		0x00000418, INREG32(module_base + 0x00000418),
		0x0000041C, INREG32(module_base + 0x0000041C),
		0x00000420, INREG32(module_base + 0x00000420));
	DDPDUMP("AAL: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x\n",
		0x00000424, INREG32(module_base + 0x00000424),
		0x00000428, INREG32(module_base + 0x00000428),
		0x0000042C, INREG32(module_base + 0x0000042C),
		0x00000430, INREG32(module_base + 0x00000430));
	DDPDUMP("AAL: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x\n",
		0x00000434, INREG32(module_base + 0x00000434),
		0x00000440, INREG32(module_base + 0x00000440),
		0x00000444, INREG32(module_base + 0x00000444),
		0x00000448, INREG32(module_base + 0x00000448));
	DDPDUMP("-- END: DISP AAL%d registers --\n", i);
}

static void aal_dump_analysis(enum DISP_MODULE_ENUM module)
{
	int i;
	unsigned int offset = 0x1000;

	if (module == DISP_MODULE_AAL0)
		i = 0;
	else
		i = 1;


	DDPDUMP("== DISP AAL ANALYSIS ==\n");
	DDPDUMP("aal: bypass=%d, relay=%d, en=%d, w=%d, h=%d, in(%d,%d),out(%d,%d)\n",
		DISP_REG_GET(DISP_AAL_EN + i * offset) == 0x0,
		DISP_REG_GET(DISP_AAL_CFG + i * offset) & 0x01,
		DISP_REG_GET(DISP_AAL_EN + i * offset),
		(DISP_REG_GET(DISP_AAL_SIZE + i * offset) >> 16) & 0x1fff,
		DISP_REG_GET(DISP_AAL_SIZE + i * offset) & 0x1fff,
		DISP_REG_GET(DISP_AAL_IN_CNT + i * offset) & 0x1fff,
		(DISP_REG_GET(DISP_AAL_IN_CNT + i * offset) >> 16) & 0x1fff,
		DISP_REG_GET(DISP_AAL_OUT_CNT + i * offset) & 0x1fff,
		(DISP_REG_GET(DISP_AAL_OUT_CNT + i * offset) >> 16) & 0x1fff);
}

static void pwm_dump_reg(enum DISP_MODULE_ENUM module)
{
	unsigned long module_base = DISPSYS_PWM0_BASE;

	DDPDUMP("== START: DISP PWM0 registers ==\n");
	DDPDUMP("PWM0: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x\n",
		0x0, INREG32(module_base + 0x0),
		0x4, INREG32(module_base + 0x4),
		0x8, INREG32(module_base + 0x8),
		0xC, INREG32(module_base + 0xC));
	DDPDUMP("PWM0: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x\n",
		0x10, INREG32(module_base + 0x10),
		0x14, INREG32(module_base + 0x14),
		0x18, INREG32(module_base + 0x18),
		0x1C, INREG32(module_base + 0x1C));
	DDPDUMP("PWM0: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x\n",
		0x80, INREG32(module_base + 0x80),
		0x28, INREG32(module_base + 0x28),
		0x2C, INREG32(module_base + 0x2C),
		0x30, INREG32(module_base + 0x30));
	DDPDUMP("PWM0: 0x%04x=0x%08x\n",
		0xC0, INREG32(module_base + 0xC0));
	DDPDUMP("-- END: DISP PWM0 registers --\n");
}

static void pwm_dump_analysis(enum DISP_MODULE_ENUM module)
{
	int index = 0;
	unsigned int reg_base = 0;

	index = 0;
	reg_base = DISPSYS_PWM0_BASE;

	DDPDUMP("== DISP PWM%d ANALYSIS ==\n", index);
#if 0 /* TODO: clk reg?? */
	DDPDUMP("pwm clock=%d\n", (DISP_REG_GET(DISP_REG_CLK_CFG_1_CLR) >> 7) & 0x1);
#endif

}



static void ccorr_dump_reg(enum DISP_MODULE_ENUM module)
{
	int i;
	unsigned long module_base = DISPSYS_CCORR0_BASE;

	if (module == DISP_MODULE_CCORR0)
		i = 0;
	else
		i = 1;

	DDPDUMP("== START: DISP CCORR%i registers ==\n", i);
	DDPDUMP("CCORR: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x\n",
		0x000, INREG32(module_base + 0x000),
		0x004, INREG32(module_base + 0x004),
		0x008, INREG32(module_base + 0x008),
		0x00C, INREG32(module_base + 0x00C));
	DDPDUMP("CCORR: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x\n",
		0x010, INREG32(module_base + 0x010),
		0x020, INREG32(module_base + 0x020),
		0x024, INREG32(module_base + 0x024),
		0x028, INREG32(module_base + 0x028));
	DDPDUMP("CCORR: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x\n",
		0x02C, INREG32(module_base + 0x02C),
		0x030, INREG32(module_base + 0x030),
		0x080, INREG32(module_base + 0x080),
		0x084, INREG32(module_base + 0x084));
	DDPDUMP("CCORR: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x\n",
		0x088, INREG32(module_base + 0x088),
		0x08C, INREG32(module_base + 0x08C),
		0x090, INREG32(module_base + 0x090),
		0x0A0, INREG32(module_base + 0x0A0));
	DDPDUMP("CCORR: 0x%04x=0x%08x\n",
		0x0C0, INREG32(module_base + 0x0C0));
	DDPDUMP("-- END: DISP CCORR%d registers --\n", i);
}

static void ccorr_dump_analyze(enum DISP_MODULE_ENUM module)
{
	int i;
	unsigned int offset = 0x1000;

	if (module == DISP_MODULE_CCORR0)
		i = 0;
	else
		i = 1;


	DDPDUMP("ccorr: en=%d, config=%d, w=%d, h=%d, in_p_cnt=%d, in_l_cnt=%d, out_p_cnt=%d, out_l_cnt=%d\n",
	     DISP_REG_GET(DISP_REG_CCORR_EN + i * offset),
	     DISP_REG_GET(DISP_REG_CCORR_CFG + i * offset),
	     (DISP_REG_GET(DISP_REG_CCORR_SIZE + i * offset) >> 16) & 0x1fff,
	     DISP_REG_GET(DISP_REG_CCORR_SIZE + i * offset) & 0x1fff,
	     DISP_REG_GET(DISP_REG_CCORR_IN_CNT + i * offset) & 0x1fff,
	     (DISP_REG_GET(DISP_REG_CCORR_IN_CNT + i * offset) >> 16) & 0x1fff,
	     DISP_REG_GET(DISP_REG_CCORR_OUT_CNT + i * offset) & 0x1fff,
	     (DISP_REG_GET(DISP_REG_CCORR_OUT_CNT + i * offset) >> 16) & 0x1fff);
}

static void dither_dump_reg(enum DISP_MODULE_ENUM module)
{
	int i;
	unsigned long module_base = DISPSYS_DITHER0_BASE;

	if (module == DISP_MODULE_DITHER0)
		i = 0;
	else
		i = 1;

	DDPDUMP("== START: DISP DITHER%d registers ==\n", i);
	DDPDUMP("DITHER: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x\n",
		0x00000000, INREG32(module_base + 0x00000000),
		0x00000004, INREG32(module_base + 0x00000004),
		0x00000008, INREG32(module_base + 0x00000008),
		0x0000000C, INREG32(module_base + 0x0000000C));
	DDPDUMP("DITHER: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x\n",
		0x00000010, INREG32(module_base + 0x00000010),
		0x00000020, INREG32(module_base + 0x00000020),
		0x00000024, INREG32(module_base + 0x00000024),
		0x00000028, INREG32(module_base + 0x00000028));
	DDPDUMP("DITHER: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x\n",
		0x0000002C, INREG32(module_base + 0x0000002C),
		0x00000030, INREG32(module_base + 0x00000030),
		0x000000C0, INREG32(module_base + 0x000000C0),
		0x00000100, INREG32(module_base + 0x00000100));
	DDPDUMP("DITHER: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x\n",
		0x00000114, INREG32(module_base + 0x00000114),
		0x00000118, INREG32(module_base + 0x00000118),
		0x0000011C, INREG32(module_base + 0x0000011C),
		0x00000120, INREG32(module_base + 0x00000120));
	DDPDUMP("DITHER: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x\n",
		0x00000124, INREG32(module_base + 0x00000124),
		0x00000128, INREG32(module_base + 0x00000128),
		0x0000012C, INREG32(module_base + 0x0000012C),
		0x00000130, INREG32(module_base + 0x00000130));
	DDPDUMP("DITHER: 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x, 0x%04x=0x%08x\n",
		0x00000134, INREG32(module_base + 0x00000134),
		0x00000138, INREG32(module_base + 0x00000138),
		0x0000013C, INREG32(module_base + 0x0000013C),
		0x00000140, INREG32(module_base + 0x00000140));
	DDPDUMP("DITHER: 0x%04x=0x%08x\n",
		0x00000144, INREG32(module_base + 0x00000144));
	DDPDUMP("-- END: DISP DITHER%d registers --\n", i);

}


static void dither_dump_analyze(enum DISP_MODULE_ENUM module)
{
	int i;
	unsigned int offset = 0x1000;

	if (module == DISP_MODULE_DITHER0)
		i = 0;
	else
		i = 1;


	DDPDUMP
	    ("dither: en=%d, config=%d, w=%d, h=%d, in_p_cnt=%d, in_l_cnt=%d, out_p_cnt=%d, out_l_cnt=%d\n",
	     DISP_REG_GET(DISPSYS_DITHER0_BASE + 0x000 + i * offset),
	     DISP_REG_GET(DISPSYS_DITHER0_BASE + 0x020 + i * offset),
	     (DISP_REG_GET(DISP_REG_DITHER_SIZE + i * offset) >> 16) & 0x1fff,
	     DISP_REG_GET(DISP_REG_DITHER_SIZE + i * offset) & 0x1fff,
	     DISP_REG_GET(DISP_REG_DITHER_IN_CNT + i * offset) & 0x1fff,
	     (DISP_REG_GET(DISP_REG_DITHER_IN_CNT + i * offset) >> 16) & 0x1fff,
	     DISP_REG_GET(DISP_REG_DITHER_OUT_CNT + i * offset) & 0x1fff,
	     (DISP_REG_GET(DISP_REG_DITHER_OUT_CNT + i * offset) >> 16) & 0x1fff);
}

static void dsi_dump_reg(enum DISP_MODULE_ENUM module)
{
	DSI_DumpRegisters(module, 1);
#if 0
	if (DISP_MODULE_DSI0) {
		int i = 0;

		DDPDUMP("== DISP DSI0 REGS ==\n");
		for (i = 0; i < 25 * 16; i += 16) {
			DDPDUMP("DSI0+%04x: 0x%08x 0x%08x 0x%08x 0x%08x\n", i,
			       INREG32(DISPSYS_DSI0_BASE + i), INREG32(DISPSYS_DSI0_BASE + i + 0x4),
			       INREG32(DISPSYS_DSI0_BASE + i + 0x8),
			       INREG32(DISPSYS_DSI0_BASE + i + 0xc));
		}
		DDPDUMP("DSI0 CMDQ+0x200: 0x%08x 0x%08x 0x%08x 0x%08x\n",
		       INREG32(DISPSYS_DSI0_BASE + 0x200), INREG32(DISPSYS_DSI0_BASE + 0x200 + 0x4),
		       INREG32(DISPSYS_DSI0_BASE + 0x200 + 0x8),
		       INREG32(DISPSYS_DSI0_BASE + 0x200 + 0xc));
	}
#endif
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
	unsigned long module_base = ddp_get_module_va(module);

	DDPMSG("== DISP SPLIT0 ANALYSIS ==\n");
	DDPDUMP("split: en:%d,  input_line=%d, input_pixel=%d\n",
			DISP_REG_GET(module_base + 0x0) & 0x1,
			(DISP_REG_GET(module_base + 0x24) >> 16) & 0x1fff,
			DISP_REG_GET(module_base + 0x24) & 0x1fff);
	DDPDUMP("left_output_line=%d, left_output_pixel=%d\n",
			(DISP_REG_GET(module_base + 0x28) >> 16) & 0x1fff,
			DISP_REG_GET(module_base + 0x28) & 0x1fff);
	DDPDUMP("right_output_line=%d, right_output_pixel=%d\n",
			(DISP_REG_GET(module_base + 0x2c) >> 16) & 0x1fff,
			DISP_REG_GET(module_base + 0x2c) & 0x1fff);
	return 0;
}

int ddp_dump_reg(enum DISP_MODULE_ENUM module)
{
	switch (module) {
	case DISP_MODULE_WDMA0:
		wdma_dump_reg(module);
		break;
	case DISP_MODULE_RDMA0:
	case DISP_MODULE_RDMA1:
		rdma_dump_reg(module);
		break;
	case DISP_MODULE_OVL0:
	case DISP_MODULE_OVL0_2L:
	case DISP_MODULE_OVL1_2L:
		ovl_dump_reg(module);
		break;
	case DISP_MODULE_GAMMA0:
		gamma_dump_reg(module);
		break;
	case DISP_MODULE_CONFIG:
		mmsys_config_dump_reg();
		break;
	case DISP_MODULE_MUTEX:
		mutex_dump_reg();
		break;
	case DISP_MODULE_COLOR0:
		color_dump_reg(module);
		break;
	case DISP_MODULE_AAL0:
		aal_dump_reg(module);
		break;
	case DISP_MODULE_PWM0:
		pwm_dump_reg(module);
		break;
	case DISP_MODULE_DSI0:
	case DISP_MODULE_DSI1:
	case DISP_MODULE_DSIDUAL:
		dsi_dump_reg(module);
		break;
	case DISP_MODULE_CCORR0:
		ccorr_dump_reg(module);
		break;
	case DISP_MODULE_DITHER0:
		dither_dump_reg(module);
		break;
	case DISP_MODULE_RSZ:
		rsz_dump_reg(module);
		break;
	case DISP_MODULE_SPLIT0:
		split_dump_regs(module);
		break;
	default:
		DDPDUMP("no dump_reg for module %s(%d)\n", ddp_get_module_name(module), module);
	}
	return 0;
}

int ddp_dump_analysis(enum DISP_MODULE_ENUM module)
{
	switch (module) {
	case DISP_MODULE_WDMA0:
		wdma_dump_analysis(module);
		break;
	case DISP_MODULE_RDMA0:
	case DISP_MODULE_RDMA1:
		rdma_dump_analysis(module);
		break;
	case DISP_MODULE_OVL0:
	case DISP_MODULE_OVL0_2L:
	case DISP_MODULE_OVL1_2L:
		ovl_dump_analysis(module);
		break;
	case DISP_MODULE_GAMMA0:
		gamma_dump_analysis(module);
		break;
	case DISP_MODULE_CONFIG:
		mmsys_config_dump_analysis();
		break;
	case DISP_MODULE_MUTEX:
		mutex_dump_analysis();
		break;
	case DISP_MODULE_COLOR0:
		color_dump_analysis(module);
		break;
	case DISP_MODULE_AAL0:
		aal_dump_analysis(module);
		break;
	case DISP_MODULE_PWM0:
		pwm_dump_analysis(module);
		break;
	case DISP_MODULE_DSI0:
	case DISP_MODULE_DSI1:
	case DISP_MODULE_DSIDUAL:
		dsi_analysis(module);
		break;
	case DISP_MODULE_CCORR0:
		ccorr_dump_analyze(module);
		break;
	case DISP_MODULE_DITHER0:
		dither_dump_analyze(module);
		break;
	case DISP_MODULE_RSZ:
		rsz_dump_analysis(module);
		break;
	case DISP_MODULE_SPLIT0:
		split_dump_analysis(module);
		break;
	default:
		DDPDUMP("no dump_analysis for module %s(%d)\n", ddp_get_module_name(module),
			module);
	}
	return 0;
}
