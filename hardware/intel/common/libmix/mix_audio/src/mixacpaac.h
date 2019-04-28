/* 
 INTEL CONFIDENTIAL
 Copyright 2009 Intel Corporation All Rights Reserved. 
 The source code contained or described herein and all documents related to the source code ("Material") are owned by Intel Corporation or its suppliers or licensors. Title to the Material remains with Intel Corporation or its suppliers and licensors. The Material contains trade secrets and proprietary and confidential information of Intel or its suppliers and licensors. The Material is protected by worldwide copyright and trade secret laws and treaty provisions. No part of the Material may be used, copied, reproduced, modified, published, uploaded, posted, transmitted, distributed, or disclosed in any way without Intel’s prior express written permission.

 No license under any patent, copyright, trade secret or other intellectual property right is granted to or conferred upon you by disclosure or delivery of the Materials, either expressly, by implication, inducement, estoppel or otherwise. Any license under such intellectual property rights must be express and approved by Intel in writing.
*/

#ifndef __MIX_ACP_AAC_H__
#define __MIX_ACP_AAC_H__

#include "mixacp.h"

/**
 * MIX_TYPE_AUDIOCONFIGPARAMSAAC:
 * 
 * Get type of class.
 */
#define MIX_TYPE_AUDIOCONFIGPARAMSAAC (mix_acp_aac_get_type ())

/**
 * MIX_AUDIOCONFIGPARAMSAAC:
 * @obj: object to be type-casted.
 * 
 * Type casting
 */
#define MIX_AUDIOCONFIGPARAMSAAC(obj) (G_TYPE_CHECK_INSTANCE_CAST ((obj), MIX_TYPE_AUDIOCONFIGPARAMSAAC, MixAudioConfigParamsAAC))

/**
 * MIX_IS_AUDIOCONFIGPARAMSAAC:
 * @obj: an object.
 * 
 * Checks if the given object is an instance of #MixAudioConfigParams
 */
#define MIX_IS_AUDIOCONFIGPARAMSAAC(obj) (G_TYPE_CHECK_INSTANCE_TYPE ((obj), MIX_TYPE_AUDIOCONFIGPARAMSAAC))

/**
 * MIX_AUDIOCONFIGPARAMSAAC_CLASS:
 * @klass: class to be type-casted.
 * 
 * Type Casting.
 */
#define MIX_AUDIOCONFIGPARAMSAAC_CLASS(klass) (G_TYPE_CHECK_CLASS_CAST ((klass), MIX_TYPE_AUDIOCONFIGPARAMSAAC, MixAudioConfigParamsAACClass))

/**
 * MIX_IS_AUDIOCONFIGPARAMSAAC_CLASS:
 * @klass: a class.
 * 
 * Checks if the given class is #MixAudioConfigParamsClass
 */
#define MIX_IS_AUDIOCONFIGPARAMSAAC_CLASS(klass) (G_TYPE_CHECK_CLASS_TYPE ((klass), MIX_TYPE_AUDIOCONFIGPARAMSAAC))

/**
 * MIX_AUDIOCONFIGPARAMSAAC_GET_CLASS:
 * @obj: a #MixAudioConfigParams object.
 * 
 * Get the class instance of the object.
 */
#define MIX_AUDIOCONFIGPARAMSAAC_GET_CLASS(obj) (G_TYPE_INSTANCE_GET_CLASS ((obj), MIX_TYPE_AUDIOCONFIGPARAMSAAC, MixAudioConfigParamsAACClass))

typedef struct _MixAudioConfigParamsAAC        MixAudioConfigParamsAAC;
typedef struct _MixAudioConfigParamsAACClass   MixAudioConfigParamsAACClass;

/**
 * MixAACBitrateType:
 * @MIX_AAC_BR_NULL: Undefined bit rate type.
 * @MIX_AAC_BR_CONSTANT: Constant bit rate.
 * @MIX_AAC_BR_VARIABLE: Variable bit rate.
 * @MIX_AAC_BR_LAST: last entry.
 * 
 * Types of bitrate in AAC.
 */
typedef enum {
  MIX_AAC_BR_NULL=-1,
  MIX_AAC_BR_CONSTANT=0,
  MIX_AAC_BR_VARIABLE,
  MIX_AAC_BR_LAST
} MixAACBitrateType;

