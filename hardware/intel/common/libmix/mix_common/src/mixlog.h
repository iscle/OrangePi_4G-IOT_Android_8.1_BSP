/*
 INTEL CONFIDENTIAL
 Copyright 2009 Intel Corporation All Rights Reserved.
 The source code contained or described herein and all documents related to the source code ("Material") are owned by Intel Corporation or its suppliers or licensors. Title to the Material remains with Intel Corporation or its suppliers and licensors. The Material contains trade secrets and proprietary and confidential information of Intel or its suppliers and licensors. The Material is protected by worldwide copyright and trade secret laws and treaty provisions. No part of the Material may be used, copied, reproduced, modified, published, uploaded, posted, transmitted, distributed, or disclosed in any way without Intel’s prior express written permission.

 No license under any patent, copyright, trade secret or other intellectual property right is granted to or conferred upon you by disclosure or delivery of the Materials, either expressly, by implication, inducement, estoppel or otherwise. Any license under such intellectual property rights must be express and approved by Intel in writing.
 */

#include <glib.h>

#ifndef __MIX_LOG_H__
#define __MIX_LOG_H__

/* Warning: don't call these functions */
void mix_log_func(const gchar* comp, gint level, const gchar *file,
		const gchar *func, gint line, const gchar *format, ...);

/* Components */
#define MIX_VIDEO_COMP 		"mixvideo"
#define GST_MIX_VIDEO_DEC_COMP 	"gstmixvideodec"
#define GST_MIX_VIDEO_SINK_COMP "gstmixvideosink"
#define GST_MIX_VIDEO_ENC_COMP  "gstmixvideoenc"

#define MIX_AUDIO_COMP 		"mixaudio"
#define GST_MIX_AUDIO_DEC_COMP 	"gstmixaudiodec"
#define GST_MIX_AUDIO_SINK_COMP "gstmixaudiosink"

/* log level */
#define MIX_LOG_LEVEL_ERROR	1
#define MIX_LOG_LEVEL_WARNING	2
#define MIX_LOG_LEVEL_INFO	3
#define MIX_LOG_LEVEL_VERBOSE	4


/* MACROS for mixlog */
#ifdef MIX_LOG_ENABLE

#define mix_log(comp, level, format, ...) \
	mix_log_func(comp, level, __FILE__, __FUNCTION__, __LINE__, format, ##__VA_ARGS__)

#else

#define mix_log(comp, level, format, ...)

#endif

#endif
