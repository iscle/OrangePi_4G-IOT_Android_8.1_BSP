/* 
 INTEL CONFIDENTIAL
 Copyright 2009 Intel Corporation All Rights Reserved. 
 The source code contained or described herein and all documents related to the source code ("Material") are owned by Intel Corporation or its suppliers or licensors. Title to the Material remains with Intel Corporation or its suppliers and licensors. The Material contains trade secrets and proprietary and confidential information of Intel or its suppliers and licensors. The Material is protected by worldwide copyright and trade secret laws and treaty provisions. No part of the Material may be used, copied, reproduced, modified, published, uploaded, posted, transmitted, distributed, or disclosed in any way without Intel’s prior express written permission.

 No license under any patent, copyright, trade secret or other intellectual property right is granted to or conferred upon you by disclosure or delivery of the Materials, either expressly, by implication, inducement, estoppel or otherwise. Any license under such intellectual property rights must be express and approved by Intel in writing.
*/

#ifndef __MIX_ACP_WMA_H__
#define __MIX_ACP_WMA_H__


#include "mixacp.h"

/**
 * MIX_TYPE_AUDIOCONFIGPARAMSWMA:
 * 
 * Get type of class.
 */
#define MIX_TYPE_AUDIOCONFIGPARAMSWMA (mix_acp_wma_get_type ())

/**
 * MIX_AUDIOCONFIGPARAMSWMA:
 * @obj: object to be type-casted.
 * 
 * Type casting.
 */
#define MIX_AUDIOCONFIGPARAMSWMA(obj) (G_TYPE_CHECK_INSTANCE_CAST ((obj), MIX_TYPE_AUDIOCONFIGPARAMSWMA, MixAudioConfigParamsWMA))

/**
 * MIX_IS_AUDIOCONFIGPARAMSWMA:
 * @obj: an object.
 * 
 * Checks if the given object is an instance of #MixAudioConfigParamsWMA
 */
#define MIX_IS_AUDIOCONFIGPARAMSWMA(obj) (G_TYPE_CHECK_INSTANCE_TYPE ((obj), MIX_TYPE_AUDIOCONFIGPARAMSWMA))

/**
 * MIX_AUDIOCONFIGPARAMSWMA_CLASS:
 * @klass: class to be type-casted.
 * 
 * Type casting.
 */
#define MIX_AUDIOCONFIGPARAMSWMA_CLASS(klass) (G_TYPE_CHECK_CLASS_CAST ((klass), MIX_TYPE_AUDIOCONFIGPARAMSWMA, MixAudioConfigParamsWMAClass))

/**
 * MIX_IS_AUDIOCONFIGPARAMSWMA_CLASS:
 * @klass: a class.
 * 
 * Checks if the given class is #MixAudioConfigParamsWMAClass
 */
#define MIX_IS_AUDIOCONFIGPARAMSWMA_CLASS(klass) (G_TYPE_CHECK_CLASS_TYPE ((klass), MIX_TYPE_AUDIOCONFIGPARAMSWMA))

/**
 * MIX_AUDIOCONFIGPARAMSWMA_GET_CLASS:
 * @obj: a #MixAudioConfigParamsWMA object.
 * 
 * Get the class instance of the object.
 */
#define MIX_AUDIOCONFIGPARAMSWMA_GET_CLASS(obj) (G_TYPE_INSTANCE_GET_CLASS ((obj), MIX_TYPE_AUDIOCONFIGPARAMSWMA, MixAudioConfigParamsWMAClass))

/**
 * MixAudioWMAVersion:
 * @MIX_AUDIO_WMA_VUNKNOWN: WMA version undefined.
 * @MIX_AUDIO_WMA_V9: WMA 9
 * @MIX_AUDIO_WMA_V10: <emphasis>Not Supported</emphasis> WMA 10
 * @MIX_AUDIO_WMA_V10P: <emphasis>Not Supported</emphasis> WMA 10 Pro
 * @MIX_AUDIO_WMA_LAST: last entry.
 * 
 * WMA version.
 */
typedef enum {
  MIX_AUDIO_WMA_VUNKNOWN,
  MIX_AUDIO_WMA_V9,
  MIX_AUDIO_WMA_V10,
  MIX_AUDIO_WMA_V10P,
  MIX_AUDIO_WMA_LAST
} MixAudioWMAVersion;

typedef struct _MixAudioConfigParamsWMA        MixAudioConfigParamsWMA;
typedef struct _MixAudioConfigParamsWMAClass   MixAudioConfigParamsWMAClass;

/**
 * MixAudioConfigParamsWMA:
 * @parent: parent.
 * @channel_mask: Channel Mask. See #MIX_ACP_WMA_CHANNEL_MASK
 * @format_tag: Format tag. See #MIX_ACP_WMA_FORMAT_TAG
 * @block_algin: Block alignment. See #MIX_ACP_WMA_BLOCK_ALIGN
 * @wma_encode_opt: Encoder option. See #MIX_ACP_WMA_ENCODE_OPT
 * @pcm_bit_width: Source pcm bit width. See #MIX_ACP_WMA_PCM_BIT_WIDTH
 * @wma_version: WMA version. See #mix_acp_wma_set_version
 *
 * MI-X Audio Parameter object
 */
