/* Copyright (c) 2012 The Chromium OS Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

#include <stdlib.h>
#include "cras_dsp_module.h"
#include "drc.h"
#include "dsp_util.h"
#include "eq.h"
#include "eq2.h"

/*
 *  empty module functions (for source and sink)
 */
static int empty_instantiate(struct dsp_module *module,
			     unsigned long sample_rate)
{
	return 0;
}

static void empty_connect_port(struct dsp_module *module, unsigned long port,
			       float *data_location) {}

static int empty_get_delay(struct dsp_module *module)
{
	return 0;
}

static void empty_run(struct dsp_module *module, unsigned long sample_count) {}

static void empty_deinstantiate(struct dsp_module *module) {}

static void empty_free_module(struct dsp_module *module)
{
	free(module);
}

static int empty_get_properties(struct dsp_module *module) { return 0; }

static void empty_init_module(struct dsp_module *module)
{
	module->instantiate = &empty_instantiate;
	module->connect_port = &empty_connect_port;
	module->get_delay = &empty_get_delay;
	module->run = &empty_run;
	module->deinstantiate = &empty_deinstantiate;
	module->free_module = &empty_free_module;
	module->get_properties = &empty_get_properties;
}

/*
 *  invert_lr module functions
 */
static int invert_lr_instantiate(struct dsp_module *module,
				 unsigned long sample_rate)
{
	module->data = calloc(4, sizeof(float*));
	return 0;
}

static void invert_lr_connect_port(struct dsp_module *module,
				   unsigned long port, float *data_location)
{
	float **ports;
	ports = (float **)module->data;
	ports[port] = data_location;
}

static void invert_lr_run(struct dsp_module *module,
			  unsigned long sample_count)
{
	size_t i;
	float **ports = (float **)module->data;

	for (i = 0; i < sample_count; i++) {
		ports[2][i] = -ports[0][i];
		ports[3][i] = ports[1][i];
	}
}

static void invert_lr_deinstantiate(struct dsp_module *module)
{
	free(module->data);
}

static void invert_lr_init_module(struct dsp_module *module)
{
	module->instantiate = &invert_lr_instantiate;
	module->connect_port = &invert_lr_connect_port;
	module->get_delay = &empty_get_delay;
	module->run = &invert_lr_run;
	module->deinstantiate = &invert_lr_deinstantiate;
	module->free_module = &empty_free_module;
	module->get_properties = &empty_get_properties;
}

/*
 *  mix_stereo module functions
 */
static int mix_stereo_instantiate(struct dsp_module *module,
				  unsigned long sample_rate)
{
	module->data = calloc(4, sizeof(float*));
	return 0;
}

static void mix_stereo_connect_port(struct dsp_module *module,
				    unsigned long port, float *data_location)
{
	float **ports;
	ports = (float **)module->data;
	ports[port] = data_location;
}

static void mix_stereo_run(struct dsp_module *module,
			   unsigned long sample_count)
{
	size_t i;
	float tmp;
	float **ports = (float **)module->data;

	for (i = 0; i < sample_count; i++) {
		tmp = ports[0][i] + ports[1][i];
		ports[2][i] = tmp;
		ports[3][i] = tmp;
	}
}

static void mix_stereo_deinstantiate(struct dsp_module *module)
{
	free(module->data);
}

static void mix_stereo_init_module(struct dsp_module *module)
{
	module->instantiate = &mix_stereo_instantiate;
	module->connect_port = &mix_stereo_connect_port;
	module->get_delay = &empty_get_delay;
	module->run = &mix_stereo_run;
	module->deinstantiate = &mix_stereo_deinstantiate;
	module->free_module = &empty_free_module;
	module->get_properties = &empty_get_properties;
}

/*
 *  eq module functions
 */
struct eq_data {
	int sample_rate;
	struct eq *eq;  /* Initialized in the first call of eq_run() */

	/* One port for input, one for output, and 4 parameters per eq */
	float *ports[2 + MAX_BIQUADS_PER_EQ * 4];
};

static int eq_instantiate(struct dsp_module *module, unsigned long sample_rate)
{
	struct eq_data *data;

	module->data = calloc(1, sizeof(struct eq_data));
	data = (struct eq_data *) module->data;
	data->sample_rate = (int) sample_rate;
	return 0;
}

