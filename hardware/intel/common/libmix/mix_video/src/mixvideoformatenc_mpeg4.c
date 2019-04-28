/*
 INTEL CONFIDENTIAL
 Copyright 2009 Intel Corporation All Rights Reserved.
 The source code contained or described herein and all documents related to the source code ("Material") are owned by Intel Corporation or its suppliers or licensors. Title to the Material remains with Intel Corporation or its suppliers and licensors. The Material contains trade secrets and proprietary and confidential information of Intel or its suppliers and licensors. The Material is protected by worldwide copyright and trade secret laws and treaty provisions. No part of the Material may be used, copied, reproduced, modified, published, uploaded, posted, transmitted, distributed, or disclosed in any way without Intel’s prior express written permission.

 No license under any patent, copyright, trade secret or other intellectual property right is granted to or conferred upon you by disclosure or delivery of the Materials, either expressly, by implication, inducement, estoppel or otherwise. Any license under such intellectual property rights must be express and approved by Intel in writing.
 */
#include <glib.h>
#include <string.h>
#include <stdlib.h>

#include "mixvideolog.h"

#include "mixvideoformatenc_mpeg4.h"
#include "mixvideoconfigparamsenc_mpeg4.h"

#define MDEBUG
#undef SHOW_SRC

#ifdef SHOW_SRC
Window win = 0;
#endif /* SHOW_SRC */


/* The parent class. The pointer will be saved
 * in this class's initialization. The pointer
 * can be used for chaining method call if needed.
 */
static MixVideoFormatEncClass *parent_class = NULL;

static void mix_videoformatenc_mpeg4_finalize(GObject * obj);

/*
 * Please note that the type we pass to G_DEFINE_TYPE is MIX_TYPE_VIDEOFORMATENC
 */
G_DEFINE_TYPE (MixVideoFormatEnc_MPEG4, mix_videoformatenc_mpeg4, MIX_TYPE_VIDEOFORMATENC);

static void mix_videoformatenc_mpeg4_init(MixVideoFormatEnc_MPEG4 * self) {
    MixVideoFormatEnc *parent = MIX_VIDEOFORMATENC(self);

    /* TODO: public member initialization */

    /* TODO: private member initialization */
    self->encoded_frames = 0;
    self->pic_skipped = FALSE;
    self->is_intra = TRUE;
    self->cur_fame = NULL;
    self->ref_fame = NULL;
    self->rec_fame = NULL;	

    self->ci_shared_surfaces = NULL;
    self->surfaces= NULL;
    self->surface_num = 0;

    parent->initialized = FALSE;
}

static void mix_videoformatenc_mpeg4_class_init(
        MixVideoFormatEnc_MPEG4Class * klass) {

    /* root class */
    GObjectClass *gobject_class = (GObjectClass *) klass;

    /* direct parent class */
    MixVideoFormatEncClass *video_formatenc_class = 
        MIX_VIDEOFORMATENC_CLASS(klass);

    /* parent class for later use */
    parent_class = g_type_class_peek_parent(klass);

    /* setup finializer */
    gobject_class->finalize = mix_videoformatenc_mpeg4_finalize;

    /* setup vmethods with base implementation */
    /* TODO: decide if we need to override the parent's methods */
    video_formatenc_class->getcaps = mix_videofmtenc_mpeg4_getcaps;
    video_formatenc_class->initialize = mix_videofmtenc_mpeg4_initialize;
    video_formatenc_class->encode = mix_videofmtenc_mpeg4_encode;
    video_formatenc_class->flush = mix_videofmtenc_mpeg4_flush;
    video_formatenc_class->eos = mix_videofmtenc_mpeg4_eos;
    video_formatenc_class->deinitialize = mix_videofmtenc_mpeg4_deinitialize;
    video_formatenc_class->getmaxencodedbufsize = mix_videofmtenc_mpeg4_get_max_encoded_buf_size;
}

MixVideoFormatEnc_MPEG4 *
mix_videoformatenc_mpeg4_new(void) {
    MixVideoFormatEnc_MPEG4 *ret =
        g_object_new(MIX_TYPE_VIDEOFORMATENC_MPEG4, NULL);

    return ret;
}

void mix_videoformatenc_mpeg4_finalize(GObject * obj) {
    /* clean up here. */

    /*MixVideoFormatEnc_MPEG4 *mix = MIX_VIDEOFORMATENC_MPEG4(obj); */
    GObjectClass *root_class = (GObjectClass *) parent_class;

    LOG_V( "\n");

    /* Chain up parent */
    if (root_class->finalize) {
        root_class->finalize(obj);
    }
}

MixVideoFormatEnc_MPEG4 *
mix_videoformatenc_mpeg4_ref(MixVideoFormatEnc_MPEG4 * mix) {
    return (MixVideoFormatEnc_MPEG4 *) g_object_ref(G_OBJECT(mix));
}

/*MPEG-4:2 vmethods implementation */
MIX_RESULT mix_videofmtenc_mpeg4_getcaps(MixVideoFormatEnc *mix, GString *msg) {

    /* TODO: add codes for MPEG-4:2 */

    /* TODO: decide if we need to chainup parent method.
     * if we do, the following is the code:
     */

    LOG_V( "mix_videofmtenc_mpeg4_getcaps\n");

    if (mix == NULL) {
        LOG_E( "mix == NULL\n");				
        return MIX_RESULT_NULL_PTR;	
    }
	

    if (parent_class->getcaps) {
        return parent_class->getcaps(mix, msg);
    }
    return MIX_RESULT_SUCCESS;
}

