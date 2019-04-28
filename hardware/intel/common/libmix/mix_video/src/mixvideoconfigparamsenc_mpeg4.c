/* 
INTEL CONFIDENTIAL
Copyright 2009 Intel Corporation All Rights Reserved. 
The source code contained or described herein and all documents related to the source code ("Material") are owned by Intel Corporation or its suppliers or licensors. Title to the Material remains with Intel Corporation or its suppliers and licensors. The Material contains trade secrets and proprietary and confidential information of Intel or its suppliers and licensors. The Material is protected by worldwide copyright and trade secret laws and treaty provisions. No part of the Material may be used, copied, reproduced, modified, published, uploaded, posted, transmitted, distributed, or disclosed in any way without Intel’s prior express written permission.

No license under any patent, copyright, trade secret or other intellectual property right is granted to or conferred upon you by disclosure or delivery of the Materials, either expressly, by implication, inducement, estoppel or otherwise. Any license under such intellectual property rights must be express and approved by Intel in writing.
*/

/**
* SECTION:mixvideoconfigparamsenc_mpeg4
* @short_description: VideoConfig parameters
*
* A data object which stores videoconfig specific parameters.
*/

#include "mixvideolog.h"
#include "mixvideoconfigparamsenc_mpeg4.h"

#define MDEBUG


static GType _mix_videoconfigparamsenc_mpeg4_type = 0;
static MixVideoConfigParamsEncClass *parent_class = NULL;

#define _do_init { _mix_videoconfigparamsenc_mpeg4_type = g_define_type_id; }

gboolean mix_videoconfigparamsenc_mpeg4_copy (MixParams * target,
					  const MixParams * src);
MixParams *mix_videoconfigparamsenc_mpeg4_dup (const MixParams * obj);
gboolean mix_videoconfigparamsencenc_mpeg4_equal (MixParams * first,
					   MixParams * second);
static void mix_videoconfigparamsenc_mpeg4_finalize (MixParams * obj);

G_DEFINE_TYPE_WITH_CODE (MixVideoConfigParamsEncMPEG4,	/* The name of the new type, in Camel case */
			 mix_videoconfigparamsenc_mpeg4,	/* The name of the new type in lowercase */
			 MIX_TYPE_VIDEOCONFIGPARAMSENC,	/* The GType of the parent type */
			 _do_init);

void
_mix_videoconfigparamsenc_mpeg4_initialize (void)
{
  /* the MixParams types need to be class_ref'd once before it can be
   * done from multiple threads;
   * see http://bugzilla.gnome.org/show_bug.cgi?id=304551 */
  g_type_class_ref (mix_videoconfigparamsenc_mpeg4_get_type ());
}

static void
mix_videoconfigparamsenc_mpeg4_init (MixVideoConfigParamsEncMPEG4 * self)
{
  /* initialize properties here */
  /* TODO: initialize properties */

  self->fixed_vop_time_increment = 3;
  self->profile_and_level_indication = 3;
  self->disable_deblocking_filter_idc = 0;

  self->reserved1 = NULL;
  self->reserved2 = NULL;
  self->reserved3 = NULL;
  self->reserved4 = NULL;
}

static void
mix_videoconfigparamsenc_mpeg4_class_init (MixVideoConfigParamsEncMPEG4Class * klass)
{
  MixVideoConfigParamsEncClass *this_parent_class =
    MIX_VIDEOCONFIGPARAMSENC_CLASS (klass);
  MixParamsClass *this_root_class = MIX_PARAMS_CLASS (this_parent_class);

  /* setup static parent class */
  parent_class =
    (MixVideoConfigParamsEncClass *) g_type_class_peek_parent (klass);

  this_root_class->finalize = mix_videoconfigparamsenc_mpeg4_finalize;
  this_root_class->copy =
    (MixParamsCopyFunction) mix_videoconfigparamsenc_mpeg4_copy;
  this_root_class->dup =
    (MixParamsDupFunction) mix_videoconfigparamsenc_mpeg4_dup;
  this_root_class->equal =
    (MixParamsEqualFunction) mix_videoconfigparamsencenc_mpeg4_equal;
}

