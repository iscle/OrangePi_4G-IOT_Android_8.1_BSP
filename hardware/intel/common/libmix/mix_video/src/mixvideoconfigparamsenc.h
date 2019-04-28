/*
 INTEL CONFIDENTIAL
 Copyright 2009 Intel Corporation All Rights Reserved.
 The source code contained or described herein and all documents related to the source code ("Material") are owned by Intel Corporation or its suppliers or licensors. Title to the Material remains with Intel Corporation or its suppliers and licensors. The Material contains trade secrets and proprietary and confidential information of Intel or its suppliers and licensors. The Material is protected by worldwide copyright and trade secret laws and treaty provisions. No part of the Material may be used, copied, reproduced, modified, published, uploaded, posted, transmitted, distributed, or disclosed in any way without Intel’s prior express written permission.

 No license under any patent, copyright, trade secret or other intellectual property right is granted to or conferred upon you by disclosure or delivery of the Materials, either expressly, by implication, inducement, estoppel or otherwise. Any license under such intellectual property rights must be express and approved by Intel in writing.
 */

#ifndef __MIX_VIDEOCONFIGPARAMSENC_H__
#define __MIX_VIDEOCONFIGPARAMSENC_H__

#include <mixvideoconfigparams.h>
#include "mixvideodef.h"

/**
 * MIX_TYPE_VIDEOCONFIGPARAMSENC:
 *
 * Get type of class.
 */
#define MIX_TYPE_VIDEOCONFIGPARAMSENC (mix_videoconfigparamsenc_get_type ())

/**
 * MIX_VIDEOCONFIGPARAMSENC:
 * @obj: object to be type-casted.
 */
#define MIX_VIDEOCONFIGPARAMSENC(obj) (G_TYPE_CHECK_INSTANCE_CAST ((obj), MIX_TYPE_VIDEOCONFIGPARAMSENC, MixVideoConfigParamsEnc))

/**
 * MIX_IS_VIDEOCONFIGPARAMSENC:
 * @obj: an object.
 *
 * Checks if the given object is an instance of #MixParams
 */
#define MIX_IS_VIDEOCONFIGPARAMSENC(obj) (G_TYPE_CHECK_INSTANCE_TYPE ((obj), MIX_TYPE_VIDEOCONFIGPARAMSENC))

/**
 * MIX_VIDEOCONFIGPARAMSENC_CLASS:
 * @klass: class to be type-casted.
 */
#define MIX_VIDEOCONFIGPARAMSENC_CLASS(klass) (G_TYPE_CHECK_CLASS_CAST ((klass), MIX_TYPE_VIDEOCONFIGPARAMSENC, MixVideoConfigParamsEncClass))

/**
 * MIX_IS_VIDEOCONFIGPARAMSENC_CLASS:
 * @klass: a class.
 *
 * Checks if the given class is #MixParamsClass
 */
#define MIX_IS_VIDEOCONFIGPARAMSENC_CLASS(klass) (G_TYPE_CHECK_CLASS_TYPE ((klass), MIX_TYPE_VIDEOCONFIGPARAMSENC))

/**
 * MIX_VIDEOCONFIGPARAMSENC_GET_CLASS:
 * @obj: a #MixParams object.
 *
 * Get the class instance of the object.
 */
#define MIX_VIDEOCONFIGPARAMSENC_GET_CLASS(obj) (G_TYPE_INSTANCE_GET_CLASS ((obj), MIX_TYPE_VIDEOCONFIGPARAMSENC, MixVideoConfigParamsEncClass))

typedef struct _MixVideoConfigParamsEnc MixVideoConfigParamsEnc;
typedef struct _MixVideoConfigParamsEncClass MixVideoConfigParamsEncClass;

/**
 * MixVideoConfigParamsEnc:
 *
 * MI-X VideoConfig Parameter object
 */
struct _MixVideoConfigParamsEnc {
	/*< public > */
	MixVideoConfigParams parent;

	/*< public > */
	//MixIOVec header;

	/* the type of the following members will be changed after MIX API doc is ready */

       MixProfile profile;
       MixRawTargetFormat raw_format;
       MixRateControl rate_control;  	

	guint bitrate;
	guint frame_rate_num;
	guint frame_rate_denom;
	guint initial_qp;
	guint min_qp;
	guint intra_period;
	guint16 picture_width;
	guint16 picture_height;	

	GString * mime_type;
	MixEncodeTargetFormat encode_format;

	guint mixbuffer_pool_size;

	gboolean share_buf_mode;	

	gulong *	ci_frame_id;
	guint	ci_frame_num;
	
	gulong draw;
	gboolean need_display;
	
	void *reserved1;
	void *reserved2;
	void *reserved3;
	void *reserved4;
};

/**
 * MixVideoConfigParamsEncClass:
 *
 * MI-X VideoConfig object class
 */
struct _MixVideoConfigParamsEncClass {
	/*< public > */
	MixVideoConfigParamsClass parent_class;

	/* class members */
};

/**
 * mix_videoconfigparamsenc_get_type:
 * @returns: type
 *
 * Get the type of object.
 */
GType mix_videoconfigparamsenc_get_type(void);

/**
 * mix_videoconfigparamsenc_new:
 * @returns: A newly allocated instance of #MixVideoConfigParamsEnc
 *
 * Use this method to create new instance of #MixVideoConfigParamsEnc
 */
MixVideoConfigParamsEnc *mix_videoconfigparamsenc_new(void);
/**
 * mix_videoconfigparamsenc_ref:
 * @mix: object to add reference
 * @returns: the MixVideoConfigParamsEnc instance where reference count has been increased.
 *
 * Add reference count.
 */
