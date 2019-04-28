/*
 INTEL CONFIDENTIAL
 Copyright 2009 Intel Corporation All Rights Reserved.
 The source code contained or described herein and all documents related to the source code ("Material") are owned by Intel Corporation or its suppliers or licensors. Title to the Material remains with Intel Corporation or its suppliers and licensors. The Material contains trade secrets and proprietary and confidential information of Intel or its suppliers and licensors. The Material is protected by worldwide copyright and trade secret laws and treaty provisions. No part of the Material may be used, copied, reproduced, modified, published, uploaded, posted, transmitted, distributed, or disclosed in any way without Intel’s prior express written permission.

 No license under any patent, copyright, trade secret or other intellectual property right is granted to or conferred upon you by disclosure or delivery of the Materials, either expressly, by implication, inducement, estoppel or otherwise. Any license under such intellectual property rights must be express and approved by Intel in writing.
 */

#include <va/va.h>             /* libVA */
#include <X11/Xlib.h>
#include <va/va_x11.h>

#include "mixvideolog.h"

#include "mixdisplayx11.h"
#include "mixvideoframe.h"

#include "mixframemanager.h"
#include "mixvideorenderparams.h"
#include "mixvideorenderparams_internal.h"

#include "mixvideoformat.h"
#include "mixvideoformat_vc1.h"
#include "mixvideoformat_h264.h"
#include "mixvideoformat_mp42.h"

#include "mixvideoconfigparamsdec_vc1.h"
#include "mixvideoconfigparamsdec_h264.h"
#include "mixvideoconfigparamsdec_mp42.h"

#include "mixvideoformatenc.h"
#include "mixvideoformatenc_h264.h"
#include "mixvideoformatenc_mpeg4.h"
#include "mixvideoformatenc_preview.h"

#include "mixvideoconfigparamsenc_h264.h"
#include "mixvideoconfigparamsenc_mpeg4.h"
#include "mixvideoconfigparamsenc_preview.h"


#include "mixvideo.h"
#include "mixvideo_private.h"

#define USE_OPAQUE_POINTER 

#ifdef USE_OPAQUE_POINTER
#define MIX_VIDEO_PRIVATE(mix) (MixVideoPrivate *)(mix->context)
#else
#define MIX_VIDEO_PRIVATE(mix) MIX_VIDEO_GET_PRIVATE(mix)
#endif

#define CHECK_INIT(mix, priv) \
	if (!mix) { \
		return MIX_RESULT_NULL_PTR; \
	} \
	if (!MIX_IS_VIDEO(mix)) { \
		LOG_E( "Not MixVideo\n"); \
		return MIX_RESULT_INVALID_PARAM; \
	} \
	priv = MIX_VIDEO_PRIVATE(mix); \
	if (!priv->initialized) { \
		LOG_E( "Not initialized\n"); \
		return MIX_RESULT_NOT_INIT; \
	}

#define CHECK_INIT_CONFIG(mix, priv) \
	CHECK_INIT(mix, priv); \
	if (!priv->configured) { \
		LOG_E( "Not configured\n"); \
		return MIX_RESULT_NOT_CONFIGURED; \
	}

/*
 * default implementation of virtual methods
 */

MIX_RESULT mix_video_get_version_default(MixVideo * mix, guint * major,
		guint * minor);

MIX_RESULT mix_video_initialize_default(MixVideo * mix, MixCodecMode mode,
		MixVideoInitParams * init_params, MixDrmParams * drm_init_params);

MIX_RESULT mix_video_deinitialize_default(MixVideo * mix);

MIX_RESULT mix_video_configure_default(MixVideo * mix,
		MixVideoConfigParams * config_params, MixDrmParams * drm_config_params);

MIX_RESULT mix_video_get_config_default(MixVideo * mix,
		MixVideoConfigParams ** config_params);

MIX_RESULT mix_video_decode_default(MixVideo * mix, MixBuffer * bufin[],
		gint bufincnt, MixVideoDecodeParams * decode_params);

MIX_RESULT mix_video_get_frame_default(MixVideo * mix, MixVideoFrame ** frame);

MIX_RESULT mix_video_release_frame_default(MixVideo * mix,
		MixVideoFrame * frame);

MIX_RESULT mix_video_render_default(MixVideo * mix,
		MixVideoRenderParams * render_params, MixVideoFrame *frame);

MIX_RESULT mix_video_encode_default(MixVideo * mix, MixBuffer * bufin[],
		gint bufincnt, MixIOVec * iovout[], gint iovoutcnt,
		MixVideoEncodeParams * encode_params);

MIX_RESULT mix_video_flush_default(MixVideo * mix);

MIX_RESULT mix_video_eos_default(MixVideo * mix);

MIX_RESULT mix_video_get_state_default(MixVideo * mix, MixState * state);

MIX_RESULT mix_video_get_mixbuffer_default(MixVideo * mix, MixBuffer ** buf);

MIX_RESULT mix_video_release_mixbuffer_default(MixVideo * mix, MixBuffer * buf);

MIX_RESULT mix_video_get_max_coded_buffer_size_default (MixVideo * mix, guint *max_size);


static void mix_video_finalize(GObject * obj);
MIX_RESULT mix_video_configure_decode(MixVideo * mix,
		MixVideoConfigParamsDec * config_params_dec,
		MixDrmParams * drm_config_params);

MIX_RESULT mix_video_configure_encode(MixVideo * mix,
		MixVideoConfigParamsEnc * config_params_enc,
		MixDrmParams * drm_config_params);

G_DEFINE_TYPE( MixVideo, mix_video, G_TYPE_OBJECT);

static void mix_video_init(MixVideo * self) {

	MixVideoPrivate *priv = MIX_VIDEO_GET_PRIVATE(self);

#ifdef USE_OPAQUE_POINTER
	self->context = priv;
#else
	self->context = NULL;
#endif

	/* private structure initialization */

	mix_video_private_initialize(priv);
}

static void mix_video_class_init(MixVideoClass * klass) {
	GObjectClass *gobject_class = (GObjectClass *) klass;

	gobject_class->finalize = mix_video_finalize;

	/* Register and allocate the space the private structure for this object */
	g_type_class_add_private(gobject_class, sizeof(MixVideoPrivate));

	klass->get_version_func = mix_video_get_version_default;
	klass->initialize_func = mix_video_initialize_default;
	klass->deinitialize_func = mix_video_deinitialize_default;
	klass->configure_func = mix_video_configure_default;
	klass->get_config_func = mix_video_get_config_default;
	klass->decode_func = mix_video_decode_default;
	klass->get_frame_func = mix_video_get_frame_default;
	klass->release_frame_func = mix_video_release_frame_default;
	klass->render_func = mix_video_render_default;
	klass->encode_func = mix_video_encode_default;
	klass->flush_func = mix_video_flush_default;
	klass->eos_func = mix_video_eos_default;
	klass->get_state_func = mix_video_get_state_default;
	klass->get_mix_buffer_func = mix_video_get_mixbuffer_default;
	klass->release_mix_buffer_func = mix_video_release_mixbuffer_default;
	klass->get_max_coded_buffer_size_func = mix_video_get_max_coded_buffer_size_default;
}