MixVideoConfigParamsEncMPEG4 *
mix_videoconfigparamsenc_mpeg4_new (void)
{
  MixVideoConfigParamsEncMPEG4 *ret = (MixVideoConfigParamsEncMPEG4 *)
    g_type_create_instance (MIX_TYPE_VIDEOCONFIGPARAMSENC_MPEG4);

  return ret;
}

void
mix_videoconfigparamsenc_mpeg4_finalize (MixParams * obj)
{
  /* MixVideoConfigParamsEncMPEG4 *this_obj = MIX_VIDEOCONFIGPARAMSENC_MPEG4 (obj); */
  MixParamsClass *root_class = MIX_PARAMS_CLASS (parent_class);

  /* TODO: cleanup resources allocated */

  /* Chain up parent */

  if (root_class->finalize)
    {
      root_class->finalize (obj);
    }
}

MixVideoConfigParamsEncMPEG4
  * mix_videoconfigparamsenc_mpeg4_ref (MixVideoConfigParamsEncMPEG4 * mix)
{
  return (MixVideoConfigParamsEncMPEG4 *) mix_params_ref (MIX_PARAMS (mix));
}

/**
* mix_videoconfigparamsenc_mpeg4_dup:
* @obj: a #MixVideoConfigParams object
* @returns: a newly allocated duplicate of the object.
* 
* Copy duplicate of the object.
*/
MixParams *
mix_videoconfigparamsenc_mpeg4_dup (const MixParams * obj)
{
  MixParams *ret = NULL;

  if (MIX_IS_VIDEOCONFIGPARAMSENC_MPEG4 (obj))
    {
      MixVideoConfigParamsEncMPEG4 *duplicate = mix_videoconfigparamsenc_mpeg4_new ();
      if (mix_videoconfigparamsenc_mpeg4_copy
	  (MIX_PARAMS (duplicate), MIX_PARAMS (obj)))
	{
	  ret = MIX_PARAMS (duplicate);
	}
      else
	{
	  mix_videoconfigparamsenc_mpeg4_unref (duplicate);
	}
    }
  return ret;
}

