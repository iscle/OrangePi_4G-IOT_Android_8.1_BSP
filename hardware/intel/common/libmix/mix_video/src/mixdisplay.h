/* 
INTEL CONFIDENTIAL
Copyright 2009 Intel Corporation All Rights Reserved. 
The source code contained or described herein and all documents related to the source code ("Material") are owned by Intel Corporation or its suppliers or licensors. Title to the Material remains with Intel Corporation or its suppliers and licensors. The Material contains trade secrets and proprietary and confidential information of Intel or its suppliers and licensors. The Material is protected by worldwide copyright and trade secret laws and treaty provisions. No part of the Material may be used, copied, reproduced, modified, published, uploaded, posted, transmitted, distributed, or disclosed in any way without Intel’s prior express written permission.

No license under any patent, copyright, trade secret or other intellectual property right is granted to or conferred upon you by disclosure or delivery of the Materials, either expressly, by implication, inducement, estoppel or otherwise. Any license under such intellectual property rights must be express and approved by Intel in writing.
*/

#ifndef __MIX_DISPLAY_H__
#define __MIX_DISPLAY_H__

#include <glib-object.h>

G_BEGIN_DECLS
#define MIX_TYPE_DISPLAY          (mix_display_get_type())
#define MIX_IS_DISPLAY(obj)       (G_TYPE_CHECK_INSTANCE_TYPE ((obj), MIX_TYPE_DISPLAY))
#define MIX_IS_DISPLAY_CLASS(klass) (G_TYPE_CHECK_CLASS_TYPE ((klass), MIX_TYPE_DISPLAY))
#define MIX_DISPLAY_GET_CLASS(obj) (G_TYPE_INSTANCE_GET_CLASS ((obj), MIX_TYPE_DISPLAY, MixDisplayClass))
#define MIX_DISPLAY(obj)          (G_TYPE_CHECK_INSTANCE_CAST ((obj), MIX_TYPE_DISPLAY, MixDisplay))
#define MIX_DISPLAY_CLASS(klass)  (G_TYPE_CHECK_CLASS_CAST ((klass), MIX_TYPE_DISPLAY, MixDisplayClass))
#define MIX_DISPLAY_CAST(obj)     ((MixDisplay*)(obj))
typedef struct _MixDisplay MixDisplay;
typedef struct _MixDisplayClass MixDisplayClass;

/**
* MixDisplayDupFunction:
* @obj: Display to duplicate
* @returns: reference to cloned instance. 
*
* Virtual function prototype for methods to create duplicate of instance.
*
*/
typedef MixDisplay *(*MixDisplayDupFunction) (const MixDisplay * obj);

/**
* MixDisplayCopyFunction:
* @target: target of the copy
* @src: source of the copy
* @returns: boolean indicates if copy is successful.
*
* Virtual function prototype for methods to create copies of instance.
*
*/
typedef gboolean (*MixDisplayCopyFunction) (MixDisplay * target,
					    const MixDisplay * src);

/**
* MixDisplayFinalizeFunction:
* @obj: Display to finalize
*
* Virtual function prototype for methods to free ressources used by
* object.
*/
typedef void (*MixDisplayFinalizeFunction) (MixDisplay * obj);

/**
* MixDisplayEqualsFunction:
* @first: first object in the comparison
* @second: second object in the comparison
*
* Virtual function prototype for methods to compare 2 objects and check if they are equal.
*/
typedef gboolean (*MixDisplayEqualFunction) (MixDisplay * first,
					     MixDisplay * second);

/**
* MIX_VALUE_HOLDS_DISPLAY:
* @value: the #GValue to check
*
* Checks if the given #GValue contains a #MIX_TYPE_PARAM value.
*/
#define MIX_VALUE_HOLDS_DISPLAY(value)  (G_VALUE_HOLDS(value, MIX_TYPE_DISPLAY))

/**
* MIX_DISPLAY_REFCOUNT:
* @obj: a #MixDisplay
*
* Get access to the reference count field of the object.
*/
#define MIX_DISPLAY_REFCOUNT(obj)           ((MIX_DISPLAY_CAST(obj))->refcount)
/**
* MIX_DISPLAY_REFCOUNT_VALUE:
* @obj: a #MixDisplay
*
* Get the reference count value of the object
*/
#define MIX_DISPLAY_REFCOUNT_VALUE(obj)     (g_atomic_int_get (&(MIX_DISPLAY_CAST(obj))->refcount))

