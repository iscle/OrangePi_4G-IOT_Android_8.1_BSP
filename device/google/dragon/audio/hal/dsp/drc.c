/* Copyright (c) 2013 The Chromium OS Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

/* Copyright (C) 2011 Google Inc. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE.WEBKIT file.
 */

#include <assert.h>
#include <stdlib.h>

#include "drc.h"
#include "drc_math.h"

static void set_default_parameters(struct drc *drc);
static void init_data_buffer(struct drc *drc);
static void init_emphasis_eq(struct drc *drc);
static void init_crossover(struct drc *drc);
static void init_kernel(struct drc *drc);
static void free_data_buffer(struct drc *drc);
static void free_emphasis_eq(struct drc *drc);
static void free_kernel(struct drc *drc);

struct drc *drc_new(float sample_rate)
{
	struct drc *drc = (struct drc *)calloc(1, sizeof(struct drc));
	drc->sample_rate = sample_rate;
	set_default_parameters(drc);
	return drc;
}

void drc_init(struct drc *drc)
{
	init_data_buffer(drc);
	init_emphasis_eq(drc);
	init_crossover(drc);
	init_kernel(drc);
}

void drc_free(struct drc *drc)
{
	free_kernel(drc);
	free_emphasis_eq(drc);
	free_data_buffer(drc);
	free(drc);
}

/* Allocates temporary buffers used during drc_process(). */
static void init_data_buffer(struct drc *drc)
{
	int i;
	size_t size = sizeof(float) * DRC_PROCESS_MAX_FRAMES;

	for (i = 0; i < DRC_NUM_CHANNELS; i++) {
		drc->data1[i] = (float *)calloc(1, size);
		drc->data2[i] = (float *)calloc(1, size);
	}
}

/* Frees temporary buffers */
static void free_data_buffer(struct drc *drc)
{
	int i;

	for (i = 0; i < DRC_NUM_CHANNELS; i++) {
		free(drc->data1[i]);
		free(drc->data2[i]);
	}
}

void drc_set_param(struct drc *drc, int index, unsigned paramID, float value)
{
	assert(paramID < PARAM_LAST);
	if (paramID < PARAM_LAST)
		drc->parameters[index][paramID] = value;
}

static float drc_get_param(struct drc *drc, int index, unsigned paramID)
{
	assert(paramID < PARAM_LAST);
	return drc->parameters[index][paramID];
}

/* Initializes parameters to default values. */
static void set_default_parameters(struct drc *drc)
{
	float nyquist = drc->sample_rate / 2;
	int i;

	for (i = 0; i < DRC_NUM_KERNELS; i++) {
		float *param = drc->parameters[i];
		param[PARAM_THRESHOLD] = -24; /* dB */
		param[PARAM_KNEE] = 30; /* dB */
		param[PARAM_RATIO] = 12; /* unit-less */
		param[PARAM_ATTACK] = 0.003f; /* seconds */
		param[PARAM_RELEASE] = 0.250f; /* seconds */
		param[PARAM_PRE_DELAY] = DRC_DEFAULT_PRE_DELAY; /* seconds */

		/* Release zone values 0 -> 1. */
		param[PARAM_RELEASE_ZONE1] = 0.09f;
		param[PARAM_RELEASE_ZONE2] = 0.16f;
		param[PARAM_RELEASE_ZONE3] = 0.42f;
		param[PARAM_RELEASE_ZONE4] = 0.98f;

		/* This is effectively a master volume on the compressed
		 * signal */
		param[PARAM_POST_GAIN] = 0; /* dB */
		param[PARAM_ENABLED] = 0;
	}

	drc->parameters[0][PARAM_CROSSOVER_LOWER_FREQ] = 0;
	drc->parameters[1][PARAM_CROSSOVER_LOWER_FREQ] = 200 / nyquist;
	drc->parameters[2][PARAM_CROSSOVER_LOWER_FREQ] = 2000 / nyquist;

	/* These parameters has only one copy */
	drc->parameters[0][PARAM_FILTER_STAGE_GAIN] = 4.4f; /* dB */
	drc->parameters[0][PARAM_FILTER_STAGE_RATIO] = 2;
	drc->parameters[0][PARAM_FILTER_ANCHOR] = 15000 / nyquist;
}

/* Finds the zero and pole for one stage of the emphasis filter */
static void emphasis_stage_roots(float gain, float normalized_frequency,
				 float *zero, float *pole)
{
	float gk = 1 - gain / 20;
	float f1 = normalized_frequency * gk;
	float f2 = normalized_frequency / gk;
	*zero = expf(-f1 * PI_FLOAT);
	*pole = expf(-f2 * PI_FLOAT);
}