/**
 * MixAACBitstreamFormt:
 * @MIX_AAC_BS_NULL: Undefined bitstream format.
 * @MIX_AAC_BS_ADTS: Bitstream is in ADTS format.
 * @MIX_AAC_BS_ADIF: Bitstream is in ADIF format.
 * @MIX_AAC_BS_RAW: Bitstream is in raw format.
 * @MIX_AAC_BS_LAST: Last entry.
 * 
 * AAC bitstream format.
 */
typedef enum {
  MIX_AAC_BS_NULL=-1,
  MIX_AAC_BS_ADTS=0,
  MIX_AAC_BS_ADIF,
  MIX_AAC_BS_RAW,
  MIX_AAC_BS_LAST
} MixAACBitstreamFormt;

/**
 * MixAACProfile:
 * @MIX_AAC_PROFILE_NULL: Undefined profile.
 * @MIX_AAC_PROFILE_MAIN: <emphasis>Not Supported</emphasis> AAC Main profile. 
 * @MIX_AAC_PROFILE_LC: AAC-LC profile, including support of SBR and PS tool.
 * @MIX_AAC_PROFILE_SSR: <emphasis>Not Supported</emphasis> SSR profile. 
 * @MIX_AAC_PROFILE_LAST: Last entry.
 * 
 * AAC profiles definitions.
 */
typedef enum {
  MIX_AAC_PROFILE_NULL=-1,
  MIX_AAC_PROFILE_MAIN=0,
  MIX_AAC_PROFILE_LC,
  MIX_AAC_PROFILE_SSR,
  MIX_AAC_PROFILE_LAST
} MixAACProfile;

/* Using enumeration as this MPEG ID definition is specific to SST and different from
 any MPEG/ADTS header.
*/
/**
 * MixAACMpegID:
 * @MIX_AAC_MPEG_ID_NULL: Undefined MPEG ID.
 * @MIX_AAC_MPEG_2_ID: Indicate MPEG 2 Audio.
 * @MIX_AAC_MPEG_4_ID: Indicate MPEG 4 Audio.
 * @MIX_AAC_MPEG_LAST: last entry. 
 * 
 * AAC MPEG ID.
*/
typedef enum {
  MIX_AAC_MPEG_ID_NULL=-1,
  MIX_AAC_MPEG_2_ID = 0,
  MIX_AAC_MPEG_4_ID = 1,
  MIX_AAC_MPEG_LAST
} MixAACMpegID;

/**
 * MixAudioConfigParamsAAC:
 * @parent: parent.
 * @MPEG_id: MPEG ID. See #mix_acp_aac_set_mpeg_id
 * @bit_stream_format: Bitstream format. See #mix_acp_aac_set_bit_stream_format.
 * @aac_profile: AAC profile. See #mix_acp_aac_set_aac_profile.
 * @aot: Audio object type. See #mix_acp_aac_set_aot
 * @aac_sample_rate: See #MIX_ACP_AAC_SAMPLE_RATE macro.
 * @aac_channels: See #MIX_ACP_AAC_CHANNELS macro.
 * @bit_rate_type: Bitrate type. See #mix_acp_aac_set_bit_rate_type
 * @sbrPresentFlag: See #MIX_ACP_AAC_SBR_FLAG macro.
 * @psPresentFlag: See #MIX_ACP_AAC_PS_FLAG macro.
 * @CRC: CRC check 0:disable, 1:enable.
 * @pce_present: <emphasis>Not Used.</emphasis> See #MIX_ACP_AAC_PCE_FLAG
 * @syntc_id: <emphasis>Not Used.</emphasis> 0 for ID_SCE(Dula Mono), -1 for raw. 
 * @syntc_tag: <emphasis>Not Used.</emphasis> -1 for raw. 0-16 for rest of the streams. 
 * @num_syntc_elems: <emphasis>Not Used.</emphasis> Number of syntatic elements. 
 *
 * MixAudio Parameter object
 */
struct _MixAudioConfigParamsAAC
{
  /*< public >*/
  MixAudioConfigParams parent;

  /*< public >*/
  /* Audio Format Parameters */
  MixAACMpegID MPEG_id;
  MixAACBitstreamFormt bit_stream_format;
  MixAACProfile aac_profile;
  guint aot;
  guint aac_sample_rate;      
  guint aac_channels;  
  MixAACBitrateType bit_rate_type;
  gboolean CRC;
  guint sbrPresentFlag;
  guint psPresentFlag;
  gboolean pce_present;
  gint8 syntc_id[2]; 
  gint8 syntc_tag[2]; 
  gint num_syntc_elems;
  /*< private >*/
  void* reserved1;
  void* reserved2;
  void* reserved3;
  void* reserved4;
};