MIX_RESULT mix_videofmtenc_mpeg4_initialize(MixVideoFormatEnc *mix, 
        MixVideoConfigParamsEnc * config_params_enc,
        MixFrameManager * frame_mgr,
        MixBufferPool * input_buf_pool,
        MixSurfacePool ** surface_pool,
        VADisplay va_display ) {

    MIX_RESULT ret = MIX_RESULT_SUCCESS;
    MixVideoFormatEnc *parent = NULL;
    MixVideoConfigParamsEncMPEG4 * config_params_enc_mpeg4;
    
    VAStatus va_status = VA_STATUS_SUCCESS;
    VASurfaceID * surfaces;
    
    gint va_max_num_profiles, va_max_num_entrypoints, va_max_num_attribs;
    gint va_num_profiles,  va_num_entrypoints;

    VAProfile *va_profiles = NULL;
    VAEntrypoint *va_entrypoints = NULL;
    VAConfigAttrib va_attrib[2];	
    guint index;		
	

    /*frame_mgr and input_buf_pool is reservered for future use*/
    
    if (mix == NULL || config_params_enc == NULL || va_display == NULL) {
        LOG_E( 
                "mix == NULL || config_params_enc == NULL || va_display == NULL\n");			
        return MIX_RESULT_NULL_PTR;
    }

    LOG_V( "begin\n");

    
    //TODO additional parameter checking
 
    /* Chainup parent method. */
#if 1
    if (parent_class->initialize) {
        ret = parent_class->initialize(mix, config_params_enc,
                frame_mgr, input_buf_pool, surface_pool, 
                va_display);
    }
    
    if (ret != MIX_RESULT_SUCCESS)
    {
        return ret;
    }
    
#endif //disable it currently

    if (MIX_IS_VIDEOFORMATENC_MPEG4(mix))
    {
        parent = MIX_VIDEOFORMATENC(&(mix->parent));
        MixVideoFormatEnc_MPEG4 *self = MIX_VIDEOFORMATENC_MPEG4(mix);
        
        if (MIX_IS_VIDEOCONFIGPARAMSENC_MPEG4 (config_params_enc)) {
            config_params_enc_mpeg4 = 
                MIX_VIDEOCONFIGPARAMSENC_MPEG4 (config_params_enc);
        } else {
            LOG_V( 
                    "mix_videofmtenc_mpeg4_initialize:  no mpeg4 config params found\n");
            return MIX_RESULT_FAIL;
        }
        
        g_mutex_lock(parent->objectlock);        

        LOG_V( 
                "Start to get properities from MPEG-4:2 params\n");

        /* get properties from MPEG4 params Object, which is special to MPEG4 format*/

        ret = mix_videoconfigparamsenc_mpeg4_get_profile_level (config_params_enc_mpeg4,
                &self->profile_and_level_indication);
        
        if (ret != MIX_RESULT_SUCCESS) {
            //TODO cleanup
            LOG_E( 
                    "Failed to mix_videoconfigparamsenc_mpeg4_get_profile_level\n");                             
            g_mutex_unlock(parent->objectlock);
            return MIX_RESULT_FAIL;
        }	

        ret = mix_videoconfigparamsenc_mpeg4_get_fixed_vti (config_params_enc_mpeg4,
                &(self->fixed_vop_time_increment));
        
        if (ret != MIX_RESULT_SUCCESS) {
            //TODO cleanup
            LOG_E( 
                    "Failed to mix_videoconfigparamsenc_mpeg4_get_fixed_vti\n");                             
            g_mutex_unlock(parent->objectlock);
            return MIX_RESULT_FAIL;
        }			
          
        ret = mix_videoconfigparamsenc_mpeg4_get_dlk (config_params_enc_mpeg4,
                &(self->disable_deblocking_filter_idc));
        
        if (ret != MIX_RESULT_SUCCESS) {
            //TODO cleanup
            LOG_E( 
                    "Failed to config_params_enc_mpeg4\n");            
            g_mutex_unlock(parent->objectlock);
            return MIX_RESULT_FAIL;
        }			


        LOG_V( 
                "======MPEG4 Encode Object properities======:\n");

        LOG_I( "self->profile_and_level_indication = %d\n", 
                self->profile_and_level_indication);			
        LOG_I( "self->fixed_vop_time_increment = %d\n\n", 
                self->fixed_vop_time_increment);					
        
        LOG_V( 
                "Get properities from params done\n");
        

    	//display = XOpenDisplay(NULL);    
     	//va_display = vaGetDisplay (videoencobj->display);

        parent->va_display = va_display;	
        
        LOG_V( "Get Display\n");
        LOG_I( "Display = 0x%08x\n", 
                (guint)va_display);			

        //va_status = vaInitialize(va_display, &va_major_ver, &va_minor_ver);
        //g_print ("vaInitialize va_status = %d\n", va_status);


#if 0
        /* query the vender information, can ignore*/
        va_vendor = vaQueryVendorString (va_display);
        LOG_I( "Vendor = %s\n", 
                va_vendor);			
#endif		
        
        /*get the max number for profiles/entrypoints/attribs*/
        va_max_num_profiles = vaMaxNumProfiles(va_display);
        LOG_I( "va_max_num_profiles = %d\n", 
                va_max_num_profiles);		
        
        va_max_num_entrypoints = vaMaxNumEntrypoints(va_display);
        LOG_I( "va_max_num_entrypoints = %d\n", 
                va_max_num_entrypoints);	
        
        va_max_num_attribs = vaMaxNumConfigAttributes(va_display);
        LOG_I( "va_max_num_attribs = %d\n", 
                va_max_num_attribs);		        
          
        va_profiles = g_malloc(sizeof(VAProfile)*va_max_num_profiles);
        va_entrypoints = g_malloc(sizeof(VAEntrypoint)*va_max_num_entrypoints);	
        
        if (va_profiles == NULL || va_entrypoints ==NULL) 
        {
            LOG_E( 
                    "!va_profiles || !va_entrypoints\n");	
            g_mutex_unlock(parent->objectlock);
            return MIX_RESULT_NO_MEMORY;
        }

        LOG_I( 
                "va_profiles = 0x%08x\n", (guint)va_profiles);		
		
        LOG_V( "vaQueryConfigProfiles\n");
		        	 	 
        
        va_status = vaQueryConfigProfiles (va_display, va_profiles, &va_num_profiles);
        
        if (va_status != VA_STATUS_SUCCESS)	 
        {
            LOG_E( 
                    "Failed to call vaQueryConfigProfiles\n");	
            g_free(va_profiles);
            g_free (va_entrypoints);
            g_mutex_unlock(parent->objectlock);		
            return MIX_RESULT_FAIL;
        }
        
        LOG_V( "vaQueryConfigProfiles Done\n");

        
        
        /*check whether profile is supported*/
        for(index= 0; index < va_num_profiles; index++) {
            if(parent->va_profile == va_profiles[index])
                break;
        }
        
        if(index == va_num_profiles) 
        {
            LOG_E( "Profile not supported\n");				
            g_free(va_profiles);
            g_free (va_entrypoints);
            g_mutex_unlock(parent->objectlock);	 
            return MIX_RESULT_FAIL;  //Todo, add error handling here
        }

        LOG_V( "vaQueryConfigEntrypoints\n");
        
	
        /*Check entry point*/
        va_status = vaQueryConfigEntrypoints(va_display, 
                parent->va_profile, 
                va_entrypoints, &va_num_entrypoints);
        
        if (va_status != VA_STATUS_SUCCESS)	 
        {
            LOG_E( 
                    "Failed to call vaQueryConfigEntrypoints\n");	
            g_free(va_profiles);
            g_free (va_entrypoints);
            g_mutex_unlock(parent->objectlock);
            return MIX_RESULT_FAIL;
        }
        
        for (index = 0; index < va_num_entrypoints; index ++) {
            if (va_entrypoints[index] == VAEntrypointEncSlice) {
                break;
            }
        }
        
        if (index == va_num_entrypoints) {
            LOG_E( "Entrypoint not found\n");			
            g_free(va_profiles);
            g_free (va_entrypoints);		
            g_mutex_unlock(parent->objectlock);
            return MIX_RESULT_FAIL;  //Todo, add error handling here
        }	
        
        
        /*free profiles and entrypoints*/
        g_free(va_profiles);
        g_free (va_entrypoints);
        
        va_attrib[0].type = VAConfigAttribRTFormat;
        va_attrib[1].type = VAConfigAttribRateControl;
        
        LOG_V( "vaGetConfigAttributes\n");
        
        va_status = vaGetConfigAttributes(va_display, parent->va_profile, 
                parent->va_entrypoint,
                &va_attrib[0], 2);		
        
        if (va_status != VA_STATUS_SUCCESS)	 
        {
            LOG_E( 
                    "Failed to call vaGetConfigAttributes\n");	
            g_mutex_unlock(parent->objectlock);
            return MIX_RESULT_FAIL;
        }
        
        if ((va_attrib[0].value & parent->va_format) == 0) {
            LOG_E( "Matched format not found\n");	
            g_mutex_unlock(parent->objectlock);		
            return MIX_RESULT_FAIL;  //Todo, add error handling here
        }	  
        
        
        if ((va_attrib[1].value & parent->va_rcmode) == 0) {
            LOG_E( "RC mode not found\n");	
            g_mutex_unlock(parent->objectlock);		
            return MIX_RESULT_FAIL;  //Todo, add error handling here
        }
        
        va_attrib[0].value = parent->va_format; //VA_RT_FORMAT_YUV420;
        va_attrib[1].value = parent->va_rcmode; 

        LOG_V( "======VA Configuration======\n");

        LOG_I( "profile = %d\n", 
                parent->va_profile);	
        LOG_I( "va_entrypoint = %d\n", 
                parent->va_entrypoint);	
        LOG_I( "va_attrib[0].type = %d\n", 
                va_attrib[0].type);			
        LOG_I( "va_attrib[1].type = %d\n", 
                va_attrib[1].type);				
        LOG_I( "va_attrib[0].value (Format) = %d\n", 
                va_attrib[0].value);			
        LOG_I( "va_attrib[1].value (RC mode) = %d\n", 
                va_attrib[1].value);				

        LOG_V( "vaCreateConfig\n");
		
        va_status = vaCreateConfig(va_display, parent->va_profile, 
                parent->va_entrypoint, 
                &va_attrib[0], 2, &(parent->va_config));
        
        if (va_status != VA_STATUS_SUCCESS)	 
        {
            LOG_E( "Failed vaCreateConfig\n");				
            g_mutex_unlock(parent->objectlock);
            return MIX_RESULT_FAIL;
        }

        /*TODO: compute the surface number*/
        int numSurfaces;
        
        if (parent->share_buf_mode) {
            numSurfaces = 2;
        }
        else {
            numSurfaces = 8;
            parent->ci_frame_num = 0;			
        }
        
        self->surface_num = numSurfaces + parent->ci_frame_num;
        
        surfaces = g_malloc(sizeof(VASurfaceID)*numSurfaces);
        
        if (surfaces == NULL)
        {
            LOG_E( 
                    "Failed allocate surface\n");	
            g_mutex_unlock(parent->objectlock);
            return MIX_RESULT_NO_MEMORY;
        }
      
        LOG_V( "vaCreateSurfaces\n");
        
        va_status = vaCreateSurfaces(va_display, parent->picture_width, 
                parent->picture_height, parent->va_format,
                numSurfaces, surfaces);
        //TODO check vret and return fail if needed

        if (va_status != VA_STATUS_SUCCESS)	 
        {
            LOG_E( 
                    "Failed vaCreateSurfaces\n");	
            g_mutex_unlock(parent->objectlock);
            return MIX_RESULT_FAIL;
        }

        if (parent->share_buf_mode) {
            
            LOG_V( 
                    "We are in share buffer mode!\n");	
            self->ci_shared_surfaces = 
                g_malloc(sizeof(VASurfaceID) * parent->ci_frame_num);
    
            if (self->ci_shared_surfaces == NULL)
            {
                LOG_E( 
                        "Failed allocate shared surface\n");	
                g_mutex_unlock(parent->objectlock);
                return MIX_RESULT_NO_MEMORY;
            }
            
            guint index;
            for(index = 0; index < parent->ci_frame_num; index++) {
                
                LOG_I( "ci_frame_id = %lu\n", 
                        parent->ci_frame_id[index]);	
                
                LOG_V( 
                        "vaCreateSurfaceFromCIFrame\n");		
                
                va_status = vaCreateSurfaceFromCIFrame(va_display, 
                        (gulong) (parent->ci_frame_id[index]), 
                        &self->ci_shared_surfaces[index]);
                if (va_status != VA_STATUS_SUCCESS)	 
                {
                    LOG_E( 
                            "Failed to vaCreateSurfaceFromCIFrame\n");				   
                    g_mutex_unlock(parent->objectlock);
                    return MIX_RESULT_FAIL;
                }		
            }
            
            LOG_V( 
                    "vaCreateSurfaceFromCIFrame Done\n");
            
        }// if (parent->share_buf_mode)
        
        self->surfaces = g_malloc(sizeof(VASurfaceID) * self->surface_num);
        
        if (self->surfaces == NULL)
        {
            LOG_E( 
                    "Failed allocate private surface\n");	
            g_free (surfaces);			
            g_mutex_unlock(parent->objectlock);
            return MIX_RESULT_NO_MEMORY;
        }		

        if (parent->share_buf_mode) {  
            /*shared surfaces should be put in pool first, 
             * because we will get it accoring to CI index*/
            for(index = 0; index < parent->ci_frame_num; index++)
                self->surfaces[index] = self->ci_shared_surfaces[index];
        }
        
        for(index = 0; index < numSurfaces; index++) {
            self->surfaces[index + parent->ci_frame_num] = surfaces[index];	
        }

        LOG_V( "assign surface Done\n");	
        LOG_I( "Created %d libva surfaces\n", 
                numSurfaces + parent->ci_frame_num);		
        
#if 0  //current put this in gst
        images = g_malloc(sizeof(VAImage)*numSurfaces);	
        if (images == NULL)
        {
            g_mutex_unlock(parent->objectlock);            
            return MIX_RESULT_FAIL;
        }		
        
        for (index = 0; index < numSurfaces; index++) {   
            //Derive an VAImage from an existing surface. 
            //The image buffer can then be mapped/unmapped for CPU access
            va_status = vaDeriveImage(va_display, surfaces[index],
                    &images[index]);
        }
#endif		 
        
        LOG_V( "mix_surfacepool_new\n");		

        parent->surfacepool = mix_surfacepool_new();
        if (surface_pool)
            *surface_pool = parent->surfacepool;  
        //which is useful to check before encode

        if (parent->surfacepool == NULL)
        {
            LOG_E( 
                    "Failed to mix_surfacepool_new\n");
            g_free (surfaces);			
            g_mutex_unlock(parent->objectlock);
            return MIX_RESULT_FAIL;
        }

        LOG_V( 
                "mix_surfacepool_initialize\n");			
        
        ret = mix_surfacepool_initialize(parent->surfacepool,
                self->surfaces, parent->ci_frame_num + numSurfaces);
        
        switch (ret)
        {
            case MIX_RESULT_SUCCESS:
                break;
            case MIX_RESULT_ALREADY_INIT:
                //TODO cleanup and/or retry
                g_free (surfaces);			
                g_mutex_unlock(parent->objectlock);                
                return MIX_RESULT_FAIL;
            default:
                break;
        }

        
        //Initialize and save the VA context ID
        LOG_V( "vaCreateContext\n");		        
          
        va_status = vaCreateContext(va_display, parent->va_config,
                parent->picture_width, parent->picture_height,
                VA_PROGRESSIVE, self->surfaces, parent->ci_frame_num + numSurfaces,
                &(parent->va_context));
        
        LOG_I( 
                "Created libva context width %d, height %d\n", 
                parent->picture_width, parent->picture_height);
        
        if (va_status != VA_STATUS_SUCCESS)	 
        {
            LOG_E( 
                    "Failed to vaCreateContext\n");	
            LOG_I( "va_status = %d\n", 
                    (guint)va_status);			
            g_free (surfaces);			
            g_mutex_unlock(parent->objectlock);			
            return MIX_RESULT_FAIL;
        }

	 guint max_size = 0;
        ret = mix_videofmtenc_mpeg4_get_max_encoded_buf_size (parent, &max_size);
        if (ret != MIX_RESULT_SUCCESS)
        {
            LOG_E( 
                    "Failed to mix_videofmtenc_mpeg4_get_max_encoded_buf_size\n");	
            g_free (surfaces);			
            g_mutex_unlock(parent->objectlock);			
            return MIX_RESULT_FAIL;
            
        }
    
        /*Create coded buffer for output*/
        va_status = vaCreateBuffer (va_display, parent->va_context,
                VAEncCodedBufferType,
                self->coded_buf_size,  //
                1, NULL,
                &self->coded_buf);
        
        if (va_status != VA_STATUS_SUCCESS)	 
        {
            LOG_E( 
                    "Failed to vaCreateBuffer: VAEncCodedBufferType\n");	
            g_free (surfaces);			
            g_mutex_unlock(parent->objectlock);
            return MIX_RESULT_FAIL;
        }
        
#ifdef SHOW_SRC
        Display * display = XOpenDisplay (NULL);

        LOG_I( "display = 0x%08x\n", 
                (guint) display);	        
        win = XCreateSimpleWindow(display, RootWindow(display, 0), 0, 0,
                parent->picture_width,  parent->picture_height, 0, 0,
                WhitePixel(display, 0));
        XMapWindow(display, win);
        XSelectInput(display, win, KeyPressMask | StructureNotifyMask);

        XSync(display, False);
        LOG_I( "va_display = 0x%08x\n", 
                (guint) va_display);	            
        
#endif /* SHOW_SRC */		

        parent->initialized = TRUE;
        
        g_mutex_unlock(parent->objectlock);
        g_free (surfaces);
              
    }
    else
    {
        LOG_E( 
                "not MPEG4 video encode Object\n");			
        return MIX_RESULT_FAIL;
        
    }

    LOG_V( "end\n");		
    
    return MIX_RESULT_SUCCESS;
}

