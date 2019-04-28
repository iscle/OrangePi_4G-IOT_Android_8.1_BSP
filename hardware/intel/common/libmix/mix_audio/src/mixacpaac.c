/* 
 INTEL CONFIDENTIAL
 Copyright 2009 Intel Corporation All Rights Reserved. 
 The source code contained or described herein and all documents related to the source code ("Material") are owned by Intel Corporation or its suppliers or licensors. Title to the Material remains with Intel Corporation or its suppliers and licensors. The Material contains trade secrets and proprietary and confidential information of Intel or its suppliers and licensors. The Material is protected by worldwide copyright and trade secret laws and treaty provisions. No part of the Material may be used, copied, reproduced, modified, published, uploaded, posted, transmitted, distributed, or disclosed in any way without Intel’s prior express written permission.

 No license under any patent, copyright, trade secret or other intellectual property right is granted to or conferred upon you by disclosure or delivery of the Materials, either expressly, by implication, inducement, estoppel or otherwise. Any license under such intellectual property rights must be express and approved by Intel in writing.
*/

/**
 * SECTION:mixacpaac
 * @short_description: Audio configuration parameters for AAC-LC, HEAAC v1, and HEAAC v2 audio format.
 * @include: mixacpaac.h
 *
 * A data object which stores audio specific parameters for the following formats:
 * <itemizedlist>
 * <listitem>AAC-LC</listitem>
 * <listitem>HE-AAC v1</listitem>
 * <listitem>HE-AAC v2</listitem>
 * </itemizedlist>
 * 
 * Additional parameters must be set in the parent object #MixAudioConfigParams
 */

#include "mixacpaac.h"
#include <string.h>
#include <mixlog.h>

static GType _mix_acp_aac_type = 0;
static MixAudioConfigParamsClass *parent_class = NULL;

#define _do_init { _mix_acp_aac_type = g_define_type_id; }

gboolean mix_acp_aac_copy(MixParams* target, const MixParams *src);
MixParams* mix_acp_aac_dup(const MixParams *obj);
gboolean mix_acp_aac_equal(MixParams* first, MixParams *second);
static void mix_acp_aac_finalize(MixParams *obj);

void mix_aac_print_params(MixAudioConfigParams *obj);

G_DEFINE_TYPE_WITH_CODE(MixAudioConfigParamsAAC, mix_acp_aac, MIX_TYPE_AUDIOCONFIGPARAMS, _do_init);

static void mix_acp_aac_init (MixAudioConfigParamsAAC *self)
{
  self->MPEG_id = MIX_AAC_MPEG_ID_NULL;
  self->bit_stream_format= MIX_AAC_BS_NULL;
  self->aac_profile=MIX_AAC_PROFILE_NULL;
  self->aot=0;
  self->bit_rate_type=MIX_AAC_BR_NULL; /* 0=CBR, 1=VBR */
  self->CRC=FALSE;
  self->sbrPresentFlag = -1;
  self->psPresentFlag = -1;
  self->pce_present=FALSE; /* Flag. 1- present 0 - not present, for RAW */
  self->syntc_id[0] = self->syntc_id[1] = 0; /* 0 for ID_SCE(Dula Mono), -1 for raw */
  self->syntc_tag[0] = self->syntc_tag[1] = 0; /* raw - -1 and 0 -16 for rest of the streams */
  self->num_syntc_elems = 0;
  self->aac_sample_rate = 0;
  self->aac_channels = 0;
}

static void mix_acp_aac_class_init(MixAudioConfigParamsAACClass *klass)
{
  MixParamsClass *mixparams_class = MIX_PARAMS_CLASS(klass);

  /* setup static parent class */
  parent_class = (MixAudioConfigParamsClass *) g_type_class_peek_parent (klass);

  mixparams_class->finalize = mix_acp_aac_finalize;
  mixparams_class->copy = (MixParamsCopyFunction)mix_acp_aac_copy;
  mixparams_class->dup = (MixParamsDupFunction)mix_acp_aac_dup;
  mixparams_class->equal = (MixParamsEqualFunction)mix_acp_aac_equal;

//  MixAudioConfigParamsClass *acp = MIX_AUDIOCONFIGPARAMS_GET_CLASS(klass);
  MixAudioConfigParamsClass *acp = (MixAudioConfigParamsClass *)klass;
  acp->print_params = mix_aac_print_params;
}

MixAudioConfigParamsAAC *mix_acp_aac_new(void)
{
  MixAudioConfigParamsAAC *ret = (MixAudioConfigParamsAAC *)g_type_create_instance (MIX_TYPE_AUDIOCONFIGPARAMSAAC);

  return ret;
}

