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

#ifdef __KERNEL__
#include <mt-plat/mtk_io.h>
#include <mt-plat/sync_write.h>
#include <mt-plat/mtk_secure_api.h>
#else /* ! __KERNEL__ */
#ifdef __CTP_DCM__
#include <sync_write.h>
#include <common.h>
#elif defined(__RTL__)
#include "cmessage.h"
#include "API.h"
#else /* __LK__ */
#include <debug.h>
#include <stdlib.h>
#include <string.h>
#include <sys/types.h>
#include <arch/arm.h>
#include <arch/arm/mmu.h>
#include <arch/ops.h>
#include <target/board.h>
#include <platform/mt_reg_base.h>
#include <platform/mt_typedefs.h>
#include <platform/sync_write.h>
#endif
#endif

#include <mtk_dcm_autogen.h>
#include <mtk_dcm_internal.h>

#ifdef __KERNEL__
#define reg_read(addr)	__raw_readl(IOMEM(addr))
#define reg_write(addr, val)	mt_reg_sync_writel((val), ((void *)addr))
#else /* ! __KERNEL__ */
#endif /* #ifdef __KERNEL__ */

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
#else /* !__KERNEL__, for CTP */
#if 0
#ifdef __CTP_DCM__ /* CTP */
#define MCUSYS_SMC_WRITE(addr, val)  reg_write(addr, val)
#define MCSI_SMC_WRITE(addr, val)  reg_write(addr, val)
#define MCSI_SMC_READ(addr)  reg_read(addr)
#else /* __LK__ */
#define MCUSYS_SMC_WRITE(addr, val)  reg_write(addr, val)
#define MCSI_SMC_WRITE(addr, val)  mcsi_reg_write(val, (addr##_PHYS & 0xFFFF))
#define MCSI_SMC_READ(addr)  mcsi_reg_read(addr##_PHYS & 0xFFFF)
#endif /* #ifdef __CTP_DCM__ */
#endif
#endif /* #ifdef __KERNEL__ */

#define INFRACFG_AO_INFRABUS_REG0_MASK ((0x1 << 0) | \
			(0x1 << 1) | \
			(0x1 << 2) | \
			(0x1 << 3) | \
			(0x1 << 4) | \
			(0x1f << 15) | \
			(0x1 << 20) | \
			(0x1 << 21) | \
			(0x1 << 22))
#define INFRACFG_AO_INFRABUS_REG0_ON ((0x1 << 0) | \
			(0x0 << 1) | \
			(0x0 << 2) | \
			(0x0 << 3) | \
			(0x0 << 4) | \
			(0x10 << 15) | \
			(0x1 << 20) | \
			(0x1 << 21) | \
			(0x1 << 22))
#define INFRACFG_AO_INFRABUS_REG0_OFF ((0x0 << 0) | \
			(0x0 << 1) | \
			(0x0 << 2) | \
			(0x0 << 3) | \
			(0x0 << 4) | \
			(0x10 << 15) | \
			(0x1 << 20) | \
			(0x1 << 21) | \
			(0x1 << 22))
#if 0
static unsigned int infracfg_ao_infra_dcm_133m_rg_sfsel_get(void)
{
	return (reg_read(INFRA_BUS_DCM_CTRL) >> 10) & 0x1f;
}
#endif
static void infracfg_ao_infra_dcm_133m_rg_sfsel_set(unsigned int val)
{
	reg_write(INFRA_BUS_DCM_CTRL,
		(reg_read(INFRA_BUS_DCM_CTRL) &
		~(0x1f << 10)) |
		(val & 0x1f) << 10);
}

bool dcm_infracfg_ao_infrabus_is_on(int on)
{
	bool ret = true;

	ret &= !!(reg_read(INFRA_BUS_DCM_CTRL) &
		INFRACFG_AO_INFRABUS_REG0_MASK &
		INFRACFG_AO_INFRABUS_REG0_ON);

	return ret;
}

void dcm_infracfg_ao_infrabus(int on)
{
	if (on) {
		/* TINFO = "Turn ON DCM 'infracfg_ao_infrabus'" */
		infracfg_ao_infra_dcm_133m_rg_sfsel_set(0x1f);
		reg_write(INFRA_BUS_DCM_CTRL,
			(reg_read(INFRA_BUS_DCM_CTRL) &
			~INFRACFG_AO_INFRABUS_REG0_MASK) |
			INFRACFG_AO_INFRABUS_REG0_ON);
	} else {
		/* TINFO = "Turn OFF DCM 'infracfg_ao_infrabus'" */
		infracfg_ao_infra_dcm_133m_rg_sfsel_set(0x1f);
		reg_write(INFRA_BUS_DCM_CTRL,
			(reg_read(INFRA_BUS_DCM_CTRL) &
			~INFRACFG_AO_INFRABUS_REG0_MASK) |
			INFRACFG_AO_INFRABUS_REG0_OFF);
	}
}

#define INFRACFG_AO_INFRABUS_1_REG0_MASK (0x3 << 2)
#define INFRACFG_AO_INFRABUS_1_REG0_ON (0x2 << 2)
#define INFRACFG_AO_INFRABUS_1_REG0_OFF (0x1 << 2)

bool dcm_infracfg_ao_infrabus1_is_on(int on)
{
	bool ret = true;

	ret &= !!(reg_read(INFRA_BUS_DCM_CTRL_1) &
		INFRACFG_AO_INFRABUS_1_REG0_MASK &
		INFRACFG_AO_INFRABUS_1_REG0_ON);

	return ret;
}

void dcm_infracfg_ao_infrabus1(int on)
{
	if (on) {
		/* TINFO = "Turn ON DCM 'infracfg_ao_infrabus'" */
		/*infracfg_ao_infra_dcm_133m_rg_sfsel_set(0x1f);*/
		reg_write(INFRA_BUS_DCM_CTRL_1,
			(reg_read(INFRA_BUS_DCM_CTRL_1) &
			~INFRACFG_AO_INFRABUS_1_REG0_MASK) |
			INFRACFG_AO_INFRABUS_1_REG0_ON);
	} else {
		/* TINFO = "Turn OFF DCM 'infracfg_ao_infrabus'" */
		/*infracfg_ao_infra_dcm_133m_rg_sfsel_set(0x1f);*/
		reg_write(INFRA_BUS_DCM_CTRL_1,
			(reg_read(INFRA_BUS_DCM_CTRL_1) &
			~INFRACFG_AO_INFRABUS_1_REG0_MASK) |
			INFRACFG_AO_INFRABUS_1_REG0_OFF);
	}
}

#define INFRACFG_AO_INFRA_EMI_REG0_MASK ((0x1 << 0) | \
			(0x1 << 1) | \
			(0x1 << 2) | \
			(0x1 << 3) | \
			(0x1 << 4) | \
			(0x1 << 5))
#define INFRACFG_AO_INFRA_EMI_REG0_ON ((0x1 << 0) | \
			(0x1 << 1) | \
			(0x1 << 2) | \
			(0x1 << 3) | \
			(0x1 << 4) | \
			(0x1 << 5))
#define INFRACFG_AO_INFRA_EMI_REG0_OFF ((0x0 << 0) | \
			(0x0 << 1) | \
			(0x0 << 2) | \
			(0x0 << 3) | \
			(0x0 << 4) | \
			(0x0 << 5))

bool dcm_infracfg_ao_infra_emi_is_on(int on)
{
	bool ret = true;

	ret &= !!(reg_read(INFRA_EMI_DCM_CTRL) &
		INFRACFG_AO_INFRA_EMI_REG0_MASK &
		INFRACFG_AO_INFRA_EMI_REG0_ON);

	return ret;
}

void dcm_infracfg_ao_infra_emi(int on)
{
	if (on) {
		/* TINFO = "Turn ON DCM 'infracfg_ao_infra_emi'" */
		reg_write(INFRA_EMI_DCM_CTRL,
			(reg_read(INFRA_EMI_DCM_CTRL) &
			~INFRACFG_AO_INFRA_EMI_REG0_MASK) |
			INFRACFG_AO_INFRA_EMI_REG0_ON);
	} else {
		/* TINFO = "Turn OFF DCM 'infracfg_ao_infra_emi'" */
		reg_write(INFRA_EMI_DCM_CTRL,
			(reg_read(INFRA_EMI_DCM_CTRL) &
			~INFRACFG_AO_INFRA_EMI_REG0_MASK) |
			INFRACFG_AO_INFRA_EMI_REG0_OFF);
	}
}

#define INFRACFG_AO_MDBUS_REG0_MASK ((0x1 << 0) | \
			(0x1 << 1) | \
			(0x1 << 2) | \
			(0x1 << 3) | \
			(0x1f << 15) | \
			(0x1 << 20))
#define INFRACFG_AO_MDBUS_REG0_ON ((0x1 << 0) | \
			(0x0 << 1) | \
			(0x0 << 2) | \
			(0x0 << 3) | \
			(0x10 << 15) | \
			(0x1 << 20))
#define INFRACFG_AO_MDBUS_REG0_OFF ((0x0 << 0) | \
			(0x0 << 1) | \
			(0x0 << 2) | \
			(0x0 << 3) | \
			(0x10 << 15) | \
			(0x1 << 20))

bool dcm_infracfg_ao_mdbus_is_on(int on)
{
	bool ret = true;

	ret &= !!(reg_read(INFRA_MDBUS_DCM_CTRL) &
		INFRACFG_AO_MDBUS_REG0_MASK &
		INFRACFG_AO_MDBUS_REG0_ON);

	return ret;
}

void dcm_infracfg_ao_mdbus(int on)
{
	if (on) {
		/* TINFO = "Turn ON DCM 'infracfg_ao_mdbus'" */
		reg_write(INFRA_MDBUS_DCM_CTRL,
			(reg_read(INFRA_MDBUS_DCM_CTRL) &
			~INFRACFG_AO_MDBUS_REG0_MASK) |
			INFRACFG_AO_MDBUS_REG0_ON);
	} else {
		/* TINFO = "Turn OFF DCM 'infracfg_ao_mdbus'" */
		reg_write(INFRA_MDBUS_DCM_CTRL,
			(reg_read(INFRA_MDBUS_DCM_CTRL) &
			~INFRACFG_AO_MDBUS_REG0_MASK) |
			INFRACFG_AO_MDBUS_REG0_OFF);
	}
}

#define INFRACFG_AO_MTS_REG0_MASK ((0x1 << 30))
#define INFRACFG_AO_MTS_REG0_ON ((0x1 << 30))
#define INFRACFG_AO_MTS_REG0_OFF ((0x0 << 30))

bool dcm_infracfg_ao_mts_is_on(int on)
{
	bool ret = true;

	ret &= !!(reg_read(INFRA_BUS_DCM_CTRL) &
		INFRACFG_AO_MTS_REG0_MASK &
		INFRACFG_AO_MTS_REG0_ON);

	return ret;
}

void dcm_infracfg_ao_mts(int on)
{
	if (on) {
		/* TINFO = "Turn ON DCM 'infracfg_ao_mts'" */
		reg_write(INFRA_BUS_DCM_CTRL,
			(reg_read(INFRA_BUS_DCM_CTRL) &
			~INFRACFG_AO_MTS_REG0_MASK) |
			INFRACFG_AO_MTS_REG0_ON);
	} else {
		/* TINFO = "Turn OFF DCM 'infracfg_ao_mts'" */
		reg_write(INFRA_BUS_DCM_CTRL,
			(reg_read(INFRA_BUS_DCM_CTRL) &
			~INFRACFG_AO_MTS_REG0_MASK) |
			INFRACFG_AO_MTS_REG0_OFF);
	}
}