MixVideo *mix_video_new(void) {

	MixVideo *ret = g_object_new(MIX_TYPE_VIDEO, NULL);

	return ret;
}

void mix_video_finalize(GObject * obj) {

	/* clean up here. */

	MixVideo *mix = MIX_VIDEO(obj);
	mix_video_deinitialize(mix);
}

MixVideo *
mix_video_ref(MixVideo * mix) {
	return (MixVideo *) g_object_ref(G_OBJECT(mix));
}

/* private methods */

#define MIXUNREF(obj, unref) if(obj) { unref(obj); obj = NULL; }

void mix_video_private_initialize(MixVideoPrivate* priv) {
	priv->objlock = NULL;
	priv->initialized = FALSE;
	priv->configured = FALSE;

	/* libVA */
	priv->va_display = NULL;
	priv->va_major_version = -1;
	priv->va_major_version = -1;

	/* mix objects */
	priv->frame_manager = NULL;
	priv->video_format = NULL;
	priv->video_format_enc = NULL; //for encoding
	priv->surface_pool = NULL;
	priv->buffer_pool = NULL;

	priv->codec_mode = MIX_CODEC_MODE_DECODE;
	priv->init_params = NULL;
	priv->drm_params = NULL;
	priv->config_params = NULL;
}

void mix_video_private_cleanup(MixVideoPrivate* priv) {

	VAStatus va_status;

	if (!priv) {
		return;
	}

	if (priv->video_format_enc) {
		mix_videofmtenc_deinitialize(priv->video_format_enc);
	}

	MIXUNREF(priv->frame_manager, mix_framemanager_unref)
	MIXUNREF(priv->video_format, mix_videoformat_unref)
	MIXUNREF(priv->video_format_enc, mix_videoformatenc_unref)
	//for encoding
	MIXUNREF(priv->buffer_pool, mix_bufferpool_unref)
	MIXUNREF(priv->surface_pool, mix_surfacepool_unref)
/*	MIXUNREF(priv->init_params, mix_videoinitparams_unref) */
	MIXUNREF(priv->drm_params, mix_drmparams_unref)
	MIXUNREF(priv->config_params, mix_videoconfigparams_unref)

	/* terminate libVA */
	if (priv->va_display) {
		va_status = vaTerminate(priv->va_display);
		LOG_V( "vaTerminate\n");
		if (va_status != VA_STATUS_SUCCESS) {
			LOG_W( "Failed vaTerminate\n");
		} else {
			priv->va_display = NULL;
		}
	}

	MIXUNREF(priv->init_params, mix_videoinitparams_unref)

	priv->va_major_version = -1;
	priv->va_major_version = -1;

	if (priv->objlock) {
		g_mutex_free(priv->objlock);
		priv->objlock = NULL;
	}

	priv->codec_mode = MIX_CODEC_MODE_DECODE;
	priv->initialized = FALSE;
	priv->configured = FALSE;
}

/* The following methods are defined in MI-X API */

MIX_RESULT mix_video_get_version_default(MixVideo * mix, guint * major,
		guint * minor) {
	if (!mix || !major || !minor) {
		return MIX_RESULT_NULL_PTR;
	}

	if (!MIX_IS_VIDEO(mix)) {
		return MIX_RESULT_INVALID_PARAM;
	}

	*major = MIXVIDEO_CURRENT - MIXVIDEO_AGE;
	*minor = MIXVIDEO_AGE;

	return MIX_RESULT_SUCCESS;
}

MIX_RESULT mix_video_initialize_default(MixVideo * mix, MixCodecMode mode,
		MixVideoInitParams * init_params, MixDrmParams * drm_init_params) {

	MIX_RESULT ret = MIX_RESULT_FAIL;
	MixVideoPrivate *priv = NULL;
	MixDisplay *mix_display = NULL;

	LOG_V( "Begin\n");

	if (!mix || !init_params) {
		LOG_E( "!mix || !init_params\n");
		return MIX_RESULT_NULL_PTR;
	}

	if (mode >= MIX_CODEC_MODE_LAST) {
		LOG_E("mode >= MIX_CODEC_MODE_LAST\n");
		return MIX_RESULT_INVALID_PARAM;
	}

#if 0  //we have encoding support
	/* TODO: We need to support encoding in the future */
	if (mode == MIX_CODEC_MODE_ENCODE) {
		LOG_E("mode == MIX_CODEC_MODE_ENCODE\n");
		return MIX_RESULT_NOTIMPL;
	}
#endif

	if (!MIX_IS_VIDEO(mix)) {
		LOG_E( "!MIX_IS_VIDEO(mix)\n");
		return MIX_RESULT_INVALID_PARAM;
	}

	if (!MIX_IS_VIDEOINITPARAMS(init_params)) {
		LOG_E("!MIX_IS_VIDEOINITPARAMS(init_params\n");
		return MIX_RESULT_INVALID_PARAM;
	}

	priv = MIX_VIDEO_PRIVATE(mix);

	if (priv->initialized) {
		LOG_W( "priv->initialized\n");
		return MIX_RESULT_ALREADY_INIT;
	}

	/*
	 * Init thread before any threads/sync object are used.
	 * TODO: If thread is not supported, what we do?
	 */

	if (!g_thread_supported()) {
		LOG_W("!g_thread_supported()\n");
		g_thread_init(NULL);
	}

	/* create object lock */
	priv->objlock = g_mutex_new();
	if (!priv->objlock) {
		ret = MIX_RESULT_NO_MEMORY;
		LOG_E( "!priv->objlock\n");
		goto cleanup;
	}

	/* clone mode */
	priv->codec_mode = mode;

	/* ref init_params */
	priv->init_params = (MixVideoInitParams *) mix_params_ref(MIX_PARAMS(
			init_params));
	if (!priv->init_params) {
		ret = MIX_RESULT_NO_MEMORY;
		LOG_E( "!priv->init_params\n");
		goto cleanup;
	}

	/* NOTE: we don't do anything with drm_init_params */

	/* libVA initialization */

	{
		VAStatus va_status;
		Display *display = NULL;
		ret = mix_videoinitparams_get_display(priv->init_params, &mix_display);
		if (ret != MIX_RESULT_SUCCESS) {
			LOG_E("Failed to get display 1\n");
			goto cleanup;
		}

		if (MIX_IS_DISPLAYX11(mix_display)) {
			MixDisplayX11 *mix_displayx11 = MIX_DISPLAYX11(mix_display);
			ret = mix_displayx11_get_display(mix_displayx11, &display);
			if (ret != MIX_RESULT_SUCCESS) {
				LOG_E("Failed to get display 2\n");
				goto cleanup;
			}
		} else {

			/* TODO: add support to other MixDisplay type. For now, just return error!*/
			LOG_E("It is not display x11\n");
			ret = MIX_RESULT_FAIL;
			goto cleanup;
		}

		/* Now, we can initialize libVA */
		priv->va_display = vaGetDisplay(display);

		/* Oops! Fail to get VADisplay */
		if (!priv->va_display) {
			ret = MIX_RESULT_FAIL;
			LOG_E("Fail to get VADisplay\n");
			goto cleanup;
		}

		/* Initialize libVA */
		va_status = vaInitialize(priv->va_display, &priv->va_major_version,
				&priv->va_minor_version);

		/* Oops! Fail to initialize libVA */
		if (va_status != VA_STATUS_SUCCESS) {
			ret = MIX_RESULT_FAIL;
			LOG_E("Fail to initialize libVA\n");
			goto cleanup;
		}

		/* TODO: check the version numbers of libVA */

		priv->initialized = TRUE;
		ret = MIX_RESULT_SUCCESS;
	}

	cleanup:

	if (ret != MIX_RESULT_SUCCESS) {
		mix_video_private_cleanup(priv);
	}

	MIXUNREF(mix_display, mix_display_unref);

	LOG_V( "End\n");

	return ret;
}

