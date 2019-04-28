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

#ifndef __MTK_DCM_AUTOGEN_H__
#define __MTK_DCM_AUTOGEN_H__

#include <mtk_dcm.h>

#if defined(__KERNEL__) && defined(CONFIG_OF)
extern unsigned long dcm_infracfg_ao_base;
extern unsigned long dcm_pericfg_reg_base;
extern unsigned long dcm_mcucfg_base;
extern unsigned long dcm_mcucfg_phys_base;
#ifndef USE_DRAM_API_INSTEAD
extern unsigned long dcm_ddrphy0_ao_base;
extern unsigned long dcm_ddrphy1_ao_base;
#endif /* #ifndef USE_DRAM_API_INSTEAD */
extern unsigned long dcm_dramc0_ao_base;
extern unsigned long dcm_dramc1_ao_base;
extern unsigned long dcm_chn_emi_reg0_base;
extern unsigned long dcm_chn_emi_reg1_base;
extern unsigned long dcm_emi_base;

#define INFRACFG_AO_BASE		(dcm_infracfg_ao_base)
#define PERICFG_REG_BASE		(dcm_pericfg_reg_base)
#define MCUCFG_BASE			(dcm_mcucfg_base)
#define MP0_CPUCFG_BASE			(MCUCFG_BASE)
#define MP1_CPUCFG_BASE			(MCUCFG_BASE + 0x200)
#define MCU_MISCCFG_BASE		(MCUCFG_BASE + 0x400)
#define MCU_MISC1CFG_BASE		(MCUCFG_BASE + 0x800)
#define DDRPHY0AO_BASE			(dcm_ddrphy0_ao_base)
#define DDRPHY1AO_BASE			(dcm_ddrphy1_ao_base)
#define CHN0_EMI_BASE			(dcm_chn_emi_reg0_base)
#define CHN1_EMI_BASE			(dcm_chn_emi_reg1_base)
#define DRAMC0_AO_BASE			(dcm_dramc0_ao_base)
#define DRAMC1_AO_BASE			(dcm_dramc1_ao_base)
#define EMI_BASE				(dcm_emi_base)
#else /* !(defined(__KERNEL__) && defined(CONFIG_OF)) */
#undef INFRACFG_AO_BASE
#undef PERICFG_REG_BASE
#undef MCUCFG_BASE
#undef MP0_CPUCFG_BASE
#undef MP1_CPUCFG_BASE
#undef MCU_MISCCFG_BASE
#undef MCU_MISC1CFG_BASE
#undef DDRPHY0AO_BASE
#undef DDRPHY1AO_BASE
#undef CHN0_EMI_BASE
#undef CHN1_EMI_BASE
#undef DRAMC0_AO_BASE
#undef DRAMC1_AO_BASE
#undef EMI_BASE

/* Base */
#define INFRACFG_AO_BASE 0x10000000
#define PERICFG_REG_BASE 0x11010000
#define EMI_BASE 0x10230000
#define DDRPHY0AO_BASE 0x10330000
#define DRAMC0_AO_BASE 0x10332000
#define CHN0_EMI_BASE 0x10335000
#define DDRPHY1AO_BASE 0x10338000
#define DRAMC1_AO_BASE 0x1033a000
#define CHN1_EMI_BASE 0x1033d000
#define MP0_CPUCFG_BASE 0xc530000
#define MP1_CPUCFG_BASE 0xc530200
#define MCU_MISCCFG_BASE 0xc530400
#define MCU_MISC1CFG_BASE 0xc530800
#define MCUCFG_BASE MP0_CPUCFG_BASE
#endif /* #if defined(__KERNEL__) && defined(CONFIG_OF) */

