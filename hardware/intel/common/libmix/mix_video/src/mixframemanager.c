/*
 INTEL CONFIDENTIAL
 Copyright 2009 Intel Corporation All Rights Reserved.
 The source code contained or described herein and all documents related to the source code ("Material") are owned by Intel Corporation or its suppliers or licensors. Title to the Material remains with Intel Corporation or its suppliers and licensors. The Material contains trade secrets and proprietary and confidential information of Intel or its suppliers and licensors. The Material is protected by worldwide copyright and trade secret laws and treaty provisions. No part of the Material may be used, copied, reproduced, modified, published, uploaded, posted, transmitted, distributed, or disclosed in any way without Intel’s prior express written permission.

 No license under any patent, copyright, trade secret or other intellectual property right is granted to or conferred upon you by disclosure or delivery of the Materials, either expressly, by implication, inducement, estoppel or otherwise. Any license under such intellectual property rights must be express and approved by Intel in writing.
 */
#include <glib.h>

#include "mixvideolog.h"
#include "mixframemanager.h"
#include "mixvideoframe_private.h"

#define INITIAL_FRAME_ARRAY_SIZE 	16
#define MIX_SECOND  (G_USEC_PER_SEC * G_GINT64_CONSTANT (1000))

static GObjectClass *parent_class = NULL;

static void mix_framemanager_finalize(GObject * obj);
G_DEFINE_TYPE( MixFrameManager, mix_framemanager, G_TYPE_OBJECT);

static void mix_framemanager_init(MixFrameManager * self) {
	/* TODO: public member initialization */

	/* TODO: private member initialization */

	if (!g_thread_supported()) {
		g_thread_init(NULL);
	}

	self->lock = g_mutex_new();

	self->flushing = FALSE;
	self->eos = FALSE;
	self->frame_array = NULL;
	self->frame_queue = NULL;
	self->initialized = FALSE;

	self->mode = MIX_FRAMEORDER_MODE_DISPLAYORDER;
	self->framerate_numerator = 30;
	self->framerate_denominator = 1;

	self->is_first_frame = TRUE;

	/* for vc1 in asf */
	self->p_frame = NULL;
	self->prev_timestamp = 0;
}

static void mix_framemanager_class_init(MixFrameManagerClass * klass) {
	GObjectClass *gobject_class = (GObjectClass *) klass;

	/* parent class for later use */
	parent_class = g_type_class_peek_parent(klass);

	gobject_class->finalize = mix_framemanager_finalize;
}

MixFrameManager *mix_framemanager_new(void) {
	MixFrameManager *ret = g_object_new(MIX_TYPE_FRAMEMANAGER, NULL);

	return ret;
}

void mix_framemanager_finalize(GObject * obj) {
	/* clean up here. */

	MixFrameManager *fm = MIX_FRAMEMANAGER(obj);

	/* cleanup here */
	mix_framemanager_deinitialize(fm);

	if (fm->lock) {
		g_mutex_free(fm->lock);
		fm->lock = NULL;
	}

	/* Chain up parent */
	if (parent_class->finalize) {
		parent_class->finalize(obj);
	}
}

MixFrameManager *mix_framemanager_ref(MixFrameManager * fm) {
	return (MixFrameManager *) g_object_ref(G_OBJECT(fm));
}

/* MixFrameManager class methods */

