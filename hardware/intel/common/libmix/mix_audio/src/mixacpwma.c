/* 
 INTEL CONFIDENTIAL
 Copyright 2009 Intel Corporation All Rights Reserved. 
 The source code contained or described herein and all documents related to the source code ("Material") are owned by Intel Corporation or its suppliers or licensors. Title to the Material remains with Intel Corporation or its suppliers and licensors. The Material contains trade secrets and proprietary and confidential information of Intel or its suppliers and licensors. The Material is protected by worldwide copyright and trade secret laws and treaty provisions. No part of the Material may be used, copied, reproduced, modified, published, uploaded, posted, transmitted, distributed, or disclosed in any way without Intel’s prior express written permission.

 No license under any patent, copyright, trade secret or other intellectual property right is granted to or conferred upon you by disclosure or delivery of the Materials, either expressly, by implication, inducement, estoppel or otherwise. Any license under such intellectual property rights must be express and approved by Intel in writing.
*/

/**
 * SECTION:mixacpwma
 * @short_description: Audio parameters for WMA audio.
 * @include: mixacpwma.h
 *
 * A data object which stores audio specific parameters for WMA.
 * 
 * In Moorestown, only WMA2 is supported.
 * 
 * Additional parameters must be set in the parent object #MixAudioConfigParams
 */

#include "mixacpwma.h"

static GType _mix_acp_wma_type = 0;
static MixAudioConfigParamsClass *parent_class = NULL;

#define _do_init { _mix_acp_wma_type = g_define_type_id; }

gboolean mix_acp_wma_copy(MixParams* target, const MixParams *src);
MixParams* mix_acp_wma_dup(const MixParams *obj);
gboolean mix_acp_wma_equal(MixParams* first, MixParams *second);
static void mix_acp_wma_finalize(MixParams *obj);

G_DEFINE_TYPE_WITH_CODE(MixAudioConfigParamsWMA, mix_acp_wma, MIX_TYPE_AUDIOCONFIGPARAMS, _do_init);

static void mix_acp_wma_init (MixAudioConfigParamsWMA *self)
{
  self->channel_mask = 0;
  self->format_tag = 0;
  self->block_align = 0;
  self->wma_encode_opt = 0;
  self->pcm_bit_width = 0;  /* source pcm bit width */
  self->wma_version = MIX_AUDIO_WMA_VUNKNOWN;
}

static void mix_acp_wma_class_init(MixAudioConfigParamsWMAClass *klass)
{
  MixParamsClass *mixparams_class = MIX_PARAMS_CLASS(klass);

  /* setup static parent class */
  parent_class = (MixAudioConfigParamsClass *) g_type_class_peek_parent (klass);

  mixparams_class->finalize = mix_acp_wma_finalize;
  mixparams_class->copy = (MixParamsCopyFunction)mix_acp_wma_copy;
  mixparams_class->dup = (MixParamsDupFunction)mix_acp_wma_dup;
  mixparams_class->equal = (MixParamsEqualFunction)mix_acp_wma_equal;
}

MixAudioConfigParamsWMA *mix_acp_wma_new(void)
{
  MixAudioConfigParamsWMA *ret = (MixAudioConfigParamsWMA *)g_type_create_instance (MIX_TYPE_AUDIOCONFIGPARAMSWMA);

  return ret;
}

void mix_acp_wma_finalize(MixParams *obj)
{
  /* clean up here. */
  
  /* Chain up parent */ 
  MixParamsClass *klass = MIX_PARAMS_CLASS(parent_class);
  if (klass->finalize) 
    klass->finalize(obj);
}

MixAudioConfigParamsWMA *mix_acp_wma_ref(MixAudioConfigParamsWMA *obj) 
{ 
  return (MixAudioConfigParamsWMA*)mix_params_ref(MIX_PARAMS(obj)); 
}

/**
 * mix_acp_wma_dup:
 * @obj: a #MixAudioConfigParamsWMA object
 * @returns: a newly allocated duplicate of the object.
 * 
 * Copy duplicate of the object.
 */
