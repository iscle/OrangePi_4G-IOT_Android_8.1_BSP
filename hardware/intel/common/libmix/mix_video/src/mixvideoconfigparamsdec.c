/*
 INTEL CONFIDENTIAL
 Copyright 2009 Intel Corporation All Rights Reserved.
 The source code contained or described herein and all documents related to the source code ("Material") are owned by Intel Corporation or its suppliers or licensors. Title to the Material remains with Intel Corporation or its suppliers and licensors. The Material contains trade secrets and proprietary and confidential information of Intel or its suppliers and licensors. The Material is protected by worldwide copyright and trade secret laws and treaty provisions. No part of the Material may be used, copied, reproduced, modified, published, uploaded, posted, transmitted, distributed, or disclosed in any way without Intel’s prior express written permission.

 No license under any patent, copyright, trade secret or other intellectual property right is granted to or conferred upon you by disclosure or delivery of the Materials, either expressly, by implication, inducement, estoppel or otherwise. Any license under such intellectual property rights must be express and approved by Intel in writing.
 */

/**
 * SECTION:mixvideoconfigparams
 * @short_description: VideoConfig parameters
 *
 * A data object which stores videoconfig specific parameters.
 */

#include <string.h>
#include "mixvideolog.h"
#include "mixvideoconfigparamsdec.h"

static GType _mix_videoconfigparamsdec_type = 0;
static MixVideoConfigParamsClass *parent_class = NULL;

#define _do_init { _mix_videoconfigparamsdec_type = g_define_type_id; }

gboolean mix_videoconfigparamsdec_copy(MixParams * target, const MixParams * src);
MixParams *mix_videoconfigparamsdec_dup(const MixParams * obj);
gboolean mix_videoconfigparamsdec_equal(MixParams * first, MixParams * second);
static void mix_videoconfigparamsdec_finalize(MixParams * obj);

G_DEFINE_TYPE_WITH_CODE (MixVideoConfigParamsDec, mix_videoconfigparamsdec,
		MIX_TYPE_VIDEOCONFIGPARAMS, _do_init);

static void mix_videoconfigparamsdec_init(MixVideoConfigParamsDec * self) {

	/* initialize properties here */

	self->frame_order_mode = MIX_FRAMEORDER_MODE_DISPLAYORDER;
	memset(&self->header, 0, sizeof(self->header));

	self->mime_type = NULL;

	self->frame_rate_num = 0;
	self->frame_rate_denom = 0;

	self->picture_width = 0;
	self->picture_height = 0;

	self->raw_format = 0;
	self->rate_control = 0;
	self->mixbuffer_pool_size = 0;
	self->extra_surface_allocation = 0;

	/* TODO: initialize other properties */
	self->reserved1 = NULL;
	self->reserved2 = NULL;
	self->reserved3 = NULL;
	self->reserved4 = NULL;
}

static void mix_videoconfigparamsdec_class_init(MixVideoConfigParamsDecClass * klass) {
	MixParamsClass *mixparams_class = MIX_PARAMS_CLASS(klass);

	/* setup static parent class */
	parent_class = (MixVideoConfigParamsClass *) g_type_class_peek_parent(klass);

	mixparams_class->finalize = mix_videoconfigparamsdec_finalize;
	mixparams_class->copy = (MixParamsCopyFunction) mix_videoconfigparamsdec_copy;
	mixparams_class->dup = (MixParamsDupFunction) mix_videoconfigparamsdec_dup;
	mixparams_class->equal
			= (MixParamsEqualFunction) mix_videoconfigparamsdec_equal;
}

MixVideoConfigParamsDec *
mix_videoconfigparamsdec_new(void) {
	MixVideoConfigParamsDec *ret =
			(MixVideoConfigParamsDec *) g_type_create_instance(
					MIX_TYPE_VIDEOCONFIGPARAMSDEC);

	return ret;
}

