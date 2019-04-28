/*
 INTEL CONFIDENTIAL
 Copyright 2009 Intel Corporation All Rights Reserved.
 The source code contained or described herein and all documents related to the source code ("Material") are owned by Intel Corporation or its suppliers or licensors. Title to the Material remains with Intel Corporation or its suppliers and licensors. The Material contains trade secrets and proprietary and confidential information of Intel or its suppliers and licensors. The Material is protected by worldwide copyright and trade secret laws and treaty provisions. No part of the Material may be used, copied, reproduced, modified, published, uploaded, posted, transmitted, distributed, or disclosed in any way without Intel’s prior express written permission.

 No license under any patent, copyright, trade secret or other intellectual property right is granted to or conferred upon you by disclosure or delivery of the Materials, either expressly, by implication, inducement, estoppel or otherwise. Any license under such intellectual property rights must be express and approved by Intel in writing.
 */
#include <glib.h>
#include "mixvideolog.h"
#include "mixvideoformatenc.h"

//#define MDEBUG

/* Default vmethods implementation */
static MIX_RESULT mix_videofmtenc_getcaps_default(MixVideoFormatEnc *mix,
        GString *msg);
static MIX_RESULT mix_videofmtenc_initialize_default(MixVideoFormatEnc *mix,
        MixVideoConfigParamsEnc * config_params_enc,
        MixFrameManager * frame_mgr,
        MixBufferPool * input_buf_pool,
        MixSurfacePool ** surface_pool,
        VADisplay vadisplay);

static MIX_RESULT
mix_videofmtenc_encode_default(MixVideoFormatEnc *mix, MixBuffer * bufin[],
        gint bufincnt, MixIOVec * iovout[], gint iovoutcnt,
        MixVideoEncodeParams * encode_params);
static MIX_RESULT mix_videofmtenc_flush_default(MixVideoFormatEnc *mix);
static MIX_RESULT mix_videofmtenc_eos_default(MixVideoFormatEnc *mix);
static MIX_RESULT mix_videofmtenc_deinitialize_default(MixVideoFormatEnc *mix);
static MIX_RESULT mix_videofmtenc_get_max_coded_buffer_size_default(
	MixVideoFormatEnc *mix, guint *max_size);


static GObjectClass *parent_class = NULL;

static void mix_videoformatenc_finalize(GObject * obj);
G_DEFINE_TYPE (MixVideoFormatEnc, mix_videoformatenc, G_TYPE_OBJECT);

static void mix_videoformatenc_init(MixVideoFormatEnc * self) {
	/* TODO: public member initialization */

	/* TODO: private member initialization */

	self->objectlock = g_mutex_new();

	self->initialized = FALSE;
	self->framemgr = NULL;
	self->surfacepool = NULL;
	self->inputbufpool = NULL;
	self->inputbufqueue = NULL;
	self->va_display = NULL;
	self->va_context = 0;
	self->va_config = 0;
	self->mime_type = NULL;
	self->frame_rate_num= 0;
	self->frame_rate_denom = 1;	
	self->picture_width = 0;
	self->picture_height = 0;
	self->initial_qp = 0;
	self->min_qp = 0;
	self->intra_period = 0;
	self->bitrate = 0;
	self->share_buf_mode = FALSE;
	self->ci_frame_id = NULL;
	self->ci_frame_num = 0;
       self->drawable = 0x0;
       self->need_display = TRUE;	   

      self->va_rcmode = VA_RC_NONE;
      self->va_format = VA_RT_FORMAT_YUV420;
      self->va_entrypoint = VAEntrypointEncSlice;
      self->va_profile = VAProfileH264Baseline;	   
	
	//add more properties here
}

static void mix_videoformatenc_class_init(MixVideoFormatEncClass * klass) {
	GObjectClass *gobject_class = (GObjectClass *) klass;

	/* parent class for later use */
	parent_class = g_type_class_peek_parent(klass);

	gobject_class->finalize = mix_videoformatenc_finalize;

	/* setup vmethods with base implementation */
	klass->getcaps = mix_videofmtenc_getcaps_default;
	klass->initialize = mix_videofmtenc_initialize_default;
	klass->encode = mix_videofmtenc_encode_default;
	klass->flush = mix_videofmtenc_flush_default;
	klass->eos = mix_videofmtenc_eos_default;
	klass->deinitialize = mix_videofmtenc_deinitialize_default;
	klass->getmaxencodedbufsize = mix_videofmtenc_get_max_coded_buffer_size_default;
}

MixVideoFormatEnc *
mix_videoformatenc_new(void) {
	MixVideoFormatEnc *ret = g_object_new(MIX_TYPE_VIDEOFORMATENC, NULL);

	return ret;
}

