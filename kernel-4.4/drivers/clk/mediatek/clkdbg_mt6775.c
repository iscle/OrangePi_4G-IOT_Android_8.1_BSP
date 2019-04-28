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

#include <linux/clk-provider.h>
#include <linux/io.h>

#include "clkdbg.h"
#include "mt6775_clkmgr.h"

#define ALL_CLK_ON		0
#define DUMP_INIT_STATE		0

/*
 * clkdbg dump_regs
 */

enum {
	topckgen,
	infracfg,
	scpsys,
	apmixed,
	audio,
	mfgsys,
	mmsys,
	imgsys,
	vdecsys,
	camsys,
	ipu_vcore_sys,
	ipu_conn_sys,
	ipu_core0_sys,
	ipu_core1_sys,
	ipu_core2_sys,
	pericfg,
	vencsys,
};

#define REGBASE_V(_phys, _id_name) { .phys = _phys, .name = #_id_name }

/*
 * checkpatch.pl ERROR:COMPLEX_MACRO
 *
 * #define REGBASE(_phys, _id_name) [_id_name] = REGBASE_V(_phys, _id_name)
 */

static struct regbase rb[] = {
	[topckgen] = REGBASE_V(0x10210000, topckgen),
	[infracfg] = REGBASE_V(0x10000000, infracfg),
	[scpsys]   = REGBASE_V(0x10a00000, scpsys),
	[apmixed]  = REGBASE_V(0x10212000, apmixed),
	[audio]    = REGBASE_V(0x10C00000, audio),
	[mfgsys]   = REGBASE_V(0x13000000, mfgsys),
	[mmsys]    = REGBASE_V(0x14000000, mmsys),
	[imgsys]   = REGBASE_V(0x15020000, imgsys),
	[vdecsys]  = REGBASE_V(0x16000000, vdecsys),
	[camsys]   = REGBASE_V(0x18000000, camsys),
	[ipu_vcore_sys]   = REGBASE_V(0x19020000, ipu_vcore_sys),
	[ipu_conn_sys]    = REGBASE_V(0x19000000, ipu_conn_sys),
	[ipu_core0_sys]   = REGBASE_V(0x19180000, ipu_core0_sys),
	[ipu_core1_sys]   = REGBASE_V(0x19280000, ipu_core1_sys),
	[ipu_core2_sys]   = REGBASE_V(0x19380000, ipu_core2_sys),
	[pericfg]  = REGBASE_V(0x11010000, pericfg),
	[vencsys]  = REGBASE_V(0x17000000, vencsys),
};

