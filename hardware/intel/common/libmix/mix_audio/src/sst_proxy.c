/* 
 INTEL CONFIDENTIAL
 Copyright 2009 Intel Corporation All Rights Reserved. 
 The source code contained or described herein and all documents related to the source code ("Material") are owned by Intel Corporation or its suppliers or licensors. Title to the Material remains with Intel Corporation or its suppliers and licensors. The Material contains trade secrets and proprietary and confidential information of Intel or its suppliers and licensors. The Material is protected by worldwide copyright and trade secret laws and treaty provisions. No part of the Material may be used, copied, reproduced, modified, published, uploaded, posted, transmitted, distributed, or disclosed in any way without Intel’s prior express written permission.

 No license under any patent, copyright, trade secret or other intellectual property right is granted to or conferred upon you by disclosure or delivery of the Materials, either expressly, by implication, inducement, estoppel or otherwise. Any license under such intellectual property rights must be express and approved by Intel in writing.
*/


#include <glib.h>
#include <glib/gprintf.h>
#include <linux/types.h>
#include "mixacpmp3.h"
#include "mixacpwma.h"
#include "mixacpaac.h"
#include "intel_sst_ioctl.h"
#include "mixacp.h"
#include "sst_proxy.h"

#ifdef G_LOG_DOMAIN
#undef G_LOG_DOMAIN
#define G_LOG_DOMAIN    ((gchar*)"mixaudio")
#endif

gboolean mix_sst_params_convert_mp3(MixAudioConfigParamsMP3 *acp, struct snd_sst_params *s);
gboolean mix_sst_params_convert_wma(MixAudioConfigParamsWMA *acp, struct snd_sst_params *s);
gboolean mix_sst_params_convert_aac(MixAudioConfigParamsAAC *acp, struct snd_sst_params *s);
void mix_sst_params_to_mp3(MixAudioConfigParamsMP3 *acp, struct snd_mp3_params *params);
void mix_sst_params_to_wma(MixAudioConfigParamsWMA *acp, struct snd_wma_params *params);
void mix_sst_params_to_aac(MixAudioConfigParamsAAC *acp, struct snd_aac_params *params);
void mix_sst_set_bps(MixAudioConfigParams *acp, guchar pcm_wd_sz);
void mix_sst_set_op_align(MixAudioConfigParams *acp, guchar op_align);

/* 
 * Utilities that convert param object to driver struct.
 * No Mix Context needed. However, it knows about the driver's param structure.
 */
gboolean mix_sst_params_convert(MixAudioConfigParams *acp, struct snd_sst_params *s)
{
  gboolean ret = FALSE;

  if (!s) return FALSE;

  if (MIX_IS_AUDIOCONFIGPARAMSMP3(acp))
    ret = mix_sst_params_convert_mp3(MIX_AUDIOCONFIGPARAMSMP3(acp), s);
  else if (MIX_IS_AUDIOCONFIGPARAMSWMA(acp))
    ret = mix_sst_params_convert_wma(MIX_AUDIOCONFIGPARAMSWMA(acp), s);
  else if (MIX_IS_AUDIOCONFIGPARAMSAAC(acp))
    ret = mix_sst_params_convert_aac(MIX_AUDIOCONFIGPARAMSAAC(acp), s);

  return ret;
}


gboolean mix_sst_params_convert_mp3(MixAudioConfigParamsMP3 *acp, struct snd_sst_params *s)
{
  struct snd_mp3_params *p = &s->sparams.uc.mp3_params;

  s->codec = p->codec = SST_CODEC_TYPE_MP3;
  p->num_chan = MIX_ACP_NUM_CHANNELS(acp);
  p->brate = MIX_ACP_BITRATE(acp);
  p->sfreq = MIX_ACP_SAMPLE_FREQ(acp);
  p->crc_check = MIX_ACP_MP3_CRC(acp);
  p->pcm_wd_sz = mix_acp_get_bps(MIX_AUDIOCONFIGPARAMS(acp));
  if (p->pcm_wd_sz == MIX_ACP_BPS_16)
    p->op_align = MIX_ACP_OUTPUT_ALIGN_16;
  else
    p->op_align = mix_acp_get_op_align(MIX_AUDIOCONFIGPARAMS(acp));

  return TRUE;
}

