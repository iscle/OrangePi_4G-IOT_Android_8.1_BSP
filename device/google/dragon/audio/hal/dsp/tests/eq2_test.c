/* Copyright (c) 2013 The Chromium OS Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

#include <stdio.h>
#include <stdlib.h>
#include <time.h>

#include "dsp_test_util.h"
#include "dsp_util.h"
#include "eq2.h"
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

/* Processes a buffer of data chunk by chunk using eq2 */
static void process(struct eq2 *eq2, float *data0, float *data1, int count)
{
	int start;
	for (start = 0; start < count; start += 2048)
		eq2_process(eq2, data0 + start, data1 + start,
			    min(2048, count - start));
}

/* Runs the filters on an input file */
static void test_file(const char *input_filename, const char *output_filename)
{
	size_t frames;
	int i;
	double NQ = 44100 / 2; /* nyquist frequency */
	struct timespec tp1, tp2;
	struct eq2 *eq2;

	float *data = read_raw(input_filename, &frames);

	/* Set some data to 0 to test for denormals. */
	for (i = frames / 10; i < frames; i++)
		data[i] = 0.0;

	/* eq chain */
	eq2 = eq2_new();
	eq2_append_biquad(eq2, 0, BQ_PEAKING, 380/NQ, 3, -10);
	eq2_append_biquad(eq2, 0, BQ_PEAKING, 720/NQ, 3, -12);
	eq2_append_biquad(eq2, 0, BQ_PEAKING, 1705/NQ, 3, -8);
	eq2_append_biquad(eq2, 0, BQ_HIGHPASS, 218/NQ, 0.7, -10.2);
	eq2_append_biquad(eq2, 0, BQ_PEAKING, 580/NQ, 6, -8);
	eq2_append_biquad(eq2, 0, BQ_HIGHSHELF, 8000/NQ, 3, 2);
	eq2_append_biquad(eq2, 1, BQ_PEAKING, 450/NQ, 3, -12);
	eq2_append_biquad(eq2, 1, BQ_PEAKING, 721/NQ, 3, -12);
	eq2_append_biquad(eq2, 1, BQ_PEAKING, 1800/NQ, 8, -10.2);
	eq2_append_biquad(eq2, 1, BQ_PEAKING, 580/NQ, 6, -8);
	eq2_append_biquad(eq2, 1, BQ_HIGHPASS, 250/NQ, 0.6578, 0);
	eq2_append_biquad(eq2, 1, BQ_HIGHSHELF, 8000/NQ, 0, 2);
	clock_gettime(CLOCK_THREAD_CPUTIME_ID, &tp1);
	process(eq2, data, data + frames, frames);
	clock_gettime(CLOCK_THREAD_CPUTIME_ID, &tp2);
	printf("processing takes %g seconds for %zu samples\n",
	       tp_diff(&tp2, &tp1), frames * 2);
	eq2_free(eq2);

	write_raw(output_filename, data, frames);
	free(data);
}

int main(int argc, char **argv)
{
	dsp_enable_flush_denormal_to_zero();
	if (dsp_util_has_denormal())
		printf("denormal still supported?\n");
	else
		printf("denormal disabled\n");
	dsp_util_clear_fp_exceptions();

	if (argc == 3)
		test_file(argv[1], argv[2]);
	else
		printf("Usage: eq2_test [input.raw output.raw]\n");

	dsp_util_print_fp_exceptions();
	return 0;
}
