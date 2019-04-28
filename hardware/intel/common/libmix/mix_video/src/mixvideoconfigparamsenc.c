/*
 INTEL CONFIDENTIAL
 Copyright 2009 Intel Corporation All Rights Reserved.
 The source code contained or described herein and all documents related to the source code ("Material") are owned by Intel Corporation or its suppliers or licensors. Title to the Material remains with Intel Corporation or its suppliers and licensors. The Material contains trade secrets and proprietary and confidential information of Intel or its suppliers and licensors. The Material is protected by worldwide copyright and trade secret laws and treaty provisions. No part of the Material may be used, copied, reproduced, modified, published, uploaded, posted, transmitted, distributed, or disclosed in any way without Intel’s prior express written permission.

 No license under any patent, copyright, trade secret or other intellectual property right is granted to or conferred upon you by disclosure or delivery of the Materials, either expressly, by implication, inducement, estoppel or otherwise. Any license under such intellectual property rights must be express and approved by Intel in writing.
 */

/**
 * SECTION:mixvideoconfigparamsenc
 * @short_description: VideoConfig parameters
 *
 * A data object which stores videoconfig specific parameters.
 */

#include <string.h>
#include "mixvideolog.h"
#include "mixvideoconfigparamsenc.h"

static GType _mix_videoconfigparamsenc_type = 0;
static MixParamsClass *parent_class = NULL;

#define MDEBUG

#define _do_init { _mix_videoconfigparamsenc_type = g_define_type_id; }

gboolean mix_videoconfigparamsenc_copy(MixParams * target, const MixParams * src);
MixParams *mix_videoconfigparamsenc_dup(const MixParams * obj);
gboolean mix_videoconfigparamsenc_equal(MixParams * first, MixParams * second);
static void mix_videoconfigparamsenc_finalize(MixParams * obj);

G_DEFINE_TYPE_WITH_CODE (MixVideoConfigParamsEnc, mix_videoconfigparamsenc,
		MIX_TYPE_VIDEOCONFIGPARAMS, _do_init);

static void mix_videoconfigparamsenc_init(MixVideoConfigParamsEnc * self) {
    /* initialize properties here */	
	self->bitrate = 0;
	self->frame_rate_num = 30;
	self->frame_rate_denom = 1;	
	self->initial_qp = 15;
	self->min_qp = 0;

	self->picture_width = 0;
	self->picture_height = 0;

	self->mime_type = NULL;
	self->encode_format = 0;
	self->intra_period = 30;

	self->mixbuffer_pool_size = 0;

	self->share_buf_mode = FALSE;

	self->ci_frame_id = NULL;
	self->ci_frame_num = 0;
	
	self->need_display = TRUE;	

	self->rate_control = MIX_RATE_CONTROL_NONE;
	self->raw_format = MIX_RAW_TARGET_FORMAT_YUV420;
	self->profile = MIX_PROFILE_H264BASELINE;	

	/* TODO: initialize other properties */
	self->reserved1 = NULL;
	self->reserved2 = NULL;
	self->reserved3 = NULL;
	self->reserved4 = NULL;
}

static void mix_videoconfigparamsenc_class_init(MixVideoConfigParamsEncClass * klass) {
    MixParamsClass *mixparams_class = MIX_PARAMS_CLASS(klass);
    
    /* setup static parent class */
    parent_class = (MixParamsClass *) g_type_class_peek_parent(klass);
    
    mixparams_class->finalize = mix_videoconfigparamsenc_finalize;
    mixparams_class->copy = (MixParamsCopyFunction) mix_videoconfigparamsenc_copy;
    mixparams_class->dup = (MixParamsDupFunction) mix_videoconfigparamsenc_dup;
    mixparams_class->equal
        = (MixParamsEqualFunction) mix_videoconfigparamsenc_equal;
}

