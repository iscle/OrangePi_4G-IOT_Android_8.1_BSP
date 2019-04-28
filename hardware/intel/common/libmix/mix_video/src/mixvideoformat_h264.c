/*
 INTEL CONFIDENTIAL
 Copyright 2009 Intel Corporation All Rights Reserved.
 The source code contained or described herein and all documents related to the source code ("Material") are owned by Intel Corporation or its suppliers or licensors. Title to the Material remains with Intel Corporation or its suppliers and licensors. The Material contains trade secrets and proprietary and confidential information of Intel or its suppliers and licensors. The Material is protected by worldwide copyright and trade secret laws and treaty provisions. No part of the Material may be used, copied, reproduced, modified, published, uploaded, posted, transmitted, distributed, or disclosed in any way without Intel’s prior express written permission.

 No license under any patent, copyright, trade secret or other intellectual property right is granted to or conferred upon you by disclosure or delivery of the Materials, either expressly, by implication, inducement, estoppel or otherwise. Any license under such intellectual property rights must be express and approved by Intel in writing.
 */
#include <glib.h>
#include <va/va_x11.h>

#include "mixvideolog.h"
#include "mixvideoformat_h264.h"

#ifdef MIX_LOG_ENABLE
static int mix_video_h264_counter = 0;
#endif /* MIX_LOG_ENABLE */

/* The parent class. The pointer will be saved
 * in this class's initialization. The pointer
 * can be used for chaining method call if needed.
 */
static MixVideoFormatClass *parent_class = NULL;

static void mix_videoformat_h264_finalize(GObject * obj);

/*
 * Please note that the type we pass to G_DEFINE_TYPE is MIX_TYPE_VIDEOFORMAT
 */
G_DEFINE_TYPE (MixVideoFormat_H264, mix_videoformat_h264, MIX_TYPE_VIDEOFORMAT);

static void mix_videoformat_h264_init(MixVideoFormat_H264 * self) {
	MixVideoFormat *parent = MIX_VIDEOFORMAT(self);

	/* public member initialization */
	/* These are all public because MixVideoFormat objects are completely internal to MixVideo,
		no need for private members  */
	self->dpb_surface_table = NULL;

	/* NOTE: we don't need to do this here.
	 * This just demostrates how to access
	 * member varibles beloned to parent
	 */
	parent->initialized = FALSE;
}

static void mix_videoformat_h264_class_init(
		MixVideoFormat_H264Class * klass) {

	/* root class */
	GObjectClass *gobject_class = (GObjectClass *) klass;

	/* direct parent class */
	MixVideoFormatClass *video_format_class =
			MIX_VIDEOFORMAT_CLASS(klass);

	/* parent class for later use */
	parent_class = g_type_class_peek_parent(klass);

	/* setup finializer */
	gobject_class->finalize = mix_videoformat_h264_finalize;

	/* setup vmethods with base implementation */
	/* This is where we can override base class methods if needed */
	video_format_class->getcaps = mix_videofmt_h264_getcaps;
	video_format_class->initialize = mix_videofmt_h264_initialize;
	video_format_class->decode = mix_videofmt_h264_decode;
	video_format_class->flush = mix_videofmt_h264_flush;
	video_format_class->eos = mix_videofmt_h264_eos;
	video_format_class->deinitialize = mix_videofmt_h264_deinitialize;
}

MixVideoFormat_H264 *
mix_videoformat_h264_new(void) {
	MixVideoFormat_H264 *ret =
			g_object_new(MIX_TYPE_VIDEOFORMAT_H264, NULL);

	return ret;
}

void mix_videoformat_h264_finalize(GObject * obj) {
	gint32 pret = VBP_OK;

	/* clean up here. */

	MixVideoFormat *parent = NULL;
	MixVideoFormat_H264 *self = MIX_VIDEOFORMAT_H264(obj);
	GObjectClass *root_class = (GObjectClass *) parent_class;

	parent = MIX_VIDEOFORMAT(self);

	//surfacepool is deallocated by parent
	//inputbufqueue is deallocated by parent
	//parent calls vaDestroyConfig, vaDestroyContext and vaDestroySurfaces

	//Free the DPB surface table
	//First remove all the entries (frames will be unrefed)
	g_hash_table_remove_all(self->dpb_surface_table);
	//Then unref the table
	g_hash_table_unref(self->dpb_surface_table);
	self->dpb_surface_table = NULL;

	g_mutex_lock(parent->objectlock);
	parent->initialized = TRUE;
	parent->parse_in_progress = FALSE;
	parent->current_timestamp = 0;

	//Close the parser
        pret = vbp_close(parent->parser_handle);
	parent->parser_handle = NULL;
	if (pret != VBP_OK)
	{
		LOG_E( "Error closing parser\n");
	}

	g_mutex_unlock(parent->objectlock);

	/* Chain up parent */
	if (root_class->finalize) {
		root_class->finalize(obj);
	}
}

MixVideoFormat_H264 *
mix_videoformat_h264_ref(MixVideoFormat_H264 * mix) {
	return (MixVideoFormat_H264 *) g_object_ref(G_OBJECT(mix));
}

/*  H.264 vmethods implementation */
MIX_RESULT mix_videofmt_h264_getcaps(MixVideoFormat *mix, GString *msg) {

MIX_RESULT ret = MIX_RESULT_SUCCESS;

	if (mix == NULL || msg == NULL)
	{
		LOG_E( "NUll pointer passed in\n");
		return MIX_RESULT_NULL_PTR;
	}

	LOG_V( "Begin\n");

	/* Chainup parent method.
	 */

	if (parent_class->getcaps) {
		ret = parent_class->getcaps(mix, msg);
	}

	LOG_V( "End\n");

	return ret;
}

