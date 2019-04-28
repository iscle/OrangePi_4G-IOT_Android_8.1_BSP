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

#include <mt-plat/mtk_io.h>
#include <mt-plat/sync_write.h>
/* #include <mt-plat/mtk_secure_api.h> */

#include <mtk_dcm_internal.h>
#include <mtk_dcm_autogen.h>
#include <mtk_dcm.h>

#define INFRACFG_AO_INFRABUS_REG0_MASK ((0x1 << 0) | \
			(0x1 << 1) | \
			(0x1 << 2) | \
			(0x1 << 3) | \
			(0x1 << 4) | \
			(0x1f << 10) | \
			(0x1f << 15) | \
			(0x1 << 20) | \
			(0x1 << 21) | \
			(0x1 << 22))
#define INFRACFG_AO_INFRABUS_REG0_ON ((0x1 << 0) | \
			(0x0 << 1) | \
			(0x0 << 2) | \
			(0x0 << 3) | \
			(0x0 << 4) | \
			(0x1f << 10) | \
			(0x10 << 15) | \
			(0x1 << 20) | \
			(0x1 << 21) | \
			(0x1 << 22))
#define INFRACFG_AO_INFRABUS_REG0_OFF ((0x0 << 0) | \
			(0x0 << 1) | \
			(0x0 << 2) | \
			(0x0 << 3) | \
			(0x0 << 4) | \
			(0x1f << 10) | \
			(0x10 << 15) | \
			(0x1 << 20) | \
			(0x1 << 21) | \
			(0x1 << 22))

bool dcm_infracfg_ao_infrabus_is_on(void)
{
	bool ret = true;

	ret &= ((reg_read(INFRA_BUS_DCM_CTRL) &
		INFRACFG_AO_INFRABUS_REG0_MASK) ==
		(unsigned int) INFRACFG_AO_INFRABUS_REG0_ON);

	return ret;
}

