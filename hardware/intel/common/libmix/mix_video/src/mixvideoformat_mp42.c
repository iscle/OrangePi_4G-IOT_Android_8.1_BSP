/*
 INTEL CONFIDENTIAL
 Copyright 2009 Intel Corporation All Rights Reserved.
 The source code contained or described herein and all documents related to the source code ("Material") are owned by Intel Corporation or its suppliers or licensors. Title to the Material remains with Intel Corporation or its suppliers and licensors. The Material contains trade secrets and proprietary and confidential information of Intel or its suppliers and licensors. The Material is protected by worldwide copyright and trade secret laws and treaty provisions. No part of the Material may be used, copied, reproduced, modified, published, uploaded, posted, transmitted, distributed, or disclosed in any way without Intel’s prior express written permission.

 No license under any patent, copyright, trade secret or other intellectual property right is granted to or conferred upon you by disclosure or delivery of the Materials, either expressly, by implication, inducement, estoppel or otherwise. Any license under such intellectual property rights must be express and approved by Intel in writing.
 */
#include <glib.h>
#include <string.h>
#include "mixvideolog.h"
#include "mixvideoformat_mp42.h"

enum {
	MP4_VOP_TYPE_I = 0,
	MP4_VOP_TYPE_P = 1,
	MP4_VOP_TYPE_B = 2,
	MP4_VOP_TYPE_S = 3,
};

/*
 * This is for divx packed stream
 */
typedef struct _PackedStream PackedStream;
struct _PackedStream {
	vbp_picture_data_mp42 *picture_data;
	MixBuffer *mix_buffer;
};

/*
 * Clone and destroy vbp_picture_data_mp42
 */
static vbp_picture_data_mp42 *mix_videoformat_mp42_clone_picture_data(
		vbp_picture_data_mp42 *picture_data);
static void mix_videoformat_mp42_free_picture_data(
		vbp_picture_data_mp42 *picture_data);
static void mix_videoformat_mp42_flush_packed_stream_queue(
		GQueue *packed_stream_queue);

/* The parent class. The pointer will be saved
 * in this class's initialization. The pointer
 * can be used for chaining method call if needed.
 */
static MixVideoFormatClass *parent_class = NULL;

static void mix_videoformat_mp42_finalize(GObject * obj);

/*
 * Please note that the type we pass to G_DEFINE_TYPE is MIX_TYPE_VIDEOFORMAT
 */
G_DEFINE_TYPE( MixVideoFormat_MP42, mix_videoformat_mp42, MIX_TYPE_VIDEOFORMAT);

static void mix_videoformat_mp42_init(MixVideoFormat_MP42 * self) {
	MixVideoFormat *parent = MIX_VIDEOFORMAT(self);

	self->reference_frames[0] = NULL;
	self->reference_frames[1] = NULL;

	self->last_frame = NULL;
	self->last_vop_coding_type = -1;

	self->packed_stream_queue = NULL;

	/* NOTE: we don't need to do this here.
	 * This just demostrates how to access
	 * member varibles beloned to parent
	 */
	parent->initialized = FALSE;
}

static void mix_videoformat_mp42_class_init(MixVideoFormat_MP42Class * klass) {

	/* root class */
	GObjectClass *gobject_class = (GObjectClass *) klass;

	/* direct parent class */
	MixVideoFormatClass *video_format_class = MIX_VIDEOFORMAT_CLASS(klass);

	/* parent class for later use */
	parent_class = g_type_class_peek_parent(klass);

	/* setup finializer */
	gobject_class->finalize = mix_videoformat_mp42_finalize;

	/* setup vmethods with base implementation */
	video_format_class->getcaps = mix_videofmt_mp42_getcaps;
	video_format_class->initialize = mix_videofmt_mp42_initialize;
	video_format_class->decode = mix_videofmt_mp42_decode;
	video_format_class->flush = mix_videofmt_mp42_flush;
	video_format_class->eos = mix_videofmt_mp42_eos;
	video_format_class->deinitialize = mix_videofmt_mp42_deinitialize;
}

MixVideoFormat_MP42 *mix_videoformat_mp42_new(void) {
	MixVideoFormat_MP42 *ret = g_object_new(MIX_TYPE_VIDEOFORMAT_MP42, NULL);

	return ret;
}

void mix_videoformat_mp42_finalize(GObject * obj) {
	/* clean up here. */

	/*	MixVideoFormat_MP42 *mix = MIX_VIDEOFORMAT_MP42(obj); */
	GObjectClass *root_class = (GObjectClass *) parent_class;
	MixVideoFormat *parent = NULL;
	gint32 vbp_ret = VBP_OK;
	MixVideoFormat_MP42 *self = NULL;

	LOG_V("Begin\n");

	if (obj == NULL) {
		LOG_E("obj is NULL\n");
		return;
	}

	if (!MIX_IS_VIDEOFORMAT_MP42(obj)) {
		LOG_E("obj is not mixvideoformat_mp42\n");
		return;
	}

	self = MIX_VIDEOFORMAT_MP42(obj);
	parent = MIX_VIDEOFORMAT(self);

	//surfacepool is deallocated by parent
	//inputbufqueue is deallocated by parent
	//parent calls vaDestroyConfig, vaDestroyContext and vaDestroySurfaces

	g_mutex_lock(parent->objectlock);

	/* unref reference frames */
	{
		gint idx = 0;
		for (idx = 0; idx < 2; idx++) {
			if (self->reference_frames[idx] != NULL) {
				mix_videoframe_unref(self->reference_frames[idx]);
				self->reference_frames[idx] = NULL;
			}
		}
	}


	/* Reset state */
	parent->initialized = TRUE;
	parent->parse_in_progress = FALSE;
	parent->discontinuity_frame_in_progress = FALSE;
	parent->current_timestamp = 0;

	/* Close the parser */
	vbp_ret = vbp_close(parent->parser_handle);
	parent->parser_handle = NULL;

	if (self->packed_stream_queue) {
		mix_videoformat_mp42_flush_packed_stream_queue(self->packed_stream_queue);
		g_queue_free(self->packed_stream_queue);
	}
	self->packed_stream_queue = NULL;

	g_mutex_unlock(parent->objectlock);

	/* Chain up parent */
	if (root_class->finalize) {
		root_class->finalize(obj);
	}

	LOG_V("End\n");
}

