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

#ifndef __DRV_CLK_MT6775_PG_H
#define __DRV_CLK_MT6775_PG_H

enum subsys_id {
	SYS_MFG0 = 0,
	SYS_MFG1 = 1,
	SYS_MFG2 = 2,
	SYS_MFG3 = 3,
	SYS_MFG4 = 4,
	SYS_MFG5 = 5,
	SYS_C2K = 6,
	SYS_MD1 = 7,
	SYS_CONN = 8,
	SYS_AUD = 9,
	SYS_MM0 = 10,
	SYS_CAM = 11,
	SYS_ISP = 12,
	SYS_VEN = 13,
	SYS_VDE = 14,
	SYS_IPU_VCORE_SHUTDOWN = 15,
	SYS_IPU_SHUTDOWN = 16,
	SYS_IPU_CORE0_SHUTDOWN = 17,
	SYS_IPU_CORE0_SLEEP = 18,
	SYS_IPU_CORE1_SHUTDOWN = 19,
	SYS_IPU_CORE1_SLEEP = 20,
	SYS_IPU_CORE2_SHUTDOWN = 21,
	SYS_IPU_CORE2_SLEEP = 22,
	NR_SYSS = 23,
};

struct pg_callbacks {
	struct list_head list;
	void (*before_off)(enum subsys_id sys);
	void (*after_on)(enum subsys_id sys);
};

/* register new pg_callbacks and return previous pg_callbacks. */
extern struct pg_callbacks *register_pg_callback(struct pg_callbacks *pgcb);
extern int spm_topaxi_protect(unsigned int mask_value, int en);

#if 0
extern void switch_mfg_clk(int src);
#endif
extern void subsys_if_on(void);
extern void mtcmos_force_off(void);


extern void ven_mtcmos_patch(void);
extern void vde_mtcmos_patch(void);

void mm0_mtcmos_before_power_off(void);
void mm0_mtcmos_after_power_on(void);
void isp_mtcmos_before_power_off(void);
void isp_mtcmos_after_power_on(void);
void cam_mtcmos_before_power_off(void);
void cam_mtcmos_after_power_on(void);
void ipu_mtcmos_before_power_off(void);
void ipu_mtcmos_after_power_on(void);
void ipu_core_mtcmos_before_power_off(void);

extern void check_ven_clk_sts(void);
extern void set_ven_bus_protect(void);
extern void mm_clk_restore(void);
/*ram console api*/
/*
*[0] bus protect reg
*[1] pwr_status
*[2] pwr_status 2
*[others] local function use
*/
#ifdef CONFIG_MTK_RAM_CONSOLE
extern void aee_rr_rec_clk(int id, u32 val);
#endif
#endif/* __DRV_CLK_MT6775_PG_H */
