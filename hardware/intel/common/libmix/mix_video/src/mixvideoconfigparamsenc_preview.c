/* 
INTEL CONFIDENTIAL
Copyright 2009 Intel Corporation All Rights Reserved. 
The source code contained or described herein and all documents related to the source code ("Material") are owned by Intel Corporation or its suppliers or licensors. Title to the Material remains with Intel Corporation or its suppliers and licensors. The Material contains trade secrets and proprietary and confidential information of Intel or its suppliers and licensors. The Material is protected by worldwide copyright and trade secret laws and treaty provisions. No part of the Material may be used, copied, reproduced, modified, published, uploaded, posted, transmitted, distributed, or disclosed in any way without Intel’s prior express written permission.

No license under any patent, copyright, trade secret or other intellectual property right is granted to or conferred upon you by disclosure or delivery of the Materials, either expressly, by implication, inducement, estoppel or otherwise. Any license under such intellectual property rights must be express and approved by Intel in writing.
*/

/**
* SECTION:mixvideoconfigparamsenc_preview
* @short_description: VideoConfig parameters
*
* A data object which stores videoconfig specific parameters.
*/

#include "mixvideolog.h"
#include "mixvideoconfigparamsenc_preview.h"

#define MDEBUG


static GType _mix_videoconfigparamsenc_preview_type = 0;
static MixVideoConfigParamsEncClass *parent_class = NULL;

#define _do_init { _mix_videoconfigparamsenc_preview_type = g_define_type_id; }

gboolean mix_videoconfigparamsenc_preview_copy (MixParams * target,
					  const MixParams * src);
MixParams *mix_videoconfigparamsenc_preview_dup (const MixParams * obj);
gboolean mix_videoconfigparamsencenc_preview_equal (MixParams * first,
					   MixParams * second);
static void mix_videoconfigparamsenc_preview_finalize (MixParams * obj);

G_DEFINE_TYPE_WITH_CODE (MixVideoConfigParamsEncPreview,	/* The name of the new type, in Camel case */
			 mix_videoconfigparamsenc_preview,	/* The name of the new type in lowercase */
			 MIX_TYPE_VIDEOCONFIGPARAMSENC,	/* The GType of the parent type */
			 _do_init);

void
_mix_videoconfigparamsenc_preview_initialize (void)
{
  /* the MixParams types need to be class_ref'd once before it can be
   * done from multiple threads;
   * see http://bugzilla.gnome.org/show_bug.cgi?id=304551 */
  g_type_class_ref (mix_videoconfigparamsenc_preview_get_type ());
}

static void
mix_videoconfigparamsenc_preview_init (MixVideoConfigParamsEncPreview * self)
{
  /* initialize properties here */
  /* TODO: initialize properties */

  self->reserved1 = NULL;
  self->reserved2 = NULL;
  self->reserved3 = NULL;
  self->reserved4 = NULL;
}

static void
mix_videoconfigparamsenc_preview_class_init (MixVideoConfigParamsEncPreviewClass * klass)
{
  MixVideoConfigParamsEncClass *this_parent_class =
    MIX_VIDEOCONFIGPARAMSENC_CLASS (klass);
  MixParamsClass *this_root_class = MIX_PARAMS_CLASS (this_parent_class);

  /* setup static parent class */
  parent_class =
    (MixVideoConfigParamsEncClass *) g_type_class_peek_parent (klass);

  this_root_class->finalize = mix_videoconfigparamsenc_preview_finalize;
  this_root_class->copy =
    (MixParamsCopyFunction) mix_videoconfigparamsenc_preview_copy;
  this_root_class->dup =
    (MixParamsDupFunction) mix_videoconfigparamsenc_preview_dup;
  this_root_class->equal =
    (MixParamsEqualFunction) mix_videoconfigparamsencenc_preview_equal;
}

MixVideoConfigParamsEncPreview *
mix_videoconfigparamsenc_preview_new (void)
{
  MixVideoConfigParamsEncPreview *ret = (MixVideoConfigParamsEncPreview *)
    g_type_create_instance (MIX_TYPE_VIDEOCONFIGPARAMSENC_PREVIEW);

  return ret;
}

void
mix_videoconfigparamsenc_preview_finalize (MixParams * obj)
{
  /* MixVideoConfigParamsEncPreview *this_obj = MIX_VIDEOCONFIGPARAMSENC_PREVIEW (obj); */
  MixParamsClass *root_class = MIX_PARAMS_CLASS (parent_class);

  /* TODO: cleanup resources allocated */

  /* Chain up parent */

  if (root_class->finalize)
    {
      root_class->finalize (obj);
    }
}

MixVideoConfigParamsEncPreview
  * mix_videoconfigparamsenc_preview_ref (MixVideoConfigParamsEncPreview * mix)
{
  return (MixVideoConfigParamsEncPreview *) mix_params_ref (MIX_PARAMS (mix));
}

/**
* mix_videoconfigparamsenc_preview_dup:
* @obj: a #MixVideoConfigParams object
* @returns: a newly allocated duplicate of the object.
* 
* Copy duplicate of the object.
*/
MixParams *
mix_videoconfigparamsenc_preview_dup (const MixParams * obj)
{
  MixParams *ret = NULL;

  if (MIX_IS_VIDEOCONFIGPARAMSENC_PREVIEW (obj))
    {
      MixVideoConfigParamsEncPreview *duplicate = mix_videoconfigparamsenc_preview_new ();
      if (mix_videoconfigparamsenc_preview_copy
	  (MIX_PARAMS (duplicate), MIX_PARAMS (obj)))
	{
	  ret = MIX_PARAMS (duplicate);
	}
      else
	{
	  mix_videoconfigparamsenc_preview_unref (duplicate);
	}
    }
  return ret;
}

/**
* mix_videoconfigparamsenc_preview_copy:
* @target: copy to target
* @src: copy from src
* @returns: boolean indicates if copy is successful.
* 
* Copy instance data from @src to @target.
*/
gboolean
mix_videoconfigparamsenc_preview_copy (MixParams * target, const MixParams * src)
{
    MixVideoConfigParamsEncPreview *this_target, *this_src;
    MixParamsClass *root_class;

    LOG_V( "Begin\n");	

    if (MIX_IS_VIDEOCONFIGPARAMSENC_PREVIEW (target)
      && MIX_IS_VIDEOCONFIGPARAMSENC_PREVIEW (src))
    {
      // Cast the base object to this child object
      this_target = MIX_VIDEOCONFIGPARAMSENC_PREVIEW (target);
      this_src = MIX_VIDEOCONFIGPARAMSENC_PREVIEW (src);

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
* mix_videoconfigparamsenc_preview:
* @first: first object to compare
* @second: seond object to compare
* @returns: boolean indicates if instance are equal.
* 
* Copy instance data from @src to @target.
*/
gboolean
mix_videoconfigparamsencenc_preview_equal (MixParams * first, MixParams * second)
{
  gboolean ret = FALSE;
  MixVideoConfigParamsEncPreview *this_first, *this_second;

  if (MIX_IS_VIDEOCONFIGPARAMSENC_PREVIEW (first)
      && MIX_IS_VIDEOCONFIGPARAMSENC_PREVIEW (second))
    {
      // Cast the base object to this child object

      this_first = MIX_VIDEOCONFIGPARAMSENC_PREVIEW (first);
      this_second = MIX_VIDEOCONFIGPARAMSENC_PREVIEW (second);
  

	ret = TRUE;


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