MIX_RESULT mix_videofmt_h264_initialize(MixVideoFormat *mix, 
		MixVideoConfigParamsDec * config_params,
                MixFrameManager * frame_mgr,
		MixBufferPool * input_buf_pool,
		MixSurfacePool ** surface_pool,
		VADisplay va_display ) {

	uint32 pret = 0;
	MIX_RESULT ret = MIX_RESULT_SUCCESS;
	enum _vbp_parser_type ptype = VBP_H264;
	vbp_data_h264 *data = NULL;
	MixVideoFormat *parent = NULL;
	MixIOVec *header = NULL;
	gint numprofs = 0, numactualprofs = 0;
	gint numentrypts = 0, numactualentrypts = 0;
	VADisplay vadisplay = NULL;
	VAProfile *profiles = NULL;
	VAEntrypoint *entrypts = NULL;
	VAConfigAttrib attrib;
	VAStatus vret = VA_STATUS_SUCCESS;
	guint extra_surfaces = 0;
	VASurfaceID *surfaces = NULL;
	guint numSurfaces = 0;

	//TODO Partition this method into smaller methods

	if (mix == NULL || config_params == NULL || frame_mgr == NULL || input_buf_pool == NULL || va_display == NULL)
	{
		LOG_E( "NUll pointer passed in\n");
		return MIX_RESULT_NULL_PTR;
	}

	LOG_V( "Begin\n");

	/* Chainup parent method. */

	if (parent_class->initialize) {
		ret = parent_class->initialize(mix, config_params,
				frame_mgr, input_buf_pool, surface_pool, 
				va_display);
	}

	if (ret != MIX_RESULT_SUCCESS)
	{
		LOG_E( "Error initializing\n");
		return ret;
	}

	if (!MIX_IS_VIDEOFORMAT_H264(mix))
		return MIX_RESULT_INVALID_PARAM;

	parent = MIX_VIDEOFORMAT(mix);
	MixVideoFormat_H264 *self = MIX_VIDEOFORMAT_H264(mix);

	LOG_V( "Locking\n");
	//From now on, we exit this function through cleanup:
	g_mutex_lock(parent->objectlock);

	LOG_V( "Before vbp_open\n");
	//Load the bitstream parser
	pret = vbp_open(ptype, &(parent->parser_handle));

	LOG_V( "After vbp_open\n");
        if (!(pret == VBP_OK))
	{
		ret = MIX_RESULT_FAIL;
		LOG_E( "Error opening parser\n");
		goto cleanup;
	}
	LOG_V( "Opened parser\n");

	ret = mix_videoconfigparamsdec_get_header(config_params, 
		&header);

        if ((ret != MIX_RESULT_SUCCESS) || (header == NULL))
        {
		ret = MIX_RESULT_FAIL;
		LOG_E( "Cannot get header data\n");
		goto cleanup;
        }

        ret = mix_videoconfigparamsdec_get_extra_surface_allocation(config_params,
                &extra_surfaces);

        if (ret != MIX_RESULT_SUCCESS)
        {
		ret = MIX_RESULT_FAIL;
		LOG_E( "Cannot get extra surface allocation setting\n");
		goto cleanup;
        }

        LOG_V( "Calling parse on header data, handle %d\n", (int)parent->parser_handle);

	pret = vbp_parse(parent->parser_handle, header->data, 
			header->data_size, TRUE);

        if (!((pret == VBP_OK) || (pret == VBP_DONE)))
        {
		ret = MIX_RESULT_FAIL;
		LOG_E( "Error parsing header data\n");
		goto cleanup;
        }

        LOG_V( "Parsed header\n");

       //Get the header data and save
        pret = vbp_query(parent->parser_handle, (void *)&data);

	if ((pret != VBP_OK) || (data == NULL))
	{
		ret = MIX_RESULT_FAIL;
		LOG_E( "Error reading parsed header data\n");
		goto cleanup;
	}

	LOG_V( "Queried parser for header data\n");

	//Time for libva initialization

	vadisplay = parent->va_display;

	numprofs = vaMaxNumProfiles(vadisplay);
	profiles = g_malloc(numprofs*sizeof(VAProfile));

	if (!profiles)
	{
		ret = MIX_RESULT_NO_MEMORY;
		LOG_E( "Error allocating memory\n");
		goto cleanup;
	}

	vret = vaQueryConfigProfiles(vadisplay, profiles, 
		&numactualprofs);
	if (!(vret == VA_STATUS_SUCCESS))
	{
		ret = MIX_RESULT_FAIL;
		LOG_E( "Error initializing video driver\n");
		goto cleanup;
	}

        //check the desired profile support
        gint vaprof = 0;

	//TODO Need to cover more cases
	switch (data->codec_data->profile_idc)
	{
#if 1
//TODO Reinstate this once constraint_set1 flag has been added to codec_data
	case 66: //Baseline profile

	LOG_V( "mix_videofmt_h264_initialize:  Baseline profile\n");
		if (data->codec_data->constraint_set1_flag == 0)
		{
        		for (; vaprof < numactualprofs; vaprof++)
        		{
               			if (profiles[vaprof] == VAProfileH264Baseline)
               	       	 	break;
        		}
		} else
		{
        		for (; vaprof < numactualprofs; vaprof++)
        		{
               			if (profiles[vaprof] == VAProfileH264High)
               	       	 	break;
        		}
		}
		if ((vaprof >= numprofs) || ((profiles[vaprof] != VAProfileH264Baseline) && (profiles[vaprof] != VAProfileH264High)))
		//Did not get the profile we wanted
		{
			ret = MIX_RESULT_FAIL;
			LOG_E( "Profile not supported by driver\n");
			goto cleanup;
		}
		break;
#endif

#if 0
//Code left in place in case bug is fixed in libva
	case 77: //Main profile (need to set to High for libva bug)
	LOG_V( "mix_videofmt_h264_initialize:  Main profile\n");

        	for (; vaprof < numactualprofs; vaprof++)
        	{
               		if (profiles[vaprof] == VAProfileH264Main)
               	        	break;
        	}
		if (vaprof >= numprofs || profiles[vaprof] != VAProfileH264Main)  
		//Did not get the profile we wanted
		{
			ret = MIX_RESULT_FAIL;
			LOG_E( "Profile not supported by driver\n");
			goto cleanup;
		}
		break;
#endif

	case 100: //High profile
	default:  //Set to High as default

	LOG_V( "High profile\n");

        	for (; vaprof < numactualprofs; vaprof++)
        	{
               		if (profiles[vaprof] == VAProfileH264High)
               	        	break;
        	}
		if (vaprof >= numprofs || profiles[vaprof] != VAProfileH264High)
		//Did not get the profile we wanted
		{
			ret = MIX_RESULT_FAIL;
			LOG_E( "Profile not supported by driver\n");
			goto cleanup;
		}
		break;


	}

	numentrypts = vaMaxNumEntrypoints(vadisplay);
	entrypts = g_malloc(numentrypts*sizeof(VAEntrypoint));

	if (!entrypts)
	{
		ret = MIX_RESULT_NO_MEMORY;
		LOG_E( "Error allocating memory\n");
		goto cleanup;
	}

	vret = vaQueryConfigEntrypoints(vadisplay, profiles[vaprof], 
		entrypts, &numactualentrypts);
	if (!(vret == VA_STATUS_SUCCESS))
	{
		ret = MIX_RESULT_FAIL;
		LOG_E( "Error initializing driver\n");
		goto cleanup;
	}

	gint vaentrypt = 0;
	for (; vaentrypt < numactualentrypts; vaentrypt++)
	{
		if (entrypts[vaentrypt] == VAEntrypointVLD)
			break;
	}
	if (vaentrypt >= numentrypts || entrypts[vaentrypt] != VAEntrypointVLD)  
	//Did not get the entrypt we wanted
	{
		ret = MIX_RESULT_FAIL;
		LOG_E( "Entry point not supported by driver\n");
		goto cleanup;
	}

	//We are requesting RT attributes
	attrib.type = VAConfigAttribRTFormat;

	vret = vaGetConfigAttributes(vadisplay, profiles[vaprof], 
		entrypts[vaentrypt], &attrib, 1);

        //TODO Handle other values returned for RT format
        // and check with requested format provided in config params
        //Right now only YUV 4:2:0 is supported by libva
        // and this is our default
        if (((attrib.value & VA_RT_FORMAT_YUV420) == 0) ||
                vret != VA_STATUS_SUCCESS)
        {
		ret = MIX_RESULT_FAIL;
		LOG_E( "Error initializing driver\n");
		goto cleanup;
        }

	//Initialize and save the VA config ID
	vret = vaCreateConfig(vadisplay, profiles[vaprof], 
		entrypts[vaentrypt], &attrib, 1, &(parent->va_config));

	if (!(vret == VA_STATUS_SUCCESS))
	{
		ret = MIX_RESULT_FAIL;
		LOG_E( "Error initializing driver\n");
		goto cleanup;
	}

	LOG_V( "Created libva config with profile %d\n", vaprof);


	//Initialize the surface pool

	LOG_V( "Codec data says num_ref_frames is %d\n", data->codec_data->num_ref_frames);


	// handle both frame and field coding for interlaced content
	int num_ref_pictures = data->codec_data->num_ref_frames;
	if (!data->codec_data->frame_mbs_only_flag &&
		!data->codec_data->mb_adaptive_frame_field_flag)
	{
		
		// field coding, two fields share the same surface.	
		//num_ref_pictures *= 2;			
	}

	//Adding 1 to work around VBLANK issue
	parent->va_num_surfaces = 1 + extra_surfaces + (((num_ref_pictures + 3) <
		MIX_VIDEO_H264_SURFACE_NUM) ? 
		(num_ref_pictures + 3)
		: MIX_VIDEO_H264_SURFACE_NUM);
		
	numSurfaces = parent->va_num_surfaces;
	
	parent->va_surfaces = g_malloc(sizeof(VASurfaceID)*numSurfaces);

	surfaces = parent->va_surfaces;

	if (surfaces == NULL)
	{
		ret = MIX_RESULT_FAIL;
		LOG_E( "Cannot allocate temporary data\n");
		goto cleanup;
	}

	LOG_V( "Codec data says picture size is %d x %d\n", (data->pic_data[0].pic_parms->picture_width_in_mbs_minus1 + 1) * 16, (data->pic_data[0].pic_parms->picture_height_in_mbs_minus1 + 1) * 16);
	LOG_V( "getcaps says picture size is %d x %d\n", parent->picture_width, parent->picture_height);

	vret = vaCreateSurfaces(vadisplay, (data->pic_data[0].pic_parms->picture_width_in_mbs_minus1 + 1) * 16, 
		(data->pic_data[0].pic_parms->picture_height_in_mbs_minus1 + 1) * 16, entrypts[vaentrypt],
		numSurfaces, surfaces);

	if (!(vret == VA_STATUS_SUCCESS))
	{
		ret = MIX_RESULT_FAIL;
		LOG_E( "Error allocating surfaces\n");
		goto cleanup;
	}

	parent->surfacepool = mix_surfacepool_new();
	*surface_pool = parent->surfacepool;

	if (parent->surfacepool == NULL)
	{
		ret = MIX_RESULT_FAIL;
		LOG_E( "Error initializing surface pool\n");
		goto cleanup;
	}
	

	ret = mix_surfacepool_initialize(parent->surfacepool,
		surfaces, numSurfaces);

	switch (ret)
	{
		case MIX_RESULT_SUCCESS:
			break;
		case MIX_RESULT_ALREADY_INIT:  //This case is for future use when we can be  initialized multiple times.  It is to detect when we have not been reset before re-initializing.
		default:
			ret = MIX_RESULT_ALREADY_INIT;
			LOG_E( "Error init failure\n");
			goto cleanup;
                        break;
	}

	LOG_V( "Created %d libva surfaces\n", numSurfaces);

        //Initialize and save the VA context ID
        //Note: VA_PROGRESSIVE libva flag is only relevant to MPEG2
        vret = vaCreateContext(vadisplay, parent->va_config,
                parent->picture_width, parent->picture_height,
                0, surfaces, numSurfaces,
                &(parent->va_context));
	if (!(vret == VA_STATUS_SUCCESS))
	{
		ret = MIX_RESULT_FAIL;
		LOG_E( "Error initializing video driver\n");
		goto cleanup;
	}

	LOG_V( "Created libva context width %d, height %d\n", parent->picture_width, parent->picture_height);

	//Create our table of Decoded Picture Buffer "in use" surfaces
	self->dpb_surface_table = g_hash_table_new_full(NULL, NULL, mix_videofmt_h264_destroy_DPB_key, mix_videofmt_h264_destroy_DPB_value);

	if (self->dpb_surface_table == NULL)
	{
		ret = MIX_RESULT_NO_MEMORY;
		LOG_E( "Error allocating dbp surface table\n");
		goto cleanup;  //leave this goto here in case other code is added between here and cleanup label
	}

	cleanup:
	if (ret != MIX_RESULT_SUCCESS) {
		pret = vbp_close(parent->parser_handle);
		parent->parser_handle = NULL;
       		parent->initialized = FALSE;

	} else {
	         parent->initialized = TRUE;
	}

	if (header != NULL)
	{
		if (header->data != NULL)
			g_free(header->data);
		g_free(header);
		header = NULL;
	}

	g_free(profiles);
        g_free(entrypts);

	LOG_V( "Unlocking\n");
        g_mutex_unlock(parent->objectlock);


	return ret;
}