static void eq_connect_port(struct dsp_module *module,
			    unsigned long port, float *data_location)
{
	struct eq_data *data = (struct eq_data *) module->data;
	data->ports[port] = data_location;
}

static void eq_run(struct dsp_module *module, unsigned long sample_count)
{
	struct eq_data *data = (struct eq_data *) module->data;
	if (!data->eq) {
		float nyquist = data->sample_rate / 2;
		int i;

		data->eq = eq_new();
		for (i = 2; i < 2 + MAX_BIQUADS_PER_EQ * 4; i += 4) {
			if (!data->ports[i])
				break;
			int type = (int) *data->ports[i];
			float freq = *data->ports[i+1];
			float Q = *data->ports[i+2];
			float gain = *data->ports[i+3];
			eq_append_biquad(data->eq, type, freq / nyquist, Q,
					 gain);
		}
	}
	if (data->ports[0] != data->ports[1])
		memcpy(data->ports[1], data->ports[0],
		       sizeof(float) * sample_count);
	eq_process(data->eq, data->ports[1], (int) sample_count);
}

static void eq_deinstantiate(struct dsp_module *module)
{
	struct eq_data *data = (struct eq_data *) module->data;
	if (data->eq)
		eq_free(data->eq);
	free(data);
}

static void eq_init_module(struct dsp_module *module)
{
	module->instantiate = &eq_instantiate;
	module->connect_port = &eq_connect_port;
	module->get_delay = &empty_get_delay;
	module->run = &eq_run;
	module->deinstantiate = &eq_deinstantiate;
	module->free_module = &empty_free_module;
	module->get_properties = &empty_get_properties;
}

/*
 *  eq2 module functions
 */
struct eq2_data {
	int sample_rate;
	struct eq2 *eq2;  /* Initialized in the first call of eq2_run() */

	/* Two ports for input, two for output, and 8 parameters per eq pair */
	float *ports[4 + MAX_BIQUADS_PER_EQ2 * 8];
};

static int eq2_instantiate(struct dsp_module *module, unsigned long sample_rate)
{
	struct eq2_data *data;

	module->data = calloc(1, sizeof(struct eq2_data));
	data = (struct eq2_data *) module->data;
	data->sample_rate = (int) sample_rate;
	return 0;
}

static void eq2_connect_port(struct dsp_module *module,
			     unsigned long port, float *data_location)
{
	struct eq2_data *data = (struct eq2_data *) module->data;
	data->ports[port] = data_location;
}

static void eq2_run(struct dsp_module *module, unsigned long sample_count)
{
	struct eq2_data *data = (struct eq2_data *) module->data;
	if (!data->eq2) {
		float nyquist = data->sample_rate / 2;
		int i, channel;

		data->eq2 = eq2_new();
		for (i = 4; i < 4 + MAX_BIQUADS_PER_EQ2 * 8; i += 8) {
			if (!data->ports[i])
				break;
			for (channel = 0; channel < 2; channel++) {
				int k = i + channel * 4;
				int type = (int) *data->ports[k];
				float freq = *data->ports[k+1];
				float Q = *data->ports[k+2];
				float gain = *data->ports[k+3];
				eq2_append_biquad(data->eq2, channel, type,
						  freq / nyquist, Q, gain);
			}
		}
	}


	if (data->ports[0] != data->ports[2])
		memcpy(data->ports[2], data->ports[0],
		       sizeof(float) * sample_count);
	if (data->ports[3] != data->ports[1])
		memcpy(data->ports[3], data->ports[1],
		       sizeof(float) * sample_count);

	eq2_process(data->eq2, data->ports[2], data->ports[3],
		    (int) sample_count);
}

static void eq2_deinstantiate(struct dsp_module *module)
{
	struct eq2_data *data = (struct eq2_data *) module->data;
	if (data->eq2)
		eq2_free(data->eq2);
	free(data);
}

static void eq2_init_module(struct dsp_module *module)
{
	module->instantiate = &eq2_instantiate;
	module->connect_port = &eq2_connect_port;
	module->get_delay = &empty_get_delay;
	module->run = &eq2_run;
	module->deinstantiate = &eq2_deinstantiate;
	module->free_module = &empty_free_module;
	module->get_properties = &empty_get_properties;
}

