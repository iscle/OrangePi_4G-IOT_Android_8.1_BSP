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

#include <linux/init.h>
#include <linux/module.h>
#include <linux/kernel.h>
#include <linux/spinlock.h>
#include <linux/delay.h>
#include <linux/of_fdt.h>
#include <mt-plat/mtk_secure_api.h>

#ifdef CONFIG_OF
#include <linux/of.h>
#include <linux/of_irq.h>
#include <linux/of_address.h>
#endif

#include <mt-plat/aee.h>
#include <mt-plat/upmu_common.h>
#include <mt-plat/mtk_chip.h>

#include <mtk_spm_misc.h>
#include <mtk_spm_vcore_dvfs.h>
#include <mtk_spm_internal.h>
#include <mtk_spm_pmic_wrap.h>
#include <mtk_dvfsrc_reg.h>
#include <mtk_eem.h>
#include <ext_wd_drv.h>

#ifdef CONFIG_MTK_SMI_EXT
#include <mmdvfs_mgr.h>
#endif
#include <helio-dvfsrc.h>
#include <helio-dvfsrc-opp.h>

#define is_dvfs_in_progress()    (spm_read(DVFSRC_LEVEL) & 0xFFFF)
#define get_dvfs_level()         (spm_read(DVFSRC_LEVEL) >> 16)

/*
 * only for internal debug
 */
#define SPM_VCOREFS_TAG	"[VcoreFS] "
#define spm_vcorefs_err spm_vcorefs_info
#define spm_vcorefs_warn spm_vcorefs_info
#define spm_vcorefs_debug spm_vcorefs_info
#define spm_vcorefs_info(fmt, args...)	pr_notice(SPM_VCOREFS_TAG fmt, ##args)

void __iomem *dvfsrc_base;
void __iomem *qos_sram_base;

u32 vcore_to_vcore_dvfs_level[VCORE_OPP_NUM]		= { BIT(0), BIT(3), BIT(5) };
u32 emi_to_vcore_dvfs_level[DDR_OPP_NUM]		= { BIT(0), BIT(2), BIT(4), BIT(6)};
u32 vcore_dvfs_to_vcore_dvfs_level[VCORE_DVFS_OPP_NUM]	= { BIT(0), BIT(1), BIT(2), BIT(3), BIT(4), BIT(5), BIT(6)};

enum spm_vcorefs_step {
	SPM_VCOREFS_ENTER = 0x00000001,
	SPM_VCOREFS_DVFS_START = 0x000000ff,
	SPM_VCOREFS_DVFS_END = 0x000001ff,
	SPM_VCOREFS_LEAVE = 0x000007ff,
};

__weak int mtk_rgu_cfg_dvfsrc(int enable) { return 0; }
__weak int vcore_opp_init(void) { return 0; }
__weak unsigned int get_vcore_opp_volt(unsigned int opp) { return 0; }
__weak int __spm_get_dram_type(void) { return 0; }

static inline void spm_vcorefs_footprint(enum spm_vcorefs_step step)
{
#ifdef CONFIG_MTK_RAM_CONSOLE
	aee_rr_rec_vcore_dvfs_status(step);
#endif
}

