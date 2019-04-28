/*
 INTEL CONFIDENTIAL
 Copyright 2009 Intel Corporation All Rights Reserved.
 The source code contained or described herein and all documents related to the source code ("Material") are owned by Intel Corporation or its suppliers or licensors. Title to the Material remains with Intel Corporation or its suppliers and licensors. The Material contains trade secrets and proprietary and confidential information of Intel or its suppliers and licensors. The Material is protected by worldwide copyright and trade secret laws and treaty provisions. No part of the Material may be used, copied, reproduced, modified, published, uploaded, posted, transmitted, distributed, or disclosed in any way without Intel’s prior express written permission.

 No license under any patent, copyright, trade secret or other intellectual property right is granted to or conferred upon you by disclosure or delivery of the Materials, either expressly, by implication, inducement, estoppel or otherwise. Any license under such intellectual property rights must be express and approved by Intel in writing.
 */

/**
 * SECTION:mixvideorenderparams
 * @short_description: VideoRender parameters
 *
 * A data object which stores videorender specific parameters.
 */
#include <va/va.h>             /* libVA */
#include <glib-object.h>

#include "mixvideorenderparams.h"
#include "mixvideorenderparams_internal.h"

#include <string.h>

static GType _mix_videorenderparams_type = 0;
static MixParamsClass *parent_class = NULL;

#define _do_init { _mix_videorenderparams_type = g_define_type_id; }

gboolean mix_videorenderparams_copy(MixParams * target, const MixParams * src);
MixParams *mix_videorenderparams_dup(const MixParams * obj);
gboolean mix_videorenderparams_equal(MixParams * first, MixParams * second);
static void mix_videorenderparams_finalize(MixParams * obj);

G_DEFINE_TYPE_WITH_CODE (MixVideoRenderParams, mix_videorenderparams,
		MIX_TYPE_PARAMS, _do_init);

static void mix_videorenderparams_init(MixVideoRenderParams * self) {

	MixVideoRenderParamsPrivate *priv = MIX_VIDEORENDERPARAMS_GET_PRIVATE(self);
	priv->va_cliprects = NULL;
	self->reserved = priv;

	/* initialize properties here */
	self->display = NULL;
	memset(&(self->src_rect), 0, sizeof(MixRect));
	memset(&(self->dst_rect), 0, sizeof(MixRect));

	self->clipping_rects = NULL;
	self->number_of_clipping_rects = 0;

	/* TODO: initialize other properties */
	self->reserved1 = NULL;
	self->reserved2 = NULL;
	self->reserved3 = NULL;
	self->reserved4 = NULL;
}

static void mix_videorenderparams_class_init(MixVideoRenderParamsClass * klass) {
	MixParamsClass *mixparams_class = MIX_PARAMS_CLASS(klass);

	/* setup static parent class */
	parent_class = (MixParamsClass *) g_type_class_peek_parent(klass);

	mixparams_class->finalize = mix_videorenderparams_finalize;
	mixparams_class->copy = (MixParamsCopyFunction) mix_videorenderparams_copy;
	mixparams_class->dup = (MixParamsDupFunction) mix_videorenderparams_dup;
	mixparams_class->equal
			= (MixParamsEqualFunction) mix_videorenderparams_equal;

	/* Register and allocate the space the private structure for this object */
	g_type_class_add_private(mixparams_class, sizeof(MixVideoRenderParamsPrivate));
}

MixVideoRenderParams *
mix_videorenderparams_new(void) {
	MixVideoRenderParams *ret =
			(MixVideoRenderParams *) g_type_create_instance(
					MIX_TYPE_VIDEORENDERPARAMS);

	return ret;
}

void mix_videorenderparams_finalize(MixParams * obj) {
	/* clean up here. */

	MixVideoRenderParams *self = MIX_VIDEORENDERPARAMS(obj);
	MixVideoRenderParamsPrivate *priv =
			(MixVideoRenderParamsPrivate *) self->reserved;

	if (self->clipping_rects) {
		g_free(self->clipping_rects);
		self->clipping_rects = NULL;
	}

	if (priv->va_cliprects) {
		g_free(self->clipping_rects);
		priv->va_cliprects = NULL;
	}

	self->number_of_clipping_rects = 0;

	if (self->display) {
		mix_display_unref(self->display);
		self->display = NULL;
	}

	/* TODO: cleanup other resources allocated */

	/* Chain up parent */
	if (parent_class->finalize) {
		parent_class->finalize(obj);
	}
}

MixVideoRenderParams *
mix_videorenderparams_ref(MixVideoRenderParams * mix) {
	return (MixVideoRenderParams *) mix_params_ref(MIX_PARAMS(mix));
}

/**
 * mix_videorenderparams_dup:
 * @obj: a #MixVideoRenderParams object
 * @returns: a newly allocated duplicate of the object.
 *
 * Copy duplicate of the object.
 */