/**
 * MixAudioConfigParamsAACClass:
 * 
 * MI-X Audio object class
 */
struct _MixAudioConfigParamsAACClass
{
  /*< public >*/
  MixAudioConfigParamsClass parent_class;

  /* class members */
};
 
/**
 * mix_acp_aac_get_type:
 * @returns: type
 * 
 * Get the type of object.
 */
GType mix_acp_aac_get_type (void);

/**
 * mix_acp_aac_new:
 * @returns: A newly allocated instance of #MixAudioConfigParamsAAC
 * 
 * Use this method to create new instance of #MixAudioConfigParamsAAC
 */
MixAudioConfigParamsAAC *mix_acp_aac_new(void);

/**
 * mix_acp_aac_ref:
 * @mix: object to add reference
 * @returns: the MixAudioConfigParamsAAC instance where reference count has been increased.
 * 
 * Add reference count.
 */
MixAudioConfigParamsAAC *mix_acp_aac_ref(MixAudioConfigParamsAAC *mix);

/**
 * mix_acp_aac_unref:
 * @obj: object to unref.
 * 
 * Decrement reference count of the object.
 */
#define mix_acp_aac_unref(obj) mix_params_unref(MIX_PARAMS(obj))

/* Class Methods */


/**
 * mix_acp_aac_set_mpeg_id:
 * @obj: #MixAudioConfigParamsAAC
 * @mpegid: MPEG ID to set.
 * @return: MIX_RESULT
 * 
 * Configure decoder to treat audio as MPEG 2 or MPEG 4.
*/
MIX_RESULT mix_acp_aac_set_mpeg_id(MixAudioConfigParamsAAC *obj, MixAACMpegID mpegid);

/**
 * mix_acp_aac_get_mpeg_id:
 * @obj: #MixAudioConfigParamsAAC object
 * @returns: MPEG ID.
 * 
 * Retrieve currently configured mpeg id value.
*/
MixAACMpegID mix_acp_aac_get_mpeg_id(MixAudioConfigParamsAAC *obj);

/**
 * MIX_ACP_AAC_CRC:
 * @obj: #MixAudioConfigParamsAAC object.
 * 
 * #MixAudioConfigParamAAC.CRC accessor.
*/
#define MIX_ACP_AAC_CRC(obj) (MIX_AUDIOCONFIGPARAMSAAC(obj)->CRC)

/**
 * mix_acp_aac_set_aot:
 * @obj: #MixAudioConfigParamsAAC
 * @aot: Audio Object Type.
 * 
 * Audio Object Type for the MPEG-4 audio stream. Valid value are:
 * 
 * 2 - for AAC-LC
 * 
 * 5 - for SBR
 * 
 * Method returns MIX_RESULT_NOT_SUPPORTED for not supported value.
 * 
*/
MIX_RESULT mix_acp_aac_set_aot(MixAudioConfigParamsAAC *obj, guint aot);

/**
 * mix_acp_aac_get_aot:
 * @obj: #MixAudioConfigParamsAAC
 * @aot: Pointer to receive the Audio Object Type.
 * @return: Currently configured audio object type. Or 0 if not yet specified.
 * 
 * To retrieve currently configured audio object type.
*/
guint mix_acp_aac_get_aot(MixAudioConfigParamsAAC *obj);

/**
 * MIX_ACP_AAC_SBR_FLAG:
 * @obj: #MixAudioConfigParamsAAC object
 * 
 * MixAudioConfigParamAAC.sbrPresentFlag accessor.
 * 
 * Applicable only when @bit_stream_format==#MIX_AAC_BS_RAW. Indicates whether SBR data is present. 
 * 
 * 0: Absent
 * 
 * 1: Present
 * 
 * -1 (0xffffffff): indicates implicit signalling.
 */
#define MIX_ACP_AAC_SBR_FLAG(obj) (MIX_AUDIOCONFIGPARAMSAAC(obj)->sbrPresentFlag)

/**
 * MIX_ACP_AAC_PS_FLAG:
 * @obj: #MixAudioConfigParamsAAC object
 * 
 * MixAudioConfigParamAAC.psPresentFlag accessor.
 * 
 * Applicable only when @bit_stream_format==#MIX_AAC_BS_RAW. Indicates whether PS data is present. 
 * 
 * 0: Absent
 * 
 * 1: Present
 * 
 * -1 (0xffffffff): indicates implicit signalling.
 */