#define REGNAME(_base, _ofs, _name)	\
	{ .base = &rb[_base], .ofs = _ofs, .name = #_name }

static struct regname rn[] = {
	REGNAME(topckgen,  0x100, CLK_CFG_0),
	REGNAME(topckgen,  0x110, CLK_CFG_1),
	REGNAME(topckgen,  0x120, CLK_CFG_2),
	REGNAME(topckgen,  0x130, CLK_CFG_3),
	REGNAME(topckgen,  0x140, CLK_CFG_4),
	REGNAME(topckgen,  0x150, CLK_CFG_5),
	REGNAME(topckgen,  0x160, CLK_CFG_6),
	REGNAME(topckgen,  0x170, CLK_CFG_7),
	REGNAME(topckgen,  0x180, CLK_CFG_8),
	REGNAME(topckgen,  0x190, CLK_CFG_9),
	REGNAME(topckgen,  0x1a0, CLK_CFG_10),
	REGNAME(topckgen,  0x1b0, CLK_CFG_11),
	REGNAME(topckgen,  0x400, CLK_SCP_CFG_0),
	REGNAME(topckgen,  0x420, CLK_MISC_CFG_4),
	REGNAME(audio,	0x000, AUDIO_TOP_CON0),
	REGNAME(audio,	0x004, AUDIO_TOP_CON1),
	REGNAME(camsys,  0x000, CAMSYS_CG),
	REGNAME(imgsys,  0x000, IMG_CG),
	REGNAME(infracfg,  0x090, MODULE_SW_CG_0),
	REGNAME(infracfg,  0x094, MODULE_SW_CG_1),
	REGNAME(infracfg,  0x0b8, MODULE_SW_CG_3),
	REGNAME(ipu_vcore_sys,  0x000, IPU_CG),
	/*REGNAME(mfgsys,  0x000, MFG_CG),*/
	REGNAME(mmsys,	0x100, MMSYS_CG_CON0),
	REGNAME(mmsys,	0x110, MMSYS_CG_CON1),
	REGNAME(pericfg,  0x278, PERICFG_MODULE_SW_CG_0),
	REGNAME(pericfg,  0x288, PERICFG_MODULE_SW_CG_1),
	REGNAME(pericfg,  0x298, PERICFG_MODULE_SW_CG_2),
	REGNAME(pericfg,  0x2a8, PERICFG_MODULE_SW_CG_3),
	REGNAME(pericfg,  0x2b8, PERICFG_MODULE_SW_CG_4),
	REGNAME(vdecsys,  0x000, VDEC_CKEN),
	REGNAME(vdecsys,  0x008, VDEC_LARB1_CKEN),
	REGNAME(vencsys,  0x000, VENCSYS_CG),
	REGNAME(apmixed,  0x210, GPUPLL_CON0),
	REGNAME(apmixed,  0x214, GPUPLL_CON1),
	REGNAME(apmixed,  0x21C, GPUPLL_PWR_CON0),
	REGNAME(apmixed,  0x220, MPLL_CON0),
	REGNAME(apmixed,  0x224, MPLL_CON1),
	REGNAME(apmixed,  0x22C, MPLL_PWR_CON0),
	REGNAME(apmixed,  0x230, MAINPLL_CON0),
	REGNAME(apmixed,  0x234, MAINPLL_CON1),
	REGNAME(apmixed,  0x23C, MAINPLL_PWR_CON0),
	REGNAME(apmixed,  0x240, UNIVPLL_CON0),
	REGNAME(apmixed,  0x244, UNIVPLL_CON1),
	REGNAME(apmixed,  0x24C, UNIVPLL_PWR_CON0),
	REGNAME(apmixed,  0x250, MSDCPLL_CON0),
	REGNAME(apmixed,  0x254, MSDCPLL_CON1),
	REGNAME(apmixed,  0x25C, MSDCPLL_PWR_CON0),
	REGNAME(apmixed,  0x260, MMPLL_CON0),
	REGNAME(apmixed,  0x264, MMPLL_CON1),
	REGNAME(apmixed,  0x26C, MMPLL_PWR_CON0),
	REGNAME(apmixed,  0x280, TVDPLL_CON0),
	REGNAME(apmixed,  0x284, TVDPLL_CON1),
	REGNAME(apmixed,  0x28C, TVDPLL_PWR_CON0),
	REGNAME(apmixed,  0x290, EMIPLL_CON0),
	REGNAME(apmixed,  0x294, EMIPLL_CON1),
	REGNAME(apmixed,  0x29C, EMIPLL_PWR_CON0),
	REGNAME(apmixed,  0x2A0, APLL1_CON0),
	REGNAME(apmixed,  0x2A4, APLL1_CON1),
	REGNAME(apmixed,  0x2B8, APLL1_PWR_CON0),
	REGNAME(apmixed,  0x2C0, APLL2_CON0),
	REGNAME(apmixed,  0x2C4, APLL2_CON1),
	REGNAME(apmixed,  0x2D8, APLL2_PWR_CON0),
	REGNAME(apmixed,  0x310, ARMPLL1_CON0),
	REGNAME(apmixed,  0x314, ARMPLL1_CON1),
	REGNAME(apmixed,  0x31C, ARMPLL1_PWR_CON0),
	REGNAME(apmixed,  0x320, ARMPLL2_CON0),
	REGNAME(apmixed,  0x324, ARMPLL2_CON1),
	REGNAME(apmixed,  0x32C, ARMPLL2_PWR_CON0),
	REGNAME(apmixed,  0x330, ARMPLL3_CON0),
	REGNAME(apmixed,  0x334, ARMPLL3_CON1),
	REGNAME(apmixed,  0x33C, ARMPLL3_PWR_CON0),
	REGNAME(apmixed,  0x350, CCIPLL_CON0),
	REGNAME(apmixed,  0x354, CCIPLL_CON1),
	REGNAME(apmixed,  0x35C, CCIPLL_PWR_CON0),
	REGNAME(apmixed,  0x000, AP_PLL_CON0),
	REGNAME(apmixed,  0x004, AP_PLL_CON1),
	REGNAME(apmixed,  0x00C, AP_PLL_CON3),
	REGNAME(apmixed,  0x010, AP_PLL_CON4),
	REGNAME(scpsys,  0x0190, PWR_STATUS),
	REGNAME(scpsys,  0x0194, PWR_STATUS_2ND),
	REGNAME(scpsys,  0x0300, MFG0_PWR_CON),
	REGNAME(scpsys,  0x0304, MFG1_PWR_CON),
	REGNAME(scpsys,  0x0308, MFG2_PWR_CON),
	REGNAME(scpsys,  0x030C, MFG3_PWR_CON),
	REGNAME(scpsys,  0x0314, C2K_PWR_CON),
	REGNAME(scpsys,  0x0318, MD1_PWR_CON),
	REGNAME(scpsys,  0x0324, CONN_PWR_CON),
	REGNAME(scpsys,  0x032C, AUD_PWR_CON),
	REGNAME(scpsys,  0x0334, MM0_PWR_CON),
	REGNAME(scpsys,  0x0338, CAM_PWR_CON),
	REGNAME(scpsys,  0x033C, IPU_PWR_CON),
	REGNAME(scpsys,  0x0340, ISP_PWR_CON),
	REGNAME(scpsys,  0x0344, VEN_PWR_CON),
	REGNAME(scpsys,  0x0348, VDE_PWR_CON),
	{}
};

static const struct regname *get_all_regnames(void)
{
	return rn;
}

static void __init init_regbase(void)
{
	int i;

	for (i = 0; i < ARRAY_SIZE(rb); i++)
		rb[i].virt = ioremap(rb[i].phys, PAGE_SIZE);
}

/*
 * clkdbg fmeter
 */

#include <linux/delay.h>

#ifndef GENMASK
#define GENMASK(h, l)	(((1U << ((h) - (l) + 1)) - 1) << (l))
#endif

#define ALT_BITS(o, h, l, v) \
	(((o) & ~GENMASK(h, l)) | (((v) << (l)) & GENMASK(h, l)))

#define clk_readl(addr)		readl(addr)
#define clk_writel(addr, val)	\
	do { writel(val, addr); wmb(); } while (0) /* sync write */
#define clk_writel_mask(addr, h, l, v)	\
	clk_writel(addr, (clk_readl(addr) & ~GENMASK(h, l)) | ((v) << (l)))

#define ABS_DIFF(a, b)	((a) > (b) ? (a) - (b) : (b) - (a))

enum FMETER_TYPE {
	FT_NULL,
	ABIST,
	CKGEN
};

#define FMCLK(_t, _i, _n) { .type = _t, .id = _i, .name = _n }

static const struct fmeter_clk fclks[] = {
	FMCLK(CKGEN,  1, "hf_faxi_ck"),
	FMCLK(CKGEN,  2, "hf_fmem_ck"),
	FMCLK(CKGEN,  3, "hf_fddrphycfg_ck"),
	FMCLK(CKGEN,  4, "hf_fmm_ck"),
	FMCLK(CKGEN,  5, "hf_fdpi0_ck"),
	FMCLK(CKGEN,  6, "f_fpwm_ck"),
	FMCLK(CKGEN,  7, "f_fdispwm_ck"),
	FMCLK(CKGEN,  8, "hf_fvdec_ck"),
	FMCLK(CKGEN,  9, "hf_fvenc_ck"),
	FMCLK(CKGEN,  10, "hf_fmfg_ck"),
	FMCLK(CKGEN,  11, "hf_fcamtg_ck"),
	FMCLK(CKGEN,  12, "f_fcamtg2_ck"),
	FMCLK(CKGEN,  13, "f_fcamtg3_ck"),
	FMCLK(CKGEN,  14, "f_fcamtg4_ck"),
	FMCLK(CKGEN,  15, "f_fi2c_ck"),
	FMCLK(CKGEN,  16, "f_fuart_ck"),
	FMCLK(CKGEN,  17, "hf_fspi_ck"),
	FMCLK(CKGEN,  18, "f_fusb30_p0_ck"),
	FMCLK(CKGEN,  19, "hf_fmsdc50_0_hclk_ck"),
	FMCLK(CKGEN,  20, "hf_fmsdc50_0_ck"),
	FMCLK(CKGEN,  21, "hf_fmsdc30_1_ck"),
	FMCLK(CKGEN,  22, "f_fi3c_ck"),
	FMCLK(CKGEN,  23, "hf_fmsdc30_3_ck"),
	FMCLK(CKGEN,  24, "hf_fmsdc50_3_hclk_ck"),
	FMCLK(CKGEN,  25, "hf_faudio_ck"),
	FMCLK(CKGEN,  26, "hf_faud_intbus_ck"),
	FMCLK(CKGEN,  27, "hf_pmicspi_ck"),
	FMCLK(CKGEN,  28, "hf_fscp_ck"),
	FMCLK(CKGEN,  29, "hf_fatb_ck"),
	FMCLK(CKGEN,  30, "hf_fdsp_ck"),
	FMCLK(CKGEN,  31, "hf_faud_1_ck"),
	FMCLK(CKGEN,  32, "hf_faus_2_ck"),
	FMCLK(CKGEN,  33, "hf_faud_engen1_ck"),
	FMCLK(CKGEN,  34, "hf_faud_engen2_ck"),
	FMCLK(CKGEN,  35, "hf_fdfp_mfg_ck"),
	FMCLK(CKGEN,  36, "hf_fcam_ck"),
	FMCLK(CKGEN,  37, "hf_fipu_if_ck"),
	FMCLK(CKGEN,  38, "hf_fimg_ck"),
	FMCLK(CKGEN,  39, "hf_faes_ufsfde_ck"),
	FMCLK(CKGEN,  40, "hf_faudio_h_ck"),
	FMCLK(CKGEN,  41, "hf_fsspm_ck"),
	FMCLK(CKGEN,  42, "hf_fufs_card_ck"),
	FMCLK(CKGEN,  43, "hf_fbsi_spi_ck"),
	FMCLK(CKGEN,  44, "hf_fdxcc_ck"),
	FMCLK(CKGEN,  45, "f_fseninf_ck"),
	FMCLK(CKGEN,  46, "hf_fdfp_ck"),
	FMCLK(CKGEN,  47, "f_frtc_ck"),
	FMCLK(CKGEN,  48, "f_f26m_ck"),
	FMCLK(CKGEN,  49, "hf_fdsp1_ck"),
	FMCLK(CKGEN,  50, "hf_fdsp2_ck"),
	FMCLK(CKGEN,  51, "hf_fdsp3_ck"),
	FMCLK(ABIST,  1, "AD_CSI0A_CDPHY_DELAYCAL_CK"),
	FMCLK(ABIST,  2, "AD_CSI0B_CDPHY_DELAYCAL_CK"),
	FMCLK(ABIST,  3, "AD_CSI1A_CDPHY_DELAYCAL_CK"),
	FMCLK(ABIST,  4, "AD_CSI1B_CDPHY_DELAYCAL_CK"),
	FMCLK(ABIST,  5, "AD_CSI2A_CDPHY_DELAYCAL_CK"),
	FMCLK(ABIST,  6, "AD_CSI2B_CDPHY_DELAYCAL_CK"),
	FMCLK(ABIST,  7, "AD_DSI0_CKG_DSICLK"),
	FMCLK(ABIST,  8, "AD_DSI0_TEST_CK"),
	FMCLK(ABIST,  11, "AD_CCIPLL_CK_VCORE"),
	FMCLK(ABIST,  12, "AD_MMPLL_CK"),
	FMCLK(ABIST,  13, "AD_MDMCUPLL_CK"),
	FMCLK(ABIST,  14, "AD_MDINFRAPLL_CK"),
	FMCLK(ABIST,  15, "AD_BRPPLL_CK"),
	FMCLK(ABIST,  16, "AD_IMCPLL_CK"),
	FMCLK(ABIST,  17, "AD_ICCPLL_CK"),
	FMCLK(ABIST,  18, "AD_MPCPLL_CK"),
	FMCLK(ABIST,  19, "AD_DFEPLL_CK"),
	FMCLK(ABIST,  20, "AD_MD2GPLL_CK"),
	FMCLK(ABIST,  22, "AD_C2KCPPLL_CK"),
	FMCLK(ABIST,  23, "fmem_ck_bfe_dcm_ch0"),
	FMCLK(ABIST,  24, "fmem_ck_bfe_dcm_ch1"),
	FMCLK(ABIST,  25, "fmem_ck_aft_dcm_ch0"),
	FMCLK(ABIST,  26, "fmem_ck_aft_dcm_ch1"),
	FMCLK(ABIST,  27, "AD_RPHYPLL_DIV4_CK"),
	FMCLK(ABIST,  28, "AD_RCLRPLL_DIV4_CK"),
	FMCLK(ABIST,  29, "AD_PLLGP_TSTDIV2_CK"),
	FMCLK(ABIST,  30, "AD_APLLGP_TSTDIV2_CK"),
	FMCLK(ABIST,  32, "UFS_MP_CLK2FRE0"),
	FMCLK(ABIST,  34, "AD_ARMPLL_L_CK_VCORE"),
	FMCLK(ABIST,  35, "AD_ARMPLL_M_CK_VCORE"),
	FMCLK(ABIST,  37, "AD_OSC_SYNC_CK"),
	FMCLK(ABIST,  38, "AD_OSC_SYNC_CK_2"),
	FMCLK(ABIST,  39, "msdc01_in_ck"),
	FMCLK(ABIST,  40, "msdc02_in_ck"),
	FMCLK(ABIST,  41, "msdc11_in_ck"),
	FMCLK(ABIST,  42, "msdc12_in_ck"),
	FMCLK(ABIST,  43, "msdc31_in_ck "),
	FMCLK(ABIST,  44, "msdc32_in_ck"),
	FMCLK(ABIST,  45, "hd_fmem_ck_mon"),
	FMCLK(ABIST,  46, "AD_MPLL_CK"),
	FMCLK(ABIST,  55, "fmem_ck_bfe_dcm_ch0"),
	FMCLK(ABIST,  56, "fmem_ck_aft_dcm_ch0"),
	FMCLK(ABIST,  57, "fmem_ck_bfe_dcm_ch1"),
	FMCLK(ABIST,  58, "fmem_ck_aft_dcm_ch1"),
	FMCLK(ABIST,  71, "AD_MIANPLL_D7_CK"),
	FMCLK(ABIST,  72, "AD_UNIVPLL_D7_CK"),
	FMCLK(ABIST,  76, "AD_APLL1_CK"),
	FMCLK(ABIST,  77, "AD_APLL2_CK"),
	FMCLK(ABIST,  80, "AD_GPUPLL_CK"),
	FMCLK(ABIST,  84, "AD_MMPLL_D7_CK"),
	FMCLK(ABIST,  88, "AD_EMIPLL_CK"),
	FMCLK(ABIST,  89, "AD_MSDCPLL_CK"),
	FMCLK(ABIST,  90, "AD_OSC_CK"),
	FMCLK(ABIST,  91, "AD_OSC_CK_2"),
	FMCLK(ABIST,  93, "AD_USB_192M_CK"),
	{}
};


#define PLL_HP_CON0			(rb[apmixed].virt + 0x014)
#define PLL_TEST_CON1			(rb[apmixed].virt + 0x064)
#define TEST_DBG_CTRL			(rb[topckgen].virt + 0x38)
#define FREQ_MTR_CTRL_REG		(rb[topckgen].virt + 0x10)
#define FREQ_MTR_CTRL_RDATA		(rb[topckgen].virt + 0x14)

#define RG_FQMTR_CKDIV_GET(x)		(((x) >> 28) & 0x3)
#define RG_FQMTR_CKDIV_SET(x)		(((x) & 0x3) << 28)
#define RG_FQMTR_FIXCLK_SEL_GET(x)	(((x) >> 24) & 0x3)
#define RG_FQMTR_FIXCLK_SEL_SET(x)	(((x) & 0x3) << 24)
#define RG_FQMTR_MONCLK_SEL_GET(x)	(((x) >> 16) & 0x7f)
#define RG_FQMTR_MONCLK_SEL_SET(x)	(((x) & 0x7f) << 16)
#define RG_FQMTR_MONCLK_EN_GET(x)	(((x) >> 15) & 0x1)
#define RG_FQMTR_MONCLK_EN_SET(x)	(((x) & 0x1) << 15)
#define RG_FQMTR_MONCLK_RST_GET(x)	(((x) >> 14) & 0x1)
#define RG_FQMTR_MONCLK_RST_SET(x)	(((x) & 0x1) << 14)
#define RG_FQMTR_MONCLK_WINDOW_GET(x)	(((x) >> 0) & 0xfff)
#define RG_FQMTR_MONCLK_WINDOW_SET(x)	(((x) & 0xfff) << 0)

#define RG_FQMTR_CKDIV_DIV_2		0
#define RG_FQMTR_CKDIV_DIV_4		1
#define RG_FQMTR_CKDIV_DIV_8		2
#define RG_FQMTR_CKDIV_DIV_16		3

#define RG_FQMTR_FIXCLK_26MHZ		0
#define RG_FQMTR_FIXCLK_32KHZ		2

#define RG_FQMTR_EN     1
#define RG_FQMTR_RST    1

#define RG_FRMTR_WINDOW     519

#if 0 /*use other function*/
static u32 fmeter_freq(enum FMETER_TYPE type, int k1, int clk)
{
	u32 cnt = 0;

	/* reset & reset deassert */
	clk_writel(FREQ_MTR_CTRL_REG, RG_FQMTR_MONCLK_RST_SET(RG_FQMTR_RST));
	clk_writel(FREQ_MTR_CTRL_REG, RG_FQMTR_MONCLK_RST_SET(!RG_FQMTR_RST));

	/* set window and target */
	clk_writel(FREQ_MTR_CTRL_REG,
		RG_FQMTR_MONCLK_WINDOW_SET(RG_FRMTR_WINDOW) |
		RG_FQMTR_MONCLK_SEL_SET(clk) |
		RG_FQMTR_FIXCLK_SEL_SET(RG_FQMTR_FIXCLK_26MHZ) |
		RG_FQMTR_MONCLK_EN_SET(RG_FQMTR_EN));

	udelay(30);

	cnt = clk_readl(FREQ_MTR_CTRL_RDATA);
	/* reset & reset deassert */
	clk_writel(FREQ_MTR_CTRL_REG, RG_FQMTR_MONCLK_RST_SET(RG_FQMTR_RST));
	clk_writel(FREQ_MTR_CTRL_REG, RG_FQMTR_MONCLK_RST_SET(!RG_FQMTR_RST));

	return ((cnt * 26000) / (RG_FRMTR_WINDOW + 1));
}


static u32 measure_stable_fmeter_freq(enum FMETER_TYPE type, int k1, int clk)
{
	u32 last_freq = 0;
	u32 freq = fmeter_freq(type, k1, clk);
	u32 maxfreq = max(freq, last_freq);

	while (maxfreq > 0 && ABS_DIFF(freq, last_freq) * 100 / maxfreq > 10) {
		last_freq = freq;
		freq = fmeter_freq(type, k1, clk);
		maxfreq = max(freq, last_freq);
	}

	return freq;
}
#endif

static const struct fmeter_clk *get_all_fmeter_clks(void)
{
	return fclks;
}

struct bak {
	u32 pll_hp_con0;
	u32 pll_test_con1;
	u32 test_dbg_ctrl;
};

static u32 fmeter_freq_op(const struct fmeter_clk *fclk)
{
	if (fclk->type == ABIST)
		return mt_get_abist_freq(fclk->id);
	else if (fclk->type == CKGEN)
		return mt_get_ckgen_freq(fclk->id);
	return 0;
}

/*
 * clkdbg dump_state
 */

static const char * const *get_all_clk_names(void)
{
	static const char * const clks[] = {
		/* APMIXEDSYS */
		"mainpll",
		"univpll",
		"msdcpll",
		"gpupll",
		"mmpll",
		"apll1",
		"apll2",

		/* TOP */
		"axi_sel",
		"mem_sel",
		"ddrphycfg_sel",
		"mm_sel",
		"sflash_sel",
		"pwm_sel",
		"disppwm_sel",
		"vdec_sel",
		"venc_sel",
		"mfg_sel",
		"camtg_sel",
		"i2c_sel",
		"uart_sel",
		"spi_sel",
		"mem2_sel",
		"usb20_sel",
		"usb30_p0_sel",
		"msdc50_0_hclk_sel",
		"msdc50_0_sel",
		"msdc30_1_sel",
		"i3c_sel",
		"msdc30_3_sel",
		"msdc50_3_hclk_sel",
		"smi0_2x_sel",
		"audio_sel",
		"aud_intbus_sel",
		"pmicspi_sel",
		"scp_sel",
		"atb_sel",
		"mjc_sel",
		"dpi0_sel",
		"dsp_sel",
		"aud_1_sel",
		"aud_2_sel",
		"aud_engen1_sel",
		"aud_engen2_sel",
		"dfp_mfg_sel",
		"cam_sel",
		"ipu_if_sel",
		"smi1_2x_sel",
		"axi_mfg_in_as_sel",
		"img_sel",
		"ufo_enc_sel",
		"ufo_dec_sel",
		"pcie_mac_sel",
		"emi_sel",
		"aes_ufsfde_sel",
		"aes_fde_sel",
		"audio_h_sel",
		"sspm_sel",
		"ancmd32_sel",
		"slow_mfg_sel",
		"ufs_card_sel",
		"bsi_spi_sel",
		"dxcc_sel",
		"seninf_sel",
		"dfp_sel",

		/* INFRACFG */
		"infra_pmic_tmr",
		"infra_pmic_ap",
		"infra_pmic_md",
		"infra_pmic_conn",
		"infra_scp",
		"infra_sej",
		"infra_apxgpt",
		"infra_dvfsrc",
		"infra_gce",
		"infra_dbg",
		"infra_spm_apb_async",
		"infra_cldma_ap_top",
		"infra_ccif_3_set_0",
		"infra_aes_top0",
		"infra_aes_top1",
		"infra_devapc_mpu",
		"infra_ccif_3_set_1",
		"infra_md2md_ccif_set_0",
		"infra_md2md_ccif_set_1",
		"infra_md2md_ccif_set_2",
		"infra_fhctl",
		"infra_modem_temp_share",
		"infra_md2md_ccif_md_set_0",
		"infra_md2md_ccif_md_set_1",
		"infra_audio_dcm_en",
		"infra_cldma_ao_top_hclk",
		"infra_md2md_ccif_md_set_2",
		"infra_trng",
		"infra_auxadc",
		"infra_cpum",
		"infra_ccif1_ap",
		"infra_ccif1_md",
		"infra_ccif2_ap",
		"infra_ccif2_md",
		"infra_ccif4_ap",
		"infra_xiu_ckcg_set_for_dbg_ctrler",
		"infra_device_apc",
		"infra_ccif4_md",
		"infra_smi_l2c",
		"infra_ccif_ap",
		"infra_rg_dbg_ao_clk",
		"infra_audio",
		"infra_ccif_md",
		"infra_ccif5_ap",
		"infra_ccif5_md",
		"infra_rg_ft_l2c",
		"infra_spm_ahb",
		"infra_dramc_f26m",
		"infra_therm_bclk",
		"infra_ptp_bclk",
		"infra_auxadc_md",
		"infra_dvfs_ctrl_apb_rx"

		/* AUDIO */
		"aud_afe",
		"aud_i2s",
		"aud_22m",
		"aud_24m",
		"aud_apll2_tuner",
		"aud_apll_tuner",
		"aud_tdm",
		"aud_adc",
		"aud_dac",
		"aud_dac_predis",
		"aud_tml",
		"aud_i2s1_bclk_sw",
		"aud_i2s2_bclk_sw",
		"aud_i2s3_bclk_sw",
		"aud_i2s4_bclk_sw",
		"aud_i2s5_bclk_sw",
		"aud_adc_hires",
		"aud_adc_hires_tml",
		"aud_adda6_adc",
		"aud_adda6_adc_hires",
		/* CAM */
		"cam_larb6",
		"cam_dfp_vad",
		"cam_larb3",
		"cam",
		"camtg",
		"cam_seninf",
		"camsv0",
		"camsv1",
		"camsv2",
		"cam_ccu",
		/* IMG */
		"img_larb5",
		"img_larb2",
		"img_dip",
		"img_fdvt",
		"img_dpe",
		"img_rsc",
		/* IPU */
		"ipu",
		"ipu_isp",
		"ipu_dfp",
		"ipu_vad",
		"ipu_jtag",
		"ipu_axi",
		"ipu_ahb",
		"ipu_axi",
		"ipu_cam_axi",
		"ipu_img_axi",
		/* MFG */
		"mfg_bg3d",
		/* MM */
		"mm_smi_common",
		"mm_smi_larb0",
		"mm_smi_larb1",
		"mm_gals_comm0",
		"mm_gals_comm1",
		"mm_gals_venc2mm",
		"mm_gals_vdec2mm",
		"mm_gals_img2mm",
		"mm_gals_cam2mm",
		"mm_gals_ipu2mm",
		"mm_mdp_dl_tx_clock",
		"mm_ipu_dl_tx_clock",
		"mm_mdp_rdma0",
		"mm_mdp_rdma1",
		"mm_mdp_rsz0",
		"mm_mdp_rsz1",
		"mm_mdp_tdshp",
		"mm_mdp_wrot0",
		"mm_mdp_wrot1",
		"mm_fake_eng",
		"mm_disp_ovl0",
		"mm_disp_ovl0_2l",
		"mm_disp_ovl1_2l",
		"mm_disp_rdma0",
		"mm_disp_rdma1",
		"mm_disp_wdma0",
		"mm_disp_color0",
		"mm_disp_ccorr0",
		"mm_disp_aal0",
		"mm_disp_gamma0",
		"mm_disp_dither0",
		"mm_disp_split",
		"mm_dsi0_mm_clock",
		"mm_dsi0_interface_clock",
		"mm_dsi1_mm_clock",
		"mm_dsi1_interface_clock",
		"mm_fake_eng2",
		"mm_mdp_dl_rx_clock",
		"mm_ipu_dl_rx_clock",
		"mm_26m",
		"mmsys_r2y",
		"mm_disp_rsz",
		/* PERICFG */
		"pericfg_rg_pwm_bclk",
		"pericfg_rg_pwm_fbclk1",
		"pericfg_rg_pwm_fbclk2",
		"pericfg_rg_pwm_fbclk3",
		"pericfg_rg_pwm_fbclk4",
		"pericfg_rg_i2c0_bclk",
		"pericfg_rg_i2c1_bclk",
		"pericfg_rg_i2c2_bclk",
		"pericfg_rg_i2c3_bclk",
		"pericfg_rg_i2c4_bclk",
		"pericfg_rg_i2c5_bclk",
		"pericfg_rg_i2c6_bclk",
		"pericfg_rg_i2c7_bclk",
		"pericfg_rg_i2c8_bclk",
		"pericfg_rg_i2c9_bclk",
		"pericfg_rg_idvfs",
		"pericfg_rg_uart0",
		"pericfg_rg_uart1",
		"pericfg_rg_uart2",
		"pericfg_rg_spi0",
		"pericfg_rg_spi1",
		"pericfg_rg_spi2",
		"pericfg_rg_spi3",
		"pericfg_rg_spi4",
		"pericfg_rg_spi5",
		"pericfg_rg_spi6",
		"pericfg_rg_spi7",
		"pericfg_rg_spi8",
		"pericfg_rg_spi9",
		"pericfg_rg_msdc0_ck_ap_norm",
		"pericfg_rg_msdc1",
		"pericfg_rg_msdc2",
		"pericfg_rg_msdc3",
		"pericfg_rg_msdc4",
		"pericfg_rg_ufsdev",
		"pericfg_rg_ufsdev_mp_sap_cfg",
		"pericfg_rg_ufscard",
		"pericfg_rg_ufscard_mp_sap_cfg",
		"pericfg_rg_ufs_aes_core",
		"pericfg_rg_msdc0_ck_ap_secure",
		"pericfg_rg_msdc0_ck_md_secure",
		"pericfg_rg_usb_p0",
		"pericfg_rg_ap_dm",
		"pericfg_rg_disp_pwm0",
		"pericfg_rg_btif",
		"pericfg_rg_cq_dma",
		"pericfg_rg_mbist_mem_off_dly",
		"pericfg_rg_device_apc_peri",
		"pericfg_rg_dxcc_ao",
		"pericfg_rg_dxcc_pub",
		"pericfg_rg_dxcc_sec",
		"pericfg_rg_mtk_trng",
		"pericfg_rg_debugtop",
		/* VDEC */
		"vdec_cken",
		"vdec_larb1_cken",
		/* VENC */
		"venc_cke0",
		"venc_cke1",
		"venc_cke2",
		/* end */
		NULL
	};

	return clks;
}

/*
 * clkdbg pwr_status
 */

static const char * const *get_pwr_names(void)
{
	static const char * const pwr_names[] = {
		[0]  = "",
		[1]  = "MFG_ASYNC",
		[2]  = "MFG_TOP",
		[3]  = "MFG_SHADER0",
		[4]  = "MFG_SHADER1",
		[5]  = "MFG_SHADER2",
		[6]  = "C2K",
		[7]  = "MD1",
		[8]  = "DPHY_CH0",
		[9]  = "DPHY_CH1",
		[10] = "CONN",
		[11] = "INFRA",
		[12] = "AUD",
		[13] = "",/*NO MJC*/
		[14] = "MM0",
		[15] = "CAM",
		[16] = "IPU",
		[17] = "ISP",
		[18] = "VNE",
		[19] = "VDE",
		[20] = "",
		[21] = "",
		[22] = "",
		[23] = "",
		[24] = "",
		[25] = "",
		[26] = "",
		[27] = "",
		[28] = "",
		[29] = "",
		[30] = "",
		[31] = "",
	};

	return pwr_names;
}

/*
 * clkdbg dump_clks
 */

void setup_provider_clk(struct provider_clk *pvdck)
{
	static const struct {
		const char *pvdname;
		u32 pwr_mask;
	} pvd_pwr_mask[] = {
	};

	int i;
	const char *pvdname = pvdck->provider_name;

	if (!pvdname)
		return;

	for (i = 0; i < ARRAY_SIZE(pvd_pwr_mask); i++) {
		if (strcmp(pvdname, pvd_pwr_mask[i].pvdname) == 0) {
			pvdck->pwr_mask = pvd_pwr_mask[i].pwr_mask;
			return;
		}
	}
}

/*
 * chip_ver functions
 */

#include <linux/seq_file.h>
#include <mt-plat/mtk_chip.h>

static int clkdbg_chip_ver(struct seq_file *s, void *v)
{
	static const char * const sw_ver_name[] = {
		"CHIP_SW_VER_01",
		"CHIP_SW_VER_02",
		"CHIP_SW_VER_03",
		"CHIP_SW_VER_04",
	};

	#if 0 /*no support*/
	enum chip_sw_ver ver = mt_get_chip_sw_ver();

	seq_printf(s, "mt_get_chip_sw_ver(): %d (%s)\n", ver, sw_ver_name[ver]);
	#else
	seq_printf(s, "mt_get_chip_sw_ver(): %d (%s)\n", 0, sw_ver_name[0]);
	#endif

	return 0;
}

/*
 * init functions
 */

static struct clkdbg_ops clkdbg_mt6775_ops = {
	.get_all_fmeter_clks = get_all_fmeter_clks,
	.prepare_fmeter = NULL,
	.unprepare_fmeter = NULL,
	.fmeter_freq = fmeter_freq_op,
	.get_all_regnames = get_all_regnames,
	.get_all_clk_names = get_all_clk_names,
	.get_pwr_names = get_pwr_names,
	.setup_provider_clk = setup_provider_clk,
};

static void __init init_custom_cmds(void)
{
	static const struct cmd_fn cmds[] = {
		CMDFN("chip_ver", clkdbg_chip_ver),
		{}
	};

	set_custom_cmds(cmds);
}

static int __init clkdbg_mt6775_init(void)
{
	if (!of_machine_is_compatible("mediatek,mt6775"))
		return -ENODEV;

	init_regbase();

	init_custom_cmds();
	set_clkdbg_ops(&clkdbg_mt6775_ops);

#if ALL_CLK_ON
	prepare_enable_provider("topckgen");
	reg_pdrv("all");
	prepare_enable_provider("all");
#endif

#if DUMP_INIT_STATE
	print_regs();
	print_fmeter_all();
#endif /* DUMP_INIT_STATE */

	return 0;
}
device_initcall(clkdbg_mt6775_init);