MIX_RESULT mix_videofmtenc_mpeg4_encode(MixVideoFormatEnc *mix, MixBuffer * bufin[],
        gint bufincnt, MixIOVec * iovout[], gint iovoutcnt,
        MixVideoEncodeParams * encode_params) {
    
    MIX_RESULT ret = MIX_RESULT_SUCCESS;
    MixVideoFormatEnc *parent = NULL;
    
    LOG_V( "Begin\n");		
    
    /*currenly only support one input and output buffer*/
    //TODO: params i

    if (bufincnt != 1 || iovoutcnt != 1) {
        LOG_E( 
                "buffer count not equel to 1\n");			
        LOG_E( 
                "maybe some exception occurs\n");				
    }
   
    if (mix == NULL ||bufin[0] == NULL ||  iovout[0] == NULL) {
        LOG_E( 
                "!mix || !bufin[0] ||!iovout[0]\n");				
        return MIX_RESULT_NULL_PTR;
    }
    
    //TODO: encode_params is reserved here for future usage.

    /* TODO: decide if we need to chainup parent method.
     *      * * if we do, the following is the code:
     * */
    
#if 0
    if (parent_class->encode) {
        return parent_class->encode(mix, bufin, bufincnt, iovout,
                iovoutcnt, encode_params);
    }
#endif
    
    if (MIX_IS_VIDEOFORMATENC_MPEG4(mix))
    {
        
        parent = MIX_VIDEOFORMATENC(&(mix->parent));
        MixVideoFormatEnc_MPEG4 *self = MIX_VIDEOFORMATENC_MPEG4 (mix);
        
        LOG_V( "Locking\n");		
        g_mutex_lock(parent->objectlock);
        
        
        //TODO: also we could move some encode Preparation work to here
    
        LOG_V( 
                "mix_videofmtenc_mpeg4_process_encode\n");		        

        ret = mix_videofmtenc_mpeg4_process_encode (self, 
                bufin[0], iovout[0]);
        if (ret != MIX_RESULT_SUCCESS)
        {
            LOG_E( 
                    "Failed mix_videofmtenc_mpeg4_process_encode\n");		
            return MIX_RESULT_FAIL;
        }
        
        
        LOG_V( "UnLocking\n");		
		
        g_mutex_unlock(parent->objectlock);
    }    
    else
    {
        LOG_E( 
                "not MPEG4 video encode Object\n");			
        return MIX_RESULT_FAIL;   
    }

    LOG_V( "end\n");		
    
    return MIX_RESULT_SUCCESS;
}

