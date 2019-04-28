/*
 * Copyright (c) 2016 MediaTek Inc.
 * Author: Kevin Chen <Kevin-CW.Chen@mediatek.com>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2 as
 * published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */

#include <linux/delay.h>
#include <linux/of.h>
#include <linux/of_address.h>
#include <linux/slab.h>
#include <linux/mfd/syscon.h>

#include "clk-mtk.h"
#include "clk-gate.h"
#include "clk-mux.h"

#include <dt-bindings/clock/mt6758-clk.h>

#define MT_CCF_BRINGUP	0
/*fmeter div select 4*/
#define _DIV4_ 1

#ifdef CONFIG_ARM64
#define IOMEM(a)	((void __force __iomem *)((a)))
#endif

#define mt_reg_sync_writel(v, a) \
	do { \
		__raw_writel((v), IOMEM(a)); \
		/* sync up */ \
		mb(); } \
while (0)

#define clk_readl(addr)			__raw_readl(IOMEM(addr))

#define clk_writel(addr, val)   \
	mt_reg_sync_writel(val, addr)

#define clk_setl(addr, val) \
	mt_reg_sync_writel(clk_readl(addr) | (val), addr)

#define clk_clrl(addr, val) \
	mt_reg_sync_writel(clk_readl(addr) & ~(val), addr)

#define PLL_EN  (0x1 << 0)
#define PLL_PWR_ON  (0x1 << 0)
#define PLL_ISO_EN  (0x1 << 1)

const char *ckgen_array[] = {
"hf_faxi_ck",
"hf_fmem_ck",
"hf_fddrphycfg_ck",
"hf_fmm_ck",
"f_fpwm_ck",
"f_fdispwm_ck",
"hf_fvdec_ck",
"hf_fvenc_ck",
"hf_fmfg_ck",
"hf_fcamtg_ck",
"hf_fi2c_ck",
"hf_fuart_ck",
"hf_fspi_ck",
"f_fusb20_ck",
"f_fusb30_p0_ck",
"hf_fmsdc50_0_hclk_ck",
"hf_fmsdc50_0_ck",
"hf_fmsdc30_1_ck",
"f_fi3c_ck",
"hf_fmsdc_30_3_ck",
"hf_fmsdc50_3_hclk_ck",
"hf_faudio_ck",
"hf_faud_intbus_ck",
"hf_pmicspi_ck",
"hf_fscp_ck",
"hf_fatb_ck",
"hf_fdsp_ck",
"hf_faud_1_ck",
"hf_faus_2_ck",
"hf_faud_engen1_ck",
"hf_faud_engen2_ck",
"hf_fdfp_mfg_ck",
"hf_fcam_ck",
"hf_fipu_if_ck",
"hf_faxi_mfg_in_as_ck",
"hf_fimg_ck",
"hf_faes_ufsfde_ck",
"hf_faes_fde_ck",
"hf_faudio_h_ck",
"hf_fsspm_ck",
"hf_fufs_card_ck",
"hf_fbsi_spi_ck",
"hf_fdxcc_ck",
"f_fseninf_ck",
"hf_fdfp_ck",
};

const char *abist_array[] = {
"AD_CSI0A_CDPHY_DELAYCAL_CK",
"AD_CSI0B_CDPHY_DELAYCAL_CK",
"AD_CSI1A_CDPHY_DELAYCAL_CK",
"AD_CSI1B_CDPHY_DELAYCAL_CK",
"AD_CSI2A_CDPHY_DELAYCAL_CK",
"AD_CSI2B_CDPHY_DELAYCAL_CK",
"AD_DSI0_CKG_DSICLK",
"AD_DSI0_TEST_CK",
"AD_DSI1_CKG_DSICLK",
"AD_DSI1_TEST_CK",
"AD_CCIPLL_CK_VCORE",
"AD_MMPLL_CK",
"AD_MDMCUPLL_CK",
"AD_MDINFRAPLL_CK",
"AD_BRPPLL_CK",
"AD_IMCPLL_CK",
"AD_ICCPLL_CK",
"AD_MPCPLL_CK",
"AD_DFEPLL_CK",
"AD_MD2GPLL_CK",
"AD_RAKEPLL_CK",
"AD_C2KCPPLL_CK",
"MCK_BEF_DCM_CHA",
"MCK_BEF_DCM_CHB",
"MCK_AFT_DCM_CHA",
"MCK_AFT_DCM_CHB",
"AD_RPHYPLL_DIV4_CK",
"AD_RCLRPLL_DIV4_CK",
"AD_PLLGP_TSTDIV2_CK",
"AD_MP_PLL0_CK_ABIST_OUT",
"AD_MP_RX0_TSTCK_DIV2",
"mp_tx_mon_div2_ck",
"mp_rx0_mon_div2_ck",
"AD_ARMPLL_L_CK_VCORE",
"AD_ARMPLL_M_CK_VCORE",
"AD_ARMPLL_B_CK_VCORE",
"AD_OSC_SYNC_CK_gated(PLL_ULPOSC_CON0[7]=1)",
"AD_OSC_SYNC_CK_2_gated(PLL_ULPOSC_CON2[7]=1)",
"msdc01_in_ck",
"msdc02_in_ck",
"msdc11_in_ck",
"msdc12_in_ck",
"msdc31_in_ck ",
"msdc32_in_ck",
"hd_fmem_ck_mon_gated(MEM_DCM_CTRL[27]=1)",
"AD_MPLL_CK",
"NA",
"NA",
"NA",
"NA",
"NA",
"AD_USB_192M_CK",
"AD_APLL1_CK",
"AD_APLL2_CK",
"AD_EMIPLL_CK",
"AD_GPUPLL_CK",
"AD_ LTEPLL_FS26M_CK",
"AD_ MDPLL1_FS208M_CK",
"AD_ MAINPLL_CK",
"AD_ UNIVPLL_CK",
"AD_ MSDCPLL_806M_CK",
"AD_ OSC_CK",
"AD_ OSC_CK_2",
"NA",
"DRAMC_CH0_CGCLK",
"NA",
"NA",
"AD_MAIN_H546M_CK",
"AD_MAIN_H364M_CK",
"AD_MAIN_H218P4M_CK",
"AD_MAIN_H156M_CK",
"AD_UNIVPLL_356P6M_CK",
"AD_UNIVPLL_624M_CK",
"AD_UNIVPLL_832M_CK",
"AD_UNIVPLL_499P2M_CK",
"AD_APLL1_CK",
"AD_APLL2_CK",
"AD_LTEPLL_FS26M_CK",
"rc32k_ck_i",
"AD_GPUPLL_CK",
"NA",
"AD_MMPLL_D5_CK",
"AD_MMPLL_D6_CK",
"AD_MMPLL_D7_CK",
"AD_MDPLL1_FS208M_CK_gated	(TST_SEL_3[31]=1)",
"NA",
"NA",
"AD_EMIPLL_CK",
"AD_MSDCPLL_806M_CK",
"AD_OSC_CK",
"AD_OSC_CK_2",
"fpc_ck",
"AD_USB_192M_CK",
"test_sel_1[1]",
"test_sel_1[2]",
};

static DEFINE_SPINLOCK(mt6758_clk_lock);

/* Total 12 subsys */
void __iomem *cksys_base;
void __iomem *infracfg_base;
void __iomem *apmixed_base;
void __iomem *audio_base;
void __iomem *cam_base;
void __iomem *img_base;
void __iomem *ipu_base;
void __iomem *mfgcfg_base;
void __iomem *mmsys_config_base;
void __iomem *pericfg_base;
void __iomem *vdec_gcon_base;
void __iomem *venc_gcon_base;

/* CKSYS */
#define TST_SEL_1		(cksys_base + 0x024)
#define CLK_CFG_0		(cksys_base + 0x100)
#define CLK_CFG_1		(cksys_base + 0x110)
#define CLK_CFG_2		(cksys_base + 0x120)
#define CLK_CFG_7		(cksys_base + 0x170)
#define CLK_CFG_9		(cksys_base + 0x190)
#define CLK_CFG_20		(cksys_base + 0x210)
#define CLK_CFG_21		(cksys_base + 0x214)
#define CLK_MISC_CFG_1		(cksys_base + 0x414)
#define CLK26CALI_0		(cksys_base + 0x520)
#define CLK26CALI_1		(cksys_base + 0x524)
#define CLK26CALI_2		(cksys_base + 0x528)
/*#define TOP_CLK2		(cksys_base + 0x0120)*/
#define CLK_SCP_CFG_0		(cksys_base + 0x0400)
#define CLK_SCP_CFG_1		(cksys_base + 0x0404)

/* CG */
#define INFRA_PDN_SET0		(infracfg_base + 0x0080)
#define INFRA_PDN_SET1		(infracfg_base + 0x0088)
#define INFRA_PDN_SET3		(infracfg_base + 0x00B0)
#define INFRA_TOPAXI_SI0_CTL	(infracfg_base + 0x0200)

#define AP_PLL_CON1		(apmixed_base + 0x0004)
#define AP_PLL_CON3		(apmixed_base + 0x000C)
#define AP_PLL_CON4		(apmixed_base + 0x0010)
#define AP_PLL_CON8		(apmixed_base + 0x0020)
#define GPUPLL_CON0		(apmixed_base + 0x0210)
#define GPUPLL_CON1		(apmixed_base + 0x0214)
#define GPUPLL_PWR_CON0		(apmixed_base + 0x021C)
#define UNIVPLL_CON0		(apmixed_base + 0x0240)
#define UNIVPLL_PWR_CON0	(apmixed_base + 0x024C)
#define MSDCPLL_CON0		(apmixed_base + 0x0250)
#define MSDCPLL_PWR_CON0	(apmixed_base + 0x025C)
#define MMPLL_CON0		(apmixed_base + 0x0260)
#define MMPLL_PWR_CON0		(apmixed_base + 0x026C)
#define TVDPLL_CON0		(apmixed_base + 0x0280)
#define TVDPLL_PWR_CON0		(apmixed_base + 0x028C)
#define APLL1_CON0		(apmixed_base + 0x02A0)
#define APLL1_PWR_CON0		(apmixed_base + 0x02B8)
#define APLL2_CON0		(apmixed_base + 0x02C0)
#define APLL2_PWR_CON0		(apmixed_base + 0x02D8)

#define ARMPLL1_CON0		(apmixed_base + 0x0310)
#define ARMPLL1_CON1		(apmixed_base + 0x0314)
#define ARMPLL1_PWR_CON0	(apmixed_base + 0x031C)
#define ARMPLL2_CON0		(apmixed_base + 0x0320)
#define ARMPLL2_CON1		(apmixed_base + 0x0324)
#define ARMPLL2_PWR_CON0	(apmixed_base + 0x032C)
#define CCIPLL_CON0		(apmixed_base + 0x0350)
#define CCIPLL_CON1		(apmixed_base + 0x0354)
#define CCIPLL_PWR_CON0		(apmixed_base + 0x035C)
#define MPLL_CON0		(apmixed_base + 0x0220)
#define MAINPLL_CON0		(apmixed_base + 0x0230)
#define EMIPLL_CON0		(apmixed_base + 0x0290)

#define AUDIO_TOP_CON0		(audio_base + 0x0000)
#define AUDIO_TOP_CON1		(audio_base + 0x0004)

#define CAMSYS_CG_CON		(cam_base + 0x0000)
#define CAMSYS_CG_SET		(cam_base + 0x0004)
#define CAMSYS_CG_CLR		(cam_base + 0x0008)

#define IMG_CG_CON		(img_base + 0x0000)
#define IMG_CG_SET		(img_base + 0x0004)
#define IMG_CG_CLR		(img_base + 0x0008)

#define IPU_CG_CON              (ipu_base + 0x0000)
#define IPU_CG_SET              (ipu_base + 0x0004)
#define IPU_CG_CLR              (ipu_base + 0x0008)

#define MFG_CG_CON              (mfgcfg_base + 0x0000)
#define MFG_CG_SET              (mfgcfg_base + 0x0004)
#define MFG_CG_CLR              (mfgcfg_base + 0x0008)

#define MM_CG_CON0            (mmsys_config_base + 0x100)
#define MM_CG_SET0            (mmsys_config_base + 0x104)
#define MM_CG_CLR0            (mmsys_config_base + 0x108)
#define MM_CG_CON1            (mmsys_config_base + 0x110)
#define MM_CG_SET1            (mmsys_config_base + 0x114)
#define MM_CG_CLR1            (mmsys_config_base + 0x118)

#define PERI_CG_SET0             (pericfg_base + 0x0270)
#define PERI_CG_CLR0             (pericfg_base + 0x0274)
#define PERI_CG_STA0             (pericfg_base + 0x0278)
#define PERI_CG_SET1             (pericfg_base + 0x0280)
#define PERI_CG_CLR1             (pericfg_base + 0x0284)
#define PERI_CG_STA1             (pericfg_base + 0x0288)
#define PERI_CG_SET2             (pericfg_base + 0x0290)
#define PERI_CG_CLR2             (pericfg_base + 0x0294)
#define PERI_CG_STA2             (pericfg_base + 0x0298)
#define PERI_CG_SET3             (pericfg_base + 0x02A0)
#define PERI_CG_CLR3             (pericfg_base + 0x02A4)
#define PERI_CG_STA3             (pericfg_base + 0x02A8)
#define PERI_CG_SET4             (pericfg_base + 0x02B0)
#define PERI_CG_CLR4             (pericfg_base + 0x02B4)
#define PERI_CG_STA4             (pericfg_base + 0x02B8)

#define VDEC_CKEN_SET           (vdec_gcon_base + 0x0000)
#define VDEC_CKEN_CLR           (vdec_gcon_base + 0x0004)
#define LARB1_CKEN_SET          (vdec_gcon_base + 0x0008)
#define LARB1_CKEN_CLR          (vdec_gcon_base + 0x000C)


#define VENC_CG_CON		(venc_gcon_base + 0x0000)
#define VENC_CG_SET		(venc_gcon_base + 0x0004)
#define VENC_CG_CLR		(venc_gcon_base + 0x0008)

#define INFRA_CG0 0x06E9CD00
#define INFRA_CG1 0x0248CA76
#define INFRA_CG3 0xBEFFFF7F

#define PERI_CG0 0x7FFFFFFF
#define PERI_CG1 0xFFFFFFFE
#define PERI_CG2 0xFFFFFFFF
#define PERI_CG3 0xFFFFFFFF
#define PERI_CG4 0xFFEFFCFF

/* AO */
#define AUDIO_DISABLE_CG0	0x80114000
#define AUDIO_DISABLE_CG1	0x00000000
#define CAM_DISABLE_CG		0x00001fc7
#define IMG_DISABLE_CG		0x0000003F
#define MFG_DISABLE_CG		0x00000001
#define VDE_DISABLE_CG		0x00000001
#define LARB1_DISABLE_CG	0x00000001
#define VEN_DISABLE_CG		0x00000111
#define IPU_DISABLE_CG		0x000003FF
#define MM_DISABLE_CG0		0xFFFFFFFF
#define MM_DISABLE_CG1		0xFFFFFFFF

#define CK_CFG_0 0x100
#define CK_CFG_0_SET 0x104
#define CK_CFG_0_CLR 0x108
#define CK_CFG_1 0x110
#define CK_CFG_1_SET 0x114
#define CK_CFG_1_CLR 0x118
#define CK_CFG_2 0x120
#define CK_CFG_2_SET 0x124
#define CK_CFG_2_CLR 0x128
#define CK_CFG_3 0x130
#define CK_CFG_3_SET 0x134
#define CK_CFG_3_CLR 0x138
#define CK_CFG_4 0x140
#define CK_CFG_4_SET 0x144
#define CK_CFG_4_CLR 0x148
#define CK_CFG_5 0x150
#define CK_CFG_5_SET 0x154
#define CK_CFG_5_CLR 0x158
#define CK_CFG_6 0x160
#define CK_CFG_6_SET 0x164
#define CK_CFG_6_CLR 0x168
#define CK_CFG_7 0x170
#define CK_CFG_7_SET 0x174
#define CK_CFG_7_CLR 0x178
#define CK_CFG_8 0x180
#define CK_CFG_8_SET 0x184
#define CK_CFG_8_CLR 0x188
#define CK_CFG_9 0x190
#define CK_CFG_9_SET 0x194
#define CK_CFG_9_CLR 0x198
#define CK_CFG_10 0x1a0
#define CK_CFG_10_SET 0x1a4
#define CK_CFG_10_CLR 0x1a8
#define CK_CFG_11 0x1b0
#define CK_CFG_11_SET 0x1b4
#define CK_CFG_11_CLR 0x1b8


static const struct mtk_fixed_clk fixed_clks[] __initconst = {
	FIXED_CLK(CLK_TOP_CLK26M, "f_f26m_ck", "clk26m", 26000000),
};