MIX_RESULT mix_video_deinitialize_default(MixVideo * mix) {

	MixVideoPrivate *priv = NULL;

	LOG_V( "Begin\n");

	CHECK_INIT(mix, priv);

	mix_video_private_cleanup(priv);

	LOG_V( "End\n");
	return MIX_RESULT_SUCCESS;
}

MIX_RESULT mix_video_configure_decode(MixVideo * mix,
		MixVideoConfigParamsDec * config_params_dec, MixDrmParams * drm_config_params) {

	MIX_RESULT ret = MIX_RESULT_FAIL;
	MixVideoPrivate *priv = NULL;
	MixVideoConfigParamsDec *priv_config_params_dec = NULL;

	gchar *mime_type = NULL;
	guint fps_n, fps_d;
	guint bufpoolsize = 0;

	MixFrameOrderMode frame_order_mode = MIX_FRAMEORDER_MODE_DISPLAYORDER;

	LOG_V( "Begin\n");

	CHECK_INIT(mix, priv);

	if (!config_params_dec) {
		LOG_E( "!config_params_dec\n");
		return MIX_RESULT_NULL_PTR;
	}

	if (!MIX_IS_VIDEOCONFIGPARAMSDEC(config_params_dec)) {
		LOG_E("Not a MixVideoConfigParamsDec\n");
		return MIX_RESULT_INVALID_PARAM;
	}

	/* ---------------------- begin lock --------------------- */
	g_mutex_lock(priv->objlock);

	/*
	 * MixVideo has already been configured, it should be
	 * re-configured.
	 *
	 * TODO: Allow MixVideo re-configuration
	 */
	if (priv->configured) {
		ret = MIX_RESULT_SUCCESS;
		LOG_W( "Already configured\n");
		goto cleanup;
	}

	/* Make a copy of config_params */
	priv->config_params = (MixVideoConfigParams *) mix_params_dup(MIX_PARAMS(
			config_params_dec));
	if (!priv->config_params) {
		ret = MIX_RESULT_NO_MEMORY;
		LOG_E("Fail to duplicate config_params\n");
		goto cleanup;
	}

	priv_config_params_dec = (MixVideoConfigParamsDec *)priv->config_params;

	/* Get fps, frame order mode and mime type from config_params */
	ret = mix_videoconfigparamsdec_get_mime_type(priv_config_params_dec, &mime_type);
	if (ret != MIX_RESULT_SUCCESS) {
		LOG_E("Failed to get mime type\n");
		goto cleanup;
	}

	LOG_I( "mime : %s\n", mime_type);

#ifdef MIX_LOG_ENABLE
	if (g_strcmp0(mime_type, "video/x-wmv") == 0) {

		LOG_I( "mime : video/x-wmv\n");
		if (MIX_IS_VIDEOCONFIGPARAMSDEC_VC1(priv_config_params_dec)) {
			LOG_I( "VC1 config_param\n");
		} else {
			LOG_E("Not VC1 config_param\n");
		}
	}
#endif

	ret = mix_videoconfigparamsdec_get_frame_order_mode(priv_config_params_dec,
			&frame_order_mode);
	if (ret != MIX_RESULT_SUCCESS) {
		LOG_E("Failed to frame order mode\n");
		goto cleanup;
	}

	ret = mix_videoconfigparamsdec_get_frame_rate(priv_config_params_dec, &fps_n,
			&fps_d);
	if (ret != MIX_RESULT_SUCCESS) {
		LOG_E("Failed to get frame rate\n");
		goto cleanup;
	}

	if (!fps_n) {
		ret = MIX_RESULT_FAIL;
		LOG_E( "fps_n is 0\n");
		goto cleanup;
	}

	ret = mix_videoconfigparamsdec_get_buffer_pool_size(priv_config_params_dec,
			&bufpoolsize);
	if (ret != MIX_RESULT_SUCCESS) {
		LOG_E("Failed to get buffer pool size\n");
		goto cleanup;
	}

	/* create frame manager */
	priv->frame_manager = mix_framemanager_new();
	if (!priv->frame_manager) {
		ret = MIX_RESULT_NO_MEMORY;
		LOG_E("Failed to create frame manager\n");
		goto cleanup;
	}

	/* initialize frame manager */

	if (g_strcmp0(mime_type, "video/x-wmv") == 0 || g_strcmp0(mime_type,
			"video/mpeg") == 0 || g_strcmp0(mime_type, "video/x-divx") == 0) {
		ret = mix_framemanager_initialize(priv->frame_manager,
				frame_order_mode, fps_n, fps_d, FALSE);
	} else {
		ret = mix_framemanager_initialize(priv->frame_manager,
				frame_order_mode, fps_n, fps_d, TRUE);
	}

	if (ret != MIX_RESULT_SUCCESS) {
		LOG_E("Failed to initialize frame manager\n");
		goto cleanup;
	}

	/* create buffer pool */
	priv->buffer_pool = mix_bufferpool_new();
	if (!priv->buffer_pool) {
		ret = MIX_RESULT_NO_MEMORY;
		LOG_E("Failed to create buffer pool\n");
		goto cleanup;
	}

	ret = mix_bufferpool_initialize(priv->buffer_pool, bufpoolsize);
	if (ret != MIX_RESULT_SUCCESS) {
		LOG_E("Failed to initialize buffer pool\n");
		goto cleanup;
	}

	/* Finally, we can create MixVideoFormat */
	/* What type of MixVideoFormat we need create? */

	if (g_strcmp0(mime_type, "video/x-wmv") == 0
			&& MIX_IS_VIDEOCONFIGPARAMSDEC_VC1(priv_config_params_dec)) {

		MixVideoFormat_VC1 *video_format = mix_videoformat_vc1_new();
		if (!video_format) {
			ret = MIX_RESULT_NO_MEMORY;
			LOG_E("Failed to create VC-1 video format\n");
			goto cleanup;
		}

		/* TODO: work specific to VC-1 */

		priv->video_format = MIX_VIDEOFORMAT(video_format);

	} else if (g_strcmp0(mime_type, "video/x-h264") == 0
			&& MIX_IS_VIDEOCONFIGPARAMSDEC_H264(priv_config_params_dec)) {

		MixVideoFormat_H264 *video_format = mix_videoformat_h264_new();
		if (!video_format) {
			ret = MIX_RESULT_NO_MEMORY;
			LOG_E("Failed to create H.264 video format\n");
			goto cleanup;
		}

		/* TODO: work specific to H.264 */

		priv->video_format = MIX_VIDEOFORMAT(video_format);

	} else if (g_strcmp0(mime_type, "video/mpeg") == 0 || g_strcmp0(mime_type,
			"video/x-divx") == 0) {

		guint version = 0;

		/* Is this mpeg4:2 ? */
		if (g_strcmp0(mime_type, "video/mpeg") == 0) {

			/*
			 *  we don't support mpeg other than mpeg verion 4
			 */
			if (!MIX_IS_VIDEOCONFIGPARAMSDEC_MP42(priv_config_params_dec)) {
				ret = MIX_RESULT_NOT_SUPPORTED;
				goto cleanup;
			}

			/* what is the mpeg version ? */
			ret = mix_videoconfigparamsdec_mp42_get_mpegversion(
					MIX_VIDEOCONFIGPARAMSDEC_MP42(priv_config_params_dec), &version);
			if (ret != MIX_RESULT_SUCCESS) {
				LOG_E("Failed to get mpeg version\n");
				goto cleanup;
			}

			/* if it is not MPEG4 */
			if (version != 4) {
				ret = MIX_RESULT_NOT_SUPPORTED;
				goto cleanup;
			}

		} else {

			/* config_param shall be MixVideoConfigParamsDecMP42 */
			if (!MIX_IS_VIDEOCONFIGPARAMSDEC_MP42(priv_config_params_dec)) {
				ret = MIX_RESULT_NOT_SUPPORTED;
				goto cleanup;
			}

			/* what is the divx version ? */
			ret = mix_videoconfigparamsdec_mp42_get_divxversion(
					MIX_VIDEOCONFIGPARAMSDEC_MP42(priv_config_params_dec), &version);
			if (ret != MIX_RESULT_SUCCESS) {
				LOG_E("Failed to get divx version\n");
				goto cleanup;
			}

			/* if it is not divx 4 or 5 */
			if (version != 4 && version != 5) {
				ret = MIX_RESULT_NOT_SUPPORTED;
				goto cleanup;
			}
		}

		MixVideoFormat_MP42 *video_format = mix_videoformat_mp42_new();
		if (!video_format) {
			ret = MIX_RESULT_NO_MEMORY;
			LOG_E("Failed to create MPEG-4:2 video format\n");
			goto cleanup;
		}

		/* TODO: work specific to MPEG-4:2 */
		priv->video_format = MIX_VIDEOFORMAT(video_format);

	} else {

		/* Oops! A format we don't know */

		ret = MIX_RESULT_FAIL;
		LOG_E("Unknown format, we can't handle it\n");
		goto cleanup;
	}

	/* initialize MixVideoFormat */
	ret = mix_videofmt_initialize(priv->video_format, priv_config_params_dec,
			priv->frame_manager, priv->buffer_pool, &priv->surface_pool,
			priv->va_display);

	if (ret != MIX_RESULT_SUCCESS) {
		LOG_E("Failed initialize video format\n");
		goto cleanup;
	}

	mix_surfacepool_ref(priv->surface_pool);

	/* decide MixVideoFormat from mime_type*/

	priv->configured = TRUE;
	ret = MIX_RESULT_SUCCESS;

	cleanup:

	if (ret != MIX_RESULT_SUCCESS) {
		MIXUNREF(priv->config_params, mix_videoconfigparams_unref);
		MIXUNREF(priv->frame_manager, mix_framemanager_unref);
		MIXUNREF(priv->buffer_pool, mix_bufferpool_unref);
		MIXUNREF(priv->video_format, mix_videoformat_unref);
	}

	if (mime_type) {
		g_free(mime_type);
	}

	g_mutex_unlock(priv->objlock);
	/* ---------------------- end lock --------------------- */

	LOG_V( "End\n");

	return ret;
}