MIX_RESULT mix_videofmt_h264_decode(MixVideoFormat *mix, MixBuffer * bufin[],
                gint bufincnt, MixVideoDecodeParams * decode_params) {

        uint32 pret = 0;
	int i = 0;
        MixVideoFormat *parent = NULL;
	MIX_RESULT ret = MIX_RESULT_SUCCESS;
	guint64 ts = 0;
	vbp_data_h264 *data = NULL;
	gboolean discontinuity = FALSE;
	MixInputBufferEntry *bufentry = NULL;

        LOG_V( "Begin\n");

        if (mix == NULL || bufin == NULL || decode_params == NULL )
	{
		LOG_E( "NUll pointer passed in\n");
                return MIX_RESULT_NULL_PTR;
	}

	/* Chainup parent method.
		We are not chaining up to parent method for now.
	 */

#if 0
        if (parent_class->decode) {
                return parent_class->decode(mix, bufin, bufincnt,
                                        decode_params);
	}
#endif

	if (!MIX_IS_VIDEOFORMAT_H264(mix))
		return MIX_RESULT_INVALID_PARAM;

	parent = MIX_VIDEOFORMAT(mix);


	ret = mix_videodecodeparams_get_timestamp(decode_params, 
			&ts);
	if (ret != MIX_RESULT_SUCCESS)
	{
		return MIX_RESULT_FAIL;
	}

	ret = mix_videodecodeparams_get_discontinuity(decode_params, 
			&discontinuity);
	if (ret != MIX_RESULT_SUCCESS)
	{
		return MIX_RESULT_FAIL;
	}

	//From now on, we exit this function through cleanup:

	LOG_V( "Locking\n");
        g_mutex_lock(parent->objectlock);

	LOG_V( "parse in progress is %d\n", parent->parse_in_progress);
	//If this is a new frame and we haven't retrieved parser
	//  workload data from previous frame yet, do so
	if ((ts != parent->current_timestamp) && 
			(parent->parse_in_progress))
	{

		//query for data
		pret = vbp_query(parent->parser_handle,
			(void *) &data);

		if ((pret != VBP_OK) || (data == NULL))
        	{
			ret = MIX_RESULT_FAIL;
			LOG_E( "Error initializing parser\n");
               		goto cleanup;
        	}
	
		LOG_V( "Queried for last frame data\n");

		//process and decode data
		ret = mix_videofmt_h264_process_decode(mix,
			data, parent->current_timestamp, 
			parent->discontinuity_frame_in_progress);

		if (ret != MIX_RESULT_SUCCESS)
        	{
			//We log this but need to process the new frame data, so do not return
			LOG_E( "Process_decode failed.\n");
        	}

		LOG_V( "Called process and decode for last frame\n");

		parent->parse_in_progress = FALSE;

	}

	parent->current_timestamp = ts;
	parent->discontinuity_frame_in_progress = discontinuity;

	LOG_V( "Starting current frame %d, timestamp %"G_GINT64_FORMAT"\n", mix_video_h264_counter++, ts);

	for (i = 0; i < bufincnt; i++)
	{

		LOG_V( "Calling parse for current frame, parse handle %d, buf %x, size %d\n", (int)parent->parser_handle, (guint)bufin[i]->data, bufin[i]->size);

		pret = vbp_parse(parent->parser_handle, 
			bufin[i]->data, 
			bufin[i]->size,
			FALSE);

		LOG_V( "Called parse for current frame\n");

		if ((pret == VBP_DONE) || (pret == VBP_OK))
		{
			//query for data
			pret = vbp_query(parent->parser_handle,
				(void *) &data);

			if ((pret != VBP_OK) || (data == NULL))
        		{
				ret = MIX_RESULT_FAIL;
				LOG_E( "Error getting parser data\n");
               			goto cleanup;
        		}

			LOG_V( "Called query for current frame\n");

			//Increase the ref count of this input buffer
			mix_buffer_ref(bufin[i]);

			//Create a new MixInputBufferEntry
			//TODO make this from a pool to optimize
			bufentry = g_malloc(sizeof(
				MixInputBufferEntry));
			if (bufentry == NULL)
        		{
				ret = MIX_RESULT_NO_MEMORY;
				LOG_E( "Error allocating bufentry\n");
               			goto cleanup;
        		}

			bufentry->buf = bufin[i];
	LOG_V( "Setting bufentry %x for mixbuffer %x ts to %"G_GINT64_FORMAT"\n", (guint)bufentry, (guint)bufentry->buf, ts);
			bufentry->timestamp = ts;

			LOG_V( "Enqueue this input buffer for current frame\n");
			LOG_V( "bufentry->timestamp %"G_GINT64_FORMAT"\n", bufentry->timestamp);

			//Enqueue this input buffer
			g_queue_push_tail(parent->inputbufqueue, 
				(gpointer)bufentry);

			//process and decode data
			ret = mix_videofmt_h264_process_decode(mix,
				data, ts, discontinuity);

			if (ret != MIX_RESULT_SUCCESS)
                	{
				//We log this but continue since we need to complete our processing of input buffers
				LOG_E( "Process_decode failed.\n");
                	}

			LOG_V( "Called process and decode for current frame\n");

			parent->parse_in_progress = FALSE;
		}
		else if (pret != VBP_OK)
        	{
			//We log this but continue since we need to complete our processing of input buffers
			LOG_E( "Parsing failed.\n");
			ret = MIX_RESULT_FAIL;
        	}
		else
		{

			LOG_V( "Enqueuing buffer and going on to next (if any) for this frame\n");

			//Increase the ref count of this input buffer
			mix_buffer_ref(bufin[i]);

			//Create a new MixInputBufferEntry
			//TODO make this from a pool to optimize
			bufentry = g_malloc(sizeof
				(MixInputBufferEntry));
			if (bufentry == NULL)
        		{
				ret = MIX_RESULT_NO_MEMORY;
				LOG_E( "Error allocating bufentry\n");
               			goto cleanup;
        		}
			bufentry->buf = bufin[i];
	LOG_V( "Setting bufentry %x for mixbuffer %x ts to %"G_GINT64_FORMAT"\n", (guint)bufentry, (guint)bufentry->buf, ts);
			bufentry->timestamp = ts;

			LOG_V( "Enqueue this input buffer for current frame\n");
			LOG_V( "bufentry->timestamp %"G_GINT64_FORMAT"\n", bufentry->timestamp);

			//Enqueue this input buffer
			g_queue_push_tail(parent->inputbufqueue, 
				(gpointer)bufentry);
	LOG_V( "Setting parse_in_progress to TRUE\n");
			parent->parse_in_progress = TRUE;
		}

	}


	cleanup:

	LOG_V( "Unlocking\n");
 	g_mutex_unlock(parent->objectlock);

        LOG_V( "End\n");

	return ret;
}

