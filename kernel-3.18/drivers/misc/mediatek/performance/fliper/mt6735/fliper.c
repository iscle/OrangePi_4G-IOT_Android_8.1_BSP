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

#include <linux/proc_fs.h>
#include <linux/sched.h>
#include <linux/seq_file.h>
#include <linux/kallsyms.h>
#include <linux/utsname.h>
#include <linux/module.h>
#include <linux/moduleparam.h>
#include <asm/uaccess.h>
#include <linux/printk.h>

#include <linux/platform_device.h>
#include <linux/suspend.h>

#include <linux/timer.h>
#include <linux/jiffies.h>

#ifdef CONFIG_PM_AUTOSLEEP
#include <linux/fb.h>
#include <linux/notifier.h>
#endif
#include <mt_vcore_dvfs_2.h>
#include "mach/fliper.h"
#include "mach/mt_mem_bw.h"
#define SEQ_printf(m, x...)\
	do {\
		if (m)\
			seq_printf(m, x);\
		else\
			pr_debug(x);\
	} while (0)

#define X_ms 100
#define Y_steps (2000/X_ms)
#define BW_THRESHOLD 1500
#define BW_THRESHOLD_MAX 9000
#define BW_THRESHOLD_MIN 1000
static int num_opp;
static int perf_now = -1;
static int bw_threshold;
static int fliper_enabled;
static void enable_fliper(void);
static void disable_fliper(void);
static int vcore_high(void);
static int vcore_low(void);
extern unsigned int get_ddr_type(void)__attribute__((weak));
/* define supported DRAM types */
enum {
	LPDDR2 = 0,
	DDR3_16,
	DDR3_32,
	LPDDR3,
	mDDR,
};
static int fliper_debug;
static int ddr_type;
static ssize_t mt_fliper_write(struct file *filp, const char *ubuf,
		size_t cnt, loff_t *data)
{
	char buf[64];
	unsigned long val;
	int ret;

	if (cnt >= sizeof(buf))
		return -EINVAL;

	if (copy_from_user(&buf, ubuf, cnt))
		return -EFAULT;

	buf[cnt] = 0;

	ret = kstrtoul(buf, 10, (unsigned long *)&val);
	if (ret < 0) {
		if (fliper_debug)
			pr_crit("\n<<SOC DVFS FLIPER>> no support change POWER_MODE\n");
		return cnt;
	}
	if (val == 1) {
		fliper_enabled = 1;
		enable_fliper();
	} else if (val == 0) {
		fliper_enabled = 0;
		disable_fliper();
	} else if (val == 3) {
		fliper_debug ^= 1;
	} else if (val == 4) {
		fliper_set_bw(0);
		fliper_set_bw(10000);
		fliper_set_bw(BW_THRESHOLD_HIGH);
	} else if (val == 5) {
		fliper_restore_bw();
	} else if (val == 6) {
		ret = vcore_high();
		pr_crit("\n<<SOC DVFS FLIPER>> Command flip to S(%d)\n", ret);
	} else if (val == 7) {
		ret = vcore_low();
		pr_crit("\n<<SOC DVFS FLIPER>> Command flip to E(%d)\n", ret);
	} else if (val == 8) {
		if (bw_threshold < 5000)
			bw_threshold += 250;
		pr_crit("\n<<SOC DVFS FLIPER>> Command Change EMI bandwidth threshold to %d MB/s\n", bw_threshold);
	} else if (val == 9) {
		if (bw_threshold > 500)
			bw_threshold -= 250;
		pr_crit("\n<<SOC DVFS FLIPER>> Command Change EMI bandwidth threshold to %d MB/s\n", bw_threshold);
	}
	pr_crit(" fliper option: %lu\n", val);
	return cnt;

}

static int mt_fliper_show(struct seq_file *m, void *v)
{
	SEQ_printf(m, "----------------------------------------\n");
	SEQ_printf(m, "Fliper Enabled:%d, bw threshold:%d MB/s\n", fliper_enabled, bw_threshold);
	SEQ_printf(m, "----------------------------------------\n");
	return 0;
}
/*** Seq operation of mtprof ****/
static int mt_fliper_open(struct inode *inode, struct file *file)
{
	return single_open(file, mt_fliper_show, inode->i_private);
}