MIX_RESULT mix_video_configure_encode(MixVideo * mix,
		MixVideoConfigParamsEnc * config_params_enc,
		MixDrmParams * drm_config_params) {

	MIX_RESULT ret = MIX_RESULT_FAIL;
	MixVideoPrivate *priv = NULL;
	MixVideoConfigParamsEnc *priv_config_params_enc = NULL;


	gchar *mime_type = NULL;
	MixEncodeTargetFormat encode_format = MIX_ENCODE_TARGET_FORMAT_H264;
	guint bufpoolsize = 0;

	MixFrameOrderMode frame_order_mode = MIX_FRAMEORDER_MODE_DECODEORDER;


	LOG_V( "Begin\n");

	CHECK_INIT(mix, priv);

	if (!config_params_enc) {
		LOG_E("!config_params_enc\n");
		return MIX_RESULT_NULL_PTR;
	}
	if (!MIX_IS_VIDEOCONFIGPARAMSENC(config_params_enc)) {
		LOG_E("Not a MixVideoConfigParams\n");
		return MIX_RESULT_INVALID_PARAM;
	}

	/* ---------------------- begin lock --------------------- */
	g_mutex_lock(priv->objlock);

	/*
	 * MixVideo has already been configured, it should be
	 * re-configured.
	 *
	 * TODO: Allow MixVideo re-configuration
	 */
	if (priv->configured) {
		ret = MIX_RESULT_SUCCESS;
		LOG_E( "Already configured\n");
		goto cleanup;
	}

	/* Make a copy of config_params */
	priv->config_params = (MixVideoConfigParams *) mix_params_dup(
			MIX_PARAMS(config_params_enc));
	if (!priv->config_params) {
		ret = MIX_RESULT_NO_MEMORY;
		LOG_E("Fail to duplicate config_params\n");
		goto cleanup;
	}

	priv_config_params_enc = (MixVideoConfigParamsEnc *)priv->config_params;

	/* Get fps, frame order mode and mime type from config_params */
	ret = mix_videoconfigparamsenc_get_mime_type(priv_config_params_enc,
			&mime_type);
	if (ret != MIX_RESULT_SUCCESS) {
		LOG_E("Failed to get mime type\n");
		goto cleanup;
	}

	LOG_I( "mime : %s\n", mime_type);

	ret = mix_videoconfigparamsenc_get_encode_format(priv_config_params_enc,
			&encode_format);
	if (ret != MIX_RESULT_SUCCESS) {
		LOG_E("Failed to get target format\n");
		goto cleanup;
	}

	LOG_I( "encode_format : %d\n",
			encode_format);

	ret = mix_videoconfigparamsenc_get_buffer_pool_size(
			priv_config_params_enc, &bufpoolsize);
	if (ret != MIX_RESULT_SUCCESS) {
		LOG_E("Failed to get buffer pool size\n");
		goto cleanup;
	}

	/* create frame manager */
	priv->frame_manager = mix_framemanager_new();
	if (!priv->frame_manager) {
		ret = MIX_RESULT_NO_MEMORY;
		LOG_E("Failed to create frame manager\n");
		goto cleanup;
	}

	/* initialize frame manager */
	/* frame rate can be any value for encoding. */
	ret = mix_framemanager_initialize(priv->frame_manager, frame_order_mode,
			1, 1, FALSE);

	if (ret != MIX_RESULT_SUCCESS) {
		LOG_E("Failed to initialize frame manager\n");
		goto cleanup;
	}

	/* create buffer pool */
	priv->buffer_pool = mix_bufferpool_new();
	if (!priv->buffer_pool) {
		ret = MIX_RESULT_NO_MEMORY;
		LOG_E("Failed to create buffer pool\n");
		goto cleanup;
	}

	ret = mix_bufferpool_initialize(priv->buffer_pool, bufpoolsize);
	if (ret != MIX_RESULT_SUCCESS) {
		LOG_E("Failed to initialize buffer pool\n");
		goto cleanup;
	}

	/* Finally, we can create MixVideoFormatEnc */
	/* What type of MixVideoFormatEnc we need create? */

	if (encode_format == MIX_ENCODE_TARGET_FORMAT_H264
			&& MIX_IS_VIDEOCONFIGPARAMSENC_H264(priv_config_params_enc)) {

		MixVideoFormatEnc_H264 *video_format_enc =
				mix_videoformatenc_h264_new();
		if (!video_format_enc) {
			ret = MIX_RESULT_NO_MEMORY;
			LOG_E("mix_video_configure_encode: Failed to create h264 video enc format\n");
			goto cleanup;
		}

		/* TODO: work specific to h264 encode */

		priv->video_format_enc = MIX_VIDEOFORMATENC(video_format_enc);

	}
    else if (encode_format == MIX_ENCODE_TARGET_FORMAT_MPEG4
            && MIX_IS_VIDEOCONFIGPARAMSENC_MPEG4(priv_config_params_enc)) {

        MixVideoFormatEnc_MPEG4 *video_format_enc = mix_videoformatenc_mpeg4_new();
        if (!video_format_enc) {
            ret = MIX_RESULT_NO_MEMORY;
            LOG_E("mix_video_configure_encode: Failed to create mpeg-4:2 video format\n");
            goto cleanup;
        }

		/* TODO: work specific to mpeg4 */

		priv->video_format_enc = MIX_VIDEOFORMATENC(video_format_enc);

	}
    else if (encode_format == MIX_ENCODE_TARGET_FORMAT_PREVIEW
            && MIX_IS_VIDEOCONFIGPARAMSENC_PREVIEW(priv_config_params_enc)) {

        MixVideoFormatEnc_Preview *video_format_enc = mix_videoformatenc_preview_new();
        if (!video_format_enc) {
            ret = MIX_RESULT_NO_MEMORY;
            LOG_E( "mix_video_configure_encode: Failed to create preview video format\n");
            goto cleanup;
        }

		priv->video_format_enc = MIX_VIDEOFORMATENC(video_format_enc);

	}
	else {

		/*unsupported format */
		ret = MIX_RESULT_NOT_SUPPORTED;
		LOG_E("Unknown format, we can't handle it\n");
		goto cleanup;
	}

	/* initialize MixVideoEncFormat */
	ret = mix_videofmtenc_initialize(priv->video_format_enc,
            priv_config_params_enc, priv->frame_manager, NULL, &priv->surface_pool,
            priv->va_display);

	if (ret != MIX_RESULT_SUCCESS) {
		LOG_E("Failed initialize video format\n");
		goto cleanup;
	}

	mix_surfacepool_ref(priv->surface_pool);

	priv->configured = TRUE;
	ret = MIX_RESULT_SUCCESS;

	cleanup:

	if (ret != MIX_RESULT_SUCCESS) {
		MIXUNREF(priv->frame_manager, mix_framemanager_unref);
		MIXUNREF(priv->config_params, mix_videoconfigparams_unref);
		MIXUNREF(priv->buffer_pool, mix_bufferpool_unref);
		MIXUNREF(priv->video_format_enc, mix_videoformatenc_unref);
	}

	if (mime_type) {
		g_free(mime_type);
	}

	g_mutex_unlock(priv->objlock);
	/* ---------------------- end lock --------------------- */

	LOG_V( "End\n");

	return ret;
}