#define INFRACFG_AO_QAXIBUS_REG0_MASK ((0x1 << 0) | \
			(0x1 << 1) | \
			(0x1 << 2) | \
			(0x1 << 3) | \
			(0x1f << 15) | \
			(0x1 << 20))
#define INFRACFG_AO_QAXIBUS_REG0_ON ((0x1 << 0) | \
			(0x0 << 1) | \
			(0x0 << 2) | \
			(0x0 << 3) | \
			(0x10 << 15) | \
			(0x1 << 20))
#define INFRACFG_AO_QAXIBUS_REG0_OFF ((0x0 << 0) | \
			(0x0 << 1) | \
			(0x0 << 2) | \
			(0x0 << 3) | \
			(0x10 << 15) | \
			(0x1 << 20))
#if 0
static unsigned int infracfg_ao_infra_qaxibus_dcm_rg_sfsel_get(void)
{
	return (reg_read(INFRA_QAXIBUS_DCM_CTRL) >> 10) & 0x1f;
}
#endif
static void infracfg_ao_infra_qaxibus_dcm_rg_sfsel_set(unsigned int val)
{
	reg_write(INFRA_QAXIBUS_DCM_CTRL,
		(reg_read(INFRA_QAXIBUS_DCM_CTRL) &
		~(0x1f << 10)) |
		(val & 0x1f) << 10);
}

bool dcm_infracfg_ao_qaxibus_is_on(int on)
{
	bool ret = true;

	ret &= !!(reg_read(INFRA_QAXIBUS_DCM_CTRL) &
		INFRACFG_AO_QAXIBUS_REG0_MASK &
		INFRACFG_AO_QAXIBUS_REG0_ON);

	return ret;
}

void dcm_infracfg_ao_qaxibus(int on)
{
	if (on) {
		/* TINFO = "Turn ON DCM 'infracfg_ao_qaxibus'" */
		infracfg_ao_infra_qaxibus_dcm_rg_sfsel_set(0x1f);
		reg_write(INFRA_QAXIBUS_DCM_CTRL,
			(reg_read(INFRA_QAXIBUS_DCM_CTRL) &
			~INFRACFG_AO_QAXIBUS_REG0_MASK) |
			INFRACFG_AO_QAXIBUS_REG0_ON);
	} else {
		/* TINFO = "Turn OFF DCM 'infracfg_ao_qaxibus'" */
		infracfg_ao_infra_qaxibus_dcm_rg_sfsel_set(0x1f);
		reg_write(INFRA_QAXIBUS_DCM_CTRL,
			(reg_read(INFRA_QAXIBUS_DCM_CTRL) &
			~INFRACFG_AO_QAXIBUS_REG0_MASK) |
			INFRACFG_AO_QAXIBUS_REG0_OFF);
	}
}

#if 0
#define INFRACFG_AO_TOP_EMI_REG0_MASK ((0x1f << 1) | \
			(0x1 << 6) | \
			(0x1 << 7) | \
			(0x1 << 8) | \
			(0x7f << 9) | \
			(0x1 << 26))
#define INFRACFG_AO_TOP_EMI_REG0_ON ((0x0 << 1) | \
			(0x0 << 6) | \
			(0x0 << 7) | \
			(0x0 << 8) | \
			(0x0 << 9) | \
			(0x0 << 26))
#define INFRACFG_AO_TOP_EMI_REG0_OFF ((0x0 << 1) | \
			(0x0 << 6) | \
			(0x0 << 7) | \
			(0x0 << 8) | \
			(0x0 << 9) | \
			(0x0 << 26))
#endif
#define INFRACFG_AO_TOP_EMI_REG0_MASK (0x1 << 27)
#define INFRACFG_AO_TOP_EMI_REG0_ON (0x1 << 27)
#define INFRACFG_AO_TOP_EMI_REG0_OFF (0x0 << 27)

#define INFRACFG_AO_TOP_EMI_REG0_TOG_MASK ((0x1 << 0))
#define INFRACFG_AO_TOP_EMI_REG0_TOG1 ((0x1 << 0))
#define INFRACFG_AO_TOP_EMI_REG0_TOG0 ((0x0 << 0))
#if 0
static unsigned int infracfg_ao_mem_dcm_idle_fsel_get(void)
{
	return (reg_read(MEM_DCM_CTRL) >> 21) & 0x1f;
}
#endif
static void infracfg_ao_mem_dcm_idle_fsel_set(unsigned int val)
{
	reg_write(MEM_DCM_CTRL,
		(reg_read(MEM_DCM_CTRL) &
		~(0x1f << 21)) |
		(val & 0x1f) << 21);
}

bool dcm_infracfg_ao_top_emi_is_on(int on)
{
	bool ret = true;

	ret &= !!(reg_read(MEM_DCM_CTRL) &
		INFRACFG_AO_TOP_EMI_REG0_MASK &
		INFRACFG_AO_TOP_EMI_REG0_ON);

	return ret;
}

void dcm_infracfg_ao_top_emi(int on)
{
	if (on) {
		/* TINFO = "Turn ON DCM 'infracfg_ao_top_emi'" */
		reg_write(MEM_DCM_CTRL,
			(reg_read(MEM_DCM_CTRL) &
			~INFRACFG_AO_TOP_EMI_REG0_TOG_MASK) |
			INFRACFG_AO_TOP_EMI_REG0_TOG0);
		infracfg_ao_mem_dcm_idle_fsel_set(0x0);
		reg_write(MEM_DCM_CTRL,
			(reg_read(MEM_DCM_CTRL) &
			~INFRACFG_AO_TOP_EMI_REG0_MASK) |
			INFRACFG_AO_TOP_EMI_REG0_ON);
		reg_write(MEM_DCM_CTRL,
			(reg_read(MEM_DCM_CTRL) &
			~INFRACFG_AO_TOP_EMI_REG0_TOG_MASK) |
			INFRACFG_AO_TOP_EMI_REG0_TOG1);
	} else {
		/* TINFO = "Turn OFF DCM 'infracfg_ao_top_emi'" */
		reg_write(MEM_DCM_CTRL,
			(reg_read(MEM_DCM_CTRL) &
			~INFRACFG_AO_TOP_EMI_REG0_TOG_MASK) |
			INFRACFG_AO_TOP_EMI_REG0_TOG0);
		infracfg_ao_mem_dcm_idle_fsel_set(0x0);
		reg_write(MEM_DCM_CTRL,
			(reg_read(MEM_DCM_CTRL) &
			~INFRACFG_AO_TOP_EMI_REG0_MASK) |
			INFRACFG_AO_TOP_EMI_REG0_OFF);
		reg_write(MEM_DCM_CTRL,
			(reg_read(MEM_DCM_CTRL) &
			~INFRACFG_AO_TOP_EMI_REG0_TOG_MASK) |
			INFRACFG_AO_TOP_EMI_REG0_TOG1);
	}
}

#define EMI_DCM_EMI_GROUP_REG0_MASK ((0xff << 24))
#define EMI_DCM_EMI_GROUP_REG1_MASK ((0xff << 24))
#define EMI_DCM_EMI_GROUP_REG0_ON ((0x4 << 24))
#define EMI_DCM_EMI_GROUP_REG1_ON ((0x6 << 24))
#define EMI_DCM_EMI_GROUP_REG0_OFF ((0xff << 24))
#define EMI_DCM_EMI_GROUP_REG1_OFF ((0xff << 24))

bool dcm_emi_dcm_emi_group_is_on(int on)
{
	bool ret = true;

	ret &= !!(reg_read(EMI_CONM) &
		EMI_DCM_EMI_GROUP_REG0_MASK &
		EMI_DCM_EMI_GROUP_REG0_ON);
	ret &= !!(reg_read(EMI_CONN) &
		EMI_DCM_EMI_GROUP_REG1_MASK &
		EMI_DCM_EMI_GROUP_REG1_ON);

	return ret;
}

void dcm_emi_dcm_emi_group(int on)
{
	if (on) {
		/* TINFO = "Turn ON DCM 'emi_dcm_emi_group'" */
		reg_write(EMI_CONM,
			(reg_read(EMI_CONM) &
			~EMI_DCM_EMI_GROUP_REG0_MASK) |
			EMI_DCM_EMI_GROUP_REG0_ON);
		reg_write(EMI_CONN,
			(reg_read(EMI_CONN) &
			~EMI_DCM_EMI_GROUP_REG1_MASK) |
			EMI_DCM_EMI_GROUP_REG1_ON);
	} else {
		/* TINFO = "Turn OFF DCM 'emi_dcm_emi_group'" */
		reg_write(EMI_CONM,
			(reg_read(EMI_CONM) &
			~EMI_DCM_EMI_GROUP_REG0_MASK) |
			EMI_DCM_EMI_GROUP_REG0_OFF);
		reg_write(EMI_CONN,
			(reg_read(EMI_CONN) &
			~EMI_DCM_EMI_GROUP_REG1_MASK) |
			EMI_DCM_EMI_GROUP_REG1_OFF);
	}
}

#define DDRPHY0AO_DDRPHY_REG0_MASK ((0x1 << 8) | \
			(0x1 << 9) | \
			(0x1 << 10) | \
			(0x1 << 11) | \
			(0x1 << 12) | \
			(0x1 << 13) | \
			(0x1 << 14) | \
			(0x1 << 15) | \
			(0x1 << 16) | \
			(0x1 << 17) | \
			(0x1 << 19))
#define DDRPHY0AO_DDRPHY_REG1_MASK ((0x1 << 6) | \
			(0x1 << 7) | \
			(0x1 << 26))
#define DDRPHY0AO_DDRPHY_REG2_MASK ((0x1 << 26) | \
			(0x1 << 27))
#define DDRPHY0AO_DDRPHY_REG0_ON ((0x0 << 8) | \
			(0x0 << 9) | \
			(0x0 << 10) | \
			(0x0 << 11) | \
			(0x0 << 12) | \
			(0x0 << 13) | \
			(0x0 << 14) | \
			(0x0 << 15) | \
			(0x0 << 16) | \
			(0x0 << 17) | \
			(0x0 << 19))
#define DDRPHY0AO_DDRPHY_REG1_ON ((0x0 << 6) | \
			(0x0 << 7) | \
			(0x0 << 26))
#define DDRPHY0AO_DDRPHY_REG2_ON ((0x0 << 26) | \
			(0x0 << 27))
#define DDRPHY0AO_DDRPHY_REG0_OFF ((0x1 << 8) | \
			(0x1 << 9) | \
			(0x1 << 10) | \
			(0x1 << 11) | \
			(0x1 << 12) | \
			(0x1 << 13) | \
			(0x1 << 14) | \
			(0x1 << 15) | \
			(0x1 << 16) | \
			(0x1 << 17) | \
			(0x1 << 19))
#define DDRPHY0AO_DDRPHY_REG1_OFF ((0x1 << 6) | \
			(0x1 << 7) | \
			(0x0 << 26))
#define DDRPHY0AO_DDRPHY_REG2_OFF ((0x1 << 26) | \
			(0x1 << 27))
