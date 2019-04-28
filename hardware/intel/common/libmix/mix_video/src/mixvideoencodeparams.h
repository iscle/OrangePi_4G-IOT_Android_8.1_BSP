/*
 INTEL CONFIDENTIAL
 Copyright 2009 Intel Corporation All Rights Reserved.
 The source code contained or described herein and all documents related to the source code ("Material") are owned by Intel Corporation or its suppliers or licensors. Title to the Material remains with Intel Corporation or its suppliers and licensors. The Material contains trade secrets and proprietary and confidential information of Intel or its suppliers and licensors. The Material is protected by worldwide copyright and trade secret laws and treaty provisions. No part of the Material may be used, copied, reproduced, modified, published, uploaded, posted, transmitted, distributed, or disclosed in any way without Intel’s prior express written permission.

 No license under any patent, copyright, trade secret or other intellectual property right is granted to or conferred upon you by disclosure or delivery of the Materials, either expressly, by implication, inducement, estoppel or otherwise. Any license under such intellectual property rights must be express and approved by Intel in writing.
 */

#ifndef __MIX_VIDEOENCODEPARAMS_H__
#define __MIX_VIDEOENCODEPARAMS_H__

#include <mixparams.h>
#include "mixvideodef.h"

/**
 * MIX_TYPE_VIDEOENCODEPARAMS:
 *
 * Get type of class.
 */
#define MIX_TYPE_VIDEOENCODEPARAMS (mix_videoencodeparams_get_type ())

/**
 * MIX_VIDEOENCODEPARAMS:
 * @obj: object to be type-casted.
 */
#define MIX_VIDEOENCODEPARAMS(obj) (G_TYPE_CHECK_INSTANCE_CAST ((obj), MIX_TYPE_VIDEOENCODEPARAMS, MixVideoEncodeParams))

/**
 * MIX_IS_VIDEOENCODEPARAMS:
 * @obj: an object.
 *
 * Checks if the given object is an instance of #MixParams
 */
#define MIX_IS_VIDEOENCODEPARAMS(obj) (G_TYPE_CHECK_INSTANCE_TYPE ((obj), MIX_TYPE_VIDEOENCODEPARAMS))

/**
 * MIX_VIDEOENCODEPARAMS_CLASS:
 * @klass: class to be type-casted.
 */
#define MIX_VIDEOENCODEPARAMS_CLASS(klass) (G_TYPE_CHECK_CLASS_CAST ((klass), MIX_TYPE_VIDEOENCODEPARAMS, MixVideoEncodeParamsClass))

/**
 * MIX_IS_VIDEOENCODEPARAMS_CLASS:
 * @klass: a class.
 *
 * Checks if the given class is #MixParamsClass
 */
#define MIX_IS_VIDEOENCODEPARAMS_CLASS(klass) (G_TYPE_CHECK_CLASS_TYPE ((klass), MIX_TYPE_VIDEOENCODEPARAMS))

/**
 * MIX_VIDEOENCODEPARAMS_GET_CLASS:
 * @obj: a #MixParams object.
 *
 * Get the class instance of the object.
 */
#define MIX_VIDEOENCODEPARAMS_GET_CLASS(obj) (G_TYPE_INSTANCE_GET_CLASS ((obj), MIX_TYPE_VIDEOENCODEPARAMS, MixVideoEncodeParamsClass))

typedef struct _MixVideoEncodeParams MixVideoEncodeParams;
typedef struct _MixVideoEncodeParamsClass MixVideoEncodeParamsClass;

/**
 * MixVideoEncodeParams:
 *
 * MI-X VideoDecode Parameter object
 */
struct _MixVideoEncodeParams {
	/*< public > */
	MixParams parent;

	/*< public > */

	/* TODO: Add properties */
	guint64 timestamp;
	gboolean discontinuity;

	void *reserved1;
	void *reserved2;
	void *reserved3;
	void *reserved4;
};

/**
 * MixVideoEncodeParamsClass:
 *
 * MI-X VideoDecode object class
 */
struct _MixVideoEncodeParamsClass {
	/*< public > */
	MixParamsClass parent_class;

	/* class members */
};

/**
 * mix_videoencodeparams_get_type:
 * @returns: type
 *
 * Get the type of object.
 */
GType mix_videoencodeparams_get_type(void);

/**
 * mix_videoencodeparams_new:
 * @returns: A newly allocated instance of #MixVideoEncodeParams
 *
 * Use this method to create new instance of #MixVideoEncodeParams
 */
MixVideoEncodeParams *mix_videoencodeparams_new(void);
/**
 * mix_videoencodeparams_ref:
 * @mix: object to add reference
 * @returns: the MixVideoEncodeParams instance where reference count has been increased.
 *
 * Add reference count.
 */
MixVideoEncodeParams *mix_videoencodeparams_ref(MixVideoEncodeParams * mix);

/**
 * mix_videoencodeparams_unref:
 * @obj: object to unref.
 *
 * Decrement reference count of the object.
 */
#define mix_videoencodeparams_unref(obj) mix_params_unref(MIX_PARAMS(obj))

/* Class Methods */

/* TODO: Add getters and setters for properties */
MIX_RESULT mix_videoencodeparams_set_timestamp(MixVideoEncodeParams * obj,
		guint64 timestamp);
MIX_RESULT mix_videoencodeparams_get_timestamp(MixVideoEncodeParams * obj,
		guint64 * timestamp);

MIX_RESULT mix_videoencodeparams_set_discontinuity(MixVideoEncodeParams * obj,
		gboolean discontinuity);
MIX_RESULT mix_videoencodeparams_get_discontinuity(MixVideoEncodeParams * obj,
		gboolean *discontinuity);

#endif /* __MIX_VIDEOENCODEPARAMS_H__ */

