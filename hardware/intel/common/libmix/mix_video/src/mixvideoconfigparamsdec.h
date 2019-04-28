/*
 INTEL CONFIDENTIAL
 Copyright 2009 Intel Corporation All Rights Reserved.
 The source code contained or described herein and all documents related to the source code ("Material") are owned by Intel Corporation or its suppliers or licensors. Title to the Material remains with Intel Corporation or its suppliers and licensors. The Material contains trade secrets and proprietary and confidential information of Intel or its suppliers and licensors. The Material is protected by worldwide copyright and trade secret laws and treaty provisions. No part of the Material may be used, copied, reproduced, modified, published, uploaded, posted, transmitted, distributed, or disclosed in any way without Intel’s prior express written permission.

 No license under any patent, copyright, trade secret or other intellectual property right is granted to or conferred upon you by disclosure or delivery of the Materials, either expressly, by implication, inducement, estoppel or otherwise. Any license under such intellectual property rights must be express and approved by Intel in writing.
 */

#ifndef __MIX_VIDEOCONFIGPARAMSDEC_H__
#define __MIX_VIDEOCONFIGPARAMSDEC_H__

#include <mixvideoconfigparams.h>
#include "mixvideodef.h"

/**
 * MIX_TYPE_VIDEOCONFIGPARAMSDEC:
 *
 * Get type of class.
 */
#define MIX_TYPE_VIDEOCONFIGPARAMSDEC (mix_videoconfigparamsdec_get_type ())

/**
 * MIX_VIDEOCONFIGPARAMSDEC:
 * @obj: object to be type-casted.
 */
#define MIX_VIDEOCONFIGPARAMSDEC(obj) (G_TYPE_CHECK_INSTANCE_CAST ((obj), MIX_TYPE_VIDEOCONFIGPARAMSDEC, MixVideoConfigParamsDec))

/**
 * MIX_IS_VIDEOCONFIGPARAMSDEC:
 * @obj: an object.
 *
 * Checks if the given object is an instance of #MixParams
 */
#define MIX_IS_VIDEOCONFIGPARAMSDEC(obj) (G_TYPE_CHECK_INSTANCE_TYPE ((obj), MIX_TYPE_VIDEOCONFIGPARAMSDEC))

/**
 * MIX_VIDEOCONFIGPARAMSDEC_CLASS:
 * @klass: class to be type-casted.
 */
#define MIX_VIDEOCONFIGPARAMSDEC_CLASS(klass) (G_TYPE_CHECK_CLASS_CAST ((klass), MIX_TYPE_VIDEOCONFIGPARAMSDEC, MixVideoConfigParamsDecClass))

/**
 * MIX_IS_VIDEOCONFIGPARAMSDEC_CLASS:
 * @klass: a class.
 *
 * Checks if the given class is #MixParamsClass
 */
#define MIX_IS_VIDEOCONFIGPARAMSDEC_CLASS(klass) (G_TYPE_CHECK_CLASS_TYPE ((klass), MIX_TYPE_VIDEOCONFIGPARAMSDEC))

/**
 * MIX_VIDEOCONFIGPARAMSDEC_GET_CLASS:
 * @obj: a #MixParams object.
 *
 * Get the class instance of the object.
 */
#define MIX_VIDEOCONFIGPARAMSDEC_GET_CLASS(obj) (G_TYPE_INSTANCE_GET_CLASS ((obj), MIX_TYPE_VIDEOCONFIGPARAMSDEC, MixVideoConfigParamsDecClass))

typedef struct _MixVideoConfigParamsDec MixVideoConfigParamsDec;
typedef struct _MixVideoConfigParamsDecClass MixVideoConfigParamsDecClass;

/**
 * MixVideoConfigParamsDec:
 *
 * MI-X VideoConfig Parameter object
 */
struct _MixVideoConfigParamsDec {
	/*< public > */
	MixVideoConfigParams parent;

	/*< public > */
	MixFrameOrderMode frame_order_mode;
	MixIOVec header;

	/* the type of the following members will be changed after MIX API doc is ready */
	GString * mime_type;
	guint frame_rate_num;
	guint frame_rate_denom;
	gulong picture_width;
	gulong picture_height;
	guint raw_format;
	guint rate_control;

	guint mixbuffer_pool_size;
	guint extra_surface_allocation;
	
	void *reserved1;
	void *reserved2;
	void *reserved3;
	void *reserved4;
};