MixVideoConfigParamsEnc *
mix_videoconfigparamsenc_new(void) {
    MixVideoConfigParamsEnc *ret =
        (MixVideoConfigParamsEnc *) g_type_create_instance(
                MIX_TYPE_VIDEOCONFIGPARAMSENC);
    
    return ret;
}

void mix_videoconfigparamsenc_finalize(MixParams * obj) {

	/* clean up here. */
	MixVideoConfigParamsEnc *self = MIX_VIDEOCONFIGPARAMSENC(obj);

	/* free mime_type */
	if (self->mime_type->str)
		g_string_free(self->mime_type, TRUE);
	else
		g_string_free(self->mime_type, FALSE);

	if (self->ci_frame_id)
		g_free (self->ci_frame_id);

	/* Chain up parent */
	if (parent_class->finalize) {
		parent_class->finalize(obj);
	}
}

MixVideoConfigParamsEnc *
mix_videoconfigparamsenc_ref(MixVideoConfigParamsEnc * mix) {
    return (MixVideoConfigParamsEnc *) mix_params_ref(MIX_PARAMS(mix));
}

/**
 * mix_videoconfigparamsenc_dup:
 * @obj: a #MixVideoConfigParamsEnc object
 * @returns: a newly allocated duplicate of the object.
 *
 * Copy duplicate of the object.
 */
MixParams *
mix_videoconfigparamsenc_dup(const MixParams * obj) {
    MixParams *ret = NULL;
    
    LOG_V( "Begin\n");	
    
    if (MIX_IS_VIDEOCONFIGPARAMSENC(obj)) {
        MixVideoConfigParamsEnc *duplicate = mix_videoconfigparamsenc_new();
        if (mix_videoconfigparamsenc_copy(MIX_PARAMS(duplicate), MIX_PARAMS(obj))) {

			ret = MIX_PARAMS(duplicate);
		} else {
			mix_videoconfigparamsenc_unref(duplicate);
		}
	}
	return ret;
}

/**
 * mix_videoconfigparamsenc_copy:
 * @target: copy to target
 * @src: copy from src
 * @returns: boolean indicates if copy is successful.
 *
 * Copy instance data from @src to @target.
 */
gboolean mix_videoconfigparamsenc_copy(MixParams * target, const MixParams * src) {

	MixVideoConfigParamsEnc *this_target, *this_src;
	MIX_RESULT mix_result = MIX_RESULT_FAIL;

    LOG_V( "Begin\n");	

	if (MIX_IS_VIDEOCONFIGPARAMSENC(target) && MIX_IS_VIDEOCONFIGPARAMSENC(src)) {

		/* Cast the base object to this child object */
		this_target = MIX_VIDEOCONFIGPARAMSENC(target);
		this_src = MIX_VIDEOCONFIGPARAMSENC(src);

		/* copy properties of primitive type */

		this_target->bitrate   = this_src->bitrate;
		this_target->frame_rate_num = this_src->frame_rate_num;
		this_target->frame_rate_denom = this_src->frame_rate_denom;		
		this_target->initial_qp = this_src->initial_qp;
		this_target->min_qp = this_src->min_qp;
		this_target->intra_period    = this_src->intra_period;
		this_target->picture_width    = this_src->picture_width;		
		this_target->picture_height   = this_src->picture_height;
		this_target->mixbuffer_pool_size = this_src->mixbuffer_pool_size;
		this_target->share_buf_mode = this_src->share_buf_mode;
		this_target->encode_format = this_src->encode_format;		
		this_target->ci_frame_num = this_src->ci_frame_num;		
		this_target->draw= this_src->draw;		
		this_target->need_display = this_src->need_display;
	       this_target->rate_control = this_src->rate_control;
	       this_target->raw_format = this_src->raw_format;
	       this_target->profile = this_src->profile;		
		
		/* copy properties of non-primitive */

		/* copy mime_type */

		if (this_src->mime_type) {
#ifdef MDEBUG
            if (this_src->mime_type->str) {
                
                LOG_I( "this_src->mime_type->str = %s  %x\n", 
                        this_src->mime_type->str, (unsigned int)this_src->mime_type->str);	
            }
#endif

            mix_result = mix_videoconfigparamsenc_set_mime_type(this_target,
                    this_src->mime_type->str);
        } else {
            
            LOG_I( "this_src->mime_type = NULL\n");
            
            mix_result = mix_videoconfigparamsenc_set_mime_type(this_target, NULL);
        }
        
        if (mix_result != MIX_RESULT_SUCCESS) {
            
            LOG_E( "Failed to mix_videoconfigparamsenc_set_mime_type\n");	
            return FALSE;
        }	
        
        mix_result = mix_videoconfigparamsenc_set_ci_frame_info (this_target, this_src->ci_frame_id,
                this_src->ci_frame_num);
        
        /* TODO: copy other properties if there's any */
        
		/* Now chainup base class */
		if (parent_class->copy) {
            return parent_class->copy(MIX_PARAMS_CAST(target), MIX_PARAMS_CAST(
                        src));
        } else {
            return TRUE;
        }
    }
    
    return FALSE;
}