char *spm_vcorefs_dump_dvfs_regs(char *p)
{
	if (p) {
		/* DVFSRC */
		p += sprintf(p, "DVFSRC_RECORD_COUNT    : 0x%x\n", spm_read(DVFSRC_RECORD_COUNT));
		p += sprintf(p, "DVFSRC_LAST            : 0x%x\n", spm_read(DVFSRC_LAST));
		p += sprintf(p, "DVFSRC_RECORD_0_1~3_1  : 0x%08x, 0x%08x, 0x%08x, 0x%08x\n",
							spm_read(DVFSRC_RECORD_0_1), spm_read(DVFSRC_RECORD_1_1),
							spm_read(DVFSRC_RECORD_2_1), spm_read(DVFSRC_RECORD_3_1));
		p += sprintf(p, "DVFSRC_RECORD_4_1~7_1  : 0x%08x, 0x%08x, 0x%08x, 0x%08x\n",
							spm_read(DVFSRC_RECORD_4_1), spm_read(DVFSRC_RECORD_5_1),
							spm_read(DVFSRC_RECORD_6_1), spm_read(DVFSRC_RECORD_7_1));
		p += sprintf(p, "DVFSRC_RECORD_0_0~3_0  : 0x%08x, 0x%08x, 0x%08x, 0x%08x\n",
							spm_read(DVFSRC_RECORD_0_0), spm_read(DVFSRC_RECORD_1_0),
							spm_read(DVFSRC_RECORD_2_0), spm_read(DVFSRC_RECORD_3_0));
		p += sprintf(p, "DVFSRC_RECORD_4_0~7_0  : 0x%08x, 0x%08x, 0x%08x, 0x%08x\n",
							spm_read(DVFSRC_RECORD_4_0), spm_read(DVFSRC_RECORD_5_0),
							spm_read(DVFSRC_RECORD_6_0), spm_read(DVFSRC_RECORD_7_0));
		p += sprintf(p, "DVFSRC_RECORD_MD_0~3   : 0x%08x, 0x%08x, 0x%08x, 0x%08x\n",
							spm_read(DVFSRC_RECORD_MD_0), spm_read(DVFSRC_RECORD_MD_1),
							spm_read(DVFSRC_RECORD_MD_2), spm_read(DVFSRC_RECORD_MD_3));
		p += sprintf(p, "DVFSRC_RECORD_MD_4~7   : 0x%08x, 0x%08x, 0x%08x, 0x%08x\n",
							spm_read(DVFSRC_RECORD_MD_4), spm_read(DVFSRC_RECORD_MD_5),
							spm_read(DVFSRC_RECORD_MD_6), spm_read(DVFSRC_RECORD_MD_7));
		p += sprintf(p, "DVFSRC_LEVEL           : 0x%x\n", spm_read(DVFSRC_LEVEL));
		p += sprintf(p, "DVFSRC_VCORE_REQUEST   : 0x%x\n", spm_read(DVFSRC_VCORE_REQUEST));
		p += sprintf(p, "DVFSRC_EMI_REQUEST     : 0x%x\n", spm_read(DVFSRC_EMI_REQUEST));
		p += sprintf(p, "DVFSRC_MD_REQUEST      : 0x%x\n", spm_read(DVFSRC_MD_REQUEST));
		p += sprintf(p, "DVFSRC_RSRV_0          : 0x%x\n", spm_read(DVFSRC_RSRV_0));
		/* SPM */
		p += sprintf(p, "SPM_SW_FLAG            : 0x%x\n", spm_read(SPM_SW_FLAG));
		p += sprintf(p, "SPM_SW_RSV_5           : 0x%x\n", spm_read(SPM_SW_RSV_5));
		/* p += sprintf(p, "DVFSRC_MD_GEAR         : 0x%x\n", spm_read(DVFSRC_MD_GEAR)); */
		p += sprintf(p, "MD2SPM_DVFS_CON        : 0x%x\n", spm_read(MD2SPM_DVFS_CON));
		/*
		 * p += sprintf(p, "SPM_DVFS_EVENT_STA     : 0x%x\n", spm_read(SPM_DVFS_EVENT_STA));
		 * p += sprintf(p, "SPM_DVFS_LEVEL         : 0x%x\n", spm_read(SPM_DVFS_LEVEL));
		 * p += sprintf(p, "SPM_DFS_LEVEL          : 0x%x\n", spm_read(SPM_DFS_LEVEL));
		 * p += sprintf(p, "SPM_DVS_LEVEL          : 0x%x\n", spm_read(SPM_DVS_LEVEL));
		 */

		p += sprintf(p, "PCM_REG_DATA_0~3       : 0x%x, 0x%x, 0x%x, 0x%x\n",
							spm_read(PCM_REG0_DATA), spm_read(PCM_REG1_DATA),
							spm_read(PCM_REG2_DATA), spm_read(PCM_REG3_DATA));
		p += sprintf(p, "PCM_REG_DATA_4~7       : 0x%x, 0x%x, 0x%x, 0x%x\n",
							spm_read(PCM_REG4_DATA), spm_read(PCM_REG5_DATA),
							spm_read(PCM_REG6_DATA), spm_read(PCM_REG7_DATA));
		p += sprintf(p, "PCM_REG_DATA_8~11      : 0x%x, 0x%x, 0x%x, 0x%x\n",
							spm_read(PCM_REG8_DATA), spm_read(PCM_REG9_DATA),
							spm_read(PCM_REG10_DATA), spm_read(PCM_REG11_DATA));
		p += sprintf(p, "PCM_REG_DATA_12~15     : 0x%x, 0x%x, 0x%x, 0x%x\n",
							spm_read(PCM_REG12_DATA), spm_read(PCM_REG13_DATA),
							spm_read(PCM_REG14_DATA), spm_read(PCM_REG15_DATA));
		/*
		 * p += sprintf(p, "MDPTP_VMODEM_SPM_DVFS_CMD16~19   : 0x%x, 0x%x, 0x%x, 0x%x\n",
		 *                 spm_read(SLEEP_REG_MD_SPM_DVFS_CMD16), spm_read(SLEEP_REG_MD_SPM_DVFS_CMD17),
		 *                 spm_read(SLEEP_REG_MD_SPM_DVFS_CMD18), spm_read(SLEEP_REG_MD_SPM_DVFS_CMD19));
		 * p += sprintf(p, "SPM_DVFS_CMD0~1        : 0x%x, 0x%x\n",
		 *                                         spm_read(SPM_DVFS_CMD0), spm_read(SPM_DVFS_CMD1));
		 */
		p += sprintf(p, "PCM_IM_PTR             : 0x%x (%u)\n", spm_read(PCM_IM_PTR), spm_read(PCM_IM_LEN));
	} else {
		/* DVFSRC */
		spm_vcorefs_warn("DVFSRC_RECORD_COUNT    : 0x%x\n", spm_read(DVFSRC_RECORD_COUNT));
		spm_vcorefs_warn("DVFSRC_LAST            : 0x%x\n", spm_read(DVFSRC_LAST));
		spm_vcorefs_warn("DVFSRC_RECORD_0_1~3_1  : 0x%08x, 0x%08x, 0x%08x, 0x%08x\n",
							spm_read(DVFSRC_RECORD_0_1), spm_read(DVFSRC_RECORD_1_1),
							spm_read(DVFSRC_RECORD_2_1), spm_read(DVFSRC_RECORD_3_1));
		spm_vcorefs_warn("DVFSRC_RECORD_4_1~7_1  : 0x%08x, 0x%08x, 0x%08x, 0x%08x\n",
							spm_read(DVFSRC_RECORD_4_1), spm_read(DVFSRC_RECORD_5_1),
							spm_read(DVFSRC_RECORD_6_1), spm_read(DVFSRC_RECORD_7_1));
		spm_vcorefs_warn("DVFSRC_RECORD_0_0~3_0  : 0x%08x, 0x%08x, 0x%08x, 0x%08x\n",
							spm_read(DVFSRC_RECORD_0_0), spm_read(DVFSRC_RECORD_1_0),
							spm_read(DVFSRC_RECORD_2_0), spm_read(DVFSRC_RECORD_3_0));
		spm_vcorefs_warn("DVFSRC_RECORD_4_0~7_0  : 0x%08x, 0x%08x, 0x%08x, 0x%08x\n",
							spm_read(DVFSRC_RECORD_4_0), spm_read(DVFSRC_RECORD_5_0),
							spm_read(DVFSRC_RECORD_6_0), spm_read(DVFSRC_RECORD_7_0));
		spm_vcorefs_warn("DVFSRC_RECORD_MD_0~3   : 0x%08x, 0x%08x, 0x%08x, 0x%08x\n",
							spm_read(DVFSRC_RECORD_MD_0), spm_read(DVFSRC_RECORD_MD_1),
							spm_read(DVFSRC_RECORD_MD_2), spm_read(DVFSRC_RECORD_MD_3));
		spm_vcorefs_warn("DVFSRC_RECORD_MD_4~7   : 0x%08x, 0x%08x, 0x%08x, 0x%08x\n",
							spm_read(DVFSRC_RECORD_MD_4), spm_read(DVFSRC_RECORD_MD_5),
							spm_read(DVFSRC_RECORD_MD_6), spm_read(DVFSRC_RECORD_MD_7));
		spm_vcorefs_warn("DVFSRC_LEVEL           : 0x%x\n", spm_read(DVFSRC_LEVEL));
		spm_vcorefs_warn("DVFSRC_VCORE_REQUEST   : 0x%x\n", spm_read(DVFSRC_VCORE_REQUEST));
		spm_vcorefs_warn("DVFSRC_EMI_REQUEST     : 0x%x\n", spm_read(DVFSRC_EMI_REQUEST));
		spm_vcorefs_warn("DVFSRC_MD_REQUEST      : 0x%x\n", spm_read(DVFSRC_MD_REQUEST));
		/* SPM */
		spm_vcorefs_warn("SPM_SW_FLAG            : 0x%x\n", spm_read(SPM_SW_FLAG));
		spm_vcorefs_warn("SPM_SW_RSV_5           : 0x%x\n", spm_read(SPM_SW_RSV_5));
		/* spm_vcorefs_warn("SPM_SW_RSV_11          : 0x%x\n", spm_read(SPM_SW_RSV_11)); */
		/* spm_vcorefs_warn("DVFSRC_MD_GEAR         : 0x%x\n", spm_read(DVFSRC_MD_GEAR)); */
		spm_vcorefs_warn("MD2SPM_DVFS_CON        : 0x%x\n", spm_read(MD2SPM_DVFS_CON));
		/*
		 * spm_vcorefs_warn("SPM_DVFS_EVENT_STA     : 0x%x\n", spm_read(SPM_DVFS_EVENT_STA));
		 * spm_vcorefs_warn("SPM_DVFS_LEVEL         : 0x%x\n", spm_read(SPM_DVFS_LEVEL));
		 * spm_vcorefs_warn("SPM_DFS_LEVEL          : 0x%x\n", spm_read(SPM_DFS_LEVEL));
		 * spm_vcorefs_warn("SPM_DVS_LEVEL          : 0x%x\n", spm_read(SPM_DVS_LEVEL));
		 */
		spm_vcorefs_warn("PCM_REG_DATA_0~3       : 0x%x, 0x%x, 0x%x, 0x%x\n",
							spm_read(PCM_REG0_DATA), spm_read(PCM_REG1_DATA),
							spm_read(PCM_REG2_DATA), spm_read(PCM_REG3_DATA));
		spm_vcorefs_warn("PCM_REG_DATA_4~7       : 0x%x, 0x%x, 0x%x, 0x%x\n",
							spm_read(PCM_REG4_DATA), spm_read(PCM_REG5_DATA),
							spm_read(PCM_REG6_DATA), spm_read(PCM_REG7_DATA));
		spm_vcorefs_warn("PCM_REG_DATA_8~11      : 0x%x, 0x%x, 0x%x, 0x%x\n",
							spm_read(PCM_REG8_DATA), spm_read(PCM_REG9_DATA),
							spm_read(PCM_REG10_DATA), spm_read(PCM_REG11_DATA));
		spm_vcorefs_warn("PCM_REG_DATA_12~15     : 0x%x, 0x%x, 0x%x, 0x%x\n",
							spm_read(PCM_REG12_DATA), spm_read(PCM_REG13_DATA),
							spm_read(PCM_REG14_DATA), spm_read(PCM_REG15_DATA));
		/*
		 * spm_vcorefs_warn("MDPTP_VMODEM_SPM_DVFS_CMD16~19   : 0x%x, 0x%x, 0x%x, 0x%x\n",
		 *                 spm_read(SLEEP_REG_MD_SPM_DVFS_CMD16), spm_read(SLEEP_REG_MD_SPM_DVFS_CMD17),
		 *                 spm_read(SLEEP_REG_MD_SPM_DVFS_CMD18), spm_read(SLEEP_REG_MD_SPM_DVFS_CMD19));
		 * spm_vcorefs_warn("SPM_DVFS_CMD0~1        : 0x%x, 0x%x\n",
		 *                                         spm_read(SPM_DVFS_CMD0), spm_read(SPM_DVFS_CMD1));
		 */
		spm_vcorefs_warn("PCM_IM_PTR             : 0x%x (%u)\n", spm_read(PCM_IM_PTR), spm_read(PCM_IM_LEN));
	}

	return p;
}