MIX_RESULT mix_videofmtenc_mpeg4_flush(MixVideoFormatEnc *mix) {
    
    //MIX_RESULT ret = MIX_RESULT_SUCCESS;
    
    LOG_V( "Begin\n");	

    if (mix == NULL) {
        LOG_E( "mix == NULL\n");				
        return MIX_RESULT_NULL_PTR;	
    }	
 
    
    /*not chain to parent flush func*/
#if 0
    if (parent_class->flush) {
        return parent_class->flush(mix, msg);
    }
#endif
    
    MixVideoFormatEnc_MPEG4 *self = MIX_VIDEOFORMATENC_MPEG4(mix);
    
    g_mutex_lock(mix->objectlock);
    
    /*unref the current source surface*/ 
    if (self->cur_fame != NULL)
    {
        mix_videoframe_unref (self->cur_fame);
        self->cur_fame = NULL;
    }
    
    /*unref the reconstructed surface*/ 
    if (self->rec_fame != NULL)
    {
        mix_videoframe_unref (self->rec_fame);
        self->rec_fame = NULL;
    }

    /*unref the reference surface*/ 
    if (self->ref_fame != NULL)
    {
        mix_videoframe_unref (self->ref_fame);
        self->ref_fame = NULL;       
    }
    
    /*reset the properities*/    
    self->encoded_frames = 0;
    self->pic_skipped = FALSE;
    self->is_intra = TRUE;
    
    g_mutex_unlock(mix->objectlock);
    
    LOG_V( "end\n");		
    
    return MIX_RESULT_SUCCESS;
}

MIX_RESULT mix_videofmtenc_mpeg4_eos(MixVideoFormatEnc *mix) {

    /* TODO: add codes for MPEG-4:2 */

    /* TODO: decide if we need to chainup parent method.
     * if we do, the following is the code:
     */
   
    LOG_V( "\n");		 

    if (mix == NULL) {
        LOG_E( "mix == NULL\n");				
        return MIX_RESULT_NULL_PTR;	
    }	

    if (parent_class->eos) {
        return parent_class->eos(mix);
    }
    return MIX_RESULT_SUCCESS;
}

