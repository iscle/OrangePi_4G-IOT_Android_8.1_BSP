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

#ifndef __MTK_CM_MGR_MT6758_DATA_H__
#define __MTK_CM_MGR_MT6758_DATA_H__

#define PROC_FOPS_RW(name)					\
	static int name ## _proc_open(struct inode *inode,	\
		struct file *file)				\
	{							\
		return single_open(file, name ## _proc_show,	\
			PDE_DATA(inode));			\
	}							\
	static const struct file_operations name ## _proc_fops = {	\
		.owner		  = THIS_MODULE,				\
		.open		   = name ## _proc_open,			\
		.read		   = seq_read,				\
		.llseek		 = seq_lseek,				\
		.release		= single_release,			\
		.write		  = name ## _proc_write,			\
	}

#define PROC_FOPS_RO(name)					\
	static int name ## _proc_open(struct inode *inode,	\
		struct file *file)				\
	{							\
		return single_open(file, name ## _proc_show,	\
			PDE_DATA(inode));			\
	}							\
	static const struct file_operations name ## _proc_fops = {	\
		.owner		  = THIS_MODULE,				\
		.open		   = name ## _proc_open,			\
		.read		   = seq_read,				\
		.llseek		 = seq_lseek,				\
		.release		= single_release,			\
	}

#define PROC_ENTRY(name)	{__stringify(name), &name ## _proc_fops}

struct cm_mgr_met_data {
	unsigned int cm_mgr_power[14];
	unsigned int cm_mgr_count[4];
	unsigned int cm_mgr_opp[6];
	unsigned int cm_mgr_loading[12];
	unsigned int cm_mgr_ratio[12];
	unsigned int cm_mgr_bw;
	unsigned int cm_mgr_valid;
};

static int light_load_cps = 1000;
static int cm_mgr_loop_count;
static int cm_mgr_loop;
static int total_bw_value;
static int cpu_power_ratio_up[CM_MGR_EMI_OPP] = {100, 100};
static int cpu_power_ratio_down[CM_MGR_EMI_OPP] = {100, 100};
int vcore_power_ratio_up[CM_MGR_EMI_OPP] = {80, 100};
int vcore_power_ratio_down[CM_MGR_EMI_OPP] = {80, 100};
static int debounce_times_up_adb[CM_MGR_EMI_OPP] = {0, 3};
static int debounce_times_down_adb[CM_MGR_EMI_OPP] = {0, 3};
static int debounce_times_reset_adb;
static int update;
static int update_v2f_table = 1;
static int cm_mgr_opp_enable = 1;
int cm_mgr_enable = 1;
#ifdef USE_TIMER_CHECK
int cm_mgr_timer_enable = 1;
#endif /* USE_TIMER_CHECK */
int cm_mgr_disable_fb = 1;
int cm_mgr_blank_status;

static unsigned int vcore_power_gain_0[][VCORE_ARRAY_SIZE] = {
	{130, 389},
	{202, 564},
	{236, 613},
	{273, 654},
	{283, 720},
	{285, 748},
	{284, 767},
	{279, 784},
	{274, 800},
	{269, 817},
	{264, 834},
	{259, 850},
	{259, 867},
	{259, 884},
	{259, 901},
	{259, 917},
};

