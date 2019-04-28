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

#ifndef __MTK_CM_MGR_MT6775_DATA_H__
#define __MTK_CM_MGR_MT6775_DATA_H__

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
static int cpu_power_ratio_up[CM_MGR_EMI_OPP] = {100, 100, 100};
static int cpu_power_ratio_down[CM_MGR_EMI_OPP] = {100, 100, 100};
int vcore_power_ratio_up[CM_MGR_EMI_OPP] = {80, 100, 100};
int vcore_power_ratio_down[CM_MGR_EMI_OPP] = {80, 100, 100};
static int debounce_times_up_adb[CM_MGR_EMI_OPP] = {0, 3, 3};
static int debounce_times_down_adb[CM_MGR_EMI_OPP] = {0, 3, 3};
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
	{46, 205, 246},
	{102, 630, 537},
	{139, 570, 602},
	{171, 598, 629},
	{258, 634, 669},
	{279, 717, 717},
	{280, 780, 769},
	{280, 805, 819},
	{280, 835, 832},
	{280, 864, 845},
	{280, 894, 858},
	{292, 923, 871},
	{303, 953, 885},
	{303, 749, 898},
	{303, 761, 911},
	{303, 772, 924},
	{303, 799, 937},
	{303, 799, 950},
	{303, 799, 964},
	{303, 799, 977},
	{303, 799, 990},
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
	/* OD+ */
	{180, 224},
	{170, 214},
	{158, 200},
	{150, 185},
	{136, 170},
	{124, 155},
	{112, 141},
	{102, 127},
	{91, 113},
	{80, 99},
	{70, 87},
	{61, 76},
	{52, 64},
	{43, 54},
	{36, 44},
	{29, 36},
};

#ifndef ATF_SECURE_SMC
static unsigned int cpu_power_gain_UpLow0[][CM_MGR_CPU_ARRAY_SIZE] = {
	{2, 25, 2, 19, 1, 11},
	{4, 50, 3, 38, 2, 22},
	{7, 75, 5, 56, 3, 32},
	{9, 100, 7, 75, 4, 43},
	{68, 363, 8, 94, 5, 54},
	{66, 372, 63, 335, 6, 65},
	{65, 381, 61, 338, 7, 75},
	{63, 391, 59, 341, 8, 86},
	{62, 400, 57, 344, 9, 97},
	{60, 409, 55, 347, 47, 266},
	{59, 419, 53, 350, 45, 261},
	{57, 428, 50, 353, 42, 256},
	{56, 437, 48, 356, 39, 251},
	{54, 446, 46, 359, 36, 246},
	{53, 456, 44, 362, 33, 240},
	{51, 465, 42, 365, 31, 235},
	{50, 474, 40, 368, 28, 230},
	{48, 484, 38, 371, 25, 225},
	{46, 493, 36, 374, 22, 220},
	{45, 502, 34, 377, 19, 215},
};

static unsigned int cpu_power_gain_DownLow0[][CM_MGR_CPU_ARRAY_SIZE] = {
	{3, 38, 2, 25, 1, 13},
	{7, 75, 4, 50, 2, 25},
	{79, 401, 7, 75, 3, 38},
	{78, 422, 9, 100, 4, 50},
	{78, 443, 72, 380, 6, 63},
	{77, 464, 70, 388, 7, 75},
	{76, 484, 68, 396, 8, 88},
	{76, 505, 67, 404, 9, 100},
	{75, 526, 65, 413, 55, 300},
	{74, 546, 63, 421, 52, 295},
	{74, 567, 61, 429, 49, 291},
	{73, 588, 59, 437, 46, 286},
	{72, 608, 58, 445, 43, 282},
	{71, 629, 56, 453, 40, 277},
	{71, 650, 54, 461, 37, 273},
	{70, 670, 52, 470, 34, 269},
	{69, 691, 50, 478, 31, 264},
	{69, 712, 49, 486, 28, 260},
	{68, 732, 47, 494, 25, 255},
	{67, 753, 45, 502, 22, 251},
};

static unsigned int cpu_power_gain_UpHigh0[][CM_MGR_CPU_ARRAY_SIZE] = {
	{2, 25, 2, 19, 1, 11},
	{4, 50, 3, 38, 2, 22},
	{7, 75, 5, 56, 3, 32},
	{9, 100, 7, 75, 4, 43},
	{11, 126, 8, 94, 5, 54},
	{13, 151, 10, 113, 6, 65},
	{107, 560, 12, 132, 7, 75},
	{103, 555, 13, 151, 8, 86},
	{98, 551, 93, 494, 9, 97},
	{93, 546, 87, 483, 10, 108},
	{88, 542, 82, 473, 11, 118},
	{83, 537, 77, 462, 12, 129},
	{79, 533, 71, 451, 12, 140},
	{74, 529, 66, 441, 13, 151},
	{69, 524, 61, 430, 14, 161},
	{64, 520, 55, 419, 44, 290},
	{59, 515, 50, 409, 38, 271},
	{55, 511, 44, 398, 31, 253},
	{50, 506, 39, 387, 25, 234},
	{45, 502, 34, 377, 19, 215},
};

static unsigned int cpu_power_gain_DownHigh0[][CM_MGR_CPU_ARRAY_SIZE] = {
	{3, 38, 2, 25, 1, 13},
	{7, 75, 4, 50, 2, 25},
	{10, 113, 7, 75, 3, 38},
	{13, 151, 9, 100, 4, 50},
	{129, 658, 11, 126, 6, 63},
	{125, 664, 13, 151, 7, 75},
	{121, 670, 113, 582, 8, 88},
	{117, 677, 108, 576, 9, 100},
	{113, 683, 102, 570, 10, 113},
	{108, 689, 97, 564, 11, 125},
	{104, 696, 92, 558, 12, 138},
	{100, 702, 87, 552, 13, 151},
	{96, 708, 82, 545, 15, 163},
	{92, 715, 76, 539, 61, 363},
	{88, 721, 71, 533, 54, 345},
	{84, 728, 66, 527, 48, 326},
	{80, 734, 61, 521, 42, 307},
	{76, 740, 55, 514, 35, 288},
	{71, 747, 50, 508, 29, 270},
	{67, 753, 45, 502, 22, 251},
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

#endif	/* __MTK_CM_MGR_MT6775_DATA_H__ */