static const struct mtk_fixed_factor top_divs[] __initconst = {
	FACTOR(CLK_TOP_26M_D2, "ad_sys_26m_ck_d2", "clk26m",
		1, 2),
	FACTOR(CLK_TOP_ARMPLL1, "armpll1_ck", "armpll_m",
		1, 1),
	FACTOR(CLK_TOP_ARMPLL2, "armpll2_ck", "armpll_l",
		1, 1),
	FACTOR(CLK_TOP_CCIPLL, "ccipll_ck", "ccipll",
		1, 1),

	FACTOR(CLK_TOP_SYSPLL, "syspll_ck", "mainpll",
		1, 1),

	FACTOR(CLK_TOP_SYSPLL_D2, "syspll_d2", "mainpll",
		1, 2),
	FACTOR(CLK_TOP_SYSPLL1_D2, "syspll1_d2", "syspll_d2",
		1, 2),
	FACTOR(CLK_TOP_SYSPLL1_D4, "syspll1_d4", "syspll_d2",
		1, 4),
	FACTOR(CLK_TOP_SYSPLL1_D8, "syspll1_d8", "syspll_d2",
		1, 8),
	FACTOR(CLK_TOP_SYSPLL1_D16, "syspll1_d16", "syspll_d2",
		1, 16),


	FACTOR(CLK_TOP_SYSPLL_D3, "syspll_d3", "mainpll",
		1, 3),
	FACTOR(CLK_TOP_SYSPLL2_D2, "syspll2_d2", "syspll_d3",
		1, 2),
	FACTOR(CLK_TOP_SYSPLL2_D4, "syspll2_d4", "syspll_d3",
		1, 4),
	FACTOR(CLK_TOP_SYSPLL2_D8, "syspll2_d8", "syspll_d3",
		1, 8),
	FACTOR(CLK_TOP_SYSPLL2_D3, "syspll2_d3", "syspll_d3",
		1, 3),


	FACTOR(CLK_TOP_SYSPLL_D5, "syspll_d5", "mainpll",
		1, 5),
	FACTOR(CLK_TOP_SYSPLL3_D2, "syspll3_d2", "syspll_d5",
		1, 2),
	FACTOR(CLK_TOP_SYSPLL3_D4, "syspll3_d4", "syspll_d5",
		1, 4),


	FACTOR(CLK_TOP_SYSPLL_D7, "syspll_d7", "mainpll",
		1, 7),
	FACTOR(CLK_TOP_SYSPLL4_D2, "syspll4_d2", "syspll_d7",
		1, 2),
	FACTOR(CLK_TOP_SYSPLL4_D4, "syspll4_d4", "syspll_d7",
		1, 4),


	FACTOR(CLK_TOP_UNIVPLL, "univpll_ck", "univpll",
		1, 1),
	FACTOR(CLK_TOP_UNIVPLL_D26, "univpll_d26", "univpll",
		1, 26),
	FACTOR(CLK_TOP_USB_PHY48M, "usb_phy48m_ck", "univpll",
		1, 52),

	FACTOR(CLK_TOP_UNIVPLL_D2, "univpll_d2", "univpll",
		1, 2),
	FACTOR(CLK_TOP_UNIVPLL1_D2, "univpll1_d2", "univpll_d2",
		1, 2),
	FACTOR(CLK_TOP_UNIVPLL1_D4, "univpll1_d4", "univpll_d2",
		1, 4),
	FACTOR(CLK_TOP_UNIVPLL1_D8, "univpll1_d8", "univpll_d2",
		1, 8),
	FACTOR(CLK_TOP_UNIVPLL1_D16, "univpll1_d16", "univpll_d2",
		1, 16),


	FACTOR(CLK_TOP_UNIVPLL_D3, "univpll_d3", "univpll",
		1, 3),
	FACTOR(CLK_TOP_UNIVPLL2_D2, "univpll2_d2", "univpll_d3",
		1, 2),
	FACTOR(CLK_TOP_UNIVPLL2_D4, "univpll2_d4", "univpll_d3",
		1, 4),
	FACTOR(CLK_TOP_UNIVPLL2_D8, "univpll2_d8", "univpll_d3",
		1, 8),
	FACTOR(CLK_TOP_UNIVPLL2_D16, "univpll2_d16", "univpll_d3",
		1, 16),


	FACTOR(CLK_TOP_UNIVPLL_D5, "univpll_d5", "univpll",
		1, 5),
	FACTOR(CLK_TOP_UNIVPLL3_D2, "univpll3_d2", "univpll_d5",
		1, 2),
	FACTOR(CLK_TOP_UNIVPLL3_D4, "univpll3_d4", "univpll_d5",
		1, 4),
	FACTOR(CLK_TOP_UNIVPLL3_D8, "univpll3_d8", "univpll_d5",
		1, 8),
#if 0
	FACTOR(CLK_TOP_UNIVPLL3_D16, "univpll3_d16", "univpll_d5",
		1, 16),
#endif
	FACTOR(CLK_TOP_UNIVPLL_D7, "univpll_d7", "univpll",
		1, 7),
	FACTOR(CLK_TOP_UNIVPLL4_D2, "univpll4_d2", "univpll_d7",
		1, 2),
	FACTOR(CLK_TOP_UNIVPLL4_D4, "univpll4_d4", "univpll_d7",
		1, 4),
	FACTOR(CLK_TOP_UNIVPLL4_D8, "univpll4_d8", "univpll_d7",
		1, 8),

	FACTOR(CLK_TOP_UNIVPLL_D2_2ND, "univpll_d2_2nd", "univpll",
		1, 2),
	FACTOR(CLK_TOP_UNIVPLL_D52, "univpll_d52", "univpll",
		1, 52),
	FACTOR(CLK_TOP_UNIVPLL_D104, "univpll_d104", "univpll",
		1, 104),
	FACTOR(CLK_TOP_UNIVPLL_D208, "univpll_d208", "univpll",
		1, 208),
	FACTOR(CLK_TOP_UNIVPLL_D416, "univpll_d416", "univpll",
		1, 416),

	FACTOR(CLK_TOP_TVDPLL, "tvdpll_ck", "tvdpll",
		1, 1),
	FACTOR(CLK_TOP_TVDPLL_D2, "tvdpll_d2", "tvdpll",
		1, 2),
	FACTOR(CLK_TOP_TVDPLL_D4, "tvdpll_d4", "tvdpll",
		1, 4),
	FACTOR(CLK_TOP_TVDPLL_D8, "tvdpll_d8", "tvdpll",
		1, 8),
	FACTOR(CLK_TOP_TVDPLL_D16, "tvdpll_d16", "tvdpll",
		1, 16),


	FACTOR(CLK_TOP_APLL1, "apll1_ck", "apll1",
		1, 1),
	FACTOR(CLK_TOP_APLL1_D2, "apll1_d2", "apll1",
		1, 2),
	FACTOR(CLK_TOP_APLL1_D4, "apll1_d4", "apll1",
		1, 4),
	FACTOR(CLK_TOP_APLL1_D8, "apll1_d8", "apll1",
		1, 8),
	FACTOR(CLK_TOP_APLL2, "apll2_ck", "apll2",
		1, 1),
	FACTOR(CLK_TOP_APLL2_D2, "apll2_d2", "apll2",
		1, 2),
	FACTOR(CLK_TOP_APLL2_D4, "apll2_d4", "apll2",
		1, 4),
	FACTOR(CLK_TOP_APLL2_D8, "apll2_d8", "apll2",
		1, 8),


	FACTOR(CLK_TOP_GPUPLL, "gpupll_ck", "gpupll",
		1, 1),

	FACTOR(CLK_TOP_MMPLL, "mmpll_ck", "mmpll",
		1, 1),
	FACTOR(CLK_TOP_MMPLL_D4, "mmpll_d4", "mmpll",
		1, 4),
	FACTOR(CLK_TOP_MMPLL_D5, "mmpll_d5", "mmpll",
		1, 5),
	FACTOR(CLK_TOP_MMPLL_D6, "mmpll_d6", "mmpll",
		1, 6),
	FACTOR(CLK_TOP_MMPLL_D7, "mmpll_d7", "mmpll",
		1, 7),
	FACTOR(CLK_TOP_MMPLL_D8, "mmpll_d8", "mmpll",
		1, 8),
	FACTOR(CLK_TOP_MMPLL_D10, "mmpll_d10", "mmpll",
		1, 10),
	FACTOR(CLK_TOP_MMPLL_D16, "mmpll_d16", "mmpll",
		1, 16),
	FACTOR(CLK_TOP_MMPLL_D20, "mmpll_d20", "mmpll",
		1, 20),

	FACTOR(CLK_TOP_EMIPLL, "emipll_ck", "emipll",
		1, 1),

	FACTOR(CLK_TOP_MSDCPLL, "msdcpll_ck", "msdcpll",
		1, 1),
	FACTOR(CLK_TOP_MSDCPLL_D2, "msdcpll_d2", "msdcpll",
		1, 2),
	FACTOR(CLK_TOP_MSDCPLL_D4, "msdcpll_d4", "msdcpll",
		1, 4),
	FACTOR(CLK_TOP_MSDCPLL_D8, "msdcpll_d8", "msdcpll",
		1, 8),
	FACTOR(CLK_TOP_MSDCPLL_D16, "msdcpll_d16", "msdcpll",
		1, 16),

	FACTOR(CLK_TOP_ULPOSCPLL, "ulposcpll_ck", "ulposc",
		1, 1),
	FACTOR(CLK_TOP_ULPOSCPLL_D2, "ulposcpll_d2", "ulposc",
		1, 2),
	FACTOR(CLK_TOP_ULPOSCPLL_D4, "ulposcpll_d4", "ulposc",
		1, 4),
	FACTOR(CLK_TOP_ULPOSCPLL_D8, "ulposcpll_d8", "ulposc",
		1, 8),
	FACTOR(CLK_TOP_ULPOSCPLL_D16, "ulposcpll_d16", "ulposc",
		1, 16),
#if 0
	FACTOR(CLK_TOP_F_F26M, "f_f26m_ck", "f26m_sel",
		1, 1),
#endif
	FACTOR(CLK_TOP_F_FRTC, "f_frtc_ck", "clk32k",
		1, 1),

	FACTOR(CLK_TOP_CLK13M, "clk13m", "clk26m", 1,
		2),
#if 0
	FACTOR(CLK_TOP_ULPOSC, "ulposc", "clk_null", 1,
		1),
#endif
};

static const char * const axi_parents[] __initconst = {
	"clk26m",
	"syspll_d5",
	"syspll1_d4",
	"syspll2_d2",
	"univpll3_d4",
	"univpll2_d4",
	"syspll3_d2",
	"syspll_d7"
};

static const char * const mem_parents[] __initconst = {
	"clk26m",
	"univpll3_d4",
	"emipll_ck",
	"syspll_d3"
};

static const char * const ddrphycfg_parents[] __initconst = {
	"clk26m",
	"syspll1_d8"
};

static const char * const mm_parents[] __initconst = {
	"clk26m",
	"mmpll_d7",
	"mmpll_d6",
	"mmpll_d4",
	"syspll_d3",
	"univpll1_d4",
	"mmpll_d10",
	"syspll1_d2",
	"syspll_d5",
	"syspll1_d4",
	"univpll_d5"
};

static const char * const dpi0_parents[] __initconst = {
	"clk26m",
	"tvdpll_d2",
	"tvdpll_d4",
	"tvdpll_d8",
	"tvdpll_d16",
};

static const char * const pwm_parents[] __initconst = {
	"clk26m",
	"syspll1_d8",
	"univpll3_d8"
};

static const char * const disppwm_parents[] __initconst = {
	"clk26m",
	"ulposcpll_d2",
	"ulposcpll_d16",
	"univpll1_d8"
};

static const char * const vdec_parents[] __initconst = {
	"clk26m",
	"syspll_d5",
	"univpll_d5",
	"mmpll_d6",
	"mmpll_d4",
	"syspll_d3",
	"univpll1_d4",
	"syspll1_d2",
	"univpll3_d4",
	"mmpll_d10",
	"univpll2_d4",
	"syspll2_d2",
	"univpll1_d8",
	"univpll2_d8",
	"syspll4_d2",
	"emipll_ck"
};

static const char * const venc_parents[] __initconst = {
	"clk26m",
	"univpll_d5",
	"univpll2_d2",
	"syspll_d3",
	"univpll1_d4",
	"mmpll_d7",
	"syspll1_d2",
	"mmpll_d10",
	"univpll2_d4",
	"syspll1_d4",
	"emipll_ck"
};

static const char * const mfg_parents[] __initconst = {
	"clk26m",
	"gpupll_ck",
	"univpll2_d2",
	"syspll_d3"
};
/*change a lot*/
static const char * const camtg_parents[] __initconst = {
	"clk26m",
	"univpll_d104",
	"univpll2_d16",
	"univpll_d52",
	"ad_sys_26m_ck_d2",
	"univpll_d208",
	"univpll_d416"
};

static const char * const camtg2_parents[] __initconst = {
	"clk26m",
	"univpll_d104",
	"univpll2_d16",
	"univpll_d52",
	"ad_sys_26m_ck_d2",
	"univpll_d208",
	"univpll_d416"
};

static const char * const camtg3_parents[] __initconst = {
	"clk26m",
	"univpll_d104",
	"univpll2_d16",
	"univpll_d52",
	"ad_sys_26m_ck_d2",
	"univpll_d208",
	"univpll_d416"
};

static const char * const camtg4_parents[] __initconst = {
	"clk26m",
	"univpll_d104",
	"univpll2_d16",
	"univpll_d52",
	"ad_sys_26m_ck_d2",
	"univpll_d208",
	"univpll_d416"
};

static const char * const i2c_parents[] __initconst = {
	"clk26m",
	"univpll3_d8"
};

static const char * const uart_parents[] __initconst = {
	"clk26m",
	"univpll2_d16"
};

static const char * const spi_parents[] __initconst = {
	"clk26m",
	"syspll3_d2",
	"syspll1_d4",
	"syspll4_d2",
	"univpll3_d4",
	"univpll2_d8",
	"univpll1_d16"
};

static const char * const usb30_p0_parents[] __initconst = {
	"clk26m",
	"univpll3_d4",
	"syspll1_d8",
	"univpll2_d8"
};

static const char * const msdc50_0_hclk_parents[] __initconst = {
	"clk26m",
	"univpll3_d2",
	"syspll2_d2",
	"syspll4_d2",
	"syspll1_d2",
	"univpll1_d8"
};

static const char * const msdc50_0_parents[] __initconst = {
	"clk26m",
	"msdcpll_d2",
	"mmpll_d8",
	"univpll1_d8",
	"syspll2_d2",
	"syspll_d3",
	"msdcpll_d4",
	"univpll1_d4"
};

static const char * const msdc30_1_parents[] __initconst = {
	"clk26m",
	"univpll2_d4",
	"msdcpll_d4",
	"univpll1_d8",
	"syspll2_d2",
	"mmpll_d16",
	"univpll4_d2"
};

static const char * const i3c_parents[] __initconst = {
	"clk26m",
	"univpll3_d4",
	"univpll3_d8"
};

static const char * const msdc30_3_parents[] __initconst = {
	"clk26m",
	"univpll2_d4",
	"msdcpll_d2",
	"univpll1_d8",
	"syspll2_d2",
	"univpll1_d4",
	"syspll_d3",
	"msdcpll_d4",
	"mmpll_d8",
	"mmpll_d16"
};

static const char * const msdc50_3_hsel_parents[] __initconst = {
	"clk26m",
	"syspll2_d2",
	"syspll4_d2",
	"univpll1_d8"
};

static const char * const audio_parents[] __initconst = {
	"clk26m",
	"syspll3_d4",
	"syspll4_d4",
	"syspll1_d16"
};

static const char * const aud_intbus_parents[] __initconst = {
	"clk26m",
	"syspll1_d4",
	"syspll4_d2",
	"univpll3_d4",
	"univpll2_d16",
};

static const char * const pmicspi_parents[] __initconst = {
	"clk26m",
	"syspll1_d8",
	"syspll3_d4",
	"syspll1_d16",
	"univpll3_d8",
	"univpll_d52",
};

static const char * const scp_parents[] __initconst = {
	"clk26m",
	"syspll1_d2",
	"univpll1_d4",
	"syspll_d3",
	"univpll2_d4",
	"univpll3_d4",
	"mmpll_d5"
};

static const char * const atb_parents[] __initconst = {
	"clk26m",
	"syspll1_d4",
	"syspll1_d2"
};