MixVideoFormat_MP42 *
mix_videoformat_mp42_ref(MixVideoFormat_MP42 * mix) {
	return (MixVideoFormat_MP42 *) g_object_ref(G_OBJECT(mix));
}

/*  MP42 vmethods implementation */
MIX_RESULT mix_videofmt_mp42_getcaps(MixVideoFormat *mix, GString *msg) {

//This method is reserved for future use

	LOG_V("Begin\n");
	if (parent_class->getcaps) {
		return parent_class->getcaps(mix, msg);
	}

	LOG_V("End\n");
	return MIX_RESULT_NOTIMPL;
}

MIX_RESULT mix_videofmt_mp42_initialize(MixVideoFormat *mix,
		MixVideoConfigParamsDec * config_params, MixFrameManager * frame_mgr,
		MixBufferPool * input_buf_pool, MixSurfacePool ** surface_pool,
		VADisplay va_display) {
	uint32 vbp_ret = 0;
	MIX_RESULT ret = MIX_RESULT_FAIL;

	vbp_data_mp42 *data = NULL;
	MixVideoFormat *parent = NULL;
	MixIOVec *header = NULL;

	VAProfile va_profile = VAProfileMPEG4AdvancedSimple;
	VAConfigAttrib attrib;

	VAStatus va_ret = VA_STATUS_SUCCESS;
	guint number_extra_surfaces = 0;
	VASurfaceID *surfaces = NULL;
	guint numSurfaces = 0;

	MixVideoFormat_MP42 *self = MIX_VIDEOFORMAT_MP42(mix);

	if (mix == NULL || config_params == NULL || frame_mgr == NULL) {
		return MIX_RESULT_NULL_PTR;
	}

	if (!MIX_IS_VIDEOFORMAT_MP42(mix)) {
		return MIX_RESULT_INVALID_PARAM;
	}

	LOG_V("begin\n");

	if (parent_class->initialize) {
		ret = parent_class->initialize(mix, config_params, frame_mgr,
				input_buf_pool, surface_pool, va_display);
		if (ret != MIX_RESULT_SUCCESS) {
			LOG_E("Failed to initialize parent!\n");
			return ret;
		}
	}

	parent = MIX_VIDEOFORMAT(mix);

	g_mutex_lock(parent->objectlock);

	parent->initialized = FALSE;

	vbp_ret = vbp_open(VBP_MPEG4, &(parent->parser_handle));

	if (vbp_ret != VBP_OK) {
		LOG_E("Failed to call vbp_open()\n");
		ret = MIX_RESULT_FAIL;
		goto cleanup;
	}

	/*
	 * avidemux doesn't pass codec_data, we need handle this.
	 */

	LOG_V("Try to get header data from config_param\n");

	ret = mix_videoconfigparamsdec_get_header(config_params, &header);
	if (ret == MIX_RESULT_SUCCESS && header != NULL) {

		LOG_V("Found header data from config_param\n");
		vbp_ret = vbp_parse(parent->parser_handle, header->data, header->data_size,
				TRUE);

		LOG_V("vbp_parse() returns 0x%x\n", vbp_ret);

		g_free(header->data);
		g_free(header);

		if (!((vbp_ret == VBP_OK) || (vbp_ret == VBP_DONE))) {
			LOG_E("Failed to call vbp_parse() to parse header data!\n");
			goto cleanup;
		}

		/* Get the header data and save */

		LOG_V("Call vbp_query()\n");
		vbp_ret = vbp_query(parent->parser_handle, (void *) &data);
		LOG_V("vbp_query() returns 0x%x\n", vbp_ret);

		if ((vbp_ret != VBP_OK) || (data == NULL)) {
			LOG_E("Failed to call vbp_query() to query header data parsing result\n");
			goto cleanup;
		}

		if ((data->codec_data.profile_and_level_indication & 0xF8) == 0xF0) {
			va_profile = VAProfileMPEG4AdvancedSimple;
			LOG_V("The profile is VAProfileMPEG4AdvancedSimple from header data\n");
		} else {
			va_profile = VAProfileMPEG4Simple;
			LOG_V("The profile is VAProfileMPEG4Simple from header data\n");
		}
	}

	va_display = parent->va_display;

	/* We are requesting RT attributes */
	attrib.type = VAConfigAttribRTFormat;

	va_ret = vaGetConfigAttributes(va_display, va_profile, VAEntrypointVLD,
			&attrib, 1);
	if (va_ret != VA_STATUS_SUCCESS) {
		LOG_E("Failed to call vaGetConfigAttributes()\n");
		goto cleanup;
	}

	if ((attrib.value & VA_RT_FORMAT_YUV420) == 0) {
		LOG_E("The attrib.value is wrong!\n");
		goto cleanup;
	}

	va_ret = vaCreateConfig(va_display, va_profile, VAEntrypointVLD, &attrib,
			1, &(parent->va_config));

	if (va_ret != VA_STATUS_SUCCESS) {
		LOG_E("Failed to call vaCreateConfig()!\n");
		goto cleanup;
	}

	ret = mix_videoconfigparamsdec_get_extra_surface_allocation(config_params,
			&number_extra_surfaces);

	if (ret != MIX_RESULT_SUCCESS) {
		LOG_E("Failed to call mix_videoconfigparams_get_extra_surface_allocation()!\n");
		goto cleanup;
	}

	parent->va_num_surfaces = number_extra_surfaces + 4;
	if (parent->va_num_surfaces > MIX_VIDEO_MP42_SURFACE_NUM) {
		parent->va_num_surfaces = MIX_VIDEO_MP42_SURFACE_NUM;
	}

	numSurfaces = parent->va_num_surfaces;

	parent->va_surfaces = g_malloc(sizeof(VASurfaceID) * numSurfaces);
	if (!parent->va_surfaces) {
		LOG_E("Not enough memory to allocate surfaces!\n");
		ret = MIX_RESULT_NO_MEMORY;
		goto cleanup;
	}

	surfaces = parent->va_surfaces;

	va_ret = vaCreateSurfaces(va_display, parent->picture_width,
			parent->picture_height, VA_RT_FORMAT_YUV420, numSurfaces,
			surfaces);
	if (va_ret != VA_STATUS_SUCCESS) {
		LOG_E("Failed to call vaCreateSurfaces()!\n");
		goto cleanup;
	}

	parent->surfacepool = mix_surfacepool_new();
	if (parent->surfacepool == NULL) {
		LOG_E("Not enough memory to create surface pool!\n");
		ret = MIX_RESULT_NO_MEMORY;
		goto cleanup;
	}

	*surface_pool = parent->surfacepool;

	ret = mix_surfacepool_initialize(parent->surfacepool, surfaces,
			numSurfaces);

	/* Initialize and save the VA context ID
	 * Note: VA_PROGRESSIVE libva flag is only relevant to MPEG2
	 */
	va_ret = vaCreateContext(va_display, parent->va_config,
			parent->picture_width, parent->picture_height, 0, surfaces,
			numSurfaces, &(parent->va_context));

	if (va_ret != VA_STATUS_SUCCESS) {
		LOG_E("Failed to call vaCreateContext()!\n");
		ret = MIX_RESULT_FAIL;
		goto cleanup;
	}

	/*
	 * Packed stream queue
	 */

	self->packed_stream_queue = g_queue_new();
	if (!self->packed_stream_queue) {
		LOG_E("Failed to crate packed stream queue!\n");
		ret = MIX_RESULT_NO_MEMORY;
		goto cleanup;
	}

	self->last_frame = NULL;
	self->last_vop_coding_type = -1;
	parent->initialized = FALSE;
	ret = MIX_RESULT_SUCCESS;

	cleanup:

	g_mutex_unlock(parent->objectlock);

	LOG_V("End\n");

	return ret;
}

