/*
 * Copyright (C) 2016 MediaTek Inc.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2 as
 * published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See http://www.gnu.org/licenses/gpl-2.0.html for more details.
 */

#ifndef __MTK_IDLE_MT6758_H__
#define __MTK_IDLE_MT6758_H__
#include <linux/io.h>

enum {
	UNIV_PLL = 0,
	MSDC_PLL,
	MM_PLL,
	NR_PLLS,
};

enum {
	CG_INFRA_0  = 0,
	CG_INFRA_1,
	CG_INFRA_2,
	CG_PERI_0,
	CG_PERI_1,
	CG_PERI_2,
	CG_PERI_3,
	CG_PERI_4,
	CG_PERI_5,
	CG_AUDIO_0,
	CG_AUDIO_1,
	CG_DISP_0,
	CG_DISP_1,
	CG_DISP_2,
	CG_CAM,
	CG_IMAGE,
	CG_MFG,
	CG_VDEC_0,
	CG_VDEC_1,
	CG_VENC_0,
	CG_VENC_1,
	CG_MJC,
	CG_IPU,
	NR_GRPS,
};

#define NF_CG_STA_RECORD	(NR_GRPS + 2)

enum {
	PDN_MASK         = 0,
	PDN_VALUE,
	PUP_MASK,
	PUP_VALUE,
	NF_CLKMUX_MASK,
};

enum {
	/* CLK_CFG_0 */
	CLKMUX_MM             = 0,
	CLKMUX_DDRPHY         = 1,
	CLKMUX_MEM            = 2,
	CLKMUX_AXI            = 3,

	/* CLK_CFG_1 */
	CLKMUX_VDEC           = 4,
	CLKMUX_DISPPWM        = 5,
	CLKMUX_PWM            = 6,
	CLKMUX_DPI0           = 7,

	/* CLK_CFG_2 */
	CLKMUX_CAMTG2         = 8,
	CLKMUX_CAMTG          = 9,
	CLKMUX_MFG            = 10,
	CLKMUX_VENC           = 11,

	/* CLK_CFG_3 */
	CLKMUX_UART           = 12,
	CLKMUX_I2C            = 13,
	CLKMUX_CAMTG4         = 14,
	CLKMUX_CAMTG3         = 15,

	/* CLK_CFG_4 */
	CLKMUX_MSDC50_0       = 16,
	CLKMUX_MSDC50_0_HCLK  = 17,
	CLKMUX_USB30_P0       = 18,
	CLKMUX_SPI            = 19,

	/* CLK_CFG_5 */
	CLKMUX_MSDC50_3_HCLK  = 20,
	CLKMUX_MSDC30_3       = 21,
	CLKMUX_I3C            = 22,
	CLKMUX_MSDC30_1       = 23,

	/* CLK_CFG_6 */
	CLKMUX_SCP            = 24,
	CLKMUX_PMICSPI        = 25,
	CLKMUX_AUD_INTBUS     = 26,
	CLKMUX_AUDIO          = 27,

	/* CLK_CFG_7 */
	CLKMUX_AUD_2          = 28,
	CLKMUX_AUD_1          = 29,
	CLKMUX_DSP            = 30,
	CLKMUX_ATB            = 31,

	/* CLK_CFG_8 */
	CLKMUX_CAM            = 32,
	CLKMUX_DFP_MFG        = 33,
	CLKMUX_AUD_ENGEN2     = 34,
	CLKMUX_AUD_ENGEN1     = 35,

	/* CLK_CFG_9 */
	CLKMUX_AUDIO_H        = 36,
	CLKMUX_AES_UFSFD      = 37,
	CLKMUX_IMG            = 38,
	CLKMUX_IPU_IF         = 39,

	/* CLK_CFG_10 */
	CLKMUX_DXCC           = 40,
	CLKMUX_BSI_SPI        = 41,
	CLKMUX_UFS_CARD       = 42,
	CLKMUX_PWRMCU         = 43,

	/* CLK_CFG_11 */
	CLKMUX_RSV_0          = 44,
	CLKMUX_RSV_1          = 45,
	CLKMUX_DFP            = 46,
	CLKMUX_SENIF          = 47,

	NF_CLKMUX,
};

#define NF_CLK_CFG            (NF_CLKMUX/4)

extern bool             soidle_by_pass_pg;
extern bool             mcsodi_by_pass_pg;
extern bool             dpidle_by_pass_pg;

extern void __iomem *infrasys_base;
extern void __iomem *perisys_base;
extern void __iomem *audiosys_base_in_idle;
extern void __iomem *mmsys_base;
extern void __iomem *camsys_base;
extern void __iomem *imgsys_base;
extern void __iomem *mfgsys_base;
extern void __iomem *vdecsys_base;
extern void __iomem *vencsys_global_base;
extern void __iomem *vencsys_base;
extern void __iomem *ipusys_base;
extern void __iomem *topcksys_base;

extern void __iomem *sleepsys_base;
extern void __iomem *apmixed_base_in_idle;
extern void __iomem *timer_base_in_idle;

#define INFRA_REG(ofs)        (infrasys_base + ofs)
#define PERI_REG(ofs)         (perisys_base + ofs)
#define AUDIOSYS_REG(ofs)     (audiosys_base_in_idle + ofs)
#define MM_REG(ofs)           (mmsys_base + ofs)
#define CAMSYS_REG(ofs)       (camsys_base + ofs)
#define IMGSYS_REG(ofs)       (imgsys_base + ofs)
#define MFGSYS_REG(ofs)       (mfgsys_base + ofs)
#define VDECSYS_REG(ofs)      (vdecsys_base + ofs)
#define VENC_GLOBAL_REG(ofs)  (vencsys_global_base + ofs)
#define VENCSYS_REG(ofs)      (vencsys_base + ofs)
#define IPUSYS_REG(ofs)       (ipusys_base + ofs)

