/* 
INTEL CONFIDENTIAL
Copyright 2009 Intel Corporation All Rights Reserved. 
The source code contained or described herein and all documents related to the source code ("Material") are owned by Intel Corporation or its suppliers or licensors. Title to the Material remains with Intel Corporation or its suppliers and licensors. The Material contains trade secrets and proprietary and confidential information of Intel or its suppliers and licensors. The Material is protected by worldwide copyright and trade secret laws and treaty provisions. No part of the Material may be used, copied, reproduced, modified, published, uploaded, posted, transmitted, distributed, or disclosed in any way without Intel’s prior express written permission.

No license under any patent, copyright, trade secret or other intellectual property right is granted to or conferred upon you by disclosure or delivery of the Materials, either expressly, by implication, inducement, estoppel or otherwise. Any license under such intellectual property rights must be express and approved by Intel in writing.
*/

/**
* SECTION:mixvideoconfigparamsenc_h264
* @short_description: VideoConfig parameters
*
* A data object which stores videoconfig specific parameters.
*/

#include "mixvideolog.h"
#include "mixvideoconfigparamsenc_h264.h"

#define MDEBUG


static GType _mix_videoconfigparamsenc_h264_type = 0;
static MixVideoConfigParamsEncClass *parent_class = NULL;

#define _do_init { _mix_videoconfigparamsenc_h264_type = g_define_type_id; }

gboolean mix_videoconfigparamsenc_h264_copy (MixParams * target,
					  const MixParams * src);
MixParams *mix_videoconfigparamsenc_h264_dup (const MixParams * obj);
gboolean mix_videoconfigparamsencenc_h264_equal (MixParams * first,
					   MixParams * second);
static void mix_videoconfigparamsenc_h264_finalize (MixParams * obj);

G_DEFINE_TYPE_WITH_CODE (MixVideoConfigParamsEncH264,	/* The name of the new type, in Camel case */
			 mix_videoconfigparamsenc_h264,	/* The name of the new type in lowercase */
			 MIX_TYPE_VIDEOCONFIGPARAMSENC,	/* The GType of the parent type */
			 _do_init);

void
_mix_videoconfigparamsenc_h264_initialize (void)
{
  /* the MixParams types need to be class_ref'd once before it can be
   * done from multiple threads;
   * see http://bugzilla.gnome.org/show_bug.cgi?id=304551 */
  g_type_class_ref (mix_videoconfigparamsenc_h264_get_type ());
}

static void
mix_videoconfigparamsenc_h264_init (MixVideoConfigParamsEncH264 * self)
{
  /* initialize properties here */
  /* TODO: initialize properties */
  self->basic_unit_size = 0;
  self->slice_num = 1;
  self->disable_deblocking_filter_idc = 0;

  self->delimiter_type = MIX_DELIMITER_LENGTHPREFIX;

  self->reserved1 = NULL;
  self->reserved2 = NULL;
  self->reserved3 = NULL;
  self->reserved4 = NULL;
}

static void
mix_videoconfigparamsenc_h264_class_init (MixVideoConfigParamsEncH264Class * klass)
{
  MixVideoConfigParamsEncClass *this_parent_class =
    MIX_VIDEOCONFIGPARAMSENC_CLASS (klass);
  MixParamsClass *this_root_class = MIX_PARAMS_CLASS (this_parent_class);

  /* setup static parent class */
  parent_class =
    (MixVideoConfigParamsEncClass *) g_type_class_peek_parent (klass);

  this_root_class->finalize = mix_videoconfigparamsenc_h264_finalize;
  this_root_class->copy =
    (MixParamsCopyFunction) mix_videoconfigparamsenc_h264_copy;
  this_root_class->dup =
    (MixParamsDupFunction) mix_videoconfigparamsenc_h264_dup;
  this_root_class->equal =
    (MixParamsEqualFunction) mix_videoconfigparamsencenc_h264_equal;
}

MixVideoConfigParamsEncH264 *
mix_videoconfigparamsenc_h264_new (void)
{
  MixVideoConfigParamsEncH264 *ret = (MixVideoConfigParamsEncH264 *)
    g_type_create_instance (MIX_TYPE_VIDEOCONFIGPARAMSENC_H264);

  return ret;
}