MIX_RESULT mix_videofmt_mp42_decode(MixVideoFormat *mix, MixBuffer * bufin[],
		gint bufincnt, MixVideoDecodeParams * decode_params) {
	uint32 vbp_ret = 0;
	MixVideoFormat *parent = NULL;
	MIX_RESULT ret = MIX_RESULT_FAIL;
	guint64 ts = 0;
	vbp_data_mp42 *data = NULL;
	gboolean discontinuity = FALSE;
	MixInputBufferEntry *bufentry = NULL;
	gint i = 0;

	LOG_V("Begin\n");

	if (mix == NULL || bufin == NULL || decode_params == NULL) {
		return MIX_RESULT_NULL_PTR;
	}

	if (!MIX_IS_VIDEOFORMAT_MP42(mix)) {
		return MIX_RESULT_INVALID_PARAM;
	}

	parent = MIX_VIDEOFORMAT(mix);

	g_mutex_lock(parent->objectlock);

	ret = mix_videodecodeparams_get_timestamp(decode_params, &ts);
	if (ret != MIX_RESULT_SUCCESS) {
		LOG_E("Failed to get timestamp\n");
		goto cleanup;
	}

	LOG_I("ts after mix_videodecodeparams_get_timestamp() = %"G_GINT64_FORMAT"\n", ts);

	ret
			= mix_videodecodeparams_get_discontinuity(decode_params,
					&discontinuity);
	if (ret != MIX_RESULT_SUCCESS) {
		LOG_E("Failed to get discontinuity\n");
		goto cleanup;
	}

	/*  If this is a new frame and we haven't retrieved parser
	 *	workload data from previous frame yet, do so
	 */

	if ((ts != parent->current_timestamp) && (parent->parse_in_progress)) {

		LOG_V("timestamp changed and parsing is still in progress\n");

		/* this is new data and the old data parsing is not complete, continue
		 * to parse the old data
		 */
		vbp_ret = vbp_query(parent->parser_handle, (void *) &data);
		LOG_V("vbp_query() returns 0x%x\n", vbp_ret);

		if ((vbp_ret != VBP_OK) || (data == NULL)) {
			ret = MIX_RESULT_FAIL;
			LOG_E("vbp_ret != VBP_OK || data == NULL\n");
			goto cleanup;
		}

		ret = mix_videofmt_mp42_process_decode(mix, data,
				parent->current_timestamp,
				parent->discontinuity_frame_in_progress);

		if (ret != MIX_RESULT_SUCCESS) {
			/* We log this but need to process 
			 * the new frame data, so do not return
			 */
			LOG_W("process_decode failed.\n");
		}

		/* we are done parsing for old data */
		parent->parse_in_progress = FALSE;
	}

	parent->current_timestamp = ts;
	parent->discontinuity_frame_in_progress = discontinuity;

	/* we parse data buffer one by one */
	for (i = 0; i < bufincnt; i++) {

		LOG_V(
				"Calling parse for current frame, parse handle %d, buf %x, size %d\n",
				(int) parent->parser_handle, (guint) bufin[i]->data,
				bufin[i]->size);

		vbp_ret = vbp_parse(parent->parser_handle, bufin[i]->data,
				bufin[i]->size, FALSE);

		LOG_V("vbp_parse() returns 0x%x\n", vbp_ret);

		/* The parser failed to parse */
		if (vbp_ret != VBP_DONE && vbp_ret != VBP_OK) {
			LOG_E("vbp_parse() ret = %d\n", vbp_ret);
			ret = MIX_RESULT_FAIL;
			goto cleanup;
		}

		LOG_V("vbp_parse() ret = %d\n", vbp_ret);

		if (vbp_ret == VBP_OK || vbp_ret == VBP_DONE) {

			LOG_V("Now, parsing is done (VBP_DONE)!\n");

			vbp_ret = vbp_query(parent->parser_handle, (void *) &data);
			LOG_V("vbp_query() returns 0x%x\n", vbp_ret);

			if ((vbp_ret != VBP_OK) || (data == NULL)) {
				ret = MIX_RESULT_FAIL;
				goto cleanup;
			}

			/* Increase the ref count of this input buffer */
			mix_buffer_ref(bufin[i]);

			/* Create a new MixInputBufferEntry
			 * TODO: make this from a pool later 
			 */
			bufentry = g_malloc(sizeof(MixInputBufferEntry));
			if (bufentry == NULL) {
				ret = MIX_RESULT_NO_MEMORY;
				goto cleanup;
			}

			bufentry->buf = bufin[i];
			bufentry->timestamp = ts;

			LOG_I("bufentry->buf = %x bufentry->timestamp FOR VBP_DONE = %"G_GINT64_FORMAT"\n", bufentry->buf, bufentry->timestamp);

			/* Enqueue this input buffer */
			g_queue_push_tail(parent->inputbufqueue, (gpointer) bufentry);

			/* process and decode data */
			ret
					= mix_videofmt_mp42_process_decode(mix, data, ts,
							discontinuity);

			if (ret != MIX_RESULT_SUCCESS) {
				/* We log this but continue since we need 
				 * to complete our processing
				 */
				LOG_W("process_decode failed.\n");
			}

			LOG_V("Called process and decode for current frame\n");

			parent->parse_in_progress = FALSE;

		}
#if 0
		/*
		 * The DHG parser checks for next_sc, if next_sc is a start code, it thinks the current parsing is done: VBP_DONE.
		 * For our situtation, this not the case. The start code is always begin with the gstbuffer. At the end of frame,
		 * the start code is never found.
		 */

		else if (vbp_ret == VBP_OK) {

			LOG_V("Now, parsing is not done (VBP_OK)!\n");

			LOG_V(
					"Enqueuing buffer and going on to next (if any) for this frame\n");

			/* Increase the ref count of this input buffer */
			mix_buffer_ref(bufin[i]);

			/* Create a new MixInputBufferEntry
			 * TODO make this from a pool later
			 */
			bufentry = g_malloc(sizeof(MixInputBufferEntry));
			if (bufentry == NULL) {
				ret = MIX_RESULT_FAIL;
				goto cleanup;
			}

			bufentry->buf = bufin[i];
			bufentry->timestamp = ts;
			LOG_I("bufentry->buf = %x bufentry->timestamp FOR VBP_OK = %"G_GINT64_FORMAT"\n", bufentry->buf, bufentry->timestamp);

			/* Enqueue this input buffer */
			g_queue_push_tail(parent->inputbufqueue, (gpointer) bufentry);
			parent->parse_in_progress = TRUE;
		}
#endif
	}

	cleanup:

	g_mutex_unlock(parent->objectlock);

	LOG_V("End\n");

	return ret;
}