gboolean mix_sst_params_convert_wma(MixAudioConfigParamsWMA *acp, struct snd_sst_params *s)
{
  struct snd_wma_params *p = &s->sparams.uc.wma_params;

  p->num_chan = MIX_ACP_NUM_CHANNELS(acp);
  p->brate = MIX_ACP_BITRATE(acp);
  p->sfreq = MIX_ACP_SAMPLE_FREQ(acp);
  p->wma_encode_opt = MIX_ACP_WMA_ENCODE_OPT(acp);
  p->block_align = MIX_ACP_WMA_BLOCK_ALIGN(acp);
  p->channel_mask = MIX_ACP_WMA_CHANNEL_MASK(acp);
  p->format_tag = MIX_ACP_WMA_FORMAT_TAG(acp);
  p->pcm_src = MIX_ACP_WMA_PCM_BIT_WIDTH(acp);
  p->pcm_wd_sz = mix_acp_get_bps(MIX_AUDIOCONFIGPARAMS(acp));
  if (p->pcm_wd_sz == MIX_ACP_BPS_16)
    p->op_align = MIX_ACP_OUTPUT_ALIGN_16;
  else
    p->op_align = mix_acp_get_op_align(MIX_AUDIOCONFIGPARAMS(acp));

  switch (mix_acp_wma_get_version(acp))
  {
    case MIX_AUDIO_WMA_V9:
      s->codec = p->codec = SST_CODEC_TYPE_WMA9;
      break;
    case MIX_AUDIO_WMA_V10:
      s->codec = p->codec = SST_CODEC_TYPE_WMA10;
      break;
    case MIX_AUDIO_WMA_V10P:
      s->codec = p->codec = SST_CODEC_TYPE_WMA10P;
      break;
    default:
      break;
  }

  return TRUE;
}

#define AAC_DUMP(param) g_message("snd_aac_params.%s=%u", #param, p->param)
#define AAC_DUMP_I(param, idx) g_message("snd_aac_params.%s[%d]=%x", #param, idx, p->param[idx])