/**
* MixDisplay:
* @instance: type instance
* @refcount: atomic refcount
*
* Base class for a refcounted parameter objects.
*/
struct _MixDisplay
{
  GTypeInstance instance;
  /*< public > */
  gint refcount;

  /*< private > */
  gpointer _reserved;
};

/**
* MixDisplayClass:
* @dup: method to duplicate the object.
* @copy: method to copy details in one object to the other.
* @finalize: destructor
* @equal: method to check if the content of two objects are equal.
* 
* #MixDisplay class strcut.
*/
struct _MixDisplayClass
{
  GTypeClass type_class;

  MixDisplayDupFunction dup;
  MixDisplayCopyFunction copy;
  MixDisplayFinalizeFunction finalize;
  MixDisplayEqualFunction equal;

  /*< private > */
  gpointer _mix_reserved;
};

/**
* mix_display_get_type:
* @returns: type of this object.
* 
* Get type.
*/
GType mix_display_get_type (void);

/**
* mix_display_new:
* @returns: return a newly allocated object.
* 
* Create new instance of the object.
*/
MixDisplay *mix_display_new ();

/**
* mix_display_copy:
* @target: copy to target
* @src: copy from source
* @returns: boolean indicating if copy is successful.
* 
* Copy data from one instance to the other. This method internally invoked the #MixDisplay::copy method such that derived object will be copied correctly.
*/
gboolean mix_display_copy (MixDisplay * target, const MixDisplay * src);

/** 
* mix_display_ref:
* @obj: a #MixDisplay object.
* @returns: the object with reference count incremented.
* 
* Increment reference count.
*/
MixDisplay *mix_display_ref (MixDisplay * obj);

/** 
* mix_display_unref:
* @obj: a #MixDisplay object.
* 
* Decrement reference count.
*/
void mix_display_unref (MixDisplay * obj);

/**
* mix_display_replace:
* @olddata:
* @newdata:
* 
* Replace a pointer of the object with the new one.
*/
void mix_display_replace (MixDisplay ** olddata, MixDisplay * newdata);

/**
* mix_display_dup:
* @obj: #MixDisplay object to duplicate.
* @returns: A newly allocated duplicate of the object, or NULL if failed.
* 
* Duplicate the given #MixDisplay and allocate a new instance. This method is chained up properly and derive object will be dupped properly.
*/
MixDisplay *mix_display_dup (const MixDisplay * obj);

/**
* mix_display_equal:
* @first: first object to compare
* @second: second object to compare
* @returns: boolean indicates if the 2 object contains same data.
* 
* Note that the parameter comparison compares the values that are hold inside the object, not for checking if the 2 pointers are of the same instance.
*/
gboolean mix_display_equal (MixDisplay * first, MixDisplay * second);

/* GParamSpec */

#define MIX_TYPE_PARAM_DISPLAY (mix_param_spec_display_get_type())
#define MIX_IS_PARAM_SPEC_DISPLAY(pspec) (G_TYPE_CHECK_INSTANCE_TYPE ((pspec), MIX_TYPE_PARAM_DISPLAY))
#define MIX_PARAM_SPEC_DISPLAY(pspec) (G_TYPE_CHECK_INSTANCE_CAST ((pspec), MIX_TYPE_PARAM_DISPLAY, MixParamSpecDisplay))

typedef struct _MixParamSpecDisplay MixParamSpecDisplay;

/**
* MixParamSpecDisplay:
* @parent: #GParamSpec portion
* 
* A #GParamSpec derived structure that contains the meta data
* for #MixDisplay properties.
*/
struct _MixParamSpecDisplay
{
  GParamSpec parent;
};

GType mix_param_spec_display_get_type (void);

GParamSpec *mix_param_spec_display (const char *name, const char *nick,
				    const char *blurb, GType object_type,
				    GParamFlags flags);

/* GValue methods */

void mix_value_set_display (GValue * value, MixDisplay * obj);
void mix_value_take_display (GValue * value, MixDisplay * obj);
MixDisplay *mix_value_get_display (const GValue * value);
MixDisplay *mix_value_dup_display (const GValue * value);

G_END_DECLS
#endif
