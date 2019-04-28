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

#ifndef __DAPC_H__
#define __DAPC_H__

#include <linux/types.h>

/*************************************************************************
* CONSTANT DEFINATION
*************************************************************************/

#define MOD_NO_IN_1_DEVAPC                  16
#define DEVAPC_TAG                          "DEVAPC"
#define MTK_SIP_LK_DAPC			0x82000101

/* 1: Force to enable enhanced one-core violation debugging */
/* 0: Enhanced one-core violation debugging can be enabled dynamically */
/* Notice: You should only use one core to debug */
/* (Please note it may trigger PRINTK too much)  */
#define DEVAPC_ENABLE_ONE_CORE_VIOLATION_DEBUG	0

/* Uncomment to enable AEE  */
#define DEVAPC_ENABLE_AEE	1

/* This is necessary for AEE */
#define DEVAPC_INFRA_TOTAL_SLAVES	238
#define DEVAPC_PERI_TOTAL_SLAVES	56


#if defined(CONFIG_MTK_AEE_FEATURE) && defined(DEVAPC_ENABLE_AEE)

/* AEE trigger threshold for each module.
 * Remember: NEVER set it to 1
 */
#define DEVAPC_VIO_AEE_TRIGGER_TIMES        10

/* AEE trigger frequency for each module (ms) */
#define DEVAPC_VIO_AEE_TRIGGER_FREQUENCY    1000

/* Maximum populating AEE times for all the modules */
#define DEVAPC_VIO_MAX_TOTAL_MODULE_AEE_TRIGGER_TIMES        3

#endif

#define DAPC_INPUT_TYPE_DEBUG_ON	200
#define DAPC_INPUT_TYPE_DEBUG_OFF	100

#define DAPC_DEVICE_TREE_NODE_PD_INFRA_INDEX    0
#define DAPC_DEVICE_TREE_NODE_PD_PERI_INDEX     1

/* For Infra VIO_DBG */
#define INFRA_VIO_DBG_MSTID             0x0000FFFF
#define INFRA_VIO_DBG_MSTID_START_BIT   0
#define INFRA_VIO_DBG_DMNID             0x00FF0000
#define INFRA_VIO_DBG_DMNID_START_BIT   16
#define INFRA_VIO_ADDR_HIGH             0x0F000000
#define INFRA_VIO_ADDR_HIGH_START_BIT   24
#define INFRA_VIO_DBG_W                 0x10000000
#define INFRA_VIO_DBG_W_START_BIT       28
#define INFRA_VIO_DBG_R                 0x20000000
#define INFRA_VIO_DBG_R_START_BIT       29
#define INFRA_VIO_DBG_CLR               0x80000000
#define INFRA_VIO_DBG_CLR_START_BIT     31

/* For Peri VIO_DBG */
#define PERI_VIO_DBG_MSTID              0x0003FFFF
#define PERI_VIO_DBG_MSTID_START_BIT    0
#define PERI_VIO_DBG_DMNID              0x00FC0000
#define PERI_VIO_DBG_DMNID_START_BIT    18
#define PERI_VIO_ADDR_HIGH              0x0F000000
#define PERI_VIO_ADDR_HIGH_START_BIT    24
#define PERI_VIO_DBG_W                  0x10000000
#define PERI_VIO_DBG_W_START_BIT        28
#define PERI_VIO_DBG_R                  0x20000000
#define PERI_VIO_DBG_R_START_BIT        29
#define PERI_VIO_DBG_CLR                0x80000000
#define PERI_VIO_DBG_CLR_START_BIT      31

/*************************************************************************
* REGISTER ADDRESS DEFINATION
*************************************************************************/

/* Device APC PD */
#define PD_INFRA_VIO_MASK_MAX_INDEX     237
#define PD_INFRA_VIO_STA_MAX_INDEX      237

#define DEVAPC_PD_INFRA_VIO_MASK(index)    ((unsigned int *)(devapc_pd_infra_base + 0x4 * index))
#define DEVAPC_PD_INFRA_VIO_STA(index)     ((unsigned int *)(devapc_pd_infra_base + 0x400 + 0x4 * index))

#define DEVAPC_PD_INFRA_VIO_DBG0           ((unsigned int *)(devapc_pd_infra_base+0x900))
#define DEVAPC_PD_INFRA_VIO_DBG1           ((unsigned int *)(devapc_pd_infra_base+0x904))

#define DEVAPC_PD_INFRA_APC_CON            ((unsigned int *)(devapc_pd_infra_base+0xF00))


/* Device APC PD PERI */
#define PD_PERI_VIO_MASK_MAX_INDEX      55
#define PD_PERI_VIO_STA_MAX_INDEX       55

#define DEVAPC_PD_PERI_VIO_MASK(index)     ((unsigned int *)(devapc_pd_peri_base + 0x4 * index))
#define DEVAPC_PD_PERI_VIO_STA(index)      ((unsigned int *)(devapc_pd_peri_base + 0x400 + 0x4 * index))

#define DEVAPC_PD_PERI_VIO_DBG0            ((unsigned int *)(devapc_pd_peri_base+0x900))
#define DEVAPC_PD_PERI_VIO_DBG1            ((unsigned int *)(devapc_pd_peri_base+0x904))

#define DEVAPC_PD_PERI_APC_CON             ((unsigned int *)(devapc_pd_peri_base+0xF00))


struct DEVICE_INFO {
	const char      *device;
	bool            enable_vio_irq;
};

#ifdef CONFIG_MTK_HIBERNATION
extern void mt_irq_set_sens(unsigned int irq, unsigned int sens);
extern void mt_irq_set_polarity(unsigned int irq, unsigned int polarity);
#endif

enum DAPC_TYPE {
	DEVAPC_INFRA_TYPE = 0,
	DEVAPC_PERI_TYPE
};

#endif /* __DAPC_H__ */

