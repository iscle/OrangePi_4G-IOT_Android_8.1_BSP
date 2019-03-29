/* Copyright (c) 2013 The Chromium OS Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

#ifndef CROSSOVER2_H_
#define CROSSOVER2_H_

#ifdef __cplusplus
extern "C" {
#endif

/* "crossover2" is a two channel version of the "crossover" filter. It processes
 * two channels of data at once to increase performance. */

/* An LR4 filter is two biquads with the same parameters connected in series:
 *
 * x -- [BIQUAD] -- y -- [BIQUAD] -- z
 *
 * Both biquad filter has the same parameter b[012] and a[12],
 * The variable [xyz][12][LR] keep the history values.
 */
struct lr42 {
	float b0, b1, b2;
	float a1, a2;
	float x1L, x1R, x2L, x2R;
	float y1L, y1R, y2L, y2R;
	float z1L, z1R, z2L, z2R;
};

/* Three bands crossover filter:
 *
 * INPUT --+-- lp0 --+-- lp1 --+---> LOW (0)
 *         |         |         |
 *         |         \-- hp1 --/
 *         |
 *         \-- hp0 --+-- lp2 ------> MID (1)
 *                   |
 *                   \-- hp2 ------> HIGH (2)
 *
 *            [f0]       [f1]
 *
 * Each lp or hp is an LR4 filter, which consists of two second-order
 * lowpass or highpass butterworth filters.
 */
struct crossover2 {
	struct lr42 lp[3], hp[3];
};

/* Initializes a crossover2 filter
 * Args:
 *    xo2 - The crossover2 filter we want to initialize.
 *    freq1 - The normalized frequency splits low and mid band.
 *    freq2 - The normalized frequency splits mid and high band.
 */
void crossover2_init(struct crossover2 *xo2, float freq1, float freq2);

/* Splits input samples to three bands.
 * Args:
 *    xo2 - The crossover2 filter to use.
 *    count - The number of input samples.
 *    data0L, data0R - The input samples, also the place to store low band
 *                     output.
 *    data1L, data1R - The place to store mid band output.
 *    data2L, data2R - The place to store high band output.
 */
void crossover2_process(struct crossover2 *xo2, int count,
			float *data0L, float *data0R,
			float *data1L, float *data1R,
			float *data2L, float *data2R);

#ifdef __cplusplus
} /* extern "C" */
#endif

#endif /* CROSSOVER2_H_ */