/**
 * mix_videoconfigparamsenc_:
 * @first: first object to compare
 * @second: seond object to compare
 * @returns: boolean indicates if instance are equal.
 *
 * Copy instance data from @src to @target.
 */
gboolean mix_videoconfigparamsenc_equal(MixParams * first, MixParams * second) {

	gboolean ret = FALSE;

	MixVideoConfigParamsEnc *this_first, *this_second;

	if (MIX_IS_VIDEOCONFIGPARAMSENC(first) && MIX_IS_VIDEOCONFIGPARAMSENC(second)) {

		// Deep compare
		// Cast the base object to this child object
		this_first = MIX_VIDEOCONFIGPARAMSENC(first);
		this_second = MIX_VIDEOCONFIGPARAMSENC(second);

		/* check the equalitiy of the primitive type properties */
		if (this_first->bitrate != this_second->bitrate) {
			goto not_equal;
		}

		if (this_first->frame_rate_num != this_second->frame_rate_num) {
			goto not_equal;
		}

		if (this_first->frame_rate_denom != this_second->frame_rate_denom) {
			goto not_equal;
		}

		if (this_first->initial_qp != this_second->initial_qp) {
			goto not_equal;
		}

		if (this_first->min_qp != this_second->min_qp) {
			goto not_equal;
		}
		
		if (this_first->intra_period != this_second->intra_period) {
			goto not_equal;
		}		

		if (this_first->picture_width != this_second->picture_width
				&& this_first->picture_height != this_second->picture_height) {
			goto not_equal;
		}

		if (this_first->encode_format != this_second->encode_format) {
			goto not_equal;
		}

		if (this_first->mixbuffer_pool_size != this_second->mixbuffer_pool_size) {
			goto not_equal;
		}	

		if (this_first->share_buf_mode != this_second->share_buf_mode) {
			goto not_equal;
		}		

		if (this_first->ci_frame_id != this_second->ci_frame_id) {
			goto not_equal;
		}

		if (this_first->ci_frame_num != this_second->ci_frame_num) {
			goto not_equal;
		}		

		if (this_first->draw != this_second->draw) {
			goto not_equal;
		}	

		if (this_first->need_display!= this_second->need_display) {
			goto not_equal;
		}		

	      if (this_first->rate_control != this_second->rate_control) {
		  	goto not_equal;
		}	  

	      if (this_first->raw_format != this_second->raw_format) {
		  	goto not_equal;
		}	  

	      if (this_first->profile != this_second->profile) {
		  	goto not_equal;
		}	  	

		/* check the equalitiy of the none-primitive type properties */

		/* compare mime_type */

		if (this_first->mime_type && this_second->mime_type) {
			if (g_string_equal(this_first->mime_type, this_second->mime_type)
					!= TRUE) {
				goto not_equal;
			}
		} else if (!(!this_first->mime_type && !this_second->mime_type)) {
			goto not_equal;
		}	

		ret = TRUE;

		not_equal:

		if (ret != TRUE) {
			return ret;
		}

		/* chaining up. */
		MixParamsClass *klass = MIX_PARAMS_CLASS(parent_class);
		if (klass->equal)
			ret = parent_class->equal(first, second);
		else
			ret = TRUE;
	}

	return ret;
}

