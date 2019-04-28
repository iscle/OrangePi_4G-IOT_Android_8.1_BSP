/* 
 INTEL CONFIDENTIAL
 Copyright 2009 Intel Corporation All Rights Reserved. 
 The source code contained or described herein and all documents related to the source code ("Material") are owned by Intel Corporation or its suppliers or licensors. Title to the Material remains with Intel Corporation or its suppliers and licensors. The Material contains trade secrets and proprietary and confidential information of Intel or its suppliers and licensors. The Material is protected by worldwide copyright and trade secret laws and treaty provisions. No part of the Material may be used, copied, reproduced, modified, published, uploaded, posted, transmitted, distributed, or disclosed in any way without Intel’s prior express written permission.

 No license under any patent, copyright, trade secret or other intellectual property right is granted to or conferred upon you by disclosure or delivery of the Materials, either expressly, by implication, inducement, estoppel or otherwise. Any license under such intellectual property rights must be express and approved by Intel in writing.
*/

/**
 * SECTION:mixacp
 * @short_description: MixAudio configuration parameters object.
 * @include: mixacp.h
 *
 * #MixAudio configuration parameters object which is used to communicate audio specific parameters.
 * 
 * This object is should not be instantiated as codec specific parameters are definied in individual derive classes.
 */

#include "mixacp.h"
#include <mixlog.h>

static GType _mix_acp_type = 0;
static MixParamsClass *parent_class = NULL;

#define _do_init { _mix_acp_type = g_define_type_id; }

gboolean mix_acp_copy(MixParams* target, const MixParams *src);
MixParams* mix_acp_dup(const MixParams *obj);
gboolean mix_acp_equal(MixParams* first, MixParams *second);
static void mix_acp_finalize(MixParams *obj);

G_DEFINE_TYPE_WITH_CODE(MixAudioConfigParams, mix_acp, MIX_TYPE_PARAMS, _do_init);

void
_mix_acp_initialize (void)
{
  /* the MixParams types need to be class_ref'd once before it can be
   * done from multiple threads;
   * see http://bugzilla.gnome.org/show_bug.cgi?id=304551 */
  g_type_class_ref (mix_acp_get_type ());
}

static void mix_acp_init (MixAudioConfigParams *self)
{
  self->decode_mode = MIX_DECODE_NULL;
  self->stream_name = NULL;
  self->audio_manager=MIX_AUDIOMANAGER_NONE;
  self->num_channels = 0;
  self->bit_rate = 0;
  self->sample_freq = 0;
  self->bits_per_sample = MIX_ACP_BPS_16;
  self->op_align = MIX_ACP_OUTPUT_ALIGN_16;
}

static void mix_acp_class_init(MixAudioConfigParamsClass *klass)
{
  MixParamsClass *mixparams_class = MIX_PARAMS_CLASS(klass);

  /* setup static parent class */
  parent_class = (MixParamsClass *) g_type_class_peek_parent (klass);

  mixparams_class->finalize = mix_acp_finalize;
  mixparams_class->copy = (MixParamsCopyFunction)mix_acp_copy;
  mixparams_class->dup = (MixParamsDupFunction)mix_acp_dup;
  mixparams_class->equal = (MixParamsEqualFunction)mix_acp_equal;

  klass->print_params = NULL;
}

MixAudioConfigParams *mix_acp_new(void)
{
  MixAudioConfigParams *ret = (MixAudioConfigParams *)g_type_create_instance (MIX_TYPE_AUDIOCONFIGPARAMS);

  return ret;
}

void mix_acp_finalize(MixParams *obj)
{
  /* clean up here. */
  MixAudioConfigParams *acp = MIX_AUDIOCONFIGPARAMS(obj);

  if (acp->stream_name) {
    g_free(acp->stream_name);
    acp->stream_name = NULL;
  }
  
  /* Chain up parent */ 
  if (parent_class->finalize) 
    parent_class->finalize(obj);
}

