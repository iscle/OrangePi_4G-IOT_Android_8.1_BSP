/*
 * Copyright (c) 2014 MediaTek Inc.
 * Author: James Liao <jamesjj.liao@mediatek.com>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2 as
 * published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty off
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 */

#include <linux/of.h>
#include <linux/of_address.h>

#include <linux/io.h>
#include <linux/slab.h>
#include <linux/delay.h>
#include <linux/clkdev.h>
#include <linux/clk-provider.h>
#include <linux/clk.h>
#include <linux/delay.h>

#include "clk-mtk-v1.h"
#include "clk-mt6775-pg.h"

#include <dt-bindings/clock/mt6775-clk.h>

/*#define TOPAXI_PROTECT_LOCK*/
#ifdef CONFIG_FPGA_EARLY_PORTING
#define IGNORE_MTCMOS_CHECK
#endif
#if !defined(MT_CCF_DEBUG) || !defined(MT_CCF_BRINGUP)
#define MT_CCF_DEBUG	0
#define MT_CCF_BRINGUP	0
#define CONTROL_LIMIT	1
#endif

#define	CHECK_PWR_ST	1

#ifndef GENMASK
#define GENMASK(h, l)	(((U32_C(1) << ((h) - (l) + 1)) - 1) << (l))
#endif

#ifdef CONFIG_ARM64
#define IOMEM(a)	((void __force __iomem *)((a)))
#endif

#if 0
#define clk_readl(addr)		readl(addr)
#define clk_writel(val, addr)	\
	do { writel(val, addr); wmb(); } while (0)	/* sync_write */
#define clk_setl(mask, addr)	clk_writel(clk_readl(addr) | (mask), addr)
#define clk_clrl(mask, addr)	clk_writel(clk_readl(addr) & ~(mask), addr)
#endif

#define mt_reg_sync_writel(v, a) \
	do { \
		__raw_writel((v), IOMEM(a)); \
		/* sync up */ \
		mb(); } \
while (0)
#define spm_read(addr)			__raw_readl(IOMEM(addr))
#define spm_write(addr, val)		mt_reg_sync_writel(val, addr)

#define clk_writel(addr, val)   \
	mt_reg_sync_writel(val, addr)

#define clk_readl(addr)			__raw_readl(IOMEM(addr))

/*MM Bus*/
#ifdef CONFIG_OF
/*void __iomem *clk_mfgcfg_base;*/
void __iomem *clk_mmsys_config_base;
void __iomem *clk_imgsys_base;
void __iomem *clk_vdec_gcon_base;
void __iomem *clk_venc_gcon_base;
void __iomem *clk_camsys_base;

void __iomem *clk_ipusys_vcore_base;
void __iomem *clk_ipusys_conn_base;
void __iomem *clk_ipusys_core0_base;
void __iomem *clk_ipusys_core1_base;
void __iomem *clk_ipusys_core2_base;
#endif

/*#define MFG_CG_CLR (clk_mfgcfg_base + 0x108)*/
#define MM_CG_0			(clk_mmsys_config_base + 0x100)
#define MM_CG_CLR0		(clk_mmsys_config_base + 0x108)
#define MM_CG_SET0		(clk_mmsys_config_base + 0x104)
#define MM_CG_1			(clk_mmsys_config_base + 0x110)
#define MM_CG_CLR1		(clk_mmsys_config_base + 0x118)
#define MMSYS_HW_DCM_1ST_DIS0	(clk_mmsys_config_base + 0x120)
#define MMSYS_HW_DCM_1ST_DIS_SET0	(clk_mmsys_config_base + 0x124)
#define MMSYS_HW_DCM_1ST_DIS_CLR0	(clk_mmsys_config_base + 0x128)

#define IMG_CG			(clk_imgsys_base + 0x0000)
#define IMG_CG_CLR		(clk_imgsys_base + 0x0008)
#define VDEC_CKEN_SET		(clk_vdec_gcon_base + 0x0000)
#define VDEC_LARB1_SET		(clk_vdec_gcon_base + 0x0008)
#define VDEC_GALS_CFG		(clk_vdec_gcon_base + 0x0168)
#define VENC_CG			(clk_venc_gcon_base + 0x0000)
#define VENC_CG_SET		(clk_venc_gcon_base + 0x0004)
#define CAMSYS_CG		(clk_camsys_base + 0x0000)
#define CAMSYS_CG_CLR		(clk_camsys_base + 0x0008)

#define IPU_VCORE_CG_CON	(clk_ipusys_vcore_base + 0x0000)
#define IPU_VCORE_CG_SET	(clk_ipusys_vcore_base + 0x0004)
#define IPU_VCORE_CG_CLR	(clk_ipusys_vcore_base + 0x0008)

#define IPU_CONN_CG_CON		(clk_ipusys_conn_base + 0x0000)
#define IPU_CONN_CG_SET		(clk_ipusys_conn_base + 0x0004)
#define IPU_CONN_CG_CLR		(clk_ipusys_conn_base + 0x0008)
#define IPU_CONN_APB_CONT	(clk_ipusys_conn_base + 0x0010)
#define IPU_CONN_AXI_CONT	(clk_ipusys_conn_base + 0x0018)
#define IPU_CONN_AXI_CONT1	(clk_ipusys_conn_base + 0x001c)
#define IPU_CONN_AXI_CONT2	(clk_ipusys_conn_base + 0x0020)

#define IPU_CORE0_CG_CON	(clk_ipusys_core0_base + 0x0000)
#define IPU_CORE0_CG_SET	(clk_ipusys_core0_base + 0x0004)
#define IPU_CORE0_CG_CLR	(clk_ipusys_core0_base + 0x0008)

#define IPU_CORE1_CG_CON	(clk_ipusys_core1_base + 0x0000)
#define IPU_CORE1_CG_SET	(clk_ipusys_core1_base + 0x0004)
#define IPU_CORE1_CG_CLR	(clk_ipusys_core1_base + 0x0008)

#define IPU_CORE2_CG_CON	(clk_ipusys_core2_base + 0x0000)
#define IPU_CORE2_CG_SET	(clk_ipusys_core2_base + 0x0004)
#define IPU_CORE2_CG_CLR	(clk_ipusys_core2_base + 0x0008)



/*
 * MTCMOS
 */

#define STA_POWER_DOWN	0
#define STA_POWER_ON	1
#define SUBSYS_PWR_DOWN		0
#define SUBSYS_PWR_ON		1

struct subsys;

struct subsys_ops {
	int (*enable)(struct subsys *sys);
	int (*disable)(struct subsys *sys);
	int (*get_state)(struct subsys *sys);
};

struct subsys {
	const char *name;
	uint32_t sta_mask;
	void __iomem *ctl_addr;
	uint32_t sram_pdn_bits;
	uint32_t sram_pdn_ack_bits;
	uint32_t bus_prot_mask;
	struct subsys_ops *ops;
};

/*static struct subsys_ops general_sys_ops;*/
static struct subsys_ops MFG0_sys_ops;
static struct subsys_ops MFG1_sys_ops;
static struct subsys_ops MFG2_sys_ops;
static struct subsys_ops MFG3_sys_ops;
static struct subsys_ops MFG4_sys_ops;
static struct subsys_ops MFG5_sys_ops;
static struct subsys_ops C2K_sys_ops;
static struct subsys_ops MD1_sys_ops;
static struct subsys_ops CONN_sys_ops;
static struct subsys_ops AUD_sys_ops;
static struct subsys_ops MM0_sys_ops;
static struct subsys_ops CAM_sys_ops;
static struct subsys_ops ISP_sys_ops;
static struct subsys_ops VEN_sys_ops;
static struct subsys_ops VDE_sys_ops;
static struct subsys_ops IPU_vcore_shutdown_sys_ops;
static struct subsys_ops IPU_shutdown_sys_ops;
static struct subsys_ops IPU_core0_shutdown_sys_ops;
static struct subsys_ops IPU_core0_sleep_sys_ops;
static struct subsys_ops IPU_core1_shutdown_sys_ops;
static struct subsys_ops IPU_core1_sleep_sys_ops;
static struct subsys_ops IPU_core2_shutdown_sys_ops;
static struct subsys_ops IPU_core2_sleep_sys_ops;



static void __iomem *infracfg_base;	/*infracfg_ao*/
static void __iomem *spm_base;
static void __iomem *smi_common_base;
static void __iomem *smi_common_ext_base;
static void __iomem *infra_base;	/*infra*/
static void __iomem *ipu_conn_base;	/* ipu_conn */


#define INFRACFG_REG(offset)		(infracfg_base + offset)
#define SPM_REG(offset)				(spm_base + offset)
#define SMI_COMMON_REG(offset)		(smi_common_base + offset)
#define SMI_COMMON_EXT_REG(offset)	(smi_common_ext_base + offset)
#define INFRA_REG(offset)			(infra_base + offset)
#define IPU_CONN_REG(offset)		(ipu_conn_base + offset)

#define  SPM_PROJECT_CODE    0xB16

#define PWR_RST_B                        (0x1 << 0)
#define PWR_ISO                          (0x1 << 1)
#define PWR_ON                           (0x1 << 2)
#define PWR_ON_2ND                       (0x1 << 3)
#define PWR_CLK_DIS                      (0x1 << 4)
#define SRAM_CKISO                       (0x1 << 5)
#define SRAM_ISOINT_B                    (0x1 << 6)
#define SLPB_CLAMP                       (0x1 << 7)


#define POWERON_CONFIG_EN	SPM_REG(0x0000)
#define SPM_POWER_ON_VAL0	SPM_REG(0x0004)
#define SPM_POWER_ON_VAL1	SPM_REG(0x0008)
#define PWR_STATUS			SPM_REG(0x0190)
#define PWR_STATUS_2ND		SPM_REG(0x0194)
#define MFG0_PWR_CON		SPM_REG(0x0300)
#define MFG1_PWR_CON		SPM_REG(0x0304)
#define MFG2_PWR_CON		SPM_REG(0x0308)
#define MFG3_PWR_CON		SPM_REG(0x030C)
#define MFG4_PWR_CON		SPM_REG(0x0310)
#define C2K_PWR_CON			SPM_REG(0x0314)
#define MD1_PWR_CON			SPM_REG(0x0318)
#define CONN_PWR_CON		SPM_REG(0x0324)
#define AUD_PWR_CON			SPM_REG(0x032C)
#define IPU_VCORE_PWR_CON	SPM_REG(0x0330)
#define MM0_PWR_CON			SPM_REG(0x0334)
#define CAM_PWR_CON			SPM_REG(0x0338)
#define IPU_PWR_CON			SPM_REG(0x033C)
#define ISP_PWR_CON			SPM_REG(0x0340)
#define VEN_PWR_CON			SPM_REG(0x0344)
#define VDE_PWR_CON			SPM_REG(0x0348)
#define IPU_CORE0_PWR_CON	SPM_REG(0x034C)
#define IPU_CORE1_PWR_CON	SPM_REG(0x0350)
#define IPU_CORE2_PWR_CON	SPM_REG(0x0354)
#define MFG5_PWR_CON        SPM_REG(0x0358)
#define EXT_BUCK_ISO		SPM_REG(0x0394)
#define IPU_SRAM_CON		SPM_REG(0x03A0)





#define INFRA_TOPAXI_SI0_CTL		INFRACFG_REG(0x0200)
#define INFRA_TOPAXI_PROTECTSTA0	INFRACFG_REG(0x0224)
#define INFRASYS_QAXI_CTRL		INFRACFG_REG(0x0F28)

#define INFRA_TOPAXI_PROTECTEN_SET	INFRACFG_REG(0x02A0)
#define INFRA_TOPAXI_PROTECTSTA1	INFRACFG_REG(0x0228)
#define INFRA_TOPAXI_PROTECTEN_CLR	INFRACFG_REG(0x02A4)

#define INFRA_TOPAXI_PROTECTEN_1_SET	INFRACFG_REG(0x02A8)
#define INFRA_TOPAXI_PROTECTSTA1_1	INFRACFG_REG(0x0258)
#define INFRA_TOPAXI_PROTECTEN_1_CLR	INFRACFG_REG(0x02AC)

/*#define INFRA_TOPAXI_PROTECTEN_2_CON	INFRACFG_REG(0x02C4)*/
#define INFRA_TOPAXI_PROTECTEN_2_SET	INFRACFG_REG(0x02C8)
#define INFRA_TOPAXI_PROTECTSTA1_2	INFRACFG_REG(0x02D4)
#define INFRA_TOPAXI_PROTECTEN_2_CLR	INFRACFG_REG(0x02CC)

#define INFRA_TOPAXI_SI0_STA		INFRA_REG(0x0000)

#define SMI_DCM				SMI_COMMON_REG(0x0300)
#define SMI_CLAMP			SMI_COMMON_REG(0x03C0)
#define SMI_CLAMP_SET			SMI_COMMON_REG(0x03C4)
#define SMI_CLAMP_CLR			SMI_COMMON_REG(0x03C8)

#define MMSYS_SMI_2X1_SUB_CLAMP		SMI_COMMON_EXT_REG(0x03C0)
#define MMSYS_SMI_2X1_SUB_CLAMP_SET	SMI_COMMON_EXT_REG(0x03C4)
#define MMSYS_SMI_2X1_SUB_CLAMP_CLR	SMI_COMMON_EXT_REG(0x03C8)


#define IPU_CONN_AXI_CTRL1				IPU_CONN_REG(0x01c)
#define IPU_CONN_AXI_CTRL3				IPU_CONN_REG(0x02c)

/* Define MTCMOS Power Status Mask */
#define MFG0_PWR_STA_MASK                (0x1 << 1)
#define MFG1_PWR_STA_MASK                (0x1 << 2)
#define MFG2_PWR_STA_MASK                (0x1 << 3)
#define MFG3_PWR_STA_MASK                (0x1 << 4)
#define MFG4_PWR_STA_MASK                (0x1 << 5)
#define C2K_PWR_STA_MASK                 (0x1 << 6)
#define MD1_PWR_STA_MASK                 (0x1 << 7)
#define CONN_PWR_STA_MASK                (0x1 << 10)
#define INFRA_PWR_STA_MASK               (0x1 << 11)
#define AUD_PWR_STA_MASK                 (0x1 << 12)
#define MM0_PWR_STA_MASK                 (0x1 << 14)
#define CAM_PWR_STA_MASK                 (0x1 << 15)
#define IPU_PWR_STA_MASK                 (0x1 << 16)
#define ISP_PWR_STA_MASK                 (0x1 << 17)
#define VEN_PWR_STA_MASK                 (0x1 << 18)
#define VDE_PWR_STA_MASK                 (0x1 << 19)
#define IPU_CORE0_PWR_STA_MASK           (0x1 << 20)
#define IPU_CORE1_PWR_STA_MASK           (0x1 << 21)
#define IPU_CORE2_PWR_STA_MASK           (0x1 << 22)
#define MFG5_PWR_STA_MASK                (0x1 << 23)
#define IPU_VCORE_PWR_ACK_STA_MASK       (0x1 << 28)
#define IPU_VCORE_PWR_ACK_2ND_STA_MASK   (0x1 << 29)

/* Define Non-CPU SRAM Mask */
#define MFG1_SRAM_PDN                    (0xF << 8)
#define MFG1_SRAM_PDN_ACK                (0x1 << 24)
#define MFG1_SRAM_PDN_ACK_BIT0           (0x1 << 24)
#define MFG1_SRAM_PDN_ACK_BIT1           (0x1 << 25)
#define MFG1_SRAM_PDN_ACK_BIT2           (0x1 << 26)
#define MFG1_SRAM_PDN_ACK_BIT3           (0x1 << 27)
#define MFG2_SRAM_PDN                    (0xF << 8)
#define MFG2_SRAM_PDN_ACK                (0x1 << 24)
#define MFG2_SRAM_PDN_ACK_BIT0           (0x1 << 24)
#define MFG2_SRAM_PDN_ACK_BIT1           (0x1 << 25)
#define MFG2_SRAM_PDN_ACK_BIT2           (0x1 << 26)
#define MFG2_SRAM_PDN_ACK_BIT3           (0x1 << 27)
#define MFG3_SRAM_PDN                    (0xF << 8)
#define MFG3_SRAM_PDN_ACK                (0x1 << 24)
#define MFG3_SRAM_PDN_ACK_BIT0           (0x1 << 24)
#define MFG3_SRAM_PDN_ACK_BIT1           (0x1 << 25)
#define MFG3_SRAM_PDN_ACK_BIT2           (0x1 << 26)
#define MFG3_SRAM_PDN_ACK_BIT3           (0x1 << 27)
#define MFG4_SRAM_PDN                    (0xF << 8)
#define MFG4_SRAM_PDN_ACK                (0x1 << 24)
#define MFG4_SRAM_PDN_ACK_BIT0           (0x1 << 24)
#define MFG4_SRAM_PDN_ACK_BIT1           (0x1 << 25)
#define MFG4_SRAM_PDN_ACK_BIT2           (0x1 << 26)
#define MFG4_SRAM_PDN_ACK_BIT3           (0x1 << 27)
#define MD1_SRAM_PDN                     (0x7 << 8)
#define MD1_SRAM_PDN_ACK                 (0x0 << 24)
#define MD1_SRAM_PDN_ACK_BIT0            (0x1 << 24)
#define MD1_SRAM_PDN_ACK_BIT1            (0x1 << 25)
#define MD1_SRAM_PDN_ACK_BIT2            (0x1 << 26)
#define AUD_SRAM_PDN                     (0xF << 8)
#define AUD_SRAM_PDN_ACK                 (0xF << 24)
#define AUD_SRAM_PDN_ACK_BIT0            (0x1 << 24)
#define AUD_SRAM_PDN_ACK_BIT1            (0x1 << 25)
#define AUD_SRAM_PDN_ACK_BIT2            (0x1 << 26)
#define AUD_SRAM_PDN_ACK_BIT3            (0x1 << 27)
#define MM0_SRAM_PDN                     (0x1 << 8)
#define MM0_SRAM_PDN_ACK                 (0x1 << 24)
#define MM0_SRAM_PDN_ACK_BIT0            (0x1 << 24)
#define CAM_SRAM_PDN                     (0x3 << 8)
#define CAM_SRAM_PDN_ACK                 (0x3 << 24)
#define CAM_SRAM_PDN_ACK_BIT0            (0x1 << 24)
#define CAM_SRAM_PDN_ACK_BIT1            (0x1 << 25)
#define IPU_SRAM_PDN                     (0x1 << 8)
#define IPU_SRAM_PDN_ACK                 (0x1 << 24)
#define IPU_SRAM_PDN_ACK_BIT0            (0x1 << 24)
#define ISP_SRAM_PDN                     (0x3 << 8)
#define ISP_SRAM_PDN_ACK                 (0x3 << 24)
#define ISP_SRAM_PDN_ACK_BIT0            (0x1 << 24)
#define ISP_SRAM_PDN_ACK_BIT1            (0x1 << 25)
#define VEN_SRAM_PDN                     (0xF << 8)
#define VEN_SRAM_PDN_ACK                 (0xF << 24)
#define VEN_SRAM_PDN_ACK_BIT0            (0x1 << 24)
#define VEN_SRAM_PDN_ACK_BIT1            (0x1 << 25)
#define VEN_SRAM_PDN_ACK_BIT2            (0x1 << 26)
#define VEN_SRAM_PDN_ACK_BIT3            (0x1 << 27)
#define VDE_SRAM_PDN                     (0x1 << 8)
#define VDE_SRAM_PDN_ACK                 (0x1 << 24)
#define VDE_SRAM_PDN_ACK_BIT0            (0x1 << 24)
#define IPU_CORE0_SRAM_PDN               (0xF << 8)
#define IPU_CORE0_SRAM_PDN_BIT0          (0x1 << 8)
#define IPU_CORE0_SRAM_PDN_BIT1          (0x1 << 9)
#define IPU_CORE0_SRAM_PDN_BIT2          (0x1 << 10)
#define IPU_CORE0_SRAM_PDN_BIT3          (0x1 << 11)
#define IPU_CORE0_SRAM_PDN_ACK           (0x3 << 24)
#define IPU_CORE0_SRAM_PDN_ACK_BIT0      (0x1 << 24)
#define IPU_CORE0_SRAM_PDN_ACK_BIT1      (0x1 << 25)
#define IPU_CORE1_SRAM_PDN               (0xF << 8)
#define IPU_CORE1_SRAM_PDN_BIT0          (0x1 << 8)
#define IPU_CORE1_SRAM_PDN_BIT1          (0x1 << 9)
#define IPU_CORE1_SRAM_PDN_BIT2          (0x1 << 10)
#define IPU_CORE1_SRAM_PDN_BIT3          (0x1 << 11)
#define IPU_CORE1_SRAM_PDN_ACK           (0x3 << 24)
#define IPU_CORE1_SRAM_PDN_ACK_BIT0      (0x1 << 24)
#define IPU_CORE1_SRAM_PDN_ACK_BIT1      (0x1 << 25)
#define IPU_CORE2_SRAM_PDN               (0xF << 8)
#define IPU_CORE2_SRAM_PDN_BIT0          (0x1 << 8)
#define IPU_CORE2_SRAM_PDN_BIT1          (0x1 << 9)
#define IPU_CORE2_SRAM_PDN_BIT2          (0x1 << 10)
#define IPU_CORE2_SRAM_PDN_BIT3          (0x1 << 11)
#define IPU_CORE2_SRAM_PDN_ACK           (0x3 << 24)
#define IPU_CORE2_SRAM_PDN_ACK_BIT0      (0x1 << 24)
#define IPU_CORE2_SRAM_PDN_ACK_BIT1      (0x1 << 25)
#define MFG5_SRAM_PDN                    (0xF << 8)
#define MFG5_SRAM_PDN_ACK                (0x1 << 24)
#define MFG5_SRAM_PDN_ACK_BIT0           (0x1 << 24)
#define MFG5_SRAM_PDN_ACK_BIT1           (0x1 << 25)
#define MFG5_SRAM_PDN_ACK_BIT2           (0x1 << 26)
#define MFG5_SRAM_PDN_ACK_BIT3           (0x1 << 27)


static struct subsys syss[] =	/* NR_SYSS *//* FIXME: set correct value */
{
	[SYS_MFG0] = {
		     .name = __stringify(SYS_MFG0),
		     .sta_mask = MFG0_PWR_STA_MASK,
		     /* .ctl_addr = NULL,  */
		     .sram_pdn_bits = 0,
		     .sram_pdn_ack_bits = 0,
		     .bus_prot_mask = 0,
		     .ops = &MFG0_sys_ops,
		     },
	[SYS_MFG1] = {
		     .name = __stringify(SYS_MFG1),
		     .sta_mask = MFG1_PWR_STA_MASK,
		     /* .ctl_addr = NULL,  */
		     .sram_pdn_bits = 0,
		     .sram_pdn_ack_bits = 0,
		     .bus_prot_mask = 0,
		     .ops = &MFG1_sys_ops,
		     },
	[SYS_MFG2] = {
		     .name = __stringify(SYS_MFG2),
		     .sta_mask = MFG2_PWR_STA_MASK,
		     /* .ctl_addr = NULL,  */
		     .sram_pdn_bits = 0,
		     .sram_pdn_ack_bits = 0,
		     .bus_prot_mask = 0,
		     .ops = &MFG2_sys_ops,
		     },
	[SYS_MFG3] = {
		     .name = __stringify(SYS_MFG3),
		     .sta_mask = MFG3_PWR_STA_MASK,
		     /* .ctl_addr = NULL,  */
		     .sram_pdn_bits = 0,
		     .sram_pdn_ack_bits = 0,
		     .bus_prot_mask = 0,
		     .ops = &MFG3_sys_ops,
		     },
	[SYS_MFG4] = {
		     .name = __stringify(SYS_MFG4),
		     .sta_mask = MFG4_PWR_STA_MASK,
		     /* .ctl_addr = NULL,  */
		     .sram_pdn_bits = 0,
		     .sram_pdn_ack_bits = 0,
		     .bus_prot_mask = 0,
		     .ops = &MFG4_sys_ops,
		     },
	[SYS_MFG5] = {
		     .name = __stringify(SYS_MFG5),
		     .sta_mask = MFG5_PWR_STA_MASK,
		     /* .ctl_addr = NULL,  */
		     .sram_pdn_bits = 0,
		     .sram_pdn_ack_bits = 0,
		     .bus_prot_mask = 0,
		     .ops = &MFG5_sys_ops,
		     },
	 [SYS_C2K] = {
		     .name = __stringify(SYS_C2K),
		     .sta_mask = C2K_PWR_STA_MASK,
		     /*.ctl_addr = NULL, SPM_C2K_PWR_CON, */
		     .sram_pdn_bits = 0,
		     .sram_pdn_ack_bits = 0,
		     .bus_prot_mask = 0,
		     .ops = &C2K_sys_ops,
		     },
	[SYS_MD1] = {
			.name = __stringify(SYS_MD1),
			.sta_mask = MD1_PWR_STA_MASK,
			/* .ctl_addr = NULL,  */
			.sram_pdn_bits = MD1_SRAM_PDN,
			.sram_pdn_ack_bits = 0,
			.bus_prot_mask = 0,
			.ops = &MD1_sys_ops,
			},
	[SYS_CONN] = {
			.name = __stringify(SYS_CONN),
			.sta_mask = CONN_PWR_STA_MASK,
			/* .ctl_addr = NULL,  */
			.sram_pdn_bits = 0,
			.sram_pdn_ack_bits = 0,
			.bus_prot_mask = 0,
			.ops = &CONN_sys_ops,
			},
	[SYS_AUD] = {
		       .name = __stringify(SYS_AUD),
		       .sta_mask = AUD_PWR_STA_MASK,
		     /* .ctl_addr = NULL,  */
		       .sram_pdn_bits = 0,
		       .sram_pdn_ack_bits = 0,
		       .bus_prot_mask = 0,
		       .ops = &AUD_sys_ops,
		       },
	[SYS_MM0] = {
			.name = __stringify(SYS_MM0),
			.sta_mask = MM0_PWR_STA_MASK,
			/* .ctl_addr = NULL,  */
			.sram_pdn_bits = 0,
			.sram_pdn_ack_bits = 0,
			.bus_prot_mask = 0,
			.ops = &MM0_sys_ops,
			},
	[SYS_CAM] = {
		       .name = __stringify(SYS_CAM),
		       .sta_mask = CAM_PWR_STA_MASK,
		     /* .ctl_addr = NULL,  */
		       .sram_pdn_bits = 0,
		       .sram_pdn_ack_bits = 0,
		       .bus_prot_mask = 0,
		       .ops = &CAM_sys_ops,
		       },
	[SYS_ISP] = {
		     .name = __stringify(SYS_ISP),
		     .sta_mask = ISP_PWR_STA_MASK,
		     /* .ctl_addr = NULL,  */
		     .sram_pdn_bits = 0,
		     .sram_pdn_ack_bits = 0,
		     .bus_prot_mask = 0,
		     .ops = &ISP_sys_ops,
		     },
	[SYS_VEN] = {
		     .name = __stringify(SYS_VEN),
		     .sta_mask = VEN_PWR_STA_MASK,
		     /* .ctl_addr = NULL,  */
		     .sram_pdn_bits = 0,
		     .sram_pdn_ack_bits = 0,
		     .bus_prot_mask = 0,
		     .ops = &VEN_sys_ops,
		     },
	[SYS_VDE] = {
		     .name = __stringify(SYS_VDE),
		     .sta_mask = VDE_PWR_STA_MASK,
		     /* .ctl_addr = NULL,  */
		     .sram_pdn_bits = 0,
		     .sram_pdn_ack_bits = 0,
		     .bus_prot_mask = 0,
		     .ops = &VDE_sys_ops,
		     },

	[SYS_IPU_VCORE_SHUTDOWN] = {
			   .name = __stringify(SYS_IPU_VCORE_SHUTDOWN),
			   .sta_mask = IPU_VCORE_PWR_ACK_STA_MASK |
				IPU_VCORE_PWR_ACK_2ND_STA_MASK	/* todo : check again */,
		     /* .ctl_addr = NULL,  */
			   .sram_pdn_bits = 0,
			   .sram_pdn_ack_bits = 0,
			   .bus_prot_mask = 0,
			   .ops = &IPU_vcore_shutdown_sys_ops,
			   },
	[SYS_IPU_SHUTDOWN] = {
			   .name = __stringify(SYS_IPU_SHUTDOWN),
			   .sta_mask = IPU_PWR_STA_MASK,
		     /* .ctl_addr = NULL,  */
			   .sram_pdn_bits = 0,
			   .sram_pdn_ack_bits = 0,
			   .bus_prot_mask = 0,
			   .ops = &IPU_shutdown_sys_ops,
			   },

	[SYS_IPU_CORE0_SHUTDOWN] = {
			   .name = __stringify(SYS_IPU_CORE0_SHUTDOWN),
			   .sta_mask = IPU_CORE0_PWR_STA_MASK,
		     /* .ctl_addr = NULL,  */
			   .sram_pdn_bits = 0,
			   .sram_pdn_ack_bits = 0,
			   .bus_prot_mask = 0,
			   .ops = &IPU_core0_shutdown_sys_ops,
			   },

	[SYS_IPU_CORE0_SLEEP] = {
			   .name = __stringify(SYS_IPU_CORE0_SLEEP),
			   .sta_mask = IPU_CORE0_PWR_STA_MASK,
		     /* .ctl_addr = NULL,  */
			   .sram_pdn_bits = 0,
			   .sram_pdn_ack_bits = 0,
			   .bus_prot_mask = 0,
			   .ops = &IPU_core0_sleep_sys_ops,
			   },
	[SYS_IPU_CORE1_SHUTDOWN] = {
			   .name = __stringify(SYS_IPU_CORE1_SHUTDOWN),
			   .sta_mask = IPU_CORE1_PWR_STA_MASK,
		     /* .ctl_addr = NULL,  */
			   .sram_pdn_bits = 0,
			   .sram_pdn_ack_bits = 0,
			   .bus_prot_mask = 0,
			   .ops = &IPU_core1_shutdown_sys_ops,
			   },
	[SYS_IPU_CORE1_SLEEP] = {
			   .name = __stringify(SYS_IPU_CORE1_SLEEP),
			   .sta_mask = IPU_CORE1_PWR_STA_MASK,
		     /* .ctl_addr = NULL,  */
			   .sram_pdn_bits = 0,
			   .sram_pdn_ack_bits = 0,
			   .bus_prot_mask = 0,
			   .ops = &IPU_core1_sleep_sys_ops,
			   },

	[SYS_IPU_CORE2_SHUTDOWN] = {
			   .name = __stringify(SYS_IPU_CORE2_SHUTDOWN),
			   .sta_mask = IPU_CORE2_PWR_STA_MASK,
		     /* .ctl_addr = NULL,  */
			   .sram_pdn_bits = 0,
			   .sram_pdn_ack_bits = 0,
			   .bus_prot_mask = 0,
			   .ops = &IPU_core2_shutdown_sys_ops,
			   },
	[SYS_IPU_CORE2_SLEEP] = {
			   .name = __stringify(SYS_IPU_CORE2_SLEEP),
			   .sta_mask = IPU_CORE2_PWR_STA_MASK,
		     /* .ctl_addr = NULL,  */
			   .sram_pdn_bits = 0,
			   .sram_pdn_ack_bits = 0,
			   .bus_prot_mask = 0,
			   .ops = &IPU_core2_sleep_sys_ops,
			   },
};

LIST_HEAD(pgcb_list);

struct pg_callbacks *register_pg_callback(struct pg_callbacks *pgcb)
{
	INIT_LIST_HEAD(&pgcb->list);

	list_add(&pgcb->list, &pgcb_list);

	return pgcb;
}

static struct subsys *id_to_sys(unsigned int id)
{
	return id < NR_SYSS ? &syss[id] : NULL;
}

/* sync from mtcmos_ctrl.c  */
#ifdef CONFIG_MTK_RAM_CONSOLE
#if 0 /*add after early porting*/
static void aee_clk_data_rest(void)
{
	aee_rr_rec_clk(0, 0);
	aee_rr_rec_clk(1, 0);
	aee_rr_rec_clk(2, 0);
	aee_rr_rec_clk(3, 0);
}
#endif
#endif

#define DBG_ID_MFG0 1
#define DBG_ID_MFG1 2
#define DBG_ID_MFG2 3
#define DBG_ID_MFG3 4
#define DBG_ID_MFG4 5
#define DBG_ID_AUD 6
#define DBG_ID_MM 7
#define DBG_ID_CAM 8
#define DBG_ID_IPU_SHUTDOWN 9
#define DBG_ID_IPU_SLEEP 10
#define DBG_ID_ISP 11
#define DBG_ID_VEN 12
#define DBG_ID_VDE 13
#define DBG_ID_MD1 14
#define DBG_ID_C2K 15
#define DBG_ID_CONN 16


#define ID_MADK   0xFF000000
#define STA_MASK  0x00F00000
#define STEP_MASK 0x000000FF

#define INCREASE_STEPS \
	do { DBG_STEP++; } while (0)

