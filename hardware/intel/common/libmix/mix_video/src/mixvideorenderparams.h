/*
 INTEL CONFIDENTIAL
 Copyright 2009 Intel Corporation All Rights Reserved.
 The source code contained or described herein and all documents related to the source code ("Material") are owned by Intel Corporation or its suppliers or licensors. Title to the Material remains with Intel Corporation or its suppliers and licensors. The Material contains trade secrets and proprietary and confidential information of Intel or its suppliers and licensors. The Material is protected by worldwide copyright and trade secret laws and treaty provisions. No part of the Material may be used, copied, reproduced, modified, published, uploaded, posted, transmitted, distributed, or disclosed in any way without Intel’s prior express written permission.

 No license under any patent, copyright, trade secret or other intellectual property right is granted to or conferred upon you by disclosure or delivery of the Materials, either expressly, by implication, inducement, estoppel or otherwise. Any license under such intellectual property rights must be express and approved by Intel in writing.
 */

#ifndef __MIX_VIDEORENDERPARAMS_H__
#define __MIX_VIDEORENDERPARAMS_H__

#include <mixparams.h>
#include "mixvideodef.h"
#include "mixdisplay.h"
#include "mixvideoframe.h"

/**
 * MIX_TYPE_VIDEORENDERPARAMS:
 *
 * Get type of class.
 */
#define MIX_TYPE_VIDEORENDERPARAMS (mix_videorenderparams_get_type ())

/**
 * MIX_VIDEORENDERPARAMS:
 * @obj: object to be type-casted.
 */
#define MIX_VIDEORENDERPARAMS(obj) (G_TYPE_CHECK_INSTANCE_CAST ((obj), MIX_TYPE_VIDEORENDERPARAMS, MixVideoRenderParams))

/**
 * MIX_IS_VIDEORENDERPARAMS:
 * @obj: an object.
 *
 * Checks if the given object is an instance of #MixParams
 */
#define MIX_IS_VIDEORENDERPARAMS(obj) (G_TYPE_CHECK_INSTANCE_TYPE ((obj), MIX_TYPE_VIDEORENDERPARAMS))

/**
 * MIX_VIDEORENDERPARAMS_CLASS:
 * @klass: class to be type-casted.
 */
#define MIX_VIDEORENDERPARAMS_CLASS(klass) (G_TYPE_CHECK_CLASS_CAST ((klass), MIX_TYPE_VIDEORENDERPARAMS, MixVideoRenderParamsClass))

/**
 * MIX_IS_VIDEORENDERPARAMS_CLASS:
 * @klass: a class.
 *
 * Checks if the given class is #MixParamsClass
 */
#define MIX_IS_VIDEORENDERPARAMS_CLASS(klass) (G_TYPE_CHECK_CLASS_TYPE ((klass), MIX_TYPE_VIDEORENDERPARAMS))

/**
 * MIX_VIDEORENDERPARAMS_GET_CLASS:
 * @obj: a #MixParams object.
 *
 * Get the class instance of the object.
 */
#define MIX_VIDEORENDERPARAMS_GET_CLASS(obj) (G_TYPE_INSTANCE_GET_CLASS ((obj), MIX_TYPE_VIDEORENDERPARAMS, MixVideoRenderParamsClass))

typedef struct _MixVideoRenderParams MixVideoRenderParams;
typedef struct _MixVideoRenderParamsClass MixVideoRenderParamsClass;

/**
 * MixVideoRenderParams:
 *
 * MI-X VideoRender Parameter object
 */
struct _MixVideoRenderParams {
	/*< public > */
	MixParams parent;

	/*< public > */
	MixDisplay *display;

	MixRect src_rect;
	MixRect dst_rect;

	MixRect *clipping_rects;
	guint number_of_clipping_rects;

	guint post_proc;

	gpointer reserved;
	gpointer reserved1;
	gpointer reserved2;
	gpointer reserved3;
	gpointer reserved4;
};

/**
 * MixVideoRenderParamsClass:
 *
 * MI-X VideoRender object class
 */
struct _MixVideoRenderParamsClass {
	/*< public > */
	MixParamsClass parent_class;

	/* class members */
};

/**
 * mix_videorenderparams_get_type:
 * @returns: type
 *
 * Get the type of object.
 */
GType mix_videorenderparams_get_type(void);

/**
 * mix_videorenderparams_new:
 * @returns: A newly allocated instance of #MixVideoRenderParams
 *
 * Use this method to create new instance of #MixVideoRenderParams
 */
MixVideoRenderParams *mix_videorenderparams_new(void);
/**
 * mix_videorenderparams_ref:
 * @mix: object to add reference
 * @returns: the MixVideoRenderParams instance where reference count has been increased.
 *
 * Add reference count.
 */
MixVideoRenderParams *mix_videorenderparams_ref(MixVideoRenderParams * mix);

/**
 * mix_videorenderparams_unref:
 * @obj: object to unref.
 *
 * Decrement reference count of the object.
 */
#define mix_videorenderparams_unref(obj) mix_params_unref(MIX_PARAMS(obj))

/* Class Methods */

MIX_RESULT mix_videorenderparams_set_display(MixVideoRenderParams * obj,
		MixDisplay * display);
MIX_RESULT mix_videorenderparams_get_display(MixVideoRenderParams * obj,
		MixDisplay ** display);

MIX_RESULT mix_videorenderparams_set_src_rect(MixVideoRenderParams * obj,
		MixRect src_rect);
MIX_RESULT mix_videorenderparams_get_src_rect(MixVideoRenderParams * obj,
		MixRect * src_rect);

MIX_RESULT mix_videorenderparams_set_dest_rect(MixVideoRenderParams * obj,
		MixRect dst_rect);
MIX_RESULT mix_videorenderparams_get_dest_rect(MixVideoRenderParams * obj,
		MixRect * dst_rect);

MIX_RESULT mix_videorenderparams_set_clipping_rects(MixVideoRenderParams * obj,
		MixRect* clipping_rects, guint number_of_clipping_rects);
MIX_RESULT mix_videorenderparams_get_clipping_rects(MixVideoRenderParams * obj,
		MixRect ** clipping_rects, guint* number_of_clipping_rects);

/* TODO: Add getters and setters for other properties */

#endif /* __MIX_VIDEORENDERPARAMS_H__ */
