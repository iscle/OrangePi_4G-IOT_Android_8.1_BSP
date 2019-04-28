/* 
INTEL CONFIDENTIAL
Copyright 2009 Intel Corporation All Rights Reserved. 
The source code contained or described herein and all documents related to the source code ("Material") are owned by Intel Corporation or its suppliers or licensors. Title to the Material remains with Intel Corporation or its suppliers and licensors. The Material contains trade secrets and proprietary and confidential information of Intel or its suppliers and licensors. The Material is protected by worldwide copyright and trade secret laws and treaty provisions. No part of the Material may be used, copied, reproduced, modified, published, uploaded, posted, transmitted, distributed, or disclosed in any way without Intel’s prior express written permission.

No license under any patent, copyright, trade secret or other intellectual property right is granted to or conferred upon you by disclosure or delivery of the Materials, either expressly, by implication, inducement, estoppel or otherwise. Any license under such intellectual property rights must be express and approved by Intel in writing.
*/

#ifndef __MIX_DRMPARAMS_H__
#define __MIX_DRMPARAMS_H__

#include <mixparams.h>
#include "mixvideodef.h"

/**
* MIX_TYPE_DRMPARAMS:
* 
* Get type of class.
*/
#define MIX_TYPE_DRMPARAMS (mix_drmparams_get_type ())

/**
* MIX_DRMPARAMS:
* @obj: object to be type-casted.
*/
#define MIX_DRMPARAMS(obj) (G_TYPE_CHECK_INSTANCE_CAST ((obj), MIX_TYPE_DRMPARAMS, MixDrmParams))

/**
* MIX_IS_DRMPARAMS:
* @obj: an object.
* 
* Checks if the given object is an instance of #MixParams
*/
#define MIX_IS_DRMPARAMS(obj) (G_TYPE_CHECK_INSTANCE_TYPE ((obj), MIX_TYPE_DRMPARAMS))

/**
* MIX_DRMPARAMS_CLASS:
* @klass: class to be type-casted.
*/
#define MIX_DRMPARAMS_CLASS(klass) (G_TYPE_CHECK_CLASS_CAST ((klass), MIX_TYPE_DRMPARAMS, MixDrmParamsClass))

/**
* MIX_IS_DRMPARAMS_CLASS:
* @klass: a class.
* 
* Checks if the given class is #MixParamsClass
*/
#define MIX_IS_DRMPARAMS_CLASS(klass) (G_TYPE_CHECK_CLASS_TYPE ((klass), MIX_TYPE_DRMPARAMS))

/**
* MIX_DRMPARAMS_GET_CLASS:
* @obj: a #MixParams object.
* 
* Get the class instance of the object.
*/
#define MIX_DRMPARAMS_GET_CLASS(obj) (G_TYPE_INSTANCE_GET_CLASS ((obj), MIX_TYPE_DRMPARAMS, MixDrmParamsClass))

typedef struct _MixDrmParams MixDrmParams;
typedef struct _MixDrmParamsClass MixDrmParamsClass;

/**
* MixDrmParams:
*
* MI-X Drm Parameter object
*/
struct _MixDrmParams
{
  /*< public > */
  MixParams parent;

  /*< public > */

  /* TODO: Add properties */

};

/**
* MixDrmParamsClass:
* 
* MI-X Drm object class
*/
struct _MixDrmParamsClass
{
  /*< public > */
  MixParamsClass parent_class;

  /* class members */
};

/**
* mix_drmparams_get_type:
* @returns: type
* 
* Get the type of object.
*/
GType mix_drmparams_get_type (void);

/**
* mix_drmparams_new:
* @returns: A newly allocated instance of #MixDrmParams
* 
* Use this method to create new instance of #MixDrmParams
*/
MixDrmParams *mix_drmparams_new (void);
/**
* mix_drmparams_ref:
* @mix: object to add reference
* @returns: the MixDrmParams instance where reference count has been increased.
* 
* Add reference count.
*/
MixDrmParams *mix_drmparams_ref (MixDrmParams * mix);

/**
* mix_drmparams_unref:
* @obj: object to unref.
* 
* Decrement reference count of the object.
*/
#define mix_drmparams_unref(obj) mix_params_unref(MIX_PARAMS(obj))

/* Class Methods */

/* TODO: Add getters and setters for properties */

#endif /* __MIX_DRMPARAMS_H__ */