/* disabled by yy */
#if 0
static int DBG_ID;
static int DBG_STA;
static int DBG_STEP;

/*
* ram console data0 define
* [31:24] : DBG_ID
* [23:20] : DBG_STA
* [7:0] : DBG_STEP
*/
static void ram_console_update(void)
{
	#ifdef CONFIG_MTK_RAM_CONSOLE

	unsigned long data1 = 0;

	data1 = ((DBG_ID << 24)&ID_MADK)
						|((DBG_STA << 20)&STA_MASK)
						|(DBG_STEP&STEP_MASK);
	/*aee_rr_rec_clk(0, data1);*/
	/*todo: add each domain's debug register to ram console*/
	#endif
}
#endif /* 0 */

/* auto-gen begin */
int spm_mtcmos_ctrl_mfg0(int state)
{
	int err = 0;

	/* TINFO="enable SPM register control" */
	spm_write(POWERON_CONFIG_EN, (SPM_PROJECT_CODE << 16) | (0x1 << 0));

	if (state == STA_POWER_DOWN) {
		/* TINFO="Start to turn off MFG0" */
#ifndef IGNORE_MTCMOS_CHECK
#endif
		/* TINFO="Set PWR_ISO = 1" */
		spm_write(MFG0_PWR_CON, spm_read(MFG0_PWR_CON) | PWR_ISO);
		/* TINFO="Set PWR_CLK_DIS = 1" */
		spm_write(MFG0_PWR_CON, spm_read(MFG0_PWR_CON) | PWR_CLK_DIS);
		/* TINFO="Set PWR_RST_B = 0" */
		spm_write(MFG0_PWR_CON, spm_read(MFG0_PWR_CON) & ~PWR_RST_B);
		/* TINFO="Set PWR_ON = 0" */
		spm_write(MFG0_PWR_CON, spm_read(MFG0_PWR_CON) & ~PWR_ON);
		/* TINFO="Set PWR_ON_2ND = 0" */
		spm_write(MFG0_PWR_CON, spm_read(MFG0_PWR_CON) & ~PWR_ON_2ND);
#ifndef IGNORE_MTCMOS_CHECK
		/* TINFO="Wait until PWR_STATUS = 0 and PWR_STATUS_2ND = 0" */
		while ((spm_read(PWR_STATUS) & MFG0_PWR_STA_MASK)
		       || (spm_read(PWR_STATUS_2ND) & MFG0_PWR_STA_MASK)) {
				/*  */
				/*  */
		}
#endif
		/* TINFO="Finish to turn off MFG0" */
	} else {    /* STA_POWER_ON */
		/* TINFO="Start to turn on MFG0" */
		/* TINFO="Set PWR_ON = 1" */
		spm_write(MFG0_PWR_CON, spm_read(MFG0_PWR_CON) | PWR_ON);
		/* TINFO="Set PWR_ON_2ND = 1" */
		spm_write(MFG0_PWR_CON, spm_read(MFG0_PWR_CON) | PWR_ON_2ND);
#ifndef IGNORE_MTCMOS_CHECK
		/* TINFO="Wait until PWR_STATUS = 1 and PWR_STATUS_2ND = 1" */
		while (((spm_read(PWR_STATUS) & MFG0_PWR_STA_MASK) != MFG0_PWR_STA_MASK)
		       || ((spm_read(PWR_STATUS_2ND) & MFG0_PWR_STA_MASK) != MFG0_PWR_STA_MASK)) {
				/*  */
				/*  */
		}
#endif
		/* TINFO="Set PWR_CLK_DIS = 0" */
		spm_write(MFG0_PWR_CON, spm_read(MFG0_PWR_CON) & ~PWR_CLK_DIS);
		/* TINFO="Set PWR_ISO = 0" */
		spm_write(MFG0_PWR_CON, spm_read(MFG0_PWR_CON) & ~PWR_ISO);
		/* TINFO="Set PWR_RST_B = 1" */
		spm_write(MFG0_PWR_CON, spm_read(MFG0_PWR_CON) | PWR_RST_B);
#ifndef IGNORE_MTCMOS_CHECK
#endif
		/* TINFO="Finish to turn on MFG0" */
	}

	return err;
}

/* Define MTCMOS Bus Protect Mask */
#define MFG1_PROT_BIT_MASK               ((0x1 << 24) \
					  |(0x1 << 25))
#define MFG1_PROT_BIT_ACK_MASK           ((0x1 << 24) \
					  |(0x1 << 25))
#define MFG1_PROT_BIT_2ND_MASK           ((0x1 << 4) \
					  |(0x1 << 5) \
					  |(0x1 << 6))
#define MFG1_PROT_BIT_ACK_2ND_MASK       ((0x1 << 4) \
					  |(0x1 << 5) \
					  |(0x1 << 6))

int spm_mtcmos_ctrl_mfg1(int state)
{
	int err = 0;

	/* TINFO="enable SPM register control" */
	spm_write(POWERON_CONFIG_EN, (SPM_PROJECT_CODE << 16) | (0x1 << 0));

	if (state == STA_POWER_DOWN) {
		/* TINFO="Start to turn off MFG1" */
		/* TINFO="Set way_en = 0" */
		/* *((UINT32P)(0x10000200)) = *((UINT32P)(0x10000200)) & (~(0x1 << 7)); */
		spm_write(INFRA_TOPAXI_SI0_CTL, spm_read(INFRA_TOPAXI_SI0_CTL) & (~(0x1 << 7)));
#ifndef IGNORE_MTCMOS_CHECK
		/* TINFO="Polling way_en ctrl_update=1" */
		/* while ((*((UINT32P)(0x10260000)) & (0x1 << 24)) != (0x1 << 24)){ */
		while ((spm_read(INFRA_TOPAXI_SI0_STA) & (0x1 << 24)) != (0x1 << 24))
			;

		/* TINFO="Polling protect_idle=1" */
		/* while ((*((UINT32P)(0x10000224)) & (0x1 << 6)) != (0x1 << 6)){ */
		while ((spm_read(INFRA_TOPAXI_PROTECTSTA0) & (0x1 << 6)) != (0x1 << 6))
			;
#endif
		/* TINFO="Set bus protect" */
		spm_write(INFRA_TOPAXI_PROTECTEN_2_SET, MFG1_PROT_BIT_MASK);
#ifndef IGNORE_MTCMOS_CHECK
		while ((spm_read(INFRA_TOPAXI_PROTECTSTA1_2) & MFG1_PROT_BIT_ACK_MASK) != MFG1_PROT_BIT_ACK_MASK)
			;
#endif
		/* TINFO="Set bus protect" */
		spm_write(INFRA_TOPAXI_PROTECTEN_SET, MFG1_PROT_BIT_2ND_MASK);
#ifndef IGNORE_MTCMOS_CHECK
		while ((spm_read(INFRA_TOPAXI_PROTECTSTA1) & MFG1_PROT_BIT_ACK_2ND_MASK) !=
				MFG1_PROT_BIT_ACK_2ND_MASK)
			;
#endif
		/* TINFO="Set SRAM_PDN = 1" */
		spm_write(MFG1_PWR_CON, spm_read(MFG1_PWR_CON) | MFG1_SRAM_PDN);
#ifndef IGNORE_MTCMOS_CHECK
		/* TINFO="Wait until MFG1_SRAM_PDN_ACK = 1" */
		while ((spm_read(MFG1_PWR_CON) & MFG1_SRAM_PDN_ACK) != MFG1_SRAM_PDN_ACK) {
				/*  */
				/*  */
		}
#endif
		/* TINFO="Set PWR_ISO = 1" */
		spm_write(MFG1_PWR_CON, spm_read(MFG1_PWR_CON) | PWR_ISO);
		/* TINFO="Set PWR_CLK_DIS = 1" */
		spm_write(MFG1_PWR_CON, spm_read(MFG1_PWR_CON) | PWR_CLK_DIS);
		/* TINFO="Set PWR_RST_B = 0" */
		spm_write(MFG1_PWR_CON, spm_read(MFG1_PWR_CON) & ~PWR_RST_B);
		/* TINFO="Set PWR_ON = 0" */
		spm_write(MFG1_PWR_CON, spm_read(MFG1_PWR_CON) & ~PWR_ON);
		/* TINFO="Set PWR_ON_2ND = 0" */
		spm_write(MFG1_PWR_CON, spm_read(MFG1_PWR_CON) & ~PWR_ON_2ND);
#ifndef IGNORE_MTCMOS_CHECK
		/* TINFO="Wait until PWR_STATUS = 0 and PWR_STATUS_2ND = 0" */
		while ((spm_read(PWR_STATUS) & MFG1_PWR_STA_MASK)
		       || (spm_read(PWR_STATUS_2ND) & MFG1_PWR_STA_MASK)) {
				/*  */
				/*  */
		}
#endif
		/* TINFO="Finish to turn off MFG1" */
	} else {    /* STA_POWER_ON */
		/* TINFO="Start to turn on MFG1" */
		/* TINFO="Set PWR_ON = 1" */
		spm_write(MFG1_PWR_CON, spm_read(MFG1_PWR_CON) | PWR_ON);
		/* TINFO="Set PWR_ON_2ND = 1" */
		spm_write(MFG1_PWR_CON, spm_read(MFG1_PWR_CON) | PWR_ON_2ND);
#ifndef IGNORE_MTCMOS_CHECK
		/* TINFO="Wait until PWR_STATUS = 1 and PWR_STATUS_2ND = 1" */
		while (((spm_read(PWR_STATUS) & MFG1_PWR_STA_MASK) != MFG1_PWR_STA_MASK)
		       || ((spm_read(PWR_STATUS_2ND) & MFG1_PWR_STA_MASK) != MFG1_PWR_STA_MASK)) {
				/*  */
				/*  */
		}
#endif
		/* TINFO="Set PWR_CLK_DIS = 0" */
		spm_write(MFG1_PWR_CON, spm_read(MFG1_PWR_CON) & ~PWR_CLK_DIS);
		/* TINFO="Set PWR_ISO = 0" */
		spm_write(MFG1_PWR_CON, spm_read(MFG1_PWR_CON) & ~PWR_ISO);
		/* TINFO="Set PWR_RST_B = 1" */
		spm_write(MFG1_PWR_CON, spm_read(MFG1_PWR_CON) | PWR_RST_B);
		/* TINFO="Set SRAM_PDN = 0" */
		spm_write(MFG1_PWR_CON, spm_read(MFG1_PWR_CON) & ~(0x1 << 8));
#ifndef IGNORE_MTCMOS_CHECK
		/* TINFO="Wait until MFG1_SRAM_PDN_ACK_BIT0 = 0" */
		while (spm_read(MFG1_PWR_CON) & MFG1_SRAM_PDN_ACK_BIT0) {
				/*  */
				/*  */
		}
#endif
		spm_write(MFG1_PWR_CON, spm_read(MFG1_PWR_CON) & ~(0x1 << 9));
		spm_write(MFG1_PWR_CON, spm_read(MFG1_PWR_CON) & ~(0x1 << 10));
		spm_write(MFG1_PWR_CON, spm_read(MFG1_PWR_CON) & ~(0x1 << 11));
		/* TINFO="Release bus protect" */
		spm_write(INFRA_TOPAXI_PROTECTEN_CLR, MFG1_PROT_BIT_2ND_MASK);
#ifndef IGNORE_MTCMOS_CHECK
		/* Note that this protect ack check after releasing protect has been ignored */
#endif
		/* TINFO="Release bus protect" */
		spm_write(INFRA_TOPAXI_PROTECTEN_2_CLR, MFG1_PROT_BIT_MASK);
#ifndef IGNORE_MTCMOS_CHECK
		/* Note that this protect ack check after releasing protect has been ignored */
#endif
		/* TINFO=Set way_en = 1" */
		/* *((UINT32P)(0x10000200)) = *((UINT32P)(0x10000200)) | (0x1 << 7); */
		spm_write(INFRA_TOPAXI_SI0_CTL, spm_read(INFRA_TOPAXI_SI0_CTL) | (0x1 << 7));
#ifndef IGNORE_MTCMOS_CHECK
		/* TINFO="Polling way_en ctrl_update=1" */
		/* while ((*((UINT32P)(0x10260000)) & (0x1 << 24)) != (0x1 << 24)){ */
		while ((spm_read(INFRA_TOPAXI_SI0_STA) & (0x1 << 24)) != (0x1 << 24)) {
				/*  */
				/*  */
		}
#endif
		/* TINFO="Finish to turn on MFG1" */
	}

	return err;
}

int spm_mtcmos_ctrl_mfg2(int state)
{
	int err = 0;

	/* TINFO="enable SPM register control" */
	spm_write(POWERON_CONFIG_EN, (SPM_PROJECT_CODE << 16) | (0x1 << 0));

	if (state == STA_POWER_DOWN) {
		/* TINFO="Start to turn off MFG2" */
		/* TINFO="Set SRAM_PDN = 1" */
		spm_write(MFG2_PWR_CON, spm_read(MFG2_PWR_CON) | MFG2_SRAM_PDN);
#ifndef IGNORE_MTCMOS_CHECK
		/* TINFO="Wait until MFG2_SRAM_PDN_ACK = 1" */
		while ((spm_read(MFG2_PWR_CON) & MFG2_SRAM_PDN_ACK) != MFG2_SRAM_PDN_ACK) {
				/*  */
				/*  */
		}
#endif
		/* TINFO="Set PWR_ISO = 1" */
		spm_write(MFG2_PWR_CON, spm_read(MFG2_PWR_CON) | PWR_ISO);
		/* TINFO="Set PWR_CLK_DIS = 1" */
		spm_write(MFG2_PWR_CON, spm_read(MFG2_PWR_CON) | PWR_CLK_DIS);
		/* TINFO="Set PWR_RST_B = 0" */
		spm_write(MFG2_PWR_CON, spm_read(MFG2_PWR_CON) & ~PWR_RST_B);
		/* TINFO="Set PWR_ON = 0" */
		spm_write(MFG2_PWR_CON, spm_read(MFG2_PWR_CON) & ~PWR_ON);
		/* TINFO="Set PWR_ON_2ND = 0" */
		spm_write(MFG2_PWR_CON, spm_read(MFG2_PWR_CON) & ~PWR_ON_2ND);
#ifndef IGNORE_MTCMOS_CHECK
		/* TINFO="Wait until PWR_STATUS = 0 and PWR_STATUS_2ND = 0" */
		while ((spm_read(PWR_STATUS) & MFG2_PWR_STA_MASK)
		       || (spm_read(PWR_STATUS_2ND) & MFG2_PWR_STA_MASK)) {
				/*  */
				/*  */
		}
#endif
		/* TINFO="Finish to turn off MFG2" */
	} else {    /* STA_POWER_ON */
		/* TINFO="Start to turn on MFG2" */
		/* TINFO="Set PWR_ON = 1" */
		spm_write(MFG2_PWR_CON, spm_read(MFG2_PWR_CON) | PWR_ON);
		/* TINFO="Set PWR_ON_2ND = 1" */
		spm_write(MFG2_PWR_CON, spm_read(MFG2_PWR_CON) | PWR_ON_2ND);
#ifndef IGNORE_MTCMOS_CHECK
		/* TINFO="Wait until PWR_STATUS = 1 and PWR_STATUS_2ND = 1" */
		while (((spm_read(PWR_STATUS) & MFG2_PWR_STA_MASK) != MFG2_PWR_STA_MASK)
		       || ((spm_read(PWR_STATUS_2ND) & MFG2_PWR_STA_MASK) != MFG2_PWR_STA_MASK)) {
				/*  */
				/*  */
		}
#endif
		/* TINFO="Set PWR_CLK_DIS = 0" */
		spm_write(MFG2_PWR_CON, spm_read(MFG2_PWR_CON) & ~PWR_CLK_DIS);
		/* TINFO="Set PWR_ISO = 0" */
		spm_write(MFG2_PWR_CON, spm_read(MFG2_PWR_CON) & ~PWR_ISO);
		/* TINFO="Set PWR_RST_B = 1" */
		spm_write(MFG2_PWR_CON, spm_read(MFG2_PWR_CON) | PWR_RST_B);
		/* TINFO="Set SRAM_PDN = 0" */
		spm_write(MFG2_PWR_CON, spm_read(MFG2_PWR_CON) & ~(0x1 << 8));
#ifndef IGNORE_MTCMOS_CHECK
		/* TINFO="Wait until MFG2_SRAM_PDN_ACK_BIT0 = 0" */
		while (spm_read(MFG2_PWR_CON) & MFG2_SRAM_PDN_ACK_BIT0) {
				/*  */
				/*  */
		}
#endif
		spm_write(MFG2_PWR_CON, spm_read(MFG2_PWR_CON) & ~(0x1 << 9));
		spm_write(MFG2_PWR_CON, spm_read(MFG2_PWR_CON) & ~(0x1 << 10));
		spm_write(MFG2_PWR_CON, spm_read(MFG2_PWR_CON) & ~(0x1 << 11));
		/* TINFO="Finish to turn on MFG2" */
	}

	return err;
}

int spm_mtcmos_ctrl_mfg3(int state)
{
	int err = 0;

	/* TINFO="enable SPM register control" */
	spm_write(POWERON_CONFIG_EN, (SPM_PROJECT_CODE << 16) | (0x1 << 0));

	if (state == STA_POWER_DOWN) {
		/* TINFO="Start to turn off MFG3" */
		/* TINFO="Set SRAM_PDN = 1" */
		spm_write(MFG3_PWR_CON, spm_read(MFG3_PWR_CON) | MFG3_SRAM_PDN);
#ifndef IGNORE_MTCMOS_CHECK
		/* TINFO="Wait until MFG3_SRAM_PDN_ACK = 1" */
		while ((spm_read(MFG3_PWR_CON) & MFG3_SRAM_PDN_ACK) != MFG3_SRAM_PDN_ACK) {
				/*  */
				/*  */
		}
#endif
		/* TINFO="Set PWR_ISO = 1" */
		spm_write(MFG3_PWR_CON, spm_read(MFG3_PWR_CON) | PWR_ISO);
		/* TINFO="Set PWR_CLK_DIS = 1" */
		spm_write(MFG3_PWR_CON, spm_read(MFG3_PWR_CON) | PWR_CLK_DIS);
		/* TINFO="Set PWR_RST_B = 0" */
		spm_write(MFG3_PWR_CON, spm_read(MFG3_PWR_CON) & ~PWR_RST_B);
		/* TINFO="Set PWR_ON = 0" */
		spm_write(MFG3_PWR_CON, spm_read(MFG3_PWR_CON) & ~PWR_ON);
		/* TINFO="Set PWR_ON_2ND = 0" */
		spm_write(MFG3_PWR_CON, spm_read(MFG3_PWR_CON) & ~PWR_ON_2ND);
#ifndef IGNORE_MTCMOS_CHECK
		/* TINFO="Wait until PWR_STATUS = 0 and PWR_STATUS_2ND = 0" */
		while ((spm_read(PWR_STATUS) & MFG3_PWR_STA_MASK)
		       || (spm_read(PWR_STATUS_2ND) & MFG3_PWR_STA_MASK)) {
				/*  */
				/*  */
		}
#endif
		/* TINFO="Finish to turn off MFG3" */
	} else {    /* STA_POWER_ON */
		/* TINFO="Start to turn on MFG3" */
		/* TINFO="Set PWR_ON = 1" */
		spm_write(MFG3_PWR_CON, spm_read(MFG3_PWR_CON) | PWR_ON);
		/* TINFO="Set PWR_ON_2ND = 1" */
		spm_write(MFG3_PWR_CON, spm_read(MFG3_PWR_CON) | PWR_ON_2ND);
#ifndef IGNORE_MTCMOS_CHECK
		/* TINFO="Wait until PWR_STATUS = 1 and PWR_STATUS_2ND = 1" */
		while (((spm_read(PWR_STATUS) & MFG3_PWR_STA_MASK) != MFG3_PWR_STA_MASK)
		       || ((spm_read(PWR_STATUS_2ND) & MFG3_PWR_STA_MASK) != MFG3_PWR_STA_MASK)) {
				/*  */
				/*  */
		}
#endif
		/* TINFO="Set PWR_CLK_DIS = 0" */
		spm_write(MFG3_PWR_CON, spm_read(MFG3_PWR_CON) & ~PWR_CLK_DIS);
		/* TINFO="Set PWR_ISO = 0" */
		spm_write(MFG3_PWR_CON, spm_read(MFG3_PWR_CON) & ~PWR_ISO);
		/* TINFO="Set PWR_RST_B = 1" */
		spm_write(MFG3_PWR_CON, spm_read(MFG3_PWR_CON) | PWR_RST_B);
		/* TINFO="Set SRAM_PDN = 0" */
		spm_write(MFG3_PWR_CON, spm_read(MFG3_PWR_CON) & ~(0x1 << 8));
#ifndef IGNORE_MTCMOS_CHECK
		/* TINFO="Wait until MFG3_SRAM_PDN_ACK_BIT0 = 0" */
		while (spm_read(MFG3_PWR_CON) & MFG3_SRAM_PDN_ACK_BIT0) {
				/*  */
				/*  */
		}
#endif
		spm_write(MFG3_PWR_CON, spm_read(MFG3_PWR_CON) & ~(0x1 << 9));
		spm_write(MFG3_PWR_CON, spm_read(MFG3_PWR_CON) & ~(0x1 << 10));
		spm_write(MFG3_PWR_CON, spm_read(MFG3_PWR_CON) & ~(0x1 << 11));
		/* TINFO="Finish to turn on MFG3" */
	}

	return err;
}

int spm_mtcmos_ctrl_mfg4(int state)
{
	int err = 0;

	/* TINFO="enable SPM register control" */
	spm_write(POWERON_CONFIG_EN, (SPM_PROJECT_CODE << 16) | (0x1 << 0));

	if (state == STA_POWER_DOWN) {
		/* TINFO="Start to turn off MFG4" */
		/* TINFO="Set SRAM_PDN = 1" */
		spm_write(MFG4_PWR_CON, spm_read(MFG4_PWR_CON) | MFG4_SRAM_PDN);
#ifndef IGNORE_MTCMOS_CHECK
		/* TINFO="Wait until MFG4_SRAM_PDN_ACK = 1" */
		while ((spm_read(MFG4_PWR_CON) & MFG4_SRAM_PDN_ACK) != MFG4_SRAM_PDN_ACK) {
				/*  */
				/*  */
		}
#endif
		/* TINFO="Set PWR_ISO = 1" */
		spm_write(MFG4_PWR_CON, spm_read(MFG4_PWR_CON) | PWR_ISO);
		/* TINFO="Set PWR_CLK_DIS = 1" */
		spm_write(MFG4_PWR_CON, spm_read(MFG4_PWR_CON) | PWR_CLK_DIS);
		/* TINFO="Set PWR_RST_B = 0" */
		spm_write(MFG4_PWR_CON, spm_read(MFG4_PWR_CON) & ~PWR_RST_B);
		/* TINFO="Set PWR_ON = 0" */
		spm_write(MFG4_PWR_CON, spm_read(MFG4_PWR_CON) & ~PWR_ON);
		/* TINFO="Set PWR_ON_2ND = 0" */
		spm_write(MFG4_PWR_CON, spm_read(MFG4_PWR_CON) & ~PWR_ON_2ND);
#ifndef IGNORE_MTCMOS_CHECK
		/* TINFO="Wait until PWR_STATUS = 0 and PWR_STATUS_2ND = 0" */
		while ((spm_read(PWR_STATUS) & MFG4_PWR_STA_MASK)
		       || (spm_read(PWR_STATUS_2ND) & MFG4_PWR_STA_MASK)) {
				/*  */
				/*  */
		}
#endif
		/* TINFO="Finish to turn off MFG4" */
	} else {    /* STA_POWER_ON */
		/* TINFO="Start to turn on MFG4" */
		/* TINFO="Set PWR_ON = 1" */
		spm_write(MFG4_PWR_CON, spm_read(MFG4_PWR_CON) | PWR_ON);
		/* TINFO="Set PWR_ON_2ND = 1" */
		spm_write(MFG4_PWR_CON, spm_read(MFG4_PWR_CON) | PWR_ON_2ND);
#ifndef IGNORE_MTCMOS_CHECK
		/* TINFO="Wait until PWR_STATUS = 1 and PWR_STATUS_2ND = 1" */
		while (((spm_read(PWR_STATUS) & MFG4_PWR_STA_MASK) != MFG4_PWR_STA_MASK)
		       || ((spm_read(PWR_STATUS_2ND) & MFG4_PWR_STA_MASK) != MFG4_PWR_STA_MASK)) {
				/*  */
				/*  */
		}
#endif
		/* TINFO="Set PWR_CLK_DIS = 0" */
		spm_write(MFG4_PWR_CON, spm_read(MFG4_PWR_CON) & ~PWR_CLK_DIS);
		/* TINFO="Set PWR_ISO = 0" */
		spm_write(MFG4_PWR_CON, spm_read(MFG4_PWR_CON) & ~PWR_ISO);
		/* TINFO="Set PWR_RST_B = 1" */
		spm_write(MFG4_PWR_CON, spm_read(MFG4_PWR_CON) | PWR_RST_B);
		/* TINFO="Set SRAM_PDN = 0" */
		spm_write(MFG4_PWR_CON, spm_read(MFG4_PWR_CON) & ~(0x1 << 8));
#ifndef IGNORE_MTCMOS_CHECK
		/* TINFO="Wait until MFG4_SRAM_PDN_ACK_BIT0 = 0" */
		while (spm_read(MFG4_PWR_CON) & MFG4_SRAM_PDN_ACK_BIT0) {
				/*  */
				/*  */
		}
#endif
		spm_write(MFG4_PWR_CON, spm_read(MFG4_PWR_CON) & ~(0x1 << 9));
		spm_write(MFG4_PWR_CON, spm_read(MFG4_PWR_CON) & ~(0x1 << 10));
		spm_write(MFG4_PWR_CON, spm_read(MFG4_PWR_CON) & ~(0x1 << 11));
		/* TINFO="Finish to turn on MFG4" */
	}

	return err;
}

int spm_mtcmos_ctrl_mfg5(int state)
{
	int err = 0;

	/* TINFO="enable SPM register control" */
	spm_write(POWERON_CONFIG_EN, (SPM_PROJECT_CODE << 16) | (0x1 << 0));

	if (state == STA_POWER_DOWN) {
		/* TINFO="Start to turn off MFG5" */
		/* TINFO="Set SRAM_PDN = 1" */
		spm_write(MFG5_PWR_CON, spm_read(MFG5_PWR_CON) | MFG5_SRAM_PDN);
#ifndef IGNORE_MTCMOS_CHECK
		/* TINFO="Wait until MFG5_SRAM_PDN_ACK = 1" */
		while ((spm_read(MFG5_PWR_CON) & MFG5_SRAM_PDN_ACK) != MFG5_SRAM_PDN_ACK) {
				/*  */
				/*  */
		}
#endif
		/* TINFO="Set PWR_ISO = 1" */
		spm_write(MFG5_PWR_CON, spm_read(MFG5_PWR_CON) | PWR_ISO);
		/* TINFO="Set PWR_CLK_DIS = 1" */
		spm_write(MFG5_PWR_CON, spm_read(MFG5_PWR_CON) | PWR_CLK_DIS);
		/* TINFO="Set PWR_RST_B = 0" */
		spm_write(MFG5_PWR_CON, spm_read(MFG5_PWR_CON) & ~PWR_RST_B);
		/* TINFO="Set PWR_ON = 0" */
		spm_write(MFG5_PWR_CON, spm_read(MFG5_PWR_CON) & ~PWR_ON);
		/* TINFO="Set PWR_ON_2ND = 0" */
		spm_write(MFG5_PWR_CON, spm_read(MFG5_PWR_CON) & ~PWR_ON_2ND);
#ifndef IGNORE_MTCMOS_CHECK
		/* TINFO="Wait until PWR_STATUS = 0 and PWR_STATUS_2ND = 0" */
		while ((spm_read(PWR_STATUS) & MFG5_PWR_STA_MASK)
		       || (spm_read(PWR_STATUS_2ND) & MFG5_PWR_STA_MASK)) {
				/*  */
				/*  */
		}
#endif
		/* TINFO="Finish to turn off MFG5" */
	} else {    /* STA_POWER_ON */
		/* TINFO="Start to turn on MFG5" */
		/* TINFO="Set PWR_ON = 1" */
		spm_write(MFG5_PWR_CON, spm_read(MFG5_PWR_CON) | PWR_ON);
		/* TINFO="Set PWR_ON_2ND = 1" */
		spm_write(MFG5_PWR_CON, spm_read(MFG5_PWR_CON) | PWR_ON_2ND);
#ifndef IGNORE_MTCMOS_CHECK
		/* TINFO="Wait until PWR_STATUS = 1 and PWR_STATUS_2ND = 1" */
		while (((spm_read(PWR_STATUS) & MFG5_PWR_STA_MASK) != MFG5_PWR_STA_MASK)
		       || ((spm_read(PWR_STATUS_2ND) & MFG5_PWR_STA_MASK) != MFG5_PWR_STA_MASK)) {
				/*  */
				/*  */
		}
#endif
		/* TINFO="Set PWR_CLK_DIS = 0" */
		spm_write(MFG5_PWR_CON, spm_read(MFG5_PWR_CON) & ~PWR_CLK_DIS);
		/* TINFO="Set PWR_ISO = 0" */
		spm_write(MFG5_PWR_CON, spm_read(MFG5_PWR_CON) & ~PWR_ISO);
		/* TINFO="Set PWR_RST_B = 1" */
		spm_write(MFG5_PWR_CON, spm_read(MFG5_PWR_CON) | PWR_RST_B);
		/* TINFO="Set SRAM_PDN = 0" */
		spm_write(MFG5_PWR_CON, spm_read(MFG5_PWR_CON) & ~(0x1 << 8));
#ifndef IGNORE_MTCMOS_CHECK
		/* TINFO="Wait until MFG5_SRAM_PDN_ACK_BIT0 = 0" */
		while (spm_read(MFG5_PWR_CON) & MFG5_SRAM_PDN_ACK_BIT0) {
				/*  */
				/*  */
		}
#endif
		spm_write(MFG5_PWR_CON, spm_read(MFG5_PWR_CON) & ~(0x1 << 9));
		spm_write(MFG5_PWR_CON, spm_read(MFG5_PWR_CON) & ~(0x1 << 10));
		spm_write(MFG5_PWR_CON, spm_read(MFG5_PWR_CON) & ~(0x1 << 11));
		/* TINFO="Finish to turn on MFG5" */
	}

	return err;
}

#define C2K_PROT_BIT_MASK                ((0x1 << 22) \
					  |(0x1 << 23) \
					  |(0x1 << 24) \
					  |(0x1 << 25))
#define C2K_PROT_BIT_ACK_MASK            ((0x1 << 22) \
					  |(0x1 << 23) \
					  |(0x1 << 24) \
					  |(0x1 << 25))

int spm_mtcmos_ctrl_c2k(int state)
{
	int err = 0;

	/* TINFO="enable SPM register control" */
	spm_write(POWERON_CONFIG_EN, (SPM_PROJECT_CODE << 16) | (0x1 << 0));

	if (state == STA_POWER_DOWN) {
		/* TINFO="Start to turn off C2K" */
		/* TINFO="Set bus protect" */
		spm_write(INFRA_TOPAXI_PROTECTEN_1_SET, C2K_PROT_BIT_MASK);
#ifndef IGNORE_MTCMOS_CHECK
		while ((spm_read(INFRA_TOPAXI_PROTECTSTA1_1) & C2K_PROT_BIT_ACK_MASK) != C2K_PROT_BIT_ACK_MASK) {
			/*  */
			/*  */
		}
#endif
#ifndef IGNORE_MTCMOS_CHECK
#endif
		/* TINFO="Set PWR_ISO = 1" */
		spm_write(C2K_PWR_CON, spm_read(C2K_PWR_CON) | PWR_ISO);
		/* TINFO="Set PWR_CLK_DIS = 1" */
		spm_write(C2K_PWR_CON, spm_read(C2K_PWR_CON) | PWR_CLK_DIS);
		/* TINFO="Set PWR_RST_B = 0" */
		spm_write(C2K_PWR_CON, spm_read(C2K_PWR_CON) & ~PWR_RST_B);
		/* TINFO="Set PWR_ON = 0" */
		spm_write(C2K_PWR_CON, spm_read(C2K_PWR_CON) & ~PWR_ON);
		/* TINFO="Set PWR_ON_2ND = 0" */
		spm_write(C2K_PWR_CON, spm_read(C2K_PWR_CON) & ~PWR_ON_2ND);
#ifndef IGNORE_MTCMOS_CHECK
		/* TINFO="Wait until PWR_STATUS = 0 and PWR_STATUS_2ND = 0" */
		while ((spm_read(PWR_STATUS) & C2K_PWR_STA_MASK)
		       || (spm_read(PWR_STATUS_2ND) & C2K_PWR_STA_MASK)) {
				/*  */
				/*  */
		}
#endif
		/* TINFO="Finish to turn off C2K" */
	} else {    /* STA_POWER_ON */
		/* TINFO="Start to turn on C2K" */
		/* TINFO="Set PWR_ON = 1" */
		spm_write(C2K_PWR_CON, spm_read(C2K_PWR_CON) | PWR_ON);
		/* TINFO="Set PWR_ON_2ND = 1" */
		spm_write(C2K_PWR_CON, spm_read(C2K_PWR_CON) | PWR_ON_2ND);
#ifndef IGNORE_MTCMOS_CHECK
		/* TINFO="Wait until PWR_STATUS = 1 and PWR_STATUS_2ND = 1" */
		while (((spm_read(PWR_STATUS) & C2K_PWR_STA_MASK) != C2K_PWR_STA_MASK)
		       || ((spm_read(PWR_STATUS_2ND) & C2K_PWR_STA_MASK) != C2K_PWR_STA_MASK)) {
				/*  */
				/*  */
		}
#endif
		/* TINFO="Set PWR_CLK_DIS = 0" */
		spm_write(C2K_PWR_CON, spm_read(C2K_PWR_CON) & ~PWR_CLK_DIS);
		/* TINFO="Set PWR_ISO = 0" */
		spm_write(C2K_PWR_CON, spm_read(C2K_PWR_CON) & ~PWR_ISO);
		/* TINFO="Set PWR_RST_B = 1" */
		spm_write(C2K_PWR_CON, spm_read(C2K_PWR_CON) | PWR_RST_B);
#ifndef IGNORE_MTCMOS_CHECK
#endif
		/* TINFO="Release bus protect" */
		spm_write(INFRA_TOPAXI_PROTECTEN_1_CLR, C2K_PROT_BIT_MASK);
#ifndef IGNORE_MTCMOS_CHECK
		/* Note that this protect ack check after releasing protect has been ignored */
#endif
		/* TINFO="Finish to turn on C2K" */
	}

	return err;
}

