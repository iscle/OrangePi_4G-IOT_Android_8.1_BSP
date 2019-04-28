/*
 INTEL CONFIDENTIAL
 Copyright 2009 Intel Corporation All Rights Reserved.
 The source code contained or described herein and all documents related to the source code ("Material") are owned by Intel Corporation or its suppliers or licensors. Title to the Material remains with Intel Corporation or its suppliers and licensors. The Material contains trade secrets and proprietary and confidential information of Intel or its suppliers and licensors. The Material is protected by worldwide copyright and trade secret laws and treaty provisions. No part of the Material may be used, copied, reproduced, modified, published, uploaded, posted, transmitted, distributed, or disclosed in any way without Intel’s prior express written permission.

 No license under any patent, copyright, trade secret or other intellectual property right is granted to or conferred upon you by disclosure or delivery of the Materials, either expressly, by implication, inducement, estoppel or otherwise. Any license under such intellectual property rights must be express and approved by Intel in writing.
 */

/**
 * SECTION:mixvideoinitparams
 * @short_description: VideoInit parameters
 *
 * A data object which stores videoinit specific parameters.
 */

#include "mixvideoinitparams.h"

#define SAFE_FREE(p) if(p) { g_free(p); p = NULL; }

static GType _mix_videoinitparams_type = 0;
static MixParamsClass *parent_class = NULL;

#define _do_init { _mix_videoinitparams_type = g_define_type_id; }

gboolean mix_videoinitparams_copy(MixParams * target, const MixParams * src);
MixParams *mix_videoinitparams_dup(const MixParams * obj);
gboolean mix_videoinitparams_equal(MixParams * first, MixParams * second);
static void mix_videoinitparams_finalize(MixParams * obj);

G_DEFINE_TYPE_WITH_CODE (MixVideoInitParams, mix_videoinitparams,
		MIX_TYPE_PARAMS, _do_init);

static void mix_videoinitparams_init(MixVideoInitParams * self) {

	/* Initialize member varibles */
	self->display = NULL;
	self->reserved1 = NULL;
	self->reserved2 = NULL;
	self->reserved3 = NULL;
	self->reserved4 = NULL;
}

static void mix_videoinitparams_class_init(MixVideoInitParamsClass * klass) {
	MixParamsClass *mixparams_class = MIX_PARAMS_CLASS(klass);

	/* setup static parent class */
	parent_class = (MixParamsClass *) g_type_class_peek_parent(klass);

	mixparams_class->finalize = mix_videoinitparams_finalize;
	mixparams_class->copy = (MixParamsCopyFunction) mix_videoinitparams_copy;
	mixparams_class->dup = (MixParamsDupFunction) mix_videoinitparams_dup;
	mixparams_class->equal = (MixParamsEqualFunction) mix_videoinitparams_equal;
}

MixVideoInitParams *
mix_videoinitparams_new(void) {
	MixVideoInitParams *ret = (MixVideoInitParams *) g_type_create_instance(
			MIX_TYPE_VIDEOINITPARAMS);

	return ret;
}

void mix_videoinitparams_finalize(MixParams * obj) {
	/* clean up here. */

	MixVideoInitParams *self = MIX_VIDEOINITPARAMS(obj);

	/* unref display */
	if (self->display) {
		mix_display_unref(self->display);
		self->display = NULL;
	}

	/* Chain up parent */
	if (parent_class->finalize) {
		parent_class->finalize(obj);
	}
}

MixVideoInitParams *
mix_videoinitparams_ref(MixVideoInitParams * mix) {
	return (MixVideoInitParams *) mix_params_ref(MIX_PARAMS(mix));
}

/**
 * mix_videoinitparams_dup:
 * @obj: a #MixVideoInitParams object
 * @returns: a newly allocated duplicate of the object.
 *
 * Copy duplicate of the object.
 */
MixParams *
mix_videoinitparams_dup(const MixParams * obj) {
	MixParams *ret = NULL;
	if (MIX_IS_VIDEOINITPARAMS(obj)) {
		MixVideoInitParams *duplicate = mix_videoinitparams_new();
		if (mix_videoinitparams_copy(MIX_PARAMS(duplicate), MIX_PARAMS(obj))) {
			ret = MIX_PARAMS(duplicate);
		} else {
			mix_videoinitparams_unref(duplicate);
		}
	}
	return ret;
}

