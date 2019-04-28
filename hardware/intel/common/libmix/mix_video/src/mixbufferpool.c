/* 
 INTEL CONFIDENTIAL
 Copyright 2009 Intel Corporation All Rights Reserved.
 The source code contained or described herein and all documents related to the source code ("Material") are owned by Intel Corporation or its suppliers or licensors. Title to the Material remains with Intel Corporation or its suppliers and licensors. The Material contains trade secrets and proprietary and confidential information of Intel or its suppliers and licensors. The Material is protected by worldwide copyright and trade secret laws and treaty provisions. No part of the Material may be used, copied, reproduced, modified, published, uploaded, posted, transmitted, distributed, or disclosed in any way without Intel’s prior express written permission.

 No license under any patent, copyright, trade secret or other intellectual property right is granted to or conferred upon you by disclosure or delivery of the Materials, either expressly, by implication, inducement, estoppel or otherwise. Any license under such intellectual property rights must be express and approved by Intel in writing.
 */

/**
 * SECTION:mixbufferpool
 * @short_description: MI-X Input Buffer Pool
 *
 * A data object which stores and manipulates a pool of compressed video buffers.
 */

#include "mixvideolog.h"
#include "mixbufferpool.h"
#include "mixbuffer_private.h"

#define MIX_LOCK(lock) g_mutex_lock(lock);
#define MIX_UNLOCK(lock) g_mutex_unlock(lock);

#define SAFE_FREE(p) if(p) { g_free(p); p = NULL; }

static GType _mix_bufferpool_type = 0;
static MixParamsClass *parent_class = NULL;

#define _do_init { _mix_bufferpool_type = g_define_type_id; }

gboolean mix_bufferpool_copy(MixParams * target, const MixParams * src);
MixParams *mix_bufferpool_dup(const MixParams * obj);
gboolean mix_bufferpool_equal(MixParams * first, MixParams * second);
static void mix_bufferpool_finalize(MixParams * obj);

G_DEFINE_TYPE_WITH_CODE (MixBufferPool, mix_bufferpool, MIX_TYPE_PARAMS,
		_do_init);

static void mix_bufferpool_init(MixBufferPool * self) {
	/* initialize properties here */
	self->free_list = NULL;
	self->in_use_list = NULL;
	self->free_list_max_size = 0;
	self->high_water_mark = 0;

	self->reserved1 = NULL;
	self->reserved2 = NULL;
	self->reserved3 = NULL;
	self->reserved4 = NULL;

	// TODO: relocate this mutex allocation -we can't communicate failure in ctor.
	// Note that g_thread_init() has already been called by mix_video_init()
	self->objectlock = g_mutex_new();

}

static void mix_bufferpool_class_init(MixBufferPoolClass * klass) {
	MixParamsClass *mixparams_class = MIX_PARAMS_CLASS(klass);

	/* setup static parent class */
	parent_class = (MixParamsClass *) g_type_class_peek_parent(klass);

	mixparams_class->finalize = mix_bufferpool_finalize;
	mixparams_class->copy = (MixParamsCopyFunction) mix_bufferpool_copy;
	mixparams_class->dup = (MixParamsDupFunction) mix_bufferpool_dup;
	mixparams_class->equal = (MixParamsEqualFunction) mix_bufferpool_equal;
}

MixBufferPool *
mix_bufferpool_new(void) {
	MixBufferPool *ret = (MixBufferPool *) g_type_create_instance(
			MIX_TYPE_BUFFERPOOL);
	return ret;
}

void mix_bufferpool_finalize(MixParams * obj) {
	/* clean up here. */

	MixBufferPool *self = MIX_BUFFERPOOL(obj);

	if (self->objectlock) {
		g_mutex_free(self->objectlock);
		self->objectlock = NULL;
	}

	/* Chain up parent */
	if (parent_class->finalize) {
		parent_class->finalize(obj);
	}
}

MixBufferPool *
mix_bufferpool_ref(MixBufferPool * mix) {
	return (MixBufferPool *) mix_params_ref(MIX_PARAMS(mix));
}