/* Calculates the biquad coefficients for two emphasis stages. */
static void emphasis_stage_pair_biquads(float gain, float f1, float f2,
					struct biquad *emphasis,
					struct biquad *deemphasis)
{
	float z1, p1;
	float z2, p2;

	emphasis_stage_roots(gain, f1, &z1, &p1);
	emphasis_stage_roots(gain, f2, &z2, &p2);

	float b0 = 1;
	float b1 = -(z1 + z2);
	float b2 = z1 * z2;
	float a0 = 1;
	float a1 = -(p1 + p2);
	float a2 = p1 * p2;

	/* Gain compensation to make 0dB @ 0Hz */
	float alpha = (a0 + a1 + a2) / (b0 + b1 + b2);

	emphasis->b0 = b0 * alpha;
	emphasis->b1 = b1 * alpha;
	emphasis->b2 = b2 * alpha;
	emphasis->a1 = a1;
	emphasis->a2 = a2;

	float beta = (b0 + b1 + b2) / (a0 + a1 + a2);

	deemphasis->b0 = a0 * beta;
	deemphasis->b1 = a1 * beta;
	deemphasis->b2 = a2 * beta;
	deemphasis->a1 = b1;
	deemphasis->a2 = b2;
}

/* Initializes the emphasis and deemphasis filter */
static void init_emphasis_eq(struct drc *drc)
{
	struct biquad e;
	struct biquad d;
	int i, j;

	float stage_gain = drc_get_param(drc, 0, PARAM_FILTER_STAGE_GAIN);
	float stage_ratio = drc_get_param(drc, 0, PARAM_FILTER_STAGE_RATIO);
	float anchor_freq = drc_get_param(drc, 0,  PARAM_FILTER_ANCHOR);

	drc->emphasis_eq = eq2_new();
	drc->deemphasis_eq = eq2_new();

	for (i = 0; i < 2; i++) {
		emphasis_stage_pair_biquads(stage_gain, anchor_freq,
					    anchor_freq / stage_ratio,
					    &e, &d);
		for (j = 0; j < 2; j++) {
			eq2_append_biquad_direct(drc->emphasis_eq, j, &e);
			eq2_append_biquad_direct(drc->deemphasis_eq, j, &d);
		}
		anchor_freq /= (stage_ratio * stage_ratio);
	}
}

/* Frees the emphasis and deemphasis filter */
static void free_emphasis_eq(struct drc *drc)
{
	eq2_free(drc->emphasis_eq);
	eq2_free(drc->deemphasis_eq);
}

/* Initializes the crossover filter */
static void init_crossover(struct drc *drc)
{
	float freq1 = drc->parameters[1][PARAM_CROSSOVER_LOWER_FREQ];
	float freq2 = drc->parameters[2][PARAM_CROSSOVER_LOWER_FREQ];

	crossover2_init(&drc->xo2, freq1, freq2);
}

/* Initializes the compressor kernels */
static void init_kernel(struct drc *drc)
{
	int i;

	for (i = 0; i < DRC_NUM_KERNELS; i++) {
		dk_init(&drc->kernel[i], drc->sample_rate);

		float db_threshold = drc_get_param(drc, i, PARAM_THRESHOLD);
		float db_knee = drc_get_param(drc, i, PARAM_KNEE);
		float ratio = drc_get_param(drc, i, PARAM_RATIO);
		float attack_time = drc_get_param(drc, i, PARAM_ATTACK);
		float release_time = drc_get_param(drc, i, PARAM_RELEASE);
		float pre_delay_time = drc_get_param(drc, i, PARAM_PRE_DELAY);
		float releaseZone1 = drc_get_param(drc, i, PARAM_RELEASE_ZONE1);
		float releaseZone2 = drc_get_param(drc, i, PARAM_RELEASE_ZONE2);
		float releaseZone3 = drc_get_param(drc, i, PARAM_RELEASE_ZONE3);
		float releaseZone4 = drc_get_param(drc, i, PARAM_RELEASE_ZONE4);
		float db_post_gain = drc_get_param(drc, i, PARAM_POST_GAIN);
		int enabled = drc_get_param(drc, i, PARAM_ENABLED);

		dk_set_parameters(&drc->kernel[i],
				  db_threshold,
				  db_knee,
				  ratio,
				  attack_time,
				  release_time,
				  pre_delay_time,
				  db_post_gain,
				  releaseZone1,
				  releaseZone2,
				  releaseZone3,
				  releaseZone4
			);

		dk_set_enabled(&drc->kernel[i], enabled);
	}
}

