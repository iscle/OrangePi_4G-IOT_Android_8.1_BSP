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

#include <linux/device.h>
#include <linux/seq_file.h>
#include <linux/file.h>
#include <linux/proc_fs.h>
#include <linux/uaccess.h>

#include <helio-dvfsrc.h>
#include <helio-dvfsrc-opp.h>
#include <mtk_spm_vcore_dvfs.h>
#include <mt-plat/mtk_devinfo.h>
#include <mtk_dramc.h>

#define VCORE_OPP_EFUSE_NUM     (2)

__weak int spm_vcorefs_pwarp_cmd(void) { return 0; }
__weak int get_ddr_type(void) { return TYPE_LPDDR4X; }

/* SOC v1 Voltage (10uv)*/
static unsigned int vcore_opp_L4_2CH[VCORE_OPP_NUM][VCORE_OPP_EFUSE_NUM] = {
	{ 800000, 800000 },
	{ 725000, 700000 },
	{ 650000, 625000 }
};

int vcore_to_vcore_dvfs_opp[] = {
	VCORE_DVFS_OPP_1, VCORE_DVFS_OPP_3, VCORE_DVFS_OPP_6 };

int ddr_to_vcore_dvfs_opp[]   = {
	VCORE_DVFS_OPP_0, VCORE_DVFS_OPP_2, VCORE_DVFS_OPP_4, VCORE_DVFS_OPP_6 };

int vcore_dvfs_to_vcore_opp[] = {
	VCORE_OPP_0, VCORE_OPP_0, VCORE_OPP_1, VCORE_OPP_1,
	VCORE_OPP_2, VCORE_OPP_2, VCORE_OPP_2 };

int vcore_dvfs_to_ddr_opp[]   = {
	DDR_OPP_0, DDR_OPP_1, DDR_OPP_1, DDR_OPP_2,
	DDR_OPP_2, DDR_OPP_3, DDR_OPP_3 };

/* ptr that points to v1 or v2 opp table */
unsigned int (*vcore_opp)[VCORE_OPP_EFUSE_NUM];

/* final vcore opp table */
unsigned int vcore_opp_table[VCORE_OPP_NUM];

/* record index for vcore opp table from efuse */
unsigned int vcore_opp_efuse_idx[VCORE_OPP_NUM] = { 0 };

unsigned int get_cur_vcore_opp(void)
{
	return vcore_dvfs_to_vcore_opp[get_cur_vcore_dvfs_opp()];
}

unsigned int get_cur_ddr_opp(void)
{
	return vcore_dvfs_to_ddr_opp[get_cur_vcore_dvfs_opp()];
}

unsigned int get_vcore_opp_volt(unsigned int opp)
{
	if (opp >= VCORE_OPP_NUM) {
		pr_err("WRONG OPP: %u\n", opp);
		return 0;
	}

	return vcore_opp_table[opp];
}

static unsigned int update_vcore_opp_uv(unsigned int opp, unsigned int vcore_uv)
{
	unsigned int ret = 0;
#ifdef CONFIG_MTK_TINYSYS_SSPM_SUPPORT
	int i;
#endif

	if ((opp < VCORE_OPP_NUM) && (opp >= 0))
		vcore_opp_table[opp] = vcore_uv;
	else
		return 0;

#ifdef CONFIG_MTK_TINYSYS_SSPM_SUPPORT
	for (i = vcore_to_vcore_dvfs_opp[opp]; i >= 0 &&
			(opp == 0 || i > vcore_to_vcore_dvfs_opp[opp - 1]); i--)
		dvfsrc_update_sspm_vcore_opp_table(i, vcore_uv);
#endif

	ret = spm_vcorefs_pwarp_cmd();

	return ret;
}

static int get_soc_efuse(void)
{
	return (get_devinfo_with_index(50) >> 20) & 0xF;
}

