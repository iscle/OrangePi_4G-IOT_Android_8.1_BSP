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

#include "mtk_cpufreq_struct.h"
#include "mtk_cpufreq_config.h"

/* for DVFS OPP table L/FY */
#define CPU_DVFS_FREQ0_L_FY        2001000    /* KHz */
#define CPU_DVFS_FREQ1_L_FY        1950000    /* KHz */
#define CPU_DVFS_FREQ2_L_FY        1885000    /* KHz */
#define CPU_DVFS_FREQ3_L_FY        1846000    /* KHz */
#define CPU_DVFS_FREQ4_L_FY        1781000    /* KHz */
#define CPU_DVFS_FREQ5_L_FY        1716000    /* KHz */
#define CPU_DVFS_FREQ6_L_FY        1651000    /* KHz */
#define CPU_DVFS_FREQ7_L_FY        1599000    /* KHz */
#define CPU_DVFS_FREQ8_L_FY        1508000    /* KHz */
#define CPU_DVFS_FREQ9_L_FY        1417000    /* KHz */
#define CPU_DVFS_FREQ10_L_FY       1326000    /* KHz */
#define CPU_DVFS_FREQ11_L_FY       1248000    /* KHz */
#define CPU_DVFS_FREQ12_L_FY       1131000    /* KHz */
#define CPU_DVFS_FREQ13_L_FY       1014000    /* KHz */
#define CPU_DVFS_FREQ14_L_FY        910000    /* KHz */
#define CPU_DVFS_FREQ15_L_FY        793000    /* KHz */

/* for DVFS OPP table CCI/FY */
#define CPU_DVFS_FREQ0_CCI_FY        1196000    /* KHz */
#define CPU_DVFS_FREQ1_CCI_FY        1170000    /* KHz */
#define CPU_DVFS_FREQ2_CCI_FY        1118000    /* KHz */
#define CPU_DVFS_FREQ3_CCI_FY        1092000    /* KHz */
#define CPU_DVFS_FREQ4_CCI_FY        1027000    /* KHz */
#define CPU_DVFS_FREQ5_CCI_FY         975000    /* KHz */
#define CPU_DVFS_FREQ6_CCI_FY         910000    /* KHz */
#define CPU_DVFS_FREQ7_CCI_FY         845000    /* KHz */
#define CPU_DVFS_FREQ8_CCI_FY         767000    /* KHz */
#define CPU_DVFS_FREQ9_CCI_FY         689000    /* KHz */
#define CPU_DVFS_FREQ10_CCI_FY        624000    /* KHz */
#define CPU_DVFS_FREQ11_CCI_FY        546000    /* KHz */
#define CPU_DVFS_FREQ12_CCI_FY        481000    /* KHz */
#define CPU_DVFS_FREQ13_CCI_FY        403000    /* KHz */
#define CPU_DVFS_FREQ14_CCI_FY        338000    /* KHz */
#define CPU_DVFS_FREQ15_CCI_FY        273000    /* KHz */

/* for DVFS OPP table B/FY */
#define CPU_DVFS_FREQ0_B_FY        2501000    /* KHz */
#define CPU_DVFS_FREQ1_B_FY        2431000    /* KHz */
#define CPU_DVFS_FREQ2_B_FY        2366000    /* KHz */
#define CPU_DVFS_FREQ3_B_FY        2288000    /* KHz */
#define CPU_DVFS_FREQ4_B_FY        2223000    /* KHz */
#define CPU_DVFS_FREQ5_B_FY        2145000    /* KHz */
#define CPU_DVFS_FREQ6_B_FY        2067000    /* KHz */
#define CPU_DVFS_FREQ7_B_FY        1989000    /* KHz */
#define CPU_DVFS_FREQ8_B_FY        1885000    /* KHz */
#define CPU_DVFS_FREQ9_B_FY        1768000    /* KHz */
#define CPU_DVFS_FREQ10_B_FY       1651000    /* KHz */
#define CPU_DVFS_FREQ11_B_FY       1547000    /* KHz */
#define CPU_DVFS_FREQ12_B_FY       1404000    /* KHz */
#define CPU_DVFS_FREQ13_B_FY       1274000    /* KHz */
#define CPU_DVFS_FREQ14_B_FY       1131000    /* KHz */
#define CPU_DVFS_FREQ15_B_FY        988000    /* KHz */

