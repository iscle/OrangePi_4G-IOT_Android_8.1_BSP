/* 
 INTEL CONFIDENTIAL
 Copyright 2009 Intel Corporation All Rights Reserved. 
 The source code contained or described herein and all documents related to the source code ("Material") are owned by Intel Corporation or its suppliers or licensors. Title to the Material remains with Intel Corporation or its suppliers and licensors. The Material contains trade secrets and proprietary and confidential information of Intel or its suppliers and licensors. The Material is protected by worldwide copyright and trade secret laws and treaty provisions. No part of the Material may be used, copied, reproduced, modified, published, uploaded, posted, transmitted, distributed, or disclosed in any way without Intel’s prior express written permission.

 No license under any patent, copyright, trade secret or other intellectual property right is granted to or conferred upon you by disclosure or delivery of the Materials, either expressly, by implication, inducement, estoppel or otherwise. Any license under such intellectual property rights must be express and approved by Intel in writing.
*/

#ifndef __MIX_ACP_MP3_H__
#define __MIX_ACP_MP3_H__


#include "mixacp.h"

/**
 * MIX_TYPE_AUDIOCONFIGPARAMSMP3:
 * 
 * Get type of class.
 */
#define MIX_TYPE_AUDIOCONFIGPARAMSMP3 (mix_acp_mp3_get_type ())

/**
 * MIX_AUDIOCONFIGPARAMSMP3:
 * @obj: object to be type-casted.
 * 
 * Type casting.
 */
#define MIX_AUDIOCONFIGPARAMSMP3(obj) (G_TYPE_CHECK_INSTANCE_CAST ((obj), MIX_TYPE_AUDIOCONFIGPARAMSMP3, MixAudioConfigParamsMP3))

/**
 * MIX_IS_AUDIOCONFIGPARAMSMP3:
 * @obj: an object.
 * 
 * Checks if the given object is an instance of #MixAudioConfigParamsMP3
 */
#define MIX_IS_AUDIOCONFIGPARAMSMP3(obj) (G_TYPE_CHECK_INSTANCE_TYPE ((obj), MIX_TYPE_AUDIOCONFIGPARAMSMP3))

/**
 * MIX_AUDIOCONFIGPARAMSMP3_CLASS:
 * @klass: class to be type-casted.
 * 
 * Type casting.
 */
#define MIX_AUDIOCONFIGPARAMSMP3_CLASS(klass) (G_TYPE_CHECK_CLASS_CAST ((klass), MIX_TYPE_AUDIOCONFIGPARAMSMP3, MixAudioConfigParamsMP3Class))

/**
 * MIX_IS_AUDIOCONFIGPARAMSMP3_CLASS:
 * @klass: a class.
 * 
 * Checks if the given class is #MixAudioConfigParamsMP3Class
 */
#define MIX_IS_AUDIOCONFIGPARAMSMP3_CLASS(klass) (G_TYPE_CHECK_CLASS_TYPE ((klass), MIX_TYPE_AUDIOCONFIGPARAMSMP3))

/**
 * MIX_AUDIOCONFIGPARAMSMP3_GET_CLASS:
 * @obj: a #MixAudioConfigParams object.
 * 
 * Get the class instance of the object.
 */
#define MIX_AUDIOCONFIGPARAMSMP3_GET_CLASS(obj) (G_TYPE_INSTANCE_GET_CLASS ((obj), MIX_TYPE_AUDIOCONFIGPARAMSMP3, MixAudioConfigParamsMP3Class))

typedef struct _MixAudioConfigParamsMP3        MixAudioConfigParamsMP3;
typedef struct _MixAudioConfigParamsMP3Class   MixAudioConfigParamsMP3Class;

/**
 * MixAudioConfigParamsMP3:
 * @parent: parent.
 * @CRC: CRC. See #MIX_ACP_MP3_CRC
 * @MPEG_format: <emphasis>Optional</emphasis>MPEG format of the mpeg audio. See #MIX_ACP_MP3_MPEG_FORMAT
 * @MPEG_layer: <emphasis>Optional</emphasis>MPEG layer of the mpeg audio. See #MIX_ACP_MP3_MPEG_LAYER
 *
 * MI-X Audio Parameter object for MP3 Audio.
 */
struct _MixAudioConfigParamsMP3
{
  /*< public >*/
  MixAudioConfigParams parent;

  /*< public >*/
  /* Audio Format Parameters */
  gboolean CRC;
  gint MPEG_format;
  gint MPEG_layer;

  /*< private >*/
  void* reserved1;
  void* reserved2;
  void* reserved3;
  void* reserved4;
};

/**
 * MixAudioConfigParamsMP3Class:
 * 
 * MI-X Audio object class
 */
struct _MixAudioConfigParamsMP3Class
{
  /*< public >*/
  MixAudioConfigParamsClass parent_class;

  /* class members */
};
 
/**
 * mix_acp_mp3_get_type:
 * @returns: type
 * 
 * Get the type of object.
 */
GType mix_acp_mp3_get_type (void);

/**
 * mix_acp_mp3_new:
 * @returns: A newly allocated instance of #MixAudioConfigParamsMP3
 * 
 * Use this method to create new instance of #MixAudioConfigParamsMP3
 */
MixAudioConfigParamsMP3 *mix_acp_mp3_new(void);

/**
 * mix_acp_mp3_ref:
 * @mix: object to add reference
 * @returns: the MixAudioConfigParamsMP3 instance where reference count has been increased.
 * 
 * Add reference count.
 */
MixAudioConfigParamsMP3 *mix_acp_mp3_ref(MixAudioConfigParamsMP3 *mix);

/**
 * mix_acp_mp3_unref:
 * @obj: object to unref.
 * 
 * Decrement reference count of the object.
 */
#define mix_acp_mp3_unref(obj) mix_params_unref(MIX_PARAMS(obj))

/* Class Methods */

/**
 * MIX_ACP_MP3_CRC:
 * @obj: #MixAudioConfigParamsMP3 object.
 * 
 * MixAudioConfigParamMP3.CRC accessor.
 * 
 * <remark>Optional</remark>
*/
#define MIX_ACP_MP3_CRC(obj) (MIX_AUDIOCONFIGPARAMSMP3(obj)->CRC)

/**
 * MIX_ACP_MP3_MPEG_FORMAT:
 * @obj: #MixAudioConfigParamsMP3 object.
 * 
 * MixAudioConfigParamMP3.MPEG_format accessor.
 * 
 * Supported MPEG format should be 1 or 2.
*/
#define MIX_ACP_MP3_MPEG_FORMAT(obj) (MIX_AUDIOCONFIGPARAMSMP3(obj)->MPEG_format)

/**
 * MIX_ACP_MP3_MPEG_LAYER:
 * @obj: #MixAudioConfigParamsMP3 object.
 * 
 * MixAudioConfigParamMP3.MPEG_layer accessor.
 * 
 * Supported layer should be 1, 2, or 3.
*/
#define MIX_ACP_MP3_MPEG_LAYER(obj) (MIX_AUDIOCONFIGPARAMSMP3(obj)->MPEG_layer)

#endif /* __MIX_AUDIOCONFIGPARAMSMP3_H__ */