MixVideoConfigParamsEnc *mix_videoconfigparamsenc_ref(MixVideoConfigParamsEnc * mix);

/**
 * mix_videoconfigparamsenc_unref:
 * @obj: object to unref.
 *
 * Decrement reference count of the object.
 */
#define mix_videoconfigparamsenc_unref(obj) mix_params_unref(MIX_PARAMS(obj))

/* Class Methods */


MIX_RESULT mix_videoconfigparamsenc_set_mime_type(MixVideoConfigParamsEnc * obj,
		const gchar * mime_type);

MIX_RESULT mix_videoconfigparamsenc_get_mime_type(MixVideoConfigParamsEnc * obj,
		gchar ** mime_type);

MIX_RESULT mix_videoconfigparamsenc_set_frame_rate(MixVideoConfigParamsEnc * obj,
		guint frame_rate_num, guint frame_rate_denom);

MIX_RESULT mix_videoconfigparamsenc_get_frame_rate(MixVideoConfigParamsEnc * obj,
		guint * frame_rate_num, guint * frame_rate_denom);

MIX_RESULT mix_videoconfigparamsenc_set_picture_res(MixVideoConfigParamsEnc * obj,
		guint picture_width, guint picture_height);

MIX_RESULT mix_videoconfigparamsenc_get_picture_res(MixVideoConfigParamsEnc * obj,
		guint * picture_width, guint * picture_height);

MIX_RESULT mix_videoconfigparamsenc_set_encode_format (MixVideoConfigParamsEnc * obj,
		MixEncodeTargetFormat encode_format);

MIX_RESULT mix_videoconfigparamsenc_get_encode_format (MixVideoConfigParamsEnc * obj,
		MixEncodeTargetFormat * encode_format);

MIX_RESULT mix_videoconfigparamsenc_set_bit_rate (MixVideoConfigParamsEnc * obj,
        guint bps);

MIX_RESULT mix_videoconfigparamsenc_get_bit_rate (MixVideoConfigParamsEnc * obj,
        guint *bps);

MIX_RESULT mix_videoconfigparamsenc_set_init_qp (MixVideoConfigParamsEnc * obj,
        guint initial_qp);

MIX_RESULT mix_videoconfigparamsenc_get_init_qp (MixVideoConfigParamsEnc * obj,
        guint *initial_qp);

MIX_RESULT mix_videoconfigparamsenc_set_min_qp (MixVideoConfigParamsEnc * obj,
        guint min_qp);

MIX_RESULT mix_videoconfigparamsenc_get_min_qp(MixVideoConfigParamsEnc * obj,
        guint *min_qp);

MIX_RESULT mix_videoconfigparamsenc_set_intra_period (MixVideoConfigParamsEnc * obj,
        guint intra_period);

MIX_RESULT mix_videoconfigparamsenc_get_intra_period (MixVideoConfigParamsEnc * obj,
        guint *intra_period);

MIX_RESULT mix_videoconfigparamsenc_set_buffer_pool_size(MixVideoConfigParamsEnc * obj,
		guint bufpoolsize);

MIX_RESULT mix_videoconfigparamsenc_get_buffer_pool_size(MixVideoConfigParamsEnc * obj,
		guint *bufpoolsize);

MIX_RESULT mix_videoconfigparamsenc_set_share_buf_mode (MixVideoConfigParamsEnc * obj,
		gboolean share_buf_mod);

MIX_RESULT mix_videoconfigparamsenc_get_share_buf_mode(MixVideoConfigParamsEnc * obj,
		gboolean *share_buf_mod);

MIX_RESULT mix_videoconfigparamsenc_set_ci_frame_info(MixVideoConfigParamsEnc * obj, 
		gulong *	ci_frame_id, guint  ci_frame_num);

MIX_RESULT mix_videoconfigparamsenc_get_ci_frame_info (MixVideoConfigParamsEnc * obj,
		gulong * *ci_frame_id, guint *ci_frame_num);

MIX_RESULT mix_videoconfigparamsenc_set_drawable (MixVideoConfigParamsEnc * obj, 
		gulong draw);

MIX_RESULT mix_videoconfigparamsenc_get_drawable (MixVideoConfigParamsEnc * obj,
        gulong *draw);

MIX_RESULT mix_videoconfigparamsenc_set_need_display (
        MixVideoConfigParamsEnc * obj, gboolean need_display);

MIX_RESULT mix_videoconfigparamsenc_get_need_display(MixVideoConfigParamsEnc * obj,
		gboolean *need_display);


MIX_RESULT mix_videoconfigparamsenc_set_rate_control(MixVideoConfigParamsEnc * obj,
		MixRateControl rcmode);

MIX_RESULT mix_videoconfigparamsenc_get_rate_control(MixVideoConfigParamsEnc * obj,
		MixRateControl * rcmode);

MIX_RESULT mix_videoconfigparamsenc_set_raw_format (MixVideoConfigParamsEnc * obj,
		MixRawTargetFormat raw_format);

MIX_RESULT mix_videoconfigparamsenc_get_raw_format (MixVideoConfigParamsEnc * obj,
		MixRawTargetFormat * raw_format);

MIX_RESULT mix_videoconfigparamsenc_set_profile (MixVideoConfigParamsEnc * obj,
		MixProfile profile);

MIX_RESULT mix_videoconfigparamsenc_get_profile (MixVideoConfigParamsEnc * obj,
		MixProfile * profile);

/* TODO: Add getters and setters for other properties */

#endif /* __MIX_VIDEOCONFIGPARAMSENC_H__ */

