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

#include "mixvideoformatenc_preview.h"
#include "mixvideoconfigparamsenc_preview.h"

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

static void mix_videoformatenc_preview_finalize(GObject * obj);

/*
 * Please note that the type we pass to G_DEFINE_TYPE is MIX_TYPE_VIDEOFORMATENC
 */
G_DEFINE_TYPE (MixVideoFormatEnc_Preview, mix_videoformatenc_preview, MIX_TYPE_VIDEOFORMATENC);

static void mix_videoformatenc_preview_init(MixVideoFormatEnc_Preview * self) {
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

static void mix_videoformatenc_preview_class_init(
        MixVideoFormatEnc_PreviewClass * klass) {

    /* root class */
    GObjectClass *gobject_class = (GObjectClass *) klass;

    /* direct parent class */
    MixVideoFormatEncClass *video_formatenc_class = 
        MIX_VIDEOFORMATENC_CLASS(klass);

    /* parent class for later use */
    parent_class = g_type_class_peek_parent(klass);

    /* setup finializer */
    gobject_class->finalize = mix_videoformatenc_preview_finalize;

    /* setup vmethods with base implementation */
    /* TODO: decide if we need to override the parent's methods */
    video_formatenc_class->getcaps = mix_videofmtenc_preview_getcaps;
    video_formatenc_class->initialize = mix_videofmtenc_preview_initialize;
    video_formatenc_class->encode = mix_videofmtenc_preview_encode;
    video_formatenc_class->flush = mix_videofmtenc_preview_flush;
    video_formatenc_class->eos = mix_videofmtenc_preview_eos;
    video_formatenc_class->deinitialize = mix_videofmtenc_preview_deinitialize;
}

MixVideoFormatEnc_Preview *
mix_videoformatenc_preview_new(void) {
    MixVideoFormatEnc_Preview *ret =
        g_object_new(MIX_TYPE_VIDEOFORMATENC_PREVIEW, NULL);

    return ret;
}

void mix_videoformatenc_preview_finalize(GObject * obj) {
    /* clean up here. */

    /*MixVideoFormatEnc_Preview *mix = MIX_VIDEOFORMATENC_PREVIEW(obj); */
    GObjectClass *root_class = (GObjectClass *) parent_class;

    LOG_V( "\n");

    /* Chain up parent */
    if (root_class->finalize) {
        root_class->finalize(obj);
    }
}

MixVideoFormatEnc_Preview *
mix_videoformatenc_preview_ref(MixVideoFormatEnc_Preview * mix) {
    return (MixVideoFormatEnc_Preview *) g_object_ref(G_OBJECT(mix));
}

/*Preview vmethods implementation */
MIX_RESULT mix_videofmtenc_preview_getcaps(MixVideoFormatEnc *mix, GString *msg) {

    /* TODO: add codes for Preview format */

    /* TODO: decide if we need to chainup parent method.
     * if we do, the following is the code:
     */

    LOG_V( "mix_videofmtenc_preview_getcaps\n");

    if (mix == NULL) {
        LOG_E( "mix == NULL\n");				
        return MIX_RESULT_NULL_PTR;	
    }
	

    if (parent_class->getcaps) {
        return parent_class->getcaps(mix, msg);
    }
    return MIX_RESULT_SUCCESS;
}

MIX_RESULT mix_videofmtenc_preview_initialize(MixVideoFormatEnc *mix, 
        MixVideoConfigParamsEnc * config_params_enc,
        MixFrameManager * frame_mgr,
        MixBufferPool * input_buf_pool,
        MixSurfacePool ** surface_pool,
        VADisplay va_display ) {

    MIX_RESULT ret = MIX_RESULT_SUCCESS;
    MixVideoFormatEnc *parent = NULL;
    MixVideoConfigParamsEncPreview * config_params_enc_preview;
    
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

    if (MIX_IS_VIDEOFORMATENC_PREVIEW(mix))
    {
        parent = MIX_VIDEOFORMATENC(&(mix->parent));
        MixVideoFormatEnc_Preview *self = MIX_VIDEOFORMATENC_PREVIEW(mix);
        
        if (MIX_IS_VIDEOCONFIGPARAMSENC_PREVIEW (config_params_enc)) {
            config_params_enc_preview = 
                MIX_VIDEOCONFIGPARAMSENC_PREVIEW (config_params_enc);
        } else {
            LOG_V( 
                    "mix_videofmtenc_preview_initialize:  no preview config params found\n");
            return MIX_RESULT_FAIL;
        }
        
        g_mutex_lock(parent->objectlock);        

      
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
                0, self->surfaces, parent->ci_frame_num + numSurfaces,
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

        self->coded_buf_size = 4;
		
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
                "not Preview video encode Object\n");			
        return MIX_RESULT_FAIL;
        
    }

    LOG_V( "end\n");		
    
    return MIX_RESULT_SUCCESS;
}