/* Frees the compressor kernels */
static void free_kernel(struct drc *drc)
{
	int i;
	for (i = 0; i < DRC_NUM_KERNELS; i++)
		dk_free(&drc->kernel[i]);
}

#if defined(__ARM_NEON__)
#include <arm_neon.h>
static void sum3(float *data, float *data1, float *data2, int n)
{
	float32x4_t x, y, z;
	int count = n / 4;
	int i;

	if (count) {
		__asm__ __volatile(
			"1:                                         \n"
			"vld1.32 {%e[x],%f[x]}, [%[data1]]!         \n"
			"vld1.32 {%e[y],%f[y]}, [%[data2]]!         \n"
			"vld1.32 {%e[z],%f[z]}, [%[data]]           \n"
			"vadd.f32 %q[y], %q[x]                      \n"
			"vadd.f32 %q[z], %q[y]                      \n"
			"vst1.32 {%e[z],%f[z]}, [%[data]]!          \n"
			"subs %[count], #1                          \n"
			"bne 1b                                     \n"
			: /* output */
			  "=r"(data),
			  "=r"(data1),
			  "=r"(data2),
			  "=r"(count),
			  [x]"=&w"(x),
			  [y]"=&w"(y),
			  [z]"=&w"(z)
			: /* input */
			  [data]"0"(data),
			  [data1]"1"(data1),
			  [data2]"2"(data2),
			  [count]"3"(count)
			: /* clobber */
			  "memory", "cc"
			);
	}

	n &= 3;
	for (i = 0; i < n; i++)
		data[i] += data1[i] + data2[i];
}
#elif defined(__SSE3__)
#include <emmintrin.h>
static void sum3(float *data, float *data1, float *data2, int n)
{
	__m128 x, y, z;
	int count = n / 4;
	int i;

	if (count) {
		__asm__ __volatile(
			"1:                                         \n"
			"lddqu (%[data1]), %[x]                     \n"
			"lddqu (%[data2]), %[y]                     \n"
			"lddqu (%[data]), %[z]                      \n"
			"addps %[x], %[y]                           \n"
			"addps %[y], %[z]                           \n"
			"movdqu %[z], (%[data])                     \n"
			"add $16, %[data1]                          \n"
			"add $16, %[data2]                          \n"
			"add $16, %[data]                           \n"
			"sub $1, %[count]                           \n"
			"jne 1b                                     \n"
			: /* output */
			  "=r"(data),
			  "=r"(data1),
			  "=r"(data2),
			  "=r"(count),
			  [x]"=&x"(x),
			  [y]"=&x"(y),
			  [z]"=&x"(z)
			: /* input */
			  [data]"0"(data),
			  [data1]"1"(data1),
			  [data2]"2"(data2),
			  [count]"3"(count)
			: /* clobber */
			  "memory", "cc"
			);
	}

	n &= 3;
	for (i = 0; i < n; i++)
		data[i] += data1[i] + data2[i];
}
#else
static void sum3(float *data, float *data1, float *data2, int n)
{
	int i;
	for (i = 0; i < n; i++)
		data[i] += data1[i] + data2[i];
}
#endif

void drc_process(struct drc *drc, float **data, int frames)
{
	int i;
	float **data1 = drc->data1;
	float **data2 = drc->data2;

	/* Apply pre-emphasis filter if it is not disabled. */
	if (!drc->emphasis_disabled)
		eq2_process(drc->emphasis_eq, data[0], data[1], frames);

	/* Crossover */
	crossover2_process(&drc->xo2, frames, data[0], data[1],
			   data1[0], data1[1], data2[0], data2[1]);

	/* Apply compression to each band of the signal. The processing is
	 * performed in place.
	 */
	dk_process(&drc->kernel[0], data, frames);
	dk_process(&drc->kernel[1], data1, frames);
	dk_process(&drc->kernel[2], data2, frames);

	/* Sum the three bands of signal */
	for (i = 0; i < DRC_NUM_CHANNELS; i++)
		sum3(data[i], data1[i], data2[i], frames);

	/* Apply de-emphasis filter if emphasis is not disabled. */
	if (!drc->emphasis_disabled)
		eq2_process(drc->deemphasis_eq, data[0], data[1], frames);
}