MIX_RESULT mix_videofmt_h264_flush(MixVideoFormat *mix) {

MIX_RESULT ret = MIX_RESULT_SUCCESS;

	LOG_V( "Begin\n");

	if (mix == NULL)
	{
		LOG_E( "Null pointer passed in\n");
		return MIX_RESULT_NULL_PTR;
	}

        uint32 pret = 0;
	MixInputBufferEntry *bufentry = NULL;


	/* Chainup parent method.
		We are not chaining up to parent method for now.
	 */

#if 0
	if (parent_class->flush) {
		return parent_class->flush(mix, msg);
	}
#endif

	MixVideoFormat_H264 *self = MIX_VIDEOFORMAT_H264(mix);

        g_mutex_lock(mix->objectlock);

	//Clear the contents of inputbufqueue
	while (!g_queue_is_empty(mix->inputbufqueue))
	{
		bufentry = (MixInputBufferEntry *) g_queue_pop_head(
				mix->inputbufqueue);
		if (bufentry == NULL) continue;

		mix_buffer_unref(bufentry->buf);
		g_free(bufentry);
	}

	//Clear parse_in_progress flag and current timestamp
        mix->parse_in_progress = FALSE;
	mix->discontinuity_frame_in_progress = FALSE;
	mix->current_timestamp = 0;

	//Clear the DPB surface table
	g_hash_table_remove_all(self->dpb_surface_table);

	//Call parser flush
	pret = vbp_flush(mix->parser_handle);
	if (pret != VBP_OK)
		ret = MIX_RESULT_FAIL;

        g_mutex_unlock(mix->objectlock);

	LOG_V( "End\n");

	return ret;
}

