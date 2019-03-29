/* Copyright (c) 2013 The Chromium OS Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

/* Copyright (C) 2011 Google Inc. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE.WEBKIT file.
 */

#ifndef DRC_KERNEL_H_
#define DRC_KERNEL_H_

#ifdef __cplusplus
extern "C" {
#endif

#define DRC_NUM_CHANNELS 2

struct drc_kernel {
	float sample_rate;

	/* The detector_average is the target gain obtained by looking at the
	 * future samples in the lookahead buffer and applying the compression
	 * curve on them. compressor_gain is the gain applied to the current
	 * samples. compressor_gain moves towards detector_average with the
	 * speed envelope_rate which is calculated once for each division (32
	 * frames). */
	float detector_average;
	float compressor_gain;
	int enabled;
	int processed;

	/* Lookahead section. */
	unsigned last_pre_delay_frames;
	float *pre_delay_buffers[DRC_NUM_CHANNELS];
	int pre_delay_read_index;
	int pre_delay_write_index;

	float max_attack_compression_diff_db;

	/* Amount of input change in dB required for 1 dB of output change.
	 * This applies to the portion of the curve above knee_threshold
	 * (see below).
	 */
	float ratio;
	float slope; /* Inverse ratio. */

	/* The input to output change below the threshold is 1:1. */
	float linear_threshold;
	float db_threshold;

	/* db_knee is the number of dB above the threshold before we enter the
	 * "ratio" portion of the curve.  The portion between db_threshold and
	 * (db_threshold + db_knee) is the "soft knee" portion of the curve
	 * which transitions smoothly from the linear portion to the ratio
	 * portion. knee_threshold is db_to_linear(db_threshold + db_knee).
	 */
	float db_knee;
	float knee_threshold;
	float ratio_base;

	/* Internal parameter for the knee portion of the curve. */
	float K;

	/* The release frames coefficients */
	float kA, kB, kC, kD, kE;

	/* Calculated parameters */
	float master_linear_gain;
	float attack_frames;
	float sat_release_frames_inv_neg;
	float sat_release_rate_at_neg_two_db;
	float knee_alpha, knee_beta;

	/* envelope for the current division */
	float envelope_rate;
	float scaled_desired_gain;
};

/* Initializes a drc kernel */
void dk_init(struct drc_kernel *dk, float sample_rate);

/* Frees a drc kernel */
void dk_free(struct drc_kernel *dk);

/* Sets the parameters of a drc kernel. See drc.h for details */
void dk_set_parameters(struct drc_kernel *dk,
		       float db_threshold,
		       float db_knee,
		       float ratio,
		       float attack_time,
		       float release_time,
		       float pre_delay_time,
		       float db_post_gain,
		       float releaseZone1,
		       float releaseZone2,
		       float releaseZone3,
		       float releaseZone4
		       );

/* Enables or disables a drc kernel */
void dk_set_enabled(struct drc_kernel *dk, int enabled);

/* Performs stereo-linked compression.
 * Args:
 *    dk - The DRC kernel.
 *    data - The pointers to the audio sample buffer. One pointer per channel.
 *    count - The number of audio samples per channel.
 */
void dk_process(struct drc_kernel *dk, float *data_channels[], unsigned count);

#ifdef __cplusplus
} /* extern "C" */
#endif

#endif /* DRC_KERNEL_H_ */
