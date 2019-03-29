/* Copyright (c) 2012 The Chromium OS Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

#include <inttypes.h>
#include <sys/param.h>
#include <syslog.h>

//#include "cras_util.h"
#include "cras_dsp_module.h"
#include "cras_dsp_pipeline.h"
#include "dsp_util.h"

/* We have a static representation of the dsp graph in a "struct ini",
 * and here we will construct a dynamic representation of it in a
 * "struct pipeline". The difference between the static one and the
 * dynamic one is that we will only include the subset of the dsp
 * graph actually needed in the dynamic one (so those plugins that are
 * disabled will not be included). Here are the mapping between the
 * static representation and the dynamic representation:
 *
 *      static                      dynamic
 *  -------------    --------------------------------------
 *  struct ini       struct pipeline
 *  struct plugin    struct instance
 *  strict port      struct audio_port, struct control_port
 *
 * For example, if the ini file specifies these plugins and their
 * connections:
 *
 * [A]
 * output_0={audio}
 * [B]
 * input_0={audio}
 * output_1={result}
 * [C]
 * input_0={result}
 *
 * That is, A connects to B, and B connects to C. If the plugin B is
 * now disabled, in the pipeline we construct there will only be two
 * instances (A and C) and the audio ports on these instances will
 * connect to each other directly, bypassing B.
 */

/* This represents an audio port on an instance. */
struct audio_port {
	struct audio_port *peer;  /* the audio port this port connects to */
	struct plugin *plugin;  /* the plugin corresponds to the instance */
	int original_index;  /* the port index in the plugin */
	int buf_index; /* the buffer index in the pipeline */
};

/* This represents a control port on an instance. */
struct control_port {
	struct control_port *peer;  /* the control port this port connects to */
	struct plugin *plugin;  /* the plugin corresponds to the instance */
	int original_index;  /* the port index in the plugin */
	float value;  /* the value of the control port */
};

DECLARE_ARRAY_TYPE(struct audio_port, audio_port_array);
DECLARE_ARRAY_TYPE(struct control_port, control_port_array);

/* An instance is a dynamic representation of a plugin. We only create
 * an instance when a plugin is needed (data actually flows through it
 * and it is not disabled). An instance also contains a pointer to a
 * struct dsp_module, which is the implementation of the plugin */
struct instance {
	/* The plugin this instance corresponds to */
	struct plugin *plugin;

	/* These are the ports on this instance. The difference
	 * between this and the port array in a struct plugin is that
	 * the ports skip disabled plugins and connect to the upstream
	 * ports directly.
	 */
	audio_port_array input_audio_ports;
	audio_port_array output_audio_ports;
	control_port_array input_control_ports;
	control_port_array output_control_ports;

	/* The implementation of the plugin */
	struct dsp_module *module;

	/* Whether this module's instantiate() function has been called */
	int instantiated;

	/* This caches the value returned from get_properties() of a module */
	int properties;

	/* This is the total buffering delay from source to this instance. It is
	 * in number of frames. */
	int total_delay;
};

DECLARE_ARRAY_TYPE(struct instance, instance_array)

/* An pipeline is a dynamic representation of a dsp ini file. */
struct pipeline {
	/* The purpose of the pipeline. "playback" or "capture" */
	const char *purpose;

	/* The ini file this pipeline comes from */
	struct ini *ini;

	/* All needed instances for this pipeline. It is sorted in an
	 * order that if instance B depends on instance A, then A will
	 * appear in front of B. */
	instance_array instances;

	/* The maximum number of audio buffers that will be used at
	 * the same time for this pipeline */
	int peak_buf;

	/* The audio data buffers */
	float **buffers;

	/* The instance where the audio data flow in */
	struct instance *source_instance;

	/* The instance where the audio data flow out */
	struct instance *sink_instance;

	/* The number of audio channels for this pipeline */
	int input_channels;
	int output_channels;