MIX_RESULT mix_videofmt_h264_eos(MixVideoFormat *mix) {

	MIX_RESULT ret = MIX_RESULT_SUCCESS;
	vbp_data_h264 *data = NULL;
        uint32 pret = 0;

        LOG_V( "Begin\n");

	if (mix == NULL)
	{
		LOG_E( "Null pointer passed in\n");
		return MIX_RESULT_NULL_PTR;
	}

	/* Chainup parent method.
		We are not chaining up to parent method for now.
	 */

#if 0
	if (parent_class->eos) {
		return parent_class->eos(mix, msg);
	}
#endif

        g_mutex_lock(mix->objectlock);

	//if a frame is in progress, process the frame
	if (mix->parse_in_progress)
	{
		//query for data
		pret = vbp_query(mix->parser_handle,
			(void *) &data);

		if ((pret != VBP_OK) || (data == NULL))
               	{
               		ret = MIX_RESULT_FAIL;
 			LOG_E( "Error getting last parse data\n");
			goto cleanup;
               	}

		//process and decode data
		ret = mix_videofmt_h264_process_decode(mix,
			data, mix->current_timestamp, 
			mix->discontinuity_frame_in_progress);
		mix->parse_in_progress = FALSE;
		if (ret != MIX_RESULT_SUCCESS)
		{
 			LOG_E( "Error processing last frame\n");
			goto cleanup;
		}

	}

cleanup:

        g_mutex_unlock(mix->objectlock);

	//Call Frame Manager with _eos()
	ret = mix_framemanager_eos(mix->framemgr);

	LOG_V( "End\n");

	return ret;


}