MIX_RESULT mix_videofmt_mp42_process_decode(MixVideoFormat *mix,
		vbp_data_mp42 *data, guint64 timestamp, gboolean discontinuity) {

	MIX_RESULT ret = MIX_RESULT_SUCCESS;
	VAStatus va_ret = VA_STATUS_SUCCESS;
	VADisplay va_display = NULL;
	VAContextID va_context;

	MixVideoFormat_MP42 *self = NULL;
	vbp_picture_data_mp42 *picture_data = NULL;
	VAPictureParameterBufferMPEG4 *picture_param = NULL;
	VAIQMatrixBufferMPEG4 *iq_matrix_buffer = NULL;
	vbp_slice_data_mp42 *slice_data = NULL;
	VASliceParameterBufferMPEG4 *slice_param = NULL;

	gint frame_type = -1;
	guint buffer_id_number = 0;
	guint buffer_id_cnt = 0;
	VABufferID *buffer_ids = NULL;
	MixVideoFrame *frame = NULL;

	gint idx = 0, jdx = 0;
	gulong surface = 0;

	MixBuffer *mix_buffer = NULL;
	gboolean is_from_queued_data = FALSE;

	LOG_V("Begin\n");

	if ((mix == NULL) || (data == NULL)) {
		return MIX_RESULT_NULL_PTR;
	}

	if (!MIX_IS_VIDEOFORMAT_MP42(mix)) {
		return MIX_RESULT_INVALID_PARAM;
	}

	self = MIX_VIDEOFORMAT_MP42(mix);

	LOG_V("data->number_pictures = %d\n", data->number_pictures);

	if (data->number_pictures == 0) {
		LOG_W("data->number_pictures == 0\n");
		mix_videofmt_mp42_release_input_buffers(mix, timestamp);
		return ret;
	}

	is_from_queued_data = FALSE;

	/* Do we have packed frames? */
	if (data->number_pictures > 1) {

		/*

		 Assumption:

		 1. In one packed frame, there's only one P or I frame and the
		 reference frame will be the first one in the packed frame
		 2. In packed frame, there's no skipped frame(vop_coded = 0)
		 3. In one packed frame, if there're n B frames, there will be
		 n N-VOP frames to follow the packed frame.
		 The timestamp of each N-VOP frame will be used for each B frames
		 in the packed frame
		 4. N-VOP frame is the frame with vop_coded = 0.

		 {P, B, B, B }, N, N, N, P, P, P, I, ...

		 */

		MixInputBufferEntry *bufentry = NULL;
		PackedStream *packed_stream = NULL;
		vbp_picture_data_mp42 *cloned_picture_data = NULL;

		LOG_V("This is packed frame\n");

		/*
		 * Is the packed_frame_queue empty? If not, how come
		 * a packed frame can follow another packed frame without
		 * necessary number of N-VOP between them?
		 */

		if (!g_queue_is_empty(self->packed_stream_queue)) {
			ret = MIX_RESULT_FAIL;
			LOG_E("The previous packed frame is not fully processed yet!\n");
			goto cleanup;
		}

		/* Packed frame shall be something like this {P, B, B, B, ... B } */
		for (idx = 0; idx < data->number_pictures; idx++) {
			picture_data = &(data->picture_data[idx]);
			picture_param = &(picture_data->picture_param);
			frame_type = picture_param->vop_fields.bits.vop_coding_type;

			/* Is the first frame in the packed frames a reference frame? */
			if (idx == 0 && frame_type != MP4_VOP_TYPE_I && frame_type
					!= MP4_VOP_TYPE_P) {
				ret = MIX_RESULT_FAIL;
				LOG_E("The first frame in packed frame is not I or B\n");
				goto cleanup;
			}

			if (idx != 0 && frame_type != MP4_VOP_TYPE_B) {
				ret = MIX_RESULT_FAIL;
				LOG_E("The frame other than the first one in packed frame is not B\n");
				goto cleanup;
			}

			if (picture_data->vop_coded == 0) {
				ret = MIX_RESULT_FAIL;
				LOG_E("In packed frame, there's unexpected skipped frame\n");
				goto cleanup;
			}
		}

		LOG_V("The packed frame looks valid\n");

		/* Okay, the packed-frame looks ok. Now, we enqueue all the B frames */
		bufentry
				= (MixInputBufferEntry *) g_queue_peek_head(mix->inputbufqueue);
		if (bufentry == NULL) {
			ret = MIX_RESULT_FAIL;
			LOG_E("There's data in in inputbufqueue\n");
			goto cleanup;
		}

		LOG_V("Enqueue all B frames in the packed frame\n");

		mix_buffer = bufentry->buf;
		for (idx = 1; idx < data->number_pictures; idx++) {
			picture_data = &(data->picture_data[idx]);
			cloned_picture_data = mix_videoformat_mp42_clone_picture_data(
					picture_data);
			if (!cloned_picture_data) {
				ret = MIX_RESULT_NO_MEMORY;
				LOG_E("Failed to allocate memory for cloned picture_data\n");
				goto cleanup;
			}

			packed_stream = g_malloc(sizeof(PackedStream));
			if (packed_stream == NULL) {
				ret = MIX_RESULT_NO_MEMORY;
				LOG_E("Failed to allocate memory for packed_stream\n");
				goto cleanup;
			}

			packed_stream->mix_buffer = mix_buffer_ref(mix_buffer);
			packed_stream->picture_data = cloned_picture_data;

			g_queue_push_tail(self->packed_stream_queue,
					(gpointer) packed_stream);
		}

		LOG_V("Prepare to decode the first frame in the packed frame\n");

		/* we are going to process the firs frame */
		picture_data = &(data->picture_data[0]);

	} else {

		LOG_V("This is a single frame\n");

		/* Okay, we only have one frame */
		if (g_queue_is_empty(self->packed_stream_queue)) {
			/* If the packed_stream_queue is empty, everything is fine */
			picture_data = &(data->picture_data[0]);

			LOG_V("There's no packed frame not processed yet\n");

		} else {
			/*	The packed_stream_queue is not empty, is this frame N-VOP? */
			picture_data = &(data->picture_data[0]);
			if (picture_data->vop_coded != 0) {

				LOG_V("The packed frame queue is not empty, we will flush it\n");

				/* 
				 * Unexpected! We flush the packed_stream_queue and begin to process the 
				 * current frame if it is not a B frame
				 */
				mix_videoformat_mp42_flush_packed_stream_queue(
						self->packed_stream_queue);

				picture_param = &(picture_data->picture_param);
				frame_type = picture_param->vop_fields.bits.vop_coding_type;

				if (frame_type == MP4_VOP_TYPE_B) {
					ret = MIX_RESULT_FAIL;
					LOG_E("The frame right after packed frame is B frame!\n");
					goto cleanup;
				}

			} else {
				/*	This is N-VOP, process B frame from the packed_stream_queue */
				PackedStream *packed_stream = NULL;

				LOG_V("N-VOP found, we ignore it and start to process the B frame from the packed frame queue\n");

				packed_stream = (PackedStream *) g_queue_pop_head(
						self->packed_stream_queue);
				picture_data = packed_stream->picture_data;
				mix_buffer = packed_stream->mix_buffer;
				g_free(packed_stream);
				is_from_queued_data = TRUE;
			}
		}
	}

	picture_param = &(picture_data->picture_param);
	iq_matrix_buffer = &(picture_data->iq_matrix_buffer);

	if (picture_param == NULL) {
		ret = MIX_RESULT_NULL_PTR;
		LOG_E("picture_param == NULL\n");
		goto cleanup;
	}

	/* If the frame type is not I, P or B */
	frame_type = picture_param->vop_fields.bits.vop_coding_type;
	if (frame_type != MP4_VOP_TYPE_I && frame_type != MP4_VOP_TYPE_P
			&& frame_type != MP4_VOP_TYPE_B) {
		ret = MIX_RESULT_FAIL;
		LOG_E("frame_type is not I, P or B. frame_type = %d\n", frame_type);
		goto cleanup;
	}

	/*
	 * This is a skipped frame (vop_coded = 0)
	 * Please note that this is not a N-VOP (DivX).
	 */
	if (picture_data->vop_coded == 0) {

		MixVideoFrame *skip_frame = NULL;
		gulong frame_id = VA_INVALID_SURFACE;

		LOG_V("vop_coded == 0\n");
		if (self->last_frame == NULL) {
			LOG_W("Previous frame is NULL\n");

			/*
			 * We shouldn't get a skipped frame
			 * before we are able to get a real frame
			 */
			ret = MIX_RESULT_DROPFRAME;
			goto cleanup;
		}

		skip_frame = mix_videoframe_new();
		ret = mix_videoframe_set_is_skipped(skip_frame, TRUE);
		mix_videoframe_ref(self->last_frame);

		ret = mix_videoframe_get_frame_id(self->last_frame, &frame_id);
		ret = mix_videoframe_set_frame_id(skip_frame, frame_id);
		ret = mix_videoframe_set_frame_type(skip_frame, MP4_VOP_TYPE_P);
		ret = mix_videoframe_set_real_frame(skip_frame, self->last_frame);
		ret = mix_videoframe_set_timestamp(skip_frame, timestamp);
		ret = mix_videoframe_set_discontinuity(skip_frame, FALSE);

		LOG_V("Processing skipped frame %x, frame_id set to %d, ts %"G_GINT64_FORMAT"\n",
				(guint)skip_frame, (guint)frame_id, timestamp);

		/* Release our input buffers */
		ret = mix_videofmt_mp42_release_input_buffers(mix, timestamp);

		/* Enqueue the skipped frame using frame manager */
		ret = mix_framemanager_enqueue(mix->framemgr, skip_frame);
		goto cleanup;
	}

	/*
	 * Decide the number of buffer to use
	 */

	buffer_id_number = picture_data->number_slices * 2 + 2;
	LOG_V("number_slices is %d, allocating %d buffer_ids\n",
			picture_data->number_slices, buffer_id_number);

	/*
	 * Check for B frames after a seek
	 * We need to have both reference frames in hand before we can decode a B frame
	 * If we don't have both reference frames, we must return MIX_RESULT_DROPFRAME
	 */
	if (frame_type == MP4_VOP_TYPE_B) {

		if (self->reference_frames[1] == NULL) {
			LOG_W("Insufficient reference frames for B frame\n");
			ret = MIX_RESULT_DROPFRAME;
			goto cleanup;
		}
	}

	buffer_ids = g_malloc(sizeof(VABufferID) * buffer_id_number);
	if (buffer_ids == NULL) {
		ret = MIX_RESULT_NO_MEMORY;
		LOG_E("Failed to allocate buffer_ids!\n");
		goto cleanup;
	}

	LOG_V("Getting a new surface\n");LOG_V("frame type is %d\n", frame_type);

	/* Get a frame from the surface pool */
	ret = mix_surfacepool_get(mix->surfacepool, &frame);
	if (ret != MIX_RESULT_SUCCESS) {
		LOG_E("Failed to get frame from surface pool!\n");
		goto cleanup;
	}

	/*
	 * Set the frame type for the frame object (used in reordering by frame manager)
	 */
	ret = mix_videoframe_set_frame_type(frame, frame_type);
	if (ret != MIX_RESULT_SUCCESS) {
		LOG_E("Failed to set frame type!\n");
		goto cleanup;
	}

	/* If I or P frame, update the reference array */
	if ((frame_type == MP4_VOP_TYPE_I) || (frame_type == MP4_VOP_TYPE_P)) {
		LOG_V("Updating forward/backward references for libva\n");

		self->last_vop_coding_type = frame_type;
		mix_videofmt_mp42_handle_ref_frames(mix, frame_type, frame);
	}

	LOG_V("Setting reference frames in picparams, frame_type = %d\n",
			frame_type);

	switch (frame_type) {
	case MP4_VOP_TYPE_I:
		picture_param->forward_reference_picture = VA_INVALID_SURFACE;
		picture_param->backward_reference_picture = VA_INVALID_SURFACE;
		LOG_V("I frame, surface ID %u\n", (guint) frame->frame_id);
		break;
	case MP4_VOP_TYPE_P:
		picture_param-> forward_reference_picture
				= self->reference_frames[0]->frame_id;
		picture_param-> backward_reference_picture = VA_INVALID_SURFACE;

		LOG_V("P frame, surface ID %u, forw ref frame is %u\n",
				(guint) frame->frame_id,
				(guint) self->reference_frames[0]->frame_id);
		break;
	case MP4_VOP_TYPE_B:

		picture_param->vop_fields.bits.backward_reference_vop_coding_type
				= self->last_vop_coding_type;

		picture_param->forward_reference_picture
				= self->reference_frames[1]->frame_id;
		picture_param->backward_reference_picture
				= self->reference_frames[0]->frame_id;

		LOG_V("B frame, surface ID %u, forw ref %d, back ref %d\n",
				(guint) frame->frame_id,
				(guint) picture_param->forward_reference_picture,
				(guint) picture_param->backward_reference_picture);
		break;
	case MP4_VOP_TYPE_S:
		LOG_W("MP4_VOP_TYPE_S, Will never reach here\n");
		break;

	default:
		LOG_W("default, Will never reach here\n");
		break;

	}

	/* Libva buffer set up */
	va_display = mix->va_display;
	va_context = mix->va_context;

	LOG_V("Creating libva picture parameter buffer\n");

	/* First the picture parameter buffer */
	buffer_id_cnt = 0;
	va_ret = vaCreateBuffer(va_display, va_context,
			VAPictureParameterBufferType,
			sizeof(VAPictureParameterBufferMPEG4), 1, picture_param,
			&buffer_ids[buffer_id_cnt]);
	buffer_id_cnt++;

	if (va_ret != VA_STATUS_SUCCESS) {
		ret = MIX_RESULT_FAIL;
		LOG_E("Failed to create va buffer of type VAPictureParameterBufferMPEG4!\n");
		goto cleanup;
	}

	LOG_V("Creating libva VAIQMatrixBufferMPEG4 buffer\n");

	if (picture_param->vol_fields.bits.quant_type) {
		va_ret = vaCreateBuffer(va_display, va_context, VAIQMatrixBufferType,
				sizeof(VAIQMatrixBufferMPEG4), 1, iq_matrix_buffer,
				&buffer_ids[buffer_id_cnt]);

		if (va_ret != VA_STATUS_SUCCESS) {
			ret = MIX_RESULT_FAIL;
			LOG_E("Failed to create va buffer of type VAIQMatrixBufferType!\n");
			goto cleanup;
		}
		buffer_id_cnt++;
	}

	/* Now for slices */
	for (jdx = 0; jdx < picture_data->number_slices; jdx++) {

		slice_data = &(picture_data->slice_data[jdx]);
		slice_param = &(slice_data->slice_param);

		LOG_V(
				"Creating libva slice parameter buffer, for slice %d\n",
				jdx);

		/* Do slice parameters */
		va_ret = vaCreateBuffer(va_display, va_context,
				VASliceParameterBufferType,
				sizeof(VASliceParameterBufferMPEG4), 1, slice_param,
				&buffer_ids[buffer_id_cnt]);
		if (va_ret != VA_STATUS_SUCCESS) {
			ret = MIX_RESULT_FAIL;
			LOG_E("Failed to create va buffer of type VASliceParameterBufferMPEG4!\n");
			goto cleanup;
		}
		buffer_id_cnt++;

		/* Do slice data */
		va_ret = vaCreateBuffer(va_display, va_context, VASliceDataBufferType,
				slice_data->slice_size, 1, slice_data->buffer_addr
						+ slice_data->slice_offset, &buffer_ids[buffer_id_cnt]);
		if (va_ret != VA_STATUS_SUCCESS) {
			ret = MIX_RESULT_FAIL;
			LOG_E("Failed to create va buffer of type VASliceDataBufferType!\n");
			goto cleanup;
		}
		buffer_id_cnt++;
	}

	/* Get our surface ID from the frame object */
	ret = mix_videoframe_get_frame_id(frame, &surface);
	if (ret != MIX_RESULT_SUCCESS) {
		LOG_E("Failed to get frame id: ret = 0x%x\n", ret);
		goto cleanup;
	}

	LOG_V("Calling vaBeginPicture\n");

	/* Now we can begin the picture */
	va_ret = vaBeginPicture(va_display, va_context, surface);
	if (va_ret != VA_STATUS_SUCCESS) {
		ret = MIX_RESULT_FAIL;
		LOG_E("Failed to vaBeginPicture(): va_ret = 0x%x\n", va_ret);
		goto cleanup;
	}

	LOG_V("Calling vaRenderPicture\n");

	/* Render the picture */
	va_ret = vaRenderPicture(va_display, va_context, buffer_ids, buffer_id_cnt);
	if (va_ret != VA_STATUS_SUCCESS) {
		ret = MIX_RESULT_FAIL;
		LOG_E("Failed to vaRenderPicture(): va_ret = 0x%x\n", va_ret);
		goto cleanup;
	}

	LOG_V("Calling vaEndPicture\n");

	/* End picture */
	va_ret = vaEndPicture(va_display, va_context);
	if (va_ret != VA_STATUS_SUCCESS) {
		ret = MIX_RESULT_FAIL;
		LOG_E("Failed to vaEndPicture(): va_ret = 0x%x\n", va_ret);
		goto cleanup;
	}

	LOG_V("Calling vaSyncSurface\n");

	/* Decode the picture */
	va_ret = vaSyncSurface(va_display, surface);
	if (va_ret != VA_STATUS_SUCCESS) {
		ret = MIX_RESULT_FAIL;
		LOG_E("Failed to vaSyncSurface(): va_ret = 0x%x\n", va_ret);
		goto cleanup;
	}

	/* Set the discontinuity flag */
	mix_videoframe_set_discontinuity(frame, discontinuity);

	/* Set the timestamp */
	mix_videoframe_set_timestamp(frame, timestamp);

	LOG_V("Enqueueing the frame with frame manager, timestamp %"G_GINT64_FORMAT"\n", timestamp);

	/* Enqueue the decoded frame using frame manager */
	ret = mix_framemanager_enqueue(mix->framemgr, frame);
	if (ret != MIX_RESULT_SUCCESS) {
		LOG_E("Failed to mix_framemanager_enqueue()!\n");
		goto cleanup;
	}

	/* For I or P frames, save this frame off for skipped frame handling */
	if ((frame_type == MP4_VOP_TYPE_I) || (frame_type == MP4_VOP_TYPE_P)) {
		if (self->last_frame != NULL) {
			mix_videoframe_unref(self->last_frame);
		}
		self->last_frame = frame;
		mix_videoframe_ref(frame);
	}

	ret = MIX_RESULT_SUCCESS;

	cleanup:

	if (ret != MIX_RESULT_SUCCESS && frame != NULL) {
		mix_videoframe_unref(frame);
	}

	if (ret != MIX_RESULT_SUCCESS) {
		mix_videoformat_mp42_flush_packed_stream_queue(
				self->packed_stream_queue);
	}

	g_free(buffer_ids);
	mix_videofmt_mp42_release_input_buffers(mix, timestamp);

	if (is_from_queued_data) {
		if (mix_buffer) {
			mix_buffer_unref(mix_buffer);
		}
		mix_videoformat_mp42_free_picture_data(picture_data);
	}

	LOG_V("End\n");

	return ret;
}

