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

#ifndef __MTK_SPM_VCORE_DVFS_MT6775_H__
#define __MTK_SPM_VCORE_DVFS_MT6775_H__

#include "mtk_spm.h"
#include <mtk_vcorefs_manager.h>

/* Feature will disable both of DVS/DFS are 0 */
#define SPM_VCORE_DVS_EN       1
#define SPM_DDR_DFS_EN         1
#define SPM_MM_CLK_EN          0

#define SPM_DVFS_TIMEOUT       2000	/* 2ms , include 0.5ms for debounce*/
#define SPM_CHK_GUARD_TIME       10     /* 10us */

#define QOS_SRAM_BASE (qos_sram_base)

#define QOS_TOTAL_BW_BUF_SIZE 8

#define QOS_TOTAL_BW_BUF(idx) (QOS_SRAM_BASE + idx * 4)
#define QOS_TOTAL_BW          (QOS_SRAM_BASE + QOS_TOTAL_BW_BUF_SIZE * 4)
#define QOS_CPU_BW            (QOS_SRAM_BASE + QOS_TOTAL_BW_BUF_SIZE * 4 + 0x4)
#define QOS_MM_BW             (QOS_SRAM_BASE + QOS_TOTAL_BW_BUF_SIZE * 4 + 0x8)
#define QOS_GPU_BW            (QOS_SRAM_BASE + QOS_TOTAL_BW_BUF_SIZE * 4 + 0xC)
#define QOS_MD_PERI_BW        (QOS_SRAM_BASE + QOS_TOTAL_BW_BUF_SIZE * 4 + 0x10)

enum {
	QOS_TOTAL = 0,
	QOS_CPU,
	QOS_MM,
	QOS_GPU,
	QOS_MD_PERI,
	QOS_TOTAL_AVE
};

enum spm_opp {
	SPM_OPP_UNREQ = -1,
	SPM_OPP_0 = 0,
	SPM_OPP_1,
	SPM_OPP_2,
	SPM_OPP_3,
	SPM_OPP_4,
	SPM_OPP_5,
	SPM_OPP_6,
	SPM_NUM_OPP,
};

enum vcorefs_smc_cmd {
	VCOREFS_SMC_CMD_0, /* init setting */
	VCOREFS_SMC_CMD_1, /* load fw */
	VCOREFS_SMC_CMD_2, /* set opp */
	VCOREFS_SMC_CMD_3, /* write */
	VCOREFS_SMC_CMD_4, /* read */
	NUM_VCOREFS_SMC_CMD,
};

enum dvfs_kicker_group {
	KIR_GROUP_FIX = 0,
	KIR_GROUP_HPM,
	KIR_GROUP_HPM_NON_FORCE,
	NUM_KIR_GROUP,
};

enum emi_bw_type {
	BW_CG_H = 0,
	BW_CG_L,
	BW_TOTAL_H,
	BW_TOTAL_L,
	BW_DFS2_H,
	BW_DFS1_H,
	BW_DFS2_L,
	BW_DFS1_L,
	BW_NUM
};

/* met profile table index */
enum met_info_index {
	INFO_OPP_IDX = 0,
	INFO_SW_RSV5_IDX,
	INFO_DVFS_LEVEL_IDX,
	INFO_DVFS_DEBUG_IDX,
	INFO_DVFS_FLOOR_MASK3_IDX,
	INFO_OPP10_CNT_IDX,
	INFO_OPP01_CNT_IDX,
	INFO_OPP21_CNT_IDX,
	INFO_OPP12_CNT_IDX,
	INFO_OPP32_CNT_IDX,
	INFO_OPP23_CNT_IDX,
	INFO_OPP31_CNT_IDX,
	INFO_OPP13_CNT_IDX,
	INFO_MAX,
};

enum met_src_index {
	SRC_MD2SPM_IDX = 0,
	SRC_MD_OPP_IDX,
	SRC_SCP_BOOST_IDX,
	SRC_BW_CG_H_EN_IDX,
	SRC_BW_CG_L_EN_IDX,
	SRC_BW_TOTAL_H_EN_IDX,
	SRC_BW_TOTAL_L_EN_IDX,
	SRC_BW_DFS2_H_EN_IDX,
	SRC_BW_DFS1_H_EN_IDX,
	SRC_BW_DFS2_L_EN_IDX,
	SRC_BW_DFS1_L_EN_IDX,
	SRC_MAX
};

extern void vcorefs_get_emi_bw_ctrl(void);
extern int vcorefs_set_emi_bw_ctrl(int bw_ctrl_index, int enable);
extern void spm_prepare_mm_clk(int enable);
extern void spm_go_to_vcorefs(int spm_flags);
extern int spm_set_vcore_dvfs(struct kicker_config *krconf);
extern void spm_vcorefs_init(void);
extern int spm_dvfs_flag_init(void);
extern char *spm_vcorefs_dump_dvfs_regs(char *p);
extern u32 spm_vcorefs_get_MD_status(void);
extern int spm_vcorefs_pwarp_cmd(void);
extern int spm_vcorefs_get_opp(void);
extern int spm_vcorefs_get_kicker_group(int kicker);

/* met profile function */
extern int vcorefs_get_opp_info_num(void);
extern char **vcorefs_get_opp_info_name(void);
extern unsigned int *vcorefs_get_opp_info(void);
extern int vcorefs_get_src_req_num(void);
extern char **vcorefs_get_src_req_name(void);
extern unsigned int *vcorefs_get_src_req(void);

extern void dvfsrc_set_scp_vcore_request(unsigned int val);
extern void helio_dvfsrc_sspm_ipi_init(int dvfs_en, int dram_type);
extern void dvfsrc_update_sspm_vcore_opp_table(int opp, unsigned int vcore_uv);
extern void dvfsrc_update_sspm_ddr_opp_table(int opp, unsigned int ddr_khz);
extern void dvfsrc_update_sspm_qos_enable(int dvfs_en, unsigned int dram_type);
extern void dvfsrc_power_model_ddr_request(unsigned int level);

#define USE_VCOREFS_SRAM /* enable sram debug info */
#define VCOREFS_SRAM_DEBUG_COUNT 8
#define VCOREFS_SRAM_TOTAL_COUNT 32

#endif /* __MTK_SPM_VCORE_DVFS_H__ */