MIX_RESULT mix_videofmt_h264_deinitialize(MixVideoFormat *mix) {

//Note this method is not called; may remove in future

	LOG_V( "Begin\n");

	if (mix == NULL)
	{
		LOG_E( "Null pointer passed in\n");
		return MIX_RESULT_NULL_PTR;
	}

	/* Chainup parent method.
	 */

	if (parent_class->deinitialize) {
		return parent_class->deinitialize(mix);
	}

        //Most stuff is cleaned up in parent_class->finalize() and in _finalize

        LOG_V( "End\n");

	return MIX_RESULT_SUCCESS;
}
#define HACK_DPB
#ifdef HACK_DPB
static inline void mix_videofmt_h264_hack_dpb(MixVideoFormat *mix, 
					vbp_picture_data_h264* pic_data
					) 
{

	gboolean found = FALSE;
	guint tflags = 0;
	VAPictureParameterBufferH264 *pic_params = pic_data->pic_parms;
	VAPictureH264 *pRefList = NULL;
	int i = 0, j = 0, k = 0, list = 0;

	MixVideoFormat_H264 *self = MIX_VIDEOFORMAT_H264(mix);

	//Set the surface ID for everything in the parser DPB to INVALID
	for (i = 0; i < 16; i++)
	{
		pic_params->ReferenceFrames[i].picture_id = VA_INVALID_SURFACE;
		pic_params->ReferenceFrames[i].frame_idx = -1; 
		pic_params->ReferenceFrames[i].TopFieldOrderCnt = -1; 
		pic_params->ReferenceFrames[i].BottomFieldOrderCnt = -1; 
		pic_params->ReferenceFrames[i].flags = VA_PICTURE_H264_INVALID;  //assuming we don't need to OR with existing flags
	}

	pic_params->num_ref_frames = 0;

	for (i = 0; i < pic_data->num_slices; i++)
	{

		//Copy from the List0 and List1 surface IDs
		pRefList = pic_data->slc_data[i].slc_parms.RefPicList0;
		for (list = 0; list < 2; list++)
		{
			for (j = 0; j < 32; j++)  
			{
				if (pRefList[j].flags & VA_PICTURE_H264_INVALID)
				{
					break;  //no more valid reference frames in this list
				}
				found = FALSE;
				for (k = 0; k < pic_params->num_ref_frames; k++)
				{
					if (pic_params->ReferenceFrames[k].TopFieldOrderCnt == pRefList[j].TopFieldOrderCnt)
					{
						///check for complementary field
						tflags = pic_params->ReferenceFrames[k].flags | pRefList[j].flags;
						//If both TOP and BOTTOM are set, we'll clear those flags
						if ((tflags & VA_PICTURE_H264_TOP_FIELD) &&
							(tflags & VA_PICTURE_H264_TOP_FIELD))
							pic_params->ReferenceFrames[k].flags = VA_PICTURE_H264_SHORT_TERM_REFERENCE;
						found = TRUE;  //already in the DPB; will not add this one
						break;
					}
				}
				if (!found)
				{
					guint poc = mix_videofmt_h264_get_poc(&(pRefList[j]));
					gpointer video_frame = g_hash_table_lookup(self->dpb_surface_table, (gpointer)poc);
					pic_params->ReferenceFrames[pic_params->num_ref_frames].picture_id = 
						((MixVideoFrame *)video_frame)->frame_id;

        				LOG_V( "Inserting frame id %d into DPB\n", pic_params->ReferenceFrames[pic_params->num_ref_frames].picture_id);

					pic_params->ReferenceFrames[pic_params->num_ref_frames].flags = 
						pRefList[j].flags;
					pic_params->ReferenceFrames[pic_params->num_ref_frames].frame_idx = 
						pRefList[j].frame_idx;
					pic_params->ReferenceFrames[pic_params->num_ref_frames].TopFieldOrderCnt = 
						pRefList[j].TopFieldOrderCnt;
					pic_params->ReferenceFrames[pic_params->num_ref_frames++].BottomFieldOrderCnt = 
						pRefList[j].BottomFieldOrderCnt;
				}

			}
		pRefList = pic_data->slc_data[i].slc_parms.RefPicList1;
		}

	}
}
#endif

					
MIX_RESULT mix_videofmt_h264_process_decode_picture(MixVideoFormat *mix,
					vbp_data_h264 *data, 
					guint64 timestamp,
					gboolean discontinuity,
					int pic_index,
					MixVideoFrame *frame)
{

	MIX_RESULT ret = MIX_RESULT_SUCCESS;
	VAStatus vret = VA_STATUS_SUCCESS;
	VADisplay vadisplay = NULL;
	VAContextID vacontext;
	guint buffer_id_cnt = 0;
	VABufferID *buffer_ids = NULL;

	//TODO Partition this method into smaller methods

	LOG_V( "Begin\n");

	if ((mix == NULL) || (data == NULL) || (data->pic_data == NULL) || (frame == NULL))
	{
		LOG_E( "Null pointer passed in\n");
		return MIX_RESULT_NULL_PTR;
	}

	vbp_picture_data_h264* pic_data = &(data->pic_data[pic_index]);
	
	
	//After this point, all exits from this function are through cleanup:

	if (!MIX_IS_VIDEOFORMAT_H264(mix))
		return MIX_RESULT_INVALID_PARAM;

	MixVideoFormat_H264 *self = MIX_VIDEOFORMAT_H264(mix);

	VAPictureParameterBufferH264 *pic_params = pic_data->pic_parms;

	if (pic_params == NULL) 
	{
		ret = MIX_RESULT_NULL_PTR;
		LOG_E( "Error reading parser data\n");
		goto cleanup;
	}

	//TODO
	//Check for frame gaps and repeat frames if necessary

	LOG_V( "num_slices is %d, allocating %d buffer_ids\n", pic_data->num_slices, (pic_data->num_slices * 2) + 2);

	buffer_ids = g_malloc(sizeof(VABufferID) * 
					((pic_data->num_slices * 2) + 2));

	if (buffer_ids == NULL) 
	{
		LOG_E( "Cannot allocate buffer IDs\n");
		ret = MIX_RESULT_NO_MEMORY;
		goto cleanup;
	}

	//Set up reference frames for the picture parameter buffer

	//Set the picture type (I, B or P frame)
	//For H.264 we use the first encountered slice type for this (check - may need to change later to search all slices for B type)
	MixFrameType frame_type = TYPE_INVALID;

	switch (pic_data->slc_data->slc_parms.slice_type)
	{
		case 0:
		case 3:
		case 5:
		case 8:
			frame_type = TYPE_P;
			break;
		case 1:
		case 6:
			frame_type = TYPE_B;
			break;
		case 2:
		case 4:
		case 7:
		case 9:
			frame_type = TYPE_I;
			break;
		default:
			break;
	}

	//Do not have to check for B frames after a seek
	//Note:  Demux should seek to IDR (instantaneous decoding refresh) frame, otherwise
	//  DPB will not be correct and frames may come in with invalid references
	//  This will be detected when DPB is checked for valid mapped surfaces and 
	//  error returned from there.

	LOG_V( "Getting a new surface for frame_num %d\n", pic_params->frame_num);
	LOG_V( "frame type is %d\n", frame_type);



	//Set the frame type for the frame object (used in reordering by frame manager)
	ret = mix_videoframe_set_frame_type(frame, frame_type);

	if (ret != MIX_RESULT_SUCCESS)
	{
		LOG_E( "Error setting frame type on frame\n");
		goto cleanup;
	}

	LOG_V( "Updating DPB for libva\n");

	//Now handle the reference frames and surface IDs for DPB and current frame
	mix_videofmt_h264_handle_ref_frames(mix, pic_params, frame);

#ifdef HACK_DPB
	//We have to provide a hacked DPB rather than complete DPB for libva as workaround
	mix_videofmt_h264_hack_dpb(mix, pic_data);
#endif

	//Libva buffer set up

	vadisplay = mix->va_display;
	vacontext = mix->va_context;

	LOG_V( "Creating libva picture parameter buffer\n");
	LOG_V( "picture parameter buffer shows num_ref_frames is %d\n", pic_params->num_ref_frames);

	//First the picture parameter buffer
	vret = vaCreateBuffer(vadisplay, vacontext,
			VAPictureParameterBufferType,
			sizeof(VAPictureParameterBufferH264),
			1,
			pic_params,
			&buffer_ids[buffer_id_cnt]);
	buffer_id_cnt++;

	if (vret != VA_STATUS_SUCCESS)
	{
		ret = MIX_RESULT_FAIL;
		LOG_E( "Video driver returned error from vaCreateBuffer\n");
		goto cleanup;
	}
			
	LOG_V( "Creating libva IQMatrix buffer\n");


	//Then the IQ matrix buffer
    	vret = vaCreateBuffer(vadisplay, vacontext,
                    VAIQMatrixBufferType,
                    sizeof(VAIQMatrixBufferH264),
                    1,
                    data->IQ_matrix_buf,
                    &buffer_ids[buffer_id_cnt]);
	buffer_id_cnt++;

	if (vret != VA_STATUS_SUCCESS)
	{
		ret = MIX_RESULT_FAIL;
		LOG_E( "Video driver returned error from vaCreateBuffer\n");
		goto cleanup;
	}


	//Now for slices
	int i = 0;
	gpointer video_frame;
	for (;i < pic_data->num_slices; i++)
	{
	
		LOG_V( "Creating libva slice parameter buffer, for slice %d\n", i);

		//Do slice parameters

		//First patch up the List0 and List1 surface IDs
		int j = 0;
		guint poc = 0;
		for (; j <= pic_data->slc_data[i].slc_parms.num_ref_idx_l0_active_minus1; j++)
		{
			if (!(pic_data->slc_data[i].slc_parms.RefPicList0[j].flags & VA_PICTURE_H264_INVALID))
			{
				poc = mix_videofmt_h264_get_poc(&(pic_data->slc_data[i].slc_parms.RefPicList0[j]));
				video_frame = g_hash_table_lookup(self->dpb_surface_table, (gpointer)poc);
				if (video_frame == NULL)
				{
					LOG_E(  "unable to find surface of picture %d (current picture %d).", poc, mix_videofmt_h264_get_poc(&pic_params->CurrPic));
					ret = MIX_RESULT_FAIL;
					goto cleanup;
				}
				else
				{
					pic_data->slc_data[i].slc_parms.RefPicList0[j].picture_id = 
						((MixVideoFrame *)video_frame)->frame_id;
				}
			}

		}

		if ((pic_data->slc_data->slc_parms.slice_type == 1) || (pic_data->slc_data->slc_parms.slice_type == 6))
		{
			for (j = 0; j <= pic_data->slc_data[i].slc_parms.num_ref_idx_l1_active_minus1; j++)
			{
				if (!(pic_data->slc_data[i].slc_parms.RefPicList1[j].flags & VA_PICTURE_H264_INVALID))
				{
					poc = mix_videofmt_h264_get_poc(&(pic_data->slc_data[i].slc_parms.RefPicList1[j]));
					video_frame = g_hash_table_lookup(self->dpb_surface_table, (gpointer)poc);
					if (video_frame == NULL)
					{
						LOG_E(  "unable to find surface of picture %d (current picture %d).", poc, mix_videofmt_h264_get_poc(&pic_params->CurrPic));
						ret = MIX_RESULT_FAIL;
						goto cleanup;
					}
					else
					{						
						pic_data->slc_data[i].slc_parms.RefPicList1[j].picture_id = 
							((MixVideoFrame *)video_frame)->frame_id;
					}
				}
			}
		}


		//Then do the libva setup

	       	vret = vaCreateBuffer(vadisplay, vacontext,
			 VASliceParameterBufferType,
			 sizeof(VASliceParameterBufferH264),
			 1,
	       	         &(pic_data->slc_data[i].slc_parms),
	       	         &buffer_ids[buffer_id_cnt]);

		if (vret != VA_STATUS_SUCCESS)
		{
			ret = MIX_RESULT_FAIL;
			LOG_E( "Video driver returned error from vaCreateBuffer\n");
			goto cleanup;
		}

	    	buffer_id_cnt++;


		LOG_V( "Creating libva slice data buffer for slice %d, using slice address %x, with offset %d and size %u\n", i, (guint)pic_data->slc_data[i].buffer_addr, pic_data->slc_data[i].slc_parms.slice_data_offset, pic_data->slc_data[i].slice_size);


		//Do slice data

      		vret = vaCreateBuffer(vadisplay, vacontext,
       	       	  VASliceDataBufferType,
		  //size
		  pic_data->slc_data[i].slice_size,
		  //num_elements
       	       	  1,
		  //slice data buffer pointer
		  //Note that this is the original data buffer ptr;
		  // offset to the actual slice data is provided in
		  // slice_data_offset in VASliceParameterBufferH264
		  pic_data->slc_data[i].buffer_addr + pic_data->slc_data[i].slice_offset,
      	       	  &buffer_ids[buffer_id_cnt]);

       	 	buffer_id_cnt++;

       	 	if (vret != VA_STATUS_SUCCESS)
		{
			ret = MIX_RESULT_FAIL;
 			LOG_E( "Video driver returned error from vaCreateBuffer\n");
			goto cleanup;
		}

	}

	gulong surface = 0;

	//Get our surface ID from the frame object
	ret = mix_videoframe_get_frame_id(frame, &surface);

	if (ret != MIX_RESULT_SUCCESS)
	{
		LOG_E( "Error getting surface ID from frame object\n");
		goto cleanup;
	}

	LOG_V( "Calling vaBeginPicture\n");

	//Now we can begin the picture
      	vret = vaBeginPicture(vadisplay, vacontext, surface);

       	if (vret != VA_STATUS_SUCCESS)
	{
		ret = MIX_RESULT_FAIL;
		LOG_E( "Video driver returned error from vaBeginPicture\n");
		goto cleanup;
	}

	LOG_V( "Calling vaRenderPicture\n");

	//Render the picture
      	vret = vaRenderPicture(vadisplay, vacontext,
      	     		buffer_ids,
			buffer_id_cnt);


       	if (vret != VA_STATUS_SUCCESS)
	{
		ret = MIX_RESULT_FAIL;
		LOG_E( "Video driver returned error from vaRenderPicture\n");
		goto cleanup;
	}

	LOG_V( "Calling vaEndPicture\n");

	//End picture
	vret = vaEndPicture(vadisplay, vacontext);

       	if (vret != VA_STATUS_SUCCESS)
	{
		ret = MIX_RESULT_FAIL;
		LOG_E( "Video driver returned error from vaEndPicture\n");
		goto cleanup;
	}

	LOG_V( "Calling vaSyncSurface\n");

	//Decode the picture
      	vret = vaSyncSurface(vadisplay, surface);

       	if (vret != VA_STATUS_SUCCESS)
	{
		ret = MIX_RESULT_FAIL;
		LOG_E( "Video driver returned error from vaSyncSurface\n");
		goto cleanup;
	}


	if (pic_index == 0)
	{
		//Set the discontinuity flag
		mix_videoframe_set_discontinuity(frame, discontinuity);

		//Set the timestamp
		mix_videoframe_set_timestamp(frame, timestamp);
		
		guint32 frame_structure = VA_FRAME_PICTURE;
		if (pic_params->CurrPic.flags & VA_PICTURE_H264_TOP_FIELD)
		{
			frame_structure =  VA_TOP_FIELD;
		}
		else if (pic_params->CurrPic.flags & VA_PICTURE_H264_BOTTOM_FIELD)
		{
			frame_structure = VA_BOTTOM_FIELD;
		}		
		mix_videoframe_set_frame_structure(frame, frame_structure);	
	}
	else
	{
		// frame must be field-coded, no need to set
		// discontinuity falg and time stamp again
		mix_videoframe_set_frame_structure(frame, VA_BOTTOM_FIELD | VA_TOP_FIELD);
	}
	
	//TODO need to save off frame when handling is added for repeat frames?

//TODO Complete YUVDUMP code and move into base class
#ifdef YUVDUMP
	if (mix_video_h264_counter < 10)
		ret = GetImageFromSurface (mix, frame);
//		g_usleep(5000000);
#endif  /* YUVDUMP */

	LOG_V( "Enqueueing the frame with frame manager, timestamp %"G_GINT64_FORMAT"\n", timestamp);


	cleanup:

	if (NULL != buffer_ids)
		g_free(buffer_ids);


	LOG_V( "End\n");

	return ret;

}


