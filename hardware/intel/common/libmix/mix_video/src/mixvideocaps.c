/* 
INTEL CONFIDENTIAL
Copyright 2009 Intel Corporation All Rights Reserved. 
The source code contained or described herein and all documents related to the source code ("Material") are owned by Intel Corporation or its suppliers or licensors. Title to the Material remains with Intel Corporation or its suppliers and licensors. The Material contains trade secrets and proprietary and confidential information of Intel or its suppliers and licensors. The Material is protected by worldwide copyright and trade secret laws and treaty provisions. No part of the Material may be used, copied, reproduced, modified, published, uploaded, posted, transmitted, distributed, or disclosed in any way without Intel’s prior express written permission.

No license under any patent, copyright, trade secret or other intellectual property right is granted to or conferred upon you by disclosure or delivery of the Materials, either expressly, by implication, inducement, estoppel or otherwise. Any license under such intellectual property rights must be express and approved by Intel in writing.
*/

/**
* SECTION:mixvideocaps
* @short_description: VideoConfig parameters
*
* A data object which stores videoconfig specific parameters.
*/

#include "mixvideocaps.h"

#define SAFE_FREE(p) if(p) { g_free(p); p = NULL; }

static GType _mix_videocaps_type = 0;
static MixParamsClass *parent_class = NULL;

#define _do_init { _mix_videocaps_type = g_define_type_id; }

gboolean mix_videocaps_copy (MixParams * target, const MixParams * src);
MixParams *mix_videocaps_dup (const MixParams * obj);
gboolean mix_videocaps_equal (MixParams * first, MixParams * second);
static void mix_videocaps_finalize (MixParams * obj);

G_DEFINE_TYPE_WITH_CODE (MixVideoCaps, mix_videocaps, MIX_TYPE_PARAMS,
			 _do_init);

static void
mix_videocaps_init (MixVideoCaps * self)
{
  /* initialize properties here */
  self->mix_caps = NULL;
  self->video_hw_caps = NULL;

  self->reserved1 = NULL;
  self->reserved2 = NULL;
  self->reserved3 = NULL;
  self->reserved4 = NULL;

}

static void
mix_videocaps_class_init (MixVideoCapsClass * klass)
{
  MixParamsClass *mixparams_class = MIX_PARAMS_CLASS (klass);

  /* setup static parent class */
  parent_class = (MixParamsClass *) g_type_class_peek_parent (klass);

  mixparams_class->finalize = mix_videocaps_finalize;
  mixparams_class->copy = (MixParamsCopyFunction) mix_videocaps_copy;
  mixparams_class->dup = (MixParamsDupFunction) mix_videocaps_dup;
  mixparams_class->equal = (MixParamsEqualFunction) mix_videocaps_equal;
}

MixVideoCaps *
mix_videocaps_new (void)
{
  MixVideoCaps *ret =
    (MixVideoCaps *) g_type_create_instance (MIX_TYPE_VIDEOCAPS);
  return ret;
}

void
mix_videocaps_finalize (MixParams * obj)
{
  /* clean up here. */

  MixVideoCaps *self = MIX_VIDEOCAPS (obj);
  SAFE_FREE (self->mix_caps);
  SAFE_FREE (self->video_hw_caps);

  /* Chain up parent */
  if (parent_class->finalize)
    {
      parent_class->finalize (obj);
    }
}

MixVideoCaps *
mix_videocaps_ref (MixVideoCaps * mix)
{
  return (MixVideoCaps *) mix_params_ref (MIX_PARAMS (mix));
}

/**
* mix_videocaps_dup:
* @obj: a #MixVideoCaps object
* @returns: a newly allocated duplicate of the object.
* 
* Copy duplicate of the object.
*/
MixParams *
mix_videocaps_dup (const MixParams * obj)
{
  MixParams *ret = NULL;

  if (MIX_IS_VIDEOCAPS (obj))
    {
      MixVideoCaps *duplicate = mix_videocaps_new ();
      if (mix_videocaps_copy (MIX_PARAMS (duplicate), MIX_PARAMS (obj)))
	{
	  ret = MIX_PARAMS (duplicate);
	}
      else
	{
	  mix_videocaps_unref (duplicate);
	}
    }
  return ret;
}