	/* The audio sampling rate for this pipleine. It is zero if
	 * cras_dsp_pipeline_instantiate() has not been called. */
	int sample_rate;

	/* The total time it takes to run the pipeline, in nanoseconds. */
	int64_t total_time;

	/* The max/min time it takes to run the pipeline, in nanoseconds. */
	int64_t max_time;
	int64_t min_time;

	/* The number of blocks the pipeline. */
	int64_t total_blocks;

	/* The total number of sample frames the pipeline processed */
	int64_t total_samples;
};

static struct instance *find_instance_by_plugin(instance_array *instances,
						struct plugin *plugin)
{
	int i;
	struct instance *instance;

	FOR_ARRAY_ELEMENT(instances, i, instance) {
		if (instance->plugin == plugin)
			return instance;
	}

	return NULL;
}

/* Finds out where the data sent to plugin:index come from. The issue
 * we need to handle here is the previous plugin may be disabled, so
 * we need to go upstream until we find the real origin */
static int find_origin_port(struct ini *ini, instance_array *instances,
			    struct plugin *plugin, int index,
			    struct plugin **origin, int *origin_index)
{
	enum port_type type;
	struct port *port;
	int flow_id;
	struct flow *flow;
	int i, k;
	int found;

	port = ARRAY_ELEMENT(&plugin->ports, index);
	type = port->type;
	flow_id = port->flow_id;
	if (flow_id == INVALID_FLOW_ID)
		return -1;
	flow = ARRAY_ELEMENT(&ini->flows, flow_id);

	/* move to the previous plugin */
	plugin = flow->from;
	index = flow->from_port;

	/* if the plugin is not disabled, it will be pointed by some instance */
	if (find_instance_by_plugin(instances, plugin)) {
		*origin = plugin;
		*origin_index = index;
		return 0;
	}

	/* Now we know the previous plugin is disabled, we need to go
	 * upstream. We assume the k-th output port of the plugin
	 * corresponds to the k-th input port of the plugin (with the
	 * same type) */

	k = 0;
	found = 0;
	FOR_ARRAY_ELEMENT(&plugin->ports, i, port) {
		if (index == i) {
			found = 1;
			break;
		}
		if (port->direction == PORT_OUTPUT && port->type == type)
			k++;
	}
	if (!found)
		return -1;

	found = 0;
	FOR_ARRAY_ELEMENT(&plugin->ports, i, port) {
		if (port->direction == PORT_INPUT && port->type == type) {
			if (k-- == 0) {
				index = i;
				found = 1;
				break;
			}
		}
	}
	if (!found)
		return -1;

	return find_origin_port(ini, instances, plugin, index, origin,
				origin_index);
}

static struct audio_port *find_output_audio_port(instance_array *instances,
						 struct plugin *plugin,
						 int index)
{
	int i;
	struct instance *instance;
	struct audio_port *audio_port;

	instance = find_instance_by_plugin(instances, plugin);
	if (!instance)
		return NULL;

	FOR_ARRAY_ELEMENT(&instance->output_audio_ports, i, audio_port) {
		if (audio_port->original_index == index)
			return audio_port;
	}

	return NULL;
}

static struct control_port *find_output_control_port(instance_array *instances,
						     struct plugin *plugin,
						     int index)
{
	int i;
	struct instance *instance;
	struct control_port *control_port;

	instance = find_instance_by_plugin(instances, plugin);
	if (!instance)
		return NULL;

	FOR_ARRAY_ELEMENT(&instance->output_control_ports, i, control_port) {
		if (control_port->original_index == index)
			return control_port;
	}

	return NULL;
}

static char is_disabled(struct plugin *plugin, struct cras_expr_env *env)
{
	char disabled;
	return (plugin->disable_expr &&
		cras_expr_expression_eval_boolean(
			plugin->disable_expr, env, &disabled) == 0 &&
		disabled == 1);
}