#define MIX_VIDEOCONFIGPARAMSENC_SETTER_CHECK_INPUT(obj) \
	if(!obj) return MIX_RESULT_NULL_PTR; \
	if(!MIX_IS_VIDEOCONFIGPARAMSENC(obj)) return MIX_RESULT_FAIL; \

#define MIX_VIDEOCONFIGPARAMSENC_GETTER_CHECK_INPUT(obj, prop) \
	if(!obj || !prop) return MIX_RESULT_NULL_PTR; \
	if(!MIX_IS_VIDEOCONFIGPARAMSENC(obj)) return MIX_RESULT_FAIL; \

#define MIX_VIDEOCONFIGPARAMSENC_GETTER_CHECK_INPUT_PAIR(obj, prop, prop2) \
	if(!obj || !prop || !prop2 ) return MIX_RESULT_NULL_PTR; \
	if(!MIX_IS_VIDEOCONFIGPARAMSENC(obj)) return MIX_RESULT_FAIL; \

/* TODO: Add getters and setters for other properties. The following is incomplete */


MIX_RESULT mix_videoconfigparamsenc_set_mime_type(MixVideoConfigParamsEnc * obj,
		const gchar * mime_type) {

	MIX_VIDEOCONFIGPARAMSENC_SETTER_CHECK_INPUT (obj);

	if (!mime_type) {
		return MIX_RESULT_NULL_PTR;
	}

	LOG_I( "mime_type = %s  %x\n", 
		mime_type, (unsigned int)mime_type);

	if (obj->mime_type) {
		if (obj->mime_type->str)
			g_string_free(obj->mime_type, TRUE);
		else
			g_string_free(obj->mime_type, FALSE);
	}


	LOG_I( "mime_type = %s  %x\n", 
		mime_type, (unsigned int)mime_type);
	
	obj->mime_type = g_string_new(mime_type);
	if (!obj->mime_type) {
		return MIX_RESULT_NO_MEMORY;
	}


	LOG_I( "mime_type = %s obj->mime_type->str = %s\n",
			mime_type, obj->mime_type->str);

	return MIX_RESULT_SUCCESS;
}

MIX_RESULT mix_videoconfigparamsenc_get_mime_type(MixVideoConfigParamsEnc * obj,
		gchar ** mime_type) {
	MIX_VIDEOCONFIGPARAMSENC_GETTER_CHECK_INPUT (obj, mime_type);

	if (!obj->mime_type) {
		*mime_type = NULL;
		return MIX_RESULT_SUCCESS;
	}
	*mime_type = g_strdup(obj->mime_type->str);
	if (!*mime_type) {
		return MIX_RESULT_NO_MEMORY;
	}

	return MIX_RESULT_SUCCESS;
}

MIX_RESULT mix_videoconfigparamsenc_set_frame_rate(MixVideoConfigParamsEnc * obj,
		guint frame_rate_num, guint frame_rate_denom) {
	MIX_VIDEOCONFIGPARAMSENC_SETTER_CHECK_INPUT (obj);
	obj->frame_rate_num = frame_rate_num;
	obj->frame_rate_denom = frame_rate_denom;
	return MIX_RESULT_SUCCESS;
}

MIX_RESULT mix_videoconfigparamsenc_get_frame_rate(MixVideoConfigParamsEnc * obj,
		guint * frame_rate_num, guint * frame_rate_denom) {
	MIX_VIDEOCONFIGPARAMSENC_GETTER_CHECK_INPUT_PAIR (obj, frame_rate_num, frame_rate_denom);
	*frame_rate_num = obj->frame_rate_num;
	*frame_rate_denom = obj->frame_rate_denom;
	return MIX_RESULT_SUCCESS;
}

