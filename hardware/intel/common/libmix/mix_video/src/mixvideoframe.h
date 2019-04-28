/*
 INTEL CONFIDENTIAL
 Copyright 2009 Intel Corporation All Rights Reserved.
 The source code contained or described herein and all documents related to the source code ("Material") are owned by Intel Corporation or its suppliers or licensors. Title to the Material remains with Intel Corporation or its suppliers and licensors. The Material contains trade secrets and proprietary and confidential information of Intel or its suppliers and licensors. The Material is protected by worldwide copyright and trade secret laws and treaty provisions. No part of the Material may be used, copied, reproduced, modified, published, uploaded, posted, transmitted, distributed, or disclosed in any way without Intel’s prior express written permission.

 No license under any patent, copyright, trade secret or other intellectual property right is granted to or conferred upon you by disclosure or delivery of the Materials, either expressly, by implication, inducement, estoppel or otherwise. Any license under such intellectual property rights must be express and approved by Intel in writing.
 */

#ifndef __MIX_VIDEOFRAME_H__
#define __MIX_VIDEOFRAME_H__

#include <mixparams.h>
#include "mixvideodef.h"

/**
 * MIX_TYPE_VIDEOFRAME:
 *
 * Get type of class.
 */
#define MIX_TYPE_VIDEOFRAME (mix_videoframe_get_type ())

/**
 * MIX_VIDEOFRAME:
 * @obj: object to be type-casted.
 */
#define MIX_VIDEOFRAME(obj) (G_TYPE_CHECK_INSTANCE_CAST ((obj), MIX_TYPE_VIDEOFRAME, MixVideoFrame))

/**
 * MIX_IS_VIDEOFRAME:
 * @obj: an object.
 *
 * Checks if the given object is an instance of #MixVideoFrame
 */
#define MIX_IS_VIDEOFRAME(obj) (G_TYPE_CHECK_INSTANCE_TYPE ((obj), MIX_TYPE_VIDEOFRAME))

/**
 * MIX_VIDEOFRAME_CLASS:
 * @klass: class to be type-casted.
 */
#define MIX_VIDEOFRAME_CLASS(klass) (G_TYPE_CHECK_CLASS_CAST ((klass), MIX_TYPE_VIDEOFRAME, MixVideoFrameClass))

/**
 * MIX_IS_VIDEOFRAME_CLASS:
 * @klass: a class.
 *
 * Checks if the given class is #MixVideoFrameClass
 */
#define MIX_IS_VIDEOFRAME_CLASS(klass) (G_TYPE_CHECK_CLASS_TYPE ((klass), MIX_TYPE_VIDEOFRAME))

/**
 * MIX_VIDEOFRAME_GET_CLASS:
 * @obj: a #MixVideoFrame object.
 *
 * Get the class instance of the object.
 */
#define MIX_VIDEOFRAME_GET_CLASS(obj) (G_TYPE_INSTANCE_GET_CLASS ((obj), MIX_TYPE_VIDEOFRAME, MixVideoFrameClass))

typedef struct _MixVideoFrame MixVideoFrame;
typedef struct _MixVideoFrameClass MixVideoFrameClass;

/**
 * MixVideoFrame:
 *
 * MI-X VideoConfig Parameter object
 */
struct _MixVideoFrame {
	/*< public > */
	MixParams parent;

	/*< public > */
	gulong frame_id;
	guint ci_frame_idx;	
	guint64 timestamp;
	gboolean discontinuity;
	guint32 frame_structure; // 0: frame, 1: top field, 2: bottom field

	void *reserved1;
	void *reserved2;
	void *reserved3;
	void *reserved4;
};

/**
 * MixVideoFrameClass:
 *
 * MI-X VideoConfig object class
 */
struct _MixVideoFrameClass {
	/*< public > */
	MixParamsClass parent_class;

	/* class members */
};

/**
 * mix_videoframe_get_type:
 * @returns: type
 *
 * Get the type of object.
 */
GType mix_videoframe_get_type(void);

/**
 * mix_videoframe_new:
 * @returns: A newly allocated instance of #MixVideoFrame
 *
 * Use this method to create new instance of #MixVideoFrame
 */
MixVideoFrame *mix_videoframe_new(void);
/**
 * mix_videoframe_ref:
 * @mix: object to add reference
 * @returns: the MixVideoFrame instance where reference count has been increased.
 *
 * Add reference count.
 */
MixVideoFrame *mix_videoframe_ref(MixVideoFrame * obj);

/**
 * mix_videoframe_unref:
 * @obj: object to unref.
 *
 * Decrement reference count of the object.
 */
void mix_videoframe_unref(MixVideoFrame * obj);

/* Class Methods */

MIX_RESULT mix_videoframe_set_frame_id(MixVideoFrame * obj, gulong frame_id);
MIX_RESULT mix_videoframe_get_frame_id(MixVideoFrame * obj, gulong * frame_id);

MIX_RESULT mix_videoframe_set_ci_frame_idx(MixVideoFrame * obj, guint ci_frame_idx);
MIX_RESULT mix_videoframe_get_ci_frame_idx(MixVideoFrame * obj, guint * ci_frame_idx);

MIX_RESULT mix_videoframe_set_timestamp(MixVideoFrame * obj, guint64 timestamp);
MIX_RESULT mix_videoframe_get_timestamp(MixVideoFrame * obj, guint64 * timestamp);

MIX_RESULT mix_videoframe_set_discontinuity(MixVideoFrame * obj, gboolean discontinuity);
MIX_RESULT mix_videoframe_get_discontinuity(MixVideoFrame * obj, gboolean * discontinuity);

MIX_RESULT mix_videoframe_set_frame_structure(MixVideoFrame * obj, guint32 frame_structure);
MIX_RESULT mix_videoframe_get_frame_structure(MixVideoFrame * obj, guint32* frame_structure);

#endif /* __MIX_VIDEOFRAME_H__ */