static const struct file_operations mt_fliper_fops = {
	.open = mt_fliper_open,
	.write = mt_fliper_write,
	.read = seq_read,
	.llseek = seq_lseek,
	.release = single_release,
};
/******* POWER PERF TRANSFORMER *********/
#include <asm/div64.h>
#include <mt_cpufreq.h>

static void mt_power_pef_transfer(void);
static DEFINE_TIMER(mt_pp_transfer_timer, (void *)mt_power_pef_transfer, 0, 0);
static int pp_index;

static void mt_power_pef_transfer_work(void);
static DECLARE_WORK(mt_pp_work, (void *) mt_power_pef_transfer_work);

static int vcore_high(void)
{
	int ret = 0;

	ret = vcorefs_request_dvfs_opp(KIR_EMIBW, OPPI_PERF);
	return ret;
}
static int vcore_low(void)
{
	int ret = 0;

	ret = vcorefs_request_dvfs_opp(KIR_EMIBW, OPPI_UNREQ);
	return ret;
}

static void mt_power_pef_transfer_work(void)
{
	unsigned long long emi_bw = 0;
	int perf_mode = -1;
	int ret;
	unsigned long long t1, t2;

	t1 = 0; t2 = 0;
	/*Get EMI*/
	if (fliper_debug == 1) {
		t1 = sched_clock();
		emi_bw = get_mem_bw();
		t2 = sched_clock();
	} else
		emi_bw = get_mem_bw();

	if (emi_bw > bw_threshold)
		perf_mode = 1;

	if (perf_mode == 1) {
		if (pp_index == 0) {
			ret = vcore_high();
			pr_crit("\n<<SOC DVFS FLIPER>> flip to S(%d), %llu\n", ret, emi_bw);
		}
		pp_index = 1 << Y_steps;
	} else {
		if (pp_index == 1) {
			ret = vcore_low();
			pr_crit("\n<<SOC DVFS FLIPER>> flip to E(%d), %llu\n", ret, emi_bw);
		}
		pp_index = pp_index >> 1;
	}

	if (fliper_debug == 1)
		pr_crit("EMI:Rate:count:mode %6llu:%4d :%llu ns\n", emi_bw, pp_index, t2-t1);


}
int fliper_set_bw(int bw)
{
	if (bw <= BW_THRESHOLD_MAX && bw >= BW_THRESHOLD_MIN) {
		pr_crit("\n<<SOC DVFS FLIPER>> Set bdw threshold %d -> %d\n", bw_threshold, bw);
		bw_threshold = bw;
	} else {
		pr_crit("\n<<SOC DVFS FLIPER>> Set bdw threshold Error: %d (MAX:%d, MIN:%d)\n"
			, bw, BW_THRESHOLD_MAX, BW_THRESHOLD_MIN);
	}
	return 0;
}
int fliper_restore_bw(void)
{
	pr_crit("\n<<SOC DVFS FLIPER>> Restore bdw threshold %d -> %d\n", bw_threshold, BW_THRESHOLD);
	bw_threshold = BW_THRESHOLD;
	return 0;
}
static void enable_fliper(void)
{
	pr_crit("fliper enable +++\n");
	mod_timer(&mt_pp_transfer_timer, jiffies + msecs_to_jiffies(X_ms));
}
static void disable_fliper(void)
{
	pr_crit("fliper disable ---\n");
	del_timer(&mt_pp_transfer_timer);
}
static void mt_power_pef_transfer(void)
{
	mod_timer(&mt_pp_transfer_timer, jiffies + msecs_to_jiffies(X_ms));
	schedule_work(&mt_pp_work);
}
#ifndef CONFIG_PM_AUTOSLEEP
	static int
fliper_pm_callback(struct notifier_block *nb,
		unsigned long action, void *ptr)
{
	int ret;

	switch (action) {

	case PM_SUSPEND_PREPARE:
		ret = vcore_low();
		pr_crit("\n<<SOC DVFS FLIPER>> Suspend and flip to E(%d)\n", ret);
		pp_index = 0;
		disable_fliper();
		break;
	case PM_HIBERNATION_PREPARE:
		break;

	case PM_POST_SUSPEND:
		if (fliper_enabled == 1)
			enable_fliper();
		else
			pr_crit("\n<<SOC DVFS FLIPER>> Resume enable flipper but flipper is disabled\n");
		break;

	case PM_POST_HIBERNATION:
		break;

	default:
		return NOTIFY_DONE;
	}

	return NOTIFY_OK;
}
#endif