/**
 * mix_bufferpool_dup:
 * @obj: a #MixBufferPool object
 * @returns: a newly allocated duplicate of the object.
 *
 * Copy duplicate of the object.
 */
MixParams *
mix_bufferpool_dup(const MixParams * obj) {
	MixParams *ret = NULL;

	if (MIX_IS_BUFFERPOOL(obj)) {

		MIX_LOCK(MIX_BUFFERPOOL(obj)->objectlock);

		MixBufferPool *duplicate = mix_bufferpool_new();
		if (mix_bufferpool_copy(MIX_PARAMS(duplicate), MIX_PARAMS(obj))) {
			ret = MIX_PARAMS(duplicate);
		} else {
			mix_bufferpool_unref(duplicate);
		}

		MIX_UNLOCK(MIX_BUFFERPOOL(obj)->objectlock);

	}
	return ret;
}

/**
 * mix_bufferpool_copy:
 * @target: copy to target
 * @src: copy from src
 * @returns: boolean indicates if copy is successful.
 *
 * Copy instance data from @src to @target.
 */
gboolean mix_bufferpool_copy(MixParams * target, const MixParams * src) {
	MixBufferPool *this_target, *this_src;

	if (MIX_IS_BUFFERPOOL(target) && MIX_IS_BUFFERPOOL(src)) {

		MIX_LOCK(MIX_BUFFERPOOL(src)->objectlock);
		MIX_LOCK(MIX_BUFFERPOOL(target)->objectlock);

		// Cast the base object to this child object
		this_target = MIX_BUFFERPOOL(target);
		this_src = MIX_BUFFERPOOL(src);

		// Free the existing properties

		// Duplicate string
		this_target->free_list = this_src->free_list;
		this_target->in_use_list = this_src->in_use_list;
		this_target->free_list_max_size = this_src->free_list_max_size;
		this_target->high_water_mark = this_src->high_water_mark;

		MIX_UNLOCK(MIX_BUFFERPOOL(src)->objectlock);
		MIX_UNLOCK(MIX_BUFFERPOOL(target)->objectlock);

		// Now chainup base class
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
 * mix_bufferpool_equal:
 * @first: first object to compare
 * @second: seond object to compare
 * @returns: boolean indicates if instance are equal.
 *
 * Copy instance data from @src to @target.
 */
gboolean mix_bufferpool_equal(MixParams * first, MixParams * second) {
	gboolean ret = FALSE;
	MixBufferPool *this_first, *this_second;

	if (MIX_IS_BUFFERPOOL(first) && MIX_IS_BUFFERPOOL(second)) {
		// Deep compare
		// Cast the base object to this child object

		MIX_LOCK(MIX_BUFFERPOOL(first)->objectlock);
		MIX_LOCK(MIX_BUFFERPOOL(second)->objectlock);

		this_first = MIX_BUFFERPOOL(first);
		this_second = MIX_BUFFERPOOL(second);

		/* TODO: add comparison for other properties */
		if (this_first->free_list == this_second->free_list
				&& this_first->in_use_list == this_second->in_use_list
				&& this_first->free_list_max_size
						== this_second->free_list_max_size
				&& this_first->high_water_mark == this_second->high_water_mark) {
			// members within this scope equal. chaining up.
			MixParamsClass *klass = MIX_PARAMS_CLASS(parent_class);
			if (klass->equal)
				ret = klass->equal(first, second);
			else
				ret = TRUE;
		}

		MIX_LOCK(MIX_BUFFERPOOL(first)->objectlock);
		MIX_LOCK(MIX_BUFFERPOOL(second)->objectlock);

	}

	return ret;
}

/*  Class Methods  */

/**
 * mix_bufferpool_initialize:
 * @returns: MIX_RESULT_SUCCESS if successful in creating the buffer pool
 *
 * Use this method to create a new buffer pool, consisting of a GSList of
 * buffer objects that represents a pool of buffers.
 */
MIX_RESULT mix_bufferpool_initialize(MixBufferPool * obj, guint num_buffers) {

	LOG_V( "Begin\n");

	if (obj == NULL)
	return MIX_RESULT_NULL_PTR;

	MIX_LOCK(obj->objectlock);

	if ((obj->free_list != NULL) || (obj->in_use_list != NULL)) {
		//buffer pool is in use; return error; need proper cleanup
		//TODO need cleanup here?

		MIX_UNLOCK(obj->objectlock);

		return MIX_RESULT_ALREADY_INIT;
	}

	if (num_buffers == 0) {
		obj->free_list = NULL;

		obj->in_use_list = NULL;

		obj->free_list_max_size = num_buffers;

		obj->high_water_mark = 0;

		MIX_UNLOCK(obj->objectlock);

		return MIX_RESULT_SUCCESS;
	}

	// Initialize the free pool with MixBuffer objects

	gint i = 0;
	MixBuffer *buffer = NULL;

	for (; i < num_buffers; i++) {

		buffer = mix_buffer_new();

		if (buffer == NULL) {
			//TODO need to log an error here and do cleanup

			MIX_UNLOCK(obj->objectlock);

			return MIX_RESULT_NO_MEMORY;
		}

		// Set the pool reference in the private data of the MixBuffer object
		mix_buffer_set_pool(buffer, obj);

		//Add each MixBuffer object to the pool list
		obj->free_list = g_slist_append(obj->free_list, buffer);

	}

	obj->in_use_list = NULL;

	obj->free_list_max_size = num_buffers;

	obj->high_water_mark = 0;

	MIX_UNLOCK(obj->objectlock);

	LOG_V( "End\n");

return MIX_RESULT_SUCCESS;
}

/**
 * mix_bufferpool_put:
 * @returns: SUCCESS or FAILURE
 *
 * Use this method to return a buffer to the free pool
 */
MIX_RESULT mix_bufferpool_put(MixBufferPool * obj, MixBuffer * buffer) {

	if (obj == NULL || buffer == NULL)
		return MIX_RESULT_NULL_PTR;

	MIX_LOCK(obj->objectlock);

	if (obj->in_use_list == NULL) {
		//in use list cannot be empty if a buffer is in use
		//TODO need better error code for this

		MIX_UNLOCK(obj->objectlock);

		return MIX_RESULT_FAIL;
	}

	GSList *element = g_slist_find(obj->in_use_list, buffer);
	if (element == NULL) {
		//Integrity error; buffer not found in in use list
		//TODO need better error code and handling for this

		MIX_UNLOCK(obj->objectlock);

		return MIX_RESULT_FAIL;
	} else {
		//Remove this element from the in_use_list
		obj->in_use_list = g_slist_remove_link(obj->in_use_list, element);

		//Concat the element to the free_list
		obj->free_list = g_slist_concat(obj->free_list, element);
	}

	//Note that we do nothing with the ref count for this.  We want it to
	//stay at 1, which is what triggered it to be added back to the free list.

	MIX_UNLOCK(obj->objectlock);

	return MIX_RESULT_SUCCESS;
}

/**
 * mix_bufferpool_get:
 * @returns: SUCCESS or FAILURE
 *
 * Use this method to get a buffer from the free pool
 */
MIX_RESULT mix_bufferpool_get(MixBufferPool * obj, MixBuffer ** buffer) {

	if (obj == NULL || buffer == NULL)
		return MIX_RESULT_NULL_PTR;

	MIX_LOCK(obj->objectlock);

	if (obj->free_list == NULL) {
		//We are out of buffers
		//TODO need to log this as well

		MIX_UNLOCK(obj->objectlock);

		return MIX_RESULT_POOLEMPTY;
	}

	//Remove a buffer from the free pool

	//We just remove the one at the head, since it's convenient
	GSList *element = obj->free_list;
	obj->free_list = g_slist_remove_link(obj->free_list, element);
	if (element == NULL) {
		//Unexpected behavior
		//TODO need better error code and handling for this

		MIX_UNLOCK(obj->objectlock);

		return MIX_RESULT_FAIL;
	} else {
		//Concat the element to the in_use_list
		obj->in_use_list = g_slist_concat(obj->in_use_list, element);

		//TODO replace with proper logging

		LOG_I( "buffer refcount%d\n",
				MIX_PARAMS(element->data)->refcount);

		//Set the out buffer pointer
		*buffer = (MixBuffer *) element->data;

		//Check the high water mark for buffer use
		guint size = g_slist_length(obj->in_use_list);
		if (size > obj->high_water_mark)
			obj->high_water_mark = size;
		//TODO Log this high water mark
	}

	//Increment the reference count for the buffer
	mix_buffer_ref(*buffer);

	MIX_UNLOCK(obj->objectlock);

	return MIX_RESULT_SUCCESS;
}

/**
 * mix_bufferpool_deinitialize:
 * @returns: SUCCESS or FAILURE
 *
 * Use this method to teardown a buffer pool
 */
MIX_RESULT mix_bufferpool_deinitialize(MixBufferPool * obj) {
	if (obj == NULL)
		return MIX_RESULT_NULL_PTR;

	MIX_LOCK(obj->objectlock);

	if ((obj->in_use_list != NULL) || (g_slist_length(obj->free_list)
			!= obj->free_list_max_size)) {
		//TODO better error code
		//We have outstanding buffer objects in use and they need to be
		//freed before we can deinitialize.

		MIX_UNLOCK(obj->objectlock);

		return MIX_RESULT_FAIL;
	}

	//Now remove buffer objects from the list

	MixBuffer *buffer = NULL;

	while (obj->free_list != NULL) {
		//Get the buffer object from the head of the list
		buffer = obj->free_list->data;
		//buffer = g_slist_nth_data(obj->free_list, 0);

		//Release it
		mix_buffer_unref(buffer);

		//Delete the head node of the list and store the new head
		obj->free_list = g_slist_delete_link(obj->free_list, obj->free_list);

		//Repeat until empty
	}

	obj->free_list_max_size = 0;

	//May want to log this information for tuning
	obj->high_water_mark = 0;

	MIX_UNLOCK(obj->objectlock);

	return MIX_RESULT_SUCCESS;
}

#define MIX_BUFFERPOOL_SETTER_CHECK_INPUT(obj) \
	if(!obj) return MIX_RESULT_NULL_PTR; \
	if(!MIX_IS_BUFFERPOOL(obj)) return MIX_RESULT_FAIL; \

#define MIX_BUFFERPOOL_GETTER_CHECK_INPUT(obj, prop) \
	if(!obj || !prop) return MIX_RESULT_NULL_PTR; \
	if(!MIX_IS_BUFFERPOOL(obj)) return MIX_RESULT_FAIL; \


MIX_RESULT
mix_bufferpool_dumpbuffer(MixBuffer *buffer)
{
	LOG_I( "\tBuffer %x, ptr %x, refcount %d\n", (guint)buffer,
			(guint)buffer->data, MIX_PARAMS(buffer)->refcount);
	return MIX_RESULT_SUCCESS;
}

MIX_RESULT
mix_bufferpool_dumpprint (MixBufferPool * obj)
{
	//TODO replace this with proper logging later

	LOG_I( "BUFFER POOL DUMP:\n");
	LOG_I( "Free list size is %d\n", g_slist_length(obj->free_list));
	LOG_I( "In use list size is %d\n", g_slist_length(obj->in_use_list));
	LOG_I( "High water mark is %lu\n", obj->high_water_mark);

	//Walk the free list and report the contents
	LOG_I( "Free list contents:\n");
	g_slist_foreach(obj->free_list, (GFunc) mix_bufferpool_dumpbuffer, NULL);

	//Walk the in_use list and report the contents
	LOG_I( "In Use list contents:\n");
	g_slist_foreach(obj->in_use_list, (GFunc) mix_bufferpool_dumpbuffer, NULL);

	return MIX_RESULT_SUCCESS;
}