gboolean mix_sst_params_convert_aac(MixAudioConfigParamsAAC *acp, struct snd_sst_params *s)
{
    struct snd_aac_params *p = &s->sparams.uc.aac_params;

    // I have only AOT, where tools are usually specified at eAOT.
    // However, sometimes, AOT could tell us the tool involved. e.g.
    // AOT==5 --> SBR
    // AOT==29 --> PS
    // AOT==2 --> AAC-LC

    // we know SBR present only if it is indicated presence, or AOT says so.
    guint aot = mix_acp_aac_get_aot(acp);
    p->sbr_present = ((MIX_ACP_AAC_SBR_FLAG(acp) == 1) ||
                      (aot == 5) || 
                      (MIX_ACP_AAC_PS_FLAG(acp) == 1) ||  
                      (aot == 29))?1:0;

    // As far as we know, we should: 
    // set sbr_present flag for SST in case of possible implicit signalling of SBR, and
    // we should use HEAACv2 decoder in case of possible implicit signalling of PS.
    // Although we should theoretically select HEAACv2 decoder for HEAACv1 and HEAAC,
    // it is not advisable since HEAACv2 decoder has more overhead as per SST team.
    // So MixAudio is implicitly selecting codec base on AOT, psPresentFlag and sbrPresentFlag.
    // Application can override the selection by explicitly setting psPresentFlag and/or sbrPresentFlag.
    if ((MIX_ACP_AAC_PS_FLAG(acp) == 1) || (aot == 29))
    {
        // PS present.
        s->codec = p->codec = SST_CODEC_TYPE_eAACP;
    }
    else if (p->sbr_present == 1)
    {
        s->codec = p->codec = SST_CODEC_TYPE_AACP;
    }
    else
    {
        s->codec = p->codec = SST_CODEC_TYPE_AAC;
    }

    p->num_chan = MIX_ACP_AAC_CHANNELS(acp); // core/internal channels
    p->ext_chl = MIX_ACP_NUM_CHANNELS(acp);  // external channels
    p->aac_srate = MIX_ACP_AAC_SAMPLE_RATE(acp);  // aac decoder internal frequency
    p->sfreq = MIX_ACP_SAMPLE_FREQ(acp);  // output/external frequency
    
    p->brate = MIX_ACP_BITRATE(acp);
    p->mpg_id = (guint)mix_acp_aac_get_mpeg_id(acp);
    p->bs_format = mix_acp_aac_get_bit_stream_format(acp);
    p->aac_profile = mix_acp_aac_get_aac_profile(acp);
    // AOT defined by MPEG spec is 5 for SBR but SST definition is 4 for SBR.
    if (aot == 5)
        p->aot = 4;
    else if (aot == 2)
        p->aot = aot;
    p->crc_check = MIX_ACP_AAC_CRC(acp);
    p->brate_type = mix_acp_aac_get_bit_rate_type(acp);
    p->pce_present = MIX_ACP_AAC_PCE_FLAG(acp);
    p->pcm_wd_sz = mix_acp_get_bps(MIX_AUDIOCONFIGPARAMS(acp));
    
    if (p->pcm_wd_sz == MIX_ACP_BPS_16)
        p->op_align = MIX_ACP_OUTPUT_ALIGN_16;
    else
        p->op_align = mix_acp_get_op_align(MIX_AUDIOCONFIGPARAMS(acp));
    
    //p->aac_srate = ; // __u32 aac_srate;	/* Plain AAC decoder operating sample rate */
    //p->ext_chl = ; // __u8 ext_chl; /* No.of external channels */
    
    switch (p->bs_format)
    {
        case MIX_AAC_BS_ADTS:
        g_sprintf((gchar*)p->bit_stream_format, "adts");
        break;
        case MIX_AAC_BS_ADIF:
        g_sprintf((gchar*)p->bit_stream_format, "adif");
        break;
        case MIX_AAC_BS_RAW:
        g_sprintf((gchar*)p->bit_stream_format, "raw");
        p->num_syntc_elems = 0;
        p->syntc_id[0] = (gint8)-1; /* 0 for ID_SCE(Dula Mono), -1 for raw */
        p->syntc_id[1] = (gint8)-1;
        p->syntc_tag[0] = (gint8)-1; /* raw - -1 and 0 -16 for rest of the streams */
        p->syntc_tag[1] = (gint8)-1;
        break;
        default:
        break;
    }
    
    {
        AAC_DUMP(codec);
        AAC_DUMP(num_chan); /* 1=Mono, 2=Stereo*/
        AAC_DUMP(pcm_wd_sz); /* 16/24 - bit*/
        AAC_DUMP(brate);
        AAC_DUMP(sfreq); /* Sampling freq eg. 8000, 441000, 48000 */
        AAC_DUMP(aac_srate);	/* Plain AAC decoder operating sample rate */
        AAC_DUMP(mpg_id); /* 0=MPEG-2, 1=MPEG-4 */
        AAC_DUMP(bs_format); /* input bit stream format adts=0, adif=1, raw=2 */
        AAC_DUMP(aac_profile); /* 0=Main Profile, 1=LC profile, 3=SSR profile */
        AAC_DUMP(ext_chl); /* No.of external channels */
        AAC_DUMP(aot); /* Audio object type. 1=Main , 2=LC , 3=SSR, 4=SBR*/
        AAC_DUMP(op_align); /* output alignment 0=16 bit , 1=MSB, 2= LSB align */
        AAC_DUMP(brate_type); /* 0=CBR, 1=VBR */
        AAC_DUMP(crc_check); /* crc check 0= disable, 1=enable */
        // AAC_DUMP(bit_stream_format[8]); /* input bit stream format adts/adif/raw */
        g_message("snd_aac_params.bit_stream_format=%s", p->bit_stream_format);
        AAC_DUMP(jstereo); /* Joint stereo Flag */
        AAC_DUMP(sbr_present); /* 1 = SBR Present, 0 = SBR absent, for RAW */
        AAC_DUMP(downsample);       /* 1 = Downsampling ON, 0 = Downsampling OFF */
        AAC_DUMP(num_syntc_elems); /* 1- Mono/stereo, 0 - Dual Mono, 0 - for raw */
        g_message("snd_aac_params.syntc_id[0]=%x", p->syntc_id[0]);
        g_message("snd_aac_params.syntc_id[1]=%x", p->syntc_id[1]);
        g_message("snd_aac_params.syntc_tag[0]=%x", p->syntc_tag[0]);
        g_message("snd_aac_params.syntc_tag[1]=%x", p->syntc_tag[1]);
        //AAC_DUMP_I(syntc_id, 0); /* 0 for ID_SCE(Dula Mono), -1 for raw */
        //AAC_DUMP_I(syntc_id, 1); /* 0 for ID_SCE(Dula Mono), -1 for raw */
        //AAC_DUMP_I(syntc_tag, 0); /* raw - -1 and 0 -16 for rest of the streams */
        //AAC_DUMP_I(syntc_tag, 1); /* raw - -1 and 0 -16 for rest of the streams */
        AAC_DUMP(pce_present); /* Flag. 1- present 0 - not present, for RAW */
        AAC_DUMP(reserved);
        AAC_DUMP(reserved1);
    }
    
    return TRUE;
}