static const char * const dsp_parents[] __initconst = {
	"clk26m",
	"syspll_d2",
	"univpll_d5",
	"mmpll_d4",
	"mmpll_d5",
	"mmpll_d6",
	"syspll_d3",
	"univpll1_d2",
	"univpll1_d4",
	"syspll1_d2",
	"syspll1_d4",
	"mmpll_d10"
};

static const char * const aud_1_parents[] __initconst = {
	"clk26m",
	"apll1_ck"
};

static const char * const aud_2_parents[] __initconst = {
	"clk26m",
	"apll2_ck"
};

static const char * const aud_engen1_parents[] __initconst = {
	"clk26m",
	"apll1_d2",
	"apll1_d4",
	"apll1_d8"
};

static const char * const aud_engen2_parents[] __initconst = {
	"clk26m",
	"apll2_d2",
	"apll2_d4",
	"apll2_d8"
};

static const char * const dfp_mfg_parents[] __initconst = {
	"clk26m",
	"univpll2_d4",
	"univpll2_d8",
	"univpll2_d16"
};

static const char * const cam_parents[] __initconst = {
	"clk26m",
	"syspll_d2",
	"mmpll_d7",
	"mmpll_d5",
	"mmpll_d6",
	"syspll_d3",
	"univpll1_d4",
	"syspll1_d2",
	"emipll_ck"
};

static const char * const ipu_if_parents[] __initconst = {
	"clk26m",
	"syspll1_d2",
	"mmpll_d7",
	"univpll_d5",
	"mmpll_d6",
	"univpll1_d2",
	"univpll1_d4",
	"syspll_d2"
};

static const char * const img_parents[] __initconst = {
	"clk26m",
	"syspll1_d2",
	"mmpll_d7",
	"mmpll_d5",
	"mmpll_d6",
	"syspll_d3",
	"mmpll_d10",
	"univpll1_d4",
	"emipll_ck"
};

static const char * const aes_ufsfde_parents[] __initconst = {
	"clk26m",
	"syspll_d2",
	"syspll1_d2",
	"syspll_d3",
	"syspll1_d4",
	"emipll_d2",
	"univpll2_d2"
};

static const char * const audio_h_parents[] __initconst = {
	"clk26m",
	"univpll4_d2",
	"apll1_ck",
	"apll2_ck"
};

static const char * const sspm_parents[] __initconst = {
	"clk26m",
	"syspll1_d2",
	"univpll3_d2",
	"syspll_d5",
	"univpll1_d4",
	"syspll_d3",
	"univpll2_d2",
	"syspll2_d2"
};

static const char * const ufs_card_parents[] __initconst = {
	"clk26m",
	"syspll1_d4",
	"syspll1_d8",
	"syspll1_d16"
};

static const char * const bsi_spi_parents[] __initconst = {
	"clk26m",
	"syspll2_d3",
	"syspll1_d4",
	"syspll_d7"
};

static const char * const dxcc_parents[] __initconst = {
	"clk26m",
	"syspll1_d2",
	"syspll1_d4",
	"syspll1_d8"
};

static const char * const seninf_parents[] __initconst = {
	"clk26m",
	"univpll1_d4",
	"univpll2_d4",
	"univpll1_d8"
};

static const char * const dfp_parents[] __initconst = {
	"clk26m",
	"univpll2_d16"
};

#define INVALID_UPDATE_REG 0xFFFFFFFF
#define INVALID_UPDATE_SHIFT -1
#define INVALID_MUX_GATE -1

static const struct mtk_mux_upd top_muxes[] __initconst = {
	/* CLK_CFG_0 */
	MUX_UPD(CLK_TOP_AXI_SEL, "axi_sel", axi_parents, CK_CFG_0,
		0, 3, INVALID_MUX_GATE, INVALID_UPDATE_REG, INVALID_UPDATE_SHIFT),
	MUX_UPD(CLK_TOP_MEM_SEL, "mem_sel", mem_parents, CK_CFG_0,
		8, 2, INVALID_MUX_GATE, INVALID_UPDATE_REG, INVALID_UPDATE_SHIFT),
	MUX_UPD(CLK_TOP_DDRPHYCFG_SEL, "ddrphycfg_sel", ddrphycfg_parents, CK_CFG_0,
		16, 1, INVALID_MUX_GATE, INVALID_UPDATE_REG, INVALID_UPDATE_SHIFT),
	MUX_UPD(CLK_TOP_MM_SEL, "mm_sel", mm_parents, CK_CFG_0,
		24, 4, 31, INVALID_UPDATE_REG, INVALID_UPDATE_SHIFT),
	/* CLK_CFG_1 */
	MUX_UPD(CLK_TOP_DPI0_SEL, "dpi0_sel", dpi0_parents, CK_CFG_1,
		0, 3, 7, INVALID_UPDATE_REG, INVALID_UPDATE_SHIFT),
	MUX_UPD(CLK_TOP_PWM_SEL, "pwm_sel", pwm_parents, CK_CFG_1,
		8, 2, 15, INVALID_UPDATE_REG, INVALID_UPDATE_SHIFT),
	MUX_UPD(CLK_TOP_DISPPWM_SEL, "disppwm_sel", disppwm_parents, CK_CFG_1,
		16, 2, 23, INVALID_UPDATE_REG, INVALID_UPDATE_SHIFT),
	MUX_UPD(CLK_TOP_VDEC_SEL, "vdec_sel", vdec_parents, CK_CFG_1,
		24, 4, 31, INVALID_UPDATE_REG, INVALID_UPDATE_SHIFT),
	/* CLK_CFG_2 */
	MUX_UPD(CLK_TOP_VENC_SEL, "venc_sel", venc_parents, CK_CFG_2,
		0, 4, 7, INVALID_UPDATE_REG, INVALID_UPDATE_SHIFT),
	MUX_UPD(CLK_TOP_MFG_SEL, "mfg_sel", mfg_parents, CK_CFG_2,
		8, 2, 15, INVALID_UPDATE_REG, INVALID_UPDATE_SHIFT),
	MUX_UPD(CLK_TOP_CAMTG_SEL, "camtg_sel", camtg_parents, CK_CFG_2,
		16, 3, 23, INVALID_UPDATE_REG, INVALID_UPDATE_SHIFT),
	MUX_UPD(CLK_TOP_CAMTG2_SEL, "camtg2_sel", camtg2_parents, CK_CFG_2,
		24, 3, 31, INVALID_UPDATE_REG, INVALID_UPDATE_SHIFT),
	/* CLK_CFG_3 */
	MUX_UPD(CLK_TOP_CAMTG3_SEL, "camtg3_sel", camtg3_parents, CK_CFG_3,
		0, 3, 7, INVALID_UPDATE_REG, INVALID_UPDATE_SHIFT),
	MUX_UPD(CLK_TOP_CAMTG4_SEL, "camtg4_sel", camtg4_parents, CK_CFG_3,
		8, 3, 15, INVALID_UPDATE_REG, INVALID_UPDATE_SHIFT),
	MUX_UPD(CLK_TOP_I2C_SEL, "i2c_sel", i2c_parents, CK_CFG_3,
		16, 1, 23, INVALID_UPDATE_REG, INVALID_UPDATE_SHIFT),
	MUX_UPD(CLK_TOP_UART_SEL, "uart_sel", uart_parents, CK_CFG_3,
		24, 1, 31, INVALID_UPDATE_REG, INVALID_UPDATE_SHIFT),
	/* CLK_CFG_4 */
	MUX_UPD(CLK_TOP_SPI_SEL, "spi_sel", spi_parents, CK_CFG_4,
		0, 3, 7, INVALID_UPDATE_REG, INVALID_UPDATE_SHIFT),
	MUX_UPD(CLK_TOP_USB30_P0_SEL, "usb30_p0_sel", usb30_p0_parents, CK_CFG_4,
		8, 2, 15, INVALID_UPDATE_REG, INVALID_UPDATE_SHIFT),
	MUX_UPD(CLK_TOP_MSDC50_0_HCLK_SEL, "msdc50_0_hclk_sel", msdc50_0_hclk_parents, CK_CFG_4,
		16, 3, 23, INVALID_UPDATE_REG, INVALID_UPDATE_SHIFT),
	MUX_UPD(CLK_TOP_MSDC50_0_SEL, "msdc50_0_sel", msdc50_0_parents, CK_CFG_4,
		24, 3, 31, INVALID_UPDATE_REG, INVALID_UPDATE_SHIFT),
	/* CLK_CFG_5 */
	MUX_UPD(CLK_TOP_MSDC30_1_SEL, "msdc30_1_sel", msdc30_1_parents, CK_CFG_5,
		0, 3, 7, INVALID_UPDATE_REG, INVALID_UPDATE_SHIFT),
	MUX_UPD(CLK_TOP_I3C_SEL, "i3c_sel", i3c_parents, CK_CFG_5,
		8, 2, 15, INVALID_UPDATE_REG, INVALID_UPDATE_SHIFT),
	MUX_UPD(CLK_TOP_MSDC30_3_SEL, "msdc30_3_sel", msdc30_3_parents, CK_CFG_5,
		16, 4, 23, INVALID_UPDATE_REG, INVALID_UPDATE_SHIFT),
	MUX_UPD(CLK_TOP_MSDC50_3_HCLK_SEL, "msdc50_3_hsel_sel", msdc50_3_hsel_parents, CK_CFG_5,
		24, 2, 31, INVALID_UPDATE_REG, INVALID_UPDATE_SHIFT),
	/* CLK_CFG_6 */
	MUX_UPD(CLK_TOP_AUDIO_SEL, "audio_sel", audio_parents, CK_CFG_6,
		0, 2, 7, INVALID_UPDATE_REG, INVALID_UPDATE_SHIFT),
	MUX_UPD(CLK_TOP_AUD_INTBUS_SEL, "aud_intbus_sel", aud_intbus_parents, CK_CFG_6,
		8, 3, 15, INVALID_UPDATE_REG, INVALID_UPDATE_SHIFT),
	MUX_UPD(CLK_TOP_PMICSPI_SEL, "pmicspi_sel", pmicspi_parents, CK_CFG_6,
		16, 3, 23, INVALID_UPDATE_REG, INVALID_UPDATE_SHIFT),
	MUX_UPD(CLK_TOP_SCP_SEL, "scp_sel", scp_parents, CK_CFG_6,
		24, 3, 31, INVALID_UPDATE_REG, INVALID_UPDATE_SHIFT),
	/* CLK_CFG_7 */
	MUX_UPD(CLK_TOP_ATB_SEL, "atb_sel", atb_parents, CK_CFG_7,
		0, 2, 7, INVALID_UPDATE_REG, INVALID_UPDATE_SHIFT),
	MUX_UPD(CLK_TOP_DSP_SEL, "dsp_sel", dsp_parents, CK_CFG_7,
		8, 4, 15, INVALID_UPDATE_REG, INVALID_UPDATE_SHIFT),
	MUX_UPD(CLK_TOP_AUD_1_SEL, "aud_1_sel", aud_1_parents, CK_CFG_7,
		16, 1, 23, INVALID_UPDATE_REG, INVALID_UPDATE_SHIFT),
	MUX_UPD(CLK_TOP_AUD_2_SEL, "aud_2_sel", aud_2_parents, CK_CFG_7,
		24, 1, 31, INVALID_UPDATE_REG, INVALID_UPDATE_SHIFT),
	/* CLK_CFG_8 */
	MUX_UPD(CLK_TOP_AUD_ENGEN1_SEL, "aud_engen1_sel", aud_engen1_parents, CK_CFG_8,
		0, 2, 7, INVALID_UPDATE_REG, INVALID_UPDATE_SHIFT),
	MUX_UPD(CLK_TOP_AUD_ENGEN2_SEL, "aud_engen2_sel", aud_engen2_parents, CK_CFG_8,
		8, 2, 15, INVALID_UPDATE_REG, INVALID_UPDATE_SHIFT),
	MUX_UPD(CLK_TOP_DFP_MFG_SEL, "dfp_mfg_sel", dfp_mfg_parents, CK_CFG_8,
		16, 2, 23, INVALID_UPDATE_REG, INVALID_UPDATE_SHIFT),
	MUX_UPD(CLK_TOP_CAM_SEL, "cam_sel", cam_parents, CK_CFG_8,
		24, 4, 31, INVALID_UPDATE_REG, INVALID_UPDATE_SHIFT),
	/* CLK_CFG_9 */
	MUX_UPD(CLK_TOP_IPU_IF_SEL, "ipu_if_sel", ipu_if_parents, CK_CFG_9,
		0, 3, 7, INVALID_UPDATE_REG, INVALID_UPDATE_SHIFT),
	MUX_UPD(CLK_TOP_IMG_SEL, "img_sel", img_parents, CK_CFG_9,
		8, 4, 15, INVALID_UPDATE_REG, INVALID_UPDATE_SHIFT),
	MUX_UPD(CLK_TOP_AES_UFSFDE_SEL, "aes_ufsfde_sel", aes_ufsfde_parents, CK_CFG_9,
		16, 3, 23, INVALID_UPDATE_REG, INVALID_UPDATE_SHIFT),
	MUX_UPD(CLK_TOP_AUDIO_H_SEL, "audio_h_sel", audio_h_parents, CK_CFG_9,
		24, 2, 31, INVALID_UPDATE_REG, INVALID_UPDATE_SHIFT),
	/* CLK_CFG_10 */
	MUX_UPD(CLK_TOP_SSPM_SEL, "sspm_sel", sspm_parents, CK_CFG_10,
		0, 3, 7, INVALID_UPDATE_REG, INVALID_UPDATE_SHIFT),
	/* CLK_CFG_10 */
	MUX_UPD(CLK_TOP_DFP_SEL, "dfp_sel", dfp_parents, CK_CFG_11,
		8, 1, 15, INVALID_UPDATE_REG, INVALID_UPDATE_SHIFT),
};

static const struct mtk_mux_clr_set_upd top_clr_set_muxes[] __initconst = {
	MUX_CLR_SET_UPD(CLK_TOP_UFS_CARD_SEL, "ufs_card_sel", ufs_card_parents, CK_CFG_10,
		CK_CFG_10_SET, CK_CFG_10_CLR, 8, 2, 15, INVALID_UPDATE_REG, INVALID_UPDATE_SHIFT),
	MUX_CLR_SET_UPD(CLK_TOP_BSI_SPI_SEL, "bsi_spi_sel", bsi_spi_parents, CK_CFG_10,
		CK_CFG_10_SET, CK_CFG_10_CLR, 16, 2, 23, INVALID_UPDATE_REG, INVALID_UPDATE_SHIFT),
	MUX_CLR_SET_UPD(CLK_TOP_DXCC_SEL, "dxcc_sel", dxcc_parents, CK_CFG_10,
		CK_CFG_10_SET, CK_CFG_10_CLR, 24, 2, 31, INVALID_UPDATE_REG, INVALID_UPDATE_SHIFT),
	/* CLK_CFG_11 */
	MUX_CLR_SET_UPD(CLK_TOP_SENINF_SEL, "seninf_sel", seninf_parents, CK_CFG_11,
		CK_CFG_11_SET, CK_CFG_11_CLR, 0, 2, 7, INVALID_UPDATE_REG, INVALID_UPDATE_SHIFT),
};

/* TODO: remove audio clocks after audio driver ready */

static int mtk_cg_bit_is_cleared(struct clk_hw *hw)
{
	struct mtk_clk_gate *cg = to_clk_gate(hw);
	u32 val = 0;

	regmap_read(cg->regmap, cg->sta_ofs, &val);

	val &= BIT(cg->bit);

	return val == 0;
}

static int mtk_cg_bit_is_set(struct clk_hw *hw)
{
	struct mtk_clk_gate *cg = to_clk_gate(hw);
	u32 val = 0;

	regmap_read(cg->regmap, cg->sta_ofs, &val);

	val &= BIT(cg->bit);

	return val != 0;
}

static void mtk_cg_set_bit(struct clk_hw *hw)
{
	struct mtk_clk_gate *cg = to_clk_gate(hw);

	regmap_update_bits(cg->regmap, cg->sta_ofs, BIT(cg->bit), BIT(cg->bit));
}

static void mtk_cg_clr_bit(struct clk_hw *hw)
{
	struct mtk_clk_gate *cg = to_clk_gate(hw);

	regmap_update_bits(cg->regmap, cg->sta_ofs, BIT(cg->bit), 0);
}

static int mtk_cg_enable(struct clk_hw *hw)
{
	mtk_cg_clr_bit(hw);

	return 0;
}

