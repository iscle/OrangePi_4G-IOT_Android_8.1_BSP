/* 
INTEL CONFIDENTIAL
Copyright 2009 Intel Corporation All Rights Reserved. 
The source code contained or described herein and all documents related to the source code ("Material") are owned by Intel Corporation or its suppliers or licensors. Title to the Material remains with Intel Corporation or its suppliers and licensors. The Material contains trade secrets and proprietary and confidential information of Intel or its suppliers and licensors. The Material is protected by worldwide copyright and trade secret laws and treaty provisions. No part of the Material may be used, copied, reproduced, modified, published, uploaded, posted, transmitted, distributed, or disclosed in any way without Intel’s prior express written permission.

No license under any patent, copyright, trade secret or other intellectual property right is granted to or conferred upon you by disclosure or delivery of the Materials, either expressly, by implication, inducement, estoppel or otherwise. Any license under such intellectual property rights must be express and approved by Intel in writing.
*/

#ifndef __MIX_SURFACEPOOL_H__
#define __MIX_SURFACEPOOL_H__

#include <mixparams.h>
#include "mixvideodef.h"
#include "mixvideoframe.h"

#include <va/va.h>

G_BEGIN_DECLS

/**
* MIX_TYPE_SURFACEPOOL:
* 
* Get type of class.
*/
#define MIX_TYPE_SURFACEPOOL (mix_surfacepool_get_type ())

/**
* MIX_SURFACEPOOL:
* @obj: object to be type-casted.
*/
#define MIX_SURFACEPOOL(obj) (G_TYPE_CHECK_INSTANCE_CAST ((obj), MIX_TYPE_SURFACEPOOL, MixSurfacePool))

/**
* MIX_IS_SURFACEPOOL:
* @obj: an object.
* 
* Checks if the given object is an instance of #MixSurfacePool
*/
#define MIX_IS_SURFACEPOOL(obj) (G_TYPE_CHECK_INSTANCE_TYPE ((obj), MIX_TYPE_SURFACEPOOL))

/**
* MIX_SURFACEPOOL_CLASS:
* @klass: class to be type-casted.
*/
#define MIX_SURFACEPOOL_CLASS(klass) (G_TYPE_CHECK_CLASS_CAST ((klass), MIX_TYPE_SURFACEPOOL, MixSurfacePoolClass))

/**
* MIX_IS_SURFACEPOOL_CLASS:
* @klass: a class.
* 
* Checks if the given class is #MixSurfacePoolClass
*/
#define MIX_IS_SURFACEPOOL_CLASS(klass) (G_TYPE_CHECK_CLASS_TYPE ((klass), MIX_TYPE_SURFACEPOOL))

/**
* MIX_SURFACEPOOL_GET_CLASS:
* @obj: a #MixSurfacePool object.
* 
* Get the class instance of the object.
*/
#define MIX_SURFACEPOOL_GET_CLASS(obj) (G_TYPE_INSTANCE_GET_CLASS ((obj), MIX_TYPE_SURFACEPOOL, MixSurfacePoolClass))

typedef struct _MixSurfacePool MixSurfacePool;
typedef struct _MixSurfacePoolClass MixSurfacePoolClass;

/**
* MixSurfacePool:
*
* MI-X Video Surface Pool object
*/
struct _MixSurfacePool
{
  /*< public > */
  MixParams parent;

  /*< public > */
  GSList *free_list;		/* list of free surfaces */
  GSList *in_use_list;		/* list of surfaces in use */
  gulong free_list_max_size;	/* initial size of the free list */
  gulong free_list_cur_size;	/* current size of the free list */
  gulong high_water_mark;	/* most surfaces in use at one time */
//  guint64 timestamp;

  void *reserved1;
  void *reserved2;
  void *reserved3;
  void *reserved4;

  /*< private > */
  GMutex *objectlock;

};

/**
* MixSurfacePoolClass:
* 
* MI-X Video Surface Pool object class
*/
struct _MixSurfacePoolClass
{
  /*< public > */
  MixParamsClass parent_class;

  /* class members */
};

/**
* mix_surfacepool_get_type:
* @returns: type
* 
* Get the type of object.
*/
GType mix_surfacepool_get_type (void);

/**
* mix_surfacepool_new:
* @returns: A newly allocated instance of #MixSurfacePool
* 
* Use this method to create new instance of #MixSurfacePool
*/
MixSurfacePool *mix_surfacepool_new (void);
/**
* mix_surfacepool_ref:
* @mix: object to add reference
* @returns: the MixSurfacePool instance where reference count has been increased.
* 
* Add reference count.
*/
MixSurfacePool *mix_surfacepool_ref (MixSurfacePool * mix);

/**
* mix_surfacepool_unref:
* @obj: object to unref.
* 
* Decrement reference count of the object.
*/
#define mix_surfacepool_unref(obj) mix_params_unref(MIX_PARAMS(obj))

/* Class Methods */

MIX_RESULT mix_surfacepool_initialize (MixSurfacePool * obj, 
				VASurfaceID *surfaces, guint num_surfaces);
MIX_RESULT mix_surfacepool_put (MixSurfacePool * obj,
				MixVideoFrame * frame);

MIX_RESULT mix_surfacepool_get (MixSurfacePool * obj,
				MixVideoFrame ** frame);

MIX_RESULT mix_surfacepool_get_frame_with_ci_frameidx (MixSurfacePool * obj, 
	MixVideoFrame ** frame, MixVideoFrame *in_frame);

MIX_RESULT mix_surfacepool_check_available (MixSurfacePool * obj);

MIX_RESULT mix_surfacepool_deinitialize (MixSurfacePool * obj);

G_END_DECLS

#endif /* __MIX_SURFACEPOOL_H__ */
