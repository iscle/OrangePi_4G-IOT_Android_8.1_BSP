/* 
 INTEL CONFIDENTIAL
 Copyright 2009 Intel Corporation All Rights Reserved. 
 The source code contained or described herein and all documents related to the source code ("Material") are owned by Intel Corporation or its suppliers or licensors. Title to the Material remains with Intel Corporation or its suppliers and licensors. The Material contains trade secrets and proprietary and confidential information of Intel or its suppliers and licensors. The Material is protected by worldwide copyright and trade secret laws and treaty provisions. No part of the Material may be used, copied, reproduced, modified, published, uploaded, posted, transmitted, distributed, or disclosed in any way without Intel’s prior express written permission.

 No license under any patent, copyright, trade secret or other intellectual property right is granted to or conferred upon you by disclosure or delivery of the Materials, either expressly, by implication, inducement, estoppel or otherwise. Any license under such intellectual property rights must be express and approved by Intel in writing.
*/

#ifndef __MIX_AUDIOCONFIGPARAMS_H__
#define __MIX_AUDIOCONFIGPARAMS_H__


#include "mixparams.h"
#include "mixresult.h"
#include "mixaudiotypes.h"

/**
 * MixACPOpAlign:
 * @MIX_ACP_OUTPUT_ALIGN_UNKNOWN: Output alignment undefined.
 * @IX_ACP_OUTPUT_ALIGN_16: Output word is 16-bit aligned
 * @MIX_ACP_OUTPUT_ALIGN_MSB: Output word is MSB aligned
 * @MIX_ACP_OUTPUT_ALIGN_LSB: Output word is LSB aligned
 * @MIX_ACP_OUTPUT_ALIGN_LAST: Last entry in list.
 * 
 * Audio Output alignment.
 * 
 */
typedef enum {
  MIX_ACP_OUTPUT_ALIGN_UNKNOWN=-1,
  MIX_ACP_OUTPUT_ALIGN_16=0,
  MIX_ACP_OUTPUT_ALIGN_MSB,
  MIX_ACP_OUTPUT_ALIGN_LSB,
  MIX_ACP_OUTPUT_ALIGN_LAST
} MixACPOpAlign;

/**
 * MixACPBPSType:
 * @MIX_ACP_BPS_UNKNOWN: Bit Per Sample undefined.
 * @MIX_ACP_BPS_16: Output bits per sample is 16 bits 
 * @MIX_ACP_BPS_24: Output bits per sample is 24 bits 
 * 
 * Audio Output Size in bits per sample.
 * 
 */
typedef enum {
  MIX_ACP_BPS_UNKNOWN=0,
  MIX_ACP_BPS_16=16,
  MIX_ACP_BPS_24=24,
} MixACPBPSType;

/**
 * MIX_TYPE_AUDIOCONFIGPARAMS:
 * 
 * Get type of class.
 */
#define MIX_TYPE_AUDIOCONFIGPARAMS (mix_acp_get_type ())

/**
 * MIX_AUDIOCONFIGPARAMS:
 * @obj: object to be type-casted.
 * 
 * Type casting.
 */
#define MIX_AUDIOCONFIGPARAMS(obj) (G_TYPE_CHECK_INSTANCE_CAST ((obj), MIX_TYPE_AUDIOCONFIGPARAMS, MixAudioConfigParams))

/**
 * MIX_IS_AUDIOCONFIGPARAMS:
 * @obj: an object.
 * 
 * Checks if the given object is an instance of #MixAudioConfigParams
 */
#define MIX_IS_AUDIOCONFIGPARAMS(obj) (G_TYPE_CHECK_INSTANCE_TYPE ((obj), MIX_TYPE_AUDIOCONFIGPARAMS))

/**
 * MIX_AUDIOCONFIGPARAMS_CLASS:
 * @klass: class to be type-casted.
 *
 * Type casting.
 */
#define MIX_AUDIOCONFIGPARAMS_CLASS(klass) (G_TYPE_CHECK_CLASS_CAST ((klass), MIX_TYPE_AUDIOCONFIGPARAMS, MixAudioConfigParamsClass))

/**
 * MIX_IS_AUDIOCONFIGPARAMS_CLASS:
 * @klass: a class.
 * 
 * Checks if the given class is #MixAudioConfigParamsClass
 */
#define MIX_IS_AUDIOCONFIGPARAMS_CLASS(klass) (G_TYPE_CHECK_CLASS_TYPE ((klass), MIX_TYPE_AUDIOCONFIGPARAMS))

/**
 * MIX_AUDIOCONFIGPARAMS_GET_CLASS:
 * @obj: a #MixParams object.
 * 
 * Get the class instance of the object.
 */