static void build_vcore_opp_table(unsigned int ddr_type, unsigned int soc_efuse)
{
	int i, mask = 0x1;

	if (ddr_type == TYPE_LPDDR4X) {
		vcore_opp = &vcore_opp_L4_2CH[0];

		vcore_opp_efuse_idx[0] = 0; /* 0.8V, no corner tightening*/
		vcore_opp_efuse_idx[1] = (soc_efuse >> 2) & mask; /* 0.7V */
		vcore_opp_efuse_idx[2] = soc_efuse & mask; /* 0.625 */
	} else {
		pr_err("WRONG DRAM TYPE: %d\n", ddr_type);
		return;
	}

	for (i = 0; i < VCORE_OPP_NUM; i++)
		vcore_opp_table[i] = *(vcore_opp[i] + vcore_opp_efuse_idx[i]);

	for (i = VCORE_OPP_NUM - 2; i >= 0; i--)
		vcore_opp_table[i] = max(vcore_opp_table[i], vcore_opp_table[i + 1]);
}

static int vcore_opp_proc_show(struct seq_file *m, void *v)
{
	unsigned int i = 0;

	for (i = 0; i < VCORE_OPP_NUM; i++)
		seq_printf(m, "%d ", get_vcore_opp_volt(i));
	seq_puts(m, "\n");

	return 0;
}

static ssize_t vcore_opp_proc_write(struct file *file,
		const char __user *buffer, size_t count, loff_t *pos)
{
	int ret;
	s32 opp, vcore_uv;
	char *buf = (char *) __get_free_page(GFP_USER);

	if (!buf)
		return -ENOMEM;

	ret = -EINVAL;
	if (count >= PAGE_SIZE)
		goto out;

	ret = -EFAULT;
	if (copy_from_user(buf, buffer, count))
		goto out;
	buf[count] = '\0';

	if (sscanf(buf, "%u %u", &opp, &vcore_uv) == 2)
		update_vcore_opp_uv(opp, vcore_uv);
	else
		ret = -EINVAL;
out:
	free_page((unsigned long)buf);

	return (ret < 0) ? ret : count;
}

#define PROC_FOPS_RW(name)					\
	static int name ## _proc_open(struct inode *inode,	\
		struct file *file)				\
	{							\
		return single_open(file, name ## _proc_show,	\
			PDE_DATA(inode));			\
	}							\
	static const struct file_operations name ## _proc_fops = {	\
		.owner		= THIS_MODULE,				\
		.open		= name ## _proc_open,			\
		.read		= seq_read,				\
		.llseek		= seq_lseek,				\
		.release	= single_release,			\
		.write		= name ## _proc_write,			\
	}

#define PROC_ENTRY(name)	{__stringify(name), &name ## _proc_fops}

PROC_FOPS_RW(vcore_opp);

static int vcore_opp_procfs_init(void)
{
	struct proc_dir_entry *dir = NULL;
	int ret = 0;
	int i;

	struct pentry {
		const char *name;
		const struct file_operations *fops;
	};

	struct pentry det_entries_vcore[] = {
		PROC_ENTRY(vcore_opp),
	};

	dir = proc_mkdir("vcore_opp", NULL);
	if (!dir) {
		pr_err("%s: Failed to create /proc/vcore_opp dir\n", __func__);
		return -ENOMEM;
	}

	for (i = 0; i < ARRAY_SIZE(det_entries_vcore); i++) {
		if (!proc_create(det_entries_vcore[i].name,
					S_IRUGO | S_IWUSR | S_IWGRP, dir,
					det_entries_vcore[i].fops)) {
			pr_err("%s: Failed to create /proc/vcore_opp/%s\n",
					__func__, det_entries_vcore[i].name);

			return -ENOMEM;
		}
	}

	return ret;
}

int vcore_opp_init(void)
{
	int ret = 0;

	ret = vcore_opp_procfs_init();
	if (ret)
		return ret;

	build_vcore_opp_table(get_ddr_type(), get_soc_efuse());

	return 0;
}

