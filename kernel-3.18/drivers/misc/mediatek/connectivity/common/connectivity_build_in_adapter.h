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

#ifndef CONNECTIVITY_BUILD_IN_ADAPTER_H
#define CONNECTIVITY_BUILD_IN_ADAPTER_H

#include <linux/kernel.h>
#include <linux/sched.h>


/*******************************************************************************
 * Clock Buffer Control
 *
 * The Connsys adaptation layer must provide Clock Buffer Control support
 * should it be available from the platform.
 * Therefore CONNADP_HAS_CLOCK_BUF_CTRL is defined based on platform chip, and
 * is used to decide if adaptation has support on Clock Buffer Control.
 *
 * Each WMT platform file must still define its own CONSYS_CLOCK_BUF_CTRL to
 * decide if it is a co-clock'd platform.
 * It is possible that the Clock Buffer Control is available on the platform,
 * but is not used by Connsys.
 *
 * For Kernel-3.18, definition of CONNADP_HAS_CLOCK_BUF_CTRL must align with:
 *	drivers/misc/mediatek/base/power/include/mt_clkbuf_ctl.h
 *
 * Platform that wishes to use Clock Buffer Control, please be sure to #include
 * the header file above.
 ******************************************************************************/
#if defined(CONFIG_ARCH_MT6735) || defined(CONFIG_ARCH_MT6735M) || defined(CONFIG_ARCH_MT6753) || \
	defined(CONFIG_ARCH_MT6755) || \
	defined(CONFIG_ARCH_MT6757) || \
	defined(CONFIG_ARCH_MT6797) || \
	defined(CONFIG_ARCH_MT6570) || \
	defined(CONFIG_ARCH_MT6580)
#define CONNADP_HAS_CLOCK_BUF_CTRL
#define KERNEL_clk_buf_ctrl connectivity_export_clk_buf_ctrl
void connectivity_export_clk_buf_ctrl(/*enum clk_buf_id*/ int id, bool onoff);
#endif

/*******************************************************************************
 * PMIC
 * Caller please be sure to #include:
 *	drivers/misc/mediatek/include/mt-plat/upmu_common.h
 ******************************************************************************/
#if defined(CONFIG_ARCH_MT6735) || defined(CONFIG_ARCH_MT6735M) || defined(CONFIG_ARCH_MT6753) || \
	defined(CONFIG_ARCH_MT6755) || \
	defined(CONFIG_ARCH_MT6757) || \
	defined(CONFIG_ARCH_MT6797) || \
	defined(CONFIG_ARCH_MT6570) || \
	defined(CONFIG_ARCH_MT6580)
#define CONNADP_HAS_PMIC_API
#define KERNEL_pmic_config_interface connectivity_export_pmic_config_interface
#define KERNEL_pmic_read_interface connectivity_export_pmic_read_interface
#define KERNEL_pmic_set_register_value connectivity_export_pmic_set_register_value
void connectivity_export_pmic_config_interface(unsigned int RegNum, unsigned int val,
						unsigned int MASK, unsigned int SHIFT);
void connectivity_export_pmic_read_interface(unsigned int RegNum, unsigned int *val,
						unsigned int MASK, unsigned int SHIFT);
void connectivity_export_pmic_set_register_value(/*PMU_FLAGS_LIST_ENUM*/ int flagname, unsigned int val);
#endif
#if defined(CONFIG_ARCH_MT8127) || \
	defined(CONFIG_ARCH_MT8163)
#define CONNADP_HAS_UPMU_VCN_CTRL
#define KERNEL_upmu_set_vcn_1v8_lp_mode_set connectivity_export_upmu_set_vcn_1v8_lp_mode_set
#define KERNEL_upmu_set_vcn28_on_ctrl connectivity_export_upmu_set_vcn28_on_ctrl
#define KERNEL_upmu_set_vcn33_on_ctrl_bt connectivity_export_upmu_set_vcn33_on_ctrl_bt
#define KERNEL_upmu_set_vcn33_on_ctrl_wifi connectivity_export_upmu_set_vcn33_on_ctrl_wifi
void connectivity_export_upmu_set_vcn_1v8_lp_mode_set(unsigned int val);
void connectivity_export_upmu_set_vcn28_on_ctrl(unsigned int val);
void connectivity_export_upmu_set_vcn33_on_ctrl_bt(unsigned int val);
void connectivity_export_upmu_set_vcn33_on_ctrl_wifi(unsigned int val);
#endif

/*******************************************************************************
 * MMC
 * Caller please be sure to #include:
 *	<linux/mmc/host.h>
 *	<linux/mmc/card.h>
 *	drivers/mmc/core/sdio_ops.h
 ******************************************************************************/