MIX_RESULT mix_videofmt_h264_process_decode(MixVideoFormat *mix,
					vbp_data_h264 *data, 
					guint64 timestamp,
					gboolean discontinuity)
{
	MIX_RESULT ret = MIX_RESULT_SUCCESS;
	int i = 0;	
	
	if ((mix == NULL) || (data == NULL))
	{
		LOG_E( "Null pointer passed in\n");
		return MIX_RESULT_NULL_PTR;
	}

	//Get a frame from the surface pool
	MixVideoFrame *frame = NULL;

	ret = mix_surfacepool_get(mix->surfacepool, &frame);

	if (ret != MIX_RESULT_SUCCESS)
	{
		LOG_E( "Error getting frame from surfacepool\n");
		return MIX_RESULT_FAIL;
	}

	
	for (i = 0; i < data->num_pictures; i++)
	{
		ret = mix_videofmt_h264_process_decode_picture(mix, data, timestamp, discontinuity, i, frame);
		if (ret != 	MIX_RESULT_SUCCESS)
		{
			LOG_E( "Failed to process decode picture %d, error =  %#X.", data->buf_number, ret);
			break;
		}		
	}
	
	if (ret == MIX_RESULT_SUCCESS)
	{
		//Enqueue the decoded frame using frame manager
		ret = mix_framemanager_enqueue(mix->framemgr, frame);
		if (ret != MIX_RESULT_SUCCESS)
               	{
 			LOG_E( "Error enqueuing frame object\n");
			mix_videoframe_unref(frame);
               	}
		
	}
	else
	{
		mix_videoframe_unref(frame);
	}
	mix_videofmt_h264_release_input_buffers(mix, timestamp);
	
	return ret;
}

