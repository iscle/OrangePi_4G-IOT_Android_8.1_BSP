/*
 INTEL CONFIDENTIAL
 Copyright 2009 Intel Corporation All Rights Reserved.
 The source code contained or described herein and all documents related to the source code ("Material") are owned by Intel Corporation or its suppliers or licensors. Title to the Material remains with Intel Corporation or its suppliers and licensors. The Material contains trade secrets and proprietary and confidential information of Intel or its suppliers and licensors. The Material is protected by worldwide copyright and trade secret laws and treaty provisions. No part of the Material may be used, copied, reproduced, modified, published, uploaded, posted, transmitted, distributed, or disclosed in any way without Intel’s prior express written permission.

 No license under any patent, copyright, trade secret or other intellectual property right is granted to or conferred upon you by disclosure or delivery of the Materials, either expressly, by implication, inducement, estoppel or otherwise. Any license under such intellectual property rights must be express and approved by Intel in writing.
 */

#ifndef __MIX_VIDEO_PRIVATE_H__
#define __MIX_VIDEO_PRIVATE_H__


typedef struct _MixVideoPrivate MixVideoPrivate;

struct _MixVideoPrivate {
	/*< private > */

	GMutex *objlock;
	gboolean initialized;
	gboolean configured;

	VADisplay va_display;

	int va_major_version;
	int va_minor_version;

	MixCodecMode codec_mode;

	MixVideoInitParams 	*init_params;
	MixDrmParams 		*drm_params;

	MixVideoConfigParams 	*config_params;

	MixFrameManager 	*frame_manager;
	MixVideoFormat 		*video_format;
	MixVideoFormatEnc       *video_format_enc;

	MixSurfacePool		*surface_pool;
	MixBufferPool		*buffer_pool;

};

/**
 * MIX_VIDEO_PRIVATE:
 *
 * Get private structure of this class.
 * @obj: class object for which to get private data.
 */
#define MIX_VIDEO_GET_PRIVATE(obj)  \
   (G_TYPE_INSTANCE_GET_PRIVATE ((obj), MIX_TYPE_VIDEO, MixVideoPrivate))

/* Private functions */
void mix_video_private_initialize(MixVideoPrivate* priv);
void mix_video_private_cleanup(MixVideoPrivate* priv);


#endif /* __MIX_VIDEO_PRIVATE_H__ */