/**
 * MixVideoConfigParamsDecClass:
 *
 * MI-X VideoConfig object class
 */
struct _MixVideoConfigParamsDecClass {
	/*< public > */
	MixVideoConfigParamsClass parent_class;

	/* class members */
};

/**
 * mix_videoconfigparamsdec_get_type:
 * @returns: type
 *
 * Get the type of object.
 */
GType mix_videoconfigparamsdec_get_type(void);

/**
 * mix_videoconfigparamsdec_new:
 * @returns: A newly allocated instance of #MixVideoConfigParamsDec
 *
 * Use this method to create new instance of #MixVideoConfigParamsDec
 */
MixVideoConfigParamsDec *mix_videoconfigparamsdec_new(void);
/**
 * mix_videoconfigparamsdec_ref:
 * @mix: object to add reference
 * @returns: the MixVideoConfigParamsDec instance where reference count has been increased.
 *
 * Add reference count.
 */
MixVideoConfigParamsDec *mix_videoconfigparamsdec_ref(MixVideoConfigParamsDec * mix);

/**
 * mix_videoconfigparamsdec_unref:
 * @obj: object to unref.
 *
 * Decrement reference count of the object.
 */
#define mix_videoconfigparamsdec_unref(obj) mix_params_unref(MIX_PARAMS(obj))

/* Class Methods */

MIX_RESULT mix_videoconfigparamsdec_set_frame_order_mode(
		MixVideoConfigParamsDec * obj, MixFrameOrderMode frame_order_mode);

MIX_RESULT mix_videoconfigparamsdec_get_frame_order_mode(
		MixVideoConfigParamsDec * obj, MixFrameOrderMode * frame_order_mode);

MIX_RESULT mix_videoconfigparamsdec_set_header(MixVideoConfigParamsDec * obj,
		MixIOVec *header);

/* caller is responsible to g_free MixIOVec::data field */
MIX_RESULT mix_videoconfigparamsdec_get_header(MixVideoConfigParamsDec * obj,
		MixIOVec ** header);

MIX_RESULT mix_videoconfigparamsdec_set_mime_type(MixVideoConfigParamsDec * obj,
		const gchar * mime_type);

MIX_RESULT mix_videoconfigparamsdec_get_mime_type(MixVideoConfigParamsDec * obj,
		gchar ** mime_type);

MIX_RESULT mix_videoconfigparamsdec_set_frame_rate(MixVideoConfigParamsDec * obj,
		guint frame_rate_num, guint frame_rate_denom);

MIX_RESULT mix_videoconfigparamsdec_get_frame_rate(MixVideoConfigParamsDec * obj,
		guint * frame_rate_num, guint * frame_rate_denom);

MIX_RESULT mix_videoconfigparamsdec_set_picture_res(MixVideoConfigParamsDec * obj,
		guint picture_width, guint picture_height);

MIX_RESULT mix_videoconfigparamsdec_get_picture_res(MixVideoConfigParamsDec * obj,
		guint * picture_width, guint * picture_height);

MIX_RESULT mix_videoconfigparamsdec_set_raw_format(MixVideoConfigParamsDec * obj,
		guint raw_format);

MIX_RESULT mix_videoconfigparamsdec_get_raw_format(MixVideoConfigParamsDec * obj,
		guint *raw_format);

MIX_RESULT mix_videoconfigparamsdec_set_rate_control(MixVideoConfigParamsDec * obj,
		guint rate_control);

MIX_RESULT mix_videoconfigparamsdec_get_rate_control(MixVideoConfigParamsDec * obj,
		guint *rate_control);

MIX_RESULT mix_videoconfigparamsdec_set_buffer_pool_size(MixVideoConfigParamsDec * obj,
		guint bufpoolsize);

MIX_RESULT mix_videoconfigparamsdec_get_buffer_pool_size(MixVideoConfigParamsDec * obj,
		guint *bufpoolsize);

MIX_RESULT mix_videoconfigparamsdec_set_extra_surface_allocation(MixVideoConfigParamsDec * obj,
		guint extra_surface_allocation);

MIX_RESULT mix_videoconfigparamsdec_get_extra_surface_allocation(MixVideoConfigParamsDec * obj,
		guint *extra_surface_allocation);

/* TODO: Add getters and setters for other properties */

#endif /* __MIX_VIDEOCONFIGPARAMSDEC_H__ */