MIX_RESULT mix_videofmt_h264_handle_ref_frames(MixVideoFormat *mix, 
					VAPictureParameterBufferH264* pic_params,
					MixVideoFrame * current_frame
					) {

	guint poc = 0;

	LOG_V( "Begin\n");

        if (mix == NULL || current_frame == NULL || pic_params == NULL)
	{
		LOG_E( "Null pointer passed in\n");
		return MIX_RESULT_NULL_PTR;
	}


	LOG_V( "Pic_params has flags %d, topfieldcnt %d, bottomfieldcnt %d.  Surface ID is %d\n", pic_params->CurrPic.flags, pic_params->CurrPic.TopFieldOrderCnt, pic_params->CurrPic.BottomFieldOrderCnt, (gint) current_frame->frame_id);

#ifdef MIX_LOG_ENABLE
	if (pic_params->CurrPic.flags & VA_PICTURE_H264_INVALID)
		LOG_V( "Flags show VA_PICTURE_H264_INVALID\n");

	if (pic_params->CurrPic.flags & VA_PICTURE_H264_TOP_FIELD)
		LOG_V( "Flags show VA_PICTURE_H264_TOP_FIELD\n");

	if (pic_params->CurrPic.flags & VA_PICTURE_H264_BOTTOM_FIELD)
		LOG_V( "Flags show VA_PICTURE_H264_BOTTOM_FIELD\n");

	if (pic_params->CurrPic.flags & VA_PICTURE_H264_SHORT_TERM_REFERENCE)
		LOG_V( "Flags show VA_PICTURE_H264_SHORT_TERM_REFERENCE\n");

	if (pic_params->CurrPic.flags & VA_PICTURE_H264_LONG_TERM_REFERENCE)
		LOG_V( "Flags show VA_PICTURE_H264_LONG_TERM_REFERENCE\n");
#endif

        MixVideoFormat_H264 *self = MIX_VIDEOFORMAT_H264(mix);


	//First we need to check the parser DBP against our DPB table
	//So for each item in our DBP table, we look to see if it is in the parser DPB
	//If it is not, it gets unrefed and removed
#ifdef MIX_LOG_ENABLE
	guint num_removed =
#endif
	g_hash_table_foreach_remove(self->dpb_surface_table, mix_videofmt_h264_check_in_DPB, pic_params);

		LOG_V( "%d entries removed from DPB surface table at this frame\n", num_removed);


	MixVideoFrame *mvf = NULL;
	gboolean found = FALSE;
	//Set the surface ID for everything in the parser DPB
	int i = 0;
	for (; i < 16; i++)
	{
		if (!(pic_params->ReferenceFrames[i].flags & VA_PICTURE_H264_INVALID))
		{

			poc = mix_videofmt_h264_get_poc(&(pic_params->ReferenceFrames[i]));
		LOG_V( "Looking up poc %d in dpb table\n", poc);
			found = g_hash_table_lookup_extended(self->dpb_surface_table, (gpointer)poc, NULL, (gpointer)&mvf);

			if (found)
			{
				pic_params->ReferenceFrames[i].picture_id = mvf->frame_id;
		LOG_V( "Looked up poc %d in dpb table found frame ID %d\n", poc, (gint)mvf->frame_id);
			} else {
		LOG_V( "Looking up poc %d in dpb table did not find value\n", poc);
			}
		LOG_V( "For poc %d, set surface id for DPB index %d to %d\n", poc, i, (gint)pic_params->ReferenceFrames[i].picture_id);
		}

	}


	//Set picture_id for current picture
	pic_params->CurrPic.picture_id = current_frame->frame_id;

	//Check to see if current frame is a reference frame
	if ((pic_params->CurrPic.flags & VA_PICTURE_H264_SHORT_TERM_REFERENCE) || (pic_params->CurrPic.flags & VA_PICTURE_H264_LONG_TERM_REFERENCE))
	{
		//Get current frame's POC
		poc = mix_videofmt_h264_get_poc(&(pic_params->CurrPic));	

		//Increment the reference count for this frame
		mix_videoframe_ref(current_frame);

		LOG_V( "Inserting poc %d, surfaceID %d\n", poc, (gint)current_frame->frame_id);
		//Add this frame to the DPB surface table
		g_hash_table_insert(self->dpb_surface_table, (gpointer)poc, current_frame);
	}



	LOG_V( "End\n");

	return MIX_RESULT_SUCCESS;
}

guint mix_videofmt_h264_get_poc(VAPictureH264 *pic)
{

        if (pic == NULL)
                return 0;

	if (pic->flags & VA_PICTURE_H264_BOTTOM_FIELD)
		return pic->BottomFieldOrderCnt;

	
	if (pic->flags & VA_PICTURE_H264_TOP_FIELD)
		return pic->TopFieldOrderCnt;

	return pic->TopFieldOrderCnt;

}


gboolean mix_videofmt_h264_check_in_DPB(gpointer key, gpointer value, gpointer user_data)
{
	gboolean ret = TRUE;

        if ((value == NULL) || (user_data == NULL))  //Note that 0 is valid value for key
                return FALSE;

	VAPictureH264* vaPic = NULL;
	int i = 0;
	for (; i < 16; i++)
	{
		vaPic = &(((VAPictureParameterBufferH264*)user_data)->ReferenceFrames[i]);
		if (vaPic->flags & VA_PICTURE_H264_INVALID)
			continue;
			
		if ((guint)key == vaPic->TopFieldOrderCnt ||
			(guint)key == vaPic->BottomFieldOrderCnt)
		{
			ret = FALSE;
			break;
		}
	}

	return ret;
}

void mix_videofmt_h264_destroy_DPB_key(gpointer data)
{
//TODO remove this method and don't register it with the hash table foreach call; it is no longer needed
	LOG_V( "Begin, poc of %d\n", (guint)data);
	LOG_V( "End\n");

	return;
}

void mix_videofmt_h264_destroy_DPB_value(gpointer data)
{
	LOG_V( "Begin\n");
        if (data == NULL)
        	return ;
	mix_videoframe_unref((MixVideoFrame *)data);

	return;
}


MIX_RESULT mix_videofmt_h264_release_input_buffers(MixVideoFormat *mix, 
					guint64 timestamp
					) {

	MixInputBufferEntry *bufentry = NULL;
	gboolean done = FALSE;

	LOG_V( "Begin\n");

        if (mix == NULL)
                return MIX_RESULT_NULL_PTR;

	//Dequeue and release all input buffers for this frame
		
	LOG_V( "Releasing all the MixBuffers for this frame\n");

	//While the head of the queue has timestamp == current ts
	//dequeue the entry, unref the MixBuffer, and free the struct
	done = FALSE;
	while (!done)
	{
		bufentry = (MixInputBufferEntry *) g_queue_peek_head(
				mix->inputbufqueue);
		if (bufentry == NULL) break;
	LOG_V( "head of queue buf %x, timestamp %"G_GINT64_FORMAT", buffer timestamp %"G_GINT64_FORMAT"\n", (guint)bufentry->buf, timestamp, bufentry->timestamp);

		if (bufentry->timestamp != timestamp)
		{
	LOG_V( "buf %x, timestamp %"G_GINT64_FORMAT", buffer timestamp %"G_GINT64_FORMAT"\n", (guint)bufentry->buf, timestamp, bufentry->timestamp);
			done = TRUE;
			break;
		}

		bufentry = (MixInputBufferEntry *) g_queue_pop_head(
				mix->inputbufqueue);
		LOG_V( "Unref this MixBuffers %x\n", (guint)bufentry->buf);
		mix_buffer_unref(bufentry->buf);
		g_free(bufentry);
	}
	

	LOG_V( "End\n");

	return MIX_RESULT_SUCCESS;
}



