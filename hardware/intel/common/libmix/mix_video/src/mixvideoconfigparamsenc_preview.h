/* 
INTEL CONFIDENTIAL
Copyright 2009 Intel Corporation All Rights Reserved. 
The source code contained or described herein and all documents related to the source code ("Material") are owned by Intel Corporation or its suppliers or licensors. Title to the Material remains with Intel Corporation or its suppliers and licensors. The Material contains trade secrets and proprietary and confidential information of Intel or its suppliers and licensors. The Material is protected by worldwide copyright and trade secret laws and treaty provisions. No part of the Material may be used, copied, reproduced, modified, published, uploaded, posted, transmitted, distributed, or disclosed in any way without Intel’s prior express written permission.

No license under any patent, copyright, trade secret or other intellectual property right is granted to or conferred upon you by disclosure or delivery of the Materials, either expressly, by implication, inducement, estoppel or otherwise. Any license under such intellectual property rights must be express and approved by Intel in writing.
*/

#ifndef __MIX_VIDEOCONFIGPARAMSENC_PREVIEW_H__
#define __MIX_VIDEOCONFIGPARAMSENC_PREVIEW_H__

#include "mixvideoconfigparamsenc.h"
#include "mixvideodef.h"

/**
* MIX_TYPE_VIDEOCONFIGPARAMSENC_PREVIEW:
* 
* Get type of class.
*/
#define MIX_TYPE_VIDEOCONFIGPARAMSENC_PREVIEW (mix_videoconfigparamsenc_preview_get_type ())

/**
* MIX_VIDEOCONFIGPARAMSENC_PREVIEW:
* @obj: object to be type-casted.
*/
#define MIX_VIDEOCONFIGPARAMSENC_PREVIEW(obj) (G_TYPE_CHECK_INSTANCE_CAST ((obj), MIX_TYPE_VIDEOCONFIGPARAMSENC_PREVIEW, MixVideoConfigParamsEncPreview))

/**
* MIX_IS_VIDEOCONFIGPARAMSENC_PREVIEW:
* @obj: an object.
* 
* Checks if the given object is an instance of #MixVideoConfigParamsEncPreview
*/
#define MIX_IS_VIDEOCONFIGPARAMSENC_PREVIEW(obj) (G_TYPE_CHECK_INSTANCE_TYPE ((obj), MIX_TYPE_VIDEOCONFIGPARAMSENC_PREVIEW))

/**
* MIX_VIDEOCONFIGPARAMSENC_PREVIEW_CLASS:
* @klass: class to be type-casted.
*/
#define MIX_VIDEOCONFIGPARAMSENC_PREVIEW_CLASS(klass) (G_TYPE_CHECK_CLASS_CAST ((klass), MIX_TYPE_VIDEOCONFIGPARAMSENC_PREVIEW, MixVideoConfigParamsEncPreviewClass))

/**
* MIX_IS_VIDEOCONFIGPARAMSENC_PREVIEW_CLASS:
* @klass: a class.
* 
* Checks if the given class is #MixVideoConfigParamsEncPreviewClass
*/
#define MIX_IS_VIDEOCONFIGPARAMSENC_PREVIEW_CLASS(klass) (G_TYPE_CHECK_CLASS_TYPE ((klass), MIX_TYPE_VIDEOCONFIGPARAMSENC_PREVIEW))

/**
* MIX_VIDEOCONFIGPARAMSENC_PREVIEW_GET_CLASS:
* @obj: a #MixParams object.
* 
* Get the class instance of the object.
*/
#define MIX_VIDEOCONFIGPARAMSENC_PREVIEW_GET_CLASS(obj) (G_TYPE_INSTANCE_GET_CLASS ((obj), MIX_TYPE_VIDEOCONFIGPARAMSENC_PREVIEW, MixVideoConfigParamsEncPreviewClass))

typedef struct _MixVideoConfigParamsEncPreview MixVideoConfigParamsEncPreview;
typedef struct _MixVideoConfigParamsEncPreviewClass MixVideoConfigParamsEncPreviewClass;

/**
* MixVideoConfigParamsEncPreview:
*
* MI-X VideoConfig Parameter object
*/
struct _MixVideoConfigParamsEncPreview
{
  /*< public > */
  MixVideoConfigParamsEnc parent;

  void *reserved1;
  void *reserved2;
  void *reserved3;
  void *reserved4;
};

/**
* MixVideoConfigParamsEncPreviewClass:
* 
* MI-X VideoConfig object class
*/
struct _MixVideoConfigParamsEncPreviewClass
{
  /*< public > */
  MixVideoConfigParamsEncClass parent_class;

  /* class members */
};

/**
* mix_videoconfigparamsenc_preview_get_type:
* @returns: type
* 
* Get the type of object.
*/
GType mix_videoconfigparamsenc_preview_get_type (void);

/**
* mix_videoconfigparamsenc_preview_new:
* @returns: A newly allocated instance of #MixVideoConfigParamsEncPreview
* 
* Use this method to create new instance of #MixVideoConfigParamsEncPreview
*/
MixVideoConfigParamsEncPreview *mix_videoconfigparamsenc_preview_new (void);
/**
* mix_videoconfigparamsenc_preview_ref:
* @mix: object to add reference
* @returns: the MixVideoConfigParamsEncPreview instance where reference count has been increased.
* 
* Add reference count.
*/
MixVideoConfigParamsEncPreview
  * mix_videoconfigparamsenc_preview_ref (MixVideoConfigParamsEncPreview * mix);

/**
* mix_videoconfigparamsenc_preview_unref:
* @obj: object to unref.
* 
* Decrement reference count of the object.
*/
#define mix_videoconfigparamsenc_preview_unref(obj) mix_params_unref(MIX_PARAMS(obj))

#endif /* __MIX_VIDEOCONFIGPARAMSENC_PREVIEW_H__ */