static int topological_sort(struct pipeline *pipeline,
			    struct cras_expr_env *env,
			    struct plugin *plugin, char* visited)
{
	struct port *port;
	struct flow *flow;
	int index;
	int i;
	int flow_id;
	struct instance *instance;
	struct ini *ini = pipeline->ini;

	index = ARRAY_INDEX(&ini->plugins, plugin);
	if (visited[index])
		return 0;
	visited[index] = 1;

	FOR_ARRAY_ELEMENT(&plugin->ports, i, port) {
		if (port->flow_id == INVALID_FLOW_ID)
			continue;
		flow_id = port->flow_id;
		flow = ARRAY_ELEMENT(&ini->flows, flow_id);
		if (!flow->from) {
			syslog(LOG_ERR, "no plugin flows to %s:%d",
			       plugin->title, i);
			return -1;
		}
		if (topological_sort(pipeline, env, flow->from, visited) < 0)
			return -1;
	}

	/* if the plugin is disabled, we don't construct an instance for it */
	if (is_disabled(plugin, env))
		return 0;

	instance = ARRAY_APPEND_ZERO(&pipeline->instances);
	instance->plugin = plugin;

	/* constructs audio and control ports for the instance */
	FOR_ARRAY_ELEMENT(&plugin->ports, i, port) {
		int need_connect = (port->flow_id != INVALID_FLOW_ID &&
				    port->direction == PORT_INPUT);
                struct plugin *origin = NULL;
		int origin_index = 0;

		if (need_connect) {
			if (find_origin_port(ini, &pipeline->instances, plugin,
					     i, &origin, &origin_index) < 0)
				return -1;
		}

		if (port->type == PORT_AUDIO) {
			audio_port_array *audio_port_array =
				(port->direction == PORT_INPUT) ?
				&instance->input_audio_ports :
				&instance->output_audio_ports;
			struct audio_port *audio_port =
				ARRAY_APPEND_ZERO(audio_port_array);
			audio_port->plugin = plugin;
			audio_port->original_index = i;
			if (need_connect) {
				struct audio_port *from;
				from = find_output_audio_port(
					&pipeline->instances, origin,
					origin_index);
				if (!from)
					return -1;
				from->peer = audio_port;
				audio_port->peer = from;
			}
		} else if (port->type == PORT_CONTROL) {
			control_port_array *control_port_array =
				(port->direction == PORT_INPUT) ?
				&instance->input_control_ports :
				&instance->output_control_ports;
			struct control_port *control_port =
				ARRAY_APPEND_ZERO(control_port_array);
			control_port->plugin = plugin;
			control_port->original_index = i;
			control_port->value = port->init_value;
			if (need_connect) {
				struct control_port *from;
				from = find_output_control_port(
					&pipeline->instances, origin,
					origin_index);
				if (!from)
					return -1;
				from->peer = control_port;
				control_port->peer = from;
			}
		}
	}

	return 0;
}

static struct plugin *find_enabled_builtin_plugin(struct ini *ini,
						  const char *label,
						  const char *purpose,
						  struct cras_expr_env *env)
{
	int i;
	struct plugin *plugin, *found = NULL;

	FOR_ARRAY_ELEMENT(&ini->plugins, i, plugin) {
		if (strcmp(plugin->library, "builtin") != 0)
			continue;
		if (strcmp(plugin->label, label) != 0)
			continue;
		if (!plugin->purpose || strcmp(plugin->purpose, purpose) != 0)
			continue;
		if (is_disabled(plugin, env))
			continue;
		if (found) {
			syslog(LOG_ERR, "two %s plugins enabled: %s and %s",
			       label, found->title, plugin->title);
			return NULL;
		}
		found = plugin;
	}

	return found;
}