struct _MixAudioConfigParamsWMA
{
  /*< public >*/
  MixAudioConfigParams parent;

  /*< public >*/
  /* Audio Format Parameters */
  guint32 channel_mask;
  guint16 format_tag;
  guint16 block_align;
  guint16 wma_encode_opt;/* Encoder option */
  guint8 pcm_bit_width;  /* source pcm bit width */
  MixAudioWMAVersion wma_version;
};

/**
 * MixAudioConfigParamsWMAClass:
 * 
 * MI-X Audio object class
 */
struct _MixAudioConfigParamsWMAClass
{
  /*< public >*/
  MixAudioConfigParamsClass parent_class;

  /* class members */
};
 
/**
 * mix_acp_wma_get_type:
 * @returns: type
 * 
 * Get the type of object.
 */
GType mix_acp_wma_get_type (void);

/**
 * mix_acp_wma_new:
 * @returns: A newly allocated instance of #MixAudioConfigParamsWMA
 * 
 * Use this method to create new instance of #MixAudioConfigParamsWMA
 */
MixAudioConfigParamsWMA *mix_acp_wma_new(void);

/**
 * mix_acp_wma_ref:
 * @mix: object to add reference
 * @returns: the MixAudioConfigParamsWMA instance where reference count has been increased.
 * 
 * Add reference count.
 */
MixAudioConfigParamsWMA *mix_acp_wma_ref(MixAudioConfigParamsWMA *mix);

/**
 * mix_acp_wma_unref:
 * @obj: object to unref.
 * 
 * Decrement reference count of the object.
 */
#define mix_acp_wma_unref(obj) mix_params_unref(MIX_PARAMS(obj))

/**
 * MIX_ACP_WMA_CHANNEL_MASK:
 * @obj: #MixAudioConfigParamsWMA object
 * 
 * MixAudioConfigParamWMA.channel_mask accessor.
 * 
 * Channel mask must be one of the following:
 * 
 * 4: For single (1) channel output.
 * 
 * 3: For stereo (2) channels output.
 * 
 * Only 1 or 2 output channels are supported.
 * 
*/
#define MIX_ACP_WMA_CHANNEL_MASK(obj) (MIX_AUDIOCONFIGPARAMSWMA(obj)->channel_mask)

/**
 * MIX_ACP_WMA_FORMAT_TAG:
 * @obj: #MixAudioConfigParamsWMA object
 * 
 * MixAudioConfigParamWMA.format_tag accessor.
 * 
 * <remark>In Moorestown, only value 0x0161 combined with use of #MIX_AUDIO_WMA_V9 is supported.</remark>
*/
#define MIX_ACP_WMA_FORMAT_TAG(obj) (MIX_AUDIOCONFIGPARAMSWMA(obj)->format_tag)

/**
 * MIX_ACP_WMA_BLOCK_ALIGN:
 * @obj: #MixAudioConfigParamsWMA object
 * 
 * MixAudioConfigParamWMA.block_align accessor.
 * 
 * Block alignment indicates packet size. Available from ASF Header.
*/
#define MIX_ACP_WMA_BLOCK_ALIGN(obj) (MIX_AUDIOCONFIGPARAMSWMA(obj)->block_align)

/**
 * MIX_ACP_WMA_ENCODE_OPT:
 * @obj: #MixAudioConfigParamsWMA object
 * 
 * MixAudioConfigParamWMA.wma_encode_opt accessor.
 * 
 * Encoder option available from ASF header.
*/
#define MIX_ACP_WMA_ENCODE_OPT(obj) (MIX_AUDIOCONFIGPARAMSWMA(obj)->wma_encode_opt)

/**
 * MIX_ACP_WMA_PCM_BIT_WIDTH:
 * @obj: #MixAudioConfigParamsWMA object
 * 
 * MixAudioConfigParamWMA.pcm_bit_width accessor.
 * 
 * Source pcm bit width available from ASF Header.
*/
#define MIX_ACP_WMA_PCM_BIT_WIDTH(obj) (MIX_AUDIOCONFIGPARAMSWMA(obj)->pcm_bit_width)

/* Class Methods */
/**
 * mix_acp_wma_get_version:
 * @obj: #MixAudioConfigParamsWMA object
 * @returns: MixAudioWMAVersion
 * 
 * Get WMA Version.
*/
MixAudioWMAVersion mix_acp_wma_get_version(MixAudioConfigParamsWMA *obj);

/**
 * mix_acp_wma_set_version:
 * @obj: #MixAudioConfigParamsWMA object
 * @ver: MixAudioWMAVersion to set.
 * @returns: MIX_RESULT.
 * 
 * Set WMA Version.
 * 
 * <remark>In Moorestown, only #MIX_AUDIO_WMA_V9 is supported</remark>
*/
MIX_RESULT mix_acp_wma_set_version(MixAudioConfigParamsWMA *obj, MixAudioWMAVersion ver);

#endif /* __MIX_AUDIOCONFIGPARAMSWMA_H__ */
