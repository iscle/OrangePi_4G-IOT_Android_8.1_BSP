/*
 INTEL CONFIDENTIAL
 Copyright 2009 Intel Corporation All Rights Reserved.
 The source code contained or described herein and all documents related to the source code ("Material") are owned by Intel Corporation or its suppliers or licensors. Title to the Material remains with Intel Corporation or its suppliers and licensors. The Material contains trade secrets and proprietary and confidential information of Intel or its suppliers and licensors. The Material is protected by worldwide copyright and trade secret laws and treaty provisions. No part of the Material may be used, copied, reproduced, modified, published, uploaded, posted, transmitted, distributed, or disclosed in any way without Intel’s prior express written permission.

 No license under any patent, copyright, trade secret or other intellectual property right is granted to or conferred upon you by disclosure or delivery of the Materials, either expressly, by implication, inducement, estoppel or otherwise. Any license under such intellectual property rights must be express and approved by Intel in writing.
 */

#ifndef __MIX_VIDEO_H__
#define __MIX_VIDEO_H__

#include <glib-object.h>

#include "mixdrmparams.h"
#include "mixvideoinitparams.h"
#include "mixvideoconfigparamsdec.h"
#include "mixvideoconfigparamsenc.h"
#include "mixvideodecodeparams.h"
#include "mixvideoencodeparams.h"
#include "mixvideorenderparams.h"
#include "mixvideocaps.h"
#include "mixbuffer.h"

/*
 * Type macros.
 */
#define MIX_TYPE_VIDEO                  (mix_video_get_type ())
#define MIX_VIDEO(obj)                  (G_TYPE_CHECK_INSTANCE_CAST ((obj), MIX_TYPE_VIDEO, MixVideo))
#define MIX_IS_VIDEO(obj)               (G_TYPE_CHECK_INSTANCE_TYPE ((obj), MIX_TYPE_VIDEO))
#define MIX_VIDEO_CLASS(klass)          (G_TYPE_CHECK_CLASS_CAST ((klass), MIX_TYPE_VIDEO, MixVideoClass))
#define MIX_IS_VIDEO_CLASS(klass)       (G_TYPE_CHECK_CLASS_TYPE ((klass), MIX_TYPE_VIDEO))
#define MIX_VIDEO_GET_CLASS(obj)        (G_TYPE_INSTANCE_GET_CLASS ((obj), MIX_TYPE_VIDEO, MixVideoClass))

typedef struct _MixVideo MixVideo;
typedef struct _MixVideoClass MixVideoClass;

/*
 * Virtual methods typedef
 */

typedef MIX_RESULT (*MixVideoGetVersionFunc)(MixVideo * mix, guint * major,
		guint * minor);

typedef MIX_RESULT (*MixVideoInitializeFunc)(MixVideo * mix, MixCodecMode mode,
		MixVideoInitParams * init_params, MixDrmParams * drm_init_params);

typedef MIX_RESULT (*MixVideoDeinitializeFunc)(MixVideo * mix);

typedef MIX_RESULT (*MixVideoConfigureFunc)(MixVideo * mix,
		MixVideoConfigParams * config_params,
		MixDrmParams * drm_config_params);

typedef MIX_RESULT (*MixVideoGetConfigFunc)(MixVideo * mix,
		MixVideoConfigParams ** config_params);

typedef MIX_RESULT (*MixVideoDecodeFunc)(MixVideo * mix, MixBuffer * bufin[],
		gint bufincnt, MixVideoDecodeParams * decode_params);

typedef MIX_RESULT (*MixVideoGetFrameFunc)(MixVideo * mix,
		MixVideoFrame ** frame);

typedef MIX_RESULT (*MixVideoReleaseFrameFunc)(MixVideo * mix,
		MixVideoFrame * frame);

typedef MIX_RESULT (*MixVideoRenderFunc)(MixVideo * mix,
		MixVideoRenderParams * render_params, MixVideoFrame *frame);

typedef MIX_RESULT (*MixVideoEncodeFunc)(MixVideo * mix, MixBuffer * bufin[],
		gint bufincnt, MixIOVec * iovout[], gint iovoutcnt,
		MixVideoEncodeParams * encode_params);

typedef MIX_RESULT (*MixVideoFlushFunc)(MixVideo * mix);

typedef MIX_RESULT (*MixVideoEOSFunc)(MixVideo * mix);

typedef MIX_RESULT (*MixVideoGetStateFunc)(MixVideo * mix, MixState * state);

