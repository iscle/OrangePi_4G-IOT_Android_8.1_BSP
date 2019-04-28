/*
 INTEL CONFIDENTIAL
 Copyright 2009 Intel Corporation All Rights Reserved.
 The source code contained or described herein and all documents related to the source code ("Material") are owned by Intel Corporation or its suppliers or licensors. Title to the Material remains with Intel Corporation or its suppliers and licensors. The Material contains trade secrets and proprietary and confidential information of Intel or its suppliers and licensors. The Material is protected by worldwide copyright and trade secret laws and treaty provisions. No part of the Material may be used, copied, reproduced, modified, published, uploaded, posted, transmitted, distributed, or disclosed in any way without Intel’s prior express written permission.

 No license under any patent, copyright, trade secret or other intellectual property right is granted to or conferred upon you by disclosure or delivery of the Materials, either expressly, by implication, inducement, estoppel or otherwise. Any license under such intellectual property rights must be express and approved by Intel in writing.
 */
#include <glib.h>
#include "mixvideolog.h"

#include "mixvideoformat.h"

#define MIXUNREF(obj, unref) if(obj) { unref(obj); obj = NULL; }


/* Default vmethods implementation */
static MIX_RESULT mix_videofmt_getcaps_default(MixVideoFormat *mix,
		GString *msg);
static MIX_RESULT mix_videofmt_initialize_default(MixVideoFormat *mix,
		MixVideoConfigParamsDec * config_params,
                MixFrameManager * frame_mgr,
		MixBufferPool * input_buf_pool,
		MixSurfacePool ** surface_pool,
                VADisplay vadisplay);
static MIX_RESULT
		mix_videofmt_decode_default(MixVideoFormat *mix, 
		MixBuffer * bufin[], gint bufincnt, 
                MixVideoDecodeParams * decode_params);
static MIX_RESULT mix_videofmt_flush_default(MixVideoFormat *mix);
static MIX_RESULT mix_videofmt_eos_default(MixVideoFormat *mix);
static MIX_RESULT mix_videofmt_deinitialize_default(MixVideoFormat *mix);

static GObjectClass *parent_class = NULL;

static void mix_videoformat_finalize(GObject * obj);
G_DEFINE_TYPE (MixVideoFormat, mix_videoformat, G_TYPE_OBJECT);

static void mix_videoformat_init(MixVideoFormat * self) {

	/* public member initialization */
	/* These are all public because MixVideoFormat objects are completely internal to MixVideo,
		no need for private members  */

	self->initialized = FALSE;
	self->framemgr = NULL;
	self->surfacepool = NULL;
	self->inputbufpool = NULL;
	self->inputbufqueue = NULL;
	self->va_display = NULL;
	self->va_context = VA_INVALID_ID;
	self->va_config = VA_INVALID_ID;
	self->va_surfaces = NULL;
	self->va_num_surfaces = 0;
	self->mime_type = NULL;
	self->frame_rate_num = 0;
	self->frame_rate_denom = 0;
	self->picture_width = 0;
	self->picture_height = 0;
	self->parse_in_progress = FALSE;
	self->current_timestamp = 0;
}

static void mix_videoformat_class_init(MixVideoFormatClass * klass) {
	GObjectClass *gobject_class = (GObjectClass *) klass;

	/* parent class for later use */
	parent_class = g_type_class_peek_parent(klass);

	gobject_class->finalize = mix_videoformat_finalize;

	/* setup vmethods with base implementation */
	klass->getcaps = mix_videofmt_getcaps_default;
	klass->initialize = mix_videofmt_initialize_default;
	klass->decode = mix_videofmt_decode_default;
	klass->flush = mix_videofmt_flush_default;
	klass->eos = mix_videofmt_eos_default;
	klass->deinitialize = mix_videofmt_deinitialize_default;
}

MixVideoFormat *
mix_videoformat_new(void) {
	MixVideoFormat *ret = g_object_new(MIX_TYPE_VIDEOFORMAT, NULL);

	return ret;
}