void
mix_videoconfigparamsenc_h264_finalize (MixParams * obj)
{
  /* MixVideoConfigParamsEncH264 *this_obj = MIX_VIDEOCONFIGPARAMSENC_H264 (obj); */
  MixParamsClass *root_class = MIX_PARAMS_CLASS (parent_class);

  /* TODO: cleanup resources allocated */

  /* Chain up parent */

  if (root_class->finalize)
    {
      root_class->finalize (obj);
    }
}

MixVideoConfigParamsEncH264
  * mix_videoconfigparamsenc_h264_ref (MixVideoConfigParamsEncH264 * mix)
{
  return (MixVideoConfigParamsEncH264 *) mix_params_ref (MIX_PARAMS (mix));
}

/**
* mix_videoconfigparamsenc_h264_dup:
* @obj: a #MixVideoConfigParams object
* @returns: a newly allocated duplicate of the object.
* 
* Copy duplicate of the object.
*/
MixParams *
mix_videoconfigparamsenc_h264_dup (const MixParams * obj)
{
  MixParams *ret = NULL;

  if (MIX_IS_VIDEOCONFIGPARAMSENC_H264 (obj))
    {
      MixVideoConfigParamsEncH264 *duplicate = mix_videoconfigparamsenc_h264_new ();
      if (mix_videoconfigparamsenc_h264_copy
	  (MIX_PARAMS (duplicate), MIX_PARAMS (obj)))
	{
	  ret = MIX_PARAMS (duplicate);
	}
      else
	{
	  mix_videoconfigparamsenc_h264_unref (duplicate);
	}
    }
  return ret;
}

/**
* mix_videoconfigparamsenc_h264_copy:
* @target: copy to target
* @src: copy from src
* @returns: boolean indicates if copy is successful.
* 
* Copy instance data from @src to @target.
*/
gboolean
mix_videoconfigparamsenc_h264_copy (MixParams * target, const MixParams * src)
{
    MixVideoConfigParamsEncH264 *this_target, *this_src;
    MixParamsClass *root_class;

    LOG_V( "Begin\n");	

    if (MIX_IS_VIDEOCONFIGPARAMSENC_H264 (target)
      && MIX_IS_VIDEOCONFIGPARAMSENC_H264 (src))
    {
      // Cast the base object to this child object
      this_target = MIX_VIDEOCONFIGPARAMSENC_H264 (target);
      this_src = MIX_VIDEOCONFIGPARAMSENC_H264 (src);

      //add properties
      this_target->basic_unit_size = this_src->basic_unit_size;
      this_target->slice_num = this_src->slice_num;
      this_target->disable_deblocking_filter_idc = this_src->disable_deblocking_filter_idc;
      this_target->delimiter_type = this_src->delimiter_type;
	  

      // Now chainup base class
      root_class = MIX_PARAMS_CLASS (parent_class);

      if (root_class->copy)
	{
	  return root_class->copy (MIX_PARAMS_CAST (target),
				   MIX_PARAMS_CAST (src));
	}
      else
	{
	  return TRUE;
	}
    }
  return FALSE;
}

/**
* mix_videoconfigparamsenc_h264:
* @first: first object to compare
* @second: seond object to compare
* @returns: boolean indicates if instance are equal.
* 
* Copy instance data from @src to @target.
*/
gboolean
mix_videoconfigparamsencenc_h264_equal (MixParams * first, MixParams * second)
{
  gboolean ret = FALSE;
  MixVideoConfigParamsEncH264 *this_first, *this_second;

  if (MIX_IS_VIDEOCONFIGPARAMSENC_H264 (first)
      && MIX_IS_VIDEOCONFIGPARAMSENC_H264 (second))
    {
      // Cast the base object to this child object

      this_first = MIX_VIDEOCONFIGPARAMSENC_H264 (first);
      this_second = MIX_VIDEOCONFIGPARAMSENC_H264 (second);

      if (this_first->basic_unit_size != this_second->basic_unit_size) {
	  	goto not_equal;
	}
	  
      if (this_first->slice_num != this_second->slice_num) {
	  	goto not_equal;
	}

      if (this_first->disable_deblocking_filter_idc != this_second->disable_deblocking_filter_idc) {
	  	goto not_equal;
	}  

      if (this_first->delimiter_type != this_second->delimiter_type) {
	  	goto not_equal;
	}  	  
	  

	ret = TRUE;

	not_equal:

	if (ret != TRUE) {
		return ret;
	}		

      /* TODO: add comparison for properties */
      {
	// members within this scope equal. chaining up.
	MixParamsClass *klass = MIX_PARAMS_CLASS (parent_class);
	if (klass->equal)
	  {
	    ret = klass->equal (first, second);
	  }
	else
	  {
	    ret = TRUE;
	  }
      }
    }

  return ret;
}

