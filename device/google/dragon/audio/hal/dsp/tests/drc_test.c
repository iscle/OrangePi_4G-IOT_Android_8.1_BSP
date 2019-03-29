/* Copyright (c) 2013 The Chromium OS Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */
#include <stdio.h>
#include <stdlib.h>
#include <time.h>

#include "dsp_test_util.h"
#include "dsp_util.h"
#include "drc.h"
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

static void process(struct drc *drc, float *buf, size_t frames)
{
	struct timespec tp1, tp2;
	int start;
	float *data[2];
	int chunk;
	clock_gettime(CLOCK_THREAD_CPUTIME_ID, &tp1);
	for (start = 0; start < frames;) {
		data[0] = buf + start;
		data[1] = buf + start + frames;
		chunk = min(DRC_PROCESS_MAX_FRAMES, frames - start);
		drc_process(drc, data, chunk);
		start += chunk;
	}
	clock_gettime(CLOCK_THREAD_CPUTIME_ID, &tp2);
	printf("drc processing takes %g seconds for %zu samples\n",
	       tp_diff(&tp2, &tp1), frames * 2);
}

int main(int argc, char **argv)
{
	double NQ = 44100 / 2; /* nyquist frequency */
	struct drc *drc;
	size_t frames;
	float *buf;

	if (argc != 3) {
		printf("Usage: drc_test input.raw output.raw\n");
		return 1;
	}

	dsp_enable_flush_denormal_to_zero();
	dsp_util_clear_fp_exceptions();
	drc = drc_new(44100);

	drc->emphasis_disabled = 0;
	drc_set_param(drc, 0, PARAM_CROSSOVER_LOWER_FREQ, 0);
	drc_set_param(drc, 0, PARAM_ENABLED, 1);
	drc_set_param(drc, 0, PARAM_THRESHOLD, -29);
	drc_set_param(drc, 0, PARAM_KNEE, 3);
	drc_set_param(drc, 0, PARAM_RATIO, 6.677);
	drc_set_param(drc, 0, PARAM_ATTACK, 0.02);
	drc_set_param(drc, 0, PARAM_RELEASE, 0.2);
	drc_set_param(drc, 0, PARAM_POST_GAIN, -7);

	drc_set_param(drc, 1, PARAM_CROSSOVER_LOWER_FREQ, 200 / NQ);
	drc_set_param(drc, 1, PARAM_ENABLED, 1);
	drc_set_param(drc, 1, PARAM_THRESHOLD, -32);
	drc_set_param(drc, 1, PARAM_KNEE, 23);
	drc_set_param(drc, 1, PARAM_RATIO, 12);
	drc_set_param(drc, 1, PARAM_ATTACK, 0.02);
	drc_set_param(drc, 1, PARAM_RELEASE, 0.2);
	drc_set_param(drc, 1, PARAM_POST_GAIN, 0.7);

	drc_set_param(drc, 2, PARAM_CROSSOVER_LOWER_FREQ, 1200 / NQ);
	drc_set_param(drc, 2, PARAM_ENABLED, 1);
	drc_set_param(drc, 2, PARAM_THRESHOLD, -24);
	drc_set_param(drc, 2, PARAM_KNEE, 30);
	drc_set_param(drc, 2, PARAM_RATIO, 1);
	drc_set_param(drc, 2, PARAM_ATTACK, 0.001);
	drc_set_param(drc, 2, PARAM_RELEASE, 1);
	drc_set_param(drc, 2, PARAM_POST_GAIN, 0);

	drc_init(drc);
	buf = read_raw(argv[1], &frames);
	process(drc, buf, frames);
	write_raw(argv[2], buf, frames);
	drc_free(drc);
	free(buf);
	dsp_util_print_fp_exceptions();
	return 0;
}