struct pipeline *cras_dsp_pipeline_create(struct ini *ini,
					  struct cras_expr_env *env,
					  const char *purpose)
{
	struct pipeline *pipeline;
	int n;
	char *visited;
	int rc;
	struct plugin *source = find_enabled_builtin_plugin(
		ini, "source", purpose, env);
	struct plugin *sink = find_enabled_builtin_plugin(
		ini, "sink", purpose, env);

	if (!source || !sink) {
		syslog(LOG_INFO,
		       "no enabled source or sink found %p/%p for %s",
		       source, sink, purpose);
		return NULL;
	}

	pipeline = calloc(1, sizeof(struct pipeline));
	if (!pipeline) {
		syslog(LOG_ERR, "no memory for pipeline");
		return NULL;
	}

	pipeline->ini = ini;
	pipeline->purpose = purpose;
	/* create instances for needed plugins, in the order of dependency */
	n = ARRAY_COUNT(&ini->plugins);
	visited = calloc(1, n);
	rc = topological_sort(pipeline, env, sink, visited);
	free(visited);

	if (rc < 0) {
		syslog(LOG_ERR, "failed to construct pipeline");
		return NULL;
	}

	pipeline->source_instance = find_instance_by_plugin(
		&pipeline->instances, source);
	pipeline->sink_instance = find_instance_by_plugin(
		&pipeline->instances, sink);

	if (!pipeline->source_instance || !pipeline->sink_instance) {
		syslog(LOG_ERR, "source(%p) or sink(%p) missing/disabled?",
		       source, sink);
		cras_dsp_pipeline_free(pipeline);
		return NULL;
	}

	pipeline->input_channels = ARRAY_COUNT(
		&pipeline->source_instance->output_audio_ports);
	pipeline->output_channels = ARRAY_COUNT(
		&pipeline->sink_instance->input_audio_ports);
	if (pipeline->output_channels > pipeline->input_channels) {
		/* Can't increase channel count, no where to put them. */
		syslog(LOG_ERR, "DSP output more channels than input\n");
		cras_dsp_pipeline_free(pipeline);
		return NULL;
	}

	return pipeline;
}

static int load_module(struct plugin *plugin, struct instance *instance)
{
	struct dsp_module *module;
	module = cras_dsp_module_load_builtin(plugin);
	if (!module)
		return -1;
	instance->module = module;
	instance->properties = module->get_properties(module);
	return 0;
}

static void use_buffers(char *busy, audio_port_array *audio_ports)
{
	int i, k = 0;
	struct audio_port *audio_port;

	FOR_ARRAY_ELEMENT(audio_ports, i, audio_port) {
		while (busy[k])
			k++;
		audio_port->buf_index = k;
		busy[k] = 1;
	}
}

static void unuse_buffers(char *busy, audio_port_array *audio_ports)
{
	int i;
	struct audio_port *audio_port;

	FOR_ARRAY_ELEMENT(audio_ports, i, audio_port) {
		busy[audio_port->buf_index] = 0;
	}
}

