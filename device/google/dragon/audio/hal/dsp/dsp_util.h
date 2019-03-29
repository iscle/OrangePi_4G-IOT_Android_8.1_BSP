/* Copyright (c) 2013 The Chromium OS Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

#ifndef DSPUTIL_H_
#define DSPUTIL_H_

#ifdef __cplusplus
extern "C" {
#endif

#include <stdint.h>

/* Converts from interleaved int16_t samples to non-interleaved float samples.
 * The int16_t samples have range [-32768, 32767], and the float samples have
 * range [-1.0, 1.0].
 * Args:
 *    input - The interleaved input buffer. Every "channels" samples is a frame.
 *    output - Pointers to output buffers. There are "channels" output buffers.
 *    channels - The number of samples per frame.
 *    frames - The number of frames to convert.
 */
void dsp_util_deinterleave(int16_t *input, float *const *output, int channels,
			   int frames);

/* Converts from non-interleaved float samples to interleaved int16_t samples.
 * The int16_t samples have range [-32768, 32767], and the float samples have
 * range [-1.0, 1.0]. This is the inverse of dsputil_deinterleave().
 * Args:
 *    input - Pointers to input buffers. There are "channels" input buffers.
 *    output - The interleaved output buffer. Every "channels" samples is a
 *        frame.
 *    channels - The number of samples per frame.
 *    frames - The number of frames to convert.
 */
void dsp_util_interleave(float *const *input, int16_t *output, int channels,
			 int frames);

/* Disables denormal numbers in floating point calculation. Denormal numbers
 * happens often in IIR filters, and it can be very slow.
 */
void dsp_enable_flush_denormal_to_zero();

#ifdef __cplusplus
} /* extern "C" */
#endif

#endif /* DSPUTIL_H_ */