/* for DVFS OPP table L */
#define CPU_DVFS_VOLT0_VPROC_L_FY    95000    /* 10uV */
#define CPU_DVFS_VOLT1_VPROC_L_FY    93500    /* 10uV */
#define CPU_DVFS_VOLT2_VPROC_L_FY    91500    /* 10uV */
#define CPU_DVFS_VOLT3_VPROC_L_FY    90000    /* 10uV */
#define CPU_DVFS_VOLT4_VPROC_L_FY    87500    /* 10uV */
#define CPU_DVFS_VOLT5_VPROC_L_FY    85000    /* 10uV */
#define CPU_DVFS_VOLT6_VPROC_L_FY    82500    /* 10uV */
#define CPU_DVFS_VOLT7_VPROC_L_FY    80000    /* 10uV */
#define CPU_DVFS_VOLT8_VPROC_L_FY    77500    /* 10uV */
#define CPU_DVFS_VOLT9_VPROC_L_FY    75000    /* 10uV */
#define CPU_DVFS_VOLT10_VPROC_L_FY   72500    /* 10uV */
#define CPU_DVFS_VOLT11_VPROC_L_FY   70000    /* 10uV */
#define CPU_DVFS_VOLT12_VPROC_L_FY   67500    /* 10uV */
#define CPU_DVFS_VOLT13_VPROC_L_FY   65000    /* 10uV */
#define CPU_DVFS_VOLT14_VPROC_L_FY   62500    /* 10uV */
#define CPU_DVFS_VOLT15_VPROC_L_FY   60000    /* 10uV */

/* for DVFS OPP table CCI */
#define CPU_DVFS_VOLT0_VPROC_CCI_FY    95000    /* 10uV */
#define CPU_DVFS_VOLT1_VPROC_CCI_FY    93500    /* 10uV */
#define CPU_DVFS_VOLT2_VPROC_CCI_FY    91500    /* 10uV */
#define CPU_DVFS_VOLT3_VPROC_CCI_FY    90000    /* 10uV */
#define CPU_DVFS_VOLT4_VPROC_CCI_FY    87500    /* 10uV */
#define CPU_DVFS_VOLT5_VPROC_CCI_FY    85000    /* 10uV */
#define CPU_DVFS_VOLT6_VPROC_CCI_FY    82500    /* 10uV */
#define CPU_DVFS_VOLT7_VPROC_CCI_FY    80000    /* 10uV */
#define CPU_DVFS_VOLT8_VPROC_CCI_FY    77500    /* 10uV */
#define CPU_DVFS_VOLT9_VPROC_CCI_FY    75000    /* 10uV */
#define CPU_DVFS_VOLT10_VPROC_CCI_FY   72500    /* 10uV */
#define CPU_DVFS_VOLT11_VPROC_CCI_FY   70000    /* 10uV */
#define CPU_DVFS_VOLT12_VPROC_CCI_FY   67500    /* 10uV */
#define CPU_DVFS_VOLT13_VPROC_CCI_FY   65000    /* 10uV */
#define CPU_DVFS_VOLT14_VPROC_CCI_FY   62500    /* 10uV */
#define CPU_DVFS_VOLT15_VPROC_CCI_FY   60000    /* 10uV */

/* for DVFS OPP table B */
#define CPU_DVFS_VOLT0_VPROC_B_FY    95000    /* 10uV */
#define CPU_DVFS_VOLT1_VPROC_B_FY    93750    /* 10uV */
#define CPU_DVFS_VOLT2_VPROC_B_FY    91875    /* 10uV */
#define CPU_DVFS_VOLT3_VPROC_B_FY    90000    /* 10uV */
#define CPU_DVFS_VOLT4_VPROC_B_FY    87500    /* 10uV */
#define CPU_DVFS_VOLT5_VPROC_B_FY    85000    /* 10uV */
#define CPU_DVFS_VOLT6_VPROC_B_FY    82500    /* 10uV */
#define CPU_DVFS_VOLT7_VPROC_B_FY    80000    /* 10uV */
#define CPU_DVFS_VOLT8_VPROC_B_FY    77500    /* 10uV */
#define CPU_DVFS_VOLT9_VPROC_B_FY    75000    /* 10uV */
#define CPU_DVFS_VOLT10_VPROC_B_FY   72500    /* 10uV */
#define CPU_DVFS_VOLT11_VPROC_B_FY   70000    /* 10uV */
#define CPU_DVFS_VOLT12_VPROC_B_FY   67500    /* 10uV */
#define CPU_DVFS_VOLT13_VPROC_B_FY   65000    /* 10uV */
#define CPU_DVFS_VOLT14_VPROC_B_FY   62500    /* 10uV */
#define CPU_DVFS_VOLT15_VPROC_B_FY   60000    /* 10uV */