#define MIX_AUDIOCONFIGPARAMS_GET_CLASS(obj) (G_TYPE_INSTANCE_GET_CLASS ((obj), MIX_TYPE_AUDIOCONFIGPARAMS, MixAudioConfigParamsClass))

typedef struct _MixAudioConfigParams        MixAudioConfigParams;
typedef struct _MixAudioConfigParamsClass   MixAudioConfigParamsClass;

/**
 * MixDecodeMode:
 * @MIX_DECODE_NULL: Undefined decode mode.
 * @MIX_DECODE_DIRECTRENDER: Stream is configured in Direct Render mode
 * @MIX_DECODE_DECODERETURN: Stream is configured in Decode Return mode
 * @MIX_DECODE_LAST: Last index in the enumeration.
 * 
 * Operation Mode for a MI-X session. See mix_audio_configure().
 * 
 */
typedef enum {
  MIX_DECODE_NULL=0,
  MIX_DECODE_DIRECTRENDER,
  MIX_DECODE_DECODERETURN,
  MIX_DECODE_LAST
} MixDecodeMode;

/**
 * MixAudioConfigParams:
 * @parent: parent.
 * @decode_mode: Decode Mode to use for current session. See #mix_acp_set_decodemode
 * @stream_name: Stream name. See #mix_acp_set_streamname. This object will release the string upon destruction.
 * @audio_manager: Type of Audio Manager. See #mix_acp_set_audio_manager.
 * @num_channels: Number of output channels. See #MIX_ACP_NUM_CHANNELS
 * @bit_rate: <emphasis>Optional.</emphasis> See #MIX_ACP_BITRATE
 * @sample_freq: Output frequency. See #MIX_ACP_SAMPLE_FREQ
 * @bits_per_sample: Number of output bit per sample. See #mix_acp_set_bps
 * @op_align: Output Byte Alignment. See #mix_acp_set_op_align
 *
 * @MixAudio configuration parameters object.
 */
struct _MixAudioConfigParams
{
  /*< public >*/
  MixParams parent;

  /*< public >*/
  /* Audio Session Parameters */
  MixDecodeMode decode_mode;
  gchar *stream_name;
  MixAudioManager audio_manager;

  /*< public >*/
  /* Audio Format Parameters */
  gint num_channels;
  gint bit_rate;
  gint sample_freq;
  MixACPBPSType bits_per_sample;
  MixACPOpAlign op_align;
  /*< private >*/
  void* reserved1;
  void* reserved2;
  void* reserved3;
  void* reserved4;
};

/**
 * MixAudioConfigParamsClass:
 * 
 * MI-X Audio object class
 */
struct _MixAudioConfigParamsClass
{
  /*< public >*/
  MixParamsClass parent_class;

  /*< virtual public >*/
  void (*print_params) (MixAudioConfigParams *obj);

  /* class members */

};

/**
 * mix_acp_get_type:
 * @returns: type
 * 
 * Get the type of object.
 */
GType mix_acp_get_type (void);

/**
 * mix_acp_new:
 * @returns: A newly allocated instance of #MixAudioConfigParams
 * 
 * Use this method to create new instance of #MixAudioConfigParams
 */
MixAudioConfigParams *mix_acp_new(void);

/**
 * mix_acp_ref:
 * @mix: object to add reference
 * @returns: the MixAudioConfigParams instance where reference count has been increased.
 * 
 * Add reference count.
 */
MixAudioConfigParams *mix_acp_ref(MixAudioConfigParams *mix);

/**
 * mix_acp_unref:
 * @obj: object to unref.
 * 
 * Decrement reference count of the object.
 */
#define mix_acp_unref(obj) mix_params_unref(MIX_PARAMS(obj))

/* Class Methods */

/**
 * MIX_ACP_DECODEMODE:
 * @obj: #MixAudioConfigParams object
 * 
 * MixAudioConfigParam.decode_mode accessor.
 * 
 * Configure the decode mode to one of #MixDecodeMode value.
*/
#define MIX_ACP_DECODEMODE(obj) (MIX_AUDIOCONFIGPARAMS(obj)->decode_mode)

/**
 * MIX_ACP_NUM_CHANNELS:
 * @obj: #MixAudioConfigParams object
 * 
 * MixAudioConfigParam.num_channels accessor.
 * 
 * Configure the number of output channels. This value need to be exact the same as the supported output channel in the audio since down-mixing is not supported.
 * 
 * This value can be used during #MIX_DECODE_DECODERETURN mode for buffer size/duration calculation.
 * 
 * In Moorestown, number of channel must be 1 or 2.
*/
#define MIX_ACP_NUM_CHANNELS(obj) (MIX_AUDIOCONFIGPARAMS(obj)->num_channels)