MIX_RESULT mix_framemanager_initialize(MixFrameManager *fm,
		MixFrameOrderMode mode, gint framerate_numerator,
		gint framerate_denominator, gboolean timebased_ordering) {

	MIX_RESULT ret = MIX_RESULT_FAIL;

	if (!MIX_IS_FRAMEMANAGER(fm) || (mode != MIX_FRAMEORDER_MODE_DISPLAYORDER
			&& mode != MIX_FRAMEORDER_MODE_DECODEORDER) || framerate_numerator
			<= 0 || framerate_denominator <= 0) {
		return MIX_RESULT_INVALID_PARAM;
	}

	if (fm->initialized) {
		return MIX_RESULT_ALREADY_INIT;
	}

	if (!g_thread_supported()) {
		g_thread_init(NULL);
	}

	ret = MIX_RESULT_NO_MEMORY;
	if (!fm->lock) {
		fm->lock = g_mutex_new();
		if (!fm->lock) {
			goto cleanup;
		}
	}

	if (mode == MIX_FRAMEORDER_MODE_DISPLAYORDER) {
		fm->frame_array = g_ptr_array_sized_new(INITIAL_FRAME_ARRAY_SIZE);
		if (!fm->frame_array) {
			goto cleanup;
		}
	}

	fm->frame_queue = g_queue_new();
	if (!fm->frame_queue) {
		goto cleanup;
	}

	fm->framerate_numerator = framerate_numerator;
	fm->framerate_denominator = framerate_denominator;
	fm->frame_timestamp_delta = fm->framerate_denominator * MIX_SECOND
			/ fm->framerate_numerator;

	fm->mode = mode;

	fm->timebased_ordering = timebased_ordering;

	fm->initialized = TRUE;

	ret = MIX_RESULT_SUCCESS;

	cleanup:

	if (ret != MIX_RESULT_SUCCESS) {
		if (fm->frame_array) {
			g_ptr_array_free(fm->frame_array, TRUE);
			fm->frame_array = NULL;
		}
		if (fm->frame_queue) {
			g_queue_free(fm->frame_queue);
			fm->frame_queue = NULL;
		}
	}
	return ret;
}
MIX_RESULT mix_framemanager_deinitialize(MixFrameManager *fm) {

	if (!MIX_IS_FRAMEMANAGER(fm)) {
		return MIX_RESULT_INVALID_PARAM;
	}

	if (!fm->lock) {
		return MIX_RESULT_FAIL;
	}

	if (!fm->initialized) {
		return MIX_RESULT_NOT_INIT;
	}

	mix_framemanager_flush(fm);

	g_mutex_lock(fm->lock);

	if (fm->frame_array) {
		g_ptr_array_free(fm->frame_array, TRUE);
		fm->frame_array = NULL;
	}
	if (fm->frame_queue) {
		g_queue_free(fm->frame_queue);
		fm->frame_queue = NULL;
	}

	fm->initialized = FALSE;

	g_mutex_unlock(fm->lock);

	return MIX_RESULT_SUCCESS;
}

MIX_RESULT mix_framemanager_set_framerate(MixFrameManager *fm,
		gint framerate_numerator, gint framerate_denominator) {

	if (!MIX_IS_FRAMEMANAGER(fm)) {
		return MIX_RESULT_INVALID_PARAM;
	}

	if (!fm->lock) {
		return MIX_RESULT_FAIL;
	}

	if (framerate_numerator <= 0 || framerate_denominator <= 0) {
		return MIX_RESULT_INVALID_PARAM;
	}

	g_mutex_lock(fm->lock);

	fm->framerate_numerator = framerate_numerator;
	fm->framerate_denominator = framerate_denominator;
	fm->frame_timestamp_delta = fm->framerate_denominator * MIX_SECOND
			/ fm->framerate_numerator;

	g_mutex_unlock(fm->lock);

	return MIX_RESULT_SUCCESS;
}

MIX_RESULT mix_framemanager_get_framerate(MixFrameManager *fm,
		gint *framerate_numerator, gint *framerate_denominator) {

	if (!MIX_IS_FRAMEMANAGER(fm)) {
		return MIX_RESULT_INVALID_PARAM;
	}

	if (!fm->lock) {
		return MIX_RESULT_FAIL;
	}

	if (!framerate_numerator || !framerate_denominator) {
		return MIX_RESULT_INVALID_PARAM;
	}

	g_mutex_lock(fm->lock);

	*framerate_numerator = fm->framerate_numerator;
	*framerate_denominator = fm->framerate_denominator;

	g_mutex_unlock(fm->lock);

	return MIX_RESULT_SUCCESS;
}

