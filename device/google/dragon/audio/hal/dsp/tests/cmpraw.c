/* Copyright (c) 2013 The Chromium OS Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

#include <math.h>
#include <stdio.h>
#include <stdlib.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <unistd.h>
#include "raw.h"

/* Compare the difference between two raw files */

static inline double max(double a, double b)
{
	return (a > b) ? a : b;
}

int main(int argc, char **argv)
{
	size_t frame1, frame2;
	float *data1, *data2;
	size_t i, n, changed;
	double diff = 0;
	double maxdiff = 0;

	if (argc != 3) {
		fprintf(stderr, "usage: cmpraw 1.raw 2.raw\n");
		exit(1);
	}

	data1 = read_raw(argv[1], &frame1);
	data2 = read_raw(argv[2], &frame2);

	if (frame1 != frame2) {
		fprintf(stderr, "mismatch size (%zu vs %zu)\n", frame1, frame2);
		exit(1);
	}

	n = frame1 * 2;
	changed = 0;
	for (i = 0; i < n; i++) {
		if (data1[i] != data2[i]) {
			changed++;
			diff += fabs(data1[i] - data2[i]);
			maxdiff = max(fabs(data1[i] - data2[i]), maxdiff);
		}
	}
	printf("avg diff = %g, max diff = %g, changed = %.3f%%\n",
	       diff / n, maxdiff * 32768, changed*100.0f/n);

	free(data1);
	free(data2);
	return 0;
}
