/*
 * Copyright (C) 2017 MediaTek Inc.
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

#include <mtk_dcm_internal.h>

#ifdef __KERNEL__
#include <linux/init.h>
#include <linux/export.h>
#include <linux/module.h>
#include <linux/kernel.h>
#include <linux/of.h>
#include <linux/of_address.h>
#include <linux/cpumask.h>
#include <linux/cpu.h>
#include <mt-plat/mtk_io.h>
#include <mt-plat/sync_write.h>
#include <mt-plat/mtk_secure_api.h>
#else /* ! __KERNEL__ */
#ifdef __CTP_DCM__ /* CTP */
#include <sync_write.h>
#include <common.h>
#include <mmu.h>
#include <sizes.h>
#else /* LK */
#include <debug.h>
#include <stdlib.h>
#include <string.h>
#include <arch/arm.h>
#include <arch/arm/mmu.h>
#include <arch/ops.h>
#include <target/board.h>
#include <platform/mt_reg_base.h>
#include <platform/mt_typedefs.h>
#include <platform/sync_write.h>
#endif /* #ifdef __CTP_DCM__ */
#endif /* #ifdef __KERNEL__ */

#include <mtk_dcm_autogen.h>

/* #define CTRL_BIGCORE_DCM_IN_KERNEL */

#if defined(__KERNEL__) && defined(CONFIG_OF)
/* CT: TODO Check all necessary reg base. */
unsigned long dcm_infracfg_ao_base;
unsigned long dcm_pericfg_reg_base;
unsigned long dcm_mcucfg_base;
unsigned long dcm_mcucfg_phys_base; /* CT: TODO Check what is this. */
unsigned long dcm_dramc0_ao_base;
unsigned long dcm_dramc1_ao_base;
unsigned long dcm_ddrphy0ao_base;
unsigned long dcm_ddrphy1ao_base;
unsigned long dcm_chn_emi_reg0_base;
unsigned long dcm_chn_emi_reg1_base;
unsigned long dcm_emi_base;
unsigned long dcm_venc_base;
unsigned long dcm_ca15m_config_base;

/* CT: TODO Check node names in dts. */
#define INFRACFG_AO_NODE "mediatek,infracfg_ao"
#define PERICFG_NODE "mediatek,pericfg"
#define MCUCFG_NODE "mediatek,mcucfg"
#ifndef USE_DRAM_API_INSTEAD
#define DRAMC_AO_NODE "mediatek,dramc"
#endif
#define CHN0_EMI_NODE "mediatek,chn0_emi"
#define CHN1_EMI_NODE "mediatek,chn1_emi"
#define EMI_NODE "mediatek,emi"
#define MP2_CA15M_NODE "mediatek,mp2_ca15m_config"
#endif /* #if defined(__KERNEL__) && defined(CONFIG_OF) */


#define reg_read(addr)	 __raw_readl(IOMEM(addr))
#define reg_write(addr, val)   mt_reg_sync_writel((val), ((void *)addr))

#ifdef __KERNEL__ /* for KERNEL */
#if defined(CONFIG_ARM_PSCI) || defined(CONFIG_MTK_PSCI)
#define MCUSYS_SMC_WRITE(addr, val)  mcusys_smc_write_phy(addr##_PHYS, val)
#ifndef mcsi_reg_read
#define mcsi_reg_read(offset) \
	mt_secure_call(MTK_SIP_KERNEL_MCSI_NS_ACCESS, 0, offset, 0)
#endif
#ifndef mcsi_reg_write
#define mcsi_reg_write(val, offset) \
	mt_secure_call(MTK_SIP_KERNEL_MCSI_NS_ACCESS, 1, offset, val)
#endif
#define MCSI_SMC_WRITE(addr, val)  mcsi_reg_write(val, (addr##_PHYS & 0xFFFF))
#define MCSI_SMC_READ(addr)  mcsi_reg_read(addr##_PHYS & 0xFFFF)
#else
#define MCUSYS_SMC_WRITE(addr, val)  mcusys_smc_write(addr, val)
#define MCSI_SMC_WRITE(addr, val)  reg_write(addr, val)
#define MCSI_SMC_READ(addr)  reg_read(addr)
#endif
#define dcm_smc_msg_send(msg) dcm_smc_msg(msg)
#else /* !__KERNEL__, for CTP */
#ifdef __CTP_DCM__ /* CTP */
#define MCUSYS_SMC_WRITE(addr, val)  reg_write(addr, val)
#define MCSI_SMC_WRITE(addr, val)  reg_write(addr, val)
#define MCSI_SMC_READ(addr)  reg_read(addr)
#define dcm_smc_msg_send(msg)
#else /* __LK__ */
#define MCUSYS_SMC_WRITE(addr, val)  reg_write(addr, val)
#define MCSI_SMC_WRITE(addr, val)  mcsi_reg_write(val, (addr##_PHYS & 0xFFFF))
#define MCSI_SMC_READ(addr)  mcsi_reg_read(addr##_PHYS & 0xFFFF)
#define dcm_smc_msg_send(msg) dcm_smc_msg(msg)
#endif /* #ifdef __CTP_DCM__ */
#endif /* #ifdef __KERNEL__ */