void dcm_infracfg_ao_infrabus(int on)
{
	if (on) {
		/* TINFO = "Turn ON DCM 'infracfg_ao_infrabus'" */
		reg_write(INFRA_BUS_DCM_CTRL,
			(reg_read(INFRA_BUS_DCM_CTRL) &
			~INFRACFG_AO_INFRABUS_REG0_MASK) |
			INFRACFG_AO_INFRABUS_REG0_ON);
	} else {
		/* TINFO = "Turn OFF DCM 'infracfg_ao_infrabus'" */
		reg_write(INFRA_BUS_DCM_CTRL,
			(reg_read(INFRA_BUS_DCM_CTRL) &
			~INFRACFG_AO_INFRABUS_REG0_MASK) |
			INFRACFG_AO_INFRABUS_REG0_OFF);
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

bool dcm_infracfg_ao_infra_emi_is_on(void)
{
	bool ret = true;

	ret &= ((reg_read(INFRA_EMI_DCM_CTRL_1) &
		INFRACFG_AO_INFRA_EMI_REG0_MASK) ==
		(unsigned int) INFRACFG_AO_INFRA_EMI_REG0_ON);

	return ret;
}

void dcm_infracfg_ao_infra_emi(int on)
{
	if (on) {
		/* TINFO = "Turn ON DCM 'infracfg_ao_infra_emi'" */
		reg_write(INFRA_EMI_DCM_CTRL,
			(reg_read(INFRA_EMI_DCM_CTRL_1) &
			~INFRACFG_AO_INFRA_EMI_REG0_MASK) |
			INFRACFG_AO_INFRA_EMI_REG0_ON);
	} else {
		/* TINFO = "Turn OFF DCM 'infracfg_ao_infra_emi'" */
		reg_write(INFRA_EMI_DCM_CTRL,
			(reg_read(INFRA_EMI_DCM_CTRL_1) &
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

bool dcm_infracfg_ao_mdbus_is_on(void)
{
	bool ret = true;

	ret &= ((reg_read(INFRA_MDBUS_DCM_CTRL) &
		INFRACFG_AO_MDBUS_REG0_MASK) ==
		(unsigned int) INFRACFG_AO_MDBUS_REG0_ON);

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
#define INFRACFG_AO_MTS_REG0_OFF ((0x1 << 30))

bool dcm_infracfg_ao_mts_is_on(void)
{
	bool ret = true;

	ret &= ((reg_read(INFRA_BUS_DCM_CTRL) &
		INFRACFG_AO_MTS_REG0_MASK) ==
		(unsigned int) INFRACFG_AO_MTS_REG0_ON);

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
			(0x1f << 10) | \
			(0x1f << 15) | \
			(0x1 << 20))
#define INFRACFG_AO_QAXIBUS_REG0_ON ((0x1 << 0) | \
			(0x0 << 1) | \
			(0x0 << 2) | \
			(0x0 << 3) | \
			(0x1f << 10) | \
			(0x10 << 15) | \
			(0x1 << 20))
#define INFRACFG_AO_QAXIBUS_REG0_OFF ((0x0 << 0) | \
			(0x0 << 1) | \
			(0x0 << 2) | \
			(0x0 << 3) | \
			(0x1f << 10) | \
			(0x10 << 15) | \
			(0x1 << 20))

bool dcm_infracfg_ao_qaxibus_is_on(void)
{
	bool ret = true;

	ret &= ((reg_read(INFRA_QAXIBUS_DCM_CTRL) &
		INFRACFG_AO_QAXIBUS_REG0_MASK) ==
		(unsigned int) INFRACFG_AO_QAXIBUS_REG0_ON);

	return ret;
}

void dcm_infracfg_ao_qaxibus(int on)
{
	if (on) {
		/* TINFO = "Turn ON DCM 'infracfg_ao_qaxibus'" */
		reg_write(INFRA_QAXIBUS_DCM_CTRL,
			(reg_read(INFRA_QAXIBUS_DCM_CTRL) &
			~INFRACFG_AO_QAXIBUS_REG0_MASK) |
			INFRACFG_AO_QAXIBUS_REG0_ON);
	} else {
		/* TINFO = "Turn OFF DCM 'infracfg_ao_qaxibus'" */
		reg_write(INFRA_QAXIBUS_DCM_CTRL,
			(reg_read(INFRA_QAXIBUS_DCM_CTRL) &
			~INFRACFG_AO_QAXIBUS_REG0_MASK) |
			INFRACFG_AO_QAXIBUS_REG0_OFF);
	}
}

#define PERICFG_REG_REG0_MASK ((0x1 << 11))
#define PERICFG_REG_REG0_ON ((0x1 << 11))
#define PERICFG_REG_REG0_OFF ((0x0 << 11))

bool dcm_pericfg_reg_is_on(void)
{
	bool ret = true;

	ret &= ((reg_read(PERICFG_DCM_EMI_EARLY_CTRL) &
		PERICFG_REG_REG0_MASK) ==
		(unsigned int) PERICFG_REG_REG0_ON);

	return ret;
}

void dcm_pericfg_reg(int on)
{
	if (on) {
		/* TINFO = "Turn ON DCM 'dcm_pericfg_reg'" */
		reg_write(PERICFG_DCM_EMI_EARLY_CTRL,
			(reg_read(PERICFG_DCM_EMI_EARLY_CTRL) &
			 ~PERICFG_REG_REG0_MASK) |
			PERICFG_REG_REG0_ON);
	} else {
		/* TINFO = "Turn OFF DCM 'dcm_pericfg_reg'" */
		reg_write(PERICFG_DCM_EMI_EARLY_CTRL,
			(reg_read(PERICFG_DCM_EMI_EARLY_CTRL) &
			~PERICFG_REG_REG0_MASK) |
			PERICFG_REG_REG0_OFF);
	}
}

#define EMI_DCM_EMI_GROUP_REG0_MASK ((0xff << 24))
#define EMI_DCM_EMI_GROUP_REG1_MASK ((0xff << 24))
#define EMI_DCM_EMI_GROUP_REG0_ON ((0x0 << 24))
#define EMI_DCM_EMI_GROUP_REG1_ON ((0x0 << 24))
#define EMI_DCM_EMI_GROUP_REG0_OFF ((0xff << 24))
#define EMI_DCM_EMI_GROUP_REG1_OFF ((0xff << 24))

bool dcm_emi_dcm_emi_group_is_on(void)
{
	bool ret = true;

	ret &= ((reg_read(EMI_CONM) &
		EMI_DCM_EMI_GROUP_REG0_MASK) ==
		(unsigned int) EMI_DCM_EMI_GROUP_REG0_ON);
	ret &= ((reg_read(EMI_CONN) &
		EMI_DCM_EMI_GROUP_REG1_MASK) ==
		(unsigned int) EMI_DCM_EMI_GROUP_REG1_ON);

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
			(0x1f << 21) | \
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
			(0x8 << 21) | \
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
			(0x0 << 21) | \
			(0x0 << 26))
#define DDRPHY0AO_DDRPHY_REG2_OFF ((0x1 << 26) | \
			(0x1 << 27))

bool dcm_ddrphy0ao_ddrphy_is_on(void)
{
	bool ret = true;

	ret &= ((reg_read(DDRPHY0AO_MISC_CG_CTRL0) &
		DDRPHY0AO_DDRPHY_REG0_MASK) ==
		(unsigned int) DDRPHY0AO_DDRPHY_REG0_ON);
	ret &= ((reg_read(DDRPHY0AO_MISC_CG_CTRL2) &
		DDRPHY0AO_DDRPHY_REG1_MASK) ==
		(unsigned int) DDRPHY0AO_DDRPHY_REG1_ON);
	ret &= ((reg_read(DDRPHY0AO_MISC_CTRL3) &
		DDRPHY0AO_DDRPHY_REG2_MASK) ==
		(unsigned int) DDRPHY0AO_DDRPHY_REG2_ON);

	return ret;
}

void dcm_ddrphy0ao_ddrphy(int on)
{
	if (on) {
		/* TINFO = "Turn ON DCM 'ddrphy0ao_ddrphy'" */
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
			(0x1f << 21) | \
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
			(0x8 << 21) | \
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
			(0x0 << 21) | \
			(0x0 << 26))
#define DDRPHY1AO_DDRPHY_REG2_OFF ((0x1 << 26) | \
			(0x1 << 27))

bool dcm_ddrphy1ao_ddrphy_is_on(void)
{
	bool ret = true;

	ret &= ((reg_read(DDRPHY1AO_MISC_CG_CTRL0) &
		DDRPHY1AO_DDRPHY_REG0_MASK) ==
		(unsigned int) DDRPHY1AO_DDRPHY_REG0_ON);
	ret &= ((reg_read(DDRPHY1AO_MISC_CG_CTRL2) &
		DDRPHY1AO_DDRPHY_REG1_MASK) ==
		(unsigned int) DDRPHY1AO_DDRPHY_REG1_ON);
	ret &= ((reg_read(DDRPHY1AO_MISC_CTRL3) &
		DDRPHY1AO_DDRPHY_REG2_MASK) ==
		(unsigned int) DDRPHY1AO_DDRPHY_REG2_ON);

	return ret;
}

void dcm_ddrphy1ao_ddrphy(int on)
{
	if (on) {
		/* TINFO = "Turn ON DCM 'ddrphy1ao_ddrphy'" */
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

#define CHN0_EMI_DCM_EMI_GROUP_REG0_MASK ((0xff << 24))
#define CHN0_EMI_DCM_EMI_GROUP_REG0_ON ((0x0 << 24))
#define CHN0_EMI_DCM_EMI_GROUP_REG0_OFF ((0xff << 24))

bool dcm_chn0_emi_dcm_emi_group_is_on(void)
{
	bool ret = true;

	ret &= ((reg_read(CHN0_EMI_CHN_EMI_CONB) &
		CHN0_EMI_DCM_EMI_GROUP_REG0_MASK) ==
		(unsigned int) CHN0_EMI_DCM_EMI_GROUP_REG0_ON);

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

#define CHN1_EMI_DCM_EMI_GROUP_REG0_MASK ((0xff << 24))
#define CHN1_EMI_DCM_EMI_GROUP_REG0_ON ((0x0 << 24))
#define CHN1_EMI_DCM_EMI_GROUP_REG0_OFF ((0xff << 24))

bool dcm_chn1_emi_dcm_emi_group_is_on(void)
{
	bool ret = true;

	ret &= ((reg_read(CHN1_EMI_CHN_EMI_CONB) &
		CHN1_EMI_DCM_EMI_GROUP_REG0_MASK) ==
		(unsigned int) CHN1_EMI_DCM_EMI_GROUP_REG0_ON);

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

#define DRAMC0_AO_DRAMC_DCM_REG0_MASK ((0x1 << 0) | \
			(0x1 << 1) | \
			(0x1 << 26) | \
			(0x1 << 30) | \
			(0x1 << 31))
#define DRAMC0_AO_DRAMC_DCM_REG1_MASK ((0x1 << 31))
#define DRAMC0_AO_DRAMC_DCM_REG0_ON ((0x1 << 0) | \
			(0x1 << 1) | \
			(0x0 << 26) | \
			(0x1 << 30) | \
			(0x1 << 31))
#define DRAMC0_AO_DRAMC_DCM_REG1_ON ((0x1 << 31))
#define DRAMC0_AO_DRAMC_DCM_REG0_OFF ((0x0 << 0) | \
			(0x0 << 1) | \
			(0x1 << 26) | \
			(0x0 << 30) | \
			(0x0 << 31))
#define DRAMC0_AO_DRAMC_DCM_REG1_OFF ((0x0 << 31))

bool dcm_dramc0_ao_dramc_dcm_is_on(void)
{
	bool ret = true;

	ret &= ((reg_read(DRAMC0_AO_DRAMC_PD_CTRL) &
		DRAMC0_AO_DRAMC_DCM_REG0_MASK) ==
		(unsigned int) DRAMC0_AO_DRAMC_DCM_REG0_ON);
	ret &= ((reg_read(DRAMC0_AO_CLKAR) &
		DRAMC0_AO_DRAMC_DCM_REG1_MASK) ==
		(unsigned int) DRAMC0_AO_DRAMC_DCM_REG1_ON);

	return ret;
}

void dcm_dramc0_ao_dramc_dcm(int on)
{
	if (on) {
		/* TINFO = "Turn ON DCM 'dcm_dramc0_ao_dramc_dcm'" */
		reg_write(DRAMC0_AO_DRAMC_PD_CTRL,
			(reg_read(DRAMC0_AO_DRAMC_PD_CTRL) &
			~DRAMC0_AO_DRAMC_DCM_REG0_MASK) |
			DRAMC0_AO_DRAMC_DCM_REG0_ON);
		reg_write(DRAMC0_AO_CLKAR,
			(reg_read(DRAMC0_AO_CLKAR) &
			~DRAMC0_AO_DRAMC_DCM_REG1_MASK) |
			DRAMC0_AO_DRAMC_DCM_REG1_ON);
	} else {
		/* TINFO = "Turn OFF DCM 'dcm_dramc0_ao_dramc_dcm'" */
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

#define DRAMC1_AO_DRAMC_DCM_REG0_MASK ((0x1 << 0) | \
			(0x1 << 1) | \
			(0x1 << 26) | \
			(0x1 << 30) | \
			(0x1 << 31))
#define DRAMC1_AO_DRAMC_DCM_REG1_MASK ((0x1 << 31))
#define DRAMC1_AO_DRAMC_DCM_REG0_ON ((0x1 << 0) | \
			(0x1 << 1) | \
			(0x0 << 26) | \
			(0x1 << 30) | \
			(0x1 << 31))
#define DRAMC1_AO_DRAMC_DCM_REG1_ON ((0x1 << 31))
#define DRAMC1_AO_DRAMC_DCM_REG0_OFF ((0x0 << 0) | \
			(0x0 << 1) | \
			(0x1 << 26) | \
			(0x0 << 30) | \
			(0x0 << 31))
#define DRAMC1_AO_DRAMC_DCM_REG1_OFF ((0x0 << 31))

bool dcm_dramc1_ao_dramc_dcm_is_on(void)
{
	bool ret = true;

	ret &= ((reg_read(DRAMC1_AO_DRAMC_PD_CTRL) &
		DRAMC1_AO_DRAMC_DCM_REG0_MASK) ==
		(unsigned int) DRAMC1_AO_DRAMC_DCM_REG0_ON);
	ret &= ((reg_read(DRAMC1_AO_CLKAR) &
		DRAMC1_AO_DRAMC_DCM_REG1_MASK) ==
		(unsigned int) DRAMC1_AO_DRAMC_DCM_REG1_ON);

	return ret;
}

void dcm_dramc1_ao_dramc_dcm(int on)
{
	if (on) {
		/* TINFO = "Turn ON DCM 'dcm_dramc1_ao_dramc_dcm'" */
		reg_write(DRAMC1_AO_DRAMC_PD_CTRL,
			(reg_read(DRAMC1_AO_DRAMC_PD_CTRL) &
			~DRAMC1_AO_DRAMC_DCM_REG0_MASK) |
			DRAMC1_AO_DRAMC_DCM_REG0_ON);
		reg_write(DRAMC1_AO_CLKAR,
			(reg_read(DRAMC1_AO_CLKAR) &
			~DRAMC1_AO_DRAMC_DCM_REG1_MASK) |
			DRAMC1_AO_DRAMC_DCM_REG1_ON);
	} else {
		/* TINFO = "Turn OFF DCM 'dcm_dramc1_ao_dramc_dcm'" */
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

#define MP0_CPUCFG_MP0_RGU_DCM_REG0_MASK ((0x1 << 0))
#define MP0_CPUCFG_MP0_RGU_DCM_REG0_ON ((0x1 << 0))
#define MP0_CPUCFG_MP0_RGU_DCM_REG0_OFF ((0x0 << 0))

bool dcm_mp0_cpucfg_mp0_rgu_dcm_is_on(void)
{
	bool ret = true;

	ret &= ((reg_read(MP0_CPUCFG_MP0_RGU_DCM_CONFIG) &
		MP0_CPUCFG_MP0_RGU_DCM_REG0_MASK) ==
		(unsigned int) MP0_CPUCFG_MP0_RGU_DCM_REG0_ON);

	return ret;
}

void dcm_mp0_cpucfg_mp0_rgu_dcm(int on)
{
	if (on) {
		/* TINFO = "Turn ON DCM 'mp0_cpucfg_mp0_rgu_dcm'" */
		reg_write(MP0_CPUCFG_MP0_RGU_DCM_CONFIG,
			(reg_read(MP0_CPUCFG_MP0_RGU_DCM_CONFIG) &
			~MP0_CPUCFG_MP0_RGU_DCM_REG0_MASK) |
			MP0_CPUCFG_MP0_RGU_DCM_REG0_ON);
	} else {
		/* TINFO = "Turn OFF DCM 'mp0_cpucfg_mp0_rgu_dcm'" */
		reg_write(MP0_CPUCFG_MP0_RGU_DCM_CONFIG,
			(reg_read(MP0_CPUCFG_MP0_RGU_DCM_CONFIG) &
			~MP0_CPUCFG_MP0_RGU_DCM_REG0_MASK) |
			MP0_CPUCFG_MP0_RGU_DCM_REG0_OFF);
	}
}

#define MP1_CPUCFG_MP1_RGU_DCM_REG0_MASK ((0x1 << 0))
#define MP1_CPUCFG_MP1_RGU_DCM_REG0_ON ((0x1 << 0))
#define MP1_CPUCFG_MP1_RGU_DCM_REG0_OFF ((0x0 << 0))

bool dcm_mp1_cpucfg_mp1_rgu_dcm_is_on(void)
{
	bool ret = true;

	ret &= ((reg_read(MP1_CPUCFG_MP1_RGU_DCM_CONFIG) &
		MP1_CPUCFG_MP1_RGU_DCM_REG0_MASK) ==
		(unsigned int) MP1_CPUCFG_MP1_RGU_DCM_REG0_ON);

	return ret;
}

void dcm_mp1_cpucfg_mp1_rgu_dcm(int on)
{
	if (on) {
		/* TINFO = "Turn ON DCM 'mp1_cpucfg_mp1_rgu_dcm'" */
		reg_write(MP1_CPUCFG_MP1_RGU_DCM_CONFIG,
			(reg_read(MP1_CPUCFG_MP1_RGU_DCM_CONFIG) &
			~MP1_CPUCFG_MP1_RGU_DCM_REG0_MASK) |
			MP1_CPUCFG_MP1_RGU_DCM_REG0_ON);
	} else {
		/* TINFO = "Turn OFF DCM 'mp1_cpucfg_mp1_rgu_dcm'" */
		reg_write(MP1_CPUCFG_MP1_RGU_DCM_CONFIG,
			(reg_read(MP1_CPUCFG_MP1_RGU_DCM_CONFIG) &
			~MP1_CPUCFG_MP1_RGU_DCM_REG0_MASK) |
			MP1_CPUCFG_MP1_RGU_DCM_REG0_OFF);
	}
}

#define MCU_MISCCFG_ADB400_DCM_REG0_MASK ((0x1 << 0) | \
			(0x1 << 1) | \
			(0x1 << 2) | \
			(0x1 << 5) | \
			(0x1 << 6))
#define MCU_MISCCFG_ADB400_DCM_REG0_ON ((0x1 << 0) | \
			(0x1 << 1) | \
			(0x1 << 2) | \
			(0x1 << 5) | \
			(0x1 << 6))
#define MCU_MISCCFG_ADB400_DCM_REG0_OFF ((0x0 << 0) | \
			(0x0 << 1) | \
			(0x0 << 2) | \
			(0x0 << 5) | \
			(0x0 << 6))

bool dcm_mcu_misccfg_adb400_dcm_is_on(void)
{
	bool ret = true;

	ret &= ((reg_read(CCI_ADB400_DCM_CONFIG) &
		MCU_MISCCFG_ADB400_DCM_REG0_MASK) ==
		(unsigned int) MCU_MISCCFG_ADB400_DCM_REG0_ON);

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

bool dcm_mcu_misccfg_bus_arm_pll_divider_dcm_is_on(void)
{
	bool ret = true;

	ret &= ((reg_read(BUS_PLL_DIVIDER_CFG) &
		MCU_MISCCFG_BUS_ARM_PLL_DIVIDER_DCM_REG0_MASK) ==
		(unsigned int) MCU_MISCCFG_BUS_ARM_PLL_DIVIDER_DCM_REG0_ON);

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

#define MCU_MISCCFG_BUS_SYNC_DCM_REG0_MASK ((0x1 << 0) | \
			(0x1 << 1) | \
			(0x7f << 2))
#define MCU_MISCCFG_BUS_SYNC_DCM_REG0_ON ((0x1 << 0) | \
			(0x1 << 1) | \
			(0x0 << 2))
#define MCU_MISCCFG_BUS_SYNC_DCM_REG0_OFF ((0x0 << 0) | \
			(0x1 << 1) | \
			(0x1 << 2))

bool dcm_mcu_misccfg_bus_sync_dcm_is_on(void)
{
	bool ret = true;

	ret &= ((reg_read(SYNC_DCM_CONFIG) &
		MCU_MISCCFG_BUS_SYNC_DCM_REG0_MASK) ==
		(unsigned int) MCU_MISCCFG_BUS_SYNC_DCM_REG0_ON);

	return ret;
}

void dcm_mcu_misccfg_bus_sync_dcm(int on)
{
	if (on) {
		/* TINFO = "Turn ON DCM 'mcu_misccfg_bus_sync_dcm'" */
		reg_write(SYNC_DCM_CONFIG,
			(reg_read(SYNC_DCM_CONFIG) &
			~MCU_MISCCFG_BUS_SYNC_DCM_REG0_MASK) |
			MCU_MISCCFG_BUS_SYNC_DCM_REG0_ON);
	} else {
		/* TINFO = "Turn OFF DCM 'mcu_misccfg_bus_sync_dcm'" */
		reg_write(SYNC_DCM_CONFIG,
			(reg_read(SYNC_DCM_CONFIG) &
			~MCU_MISCCFG_BUS_SYNC_DCM_REG0_MASK) |
			MCU_MISCCFG_BUS_SYNC_DCM_REG0_OFF);
	}
}

#define MCU_MISCCFG_BUS_CLOCK_DCM_REG0_MASK ((0x1 << 8))
#define MCU_MISCCFG_BUS_CLOCK_DCM_REG0_ON ((0x1 << 8))
#define MCU_MISCCFG_BUS_CLOCK_DCM_REG0_OFF ((0x0 << 8))

bool dcm_mcu_misccfg_bus_clock_dcm_is_on(void)
{
	bool ret = true;

	ret &= ((reg_read(CCI_CLK_CTRL) &
		MCU_MISCCFG_BUS_CLOCK_DCM_REG0_MASK) ==
		(unsigned int) MCU_MISCCFG_BUS_CLOCK_DCM_REG0_ON);

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
			(0x1 << 18) | \
			(0x1 << 19) | \
			(0x1 << 22) | \
			(0x1 << 23))
#define MCU_MISCCFG_BUS_FABRIC_DCM_REG0_ON ((0x1 << 0) | \
			(0x1 << 1) | \
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
			(0x1 << 18) | \
			(0x1 << 19) | \
			(0x1 << 22) | \
			(0x1 << 23))
#define MCU_MISCCFG_BUS_FABRIC_DCM_REG0_OFF ((0x0 << 0) | \
			(0x0 << 1) | \
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
			(0x0 << 18) | \
			(0x0 << 19) | \
			(0x0 << 22) | \
			(0x0 << 23))