MIX_RESULT mix_video_configure_default(MixVideo * mix,
		MixVideoConfigParams * config_params,
		MixDrmParams * drm_config_params) {

	MIX_RESULT ret = MIX_RESULT_FAIL;
	MixVideoPrivate *priv = NULL;

	LOG_V( "Begin\n");
	
	CHECK_INIT(mix, priv);
	if(!config_params) {
		LOG_E("!config_params\n");
		return MIX_RESULT_NULL_PTR;
	}

	/*Decoder mode or Encoder mode*/
	if (priv->codec_mode == MIX_CODEC_MODE_DECODE && MIX_IS_VIDEOCONFIGPARAMSDEC(config_params)) {
		ret = mix_video_configure_decode(mix, (MixVideoConfigParamsDec*)config_params, NULL);
	} else if (priv->codec_mode == MIX_CODEC_MODE_ENCODE && MIX_IS_VIDEOCONFIGPARAMSENC(config_params)) {
		ret = mix_video_configure_encode(mix, (MixVideoConfigParamsEnc*)config_params, NULL);
	} else {
		LOG_E("Codec mode not supported\n");
	}

	LOG_V( "end\n");

	return ret;
}

MIX_RESULT mix_video_get_config_default(MixVideo * mix,
		MixVideoConfigParams ** config_params) {

	MIX_RESULT ret = MIX_RESULT_FAIL;
	MixVideoPrivate *priv = NULL;

	CHECK_INIT_CONFIG(mix, priv);

	if (!config_params) {
		LOG_E( "!config_params\n");
		return MIX_RESULT_NULL_PTR;
	}

	/* ---------------------- begin lock --------------------- */
	g_mutex_lock(priv->objlock);

	*config_params = MIX_VIDEOCONFIGPARAMS(mix_params_dup(MIX_PARAMS(priv->config_params)));
	if(!*config_params) {
		ret = MIX_RESULT_NO_MEMORY;
		LOG_E("Failed to duplicate MixVideoConfigParams\n");
		goto cleanup;
	}

	cleanup:

	/* ---------------------- end lock --------------------- */
	g_mutex_unlock(priv->objlock);

	LOG_V( "End\n");

	return ret;

}