MixAudioConfigParams *mix_sst_acp_from_codec(guint codec)
{
  MixAudioConfigParams *ret = NULL;

  // need stream specific ACP
  switch (codec)
  {
    case SST_CODEC_TYPE_MP3:
    case SST_CODEC_TYPE_MP24:
      ret = (MixAudioConfigParams*)mix_acp_mp3_new();
      break;
    case SST_CODEC_TYPE_AAC:
    case SST_CODEC_TYPE_AACP:
    case SST_CODEC_TYPE_eAACP:
      ret = (MixAudioConfigParams*)mix_acp_aac_new();
      break;
    case SST_CODEC_TYPE_WMA9:
    case SST_CODEC_TYPE_WMA10:
    case SST_CODEC_TYPE_WMA10P:
      ret = (MixAudioConfigParams*)mix_acp_wma_new();
      break;
  }

  return ret;
}



MixAudioConfigParams *mix_sst_params_to_acp(struct snd_sst_get_stream_params *stream_params)
{
  MixAudioConfigParams *ret = NULL;

  gboolean allocated = FALSE;
  // Ingoring stream_params.codec_params.result, which seem to return details specific to stream allocation.
  switch  (stream_params->codec_params.result)
  {
      // Please refers to SST API doc for return value definition.
      case 5:
        g_debug("last SET_PARAMS succeeded with Stream Parameter Modified.");
      case 0:
        allocated = TRUE;
        break;
      case 1:
        // last SET_PARAMS failed STREAM was not available.
      case 2:
        // last SET_PARAMS failed CODEC was not available.
      case 3:
        // last SET_PARAMS failed CODEC was not supported.
      case 4:
        // last SET_PARAMS failed Invalid Stream Parameters.
      case 6:
        // last SET_PARAMS failed Invalid Stream ID.
      default:
        // last SET_PARAMS failed unexpectedly.
        break;
  }

  if (allocated)
  {
    switch (stream_params->codec_params.codec)
    {
      case SST_CODEC_TYPE_MP3:
      case SST_CODEC_TYPE_MP24:
        ret = (MixAudioConfigParams*)mix_acp_mp3_new();
        mix_sst_params_to_mp3(MIX_AUDIOCONFIGPARAMSMP3(ret), &stream_params->codec_params.sparams.uc.mp3_params);
        break;
      case SST_CODEC_TYPE_AAC:
      case SST_CODEC_TYPE_AACP:
      case SST_CODEC_TYPE_eAACP:
        ret = (MixAudioConfigParams*)mix_acp_aac_new();
        mix_sst_params_to_aac(MIX_AUDIOCONFIGPARAMSAAC(ret), &stream_params->codec_params.sparams.uc.aac_params);
        break;
      case SST_CODEC_TYPE_WMA9:
      case SST_CODEC_TYPE_WMA10:
      case SST_CODEC_TYPE_WMA10P:
        ret = (MixAudioConfigParams*)mix_acp_wma_new();
        mix_sst_params_to_wma(MIX_AUDIOCONFIGPARAMSWMA(ret), &stream_params->codec_params.sparams.uc.wma_params);
        break;
    }
  }

  if (!ret) ret = mix_acp_new();

  if (ret)
  {
    // Be sure to update all vars that becomes available since the ACP could set defaults.
    MIX_ACP_SAMPLE_FREQ(ret) = stream_params->pcm_params.sfreq;
    MIX_ACP_NUM_CHANNELS(ret) = stream_params->pcm_params.num_chan;
    mix_sst_set_bps(MIX_AUDIOCONFIGPARAMS(ret), stream_params->pcm_params.pcm_wd_sz);
  }

  return ret;
}


void mix_sst_params_to_mp3(MixAudioConfigParamsMP3 *acp, struct snd_mp3_params *params)
{
  if(!acp || !params) return;

  MIX_ACP_NUM_CHANNELS(MIX_AUDIOCONFIGPARAMS(acp)) = params->num_chan;
  MIX_ACP_BITRATE(MIX_AUDIOCONFIGPARAMS(acp)) = params->brate;
  MIX_ACP_SAMPLE_FREQ(MIX_AUDIOCONFIGPARAMS(acp)) = params->sfreq;
  MIX_ACP_MP3_CRC(acp) = params->crc_check;

  mix_sst_set_bps(MIX_AUDIOCONFIGPARAMS(acp), params->pcm_wd_sz);
  mix_sst_set_op_align(MIX_AUDIOCONFIGPARAMS(acp), params->op_align);
}