void mix_videoformatenc_finalize(GObject * obj) {
	/* clean up here. */

    if (obj == NULL) {
        LOG_E( "obj == NULL\n");				
        return;	
    }
	
    MixVideoFormatEnc *mix = MIX_VIDEOFORMATENC(obj); 
    
    LOG_V( "\n");		

    if(mix->objectlock) {
        g_mutex_free(mix->objectlock);
        mix->objectlock = NULL;
    }

	//MiVideo object calls the _deinitialize() for frame manager
	if (mix->framemgr)
	{
	  mix_framemanager_unref(mix->framemgr);  
	  mix->framemgr = NULL;
	}	

	if (mix->mime_type)
    {
        if (mix->mime_type->str)
            g_string_free(mix->mime_type, TRUE);
        else
            g_string_free(mix->mime_type, FALSE);
    }
    
	if (mix->ci_frame_id)
        g_free (mix->ci_frame_id);
	

	if (mix->surfacepool)
	{
        mix_surfacepool_deinitialize(mix->surfacepool);
        mix_surfacepool_unref(mix->surfacepool);
        mix->surfacepool = NULL;
    }


	/* TODO: cleanup here */

	/* Chain up parent */
	if (parent_class->finalize) {
		parent_class->finalize(obj);
	}
}

MixVideoFormatEnc *
mix_videoformatenc_ref(MixVideoFormatEnc * mix) {
	return (MixVideoFormatEnc *) g_object_ref(G_OBJECT(mix));
}

/* Default vmethods implementation */
static MIX_RESULT mix_videofmtenc_getcaps_default(MixVideoFormatEnc *mix,
        GString *msg) {
    LOG_V( "Begin\n");	
    return MIX_RESULT_SUCCESS;
}