static void mtk_cg_disable(struct clk_hw *hw)
{
	struct mtk_clk_gate *cg = to_clk_gate(hw);

	if ((strcmp(__clk_get_name(cg->hw.clk), "mm_gals_venc2mm")) &&
		(strcmp(__clk_get_name(cg->hw.clk), "mm_gals_vdec2mm")) &&
		(strcmp(__clk_get_name(cg->hw.clk), "mm_gals_img2mm")) &&
		(strcmp(__clk_get_name(cg->hw.clk), "mm_gals_cam2mm")) &&
		(strcmp(__clk_get_name(cg->hw.clk), "mm_gals_ipu2mm")) &&
		(strcmp(__clk_get_name(cg->hw.clk), "mm_smi_common")) &&
		(strcmp(__clk_get_name(cg->hw.clk), "mm_gals_comm0")) &&
		(strcmp(__clk_get_name(cg->hw.clk), "mm_gals_comm1")) &&
		(strcmp(__clk_get_name(cg->hw.clk), "mm_disp_color0")) &&
		(strcmp(__clk_get_name(cg->hw.clk), "mm_smi_larb0")) &&
		(strcmp(__clk_get_name(cg->hw.clk), "mm_smi_larb1")) &&

		(strcmp(__clk_get_name(cg->hw.clk), "ipu_infra_ahb")) &&
		(strcmp(__clk_get_name(cg->hw.clk), "ipu_mm_axi")) &&
		(strcmp(__clk_get_name(cg->hw.clk), "ipu_cam_axi")) &&
		(strcmp(__clk_get_name(cg->hw.clk), "ipu_img_axi")) &&

		(strcmp(__clk_get_name(cg->hw.clk), "cam_larb6")) &&
		(strcmp(__clk_get_name(cg->hw.clk), "cam_larb3")) &&

		(strcmp(__clk_get_name(cg->hw.clk), "img_larb5")) &&
		(strcmp(__clk_get_name(cg->hw.clk), "img_larb2")) &&

		(strcmp(__clk_get_name(cg->hw.clk), "mfg_bg3d")))
	mtk_cg_set_bit(hw);
}

static int mtk_cg_enable_inv(struct clk_hw *hw)
{
	mtk_cg_set_bit(hw);

	return 0;
}

static void mtk_cg_disable_inv(struct clk_hw *hw)
{
	struct mtk_clk_gate *cg = to_clk_gate(hw);

	if ((strcmp(__clk_get_name(cg->hw.clk), "vdec_cken")) &&
		(strcmp(__clk_get_name(cg->hw.clk), "vdec_larb1_cken")) &&

		(strcmp(__clk_get_name(cg->hw.clk), "venc_larb")) &&
		(strcmp(__clk_get_name(cg->hw.clk), "venc_cke1venc")) &&
		(strcmp(__clk_get_name(cg->hw.clk), "venc_jpgenc")))
	mtk_cg_clr_bit(hw);
}

const struct clk_ops mtk_clk_gate_ops = {
	.is_enabled	= mtk_cg_bit_is_cleared,
	.enable		= mtk_cg_enable,
	.disable	= mtk_cg_disable,
};

const struct clk_ops mtk_clk_gate_ops_inv = {
	.is_enabled	= mtk_cg_bit_is_set,
	.enable		= mtk_cg_enable_inv,
	.disable	= mtk_cg_disable_inv,
};
/*topck*/
static const struct mtk_gate_regs topck_cg_regs = {
	.set_ofs = 0x420,
	.clr_ofs = 0x420,
	.sta_ofs = 0x420,
};

#define GATE_TOPCK(_id, _name, _parent, _shift) {	\
		.id = _id,				\
		.name = _name,				\
		.parent_name = _parent,			\
		.regs = &topck_cg_regs,		\
		.shift = _shift,			\
		.ops = &mtk_clk_gate_ops_inv,	\
	}

static const struct mtk_gate topck_clks[] __initconst = {
	/* INFRA0 */
	GATE_TOPCK(CLK_TOP_F_FAUD26M_EN, "topck_audio26m",
		"f_f26m_ck", 0),
};

/*infra*/
static const struct mtk_gate_regs infra0_cg_regs = {
	.set_ofs = 0x80,
	.clr_ofs = 0x84,
	.sta_ofs = 0x90,
};

static const struct mtk_gate_regs infra1_cg_regs = {
	.set_ofs = 0x88,
	.clr_ofs = 0x8c,
	.sta_ofs = 0x94,
};

static const struct mtk_gate_regs infra2_cg_regs = {
	.set_ofs = 0xb0,
	.clr_ofs = 0xb4,
	.sta_ofs = 0xb8,
};

#define GATE_INFRA0(_id, _name, _parent, _shift) {	\
		.id = _id,				\
		.name = _name,				\
		.parent_name = _parent,			\
		.regs = &infra0_cg_regs,		\
		.shift = _shift,			\
		.ops = &mtk_clk_gate_ops_setclr,	\
	}

#define GATE_INFRA1(_id, _name, _parent, _shift) {	\
		.id = _id,				\
		.name = _name,				\
		.parent_name = _parent,			\
		.regs = &infra1_cg_regs,		\
		.shift = _shift,			\
		.ops = &mtk_clk_gate_ops_setclr,	\
	}

#define GATE_INFRA2(_id, _name, _parent, _shift) {	\
		.id = _id,				\
		.name = _name,				\
		.parent_name = _parent,			\
		.regs = &infra2_cg_regs,		\
		.shift = _shift,			\
		.ops = &mtk_clk_gate_ops_setclr,	\
	}

static const struct mtk_gate infra_clks[] __initconst = {
	/* INFRA0 */
	GATE_INFRA0(CLK_INFRA_PMIC_TMR, "infra_pmic_tmr",
		"f_f26m_ck", 0),
	GATE_INFRA0(CLK_INFRA_PMIC_AP, "infra_pmic_ap",
		"f_f26m_ck", 1),
	GATE_INFRA0(CLK_INFRA_PMIC_MD, "infra_pmic_md",
		"f_f26m_ck", 2),
	GATE_INFRA0(CLK_INFRA_PMIC_CONN, "infra_pmic_conn",
		"f_f26m_ck", 3),
	GATE_INFRA0(CLK_INFRA_SCP, "infra_scp",
		"scp_sel", 4),
	GATE_INFRA0(CLK_INFRA_SEJ, "infra_sej",
		"f_f26m_ck", 5),
	GATE_INFRA0(CLK_INFRA_APXGPT, "infra_apxgpt",
		"f_f26m_ck", 6),
	GATE_INFRA0(CLK_INFRA_DVFSRC, "infra_dvfsrc",
		"f_f26m_ck", 7),
	GATE_INFRA0(CLK_INFRA_GCE, "infra_gce",
		"axi_sel", 8),
	GATE_INFRA0(CLK_INFRA_DBG, "infra_dbg",
		"axi_sel", 9),
	GATE_INFRA0(CLK_INFRA_SPM_APB_ASYNC, "infra_spm_apb_async",
		"clk_null", 12),
	GATE_INFRA0(CLK_INFRA_CLDMA_AP_TOP, "infra_cldma_ap_top",
		"axi_sel", 13),
	GATE_INFRA0(CLK_INFRA_CCIF_3_SET_0, "infra_ccif_3_set_0",
		"axi_sel", 17),
	GATE_INFRA0(CLK_INFRA_AES_TOP0, "infra_aes_top0",
		"axi_sel", 18),
	GATE_INFRA0(CLK_INFRA_AES_TOP1, "infra_aes_top1",
		"axi_sel", 19),
	GATE_INFRA0(CLK_INFRA_DEVAPC_MPU, "infra_devapc_mpu",
		"axi_sel", 20),
	GATE_INFRA0(CLK_INFRA_CCIF_3_SET_1, "infra_ccif_3_set_1",
		"axi_sel", 24),
	GATE_INFRA0(CLK_INFRA_MD2MD_CCIF_SET_0, "infra_md2md_ccif_set_0",
		"axi_sel", 27),
	GATE_INFRA0(CLK_INFRA_MD2MD_CCIF_SET_1, "infra_md2md_ccif_set_1",
		"axi_sel", 28),
	GATE_INFRA0(CLK_INFRA_MD2MD_CCIF_SET_2, "infra_md2md_ccif_set_2",
		"axi_sel", 29),
	GATE_INFRA0(CLK_INFRA_FHCTL, "infra_fhctl",
		"f_f26m_ck", 30),
	GATE_INFRA0(CLK_INFRA_MODEM_TEMP_SHARE, "infra_modem_temp_share",
		"axi_sel", 31),
	/* INFRA1 */
	GATE_INFRA1(CLK_INFRA_MD2MD_CCIF_MD_SET_0, "infra_md2md_ccif_md_set_0",
		"axi_sel", 0),
	GATE_INFRA1(CLK_INFRA_MD2MD_CCIF_MD_SET_1, "infra_md2md_ccif_md_set_1",
		"axi_sel", 3),
	GATE_INFRA1(CLK_INFRA_AUDIO_DCM_EN, "infra_audio_dcm_en",
		"axi_sel", 5),
	GATE_INFRA1(CLK_INFRA_CLDMA_AO_TOP_HCLK, "infra_cldma_ao_top_hclk",
		"axi_sel", 6),
	GATE_INFRA1(CLK_INFRA_MD2MD_CCIF_MD_SET_2, "infra_md2md_ccif_md_set_2",
		"axi_sel", 7),
	GATE_INFRA1(CLK_INFRA_TRNG, "infra_trng",
		"clk_null", 9),
	GATE_INFRA1(CLK_INFRA_AUXADC, "infra_auxadc",
		"f_f26m_ck", 10),
	GATE_INFRA1(CLK_INFRA_CPUM, "infra_cpum",
		"axi_sel", 11),
	GATE_INFRA1(CLK_INFRA_CCIF1_AP, "infra_ccif1_ap",
		"axi_sel", 12),
	GATE_INFRA1(CLK_INFRA_CCIF1_MD, "infra_ccif1_md",
		"axi_sel", 13),
	GATE_INFRA1(CLK_INFRA_CCIF2_AP, "infra_ccif2_ap",
		"axi_sel", 16),
	GATE_INFRA1(CLK_INFRA_CCIF2_MD, "infra_ccif2_md",
		"axi_sel", 17),
	GATE_INFRA1(CLK_INFRA_CCIF4_AP, "infra_ccif4_ap",
		"axi_sel", 18),
	GATE_INFRA1(CLK_INFRA_XIU_CKCG_SET_FOR_DBG_CTRLER, "infra_xiu_ckcg_set_for_dbg_ctrler",
		"f_frtc_ck", 19),
	GATE_INFRA1(CLK_INFRA_DEVICE_APC, "infra_device_apc",
		"axi_sel", 20),
	GATE_INFRA1(CLK_INFRA_CCIF4_MD, "infra_ccif4_md",
		"axi_sel", 21),
	GATE_INFRA1(CLK_INFRA_SMI_L2C, "infra_smi_l2c",
		"mm_sel", 22),
	GATE_INFRA1(CLK_INFRA_CCIF_AP, "infra_ccif_ap",
		"axi_sel", 23),
	GATE_INFRA1(CLK_INFRA_RG_DBG_AO_CLK, "infra_rg_dbg_ao_clk",
		"atb_sel", 24),
	GATE_INFRA1(CLK_INFRA_AUDIO, "infra_audio",
		"axi_sel", 25),
	GATE_INFRA1(CLK_INFRA_CCIF_MD, "infra_ccif_md",
		"axi_sel", 26),
	GATE_INFRA1(CLK_INFRA_CCIF5_AP, "infra_ccif5_ap",
		"axi_sel", 27),
	GATE_INFRA1(CLK_INFRA_CCIF5_MD, "infra_ccif5_md",
		"axi_sel", 28),
	GATE_INFRA1(CLK_INFRA_RG_FT_L2C, "infra_rg_ft_l2c",
		"mem2_sel", 29),
	GATE_INFRA1(CLK_INFRA_SPM_AHB, "infra_spm_ahb",
		"clk_null", 30),
	GATE_INFRA1(CLK_INFRA_DRAMC_F26M, "infra_dramc_f26m",
		"f_f26m_ck", 31),
	/* INFRA2 */
	GATE_INFRA2(CLK_INFRA_THERM_BCLK, "infra_therm_bclk",
		"axi_sel", 6),
	GATE_INFRA2(CLK_INFRA_PTP_BCLK, "infra_ptp_bclk",
		"axi_sel", 7),
	GATE_INFRA2(CLK_INFRA_AUXADC_MD, "infra_auxadc_md",
		"f_f26m_ck", 24),
	GATE_INFRA2(CLK_INFRA_DVFS_CTRL_APB_RX, "infra_dvfs_ctrl_apb_rx",
		"f_f26m_ck", 30),
};

static const struct mtk_gate_regs mfg_cg_regs = {
	.set_ofs = 0x4,
	.clr_ofs = 0x8,
	.sta_ofs = 0x0,
};

#define GATE_MFG(_id, _name, _parent, _shift) {		\
		.id = _id,				\
		.name = _name,				\
		.parent_name = _parent,			\
		.regs = &mfg_cg_regs,			\
		.shift = _shift,			\
		.ops = &mtk_clk_gate_ops_setclr,	\
	}

/*
* Real : Dummy bit mapping
* 0   ->   10
*/
static const struct mtk_gate mfg_clks[] __initconst = {
	GATE_MFG(CLK_MFG_BG3D, "mfg_bg3d", "mfg_sel", 10),
};

static const struct mtk_gate_regs pericfg0_cg_regs = {
	.set_ofs = 0x270,
	.clr_ofs = 0x274,
	.sta_ofs = 0x278,
};

static const struct mtk_gate_regs pericfg1_cg_regs = {
	.set_ofs = 0x280,
	.clr_ofs = 0x284,
	.sta_ofs = 0x288,
};

static const struct mtk_gate_regs pericfg2_cg_regs = {
	.set_ofs = 0x290,
	.clr_ofs = 0x294,
	.sta_ofs = 0x298,
};

static const struct mtk_gate_regs pericfg3_cg_regs = {
	.set_ofs = 0x2a0,
	.clr_ofs = 0x2a4,
	.sta_ofs = 0x2a8,
};

static const struct mtk_gate_regs pericfg4_cg_regs = {
	.set_ofs = 0x2b0,
	.clr_ofs = 0x2b4,
	.sta_ofs = 0x2b8,
};

#define GATE_PERICFG0(_id, _name, _parent, _shift) {	\
		.id = _id,				\
		.name = _name,				\
		.parent_name = _parent,			\
		.regs = &pericfg0_cg_regs,		\
		.shift = _shift,			\
		.ops = &mtk_clk_gate_ops_setclr,	\
	}

#define GATE_PERICFG1(_id, _name, _parent, _shift) {	\
		.id = _id,				\
		.name = _name,				\
		.parent_name = _parent,			\
		.regs = &pericfg1_cg_regs,		\
		.shift = _shift,			\
		.ops = &mtk_clk_gate_ops_setclr,	\
	}

#define GATE_PERICFG2(_id, _name, _parent, _shift) {	\
		.id = _id,				\
		.name = _name,				\
		.parent_name = _parent,			\
		.regs = &pericfg2_cg_regs,		\
		.shift = _shift,			\
		.ops = &mtk_clk_gate_ops_setclr,	\
	}

#define GATE_PERICFG3(_id, _name, _parent, _shift) {	\
		.id = _id,				\
		.name = _name,				\
		.parent_name = _parent,			\
		.regs = &pericfg3_cg_regs,		\
		.shift = _shift,			\
		.ops = &mtk_clk_gate_ops_setclr,	\
	}

#define GATE_PERICFG4(_id, _name, _parent, _shift) {	\
		.id = _id,				\
		.name = _name,				\
		.parent_name = _parent,			\
		.regs = &pericfg4_cg_regs,		\
		.shift = _shift,			\
		.ops = &mtk_clk_gate_ops_setclr,	\
	}

