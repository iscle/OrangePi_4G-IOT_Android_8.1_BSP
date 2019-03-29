/* Copyright (c) 2012 The Chromium OS Authors. All rights reserved.
 * Use of this source code is governed by a BSD-style license that can be
 * found in the LICENSE file.
 */

#ifndef CRAS_DSP_MODULE_H_
#define CRAS_DSP_MODULE_H_

#ifdef __cplusplus
extern "C" {
#endif

#include "cras_dsp_ini.h"

/* Holds the functions we can use on a dsp module. */
struct dsp_module {
	/* Opaque data used by the implementation of this module */
	void *data;

	/* Initializes the module for a given sampling rate. To change
	 * the sampling rate, deinstantiate() must be called before
	 * calling instantiate again.
	 * Args:
	 *    sample_rate - The sampling rate for the audio data, like 44100.
	 * Returns:
	 *    0 if the initialization is successful. -1 otherwise.
	 */
	int (*instantiate)(struct dsp_module *mod, unsigned long sample_rate);

	/* Assigns the memory location for a port of this module.
	 * Args:
	 *    port - The index of the port.
	 *    data_location - The memory address of the data for this port.
	 */
	void (*connect_port)(struct dsp_module *mod, unsigned long port,
			     float *data_location);

	/* Returns the buffering delay of this module. This should be called
	 * only after all input control ports have been connected.
	 * Returns:
	 *     The buffering delay in frames. The value returned should only be
	 * based on the sampling rate and the input control ports values and not
	 * the audio data itself.
	 */
	int (*get_delay)(struct dsp_module *mod);

	/* Processes a block of samples using this module. The memory
	 * location for the input and output data are assigned by the
	 * connect_port() call.
	 * Args:
	 *    sample_count - The number of samples to be processed.
	 */
	void (*run)(struct dsp_module *mod, unsigned long sample_count);

	/* Free resources used by the module. This module can be used
	 * again by calling instantiate() */
	void (*deinstantiate)(struct dsp_module *mod);

	/* Frees all resources used by this module. After calling
	 * free_module(), this struct dsp_module cannot be used
	 * anymore.
	 */
	void (*free_module)(struct dsp_module *mod);

	/* Returns special properties of this module, see the enum
	 * below for details */
	int (*get_properties)(struct dsp_module *mod);
};

enum {
	MODULE_INPLACE_BROKEN = 1  /* See ladspa.h for explanation */
};

struct dsp_module *cras_dsp_module_load_builtin(struct plugin *plugin);

#ifdef __cplusplus
} /* extern "C" */
#endif

#endif /* CRAS_DSP_MODULE_H_ */