#if 0
static unsigned int ddrphy0ao_rg_mem_dcm_idle_fsel_get(void)
{
	return (reg_read(DDRPHY0AO_MISC_CG_CTRL2) >> 21) & 0x1f;
}
#endif
static void ddrphy0ao_rg_mem_dcm_idle_fsel_set(unsigned int val)
{
	reg_write(DDRPHY0AO_MISC_CG_CTRL2,
		(reg_read(DDRPHY0AO_MISC_CG_CTRL2) &
		~(0x1f << 21)) |
		(val & 0x1f) << 21);
}

bool dcm_ddrphy0ao_ddrphy_is_on(int on)
{
	bool ret = true;

	ret &= !!(reg_read(DDRPHY0AO_MISC_CG_CTRL0) &
		DDRPHY0AO_DDRPHY_REG0_MASK &
		DDRPHY0AO_DDRPHY_REG0_ON);
	ret &= !!(reg_read(DDRPHY0AO_MISC_CG_CTRL2) &
		DDRPHY0AO_DDRPHY_REG1_MASK &
		DDRPHY0AO_DDRPHY_REG1_ON);
	ret &= !!(reg_read(DDRPHY0AO_MISC_CTRL3) &
		DDRPHY0AO_DDRPHY_REG2_MASK &
		DDRPHY0AO_DDRPHY_REG2_ON);

	return ret;
}

void dcm_ddrphy0ao_ddrphy(int on)
{
	if (on) {
		/* TINFO = "Turn ON DCM 'ddrphy0ao_ddrphy'" */
		ddrphy0ao_rg_mem_dcm_idle_fsel_set(0x8);
		reg_write(DDRPHY0AO_MISC_CG_CTRL0,
			(reg_read(DDRPHY0AO_MISC_CG_CTRL0) &
			~DDRPHY0AO_DDRPHY_REG0_MASK) |
			DDRPHY0AO_DDRPHY_REG0_ON);
		reg_write(DDRPHY0AO_MISC_CG_CTRL2,
			(reg_read(DDRPHY0AO_MISC_CG_CTRL2) &
			~DDRPHY0AO_DDRPHY_REG1_MASK) |
			DDRPHY0AO_DDRPHY_REG1_ON);
		reg_write(DDRPHY0AO_MISC_CTRL3,
			(reg_read(DDRPHY0AO_MISC_CTRL3) &
			~DDRPHY0AO_DDRPHY_REG2_MASK) |
			DDRPHY0AO_DDRPHY_REG2_ON);
	} else {
		/* TINFO = "Turn OFF DCM 'ddrphy0ao_ddrphy'" */
		ddrphy0ao_rg_mem_dcm_idle_fsel_set(0x0);
		reg_write(DDRPHY0AO_MISC_CG_CTRL0,
			(reg_read(DDRPHY0AO_MISC_CG_CTRL0) &
			~DDRPHY0AO_DDRPHY_REG0_MASK) |
			DDRPHY0AO_DDRPHY_REG0_OFF);
		reg_write(DDRPHY0AO_MISC_CG_CTRL2,
			(reg_read(DDRPHY0AO_MISC_CG_CTRL2) &
			~DDRPHY0AO_DDRPHY_REG1_MASK) |
			DDRPHY0AO_DDRPHY_REG1_OFF);
		reg_write(DDRPHY0AO_MISC_CTRL3,
			(reg_read(DDRPHY0AO_MISC_CTRL3) &
			~DDRPHY0AO_DDRPHY_REG2_MASK) |
			DDRPHY0AO_DDRPHY_REG2_OFF);
	}
}

#define DRAMC0_AO_DRAMC_DCM_REG0_MASK ((0x1 << 0) | \
			(0x1 << 1) | \
			(0x1 << 2) | \
			(0x1 << 26) | \
			(0x1 << 30) | \
			(0x1 << 31))
#define DRAMC0_AO_DRAMC_DCM_REG1_MASK ((0x1 << 31))
#define DRAMC0_AO_DRAMC_DCM_REG0_ON ((0x1 << 0) | \
			(0x1 << 1) | \
			(0x1 << 2) | \
			(0x0 << 26) | \
			(0x1 << 30) | \
			(0x1 << 31))
#define DRAMC0_AO_DRAMC_DCM_REG1_ON ((0x1 << 31))
#define DRAMC0_AO_DRAMC_DCM_REG0_OFF ((0x0 << 0) | \
			(0x0 << 1) | \
			(0x0 << 2) | \
			(0x1 << 26) | \
			(0x0 << 30) | \
			(0x0 << 31))
#define DRAMC0_AO_DRAMC_DCM_REG1_OFF ((0x0 << 31))

bool dcm_dramc0_ao_dramc_dcm_is_on(int on)
{
	bool ret = true;

	ret &= !!(reg_read(DRAMC0_AO_DRAMC_PD_CTRL) &
		DRAMC0_AO_DRAMC_DCM_REG0_MASK &
		DRAMC0_AO_DRAMC_DCM_REG0_ON);
	ret &= !!(reg_read(DRAMC0_AO_CLKAR) &
		DRAMC0_AO_DRAMC_DCM_REG1_MASK &
		DRAMC0_AO_DRAMC_DCM_REG1_ON);

	return ret;
}

void dcm_dramc0_ao_dramc_dcm(int on)
{
	if (on) {
		/* TINFO = "Turn ON DCM 'dramc0_ao_dramc_dcm'" */
		reg_write(DRAMC0_AO_DRAMC_PD_CTRL,
			(reg_read(DRAMC0_AO_DRAMC_PD_CTRL) &
			~DRAMC0_AO_DRAMC_DCM_REG0_MASK) |
			DRAMC0_AO_DRAMC_DCM_REG0_ON);
		reg_write(DRAMC0_AO_CLKAR,
			(reg_read(DRAMC0_AO_CLKAR) &
			~DRAMC0_AO_DRAMC_DCM_REG1_MASK) |
			DRAMC0_AO_DRAMC_DCM_REG1_ON);
	} else {
		/* TINFO = "Turn OFF DCM 'dramc0_ao_dramc_dcm'" */
		reg_write(DRAMC0_AO_DRAMC_PD_CTRL,
			(reg_read(DRAMC0_AO_DRAMC_PD_CTRL) &
			~DRAMC0_AO_DRAMC_DCM_REG0_MASK) |
			DRAMC0_AO_DRAMC_DCM_REG0_OFF);
		reg_write(DRAMC0_AO_CLKAR,
			(reg_read(DRAMC0_AO_CLKAR) &
			~DRAMC0_AO_DRAMC_DCM_REG1_MASK) |
			DRAMC0_AO_DRAMC_DCM_REG1_OFF);
	}
}

#define CHN0_EMI_DCM_EMI_GROUP_REG0_MASK ((0xff << 24))
#define CHN0_EMI_DCM_EMI_GROUP_REG0_ON ((0x0 << 24))
#define CHN0_EMI_DCM_EMI_GROUP_REG0_OFF ((0xff << 24))

bool dcm_chn0_emi_dcm_emi_group_is_on(int on)
{
	bool ret = true;

	ret &= !!(reg_read(CHN0_EMI_CHN_EMI_CONB) &
		CHN0_EMI_DCM_EMI_GROUP_REG0_MASK &
		CHN0_EMI_DCM_EMI_GROUP_REG0_ON);

	return ret;
}

void dcm_chn0_emi_dcm_emi_group(int on)
{
	if (on) {
		/* TINFO = "Turn ON DCM 'chn0_emi_dcm_emi_group'" */
		reg_write(CHN0_EMI_CHN_EMI_CONB,
			(reg_read(CHN0_EMI_CHN_EMI_CONB) &
			~CHN0_EMI_DCM_EMI_GROUP_REG0_MASK) |
			CHN0_EMI_DCM_EMI_GROUP_REG0_ON);
	} else {
		/* TINFO = "Turn OFF DCM 'chn0_emi_dcm_emi_group'" */
		reg_write(CHN0_EMI_CHN_EMI_CONB,
			(reg_read(CHN0_EMI_CHN_EMI_CONB) &
			~CHN0_EMI_DCM_EMI_GROUP_REG0_MASK) |
			CHN0_EMI_DCM_EMI_GROUP_REG0_OFF);
	}
}

#define DDRPHY1AO_DDRPHY_REG0_MASK ((0x1 << 8) | \
			(0x1 << 9) | \
			(0x1 << 10) | \
			(0x1 << 11) | \
			(0x1 << 12) | \
			(0x1 << 13) | \
			(0x1 << 14) | \
			(0x1 << 15) | \
			(0x1 << 16) | \
			(0x1 << 17) | \
			(0x1 << 19))
#define DDRPHY1AO_DDRPHY_REG1_MASK ((0x1 << 6) | \
			(0x1 << 7) | \
			(0x1 << 26))
#define DDRPHY1AO_DDRPHY_REG2_MASK ((0x1 << 26) | \
			(0x1 << 27))
#define DDRPHY1AO_DDRPHY_REG0_ON ((0x0 << 8) | \
			(0x0 << 9) | \
			(0x0 << 10) | \
			(0x0 << 11) | \
			(0x0 << 12) | \
			(0x0 << 13) | \
			(0x0 << 14) | \
			(0x0 << 15) | \
			(0x0 << 16) | \
			(0x0 << 17) | \
			(0x0 << 19))
#define DDRPHY1AO_DDRPHY_REG1_ON ((0x0 << 6) | \
			(0x0 << 7) | \
			(0x0 << 26))
#define DDRPHY1AO_DDRPHY_REG2_ON ((0x0 << 26) | \
			(0x0 << 27))
#define DDRPHY1AO_DDRPHY_REG0_OFF ((0x1 << 8) | \
			(0x1 << 9) | \
			(0x1 << 10) | \
			(0x1 << 11) | \
			(0x1 << 12) | \
			(0x1 << 13) | \
			(0x1 << 14) | \
			(0x1 << 15) | \
			(0x1 << 16) | \
			(0x1 << 17) | \
			(0x1 << 19))
#define DDRPHY1AO_DDRPHY_REG1_OFF ((0x1 << 6) | \
			(0x1 << 7) | \
			(0x0 << 26))
#define DDRPHY1AO_DDRPHY_REG2_OFF ((0x1 << 26) | \
			(0x1 << 27))
#if 0
static unsigned int ddrphy1ao_rg_mem_dcm_idle_fsel_get(void)
{
	return (reg_read(DDRPHY1AO_MISC_CG_CTRL2) >> 21) & 0x1f;
}
#endif
static void ddrphy1ao_rg_mem_dcm_idle_fsel_set(unsigned int val)
{
	reg_write(DDRPHY1AO_MISC_CG_CTRL2,
		(reg_read(DDRPHY1AO_MISC_CG_CTRL2) &
		~(0x1f << 21)) |
		(val & 0x1f) << 21);
}

bool dcm_ddrphy1ao_ddrphy_is_on(int on)
{
	bool ret = true;

	ret &= !!(reg_read(DDRPHY1AO_MISC_CG_CTRL0) &
		DDRPHY1AO_DDRPHY_REG0_MASK &
		DDRPHY1AO_DDRPHY_REG0_ON);
	ret &= !!(reg_read(DDRPHY1AO_MISC_CG_CTRL2) &
		DDRPHY1AO_DDRPHY_REG1_MASK &
		DDRPHY1AO_DDRPHY_REG1_ON);
	ret &= !!(reg_read(DDRPHY1AO_MISC_CTRL3) &
		DDRPHY1AO_DDRPHY_REG2_MASK &
		DDRPHY1AO_DDRPHY_REG2_ON);

	return ret;
}