static const struct mtk_gate pericfg_clks[] __initconst = {
	/* PERICFG0 */
	GATE_PERICFG0(CLK_PERICFG_RG_PWM_BCLK, "pericfg_rg_pwm_bclk",
		"pwm_sel", 0),
	GATE_PERICFG0(CLK_PERICFG_RG_PWM_FBCLK1, "pericfg_rg_pwm_fbclk1",
		"pwm_sel", 1),
	GATE_PERICFG0(CLK_PERICFG_RG_PWM_FBCLK2, "pericfg_rg_pwm_fbclk2",
		"pwm_sel", 2),
	GATE_PERICFG0(CLK_PERICFG_RG_PWM_FBCLK3, "pericfg_rg_pwm_fbclk3",
		"pwm_sel", 3),
	GATE_PERICFG0(CLK_PERICFG_RG_PWM_FBCLK4, "pericfg_rg_pwm_fbclk4",
		"pwm_sel", 4),
	GATE_PERICFG0(CLK_PERICFG_RG_I2C0_BCLK, "pericfg_rg_i2c0_bclk",
		"i2c_sel", 16),
	GATE_PERICFG0(CLK_PERICFG_RG_I2C1_BCLK, "pericfg_rg_i2c1_bclk",
		"i2c_sel", 17),
	GATE_PERICFG0(CLK_PERICFG_RG_I2C2_BCLK, "pericfg_rg_i2c2_bclk",
		"i2c_sel", 18),
	GATE_PERICFG0(CLK_PERICFG_RG_I2C3_BCLK, "pericfg_rg_i2c3_bclk",
		"i2c_sel", 19),
	GATE_PERICFG0(CLK_PERICFG_RG_I2C4_BCLK, "pericfg_rg_i2c4_bclk",
		"i2c_sel", 20),
	GATE_PERICFG0(CLK_PERICFG_RG_I2C5_BCLK, "pericfg_rg_i2c5_bclk",
		"i2c_sel", 21),
	GATE_PERICFG0(CLK_PERICFG_RG_I2C6_BCLK, "pericfg_rg_i2c6_bclk",
		"i2c_sel", 22),
	GATE_PERICFG0(CLK_PERICFG_RG_I2C7_BCLK, "pericfg_rg_i2c7_bclk",
		"i2c_sel", 23),
	GATE_PERICFG0(CLK_PERICFG_RG_I2C8_BCLK, "pericfg_rg_i2c8_bclk",
		"i2c_sel", 24),
	GATE_PERICFG0(CLK_PERICFG_RG_I2C9_BCLK, "pericfg_rg_i2c9_bclk",
		"i2c_sel", 25),
	GATE_PERICFG0(CLK_PERICFG_RG_IDVFS, "pericfg_rg_idvfs",
		"clk_null", 31),
	/* PERICFG1 */
	GATE_PERICFG1(CLK_PERICFG_RG_UART0, "pericfg_rg_uart0",
		"uart_sel", 0),
	GATE_PERICFG1(CLK_PERICFG_RG_UART1, "pericfg_rg_uart1",
		"uart_sel", 1),
	GATE_PERICFG1(CLK_PERICFG_RG_UART2, "pericfg_rg_uart2",
		"uart_sel", 2),
	GATE_PERICFG1(CLK_PERICFG_RG_SPI0, "pericfg_rg_spi0",
		"spi_sel", 16),
	GATE_PERICFG1(CLK_PERICFG_RG_SPI1, "pericfg_rg_spi1",
		"spi_sel", 17),
	GATE_PERICFG1(CLK_PERICFG_RG_SPI2, "pericfg_rg_spi2",
		"spi_sel", 18),
	GATE_PERICFG1(CLK_PERICFG_RG_SPI3, "pericfg_rg_spi3",
		"spi_sel", 19),
	GATE_PERICFG1(CLK_PERICFG_RG_SPI4, "pericfg_rg_spi4",
		"spi_sel", 20),
	GATE_PERICFG1(CLK_PERICFG_RG_SPI5, "pericfg_rg_spi5",
		"spi_sel", 21),
	GATE_PERICFG1(CLK_PERICFG_RG_SPI6, "pericfg_rg_spi6",
		"spi_sel", 22),
	GATE_PERICFG1(CLK_PERICFG_RG_SPI7, "pericfg_rg_spi7",
		"spi_sel", 23),
	GATE_PERICFG1(CLK_PERICFG_RG_SPI8, "pericfg_rg_spi8",
		"spi_sel", 24),
	GATE_PERICFG1(CLK_PERICFG_RG_SPI9, "pericfg_rg_spi9",
		"spi_sel", 25),
	/* PERICFG2 */
	GATE_PERICFG2(CLK_PERICFG_RG_MSDC0_AP_NORM, "pericfg_rg_msdc0_ck_ap_norm",
		"msdc50_0_hclk_sel", 0),
	GATE_PERICFG2(CLK_PERICFG_RG_MSDC1, "pericfg_rg_msdc1",
		"msdc30_1_sel", 1),
	GATE_PERICFG2(CLK_PERICFG_RG_MSDC2, "pericfg_rg_msdc2",
		"clk_null", 2),
	GATE_PERICFG2(CLK_PERICFG_RG_MSDC3, "pericfg_rg_msdc3",
		"msdc30_3_sel", 3),
	GATE_PERICFG2(CLK_PERICFG_RG_MSDC4, "pericfg_rg_msdc4",
		"clk_null", 4),
	GATE_PERICFG2(CLK_PERICFG_RG_UFSDEV, "pericfg_rg_ufsdev",
		"clk_null", 8),
	GATE_PERICFG2(CLK_PERICFG_RG_UFSDEV_MP_SAP_CFG, "pericfg_rg_ufsdev_mp_sap_cfg",
		"clk_null", 9),
	GATE_PERICFG2(CLK_PERICFG_RG_UFSCARD, "pericfg_rg_ufscard",
		"ufs_card_sel", 10),
	GATE_PERICFG2(CLK_PERICFG_RG_UFSCARD_MP_SAP_CFG, "pericfg_rg_ufscard_mp_sap_cfg",
		"f_f26m_ck", 11),
	GATE_PERICFG2(CLK_PERICFG_RG_UFS_AES_CORE, "pericfg_rg_ufs_aes_core",
		"aes_ufsfde_sel", 12),
	GATE_PERICFG2(CLK_PERICFG_RG_MSDC0_AP_SECURE, "pericfg_rg_msdc0_ck_ap_secure",
		"msdc50_0_hclk_sel", 24),
	GATE_PERICFG2(CLK_PERICFG_RG_MSDC0_MD_SECURE, "pericfg_rg_msdc0_ck_md_secure",
		"msdc50_0_hclk_sel", 25),
	/* PERICFG3 */
	GATE_PERICFG3(CLK_PERICFG_RG_USB_P0, "pericfg_rg_usb_p0",
		"usb30_p0_sel", 0),
	/* PERICFG4 */
	GATE_PERICFG4(CLK_PERICFG_RG_AP_DM, "pericfg_rg_ap_dm",
		"axi_sel", 0),
	GATE_PERICFG4(CLK_PERICFG_RG_DISP_PWM0, "pericfg_rg_disp_pwm0",
		"disppwm_sel", 3),
	GATE_PERICFG4(CLK_PERICFG_RG_BTIF, "pericfg_rg_btif",
		"axi_sel", 5),
	GATE_PERICFG4(CLK_PERICFG_RG_CQ_DMA, "pericfg_rg_cq_dma",
		"clk_null", 6),
	GATE_PERICFG4(CLK_PERICFG_RG_MBIST_MEM_OFF_DLY, "pericfg_rg_mbist_mem_off_dly",
		"f_f26m_ck", 8),
	GATE_PERICFG4(CLK_PERICFG_RG_DEVICE_APC_PERI, "pericfg_rg_device_apc_peri",
		"axi_sel", 9),
	GATE_PERICFG4(CLK_PERICFG_RG_DXCC_AO, "pericfg_rg_dxcc_ao",
		"dxcc_sel", 16),
	GATE_PERICFG4(CLK_PERICFG_RG_DXCC_PUB, "pericfg_rg_dxcc_pub",
		"dxcc_sel", 17),
	GATE_PERICFG4(CLK_PERICFG_RG_DXCC_SEC, "pericfg_rg_dxcc_sec",
		"dxcc_sel", 18),
	GATE_PERICFG4(CLK_PERICFG_RG_MTK_TRNG, "pericfg_rg_mtk_trng",
		"axi_sel", 19),
	GATE_PERICFG4(CLK_PERICFG_RG_DEBUGTOP, "pericfg_rg_debugtop",
		"axi_sel", 20),
};

static const struct mtk_gate_regs vdec0_cg_regs = {
	.set_ofs = 0x0,
	.clr_ofs = 0x4,
	.sta_ofs = 0x0,
};

static const struct mtk_gate_regs vdec1_cg_regs = {
	.set_ofs = 0x8,
	.clr_ofs = 0xc,
	.sta_ofs = 0x8,
};

#define GATE_VDEC0_I(_id, _name, _parent, _shift) {	\
		.id = _id,				\
		.name = _name,				\
		.parent_name = _parent,			\
		.regs = &vdec0_cg_regs,			\
		.shift = _shift,			\
		.ops = &mtk_clk_gate_ops_setclr_inv,	\
	}

#define GATE_VDEC1_I(_id, _name, _parent, _shift) {	\
		.id = _id,				\
		.name = _name,				\
		.parent_name = _parent,			\
		.regs = &vdec1_cg_regs,			\
		.shift = _shift,			\
		.ops = &mtk_clk_gate_ops_setclr_inv,	\
	}

/*
* Real : Dummy bit mapping
* 0   ->   10
* 0   ->   11
*/
static const struct mtk_gate vdec_clks[] __initconst = {
	/* VDEC0 */
	GATE_VDEC0_I(CLK_VDEC_CKEN, "vdec_cken", "vdec_sel", 10),
	/* VDEC1 */
	GATE_VDEC1_I(CLK_VDEC_LARB1_CKEN, "vdec_larb1_cken", "vdec_sel", 11),
};

static const struct mtk_gate_regs venc_cg_regs = {
	.set_ofs = 0x4,
	.clr_ofs = 0x8,
	.sta_ofs = 0x0,
};

#define GATE_VENC_I(_id, _name, _parent, _shift) {	\
		.id = _id,				\
		.name = _name,				\
		.parent_name = _parent,			\
		.regs = &venc_cg_regs,			\
		.shift = _shift,			\
		.ops = &mtk_clk_gate_ops_setclr_inv,		\
	}

/*
* Real : Dummy bit mapping
* 0   ->   10
* 4   ->   11
* 8   ->   12
*/
static const struct mtk_gate venc_clks[] __initconst = {
	GATE_VENC_I(CLK_VENC_LARB, "venc_larb", "venc_sel", 10),
	GATE_VENC_I(CLK_VENC_CKE1VENC, "venc_cke1venc", "venc_sel", 11),
	GATE_VENC_I(CLK_VENC_JPGENC, "venc_jpgenc", "venc_sel", 12),
};

static const struct mtk_gate_regs ipu_cg_regs = {
	.set_ofs = 0x4,
	.clr_ofs = 0x8,
	.sta_ofs = 0x0,
};

#define GATE_IPU(_id, _name, _parent, _shift) {		\
		.id = _id,				\
		.name = _name,				\
		.parent_name = _parent,			\
		.regs = &ipu_cg_regs,			\
		.shift = _shift,			\
		.ops = &mtk_clk_gate_ops_setclr,	\
	}

/*
* Real : Dummy bit mapping
* 6   ->   10
* 7   ->   11
* 8   ->   12
* 9   ->   13
*/
static const struct mtk_gate ipu_clks[] __initconst = {
	GATE_IPU(CLK_IPU_IPU, "ipu_ipu", "dsp_sel", 0),
	GATE_IPU(CLK_IPU_ISP, "ipu_isp", "ipu_if_sel", 1),
	GATE_IPU(CLK_IPU_DFP, "ipu_dfp", "dfp_sel", 2),
	GATE_IPU(CLK_IPU_VAD, "ipu_vad", "dfp_sel", 3),
	GATE_IPU(CLK_IPU_JTAG, "ipu_jtag", "clk_null", 4),
	GATE_IPU(CLK_IPU_INFRA_AXI, "ipu_infra_axi", "ipu_if_sel", 5),
	GATE_IPU(CLK_IPU_INFRA_AHB, "ipu_infra_ahb", "ipu_if_sel", 10),/*use dummy*/
	GATE_IPU(CLK_IPU_MM_AXI, "ipu_mm_axi", "ipu_if_sel", 11),/*use dummy*/
	GATE_IPU(CLK_IPU_CAM_AXI, "ipu_cam_axi", "ipu_if_sel", 12),/*use dummy*/
	GATE_IPU(CLK_IPU_IMG_AXI, "ipu_img_axi", "ipu_if_sel", 13),/*use dummy*/
};

static const struct mtk_gate_regs cam_cg_regs = {
	.set_ofs = 0x4,
	.clr_ofs = 0x8,
	.sta_ofs = 0x0,
};

#define GATE_CAM(_id, _name, _parent, _shift) {		\
		.id = _id,				\
		.name = _name,				\
		.parent_name = _parent,			\
		.regs = &cam_cg_regs,			\
		.shift = _shift,			\
		.ops = &mtk_clk_gate_ops_setclr,	\
	}

/*
* Real : Dummy bit mapping
* 0   ->   13
* 2   ->   14
*/
static const struct mtk_gate cam_clks[] __initconst = {
	GATE_CAM(CLK_CAM_LARB6, "cam_larb6", "cam_sel", 13),/*use dummy*/
	GATE_CAM(CLK_CAM_DFP_VAD, "cam_dfp_vad", "cam_sel", 1),
	GATE_CAM(CLK_CAM_LARB3, "cam_larb3", "cam_sel", 14),/*use dummy*/
	GATE_CAM(CLK_CAM, "cam", "cam_sel", 6),
	GATE_CAM(CLK_CAMTG, "camtg", "cam_sel", 7),
	GATE_CAM(CLK_CAM_SENINF, "cam_seninf", "seninf_sel", 8),
	GATE_CAM(CLK_CAMSV0, "camsv0", "cam_sel", 9),
	GATE_CAM(CLK_CAMSV1, "camsv1", "cam_sel", 10),
	GATE_CAM(CLK_CAMSV2, "camsv2", "cam_sel", 11),
	GATE_CAM(CLK_CAM_CCU, "cam_ccu", "cam_sel", 12),
};

static const struct mtk_gate_regs img_cg_regs = {
	.set_ofs = 0x4,
	.clr_ofs = 0x8,
	.sta_ofs = 0x0,
};

#define GATE_IMG(_id, _name, _parent, _shift) {		\
		.id = _id,				\
		.name = _name,				\
		.parent_name = _parent,			\
		.regs = &img_cg_regs,			\
		.shift = _shift,			\
		.ops = &mtk_clk_gate_ops_setclr,	\
	}

/*
* Real : Dummy bit mapping
* 0   ->   6
* 1   ->   7
*/
static const struct mtk_gate img_clks[] __initconst = {
	GATE_IMG(CLK_IMG_LARB5, "img_larb5", "img_sel", 6),/*use dummy*/
	GATE_IMG(CLK_IMG_LARB2, "img_larb2", "img_sel", 7),/*use dummy*/
	GATE_IMG(CLK_IMG_DIP, "img_dip", "img_sel", 2),
	GATE_IMG(CLK_IMG_FDVT, "img_fdvt", "img_sel", 3),
	GATE_IMG(CLK_IMG_DPE, "img_dpe", "img_sel", 4),
	GATE_IMG(CLK_IMG_RSC, "img_rsc", "img_sel", 5),
};

static const struct mtk_gate_regs audio0_cg_regs = {
	.set_ofs = 0x0,
	.clr_ofs = 0x0,
	.sta_ofs = 0x0,
};

static const struct mtk_gate_regs audio1_cg_regs = {
	.set_ofs = 0x4,
	.clr_ofs = 0x4,
	.sta_ofs = 0x4,
};

#define GATE_AUDIO0(_id, _name, _parent, _shift) {	\
		.id = _id,				\
		.name = _name,				\
		.parent_name = _parent,			\
		.regs = &audio0_cg_regs,		\
		.shift = _shift,			\
		.ops = &mtk_clk_gate_ops,		\
	}

#define GATE_AUDIO1(_id, _name, _parent, _shift) {	\
		.id = _id,				\
		.name = _name,				\
		.parent_name = _parent,			\
		.regs = &audio1_cg_regs,		\
		.shift = _shift,			\
		.ops = &mtk_clk_gate_ops,		\
	}

