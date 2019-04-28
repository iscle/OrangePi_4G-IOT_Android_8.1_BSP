/*
 INTEL CONFIDENTIAL
 Copyright 2009 Intel Corporation All Rights Reserved.
 The source code contained or described herein and all documents related to the source code ("Material") are owned by Intel Corporation or its suppliers or licensors. Title to the Material remains with Intel Corporation or its suppliers and licensors. The Material contains trade secrets and proprietary and confidential information of Intel or its suppliers and licensors. The Material is protected by worldwide copyright and trade secret laws and treaty provisions. No part of the Material may be used, copied, reproduced, modified, published, uploaded, posted, transmitted, distributed, or disclosed in any way without Intel’s prior express written permission.

 No license under any patent, copyright, trade secret or other intellectual property right is granted to or conferred upon you by disclosure or delivery of the Materials, either expressly, by implication, inducement, estoppel or otherwise. Any license under such intellectual property rights must be express and approved by Intel in writing.
 */

/**
 * SECTION:mixvideoframe
 * @short_description: VideoConfig parameters
 *
 * A data object which stores videoconfig specific parameters.
 */


#include <va/va.h>
#include <va/va_x11.h>
#include "mixvideolog.h"
#include "mixvideoframe.h"
#include "mixvideoframe_private.h"

#define SAFE_FREE(p) if(p) { g_free(p); p = NULL; }

static GType _mix_videoframe_type = 0;
static MixParamsClass *parent_class = NULL;

#define _do_init { _mix_videoframe_type = g_define_type_id; }

gboolean mix_videoframe_copy(MixParams * target, const MixParams * src);
MixParams *mix_videoframe_dup(const MixParams * obj);
gboolean mix_videoframe_equal(MixParams * first, MixParams * second);
static void mix_videoframe_finalize(MixParams * obj);

G_DEFINE_TYPE_WITH_CODE (MixVideoFrame, mix_videoframe, MIX_TYPE_PARAMS,
		_do_init);

#define VIDEOFRAME_PRIVATE(self) ((MixVideoFramePrivate *)((self)->reserved1))
static void mix_videoframe_init(MixVideoFrame * self) {
	/* initialize properties here */
	self->frame_id = VA_INVALID_SURFACE;
	self->timestamp = 0;
	self->discontinuity = FALSE;
	self->frame_structure = VA_FRAME_PICTURE;

	MixVideoFramePrivate *priv = MIX_VIDEOFRAME_GET_PRIVATE(self);
	self->reserved1 = priv;
	self->reserved2 = NULL;
	self->reserved3 = NULL;
	self->reserved4 = NULL;

	/* set pool pointer in private structure to NULL */
	priv -> pool = NULL;

	/* set stuff for skipped frames */
	priv -> is_skipped = FALSE;
	priv -> real_frame = NULL;

	g_static_rec_mutex_init (&priv -> lock);

}

static void mix_videoframe_class_init(MixVideoFrameClass * klass) {
	MixParamsClass *mixparams_class = MIX_PARAMS_CLASS(klass);

	/* setup static parent class */
	parent_class = (MixParamsClass *) g_type_class_peek_parent(klass);

	mixparams_class->finalize = mix_videoframe_finalize;
	mixparams_class->copy = (MixParamsCopyFunction) mix_videoframe_copy;
	mixparams_class->dup = (MixParamsDupFunction) mix_videoframe_dup;
	mixparams_class->equal = (MixParamsEqualFunction) mix_videoframe_equal;

	/* Register and allocate the space the private structure for this object */
	g_type_class_add_private(mixparams_class, sizeof(MixVideoFramePrivate));

}

MixVideoFrame *
mix_videoframe_new(void) {
	MixVideoFrame *ret = (MixVideoFrame *) g_type_create_instance(
			MIX_TYPE_VIDEOFRAME);
	return ret;
}

void mix_videoframe_finalize(MixParams * obj) {
	/* clean up here. */
	MixVideoFrame *self = MIX_VIDEOFRAME (obj);
	MixVideoFramePrivate *priv = VIDEOFRAME_PRIVATE(self);

	g_static_rec_mutex_free (&priv->lock);

	/* Chain up parent */
	if (parent_class->finalize) {
		parent_class->finalize(obj);
	}
}

MixVideoFrame *
mix_videoframe_ref(MixVideoFrame * obj) {

	MixVideoFrame *ret = NULL;
	MixVideoFramePrivate *priv = VIDEOFRAME_PRIVATE(obj);
	g_static_rec_mutex_lock(&priv->lock);
	LOG_I("obj %x, new refcount is %d\n", (guint) obj,
			MIX_PARAMS(obj)->refcount + 1);

	ret = (MixVideoFrame *) mix_params_ref(MIX_PARAMS(obj));
	g_static_rec_mutex_unlock (&priv->lock);
	return ret;
}