void mix_videoconfigparamsdec_finalize(MixParams * obj) {

	/* clean up here. */
	MixVideoConfigParamsDec *self = MIX_VIDEOCONFIGPARAMSDEC(obj);
	MixParamsClass *root_class = MIX_PARAMS_CLASS(parent_class);


	/* free header */
	if (self->header.data) {
		g_free(self->header.data);
		memset(&self->header, 0, sizeof(self->header));
	}

	/* free mime_type */
	if (self->mime_type->str)
		g_string_free(self->mime_type, TRUE);
	else
		g_string_free(self->mime_type, FALSE);

	/* Chain up parent */
	if (root_class->finalize) {
		root_class->finalize(obj);
	}
}

MixVideoConfigParamsDec *
mix_videoconfigparamsdec_ref(MixVideoConfigParamsDec * mix) {
	return (MixVideoConfigParamsDec *) mix_params_ref(MIX_PARAMS(mix));
}

/**
 * mix_videoconfigparamsdec_dup:
 * @obj: a #MixVideoConfigParamsDec object
 * @returns: a newly allocated duplicate of the object.
 *
 * Copy duplicate of the object.
 */
MixParams *
mix_videoconfigparamsdec_dup(const MixParams * obj) {
	MixParams *ret = NULL;

	if (MIX_IS_VIDEOCONFIGPARAMSDEC(obj)) {
		MixVideoConfigParamsDec *duplicate = mix_videoconfigparamsdec_new();
		if (mix_videoconfigparamsdec_copy(MIX_PARAMS(duplicate), MIX_PARAMS(obj))) {
			ret = MIX_PARAMS(duplicate);
		} else {
			mix_videoconfigparamsdec_unref(duplicate);
		}
	}

	return ret;
}

/**
 * mix_videoconfigparamsdec_copy:
 * @target: copy to target
 * @src: copy from src
 * @returns: boolean indicates if copy is successful.
 *
 * Copy instance data from @src to @target.
 */
gboolean mix_videoconfigparamsdec_copy(MixParams * target, const MixParams * src) {

	MixVideoConfigParamsDec *this_target, *this_src;
	MIX_RESULT mix_result = MIX_RESULT_FAIL;
	MixParamsClass *root_class = MIX_PARAMS_CLASS(parent_class);

	LOG_V( "Begin\n");

	if (MIX_IS_VIDEOCONFIGPARAMSDEC(target) && MIX_IS_VIDEOCONFIGPARAMSDEC(src)) {

		/* Cast the base object to this child object */
		this_target = MIX_VIDEOCONFIGPARAMSDEC(target);
		this_src = MIX_VIDEOCONFIGPARAMSDEC(src);

		/* copy properties of primitive type */

		this_target->frame_rate_num = this_src->frame_rate_num;
		this_target->frame_rate_denom = this_src->frame_rate_denom;
		this_target->picture_width = this_src->picture_width;
		this_target->picture_height = this_src->picture_height;
		this_target->raw_format = this_src->raw_format;
		this_target->rate_control = this_src->rate_control;
		this_target->mixbuffer_pool_size = this_src->mixbuffer_pool_size;
		this_target->extra_surface_allocation = this_src->extra_surface_allocation;

		/* copy properties of non-primitive */

		/* copy header */
		mix_result = mix_videoconfigparamsdec_set_header(this_target,
				&this_src->header);

		if (mix_result != MIX_RESULT_SUCCESS) {

			LOG_E( "set_header failed: mix_result = 0x%x\n", mix_result);
			return FALSE;
		}

		/* copy mime_type */
		if (this_src->mime_type) {

			mix_result = mix_videoconfigparamsdec_set_mime_type(this_target,
					this_src->mime_type->str);
		} else {
			mix_result = mix_videoconfigparamsdec_set_mime_type(this_target, NULL);
		}

		if (mix_result != MIX_RESULT_SUCCESS) {
			LOG_E( "set_mime_type failed: mix_result = 0x%x\n", mix_result);
			return FALSE;
		}

		/* TODO: copy other properties if there's any */

		/* Now chainup base class */
		if (root_class->copy) {
			LOG_V( "root_class->copy != NULL\n");
			return root_class->copy(MIX_PARAMS_CAST(target), MIX_PARAMS_CAST(
					src));
		} else {
			LOG_E( "root_class->copy == NULL\n");
			return TRUE;
		}
	}

	LOG_V( "End\n");

	return FALSE;
}

