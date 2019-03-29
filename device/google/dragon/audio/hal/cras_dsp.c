/* Copyright (c) 2012 The Chromium OS Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

#include <cutils/log.h>
#include <semaphore.h>
#include "cras_expr.h"
#include "cras_dsp_ini.h"
#include "cras_dsp_pipeline.h"
#include "dsp_util.h"
#include "utlist.h"

#define LOG_TAG "audio_cras_dsp"

/* We have a dsp_context for each pipeline. The context records the
 * parameters used to create a pipeline, so the pipeline can be
 * (re-)loaded later. The pipeline is (re-)loaded in the following
 * cases:
 *
 * (1) The client asks to (re-)load it with cras_load_pipeline().
 * (2) The client asks to reload the ini with cras_reload_ini().
 *
 */
struct cras_dsp_context {
	struct pipeline *pipeline;

	struct cras_expr_env env;
	int sample_rate;
	const char *purpose;
	struct cras_dsp_context *prev, *next;
};

static const char *ini_filename;
static struct ini *ini;
static struct cras_dsp_context *context_list;

static void initialize_environment(struct cras_expr_env *env)
{
	cras_expr_env_install_builtins(env);
	cras_expr_env_set_variable_boolean(env, "disable_eq", 0);
	cras_expr_env_set_variable_boolean(env, "disable_drc", 0);
	cras_expr_env_set_variable_string(env, "dsp_name", "");
}

static struct pipeline *prepare_pipeline(struct cras_dsp_context *ctx)
{
	struct pipeline *pipeline;
	const char *purpose = ctx->purpose;

	if (!ini)
		return NULL;

	pipeline = cras_dsp_pipeline_create(ini, &ctx->env, purpose);

	if (pipeline) {
		ALOGI("pipeline created");
	} else {
		ALOGI("cannot create pipeline");
		goto bail;
	}

	if (cras_dsp_pipeline_load(pipeline) != 0) {
		ALOGE("cannot load pipeline");
		goto bail;
	}

	if (cras_dsp_pipeline_instantiate(pipeline, ctx->sample_rate) != 0) {
		ALOGE("cannot instantiate pipeline");
		goto bail;
	}

	if (cras_dsp_pipeline_get_sample_rate(pipeline) != ctx->sample_rate) {
		ALOGE("pipeline sample rate mismatch (%d vs %d)",
		       cras_dsp_pipeline_get_sample_rate(pipeline),
		       ctx->sample_rate);
		goto bail;
	}

	return pipeline;

bail:
	if (pipeline)
		cras_dsp_pipeline_free(pipeline);
	return NULL;
}

/* Exported functions */
void cras_dsp_set_variable(struct cras_dsp_context *ctx, const char *key,
			     const char *value)
{
	cras_expr_env_set_variable_string(&ctx->env, key, value);
}

void cras_dsp_load_pipeline(struct cras_dsp_context *ctx)
{
	struct pipeline *pipeline, *old_pipeline;

	pipeline = prepare_pipeline(ctx);

	old_pipeline = ctx->pipeline;
	ctx->pipeline = pipeline;

	if (old_pipeline)
		cras_dsp_pipeline_free(old_pipeline);
}

void cras_dsp_reload_ini()
{
	struct ini *old_ini = ini;
	struct cras_dsp_context *ctx;

	ini = cras_dsp_ini_create(ini_filename);
	if (!ini)
		ALOGE("cannot create dsp ini");

	DL_FOREACH(context_list, ctx) {
		cras_dsp_load_pipeline(ctx);
	}

	if (old_ini)
		cras_dsp_ini_free(old_ini);
}

void cras_dsp_init(const char *filename)
{
	dsp_enable_flush_denormal_to_zero();
	ini_filename = strdup(filename);
	cras_dsp_reload_ini();
}

void cras_dsp_stop()
{
	free((char *)ini_filename);
	if (ini) {
		cras_dsp_ini_free(ini);
		ini = NULL;
	}
}

struct cras_dsp_context *cras_dsp_context_new(int sample_rate,
					      const char *purpose)
{
	struct cras_dsp_context *ctx = calloc(1, sizeof(*ctx));

	initialize_environment(&ctx->env);
	ctx->sample_rate = sample_rate;
	ctx->purpose = strdup(purpose);

	DL_APPEND(context_list, ctx);
	return ctx;
}

void cras_dsp_context_free(struct cras_dsp_context *ctx)
{
	DL_DELETE(context_list, ctx);

	if (ctx->pipeline) {
		cras_dsp_pipeline_free(ctx->pipeline);
		ctx->pipeline = NULL;
	}
	cras_expr_env_free(&ctx->env);
	free((char *)ctx->purpose);
	free(ctx);
}

struct pipeline *cras_dsp_get_pipeline(struct cras_dsp_context *ctx)
{
	return ctx->pipeline;
}

void cras_dsp_put_pipeline(struct cras_dsp_context *ctx)
{
}

unsigned int cras_dsp_num_output_channels(const struct cras_dsp_context *ctx)
{
	return cras_dsp_pipeline_get_num_output_channels(ctx->pipeline);
}

unsigned int cras_dsp_num_input_channels(const struct cras_dsp_context *ctx)
{
	return cras_dsp_pipeline_get_num_input_channels(ctx->pipeline);
}

void cras_dsp_sync()
{
}