void mix_videoframe_unref(MixVideoFrame * obj) {

	if(obj == NULL) {
		LOG_E("obj is NULL\n");
		return;
	}

	MixVideoFramePrivate *priv = VIDEOFRAME_PRIVATE(obj);
	g_static_rec_mutex_lock(&priv->lock);

	LOG_I("obj %x, frame id %d, new refcount is %d\n", (guint) obj,
			(guint) obj->frame_id, MIX_PARAMS(obj)->refcount - 1);

	// Check if we have reduced to 1, in which case we add ourselves to free pool
	// but only do this for real frames, not skipped frames
	if (((MIX_PARAMS(obj)->refcount - 1) == 1) && (!(priv -> is_skipped))) {

		LOG_I("Adding obj %x, frame id %d back to pool\n", (guint) obj,
				(guint) obj->frame_id);

		MixSurfacePool *pool = NULL;
		pool = priv -> pool;
		if(pool == NULL) {
			LOG_E("pool is NULL\n");
			g_static_rec_mutex_unlock (&priv->lock);
			return;
		}
		mix_surfacepool_put(pool, obj);
	}

	//If this is a skipped frame that is being deleted, release the real frame
	if (((MIX_PARAMS(obj)->refcount - 1) == 0) && (priv -> is_skipped)) {

		LOG_I("skipped frame obj %x, releasing real frame %x \n",
				(guint) obj, (guint) priv->real_frame);

		mix_videoframe_unref(priv -> real_frame);
	}

	// Unref through base class
	mix_params_unref(MIX_PARAMS(obj));
	g_static_rec_mutex_unlock (&priv->lock);
}

/**
 * mix_videoframe_dup:
 * @obj: a #MixVideoFrame object
 * @returns: a newly allocated duplicate of the object.
 *
 * Copy duplicate of the object.
 */
MixParams *
mix_videoframe_dup(const MixParams * obj) {
	MixParams *ret = NULL;

	if (MIX_IS_VIDEOFRAME(obj)) {
		MixVideoFrame *duplicate = mix_videoframe_new();
		if (mix_videoframe_copy(MIX_PARAMS(duplicate), MIX_PARAMS(obj))) {
			ret = MIX_PARAMS(duplicate);
		} else {
			mix_videoframe_unref(duplicate);
		}
	}
	return ret;
}

/**
 * mix_videoframe_copy:
 * @target: copy to target
 * @src: copy from src
 * @returns: boolean indicates if copy is successful.
 *
 * Copy instance data from @src to @target.
 */