bool dcm_mcu_misccfg_bus_fabric_dcm_is_on(void)
{
	bool ret = true;

	ret &= ((reg_read(BUS_FABRIC_DCM_CTRL) &
		MCU_MISCCFG_BUS_FABRIC_DCM_REG0_MASK) ==
		(unsigned int) MCU_MISCCFG_BUS_FABRIC_DCM_REG0_ON);

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

bool dcm_mcu_misccfg_gic_sync_dcm_is_on(void)
{
	bool ret = true;

	ret &= ((reg_read(MP_GIC_RGU_SYNC_DCM) &
		MCU_MISCCFG_GIC_SYNC_DCM_REG0_MASK) ==
		(unsigned int) MCU_MISCCFG_GIC_SYNC_DCM_REG0_ON);

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

bool dcm_mcu_misccfg_l2_shared_dcm_is_on(void)
{
	bool ret = true;

	ret &= ((reg_read(L2C_SRAM_CTRL) &
		MCU_MISCCFG_L2_SHARED_DCM_REG0_MASK) ==
		(unsigned int) MCU_MISCCFG_L2_SHARED_DCM_REG0_ON);

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
			(0x1 << 25))
#define MCU_MISCCFG_MP0_ARM_PLL_DIVIDER_DCM_REG0_ON ((0x1 << 11) | \
			(0x1 << 24) | \
			(0x1 << 25))
#define MCU_MISCCFG_MP0_ARM_PLL_DIVIDER_DCM_REG0_OFF ((0x0 << 11) | \
			(0x0 << 24) | \
			(0x0 << 25))