/*
 * condition: false will loop for check
 */
#define wait_spm_complete_by_condition(condition, timeout)	\
({								\
	int i = 0;						\
	while (!(condition)) {					\
		if (i >= (timeout)) {				\
			i = -EBUSY;				\
			break;					\
		}						\
		udelay(1);					\
		i++;						\
	}							\
	i;							\
})

u32 spm_vcorefs_get_MD_status(void)
{
	return spm_read(MD2SPM_DVFS_CON);
}

static void spm_dvfsfw_init(int curr_opp)
{
	unsigned long flags;

	spin_lock_irqsave(&__spm_lock, flags);

	mt_secure_call(MTK_SIP_KERNEL_SPM_VCOREFS_ARGS, VCOREFS_SMC_CMD_0, curr_opp, 0);

	spin_unlock_irqrestore(&__spm_lock, flags);
}

int spm_vcorefs_pwarp_cmd(void)
{
#ifndef CONFIG_MTK_TINYSYS_SSPM_SUPPORT
	/* PMIC_WRAP_PHASE_ALLINONE */
	mt_spm_pmic_wrap_set_cmd(PMIC_WRAP_PHASE_ALLINONE, CMD_8,
			vcore_uv_to_pmic(get_vcore_opp_volt(VCORE_OPP_2))); /* 0.625 */
	mt_spm_pmic_wrap_set_cmd(PMIC_WRAP_PHASE_ALLINONE, CMD_9,
			vcore_uv_to_pmic(get_vcore_opp_volt(VCORE_OPP_1))); /* 0.7 */
	mt_spm_pmic_wrap_set_cmd(PMIC_WRAP_PHASE_ALLINONE, CMD_10,
			vcore_uv_to_pmic(get_vcore_opp_volt(VCORE_OPP_0))); /* 0.8 */

	mt_spm_pmic_wrap_set_phase(PMIC_WRAP_PHASE_ALLINONE);

	spm_vcorefs_warn("spm_vcorefs_pwarp_cmd: kernel\n");

#else
	int ret;
	struct spm_data spm_d;

	memset(&spm_d, 0, sizeof(struct spm_data));

	spm_d.u.vcorefs.vcore_level0 = vcore_uv_to_pmic(get_vcore_opp_volt(VCORE_OPP_0));
	spm_d.u.vcorefs.vcore_level1 = vcore_uv_to_pmic(get_vcore_opp_volt(VCORE_OPP_1));
	spm_d.u.vcorefs.vcore_level2 = vcore_uv_to_pmic(get_vcore_opp_volt(VCORE_OPP_2));

	ret = spm_to_sspm_command(SPM_VCORE_PWARP_CMD, &spm_d);
	if (ret < 0)
		spm_crit2("ret %d", ret);

	spm_vcorefs_warn("spm_vcorefs_pwarp_cmd: sspm\n");
#endif
	return 0;
}