MIX_RESULT mix_videofmtenc_mpeg4_deinitialize(MixVideoFormatEnc *mix) {
    
    MixVideoFormatEnc *parent = NULL;
    VAStatus va_status;
	    
    LOG_V( "Begin\n");		

    if (mix == NULL) {
        LOG_E( "mix == NULL\n");				
        return MIX_RESULT_NULL_PTR;	
    }	

    parent = MIX_VIDEOFORMATENC(&(mix->parent));
    MixVideoFormatEnc_MPEG4 *self = MIX_VIDEOFORMATENC_MPEG4(mix);	
   
    LOG_V( "Release frames\n");		

    g_mutex_lock(parent->objectlock);

#if 0
    /*unref the current source surface*/ 
    if (self->cur_fame != NULL)
    {
        mix_videoframe_unref (self->cur_fame);
        self->cur_fame = NULL;
    }
#endif	
    
    /*unref the reconstructed surface*/ 
    if (self->rec_fame != NULL)
    {
        mix_videoframe_unref (self->rec_fame);
        self->rec_fame = NULL;
    }

    /*unref the reference surface*/ 
    if (self->ref_fame != NULL)
    {
        mix_videoframe_unref (self->ref_fame);
        self->ref_fame = NULL;       
    }	

    LOG_V( "Release surfaces\n");			

    if (self->ci_shared_surfaces)
    {
        g_free (self->ci_shared_surfaces);
        self->ci_shared_surfaces = NULL;
    }

    if (self->surfaces)
    {
        g_free (self->surfaces);    
        self->surfaces = NULL;
    }		

    LOG_V( "vaDestroyContext\n");	
    
    va_status = vaDestroyContext (parent->va_display, parent->va_context);
    if (va_status != VA_STATUS_SUCCESS)	 
    {
        LOG_E( 
                "Failed vaDestroyContext\n");		
        g_mutex_unlock(parent->objectlock);
        return MIX_RESULT_FAIL;
    }		

    LOG_V( "vaDestroyConfig\n");	
    
    va_status = vaDestroyConfig (parent->va_display, parent->va_config);	
    if (va_status != VA_STATUS_SUCCESS)	 
    {
        LOG_E( 
                "Failed vaDestroyConfig\n");	
        g_mutex_unlock(parent->objectlock);
        return MIX_RESULT_FAIL;
    }			

    parent->initialized = TRUE;

    g_mutex_unlock(parent->objectlock);	

#if 1
    if (parent_class->deinitialize) {
        return parent_class->deinitialize(mix);
    }
#endif	

    //Most stuff is cleaned up in parent_class->finalize()

    LOG_V( "end\n");			
    
    return MIX_RESULT_SUCCESS;
}

MIX_RESULT mix_videofmtenc_mpeg4_send_seq_params (MixVideoFormatEnc_MPEG4 *mix)
{
    
    VAStatus va_status;
    VAEncSequenceParameterBufferMPEG4 mpeg4_seq_param;
    VABufferID				seq_para_buf_id;
	
    
    MixVideoFormatEnc *parent = NULL;
    
    if (mix == NULL)
        return MIX_RESULT_NULL_PTR;
    
    LOG_V( "Begin\n\n");		
    
    if (MIX_IS_VIDEOFORMATENC_MPEG4(mix))
    {
        parent = MIX_VIDEOFORMATENC(&(mix->parent));	
        
        /*set up the sequence params for HW*/
        mpeg4_seq_param.profile_and_level_indication = mix->profile_and_level_indication;  //TODO, hard code now
        mpeg4_seq_param.video_object_layer_width= parent->picture_width;
        mpeg4_seq_param.video_object_layer_height= parent->picture_height;
        mpeg4_seq_param.vop_time_increment_resolution = 
			(unsigned int) (parent->frame_rate_num + parent->frame_rate_denom /2 ) / parent->frame_rate_denom;
        mpeg4_seq_param.fixed_vop_time_increment= mix->fixed_vop_time_increment;	
        mpeg4_seq_param.bits_per_second= parent->bitrate;		
        mpeg4_seq_param.frame_rate = 
			(unsigned int) (parent->frame_rate_num + parent->frame_rate_denom /2 ) / parent->frame_rate_denom;
        mpeg4_seq_param.initial_qp = parent->initial_qp;
        mpeg4_seq_param.min_qp = parent->min_qp;
        mpeg4_seq_param.intra_period = parent->intra_period;
		

        //mpeg4_seq_param.fixed_vop_rate = 30;
		


        LOG_V( 
                "===mpeg4 sequence params===\n");		
        
        LOG_I( "profile_and_level_indication = %d\n", 
                (guint)mpeg4_seq_param.profile_and_level_indication);	
        LOG_I( "intra_period = %d\n", 
                mpeg4_seq_param.intra_period);			
        LOG_I( "video_object_layer_width = %d\n", 
                mpeg4_seq_param.video_object_layer_width);	 
        LOG_I( "video_object_layer_height = %d\n", 
                mpeg4_seq_param.video_object_layer_height);		
        LOG_I( "vop_time_increment_resolution = %d\n", 
                mpeg4_seq_param.vop_time_increment_resolution);	
        LOG_I( "fixed_vop_rate = %d\n", 
                mpeg4_seq_param.fixed_vop_rate);		
        LOG_I( "fixed_vop_time_increment = %d\n", 
                mpeg4_seq_param.fixed_vop_time_increment);			
        LOG_I( "bitrate = %d\n", 
                mpeg4_seq_param.bits_per_second);	
        LOG_I( "frame_rate = %d\n", 
                mpeg4_seq_param.frame_rate);		
        LOG_I( "initial_qp = %d\n", 
                mpeg4_seq_param.initial_qp);		
        LOG_I( "min_qp = %d\n", 
                mpeg4_seq_param.min_qp);	
        LOG_I( "intra_period = %d\n\n", 
                mpeg4_seq_param.intra_period);				             
        
        va_status = vaCreateBuffer(parent->va_display, parent->va_context,
                VAEncSequenceParameterBufferType,
                sizeof(mpeg4_seq_param),
                1, &mpeg4_seq_param,
                &seq_para_buf_id);
        if (va_status != VA_STATUS_SUCCESS)	 
        {
            LOG_E( 
                    "Failed to vaCreateBuffer\n");				
            return MIX_RESULT_FAIL;
        }
        
        va_status = vaRenderPicture(parent->va_display, parent->va_context, 
                &seq_para_buf_id, 1);
        if (va_status != VA_STATUS_SUCCESS)	 
        {
            LOG_E( 
                    "Failed to vaRenderPicture\n");		
            LOG_I( "va_status = %d\n", va_status);			
            return MIX_RESULT_FAIL;
        }	
    }
    else
    {
        LOG_E( 
                "not MPEG4 video encode Object\n");		
        return MIX_RESULT_FAIL;		
    }		
    
    LOG_V( "end\n");		
    
    return MIX_RESULT_SUCCESS;
    
    
}

