/* 
INTEL CONFIDENTIAL
Copyright 2009 Intel Corporation All Rights Reserved. 
The source code contained or described herein and all documents related to the source code ("Material") are owned by Intel Corporation or its suppliers or licensors. Title to the Material remains with Intel Corporation or its suppliers and licensors. The Material contains trade secrets and proprietary and confidential information of Intel or its suppliers and licensors. The Material is protected by worldwide copyright and trade secret laws and treaty provisions. No part of the Material may be used, copied, reproduced, modified, published, uploaded, posted, transmitted, distributed, or disclosed in any way without Intel’s prior express written permission.

No license under any patent, copyright, trade secret or other intellectual property right is granted to or conferred upon you by disclosure or delivery of the Materials, either expressly, by implication, inducement, estoppel or otherwise. Any license under such intellectual property rights must be express and approved by Intel in writing.
*/

#ifndef __MIX_VIDEOCAPS_H__
#define __MIX_VIDEOCAPS_H__

#include <mixparams.h>
#include "mixvideodef.h"

/**
* MIX_TYPE_VIDEOCAPS:
* 
* Get type of class.
*/
#define MIX_TYPE_VIDEOCAPS (mix_videocaps_get_type ())

/**
* MIX_VIDEOCAPS:
* @obj: object to be type-casted.
*/
#define MIX_VIDEOCAPS(obj) (G_TYPE_CHECK_INSTANCE_CAST ((obj), MIX_TYPE_VIDEOCAPS, MixVideoCaps))

/**
* MIX_IS_VIDEOCAPS:
* @obj: an object.
* 
* Checks if the given object is an instance of #MixParams
*/
#define MIX_IS_VIDEOCAPS(obj) (G_TYPE_CHECK_INSTANCE_TYPE ((obj), MIX_TYPE_VIDEOCAPS))

/**
* MIX_VIDEOCAPS_CLASS:
* @klass: class to be type-casted.
*/
#define MIX_VIDEOCAPS_CLASS(klass) (G_TYPE_CHECK_CLASS_CAST ((klass), MIX_TYPE_VIDEOCAPS, MixVideoCapsClass))

/**
* MIX_IS_VIDEOCAPS_CLASS:
* @klass: a class.
* 
* Checks if the given class is #MixParamsClass
*/
#define MIX_IS_VIDEOCAPS_CLASS(klass) (G_TYPE_CHECK_CLASS_TYPE ((klass), MIX_TYPE_VIDEOCAPS))

/**
* MIX_VIDEOCAPS_GET_CLASS:
* @obj: a #MixParams object.
* 
* Get the class instance of the object.
*/
#define MIX_VIDEOCAPS_GET_CLASS(obj) (G_TYPE_INSTANCE_GET_CLASS ((obj), MIX_TYPE_VIDEOCAPS, MixVideoCapsClass))

typedef struct _MixVideoCaps MixVideoCaps;
typedef struct _MixVideoCapsClass MixVideoCapsClass;

/**
* MixVideoCaps:
*
* MI-X VideoConfig Parameter object
*/
struct _MixVideoCaps
{
  /*< public > */
  MixParams parent;

  /*< public > */
  gchar *mix_caps;
  gchar *video_hw_caps;

  void *reserved1;
  void *reserved2;
  void *reserved3;
  void *reserved4;
};

/**
* MixVideoCapsClass:
* 
* MI-X VideoConfig object class
*/
struct _MixVideoCapsClass
{
  /*< public > */
  MixParamsClass parent_class;

  /* class members */
};

/**
* mix_videocaps_get_type:
* @returns: type
* 
* Get the type of object.
*/
GType mix_videocaps_get_type (void);

/**
* mix_videocaps_new:
* @returns: A newly allocated instance of #MixVideoCaps
* 
* Use this method to create new instance of #MixVideoCaps
*/
MixVideoCaps *mix_videocaps_new (void);
/**
* mix_videocaps_ref:
* @mix: object to add reference
* @returns: the MixVideoCaps instance where reference count has been increased.
* 
* Add reference count.
*/
MixVideoCaps *mix_videocaps_ref (MixVideoCaps * mix);

/**
* mix_videocaps_unref:
* @obj: object to unref.
* 
* Decrement reference count of the object.
*/
#define mix_videocaps_unref(obj) mix_params_unref(MIX_PARAMS(obj))

/* Class Methods */

MIX_RESULT mix_videocaps_set_mix_caps (MixVideoCaps * obj, gchar * mix_caps);
MIX_RESULT mix_videocaps_get_mix_caps (MixVideoCaps * obj,
				       gchar ** mix_caps);

MIX_RESULT mix_videocaps_set_video_hw_caps (MixVideoCaps * obj,
					    gchar * video_hw_caps);
MIX_RESULT mix_videocaps_get_video_hw_caps (MixVideoCaps * obj,
					    gchar ** video_hw_caps);

#endif /* __MIX_VIDEOCAPS_H__ */