/* assign which buffer each audio port on each instance should use */
static int allocate_buffers(struct pipeline *pipeline)
{
	int i;
	struct instance *instance;
	int need_buf = 0, peak_buf = 0;
	char *busy;

	/* first figure out how many buffers do we need */
	FOR_ARRAY_ELEMENT(&pipeline->instances, i, instance) {
		int in = ARRAY_COUNT(&instance->input_audio_ports);
		int out = ARRAY_COUNT(&instance->output_audio_ports);

		if (instance->properties & MODULE_INPLACE_BROKEN) {
			/* We cannot reuse input buffer as output
			 * buffer, so we need to use extra buffers */
			need_buf += out;
			peak_buf = MAX(peak_buf, need_buf);
			need_buf -= in;
		} else {
			need_buf += out - in;
			peak_buf = MAX(peak_buf, need_buf);
		}
	}

	/* then allocate the buffers */
	pipeline->peak_buf = peak_buf;
	pipeline->buffers = (float **)calloc(peak_buf, sizeof(float *));

	if (!pipeline->buffers) {
		syslog(LOG_ERR, "failed to allocate buffers");
		return -1;
	}

	for (i = 0; i < peak_buf; i++) {
		size_t size = DSP_BUFFER_SIZE * sizeof(float);
		float *buf = calloc(1, size);
		if (!buf) {
			syslog(LOG_ERR, "failed to allocate buf");
			return -1;
		}
		pipeline->buffers[i] = buf;
	}

	/* Now assign buffer index for each instance's input/output ports */
	busy = calloc(peak_buf, sizeof(*busy));
	FOR_ARRAY_ELEMENT(&pipeline->instances, i, instance) {
		int j;
		struct audio_port *audio_port;

		/* Collect input buffers from upstream */
		FOR_ARRAY_ELEMENT(&instance->input_audio_ports, j, audio_port) {
			audio_port->buf_index = audio_port->peer->buf_index;
		}

		/* If the module has the MODULE_INPLACE_BROKEN flag,
		 * we cannot reuse input buffers as output buffers, so
		 * we need to use extra buffers. For example, in this graph
		 *
		 * [A]
		 * output_0={x}
		 * output_1={y}
		 * output_2={z}
		 * output_3={w}
		 * [B]
		 * input_0={x}
		 * input_1={y}
		 * input_2={z}
		 * input_3={w}
		 * output_4={u}
		 *
		 * Then peak_buf for this pipeline is 4. However if
		 * plugin B has the MODULE_INPLACE_BROKEN flag, then
		 * peak_buf is 5 because plugin B cannot output to the
		 * same buffer used for input.
		 *
		 * This means if we don't have the flag, we can free
		 * the input buffers then allocate the output buffers,
		 * but if we have the flag, we have to allocate the
		 * output buffers before freeing the input buffers.
		 */
		if (instance->properties & MODULE_INPLACE_BROKEN) {
			use_buffers(busy, &instance->output_audio_ports);
			unuse_buffers(busy, &instance->input_audio_ports);
		} else {
			unuse_buffers(busy, &instance->input_audio_ports);
			use_buffers(busy, &instance->output_audio_ports);
		}
	}
	free(busy);

	return 0;
}

int cras_dsp_pipeline_load(struct pipeline *pipeline)
{
	int i;
	struct instance *instance;

	FOR_ARRAY_ELEMENT(&pipeline->instances, i, instance) {
		struct plugin *plugin = instance->plugin;
		if (load_module(plugin, instance) != 0)
			return -1;
	}

	if (allocate_buffers(pipeline) != 0)
		return -1;

	return 0;
}

/* Calculates the total buffering delay of each instance from the source */
static void calculate_audio_delay(struct pipeline *pipeline)
{
	int i;
	struct instance *instance;

	FOR_ARRAY_ELEMENT(&pipeline->instances, i, instance) {
		struct dsp_module *module = instance->module;
		audio_port_array *audio_in = &instance->input_audio_ports;
		struct audio_port *audio_port;
		int delay = 0;
		int j;

		/* Finds the max delay of all modules that provide input to this
		 * instance. */
		FOR_ARRAY_ELEMENT(audio_in, j, audio_port) {
			struct instance *upstream = find_instance_by_plugin(
				&pipeline->instances, audio_port->peer->plugin);
			delay = MAX(upstream->total_delay, delay);
		}

		instance->total_delay = delay + module->get_delay(module);
	}
}

