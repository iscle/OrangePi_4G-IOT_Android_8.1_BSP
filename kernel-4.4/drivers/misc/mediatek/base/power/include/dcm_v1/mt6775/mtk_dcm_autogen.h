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
extern unsigned long dcm_emi_base;
extern unsigned long dcm_ddrphy0ao_base;
extern unsigned long dcm_dramc0_ao_base;
extern unsigned long dcm_ddrphy1ao_base;
extern unsigned long dcm_dramc1_ao_base;
extern unsigned long dcm_chn_emi_reg0_base;
extern unsigned long dcm_chn_emi_reg1_base;
extern unsigned long dcm_pericfg_reg_base;
extern unsigned long dcm_venc_base;
extern unsigned long dcm_mcucfg_base;
extern unsigned long dcm_ca15m_config_base;

#define INFRACFG_AO_BASE (dcm_infracfg_ao_base)
#define EMI_BASE (dcm_emi_base)
#define DDRPHY0AO_BASE (dcm_ddrphy0ao_base)
#define DRAMC0_AO_BASE (dcm_dramc0_ao_base)
#define DDRPHY1AO_BASE (dcm_ddrphy1ao_base)
#define DRAMC1_AO_BASE (dcm_dramc1_ao_base)
#define CHN0_EMI_BASE (dcm_chn_emi_reg0_base)
#define CHN1_EMI_BASE (dcm_chn_emi_reg1_base)
#define PERICFG_BASE (dcm_pericfg_reg_base)
#define VENC_BASE (dcm_venc_base)
#define MP0_CPUCFG_BASE (dcm_mcucfg_base)
#define MCU_MISCCFG_BASE (dcm_mcucfg_base + 0x400)
#define MCU_MISC1CFG_BASE (dcm_mcucfg_base + 0x800)
#define MP2_CA15M_CONFIG_BASE (dcm_ca15m_config_base)
#else /* !(defined(__KERNEL__) && defined(CONFIG_OF)) */
#undef INFRACFG_AO_BASE
#undef EMI_BASE
#undef DDRPHY0AO_BASE
#undef DRAMC0_AO_BASE
#undef DDRPHY1AO_BASE
#undef DRAMC1_AO_BASE
/* #undef CHN0_EMI_BASE */
#undef CHN1_EMI_BASE
#undef PERICFG_BASE
#undef VENC_BASE
#undef MP0_CPUCFG_BASE
#undef MCU_MISCCFG_BASE
#undef MCU_MISC1CFG_BASE
#undef MP2_CA15M_CONFIG_BASE

/* Base */
#define INFRACFG_AO_BASE 0x10000000
#define EMI_BASE 0x10230000
#define DDRPHY0AO_BASE 0x10330000
#define DRAMC0_AO_BASE 0x10332000
#define CHN0_EMI_BASE 0x10335000
#define DDRPHY1AO_BASE 0x10338000
#define DRAMC1_AO_BASE 0x1033a000
#define CHN1_EMI_BASE 0x1033d000
#define PERICFG_BASE 0x11010000
#define VENC_BASE 0x17020000
#define MP0_CPUCFG_BASE 0xc530000
#define MCU_MISCCFG_BASE 0xc530400
#define MCU_MISC1CFG_BASE 0xc530800
#define MP2_CA15M_CONFIG_BASE 0xc532000
#endif /* #if defined(__KERNEL__) && defined(CONFIG_OF) */

