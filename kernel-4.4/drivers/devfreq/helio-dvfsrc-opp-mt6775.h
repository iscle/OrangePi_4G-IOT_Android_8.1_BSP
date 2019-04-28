/*
 * Copyright (C) 2017 MediaTek Inc.
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

#ifndef __HELIO_DVFSRC_OPP_MT6775_H
#define __HELIO_DVFSRC_OPP_MT6775_H

extern int vcore_opp_init(void);
extern unsigned int get_vcore_opp_volt(unsigned int opp);
extern unsigned int get_cur_vcore_opp(void);
extern unsigned int get_cur_ddr_opp(void);

extern int vcore_to_vcore_dvfs_opp[];
extern int ddr_to_vcore_dvfs_opp[];
extern int vcore_dvfs_to_vcore_opp[];
extern int vcore_dvfs_to_ddr_opp[];

enum ddr_opp {
	DDR_OPP_UNREQ = -1,
	DDR_OPP_0 = 0,		/* 3733 MB/s */
	DDR_OPP_1,		/* 3200 MB/s */
	DDR_OPP_2,		/* 2400 MB/s */
	DDR_OPP_3,		/* 1600 MB/s */
	DDR_OPP_NUM,
};

enum vcore_opp {
	VCORE_OPP_UNREQ = -1,
	VCORE_OPP_0 = 0,	/* 0.8V */
	VCORE_OPP_1,		/* 0.7V */
	VCORE_OPP_2,		/* 0.625V */
	VCORE_OPP_NUM,
};

enum vcore_dvfs_opp {
	VCORE_DVFS_OPP_UNREQ = -1,
	VCORE_DVFS_OPP_0 = 0,	/* 3733 MB/s / 0.8V */
	VCORE_DVFS_OPP_1,	/* 3200 MB/s / 0.8V */
	VCORE_DVFS_OPP_2,	/* 3200 MB/s / 0.7V */
	VCORE_DVFS_OPP_3,	/* 2400 MB/s / 0.7V */
	VCORE_DVFS_OPP_4,	/* 2400 MB/s / 0.625V */
	VCORE_DVFS_OPP_5,	/* 1600 MB/s / 0.625V / ignore this opp */
	VCORE_DVFS_OPP_6,	/* 1600 MB/s / 0.625V */
	VCORE_DVFS_OPP_NUM,
};

#endif /* __HELIO_DVFSRC_MT6775_H */
