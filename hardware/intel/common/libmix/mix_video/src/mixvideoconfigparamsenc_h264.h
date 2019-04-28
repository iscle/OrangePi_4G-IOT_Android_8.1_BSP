/* 
INTEL CONFIDENTIAL
Copyright 2009 Intel Corporation All Rights Reserved. 
The source code contained or described herein and all documents related to the source code ("Material") are owned by Intel Corporation or its suppliers or licensors. Title to the Material remains with Intel Corporation or its suppliers and licensors. The Material contains trade secrets and proprietary and confidential information of Intel or its suppliers and licensors. The Material is protected by worldwide copyright and trade secret laws and treaty provisions. No part of the Material may be used, copied, reproduced, modified, published, uploaded, posted, transmitted, distributed, or disclosed in any way without Intel’s prior express written permission.

No license under any patent, copyright, trade secret or other intellectual property right is granted to or conferred upon you by disclosure or delivery of the Materials, either expressly, by implication, inducement, estoppel or otherwise. Any license under such intellectual property rights must be express and approved by Intel in writing.
*/

#ifndef __MIX_VIDEOCONFIGPARAMSENC_H264_H__
#define __MIX_VIDEOCONFIGPARAMSENC_H264_H__

#include "mixvideoconfigparamsenc.h"
#include "mixvideodef.h"

/**
* MIX_TYPE_VIDEOCONFIGPARAMSENC_H264:
* 
* Get type of class.
*/
#define MIX_TYPE_VIDEOCONFIGPARAMSENC_H264 (mix_videoconfigparamsenc_h264_get_type ())

/**
* MIX_VIDEOCONFIGPARAMSENC_H264:
* @obj: object to be type-casted.
*/
#define MIX_VIDEOCONFIGPARAMSENC_H264(obj) (G_TYPE_CHECK_INSTANCE_CAST ((obj), MIX_TYPE_VIDEOCONFIGPARAMSENC_H264, MixVideoConfigParamsEncH264))

/**
* MIX_IS_VIDEOCONFIGPARAMSENC_H264:
* @obj: an object.
* 
* Checks if the given object is an instance of #MixVideoConfigParamsEncH264
*/
#define MIX_IS_VIDEOCONFIGPARAMSENC_H264(obj) (G_TYPE_CHECK_INSTANCE_TYPE ((obj), MIX_TYPE_VIDEOCONFIGPARAMSENC_H264))

/**
* MIX_VIDEOCONFIGPARAMSENC_H264_CLASS:
* @klass: class to be type-casted.
*/
#define MIX_VIDEOCONFIGPARAMSENC_H264_CLASS(klass) (G_TYPE_CHECK_CLASS_CAST ((klass), MIX_TYPE_VIDEOCONFIGPARAMSENC_H264, MixVideoConfigParamsEncH264Class))

/**
* MIX_IS_VIDEOCONFIGPARAMSENC_H264_CLASS:
* @klass: a class.
* 
* Checks if the given class is #MixVideoConfigParamsEncH264Class
*/
#define MIX_IS_VIDEOCONFIGPARAMSENC_H264_CLASS(klass) (G_TYPE_CHECK_CLASS_TYPE ((klass), MIX_TYPE_VIDEOCONFIGPARAMSENC_H264))

/**
* MIX_VIDEOCONFIGPARAMSENC_H264_GET_CLASS:
* @obj: a #MixParams object.
* 
* Get the class instance of the object.
*/
#define MIX_VIDEOCONFIGPARAMSENC_H264_GET_CLASS(obj) (G_TYPE_INSTANCE_GET_CLASS ((obj), MIX_TYPE_VIDEOCONFIGPARAMSENC_H264, MixVideoConfigParamsEncH264Class))

typedef struct _MixVideoConfigParamsEncH264 MixVideoConfigParamsEncH264;
typedef struct _MixVideoConfigParamsEncH264Class MixVideoConfigParamsEncH264Class;

/**
* MixVideoConfigParamsEncH264:
*
* MI-X VideoConfig Parameter object
*/
struct _MixVideoConfigParamsEncH264
{
  /*< public > */
  MixVideoConfigParamsEnc parent;

  /*< public > */

  /* TODO: Add H.264 configuration paramters */
  guint basic_unit_size;
  guint slice_num;
  guint8 disable_deblocking_filter_idc;	

  MixDelimiterType delimiter_type;
  
  void *reserved1;
  void *reserved2;
  void *reserved3;
  void *reserved4;
};

/**
* MixVideoConfigParamsEncH264Class:
* 
* MI-X VideoConfig object class
*/
struct _MixVideoConfigParamsEncH264Class
{
  /*< public > */
  MixVideoConfigParamsEncClass parent_class;

  /* class members */
};

/**
* mix_videoconfigparamsenc_h264_get_type:
* @returns: type
* 
* Get the type of object.
*/
GType mix_videoconfigparamsenc_h264_get_type (void);

/**
* mix_videoconfigparamsenc_h264_new:
* @returns: A newly allocated instance of #MixVideoConfigParamsEncH264
* 
* Use this method to create new instance of #MixVideoConfigParamsEncH264
*/
MixVideoConfigParamsEncH264 *mix_videoconfigparamsenc_h264_new (void);
/**
* mix_videoconfigparamsenc_h264_ref:
* @mix: object to add reference
* @returns: the MixVideoConfigParamsEncH264 instance where reference count has been increased.
* 
* Add reference count.
*/
MixVideoConfigParamsEncH264
  * mix_videoconfigparamsenc_h264_ref (MixVideoConfigParamsEncH264 * mix);

/**
* mix_videoconfigparamsenc_h264_unref:
* @obj: object to unref.
* 
* Decrement reference count of the object.
*/
#define mix_videoconfigparamsenc_h264_unref(obj) mix_params_unref(MIX_PARAMS(obj))

/* Class Methods */

/* TODO: Add getters and setters for other properties */
MIX_RESULT mix_videoconfigparamsenc_h264_set_bus (MixVideoConfigParamsEncH264 * obj,
		guint basic_unit_size);

MIX_RESULT mix_videoconfigparamsenc_h264_get_bus (MixVideoConfigParamsEncH264 * obj,
		guint * basic_unit_size);

MIX_RESULT mix_videoconfigparamsenc_h264_set_dlk (MixVideoConfigParamsEncH264 * obj,
		guint disable_deblocking_filter_idc);

MIX_RESULT mix_videoconfigparamsenc_h264_get_dlk (MixVideoConfigParamsEncH264 * obj,
		guint * disable_deblocking_filter_idc);

MIX_RESULT mix_videoconfigparamsenc_h264_set_slice_num(MixVideoConfigParamsEncH264 * obj,
		guint slice_num);

MIX_RESULT mix_videoconfigparamsenc_h264_get_slice_num(MixVideoConfigParamsEncH264 * obj,
		guint * slice_num);

MIX_RESULT mix_videoconfigparamsenc_h264_set_delimiter_type (MixVideoConfigParamsEncH264 * obj,
		MixDelimiterType delimiter_type);

MIX_RESULT mix_videoconfigparamsenc_h264_get_delimiter_type (MixVideoConfigParamsEncH264 * obj,
		MixDelimiterType * delimiter_type);

#endif /* __MIX_VIDEOCONFIGPARAMSENC_H264_H__ */

