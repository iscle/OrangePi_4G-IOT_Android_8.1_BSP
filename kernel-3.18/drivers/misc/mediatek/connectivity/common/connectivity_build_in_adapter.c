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

#ifdef DFT_TAG
#undef DFT_TAG
#endif
#define DFT_TAG "[CONNADP]"

#include "connectivity_build_in_adapter.h"


/*device tree mode*/
#ifdef CONFIG_OF
#include <linux/of.h>
#include <linux/of_irq.h>
#include <linux/irqreturn.h>
#include <linux/of_address.h>
#endif

#include <linux/platform_device.h>
#include <linux/pm_runtime.h>
#include <linux/of_reserved_mem.h>

#include <linux/interrupt.h>


#ifdef CONNADP_HAS_CLOCK_BUF_CTRL
#ifdef CONFIG_ARCH_MT6570
/* Ensure we #include from:
 * drivers/misc/mediatek/include/mt-plat/mt6570/include/mach/mt_clkbuf_ctl.h
 */
#include <mach/mt_clkbuf_ctl.h>
#else
/* For all other platforms, we could safely use this file */
#include <mt_clkbuf_ctl.h>
#endif
#endif

/* PMIC */
#if defined(CONNADP_HAS_PMIC_API) || defined(CONNADP_HAS_UPMU_VCN_CTRL)
#include <upmu_common.h>
#endif

/* MMC */
#include <linux/mmc/card.h>
#include <linux/mmc/host.h>
#include <sdio_ops.h>


phys_addr_t gConEmiPhyBase;
EXPORT_SYMBOL(gConEmiPhyBase);

unsigned long long gConEmiSize;
EXPORT_SYMBOL(gConEmiSize);

/*Reserved memory by device tree!*/
int reserve_memory_consys_fn(struct reserved_mem *rmem)
{
	pr_info(DFT_TAG "%s: name: %s, base: 0x%llx, size: 0x%llx\n",
		__func__, rmem->name, (unsigned long long)rmem->base,
		(unsigned long long)rmem->size);
	gConEmiPhyBase = rmem->base;
	gConEmiSize = rmem->size;
	return 0;
}

RESERVEDMEM_OF_DECLARE(reserve_memory_test, "mediatek,consys-reserve-memory", reserve_memory_consys_fn);


void connectivity_export_show_stack(struct task_struct *tsk, unsigned long *sp)
{
	show_stack(tsk, sp);
}
EXPORT_SYMBOL(connectivity_export_show_stack);

void connectivity_export_tracing_record_cmdline(struct task_struct *tsk)
{
	tracing_record_cmdline(tsk);
}
EXPORT_SYMBOL(connectivity_export_tracing_record_cmdline);

#ifdef CPU_BOOST
void connectivity_export_mt_ppm_sysboost_freq(enum ppm_sysboost_user user, unsigned int freq)
{
	mt_ppm_sysboost_freq(user, freq);
}
EXPORT_SYMBOL(connectivity_export_mt_ppm_sysboost_freq);

void connectivity_export_mt_ppm_sysboost_core(enum ppm_sysboost_user user, unsigned int core_num)
{
	mt_ppm_sysboost_core(user, core_num);
}
EXPORT_SYMBOL(connectivity_export_mt_ppm_sysboost_core);

void connectivity_export_mt_ppm_sysboost_set_core_limit(enum ppm_sysboost_user user, unsigned int cluster,
					int min_core, int max_core)
{
	mt_ppm_sysboost_set_core_limit(user, cluster, min_core, max_core);
}
EXPORT_SYMBOL(connectivity_export_mt_ppm_sysboost_set_core_limit);
#endif

#if defined(CONFIG_ARCH_MT6735) || defined(CONFIG_ARCH_MT6735M) || defined(CONFIG_ARCH_MT6753)
void connectivity_update_userlimit_cpu_freq(int kicker,
						int num_cluster, struct ppm_limit_data *freq_limit)
{
	update_userlimit_cpu_freq(kicker, num_cluster, freq_limit);
}
EXPORT_SYMBOL(connectivity_update_userlimit_cpu_freq);

