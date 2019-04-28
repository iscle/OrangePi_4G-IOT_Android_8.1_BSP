/*
 * Copyright (C) 2017 MediaTek Inc.
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

#include <linux/kernel.h>
#include <linux/module.h>
#include <linux/interrupt.h>
#include <linux/device.h>
#include <linux/platform_device.h>
#include <linux/mm.h>
#include <linux/uaccess.h>
#include <linux/slab.h>
#include <linux/spinlock.h>
#include <linux/irq.h>
#include <linux/sched.h>
#include <linux/cdev.h>
#include <linux/init.h>
#include <linux/fs.h>
#include <linux/proc_fs.h>
#include <linux/device.h>
#include <linux/platform_device.h>
#include <linux/of_platform.h>
#include <linux/of_irq.h>
#include <linux/of_address.h>
#include <linux/types.h>

#ifdef CONFIG_MTK_HIBERNATION
#include <mtk_hibernate_dpm.h>
#endif

/* CCF */
#include <linux/clk.h>

#include "mtk_io.h"
#include "sync_write.h"
#include "devapc.h"

#if defined(CONFIG_MTK_AEE_FEATURE) && defined(DEVAPC_ENABLE_AEE)
#include <mt-plat/aee.h>
#endif

/* 0 for early porting */
#define DEVAPC_TURN_ON         1
#define DEVAPC_USE_CCF         1

/* Debug message event */
#define DEVAPC_LOG_NONE        0x00000000
#define DEVAPC_LOG_ERR         0x00000001
#define DEVAPC_LOG_WARN        0x00000002
#define DEVAPC_LOG_INFO        0x00000004
#define DEVAPC_LOG_NOTICE      0x00000008
#define DEVAPC_LOG_DBG         0x00000010

#define DEVAPC_LOG_LEVEL      (DEVAPC_LOG_INFO)