MIX_RESULT mix_framemanager_get_frame_order_mode(MixFrameManager *fm,
		MixFrameOrderMode *mode) {

	if (!MIX_IS_FRAMEMANAGER(fm)) {
		return MIX_RESULT_INVALID_PARAM;
	}

	if (!fm->lock) {
		return MIX_RESULT_FAIL;
	}

	if (!mode) {
		return MIX_RESULT_INVALID_PARAM;
	}

	/* no need to use lock */
	*mode = fm->mode;

	return MIX_RESULT_SUCCESS;
}

MIX_RESULT mix_framemanager_flush(MixFrameManager *fm) {

	if (!MIX_IS_FRAMEMANAGER(fm)) {
		return MIX_RESULT_INVALID_PARAM;
	}

	if (!fm->initialized) {
		return MIX_RESULT_NOT_INIT;
	}

	g_mutex_lock(fm->lock);

	/* flush frame_array */
	if (fm->frame_array) {
		guint len = fm->frame_array->len;
		if (len) {
			guint idx = 0;
			MixVideoFrame *frame = NULL;
			for (idx = 0; idx < len; idx++) {
				frame = (MixVideoFrame *) g_ptr_array_index(fm->frame_array,
						idx);
				if (frame) {
					mix_videoframe_unref(frame);
					g_ptr_array_index(fm->frame_array, idx) = NULL;
				}
			}
			/* g_ptr_array_remove_range(fm->frame_array, 0, len); */
		}
	}

	if (fm->frame_queue) {
		guint len = fm->frame_queue->length;
		if (len) {
			MixVideoFrame *frame = NULL;
			while ((frame = (MixVideoFrame *) g_queue_pop_head(fm->frame_queue))) {
				mix_videoframe_unref(frame);
			}
		}
	}

	if(fm->p_frame) {
		mix_videoframe_unref(fm->p_frame);
		fm->p_frame = NULL;
	}
	fm->prev_timestamp = 0;

	fm->eos = FALSE;

	fm->is_first_frame = TRUE;

	g_mutex_unlock(fm->lock);

	return MIX_RESULT_SUCCESS;
}

MixVideoFrame *get_expected_frame_from_array(GPtrArray *array,
		guint64 expected, guint64 tolerance, guint64 *frametimestamp) {

	guint idx = 0;
	guint len = 0;
	guint64 timestamp = 0;
	guint64 lowest_timestamp = (guint64)-1;
	guint lowest_timestamp_idx = -1;
	
	MixVideoFrame *frame = NULL;

	if (!array || !expected || !tolerance || !frametimestamp || expected < tolerance) {

		return NULL;
	}

	len = array->len;
	if (!len) {
		return NULL;
	}

	for (idx = 0; idx < len; idx++) {
		MixVideoFrame *_frame = (MixVideoFrame *) g_ptr_array_index(array, idx);
		if (_frame) {

			if (mix_videoframe_get_timestamp(_frame, &timestamp)
					!= MIX_RESULT_SUCCESS) {

				/*
				 * Oops, this shall never happen!
				 * In case it heppens, release the frame!
				 */

				mix_videoframe_unref(_frame);

				/* make an available slot */
				g_ptr_array_index(array, idx) = NULL;

				break;
			}
			
			if (lowest_timestamp > timestamp)
			{
				lowest_timestamp = timestamp;
				lowest_timestamp_idx = idx;
			}
		}
	}
	
	if (lowest_timestamp == (guint64)-1)
	{
		return NULL;
	}
		

	/* check if this is the expected next frame */
	if (lowest_timestamp <= expected + tolerance)
	{
		MixVideoFrame *_frame = (MixVideoFrame *) g_ptr_array_index(array, lowest_timestamp_idx);
		/* make this slot available */
		g_ptr_array_index(array, lowest_timestamp_idx) = NULL;

		*frametimestamp = lowest_timestamp;
		frame = _frame;
	}
	
	return frame;
}