/* Register Definition */
#define MP0_CPUCFG_MP0_RGU_DCM_CONFIG (MP0_CPUCFG_BASE + 0x88)
#define MP1_CPUCFG_MP1_RGU_DCM_CONFIG (MP1_CPUCFG_BASE + 0x88)
#define L2C_SRAM_CTRL (MCU_MISCCFG_BASE + 0x248)
#define CCI_CLK_CTRL (MCU_MISCCFG_BASE + 0x260)
#define BUS_FABRIC_DCM_CTRL (MCU_MISCCFG_BASE + 0x268)
#define MCU_MISC_DCM_CTRL (MCU_MISCCFG_BASE + 0x26c)
#define CCI_ADB400_DCM_CONFIG (MCU_MISCCFG_BASE + 0x340)
#define SYNC_DCM_CONFIG (MCU_MISCCFG_BASE + 0x344)
#define SYNC_DCM_CLUSTER_CONFIG (MCU_MISCCFG_BASE + 0x34c)
#define MP_GIC_RGU_SYNC_DCM (MCU_MISCCFG_BASE + 0x358)
#define MP0_PLL_DIVIDER_CFG (MCU_MISCCFG_BASE + 0x3a0)
#define MP1_PLL_DIVIDER_CFG (MCU_MISCCFG_BASE + 0x3a4)
#define BUS_PLL_DIVIDER_CFG (MCU_MISCCFG_BASE + 0x3c0)
#define MCSIA_DCM_EN (MCU_MISC1CFG_BASE + 0x360)
#define EMI_CONM (EMI_BASE + 0x60)
#define EMI_CONN (EMI_BASE + 0x68)
#define DDRPHY0AO_MISC_CG_CTRL0 (DDRPHY0AO_BASE + 0x284)
#define DDRPHY0AO_MISC_CG_CTRL2 (DDRPHY0AO_BASE + 0x28c)
#define DDRPHY0AO_MISC_CTRL3 (DDRPHY0AO_BASE + 0x2a8)
#define DDRPHY1AO_MISC_CG_CTRL0 (DDRPHY1AO_BASE + 0x284)
#define DDRPHY1AO_MISC_CG_CTRL2 (DDRPHY1AO_BASE + 0x28c)
#define DDRPHY1AO_MISC_CTRL3 (DDRPHY1AO_BASE + 0x2a8)
#define CHN0_EMI_CHN_EMI_CONB (CHN0_EMI_BASE + 0x8)
#define CHN1_EMI_CHN_EMI_CONB (CHN1_EMI_BASE + 0x8)
#define DRAMC0_AO_DRAMC_PD_CTRL (DRAMC0_AO_BASE + 0x38)
#define DRAMC0_AO_CLKAR (DRAMC0_AO_BASE + 0x3c)
#define DRAMC1_AO_DRAMC_PD_CTRL (DRAMC1_AO_BASE + 0x38)
#define DRAMC1_AO_CLKAR (DRAMC1_AO_BASE + 0x3c)
#define PERICFG_DCM_EMI_EARLY_CTRL (PERICFG_REG_BASE + 0x220)
#define INFRA_BUS_DCM_CTRL (INFRACFG_AO_BASE + 0x70)
#define INFRA_MDBUS_DCM_CTRL (INFRACFG_AO_BASE + 0xd0)
#define INFRA_QAXIBUS_DCM_CTRL (INFRACFG_AO_BASE + 0xd4)
#define INFRA_EMI_DCM_CTRL (INFRACFG_AO_BASE + 0xdc)
#define INFRA_EMI_DCM_CTRL_1 (INFRACFG_AO_BASE + 0xf70)

