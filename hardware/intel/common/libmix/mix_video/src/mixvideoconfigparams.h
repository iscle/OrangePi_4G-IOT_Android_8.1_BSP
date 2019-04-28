/*
 INTEL CONFIDENTIAL
 Copyright 2009 Intel Corporation All Rights Reserved.
 The source code contained or described herein and all documents related to the source code ("Material") are owned by Intel Corporation or its suppliers or licensors. Title to the Material remains with Intel Corporation or its suppliers and licensors. The Material contains trade secrets and proprietary and confidential information of Intel or its suppliers and licensors. The Material is protected by worldwide copyright and trade secret laws and treaty provisions. No part of the Material may be used, copied, reproduced, modified, published, uploaded, posted, transmitted, distributed, or disclosed in any way without Intel’s prior express written permission.

 No license under any patent, copyright, trade secret or other intellectual property right is granted to or conferred upon you by disclosure or delivery of the Materials, either expressly, by implication, inducement, estoppel or otherwise. Any license under such intellectual property rights must be express and approved by Intel in writing.
 */

#ifndef __MIX_VIDEOCONFIGPARAMS_H__
#define __MIX_VIDEOCONFIGPARAMS_H__

#include <mixparams.h>
#include "mixvideodef.h"

/**
 * MIX_TYPE_VIDEOCONFIGPARAMS:
 *
 * Get type of class.
 */
#define MIX_TYPE_VIDEOCONFIGPARAMS (mix_videoconfigparams_get_type ())

/**
 * MIX_VIDEOCONFIGPARAMS:
 * @obj: object to be type-casted.
 */
#define MIX_VIDEOCONFIGPARAMS(obj) (G_TYPE_CHECK_INSTANCE_CAST ((obj), MIX_TYPE_VIDEOCONFIGPARAMS, MixVideoConfigParams))

/**
 * MIX_IS_VIDEOCONFIGPARAMS:
 * @obj: an object.
 *
 * Checks if the given object is an instance of #MixParams
 */
#define MIX_IS_VIDEOCONFIGPARAMS(obj) (G_TYPE_CHECK_INSTANCE_TYPE ((obj), MIX_TYPE_VIDEOCONFIGPARAMS))

/**
 * MIX_VIDEOCONFIGPARAMS_CLASS:
 * @klass: class to be type-casted.
 */
#define MIX_VIDEOCONFIGPARAMS_CLASS(klass) (G_TYPE_CHECK_CLASS_CAST ((klass), MIX_TYPE_VIDEOCONFIGPARAMS, MixVideoConfigParamsClass))

/**
 * MIX_IS_VIDEOCONFIGPARAMS_CLASS:
 * @klass: a class.
 *
 * Checks if the given class is #MixParamsClass
 */
#define MIX_IS_VIDEOCONFIGPARAMS_CLASS(klass) (G_TYPE_CHECK_CLASS_TYPE ((klass), MIX_TYPE_VIDEOCONFIGPARAMS))

/**
 * MIX_VIDEOCONFIGPARAMS_GET_CLASS:
 * @obj: a #MixParams object.
 *
 * Get the class instance of the object.
 */
#define MIX_VIDEOCONFIGPARAMS_GET_CLASS(obj) (G_TYPE_INSTANCE_GET_CLASS ((obj), MIX_TYPE_VIDEOCONFIGPARAMS, MixVideoConfigParamsClass))

typedef struct _MixVideoConfigParams MixVideoConfigParams;
typedef struct _MixVideoConfigParamsClass MixVideoConfigParamsClass;

/**
 * MixVideoConfigParams:
 *
 * MI-X VideoConfig Parameter object
 */
struct _MixVideoConfigParams {
	/*< public > */
	MixParams parent;

	/*< public > */

	void *reserved1;
	void *reserved2;
	void *reserved3;
	void *reserved4;
};

/**
 * MixVideoConfigParamsClass:
 *
 * MI-X VideoConfig object class
 */
struct _MixVideoConfigParamsClass {
	/*< public > */
	MixParamsClass parent_class;

/* class members */
};

/**
 * mix_videoconfigparams_get_type:
 * @returns: type
 *
 * Get the type of object.
 */
GType mix_videoconfigparams_get_type(void);

/**
 * mix_videoconfigparams_new:
 * @returns: A newly allocated instance of #MixVideoConfigParams
 *
 * Use this method to create new instance of #MixVideoConfigParams
 */
MixVideoConfigParams *mix_videoconfigparams_new(void);
/**
 * mix_videoconfigparams_ref:
 * @mix: object to add reference
 * @returns: the MixVideoConfigParams instance where reference count has been increased.
 *
 * Add reference count.
 */
MixVideoConfigParams *mix_videoconfigparams_ref(MixVideoConfigParams * mix);

/**
 * mix_videoconfigparams_unref:
 * @obj: object to unref.
 *
 * Decrement reference count of the object.
 */
#define mix_videoconfigparams_unref(obj) mix_params_unref(MIX_PARAMS(obj))

/* Class Methods */

/* TODO: Add getters and setters for other properties */

#endif /* __MIX_VIDEOCONFIGPARAMS_H__ */
