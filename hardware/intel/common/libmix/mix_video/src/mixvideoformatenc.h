/*
 INTEL CONFIDENTIAL
 Copyright 2009 Intel Corporation All Rights Reserved.
 The source code contained or described herein and all documents related to the source code ("Material") are owned by Intel Corporation or its suppliers or licensors. Title to the Material remains with Intel Corporation or its suppliers and licensors. The Material contains trade secrets and proprietary and confidential information of Intel or its suppliers and licensors. The Material is protected by worldwide copyright and trade secret laws and treaty provisions. No part of the Material may be used, copied, reproduced, modified, published, uploaded, posted, transmitted, distributed, or disclosed in any way without Intel’s prior express written permission.

 No license under any patent, copyright, trade secret or other intellectual property right is granted to or conferred upon you by disclosure or delivery of the Materials, either expressly, by implication, inducement, estoppel or otherwise. Any license under such intellectual property rights must be express and approved by Intel in writing.
 */

#ifndef __MIX_VIDEOFORMATENC_H__
#define __MIX_VIDEOFORMATENC_H__

#include <va/va.h>
#include <glib-object.h>
#include "mixvideodef.h"
#include "mixdrmparams.h"
#include "mixvideoconfigparamsenc.h"
#include "mixvideoframe.h"
#include "mixframemanager.h"
#include "mixsurfacepool.h"
#include "mixbuffer.h"
#include "mixbufferpool.h"
#include "mixvideoformatqueue.h"
#include "mixvideoencodeparams.h"

/*
 * Type macros.
 */
#define MIX_TYPE_VIDEOFORMATENC                  (mix_videoformatenc_get_type ())
#define MIX_VIDEOFORMATENC(obj)                  (G_TYPE_CHECK_INSTANCE_CAST ((obj), MIX_TYPE_VIDEOFORMATENC, MixVideoFormatEnc))
#define MIX_IS_VIDEOFORMATENC(obj)               (G_TYPE_CHECK_INSTANCE_TYPE ((obj), MIX_TYPE_VIDEOFORMATENC))
#define MIX_VIDEOFORMATENC_CLASS(klass)          (G_TYPE_CHECK_CLASS_CAST ((klass), MIX_TYPE_VIDEOFORMATENC, MixVideoFormatEncClass))
#define MIX_IS_VIDEOFORMATENC_CLASS(klass)       (G_TYPE_CHECK_CLASS_TYPE ((klass), MIX_TYPE_VIDEOFORMATENC))
#define MIX_VIDEOFORMATENC_GET_CLASS(obj)        (G_TYPE_INSTANCE_GET_CLASS ((obj), MIX_TYPE_VIDEOFORMATENC, MixVideoFormatEncClass))

typedef struct _MixVideoFormatEnc MixVideoFormatEnc;
typedef struct _MixVideoFormatEncClass MixVideoFormatEncClass;

/* vmethods typedef */

/* TODO: change return type and method parameters */
typedef MIX_RESULT (*MixVideoFmtEncGetCapsFunc)(MixVideoFormatEnc *mix, GString *msg);
typedef MIX_RESULT (*MixVideoFmtEncInitializeFunc)(MixVideoFormatEnc *mix,
        MixVideoConfigParamsEnc* config_params_enc,
        MixFrameManager * frame_mgr,
        MixBufferPool * input_buf_pool,
        MixSurfacePool ** surface_pool,
        VADisplay va_display);
typedef MIX_RESULT (*MixVideoFmtEncodeFunc)(MixVideoFormatEnc *mix, MixBuffer * bufin[],
        gint bufincnt, MixIOVec * iovout[], gint iovoutcnt,
        MixVideoEncodeParams * encode_params);
typedef MIX_RESULT (*MixVideoFmtEncFlushFunc)(MixVideoFormatEnc *mix);
typedef MIX_RESULT (*MixVideoFmtEncEndOfStreamFunc)(MixVideoFormatEnc *mix);
typedef MIX_RESULT (*MixVideoFmtEncDeinitializeFunc)(MixVideoFormatEnc *mix);
typedef MIX_RESULT (*MixVideoFmtEncGetMaxEncodedBufSizeFunc) (MixVideoFormatEnc *mix, guint *max_size);