/* INFRACFG_AO */
bool dcm_infracfg_ao_infrabus_is_on(void);
void dcm_infracfg_ao_infrabus(int on);
bool dcm_infracfg_ao_infra_emi_is_on(void);
void dcm_infracfg_ao_infra_emi(int on);
bool dcm_infracfg_ao_mdbus_is_on(void);
void dcm_infracfg_ao_mdbus(int on);
bool dcm_infracfg_ao_mts_is_on(void);
void dcm_infracfg_ao_mts(int on);
bool dcm_infracfg_ao_qaxibus_is_on(void);
void dcm_infracfg_ao_qaxibus(int on);
/* PERICFG */
bool dcm_pericfg_reg_is_on(void);
void dcm_pericfg_reg(int on);
/* EMI */
bool dcm_emi_dcm_emi_group_is_on(void);
void dcm_emi_dcm_emi_group(int on);
/* DDRPHY0AO */
bool dcm_ddrphy0ao_ddrphy_is_on(void);
void dcm_ddrphy0ao_ddrphy(int on);
/* DDRPHY1AO */
bool dcm_ddrphy1ao_ddrphy_is_on(void);
void dcm_ddrphy1ao_ddrphy(int on);
/* CHN0_EMI */
bool dcm_chn0_emi_dcm_emi_group_is_on(void);
void dcm_chn0_emi_dcm_emi_group(int on);
/* CHN1_EMI */
bool dcm_chn1_emi_dcm_emi_group_is_on(void);
void dcm_chn1_emi_dcm_emi_group(int on);
/* DRAMC0_AO */
bool dcm_dramc0_ao_dramc_dcm_is_on(void);
void dcm_dramc0_ao_dramc_dcm(int on);
/* DRAMC1_AO */
bool dcm_dramc1_ao_dramc_dcm_is_on(void);
void dcm_dramc1_ao_dramc_dcm(int on);
/* MP0_CPUCFG */
bool dcm_mp0_cpucfg_mp0_rgu_dcm_is_on(void);
void dcm_mp0_cpucfg_mp0_rgu_dcm(int on);
/* MP1_CPUCFG */
bool dcm_mp1_cpucfg_mp1_rgu_dcm_is_on(void);
void dcm_mp1_cpucfg_mp1_rgu_dcm(int on);
/* MCU_MISCCFG */
bool dcm_mcu_misccfg_adb400_dcm_is_on(void);
void dcm_mcu_misccfg_adb400_dcm(int on);
bool dcm_mcu_misccfg_bus_arm_pll_divider_dcm_is_on(void);
void dcm_mcu_misccfg_bus_arm_pll_divider_dcm(int on);
bool dcm_mcu_misccfg_bus_sync_dcm_is_on(void);
void dcm_mcu_misccfg_bus_sync_dcm(int on);
bool dcm_mcu_misccfg_bus_clock_dcm_is_on(void);
void dcm_mcu_misccfg_bus_clock_dcm(int on);
bool dcm_mcu_misccfg_bus_fabric_dcm_is_on(void);
void dcm_mcu_misccfg_bus_fabric_dcm(int on);
bool dcm_mcu_misccfg_gic_sync_dcm_is_on(void);
void dcm_mcu_misccfg_gic_sync_dcm(int on);
bool dcm_mcu_misccfg_l2_shared_dcm_is_on(void);
void dcm_mcu_misccfg_l2_shared_dcm(int on);
bool dcm_mcu_misccfg_mp0_arm_pll_divider_dcm_is_on(void);
void dcm_mcu_misccfg_mp0_arm_pll_divider_dcm(int on);
bool dcm_mcu_misccfg_mp0_stall_dcm_is_on(void);
void dcm_mcu_misccfg_mp0_stall_dcm(int on);
bool dcm_mcu_misccfg_mp0_sync_dcm_enable_is_on(void);
void dcm_mcu_misccfg_mp0_sync_dcm_enable(int on);
bool dcm_mcu_misccfg_mp1_arm_pll_divider_dcm_is_on(void);
void dcm_mcu_misccfg_mp1_arm_pll_divider_dcm(int on);
bool dcm_mcu_misccfg_mp1_stall_dcm_is_on(void);
void dcm_mcu_misccfg_mp1_stall_dcm(int on);
bool dcm_mcu_misccfg_mp1_sync_dcm_enable_is_on(void);
void dcm_mcu_misccfg_mp1_sync_dcm_enable(int on);
bool dcm_mcu_misccfg_mp_stall_dcm_is_on(void);
void dcm_mcu_misccfg_mp_stall_dcm(int on);
bool dcm_mcu_misccfg_mcu_misc_dcm_is_on(void);
void dcm_mcu_misccfg_mcu_misc_dcm(int on);
/* MCU_MISC1CFG */
bool dcm_mcu_misc1cfg_mcsia_dcm_is_on(void);
void dcm_mcu_misc1cfg_mcsia_dcm(int on);

#endif
