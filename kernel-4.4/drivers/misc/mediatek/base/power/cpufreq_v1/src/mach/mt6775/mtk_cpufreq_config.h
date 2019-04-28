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

#ifndef __MTK_CPUFREQ_CONFIG_H__
#define __MTK_CPUFREQ_CONFIG_H__

#define MT_CPU_DVFS_LL 0xFF /*not use*/

enum mt_cpu_dvfs_id {
	MT_CPU_DVFS_L,
	MT_CPU_DVFS_B,
	MT_CPU_DVFS_CCI,

	NR_MT_CPU_DVFS,
};

enum cpu_level {
	CPU_LEVEL_0,

	NUM_CPU_LEVEL,
};

/* PMIC Config */
enum mt_cpu_dvfs_buck_id {
	CPU_DVFS_VPROC_B, /* CPU-B : vproc11 */
	CPU_DVFS_VSRAM_B, /* CPU-B SRAM : vsram_gpu */
	CPU_DVFS_VPROC_L, /* CPU-L : rt5738 */
	CPU_DVFS_VSRAM_L, /* CPU-L SRAM : vsram_proc */

	NR_MT_BUCK,
};

enum mt_cpu_dvfs_pmic_type {
	BUCK_MT6355_VPROC11,
	LDO_MT6355_VSRAM_GPU,
	BUCK_RT5738_VPROC,
	LDO_MT6355_VSRAM_PROC,

	NR_MT_PMIC,
};

/* PLL Config */
enum mt_cpu_dvfs_pll_id {
	PLL_L_CLUSTER,
	PLL_B_CLUSTER,
	PLL_CCI_CLUSTER,

	NR_MT_PLL,
};

enum top_ckmuxsel {
	TOP_CKMUXSEL_CLKSQ = 0,
	TOP_CKMUXSEL_ARMPLL = 1,
	TOP_CKMUXSEL_MAINPLL = 2,
	TOP_CKMUXSEL_UNIVPLL = 3,

	NR_TOP_CKMUXSEL,
};

#endif	/* __MTK_CPUFREQ_CONFIG_H__ */