MIX_RESULT mix_videofmtenc_mpeg4_send_picture_parameter (MixVideoFormatEnc_MPEG4 *mix)
{
    VAStatus va_status;
    VAEncPictureParameterBufferMPEG4 mpeg4_pic_param;
    MixVideoFormatEnc *parent = NULL;
    
    if (mix == NULL)
        return MIX_RESULT_NULL_PTR;
    
    LOG_V( "Begin\n\n");		
    
#if 0 //not needed currently
    MixVideoConfigParamsEncMPEG4 * params_mpeg4
        = MIX_VIDEOCONFIGPARAMSENC_MPEG4 (config_params_enc);
#endif	
    
    if (MIX_IS_VIDEOFORMATENC_MPEG4(mix)) {
        
        parent = MIX_VIDEOFORMATENC(&(mix->parent));
        
        /*set picture params for HW*/
        mpeg4_pic_param.reference_picture = mix->ref_fame->frame_id;  
        mpeg4_pic_param.reconstructed_picture = mix->rec_fame->frame_id;
        mpeg4_pic_param.coded_buf = mix->coded_buf;
        mpeg4_pic_param.picture_width = parent->picture_width;
        mpeg4_pic_param.picture_height = parent->picture_height;
        mpeg4_pic_param.vop_time_increment= mix->encoded_frames;	
        mpeg4_pic_param.picture_type = mix->is_intra ? VAEncPictureTypeIntra : VAEncPictureTypePredictive;	
		
        

        LOG_V( 
                "======mpeg4 picture params======\n");		
        LOG_I( "reference_picture = 0x%08x\n", 
                mpeg4_pic_param.reference_picture);	
        LOG_I( "reconstructed_picture = 0x%08x\n", 
                mpeg4_pic_param.reconstructed_picture);	
        LOG_I( "coded_buf = 0x%08x\n", 
                mpeg4_pic_param.coded_buf);	
        LOG_I( "picture_width = %d\n", 
                mpeg4_pic_param.picture_width);	
        LOG_I( "picture_height = %d\n", 
                mpeg4_pic_param.picture_height);		
        LOG_I( "vop_time_increment = %d\n", 
                mpeg4_pic_param.vop_time_increment);	
        LOG_I( "picture_type = %d\n\n", 
                mpeg4_pic_param.picture_type);			
       
        va_status = vaCreateBuffer(parent->va_display, parent->va_context,
                VAEncPictureParameterBufferType,
                sizeof(mpeg4_pic_param),
                1,&mpeg4_pic_param,
                &mix->pic_param_buf);	
        
        if (va_status != VA_STATUS_SUCCESS)	 
        {
            LOG_E( 
                    "Failed to vaCreateBuffer\n");												
            return MIX_RESULT_FAIL;
        }
        
        
        va_status = vaRenderPicture(parent->va_display, parent->va_context,
                &mix->pic_param_buf, 1);	
        
        if (va_status != VA_STATUS_SUCCESS)	 
        {
            LOG_E( 
                    "Failed to vaRenderPicture\n");		
            LOG_I( "va_status = %d\n", va_status);			
            return MIX_RESULT_FAIL;
        }			
    }
    else
    {
        LOG_E( 
                "not MPEG4 video encode Object\n");						
        return MIX_RESULT_FAIL;		
    }		
    
    LOG_V( "end\n");		
    return MIX_RESULT_SUCCESS;  
    
}


MIX_RESULT mix_videofmtenc_mpeg4_send_slice_parameter (MixVideoFormatEnc_MPEG4 *mix)
{
    VAStatus va_status;

    guint slice_height;
    guint slice_index;
    guint slice_height_in_mb;
    
    if (mix == NULL)
        return MIX_RESULT_NULL_PTR;
    
    LOG_V( "Begin\n\n");			
    
    
    MixVideoFormatEnc *parent = NULL;	
    
    if (MIX_IS_VIDEOFORMATENC_MPEG4(mix))
    {
        parent = MIX_VIDEOFORMATENC(&(mix->parent));		
        
        slice_height = parent->picture_height;	
        
        slice_height += 15;
        slice_height &= (~15);

        VAEncSliceParameterBuffer slice_param;
        slice_index = 0;
        slice_height_in_mb = slice_height / 16;
        slice_param.start_row_number = 0;  
        slice_param.slice_height = slice_height / 16;   
        slice_param.slice_flags.bits.is_intra = mix->is_intra;	
        slice_param.slice_flags.bits.disable_deblocking_filter_idc
            = mix->disable_deblocking_filter_idc;

            LOG_V( 
                    "======mpeg4 slice params======\n");		

            LOG_I( "start_row_number = %d\n", 
                    (gint) slice_param.start_row_number);	
            LOG_I( "slice_height_in_mb = %d\n", 
                    (gint) slice_param.slice_height);		
            LOG_I( "slice.is_intra = %d\n", 
                    (gint) slice_param.slice_flags.bits.is_intra);		
            LOG_I( 
                    "disable_deblocking_filter_idc = %d\n\n", 
                    (gint) mix->disable_deblocking_filter_idc);			

        va_status = vaCreateBuffer (parent->va_display, parent->va_context, 
                VAEncSliceParameterBufferType,
                sizeof(VAEncSliceParameterBuffer),
                1, &slice_param,
                &mix->slice_param_buf);		
        
        if (va_status != VA_STATUS_SUCCESS)	 
        {
            LOG_E( 
                    "Failed to vaCreateBuffer\n");	
            return MIX_RESULT_FAIL;
        }			
        
        va_status = vaRenderPicture(parent->va_display, parent->va_context,
                &mix->slice_param_buf, 1);	
        
        if (va_status != VA_STATUS_SUCCESS)	 
        {
            LOG_E( 
                    "Failed to vaRenderPicture\n");				
            return MIX_RESULT_FAIL;
        }	
        
    }
    else
    {
        LOG_E( 
                "not MPEG4 video encode Object\n");	
        return MIX_RESULT_FAIL;		
    }		
    
    LOG_V( "end\n");		
    
    return MIX_RESULT_SUCCESS;
}