/**
 * MIX_ACP_BITRATE:
 * @obj: #MixAudioConfigParams object
 * 
 * MixAudioConfigParam.bit_rate accessor.
 * 
 * Bit rate of the current audio.
 * 
 * <remark>Optional</remark>
*/
#define MIX_ACP_BITRATE(obj) (MIX_AUDIOCONFIGPARAMS(obj)->bit_rate)

/**
 * MIX_ACP_SAMPLE_FREQ:
 * @obj: #MixAudioConfigParams object
 * 
 * MixAudioConfigParam.sample_freq accessor.
 * 
 * Output sampling frequency.
 * 
 * This value can be used during #MIX_DECODE_DECODERETURN mode for buffer size/duration calculation.
*/
#define MIX_ACP_SAMPLE_FREQ(obj) (MIX_AUDIOCONFIGPARAMS(obj)->sample_freq)

/**
 * mix_acp_get_decodemode:
 * @obj: #MixAudioConfigParams
 * @returns: #MixDecodeMode
 * 
 * Retrieve currently configured #MixDecodeMode.
 */
MixDecodeMode mix_acp_get_decodemode(MixAudioConfigParams *obj);

/**
 * mix_acp_set_decodemode:
 * @obj: #MixAudioConfigParams
 * @mode: #MixDecodeMode to set
 * @returns: #MIX_RESULT
 * 
 * Configure session for one of the #MixDecodeMode.
 */
MIX_RESULT mix_acp_set_decodemode(MixAudioConfigParams *obj, MixDecodeMode mode);

/**
 * mix_acp_get_streamname:
 * @obj: #MixAudioConfigParams
 * @returns: pointer to a copy of the stream name. NULL if name is not available.
 * 
 * Return copy of streamname. caller must free with g_free()
 */
gchar *mix_acp_get_streamname(MixAudioConfigParams *obj);

/**
 * mix_acp_set_streamname:
 * @obj: #MixAudioConfigParams
 * @streamname: Stream name to set
 * @returns: #MIX_RESULT
 * 
 * Set the stream name. The object will make a copy of the input stream name string.
 * 
 */
MIX_RESULT mix_acp_set_streamname(MixAudioConfigParams *obj, const gchar *streamname);

/**
 * mix_acp_set_audio_manager:
 * @obj: #MixAudioConfigParams
 * @am: #MixAudioManager
 * @returns: #MIX_RESULT
 * 
 * Set the Audio Manager to one of the #MixAudioManager.
 */
MIX_RESULT mix_acp_set_audio_manager(MixAudioConfigParams *obj, MixAudioManager am);

/**
 * mix_acp_get_audio_manager:
 * @obj: #MixAudioConfigParams
 * @returns: #MixAudioManager
 * 
 * Retrieve name of currently configured audio manager.
 */
MixAudioManager mix_acp_get_audio_manager(MixAudioConfigParams *obj);

/**
 * mix_acp_is_streamname_valid:
 * @obj: #MixAudioConfigParams
 * @returns: boolean indicates if stream name is valid.
 * 
 * Check if stream name is valid considering the current Decode Mode.
 */
gboolean mix_acp_is_streamname_valid(MixAudioConfigParams *obj);


/**
 * mix_acp_get_bps:
 * @obj: #MixAudioConfigParams
 * @returns: #MixACPBPSType
 * 
 * Retrive currently configured bit-per-stream value.
 */
MixACPBPSType mix_acp_get_bps(MixAudioConfigParams *obj);

/**
 * mix_acp_set_bps:
 * @obj: #MixAudioConfigParams
 * @mode: #MixACPBPSType to set
 * @returns: #MIX_RESULT
 * 
 * Configure bit-per-stream of one of the supported #MixACPBPSType.
 */
MIX_RESULT mix_acp_set_bps(MixAudioConfigParams *obj, MixACPBPSType type);

/**
 * mix_acp_get_op_align:
 * @obj: #MixAudioConfigParams object
 * @returns: #MixACPOpAlign
 * 
 * Get Output Alignment.
 */
MixACPOpAlign mix_acp_get_op_align(MixAudioConfigParams *obj);

/**
 * mix_acp_set_op_align:
 * @obj: #MixAudioConfigParams object
 * @op_align: One of the supported #MixACPOpAlign
 * @returns: MIX_RESULT
 * 
 * Set Output Alignment to one of the #MixACPOpAlign value.
 */
MIX_RESULT mix_acp_set_op_align(MixAudioConfigParams *obj, MixACPOpAlign op_align);

/* void mix_acp_print_params(MixAudioConfigParams *obj); */


#endif /* __MIX_AUDIOCONFIGPARAMS_H__ */