MIX_RESULT mix_videoconfigparamsenc_set_picture_res(MixVideoConfigParamsEnc * obj,
		guint picture_width, guint picture_height) {
	MIX_VIDEOCONFIGPARAMSENC_SETTER_CHECK_INPUT (obj);
	obj->picture_width = picture_width;
	obj->picture_height = picture_height;
	return MIX_RESULT_SUCCESS;
}

MIX_RESULT mix_videoconfigparamsenc_get_picture_res(MixVideoConfigParamsEnc * obj,
        guint * picture_width, guint * picture_height) {
	MIX_VIDEOCONFIGPARAMSENC_GETTER_CHECK_INPUT_PAIR (obj, picture_width, picture_height);
	*picture_width = obj->picture_width;
	*picture_height = obj->picture_height;
	return MIX_RESULT_SUCCESS;
}

MIX_RESULT mix_videoconfigparamsenc_set_encode_format(MixVideoConfigParamsEnc * obj,
		MixEncodeTargetFormat encode_format) {
	MIX_VIDEOCONFIGPARAMSENC_SETTER_CHECK_INPUT (obj);
	obj->encode_format = encode_format;
	return MIX_RESULT_SUCCESS;
}

MIX_RESULT mix_videoconfigparamsenc_get_encode_format (MixVideoConfigParamsEnc * obj,
		MixEncodeTargetFormat* encode_format) {
	MIX_VIDEOCONFIGPARAMSENC_GETTER_CHECK_INPUT (obj, encode_format);
       *encode_format = obj->encode_format;
	return MIX_RESULT_SUCCESS;
}

MIX_RESULT mix_videoconfigparamsenc_set_bit_rate (MixVideoConfigParamsEnc * obj,
        guint bitrate) {
	MIX_VIDEOCONFIGPARAMSENC_SETTER_CHECK_INPUT (obj);
	obj->bitrate= bitrate;
	return MIX_RESULT_SUCCESS;

}              

MIX_RESULT mix_videoconfigparamsenc_get_bit_rate (MixVideoConfigParamsEnc * obj,
        guint *bitrate) {
	MIX_VIDEOCONFIGPARAMSENC_GETTER_CHECK_INPUT (obj, bitrate);
	*bitrate = obj->bitrate;
	return MIX_RESULT_SUCCESS;              
}

MIX_RESULT mix_videoconfigparamsenc_set_init_qp (MixVideoConfigParamsEnc * obj,
        guint initial_qp) {
	MIX_VIDEOCONFIGPARAMSENC_SETTER_CHECK_INPUT (obj);
	obj->initial_qp = initial_qp;
	return MIX_RESULT_SUCCESS;
}              

MIX_RESULT mix_videoconfigparamsenc_get_init_qp (MixVideoConfigParamsEnc * obj,
        guint *initial_qp) {
	MIX_VIDEOCONFIGPARAMSENC_GETTER_CHECK_INPUT (obj, initial_qp);
	*initial_qp = obj->initial_qp;
	return MIX_RESULT_SUCCESS;
             
}              

MIX_RESULT mix_videoconfigparamsenc_set_min_qp (MixVideoConfigParamsEnc * obj,
        guint min_qp) {
	MIX_VIDEOCONFIGPARAMSENC_SETTER_CHECK_INPUT (obj);
	obj->min_qp = min_qp;	
	return MIX_RESULT_SUCCESS;
}

MIX_RESULT mix_videoconfigparamsenc_get_min_qp(MixVideoConfigParamsEnc * obj,
        guint *min_qp) {
    MIX_VIDEOCONFIGPARAMSENC_GETTER_CHECK_INPUT (obj, min_qp);
    *min_qp = obj->min_qp;
    
    return MIX_RESULT_SUCCESS;
}