MIX_RESULT mix_video_decode_default(MixVideo * mix, MixBuffer * bufin[],
		gint bufincnt, MixVideoDecodeParams * decode_params) {

	MIX_RESULT ret = MIX_RESULT_FAIL;
	MixVideoPrivate *priv = NULL;

	LOG_V( "Begin\n");

	CHECK_INIT_CONFIG(mix, priv);
	if(!bufin || !bufincnt || !decode_params) {
		LOG_E( "!bufin || !bufincnt || !decode_params\n");
		return MIX_RESULT_NULL_PTR;
	}

	//First check that we have surfaces available for decode
	ret = mix_surfacepool_check_available(priv->surface_pool);

	if (ret == MIX_RESULT_POOLEMPTY) {
		LOG_I( "Out of surface\n");
		return MIX_RESULT_OUTOFSURFACES;
	}

	g_mutex_lock(priv->objlock);

	ret = mix_videofmt_decode(priv->video_format, bufin, bufincnt, decode_params);

	g_mutex_unlock(priv->objlock);

	LOG_V( "End\n");

	return ret;
}

MIX_RESULT mix_video_get_frame_default(MixVideo * mix, MixVideoFrame ** frame) {

	LOG_V( "Begin\n");

	MIX_RESULT ret = MIX_RESULT_FAIL;
	MixVideoPrivate *priv = NULL;
	
	CHECK_INIT_CONFIG(mix, priv);

	if (!frame) {
		LOG_E( "!frame\n");
		return MIX_RESULT_NULL_PTR;
	}

	/* ---------------------- begin lock --------------------- */
	g_mutex_lock(priv->objlock);

	LOG_V("Calling frame manager dequeue\n");

	ret = mix_framemanager_dequeue(priv->frame_manager, frame);

	/* ---------------------- end lock --------------------- */
	g_mutex_unlock(priv->objlock);

	LOG_V( "End\n");

	return ret;
}

MIX_RESULT mix_video_release_frame_default(MixVideo * mix,
		MixVideoFrame * frame) {

	LOG_V( "Begin\n");

	MIX_RESULT ret = MIX_RESULT_FAIL;
	MixVideoPrivate *priv = NULL;

	CHECK_INIT_CONFIG(mix, priv);

	if (!frame) {
		LOG_E( "!frame\n");
		return MIX_RESULT_NULL_PTR;
	}

	/*
	 * We don't need lock here. MixVideoFrame has lock to
	 * protect itself.
	 */
#if 0
	/* ---------------------- begin lock --------------------- */
	g_mutex_lock(priv->objlock);
#endif

	LOG_I("Releasing reference frame %x\n", (guint) frame);
	mix_videoframe_unref(frame);

	ret = MIX_RESULT_SUCCESS;

#if 0
	/* ---------------------- end lock --------------------- */
	g_mutex_unlock(priv->objlock);
#endif

	LOG_V( "End\n");

	return ret;

}

MIX_RESULT mix_video_render_default(MixVideo * mix,
		MixVideoRenderParams * render_params, MixVideoFrame *frame) {

	LOG_V( "Begin\n");

	MIX_RESULT ret = MIX_RESULT_FAIL;
	MixVideoPrivate *priv = NULL;

	MixDisplay *mix_display = NULL;
	MixDisplayX11 *mix_display_x11 = NULL;

	Display *display = NULL;
	Drawable drawable = 0;

	MixRect src_rect, dst_rect;

	VARectangle *va_cliprects = NULL;
	guint number_of_cliprects = 0;

	/* VASurfaceID va_surface_id; */
	gulong va_surface_id;
	VAStatus va_status;

	CHECK_INIT_CONFIG(mix, priv);

	if (!render_params || !frame) {
		LOG_E( "!render_params || !frame\n");
		return MIX_RESULT_NULL_PTR;
	}

	/* Is this render param valid? */
	if (!MIX_IS_VIDEORENDERPARAMS(render_params)) {
		LOG_E("Not MixVideoRenderParams\n");
		return MIX_RESULT_INVALID_PARAM;
	}

	/*
	 * We don't need lock here. priv->va_display may be the only variable
	 * seems need to be protected. But, priv->va_display is initialized
	 * when mixvideo object is initialized, and it keeps
	 * the same value thoughout the life of mixvideo.
	 */
#if 0
	/* ---------------------- begin lock --------------------- */
	g_mutex_lock(priv->objlock);
#endif

	/* get MixDisplay prop from render param */
	ret = mix_videorenderparams_get_display(render_params, &mix_display);
	if (ret != MIX_RESULT_SUCCESS) {
		LOG_E("Failed to get mix_display\n");
		goto cleanup;
	}

	/* Is this MixDisplayX11 ? */
	/* TODO: we shall also support MixDisplay other than MixDisplayX11 */
	if (!MIX_IS_DISPLAYX11(mix_display)) {
		ret = MIX_RESULT_INVALID_PARAM;
		LOG_E( "Not MixDisplayX11\n");
		goto cleanup;
	}

	/* cast MixDisplay to MixDisplayX11 */
	mix_display_x11 = MIX_DISPLAYX11(mix_display);

	/* Get Drawable */
	ret = mix_displayx11_get_drawable(mix_display_x11, &drawable);
	if (ret != MIX_RESULT_SUCCESS) {
		LOG_E( "Failed to get drawable\n");
		goto cleanup;
	}

	/* Get Display */
	ret = mix_displayx11_get_display(mix_display_x11, &display);
	if (ret != MIX_RESULT_SUCCESS) {
		LOG_E( "Failed to get display\n");
		goto cleanup;
	}

	/* get src_rect */
	ret = mix_videorenderparams_get_src_rect(render_params, &src_rect);
	if (ret != MIX_RESULT_SUCCESS) {
		LOG_E("Failed to get SOURCE src_rect\n");
		goto cleanup;
	}

	/* get dst_rect */
	ret = mix_videorenderparams_get_dest_rect(render_params, &dst_rect);
	if (ret != MIX_RESULT_SUCCESS) {
		LOG_E( "Failed to get dst_rect\n");
		goto cleanup;
	}

	/* get va_cliprects */
	ret = mix_videorenderparams_get_cliprects_internal(render_params,
			&va_cliprects, &number_of_cliprects);
	if (ret != MIX_RESULT_SUCCESS) {
		LOG_E("Failed to get va_cliprects\n");
		goto cleanup;
	}

	/* get surface id from frame */
	ret = mix_videoframe_get_frame_id(frame, &va_surface_id);
	if (ret != MIX_RESULT_SUCCESS) {
		LOG_E("Failed to get va_surface_id\n");
		goto cleanup;
	}
	guint64 timestamp = 0;
	mix_videoframe_get_timestamp(frame, &timestamp);
	LOG_V( "Displaying surface ID %d, timestamp %"G_GINT64_FORMAT"\n", (int)va_surface_id, timestamp);

	guint32 frame_structure = 0;
	mix_videoframe_get_frame_structure(frame, &frame_structure);
	/* TODO: the last param of vaPutSurface is de-interlacing flags,
	 what is value shall be*/
	va_status = vaPutSurface(priv->va_display, (VASurfaceID) va_surface_id,
			drawable, src_rect.x, src_rect.y, src_rect.width, src_rect.height,
			dst_rect.x, dst_rect.y, dst_rect.width, dst_rect.height,
			va_cliprects, number_of_cliprects, frame_structure);

	if (va_status != VA_STATUS_SUCCESS) {
		ret = MIX_RESULT_FAIL;
		LOG_E("Failed vaPutSurface() : va_status = %d\n", va_status);
		goto cleanup;
	}

	/* TODO: Is this only for X11? */
	XSync(display, FALSE);

	ret = MIX_RESULT_SUCCESS;

	cleanup:

	MIXUNREF(mix_display, mix_display_unref)
	/*	MIXUNREF(render_params, mix_videorenderparams_unref)*/

#if 0
	/* ---------------------- end lock --------------------- */
	g_mutex_unlock(priv->objlock);
#endif

	LOG_V( "End\n");

	return ret;

}