#define MD1_PROT_BIT_MASK                ((0x1 << 16) \
					  |(0x1 << 17) \
					  |(0x1 << 18) \
					  |(0x1 << 19) \
					  |(0x1 << 21) \
					  |(0x1 << 25) \
					  |(0x1 << 28))
#define MD1_PROT_BIT_ACK_MASK            ((0x1 << 16) \
					  |(0x1 << 17) \
					  |(0x1 << 18) \
					  |(0x1 << 19) \
					  |(0x1 << 21) \
					  |(0x1 << 25))

int spm_mtcmos_ctrl_md1(int state)
{
	int err = 0;

	/* TINFO="enable SPM register control" */
	spm_write(POWERON_CONFIG_EN, (SPM_PROJECT_CODE << 16) | (0x1 << 0));

	if (state == STA_POWER_DOWN) {
		/* TINFO="Start to turn off MD1" */
		/* TINFO="Set bus protect" */
		spm_write(INFRA_TOPAXI_PROTECTEN_1_SET, MD1_PROT_BIT_MASK);
#ifndef IGNORE_MTCMOS_CHECK
		while ((spm_read(INFRA_TOPAXI_PROTECTSTA1_1) & MD1_PROT_BIT_ACK_MASK) != MD1_PROT_BIT_ACK_MASK) {
				/*  */
				/*  */
		}
#endif
		/* TINFO="Set SRAM_PDN = 1" */
		spm_write(MD1_PWR_CON, spm_read(MD1_PWR_CON) | MD1_SRAM_PDN);
#ifndef IGNORE_MTCMOS_CHECK
#endif
		/* TINFO="Set PWR_CLK_DIS = 1" */
		spm_write(MD1_PWR_CON, spm_read(MD1_PWR_CON) | PWR_CLK_DIS);
		/* TINFO="EXT_BUCK_ISO[0]=1"*/
		spm_write(EXT_BUCK_ISO, spm_read(EXT_BUCK_ISO) | (0x1 << 0));
		/* TINFO="Set PWR_ISO = 1" */
		spm_write(MD1_PWR_CON, spm_read(MD1_PWR_CON) | PWR_ISO);
		/* TINFO="Set PWR_RST_B = 0" */
		spm_write(MD1_PWR_CON, spm_read(MD1_PWR_CON) & ~PWR_RST_B);
		/* TINFO="Set PWR_ON = 0" */
		spm_write(MD1_PWR_CON, spm_read(MD1_PWR_CON) & ~PWR_ON);
		/* TINFO="Set PWR_ON_2ND = 0" */
		spm_write(MD1_PWR_CON, spm_read(MD1_PWR_CON) & ~PWR_ON_2ND);
		/* TINFO="Finish to turn off MD1" */
	} else {    /* STA_POWER_ON */
		/* TINFO="Start to turn on MD1" */
		/* TINFO="EXT_BUCK_ISO[0]=0"*/
		spm_write(EXT_BUCK_ISO, spm_read(EXT_BUCK_ISO) & ~(0x1 << 0));
		/* TINFO="Set PWR_ON = 1" */
		spm_write(MD1_PWR_CON, spm_read(MD1_PWR_CON) | PWR_ON);
		/* TINFO="Set PWR_ON_2ND = 1" */
		spm_write(MD1_PWR_CON, spm_read(MD1_PWR_CON) | PWR_ON_2ND);
#ifndef IGNORE_MTCMOS_CHECK
		/* TINFO="Wait until PWR_STATUS = 1 and PWR_STATUS_2ND = 1" */
		while (((spm_read(PWR_STATUS) & MD1_PWR_STA_MASK) != MD1_PWR_STA_MASK)
		       || ((spm_read(PWR_STATUS_2ND) & MD1_PWR_STA_MASK) != MD1_PWR_STA_MASK)) {
				/*  */
				/*  */
		}
#endif
		/* TINFO="Set PWR_CLK_DIS = 0" */
		spm_write(MD1_PWR_CON, spm_read(MD1_PWR_CON) & ~PWR_CLK_DIS);
		/* TINFO="Set bus protect" */
		spm_write(INFRA_TOPAXI_PROTECTEN_1_SET, MD1_PROT_BIT_MASK);
#ifndef IGNORE_MTCMOS_CHECK
		while ((spm_read(INFRA_TOPAXI_PROTECTSTA1_1) & MD1_PROT_BIT_ACK_MASK) != MD1_PROT_BIT_ACK_MASK) {
				/*  */
				/*  */
		}
#endif
		/* TINFO="Set PWR_ISO = 0" */
		spm_write(MD1_PWR_CON, spm_read(MD1_PWR_CON) & ~PWR_ISO);
		/* TINFO="Set SRAM_PDN = 0" */
		spm_write(MD1_PWR_CON, spm_read(MD1_PWR_CON) & ~(0x1 << 8));
		spm_write(MD1_PWR_CON, spm_read(MD1_PWR_CON) & ~(0x1 << 9));
		spm_write(MD1_PWR_CON, spm_read(MD1_PWR_CON) & ~(0x1 << 10));
		/* TINFO="Set PWR_RST_B = 1" */
		spm_write(MD1_PWR_CON, spm_read(MD1_PWR_CON) | PWR_RST_B);
		/* TINFO="Release bus protect" */
		spm_write(INFRA_TOPAXI_PROTECTEN_1_CLR, MD1_PROT_BIT_MASK);
#ifndef IGNORE_MTCMOS_CHECK
		/* Note that this protect ack check after releasing protect has been ignored */
#endif
		/* TINFO="Finish to turn on MD1" */
	}

	return err;
}

#define CONN_PROT_BIT_MASK               ((0x1 << 10) \
					  |(0x1 << 19))
#define CONN_PROT_BIT_ACK_MASK           ((0x1 << 10) \
					  |(0x1 << 19))

int spm_mtcmos_ctrl_conn(int state)
{
	int err = 0;

	/* TINFO="enable SPM register control" */
	spm_write(POWERON_CONFIG_EN, (SPM_PROJECT_CODE << 16) | (0x1 << 0));

	if (state == STA_POWER_DOWN) {
		/* TINFO="Start to turn off CONN" */
		/* TINFO="Set bus protect" */
		spm_write(INFRA_TOPAXI_PROTECTEN_SET, CONN_PROT_BIT_MASK);
#ifndef IGNORE_MTCMOS_CHECK
		while ((spm_read(INFRA_TOPAXI_PROTECTSTA1) & CONN_PROT_BIT_ACK_MASK) != CONN_PROT_BIT_ACK_MASK) {
				/*  */
				/*  */
		}
#endif
#ifndef IGNORE_MTCMOS_CHECK
#endif
		/* TINFO="Set PWR_ISO = 1" */
		spm_write(CONN_PWR_CON, spm_read(CONN_PWR_CON) | PWR_ISO);
		/* TINFO="Set PWR_CLK_DIS = 1" */
		spm_write(CONN_PWR_CON, spm_read(CONN_PWR_CON) | PWR_CLK_DIS);
		/* TINFO="Set PWR_RST_B = 0" */
		spm_write(CONN_PWR_CON, spm_read(CONN_PWR_CON) & ~PWR_RST_B);
		/* TINFO="Set PWR_ON = 0" */
		spm_write(CONN_PWR_CON, spm_read(CONN_PWR_CON) & ~PWR_ON);
		/* TINFO="Set PWR_ON_2ND = 0" */
		spm_write(CONN_PWR_CON, spm_read(CONN_PWR_CON) & ~PWR_ON_2ND);
#ifndef IGNORE_MTCMOS_CHECK
		/* TINFO="Wait until PWR_STATUS = 0 and PWR_STATUS_2ND = 0" */
		while ((spm_read(PWR_STATUS) & CONN_PWR_STA_MASK)
		       || (spm_read(PWR_STATUS_2ND) & CONN_PWR_STA_MASK)) {
				/*  */
				/*  */
		}
#endif
		/* TINFO="Finish to turn off CONN" */
	} else {    /* STA_POWER_ON */
		/* TINFO="Start to turn on CONN" */
		/* TINFO="Set PWR_ON = 1" */
		spm_write(CONN_PWR_CON, spm_read(CONN_PWR_CON) | PWR_ON);
		/* TINFO="Set PWR_ON_2ND = 1" */
		spm_write(CONN_PWR_CON, spm_read(CONN_PWR_CON) | PWR_ON_2ND);
#ifndef IGNORE_MTCMOS_CHECK
		/* TINFO="Wait until PWR_STATUS = 1 and PWR_STATUS_2ND = 1" */
		while (((spm_read(PWR_STATUS) & CONN_PWR_STA_MASK) != CONN_PWR_STA_MASK)
		       || ((spm_read(PWR_STATUS_2ND) & CONN_PWR_STA_MASK) != CONN_PWR_STA_MASK)) {
				/*  */
				/*  */
		}
#endif
		/* TINFO="Set PWR_CLK_DIS = 0" */
		spm_write(CONN_PWR_CON, spm_read(CONN_PWR_CON) & ~PWR_CLK_DIS);
		/* TINFO="Set PWR_ISO = 0" */
		spm_write(CONN_PWR_CON, spm_read(CONN_PWR_CON) & ~PWR_ISO);
		/* TINFO="Set PWR_RST_B = 1" */
		spm_write(CONN_PWR_CON, spm_read(CONN_PWR_CON) | PWR_RST_B);
#ifndef IGNORE_MTCMOS_CHECK
#endif
		/* TINFO="Release bus protect" */
		spm_write(INFRA_TOPAXI_PROTECTEN_CLR, CONN_PROT_BIT_MASK);
#ifndef IGNORE_MTCMOS_CHECK
		/* Note that this protect ack check after releasing protect has been ignored */
#endif
		/* TINFO="Finish to turn on CONN" */
	}

	return err;
}

int spm_mtcmos_ctrl_aud(int state)
{
	int err = 0;

	/* TINFO="enable SPM register control" */
	spm_write(POWERON_CONFIG_EN, (SPM_PROJECT_CODE << 16) | (0x1 << 0));

	if (state == STA_POWER_DOWN) {
		/* TINFO="Start to turn off AUD" */
		/* TINFO="Set SRAM_PDN = 1" */
		spm_write(AUD_PWR_CON, spm_read(AUD_PWR_CON) | AUD_SRAM_PDN);
#ifndef IGNORE_MTCMOS_CHECK
		/* TINFO="Wait until AUD_SRAM_PDN_ACK = 1" */
		while ((spm_read(AUD_PWR_CON) & AUD_SRAM_PDN_ACK) != AUD_SRAM_PDN_ACK) {
				/*  */
				/*  */
		}
#endif
		/* TINFO="Set PWR_ISO = 1" */
		spm_write(AUD_PWR_CON, spm_read(AUD_PWR_CON) | PWR_ISO);
		/* TINFO="Set PWR_CLK_DIS = 1" */
		spm_write(AUD_PWR_CON, spm_read(AUD_PWR_CON) | PWR_CLK_DIS);
		/* TINFO="Set PWR_RST_B = 0" */
		spm_write(AUD_PWR_CON, spm_read(AUD_PWR_CON) & ~PWR_RST_B);
		/* TINFO="Set PWR_ON = 0" */
		spm_write(AUD_PWR_CON, spm_read(AUD_PWR_CON) & ~PWR_ON);
		/* TINFO="Set PWR_ON_2ND = 0" */
		spm_write(AUD_PWR_CON, spm_read(AUD_PWR_CON) & ~PWR_ON_2ND);
#ifndef IGNORE_MTCMOS_CHECK
		/* TINFO="Wait until PWR_STATUS = 0 and PWR_STATUS_2ND = 0" */
		while ((spm_read(PWR_STATUS) & AUD_PWR_STA_MASK)
		       || (spm_read(PWR_STATUS_2ND) & AUD_PWR_STA_MASK)) {
				/*  */
				/*  */
		}
#endif
		/* TINFO="Finish to turn off AUD" */
	} else {    /* STA_POWER_ON */
		/* TINFO="Start to turn on AUD" */
		/* TINFO="Set PWR_ON = 1" */
		spm_write(AUD_PWR_CON, spm_read(AUD_PWR_CON) | PWR_ON);
		/* TINFO="Set PWR_ON_2ND = 1" */
		spm_write(AUD_PWR_CON, spm_read(AUD_PWR_CON) | PWR_ON_2ND);
#ifndef IGNORE_MTCMOS_CHECK
		/* TINFO="Wait until PWR_STATUS = 1 and PWR_STATUS_2ND = 1" */
		while (((spm_read(PWR_STATUS) & AUD_PWR_STA_MASK) != AUD_PWR_STA_MASK)
		       || ((spm_read(PWR_STATUS_2ND) & AUD_PWR_STA_MASK) != AUD_PWR_STA_MASK)) {
				/*  */
				/*  */
		}
#endif
		/* TINFO="Set PWR_CLK_DIS = 0" */
		spm_write(AUD_PWR_CON, spm_read(AUD_PWR_CON) & ~PWR_CLK_DIS);
		/* TINFO="Set PWR_ISO = 0" */
		spm_write(AUD_PWR_CON, spm_read(AUD_PWR_CON) & ~PWR_ISO);
		/* TINFO="Set PWR_RST_B = 1" */
		spm_write(AUD_PWR_CON, spm_read(AUD_PWR_CON) | PWR_RST_B);
		/* TINFO="Set SRAM_PDN = 0" */
		spm_write(AUD_PWR_CON, spm_read(AUD_PWR_CON) & ~(0x1 << 8));
#ifndef IGNORE_MTCMOS_CHECK
		/* TINFO="Wait until AUD_SRAM_PDN_ACK_BIT0 = 0" */
		while (spm_read(AUD_PWR_CON) & AUD_SRAM_PDN_ACK_BIT0) {
				/*  */
				/*  */
		}
#endif
		spm_write(AUD_PWR_CON, spm_read(AUD_PWR_CON) & ~(0x1 << 9));
#ifndef IGNORE_MTCMOS_CHECK
		/* TINFO="Wait until AUD_SRAM_PDN_ACK_BIT1 = 0" */
		while (spm_read(AUD_PWR_CON) & AUD_SRAM_PDN_ACK_BIT1) {
				/*  */
				/*  */
		}
#endif
		spm_write(AUD_PWR_CON, spm_read(AUD_PWR_CON) & ~(0x1 << 10));
#ifndef IGNORE_MTCMOS_CHECK
		/* TINFO="Wait until AUD_SRAM_PDN_ACK_BIT2 = 0" */
		while (spm_read(AUD_PWR_CON) & AUD_SRAM_PDN_ACK_BIT2) {
				/*  */
				/*  */
		}
#endif
		spm_write(AUD_PWR_CON, spm_read(AUD_PWR_CON) & ~(0x1 << 11));
#ifndef IGNORE_MTCMOS_CHECK
		/* TINFO="Wait until AUD_SRAM_PDN_ACK_BIT3 = 0" */
		while (spm_read(AUD_PWR_CON) & AUD_SRAM_PDN_ACK_BIT3) {
				/*  */
				/*  */
		}
#endif
		/* TINFO="Finish to turn on AUD" */
	}

	return err;
}

#define MM0_PROT_BIT_MASK                ((0x1 << 0) \
					  |(0x1 << 1) \
					  |(0x1 << 4) \
					  |(0x1 << 6) \
					  |(0x1 << 8) \
					  |(0x1 << 9) \
					  |(0x1 << 12) \
					  |(0x1 << 14) \
					  |(0x1 << 15))
#define MM0_PROT_BIT_ACK_MASK            ((0x1 << 0) \
					  |(0x1 << 1) \
					  |(0x1 << 4) \
					  |(0x1 << 6) \
					  |(0x1 << 8) \
					  |(0x1 << 9) \
					  |(0x1 << 12) \
					  |(0x1 << 14) \
					  |(0x1 << 15))
#define MM0_PROT_BIT_2ND_MASK            ((0x1 << 8) \
					  |(0x1 << 9))
#define MM0_PROT_BIT_ACK_2ND_MASK        ((0x1 << 8) \
					  |(0x1 << 9))

int spm_mtcmos_ctrl_mm0(int state)
{
	int err = 0;

	/* TINFO="enable SPM register control" */
	spm_write(POWERON_CONFIG_EN, (SPM_PROJECT_CODE << 16) | (0x1 << 0));

	if (state == STA_POWER_DOWN) {
		mm0_mtcmos_before_power_off();

		/* TINFO="Start to turn off MM0" */
		/* TINFO="Set way_en = 0" */
		/*	*((UINT32P)(0x10000200)) = *((UINT32P)(0x10000200)) & (~(0x1 << 6)); */
		spm_write(INFRA_TOPAXI_SI0_CTL, spm_read(INFRA_TOPAXI_SI0_CTL) & (~(0x1 << 6)));
#if 0 /* to pass checkpatch */
#ifndef IGNORE_MTCMOS_CHECK
		/* TINFO="Polling way_en ctrl_update=1" */
		while ((*((UINT32P)(0x10260000)) & (0x1 << 24)) != (0x1 << 24)) {
				/*  */
				/*  */
		}
		/* TINFO="Polling protect_idle=1" */
		while ((*((UINT32P)(0x10000224)) & (0x1 << 18)) != (0x1 << 18)) {
				/*  */
				/*  */
		}
#endif
#endif
		/* TINFO="Set bus protect" */
		spm_write(INFRA_TOPAXI_PROTECTEN_2_SET, MM0_PROT_BIT_MASK);
#ifndef IGNORE_MTCMOS_CHECK
		while ((spm_read(INFRA_TOPAXI_PROTECTSTA1_2) & MM0_PROT_BIT_ACK_MASK) != MM0_PROT_BIT_ACK_MASK) {
				/*  */
				/*  */
		}
#endif
		/* TINFO="Set bus protect" */
		spm_write(INFRA_TOPAXI_PROTECTEN_SET, MM0_PROT_BIT_2ND_MASK);
#ifndef IGNORE_MTCMOS_CHECK
		while ((spm_read(INFRA_TOPAXI_PROTECTSTA1) & MM0_PROT_BIT_ACK_2ND_MASK) !=
					MM0_PROT_BIT_ACK_2ND_MASK) {
				/*  */
				/*  */
		}
#endif
		/* TINFO="Set SRAM_PDN = 1" */
		spm_write(MM0_PWR_CON, spm_read(MM0_PWR_CON) | MM0_SRAM_PDN);
#ifndef IGNORE_MTCMOS_CHECK
		/* TINFO="Wait until MM0_SRAM_PDN_ACK = 1" */
		while ((spm_read(MM0_PWR_CON) & MM0_SRAM_PDN_ACK) != MM0_SRAM_PDN_ACK) {
				/*  */
				/*  */
		}
#endif
		/* TINFO="Set PWR_ISO = 1" */
		spm_write(MM0_PWR_CON, spm_read(MM0_PWR_CON) | PWR_ISO);
		/* TINFO="Set PWR_CLK_DIS = 1" */
		spm_write(MM0_PWR_CON, spm_read(MM0_PWR_CON) | PWR_CLK_DIS);
		/* TINFO="Set PWR_RST_B = 0" */
		spm_write(MM0_PWR_CON, spm_read(MM0_PWR_CON) & ~PWR_RST_B);
		/* TINFO="Set PWR_ON = 0" */
		spm_write(MM0_PWR_CON, spm_read(MM0_PWR_CON) & ~PWR_ON);
		/* TINFO="Set PWR_ON_2ND = 0" */
		spm_write(MM0_PWR_CON, spm_read(MM0_PWR_CON) & ~PWR_ON_2ND);
#ifndef IGNORE_MTCMOS_CHECK
		/* TINFO="Wait until PWR_STATUS = 0 and PWR_STATUS_2ND = 0" */
		while ((spm_read(PWR_STATUS) & MM0_PWR_STA_MASK)
		       || (spm_read(PWR_STATUS_2ND) & MM0_PWR_STA_MASK)) {
				/*  */
				/*  */
		}
#endif
		/* TINFO="Finish to turn off MM0" */
	} else {    /* STA_POWER_ON */
		/* TINFO="Start to turn on MM0" */
		/* TINFO="Set PWR_ON = 1" */
		spm_write(MM0_PWR_CON, spm_read(MM0_PWR_CON) | PWR_ON);
		/* TINFO="Set PWR_ON_2ND = 1" */
		spm_write(MM0_PWR_CON, spm_read(MM0_PWR_CON) | PWR_ON_2ND);
#ifndef IGNORE_MTCMOS_CHECK
		/* TINFO="Wait until PWR_STATUS = 1 and PWR_STATUS_2ND = 1" */
		while (((spm_read(PWR_STATUS) & MM0_PWR_STA_MASK) != MM0_PWR_STA_MASK)
		       || ((spm_read(PWR_STATUS_2ND) & MM0_PWR_STA_MASK) != MM0_PWR_STA_MASK)) {
				/*  */
				/*  */
		}
#endif
		/* TINFO="Set PWR_CLK_DIS = 0" */
		spm_write(MM0_PWR_CON, spm_read(MM0_PWR_CON) & ~PWR_CLK_DIS);
		/* TINFO="Set PWR_ISO = 0" */
		spm_write(MM0_PWR_CON, spm_read(MM0_PWR_CON) & ~PWR_ISO);
		/* TINFO="Set PWR_RST_B = 1" */
		spm_write(MM0_PWR_CON, spm_read(MM0_PWR_CON) | PWR_RST_B);
		/* TINFO="Set SRAM_PDN = 0" */
		spm_write(MM0_PWR_CON, spm_read(MM0_PWR_CON) & ~(0x1 << 8));
#ifndef IGNORE_MTCMOS_CHECK
		/* TINFO="Wait until MM0_SRAM_PDN_ACK_BIT0 = 0" */
		while (spm_read(MM0_PWR_CON) & MM0_SRAM_PDN_ACK_BIT0) {
				/*  */
				/*  */
		}
#endif
		/* TINFO="Release bus protect" */
		spm_write(INFRA_TOPAXI_PROTECTEN_CLR, MM0_PROT_BIT_2ND_MASK);
#ifndef IGNORE_MTCMOS_CHECK
		/* Note that this protect ack check after releasing protect has been ignored */
#endif
		/* TINFO="Release bus protect" */
		spm_write(INFRA_TOPAXI_PROTECTEN_2_CLR, MM0_PROT_BIT_MASK);
#ifndef IGNORE_MTCMOS_CHECK
		/* Note that this protect ack check after releasing protect has been ignored */
#endif
		/* TINFO=Set way_en = 1" */
		/* *((UINT32P)(0x10000200)) = *((UINT32P)(0x10000200)) | (0x1 << 6); */
		spm_write(INFRA_TOPAXI_SI0_CTL, spm_read(INFRA_TOPAXI_SI0_CTL) | (0x1 << 6));
#if 0 /* to pass checkpatch */
#ifndef IGNORE_MTCMOS_CHECK
		/* TINFO="Polling way_en ctrl_update=1" */
		while ((*((UINT32P)(0x10260000)) & (0x1 << 24)) != (0x1 << 24)) {
				/*  */
				/*  */
		}
#endif
#endif
		/* TINFO="Finish to turn on MM0" */
		mm0_mtcmos_after_power_on();
	}

	return err;
}

#define CAM_PROT_BIT_MASK                ((0x1 << 18) \
					  |(0x1 << 8) \
					  |(0x1 << 9))
#define CAM_PROT_BIT_ACK_MASK            ((0x1 << 18) \
					  |(0x1 << 8) \
					  |(0x1 << 9))
#define CAM_PROT_BIT_2ND_MASK            ((0x1 << 19))
#define CAM_PROT_BIT_ACK_2ND_MASK        ((0x1 << 19))
#define CAM_PROT_BIT_3RD_MASK            ((0x1 << 4))
#define CAM_PROT_BIT_ACK_3RD_MASK        ((0x1 << 4))
#define CAM_PROT_BIT_3RD_2_MASK            ((0x1 << 1))
#define CAM_PROT_BIT_ACK_3RD_2_MASK        ((0x1 << 1))

int spm_mtcmos_ctrl_cam(int state)
{
	int err = 0;

	/* TINFO="enable SPM register control" */
	spm_write(POWERON_CONFIG_EN, (SPM_PROJECT_CODE << 16) | (0x1 << 0));

	if (state == STA_POWER_DOWN) {
		cam_mtcmos_before_power_off();

		/* TINFO="Start to turn off CAM" */
		/* TINFO="Set bus protect" */
		spm_write(INFRA_TOPAXI_PROTECTEN_2_SET, CAM_PROT_BIT_MASK);
#ifndef IGNORE_MTCMOS_CHECK
		while ((spm_read(INFRA_TOPAXI_PROTECTSTA1_2) & CAM_PROT_BIT_ACK_MASK) != CAM_PROT_BIT_ACK_MASK) {
				/*  */
				/*  */
		}
#endif
		/* TINFO="Set bus protect" */
		spm_write(INFRA_TOPAXI_PROTECTEN_2_SET, CAM_PROT_BIT_2ND_MASK);
#ifndef IGNORE_MTCMOS_CHECK
		while ((spm_read(INFRA_TOPAXI_PROTECTSTA1_2) & CAM_PROT_BIT_ACK_2ND_MASK)
						!= CAM_PROT_BIT_ACK_2ND_MASK) {
				/*  */
				/*  */
		}
#endif
		/* TINFO="Set bus protect" */
		spm_write(SMI_CLAMP_SET, CAM_PROT_BIT_3RD_MASK);
#ifndef IGNORE_MTCMOS_CHECK
		while ((spm_read(SMI_CLAMP) & CAM_PROT_BIT_ACK_3RD_MASK) != CAM_PROT_BIT_ACK_3RD_MASK) {
				/*  */
				/*  */
		}
#endif
		/* TINFO="Set bus protect" */
		spm_write(MMSYS_SMI_2X1_SUB_CLAMP_SET, CAM_PROT_BIT_3RD_2_MASK);
#ifndef IGNORE_MTCMOS_CHECK
		while ((spm_read(MMSYS_SMI_2X1_SUB_CLAMP) & CAM_PROT_BIT_ACK_3RD_2_MASK) !=
						CAM_PROT_BIT_ACK_3RD_2_MASK) {
				/*  */
				/*  */
		}
#endif
		/* TINFO="Set SRAM_PDN = 1" */
		spm_write(CAM_PWR_CON, spm_read(CAM_PWR_CON) | CAM_SRAM_PDN);
#ifndef IGNORE_MTCMOS_CHECK
		/* TINFO="Wait until CAM_SRAM_PDN_ACK = 1" */
		while ((spm_read(CAM_PWR_CON) & CAM_SRAM_PDN_ACK) != CAM_SRAM_PDN_ACK) {
				/*  */
				/*  */
		}
#endif
		/* TINFO="Set PWR_ISO = 1" */
		spm_write(CAM_PWR_CON, spm_read(CAM_PWR_CON) | PWR_ISO);
		/* TINFO="Set PWR_CLK_DIS = 1" */
		spm_write(CAM_PWR_CON, spm_read(CAM_PWR_CON) | PWR_CLK_DIS);
		/* TINFO="Set PWR_RST_B = 0" */
		spm_write(CAM_PWR_CON, spm_read(CAM_PWR_CON) & ~PWR_RST_B);
		/* TINFO="Set PWR_ON = 0" */
		spm_write(CAM_PWR_CON, spm_read(CAM_PWR_CON) & ~PWR_ON);
		/* TINFO="Set PWR_ON_2ND = 0" */
		spm_write(CAM_PWR_CON, spm_read(CAM_PWR_CON) & ~PWR_ON_2ND);
#ifndef IGNORE_MTCMOS_CHECK
		/* TINFO="Wait until PWR_STATUS = 0 and PWR_STATUS_2ND = 0" */
		while ((spm_read(PWR_STATUS) & CAM_PWR_STA_MASK)
		       || (spm_read(PWR_STATUS_2ND) & CAM_PWR_STA_MASK)) {
				/*  */
				/*  */
		}
#endif
		mm_clk_restore();
		/* TINFO="Finish to turn off CAM" */
	} else {    /* STA_POWER_ON */
		/* TINFO="Start to turn on CAM" */
		/* TINFO="Set PWR_ON = 1" */
		spm_write(CAM_PWR_CON, spm_read(CAM_PWR_CON) | PWR_ON);
		/* TINFO="Set PWR_ON_2ND = 1" */
		spm_write(CAM_PWR_CON, spm_read(CAM_PWR_CON) | PWR_ON_2ND);
#ifndef IGNORE_MTCMOS_CHECK
		/* TINFO="Wait until PWR_STATUS = 1 and PWR_STATUS_2ND = 1" */
		while (((spm_read(PWR_STATUS) & CAM_PWR_STA_MASK) != CAM_PWR_STA_MASK)
		       || ((spm_read(PWR_STATUS_2ND) & CAM_PWR_STA_MASK) != CAM_PWR_STA_MASK)) {
				/*  */
				/*  */
		}
#endif
		/* TINFO="Set PWR_CLK_DIS = 0" */
		spm_write(CAM_PWR_CON, spm_read(CAM_PWR_CON) & ~PWR_CLK_DIS);
		/* TINFO="Set PWR_ISO = 0" */
		spm_write(CAM_PWR_CON, spm_read(CAM_PWR_CON) & ~PWR_ISO);
		/* TINFO="Set PWR_RST_B = 1" */
		spm_write(CAM_PWR_CON, spm_read(CAM_PWR_CON) | PWR_RST_B);
		/* TINFO="Set SRAM_PDN = 0" */
		spm_write(CAM_PWR_CON, spm_read(CAM_PWR_CON) & ~(0x1 << 8));
#ifndef IGNORE_MTCMOS_CHECK
		/* TINFO="Wait until CAM_SRAM_PDN_ACK_BIT0 = 0" */
		while (spm_read(CAM_PWR_CON) & CAM_SRAM_PDN_ACK_BIT0) {
				/*  */
				/*  */
		}
#endif
		spm_write(CAM_PWR_CON, spm_read(CAM_PWR_CON) & ~(0x1 << 9));
#ifndef IGNORE_MTCMOS_CHECK
		/* TINFO="Wait until CAM_SRAM_PDN_ACK_BIT1 = 0" */
		while (spm_read(CAM_PWR_CON) & CAM_SRAM_PDN_ACK_BIT1) {
				/*  */
				/*  */
		}
#endif
		/* TINFO="Release bus protect" */
		spm_write(MMSYS_SMI_2X1_SUB_CLAMP_CLR, CAM_PROT_BIT_3RD_2_MASK);
#ifndef IGNORE_MTCMOS_CHECK
		/* Note that this protect ack check after releasing protect has been ignored */
#endif
		/* TINFO="Release bus protect" */
		spm_write(SMI_CLAMP_CLR, CAM_PROT_BIT_3RD_MASK);
#ifndef IGNORE_MTCMOS_CHECK
		/* Note that this protect ack check after releasing protect has been ignored */
#endif
		/* TINFO="Release bus protect" */
		spm_write(INFRA_TOPAXI_PROTECTEN_2_CLR, CAM_PROT_BIT_2ND_MASK);
#ifndef IGNORE_MTCMOS_CHECK
		/* Note that this protect ack check after releasing protect has been ignored */
#endif
		/* TINFO="Release bus protect" */
		spm_write(INFRA_TOPAXI_PROTECTEN_2_CLR, CAM_PROT_BIT_MASK);
#ifndef IGNORE_MTCMOS_CHECK
		/* Note that this protect ack check after releasing protect has been ignored */
#endif
		/* TINFO="Finish to turn on CAM" */
		cam_mtcmos_after_power_on();
	}

	return err;
}