void add_frame_into_array(GPtrArray *array, MixVideoFrame *mvf) {

	gboolean found_slot = FALSE;
	guint len = 0;

	if (!array || !mvf) {
		return;
	}

	/* do we have slot for this frame? */
	len = array->len;
	if (len) {
		guint idx = 0;
		gpointer frame = NULL;
		for (idx = 0; idx < len; idx++) {
			frame = g_ptr_array_index(array, idx);
			if (!frame) {
				found_slot = TRUE;
				g_ptr_array_index(array, idx) = (gpointer) mvf;
				break;
			}
		}
	}

	if (!found_slot) {
		g_ptr_array_add(array, (gpointer) mvf);
	}

}

MIX_RESULT mix_framemanager_timestamp_based_enqueue(MixFrameManager *fm,
		MixVideoFrame *mvf) {
	/*
	 * display order mode.
	 *
	 * if this is the first frame, we always push it into
	 * output queue, if it is not, check if it is the one
	 * expected, if yes, push it into the output queue.
	 * if not, put it into waiting list.
	 *
	 * while the expected frame is pushed into output queue,
	 * the expected next timestamp is also updated. with this
	 * updated expected next timestamp, we search for expected
	 * frame from the waiting list, if found, repeat the process.
	 *
	 */

	MIX_RESULT ret = MIX_RESULT_FAIL;
	guint64 timestamp = 0;

	first_frame:

	ret = mix_videoframe_get_timestamp(mvf, &timestamp);
	if (ret != MIX_RESULT_SUCCESS) {
		goto cleanup;
	}

	if (fm->is_first_frame) {

		/*
		 * for the first frame, we can always put it into the output queue
		 */
		g_queue_push_tail(fm->frame_queue, (gpointer) mvf);

		/*
		 * what timestamp of next frame shall be?
		 */
		fm->next_frame_timestamp = timestamp + fm->frame_timestamp_delta;

		fm->is_first_frame = FALSE;

	} else {

		/*
		 * is this the next frame expected?
		 */

		/* calculate tolerance */
		guint64 tolerance = fm->frame_timestamp_delta / 4;
		MixVideoFrame *frame_from_array = NULL;
		guint64 timestamp_frame_array = 0;

		/*
		* timestamp may be associated with the second field, which
		* will not fall between the tolerance range. 
		*/

		if (timestamp <= fm->next_frame_timestamp + tolerance) {

			/*
			 * ok, this is the frame expected, push it into output queue
			 */
			g_queue_push_tail(fm->frame_queue, (gpointer) mvf);

			/*
			 * update next_frame_timestamp only if it falls within the tolerance range
			 */
			if (timestamp >= fm->next_frame_timestamp - tolerance)
			{ 
				fm->next_frame_timestamp = timestamp + fm->frame_timestamp_delta;
			}
			
			/*
			 * since we updated next_frame_timestamp, there might be a frame
			 * in the frame_array that satisfying this new next_frame_timestamp
			 */

			while ((frame_from_array = get_expected_frame_from_array(
					fm->frame_array, fm->next_frame_timestamp, tolerance,
					&timestamp_frame_array))) {

				g_queue_push_tail(fm->frame_queue, (gpointer) frame_from_array);
				
				/*
			 	* update next_frame_timestamp only if it falls within the tolerance range
			 	*/				
				if (timestamp_frame_array >= fm->next_frame_timestamp - tolerance)
				{
					fm->next_frame_timestamp = timestamp_frame_array
							+ fm->frame_timestamp_delta;
				}
			}

		} else {

			/*
			 * is discontinuity flag set for this frame ?
			 */
			gboolean discontinuity = FALSE;
			ret = mix_videoframe_get_discontinuity(mvf, &discontinuity);
			if (ret != MIX_RESULT_SUCCESS) {
				goto cleanup;
			}

			/*
			 * If this is a frame with discontinuity flag set, clear frame_array
			 * and treat the frame as the first frame.
			 */
			if (discontinuity) {

				guint len = fm->frame_array->len;
				if (len) {
					guint idx = 0;
					MixVideoFrame *frame = NULL;
					for (idx = 0; idx < len; idx++) {
						frame = (MixVideoFrame *) g_ptr_array_index(
								fm->frame_array, idx);
						if (frame) {
							mix_videoframe_unref(frame);
							g_ptr_array_index(fm->frame_array, idx) = NULL;
						}
					}
				}

				fm->is_first_frame = TRUE;
				goto first_frame;
			}

			/*
			 * handle variable frame rate:
			 * display any frame which time stamp is less than current one. 
			 * 
			 */
			guint64 tolerance = fm->frame_timestamp_delta / 4;
			MixVideoFrame *frame_from_array = NULL;
			guint64 timestamp_frame_array = 0;

			while ((frame_from_array = get_expected_frame_from_array(
					fm->frame_array, timestamp, tolerance,
					&timestamp_frame_array)))
			{
				g_queue_push_tail(fm->frame_queue, (gpointer) frame_from_array);
				
				/*
			 	* update next_frame_timestamp only if it falls within the tolerance range
			 	*/				
				if (timestamp_frame_array >= fm->next_frame_timestamp - tolerance)
				{
					fm->next_frame_timestamp = timestamp_frame_array
							+ fm->frame_timestamp_delta;
				}
			}
			/*
			 * this is not the expected frame, put it into frame_array
			 */					

			add_frame_into_array(fm->frame_array, mvf);
		}
	}
	cleanup:

	return ret;
}