/**
* mix_videocaps_copy:
* @target: copy to target
* @src: copy from src
* @returns: boolean indicates if copy is successful.
* 
* Copy instance data from @src to @target.
*/
gboolean
mix_videocaps_copy (MixParams * target, const MixParams * src)
{
  MixVideoCaps *this_target, *this_src;

  if (MIX_IS_VIDEOCAPS (target) && MIX_IS_VIDEOCAPS (src))
    {
      // Cast the base object to this child object
      this_target = MIX_VIDEOCAPS (target);
      this_src = MIX_VIDEOCAPS (src);

      // Free the existing properties
      SAFE_FREE (this_target->mix_caps);
      SAFE_FREE (this_target->video_hw_caps);

      // Duplicate string
      this_target->mix_caps = g_strdup (this_src->mix_caps);
      this_target->video_hw_caps = g_strdup (this_src->video_hw_caps);

      // Now chainup base class
      if (parent_class->copy)
	{
	  return parent_class->copy (MIX_PARAMS_CAST (target),
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
* mix_videocaps_:
* @first: first object to compare
* @second: seond object to compare
* @returns: boolean indicates if instance are equal.
* 
* Copy instance data from @src to @target.
*/
gboolean
mix_videocaps_equal (MixParams * first, MixParams * second)
{
  gboolean ret = FALSE;
  MixVideoCaps *this_first, *this_second;

  if (MIX_IS_VIDEOCAPS (first) && MIX_IS_VIDEOCAPS (second))
    {
      // Deep compare
      // Cast the base object to this child object

      this_first = MIX_VIDEOCAPS (first);
      this_second = MIX_VIDEOCAPS (second);

      /* TODO: add comparison for other properties */
      if (g_strcmp0 (this_first->mix_caps, this_second->mix_caps) == 0
	  && g_strcmp0 (this_first->video_hw_caps,
			this_second->video_hw_caps) == 0)
	{
	  // members within this scope equal. chaining up.
	  MixParamsClass *klass = MIX_PARAMS_CLASS (parent_class);
	  if (klass->equal)
	    ret = klass->equal (first, second);
	  else
	    ret = TRUE;
	}
    }

  return ret;
}

#define MIX_VIDEOCAPS_SETTER_CHECK_INPUT(obj) \
	if(!obj) return MIX_RESULT_NULL_PTR; \
	if(!MIX_IS_VIDEOCAPS(obj)) return MIX_RESULT_FAIL; \

#define MIX_VIDEOCAPS_GETTER_CHECK_INPUT(obj, prop) \
	if(!obj || !prop) return MIX_RESULT_NULL_PTR; \
	if(!MIX_IS_VIDEOCAPS(obj)) return MIX_RESULT_FAIL; \


/* TODO: Add getters and setters for other properties. The following is just an exmaple, not implemented yet. */
MIX_RESULT
mix_videocaps_set_mix_caps (MixVideoCaps * obj, gchar * mix_caps)
{
  MIX_VIDEOCAPS_SETTER_CHECK_INPUT (obj);

  SAFE_FREE (obj->mix_caps);
  obj->mix_caps = g_strdup (mix_caps);
  if (mix_caps != NULL && obj->mix_caps == NULL)
    {
      return MIX_RESULT_NO_MEMORY;
    }

  return MIX_RESULT_SUCCESS;
}

MIX_RESULT
mix_videocaps_get_mix_caps (MixVideoCaps * obj, gchar ** mix_caps)
{
  MIX_VIDEOCAPS_GETTER_CHECK_INPUT (obj, mix_caps);
  *mix_caps = g_strdup (obj->mix_caps);
  if (*mix_caps == NULL && obj->mix_caps)
    {
      return MIX_RESULT_NO_MEMORY;
    }
  return MIX_RESULT_SUCCESS;
}

MIX_RESULT
mix_videocaps_set_video_hw_caps (MixVideoCaps * obj, gchar * video_hw_caps)
{
  MIX_VIDEOCAPS_SETTER_CHECK_INPUT (obj);

  SAFE_FREE (obj->video_hw_caps);
  obj->video_hw_caps = g_strdup (video_hw_caps);
  if (video_hw_caps != NULL && obj->video_hw_caps == NULL)
    {
      return MIX_RESULT_NO_MEMORY;
    }

  return MIX_RESULT_SUCCESS;
}

MIX_RESULT
mix_videocaps_get_video_hw_caps (MixVideoCaps * obj, gchar ** video_hw_caps)
{
  MIX_VIDEOCAPS_GETTER_CHECK_INPUT (obj, video_hw_caps);

  *video_hw_caps = g_strdup (obj->video_hw_caps);
  if (*video_hw_caps == NULL && obj->video_hw_caps)
    {
      return MIX_RESULT_NO_MEMORY;
    }
  return MIX_RESULT_SUCCESS;
}