/* Register Definition */
#define MP0_CPUSYS_RGU_SYNC_DCM (MP0_CPUCFG_BASE + 0x88)
#define L2C_SRAM_CTRL (MCU_MISCCFG_BASE + 0x248)
#define CCI_CLK_CTRL (MCU_MISCCFG_BASE + 0x260)
#define BUS_FABRIC_DCM_CTRL (MCU_MISCCFG_BASE + 0x268)
#define MCU_MISC_DCM_CTRL (MCU_MISCCFG_BASE + 0x26c)
#define CCI_ADB400_DCM_CONFIG (MCU_MISCCFG_BASE + 0x340)
#define SYNC_DCM_CONFIG (MCU_MISCCFG_BASE + 0x344)
#define SYNC_DCM_CLUSTER_CONFIG (MCU_MISCCFG_BASE + 0x34c)
#define MP_GIC_RGU_SYNC_DCM (MCU_MISCCFG_BASE + 0x358)
#define MP0_PLL_DIVIDER_CFG (MCU_MISCCFG_BASE + 0x3a0)
#define MP2_PLL_DIVIDER_CFG (MCU_MISCCFG_BASE + 0x3a8)
#define BUS_PLL_DIVIDER_CFG (MCU_MISCCFG_BASE + 0x3c0)
#define MCSIA_DCM_EN (MCU_MISC1CFG_BASE + 0x360)
#define MP2_CA15M_SYNC_DCM (MP2_CA15M_CONFIG_BASE + 0x274)
#define EMI_CONM (EMI_BASE + 0x60)
#define EMI_CONN (EMI_BASE + 0x68)
#define DDRPHY0AO_MISC_CG_CTRL0 (DDRPHY0AO_BASE + 0x284)
#define DDRPHY0AO_MISC_CG_CTRL2 (DDRPHY0AO_BASE + 0x28c)
#define DDRPHY0AO_MISC_CTRL3 (DDRPHY0AO_BASE + 0x2a8)
#define DRAMC0_AO_DRAMC_PD_CTRL (DRAMC0_AO_BASE + 0x38)
#define DRAMC0_AO_CLKAR (DRAMC0_AO_BASE + 0x3c)
#define CHN0_EMI_CHN_EMI_CONB (CHN0_EMI_BASE + 0x8)
#define DDRPHY1AO_MISC_CG_CTRL0 (DDRPHY1AO_BASE + 0x284)
#define DDRPHY1AO_MISC_CG_CTRL2 (DDRPHY1AO_BASE + 0x28c)
#define DDRPHY1AO_MISC_CTRL3 (DDRPHY1AO_BASE + 0x2a8)
#define DRAMC1_AO_DRAMC_PD_CTRL (DRAMC1_AO_BASE + 0x38)
#define DRAMC1_AO_CLKAR (DRAMC1_AO_BASE + 0x3c)
#define CHN1_EMI_CHN_EMI_CONB (CHN1_EMI_BASE + 0x8)
#define PERICFG_PERI_BIU_REG_DCM_CTRL (PERICFG_BASE + 0x210)
#define PERICFG_PERI_BIU_EMI_DCM_CTRL (PERICFG_BASE + 0x214)
#define VENC_VENC_CE (VENC_BASE + 0xec)
#define VENC_VENC_CLK_DCM_CTRL (VENC_BASE + 0xf4)
#define VENC_VENC_CLK_CG_CTRL (VENC_BASE + 0xfc)
#define INFRA_BUS_DCM_CTRL (INFRACFG_AO_BASE + 0x70)
#define INFRA_BUS_DCM_CTRL_1 (INFRACFG_AO_BASE + 0x74)
#define MEM_DCM_CTRL (INFRACFG_AO_BASE + 0x78)
#define INFRA_MDBUS_DCM_CTRL (INFRACFG_AO_BASE + 0xd0)
#define INFRA_QAXIBUS_DCM_CTRL (INFRACFG_AO_BASE + 0xd4)
#define INFRA_EMI_DCM_CTRL (INFRACFG_AO_BASE + 0xdc)

