
/*
 * Copyright (C) 2016 MediaTek Inc.
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
#ifndef _MTK_EEM_CONFIG_H_
#define _MTK_EEM_CONFIG_H_

/* CONFIG (SW related) */
#define CONFIG_EEM_SHOWLOG		(0)
#define EN_ISR_LOG			(1)
#define DVT				(0)
#define EEM_BANK_BIG			(0)
#define EEM_BANK_SOC			(0) /* use voltage bin, so disable it */
#define EARLY_PORTING			(0) /* for detecting real vboot in eem_init01 */
#define DUMP_DATA_TO_DE			(1)
#define EEM_ENABLE			(1)
#define UPDATE_TO_UPOWER		(1)
#define EEM_LOCKTIME_LIMIT		(3000)

/* These 4 configs are reserving for sspm EEM */
/* #define EEM_CUR_VOLT_PROC_SHOW */
#define EEM_OFFSET
/* #define EEM_LOG_EN */
/* #define EEM_VCORE_IN_SSPM */

/* for early porting */
/*
 * #ifdef CONFIG_FPGA_EARLY_PORTING
 * #define EARLY_PORTING_GPU
 * #else
 * #define EARLY_PORTING_GPU
 * #endif
*/

/* #define EARLY_PORTING_CPU */
/* #define EARLY_PORTING_PPM */
/* #define EARLY_PORTING_PMIC */
/* #define EARLY_PORTING_VCORE */
/* #define EARLY_PORTING_THERMAL */

/* only used for ap ptp */
#ifdef CONFIG_MTK_TINYSYS_SSPM_SUPPORT
	#define EEM_ENABLE_TINYSYS_SSPM (0)
#else
	#define EEM_ENABLE_TINYSYS_SSPM (0)
#endif
#define SET_PMIC_VOLT			(1)
#define SET_PMIC_VOLT_TO_DVFS		(1)
#define LOG_INTERVAL			(2LL * NSEC_PER_SEC)
#define ITurbo				(0)

/* fake devinfo for early verification */
/* OD0 */
#define DEVINFO_0			 0xFF00
#define DEVINFO_DVT_0			 0xFF00
#define DEVINFO_IDX_0			 50
/* LL */
#define DEVINFO_1			0x11BA731A
#define DEVINFO_2			0x004400C7
#define DEVINFO_DVT_1			0x10BD3C1B
#define DEVINFO_DVT_2			0x0055C000
#define DEVINFO_IDX_1			51
#define DEVINFO_IDX_2			52
/* L */
#define DEVINFO_3			0x11BB7615
#define DEVINFO_4			0x004400C7
#define DEVINFO_DVT_3			0x10BD3C1B
#define DEVINFO_DVT_4			0x0055C000
#define DEVINFO_IDX_3			53
#define DEVINFO_IDX_4			54
/* CCI */
#define DEVINFO_5			0x11BB711D
#define DEVINFO_6			0x004400C7
#define DEVINFO_DVT_5			0x10BD3C1B
#define DEVINFO_DVT_6			0x0055C000
#define DEVINFO_IDX_5			55
#define DEVINFO_IDX_6			56
/* GPU */
#define DEVINFO_7			0x11BA4820
#define DEVINFO_8			0x003200C7
#define DEVINFO_DVT_7			0x10BD3C1B
#define DEVINFO_DVT_8			0x0055C000
#define DEVINFO_IDX_7			57
#define DEVINFO_IDX_8			58
/* SOC */
#define DEVINFO_9			0x0BCC7A18
#define DEVINFO_10			0x004100C7
#define DEVINFO_DVT_9			0x10BD3C1B
#define DEVINFO_DVT_10                  0x0055C000
#define DEVINFO_IDX_9			59 /* 131 */
#define DEVINFO_IDX_10			60 /* 131 */

/* voltage adjust */
#define DEVINFO_IDX_VOLT_ADJUST		3
/*****************************************
* eem sw setting
******************************************
*/
#define NR_HW_RES			(11) /* reserve for eem log */
#define NR_HW_RES_FOR_BANK		(8) /* real eem banks for efuse */
#define VCORE_NR_FREQ			(3)
#define VCORE_NR_FREQ_EFUSE		(4)
#define HW_RES_IDX_TURBO		(11) /* for providing turbo value to cpu dvfs */
#if EEM_BANK_SOC
#define EEM_INIT01_FLAG			(0x1F) /* should be 0x5F if vcore eem enable */
#else
#define EEM_INIT01_FLAG			(0x0F) /* should be 0x5F if vcore eem enable */
#endif

