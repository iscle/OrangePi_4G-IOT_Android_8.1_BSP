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

#ifndef STK8BAXX_H
#define STK8BAXX_H

#include <linux/ioctl.h>

#define STK8BAXX_BUFSIZE				256

#define STK8BAXX_SUCCESS					0
#define STK8BAXX_ERR_I2C						-1
#define STK8BAXX_ERR_STATUS					-3
#define STK8BAXX_ERR_SETUP_FAILURE			-4
#define STK8BAXX_ERR_GETGSENSORDATA		-5
#define STK8BAXX_ERR_IDENTIFICATION			-6

/*----------------------------------------------------------------------------*/
#define STK8BAXX_AXIS_X          0
#define STK8BAXX_AXIS_Y          1
#define STK8BAXX_AXIS_Z          2
#define STK8BAXX_AXES_NUM        3
#define STK8BAXX_DATA_LEN        6
#define STK8BAXX_DEV_NAME        "STK8BAXX"

/*----------------------------------------------------------------------------*/
enum CUST_ACTION {
	STK8BAXX_CUST_ACTION_SET_CUST = 1,
	STK8BAXX_CUST_ACTION_SET_CALI,
	STK8BAXX_CUST_ACTION_RESET_CALI
};
/*----------------------------------------------------------------------------*/
struct STK8BAXX_CUST {
	uint16_t action;
};
/*----------------------------------------------------------------------------*/
struct STK8BAXX_SET_CUST {
	uint16_t action;
	uint16_t part;
	int32_t data[0];
};
/*----------------------------------------------------------------------------*/
struct STK8BAXX_SET_CALI {
	uint16_t action;
	int32_t data[STK8BAXX_AXES_NUM];
};
/*----------------------------------------------------------------------------*/
union STK8BAXX_CUST_DATA {
	uint32_t data[10];
	struct STK8BAXX_CUST cust;
	struct STK8BAXX_SET_CUST setCust;
	struct STK8BAXX_SET_CALI setCali;
	struct STK8BAXX_CUST resetCali;
};
#endif