bool dcm_mcu_misccfg_mp0_arm_pll_divider_dcm_is_on(void)
{
	bool ret = true;

	ret &= ((reg_read(MP0_PLL_DIVIDER_CFG) &
		MCU_MISCCFG_MP0_ARM_PLL_DIVIDER_DCM_REG0_MASK) ==
		(unsigned int) MCU_MISCCFG_MP0_ARM_PLL_DIVIDER_DCM_REG0_ON);

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

bool dcm_mcu_misccfg_mp0_stall_dcm_is_on(void)
{
	bool ret = true;

	ret &= ((reg_read(SYNC_DCM_CLUSTER_CONFIG) &
		MCU_MISCCFG_MP0_STALL_DCM_REG0_MASK) ==
		(unsigned int) MCU_MISCCFG_MP0_STALL_DCM_REG0_ON);

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

#define MCU_MISCCFG_MP0_SYNC_DCM_ENABLE_REG0_MASK ((0x1 << 9) | \
			(0x1 << 10) | \
			(0xf << 11) | \
			(0x7 << 25))
#define MCU_MISCCFG_MP0_SYNC_DCM_ENABLE_REG0_ON ((0x1 << 9) | \
			(0x1 << 10) | \
			(0x0 << 11) | \
			(0x0 << 25))