static const struct mtk_gate audio_clks[] __initconst = {
	/* AUDIO0 */
	GATE_AUDIO0(CLK_AUDIO_AFE, "aud_afe", "audio_sel",
		2),
	GATE_AUDIO0(CLK_AUDIO_I2S, "aud_i2s", "clk_null",
		6),
	GATE_AUDIO0(CLK_AUDIO_22M, "aud_22m", "aud_engen1_sel",
		8),
	GATE_AUDIO0(CLK_AUDIO_24M, "aud_24m", "aud_engen2_sel",
		9),
	GATE_AUDIO0(CLK_AUDIO_APLL2_TUNER, "aud_apll2_tuner", "aud_engen2_sel",
		18),
	GATE_AUDIO0(CLK_AUDIO_APLL_TUNER, "aud_apll_tuner", "aud_engen1_sel",
		19),
	GATE_AUDIO0(CLK_AUDIO_TDM, "aud_tdm", "aud_engen1_sel",
		20),
	GATE_AUDIO0(CLK_AUDIO_ADC, "aud_adc", "audio_sel",
		24),
	GATE_AUDIO0(CLK_AUDIO_DAC, "aud_dac", "audio_sel",
		25),
	GATE_AUDIO0(CLK_AUDIO_DAC_PREDIS, "aud_dac_predis", "audio_sel",
		26),
	GATE_AUDIO0(CLK_AUDIO_TML, "aud_tml", "audio_sel",
		27),
	/* AUDIO1 */
	GATE_AUDIO1(CLK_AUDIO_I2S1_BCLK_SW, "aud_i2s1_bclk_sw", "audio_sel",
		4),
	GATE_AUDIO1(CLK_AUDIO_I2S2_BCLK_SW, "aud_i2s2_bclk_sw", "audio_sel",
		5),
	GATE_AUDIO1(CLK_AUDIO_I2S3_BCLK_SW, "aud_i2s3_bclk_sw", "audio_sel",
		6),
	GATE_AUDIO1(CLK_AUDIO_I2S4_BCLK_SW, "aud_i2s4_bclk_sw", "audio_sel",
		7),
	GATE_AUDIO1(CLK_AUDIO_I2S5_BCLK_SW, "aud_i2s5_bclk_sw", "audio_sel",
		8),
	GATE_AUDIO1(CLK_AUDIO_ADC_HIRES, "aud_adc_hires", "audio_h_sel",
		16),
	GATE_AUDIO1(CLK_AUDIO_ADC_HIRES_TML, "aud_adc_hires_tml", "audio_h_sel",
		17),
	GATE_AUDIO1(CLK_AUDIO_ADDA6_ADC, "aud_adda6_adc", "audio_sel",
		20),
	GATE_AUDIO1(CLK_AUDIO_ADDA6_ADC_HIRES, "aud_adda6_adc_hires", "audio_h_sel",
		21),
};

static const struct mtk_gate_regs mm0_cg_regs = {
	.set_ofs = 0x104,
	.clr_ofs = 0x108,
	.sta_ofs = 0x100,
};

static const struct mtk_gate_regs mm1_cg_regs = {
	.set_ofs = 0x114,
	.clr_ofs = 0x118,
	.sta_ofs = 0x110,
};

#define GATE_MM0(_id, _name, _parent, _shift) {		\
		.id = _id,				\
		.name = _name,				\
		.parent_name = _parent,			\
		.regs = &mm0_cg_regs,			\
		.shift = _shift,			\
		.ops = &mtk_clk_gate_ops_setclr,	\
	}

#define GATE_MM1(_id, _name, _parent, _shift) {		\
		.id = _id,				\
		.name = _name,				\
		.parent_name = _parent,			\
		.regs = &mm1_cg_regs,			\
		.shift = _shift,			\
		.ops = &mtk_clk_gate_ops_setclr,	\
	}

/*
* Real : Dummy bit mapping
* 0   ->   26
* 3   ->   27
* 4   ->   28
* 26   ->   29
* 1    ->   30
* 2    ->   31
*/
static const struct mtk_gate mm_clks[] __initconst = {
	/* MM0 */
#if 0
	GATE_MM0(CLK_MM_SMI_COMMON, "mm_smi_common", "mm_sel",
		0),
	GATE_MM0(CLK_MM_SMI_LARB0, "mm_smi_larb0", "mm_sel",
		1),
	GATE_MM0(CLK_MM_SMI_LARB1, "mm_smi_larb1", "mm_sel",
		2),
	GATE_MM0(CLK_MM_GALS_COMM0, "mm_gals_comm0", "mm_sel",
		3),
	GATE_MM0(CLK_MM_GALS_COMM1, "mm_gals_comm1", "mm_sel",
		4),
#endif
	GATE_MM0(CLK_MM_GALS_VENC2MM, "mm_gals_venc2mm", "mm_sel",
		5),
	GATE_MM0(CLK_MM_GALS_VDEC2MM, "mm_gals_vdec2mm", "mm_sel",
		6),
	GATE_MM0(CLK_MM_GALS_IMG2MM, "mm_gals_img2mm", "mm_sel",
		7),
	GATE_MM0(CLK_MM_GALS_CAM2MM, "mm_gals_cam2mm", "mm_sel",
		8),
	GATE_MM0(CLK_MM_GALS_IPU2MM, "mm_gals_ipu2mm", "mm_sel",
		9),
	GATE_MM0(CLK_MM_MDP_DL_TX_CLOCK, "mm_mdp_dl_tx_clock", "mm_sel",
		10),
	GATE_MM0(CLK_MM_IPU_DL_TX_CLOCK, "mm_ipu_dl_tx_clock", "mm_sel",
		11),
	GATE_MM0(CLK_MM_MDP_RDMA0, "mm_mdp_rdma0", "mm_sel",
		12),
	GATE_MM0(CLK_MM_MDP_RDMA1, "mm_mdp_rdma1", "mm_sel",
		13),
	GATE_MM0(CLK_MM_MDP_RSZ0, "mm_mdp_rsz0", "mm_sel",
		14),
	GATE_MM0(CLK_MM_MDP_RSZ1, "mm_mdp_rsz1", "mm_sel",
		15),
	GATE_MM0(CLK_MM_MDP_TDSHP, "mm_mdp_tdshp", "mm_sel",
		16),
	GATE_MM0(CLK_MM_MDP_WROT0, "mm_mdp_wrot0", "mm_sel",
		17),
	GATE_MM0(CLK_MM_MDP_WROT1, "mm_mdp_wrot1", "mm_sel",
		18),
	GATE_MM0(CLK_MM_FAKE_ENG, "mm_fake_eng", "mm_sel",
		19),
	GATE_MM0(CLK_MM_DISP_OVL0, "mm_disp_ovl0", "mm_sel",
		20),
	GATE_MM0(CLK_MM_DISP_OVL0_2L, "mm_disp_ovl0_2l", "mm_sel",
		21),
	GATE_MM0(CLK_MM_DISP_OVL1_2L, "mm_disp_ovl1_2l", "mm_sel",
		22),
	GATE_MM0(CLK_MM_DISP_RDMA0, "mm_disp_rdma0", "mm_sel",
		23),
	GATE_MM0(CLK_MM_DISP_RDMA1, "mm_disp_rdma1", "mm_sel",
		24),
	GATE_MM0(CLK_MM_DISP_WDMA0, "mm_disp_wdma0", "mm_sel",
		25),
#if 0
	GATE_MM0(CLK_MM_DISP_COLOR0, "mm_disp_color0", "mm_sel",
		26),/*use dummy*/
#endif
	GATE_MM0(CLK_MM_DISP_CCORR0, "mm_disp_ccorr0", "mm_sel",
		27),
	GATE_MM0(CLK_MM_DISP_AAL0, "mm_disp_aal0", "mm_sel",
		28),
	GATE_MM0(CLK_MM_DISP_GAMMA0, "mm_disp_gamma0", "mm_sel",
		29),
	GATE_MM0(CLK_MM_DISP_DITHER0, "mm_disp_dither0", "mm_sel",
		30),
	GATE_MM0(CLK_MM_DISP_SPLIT, "mm_disp_split", "mm_sel",
		31),
	/* MM1 */
	GATE_MM1(CLK_MM_DSI0_MM_CLOCK, "mm_dsi0_mm_clock", "mm_sel",
		0),
	GATE_MM1(CLK_MM_DSI0_INTERFACE_CLOCK, "mm_dsi0_interface_clock", "clk_null",
		1),
	GATE_MM1(CLK_MM_DSI1_MM_CLOCK, "mm_dsi1_mm_clock", "mm_sel",
		2),
	GATE_MM1(CLK_MM_DSI1_INTERFACE_CLOCK, "mm_dsi1_interface_clock", "clk_null",
		3),
	GATE_MM1(CLK_MM_FAKE_ENG2, "mm_fake_eng2", "mm_sel",
		4),
	GATE_MM1(CLK_MM_MDP_DL_RX_CLOCK, "mm_mdp_dl_rx_clock", "mm_sel",
		5),
	GATE_MM1(CLK_MM_IPU_DL_RX_CLOCK, "mm_ipu_dl_rx_clock", "mm_sel",
		6),
	GATE_MM1(CLK_MM_26M, "mm_26m", "f_f26m_ck",
		7),
	GATE_MM1(CLK_MMSYS_R2Y, "mmsys_r2y", "mm_sel",
		8),
	GATE_MM1(CLK_MM_DISP_RSZ, "mm_disp_rsz", "mm_sel",
		9),
	/* AO */
	GATE_MM1(CLK_MM_SMI_COMMON, "mm_smi_common", "mm_sel",
		26),
	GATE_MM1(CLK_MM_GALS_COMM0, "mm_gals_comm0", "mm_sel",
		27),
	GATE_MM1(CLK_MM_GALS_COMM1, "mm_gals_comm1", "mm_sel",
		28),
	GATE_MM1(CLK_MM_DISP_COLOR0, "mm_disp_color0", "mm_sel",
		29),
	GATE_MM1(CLK_MM_SMI_LARB0, "mm_smi_larb0", "mm_sel",
		30),
	GATE_MM1(CLK_MM_SMI_LARB1, "mm_smi_larb1", "mm_sel",
		31),/*use dummy*/
};

/* TODO: why disable critical */
#if 0
static bool timer_ready;
static struct clk_onecell_data *top_data;
static struct clk_onecell_data *pll_data;

static void mtk_clk_enable_critical(void)
{
	if (!timer_ready || !top_data || !pll_data)
		return;
#if 0
	clk_prepare_enable(top_data->clks[CLK_TOP_AXI_SEL]);
	clk_prepare_enable(top_data->clks[CLK_TOP_MEM_SEL]);
	clk_prepare_enable(top_data->clks[CLK_TOP_DDRPHYCFG_SEL]);
	clk_prepare_enable(top_data->clks[CLK_TOP_RTC_SEL]);
#endif
}
#endif
static void __init mtk_topckgen_init(struct device_node *node)
{
	struct clk_onecell_data *clk_data;
	void __iomem *base;
	int r;

	base = of_iomap(node, 0);
	if (!base) {
		pr_err("%s(): ioremap failed\n", __func__);
		return;
	}

	clk_data = mtk_alloc_clk_data(CLK_TOP_NR_CLK);

	mtk_clk_register_fixed_clks(fixed_clks, ARRAY_SIZE(fixed_clks), clk_data);

	mtk_clk_register_factors(top_divs, ARRAY_SIZE(top_divs), clk_data);
	mtk_clk_register_mux_upds(top_muxes, ARRAY_SIZE(top_muxes), base,
		&mt6758_clk_lock, clk_data);
	mtk_clk_register_mux_clr_set_upds(top_clr_set_muxes, ARRAY_SIZE(top_clr_set_muxes), base,
		&mt6758_clk_lock, clk_data);
	mtk_clk_register_gates(node, topck_clks, ARRAY_SIZE(topck_clks), clk_data);
	r = of_clk_add_provider(node, of_clk_src_onecell_get, clk_data);

	if (r)
		pr_err("%s(): could not register clock provider: %d\n",
			__func__, r);
	cksys_base = base;
	clk_writel(CLK_SCP_CFG_0, clk_readl(CLK_SCP_CFG_0) | 0x3EF);/*[10]:no need*/
	clk_writel(CLK_SCP_CFG_1, clk_readl(CLK_SCP_CFG_1) | 0x11);/*[1,2,3,8]: no need*/
	/*mtk_clk_enable_critical();*/

	/* PWM7, MFG31 MUX PDN */
	clk_writel(cksys_base + CK_CFG_1_CLR, 0x00008080);
	clk_writel(cksys_base + CK_CFG_1_SET, 0x00008080);

	/* msdc50_0_hclk15, msdc50_023 MUX PDN */
	clk_writel(cksys_base + CK_CFG_3_CLR, 0x00808000);
	clk_writel(cksys_base + CK_CFG_3_SET, 0x00808000);

	/* msdc30_2 7, msdc30_3 15 MUX PDN */
	clk_writel(cksys_base + CK_CFG_4_CLR, 0x00008000);
	clk_writel(cksys_base + CK_CFG_4_SET, 0x00008000);

#if 1
	clk_writel(cksys_base + CK_CFG_5_CLR, 0x80808000);
	clk_writel(cksys_base + CK_CFG_5_SET, 0x80808000);
#else
	clk_writel(cksys_base + CK_CFG_5_CLR, 0x80800000);
	clk_writel(cksys_base + CK_CFG_5_SET, 0x80800000);
	/* scp15, atb23 MUX PDN */
	if (strncmp(CONFIG_BUILD_ARM64_APPENDED_DTB_IMAGE_NAMES, "mediatek/k58v1_64_op01_lp", 25) == 0) {
		clk_writel(cksys_base + CK_CFG_5_CLR, 0x80808000);
		clk_writel(cksys_base + CK_CFG_5_SET, 0x80808000);
	} else if (strncmp(CONFIG_BUILD_ARM64_APPENDED_DTB_IMAGE_NAMES, "mediatek/k58v1_64_lp", 20) == 0) {
		clk_writel(cksys_base + CK_CFG_5_CLR, 0x80808000);
		clk_writel(cksys_base + CK_CFG_5_SET, 0x80808000);
	}
#endif

#if 0
	/* dpi0 7, scam 15 MUX PDN */
	clk_writel(cksys_base + CK_CFG_6_CLR, 0x80008000);
	clk_writel(cksys_base + CK_CFG_6_SET, 0x80008000);
#endif

	/* ssusb_top_sys 15, ssusb_top_xhci 23 MUX PDN */
	clk_writel(cksys_base + CK_CFG_7_CLR, 0x80808080);
	clk_writel(cksys_base + CK_CFG_7_SET, 0x80808080);

	clk_writel(cksys_base + CK_CFG_8_CLR, 0x00808080);
	clk_writel(cksys_base + CK_CFG_8_SET, 0x00808080);

	clk_writel(cksys_base + CK_CFG_9_CLR, 0x80800080);
	clk_writel(cksys_base + CK_CFG_9_SET, 0x80800080);

	clk_writel(cksys_base + CK_CFG_10_CLR, 0x00008000);
	clk_writel(cksys_base + CK_CFG_10_SET, 0x00008000);

	clk_writel(cksys_base + CK_CFG_11_CLR, 0x00008000);
	clk_writel(cksys_base + CK_CFG_11_SET, 0x00008000);

}
CLK_OF_DECLARE(mtk_topckgen, "mediatek,topckgen", mtk_topckgen_init);

static void __init mtk_infracfg_ao_init(struct device_node *node)
{
	struct clk_onecell_data *clk_data;
	void __iomem *base;
	int r;

	base = of_iomap(node, 0);
	if (!base) {
		pr_err("%s(): ioremap failed\n", __func__);
		return;
	}

	clk_data = mtk_alloc_clk_data(CLK_INFRA_NR_CLK);

	mtk_clk_register_gates(node, infra_clks, ARRAY_SIZE(infra_clks), clk_data);
	r = of_clk_add_provider(node, of_clk_src_onecell_get, clk_data);

	if (r)
		pr_err("%s(): could not register clock provider: %d\n",
			__func__, r);
	infracfg_base = base;
	#if 0
	clk_writel(INFRA_TOPAXI_SI0_CTL, clk_readl(INFRA_TOPAXI_SI0_CTL) | 0x2);/*CDC, MFG issue*/
	#endif
	/*mtk_clk_enable_critical();*/
#if !MT_CCF_BRINGUP
	clk_writel(INFRA_PDN_SET0, INFRA_CG0);
	clk_writel(INFRA_PDN_SET1, INFRA_CG1);
	clk_writel(INFRA_PDN_SET3, INFRA_CG3);
#endif
}
CLK_OF_DECLARE(mtk_infracfg_ao, "mediatek,infracfg_ao", mtk_infracfg_ao_init);
#if 0
struct mtk_clk_usb {
	int id;
	const char *name;
	const char *parent;
	u32 reg_ofs;
};

#define APMIXED_USB(_id, _name, _parent, _reg_ofs) {			\
		.id = _id,						\
		.name = _name,						\
		.parent = _parent,					\
		.reg_ofs = _reg_ofs,					\
	}

static const struct mtk_clk_usb apmixed_usb[] __initconst = {
	APMIXED_USB(CLK_APMIXED_REF2USB_TX, "ref2usb_tx", "clk26m", 0x8),
};
#endif
/* FIXME: modify FMAX */
#define MT6758_PLL_FMAX		(3200UL * MHZ)

#define CON0_MT6758_RST_BAR	BIT(24)