/**
 * mix_videoconfigparamsdec_:
 * @first: first object to compare
 * @second: seond object to compare
 * @returns: boolean indicates if instance are equal.
 *
 * Copy instance data from @src to @target.
 */
gboolean mix_videoconfigparamsdec_equal(MixParams * first, MixParams * second) {

	gboolean ret = FALSE;

	MixVideoConfigParamsDec *this_first, *this_second;
	MixParamsClass *root_class = MIX_PARAMS_CLASS(parent_class);


	if (MIX_IS_VIDEOCONFIGPARAMSDEC(first) && MIX_IS_VIDEOCONFIGPARAMSDEC(second)) {

		// Deep compare
		// Cast the base object to this child object
		this_first = MIX_VIDEOCONFIGPARAMSDEC(first);
		this_second = MIX_VIDEOCONFIGPARAMSDEC(second);

		/* check the equalitiy of the primitive type properties */
		if (this_first->frame_order_mode != this_second->frame_order_mode) {
			goto not_equal;
		}

		if (this_first->frame_rate_num != this_second->frame_rate_num
				&& this_first->frame_rate_denom
						!= this_second->frame_rate_denom) {
			goto not_equal;
		}

		if (this_first->picture_width != this_second->picture_width
				&& this_first->picture_height != this_second->picture_height) {
			goto not_equal;
		}

		if (this_first->raw_format != this_second->raw_format) {
			goto not_equal;
		}

		if (this_first->rate_control != this_second->rate_control) {
			goto not_equal;
		}

		if (this_first->mixbuffer_pool_size != this_second->mixbuffer_pool_size) {
			goto not_equal;
		}

		if (this_first->extra_surface_allocation != this_second->extra_surface_allocation) {
			goto not_equal;
		}

		/* check the equalitiy of the none-primitive type properties */

		/* MixIOVec header */

		if (this_first->header.data_size != this_second->header.data_size) {
			goto not_equal;
		}

		if (this_first->header.buffer_size != this_second->header.buffer_size) {
			goto not_equal;
		}

		if (this_first->header.data && this_second->header.data) {
			if (memcmp(this_first->header.data, this_second->header.data,
					this_first->header.data_size) != 0) {
				goto not_equal;
			}
		} else if (!(!this_first->header.data && !this_second->header.data)) {
			goto not_equal;
		}

		/* compare mime_type */

		if (this_first->mime_type && this_second->mime_type) {
			if (g_string_equal(this_first->mime_type, this_second->mime_type)
					!= TRUE) {
				goto not_equal;
			}
		} else if (!(!this_first->mime_type && !this_second->mime_type)) {
			goto not_equal;
		}

		ret = TRUE;

		not_equal:

		if (ret != TRUE) {
			return ret;
		}

		/* chaining up. */
		if (root_class->equal)
			ret = root_class->equal(first, second);
		else
			ret = TRUE;
	}

	return ret;
}

#define MIX_VIDEOCONFIGPARAMSDEC_SETTER_CHECK_INPUT(obj) \
	if(!obj) return MIX_RESULT_NULL_PTR; \
	if(!MIX_IS_VIDEOCONFIGPARAMSDEC(obj)) return MIX_RESULT_FAIL; \

#define MIX_VIDEOCONFIGPARAMSDEC_GETTER_CHECK_INPUT(obj, prop) \
	if(!obj || !prop) return MIX_RESULT_NULL_PTR; \
	if(!MIX_IS_VIDEOCONFIGPARAMSDEC(obj)) return MIX_RESULT_FAIL; \

#define MIX_VIDEOCONFIGPARAMSDEC_GETTER_CHECK_INPUT_PAIR(obj, prop, prop2) \
	if(!obj || !prop || !prop2 ) return MIX_RESULT_NULL_PTR; \
	if(!MIX_IS_VIDEOCONFIGPARAMSDEC(obj)) return MIX_RESULT_FAIL; \

/* TODO: Add getters and setters for other properties. The following is incomplete */

MIX_RESULT mix_videoconfigparamsdec_set_frame_order_mode(
		MixVideoConfigParamsDec * obj, MixFrameOrderMode frame_order_mode) {
	MIX_VIDEOCONFIGPARAMSDEC_SETTER_CHECK_INPUT (obj);
	obj->frame_order_mode = frame_order_mode;
	return MIX_RESULT_SUCCESS;
}