MIX_RESULT mix_videofmt_mp42_flush(MixVideoFormat *mix) {

	MIX_RESULT ret = MIX_RESULT_SUCCESS;
	MixVideoFormat_MP42 *self = MIX_VIDEOFORMAT_MP42(mix);
	MixInputBufferEntry *bufentry = NULL;

	LOG_V("Begin\n");

	g_mutex_lock(mix->objectlock);

	mix_videoformat_mp42_flush_packed_stream_queue(self->packed_stream_queue);

	/*
	 * Clear the contents of inputbufqueue
	 */
	while (!g_queue_is_empty(mix->inputbufqueue)) {
		bufentry = (MixInputBufferEntry *) g_queue_pop_head(mix->inputbufqueue);
		if (bufentry == NULL) {
			continue;
		}

		mix_buffer_unref(bufentry->buf);
		g_free(bufentry);
	}

	/*
	 * Clear parse_in_progress flag and current timestamp
	 */
	mix->parse_in_progress = FALSE;
	mix->discontinuity_frame_in_progress = FALSE;
	mix->current_timestamp = 0;

	{
		gint idx = 0;
		for (idx = 0; idx < 2; idx++) {
			if (self->reference_frames[idx] != NULL) {
				mix_videoframe_unref(self->reference_frames[idx]);
				self->reference_frames[idx] = NULL;
			}
		}
	}

	/* Call parser flush */
	vbp_flush(mix->parser_handle);

	g_mutex_unlock(mix->objectlock);

	LOG_V("End\n");

	return ret;
}