#if DVT
	#define NR_FREQ			8
	#define NR_FREQ_GPU		8
	#define NR_FREQ_CPU		8
#else
/* if max freq = 16 , NR_FREQ = 16, NR_FREQ_CPU = 16
 * if max freq = 4, NR_FREQ = 8, NR_FREQ_CPU = 4
*/
	#define NR_FREQ			16
	#define NR_FREQ_GPU		16
	#define NR_FREQ_CPU		16
#endif

/*
 * 100 us, This is the EEM Detector sampling time as represented in
 * cycles of bclk_ck during INIT. 52 MHz
 */
#define DETWINDOW_VAL			0xA28

/*
 * mili Volt to config value. voltage = 400mV + val * 6.25mV
 * val = (voltage - 400) / 6.25
 * @mV:	mili volt
 */

/* 1mV=>10uV */
/* EEM */
#define EEM_V_BASE			(40625)
#define EEM_STEP			(625)

/* SRAM (for vcore) */
#define SRAM_BASE			(40625)
#define SRAM_STEP			(625)
#define EEM_2_RMIC(value)	(((((value) * EEM_STEP) + EEM_V_BASE - SRAM_BASE + SRAM_STEP - 1) / SRAM_STEP) + 3)
#define PMIC_2_RMIC(det, value)	\
	(((((value) * det->pmic_step) + det->pmic_base - SRAM_BASE + SRAM_STEP - 1) / SRAM_STEP) + 3)
#define VOLT_2_RMIC(volt)	(((((volt) - SRAM_BASE) + SRAM_STEP - 1) / SRAM_STEP) + 3)

/* for voltage bin vcore dvfs */
#define VCORE_PMIC_BASE			(40625)
#define VCORE_PMIC_STEP			(625)
#if 0
#define VOLT_2_VCORE_PMIC(volt)   ((((volt) - VCORE_PMIC_BASE) + VCORE_PMIC_STEP - 1) / VCORE_PMIC_STEP)
#define VCORE_PMIC_2_VOLT(pmic) (((pmic) * VCORE_PMIC_STEP) + VCORE_PMIC_BASE)
#endif

/* CPU */
#define CPU_PMIC_BASE			(40625)
#define CPU_PMIC_STEP			(625)
#define MAX_ITURBO_OFFSET		(2000)

/* GPU */
#define GPU_PMIC_BASE			(40625)
#define GPU_PMIC_STEP			(625)

/* common part: for big, cci, LL, L, GPU, SOC*/
#define VBOOT_VAL			(0x3F) /* volt domain: 0.8v */
#define VMAX_VAL			(0x73) /* volt domain: 1.12v */
#define VMIN_VAL			(0x1F) /* volt domain: 0.6v */
#define VCO_VAL				(0x1F)
#define DVTFIXED_VAL			(0x9)

#define DTHI_VAL			(0x01)	/* positive */
#define DTLO_VAL			(0xfe)	/* negative (2's compliment) */
#define DETMAX_VAL			(0xffff)/* This timeout value is in cycles of bclk_ck. */
#define AGECONFIG_VAL			(0x555555)
#define AGEM_VAL			(0x0)
#define DCCONFIG_VAL			(0x555555)

/* different for GPU */
#define VBOOT_VAL_GPU			(0x3F) /* eem domain: 0x40, volt domain: 0.8v */
#define VMAX_VAL_GPU			(0x5F) /* eem domain: 0x4F, volt domain: 1.0v */
#define DVTFIXED_VAL_GPU		(0x3)

/* different for SOC */
#define VMAX_VAL_SOC			(0x37) /* eem domain: 0x37, volt domain: 0.75v */

/* use in base_ops_mon_mode */
#define MTS_VAL				(0x1fb)
#define BTS_VAL				(0x6d1)

#define CORESEL_VAL			(0x8fff0000)
#define CORESEL_INIT2_VAL		(0x0fff0000)
#define INVERT_TEMP_VAL			(25000)
#define OVER_INV_TEM_VAL		(27000)

/* for EEMCTL0's setting */
/* #define EEM_CTL0_BIG			(0x00000001) */
#define EEM_CTL0_2L			(0x00010001)
#define EEM_CTL0_L			(0x00000001)
#define EEM_CTL0_CCI			(0x00100003)
#define EEM_CTL0_GPU			(0x00060001)
#define EEM_CTL0_SOC			(0x00050001)

/* low temp offset E1 is 0 */
#define LOW_TEMP_OFF_DEFAULT		(0)

#endif
