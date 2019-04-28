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

#ifndef __DRV_CLK_MT6758_PG_H
#define __DRV_CLK_MT6758_PG_H

enum subsys_id {
	SYS_MFG0 = 0,
	SYS_MFG1 = 1,
	SYS_MFG2 = 2,
	SYS_MFG3 = 3,
	SYS_MFG4 = 4,
	SYS_C2K = 5,
	SYS_MD1 = 6,
	SYS_CONN = 7,
	SYS_AUD = 8,
	SYS_MM0 = 9,
	SYS_CAM = 10,
	SYS_ISP = 11,
	SYS_VEN = 12,
	SYS_VDE = 13,
	SYS_IPU_SHUTDOWN = 14,
	SYS_IPU_SLEEP = 15,
	NR_SYSS = 16,
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

/*new arch*/
/*extern void mfg_mtcmos_patch(void);*/
extern void mm0_mtcmos_patch(int on);
extern void ven_mtcmos_patch(void);
extern void ipu_mtcmos_patch(int on);
extern void isp_mtcmos_patch(int on);
extern void vde_mtcmos_patch(void);
extern void cam_mtcmos_patch(int on);

extern void check_ven_clk_sts(void);
extern void set_ven_bus_protect(void);
extern void mm_clk_restore(void);
extern void mfg_sts_check(void);
extern void ven_clk_check(void);
extern unsigned int mt_get_ckgen_freq(unsigned int ID);
/*extern void aee_sram_printk(const char *fmt, ...);*/
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
#endif/* __DRV_CLK_MT6758_PG_H */