MIX_RESULT mix_videoconfigparamsenc_set_intra_period (MixVideoConfigParamsEnc * obj,
        guint intra_period) {
	MIX_VIDEOCONFIGPARAMSENC_SETTER_CHECK_INPUT (obj);
	obj->intra_period = intra_period;	
	
	return MIX_RESULT_SUCCESS;
}

MIX_RESULT mix_videoconfigparamsenc_get_intra_period (MixVideoConfigParamsEnc * obj,
        guint *intra_period) {
	MIX_VIDEOCONFIGPARAMSENC_GETTER_CHECK_INPUT (obj, intra_period);
	*intra_period = obj->intra_period;
	
	return MIX_RESULT_SUCCESS;
}

MIX_RESULT mix_videoconfigparamsenc_set_buffer_pool_size(
		MixVideoConfigParamsEnc * obj, guint bufpoolsize) {

	MIX_VIDEOCONFIGPARAMSENC_SETTER_CHECK_INPUT (obj);

	obj->mixbuffer_pool_size = bufpoolsize;
	return MIX_RESULT_SUCCESS;

}

MIX_RESULT mix_videoconfigparamsenc_get_buffer_pool_size(
		MixVideoConfigParamsEnc * obj, guint *bufpoolsize) {

	MIX_VIDEOCONFIGPARAMSENC_GETTER_CHECK_INPUT (obj, bufpoolsize);
	*bufpoolsize = obj->mixbuffer_pool_size;
	return MIX_RESULT_SUCCESS;
}

MIX_RESULT mix_videoconfigparamsenc_set_share_buf_mode (
	MixVideoConfigParamsEnc * obj, gboolean share_buf_mod) {
	MIX_VIDEOCONFIGPARAMSENC_SETTER_CHECK_INPUT (obj);

	obj->share_buf_mode = share_buf_mod;
	return MIX_RESULT_SUCCESS;
}

MIX_RESULT mix_videoconfigparamsenc_get_share_buf_mode(MixVideoConfigParamsEnc * obj,
		gboolean *share_buf_mod) {
	MIX_VIDEOCONFIGPARAMSENC_GETTER_CHECK_INPUT (obj, share_buf_mod);

	*share_buf_mod = obj->share_buf_mode;	
	return MIX_RESULT_SUCCESS;		
}

MIX_RESULT mix_videoconfigparamsenc_set_ci_frame_info(MixVideoConfigParamsEnc * obj, 
        gulong * ci_frame_id, guint ci_frame_num) {
	MIX_VIDEOCONFIGPARAMSENC_SETTER_CHECK_INPUT (obj);
	
	
	if (!ci_frame_id || !ci_frame_num) {
		obj->ci_frame_id = NULL;
		obj->ci_frame_num = 0;
		return MIX_RESULT_SUCCESS;
	}

	if (obj->ci_frame_id)
		g_free (obj->ci_frame_id);

	guint size = ci_frame_num * sizeof (gulong);
	obj->ci_frame_num = ci_frame_num;
	
	obj->ci_frame_id = g_malloc (ci_frame_num * sizeof (gulong));
	if (!(obj->ci_frame_id)) {
		return MIX_RESULT_NO_MEMORY;
	}

	memcpy (obj->ci_frame_id, ci_frame_id, size);

	return MIX_RESULT_SUCCESS;
}

MIX_RESULT mix_videoconfigparamsenc_get_ci_frame_info (MixVideoConfigParamsEnc * obj,
        gulong * *ci_frame_id, guint *ci_frame_num) {
    MIX_VIDEOCONFIGPARAMSENC_GETTER_CHECK_INPUT_PAIR (obj, ci_frame_id, ci_frame_num);

	*ci_frame_num = obj->ci_frame_num;
	
	if (!obj->ci_frame_id) {
		*ci_frame_id = NULL;
		return MIX_RESULT_SUCCESS;
	}

	if (obj->ci_frame_num) {
		*ci_frame_id = g_malloc (obj->ci_frame_num * sizeof (gulong));
		
		if (!*ci_frame_id) {
			return MIX_RESULT_NO_MEMORY;
		}		
		
		memcpy (*ci_frame_id, obj->ci_frame_id, obj->ci_frame_num * sizeof (gulong));
		
	} else {
		*ci_frame_id = NULL;
	}
	
	return MIX_RESULT_SUCCESS;		
}


