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

#ifndef _DDP_REG_SPLIT_H_
#define _DDP_REG_SPLIT_H_

/* ------------------------------------------------------------- */
/* SPLIT */

/* for split driver */
#define DISP_REG_SPLIT_ENABLE				(DISPSYS_SPLIT0_BASE+0x00)
#define DISP_REG_SPLIT_SW_RESET				(DISPSYS_SPLIT0_BASE+0x04)
#define DISP_REG_SPLIT_INTEN				(DISPSYS_SPLIT0_BASE+0x08)
#define DISP_REG_SPLIT_INTSTA				(DISPSYS_SPLIT0_BASE+0x0c)
#define DISP_REG_SPLIT_CFG				    (DISPSYS_SPLIT0_BASE+0x20)

#define DISP_REG_SPLIT_HSIZE                (DISPSYS_SPLIT0_BASE+0x34)
#define DISP_REG_SPLIT_VSIZE                (DISPSYS_SPLIT0_BASE+0x38)
#define DISP_REG_SPLIT_SHADOW_CTRL			(DISPSYS_SPLIT0_BASE+0x80)
#define FLD_SPLIT_FORCE_COMMIT				REG_FLD(1, 0)
#define FLD_SPLIT_BYPASS_SHADOW				REG_FLD(1, 1)
#define FLD_SPLIT_RD_WRK_REG				REG_FLD(1, 2)

/* for debug */
#define DISP_REG_SPLIT_STATUS				(DISPSYS_SPLIT0_BASE+0x10)
#define DISP_REG_SPLIT_INPUT_COUNT			(DISPSYS_SPLIT0_BASE+0x24)
#define DISP_REG_SPLIT_LEFT_OUTPUT_COUNT	(DISPSYS_SPLIT0_BASE+0x28)
#define DISP_REG_SPLIT_RIGHT_OUTPUT_COUNT	(DISPSYS_SPLIT0_BASE+0x2c)
#define DISP_REG_SPLIT_INFO_A               (DISPSYS_SPLIT0_BASE+0x40)
#define DISP_REG_SPLIT_INFO_B               (DISPSYS_SPLIT0_BASE+0x44)
#define DISP_REG_SPLIT_INFO_C               (DISPSYS_SPLIT0_BASE+0x48)

/* field */
#define ENABLE_FLD_SPLIT_EN					REG_FLD(1, 0)
#define W_RESET_FLD_SPLIT_SW_RST			REG_FLD(1, 0)
#define REG_HSIZE_FLD_HSIZE_L				REG_FLD(11, 16)
#define REG_HSIZE_FLD_HSIZE_R				REG_FLD(11, 0)
#define REG_VSIZE_FLD_VSIZE					REG_FLD(13, 0)

#endif