int spm_vcorefs_get_opp(void)
{
	unsigned long flags;
	int level;

	if (is_vcorefs_can_work() == 1) {
		spin_lock_irqsave(&__spm_lock, flags);

		level = (spm_read(DVFSRC_LEVEL) >> 16);

		if (level == 0x40)
			level = OPP_0;
		else if (level == 0x20)
			level = OPP_1;
		else if (level == 0x10)
			level = OPP_2;
		else if (level == 0x8)
			level = OPP_3;
		else if (level == 0x4)
			level = OPP_4;
		else if (level == 0x2)
			level = OPP_5;
		else if (level == 0x1)
			level = OPP_6;
		else if (level == 0x80)
			level = 7;
		else if (level == 0x100)
			level = 8;
		else if (level == 0x200)
			level = 9;
		else if (level == 0x400)
			level = 10;
		else if (level == 0x800)
			level = 11;
		else if (level == 0x1000)
			level = 12;
		else if (level == 0x2000)
			level = 13;
		else if (level == 0x4000)
			level = 14;
		else if (level == 0x8000)
			level = 15;

		spin_unlock_irqrestore(&__spm_lock, flags);
	} else {
		level = BOOT_UP_OPP;
	}

	return level;
}

static void dvfsrc_hw_policy_mask(bool force)
{
}

