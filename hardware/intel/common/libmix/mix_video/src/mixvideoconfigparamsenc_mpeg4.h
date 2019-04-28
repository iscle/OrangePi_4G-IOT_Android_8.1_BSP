/* 
INTEL CONFIDENTIAL
Copyright 2009 Intel Corporation All Rights Reserved. 
The source code contained or described herein and all documents related to the source code ("Material") are owned by Intel Corporation or its suppliers or licensors. Title to the Material remains with Intel Corporation or its suppliers and licensors. The Material contains trade secrets and proprietary and confidential information of Intel or its suppliers and licensors. The Material is protected by worldwide copyright and trade secret laws and treaty provisions. No part of the Material may be used, copied, reproduced, modified, published, uploaded, posted, transmitted, distributed, or disclosed in any way without Intel’s prior express written permission.

No license under any patent, copyright, trade secret or other intellectual property right is granted to or conferred upon you by disclosure or delivery of the Materials, either expressly, by implication, inducement, estoppel or otherwise. Any license under such intellectual property rights must be express and approved by Intel in writing.
*/

#ifndef __MIX_VIDEOCONFIGPARAMSENC_MPEG4_H__
#define __MIX_VIDEOCONFIGPARAMSENC_MPEG4_H__

#include "mixvideoconfigparamsenc.h"
#include "mixvideodef.h"

/**
* MIX_TYPE_VIDEOCONFIGPARAMSENC_MPEG4:
* 
* Get type of class.
*/
#define MIX_TYPE_VIDEOCONFIGPARAMSENC_MPEG4 (mix_videoconfigparamsenc_mpeg4_get_type ())

/**
* MIX_VIDEOCONFIGPARAMSENC_MPEG4:
* @obj: object to be type-casted.
*/
#define MIX_VIDEOCONFIGPARAMSENC_MPEG4(obj) (G_TYPE_CHECK_INSTANCE_CAST ((obj), MIX_TYPE_VIDEOCONFIGPARAMSENC_MPEG4, MixVideoConfigParamsEncMPEG4))

/**
* MIX_IS_VIDEOCONFIGPARAMSENC_MPEG4:
* @obj: an object.
* 
* Checks if the given object is an instance of #MixVideoConfigParamsEncMPEG4
*/
#define MIX_IS_VIDEOCONFIGPARAMSENC_MPEG4(obj) (G_TYPE_CHECK_INSTANCE_TYPE ((obj), MIX_TYPE_VIDEOCONFIGPARAMSENC_MPEG4))

/**
* MIX_VIDEOCONFIGPARAMSENC_MPEG4_CLASS:
* @klass: class to be type-casted.
*/
#define MIX_VIDEOCONFIGPARAMSENC_MPEG4_CLASS(klass) (G_TYPE_CHECK_CLASS_CAST ((klass), MIX_TYPE_VIDEOCONFIGPARAMSENC_MPEG4, MixVideoConfigParamsEncMPEG4Class))

/**
* MIX_IS_VIDEOCONFIGPARAMSENC_MPEG4_CLASS:
* @klass: a class.
* 
* Checks if the given class is #MixVideoConfigParamsEncMPEG4Class
*/
#define MIX_IS_VIDEOCONFIGPARAMSENC_MPEG4_CLASS(klass) (G_TYPE_CHECK_CLASS_TYPE ((klass), MIX_TYPE_VIDEOCONFIGPARAMSENC_MPEG4))

/**
* MIX_VIDEOCONFIGPARAMSENC_MPEG4_GET_CLASS:
* @obj: a #MixParams object.
* 
* Get the class instance of the object.
*/
#define MIX_VIDEOCONFIGPARAMSENC_MPEG4_GET_CLASS(obj) (G_TYPE_INSTANCE_GET_CLASS ((obj), MIX_TYPE_VIDEOCONFIGPARAMSENC_MPEG4, MixVideoConfigParamsEncMPEG4Class))

typedef struct _MixVideoConfigParamsEncMPEG4 MixVideoConfigParamsEncMPEG4;
typedef struct _MixVideoConfigParamsEncMPEG4Class MixVideoConfigParamsEncMPEG4Class;

/**
* MixVideoConfigParamsEncMPEG4:
*
* MI-X VideoConfig Parameter object
*/
struct _MixVideoConfigParamsEncMPEG4
{
  /*< public > */
  MixVideoConfigParamsEnc parent;

  /*< public > */

  /* TODO: Add MPEG-4 configuration paramters */
  guchar  profile_and_level_indication;
  guint fixed_vop_time_increment;
  guint disable_deblocking_filter_idc;
  
  void *reserved1;
  void *reserved2;
  void *reserved3;
  void *reserved4;
};

/**
* MixVideoConfigParamsEncMPEG4Class:
* 
* MI-X VideoConfig object class
*/
struct _MixVideoConfigParamsEncMPEG4Class
{
  /*< public > */
  MixVideoConfigParamsEncClass parent_class;

  /* class members */
};

/**
* mix_videoconfigparamsenc_mpeg4_get_type:
* @returns: type
* 
* Get the type of object.
*/
GType mix_videoconfigparamsenc_mpeg4_get_type (void);

/**
* mix_videoconfigparamsenc_mpeg4_new:
* @returns: A newly allocated instance of #MixVideoConfigParamsEncMPEG4
* 
* Use this method to create new instance of #MixVideoConfigParamsEncMPEG4
*/
MixVideoConfigParamsEncMPEG4 *mix_videoconfigparamsenc_mpeg4_new (void);
/**
* mix_videoconfigparamsenc_mpeg4_ref:
* @mix: object to add reference
* @returns: the MixVideoConfigParamsEncMPEG4 instance where reference count has been increased.
* 
* Add reference count.
*/
MixVideoConfigParamsEncMPEG4
  * mix_videoconfigparamsenc_mpeg4_ref (MixVideoConfigParamsEncMPEG4 * mix);

/**
* mix_videoconfigparamsenc_mpeg4_unref:
* @obj: object to unref.
* 
* Decrement reference count of the object.
*/
#define mix_videoconfigparamsenc_mpeg4_unref(obj) mix_params_unref(MIX_PARAMS(obj))

/* Class Methods */

/* TODO: Add getters and setters for other properties */

MIX_RESULT mix_videoconfigparamsenc_mpeg4_set_dlk (MixVideoConfigParamsEncMPEG4 * obj,
		guint disable_deblocking_filter_idc);

MIX_RESULT mix_videoconfigparamsenc_mpeg4_get_dlk (MixVideoConfigParamsEncMPEG4 * obj,
		guint * disable_deblocking_filter_idc);

MIX_RESULT mix_videoconfigparamsenc_mpeg4_set_profile_level (MixVideoConfigParamsEncMPEG4 * obj,
		guchar profile_and_level_indication);

MIX_RESULT mix_videoconfigparamsenc_mpeg4_get_profile_level (MixVideoConfigParamsEncMPEG4 * obj,
		guchar * profile_and_level_indication);

MIX_RESULT mix_videoconfigparamsenc_mpeg4_set_fixed_vti (MixVideoConfigParamsEncMPEG4 * obj,
		guint fixed_vop_time_increment);

MIX_RESULT mix_videoconfigparamsenc_mpeg4_get_fixed_vti (MixVideoConfigParamsEncMPEG4 * obj,
		guint * fixed_vop_time_increment);

#endif /* __MIX_VIDEOCONFIGPARAMSENC_MPEG4_H__ */