MIX_RESULT mix_videofmtenc_mpeg4_process_encode (MixVideoFormatEnc_MPEG4 *mix,
        MixBuffer * bufin, MixIOVec * iovout)
{
    
    MIX_RESULT ret = MIX_RESULT_SUCCESS;
    VAStatus va_status = VA_STATUS_SUCCESS;
    VADisplay va_display = NULL;	
    VAContextID va_context;
    gulong surface = 0;
    guint16 width, height;
    
    MixVideoFrame *  tmp_fame;
    guint8 *buf;
    
    if ((mix == NULL) || (bufin == NULL) || (iovout == NULL)) {
        LOG_E( 
                "mix == NUL) || bufin == NULL || iovout == NULL\n");
        return MIX_RESULT_NULL_PTR;
    }    

    LOG_V( "Begin\n");		
    
    if (MIX_IS_VIDEOFORMATENC_MPEG4(mix))
    {
        
        MixVideoFormatEnc *parent = MIX_VIDEOFORMATENC(&(mix->parent));
        
        va_display = parent->va_display;
        va_context = parent->va_context;
        width = parent->picture_width;
        height = parent->picture_height;		
        

        LOG_I( "encoded_frames = %d\n", 
                mix->encoded_frames);	
        LOG_I( "is_intra = %d\n", 
                mix->is_intra);	
        LOG_I( "ci_frame_id = 0x%08x\n", 
                (guint) parent->ci_frame_id);
		
        /* determine the picture type*/
        if ((mix->encoded_frames % parent->intra_period) == 0) {
            mix->is_intra = TRUE;
        } else {
            mix->is_intra = FALSE;
        }		

        LOG_I( "is_intra_picture = %d\n", 
                mix->is_intra);			
        
        LOG_V( 
                "Get Surface from the pool\n");		
        
        /*current we use one surface for source data, 
         * one for reference and one for reconstructed*/
        /*TODO, could be refine here*/

        if (!parent->share_buf_mode) {
            LOG_V( 
                    "We are NOT in share buffer mode\n");		
            
            if (mix->ref_fame == NULL)
            {
                ret = mix_surfacepool_get(parent->surfacepool, &mix->ref_fame);
                if (ret != MIX_RESULT_SUCCESS)  //#ifdef SLEEP_SURFACE not used
                {
                    LOG_E( 
                            "Failed to mix_surfacepool_get\n");	
                    return MIX_RESULT_FAIL;
                }
            }
            
            if (mix->rec_fame == NULL)	
            {
                ret = mix_surfacepool_get(parent->surfacepool, &mix->rec_fame);
                if (ret != MIX_RESULT_SUCCESS)
                {
                    LOG_E( 
                            "Failed to mix_surfacepool_get\n");					
                    return MIX_RESULT_FAIL;
                }
            }

            if (parent->need_display) {
                mix->cur_fame = NULL;				
            }
			
            if (mix->cur_fame == NULL)
            {
                ret = mix_surfacepool_get(parent->surfacepool, &mix->cur_fame);
                if (ret != MIX_RESULT_SUCCESS)
                {
                    LOG_E( 
                            "Failed to mix_surfacepool_get\n");					
                    return MIX_RESULT_FAIL;
                }			
            }
            
            LOG_V( "Get Surface Done\n");		

            
            VAImage src_image;
            guint8 *pvbuf;
            guint8 *dst_y;
            guint8 *dst_uv;	
            int i,j;
            
            LOG_V( 
                    "map source data to surface\n");	
            
            ret = mix_videoframe_get_frame_id(mix->cur_fame, &surface);
            if (ret != MIX_RESULT_SUCCESS)
            {
                LOG_E( 
                        "Failed to mix_videoframe_get_frame_id\n");				
                return MIX_RESULT_FAIL;
            }
            
            
            LOG_I( 
                    "surface id = 0x%08x\n", (guint) surface);
            
            va_status = vaDeriveImage(va_display, surface, &src_image);	 
            //need to destroy
            
            if (va_status != VA_STATUS_SUCCESS)	 
            {
                LOG_E( 
                        "Failed to vaDeriveImage\n");			
                return MIX_RESULT_FAIL;
            }
            
            VAImage *image = &src_image;
            
            LOG_V( "vaDeriveImage Done\n");			
    
            
            va_status = vaMapBuffer (va_display, image->buf, (void **)&pvbuf);
            if (va_status != VA_STATUS_SUCCESS)	 
            {
                LOG_E( "Failed to vaMapBuffer\n");
                return MIX_RESULT_FAIL;
            }		
            
            LOG_V( 
                    "vaImage information\n");	
            LOG_I( 
                    "image->pitches[0] = %d\n", image->pitches[0]);
            LOG_I( 
                    "image->pitches[1] = %d\n", image->pitches[1]);		
            LOG_I( 
                    "image->offsets[0] = %d\n", image->offsets[0]);
            LOG_I( 
                    "image->offsets[1] = %d\n", image->offsets[1]);	
            LOG_I( 
                    "image->num_planes = %d\n", image->num_planes);			
            LOG_I( 
                    "image->width = %d\n", image->width);			
            LOG_I( 
                    "image->height = %d\n", image->height);			
            
            LOG_I( 
                    "input buf size = %d\n", bufin->size);			
            
            guint8 *inbuf = bufin->data;      
            
            /*need to convert YUV420 to NV12*/
            dst_y = pvbuf +image->offsets[0];
            
            for (i = 0; i < height; i ++) {
                memcpy (dst_y, inbuf + i * width, width);
                dst_y += image->pitches[0];
            }
            
            dst_uv = pvbuf + image->offsets[1];
            
            for (i = 0; i < height / 2; i ++) {
                for (j = 0; j < width; j+=2) {
                    dst_uv [j] = inbuf [width * height + i * width / 2 + j / 2];
                    dst_uv [j + 1] = 
                        inbuf [width * height * 5 / 4 + i * width / 2 + j / 2];
                }
                dst_uv += image->pitches[1];
            }
            
            vaUnmapBuffer(va_display, image->buf);	
            if (va_status != VA_STATUS_SUCCESS)	 
            {
                LOG_E( 
                        "Failed to vaUnmapBuffer\n");	
                return MIX_RESULT_FAIL;
            }	
            
            va_status = vaDestroyImage(va_display, src_image.image_id);
            if (va_status != VA_STATUS_SUCCESS)	 
            {
                LOG_E( 
                        "Failed to vaDestroyImage\n");					
                return MIX_RESULT_FAIL;
            }	
            
            LOG_V( 
                    "Map source data to surface done\n");	
            
        }
        
        else {//if (!parent->share_buf_mode)
                   
            MixVideoFrame * frame = mix_videoframe_new();
            
            if (mix->ref_fame == NULL)
            {
                ret = mix_videoframe_set_ci_frame_idx (frame, mix->surface_num - 1);
                
                ret = mix_surfacepool_get_frame_with_ci_frameidx 
                    (parent->surfacepool, &mix->ref_fame, frame);
                if (ret != MIX_RESULT_SUCCESS)  //#ifdef SLEEP_SURFACE not used
                {
                    LOG_E( 
                            "get reference surface from pool failed\n");				
                    return MIX_RESULT_FAIL;
                }
            }
            
            if (mix->rec_fame == NULL)	
            {
                ret = mix_videoframe_set_ci_frame_idx (frame, mix->surface_num - 2);        
                
                ret = mix_surfacepool_get_frame_with_ci_frameidx
                    (parent->surfacepool, &mix->rec_fame, frame);

                if (ret != MIX_RESULT_SUCCESS)
                {
                    LOG_E( 
                            "get recontructed surface from pool failed\n");				
                    return MIX_RESULT_FAIL;
                }
            }

            if (parent->need_display) {
                mix->cur_fame = NULL;		
            }			
            
            if (mix->cur_fame == NULL)
            {
                guint ci_idx;
                memcpy (&ci_idx, bufin->data, bufin->size);
                
                LOG_I( 
                        "surface_num = %d\n", mix->surface_num);			 
                LOG_I( 
                        "ci_frame_idx = %d\n", ci_idx);					
                
                if (ci_idx > mix->surface_num - 2) {
                    LOG_E( 
                            "the CI frame idx is too bigger than CI frame number\n");				
                    return MIX_RESULT_FAIL;			
                }
                
                
                ret = mix_videoframe_set_ci_frame_idx (frame, ci_idx);        
                
                ret = mix_surfacepool_get_frame_with_ci_frameidx
                    (parent->surfacepool, &mix->cur_fame, frame);

                if (ret != MIX_RESULT_SUCCESS)
                {
                    LOG_E( 
                            "get current working surface from pool failed\n");
                    return MIX_RESULT_FAIL;
                }			
            }
            
            ret = mix_videoframe_get_frame_id(mix->cur_fame, &surface);
            
        }
        
        LOG_V( "vaBeginPicture\n");	
        LOG_I( "va_context = 0x%08x\n",(guint)va_context);
        LOG_I( "surface = 0x%08x\n",(guint)surface);	        
        LOG_I( "va_display = 0x%08x\n",(guint)va_display);
 
        va_status = vaBeginPicture(va_display, va_context, surface);
        if (va_status != VA_STATUS_SUCCESS)	 
        {
            LOG_E( "Failed vaBeginPicture\n");
            return MIX_RESULT_FAIL;
        }	

        LOG_V( "mix_videofmtenc_mpeg4_send_seq_params\n");	
		
        if (mix->encoded_frames == 0) {
            mix_videofmtenc_mpeg4_send_seq_params (mix);
            if (ret != MIX_RESULT_SUCCESS)
            {
                LOG_E( 
                        "Failed mix_videofmtenc_mpeg4_send_seq_params\n");
                return MIX_RESULT_FAIL;
            }
        }
        
        ret = mix_videofmtenc_mpeg4_send_picture_parameter (mix);	
        
        if (ret != MIX_RESULT_SUCCESS)
        {
           LOG_E( 
                   "Failed mix_videofmtenc_mpeg4_send_picture_parameter\n");	
           return MIX_RESULT_FAIL;
        }
        
        ret = mix_videofmtenc_mpeg4_send_slice_parameter (mix);
        if (ret != MIX_RESULT_SUCCESS)
        {            
            LOG_E( 
                    "Failed mix_videofmtenc_mpeg4_send_slice_parameter\n");	
            return MIX_RESULT_FAIL;
        }		
        
        LOG_V( "before vaEndPicture\n");	

        va_status = vaEndPicture (va_display, va_context);
        if (va_status != VA_STATUS_SUCCESS)	 
        {
            LOG_E( "Failed vaEndPicture\n");		
            return MIX_RESULT_FAIL;
        }				
    
        
        LOG_V( "vaSyncSurface\n");	
        
        va_status = vaSyncSurface(va_display, surface);
        if (va_status != VA_STATUS_SUCCESS)	 
        {
            LOG_E( "Failed vaSyncSurface\n");		
            return MIX_RESULT_FAIL;
        }				
        

        LOG_V( 
                "Start to get encoded data\n");		
        
        /*get encoded data from the VA buffer*/
        va_status = vaMapBuffer (va_display, mix->coded_buf, (void **)&buf);
        if (va_status != VA_STATUS_SUCCESS)	 
        {
            LOG_E( "Failed vaMapBuffer\n");	
            return MIX_RESULT_FAIL;
        }			

        // first 4 bytes is the size of the buffer
		memcpy (&(iovout->data_size), (void*)buf, 4); 
        //size = (guint*) buf;
        
        if (iovout->data == NULL) { //means app doesn't allocate the buffer, so _encode will allocate it.
        
            iovout->data = g_malloc (iovout->data_size);
            if (iovout->data == NULL) {
                return MIX_RESULT_NO_MEMORY;
            }		
        }
        
        memcpy (iovout->data, buf + 16, iovout->data_size);

        iovout->buffer_size = iovout->data_size;
        
        LOG_I( 
                "out size is = %d\n", iovout->data_size);	
        
        va_status = vaUnmapBuffer (va_display, mix->coded_buf);
        if (va_status != VA_STATUS_SUCCESS)	 
        {
            LOG_E( "Failed vaUnmapBuffer\n");				
            return MIX_RESULT_FAIL;
        }		
	
        LOG_V( "get encoded data done\n");		

#if 0
        if (parent->drawable) {
            va_status = vaPutSurface(va_display, surface, (Drawable)parent->drawable,
                    0,0, width, height,
                    0,0, width, height,
                    NULL,0,0);
        } 
		
#ifdef SHOW_SRC
        else {

            va_status = vaPutSurface(va_display, surface, win,
                    0,0, width, height,
                    0,0, width, height,
                    NULL,0,0);		
        }
#endif //SHOW_SRC
#endif

        VASurfaceStatus status;
        
        /*query the status of current surface*/
        va_status = vaQuerySurfaceStatus(va_display, surface,  &status);
        if (va_status != VA_STATUS_SUCCESS)	 
        {
            LOG_E( 
                    "Failed vaQuerySurfaceStatus\n");				
            return MIX_RESULT_FAIL;
        }				
        mix->pic_skipped = status & VASurfaceSkipped;		

	//ret = mix_framemanager_enqueue(parent->framemgr, mix->rec_fame);	

       if (parent->need_display) {
	ret = mix_framemanager_enqueue(parent->framemgr, mix->cur_fame);	
        if (ret != MIX_RESULT_SUCCESS)
        {            
            LOG_E( 
                    "Failed mix_framemanager_enqueue\n");	
            return MIX_RESULT_FAIL;
        }		
        }	
		
        
        /*update the reference surface and reconstructed surface */
        if (!mix->pic_skipped) {
            tmp_fame = mix->rec_fame;
            mix->rec_fame= mix->ref_fame;
            mix->ref_fame = tmp_fame;
        } 			
        
        
#if 0
        if (mix->ref_fame != NULL)
            mix_videoframe_unref (mix->ref_fame);
        mix->ref_fame = mix->rec_fame;
        
        mix_videoframe_unref (mix->cur_fame);
#endif		

        if (!(parent->need_display)) {
            mix_videoframe_unref (mix->cur_fame);
            mix->cur_fame = NULL;
        }
        
        mix->encoded_frames ++;
    }
    else
    {
        LOG_E( 
                "not MPEG4 video encode Object\n");	
        return MIX_RESULT_FAIL;		
    }
    
    
    LOG_V( "end\n");		
 
    return MIX_RESULT_SUCCESS;
}

