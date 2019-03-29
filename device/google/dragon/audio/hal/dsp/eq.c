/* Copyright (c) 2013 The Chromium OS Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

#include <stdlib.h>
#include "eq.h"

struct eq {
	int n;
	struct biquad biquad[MAX_BIQUADS_PER_EQ];
};

struct eq *eq_new()
{
	struct eq *eq = (struct eq *)calloc(1, sizeof(*eq));
	return eq;
}

void eq_free(struct eq *eq)
{
	free(eq);
}

int eq_append_biquad(struct eq *eq, enum biquad_type type, float freq, float Q,
		      float gain)
{
	if (eq->n >= MAX_BIQUADS_PER_EQ)
		return -1;
	biquad_set(&eq->biquad[eq->n++], type, freq, Q, gain);
	return 0;
}

int eq_append_biquad_direct(struct eq *eq, const struct biquad *biquad)
{
	if (eq->n >= MAX_BIQUADS_PER_EQ)
		return -1;
	eq->biquad[eq->n++] = *biquad;
	return 0;
}

/* This is the prototype of the processing loop. */
void eq_process1(struct eq *eq, float *data, int count)
{
	int i, j;
	for (i = 0; i < eq->n; i++) {
		struct biquad *q = &eq->biquad[i];
		float x1 = q->x1;
		float x2 = q->x2;
		float y1 = q->y1;
		float y2 = q->y2;
		float b0 = q->b0;
		float b1 = q->b1;
		float b2 = q->b2;
		float a1 = q->a1;
		float a2 = q->a2;
		for (j = 0; j < count; j++) {
			float x = data[j];
			float y = b0*x
				+ b1*x1 + b2*x2
				- a1*y1 - a2*y2;
			data[j] = y;
			x2 = x1;
			x1 = x;
			y2 = y1;
			y1 = y;
		}
		q->x1 = x1;
		q->x2 = x2;
		q->y1 = y1;
		q->y2 = y2;
	}
}

/* This is the actual processing loop used. It is the unrolled version of the
 * above prototype. */
void eq_process(struct eq *eq, float *data, int count)
{
	int i, j;
	for (i = 0; i < eq->n; i += 2) {
		if (i + 1 == eq->n) {
			struct biquad *q = &eq->biquad[i];
			float x1 = q->x1;
			float x2 = q->x2;
			float y1 = q->y1;
			float y2 = q->y2;
			float b0 = q->b0;
			float b1 = q->b1;
			float b2 = q->b2;
			float a1 = q->a1;
			float a2 = q->a2;
			for (j = 0; j < count; j++) {
				float x = data[j];
				float y = b0*x
					+ b1*x1 + b2*x2
					- a1*y1 - a2*y2;
				data[j] = y;
				x2 = x1;
				x1 = x;
				y2 = y1;
				y1 = y;
			}
			q->x1 = x1;
			q->x2 = x2;
			q->y1 = y1;
			q->y2 = y2;
		} else {
			struct biquad *q = &eq->biquad[i];
			struct biquad *r = &eq->biquad[i+1];
			float x1 = q->x1;
			float x2 = q->x2;
			float y1 = q->y1;
			float y2 = q->y2;
			float qb0 = q->b0;
			float qb1 = q->b1;
			float qb2 = q->b2;
			float qa1 = q->a1;
			float qa2 = q->a2;

			float z1 = r->y1;
			float z2 = r->y2;
			float rb0 = r->b0;
			float rb1 = r->b1;
			float rb2 = r->b2;
			float ra1 = r->a1;
			float ra2 = r->a2;

			for (j = 0; j < count; j++) {
				float x = data[j];
				float y = qb0*x
					+ qb1*x1 + qb2*x2
					- qa1*y1 - qa2*y2;
				float z = rb0*y
					+ rb1*y1 + rb2*y2
					- ra1*z1 - ra2*z2;
				data[j] = z;
				x2 = x1;
				x1 = x;
				y2 = y1;
				y1 = y;
				z2 = z1;
				z1 = z;
			}
			q->x1 = x1;
			q->x2 = x2;
			q->y1 = y1;
			q->y2 = y2;
			r->y1 = z1;
			r->y2 = z2;
		}
	}
}