static int spm_trigger_dvfs(int kicker, int opp, bool fix)
{
	int r = 0;

	u32 vcore_req[NUM_OPP] = {0x2, 0x2, 0x2, 0x1, 0x1, 0x0, 0x0};
	u32 emi_req[NUM_OPP] = {0x3, 0x3, 0x2, 0x2, 0x1, 0x1, 0x0};
	/* u32 md_req[NUM_OPP] = {0x0, 0x0, 0x0, 0x0}; */
	u32 dvfsrc_level[NUM_OPP] = {0x40, 0x20, 0x10, 0x8, 0x4, 0x2, 0x1};

	if (fix)
		dvfsrc_hw_policy_mask(1);
	else
		dvfsrc_hw_policy_mask(0);

	/* check DVFS idle */
	r = wait_spm_complete_by_condition(is_dvfs_in_progress() == 0, SPM_DVFS_TIMEOUT);
	if (r < 0) {
		spm_vcorefs_dump_dvfs_regs(NULL);
		/* aee_kernel_warning("SPM Warring", "Vcore DVFS timeout warning"); */
		return -1;
	}

	spm_write(DVFSRC_SW_REQ, (spm_read(DVFSRC_SW_REQ) & ~(0x3 << 2)) | (vcore_req[opp] << 2));
	spm_write(DVFSRC_SW_REQ, (spm_read(DVFSRC_SW_REQ) & ~(0x3)) | (emi_req[opp]));

	/*
	 * vcorefs_crit_mask(log_mask(), kicker, "[%s] fix: %d, opp: %d, sw: 0x%x\n",
	 *                 __func__, fix, opp, spm_read(DVFSRC_SW_REQ));
	 */
	vcorefs_crit_mask(0, kicker, "[%s] fix: %d, opp: %d, sw: 0x%x\n",
			__func__, fix, opp, spm_read(DVFSRC_SW_REQ));

	/* check DVFS timer */
	if (fix)
		r = wait_spm_complete_by_condition(get_dvfs_level() == dvfsrc_level[opp], SPM_DVFS_TIMEOUT);
	else
		r = wait_spm_complete_by_condition(get_dvfs_level() >= dvfsrc_level[opp], SPM_DVFS_TIMEOUT);

	if (r < 0) {
		spm_vcorefs_dump_dvfs_regs(NULL);
		/* aee_kernel_warning("SPM Warring", "Vcore DVFS timeout warning"); */
		return -1;
	}

	return 0;
}