MIX_RESULT mix_videofmt_mp42_eos(MixVideoFormat *mix) {

	MIX_RESULT ret = MIX_RESULT_SUCCESS;
	vbp_data_mp42 *data = NULL;
	uint32 vbp_ret = 0;

	LOG_V("Begin\n");

	if (mix == NULL) {
		return MIX_RESULT_NULL_PTR;
	}

	if (!MIX_IS_VIDEOFORMAT_MP42(mix)) {
		return MIX_RESULT_INVALID_PARAM;
	}

	g_mutex_lock(mix->objectlock);

	/* if a frame is in progress, process the frame */
	if (mix->parse_in_progress) {
		/* query for data */
		vbp_ret = vbp_query(mix->parser_handle, (void *) &data);
		LOG_V("vbp_query() returns 0x%x\n", vbp_ret);

		if ((vbp_ret != VBP_OK) || (data == NULL)) {
			ret = MIX_RESULT_FAIL;
			LOG_E("vbp_ret != VBP_OK || data == NULL\n");
			goto cleanup;
		}

		/* process and decode data */
		ret = mix_videofmt_mp42_process_decode(mix, data,
				mix->current_timestamp, mix->discontinuity_frame_in_progress);
		mix->parse_in_progress = FALSE;

	}

	ret = mix_framemanager_eos(mix->framemgr);

	cleanup:

	g_mutex_unlock(mix->objectlock);

	LOG_V("End\n");

	return ret;
}

