/* 
INTEL CONFIDENTIAL
Copyright 2009 Intel Corporation All Rights Reserved. 
The source code contained or described herein and all documents related to the source code ("Material") are owned by Intel Corporation or its suppliers or licensors. Title to the Material remains with Intel Corporation or its suppliers and licensors. The Material contains trade secrets and proprietary and confidential information of Intel or its suppliers and licensors. The Material is protected by worldwide copyright and trade secret laws and treaty provisions. No part of the Material may be used, copied, reproduced, modified, published, uploaded, posted, transmitted, distributed, or disclosed in any way without Intel’s prior express written permission.

No license under any patent, copyright, trade secret or other intellectual property right is granted to or conferred upon you by disclosure or delivery of the Materials, either expressly, by implication, inducement, estoppel or otherwise. Any license under such intellectual property rights must be express and approved by Intel in writing.
*/

/**
* SECTION:mixdrmparams
* @short_description: Drm parameters
*
* A data object which stores drm specific parameters.
*/

#include "mixdrmparams.h"

static GType _mix_drmparams_type = 0;
static MixParamsClass *parent_class = NULL;

#define _do_init { _mix_drmparams_type = g_define_type_id; }

gboolean mix_drmparams_copy (MixParams * target, const MixParams * src);
MixParams *mix_drmparams_dup (const MixParams * obj);
gboolean mix_drmparams_equal (MixParams * first, MixParams * second);
static void mix_drmparams_finalize (MixParams * obj);

G_DEFINE_TYPE_WITH_CODE (MixDrmParams, mix_drmparams, MIX_TYPE_PARAMS,
			 _do_init);

static void
mix_drmparams_init (MixDrmParams * self)
{
  /* initialize properties here */

  /* TODO: initialize properties */
}

static void
mix_drmparams_class_init (MixDrmParamsClass * klass)
{
  MixParamsClass *mixparams_class = MIX_PARAMS_CLASS (klass);

  /* setup static parent class */
  parent_class = (MixParamsClass *) g_type_class_peek_parent (klass);

  mixparams_class->finalize = mix_drmparams_finalize;
  mixparams_class->copy = (MixParamsCopyFunction) mix_drmparams_copy;
  mixparams_class->dup = (MixParamsDupFunction) mix_drmparams_dup;
  mixparams_class->equal = (MixParamsEqualFunction) mix_drmparams_equal;
}

MixDrmParams *
mix_drmparams_new (void)
{
  MixDrmParams *ret =
    (MixDrmParams *) g_type_create_instance (MIX_TYPE_DRMPARAMS);

  return ret;
}

void
mix_drmparams_finalize (MixParams * obj)
{
  /* clean up here. */
  /* TODO: cleanup resources allocated */

  /* Chain up parent */
  if (parent_class->finalize)
    {
      parent_class->finalize (obj);
    }
}

MixDrmParams *
mix_drmparams_ref (MixDrmParams * mix)
{
  return (MixDrmParams *) mix_params_ref (MIX_PARAMS (mix));
}

/**
* mix_drmparams_dup:
* @obj: a #MixDrmParams object
* @returns: a newly allocated duplicate of the object.
* 
* Copy duplicate of the object.
*/
MixParams *
mix_drmparams_dup (const MixParams * obj)
{
  MixParams *ret = NULL;

  if (MIX_IS_DRMPARAMS (obj))
    {
      MixDrmParams *duplicate = mix_drmparams_new ();
      if (mix_drmparams_copy (MIX_PARAMS (duplicate), MIX_PARAMS (obj)))
	{
	  ret = MIX_PARAMS (duplicate);
	}
      else
	{
	  mix_drmparams_unref (duplicate);
	}
    }
  return ret;
}

/**
* mix_drmparams_copy:
* @target: copy to target
* @src: copy from src
* @returns: boolean indicates if copy is successful.
* 
* Copy instance data from @src to @target.
*/
gboolean
mix_drmparams_copy (MixParams * target, const MixParams * src)
{
  MixDrmParams *this_target, *this_src;

  if (MIX_IS_DRMPARAMS (target) && MIX_IS_DRMPARAMS (src))
    {
      // Cast the base object to this child object
      this_target = MIX_DRMPARAMS (target);
      this_src = MIX_DRMPARAMS (src);

      // TODO: copy properties */

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
* mix_drmparams_:
* @first: first object to compare
* @second: seond object to compare
* @returns: boolean indicates if instance are equal.
* 
* Copy instance data from @src to @target.
*/
gboolean
mix_drmparams_equal (MixParams * first, MixParams * second)
{
  gboolean ret = FALSE;
  MixDrmParams *this_first, *this_second;

  if (MIX_IS_DRMPARAMS (first) && MIX_IS_DRMPARAMS (second))
    {
      // Deep compare
      // Cast the base object to this child object

      this_first = MIX_DRMPARAMS (first);
      this_second = MIX_DRMPARAMS (second);

      /* TODO: add comparison for properties */
      /* if ( first properties ==  sencod properties) */
      {
	// members within this scope equal. chaining up.
	MixParamsClass *klass = MIX_PARAMS_CLASS (parent_class);
	if (klass->equal)
	  ret = parent_class->equal (first, second);
	else
	  ret = TRUE;
      }
    }

  return ret;
}

#define MIX_DRMPARAMS_SETTER_CHECK_INPUT(obj) \
	if(!obj) return MIX_RESULT_NULL_PTR; \
	if(!MIX_IS_DRMPARAMS(obj)) return MIX_RESULT_FAIL; \

#define MIX_DRMPARAMS_GETTER_CHECK_INPUT(obj, prop) \
	if(!obj || !prop) return MIX_RESULT_NULL_PTR; \
	if(!MIX_IS_DRMPARAMS(obj)) return MIX_RESULT_FAIL; \


/* TODO: Add getters and setters for properties. */
