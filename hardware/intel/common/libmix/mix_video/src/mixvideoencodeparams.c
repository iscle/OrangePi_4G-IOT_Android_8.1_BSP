/*
 INTEL CONFIDENTIAL
 Copyright 2009 Intel Corporation All Rights Reserved.
 The source code contained or described herein and all documents related to the source code ("Material") are owned by Intel Corporation or its suppliers or licensors. Title to the Material remains with Intel Corporation or its suppliers and licensors. The Material contains trade secrets and proprietary and confidential information of Intel or its suppliers and licensors. The Material is protected by worldwide copyright and trade secret laws and treaty provisions. No part of the Material may be used, copied, reproduced, modified, published, uploaded, posted, transmitted, distributed, or disclosed in any way without Intel’s prior express written permission.

 No license under any patent, copyright, trade secret or other intellectual property right is granted to or conferred upon you by disclosure or delivery of the Materials, either expressly, by implication, inducement, estoppel or otherwise. Any license under such intellectual property rights must be express and approved by Intel in writing.
 */

/**
 * SECTION:mixvideoencodeparams
 * @short_description: VideoDecode parameters
 *
 * A data object which stores videodecode specific parameters.
 */

#include "mixvideoencodeparams.h"

static GType _mix_videoencodeparams_type = 0;
static MixParamsClass *parent_class = NULL;

#define _do_init { _mix_videoencodeparams_type = g_define_type_id; }

gboolean mix_videoencodeparams_copy(MixParams * target, const MixParams * src);
MixParams *mix_videoencodeparams_dup(const MixParams * obj);
gboolean mix_videoencodeparams_equal(MixParams * first, MixParams * second);
static void mix_videoencodeparams_finalize(MixParams * obj);

G_DEFINE_TYPE_WITH_CODE (MixVideoEncodeParams, mix_videoencodeparams,
		MIX_TYPE_PARAMS, _do_init);

static void mix_videoencodeparams_init(MixVideoEncodeParams * self) {
	/* initialize properties here */

	/* TODO: initialize properties */

	self->timestamp = 0;
	self->discontinuity = FALSE;
	self->reserved1 = NULL;
	self->reserved2 = NULL;
	self->reserved3 = NULL;
	self->reserved4 = NULL;
}

static void mix_videoencodeparams_class_init(MixVideoEncodeParamsClass * klass) {
	MixParamsClass *mixparams_class = MIX_PARAMS_CLASS(klass);

	/* setup static parent class */
	parent_class = (MixParamsClass *) g_type_class_peek_parent(klass);

	mixparams_class->finalize = mix_videoencodeparams_finalize;
	mixparams_class->copy = (MixParamsCopyFunction) mix_videoencodeparams_copy;
	mixparams_class->dup = (MixParamsDupFunction) mix_videoencodeparams_dup;
	mixparams_class->equal
			= (MixParamsEqualFunction) mix_videoencodeparams_equal;
}

MixVideoEncodeParams *
mix_videoencodeparams_new(void) {
	MixVideoEncodeParams *ret =
			(MixVideoEncodeParams *) g_type_create_instance(
					MIX_TYPE_VIDEOENCODEPARAMS);

	return ret;
}

void mix_videoencodeparams_finalize(MixParams * obj) {
	/* clean up here. */
	/* TODO: cleanup resources allocated */

	/* Chain up parent */
	if (parent_class->finalize) {
		parent_class->finalize(obj);
	}
}

MixVideoEncodeParams *
mix_videoencodeparams_ref(MixVideoEncodeParams * mix) {
	return (MixVideoEncodeParams *) mix_params_ref(MIX_PARAMS(mix));
}

/**
 * mix_videoencodeparams_dup:
 * @obj: a #MixVideoEncodeParams object
 * @returns: a newly allocated duplicate of the object.
 *
 * Copy duplicate of the object.
 */