/* DVFS OPP table */
#define OPP_TBL(cluster, seg, lv, vol)	\
static struct mt_cpu_freq_info opp_tbl_##cluster##_e##lv##_0[] = {	\
	OP(CPU_DVFS_FREQ0_##cluster##_##seg, CPU_DVFS_VOLT0_VPROC_##vol##_##seg),	\
	OP(CPU_DVFS_FREQ1_##cluster##_##seg, CPU_DVFS_VOLT1_VPROC_##vol##_##seg),	\
	OP(CPU_DVFS_FREQ2_##cluster##_##seg, CPU_DVFS_VOLT2_VPROC_##vol##_##seg),	\
	OP(CPU_DVFS_FREQ3_##cluster##_##seg, CPU_DVFS_VOLT3_VPROC_##vol##_##seg),	\
	OP(CPU_DVFS_FREQ4_##cluster##_##seg, CPU_DVFS_VOLT4_VPROC_##vol##_##seg),	\
	OP(CPU_DVFS_FREQ5_##cluster##_##seg, CPU_DVFS_VOLT5_VPROC_##vol##_##seg),	\
	OP(CPU_DVFS_FREQ6_##cluster##_##seg, CPU_DVFS_VOLT6_VPROC_##vol##_##seg),	\
	OP(CPU_DVFS_FREQ7_##cluster##_##seg, CPU_DVFS_VOLT7_VPROC_##vol##_##seg),	\
	OP(CPU_DVFS_FREQ8_##cluster##_##seg, CPU_DVFS_VOLT8_VPROC_##vol##_##seg),	\
	OP(CPU_DVFS_FREQ9_##cluster##_##seg, CPU_DVFS_VOLT9_VPROC_##vol##_##seg),	\
	OP(CPU_DVFS_FREQ10_##cluster##_##seg, CPU_DVFS_VOLT10_VPROC_##vol##_##seg),	\
	OP(CPU_DVFS_FREQ11_##cluster##_##seg, CPU_DVFS_VOLT11_VPROC_##vol##_##seg),	\
	OP(CPU_DVFS_FREQ12_##cluster##_##seg, CPU_DVFS_VOLT12_VPROC_##vol##_##seg),	\
	OP(CPU_DVFS_FREQ13_##cluster##_##seg, CPU_DVFS_VOLT13_VPROC_##vol##_##seg),	\
	OP(CPU_DVFS_FREQ14_##cluster##_##seg, CPU_DVFS_VOLT14_VPROC_##vol##_##seg),	\
	OP(CPU_DVFS_FREQ15_##cluster##_##seg, CPU_DVFS_VOLT15_VPROC_##vol##_##seg),	\
}

OPP_TBL(L, FY, 0, L);
OPP_TBL(B, FY, 0, B);
OPP_TBL(CCI, FY, 0, CCI);

struct opp_tbl_info opp_tbls[NR_MT_CPU_DVFS][NUM_CPU_LEVEL] = {		/* v?? */
	/* L */
	{
		[CPU_LEVEL_0] = { opp_tbl_L_e0_0, ARRAY_SIZE(opp_tbl_L_e0_0) },
	},
	/* B */
	{
		[CPU_LEVEL_0] = { opp_tbl_B_e0_0, ARRAY_SIZE(opp_tbl_B_e0_0) },
	},
	/* CCI */
	{
		[CPU_LEVEL_0] = { opp_tbl_CCI_e0_0, ARRAY_SIZE(opp_tbl_CCI_e0_0) },
	},
};

/* 16 steps OPP table */
static struct mt_cpu_freq_method opp_tbl_method_L_FY[] = {
	/* POS,	CLK */
	FP(1,	1),
	FP(1,	1),
	FP(1,	1),
	FP(1,	1),
	FP(1,	1),
	FP(1,	1),
	FP(1,	1),
	FP(1,	1),
	FP(1,	1),
	FP(2,	1),
	FP(2,	1),
	FP(2,	1),
	FP(2,	1),
	FP(2,	1),
	FP(2,	1),
	FP(2,	1),
};

static struct mt_cpu_freq_method opp_tbl_method_CCI_FY[] = {
	/* POS,	CLK */
	FP(2,	1),
	FP(2,	1),
	FP(2,	1),
	FP(2,	1),
	FP(2,	1),
	FP(2,	1),
	FP(2,	1),
	FP(2,	1),
	FP(2,	1),
	FP(4,	1),
	FP(4,	1),
	FP(4,	1),
	FP(4,	1),
	FP(4,	1),
	FP(4,	2),
	FP(4,	2),
};

static struct mt_cpu_freq_method opp_tbl_method_B_FY[] = {
	/* POS,	CLK */
	FP(1,	1),
	FP(1,	1),
	FP(1,	1),
	FP(1,	1),
	FP(1,	1),
	FP(1,	1),
	FP(1,	1),
	FP(1,	1),
	FP(1,	1),
	FP(1,	1),
	FP(1,	1),
	FP(1,	1),
	FP(2,	1),
	FP(2,	1),
	FP(2,	1),
	FP(2,	1),
};

struct opp_tbl_m_info opp_tbls_m[NR_MT_CPU_DVFS][NUM_CPU_LEVEL] = {	/* v?? */
	/* L */
	{
		[CPU_LEVEL_0] = { opp_tbl_method_L_FY },
	},
	/* B */
	{
		[CPU_LEVEL_0] = { opp_tbl_method_B_FY },
	},
	/* CCI */
	{
		[CPU_LEVEL_0] = { opp_tbl_method_CCI_FY },
	},
};