MIX_RESULT mix_video_encode_default(MixVideo * mix, MixBuffer * bufin[],
		gint bufincnt, MixIOVec * iovout[], gint iovoutcnt,
		MixVideoEncodeParams * encode_params) {

	MIX_RESULT ret = MIX_RESULT_FAIL;
	MixVideoPrivate *priv = NULL;

	LOG_V( "Begin\n");

	CHECK_INIT_CONFIG(mix, priv);
	if(!bufin || !bufincnt) { //we won't check encode_params here, it's just a placeholder
		LOG_E( "!bufin || !bufincnt\n");
		return MIX_RESULT_NULL_PTR;
	}

	g_mutex_lock(priv->objlock);

	ret = mix_videofmtenc_encode(priv->video_format_enc, bufin, bufincnt,
			iovout, iovoutcnt, encode_params);

	g_mutex_unlock(priv->objlock);

	LOG_V( "End\n");
	return ret;
}

MIX_RESULT mix_video_flush_default(MixVideo * mix) {

	MIX_RESULT ret = MIX_RESULT_FAIL;
	MixVideoPrivate *priv = NULL;

	LOG_V( "Begin\n");

	CHECK_INIT_CONFIG(mix, priv);

	/* ---------------------- begin lock --------------------- */
	g_mutex_lock(priv->objlock);

	if (priv->codec_mode == MIX_CODEC_MODE_DECODE && priv->video_format != NULL) {
		ret = mix_videofmt_flush(priv->video_format);

		ret = mix_framemanager_flush(priv->frame_manager);
	} else if (priv->codec_mode == MIX_CODEC_MODE_ENCODE
			&& priv->video_format_enc != NULL) {
		/*No framemanager for encoder now*/
		ret = mix_videofmtenc_flush(priv->video_format_enc);
	} else {
		g_mutex_unlock(priv->objlock);
		LOG_E("Invalid video_format/video_format_enc Pointer\n");
		return MIX_RESULT_NULL_PTR;
	}

	/* ---------------------- end lock --------------------- */
	g_mutex_unlock(priv->objlock);

	LOG_V( "End\n");

	return ret;

}

MIX_RESULT mix_video_eos_default(MixVideo * mix) {

	MIX_RESULT ret = MIX_RESULT_FAIL;
	MixVideoPrivate *priv = NULL;

	LOG_V( "Begin\n");
	
	CHECK_INIT_CONFIG(mix, priv);

	/* ---------------------- begin lock --------------------- */
	g_mutex_lock(priv->objlock);

	if (priv->codec_mode == MIX_CODEC_MODE_DECODE && priv->video_format != NULL) {
		ret = mix_videofmt_eos(priv->video_format);

		/* frame manager will set EOS flag to be TRUE */
		ret = mix_framemanager_eos(priv->frame_manager);
	} else if (priv->codec_mode == MIX_CODEC_MODE_ENCODE
			&& priv->video_format_enc != NULL) {
		/*No framemanager now*/
		ret = mix_videofmtenc_eos(priv->video_format_enc);
	} else {
		g_mutex_unlock(priv->objlock);
		LOG_E("Invalid video_format/video_format_enc Pointer\n");
		return MIX_RESULT_NULL_PTR;
	}

	/* ---------------------- end lock --------------------- */
	g_mutex_unlock(priv->objlock);

	LOG_V( "End\n");

	return ret;
}

MIX_RESULT mix_video_get_state_default(MixVideo * mix, MixState * state) {

	MixVideoPrivate *priv = NULL;

	LOG_V( "Begin\n");

	CHECK_INIT_CONFIG(mix, priv);

	if (!state) {
		LOG_E( "!state\n");
		return MIX_RESULT_NULL_PTR;
	}

	*state = MIX_STATE_CONFIGURED;

	LOG_V( "End\n");

	return MIX_RESULT_SUCCESS;
}

MIX_RESULT mix_video_get_mixbuffer_default(MixVideo * mix, MixBuffer ** buf) {

	MIX_RESULT ret = MIX_RESULT_FAIL;
	MixVideoPrivate *priv = NULL;

	LOG_V( "Begin\n");

	CHECK_INIT_CONFIG(mix, priv);

	if (!buf) {
		LOG_E( "!buf\n");
		return MIX_RESULT_INVALID_PARAM;
	}

	/* ---------------------- begin lock --------------------- */
	g_mutex_lock(priv->objlock);

	ret = mix_bufferpool_get(priv->buffer_pool, buf);

	/* ---------------------- end lock --------------------- */
	g_mutex_unlock(priv->objlock);

	LOG_V( "End ret = 0x%x\n", ret);

	return ret;

}

MIX_RESULT mix_video_release_mixbuffer_default(MixVideo * mix, MixBuffer * buf) {

	MIX_RESULT ret = MIX_RESULT_FAIL;
	MixVideoPrivate *priv = NULL;

	LOG_V( "Begin\n");

	CHECK_INIT_CONFIG(mix, priv);

	if (!buf) {
		LOG_E( "!buf\n");
		return MIX_RESULT_INVALID_PARAM;
	}

	/* ---------------------- begin lock --------------------- */
	g_mutex_lock(priv->objlock);

	mix_buffer_unref(buf);

	/* ---------------------- end lock --------------------- */
	g_mutex_unlock(priv->objlock);

	LOG_V( "End\n");
	return ret;

}

MIX_RESULT mix_video_get_max_coded_buffer_size_default (MixVideo * mix, guint *max_size)
{
      MIX_RESULT ret = MIX_RESULT_FAIL;
	MixVideoPrivate *priv = NULL;

	LOG_V( "Begin\n");

	if (!mix || !max_size) /* TODO: add other parameter NULL checking */
	{
		LOG_E( "!mix || !bufsize\n");
		return MIX_RESULT_NULL_PTR;
	}

	CHECK_INIT_CONFIG(mix, priv);

	g_mutex_lock(priv->objlock);

	ret = mix_videofmtenc_get_max_coded_buffer_size(priv->video_format_enc, max_size);

	g_mutex_unlock(priv->objlock);

	LOG_V( "End\n");
	return ret;
}

