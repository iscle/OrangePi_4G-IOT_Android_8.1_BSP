/* 
 INTEL CONFIDENTIAL
 Copyright 2009 Intel Corporation All Rights Reserved. 
 The source code contained or described herein and all documents related to the source code ("Material") are owned by Intel Corporation or its suppliers or licensors. Title to the Material remains with Intel Corporation or its suppliers and licensors. The Material contains trade secrets and proprietary and confidential information of Intel or its suppliers and licensors. The Material is protected by worldwide copyright and trade secret laws and treaty provisions. No part of the Material may be used, copied, reproduced, modified, published, uploaded, posted, transmitted, distributed, or disclosed in any way without Intel’s prior express written permission.

 No license under any patent, copyright, trade secret or other intellectual property right is granted to or conferred upon you by disclosure or delivery of the Materials, either expressly, by implication, inducement, estoppel or otherwise. Any license under such intellectual property rights must be express and approved by Intel in writing.
*/

/**
 * SECTION:mixacpmp3
 * @short_description: Audio configuration parameters for MP3 audio.
 * @include: mixacpmp3.h
 *
 * A data object which stores audio specific parameters for MP3 audio.
 * 
 * Additional parameters must be set in the parent object #MixAudioConfigParams
 */

#include "mixacpmp3.h"

static GType _mix_acp_mp3_type = 0;
static MixAudioConfigParamsClass *parent_class = NULL;

#define _do_init { _mix_acp_mp3_type = g_define_type_id; }

gboolean mix_acp_mp3_copy(MixParams* target, const MixParams *src);
MixParams* mix_acp_mp3_dup(const MixParams *obj);
gboolean mix_acp_mp3_equal(MixParams* first, MixParams *second);
static void mix_acp_mp3_finalize(MixParams *obj);

G_DEFINE_TYPE_WITH_CODE(MixAudioConfigParamsMP3, mix_acp_mp3, MIX_TYPE_AUDIOCONFIGPARAMS, _do_init);

static void mix_acp_mp3_init (MixAudioConfigParamsMP3 *self)
{
  self->CRC=FALSE;
  self->MPEG_format=0;
  self->MPEG_layer=0;
}

static void mix_acp_mp3_class_init(MixAudioConfigParamsMP3Class *klass)
{
  MixParamsClass *mixparams_class = MIX_PARAMS_CLASS(klass);

  /* setup static parent class */
  parent_class = (MixAudioConfigParamsClass *) g_type_class_peek_parent (klass);

  mixparams_class->finalize = mix_acp_mp3_finalize;
  mixparams_class->copy = (MixParamsCopyFunction)mix_acp_mp3_copy;
  mixparams_class->dup = (MixParamsDupFunction)mix_acp_mp3_dup;
  mixparams_class->equal = (MixParamsEqualFunction)mix_acp_mp3_equal;
}

MixAudioConfigParamsMP3 *mix_acp_mp3_new(void)
{
  MixAudioConfigParamsMP3 *ret = (MixAudioConfigParamsMP3 *)g_type_create_instance (MIX_TYPE_AUDIOCONFIGPARAMSMP3);

  return ret;
}

void mix_acp_mp3_finalize(MixParams *obj)
{
  /* clean up here. */
  
  /* Chain up parent */ 
  MixParamsClass *klass = MIX_PARAMS_CLASS(parent_class);
  if (klass->finalize) 
    klass->finalize(obj);
}

MixAudioConfigParamsMP3 *mix_acp_mp3_ref(MixAudioConfigParamsMP3 *mix) 
{
  if (G_UNLIKELY(!mix)) return NULL;
  return (MixAudioConfigParamsMP3*)mix_params_ref(MIX_PARAMS(mix)); 
}

/**
 * mix_acp_mp3_dup:
 * @obj: a #MixAudioConfigParamsMP3 object
 * @returns: a newly allocated duplicate of the object.
 * 
 * Copy duplicate of the object.
 */
MixParams* mix_acp_mp3_dup(const MixParams *obj)
{
  MixParams *ret = NULL;

  if (MIX_IS_AUDIOCONFIGPARAMSMP3(obj))
  {
    MixAudioConfigParamsMP3 *duplicate = mix_acp_mp3_new();
    if (mix_acp_mp3_copy(MIX_PARAMS(duplicate), MIX_PARAMS(obj)))
    {
      ret = MIX_PARAMS(duplicate);
    }
    else
    {
      mix_acp_mp3_unref(duplicate);
    }
  }

  return ret;
}

/**
 * mix_acp_mp3_copy:
 * @target: copy to target
 * @src: copy from src
 * @returns: boolean indicates if copy is successful.
 * 
 * Copy instance data from @src to @target.
 */
gboolean mix_acp_mp3_copy(MixParams* target, const MixParams *src)
{
  if (MIX_IS_AUDIOCONFIGPARAMSMP3(target) && MIX_IS_AUDIOCONFIGPARAMSMP3(src))
  {
    MixAudioConfigParamsMP3 *t = MIX_AUDIOCONFIGPARAMSMP3(target);
    MixAudioConfigParamsMP3 *s = MIX_AUDIOCONFIGPARAMSMP3(src);

    t->CRC = s->CRC;
    t->MPEG_format = s->MPEG_format;
    t->MPEG_layer = s->MPEG_layer;

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
 * mix_acp_mp3_equal:
 * @first: first object to compare
 * @second: seond object to compare
 * @returns: boolean indicates if instance are equal.
 * 
 * Copy instance data from @src to @target.
 */
gboolean mix_acp_mp3_equal(MixParams* first, MixParams *second)
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

  // members within this scope equal. chaining up.
  MixParamsClass *klass = MIX_PARAMS_CLASS(parent_class);
  if (klass->equal)
    ret = klass->equal(first, second);
  else
    ret = TRUE;

  if (ret && MIX_IS_AUDIOCONFIGPARAMSMP3(first) && MIX_IS_AUDIOCONFIGPARAMSMP3(second))
  {
    MixAudioConfigParamsMP3 *acp1 = MIX_AUDIOCONFIGPARAMSMP3(first);
    MixAudioConfigParamsMP3 *acp2 = MIX_AUDIOCONFIGPARAMSMP3(second);

    ret = (acp1->CRC == acp2->CRC) &&
          (acp1->MPEG_format == acp2->MPEG_format) &&
          (acp1->MPEG_layer == acp2->MPEG_layer);
  }

  return ret;
}


