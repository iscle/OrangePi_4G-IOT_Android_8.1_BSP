/* Copyright (c) 2012 The Chromium OS Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

#ifndef CRAS_DSP_H_
#define CRAS_DSP_H_

#ifdef __cplusplus
extern "C" {
#endif

#include "cras_dsp_pipeline.h"

struct cras_dsp_context;

/* Starts the dsp subsystem. It starts a thread internally to load the
 * plugins. This should be called before other functions.
 * Args:
 *    filename - The ini file where the dsp plugin graph should be read from.
 */
void cras_dsp_init(const char *filename);

/* Stops the dsp subsystem. */
void cras_dsp_stop();

/* Creates a dsp context. The context holds a pipeline and its
 * parameters.  To use the pipeline in the context, first use
 * cras_dsp_load_pipeline() to load it and then use
 * cras_dsp_get_pipeline() to lock it for access.
 * Args:
 *    sample_rate - The sampling rate of the pipeline.
 *    purpose - The purpose of the pipeline, "playback" or "capture".
 * Returns:
 *    A pointer to the dsp context.
 */
struct cras_dsp_context *cras_dsp_context_new(int sample_rate,
					      const char *purpose);

/* Frees a dsp context. */
void cras_dsp_context_free(struct cras_dsp_context *ctx);

/* Sets a configuration variable in the context. */
void cras_dsp_set_variable(struct cras_dsp_context *ctx, const char *key,
			   const char *value);

/* Loads the pipeline to the context. This should be called again when
 * new values of configuration variables may change the plugin
 * graph. The actual loading happens in another thread to avoid
 * blocking the audio thread. */
void cras_dsp_load_pipeline(struct cras_dsp_context *ctx);

/* Locks the pipeline in the context for access. Returns NULL if the
 * pipeline is still being loaded or cannot be loaded. */
struct pipeline *cras_dsp_get_pipeline(struct cras_dsp_context *ctx);

/* Releases the pipeline in the context. This must be called in pair
 * with cras_dsp_get_pipeline() once the client finishes using the
 * pipeline. This should be called in the same thread as
 * cras_dsp_get_pipeline() was called. */
void cras_dsp_put_pipeline(struct cras_dsp_context *ctx);

/* Re-reads the ini file and reloads all pipelines in the system. */
void cras_dsp_reload_ini();

/* Number of channels output. */
unsigned int cras_dsp_num_output_channels(const struct cras_dsp_context *ctx);

/* Number of channels input. */
unsigned int cras_dsp_num_input_channels(const struct cras_dsp_context *ctx);

/* Wait for the previous asynchronous requests to finish. The
 * asynchronous requests include:
 *
 * cras_dsp_context_free()
 * cras_dsp_set_variable()
 * cras_dsp_load_pipeline()
 * cras_dsp_reload_ini()
 * cras_dsp_dump_info()
 *
 * This is mainly used for testing.
 */
void cras_dsp_sync();

#ifdef __cplusplus
} /* extern "C" */
#endif

#endif /* CRAS_DSP_H_ */