#define ISP_PROT_BIT_MASK                ((0x1 << 20) \
					  |(0x1 << 6))
#define ISP_PROT_BIT_ACK_MASK            ((0x1 << 20) \
					  |(0x1 << 6))
#define ISP_PROT_BIT_2ND_MASK            ((0x1 << 21))
#define ISP_PROT_BIT_ACK_2ND_MASK        ((0x1 << 21))
#define ISP_PROT_BIT_3RD_MASK            ((0x1 << 3))
#define ISP_PROT_BIT_ACK_3RD_MASK        ((0x1 << 3))

int spm_mtcmos_ctrl_isp(int state)
{
	int err = 0;

	/* TINFO="enable SPM register control" */
	spm_write(POWERON_CONFIG_EN, (SPM_PROJECT_CODE << 16) | (0x1 << 0));

	if (state == STA_POWER_DOWN) {
		isp_mtcmos_before_power_off();

		/* TINFO="Start to turn off ISP" */
		/* TINFO="Set bus protect" */
		spm_write(INFRA_TOPAXI_PROTECTEN_2_SET, ISP_PROT_BIT_MASK);
#ifndef IGNORE_MTCMOS_CHECK
		while ((spm_read(INFRA_TOPAXI_PROTECTSTA1_2) & ISP_PROT_BIT_ACK_MASK) != ISP_PROT_BIT_ACK_MASK) {
				/*  */
				/*  */
		}
#endif
		/* TINFO="Set bus protect" */
		spm_write(INFRA_TOPAXI_PROTECTEN_2_SET, ISP_PROT_BIT_2ND_MASK);
#ifndef IGNORE_MTCMOS_CHECK
		while ((spm_read(INFRA_TOPAXI_PROTECTSTA1_2) & ISP_PROT_BIT_ACK_2ND_MASK) !=
						ISP_PROT_BIT_ACK_2ND_MASK) {
				/*  */
				/*  */
		}
#endif
		/* TINFO="Set bus protect" */
		spm_write(SMI_CLAMP_SET, ISP_PROT_BIT_3RD_MASK);
#ifndef IGNORE_MTCMOS_CHECK
		while ((spm_read(SMI_CLAMP) & ISP_PROT_BIT_ACK_3RD_MASK) != ISP_PROT_BIT_ACK_3RD_MASK) {
				/*  */
				/*  */
		}
#endif
		/* TINFO="Set SRAM_PDN = 1" */
		spm_write(ISP_PWR_CON, spm_read(ISP_PWR_CON) | ISP_SRAM_PDN);
#ifndef IGNORE_MTCMOS_CHECK
		/* TINFO="Wait until ISP_SRAM_PDN_ACK = 1" */
		while ((spm_read(ISP_PWR_CON) & ISP_SRAM_PDN_ACK) != ISP_SRAM_PDN_ACK) {
				/*  */
				/*  */
		}
#endif
		/* TINFO="Set PWR_ISO = 1" */
		spm_write(ISP_PWR_CON, spm_read(ISP_PWR_CON) | PWR_ISO);
		/* TINFO="Set PWR_CLK_DIS = 1" */
		spm_write(ISP_PWR_CON, spm_read(ISP_PWR_CON) | PWR_CLK_DIS);
		/* TINFO="Set PWR_RST_B = 0" */
		spm_write(ISP_PWR_CON, spm_read(ISP_PWR_CON) & ~PWR_RST_B);
		/* TINFO="Set PWR_ON = 0" */
		spm_write(ISP_PWR_CON, spm_read(ISP_PWR_CON) & ~PWR_ON);
		/* TINFO="Set PWR_ON_2ND = 0" */
		spm_write(ISP_PWR_CON, spm_read(ISP_PWR_CON) & ~PWR_ON_2ND);
#ifndef IGNORE_MTCMOS_CHECK
		/* TINFO="Wait until PWR_STATUS = 0 and PWR_STATUS_2ND = 0" */
		while ((spm_read(PWR_STATUS) & ISP_PWR_STA_MASK)
		       || (spm_read(PWR_STATUS_2ND) & ISP_PWR_STA_MASK)) {
				/*  */
				/*  */
		}
#endif
		/* TINFO="Finish to turn off ISP" */
		mm_clk_restore();
	} else {    /* STA_POWER_ON */
		/* TINFO="Start to turn on ISP" */
		/* TINFO="Set PWR_ON = 1" */
		spm_write(ISP_PWR_CON, spm_read(ISP_PWR_CON) | PWR_ON);
		/* TINFO="Set PWR_ON_2ND = 1" */
		spm_write(ISP_PWR_CON, spm_read(ISP_PWR_CON) | PWR_ON_2ND);
#ifndef IGNORE_MTCMOS_CHECK
		/* TINFO="Wait until PWR_STATUS = 1 and PWR_STATUS_2ND = 1" */
		while (((spm_read(PWR_STATUS) & ISP_PWR_STA_MASK) != ISP_PWR_STA_MASK)
		       || ((spm_read(PWR_STATUS_2ND) & ISP_PWR_STA_MASK) != ISP_PWR_STA_MASK)) {
				/*  */
				/*  */
		}
#endif
		/* TINFO="Set PWR_CLK_DIS = 0" */
		spm_write(ISP_PWR_CON, spm_read(ISP_PWR_CON) & ~PWR_CLK_DIS);
		/* TINFO="Set PWR_ISO = 0" */
		spm_write(ISP_PWR_CON, spm_read(ISP_PWR_CON) & ~PWR_ISO);
		/* TINFO="Set PWR_RST_B = 1" */
		spm_write(ISP_PWR_CON, spm_read(ISP_PWR_CON) | PWR_RST_B);
		/* TINFO="Set SRAM_PDN = 0" */
		spm_write(ISP_PWR_CON, spm_read(ISP_PWR_CON) & ~(0x1 << 8));
#ifndef IGNORE_MTCMOS_CHECK
		/* TINFO="Wait until ISP_SRAM_PDN_ACK_BIT0 = 0" */
		while (spm_read(ISP_PWR_CON) & ISP_SRAM_PDN_ACK_BIT0) {
				/*  */
				/*  */
		}
#endif
		spm_write(ISP_PWR_CON, spm_read(ISP_PWR_CON) & ~(0x1 << 9));
#ifndef IGNORE_MTCMOS_CHECK
		/* TINFO="Wait until ISP_SRAM_PDN_ACK_BIT1 = 0" */
		while (spm_read(ISP_PWR_CON) & ISP_SRAM_PDN_ACK_BIT1) {
				/*  */
				/*  */
		}
#endif
		/* TINFO="Release bus protect" */
		spm_write(SMI_CLAMP_CLR, ISP_PROT_BIT_3RD_MASK);
#ifndef IGNORE_MTCMOS_CHECK
		/* Note that this protect ack check after releasing protect has been ignored */
#endif
		/* TINFO="Release bus protect" */
		spm_write(INFRA_TOPAXI_PROTECTEN_2_CLR, ISP_PROT_BIT_2ND_MASK);
#ifndef IGNORE_MTCMOS_CHECK
		/* Note that this protect ack check after releasing protect has been ignored */
#endif
		/* TINFO="Release bus protect" */
		spm_write(INFRA_TOPAXI_PROTECTEN_2_CLR, ISP_PROT_BIT_MASK);
#ifndef IGNORE_MTCMOS_CHECK
		/* Note that this protect ack check after releasing protect has been ignored */
#endif
		/* TINFO="Finish to turn on ISP" */
		isp_mtcmos_after_power_on();
	}

	return err;
}

#define VEN_PROT_BIT_MASK                ((0x1 << 12))
#define VEN_PROT_BIT_ACK_MASK            ((0x1 << 12))
#define VEN_PROT_BIT_2ND_MASK            ((0x1 << 5))
#define VEN_PROT_BIT_ACK_2ND_MASK        ((0x1 << 5))

int spm_mtcmos_ctrl_ven(int state)
{
	int err = 0;

	/* TINFO="enable SPM register control" */
	spm_write(POWERON_CONFIG_EN, (SPM_PROJECT_CODE << 16) | (0x1 << 0));

	if (state == STA_POWER_DOWN) {
		ven_mtcmos_patch();

		/* TINFO="Start to turn off VEN" */
		/* TINFO="Set bus protect" */
		spm_write(INFRA_TOPAXI_PROTECTEN_2_SET, VEN_PROT_BIT_MASK);
#ifndef IGNORE_MTCMOS_CHECK
		while ((spm_read(INFRA_TOPAXI_PROTECTSTA1_2) & VEN_PROT_BIT_ACK_MASK) != VEN_PROT_BIT_ACK_MASK) {
				/*  */
				/*  */
		}
#endif
		/* TINFO="Set bus protect" */
		spm_write(SMI_CLAMP_SET, VEN_PROT_BIT_2ND_MASK);
#ifndef IGNORE_MTCMOS_CHECK
		while ((spm_read(SMI_CLAMP) & VEN_PROT_BIT_ACK_2ND_MASK) != VEN_PROT_BIT_ACK_2ND_MASK) {
				/*  */
				/*  */
		}
#endif
		/* TINFO="Set SRAM_PDN = 1" */
		spm_write(VEN_PWR_CON, spm_read(VEN_PWR_CON) | VEN_SRAM_PDN);
#ifndef IGNORE_MTCMOS_CHECK
		/* TINFO="Wait until VEN_SRAM_PDN_ACK = 1" */
		while ((spm_read(VEN_PWR_CON) & VEN_SRAM_PDN_ACK) != VEN_SRAM_PDN_ACK) {
				/*  */
				/*  */
		}
#endif
		/* TINFO="Set PWR_ISO = 1" */
		spm_write(VEN_PWR_CON, spm_read(VEN_PWR_CON) | PWR_ISO);
		/* TINFO="Set PWR_CLK_DIS = 1" */
		spm_write(VEN_PWR_CON, spm_read(VEN_PWR_CON) | PWR_CLK_DIS);
		/* TINFO="Set PWR_RST_B = 0" */
		spm_write(VEN_PWR_CON, spm_read(VEN_PWR_CON) & ~PWR_RST_B);
		/* TINFO="Set PWR_ON = 0" */
		spm_write(VEN_PWR_CON, spm_read(VEN_PWR_CON) & ~PWR_ON);
		/* TINFO="Set PWR_ON_2ND = 0" */
		spm_write(VEN_PWR_CON, spm_read(VEN_PWR_CON) & ~PWR_ON_2ND);
#ifndef IGNORE_MTCMOS_CHECK
		/* TINFO="Wait until PWR_STATUS = 0 and PWR_STATUS_2ND = 0" */
		while ((spm_read(PWR_STATUS) & VEN_PWR_STA_MASK)
		       || (spm_read(PWR_STATUS_2ND) & VEN_PWR_STA_MASK)) {
				/*  */
				/*  */
		}
#endif
		/* TINFO="Finish to turn off VEN" */
		mm_clk_restore();
	} else {    /* STA_POWER_ON */
		/* TINFO="Start to turn on VEN" */
		/* TINFO="Set PWR_ON = 1" */
		spm_write(VEN_PWR_CON, spm_read(VEN_PWR_CON) | PWR_ON);
		/* TINFO="Set PWR_ON_2ND = 1" */
		spm_write(VEN_PWR_CON, spm_read(VEN_PWR_CON) | PWR_ON_2ND);
#ifndef IGNORE_MTCMOS_CHECK
		/* TINFO="Wait until PWR_STATUS = 1 and PWR_STATUS_2ND = 1" */
		while (((spm_read(PWR_STATUS) & VEN_PWR_STA_MASK) != VEN_PWR_STA_MASK)
		       || ((spm_read(PWR_STATUS_2ND) & VEN_PWR_STA_MASK) != VEN_PWR_STA_MASK)) {
				/*  */
				/*  */
		}
#endif
		/* TINFO="Set PWR_CLK_DIS = 0" */
		spm_write(VEN_PWR_CON, spm_read(VEN_PWR_CON) & ~PWR_CLK_DIS);
		/* TINFO="Set PWR_ISO = 0" */
		spm_write(VEN_PWR_CON, spm_read(VEN_PWR_CON) & ~PWR_ISO);
		/* TINFO="Set PWR_RST_B = 1" */
		spm_write(VEN_PWR_CON, spm_read(VEN_PWR_CON) | PWR_RST_B);
		/* TINFO="Set SRAM_PDN = 0" */
		spm_write(VEN_PWR_CON, spm_read(VEN_PWR_CON) & ~(0x1 << 8));
#ifndef IGNORE_MTCMOS_CHECK
		/* TINFO="Wait until VEN_SRAM_PDN_ACK_BIT0 = 0" */
		while (spm_read(VEN_PWR_CON) & VEN_SRAM_PDN_ACK_BIT0) {
				/*  */
				/*  */
		}
#endif
		spm_write(VEN_PWR_CON, spm_read(VEN_PWR_CON) & ~(0x1 << 9));
#ifndef IGNORE_MTCMOS_CHECK
		/* TINFO="Wait until VEN_SRAM_PDN_ACK_BIT1 = 0" */
		while (spm_read(VEN_PWR_CON) & VEN_SRAM_PDN_ACK_BIT1) {
				/*  */
				/*  */
		}
#endif
		spm_write(VEN_PWR_CON, spm_read(VEN_PWR_CON) & ~(0x1 << 10));
#ifndef IGNORE_MTCMOS_CHECK
		/* TINFO="Wait until VEN_SRAM_PDN_ACK_BIT2 = 0" */
		while (spm_read(VEN_PWR_CON) & VEN_SRAM_PDN_ACK_BIT2) {
				/*  */
				/*  */
		}
#endif
		spm_write(VEN_PWR_CON, spm_read(VEN_PWR_CON) & ~(0x1 << 11));
#ifndef IGNORE_MTCMOS_CHECK
		/* TINFO="Wait until VEN_SRAM_PDN_ACK_BIT3 = 0" */
		while (spm_read(VEN_PWR_CON) & VEN_SRAM_PDN_ACK_BIT3) {
				/*  */
				/*  */
		}
#endif
		/* TINFO="Release bus protect" */
		spm_write(SMI_CLAMP_CLR, VEN_PROT_BIT_2ND_MASK);
#ifndef IGNORE_MTCMOS_CHECK
		/* Note that this protect ack check after releasing protect has been ignored */
#endif
		/* TINFO="Release bus protect" */
		spm_write(INFRA_TOPAXI_PROTECTEN_2_CLR, VEN_PROT_BIT_MASK);
#ifndef IGNORE_MTCMOS_CHECK
		/* Note that this protect ack check after releasing protect has been ignored */
#endif
		/* TINFO="Finish to turn on VEN" */
		ven_mtcmos_patch();
	}

	return err;
}

#define VDE_PROT_BIT_MASK                ((0x1 << 4))
#define VDE_PROT_BIT_ACK_MASK            ((0x1 << 4))
#define VDE_PROT_BIT_2ND_MASK            ((0x1 << 0))
#define VDE_PROT_BIT_ACK_2ND_MASK        ((0x1 << 0))
int spm_mtcmos_ctrl_vde(int state)
{
	int err = 0;

	/* TINFO="enable SPM register control" */
	spm_write(POWERON_CONFIG_EN, (SPM_PROJECT_CODE << 16) | (0x1 << 0));

	if (state == STA_POWER_DOWN) {
		vde_mtcmos_patch();
		/* TINFO="Start to turn off VDE" */
		/* TINFO="Set bus protect" */
		spm_write(INFRA_TOPAXI_PROTECTEN_2_SET, VDE_PROT_BIT_MASK);
#ifndef IGNORE_MTCMOS_CHECK
		while ((spm_read(INFRA_TOPAXI_PROTECTSTA1_2) & VDE_PROT_BIT_ACK_MASK) != VDE_PROT_BIT_ACK_MASK) {
				/*  */
				/*  */
		}
#endif
		/* TINFO="Set bus protect" */
		spm_write(MMSYS_SMI_2X1_SUB_CLAMP_SET, VDE_PROT_BIT_2ND_MASK);
#ifndef IGNORE_MTCMOS_CHECK
		while ((spm_read(MMSYS_SMI_2X1_SUB_CLAMP) & VDE_PROT_BIT_ACK_2ND_MASK) != VDE_PROT_BIT_ACK_2ND_MASK) {
				/*  */
				/*  */
		}
#endif
		/* TINFO="Set SRAM_PDN = 1" */
		spm_write(VDE_PWR_CON, spm_read(VDE_PWR_CON) | VDE_SRAM_PDN);
#ifndef IGNORE_MTCMOS_CHECK
		/* TINFO="Wait until VDE_SRAM_PDN_ACK = 1" */
		while ((spm_read(VDE_PWR_CON) & VDE_SRAM_PDN_ACK) != VDE_SRAM_PDN_ACK) {
				/*  */
				/*  */
		}
#endif
		/* TINFO="Set PWR_ISO = 1" */
		spm_write(VDE_PWR_CON, spm_read(VDE_PWR_CON) | PWR_ISO);
		/* TINFO="Set PWR_CLK_DIS = 1" */
		spm_write(VDE_PWR_CON, spm_read(VDE_PWR_CON) | PWR_CLK_DIS);
		/* TINFO="Set PWR_RST_B = 0" */
		spm_write(VDE_PWR_CON, spm_read(VDE_PWR_CON) & ~PWR_RST_B);
		/* TINFO="Set PWR_ON = 0" */
		spm_write(VDE_PWR_CON, spm_read(VDE_PWR_CON) & ~PWR_ON);
		/* TINFO="Set PWR_ON_2ND = 0" */
		spm_write(VDE_PWR_CON, spm_read(VDE_PWR_CON) & ~PWR_ON_2ND);
#ifndef IGNORE_MTCMOS_CHECK
		/* TINFO="Wait until PWR_STATUS = 0 and PWR_STATUS_2ND = 0" */
		while ((spm_read(PWR_STATUS) & VDE_PWR_STA_MASK)
		       || (spm_read(PWR_STATUS_2ND) & VDE_PWR_STA_MASK)) {
				/*  */
				/*  */
		}
#endif
		/* TINFO="Finish to turn off VDE" */

		mm_clk_restore();

	} else {    /* STA_POWER_ON */
		/* TINFO="Start to turn on VDE" */
		/* TINFO="Set PWR_ON = 1" */
		spm_write(VDE_PWR_CON, spm_read(VDE_PWR_CON) | PWR_ON);
		/* TINFO="Set PWR_ON_2ND = 1" */
		spm_write(VDE_PWR_CON, spm_read(VDE_PWR_CON) | PWR_ON_2ND);
#ifndef IGNORE_MTCMOS_CHECK
		/* TINFO="Wait until PWR_STATUS = 1 and PWR_STATUS_2ND = 1" */
		while (((spm_read(PWR_STATUS) & VDE_PWR_STA_MASK) != VDE_PWR_STA_MASK)
		       || ((spm_read(PWR_STATUS_2ND) & VDE_PWR_STA_MASK) != VDE_PWR_STA_MASK)) {
				/*  */
				/*  */
		}
#endif
		/* TINFO="Set PWR_CLK_DIS = 0" */
		spm_write(VDE_PWR_CON, spm_read(VDE_PWR_CON) & ~PWR_CLK_DIS);
		/* TINFO="Set PWR_ISO = 0" */
		spm_write(VDE_PWR_CON, spm_read(VDE_PWR_CON) & ~PWR_ISO);
		/* TINFO="Set PWR_RST_B = 1" */
		spm_write(VDE_PWR_CON, spm_read(VDE_PWR_CON) | PWR_RST_B);
		/* TINFO="Set SRAM_PDN = 0" */
		spm_write(VDE_PWR_CON, spm_read(VDE_PWR_CON) & ~(0x1 << 8));
#ifndef IGNORE_MTCMOS_CHECK
		/* TINFO="Wait until VDE_SRAM_PDN_ACK_BIT0 = 0" */
		while (spm_read(VDE_PWR_CON) & VDE_SRAM_PDN_ACK_BIT0) {
				/*  */
				/*  */
		}
#endif
		/* TINFO="Release bus protect" */
		spm_write(MMSYS_SMI_2X1_SUB_CLAMP_CLR, VDE_PROT_BIT_2ND_MASK);
#ifndef IGNORE_MTCMOS_CHECK
		/* Note that this protect ack check after releasing protect has been ignored */
#endif
		/* TINFO="Release bus protect" */
		spm_write(INFRA_TOPAXI_PROTECTEN_2_CLR, VDE_PROT_BIT_MASK);
#ifndef IGNORE_MTCMOS_CHECK
		/* Note that this protect ack check after releasing protect has been ignored */
#endif
		/* TINFO="Finish to turn on VDE" */
		vde_mtcmos_patch();
	}

	return err;
}


int spm_mtcmos_ctrl_ipu_vcore_shut_down(int state)
{
	int err = 0;

	/* TINFO="enable SPM register control" */
	spm_write(POWERON_CONFIG_EN, (SPM_PROJECT_CODE << 16) | (0x1 << 0));

	if (state == STA_POWER_DOWN) {
		/* TINFO="Start to turn off IPU_VCORE" */
		/* TINFO="Set PWR_ISO = 1" */
		spm_write(IPU_VCORE_PWR_CON, spm_read(IPU_VCORE_PWR_CON) | PWR_ISO);
		/* TINFO="Set PWR_CLK_DIS = 1" */
		spm_write(IPU_VCORE_PWR_CON, spm_read(IPU_VCORE_PWR_CON) | PWR_CLK_DIS);
		/* TINFO="Set PWR_RST_B = 0" */
		spm_write(IPU_VCORE_PWR_CON, spm_read(IPU_VCORE_PWR_CON) & ~PWR_RST_B);
#if 0 /* to pass checkpatch */
		default on /* TINFO="Set PWR_ON = 0" */
		default on spm_write(IPU_VCORE_PWR_CON, spm_read(IPU_VCORE_PWR_CON) & ~PWR_ON);
		default on /* TINFO="Set PWR_ON_2ND = 0" */
		default on spm_write(IPU_VCORE_PWR_CON, spm_read(IPU_VCORE_PWR_CON) & ~PWR_ON_2ND);
#ifndef IGNORE_MTCMOS_CHECK
		default on /* TINFO="Wait until IPU_VCORE_PWR_ACK = 0 and IPU_VCORE_PWR_ACK_2ND = 0" */
		default on while ((spm_read(IPU_PWR_CON) & IPU_VCORE_PWR_ACK_STA_MASK)
		default on        || (spm_read(IPU_PWR_CON) & IPU_VCORE_PWR_ACK_2ND_STA_MASK)) {
		default on		/*  */
		default on }
#endif
#endif
		/* TINFO="Finish to turn off IPU_VCORE" */
	} else {    /* STA_POWER_ON */
		/* TINFO="Start to turn on IPU_VCORE" */
#if 0 /* to pass checkpatch */
		default on /* TINFO="Set PWR_ON = 1" */
		default on spm_write(IPU_VCORE_PWR_CON, spm_read(IPU_VCORE_PWR_CON) | PWR_ON);
		default on /* TINFO="Set PWR_ON_2ND = 1" */
		default on spm_write(IPU_VCORE_PWR_CON, spm_read(IPU_VCORE_PWR_CON) | PWR_ON_2ND);
#ifndef IGNORE_MTCMOS_CHECK
		default on /* TINFO="Wait until IPU_VCORE_PWR_ACK = 1 and IPU_VCORE_PWR_ACK_2ND = 1" */
		default on while (((spm_read(IPU_PWR_CON) & IPU_VCORE_PWR_ACK_STA_MASK) != IPU_VCORE_PWR_ACK_STA_MASK)
		default on        || ((spm_read(IPU_PWR_CON) & IPU_VCORE_PWR_ACK_2ND_STA_MASK) !=
		default on						IPU_VCORE_PWR_ACK_2ND_STA_MASK)) {
		default on		/*  */
		default on }
#endif
#endif
		/* TINFO="Set PWR_CLK_DIS = 0" */
		spm_write(IPU_VCORE_PWR_CON, spm_read(IPU_VCORE_PWR_CON) & ~PWR_CLK_DIS);
		/* TINFO="Set PWR_ISO = 0" */
		spm_write(IPU_VCORE_PWR_CON, spm_read(IPU_VCORE_PWR_CON) & ~PWR_ISO);
		/* TINFO="Set PWR_RST_B = 1" */
		spm_write(IPU_VCORE_PWR_CON, spm_read(IPU_VCORE_PWR_CON) | PWR_RST_B);
		/* TINFO="Finish to turn on IPU_VCORE" */
	}

	return err;
}


#define IPU_PROT_BIT_MASK                ((0x1 << 14) \
					  |(0x1 << 15) \
					  |(0x1 << 16) \
					  |(0x1 << 18) \
					  |(0x1 << 20))
#define IPU_PROT_BIT_ACK_MASK            ((0x1 << 14) \
					  |(0x1 << 15) \
					  |(0x1 << 16) \
					  |(0x1 << 18) \
					  |(0x1 << 20))
#define IPU_PROT_BIT_2ND_MASK            ((0x1 << 19) \
					  |(0x1 << 21))
#define IPU_PROT_BIT_ACK_2ND_MASK        ((0x1 << 19) \
					  |(0x1 << 21))
#define IPU_PROT_BIT_3RD_MASK            ((0x1 << 7))
#define IPU_PROT_BIT_ACK_3RD_MASK        ((0x1 << 7))
#define IPU_PROT_BIT_3RD_2_MASK            ((0x1 << 6) \
					  |(0x1 << 7))
#define IPU_PROT_BIT_ACK_3RD_2_MASK        ((0x1 << 6) \
					  |(0x1 << 7))
int spm_mtcmos_ctrl_ipu_shut_down(int state)
{
	int err = 0;

	/* TINFO="enable SPM register control" */
	spm_write(POWERON_CONFIG_EN, (SPM_PROJECT_CODE << 16) | (0x1 << 0));

	if (state == STA_POWER_DOWN) {
		ipu_mtcmos_before_power_off();

		/* TINFO="Start to turn off IPU" */
		/* TINFO="Set bus protect" */
		spm_write(INFRA_TOPAXI_PROTECTEN_2_SET, IPU_PROT_BIT_MASK);
#ifndef IGNORE_MTCMOS_CHECK
		while ((spm_read(INFRA_TOPAXI_PROTECTSTA1_2) & IPU_PROT_BIT_ACK_MASK) != IPU_PROT_BIT_ACK_MASK) {
				/*  */
				/*  */
		}
#endif

		/* TINFO="Set bus protect" */
		spm_write(INFRA_TOPAXI_PROTECTEN_2_SET, IPU_PROT_BIT_2ND_MASK);
#ifndef IGNORE_MTCMOS_CHECK
		while ((spm_read(INFRA_TOPAXI_PROTECTSTA1_2) & IPU_PROT_BIT_ACK_2ND_MASK) !=
						IPU_PROT_BIT_ACK_2ND_MASK) {
				/*  */
				/*  */
		}
#endif

		/* TINFO="Set bus protect" */
		spm_write(INFRA_TOPAXI_PROTECTEN_SET, IPU_PROT_BIT_3RD_MASK);
#ifndef IGNORE_MTCMOS_CHECK
		while ((spm_read(INFRA_TOPAXI_PROTECTSTA1) & IPU_PROT_BIT_ACK_3RD_MASK) !=
						IPU_PROT_BIT_ACK_3RD_MASK) {
				/*  */
				/*  */
		}
#endif
		/* TINFO="Set bus protect" */
		spm_write(SMI_CLAMP_SET, IPU_PROT_BIT_3RD_2_MASK);
#ifndef IGNORE_MTCMOS_CHECK
		while ((spm_read(SMI_CLAMP) & IPU_PROT_BIT_ACK_3RD_2_MASK) != IPU_PROT_BIT_ACK_3RD_2_MASK) {
				/*  */
				/*  */
		}
#endif
		/* TINFO="Set SRAM_PDN = 1" */
		spm_write(IPU_PWR_CON, spm_read(IPU_PWR_CON) | IPU_SRAM_PDN);
#ifndef IGNORE_MTCMOS_CHECK
		/* TINFO="Wait until IPU_SRAM_PDN_ACK = 1" */
		while ((spm_read(IPU_PWR_CON) & IPU_SRAM_PDN_ACK) != IPU_SRAM_PDN_ACK) {
				/*  */
				/*  */
		}
#endif
		/* TINFO="Set PWR_ISO = 1" */
		spm_write(IPU_PWR_CON, spm_read(IPU_PWR_CON) | PWR_ISO);
		/* TINFO="Set PWR_CLK_DIS = 1" */
		spm_write(IPU_PWR_CON, spm_read(IPU_PWR_CON) | PWR_CLK_DIS);
		/* TINFO="Set PWR_RST_B = 0" */
		spm_write(IPU_PWR_CON, spm_read(IPU_PWR_CON) & ~PWR_RST_B);
		/* TINFO="Set PWR_ON = 0" */
		spm_write(IPU_PWR_CON, spm_read(IPU_PWR_CON) & ~PWR_ON);
		/* TINFO="Set PWR_ON_2ND = 0" */
		spm_write(IPU_PWR_CON, spm_read(IPU_PWR_CON) & ~PWR_ON_2ND);
#ifndef IGNORE_MTCMOS_CHECK
		/* TINFO="Wait until PWR_STATUS = 0 and PWR_STATUS_2ND = 0" */
		while ((spm_read(PWR_STATUS) & IPU_PWR_STA_MASK)
		       || (spm_read(PWR_STATUS_2ND) & IPU_PWR_STA_MASK)) {
				/*  */
				/*  */
		}
#endif
		/* TINFO="EXT_BUCK_ISO[2]=1"*/
		spm_write(EXT_BUCK_ISO, spm_read(EXT_BUCK_ISO) | (0x1 << 2));
		/* TINFO="Finish to turn off IPU" */

		mm_clk_restore();
	} else {    /* STA_POWER_ON */
		/* TINFO="Start to turn on IPU" */
		/* TINFO="EXT_BUCK_ISO[2]=0"*/
		spm_write(EXT_BUCK_ISO, spm_read(EXT_BUCK_ISO) & ~(0x1 << 2));
		/* TINFO="Set PWR_ON = 1" */
		spm_write(IPU_PWR_CON, spm_read(IPU_PWR_CON) | PWR_ON);
		/* TINFO="Set PWR_ON_2ND = 1" */
		spm_write(IPU_PWR_CON, spm_read(IPU_PWR_CON) | PWR_ON_2ND);
#ifndef IGNORE_MTCMOS_CHECK
		/* TINFO="Wait until PWR_STATUS = 1 and PWR_STATUS_2ND = 1" */
		while (((spm_read(PWR_STATUS) & IPU_PWR_STA_MASK) != IPU_PWR_STA_MASK)
		       || ((spm_read(PWR_STATUS_2ND) & IPU_PWR_STA_MASK) != IPU_PWR_STA_MASK)) {
				/*  */
				/*  */
		}
#endif
		/* TINFO="Set PWR_CLK_DIS = 0" */
		spm_write(IPU_PWR_CON, spm_read(IPU_PWR_CON) & ~PWR_CLK_DIS);
		/* TINFO="Set PWR_ISO = 0" */
		spm_write(IPU_PWR_CON, spm_read(IPU_PWR_CON) & ~PWR_ISO);
		/* TINFO="Set PWR_RST_B = 1" */
		spm_write(IPU_PWR_CON, spm_read(IPU_PWR_CON) | PWR_RST_B);
		/* TINFO="Set SRAM_PDN = 0" */
		spm_write(IPU_PWR_CON, spm_read(IPU_PWR_CON) & ~(0x1 << 8));
#ifndef IGNORE_MTCMOS_CHECK
		/* TINFO="Wait until IPU_SRAM_PDN_ACK_BIT0 = 0" */
		while (spm_read(IPU_PWR_CON) & IPU_SRAM_PDN_ACK_BIT0) {
				/*  */
				/*  */
		}
#endif
		/* TINFO="Release bus protect" */
		spm_write(SMI_CLAMP_CLR, IPU_PROT_BIT_3RD_2_MASK);
#ifndef IGNORE_MTCMOS_CHECK
		/* Note that this protect ack check after releasing protect has been ignored */
#endif
		/* TINFO="Release bus protect" */
		spm_write(INFRA_TOPAXI_PROTECTEN_CLR, IPU_PROT_BIT_3RD_MASK);
#ifndef IGNORE_MTCMOS_CHECK
		/* Note that this protect ack check after releasing protect has been ignored */
#endif
		/* TINFO="Release bus protect" */
		spm_write(INFRA_TOPAXI_PROTECTEN_2_CLR, IPU_PROT_BIT_2ND_MASK);
#ifndef IGNORE_MTCMOS_CHECK
		/* Note that this protect ack check after releasing protect has been ignored */
#endif
		/* TINFO="Release bus protect" */
		spm_write(INFRA_TOPAXI_PROTECTEN_2_CLR, IPU_PROT_BIT_MASK);
#ifndef IGNORE_MTCMOS_CHECK
		/* Note that this protect ack check after releasing protect has been ignored */
#endif
		/* TINFO="Finish to turn on IPU" */
		ipu_mtcmos_after_power_on();
	}

	return err;
}

