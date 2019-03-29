/* Copyright (c) 2012 The Chromium OS Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

#ifndef CRAS_DSP_INI_H_
#define CRAS_DSP_INI_H_

#ifdef __cplusplus
extern "C" {
#endif

#include "iniparser.h"

#include "array.h"
#include "cras_expr.h"

enum port_direction {
	PORT_INPUT,
	PORT_OUTPUT,
};

enum port_type {
	PORT_CONTROL,
	PORT_AUDIO,
};

#define INVALID_FLOW_ID -1

struct port {
	enum port_direction direction;
	enum port_type type;

	/* This is only used if there is a flow connects to this port,
	   -1 otherwise (i.e. the port has a constant input/output) */
	int flow_id;

	/* This is only used if type is PORT_CONTROL */
	float init_value;
};

DECLARE_ARRAY_TYPE(struct port, port_array)

struct plugin {
	const char *title;
	const char *library;  /* file name like "plugin.so" */
	const char *label;    /* label like "Eq" */
	const char *purpose;  /* like "playback" or "capture" */
	struct cras_expr_expression *disable_expr;  /* the disable expression of
					     this plugin */
	port_array ports;
};

struct flow {
	enum port_type type;  /* the type of the ports this flow connects to */
	const char *name;
	struct plugin *from;
	struct plugin *to;
	int from_port;
	int to_port;
};

DECLARE_ARRAY_TYPE(struct plugin, plugin_array)
DECLARE_ARRAY_TYPE(struct flow, flow_array)

struct ini {
	dictionary *dict;
	plugin_array plugins;
	flow_array flows;
};

/* Reads the ini file into the ini structure */
struct ini *cras_dsp_ini_create(const char *ini_filename);
/* Frees the dsp structure. */
void cras_dsp_ini_free(struct ini *ini);

#ifdef __cplusplus
} /* extern "C" */
#endif

#endif /* CRAS_DSP_INI_H_ */
