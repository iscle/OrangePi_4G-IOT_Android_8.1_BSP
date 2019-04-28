
/*
 * Copyright (C) 2016 MediaTek Inc.
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
#ifndef _MTK_EEM_CONFIG_H_
#define _MTK_EEM_CONFIG_H_

/* CONFIG (SW related) */
/* #define EEM_NOT_READY       (1) */
#define CONFIG_EEM_SHOWLOG  (0)
#define EN_ISR_LOG          (1)
#define EEM_BANK_SOC        (0) /* use voltage bin, so disable it */
#define EEM_BANK_VPU		(1)
#define EARLY_PORTING       (0) /* for detecting real vboot in eem_init01 */
#define DUMP_DATA_TO_DE     (1)
#define EEM_ENABLE          (1) /* enable; after pass HPT mini-SQC */
#define EEM_FAKE_EFUSE      (0)
/* FIX ME */
#define UPDATE_TO_UPOWER    (1)
#define EEM_LOCKTIME_LIMIT  (3000)
#define ENABLE_EEMCTL0      (1)
#define ENABLE_LOO			(1)
#define ENABLE_INIT1_STRESS	(1)

#define EEM_OFFSET
#define SET_PMIC_VOLT           (1)
#define SET_PMIC_VOLT_TO_DVFS   (1)
#define LOG_INTERVAL            (2LL * NSEC_PER_SEC)

enum mt_cpu_dvfs_id {
	MT_CPU_DVFS_L,
	MT_CPU_DVFS_B,
	MT_CPU_DVFS_CCI,

	NR_MT_CPU_DVFS,
};

#define DEVINFO_IDX_0 50    /* 0x580 */
#define DEVINFO_IDX_1 51    /* 0x584 */
#define DEVINFO_IDX_2 52    /* 0x588 */
#define DEVINFO_IDX_3 53    /* 0x58C */
#define DEVINFO_IDX_4 54    /* 0x590 */
#define DEVINFO_IDX_5 55    /* 0x594 */
#define DEVINFO_IDX_6 56    /* 0x598 */
#define DEVINFO_IDX_7 57    /* 0x59C */
#define DEVINFO_IDX_8 58    /* 0x5A0 */
#define DEVINFO_IDX_9 59    /* 0x5A4 */
#define DEVINFO_IDX_13 63    /* 0x5B4 */
#define DEVINFO_IDX_14 64    /* 0x5B8 */
#define DEVINFO_IDX_15 65    /* 0x5BC */

#define DEVINFO_IDX_39 39    /* 0x104506A4 */

/* Fake EFUSE */
#define DEVINFO_0 0xFF00

#if 0 /* source */
/* L_LOW */
#define DEVINFO_1 0x041E5205
/* B_LOW + L_LOW */
#define DEVINFO_2 0x003F003F
/* B_LOW */
#define DEVINFO_3 0x041E5305
/* GPU */
#define DEVINFO_4 0x07163D11
/* CCI + GPU */
#define DEVINFO_5 0x00320042
/* CCI */
#define DEVINFO_6 0x041E5310
/* L_HIGH */
#define DEVINFO_7 0x041E98DA
/* B_HIGH + L_HIGH */
#define DEVINFO_8 0x00420042
/* B_HIGH */
#define DEVINFO_9 0x041E95DC
/* L */
#define DEVINFO_13 0x041E69FE
/* B + L */
#define DEVINFO_14 0x00420042
/* B */
#define DEVINFO_15 0x041E69FE
#else /* scrambled */
/* L_LOW */
#define DEVINFO_1 0xDC1E8ADD
/* B_LOW + L_LOW */
#define DEVINFO_2 0x00E700E7
/* B_LOW */
#define DEVINFO_3 0xDC1E8BDD
/* GPU */
#define DEVINFO_4 0x5F166549
/* CCI + GPU */
#define DEVINFO_5 0x006A009A
/* CCI */
#define DEVINFO_6 0xDC1E8BC8
/* L_HIGH */
#define DEVINFO_7 0xDC1E4002
/* B_HIGH + L_HIGH */
#define DEVINFO_8 0x009A009A
/* B_HIGH */
#define DEVINFO_9 0xDC1E4D04
/* L */
#define DEVINFO_13 0xDC1EB126
/* B + L */
#define DEVINFO_14 0x009A009A
/* B */
#define DEVINFO_15 0xDC1EB126
#endif


/*****************************************
* eem sw setting
******************************************
*/
#define NR_HW_RES_FOR_BANK	(13) /* real eem banks for efuse */
#define EEM_INIT01_FLAG		(0x0F)
#if ENABLE_LOO
#define EEM_L_INIT02_FLAG (0x21) /* should be 0x0F=> [5]:L_HI, [0]:L */
#define EEM_B_INIT02_FLAG (0xA) /* should be 0x0F=> [3]:B_HI, [1]:B */
#endif

#define NR_FREQ 16
#define NR_FREQ_GPU 16
#define NR_FREQ_CPU 16

/*
 * 100 us, This is the EEM Detector sampling time as represented in
 * cycles of bclk_ck during INIT. 52 MHz
 */
#define DETWINDOW_VAL		0xA28

/*
 * mili Volt to config value. voltage = 600mV + val * 6.25mV
 * val = (voltage - 600) / 6.25
 * @mV:	mili volt
 */