void dcm_ddrphy1ao_ddrphy(int on)
{
	if (on) {
		/* TINFO = "Turn ON DCM 'ddrphy1ao_ddrphy'" */
		ddrphy1ao_rg_mem_dcm_idle_fsel_set(0x8);
		reg_write(DDRPHY1AO_MISC_CG_CTRL0,
			(reg_read(DDRPHY1AO_MISC_CG_CTRL0) &
			~DDRPHY1AO_DDRPHY_REG0_MASK) |
			DDRPHY1AO_DDRPHY_REG0_ON);
		reg_write(DDRPHY1AO_MISC_CG_CTRL2,
			(reg_read(DDRPHY1AO_MISC_CG_CTRL2) &
			~DDRPHY1AO_DDRPHY_REG1_MASK) |
			DDRPHY1AO_DDRPHY_REG1_ON);
		reg_write(DDRPHY1AO_MISC_CTRL3,
			(reg_read(DDRPHY1AO_MISC_CTRL3) &
			~DDRPHY1AO_DDRPHY_REG2_MASK) |
			DDRPHY1AO_DDRPHY_REG2_ON);
	} else {
		/* TINFO = "Turn OFF DCM 'ddrphy1ao_ddrphy'" */
		ddrphy1ao_rg_mem_dcm_idle_fsel_set(0x0);
		reg_write(DDRPHY1AO_MISC_CG_CTRL0,
			(reg_read(DDRPHY1AO_MISC_CG_CTRL0) &
			~DDRPHY1AO_DDRPHY_REG0_MASK) |
			DDRPHY1AO_DDRPHY_REG0_OFF);
		reg_write(DDRPHY1AO_MISC_CG_CTRL2,
			(reg_read(DDRPHY1AO_MISC_CG_CTRL2) &
			~DDRPHY1AO_DDRPHY_REG1_MASK) |
			DDRPHY1AO_DDRPHY_REG1_OFF);
		reg_write(DDRPHY1AO_MISC_CTRL3,
			(reg_read(DDRPHY1AO_MISC_CTRL3) &
			~DDRPHY1AO_DDRPHY_REG2_MASK) |
			DDRPHY1AO_DDRPHY_REG2_OFF);
	}
}

#define DRAMC1_AO_DRAMC_DCM_REG0_MASK ((0x1 << 0) | \
			(0x1 << 1) | \
			(0x1 << 2) | \
			(0x1 << 26) | \
			(0x1 << 30) | \
			(0x1 << 31))
#define DRAMC1_AO_DRAMC_DCM_REG1_MASK ((0x1 << 31))
#define DRAMC1_AO_DRAMC_DCM_REG0_ON ((0x1 << 0) | \
			(0x1 << 1) | \
			(0x1 << 2) | \
			(0x0 << 26) | \
			(0x1 << 30) | \
			(0x1 << 31))
#define DRAMC1_AO_DRAMC_DCM_REG1_ON ((0x1 << 31))
#define DRAMC1_AO_DRAMC_DCM_REG0_OFF ((0x0 << 0) | \
			(0x0 << 1) | \
			(0x0 << 2) | \
			(0x1 << 26) | \
			(0x0 << 30) | \
			(0x0 << 31))
#define DRAMC1_AO_DRAMC_DCM_REG1_OFF ((0x0 << 31))

bool dcm_dramc1_ao_dramc_dcm_is_on(int on)
{
	bool ret = true;

	ret &= !!(reg_read(DRAMC1_AO_DRAMC_PD_CTRL) &
		DRAMC1_AO_DRAMC_DCM_REG0_MASK &
		DRAMC1_AO_DRAMC_DCM_REG0_ON);
	ret &= !!(reg_read(DRAMC1_AO_CLKAR) &
		DRAMC1_AO_DRAMC_DCM_REG1_MASK &
		DRAMC1_AO_DRAMC_DCM_REG1_ON);

	return ret;
}

void dcm_dramc1_ao_dramc_dcm(int on)
{
	if (on) {
		/* TINFO = "Turn ON DCM 'dramc1_ao_dramc_dcm'" */
		reg_write(DRAMC1_AO_DRAMC_PD_CTRL,
			(reg_read(DRAMC1_AO_DRAMC_PD_CTRL) &
			~DRAMC1_AO_DRAMC_DCM_REG0_MASK) |
			DRAMC1_AO_DRAMC_DCM_REG0_ON);
		reg_write(DRAMC1_AO_CLKAR,
			(reg_read(DRAMC1_AO_CLKAR) &
			~DRAMC1_AO_DRAMC_DCM_REG1_MASK) |
			DRAMC1_AO_DRAMC_DCM_REG1_ON);
	} else {
		/* TINFO = "Turn OFF DCM 'dramc1_ao_dramc_dcm'" */
		reg_write(DRAMC1_AO_DRAMC_PD_CTRL,
			(reg_read(DRAMC1_AO_DRAMC_PD_CTRL) &
			~DRAMC1_AO_DRAMC_DCM_REG0_MASK) |
			DRAMC1_AO_DRAMC_DCM_REG0_OFF);
		reg_write(DRAMC1_AO_CLKAR,
			(reg_read(DRAMC1_AO_CLKAR) &
			~DRAMC1_AO_DRAMC_DCM_REG1_MASK) |
			DRAMC1_AO_DRAMC_DCM_REG1_OFF);
	}
}

#define CHN1_EMI_DCM_EMI_GROUP_REG0_MASK ((0xff << 24))
#define CHN1_EMI_DCM_EMI_GROUP_REG0_ON ((0x0 << 24))
#define CHN1_EMI_DCM_EMI_GROUP_REG0_OFF ((0xff << 24))

bool dcm_chn1_emi_dcm_emi_group_is_on(int on)
{
	bool ret = true;

	ret &= !!(reg_read(CHN1_EMI_CHN_EMI_CONB) &
		CHN1_EMI_DCM_EMI_GROUP_REG0_MASK &
		CHN1_EMI_DCM_EMI_GROUP_REG0_ON);

	return ret;
}

void dcm_chn1_emi_dcm_emi_group(int on)
{
	if (on) {
		/* TINFO = "Turn ON DCM 'chn1_emi_dcm_emi_group'" */
		reg_write(CHN1_EMI_CHN_EMI_CONB,
			(reg_read(CHN1_EMI_CHN_EMI_CONB) &
			~CHN1_EMI_DCM_EMI_GROUP_REG0_MASK) |
			CHN1_EMI_DCM_EMI_GROUP_REG0_ON);
	} else {
		/* TINFO = "Turn OFF DCM 'chn1_emi_dcm_emi_group'" */
		reg_write(CHN1_EMI_CHN_EMI_CONB,
			(reg_read(CHN1_EMI_CHN_EMI_CONB) &
			~CHN1_EMI_DCM_EMI_GROUP_REG0_MASK) |
			CHN1_EMI_DCM_EMI_GROUP_REG0_OFF);
	}
}

#define PERICFG_EMIBIU_REG0_MASK ((0x1 << 14) | \
			(0x1 << 15) | \
			(0x1 << 16) | \
			(0x1 << 17))
#define PERICFG_EMIBIU_REG0_ON ((0x0 << 14) | \
			(0x1 << 15) | \
			(0x0 << 16) | \
			(0x0 << 17))
#define PERICFG_EMIBIU_REG0_OFF ((0x0 << 14) | \
			(0x0 << 15) | \
			(0x0 << 16) | \
			(0x0 << 17))
#if 0
static unsigned int pericfg_dcm_emi_group_biu_rg_sfsel_get(void)
{
	return (reg_read(PERICFG_PERI_BIU_EMI_DCM_CTRL) >> 23) & 0x1f;
}
#endif
static void pericfg_dcm_emi_group_biu_rg_sfsel_set(unsigned int val)
{
	reg_write(PERICFG_PERI_BIU_EMI_DCM_CTRL,
		(reg_read(PERICFG_PERI_BIU_EMI_DCM_CTRL) &
		~(0x1f << 23)) |
		(val & 0x1f) << 23);
}

bool dcm_pericfg_emibiu_is_on(int on)
{
	bool ret = true;

	ret &= !!(reg_read(PERICFG_PERI_BIU_EMI_DCM_CTRL) &
		PERICFG_EMIBIU_REG0_MASK &
		PERICFG_EMIBIU_REG0_ON);

	return ret;
}

void dcm_pericfg_emibiu(int on)
{
	if (on) {
		/* TINFO = "Turn ON DCM 'pericfg_emibiu'" */
		pericfg_dcm_emi_group_biu_rg_sfsel_set(0xf);
		reg_write(PERICFG_PERI_BIU_EMI_DCM_CTRL,
			(reg_read(PERICFG_PERI_BIU_EMI_DCM_CTRL) &
			~PERICFG_EMIBIU_REG0_MASK) |
			PERICFG_EMIBIU_REG0_ON);
	} else {
		/* TINFO = "Turn OFF DCM 'pericfg_emibiu'" */
		pericfg_dcm_emi_group_biu_rg_sfsel_set(0xf);
		reg_write(PERICFG_PERI_BIU_EMI_DCM_CTRL,
			(reg_read(PERICFG_PERI_BIU_EMI_DCM_CTRL) &
			~PERICFG_EMIBIU_REG0_MASK) |
			PERICFG_EMIBIU_REG0_OFF);
	}
}

#define PERICFG_EMIBUS_REG0_MASK ((0x1 << 0) | \
			(0x1 << 1) | \
			(0x1 << 2) | \
			(0x1 << 3))
#define PERICFG_EMIBUS_REG0_ON ((0x1 << 0) | \
			(0x0 << 1) | \
			(0x0 << 2) | \
			(0x0 << 3))
#define PERICFG_EMIBUS_REG0_OFF ((0x0 << 0) | \
			(0x0 << 1) | \
			(0x0 << 2) | \
			(0x0 << 3))
#if 0
static unsigned int pericfg_dcm_emi_group_bus_rg_sfsel_get(void)
{
	return (reg_read(PERICFG_PERI_BIU_EMI_DCM_CTRL) >> 9) & 0x1f;
}
#endif
static void pericfg_dcm_emi_group_bus_rg_sfsel_set(unsigned int val)
{
	reg_write(PERICFG_PERI_BIU_EMI_DCM_CTRL,
		(reg_read(PERICFG_PERI_BIU_EMI_DCM_CTRL) &
		~(0x1f << 9)) |
		(val & 0x1f) << 9);
}

bool dcm_pericfg_emibus_is_on(int on)
{
	bool ret = true;

	ret &= !!(reg_read(PERICFG_PERI_BIU_EMI_DCM_CTRL) &
		PERICFG_EMIBUS_REG0_MASK &
		PERICFG_EMIBUS_REG0_ON);

	return ret;
}

