/* 
INTEL CONFIDENTIAL
Copyright 2009 Intel Corporation All Rights Reserved. 
The source code contained or described herein and all documents related to the source code ("Material") are owned by Intel Corporation or its suppliers or licensors. Title to the Material remains with Intel Corporation or its suppliers and licensors. The Material contains trade secrets and proprietary and confidential information of Intel or its suppliers and licensors. The Material is protected by worldwide copyright and trade secret laws and treaty provisions. No part of the Material may be used, copied, reproduced, modified, published, uploaded, posted, transmitted, distributed, or disclosed in any way without Intel’s prior express written permission.

No license under any patent, copyright, trade secret or other intellectual property right is granted to or conferred upon you by disclosure or delivery of the Materials, either expressly, by implication, inducement, estoppel or otherwise. Any license under such intellectual property rights must be express and approved by Intel in writing.
*/

#ifndef __MIX_DISPLAYX11_H__
#define __MIX_DISPLAYX11_H__

#include "mixdisplay.h"
#include "mixvideodef.h"
#include <X11/Xlib.h>

/**
* MIX_TYPE_DISPLAYX11:
* 
* Get type of class.
*/
#define MIX_TYPE_DISPLAYX11 (mix_displayx11_get_type ())

/**
* MIX_DISPLAYX11:
* @obj: object to be type-casted.
*/
#define MIX_DISPLAYX11(obj) (G_TYPE_CHECK_INSTANCE_CAST ((obj), MIX_TYPE_DISPLAYX11, MixDisplayX11))

/**
* MIX_IS_DISPLAYX11:
* @obj: an object.
* 
* Checks if the given object is an instance of #MixDisplay
*/
#define MIX_IS_DISPLAYX11(obj) (G_TYPE_CHECK_INSTANCE_TYPE ((obj), MIX_TYPE_DISPLAYX11))

/**
* MIX_DISPLAYX11_CLASS:
* @klass: class to be type-casted.
*/
#define MIX_DISPLAYX11_CLASS(klass) (G_TYPE_CHECK_CLASS_CAST ((klass), MIX_TYPE_DISPLAYX11, MixDisplayX11Class))

/**
* MIX_IS_DISPLAYX11_CLASS:
* @klass: a class.
* 
* Checks if the given class is #MixDisplayClass
*/
#define MIX_IS_DISPLAYX11_CLASS(klass) (G_TYPE_CHECK_CLASS_TYPE ((klass), MIX_TYPE_DISPLAYX11))

/**
* MIX_DISPLAYX11_GET_CLASS:
* @obj: a #MixDisplay object.
* 
* Get the class instance of the object.
*/
#define MIX_DISPLAYX11_GET_CLASS(obj) (G_TYPE_INSTANCE_GET_CLASS ((obj), MIX_TYPE_DISPLAYX11, MixDisplayX11Class))

typedef struct _MixDisplayX11 MixDisplayX11;
typedef struct _MixDisplayX11Class MixDisplayX11Class;

/**
* MixDisplayX11:
*
* MI-X VideoInit Parameter object
*/
struct _MixDisplayX11
{
  /*< public > */
  MixDisplay parent;

  /*< public > */

  Display *display;
  Drawable drawable;
};

/**
* MixDisplayX11Class:
* 
* MI-X VideoInit object class
*/
struct _MixDisplayX11Class
{
  /*< public > */
  MixDisplayClass parent_class;

  /* class members */
};

/**
* mix_displayx11_get_type:
* @returns: type
* 
* Get the type of object.
*/
GType mix_displayx11_get_type (void);

/**
* mix_displayx11_new:
* @returns: A newly allocated instance of #MixDisplayX11
* 
* Use this method to create new instance of #MixDisplayX11
*/
MixDisplayX11 *mix_displayx11_new (void);
/**
* mix_displayx11_ref:
* @mix: object to add reference
* @returns: the MixDisplayX11 instance where reference count has been increased.
* 
* Add reference count.
*/
MixDisplayX11 *mix_displayx11_ref (MixDisplayX11 * mix);

/**
* mix_displayx11_unref:
* @obj: object to unref.
* 
* Decrement reference count of the object.
*/
#define mix_displayx11_unref(obj) mix_display_unref(MIX_DISPLAY(obj))

/* Class Methods */

/*
TO DO: Add documents
*/

MIX_RESULT mix_displayx11_set_display (MixDisplayX11 * obj,
				       Display * display);

MIX_RESULT mix_displayx11_get_display (MixDisplayX11 * obj,
				       Display ** dislay);

MIX_RESULT mix_displayx11_set_drawable (MixDisplayX11 * obj,
					Drawable drawable);

MIX_RESULT mix_displayx11_get_drawable (MixDisplayX11 * obj,
					Drawable * drawable);

#endif /* __MIX_DISPLAYX11_H__ */
