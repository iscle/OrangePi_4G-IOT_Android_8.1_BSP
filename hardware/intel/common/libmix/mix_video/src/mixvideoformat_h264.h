/*
 INTEL CONFIDENTIAL
 Copyright 2009 Intel Corporation All Rights Reserved.
 The source code contained or described herein and all documents related to the source code ("Material") are owned by Intel Corporation or its suppliers or licensors. Title to the Material remains with Intel Corporation or its suppliers and licensors. The Material contains trade secrets and proprietary and confidential information of Intel or its suppliers and licensors. The Material is protected by worldwide copyright and trade secret laws and treaty provisions. No part of the Material may be used, copied, reproduced, modified, published, uploaded, posted, transmitted, distributed, or disclosed in any way without Intel’s prior express written permission.

 No license under any patent, copyright, trade secret or other intellectual property right is granted to or conferred upon you by disclosure or delivery of the Materials, either expressly, by implication, inducement, estoppel or otherwise. Any license under such intellectual property rights must be express and approved by Intel in writing.
 */

#ifndef __MIX_VIDEOFORMAT_H264_H__
#define __MIX_VIDEOFORMAT_H264_H__

#include "mixvideoformat.h"
#include "mixvideoframe_private.h"

#define MIX_VIDEO_H264_SURFACE_NUM       20

/*
 * Type macros.
 */
#define MIX_TYPE_VIDEOFORMAT_H264                  (mix_videoformat_h264_get_type ())
#define MIX_VIDEOFORMAT_H264(obj)                  (G_TYPE_CHECK_INSTANCE_CAST ((obj), MIX_TYPE_VIDEOFORMAT_H264, MixVideoFormat_H264))
#define MIX_IS_VIDEOFORMAT_H264(obj)               (G_TYPE_CHECK_INSTANCE_TYPE ((obj), MIX_TYPE_VIDEOFORMAT_H264))
#define MIX_VIDEOFORMAT_H264_CLASS(klass)          (G_TYPE_CHECK_CLASS_CAST ((klass), MIX_TYPE_VIDEOFORMAT_H264, MixVideoFormat_H264Class))
#define MIX_IS_VIDEOFORMAT_H264_CLASS(klass)       (G_TYPE_CHECK_CLASS_TYPE ((klass), MIX_TYPE_VIDEOFORMAT_H264))
#define MIX_VIDEOFORMAT_H264_GET_CLASS(obj)        (G_TYPE_INSTANCE_GET_CLASS ((obj), MIX_TYPE_VIDEOFORMAT_H264, MixVideoFormat_H264Class))

typedef struct _MixVideoFormat_H264 MixVideoFormat_H264;
typedef struct _MixVideoFormat_H264Class MixVideoFormat_H264Class;

struct _MixVideoFormat_H264 {
	/*< public > */
	MixVideoFormat parent;

	/*< public > */

	/*< private > */
	GHashTable *dpb_surface_table;
};

/**
 * MixVideoFormat_H264Class:
 *
 * MI-X Video object class
 */
struct _MixVideoFormat_H264Class {
	/*< public > */
	MixVideoFormatClass parent_class;

	/* class members */

	/*< public > */
};

/**
 * mix_videoformat_h264_get_type:
 * @returns: type
 *
 * Get the type of object.
 */
GType mix_videoformat_h264_get_type(void);

/**
 * mix_videoformat_h264_new:
 * @returns: A newly allocated instance of #MixVideoFormat_H264
 *
 * Use this method to create new instance of #MixVideoFormat_H264
 */
MixVideoFormat_H264 *mix_videoformat_h264_new(void);

/**
 * mix_videoformat_h264_ref:
 * @mix: object to add reference
 * @returns: the MixVideoFormat_H264 instance where reference count has been increased.
 *
 * Add reference count.
 */
MixVideoFormat_H264 *mix_videoformat_h264_ref(MixVideoFormat_H264 * mix);

/**
 * mix_videoformat_h264_unref:
 * @obj: object to unref.
 *
 * Decrement reference count of the object.
 */
#define mix_videoformat_h264_unref(obj) g_object_unref (G_OBJECT(obj))

/* Class Methods */

/* H.264 vmethods */
MIX_RESULT mix_videofmt_h264_getcaps(MixVideoFormat *mix, GString *msg);
MIX_RESULT mix_videofmt_h264_initialize(MixVideoFormat *mix, 
				  MixVideoConfigParamsDec * config_params,
				  MixFrameManager * frame_mgr,
				  MixBufferPool * input_buf_pool,
				  MixSurfacePool ** surface_pool,
				  VADisplay va_display);
MIX_RESULT mix_videofmt_h264_decode(MixVideoFormat *mix, MixBuffer * bufin[],
                gint bufincnt, MixVideoDecodeParams * decode_params);
MIX_RESULT mix_videofmt_h264_flush(MixVideoFormat *mix);
MIX_RESULT mix_videofmt_h264_eos(MixVideoFormat *mix);
MIX_RESULT mix_videofmt_h264_deinitialize(MixVideoFormat *mix);

/* Local Methods */

MIX_RESULT mix_videofmt_h264_handle_ref_frames(MixVideoFormat *mix,
                                        VAPictureParameterBufferH264* pic_params,
                                        MixVideoFrame * current_frame);


MIX_RESULT mix_videofmt_h264_process_decode(MixVideoFormat *mix,
                                        vbp_data_h264 *data, 
					guint64 timestamp,
					gboolean discontinuity);


MIX_RESULT mix_videofmt_h264_release_input_buffers(MixVideoFormat *mix, 
					guint64 timestamp);


/* Helper functions to manage the DPB table */
gboolean mix_videofmt_h264_check_in_DPB(gpointer key, gpointer value, gpointer user_data);
void mix_videofmt_h264_destroy_DPB_key(gpointer data);
void mix_videofmt_h264_destroy_DPB_value(gpointer data);
guint mix_videofmt_h264_get_poc(VAPictureH264 *pic);




#endif /* __MIX_VIDEOFORMAT_H264_H__ */