/*
 * API functions
 */

#define CHECK_AND_GET_MIX_CLASS(mix, klass) \
	if (!mix) { \
		return MIX_RESULT_NULL_PTR; \
	} \
	if (!MIX_IS_VIDEO(mix)) { \
		LOG_E( "Not MixVideo\n"); \
		return MIX_RESULT_INVALID_PARAM; \
	} \
	klass = MIX_VIDEO_GET_CLASS(mix);


MIX_RESULT mix_video_get_version(MixVideo * mix, guint * major, guint * minor) {

	MixVideoClass *klass = NULL;
	CHECK_AND_GET_MIX_CLASS(mix, klass);

	if (klass->get_version_func) {
		return klass->get_version_func(mix, major, minor);
	}
	return MIX_RESULT_NOTIMPL;

}

MIX_RESULT mix_video_initialize(MixVideo * mix, MixCodecMode mode,
		MixVideoInitParams * init_params, MixDrmParams * drm_init_params) {

	MixVideoClass *klass = NULL;
	CHECK_AND_GET_MIX_CLASS(mix, klass);

	if (klass->initialize_func) {
		return klass->initialize_func(mix, mode, init_params, drm_init_params);
	}
	return MIX_RESULT_NOTIMPL;

}

MIX_RESULT mix_video_deinitialize(MixVideo * mix) {

	MixVideoClass *klass = NULL;
	CHECK_AND_GET_MIX_CLASS(mix, klass);

	if (klass->deinitialize_func) {
		return klass->deinitialize_func(mix);
	}
	return MIX_RESULT_NOTIMPL;
}

MIX_RESULT mix_video_configure(MixVideo * mix,
		MixVideoConfigParams * config_params,
		MixDrmParams * drm_config_params) {

	MixVideoClass *klass = NULL;
	CHECK_AND_GET_MIX_CLASS(mix, klass);

	if (klass->configure_func) {
		return klass->configure_func(mix, config_params, drm_config_params);
	}
	return MIX_RESULT_NOTIMPL;
}

MIX_RESULT mix_video_get_config(MixVideo * mix,
		MixVideoConfigParams ** config_params_dec) {

	MixVideoClass *klass = NULL;
	CHECK_AND_GET_MIX_CLASS(mix, klass);

	if (klass->get_config_func) {
		return klass->get_config_func(mix, config_params_dec);
	}
	return MIX_RESULT_NOTIMPL;

}

MIX_RESULT mix_video_decode(MixVideo * mix, MixBuffer * bufin[], gint bufincnt,
		MixVideoDecodeParams * decode_params) {

	MixVideoClass *klass = NULL;
	CHECK_AND_GET_MIX_CLASS(mix, klass);

	if (klass->decode_func) {
		return klass->decode_func(mix, bufin, bufincnt, 
				decode_params);
	}
	return MIX_RESULT_NOTIMPL;

}

MIX_RESULT mix_video_get_frame(MixVideo * mix, MixVideoFrame ** frame) {

	MixVideoClass *klass = NULL;
	CHECK_AND_GET_MIX_CLASS(mix, klass);

	if (klass->get_frame_func) {
		return klass->get_frame_func(mix, frame);
	}
	return MIX_RESULT_NOTIMPL;

}

MIX_RESULT mix_video_release_frame(MixVideo * mix, MixVideoFrame * frame) {

	MixVideoClass *klass = NULL;
	CHECK_AND_GET_MIX_CLASS(mix, klass);

	if (klass->release_frame_func) {
		return klass->release_frame_func(mix, frame);
	}
	return MIX_RESULT_NOTIMPL;
}

MIX_RESULT mix_video_render(MixVideo * mix,
		MixVideoRenderParams * render_params, MixVideoFrame *frame) {

	MixVideoClass *klass = NULL;
	CHECK_AND_GET_MIX_CLASS(mix, klass);

	if (klass->render_func) {
		return klass->render_func(mix, render_params, frame);
	}
	return MIX_RESULT_NOTIMPL;

}

MIX_RESULT mix_video_encode(MixVideo * mix, MixBuffer * bufin[], gint bufincnt,
		MixIOVec * iovout[], gint iovoutcnt,
		MixVideoEncodeParams * encode_params) {

	MixVideoClass *klass = NULL;
	CHECK_AND_GET_MIX_CLASS(mix, klass);

	if (klass->encode_func) {
		return klass->encode_func(mix, bufin, bufincnt, iovout, iovoutcnt,
				encode_params);
	}
	return MIX_RESULT_NOTIMPL;

}

MIX_RESULT mix_video_flush(MixVideo * mix) {

	MixVideoClass *klass = NULL;
	CHECK_AND_GET_MIX_CLASS(mix, klass);

	if (klass->flush_func) {
		return klass->flush_func(mix);
	}
	return MIX_RESULT_NOTIMPL;
}

MIX_RESULT mix_video_eos(MixVideo * mix) {

	MixVideoClass *klass = NULL;
	CHECK_AND_GET_MIX_CLASS(mix, klass);

	if (klass->eos_func) {
		return klass->eos_func(mix);
	}
	return MIX_RESULT_NOTIMPL;
}

MIX_RESULT mix_video_get_state(MixVideo * mix, MixState * state) {

	MixVideoClass *klass = NULL;
	CHECK_AND_GET_MIX_CLASS(mix, klass);

	if (klass->get_state_func) {
		return klass->get_state_func(mix, state);
	}
	return MIX_RESULT_NOTIMPL;
}

MIX_RESULT mix_video_get_mixbuffer(MixVideo * mix, MixBuffer ** buf) {

	MixVideoClass *klass = NULL;
	CHECK_AND_GET_MIX_CLASS(mix, klass);

	if (klass->get_mix_buffer_func) {
		return klass->get_mix_buffer_func(mix, buf);
	}
	return MIX_RESULT_NOTIMPL;
}

MIX_RESULT mix_video_release_mixbuffer(MixVideo * mix, MixBuffer * buf) {

	MixVideoClass *klass = NULL;
	CHECK_AND_GET_MIX_CLASS(mix, klass);

	if (klass->release_mix_buffer_func) {
		return klass->release_mix_buffer_func(mix, buf);
	}
	return MIX_RESULT_NOTIMPL;
}

MIX_RESULT mix_video_get_max_coded_buffer_size(MixVideo * mix, guint *bufsize) {

	MixVideoClass *klass = MIX_VIDEO_GET_CLASS(mix);

	if (klass->get_max_coded_buffer_size_func) {
		return klass->get_max_coded_buffer_size_func(mix, bufsize);
	}
	return MIX_RESULT_NOTIMPL;
}