MIX_RESULT mix_videoconfigparamsenc_set_drawable (MixVideoConfigParamsEnc * obj, 
        gulong draw) {
		
	MIX_VIDEOCONFIGPARAMSENC_SETTER_CHECK_INPUT (obj);
	obj->draw = draw;
	return MIX_RESULT_SUCCESS;
		
}

MIX_RESULT mix_videoconfigparamsenc_get_drawable (MixVideoConfigParamsEnc * obj,
        gulong *draw) {

	MIX_VIDEOCONFIGPARAMSENC_GETTER_CHECK_INPUT (obj, draw);
	*draw = obj->draw;	
	return MIX_RESULT_SUCCESS;		
}

MIX_RESULT mix_videoconfigparamsenc_set_need_display (
	MixVideoConfigParamsEnc * obj, gboolean need_display) {
	MIX_VIDEOCONFIGPARAMSENC_SETTER_CHECK_INPUT (obj);

	obj->need_display = need_display;
	return MIX_RESULT_SUCCESS;
}

MIX_RESULT mix_videoconfigparamsenc_get_need_display(MixVideoConfigParamsEnc * obj,
		gboolean *need_display) {
	MIX_VIDEOCONFIGPARAMSENC_GETTER_CHECK_INPUT (obj, need_display);

	*need_display = obj->need_display;	
	return MIX_RESULT_SUCCESS;		
}

MIX_RESULT mix_videoconfigparamsenc_set_rate_control(MixVideoConfigParamsEnc * obj,
		MixRateControl rate_control) {
	MIX_VIDEOCONFIGPARAMSENC_SETTER_CHECK_INPUT (obj);
	obj->rate_control = rate_control;
	return MIX_RESULT_SUCCESS;
}

MIX_RESULT mix_videoconfigparamsenc_get_rate_control(MixVideoConfigParamsEnc * obj,
		MixRateControl * rate_control) {
	MIX_VIDEOCONFIGPARAMSENC_GETTER_CHECK_INPUT (obj, rate_control);
	*rate_control = obj->rate_control;
	return MIX_RESULT_SUCCESS;
}	

MIX_RESULT mix_videoconfigparamsenc_set_raw_format (MixVideoConfigParamsEnc * obj,
		MixRawTargetFormat raw_format) {
	MIX_VIDEOCONFIGPARAMSENC_SETTER_CHECK_INPUT (obj);
	obj->raw_format = raw_format;
	return MIX_RESULT_SUCCESS;		
}

MIX_RESULT mix_videoconfigparamsenc_get_raw_format (MixVideoConfigParamsEnc * obj,
		MixRawTargetFormat * raw_format) {
	MIX_VIDEOCONFIGPARAMSENC_GETTER_CHECK_INPUT (obj, raw_format);
	*raw_format = obj->raw_format;
	return MIX_RESULT_SUCCESS;		
}

MIX_RESULT mix_videoconfigparamsenc_set_profile (MixVideoConfigParamsEnc * obj,
		MixProfile profile) {
	MIX_VIDEOCONFIGPARAMSENC_SETTER_CHECK_INPUT (obj);
	obj->profile = profile;
	return MIX_RESULT_SUCCESS;			
}

MIX_RESULT mix_videoconfigparamsenc_get_profile (MixVideoConfigParamsEnc * obj,
		MixProfile * profile) {
	MIX_VIDEOCONFIGPARAMSENC_GETTER_CHECK_INPUT (obj, profile);
	*profile = obj->profile;
	return MIX_RESULT_SUCCESS;			
}