int spm_dvfs_flag_init(void)
{
	int flag = SPM_FLAG_RUN_COMMON_SCENARIO;

	if (!SPM_VCORE_DVS_EN)
		flag |= SPM_FLAG_DIS_VCORE_DVS;
	if (!SPM_DDR_DFS_EN)
		flag |= SPM_FLAG_DIS_VCORE_DFS;
	if (!SPM_MM_CLK_EN)
		flag |= SPM_FLAG_DIS_DVFS_MMPLL_SET;

	return flag;
}

void dvfsrc_set_vcore_request(unsigned int mask, unsigned int shift, unsigned int level)
{
	int r = 0;
	unsigned long flags;
	unsigned int val;

	spin_lock_irqsave(&__spm_lock, flags);

	/* check DVFS idle */
	r = wait_spm_complete_by_condition(is_dvfs_in_progress() == 0, SPM_DVFS_TIMEOUT);
	if (r < 0) {
		spm_vcorefs_dump_dvfs_regs(NULL);
		aee_kernel_exception("VCOREFS", "dvfsrc cannot be idle.");
		goto out;
	}

	val = (spm_read(DVFSRC_VCORE_REQUEST) & ~(mask << shift)) | (level << shift);
	spm_write(DVFSRC_VCORE_REQUEST, val);

	r = wait_spm_complete_by_condition(get_dvfs_level() >= vcore_to_vcore_dvfs_level[level], SPM_DVFS_TIMEOUT);
	if (r < 0) {
		spm_vcorefs_dump_dvfs_regs(NULL);
		aee_kernel_exception("VCOREFS", "dvfsrc cannot be done.");
	}

out:
	spin_unlock_irqrestore(&__spm_lock, flags);
}

static int scp_vcore_level;
void dvfsrc_set_scp_vcore_request(unsigned int level)
{
	if (is_vcorefs_can_work() != 1) {
		scp_vcore_level = level;
		return;
	}
	dvfsrc_set_vcore_request(0x3, 30, (level & 0x3));
}

void dvfsrc_set_sw_req2(unsigned int mask, unsigned int shift, unsigned int level)
{
	unsigned long flags;
	unsigned int val;

	spin_lock_irqsave(&__spm_lock, flags);

	val = (spm_read(DVFSRC_SW_REQ2) & ~(mask << shift)) | (level << shift);
	spm_write(DVFSRC_SW_REQ2, val);

	spin_unlock_irqrestore(&__spm_lock, flags);
}

void dvfsrc_power_model_ddr_request(unsigned int level)
{
	dvfsrc_set_sw_req2(0x3, 0, (level & 0x3));
}