void mix_videoformat_finalize(GObject * obj) {
	/* clean up here. */
	VAStatus va_status;

	MixVideoFormat *mix = MIX_VIDEOFORMAT(obj); 
	MixInputBufferEntry *buf_entry = NULL;

        if(mix->objectlock) {
                g_mutex_free(mix->objectlock);
                mix->objectlock = NULL;
        }

	if (mix->mime_type)
	{
		if (mix->mime_type->str)
			g_string_free(mix->mime_type, TRUE);
		else
			g_string_free(mix->mime_type, FALSE);
	}

	//MiVideo object calls the _deinitialize() for frame manager
	MIXUNREF(mix->framemgr, mix_framemanager_unref);

	if (mix->surfacepool)
	{
	  mix_surfacepool_deinitialize(mix->surfacepool);
	  MIXUNREF(mix->surfacepool, mix_surfacepool_unref);
	}

	//libVA cleanup (vaTerminate is called from MixVideo object)
	if (mix->va_display) {
		if (mix->va_context != VA_INVALID_ID)
		{
			va_status = vaDestroyConfig(mix->va_display, mix->va_config);
			if (va_status != VA_STATUS_SUCCESS) {
			LOG_W( "Failed vaDestroyConfig\n");
			} 
			mix->va_config = VA_INVALID_ID;
		}
		if (mix->va_context != VA_INVALID_ID)
		{
			va_status = vaDestroyContext(mix->va_display, mix->va_context);
			if (va_status != VA_STATUS_SUCCESS) {
				LOG_W( "Failed vaDestroyContext\n");
			}
			mix->va_context = VA_INVALID_ID;
		}
		if (mix->va_surfaces)
		{
			va_status = vaDestroySurfaces(mix->va_display, mix->va_surfaces, mix->va_num_surfaces);
			if (va_status != VA_STATUS_SUCCESS) {
				LOG_W( "Failed vaDestroySurfaces\n");
			} 
			g_free(mix->va_surfaces);
			mix->va_surfaces = NULL;
			mix->va_num_surfaces = 0;
		}
	}


	//Deinit input buffer queue 

	while (!g_queue_is_empty(mix->inputbufqueue))
	{
		buf_entry = g_queue_pop_head(mix->inputbufqueue); 
		mix_buffer_unref(buf_entry->buf);
		g_free(buf_entry);
	}

	g_queue_free(mix->inputbufqueue);

	//MixBuffer pool is deallocated in MixVideo object
	mix->inputbufpool = NULL;

	/* Chain up parent */
	if (parent_class->finalize) {
		parent_class->finalize(obj);
	}
}

MixVideoFormat *
mix_videoformat_ref(MixVideoFormat * mix) {
	return (MixVideoFormat *) g_object_ref(G_OBJECT(mix));
}

/* Default vmethods implementation */
static MIX_RESULT mix_videofmt_getcaps_default(MixVideoFormat *mix,
		GString *msg) {
	g_print("mix_videofmt_getcaps_default\n");
	return MIX_RESULT_SUCCESS;
}

static MIX_RESULT mix_videofmt_initialize_default(MixVideoFormat *mix,
		MixVideoConfigParamsDec * config_params,
                MixFrameManager * frame_mgr,
		MixBufferPool * input_buf_pool,
		MixSurfacePool ** surface_pool,
                VADisplay va_display) {

	LOG_V(	"Begin\n");

	MIX_RESULT res = MIX_RESULT_SUCCESS;
	MixInputBufferEntry *buf_entry = NULL;

	if (!mix || !config_params || !frame_mgr || !input_buf_pool || !surface_pool || !va_display)
	{
		LOG_E( "NUll pointer passed in\n");
		return (MIX_RESULT_NULL_PTR);
	}

	// Create object lock
	// Note that g_thread_init() has already been called by mix_video_init()
	if (mix->objectlock)  //If already exists, then deallocate old one (we are being re-initialized)
	{
                g_mutex_free(mix->objectlock);
                mix->objectlock = NULL;
	}
	mix->objectlock = g_mutex_new();
	if (!mix->objectlock) {
		LOG_E( "!mix->objectlock\n");
		return (MIX_RESULT_NO_MEMORY);
	}

	g_mutex_lock(mix->objectlock);

	//Clean up any previous framemgr
	MIXUNREF(mix->framemgr, mix_framemanager_unref);
	mix->framemgr = frame_mgr;
	mix_framemanager_ref(mix->framemgr);

	mix->va_display = va_display;

	if (mix->mime_type)  //Clean up any previous mime_type
	{
		if (mix->mime_type->str)
			g_string_free(mix->mime_type, TRUE);
		else
			g_string_free(mix->mime_type, FALSE);
	}
	gchar *mime_tmp = NULL;
	res = mix_videoconfigparamsdec_get_mime_type(config_params, &mime_tmp);
	if (mime_tmp)
	{
		mix->mime_type = g_string_new(mime_tmp);
		g_free(mime_tmp);
		if (!mix->mime_type) //new failed
		{
			res = MIX_RESULT_NO_MEMORY;
			LOG_E( "Could not duplicate mime_type\n");
			goto cleanup;
		}
	}  //else there is no mime_type; leave as NULL

	res = mix_videoconfigparamsdec_get_frame_rate(config_params, &(mix->frame_rate_num), &(mix->frame_rate_denom));
	if (res != MIX_RESULT_SUCCESS)
	{
		LOG_E( "Error getting frame_rate\n");
		goto cleanup;
	}
	res = mix_videoconfigparamsdec_get_picture_res(config_params, &(mix->picture_width), &(mix->picture_height));
	if (res != MIX_RESULT_SUCCESS)
	{
		LOG_E( "Error getting picture_res\n");
		goto cleanup;
	}

	if (mix->inputbufqueue)
	{
		//Deinit previous input buffer queue 
	
		while (!g_queue_is_empty(mix->inputbufqueue))
		{
			buf_entry = g_queue_pop_head(mix->inputbufqueue); 
			mix_buffer_unref(buf_entry->buf);
			g_free(buf_entry);
		}

		g_queue_free(mix->inputbufqueue);
	}

	//MixBuffer pool is cleaned up in MixVideo object
	mix->inputbufpool = NULL;

	mix->inputbufpool = input_buf_pool;
	mix->inputbufqueue = g_queue_new();
	if (!mix->inputbufqueue)  //New failed
	{
		res = MIX_RESULT_NO_MEMORY;
		LOG_E( "Could not duplicate mime_type\n");
		goto cleanup;
	}

	// surface pool, VA context/config and parser handle are initialized by
	// derived classes

	
	cleanup:
	if (res != MIX_RESULT_SUCCESS) {

		MIXUNREF(mix->framemgr, mix_framemanager_unref);
		if (mix->mime_type)
		{
			if (mix->mime_type->str)
				g_string_free(mix->mime_type, TRUE);
			else
				g_string_free(mix->mime_type, FALSE);
			mix->mime_type = NULL;
		}

		if (mix->objectlock)
			g_mutex_unlock(mix->objectlock);
                g_mutex_free(mix->objectlock);
                mix->objectlock = NULL;
		mix->frame_rate_num = 0;
		mix->frame_rate_denom = 1;
		mix->picture_width = 0;
		mix->picture_height = 0;

	} else {
	//Normal unlock
		if (mix->objectlock)
			g_mutex_unlock(mix->objectlock);
	}

	LOG_V( "End\n");

	return res;
}