static MIX_RESULT mix_videofmtenc_initialize_default(MixVideoFormatEnc *mix,
        MixVideoConfigParamsEnc * config_params_enc,
        MixFrameManager * frame_mgr,
        MixBufferPool * input_buf_pool,
        MixSurfacePool ** surface_pool,
        VADisplay va_display) {
    
    LOG_V( "Begin\n");	
	
    if (mix == NULL ||config_params_enc == NULL) {
        LOG_E( 
                "!mix || config_params_enc == NULL\n");				
        return MIX_RESULT_NULL_PTR;
    }
	
    
    MIX_RESULT ret = MIX_RESULT_SUCCESS;

	//TODO check return values of getter fns for config_params

	g_mutex_lock(mix->objectlock);

	mix->framemgr = frame_mgr;
	mix_framemanager_ref(mix->framemgr);	

	mix->va_display = va_display;
	
    LOG_V( 
            "Start to get properities from parent params\n");
    
    /* get properties from param (parent) Object*/
    ret = mix_videoconfigparamsenc_get_bit_rate (config_params_enc, 
            &(mix->bitrate));
    if (ret != MIX_RESULT_SUCCESS) {
        //TODO cleanup
        LOG_E( 
                "Failed to mix_videoconfigparamsenc_get_bps\n");			            
        g_mutex_unlock(mix->objectlock);
        return MIX_RESULT_FAIL;
    }
    
    ret = mix_videoconfigparamsenc_get_frame_rate (config_params_enc,
            &(mix->frame_rate_num), &(mix->frame_rate_denom));
    
    if (ret != MIX_RESULT_SUCCESS) {
        //TODO cleanup
        LOG_E( 
                "Failed to mix_videoconfigparamsenc_get_frame_rate\n");            
        g_mutex_unlock(mix->objectlock);
        return MIX_RESULT_FAIL;
    }		
    
    ret = mix_videoconfigparamsenc_get_init_qp (config_params_enc,
            &(mix->initial_qp));
    
    if (ret != MIX_RESULT_SUCCESS) {
        //TODO cleanup
        
        LOG_E( 
                "Failed to mix_videoconfigparamsenc_get_init_qp\n");               
        g_mutex_unlock(mix->objectlock);
        return MIX_RESULT_FAIL;
    }		
    
    
    ret = mix_videoconfigparamsenc_get_min_qp (config_params_enc,
            &(mix->min_qp));
    
    if (ret != MIX_RESULT_SUCCESS) {
        //TODO cleanup

        LOG_E( 
                "Failed to mix_videoconfigparamsenc_get_min_qp\n");             
        g_mutex_unlock(mix->objectlock);
        return MIX_RESULT_FAIL;
    }				  
    
    ret = mix_videoconfigparamsenc_get_intra_period (config_params_enc,
            &(mix->intra_period));
    
    if (ret != MIX_RESULT_SUCCESS) {
        //TODO cleanup
        
        LOG_E( 
                "Failed to mix_videoconfigparamsenc_get_intra_period\n");               
        g_mutex_unlock(mix->objectlock);
        return MIX_RESULT_FAIL;
    }				  
    
    ret = mix_videoconfigparamsenc_get_picture_res (config_params_enc,
            &(mix->picture_width), &(mix->picture_height));
    
    if (ret != MIX_RESULT_SUCCESS) {
        //TODO cleanup

        LOG_E( 
                "Failed to mix_videoconfigparamsenc_get_picture_res\n");              
        g_mutex_unlock(mix->objectlock);
        return MIX_RESULT_FAIL;
    }	
    
    ret = mix_videoconfigparamsenc_get_share_buf_mode (config_params_enc,
            &(mix->share_buf_mode));
    
    if (ret != MIX_RESULT_SUCCESS) {
        //TODO cleanup

        LOG_E( 
                "Failed to mix_videoconfigparamsenc_get_share_buf_mode\n");                
        g_mutex_unlock(mix->objectlock);
        return MIX_RESULT_FAIL;
    }		
    
    
    ret = mix_videoconfigparamsenc_get_ci_frame_info (config_params_enc,
            &(mix->ci_frame_id),  &(mix->ci_frame_num));
    
    if (ret != MIX_RESULT_SUCCESS) {
        //TODO cleanup

        LOG_E( 
                "Failed to mix_videoconfigparamsenc_get_ci_frame_info\n");                            
        g_mutex_unlock(mix->objectlock);
        return MIX_RESULT_FAIL;
    }			
    
    
    ret = mix_videoconfigparamsenc_get_drawable (config_params_enc,
            &(mix->drawable));
    
    if (ret != MIX_RESULT_SUCCESS) {
        //TODO cleanup

        LOG_E( 
                "Failed to mix_videoconfigparamsenc_get_drawable\n");                            
        g_mutex_unlock(mix->objectlock);
        return MIX_RESULT_FAIL;
    }	

    ret = mix_videoconfigparamsenc_get_need_display (config_params_enc,
            &(mix->need_display));
    
    if (ret != MIX_RESULT_SUCCESS) {
        //TODO cleanup

        LOG_E( 
                "Failed to mix_videoconfigparamsenc_get_drawable\n");                            
        g_mutex_unlock(mix->objectlock);
        return MIX_RESULT_FAIL;
    }		

    ret = mix_videoconfigparamsenc_get_rate_control (config_params_enc,
            &(mix->va_rcmode));
    
    if (ret != MIX_RESULT_SUCCESS) {
        //TODO cleanup

        LOG_E( 
                "Failed to mix_videoconfigparamsenc_get_rc_mode\n");                            
        g_mutex_unlock(mix->objectlock);
        return MIX_RESULT_FAIL;
    }		

    ret = mix_videoconfigparamsenc_get_raw_format (config_params_enc,
            &(mix->va_format));
    
    if (ret != MIX_RESULT_SUCCESS) {
        //TODO cleanup

        LOG_E( 
                "Failed to mix_videoconfigparamsenc_get_format\n");                            
        g_mutex_unlock(mix->objectlock);
        return MIX_RESULT_FAIL;
    }		

    ret = mix_videoconfigparamsenc_get_profile (config_params_enc,
            (MixProfile *) &(mix->va_profile));
    
    if (ret != MIX_RESULT_SUCCESS) {
        //TODO cleanup

        LOG_E( 
                "Failed to mix_videoconfigparamsenc_get_profile\n");                            
        g_mutex_unlock(mix->objectlock);
        return MIX_RESULT_FAIL;
    }			
    
    
    LOG_V( 
            "======Video Encode Parent Object properities======:\n");
    
    LOG_I( "mix->bitrate = %d\n", 
            mix->bitrate);
    LOG_I( "mix->frame_rate = %d\n", 
            mix->frame_rate_denom / mix->frame_rate_denom);		
    LOG_I( "mix->initial_qp = %d\n", 
            mix->initial_qp);		
    LOG_I( "mix->min_qp = %d\n", 
            mix->min_qp);		
    LOG_I( "mix->intra_period = %d\n", 
            mix->intra_period);		
    LOG_I( "mix->picture_width = %d\n", 
            mix->picture_width);		
    LOG_I( "mix->picture_height = %d\n", 
            mix->picture_height);	
    LOG_I( "mix->share_buf_mode = %d\n", 
            mix->share_buf_mode);		
    LOG_I( "mix->ci_frame_id = 0x%08x\n", 
            mix->ci_frame_id);		
    LOG_I( "mix->ci_frame_num = %d\n", 
            mix->ci_frame_num);	
    LOG_I( "mix->drawable = 0x%08x\n", 
            mix->drawable);	
    LOG_I( "mix->need_display = %d\n", 
            mix->need_display);	
    LOG_I( "mix->va_format = %d\n", 
            mix->va_format);	
    LOG_I( "mix->va_profile = %d\n", 
            mix->va_profile);	
    LOG_I( "mix->va_rcmode = %d\n\n", 
            mix->va_rcmode);		
    
    g_mutex_unlock(mix->objectlock);
    
    LOG_V( "end\n");	
    
    return MIX_RESULT_SUCCESS;
}