#define DEVAPC_MSG(fmt, args...) \
	do {    \
		if (DEVAPC_LOG_LEVEL & DEVAPC_LOG_DBG) { \
			pr_debug(fmt, ##args); \
		} else if (DEVAPC_LOG_LEVEL & DEVAPC_LOG_INFO) { \
			pr_info(fmt, ##args); \
		} \
	} while (0)


#define DEVAPC_VIO_LEVEL      (DEVAPC_LOG_NOTICE)

#define DEVAPC_VIO_MSG(fmt, args...) \
	do {    \
		if (DEVAPC_VIO_LEVEL & DEVAPC_LOG_DBG) { \
			pr_debug(fmt, ##args); \
		} else if (DEVAPC_VIO_LEVEL & DEVAPC_LOG_INFO) { \
			pr_info(fmt, ##args); \
		} else if (DEVAPC_VIO_LEVEL & DEVAPC_LOG_NOTICE) { \
			pr_notice(fmt, ##args); \
		} \
	} while (0)


#if DEVAPC_USE_CCF
static struct clk *dapc_infra_clk;
static struct clk *dapc_peri_clk;
#endif

static struct cdev *g_devapc_ctrl;
static unsigned int devapc_infra_irq;
static unsigned int devapc_peri_irq;
static void __iomem *devapc_pd_infra_base;
static void __iomem *devapc_pd_peri_base;

static unsigned int enable_dynamic_one_core_violation_debug;

#if defined(CONFIG_MTK_AEE_FEATURE) && defined(DEVAPC_ENABLE_AEE)
unsigned long long devapc_infra_vio_first_trigger_time[DEVAPC_INFRA_TOTAL_SLAVES];
unsigned long long devapc_peri_vio_first_trigger_time[DEVAPC_PERI_TOTAL_SLAVES];

/* violation times */
unsigned int devapc_infra_vio_count[DEVAPC_INFRA_TOTAL_SLAVES];
unsigned int devapc_peri_vio_count[DEVAPC_PERI_TOTAL_SLAVES];

/* show the status of whether AEE warning has been populated */
unsigned int devapc_infra_vio_aee_shown[DEVAPC_INFRA_TOTAL_SLAVES];
unsigned int devapc_peri_vio_aee_shown[DEVAPC_PERI_TOTAL_SLAVES];
unsigned int devapc_vio_current_aee_trigger_times;
#endif

#if DEVAPC_TURN_ON
static struct DEVICE_INFO devapc_infra_devices[] = {
	/* device name                          enable_vio_irq */

	/* 0 */
	{"INFRA_AO_INFRASYS_CONFIG_REGS",         true    },
	{"INFRA_AO_PMIC_WRAP",                    true    },
	{"RESERVED",                              true    },
	{"INFRA_AO_KEYPAD",                       true    },
	{"INFRA_AO_APXGPT",                       true    },
	{"INFRA_AO_AP_CIRQ_EINT",                 true    },
	{"INFRA_AO_DEVICE_APC_MPU",               true    },
	{"INFRA_AO_DEVICE_APC_AO",                true    },
	{"INFRA_AO_SEJ",                          true    },
	{"RESERVED",                              true    },

	/* 10 */
	{"RESERVED",                              true    },
	{"INFRA_AO_AES_TOP_0",                    true    },
	{"INFRA_AO_AES_TOP_1",                    true    },
	{"INFRA_AO_MDEM_TEMP_SHARE",              true    },
	{"INFRA_SYSTEM_TIMER",                    true    },
	{"INFRA_AO_CLDMA_AO_TOP_AP",              true    },
	{"INFRA_AO_CLDMA_AO_TOP_MD",              true    },
	{"INFRA_AO_DVFS_CTRL_PROC",               true    },
	{"INFRA_AO_CRYPTO_WRAPPER",               true    },
	{"INFRA_AO_SSHUB",                        true    },

	/* 20 */
	{"INFRA_AO_SSPM_0",                       true    },
	{"INFRA_AO_SSPM_1",                       true    },
	{"INFRA_AO_SSPM_2",                       true    },
	{"INFRA_AO_SSPM_3",                       true    },
	{"INFRA_AO_SSPM_4",                       true    },
	{"INFRA_AO_SSPM_5",                       true    },
	{"INFRA_AO_SSPM_6",                       true    },
	{"INFRA_AO_SSPM_7",                       true    },
	{"INFRA_AO_SSPM_8",                       true    },
	{"INFRA_AO_SLEEP_CONTROL_PROCESSOR_0",    true    },

	/* 30 */
	{"INFRA_AO_SLEEP_CONTROL_PROCESSOR_1",    true    },
	{"INFRA_AO_SLEEP_CONTROL_PROCESSOR_2",    true    },
	{"RESERVED",                              true    },
	{"INFRASYS_TOP_MODULE",                   true    },
	{"INFRASYS_SECURITY_GROUP",               true    },
	{"INFRASYS_EMI_GROUP",                    true    },
	{"INFRASYS_CCIF0_1_CLDMA_AP",             true    },
	{"INFRASYS_CCIF2_3_AP",                   true    },
	{"INFRASYS_CFG_GROUP",                    true    },
	{"INFRASYS_SYS_CIRQ",                     true    },

	/* 40 */
	{"INFRASYS_GCE_GROUP",                    true    },
	{"INFRASYS_SMI_GROUP",                    true    },
	{"INFRASYS_PTP_THERMAL_GROUP",            true    },
	{"INFRASYS_MM_IOMMU_GROUP",               true    },
	{"RESERVED",                              true    },
	{"INFRASYS_GPIO_GROUP",                   true    },
	{"INFRASYS_BUS_TRACE_GROUP",              true    },
	{"INFRASYS_EMI_MPU_REG",                  true    },
	{"INFRASYS_CCIF4_5_AP",                   true    },
	{"INFRASYS_AUXADC",                       true    },

	/* 50 */
	{"INFRASYS_BSI_BPI_GROUP",                true    },
	{"INFRASYS_DRAMC_0_GROUP",                true    },
	{"CSI_",                                  true    },
	{"EAST_IO_R",                             true    },
	{"EAST_IO_RT",                            true    },
	{"RESERVED",                              true    },
	{"SSUSB_PHY",                             true    },
	{"EFUSE",                                 true    },
	{"SOUTH_IO_B",                            true    },
	{"SOUTH_IO_BR",                           true    },

	/* 60 */
	{"MIPI_DSI0",                             true    },
	{"MIPI_DSI1",                             true    },
	{"WEST_IO_L",                             true    },
	{"EAST_RESERVE_B",                        true    },
	{"UFS_MPHY",                              true    },
	{"MSDC_PAD_MACRO",                        true    },
	{"NORTH_IO_T",                            true    },
	{"RESERVED",                              true    },
	{"INFRASYS_CCIF0_MD1",                    true    },
	{"INFRASYS_CCIF1_C2K",                    true    },

	/* 70 */
	{"INFRASYS_CCIF2_MD1",                    true    },
	{"INFRASYS_CCIF3_C2K",                    true    },
	{"INFRASYS_CLDMA_MD",                     true    },
	{"INFRASYS_MD1_C2K_CCIF",                 true    },
	{"INFRASYS_C2K_MD1_CCIF",                 true    },
	{"INFRASYS_CCIF4_MD1",                    true    },
	{"INFRASYS_CCIF5_C2K",                    true    },
	{"RESERVED",                              true    },
	{"INFRA_PDN_MCSIB",                       true    },
	{"INFRASYS_AUDIO",                        true    },

	/* 80 */
	{"CONNSYS",                               true    },
	{"MD1",                                   true    },
	{"MD3",                                   true    },
	{"MFG_RGX_JONES_0",                       true    },
	{"MFG_RGX_JONES_1",                       true    },
	{"MFG_RGX_JONES_2",                       true    },
	{"MFG_RGX_JONES_3",                       true    },
	{"MFG_RGX_JONES_4",                       true    },
	{"MFG_RGX_JONES_5",                       true    },
	{"IPUSYS_TOP",                            true    },

	/* 90 */
	{"IPU_DMEM",                              true    },
	{"IPU_DMEM",                              true    },
	{"IPU_IMEM",                              true    },
	{"IPU_CORE",                              true    },
	{"IPUSYS_POWER_METER",                    true    },
	{"MMSYS_CONFIG",                          true    },
	{"MDP_RDMA0",                             true    },
	{"MDP_RDMA1",                             true    },
	{"MDP_RSZ0",                              true    },
	{"MDP_RSZ1",                              true    },

	/* 100 */
	{"MDP_WROT0",                             true    },
	{"MDP_WROT1",                             true    },
	{"MDP_TDSHP",                             true    },
	{"DISP_OVL0",                             true    },
	{"DISP_OVL0_2L",                          true    },
	{"DISP_OVL1_2L",                          true    },
	{"DISP_RDMA0",                            true    },
	{"DISP_RDMA1",                            true    },
	{"DISP_WDMA0",                            true    },
	{"DISP_COLOR0",                           true    },

	/* 110 */
	{"DISP_CCORR0",                           true    },
	{"DISP_AAL0",                             true    },
	{"DISP_GAMMA0",                           true    },
	{"DISP_DITHER0",                          true    },
	{"RESERVED",	                          true    },
	{"DSI0",		                          true    },
	{"DPI0",                                  true    },
	{"MM_MUTEX",                              true    },
	{"SMI_LARB0",                             true    },
	{"SMI_LARB1",                             true    },

	/* 120 */
	{"SMI0_COMMON",                           true    },
	{"RESERVED",	                          true    },
	{"RESERVED",	                          true    },
	{"RESERVED",	                          true    },
	{"RESERVED",	                          true    },
	{"IMGSYS_TOP",                            true    },
	{"SMI_LARB5",                             true    },
	{"DIP_A0",                                true    },
	{"DIP_A1",                                true    },
	{"DIP_A_NBC",                             true    },

	/* 130 */
	{"RESERVED",	                          true    },
	{"DPE",                                   true    },
	{"RSC",                                   true    },
	{"RESERVED",	                          true    },
	{"FDVT",                                  true    },
	{"RESERVED",	                          true    },
	{"RESERVED",	                          true    },
	{"RESERVED",	                          true    },
	{"SMI_LARB2",                             true    },
	{"VDEC_TOP_GLOBAL_CON",                   true    },

	/* 140 */
	{"VDEC_TOP_SMI_LARB4",                    true    },
	{"VDEC_TOP_FULL_TOP",                     true    },
	{"RESERVED",	                          true    },
	{"RESERVED",	                          true    },
	{"VENC_GLOBAL_CON",                       true    },
	{"SMI_LARB7",                             true    },
	{"VENC",                                  true    },
	{"JPGENC",                                true    },
	{"RESERVED",	                          true    },
	{"RESERVED",	                          true    },

	/* 150 */
	{"RESERVED",	                          true    },
	{"CAMSYS_TOP",                            true    },
	{"SMI_LARB6",                             true    },
	{"SMI_LARB3",                             true    },
	{"CAM_TOP",                               true    },
	{"CAM_A",                                 true    },
	{"CAM_B",                                 true    },
	{"RESERVED",	                          true    },
	{"RESERVED",	                          true    },
	{"RESERVED",	                          true    },

	/* 160 */
	{"RESERVED",	                          true    },
	{"RESERVED",	                          true    },
	{"CAM_TOP_SET",                           true    },
	{"CAM_A_SET",                             true    },
	{"CAM_B_SET",                             true    },
	{"RESERVED",	                          true    },
	{"RESERVED",	                          true    },
	{"CAM_TOP_INNER",                         true    },
	{"CAM_A_INNER",                           true    },
	{"CAM_B_INNER",                           true    },

	/* 170 */
	{"RESERVED",	                          true    },
	{"RESERVED",	                          true    },
	{"RESERVED",	                          true    },
	{"CAM_TOP_CLR",                           true    },
	{"CAM_A_CLR",                             true    },
	{"CAM_B_CLR",                             true    },
	{"RESERVED",	                          true    },
	{"RESERVED",	                          true    },
	{"CAM_A_EXT",                             true    },
	{"CAM_B_EXT",                             true    },

	/* 180 */
	{"RESERVED",	                          true    },
	{"RESERVED",	                          true    },
	{"SENINF_A",                              true    },
	{"SENINF_B",                              true    },
	{"SENINF_C",                              true    },
	{"SENINF_D",                              true    },
	{"SENINF_E",                              true    },
	{"SENINF_F",                              true    },
	{"SENINF_G",                              true    },
	{"SENINF_H",                              true    },

	/* 190 */
	{"CAMSV_A",                               true    },
	{"CAMSV_B",                               true    },
	{"CAMSV_C",                               true    },
	{"CAMSV_D",                               true    },
	{"MD32_DMEM",                             true    },
	{"RESERVED",	                          true    },
	{"MD32_PMEM",                             true    },
	{"MD32+TSF",                              true    },
	{"MM_DEVAPC_MPU_SI0_DEVICE_VIO_IN",       true    },
	{"MM_DEVAPC_MPU_SI1_DEVICE_VIO_IN",       true    },

	/* 200 */
	{"MFG_DEVAPC_MPU_SI0_DEVICE_VIO_IN",      true    },
	{"MFG_DEVAPC_MPU_SI1_DEVICE_VIO_IN",      true    },
	{"PERI_DEVAPC_MPU_SI0_DEVICE_VIO_IN",     true    },

};


static struct DEVICE_INFO devapc_peri_devices[] = {
	/* device name                          enable_vio_irq */

	/* 0 */
	{"APDMA",                                 true    },
	{"PERICFG_REG",                           true    },
	{"UART0",                                 true    },
	{"UART1",                                 true    },
	{"UART2",                                 true    },
	{"RESERVED",                              true    },
	{"RESERVED",                              true    },
	{"PWM",                                   true    },
	{"I2C0",                                  true    },
	{"I2C1",                                  true    },

	/* 10 */
	{"I2C2",                                  true    },
	{"SPI6",                                  true    },
	{"I2C4",                                  true    },
	{"I2C5",                                  true    },
	{"I2C6",                                  true    },
	{"SPI0",                                  true    },
	{"I2C3",                                  true    },
	{"RESERVED",                              true    },
	{"IRTX",                                  true    },
	{"SPI7",                                  true    },

	/* 20 */
	{"DISP_PWM0",                             true    },
	{"RESERVED",                              true    },
	{"SPI1",                                  true    },
	{"SPI2",                                  true    },
	{"RESERVED",                              true    },
	{"I2C7",                                  true    },
	{"PERI_DEVAPC",                           true    },
	{"SPI3",                                  true    },
	{"SPI4",                                  true    },
	{"SPI5",                                  true    },

	/* 30 */
	{"I2C8",                                  true    },
	{"I2C9",                                  true    },
	{"CQDMA",                                 true    },
	{"PERI_MBIST",                            true    },
	{"PERI_AO_MBIST",                         true    },
	{"DX_CC_PUBLIC",                          true    },
	{"DX_CC_PRIVATE",                         true    },
	{"TRNG",                                  true    },
	{"BTIF",                                  true    },
	{"PERI_DFP_TOP",                          true    },

	/* 40 */
	{"USB3.0_PORT0_CSR",                      true    },
	{"MSDC0",                                 true    },
	{"MSDC1",                                 true    },
	{"USB3.0_SUB_SECURITY_REGION",            true    },
	{"MSDC3",                                 true    },
	{"UFS_DEV",                               true    },
	{"USB3.0_PORT0_SIFSLV",                   true    },
	{"DEBUG_TOP",                             true    },

};
#endif

/*
 * The extern functions for EMI MPU are removed because EMI MPU and Device APC
 * do not share the same IRQ now.
 */

/**************************************************************************
*STATIC FUNCTION
**************************************************************************/

#ifdef CONFIG_MTK_HIBERNATION
static int devapc_pm_restore_noirq(struct device *device)
{
	if (devapc_infra_irq != 0) {
		mt_irq_set_sens(devapc_infra_irq, MT_LEVEL_SENSITIVE);
		mt_irq_set_polarity(devapc_infra_irq, MT_POLARITY_LOW);
	}

	if (devapc_peri_irq != 0) {
		mt_irq_set_sens(devapc_peri_irq, MT_LEVEL_SENSITIVE);
		mt_irq_set_polarity(devapc_peri_irq, MT_POLARITY_LOW);
	}

	return 0;
}
#endif

#if DEVAPC_TURN_ON
static int clear_infra_vio_status(unsigned int module)
{
	unsigned int apc_index = 0;
	unsigned int apc_bit_index = 0;

	if (module > PD_INFRA_VIO_STA_MAX_INDEX) {
		pr_err("[DEVAPC] clear_infra_vio_status: module overflow!\n");
		return -1;
	}

	apc_index = module / (MOD_NO_IN_1_DEVAPC * 2);
	apc_bit_index = module % (MOD_NO_IN_1_DEVAPC * 2);

	*DEVAPC_PD_INFRA_VIO_STA(apc_index) = (0x1 << apc_bit_index);

	return 0;
}

static int clear_peri_vio_status(unsigned int module)
{
	unsigned int apc_index = 0;
	unsigned int apc_bit_index = 0;

	if (module > PD_PERI_VIO_STA_MAX_INDEX) {
		pr_err("[DEVAPC] clear_peri_vio_status: module overflow!\n");
		return -1;
	}

	apc_index = module / (MOD_NO_IN_1_DEVAPC * 2);
	apc_bit_index = module % (MOD_NO_IN_1_DEVAPC * 2);

	*DEVAPC_PD_PERI_VIO_STA(apc_index) = (0x1 << apc_bit_index);

	return 0;
}

static void unmask_infra_module_irq(unsigned int module)
{
	unsigned int apc_index = 0;
	unsigned int apc_bit_index = 0;

	if (module > PD_INFRA_VIO_MASK_MAX_INDEX) {
		pr_err("[DEVAPC] unmask_infra_module_irq: module overflow!\n");
		return;
	}

	apc_index = module / (MOD_NO_IN_1_DEVAPC * 2);
	apc_bit_index = module % (MOD_NO_IN_1_DEVAPC * 2);

	*DEVAPC_PD_INFRA_VIO_MASK(apc_index) &= (0xFFFFFFFF ^ (1 << apc_bit_index));
}

static void unmask_peri_module_irq(unsigned int module)
{
	unsigned int apc_index = 0;
	unsigned int apc_bit_index = 0;

	if (module > PD_PERI_VIO_MASK_MAX_INDEX) {
		pr_err("[DEVAPC] unmask_peri_module_irq: module overflow!\n");
		return;
	}

	apc_index = module / (MOD_NO_IN_1_DEVAPC * 2);
	apc_bit_index = module % (MOD_NO_IN_1_DEVAPC * 2);

	*DEVAPC_PD_PERI_VIO_MASK(apc_index) &= (0xFFFFFFFF ^ (1 << apc_bit_index));
}

static int check_infra_vio_status(unsigned int module)
{
	unsigned int apc_index = 0;
	unsigned int apc_bit_index = 0;

	if (module > PD_INFRA_VIO_STA_MAX_INDEX) {
		pr_err("[DEVAPC] check_infra_vio_status: module overflow!\n");
		return -1;
	}

	apc_index = module / (MOD_NO_IN_1_DEVAPC * 2);
	apc_bit_index = module % (MOD_NO_IN_1_DEVAPC * 2);

	if (*DEVAPC_PD_INFRA_VIO_STA(apc_index) & (0x1 << apc_bit_index))
		return 1;

	return 0;
}

static int check_peri_vio_status(unsigned int module)
{
	unsigned int apc_index = 0;
	unsigned int apc_bit_index = 0;

	if (module > PD_PERI_VIO_STA_MAX_INDEX) {
		pr_err("[DEVAPC] check_peri_vio_status: module overflow!\n");
		return -1;
	}

	apc_index = module / (MOD_NO_IN_1_DEVAPC * 2);
	apc_bit_index = module % (MOD_NO_IN_1_DEVAPC * 2);

	if (*DEVAPC_PD_PERI_VIO_STA(apc_index) & (0x1 << apc_bit_index))
		return 1;

	return 0;
}

static void start_devapc(void)
{
	unsigned int i;

	mt_reg_sync_writel(0x80000000, DEVAPC_PD_INFRA_APC_CON);
	mt_reg_sync_writel(0x80000000, DEVAPC_PD_PERI_APC_CON);

	/* SMC call is called to set Device APC in LK instead */

	DEVAPC_MSG("[DEVAPC] INFRA VIO_STA 0:0x%x, 1:0x%x, 2:0x%x, 3:0x%x, 4:0x%x, 5:0x%x, 6:0x%x\n",
			   readl(DEVAPC_PD_INFRA_VIO_STA(0)), readl(DEVAPC_PD_INFRA_VIO_STA(1)),
			   readl(DEVAPC_PD_INFRA_VIO_STA(2)), readl(DEVAPC_PD_INFRA_VIO_STA(3)),
			   readl(DEVAPC_PD_INFRA_VIO_STA(4)), readl(DEVAPC_PD_INFRA_VIO_STA(5)),
			   readl(DEVAPC_PD_INFRA_VIO_STA(6)));

	DEVAPC_MSG("[DEVAPC] PERI VIO_STA 0:0x%x, 1:0x%x\n",
			   readl(DEVAPC_PD_PERI_VIO_STA(0)), readl(DEVAPC_PD_PERI_VIO_STA(1)));

	DEVAPC_MSG("[DEVAPC] INFRA VIO_MASK(B) 0:0x%x, 1:0x%x, 2:0x%x, 3:0x%x, 4:0x%x, 5:0x%x, 6:0x%x\n",
			   readl(DEVAPC_PD_INFRA_VIO_MASK(0)), readl(DEVAPC_PD_INFRA_VIO_MASK(1)),
			   readl(DEVAPC_PD_INFRA_VIO_MASK(2)), readl(DEVAPC_PD_INFRA_VIO_MASK(3)),
			   readl(DEVAPC_PD_INFRA_VIO_MASK(4)), readl(DEVAPC_PD_INFRA_VIO_MASK(5)),
			   readl(DEVAPC_PD_INFRA_VIO_MASK(6)));

	DEVAPC_MSG("[DEVAPC] PERI VIO_MASK(B) 0:0x%x, 1:0x%x\n",
			   readl(DEVAPC_PD_PERI_VIO_MASK(0)), readl(DEVAPC_PD_PERI_VIO_MASK(1)));

	for (i = 0; i < ARRAY_SIZE(devapc_infra_devices); i++) {
		clear_infra_vio_status(i);
		if (true == devapc_infra_devices[i].enable_vio_irq)
			unmask_infra_module_irq(i);
	}

	for (i = 0; i < ARRAY_SIZE(devapc_peri_devices); i++) {
		clear_peri_vio_status(i);
		if (true == devapc_peri_devices[i].enable_vio_irq)
			unmask_peri_module_irq(i);
	}

	DEVAPC_MSG("[DEVAPC] INFRA VIO_MASK(A) 0:0x%x, 1:0x%x, 2:0x%x, 3:0x%x, 4:0x%x, 5:0x%x, 6:0x%x\n",
			   readl(DEVAPC_PD_INFRA_VIO_MASK(0)), readl(DEVAPC_PD_INFRA_VIO_MASK(1)),
			   readl(DEVAPC_PD_INFRA_VIO_MASK(2)), readl(DEVAPC_PD_INFRA_VIO_MASK(3)),
			   readl(DEVAPC_PD_INFRA_VIO_MASK(4)), readl(DEVAPC_PD_INFRA_VIO_MASK(5)),
			   readl(DEVAPC_PD_INFRA_VIO_MASK(6)));

	DEVAPC_MSG("[DEVAPC] PERI VIO_MASK(A) 0:0x%x, 1:0x%x\n",
			   readl(DEVAPC_PD_PERI_VIO_MASK(0)), readl(DEVAPC_PD_PERI_VIO_MASK(1)));

#if defined(CONFIG_MTK_AEE_FEATURE) && defined(DEVAPC_ENABLE_AEE)
	devapc_vio_current_aee_trigger_times = 0;
#endif

}

#if defined(CONFIG_MTK_AEE_FEATURE) && defined(DEVAPC_ENABLE_AEE)
static void execute_aee(unsigned int i, unsigned int dbg1, unsigned int type)
{
	char aee_str[256];

	DEVAPC_VIO_MSG("[DEVAPC] Executing AEE Exception...\n");
	if (type == DEVAPC_INFRA_TYPE) {
		/* Mark the flag for showing AEE (AEE should be shown only once) */
		devapc_infra_vio_aee_shown[i] = 1;

		if (devapc_vio_current_aee_trigger_times <
			DEVAPC_VIO_MAX_TOTAL_MODULE_AEE_TRIGGER_TIMES) {

			devapc_vio_current_aee_trigger_times++;

			sprintf(aee_str, "[DEVAPC] Access Violation Slave: %s (infra index=%d)\n",
					devapc_infra_devices[i].device, i);

			aee_kernel_exception(aee_str,
						"%s\nAccess Violation Slave: %s\nVio Addr: 0x%x\n%s%s\n",
						"Device APC Violation",
						devapc_infra_devices[i].device,
						dbg1,
						"CRDISPATCH_KEY:Device APC Violation Issue/",
						devapc_infra_devices[i].device
			);
		}
	} else if (type == DEVAPC_PERI_TYPE) {
		/* Mark the flag for showing AEE (AEE should be shown only once) */
		devapc_peri_vio_aee_shown[i] = 1;

		if (devapc_vio_current_aee_trigger_times <
			DEVAPC_VIO_MAX_TOTAL_MODULE_AEE_TRIGGER_TIMES) {

			devapc_vio_current_aee_trigger_times++;

			sprintf(aee_str, "[DEVAPC] Access Violation Slave: %s (peri index=%d)\n",
					devapc_peri_devices[i].device, i);

			aee_kernel_exception(aee_str,
						"%s\nAccess Violation Slave: %s\nVio Addr: 0x%x\n%s%s\n",
						"Device APC Violation",
						devapc_peri_devices[i].device,
						dbg1,
						"CRDISPATCH_KEY:Device APC Violation Issue/",
						devapc_peri_devices[i].device
			);
		}
	} else {
		DEVAPC_VIO_MSG("[DEVAPC] (Error) devpac type %d is not registered!\n", type);
	}
}

static void evaluate_aee_exception(unsigned int i, unsigned int dbg1, unsigned int type)
{
	unsigned long long current_time;

	if (type == DEVAPC_INFRA_TYPE && devapc_infra_vio_aee_shown[i] == 0) {
		if (devapc_infra_vio_count[i] < DEVAPC_VIO_AEE_TRIGGER_TIMES) {
			devapc_infra_vio_count[i]++;

			if (devapc_infra_vio_count[i] == 1) {
				/* this slave violation is triggered for the first time */

				/* get current time from start-up in ns */
				devapc_infra_vio_first_trigger_time[i] = sched_clock();

				DEVAPC_VIO_MSG("[DEVAPC] devapc_vio_first_trigger_time: %llu\n",
					devapc_infra_vio_first_trigger_time[i] / 1000000); /* ms */
			}
		}

		if (devapc_infra_vio_count[i] >= DEVAPC_VIO_AEE_TRIGGER_TIMES) {
			current_time = sched_clock(); /* get current time from start-up in ns */

			DEVAPC_VIO_MSG("[DEVAPC] current_time: %llu\n",
				current_time / 1000000); /* ms */
			DEVAPC_VIO_MSG("[DEVAPC] devapc_vio_count[%d]: %d\n",
				i, devapc_infra_vio_count[i]);

			if (((current_time - devapc_infra_vio_first_trigger_time[i]) / 1000000) <=
				(unsigned long long)DEVAPC_VIO_AEE_TRIGGER_FREQUENCY) {  /* diff time by ms */
				execute_aee(i, dbg1, type);
			}
		}
	} else if (type == DEVAPC_PERI_TYPE && devapc_peri_vio_aee_shown[i] == 0) {
		if (devapc_peri_vio_count[i] < DEVAPC_VIO_AEE_TRIGGER_TIMES) {
			devapc_peri_vio_count[i]++;

			if (devapc_peri_vio_count[i] == 1) {
				/* this slave violation is triggered for the first time */

				/* get current time from start-up in ns */
				devapc_peri_vio_first_trigger_time[i] = sched_clock();

				DEVAPC_VIO_MSG("[DEVAPC] devapc_vio_first_trigger_time: %llu\n",
					devapc_peri_vio_first_trigger_time[i] / 1000000); /* ms */
			}
		}

		if (devapc_peri_vio_count[i] >= DEVAPC_VIO_AEE_TRIGGER_TIMES) {
			current_time = sched_clock(); /* get current time from start-up in ns */

			DEVAPC_VIO_MSG("[DEVAPC] current_time: %llu\n",
				current_time / 1000000); /* ms */
			DEVAPC_VIO_MSG("[DEVAPC] devapc_vio_count[%d]: %d\n",
				i, devapc_peri_vio_count[i]);

			if (((current_time - devapc_peri_vio_first_trigger_time[i]) / 1000000) <=
				(unsigned long long)DEVAPC_VIO_AEE_TRIGGER_FREQUENCY) {  /* diff time by ms */
				execute_aee(i, dbg1, type);
			}
		}
	}
}
#endif


static irqreturn_t devapc_violation_irq(int irq_number, void *dev_id)
{
	unsigned int dbg0 = 0, dbg1 = 0;
	unsigned int master_id;
	unsigned int domain_id;
	unsigned int vio_addr_high;
	unsigned int read_violation;
	unsigned int write_violation;
	unsigned int i;
	struct pt_regs *regs;

	if (irq_number == devapc_infra_irq) {

		dbg0 = readl(DEVAPC_PD_INFRA_VIO_DBG0);
		dbg1 = readl(DEVAPC_PD_INFRA_VIO_DBG1);
		master_id = (dbg0 & INFRA_VIO_DBG_MSTID) >> INFRA_VIO_DBG_MSTID_START_BIT;
		domain_id = (dbg0 & INFRA_VIO_DBG_DMNID) >> INFRA_VIO_DBG_DMNID_START_BIT;
		vio_addr_high = (dbg0 & INFRA_VIO_ADDR_HIGH) >> INFRA_VIO_ADDR_HIGH_START_BIT;
		write_violation = (dbg0 & INFRA_VIO_DBG_W) >> INFRA_VIO_DBG_W_START_BIT;
		read_violation = (dbg0 & INFRA_VIO_DBG_R) >> INFRA_VIO_DBG_R_START_BIT;

		/* violation information improvement (code should be less than 120 characters per line) */
		pr_notice
			("%s,%s%s%s:%s, PID:%i, Vio Addr:0x%x (High:0x%x), Bus ID:0x%x, Dom ID:0x%x, DBG0:0x%x\n",
			"[DEVAPC] Violation(Infra",
			((read_violation  == 1) ? "R" : ""),
			((write_violation == 1) ? "W" : ""),
			") - Process",
			current->comm, current->pid, dbg1, vio_addr_high, master_id, domain_id, dbg0);

		pr_notice
			("%s 0:0x%x, 1:0x%x, 2:0x%x, 3:0x%x, 4:0x%x, 5:0x%x, 6:0x%x\n",
			"[DEVAPC] INFRA VIO_STA",
			readl(DEVAPC_PD_INFRA_VIO_STA(0)), readl(DEVAPC_PD_INFRA_VIO_STA(1)),
			readl(DEVAPC_PD_INFRA_VIO_STA(2)), readl(DEVAPC_PD_INFRA_VIO_STA(3)),
			readl(DEVAPC_PD_INFRA_VIO_STA(4)), readl(DEVAPC_PD_INFRA_VIO_STA(5)),
			readl(DEVAPC_PD_INFRA_VIO_STA(6)));

		/* No need to check violation of EMI & EMI MPU slaves for Infra because they will not be unmasked */

		/* checking and showing violation normal slaves */
		for (i = 0; i < DEVAPC_INFRA_TOTAL_SLAVES; i++) {
			/* violation information improvement */

			if (devapc_infra_devices[i].enable_vio_irq == true && check_infra_vio_status(i) == 1) {
				clear_infra_vio_status(i);
				pr_notice("[DEVAPC] Access Violation Slave: %s (infra index=%d)\n",
							devapc_infra_devices[i].device, i);

#if defined(CONFIG_MTK_AEE_FEATURE) && defined(DEVAPC_ENABLE_AEE)
				/* Frequency-based Violation AEE Warning (Under the condition that the violation     */
				/* for the module is not shown, it will trigger the AEE if "x" violations in "y" ms) */
				evaluate_aee_exception(i, dbg1, DEVAPC_INFRA_TYPE);
#endif
			}

		}

		mt_reg_sync_writel(INFRA_VIO_DBG_CLR, DEVAPC_PD_INFRA_VIO_DBG0);

		dbg0 = readl(DEVAPC_PD_INFRA_VIO_DBG0);
		dbg1 = readl(DEVAPC_PD_INFRA_VIO_DBG1);

	} else if (irq_number == devapc_peri_irq) {

		dbg0 = readl(DEVAPC_PD_PERI_VIO_DBG0);
		dbg1 = readl(DEVAPC_PD_PERI_VIO_DBG1);
		master_id = (dbg0 & PERI_VIO_DBG_MSTID) >> PERI_VIO_DBG_MSTID_START_BIT;
		domain_id = (dbg0 & PERI_VIO_DBG_DMNID) >> PERI_VIO_DBG_DMNID_START_BIT;
		vio_addr_high = (dbg0 & PERI_VIO_ADDR_HIGH) >> PERI_VIO_ADDR_HIGH_START_BIT;
		write_violation = (dbg0 & PERI_VIO_DBG_W) >> PERI_VIO_DBG_W_START_BIT;
		read_violation = (dbg0 & PERI_VIO_DBG_R) >> PERI_VIO_DBG_R_START_BIT;

		/* violation information improvement (code should be less than 120 characters per line) */
		pr_notice
			("%s,%s%s%s:%s, PID:%i, Vio Addr:0x%x (High:0x%x), Bus ID:0x%x, Dom ID:0x%x, DBG0:0x%x\n",
			"[DEVAPC] Violation(Peri",
			((read_violation  == 1) ? "R" : ""),
			((write_violation == 1) ? "W" : ""),
			") - Process",
			current->comm, current->pid, dbg1, vio_addr_high, master_id, domain_id, dbg0);

		pr_notice("[DEVAPC] PERI VIO_STA 0:0x%x, 1:0x%x\n",
					readl(DEVAPC_PD_PERI_VIO_STA(0)), readl(DEVAPC_PD_PERI_VIO_STA(1)));

		/* No need to check violation of EMI & EMI MPU slaves for Peri because they will not be unmasked */

		/* checking and showing violation normal slaves */
		for (i = 0; i < DEVAPC_PERI_TOTAL_SLAVES; i++) {
			/* violation information improvement */

			if (devapc_peri_devices[i].enable_vio_irq == true && check_peri_vio_status(i) == 1) {
				clear_peri_vio_status(i);
				pr_notice("[DEVAPC] Access Violation Slave: %s (peri index=%d)\n",
							devapc_peri_devices[i].device, i);

#if defined(CONFIG_MTK_AEE_FEATURE) && defined(DEVAPC_ENABLE_AEE)
				/* Frequency-based Violation AEE Warning (Under the condition that the violation     */
				/* for the module is not shown, it will trigger the AEE if "x" violations in "y" ms) */
				evaluate_aee_exception(i, dbg1, DEVAPC_PERI_TYPE);
#endif
			}

		}

		mt_reg_sync_writel(PERI_VIO_DBG_CLR, DEVAPC_PD_PERI_VIO_DBG0);

		dbg0 = readl(DEVAPC_PD_PERI_VIO_DBG0);
		dbg1 = readl(DEVAPC_PD_PERI_VIO_DBG1);

	} else {
		pr_notice("[DEVAPC] (ERROR) irq_number %d is not registered!\n", irq_number);
	}

	if ((DEVAPC_ENABLE_ONE_CORE_VIOLATION_DEBUG) || (enable_dynamic_one_core_violation_debug)) {
		pr_notice("[DEVAPC] ====== Start dumping Device APC violation tracing ======\n");

		pr_notice("[DEVAPC] **************** [All IRQ Registers] ****************\n");
		regs = get_irq_regs();
		show_regs(regs);

		pr_notice("[DEVAPC] **************** [All Current Task Stack] ****************\n");
		show_stack(current, NULL);

		pr_notice("[DEVAPC] ====== End of dumping Device APC violation tracing ======\n");
	}

	if ((dbg0 != 0) || (dbg1 != 0)) {
		pr_notice("[DEVAPC] Multi-violation!\n");
		pr_notice("[DEVAPC] DBG0 = %x, DBG1 = %x\n", dbg0, dbg1);
	}

	return IRQ_HANDLED;
}
#endif

static int devapc_probe(struct platform_device *pdev)
{
	struct device_node *node = pdev->dev.of_node;
#if DEVAPC_TURN_ON
	int ret;
#endif

	DEVAPC_MSG("[DEVAPC] module probe.\n");

	if (devapc_pd_infra_base == NULL) {
		if (node) {
			devapc_pd_infra_base = of_iomap(node, DAPC_DEVICE_TREE_NODE_PD_INFRA_INDEX);
			devapc_infra_irq = irq_of_parse_and_map(node, DAPC_DEVICE_TREE_NODE_PD_INFRA_INDEX);
			DEVAPC_MSG("[DEVAPC] PD_INFRA_ADDRESS: %p, IRQ: %d\n", devapc_pd_infra_base, devapc_infra_irq);
		} else {
			pr_err("[DEVAPC] can't find DAPC_INFRA_PD compatible node\n");
			return -1;
		}
	}

	if (devapc_pd_peri_base == NULL) {
		if (node) {
			devapc_pd_peri_base = of_iomap(node, DAPC_DEVICE_TREE_NODE_PD_PERI_INDEX);
			devapc_peri_irq = irq_of_parse_and_map(node, DAPC_DEVICE_TREE_NODE_PD_PERI_INDEX);
			DEVAPC_MSG("[DEVAPC] PD_PERI_ADDRESS: %p, IRQ: %d\n", devapc_pd_peri_base, devapc_peri_irq);
		} else {
			pr_err("[DEVAPC] can't find DAPC_PERI_PD compatible node\n");
			return -1;
		}
	}

#if DEVAPC_TURN_ON
	ret = request_irq(devapc_infra_irq, (irq_handler_t) devapc_violation_irq,
			  IRQF_TRIGGER_LOW | IRQF_SHARED, "devapc", &g_devapc_ctrl);
	if (ret) {
		pr_err("[DEVAPC] Failed to request infra irq! (%d)\n", ret);
		return ret;
	}

	ret = request_irq(devapc_peri_irq, (irq_handler_t) devapc_violation_irq,
			  IRQF_TRIGGER_LOW | IRQF_SHARED, "devapc", &g_devapc_ctrl);
	if (ret) {
		pr_err("[DEVAPC] Failed to request peri irq! (%d)\n", ret);
		return ret;
	}
#endif

/* CCF */
#if DEVAPC_USE_CCF
	dapc_infra_clk = devm_clk_get(&pdev->dev, "devapc-infra-clock");
	if (IS_ERR(dapc_infra_clk)) {
		pr_err("[DEVAPC] (Infra) Cannot get devapc clock from common clock framework.\n");
		return PTR_ERR(dapc_infra_clk);
	}
	clk_prepare_enable(dapc_infra_clk);

	dapc_peri_clk = devm_clk_get(&pdev->dev, "devapc-peri-clock");
	if (IS_ERR(dapc_peri_clk)) {
		pr_err("[DEVAPC] (Peri) Cannot get devapc clock from common clock framework.\n");
		return PTR_ERR(dapc_peri_clk);
	}
	clk_prepare_enable(dapc_peri_clk);
#endif

#ifdef CONFIG_MTK_HIBERNATION
	register_swsusp_restore_noirq_func(ID_M_DEVAPC, devapc_pm_restore_noirq, NULL);
#endif

#if DEVAPC_TURN_ON
	start_devapc();
#endif

	return 0;
}

static int devapc_remove(struct platform_device *dev)
{
	return 0;
}

static int devapc_suspend(struct platform_device *dev, pm_message_t state)
{
	return 0;
}

static int devapc_resume(struct platform_device *dev)
{
	DEVAPC_MSG("[DEVAPC] module resume.\n");

	return 0;
}

static int check_debug_input_type(const char *str)
{
	if (sysfs_streq(str, "1"))
		return DAPC_INPUT_TYPE_DEBUG_ON;
	else if (sysfs_streq(str, "0"))
		return DAPC_INPUT_TYPE_DEBUG_OFF;
	else
		return 0;
}

static ssize_t devapc_dbg_write(struct file *file, const char __user *buffer, size_t count, loff_t *data)
{
	char desc[32];
	int len = 0;
	int input_type;

	len = (count < (sizeof(desc) - 1)) ? count : (sizeof(desc) - 1);
	if (copy_from_user(desc, buffer, len))
		return -EFAULT;

	desc[len] = '\0';

	input_type = check_debug_input_type(desc);
	if (!input_type)
		return -EFAULT;

	if (input_type == DAPC_INPUT_TYPE_DEBUG_ON) {
		enable_dynamic_one_core_violation_debug = 1;
		DEVAPC_VIO_MSG("[DEVAPC] One-Core Debugging: Enabled\n");
	} else if (input_type == DAPC_INPUT_TYPE_DEBUG_OFF) {
		enable_dynamic_one_core_violation_debug = 0;
		DEVAPC_VIO_MSG("[DEVAPC] One-Core Debugging: Disabled\n");
	}

	return count;
}

static int devapc_dbg_open(struct inode *inode, struct file *file)
{
	return 0;
}

static const struct file_operations devapc_dbg_fops = {
	.owner = THIS_MODULE,
	.open  = devapc_dbg_open,
	.write = devapc_dbg_write,
	.read = NULL,
};

static const struct of_device_id plat_devapc_dt_match[] = {
	{ .compatible = "mediatek,devapc" },
	{},
};

static struct platform_driver devapc_driver = {
	.probe = devapc_probe,
	.remove = devapc_remove,
	.suspend = devapc_suspend,
	.resume = devapc_resume,
	.driver = {
			.name = "devapc",
			.owner = THIS_MODULE,
			.of_match_table	= plat_devapc_dt_match,
	},
};

/*
 * devapc_init: module init function.
 */
static int __init devapc_init(void)
{
	int ret;

	DEVAPC_MSG("[DEVAPC] kernel module init.\n");

	ret = platform_driver_register(&devapc_driver);
	if (ret) {
		pr_err("[DEVAPC] Unable to register driver (%d)\n", ret);
		return ret;
	}

	g_devapc_ctrl = cdev_alloc();
	if (!g_devapc_ctrl) {
		pr_err("[DEVAPC] Failed to add devapc device! (%d)\n", ret);
		platform_driver_unregister(&devapc_driver);
		return ret;
	}
	g_devapc_ctrl->owner = THIS_MODULE;

	proc_create("devapc_dbg", (S_IRUSR | S_IWUSR | S_IRGRP | S_IWGRP | S_IROTH), NULL,
		&devapc_dbg_fops);

	return 0;
}

/*
 * devapc_exit: module exit function.
 */
static void __exit devapc_exit(void)
{
	DEVAPC_MSG("[DEVAPC] DEVAPC module exit\n");
#ifdef CONFIG_MTK_HIBERNATION
	unregister_swsusp_restore_noirq_func(ID_M_DEVAPC);
#endif
}

/* Device APC no longer shares IRQ with EMI and can be changed to use the earlier "arch_initcall" */
arch_initcall(devapc_init);
module_exit(devapc_exit);
MODULE_LICENSE("GPL");