/**
 * mix_videoinitparams_copy:
 * @target: copy to target
 * @src: copy from src
 * @returns: boolean indicates if copy is successful.
 *
 * Copy instance data from @src to @target.
 */
gboolean mix_videoinitparams_copy(MixParams * target, const MixParams * src) {
	MixVideoInitParams *this_target, *this_src;
	if (MIX_IS_VIDEOINITPARAMS(target) && MIX_IS_VIDEOINITPARAMS(src)) {
		/* Cast the base object to this child object */
		this_target = MIX_VIDEOINITPARAMS(target);
		this_src = MIX_VIDEOINITPARAMS(src);
		/* Copy properties from source to target. */

		/* duplicate display */

		this_target->display = mix_display_dup(this_src->display);

		/* Now chainup base class */
		if (parent_class->copy) {
			return parent_class->copy(MIX_PARAMS_CAST(target), MIX_PARAMS_CAST(
					src));
		} else {
			return TRUE;
		}
	}
	return FALSE;
}

/**
 * mix_videoinitparams_equal:
 * @first: first object to compare
 * @second: seond object to compare
 * @returns: boolean indicates if instance are equal.
 *
 * Copy instance data from @src to @target.
 */
gboolean mix_videoinitparams_equal(MixParams * first, MixParams * second) {
	gboolean ret = FALSE;
	MixVideoInitParams *this_first, *this_second;
	this_first = MIX_VIDEOINITPARAMS(first);
	this_second = MIX_VIDEOINITPARAMS(second);
	if (MIX_IS_VIDEOINITPARAMS(first) && MIX_IS_VIDEOINITPARAMS(second)) {
		// Compare member variables
		if (!this_first->display && !this_second->display) {
			ret = TRUE;
		} else if (this_first->display && this_second->display) {

			/* compare MixDisplay */
			ret = mix_display_equal(this_first->display, this_second->display);
		}

		if (ret == FALSE) {
			return FALSE;
		}
		// members within this scope equal. chaining up.
		MixParamsClass *klass = MIX_PARAMS_CLASS(parent_class);
		if (klass->equal)
			ret = parent_class->equal(first, second);
		else
			ret = TRUE;
	}
	return ret;
}

#define MIX_VIDEOINITPARAMS_SETTER_CHECK_INPUT(obj) \
	if(!obj) return MIX_RESULT_NULL_PTR; \
	if(!MIX_IS_VIDEOINITPARAMS(obj)) return MIX_RESULT_FAIL; \

#define MIX_VIDEOINITPARAMS_GETTER_CHECK_INPUT(obj, prop) \
	if(!obj || !prop) return MIX_RESULT_NULL_PTR; \
	if(!MIX_IS_VIDEOINITPARAMS(obj)) return MIX_RESULT_FAIL; \

MIX_RESULT mix_videoinitparams_set_display(MixVideoInitParams * obj,
		MixDisplay * display) {
	MIX_VIDEOINITPARAMS_SETTER_CHECK_INPUT (obj);

	if(obj->display) {
		mix_display_unref(obj->display);
	}
	obj->display = NULL;

	if(display) {
	/*	obj->display = mix_display_dup(display);
		if(!obj->display) {
			return MIX_RESULT_NO_MEMORY;
		}*/
		
		obj->display = mix_display_ref(display);
	}

	return MIX_RESULT_SUCCESS;
}

/*
 Caller is responsible to use g_free to free the memory
 */
MIX_RESULT mix_videoinitparams_get_display(MixVideoInitParams * obj,
		MixDisplay ** display) {
	MIX_VIDEOINITPARAMS_GETTER_CHECK_INPUT (obj, display);

	*display = NULL;
	if(obj->display) {
	/*	*display = mix_display_dup(obj->display);
		if(!*display) {
			return MIX_RESULT_NO_MEMORY;
		}*/
		*display = mix_display_ref(obj->display);
	}

	return MIX_RESULT_SUCCESS;
}
