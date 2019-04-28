/*
 INTEL CONFIDENTIAL
 Copyright 2009 Intel Corporation All Rights Reserved.
 The source code contained or described herein and all documents related to the source code ("Material") are owned by Intel Corporation or its suppliers or licensors. Title to the Material remains with Intel Corporation or its suppliers and licensors. The Material contains trade secrets and proprietary and confidential information of Intel or its suppliers and licensors. The Material is protected by worldwide copyright and trade secret laws and treaty provisions. No part of the Material may be used, copied, reproduced, modified, published, uploaded, posted, transmitted, distributed, or disclosed in any way without Intel’s prior express written permission.

 No license under any patent, copyright, trade secret or other intellectual property right is granted to or conferred upon you by disclosure or delivery of the Materials, either expressly, by implication, inducement, estoppel or otherwise. Any license under such intellectual property rights must be express and approved by Intel in writing.
 */

/**
 * SECTION:mixvideodecodeparams
 * @short_description: VideoDecode parameters
 *
 * A data object which stores videodecode specific parameters.
 */

#include "mixvideodecodeparams.h"

static GType _mix_videodecodeparams_type = 0;
static MixParamsClass *parent_class = NULL;

#define _do_init { _mix_videodecodeparams_type = g_define_type_id; }

gboolean mix_videodecodeparams_copy(MixParams * target, const MixParams * src);
MixParams *mix_videodecodeparams_dup(const MixParams * obj);
gboolean mix_videodecodeparams_equal(MixParams * first, MixParams * second);
static void mix_videodecodeparams_finalize(MixParams * obj);

G_DEFINE_TYPE_WITH_CODE (MixVideoDecodeParams, mix_videodecodeparams,
		MIX_TYPE_PARAMS, _do_init);

static void mix_videodecodeparams_init(MixVideoDecodeParams * self) {
	/* initialize properties here */

	/* TODO: initialize properties */

	self->timestamp = 0;
	self->discontinuity = FALSE;
	self->reserved1 = NULL;
	self->reserved2 = NULL;
	self->reserved3 = NULL;
	self->reserved4 = NULL;
}

static void mix_videodecodeparams_class_init(MixVideoDecodeParamsClass * klass) {
	MixParamsClass *mixparams_class = MIX_PARAMS_CLASS(klass);

	/* setup static parent class */
	parent_class = (MixParamsClass *) g_type_class_peek_parent(klass);

	mixparams_class->finalize = mix_videodecodeparams_finalize;
	mixparams_class->copy = (MixParamsCopyFunction) mix_videodecodeparams_copy;
	mixparams_class->dup = (MixParamsDupFunction) mix_videodecodeparams_dup;
	mixparams_class->equal
			= (MixParamsEqualFunction) mix_videodecodeparams_equal;
}

MixVideoDecodeParams *
mix_videodecodeparams_new(void) {
	MixVideoDecodeParams *ret =
			(MixVideoDecodeParams *) g_type_create_instance(
					MIX_TYPE_VIDEODECODEPARAMS);

	return ret;
}

void mix_videodecodeparams_finalize(MixParams * obj) {
	/* clean up here. */
	/* TODO: cleanup resources allocated */

	/* Chain up parent */
	if (parent_class->finalize) {
		parent_class->finalize(obj);
	}
}

MixVideoDecodeParams *
mix_videodecodeparams_ref(MixVideoDecodeParams * mix) {
	return (MixVideoDecodeParams *) mix_params_ref(MIX_PARAMS(mix));
}

/**
 * mix_videodecodeparams_dup:
 * @obj: a #MixVideoDecodeParams object
 * @returns: a newly allocated duplicate of the object.
 *
 * Copy duplicate of the object.
 */
MixParams *
mix_videodecodeparams_dup(const MixParams * obj) {
	MixParams *ret = NULL;

	if (MIX_IS_VIDEODECODEPARAMS(obj)) {
		MixVideoDecodeParams *duplicate = mix_videodecodeparams_new();
		if (mix_videodecodeparams_copy(MIX_PARAMS(duplicate), MIX_PARAMS(obj))) {
			ret = MIX_PARAMS(duplicate);
		} else {
			mix_videodecodeparams_unref(duplicate);
		}
	}
	return ret;
}