typedef MIX_RESULT
(*MixVideoGetMixBufferFunc)(MixVideo * mix, MixBuffer ** buf);

typedef MIX_RESULT (*MixVideoReleaseMixBufferFunc)(MixVideo * mix,
		MixBuffer * buf);

typedef MIX_RESULT (*MixVideoGetMaxCodedBufferSizeFunc) (MixVideo * mix,
	      guint *max_size);

/**
 * MixVideo:
 * @parent: Parent object.
 * @streamState: Current state of the stream
 * @decodeMode: Current decode mode of the device. This value is valid only when @codingMode equals #MIX_CODING_ENCODE.
 * @encoding: <comment>TBD...</comment>
 *
 * MI-X Video object
 */
struct _MixVideo {
	/*< public > */
	GObject parent;

	/*< public > */

	/*< private > */
	gpointer context;
};

/**
 * MixVideoClass:
 *
 * MI-X Video object class
 */
struct _MixVideoClass {
	/*< public > */
	GObjectClass parent_class;

	/* class members */

	MixVideoGetVersionFunc get_version_func;
	MixVideoInitializeFunc initialize_func;
	MixVideoDeinitializeFunc deinitialize_func;
	MixVideoConfigureFunc configure_func;
	MixVideoGetConfigFunc get_config_func;
	MixVideoDecodeFunc decode_func;
	MixVideoGetFrameFunc get_frame_func;
	MixVideoReleaseFrameFunc release_frame_func;
	MixVideoRenderFunc render_func;
	MixVideoEncodeFunc encode_func;
	MixVideoFlushFunc flush_func;
	MixVideoEOSFunc eos_func;
	MixVideoGetStateFunc get_state_func;
	MixVideoGetMixBufferFunc get_mix_buffer_func;
	MixVideoReleaseMixBufferFunc release_mix_buffer_func;
	MixVideoGetMaxCodedBufferSizeFunc get_max_coded_buffer_size_func;
};

/**
 * mix_video_get_type:
 * @returns: type
 *
 * Get the type of object.
 */
GType mix_video_get_type(void);

/**
 * mix_video_new:
 * @returns: A newly allocated instance of #MixVideo
 *
 * Use this method to create new instance of #MixVideo
 */
MixVideo *mix_video_new(void);

/**
 * mix_video_ref:
 * @mix: object to add reference
 * @returns: the MixVideo instance where reference count has been increased.
 *
 * Add reference count.
 */
MixVideo *mix_video_ref(MixVideo * mix);

/**
 * mix_video_unref:
 * @obj: object to unref.
 *
 * Decrement reference count of the object.
 */
#define mix_video_unref(obj) g_object_unref (G_OBJECT(obj))

/* Class Methods */

MIX_RESULT mix_video_get_version(MixVideo * mix, guint * major, guint * minor);

MIX_RESULT mix_video_initialize(MixVideo * mix, MixCodecMode mode,
		MixVideoInitParams * init_params, MixDrmParams * drm_init_params);

MIX_RESULT mix_video_deinitialize(MixVideo * mix);

MIX_RESULT mix_video_configure(MixVideo * mix,
		MixVideoConfigParams * config_params,
		MixDrmParams * drm_config_params);

MIX_RESULT mix_video_get_config(MixVideo * mix,
		MixVideoConfigParams ** config_params);

MIX_RESULT mix_video_decode(MixVideo * mix, MixBuffer * bufin[], gint bufincnt,
		MixVideoDecodeParams * decode_params);

MIX_RESULT mix_video_get_frame(MixVideo * mix, MixVideoFrame ** frame);

MIX_RESULT mix_video_release_frame(MixVideo * mix, MixVideoFrame * frame);

MIX_RESULT mix_video_render(MixVideo * mix,
		MixVideoRenderParams * render_params, MixVideoFrame *frame);

MIX_RESULT mix_video_encode(MixVideo * mix, MixBuffer * bufin[], gint bufincnt,
		MixIOVec * iovout[], gint iovoutcnt,
		MixVideoEncodeParams * encode_params);

MIX_RESULT mix_video_flush(MixVideo * mix);

MIX_RESULT mix_video_eos(MixVideo * mix);

MIX_RESULT mix_video_get_state(MixVideo * mix, MixState * state);

MIX_RESULT mix_video_get_mixbuffer(MixVideo * mix, MixBuffer ** buf);

MIX_RESULT mix_video_release_mixbuffer(MixVideo * mix, MixBuffer * buf);

#endif /* __MIX_VIDEO_H__ */
