/*
 INTEL CONFIDENTIAL
 Copyright 2009 Intel Corporation All Rights Reserved.
 The source code contained or described herein and all documents related to the source code ("Material") are owned by Intel Corporation or its suppliers or licensors. Title to the Material remains with Intel Corporation or its suppliers and licensors. The Material contains trade secrets and proprietary and confidential information of Intel or its suppliers and licensors. The Material is protected by worldwide copyright and trade secret laws and treaty provisions. No part of the Material may be used, copied, reproduced, modified, published, uploaded, posted, transmitted, distributed, or disclosed in any way without Intel’s prior express written permission.

 No license under any patent, copyright, trade secret or other intellectual property right is granted to or conferred upon you by disclosure or delivery of the Materials, either expressly, by implication, inducement, estoppel or otherwise. Any license under such intellectual property rights must be express and approved by Intel in writing.
 */
#include <glib.h>
#include "mixvideolog.h"

#include "mixvideoformat_vc1.h"
#include <va/va_x11.h>

#ifdef YUVDUMP
//TODO Complete YUVDUMP code and move into base class
#include <stdio.h>
#endif /* YUVDUMP */

#include <string.h>


#ifdef MIX_LOG_ENABLE
static int mix_video_vc1_counter = 0;
#endif

/* The parent class. The pointer will be saved
 * in this class's initialization. The pointer
 * can be used for chaining method call if needed.
 */
static MixVideoFormatClass *parent_class = NULL;

static void mix_videoformat_vc1_finalize(GObject * obj);

/*
 * Please note that the type we pass to G_DEFINE_TYPE is MIX_TYPE_VIDEOFORMAT
 */
G_DEFINE_TYPE (MixVideoFormat_VC1, mix_videoformat_vc1, MIX_TYPE_VIDEOFORMAT);

static void mix_videoformat_vc1_init(MixVideoFormat_VC1 * self) {
	MixVideoFormat *parent = MIX_VIDEOFORMAT(self);

	/* public member initialization */
	/* These are all public because MixVideoFormat objects are completely internal to MixVideo,
		no need for private members  */
	self->reference_frames[0] = NULL;
	self->reference_frames[1] = NULL;

	/* NOTE: we don't need to do this here.
	 * This just demostrates how to access
	 * member varibles beloned to parent
	 */
	parent->initialized = FALSE;
}

static void mix_videoformat_vc1_class_init(
		MixVideoFormat_VC1Class * klass) {

	/* root class */
	GObjectClass *gobject_class = (GObjectClass *) klass;

	/* direct parent class */
	MixVideoFormatClass *video_format_class =
			MIX_VIDEOFORMAT_CLASS(klass);

	/* parent class for later use */
	parent_class = g_type_class_peek_parent(klass);

	/* setup finializer */
	gobject_class->finalize = mix_videoformat_vc1_finalize;

	/* setup vmethods with base implementation */
	/* This is where we can override base class methods if needed */
	video_format_class->getcaps = mix_videofmt_vc1_getcaps;
	video_format_class->initialize = mix_videofmt_vc1_initialize;
	video_format_class->decode = mix_videofmt_vc1_decode;
	video_format_class->flush = mix_videofmt_vc1_flush;
	video_format_class->eos = mix_videofmt_vc1_eos;
	video_format_class->deinitialize = mix_videofmt_vc1_deinitialize;
}

MixVideoFormat_VC1 *
mix_videoformat_vc1_new(void) {
	MixVideoFormat_VC1 *ret =
			g_object_new(MIX_TYPE_VIDEOFORMAT_VC1, NULL);

	return ret;
}