void connectivity_update_userlimit_cpu_core(int kicker,
						int num_cluster, struct ppm_limit_data *core_limit)
{
	update_userlimit_cpu_core(kicker, num_cluster, core_limit);
}
EXPORT_SYMBOL(connectivity_update_userlimit_cpu_core);
#endif

/*******************************************************************************
 * Clock Buffer Control
 ******************************************************************************/
#ifdef CONNADP_HAS_CLOCK_BUF_CTRL
void connectivity_export_clk_buf_ctrl(/*enum clk_buf_id*/ int id, bool onoff)
{
	clk_buf_ctrl(id, onoff);
}
EXPORT_SYMBOL(connectivity_export_clk_buf_ctrl);
#endif

/*******************************************************************************
 * PMIC
 ******************************************************************************/
#ifdef CONNADP_HAS_PMIC_API
void connectivity_export_pmic_config_interface(unsigned int RegNum, unsigned int val,
					unsigned int MASK, unsigned int SHIFT)
{
	pmic_config_interface(RegNum, val, MASK, SHIFT);
}
EXPORT_SYMBOL(connectivity_export_pmic_config_interface);

void connectivity_export_pmic_read_interface(unsigned int RegNum, unsigned int *val,
					unsigned int MASK, unsigned int SHIFT)
{
	pmic_read_interface(RegNum, val, MASK, SHIFT);
}
EXPORT_SYMBOL(connectivity_export_pmic_read_interface);

void connectivity_export_pmic_set_register_value(/*PMU_FLAGS_LIST_ENUM*/ int flagname, unsigned int val)
{
	pmic_set_register_value(flagname, val);
}
EXPORT_SYMBOL(connectivity_export_pmic_set_register_value);
#endif
#ifdef CONNADP_HAS_UPMU_VCN_CTRL
void connectivity_export_upmu_set_vcn_1v8_lp_mode_set(unsigned int val)
{
	upmu_set_vcn_1v8_lp_mode_set(val);
}
EXPORT_SYMBOL(connectivity_export_upmu_set_vcn_1v8_lp_mode_set);

void connectivity_export_upmu_set_vcn28_on_ctrl(unsigned int val)
{
	upmu_set_vcn28_on_ctrl(val);
}
EXPORT_SYMBOL(connectivity_export_upmu_set_vcn28_on_ctrl);

void connectivity_export_upmu_set_vcn33_on_ctrl_bt(unsigned int val)
{
	upmu_set_vcn33_on_ctrl_bt(val);
}
EXPORT_SYMBOL(connectivity_export_upmu_set_vcn33_on_ctrl_bt);

void connectivity_export_upmu_set_vcn33_on_ctrl_wifi(unsigned int val)
{
	upmu_set_vcn33_on_ctrl_wifi(val);
}
EXPORT_SYMBOL(connectivity_export_upmu_set_vcn33_on_ctrl_wifi);
#endif

/*******************************************************************************
 * MMC
 ******************************************************************************/
int connectivity_export_mmc_io_rw_direct(struct mmc_card *card, int write, unsigned fn,
				unsigned addr, u8 in, u8 *out)
{
	return mmc_io_rw_direct(card, write, fn, addr, in, out);
}
EXPORT_SYMBOL(connectivity_export_mmc_io_rw_direct);

/*******************************************************************************
 * Watchdog
 ******************************************************************************/
#if defined(CONFIG_ARCH_MT6797)
int connectivity_export_mtk_wdt_swsysret_config(int bit, int set_value)
{
	return mtk_wdt_swsysret_config(bit, set_value);
}
EXPORT_SYMBOL(connectivity_export_mtk_wdt_swsysret_config);
#endif