int cras_dsp_pipeline_instantiate(struct pipeline *pipeline, int sample_rate)
{
	int i;
	struct instance *instance;

	FOR_ARRAY_ELEMENT(&pipeline->instances, i, instance) {
		struct dsp_module *module = instance->module;
		if (module->instantiate(module, sample_rate) != 0)
			return -1;
		instance->instantiated = 1;
		syslog(LOG_DEBUG, "instantiate %s", instance->plugin->label);
	}
	pipeline->sample_rate = sample_rate;

	FOR_ARRAY_ELEMENT(&pipeline->instances, i, instance) {
		audio_port_array *audio_in = &instance->input_audio_ports;
		audio_port_array *audio_out = &instance->output_audio_ports;
		control_port_array *control_in = &instance->input_control_ports;
		control_port_array *control_out =
			&instance->output_control_ports;
		int j;
		struct audio_port *audio_port;
		struct control_port *control_port;
		struct dsp_module *module = instance->module;

		/* connect audio ports */
		FOR_ARRAY_ELEMENT(audio_in, j, audio_port) {
			float *buf = pipeline->buffers[audio_port->buf_index];
			module->connect_port(module,
					     audio_port->original_index,
					     buf);
			syslog(LOG_DEBUG, "connect audio buf %d to %s:%d (in)",
			       audio_port->buf_index, instance->plugin->title,
			       audio_port->original_index);
		}
		FOR_ARRAY_ELEMENT(audio_out, j, audio_port) {
			float *buf = pipeline->buffers[audio_port->buf_index];
			module->connect_port(module,
					     audio_port->original_index,
					     buf);
			syslog(LOG_DEBUG, "connect audio buf %d to %s:%d (out)",
			       audio_port->buf_index, instance->plugin->title,
			       audio_port->original_index);
		}

		/* connect control ports */
		FOR_ARRAY_ELEMENT(control_in, j, control_port) {
			/* Note for input control ports which has a
			 * peer, we use &control_port->peer->value, so
			 * we can get the peer port's output value
			 * directly */
			float *value = control_port->peer ?
				&control_port->peer->value :
				&control_port->value;
			module->connect_port(module,
					     control_port->original_index,
					     value);
			syslog(LOG_DEBUG,
			       "connect control (val=%g) to %s:%d (in)",
			       control_port->value, instance->plugin->title,
			       control_port->original_index);
		}
		FOR_ARRAY_ELEMENT(control_out, j, control_port) {
			module->connect_port(module,
					     control_port->original_index,
					     &control_port->value);
			syslog(LOG_DEBUG,
			       "connect control (val=%g) to %s:%d (out)",
			       control_port->value, instance->plugin->title,
			       control_port->original_index);
		}
	}

	calculate_audio_delay(pipeline);
	return 0;
}

void cras_dsp_pipeline_deinstantiate(struct pipeline *pipeline)
{
	int i;
	struct instance *instance;

	FOR_ARRAY_ELEMENT(&pipeline->instances, i, instance) {
		struct dsp_module *module = instance->module;
		if (instance->instantiated) {
			module->deinstantiate(module);
			instance->instantiated = 0;
		}
	}
	pipeline->sample_rate = 0;
}

int cras_dsp_pipeline_get_delay(struct pipeline *pipeline)
{
	return pipeline->sink_instance->total_delay;
}

int cras_dsp_pipeline_get_sample_rate(struct pipeline *pipeline)
{
	return pipeline->sample_rate;
}

int cras_dsp_pipeline_get_num_input_channels(struct pipeline *pipeline)
{
	return pipeline->input_channels;
}

int cras_dsp_pipeline_get_num_output_channels(struct pipeline *pipeline)
{
	return pipeline->output_channels;
}

int cras_dsp_pipeline_get_peak_audio_buffers(struct pipeline *pipeline)
{
	return pipeline->peak_buf;
}

static float *find_buffer(struct pipeline *pipeline,
			  audio_port_array *audio_ports,
			  int index)
{
	int i;
	struct audio_port *audio_port;

	FOR_ARRAY_ELEMENT(audio_ports, i, audio_port) {
		if (audio_port->original_index == index)
			return pipeline->buffers[audio_port->buf_index];
	}
	return NULL;
}

float *cras_dsp_pipeline_get_source_buffer(struct pipeline *pipeline, int index)
{
	return find_buffer(pipeline,
			   &pipeline->source_instance->output_audio_ports,
			   index);
}