MixParams* mix_acp_wma_dup(const MixParams *obj)
{
  MixParams *ret = NULL;

  if (MIX_IS_AUDIOCONFIGPARAMSWMA(obj))
  {
    MixAudioConfigParamsWMA *duplicate = mix_acp_wma_new();
    if (mix_acp_wma_copy(MIX_PARAMS(duplicate), MIX_PARAMS(obj)))
    {
      ret = MIX_PARAMS(duplicate);
    }
    else
    {
      mix_acp_wma_unref(duplicate);
    }
  }

  return ret;
}

/**
 * mix_acp_wma_copy:
 * @target: copy to target
 * @src: copy from src
 * @returns: boolean indicates if copy is successful.
 * 
 * Copy instance data from @src to @target.
 */
gboolean mix_acp_wma_copy(MixParams* target, const MixParams *src)
{
  if (MIX_IS_AUDIOCONFIGPARAMSWMA(target) && MIX_IS_AUDIOCONFIGPARAMSWMA(src))
  {
    MixAudioConfigParamsWMA *t = MIX_AUDIOCONFIGPARAMSWMA(target);
    MixAudioConfigParamsWMA *s = MIX_AUDIOCONFIGPARAMSWMA(src);

    t->channel_mask = s->channel_mask;
    t->format_tag = s->format_tag;
    t->block_align = s->block_align;
    t->wma_encode_opt = s->wma_encode_opt;
    t->wma_version = s->wma_version;
    t->pcm_bit_width = s->pcm_bit_width;

    // Now chainup base class
    MixParamsClass *klass = MIX_PARAMS_CLASS(parent_class); 
    if (klass->copy)
    {
      return klass->copy(MIX_PARAMS_CAST(target), MIX_PARAMS_CAST(src));
    }
    else
      return TRUE;
  }
  return FALSE;
}

/**
 * mix_acp_wma_equal:
 * @first: first object to compare
 * @second: seond object to compare
 * @returns: boolean indicates if instance are equal.
 * 
 * Copy instance data from @src to @target.
 */
gboolean mix_acp_wma_equal(MixParams* first, MixParams *second)
{
  gboolean ret = FALSE;
  
  if (first && second)
  {
    if (first == second) return TRUE;
  }
  else
  {
    return FALSE;
  }

  MixParamsClass *klass = MIX_PARAMS_CLASS(parent_class);
  if (klass->equal)
    ret = klass->equal(first, second);
  else
    ret = TRUE;

  if (ret && MIX_IS_AUDIOCONFIGPARAMSWMA(first) && MIX_IS_AUDIOCONFIGPARAMSWMA(second))
  {
    MixAudioConfigParamsWMA *acp1 = MIX_AUDIOCONFIGPARAMSWMA(first);
    MixAudioConfigParamsWMA *acp2 = MIX_AUDIOCONFIGPARAMSWMA(second);

    ret = (acp1->channel_mask == acp2->channel_mask) &&
          (acp1->format_tag == acp2->format_tag) &&
          (acp1->block_align == acp2->block_align) &&
          (acp1->wma_encode_opt == acp2->wma_encode_opt) &&
          (acp1->pcm_bit_width == acp2->pcm_bit_width) &&
          (acp1->wma_version == acp2->wma_version);
  }

  return ret;
}

MixAudioWMAVersion mix_acp_wma_get_version(MixAudioConfigParamsWMA *obj)
{
  if (obj)
    return (obj->wma_version);
  else
    return MIX_AUDIO_WMA_VUNKNOWN;
}

MIX_RESULT mix_acp_wma_set_version(MixAudioConfigParamsWMA *obj, MixAudioWMAVersion ver)
{
  MIX_RESULT ret = MIX_RESULT_SUCCESS;

  if (!obj) return MIX_RESULT_NULL_PTR;

  if ((ver > MIX_AUDIO_WMA_VUNKNOWN) && (ver < MIX_AUDIO_WMA_LAST))
    obj->wma_version = ver;
  else 
    ret=MIX_RESULT_INVALID_PARAM;

  return ret;
}

