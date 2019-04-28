/* 
 INTEL CONFIDENTIAL
 Copyright 2009 Intel Corporation All Rights Reserved. 
 The source code contained or described herein and all documents related to the source code ("Material") are owned by Intel Corporation or its suppliers or licensors. Title to the Material remains with Intel Corporation or its suppliers and licensors. The Material contains trade secrets and proprietary and confidential information of Intel or its suppliers and licensors. The Material is protected by worldwide copyright and trade secret laws and treaty provisions. No part of the Material may be used, copied, reproduced, modified, published, uploaded, posted, transmitted, distributed, or disclosed in any way without Intel’s prior express written permission.

 No license under any patent, copyright, trade secret or other intellectual property right is granted to or conferred upon you by disclosure or delivery of the Materials, either expressly, by implication, inducement, estoppel or otherwise. Any license under such intellectual property rights must be express and approved by Intel in writing.
*/

/**
 * SECTION:mixaip
 * @short_description: Initialization parameters object.
 * @include: mixacp.h
 *
 * A data object which stores initialization specific parameters.
 * 
 * Not Implemented in Moorestown.
 */

#include "mixaip.h"

//static GType _mix_aip_type = 0;
static MixParamsClass *parent_class = NULL;

// #define _do_init { _mix_aip_type = g_define_type_id; };
#define _do_init 

gboolean mix_aip_copy(MixParams* target, const MixParams *src);
MixParams* mix_aip_dup(const MixParams *obj);
gboolean mix_aip_equal(MixParams* first, MixParams *second);
static void mix_aip_finalize(MixParams *obj);

G_DEFINE_TYPE_WITH_CODE(MixAudioInitParams, mix_aip, MIX_TYPE_PARAMS, _do_init );

#if 0
void _mix_aip_initialize (void)
{
  /* the MixParams types need to be class_ref'd once before it can be
   * done from multiple threads;
   * see http://bugzilla.gnome.org/show_bug.cgi?id=304551 */
  g_type_class_ref (mix_aip_get_type ());
}
#endif

static void mix_aip_init (MixAudioInitParams *self)
{
  self->reserved1 = self->reserved2 = self->reserved3 = self->reserved4 = NULL;
}

static void mix_aip_class_init(MixAudioInitParamsClass *klass)
{
  MixParamsClass *mixparams_class = MIX_PARAMS_CLASS(klass);

  /* setup static parent class */
  parent_class = (MixParamsClass *) g_type_class_peek_parent (klass);

  mixparams_class->finalize = mix_aip_finalize;
  mixparams_class->copy = (MixParamsCopyFunction)mix_aip_copy;
  mixparams_class->dup = (MixParamsDupFunction)mix_aip_dup;
  mixparams_class->equal = (MixParamsEqualFunction)mix_aip_equal;
}

MixAudioInitParams *mix_aip_new(void)
{
  MixAudioInitParams *ret = (MixAudioInitParams *)g_type_create_instance (MIX_TYPE_AUDIOINITPARAMS);

  return ret;
}

void mix_aip_finalize(MixParams *obj)
{
  /* clean up here. */
  
  /* Chain up parent */ 
  if (parent_class->finalize) 
    parent_class->finalize(obj);
}

MixAudioInitParams *mix_aip_ref(MixAudioInitParams *mix) 
{ 
  return (MixAudioInitParams*)mix_params_ref(MIX_PARAMS(mix)); 
}

/**
 * mix_aip_dup:
 * @obj: a #MixAudioInitParams object
 * @returns: a newly allocated duplicate of the object.
 * 
 * Copy duplicate of the object.
 */
MixParams* mix_aip_dup(const MixParams *obj)
{
  MixParams *ret = NULL;

  if (MIX_IS_AUDIOINITPARAMS(obj))
  {
    MixAudioInitParams *duplicate = mix_aip_new();
    if (mix_aip_copy(MIX_PARAMS(duplicate), MIX_PARAMS(obj)))
    {
      ret = MIX_PARAMS(duplicate);
    }
    else
    {
      mix_aip_unref(duplicate);
    }
  }

  return ret;
}

/**
 * mix_aip_copy:
 * @target: copy to target
 * @src: copy from src
 * @returns: boolean indicates if copy is successful.
 * 
 * Copy instance data from @src to @target.
 */
gboolean mix_aip_copy(MixParams* target, const MixParams *src)
{
  if (MIX_IS_AUDIOINITPARAMS(target) && MIX_IS_AUDIOINITPARAMS(src))
  {
    // TODO perform copy.
    // 
    // Now chainup base class
    // Get the root class from the cached parent_class object. This cached parent_class object has not be overwritten by this current class.
    // Using the cached parent_class object because this_class would have ->copy pointing to this method!
    // Cached parent_class contains the class object before it is overwritten by this derive class.
    // MixParamsClass *klass = MIX_PARAMS_CLASS(parent_class); 
    if (parent_class->copy)
    {
      return parent_class->copy(MIX_PARAMS_CAST(target), MIX_PARAMS_CAST(src));
    }
    else
      return TRUE;
  }
  return FALSE;
}

/**
 * mix_aip_equal:
 * @first: first object to compare
 * @second: seond object to compare
 * @returns: boolean indicates if instance are equal.
 * 
 * Copy instance data from @src to @target.
 */
gboolean mix_aip_equal(MixParams* first, MixParams *second)
{
  gboolean ret = FALSE;

  if (MIX_IS_AUDIOINITPARAMS(first) && MIX_IS_AUDIOINITPARAMS(second))
  {
    // TODO: do deep compare

    if (ret)
    {
      // members within this scope equal. chaining up.
      MixParamsClass *klass = MIX_PARAMS_CLASS(parent_class);
      if (klass->equal)
        ret = parent_class->equal(first, second);
      else
        ret = TRUE;
    }
  }

  return ret;
}