/* TODO: Add getters and setters for properties if any */

#define MIX_VIDEOCONFIGPARAMSENC_H264_SETTER_CHECK_INPUT(obj) \
	if(!obj) return MIX_RESULT_NULL_PTR; \
	if(!MIX_IS_VIDEOCONFIGPARAMSENC_H264(obj)) return MIX_RESULT_FAIL; \

#define MIX_VIDEOCONFIGPARAMSENC_H264_GETTER_CHECK_INPUT(obj, prop) \
	if(!obj || !prop) return MIX_RESULT_NULL_PTR; \
	if(!MIX_IS_VIDEOCONFIGPARAMSENC_H264(obj)) return MIX_RESULT_FAIL; \


MIX_RESULT mix_videoconfigparamsenc_h264_set_bus (MixVideoConfigParamsEncH264 * obj,
		guint basic_unit_size) {
	MIX_VIDEOCONFIGPARAMSENC_H264_SETTER_CHECK_INPUT (obj);
	obj->basic_unit_size = basic_unit_size;
	return MIX_RESULT_SUCCESS;
}

MIX_RESULT mix_videoconfigparamsenc_h264_get_bus (MixVideoConfigParamsEncH264 * obj,
		guint * basic_unit_size) {
	MIX_VIDEOCONFIGPARAMSENC_H264_GETTER_CHECK_INPUT (obj, basic_unit_size);
	*basic_unit_size = obj->basic_unit_size;
	return MIX_RESULT_SUCCESS;
}	


MIX_RESULT mix_videoconfigparamsenc_h264_set_dlk (MixVideoConfigParamsEncH264 * obj,
		guint disable_deblocking_filter_idc) {
	MIX_VIDEOCONFIGPARAMSENC_H264_SETTER_CHECK_INPUT (obj);
	obj->disable_deblocking_filter_idc = disable_deblocking_filter_idc;
	return MIX_RESULT_SUCCESS;
}

MIX_RESULT mix_videoconfigparamsenc_h264_get_dlk (MixVideoConfigParamsEncH264 * obj,
		guint * disable_deblocking_filter_idc) {
	MIX_VIDEOCONFIGPARAMSENC_H264_GETTER_CHECK_INPUT (obj, disable_deblocking_filter_idc);
	*disable_deblocking_filter_idc = obj->disable_deblocking_filter_idc;
	return MIX_RESULT_SUCCESS;
}	


MIX_RESULT mix_videoconfigparamsenc_h264_set_slice_num(MixVideoConfigParamsEncH264 * obj,
		guint slice_num) {
	MIX_VIDEOCONFIGPARAMSENC_H264_SETTER_CHECK_INPUT (obj);
	obj->slice_num = slice_num;
	return MIX_RESULT_SUCCESS;
}

MIX_RESULT mix_videoconfigparamsenc_h264_get_slice_num(MixVideoConfigParamsEncH264 * obj,
		guint * slice_num) {
	MIX_VIDEOCONFIGPARAMSENC_H264_GETTER_CHECK_INPUT (obj, slice_num);
	*slice_num = obj->slice_num;
	return MIX_RESULT_SUCCESS;
}	

MIX_RESULT mix_videoconfigparamsenc_h264_set_delimiter_type (MixVideoConfigParamsEncH264 * obj,
		MixDelimiterType delimiter_type) {
	MIX_VIDEOCONFIGPARAMSENC_H264_SETTER_CHECK_INPUT (obj);
	obj->delimiter_type = delimiter_type;
	return MIX_RESULT_SUCCESS;
}

MIX_RESULT mix_videoconfigparamsenc_h264_get_delimiter_type (MixVideoConfigParamsEncH264 * obj,
		MixDelimiterType * delimiter_type) {
	MIX_VIDEOCONFIGPARAMSENC_H264_GETTER_CHECK_INPUT (obj, delimiter_type);
	*delimiter_type = obj->delimiter_type;
	return MIX_RESULT_SUCCESS;
}