#define SPM_REG(ofs)          (sleepsys_base + ofs)
#define TOPCKSYS_REG(ofs)     (topcksys_base + ofs)
#define APMIXEDSYS(ofs)	      (apmixed_base_in_idle + ofs)
#define GPT_REG(ofs)          (timer_base_in_idle + ofs)

#ifdef SPM_PWR_STATUS
#undef SPM_PWR_STATUS
#endif

#ifdef SPM_PWR_STATUS_2ND
#undef SPM_PWR_STATUS_2ND
#endif

#define	INFRA_SW_CG_0_STA     INFRA_REG(0x0094)
#define	INFRA_SW_CG_1_STA     INFRA_REG(0x0090)
#define	INFRA_SW_CG_2_STA     INFRA_REG(0x10B8)
#define PERI_SW_CG_0_STA      PERI_REG(0x0278)
#define PERI_SW_CG_1_STA      PERI_REG(0x0288)
#define PERI_SW_CG_2_STA      PERI_REG(0x0298)
#define PERI_SW_CG_3_STA      PERI_REG(0x02A8)
#define PERI_SW_CG_4_STA      PERI_REG(0x02B8)
#define PERI_SW_CG_5_STA      PERI_REG(0x02C8)
#define AUDIO_TOP_CON_0       AUDIOSYS_REG(0x0)
#define AUDIO_TOP_CON_1       AUDIOSYS_REG(0x4)
#define DISP_CG_CON_0         MM_REG(0x100)
#define DISP_CG_CON_1         MM_REG(0x110)
#define DISP_CG_CON_2         MM_REG(0x140)
#define CAMSYS_CG_CON         CAMSYS_REG(0x0)
#define IMG_CG_CON            IMGSYS_REG(0x0)
#define MFG_CG_CON            MFGSYS_REG(0x0)
#define VDEC_CKEN_SET         VDECSYS_REG(0x0)
#define VDEC_LARB1_CKEN_SET   VDECSYS_REG(0x8)
#define VENC_CE               VENC_GLOBAL_REG(0xEC)
#define VENCSYS_CG_CON        VENCSYS_REG(0x0)
#define IPU_CG_CON            IPUSYS_REG(0x0)
#define FREERUN_COUNTER       GPT_REG(0x28)

#define SPM_PWR_STATUS      SPM_REG(0x0190)
#define SPM_PWR_STATUS_2ND  SPM_REG(0x0194)

#define CLK_CFG_0_BASE        TOPCKSYS_REG(0x100)
#define CLK_CFG_0_SET_BASE    TOPCKSYS_REG(0x104)
#define CLK_CFG_0_CLR_BASE    TOPCKSYS_REG(0x108)
#define CLK_CFG(n)            (CLK_CFG_0_BASE + n * 0x10)
#define CLK_CFG_SET(n)        (CLK_CFG_0_SET_BASE + n * 0x10)
#define CLK_CFG_CLR(n)        (CLK_CFG_0_CLR_BASE + n * 0x10)

#define CLK6_AUDINTBUS_MASK   0x700 /* FIXME: Need to confirm */
#define CLK13_UFS_CARD_SEL_MASK	0x3 /* FIXME: Need to confirm */

#define GPUPLL_CON0			APMIXEDSYS(0x0210)
#define MPLL_CON0			APMIXEDSYS(0x0220)
#define MAINPLL_CON0		APMIXEDSYS(0x0230)
#define UNIVPLL_CON0		APMIXEDSYS(0x0240)
#define MSDCPLL_CON0		APMIXEDSYS(0x0250)
#define MMPLL_CON0			APMIXEDSYS(0x0260)
#define EMIPLL_CON0			APMIXEDSYS(0x0290)
#define APLL1_CON0			APMIXEDSYS(0x02a0)
#define APLL2_CON0			APMIXEDSYS(0x02c0)
#define AUDPLL_CON0			APMIXEDSYS(0x02dc)
#define ARMPLL1_CON0		APMIXEDSYS(0x0310)
#define ARMPLL2_CON0		APMIXEDSYS(0x0320)
#define ARMPLL3_CON0		APMIXEDSYS(0x0330)

#define SC_MFG_ASYNC_PWR_ACK    BIT(1)
#define SC_MFG_TOP_PWR_ACK      BIT(2)
#define SC_MFG_SHADER0_PWR_ACK  BIT(3)
#define SC_MFG_SHADER1_PWR_ACK  BIT(4)
#define SC_MFG_SHADER2_PWR_ACK  BIT(5)
#define SC_INFRA_PWR_ACK        BIT(11)
#define SC_AUD_PWR_ACK          BIT(12)
#define SC_MJC_PWR_ACK          BIT(13)
#define SC_MM0_PWR_ACK          BIT(14)
#define SC_CAM_PWR_ACK          BIT(15)
#define SC_IPU_PWR_ACK          BIT(16)
#define SC_ISP_PWR_ACK          BIT(17)
#define SC_VEN_PWR_ACK          BIT(18)
#define SC_VDE_PWR_ACK          BIT(19)

extern void usb_audio_req(bool on);
extern u32 slp_spm_deepidle_flags;
extern u32 slp_spm_SODI_flags;
#endif /* __MTK_IDLE_MT6758_H__ */