#define MCU_MISCCFG_MP0_SYNC_DCM_ENABLE_REG0_OFF ((0x0 << 9) | \
			(0x1 << 10) | \
			(0x1 << 11) | \
			(0x0 << 25))

bool dcm_mcu_misccfg_mp0_sync_dcm_enable_is_on(void)
{
	bool ret = true;

	ret &= ((reg_read(SYNC_DCM_CONFIG) &
		MCU_MISCCFG_MP0_SYNC_DCM_ENABLE_REG0_MASK) ==
		(unsigned int) MCU_MISCCFG_MP0_SYNC_DCM_ENABLE_REG0_ON);

	return ret;
}

void dcm_mcu_misccfg_mp0_sync_dcm_enable(int on)
{
	if (on) {
		/* TINFO = "Turn ON DCM 'mcu_misccfg_mp0_sync_dcm_enable'" */
		reg_write(SYNC_DCM_CONFIG,
			(reg_read(SYNC_DCM_CONFIG) &
			~MCU_MISCCFG_MP0_SYNC_DCM_ENABLE_REG0_MASK) |
			MCU_MISCCFG_MP0_SYNC_DCM_ENABLE_REG0_ON);
	} else {
		/* TINFO = "Turn OFF DCM 'mcu_misccfg_mp0_sync_dcm_enable'" */
		reg_write(SYNC_DCM_CONFIG,
			(reg_read(SYNC_DCM_CONFIG) &
			~MCU_MISCCFG_MP0_SYNC_DCM_ENABLE_REG0_MASK) |
			MCU_MISCCFG_MP0_SYNC_DCM_ENABLE_REG0_OFF);
	}
}