gboolean mix_videoframe_copy(MixParams * target, const MixParams * src) {
	MixVideoFrame *this_target, *this_src;

	if (MIX_IS_VIDEOFRAME(target) && MIX_IS_VIDEOFRAME(src)) {
		// Cast the base object to this child object
		this_target = MIX_VIDEOFRAME(target);
		this_src = MIX_VIDEOFRAME(src);

		// Free the existing properties

		// Duplicate string
		this_target->frame_id = this_src->frame_id;
		this_target->timestamp = this_src->timestamp;
		this_target->discontinuity = this_src->discontinuity;
		this_target->frame_structure = this_src->frame_structure;

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
 * mix_videoframe_equal:
 * @first: first object to compare
 * @second: seond object to compare
 * @returns: boolean indicates if instance are equal.
 *
 * Copy instance data from @src to @target.
 */
gboolean mix_videoframe_equal(MixParams * first, MixParams * second) {
	gboolean ret = FALSE;
	MixVideoFrame *this_first, *this_second;

	if (MIX_IS_VIDEOFRAME(first) && MIX_IS_VIDEOFRAME(second)) {
		// Deep compare
		// Cast the base object to this child object

		this_first = MIX_VIDEOFRAME(first);
		this_second = MIX_VIDEOFRAME(second);

		/* TODO: add comparison for other properties */
		if (this_first->frame_id == this_second->frame_id
				&& this_first->timestamp == this_second->timestamp
				&& this_first->discontinuity == this_second->discontinuity 
				&& this_first->frame_structure == this_second->frame_structure) {
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

#define MIX_VIDEOFRAME_SETTER_CHECK_INPUT(obj) \
	if(!obj) return MIX_RESULT_NULL_PTR; \
	if(!MIX_IS_VIDEOFRAME(obj)) return MIX_RESULT_FAIL; \

#define MIX_VIDEOFRAME_GETTER_CHECK_INPUT(obj, prop) \
	if(!obj || !prop) return MIX_RESULT_NULL_PTR; \
	if(!MIX_IS_VIDEOFRAME(obj)) return MIX_RESULT_FAIL; \


/* TODO: Add getters and setters for other properties. The following is just an exmaple, not implemented yet. */
MIX_RESULT mix_videoframe_set_frame_id(MixVideoFrame * obj, gulong frame_id) {
	MIX_VIDEOFRAME_SETTER_CHECK_INPUT (obj);
	obj->frame_id = frame_id;

	return MIX_RESULT_SUCCESS;
}

MIX_RESULT mix_videoframe_get_frame_id(MixVideoFrame * obj, gulong * frame_id) {
	MIX_VIDEOFRAME_GETTER_CHECK_INPUT (obj, frame_id);
	*frame_id = obj->frame_id;
	return MIX_RESULT_SUCCESS;
}

MIX_RESULT mix_videoframe_set_ci_frame_idx (MixVideoFrame * obj, guint ci_frame_idx) {
	MIX_VIDEOFRAME_SETTER_CHECK_INPUT (obj);
	obj->ci_frame_idx = ci_frame_idx;

	return MIX_RESULT_SUCCESS;
}

MIX_RESULT mix_videoframe_get_ci_frame_idx (MixVideoFrame * obj, guint * ci_frame_idx) {
	MIX_VIDEOFRAME_GETTER_CHECK_INPUT (obj, ci_frame_idx);
	*ci_frame_idx = obj->ci_frame_idx;
	return MIX_RESULT_SUCCESS;
}

MIX_RESULT mix_videoframe_set_timestamp(MixVideoFrame * obj, guint64 timestamp) {
	MIX_VIDEOFRAME_SETTER_CHECK_INPUT (obj);

	obj->timestamp = timestamp;

	return MIX_RESULT_SUCCESS;
}

MIX_RESULT mix_videoframe_get_timestamp(MixVideoFrame * obj,
		guint64 * timestamp) {
	MIX_VIDEOFRAME_GETTER_CHECK_INPUT (obj, timestamp);
	*timestamp = obj->timestamp;
	return MIX_RESULT_SUCCESS;
}

MIX_RESULT mix_videoframe_set_discontinuity(MixVideoFrame * obj,
		gboolean discontinuity) {
	MIX_VIDEOFRAME_SETTER_CHECK_INPUT (obj);
	obj->discontinuity = discontinuity;
	return MIX_RESULT_SUCCESS;
}

MIX_RESULT mix_videoframe_get_discontinuity(MixVideoFrame * obj,
		gboolean * discontinuity) {
	MIX_VIDEOFRAME_GETTER_CHECK_INPUT (obj, discontinuity);
	*discontinuity = obj->discontinuity;
	return MIX_RESULT_SUCCESS;
}

MIX_RESULT mix_videoframe_set_frame_structure(MixVideoFrame * obj,
		guint32 frame_structure) {
	MIX_VIDEOFRAME_SETTER_CHECK_INPUT (obj);
	obj->frame_structure = frame_structure;
	return MIX_RESULT_SUCCESS;
}

MIX_RESULT mix_videoframe_get_frame_structure(MixVideoFrame * obj,
		guint32* frame_structure) {
	MIX_VIDEOFRAME_GETTER_CHECK_INPUT (obj, frame_structure);
	*frame_structure = obj->frame_structure;
	return MIX_RESULT_SUCCESS;
}

MIX_RESULT mix_videoframe_set_pool(MixVideoFrame * obj, MixSurfacePool * pool) {

	/* set pool pointer in private structure */
	VIDEOFRAME_PRIVATE(obj) -> pool = pool;

	return MIX_RESULT_SUCCESS;
}

MIX_RESULT mix_videoframe_set_frame_type(MixVideoFrame *obj,
		MixFrameType frame_type) {

	VIDEOFRAME_PRIVATE(obj) -> frame_type = frame_type;

	return MIX_RESULT_SUCCESS;
}

MIX_RESULT mix_videoframe_get_frame_type(MixVideoFrame *obj,
		MixFrameType *frame_type) {

	MIX_VIDEOFRAME_GETTER_CHECK_INPUT(obj, frame_type);

	*frame_type = VIDEOFRAME_PRIVATE(obj) -> frame_type;

	return MIX_RESULT_SUCCESS;

}

MIX_RESULT mix_videoframe_set_is_skipped(MixVideoFrame *obj,
		gboolean is_skipped) {

	VIDEOFRAME_PRIVATE(obj) -> is_skipped = is_skipped;

	return MIX_RESULT_SUCCESS;
}

MIX_RESULT mix_videoframe_get_is_skipped(MixVideoFrame *obj,
		gboolean *is_skipped) {

	MIX_VIDEOFRAME_GETTER_CHECK_INPUT(obj, is_skipped);

	*is_skipped = VIDEOFRAME_PRIVATE(obj) -> is_skipped;

	return MIX_RESULT_SUCCESS;
}

MIX_RESULT mix_videoframe_set_real_frame(MixVideoFrame *obj,
		MixVideoFrame *real) {

	VIDEOFRAME_PRIVATE(obj) -> real_frame = real;

	return MIX_RESULT_SUCCESS;
}

MIX_RESULT mix_videoframe_get_real_frame(MixVideoFrame *obj,
		MixVideoFrame **real) {

	MIX_VIDEOFRAME_GETTER_CHECK_INPUT(obj, real);

	*real = VIDEOFRAME_PRIVATE(obj) -> real_frame;

	return MIX_RESULT_SUCCESS;
}

