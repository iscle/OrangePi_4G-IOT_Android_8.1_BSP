/*
 INTEL CONFIDENTIAL
 Copyright 2009 Intel Corporation All Rights Reserved.
 The source code contained or described herein and all documents related to the source code ("Material") are owned by Intel Corporation or its suppliers or licensors. Title to the Material remains with Intel Corporation or its suppliers and licensors. The Material contains trade secrets and proprietary and confidential information of Intel or its suppliers and licensors. The Material is protected by worldwide copyright and trade secret laws and treaty provisions. No part of the Material may be used, copied, reproduced, modified, published, uploaded, posted, transmitted, distributed, or disclosed in any way without Intel’s prior express written permission.

 No license under any patent, copyright, trade secret or other intellectual property right is granted to or conferred upon you by disclosure or delivery of the Materials, either expressly, by implication, inducement, estoppel or otherwise. Any license under such intellectual property rights must be express and approved by Intel in writing.
 */

#ifndef __MIX_VIDEOFORMATENC_PREVIEW_H__
#define __MIX_VIDEOFORMATENC_PREVIEW_H__

#include "mixvideoformatenc.h"
#include "mixvideoframe_private.h"

#define MIX_VIDEO_ENC_PREVIEW_SURFACE_NUM       20

#define min(X,Y) (((X) < (Y)) ? (X) : (Y))
#define max(X,Y) (((X) > (Y)) ? (X) : (Y))

/*
 * Type macros.
 */
#define MIX_TYPE_VIDEOFORMATENC_PREVIEW                  (mix_videoformatenc_preview_get_type ())
#define MIX_VIDEOFORMATENC_PREVIEW(obj)                  (G_TYPE_CHECK_INSTANCE_CAST ((obj), MIX_TYPE_VIDEOFORMATENC_PREVIEW, MixVideoFormatEnc_Preview))
#define MIX_IS_VIDEOFORMATENC_PREVIEW(obj)               (G_TYPE_CHECK_INSTANCE_TYPE ((obj), MIX_TYPE_VIDEOFORMATENC_PREVIEW))
#define MIX_VIDEOFORMATENC_PREVIEW_CLASS(klass)          (G_TYPE_CHECK_CLASS_CAST ((klass), MIX_TYPE_VIDEOFORMATENC_PREVIEW, MixVideoFormatEnc_PreviewClass))
#define MIX_IS_VIDEOFORMATENC_PREVIEW_CLASS(klass)       (G_TYPE_CHECK_CLASS_TYPE ((klass), MIX_TYPE_VIDEOFORMATENC_PREVIEW))
#define MIX_VIDEOFORMATENC_PREVIEW_GET_CLASS(obj)        (G_TYPE_INSTANCE_GET_CLASS ((obj), MIX_TYPE_VIDEOFORMATENC_PREVIEW, MixVideoFormatEnc_PreviewClass))

typedef struct _MixVideoFormatEnc_Preview MixVideoFormatEnc_Preview;
typedef struct _MixVideoFormatEnc_PreviewClass MixVideoFormatEnc_PreviewClass;

struct _MixVideoFormatEnc_Preview {
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
	guint slice_num;
	guint va_rcmode; 


	guint       encoded_frames;
	gboolean    pic_skipped;

	gboolean    is_intra;

	guint       coded_buf_size;

	/*< public > */
};

/**
 * MixVideoFormatEnc_PreviewClass:
 *
 * MI-X Video object class
 */
struct _MixVideoFormatEnc_PreviewClass {
	/*< public > */
	MixVideoFormatEncClass parent_class;

	/* class members */

	/*< public > */
};

/**
 * mix_videoformatenc_preview_get_type:
 * @returns: type
 *
 * Get the type of object.
 */
GType mix_videoformatenc_preview_get_type(void);

/**
 * mix_videoformatenc_preview_new:
 * @returns: A newly allocated instance of #MixVideoFormatEnc_Preview
 *
 * Use this method to create new instance of #MixVideoFormatEnc_Preview
 */
MixVideoFormatEnc_Preview *mix_videoformatenc_preview_new(void);

/**
 * mix_videoformatenc_preview_ref:
 * @mix: object to add reference
 * @returns: the MixVideoFormatEnc_Preview instance where reference count has been increased.
 *
 * Add reference count.
 */
MixVideoFormatEnc_Preview *mix_videoformatenc_preview_ref(MixVideoFormatEnc_Preview * mix);

/**
 * mix_videoformatenc_preview_unref:
 * @obj: object to unref.
 *
 * Decrement reference count of the object.
 */
#define mix_videoformatenc_preview_unref(obj) g_object_unref (G_OBJECT(obj))

/* Class Methods */

/* Pure preview vmethods */
MIX_RESULT mix_videofmtenc_preview_getcaps(MixVideoFormatEnc *mix, GString *msg);
MIX_RESULT mix_videofmtenc_preview_initialize(MixVideoFormatEnc *mix, 
        MixVideoConfigParamsEnc * config_params_enc,
        MixFrameManager * frame_mgr,
        MixBufferPool * input_buf_pool,
        MixSurfacePool ** surface_pool,
        VADisplay va_display);
MIX_RESULT mix_videofmtenc_preview_encode(MixVideoFormatEnc *mix, MixBuffer * bufin[],
        gint bufincnt, MixIOVec * iovout[], gint iovoutcnt,
        MixVideoEncodeParams * encode_params);
MIX_RESULT mix_videofmtenc_preview_flush(MixVideoFormatEnc *mix);
MIX_RESULT mix_videofmtenc_preview_eos(MixVideoFormatEnc *mix);
MIX_RESULT mix_videofmtenc_preview_deinitialize(MixVideoFormatEnc *mix);

/* Local Methods */
MIX_RESULT mix_videofmtenc_preview_process_encode (MixVideoFormatEnc_Preview *mix, MixBuffer * bufin, 
        MixIOVec * iovout);

#endif /* __MIX_VIDEOFORMATENC_PREVIEW_H__ */
