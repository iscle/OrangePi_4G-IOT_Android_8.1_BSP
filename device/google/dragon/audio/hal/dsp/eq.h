/* Copyright (c) 2013 The Chromium OS Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

#ifndef EQ_H_
#define EQ_H_

#ifdef __cplusplus
extern "C" {
#endif

/* An EQ is a chain of biquad filters. See Web Audio API spec for details of the
 * biquad filters and their parameters. */

#include "biquad.h"

/* Maximum number of biquad filters an EQ can have */
#define MAX_BIQUADS_PER_EQ 10

struct eq;

/* Create an EQ. */
struct eq *eq_new();

/* Free an EQ. */
void eq_free(struct eq *eq);

/* Append a biquad filter to an EQ. An EQ can have at most MAX_BIQUADS_PER_EQ
 * biquad filters.
 * Args:
 *    eq - The EQ we want to use.
 *    type - The type of the biquad filter we want to append.
 *    frequency - The value should be in the range [0, 1]. It is relative to
 *        half of the sampling rate.
 *    Q, gain - The meaning depends on the type of the filter. See Web Audio
 *        API for details.
 * Returns:
 *    0 if success. -1 if the eq has no room for more biquads.
 */
int eq_append_biquad(struct eq *eq, enum biquad_type type, float freq, float Q,
		      float gain);

/* Append a biquad filter to an EQ. An EQ can have at most MAX_BIQUADS_PER_EQ
 * biquad filters. This is similar to eq_append_biquad(), but it specifies the
 * biquad coefficients directly.
 * Args:
 *    eq - The EQ we want to use.
 *    biquad - The parameters for the biquad filter.
 * Returns:
 *    0 if success. -1 if the eq has no room for more biquads.
 */
int eq_append_biquad_direct(struct eq *eq, const struct biquad *biquad);

/* Process a buffer of audio data through the EQ.
 * Args:
 *    eq - The EQ we want to use.
 *    data - The array of audio samples.
 *    count - The number of elements in the data array to process.
 */
void eq_process(struct eq *eq, float *data, int count);

#ifdef __cplusplus
} /* extern "C" */
#endif

#endif /* EQ_H_ */