void mix_sst_params_to_wma(MixAudioConfigParamsWMA *acp, struct snd_wma_params *params)
{
  
  MIX_ACP_BITRATE(acp) = params->brate;
  MIX_ACP_SAMPLE_FREQ(acp) = params->sfreq;
  MIX_ACP_WMA_ENCODE_OPT(acp) = params->wma_encode_opt;
  MIX_ACP_WMA_BLOCK_ALIGN(acp) = params->block_align;
  MIX_ACP_WMA_CHANNEL_MASK(acp) = params->channel_mask;
  MIX_ACP_WMA_FORMAT_TAG(acp) = params->format_tag;
  MIX_ACP_WMA_PCM_BIT_WIDTH(acp) = params->pcm_src;

  mix_sst_set_bps(MIX_AUDIOCONFIGPARAMS(acp), params->pcm_wd_sz);
  mix_sst_set_op_align(MIX_AUDIOCONFIGPARAMS(acp), params->op_align);

  switch (params->codec)
  {
    case SST_CODEC_TYPE_WMA9:
      mix_acp_wma_set_version(acp, MIX_AUDIO_WMA_V9);
      break;
    case SST_CODEC_TYPE_WMA10:
      mix_acp_wma_set_version(acp, MIX_AUDIO_WMA_V10);
      break;
    case SST_CODEC_TYPE_WMA10P:
      mix_acp_wma_set_version(acp, MIX_AUDIO_WMA_V10P);
      break;
  }
}


void mix_sst_params_to_aac(MixAudioConfigParamsAAC *acp, struct snd_aac_params *params)
{
  if (params->codec == SST_CODEC_TYPE_eAACP)
  {
    MIX_ACP_AAC_PS_FLAG(acp) = TRUE;
  }

  MIX_ACP_NUM_CHANNELS(acp) = params->num_chan;
  MIX_ACP_BITRATE(acp) = params->brate;
  MIX_ACP_SAMPLE_FREQ(acp) = params->sfreq;
  mix_acp_aac_set_mpeg_id(acp, params->mpg_id);
  mix_acp_aac_set_bit_stream_format(acp, params->bs_format);
  mix_acp_aac_set_aac_profile(acp, params->aac_profile);

    // SST API specific 4 for SBR while AOT definition in MPEG 4 spec specific 5.
    // converting.
  if (params->aot == 4)
    mix_acp_aac_set_aot(acp, 5);
  else if (params->aot == 2)
    mix_acp_aac_set_aot(acp, params->aot);

  MIX_ACP_AAC_CRC(acp) = params->crc_check;
  mix_acp_aac_set_bit_rate_type(acp, params->brate_type);
  MIX_ACP_AAC_SBR_FLAG(acp) = params->sbr_present;
  MIX_ACP_AAC_PCE_FLAG(acp) = params->pce_present;

  mix_sst_set_bps(MIX_AUDIOCONFIGPARAMS(acp), params->pcm_wd_sz);
  mix_sst_set_op_align(MIX_AUDIOCONFIGPARAMS(acp), params->op_align);

  acp->num_syntc_elems = params->num_syntc_elems;
  acp->syntc_id[0] = params->syntc_id[0];
  acp->syntc_id[1] = params->syntc_id[1];
  acp->syntc_tag[0] = params->syntc_tag[0];
  acp->syntc_tag[1] = params->syntc_tag[1];
}

void mix_sst_set_bps(MixAudioConfigParams *acp, guchar pcm_wd_sz)
{
  switch (pcm_wd_sz)
  {
    case MIX_ACP_BPS_16:
    case MIX_ACP_BPS_24:
      break;
    default:
      pcm_wd_sz = MIX_ACP_BPS_UNKNOWN;
      break;
  }
  mix_acp_set_bps(MIX_AUDIOCONFIGPARAMS(acp), pcm_wd_sz);
}

void mix_sst_set_op_align(MixAudioConfigParams *acp, guchar op_align)
{
  switch (op_align)
  {
    case MIX_ACP_OUTPUT_ALIGN_16:
    case MIX_ACP_OUTPUT_ALIGN_MSB:
    case MIX_ACP_OUTPUT_ALIGN_LSB:
      break;
    default:
      op_align = MIX_ACP_OUTPUT_ALIGN_UNKNOWN;
      break;
  }
  mix_acp_set_op_align(MIX_AUDIOCONFIGPARAMS(acp), op_align);
}

