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

#include "mtk_cpufreq_config.h"

#define NR_FREQ		16
#define ARRAY_COL_SIZE	4

static unsigned int fyTbl[NR_FREQ * NR_MT_CPU_DVFS][ARRAY_COL_SIZE] = {
	/* Freq, Vproc, post_div, clk_div */
	{ 1690,	114,	2,	1 },	/* LL */
	{ 1677,	109,	2,	1 },
	{ 1651,	103,	2,	1 },
	{ 1625,	98,	2,	1 },
	{ 1547,	93,	2,	1 },
	{ 1495,	89,	2,	1 },
	{ 1417,	84,	2,	1 },
	{ 1339,	79,	2,	1 },
	{ 1248,	74,	2,	1 },
	{ 1144,	69,	2,	1 },
	{ 1014,	63,	2,	1 },
	{ 884,	57,	4,	1 },
	{ 780,	52,	4,	1 },
	{ 663,	47,	4,	1 },
	{ 520,	40,	4,	1 },
	{ 338,	31,	4,	2 },

	{ 2340,	114,	1,	1 },	/* L */
	{ 2288,	109,	1,	1 },
	{ 2236,	103,	1,	1 },
	{ 2184,	98,	1,	1 },
	{ 2119,	93,	1,	1 },
	{ 2054,	89,	1,	1 },
	{ 1989,	84,	2,	1 },
	{ 1924,	79,	2,	1 },
	{ 1781,	74,	2,	1 },
	{ 1638,	69,	2,	1 },
	{ 1469,	63,	2,	1 },
	{ 1287,	57,	2,	1 },
	{ 1131,	52,	2,	1 },
	{ 962,	47,	4,	1 },
	{ 767,	40,	4,	1 },
	{ 520,	31,	4,	1 },

	{ 1040,	114,	2,	1 },	/* CCI */
	{ 1027,	109,	2,	1 },
	{ 1001,	103,	2,	1 },
	{ 988,	98,	4,	1 },
	{ 936,	93,	4,	1 },
	{ 897,	89,	4,	1 },
	{ 845,	84,	4,	1 },
	{ 793,	79,	4,	1 },
	{ 741,	74,	4,	1 },
	{ 689,	69,	4,	1 },
	{ 624,	63,	4,	1 },
	{ 546,	57,	4,	1 },
	{ 481,	52,	4,	2 },
	{ 416,	47,	4,	2 },
	{ 325,	40,	4,	2 },
	{ 195,	31,	4,	4 },
};

unsigned int *xrecordTbl[NUM_CPU_LEVEL] = {	/* v1.3.1 */
	[CPU_LEVEL_0] = &fyTbl[0][0],
};
