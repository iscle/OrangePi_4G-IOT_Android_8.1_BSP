/* 
 INTEL CONFIDENTIAL
 Copyright 2009 Intel Corporation All Rights Reserved. 
 The source code contained or described herein and all documents related to the source code ("Material") are owned by Intel Corporation or its suppliers or licensors. Title to the Material remains with Intel Corporation or its suppliers and licensors. The Material contains trade secrets and proprietary and confidential information of Intel or its suppliers and licensors. The Material is protected by worldwide copyright and trade secret laws and treaty provisions. No part of the Material may be used, copied, reproduced, modified, published, uploaded, posted, transmitted, distributed, or disclosed in any way without Intel’s prior express written permission.

 No license under any patent, copyright, trade secret or other intellectual property right is granted to or conferred upon you by disclosure or delivery of the Materials, either expressly, by implication, inducement, estoppel or otherwise. Any license under such intellectual property rights must be express and approved by Intel in writing.
*/

#ifndef __MIX_PARAMS_H__
#define __MIX_PARAMS_H__

#include <glib-object.h>

G_BEGIN_DECLS

#define MIX_TYPE_PARAMS          (mix_params_get_type())
#define MIX_IS_PARAMS(obj)       (G_TYPE_CHECK_INSTANCE_TYPE ((obj), MIX_TYPE_PARAMS))
#define MIX_IS_PARAMS_CLASS(klass) (G_TYPE_CHECK_CLASS_TYPE ((klass), MIX_TYPE_PARAMS))
#define MIX_PARAMS_GET_CLASS(obj) (G_TYPE_INSTANCE_GET_CLASS ((obj), MIX_TYPE_PARAMS, MixParamsClass))
#define MIX_PARAMS(obj)          (G_TYPE_CHECK_INSTANCE_CAST ((obj), MIX_TYPE_PARAMS, MixParams))
#define MIX_PARAMS_CLASS(klass)  (G_TYPE_CHECK_CLASS_CAST ((klass), MIX_TYPE_PARAMS, MixParamsClass))
#define MIX_PARAMS_CAST(obj)     ((MixParams*)(obj))

typedef struct _MixParams MixParams;
typedef struct _MixParamsClass MixParamsClass;

/**
 * MixParamsDupFunction:
 * @obj: Params to duplicate
 * @returns: reference to cloned instance. 
 *
 * Virtual function prototype for methods to create duplicate of instance.
 *
 */
typedef MixParams * (*MixParamsDupFunction) (const MixParams *obj);

/**
 * MixParamsCopyFunction:
 * @target: target of the copy
 * @src: source of the copy
 * @returns: boolean indicates if copy is successful.
 *
 * Virtual function prototype for methods to create copies of instance.
 *
 */
typedef gboolean (*MixParamsCopyFunction) (MixParams* target, const MixParams *src);

/**
 * MixParamsFinalizeFunction:
 * @obj: Params to finalize
 *
 * Virtual function prototype for methods to free ressources used by
 * object.
 */
typedef void (*MixParamsFinalizeFunction) (MixParams *obj);

/**
 * MixParamsEqualsFunction:
 * @first: first object in the comparison
 * @second: second object in the comparison
 *
 * Virtual function prototype for methods to compare 2 objects and check if they are equal.
 */
typedef gboolean (*MixParamsEqualFunction) (MixParams *first, MixParams *second);

/**
 * MIX_VALUE_HOLDS_PARAMS:
 * @value: the #GValue to check
 *
 * Checks if the given #GValue contains a #MIX_TYPE_PARAM value.
 */
#define MIX_VALUE_HOLDS_PARAMS(value)  (G_VALUE_HOLDS(value, MIX_TYPE_PARAMS))

/**
 * MIX_PARAMS_REFCOUNT:
 * @obj: a #MixParams
 *
 * Get access to the reference count field of the object.
 */
#define MIX_PARAMS_REFCOUNT(obj)           ((MIX_PARAMS_CAST(obj))->refcount)
/**
 * MIX_PARAMS_REFCOUNT_VALUE:
 * @obj: a #MixParams
 *
 * Get the reference count value of the object
 */
#define MIX_PARAMS_REFCOUNT_VALUE(obj)     (g_atomic_int_get (&(MIX_PARAMS_CAST(obj))->refcount))

/**
 * MixParams:
 * @instance: type instance
 * @refcount: atomic refcount
 *
 * Base class for a refcounted parameter objects.
 */
struct _MixParams {
  GTypeInstance instance;
  /*< public >*/
  gint refcount;

  /*< private >*/
  gpointer _reserved;
};

/**
 * MixParamsClass:
 * @dup: method to duplicate the object.
 * @copy: method to copy details in one object to the other.
 * @finalize: destructor
 * @equal: method to check if the content of two objects are equal.
 * 
 * #MixParams class strcut.
 */
struct _MixParamsClass {
  GTypeClass type_class;

  MixParamsDupFunction dup;
  MixParamsCopyFunction copy;
  MixParamsFinalizeFunction finalize;
  MixParamsEqualFunction equal;

  /*< private >*/
  gpointer _mix_reserved;
};

/**
 * mix_params_get_type:
 * @returns: type of this object.
 * 
 * Get type.
 */
GType mix_params_get_type(void);

/**
 * mix_params_new:
 * @returns: return a newly allocated object.
 * 
 * Create new instance of the object.
 */
MixParams* mix_params_new();

/**
 * mix_params_copy:
 * @target: copy to target
 * @src: copy from source
 * @returns: boolean indicating if copy is successful.
 * 
 * Copy data from one instance to the other. This method internally invoked the #MixParams::copy method such that derived object will be copied correctly.
 */
gboolean mix_params_copy(MixParams *target, const MixParams *src);


/** 
 * mix_params_ref:
 * @obj: a #MixParams object.
 * @returns: the object with reference count incremented.
 * 
 * Increment reference count.
 */
MixParams* mix_params_ref(MixParams *obj);


/** 
 * mix_params_unref:
 * @obj: a #MixParams object.
 * 
 * Decrement reference count.
 */
void mix_params_unref  (MixParams *obj);

/**
 * mix_params_replace:
 * @olddata:
 * @newdata:
 * 
 * Replace a pointer of the object with the new one.
 */
void mix_params_replace(MixParams **olddata, MixParams *newdata);

/**
 * mix_params_dup:
 * @obj: #MixParams object to duplicate.
 * @returns: A newly allocated duplicate of the object, or NULL if failed.
 * 
 * Duplicate the given #MixParams and allocate a new instance. This method is chained up properly and derive object will be dupped properly.
 */
MixParams *mix_params_dup(const MixParams *obj);

/**
 * mix_params_equal:
 * @first: first object to compare
 * @second: second object to compare
 * @returns: boolean indicates if the 2 object contains same data.
 * 
 * Note that the parameter comparison compares the values that are hold inside the object, not for checking if the 2 pointers are of the same instance.
 */
gboolean mix_params_equal(MixParams *first, MixParams *second);

G_END_DECLS

#endif