/* INFRACFG_AO */
bool dcm_infracfg_ao_infrabus_is_on(int on);
void dcm_infracfg_ao_infrabus(int on);
bool dcm_infracfg_ao_infrabus1_is_on(int on);
void dcm_infracfg_ao_infrabus1(int on);
bool dcm_infracfg_ao_infra_emi_is_on(int on);
void dcm_infracfg_ao_infra_emi(int on);
bool dcm_infracfg_ao_mdbus_is_on(int on);
void dcm_infracfg_ao_mdbus(int on);
bool dcm_infracfg_ao_mts_is_on(int on);
void dcm_infracfg_ao_mts(int on);
bool dcm_infracfg_ao_qaxibus_is_on(int on);
void dcm_infracfg_ao_qaxibus(int on);
bool dcm_infracfg_ao_top_emi_is_on(int on);
void dcm_infracfg_ao_top_emi(int on);
/* EMI */
bool dcm_emi_dcm_emi_group_is_on(int on);
void dcm_emi_dcm_emi_group(int on);
/* DDRPHY0AO */
bool dcm_ddrphy0ao_ddrphy_is_on(int on);
void dcm_ddrphy0ao_ddrphy(int on);
/* DRAMC0_AO */
bool dcm_dramc0_ao_dramc_dcm_is_on(int on);
void dcm_dramc0_ao_dramc_dcm(int on);
/* CHN0_EMI */
bool dcm_chn0_emi_dcm_emi_group_is_on(int on);
void dcm_chn0_emi_dcm_emi_group(int on);
/* DDRPHY1AO */
bool dcm_ddrphy1ao_ddrphy_is_on(int on);
void dcm_ddrphy1ao_ddrphy(int on);
/* DRAMC1_AO */
bool dcm_dramc1_ao_dramc_dcm_is_on(int on);
void dcm_dramc1_ao_dramc_dcm(int on);
/* CHN1_EMI */
bool dcm_chn1_emi_dcm_emi_group_is_on(int on);
void dcm_chn1_emi_dcm_emi_group(int on);
/* PERICFG */
bool dcm_pericfg_emibiu_is_on(int on);
void dcm_pericfg_emibiu(int on);
bool dcm_pericfg_emibus_is_on(int on);
void dcm_pericfg_emibus(int on);
bool dcm_pericfg_regbiu_is_on(int on);
void dcm_pericfg_regbiu(int on);
bool dcm_pericfg_regbus_is_on(int on);
void dcm_pericfg_regbus(int on);
/* VENC */
bool dcm_venc_venc_is_on(int on);
void dcm_venc_venc(int on);
/* MP0_CPUCFG */
bool dcm_mp0_cpucfg_mp0_rgu_dcm_is_on(int on);
void dcm_mp0_cpucfg_mp0_rgu_dcm(int on);
/* MCU_MISCCFG */
bool dcm_mcu_misccfg_adb400_dcm_is_on(int on);
void dcm_mcu_misccfg_adb400_dcm(int on);
bool dcm_mcu_misccfg_bus_arm_pll_divider_dcm_is_on(int on);
void dcm_mcu_misccfg_bus_arm_pll_divider_dcm(int on);
bool dcm_mcu_misccfg_bus_sync_dcm_is_on(int on);
void dcm_mcu_misccfg_bus_sync_dcm(int on);
bool dcm_mcu_misccfg_bus_clock_dcm_is_on(int on);
void dcm_mcu_misccfg_bus_clock_dcm(int on);
bool dcm_mcu_misccfg_bus_fabric_dcm_is_on(int on);
void dcm_mcu_misccfg_bus_fabric_dcm(int on);
bool dcm_mcu_misccfg_gic_sync_dcm_is_on(int on);
void dcm_mcu_misccfg_gic_sync_dcm(int on);
bool dcm_mcu_misccfg_l2_shared_dcm_is_on(int on);
void dcm_mcu_misccfg_l2_shared_dcm(int on);
bool dcm_mcu_misccfg_mp0_arm_pll_divider_dcm_is_on(int on);
void dcm_mcu_misccfg_mp0_arm_pll_divider_dcm(int on);
bool dcm_mcu_misccfg_mp0_stall_dcm_is_on(int on);
void dcm_mcu_misccfg_mp0_stall_dcm(int on);
bool dcm_mcu_misccfg_mp0_sync_dcm_enable_is_on(int on);
void dcm_mcu_misccfg_mp0_sync_dcm_enable(int on);
bool dcm_mcu_misccfg_mp2_arm_pll_divider_dcm_is_on(int on);
void dcm_mcu_misccfg_mp2_arm_pll_divider_dcm(int on);
bool dcm_mcu_misccfg_mp_stall_dcm_is_on(int on);
void dcm_mcu_misccfg_mp_stall_dcm(int on);
bool dcm_mcu_misccfg_mcu_misc_dcm_is_on(int on);
void dcm_mcu_misccfg_mcu_misc_dcm(int on);
/* MCU_MISC1CFG */
void dcm_mcu_misc1cfg_mcsib_dcm_preset(int on);
bool dcm_mcu_misc1cfg_mcsib_dcm_is_on(int on);
void dcm_mcu_misc1cfg_mcsib_dcm(int on);
/* MP2_CA15M_CONFIG */
bool dcm_mp2_ca15m_config_sync_dcm_cfg_is_on(int on);
void dcm_mp2_ca15m_config_sync_dcm_cfg(int on);

#endif