#define IPU_CORE0_PROT_BIT_2ND_MASK			((0x1<<1) \
							|(0x1<<9) \
							|(0x1<<17))
#define IPU_CORE0_PROT_BIT_ACK_2ND_MASK			(0x7<<16)

#define IPU_CORE1_PROT_BIT_2ND_MASK			((0x2<<1) \
							|(0x2<<9) \
							|(0x2<<17))
#define IPU_CORE1_PROT_BIT_ACK_2ND_MASK			(0x7<<16)

#define IPU_CORE2_PROT_BIT_2ND_MASK			((0x4<<1) \
							|(0x4<<9) \
							|(0x4<<17))
#define IPU_CORE2_PROT_BIT_ACK_2ND_MASK			(0x7<<16)

#define IPU_CORE0_PROT_BIT_MASK				((0x1 << 2) \
							|(0x1 << 3) \
							|(0x1 << 5))
#define IPU_CORE0_PROT_BIT_ACK_MASK			((0x1 << 2) \
							|(0x1 << 3) \
							|(0x1 << 5))

#define IPU_CORE1_PROT_BIT_MASK				((0x1 << 7) \
							|(0x1 << 11) \
							|(0x1 << 13))
#define IPU_CORE1_PROT_BIT_ACK_MASK			((0x1 << 7) \
							|(0x1 << 11) \
							|(0x1 << 13))

#define IPU_CORE2_PROT_BIT_MASK				((0x1 << 17) \
							|(0x1 << 22) \
							|(0x1 << 23))
#define IPU_CORE2_PROT_BIT_ACK_MASK			((0x1 << 17) \
							|(0x1 << 22) \
							|(0x1 << 23))


#define IPU_CORE0_SRAM_SLEEP_B               (0x3 << 12)
#define IPU_CORE0_SRAM_SLEEP_B_BIT0          (0x1 << 12)
#define IPU_CORE0_SRAM_SLEEP_B_BIT1          (0x1 << 13)
#define IPU_CORE0_SRAM_SLEEP_B_ACK           (0x3 << 28)
#define IPU_CORE0_SRAM_SLEEP_B_ACK_BIT0      (0x1 << 28)
#define IPU_CORE0_SRAM_SLEEP_B_ACK_BIT1      (0x1 << 29)
#define IPU_CORE1_SRAM_SLEEP_B               (0x3 << 12)
#define IPU_CORE1_SRAM_SLEEP_B_BIT0          (0x1 << 12)
#define IPU_CORE1_SRAM_SLEEP_B_BIT1          (0x1 << 13)
#define IPU_CORE1_SRAM_SLEEP_B_ACK           (0x3 << 28)
#define IPU_CORE1_SRAM_SLEEP_B_ACK_BIT0      (0x1 << 28)
#define IPU_CORE1_SRAM_SLEEP_B_ACK_BIT1      (0x1 << 29)
#define IPU_CORE2_SRAM_SLEEP_B               (0x3 << 12)
#define IPU_CORE2_SRAM_SLEEP_B_BIT0          (0x1 << 12)
#define IPU_CORE2_SRAM_SLEEP_B_BIT1          (0x1 << 13)
#define IPU_CORE2_SRAM_SLEEP_B_ACK           (0x3 << 28)
#define IPU_CORE2_SRAM_SLEEP_B_ACK_BIT0      (0x1 << 28)
#define IPU_CORE2_SRAM_SLEEP_B_ACK_BIT1      (0x1 << 29)

/* Define Non-CPU SRAM Mask */
int spm_mtcmos_ctrl_ipu_core0_shut_down(int state)
{
	int err = 0;

	/* TINFO="enable SPM register control" */
	spm_write(POWERON_CONFIG_EN, (SPM_PROJECT_CODE << 16) | (0x1 << 0));

	if (state == STA_POWER_DOWN) {
		ipu_mtcmos_before_power_off();
		ipu_core_mtcmos_before_power_off();

		/* TINFO="Start to turn off IPU_CORE0" */

		/* TINFO="Set Cabgen_3to3 Core0 WayEn to 3'h0" */
		/* *IPU_CONN_AXI_CTRL1 &= ~(IPU_CORE0_PROT_BIT_2ND_MASK); */
		spm_write(IPU_CONN_AXI_CTRL1, spm_read(IPU_CONN_AXI_CTRL1) & ~(IPU_CORE0_PROT_BIT_2ND_MASK));
#ifndef IGNORE_MTCMOS_CHECK
		/* while (((*IPU_CONN_AXI_CTRL3) & IPU_CORE0_PROT_BIT_ACK_2ND_MASK)
		 * != IPU_CORE0_PROT_BIT_ACK_2ND_MASK) {}
		 */
		while ((spm_read(IPU_CONN_AXI_CTRL3) & IPU_CORE0_PROT_BIT_ACK_2ND_MASK) !=
							IPU_CORE0_PROT_BIT_ACK_2ND_MASK) {
				/*  */
				/*  */
		}
#endif

		/* TINFO="Set bus protect" */
		spm_write(INFRA_TOPAXI_PROTECTEN_2_SET, IPU_CORE0_PROT_BIT_MASK);
#ifndef IGNORE_MTCMOS_CHECK
		while ((spm_read(INFRA_TOPAXI_PROTECTSTA1_2) & IPU_CORE0_PROT_BIT_ACK_MASK) !=
							IPU_CORE0_PROT_BIT_ACK_MASK) {
				/*  */
				/*  */
		}
#endif

		/* TINFO="Set SRAM_PDN = 1" */
		spm_write(IPU_CORE0_PWR_CON, spm_read(IPU_CORE0_PWR_CON) | IPU_CORE0_SRAM_PDN_BIT2);
		spm_write(IPU_CORE0_PWR_CON, spm_read(IPU_CORE0_PWR_CON) | IPU_CORE0_SRAM_PDN_BIT3);
		spm_write(IPU_CORE0_PWR_CON, spm_read(IPU_CORE0_PWR_CON) | IPU_CORE0_SRAM_PDN_BIT0);
#ifndef IGNORE_MTCMOS_CHECK
		/* TINFO="Wait until IPU_CORE0_SRAM_PDN_ACK = 1" */
		while ((spm_read(IPU_CORE0_PWR_CON) & IPU_CORE0_SRAM_PDN_ACK_BIT0) != IPU_CORE0_SRAM_PDN_ACK_BIT0) {
				/*  */
				/*  */
		}
#endif
		spm_write(IPU_CORE0_PWR_CON, spm_read(IPU_CORE0_PWR_CON) | IPU_CORE0_SRAM_PDN_BIT1);
#ifndef IGNORE_MTCMOS_CHECK
		/* TINFO="Wait until IPU_CORE0_SRAM_PDN_ACK = 1" */
		while ((spm_read(IPU_CORE0_PWR_CON) & IPU_CORE0_SRAM_PDN_ACK_BIT1) != IPU_CORE0_SRAM_PDN_ACK_BIT1) {
				/*  */
				/*  */
		}
#endif
		/* TINFO="Set PWR_ISO = 1" */
		spm_write(IPU_CORE0_PWR_CON, spm_read(IPU_CORE0_PWR_CON) | PWR_ISO);
		/* TINFO="Set PWR_CLK_DIS = 1" */
		spm_write(IPU_CORE0_PWR_CON, spm_read(IPU_CORE0_PWR_CON) | PWR_CLK_DIS);
		/* TINFO="Set PWR_RST_B = 0" */
		spm_write(IPU_CORE0_PWR_CON, spm_read(IPU_CORE0_PWR_CON) & ~PWR_RST_B);
		/* TINFO="Set PWR_ON = 0" */
		spm_write(IPU_CORE0_PWR_CON, spm_read(IPU_CORE0_PWR_CON) & ~PWR_ON);
		/* TINFO="Set PWR_ON_2ND = 0" */
		spm_write(IPU_CORE0_PWR_CON, spm_read(IPU_CORE0_PWR_CON) & ~PWR_ON_2ND);
#ifndef IGNORE_MTCMOS_CHECK
		/* TINFO="Wait until PWR_STATUS = 0 and PWR_STATUS_2ND = 0" */
		while ((spm_read(PWR_STATUS) & IPU_CORE0_PWR_STA_MASK)
		       || (spm_read(PWR_STATUS_2ND) & IPU_CORE0_PWR_STA_MASK)) {
				/*  */
				/*  */
		}
#endif
		/* TINFO="EXT_BUCK_ISO[5]=1"*/
		spm_write(EXT_BUCK_ISO, spm_read(EXT_BUCK_ISO) | (0x1 << 5));
		/* TINFO="Finish to turn off IPU_CORE0" */

		mm_clk_restore();
	} else {    /* STA_POWER_ON */
		/* TINFO="Start to turn on IPU_CORE0" */
		/* TINFO="EXT_BUCK_ISO[5]=0"*/
		spm_write(EXT_BUCK_ISO, spm_read(EXT_BUCK_ISO) & ~(0x1 << 5));
		/* TINFO="Set PWR_ON = 1" */
		spm_write(IPU_CORE0_PWR_CON, spm_read(IPU_CORE0_PWR_CON) | PWR_ON);
		/* TINFO="Set PWR_ON_2ND = 1" */
		spm_write(IPU_CORE0_PWR_CON, spm_read(IPU_CORE0_PWR_CON) | PWR_ON_2ND);
#ifndef IGNORE_MTCMOS_CHECK
		/* TINFO="Wait until PWR_STATUS = 1 and PWR_STATUS_2ND = 1" */
		while (((spm_read(PWR_STATUS) & IPU_CORE0_PWR_STA_MASK) != IPU_CORE0_PWR_STA_MASK)
		       || ((spm_read(PWR_STATUS_2ND) & IPU_CORE0_PWR_STA_MASK) != IPU_CORE0_PWR_STA_MASK)) {
				/*  */
				/*  */
		}
#endif
		/* TINFO="Set PWR_CLK_DIS = 0" */
		spm_write(IPU_CORE0_PWR_CON, spm_read(IPU_CORE0_PWR_CON) & ~PWR_CLK_DIS);
		/* TINFO="Set PWR_ISO = 0" */
		spm_write(IPU_CORE0_PWR_CON, spm_read(IPU_CORE0_PWR_CON) & ~PWR_ISO);
		/* TINFO="Set PWR_RST_B = 1" */
		spm_write(IPU_CORE0_PWR_CON, spm_read(IPU_CORE0_PWR_CON) | PWR_RST_B);
		/* TINFO="Set SRAM_PDN = 0" */
		spm_write(IPU_CORE0_PWR_CON, spm_read(IPU_CORE0_PWR_CON) & ~(0x1 << 10));
		spm_write(IPU_CORE0_PWR_CON, spm_read(IPU_CORE0_PWR_CON) & ~(0x1 << 11));
		spm_write(IPU_CORE0_PWR_CON, spm_read(IPU_CORE0_PWR_CON) & ~IPU_CORE0_SRAM_PDN_BIT0);
#ifndef IGNORE_MTCMOS_CHECK
		/* TINFO="Wait until IPU_CORE0_SRAM_PDN_ACK_BIT0 = 0" */
		while (spm_read(IPU_CORE0_PWR_CON) & IPU_CORE0_SRAM_PDN_ACK_BIT0) {
				/*  */
				/*  */
		}
#endif
		spm_write(IPU_CORE0_PWR_CON, spm_read(IPU_CORE0_PWR_CON) & ~IPU_CORE0_SRAM_PDN_BIT1);
#ifndef IGNORE_MTCMOS_CHECK
		/* TINFO="Wait until IPU_CORE0_SRAM_PDN_ACK_BIT1 = 0" */
		while (spm_read(IPU_CORE0_PWR_CON) & IPU_CORE0_SRAM_PDN_ACK_BIT1) {
				/*  */
				/*  */
		}
#endif
		/* TINFO="Set Cabgen_3to3 Core0 WayEn to 3'h7" */
		/* *IPU_CONN_AXI_CTRL1 |= IPU_CORE0_PROT_BIT_2ND_MASK; */
		spm_write(IPU_CONN_AXI_CTRL1, spm_read(IPU_CONN_AXI_CTRL1) | (IPU_CORE0_PROT_BIT_2ND_MASK));
#ifndef IGNORE_MTCMOS_CHECK
		/* while (((*IPU_CONN_AXI_CTRL3) & IPU_CORE0_PROT_BIT_ACK_2ND_MASK) !=
		 * IPU_CORE0_PROT_BIT_ACK_2ND_MASK) {}
		 */
		while ((spm_read(IPU_CONN_AXI_CTRL3) & IPU_CORE0_PROT_BIT_ACK_2ND_MASK) !=
									IPU_CORE0_PROT_BIT_ACK_2ND_MASK) {
				/*  */
				/*  */
		}
#endif
		/* TINFO="Release bus protect" */
		spm_write(INFRA_TOPAXI_PROTECTEN_2_CLR, IPU_CORE0_PROT_BIT_ACK_MASK);
#ifndef IGNORE_MTCMOS_CHECK
		/* Note that this protect ack check after releasing protect has been ignored */
#endif
		/* TINFO="Finish to turn on IPU_CORE0" */

		ipu_mtcmos_after_power_on();
	}

	return err;
}

int spm_mtcmos_ctrl_ipu_core0_sleep(int state)
{
	int err = 0;

	/* TINFO="enable SPM register control" */
	spm_write(POWERON_CONFIG_EN, (SPM_PROJECT_CODE << 16) | (0x1 << 0));

	if (state == STA_POWER_DOWN) {
		ipu_mtcmos_before_power_off();
		ipu_core_mtcmos_before_power_off();

		/* TINFO="Start to turn off IPU_CORE0" */

		/* TINFO="Set Cabgen_3to3 Core0 WayEn to 3'h0" */
		/* *IPU_CONN_AXI_CTRL1 &= ~(IPU_CORE0_PROT_BIT_2ND_MASK); */
		spm_write(IPU_CONN_AXI_CTRL1, spm_read(IPU_CONN_AXI_CTRL1) & ~(IPU_CORE0_PROT_BIT_2ND_MASK));
#ifndef IGNORE_MTCMOS_CHECK
		/* while (((*IPU_CONN_AXI_CTRL3) & IPU_CORE0_PROT_BIT_ACK_2ND_MASK)
		 *!= IPU_CORE0_PROT_BIT_ACK_2ND_MASK) {}
		 */
		while ((spm_read(IPU_CONN_AXI_CTRL3) & IPU_CORE0_PROT_BIT_ACK_2ND_MASK) !=
								IPU_CORE0_PROT_BIT_ACK_2ND_MASK) {
				/*  */
				/*  */
		}
#endif
		/* TINFO="Set bus protect" */
		spm_write(INFRA_TOPAXI_PROTECTEN_2_SET, IPU_CORE0_PROT_BIT_MASK);
#ifndef IGNORE_MTCMOS_CHECK
		while ((spm_read(INFRA_TOPAXI_PROTECTSTA1_2) & IPU_CORE0_PROT_BIT_ACK_MASK)
								!= IPU_CORE0_PROT_BIT_ACK_MASK) {
				/*  */
				/*  */
		}
#endif

		/* TINFO="Set SRAM_CKISO = 1" */
		spm_write(IPU_CORE0_PWR_CON, spm_read(IPU_CORE0_PWR_CON) | SRAM_CKISO);
		/* TINFO="Set SRAM_ISOINT_B = 0" */
		spm_write(IPU_CORE0_PWR_CON, spm_read(IPU_CORE0_PWR_CON) & ~SRAM_ISOINT_B);
		/* TINFO="Set SRAM_SLEEP_B = 0" */
		spm_write(IPU_CORE0_PWR_CON, spm_read(IPU_CORE0_PWR_CON) & ~IPU_CORE0_SRAM_SLEEP_B_BIT0);
		spm_write(IPU_CORE0_PWR_CON, spm_read(IPU_CORE0_PWR_CON) & ~IPU_CORE0_SRAM_SLEEP_B_BIT1);
#if 0
#ifndef IGNORE_MTCMOS_CHECK
		/ / /* TINFO="Wait until IPU_CORE0_SRAM_SLEEP_B_ACK = 0" */
		/ / while (spm_read(IPU_CORE0_PWR_CON) & IPU_CORE0_SRAM_SLEEP_B_ACK) {
		/ /		/*  */
		/ / }
#endif
#endif
		spm_write(IPU_CORE0_PWR_CON, spm_read(IPU_CORE0_PWR_CON) | IPU_CORE0_SRAM_PDN_BIT0);
#ifndef IGNORE_MTCMOS_CHECK
		/* TINFO="Wait until IPU_CORE0_SRAM_PDN_ACK = 1" */
		while ((spm_read(IPU_CORE0_PWR_CON) & IPU_CORE0_SRAM_PDN_ACK_BIT0) != IPU_CORE0_SRAM_PDN_ACK_BIT0) {
				/*  */
				/*  */
		}
#endif
		spm_write(IPU_CORE0_PWR_CON, spm_read(IPU_CORE0_PWR_CON) | IPU_CORE0_SRAM_PDN_BIT1);
#ifndef IGNORE_MTCMOS_CHECK
		/* TINFO="Wait until IPU_CORE0_SRAM_PDN_ACK = 1" */
		while ((spm_read(IPU_CORE0_PWR_CON) & IPU_CORE0_SRAM_PDN_ACK_BIT1) != IPU_CORE0_SRAM_PDN_ACK_BIT1) {
				/*  */
				/*  */
		}
#endif

		/* TINFO="Set PWR_ISO = 1" */
		spm_write(IPU_CORE0_PWR_CON, spm_read(IPU_CORE0_PWR_CON) | PWR_ISO);
		/* TINFO="Set PWR_CLK_DIS = 1" */
		spm_write(IPU_CORE0_PWR_CON, spm_read(IPU_CORE0_PWR_CON) | PWR_CLK_DIS);
		/* TINFO="Set PWR_RST_B = 0" */
		spm_write(IPU_CORE0_PWR_CON, spm_read(IPU_CORE0_PWR_CON) & ~PWR_RST_B);
		/* TINFO="Set PWR_ON = 0" */
		spm_write(IPU_CORE0_PWR_CON, spm_read(IPU_CORE0_PWR_CON) & ~PWR_ON);
		/* TINFO="Set PWR_ON_2ND = 0" */
		spm_write(IPU_CORE0_PWR_CON, spm_read(IPU_CORE0_PWR_CON) & ~PWR_ON_2ND);
#ifndef IGNORE_MTCMOS_CHECK
		/* TINFO="Wait until PWR_STATUS = 0 and PWR_STATUS_2ND = 0" */
		while ((spm_read(PWR_STATUS) & IPU_CORE0_PWR_STA_MASK)
		       || (spm_read(PWR_STATUS_2ND) & IPU_CORE0_PWR_STA_MASK)) {
				/*  */
				/*  */
		}
#endif
		/* TINFO="Finish to turn off IPU_CORE0" */
		mm_clk_restore();
	} else {    /* STA_POWER_ON */
		/* TINFO="Start to turn on IPU_CORE0" */
		/* TINFO="Set PWR_ON = 1" */
		spm_write(IPU_CORE0_PWR_CON, spm_read(IPU_CORE0_PWR_CON) | PWR_ON);
		/* TINFO="Set PWR_ON_2ND = 1" */
		spm_write(IPU_CORE0_PWR_CON, spm_read(IPU_CORE0_PWR_CON) | PWR_ON_2ND);
#ifndef IGNORE_MTCMOS_CHECK
		/* TINFO="Wait until PWR_STATUS = 1 and PWR_STATUS_2ND = 1" */
		while (((spm_read(PWR_STATUS) & IPU_CORE0_PWR_STA_MASK) != IPU_CORE0_PWR_STA_MASK)
		       || ((spm_read(PWR_STATUS_2ND) & IPU_CORE0_PWR_STA_MASK) != IPU_CORE0_PWR_STA_MASK)) {
				/*  */
				/*  */
		}
#endif
		/* TINFO="Set PWR_CLK_DIS = 0" */
		spm_write(IPU_CORE0_PWR_CON, spm_read(IPU_CORE0_PWR_CON) & ~PWR_CLK_DIS);
		/* TINFO="Set PWR_ISO = 0" */
		spm_write(IPU_CORE0_PWR_CON, spm_read(IPU_CORE0_PWR_CON) & ~PWR_ISO);
		/* TINFO="Set PWR_RST_B = 1" */
		spm_write(IPU_CORE0_PWR_CON, spm_read(IPU_CORE0_PWR_CON) | PWR_RST_B);
		/* TINFO="Set SRAM_SLEEP_B = 1" */
		spm_write(IPU_CORE0_PWR_CON, spm_read(IPU_CORE0_PWR_CON) | IPU_CORE0_SRAM_SLEEP_B_BIT0);
		spm_write(IPU_CORE0_PWR_CON, spm_read(IPU_CORE0_PWR_CON) | IPU_CORE0_SRAM_SLEEP_B_BIT1);
#if 0
#ifndef IGNORE_MTCMOS_CHECK
		/ /		/* TINFO="Wait until IPU_CORE0_SRAM_SLEEP_B_ACK = 1" */
		/ /		while ((spm_read(IPU_CORE0_PWR_CON) & IPU_CORE0_SRAM_SLEEP_B_ACK) !=
		/ /			IPU_CORE0_SRAM_SLEEP_B_ACK) {
		/ /				/*  */
		/ /		}
#endif
#endif
		/* TINFO="Set SRAM_ISOINT_B = 1" */
		spm_write(IPU_CORE0_PWR_CON, spm_read(IPU_CORE0_PWR_CON) | SRAM_ISOINT_B);
		/* TINFO="Set SRAM_CKISO = 0" */
		spm_write(IPU_CORE0_PWR_CON, spm_read(IPU_CORE0_PWR_CON) & ~SRAM_CKISO);

		spm_write(IPU_CORE0_PWR_CON, spm_read(IPU_CORE0_PWR_CON) & ~IPU_CORE0_SRAM_PDN_BIT0);
#ifndef IGNORE_MTCMOS_CHECK
		/* TINFO="Wait until IPU_CORE0_SRAM_PDN_ACK_BIT0 = 0" */
		while (spm_read(IPU_CORE0_PWR_CON) & IPU_CORE0_SRAM_PDN_ACK_BIT0) {
				/*  */
				/*  */
		}
#endif
		spm_write(IPU_CORE0_PWR_CON, spm_read(IPU_CORE0_PWR_CON) & ~IPU_CORE0_SRAM_PDN_BIT1);
#ifndef IGNORE_MTCMOS_CHECK
		/* TINFO="Wait until IPU_CORE0_SRAM_PDN_ACK_BIT1 = 0" */
		while (spm_read(IPU_CORE0_PWR_CON) & IPU_CORE0_SRAM_PDN_ACK_BIT1) {
				/*  */
				/*  */
		}
#endif
		/* TINFO="Set Cabgen_3to3 Core0 WayEn to 3'h7" */
		/* *IPU_CONN_AXI_CTRL1 |= IPU_CORE0_PROT_BIT_2ND_MASK; */
		spm_write(IPU_CONN_AXI_CTRL1, spm_read(IPU_CONN_AXI_CTRL1) | (IPU_CORE0_PROT_BIT_2ND_MASK));
#ifndef IGNORE_MTCMOS_CHECK
		/* while (((*IPU_CONN_AXI_CTRL3) & IPU_CORE0_PROT_BIT_ACK_2ND_MASK) !=
		 * IPU_CORE0_PROT_BIT_ACK_2ND_MASK) {}
		 */
		while ((spm_read(IPU_CONN_AXI_CTRL3) & IPU_CORE0_PROT_BIT_ACK_2ND_MASK) !=
					IPU_CORE0_PROT_BIT_ACK_2ND_MASK) {
				/*  */
				/*  */
		}
#endif
		/* TINFO="Release bus protect" */
		spm_write(INFRA_TOPAXI_PROTECTEN_2_CLR, IPU_CORE0_PROT_BIT_ACK_MASK);
#ifndef IGNORE_MTCMOS_CHECK
		/* Note that this protect ack check after releasing protect has been ignored */
#endif
		/* TINFO="Finish to turn on IPU_CORE0" */
		ipu_mtcmos_after_power_on();
	}

	return err;
}



int spm_mtcmos_ctrl_ipu_core1_shut_down(int state)
{
	int err = 0;

	/* TINFO="enable SPM register control" */
	spm_write(POWERON_CONFIG_EN, (SPM_PROJECT_CODE << 16) | (0x1 << 0));

	if (state == STA_POWER_DOWN) {
		ipu_mtcmos_before_power_off();
		ipu_core_mtcmos_before_power_off();

		/* TINFO="Start to turn off IPU_CORE1" */

		/* TINFO="Set Cabgen_3to3 Core1 WayEn to 3'h0" */
		/* *IPU_CONN_AXI_CTRL1 &= ~(IPU_CORE1_PROT_BIT_2ND_MASK); */
		spm_write(IPU_CONN_AXI_CTRL1, spm_read(IPU_CONN_AXI_CTRL1) & ~(IPU_CORE1_PROT_BIT_2ND_MASK));
#ifndef IGNORE_MTCMOS_CHECK
		/* while (((*IPU_CONN_AXI_CTRL3) & IPU_CORE1_PROT_BIT_ACK_2ND_MASK)
		 * != IPU_CORE1_PROT_BIT_ACK_2ND_MASK) {}
		 */
		while ((spm_read(IPU_CONN_AXI_CTRL3) & IPU_CORE1_PROT_BIT_ACK_2ND_MASK) !=
									IPU_CORE1_PROT_BIT_ACK_2ND_MASK) {
				/*  */
				/*  */
		}
#endif
		/* TINFO="Set bus protect" */
		spm_write(INFRA_TOPAXI_PROTECTEN_2_SET, IPU_CORE1_PROT_BIT_MASK);
#ifndef IGNORE_MTCMOS_CHECK
		while ((spm_read(INFRA_TOPAXI_PROTECTSTA1_2) & IPU_CORE1_PROT_BIT_ACK_MASK) !=
									IPU_CORE1_PROT_BIT_ACK_MASK) {
				/*  */
				/*  */
		}
#endif

		/* TINFO="Set SRAM_PDN = 1" */
		spm_write(IPU_CORE1_PWR_CON, spm_read(IPU_CORE1_PWR_CON) | IPU_CORE1_SRAM_PDN_BIT2);
		spm_write(IPU_CORE1_PWR_CON, spm_read(IPU_CORE1_PWR_CON) | IPU_CORE1_SRAM_PDN_BIT3);
		spm_write(IPU_CORE1_PWR_CON, spm_read(IPU_CORE1_PWR_CON) | IPU_CORE1_SRAM_PDN_BIT0);
#ifndef IGNORE_MTCMOS_CHECK
		/* TINFO="Wait until IPU_CORE1_SRAM_PDN_ACK = 1" */
		while ((spm_read(IPU_CORE1_PWR_CON) & IPU_CORE1_SRAM_PDN_ACK_BIT0) != IPU_CORE1_SRAM_PDN_ACK_BIT0) {
				/*  */
				/*  */
		}
#endif
		spm_write(IPU_CORE1_PWR_CON, spm_read(IPU_CORE1_PWR_CON) | IPU_CORE1_SRAM_PDN_BIT1);
#ifndef IGNORE_MTCMOS_CHECK
		/* TINFO="Wait until IPU_CORE1_SRAM_PDN_ACK = 1" */
		while ((spm_read(IPU_CORE1_PWR_CON) & IPU_CORE1_SRAM_PDN_ACK_BIT1) != IPU_CORE1_SRAM_PDN_ACK_BIT1) {
				/*  */
				/*  */
		}
#endif
		/* TINFO="Set PWR_ISO = 1" */
		spm_write(IPU_CORE1_PWR_CON, spm_read(IPU_CORE1_PWR_CON) | PWR_ISO);
		/* TINFO="Set PWR_CLK_DIS = 1" */
		spm_write(IPU_CORE1_PWR_CON, spm_read(IPU_CORE1_PWR_CON) | PWR_CLK_DIS);
		/* TINFO="Set PWR_RST_B = 0" */
		spm_write(IPU_CORE1_PWR_CON, spm_read(IPU_CORE1_PWR_CON) & ~PWR_RST_B);
		/* TINFO="Set PWR_ON = 0" */
		spm_write(IPU_CORE1_PWR_CON, spm_read(IPU_CORE1_PWR_CON) & ~PWR_ON);
		/* TINFO="Set PWR_ON_2ND = 0" */
		spm_write(IPU_CORE1_PWR_CON, spm_read(IPU_CORE1_PWR_CON) & ~PWR_ON_2ND);
#ifndef IGNORE_MTCMOS_CHECK
		/* TINFO="Wait until PWR_STATUS = 0 and PWR_STATUS_2ND = 0" */
		while ((spm_read(PWR_STATUS) & IPU_CORE1_PWR_STA_MASK)
		       || (spm_read(PWR_STATUS_2ND) & IPU_CORE1_PWR_STA_MASK)) {
				/*  */
				/*  */
		}
#endif
		/* TINFO="EXT_BUCK_ISO[6]=1"*/
		spm_write(EXT_BUCK_ISO, spm_read(EXT_BUCK_ISO) | (0x1 << 6));
		/* TINFO="Finish to turn off IPU_CORE1" */

		mm_clk_restore();
	} else {    /* STA_POWER_ON */
		/* TINFO="Start to turn on IPU_CORE1" */
		/* TINFO="EXT_BUCK_ISO[6]=0"*/
		spm_write(EXT_BUCK_ISO, spm_read(EXT_BUCK_ISO) & ~(0x1 << 6));
		/* TINFO="Set PWR_ON = 1" */
		spm_write(IPU_CORE1_PWR_CON, spm_read(IPU_CORE1_PWR_CON) | PWR_ON);
		/* TINFO="Set PWR_ON_2ND = 1" */
		spm_write(IPU_CORE1_PWR_CON, spm_read(IPU_CORE1_PWR_CON) | PWR_ON_2ND);

#ifndef IGNORE_MTCMOS_CHECK
		/* TINFO="Wait until PWR_STATUS = 1 and PWR_STATUS_2ND = 1" */
		while (((spm_read(PWR_STATUS) & IPU_CORE1_PWR_STA_MASK) != IPU_CORE1_PWR_STA_MASK)
		       || ((spm_read(PWR_STATUS_2ND) & IPU_CORE1_PWR_STA_MASK) != IPU_CORE1_PWR_STA_MASK)) {
				/*  */
				/*  */
		}
#endif
		/* TINFO="Set PWR_CLK_DIS = 0" */
		spm_write(IPU_CORE1_PWR_CON, spm_read(IPU_CORE1_PWR_CON) & ~PWR_CLK_DIS);
		/* TINFO="Set PWR_ISO = 0" */
		spm_write(IPU_CORE1_PWR_CON, spm_read(IPU_CORE1_PWR_CON) & ~PWR_ISO);
		/* TINFO="Set PWR_RST_B = 1" */
		spm_write(IPU_CORE1_PWR_CON, spm_read(IPU_CORE1_PWR_CON) | PWR_RST_B);
		/* TINFO="Set SRAM_PDN = 0" */
		spm_write(IPU_CORE1_PWR_CON, spm_read(IPU_CORE1_PWR_CON) & ~IPU_CORE1_SRAM_PDN_BIT2);
		spm_write(IPU_CORE1_PWR_CON, spm_read(IPU_CORE1_PWR_CON) & ~IPU_CORE1_SRAM_PDN_BIT3);
		spm_write(IPU_CORE1_PWR_CON, spm_read(IPU_CORE1_PWR_CON) & ~IPU_CORE1_SRAM_PDN_BIT0);

#ifndef IGNORE_MTCMOS_CHECK
		/* TINFO="Wait until IPU_CORE1_SRAM_PDN_ACK_BIT0 = 0" */
		while (spm_read(IPU_CORE1_PWR_CON) & IPU_CORE1_SRAM_PDN_ACK_BIT0) {
				/*  */
				/*  */
		}
#endif
		spm_write(IPU_CORE1_PWR_CON, spm_read(IPU_CORE1_PWR_CON) & ~IPU_CORE1_SRAM_PDN_BIT1);
#ifndef IGNORE_MTCMOS_CHECK
		/* TINFO="Wait until IPU_CORE1_SRAM_PDN_ACK_BIT1 = 0" */
		while (spm_read(IPU_CORE1_PWR_CON) & IPU_CORE1_SRAM_PDN_ACK_BIT1) {
				/*  */
				/*  */
		}
#endif
		/* TINFO="Set Cabgen_3to3 Core1 WayEn to 3'h7" */
		/* *IPU_CONN_AXI_CTRL1 |= IPU_CORE1_PROT_BIT_2ND_MASK; */
		spm_write(IPU_CONN_AXI_CTRL1, spm_read(IPU_CONN_AXI_CTRL1) | (IPU_CORE1_PROT_BIT_2ND_MASK));
#ifndef IGNORE_MTCMOS_CHECK
		/* while (((*IPU_CONN_AXI_CTRL3) & IPU_CORE1_PROT_BIT_ACK_2ND_MASK) !=
		 *IPU_CORE1_PROT_BIT_ACK_2ND_MASK) {}
		 */
		while ((spm_read(IPU_CONN_AXI_CTRL3) & IPU_CORE1_PROT_BIT_ACK_2ND_MASK) !=
					IPU_CORE1_PROT_BIT_ACK_2ND_MASK) {
				/*  */
				/*  */
		}

#endif
		/* TINFO="Release bus protect" */
		spm_write(INFRA_TOPAXI_PROTECTEN_2_CLR, IPU_CORE1_PROT_BIT_ACK_MASK);
#ifndef IGNORE_MTCMOS_CHECK
		/* Note that this protect ack check after releasing protect has been ignored */
#endif
		/* TINFO="Finish to turn on IPU_CORE1" */

		ipu_mtcmos_after_power_on();
	}

	return err;
}