MIX_RESULT mix_videoconfigparamsdec_get_frame_order_mode(
		MixVideoConfigParamsDec * obj, MixFrameOrderMode * frame_order_mode) {
	MIX_VIDEOCONFIGPARAMSDEC_GETTER_CHECK_INPUT (obj, frame_order_mode);
	*frame_order_mode = obj->frame_order_mode;
	return MIX_RESULT_SUCCESS;
}

MIX_RESULT mix_videoconfigparamsdec_set_header(MixVideoConfigParamsDec * obj,
		MixIOVec * header) {

	MIX_VIDEOCONFIGPARAMSDEC_SETTER_CHECK_INPUT (obj);

	if (!header) {
		return MIX_RESULT_NULL_PTR;
	}

	if (header->data && header->buffer_size) {
		obj->header.data = g_memdup(header->data, header->buffer_size);
		if (!obj->header.data) {
			return MIX_RESULT_NO_MEMORY;
		}
		obj->header.buffer_size = header->buffer_size;
		obj->header.data_size = header->data_size;
	}
	return MIX_RESULT_SUCCESS;
}

MIX_RESULT mix_videoconfigparamsdec_get_header(MixVideoConfigParamsDec * obj,
		MixIOVec ** header) {

	MIX_VIDEOCONFIGPARAMSDEC_GETTER_CHECK_INPUT (obj, header);

	if (obj->header.data && obj->header.buffer_size) {

		*header = g_malloc(sizeof(MixIOVec));

		if (*header == NULL) {
			return MIX_RESULT_NO_MEMORY;
		}

		(*header)->data = g_memdup(obj->header.data, obj->header.buffer_size);
		(*header)->buffer_size = obj->header.buffer_size;
		(*header)->data_size = obj->header.data_size;

	} else {
		*header = NULL;
	}
	return MIX_RESULT_SUCCESS;

}

MIX_RESULT mix_videoconfigparamsdec_set_mime_type(MixVideoConfigParamsDec * obj,
		const gchar * mime_type) {

	MIX_VIDEOCONFIGPARAMSDEC_SETTER_CHECK_INPUT (obj);

	if (!mime_type) {
		return MIX_RESULT_NULL_PTR;
	}

	if (obj->mime_type) {
		if (obj->mime_type->str)
			g_string_free(obj->mime_type, TRUE);
		else
			g_string_free(obj->mime_type, FALSE);
	}

	obj->mime_type = g_string_new(mime_type);
	if (!obj->mime_type) {
		return MIX_RESULT_NO_MEMORY;
	}

	return MIX_RESULT_SUCCESS;
}

MIX_RESULT mix_videoconfigparamsdec_get_mime_type(MixVideoConfigParamsDec * obj,
		gchar ** mime_type) {
	MIX_VIDEOCONFIGPARAMSDEC_GETTER_CHECK_INPUT (obj, mime_type);

	if (!obj->mime_type) {
		*mime_type = NULL;
		return MIX_RESULT_SUCCESS;
	}
	*mime_type = g_strdup(obj->mime_type->str);
	if (!*mime_type) {
		return MIX_RESULT_NO_MEMORY;
	}

	return MIX_RESULT_SUCCESS;
}

MIX_RESULT mix_videoconfigparamsdec_set_frame_rate(MixVideoConfigParamsDec * obj,
		guint frame_rate_num, guint frame_rate_denom) {
	MIX_VIDEOCONFIGPARAMSDEC_SETTER_CHECK_INPUT (obj);
	obj->frame_rate_num = frame_rate_num;
	obj->frame_rate_denom = frame_rate_denom;
	return MIX_RESULT_SUCCESS;
}

MIX_RESULT mix_videoconfigparamsdec_get_frame_rate(MixVideoConfigParamsDec * obj,
		guint * frame_rate_num, guint * frame_rate_denom) {
	MIX_VIDEOCONFIGPARAMSDEC_GETTER_CHECK_INPUT_PAIR (obj, frame_rate_num, frame_rate_denom);
	*frame_rate_num = obj->frame_rate_num;
	*frame_rate_denom = obj->frame_rate_denom;
	return MIX_RESULT_SUCCESS;
}