/*
 *  drc module functions
 */
struct drc_data {
	int sample_rate;
	struct drc *drc;  /* Initialized in the first call of drc_run() */

	/* Two ports for input, two for output, one for disable_emphasis,
	 * and 8 parameters each band */
	float *ports[4 + 1 + 8 * 3];
};

static int drc_instantiate(struct dsp_module *module, unsigned long sample_rate)
{
	struct drc_data *data;

	module->data = calloc(1, sizeof(struct drc_data));
	data = (struct drc_data *) module->data;
	data->sample_rate = (int) sample_rate;
	return 0;
}

static void drc_connect_port(struct dsp_module *module,
			    unsigned long port, float *data_location)
{
	struct drc_data *data = (struct drc_data *) module->data;
	data->ports[port] = data_location;
}

static int drc_get_delay(struct dsp_module *module)
{
	struct drc_data *data = (struct drc_data *) module->data;
	return DRC_DEFAULT_PRE_DELAY * data->sample_rate;
}

static void drc_run(struct dsp_module *module, unsigned long sample_count)
{
	struct drc_data *data = (struct drc_data *) module->data;
	if (!data->drc) {
		int i;
		float nyquist = data->sample_rate / 2;
		struct drc *drc = drc_new(data->sample_rate);

		data->drc = drc;
		drc->emphasis_disabled = (int) *data->ports[4];
		for (i = 0; i < 3; i++) {
			int k = 5 + i * 8;
			float f = *data->ports[k];
			float enable = *data->ports[k+1];
			float threshold = *data->ports[k+2];
			float knee = *data->ports[k+3];
			float ratio = *data->ports[k+4];
			float attack = *data->ports[k+5];
			float release = *data->ports[k+6];
			float boost = *data->ports[k+7];
			drc_set_param(drc, i, PARAM_CROSSOVER_LOWER_FREQ,
				      f / nyquist);
			drc_set_param(drc, i, PARAM_ENABLED, enable);
			drc_set_param(drc, i, PARAM_THRESHOLD, threshold);
			drc_set_param(drc, i, PARAM_KNEE, knee);
			drc_set_param(drc, i, PARAM_RATIO, ratio);
			drc_set_param(drc, i, PARAM_ATTACK, attack);
			drc_set_param(drc, i, PARAM_RELEASE, release);
			drc_set_param(drc, i, PARAM_POST_GAIN, boost);
		}
		drc_init(drc);
	}
	if (data->ports[0] != data->ports[2])
		memcpy(data->ports[2], data->ports[0],
		       sizeof(float) * sample_count);
	if (data->ports[1] != data->ports[3])
		memcpy(data->ports[3], data->ports[1],
		       sizeof(float) * sample_count);

	drc_process(data->drc, &data->ports[2], (int) sample_count);
}

static void drc_deinstantiate(struct dsp_module *module)
{
	struct drc_data *data = (struct drc_data *) module->data;
	if (data->drc)
		drc_free(data->drc);
	free(data);
}

static void drc_init_module(struct dsp_module *module)
{
	module->instantiate = &drc_instantiate;
	module->connect_port = &drc_connect_port;
	module->get_delay = &drc_get_delay;
	module->run = &drc_run;
	module->deinstantiate = &drc_deinstantiate;
	module->free_module = &empty_free_module;
	module->get_properties = &empty_get_properties;
}

/*
 *  builtin module dispatcher
 */
struct dsp_module *cras_dsp_module_load_builtin(struct plugin *plugin)
{
	struct dsp_module *module;
	if (strcmp(plugin->library, "builtin") != 0)
		return NULL;

	module = calloc(1, sizeof(struct dsp_module));

	if (strcmp(plugin->label, "mix_stereo") == 0) {
		mix_stereo_init_module(module);
	} else if (strcmp(plugin->label, "invert_lr") == 0) {
		invert_lr_init_module(module);
	} else if (strcmp(plugin->label, "eq") == 0) {
		eq_init_module(module);
	} else if (strcmp(plugin->label, "eq2") == 0) {
		eq2_init_module(module);
	} else if (strcmp(plugin->label, "drc") == 0) {
		drc_init_module(module);
	} else {
		empty_init_module(module);
	}

	return module;
}