int spm_mtcmos_ctrl_ipu_core1_sleep(int state)
{
	int err = 0;

	/* TINFO="enable SPM register control" */
	spm_write(POWERON_CONFIG_EN, (SPM_PROJECT_CODE << 16) | (0x1 << 0));

	if (state == STA_POWER_DOWN) {
		ipu_mtcmos_before_power_off();
		ipu_core_mtcmos_before_power_off();

		/* TINFO="Start to turn off IPU_CORE1" */

		/* TINFO="Set Cabgen_3to3 Core1 WayEn to 3'h0" */
		/* *IPU_CONN_AXI_CTRL1 &= ~(IPU_CORE1_PROT_BIT_2ND_MASK); */
		spm_write(IPU_CONN_AXI_CTRL1, spm_read(IPU_CONN_AXI_CTRL1) & ~(IPU_CORE1_PROT_BIT_2ND_MASK));
#ifndef IGNORE_MTCMOS_CHECK
		/* while (((*IPU_CONN_AXI_CTRL3) & IPU_CORE1_PROT_BIT_ACK_2ND_MASK) !=
		 * IPU_CORE1_PROT_BIT_ACK_2ND_MASK) {}
		 */
		while ((spm_read(IPU_CONN_AXI_CTRL3) & IPU_CORE1_PROT_BIT_ACK_2ND_MASK) !=
					IPU_CORE1_PROT_BIT_ACK_2ND_MASK) {
				/*  */
				/*  */
		}

#endif
		/* TINFO="Set bus protect" */
		spm_write(INFRA_TOPAXI_PROTECTEN_2_SET, IPU_CORE1_PROT_BIT_MASK);
#ifndef IGNORE_MTCMOS_CHECK
		while ((spm_read(INFRA_TOPAXI_PROTECTSTA1_2) & IPU_CORE1_PROT_BIT_ACK_MASK) !=
					IPU_CORE1_PROT_BIT_ACK_MASK) {
				/*  */
				/*  */
		}
#endif
		/* TINFO="Set SRAM_CKISO = 1" */
		spm_write(IPU_CORE1_PWR_CON, spm_read(IPU_CORE1_PWR_CON) | SRAM_CKISO);
		/* TINFO="Set SRAM_ISOINT_B = 0" */
		spm_write(IPU_CORE1_PWR_CON, spm_read(IPU_CORE1_PWR_CON) & ~SRAM_ISOINT_B);
		/* TINFO="Set SRAM_SLEEP_B = 0" */
		spm_write(IPU_CORE1_PWR_CON, spm_read(IPU_CORE1_PWR_CON) & ~IPU_CORE1_SRAM_SLEEP_B_BIT0);
		spm_write(IPU_CORE1_PWR_CON, spm_read(IPU_CORE1_PWR_CON) & ~IPU_CORE1_SRAM_SLEEP_B_BIT1);
#if 0
#ifndef IGNORE_MTCMOS_CHECK
/ /		/* TINFO="Wait until IPU_CORE1_SRAM_SLEEP_B_ACK = 0" */
/ /		while (spm_read(IPU_CORE1_PWR_CON) & IPU_CORE1_SRAM_SLEEP_B_ACK) {
/ /				/*  */
/ /		}
#endif
#endif
		spm_write(IPU_CORE1_PWR_CON, spm_read(IPU_CORE1_PWR_CON) | IPU_CORE1_SRAM_PDN_BIT0);
#ifndef IGNORE_MTCMOS_CHECK
		/* TINFO="Wait until IPU_CORE1_SRAM_PDN_ACK = 1" */
		while ((spm_read(IPU_CORE1_PWR_CON) & IPU_CORE1_SRAM_PDN_ACK_BIT0) != IPU_CORE1_SRAM_PDN_ACK_BIT0) {
				/*  */
				/*  */
		}
#endif
		spm_write(IPU_CORE1_PWR_CON, spm_read(IPU_CORE1_PWR_CON) | IPU_CORE1_SRAM_PDN_BIT1);
#ifndef IGNORE_MTCMOS_CHECK
		/* TINFO="Wait until IPU_CORE1_SRAM_PDN_ACK = 1" */
		while ((spm_read(IPU_CORE1_PWR_CON) & IPU_CORE1_SRAM_PDN_ACK_BIT1) != IPU_CORE1_SRAM_PDN_ACK_BIT1) {
				/*  */
				/*  */
		}
#endif

		/* TINFO="Set PWR_ISO = 1" */
		spm_write(IPU_CORE1_PWR_CON, spm_read(IPU_CORE1_PWR_CON) | PWR_ISO);
		/* TINFO="Set PWR_CLK_DIS = 1" */
		spm_write(IPU_CORE1_PWR_CON, spm_read(IPU_CORE1_PWR_CON) | PWR_CLK_DIS);
		/* TINFO="Set PWR_RST_B = 0" */
		spm_write(IPU_CORE1_PWR_CON, spm_read(IPU_CORE1_PWR_CON) & ~PWR_RST_B);
		/* TINFO="Set PWR_ON = 0" */
		spm_write(IPU_CORE1_PWR_CON, spm_read(IPU_CORE1_PWR_CON) & ~PWR_ON);
		/* TINFO="Set PWR_ON_2ND = 0" */
		spm_write(IPU_CORE1_PWR_CON, spm_read(IPU_CORE1_PWR_CON) & ~PWR_ON_2ND);
#ifndef IGNORE_MTCMOS_CHECK
		/* TINFO="Wait until PWR_STATUS = 0 and PWR_STATUS_2ND = 0" */
		while ((spm_read(PWR_STATUS) & IPU_CORE1_PWR_STA_MASK)
		       || (spm_read(PWR_STATUS_2ND) & IPU_CORE1_PWR_STA_MASK)) {
				/*  */
				/*  */
		}
#endif
		/* TINFO="Finish to turn off IPU_CORE1" */

		mm_clk_restore();
	} else {    /* STA_POWER_ON */
		/* TINFO="Start to turn on IPU_CORE1" */
		/* TINFO="Set PWR_ON = 1" */
		spm_write(IPU_CORE1_PWR_CON, spm_read(IPU_CORE1_PWR_CON) | PWR_ON);
		/* TINFO="Set PWR_ON_2ND = 1" */
		spm_write(IPU_CORE1_PWR_CON, spm_read(IPU_CORE1_PWR_CON) | PWR_ON_2ND);
#ifndef IGNORE_MTCMOS_CHECK
		/* TINFO="Wait until PWR_STATUS = 1 and PWR_STATUS_2ND = 1" */
		while (((spm_read(PWR_STATUS) & IPU_CORE1_PWR_STA_MASK) != IPU_CORE1_PWR_STA_MASK)
		       || ((spm_read(PWR_STATUS_2ND) & IPU_CORE1_PWR_STA_MASK) != IPU_CORE1_PWR_STA_MASK)) {
				/*  */
				/*  */
		}
#endif
		/* TINFO="Set PWR_CLK_DIS = 0" */
		spm_write(IPU_CORE1_PWR_CON, spm_read(IPU_CORE1_PWR_CON) & ~PWR_CLK_DIS);
		/* TINFO="Set PWR_ISO = 0" */
		spm_write(IPU_CORE1_PWR_CON, spm_read(IPU_CORE1_PWR_CON) & ~PWR_ISO);
		/* TINFO="Set PWR_RST_B = 1" */
		spm_write(IPU_CORE1_PWR_CON, spm_read(IPU_CORE1_PWR_CON) | PWR_RST_B);
		/* TINFO="Set SRAM_SLEEP_B = 1" */
		spm_write(IPU_CORE1_PWR_CON, spm_read(IPU_CORE1_PWR_CON) | IPU_CORE1_SRAM_SLEEP_B_BIT0);
		spm_write(IPU_CORE1_PWR_CON, spm_read(IPU_CORE1_PWR_CON) | IPU_CORE1_SRAM_SLEEP_B_BIT1);
#if 0
#ifndef IGNORE_MTCMOS_CHECK
/ /		/* TINFO="Wait until IPU_CORE1_SRAM_SLEEP_B_ACK = 1" */
/ /		while ((spm_read(IPU_CORE1_PWR_CON) & IPU_CORE1_SRAM_SLEEP_B_ACK) != IPU_CORE1_SRAM_SLEEP_B_ACK) {
/ /				/*  */
/ /		}
#endif
#endif
		/* TINFO="Set SRAM_ISOINT_B = 1" */
		spm_write(IPU_CORE1_PWR_CON, spm_read(IPU_CORE1_PWR_CON) | SRAM_ISOINT_B);
		/* TINFO="Set SRAM_CKISO = 0" */
		spm_write(IPU_CORE1_PWR_CON, spm_read(IPU_CORE1_PWR_CON) & ~SRAM_CKISO);

		spm_write(IPU_CORE1_PWR_CON, spm_read(IPU_CORE1_PWR_CON) & ~IPU_CORE1_SRAM_PDN_BIT0);
#ifndef IGNORE_MTCMOS_CHECK
		/* TINFO="Wait until IPU_CORE1_SRAM_PDN_ACK_BIT0 = 0" */
		while (spm_read(IPU_CORE1_PWR_CON) & IPU_CORE1_SRAM_PDN_ACK_BIT0) {
				/*  */
				/*  */
		}
#endif
		spm_write(IPU_CORE1_PWR_CON, spm_read(IPU_CORE1_PWR_CON) & ~IPU_CORE1_SRAM_PDN_BIT1);
#ifndef IGNORE_MTCMOS_CHECK
		/* TINFO="Wait until IPU_CORE1_SRAM_PDN_ACK_BIT1 = 0" */
		while (spm_read(IPU_CORE1_PWR_CON) & IPU_CORE1_SRAM_PDN_ACK_BIT1) {
				/*  */
				/*  */
		}
#endif
		/* TINFO="Set Cabgen_3to3 Core1 WayEn to 3'h7" */
		/* *IPU_CONN_AXI_CTRL1 |= IPU_CORE1_PROT_BIT_2ND_MASK; */
		spm_write(IPU_CONN_AXI_CTRL1, spm_read(IPU_CONN_AXI_CTRL1) | (IPU_CORE1_PROT_BIT_2ND_MASK));
#ifndef IGNORE_MTCMOS_CHECK
		/* while (((*IPU_CONN_AXI_CTRL3) & IPU_CORE1_PROT_BIT_ACK_2ND_MASK) !=
		 * IPU_CORE1_PROT_BIT_ACK_2ND_MASK) {}
		 */
		while ((spm_read(IPU_CONN_AXI_CTRL3) & IPU_CORE1_PROT_BIT_ACK_2ND_MASK) !=
					IPU_CORE1_PROT_BIT_ACK_2ND_MASK) {
				/*  */
				/*  */
		}

#endif
		/* TINFO="Release bus protect" */
		spm_write(INFRA_TOPAXI_PROTECTEN_2_CLR, IPU_CORE1_PROT_BIT_ACK_MASK);
#ifndef IGNORE_MTCMOS_CHECK
		/* Note that this protect ack check after releasing protect has been ignored */
#endif
		/* TINFO="Finish to turn on IPU_CORE1" */

		ipu_mtcmos_after_power_on();
	}

	return err;
}

int spm_mtcmos_ctrl_ipu_core2_shut_down(int state)
{
	int err = 0;

	/* TINFO="enable SPM register control" */
	spm_write(POWERON_CONFIG_EN, (SPM_PROJECT_CODE << 16) | (0x1 << 0));

	if (state == STA_POWER_DOWN) {
		ipu_mtcmos_before_power_off();
		ipu_core_mtcmos_before_power_off();

		/* TINFO="Start to turn off IPU_CORE2" */

		/* TINFO="Set Cabgen_3to3 Core2 WayEn to 3'h0" */
		/* *IPU_CONN_AXI_CTRL1 &= ~(IPU_CORE2_PROT_BIT_2ND_MASK); */
		spm_write(IPU_CONN_AXI_CTRL1, spm_read(IPU_CONN_AXI_CTRL1) & ~(IPU_CORE2_PROT_BIT_2ND_MASK));
#ifndef IGNORE_MTCMOS_CHECK
		/* while (((*IPU_CONN_AXI_CTRL3) & IPU_CORE2_PROT_BIT_ACK_2ND_MASK) !=
		 * IPU_CORE2_PROT_BIT_ACK_2ND_MASK) {}
		 */
		while ((spm_read(IPU_CONN_AXI_CTRL3) & IPU_CORE2_PROT_BIT_ACK_2ND_MASK) !=
					IPU_CORE2_PROT_BIT_ACK_2ND_MASK) {
				/*  */
				/*  */
		}
#endif
		/* TINFO="Set bus protect" */
		spm_write(INFRA_TOPAXI_PROTECTEN_2_SET, IPU_CORE2_PROT_BIT_MASK);
#ifndef IGNORE_MTCMOS_CHECK
		while ((spm_read(INFRA_TOPAXI_PROTECTSTA1_2) & IPU_CORE2_PROT_BIT_ACK_MASK) !=
											IPU_CORE2_PROT_BIT_ACK_MASK) {
				/*  */
				/*  */
		}
#endif
		/* TINFO="Set SRAM_PDN = 1" */
		spm_write(IPU_CORE2_PWR_CON, spm_read(IPU_CORE2_PWR_CON) | IPU_CORE2_SRAM_PDN_BIT2);
		spm_write(IPU_CORE2_PWR_CON, spm_read(IPU_CORE2_PWR_CON) | IPU_CORE2_SRAM_PDN_BIT3);
		spm_write(IPU_CORE2_PWR_CON, spm_read(IPU_CORE2_PWR_CON) | IPU_CORE2_SRAM_PDN_BIT0);
#ifndef IGNORE_MTCMOS_CHECK
		/* TINFO="Wait until IPU_CORE2_SRAM_PDN_ACK = 1" */
		while ((spm_read(IPU_CORE2_PWR_CON) & IPU_CORE2_SRAM_PDN_ACK_BIT0) != IPU_CORE2_SRAM_PDN_ACK_BIT0) {
				/*  */
				/*  */
		}
#endif
		spm_write(IPU_CORE2_PWR_CON, spm_read(IPU_CORE2_PWR_CON) | IPU_CORE2_SRAM_PDN_BIT1);
#ifndef IGNORE_MTCMOS_CHECK
		/* TINFO="Wait until IPU_CORE2_SRAM_PDN_ACK = 1" */
		while ((spm_read(IPU_CORE2_PWR_CON) & IPU_CORE2_SRAM_PDN_ACK_BIT1) != IPU_CORE2_SRAM_PDN_ACK_BIT1) {
				/*  */
				/*  */
		}
#endif
		/* TINFO="Set PWR_ISO = 1" */
		spm_write(IPU_CORE2_PWR_CON, spm_read(IPU_CORE2_PWR_CON) | PWR_ISO);
		/* TINFO="Set PWR_CLK_DIS = 1" */
		spm_write(IPU_CORE2_PWR_CON, spm_read(IPU_CORE2_PWR_CON) | PWR_CLK_DIS);
		/* TINFO="Set PWR_RST_B = 0" */
		spm_write(IPU_CORE2_PWR_CON, spm_read(IPU_CORE2_PWR_CON) & ~PWR_RST_B);
		/* TINFO="Set PWR_ON = 0" */
		spm_write(IPU_CORE2_PWR_CON, spm_read(IPU_CORE2_PWR_CON) & ~PWR_ON);
		/* TINFO="Set PWR_ON_2ND = 0" */
		spm_write(IPU_CORE2_PWR_CON, spm_read(IPU_CORE2_PWR_CON) & ~PWR_ON_2ND);
#ifndef IGNORE_MTCMOS_CHECK
		/* TINFO="Wait until PWR_STATUS = 0 and PWR_STATUS_2ND = 0" */
		while ((spm_read(PWR_STATUS) & IPU_CORE2_PWR_STA_MASK)
		       || (spm_read(PWR_STATUS_2ND) & IPU_CORE2_PWR_STA_MASK)) {
				/*  */
				/*  */
		}
#endif
		/* TINFO="EXT_BUCK_ISO[7]=1"*/
		spm_write(EXT_BUCK_ISO, spm_read(EXT_BUCK_ISO) | (0x1 << 7));
		/* TINFO="Finish to turn off IPU_CORE2" */
		mm_clk_restore();
	} else {    /* STA_POWER_ON */
		/* TINFO="Start to turn on IPU_CORE2" */
		/* TINFO="EXT_BUCK_ISO[7]=0"*/
		spm_write(EXT_BUCK_ISO, spm_read(EXT_BUCK_ISO) & ~(0x1 << 7));
		/* TINFO="Set PWR_ON = 1" */
		spm_write(IPU_CORE2_PWR_CON, spm_read(IPU_CORE2_PWR_CON) | PWR_ON);
		/* TINFO="Set PWR_ON_2ND = 1" */
		spm_write(IPU_CORE2_PWR_CON, spm_read(IPU_CORE2_PWR_CON) | PWR_ON_2ND);
#ifndef IGNORE_MTCMOS_CHECK
		/* TINFO="Wait until PWR_STATUS = 1 and PWR_STATUS_2ND = 1" */
		while (((spm_read(PWR_STATUS) & IPU_CORE2_PWR_STA_MASK) != IPU_CORE2_PWR_STA_MASK)
		       || ((spm_read(PWR_STATUS_2ND) & IPU_CORE2_PWR_STA_MASK) != IPU_CORE2_PWR_STA_MASK)) {
				/*  */
				/*  */
		}
#endif
		/* TINFO="Set PWR_CLK_DIS = 0" */
		spm_write(IPU_CORE2_PWR_CON, spm_read(IPU_CORE2_PWR_CON) & ~PWR_CLK_DIS);
		/* TINFO="Set PWR_ISO = 0" */
		spm_write(IPU_CORE2_PWR_CON, spm_read(IPU_CORE2_PWR_CON) & ~PWR_ISO);
		/* TINFO="Set PWR_RST_B = 1" */
		spm_write(IPU_CORE2_PWR_CON, spm_read(IPU_CORE2_PWR_CON) | PWR_RST_B);
		/* TINFO="Set SRAM_PDN = 0" */
		spm_write(IPU_CORE2_PWR_CON, spm_read(IPU_CORE2_PWR_CON) & ~IPU_CORE2_SRAM_PDN_BIT2);
		spm_write(IPU_CORE2_PWR_CON, spm_read(IPU_CORE2_PWR_CON) & ~IPU_CORE2_SRAM_PDN_BIT3);
		spm_write(IPU_CORE2_PWR_CON, spm_read(IPU_CORE2_PWR_CON) & ~IPU_CORE2_SRAM_PDN_BIT0);
#ifndef IGNORE_MTCMOS_CHECK
		/* TINFO="Wait until IPU_CORE2_SRAM_PDN_ACK_BIT0 = 0" */
		while (spm_read(IPU_CORE2_PWR_CON) & IPU_CORE2_SRAM_PDN_ACK_BIT0) {
				/*  */
				/*  */
		}
#endif
		spm_write(IPU_CORE2_PWR_CON, spm_read(IPU_CORE2_PWR_CON) & ~IPU_CORE2_SRAM_PDN_BIT1);
#ifndef IGNORE_MTCMOS_CHECK
		/* TINFO="Wait until IPU_CORE2_SRAM_PDN_ACK_BIT1 = 0" */
		while (spm_read(IPU_CORE2_PWR_CON) & IPU_CORE2_SRAM_PDN_ACK_BIT1) {
				/*  */
				/*  */
		}
#endif
		/* TINFO="Set Cabgen_3to3 Core2 WayEn to 3'h7" */
		/* *IPU_CONN_AXI_CTRL1 |= IPU_CORE2_PROT_BIT_2ND_MASK; */
		spm_write(IPU_CONN_AXI_CTRL1, spm_read(IPU_CONN_AXI_CTRL1) | (IPU_CORE2_PROT_BIT_2ND_MASK));
#ifndef IGNORE_MTCMOS_CHECK
		/* while (((*IPU_CONN_AXI_CTRL3) & IPU_CORE2_PROT_BIT_ACK_2ND_MASK) !=
		 * IPU_CORE2_PROT_BIT_ACK_2ND_MASK) {}
		 */
		while ((spm_read(IPU_CONN_AXI_CTRL3) & IPU_CORE2_PROT_BIT_ACK_2ND_MASK) !=
								IPU_CORE2_PROT_BIT_ACK_2ND_MASK) {
				/*  */
				/*  */
		}

#endif
		/* TINFO="Release bus protect" */
		spm_write(INFRA_TOPAXI_PROTECTEN_2_CLR, IPU_CORE2_PROT_BIT_ACK_MASK);
#ifndef IGNORE_MTCMOS_CHECK
		/* Note that this protect ack check after releasing protect has been ignored */
#endif
		/* TINFO="Finish to turn on IPU_CORE2" */
		ipu_mtcmos_after_power_on();
	}

	return err;
}

int spm_mtcmos_ctrl_ipu_core2_sleep(int state)
{
	int err = 0;

	/* TINFO="enable SPM register control" */
	spm_write(POWERON_CONFIG_EN, (SPM_PROJECT_CODE << 16) | (0x1 << 0));

	if (state == STA_POWER_DOWN) {
		ipu_mtcmos_before_power_off();
		ipu_core_mtcmos_before_power_off();

		/* TINFO="Start to turn off IPU_CORE2" */

		/* TINFO="Set Cabgen_3to3 Core2 WayEn to 3'h0" */
		/* *IPU_CONN_AXI_CTRL1 &= ~(IPU_CORE2_PROT_BIT_2ND_MASK); */
		spm_write(IPU_CONN_AXI_CTRL1, spm_read(IPU_CONN_AXI_CTRL1) & ~(IPU_CORE2_PROT_BIT_2ND_MASK));
#ifndef IGNORE_MTCMOS_CHECK
		/* while (((*IPU_CONN_AXI_CTRL3) & IPU_CORE2_PROT_BIT_ACK_2ND_MASK) !=
		 * IPU_CORE2_PROT_BIT_ACK_2ND_MASK) {}
		 */
		while ((spm_read(IPU_CONN_AXI_CTRL3) & IPU_CORE2_PROT_BIT_ACK_2ND_MASK) !=
					IPU_CORE2_PROT_BIT_ACK_2ND_MASK) {
				/*  */
				/*  */
		}

#endif
		/* TINFO="Set bus protect" */
		spm_write(INFRA_TOPAXI_PROTECTEN_2_SET, IPU_CORE2_PROT_BIT_MASK);
#ifndef IGNORE_MTCMOS_CHECK
		while ((spm_read(INFRA_TOPAXI_PROTECTSTA1_2) & IPU_CORE2_PROT_BIT_ACK_MASK) !=
					IPU_CORE2_PROT_BIT_ACK_MASK) {
				/*  */
				/*  */
		}
#endif
		/* TINFO="Set SRAM_CKISO = 1" */
		spm_write(IPU_CORE2_PWR_CON, spm_read(IPU_CORE2_PWR_CON) | SRAM_CKISO);
		/* TINFO="Set SRAM_ISOINT_B = 0" */
		spm_write(IPU_CORE2_PWR_CON, spm_read(IPU_CORE2_PWR_CON) & ~SRAM_ISOINT_B);
		/* TINFO="Set SRAM_SLEEP_B = 0" */
		spm_write(IPU_CORE2_PWR_CON, spm_read(IPU_CORE2_PWR_CON) & ~IPU_CORE2_SRAM_SLEEP_B_BIT0);
		spm_write(IPU_CORE2_PWR_CON, spm_read(IPU_CORE2_PWR_CON) & ~IPU_CORE2_SRAM_SLEEP_B_BIT1);
#if 0
#ifndef IGNORE_MTCMOS_CHECK
/ /		/* TINFO="Wait until IPU_CORE2_SRAM_SLEEP_B_ACK = 0" */
/ /		while (spm_read(IPU_CORE2_PWR_CON) & IPU_CORE2_SRAM_SLEEP_B_ACK) {
/ /				/*  */
/ /		}
#endif
#endif
		spm_write(IPU_CORE2_PWR_CON, spm_read(IPU_CORE2_PWR_CON) | IPU_CORE2_SRAM_PDN_BIT0);
#ifndef IGNORE_MTCMOS_CHECK
		/* TINFO="Wait until IPU_CORE2_SRAM_PDN_ACK = 1" */
		while ((spm_read(IPU_CORE2_PWR_CON) & IPU_CORE2_SRAM_PDN_ACK_BIT0) != IPU_CORE2_SRAM_PDN_ACK_BIT0) {
				/*  */
				/*  */
		}
#endif
		spm_write(IPU_CORE2_PWR_CON, spm_read(IPU_CORE2_PWR_CON) | IPU_CORE2_SRAM_PDN_BIT1);
#ifndef IGNORE_MTCMOS_CHECK
		/* TINFO="Wait until IPU_CORE2_SRAM_PDN_ACK = 1" */
		while ((spm_read(IPU_CORE2_PWR_CON) & IPU_CORE2_SRAM_PDN_ACK_BIT1) != IPU_CORE2_SRAM_PDN_ACK_BIT1) {
				/*  */
				/*  */
		}
#endif

		/* TINFO="Set PWR_ISO = 1" */
		spm_write(IPU_CORE2_PWR_CON, spm_read(IPU_CORE2_PWR_CON) | PWR_ISO);
		/* TINFO="Set PWR_CLK_DIS = 1" */
		spm_write(IPU_CORE2_PWR_CON, spm_read(IPU_CORE2_PWR_CON) | PWR_CLK_DIS);
		/* TINFO="Set PWR_RST_B = 0" */
		spm_write(IPU_CORE2_PWR_CON, spm_read(IPU_CORE2_PWR_CON) & ~PWR_RST_B);
		/* TINFO="Set PWR_ON = 0" */
		spm_write(IPU_CORE2_PWR_CON, spm_read(IPU_CORE2_PWR_CON) & ~PWR_ON);
		/* TINFO="Set PWR_ON_2ND = 0" */
		spm_write(IPU_CORE2_PWR_CON, spm_read(IPU_CORE2_PWR_CON) & ~PWR_ON_2ND);
#ifndef IGNORE_MTCMOS_CHECK
		/* TINFO="Wait until PWR_STATUS = 0 and PWR_STATUS_2ND = 0" */
		while ((spm_read(PWR_STATUS) & IPU_CORE2_PWR_STA_MASK)
		       || (spm_read(PWR_STATUS_2ND) & IPU_CORE2_PWR_STA_MASK)) {
				/*  */
				/*  */
		}
#endif
		/* TINFO="Finish to turn off IPU_CORE2" */
		mm_clk_restore();
	} else {    /* STA_POWER_ON */
		/* TINFO="Start to turn on IPU_CORE2" */
		/* TINFO="Set PWR_ON = 1" */
		spm_write(IPU_CORE2_PWR_CON, spm_read(IPU_CORE2_PWR_CON) | PWR_ON);
		/* TINFO="Set PWR_ON_2ND = 1" */
		spm_write(IPU_CORE2_PWR_CON, spm_read(IPU_CORE2_PWR_CON) | PWR_ON_2ND);
#ifndef IGNORE_MTCMOS_CHECK
		/* TINFO="Wait until PWR_STATUS = 1 and PWR_STATUS_2ND = 1" */
		while (((spm_read(PWR_STATUS) & IPU_CORE2_PWR_STA_MASK) != IPU_CORE2_PWR_STA_MASK)
		       || ((spm_read(PWR_STATUS_2ND) & IPU_CORE2_PWR_STA_MASK) != IPU_CORE2_PWR_STA_MASK)) {
				/*  */
				/*  */
		}
#endif
		/* TINFO="Set PWR_CLK_DIS = 0" */
		spm_write(IPU_CORE2_PWR_CON, spm_read(IPU_CORE2_PWR_CON) & ~PWR_CLK_DIS);
		/* TINFO="Set PWR_ISO = 0" */
		spm_write(IPU_CORE2_PWR_CON, spm_read(IPU_CORE2_PWR_CON) & ~PWR_ISO);
		/* TINFO="Set PWR_RST_B = 1" */
		spm_write(IPU_CORE2_PWR_CON, spm_read(IPU_CORE2_PWR_CON) | PWR_RST_B);
		/* TINFO="Set SRAM_SLEEP_B = 1" */
		spm_write(IPU_CORE2_PWR_CON, spm_read(IPU_CORE2_PWR_CON) | IPU_CORE2_SRAM_SLEEP_B_BIT0);
		spm_write(IPU_CORE2_PWR_CON, spm_read(IPU_CORE2_PWR_CON) | IPU_CORE2_SRAM_SLEEP_B_BIT1);
#if 0
#ifndef IGNORE_MTCMOS_CHECK
/ /		/* TINFO="Wait until IPU_CORE2_SRAM_SLEEP_B_ACK = 1" */
/ /		while ((spm_read(IPU_CORE2_PWR_CON) & IPU_CORE2_SRAM_SLEEP_B_ACK) != IPU_CORE2_SRAM_SLEEP_B_ACK) {
/ /				/*  */
/ /		}
#endif
#endif
		/* TINFO="Set SRAM_ISOINT_B = 1" */
		spm_write(IPU_CORE2_PWR_CON, spm_read(IPU_CORE2_PWR_CON) | SRAM_ISOINT_B);
		/* TINFO="Set SRAM_CKISO = 0" */
		spm_write(IPU_CORE2_PWR_CON, spm_read(IPU_CORE2_PWR_CON) & ~SRAM_CKISO);

		spm_write(IPU_CORE2_PWR_CON, spm_read(IPU_CORE2_PWR_CON) & ~IPU_CORE2_SRAM_PDN_BIT0);
#ifndef IGNORE_MTCMOS_CHECK
		/* TINFO="Wait until IPU_CORE2_SRAM_PDN_ACK_BIT0 = 0" */
		while (spm_read(IPU_CORE2_PWR_CON) & IPU_CORE2_SRAM_PDN_ACK_BIT0) {
				/*  */
				/*  */
		}
#endif
		spm_write(IPU_CORE2_PWR_CON, spm_read(IPU_CORE2_PWR_CON) & ~IPU_CORE2_SRAM_PDN_BIT1);
#ifndef IGNORE_MTCMOS_CHECK
		/* TINFO="Wait until IPU_CORE2_SRAM_PDN_ACK_BIT1 = 0" */
		while (spm_read(IPU_CORE2_PWR_CON) & IPU_CORE2_SRAM_PDN_ACK_BIT1) {
				/*  */
				/*  */
		}
#endif
		/* TINFO="Set Cabgen_3to3 Core2 WayEn to 3'h7" */
		/* *IPU_CONN_AXI_CTRL1 |= IPU_CORE2_PROT_BIT_2ND_MASK; */
		spm_write(IPU_CONN_AXI_CTRL1, spm_read(IPU_CONN_AXI_CTRL1) | (IPU_CORE2_PROT_BIT_2ND_MASK));
#ifndef IGNORE_MTCMOS_CHECK
		/* while (((*IPU_CONN_AXI_CTRL3) & IPU_CORE2_PROT_BIT_ACK_2ND_MASK) !=
		 * IPU_CORE2_PROT_BIT_ACK_2ND_MASK) {}
		 */
		while ((spm_read(IPU_CONN_AXI_CTRL3) & IPU_CORE2_PROT_BIT_ACK_2ND_MASK) !=
					IPU_CORE2_PROT_BIT_ACK_2ND_MASK) {
				/*  */
				/*  */
		}

#endif
		/* TINFO="Release bus protect" */
		spm_write(INFRA_TOPAXI_PROTECTEN_2_CLR, IPU_CORE2_PROT_BIT_ACK_MASK);
#ifndef IGNORE_MTCMOS_CHECK
		/* Note that this protect ack check after releasing protect has been ignored */
#endif
		/* TINFO="Finish to turn on IPU_CORE2" */
		ipu_mtcmos_after_power_on();
	}

	return err;
}

/* auto-gen end*/

/* enable op*/
/*
*static int general_sys_enable_op(struct subsys *sys)
*{
*	return spm_mtcmos_power_on_general_locked(sys, 1, 0);
*}
*/

