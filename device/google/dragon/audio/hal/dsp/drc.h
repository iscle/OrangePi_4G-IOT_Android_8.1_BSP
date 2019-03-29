/* Copyright (c) 2013 The Chromium OS Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

/* Copyright (C) 2011 Google Inc. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE.WEBKIT file.
 */

#ifndef DRC_H_
#define DRC_H_

#ifdef __cplusplus
extern "C" {
#endif

#include "crossover2.h"
#include "drc_kernel.h"
#include "eq2.h"

/* DRC implements a flexible audio dynamics compression effect such as is
 * commonly used in musical production and game audio. It lowers the volume of
 * the loudest parts of the signal and raises the volume of the softest parts,
 * making the sound richer, fuller, and more controlled.
 *
 * This is a three band stereo DRC. There are three compressor kernels, and each
 * can have its own parameters. If a kernel is disabled, it only delays the
 * signal and does not compress it.
 *
 *                   INPUT
 *                     |
 *                +----------+
 *                | emphasis |
 *                +----------+
 *                     |
 *               +------------+
 *               | crossover  |
 *               +------------+
 *               /     |      \
 *      (low band) (mid band) (high band)
 *             /       |        \
 *         +------+ +------+ +------+
 *         |  drc | |  drc | |  drc |
 *         |kernel| |kernel| |kernel|
 *         +------+ +------+ +------+
 *              \      |        /
 *               \     |       /
 *              +-------------+
 *              |     (+)     |
 *              +-------------+
 *                     |
 *              +------------+
 *              | deemphasis |
 *              +------------+
 *                     |
 *                   OUTPUT
 *
 */

/* The parameters of the DRC compressor.
 *
 * PARAM_THRESHOLD - The value above which the compression starts, in dB.
 * PARAM_KNEE - The value above which the knee region starts, in dB.
 * PARAM_RATIO - The input/output dB ratio after the knee region.
 * PARAM_ATTACK - The time to reduce the gain by 10dB, in seconds.
 * PARAM_RELEASE - The time to increase the gain by 10dB, in seconds.
 * PARAM_PRE_DELAY - The lookahead time for the compressor, in seconds.
 * PARAM_RELEASE_ZONE[1-4] - The adaptive release curve parameters.
 * PARAM_POST_GAIN - The static boost value in output, in dB.
 * PARAM_FILTER_STAGE_GAIN - The gain of each emphasis filter stage.
 * PARAM_FILTER_STAGE_RATIO - The frequency ratio for each emphasis filter stage
 *     to the previous stage.
 * PARAM_FILTER_ANCHOR - The frequency of the first emphasis filter, in
 *     normalized frequency (in [0, 1], relative to half of the sample rate).
 * PARAM_CROSSOVER_LOWER_FREQ - The lower frequency of the band, in normalized
 *     frequency (in [0, 1], relative to half of the sample rate).
 * PARAM_ENABLED - 1 to enable the compressor, 0 to disable it.
 */
enum {
	PARAM_THRESHOLD,
	PARAM_KNEE,
	PARAM_RATIO,
	PARAM_ATTACK,
	PARAM_RELEASE,
	PARAM_PRE_DELAY,
	PARAM_RELEASE_ZONE1,
	PARAM_RELEASE_ZONE2,
	PARAM_RELEASE_ZONE3,
	PARAM_RELEASE_ZONE4,
	PARAM_POST_GAIN,
	PARAM_FILTER_STAGE_GAIN,
	PARAM_FILTER_STAGE_RATIO,
	PARAM_FILTER_ANCHOR,
	PARAM_CROSSOVER_LOWER_FREQ,
	PARAM_ENABLED,
	PARAM_LAST
};

/* The number of compressor kernels (also the number of bands). */
#define DRC_NUM_KERNELS 3

/* The maximum number of frames can be passed to drc_process() call. */
#define DRC_PROCESS_MAX_FRAMES 2048

/* The default value of PARAM_PRE_DELAY in seconds. */
#define DRC_DEFAULT_PRE_DELAY 0.006f

struct drc {
	/* sample rate in Hz */
	float sample_rate;

	/* 1 to disable the emphasis and deemphasis, 0 to enable it. */
	int emphasis_disabled;

	/* parameters holds the tweakable compressor parameters. */
	float parameters[DRC_NUM_KERNELS][PARAM_LAST];

	/* The emphasis filter and deemphasis filter */
	struct eq2 *emphasis_eq;
	struct eq2 *deemphasis_eq;

	/* The crossover filter */
	struct crossover2 xo2;

	/* The compressor kernels */
	struct drc_kernel kernel[DRC_NUM_KERNELS];

	/* Temporary buffer used during drc_process(). The mid and high band
	 * signal is stored in these buffers (the low band is stored in the
	 * original input buffer). */
	float *data1[DRC_NUM_CHANNELS];
	float *data2[DRC_NUM_CHANNELS];
};

/* DRC needs the parameters to be set before initialization. So drc_new() should
 * be called first to allocated an instance, then drc_set_param() is called
 * (multiple times) to set the parameters. Finally drc_init() is called to do
 * the initialization. After that drc_process() can be used to process data. The
 * sequence is:
 *
 *  drc_new();
 *  drc_set_param();
 *  ...
 *  drc_set_param();
 *  drc_init();
 *  drc_process();
 *  ...
 *  drc_process();
 *  drc_free();
 */

/* Allocates a DRC. */
struct drc *drc_new(float sample_rate);

/* Initializes a DRC. */
void drc_init(struct drc *drc);

/* Frees a DRC.*/
void drc_free(struct drc *drc);

/* Processes input data using a DRC.
 * Args:
 *    drc - The DRC we want to use.
 *    float **data - Pointers to input/output data. The input must be stereo
 *        and one channel is pointed by data[0], another pointed by data[1]. The
 *        output data is stored in the same place.
 *    frames - The number of frames to process.
 */
void drc_process(struct drc *drc, float **data, int frames);

/* Sets a parameter for the DRC.
 * Args:
 *    drc - The DRC we want to use.
 *    index - The index of the kernel we want to set its parameter.
 *    paramID - One of the PARAM_* enum constant.
 *    value - The parameter value
 */
void drc_set_param(struct drc *drc, int index, unsigned paramID, float value);

#ifdef __cplusplus
} /* extern "C" */
#endif

#endif /* DRC_H_ */
