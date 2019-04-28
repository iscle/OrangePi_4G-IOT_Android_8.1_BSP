/* 
INTEL CONFIDENTIAL
Copyright 2009 Intel Corporation All Rights Reserved. 
The source code contained or described herein and all documents related to the source code ("Material") are owned by Intel Corporation or its suppliers or licensors. Title to the Material remains with Intel Corporation or its suppliers and licensors. The Material contains trade secrets and proprietary and confidential information of Intel or its suppliers and licensors. The Material is protected by worldwide copyright and trade secret laws and treaty provisions. No part of the Material may be used, copied, reproduced, modified, published, uploaded, posted, transmitted, distributed, or disclosed in any way without Intel’s prior express written permission.

No license under any patent, copyright, trade secret or other intellectual property right is granted to or conferred upon you by disclosure or delivery of the Materials, either expressly, by implication, inducement, estoppel or otherwise. Any license under such intellectual property rights must be express and approved by Intel in writing.
*/

#ifndef __MIX_BUFFERPOOL_H__
#define __MIX_BUFFERPOOL_H__

#include <mixparams.h>
#include "mixvideodef.h"
#include "mixbuffer.h"

#include <va/va.h>

G_BEGIN_DECLS

/**
* MIX_TYPE_BUFFERPOOL:
* 
* Get type of class.
*/
#define MIX_TYPE_BUFFERPOOL (mix_bufferpool_get_type ())

/**
* MIX_BUFFERPOOL:
* @obj: object to be type-casted.
*/
#define MIX_BUFFERPOOL(obj) (G_TYPE_CHECK_INSTANCE_CAST ((obj), MIX_TYPE_BUFFERPOOL, MixBufferPool))

/**
* MIX_IS_BUFFERPOOL:
* @obj: an object.
* 
* Checks if the given object is an instance of #MixBufferPool
*/
#define MIX_IS_BUFFERPOOL(obj) (G_TYPE_CHECK_INSTANCE_TYPE ((obj), MIX_TYPE_BUFFERPOOL))

/**
* MIX_BUFFERPOOL_CLASS:
* @klass: class to be type-casted.
*/
#define MIX_BUFFERPOOL_CLASS(klass) (G_TYPE_CHECK_CLASS_CAST ((klass), MIX_TYPE_BUFFERPOOL, MixBufferPoolClass))

/**
* MIX_IS_BUFFERPOOL_CLASS:
* @klass: a class.
* 
* Checks if the given class is #MixBufferPoolClass
*/
#define MIX_IS_BUFFERPOOL_CLASS(klass) (G_TYPE_CHECK_CLASS_TYPE ((klass), MIX_TYPE_BUFFERPOOL))

/**
* MIX_BUFFERPOOL_GET_CLASS:
* @obj: a #MixBufferPool object.
* 
* Get the class instance of the object.
*/
#define MIX_BUFFERPOOL_GET_CLASS(obj) (G_TYPE_INSTANCE_GET_CLASS ((obj), MIX_TYPE_BUFFERPOOL, MixBufferPoolClass))

typedef struct _MixBufferPool MixBufferPool;
typedef struct _MixBufferPoolClass MixBufferPoolClass;

/**
* MixBufferPool:
*
* MI-X Video Buffer Pool object
*/
struct _MixBufferPool
{
  /*< public > */
  MixParams parent;

  /*< public > */
  GSList *free_list;		/* list of free buffers */
  GSList *in_use_list;		/* list of buffers in use */
  gulong free_list_max_size;	/* initial size of the free list */
  gulong high_water_mark;	/* most buffers in use at one time */

  void *reserved1;
  void *reserved2;
  void *reserved3;
  void *reserved4;

  /*< private > */
  GMutex *objectlock;

};

/**
* MixBufferPoolClass:
* 
* MI-X Video Buffer Pool object class
*/
struct _MixBufferPoolClass
{
  /*< public > */
  MixParamsClass parent_class;

  /* class members */
};

/**
* mix_bufferpool_get_type:
* @returns: type
* 
* Get the type of object.
*/
GType mix_bufferpool_get_type (void);

/**
* mix_bufferpool_new:
* @returns: A newly allocated instance of #MixBufferPool
* 
* Use this method to create new instance of #MixBufferPool
*/
MixBufferPool *mix_bufferpool_new (void);
/**
* mix_bufferpool_ref:
* @mix: object to add reference
* @returns: the MixBufferPool instance where reference count has been increased.
* 
* Add reference count.
*/
MixBufferPool *mix_bufferpool_ref (MixBufferPool * mix);

/**
* mix_bufferpool_unref:
* @obj: object to unref.
* 
* Decrement reference count of the object.
*/
#define mix_bufferpool_unref(obj) mix_params_unref(MIX_PARAMS(obj))

/* Class Methods */

MIX_RESULT mix_bufferpool_initialize (MixBufferPool * obj, 
				guint num_buffers);
MIX_RESULT mix_bufferpool_put (MixBufferPool * obj,
				MixBuffer * buffer);

MIX_RESULT mix_bufferpool_get (MixBufferPool * obj,
				MixBuffer ** buffer);
MIX_RESULT mix_bufferpool_deinitialize (MixBufferPool * obj);

G_END_DECLS

#endif /* __MIX_BUFFERPOOL_H__ */