#define MCU_MISCCFG_MP1_ARM_PLL_DIVIDER_DCM_REG0_MASK ((0x1 << 11) | \
			(0x1 << 24) | \
			(0x1 << 25))
#define MCU_MISCCFG_MP1_ARM_PLL_DIVIDER_DCM_REG0_ON ((0x1 << 11) | \
			(0x1 << 24) | \
			(0x1 << 25))
#define MCU_MISCCFG_MP1_ARM_PLL_DIVIDER_DCM_REG0_OFF ((0x0 << 11) | \
			(0x0 << 24) | \
			(0x0 << 25))

bool dcm_mcu_misccfg_mp1_arm_pll_divider_dcm_is_on(void)
{
	bool ret = true;

	ret &= ((reg_read(MP1_PLL_DIVIDER_CFG) &
		MCU_MISCCFG_MP1_ARM_PLL_DIVIDER_DCM_REG0_MASK) ==
		(unsigned int) MCU_MISCCFG_MP1_ARM_PLL_DIVIDER_DCM_REG0_ON);

	return ret;
}

void dcm_mcu_misccfg_mp1_arm_pll_divider_dcm(int on)
{
	if (on) {
		/* TINFO = "Turn ON DCM 'mcu_misccfg_mp1_arm_pll_divider_dcm'" */
		reg_write(MP1_PLL_DIVIDER_CFG,
			(reg_read(MP1_PLL_DIVIDER_CFG) &
			~MCU_MISCCFG_MP1_ARM_PLL_DIVIDER_DCM_REG0_MASK) |
			MCU_MISCCFG_MP1_ARM_PLL_DIVIDER_DCM_REG0_ON);
	} else {
		/* TINFO = "Turn OFF DCM 'mcu_misccfg_mp1_arm_pll_divider_dcm'" */
		reg_write(MP1_PLL_DIVIDER_CFG,
			(reg_read(MP1_PLL_DIVIDER_CFG) &
			~MCU_MISCCFG_MP1_ARM_PLL_DIVIDER_DCM_REG0_MASK) |
			MCU_MISCCFG_MP1_ARM_PLL_DIVIDER_DCM_REG0_OFF);
	}
}