void dcm_pericfg_emibus(int on)
{
	if (on) {
		/* TINFO = "Turn ON DCM 'pericfg_emibus'" */
		pericfg_dcm_emi_group_bus_rg_sfsel_set(0x1f);
		reg_write(PERICFG_PERI_BIU_EMI_DCM_CTRL,
			(reg_read(PERICFG_PERI_BIU_EMI_DCM_CTRL) &
			~PERICFG_EMIBUS_REG0_MASK) |
			PERICFG_EMIBUS_REG0_ON);
	} else {
		/* TINFO = "Turn OFF DCM 'pericfg_emibus'" */
		pericfg_dcm_emi_group_bus_rg_sfsel_set(0x1f);
		reg_write(PERICFG_PERI_BIU_EMI_DCM_CTRL,
			(reg_read(PERICFG_PERI_BIU_EMI_DCM_CTRL) &
			~PERICFG_EMIBUS_REG0_MASK) |
			PERICFG_EMIBUS_REG0_OFF);
	}
}

#define PERICFG_REGBIU_REG0_MASK ((0x1 << 14) | \
			(0x1 << 15) | \
			(0x1 << 16) | \
			(0x1 << 17))
#define PERICFG_REGBIU_REG0_ON ((0x1 << 14) | \
			(0x1 << 15) | \
			(0x0 << 16) | \
			(0x0 << 17))
#define PERICFG_REGBIU_REG0_OFF ((0x0 << 14) | \
			(0x0 << 15) | \
			(0x0 << 16) | \
			(0x0 << 17))
#if 0
static unsigned int pericfg_dcm_reg_group_biu_rg_sfsel_get(void)
{
	return (reg_read(PERICFG_PERI_BIU_REG_DCM_CTRL) >> 23) & 0x1f;
}
#endif
static void pericfg_dcm_reg_group_biu_rg_sfsel_set(unsigned int val)
{
	reg_write(PERICFG_PERI_BIU_REG_DCM_CTRL,
		(reg_read(PERICFG_PERI_BIU_REG_DCM_CTRL) &
		~(0x1f << 23)) |
		(val & 0x1f) << 23);
}

bool dcm_pericfg_regbiu_is_on(int on)
{
	bool ret = true;

	ret &= !!(reg_read(PERICFG_PERI_BIU_REG_DCM_CTRL) &
		PERICFG_REGBIU_REG0_MASK &
		PERICFG_REGBIU_REG0_ON);

	return ret;
}

void dcm_pericfg_regbiu(int on)
{
	if (on) {
		/* TINFO = "Turn ON DCM 'pericfg_regbiu'" */
		pericfg_dcm_reg_group_biu_rg_sfsel_set(0xf);
		reg_write(PERICFG_PERI_BIU_REG_DCM_CTRL,
			(reg_read(PERICFG_PERI_BIU_REG_DCM_CTRL) &
			~PERICFG_REGBIU_REG0_MASK) |
			PERICFG_REGBIU_REG0_ON);
	} else {
		/* TINFO = "Turn OFF DCM 'pericfg_regbiu'" */
		pericfg_dcm_reg_group_biu_rg_sfsel_set(0xf);
		reg_write(PERICFG_PERI_BIU_REG_DCM_CTRL,
			(reg_read(PERICFG_PERI_BIU_REG_DCM_CTRL) &
			~PERICFG_REGBIU_REG0_MASK) |
			PERICFG_REGBIU_REG0_OFF);
	}
}

#define PERICFG_REGBUS_REG0_MASK ((0x1 << 0) | \
			(0x1 << 1) | \
			(0x1 << 2) | \
			(0x1 << 3))
#define PERICFG_REGBUS_REG0_ON ((0x1 << 0) | \
			(0x1 << 1) | \
			(0x0 << 2) | \
			(0x0 << 3))
#define PERICFG_REGBUS_REG0_OFF ((0x0 << 0) | \
			(0x0 << 1) | \
			(0x0 << 2) | \
			(0x0 << 3))
#if 0
static unsigned int pericfg_dcm_reg_group_bus_rg_sfsel_get(void)
{
	return (reg_read(PERICFG_PERI_BIU_REG_DCM_CTRL) >> 9) & 0x1f;
}
#endif
static void pericfg_dcm_reg_group_bus_rg_sfsel_set(unsigned int val)
{
	reg_write(PERICFG_PERI_BIU_REG_DCM_CTRL,
		(reg_read(PERICFG_PERI_BIU_REG_DCM_CTRL) &
		~(0x1f << 9)) |
		(val & 0x1f) << 9);
}

bool dcm_pericfg_regbus_is_on(int on)
{
	bool ret = true;

	ret &= !!(reg_read(PERICFG_PERI_BIU_REG_DCM_CTRL) &
		PERICFG_REGBUS_REG0_MASK &
		PERICFG_REGBUS_REG0_ON);

	return ret;
}

void dcm_pericfg_regbus(int on)
{
	if (on) {
		/* TINFO = "Turn ON DCM 'pericfg_regbus'" */
		pericfg_dcm_reg_group_bus_rg_sfsel_set(0xf);
		reg_write(PERICFG_PERI_BIU_REG_DCM_CTRL,
			(reg_read(PERICFG_PERI_BIU_REG_DCM_CTRL) &
			~PERICFG_REGBUS_REG0_MASK) |
			PERICFG_REGBUS_REG0_ON);
	} else {
		/* TINFO = "Turn OFF DCM 'pericfg_regbus'" */
		pericfg_dcm_reg_group_bus_rg_sfsel_set(0xf);
		reg_write(PERICFG_PERI_BIU_REG_DCM_CTRL,
			(reg_read(PERICFG_PERI_BIU_REG_DCM_CTRL) &
			~PERICFG_REGBUS_REG0_MASK) |
			PERICFG_REGBUS_REG0_OFF);
	}
}

#define VENC_VENC_REG0_MASK ((0x1 << 0))
#define VENC_VENC_REG1_MASK ((0x1 << 0))
#define VENC_VENC_REG2_MASK ((0xffffffff << 0))
#define VENC_VENC_REG0_ON ((0x0 << 0))
#define VENC_VENC_REG1_ON ((0x1 << 0))
#define VENC_VENC_REG2_ON ((0xffffffff << 0))
#define VENC_VENC_REG0_OFF ((0x0 << 0))
#define VENC_VENC_REG1_OFF ((0x0 << 0))
#define VENC_VENC_REG2_OFF ((0x0 << 0))

bool dcm_venc_venc_is_on(int on)
{
	bool ret = true;

	ret &= !!(reg_read(VENC_VENC_CE) &
		VENC_VENC_REG0_MASK &
		VENC_VENC_REG0_ON);
	ret &= !!(reg_read(VENC_VENC_CLK_DCM_CTRL) &
		VENC_VENC_REG1_MASK &
		VENC_VENC_REG1_ON);
	ret &= !!(reg_read(VENC_VENC_CLK_CG_CTRL) &
		VENC_VENC_REG2_MASK &
		VENC_VENC_REG2_ON);

	return ret;
}

void dcm_venc_venc(int on)
{
	if (on) {
		/* TINFO = "Turn ON DCM 'venc_venc'" */
		reg_write(VENC_VENC_CE,
			(reg_read(VENC_VENC_CE) &
			~VENC_VENC_REG0_MASK) |
			VENC_VENC_REG0_ON);
		reg_write(VENC_VENC_CLK_DCM_CTRL,
			(reg_read(VENC_VENC_CLK_DCM_CTRL) &
			~VENC_VENC_REG1_MASK) |
			VENC_VENC_REG1_ON);
		reg_write(VENC_VENC_CLK_CG_CTRL,
			(reg_read(VENC_VENC_CLK_CG_CTRL) &
			~VENC_VENC_REG2_MASK) |
			VENC_VENC_REG2_ON);
	} else {
		/* TINFO = "Turn OFF DCM 'venc_venc'" */
		reg_write(VENC_VENC_CE,
			(reg_read(VENC_VENC_CE) &
			~VENC_VENC_REG0_MASK) |
			VENC_VENC_REG0_OFF);
		reg_write(VENC_VENC_CLK_DCM_CTRL,
			(reg_read(VENC_VENC_CLK_DCM_CTRL) &
			~VENC_VENC_REG1_MASK) |
			VENC_VENC_REG1_OFF);
		reg_write(VENC_VENC_CLK_CG_CTRL,
			(reg_read(VENC_VENC_CLK_CG_CTRL) &
			~VENC_VENC_REG2_MASK) |
			VENC_VENC_REG2_OFF);
	}
}

#define MP0_CPUCFG_MP0_RGU_DCM_REG0_MASK ((0x1 << 0))
#define MP0_CPUCFG_MP0_RGU_DCM_REG0_ON ((0x1 << 0))
#define MP0_CPUCFG_MP0_RGU_DCM_REG0_OFF ((0x0 << 0))

bool dcm_mp0_cpucfg_mp0_rgu_dcm_is_on(int on)
{
	bool ret = true;

	ret &= !!(reg_read(MP0_CPUSYS_RGU_SYNC_DCM) &
		MP0_CPUCFG_MP0_RGU_DCM_REG0_MASK &
		MP0_CPUCFG_MP0_RGU_DCM_REG0_ON);

	return ret;
}

void dcm_mp0_cpucfg_mp0_rgu_dcm(int on)
{
	if (on) {
		/* TINFO = "Turn ON DCM 'mp0_cpucfg_mp0_rgu_dcm'" */
		reg_write(MP0_CPUSYS_RGU_SYNC_DCM,
			(reg_read(MP0_CPUSYS_RGU_SYNC_DCM) &
			~MP0_CPUCFG_MP0_RGU_DCM_REG0_MASK) |
			MP0_CPUCFG_MP0_RGU_DCM_REG0_ON);
	} else {
		/* TINFO = "Turn OFF DCM 'mp0_cpucfg_mp0_rgu_dcm'" */
		reg_write(MP0_CPUSYS_RGU_SYNC_DCM,
			(reg_read(MP0_CPUSYS_RGU_SYNC_DCM) &
			~MP0_CPUCFG_MP0_RGU_DCM_REG0_MASK) |
			MP0_CPUCFG_MP0_RGU_DCM_REG0_OFF);
	}
}

#define MCU_MISCCFG_ADB400_DCM_REG0_MASK ((0x1 << 0) | \
			(0x1 << 1) | \
			(0x1 << 2) | \
			(0x1 << 3) | \
			(0x1 << 4) | \
			(0x1 << 5) | \
			(0x1 << 6) | \
			(0x1 << 11))
#define MCU_MISCCFG_ADB400_DCM_REG0_ON ((0x1 << 0) | \
			(0x1 << 1) | \
			(0x1 << 2) | \
			(0x1 << 3) | \
			(0x1 << 4) | \
			(0x1 << 5) | \
			(0x1 << 6) | \
			(0x1 << 11))
#define MCU_MISCCFG_ADB400_DCM_REG0_OFF ((0x0 << 0) | \
			(0x0 << 1) | \
			(0x0 << 2) | \
			(0x0 << 3) | \
			(0x0 << 4) | \
			(0x0 << 5) | \
			(0x0 << 6) | \
			(0x0 << 11))

bool dcm_mcu_misccfg_adb400_dcm_is_on(int on)
{
	bool ret = true;

	ret &= !!(reg_read(CCI_ADB400_DCM_CONFIG) &
		MCU_MISCCFG_ADB400_DCM_REG0_MASK &
		MCU_MISCCFG_ADB400_DCM_REG0_ON);

	return ret;
}