MIX_RESULT mix_videoconfigparamsdec_set_picture_res(MixVideoConfigParamsDec * obj,
		guint picture_width, guint picture_height) {
	MIX_VIDEOCONFIGPARAMSDEC_SETTER_CHECK_INPUT (obj);
	obj->picture_width = picture_width;
	obj->picture_height = picture_height;
	return MIX_RESULT_SUCCESS;
}

MIX_RESULT mix_videoconfigparamsdec_get_picture_res(MixVideoConfigParamsDec * obj,
		guint * picture_width, guint * picture_height) {
	MIX_VIDEOCONFIGPARAMSDEC_GETTER_CHECK_INPUT_PAIR (obj, picture_width, picture_height);
	*picture_width = obj->picture_width;
	*picture_height = obj->picture_height;
	return MIX_RESULT_SUCCESS;
}

MIX_RESULT mix_videoconfigparamsdec_set_raw_format(MixVideoConfigParamsDec * obj,
		guint raw_format) {
	MIX_VIDEOCONFIGPARAMSDEC_SETTER_CHECK_INPUT (obj);

	/* TODO: check if the value of raw_format is valid */
	obj->raw_format = raw_format;
	return MIX_RESULT_SUCCESS;
}

MIX_RESULT mix_videoconfigparamsdec_get_raw_format(MixVideoConfigParamsDec * obj,
		guint *raw_format) {
	MIX_VIDEOCONFIGPARAMSDEC_GETTER_CHECK_INPUT (obj, raw_format);
	*raw_format = obj->raw_format;
	return MIX_RESULT_SUCCESS;
}

MIX_RESULT mix_videoconfigparamsdec_set_rate_control(MixVideoConfigParamsDec * obj,
		guint rate_control) {
	MIX_VIDEOCONFIGPARAMSDEC_SETTER_CHECK_INPUT (obj);

	/* TODO: check if the value of rate_control is valid */
	obj->rate_control = rate_control;
	return MIX_RESULT_SUCCESS;
}

MIX_RESULT mix_videoconfigparamsdec_get_rate_control(MixVideoConfigParamsDec * obj,
		guint *rate_control) {
	MIX_VIDEOCONFIGPARAMSDEC_GETTER_CHECK_INPUT (obj, rate_control);
	*rate_control = obj->rate_control;
	return MIX_RESULT_SUCCESS;
}

MIX_RESULT mix_videoconfigparamsdec_set_buffer_pool_size(
		MixVideoConfigParamsDec * obj, guint bufpoolsize) {

	MIX_VIDEOCONFIGPARAMSDEC_SETTER_CHECK_INPUT (obj);

	obj->mixbuffer_pool_size = bufpoolsize;
	return MIX_RESULT_SUCCESS;

}

MIX_RESULT mix_videoconfigparamsdec_get_buffer_pool_size(
		MixVideoConfigParamsDec * obj, guint *bufpoolsize) {

	MIX_VIDEOCONFIGPARAMSDEC_GETTER_CHECK_INPUT (obj, bufpoolsize);
	*bufpoolsize = obj->mixbuffer_pool_size;
	return MIX_RESULT_SUCCESS;
}

MIX_RESULT mix_videoconfigparamsdec_set_extra_surface_allocation(
		MixVideoConfigParamsDec * obj,
                guint extra_surface_allocation) {

	MIX_VIDEOCONFIGPARAMSDEC_SETTER_CHECK_INPUT (obj);

	obj->extra_surface_allocation = extra_surface_allocation;
	return MIX_RESULT_SUCCESS;

}

MIX_RESULT mix_videoconfigparamsdec_get_extra_surface_allocation(
		MixVideoConfigParamsDec * obj,
                guint *extra_surface_allocation) {

	MIX_VIDEOCONFIGPARAMSDEC_GETTER_CHECK_INPUT (obj, extra_surface_allocation);
	*extra_surface_allocation = obj->extra_surface_allocation;
	return MIX_RESULT_SUCCESS;

}




