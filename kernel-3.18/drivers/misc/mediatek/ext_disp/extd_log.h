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

#ifndef __EXTD_DRV_LOG_H__
#define __EXTD_DRV_LOG_H__

#include <linux/printk.h>
extern unsigned int g_mobilelog;

/* HDMI log functions*/
#define HDMI_ERR(fmt, arg...)	pr_err("[EXTD-HDMI]:"fmt, ##arg)
#define HDMI_LOG(fmt, arg...)                   \
	do {                                       \
		if (g_mobilelog) \
			pr_debug("[EXTD-HDMI]:"fmt, ##arg); \
	} while (0)

#define HDMI_FUNC()                  \
			do {									   \
				if (g_mobilelog) \
					pr_debug("[EXTD-HDMI]:%s\n", __func__); \
			} while (0)


/* Eink log functions */
#define EPD_LOG(fmt, arg...)	pr_debug("[EXTD-EPD]:"fmt, ##arg)
#define EPD_FUNC()		pr_debug("[EXTD-EPD]:%s\n", __func__)
#define EPD_ERR(fmt, arg...)	pr_err("[EXTD-EPD]:"fmt, ##arg)

/*external display - multi-control log functions */
#define MULTI_COTRL_ERR(fmt, arg...)	pr_err("[EXTD-MULTI]:"fmt, ##arg)
#define MULTI_COTRL_LOG(fmt, arg...)                   \
	do {                                       \
		if (g_mobilelog) \
			pr_debug("[EXTD-MULTI]:"fmt, ##arg); \
	} while (0)

#define MULTI_COTRL_FUNC()                  \
			do {									   \
				if (g_mobilelog) \
					pr_debug("[EXTD-MULTI]:%s\n", __func__); \
			} while (0)


/*external display log functions*/
#define EXT_DISP_ERR(fmt, arg...)	pr_err("[EXTD]:"fmt, ##arg)
#define EXT_DISP_LOG(fmt, arg...)                   \
	do {                                       \
		if (g_mobilelog) \
			pr_debug("[EXTD]:"fmt, ##arg); \
	} while (0)
#define EXT_DISP_FUNC()                   \
	do {									   \
		if (g_mobilelog) \
			pr_debug("[EXTD]:%s\n", __func__); \
	} while (0)


/*external display mgr log functions*/
#define EXT_MGR_ERR(fmt, arg...)	pr_err("[EXTD-MGR]:"fmt, ##arg)
#define EXT_MGR_LOG(fmt, arg...)                   \
	do {                                       \
		if (g_mobilelog) \
			pr_debug("[EXTD-MGR]:"fmt, ##arg); \
	} while (0)
#define EXT_MGR_FUNC()                   \
			do {									   \
				if (g_mobilelog) \
					pr_debug("[EXTD-MGR]:%s\n", __func__); \
			} while (0)


/*external display - factory log functions*/
#define EXTD_FACTORY_LOG(fmt, arg...)	pr_debug("[EXTD]:"fmt, ##arg)
#define EXTD_FACTORY_ERR(fmt, arg...)	pr_err("[EXTD]:"fmt, ##arg)
#define EXTD_FACTORY_FUNC()		pr_debug("[EXTD]:%s\n", __func__)

#endif