#define MIX_ACP_AAC_PS_FLAG(obj) (MIX_AUDIOCONFIGPARAMSAAC(obj)->psPresentFlag)

/**
 * MIX_ACP_AAC_PCE_FLAG:
 * @obj: #MixAudioConfigParamsAAC object.
 * 
 * MixAudioConfigParamAAC.pce_present accessor.
 * 
 * Applicable only when @bit_stream_format==#MIX_AAC_BS_RAW. Indicates PCE data presence. 
 * 
 * 1:present
 * 
 * 0:absent.
 * 
 * <remark>Not Used on Moorestown.</remark>
 */
#define MIX_ACP_AAC_PCE_FLAG(obj) (MIX_AUDIOCONFIGPARAMSAAC(obj)->pce_present)

/**
 * MIX_ACP_AAC_SAMPLE_RATE:
 * @obj: #MixAudioConfigParamsAAC object.
 * 
 * MixAudioConfigParamAAC.aac_sample_rate accessor.
 * 
 * Plain AAC decoder operating sample rate. Which could be different from the output sampling rate with HE AAC v1 and v2. 
 */
#define MIX_ACP_AAC_SAMPLE_RATE(obj) (MIX_AUDIOCONFIGPARAMSAAC(obj)->aac_sample_rate)

/**
 * MIX_ACP_AAC_CHANNELS:
 * @obj: #MixAudioConfigParamsAAC
 * 
 * MixAudioConfigParamAAC.aac_channels accessor. 
 * 
 * Indicates the number of output channels used by AAC decoder before SBR or PS tools are applied.
 * 
 */
#define MIX_ACP_AAC_CHANNELS(obj) (MIX_AUDIOCONFIGPARAMSAAC(obj)->aac_channels)

/**
 * mix_acp_aac_get_bit_stream_format:
 * @obj: #MixAudioConfigParamsAAC
 * @returns: #MixAACBitstreamFormt
 * 
 * Return the bitstream format currently configured.
 */
MixAACBitstreamFormt mix_acp_aac_get_bit_stream_format(MixAudioConfigParamsAAC *obj);

/**
 * mix_acp_aac_set_bit_stream_format:
 * @obj: #MixAudioConfigParamsAAC
 * @bit_stream_format: Bit stream format.
 * @returns: MIX_RESULT
 * 
 * Set the type of bitstream format as specified in #MixAACBitstreamFormt.
 */
MIX_RESULT mix_acp_aac_set_bit_stream_format(MixAudioConfigParamsAAC *obj, MixAACBitstreamFormt bit_stream_format);

/**
 * mix_acp_aac_get_aac_profile:
 * @obj: #MixAudioConfigParamsAAC
 * @returns: #MixAACProfile
 * 
 * Retrieve the AAC profile currently configured.
 */
MixAACProfile mix_acp_aac_get_aac_profile(MixAudioConfigParamsAAC *obj);

/**
 * mix_acp_aac_set_aac_profile:
 * @obj: #MixAudioConfigParamsAAC
 * @aac_profile: AAC profile to set.
 * @returns: MIX_RESULT
 * 
 * Configure AAC profile for current session.
 * 
 * Only #MIX_AAC_PROFILE_LC is supported in Moorestown.
 */
MIX_RESULT mix_acp_aac_set_aac_profile(MixAudioConfigParamsAAC *obj, MixAACProfile aac_profile);

/**
 * mix_acp_aac_get_bit_rate_type:
 * @obj: #MixAudioConfigParamsAAC
 * @returns: #MixAACBitrateType
 * 
 * Retrieve the bit rate type currently configured.
 */
MixAACBitrateType mix_acp_aac_get_bit_rate_type(MixAudioConfigParamsAAC *obj);

/**
 * mix_acp_aac_set_bit_rate_type:
 * @obj: #MixAudioConfigParamsAAC
 * @bit_rate_type: Bit rate type to set.
 * @returns: MIX_RESULT
 * 
 * Set the bit rate type used.
 */
MIX_RESULT mix_acp_aac_set_bit_rate_type(MixAudioConfigParamsAAC *obj, MixAACBitrateType bit_rate_type);

#endif /* __MIX_AUDIOCONFIGPARAMSAAC_H__ */
