/* 
 INTEL CONFIDENTIAL
 Copyright 2009 Intel Corporation All Rights Reserved. 
 The source code contained or described herein and all documents related to the source code ("Material") are owned by Intel Corporation or its suppliers or licensors. Title to the Material remains with Intel Corporation or its suppliers and licensors. The Material contains trade secrets and proprietary and confidential information of Intel or its suppliers and licensors. The Material is protected by worldwide copyright and trade secret laws and treaty provisions. No part of the Material may be used, copied, reproduced, modified, published, uploaded, posted, transmitted, distributed, or disclosed in any way without Intel’s prior express written permission.

 No license under any patent, copyright, trade secret or other intellectual property right is granted to or conferred upon you by disclosure or delivery of the Materials, either expressly, by implication, inducement, estoppel or otherwise. Any license under such intellectual property rights must be express and approved by Intel in writing.
*/

#ifndef __MIX_VIDEOINITPARAMS_H__
#define __MIX_VIDEOINITPARAMS_H__

#include <mixparams.h>
#include "mixdisplay.h"
#include "mixvideodef.h"

/**
 * MIX_TYPE_VIDEOINITPARAMS:
 * 
 * Get type of class.
 */
#define MIX_TYPE_VIDEOINITPARAMS (mix_videoinitparams_get_type ())

/**
 * MIX_VIDEOINITPARAMS:
 * @obj: object to be type-casted.
 */
#define MIX_VIDEOINITPARAMS(obj) (G_TYPE_CHECK_INSTANCE_CAST ((obj), MIX_TYPE_VIDEOINITPARAMS, MixVideoInitParams))

/**
 * MIX_IS_VIDEOINITPARAMS:
 * @obj: an object.
 * 
 * Checks if the given object is an instance of #MixParams
 */
#define MIX_IS_VIDEOINITPARAMS(obj) (G_TYPE_CHECK_INSTANCE_TYPE ((obj), MIX_TYPE_VIDEOINITPARAMS))

/**
 * MIX_VIDEOINITPARAMS_CLASS:
 * @klass: class to be type-casted.
 */
#define MIX_VIDEOINITPARAMS_CLASS(klass) (G_TYPE_CHECK_CLASS_CAST ((klass), MIX_TYPE_VIDEOINITPARAMS, MixVideoInitParamsClass))

/**
 * MIX_IS_VIDEOINITPARAMS_CLASS:
 * @klass: a class.
 * 
 * Checks if the given class is #MixParamsClass
 */
#define MIX_IS_VIDEOINITPARAMS_CLASS(klass) (G_TYPE_CHECK_CLASS_TYPE ((klass), MIX_TYPE_VIDEOINITPARAMS))

/**
 * MIX_VIDEOINITPARAMS_GET_CLASS:
 * @obj: a #MixParams object.
 * 
 * Get the class instance of the object.
 */
#define MIX_VIDEOINITPARAMS_GET_CLASS(obj) (G_TYPE_INSTANCE_GET_CLASS ((obj), MIX_TYPE_VIDEOINITPARAMS, MixVideoInitParamsClass))

typedef struct _MixVideoInitParams MixVideoInitParams;
typedef struct _MixVideoInitParamsClass MixVideoInitParamsClass;

/**
 * MixVideoInitParams:
 *
 * MI-X VideoInit Parameter object
 */
struct _MixVideoInitParams
{
  /*< public > */
  MixParams parent;

  /*< public > */

  MixDisplay *display;
  void *reserved1;
  void *reserved2;
  void *reserved3;
  void *reserved4;
};

/**
 * MixVideoInitParamsClass:
 * 
 * MI-X VideoInit object class
 */
struct _MixVideoInitParamsClass
{
  /*< public > */
  MixParamsClass parent_class;

  /* class members */
};

/**
 * mix_videoinitparams_get_type:
 * @returns: type
 * 
 * Get the type of object.
 */
GType mix_videoinitparams_get_type (void);

/**
 * mix_videoinitparams_new:
 * @returns: A newly allocated instance of #MixVideoInitParams
 * 
 * Use this method to create new instance of #MixVideoInitParams
 */
MixVideoInitParams *mix_videoinitparams_new (void);
/**
 * mix_videoinitparams_ref:
 * @mix: object to add reference
 * @returns: the MixVideoInitParams instance where reference count has been increased.
 * 
 * Add reference count.
 */
MixVideoInitParams *mix_videoinitparams_ref (MixVideoInitParams * mix);

/**
 * mix_videoinitparams_unref:
 * @obj: object to unref.
 * 
 * Decrement reference count of the object.
 */
#define mix_videoinitparams_unref(obj) mix_params_unref(MIX_PARAMS(obj))

/* Class Methods */

/*
	TO DO: Add documents
*/

MIX_RESULT mix_videoinitparams_set_display (MixVideoInitParams * obj,
					    MixDisplay * display);

MIX_RESULT mix_videoinitparams_get_display (MixVideoInitParams * obj,
					    MixDisplay ** dislay);

#endif /* __MIX_VIDEOINITPARAMS_H__ */
