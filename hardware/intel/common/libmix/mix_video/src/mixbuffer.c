/* 
 INTEL CONFIDENTIAL
 Copyright 2009 Intel Corporation All Rights Reserved.
 The source code contained or described herein and all documents related to the source code ("Material") are owned by Intel Corporation or its suppliers or licensors. Title to the Material remains with Intel Corporation or its suppliers and licensors. The Material contains trade secrets and proprietary and confidential information of Intel or its suppliers and licensors. The Material is protected by worldwide copyright and trade secret laws and treaty provisions. No part of the Material may be used, copied, reproduced, modified, published, uploaded, posted, transmitted, distributed, or disclosed in any way without Intel’s prior express written permission.

 No license under any patent, copyright, trade secret or other intellectual property right is granted to or conferred upon you by disclosure or delivery of the Materials, either expressly, by implication, inducement, estoppel or otherwise. Any license under such intellectual property rights must be express and approved by Intel in writing.
 */

/**
 * SECTION:mixbuffer
 * @short_description: VideoConfig parameters
 *
 * A data object which stores videoconfig specific parameters.
 */

#include "mixvideolog.h"
#include "mixbuffer.h"
#include "mixbuffer_private.h"

#define SAFE_FREE(p) if(p) { g_free(p); p = NULL; }

static GType _mix_buffer_type = 0;
static MixParamsClass *parent_class = NULL;

#define _do_init { _mix_buffer_type = g_define_type_id; }

gboolean mix_buffer_copy(MixParams * target, const MixParams * src);
MixParams *mix_buffer_dup(const MixParams * obj);
gboolean mix_buffer_equal(MixParams * first, MixParams * second);
static void mix_buffer_finalize(MixParams * obj);

G_DEFINE_TYPE_WITH_CODE (MixBuffer, mix_buffer, MIX_TYPE_PARAMS,
		_do_init);

static void mix_buffer_init(MixBuffer * self) {
	/* initialize properties here */

	MixBufferPrivate *priv = MIX_BUFFER_GET_PRIVATE(self);
	self->reserved = priv;

	priv->pool = NULL;

	self->data = NULL;
	self->size = 0;
	self->token = 0;
	self->callback = NULL;
}

static void mix_buffer_class_init(MixBufferClass * klass) {
	MixParamsClass *mixparams_class = MIX_PARAMS_CLASS(klass);

	/* setup static parent class */
	parent_class = (MixParamsClass *) g_type_class_peek_parent(klass);

	mixparams_class->finalize = mix_buffer_finalize;
	mixparams_class->copy = (MixParamsCopyFunction) mix_buffer_copy;
	mixparams_class->dup = (MixParamsDupFunction) mix_buffer_dup;
	mixparams_class->equal = (MixParamsEqualFunction) mix_buffer_equal;

	/* Register and allocate the space the private structure for this object */
	g_type_class_add_private(mixparams_class, sizeof(MixBufferPrivate));
}

MixBuffer *
mix_buffer_new(void) {
	MixBuffer *ret = (MixBuffer *) g_type_create_instance(MIX_TYPE_BUFFER);
	return ret;
}

void mix_buffer_finalize(MixParams * obj) {
	/* clean up here. */

	/* MixBuffer *self = MIX_BUFFER(obj); */

	/* Chain up parent */
	if (parent_class->finalize) {
		parent_class->finalize(obj);
	}
}

MixBuffer *
mix_buffer_ref(MixBuffer * mix) {
	return (MixBuffer *) mix_params_ref(MIX_PARAMS(mix));
}

/**
 * mix_buffer_dup:
 * @obj: a #MixBuffer object
 * @returns: a newly allocated duplicate of the object.
 *
 * Copy duplicate of the object.
 */
MixParams *
mix_buffer_dup(const MixParams * obj) {
	MixParams *ret = NULL;

	if (MIX_IS_BUFFER(obj)) {
		MixBuffer *duplicate = mix_buffer_new();
		if (mix_buffer_copy(MIX_PARAMS(duplicate), MIX_PARAMS(obj))) {
			ret = MIX_PARAMS(duplicate);
		} else {
			mix_buffer_unref(duplicate);
		}
	}
	return ret;
}

/**
 * mix_buffer_copy:
 * @target: copy to target
 * @src: copy from src
 * @returns: boolean indicates if copy is successful.
 *
 * Copy instance data from @src to @target.
 */
gboolean mix_buffer_copy(MixParams * target, const MixParams * src) {
	MixBuffer *this_target, *this_src;

	if (MIX_IS_BUFFER(target) && MIX_IS_BUFFER(src)) {
		// Cast the base object to this child object
		this_target = MIX_BUFFER(target);
		this_src = MIX_BUFFER(src);

		// Duplicate string
		this_target->data = this_src->data;
		this_target->size = this_src->size;
		this_target->token = this_src->token;
		this_target->callback = this_src->callback;

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
 * mix_buffer_:
 * @first: first object to compare
 * @second: seond object to compare
 * @returns: boolean indicates if instance are equal.
 *
 * Copy instance data from @src to @target.
 */
gboolean mix_buffer_equal(MixParams * first, MixParams * second) {
	gboolean ret = FALSE;
	MixBuffer *this_first, *this_second;

	if (MIX_IS_BUFFER(first) && MIX_IS_BUFFER(second)) {
		// Deep compare
		// Cast the base object to this child object

		this_first = MIX_BUFFER(first);
		this_second = MIX_BUFFER(second);

		if (this_first->data == this_second->data && this_first->size
				== this_second->size && this_first->token == this_second->token
				&& this_first->callback == this_second->callback) {
			// members within this scope equal. chaining up.
			MixParamsClass *klass = MIX_PARAMS_CLASS(parent_class);
			if (klass->equal)
				ret = klass->equal(first, second);
			else
				ret = TRUE;
		}
	}

	return ret;
}

#define MIX_BUFFER_SETTER_CHECK_INPUT(obj) \
	if(!obj) return MIX_RESULT_NULL_PTR; \
	if(!MIX_IS_BUFFER(obj)) return MIX_RESULT_FAIL; \


MIX_RESULT mix_buffer_set_data(MixBuffer * obj, guchar *data, guint size,
		gulong token, MixBufferCallback callback) {
	MIX_BUFFER_SETTER_CHECK_INPUT (obj);

	obj->data = data;
	obj->size = size;
	obj->token = token;
	obj->callback = callback;

	return MIX_RESULT_SUCCESS;
}

MIX_RESULT mix_buffer_set_pool(MixBuffer *obj, MixBufferPool *pool) {

	MIX_BUFFER_SETTER_CHECK_INPUT (obj);
	MixBufferPrivate *priv = (MixBufferPrivate *) obj->reserved;
	priv->pool = pool;

	return MIX_RESULT_SUCCESS;
}

void mix_buffer_unref(MixBuffer * obj) {

	// Unref through base class
	mix_params_unref(MIX_PARAMS(obj));

	LOG_I( "refcount = %d\n", MIX_PARAMS(
			obj)->refcount);

	// Check if we have reduced to 1, in which case we add ourselves to free pool
	if (MIX_PARAMS(obj)->refcount == 1) {
		MixBufferPrivate *priv = (MixBufferPrivate *) obj->reserved;
		g_return_if_fail(priv->pool != NULL);

		if (obj->callback) {
			obj->callback(obj->token, obj->data);
		}
		mix_bufferpool_put(priv->pool, obj);
	}
}