void dcm_mcu_misccfg_adb400_dcm(int on)
{
	if (on) {
		/* TINFO = "Turn ON DCM 'mcu_misccfg_adb400_dcm'" */
		reg_write(CCI_ADB400_DCM_CONFIG,
			(reg_read(CCI_ADB400_DCM_CONFIG) &
			~MCU_MISCCFG_ADB400_DCM_REG0_MASK) |
			MCU_MISCCFG_ADB400_DCM_REG0_ON);
	} else {
		/* TINFO = "Turn OFF DCM 'mcu_misccfg_adb400_dcm'" */
		reg_write(CCI_ADB400_DCM_CONFIG,
			(reg_read(CCI_ADB400_DCM_CONFIG) &
			~MCU_MISCCFG_ADB400_DCM_REG0_MASK) |
			MCU_MISCCFG_ADB400_DCM_REG0_OFF);
	}
}

#define MCU_MISCCFG_BUS_ARM_PLL_DIVIDER_DCM_REG0_MASK ((0x1 << 11) | \
			(0x1 << 24) | \
			(0x1 << 25))
#define MCU_MISCCFG_BUS_ARM_PLL_DIVIDER_DCM_REG0_ON ((0x1 << 11) | \
			(0x1 << 24) | \
			(0x1 << 25))
#define MCU_MISCCFG_BUS_ARM_PLL_DIVIDER_DCM_REG0_OFF ((0x0 << 11) | \
			(0x0 << 24) | \
			(0x0 << 25))

bool dcm_mcu_misccfg_bus_arm_pll_divider_dcm_is_on(int on)
{
	bool ret = true;

	ret &= !!(reg_read(BUS_PLL_DIVIDER_CFG) &
		MCU_MISCCFG_BUS_ARM_PLL_DIVIDER_DCM_REG0_MASK &
		MCU_MISCCFG_BUS_ARM_PLL_DIVIDER_DCM_REG0_ON);

	return ret;
}

void dcm_mcu_misccfg_bus_arm_pll_divider_dcm(int on)
{
	if (on) {
		/* TINFO = "Turn ON DCM 'mcu_misccfg_bus_arm_pll_divider_dcm'" */
		reg_write(BUS_PLL_DIVIDER_CFG,
			(reg_read(BUS_PLL_DIVIDER_CFG) &
			~MCU_MISCCFG_BUS_ARM_PLL_DIVIDER_DCM_REG0_MASK) |
			MCU_MISCCFG_BUS_ARM_PLL_DIVIDER_DCM_REG0_ON);
	} else {
		/* TINFO = "Turn OFF DCM 'mcu_misccfg_bus_arm_pll_divider_dcm'" */
		reg_write(BUS_PLL_DIVIDER_CFG,
			(reg_read(BUS_PLL_DIVIDER_CFG) &
			~MCU_MISCCFG_BUS_ARM_PLL_DIVIDER_DCM_REG0_MASK) |
			MCU_MISCCFG_BUS_ARM_PLL_DIVIDER_DCM_REG0_OFF);
	}
}

#define MCU_MISCCFG_BUS_SYNC_DCM_REG0_MASK ((0x1 << 0))
#define MCU_MISCCFG_BUS_SYNC_DCM_REG0_ON ((0x1 << 0))
#define MCU_MISCCFG_BUS_SYNC_DCM_REG0_OFF ((0x0 << 0))
#define MCU_MISCCFG_BUS_SYNC_DCM_REG0_TOG_MASK ((0x1 << 1))
#define MCU_MISCCFG_BUS_SYNC_DCM_REG0_TOG1 ((0x1 << 1))
#define MCU_MISCCFG_BUS_SYNC_DCM_REG0_TOG0 ((0x1 << 1))
#if 0
static unsigned int mcu_misccfg_cci_sync_dcm_div_sel_get(void)
{
	return (reg_read(SYNC_DCM_CONFIG) >> 2) & 0x7f;
}
#endif
static void mcu_misccfg_cci_sync_dcm_div_sel_set(unsigned int val)
{
	reg_write(SYNC_DCM_CONFIG,
		(reg_read(SYNC_DCM_CONFIG) &
		~(0x7f << 2)) |
		(val & 0x7f) << 2);
}

bool dcm_mcu_misccfg_bus_sync_dcm_is_on(int on)
{
	bool ret = true;

	ret &= !!(reg_read(SYNC_DCM_CONFIG) &
		MCU_MISCCFG_BUS_SYNC_DCM_REG0_MASK &
		MCU_MISCCFG_BUS_SYNC_DCM_REG0_ON);

	return ret;
}

void dcm_mcu_misccfg_bus_sync_dcm(int on)
{
	if (on) {
		/* TINFO = "Turn ON DCM 'mcu_misccfg_bus_sync_dcm'" */
		reg_write(SYNC_DCM_CONFIG,
			(reg_read(SYNC_DCM_CONFIG) &
			~MCU_MISCCFG_BUS_SYNC_DCM_REG0_TOG_MASK) |
			MCU_MISCCFG_BUS_SYNC_DCM_REG0_TOG0);
		mcu_misccfg_cci_sync_dcm_div_sel_set(0x0);
		reg_write(SYNC_DCM_CONFIG,
			(reg_read(SYNC_DCM_CONFIG) &
			~MCU_MISCCFG_BUS_SYNC_DCM_REG0_MASK) |
			MCU_MISCCFG_BUS_SYNC_DCM_REG0_ON);
		reg_write(SYNC_DCM_CONFIG,
			(reg_read(SYNC_DCM_CONFIG) &
			~MCU_MISCCFG_BUS_SYNC_DCM_REG0_TOG_MASK) |
			MCU_MISCCFG_BUS_SYNC_DCM_REG0_TOG1);
	} else {
		/* TINFO = "Turn OFF DCM 'mcu_misccfg_bus_sync_dcm'" */
		reg_write(SYNC_DCM_CONFIG,
			(reg_read(SYNC_DCM_CONFIG) &
			~MCU_MISCCFG_BUS_SYNC_DCM_REG0_TOG_MASK) |
			MCU_MISCCFG_BUS_SYNC_DCM_REG0_TOG0);
		mcu_misccfg_cci_sync_dcm_div_sel_set(0x0);
		reg_write(SYNC_DCM_CONFIG,
			(reg_read(SYNC_DCM_CONFIG) &
			~MCU_MISCCFG_BUS_SYNC_DCM_REG0_MASK) |
			MCU_MISCCFG_BUS_SYNC_DCM_REG0_OFF);
		reg_write(SYNC_DCM_CONFIG,
			(reg_read(SYNC_DCM_CONFIG) &
			~MCU_MISCCFG_BUS_SYNC_DCM_REG0_TOG_MASK) |
			MCU_MISCCFG_BUS_SYNC_DCM_REG0_TOG1);
	}
}

#define MCU_MISCCFG_BUS_CLOCK_DCM_REG0_MASK ((0x1 << 8))
#define MCU_MISCCFG_BUS_CLOCK_DCM_REG0_ON ((0x1 << 8))
#define MCU_MISCCFG_BUS_CLOCK_DCM_REG0_OFF ((0x0 << 8))

bool dcm_mcu_misccfg_bus_clock_dcm_is_on(int on)
{
	bool ret = true;

	ret &= !!(reg_read(CCI_CLK_CTRL) &
		MCU_MISCCFG_BUS_CLOCK_DCM_REG0_MASK &
		MCU_MISCCFG_BUS_CLOCK_DCM_REG0_ON);

	return ret;
}

void dcm_mcu_misccfg_bus_clock_dcm(int on)
{
	if (on) {
		/* TINFO = "Turn ON DCM 'mcu_misccfg_bus_clock_dcm'" */
		reg_write(CCI_CLK_CTRL,
			(reg_read(CCI_CLK_CTRL) &
			~MCU_MISCCFG_BUS_CLOCK_DCM_REG0_MASK) |
			MCU_MISCCFG_BUS_CLOCK_DCM_REG0_ON);
	} else {
		/* TINFO = "Turn OFF DCM 'mcu_misccfg_bus_clock_dcm'" */
		reg_write(CCI_CLK_CTRL,
			(reg_read(CCI_CLK_CTRL) &
			~MCU_MISCCFG_BUS_CLOCK_DCM_REG0_MASK) |
			MCU_MISCCFG_BUS_CLOCK_DCM_REG0_OFF);
	}
}

#define MCU_MISCCFG_BUS_FABRIC_DCM_REG0_MASK ((0x1 << 0) | \
			(0x1 << 1) | \
			(0x1 << 2) | \
			(0x1 << 3) | \
			(0x1 << 4) | \
			(0x1 << 5) | \
			(0x1 << 6) | \
			(0x1 << 7) | \
			(0x1 << 8) | \
			(0x1 << 9) | \
			(0x1 << 10) | \
			(0x1 << 11) | \
			(0x1 << 12) | \
			(0x1 << 16) | \
			(0x1 << 17) | \
			(0x1 << 18) | \
			(0x1 << 19) | \
			(0x1 << 20) | \
			(0x1 << 21) | \
			(0x1 << 22) | \
			(0x1 << 23) | \
			(0x1 << 24) | \
			(0x1 << 25) | \
			(0x1 << 26) | \
			(0x1 << 27) | \
			(0x1 << 28) | \
			(0x1 << 29))
#define MCU_MISCCFG_BUS_FABRIC_DCM_REG0_ON ((0x1 << 0) | \
			(0x1 << 1) | \
			(0x1 << 2) | \
			(0x1 << 3) | \
			(0x1 << 4) | \
			(0x1 << 5) | \
			(0x1 << 6) | \
			(0x1 << 7) | \
			(0x1 << 8) | \
			(0x1 << 9) | \
			(0x1 << 10) | \
			(0x1 << 11) | \
			(0x1 << 12) | \
			(0x1 << 16) | \
			(0x1 << 17) | \
			(0x1 << 18) | \
			(0x1 << 19) | \
			(0x1 << 20) | \
			(0x1 << 21) | \
			(0x1 << 22) | \
			(0x1 << 23) | \
			(0x1 << 24) | \
			(0x1 << 25) | \
			(0x1 << 26) | \
			(0x1 << 27) | \
			(0x1 << 28) | \
			(0x1 << 29))
#define MCU_MISCCFG_BUS_FABRIC_DCM_REG0_OFF ((0x0 << 0) | \
			(0x0 << 1) | \
			(0x0 << 2) | \
			(0x0 << 3) | \
			(0x0 << 4) | \
			(0x0 << 5) | \
			(0x0 << 6) | \
			(0x0 << 7) | \
			(0x0 << 8) | \
			(0x0 << 9) | \
			(0x0 << 10) | \
			(0x0 << 11) | \
			(0x0 << 12) | \
			(0x0 << 16) | \
			(0x0 << 17) | \
			(0x0 << 18) | \
			(0x0 << 19) | \
			(0x0 << 20) | \
			(0x0 << 21) | \
			(0x0 << 22) | \
			(0x0 << 23) | \
			(0x0 << 24) | \
			(0x0 << 25) | \
			(0x0 << 26) | \
			(0x0 << 27) | \
			(0x0 << 28) | \
			(0x0 << 29))