MIX_RESULT mix_videofmtenc_mpeg4_get_max_encoded_buf_size (
        MixVideoFormatEnc *mix, guint * max_size)
{

    MixVideoFormatEnc *parent = NULL;
	
    if (mix == NULL)
    {
        LOG_E( 
                "mix == NULL\n");
            return MIX_RESULT_NULL_PTR;
    }
    
    LOG_V( "Begin\n");		
	
    parent = MIX_VIDEOFORMATENC(mix);
    MixVideoFormatEnc_MPEG4 *self = MIX_VIDEOFORMATENC_MPEG4 (mix);			

    if (MIX_IS_VIDEOFORMATENC_MPEG4(self)) {

        if (self->coded_buf_size > 0) {
            *max_size = self->coded_buf_size;
            LOG_V ("Already calculate the max encoded size, get the value directly");
            return MIX_RESULT_SUCCESS;  			
        }
                
        /*base on the rate control mode to calculate the defaule encoded buffer size*/
        if (self->va_rcmode == VA_RC_NONE) {
            self->coded_buf_size = 
                (parent->picture_width* parent->picture_height * 400) / (16 * 16);  
            // set to value according to QP
        }
        else {	
            self->coded_buf_size = parent->bitrate/ 4; 
        }
        
        self->coded_buf_size = 
            max (self->coded_buf_size , 
                    (parent->picture_width* parent->picture_height * 400) / (16 * 16));
        
        /*in case got a very large user input bit rate value*/
        self->coded_buf_size = 
            max(self->coded_buf_size, 
                    (parent->picture_width * parent->picture_height * 1.5 * 8));
        self->coded_buf_size =  (self->coded_buf_size + 15) &(~15);
    }
    else
    {
        LOG_E( 
                "not MPEG4 video encode Object\n");				
        return MIX_RESULT_FAIL;		
    }

    *max_size = self->coded_buf_size;
	
    LOG_V( "end\n");		
	
    return MIX_RESULT_SUCCESS;    
}