#ifdef __KERNEL__
#define TAG	"[Power/dcm] "
#define dcm_err(fmt, args...)	pr_info(TAG fmt, ##args)
#define dcm_warn(fmt, args...)	pr_info(TAG fmt, ##args)
#define dcm_info(fmt, args...)	pr_notice(TAG fmt, ##args)
#define dcm_dbg(fmt, args...)				\
	do {						\
		if (dcm_debug)				\
			pr_info(TAG fmt, ##args);	\
	} while (0)
#define dcm_ver(fmt, args...)	pr_debug(TAG fmt, ##args)
#else /* !__KERNEL__: CTP/LK */

#endif

#ifdef __KERNEL__
#define REG_DUMP(addr) dcm_info("%-30s(0x%08lx): 0x%08x\n", #addr, addr, reg_read(addr))
#define SECURE_REG_DUMP(addr) dcm_info("%-30s(0x%08lx): 0x%08x\n", #addr, addr, mcsi_reg_read(addr##_PHYS & 0xFFFF))
#else
#ifdef __CTP_DCM__ /* CTP */
#define REG_DUMP(addr) dcm_info("%s(0x%X): 0x%X\n", #addr, addr, reg_read(addr))
#define SECURE_REG_DUMP(addr) dcm_info("%s(0x%X): 0x%X\n", #addr, addr, reg_read(addr))
#else /* __LK__ */
#define REG_DUMP(addr) dcm_info("%-30s(0x%08x): 0x%08x\n", #addr, addr, reg_read(addr))
#define SECURE_REG_DUMP(addr) dcm_info("%-30s(0x%08x): 0x%08lx\n", #addr, addr, mcsi_reg_read(addr##_PHYS & 0xFFFF))
#endif /* #ifdef __CTP_DCM__ */
#endif

/** macro **/
#define and(v, a) ((v) & (a))
#define or(v, o) ((v) | (o))
#define aor(v, a, o) (((v) & (a)) | (o))

/** global **/
static short dcm_cpu_cluster_stat;

unsigned int all_dcm_type = (ARMCORE_DCM_TYPE | MCUSYS_DCM_TYPE
				    | STALL_DCM_TYPE | BIG_CORE_DCM_TYPE
				    | GIC_SYNC_DCM_TYPE | RGU_DCM_TYPE
				    | INFRA_DCM_TYPE | PERI_DCM_TYPE
				    | DDRPHY_DCM_TYPE | EMI_DCM_TYPE | DRAMC_DCM_TYPE
				    );
unsigned int init_dcm_type = (ARMCORE_DCM_TYPE | MCUSYS_DCM_TYPE
				    | STALL_DCM_TYPE | BIG_CORE_DCM_TYPE
				    | GIC_SYNC_DCM_TYPE | RGU_DCM_TYPE
				    | INFRA_DCM_TYPE | PERI_DCM_TYPE
				    );

#ifdef __KERNEL__
#ifdef CONFIG_HOTPLUG_CPU
static struct notifier_block dcm_hotplug_nb;
#endif
#endif

/*****************************************
 * following is implementation per DCM module.
 * 1. per-DCM function is 1-argu with ON/OFF/MODE option.
 *****************************************/
typedef int (*DCM_FUNC)(int);
typedef void (*DCM_PRESET_FUNC)(void);

int dcm_topckg(int on)
{
	return 0;
}

/* CT: TODO Check if this one is needed. */
void dcm_infracfg_ao_emi_indiv(int on)
{
}

int dcm_armcore(int mode)
{
	dcm_mcu_misccfg_bus_arm_pll_divider_dcm(mode);
	dcm_mcu_misccfg_mp0_arm_pll_divider_dcm(mode);
	dcm_mcu_misccfg_mp2_arm_pll_divider_dcm(mode); /* CT: TODO Check if necessary. */

	return 0;
}

int dcm_infra_preset(void)
{
	dcm_mcu_misc1cfg_mcsib_dcm_preset(DCM_ON);
	dcm_mcu_misc1cfg_mcsib_dcm(DCM_ON);
	dcm_peri(DCM_ON);

	return 0;
}

int dcm_infra(int on)
{
	dcm_infracfg_ao_infrabus(on);
	dcm_infracfg_ao_infrabus1(on);
	dcm_infracfg_ao_mdbus(on);
	dcm_infracfg_ao_infra_emi(on);
	dcm_infracfg_ao_qaxibus(on);
	dcm_infracfg_ao_mts(on);
	dcm_infracfg_ao_top_emi(on); /* CT: TODO Check if necessary. */

	return 0;
}

int dcm_peri(int on)
{
	/* dcm_pericfg_reg(on); *//* Not exist */
	dcm_pericfg_emibiu(on); /* CT: TODO Check if necessary. */
	dcm_pericfg_emibus(on); /* CT: TODO Check if necessary. */
	dcm_pericfg_regbiu(on); /* CT: TODO Check if necessary. */
	dcm_pericfg_regbus(on); /* CT: TODO Check if necessary. */

	return 0;
}

int dcm_mcusys(int on)
{
	dcm_mcu_misccfg_adb400_dcm(on);
	dcm_mcu_misccfg_bus_sync_dcm(on);
	dcm_mcu_misccfg_bus_clock_dcm(on);
	dcm_mcu_misccfg_bus_fabric_dcm(on);
	dcm_mcu_misccfg_l2_shared_dcm(on);
	dcm_mcu_misccfg_mp0_sync_dcm_enable(on);
	/* dcm_mcu_misccfg_mp1_sync_dcm_enable(on); *//* Not exist */
	dcm_mcu_misccfg_mcu_misc_dcm(on);
	/* dcm_mcu_misc1cfg_mcsia_dcm(on); *//* Not exist */
	dcm_mcu_misc1cfg_mcsib_dcm(on); /* CT: TODO Check if necessary. */

	return 0;
}

int dcm_mcusys_preset(void)
{
	dcm_mcu_misc1cfg_mcsib_dcm_preset(DCM_ON);

	return 0;

}

int dcm_big_core_preset(void)
{
	return 0;
}

int dcm_big_core(int on)
{
#ifdef CTRL_BIGCORE_DCM_IN_KERNEL
	/* only can be accessed if B cluster power on */
	if (dcm_cpu_cluster_stat & DCM_CPU_CLUSTER_B)
		dcm_mp2_ca15m_config_sync_dcm_cfg(on); /* CT: TODO Check if necessary. */
#endif
	return 0;
}

int dcm_stall_preset(void)
{
    /* CT: Check if DCM_ON ok for not. */
	dcm_mcu_misccfg_mp_stall_dcm(DCM_ON);

	return 0;
}

int dcm_stall(int on)
{
	dcm_mcu_misccfg_mp0_stall_dcm(on);

	return 0;
}

int dcm_dramc_ao(int on)
{
	dcm_dramc0_ao_dramc_dcm(on);
	dcm_dramc1_ao_dramc_dcm(on);

	return 0;
}

int dcm_ddrphy(int on)
{
	dcm_ddrphy0ao_ddrphy(on);
	dcm_ddrphy1ao_ddrphy(on);

	return 0;
}

int dcm_emi(int on)
{
	dcm_emi_dcm_emi_group(on);
	dcm_chn0_emi_dcm_emi_group(on);
	dcm_chn1_emi_dcm_emi_group(on);

	return 0;
}

int dcm_gic_sync(int on)
{
	dcm_mcu_misccfg_gic_sync_dcm(on);

	return 0;
}

int dcm_last_core(int on)
{
	return 0;
}

int dcm_rgu(int on)
{
	dcm_mp0_cpucfg_mp0_rgu_dcm(on);
	/* dcm_mp1_cpucfg_mp1_rgu_dcm(on); *//* Not exist */

	return 0;
}

int dcm_lpdma(int on)
{
	return 0;
}

int dcm_mcsi_preset(void)
{
	dcm_mcu_misc1cfg_mcsib_dcm_preset(DCM_ON);

	return 0;

}

int dcm_mcsi(int on)
{
	dcm_mcu_misc1cfg_mcsib_dcm(on);

	return 0;
}

/*****************************************************/
#if 0
typedef int (*DCM_FUNC)(int);
typedef void (*DCM_PRESET_FUNC)(void);

struct DCM {
	int current_state;
	int saved_state;
	int disable_refcnt;
	int default_state;
	DCM_FUNC func;
	DCM_PRESET_FUNC preset_func;
	int typeid;
	char *name;
};
#endif

struct DCM dcm_array[NR_DCM_TYPE] = {
	{
	 .typeid = ARMCORE_DCM_TYPE,
	 .name = "ARMCORE_DCM",
	 .func = (DCM_FUNC) dcm_armcore,
	 .current_state = ARMCORE_DCM_MODE1,
	 .default_state = ARMCORE_DCM_MODE1,
	 .disable_refcnt = 0,
	 },
	{
	 .typeid = MCUSYS_DCM_TYPE,
	 .name = "MCUSYS_DCM",
	 .func = (DCM_FUNC) dcm_mcusys,
	 .preset_func = (DCM_PRESET_FUNC) dcm_mcusys_preset,
	 .current_state = MCUSYS_DCM_ON,
	 .default_state = MCUSYS_DCM_ON,
	 .disable_refcnt = 0,
	 },
	{
	 .typeid = INFRA_DCM_TYPE,
	 .name = "INFRA_DCM",
	 .func = (DCM_FUNC) dcm_infra,
	 .preset_func = (DCM_PRESET_FUNC) dcm_infra_preset,
	 .current_state = INFRA_DCM_ON,
	 .default_state = INFRA_DCM_ON,
	 .disable_refcnt = 0,
	 },
	{
	 .typeid = PERI_DCM_TYPE,
	 .name = "PERI_DCM",
	 .func = (DCM_FUNC) dcm_peri,
	 /*.preset_func = (DCM_PRESET_FUNC) dcm_peri_preset,*/
	 .current_state = PERI_DCM_ON,
	 .default_state = PERI_DCM_ON,
	 .disable_refcnt = 0,
	 },
	{
	 .typeid = EMI_DCM_TYPE,
	 .name = "EMI_DCM",
	 .func = (DCM_FUNC) dcm_emi,
	 .current_state = EMI_DCM_ON,
	 .default_state = EMI_DCM_ON,
	 .disable_refcnt = 0,
	 },
	{
	 .typeid = DRAMC_DCM_TYPE,
	 .name = "DRAMC_DCM",
	 .func = (DCM_FUNC) dcm_dramc_ao,
	 .current_state = DRAMC_AO_DCM_ON,
	 .default_state = DRAMC_AO_DCM_ON,
	 .disable_refcnt = 0,
	 },
	{
	 .typeid = DDRPHY_DCM_TYPE,
	 .name = "DDRPHY_DCM",
	 .func = (DCM_FUNC) dcm_ddrphy,
	 .current_state = DDRPHY_DCM_ON,
	 .default_state = DDRPHY_DCM_ON,
	 .disable_refcnt = 0,
	 },
	{
	 .typeid = STALL_DCM_TYPE,
	 .name = "STALL_DCM",
	 .func = (DCM_FUNC) dcm_stall,
	 .preset_func = (DCM_PRESET_FUNC) dcm_stall_preset, /* CT: TODO Check what is this for. */
	 .current_state = STALL_DCM_ON,
	 .default_state = STALL_DCM_ON,
	 .disable_refcnt = 0,
	 },
	{
	 .typeid = BIG_CORE_DCM_TYPE,
	 .name = "BIG_CORE_DCM",
	 .func = (DCM_FUNC) dcm_big_core,
	 .current_state = BIG_CORE_DCM_ON,
	 .default_state = BIG_CORE_DCM_ON,
	 .disable_refcnt = 0,
	 },
	{
	 .typeid = GIC_SYNC_DCM_TYPE,
	 .name = "GIC_SYNC_DCM",
	 .func = (DCM_FUNC) dcm_gic_sync,
	 .current_state = GIC_SYNC_DCM_ON,
	 .default_state = GIC_SYNC_DCM_ON,
	 .disable_refcnt = 0,
	 },
	{
	 .typeid = LAST_CORE_DCM_TYPE,
	 .name = "LAST_CORE_DCM",
	 .func = (DCM_FUNC) dcm_last_core,
	 .current_state = LAST_CORE_DCM_ON,
	 .default_state = LAST_CORE_DCM_ON,
	 .disable_refcnt = 0,
	 },
	{
	 .typeid = RGU_DCM_TYPE,
	 .name = "RGU_DCM", /* CT: TODO Check where is this defined, and why "RGU_CORE_DCM" is used in MT6758. */
	 .func = (DCM_FUNC) dcm_rgu,
	 .current_state = RGU_DCM_ON,
	 .default_state = RGU_DCM_ON,
	 .disable_refcnt = 0,
	 },
	{
	 .typeid = TOPCKG_DCM_TYPE,
	 .name = "TOPCKG_DCM",
	 .func = (DCM_FUNC) dcm_topckg,
	 .current_state = TOPCKG_DCM_ON,
	 .default_state = TOPCKG_DCM_ON,
	 .disable_refcnt = 0,
	 },
	{
	 .typeid = LPDMA_DCM_TYPE,
	 .name = "LPDMA_DCM",
	 .func = (DCM_FUNC) dcm_lpdma,
	 .current_state = LPDMA_DCM_ON,
	 .default_state = LPDMA_DCM_ON,
	 .disable_refcnt = 0,
	 },
	 {
	 .typeid = MCSI_DCM_TYPE,
	 .name = "MCSI_DCM",
	 .func = (DCM_FUNC) dcm_mcsi,
	 .preset_func = (DCM_PRESET_FUNC) dcm_mcsi_preset,
	 .current_state = MCSI_DCM_ON,
	 .default_state = MCSI_DCM_ON,
	 .disable_refcnt = 0,
	 },
};

/*****************************************
 * DCM driver will provide regular APIs :
 * 1. dcm_restore(type) to recovery CURRENT_STATE before any power-off reset.
 * 2. dcm_set_default(type) to reset as cold-power-on init state.
 * 3. dcm_disable(type) to disable all dcm.
 * 4. dcm_set_state(type) to set dcm state.
 * 5. dcm_dump_state(type) to show CURRENT_STATE.
 * 6. /sys/power/dcm_state interface:  'restore', 'disable', 'dump', 'set'. 4 commands.
 *
 * spsecified APIs for workaround:
 * 1. (definitely no workaround now)
 *****************************************/
#if 0
void dcm_set_default(unsigned int type)
{
	int i;
	struct DCM *dcm;

#ifndef ENABLE_DCM_IN_LK
#ifdef __KERNEL__
	dcm_info("[%s]type:0x%08x, init_dcm_type=0x%x\n", __func__, type, init_dcm_type);
#else
	dcm_info("[%s]type:0x%X, init_dcm_type=0x%X\n", __func__, type, init_dcm_type);
#endif
#else
#ifdef __KERNEL__
	dcm_info("[%s]type:0x%08x, init_dcm_type=0x%x, INIT_DCM_TYPE_BY_K=0x%x\n",
		 __func__, type, init_dcm_type, INIT_DCM_TYPE_BY_K);
#else
	dcm_info("[%s]type:0x%X, init_dcm_type=0x%X, INIT_DCM_TYPE_BY_K=0x%X\n",
		 __func__, type, init_dcm_type, INIT_DCM_TYPE_BY_K);
#endif
#endif

	mutex_lock(&dcm_lock);

	for (i = 0, dcm = &dcm_array[0]; i < NR_DCM_TYPE; i++, dcm++) {
		if (type & dcm->typeid) {
			dcm->saved_state = dcm->default_state;
			dcm->current_state = dcm->default_state;
			dcm->disable_refcnt = 0;
#ifdef ENABLE_DCM_IN_LK
			if (INIT_DCM_TYPE_BY_K & dcm->typeid) {
#endif
				if (dcm->preset_func)
					dcm->preset_func();
				dcm->func(dcm->current_state);
#ifdef ENABLE_DCM_IN_LK
			}
#endif

#ifdef __KERNEL__
			dcm_info("[%16s 0x%08x] current state:%d (%d)\n",
				 dcm->name, dcm->typeid, dcm->current_state,
				 dcm->disable_refcnt);
#else
			dcm_info("[%s 0x%X] current state:%d (%d)\n",
				 dcm->name, dcm->typeid, dcm->current_state,
				 dcm->disable_refcnt);
#endif
		}
	}

	dcm_smc_msg_send(init_dcm_type);

	mutex_unlock(&dcm_lock);
}

void dcm_set_state(unsigned int type, int state)
{
	int i;
	struct DCM *dcm;
	unsigned int init_dcm_type_pre = init_dcm_type;

#ifdef __KERNEL__
	dcm_info("[%s]type:0x%08x, set:%d, init_dcm_type_pre=0x%x\n",
		 __func__, type, state, init_dcm_type_pre);
#else
	dcm_info("[%s]type:0x%X, set:%d, init_dcm_type_pre=0x%X\n",
		 __func__, type, state, init_dcm_type_pre);
#endif

	mutex_lock(&dcm_lock);

	for (i = 0, dcm = &dcm_array[0]; type && (i < NR_DCM_TYPE); i++, dcm++) {
		if (type & dcm->typeid) {
			type &= ~(dcm->typeid);

			dcm->saved_state = state;
			if (dcm->disable_refcnt == 0) {
				if (state)
					init_dcm_type |= dcm->typeid;
				else
					init_dcm_type &= ~(dcm->typeid);

				dcm->current_state = state;
				dcm->func(dcm->current_state);
			}

#ifdef __KERNEL__
			dcm_info("[%16s 0x%08x] current state:%d (%d)\n",
				 dcm->name, dcm->typeid, dcm->current_state,
				 dcm->disable_refcnt);
#else
			dcm_info("[%s 0x%X] current state:%d (%d)\n",
				 dcm->name, dcm->typeid, dcm->current_state,
				 dcm->disable_refcnt);
#endif

		}
	}

	if (init_dcm_type_pre != init_dcm_type) {
#ifdef __KERNEL__
		dcm_info("[%s]type:0x%08x, set:%d, init_dcm_type=0x%x->0x%x\n",
			 __func__, type, state, init_dcm_type_pre, init_dcm_type);
#else
		dcm_info("[%s]type:0x%X, set:%d, init_dcm_type=0x%X->0x%X\n",
			 __func__, type, state, init_dcm_type_pre, init_dcm_type);
#endif
		dcm_smc_msg_send(init_dcm_type);
	}

	mutex_unlock(&dcm_lock);
}


void dcm_disable(unsigned int type)
{
	int i;
	struct DCM *dcm;
	unsigned int init_dcm_type_pre = init_dcm_type;

#ifdef __KERNEL__
	dcm_info("[%s]type:0x%08x\n", __func__, type);
#else
	dcm_info("[%s]type:0x%X\n", __func__, type);
#endif

	mutex_lock(&dcm_lock);

	for (i = 0, dcm = &dcm_array[0]; type && (i < NR_DCM_TYPE); i++, dcm++) {
		if (type & dcm->typeid) {
			type &= ~(dcm->typeid);

			dcm->current_state = DCM_OFF;
			if (dcm->disable_refcnt++ == 0)
				init_dcm_type &= ~(dcm->typeid);
			dcm->func(dcm->current_state);

#ifdef __KERNEL__
			dcm_info("[%16s 0x%08x] current state:%d (%d)\n",
				 dcm->name, dcm->typeid, dcm->current_state,
				 dcm->disable_refcnt);
#else
			dcm_info("[%s 0x%X] current state:%d (%d)\n",
				 dcm->name, dcm->typeid, dcm->current_state,
				 dcm->disable_refcnt);
#endif

		}
	}

	if (init_dcm_type_pre != init_dcm_type) {
#ifdef __KERNEL__
		dcm_info("[%s]type:0x%08x, init_dcm_type=0x%x->0x%x\n",
			 __func__, type, init_dcm_type_pre, init_dcm_type);
#else
		dcm_info("[%s]type:0x%X, init_dcm_type=0x%X->0x%X\n",
			 __func__, type, init_dcm_type_pre, init_dcm_type);
#endif
		dcm_smc_msg_send(init_dcm_type);
	}

	mutex_unlock(&dcm_lock);

}

void dcm_restore(unsigned int type)
{
	int i;
	struct DCM *dcm;
	unsigned int init_dcm_type_pre = init_dcm_type;

#ifdef __KERNEL__
	dcm_info("[%s]type:0x%08x\n", __func__, type);
#else
	dcm_info("[%s]type:0x%X\n", __func__, type);
#endif

	mutex_lock(&dcm_lock);

	for (i = 0, dcm = &dcm_array[0]; type && (i < NR_DCM_TYPE); i++, dcm++) {
		if (type & dcm->typeid) {
			type &= ~(dcm->typeid);

			if (dcm->disable_refcnt > 0)
				dcm->disable_refcnt--;
			if (dcm->disable_refcnt == 0) {
				if (dcm->saved_state)
					init_dcm_type |= dcm->typeid;
				else
					init_dcm_type &= ~(dcm->typeid);

				dcm->current_state = dcm->saved_state;
				dcm->func(dcm->current_state);
			}

#ifdef __KERNEL__
			dcm_info("[%16s 0x%08x] current state:%d (%d)\n",
				 dcm->name, dcm->typeid, dcm->current_state,
				 dcm->disable_refcnt);
#else
			dcm_info("[%s 0x%X] current state:%d (%d)\n",
				 dcm->name, dcm->typeid, dcm->current_state,
				 dcm->disable_refcnt);
#endif

		}
	}

	if (init_dcm_type_pre != init_dcm_type) {
#ifdef __KERNEL__
		dcm_info("[%s]type:0x%08x, init_dcm_type=0x%x->0x%x\n",
			 __func__, type, init_dcm_type_pre, init_dcm_type);
#else
		dcm_info("[%s]type:0x%X, init_dcm_type=0x%X->0x%X\n",
			 __func__, type, init_dcm_type_pre, init_dcm_type);
#endif
		dcm_smc_msg_send(init_dcm_type);
	}

	mutex_unlock(&dcm_lock);
}


void dcm_dump_state(int type)
{
	int i;
	struct DCM *dcm;

	dcm_info("\n******** dcm dump state *********\n");
	for (i = 0, dcm = &dcm_array[0]; i < NR_DCM_TYPE; i++, dcm++) {
		if (type & dcm->typeid) {
#ifdef __KERNEL__
			dcm_info("[%-16s 0x%08x] current state:%d (%d)\n",
				 dcm->name, dcm->typeid, dcm->current_state,
				 dcm->disable_refcnt);
#else
			dcm_info("[%s 0x%X] current state:%d (%d)\n",
				 dcm->name, dcm->typeid, dcm->current_state,
				 dcm->disable_refcnt);
#endif
		}
	}
}
#endif

void dcm_dump_regs(void)
{
	dcm_info("\n******** dcm dump register *********\n");
	REG_DUMP(MCSIA_DCM_EN);
	REG_DUMP(MP0_CPUSYS_RGU_SYNC_DCM);
	/* REG_DUMP(MP1_CPUCFG_MP1_RGU_DCM_CONFIG); */
	REG_DUMP(L2C_SRAM_CTRL);
	REG_DUMP(CCI_CLK_CTRL);
	REG_DUMP(BUS_FABRIC_DCM_CTRL);
	REG_DUMP(MCU_MISC_DCM_CTRL);
	REG_DUMP(CCI_ADB400_DCM_CONFIG);
	REG_DUMP(SYNC_DCM_CONFIG);
	REG_DUMP(SYNC_DCM_CLUSTER_CONFIG);
	REG_DUMP(MP_GIC_RGU_SYNC_DCM);
	REG_DUMP(MP0_PLL_DIVIDER_CFG);
	/* REG_DUMP(MP1_PLL_DIVIDER_CFG); */
	REG_DUMP(BUS_PLL_DIVIDER_CFG);
	REG_DUMP(EMI_CONM);
	REG_DUMP(EMI_CONN);
	REG_DUMP(DDRPHY0AO_MISC_CG_CTRL0);
	REG_DUMP(DDRPHY0AO_MISC_CG_CTRL2);
	REG_DUMP(DDRPHY0AO_MISC_CTRL3);
	REG_DUMP(DDRPHY1AO_MISC_CG_CTRL0);
	REG_DUMP(DDRPHY1AO_MISC_CG_CTRL2);
	REG_DUMP(DDRPHY1AO_MISC_CTRL3);
	REG_DUMP(CHN0_EMI_CHN_EMI_CONB);
	REG_DUMP(CHN1_EMI_CHN_EMI_CONB);
	REG_DUMP(DRAMC0_AO_DRAMC_PD_CTRL);
	REG_DUMP(DRAMC0_AO_CLKAR);
	REG_DUMP(DRAMC1_AO_DRAMC_PD_CTRL);
	REG_DUMP(DRAMC1_AO_CLKAR);
	/* REG_DUMP(PERICFG_DCM_EMI_EARLY_CTRL); */
	REG_DUMP(PERICFG_PERI_BIU_REG_DCM_CTRL);
	REG_DUMP(PERICFG_PERI_BIU_EMI_DCM_CTRL);
	REG_DUMP(INFRA_BUS_DCM_CTRL);
	REG_DUMP(INFRA_BUS_DCM_CTRL_1);
	REG_DUMP(INFRA_MDBUS_DCM_CTRL);
	REG_DUMP(INFRA_QAXIBUS_DCM_CTRL);
	/* REG_DUMP(INFRA_EMI_DCM_CTRL_1); */
	REG_DUMP(INFRA_EMI_DCM_CTRL);
	REG_DUMP(MEM_DCM_CTRL);
}

#if 0
#ifdef CONFIG_PM
static ssize_t dcm_state_show(struct kobject *kobj, struct kobj_attribute *attr,
				  char *buf)
{
	int len = 0;
	int i;
	struct DCM *dcm;

	/* dcm_dump_state(all_dcm_type); */
	len += snprintf(buf+len, PAGE_SIZE-len,
			"\n******** dcm dump state *********\n");
	for (i = 0, dcm = &dcm_array[0]; i < NR_DCM_TYPE; i++, dcm++)
		len += snprintf(buf+len, PAGE_SIZE-len,
				"[%-16s 0x%08x] current state:%d (%d), atf_on_cnt:%u\n",
				dcm->name, dcm->typeid, dcm->current_state,
				dcm->disable_refcnt, dcm_smc_read_cnt(dcm->typeid));

	len += snprintf(buf+len, PAGE_SIZE-len,
			"\n********** dcm_state help *********\n");
	len += snprintf(buf+len, PAGE_SIZE-len,
			"set:       echo set [mask] [mode] > /sys/power/dcm_state\n");
	len += snprintf(buf+len, PAGE_SIZE-len,
			"disable:   echo disable [mask] > /sys/power/dcm_state\n");
	len += snprintf(buf+len, PAGE_SIZE-len,
			"restore:   echo restore [mask] > /sys/power/dcm_state\n");
	len += snprintf(buf+len, PAGE_SIZE-len,
			"dump:      echo dump [mask] > /sys/power/dcm_state\n");
	len += snprintf(buf+len, PAGE_SIZE-len,
			"debug:     echo debug [0/1] > /sys/power/dcm_state\n");
	len += snprintf(buf+len, PAGE_SIZE-len,
			"***** [mask] is hexl bit mask of dcm;\n");
	len += snprintf(buf+len, PAGE_SIZE-len,
			"***** [mode] is type of DCM to set and retained\n");
	len += snprintf(buf+len, PAGE_SIZE-len,
			"init_dcm_type=0x%x, all_dcm_type=0x%x, dcm_debug=%d, dcm_cpu_cluster_stat=%d\n",
			init_dcm_type, all_dcm_type, dcm_debug, dcm_cpu_cluster_stat);

	return len;
}

static int dcm_convert_stall_wr_del_sel(unsigned int val)
{
	if (val < 0 || val > 0x1F)
		return 0;
	else
		return val;
}

int dcm_set_stall_wr_del_sel(unsigned int mp0, unsigned int mp1)
{
	mutex_lock(&dcm_lock);

	reg_write(SYNC_DCM_CLUSTER_CONFIG,
			aor(reg_read(SYNC_DCM_CLUSTER_CONFIG),
				~(MCUSYS_STALL_DCM_MP0_WR_DEL_SEL_MASK),
				(dcm_convert_stall_wr_del_sel(mp0) << 0)));
#if 0 /* Not exist */
	reg_write(SYNC_DCM_CLUSTER_CONFIG,
			aor(reg_read(SYNC_DCM_CLUSTER_CONFIG),
				~(MCUSYS_STALL_DCM_MP1_WR_DEL_SEL_MASK),
				(dcm_convert_stall_wr_del_sel(mp1) << 8)));
#endif
	mutex_unlock(&dcm_lock);

	return 0;
}

static ssize_t dcm_state_store(struct kobject *kobj,
				   struct kobj_attribute *attr, const char *buf,
				   size_t n)
{
	char cmd[16];
	unsigned int mask;
	unsigned int mp0, mp1;
	int ret, mode;

	if (sscanf(buf, "%15s %x", cmd, &mask) == 2) {
		mask &= all_dcm_type;

		if (!strcmp(cmd, "restore")) {
			/* dcm_dump_regs(); */
			dcm_restore(mask);
			/* dcm_dump_regs(); */
		} else if (!strcmp(cmd, "disable")) {
			/* dcm_dump_regs(); */
			dcm_disable(mask);
			/* dcm_dump_regs(); */
		} else if (!strcmp(cmd, "dump")) {
			dcm_dump_state(mask);
			dcm_dump_regs();
		} else if (!strcmp(cmd, "debug")) {
			if (mask == 0)
				dcm_debug = 0;
			else if (mask == 1)
				dcm_debug = 1;
			else if (mask == 2)
				dcm_infracfg_ao_emi_indiv(0);
			else if (mask == 3)
				dcm_infracfg_ao_emi_indiv(1);
		} else if (!strcmp(cmd, "set_stall_sel")) {
			if (sscanf(buf, "%15s %x %x", cmd, &mp0, &mp1) == 3)
				dcm_set_stall_wr_del_sel(mp0, mp1);
		} else if (!strcmp(cmd, "set")) {
			if (sscanf(buf, "%15s %x %d", cmd, &mask, &mode) == 3) {
				mask &= all_dcm_type;

				dcm_set_state(mask, mode);

				/* Log for stallDCM switching in Performance/Normal mode */
				if (mask & STALL_DCM_TYPE) {
					if (mode)
						dcm_info("stall dcm is enabled for Default(Normal) mode started\n");
					else
						dcm_info("stall dcm is disabled for Performance(Sports) mode started\n");
				}
			}
		} else {
			dcm_info("SORRY, do not support your command: %s\n", cmd);
		}
		ret = n;
	} else {
		dcm_info("SORRY, do not support your command.\n");
		ret = -EINVAL;
	}

	return ret;
}

static struct kobj_attribute dcm_state_attr = {
	.attr = {
		 .name = "dcm_state",
		 .mode = 0644,
		 },
	.show = dcm_state_show,
	.store = dcm_state_store,
};
#endif /* #ifdef CONFIG_PM */
#endif

#ifdef __KERNEL__
short is_dcm_bringup(void)
{
#ifdef DCM_BRINGUP
	dcm_pr_info("%s: skipped for bring up\n", __func__);
	return 1;
#else
	return 0;
#endif
}

#ifdef CONFIG_OF
/* CT: TODO Double check all the reg base addr. */
int mt_dcm_dts_map(void)
{
	struct device_node *node;
	/*struct resource r;*/

	/* infracfg_ao */
	node = of_find_compatible_node(NULL, NULL, INFRACFG_AO_NODE);
	if (!node) {
		dcm_err("error: cannot find node %s\n", INFRACFG_AO_NODE);
		return -1;
	}
	dcm_infracfg_ao_base = (unsigned long)of_iomap(node, 0);
	if (!dcm_infracfg_ao_base) {
		dcm_err("error: cannot iomap %s\n", INFRACFG_AO_NODE);
		return -1;
	}

	/* pericfg */
	node = of_find_compatible_node(NULL, NULL, PERICFG_NODE);
	if (!node) {
		dcm_err("error: cannot find node %s\n", PERICFG_NODE);
		return -1;
	}
	dcm_pericfg_reg_base = (unsigned long)of_iomap(node, 0);
	if (!dcm_pericfg_reg_base) {
		dcm_err("error: cannot iomap %s\n", PERICFG_NODE);
		return -1;
	}

	/* mcucfg */
	node = of_find_compatible_node(NULL, NULL, MCUCFG_NODE);
	if (!node) {
		dcm_err("error: cannot find node %s\n", MCUCFG_NODE);
		return -1;
	}
#if 0
	if (of_address_to_resource(node, 0, &r)) {
		dcm_err("error: cannot get phys addr %s\n", MCUCFG_NODE);
		return -1;
	}
	dcm_mcucfg_phys_base = r.start;
#endif
	dcm_mcucfg_base = (unsigned long)of_iomap(node, 0);
	if (!dcm_mcucfg_base) {
		dcm_err("error: cannot iomap %s\n", MCUCFG_NODE);
		return -1;
	}

#if 0
	/* cci: mcsi-b */
	node = of_find_compatible_node(NULL, NULL, CCI_NODE);
	if (!node) {
		dcm_err("error: cannot find node %s\n", CCI_NODE);
		return -1;
	}
	if (of_address_to_resource(node, 0, &r)) {
		dcm_err("error: cannot get phys addr %s\n", CCI_NODE);
		return -1;
	}
	dcm_cci_phys_base = r.start;
	dcm_cci_base = (unsigned long)of_iomap(node, 0);
	if (!dcm_cci_base) {
		dcm_err("error: cannot iomap %s\n", CCI_NODE);
		return -1;
	}
#endif

#ifdef CONFIG_MTK_DRAMC
	/* ddrphy0_ao */
	dcm_ddrphy0ao_base = (unsigned long)mt_ddrphy_chn_base_get(0);
	if (!dcm_ddrphy0ao_base) {
		dcm_err("error: cannot iomap %s\n", DRAMC_AO_NODE);
		return -1;
	}

	/* ddrphy1_ao */
	dcm_ddrphy1ao_base = (unsigned long)mt_ddrphy_chn_base_get(1);
	if (!dcm_ddrphy1ao_base) {
		dcm_err("error: cannot iomap %s\n", DRAMC_AO_NODE);
		return -1;
	}

	/* dramc0_ao */
	dcm_dramc0_ao_base = (unsigned long)mt_dramc_chn_base_get(0);
	if (!dcm_dramc0_ao_base) {
		dcm_err("error: cannot iomap %s\n",  DRAMC_AO_NODE);
		return -1;
	}

	/* dramc1_ao */
	dcm_dramc1_ao_base = (unsigned long)mt_dramc_chn_base_get(1);
	if (!dcm_dramc1_ao_base) {
		dcm_err("error: cannot iomap %s\n", DRAMC_AO_NODE);
		return -1;
	}
#else
	dcm_ddrphy0ao_base = 0x10330000;
	dcm_ddrphy1ao_base = 0x10338000;
#endif
#ifdef CONFIG_MTK_EMI
	dcm_chn_emi_reg0_base = (unsigned long)mt_chn_emi_base_get(0);
	if (!dcm_chn_emi_reg0_base) {
		dcm_err("error: cannot iomap %s\n", CHN0_EMI_NODE);
		return -1;
	}

	dcm_chn_emi_reg1_base = (unsigned long)mt_chn_emi_base_get(1);
	if (!dcm_chn_emi_reg1_base) {
		dcm_err("error: cannot iomap %s\n", CHN1_EMI_NODE);
		return -1;
	}

	/* emi */
	dcm_emi_base = (unsigned long)mt_cen_emi_base_get();
	if (!dcm_emi_base) {
		dcm_err("error: cannot iomap %s\n", EMI_NODE);
		return -1;
	}
#else
	dcm_emi_base = 0x10230000;
	dcm_chn_emi_reg0_base = 0x10335000;
	dcm_chn_emi_reg1_base = 0x1033d000;
#endif
	/* mp2_ca15m */
	node = of_find_compatible_node(NULL, NULL, MP2_CA15M_NODE);
	if (!node) {
		dcm_err("error: cannot find node %s\n", MP2_CA15M_NODE);
		return -1;
	}

	dcm_ca15m_config_base = (unsigned long)of_iomap(node, 0);
	if (!dcm_ca15m_config_base) {
		dcm_err("error: cannot iomap %s\n", MP2_CA15M_NODE);
		return -1;
	}

#if 0
	/* dramc0_ao */
	node = of_find_compatible_node(NULL, NULL, DRAMC_AO_NODE);
	if (!node) {
		dcm_err("error: cannot find node %s\n", DRAMC_AO_NODE);
		return -1;
	}
	dcm_dramc0_ao_base = (unsigned long)of_iomap(node, 0);
	if (!dcm_dramc0_ao_base) {
		dcm_err("error: cannot iomap %s\n", DRAMC_AO_NODE);
		return -1;
	}

	/* dramc1_ao */
	node = of_find_compatible_node(NULL, NULL, DRAMC_AO_NODE);
	if (!node) {
		dcm_err("error: cannot find node %s\n", DRAMC_AO_NODE);
		return -1;
	}
	dcm_dramc1_ao_base = (unsigned long)of_iomap(node, 0);
	if (!dcm_dramc1_ao_base) {
		dcm_err("error: cannot iomap %s\n", DRAMC_AO_NODE);
		return -1;
	}

	/* ddrphy0_ao */
	node = of_find_compatible_node(NULL, NULL, DRAMC_AO_NODE);
	if (!node) {
		dcm_err("error: cannot find node %s\n", DRAMC_AO_NODE);
		return -1;
	}
	dcm_ddrphy0_ao_base = (unsigned long)of_iomap(node, 0);
	if (!dcm_ddrphy0_ao_base) {
		dcm_err("error: cannot iomap %s\n", DRAMC_AO_NODE);
		return -1;
	}

	/* ddrphy1_ao */
	node = of_find_compatible_node(NULL, NULL, DRAMC_AO_NODE);
	if (!node) {
		dcm_err("error: cannot find node %s\n", DRAMC_AO_NODE);
		return -1;
	}
	dcm_ddrphy1_ao_base = (unsigned long)of_iomap(node, 0);
	if (!dcm_ddrphy1_ao_base) {
		dcm_err("error: cannot iomap %s\n", DRAMC_AO_NODE);
		return -1;
	}

	/* chn0_emi */
	node = of_find_compatible_node(NULL, NULL, CHN0_EMI_NODE);
	if (!node) {
		dcm_err("error: cannot find node %s\n", CHN0_EMI_NODE);
		return -1;
	}
	dcm_chn_emi_reg0_base = (unsigned long)of_iomap(node, 0);
	if (!dcm_chn_emi_reg0_base) {
		dcm_err("error: cannot iomap %s\n", CHN0_EMI_NODE);
		return -1;
	}

	/* chn1_emi */
	node = of_find_compatible_node(NULL, NULL, CHN1_EMI_NODE);
	if (!node) {
		dcm_err("error: cannot find node %s\n", CHN1_EMI_NODE);
		return -1;
	}
	dcm_chn_emi_reg1_base = (unsigned long)of_iomap(node, 0);
	if (!dcm_chn_emi_reg1_base) {
		dcm_err("error: cannot iomap %s\n", CHN1_EMI_NODE);
		return -1;
	}

	/* emi */
	node = of_find_compatible_node(NULL, NULL, EMI_NODE);
	if (!node) {
		dcm_err("error: cannot find node %s\n", EMI_NODE);
		return -1;
	}
	dcm_emi_base = (unsigned long)of_iomap(node, 0);
	if (!dcm_emi_base) {
		dcm_err("error: cannot iomap %s\n", EMI_NODE);
		return -1;
	}
#endif

	return 0;
}
#else
int mt_dcm_dts_map(void)
{
#if 1
	/* CT: TODO Check if all needs to remap. */
	remap_mem_range(EMI_BASE, EMI_BASE, SZ_256, MT_DEVICE);
	remap_mem_range(CHN0_EMI_BASE, CHN0_EMI_BASE, SZ_8K, MT_DEVICE);
	remap_mem_range(CHN1_EMI_BASE, CHN1_EMI_BASE, SZ_8K, MT_DEVICE);
	remap_mem_range(DDRPHY0AO_BASE, DDRPHY0AO_BASE, SZ_8K, MT_DEVICE);
	remap_mem_range(DDRPHY1AO_BASE, DDRPHY1AO_BASE, SZ_8K, MT_DEVICE);
	remap_mem_range(DRAMC0_AO_BASE, DRAMC0_AO_BASE, SZ_8K, MT_DEVICE);
	remap_mem_range(DRAMC1_AO_BASE, DRAMC1_AO_BASE, SZ_8K, MT_DEVICE);
	remap_mem_range(PERICFG_BASE, PERICFG_BASE, SZ_8K, MT_DEVICE);
	remap_mem_range(MP0_CPUCFG_BASE, MP0_CPUCFG_BASE, SZ_4K, MT_DEVICE);
	remap_mem_range(MP2_CA15M_CONFIG_BASE, MP2_CA15M_CONFIG_BASE, SZ_4K, MT_DEVICE);
	remap_mem_range(INFRACFG_AO_BASE, INFRACFG_AO_BASE, SZ_4K, MT_DEVICE);
	/* remap_mem_range(0x10A20000, 0x10A20000, SZ_8K, MT_DEVICE); *//* CT: TODO Check what it this. */
#if 0
	remap_mem_range(0x10a20000, 0x10a20000, SZ_8K, MT_DEVICE);
	remap_mem_range(0x10b00000, 0x10b00000, SZ_8K, MT_DEVICE);
	remap_mem_range(0x0c500000, 0x0c500000, SZ_8K, MT_DEVICE);
	remap_mem_range(0x10a00000, 0x10a00000, SZ_8K, MT_DEVICE);
	remap_mem_range(0x10270000, 0x10270000, SZ_8K, MT_DEVICE);
#endif
#endif
	return 0;
}
#endif /* #ifdef CONFIG_OF */
#endif

#ifdef CONFIG_HOTPLUG_CPU
int dcm_hotplug_nc(struct notifier_block *self,
					 unsigned long action, void *hcpu)
{
	unsigned int cpu = (long)hcpu;
	struct cpumask cpuhp_cpumask;
	struct cpumask cpu_online_cpumask;

	switch (action) {
	case CPU_ONLINE:
		arch_get_cluster_cpus(&cpuhp_cpumask, arch_get_cluster_id(cpu));
		cpumask_and(&cpu_online_cpumask, &cpuhp_cpumask, cpu_online_mask);
		if (cpumask_weight(&cpu_online_cpumask) == 1) {
			switch (cpu / 4) {
			case 0:
				dcm_dbg("%s: action=0x%lx, cpu=%u, LL CPU_ONLINE\n",
					__func__, action, cpu);
				dcm_cpu_cluster_stat |= DCM_CPU_CLUSTER_LL;
				break;
			case 1:
				dcm_dbg("%s: action=0x%lx, cpu=%u, L CPU_ONLINE\n",
					__func__, action, cpu);
				dcm_cpu_cluster_stat |= DCM_CPU_CLUSTER_L;
				break;
			case 2:
				dcm_dbg("%s: action=0x%lx, cpu=%u, B CPU_ONLINE\n",
					__func__, action, cpu);
				dcm_cpu_cluster_stat |= DCM_CPU_CLUSTER_B;
				break;
			default:
				break;
			}
		}
		break;
	case CPU_DOWN_PREPARE:
		arch_get_cluster_cpus(&cpuhp_cpumask, arch_get_cluster_id(cpu));
		cpumask_and(&cpu_online_cpumask, &cpuhp_cpumask, cpu_online_mask);
		if (cpumask_weight(&cpu_online_cpumask) == 1) {
			switch (cpu / 4) {
			case 0:
				dcm_dbg("%s: action=0x%lx, cpu=%u, LL CPU_DOWN_PREPARE\n",
					__func__, action, cpu);
				dcm_cpu_cluster_stat &= ~DCM_CPU_CLUSTER_LL;
				break;
			case 1:
				dcm_dbg("%s: action=0x%lx, cpu=%u, L CPU_DOWN_PREPARE\n",
					__func__, action, cpu);
				dcm_cpu_cluster_stat &= ~DCM_CPU_CLUSTER_L;
				break;
			case 2:
				dcm_dbg("%s: action=0x%lx, cpu=%u, B CPU_DOWN_PREPARE\n",
					__func__, action, cpu);
				dcm_cpu_cluster_stat &= ~DCM_CPU_CLUSTER_B;
				break;
			default:
				break;
			}
		}
		break;
	default:
		break;
	}

	return NOTIFY_OK;
}

void dcm_set_hotplug_nb(void)
{
#ifdef CONFIG_HOTPLUG_CPU
	dcm_hotplug_nb = (struct notifier_block) {
		.notifier_call	= dcm_hotplug_nc,
		.priority	= INT_MIN + 2, /* NOTE: make sure this is lower than CPU DVFS */
	};

	if (register_cpu_notifier(&dcm_hotplug_nb))
		dcm_err("[%s]: fail to register_cpu_notifier\n", __func__);
#endif /* #ifdef CONFIG_HOTPLUG_CPU */
}

#endif /* #ifdef CONFIG_HOTPLUG_CPU */

#if 0
int mt_dcm_init(void)
{
#ifdef DCM_BRINGUP
	dcm_info("%s: skipped for bring up\n", __func__);
	return 0;
#endif

	if (dcm_initiated)
		return 0;

#ifdef __KERNEL__
	if (mt_dcm_dts_map()) {
		dcm_err("%s: failed due to DTS failed\n", __func__);
		return -1;
	}
#endif

#if 0 /* WORKAROUND: Disable big core reg protection */
	reg_write(0x10202008, aor(reg_read(0x10202008), ~(0x3), 0x1));
	dcm_info("%s: 0x10202008=0x%x\n", __func__, reg_read(0x10202008));
#endif

#ifdef CTRL_BIGCORE_DCM_IN_KERNEL
    /* CT: TODO check what address here. */
	/* big ext buck iso power on */
	/* reg_write(0x10A00260, reg_read(0x10A00260) & ~(0x1 << 2)); */
	/* dcm_info("%s: 0x10A00260=0x%x\n", __func__, reg_read(0x10A00260)); */
	dcm_cpu_cluster_stat |= DCM_CPU_CLUSTER_B;
#endif

#ifndef DCM_DEFAULT_ALL_OFF
	/** enable all dcm **/
	dcm_set_default(init_dcm_type);
#else /* DCM_DEFAULT_ALL_OFF */
	dcm_set_state(all_dcm_type, DCM_OFF);
#endif /* #ifndef DCM_DEFAULT_ALL_OFF */

	dcm_dump_regs();

#ifdef CONFIG_PM
	{
		int err = 0;

		err = sysfs_create_file(power_kobj, &dcm_state_attr.attr);
		if (err)
			dcm_err("[%s]: fail to create sysfs\n", __func__);
	}

#ifdef DCM_DEBUG_MON
	{
		int err = 0;

		err = sysfs_create_file(power_kobj, &dcm_debug_mon_attr.attr);
		if (err)
			dcm_err("[%s]: fail to create sysfs\n", __func__);
	}
#endif /* #ifdef DCM_DEBUG_MON */
#endif /* #ifdef CONFIG_PM */

#ifdef CONFIG_HOTPLUG_CPU
	dcm_hotplug_nb = (struct notifier_block) {
		.notifier_call	= dcm_hotplug_nc,
		.priority	= INT_MIN + 2, /* NOTE: make sure this is lower than CPU DVFS */
	};

	if (register_cpu_notifier(&dcm_hotplug_nb))
		dcm_err("[%s]: fail to register_cpu_notifier\n", __func__);
#endif /* #ifdef CONFIG_HOTPLUG_CPU */

	dcm_initiated = 1;

	return 0;
}
late_initcall(mt_dcm_init);

/**** public APIs *****/
void mt_dcm_disable(void)
{
	if (!dcm_initiated)
		return;

	dcm_disable(all_dcm_type);
}

void mt_dcm_restore(void)
{
	if (!dcm_initiated)
		return;

	dcm_restore(all_dcm_type);
}
#endif

unsigned int sync_dcm_convert_freq2div(unsigned int freq)
{
	unsigned int div = 0;
#if 1
	if (freq < SYNC_DCM_CLK_MIN_FREQ)
		return 0;

	/* max divided ratio = Floor (CPU Frequency / (4 or 5) * system timer Frequency) */
	div = (freq / SYNC_DCM_CLK_MIN_FREQ) - 1;
	if (div > SYNC_DCM_MAX_DIV_VAL)
		return SYNC_DCM_MAX_DIV_VAL;
#endif
	return div;
}

int sync_dcm_set_cci_div(unsigned int cci)
{
	if (!dcm_initiated)
		return -1;

	/*
	 * 1. set xxx_sync_dcm_div first
	 * 2. set xxx_sync_dcm_tog from 0 to 1 for making sure it is toggled
	 */
	reg_write(MCUCFG_SYNC_DCM_CCI_REG,
		  aor(reg_read(MCUCFG_SYNC_DCM_CCI_REG),
		      ~MCUCFG_SYNC_DCM_SEL_CCI_MASK,
		      cci << MCUCFG_SYNC_DCM_SEL_CCI));
	reg_write(MCUCFG_SYNC_DCM_CCI_REG, aor(reg_read(MCUCFG_SYNC_DCM_CCI_REG),
				       ~MCUCFG_SYNC_DCM_CCI_TOGMASK,
				       MCUCFG_SYNC_DCM_CCI_TOG0));
	reg_write(MCUCFG_SYNC_DCM_CCI_REG, aor(reg_read(MCUCFG_SYNC_DCM_CCI_REG),
				       ~MCUCFG_SYNC_DCM_CCI_TOGMASK,
				       MCUCFG_SYNC_DCM_CCI_TOG1));
#ifdef __KERNEL__
	dcm_dbg("%s: MCUCFG_SYNC_DCM_CCI_REG=0x%08x, cci_div_sel=%u,%u\n",
#else
	dcm_dbg("%s: MCUCFG_SYNC_DCM_CCI_REG=0x%X, cci_div_sel=%u,%u\n",
#endif
		 __func__, reg_read(MCUCFG_SYNC_DCM_CCI_REG),
		 (and(reg_read(MCUCFG_SYNC_DCM_CCI_REG),
		      MCUCFG_SYNC_DCM_SEL_CCI_MASK) >> MCUCFG_SYNC_DCM_SEL_CCI),
		 cci);

	return 0;
}

int sync_dcm_set_cci_freq(unsigned int cci)
{
	dcm_dbg("%s: cci=%u\n", __func__, cci);
	sync_dcm_set_cci_div(sync_dcm_convert_freq2div(cci));

	return 0;
}

int sync_dcm_set_mp0_div(unsigned int mp0)
{
	unsigned int mp0_lo = (mp0 & 0xF);
	unsigned int mp0_hi = (mp0 & 0x70) >> 4;

	if (!dcm_initiated)
		return -1;

	/*
	 * 1. set xxx_sync_dcm_div first
	 * 2. set xxx_sync_dcm_tog from 0 to 1 for making sure it is toggled
	 */
	reg_write(MCUCFG_SYNC_DCM_MP0_REG,
		  aor(reg_read(MCUCFG_SYNC_DCM_MP0_REG),
		      ~(MCUCFG_SYNC_DCM_SEL_MP0_LO_MASK |
			MCUCFG_SYNC_DCM_SEL_MP0_HI_MASK),
		      (mp0_lo << MCUCFG_SYNC_DCM_SEL_MP0_LO) |
		      (mp0_hi << MCUCFG_SYNC_DCM_SEL_MP0_HI)));
	reg_write(MCUCFG_SYNC_DCM_MP0_REG, aor(reg_read(MCUCFG_SYNC_DCM_MP0_REG),
				       ~MCUCFG_SYNC_DCM_MP0_TOGMASK,
				       MCUCFG_SYNC_DCM_MP0_TOG0));
	reg_write(MCUCFG_SYNC_DCM_MP0_REG, aor(reg_read(MCUCFG_SYNC_DCM_MP0_REG),
				       ~MCUCFG_SYNC_DCM_MP0_TOGMASK,
				       MCUCFG_SYNC_DCM_MP0_TOG1));
#ifdef __KERNEL__
	dcm_dbg("%s: MCUCFG_SYNC_DCM_MP0_REG=0x%08x, mp0_div_sel=%u,%u, mp0_hi/lo=%u/%u\n",
#else
	dcm_dbg("%s: MCUCFG_SYNC_DCM_MP0_REG=0x%X, mp0_div_sel=%u,%u, mp0_hi/lo=%u/%u\n",
#endif
		 __func__, reg_read(MCUCFG_SYNC_DCM_MP0_REG),
		(or((and(reg_read(MCUCFG_SYNC_DCM_MP0_REG),
			 MCUCFG_SYNC_DCM_SEL_MP0_LO_MASK) >>
		     MCUCFG_SYNC_DCM_SEL_MP0_LO),
		    (and(reg_read(MCUCFG_SYNC_DCM_MP0_REG),
			 MCUCFG_SYNC_DCM_SEL_MP0_HI_MASK) >>
		     MCUCFG_SYNC_DCM_SEL_MP0_HI))),
		mp0, mp0_hi, mp0_lo);

	return 0;
}

int sync_dcm_set_mp0_freq(unsigned int mp0)
{
	dcm_dbg("%s: mp0=%u\n", __func__, mp0);
	sync_dcm_set_mp0_div(sync_dcm_convert_freq2div(mp0));

	return 0;
}

int sync_dcm_set_mp1_div(unsigned int mp1)
{
	return 0;
}

int sync_dcm_set_mp1_freq(unsigned int mp1)
{
	return 0;
}

int sync_dcm_set_mp2_div(unsigned int mp2)
{
	return 0;
}

int sync_dcm_set_mp2_freq(unsigned int mp2)
{
	return 0;
}

/* unit of frequency is MHz */
int sync_dcm_set_cpu_freq(unsigned int cci, unsigned int mp0, unsigned int mp1, unsigned int mp2)
{
	sync_dcm_set_cci_freq(cci);
	sync_dcm_set_mp0_freq(mp0);
	sync_dcm_set_mp1_freq(mp1);
	sync_dcm_set_mp2_freq(mp2);

	return 0;
}

int sync_dcm_set_cpu_div(unsigned int cci, unsigned int mp0, unsigned int mp1, unsigned int mp2)
{
	sync_dcm_set_cci_div(cci);
	sync_dcm_set_mp0_div(mp0);
	sync_dcm_set_mp1_div(mp1);
	sync_dcm_set_mp2_div(mp2);

	return 0;
}

