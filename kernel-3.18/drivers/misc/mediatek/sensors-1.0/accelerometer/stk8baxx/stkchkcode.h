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

#ifndef __STKCHKCODE_H__
#define __STKCHKCODE_H__
#define STKLSB			256
#define CHECK_CODE_SIZE  (STKLSB + 1)
extern int get_hw_ver(void);
static int stkcheckcode[CHECK_CODE_SIZE][CHECK_CODE_SIZE];

static int STK8BAXX_CheckCode(s16 acc[])
{
	int a, b;

	if (acc[0] > 0)
		a = acc[0];
	else
		a = -acc[0];
	if (acc[1] > 0)
		b = acc[1];
	else
		b = -acc[1];
	if (a >= CHECK_CODE_SIZE || b >= CHECK_CODE_SIZE)
		acc[2] = 0;
	else
		acc[2] = (s16) stkcheckcode[a][b];
	return 0;
}

#endif				/* #ifndef __STKCHKCODE_H__ */