void mix_acp_aac_finalize(MixParams *obj)
{
  /* clean up here. */
  
  /* Chain up parent */ 
  MixParamsClass *klass = MIX_PARAMS_CLASS(parent_class);
  if (klass->finalize) 
    klass->finalize(obj);
}

MixAudioConfigParamsAAC *mix_acp_aac_ref(MixAudioConfigParamsAAC *mix) 
{ 
  return (MixAudioConfigParamsAAC*)mix_params_ref(MIX_PARAMS(mix)); 
}

/**
 * mix_acp_aac_dup:
 * @obj: a #MixAudioConfigParamsAAC object
 * @returns: a newly allocated duplicate of the object.
 * 
 * Copy duplicate of the object.
 */
MixParams* mix_acp_aac_dup(const MixParams *obj)
{
  MixParams *ret = NULL;
  
  if (MIX_IS_AUDIOCONFIGPARAMSAAC(obj))
  {
    MixAudioConfigParamsAAC *duplicate = mix_acp_aac_new();
    if (mix_acp_aac_copy(MIX_PARAMS(duplicate), MIX_PARAMS(obj)))
    {
      ret = MIX_PARAMS(duplicate);
    }
    else
    {
      mix_acp_aac_unref(duplicate);
    }
  }

  return ret;
}

/**
 * mix_acp_aac_copy:
 * @target: copy to target
 * @src: copy from src
 * @returns: boolean indicates if copy is successful.
 * 
 * Copy instance data from @src to @target.
 */
