/*
 INTEL CONFIDENTIAL
 Copyright 2009 Intel Corporation All Rights Reserved.
 The source code contained or described herein and all documents related to the source code ("Material") are owned by Intel Corporation or its suppliers or licensors. Title to the Material remains with Intel Corporation or its suppliers and licensors. The Material contains trade secrets and proprietary and confidential information of Intel or its suppliers and licensors. The Material is protected by worldwide copyright and trade secret laws and treaty provisions. No part of the Material may be used, copied, reproduced, modified, published, uploaded, posted, transmitted, distributed, or disclosed in any way without Intel’s prior express written permission.

 No license under any patent, copyright, trade secret or other intellectual property right is granted to or conferred upon you by disclosure or delivery of the Materials, either expressly, by implication, inducement, estoppel or otherwise. Any license under such intellectual property rights must be express and approved by Intel in writing.
 */

#ifndef __MIX_VIDEOFORMAT_H__
#define __MIX_VIDEOFORMAT_H__

#include <va/va.h>
#include <glib-object.h>
#include "vbp_loader.h"
#include "mixvideodef.h"
#include "mixdrmparams.h"
#include "mixvideoconfigparamsdec.h"
#include "mixvideodecodeparams.h"
#include "mixvideoframe.h"
#include "mixframemanager.h"
#include "mixsurfacepool.h"
#include "mixbuffer.h"
#include "mixbufferpool.h"
#include "mixvideoformatqueue.h"

// Redefine the Handle defined in vbp_loader.h
#define	VBPhandle	Handle

/*
 * Type macros.
 */
#define MIX_TYPE_VIDEOFORMAT                  (mix_videoformat_get_type ())
#define MIX_VIDEOFORMAT(obj)                  (G_TYPE_CHECK_INSTANCE_CAST ((obj), MIX_TYPE_VIDEOFORMAT, MixVideoFormat))
#define MIX_IS_VIDEOFORMAT(obj)               (G_TYPE_CHECK_INSTANCE_TYPE ((obj), MIX_TYPE_VIDEOFORMAT))
#define MIX_VIDEOFORMAT_CLASS(klass)          (G_TYPE_CHECK_CLASS_CAST ((klass), MIX_TYPE_VIDEOFORMAT, MixVideoFormatClass))
#define MIX_IS_VIDEOFORMAT_CLASS(klass)       (G_TYPE_CHECK_CLASS_TYPE ((klass), MIX_TYPE_VIDEOFORMAT))
#define MIX_VIDEOFORMAT_GET_CLASS(obj)        (G_TYPE_INSTANCE_GET_CLASS ((obj), MIX_TYPE_VIDEOFORMAT, MixVideoFormatClass))

typedef struct _MixVideoFormat MixVideoFormat;
typedef struct _MixVideoFormatClass MixVideoFormatClass;

/* vmethods typedef */

typedef MIX_RESULT (*MixVideoFmtGetCapsFunc)(MixVideoFormat *mix, GString *msg);
typedef MIX_RESULT (*MixVideoFmtInitializeFunc)(MixVideoFormat *mix,
		MixVideoConfigParamsDec * config_params,
                MixFrameManager * frame_mgr,
		MixBufferPool * input_buf_pool,
		MixSurfacePool ** surface_pool,
		VADisplay va_display);
typedef MIX_RESULT (*MixVideoFmtDecodeFunc)(MixVideoFormat *mix, 
		MixBuffer * bufin[], gint bufincnt, 
		MixVideoDecodeParams * decode_params);
typedef MIX_RESULT (*MixVideoFmtFlushFunc)(MixVideoFormat *mix);
typedef MIX_RESULT (*MixVideoFmtEndOfStreamFunc)(MixVideoFormat *mix);
typedef MIX_RESULT (*MixVideoFmtDeinitializeFunc)(MixVideoFormat *mix);

struct _MixVideoFormat {
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
	VASurfaceID *va_surfaces;
	guint va_num_surfaces;
	VBPhandle parser_handle;
	GString *mime_type;
	guint frame_rate_num;
	guint frame_rate_denom;
	guint picture_width;
	guint picture_height;
	gboolean parse_in_progress;
	gboolean discontinuity_frame_in_progress;
	guint64 current_timestamp;
	MixBufferPool *inputbufpool;
	GQueue *inputbufqueue;
};

/**
 * MixVideoFormatClass:
 *
 * MI-X Video object class
 */
struct _MixVideoFormatClass {
	/*< public > */
	GObjectClass parent_class;

	/* class members */

	/*< public > */
	MixVideoFmtGetCapsFunc getcaps;
	MixVideoFmtInitializeFunc initialize;
	MixVideoFmtDecodeFunc decode;
	MixVideoFmtFlushFunc flush;
	MixVideoFmtEndOfStreamFunc eos;
	MixVideoFmtDeinitializeFunc deinitialize;
};

/**
 * mix_videoformat_get_type:
 * @returns: type
 *
 * Get the type of object.
 */
GType mix_videoformat_get_type(void);

/**
 * mix_videoformat_new:
 * @returns: A newly allocated instance of #MixVideoFormat
 *
 * Use this method to create new instance of #MixVideoFormat
 */
MixVideoFormat *mix_videoformat_new(void);

/**
 * mix_videoformat_ref:
 * @mix: object to add reference
 * @returns: the MixVideoFormat instance where reference count has been increased.
 *
 * Add reference count.
 */
MixVideoFormat *mix_videoformat_ref(MixVideoFormat * mix);

/**
 * mix_videoformat_unref:
 * @obj: object to unref.
 *
 * Decrement reference count of the object.
 */
#define mix_videoformat_unref(obj) g_object_unref (G_OBJECT(obj))

/* Class Methods */

MIX_RESULT mix_videofmt_getcaps(MixVideoFormat *mix, GString *msg);

MIX_RESULT mix_videofmt_initialize(MixVideoFormat *mix, 
				  MixVideoConfigParamsDec * config_params,
				  MixFrameManager * frame_mgr,
				  MixBufferPool * input_buf_pool,
				  MixSurfacePool ** surface_pool,
				  VADisplay va_display);

MIX_RESULT mix_videofmt_decode(MixVideoFormat *mix, MixBuffer * bufin[],
                gint bufincnt, MixVideoDecodeParams * decode_params);

MIX_RESULT mix_videofmt_flush(MixVideoFormat *mix);

MIX_RESULT mix_videofmt_eos(MixVideoFormat *mix);

MIX_RESULT mix_videofmt_deinitialize(MixVideoFormat *mix);

#endif /* __MIX_VIDEOFORMAT_H__ */