/**
* mix_videoconfigparamsenc_mpeg4_copy:
* @target: copy to target
* @src: copy from src
* @returns: boolean indicates if copy is successful.
* 
* Copy instance data from @src to @target.
*/
gboolean
mix_videoconfigparamsenc_mpeg4_copy (MixParams * target, const MixParams * src)
{
    MixVideoConfigParamsEncMPEG4 *this_target, *this_src;
    MixParamsClass *root_class;

    LOG_V( "Begin\n");	

    if (MIX_IS_VIDEOCONFIGPARAMSENC_MPEG4 (target)
      && MIX_IS_VIDEOCONFIGPARAMSENC_MPEG4 (src))
    {
      // Cast the base object to this child object
      this_target = MIX_VIDEOCONFIGPARAMSENC_MPEG4 (target);
      this_src = MIX_VIDEOCONFIGPARAMSENC_MPEG4 (src);

      //add properties
      this_target->profile_and_level_indication= this_src->profile_and_level_indication;
      this_target->fixed_vop_time_increment= this_src->fixed_vop_time_increment;      
      this_target->disable_deblocking_filter_idc = this_src->disable_deblocking_filter_idc;

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
* mix_videoconfigparamsenc_mpeg4:
* @first: first object to compare
* @second: seond object to compare
* @returns: boolean indicates if instance are equal.
* 
* Copy instance data from @src to @target.
*/
gboolean
mix_videoconfigparamsencenc_mpeg4_equal (MixParams * first, MixParams * second)
{
  gboolean ret = FALSE;
  MixVideoConfigParamsEncMPEG4 *this_first, *this_second;

  if (MIX_IS_VIDEOCONFIGPARAMSENC_MPEG4 (first)
      && MIX_IS_VIDEOCONFIGPARAMSENC_MPEG4 (second))
    {
      // Cast the base object to this child object

      this_first = MIX_VIDEOCONFIGPARAMSENC_MPEG4 (first);
      this_second = MIX_VIDEOCONFIGPARAMSENC_MPEG4 (second);

      if (this_first->profile_and_level_indication!= this_second->profile_and_level_indication) {
	  	goto not_equal;
	}	

      if (this_first->fixed_vop_time_increment!= this_second->fixed_vop_time_increment) {
	  	goto not_equal;
	}		  

      if (this_first->disable_deblocking_filter_idc != this_second->disable_deblocking_filter_idc) {
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

#define MIX_VIDEOCONFIGPARAMSENC_MPEG4_SETTER_CHECK_INPUT(obj) \
	if(!obj) return MIX_RESULT_NULL_PTR; \
	if(!MIX_IS_VIDEOCONFIGPARAMSENC_MPEG4(obj)) return MIX_RESULT_FAIL; \

#define MIX_VIDEOCONFIGPARAMSENC_MPEG4_GETTER_CHECK_INPUT(obj, prop) \
	if(!obj || !prop) return MIX_RESULT_NULL_PTR; \
	if(!MIX_IS_VIDEOCONFIGPARAMSENC_MPEG4(obj)) return MIX_RESULT_FAIL; \


MIX_RESULT mix_videoconfigparamsenc_mpeg4_set_profile_level (MixVideoConfigParamsEncMPEG4 * obj,
		guchar profile_and_level_indication) {
	MIX_VIDEOCONFIGPARAMSENC_MPEG4_SETTER_CHECK_INPUT (obj);
	obj->profile_and_level_indication = profile_and_level_indication;
	return MIX_RESULT_SUCCESS;		
}

MIX_RESULT mix_videoconfigparamsenc_mpeg4_get_profile_level (MixVideoConfigParamsEncMPEG4 * obj,
		guchar * profile_and_level_indication) {
	MIX_VIDEOCONFIGPARAMSENC_MPEG4_GETTER_CHECK_INPUT (obj, profile_and_level_indication);
	*profile_and_level_indication = obj->profile_and_level_indication;
	return MIX_RESULT_SUCCESS;		
}

MIX_RESULT mix_videoconfigparamsenc_mpeg4_set_fixed_vti (MixVideoConfigParamsEncMPEG4 * obj,
		guint fixed_vop_time_increment) {
	MIX_VIDEOCONFIGPARAMSENC_MPEG4_SETTER_CHECK_INPUT (obj);
	obj->fixed_vop_time_increment = fixed_vop_time_increment;
	return MIX_RESULT_SUCCESS;		
}

MIX_RESULT mix_videoconfigparamsenc_mpeg4_get_fixed_vti (MixVideoConfigParamsEncMPEG4 * obj,
		guint * fixed_vop_time_increment) {
	MIX_VIDEOCONFIGPARAMSENC_MPEG4_GETTER_CHECK_INPUT (obj, fixed_vop_time_increment);
	*fixed_vop_time_increment = obj->fixed_vop_time_increment;
	return MIX_RESULT_SUCCESS;		
}

MIX_RESULT mix_videoconfigparamsenc_mpeg4_set_dlk (MixVideoConfigParamsEncMPEG4 * obj,
		guint disable_deblocking_filter_idc) {
	MIX_VIDEOCONFIGPARAMSENC_MPEG4_SETTER_CHECK_INPUT (obj);
	obj->disable_deblocking_filter_idc = disable_deblocking_filter_idc;
	return MIX_RESULT_SUCCESS;
}

MIX_RESULT mix_videoconfigparamsenc_mpeg4_get_dlk (MixVideoConfigParamsEncMPEG4 * obj,
		guint * disable_deblocking_filter_idc) {
	MIX_VIDEOCONFIGPARAMSENC_MPEG4_GETTER_CHECK_INPUT (obj, disable_deblocking_filter_idc);
	*disable_deblocking_filter_idc = obj->disable_deblocking_filter_idc;
	return MIX_RESULT_SUCCESS;
}