#define VCORE_POWER_ARRAY_SIZE(name) \
	(sizeof(vcore_power_gain_##name) / sizeof(unsigned int) / VCORE_ARRAY_SIZE)

#define VCORE_POWER_GAIN_PTR(name) \
	(&vcore_power_gain_##name[0][0])

static int vcore_power_array_size(int idx)
{
	switch (idx) {
	case 0:
		return VCORE_POWER_ARRAY_SIZE(0);
	}

	return 0;
};

static unsigned int *vcore_power_gain_ptr(int idx)
{
	switch (idx) {
	case 0:
		return VCORE_POWER_GAIN_PTR(0);
	}

	return NULL;
};

static unsigned int *vcore_power_gain = VCORE_POWER_GAIN_PTR(0);
#define vcore_power_gain(p, i, j) (*(p + (i) * VCORE_ARRAY_SIZE + (j)))

static unsigned int _v2f_all[][CM_MGR_CPU_CLUSTER] = {
	/* FY */
	/* 20170925 */
	{212, 293},
	{198, 271},
	{182, 247},
	{169, 227},
	{151, 207},
	{138, 190},
	{123, 172},
	{108, 156},
	{94, 134},
	{80, 115},
	{65, 94},
	{51, 75},
	{42, 60},
	{32, 47},
	{22, 33},
	{12, 19},
};

#ifndef ATF_SECURE_SMC
static unsigned int cpu_power_gain_UpLow0[][CM_MGR_CPU_ARRAY_SIZE] = {
	/* 20170925 */
	{2, 3, 1, 2},
	{3, 6, 2, 5},
	{5, 10, 4, 7},
	{84, 106, 5, 10},
	{81, 104, 79, 100},
	{77, 101, 75, 96},
	{74, 99, 71, 93},
	{71, 96, 68, 90},
	{67, 93, 64, 86},
	{64, 91, 60, 83},
	{61, 88, 57, 79},
	{58, 85, 53, 76},
	{54, 83, 49, 72},
	{51, 80, 45, 69},
	{48, 78, 42, 65},
	{44, 75, 38, 62},
	{41, 72, 34, 59},
	{38, 70, 31, 55},
	{34, 67, 27, 52},
	{31, 64, 23, 48},
};

static unsigned int cpu_power_gain_DownLow0[][CM_MGR_CPU_ARRAY_SIZE] = {
	/* 20170925 */
	{2, 5, 2, 3},
	{5, 10, 3, 6},
	{96, 122, 5, 10},
	{93, 121, 90, 114},
	{91, 119, 87, 111},
	{88, 118, 83, 108},
	{85, 116, 79, 105},
	{82, 115, 76, 102},
	{79, 113, 72, 99},
	{76, 112, 68, 96},
	{73, 110, 64, 92},
	{70, 109, 61, 89},
	{67, 107, 57, 86},
	{64, 106, 53, 83},
	{61, 104, 50, 80},
	{58, 103, 46, 77},
	{56, 101, 42, 74},
	{53, 100, 39, 71},
	{50, 98, 35, 68},
	{47, 97, 31, 64},
};

static unsigned int cpu_power_gain_UpHigh0[][CM_MGR_CPU_ARRAY_SIZE] = {
	/* 20170925 */
	{2, 3, 1, 2},
	{3, 6, 2, 5},
	{5, 10, 4, 7},
	{6, 13, 5, 10},
	{8, 16, 6, 12},
	{9, 19, 7, 14},
	{11, 23, 8, 17},
	{110, 143, 9, 19},
	{103, 136, 11, 22},
	{96, 130, 12, 24},
	{90, 123, 86, 114},
	{83, 117, 79, 107},
	{77, 110, 72, 100},
	{70, 104, 65, 92},
	{64, 97, 58, 85},
	{57, 91, 51, 78},
	{51, 84, 44, 70},
	{44, 77, 37, 63},
	{38, 71, 30, 56},
	{31, 64, 23, 48},
};

static unsigned int cpu_power_gain_DownHigh0[][CM_MGR_CPU_ARRAY_SIZE] = {
	/* 20170925 */
	{2, 5, 2, 3},
	{5, 10, 3, 6},
	{7, 14, 5, 10},
	{9, 19, 6, 13},
	{12, 24, 8, 16},
	{139, 179, 9, 19},
	{132, 173, 11, 23},
	{125, 167, 119, 154},
	{119, 161, 112, 147},
	{112, 156, 105, 139},
	{106, 150, 97, 132},
	{99, 144, 90, 124},
	{93, 138, 83, 117},
	{86, 132, 75, 109},
	{80, 126, 68, 102},
	{73, 120, 61, 94},
	{66, 114, 53, 87},
	{60, 108, 46, 79},
	{53, 102, 39, 72},
	{47, 97, 31, 64},
};

#define cpu_power_gain(p, i, j) (*(p + (i) * CM_MGR_CPU_ARRAY_SIZE + (j)))
#define CPU_POWER_GAIN(a, b, c) \
	(&cpu_power_gain_##a##b##c[0][0])

static unsigned int *cpu_power_gain_up = CPU_POWER_GAIN(Up, High, 0);
static unsigned int *cpu_power_gain_down = CPU_POWER_GAIN(Down, High, 0);

static void cpu_power_gain_ptr(int opp, int tbl)
{
	if (opp < CM_MGR_LOWER_OPP) {
		switch (tbl) {
		case 0:
			cpu_power_gain_up = CPU_POWER_GAIN(Up, Low, 0);
			cpu_power_gain_down = CPU_POWER_GAIN(Down, Low, 0);
			break;
		}
	} else {
		switch (tbl) {
		case 0:
			cpu_power_gain_up = CPU_POWER_GAIN(Up, High, 0);
			cpu_power_gain_down = CPU_POWER_GAIN(Down, High, 0);
			break;
		}
	}
}

static int cpu_power_gain_opp(int bw, int is_up, int opp, int ratio_idx, int idx)
{
	cpu_power_gain_ptr(opp, cm_mgr_get_idx());

	if (is_up)
		return cpu_power_gain(cpu_power_gain_up, ratio_idx, idx);
	else
		return cpu_power_gain(cpu_power_gain_down, ratio_idx, idx);
}
#endif

#endif	/* __MTK_CM_MGR_MT6758_DATA_H__ */
