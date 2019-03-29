/* Copyright (c) 2013 The Chromium OS Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

#include <fenv.h>
#include <float.h>
#include <stdio.h>
#include "dsp_test_util.h"

int dsp_util_has_denormal()
{
	float x = 1;
	while (x >= FLT_MIN)
		x /= 2;
	return x > 0;
}

void dsp_util_clear_fp_exceptions()
{
	feclearexcept(FE_ALL_EXCEPT);
}

void dsp_util_print_fp_exceptions()
{
	int excepts = fetestexcept(FE_ALL_EXCEPT);
	printf("floating-point exceptions: ");
	if (excepts & FE_DIVBYZERO)
		printf("FE_DIVBYZERO ");
	if (excepts & FE_INVALID)
		printf("FE_INVALID ");
	if (excepts & FE_OVERFLOW)
		printf("FE_OVERFLOW ");
	if (excepts & FE_UNDERFLOW)
		printf("FE_UNDERFLOW ");
	printf("\n");
}