struct _MixVideoFormatEnc {
    /*< public > */
    GObject parent;

	/*< public > */

	/*< private > */
    GMutex *objectlock;
    gboolean initialized;
    MixFrameManager *framemgr;
    MixSurfacePool *surfacepool;
    VADisplay va_display;
    VAContextID va_context;
    VAConfigID va_config;
    GString *mime_type;
    
    guint frame_rate_num;
    guint frame_rate_denom;
    guint picture_width;
    guint picture_height;
    
    guint initial_qp;
    guint min_qp;
    guint intra_period;
    guint bitrate;
    
    gboolean share_buf_mode;	
    gulong *	ci_frame_id;
    guint	ci_frame_num;	
    
    gulong    drawable;
    gboolean need_display;	

    VAProfile va_profile;
    VAEntrypoint va_entrypoint;
    guint va_format;
    guint va_rcmode; 	
	
    
    MixBufferPool *inputbufpool;
    GQueue *inputbufqueue;
};

/**
 * MixVideoFormatEncClass:
 *
 * MI-X Video object class
 */
struct _MixVideoFormatEncClass {
	/*< public > */
	GObjectClass parent_class;

	/* class members */

	/*< public > */
	MixVideoFmtEncGetCapsFunc getcaps;
	MixVideoFmtEncInitializeFunc initialize;
	MixVideoFmtEncodeFunc encode;
	MixVideoFmtEncFlushFunc flush;
	MixVideoFmtEncEndOfStreamFunc eos;
	MixVideoFmtEncDeinitializeFunc deinitialize;
	MixVideoFmtEncGetMaxEncodedBufSizeFunc getmaxencodedbufsize;	
};

/**
 * mix_videoformatenc_get_type:
 * @returns: type
 *
 * Get the type of object.
 */
GType mix_videoformatenc_get_type(void);

/**
 * mix_videoformatenc_new:
 * @returns: A newly allocated instance of #MixVideoFormatEnc
 *
 * Use this method to create new instance of #MixVideoFormatEnc
 */
MixVideoFormatEnc *mix_videoformatenc_new(void);

/**
 * mix_videoformatenc_ref:
 * @mix: object to add reference
 * @returns: the MixVideoFormatEnc instance where reference count has been increased.
 *
 * Add reference count.
 */
MixVideoFormatEnc *mix_videoformatenc_ref(MixVideoFormatEnc * mix);

/**
 * mix_videoformatenc_unref:
 * @obj: object to unref.
 *
 * Decrement reference count of the object.
 */
#define mix_videoformatenc_unref(obj) g_object_unref (G_OBJECT(obj))

/* Class Methods */

/* TODO: change method parameter list */
MIX_RESULT mix_videofmtenc_getcaps(MixVideoFormatEnc *mix, GString *msg);

MIX_RESULT mix_videofmtenc_initialize(MixVideoFormatEnc *mix, 
        MixVideoConfigParamsEnc * enc_config_params,
        MixFrameManager * frame_mgr,
        MixBufferPool * input_buf_pool,
        MixSurfacePool ** surface_pool,
        VADisplay va_display);

MIX_RESULT mix_videofmtenc_encode(MixVideoFormatEnc *mix, MixBuffer * bufin[],
        gint bufincnt, MixIOVec * iovout[], gint iovoutcnt,
        MixVideoEncodeParams * encode_params);

MIX_RESULT mix_videofmtenc_flush(MixVideoFormatEnc *mix);

MIX_RESULT mix_videofmtenc_eos(MixVideoFormatEnc *mix);

MIX_RESULT mix_videofmtenc_deinitialize(MixVideoFormatEnc *mix);

MIX_RESULT mix_videofmtenc_get_max_coded_buffer_size(MixVideoFormatEnc *mix, guint *max_size);


#endif /* __MIX_VIDEOFORMATENC_H__ */