MIX_RESULT mix_framemanager_frametype_based_enqueue(MixFrameManager *fm,
		MixVideoFrame *mvf) {

	MIX_RESULT ret = MIX_RESULT_FAIL;
	MixFrameType frame_type;
	guint64 timestamp = 0;

	ret = mix_videoframe_get_frame_type(mvf, &frame_type);
	if (ret != MIX_RESULT_SUCCESS) {
		goto cleanup;
	}

	ret = mix_videoframe_get_timestamp(mvf, &timestamp);
	if (ret != MIX_RESULT_SUCCESS) {
		goto cleanup;
	}

#ifdef MIX_LOG_ENABLE
	if (frame_type == TYPE_I) {
		LOG_I( "TYPE_I %"G_GINT64_FORMAT"\n", timestamp);
	} else if (frame_type == TYPE_P) {
		LOG_I( "TYPE_P %"G_GINT64_FORMAT"\n", timestamp);
	} else if (frame_type == TYPE_B) {
		LOG_I( "TYPE_B %"G_GINT64_FORMAT"\n", timestamp);
	} else {
		LOG_I( "TYPE_UNKNOWN %"G_GINT64_FORMAT"\n", timestamp);
	}
#endif

	if (fm->is_first_frame) {
		/*
		 * The first frame is not a I frame, unexpected!
		 */
		if (frame_type != TYPE_I) {
			goto cleanup;
		}

		g_queue_push_tail(fm->frame_queue, (gpointer) mvf);
		fm->is_first_frame = FALSE;
	} else {

		/*
		 * I P B B P B B ...
		 */
		if (frame_type == TYPE_I || frame_type == TYPE_P) {

			if (fm->p_frame) {

				ret = mix_videoframe_set_timestamp(fm->p_frame,
						fm->prev_timestamp);
				if (ret != MIX_RESULT_SUCCESS) {
					goto cleanup;
				}

				g_queue_push_tail(fm->frame_queue, (gpointer) fm->p_frame);
				fm->p_frame = NULL;
			}

			/* it is an I frame, push it into the out queue */
			/*if (frame_type == TYPE_I) {

			 g_queue_push_tail(fm->frame_queue, (gpointer) mvf);

			 } else*/
			{
				/* it is a P frame, we can not push it to the out queue yet, save it */
				fm->p_frame = mvf;
				fm->prev_timestamp = timestamp;
			}

			ret = MIX_RESULT_SUCCESS;

		} else {
			/* it is a B frame, replace the timestamp with the previous one */
			if (timestamp > fm->prev_timestamp) {
				ret = mix_videoframe_set_timestamp(mvf, fm->prev_timestamp);
				if (ret != MIX_RESULT_SUCCESS) {
					goto cleanup;
				}

				/* save the timestamp */
				fm->prev_timestamp = timestamp;
			}
			g_queue_push_tail(fm->frame_queue, (gpointer) mvf);
			ret = MIX_RESULT_SUCCESS;
		}
	}

	cleanup:

	return ret;
}