MIX_RESULT mix_videofmt_mp42_deinitialize(MixVideoFormat *mix) {

	/*
	 * We do the all the cleanup in _finalize
	 */

	MIX_RESULT ret = MIX_RESULT_FAIL;

	LOG_V("Begin\n");

	if (mix == NULL) {
		LOG_V("mix is NULL\n");
		return MIX_RESULT_NULL_PTR;
	}

	if (!MIX_IS_VIDEOFORMAT_MP42(mix)) {
		LOG_V("mix is not mixvideoformat_mp42\n");
		return MIX_RESULT_INVALID_PARAM;
	}

	if (parent_class->deinitialize) {
		ret = parent_class->deinitialize(mix);
	}

	LOG_V("End\n");
	return ret;
}

MIX_RESULT mix_videofmt_mp42_handle_ref_frames(MixVideoFormat *mix,
		enum _picture_type frame_type, MixVideoFrame * current_frame) {

	MixVideoFormat_MP42 *self = MIX_VIDEOFORMAT_MP42(mix);

	LOG_V("Begin\n");

	if (mix == NULL || current_frame == NULL) {
		return MIX_RESULT_NULL_PTR;
	}

	switch (frame_type) {
	case MP4_VOP_TYPE_I:
	case MP4_VOP_TYPE_P:
		LOG_V("Refing reference frame %x\n", (guint) current_frame);

		mix_videoframe_ref(current_frame);

		/* should only happen on first frame */
		if (self->reference_frames[0] == NULL) {
			self->reference_frames[0] = current_frame;
			/* should only happen on second frame */
		} else if (self->reference_frames[1] == NULL) {
			self->reference_frames[1] = current_frame;
		} else {
			LOG_V("Releasing reference frame %x\n",
					(guint) self->reference_frames[0]);
			mix_videoframe_unref(self->reference_frames[0]);
			self->reference_frames[0] = self->reference_frames[1];
			self->reference_frames[1] = current_frame;
		}
		break;
	case MP4_VOP_TYPE_B:
	case MP4_VOP_TYPE_S:
	default:
		break;

	}

	LOG_V("End\n");

	return MIX_RESULT_SUCCESS;
}