float *cras_dsp_pipeline_get_sink_buffer(struct pipeline *pipeline, int index)
{
	return find_buffer(pipeline,
			   &pipeline->sink_instance->input_audio_ports,
			   index);
}

void cras_dsp_pipeline_run(struct pipeline *pipeline, int sample_count)
{
	int i;
	struct instance *instance;

	FOR_ARRAY_ELEMENT(&pipeline->instances, i, instance) {
		struct dsp_module *module = instance->module;
		module->run(module, sample_count);
	}
}

void cras_dsp_pipeline_add_statistic(struct pipeline *pipeline,
				     const struct timespec *time_delta,
				     int samples)
{
	int64_t t;
	if (samples <= 0)
		return;

	t = time_delta->tv_sec * 1000000000LL + time_delta->tv_nsec;

	if (pipeline->total_blocks == 0) {
		pipeline->max_time = t;
		pipeline->min_time = t;
	} else {
		pipeline->max_time = MAX(pipeline->max_time, t);
		pipeline->min_time = MIN(pipeline->min_time, t);
	}

	pipeline->total_blocks++;
	pipeline->total_samples += samples;
	pipeline->total_time += t;
}

void cras_dsp_pipeline_apply(struct pipeline *pipeline,
			     uint8_t *buf, unsigned int frames)
{
	size_t remaining;
	size_t chunk;
	size_t i;
	int16_t *target;
	unsigned int input_channels = pipeline->input_channels;
	unsigned int output_channels = pipeline->output_channels;
	float *source[input_channels];
	float *sink[output_channels];
	//struct timespec begin, end, delta;

	if (!pipeline || frames == 0)
		return;

	//clock_gettime(CLOCK_THREAD_CPUTIME_ID, &begin);

	target = (int16_t *)buf;

	/* get pointers to source and sink buffers */
	for (i = 0; i < input_channels; i++)
		source[i] = cras_dsp_pipeline_get_source_buffer(pipeline, i);
	for (i = 0; i < output_channels; i++)
		sink[i] = cras_dsp_pipeline_get_sink_buffer(pipeline, i);

	remaining = frames;

	/* process at most DSP_BUFFER_SIZE frames each loop */
	while (remaining > 0) {
		chunk = MIN(remaining, (size_t)DSP_BUFFER_SIZE);

		/* deinterleave and convert to float */
		dsp_util_deinterleave(target, source, input_channels, chunk);

		/* Run the pipeline */
		cras_dsp_pipeline_run(pipeline, chunk);

		/* interleave and convert back to int16_t */
		dsp_util_interleave(sink, target, output_channels, chunk);

		target += chunk * output_channels;
		remaining -= chunk;
	}

	//clock_gettime(CLOCK_THREAD_CPUTIME_ID, &end);
	//subtract_timespecs(&end, &begin, &delta);
	//cras_dsp_pipeline_add_statistic(pipeline, &delta, frames);
}

void cras_dsp_pipeline_free(struct pipeline *pipeline)
{
	int i;
	struct instance *instance;

	FOR_ARRAY_ELEMENT(&pipeline->instances, i, instance) {
		struct dsp_module *module = instance->module;
		instance->plugin = NULL;
		ARRAY_FREE(&instance->input_audio_ports);
		ARRAY_FREE(&instance->input_control_ports);
		ARRAY_FREE(&instance->output_audio_ports);
		ARRAY_FREE(&instance->output_control_ports);

		if (module) {
			if (instance->instantiated) {
				module->deinstantiate(module);
				instance->instantiated = 0;
			}
			module->free_module(module);
			instance->module = NULL;
		}
	}

	pipeline->ini = NULL;
	ARRAY_FREE(&pipeline->instances);

	for (i = 0; i < pipeline->peak_buf; i++)
		free(pipeline->buffers[i]);
	free(pipeline->buffers);
	free(pipeline);
}
