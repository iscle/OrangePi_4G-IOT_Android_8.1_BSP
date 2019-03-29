/* Copyright (c) 2013 The Chromium OS Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

#include <stdio.h>
#include <stdlib.h>
#include <assert.h>
#include <time.h>
#include <memory.h>

#include "crossover2.h"
#include "dsp_test_util.h"
#include "dsp_util.h"
#include "raw.h"

#ifndef min
#define min(a, b) ({ __typeof__(a) _a = (a);	\
			__typeof__(b) _b = (b);	\
			_a < _b ? _a : _b; })
#endif

static double tp_diff(struct timespec *tp2, struct timespec *tp1)
{
	return (tp2->tv_sec - tp1->tv_sec)
		+ (tp2->tv_nsec - tp1->tv_nsec) * 1e-9;
}

void process(struct crossover2 *xo2, int count, float *data0L, float *data0R,
	     float *data1L, float *data1R, float *data2L, float *data2R)
{
	int start;
	for (start = 0; start < count; start += 2048)
		crossover2_process(xo2, min(2048, count - start),
				   data0L + start, data0R + start,
				   data1L + start, data1R + start,
				   data2L + start, data2R + start);
}

int main(int argc, char **argv)
{
	size_t frames;
	float *data0, *data1, *data2;
	double NQ = 44100 / 2;
	struct timespec tp1, tp2;
	struct crossover2 xo2;

	if (argc != 3 && argc != 6) {
		printf("Usage: crossover2_test input.raw output.raw "
		       "[low.raw mid.raw high.raw]\n");
		return 1;
	}

	dsp_enable_flush_denormal_to_zero();
	dsp_util_clear_fp_exceptions();

	data0 = read_raw(argv[1], &frames);
	data1 = (float *)malloc(sizeof(float) * frames * 2);
	data2 = (float *)malloc(sizeof(float) * frames * 2);

	crossover2_init(&xo2, 400 / NQ, 4000 / NQ);
	clock_gettime(CLOCK_THREAD_CPUTIME_ID, &tp1);
	process(&xo2, frames, data0, data0 + frames, data1, data1 + frames,
		data2, data2 + frames);
	clock_gettime(CLOCK_THREAD_CPUTIME_ID, &tp2);
	printf("processing takes %g seconds for %zu samples\n",
	       tp_diff(&tp2, &tp1), frames * 2);

	if (argc == 6) {
		write_raw(argv[3], data0, frames);
		write_raw(argv[4], data1, frames);
		write_raw(argv[5], data2, frames);
	}

	int i;
	for (i = 0; i < frames * 2; i++)
		data0[i] += data1[i] + data2[i];
	write_raw(argv[2], data0, frames);

	free(data0);
	free(data1);
	free(data2);

	dsp_util_print_fp_exceptions();
	return 0;
}