#define MCU_MISCCFG_MP1_STALL_DCM_REG0_MASK ((0x1 << 15))
#define MCU_MISCCFG_MP1_STALL_DCM_REG0_ON ((0x1 << 15))
#define MCU_MISCCFG_MP1_STALL_DCM_REG0_OFF ((0x0 << 15))

bool dcm_mcu_misccfg_mp1_stall_dcm_is_on(void)
{
	bool ret = true;

	ret &= ((reg_read(SYNC_DCM_CLUSTER_CONFIG) &
		MCU_MISCCFG_MP1_STALL_DCM_REG0_MASK) ==
		(unsigned int) MCU_MISCCFG_MP1_STALL_DCM_REG0_ON);

	return ret;
}

void dcm_mcu_misccfg_mp1_stall_dcm(int on)
{
	if (on) {
		/* TINFO = "Turn ON DCM 'mcu_misccfg_mp1_stall_dcm'" */
		reg_write(SYNC_DCM_CLUSTER_CONFIG,
			(reg_read(SYNC_DCM_CLUSTER_CONFIG) &
			~MCU_MISCCFG_MP1_STALL_DCM_REG0_MASK) |
			MCU_MISCCFG_MP1_STALL_DCM_REG0_ON);
	} else {
		/* TINFO = "Turn OFF DCM 'mcu_misccfg_mp1_stall_dcm'" */
		reg_write(SYNC_DCM_CLUSTER_CONFIG,
			(reg_read(SYNC_DCM_CLUSTER_CONFIG) &
			~MCU_MISCCFG_MP1_STALL_DCM_REG0_MASK) |
			MCU_MISCCFG_MP1_STALL_DCM_REG0_OFF);
	}
}

#define MCU_MISCCFG_MP1_SYNC_DCM_ENABLE_REG0_MASK ((0x1 << 16) | \
			(0x1 << 17) | \
			(0x7f << 18))
#define MCU_MISCCFG_MP1_SYNC_DCM_ENABLE_REG0_ON ((0x1 << 16) | \
			(0x1 << 17) | \
			(0x0 << 18))
#define MCU_MISCCFG_MP1_SYNC_DCM_ENABLE_REG0_OFF ((0x0 << 16) | \
			(0x1 << 17) | \
			(0x1 << 18))