static int MFG0_sys_enable_op(struct subsys *sys)
{
	/*pr_debug("MFG0_sys_enable_op\r\n"); */
	return spm_mtcmos_ctrl_mfg0(STA_POWER_ON);
}

static int MFG1_sys_enable_op(struct subsys *sys)
{
	/*pr_debug("MFG1_sys_enable_op\r\n"); */
	return spm_mtcmos_ctrl_mfg1(STA_POWER_ON);
}

static int MFG2_sys_enable_op(struct subsys *sys)
{
	/*pr_debug("MFG2_sys_enable_op\r\n"); */
	return spm_mtcmos_ctrl_mfg2(STA_POWER_ON);
}

static int MFG3_sys_enable_op(struct subsys *sys)
{
	/*pr_debug("MFG3_sys_enable_op\r\n"); */
	return spm_mtcmos_ctrl_mfg3(STA_POWER_ON);
}

static int MFG4_sys_enable_op(struct subsys *sys)
{
	/*pr_debug("MFG4_sys_enable_op\r\n"); */
	return spm_mtcmos_ctrl_mfg4(STA_POWER_ON);
}

static int MFG5_sys_enable_op(struct subsys *sys)
{
	/*pr_debug("MFG5_sys_enable_op\r\n"); */
	return spm_mtcmos_ctrl_mfg5(STA_POWER_ON);
}

static int C2K_sys_enable_op(struct subsys *sys)
{
	/*pr_debug("C2K_sys_enable_op\r\n"); */
	return spm_mtcmos_ctrl_c2k(STA_POWER_ON);
}

static int MD1_sys_enable_op(struct subsys *sys)
{
	return spm_mtcmos_ctrl_md1(STA_POWER_ON);
}

static int CONN_sys_enable_op(struct subsys *sys)
{
	/*pr_debug("CONN_sys_enable_op\r\n"); */
	return spm_mtcmos_ctrl_conn(STA_POWER_ON);
}

static int AUD_sys_enable_op(struct subsys *sys)
{
	/*pr_debug("AUD_sys_enable_op\r\n"); */
	return spm_mtcmos_ctrl_aud(STA_POWER_ON);
}

static int MM0_sys_enable_op(struct subsys *sys)
{
	/*pr_debug("MM0_sys_enable_op\r\n"); */
	return spm_mtcmos_ctrl_mm0(STA_POWER_ON);
}

static int CAM_sys_enable_op(struct subsys *sys)
{
	/*pr_debug("CAM_sys_enable_op\r\n"); */
	return spm_mtcmos_ctrl_cam(STA_POWER_ON);
}

static int ISP_sys_enable_op(struct subsys *sys)
{
	/*pr_debug("ISP_sys_enable_op\r\n"); */
	return spm_mtcmos_ctrl_isp(STA_POWER_ON);
}

static int VEN_sys_enable_op(struct subsys *sys)
{
	/*pr_debug("VEN_sys_enable_op\r\n"); */
	return spm_mtcmos_ctrl_ven(STA_POWER_ON);
}

static int VDE_sys_enable_op(struct subsys *sys)
{
	/*pr_debug("VDE_sys_enable_op\r\n"); */
	return spm_mtcmos_ctrl_vde(STA_POWER_ON);
}

static int IPU_VCORE_SHUTDOWN_sys_enable_op(struct subsys *sys)
{
	/*pr_debug("IPU_VCORE_SHUTDOWN_sys_enable_op\r\n"); */
	int ret = spm_mtcmos_ctrl_ipu_vcore_shut_down(STA_POWER_ON);

	if (ret)
		return ret;
	return spm_mtcmos_ctrl_ipu_shut_down(STA_POWER_ON);
}

static int IPU_SHUTDOWN_sys_enable_op(struct subsys *sys)
{

	/*pr_debug("IPU_SHUTDOWN_sys_enable_op\r\n"); */
	/* return spm_mtcmos_ctrl_ipu_shut_down(STA_POWER_ON); */

	/* Vcore & Vconn have combinded together. */
	return 0;
}

static int IPU_CORE0_SHUTDOWN_sys_enable_op(struct subsys *sys)
{
	/*pr_debug("IPU_CORE0_SHUTDOWN_sys_enable_op\r\n"); */
	return spm_mtcmos_ctrl_ipu_core0_shut_down(STA_POWER_ON);
}

static int IPU_CORE0_SLEEP_sys_enable_op(struct subsys *sys)
{
	/*pr_debug("IPU_CORE0_SLEEP_sys_enable_op\r\n"); */
	return spm_mtcmos_ctrl_ipu_core0_sleep(STA_POWER_ON);
}

static int IPU_CORE1_SHUTDOWN_sys_enable_op(struct subsys *sys)
{
	/*pr_debug("IPU_CORE1_SHUTDOWN_sys_enable_op\r\n"); */
	return spm_mtcmos_ctrl_ipu_core1_shut_down(STA_POWER_ON);
}

static int IPU_CORE1_SLEEP_sys_enable_op(struct subsys *sys)
{
	/*pr_debug("IPU_CORE1_SLEEP_sys_enable_op\r\n"); */
	return spm_mtcmos_ctrl_ipu_core1_sleep(STA_POWER_ON);
}

static int IPU_CORE2_SHUTDOWN_sys_enable_op(struct subsys *sys)
{
	/*pr_debug("IPU_CORE2_SHUTDOWN_sys_enable_op\r\n"); */
	return spm_mtcmos_ctrl_ipu_core2_shut_down(STA_POWER_ON);
}

static int IPU_CORE2_SLEEP_sys_enable_op(struct subsys *sys)
{
	/*pr_debug("IPU_CORE2_SLEEP_sys_enable_op\r\n"); */
	return spm_mtcmos_ctrl_ipu_core2_sleep(STA_POWER_ON);
}

/* disable op */
/*
*static int general_sys_disable_op(struct subsys *sys)
*{
*	return spm_mtcmos_power_off_general_locked(sys, 1, 0);
*}
*/

static int MFG0_sys_disable_op(struct subsys *sys)
{
	/*pr_debug("MFG0_sys_disable_op\r\n"); */
	return spm_mtcmos_ctrl_mfg0(STA_POWER_DOWN);
}

static int MFG1_sys_disable_op(struct subsys *sys)
{
	/*pr_debug("MFG1_sys_disable_op\r\n"); */
	return spm_mtcmos_ctrl_mfg1(STA_POWER_DOWN);
}

static int MFG2_sys_disable_op(struct subsys *sys)
{
	/*pr_debug("MFG2_sys_disable_op\r\n"); */
	return spm_mtcmos_ctrl_mfg2(STA_POWER_DOWN);
}

static int MFG3_sys_disable_op(struct subsys *sys)
{
	/*pr_debug("MFG3_sys_disable_op\r\n"); */
	return spm_mtcmos_ctrl_mfg3(STA_POWER_DOWN);
}

static int MFG4_sys_disable_op(struct subsys *sys)
{
	/*pr_debug("MFG4_sys_disable_op\r\n"); */
	return spm_mtcmos_ctrl_mfg4(STA_POWER_DOWN);
}

static int MFG5_sys_disable_op(struct subsys *sys)
{
	/*pr_debug("MFG5_sys_disable_op\r\n"); */
	return spm_mtcmos_ctrl_mfg5(STA_POWER_DOWN);
}

static int C2K_sys_disable_op(struct subsys *sys)
{
	/*pr_debug("C2K_sys_disable_op\r\n"); */
	return spm_mtcmos_ctrl_c2k(STA_POWER_DOWN);
}

static int MD1_sys_disable_op(struct subsys *sys)
{
	return spm_mtcmos_ctrl_md1(STA_POWER_DOWN);
}

static int CONN_sys_disable_op(struct subsys *sys)
{
	/*pr_debug("CONN_sys_disable_op\r\n"); */
	return spm_mtcmos_ctrl_conn(STA_POWER_DOWN);
}

static int AUD_sys_disable_op(struct subsys *sys)
{
	/*pr_debug("AUD_sys_disable_op\r\n"); */
	return spm_mtcmos_ctrl_aud(STA_POWER_DOWN);
}

static int MM0_sys_disable_op(struct subsys *sys)
{
	/*pr_debug("MM0_sys_disable_op\r\n"); */
	return spm_mtcmos_ctrl_mm0(STA_POWER_DOWN);
}

static int CAM_sys_disable_op(struct subsys *sys)
{
	/*pr_debug("CAM_sys_disable_op\r\n"); */
	return spm_mtcmos_ctrl_cam(STA_POWER_DOWN);
}

static int ISP_sys_disable_op(struct subsys *sys)
{
	/*pr_debug("ISP_sys_disable_op\r\n"); */
	return spm_mtcmos_ctrl_isp(STA_POWER_DOWN);
}

static int VEN_sys_disable_op(struct subsys *sys)
{
	/*pr_debug("VEN_sys_disable_op\r\n"); */
	return spm_mtcmos_ctrl_ven(STA_POWER_DOWN);
}

static int VDE_sys_disable_op(struct subsys *sys)
{
	/*pr_debug("VDE_sys_disable_op\r\n"); */
	return spm_mtcmos_ctrl_vde(STA_POWER_DOWN);
}

static int IPU_VCORE_SHUTDOWN_sys_disable_op(struct subsys *sys)
{
	/*pr_debug("IPU_VCORE_SHUTDOWN_sys_disable_op\r\n"); */
	int ret = spm_mtcmos_ctrl_ipu_shut_down(STA_POWER_DOWN);

	if (ret)
		return ret;
	return spm_mtcmos_ctrl_ipu_vcore_shut_down(STA_POWER_DOWN);
}

static int IPU_SHUTDOWN_sys_disable_op(struct subsys *sys)
{
	/*pr_debug("IPU_SHUTDOWN_sys_disable_op\r\n"); */
	/* return spm_mtcmos_ctrl_ipu_shut_down(STA_POWER_DOWN); */

	/* Vcore & Vconn have combinded together. */
	return 0;
}

static int IPU_CORE0_SHUTDOWN_sys_disable_op(struct subsys *sys)
{
	/*pr_debug("IPU_CORE0_SHUTDOWN_sys_disable_op\r\n"); */
	return spm_mtcmos_ctrl_ipu_core0_shut_down(STA_POWER_DOWN);
}

static int IPU_CORE0_SLEEP_sys_disable_op(struct subsys *sys)
{
	/*pr_debug("IPU_CORE0_SLEEP_sys_disable_op\r\n"); */
	return spm_mtcmos_ctrl_ipu_core0_sleep(STA_POWER_DOWN);
}

static int IPU_CORE1_SHUTDOWN_sys_disable_op(struct subsys *sys)
{
	/*pr_debug("IPU_CORE1_SHUTDOWN_sys_disable_op\r\n"); */
	return spm_mtcmos_ctrl_ipu_core1_shut_down(STA_POWER_DOWN);
}

static int IPU_CORE1_SLEEP_sys_disable_op(struct subsys *sys)
{
	/*pr_debug("IPU_CORE1_SLEEP_sys_disable_op\r\n"); */
	return spm_mtcmos_ctrl_ipu_core1_sleep(STA_POWER_DOWN);
}

static int IPU_CORE2_SHUTDOWN_sys_disable_op(struct subsys *sys)
{
	/*pr_debug("IPU_CORE2_SHUTDOWN_sys_disable_op\r\n"); */
	return spm_mtcmos_ctrl_ipu_core2_shut_down(STA_POWER_DOWN);
}

static int IPU_CORE2_SLEEP_sys_disable_op(struct subsys *sys)
{
	/*pr_debug("IPU_CORE2_SLEEP_sys_disable_op\r\n"); */
	return spm_mtcmos_ctrl_ipu_core2_sleep(STA_POWER_DOWN);
}


static int sys_get_state_op(struct subsys *sys)
{
	unsigned int sta = clk_readl(PWR_STATUS);
	unsigned int sta_s = clk_readl(PWR_STATUS_2ND);

	return (sta & sys->sta_mask) && (sta_s & sys->sta_mask);
}

#if 1
static int mfg1_get_state_op(struct subsys *sys)
{
#if 0
	unsigned int sta = clk_readl(PWR_STATUS);
	unsigned int sta_s = clk_readl(PWR_STATUS_2ND);

	return (sta & sys->sta_mask) && (sta_s & sys->sta_mask);
#else
	if ((spm_read(MFG1_PWR_CON) & PWR_ON) && (spm_read(MFG1_PWR_CON) & PWR_ON_2ND))
		return 1;
	else
		return 0;
#endif
}
#endif


static int ipuvcore_get_state_op(struct subsys *sys)
{
	if ((spm_read(IPU_VCORE_PWR_CON) & PWR_RST_B))
		return STA_POWER_ON;
	else
		return STA_POWER_DOWN;
}


/* ops */
/*
*static struct subsys_ops general_sys_ops = {
*	.enable = general_sys_enable_op,
*	.disable = general_sys_disable_op,
*	.get_state = sys_get_state_op,
*};
*/

static struct subsys_ops MFG0_sys_ops = {
	.enable = MFG0_sys_enable_op,
	.disable = MFG0_sys_disable_op,
	.get_state = sys_get_state_op,
};

static struct subsys_ops MFG1_sys_ops = {
	.enable = MFG1_sys_enable_op,
	.disable = MFG1_sys_disable_op,
	/*.get_state = sys_get_state_op,*/
	.get_state = mfg1_get_state_op,
};

static struct subsys_ops MFG2_sys_ops = {
	.enable = MFG2_sys_enable_op,
	.disable = MFG2_sys_disable_op,
	.get_state = sys_get_state_op,
};

static struct subsys_ops MFG3_sys_ops = {
	.enable = MFG3_sys_enable_op,
	.disable = MFG3_sys_disable_op,
	.get_state = sys_get_state_op,
};

static struct subsys_ops MFG4_sys_ops = {
	.enable = MFG4_sys_enable_op,
	.disable = MFG4_sys_disable_op,
	.get_state = sys_get_state_op,
};

static struct subsys_ops MFG5_sys_ops = {
	.enable = MFG5_sys_enable_op,
	.disable = MFG5_sys_disable_op,
	.get_state = sys_get_state_op,
};

static struct subsys_ops C2K_sys_ops = {
	.enable = C2K_sys_enable_op,
	.disable = C2K_sys_disable_op,
	.get_state = sys_get_state_op,
};

static struct subsys_ops MD1_sys_ops = {
	.enable = MD1_sys_enable_op,
	.disable = MD1_sys_disable_op,
	.get_state = sys_get_state_op,
};

static struct subsys_ops CONN_sys_ops = {
	.enable = CONN_sys_enable_op,
	.disable = CONN_sys_disable_op,
	.get_state = sys_get_state_op,
};

static struct subsys_ops AUD_sys_ops = {
	.enable = AUD_sys_enable_op,
	.disable = AUD_sys_disable_op,
	.get_state = sys_get_state_op,
};

static struct subsys_ops MM0_sys_ops = {
	.enable = MM0_sys_enable_op,
	.disable = MM0_sys_disable_op,
	.get_state = sys_get_state_op,
};

static struct subsys_ops CAM_sys_ops = {
	.enable = CAM_sys_enable_op,
	.disable = CAM_sys_disable_op,
	.get_state = sys_get_state_op,
};

static struct subsys_ops ISP_sys_ops = {
	.enable = ISP_sys_enable_op,
	.disable = ISP_sys_disable_op,
	.get_state = sys_get_state_op,
};

static struct subsys_ops VEN_sys_ops = {
	.enable = VEN_sys_enable_op,
	.disable = VEN_sys_disable_op,
	.get_state = sys_get_state_op,
};

static struct subsys_ops VDE_sys_ops = {
	.enable = VDE_sys_enable_op,
	.disable = VDE_sys_disable_op,
	.get_state = sys_get_state_op,
};

static struct subsys_ops IPU_vcore_shutdown_sys_ops = {
	.enable = IPU_VCORE_SHUTDOWN_sys_enable_op,
	.disable = IPU_VCORE_SHUTDOWN_sys_disable_op,
	.get_state = ipuvcore_get_state_op, /* Special case for IPU Vcore */
};

static struct subsys_ops IPU_shutdown_sys_ops = {
	.enable = IPU_SHUTDOWN_sys_enable_op,
	.disable = IPU_SHUTDOWN_sys_disable_op,
	.get_state = sys_get_state_op,
};

static struct subsys_ops IPU_core0_shutdown_sys_ops = {
	.enable = IPU_CORE0_SHUTDOWN_sys_enable_op,
	.disable = IPU_CORE0_SHUTDOWN_sys_disable_op,
	.get_state = sys_get_state_op,
};

static struct subsys_ops IPU_core0_sleep_sys_ops = {
	.enable = IPU_CORE0_SLEEP_sys_enable_op,
	.disable = IPU_CORE0_SLEEP_sys_disable_op,
	.get_state = sys_get_state_op,
};

static struct subsys_ops IPU_core1_shutdown_sys_ops = {
	.enable = IPU_CORE1_SHUTDOWN_sys_enable_op,
	.disable = IPU_CORE1_SHUTDOWN_sys_disable_op,
	.get_state = sys_get_state_op,
};

static struct subsys_ops IPU_core1_sleep_sys_ops = {
	.enable = IPU_CORE1_SLEEP_sys_enable_op,
	.disable = IPU_CORE1_SLEEP_sys_disable_op,
	.get_state = sys_get_state_op,
};

static struct subsys_ops IPU_core2_shutdown_sys_ops = {
	.enable = IPU_CORE2_SHUTDOWN_sys_enable_op,
	.disable = IPU_CORE2_SHUTDOWN_sys_disable_op,
	.get_state = sys_get_state_op,
};

static struct subsys_ops IPU_core2_sleep_sys_ops = {
	.enable = IPU_CORE2_SLEEP_sys_enable_op,
	.disable = IPU_CORE2_SLEEP_sys_disable_op,
	.get_state = sys_get_state_op,
};

static int subsys_is_on(enum subsys_id id)
{
	int r;
	struct subsys *sys = id_to_sys(id);

	WARN_ON(!sys);

	r = sys->ops->get_state(sys);

#if MT_CCF_DEBUG
	pr_debug("[CCF] %s:%d, sys=%s, id=%d\n", __func__, r, sys->name, id);
#endif				/* MT_CCF_DEBUG */

	return r;
}

#if CONTROL_LIMIT
int allow[NR_SYSS] = {
1, /*SYS_MFG0*/
1, /*SYS_MFG1*/
1, /*SYS_MFG2*/
1, /*SYS_MFG3*/
1, /*SYS_MFG4*/
1, /*SYS_MFG5*/
1, /*SYS_C2K*/
1, /*SYS_MD1*/
1, /*SYS_CONN*/
1, /*SYS_AUD*/
1, /*SYS_MM0*/
1, /*SYS_CAM*/
1, /*SYS_ISP*/
1, /*SYS_VEN*/
1, /*SYS_VDE*/
1, /*SYS_IPU_VCORE_SHUTDOWN */
1, /*SYS_IPU_SHUTDOWN */
1, /*SYS_IPU_CORE0_SHUTDOWN */
1, /*SYS_IPU_CORE0_SLEEP */
1, /*SYS_IPU_CORE1_SHUTDOWN */
1, /*SYS_IPU_CORE1_SLEEP */
1, /*SYS_IPU_CORE2_SHUTDOWN */
1, /*SYS_IPU_CORE2_SLEEP */

};
#endif
static int enable_subsys(enum subsys_id id)
{
	int r;
	unsigned long flags;
	struct subsys *sys = id_to_sys(id);
	struct pg_callbacks *pgcb;

	WARN_ON(!sys);

#if MT_CCF_BRINGUP
	/*pr_debug("[CCF] %s: sys=%s, id=%d\n", __func__, sys->name, id);*/
	switch (id) {
	case SYS_MD1:
		spm_mtcmos_ctrl_md1(STA_POWER_ON);
		break;
	case SYS_C2K:
		spm_mtcmos_ctrl_c2k(STA_POWER_ON);
		break;
	case SYS_CONN:
		spm_mtcmos_ctrl_conn(STA_POWER_ON);
		break;
	default:
		break;
	}
	return 0;
#endif				/* MT_CCF_BRINGUP */

#if CONTROL_LIMIT
	#if MT_CCF_DEBUG
	pr_debug("[CCF] %s: sys=%s, id=%d\n", __func__, sys->name, id);
	#endif
	if (allow[id] == 0) {
		#if MT_CCF_DEBUG
		pr_debug("[CCF] %s: do nothing return\n", __func__);
		#endif
		return 0;
	}
#endif


	mtk_clk_lock(flags);

#if CHECK_PWR_ST
	if (sys->ops->get_state(sys) == SUBSYS_PWR_ON) {
		mtk_clk_unlock(flags);
		return 0;
	}
#endif				/* CHECK_PWR_ST */

	r = sys->ops->enable(sys);
	WARN_ON(r);

	mtk_clk_unlock(flags);

	list_for_each_entry(pgcb, &pgcb_list, list) {
		if (pgcb->after_on)
			pgcb->after_on(id);
	}

	return r;
}

static int disable_subsys(enum subsys_id id)
{
	int r;
	unsigned long flags;
	struct subsys *sys = id_to_sys(id);
	struct pg_callbacks *pgcb;

	WARN_ON(!sys);

#if MT_CCF_BRINGUP
	/*pr_debug("[CCF] %s: sys=%s, id=%d\n", __func__, sys->name, id);*/
	switch (id) {
	case SYS_MD1:
		spm_mtcmos_ctrl_md1(STA_POWER_DOWN);
		break;
	case SYS_C2K:
		spm_mtcmos_ctrl_c2k(STA_POWER_DOWN);
		break;
	case SYS_CONN:
		spm_mtcmos_ctrl_conn(STA_POWER_DOWN);
		break;
	default:
		break;
	}
	return 0;
#endif				/* MT_CCF_BRINGUP */
#if CONTROL_LIMIT
	#if MT_CCF_DEBUG
	pr_debug("[CCF] %s: sys=%s, id=%d\n", __func__, sys->name, id);
	#endif
	if (allow[id] == 0) {
		#if MT_CCF_DEBUG
		pr_debug("[CCF] %s: do nothing return\n", __func__);
		#endif
		return 0;
	}
#endif



	/* TODO: check all clocks related to this subsys are off */
	/* could be power off or not */
	list_for_each_entry_reverse(pgcb, &pgcb_list, list) {
		if (pgcb->before_off)
			pgcb->before_off(id);
	}

	mtk_clk_lock(flags);

#if CHECK_PWR_ST
	if (sys->ops->get_state(sys) == SUBSYS_PWR_DOWN) {
		mtk_clk_unlock(flags);
		return 0;
	}
#endif				/* CHECK_PWR_ST */

	r = sys->ops->disable(sys);
	WARN_ON(r);

	mtk_clk_unlock(flags);

	return r;
}

/*
 * power_gate
 */

struct mt_power_gate {
	struct clk_hw hw;
	struct clk *pre_clk;
	struct clk *pre_clk2;
	enum subsys_id pd_id;
};

#define to_power_gate(_hw) container_of(_hw, struct mt_power_gate, hw)

static int pg_enable(struct clk_hw *hw)
{
	struct mt_power_gate *pg = to_power_gate(hw);

#if MT_CCF_DEBUG
	pr_debug("[CCF] %s: sys=%s, pd_id=%u\n", __func__,
		 __clk_get_name(hw->clk), pg->pd_id);
#endif				/* MT_CCF_DEBUG */

	return enable_subsys(pg->pd_id);
}

static void pg_disable(struct clk_hw *hw)
{
	struct mt_power_gate *pg = to_power_gate(hw);

#if MT_CCF_DEBUG
	pr_debug("[CCF] %s: sys=%s, pd_id=%u\n", __func__,
		 __clk_get_name(hw->clk), pg->pd_id);
#endif				/* MT_CCF_DEBUG */

	disable_subsys(pg->pd_id);
}

static int pg_is_enabled(struct clk_hw *hw)
{
	struct mt_power_gate *pg = to_power_gate(hw);

	return subsys_is_on(pg->pd_id);
}

int pg_prepare(struct clk_hw *hw)
{
	int r;
	struct mt_power_gate *pg = to_power_gate(hw);

#if MT_CCF_DEBUG
	pr_debug("[CCF] %s: sys=%s, pre_sys=%s\n", __func__,
		 __clk_get_name(hw->clk),
		 pg->pre_clk ? __clk_get_name(pg->pre_clk) : "");
#endif				/* MT_CCF_DEBUG */

	if (pg->pre_clk) {
		r = clk_prepare_enable(pg->pre_clk);
		if (r)
			return r;
	}
	if (pg->pre_clk2) {
		r = clk_prepare_enable(pg->pre_clk2);
		if (r)
			return r;
	}

	return pg_enable(hw);

}

void pg_unprepare(struct clk_hw *hw)
{
	struct mt_power_gate *pg = to_power_gate(hw);

#if MT_CCF_DEBUG
	pr_debug("[CCF] %s: clk=%s, pre_clk=%s\n", __func__,
		 __clk_get_name(hw->clk),
		 pg->pre_clk ? __clk_get_name(pg->pre_clk) : "");
#endif				/* MT_CCF_DEBUG */

	pg_disable(hw);

	if (pg->pre_clk)
		clk_disable_unprepare(pg->pre_clk);
	if (pg->pre_clk2)
		clk_disable_unprepare(pg->pre_clk2);
}

static const struct clk_ops mt_power_gate_ops = {
	.prepare = pg_prepare,
	.unprepare = pg_unprepare,
	.is_enabled = pg_is_enabled,
};

struct clk *mt_clk_register_power_gate(const char *name,
				       const char *parent_name,
				       struct clk *pre_clk,
				       struct clk *pre_clk2, enum subsys_id pd_id)
{
	struct mt_power_gate *pg;
	struct clk *clk;
	struct clk_init_data init;

	pg = kzalloc(sizeof(*pg), GFP_KERNEL);
	if (!pg)
		return ERR_PTR(-ENOMEM);

	init.name = name;
	init.flags = CLK_IGNORE_UNUSED;
	init.parent_names = parent_name ? &parent_name : NULL;
	init.num_parents = parent_name ? 1 : 0;
	init.ops = &mt_power_gate_ops;

	pg->pre_clk = pre_clk;
	pg->pre_clk2 = pre_clk2;
	pg->pd_id = pd_id;
	pg->hw.init = &init;

	clk = clk_register(NULL, &pg->hw);
	if (IS_ERR(clk))
		kfree(pg);

	return clk;
}

#define pg_mfg0 "pg_mfg0"
#define pg_mfg1 "pg_mfg1"
#define pg_mfg2 "pg_mfg2"
#define pg_mfg3 "pg_mfg3"
#define pg_mfg4 "pg_mfg4"
#define pg_mfg5 "pg_mfg5"
#define pg_c2k "pg_c2k"
#define pg_md1 "pg_md1"
#define pg_conn "pg_conn"
#define pg_aud "pg_aud"
#define pg_mm0 "pg_mm0"
#define pg_cam "pg_cam"
#define pg_isp "pg_isp"
#define pg_ven "pg_ven"
#define pg_vde "pg_vde"
#define pg_ipu_vcore_shutdown "pg_ipu_vcore_shutdown"
#define pg_ipu_shutdown	"pg_ipu_shutdown"
#define pg_ipu_core0_shutdown "pg_ipu_core0_shutdown"
#define pg_ipu_core1_shutdown "pg_ipu_core1_shutdown"
#define pg_ipu_core2_shutdown "pg_ipu_core2_shutdown"
#define pg_ipu_core0_sleep "pg_ipu_core0_sleep"
#define pg_ipu_core1_sleep "pg_ipu_core1_sleep"
#define pg_ipu_core2_sleep "pg_ipu_core2_sleep"

#define mm_sel "mm_sel"
#define cam_sel "cam_sel"
#define seninf_sel "seninf_sel"
#define img_sel "img_sel"
#define venc_sel "venc_sel"
#define vdec_sel "vdec_sel"
#define ipu_if_sel "ipu_if_sel"
#define dsp_sel "dsp_sel"
#define dsp1_sel "dsp1_sel"
#define dsp2_sel "dsp2_sel"
#define dsp3_sel "dsp3_sel"
#define mfg_sel "mfg_sel"


struct mtk_power_gate {
	int id;
	const char *name;
	const char *parent_name;
	const char *pre_clk_name;
	const char *pre_clk2_name;
	enum subsys_id pd_id;
};

#define PGATE(_id, _name, _parent, _pre_clk, _pd_id) {	\
		.id = _id,				\
		.name = _name,				\
		.parent_name = _parent,			\
		.pre_clk_name = _pre_clk,		\
		.pd_id = _pd_id,			\
	}

#define PGATE2(_id, _name, _parent, _pre_clk, _pre_clk2, _pd_id) {	\
		.id = _id,				\
		.name = _name,				\
		.parent_name = _parent,			\
		.pre_clk_name = _pre_clk,		\
		.pre_clk2_name = _pre_clk2,		\
		.pd_id = _pd_id,			\
	}

/* FIXME: all values needed to be verified */
struct mtk_power_gate scp_clks[] __initdata = {
	PGATE2(SCP_SYS_MFG0, pg_mfg0, NULL, mfg_sel, NULL, SYS_MFG0),
	PGATE2(SCP_SYS_MFG1, pg_mfg1, NULL, NULL, NULL, SYS_MFG1),
	PGATE2(SCP_SYS_MFG2, pg_mfg2, NULL, NULL, NULL, SYS_MFG2),
	PGATE2(SCP_SYS_MFG3, pg_mfg3, NULL, NULL, NULL, SYS_MFG3),
	PGATE2(SCP_SYS_MFG4, pg_mfg4, NULL, NULL, NULL, SYS_MFG4),
	PGATE2(SCP_SYS_MFG5, pg_mfg5, NULL, NULL, NULL, SYS_MFG5),
	PGATE2(SCP_SYS_C2K, pg_c2k, NULL, NULL, NULL, SYS_C2K),
	PGATE2(SCP_SYS_MD1, pg_md1, NULL, NULL, NULL, SYS_MD1),
	PGATE2(SCP_SYS_CONN, pg_conn, NULL, NULL, NULL, SYS_CONN),
	PGATE2(SCP_SYS_AUD, pg_aud, NULL, NULL, NULL, SYS_AUD),
	PGATE2(SCP_SYS_MM0, pg_mm0, NULL, mm_sel, NULL, SYS_MM0),
	PGATE2(SCP_SYS_CAM, pg_cam, NULL, cam_sel, seninf_sel, SYS_CAM),
	PGATE2(SCP_SYS_ISP, pg_isp, NULL, img_sel, NULL, SYS_ISP),
	PGATE2(SCP_SYS_VEN, pg_ven, NULL, mm_sel, venc_sel, SYS_VEN),
	PGATE2(SCP_SYS_VDE, pg_vde, NULL, mm_sel, vdec_sel, SYS_VDE),
	PGATE2(SCP_SYS_IPU_VCORE_SHUTDOWN, pg_ipu_vcore_shutdown, NULL, ipu_if_sel, dsp_sel, SYS_IPU_VCORE_SHUTDOWN),
	PGATE2(SCP_SYS_IPU_SHUTDOWN, pg_ipu_shutdown, NULL, ipu_if_sel, dsp_sel, SYS_IPU_SHUTDOWN),
	PGATE2(SCP_SYS_IPU_CORE0_SHUTDOWN, pg_ipu_core0_shutdown, NULL, ipu_if_sel, dsp1_sel, SYS_IPU_CORE0_SHUTDOWN),
	PGATE2(SCP_SYS_IPU_CORE1_SHUTDOWN, pg_ipu_core1_shutdown, NULL, ipu_if_sel, dsp2_sel, SYS_IPU_CORE1_SHUTDOWN),
	PGATE2(SCP_SYS_IPU_CORE2_SHUTDOWN, pg_ipu_core2_shutdown, NULL, ipu_if_sel, dsp3_sel, SYS_IPU_CORE2_SHUTDOWN),
	PGATE2(SCP_SYS_IPU_CORE0_SLEEP, pg_ipu_core0_sleep, NULL, ipu_if_sel, dsp1_sel, SYS_IPU_CORE0_SLEEP),
	PGATE2(SCP_SYS_IPU_CORE1_SLEEP, pg_ipu_core1_sleep, NULL, ipu_if_sel, dsp2_sel, SYS_IPU_CORE1_SLEEP),
	PGATE2(SCP_SYS_IPU_CORE2_SLEEP, pg_ipu_core2_sleep, NULL, ipu_if_sel, dsp3_sel, SYS_IPU_CORE2_SLEEP),
};

static void __init init_clk_scpsys(void __iomem *infracfg_reg,
					void __iomem *spm_reg,
					void __iomem *smi_common_reg,
					void __iomem *smi_common_ext_reg,
					void __iomem *ipu_conn_reg,
					struct clk_onecell_data *clk_data)
{
	int i;
	struct clk *clk;
	struct clk *pre_clk;
	struct clk *pre_clk2;

	infracfg_base = infracfg_reg;
	spm_base = spm_reg;
	smi_common_base = smi_common_reg;
	smi_common_ext_base = smi_common_ext_reg;
	ipu_conn_base = ipu_conn_reg;