bool dcm_mcu_misccfg_bus_fabric_dcm_is_on(int on)
{
	bool ret = true;

	ret &= !!(reg_read(BUS_FABRIC_DCM_CTRL) &
		MCU_MISCCFG_BUS_FABRIC_DCM_REG0_MASK &
		MCU_MISCCFG_BUS_FABRIC_DCM_REG0_ON);

	return ret;
}

void dcm_mcu_misccfg_bus_fabric_dcm(int on)
{
	if (on) {
		/* TINFO = "Turn ON DCM 'mcu_misccfg_bus_fabric_dcm'" */
		reg_write(BUS_FABRIC_DCM_CTRL,
			(reg_read(BUS_FABRIC_DCM_CTRL) &
			~MCU_MISCCFG_BUS_FABRIC_DCM_REG0_MASK) |
			MCU_MISCCFG_BUS_FABRIC_DCM_REG0_ON);
	} else {
		/* TINFO = "Turn OFF DCM 'mcu_misccfg_bus_fabric_dcm'" */
		reg_write(BUS_FABRIC_DCM_CTRL,
			(reg_read(BUS_FABRIC_DCM_CTRL) &
			~MCU_MISCCFG_BUS_FABRIC_DCM_REG0_MASK) |
			MCU_MISCCFG_BUS_FABRIC_DCM_REG0_OFF);
	}
}

#define MCU_MISCCFG_GIC_SYNC_DCM_REG0_MASK ((0x1 << 0) | \
			(0x1 << 1))
#define MCU_MISCCFG_GIC_SYNC_DCM_REG0_ON ((0x1 << 0) | \
			(0x1 << 1))
#define MCU_MISCCFG_GIC_SYNC_DCM_REG0_OFF ((0x0 << 0) | \
			(0x0 << 1))

bool dcm_mcu_misccfg_gic_sync_dcm_is_on(int on)
{
	bool ret = true;

	ret &= !!(reg_read(MP_GIC_RGU_SYNC_DCM) &
		MCU_MISCCFG_GIC_SYNC_DCM_REG0_MASK &
		MCU_MISCCFG_GIC_SYNC_DCM_REG0_ON);

	return ret;
}

void dcm_mcu_misccfg_gic_sync_dcm(int on)
{
	if (on) {
		/* TINFO = "Turn ON DCM 'mcu_misccfg_gic_sync_dcm'" */
		reg_write(MP_GIC_RGU_SYNC_DCM,
			(reg_read(MP_GIC_RGU_SYNC_DCM) &
			~MCU_MISCCFG_GIC_SYNC_DCM_REG0_MASK) |
			MCU_MISCCFG_GIC_SYNC_DCM_REG0_ON);
	} else {
		/* TINFO = "Turn OFF DCM 'mcu_misccfg_gic_sync_dcm'" */
		reg_write(MP_GIC_RGU_SYNC_DCM,
			(reg_read(MP_GIC_RGU_SYNC_DCM) &
			~MCU_MISCCFG_GIC_SYNC_DCM_REG0_MASK) |
			MCU_MISCCFG_GIC_SYNC_DCM_REG0_OFF);
	}
}

#define MCU_MISCCFG_L2_SHARED_DCM_REG0_MASK ((0x1 << 0))
#define MCU_MISCCFG_L2_SHARED_DCM_REG0_ON ((0x1 << 0))
#define MCU_MISCCFG_L2_SHARED_DCM_REG0_OFF ((0x0 << 0))

bool dcm_mcu_misccfg_l2_shared_dcm_is_on(int on)
{
	bool ret = true;

	ret &= !!(reg_read(L2C_SRAM_CTRL) &
		MCU_MISCCFG_L2_SHARED_DCM_REG0_MASK &
		MCU_MISCCFG_L2_SHARED_DCM_REG0_ON);

	return ret;
}

void dcm_mcu_misccfg_l2_shared_dcm(int on)
{
	if (on) {
		/* TINFO = "Turn ON DCM 'mcu_misccfg_l2_shared_dcm'" */
		reg_write(L2C_SRAM_CTRL,
			(reg_read(L2C_SRAM_CTRL) &
			~MCU_MISCCFG_L2_SHARED_DCM_REG0_MASK) |
			MCU_MISCCFG_L2_SHARED_DCM_REG0_ON);
	} else {
		/* TINFO = "Turn OFF DCM 'mcu_misccfg_l2_shared_dcm'" */
		reg_write(L2C_SRAM_CTRL,
			(reg_read(L2C_SRAM_CTRL) &
			~MCU_MISCCFG_L2_SHARED_DCM_REG0_MASK) |
			MCU_MISCCFG_L2_SHARED_DCM_REG0_OFF);
	}
}

#define MCU_MISCCFG_MP0_ARM_PLL_DIVIDER_DCM_REG0_MASK ((0x1 << 11) | \
			(0x1 << 24) | \
			(0x1 << 25) | \
			(0x1 << 31))
#define MCU_MISCCFG_MP0_ARM_PLL_DIVIDER_DCM_REG0_ON ((0x1 << 11) | \
			(0x1 << 24) | \
			(0x1 << 25) | \
			(0x1 << 31))
#define MCU_MISCCFG_MP0_ARM_PLL_DIVIDER_DCM_REG0_OFF ((0x0 << 11) | \
			(0x0 << 24) | \
			(0x0 << 25) | \
			(0x0 << 31))

bool dcm_mcu_misccfg_mp0_arm_pll_divider_dcm_is_on(int on)
{
	bool ret = true;

	ret &= !!(reg_read(MP0_PLL_DIVIDER_CFG) &
		MCU_MISCCFG_MP0_ARM_PLL_DIVIDER_DCM_REG0_MASK &
		MCU_MISCCFG_MP0_ARM_PLL_DIVIDER_DCM_REG0_ON);

	return ret;
}

void dcm_mcu_misccfg_mp0_arm_pll_divider_dcm(int on)
{
	if (on) {
		/* TINFO = "Turn ON DCM 'mcu_misccfg_mp0_arm_pll_divider_dcm'" */
		reg_write(MP0_PLL_DIVIDER_CFG,
			(reg_read(MP0_PLL_DIVIDER_CFG) &
			~MCU_MISCCFG_MP0_ARM_PLL_DIVIDER_DCM_REG0_MASK) |
			MCU_MISCCFG_MP0_ARM_PLL_DIVIDER_DCM_REG0_ON);
	} else {
		/* TINFO = "Turn OFF DCM 'mcu_misccfg_mp0_arm_pll_divider_dcm'" */
		reg_write(MP0_PLL_DIVIDER_CFG,
			(reg_read(MP0_PLL_DIVIDER_CFG) &
			~MCU_MISCCFG_MP0_ARM_PLL_DIVIDER_DCM_REG0_MASK) |
			MCU_MISCCFG_MP0_ARM_PLL_DIVIDER_DCM_REG0_OFF);
	}
}

#define MCU_MISCCFG_MP0_STALL_DCM_REG0_MASK ((0x1 << 7))
#define MCU_MISCCFG_MP0_STALL_DCM_REG0_ON ((0x1 << 7))
#define MCU_MISCCFG_MP0_STALL_DCM_REG0_OFF ((0x0 << 7))

bool dcm_mcu_misccfg_mp0_stall_dcm_is_on(int on)
{
	bool ret = true;

	ret &= !!(reg_read(SYNC_DCM_CLUSTER_CONFIG) &
		MCU_MISCCFG_MP0_STALL_DCM_REG0_MASK &
		MCU_MISCCFG_MP0_STALL_DCM_REG0_ON);

	return ret;
}

void dcm_mcu_misccfg_mp0_stall_dcm(int on)
{
	if (on) {
		/* TINFO = "Turn ON DCM 'mcu_misccfg_mp0_stall_dcm'" */
		reg_write(SYNC_DCM_CLUSTER_CONFIG,
			(reg_read(SYNC_DCM_CLUSTER_CONFIG) &
			~MCU_MISCCFG_MP0_STALL_DCM_REG0_MASK) |
			MCU_MISCCFG_MP0_STALL_DCM_REG0_ON);
	} else {
		/* TINFO = "Turn OFF DCM 'mcu_misccfg_mp0_stall_dcm'" */
		reg_write(SYNC_DCM_CLUSTER_CONFIG,
			(reg_read(SYNC_DCM_CLUSTER_CONFIG) &
			~MCU_MISCCFG_MP0_STALL_DCM_REG0_MASK) |
			MCU_MISCCFG_MP0_STALL_DCM_REG0_OFF);
	}
}

#define MCU_MISCCFG_MP0_SYNC_DCM_ENABLE_REG0_MASK ((0x1 << 10))
#define MCU_MISCCFG_MP0_SYNC_DCM_ENABLE_REG0_ON ((0x1 << 10))
#define MCU_MISCCFG_MP0_SYNC_DCM_ENABLE_REG0_OFF ((0x0 << 10))
#define MCU_MISCCFG_MP0_SYNC_DCM_ENABLE_REG0_TOG_MASK ((0x1 << 11))
#define MCU_MISCCFG_MP0_SYNC_DCM_ENABLE_REG0_TOG1 ((0x1 << 11))
#define MCU_MISCCFG_MP0_SYNC_DCM_ENABLE_REG0_TOG0 ((0x1 << 11))
#if 0
static unsigned int mcu_misccfg_mp0_sync_dcm_div_sel_get(void)
{
	return (reg_read(SYNC_DCM_CONFIG) >> 12) & 0x7f;
}
#endif
static void mcu_misccfg_mp0_sync_dcm_div_sel_set(unsigned int val)
{
	reg_write(SYNC_DCM_CONFIG,
		(reg_read(SYNC_DCM_CONFIG) &
		~(0x7f << 12)) |
		(val & 0x7f) << 12);
}

bool dcm_mcu_misccfg_mp0_sync_dcm_enable_is_on(int on)
{
	bool ret = true;

	ret &= !!(reg_read(SYNC_DCM_CONFIG) &
		MCU_MISCCFG_MP0_SYNC_DCM_ENABLE_REG0_MASK &
		MCU_MISCCFG_MP0_SYNC_DCM_ENABLE_REG0_ON);

	return ret;
}

void dcm_mcu_misccfg_mp0_sync_dcm_enable(int on)
{
	if (on) {
		/* TINFO = "Turn ON DCM 'mcu_misccfg_mp0_sync_dcm_enable'" */
		reg_write(SYNC_DCM_CONFIG,
			(reg_read(SYNC_DCM_CONFIG) &
			~MCU_MISCCFG_MP0_SYNC_DCM_ENABLE_REG0_TOG_MASK) |
			MCU_MISCCFG_MP0_SYNC_DCM_ENABLE_REG0_TOG0);
		mcu_misccfg_mp0_sync_dcm_div_sel_set(0x0);
		reg_write(SYNC_DCM_CONFIG,
			(reg_read(SYNC_DCM_CONFIG) &
			~MCU_MISCCFG_MP0_SYNC_DCM_ENABLE_REG0_MASK) |
			MCU_MISCCFG_MP0_SYNC_DCM_ENABLE_REG0_ON);
		reg_write(SYNC_DCM_CONFIG,
			(reg_read(SYNC_DCM_CONFIG) &
			~MCU_MISCCFG_MP0_SYNC_DCM_ENABLE_REG0_TOG_MASK) |
			MCU_MISCCFG_MP0_SYNC_DCM_ENABLE_REG0_TOG1);
	} else {
		/* TINFO = "Turn OFF DCM 'mcu_misccfg_mp0_sync_dcm_enable'" */
		reg_write(SYNC_DCM_CONFIG,
			(reg_read(SYNC_DCM_CONFIG) &
			~MCU_MISCCFG_MP0_SYNC_DCM_ENABLE_REG0_TOG_MASK) |
			MCU_MISCCFG_MP0_SYNC_DCM_ENABLE_REG0_TOG0);
		mcu_misccfg_mp0_sync_dcm_div_sel_set(0x0);
		reg_write(SYNC_DCM_CONFIG,
			(reg_read(SYNC_DCM_CONFIG) &
			~MCU_MISCCFG_MP0_SYNC_DCM_ENABLE_REG0_MASK) |
			MCU_MISCCFG_MP0_SYNC_DCM_ENABLE_REG0_OFF);
		reg_write(SYNC_DCM_CONFIG,
			(reg_read(SYNC_DCM_CONFIG) &
			~MCU_MISCCFG_MP0_SYNC_DCM_ENABLE_REG0_TOG_MASK) |
			MCU_MISCCFG_MP0_SYNC_DCM_ENABLE_REG0_TOG1);
	}
}