void mix_videoformat_vc1_finalize(GObject * obj) {
	gint32 pret = VBP_OK;

	/* clean up here. */

        MixVideoFormat *parent = NULL;
	MixVideoFormat_VC1 *self = MIX_VIDEOFORMAT_VC1(obj);
	GObjectClass *root_class = (GObjectClass *) parent_class;

        parent = MIX_VIDEOFORMAT(self);

        g_mutex_lock(parent->objectlock);

	//surfacepool is deallocated by parent
	//inputbufqueue is deallocated by parent
	//parent calls vaDestroyConfig, vaDestroyContext and vaDestroySurfaces

	//Unref our reference frames
	int i = 0;
	for (; i < 2; i++)
	{
		if (self->reference_frames[i] != NULL)
		{
			mix_videoframe_unref(self->reference_frames[i]);
			self->reference_frames[i] = NULL;
		}
	}

	//Reset state
        parent->initialized = TRUE;
        parent->parse_in_progress = FALSE;
	parent->discontinuity_frame_in_progress = FALSE;
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

MixVideoFormat_VC1 *
mix_videoformat_vc1_ref(MixVideoFormat_VC1 * mix) {
	return (MixVideoFormat_VC1 *) g_object_ref(G_OBJECT(mix));
}

/*  VC1 vmethods implementation */
MIX_RESULT mix_videofmt_vc1_getcaps(MixVideoFormat *mix, GString *msg) {

	MIX_RESULT ret = MIX_RESULT_NOTIMPL;

//This method is reserved for future use

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

MIX_RESULT mix_videofmt_vc1_update_seq_header(
	MixVideoConfigParamsDec* config_params,
	MixIOVec *header)
{
	guint width = 0;
	guint height = 0;

	guint i = 0;
	guchar* p = header->data;
	MIX_RESULT res = MIX_RESULT_SUCCESS;

	if (!config_params || !header)
	{
		LOG_E( "NUll pointer passed in\n");
		return (MIX_RESULT_NULL_PTR);
	}

	res = mix_videoconfigparamsdec_get_picture_res(
		config_params,
		&width,
	 	&height);
	
	if (MIX_RESULT_SUCCESS != res)
	{
		return res;
	}

	/* Check for start codes.  If one exist, then this is VC-1 and not WMV. */
  	while (i < header->data_size - 2)
  	{
    		if ((p[i] == 0) && 
		    (p[i + 1] == 0) && 
                    (p[i + 2] == 1))
    		{
      			return MIX_RESULT_SUCCESS;
    		}
    		i++;
  	}

	p = g_malloc0(header->data_size + 9);

	if (!p)
	{
		LOG_E( "Cannot allocate memory\n");
                return MIX_RESULT_NO_MEMORY;
	}

	/* If we get here we have 4+ bytes of codec data that must be formatted */
  	/* to pass through as an RCV sequence header. */
	p[0] = 0; 
	p[1] = 0; 
	p[2] = 1; 
	p[3] = 0x0f;  /* Start code. */
  
 	p[4] = (width >> 8) & 0x0ff;
  	p[5] = width & 0x0ff;
  	p[6] = (height >> 8) & 0x0ff;
  	p[7] = height & 0x0ff;

	memcpy(p + 8, header->data, header->data_size);
	*(p + header->data_size + 8) = 0x80;

  	g_free(header->data);
	header->data = p;
	header->data_size = header->data_size + 9;

	return MIX_RESULT_SUCCESS;
}



MIX_RESULT mix_videofmt_vc1_initialize(MixVideoFormat *mix, 
                MixVideoConfigParamsDec * config_params,
                MixFrameManager * frame_mgr,
		MixBufferPool * input_buf_pool,
		MixSurfacePool ** surface_pool,
		VADisplay va_display) {

        uint32 pret = 0;
        MIX_RESULT ret = MIX_RESULT_SUCCESS;
        enum _vbp_parser_type ptype = VBP_VC1;
				vbp_data_vc1 *data = NULL;
        MixVideoFormat *parent = NULL;
        MixVideoFormat_VC1 *self = NULL;
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

        if (mix == NULL || config_params == NULL || frame_mgr == NULL || !input_buf_pool || !surface_pool || !va_display)
	{
		LOG_E( "NUll pointer passed in\n");
                return MIX_RESULT_NULL_PTR;
	}

        LOG_V( "Begin\n");

	/* Chainup parent method.
	 */

	if (parent_class->initialize) {
		ret = parent_class->initialize(mix, config_params,
				frame_mgr, input_buf_pool, surface_pool, 
				va_display);
	}

	if (ret != MIX_RESULT_SUCCESS)
	{
		return ret;
	}

        if (!MIX_IS_VIDEOFORMAT_VC1(mix))
                return MIX_RESULT_INVALID_PARAM;

        parent = MIX_VIDEOFORMAT(mix);
	self = MIX_VIDEOFORMAT_VC1(mix);

	LOG_V( "Locking\n");
	//From now on, we exit this function through cleanup:
        g_mutex_lock(parent->objectlock);

        //Load the bitstream parser
        pret = vbp_open(ptype, &(parent->parser_handle));

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

	ret = mix_videofmt_vc1_update_seq_header(
		config_params,
		 header);
        if (ret != MIX_RESULT_SUCCESS)
        {
		ret = MIX_RESULT_FAIL;
		LOG_E( "Error updating sequence header\n");
		goto cleanup;
        }
   
        pret = vbp_parse(parent->parser_handle, header->data,
                        header->data_size, TRUE);

        if (!((pret == VBP_OK) || (pret == VBP_DONE)))
        {
		ret = MIX_RESULT_FAIL;
		LOG_E( "Error parsing header data, size %d\n", header->data_size);
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

	VAProfile profile;
	switch (data->se_data->PROFILE)
	{
		case 0:
		profile = VAProfileVC1Simple;
		break;

		case 1:
		profile = VAProfileVC1Main;
		break;

		default:
		profile = VAProfileVC1Advanced;
		break;
	}			

	for (; vaprof < numactualprofs; vaprof++)
	{
		if (profiles[vaprof] == profile)
			break;
	}
	if (vaprof >= numprofs || profiles[vaprof] != profile)  
	//Did not get the profile we wanted
	{
		ret = MIX_RESULT_FAIL;
		LOG_E( "Profile not supported by driver\n");
		goto cleanup;
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

	//Check for loop filtering
	if (data->se_data->LOOPFILTER == 1)  
		self->loopFilter = TRUE;
	else
		self->loopFilter = FALSE;

	LOG_V( "loop filter is %d, TFCNTRFLAG is %d\n", data->se_data->LOOPFILTER, data->se_data->TFCNTRFLAG);

       //Initialize the surface pool


	if ((data->se_data->MAXBFRAMES > 0) || (data->se_data->PROFILE == 3) || (data->se_data->PROFILE == 1))  
	//If Advanced profile, have to assume B frames may be present, since MAXBFRAMES is not valid for this prof
		self->haveBframes = TRUE;
	else
		self->haveBframes = FALSE;

	//Calculate VC1 numSurfaces based on max number of B frames or
	// MIX_VIDEO_VC1_SURFACE_NUM, whichever is less

	//Adding 1 to work around VBLANK issue
	parent->va_num_surfaces = 1 + extra_surfaces + ((3 + (self->haveBframes ? 1 : 0) <
		MIX_VIDEO_VC1_SURFACE_NUM) ? 
		(3 + (self->haveBframes ? 1 : 0))
		: MIX_VIDEO_VC1_SURFACE_NUM);

	numSurfaces = parent->va_num_surfaces;

	parent->va_surfaces = g_malloc(sizeof(VASurfaceID)*numSurfaces);

	surfaces = parent->va_surfaces;

	if (surfaces == NULL)
	{
		ret = MIX_RESULT_FAIL;
		LOG_E( "Cannot allocate temporary data\n");
		goto cleanup;
	}

        vret = vaCreateSurfaces(vadisplay, parent->picture_width,
                parent->picture_height, entrypts[vaentrypt],
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
                case MIX_RESULT_ALREADY_INIT:
                default:
			ret = MIX_RESULT_ALREADY_INIT;
			LOG_E( "Error init failure\n");
			goto cleanup;
                        break;
        }

        LOG_V( "Created %d libva surfaces, MAXBFRAMES is %d\n", numSurfaces, data->se_data->MAXBFRAMES);

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

	LOG_V( "mix_video vinfo:  Content type %s, %s\n", (header->data_size > 8) ? "VC-1" : "WMV", (data->se_data->INTERLACE) ? "interlaced" : "progressive");
	LOG_V( "mix_video vinfo:  Content width %d, height %d\n", parent->picture_width, parent->picture_height);
	LOG_V( "mix_video vinfo:  MAXBFRAMES %d (note that for Advanced profile, MAXBFRAMES can be zero and there still can be B frames in the content)\n", data->se_data->MAXBFRAMES);
	LOG_V( "mix_video vinfo:  PROFILE %d, LEVEL %d\n", data->se_data->PROFILE, data->se_data->LEVEL);


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

	self->lastFrame = NULL;


	LOG_V( "Unlocking\n");
        g_mutex_unlock(parent->objectlock);

        LOG_V( "End\n");

	return ret;
}

MIX_RESULT mix_videofmt_vc1_decode(MixVideoFormat *mix, 
		MixBuffer * bufin[], gint bufincnt, 
                MixVideoDecodeParams * decode_params) {

        uint32 pret = 0;
	int i = 0;
        MixVideoFormat *parent = NULL;
	MIX_RESULT ret = MIX_RESULT_SUCCESS;
	guint64 ts = 0;
	vbp_data_vc1 *data = NULL;
	gboolean discontinuity = FALSE;
	MixInputBufferEntry *bufentry = NULL;

        if (mix == NULL || bufin == NULL || decode_params == NULL )
	{
		LOG_E( "NUll pointer passed in\n");
                return MIX_RESULT_NULL_PTR;
	}

        //TODO remove iovout and iovoutcnt; they are not used (need to remove from MixVideo/MI-X API too)

	LOG_V( "Begin\n");

	/* Chainup parent method.
		We are not chaining up to parent method for now.
	 */

#if 0
        if (parent_class->decode) {
                return parent_class->decode(mix, bufin, bufincnt, 
                                        decode_params);
	}
#endif

	if (!MIX_IS_VIDEOFORMAT_VC1(mix))
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
		ret = mix_videofmt_vc1_process_decode(mix,
			data, parent->current_timestamp, 
			parent->discontinuity_frame_in_progress);

		if (ret != MIX_RESULT_SUCCESS)
        	{
			//We log this but need to process the new frame data, so do not return
			LOG_E( "process_decode failed.\n");
        	}

		LOG_V( "Called process and decode for last frame\n");

		parent->parse_in_progress = FALSE;

	}

	parent->current_timestamp = ts;
	parent->discontinuity_frame_in_progress = discontinuity;

	LOG_V( "Starting current frame %d, timestamp %"G_GINT64_FORMAT"\n", mix_video_vc1_counter++, ts);

	for (i = 0; i < bufincnt; i++)
	{

		LOG_V( "Calling parse for current frame, parse handle %d, buf %x, size %d\n", (int)parent->parser_handle, (guint)bufin[i]->data, bufin[i]->size);

		pret = vbp_parse(parent->parser_handle, 
			bufin[i]->data, 
			bufin[i]->size,
			FALSE);

		LOG_V( "Called parse for current frame\n");

		if (pret == VBP_DONE) 
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
			ret = mix_videofmt_vc1_process_decode(mix,
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
			bufentry->timestamp = ts;

			//Enqueue this input buffer
			g_queue_push_tail(parent->inputbufqueue, 
				(gpointer)bufentry);
			parent->parse_in_progress = TRUE;
		}

	}


	cleanup:

	LOG_V( "Unlocking\n");
 	g_mutex_unlock(parent->objectlock);


	LOG_V( "End\n");

	return ret;
}

#ifdef YUVDUMP
//TODO Complete this YUVDUMP code and move into base class

MIX_RESULT GetImageFromSurface (MixVideoFormat *mix, MixVideoFrame * frame)

{

       VAStatus vaStatus = VA_STATUS_SUCCESS;
       VAImageFormat va_image_format;
	VAImage va_image;

       unsigned char*      pBuffer;
       unsigned int   ui32SrcWidth = mix->picture_width;
       unsigned int   ui32SrcHeight = mix->picture_height;
       unsigned int   ui32Stride;
       unsigned int   ui32ChromaOffset;
	FILE *fp = NULL;
	int r = 0;
 
       int i;

       g_print ("GetImageFromSurface   \n");

	if ((mix == NULL) || (frame == NULL))
	{
		LOG_E( "Null pointer passed in\n");
		return MIX_RESULT_NULL_PTR;
	}

	fp = fopen("yuvdump.yuv", "a+");

    static int have_va_image = 0;

       if (!have_va_image)
       {
              va_image_format.fourcc = VA_FOURCC_NV12;
//              va_image_format.fourcc = VA_FOURCC_YV12;

              vaStatus = vaCreateImage(mix->va_display, &va_image_format, ui32SrcWidth, ui32SrcHeight, &va_image);
              have_va_image = 1;
       }

       vaStatus = vaGetImage( mix->va_display, frame->frame_id, 0, 0, ui32SrcWidth, ui32SrcHeight, va_image.image_id );
       vaStatus = vaMapBuffer( mix->va_display, va_image.buf, (void **) &pBuffer);
       ui32ChromaOffset = va_image.offsets[1];
       ui32Stride = va_image.pitches[0];

       if (VA_STATUS_SUCCESS != vaStatus)
       {
              g_print ("VideoProcessBlt: Unable to copy surface\n\r");
              return vaStatus;
       }

       {
              g_print ("before copy memory....\n");
              g_print ("width = %d, height = %d\n", ui32SrcWidth, ui32SrcHeight);
              g_print ("data_size = %d\n", va_image.data_size);
              g_print ("num_planes = %d\n", va_image.num_planes);    
              g_print ("va_image.pitches[0] = %d\n", va_image.pitches[0]);   
              g_print ("va_image.pitches[1] = %d\n", va_image.pitches[1]);   
              g_print ("va_image.pitches[2] = %d\n", va_image.pitches[2]);   
              g_print ("va_image.offsets[0] = %d\n", va_image.offsets[0]);   
              g_print ("va_image.offsets[1] = %d\n", va_image.offsets[1]);   
              g_print ("va_image.offsets[2] = %d\n", va_image.offsets[2]);                 
//      r = fwrite (pBuffer, 1, va_image.offsets[1], fp);

      r = fwrite (pBuffer, va_image.offsets[1], 1, fp);

         for (i = 0; i < ui32SrcWidth * ui32SrcHeight / 2; i +=2)
              r = fwrite (pBuffer + va_image.offsets[1] + i / 2, 1, 1, fp);

        for (i = 0; i < ui32SrcWidth * ui32SrcHeight / 2; i +=2)
              r = fwrite (pBuffer + va_image.offsets[1] +  i / 2 + 1, 1, 1, fp);   

        g_print ("ui32ChromaOffset = %d, ui32Stride = %d\n", ui32ChromaOffset, ui32Stride);       

       }

       vaStatus = vaUnmapBuffer( mix->va_display, va_image.buf);

       return vaStatus;

}
#endif /* YUVDUMP */


MIX_RESULT mix_videofmt_vc1_decode_a_picture(
	MixVideoFormat* mix,
	vbp_data_vc1 *data,
	int pic_index,
	MixVideoFrame *frame)
{
	MIX_RESULT ret = MIX_RESULT_SUCCESS;
	VAStatus vret = VA_STATUS_SUCCESS;
	VADisplay vadisplay = NULL;
	VAContextID vacontext;
	guint buffer_id_cnt = 0;
	VABufferID *buffer_ids = NULL;
	MixVideoFormat_VC1 *self = MIX_VIDEOFORMAT_VC1(mix);
	
	vbp_picture_data_vc1* pic_data = &(data->pic_data[pic_index]);
	VAPictureParameterBufferVC1 *pic_params = pic_data->pic_parms;

	if (pic_params == NULL) 
	{
		ret = MIX_RESULT_NULL_PTR;
		LOG_E( "Error reading parser data\n");
		goto cleanup;
	}

	LOG_V( "num_slices is %d, allocating %d buffer_ids\n", pic_data->num_slices, (pic_data->num_slices * 2) + 2);

	//Set up reference frames for the picture parameter buffer

	//Set the picture type (I, B or P frame)
	enum _picture_type frame_type = pic_params->picture_fields.bits.picture_type;


	//Check for B frames after a seek
	//We need to have both reference frames in hand before we can decode a B frame
	//If we don't have both reference frames, we must return MIX_RESULT_DROPFRAME
	//Note:  demuxer should do the right thing and only seek to I frame, so we should
	//  not get P frame first, but may get B frames after the first I frame
	if (frame_type == VC1_PTYPE_B)
	{
		if (self->reference_frames[1] == NULL)
		{
			LOG_E( "Insufficient reference frames for B frame\n");
			ret = MIX_RESULT_DROPFRAME;
			goto cleanup;
		}
	}

	buffer_ids = g_malloc(sizeof(VABufferID) * ((pic_data->num_slices * 2) + 2));
	if (buffer_ids == NULL) 
	{
		LOG_E( "Cannot allocate buffer IDs\n");
		ret = MIX_RESULT_NO_MEMORY;
		goto cleanup;
	}

	LOG_V( "Getting a new surface\n");
	LOG_V( "frame type is %d\n", frame_type);

	gulong surface = 0;

	//Get our surface ID from the frame object
	ret = mix_videoframe_get_frame_id(frame, &surface);
	if (ret != MIX_RESULT_SUCCESS)
	{
		LOG_E( "Error getting surface ID from frame object\n");
		goto cleanup;
	}

	//Get a frame from the surface pool

	if (0 == pic_index)
	{
		//Set the frame type for the frame object (used in reordering by frame manager)
		switch (frame_type)
		{
			case VC1_PTYPE_I:  // I frame type
			case VC1_PTYPE_P:  // P frame type
			case VC1_PTYPE_B:  // B frame type
				ret = mix_videoframe_set_frame_type(frame, frame_type);
				break;
			case VC1_PTYPE_BI: // BI frame type
				ret = mix_videoframe_set_frame_type(frame, TYPE_I);
				break;
	//Not indicated here	case VC1_PTYPE_SKIPPED:  
			default:
				break;
		}
	}

	if (ret != MIX_RESULT_SUCCESS)
	{
		LOG_E( "Error setting frame type on frame\n");
		goto cleanup;
	}

	LOG_V( "Setting reference frames in picparams, frame_type = %d\n", frame_type);

	//TODO Check if we need to add more handling of B or P frames when reference frames are not set up (such as after flush/seek)

	switch (frame_type)
	{
		case VC1_PTYPE_I:  // I frame type
			/* forward and backward reference pictures are not used but just set to current
			surface to be in consistence with test suite	
			*/
			pic_params->forward_reference_picture = surface;
			pic_params->backward_reference_picture = surface;
			LOG_V( "I frame, surface ID %u\n", (guint)frame->frame_id);
			LOG_V( "mix_video vinfo:  Frame type is I\n");
			break;
		case VC1_PTYPE_P:  // P frame type

			// check REFDIST in the picture parameter buffer
			if (0 != pic_params->reference_fields.bits.reference_distance_flag &&
			    0 != pic_params->reference_fields.bits.reference_distance)
			{
				/* The previous decoded frame (distance is up to 16 but not 0) is used 
				for reference, as we don't allocate that many surfaces so the reference picture
				could have been overwritten and hence not avaiable for reference.
				*/
				LOG_E( "reference distance is not 0!");
				ret = MIX_RESULT_FAIL;
				goto cleanup;				
			}
			if (1 == pic_index)
			{	
				// handle interlace field coding case
				if (1 == pic_params->reference_fields.bits.num_reference_pictures ||
				1 == pic_params->reference_fields.bits.reference_field_pic_indicator)
				{
					/* two reference fields or the second closest I/P field is used for
					 prediction. Set forward reference picture to INVALID so it will be 
					updated to a valid previous reconstructed reference frame later.
					*/
					pic_params->forward_reference_picture  = VA_INVALID_SURFACE;
				}		
				else
				{
					/* the closest I/P is used for reference so it must be the 
					 complementary field in the same surface.
					*/
					pic_params->forward_reference_picture  = surface;
				}
			}
			if (VA_INVALID_SURFACE == pic_params->forward_reference_picture)
			{
				if (self->reference_frames[1])
				{
					pic_params->forward_reference_picture = self->reference_frames[1]->frame_id;
				}
				else if (self->reference_frames[0])
				{
					pic_params->forward_reference_picture = self->reference_frames[0]->frame_id;
				}
				else
				{
					ret = MIX_RESULT_FAIL;
					LOG_E( "Error could not find reference frames for P frame\n");
					goto cleanup;
				}
			}
			pic_params->backward_reference_picture = VA_INVALID_SURFACE;

			LOG_V( "P frame, surface ID %u, forw ref frame is %u\n", (guint)frame->frame_id, (guint)self->reference_frames[0]->frame_id);
			LOG_V( "mix_video vinfo:  Frame type is P\n");
			break;

		case VC1_PTYPE_B:  // B frame type
			LOG_V( "B frame, forw ref %d, back ref %d\n", (guint)self->reference_frames[0]->frame_id, (guint)self->reference_frames[1]->frame_id);

			if (!self->haveBframes)	//We don't expect B frames and have not allocated a surface 
						// for the extra ref frame so this is an error
			{
				ret = MIX_RESULT_FAIL;
				LOG_E( "Unexpected B frame, cannot process\n");
				goto cleanup;
			}

			pic_params->forward_reference_picture = self->reference_frames[0]->frame_id;
			pic_params->backward_reference_picture = self->reference_frames[1]->frame_id;

			LOG_V( "B frame, surface ID %u, forw ref %d, back ref %d\n", (guint)frame->frame_id, (guint)self->reference_frames[0]->frame_id, (guint)self->reference_frames[1]->frame_id);
			LOG_V( "mix_video vinfo:  Frame type is B\n");
			break;

		case VC1_PTYPE_BI: 
			pic_params->forward_reference_picture = VA_INVALID_SURFACE;
			pic_params->backward_reference_picture = VA_INVALID_SURFACE;
			LOG_V( "BI frame\n");
			LOG_V( "mix_video vinfo:  Frame type is BI\n");
			break;

		case VC1_PTYPE_SKIPPED: 
			//Will never happen here
			break;

		default:
			LOG_V( "Hit default\n");
			break;
			
	}

	//Loop filter handling
	if (self->loopFilter)
	{
		LOG_V( "Setting in loop decoded picture to current frame\n");
		LOG_V( "Double checking picparams inloop filter is %d\n", pic_params->entrypoint_fields.bits.loopfilter);
		pic_params->inloop_decoded_picture = frame->frame_id;
	}
	else
	{
		LOG_V( "Setting in loop decoded picture to invalid\n");
		pic_params->inloop_decoded_picture = VA_INVALID_SURFACE;
	}
		
	//Libva buffer set up

	vadisplay = mix->va_display;
	vacontext = mix->va_context;

	LOG_V( "Creating libva picture parameter buffer\n");

	//First the picture parameter buffer
	vret = vaCreateBuffer(
		vadisplay, 
		vacontext,
		VAPictureParameterBufferType,
		sizeof(VAPictureParameterBufferVC1),
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
			
	LOG_V( "Creating libva bitplane buffer\n");

	if (pic_params->bitplane_present.value)
	{
		//Then the bitplane buffer
		vret = vaCreateBuffer(
			vadisplay,
			vacontext,
			VABitPlaneBufferType,
			pic_data->size_bitplanes,
			1,
			pic_data->packed_bitplanes,
			&buffer_ids[buffer_id_cnt]);

		buffer_id_cnt++;

		if (vret != VA_STATUS_SUCCESS)
		{
			ret = MIX_RESULT_FAIL;
			LOG_E( "Video driver returned error from vaCreateBuffer\n");
			goto cleanup;
		}
	}

	//Now for slices
	int i = 0;
	for (; i < pic_data->num_slices; i++)
	{
		LOG_V( "Creating libva slice parameter buffer, for slice %d\n", i);

		//Do slice parameters
		vret = vaCreateBuffer(
			vadisplay, 
			vacontext,
			VASliceParameterBufferType,
			sizeof(VASliceParameterBufferVC1),
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
		vret = vaCreateBuffer(
			vadisplay, 
			vacontext,
			VASliceDataBufferType,
			//size
			pic_data->slc_data[i].slice_size,
			//num_elements
			1,
			//slice data buffer pointer
			//Note that this is the original data buffer ptr;
			// offset to the actual slice data is provided in
			// slice_data_offset in VASliceParameterBufferVC1
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
	vret = vaRenderPicture(
		vadisplay, 
		vacontext,
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

cleanup:
	if (NULL != buffer_ids)
		g_free(buffer_ids);

	return ret;
}


MIX_RESULT mix_videofmt_vc1_process_decode(
	MixVideoFormat *mix,
	vbp_data_vc1 *data, 
	guint64 timestamp,
	gboolean discontinuity)
{
	MIX_RESULT ret = MIX_RESULT_SUCCESS;
	gboolean unrefVideoFrame = FALSE;
	MixVideoFrame *frame = NULL;

	//TODO Partition this method into smaller methods

	LOG_V( "Begin\n");

	if ((mix == NULL) || (data == NULL))
	{
		LOG_E( "Null pointer passed in\n");
		return MIX_RESULT_NULL_PTR;
	}
	
	if (0 == data->num_pictures || NULL == data->pic_data)
	{
		return MIX_RESULT_INVALID_PARAM;
	}

	if (!MIX_IS_VIDEOFORMAT_VC1(mix))
	{
		return MIX_RESULT_INVALID_PARAM;
	}

	//After this point, all exits from this function are through cleanup:
	MixVideoFormat_VC1 *self = MIX_VIDEOFORMAT_VC1(mix);

	//Check for skipped frame
	//For skipped frames, we will reuse the last P or I frame surface and treat as P frame
	if (data->pic_data[0].picture_is_skipped == VC1_PTYPE_SKIPPED)
	{

		LOG_V( "mix_video vinfo:  Frame type is SKIPPED\n");
		if (self->lastFrame == NULL)
		{
			//we shouldn't get a skipped frame before we are able to get a real frame
			LOG_E( "Error for skipped frame, prev frame is NULL\n");
			ret = MIX_RESULT_DROPFRAME;
			goto cleanup;
		}

		//We don't worry about this memory allocation because SKIPPED is not a common case
		//Doing the allocation on the fly is a more efficient choice than trying to manage yet another pool
		MixVideoFrame *skip_frame = mix_videoframe_new();
		if (skip_frame == NULL) 
		{
			ret = MIX_RESULT_NO_MEMORY;
			LOG_E( "Error allocating new video frame object for skipped frame\n");
			goto cleanup;
		}

		mix_videoframe_set_is_skipped(skip_frame, TRUE);
//			mix_videoframe_ref(skip_frame);
		mix_videoframe_ref(self->lastFrame);
		gulong frameid = VA_INVALID_SURFACE;
		mix_videoframe_get_frame_id(self->lastFrame, &frameid);
		mix_videoframe_set_frame_id(skip_frame, frameid);
		mix_videoframe_set_frame_type(skip_frame, VC1_PTYPE_P);
		mix_videoframe_set_real_frame(skip_frame, self->lastFrame);
		mix_videoframe_set_timestamp(skip_frame, timestamp);
		mix_videoframe_set_discontinuity(skip_frame, FALSE);
		LOG_V( "Processing skipped frame %x, frame_id set to %d, ts %"G_GINT64_FORMAT"\n", (guint)skip_frame, (guint)frameid, timestamp);

		//Process reference frames
		LOG_V( "Updating skipped frame forward/backward references for libva\n");
		mix_videofmt_vc1_handle_ref_frames(mix,
				VC1_PTYPE_P,
				skip_frame);

		//Enqueue the skipped frame using frame manager
		ret = mix_framemanager_enqueue(mix->framemgr, skip_frame);

		goto cleanup;
		
	}

	ret = mix_surfacepool_get(mix->surfacepool, &frame);
	if (ret != MIX_RESULT_SUCCESS)
	{
		LOG_E( "Error getting frame from surfacepool\n");
		goto cleanup;

	}
	unrefVideoFrame = TRUE;

	// TO DO: handle multiple frames parsed from a sample buffer
	int index;
	int num_pictures = (data->num_pictures > 1) ? 2 : 1;

	for (index = 0; index < num_pictures; index++)
	{
		ret = mix_videofmt_vc1_decode_a_picture(mix, data, index, frame);
		if (ret != MIX_RESULT_SUCCESS)
		{
			LOG_E( "Failed to decode a picture.\n");
			goto cleanup;
		}
	}

	//Set the discontinuity flag
	mix_videoframe_set_discontinuity(frame, discontinuity);

	//Set the timestamp
	mix_videoframe_set_timestamp(frame, timestamp);

	// setup frame structure
	if (data->num_pictures > 1)
	{
		if (data->pic_data[0].pic_parms->picture_fields.bits.is_first_field)
			mix_videoframe_set_frame_structure(frame, VA_TOP_FIELD);
		else
			mix_videoframe_set_frame_structure(frame, VA_BOTTOM_FIELD);
	}
	else
	{
		mix_videoframe_set_frame_structure(frame, VA_FRAME_PICTURE);
	}

	enum _picture_type frame_type = data->pic_data[0].pic_parms->picture_fields.bits.picture_type;

	//For I or P frames
	//Save this frame off for skipped frame handling
	if ((frame_type == VC1_PTYPE_I) || (frame_type == VC1_PTYPE_P))
	{
		if (self->lastFrame != NULL)
		{
			mix_videoframe_unref(self->lastFrame);
		}
		self->lastFrame = frame;
		mix_videoframe_ref(frame);
	}

	//Update the references frames for the current frame
	if ((frame_type == VC1_PTYPE_I) || (frame_type == VC1_PTYPE_P))  //If I or P frame, update the reference array
	{
		LOG_V( "Updating forward/backward references for libva\n");
		mix_videofmt_vc1_handle_ref_frames(mix,
				frame_type,
				frame);
	}

//TODO Complete YUVDUMP code and move into base class
#ifdef YUVDUMP
	if (mix_video_vc1_counter < 10)
		ret = GetImageFromSurface (mix, frame);
//		g_usleep(5000000);
#endif  /* YUVDUMP */

	LOG_V( "Enqueueing the frame with frame manager, timestamp %"G_GINT64_FORMAT"\n", timestamp);

	//Enqueue the decoded frame using frame manager
	ret = mix_framemanager_enqueue(mix->framemgr, frame);

	if (ret != MIX_RESULT_SUCCESS)
	{
		LOG_E( "Error enqueuing frame object\n");
		goto cleanup;
	}
	unrefVideoFrame = FALSE;
	
	 
cleanup:

	mix_videofmt_vc1_release_input_buffers(mix, timestamp);
	if (unrefVideoFrame)
		mix_videoframe_unref(frame);


	LOG_V( "End\n");

	return ret;
}

MIX_RESULT mix_videofmt_vc1_flush(MixVideoFormat *mix)
{
	MIX_RESULT ret = MIX_RESULT_SUCCESS;

	if (mix == NULL)
	{
		LOG_E( "Null pointer passed in\n");
		return MIX_RESULT_NULL_PTR;
	}

	LOG_V( "Begin\n");

	uint32 pret = 0;
	MixInputBufferEntry *bufentry = NULL;

	/* Chainup parent method.
		We are not chaining up to parent method for now.
	 */

#if 0
	if (parent_class->flush) 
	{
		return parent_class->flush(mix, msg);
	}
#endif

	MixVideoFormat_VC1 *self = MIX_VIDEOFORMAT_VC1(mix);

	g_mutex_lock(mix->objectlock);

	//Clear the contents of inputbufqueue
	while (!g_queue_is_empty(mix->inputbufqueue))
	{
		bufentry = (MixInputBufferEntry *) g_queue_pop_head(mix->inputbufqueue);
		if (bufentry == NULL) 
			continue;

		mix_buffer_unref(bufentry->buf);
		g_free(bufentry);
	}

	//Clear parse_in_progress flag and current timestamp
	mix->parse_in_progress = FALSE;
	mix->discontinuity_frame_in_progress = FALSE;
	mix->current_timestamp = 0;

	int i = 0;
	for (; i < 2; i++)
	{
		if (self->reference_frames[i] != NULL)
		{
			mix_videoframe_unref(self->reference_frames[i]);
			self->reference_frames[i] = NULL;
		}
	}

	//Call parser flush
	pret = vbp_flush(mix->parser_handle);
	if (pret != VBP_OK)
		ret = MIX_RESULT_FAIL;

	g_mutex_unlock(mix->objectlock);

	LOG_V( "End\n");

	return ret;
}

MIX_RESULT mix_videofmt_vc1_eos(MixVideoFormat *mix) 
{
	MIX_RESULT ret = MIX_RESULT_SUCCESS;
	vbp_data_vc1 *data = NULL;
	uint32 pret = 0;

	if (mix == NULL)
	{
		LOG_E( "Null pointer passed in\n");
		return MIX_RESULT_NULL_PTR;
	}

	LOG_V( "Begin\n");


	/* Chainup parent method.
		We are not chaining up to parent method for now.
	 */

#if 0
	if (parent_class->eos) 
	{
		return parent_class->eos(mix, msg);
	}
#endif

	g_mutex_lock(mix->objectlock);

	//if a frame is in progress, process the frame
	if (mix->parse_in_progress)
	{
		//query for data
		pret = vbp_query(mix->parser_handle, (void *) &data);

		if ((pret != VBP_OK) || (data == NULL))
		{
        	ret = MIX_RESULT_FAIL;
 			LOG_E( "Error getting last parse data\n");
			goto cleanup;
        }

		//process and decode data
		ret = mix_videofmt_vc1_process_decode(mix,
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

MIX_RESULT mix_videofmt_vc1_deinitialize(MixVideoFormat *mix) 
{
	//Note this method is not called; may remove in future
	if (mix == NULL)
	{
		LOG_E( "Null pointer passed in\n");
		return MIX_RESULT_NULL_PTR;
	}

	LOG_V( "Begin\n");

	/* Chainup parent method.
	 */

	if (parent_class->deinitialize) 
	{
		return parent_class->deinitialize(mix);
	}

    //Most stuff is cleaned up in parent_class->finalize() and in _finalize

    LOG_V( "End\n");

	return MIX_RESULT_SUCCESS;
}

MIX_RESULT mix_videofmt_vc1_handle_ref_frames(
	MixVideoFormat *mix, 
	enum _picture_type frame_type,
	MixVideoFrame * current_frame)
{

	LOG_V( "Begin\n");

	if (mix == NULL || current_frame == NULL)
	{
		LOG_E( "Null pointer passed in\n");
		return MIX_RESULT_NULL_PTR;
	}

	MixVideoFormat_VC1 *self = MIX_VIDEOFORMAT_VC1(mix);


	switch (frame_type)
	{
		case VC1_PTYPE_I:  // I frame type
		case VC1_PTYPE_P:  // P frame type
			LOG_V( "Refing reference frame %x\n", (guint) current_frame);
			mix_videoframe_ref(current_frame);

			//If we have B frames, we need to keep forward and backward reference frames
			if (self->haveBframes)
			{
				if (self->reference_frames[0] == NULL) //should only happen on first frame
				{
					self->reference_frames[0] = current_frame;
//					self->reference_frames[1] = NULL;
				}
				else if (self->reference_frames[1] == NULL) //should only happen on second frame
				{
					self->reference_frames[1] = current_frame;
				}
				else
				{
					LOG_V( "Releasing reference frame %x\n", (guint) self->reference_frames[0]);
					mix_videoframe_unref(self->reference_frames[0]);
					self->reference_frames[0] = self->reference_frames[1];
					self->reference_frames[1] = current_frame;
				}
			}
			else  //No B frames in this content, only need to keep the forward reference frame
			{
				LOG_V( "Releasing reference frame %x\n", (guint) self->reference_frames[0]);
				if (self->reference_frames[0] != NULL)
					mix_videoframe_unref(self->reference_frames[0]);
				self->reference_frames[0] = current_frame;
					
			}
			break;
		case VC1_PTYPE_B:  // B or BI frame type (should not happen)
		case VC1_PTYPE_BI: 
		default: 
			LOG_E( "Wrong frame type for handling reference frames\n");
			return MIX_RESULT_FAIL;
			break;
			
	}

	LOG_V( "End\n");

	return MIX_RESULT_SUCCESS;
}

MIX_RESULT mix_videofmt_vc1_release_input_buffers(
	MixVideoFormat *mix, 
	guint64 timestamp) 
{
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
		bufentry = (MixInputBufferEntry *) g_queue_peek_head(mix->inputbufqueue);
		if (bufentry == NULL) 
			break;

		LOG_V( "head of queue buf %x, timestamp %"G_GINT64_FORMAT", buffer timestamp %"G_GINT64_FORMAT"\n", (guint)bufentry->buf, timestamp, bufentry->timestamp);

		if (bufentry->timestamp != timestamp)
		{
			LOG_V( "buf %x, timestamp %"G_GINT64_FORMAT", buffer timestamp %"G_GINT64_FORMAT"\n", (guint)bufentry->buf, timestamp, bufentry->timestamp);
			done = TRUE;
			break;
		}

		bufentry = (MixInputBufferEntry *) g_queue_pop_head(mix->inputbufqueue);

		LOG_V( "Unref this MixBuffers %x\n", (guint)bufentry->buf);
		mix_buffer_unref(bufentry->buf);
		g_free(bufentry);
	}
	

	LOG_V( "End\n");

	return MIX_RESULT_SUCCESS;
}