#define KERNEL_mmc_io_rw_direct connectivity_export_mmc_io_rw_direct
struct mmc_card;
int connectivity_export_mmc_io_rw_direct(struct mmc_card *card, int write, unsigned fn,
						unsigned addr, u8 in, u8 *out);

/*******************************************************************************
 * Watchdog
 ******************************************************************************/
#if defined(CONFIG_ARCH_MT6797)
#define KERNEL_mtk_wdt_swsysret_config connectivity_export_mtk_wdt_swsysret_config
int connectivity_export_mtk_wdt_swsysret_config(int bit, int set_value);
extern int mtk_wdt_swsysret_config(int bit, int set_value);
#endif


#ifdef CONFIG_ARCH_MT6755
#define CPU_BOOST y
#endif
#ifdef CONFIG_ARCH_MT6757
#define CPU_BOOST y
#endif
#ifdef CONFIG_ARCH_MT6797
#define CPU_BOOST y
#endif

#ifdef CPU_BOOST
#include "mach/mt_ppm_api.h"
#endif

#define KERNEL_show_stack connectivity_export_show_stack
#define KERNEL_tracing_record_cmdline connectivity_export_tracing_record_cmdline

#ifdef CPU_BOOST
#define KERNEL_mt_ppm_sysboost_freq connectivity_export_mt_ppm_sysboost_freq
#define KERNEL_mt_ppm_sysboost_core connectivity_export_mt_ppm_sysboost_core
#define KERNEL_mt_ppm_sysboost_set_core_limit connectivity_export_mt_ppm_sysboost_set_core_limit
#else
#define KERNEL_mt_ppm_sysboost_freq
#define KERNEL_mt_ppm_sysboost_core
#define KERNEL_mt_ppm_sysboost_set_core_limit
#endif

void connectivity_export_show_stack(struct task_struct *tsk, unsigned long *sp);
void connectivity_export_tracing_record_cmdline(struct task_struct *tsk);
#ifdef CPU_BOOST
void __attribute__((weak)) mt_ppm_sysboost_freq(enum ppm_sysboost_user user, unsigned int freq);
void __attribute__((weak)) mt_ppm_sysboost_core(enum ppm_sysboost_user user, unsigned int core_num);
void __attribute__((weak)) mt_ppm_sysboost_set_core_limit(enum ppm_sysboost_user user, unsigned int cluster,
					int min_core, int max_core);
void connectivity_export_mt_ppm_sysboost_freq(enum ppm_sysboost_user user, unsigned int freq);
void connectivity_export_mt_ppm_sysboost_core(enum ppm_sysboost_user user, unsigned int core_num);
void connectivity_export_mt_ppm_sysboost_set_core_limit(enum ppm_sysboost_user user, unsigned int cluster,
					int min_core, int max_core);
#endif

extern void tracing_record_cmdline(struct task_struct *tsk);
extern void show_stack(struct task_struct *tsk, unsigned long *sp);

/*********************************************
 * for CPU unified APIs
 *********************************************/
#if defined(CONFIG_ARCH_MT6735) || defined(CONFIG_ARCH_MT6735M) || defined(CONFIG_ARCH_MT6753)
#include <mach/mt_lbc.h>
void connectivity_update_userlimit_cpu_freq(int kicker,
						int num_cluster, struct ppm_limit_data *freq_limit);
void connectivity_update_userlimit_cpu_core(int kicker,
						int num_cluster, struct ppm_limit_data *core_limit);

extern int
update_userlimit_cpu_freq(int kicker, int num_cluster, struct ppm_limit_data *freq_limit);
extern int
update_userlimit_cpu_core(int kicker, int num_cluster, struct ppm_limit_data *core_limit);
#endif

/*********************************************
 * copy from
 * kernel-3.18/include/linux/ftrace_event.h
 * kernel-4.4/include/linux/trace_events.h
 *
 * event_trace_printk()
 *********************************************/

#define KERNEL_event_trace_printk(ip, fmt, args...)               \
do {                                                              \
	__trace_printk_check_format(fmt, ##args);                 \
	KERNEL_tracing_record_cmdline(current);                   \
	if (__builtin_constant_p(fmt)) {                          \
		static const char *trace_printk_fmt               \
		__attribute__((section("__trace_printk_fmt"))) =  \
		__builtin_constant_p(fmt) ? fmt : NULL;           \
		__trace_bprintk(ip, trace_printk_fmt, ##args);    \
	} else                                                    \
		__trace_printk(ip, fmt, ##args);                  \
} while (0)

#endif /* CONNECTIVITY_BUILD_IN_ADAPTER_H */