MixParams *
mix_videoencodeparams_dup(const MixParams * obj) {
	MixParams *ret = NULL;

	if (MIX_IS_VIDEOENCODEPARAMS(obj)) {
		MixVideoEncodeParams *duplicate = mix_videoencodeparams_new();
		if (mix_videoencodeparams_copy(MIX_PARAMS(duplicate), MIX_PARAMS(obj))) {
			ret = MIX_PARAMS(duplicate);
		} else {
			mix_videoencodeparams_unref(duplicate);
		}
	}
	return ret;
}

/**
 * mix_videoencodeparams_copy:
 * @target: copy to target
 * @src: copy from src
 * @returns: boolean indicates if copy is successful.
 *
 * Copy instance data from @src to @target.
 */
gboolean mix_videoencodeparams_copy(MixParams * target, const MixParams * src) {
	MixVideoEncodeParams *this_target, *this_src;

	if (MIX_IS_VIDEOENCODEPARAMS(target) && MIX_IS_VIDEOENCODEPARAMS(src)) {
		// Cast the base object to this child object
		this_target = MIX_VIDEOENCODEPARAMS(target);
		this_src = MIX_VIDEOENCODEPARAMS(src);

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
 * mix_videoencodeparams_:
 * @first: first object to compare
 * @second: seond object to compare
 * @returns: boolean indicates if instance are equal.
 *
 * Copy instance data from @src to @target.
 */
gboolean mix_videoencodeparams_equal(MixParams * first, MixParams * second) {
	gboolean ret = FALSE;
	MixVideoEncodeParams *this_first, *this_second;

	if (MIX_IS_VIDEOENCODEPARAMS(first) && MIX_IS_VIDEOENCODEPARAMS(second)) {
		// Deep compare
		// Cast the base object to this child object

		this_first = MIX_VIDEOENCODEPARAMS(first);
		this_second = MIX_VIDEOENCODEPARAMS(second);

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

#define MIX_VIDEOENCODEPARAMS_SETTER_CHECK_INPUT(obj) \
	if(!obj) return MIX_RESULT_NULL_PTR; \
	if(!MIX_IS_VIDEOENCODEPARAMS(obj)) return MIX_RESULT_FAIL; \

#define MIX_VIDEOENCODEPARAMS_GETTER_CHECK_INPUT(obj, prop) \
	if(!obj || !prop) return MIX_RESULT_NULL_PTR; \
	if(!MIX_IS_VIDEOENCODEPARAMS(obj)) return MIX_RESULT_FAIL; \


/* TODO: Add getters and setters for properties. */

MIX_RESULT mix_videoencodeparams_set_timestamp(MixVideoEncodeParams * obj,
		guint64 timestamp) {
	MIX_VIDEOENCODEPARAMS_SETTER_CHECK_INPUT (obj);
	obj->timestamp = timestamp;
	return MIX_RESULT_SUCCESS;
}

MIX_RESULT mix_videoencodeparams_get_timestamp(MixVideoEncodeParams * obj,
		guint64 * timestamp) {
	MIX_VIDEOENCODEPARAMS_GETTER_CHECK_INPUT (obj, timestamp);
	*timestamp = obj->timestamp;
	return MIX_RESULT_SUCCESS;
}

MIX_RESULT mix_videoencodeparams_set_discontinuity(MixVideoEncodeParams * obj,
		gboolean discontinuity) {
	MIX_VIDEOENCODEPARAMS_SETTER_CHECK_INPUT (obj);
	obj->discontinuity = discontinuity;
	return MIX_RESULT_SUCCESS;
}

MIX_RESULT mix_videoencodeparams_get_discontinuity(MixVideoEncodeParams * obj,
		gboolean *discontinuity) {
	MIX_VIDEOENCODEPARAMS_GETTER_CHECK_INPUT (obj, discontinuity);
	*discontinuity = obj->discontinuity;
	return MIX_RESULT_SUCCESS;
}