MIX_RESULT mix_videofmt_mp42_release_input_buffers(MixVideoFormat *mix,
		guint64 timestamp) {

	MixInputBufferEntry *bufentry = NULL;
	gboolean done = FALSE;

	LOG_V("Begin\n");

	if (mix == NULL) {
		return MIX_RESULT_NULL_PTR;
	}

	/* Dequeue and release all input buffers for this frame */
	LOG_V("Releasing all the MixBuffers for this frame\n");

	/*
	 * While the head of the queue has timestamp == current ts
	 * dequeue the entry, unref the MixBuffer, and free the struct
	 */
	done = FALSE;
	while (!done) {
		bufentry
				= (MixInputBufferEntry *) g_queue_peek_head(mix->inputbufqueue);
		if (bufentry == NULL) {
			break;
		}

		LOG_V("head of queue buf %x, timestamp %"G_GINT64_FORMAT", buffer timestamp %"G_GINT64_FORMAT"\n",
				(guint)bufentry->buf, timestamp, bufentry->timestamp);

		if (bufentry->timestamp != timestamp) {
			LOG_V("buf %x, timestamp %"G_GINT64_FORMAT", buffer timestamp %"G_GINT64_FORMAT"\n",
					(guint)bufentry->buf, timestamp, bufentry->timestamp);

			done = TRUE;
			break;
		}

		bufentry = (MixInputBufferEntry *) g_queue_pop_head(mix->inputbufqueue);
		LOG_V("Unref this MixBuffers %x\n", (guint) bufentry->buf);

		mix_buffer_unref(bufentry->buf);
		g_free(bufentry);
	}

	LOG_V("End\n");

	return MIX_RESULT_SUCCESS;
}

vbp_picture_data_mp42 *mix_videoformat_mp42_clone_picture_data(
		vbp_picture_data_mp42 *picture_data) {

	gboolean succ = FALSE;

	if (!picture_data) {
		return NULL;
	}

	if (picture_data->number_slices == 0) {
		return NULL;
	}

	vbp_picture_data_mp42 *cloned_picture_data = g_try_new0(
			vbp_picture_data_mp42, 1);
	if (cloned_picture_data == NULL) {
		goto cleanup;
	}

	memcpy(cloned_picture_data, picture_data, sizeof(vbp_picture_data_mp42));

	cloned_picture_data->number_slices = picture_data->number_slices;
	cloned_picture_data->slice_data = g_try_new0(vbp_slice_data_mp42,
			picture_data->number_slices);
	if (cloned_picture_data->slice_data == NULL) {
		goto cleanup;
	}

	memcpy(cloned_picture_data->slice_data, picture_data->slice_data,
			sizeof(vbp_slice_data_mp42) * (picture_data->number_slices));

	succ = TRUE;

	cleanup:

	if (!succ) {
		mix_videoformat_mp42_free_picture_data(cloned_picture_data);
		return NULL;
	}

	return cloned_picture_data;
}

void mix_videoformat_mp42_free_picture_data(vbp_picture_data_mp42 *picture_data) {
	if (picture_data) {
		if (picture_data->slice_data) {
			g_free(picture_data->slice_data);
		}
		g_free(picture_data);
	}
}

void mix_videoformat_mp42_flush_packed_stream_queue(GQueue *packed_stream_queue) {

	PackedStream *packed_stream = NULL;

	if (packed_stream_queue == NULL) {
		return;
	}
	while (!g_queue_is_empty(packed_stream_queue)) {
		packed_stream = (PackedStream *) g_queue_pop_head(packed_stream_queue);
		if (packed_stream == NULL) {
			continue;
		}

		if (packed_stream->picture_data) {
			mix_videoformat_mp42_free_picture_data(packed_stream->picture_data);
		}

		if (packed_stream->mix_buffer) {
			mix_buffer_unref(packed_stream->mix_buffer);
		}
		g_free(packed_stream);
	}
}