static MIX_RESULT mix_videofmt_decode_default(MixVideoFormat *mix, 
		MixBuffer * bufin[], gint bufincnt, 
                MixVideoDecodeParams * decode_params) {
	return MIX_RESULT_SUCCESS;
}

static MIX_RESULT mix_videofmt_flush_default(MixVideoFormat *mix) {
	return MIX_RESULT_SUCCESS;
}

static MIX_RESULT mix_videofmt_eos_default(MixVideoFormat *mix) {
	return MIX_RESULT_SUCCESS;
}

static MIX_RESULT mix_videofmt_deinitialize_default(MixVideoFormat *mix) {

	//All teardown is being done in _finalize()

	return MIX_RESULT_SUCCESS;
}

/* mixvideoformat class methods implementation */

MIX_RESULT mix_videofmt_getcaps(MixVideoFormat *mix, GString *msg) {
	MixVideoFormatClass *klass = MIX_VIDEOFORMAT_GET_CLASS(mix);
	g_print("mix_videofmt_getcaps\n");
	if (klass->getcaps) {
		return klass->getcaps(mix, msg);
	}
	return MIX_RESULT_NOTIMPL;
}

MIX_RESULT mix_videofmt_initialize(MixVideoFormat *mix,
		MixVideoConfigParamsDec * config_params,
                MixFrameManager * frame_mgr,
		MixBufferPool * input_buf_pool,
		MixSurfacePool ** surface_pool,
		VADisplay va_display) {
	MixVideoFormatClass *klass = MIX_VIDEOFORMAT_GET_CLASS(mix);

	if (klass->initialize) {
		return klass->initialize(mix, config_params, frame_mgr,
					input_buf_pool, surface_pool, va_display);
	}

	return MIX_RESULT_FAIL;

}

MIX_RESULT mix_videofmt_decode(MixVideoFormat *mix, MixBuffer * bufin[],
                gint bufincnt, MixVideoDecodeParams * decode_params) {

	MixVideoFormatClass *klass = MIX_VIDEOFORMAT_GET_CLASS(mix);
	if (klass->decode) {
		return klass->decode(mix, bufin, bufincnt, decode_params);
	}

	return MIX_RESULT_FAIL;
}

MIX_RESULT mix_videofmt_flush(MixVideoFormat *mix) {
	MixVideoFormatClass *klass = MIX_VIDEOFORMAT_GET_CLASS(mix);
	if (klass->flush) {
		return klass->flush(mix);
	}

	return MIX_RESULT_FAIL;
}

MIX_RESULT mix_videofmt_eos(MixVideoFormat *mix) {
	MixVideoFormatClass *klass = MIX_VIDEOFORMAT_GET_CLASS(mix);
	if (klass->eos) {
		return klass->eos(mix);
	}

	return MIX_RESULT_FAIL;
}

MIX_RESULT mix_videofmt_deinitialize(MixVideoFormat *mix) {
	MixVideoFormatClass *klass = MIX_VIDEOFORMAT_GET_CLASS(mix);
	if (klass->deinitialize) {
		return klass->deinitialize(mix);
	}

	return MIX_RESULT_FAIL;
}