MIX_RESULT mix_videofmtenc_preview_encode(MixVideoFormatEnc *mix, MixBuffer * bufin[],
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
    
    if (MIX_IS_VIDEOFORMATENC_PREVIEW(mix))
    {
        
        parent = MIX_VIDEOFORMATENC(&(mix->parent));
        MixVideoFormatEnc_Preview *self = MIX_VIDEOFORMATENC_PREVIEW (mix);
        
        LOG_V( "Locking\n");		
        g_mutex_lock(parent->objectlock);
        
        
        //TODO: also we could move some encode Preparation work to here
    
        LOG_V( 
                "mix_videofmtenc_preview_process_encode\n");		        

        ret = mix_videofmtenc_preview_process_encode (self, 
                bufin[0], iovout[0]);
        if (ret != MIX_RESULT_SUCCESS)
        {
            LOG_E( 
                    "Failed mix_videofmtenc_preview_process_encode\n");		
            return MIX_RESULT_FAIL;
        }
        
        
        LOG_V( "UnLocking\n");		
		
        g_mutex_unlock(parent->objectlock);
    }    
    else
    {
        LOG_E( 
                "not Preview video encode Object\n");			
        return MIX_RESULT_FAIL;   
    }

    LOG_V( "end\n");		
    
    return MIX_RESULT_SUCCESS;
}

MIX_RESULT mix_videofmtenc_preview_flush(MixVideoFormatEnc *mix) {
    
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
    
    MixVideoFormatEnc_Preview *self = MIX_VIDEOFORMATENC_PREVIEW(mix);
    
    g_mutex_lock(mix->objectlock);

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
    
    /*reset the properities*/    
    self->encoded_frames = 0;
    self->pic_skipped = FALSE;
    self->is_intra = TRUE;
    
    g_mutex_unlock(mix->objectlock);
    
    LOG_V( "end\n");		
    
    return MIX_RESULT_SUCCESS;
}

MIX_RESULT mix_videofmtenc_preview_eos(MixVideoFormatEnc *mix) {

    /* TODO: add codes for preview */

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

MIX_RESULT mix_videofmtenc_preview_deinitialize(MixVideoFormatEnc *mix) {
    
    MixVideoFormatEnc *parent = NULL;
    VAStatus va_status;
	    
    LOG_V( "Begin\n");		

    if (mix == NULL) {
        LOG_E( "mix == NULL\n");				
        return MIX_RESULT_NULL_PTR;	
    }	

    parent = MIX_VIDEOFORMATENC(&(mix->parent));
    MixVideoFormatEnc_Preview *self = MIX_VIDEOFORMATENC_PREVIEW(mix);	
   
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


MIX_RESULT mix_videofmtenc_preview_process_encode (MixVideoFormatEnc_Preview *mix,
        MixBuffer * bufin, MixIOVec * iovout)
{
    
    MIX_RESULT ret = MIX_RESULT_SUCCESS;
    VAStatus va_status = VA_STATUS_SUCCESS;
    VADisplay va_display = NULL;	
    VAContextID va_context;
    gulong surface = 0;
    guint16 width, height;
    
    //MixVideoFrame *  tmp_fame;
    //guint8 *buf;
    
    if ((mix == NULL) || (bufin == NULL) || (iovout == NULL)) {
        LOG_E( 
                "mix == NUL) || bufin == NULL || iovout == NULL\n");
        return MIX_RESULT_NULL_PTR;
    }    

    LOG_V( "Begin\n");		
    
    if (MIX_IS_VIDEOFORMATENC_PREVIEW(mix))
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

            //mix_videoframe_unref (mix->cur_fame);

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
	
        iovout->data_size = 4;
        iovout->data = g_malloc (iovout->data_size);
        if (iovout->data == NULL) {
            return MIX_RESULT_NO_MEMORY;
        }		
        
        memset (iovout->data, 0, iovout->data_size);

        iovout->buffer_size = iovout->data_size;
        	

        if (parent->need_display) {
            ret = mix_framemanager_enqueue(parent->framemgr, mix->cur_fame);	
            if (ret != MIX_RESULT_SUCCESS)
            {            
                LOG_E( 
                        "Failed mix_framemanager_enqueue\n");	
                return MIX_RESULT_FAIL;
            }		
        }


        if (!(parent->need_display)) {
            mix_videoframe_unref (mix->cur_fame);
            mix->cur_fame = NULL;
        }
        
        mix->encoded_frames ++;
    }
    else
    {
        LOG_E( 
                "not Preview video encode Object\n");	
        return MIX_RESULT_FAIL;		
    }
    
    
    LOG_V( "end\n");		
 
    return MIX_RESULT_SUCCESS;
}