#define MCU_MISCCFG_MP2_ARM_PLL_DIVIDER_DCM_REG0_MASK ((0x1 << 11) | \
			(0x1 << 24) | \
			(0x1 << 25) | \
			(0x1 << 31))
#define MCU_MISCCFG_MP2_ARM_PLL_DIVIDER_DCM_REG0_ON ((0x1 << 11) | \
			(0x1 << 24) | \
			(0x1 << 25) | \
			(0x1 << 31))
#define MCU_MISCCFG_MP2_ARM_PLL_DIVIDER_DCM_REG0_OFF ((0x0 << 11) | \
			(0x0 << 24) | \
			(0x0 << 25) | \
			(0x0 << 31))

bool dcm_mcu_misccfg_mp2_arm_pll_divider_dcm_is_on(int on)
{
	bool ret = true;

	ret &= !!(reg_read(MP2_PLL_DIVIDER_CFG) &
		MCU_MISCCFG_MP2_ARM_PLL_DIVIDER_DCM_REG0_MASK &
		MCU_MISCCFG_MP2_ARM_PLL_DIVIDER_DCM_REG0_ON);

	return ret;
}

void dcm_mcu_misccfg_mp2_arm_pll_divider_dcm(int on)
{
	if (on) {
		/* TINFO = "Turn ON DCM 'mcu_misccfg_mp2_arm_pll_divider_dcm'" */
		reg_write(MP2_PLL_DIVIDER_CFG,
			(reg_read(MP2_PLL_DIVIDER_CFG) &
			~MCU_MISCCFG_MP2_ARM_PLL_DIVIDER_DCM_REG0_MASK) |
			MCU_MISCCFG_MP2_ARM_PLL_DIVIDER_DCM_REG0_ON);
	} else {
		/* TINFO = "Turn OFF DCM 'mcu_misccfg_mp2_arm_pll_divider_dcm'" */
		reg_write(MP2_PLL_DIVIDER_CFG,
			(reg_read(MP2_PLL_DIVIDER_CFG) &
			~MCU_MISCCFG_MP2_ARM_PLL_DIVIDER_DCM_REG0_MASK) |
			MCU_MISCCFG_MP2_ARM_PLL_DIVIDER_DCM_REG0_OFF);
	}
}

#define MCU_MISCCFG_MP_STALL_DCM_REG0_MASK ((0xf << 24))
#define MCU_MISCCFG_MP_STALL_DCM_REG0_ON ((0x5 << 24))
#define MCU_MISCCFG_MP_STALL_DCM_REG0_OFF ((0xf << 24))

bool dcm_mcu_misccfg_mp_stall_dcm_is_on(int on)
{
	bool ret = true;

	ret &= !!(reg_read(SYNC_DCM_CLUSTER_CONFIG) &
		MCU_MISCCFG_MP_STALL_DCM_REG0_MASK &
		MCU_MISCCFG_MP_STALL_DCM_REG0_ON);

	return ret;
}

void dcm_mcu_misccfg_mp_stall_dcm(int on)
{
	if (on) {
		/* TINFO = "Turn ON DCM 'mcu_misccfg_mp_stall_dcm'" */
		reg_write(SYNC_DCM_CLUSTER_CONFIG,
			(reg_read(SYNC_DCM_CLUSTER_CONFIG) &
			~MCU_MISCCFG_MP_STALL_DCM_REG0_MASK) |
			MCU_MISCCFG_MP_STALL_DCM_REG0_ON);
	} else {
		/* TINFO = "Turn OFF DCM 'mcu_misccfg_mp_stall_dcm'" */
		reg_write(SYNC_DCM_CLUSTER_CONFIG,
			(reg_read(SYNC_DCM_CLUSTER_CONFIG) &
			~MCU_MISCCFG_MP_STALL_DCM_REG0_MASK) |
			MCU_MISCCFG_MP_STALL_DCM_REG0_OFF);
	}
}

#define MCU_MISCCFG_MCU_MISC_DCM_REG0_MASK ((0x1 << 0) | \
			(0x1 << 8))
#define MCU_MISCCFG_MCU_MISC_DCM_REG0_ON ((0x1 << 0) | \
			(0x1 << 8))
#define MCU_MISCCFG_MCU_MISC_DCM_REG0_OFF ((0x0 << 0) | \
			(0x0 << 8))

bool dcm_mcu_misccfg_mcu_misc_dcm_is_on(int on)
{
	bool ret = true;

	ret &= !!(reg_read(MCU_MISC_DCM_CTRL) &
		MCU_MISCCFG_MCU_MISC_DCM_REG0_MASK &
		MCU_MISCCFG_MCU_MISC_DCM_REG0_ON);

	return ret;
}

void dcm_mcu_misccfg_mcu_misc_dcm(int on)
{
	if (on) {
		/* TINFO = "Turn ON DCM 'mcu_misccfg_mcu_misc_dcm'" */
		reg_write(MCU_MISC_DCM_CTRL,
			(reg_read(MCU_MISC_DCM_CTRL) &
			~MCU_MISCCFG_MCU_MISC_DCM_REG0_MASK) |
			MCU_MISCCFG_MCU_MISC_DCM_REG0_ON);
	} else {
		/* TINFO = "Turn OFF DCM 'mcu_misccfg_mcu_misc_dcm'" */
		reg_write(MCU_MISC_DCM_CTRL,
			(reg_read(MCU_MISC_DCM_CTRL) &
			~MCU_MISCCFG_MCU_MISC_DCM_REG0_MASK) |
			MCU_MISCCFG_MCU_MISC_DCM_REG0_OFF);
	}
}

#define MCU_MISC1CFG_MCSIB_DCM_PRESET_REG0_MASK (0xffff << 0)

#define MCU_MISC1CFG_MCSIB_DCM_PRESET_REG0_ON (0xffff << 0)

#define MCU_MISC1CFG_MCSIB_DCM_PRESET_REG0_OFF (0x0 << 0)


#define MCU_MISC1CFG_MCSIB_DCM_REG0_MASK ((0xffff << 0) | \
			(0xffff << 16))
#define MCU_MISC1CFG_MCSIB_DCM_REG0_ON ((0xffff << 0) | \
			(0xffff << 16))
#define MCU_MISC1CFG_MCSIB_DCM_REG0_OFF ((0x0 << 0) | \
			(0x0 << 16))

void dcm_mcu_misc1cfg_mcsib_dcm_preset(int on)
{
	if (on) {
		/* TINFO = "Turn ON IDLE STATE 'mcu_misc1cfg_mcsib_dcm'" */
		reg_write(MCSIA_DCM_EN,
			(reg_read(MCSIA_DCM_EN) &
			~MCU_MISC1CFG_MCSIB_DCM_PRESET_REG0_MASK) |
			MCU_MISC1CFG_MCSIB_DCM_PRESET_REG0_ON);
	} else {
		/* TINFO = "Turn OFF IDLE STATE 'mcu_misc1cfg_mcsib_dcm'" */
		reg_write(MCSIA_DCM_EN,
			(reg_read(MCSIA_DCM_EN) &
			~MCU_MISC1CFG_MCSIB_DCM_PRESET_REG0_MASK) |
			MCU_MISC1CFG_MCSIB_DCM_PRESET_REG0_OFF);
	}
}

bool dcm_mcu_misc1cfg_mcsib_dcm_is_on(int on)
{
	bool ret = true;

	ret &= !!(reg_read(MCSIA_DCM_EN) &
		MCU_MISC1CFG_MCSIB_DCM_REG0_MASK &
		MCU_MISC1CFG_MCSIB_DCM_REG0_ON);

	return ret;
}

void dcm_mcu_misc1cfg_mcsib_dcm(int on)
{
	if (on) {
		/* TINFO = "Turn ON DCM 'mcu_misc1cfg_mcsib_dcm'" */
		reg_write(MCSIA_DCM_EN,
			(reg_read(MCSIA_DCM_EN) &
			~MCU_MISC1CFG_MCSIB_DCM_REG0_MASK) |
			MCU_MISC1CFG_MCSIB_DCM_REG0_ON);
	} else {
		/* TINFO = "Turn OFF DCM 'mcu_misc1cfg_mcsib_dcm'" */
		reg_write(MCSIA_DCM_EN,
			(reg_read(MCSIA_DCM_EN) &
			~MCU_MISC1CFG_MCSIB_DCM_REG0_MASK) |
			MCU_MISC1CFG_MCSIB_DCM_REG0_OFF);
	}
}

#define MP2_CA15M_CONFIG_SYNC_DCM_CFG_REG0_MASK ((0x1 << 0))
#define MP2_CA15M_CONFIG_SYNC_DCM_CFG_REG0_ON ((0x1 << 0))
#define MP2_CA15M_CONFIG_SYNC_DCM_CFG_REG0_OFF ((0x0 << 0))

bool dcm_mp2_ca15m_config_sync_dcm_cfg_is_on(int on)
{
	bool ret = true;

	ret &= !!(reg_read(MP2_CA15M_SYNC_DCM) &
		MP2_CA15M_CONFIG_SYNC_DCM_CFG_REG0_MASK &
		MP2_CA15M_CONFIG_SYNC_DCM_CFG_REG0_ON);

	return ret;
}

void dcm_mp2_ca15m_config_sync_dcm_cfg(int on)
{
	if (on) {
		/* TINFO = "Turn ON DCM 'mp2_ca15m_config_sync_dcm_cfg'" */
		reg_write(MP2_CA15M_SYNC_DCM,
			(reg_read(MP2_CA15M_SYNC_DCM) &
			~MP2_CA15M_CONFIG_SYNC_DCM_CFG_REG0_MASK) |
			MP2_CA15M_CONFIG_SYNC_DCM_CFG_REG0_ON);
	} else {
		/* TINFO = "Turn OFF DCM 'mp2_ca15m_config_sync_dcm_cfg'" */
		reg_write(MP2_CA15M_SYNC_DCM,
			(reg_read(MP2_CA15M_SYNC_DCM) &
			~MP2_CA15M_CONFIG_SYNC_DCM_CFG_REG0_MASK) |
			MP2_CA15M_CONFIG_SYNC_DCM_CFG_REG0_OFF);
	}
}