MixParams *
mix_videorenderparams_dup(const MixParams * obj) {
	MixParams *ret = NULL;

	if (MIX_IS_VIDEORENDERPARAMS(obj)) {
		MixVideoRenderParams *duplicate = mix_videorenderparams_new();
		if (mix_videorenderparams_copy(MIX_PARAMS(duplicate), MIX_PARAMS(obj))) {
			ret = MIX_PARAMS(duplicate);
		} else {
			mix_videorenderparams_unref(duplicate);
		}
	}
	return ret;
}

/**
 * mix_videorenderparams_copy:
 * @target: copy to target
 * @src: copy from src
 * @returns: boolean indicates if copy is successful.
 *
 * Copy instance data from @src to @target.
 */
gboolean mix_videorenderparams_copy(MixParams * target, const MixParams * src) {

	MixVideoRenderParams *this_target, *this_src;
	MIX_RESULT mix_result = MIX_RESULT_FAIL;

	if (target == src) {
		return TRUE;
	}

	if (MIX_IS_VIDEORENDERPARAMS(target) && MIX_IS_VIDEORENDERPARAMS(src)) {

		// Cast the base object to this child object
		this_target = MIX_VIDEORENDERPARAMS(target);
		this_src = MIX_VIDEORENDERPARAMS(src);

		mix_result = mix_videorenderparams_set_display(this_target,
				this_src->display);
		if (mix_result != MIX_RESULT_SUCCESS) {
			return FALSE;
		}

		mix_result = mix_videorenderparams_set_clipping_rects(this_target,
				this_src->clipping_rects, this_src->number_of_clipping_rects);

		if (mix_result != MIX_RESULT_SUCCESS) {
			return FALSE;
		}

		this_target->src_rect = this_src->src_rect;
		this_target->dst_rect = this_src->dst_rect;

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

gboolean mix_rect_equal(MixRect rc1, MixRect rc2) {

	if (rc1.x == rc2.x && rc1.y == rc2.y && rc1.width == rc2.width
			&& rc1.height == rc2.height) {
		return TRUE;
	}

	return FALSE;
}

/**
 * mix_videorenderparams_:
 * @first: first object to compare
 * @second: seond object to compare
 * @returns: boolean indicates if instance are equal.
 *
 * Copy instance data from @src to @target.
 */
gboolean mix_videorenderparams_equal(MixParams * first, MixParams * second) {
	gboolean ret = FALSE;
	MixVideoRenderParams *this_first, *this_second;

	if (MIX_IS_VIDEORENDERPARAMS(first) && MIX_IS_VIDEORENDERPARAMS(second)) {
		// Deep compare
		// Cast the base object to this child object

		this_first = MIX_VIDEORENDERPARAMS(first);
		this_second = MIX_VIDEORENDERPARAMS(second);

		if (mix_display_equal(MIX_DISPLAY(this_first->display), MIX_DISPLAY(
				this_second->display)) && mix_rect_equal(this_first->src_rect,
				this_second->src_rect) && mix_rect_equal(this_first->dst_rect,
				this_second->dst_rect) && this_first->number_of_clipping_rects
				== this_second->number_of_clipping_rects && memcmp(
				(guchar *) this_first->number_of_clipping_rects,
				(guchar *) this_second->number_of_clipping_rects,
				this_first->number_of_clipping_rects) == 0) {
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

#define MIX_VIDEORENDERPARAMS_SETTER_CHECK_INPUT(obj) \
	if(!obj) return MIX_RESULT_NULL_PTR; \
	if(!MIX_IS_VIDEORENDERPARAMS(obj)) return MIX_RESULT_FAIL; \

#define MIX_VIDEORENDERPARAMS_GETTER_CHECK_INPUT(obj, prop) \
	if(!obj || !prop) return MIX_RESULT_NULL_PTR; \
	if(!MIX_IS_VIDEORENDERPARAMS(obj)) return MIX_RESULT_FAIL; \


/* TODO: Add getters and setters for other properties. The following is just an exmaple, not implemented yet. */

MIX_RESULT mix_videorenderparams_set_display(MixVideoRenderParams * obj,
		MixDisplay * display) {

	MIX_VIDEORENDERPARAMS_SETTER_CHECK_INPUT (obj);

	if (obj->display) {
		mix_display_unref(obj->display);
		obj->display = NULL;
	}

	/* dup */
	if (display) {
		obj->display = mix_display_dup(display);
	}

	return MIX_RESULT_SUCCESS;
}

MIX_RESULT mix_videorenderparams_get_display(MixVideoRenderParams * obj,
		MixDisplay ** display) {

	MIX_VIDEORENDERPARAMS_GETTER_CHECK_INPUT (obj, display);

	/* dup? */
	if (obj->display) {
		*display = mix_display_dup(obj->display);
	}

	return MIX_RESULT_SUCCESS;
}

MIX_RESULT mix_videorenderparams_set_src_rect(MixVideoRenderParams * obj,
		MixRect src_rect) {

	MIX_VIDEORENDERPARAMS_SETTER_CHECK_INPUT (obj);

	obj->src_rect = src_rect;

	return MIX_RESULT_SUCCESS;
}

MIX_RESULT mix_videorenderparams_get_src_rect(MixVideoRenderParams * obj,
		MixRect * src_rect) {

	MIX_VIDEORENDERPARAMS_GETTER_CHECK_INPUT (obj, src_rect);

	*src_rect = obj->src_rect;

	return MIX_RESULT_SUCCESS;
}

MIX_RESULT mix_videorenderparams_set_dest_rect(MixVideoRenderParams * obj,
		MixRect dst_rect) {

	MIX_VIDEORENDERPARAMS_SETTER_CHECK_INPUT (obj);

	obj->dst_rect = dst_rect;

	return MIX_RESULT_SUCCESS;
}

MIX_RESULT mix_videorenderparams_get_dest_rect(MixVideoRenderParams * obj,
		MixRect * dst_rect) {

	MIX_VIDEORENDERPARAMS_GETTER_CHECK_INPUT (obj, dst_rect);

	*dst_rect = obj->dst_rect;

	return MIX_RESULT_SUCCESS;
}

MIX_RESULT mix_videorenderparams_set_clipping_rects(MixVideoRenderParams * obj,
		MixRect* clipping_rects, guint number_of_clipping_rects) {

	MixVideoRenderParamsPrivate *priv = NULL;
	MIX_VIDEORENDERPARAMS_SETTER_CHECK_INPUT (obj);

	priv = (MixVideoRenderParamsPrivate *) obj->reserved;


	if (obj->clipping_rects) {
		g_free(obj->clipping_rects);
		obj->clipping_rects = NULL;
		obj->number_of_clipping_rects = 0;
	}

	if(priv->va_cliprects) {
		g_free(priv->va_cliprects);
		priv->va_cliprects = NULL;
	}


	if (clipping_rects && number_of_clipping_rects) {

		gint idx = 0;

		obj->clipping_rects = g_memdup(clipping_rects, number_of_clipping_rects
				* sizeof(MixRect));
		if (!obj->clipping_rects) {
			return MIX_RESULT_NO_MEMORY;
		}

		obj->number_of_clipping_rects = number_of_clipping_rects;

		/* create VARectangle list */
		priv->va_cliprects = g_malloc(number_of_clipping_rects * sizeof(VARectangle));
		if (!priv->va_cliprects) {
			return MIX_RESULT_NO_MEMORY;
		}

		for (idx = 0; idx < number_of_clipping_rects; idx++) {
			priv->va_cliprects[idx].x = clipping_rects[idx].x;
			priv->va_cliprects[idx].y = clipping_rects[idx].y;
			priv->va_cliprects[idx].width = clipping_rects[idx].width;
			priv->va_cliprects[idx].height = clipping_rects[idx].height;
		}
	}

	return MIX_RESULT_SUCCESS;
}

MIX_RESULT mix_videorenderparams_get_clipping_rects(MixVideoRenderParams * obj,
		MixRect ** clipping_rects, guint* number_of_clipping_rects) {

	MIX_VIDEORENDERPARAMS_GETTER_CHECK_INPUT (obj, clipping_rects);
	if (!number_of_clipping_rects) {
		return MIX_RESULT_NULL_PTR;
	}

	*clipping_rects = NULL;
	*number_of_clipping_rects = 0;

	if (obj->clipping_rects && obj->number_of_clipping_rects) {
		*clipping_rects = g_memdup(obj->clipping_rects,
				obj->number_of_clipping_rects * sizeof(MixRect));
		if (!*clipping_rects) {
			return MIX_RESULT_NO_MEMORY;
		}

		*number_of_clipping_rects = obj->number_of_clipping_rects;
	}

	return MIX_RESULT_SUCCESS;
}

/* The mixvideo internal method */
MIX_RESULT mix_videorenderparams_get_cliprects_internal(
		MixVideoRenderParams * obj, VARectangle ** va_cliprects,
		guint* number_of_cliprects) {

	MIX_VIDEORENDERPARAMS_GETTER_CHECK_INPUT (obj, va_cliprects);
	if (!number_of_cliprects) {
		return MIX_RESULT_NULL_PTR;
	}
	MixVideoRenderParamsPrivate *priv =
			(MixVideoRenderParamsPrivate *) obj->reserved;
	
	*va_cliprects = NULL;
	*number_of_cliprects = 0;

	if (priv->va_cliprects && obj->number_of_clipping_rects) {
		*va_cliprects = priv->va_cliprects;
		*number_of_cliprects = obj->number_of_clipping_rects;
	}

	return MIX_RESULT_SUCCESS;

}

/* TODO: implement properties' setters and getters */
