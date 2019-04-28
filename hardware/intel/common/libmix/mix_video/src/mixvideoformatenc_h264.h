/*
 INTEL CONFIDENTIAL
 Copyright 2009 Intel Corporation All Rights Reserved.
 The source code contained or described herein and all documents related to the source code ("Material") are owned by Intel Corporation or its suppliers or licensors. Title to the Material remains with Intel Corporation or its suppliers and licensors. The Material contains trade secrets and proprietary and confidential information of Intel or its suppliers and licensors. The Material is protected by worldwide copyright and trade secret laws and treaty provisions. No part of the Material may be used, copied, reproduced, modified, published, uploaded, posted, transmitted, distributed, or disclosed in any way without Intel’s prior express written permission.

 No license under any patent, copyright, trade secret or other intellectual property right is granted to or conferred upon you by disclosure or delivery of the Materials, either expressly, by implication, inducement, estoppel or otherwise. Any license under such intellectual property rights must be express and approved by Intel in writing.
 */

#ifndef __MIX_VIDEOFORMATENC_H264_H__
#define __MIX_VIDEOFORMATENC_H264_H__

#include "mixvideoformatenc.h"
#include "mixvideoframe_private.h"

#define MIX_VIDEO_ENC_H264_SURFACE_NUM       20

#define min(X,Y) (((X) < (Y)) ? (X) : (Y))
#define max(X,Y) (((X) > (Y)) ? (X) : (Y))

/*
 * Type macros.
 */
#define MIX_TYPE_VIDEOFORMATENC_H264                  (mix_videoformatenc_h264_get_type ())
#define MIX_VIDEOFORMATENC_H264(obj)                  (G_TYPE_CHECK_INSTANCE_CAST ((obj), MIX_TYPE_VIDEOFORMATENC_H264, MixVideoFormatEnc_H264))
#define MIX_IS_VIDEOFORMATENC_H264(obj)               (G_TYPE_CHECK_INSTANCE_TYPE ((obj), MIX_TYPE_VIDEOFORMATENC_H264))
#define MIX_VIDEOFORMATENC_H264_CLASS(klass)          (G_TYPE_CHECK_CLASS_CAST ((klass), MIX_TYPE_VIDEOFORMATENC_H264, MixVideoFormatEnc_H264Class))
#define MIX_IS_VIDEOFORMATENC_H264_CLASS(klass)       (G_TYPE_CHECK_CLASS_TYPE ((klass), MIX_TYPE_VIDEOFORMATENC_H264))
#define MIX_VIDEOFORMATENC_H264_GET_CLASS(obj)        (G_TYPE_INSTANCE_GET_CLASS ((obj), MIX_TYPE_VIDEOFORMATENC_H264, MixVideoFormatEnc_H264Class))

typedef struct _MixVideoFormatEnc_H264 MixVideoFormatEnc_H264;
typedef struct _MixVideoFormatEnc_H264Class MixVideoFormatEnc_H264Class;

struct _MixVideoFormatEnc_H264 {
	/*< public > */
    MixVideoFormatEnc parent;

    VABufferID      coded_buf;
    VABufferID      seq_param_buf;
    VABufferID      pic_param_buf;
    VABufferID      slice_param_buf;	
    VASurfaceID *   ci_shared_surfaces;
    VASurfaceID *   surfaces;
    guint           surface_num;	

    MixVideoFrame  *cur_fame;	//current input frame to be encoded;	
    MixVideoFrame  *ref_fame;  //reference frame
    MixVideoFrame  *rec_fame;	//reconstructed frame;	

    guint basic_unit_size;  //for rate control
    guint disable_deblocking_filter_idc;
    MixDelimiterType delimiter_type;
    guint slice_num;
    guint va_rcmode; 

    guint       encoded_frames;
    gboolean    pic_skipped;

    gboolean    is_intra;

    guint       coded_buf_size;

	/*< public > */
};

/**
 * MixVideoFormatEnc_H264Class:
 *
 * MI-X Video object class
 */
struct _MixVideoFormatEnc_H264Class {
	/*< public > */
	MixVideoFormatEncClass parent_class;

	/* class members */

	/*< public > */
};

/**
 * mix_videoformatenc_h264_get_type:
 * @returns: type
 *
 * Get the type of object.
 */
GType mix_videoformatenc_h264_get_type(void);

/**
 * mix_videoformatenc_h264_new:
 * @returns: A newly allocated instance of #MixVideoFormatEnc_H264
 *
 * Use this method to create new instance of #MixVideoFormatEnc_H264
 */
MixVideoFormatEnc_H264 *mix_videoformatenc_h264_new(void);

/**
 * mix_videoformatenc_h264_ref:
 * @mix: object to add reference
 * @returns: the MixVideoFormatEnc_H264 instance where reference count has been increased.
 *
 * Add reference count.
 */
MixVideoFormatEnc_H264 *mix_videoformatenc_h264_ref(MixVideoFormatEnc_H264 * mix);

/**
 * mix_videoformatenc_h264_unref:
 * @obj: object to unref.
 *
 * Decrement reference count of the object.
 */
#define mix_videoformatenc_h264_unref(obj) g_object_unref (G_OBJECT(obj))

/* Class Methods */

/* H.264 vmethods */
MIX_RESULT mix_videofmtenc_h264_getcaps(MixVideoFormatEnc *mix, GString *msg);
MIX_RESULT mix_videofmtenc_h264_initialize(MixVideoFormatEnc *mix, 
        MixVideoConfigParamsEnc * config_params_enc,
        MixFrameManager * frame_mgr,
        MixBufferPool * input_buf_pool,
        MixSurfacePool ** surface_pool,
        VADisplay va_display);
MIX_RESULT mix_videofmtenc_h264_encode(MixVideoFormatEnc *mix, MixBuffer * bufin[],
        gint bufincnt, MixIOVec * iovout[], gint iovoutcnt,
        MixVideoEncodeParams * encode_params);
MIX_RESULT mix_videofmtenc_h264_flush(MixVideoFormatEnc *mix);
MIX_RESULT mix_videofmtenc_h264_eos(MixVideoFormatEnc *mix);
MIX_RESULT mix_videofmtenc_h264_deinitialize(MixVideoFormatEnc *mix);
MIX_RESULT mix_videofmtenc_h264_get_max_encoded_buf_size (MixVideoFormatEnc *mix, guint * max_size);

/* Local Methods */

MIX_RESULT mix_videofmtenc_h264_process_encode (MixVideoFormatEnc_H264 *mix, MixBuffer * bufin, 
        MixIOVec * iovout);
MIX_RESULT mix_videofmtenc_h264_AnnexB_to_length_prefixed (
        guint8 * bufin, guint bufin_len, guint8* bufout, guint *bufout_len);

#endif /* __MIX_VIDEOFORMATENC_H264_H__ */