#define PLL_B(_id, _name, _reg, _pwr_reg, _en_mask, _flags, _pcwbits,	\
			_pd_reg, _pd_shift, _tuner_reg, _pcw_reg,	\
			_pcw_shift, _div_table) {			\
		.id = _id,						\
		.name = _name,						\
		.reg = _reg,						\
		.pwr_reg = _pwr_reg,					\
		.en_mask = _en_mask,					\
		.flags = _flags,					\
		.rst_bar_mask = CON0_MT6758_RST_BAR,			\
		.fmax = MT6758_PLL_FMAX,				\
		.pcwbits = _pcwbits,					\
		.pd_reg = _pd_reg,					\
		.pd_shift = _pd_shift,					\
		.tuner_reg = _tuner_reg,				\
		.pcw_reg = _pcw_reg,					\
		.pcw_shift = _pcw_shift,				\
		.div_table = _div_table,				\
	}

#define PLL(_id, _name, _reg, _pwr_reg, _en_mask, _flags, _pcwbits,	\
			_pd_reg, _pd_shift, _tuner_reg, _pcw_reg,	\
			_pcw_shift)					\
		PLL_B(_id, _name, _reg, _pwr_reg, _en_mask, _flags, _pcwbits, \
			_pd_reg, _pd_shift, _tuner_reg, _pcw_reg, _pcw_shift, \
			NULL)

static const struct mtk_pll_data plls[] = {
	/* FIXME: need to fix flags/div_table/tuner_reg/table */
#if 1
	PLL(CLK_APMIXED_MAINPLL, "mainpll", 0x0230, 0x023C, 0x00000001, HAVE_RST_BAR,
		22, 0x0234, 28, 0, 0x0234, 0),
#endif

	PLL(CLK_APMIXED_GPUPLL, "gpupll", 0x0210, 0x021C, 0x00000001, 0,
		22, 0x0214, 28, 0, 0x0214, 0),

	PLL(CLK_APMIXED_MSDCPLL, "msdcpll", 0x0250, 0x025C, 0x00000001, 0,
		22, 0x0254, 28, 0, 0x0254, 0),
	PLL(CLK_APMIXED_UNIVPLL, "univpll", 0x0240, 0x024C, 0x00000001, HAVE_RST_BAR,
		22, 0x0244, 28, 0, 0x0244, 0),

	PLL(CLK_APMIXED_TVDPLL, "tvdpll", 0x0280, 0x028C, 0x00000001, 0,
		22, 0x0284, 28, 0, 0x0284, 0),
	PLL(CLK_APMIXED_MMPLL, "mmpll", 0x0260, 0x026C, 0x00000001, HAVE_RST_BAR,
		22, 0x0264, 28, 0, 0x0264, 0),
	PLL(CLK_APMIXED_APLL1, "apll1", 0x02A0, 0x02B8, 0x00000001, 0,
		32, 0x02A4, 28, 0, 0x02A8, 0),
	PLL(CLK_APMIXED_APLL2, "apll2", 0x02C0, 0x02D8, 0x00000001, 0,
		32, 0x02C4, 28, 0, 0x02C8, 0),
};

static void __init mtk_apmixedsys_init(struct device_node *node)
{
	struct clk_onecell_data *clk_data;
	void __iomem *base;
	int r;

	base = of_iomap(node, 0);
	if (!base) {
		pr_err("%s(): ioremap failed\n", __func__);
		return;
	}

	clk_data = mtk_alloc_clk_data(CLK_APMIXED_NR_CLK);

	/* FIXME: add code for APMIXEDSYS */
	mtk_clk_register_plls(node, plls, ARRAY_SIZE(plls), clk_data);
	r = of_clk_add_provider(node, of_clk_src_onecell_get, clk_data);

	if (r)
		pr_err("%s(): could not register clock provider: %d\n",
			__func__, r);
	apmixed_base = base;

	clk_clrl(AP_PLL_CON1, 3 << 6);/*CLKSQ_EN, CLKSQ_LPF HW Mode*/
#if 1
	clk_writel(AP_PLL_CON3, clk_readl(AP_PLL_CON3) & 0x0F63D8F6);/* ARMPLLs, UNIVPLL,EMI SW Mode */
	clk_writel(AP_PLL_CON4, clk_readl(AP_PLL_CON4) & 0x4F6000F6);/* ARMPLLs, UNIVPLL,EMI SW Mode, skip out off */
	clk_writel(AP_PLL_CON8, clk_readl(AP_PLL_CON8) & 0xFFFFFFFE);/* MAINPLL, Delay mode */
#endif
#if 1
/*GPUPLL*/
	clk_clrl(GPUPLL_CON0, PLL_EN);
	clk_setl(GPUPLL_PWR_CON0, PLL_ISO_EN);
	clk_clrl(GPUPLL_PWR_CON0, PLL_PWR_ON);
/*UNIVPLL*/
	clk_clrl(UNIVPLL_CON0, PLL_EN);
	clk_setl(UNIVPLL_PWR_CON0, PLL_ISO_EN);
	clk_clrl(UNIVPLL_PWR_CON0, PLL_PWR_ON);
/*MSDCPLL*/
	clk_clrl(MSDCPLL_CON0, PLL_EN);
	clk_setl(MSDCPLL_PWR_CON0, PLL_ISO_EN);
	clk_clrl(MSDCPLL_PWR_CON0, PLL_PWR_ON);
/*MMPLL*/
#if 0
	clk_clrl(MMPLL_CON0, PLL_EN);
	clk_setl(MMPLL_PWR_CON0, PLL_ISO_EN);
	clk_clrl(MMPLL_PWR_CON0, PLL_PWR_ON);
#endif
/*TVDPLL*/
	clk_clrl(TVDPLL_CON0, PLL_EN);
	clk_setl(TVDPLL_PWR_CON0, PLL_ISO_EN);
	clk_clrl(TVDPLL_PWR_CON0, PLL_PWR_ON);
/*APLL1*/
	clk_clrl(APLL1_CON0, PLL_EN);
	clk_setl(APLL1_PWR_CON0, PLL_ISO_EN);
	clk_clrl(APLL1_PWR_CON0, PLL_PWR_ON);
/*APLL2*/
	clk_clrl(APLL2_CON0, PLL_EN);
	clk_setl(APLL2_PWR_CON0, PLL_ISO_EN);
	clk_clrl(APLL2_PWR_CON0, PLL_PWR_ON);
#endif
}
CLK_OF_DECLARE(mtk_apmixedsys, "mediatek,apmixedsys",
		mtk_apmixedsys_init);

static void __init mtk_audio_init(struct device_node *node)
{
	struct clk_onecell_data *clk_data;
	void __iomem *base;
	int r;

	base = of_iomap(node, 0);
	if (!base) {
		pr_err("%s(): ioremap failed\n", __func__);
		return;
	}

	clk_data = mtk_alloc_clk_data(CLK_AUDIO_NR_CLK);

	mtk_clk_register_gates(node, audio_clks, ARRAY_SIZE(audio_clks), clk_data);

	r = of_clk_add_provider(node, of_clk_src_onecell_get, clk_data);

	if (r)
		pr_err("%s(): could not register clock provider: %d\n",
			__func__, r);
	audio_base = base;

#if 0/*MT_CCF_BRINGUP*/
	clk_writel(AUDIO_TOP_CON0, AUDIO_DISABLE_CG0);
	clk_writel(AUDIO_TOP_CON1, AUDIO_DISABLE_CG1);
#endif

}
CLK_OF_DECLARE(mtk_audio, "mediatek,audio", mtk_audio_init);

static void __init mtk_camsys_init(struct device_node *node)
{
	struct clk_onecell_data *clk_data;
	void __iomem *base;
	int r;

	base = of_iomap(node, 0);
	if (!base) {
		pr_err("%s(): ioremap failed\n", __func__);
		return;
	}
	clk_data = mtk_alloc_clk_data(CLK_CAM_NR_CLK);

	mtk_clk_register_gates(node, cam_clks, ARRAY_SIZE(cam_clks), clk_data);

	r = of_clk_add_provider(node, of_clk_src_onecell_get, clk_data);

	if (r)
		pr_err("%s(): could not register clock provider: %d\n",
			__func__, r);
	cam_base = base;

#if 0/*MT_CCF_BRINGUP*/
	clk_writel(CAMSYS_CG_CLR, CAM_DISABLE_CG);
#endif
}
CLK_OF_DECLARE(mtk_camsys, "mediatek,camsys", mtk_camsys_init);

static void __init mtk_imgsys_init(struct device_node *node)
{
	struct clk_onecell_data *clk_data;
	void __iomem *base;
	int r;

	base = of_iomap(node, 0);
	if (!base) {
		pr_err("%s(): ioremap failed\n", __func__);
		return;
	}
	clk_data = mtk_alloc_clk_data(CLK_IMG_NR_CLK);

	mtk_clk_register_gates(node, img_clks, ARRAY_SIZE(img_clks), clk_data);

	r = of_clk_add_provider(node, of_clk_src_onecell_get, clk_data);

	if (r)
		pr_err("%s(): could not register clock provider: %d\n",
			__func__, r);
	img_base = base;

#if 0/*MT_CCF_BRINGUP*/
	clk_writel(IMG_CG_CLR, IMG_DISABLE_CG);
#endif
}
CLK_OF_DECLARE(mtk_imgsys, "mediatek,imgsys", mtk_imgsys_init);

static void __init mtk_ipusys_init(struct device_node *node)
{
	struct clk_onecell_data *clk_data;
	void __iomem *base;
	int r;

	base = of_iomap(node, 0);
	if (!base) {
		pr_err("%s(): ioremap failed\n", __func__);
		return;
	}
	clk_data = mtk_alloc_clk_data(CLK_IPU_NR_CLK);

	mtk_clk_register_gates(node, ipu_clks, ARRAY_SIZE(ipu_clks), clk_data);

	r = of_clk_add_provider(node, of_clk_src_onecell_get, clk_data);

	if (r)
		pr_err("%s(): could not register clock provider: %d\n",
			__func__, r);
	ipu_base = base;

#if 0/*MT_CCF_BRINGUP*/
	clk_writel(IPU_CG_CLR, IPU_DISABLE_CG);
#endif

}
CLK_OF_DECLARE(mtk_ipusys, "mediatek,ipusys", mtk_ipusys_init);

static void __init mtk_g3d_config_init(struct device_node *node)
{
	struct clk_onecell_data *clk_data;
	void __iomem *base;
	int r;

	base = of_iomap(node, 0);
	if (!base) {
		pr_err("%s(): ioremap failed\n", __func__);
		return;
	}

	clk_data = mtk_alloc_clk_data(CLK_MFG_NR_CLK);

	mtk_clk_register_gates(node, mfg_clks, ARRAY_SIZE(mfg_clks), clk_data);

	r = of_clk_add_provider(node, of_clk_src_onecell_get, clk_data);

	if (r)
		pr_err("%s(): could not register clock provider: %d\n",
			__func__, r);
	mfgcfg_base = base;

#if 0/*MT_CCF_BRINGUP*/
	clk_writel(MFG_CG_CLR, MFG_DISABLE_CG);
#endif

}
CLK_OF_DECLARE(mtk_g3d_config, "mediatek,g3d_config", mtk_g3d_config_init);

static void __init mtk_mmsys_config_init(struct device_node *node)
{
	struct clk_onecell_data *clk_data;
	void __iomem *base;
	int r;

	base = of_iomap(node, 0);
	if (!base) {
		pr_err("%s(): ioremap failed\n", __func__);
		return;
	}
	clk_data = mtk_alloc_clk_data(CLK_MM_NR_CLK);

	mtk_clk_register_gates(node, mm_clks, ARRAY_SIZE(mm_clks), clk_data);

	r = of_clk_add_provider(node, of_clk_src_onecell_get, clk_data);

	if (r)
		pr_err("%s(): could not register clock provider: %d\n",
			__func__, r);
	mmsys_config_base = base;
#if 0/*MT_CCF_BRINGUP*/
	clk_writel(MM_CG_CLR0, MM_DISABLE_CG0);
	clk_writel(MM_CG_CLR1, MM_DISABLE_CG1);
#endif
}
CLK_OF_DECLARE(mtk_mmsys_config, "mediatek,mmsys_config",
		mtk_mmsys_config_init);

static void __init mtk_pericfg_init(struct device_node *node)
{
	struct clk_onecell_data *clk_data;
	void __iomem *base;
	int r;

	base = of_iomap(node, 0);
	if (!base) {
		pr_err("%s(): ioremap failed\n", __func__);
		return;
	}

	clk_data = mtk_alloc_clk_data(CLK_PERICFG_NR_CLK);

	mtk_clk_register_gates(node, pericfg_clks, ARRAY_SIZE(pericfg_clks), clk_data);

	r = of_clk_add_provider(node, of_clk_src_onecell_get, clk_data);

	if (r)
		pr_err("%s(): could not register clock provider: %d\n",
			__func__, r);
	pericfg_base = base;
#if !MT_CCF_BRINGUP
	clk_writel(PERI_CG_SET0, PERI_CG0);
	clk_writel(PERI_CG_SET1, PERI_CG1);
	clk_writel(PERI_CG_SET2, PERI_CG2);
	clk_writel(PERI_CG_SET3, PERI_CG3);
	clk_writel(PERI_CG_SET4, PERI_CG4);
#endif
}
CLK_OF_DECLARE(mtk_pericfg, "mediatek,pericfg", mtk_pericfg_init);

static void __init mtk_vdec_top_global_con_init(struct device_node *node)
{
	struct clk_onecell_data *clk_data;
	void __iomem *base;
	int r;

	base = of_iomap(node, 0);
	if (!base) {
		pr_err("%s(): ioremap failed\n", __func__);
		return;
	}
	clk_data = mtk_alloc_clk_data(CLK_VDEC_NR_CLK);

	mtk_clk_register_gates(node, vdec_clks, ARRAY_SIZE(vdec_clks), clk_data);

	r = of_clk_add_provider(node, of_clk_src_onecell_get, clk_data);

	if (r)
		pr_err("%s(): could not register clock provider: %d\n",
			__func__, r);
	vdec_gcon_base = base;

#if 0/*MT_CCF_BRINGUP*/
	clk_writel(VDEC_CKEN_SET, VDE_DISABLE_CG);
	clk_writel(LARB1_CKEN_SET, LARB1_DISABLE_CG);
#endif
}
CLK_OF_DECLARE(mtk_vdec_top_global_con, "mediatek,vdec_top_global_con", mtk_vdec_top_global_con_init);

static void __init mtk_venc_global_con_init(struct device_node *node)
{
	struct clk_onecell_data *clk_data;
	void __iomem *base;
	int r;

	base = of_iomap(node, 0);
	if (!base) {
		pr_err("%s(): ioremap failed\n", __func__);
		return;
	}
	clk_data = mtk_alloc_clk_data(CLK_VENC_NR_CLK);

	mtk_clk_register_gates(node, venc_clks, ARRAY_SIZE(venc_clks), clk_data);

	r = of_clk_add_provider(node, of_clk_src_onecell_get, clk_data);

	if (r)
		pr_err("%s(): could not register clock provider: %d\n",
			__func__, r);
	venc_gcon_base = base;

#if 0/*MT_CCF_BRINGUP*/
	clk_writel(VENC_CG_SET, VEN_DISABLE_CG);
#endif
}
CLK_OF_DECLARE(mtk_venc_global_con, "mediatek,venc_global_con",
		mtk_venc_global_con_init);

unsigned int mt_get_ckgen_freq(unsigned int ID)
{
	int output = 0, i = 0;
	unsigned int temp, clk26cali_0, clk_dbg_cfg, clk_misc_cfg_1, clk26cali_2;

	clk_dbg_cfg = clk_readl(CLK_CFG_21);
	clk_writel(CLK_CFG_21, (clk_dbg_cfg & 0xFF80FFFF)|(ID << 16));

	clk_misc_cfg_1 = clk_readl(CLK_MISC_CFG_1);
	clk_writel(CLK_MISC_CFG_1, 0xFF00FFFF);

	clk26cali_2 = clk_readl(CLK26CALI_2);
	clk26cali_0 = clk_readl(CLK26CALI_0);
	clk_writel(CLK26CALI_2, 0x03FF0000);
	clk_writel(CLK26CALI_0, 0x80);
	clk_writel(CLK26CALI_0, 0x90);

	/* wait frequency meter finish */
	while (clk_readl(CLK26CALI_0) & 0x10) {
		udelay(10);
		i++;
		if (i > 10000)
			break;
	}

	temp = clk_readl(CLK26CALI_2) & 0xFFFF;

	output = (temp * 26000) / 1024;

	clk_writel(CLK_CFG_21, clk_dbg_cfg);
	clk_writel(CLK_MISC_CFG_1, clk_misc_cfg_1);
	clk_writel(CLK26CALI_0, clk26cali_0);
	clk_writel(CLK26CALI_2, clk26cali_2);

	/*print("ckgen meter[%d] = %d Khz\n", ID, output);*/
	if (i > 10000)
		return 0;
	else
		return output;

}