/* 1mV=>10uV */
/* EEM */
#define EEM_V_BASE		(40625)
#define EEM_STEP		(625)

/* CPU L */
#define CPU_L_PMIC_BASE    (30000)
#define CPU_L_PMIC_STEP    (500)
#define CPU_L_VBOOT_VAL    (0x47) /* 0.85v */
#define CPU_L_VMAX_VAL     (0x5B)
#define CPU_L_VMIN_VAL     (0x1F)
#define CPU_L_VCO_VAL      (0x1F)
#define CPU_L_DVTFIXED_VAL (0x7)
#define CPU_L_DVTFIXED_M_VAL (0x3)


/* CPU B */
#define CPU_B_PMIC_BASE    (40625)
#define CPU_B_PMIC_STEP    (625)
#define CPU_B_VBOOT_VAL    (0x47) /* 0.85v */
#define CPU_B_VMAX_VAL     (0x5B)
#define CPU_B_TYPT_B_VMAX_VAL	(0x5C)
#define CPU_B_VMIN_VAL     (0x1F)
#define CPU_B_VCO_VAL      (0x1F)
#define CPU_B_DVTFIXED_VAL (0xA)
#define CPU_B_DVTFIXED_M_VAL (0x7)

/* GPU */
#define GPU_PMIC_BASE    (40625)
#define GPU_PMIC_STEP    (625)
#define GPU_VBOOT_VAL    (0x3F) /* 0.8v */
#define GPU_VMAX_VAL     (0x4F)
#define GPU_VMIN_VAL     (0x1F)
#define GPU_VCO_VAL      (0x1F)
#define GPU_DVTFIXED_VAL (0x2)

/* CPU CCI */
#define CPU_CCI_PMIC_BASE    (30000)
#define CPU_CCI_PMIC_STEP    (500)
#define CPU_CCI_VBOOT_VAL    (0x47) /* 0.85v */
#define CPU_CCI_VMAX_VAL     (0x5B)
#define CPU_CCI_VMIN_VAL     (0x1F)
#define CPU_CCI_VCO_VAL      (0x1F)
#define CPU_CCI_DVTFIXED_VAL (0x7)

/* common part*/
#define DTHI_VAL        (0x01)
#define DTLO_VAL        (0xFE)
#define DETMAX_VAL      (0xffff)
#define AGECONFIG_VAL   (0x555555)
#define AGEM_VAL        (0x0)
#define DCCONFIG_VAL    (0x555555)

/* use in base_ops_mon_mode */
#define MTS_VAL         (0x1fb)
#define BTS_VAL         (0x6d1)

#define CORESEL_VAL       (0x8fff0000)
#define CORESEL_INIT2_VAL (0x0fff0000)

#define INVERT_TEMP_VAL  (25000)
#define OVER_INV_TEM_VAL (27000)

#define LOW_TEMP_OFF_DEFAULT			(0)
#define MARGIN_SAFE_EFUSE_ADD_OFF		(6)
#define MARGIN_ADD_OFF					(5)
#define MARGIN_ADD_CLAMP				(5)


#if ENABLE_EEMCTL0
#define EEM_CTL0_L   (0x00010001)
#define EEM_CTL0_B   (0x00200003)
#define EEM_CTL0_GPU (0x00040001)
#define EEM_CTL0_CCI (0x02100007)
#endif

#if EEM_FAKE_EFUSE		/* select PTP secure mode based on efuse config. */
#define SEC_MOD_SEL			0xF0		/* non secure  mode */
#else
#define SEC_MOD_SEL			0x00		/* Secure Mode 0 */
/* #define SEC_MOD_SEL			0x10	*/	/* Secure Mode 1 */
/* #define SEC_MOD_SEL			0x20	*/	/* Secure Mode 2 */
/* #define SEC_MOD_SEL			0x30	*/	/* Secure Mode 3 */
/* #define SEC_MOD_SEL			0x40	*/	/* Secure Mode 4 */
#endif

#if SEC_MOD_SEL == 0x00
#define SEC_DCBDET 0xCC
#define SEC_DCMDET 0xE6
#define SEC_BDES 0xF5
#define SEC_MDES 0x97
#define SEC_MTDES 0xAC
#elif SEC_MOD_SEL == 0x10
#define SEC_DCBDET 0xE5
#define SEC_DCMDET 0xB
#define SEC_BDES 0x31
#define SEC_MDES 0x53
#define SEC_MTDES 0x68
#elif SEC_MOD_SEL == 0x20
#define SEC_DCBDET 0x39
#define SEC_DCMDET 0xFE
#define SEC_BDES 0x18
#define SEC_MDES 0x8F
#define SEC_MTDES 0xB4
#elif SEC_MOD_SEL == 0x30
#define SEC_DCBDET 0xDF
#define SEC_DCMDET 0x18
#define SEC_BDES 0x0B
#define SEC_MDES 0x7A
#define SEC_MTDES 0x52
#elif SEC_MOD_SEL == 0x40
#define SEC_DCBDET 0x36
#define SEC_DCMDET 0xF1
#define SEC_BDES 0xE2
#define SEC_MDES 0x80
#define SEC_MTDES 0x41
#endif

#endif