bool dcm_mcu_misccfg_mp1_sync_dcm_enable_is_on(void)
{
	bool ret = true;

	ret &= ((reg_read(SYNC_DCM_CONFIG) &
		MCU_MISCCFG_MP1_SYNC_DCM_ENABLE_REG0_MASK) ==
		(unsigned int) MCU_MISCCFG_MP1_SYNC_DCM_ENABLE_REG0_ON);

	return ret;
}

void dcm_mcu_misccfg_mp1_sync_dcm_enable(int on)
{
	if (on) {
		/* TINFO = "Turn ON DCM 'mcu_misccfg_mp1_sync_dcm_enable'" */
		reg_write(SYNC_DCM_CONFIG,
			(reg_read(SYNC_DCM_CONFIG) &
			~MCU_MISCCFG_MP1_SYNC_DCM_ENABLE_REG0_MASK) |
			MCU_MISCCFG_MP1_SYNC_DCM_ENABLE_REG0_ON);
	} else {
		/* TINFO = "Turn OFF DCM 'mcu_misccfg_mp1_sync_dcm_enable'" */
		reg_write(SYNC_DCM_CONFIG,
			(reg_read(SYNC_DCM_CONFIG) &
			~MCU_MISCCFG_MP1_SYNC_DCM_ENABLE_REG0_MASK) |
			MCU_MISCCFG_MP1_SYNC_DCM_ENABLE_REG0_OFF);
	}
}

#define MCU_MISCCFG_MP_STALL_DCM_REG0_MASK ((0x1 << 16) | \
			(0x1 << 17) | \
			(0x1 << 18) | \
			(0x1 << 19) | \
			(0x1 << 20) | \
			(0x1 << 21) | \
			(0xf << 24))
#define MCU_MISCCFG_MP_STALL_DCM_REG0_ON ((0x1 << 16) | \
			(0x1 << 17) | \
			(0x1 << 18) | \
			(0x1 << 19) | \
			(0x1 << 20) | \
			(0x1 << 21) | \
			(0x6 << 24))
#define MCU_MISCCFG_MP_STALL_DCM_REG0_OFF ((0x0 << 16) | \
			(0x0 << 17) | \
			(0x0 << 18) | \
			(0x0 << 19) | \
			(0x0 << 20) | \
			(0x0 << 21) | \
			(0xf << 24))

bool dcm_mcu_misccfg_mp_stall_dcm_is_on(void)
{
	bool ret = true;

	ret &= ((reg_read(SYNC_DCM_CLUSTER_CONFIG) &
		MCU_MISCCFG_MP_STALL_DCM_REG0_MASK) ==
		(unsigned int) MCU_MISCCFG_MP_STALL_DCM_REG0_ON);

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
			(0x1 << 1))
#define MCU_MISCCFG_MCU_MISC_DCM_REG0_ON ((0x1 << 0) | \
			(0x1 << 1))
#define MCU_MISCCFG_MCU_MISC_DCM_REG0_OFF ((0x0 << 0) | \
			(0x0 << 1))

bool dcm_mcu_misccfg_mcu_misc_dcm_is_on(void)
{
	bool ret = true;

	ret &= ((reg_read(MCU_MISC_DCM_CTRL) &
		MCU_MISCCFG_MCU_MISC_DCM_REG0_MASK) ==
		(unsigned int) MCU_MISCCFG_MCU_MISC_DCM_REG0_ON);

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

#define MCU_MISC1CFG_MCSIA_DCM_REG0_MASK ((0xffff << 16))
#define MCU_MISC1CFG_MCSIA_DCM_REG0_ON ((0xffff << 16))
#define MCU_MISC1CFG_MCSIA_DCM_REG0_OFF ((0x0 << 16))

bool dcm_mcu_misc1cfg_mcsia_dcm_is_on(void)
{
	bool ret = true;

	ret &= ((reg_read(MCSIA_DCM_EN) &
		MCU_MISC1CFG_MCSIA_DCM_REG0_MASK) ==
		(unsigned int) MCU_MISC1CFG_MCSIA_DCM_REG0_ON);

	return ret;
}

void dcm_mcu_misc1cfg_mcsia_dcm(int on)
{
	if (on) {
		/* TINFO = "Turn ON DCM 'mcu_misc1cfg_mcsia_dcm'" */
		reg_write(MCSIA_DCM_EN,
			(reg_read(MCSIA_DCM_EN) &
			~MCU_MISC1CFG_MCSIA_DCM_REG0_MASK) |
			MCU_MISC1CFG_MCSIA_DCM_REG0_ON);
	} else {
		/* TINFO = "Turn OFF DCM 'mcu_misc1cfg_mcsia_dcm'" */
		reg_write(MCSIA_DCM_EN,
			(reg_read(MCSIA_DCM_EN) &
			~MCU_MISC1CFG_MCSIA_DCM_REG0_MASK) |
			MCU_MISC1CFG_MCSIA_DCM_REG0_OFF);
	}
}