unsigned int mt_get_abist_freq(unsigned int ID)
{
	int output = 0, i = 0;
	unsigned int temp, clk26cali_0, clk_dbg_cfg, clk_misc_cfg_1, clk26cali_1;

	clk_dbg_cfg = clk_readl(CLK_CFG_20);
	clk_writel(CLK_CFG_20, (clk_dbg_cfg & 0xFFFF80FF)|(ID << 8)|(0x01 << 31));

	clk_misc_cfg_1 = clk_readl(CLK_MISC_CFG_1);
	#if _DIV4_
	clk_writel(CLK_MISC_CFG_1, 0xFFFFF003);
	#else
	clk_writel(CLK_MISC_CFG_1, 0x00000000);
	#endif

	clk26cali_1 = clk_readl(CLK26CALI_1);
	clk26cali_0 = clk_readl(CLK26CALI_0);

	clk_writel(CLK26CALI_0, 0x80);
	clk_writel(CLK26CALI_0, 0x81);

	/* wait frequency meter finish */
	while (clk_readl(CLK26CALI_0) & 0x01) {
		udelay(10);
		i++;
		if (i > 10000)
		break;
	}

	temp = clk_readl(CLK26CALI_1) & 0xFFFF;

	#if _DIV4_
	output = ((temp * 26000) / 1024)*4;
	#else
	output = (temp * 26000) / 1024;
	#endif

	clk_writel(CLK_CFG_20, clk_dbg_cfg);
	clk_writel(CLK_MISC_CFG_1, clk_misc_cfg_1);
	clk_writel(CLK26CALI_0, clk26cali_0);
	clk_writel(CLK26CALI_1, clk26cali_1);

	/*pr_debug("%s = %d Khz\n", abist_array[ID-1], output);*/
	if (i > 10000)
		return 0;
	else
		return output;
}

#if 0
unsigned int mt_get_abist2_freq(unsigned int ID)
{
	int output = 0, i = 0;
	unsigned int temp, clk26cali_0, clk_dbg_cfg, clk_misc_cfg_1, clk26cali_1;

	clk_dbg_cfg = clk_readl(CLK_CFG_20);
	clk_writel(CLK_CFG_20, (clk_dbg_cfg & 0xFFFF80FF)|(ID << 8)|(0x01 << 31)|(0x01 << 14));

	clk_misc_cfg_1 = clk_readl(CLK_MISC_CFG_1);
	clk_writel(CLK_MISC_CFG_1, 0x00000000);

	clk26cali_1 = clk_readl(CLK26CALI_1);
	clk26cali_0 = clk_readl(CLK26CALI_0);

	clk_writel(CLK26CALI_0, 0x80);
	clk_writel(CLK26CALI_0, 0x81);

	/* wait frequency meter finish */
	while (clk_readl(CLK26CALI_0) & 0x01) {
		mdelay(10);
		i++;
		if (i > 10)
		break;
	}

	temp = clk_readl(CLK26CALI_1) & 0xFFFF;

	output = (temp * 26000) / 1024;

	clk_writel(CLK_CFG_20, clk_dbg_cfg);
	clk_writel(CLK_MISC_CFG_1, clk_misc_cfg_1);
	clk_writel(CLK26CALI_0, clk26cali_0);
	clk_writel(CLK26CALI_1, clk26cali_1);

	/*pr_debug("%s = %d Khz\n", abist_array[ID-1], output);*/
	return output;
}
#endif

#if 0
void switch_mfg_clk(int src)
{
	if (src == 0)
		clk_writel(TOP_CLK2, clk_readl(TOP_CLK2)&0xfffffcff);
	else
		clk_writel(TOP_CLK2, (clk_readl(TOP_CLK2)&0xfffffcff)|(0x01<<8));
}

#endif

void mp_enter_suspend(int id, int suspend)
{
	/* mp0*/
	if (id == 0) {
		if (suspend) {
			clk_writel(AP_PLL_CON3, clk_readl(AP_PLL_CON3) & 0xfdff7fdf);
			clk_writel(AP_PLL_CON4, clk_readl(AP_PLL_CON4) & 0xfdff7fdf);
		} else {
			clk_writel(AP_PLL_CON3, clk_readl(AP_PLL_CON3) | 0x02008020);
			clk_writel(AP_PLL_CON4, clk_readl(AP_PLL_CON4) | 0x02008020);
		}
	} else if (id == 1) { /* mp1 */
		if (suspend) {
			clk_writel(AP_PLL_CON3, clk_readl(AP_PLL_CON3) & 0xfbfeffbf);
			clk_writel(AP_PLL_CON4, clk_readl(AP_PLL_CON4) & 0xfbfeffbf);
		} else {
			clk_writel(AP_PLL_CON3, clk_readl(AP_PLL_CON3) | 0x04010040);
			clk_writel(AP_PLL_CON4, clk_readl(AP_PLL_CON4) | 0x04010040);
		}
	}
}

void pll_if_on(void)
{
	if (clk_readl(ARMPLL1_CON0) & 0x1)
		pr_notice("suspend warning: ARMPLL1 is on!!!\n");
	if (clk_readl(ARMPLL2_CON0) & 0x1)
		pr_notice("suspend warning: ARMPLL2 is on!!!\n");
	if (clk_readl(CCIPLL_CON0) & 0x1)
		pr_notice("suspend warning: CCIPLL is on!!!\n");
	if (clk_readl(UNIVPLL_CON0) & 0x1)
		pr_err("suspend warning: UNIVPLL is on!!!\n");
	if (clk_readl(GPUPLL_CON0) & 0x1)
		pr_err("suspend warning: GPUPLL is on!!!\n");
	if (clk_readl(MMPLL_CON0) & 0x1)
		pr_err("suspend warning: MMPLL is on!!!\n");
	if (clk_readl(TVDPLL_CON0) & 0x1)
		pr_notice("suspend warning: TVDPLL is on!!!\n");
	if (clk_readl(MSDCPLL_CON0) & 0x1)
		pr_err("suspend warning: MSDCPLL is on!!!\n");
	if (clk_readl(APLL1_CON0) & 0x1)
		pr_err("suspend warning: APLL1 is on!!!\n");
	if (clk_readl(APLL2_CON0) & 0x1)
		pr_err("suspend warning: APLL2 is on!!!\n");
	if (clk_readl(MAINPLL_CON0) & 0x1)
		pr_notice("suspend warning: MAINPLL is on!!!\n");
	if (clk_readl(MPLL_CON0) & 0x1)
		pr_notice("suspend warning: MPLL is on!!!\n");
	if (clk_readl(EMIPLL_CON0) & 0x1)
		pr_notice("suspend warning: EMIPLL is on!!!\n");
}

#define AUDIO_ENABLE_CG0 0xAF1D4344
#define AUDIO_ENABLE_CG1 0x00330000

#define INFRA_ENABLE_CG0 0xFFFFFFFF
#define INFRA_ENABLE_CG1 0xFFFFFFFF
#define INFRA_ENABLE_CG3 0xFFFFFFFF


#define CAMSYS_ENABLE_CG	0x1FC7
#define IMG_ENABLE_CG	0x3F
#define IPU_ENABLE_CG	0x3FF
#define MFG_ENABLE_CG	0x1
#define MM_ENABLE_CG0	0xFFFFFFFF /* un-gating in preloader */
#define MM_ENABLE_CG1  0xFFFFFFFF /* un-gating in preloader */
#define PERI_ENABLE_CG0 0xFFFFFFFF /* un-gating in preloader */
#define PERI_ENABLE_CG1 0xFFFFFFFF /* un-gating in preloader */
#define PERI_ENABLE_CG2 0xFFFFFFFF /* un-gating in preloader */
#define PERI_ENABLE_CG3 0xFFFFFFFF /* un-gating in preloader */
#define PERI_ENABLE_CG4 0xFFFFFFFF /* un-gating in preloader */

#define VDEC_ENABLE_CG	0x1      /* inverse */
#define LARB_ENABLE_CG	0x1	  /* inverse */
#define VENC_ENABLE_CG	0x111 /* inverse */

void clock_force_off(void)
{
	#if 0
	/*INFRA_AO CG*/
	clk_writel(INFRA_PDN_SET0, INFRA_ENABLE_CG0);
	clk_writel(INFRA_PDN_SET1, INFRA_ENABLE_CG1);
	clk_writel(INFRA_PDN_SET3, INFRA_ENABLE_CG3);
	/*PERI CG*/
	clk_writel(PERI_CG_SET0, PERI_ENABLE_CG0);
	clk_writel(PERI_CG_SET1, PERI_ENABLE_CG1);
	clk_writel(PERI_CG_SET2, PERI_ENABLE_CG2);
	clk_writel(PERI_CG_SET3, PERI_ENABLE_CG3);
	clk_writel(PERI_CG_SET4, PERI_ENABLE_CG4);
	#endif
	/*DISP CG*/
	clk_writel(MM_CG_SET0, MM_ENABLE_CG0);
	clk_writel(MM_CG_SET1, MM_ENABLE_CG1);
	/*AUDIO*/
	clk_writel(AUDIO_TOP_CON0, AUDIO_ENABLE_CG0);
	clk_writel(AUDIO_TOP_CON1, AUDIO_ENABLE_CG1);
	/*MFG*/
	clk_writel(MFG_CG_SET, MFG_ENABLE_CG);
	/*ISP*/
	clk_writel(IMG_CG_SET, IMG_ENABLE_CG);
	/*VDE not inverse*/
	clk_writel(VDEC_CKEN_CLR, VDEC_ENABLE_CG);
	clk_writel(LARB1_CKEN_CLR, LARB_ENABLE_CG);
	/*VENC not inverse*/
	clk_writel(VENC_CG_CLR, VENC_ENABLE_CG);
	/*CAM*/
	clk_writel(CAMSYS_CG_SET, CAMSYS_ENABLE_CG);
	/*IPU*/
	clk_writel(IPU_CG_SET, IPU_ENABLE_CG);
}

void mux_force_off(void)
{
	/* scp, aud_int MUX PDN */
	clk_writel(cksys_base + CK_CFG_6_CLR, 0x80008000);
	clk_writel(cksys_base + CK_CFG_6_SET, 0x80008000);
}

void mmsys_cg_check(void)
{
	pr_err("[MM_CG_CON0]=0x%08x\n", clk_readl(MM_CG_CON0));
	pr_err("[MM_CG_CON1]=0x%08x\n", clk_readl(MM_CG_CON1));
}
#if 1
void mfgsys_clk_check(void)
{
	pr_notice("CLK_CFG_2 = 0x%08x\n", clk_readl(CLK_CFG_2));
	pr_notice("GPUPLL = 0x%08x, 0x%08x, 0x%08x\n",
		clk_readl(GPUPLL_CON0), clk_readl(GPUPLL_CON1), clk_readl(GPUPLL_PWR_CON0));
}

void mfgsys_cg_check(void)
{
	pr_notice("MFG_CG_CON = 0x%08x\n", clk_readl(MFG_CG_CON));
}
#endif
void pll_force_off(void)
{
/*GPUPLL*/
	clk_clrl(GPUPLL_CON0, PLL_EN);
	clk_setl(GPUPLL_PWR_CON0, PLL_ISO_EN);
	clk_clrl(GPUPLL_PWR_CON0, PLL_PWR_ON);
/*UNIVPLL*/
	clk_clrl(UNIVPLL_CON0, PLL_EN);
	clk_setl(UNIVPLL_PWR_CON0, PLL_ISO_EN);
	clk_clrl(UNIVPLL_PWR_CON0, PLL_PWR_ON);
/*MSDCPLL*/
	clk_clrl(MSDCPLL_CON0, PLL_EN);
	clk_setl(MSDCPLL_PWR_CON0, PLL_ISO_EN);
	clk_clrl(MSDCPLL_PWR_CON0, PLL_PWR_ON);
/*TVDPLL*/
	clk_clrl(TVDPLL_CON0, PLL_EN);
	clk_setl(TVDPLL_PWR_CON0, PLL_ISO_EN);
	clk_clrl(TVDPLL_PWR_CON0, PLL_PWR_ON);
/*MMPLL*/
	clk_clrl(MMPLL_CON0, PLL_EN);
	clk_setl(MMPLL_PWR_CON0, PLL_ISO_EN);
	clk_clrl(MMPLL_PWR_CON0, PLL_PWR_ON);
/*APLL1*/
	clk_clrl(APLL1_CON0, PLL_EN);
	clk_setl(APLL1_PWR_CON0, PLL_ISO_EN);
	clk_clrl(APLL1_PWR_CON0, PLL_PWR_ON);
/*APLL2*/
	clk_clrl(APLL2_CON0, PLL_EN);
	clk_setl(APLL2_PWR_CON0, PLL_ISO_EN);
	clk_clrl(APLL2_PWR_CON0, PLL_PWR_ON);
}

void armpll_control(int id, int on)
{
	if (id == 1) {
		if (on) {
			mt_reg_sync_writel((clk_readl(ARMPLL1_PWR_CON0) | 0x01), ARMPLL1_PWR_CON0);
			udelay(100);
			mt_reg_sync_writel((clk_readl(ARMPLL1_PWR_CON0) & 0xfffffffd), ARMPLL1_PWR_CON0);
			udelay(10);
			mt_reg_sync_writel((clk_readl(ARMPLL1_CON1) | 0x80000000), ARMPLL1_CON1);
			mt_reg_sync_writel((clk_readl(ARMPLL1_CON0) | 0x01), ARMPLL1_CON0);
			udelay(100);
		} else {
			mt_reg_sync_writel((clk_readl(ARMPLL1_CON0) & 0xfffffffe), ARMPLL1_CON0);
			mt_reg_sync_writel((clk_readl(ARMPLL1_PWR_CON0) | 0x00000002), ARMPLL1_PWR_CON0);
			mt_reg_sync_writel((clk_readl(ARMPLL1_PWR_CON0) & 0xfffffffe), ARMPLL1_PWR_CON0);
		}
	} else if (id == 2) {
		if (on) {
			mt_reg_sync_writel((clk_readl(ARMPLL2_PWR_CON0) | 0x01), ARMPLL2_PWR_CON0);
			udelay(100);
			mt_reg_sync_writel((clk_readl(ARMPLL2_PWR_CON0) & 0xfffffffd), ARMPLL2_PWR_CON0);
			udelay(10);
			mt_reg_sync_writel((clk_readl(ARMPLL2_CON1) | 0x80000000), ARMPLL2_CON1);
			mt_reg_sync_writel((clk_readl(ARMPLL2_CON0) | 0x01), ARMPLL2_CON0);
			udelay(100);
		} else {
			mt_reg_sync_writel((clk_readl(ARMPLL2_CON0) & 0xfffffffe), ARMPLL2_CON0);
			mt_reg_sync_writel((clk_readl(ARMPLL2_PWR_CON0) | 0x00000002), ARMPLL2_PWR_CON0);
			mt_reg_sync_writel((clk_readl(ARMPLL2_PWR_CON0) & 0xfffffffe), ARMPLL2_PWR_CON0);
		}
	}
}

void check_ven_clk_sts(void)
{
	/* confirm ven clk */
	#if 0
	pr_err("[CCF] %s: CLK_CFG_1 = 0x%08x\r\n", __func__, clk_readl(CLK_CFG_1));
	pr_err("[CCF] %s: CLK_CFG_2 = 0x%08x\r\n", __func__, clk_readl(CLK_CFG_2));
	pr_err("[CCF] %s: VCODECPLL_CON1 = 0x%08x\r\n", __func__, clk_readl(UNIVPLL_CON0));
	pr_err("[CCF] %s: VCODECPLL_CON0 = 0x%08x\r\n", __func__, clk_readl(MMPLL_CON0));
	pr_err("[CCF] %s: VENC_CG_CON = 0x%08x\r\n", __func__, clk_readl(VENC_CG_CON));
	pr_err("[CCF] %s: axi = %dkhz\r\n", __func__, mt_get_ckgen_freq(1));
	pr_err("[CCF] %s: vcodecpll_d7 = %dkhz\r\n", __func__, mt_get_abist2_freq(22));
	pr_err("[CCF] %s: mmpll_d6 = %dkhz\r\n", __func__, mt_get_abist2_freq(19));
	pr_err("[CCF] %s: ven = %dkhz\r\n", __func__, mt_get_ckgen_freq(9));
	pr_err("[CCF] %s: vde = %dkhz\r\n", __func__, mt_get_ckgen_freq(8));
	#endif
}


#if 0
void check_smi_clk_sts(void)
{
	/* confirm mjc clk */
	pr_err("[CCF] %s: smi_clk = %dkhz\r\n", __func__, mt_get_ckgen_freq(24));
}
#endif

static int __init clk_mt6758_init(void)
{
	/*timer_ready = true;*/
	/*mtk_clk_enable_critical();*/

	return 0;
}
arch_initcall(clk_mt6758_init);