#ifdef CONFIG_PM_AUTOSLEEP
static void fliper_early_suspend(void)
{
	int ret;

	ret = vcore_low();
	pr_emerg("\n<<SOC DVFS FLIPER>> Early Suspend and flip to E(%d)\n", ret);
	pp_index = 0;
	disable_fliper();
}

static void fliper_late_resume(void)
{
	pr_emerg("\n<<SOC DVFS FLIPER>> Late Resume\n");
	enable_fliper();
}




static int fliper_fb_notifier_callback(struct notifier_block *self, unsigned long event, void *fb_evdata)
{
	struct fb_event *evdata = fb_evdata;
	int blank;

	if (event != FB_EVENT_BLANK)
		return 0;

	blank = *(int *)evdata->data;

	switch (blank) {
	case FB_BLANK_UNBLANK:
	case FB_BLANK_NORMAL:
		fliper_late_resume();
		break;
	case FB_BLANK_VSYNC_SUSPEND:
	case FB_BLANK_HSYNC_SUSPEND:
		break;
	case FB_BLANK_POWERDOWN:
		fliper_early_suspend();
		break;
	default:
		return -EINVAL;
	}

	return 0;
}

static struct notifier_block fliper_fb_notif = {
	.notifier_call = fliper_fb_notifier_callback,
};
#endif
static ssize_t mt_perf_write(struct file *filp, const char *ubuf,
		size_t cnt, loff_t *data)
{
	char buf[64];
	int val;
	int ret;
	/*
	if (ddr_type == TYPE_LPDDR3)
		return cnt;
	*/
	if (fliper_debug)
		return cnt;

	if (cnt >= sizeof(buf))
		return -EINVAL;

	if (copy_from_user(buf, ubuf, cnt))
		return -EFAULT;
	buf[cnt] = 0;
	ret = kstrtoint(buf, 10, &val);
	if (ret < 0)
		return ret;

	if (val < -1 || val > num_opp)
		return -1;

	if (vcorefs_request_dvfs_opp(KIR_PERF, val) < 0)
		return cnt;
	perf_now = val;
	return cnt;
}

static int mt_perf_show(struct seq_file *m, void *v)
{
	SEQ_printf(m, "perf_now :%d\n", perf_now);
	SEQ_printf(m, "num_opp :%d\n", num_opp);
	return 0;
}
/*** Seq operation of mtprof ****/
static int mt_perf_open(struct inode *inode, struct file *file)
{
	return single_open(file, mt_perf_show, inode->i_private);
}


static const struct file_operations mt_perf_fops = {
	.open = mt_perf_open,
	.write = mt_perf_write,
	.read = seq_read,
	.llseek = seq_lseek,
	.release = single_release,
};

/*-----------------------------------------------*/

#define TIME_5SEC_IN_MS 5000
static int __init init_fliper(void)
{
	struct proc_dir_entry *pe, *fliperfs_dir, *perf_dir;
	int ret = 0;

	pr_debug("init fliper driver start\n");
	fliperfs_dir = proc_mkdir("fliperfs", NULL);

	pe = proc_create("fliper", 0664, fliperfs_dir, &mt_fliper_fops);
	if (!pe)
		return -ENOMEM;
	perf_dir = proc_create("perf", 0644, fliperfs_dir, &mt_perf_fops);
	if (!perf_dir) {
		pr_debug("not perf_dir\n");
		return -ENOMEM;
	}
	bw_threshold = BW_THRESHOLD;
	pr_debug("prepare mt pp transfer: jiffies:%lu-->%lu\n", jiffies, jiffies + msecs_to_jiffies(TIME_5SEC_IN_MS));
	pr_debug("-	next jiffies:%lu >>> %lu\n", jiffies, jiffies + msecs_to_jiffies(X_ms));
	mod_timer(&mt_pp_transfer_timer, jiffies + msecs_to_jiffies(TIME_5SEC_IN_MS));
	fliper_enabled = 1;

	num_opp = 2;
	ddr_type = get_ddr_type();
#ifdef CONFIG_PM_AUTOSLEEP
	ret = fb_register_client(&fliper_fb_notif);
	if (ret)
		pr_debug("\n<<SOC DVFS FLIPER>> register fb notifier, ret=%d\n", ret);
#else
	pm_notifier(fliper_pm_callback, 0);
#endif

	pr_debug("init fliper driver done\n");
	return 0;
}
late_initcall(init_fliper);