	syss[SYS_MFG0].ctl_addr = MFG0_PWR_CON;
	syss[SYS_MFG1].ctl_addr = MFG1_PWR_CON;
	syss[SYS_MFG2].ctl_addr = MFG2_PWR_CON;
	syss[SYS_MFG3].ctl_addr = MFG3_PWR_CON;
	syss[SYS_MFG4].ctl_addr = MFG4_PWR_CON;
	syss[SYS_MFG5].ctl_addr = MFG5_PWR_CON;
	syss[SYS_C2K].ctl_addr = C2K_PWR_CON;
	syss[SYS_MD1].ctl_addr = MD1_PWR_CON;
	syss[SYS_CONN].ctl_addr = CONN_PWR_CON;
	syss[SYS_AUD].ctl_addr = AUD_PWR_CON;
	syss[SYS_MM0].ctl_addr = MM0_PWR_CON;
	syss[SYS_CAM].ctl_addr = CAM_PWR_CON;
	syss[SYS_ISP].ctl_addr = ISP_PWR_CON;
	syss[SYS_VEN].ctl_addr = VEN_PWR_CON;
	syss[SYS_VDE].ctl_addr = VDE_PWR_CON;
	syss[SYS_IPU_VCORE_SHUTDOWN].ctl_addr = IPU_VCORE_PWR_CON;
	syss[SYS_IPU_SHUTDOWN].ctl_addr = IPU_PWR_CON;
	syss[SYS_IPU_CORE0_SHUTDOWN].ctl_addr = IPU_CORE0_PWR_CON;
	syss[SYS_IPU_CORE1_SHUTDOWN].ctl_addr = IPU_CORE1_PWR_CON;
	syss[SYS_IPU_CORE2_SHUTDOWN].ctl_addr = IPU_CORE2_PWR_CON;


	for (i = 0; i < ARRAY_SIZE(scp_clks); i++) {
		struct mtk_power_gate *pg = &scp_clks[i];

		pre_clk = pg->pre_clk_name ? __clk_lookup(pg->pre_clk_name) : NULL;
		pre_clk2 = pg->pre_clk2_name ? __clk_lookup(pg->pre_clk2_name) : NULL;

		/*clk = mt_clk_register_power_gate(pg->name, pg->parent_name, pre_clk, pg->pd_id);*/
		clk = mt_clk_register_power_gate(pg->name, pg->parent_name, pre_clk, pre_clk2, pg->pd_id);

		if (IS_ERR(clk)) {
			pr_debug("[CCF] %s: Failed to register clk %s: %ld\n",
			       __func__, pg->name, PTR_ERR(clk));
			continue;
		}

		if (clk_data)
			clk_data->clks[pg->id] = clk;

#if MT_CCF_DEBUG
		pr_debug("[CCF] %s: pgate %3d: %s\n", __func__, i, pg->name);
#endif				/* MT_CCF_DEBUG */
	}
}

/*
 * device tree support
 */

/* TODO: remove this function */
static struct clk_onecell_data *alloc_clk_data(unsigned int clk_num)
{
	int i;
	struct clk_onecell_data *clk_data;

	clk_data = kzalloc(sizeof(*clk_data), GFP_KERNEL);
	if (!clk_data)
		return NULL;

	clk_data->clks = kcalloc(clk_num, sizeof(struct clk *), GFP_KERNEL);
	if (!clk_data->clks) {
		kfree(clk_data);
		return NULL;
	}

	clk_data->clk_num = clk_num;

	for (i = 0; i < clk_num; ++i)
		clk_data->clks[i] = ERR_PTR(-ENOENT);

	return clk_data;
}

/* TODO: remove this function */
static void __iomem *get_reg(struct device_node *np, int index)
{
#if DUMMY_REG_TEST
	return kzalloc(PAGE_SIZE, GFP_KERNEL);
#else
	return of_iomap(np, index);
#endif
}

#ifdef CONFIG_OF
void iomap_mm(void)
{
	struct device_node *node;

#if 0
/*mfgcfg*/
	node = of_find_compatible_node(NULL, NULL, "mediatek,g3d_config");
	if (!node)
		pr_debug("[CLK_MFGCFG] find node failed\n");
	clk_mfgcfg_base = of_iomap(node, 0);
	if (!clk_mfgcfg_base)
		pr_debug("[CLK_MFGCFG] base failed\n");
#endif
/*mmsys_config*/
	node = of_find_compatible_node(NULL, NULL, "mediatek,mmsys_config");
	if (!node)
		pr_debug("[CLK_MMSYS] find node failed\n");
	clk_mmsys_config_base = of_iomap(node, 0);
	if (!clk_mmsys_config_base)
		pr_debug("[CLK_MMSYS] base failed\n");
/*imgsys*/
	node = of_find_compatible_node(NULL, NULL, "mediatek,imgsys");
	if (!node)
		pr_debug("[CLK_IMGSYS_CONFIG] find node failed\n");
	clk_imgsys_base = of_iomap(node, 0);
	if (!clk_imgsys_base)
		pr_debug("[CLK_IMGSYS_CONFIG] base failed\n");
/*vdec_gcon*/
	node = of_find_compatible_node(NULL, NULL, "mediatek,vdec_top_global_con");
	if (!node)
		pr_debug("[CLK_VDEC_GCON] find node failed\n");
	clk_vdec_gcon_base = of_iomap(node, 0);
	if (!clk_vdec_gcon_base)
		pr_debug("[CLK_VDEC_GCON] base failed\n");
/*venc_gcon*/
	node = of_find_compatible_node(NULL, NULL, "mediatek,venc_global_con");
	if (!node)
		pr_debug("[CLK_VENC_GCON] find node failed\n");
	clk_venc_gcon_base = of_iomap(node, 0);
	if (!clk_venc_gcon_base)
		pr_debug("[CLK_VENC_GCON] base failed\n");

/*cam*/
	node = of_find_compatible_node(NULL, NULL, "mediatek,camsys");
	if (!node)
		pr_debug("[CLK_CAM] find node failed\n");
	clk_camsys_base = of_iomap(node, 0);
	if (!clk_camsys_base)
		pr_debug("[CLK_CAM] base failed\n");

/*IPU Vcore*/
	node = of_find_compatible_node(NULL, NULL, "mediatek,ipu_vcore");
	if (!node)
		pr_debug("[CLK_IPU Vcore] find node failed\n");
	clk_ipusys_vcore_base = of_iomap(node, 0);
	if (!clk_ipusys_vcore_base)
		pr_debug("[CLK_IPU Vcore] base failed\n");

/*IPU (conn)*/
	node = of_find_compatible_node(NULL, NULL, "mediatek,ipu_conn");
	if (!node)
		pr_debug("[CLK_IPU Conn] find node failed\n");
	clk_ipusys_conn_base = of_iomap(node, 0);
	if (!clk_ipusys_conn_base)
		pr_debug("[CLK_IPU Conn] base failed\n");

/*IPU core 0*/
	node = of_find_compatible_node(NULL, NULL, "mediatek,ipu_core0");
	if (!node)
		pr_debug("[CLK_IPU Core0] find node failed\n");
	clk_ipusys_core0_base = of_iomap(node, 0);
	if (!clk_ipusys_core0_base)
		pr_debug("[CLK_IPU core0] base failed\n");

/*IPU*/
	node = of_find_compatible_node(NULL, NULL, "mediatek,ipu_core1");
	if (!node)
		pr_debug("[CLK_IPU Core1] find node failed\n");
	clk_ipusys_core1_base = of_iomap(node, 0);
	if (!clk_ipusys_core1_base)
		pr_debug("[CLK_IPU core1] base failed\n");

/*IPU*/
	node = of_find_compatible_node(NULL, NULL, "mediatek,ipu_core2");
	if (!node)
		pr_debug("[CLK_IPU Core2] find node failed\n");
	clk_ipusys_core2_base = of_iomap(node, 0);
	if (!clk_ipusys_core2_base)
		pr_debug("[CLK_IPU core2] base failed\n");

}
#endif

/*Bus Protect*/
#if 0
void mfg_mtcmos_patch(void)
{
	clk_writel(MFG_CG_CLR, 0x00000001);/*AO*/
}
#endif

void mm0_mtcmos_before_power_off(void)
{
	clk_writel(MM_CG_CLR0, 0xffffffff);
	clk_writel(MM_CG_CLR1, 0x00003fff);
}

void mm0_mtcmos_after_power_on(void)
{
	clk_writel(MM_CG_CLR0, 0x040003ff);/*AO*/
	clk_writel(MMSYS_HW_DCM_1ST_DIS_SET0, 0x00000003);/*disable DCM*/
	clk_writel(SMI_DCM, clk_readl(SMI_DCM) & 0xfffffffe);/*disable DCM*/
}

void ven_mtcmos_patch(void)
{
	clk_writel(VENC_CG_SET, 0x00000111);

	/* Enable MM CGs: MM_SMI_COMMON, MM_SMI_LARB0, MM_SMI_LARB1, MM_GALS_COMM0, MM_GALS_COMM1,
	 *		  MM_GALS_VENC2MM
	 */
	clk_writel(MM_CG_CLR0, 0x00000003f);

	clk_writel(MMSYS_HW_DCM_1ST_DIS_SET0, 0x00000003);/*disable DCM*/
	clk_writel(SMI_DCM, clk_readl(SMI_DCM) & 0xfffffffe);/*disable DCM*/
}

void vde_mtcmos_patch(void)
{
	unsigned int temp = 0;

	temp = clk_readl(VDEC_GALS_CFG) | (0x1 << 24);
	clk_writel(VDEC_GALS_CFG, temp);
	clk_writel(VDEC_CKEN_SET, 0x00000001); /* AO */
	clk_writel(VDEC_LARB1_SET, 0x00000001); /* AO */

	/* Enable MM CGs: MM_SMI_COMMON, MM_SMI_LARB0, MM_SMI_LARB1, MM_GALS_COMM0, MM_GALS_COMM1,
	 *		  MM_GALS_VDEC2MM
	 */
	clk_writel(MM_CG_CLR0, 0x00000005f);
}

void isp_mtcmos_before_power_off(void)
{
	/* Enable MM CGs: MM_SMI_COMMON, MM_SMI_LARB0, MM_SMI_LARB1, MM_GALS_COMM0, MM_GALS_COMM1,
	 *		  MM_GALS_IMG2MM
	 */
	clk_writel(MM_CG_CLR0, 0x0000009f);/*prevent bus ack fail*/

	/* Enable IMG Larb5 & Larb2 CG */
	clk_writel(IMG_CG_CLR, 0x3);/*AO*/

	/* Enable IPU VCORE AXI & AHB & ADL 3 CGs when IPU VCORE is alive. */
	if (ipuvcore_get_state_op(NULL) == STA_POWER_ON)
		clk_writel(IPU_VCORE_CG_CLR, 0x00000007); /*AO*/
}

void isp_mtcmos_after_power_on(void)
{
	clk_writel(IMG_CG_CLR, 0x3);/*AO*/
}

void cam_mtcmos_before_power_off(void)
{
	/* Enable CAM Larb3 & Larb6 CG */
	clk_writel(CAMSYS_CG_CLR, 0x00000005);/*AO*/

	/* Enable MM CGs: MM_SMI_COMMON, MM_SMI_LARB0, MM_SMI_LARB1, MM_GALS_COMM0, MM_GALS_COMM1,
	 *		  MM_GALS_CAM2MM
	 */
	clk_writel(MM_CG_CLR0, 0x0000011f);/*prevent bus ack fail*/

	/* Enable IPU VCORE AXI & AHB & ADL 3 CGs when IPU VCORE is alive. */
	if (subsys_is_on(SYS_IPU_VCORE_SHUTDOWN))
		clk_writel(IPU_VCORE_CG_CLR, 0x00000007); /*AO*/
}

void cam_mtcmos_after_power_on(void)
{
	/* Enable CAM Larb3 & Larb6 CG */
	clk_writel(CAMSYS_CG_CLR, 0x00000005);/*AO*/
}

void ipu_mtcmos_before_power_off(void)
{
	/* Enable MM CGs: MM_SMI_COMMON, MM_SMI_LARB0, MM_SMI_LARB1, MM_GALS_COMM0, MM_GALS_COMM1,
	 *		  MM_GALS_IPU2MM
	 */
	clk_writel(MM_CG_CLR0, 0x0000021f);/*prevent bus ack fail*/

	/* Enable CAM/LARB3 CG if CAM is alive */
	if (subsys_is_on(SYS_CAM))
		clk_writel(CAMSYS_CG_CLR, 0x00000004);

	/* Enable ISP/LARB2 CG if ISP is alive */
	if (subsys_is_on(SYS_ISP))
		clk_writel(IMG_CG_CLR, 0x00000002);

	clk_writel(IPU_VCORE_CG_CLR, 0x00000007); /*AO*/
}

void ipu_mtcmos_after_power_on(void)
{
	clk_writel(IPU_VCORE_CG_CLR, 0x00000007); /*AO*/
}

/* For IPU Core0, Core1, and Core2 */
void ipu_core_mtcmos_before_power_off(void)
{
	clk_writel(IPU_CONN_CG_CLR, 0x00000007); /* IPU_CG, AHB_CG, AXI_CG */
}

void mm_clk_restore(void)
{
	int ret4, ret5, ret6, ret7, ret8 = 7;
	struct clk *gals_venc2mm = __clk_lookup("mm_gals_venc2mm");
	struct clk *gals_vdec2mm = __clk_lookup("mm_gals_vdec2mm");
	struct clk *gals_img2mm = __clk_lookup("mm_gals_img2mm");
	struct clk *gals_cam2mm = __clk_lookup("mm_gals_cam2mm");
	struct clk *gals_ipu2mm = __clk_lookup("mm_gals_ipu2mm");

	ret4 = __clk_get_enable_count(gals_venc2mm);
	if (ret4 == 0)
		clk_writel(MM_CG_SET0, 0x00000020);

	ret5 = __clk_get_enable_count(gals_vdec2mm);
	if (ret5 == 0)
		clk_writel(MM_CG_SET0, 0x00000040);

	ret6 = __clk_get_enable_count(gals_img2mm);
	if (ret6 == 0)
		clk_writel(MM_CG_SET0, 0x00000080);

	ret7 = __clk_get_enable_count(gals_cam2mm);
	if (ret7 == 0)
		clk_writel(MM_CG_SET0, 0x00000100);

	ret8 = __clk_get_enable_count(gals_ipu2mm);
	if (ret8 == 0)
		clk_writel(MM_CG_SET0, 0x00000200);

}

static void __init mt_scpsys_init(struct device_node *node)
{
	struct clk_onecell_data *clk_data;
	void __iomem *infracfg_reg;
	void __iomem *spm_reg;
	void __iomem *smi_common_reg;
	void __iomem *smi_common_ext_reg;
	void __iomem *ipu_conn_reg;
	int r;

	infracfg_reg = get_reg(node, 0);
	spm_reg = get_reg(node, 1);
	smi_common_reg = get_reg(node, 2);
	infra_base = get_reg(node, 3);
	smi_common_ext_reg = get_reg(node, 4);
	ipu_conn_reg = get_reg(node, 5);

	if (!infracfg_reg || !spm_reg || !smi_common_reg || !smi_common_ext_reg || !ipu_conn_reg) {
		pr_debug("clk-pg-mt6775: missing reg\n");
		return;
	}

/*
*   pr_debug("[CCF] %s: sys: %s, reg: 0x%p, 0x%p\n",
*		__func__, node->name, infracfg_reg, spm_reg);
*/
	clk_data = alloc_clk_data(SCP_NR_SYSS);

	init_clk_scpsys(infracfg_reg, spm_reg, smi_common_reg, smi_common_ext_reg, ipu_conn_reg, clk_data);

	r = of_clk_add_provider(node, of_clk_src_onecell_get, clk_data);
	if (r)
		pr_notice("[CCF] %s:could not register clock provide\n", __func__);

	/*MM Bus*/
	iomap_mm();

#if !MT_CCF_BRINGUP
	/* subsys init: per modem owner request, disable modem power first */
	disable_subsys(SYS_MD1);
	disable_subsys(SYS_C2K);
	/*spm_mtcmos_ctrl_ipu_shutdown(STA_POWER_ON);*/
	/*spm_mtcmos_ctrl_isp(STA_POWER_ON);*/
	/*spm_mtcmos_ctrl_cam(STA_POWER_ON);*/

	/*spm_mtcmos_ctrl_mfg0(STA_POWER_ON);*/
	/*spm_mtcmos_ctrl_mfg1(STA_POWER_ON);*/
	/*spm_mtcmos_ctrl_mfg2(STA_POWER_ON);*/
	/*spm_mtcmos_ctrl_mfg3(STA_POWER_ON);*/
	/*spm_mtcmos_ctrl_mfg4(STA_POWER_ON);*/
	/*spm_mtcmos_ctrl_mfg5(STA_POWER_ON);*/

	/* spm_mtcmos_ctrl_ipu_vcore_shut_down(STA_POWER_ON); */
	/* spm_mtcmos_ctrl_ipu_shut_down(STA_POWER_ON); */
#else
	/*power on all subsys for bring up */

#ifndef CONFIG_FPGA_EARLY_PORTING

	spm_mtcmos_ctrl_mfg0(STA_POWER_ON);
	spm_mtcmos_ctrl_mfg1(STA_POWER_ON);
	spm_mtcmos_ctrl_mfg2(STA_POWER_ON);
	spm_mtcmos_ctrl_mfg3(STA_POWER_ON);
	spm_mtcmos_ctrl_mfg4(STA_POWER_ON);
	spm_mtcmos_ctrl_mfg5(STA_POWER_ON);
	/*spm_mtcmos_ctrl_c2k(STA_POWER_ON);*/
	spm_mtcmos_ctrl_md1(STA_POWER_DOWN);/*do after ccif*/
	/*spm_mtcmos_ctrl_conn(STA_POWER_ON);*/
	spm_mtcmos_ctrl_aud(STA_POWER_ON);
	spm_mtcmos_ctrl_mm0(STA_POWER_ON);
	spm_mtcmos_ctrl_cam(STA_POWER_ON);
	spm_mtcmos_ctrl_isp(STA_POWER_ON);
	spm_mtcmos_ctrl_ven(STA_POWER_ON);
	spm_mtcmos_ctrl_vde(STA_POWER_ON);
	spm_mtcmos_ctrl_ipu_vcore_shut_down(STA_POWER_ON);
	spm_mtcmos_ctrl_ipu_shut_down(STA_POWER_ON);
	spm_mtcmos_ctrl_ipu_core0_shut_down(STA_POWER_ON);
	spm_mtcmos_ctrl_ipu_core1_shut_down(STA_POWER_ON);
	spm_mtcmos_ctrl_ipu_core2_shut_down(STA_POWER_ON);

#endif
#endif	/* !MT_CCF_BRINGUP */
	clk_writel(SPM_POWER_ON_VAL0, 0x10f80000);
	clk_writel(SPM_POWER_ON_VAL1, 0x15830);
}

CLK_OF_DECLARE(mtk_pg_regs, "mediatek,scpsys", mt_scpsys_init);

int mtcmos_mfg_series_on(void)
{
	unsigned int sta = spm_read(PWR_STATUS);
	unsigned int sta_s = spm_read(PWR_STATUS_2ND);

	int ret;

	ret = 0;
	ret |= (sta & (1U << 1)) && (sta_s & (1U << 1));
	ret |= ((sta & (1U << 2)) && (sta_s & (1U << 2))) << 1;
	ret |= ((sta & (1U << 3)) && (sta_s & (1U << 3))) << 2;
	ret |= ((sta & (1U << 4)) && (sta_s & (1U << 4))) << 3;
	ret |= ((sta & (1U << 5)) && (sta_s & (1U << 5))) << 4;
	ret |= ((sta & (1U << 23)) && (sta_s & (1U << 23))) << 5;	/* #define MFG5_PWR_STA_MASK  (0x1 << 23)  */

	/*mfgsys_cg_check();*/
	return ret;
}


void subsys_if_on(void)
{
	unsigned int sta = spm_read(PWR_STATUS);
	unsigned int sta_s = spm_read(PWR_STATUS_2ND);
	int ret = 0;

	if ((sta & (1U << 1)) && (sta_s & (1U << 1))) {
		pr_notice("suspend warning: SYS_MFG0 is on!!!\n");
		ret++;
	}
	if ((sta & (1U << 2)) && (sta_s & (1U << 2))) {
		pr_notice("suspend warning: SYS_MFG1 is on!!!\n");
		ret++;
	}
	if ((sta & (1U << 3)) && (sta_s & (1U << 3))) {
		pr_notice("suspend warning: SYS_MFG2 is on!!!\n");
		ret++;
	}
	if ((sta & (1U << 4)) && (sta_s & (1U << 4))) {
		pr_notice("suspend warning: SYS_MFG3 is on!!!\n");
		ret++;
	}
	if ((sta & (1U << 5)) && (sta_s & (1U << 5))) {
		pr_notice("suspend warning: SYS_MFG4 is on!!!\n");
		ret++;
	}
	if ((sta & (1U << 23)) && (sta_s & (1U << 23))) {
		pr_notice("suspend warning: SYS_MFG5 is on!!!\n");
		ret++;
	}

	if ((sta & (1U << 6)) && (sta_s & (1U << 6)))
		pr_notice("suspend warning: SYS_C2K is on!!!\n");

	if ((sta & (1U << 7)) && (sta_s & (1U << 7)))
		pr_notice("suspend warning: SYS_MD1 is on!!!\n");

	if ((sta & (1U << 10)) && (sta_s & (1U << 10)))
		pr_notice("suspend warning: SYS_CONN is on!!!\n");

	if ((sta & (1U << 12)) && (sta_s & (1U << 12)))
		pr_notice("suspend warning: SYS_AUD is on!!!\n");
	if ((sta & (1U << 14)) && (sta_s & (1U << 14))) {
		pr_notice("suspend warning: SYS_MM0 is on!!!\n");
		pr_debug("MM_CG(%08x): %08x, %08x\r\n",
			spm_read(MM0_PWR_CON),
			spm_read(MM_CG_0),
			spm_read(MM_CG_1));
		ret++;
	}
	if ((sta & (1U << 15)) && (sta_s & (1U << 15))) {
		pr_notice("suspend warning: SYS_CAM is on!!!\n");
		pr_debug("CAM_CG(%08x): %08x\r\n",
			spm_read(CAM_PWR_CON),
			spm_read(CAMSYS_CG));
		ret++;
	}
	if ((sta & (1U << 16)) && (sta_s & (1U << 16))) {
		pr_notice("suspend warning: SYS_IPU_CONN is on!!!\n");
		pr_debug("IPU_CONN_CG(%08x): %08x\r\n",
			spm_read(IPU_PWR_CON),
			spm_read(IPU_CONN_CG_CON));
		ret++;
	}
	if ((sta & (1U << 17)) && (sta_s & (1U << 17))) {
		pr_notice("suspend warning: SYS_ISP is on!!!\n");
		pr_debug("IMG_CG(%08x): %08x\r\n",
			spm_read(ISP_PWR_CON),
			spm_read(IMG_CG));
		ret++;
	}
	if ((sta & (1U << 18)) && (sta_s & (1U << 18))) {
		pr_notice("suspend warning: SYS_VEN is on!!!\n");
		pr_debug("VENC_CG(%08x): %08x\r\n",
			spm_read(VEN_PWR_CON),
			spm_read(VENC_CG));
		ret++;
	}
	if ((sta & (1U << 19)) && (sta_s & (1U << 19))) {
		pr_notice("suspend warning: SYS_VDE is on!!!\n");
		pr_debug("VDE_CG(%08x): %08x, %08x, %08x\r\n",
			spm_read(VDE_PWR_CON),
			spm_read(VDEC_CKEN_SET),
			spm_read(VDEC_LARB1_SET),
			spm_read(VDEC_GALS_CFG));
		ret++;
	}

	if ((sta & (1U << 20)) && (sta_s & (1U << 20))) {
		pr_notice("suspend warning: SYS_IPU_CORE0 is on!!!\n");
		pr_debug("IPU Core0_CG(%08x): %08x\r\n",
			spm_read(IPU_CORE0_PWR_CON),
			spm_read(IPU_CORE0_CG_CON));
		ret++;
	}

	if ((sta & (1U << 21)) && (sta_s & (1U << 21))) {
		pr_notice("suspend warning: SYS_IPU_CORE1 is on!!!\n");
		pr_debug("IPU Core1_CG(%08x): %08x\r\n",
			spm_read(IPU_CORE1_PWR_CON),
			spm_read(IPU_CORE1_CG_CON));
		ret++;
	}

	if ((sta & (1U << 22)) && (sta_s & (1U << 22))) {
		pr_notice("suspend warning: SYS_IPU_CORE2 is on!!!\n");
		pr_debug("IPU Core2_CG(%08x): %08x\r\n",
			spm_read(IPU_CORE2_PWR_CON),
			spm_read(IPU_CORE2_CG_CON));
		ret++;
	}

#if 0 /*  todo ++  */
	/* According DE comment, IPU VCore Mtcmos should be always on. */
	if ((sta & (1U << 28)) && (sta_s & (1U << 28))) {
		pr_notice("suspend warning: SYS_IPU_VCORE is on!!!\n");
		pr_debug("IPU VCore (%08x): %08x\r\n",
			spm_read(IPU_VCORE_PWR_ACK_STA_MASK),
			spm_read(IPU_VCORE_CG_CON));
		ret++;
	}
#endif /* todo -- */

	if (ret > 0)
		WARN_ON(1);
}

#if 1 /*only use for suspend test*/
void mtcmos_force_off(void)
{
	spm_mtcmos_ctrl_mfg5(STA_POWER_DOWN);
	spm_mtcmos_ctrl_mfg4(STA_POWER_DOWN);
	spm_mtcmos_ctrl_mfg3(STA_POWER_DOWN);
	spm_mtcmos_ctrl_mfg2(STA_POWER_DOWN);
	spm_mtcmos_ctrl_mfg1(STA_POWER_DOWN);
	spm_mtcmos_ctrl_mfg0(STA_POWER_DOWN);

	spm_mtcmos_ctrl_conn(STA_POWER_DOWN);

	spm_mtcmos_ctrl_aud(STA_POWER_DOWN);
	spm_mtcmos_ctrl_cam(STA_POWER_DOWN);
	spm_mtcmos_ctrl_isp(STA_POWER_DOWN);
	spm_mtcmos_ctrl_ven(STA_POWER_DOWN);
	spm_mtcmos_ctrl_vde(STA_POWER_DOWN);

	spm_mtcmos_ctrl_ipu_core2_shut_down(STA_POWER_DOWN);
	spm_mtcmos_ctrl_ipu_core1_shut_down(STA_POWER_DOWN);
	spm_mtcmos_ctrl_ipu_core0_shut_down(STA_POWER_DOWN);
	spm_mtcmos_ctrl_ipu_shut_down(STA_POWER_DOWN);

	/* According DE comment, IPU VCore Mtcmos should be always on. */
	/* spm_mtcmos_ctrl_ipu_vcore_shut_down(STA_POWER_DOWN); */

	spm_mtcmos_ctrl_mm0(STA_POWER_DOWN);

}
#endif

#if CLK_DEBUG
/*
 * debug / unit test
 */

#include <linux/proc_fs.h>
#include <linux/fs.h>
#include <linux/seq_file.h>
#include <linux/uaccess.h>

static char last_cmd[128] = "null";

static int test_pg_dump_regs(struct seq_file *s, void *v)
{
	int i;

	for (i = 0; i < NR_SYSS; i++) {
		if (!syss[i].ctl_addr)
			continue;

		seq_printf(s, "%10s: [0x%p]: 0x%08x\n", syss[i].name,
			   syss[i].ctl_addr, clk_readl(syss[i].ctl_addr));
	}

	return 0;
}

static void dump_pg_state(const char *clkname, struct seq_file *s)
{
	struct clk *c = __clk_lookup(clkname);
	struct clk *p = IS_ERR_OR_NULL(c) ? NULL : __clk_get_parent(c);

	if (IS_ERR_OR_NULL(c)) {
		seq_printf(s, "[%17s: NULL]\n", clkname);
		return;
	}

	seq_printf(s, "[%17s: %3s, %3d, %3d, %10ld, %17s]\n",
		   __clk_get_name(c),
		   __clk_is_enabled(c) ? "ON" : "off",
		   __clk_get_prepare_count(c),
		   __clk_get_enable_count(c), __clk_get_rate(c), p ? __clk_get_name(p) : "");


	clk_put(c);
}

static int test_pg_dump_state_all(struct seq_file *s, void *v)
{
	static const char *const clks[] = {
		pg_mfg0,
		pg_mfg1,
		pg_mfg2,
		pg_mfg3,
		pg_mfg4,
		pg_mfg5,
		pg_c2k,
		pg_md1,
		pg_conn,
		pg_aud,
		pg_mm0,
		pg_cam,
		pg_isp,
		pg_ven,
		pg_vde,
		pg_ipu_vcore_shutdown,
		pg_ipu_shutdown,
		pg_ipu_core0_shutdown,
		pg_ipu_core0_sleep,
		pg_ipu_core1_shutdown,
		pg_ipu_core1_sleep,
		pg_ipu_core2_shutdown,
		pg_ipu_core2_sleep,
	};

	int i;

/*	pr_debug("\n");*/
	for (i = 0; i < ARRAY_SIZE(clks); i++)
		dump_pg_state(clks[i], s);

	return 0;
}

static struct {
	const char *name;
	struct clk *clk;
} g_clks[] = {
	{
	.name = pg_md1}, {
	.name = pg_vde}, {
	.name = pg_ven}, {
.name = pg_mfg},};

static int test_pg_1(struct seq_file *s, void *v)
{
	int i;

/*	pr_debug("\n");*/

	for (i = 0; i < ARRAY_SIZE(g_clks); i++) {
		g_clks[i].clk = __clk_lookup(g_clks[i].name);
		if (IS_ERR_OR_NULL(g_clks[i].clk)) {
			seq_printf(s, "clk_get(%s): NULL\n", g_clks[i].name);
			continue;
		}

		clk_prepare_enable(g_clks[i].clk);
		seq_printf(s, "clk_prepare_enable(%s)\n", __clk_get_name(g_clks[i].clk));
	}

	return 0;
}

static int test_pg_2(struct seq_file *s, void *v)
{
	int i;

/*	pr_debug("\n");*/

	for (i = 0; i < ARRAY_SIZE(g_clks); i++) {
		if (IS_ERR_OR_NULL(g_clks[i].clk)) {
			seq_printf(s, "(%s).clk: NULL\n", g_clks[i].name);
			continue;
		}

		seq_printf(s, "clk_disable_unprepare(%s)\n", __clk_get_name(g_clks[i].clk));
		clk_disable_unprepare(g_clks[i].clk);
		clk_put(g_clks[i].clk);
	}

	return 0;
}

static int test_pg_show(struct seq_file *s, void *v)
{
	static const struct {
		int (*fn)(struct seq_file *, void *);
		const char *cmd;
	} cmds[] = {
		{
		.cmd = "dump_regs", .fn = test_pg_dump_regs}, {
		.cmd = "dump_state", .fn = test_pg_dump_state_all}, {
		.cmd = "1", .fn = test_pg_1}, {
	.cmd = "2", .fn = test_pg_2},};

	int i;

/*	pr_debug("last_cmd: %s\n", last_cmd);*/

	for (i = 0; i < ARRAY_SIZE(cmds); i++) {
		if (strcmp(cmds[i].cmd, last_cmd) == 0)
			return cmds[i].fn(s, v);
	}

	return 0;
}

static int test_pg_open(struct inode *inode, struct file *file)
{
	return single_open(file, test_pg_show, NULL);
}

static ssize_t test_pg_write(struct file *file,
			     const char __user *buffer, size_t count, loff_t *data)
{
	char desc[sizeof(last_cmd)];
	int len = 0;

/*	pr_debug("count: %zu\n", count);*/
	len = (count < (sizeof(desc) - 1)) ? count : (sizeof(desc) - 1);
	if (copy_from_user(desc, buffer, len))
		return 0;

	desc[len] = '\0';
	strcpy(last_cmd, desc);
	if (last_cmd[len - 1] == '\n')
		last_cmd[len - 1] = 0;

	return count;
}

static const struct file_operations test_pg_fops = {
	.owner = THIS_MODULE,
	.open = test_pg_open,
	.read = seq_read,
	.write = test_pg_write,
	.llseek = seq_lseek,
	.release = single_release,
};

static int __init debug_init(void)
{
	static int init;
	struct proc_dir_entry *entry;

/*	pr_debug("init: %d\n", init);*/

	if (init)
		return 0;

	++init;

	entry = proc_create("test_pg", 0, 0, &test_pg_fops);
	if (!entry)
		return -ENOMEM;

	++init;
	return 0;
}

static void __exit debug_exit(void)
{
	remove_proc_entry("test_pg", NULL);
}

module_init(debug_init);
module_exit(debug_exit);

#endif				/* CLK_DEBUG */