static MIX_RESULT mix_videofmtenc_encode_default (MixVideoFormatEnc *mix, MixBuffer * bufin[],
        gint bufincnt, MixIOVec * iovout[], gint iovoutcnt,
        MixVideoEncodeParams * encode_params) {
    return MIX_RESULT_SUCCESS;
}

static MIX_RESULT mix_videofmtenc_flush_default(MixVideoFormatEnc *mix) {
    return MIX_RESULT_SUCCESS;
}

static MIX_RESULT mix_videofmtenc_eos_default(MixVideoFormatEnc *mix) {
	return MIX_RESULT_SUCCESS;
}

static MIX_RESULT mix_videofmtenc_deinitialize_default(MixVideoFormatEnc *mix) {

	//TODO decide whether to put any of the teardown from _finalize() here

	return MIX_RESULT_SUCCESS;
}

static MIX_RESULT mix_videofmtenc_get_max_coded_buffer_size_default(
	MixVideoFormatEnc *mix, guint *max_size) {


	return MIX_RESULT_SUCCESS;	
}

/* mixvideoformatenc class methods implementation */

MIX_RESULT mix_videofmtenc_getcaps(MixVideoFormatEnc *mix, GString *msg) {
    MixVideoFormatEncClass *klass = MIX_VIDEOFORMATENC_GET_CLASS(mix);
    
    LOG_V( "Begin\n");	
    
    if (klass->getcaps) {
        return klass->getcaps(mix, msg);
    }
    return MIX_RESULT_NOTIMPL;
}

MIX_RESULT mix_videofmtenc_initialize(MixVideoFormatEnc *mix,
        MixVideoConfigParamsEnc * config_params_enc,
        MixFrameManager * frame_mgr,
        MixBufferPool * input_buf_pool,
        MixSurfacePool ** surface_pool,
        VADisplay va_display) {
    MixVideoFormatEncClass *klass = MIX_VIDEOFORMATENC_GET_CLASS(mix);
    
    /*frame_mgr and input_buf_pool is reserved for future use*/
	if (klass->initialize) {
        return klass->initialize(mix, config_params_enc, frame_mgr,
                input_buf_pool, surface_pool, va_display);
    }
    
    return MIX_RESULT_FAIL;
    
}

MIX_RESULT mix_videofmtenc_encode(MixVideoFormatEnc *mix, MixBuffer * bufin[],
        gint bufincnt, MixIOVec * iovout[], gint iovoutcnt,
        MixVideoEncodeParams * encode_params) {
    
    MixVideoFormatEncClass *klass = MIX_VIDEOFORMATENC_GET_CLASS(mix);
    if (klass->encode) {
        return klass->encode(mix, bufin, bufincnt, iovout, iovoutcnt, encode_params);
    }
    
    return MIX_RESULT_FAIL;
}

MIX_RESULT mix_videofmtenc_flush(MixVideoFormatEnc *mix) {
    MixVideoFormatEncClass *klass = MIX_VIDEOFORMATENC_GET_CLASS(mix);
    if (klass->flush) {
        return klass->flush(mix);
    }
    
    return MIX_RESULT_FAIL;
}

MIX_RESULT mix_videofmtenc_eos(MixVideoFormatEnc *mix) {
    MixVideoFormatEncClass *klass = MIX_VIDEOFORMATENC_GET_CLASS(mix);
    if (klass->eos) {
        return klass->eos(mix);
    }
    
    return MIX_RESULT_FAIL;
}

MIX_RESULT mix_videofmtenc_deinitialize(MixVideoFormatEnc *mix) {
    MixVideoFormatEncClass *klass = MIX_VIDEOFORMATENC_GET_CLASS(mix);
    if (klass->deinitialize) {
        return klass->deinitialize(mix);
    }
    
    return MIX_RESULT_FAIL;
}

MIX_RESULT mix_videofmtenc_get_max_coded_buffer_size(MixVideoFormatEnc *mix, guint * max_size) {
    
    MixVideoFormatEncClass *klass = MIX_VIDEOFORMATENC_GET_CLASS(mix);
    if (klass->encode) {
        return klass->getmaxencodedbufsize(mix, max_size);
    }
    
    return MIX_RESULT_FAIL;
}