void dvfsrc_init(void)
{
	unsigned long flags;

	spin_lock_irqsave(&__spm_lock, flags);

	/* LP4 2CH */
	spm_write(DVFSRC_LEVEL_LABEL_0_1, 0x00000000);
	spm_write(DVFSRC_LEVEL_LABEL_2_3, 0x00110010);
	spm_write(DVFSRC_LEVEL_LABEL_4_5, 0x00220021);
	spm_write(DVFSRC_LEVEL_LABEL_6_7, 0x00320032);
	spm_write(DVFSRC_LEVEL_LABEL_8_9, 0x00320032);
	spm_write(DVFSRC_LEVEL_LABEL_10_11, 0x00320032);
	spm_write(DVFSRC_LEVEL_LABEL_12_13, 0x00320032);
	spm_write(DVFSRC_LEVEL_LABEL_14_15, 0x00320032);

	spm_write(DVFSRC_TIMEOUT_NEXTREQ, 0x0000001E);

	spm_write(DVFSRC_EMI_QOS0, 0x33);
	spm_write(DVFSRC_EMI_QOS1, 0x4C);
	/* spm_write(DVFSRC_EMI_QOS2, 0x64); */

	spm_write(DVFSRC_EMI_REQUEST, 0x00390339);
	spm_write(DVFSRC_EMI_REQUEST2, 0x00000000);
	spm_write(DVFSRC_EMI_REQUEST3, 0x39000000);
	spm_write(DVFSRC_VCORE_REQUEST, 0x00390000);

	/* spm_write(DVFSRC_VCORE_REQUEST2, 0x00393939); */

	/* ToDo: following initial setting */
	/*
	 * spm_write(DVFSRC_EMI_HRT, 0x00001C14);
	 * spm_write(DVFSRC_EMI_HRT2 0x00001C14);
	 * spm_write(DVFSRC_EMI_HRT3 0x00001C14);
	 * spm_write(DVFSRC_EMI_QOS0 0x00000000);
	 * spm_write(DVFSRC_EMI_QOS1 0x00000000);
	 * spm_write(DVFSRC_EMI_QOS2 0x00000000);
	 * spm_write(DVFSRC_EMI_MD2SPM0, 0x0000003E);
	 * spm_write(DVFSRC_EMI_MD2SPM1, 0x800080C0);
	 * spm_write(DVFSRC_EMI_MD2SPM2, 0x800080C0);
	 * spm_write(DVFSRC_EMI_MD2SPM0_T, 0x00000000);
	 * spm_write(DVFSRC_EMI_MD2SPM1_T, 0x00000000);
	 * spm_write(DVFSRC_EMI_MD2SPM2_T, 0x00000000);
	 *
	 * spm_write(DVFSRC_VCORE_HRT, 0x00001C14);
	 * spm_write(DVFSRC_VCORE_HRT2 0x00001C14);
	 * spm_write(DVFSRC_VCORE_HRT3 0x00001C14);
	 * spm_write(DVFSRC_VCORE_QOS0 0x00000000);
	 * spm_write(DVFSRC_VCORE_QOS1 0x00000000);
	 * spm_write(DVFSRC_VCORE_QOS2 0x00000000);
	 * spm_write(DVFSRC_VCORE_MD2SPM0, 0x0000003E);
	 * spm_write(DVFSRC_VCORE_MD2SPM1, 0x800080C0);
	 * spm_write(DVFSRC_VCORE_MD2SPM2, 0x800080C0);
	 * spm_write(DVFSRC_VCORE_MD2SPM0_T, 0x00000000);
	 * spm_write(DVFSRC_VCORE_MD2SPM1_T, 0x00000000);
	 * spm_write(DVFSRC_VCORE_MD2SPM2_T, 0x00000000);
	 *
	 * spm_write(DVFSRC_MM_BW_0, 0x00000000);
	 * spm_write(DVFSRC_MM_BW_1, 0x00000000);
	 * spm_write(DVFSRC_MM_BW_2, 0x00000000);
	 * spm_write(DVFSRC_MM_BW_3, 0x00000000);
	 * spm_write(DVFSRC_MM_BW_4, 0x00000000);
	 * spm_write(DVFSRC_MM_BW_5, 0x00000000);
	 * spm_write(DVFSRC_MM_BW_6, 0x00000000);
	 * spm_write(DVFSRC_MM_BW_7, 0x00000000);
	 * spm_write(DVFSRC_MM_BW_8, 0x00000000);
	 * spm_write(DVFSRC_MM_BW_9, 0x00000000);
	 * spm_write(DVFSRC_MM_BW_10, 0x00000000);
	 * spm_write(DVFSRC_MM_BW_11, 0x00000000);
	 * spm_write(DVFSRC_MM_BW_12, 0x00000000);
	 * spm_write(DVFSRC_MM_BW_13, 0x00000000);
	 * spm_write(DVFSRC_MM_BW_14, 0x00000000);
	 * spm_write(DVFSRC_MM_BW_15, 0x00000000);
	 *
	 * spm_write(DVFSRC_MD_BW_0, 0x00000000);
	 * spm_write(DVFSRC_MD_BW_1, 0x00000000);
	 * spm_write(DVFSRC_MD_BW_2, 0x00000000);
	 * spm_write(DVFSRC_MD_BW_3, 0x00000000);
	 * spm_write(DVFSRC_MD_BW_4, 0x00000000);
	 * spm_write(DVFSRC_MD_BW_5, 0x00000000);
	 * spm_write(DVFSRC_MD_BW_6, 0x00000000);
	 * spm_write(DVFSRC_MD_BW_7, 0x00000000);
	 *
	 * spm_write(DVFSRC_INT_EN, 0x00000000);
	 * spm_write(DVFSRC_BW_MON_WINDOW, 0x00000000);
	 * spm_write(DVFSRC_BW_MON_THRES_1, 0x00000000);
	 * spm_write(DVFSRC_BW_MON_THRES_2, 0x00000000);
	 */

	/* spm_write(DVFSRC_RSRV_1, 0x00000004); */

	spm_write(DVFSRC_QOS_EN, 0x0000407F);
	/* spm_write(DVFSRC_FORCE, 0x00400000); */
	spm_write(DVFSRC_BASIC_CONTROL, 0x0000007B);
	spm_write(DVFSRC_BASIC_CONTROL, 0x0000017B);

	mtk_rgu_cfg_dvfsrc(1);

	spin_unlock_irqrestore(&__spm_lock, flags);
}