/**
 * mix_videodecodeparams_copy:
 * @target: copy to target
 * @src: copy from src
 * @returns: boolean indicates if copy is successful.
 *
 * Copy instance data from @src to @target.
 */
gboolean mix_videodecodeparams_copy(MixParams * target, const MixParams * src) {
	MixVideoDecodeParams *this_target, *this_src;

	if (MIX_IS_VIDEODECODEPARAMS(target) && MIX_IS_VIDEODECODEPARAMS(src)) {
		// Cast the base object to this child object
		this_target = MIX_VIDEODECODEPARAMS(target);
		this_src = MIX_VIDEODECODEPARAMS(src);

		// TODO: copy properties */

		// Now chainup base class
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
 * mix_videodecodeparams_:
 * @first: first object to compare
 * @second: seond object to compare
 * @returns: boolean indicates if instance are equal.
 *
 * Copy instance data from @src to @target.
 */
gboolean mix_videodecodeparams_equal(MixParams * first, MixParams * second) {
	gboolean ret = FALSE;
	MixVideoDecodeParams *this_first, *this_second;

	if (MIX_IS_VIDEODECODEPARAMS(first) && MIX_IS_VIDEODECODEPARAMS(second)) {
		// Deep compare
		// Cast the base object to this child object

		this_first = MIX_VIDEODECODEPARAMS(first);
		this_second = MIX_VIDEODECODEPARAMS(second);

		/* TODO: add comparison for properties */
		/* if ( first properties ==  sencod properties) */
		{
			// members within this scope equal. chaining up.
			MixParamsClass *klass = MIX_PARAMS_CLASS(parent_class);
			if (klass->equal)
				ret = parent_class->equal(first, second);
			else
				ret = TRUE;
		}
	}

	return ret;
}

#define MIX_VIDEODECODEPARAMS_SETTER_CHECK_INPUT(obj) \
	if(!obj) return MIX_RESULT_NULL_PTR; \
	if(!MIX_IS_VIDEODECODEPARAMS(obj)) return MIX_RESULT_FAIL; \

#define MIX_VIDEODECODEPARAMS_GETTER_CHECK_INPUT(obj, prop) \
	if(!obj || !prop) return MIX_RESULT_NULL_PTR; \
	if(!MIX_IS_VIDEODECODEPARAMS(obj)) return MIX_RESULT_FAIL; \


/* TODO: Add getters and setters for properties. */

MIX_RESULT mix_videodecodeparams_set_timestamp(MixVideoDecodeParams * obj,
		guint64 timestamp) {
	MIX_VIDEODECODEPARAMS_SETTER_CHECK_INPUT (obj);
	obj->timestamp = timestamp;
	return MIX_RESULT_SUCCESS;
}

MIX_RESULT mix_videodecodeparams_get_timestamp(MixVideoDecodeParams * obj,
		guint64 * timestamp) {
	MIX_VIDEODECODEPARAMS_GETTER_CHECK_INPUT (obj, timestamp);
	*timestamp = obj->timestamp;
	return MIX_RESULT_SUCCESS;
}

MIX_RESULT mix_videodecodeparams_set_discontinuity(MixVideoDecodeParams * obj,
		gboolean discontinuity) {
	MIX_VIDEODECODEPARAMS_SETTER_CHECK_INPUT (obj);
	obj->discontinuity = discontinuity;
	return MIX_RESULT_SUCCESS;
}

MIX_RESULT mix_videodecodeparams_get_discontinuity(MixVideoDecodeParams * obj,
		gboolean *discontinuity) {
	MIX_VIDEODECODEPARAMS_GETTER_CHECK_INPUT (obj, discontinuity);
	*discontinuity = obj->discontinuity;
	return MIX_RESULT_SUCCESS;
}

