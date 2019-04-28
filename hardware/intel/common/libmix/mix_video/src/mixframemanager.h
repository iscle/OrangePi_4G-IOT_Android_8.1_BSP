/*
 INTEL CONFIDENTIAL
 Copyright 2009 Intel Corporation All Rights Reserved.
 The source code contained or described herein and all documents related to the source code ("Material") are owned by Intel Corporation or its suppliers or licensors. Title to the Material remains with Intel Corporation or its suppliers and licensors. The Material contains trade secrets and proprietary and confidential information of Intel or its suppliers and licensors. The Material is protected by worldwide copyright and trade secret laws and treaty provisions. No part of the Material may be used, copied, reproduced, modified, published, uploaded, posted, transmitted, distributed, or disclosed in any way without Intel’s prior express written permission.

 No license under any patent, copyright, trade secret or other intellectual property right is granted to or conferred upon you by disclosure or delivery of the Materials, either expressly, by implication, inducement, estoppel or otherwise. Any license under such intellectual property rights must be express and approved by Intel in writing.
 */

#ifndef __MIX_FRAMEMANAGER_H__
#define __MIX_FRAMEMANAGER_H__

#include <glib-object.h>
#include "mixvideodef.h"
#include "mixvideoframe.h"

/*
 * Type macros.
 */
#define MIX_TYPE_FRAMEMANAGER                  (mix_framemanager_get_type ())
#define MIX_FRAMEMANAGER(obj)                  (G_TYPE_CHECK_INSTANCE_CAST ((obj), MIX_TYPE_FRAMEMANAGER, MixFrameManager))
#define MIX_IS_FRAMEMANAGER(obj)               (G_TYPE_CHECK_INSTANCE_TYPE ((obj), MIX_TYPE_FRAMEMANAGER))
#define MIX_FRAMEMANAGER_CLASS(klass)          (G_TYPE_CHECK_CLASS_CAST ((klass), MIX_TYPE_FRAMEMANAGER, MixFrameManagerClass))
#define MIX_IS_FRAMEMANAGER_CLASS(klass)       (G_TYPE_CHECK_CLASS_TYPE ((klass), MIX_TYPE_FRAMEMANAGER))
#define MIX_FRAMEMANAGER_GET_CLASS(obj)        (G_TYPE_INSTANCE_GET_CLASS ((obj), MIX_TYPE_FRAMEMANAGER, MixFrameManagerClass))

typedef struct _MixFrameManager MixFrameManager;
typedef struct _MixFrameManagerClass MixFrameManagerClass;

struct _MixFrameManager {
	/*< public > */
	GObject parent;

	/*< public > */

	/*< private > */
	gboolean initialized;
	gboolean flushing;
	gboolean eos;

	GMutex *lock;
	GPtrArray *frame_array;
	GQueue *frame_queue;

	gint framerate_numerator;
	gint framerate_denominator;
	guint64 frame_timestamp_delta;

	MixFrameOrderMode mode;

	gboolean is_first_frame;
	guint64 next_frame_timestamp;

	/*
	 * For VC-1 in ASF.
	 */

	MixVideoFrame *p_frame;
	guint64 prev_timestamp;

	gboolean timebased_ordering;
};

/**
 * MixFrameManagerClass:
 *
 * MI-X Video object class
 */
struct _MixFrameManagerClass {
	/*< public > */
	GObjectClass parent_class;

/* class members */

/*< public > */
};

/**
 * mix_framemanager_get_type:
 * @returns: type
 *
 * Get the type of object.
 */
GType mix_framemanager_get_type(void);

/**
 * mix_framemanager_new:
 * @returns: A newly allocated instance of #MixFrameManager
 *
 * Use this method to create new instance of #MixFrameManager
 */
MixFrameManager *mix_framemanager_new(void);

/**
 * mix_framemanager_ref:
 * @mix: object to add reference
 * @returns: the MixFrameManager instance where reference count has been increased.
 *
 * Add reference count.
 */
MixFrameManager *mix_framemanager_ref(MixFrameManager * mix);

/**
 * mix_framemanager_unref:
 * @obj: object to unref.
 *
 * Decrement reference count of the object.
 */
#define mix_framemanager_unref(obj) g_object_unref (G_OBJECT(obj))

/* Class Methods */

/*
 * Initialize FM
 */
MIX_RESULT mix_framemanager_initialize(MixFrameManager *fm,
		MixFrameOrderMode mode, gint framerate_numerator,
		gint framerate_denominator, gboolean timebased_ordering);
/*
 * Deinitialize FM
 */
MIX_RESULT mix_framemanager_deinitialize(MixFrameManager *fm);

/*
 * Set new framerate
 */
MIX_RESULT mix_framemanager_set_framerate(MixFrameManager *fm,
						gint framerate_numerator, gint framerate_denominator);

/*
 * Get framerate
 */
MIX_RESULT mix_framemanager_get_framerate(MixFrameManager *fm,
						gint *framerate_numerator, gint *framerate_denominator);


/*
 * Get Frame Order Mode
 */
MIX_RESULT mix_framemanager_get_frame_order_mode(MixFrameManager *fm,
													MixFrameOrderMode *mode);

/*
 * For discontiunity, reset FM
 */
MIX_RESULT mix_framemanager_flush(MixFrameManager *fm);

/*
 * Enqueue MixVideoFrame
 */
MIX_RESULT mix_framemanager_enqueue(MixFrameManager *fm, MixVideoFrame *mvf);

/*
 * Dequeue MixVideoFrame in proper order depends on MixFrameOrderMode value
 * during initialization.
 */
MIX_RESULT mix_framemanager_dequeue(MixFrameManager *fm, MixVideoFrame **mvf);

/*
 * End of stream.
 */
MIX_RESULT mix_framemanager_eos(MixFrameManager *fm);


#endif /* __MIX_FRAMEMANAGER_H__ */