MixAudioConfigParams *mix_acp_ref(MixAudioConfigParams *mix) 
{ 
  return (MixAudioConfigParams*)mix_params_ref(MIX_PARAMS(mix)); 
}

/**
 * mix_acp_dup:
 * @obj: a #MixAudioConfigParams object
 * @returns: a newly allocated duplicate of the object.
 * 
 * Copy duplicate of the object.
 */
MixParams* mix_acp_dup(const MixParams *obj)
{
  MixParams *ret = NULL;

  if (MIX_IS_AUDIOCONFIGPARAMS(obj))
  {
    MixAudioConfigParams *duplicate = mix_acp_new();
    if (mix_acp_copy(MIX_PARAMS(duplicate), MIX_PARAMS(obj)))
    {
      ret = MIX_PARAMS(duplicate);
    }
    else
    {
      mix_acp_unref(duplicate);
    }
  }

  return ret;
}

/**
 * mix_acp_copy:
 * @target: copy to target
 * @src: copy from src
 * @returns: boolean indicates if copy is successful.
 * 
 * Copy instance data from @src to @target.
 */
gboolean mix_acp_copy(MixParams* target, const MixParams *src)
{
  if (MIX_IS_AUDIOCONFIGPARAMS(target) && MIX_IS_AUDIOCONFIGPARAMS(src))
  {
    MixAudioConfigParams *t = MIX_AUDIOCONFIGPARAMS(target);
    MixAudioConfigParams *s = MIX_AUDIOCONFIGPARAMS(src);

    t->decode_mode = s->decode_mode;
    t->stream_name = g_strdup(s->stream_name);
    t->audio_manager=s->audio_manager;
    t->num_channels = s->num_channels;
    t->bit_rate = s->bit_rate;
    t->sample_freq = s->sample_freq;
    t->bits_per_sample = s->bits_per_sample;
    t->op_align = s->op_align;

    // Now chainup base class
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
 * mix_acp_equal:
 * @first: first object to compare
 * @second: seond object to compare
 * @returns: boolean indicates if instance are equal.
 * 
 * Copy instance data from @src to @target.
 */
gboolean mix_acp_equal(MixParams* first, MixParams *second)
{
  gboolean ret = FALSE;
  
  if (first && second)
  {
    if (first == second) return TRUE;
  }
  else
  {
    // one of them is NULL.
    return FALSE;
  }

  // members within this scope equal. chaining up.
  MixParamsClass *klass = MIX_PARAMS_CLASS(parent_class);
  if (klass->equal)
    ret = parent_class->equal(first, second);
  else
    ret = TRUE;

  if (ret && MIX_IS_AUDIOCONFIGPARAMS(first) && MIX_IS_AUDIOCONFIGPARAMS(second))
  {
    MixAudioConfigParams *acp1 = MIX_AUDIOCONFIGPARAMS(first);
    MixAudioConfigParams *acp2 = MIX_AUDIOCONFIGPARAMS(second);

    ret = (acp1->decode_mode == acp2->decode_mode) &&
            (acp1->audio_manager == acp2->audio_manager) &&
            (acp1->num_channels == acp2->num_channels) &&
            (acp1->bit_rate == acp2->bit_rate) &&
            (acp1->sample_freq == acp2->sample_freq) &&
            (acp1->bits_per_sample == acp2->bits_per_sample) &&
            (acp1->op_align == acp2->op_align) && 
            (!g_strcmp0(acp1->stream_name, acp2->stream_name));
            //g_strcmp0 handles NULL gracefully
  }

  return ret;
}


gboolean mix_acp_is_streamname_valid(MixAudioConfigParams *obj)
{
  if (MIX_IS_AUDIOCONFIGPARAMS(obj))
    if ((obj->stream_name) && (obj->stream_name[0] != 0)) return TRUE;

  return FALSE;
}

gchar *mix_acp_get_streamname(MixAudioConfigParams *obj)
{
  gchar *ret = NULL;
  if (G_LIKELY(MIX_IS_AUDIOCONFIGPARAMS(obj)) && obj->stream_name)
  {
    ret = g_strdup(obj->stream_name);
  }
  return ret;
}

MIX_RESULT mix_acp_set_streamname(MixAudioConfigParams *obj, const gchar *streamname)
{
  MIX_RESULT ret = MIX_RESULT_FAIL;

  if (!obj) return MIX_RESULT_NULL_PTR;

  if (G_LIKELY(MIX_IS_AUDIOCONFIGPARAMS(obj)))
  {
    if (obj->stream_name)
    {
      g_free(obj->stream_name);
      obj->stream_name = NULL;
    }

    if (streamname) obj->stream_name = g_strdup(streamname);

    ret = MIX_RESULT_SUCCESS;
  }
  else
  {
    ret = MIX_RESULT_INVALID_PARAM;
  }  

  return ret;
}

MixACPBPSType mix_acp_get_bps(MixAudioConfigParams *obj)
{
  if (G_LIKELY(obj))
    return obj->bits_per_sample;
  else
    return 0;
}

MIX_RESULT mix_acp_set_bps(MixAudioConfigParams *obj, MixACPBPSType type)
{
  MIX_RESULT ret = MIX_RESULT_SUCCESS;
  
  if (!obj) return MIX_RESULT_NULL_PTR;
  
  if (G_LIKELY(MIX_IS_AUDIOCONFIGPARAMS(obj)))
  {
    switch (type)
    {
      case MIX_ACP_BPS_UNKNOWN:
      case MIX_ACP_BPS_16:
      case MIX_ACP_BPS_24:
        obj->bits_per_sample = type;
        break;
      default:
        ret = MIX_RESULT_INVALID_PARAM;
        break;
    }
  }
  else
  {
    ret = MIX_RESULT_INVALID_PARAM;
  }

  return ret;
}


MixACPOpAlign mix_acp_get_op_align(MixAudioConfigParams *obj)
{
  return (obj->op_align);
}

MIX_RESULT mix_acp_set_op_align(MixAudioConfigParams *obj, MixACPOpAlign op_align)
{
  MIX_RESULT ret = MIX_RESULT_SUCCESS;

  if ((op_align >= MIX_ACP_OUTPUT_ALIGN_16) && (op_align < MIX_ACP_OUTPUT_ALIGN_LAST))
    obj->op_align = op_align;
  else ret=MIX_RESULT_INVALID_PARAM;

  return ret;
}

void mix_acp_print_params(MixAudioConfigParams *obj)
{
    mix_log(MIX_AUDIO_COMP, MIX_LOG_LEVEL_INFO, "decode_mode: %d\n", obj->decode_mode);
    mix_log(MIX_AUDIO_COMP, MIX_LOG_LEVEL_INFO, "stream_name: %s\n", obj->stream_name);
    mix_log(MIX_AUDIO_COMP, MIX_LOG_LEVEL_INFO, "audio_manager: %d\n", obj->audio_manager);
    mix_log(MIX_AUDIO_COMP, MIX_LOG_LEVEL_INFO, "num_channels: %d\n", obj->num_channels);
    mix_log(MIX_AUDIO_COMP, MIX_LOG_LEVEL_INFO, "bit_rate: %d\n", obj->bit_rate);
    mix_log(MIX_AUDIO_COMP, MIX_LOG_LEVEL_INFO, "sample_freq: %d\n", obj->sample_freq);
    mix_log(MIX_AUDIO_COMP, MIX_LOG_LEVEL_INFO, "bits_per_sample: %d\n", obj->bits_per_sample);
    mix_log(MIX_AUDIO_COMP, MIX_LOG_LEVEL_INFO, "op_align: %d\n", obj->op_align);

    MixAudioConfigParamsClass *klass = MIX_AUDIOCONFIGPARAMS_GET_CLASS(obj);
    if (klass->print_params)
    {
      klass->print_params(obj);
    } 
}

