/* 
INTEL CONFIDENTIAL
Copyright 2009 Intel Corporation All Rights Reserved. 
The source code contained or described herein and all documents related to the source code ("Material") are owned by Intel Corporation or its suppliers or licensors. Title to the Material remains with Intel Corporation or its suppliers and licensors. The Material contains trade secrets and proprietary and confidential information of Intel or its suppliers and licensors. The Material is protected by worldwide copyright and trade secret laws and treaty provisions. No part of the Material may be used, copied, reproduced, modified, published, uploaded, posted, transmitted, distributed, or disclosed in any way without Intel’s prior express written permission.

No license under any patent, copyright, trade secret or other intellectual property right is granted to or conferred upon you by disclosure or delivery of the Materials, either expressly, by implication, inducement, estoppel or otherwise. Any license under such intellectual property rights must be express and approved by Intel in writing.
*/

#ifndef __MIX_VIDEOCONFIGPARAMSDEC_VC1_H__
#define __MIX_VIDEOCONFIGPARAMSDEC_VC1_H__

#include "mixvideoconfigparamsdec.h"
#include "mixvideodef.h"

/**
* MIX_TYPE_VIDEOCONFIGPARAMSDEC_VC1:
* 
* Get type of class.
*/
#define MIX_TYPE_VIDEOCONFIGPARAMSDEC_VC1 (mix_videoconfigparamsdec_vc1_get_type ())

/**
* MIX_VIDEOCONFIGPARAMSDEC_VC1:
* @obj: object to be type-casted.
*/
#define MIX_VIDEOCONFIGPARAMSDEC_VC1(obj) (G_TYPE_CHECK_INSTANCE_CAST ((obj), MIX_TYPE_VIDEOCONFIGPARAMSDEC_VC1, MixVideoConfigParamsDecVC1))

/**
* MIX_IS_VIDEOCONFIGPARAMSDEC_VC1:
* @obj: an object.
* 
* Checks if the given object is an instance of #MixVideoConfigParamsDecVC1
*/
#define MIX_IS_VIDEOCONFIGPARAMSDEC_VC1(obj) (G_TYPE_CHECK_INSTANCE_TYPE ((obj), MIX_TYPE_VIDEOCONFIGPARAMSDEC_VC1))

/**
* MIX_VIDEOCONFIGPARAMSDEC_VC1_CLASS:
* @klass: class to be type-casted.
*/
#define MIX_VIDEOCONFIGPARAMSDEC_VC1_CLASS(klass) (G_TYPE_CHECK_CLASS_CAST ((klass), MIX_TYPE_VIDEOCONFIGPARAMSDEC_VC1, MixVideoConfigParamsDecVC1Class))

/**
* MIX_IS_VIDEOCONFIGPARAMSDEC_VC1_CLASS:
* @klass: a class.
* 
* Checks if the given class is #MixVideoConfigParamsDecVC1Class
*/
#define MIX_IS_VIDEOCONFIGPARAMSDEC_VC1_CLASS(klass) (G_TYPE_CHECK_CLASS_TYPE ((klass), MIX_TYPE_VIDEOCONFIGPARAMSDEC_VC1))

/**
* MIX_VIDEOCONFIGPARAMSDEC_VC1_GET_CLASS:
* @obj: a #MixParams object.
* 
* Get the class instance of the object.
*/
#define MIX_VIDEOCONFIGPARAMSDEC_VC1_GET_CLASS(obj) (G_TYPE_INSTANCE_GET_CLASS ((obj), MIX_TYPE_VIDEOCONFIGPARAMSDEC_VC1, MixVideoConfigParamsDecVC1Class))

typedef struct _MixVideoConfigParamsDecVC1 MixVideoConfigParamsDecVC1;
typedef struct _MixVideoConfigParamsDecVC1Class MixVideoConfigParamsDecVC1Class;

/**
* MixVideoConfigParamsDecVC1:
*
* MI-X VideoConfig Parameter object
*/
struct _MixVideoConfigParamsDecVC1
{
  /*< public > */
  MixVideoConfigParamsDec parent;

  /*< public > */

  /* TODO: Add VC1 configuration paramters */
  /* TODO: wmv_version and fourcc type might be changed later */
  guint wmv_version;
  guint fourcc;

  void *reserved1;
  void *reserved2;
  void *reserved3;
  void *reserved4;
};

/**
* MixVideoConfigParamsDecVC1Class:
* 
* MI-X VideoConfig object class
*/
struct _MixVideoConfigParamsDecVC1Class
{
  /*< public > */
  MixVideoConfigParamsDecClass parent_class;

  /* class members */
};

/**
* mix_videoconfigparamsdec_vc1_get_type:
* @returns: type
* 
* Get the type of object.
*/
GType mix_videoconfigparamsdec_vc1_get_type (void);

/**
* mix_videoconfigparamsdec_vc1_new:
* @returns: A newly allocated instance of #MixVideoConfigParamsDecVC1
* 
* Use this method to create new instance of #MixVideoConfigParamsDecVC1
*/
MixVideoConfigParamsDecVC1 *mix_videoconfigparamsdec_vc1_new (void);
/**
* mix_videoconfigparamsdec_vc1_ref:
* @mix: object to add reference
* @returns: the MixVideoConfigParamsDecVC1 instance where reference count has been increased.
* 
* Add reference count.
*/
MixVideoConfigParamsDecVC1
  * mix_videoconfigparamsdec_vc1_ref (MixVideoConfigParamsDecVC1 * mix);

/**
* mix_videoconfigparamsdec_vc1_unref:
* @obj: object to unref.
* 
* Decrement reference count of the object.
*/
#define mix_videoconfigparamsdec_vc1_unref(obj) mix_params_unref(MIX_PARAMS(obj))

/* Class Methods */

/* TODO: Add getters and setters for other properties */

#endif /* __MIX_VIDEOCONFIGPARAMSDECDEC_VC1_H__ */