gboolean mix_acp_aac_copy(MixParams* target, const MixParams *src)
{
  if (MIX_IS_AUDIOCONFIGPARAMSAAC(target) && MIX_IS_AUDIOCONFIGPARAMSAAC(src))
  {
    MixAudioConfigParamsAAC *t = MIX_AUDIOCONFIGPARAMSAAC(target);
    MixAudioConfigParamsAAC *s = MIX_AUDIOCONFIGPARAMSAAC(src);

    t->MPEG_id = s->MPEG_id;
    t->bit_stream_format = s->bit_stream_format;
    t->aac_profile = s->aac_profile;
    t->aot = s->aot;
    t->bit_rate_type = s->bit_rate_type;
    t->CRC = s->CRC;

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
 * mix_acp_aac_equal:
 * @first: first object to compare
 * @second: seond object to compare
 * @returns: boolean indicates if instance are equal.
 * 
 * Copy instance data from @src to @target.
 */
gboolean mix_acp_aac_equal(MixParams* first, MixParams *second)
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

  if (ret && MIX_IS_AUDIOCONFIGPARAMSAAC(first) && MIX_IS_AUDIOCONFIGPARAMSAAC(second))
  {

    MixAudioConfigParamsAAC *acp1 = MIX_AUDIOCONFIGPARAMSAAC(first);
    MixAudioConfigParamsAAC *acp2 = MIX_AUDIOCONFIGPARAMSAAC(second);
  
    ret = (acp1->MPEG_id == acp2->MPEG_id) &&
          (acp1->bit_stream_format && acp2->bit_stream_format) &&
          (acp1->aac_profile == acp2->aac_profile) &&
          (acp1->aot == acp2->aot) &&
          (acp1->bit_rate_type == acp2->bit_rate_type) &&
          (acp1->CRC == acp2->CRC) &&
          (acp1->sbrPresentFlag == acp2->sbrPresentFlag) &&
          (acp1->psPresentFlag == acp2->psPresentFlag) &&
          (acp1->pce_present == acp2->pce_present) &&
          (acp1->syntc_id[0] == acp2->syntc_id[0]) &&
          (acp1->syntc_id[1] == acp2->syntc_id[1]) &&
          (acp1->syntc_tag[0] == acp2->syntc_tag[0]) &&
          (acp1->syntc_tag[1] == acp2->syntc_tag[1]);
  }

  return ret;
}

MIX_RESULT mix_acp_aac_set_bit_stream_format(MixAudioConfigParamsAAC *obj, MixAACBitstreamFormt bit_stream_format)
{
  MIX_RESULT ret = MIX_RESULT_SUCCESS;

  if (!obj) return MIX_RESULT_NULL_PTR;
  
  if (bit_stream_format < MIX_AAC_BS_ADTS && bit_stream_format >= MIX_AAC_BS_LAST)
  {
    ret = MIX_RESULT_INVALID_PARAM;
  }
  else
  {
    obj->bit_stream_format = bit_stream_format;
  }
  
  return ret;
}
MixAACBitstreamFormt mix_acp_aac_get_bit_stream_format(MixAudioConfigParamsAAC *obj)
{
  if (obj)
    return obj->bit_stream_format;
  else
    return MIX_AAC_BS_NULL;
}

MIX_RESULT mix_acp_aac_set_aac_profile(MixAudioConfigParamsAAC *obj, MixAACProfile aac_profile)
{
  MIX_RESULT ret = MIX_RESULT_SUCCESS;
  
  if (!obj) return MIX_RESULT_NULL_PTR;
  
  if (aac_profile < MIX_AAC_PROFILE_MAIN || aac_profile >= MIX_AAC_PROFILE_LAST)
  {
    ret = MIX_RESULT_INVALID_PARAM;
  }
  else
  {
    obj->aac_profile = aac_profile;
  }
  
  return ret;
}
MixAACProfile mix_acp_aac_get_aac_profile(MixAudioConfigParamsAAC *obj)
{
  if (obj)
    return obj->aac_profile;
  else
    return MIX_AAC_PROFILE_NULL;
}

MIX_RESULT mix_acp_aac_set_bit_rate_type(MixAudioConfigParamsAAC *obj, MixAACBitrateType bit_rate_type)
{
  MIX_RESULT ret = MIX_RESULT_SUCCESS;
  
  if (!obj) return MIX_RESULT_NULL_PTR;
  
  if (bit_rate_type != MIX_AAC_BR_CONSTANT && bit_rate_type != MIX_AAC_BR_VARIABLE)
  {
    ret = MIX_RESULT_INVALID_PARAM;
  }
  else
  {
    obj->bit_rate_type = bit_rate_type;
  }
  
  return ret;
}
MixAACBitrateType mix_acp_aac_get_bit_rate_type(MixAudioConfigParamsAAC *obj)
{
  if (obj)
    return obj->bit_rate_type;
  else
    return MIX_AAC_BR_NULL;
}

void mix_aac_print_params(MixAudioConfigParams *obj)
{
    MixAudioConfigParamsAAC *t = MIX_AUDIOCONFIGPARAMSAAC(obj);
    mix_log(MIX_AUDIO_COMP, MIX_LOG_LEVEL_INFO, "Mpeg ID: %d\n", t->MPEG_id);
    mix_log(MIX_AUDIO_COMP, MIX_LOG_LEVEL_INFO, "bit_stream_format: %d\n", t->bit_stream_format);
    mix_log(MIX_AUDIO_COMP, MIX_LOG_LEVEL_INFO, "aac_profile: %d\n", t->aac_profile);
    mix_log(MIX_AUDIO_COMP, MIX_LOG_LEVEL_INFO, "aot: %d\n", t->aot);
    mix_log(MIX_AUDIO_COMP, MIX_LOG_LEVEL_INFO, "bit_rate_type: %d\n", t->bit_rate_type);
    mix_log(MIX_AUDIO_COMP, MIX_LOG_LEVEL_INFO, "CRC: %d\n", t->CRC);
    mix_log(MIX_AUDIO_COMP, MIX_LOG_LEVEL_INFO, " \n");
}


MIX_RESULT mix_acp_aac_set_aot(MixAudioConfigParamsAAC *obj, guint aot)
{
    if (!obj) return MIX_RESULT_NULL_PTR;
    
    if (MIX_IS_AUDIOCONFIGPARAMSAAC(obj))
    {
        if ((aot == 2) || (aot == 5))
        {
            obj->aot=aot;
            return MIX_RESULT_SUCCESS;
        }
        else
        {
            return MIX_RESULT_NOT_SUPPORTED;
        }
    }
    else
    {
        return MIX_RESULT_INVALID_PARAM;
    }
}

guint mix_acp_aac_get_aot(MixAudioConfigParamsAAC *obj)
{
    if (MIX_IS_AUDIOCONFIGPARAMSAAC(obj))
        return obj->aot;
    else
        return 0;
}


MIX_RESULT mix_acp_aac_set_mpeg_id(MixAudioConfigParamsAAC *obj, MixAACMpegID mpegid)
{
    if (!obj) return MIX_RESULT_NULL_PTR;
    
    if (MIX_IS_AUDIOCONFIGPARAMSAAC(obj))
    {
        if ((mpegid >= MIX_AAC_MPEG_ID_NULL) || (mpegid < MIX_AAC_MPEG_LAST))
        {
            obj->MPEG_id=mpegid;
            return MIX_RESULT_SUCCESS;
        }
        else
        {
            return MIX_RESULT_NOT_SUPPORTED;
        }
    }
    else
    {
        return MIX_RESULT_INVALID_PARAM;
    }
}

MixAACMpegID mix_acp_aac_get_mpeg_id(MixAudioConfigParamsAAC *obj)
{
    if (MIX_IS_AUDIOCONFIGPARAMSAAC(obj))
        return obj->MPEG_id;
    else
        return MIX_AAC_MPEG_ID_NULL;
}