MIX_RESULT mix_framemanager_enqueue(MixFrameManager *fm, MixVideoFrame *mvf) {

	MIX_RESULT ret = MIX_RESULT_FAIL;

	/*fm->mode = MIX_FRAMEORDER_MODE_DECODEORDER;*/

	if (!mvf) {
		return MIX_RESULT_INVALID_PARAM;
	}

	if (!MIX_IS_FRAMEMANAGER(fm)) {
		return MIX_RESULT_INVALID_PARAM;
	}

	if (!fm->initialized) {
		return MIX_RESULT_NOT_INIT;
	}

	/*
	 * This should never happen!
	 */
	if (fm->mode != MIX_FRAMEORDER_MODE_DISPLAYORDER && fm->mode
			!= MIX_FRAMEORDER_MODE_DECODEORDER) {
		return MIX_RESULT_FAIL;
	}

	g_mutex_lock(fm->lock);

	ret = MIX_RESULT_SUCCESS;
	if (fm->mode == MIX_FRAMEORDER_MODE_DECODEORDER) {
		/*
		 * decode order mode, push the frame into output queue
		 */
		g_queue_push_tail(fm->frame_queue, (gpointer) mvf);

	} else {

		if (fm->timebased_ordering) {
			ret = mix_framemanager_timestamp_based_enqueue(fm, mvf);
		} else {
			ret = mix_framemanager_frametype_based_enqueue(fm, mvf);
		}
	}

	g_mutex_unlock(fm->lock);

	return ret;
}

MIX_RESULT mix_framemanager_dequeue(MixFrameManager *fm, MixVideoFrame **mvf) {

	MIX_RESULT ret = MIX_RESULT_FAIL;

	if (!MIX_IS_FRAMEMANAGER(fm)) {
		return MIX_RESULT_INVALID_PARAM;
	}

	if (!mvf) {
		return MIX_RESULT_INVALID_PARAM;
	}

	if (!fm->initialized) {
		return MIX_RESULT_NOT_INIT;
	}

	g_mutex_lock(fm->lock);

	ret = MIX_RESULT_FRAME_NOTAVAIL;
	*mvf = (MixVideoFrame *) g_queue_pop_head(fm->frame_queue);
	if (*mvf) {
		ret = MIX_RESULT_SUCCESS;
	} else if (fm->eos) {
		ret = MIX_RESULT_EOS;
	}

	g_mutex_unlock(fm->lock);

	return ret;
}

MIX_RESULT mix_framemanager_eos(MixFrameManager *fm) {

	MIX_RESULT ret = MIX_RESULT_FAIL;

	if (!MIX_IS_FRAMEMANAGER(fm)) {
		return MIX_RESULT_INVALID_PARAM;
	}

	if (!fm->initialized) {
		return MIX_RESULT_NOT_INIT;
	}

	g_mutex_lock(fm->lock);

	fm->eos = TRUE;

	g_mutex_unlock(fm->lock);

	return ret;
}