void dvfsrc_register_init(void)
{
	struct device_node *node;

	/* dvfsrc */
	node = of_find_compatible_node(NULL, NULL, "mediatek,dvfsrc_top");
	if (!node) {
		spm_vcorefs_err("[DVFSRC] find node failed\n");
		goto dvfsrc_exit;
	}

	dvfsrc_base = of_iomap(node, 0);
	if (!dvfsrc_base) {
		spm_vcorefs_err("[DVFSRC] base failed\n");
		goto dvfsrc_exit;
	}

#if defined(CONFIG_MACH_MT6775)
	qos_sram_base = of_iomap(node, 1);
	if (!qos_sram_base) {
		spm_vcorefs_err("[QOS_SRAM] base failed\n");
		goto dvfsrc_exit;
	}
#endif
dvfsrc_exit:

	spm_vcorefs_warn("spm_dvfsrc_register_init: dvfsrc_base = %p\n", dvfsrc_base);
}

void spm_check_status_before_dvfs(void)
{
	int flag;

	if (spm_read(PCM_REG15_DATA) != 0x0)
		return;

	flag = spm_dvfs_flag_init();

	spm_dvfsfw_init(spm_vcorefs_get_opp());

	spm_go_to_vcorefs(flag);
}

int spm_set_vcore_dvfs(struct kicker_config *krconf)
{
	unsigned long flags;
	int r = 0;
	u32 autok_kir_group = AUTOK_KIR_GROUP;
	bool fix = (((1U << krconf->kicker) & autok_kir_group) || krconf->kicker == KIR_SYSFSX) &&
									krconf->opp != OPP_UNREQ;
	int opp = fix ? krconf->opp : krconf->dvfs_opp;

	spm_check_status_before_dvfs();

	spm_vcorefs_footprint(SPM_VCOREFS_ENTER);

	spin_lock_irqsave(&__spm_lock, flags);

	spm_vcorefs_footprint(SPM_VCOREFS_DVFS_START);

	r = spm_trigger_dvfs(krconf->kicker, opp, fix);

	spm_vcorefs_footprint(SPM_VCOREFS_DVFS_END);

	spm_vcorefs_footprint(SPM_VCOREFS_LEAVE);

	spin_unlock_irqrestore(&__spm_lock, flags);

	spm_vcorefs_footprint(0);

	return r;
}

void spm_go_to_vcorefs(int spm_flags)
{
	unsigned long flags;

	spm_vcorefs_warn("pcm_flag: 0x%x\n", spm_flags);

	spin_lock_irqsave(&__spm_lock, flags);

	mt_secure_call(MTK_SIP_KERNEL_SPM_VCOREFS_ARGS, VCOREFS_SMC_CMD_1, spm_flags, 0);

	spin_unlock_irqrestore(&__spm_lock, flags);

	spm_vcorefs_warn("[%s] done\n", __func__);
}

void plat_info_init(void)
{
}

#ifndef CONFIG_MTK_QOS_SUPPORT
void spm_vcorefs_init(void)
{
	int flag;

	dvfsrc_register_init();
	vcorefs_module_init();
	plat_info_init();
#if defined(CONFIG_MTK_TINYSYS_SSPM_SUPPORT)
	helio_dvfsrc_sspm_ipi_init(is_vcorefs_feature_enable(), __spm_get_dram_type());
#endif

	if (is_vcorefs_feature_enable()) {
		flag = spm_dvfs_flag_init();
		vcore_opp_init();
		vcorefs_init_opp_table();
		spm_dvfsfw_init(spm_vcorefs_get_opp());
		spm_go_to_vcorefs(flag);
		dvfsrc_init();
		if (scp_vcore_level) {
			spm_vcorefs_warn("[%s] set_scp_vcore_req, level=%d\n", __func__, scp_vcore_level);
			dvfsrc_set_scp_vcore_request(scp_vcore_level);
			scp_vcore_level = 0;
		}
		vcorefs_late_init_dvfs();
		spm_vcorefs_warn("[%s] DONE\n", __func__);
	}
}
#endif

MODULE_DESCRIPTION("SPM VCORE-DVFS DRIVER");
